package app.melotrail.video.adapter

import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoGenerationOutput
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import java.time.Duration
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoTakeMediaFactsRecord
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Facts measured from the actual decoded take, not the requested render settings. */
data class VideoTakeMediaFacts(
    val frameCount: Long,
    val frameRate: Double,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val conversion: String,
) {
    init {
        require(frameCount > 0 && frameRate.isFinite() && frameRate > 0.0)
        require(nativeWidth > 0 && nativeHeight > 0 && outputWidth > 0 && outputHeight > 0)
        require(conversion.isNotBlank() && conversion.length <= 512)
    }
}

data class VideoImportedTake(val project: VideoProject, val take: VideoTakeRecord, val facts: VideoTakeMediaFacts)

/** Validates then copies backend output to a new immutable take path and saves with CAS. */
class VideoResultImport(
    private val projects: VideoProjectStore,
    private val mediaProbe: VideoMediaProbe,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val idFactory: () -> String = { "take-${UUID.randomUUID()}" },
    private val controlledOutputRoot: Path? = null,
) {
    fun import(
        session: VideoProjectSession,
        expectedRevision: Long,
        output: VideoGenerationOutput,
        input: VideoControlledMotionGenerationInput,
        lookId: VideoVersionedId?,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
    ): VideoImportedTake {
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        require(session.project.revision == expectedRevision) { "Project revision changed before take import." }
        require(output.relativePath != null && output.sha256 != null && output.byteCount != null) { "Completed job output has no immutable file pin." }
        val relative = VideoArtifact(output.relativePath, output.sha256).relativePath
        val ownedRoot = requireNotNull(controlledOutputRoot) { "Controlled output publication root is not configured." }
            .toAbsolutePath().normalize()
        val projectRoot = session.root.toAbsolutePath().normalize()
        require(ownedRoot.startsWith(projectRoot) && ownedRoot != projectRoot &&
            Files.isDirectory(ownedRoot, NOFOLLOW_LINKS) && ownedRoot.toRealPath() == ownedRoot) {
            "Controlled output root must be an owned project subtree."
        }
        val source = ownedRoot.resolve(relative).normalize()
        require(source.startsWith(ownedRoot) && Files.isRegularFile(source, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(source) && source.toRealPath().startsWith(ownedRoot)) {
            "Controlled output path is missing or unsafe."
        }
        require(Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) { "Completed output bytes no longer match the durable job pin." }
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        val runtime = input.motion.descriptor.runtime
        val tools = Path.of(requireNotNull(runtime.mediaManifest.ownedPath)).parent
        val probeRequest = VideoMediaProbeRequest(tools, source,
            session.root.resolve("take-validation-${UUID.randomUUID()}"),
            Duration.ofMillis(minOf(30_000L, input.media.execution.wallClockLimitMillis)))
        val validation = mediaProbe.validateTake(probeRequest, cancellation)
        val verified = validation.published
        val validatedOutput = validation.validatedPath
        val validatedDigest = sha256(validatedOutput, cancellation)
        val validatedSize = Files.size(validatedOutput)
        require(validatedSize > 0L) { "Validated take output is empty." }
        val descriptor = input.motion.descriptor
        require(validation.source.videoCodec == "h264" && verified.videoCodec == "h264" &&
            validation.source.width == descriptor.width && validation.source.height == descriptor.height &&
            verified.width == descriptor.width && verified.height == descriptor.height &&
            validation.source.frameRate == VideoMediaRational(descriptor.fps.toLong(), 1) &&
            verified.frameRate == VideoMediaRational(descriptor.fps.toLong(), 1) &&
            validation.source.decodedFrameCount == input.motion.endFrameExclusive - input.motion.startFrame &&
            verified.decodedFrameCount == validation.source.decodedFrameCount &&
            verified.sampleAspectRatio == VideoMediaRational(1, 1) && verified.audioStreamCount == 0) {
            "Measured media facts do not match persisted controlled motion settings."
        }
        val facts = VideoTakeMediaFacts(verified.decodedFrameCount,
            verified.frameRate.numerator.toDouble() / verified.frameRate.denominator,
            validation.source.width, validation.source.height, verified.width, verified.height, validation.conversion.name)
        require(Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) { "Output changed during media validation." }

        require(!cancellation.isCancelled()) { "Take import was cancelled before publication." }
        val current = projects.open(session.root)
        if (current.revision != expectedRevision || current.id != session.project.id) {
            throw VideoProjectConcurrencyException("Video project changed while the take was being validated.")
        }
        val id = VideoVersionedId(idFactory(), 1)
        require(current.takeVersions.none { it.id.id == id.id }) { "Take ID already exists; use a new immutable take ID." }
        val takePath = "takes/${id.id}/v${id.version}/preview.mp4"
        val artifact = projects.copyImmutableArtifact(session.root, validatedOutput, takePath, validatedDigest, cancellation)
        require(!cancellation.isCancelled() && !Thread.currentThread().isInterrupted) { "Take import was cancelled before publication." }
        val take = VideoTakeRecord(id, artifact, lookId, Instant.now(clock).toString(), VideoTakeMediaFactsRecord(
            facts.frameCount, facts.frameRate, facts.nativeWidth, facts.nativeHeight,
            facts.outputWidth, facts.outputHeight, facts.conversion,
        ))
        val replacement = current.copy(takeVersions = current.takeVersions + take, revision = Math.addExact(expectedRevision, 1L))
        try {
            val saved = projects.save(session.root, expectedRevision, replacement)
            return VideoImportedTake(saved, take, facts)
        } catch (error: Exception) {
            // The immutable bytes may remain as orphaned evidence, but the project and prior takes
            // remain authoritative. Never remove or overwrite an existing artifact.
            throw error
        }
    }

    private fun sha256(path: Path, cancellation: VideoMediaProcessCancellation): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        while (true) {
            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) {
                throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Take import was cancelled while hashing media.")
            }
            val n = input.read(bytes)
            if (n < 0) break
            digest.update(bytes, 0, n)
        }
        if (cancellation.isCancelled() || Thread.currentThread().isInterrupted) {
            throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Take import was cancelled while hashing media.")
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
