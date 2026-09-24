package app.melotrail.video.adapter

import app.melotrail.video.application.VideoJobConcurrencyException
import app.melotrail.video.application.VideoJobPersistence
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoGenerationAttempt
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoGenerationJob
import app.melotrail.video.domain.VideoJobLedger
import app.melotrail.video.domain.VideoJobControlPaths
import app.melotrail.video.domain.VideoSubmissionPhase
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Atomic filesystem ledger for one admission domain. The stable lock survives
 * document replacement; canonical roots make symlink aliases share that lock.
 * Directory identities stay pinned for the lifetime of the store.
 */
class VideoJobStore(
    admissionRoot: Path,
    private val admissionDomainId: String,
    protectedMidiRoots: Collection<Path>,
    private val atomicWriteObserver: VideoJobAtomicWriteObserver = VideoJobAtomicWriteObserver.NONE,
) : VideoJobPersistence {
    private val requestedRoot = admissionRoot.toAbsolutePath()
    private val protectedPaths = protectedMidiRoots.toList()
    private val protectedRoots = protectedMidiRoots.map(::canonicalFuturePath)
    private val root: Path
    private val directoryKeys: Map<Path, Any>

    init {
        require(admissionDomainId.isNotBlank()) { "Admission domain ID is required" }
        require(protectedMidiRoots.isNotEmpty()) {
            "At least one MIDI project or export root must be protected before video job storage is used"
        }
        val planned = validateLocation(admissionRoot)
        if (Files.exists(planned, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(planned, LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("The video job admission root is not a directory: $planned")
        }
        Files.createDirectories(planned)
        root = planned.toRealPath()
        validateLocation(root)
        directoryKeys = generateSequence(root) { it.parent }.associateWith(::directoryKey)
        requirePinnedRoot()
    }

    override fun loadOrCreate(admissionDomainId: String, createdAt: String): VideoJobLedger {
        require(admissionDomainId == this.admissionDomainId) { "The store is bound to another admission domain" }
        return withWriteLock {
            val target = root.resolve(DOCUMENT)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return@withWriteLock readCurrent()
            val ledger = VideoJobLedger(admissionDomainId, createdAt)
            publish(ledger, replace = false)
            ledger
        }
    }

    override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger {
        require(expectedRevision >= 0L) { "Expected video job revision must not be negative" }
        return withWriteLock {
            val current = readCurrent()
            if (current.revision != expectedRevision) {
                throw VideoJobConcurrencyException(
                    "Video job ledger changed from revision $expectedRevision to ${current.revision}.",
                )
            }
            require(replacement.revision == Math.addExact(expectedRevision, 1L)) {
                "The video job ledger must advance by exactly one revision"
            }
            requireSafeTransition(current, replacement)
            publish(replacement, replace = true)
            replacement
        }
    }

    fun snapshot(): VideoJobLedger = withWriteLock { readCurrent() }

    private fun requireSafeTransition(current: VideoJobLedger, replacement: VideoJobLedger) {
        require(replacement.admissionDomainId == current.admissionDomainId) { "Admission domain identity is immutable" }
        require(replacement.createdAt == current.createdAt) { "Admission domain creation time is immutable" }
        require(replacement.jobs.size in current.jobs.size..current.jobs.size + 1) {
            "A video job ledger revision can append at most one job"
        }
        current.jobs.forEachIndexed { index, oldJob ->
            val newJob = replacement.jobs[index]
            require(newJob.request == oldJob.request) { "Video generation requests are immutable" }
            require(newJob.attempts.size in oldJob.attempts.size..oldJob.attempts.size + 1) {
                "A video job ledger revision can append at most one attempt"
            }
            require(newJob.outputs.take(oldJob.outputs.size) == oldJob.outputs) { "Video generation outputs are immutable and append-only" }
            require(oldJob.currentOutputId == null || newJob.currentOutputId == oldJob.currentOutputId) {
                "A current video output cannot be replaced"
            }
            oldJob.attempts.forEachIndexed { attemptIndex, oldAttempt ->
                requireSafeAttemptTransition(oldAttempt, newJob.attempts[attemptIndex])
            }
            if (newJob.attempts.size > oldJob.attempts.size) requireFreshAttempt(newJob.attempts.last())
        }
        if (replacement.jobs.size > current.jobs.size) {
            val appended = replacement.jobs.last()
            require(appended.attempts.size == 1 && appended.outputs.isEmpty() && appended.currentOutputId == null) {
                "A new video job must begin with exactly one durable attempt and no output"
            }
            requireFreshAttempt(appended.attempts.single())
        }
    }

    private fun requireFreshAttempt(attempt: VideoGenerationAttempt) {
        require(attempt.status == VideoGenerationAttemptStatus.SUBMITTING &&
            attempt.submissionPhase == VideoSubmissionPhase.READY &&
            attempt.providerWorkId == null && attempt.progressPercent == null && attempt.finishedAt == null &&
            attempt.actualCostMicros == null && attempt.resourceUsage == null
        ) { "A new video attempt must persist unlaunched durable ownership" }
    }

    private fun requireSafeAttemptTransition(old: VideoGenerationAttempt, new: VideoGenerationAttempt) {
        require(new.id == old.id && new.requestId == old.requestId && new.number == old.number) {
            "Video attempt identity is immutable"
        }
        require(new.ownershipToken == old.ownershipToken && new.admittedAt == old.admittedAt) {
            "Video attempt ownership is immutable"
        }
        require(old.providerWorkId == null || new.providerWorkId == old.providerWorkId) {
            "Provider work identity cannot be replaced"
        }
        require(old.progressPercent == null || new.progressPercent != null && new.progressPercent >= old.progressPercent) {
            "Known video progress cannot become unknown or move backwards"
        }
        require(old.actualCostMicros == null || new.actualCostMicros == old.actualCostMicros) {
            "Recorded actual cost is immutable"
        }
        requireResourceUsageExtension(old.resourceUsage, new.resourceUsage)
        require(new.submissionPhase in allowedSubmissionPhases(old.submissionPhase)) {
            "Invalid submission phase transition ${old.submissionPhase} -> ${new.submissionPhase}"
        }
        require(new.status in allowedNextStatuses(old.status)) {
            "Invalid video attempt transition ${old.status} -> ${new.status}"
        }
    }

    private fun requireResourceUsageExtension(
        old: app.melotrail.video.domain.VideoGenerationResourceUsage?,
        new: app.melotrail.video.domain.VideoGenerationResourceUsage?,
    ) {
        if (old == null) return
        require(new != null) { "Recorded resource usage cannot become unknown" }
        require(old.wallClockMillis == null || new.wallClockMillis == old.wallClockMillis) { "Recorded wall-clock usage is immutable" }
        require(old.peakMemoryBytes == null || new.peakMemoryBytes == old.peakMemoryBytes) { "Recorded peak-memory usage is immutable" }
        require(old.diskBytes == null || new.diskBytes == old.diskBytes) { "Recorded disk usage is immutable" }
    }

    private fun allowedSubmissionPhases(phase: VideoSubmissionPhase): Set<VideoSubmissionPhase> = when (phase) {
        VideoSubmissionPhase.READY -> setOf(VideoSubmissionPhase.READY, VideoSubmissionPhase.PENDING, VideoSubmissionPhase.NOT_STARTED)
        VideoSubmissionPhase.PENDING -> VideoSubmissionPhase.entries.toSet() - VideoSubmissionPhase.READY
        VideoSubmissionPhase.UNCERTAIN -> setOf(
            VideoSubmissionPhase.UNCERTAIN,
            VideoSubmissionPhase.ACKNOWLEDGED,
            VideoSubmissionPhase.NOT_STARTED,
        )
        VideoSubmissionPhase.ACKNOWLEDGED -> setOf(VideoSubmissionPhase.ACKNOWLEDGED)
        VideoSubmissionPhase.NOT_STARTED -> setOf(VideoSubmissionPhase.NOT_STARTED, VideoSubmissionPhase.ACKNOWLEDGED)
    }

    private fun allowedNextStatuses(status: VideoGenerationAttemptStatus): Set<VideoGenerationAttemptStatus> = when (status) {
        VideoGenerationAttemptStatus.SUBMITTING -> VideoGenerationAttemptStatus.entries.toSet()
        VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN -> setOf(
            VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN,
            VideoGenerationAttemptStatus.ACTIVE,
            VideoGenerationAttemptStatus.CANCELLATION_REQUESTED,
            VideoGenerationAttemptStatus.SUCCEEDED,
            VideoGenerationAttemptStatus.FAILED,
            VideoGenerationAttemptStatus.CANCELLED,
        )
        VideoGenerationAttemptStatus.ACTIVE -> setOf(
            VideoGenerationAttemptStatus.ACTIVE,
            VideoGenerationAttemptStatus.CANCELLATION_REQUESTED,
            VideoGenerationAttemptStatus.SUCCEEDED,
            VideoGenerationAttemptStatus.FAILED,
            VideoGenerationAttemptStatus.CANCELLED,
            VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN,
        )
        VideoGenerationAttemptStatus.CANCELLATION_REQUESTED -> setOf(
            VideoGenerationAttemptStatus.CANCELLATION_REQUESTED,
            VideoGenerationAttemptStatus.SUCCEEDED,
            VideoGenerationAttemptStatus.FAILED,
            VideoGenerationAttemptStatus.CANCELLED,
        )
        VideoGenerationAttemptStatus.FAILED,
        VideoGenerationAttemptStatus.CANCELLED,
        -> setOf(status, VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, VideoGenerationAttemptStatus.SUCCEEDED)
        VideoGenerationAttemptStatus.SUCCEEDED -> setOf(VideoGenerationAttemptStatus.SUCCEEDED)
    }

    private fun readCurrent(): VideoJobLedger {
        requirePinnedRoot()
        val target = root.resolve(DOCUMENT)
        requireSafeRegularFile(target, "Video job ledger")
        val document = Files.readString(target, StandardCharsets.UTF_8)
        requirePinnedRoot()
        return VideoJobSchema.decode(document).also {
            require(it.admissionDomainId == admissionDomainId) { "The ledger belongs to another admission domain" }
        }
    }

    private fun publish(ledger: VideoJobLedger, replace: Boolean) {
        requirePinnedRoot()
        val target = root.resolve(DOCUMENT)
        val bytes = VideoJobSchema.encode(ledger).toByteArray(StandardCharsets.UTF_8)
        require(VideoJobSchema.decode(bytes.toString(StandardCharsets.UTF_8)) == ledger) {
            "Video job schema validation changed the ledger"
        }
        requirePinnedRoot()
        val temporary = Files.createTempFile(root, STAGING_PREFIX, ".tmp")
        var published = false
        try {
            requirePinnedRoot()
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            atomicWriteObserver.beforePublish(temporary, target)
            requirePinnedRoot()
            requireSafeRegularFile(temporary, "Staged video job ledger")
            require(VideoJobSchema.decode(Files.readString(temporary, StandardCharsets.UTF_8)) == ledger) {
                "The staged video job ledger changed before publication"
            }
            requirePinnedRoot()
            if (replace) {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                published = true
            } else {
                Files.createLink(target, temporary)
                published = true
            }
        } catch (error: Exception) {
            val recovery = retainRecovery(temporary)
            if (!replace && error is FileAlreadyExistsException) {
                throw VideoJobConcurrencyException("Another coordinator created the video job ledger.")
            }
            throw VideoJobStoreException(
                "Video job ledger save failed; the last known-good document was preserved.",
                target,
                recovery,
                error,
            )
        } finally {
            if (published) runCatching {
                requirePinnedRoot()
                Files.deleteIfExists(temporary)
            }
        }
    }

    private fun retainRecovery(temporary: Path): Path? {
        // A renamed directory still contains the original ledger and staged
        // evidence. Never create recovery files or clean up through its rebound
        // pathname; its new location is outside this store's authority.
        if (runCatching { requirePinnedRoot() }.isFailure) return null
        if (!Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) return null
        val recovery = root.resolve("$RECOVERY_PREFIX${UUID.randomUUID()}.json")
        return try {
            requireSafeRegularFile(temporary, "Staged video job ledger")
            requirePinnedRoot()
            Files.createLink(recovery, temporary)
            requirePinnedRoot()
            Files.deleteIfExists(temporary)
            recovery
        } catch (_: Exception) {
            temporary
        }
    }

    private fun <T> withWriteLock(action: () -> T): T {
        requirePinnedRoot()
        val lockPath = root.resolve(LOCK)
        val monitor = JVM_LOCKS.computeIfAbsent(lockPath) { Any() }
        return synchronized(monitor) {
            requirePinnedRoot()
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) &&
                (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(lockPath))
            ) {
                throw IllegalArgumentException("The video job lock path is unsafe: $lockPath")
            }
            requirePinnedRoot()
            FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                channel.lock().use {
                    requirePinnedRoot()
                    action()
                }
            }
        }
    }

    private fun requirePinnedRoot() {
        require(canonicalFuturePath(requestedRoot) == root && validateLocation(root) == root) {
            "The video job admission root has been rebound"
        }
        directoryKeys.forEach { (path, key) ->
            require(directoryKey(path) == key) { "The video job admission directory was replaced: $path" }
        }
    }

    private fun directoryKey(path: Path): Any {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        require(attributes.isDirectory && !attributes.isSymbolicLink) { "The video job directory path is unsafe: $path" }
        return requireNotNull(attributes.fileKey()) { "The video job directory identity is unavailable: $path" }
    }

    private fun validateLocation(path: Path): Path {
        val candidate = canonicalFuturePath(path)
        (protectedRoots + protectedPaths.map(::canonicalFuturePath)).firstOrNull { protected ->
            candidate.startsWith(protected) || protected.startsWith(candidate)
        }?.let { protected ->
            throw IllegalArgumentException("Video job storage '$candidate' overlaps protected MIDI storage '$protected'.")
        }
        findMidiMarker(candidate)?.let { marker ->
            throw IllegalArgumentException("Video job storage is inside a MIDI project identified by '$marker'.")
        }
        return candidate
    }

    private fun findMidiMarker(candidate: Path): Path? {
        var current: Path? = candidate
        while (current != null) {
            val marker = current.resolve(MIDI_PROJECT_FILE)
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return marker
            current = current.parent
        }
        return null
    }

    private fun requireSafeRegularFile(path: Path, label: String) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw IllegalArgumentException("$label is missing or is not a regular file")
        }
        if (!path.toRealPath().startsWith(root)) throw IllegalArgumentException("$label escapes its admission root")
    }

    companion object {
        const val DOCUMENT = VideoJobControlPaths.DOCUMENT
        const val LOCK = VideoJobControlPaths.LOCK
        const val MIDI_PROJECT_FILE = "project.json"
        private const val STAGING_PREFIX = VideoJobControlPaths.STAGING_PREFIX
        private const val RECOVERY_PREFIX = VideoJobControlPaths.RECOVERY_PREFIX
        private val JVM_LOCKS = ConcurrentHashMap<Path, Any>()
    }
}

fun interface VideoJobAtomicWriteObserver {
    fun beforePublish(temporary: Path, target: Path)

    companion object { val NONE = VideoJobAtomicWriteObserver { _, _ -> } }
}

class VideoJobStoreException(
    message: String,
    val ledgerFile: Path,
    val recoveryEvidence: Path?,
    cause: Throwable,
) : IOException(message, cause)

private object VideoJobSchema {
    private const val SCHEMA = "melotrail-video-jobs"
    private const val VERSION = 3
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }

    fun encode(ledger: VideoJobLedger): String = json.encodeToString(VideoJobDocument(SCHEMA, VERSION, ledger))

    fun decode(document: String): VideoJobLedger {
        val root = try { json.parseToJsonElement(document).jsonObject } catch (error: Exception) {
            throw IllegalArgumentException("Video job ledger is not valid JSON", error)
        }
        val schema = root["schema"]?.jsonPrimitive?.contentOrNull
        val version = root["version"]?.jsonPrimitive?.intOrNull
        require(schema == SCHEMA && version == VERSION) {
            "Unsupported video job schema '${schema ?: "missing"}' version '${version ?: "missing"}'."
        }
        return try {
            json.decodeFromString<VideoJobDocument>(document).ledger.also { ledger ->
                ledger.jobs.forEach { job ->
                    val input = job.request.input as? VideoControlledMotionGenerationInput ?: return@forEach
                    require(job.request.requestFingerprint == controlledMotionRequestFingerprint(
                        job.request.backendId, input, job.request.modelRequirements,
                    )) { "Controlled request binding does not match its durable fingerprint" }
                }
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid video job v$VERSION ledger", error)
        }
    }
}

@Serializable
private data class VideoJobDocument(val schema: String, val version: Int, val ledger: VideoJobLedger)

private fun canonicalFuturePath(path: Path): Path {
    val absolute = path.toAbsolutePath()
    var ancestor: Path? = absolute
    val missing = ArrayDeque<Path>()
    while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
        ancestor.fileName?.let { missing.addFirst(it) }
        ancestor = ancestor.parent
    }
    val existing = ancestor ?: throw IllegalArgumentException("The storage path has no resolvable ancestor: $absolute")
    val real = try { existing.toRealPath() } catch (error: IOException) {
        throw IllegalArgumentException("The storage path has an unresolved symbolic link: $absolute", error)
    }
    if (missing.any { it.toString() == "." || it.toString() == ".." }) {
        throw IllegalArgumentException("The storage path traverses an unresolved parent: $absolute")
    }
    return missing.fold(real) { resolved, segment -> resolved.resolve(segment) }
}
