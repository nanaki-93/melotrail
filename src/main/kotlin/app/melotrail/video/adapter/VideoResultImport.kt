package app.melotrail.video.adapter

import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoGenerationOutput
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoGenerationInput
import java.time.Duration
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoTakeMeasurementRecord
import app.melotrail.video.domain.VideoTakeRationalRecord
import app.melotrail.video.domain.VideoTakeProvenanceRecord
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoGenerationJob
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoPreparedScene
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
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
    private val flatMediaToolsDirectory: Path? = null,
) {
    fun import(
        session: VideoProjectSession,
        expectedRevision: Long,
        output: VideoGenerationOutput,
        input: VideoGenerationInput,
        request: VideoGenerationJobRequest,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
        currentJob: () -> VideoGenerationJob = { throw IllegalStateException("A durable job refresh is required for publication.") },
    ): VideoImportedTake {
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        require(session.project.revision == expectedRevision) { "Project revision changed before take import." }
        val controlled = input as? VideoControlledMotionGenerationInput
        val flat = input as? VideoClipGenerationInput
        require(request.projectId == session.project.id && request.input == input &&
            ((controlled != null && request.backendId == "controlled-local") ||
                (flat?.primaryPrompt != null && request.backendId == LocalVideoBackend.BACKEND_ID)) &&
            output.attemptId.isNotBlank()) { "Take provenance does not match the verified request." }
        require(output.relativePath != null && output.sha256 != null && output.byteCount != null) { "Completed job output has no immutable file pin." }
        val source = resolvePinnedOutput(session.root, request.backendId, output, cancellation)
        if (controlled != null) verifyPins(controlled.motion.preparedPins, session.root, cancellation)
        else ComfyShortI2VBinding.verify(requireNotNull(flat))
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        val tools = controlled?.motion?.descriptor?.runtime?.mediaManifest?.ownedPath?.let { Path.of(it).parent }
            ?: requireNotNull(flatMediaToolsDirectory) { "Pinned flat media tools directory is not configured." }
        val probeRequest = VideoMediaProbeRequest(tools, source,
            session.root.resolve("take-validation-${UUID.randomUUID()}"),
            Duration.ofMillis(minOf(30_000L, (request.execution as app.melotrail.video.domain.VideoLocalExecutionPolicy).wallClockLimitMillis)))
        val validation = mediaProbe.validateTake(probeRequest, cancellation)
        val verified = validation.published
        val validatedOutput = validation.validatedPath
        val validatedDigest = sha256(validatedOutput, cancellation)
        val validatedSize = Files.size(validatedOutput)
        require(validatedSize > 0L) { "Validated take output is empty." }
        val width = controlled?.motion?.descriptor?.width ?: requireNotNull(flat).width
        val height = controlled?.motion?.descriptor?.height ?: requireNotNull(flat).height
        val fps = controlled?.motion?.descriptor?.fps ?: requireNotNull(flat).framesPerSecond
        val frames = controlled?.motion?.let { it.endFrameExclusive - it.startFrame } ?: 129L
        require(validation.source.videoCodec == "h264" && verified.videoCodec == "h264" &&
            validation.source.width == width && validation.source.height == height &&
            verified.width == width && verified.height == height &&
            validation.source.frameRate == VideoMediaRational(fps.toLong(), 1) &&
            verified.frameRate == VideoMediaRational(fps.toLong(), 1) &&
            validation.source.decodedFrameCount == frames &&
            verified.decodedFrameCount == validation.source.decodedFrameCount &&
            listOf(validation.source, verified).all { measured ->
                measured.sampleAspectRatio == VideoMediaRational(1, 1) &&
                    BigInteger.valueOf(measured.videoDurationPts) * BigInteger.valueOf(measured.videoTimeBase.numerator) *
                    BigInteger.valueOf(fps.toLong()) ==
                    BigInteger.valueOf(measured.decodedFrameCount) * BigInteger.valueOf(measured.videoTimeBase.denominator)
            } && verified.audioStreamCount == 0) {
            "Measured media facts do not match persisted generation settings."
        }
        require(Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) { "Output changed during media validation." }
        if (controlled != null) verifyPins(controlled.motion.preparedPins, session.root, cancellation)
        else ComfyShortI2VBinding.verify(requireNotNull(flat))

        require(!cancellation.isCancelled()) { "Take import was cancelled before publication." }
        val current = projects.open(session.root)
        if (current.revision != expectedRevision || current.id != session.project.id) {
            throw VideoProjectConcurrencyException("Video project changed while the take was being validated.")
        }
        // Identity-derived artifact path permits recovery of a matching orphan after a failed
        // document save, without relying on the newly allocated record ID for its location.
        val identity = listOf(current.id, request.id, output.attemptId, output.id,
            request.requestFingerprint, requireNotNull(output.sha256)).joinToString("\u0000")
        val identityHash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val id = VideoVersionedId(idFactory(), 1)
        val takePath = "takes/import-$identityHash/v1/preview.mp4"
        val artifact = VideoArtifact(takePath, validatedDigest)
        // Flat inputs name their persisted image by its exact owned original path and digest;
        // neither a prepared scene nor a persisted look is invented for this route.
        val sceneRecord = controlled?.let { motion ->
            val scene = Json.decodeFromJsonElement<VideoPreparedScene>(
                Json.parseToJsonElement(motion.motion.descriptor.requestJson).jsonObject.getValue("preparedScene"))
            current.preparedSceneVersions.single { it.id == scene.id }
        }
        val referenceId = if (controlled != null) {
            val scene = Json.decodeFromJsonElement<VideoPreparedScene>(
                Json.parseToJsonElement(controlled.motion.descriptor.requestJson).jsonObject.getValue("preparedScene"))
            val finished = scene.layers.single { it.kind == app.melotrail.video.domain.VideoLayerKind.FINISHED_SCENE }.image
            scene.source.references.single { it.original == finished }.id.also { ref ->
                require(ref in requireNotNull(sceneRecord).sourceReferenceIds && current.referenceVersions.any { it.id == ref } &&
                    (sceneRecord.sourceLookId == null || current.lookVersions.any { it.id == sceneRecord.sourceLookId })) {
                    "Take source identities are not persisted in this project."
                }
            }
        } else flatReference(current, session.root, requireNotNull(flat))
        val sourceIdentity = controlled?.motion?.descriptor?.sourceIdentity ?: flatSourceIdentity(current, referenceId)
        val provenance = VideoTakeProvenanceRecord(current.id, request.id, output.attemptId, output.id,
            request.backendId, request.requestFingerprint, sourceIdentity,
            referenceId, sceneRecord?.id, sceneRecord?.sourceLookId, input.dependencyPins)
        val take = VideoTakeRecord(id, artifact, sceneRecord?.sourceLookId, Instant.now(clock).toString(),
            validation.source.toRecord(), verified.toRecord(), validation.conversion.name, provenance)
        val stage = projects.stageTake(session.root, validatedOutput, validatedDigest, cancellation)
        // Bulk decode, staging and its verification are already outside the lock. A
        // final digest of each externally writable input is still necessary at commit:
        // file keys, length and timestamps cannot attest to unchanged content.
        try {
            val (saved, published) = projects.publishImportedTake(session.root, expectedRevision, take,
                stage, cancellation, recheckJob = {
                    checkCurrentJob(currentJob(), request, output)
                }, contentPaths = listOf(source) + (controlled?.motion?.preparedPins ?: input.dependencyPins).mapNotNull { it.ownedPath?.let(Path::of) }) { latest ->
                require(latest.id == current.id) { "Project identity changed before publication." }
                checkCurrentJob(currentJob(), request, output)
                require(latest.referenceVersions.any { it.id == referenceId } &&
                    (sceneRecord == null || latest.preparedSceneVersions.any { it == sceneRecord } &&
                        (sceneRecord.sourceLookId == null || latest.lookVersions.any { it.id == sceneRecord.sourceLookId }))) {
                    "Consumed project identities changed before publication."
                }
                if (controlled != null) verifyPins(controlled.motion.preparedPins, session.root, cancellation)
                else {
                    require(flatReference(latest, session.root, requireNotNull(flat)) == referenceId &&
                        flatSourceIdentity(latest, referenceId) == sourceIdentity) { "Finished reference changed before publication." }
                    ComfyShortI2VBinding.verify(flat)
                }
                require(Files.isRegularFile(source, NOFOLLOW_LINKS) && source.toRealPath() == source &&
                    Files.size(source) == output.byteCount && sha256(source, cancellation) == output.sha256) {
                    "Completed output changed before publication."
                }
            }
            return VideoImportedTake(saved, published, VideoTakeMediaFacts(
                published.publishedMeasurement!!.decodedFrameCount,
                published.publishedMeasurement.frameRate.let { it.numerator.toDouble() / it.denominator },
                published.sourceMeasurement!!.width, published.sourceMeasurement.height,
                published.publishedMeasurement.width, published.publishedMeasurement.height,
                published.conversion!!))
        } finally { Files.deleteIfExists(stage) }
    }

    private fun flatReference(project: VideoProject, root: Path, input: VideoClipGenerationInput): VideoVersionedId {
        val image = input.dependencyPins.single { it.id == ComfyShortI2VBinding.IMAGE_ID }
        return project.referenceVersions.single { record ->
            val descriptor = projects.resolveArtifact(root, record.artifact)
            // The descriptor is already verified by open(); match the actual original
            // recorded by the asset importer, not an arbitrary path with the same bytes.
            val asset = Json.decodeFromString<app.melotrail.video.domain.VideoAsset>(Files.readString(descriptor))
            asset.id == record.id && asset.original.artifact.sha256 == image.sha256 &&
                VideoImageFiles().resolveOriginal(root, record).toString() == image.ownedPath
        }.id
    }

    private fun flatSourceIdentity(project: VideoProject, id: VideoVersionedId): String {
        val record = project.referenceVersions.single { it.id == id }
        return MessageDigest.getInstance("SHA-256").digest(
            listOf(project.id, id.id, id.version.toString(), record.artifact.sha256).joinToString("\u0000")
                .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun checkCurrentJob(job: VideoGenerationJob, request: VideoGenerationJobRequest,
                                output: VideoGenerationOutput) {
        require(job.request == request && job.attempts.lastOrNull()?.let {
            it.id == output.attemptId && it.status == VideoGenerationAttemptStatus.SUCCEEDED
        } == true && job.currentOutputId == output.id && job.outputs.singleOrNull {
            it.id == output.id && it.attemptId == output.attemptId
        } == output) { "Current durable attempt or output changed before publication." }
    }

    private fun VideoSourceMeasurement.toRecord() = VideoTakeMeasurementRecord(
        sha256, bytes, videoCodec, width, height,
        VideoTakeRationalRecord(sampleAspectRatio.numerator, sampleAspectRatio.denominator),
        VideoTakeRationalRecord(frameRate.numerator, frameRate.denominator), decodedFrameCount,
        VideoTakeRationalRecord(videoTimeBase.numerator, videoTimeBase.denominator),
        videoStartPts, videoDurationPts, videoStreamCount, audioStreamCount, otherStreamCount,
    )

    /** The backend ID and the ledger's pinned relative output select one configured publication root. */
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
