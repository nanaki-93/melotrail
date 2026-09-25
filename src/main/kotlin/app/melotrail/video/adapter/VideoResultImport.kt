package app.melotrail.video.adapter

import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoGenerationOutput
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import java.time.Duration
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoTakeMediaFactsRecord
import app.melotrail.video.domain.VideoVersionedId
import java.math.BigInteger
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
    private val comfyOutputRoot: Path? = null,
    private val protectedMidiRoots: Collection<Path> = emptyList(),
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
        val source = resolvePinnedOutput(session.root, "controlled-local", output, cancellation)
        verifyPins(input.motion.preparedPins, session.root, cancellation)
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
            listOf(validation.source, verified).all { measured ->
                measured.sampleAspectRatio == VideoMediaRational(1, 1) &&
                    BigInteger.valueOf(measured.videoDurationPts) * BigInteger.valueOf(measured.videoTimeBase.numerator) *
                    BigInteger.valueOf(descriptor.fps.toLong()) ==
                    BigInteger.valueOf(measured.decodedFrameCount) * BigInteger.valueOf(measured.videoTimeBase.denominator)
            } && verified.audioStreamCount == 0) {
            "Measured media facts do not match persisted controlled motion settings."
        }
        val facts = VideoTakeMediaFacts(verified.decodedFrameCount,
            verified.frameRate.numerator.toDouble() / verified.frameRate.denominator,
            validation.source.width, validation.source.height, verified.width, verified.height, validation.conversion.name)
        require(Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) { "Output changed during media validation." }
        verifyPins(input.motion.preparedPins, session.root, cancellation)

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

    /** The backend ID and the ledger's pinned relative output select one configured publication root.
     * This does not admit a ComfyUI take; that route remains disconnected until flat-I2V admission. */
    internal fun resolvePinnedOutput(
        projectRoot: Path, backendId: String, output: VideoGenerationOutput,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
    ): Path {
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        val relative = VideoArtifact(requireNotNull(output.relativePath), requireNotNull(output.sha256)).relativePath
        // Opening checks the project schema, artifacts and MIDI exclusions without writing anything.
        projects.open(projectRoot)
        val project = projectRoot.toRealPath()
        val configured = when (backendId) {
            "controlled-local" -> requireNotNull(controlledOutputRoot) { "Controlled output publication root is not configured." }
            LocalVideoBackend.BACKEND_ID -> requireNotNull(comfyOutputRoot) { "ComfyUI publication root is not configured." }
            else -> throw IllegalArgumentException("Output backend has no configured publication root: $backendId")
        }
        require(configured.isAbsolute && configured.none { it.toString() in setOf(".", "..") }) {
            "Publication root must be absolute without traversal."
        }
        val root = configured.normalize()
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root) {
            "Output publication root is missing or unsafe."
        }
        when (backendId) {
            "controlled-local" -> require(root.startsWith(project) && root != project) {
                "Controlled output root must be an owned project subtree."
            }
            LocalVideoBackend.BACKEND_ID -> {
                require(!root.startsWith(project) && !project.startsWith(root) && protectedMidiRoots.isNotEmpty()) {
                    "ComfyUI output root must be separate from project and configured MIDI roots."
                }
                protectedMidiRoots.forEach { protected ->
                    val midi = protected.toAbsolutePath().toRealPath()
                    require(!root.startsWith(midi) && !midi.startsWith(root)) {
                        "ComfyUI output root overlaps protected MIDI storage."
                    }
                }
                // A publication root nested in an unlisted MIDI project is not safe either.
                var ancestor: Path? = root
                while (ancestor != null) {
                    require(!Files.exists(ancestor.resolve(VideoProjectStore.MIDI_PROJECT_FILE), NOFOLLOW_LINKS)) {
                        "ComfyUI output root is inside a protected MIDI project."
                    }
                    ancestor = ancestor.parent
                }
            }
        }
        val source = root.resolve(relative)
        require(source.startsWith(root)) { "Output path escapes its publication root." }
        var component = root
        root.relativize(source).forEach { part ->
            component = component.resolve(part)
            require(!Files.isSymbolicLink(component)) { "Output path contains a symbolic link." }
        }
        require(Files.isRegularFile(source, NOFOLLOW_LINKS) && source.toRealPath() == source &&
            Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) {
            "Completed output is unsafe or bytes no longer match the durable job pin."
        }
        return source
    }

    private fun verifyPins(pins: List<VideoGenerationDependencyPin>, projectRoot: Path,
                           cancellation: VideoMediaProcessCancellation) {
        val root = projectRoot.toRealPath()
        pins.forEach { pin ->
            require(pin.id.startsWith("prepared-")) { "Only persisted prepared pins can be verified as project artifacts." }
            val path = Path.of(requireNotNull(pin.ownedPath) { "Consumed pin has no owned path: ${pin.id}" })
            require(path.isAbsolute && path.none { it.toString() in setOf(".", "..") } &&
                Files.isRegularFile(path, NOFOLLOW_LINKS) && path.toRealPath() == path &&
                sha256(path, cancellation) == pin.sha256) { "Consumed input changed or is unsafe: ${pin.id}" }
            require(path.startsWith(root)) { "Prepared input is outside its project: ${pin.id}" }
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
