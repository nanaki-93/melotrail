package app.melotrail.video.adapter

import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsafeVideoProjectLocationException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectAlreadyExistsException
import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectNotFoundException
import app.melotrail.video.application.VideoProjectPersistence
import app.melotrail.video.application.VideoProjectSaveException
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoPreparedSceneRecord
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoProjectControlPaths
import app.melotrail.video.domain.VideoTakeRecord
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
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
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

/** Filesystem adapter for an independent, current-schema Video project. */
class VideoProjectStore(
    protectedMidiRoots: Collection<Path>,
    private val atomicWriteObserver: VideoAtomicWriteObserver = VideoAtomicWriteObserver.NONE,
    private val takeCopyObserver: (Path) -> Unit = {},
    internal val changeTime: (Path) -> FileTime = { path ->
        Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS) as FileTime
    },
) : VideoProjectPersistence {
    private val protectedRoots = protectedMidiRoots.map { it.toAbsolutePath() }

    init {
        require(protectedRoots.isNotEmpty()) {
            "At least one MIDI project or export root must be protected before video storage is used"
        }
    }

    override fun create(projectRoot: Path, project: VideoProject): VideoProject {
        require(project.revision == 0L) { "A new video project must start at revision zero" }
        val plannedRoot = validateLocation(projectRoot)
        if (Files.exists(plannedRoot, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(plannedRoot)) {
            throw UnsafeVideoProjectLocationException("The video project root is not a directory: $plannedRoot")
        }
        try {
            Files.createDirectories(plannedRoot)
        } catch (error: IOException) {
            throw UnsafeVideoProjectLocationException("The video project root could not be created: $plannedRoot", error)
        }
        val root = existingSafeRoot(plannedRoot)
        return withWriteLock(root) {
            val target = projectFile(root)
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw VideoProjectAlreadyExistsException("A video project already exists in this folder.")
            }
            verifyArtifacts(root, project)
            publishDocument(root, target, project, replace = false)
            project
        }
    }

    override fun open(projectRoot: Path): VideoProject {
        val plannedRoot = validateLocation(projectRoot)
        if (!Files.isDirectory(plannedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw VideoProjectNotFoundException("No current video project was found in this folder.")
        }
        val root = existingSafeRoot(plannedRoot)
        val project = readCurrentProject(root)
        verifyArtifacts(root, project)
        return project
    }

    override fun save(projectRoot: Path, expectedRevision: Long, project: VideoProject): VideoProject {
        require(expectedRevision >= 0L) { "Expected video project revision must not be negative" }
        val root = existingSafeRoot(validateLocation(projectRoot))
        return withWriteLock(root) {
            val current = readCurrentProject(root)
            if (current.revision != expectedRevision) {
                throw VideoProjectConcurrencyException(
                    "The video project changed from revision $expectedRevision to ${current.revision}.",
                )
            }
            val requiredRevision = try {
                Math.addExact(expectedRevision, 1L)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("The video project revision cannot advance safely", error)
            }
            require(project.revision == requiredRevision) {
                "The saved video project must advance revision $expectedRevision to $requiredRevision"
            }
            requireAppendOnlyHistory(current, project)
            verifyArtifacts(root, project)
            publishDocument(root, projectFile(root), project, replace = true)
            project
        }
    }

    /** Stage independent bytes before acquiring the document lock. The temporary is owned by
     * this import and never becomes an externally writable alias of a published take. */
    internal fun stageTake(projectRoot: Path, source: Path, digest: String,
                           cancellation: VideoMediaProcessCancellation): Path {
        val root = existingSafeRoot(validateLocation(projectRoot))
        val stage = Files.createTempFile(root, ".take-import-", ".tmp")
        try {
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(stage).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) throw cancelledTake()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) throw cancelledTake()
            require(sha256(stage) == digest && sha256(source) == digest) { "Take bytes changed during staging." }
            return stage
        } catch (error: Exception) {
            Files.deleteIfExists(stage)
            throw error
        }
    }

    /** Recheck the admission and publish the reference under the same cross-instance lock as CAS.
     * A failed document write leaves only unreferenced, immutable owned evidence. */
    internal fun publishImportedTake(projectRoot: Path, expectedRevision: Long, take: VideoTakeRecord,
                                     stage: Path, cancellation: VideoMediaProcessCancellation,
                                     recheckJob: () -> Unit = {},
                                     contentPaths: List<Path> = emptyList(),
                                     recheck: (VideoProject) -> Unit): Pair<VideoProject, VideoTakeRecord> {
        val root = existingSafeRoot(validateLocation(projectRoot))
        // Expensive verification and the independent publication copy must not serialize
        // unrelated project writers. The lock below only guards the final identity/CAS.
        val inspected = open(root)
        // The witness can only certify the filesystem it lives on. Unix file keys
        // expose the device ID; reject other volumes and unknown key formats.
        val device = contentStamp(projectFile(root)).device()
        (contentPaths + listOf(stage)).forEach { path ->
            require(contentStamp(path).device() == device) {
                "Take input is on a different volume: $path; place consumed pins on the project volume."
            }
        }
        requireChangeTimeSupport(root)
        recheck(inspected)
        require(Files.isRegularFile(stage, LinkOption.NOFOLLOW_LINKS) && stage.parent == root &&
            !Files.isSymbolicLink(stage) && Files.size(stage) == take.publishedMeasurement!!.bytes &&
            sha256(stage) == take.artifact.sha256) { "Staged take bytes changed." }
        val target = root.resolve(take.artifact.relativePath)
        require(target.startsWith(root)) { "Take path escapes project." }
        val parent = requireNotNull(target.parent)
        requireNoSymlinkComponents(root, parent)
        Files.createDirectories(parent)
        requireNoSymlinkComponents(root, parent)
        require(parent.toRealPath().startsWith(root)) { "Take path escapes project." }
        val temporary = Files.createTempFile(parent, ".take-publish-", ".tmp")
        try {
            takeCopyObserver(temporary)
            Files.newInputStream(stage).use { input ->
                Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) throw cancelledTake()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) throw cancelledTake()
            require(Files.size(temporary) == take.publishedMeasurement.bytes &&
                sha256(temporary) == take.artifact.sha256) { "Take bytes changed during publication staging." }
            Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"))
            // Pin every mutable input around its full digest verification. Unix change time
            // cannot be restored by resetting mtime, unlike the metadata-only check it
            // replaces. Require it again at commit without reading gigabytes under the lock.
            val inputs = (contentPaths + listOf(temporary)).distinct()
            val before = inputs.associateWith(::contentStamp)
            recheck(inspected)
            require(contentStamp(temporary) == before.getValue(temporary) &&
                sha256(temporary) == take.artifact.sha256) { "Staged take bytes changed." }
            val existingStamp = if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                val stamp = contentStamp(target)
                verifyArtifact(root, take.artifact)
                stamp
            } else null
            val existing = existingStamp != null
            val stamps = (inputs + if (existing) listOf(target) else emptyList()).associateWith(::contentStamp)
            require(existingStamp == stamps[target]) { "Take artifact changed during verification." }
            require(before.all { (path, stamp) -> stamps[path] == stamp }) { "Take inputs changed during verification." }
            return withWriteLock(root) {
            val current = readCurrentProject(root)
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) throw cancelledTake()
            recheckJob()
            require(stamps.all { (path, stamp) -> contentStamp(path) == stamp }) {
                "Take inputs changed before publication."
            }
            // No source or artifact digest is computed under the document lock.
            val previous = current.takeVersions.find { it.provenance?.let { p ->
                val incoming = take.provenance!!
                p.projectId == incoming.projectId && p.requestId == incoming.requestId &&
                    p.attemptId == incoming.attemptId && p.outputId == incoming.outputId &&
                    p.executableFingerprint == incoming.executableFingerprint
            } == true }
            // A reused identity must agree on all media and provenance, not merely on a path.
            if (previous != null) {
                require(previous.sourceMeasurement == take.sourceMeasurement &&
                    previous.publishedMeasurement == take.publishedMeasurement &&
                    previous.conversion == take.conversion && previous.provenance == take.provenance) {
                    "Import identity collides with changed take facts."
                }
                require(previous.artifact == take.artifact) {
                    "Import identity collides with changed published bytes or destination."
                }
                if (current != inspected || !existing) {
                    throw VideoProjectConcurrencyException("Take appeared during publication; refresh and retry.")
                }
                return@withWriteLock current to previous
            }
            if (current != inspected || current.revision != expectedRevision) throw VideoProjectConcurrencyException(
                "The video project changed from revision $expectedRevision to ${current.revision}.")
            require(current.takeVersions.none { it.id == take.id }) { "Take ID already exists." }
            // Cancellation and project publication share the same synchronization point.
            cancellation.publishIfActive {
                if (Thread.currentThread().isInterrupted) throw cancelledTake()
                requireNoSymlinkComponents(root, parent)
                require(parent.toRealPath().startsWith(root)) { "Take path escapes project." }
                if (existing) {
                    require(Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Verified orphan disappeared." }
                } else {
                    require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Take destination appeared after verification." }
                    publishNewFile(temporary, target)
                }
                val replacement = current.copy(takeVersions = current.takeVersions + take,
                    revision = Math.addExact(expectedRevision, 1L))
                requireAppendOnlyHistory(current, replacement)
                publishDocument(root, projectFile(root), replacement, replace = true)
                replacement to take
            }
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    /** Optimistic content seal for an owned local Unix filesystem: full digests are taken
     * outside the document lock; at commit inode identity and kernel change time detect
     * replacement and even same-size writes followed by an mtime reset. The project and
     * publication paths must be on a filesystem where each such write advances ctime.
     * This is not protection against privileged filesystem tampering or external writers
     * racing after commit. Unsupported/coarse timestamps fail closed; use an owned local
     * Unix volume with reliable file keys and change times for take publication. */
    private data class ContentStamp(val key: Any, val size: Long, val mtime: FileTime, val ctime: FileTime) {
        fun device(): String = requireNotNull(Regex("dev=([^,)]+)").find(key.toString())?.groupValues?.get(1)) {
            "Take publication needs Unix device and inode identity; use an owned local Unix volume."
        }
    }

    private fun contentStamp(path: Path): ContentStamp {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) {
            "Verified take input is missing or unsafe: $path"
        }
        try {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val key = requireNotNull(attrs.fileKey()) { "No file identity for $path" }
            return ContentStamp(key, attrs.size(), attrs.lastModifiedTime(), changeTime(path))
        } catch (error: UnsupportedOperationException) {
            throw IllegalArgumentException("Take publication needs a local Unix volume with reliable inode identity and change time: $path", error)
        } catch (error: IOException) {
            throw IllegalArgumentException("Take input vanished or its Unix identity/change time is unavailable: $path; use an owned local Unix volume.", error)
        } catch (error: ClassCastException) {
            throw IllegalArgumentException("Take publication needs a local Unix volume with reliable inode identity and change time: $path", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Take publication needs a local Unix volume with reliable inode identity and change time: $path", error)
        }
    }

    /** A same-size write followed by an mtime restore must still be observable on this volume.
     * Calibrate outside the lock, before trusting metadata instead of bulk hashing at commit. */
    private fun requireChangeTimeSupport(root: Path) {
        val witness = Files.createTempFile(root, ".take-stamp-", ".tmp")
        try {
            Files.write(witness, byteArrayOf(1))
            val first = contentStamp(witness)
            Files.write(witness, byteArrayOf(2))
            Files.setLastModifiedTime(witness, first.mtime)
            val second = contentStamp(witness)
            require(first.key == second.key && first.size == second.size &&
                first.mtime == second.mtime && first.ctime != second.ctime) {
                "Take publication needs a local Unix volume with change time that detects restored-mtime writes; choose another project volume."
            }
        } finally { Files.deleteIfExists(witness) }
    }

    private fun cancelledTake() = VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED,
        "Take import was cancelled before publication.")

    fun resolveArtifact(projectRoot: Path, artifact: VideoArtifact): Path {
        val root = existingSafeRoot(validateLocation(projectRoot))
        return verifyArtifact(root, artifact)
    }

    /** Copy verified bytes into a previously absent immutable project path. */
    fun copyImmutableArtifact(
        projectRoot: Path, source: Path, relativePath: String, expectedSha256: String,
        cancellation: VideoMediaProcessCancellation? = null,
    ): VideoArtifact {
        fun checkCancelled() {
            if (cancellation?.isCancelled() == true || Thread.currentThread().isInterrupted) {
                throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Take import was cancelled during immutable copy.")
            }
        }
        fun digest(path: Path): String {
            val hash = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            checkCancelled()
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
        checkCancelled()
        val root = existingSafeRoot(validateLocation(projectRoot))
        val artifact = VideoArtifact(relativePath, expectedSha256)
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(source)) { "Take source is missing or unsafe." }
        require(digest(source) == expectedSha256) { "Take source digest does not match its output pin." }
        val target = root.resolve(relativePath).normalize()
        require(target.startsWith(root)) { "Take destination escapes the project root." }
        val parent = requireNotNull(target.parent)
        requireNoSymlinkComponents(root, parent)
        Files.createDirectories(parent)
        requireNoSymlinkComponents(root, parent)
        require(parent.toRealPath().startsWith(root) && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { "Take destination already exists or is unsafe." }
        val temporary = Files.createTempFile(parent, ".take-import-", ".tmp")
        try {
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(digest(temporary) == expectedSha256 && digest(source) == expectedSha256) { "Take bytes changed during immutable copy." }
            checkCancelled()
            publishNewFile(temporary, target)
            require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target) &&
                target.toRealPath().startsWith(root) && digest(target) == expectedSha256) {
                "Published take bytes changed during immutable copy."
            }
            return artifact
        } finally { runCatching { Files.deleteIfExists(temporary) } }
    }

    /**
     * Publishes a prepared-scene descriptor and appends its record under the same
     * project lock used by ordinary saves. The caller owns descriptor encoding;
     * this store continues to own path safety, artifact verification and revision publication.
     */
    internal fun appendPreparedScene(
        projectRoot: Path,
        expectedRevision: Long,
        record: VideoPreparedSceneRecord,
        descriptorBytes: ByteArray,
    ): VideoProject {
        require(expectedRevision >= 0L) { "Expected video project revision must not be negative" }
        require(sha256(descriptorBytes) == record.artifact.sha256) {
            "Prepared-scene descriptor bytes do not match their artifact pin"
        }
        val expectedPath = "prepared-scenes/${record.id.id}/v${record.id.version}/scene.json"
        require(record.artifact.relativePath == expectedPath) {
            "Prepared-scene record does not point to its immutable descriptor path"
        }
        val root = existingSafeRoot(validateLocation(projectRoot))
        return withWriteLock(root) {
            val current = readCurrentProject(root)
            if (current.revision != expectedRevision) {
                throw VideoProjectConcurrencyException(
                    "The video project changed from revision $expectedRevision to ${current.revision}.",
                )
            }
            require(current.preparedSceneVersions.none { it.id == record.id }) {
                "Prepared-scene version ${record.id.id} v${record.id.version} already exists"
            }
            val requiredRevision = try {
                Math.addExact(expectedRevision, 1L)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("The video project revision cannot advance safely", error)
            }
            val replacement = current.copy(
                preparedSceneVersions = current.preparedSceneVersions + record,
                revision = requiredRevision,
            )
            requireAppendOnlyHistory(current, replacement)
            verifyArtifacts(root, current)
            record.consumedArtifacts.forEach { verifyArtifact(root, it) }

            val target = root.resolve(record.artifact.relativePath).normalize()
            if (!target.startsWith(root)) {
                throw InvalidVideoProjectException("Prepared-scene descriptor escapes the video project root")
            }
            val parent = requireNotNull(target.parent)
            requireNoSymlinkComponents(root, parent)
            try {
                Files.createDirectories(parent)
            } catch (error: IOException) {
                throw VideoProjectSaveException(
                    "Prepared-scene descriptor folder could not be created; the project document was preserved.",
                    projectFile(root),
                    null,
                    error,
                )
            }
            requireNoSymlinkComponents(root, parent)
            val realParent = try {
                parent.toRealPath()
            } catch (error: IOException) {
                throw InvalidVideoProjectException("Prepared-scene descriptor folder could not be resolved", error)
            }
            if (!realParent.startsWith(root)) {
                throw InvalidVideoProjectException("Prepared-scene descriptor folder escapes the video project root")
            }

            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyArtifact(root, record.artifact)
            } else {
                val temporary = try {
                    Files.createTempFile(parent, ".prepared-scene-", ".tmp")
                } catch (error: IOException) {
                    throw VideoProjectSaveException(
                        "Prepared-scene descriptor could not be staged; the project document was preserved.",
                        projectFile(root),
                        null,
                        error,
                    )
                }
                try {
                    FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                        val remaining = ByteBuffer.wrap(descriptorBytes)
                        while (remaining.hasRemaining()) channel.write(remaining)
                        channel.force(true)
                    }
                    publishNewFile(temporary, target)
                } catch (error: Exception) {
                    throw VideoProjectSaveException(
                        "Prepared-scene descriptor could not be published; the project document was preserved.",
                        projectFile(root),
                        null,
                        error,
                    )
                } finally {
                    runCatching { Files.deleteIfExists(temporary) }
                }
            }

            // Retain an immutable descriptor if project publication fails. A retry may reuse
            // only the same digest, while the previous project document remains authoritative.
            verifyArtifact(root, record.artifact)
            verifyArtifacts(root, replacement)
            publishDocument(root, projectFile(root), replacement, replace = true)
            replacement
        }
    }

    private fun requireAppendOnlyHistory(current: VideoProject, replacement: VideoProject) {
        require(current.id == replacement.id) { "A save cannot replace the video project identity" }
        require(current.createdAt == replacement.createdAt) { "A save cannot replace the video project creation timestamp" }
        require(current.applicationVersion == replacement.applicationVersion) {
            "A save cannot replace the creating application version"
        }
        require(replacement.referenceVersions.startsWith(current.referenceVersions)) {
            "Reference versions are immutable and append-only"
        }
        require(replacement.lookVersions.startsWith(current.lookVersions)) {
            "Look versions are immutable and append-only"
        }
        require(replacement.preparedSceneVersions.startsWith(current.preparedSceneVersions)) {
            "Prepared-scene versions are immutable and append-only"
        }
        require(replacement.takeVersions.startsWith(current.takeVersions)) {
            "Take versions are immutable and append-only"
        }
        val identities = replacement.takeVersions.map { take ->
            val p = requireNotNull(take.provenance)
            listOf(p.projectId, p.requestId, p.attemptId, p.outputId,
                p.executableFingerprint)
        }
        require(identities.distinct().size == identities.size) { "Import identities must be unique" }
        require(replacement.exportRecords.startsWith(current.exportRecords)) {
            "Export records are immutable and append-only"
        }
    }

    private fun publishDocument(root: Path, target: Path, project: VideoProject, replace: Boolean) {
        val bytes = VideoProjectSchema.encode(project).toByteArray(StandardCharsets.UTF_8)
        require(VideoProjectSchema.decode(bytes.toString(StandardCharsets.UTF_8)) == project) {
            "Video project schema validation changed the project"
        }
        val temporary = Files.createTempFile(root, VideoProjectControlPaths.STAGING_PREFIX, ".tmp")
        var published = false
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val remaining = ByteBuffer.wrap(bytes)
                while (remaining.hasRemaining()) channel.write(remaining)
                channel.force(true)
            }
            atomicWriteObserver.beforePublish(temporary, target)
            val staged = VideoProjectSchema.decode(Files.readString(temporary, StandardCharsets.UTF_8))
            require(staged == project) { "The staged video project changed before publication" }
            if (replace) atomicMoveReplace(temporary, target) else publishNewFile(temporary, target)
            published = true
        } catch (error: Exception) {
            val recovery = retainRecoveryEvidence(temporary, target)
            if (!replace && error is FileAlreadyExistsException) {
                throw VideoProjectAlreadyExistsException("A video project already exists in this folder.")
            }
            throw VideoProjectSaveException(
                "Video project save failed; the last known-good document was preserved.",
                target,
                recovery,
                error,
            )
        } finally {
            // Publication has already succeeded; a leftover owned temporary is not a failed save.
            if (published) runCatching { Files.deleteIfExists(temporary) }
        }
    }

    private fun readCurrentProject(root: Path): VideoProject {
        val target = projectFile(root)
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw VideoProjectNotFoundException("No current video project was found in this folder.")
        }
        requireContainedRegularFile(root, target, "Video project document")
        return when (val inspected = VideoProjectSchema.inspect(Files.readString(target, StandardCharsets.UTF_8))) {
            is VideoProjectDocument.Current -> inspected.project
            is VideoProjectDocument.Unsupported -> throw UnsupportedVideoProjectException(inspected.reason)
            is VideoProjectDocument.Invalid -> throw InvalidVideoProjectException(inspected.reason)
        }
    }

    private fun verifyArtifacts(root: Path, project: VideoProject) {
        project.artifacts().forEach { verifyArtifact(root, it) }
        project.takeVersions.forEach { take ->
            take.publishedMeasurement?.let { measurement ->
                if (Files.size(root.resolve(take.artifact.relativePath)) != measurement.bytes) {
                    throw InvalidVideoProjectException("Published take byte count differs from its measurement")
                }
            }
        }
    }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyArtifact(root: Path, artifact: VideoArtifact): Path {
        val target = root.resolve(artifact.relativePath).normalize()
        if (!target.startsWith(root)) {
            throw InvalidVideoProjectException("Video artifact escapes the project root: ${artifact.relativePath}")
        }
        requireNoSymlinkComponents(root, target)
        requireContainedRegularFile(root, target, "Video artifact '${artifact.relativePath}'")
        if (sha256(target) != artifact.sha256) {
            throw InvalidVideoProjectException("Video artifact digest does not match: ${artifact.relativePath}")
        }
        return target
    }

    private fun requireNoSymlinkComponents(root: Path, target: Path) {
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw InvalidVideoProjectException("Video project paths may not contain symbolic links: $current")
            }
        }
    }

    private fun requireContainedRegularFile(root: Path, target: Path, label: String) {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw InvalidVideoProjectException("$label is missing or is not a regular file")
        }
        val real = try {
            target.toRealPath()
        } catch (error: IOException) {
            throw InvalidVideoProjectException("$label could not be resolved", error)
        }
        if (!real.startsWith(root)) {
            throw InvalidVideoProjectException("$label escapes the video project root")
        }
    }

    private fun validateLocation(projectRoot: Path): Path {
        val candidate = canonicalFuturePath(projectRoot)
        val conflicts = protectedRoots.map(::canonicalFuturePath).firstOrNull { protected ->
            candidate.startsWith(protected) || protected.startsWith(candidate)
        }
        if (conflicts != null) {
            throw UnsafeVideoProjectLocationException(
                "Video storage '$candidate' overlaps protected MIDI storage '$conflicts'.",
            )
        }
        findMidiMarker(candidate)?.let { marker ->
            throw UnsafeVideoProjectLocationException(
                "Video storage is inside a MIDI project identified by '$marker'.",
            )
        }
        return candidate
    }

    private fun canonicalFuturePath(path: Path): Path {
        // Resolve the existing prefix with filesystem semantics before handling the missing suffix.
        // Lexical normalization (including Path.relativize) can erase a symlink followed by `..`.
        val absolute = path.toAbsolutePath()
        var ancestor: Path? = absolute
        val missingSegments = ArrayDeque<Path>()
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor.fileName?.let { missingSegments.addFirst(it) }
            ancestor = ancestor.parent
        }
        val existing = ancestor ?: throw UnsafeVideoProjectLocationException("The storage path has no resolvable ancestor: $absolute")
        val realAncestor = try {
            existing.toRealPath()
        } catch (error: IOException) {
            throw UnsafeVideoProjectLocationException("The storage path has an unresolved symbolic link: $absolute", error)
        }
        if (missingSegments.any { it.toString() == "." || it.toString() == ".." }) {
            throw UnsafeVideoProjectLocationException("The storage path traverses an unresolved parent: $absolute")
        }
        return missingSegments.fold(realAncestor) { resolved, segment -> resolved.resolve(segment) }
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

    private fun existingSafeRoot(path: Path): Path {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw UnsafeVideoProjectLocationException("The video project root is missing or not a directory: $path")
        }
        val root = try {
            path.toRealPath()
        } catch (error: IOException) {
            throw UnsafeVideoProjectLocationException("The video project root could not be resolved: $path", error)
        }
        validateLocation(root)
        return root
    }

    private fun projectFile(root: Path): Path = root.resolve(PROJECT_FILE)

    private fun <T> withWriteLock(root: Path, action: () -> T): T {
        val lockPath = root.resolve(LOCK_FILE)
        val monitor = JVM_LOCKS.computeIfAbsent(lockPath) { Any() }
        return synchronized(monitor) {
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) &&
                (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(lockPath))
            ) {
                throw UnsafeVideoProjectLocationException("The video project lock path is unsafe: $lockPath")
            }
            FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                val lock = channel.lock()
                try {
                    action()
                } finally {
                    lock.release()
                }
            }
        }
    }

    private fun retainRecoveryEvidence(temporary: Path, target: Path): Path? {
        if (!Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) return null
        val recovery = target.resolveSibling("${VideoProjectControlPaths.RECOVERY_PREFIX}${UUID.randomUUID()}.json")
        return try {
            publishNewFile(temporary, recovery)
            runCatching { Files.deleteIfExists(temporary) }
            recovery
        } catch (_: Exception) {
            temporary
        }
    }

    companion object {
        const val PROJECT_FILE = VideoProjectControlPaths.DOCUMENT
        const val MIDI_PROJECT_FILE = "project.json"
        private const val LOCK_FILE = VideoProjectControlPaths.LOCK
        private val JVM_LOCKS = ConcurrentHashMap<Path, Any>()
    }
}

fun interface VideoAtomicWriteObserver {
    fun beforePublish(temporary: Path, target: Path)

    companion object {
        val NONE = VideoAtomicWriteObserver { _, _ -> }
    }
}

private object VideoProjectSchema {
    const val SCHEMA = "melotrail-video-project"
    const val VERSION = 3

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(project: VideoProject): String = json.encodeToString(VideoProjectDocumentDto(SCHEMA, VERSION, project))

    fun decode(document: String): VideoProject = when (val inspected = inspect(document)) {
        is VideoProjectDocument.Current -> inspected.project
        is VideoProjectDocument.Unsupported -> throw UnsupportedVideoProjectException(inspected.reason)
        is VideoProjectDocument.Invalid -> throw InvalidVideoProjectException(inspected.reason)
    }

    fun inspect(document: String): VideoProjectDocument {
        val root = try {
            json.parseToJsonElement(document).jsonObject
        } catch (error: Exception) {
            return VideoProjectDocument.Invalid("Video project document is not valid JSON: ${error.message ?: error.javaClass.simpleName}")
        }
        val schema = try {
            root["schema"]?.jsonPrimitive?.contentOrNull
        } catch (error: Exception) {
            return VideoProjectDocument.Invalid("Video project schema discriminator is invalid")
        }
        val version = try {
            root["version"]?.jsonPrimitive?.intOrNull
        } catch (error: Exception) {
            return VideoProjectDocument.Invalid("Video project version discriminator is invalid")
        }
        if (schema != SCHEMA || version != VERSION) {
            return VideoProjectDocument.Unsupported(
                "Unsupported video project schema '${schema ?: "missing"}' version '${version ?: "missing"}'.",
            )
        }
        return try {
            VideoProjectDocument.Current(json.decodeFromString<VideoProjectDocumentDto>(document).project)
        } catch (error: Exception) {
            VideoProjectDocument.Invalid(
                "Invalid video project v$VERSION document: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }
}

private sealed interface VideoProjectDocument {
    data class Current(val project: VideoProject) : VideoProjectDocument
    data class Unsupported(val reason: String) : VideoProjectDocument
    data class Invalid(val reason: String) : VideoProjectDocument
}

@Serializable
private data class VideoProjectDocumentDto(val schema: String, val version: Int, val project: VideoProject)

private fun <T> List<T>.startsWith(prefix: List<T>): Boolean = size >= prefix.size && take(prefix.size) == prefix

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

private fun publishNewFile(source: Path, target: Path) {
    // A hard link publishes complete staged bytes with an exclusive directory entry. ATOMIC_MOVE
    // may replace an existing target even without REPLACE_EXISTING. Unsupported links fail closed.
    Files.createLink(target, source)
}

private fun atomicMoveReplace(source: Path, target: Path) {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
