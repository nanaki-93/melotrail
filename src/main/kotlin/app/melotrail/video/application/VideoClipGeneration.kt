package app.melotrail.video.application

import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.VideoControlledMediaBinding
import app.melotrail.video.domain.MAX_JAVASCRIPT_SAFE_INTEGER
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoExecutionPolicy
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoModelRequirement
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPreparedSceneRecord
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoControlledStage
import app.melotrail.video.domain.VideoTakeReviewStatus
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoControlledMotionRuntimeBinding
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.adapter.VideoImportedTake
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoImageFiles
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.adapter.VideoMotionRenderer
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Pre-claim static resolution for the supported Canvas 0.1.80 runtime. The renderer
 * additionally asks the pinned Node process for its require.cache after claim. */
private fun app.melotrail.video.domain.VideoControlledMotionRuntimeBinding.verifyResolvedCanvas() {
    require(expectedCanvasVersion == "0.1.80") { "Canvas loaded-file contract is not established for $expectedCanvasVersion." }
    val script = Path.of(requireNotNull(compositor.ownedPath))
    val manifest = Path.of(requireNotNull(canvasManifest.ownedPath))
    require(manifest == manifest.parent.parent.parent.resolve("@napi-rs/canvas/package.json")) {
        "Canvas manifest must be in node_modules/@napi-rs/canvas."
    }
    // Node's createRequire(script) searches ancestor node_modules before NODE_PATH.
    // The renderer supplies this manifest's node_modules as NODE_PATH when none exists.
    val resolved = generateSequence(script.parent) { it.parent }
        .map { it.resolve("node_modules/@napi-rs/canvas/package.json") }
        .firstOrNull { Files.exists(it, LinkOption.NOFOLLOW_LINKS) } ?: manifest
    require(resolved == manifest) { "Node resolves a different Canvas manifest than the pinned package." }
    val canvas = Json.parseToJsonElement(Files.readString(manifest)).jsonObject
    require(canvas["name"]?.jsonPrimitive?.content == "@napi-rs/canvas" &&
        canvas["version"]?.jsonPrimitive?.content == expectedCanvasVersion) {
        "Canvas manifest does not match the requested package version."
    }
    val files = listOf("index.js", "js-binding.js", "geometry.js", "load-image.js")
        .map { manifest.parent.resolve(it) } +
        listOf(manifest.parent.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node"))
    require(canvasArtifacts.map { Path.of(requireNotNull(it.ownedPath)) }.toSet() == files.toSet() &&
        canvasArtifacts.size == files.size) {
        "Canvas loaded artifacts must exactly pin the resolved package JS and native files."
    }
}

private fun controlledRuntimeBinding(runtime: List<VideoGenerationDependencyPin>, canvasVersion: String): VideoControlledMotionRuntimeBinding {
    val byId = runtime.associateBy { it.id }
    require(byId.size == runtime.size) { "Controlled runtime roles must be unique." }
    fun pin(id: String) = requireNotNull(byId[id]) { "Controlled runtime requires $id." }
    val artifacts = runtime.filter { it.id.startsWith("canvas-artifact-") }.sortedBy { it.id }
    val required = setOf("node", "compositor", "scenery", "canvas-manifest", "ffmpeg", "ffprobe", "media-manifest")
    require(artifacts.isNotEmpty()) { "Canvas loaded artifacts must be pinned." }
    require(byId.keys.all { it in required || it.startsWith("canvas-artifact-") }) {
        "Controlled runtime contains an unsupported role."
    }
    required.forEach(::pin)
    val binding = VideoControlledMotionRuntimeBinding(
        pin("node"), pin("compositor"), pin("scenery"), pin("canvas-manifest"), artifacts,
        pin("ffmpeg"), pin("ffprobe"), pin("media-manifest"), canvasVersion,
    )
    val mediaPath = Path.of(requireNotNull(binding.mediaManifest.ownedPath))
    require(mediaPath.fileName.toString() == "melotrail-video-tools.json" &&
        Path.of(requireNotNull(binding.ffmpeg.ownedPath)).parent == mediaPath.parent &&
        Path.of(requireNotNull(binding.ffprobe.ownedPath)).parent == mediaPath.parent) {
        "Pinned media manifest must accompany the selected FFmpeg and ffprobe binaries."
    }
    val script = Path.of(requireNotNull(binding.compositor.ownedPath))
    require(Path.of(requireNotNull(binding.scenery.ownedPath)) == script.parent.resolve("scenery.cjs")) {
        "Scenery must be the compositor's pinned sibling module."
    }
    binding.verifyResolvedCanvas() // Static resolution only; Node checks require.cache after claim.
    return binding
}

private val CONTROLLED_SCENE_JSON = Json { encodeDefaults = true }

/** Production orchestration from already prepared motion through durable job and immutable take. */
class VideoClipGeneration(
    private val preparation: VideoScenePreparation,
    private val coordinator: VideoJobCoordinator,
    private val resultImport: VideoResultImport,
    private val motionRenderer: VideoMotionRenderer,
    private val backendId: String,
    private val execution: VideoExecutionPolicy,
    private val modelRequirements: List<VideoModelRequirement> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
    private val requestIdFactory: () -> String = { "clip-${java.util.UUID.randomUUID()}" },
    private val projects: VideoProjectStore? = null,
) {
    /** Read-only eligibility, not a setup probe or an authorization to launch native work.
     * Full pin, coverage and media checks still run during admission. Flat admission is
     * intentionally unavailable until the shared I2V binding is connected. */
    fun capabilities(
        session: VideoProjectSession,
        preparedSceneId: VideoVersionedId? = null,
        configuredRuntimePins: List<VideoGenerationDependencyPin> = emptyList(),
    ): List<VideoRouteCapability> {
        val store = requireNotNull(projects) { "Verified project storage is required for capability queries." }
        val project = store.open(session.root)
        require(project.id == session.project.id) { "Video project identity changed; reopen it." }
        val occupied = coordinator.snapshot().jobs.any { job ->
            job.request.execution is VideoLocalExecutionPolicy && job.attempts.any { !it.status.isTerminal }
        }
        val library = when (val opened = VideoAssetImport(VideoProjectLifecycle(store), VideoImageFiles()).open(session.root)) {
            is VideoAssetLibraryResult.Loaded -> opened.assets
            is VideoAssetLibraryResult.Rejected -> throw IllegalArgumentException(opened.problem.message)
        }
        val scene = preparedSceneId?.let { VideoPreparedSceneStore(store).load(session.root, it) }
        val finishedScene = scene?.layers?.singleOrNull { it.kind == VideoLayerKind.FINISHED_SCENE }
        val selectedReference = scene?.source?.references?.singleOrNull { reference ->
            reference.original == finishedScene?.image
        }
        val finished = project.referenceVersions.any { record ->
            VideoSceneLooks().select(project, library, record.id) is VideoSceneLookSelectionResult.Selected
        }
        val controlledArtwork = selectedReference?.let { reference ->
            (VideoSceneLooks().select(project, library, reference.id) as? VideoSceneLookSelectionResult.Selected)
                ?.look?.let { it.sourceDescriptor == reference.descriptorArtifact && it.original == reference.original }
        } == true
        // These are only operations for which the verified prepared scene has the
        // requisite kind of source. Per-request bounds/coverage remain admission checks.
        val controls = scene?.motionCapabilities.orEmpty().mapNotNull { capability ->
            if (capability.reviewStatus == VideoComponentReviewStatus.REJECTED) return@mapNotNull null
            val subject = scene?.layers.orEmpty().singleOrNull { it.id == capability.targetId &&
                it.kind == VideoLayerKind.SUBJECT && it.reviewStatus != VideoComponentReviewStatus.REJECTED }
            when {
                capability.control == VideoMotionControl.POSE_BLEND && capability.targetType == VideoMotionTargetType.POSE &&
                    scene?.poses.orEmpty().any { it.id == capability.targetId && it.reviewStatus != VideoComponentReviewStatus.REJECTED } &&
                    capability.unit == VideoMotionUnit.RATIO && capability.minimum >= 0 &&
                    capability.maximum > 0 && capability.minimum <= 1 -> VideoSceneMotionIntent.BLINK
                capability.control == VideoMotionControl.TRANSLATE_Y && capability.targetType == VideoMotionTargetType.LAYER &&
                    subject != null && capability.unit == VideoMotionUnit.PIXELS &&
                    capability.minimum < 0 && capability.maximum > 0 &&
                    scene?.layers.orEmpty().any { it.kind == VideoLayerKind.ENVIRONMENT &&
                        it.bounds.coordinateSpaceId == subject.bounds.coordinateSpaceId &&
                        it.reviewStatus != VideoComponentReviewStatus.REJECTED } -> VideoSceneMotionIntent.BREATHING
                capability.control == VideoMotionControl.ROTATE && capability.targetType == VideoMotionTargetType.LAYER &&
                    subject != null && capability.unit == VideoMotionUnit.DEGREES &&
                    capability.minimum < 0 && capability.maximum > 0 &&
                    scene?.masks.orEmpty().count { it.purpose == VideoMaskPurpose.HEAD_REGION &&
                        it.layerIds == listOf(capability.targetId) && it.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                        it.alpha?.isUsableCutout == true } == 1 -> VideoSceneMotionIntent.HEAD_GESTURE
                capability.control == VideoMotionControl.EFFECT_RATE && capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR &&
                    capability.unit == VideoMotionUnit.PER_SECOND && capability.minimum <= 8 &&
                    capability.maximum > 0 && scene?.effectAnchors.orEmpty().any { it.id == capability.targetId &&
                        it.reviewStatus != VideoComponentReviewStatus.REJECTED } -> VideoSceneMotionIntent.STEAM
                capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y) &&
                    capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE && capability.unit == VideoMotionUnit.PIXELS &&
                    (capability.minimum < 0 || capability.maximum > 0) &&
                    scene?.sceneryCoverage.orEmpty().any { coverage -> coverage.id == capability.targetId &&
                        coverage.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                        scene?.layers.orEmpty().any { it.id == coverage.layerId && it.reviewStatus != VideoComponentReviewStatus.REJECTED } } -> VideoSceneMotionIntent.SCENERY_TRAVEL
                else -> null
            }
        }.distinct()
        val toolsPresent = runCatching { controlledRuntimeBinding(configuredRuntimePins, "0.1.80") }.isSuccess &&
            configuredRuntimePins.all { pin ->
                pin.ownedPath?.let { runCatching {
                    val path = Path.of(it)
                    path.isAbsolute && path.normalize() == path && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(path) && path.toRealPath() == path &&
                        MessageDigest.getInstance("SHA-256").also { digest ->
                            Files.newInputStream(path).use { stream ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    digest.update(buffer, 0, count)
                                }
                            }
                        }.digest().joinToString("") { byte -> "%02x".format(byte) } == pin.sha256
                }.getOrDefault(false) } == true
            }
        fun blocker(code: VideoCapabilityBlockerCode, action: String) = VideoCapabilityBlocker(code, action)
        // Only the selected controlled backend is queried. Its availability is a
        // declarative in-memory observation; explicit setup/pin checks remain separate.
        val controlledAvailability = runCatching { coordinator.availability(backendId) }.getOrNull()
        val controlledBlockers = buildList {
            if (!controlledArtwork) add(blocker(VideoCapabilityBlockerCode.MISSING_ARTWORK,
                "Select a prepared scene pinned to an eligible finished image, or import replacement artwork."))
            if (scene == null || controls.isEmpty()) add(blocker(VideoCapabilityBlockerCode.UNSUPPORTED_MOTION,
                "Prepare ready layers/poses/anchors and select a supported compositor control."))
            if (!toolsPresent) add(blocker(VideoCapabilityBlockerCode.MISSING_TOOL, "Configure and verify pinned Node, Canvas and media tools before generation."))
            if (controlledAvailability?.status != VideoBackendAvailabilityStatus.AVAILABLE ||
                VideoGenerationInputKind.CONTROLLED_MOTION !in controlledAvailability.supportedInputs) {
                add(blocker(VideoCapabilityBlockerCode.UNAVAILABLE_RUNTIME,
                    "Configure an available controlled media worker; reconcile uncertain prior work before starting a new session."))
            }
            if (occupied) add(blocker(VideoCapabilityBlockerCode.OCCUPIED_ADMISSION, "Reconcile or finish the current local attempt before admitting another."))
        }
        val flatBlockers = buildList {
            if (!finished) add(blocker(VideoCapabilityBlockerCode.MISSING_ARTWORK, "Import an eligible finished scene image."))
            add(blocker(VideoCapabilityBlockerCode.UNAVAILABLE_RUNTIME,
                "The short-I2V application route is not connected yet; configure and verify an owned ComfyUI session after integration."))
            if (occupied) add(blocker(VideoCapabilityBlockerCode.OCCUPIED_ADMISSION, "Reconcile or finish the current local attempt before admitting another."))
        }
        val viewport = scene?.layers?.singleOrNull { it.kind == VideoLayerKind.FINISHED_SCENE }?.bounds
        return listOf(
            VideoRouteCapability(VideoClipRoute.CONTROLLED_MOTION, controlledBlockers.isEmpty(), controlledBlockers,
                controls, viewport?.width?.toInt(), viewport?.height?.toInt(), 3840, 2160, 30,
                1, 9_000, 34, 300_000, "Native controlled frames at 30 fps; bounded chunks of 300 frames. No full-length delivery/export is implemented.",
                setOf("H264_SILENT_REENCODE")),
            VideoRouteCapability(VideoClipRoute.FLAT_IMAGE_I2V, false, flatBlockers,
                emptyList(), null, null, 768, 448, 25, 129, 129,
                5_160, 5_160, "Measured short I2V: 129 frames / 25 fps = 5.16 seconds at 768x448; no regional or character control and no 20–30 second I2V.",
                setOf("SILENT_DERIVATIVE")),
        )
    }

    /** Project-scoped durable projection. No polling or inference occurs here. */
    fun jobs(session: VideoProjectSession): List<VideoClipJobView> {
        val project = requireNotNull(projects) { "Verified project storage is required for job queries." }.open(session.root)
        require(project.id == session.project.id) { "Video project identity changed; reopen it." }
        val ledger = coordinator.snapshot()
        return ledger.jobs.filter { it.request.projectId == project.id }.map { job ->
            val attempt = job.attempts.lastOrNull()
            val remaining = job.request.maximumAttempts - job.attempts.size
            VideoClipJobView(job.request.id,
                when (job.request.input) {
                    is VideoControlledMotionGenerationInput -> VideoClipRoute.CONTROLLED_MOTION
                    is VideoClipGenerationInput -> VideoClipRoute.FLAT_IMAGE_I2V
                    else -> VideoClipRoute.OTHER_JOB
                },
                attempt?.id, attempt?.status, attempt?.controlledEvidence?.stage, attempt?.progressPercent,
                job.currentOutputId, remaining,
                coordinator.retryEligible(ledger, job),
                project.takeVersions.filter { it.provenance?.requestId == job.request.id }.map { take ->
                    VideoClipTakeView(take.id, project.reviewStatus(take.id), take.id in project.selectedTakeIds)
                })
        }
    }

    fun snapshot() = coordinator.snapshot()
    fun recover() = coordinator.recover()
    fun reconcile(session: VideoProjectSession, requestId: String, attemptId: String? = null): VideoJobResult {
        requireOwnedJob(session, requestId)
        return coordinator.reconcile(requestId, attemptId)
    }
    fun cancel(session: VideoProjectSession, requestId: String, attemptId: String): VideoJobResult {
        requireOwnedJob(session, requestId)
        return coordinator.cancel(requestId, attemptId)
    }
    fun retry(session: VideoProjectSession, requestId: String): VideoJobResult {
        requireOwnedJob(session, requestId)
        return coordinator.retry(requestId)
    }
    private fun requireOwnedJob(session: VideoProjectSession, requestId: String) {
        val project = requireNotNull(projects) { "Verified project storage is required for job operations." }.open(session.root)
        require(project.id == session.project.id && coordinator.snapshot().jobs.any {
            it.request.id == requestId && it.request.projectId == project.id
        }) { "Durable job is not owned by this video project." }
    }

    fun generate(request: VideoClipGenerationRequest): VideoClipGenerationResult {
        if (request.project.revision != request.expectedRevision) return VideoClipGenerationResult.Rejected("Project revision changed before generation.")
        if (execution !is VideoLocalExecutionPolicy) return VideoClipGenerationResult.Rejected("Controlled compositor execution requires an explicitly configured local execution policy.")
        // A caller's review and usage fields are not an authority for generation. Reopen
        // the asset library and derive eligibility solely from its persisted descriptor.
        val verifiedLook = try {
            val store = requireNotNull(projects) { "Verified project storage is required for controlled admission." }
            val root = requireNotNull(request.projectRoot) { "A project root is required for controlled admission." }
            val current = store.open(root)
            require(current == request.project && current.revision == request.expectedRevision) { "Project changed before controlled admission." }
            val library = when (val loaded = app.melotrail.video.application.VideoAssetImport(
                app.melotrail.video.application.VideoProjectLifecycle(store), VideoImageFiles(),
            ).open(root)) {
                is app.melotrail.video.application.VideoAssetLibraryResult.Loaded -> loaded
                is app.melotrail.video.application.VideoAssetLibraryResult.Rejected ->
                    throw IllegalArgumentException(loaded.problem.message)
            }
            require(library.session.project == current) { "Project changed while reopening finished reference assets." }
            when (val selected = VideoSceneLooks().select(current, library.assets, request.look.id)) {
                is VideoSceneLookSelectionResult.Selected -> selected.look.also {
                    require(it == request.look) { "Finished reference metadata differs from the verified imported asset." }
                }
                is VideoSceneLookSelectionResult.Rejected -> throw IllegalArgumentException(
                    selected.problems.joinToString(" ") { it.message },
                )
            }
        } catch (error: Exception) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Finished reference verification failed.")
        }
        val prepared = preparation.prepare(verifiedLook, request.scene, request.motionRequest, request.backendCapabilities, request.guidelineSet)
        val preparedResult = (prepared as? VideoScenePreparationResult.Prepared)
            ?: return VideoClipGenerationResult.Rejected("Prepared scene inputs were rejected: ${(prepared as VideoScenePreparationResult.Rejected).problems.joinToString { it.message }}")
        // Preparation describes asset eligibility, not compositor execution. Do not persist a
        // controlled job for I2V or a control outside the selected renderer's implemented limits.
        if (preparedResult.input.controls.any { it.intent == VideoSceneMotionIntent.CAMERA_OR_AMBIENT }) {
            return VideoClipGenerationResult.Rejected("Composed-image I2V requires the separate local video stage; the controlled compositor cannot execute it. Select only supported compositor controls.")
        }
        val unsupported = preparedResult.input.controls.mapNotNull { input ->
            preparation.rendererUnsupportedReason(input)?.let { "${input.id}: $it" }
        }
        if (unsupported.isNotEmpty()) {
            return VideoClipGenerationResult.Rejected("Controlled compositor request is unsupported: ${unsupported.joinToString(" ")} Supply a supported reduced request; see motion-runtime.json.")
        }
        if (preparedResult.input.componentReviews.any { it.status == app.melotrail.video.domain.VideoComponentReviewStatus.REJECTED }) {
            return VideoClipGenerationResult.Rejected("Prepared motion includes rejected component review state.")
        }
        // Admission cannot use a caller's project, scene or pin list as proof of the bytes
        // the compositor will consume. Reopen through the guarded stores before submitting.
        val verified = try {
            val store = requireNotNull(projects) { "Verified project storage is required for controlled admission." }
            val root = requireNotNull(request.projectRoot) { "A project root is required for controlled admission." }
            val current = store.open(root)
            require(current == request.project && current.revision == request.expectedRevision) { "Project changed before controlled admission." }
            val record = current.preparedSceneVersions.singleOrNull { it.id == request.scene.id }
                ?: throw IllegalArgumentException("Prepared scene is not persisted in this project.")
            val scene = VideoPreparedSceneStore(store).load(root, request.scene.id)
            require(scene == request.scene) { "Prepared scene changed before controlled admission." }
            val reference = scene.source.references.singleOrNull { it.id == request.look.id }
            require(reference != null && reference.descriptorArtifact == request.look.sourceDescriptor &&
                reference.original == request.look.original) { "Finished reference changed before controlled admission." }
            val artifacts = (listOf(record.artifact) + record.consumedArtifacts).distinct()
            val pins = artifacts.mapIndexed { index, artifact ->
                VideoGenerationDependencyPin("prepared-$index", artifact.sha256, store.resolveArtifact(root, artifact).toString())
            }
            require(request.preparedDependencies == pins) { "Prepared dependency pins differ from verified consumed artifacts." }
            require(request.runtimeDependencies.isNotEmpty()) { "Explicit compositor runtime pins are required." }
            request.runtimeDependencies.forEach { pin ->
                val path = Path.of(requireNotNull(pin.ownedPath) { "Runtime path is required: ${pin.id}" })
                require(path.isAbsolute && path.normalize() == path && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(path) && path.toRealPath() == path) { "Runtime path is missing or unsafe: ${pin.id}" }
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val size = stream.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
                }
                require(digest.digest().joinToString("") { "%02x".format(it) } == pin.sha256) { "Runtime bytes changed: ${pin.id}" }
            }
            Triple(scene, pins, controlledSourceIdentity(current, scene, record, reference))
        } catch (error: Exception) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Controlled source or runtime verification failed.")
        }
        val (verifiedScene, preparedPins, sourceIdentity) = verified
        val runtime = request.runtimeDependencies
        val runtimeBinding = try {
            controlledRuntimeBinding(runtime, request.expectedCanvasVersion) // No native setup probe runs before durable claim.
        } catch (error: IllegalArgumentException) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Controlled runtime is incomplete.")
        }
        val pinById = (preparedPins + runtime).associateBy(VideoGenerationDependencyPin::id)
        if (pinById.size != preparedPins.size + runtime.size) return VideoClipGenerationResult.Rejected("Prepared/runtime dependency IDs must be unique.")
        val input = try {
            val viewport = verifiedScene.layers.single { it.kind == app.melotrail.video.domain.VideoLayerKind.FINISHED_SCENE }.bounds
            val controls = preparation.compileControlledControls(preparedResult.input)
            val scenery = preparation.compileControlledScenery(preparedResult.input, request.startFrame,
                request.endFrameExclusive, viewport.width.toInt(), viewport.height.toInt())
            val descriptorJson = buildJsonObject {
                put("schema", app.melotrail.video.domain.CONTROLLED_MOTION_DESCRIPTOR_SCHEMA)
                put("preparedScene", Json.parseToJsonElement(CONTROLLED_SCENE_JSON.encodeToString(verifiedScene)))
                put("seed", request.seed)
                put("fps", 30)
                put("canvas", buildJsonObject {
                    put("width", viewport.width.toInt())
                    put("height", viewport.height.toInt())
                    put("coordinateSpaceId", viewport.coordinateSpaceId)
                })
                put("frameRange", buildJsonObject {
                    put("startFrame", request.startFrame)
                    put("frameCount", Math.subtractExact(request.endFrameExclusive, request.startFrame))
                })
                put("controls", JsonArray(controls))
                if (scenery != null) put("scenery", scenery)
            }.toString()
            VideoControlledMotionGenerationInput(
                prompt = preparedResult.input.backendMotionPrompt,
                dependencyPins = pinById.values.toList(),
                motion = app.melotrail.video.domain.VideoControlledMotionRequest(
                    preparedPins = preparedPins,
                    startFrame = request.startFrame,
                    endFrameExclusive = request.endFrameExclusive,
                    seed = request.seed,
                    descriptor = app.melotrail.video.domain.VideoControlledMotionDescriptor(
                        projectId = request.project.id,
                        sourceIdentity = sourceIdentity,
                        requestJson = descriptorJson,
                        runtime = runtimeBinding,
                    ),
                ),
                primaryPrompt = preparedResult.input.primaryMotionPrompt,
                media = VideoControlledMediaBinding(
                    execution = execution,
                    maximumStagingBytes = execution.diskLimitBytes / 2,
                    maximumOutputBytes = execution.diskLimitBytes - execution.diskLimitBytes / 2,
                    minimumFreeDiskBytes = (execution.diskLimitBytes / 8).coerceAtLeast(1),
                    maximumConcurrentNativeProcesses = 1,
                    maximumBufferedFrames = 1,
                    encodingProfile = "image2-h264-yuv420p-silent-square-v1",
                ),
            )
        } catch (error: IllegalArgumentException) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Controlled motion inputs are invalid.")
        }
        val fingerprint = controlledMotionRequestFingerprint(backendId, input, modelRequirements, request.maximumAttempts)
        val jobRequest = try {
            VideoGenerationJobRequest(request.requestId ?: requestIdFactory(), request.project.id, backendId, modelRequirements, input,
                fingerprint, request.maximumAttempts, Instant.now(clock).toString(), execution)
        } catch (error: IllegalArgumentException) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Generation request is invalid.")
        }
        val admitted = coordinator.submit(jobRequest)
        if (admitted is VideoJobResult.Rejected) return VideoClipGenerationResult.Rejected(admitted.problem.message)
        admitted as VideoJobResult.Accepted
        if (admitted.attempt?.status == app.melotrail.video.domain.VideoGenerationAttemptStatus.FAILED ||
            admitted.attempt?.status == app.melotrail.video.domain.VideoGenerationAttemptStatus.CANCELLED) {
            return VideoClipGenerationResult.Rejected(admitted.attempt.failure ?: "Controlled motion attempt did not complete.")
        }
        return VideoClipGenerationResult.Admitted(admitted.job.request, admitted)
    }

    /** A result is importable only after its newest durable attempt succeeded. */
    fun importCompleted(
        request: VideoCompletedTakeImport,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
    ): VideoClipGenerationResult = try {
        require(!cancellation.isCancelled()) { "Take import was cancelled." }
        val store = requireNotNull(projects) { "Verified project storage is required for result import." }
        val current = store.open(request.session.root)
        require(current == request.session.project && current.revision == request.expectedRevision) {
            "Project revision changed before result import."
        }
        val job = coordinator.snapshot().jobs.singleOrNull { it.request.id == request.requestId }
            ?: throw IllegalArgumentException("Durable generation job was not found.")
        require(job.request.projectId == current.id && job.request.backendId == backendId) {
            "Completed motion project or backend identity does not match."
        }
        val input = job.request.input as? VideoControlledMotionGenerationInput
            ?: throw IllegalArgumentException("Completed route is not yet connected for take import.")
        val policy = job.request.execution as? VideoLocalExecutionPolicy
        require(policy != null && policy == execution && input.media.execution == policy &&
            job.request.modelRequirements == modelRequirements &&
            job.request.requestFingerprint == controlledMotionRequestFingerprint(
                backendId, input, modelRequirements, job.request.maximumAttempts,
            )) { "Completed motion fingerprint or persisted execution policy changed." }
        val attempt = job.attempts.lastOrNull()
        require(attempt?.id == request.attemptId && attempt.status == VideoGenerationAttemptStatus.SUCCEEDED &&
            attempt.controlledEvidence?.stage == VideoControlledStage.COMPLETED) {
            "Only the latest successfully completed controlled attempt can become a take."
        }
        val output = job.outputs.singleOrNull { it.id == request.outputId && it.id == job.currentOutputId &&
            it.attemptId == attempt.id } ?: throw IllegalArgumentException("Successful job has no matching current output.")
        require(output.id == "${attempt.id}-preview" && output.backendOutputId == output.id &&
            output.relativePath == "${attempt.id}/preview.mp4" &&
            attempt.controlledEvidence.output?.let { evidence ->
                evidence.backendOutputId == output.backendOutputId && evidence.relativePath == output.relativePath &&
                    evidence.sha256 == output.sha256 && evidence.byteCount == output.byteCount
            } == true) { "Controlled output does not match the current attempt's durable evidence." }
        val descriptor = input.motion.descriptor
        val pinnedScene = CONTROLLED_SCENE_JSON.decodeFromJsonElement<VideoPreparedScene>(
            Json.parseToJsonElement(descriptor.requestJson).jsonObject.getValue("preparedScene"))
        val scene = VideoPreparedSceneStore(store).load(request.session.root, pinnedScene.id)
        val record = current.preparedSceneVersions.single { it.id == scene.id }
        require(descriptor.projectId == current.id && scene == pinnedScene) {
            "Prepared scene changed since controlled admission."
        }
        val finishedImage = scene.layers.single { it.kind == app.melotrail.video.domain.VideoLayerKind.FINISHED_SCENE }.image
        val reference = scene.source.references.single { it.original == finishedImage }
        val previouslyImported = current.takeVersions.any { take ->
            take.provenance?.let { provenance ->
                provenance.projectId == current.id && provenance.requestId == job.request.id &&
                    provenance.attemptId == attempt.id && provenance.outputId == output.id &&
                    provenance.executableFingerprint == job.request.requestFingerprint &&
                    provenance.sourceIdentity == descriptor.sourceIdentity &&
                    take.sourceMeasurement?.sha256 == output.sha256
            } == true
        }
        // Admission's source fingerprint includes the project revision. Importing a take
        // advances that revision; an exact replay must retain the original pinned identity.
        require(previouslyImported || descriptor.sourceIdentity == controlledSourceIdentity(current, scene, record, reference)) {
            "Controlled source identity changed since admission."
        }
        val pins = (listOf(record.artifact) + record.consumedArtifacts).distinct().mapIndexed { index, artifact ->
            VideoGenerationDependencyPin("prepared-$index", artifact.sha256,
                store.resolveArtifact(request.session.root, artifact).toString())
        }
        require(input.motion.preparedPins == pins && input.dependencyPins.containsAll(pins)) {
            "Persisted prepared dependency pins changed."
        }
        VideoClipGenerationResult.Imported(resultImport.import(request.session, request.expectedRevision,
            output, input, job.request, cancellation) {
            coordinator.snapshot().jobs.singleOrNull { it.request.id == job.request.id }
                ?: throw IllegalArgumentException("Durable job disappeared before take publication.")
        })
    } catch (error: Exception) {
        VideoClipGenerationResult.Rejected(error.message ?: "Generated media could not be imported safely.")
    }
}

enum class VideoClipRoute { CONTROLLED_MOTION, FLAT_IMAGE_I2V, OTHER_JOB }
enum class VideoCapabilityBlockerCode { MISSING_ARTWORK, UNSUPPORTED_MOTION, MISSING_TOOL, UNAVAILABLE_RUNTIME, OCCUPIED_ADMISSION }
data class VideoCapabilityBlocker(val code: VideoCapabilityBlockerCode, val nextAction: String)
data class VideoRouteCapability(
    val route: VideoClipRoute,
    val available: Boolean,
    val blockers: List<VideoCapabilityBlocker>,
    val supportedControls: List<VideoSceneMotionIntent>,
    val preparedWidth: Int?,
    val preparedHeight: Int?,
    val maximumNativeWidth: Int,
    val maximumNativeHeight: Int,
    val nativeFramesPerSecond: Int,
    val minimumNativeFrames: Int,
    val maximumNativeFrames: Int,
    /** Native frame bounds above determine precise duration; milliseconds are rounded upward. */
    val minimumNativeDurationMillis: Int,
    val maximumNativeDurationMillis: Int,
    val limitation: String,
    val permittedConversions: Set<String>,
)
data class VideoClipTakeView(val id: VideoVersionedId, val review: VideoTakeReviewStatus, val selected: Boolean)
data class VideoClipJobView(
    val requestId: String,
    val route: VideoClipRoute,
    val attemptId: String?,
    val status: VideoGenerationAttemptStatus?,
    val controlledStage: VideoControlledStage?,
    val observedProgressPercent: Int?,
    val currentOutputId: String?,
    val remainingAttempts: Int,
    val retryEligible: Boolean,
    val takes: List<VideoClipTakeView>,
)

data class VideoClipGenerationRequest(
    val project: VideoProject,
    val expectedRevision: Long,
    val look: VideoSceneLook,
    val scene: app.melotrail.video.domain.VideoPreparedScene,
    val motionRequest: VideoSceneMotionRequest,
    val backendCapabilities: app.melotrail.video.domain.VideoPromptBackendCapabilities,
    val guidelineSet: VideoPromptGuidelineSet,
    val preparedDependencies: List<VideoGenerationDependencyPin>,
    val runtimeDependencies: List<VideoGenerationDependencyPin>,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val seed: Long,
    val maximumAttempts: Int = 2,
    val requestId: String? = null,
    val projectRoot: Path? = null,
    val expectedCanvasVersion: String,
) {
    init {
        require(expectedRevision >= 0 && startFrame in 0..MAX_JAVASCRIPT_SAFE_INTEGER &&
            endFrameExclusive in 1..MAX_JAVASCRIPT_SAFE_INTEGER && endFrameExclusive > startFrame &&
            Math.subtractExact(endFrameExclusive, startFrame) in 1..9_000L &&
            seed in 0..MAX_JAVASCRIPT_SAFE_INTEGER && maximumAttempts in 1..3) {
            "Controlled frame range, seed or attempt bound is invalid"
        }
        require(requestId == null || requestId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")))
    }
}

private fun controlledSourceIdentity(
    project: VideoProject, scene: VideoPreparedScene, record: VideoPreparedSceneRecord,
    reference: app.melotrail.video.domain.VideoPreparedReferencePin,
): String {
    val source = "${project.id}:${project.revision}:${reference.id.id}:${reference.id.version}:${reference.descriptorArtifact.sha256}:${reference.original.artifact.sha256}:${record.id.id}:${record.id.version}:${record.artifact.sha256}:" +
        scene.consumedArtifacts().sortedBy { it.relativePath }.joinToString("|") { "${it.relativePath}:${it.sha256}" }
    return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class VideoCompletedTakeImport(
    val session: VideoProjectSession,
    val expectedRevision: Long,
    val requestId: String,
    val attemptId: String,
    val outputId: String,
)

sealed interface VideoClipGenerationResult {
    data class Admitted(val request: VideoGenerationJobRequest, val result: VideoJobResult) : VideoClipGenerationResult
    data class Imported(val result: VideoImportedTake) : VideoClipGenerationResult
    data class Rejected(val reason: String) : VideoClipGenerationResult
}

