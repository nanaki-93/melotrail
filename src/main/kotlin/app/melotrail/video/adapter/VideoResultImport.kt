package app.melotrail.video.adapter

import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoGenerationOutput
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
) {
    fun import(
        session: VideoProjectSession,
        expectedRevision: Long,
        output: VideoGenerationOutput,
        probeRequest: VideoMediaProbeRequest,
        lookId: VideoVersionedId?,
        facts: VideoTakeMediaFacts,
    ): VideoImportedTake {
        require(session.project.revision == expectedRevision) { "Project revision changed before take import." }
        require(output.relativePath != null && output.sha256 != null && output.byteCount != null) { "Completed job output has no immutable file pin." }
        val source = projects.resolveArtifact(session.root, VideoArtifact(output.relativePath, output.sha256))
        require(Files.size(source) == output.byteCount && sha256(source) == output.sha256) { "Completed output bytes no longer match the durable job pin." }
        require(probeRequest.input.toAbsolutePath().normalize() == source) { "Media validation must inspect the exact durable job output." }
        val (verified, validatedOutput) = mediaProbe.validateTake(probeRequest)
        val validatedDigest = sha256(validatedOutput)
        val validatedSize = Files.size(validatedOutput)
        require(validatedSize > 0L) { "Validated take output is empty." }
        require(verified.decodedFrameCount == facts.frameCount && verified.frameRate == facts.frameRate &&
            verified.width == facts.nativeWidth && verified.height == facts.nativeHeight && verified.audioStreamCount == 0) {
            "Measured media facts do not match the fully decoded silent output."
        }
        require(Files.size(source) == output.byteCount && sha256(source) == output.sha256) { "Output changed during media validation." }

        val current = projects.open(session.root)
        if (current.revision != expectedRevision || current.id != session.project.id) {
            throw VideoProjectConcurrencyException("Video project changed while the take was being validated.")
        }
        val id = VideoVersionedId(idFactory(), 1)
        require(current.takeVersions.none { it.id.id == id.id }) { "Take ID already exists; use a new immutable take ID." }
        val relative = "takes/${id.id}/v${id.version}/preview.mp4"
        val artifact = projects.copyImmutableArtifact(session.root, validatedOutput, relative, validatedDigest)
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

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
