package app.melotrail.video.adapter

import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.time.Clock
import java.time.Instant
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Semaphore

/** Claimed controlled-motion worker. Rendering/encoding is supplied by the media pipeline;
 * no native work is performed by submit or while the job ledger is locked. A missing worker
 * after restart is unknown, never a reason to relaunch a PENDING attempt. */
class VideoControlledMediaStage(
    private val jobs: VideoJobPersistence,
    private val admissionDomainId: String,
    private val execute: (VideoGenerationJobRequest, VideoOwnedBackendAttempt, VideoMediaProcessCancellation) -> VideoBackendObservation,
    private val clock: Clock = Clock.systemUTC(),
    private val boundedNative: Boolean = false,
    private val receiptRoot: Path? = null,
    private val queueWork: (Runnable) -> Unit = { pool.execute(it) },
) : VideoGenerationBackendPort {
    /** Production rendering path. Claimed work is rendered, encoded and validated before publication. */
    constructor(jobs: VideoJobPersistence, admissionDomainId: String, projectRoot: Path, outputRoot: Path,
                renderer: VideoMotionRenderer = VideoMotionRenderer(), clock: Clock = Clock.systemUTC(),
                hostFreeMemoryBytes: () -> Long = {
                    (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1L
                },
                runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult =
                    { request, cancellation -> VideoMediaProcess().run(request, cancellation) }) :
        this(jobs, admissionDomainId, { request, owned, cancellation ->
            try {
                renderClaimed(request, owned, cancellation, projectRoot, outputRoot, renderer, hostFreeMemoryBytes,
                    runProcess, onEncoding = { markEncoding(jobs, admissionDomainId, owned, clock) })
            } catch (error: VideoMediaProcessException) {
                if (error.suppressed.isNotEmpty()) VideoBackendObservation.Unknown(
                    "Controlled native cleanup is unconfirmed: ${error.message}")
                else when (error.failure) {
                    VideoMediaProcessFailure.SUPERVISION_FAILED, VideoMediaProcessFailure.UNEXPECTED_DESCENDANTS ->
                        VideoBackendObservation.Unknown(error.message ?: "Native cleanup was not confirmed.")
                    VideoMediaProcessFailure.CANCELLED -> VideoBackendObservation.Cancelled(null)
                    else -> VideoBackendObservation.Failed(null, error.message ?: error.failure.name, true)
                }
            }
        }, clock, true, outputRoot)

    override val backendId: String = BACKEND_ID

    override fun availability() = VideoBackendAvailability(
        backendId, VideoBackendAvailabilityStatus.AVAILABLE, Instant.now(clock).toString(),
        setOf(VideoGenerationInputKind.CONTROLLED_MOTION), emptyList(),
        "Controlled media worker configured; ComfyUI and models are not required.",
    )

    override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
        val owned = command.ownedAttempt
        val request = try { claimed(owned) } catch (error: Exception) {
            return VideoBackendSubmission.Rejected(error.message ?: "Claim verification failed.", false)
        } ?: return VideoBackendSubmission.Rejected("Current PENDING controlled attempt is not owned by this submission.", false)
        if (request.input != command.input || request.modelRequirements != command.modelRequirements || request.execution != command.execution) {
            return VideoBackendSubmission.Rejected("Submission fields differ from persisted controlled execution.", false)
        }
        val key = workKey(owned)
        // Persist a single execution claim across adapter instances before queuing work.
        if (!checkpoint(owned, VideoControlledStage.CLAIMED, requirePending = true))
            return VideoBackendSubmission.Uncertain("Controlled claim already has execution evidence; reconcile without relaunch.")
        if (workers.containsKey(key)) return VideoBackendSubmission.Uncertain("Controlled attempt was already submitted; observe its existing work.")
        if (boundedNative && !nativeSlot.tryAcquire()) {
            val reason = "Controlled native slot is occupied; no work was queued."
            return if (checkpoint(owned, VideoControlledStage.FAILED, failure = reason, retryable = true))
                VideoBackendSubmission.Rejected(reason, true)
            else VideoBackendSubmission.Uncertain("Controlled no-start checkpoint could not be confirmed; retain admission.")
        }
        val work = Work()
        if (workers.putIfAbsent(key, work) != null) {
            if (boundedNative) nativeSlot.release()
            return VideoBackendSubmission.Uncertain("Controlled attempt was already submitted; observe its existing work.")
        }
        try {
            queueWork(Runnable {
                try {
                    // A queued worker can prove it never entered native work. Reconstructed
                    // services cannot: they must retain an unconfirmed CLAIMED attempt.
                    val current = claimed(owned, allowAcknowledged = true)
                    work.result = if (work.cancellation.isCancelled() &&
                        checkpoint(owned, VideoControlledStage.CANCELLED)) {
                        VideoBackendObservation.Cancelled(key)
                    } else if (current == null) {
                        VideoBackendObservation.Unknown("Claim changed before controlled execution.")
                    } else {
                        require(checkpoint(owned, VideoControlledStage.RENDERING)) {
                            "Controlled claim changed before native work; do not execute."
                        }
                        val result = if (work.cancellation.isCancelled()) VideoBackendObservation.Cancelled(key)
                            else execute(current, owned, work.cancellation)
                        when (result) {
                            is VideoBackendObservation.Completed -> {
                                if (verifiedOutput(owned, result.output)) checkpoint(owned, VideoControlledStage.COMPLETED,
                                    VideoControlledOutputEvidence(result.output.backendOutputId, requireNotNull(result.output.relativePath),
                                        requireNotNull(result.output.sha256), requireNotNull(result.output.byteCount)))
                            }
                            is VideoBackendObservation.Failed -> checkpoint(owned, VideoControlledStage.FAILED,
                                failure = result.reason, retryable = result.retryable)
                            is VideoBackendObservation.Cancelled -> checkpoint(owned, VideoControlledStage.CANCELLED)
                            else -> Unit
                        }
                        result
                    }
                } catch (error: Exception) {
                    work.result = VideoBackendObservation.Unknown(error.message ?: "Controlled worker outcome is uncertain.")
                } finally {
                    if (boundedNative) nativeSlot.release()
                }
            })
        } catch (error: RejectedExecutionException) {
            // A rejecting queue did not accept this task. Only this submitter can
            // attest to that fact; absence of a worker after restart cannot.
            val reason = "Controlled worker could not be queued: ${error.message ?: error.javaClass.simpleName}"
            val confirmed = checkpoint(owned, VideoControlledStage.FAILED, failure = reason, retryable = true)
            work.result = if (confirmed) VideoBackendObservation.Failed(key, reason, true)
                else VideoBackendObservation.Unknown("Controlled no-start checkpoint could not be confirmed.")
            workers.remove(key, work)
            if (boundedNative) nativeSlot.release()
            return if (confirmed) VideoBackendSubmission.Rejected(reason, true)
                else VideoBackendSubmission.Uncertain("Controlled no-start checkpoint could not be confirmed; retain admission.")
        }
        return VideoBackendSubmission.Accepted(key)
    }

    override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation {
        val key = workKey(ownedAttempt)
        val attempt = persistedAttempt(ownedAttempt) ?: return VideoBackendObservation.Unknown("Controlled ownership or current attempt changed.")
        if (ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key)
            return VideoBackendObservation.Unknown("Controlled work identity changed.")
        var output = attempt.controlledEvidence?.output
        if (output == null && !attempt.status.isTerminal && receiptRoot != null) {
            // Publication and its sealed receipt may have committed before the ledger CAS.
            // Only that exact current owner can promote verified bytes; never render again.
            val recovered = runCatching { readReceipt(requireNotNull(receiptRoot).resolve(ownedAttempt.attemptId).resolve("completion.json")) }.getOrNull()
            if (recovered != null && verifiedOutput(ownedAttempt, VideoBackendOutput(recovered.output.backendOutputId,
                    recovered.output.relativePath, recovered.output.sha256, recovered.output.byteCount)) &&
                checkpoint(ownedAttempt, VideoControlledStage.COMPLETED, recovered.output)) output = recovered.output
        }
        if (output != null) {
            val candidate = VideoBackendOutput(output.backendOutputId, output.relativePath, output.sha256, output.byteCount)
            return if (verifiedOutput(ownedAttempt, candidate)) VideoBackendObservation.Completed(key, candidate)
                else VideoBackendObservation.Unknown("Controlled completion receipt or published bytes changed; retain admission.")
        }
        when (attempt.controlledEvidence?.stage) {
            VideoControlledStage.FAILED -> return VideoBackendObservation.Failed(key,
                requireNotNull(attempt.controlledEvidence.failure), attempt.controlledEvidence.retryable)
            VideoControlledStage.CANCELLED -> return VideoBackendObservation.Cancelled(key)
            else -> Unit
        }
        val work = workers[key] ?: return VideoBackendObservation.Unknown("Claimed controlled execution has no known worker; do not relaunch it.")
        val result = work.result
        return when (result) {
            is VideoBackendObservation.Completed, is VideoBackendObservation.Failed, is VideoBackendObservation.Cancelled ->
                VideoBackendObservation.Unknown("Controlled terminal evidence was not durably verified.")
            else -> result ?: VideoBackendObservation.Running(key, null)
        }
    }

    override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation {
        val key = workKey(ownedAttempt)
        if (persistedAttempt(ownedAttempt) == null ||
            ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key) {
            return VideoBackendCancellation.Unknown("Controlled attempt identity does not match the worker.")
        }
        val work = workers[key] ?: return VideoBackendCancellation.Unknown("No owned worker can be confirmed stopped.")
        work.cancellation.cancel()
        return VideoBackendCancellation.Requested
    }

    private fun workKey(owned: VideoOwnedBackendAttempt) =
        "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}"

    private fun persistedAttempt(owned: VideoOwnedBackendAttempt): VideoGenerationAttempt? {
        if (owned.backendId != backendId) return null
        val ledger = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString())
        val job = ledger.jobs.singleOrNull { it.request.id == owned.requestId } ?: return null
        if (ledger.admissionDomainId != admissionDomainId || job.request.backendId != backendId ||
            job.request.requestFingerprint != owned.requestFingerprint || job.attempts.lastOrNull()?.id != owned.attemptId) return null
        return job.attempts.last().takeIf { it.ownershipToken == owned.ownershipToken }
    }

    private fun checkpoint(owned: VideoOwnedBackendAttempt, stage: VideoControlledStage,
        output: VideoControlledOutputEvidence? = null, requirePending: Boolean = false,
        failure: String? = null, retryable: Boolean = false): Boolean {
        repeat(64) {
            val ledger = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString())
            val index = ledger.jobs.indexOfFirst { it.request.id == owned.requestId }
            if (index < 0) return false
            val job = ledger.jobs[index]
            val attempt = job.attempts.lastOrNull() ?: return false
            if (job.request.backendId != backendId || job.request.requestFingerprint != owned.requestFingerprint ||
                attempt.id != owned.attemptId || attempt.ownershipToken != owned.ownershipToken ||
                attempt.status.isTerminal || requirePending && (attempt.submissionPhase != VideoSubmissionPhase.PENDING ||
                    attempt.status != VideoGenerationAttemptStatus.SUBMITTING || attempt.controlledEvidence != null)) return false
            val old = attempt.controlledEvidence
            val next = VideoControlledAttemptEvidence(stage, output, failure?.take(2_000)?.filterNot(Char::isISOControl), retryable,
                if (stage in setOf(VideoControlledStage.FAILED, VideoControlledStage.CANCELLED)) old?.stage else null)
            if (old != null && old.stage in setOf(VideoControlledStage.COMPLETED,
                    VideoControlledStage.FAILED, VideoControlledStage.CANCELLED)) return old == next && !requirePending
            if (old != null && old.stage.ordinal >= stage.ordinal) return old == next && !requirePending
            val changed = job.copy(attempts = job.attempts.dropLast(1) + attempt.copy(controlledEvidence = next))
            try {
                jobs.compareAndSet(ledger.revision, ledger.copy(revision = Math.addExact(ledger.revision, 1),
                    jobs = ledger.jobs.toMutableList().also { list -> list[index] = changed }))
                return true
            } catch (_: VideoJobConcurrencyException) { /* re-evaluate the claim */ }
        }
        return false
    }

    private fun verifiedOutput(owned: VideoOwnedBackendAttempt, output: VideoBackendOutput): Boolean {
        val root = receiptRoot ?: return false
        return runCatching {
            require(persistedAttempt(owned) != null)
            val request = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString()).jobs
                .single { it.request.id == owned.requestId }.request
            val limit = (request.input as VideoControlledMotionGenerationInput).media.maximumOutputBytes
            val evidence = readReceipt(root.resolve(owned.attemptId).resolve("completion.json"))
            // The receipt is evidence, not authority to choose a different output. In
            // particular, another attempt's bytes under this publication root must
            // never be promoted as the current attempt's completed preview.
            require(evidence.output.backendOutputId == "${owned.attemptId}-preview" &&
                evidence.output.relativePath == "${owned.attemptId}/preview.mp4")
            require(evidence.requestId == owned.requestId && evidence.attemptId == owned.attemptId &&
                evidence.ownershipToken == owned.ownershipToken && evidence.fingerprint == owned.requestFingerprint &&
                evidence.output == VideoControlledOutputEvidence(output.backendOutputId,
                    requireNotNull(output.relativePath), requireNotNull(output.sha256), requireNotNull(output.byteCount)))
            val directory = root.toRealPath()
            require(root.toAbsolutePath().normalize() == directory &&
                root.resolve(owned.attemptId).toRealPath() == directory.resolve(owned.attemptId) &&
                Files.getPosixFilePermissions(root.resolve(owned.attemptId).resolve("completion.json")) ==
                PosixFilePermissions.fromString("r--------"))
            val file = directory.resolve(evidence.output.relativePath).normalize()
            require(file.startsWith(directory) && Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
                evidence.output.byteCount in 1..limit && Files.size(file) == evidence.output.byteCount &&
                sha256(file) == evidence.output.sha256)
            true
        }.getOrDefault(false)
    }

    private fun claimed(owned: VideoOwnedBackendAttempt, allowAcknowledged: Boolean = false): VideoGenerationJobRequest? {
        if (owned.backendId != backendId || owned.providerWorkId != null) return null
        val ledger = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString())
        require(ledger.admissionDomainId == admissionDomainId) { "Another admission domain was returned." }
        val job = ledger.jobs.singleOrNull { it.request.id == owned.requestId } ?: return null
        val attempt = job.attempts.lastOrNull() ?: return null
        val request = job.request
        val input = request.input as? VideoControlledMotionGenerationInput ?: return null
        if (attempt.id != owned.attemptId || attempt.ownershipToken != owned.ownershipToken ||
            !(attempt.submissionPhase == VideoSubmissionPhase.PENDING && attempt.status == VideoGenerationAttemptStatus.SUBMITTING ||
                allowAcknowledged && attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED &&
                attempt.status in setOf(VideoGenerationAttemptStatus.ACTIVE, VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) && attempt.providerWorkId ==
                "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}") ||
            (!allowAcknowledged && attempt.providerWorkId != null) || request.backendId != backendId ||
            request.requestFingerprint != owned.requestFingerprint || request.projectId != input.motion.descriptor.projectId ||
            request.execution !is VideoLocalExecutionPolicy || request.execution != input.media.execution ||
            request.requestFingerprint != controlledMotionRequestFingerprint(backendId, input, request.modelRequirements, request.maximumAttempts)
        ) return null
        return request
    }

    private class Work {
        val cancellation = VideoMediaProcessCancellation()
        @Volatile var result: VideoBackendObservation? = null
    }

    companion object {
        @Serializable
        private data class ControlledCompletionReceipt(val requestId: String, val attemptId: String,
            val ownershipToken: String, val fingerprint: String, val output: VideoControlledOutputEvidence)

        private fun readReceipt(path: Path): ControlledCompletionReceipt {
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && Files.size(path) in 1..4096)
            return Json.decodeFromString(Files.readString(path))
        }

        private fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { stream ->
                val bytes = ByteArray(64 * 1024)
                while (true) { val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        const val BACKEND_ID = "controlled-media"

        private fun markEncoding(jobs: VideoJobPersistence, domain: String, owned: VideoOwnedBackendAttempt, clock: Clock) {
            repeat(64) {
                val ledger = jobs.loadOrCreate(domain, Instant.now(clock).toString())
                val index = ledger.jobs.indexOfFirst { it.request.id == owned.requestId }
                require(index >= 0) { "Controlled request disappeared before encoding." }
                val job = ledger.jobs[index]
                val attempt = job.attempts.last()
                require(job.request.backendId == BACKEND_ID && job.request.requestFingerprint == owned.requestFingerprint &&
                    attempt.id == owned.attemptId && attempt.ownershipToken == owned.ownershipToken && !attempt.status.isTerminal &&
                    attempt.controlledEvidence?.stage == VideoControlledStage.RENDERING) {
                    "Controlled claim changed before encoding; do not publish."
                }
                val updated = job.copy(attempts = job.attempts.dropLast(1) + attempt.copy(
                    controlledEvidence = VideoControlledAttemptEvidence(VideoControlledStage.ENCODING)))
                try {
                    jobs.compareAndSet(ledger.revision, ledger.copy(revision = Math.addExact(ledger.revision, 1),
                        jobs = ledger.jobs.toMutableList().also { items -> items[index] = updated }))
                    return
                } catch (_: VideoJobConcurrencyException) { /* revalidate the owner */ }
            }
            error("Controlled encoding checkpoint stayed busy; do not publish.")
        }

        private fun renderClaimed(request: VideoGenerationJobRequest, owned: VideoOwnedBackendAttempt,
            cancellation: VideoMediaProcessCancellation, projectRoot: Path, outputRoot: Path,
            renderer: VideoMotionRenderer, hostFreeMemoryBytes: () -> Long,
            runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
            onEncoding: () -> Unit): VideoBackendObservation {
            val input = request.input as VideoControlledMotionGenerationInput
            val media = input.media
            val motion = input.motion
            val binding = motion.descriptor.runtime
            val start = System.nanoTime()
            val limit = Duration.ofMillis(media.execution.wallClockLimitMillis).toNanos()
            fun remaining(): Duration = Duration.ofNanos((limit - (System.nanoTime() - start)).coerceAtLeast(0))
            fun realDirectory(path: Path): Path {
                val absolute = path.toAbsolutePath().normalize()
                require(Files.isDirectory(absolute, NOFOLLOW_LINKS) && !Files.isSymbolicLink(absolute) && absolute.toRealPath() == absolute) {
                    "Controlled media directory must already exist without a symlink: $absolute"
                }
                return absolute
            }
            if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Controlled attempt cancelled before setup.")
            val project = realDirectory(projectRoot)
            val output = realDirectory(outputRoot)
            require(output.startsWith(project) && output != project) { "Controlled output must be inside its owned project, separate from sources." }
            require(remaining() > Duration.ZERO) { "Controlled attempt deadline expired before staging." }
            val directory = output.resolve(owned.attemptId)
            Files.createDirectory(directory) // create-new; retain this private attempt evidence on every failure
            val descriptor = directory.resolve("request.json")
            Files.writeString(descriptor, motion.descriptor.requestJson, CREATE_NEW)
            fun checkTime() {
                if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Controlled attempt cancelled.")
                if (remaining() <= Duration.ZERO) throw VideoMediaProcessException(VideoMediaProcessFailure.TIMED_OUT, "Controlled attempt deadline expired.")
            }
            fun hash(path: Path): String {
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { stream ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) { checkTime(); val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
            // The renderer checks its own pins at every chunk. These two media-only pins
            // must also be checked before native setup, not merely trusted from the ledger.
            for (pin in listOf(binding.ffprobe, binding.mediaManifest)) {
                val path = Path.of(requireNotNull(pin.ownedPath))
                require(path.isAbsolute && path.normalize() == path && Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(path) && hash(path) == pin.sha256) { "Controlled media pin changed: ${pin.id}" }
            }
            fun usedBytes(): Long {
                var total = 0L
                Files.walk(directory).use { entries -> entries.forEach { file ->
                    require(!Files.isSymbolicLink(file)) { "Symlink in controlled frame staging." }
                    if (Files.isRegularFile(file, NOFOLLOW_LINKS)) total = Math.addExact(total, Files.size(file))
                } }
                return total
            }
            fun budget() {
                val staged = usedBytes()
                if (staged > media.maximumStagingBytes) throw VideoMediaProcessException(
                    VideoMediaProcessFailure.OUTPUT_LIMIT, "Controlled frame staging limit exceeded.")
                // Admit the *entire* remaining staging allowance, not only the output.
                // As frames are written their bytes leave usableSpace, so subtract them
                // from the still-unspent staging allowance to avoid double counting.
                val reserve = Math.addExact(Math.addExact(media.minimumFreeDiskBytes, media.maximumOutputBytes),
                    media.maximumStagingBytes - staged)
                if (Files.getFileStore(directory).usableSpace < reserve) throw VideoMediaProcessException(
                    VideoMediaProcessFailure.OUTPUT_LIMIT, "Controlled staging/output disk admission requires $reserve free bytes.")
            }
            // Admit host capacity before launching; the native supervisor below also
            // measures aggregate RSS of the owned process group and terminates on growth.
            val freeMemory = hostFreeMemoryBytes()
            if (freeMemory < 0 || freeMemory < media.execution.memoryLimitBytes) throw VideoMediaProcessException(
                VideoMediaProcessFailure.OUTPUT_LIMIT,
                "Controlled memory admission requires ${media.execution.memoryLimitBytes} free bytes; available capacity is ${if (freeMemory < 0) "unknown" else freeMemory}.")
            budget() // admission before the first pinned native probe or renderer invocation
            val runtime = VideoMotionRuntime(binding.node, binding.compositor, binding.scenery,
                binding.canvasManifest, binding.canvasArtifacts, binding.ffmpeg, binding.expectedCanvasVersion)
            // Native children can fill a disk before a chunk returns. Watch the owned tree
            // throughout execution, and cancel only this attempt's process token on breach.
            val violation = AtomicReference<Throwable?>()
            val monitor = Thread {
                while (!Thread.currentThread().isInterrupted && !cancellation.isCancelled()) {
                    try {
                        budget()
                        if (remaining() <= Duration.ZERO) throw VideoMediaProcessException(
                            VideoMediaProcessFailure.TIMED_OUT, "Controlled attempt deadline expired.")
                        Thread.sleep(50)
                    } catch (_: InterruptedException) { break }
                    catch (error: Throwable) { violation.compareAndSet(null, error); cancellation.cancel(); break }
                }
            }.apply { name = "video-controlled-budget"; isDaemon = true; start() }
            try {
                val result = renderer.render(VideoMotionRenderRequest(runtime, project, descriptor, directory,
                    motion.startFrame, motion.endFrameExclusive, Duration.ofMillis(media.execution.wallClockLimitMillis),
                    ::remaining, ::budget, media.execution.memoryLimitBytes), cancellation)
                violation.get()?.let { throw it }
                require(result.invocations.first().startFrame == motion.startFrame &&
                    result.invocations.last().endFrameExclusive == motion.endFrameExclusive &&
                    result.invocations.zipWithNext().all { (a, b) -> a.endFrameExclusive == b.startFrame } &&
                    result.invocations.all { it.endFrameExclusive - it.startFrame in 1..300 }) { "Controlled render chunks are not contiguous." }
                onEncoding() // Persist the render-to-encode boundary before consuming frame receipts.
                val preview = encodePreview(result, request, directory, binding.ffmpeg, binding.ffprobe,
                    ::hash, ::budget, ::checkTime, ::remaining, cancellation, run = runProcess, beforePublication = {
                        // Stop the sampler before the publication point: joining it (and observing
                        // its last failure) must not be a fallible operation after publication.
                        monitor.interrupt()
                        monitor.join()
                        violation.get()?.let { throw it }
                    })
                val evidence = VideoControlledOutputEvidence("${owned.attemptId}-preview",
                    "${owned.attemptId}/preview.mp4", preview.sha256, preview.bytes)
                val receipt = ControlledCompletionReceipt(request.id, owned.attemptId, owned.ownershipToken,
                    request.requestFingerprint, evidence)
                // Create-new and sealed: crash after publication but before this receipt is
                // uncertain; once present, the output is independently recoverable.
                val receiptPath = directory.resolve("completion.json")
                Files.writeString(receiptPath, Json.encodeToString(receipt), CREATE_NEW)
                Files.setPosixFilePermissions(receiptPath, PosixFilePermissions.fromString("r--------"))
                return VideoBackendObservation.Completed(null, VideoBackendOutput(
                    evidence.backendOutputId, evidence.relativePath, evidence.sha256, evidence.byteCount))
            } catch (error: Exception) {
                throw (violation.get() ?: error)
            } finally {
                if (monitor.isAlive) {
                    monitor.interrupt()
                    monitor.join()
                }
            }
        }

        /** Sequence copies are private, numbered from zero for image2 and checked against the
         * absolute receipts at consumption. Only one PNG is buffered by the streaming copy. */
        internal data class ValidatedPreview(val path: Path, val sha256: String, val bytes: Long)

        internal fun encodePreview(result: VideoMotionRenderResult, request: VideoGenerationJobRequest,
            directory: Path, ffmpeg: VideoGenerationDependencyPin, ffprobe: VideoGenerationDependencyPin,
            hash: (Path) -> String, budget: () -> Unit, check: () -> Unit, remaining: () -> Duration,
            cancellation: VideoMediaProcessCancellation,
            run: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult = { p, c -> VideoMediaProcess().run(p, c) },
            beforePublication: () -> Unit = {},
            publishLink: (Path, Path) -> Unit = { destination, source -> Files.createLink(destination, source); Unit },
            beforeCommit: () -> Unit = {},
        ): ValidatedPreview {
            val motion = (request.input as VideoControlledMotionGenerationInput).motion
            val media = (request.input as VideoControlledMotionGenerationInput).media
            val descriptor = Json.parseToJsonElement(motion.descriptor.requestJson).jsonObject
            val canvas = descriptor.getValue("canvas").jsonObject
            val width = canvas.getValue("width").jsonPrimitive.content.toInt()
            val height = canvas.getValue("height").jsonPrimitive.content.toInt()
            require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0 &&
                descriptor.getValue("fps").jsonPrimitive.content == "30") { "Controlled H.264 viewport/cadence is unsupported." }
            val count = Math.subtractExact(motion.endFrameExclusive, motion.startFrame)
            require(count > 0 && count <= Int.MAX_VALUE)
            val frameDirectory = Files.createDirectory(directory.resolve("encode-frames"))
            var absolute = motion.startFrame
            var number = 0
            for (invocation in result.invocations) {
                check()
                require(invocation.startFrame == absolute && invocation.endFrameExclusive > absolute &&
                    invocation.endFrameExclusive - absolute <= 300 && invocation.outputDirectory.parent == directory &&
                    invocation.receipt.parent == invocation.outputDirectory &&
                    Files.isRegularFile(invocation.receipt, NOFOLLOW_LINKS) && !Files.isSymbolicLink(invocation.receipt)) {
                    "Controlled receipt order or location changed before encoding."
                }
                val receipt = Json.parseToJsonElement(Files.readString(invocation.receipt)).jsonObject
                val range = receipt.getValue("frameRange").jsonObject
                val frames = receipt.getValue("frames").jsonArray
                require(range.getValue("startFrame").jsonPrimitive.content.toLong() == absolute &&
                    range.getValue("frameCount").jsonPrimitive.content.toLong() == invocation.endFrameExclusive - absolute &&
                    frames.size.toLong() == invocation.endFrameExclusive - absolute) { "Controlled receipt range changed before encoding." }
                for (record in frames) {
                    check()
                    val frame = record.jsonObject
                    require(frame.getValue("frame").jsonPrimitive.content.toLong() == absolute) { "Controlled frame order changed before encoding." }
                    val name = "frame-${absolute.toString().padStart(8, '0')}.png"
                    require(frame.getValue("file").jsonPrimitive.content == name) { "Controlled frame name changed before encoding." }
                    val source = invocation.outputDirectory.resolve(name)
                    require(Files.isRegularFile(source, NOFOLLOW_LINKS) && !Files.isSymbolicLink(source) &&
                        hash(source) == frame.getValue("sha256").jsonPrimitive.content) { "Controlled PNG changed before encoding: $absolute" }
                    val target = frameDirectory.resolve("frame-${number.toString().padStart(8, '0')}.png")
                    Files.copy(source, target) // absent destination, no overwrite
                    require(hash(target) == frame.getValue("sha256").jsonPrimitive.content && hash(source) == hash(target)) {
                        "Controlled PNG changed during encoding staging: $absolute"
                    }
                    budget()
                    absolute++
                    number++
                }
            }
            require(absolute == motion.endFrameExclusive && number.toLong() == count) { "Controlled sequence has missing frames." }
            val ffmpegPath = Path.of(requireNotNull(ffmpeg.ownedPath))
            val ffprobePath = Path.of(requireNotNull(ffprobe.ownedPath))
            fun invoke(pin: VideoGenerationDependencyPin, path: Path, label: String, arguments: List<String>): VideoMediaProcessResult {
                check(); budget()
                val time = remaining()
                if (time <= Duration.ZERO) throw VideoMediaProcessException(VideoMediaProcessFailure.TIMED_OUT, "Controlled attempt deadline expired before $label.")
                return run(VideoMediaProcessRequest(path, pin.sha256, arguments, directory.resolve(label), time,
                    memoryLimitBytes = media.execution.memoryLimitBytes,
                    environment = mapOf("LC_ALL" to "C", "LANG" to "C")), cancellation).also { check(); budget() }
            }
            val version = invoke(ffmpeg, ffmpegPath, "encode-version", listOf("-hide_banner", "-version")).stdout.text
            val options = version.split(Regex("\\s+")).map { it.trim('\'', '"') }
            fun enabled(kind: String, name: String) = options.any { option ->
                option.startsWith("--enable-$kind=") && name in option.substringAfter('=').split(',').map { it.trim('\'', '"') }
            }
            require(version.lineSequence().firstOrNull()?.startsWith("ffmpeg version 9.0.1") == true &&
                enabled("demuxer", "image2") && enabled("decoder", "png") &&
                enabled("encoder", "h264_videotoolbox") && enabled("muxer", "mp4") && enabled("protocol", "file")) {
                "Selected pinned FFmpeg build lacks controlled image2/H.264 capability."
            }
            // Check runtime registration as well as the pinned build options: no concat or PATH fallback.
            for ((label, flag, component) in listOf(Triple("demuxers", "-demuxers", "image2"),
                Triple("encoders", "-encoders", "h264_videotoolbox"), Triple("muxers", "-muxers", "mp4"))) {
                val listing = invoke(ffmpeg, ffmpegPath, "encode-$label", listOf("-hide_banner", flag)).stdout.text
                require(Regex("(?m)^\\s*[A-Z. ]*\\s+$component\\s", RegexOption.IGNORE_CASE).containsMatchIn(listing)) {
                    "Selected pinned FFmpeg lacks required $component $label capability."
                }
            }
            // image2 opens numbered paths asynchronously. Freeze independent copies before
            // launch: neither the renderer's writable source nor the encoder can write the
            // private sequence. The directory also forbids replacement of numbered paths.
            // This is an owned/trusted process boundary, not a sandbox for a hostile same-UID
            // program that deliberately changes permissions on our files.
            for (n in 0 until number) {
                val frame = frameDirectory.resolve("frame-${n.toString().padStart(8, '0')}.png")
                Files.setPosixFilePermissions(frame, PosixFilePermissions.fromString("r--------"))
            }
            Files.setPosixFilePermissions(frameDirectory, PosixFilePermissions.fromString("r-x------"))
            // Verify sources and frozen copies before and after native consumption. The
            // permission seal prevents even transient in-place changes during consumption.
            fun verifySequence() {
                var consuming = motion.startFrame
                for (invocation in result.invocations) {
                    check()
                    require(invocation.startFrame == consuming && invocation.endFrameExclusive > consuming &&
                        invocation.endFrameExclusive - consuming <= 300 && invocation.outputDirectory.parent == directory &&
                        invocation.receipt.parent == invocation.outputDirectory &&
                        Files.isRegularFile(invocation.receipt, NOFOLLOW_LINKS) && !Files.isSymbolicLink(invocation.receipt)) {
                        "Controlled receipt changed at image2 consumption."
                    }
                    val receipt = Json.parseToJsonElement(Files.readString(invocation.receipt)).jsonObject
                    val range = receipt.getValue("frameRange").jsonObject
                    require(range.getValue("startFrame").jsonPrimitive.content.toLong() == consuming &&
                        range.getValue("frameCount").jsonPrimitive.content.toLong() == invocation.endFrameExclusive - consuming &&
                        receipt.getValue("frames").jsonArray.size.toLong() == invocation.endFrameExclusive - consuming)
                    for (record in receipt.getValue("frames").jsonArray) {
                        check()
                        val frame = record.jsonObject
                        val n = frame.getValue("frame").jsonPrimitive.content.toLong()
                        require(n == consuming && frame.getValue("file").jsonPrimitive.content ==
                            "frame-${n.toString().padStart(8, '0')}.png") { "Controlled receipt was reordered at image2 consumption." }
                        val expected = frame.getValue("sha256").jsonPrimitive.content
                        val source = invocation.outputDirectory.resolve("frame-${n.toString().padStart(8, '0')}.png")
                        val target = frameDirectory.resolve("frame-${(n - motion.startFrame).toString().padStart(8, '0')}.png")
                        require(Files.isRegularFile(source, NOFOLLOW_LINKS) && !Files.isSymbolicLink(source) &&
                            Files.isRegularFile(target, NOFOLLOW_LINKS) && !Files.isSymbolicLink(target) &&
                            Files.getPosixFilePermissions(target) == PosixFilePermissions.fromString("r--------") &&
                            Files.getPosixFilePermissions(frameDirectory) == PosixFilePermissions.fromString("r-x------") &&
                            hash(source) == expected && hash(target) == expected) {
                            "Controlled frame digest changed at image2 consumption: $n"
                        }
                        consuming++
                    }
                }
                require(consuming == motion.endFrameExclusive)
            }
            verifySequence()
            val encoded = invoke(ffmpeg, ffmpegPath, "encode-video", listOf("-nostdin", "-hide_banner", "-v", "error", "-xerror",
                "-protocol_whitelist", "file,pipe", "-f", "image2", "-framerate", "30", "-start_number", "0",
                "-i", frameDirectory.resolve("frame-%08d.png").toString(), "-frames:v", count.toString(),
                "-map", "0:v:0", "-an", "-sn", "-dn", "-c:v", "h264_videotoolbox", "-allow_sw", "0",
                "-pix_fmt", "yuv420p", "-aspect", "$width:$height", "-movflags", "+faststart", "-f", "mp4", "encoded.mp4"))
                .workingDirectory.resolve("encoded.mp4")
            verifySequence() // Detect changed sources and violations of the frozen image2 sequence.
            require(Files.isRegularFile(encoded, NOFOLLOW_LINKS) && !Files.isSymbolicLink(encoded) &&
                Files.size(encoded) in 1..media.maximumOutputBytes) { "Controlled encode exceeded output limit or produced no MP4." }
            val probe = invoke(ffprobe, ffprobePath, "encode-probe", listOf("-v", "error", "-protocol_whitelist", "file,pipe",
                "-count_frames", "-show_entries", "stream=codec_type,codec_name,width,height,avg_frame_rate,sample_aspect_ratio,nb_read_frames",
                "-of", "json", encoded.toString())).stdout.text
            val streams = Json.parseToJsonElement(probe).jsonObject.getValue("streams").jsonArray
            require(streams.size == 1) { "Controlled MP4 must contain exactly one stream." }
            val video = streams.single().jsonObject
            fun field(name: String) = video.getValue(name).jsonPrimitive.content
            require(field("codec_type") == "video" && field("codec_name") == "h264" &&
                field("width") == width.toString() && field("height") == height.toString() &&
                field("sample_aspect_ratio") == "1:1" && field("avg_frame_rate") == "30/1" &&
                field("nb_read_frames") == count.toString()) { "Controlled MP4 codec, geometry, cadence or decoded count mismatch." }
            invoke(ffmpeg, ffmpegPath, "encode-full-decode", listOf("-nostdin", "-hide_banner", "-v", "error", "-xerror",
                "-protocol_whitelist", "file,pipe", "-i", encoded.toString(), "-map", "0:v:0", "-an", "-sn", "-dn",
                "-c:v", "rawvideo", "-f", "null", "-"))
            verifySequence() // Also catch changes during probe/full decode, not only encode.
            check(); budget()
            val bytes = Files.size(encoded)
            require(bytes in 1..media.maximumOutputBytes &&
                Files.getFileStore(directory).usableSpace >= media.minimumFreeDiskBytes) { "Controlled output/disk limit exceeded." }
            val digest = hash(encoded) // bounded, cancellation-aware read before publication
            check(); budget()
            // Copy to a distinct inode: the retained writable encoder output must never
            // be an alias of the published bytes. Seal the independent copy before linking.
            val publicationSource = directory.resolve("publication-source.mp4")
            Files.copy(encoded, publicationSource)
            require(Files.isRegularFile(publicationSource, NOFOLLOW_LINKS) && !Files.isSymbolicLink(publicationSource) &&
                Files.size(publicationSource) == bytes && hash(publicationSource) == digest &&
                Files.size(encoded) == bytes && hash(encoded) == digest) { "Controlled MP4 changed before publication." }
            Files.setPosixFilePermissions(publicationSource, PosixFilePermissions.fromString("r--------"))
            check(); budget()
            beforePublication() // stop/join the attempt monitor before the irreversible link
            check(); budget()
            require(Files.getPosixFilePermissions(publicationSource) == PosixFilePermissions.fromString("r--------") &&
                Files.size(publicationSource) == bytes && hash(publicationSource) == digest &&
                Files.size(encoded) == bytes && hash(encoded) == digest) { "Controlled MP4 changed before publication." }
            check(); budget()
            // Cancellation wins before the lock or follows create-new publication.
            // The source is a distinct, sealed inode, never the writable encoder output.
            val preview = ValidatedPreview(directory.resolve("preview.mp4"), digest, bytes)
            beforeCommit() // cancellation here must win, even after the final budget/check
            return cancellation.publishIfActive {
                publishLink(preview.path, publicationSource)
                // After publication cleanup cannot invalidate a committed result. Normally
                // remove the temporary alias so only preview.mp4 names this sealed inode.
                runCatching { Files.delete(publicationSource) }
                preview
            }
        }

        private val nativeSlot = Semaphore(1)
        private val workers = ConcurrentHashMap<String, Work>()
        private val pool = Executors.newFixedThreadPool(1) { task ->
            Thread(task, "video-controlled-media").apply { isDaemon = true }
        }
    }
}
