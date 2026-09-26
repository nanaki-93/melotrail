package app.melotrail.video.adapter

import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsafeVideoProjectLocationException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectNotFoundException
import app.melotrail.video.domain.VideoTakeMeasurementRecord
import app.melotrail.video.domain.VideoVersionedId
import java.io.IOException
import java.nio.file.Files
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

    @Synchronized fun releaseEncoded(bytes: Long) { encoded -= bytes }

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
    runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
) {
    constructor(projects: VideoProjectStore) : this(projects, { request, cancellation -> VideoMediaProcess().run(request, cancellation) })

    private val probe = VideoMediaProbe(runProcess)
    private val imageFiles = VideoImageFiles()
    private val buffers = VideoPreviewBufferBudget()

    /** Decodes a single already-extracted PNG, without claiming it represents a take frame.
     * Frame identity and presentation timing are established by the later extraction boundary. */
    internal fun decodeExtractedPng(path: Path): VideoPreviewImage = imageFiles.decodePreviewPng(path, buffers)

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
            probe.verifyPreviewTools(request.toolsDirectory, scratchPath, cancellation)
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
        return VideoPreviewOpenResult.Admitted(project.id, project.revision, take.id, artifact, measurement, pins)
    }
}
