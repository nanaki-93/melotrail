package app.melotrail.video.adapter

import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsafeVideoProjectLocationException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectNotFoundException
import app.melotrail.video.domain.VideoTakeMeasurementRecord
import app.melotrail.video.domain.VideoVersionedId
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** A take identity, not a media URL or a caller-controlled file path. */
data class VideoPreviewOpen(
    val projectRoot: Path,
    val takeId: VideoVersionedId,
    val toolsDirectory: Path,
    /** An absent child of an existing, private preview scratch parent, outside the project. */
    val scratchDirectory: Path,
)

enum class VideoPreviewOpenFailure { INVALID_REQUEST, PROJECT, TAKE_NOT_FOUND, UNSAFE_TAKE, UNSUPPORTED_TAKE, TOOLS, CANCELLED }

sealed interface VideoPreviewOpenResult {
    /** Admission is not a frame. Decoding and timing are separate, bounded operations. */
    class Admitted internal constructor(
        val projectId: String,
        val projectRevision: Long,
        val takeId: VideoVersionedId,
        val artifact: Path,
        val measurement: VideoTakeMeasurementRecord,
        internal val tools: VideoPreviewToolPins,
        internal val scratch: Path,
    ) : VideoPreviewOpenResult

    data class Rejected(val failure: VideoPreviewOpenFailure, val message: String, val nextAction: String) : VideoPreviewOpenResult
}

/** A caller-owned ARGB8888 frame. Pixel reads never expose the backing array.
 * Closing drops the buffer and its budget claim, and invalidates further reads. */
class VideoPreviewImage internal constructor(
    val width: Int,
    val height: Int,
    pixels: IntArray,
    private val budget: VideoPreviewBufferBudget,
    private val bytes: Long,
) : AutoCloseable {
    val pixelFormat: String = "ARGB8888"
    private var ownedPixels: IntArray? = pixels

    @Synchronized fun argbAt(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        return checkNotNull(ownedPixels) { "Preview frame was released." }[y * width + x]
    }

    @Synchronized override fun close() {
        if (ownedPixels != null) {
            ownedPixels = null
            budget.releaseFrame(bytes)
        }
    }
}

class VideoPreviewImageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Shared per-decoder accounting. A decode reserves both the ImageIO raster and output, so
 * concurrent decodes and retained images cannot exceed eight pixel buffers or 64 MiB. */
internal class VideoPreviewBufferBudget {
    private var encoded = 0L
    private var pixelBytes = 0L
    private var pixelBuffers = 0

    @Synchronized fun reserveEncoded(bytes: Long) {
        if (bytes <= 0 || bytes > MAX_ENCODED_BYTES - encoded) throw VideoPreviewImageException("Preview encoded staging exceeds 64 MiB.")
        encoded += bytes
    }

    @Synchronized fun releaseEncoded(bytes: Long) {
        require(bytes >= 0 && bytes <= encoded) { "Preview encoded budget was released twice." }
        encoded -= bytes
    }

    /** Exchange the process's full output allowance for the measured files, atomically.
     * File bytes remain charged until cleanup, alongside the reader's separate byte array. */
    @Synchronized fun measuredStaging(allowance: Long, bytes: Long) {
        require(allowance >= 0 && allowance <= encoded)
        if (bytes < 0 || bytes > allowance) throw VideoPreviewImageException("Preview encoded staging exceeds 64 MiB.")
        encoded -= allowance - bytes
    }

    @Synchronized fun reserveDecode(bytes: Long) {
        if (bytes <= 0 || pixelBuffers > 6 || bytes > (MAX_RESIDENT_BYTES - pixelBytes) / 2) {
            throw VideoPreviewImageException("Preview pixel buffers exceed eight frames or 64 MiB.")
        }
        pixelBytes += bytes * 2
        pixelBuffers += 2
    }

    @Synchronized fun releaseDecode(bytes: Long, published: Boolean) {
        pixelBytes -= if (published) bytes else bytes * 2
        pixelBuffers -= if (published) 1 else 2
    }

    @Synchronized fun releaseFrame(bytes: Long) {
        pixelBytes -= bytes
        pixelBuffers--
    }

    internal companion object {
        const val MAX_ENCODED_BYTES = 64L * 1024 * 1024
        const val MAX_RESIDENT_BYTES = 64L * 1024 * 1024
        const val MAX_FRAME_BYTES = 8L * 1024 * 1024
    }
}

/** Read-only preview admission. Constructing this adapter neither touches the filesystem nor
 * launches a native process. Call [open] on a worker, never on the UI thread. */
class VideoPreviewDecoder internal constructor(
    private val projects: VideoProjectStore,
    private val runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
) {
    constructor(projects: VideoProjectStore) : this(projects, { request, cancellation -> VideoMediaProcess().run(request, cancellation) })

    private val extractionLock = Any()
    private var ownershipUncertain = false

    // Tool checks, timing scans and PNG extraction share the same process gate. A failed
    // supervision cannot be followed by another launch, even from a different entry point.
    private fun ownedProcess(request: VideoMediaProcessRequest, cancellation: VideoMediaProcessCancellation): VideoMediaProcessResult =
        synchronized(extractionLock) {
            checkOwnership()
            try {
                if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Preview process cancelled before launch.")
                runProcess(request, cancellation)
            } catch (error: VideoMediaProcessException) {
                if (!teardownConfirmed(error)) ownershipUncertain = true
                throw error
            }
        }

    private fun teardownConfirmed(error: VideoMediaProcessException) =
        error.failure != VideoMediaProcessFailure.SUPERVISION_FAILED &&
            error.failure != VideoMediaProcessFailure.UNEXPECTED_DESCENDANTS &&
            !error.message.orEmpty().contains("Cleanup incomplete") && error.suppressed.isEmpty()

    /** Called after the session worker has joined; never report clean close with an orphaned child. */
    fun requireConfirmedTeardown() = synchronized(extractionLock) { checkOwnership() }

    private fun checkOwnership() {
        if (ownershipUncertain) throw VideoPreviewImageException(
            "Preview process or staging cleanup is unconfirmed; inspect the private scratch child before creating a new decoder.")
    }

    private val probe = VideoMediaProbe(::ownedProcess)

    /** Exact timestamp of a zero-based decoded frame. This scans bounded probe windows rather
     * than guessing a seek position from average FPS. No pixel buffer is allocated here. */
    fun presentationAt(admitted: VideoPreviewOpenResult.Admitted, frameIndex: Long,
                       cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()): VideoPreviewPresentation =
        synchronized(extractionLock) {
            checkOwnership()
            probe.previewPresentation(admitted.artifact, admitted.measurement, admitted.tools, admitted.scratch, frameIndex, cancellation)
        }
    private val imageFiles = VideoImageFiles()
    private val buffers = VideoPreviewBufferBudget()

    /** Decodes a single already-extracted PNG, without claiming it represents a take frame. */
    internal fun decodeExtractedPng(path: Path): VideoPreviewImage = imageFiles.decodePreviewPng(path, buffers)

    data class Frame(val presentation: VideoPreviewPresentation, val image: VideoPreviewImage) : AutoCloseable {
        override fun close() = image.close()
    }

    /** One owned FFmpeg invocation per window. Select uses decoded-frame numbers, not a
     * rounded seek timestamp. Work is synchronous: the session must call this on its worker. */
    fun extractWindow(admitted: VideoPreviewOpenResult.Admitted, first: Long, requested: Int = 1,
                      cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()): List<Frame> {
        val count = admitted.measurement.decodedFrameCount
        require(requested in 1..4 && first >= 0 && first < count && requested.toLong() <= count - first) {
            "Preview window must contain 1..4 decoded frames inside 0..${count - 1}."
        }
        return synchronized(extractionLock) {
            checkOwnership()
            if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Preview extraction cancelled.")
            if (!Files.isDirectory(admitted.scratch, NOFOLLOW_LINKS) || admitted.scratch.toRealPath() != admitted.scratch) {
                throw VideoPreviewImageException("Preview scratch changed; select a fresh private scratch child.")
            }
            val identity = sourceIdentity(admitted, cancellation)
            // Timing metadata is bounded independently. It must agree with the decoded-frame
            // ordering used by select; no guessed rate or approximate seek identifies a frame.
            val times = (0 until requested).map { presentationAt(admitted, first + it, cancellation) }
            sourceIdentity(admitted, cancellation, identity)
            buffers.reserveEncoded(VideoPreviewBufferBudget.MAX_ENCODED_BYTES)
            var reserved = VideoPreviewBufferBudget.MAX_ENCODED_BYTES
            val job = admitted.scratch.resolve("preview-frames-${UUID.randomUUID()}")
            var processConfirmed = false
            var operationFailure: Exception? = null
            val frames = mutableListOf<Frame>()
            try {
                val last = first + requested - 1
                val result = ownedProcess(VideoMediaProcessRequest(
                    admitted.tools.ffmpeg, admitted.tools.ffmpegSha256,
                    listOf("-hide_banner", "-nostdin", "-v", "error", "-protocol_whitelist", "file,pipe",
                        "-i", admitted.artifact.toString(), "-map", "0:v:0", "-an", "-sn", "-dn",
                        "-vf", "select=between(n\\,$first\\,$last)", "-vsync", "0",
                        "-frames:v", requested.toString(), "-c:v", "png", "-f", "image2",
                        // image2 may apply -fs to each output file, not the aggregate window.
                        "-fs", (VideoPreviewBufferBudget.MAX_ENCODED_BYTES / requested).toString(),
                        job.resolve("frame-%03d.png").toString()),
                    job, Duration.ofSeconds(30), maxStdoutBytes = 8192, maxStderrBytes = 8192,
                    environment = mapOf("LC_ALL" to "C", "LANG" to "C"),
                    memoryLimitBytes = 512L * 1024 * 1024,
                ), cancellation)
                processConfirmed = true
                if (result.stdout.truncated || result.stdout.totalBytes > 8192 || result.stderr.truncated) {
                    throw VideoPreviewImageException("Preview process diagnostics exceeded their bound.")
                }
                // Do not follow unexpected links or read files outside this fresh owned job.
                val expected = (1..requested).map { "frame-%03d.png".format(it) }.toSet()
                var entries = 0
                Files.newDirectoryStream(job).use { directory ->
                    for (entry in directory) {
                        entries++
                        if (entries > requested || entry.fileName.toString() !in expected) {
                            throw VideoPreviewImageException("Preview extraction produced missing or unexpected frame files.")
                        }
                    }
                }
                if (entries != requested) throw VideoPreviewImageException("Preview extraction produced missing frame files.")
                var staged = 0L
                for (number in 1..requested) {
                    val file = job.resolve("frame-%03d.png".format(number))
                    if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) throw VideoPreviewImageException("Preview frame is not a regular owned file.")
                    val size = Files.size(file)
                    if (size <= 0 || size > VideoPreviewBufferBudget.MAX_ENCODED_BYTES - staged) {
                        throw VideoPreviewImageException("Preview encoded staging exceeds 64 MiB.")
                    }
                    staged += size
                }
                buffers.measuredStaging(reserved, staged)
                reserved = staged
                for (number in 1..requested) {
                    val file = job.resolve("frame-%03d.png".format(number))
                    // The file and the in-flight reader array are distinct encoded storage.
                    // Both remain charged until the reader returns and the file is deleted.
                    val image = decodeExtractedPng(file)
                    if (image.width != admitted.measurement.width || image.height != admitted.measurement.height) {
                        image.close()
                        throw VideoPreviewImageException("Preview frame geometry differs from the measured take.")
                    }
                    frames += Frame(times[number - 1], image)
                }
                sourceIdentity(admitted, cancellation, identity)
                frames.toList()
            } catch (error: Exception) {
                operationFailure = error
                frames.forEach { it.close() }
                // A suppressed cleanup failure means the child may still own this directory.
                if (error is VideoMediaProcessException) {
                    processConfirmed = teardownConfirmed(error)
                    if (!processConfirmed) ownershipUncertain = true
                }
                throw error
            } finally {
                var cleaned = false
                try {
                    if (processConfirmed) {
                        deleteOwnedFrames(job, requested)
                        cleaned = true
                    }
                } catch (error: IOException) {
                    ownershipUncertain = true
                    frames.forEach { it.close() }
                    val failure = VideoPreviewImageException("Could not safely complete preview staging cleanup: ${error.message}; inspect the private scratch child.", error)
                    operationFailure?.let { failure.addSuppressed(it) }
                    throw failure
                } finally {
                    // Keep the full allowance charged when cleanup is incomplete. Even unknown
                    // entries larger than the allowance cannot permit another launch on this decoder.
                    if (cleaned) buffers.releaseEncoded(reserved)
                }
            }
        }
    }

    private data class SourceIdentity(val key: Any?, val size: Long, val hash: String)

    private fun sourceIdentity(admitted: VideoPreviewOpenResult.Admitted, cancellation: VideoMediaProcessCancellation,
                               expected: SourceIdentity? = null): SourceIdentity {
        val path = admitted.artifact
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || path.toRealPath() != path) {
            throw VideoPreviewImageException("Published take is missing or unsafe; reopen the project.")
        }
        val before = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Preview source check cancelled.")
                val read = input.read(chunk)
                if (read < 0) break
                digest.update(chunk, 0, read)
            }
        }
        val after = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val identity = SourceIdentity(after.fileKey(), after.size(), hash)
        if (before.fileKey() != after.fileKey() || before.size() != after.size() || before.lastModifiedTime() != after.lastModifiedTime() ||
            hash != admitted.measurement.sha256 || after.size() != admitted.measurement.bytes ||
            (expected != null && identity != expected)) {
            throw VideoPreviewImageException("Published take changed during preview; restore the original bytes and reopen.")
        }
        return identity
    }

    private fun deleteOwnedFrames(job: Path, requested: Int) {
        if (!Files.exists(job, NOFOLLOW_LINKS)) return // no process directory was created
        if (!Files.isDirectory(job, NOFOLLOW_LINKS) || job.parent.toRealPath() != job.parent) {
            throw VideoPreviewImageException("Owned preview directory changed; nothing was removed.")
        }
        // Only these exact outputs are ours. Inspect at most one remaining entry; never
        // enumerate an attacker-sized directory or retain its names in memory.
        for (number in 1..requested) {
            val file = job.resolve("frame-%03d.png".format(number))
            if (Files.isRegularFile(file, NOFOLLOW_LINKS)) Files.delete(file)
        }
        Files.newDirectoryStream(job).use { entries ->
            val iterator = entries.iterator()
            if (iterator.hasNext()) {
                val entry = iterator.next()
                val oversized = Files.isRegularFile(entry, NOFOLLOW_LINKS) &&
                    Files.size(entry) > VideoPreviewBufferBudget.MAX_ENCODED_BYTES
                throw VideoPreviewImageException(
                    if (oversized) "Unexpected oversized entry preserved; retained content cannot be bounded safely."
                    else "Unexpected entry preserved; retained content cannot be bounded safely.")
            }
        }
        Files.delete(job)
    }

    fun open(request: VideoPreviewOpen, cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()): VideoPreviewOpenResult {
        fun reject(kind: VideoPreviewOpenFailure, message: String, remedy: String) =
            VideoPreviewOpenResult.Rejected(kind, message, remedy)
        if (cancellation.isCancelled()) return reject(VideoPreviewOpenFailure.CANCELLED, "Preview open was cancelled.", "Open the take again when ready.")
        val project = try {
            projects.open(request.projectRoot)
        } catch (error: VideoProjectNotFoundException) {
            return reject(VideoPreviewOpenFailure.PROJECT, error.message.orEmpty(), "Open a folder containing a current Video project.")
        } catch (error: UnsupportedVideoProjectException) {
            return reject(VideoPreviewOpenFailure.PROJECT, error.message.orEmpty(), "Open with a compatible Melotrail version; the project was not changed.")
        } catch (error: InvalidVideoProjectException) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Restore the changed or missing project artifact from a known-good copy.")
        } catch (error: UnsafeVideoProjectLocationException) {
            return reject(VideoPreviewOpenFailure.PROJECT, error.message.orEmpty(), "Choose Video storage outside protected MIDI roots.")
        } catch (error: IOException) {
            return reject(VideoPreviewOpenFailure.PROJECT, error.message.orEmpty(), "Check project storage and permissions, then reopen.")
        }
        val take = project.takeVersions.singleOrNull { it.id == request.takeId }
            ?: return reject(VideoPreviewOpenFailure.TAKE_NOT_FOUND, "Take ${request.takeId.id} v${request.takeId.version} is not persisted in this project.",
                "Choose a persisted take from the reopened project.")
        val measurement = take.publishedMeasurement
            ?: return reject(VideoPreviewOpenFailure.UNSUPPORTED_TAKE, "Take has no published measurement.", "Import a measured, silent take before previewing.")
        if (take.artifact.sha256 != measurement.sha256 || measurement.audioStreamCount != 0 ||
            measurement.videoStreamCount != 1 || measurement.otherStreamCount != 0) {
            return reject(VideoPreviewOpenFailure.UNSUPPORTED_TAKE, "Take is not a measured silent published video.",
                "Import a validated video-only take; preview will not strip audio.")
        }
        // The selected FFmpeg distribution decodes H.264; do not guess another codec or
        // allocate pixels for unbounded geometry. The count and time base are published facts.
        val pixels = measurement.width.toLong() * measurement.height.toLong()
        if (measurement.videoCodec != "h264" || measurement.width !in 1..8192 || measurement.height !in 1..8192 ||
            pixels > 64L * 1024 * 1024 / 4 || measurement.decodedFrameCount <= 0 ||
            measurement.videoTimeBase.numerator <= 0 || measurement.videoTimeBase.denominator <= 0 ||
            measurement.videoDurationPts <= 0 || measurement.frameRate.numerator <= 0 ||
            measurement.sampleAspectRatio.numerator <= 0) {
            return reject(VideoPreviewOpenFailure.UNSUPPORTED_TAKE, "Take codec, geometry, frame count or timing is unsupported for bounded preview.",
                "Import a measured H.264 take with valid dimensions and decoded frames.")
        }
        val artifact = try {
            projects.resolveArtifact(request.projectRoot, take.artifact)
        } catch (error: InvalidVideoProjectException) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Restore the published take bytes from a known-good copy.")
        } catch (error: IOException) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Check take storage and reopen the project.")
        }
        val artifactBytes = try { Files.size(artifact) }
            catch (error: IOException) { return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Restore the missing published take.") }
        if (artifactBytes != measurement.bytes) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, "Published take size differs from its measurement.", "Restore the original published take.")
        }
        // No scratch writes, including tool-version jobs, may enter project storage.
        val scratch = request.scratchDirectory
        if (!scratch.isAbsolute || scratch.any { it.toString() == "." || it.toString() == ".." } ||
            Files.exists(scratch, NOFOLLOW_LINKS) || scratch.parent == null ||
            !Files.isDirectory(scratch.parent, NOFOLLOW_LINKS) || Files.isSymbolicLink(scratch.parent)) {
            return reject(VideoPreviewOpenFailure.INVALID_REQUEST, "Preview scratch must be an absent child of an existing non-symlink directory.",
                "Select a fresh private scratch child outside the project.")
        }
        val scratchPath = try { scratch.parent.toRealPath().resolve(scratch.fileName.toString()) }
            catch (error: IOException) { return reject(VideoPreviewOpenFailure.INVALID_REQUEST, error.message.orEmpty(), "Choose an accessible preview scratch parent.") }
        val projectPath = try { request.projectRoot.toRealPath() }
            catch (error: IOException) { return reject(VideoPreviewOpenFailure.PROJECT, error.message.orEmpty(), "Reopen the Video project from its original folder.") }
        if (scratchPath.startsWith(projectPath) || projectPath.startsWith(scratchPath)) {
            return reject(VideoPreviewOpenFailure.INVALID_REQUEST, "Preview scratch overlaps the Video project.", "Choose separate preview scratch storage.")
        }
        val pins = try {
            synchronized(extractionLock) {
                checkOwnership()
                probe.verifyPreviewTools(request.toolsDirectory, scratchPath, cancellation)
            }
        } catch (error: VideoMediaProbeException) {
            return reject(VideoPreviewOpenFailure.TOOLS, error.message.orEmpty(), "Select the separately installed, hash-pinned FFmpeg 9.0.1 tools and a fresh scratch child; no PATH fallback is used.")
        } catch (error: VideoMediaProcessException) {
            return reject(if (error.failure == VideoMediaProcessFailure.CANCELLED) VideoPreviewOpenFailure.CANCELLED else VideoPreviewOpenFailure.TOOLS,
                error.message.orEmpty(), "Check the pinned media tools and process supervision before retrying.")
        } catch (error: IOException) {
            return reject(VideoPreviewOpenFailure.TOOLS, error.message.orEmpty(), "Check the selected media tools and scratch permissions.")
        }
        if (cancellation.isCancelled()) return reject(VideoPreviewOpenFailure.CANCELLED, "Preview open was cancelled.", "Open the take again when ready.")
        // Tool checks can take time: authenticate media again before giving it to the decoder.
        val recheckedArtifact = try { projects.resolveArtifact(request.projectRoot, take.artifact) }
        catch (error: InvalidVideoProjectException) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Restore the original published take and reopen.")
        } catch (error: IOException) {
            return reject(VideoPreviewOpenFailure.UNSAFE_TAKE, error.message.orEmpty(), "Check take storage and reopen.")
        }
        if (recheckedArtifact != artifact) return reject(VideoPreviewOpenFailure.UNSAFE_TAKE,
            "Video project artifact location changed during preview admission.", "Reopen the original Video project and take.")
        return VideoPreviewOpenResult.Admitted(project.id, project.revision, take.id, artifact, measurement, pins, scratchPath)
    }
}
