package app.melotrail.video.application

import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoInputDependency
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoMotionCapability
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoPreparedLayer
import app.melotrail.video.domain.VideoPreparedMask
import app.melotrail.video.domain.VideoPreparedPose
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.requireFreeFormText

/** Pure admission preparation. V19 owns rendering and V16 remains the only durable job ledger. */
class VideoScenePreparation(
    private val promptCompiler: VideoPromptCompiler = VideoPromptCompiler(),
) {
    fun prepare(
        look: VideoSceneLook,
        scene: VideoPreparedScene,
        request: VideoSceneMotionRequest,
        backendCapabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
    ): VideoScenePreparationResult {
        val problems = mutableListOf<VideoScenePreparationProblem>()
        val sourcePin = scene.source.references.singleOrNull { it.id == look.id }
        if (sourcePin == null || sourcePin.descriptorArtifact != look.sourceDescriptor || sourcePin.original != look.original) {
            problems += problem(
                VideoScenePreparationProblemCode.SOURCE_PIN_MISMATCH,
                "Prepared scene ${scene.id.display()} does not consume the exact selected finished-scene descriptor and original bytes.",
                "Prepare a new scene through the ready-asset importer using ${look.id.display()}; keep the earlier scene and results unchanged.",
            )
        }
        request.appearanceRedesign?.let { redesign ->
            problems += problem(
                VideoScenePreparationProblemCode.REPLACEMENT_ARTWORK_REQUIRED,
                "The motion request also asks to change the finished appearance: $redesign",
                "Import externally finished replacement artwork as a new look version, then prepare motion against that version.",
            )
        }
        if (request.controls.isEmpty()) {
            problems += problem(
                VideoScenePreparationProblemCode.MISSING_MOTION_CONTROL,
                "No supported motion control was selected for this prompt.",
                "Choose the finished-scene image-to-video control, or supply ready layers/poses/anchors and select their derived control.",
            )
        }
        val duplicateIds = request.controls.groupBy(VideoSceneMotionControlRequest::id).filterValues { it.size > 1 }.keys
        duplicateIds.forEach { id ->
            problems += problem(
                VideoScenePreparationProblemCode.DUPLICATE_CONTROL_ID,
                "Motion input '$id' is declared more than once.",
                "Give each independently fingerprinted motion input a unique stable ID.",
                id,
            )
        }

        val resolved = request.controls.mapNotNull { control ->
            val capability = scene.motionCapabilities.singleOrNull { it.id == control.capabilityId }
            if (capability == null) {
                problems += unsupported(control, "The selected prepared scene does not expose capability '${control.capabilityId}'.")
                return@mapNotNull null
            }
            if (!control.intent.supports(capability, scene)) {
                problems += unsupported(
                    control,
                    "Capability '${capability.id}' (${capability.control} on ${capability.targetType}) cannot satisfy ${control.intent.label}.",
                )
                return@mapNotNull null
            }
            if (control.minimum < capability.minimum || control.maximum > capability.maximum ||
                control.defaultValue !in control.minimum..control.maximum
            ) {
                problems += problem(
                    VideoScenePreparationProblemCode.UNSUPPORTED_MOTION_RANGE,
                    "Motion input '${control.id}' requests ${control.minimum}..${control.maximum} with default ${control.defaultValue}; " +
                        "capability '${capability.id}' supports ${capability.minimum}..${capability.maximum}.",
                    "Keep the request inside the measured independent range. Combined timeline safety remains a V19 validation.",
                    control.id,
                )
                return@mapNotNull null
            }
            ResolvedControl(control, capability, consumedComponents(scene, control, capability))
        }

        val compiledPrompt = runCatching {
            promptCompiler.compileMotion(request.motionPrompt, look, backendCapabilities, guidelineSet)
        }.getOrElse { error ->
            problems += problem(
                VideoScenePreparationProblemCode.PROMPT_INVALID,
                error.message ?: "The exact motion prompt is invalid.",
                "Enter visible motion text without binary control characters and retry.",
            )
            null
        }
        if (compiledPrompt != null && !compiledPrompt.canPrepareRequests) {
            problems += problem(
                VideoScenePreparationProblemCode.PROMPT_COMPILATION_BLOCKED,
                compiledPrompt.issues.filter(VideoPromptIssue::blocksInference).joinToString(" ") { it.message },
                "Resolve the reported backend prompt or finished-scene binding capability before dispatch.",
            )
        }

        val reviews = if (resolved.size == request.controls.size) {
            (listOf(VideoSceneComponentReview(
                componentId = "${look.id.id}-v${look.id.version}",
                kind = VideoSceneComponentKind.FINISHED_LOOK,
                status = look.identityReview.toComponentReview(),
            )) + resolved.flatMap { it.consumed.components.map(ConsumedSceneComponent::review) })
                // The domain guarantees unique records within each kind. Multiple controls
                // can consume the same record, so project its review once across those controls.
                .distinctBy { it.kind to it.componentId }
                .sortedWith(compareBy({ it.kind.name }, { it.componentId }))
        } else emptyList()
        reviews.filter { it.status == VideoComponentReviewStatus.REJECTED }.forEach { review ->
            problems += problem(
                VideoScenePreparationProblemCode.REJECTED_COMPONENT,
                "Required ${review.kind.label} '${review.componentId}' has a rejected component review.",
                "Use replacement external artwork or correct the component metadata and prepare a new scene version.",
                review.componentId,
            )
        }

        if (problems.isNotEmpty() || compiledPrompt == null || resolved.size != request.controls.size) {
            return VideoScenePreparationResult.Rejected(problems.distinct())
        }

        val baseDependencies = buildList {
            addAll(compiledPrompt.dependencies)
            add(VideoInputDependency("look.descriptor", look.sourceDescriptor.sha256))
            add(VideoInputDependency("look.appearance-policy", digest(look.appearancePolicy.name)))
            scene.dependencies.forEach { dependency ->
                add(VideoInputDependency("prepared-dependency.${dependency.id}", dependency.sha256))
            }
        }.distinct()
        val inputs = resolved.map { (control, capability, consumed) ->
            val dependencies = (baseDependencies + consumed.dependencies).distinct()
            VideoPreparedMotionInput(
                id = control.id,
                intent = control.intent,
                capability = capability,
                requestedMinimum = control.minimum,
                requestedMaximum = control.maximum,
                requestedDefaultValue = control.defaultValue,
                dependencies = dependencies,
                fingerprint = fingerprint(dependencies),
            )
        }
        val dependencies = buildList {
            addAll(baseDependencies)
            add(VideoInputDependency("prepared-scene.version", digest(scene.id.id, scene.id.version.toString())))
            inputs.forEach { add(VideoInputDependency("motion-input.${it.id}", it.fingerprint)) }
        }.distinct()
        val status = if (
            compiledPrompt.status == VideoPromptCompilationStatus.REVIEW_REQUIRED ||
            reviews.any { it.status == VideoComponentReviewStatus.UNREVIEWED }
        ) VideoScenePreparationStatus.REVIEW_REQUIRED else VideoScenePreparationStatus.READY
        return VideoScenePreparationResult.Prepared(
            VideoPreparedSceneMotion(
                status = status,
                mode = if (inputs.any { it.capability.control != VideoMotionControl.IMAGE_TO_VIDEO }) {
                    VideoSceneMotionMode.CONTROLLED_REGIONAL_MOTION
                } else {
                    VideoSceneMotionMode.FLAT_IMAGE_TO_VIDEO
                },
                look = look,
                scene = scene,
                primaryMotionPrompt = compiledPrompt.primaryPrompt,
                backendMotionPrompt = checkNotNull(compiledPrompt.backendPrompt),
                promptIssues = compiledPrompt.issues,
                controls = inputs,
                componentReviews = reviews,
                dependencies = dependencies,
                requestFingerprint = fingerprint(dependencies),
            ),
        )
    }

    /** Pending inputs are invalidated independently; completed immutable results are always retained. */
    fun invalidatePending(
        previous: VideoPreparedSceneMotion,
        current: VideoPreparedSceneMotion,
        pendingMotionInputIds: Set<String>,
        completedResultIds: List<VideoVersionedId>,
    ): VideoSceneMotionInvalidation {
        val old = previous.controls.associateBy(VideoPreparedMotionInput::id)
        val replacement = current.controls.associateBy(VideoPreparedMotionInput::id)
        require(pendingMotionInputIds.all(old::containsKey)) {
            "Pending motion input IDs must belong to the previous preparation"
        }
        val invalidated = pendingMotionInputIds.filter { id ->
            replacement[id]?.fingerprint != old.getValue(id).fingerprint
        }.sorted()
        return VideoSceneMotionInvalidation(
            invalidatedPendingMotionInputIds = invalidated,
            unaffectedPendingMotionInputIds = (pendingMotionInputIds - invalidated.toSet()).sorted(),
            retainedCompletedResultIds = completedResultIds,
        )
    }

    private fun unsupported(control: VideoSceneMotionControlRequest, detail: String) = problem(
        VideoScenePreparationProblemCode.UNSUPPORTED_ACTION,
        "$detail A flat image-to-video or camera-only capability cannot satisfy ${control.intent.label}.",
        control.intent.nextAction,
        control.id,
    )

    /** Resolve once: each consumed record carries both its pin and its actual review state. */
    private fun consumedComponents(
        scene: VideoPreparedScene,
        request: VideoSceneMotionControlRequest,
        capability: VideoMotionCapability,
    ): ConsumedSceneSlice {
        val prefix = "control.${request.id}"
        val components = mutableListOf<ConsumedSceneComponent>()
        fun addComponent(
            id: String,
            kind: VideoSceneComponentKind,
            status: VideoComponentReviewStatus,
            vararg dependencies: VideoInputDependency,
        ) {
            components += ConsumedSceneComponent(VideoSceneComponentReview(id, kind, status), dependencies.toList())
        }
        addComponent(
            capability.id, VideoSceneComponentKind.MOTION_CAPABILITY, capability.reviewStatus,
            VideoInputDependency(
                "$prefix.settings",
                digest(
                    request.intent.name,
                    capability.id,
                    capability.targetType.name,
                    capability.targetId,
                    capability.control.name,
                    capability.unit.name,
                    capability.minimum.toString(), capability.maximum.toString(), capability.defaultValue.toString(),
                    request.minimum.toString(), request.maximum.toString(), request.defaultValue.toString(),
                    capability.reviewStatus.name,
                ),
            ),
        )
        val layerIds = linkedSetOf<String>()
        val spaceIds = linkedSetOf<String>()
        fun addLayer(layer: VideoPreparedLayer) {
            if (!layerIds.add(layer.id)) return
            spaceIds += layer.bounds.coordinateSpaceId
            addComponent(
                layer.id, VideoSceneComponentKind.LAYER, layer.reviewStatus,
                VideoInputDependency("$prefix.layer.${layer.id}.image", layer.image.artifact.sha256),
                VideoInputDependency(
                    "$prefix.layer.${layer.id}.geometry",
                    digest(
                        layer.kind.name, layer.image.toString(), layer.bounds.toString(), layer.transform.toString(), layer.pivot.toString(),
                        layer.alpha.toString(), layer.reviewStatus.name,
                    ),
                ),
            )
        }
        fun addPose(pose: VideoPreparedPose) {
            spaceIds += pose.bounds.coordinateSpaceId
            addComponent(
                pose.id, VideoSceneComponentKind.POSE, pose.reviewStatus,
                VideoInputDependency("$prefix.pose.${pose.id}.image", pose.image.artifact.sha256),
                VideoInputDependency(
                    "$prefix.pose.${pose.id}.geometry",
                    digest(
                        pose.subjectLayerId, pose.image.toString(), pose.bounds.toString(), pose.transform.toString(), pose.pivot.toString(),
                        pose.alpha.toString(), pose.reviewStatus.name,
                    ),
                ),
            )
            addLayer(scene.layers.single { it.id == pose.subjectLayerId })
        }
        val maskIds = linkedSetOf<String>()
        fun addMask(mask: VideoPreparedMask) {
            if (!maskIds.add(mask.id)) return
            spaceIds += mask.bounds.coordinateSpaceId
            addComponent(
                mask.id, VideoSceneComponentKind.MASK, mask.reviewStatus,
                VideoInputDependency("$prefix.mask.${mask.id}.image", mask.image.artifact.sha256),
                VideoInputDependency(
                    "$prefix.mask.${mask.id}.geometry",
                    digest(
                        mask.image.toString(), mask.bounds.toString(), mask.layerIds.sorted().joinToString(","),
                        mask.purpose.name, mask.transform.toString(), mask.pivot.toString(), mask.alpha.toString(),
                        mask.reviewStatus.name,
                    ),
                ),
            )
        }
        when (capability.targetType) {
            VideoMotionTargetType.LAYER -> addLayer(scene.layers.single { it.id == capability.targetId })
            VideoMotionTargetType.POSE -> addPose(scene.poses.single { it.id == capability.targetId })
            VideoMotionTargetType.EFFECT_ANCHOR -> {
                val anchor = scene.effectAnchors.single { it.id == capability.targetId }
                addLayer(scene.layers.single { it.id == anchor.layerId })
                anchor.landmarkId?.let { id ->
                    val landmark = scene.subjectLandmarks.single { it.id == id }
                    spaceIds += landmark.position.coordinateSpaceId
                    addComponent(
                        id, VideoSceneComponentKind.SUBJECT_LANDMARK, landmark.reviewStatus,
                        VideoInputDependency("$prefix.landmark.$id", digest(landmark.toString())),
                    )
                }
                addComponent(
                    anchor.id, VideoSceneComponentKind.EFFECT_ANCHOR, anchor.reviewStatus,
                    VideoInputDependency(
                        "$prefix.effect-anchor.${anchor.id}",
                        digest(anchor.layerId, anchor.landmarkId.toString(), anchor.position.toString(), anchor.reviewStatus.name),
                    ),
                )
            }
            VideoMotionTargetType.SCENERY_COVERAGE -> {
                val coverage = scene.sceneryCoverage.single { it.id == capability.targetId }
                addLayer(scene.layers.single { it.id == coverage.layerId })
                addComponent(
                    coverage.id, VideoSceneComponentKind.SCENERY_COVERAGE, coverage.reviewStatus,
                    VideoInputDependency(
                        "$prefix.scenery.${coverage.id}",
                        digest(coverage.layerId, coverage.bounds.toString(), coverage.reviewStatus.name),
                    ),
                )
            }
        }
        if (capability.control != VideoMotionControl.IMAGE_TO_VIDEO) {
            // The clean plate supports independent subject/pose motion. Effects attached to a
            // subject do not themselves move that subject and do not require its clean plate.
            if (capability.targetType == VideoMotionTargetType.POSE ||
                capability.targetType == VideoMotionTargetType.LAYER &&
                scene.layers.any { it.id in layerIds && it.kind == VideoLayerKind.SUBJECT }
            ) {
                scene.layers.filter {
                    it.kind == VideoLayerKind.ENVIRONMENT && it.bounds.coordinateSpaceId in spaceIds
                }.forEach(::addLayer)
            }
            // Foreground artwork participates in the regional composition, even without an
            // explicit mask. Other coordinate spaces remain independent.
            scene.layers.filter {
                it.kind == VideoLayerKind.FOREGROUND && it.bounds.coordinateSpaceId in spaceIds
            }.forEach(::addLayer)

            // Follow declared composition relationships to a fixed point. This includes both
            // sides and the occlusion mask, but never pulls in unconnected poses or anchors.
            do {
                val previousLayerCount = layerIds.size
                scene.masks.filter { mask -> mask.layerIds.any(layerIds::contains) }.forEach { mask ->
                    addMask(mask)
                    mask.layerIds.forEach { id -> addLayer(scene.layers.single { it.id == id }) }
                }
                scene.depthRelations.filter {
                    it.nearerLayerId in layerIds || it.fartherLayerId in layerIds
                }.forEach { relation ->
                    addLayer(scene.layers.single { it.id == relation.nearerLayerId })
                    addLayer(scene.layers.single { it.id == relation.fartherLayerId })
                }
                scene.occlusionRelations.filter {
                    it.occluderLayerId in layerIds || it.occludedLayerId in layerIds
                }.forEach { relation ->
                    addLayer(scene.layers.single { it.id == relation.occluderLayerId })
                    addLayer(scene.layers.single { it.id == relation.occludedLayerId })
                    addMask(scene.masks.single { it.id == relation.maskId })
                }
            } while (layerIds.size != previousLayerCount)
            scene.depthRelations.filter {
                it.nearerLayerId in layerIds || it.fartherLayerId in layerIds
            }.forEach { relation ->
                // ':' cannot occur in descriptor IDs, so ordered endpoint tuples are stable
                // and unambiguous even when another edge is inserted or lists are reordered.
                val id = "${relation.nearerLayerId}:${relation.fartherLayerId}"
                addComponent(
                    id, VideoSceneComponentKind.DEPTH_RELATION, relation.reviewStatus,
                    VideoInputDependency("$prefix.depth.${digest(id)}", digest(relation.toString())),
                )
            }
            scene.occlusionRelations.filter {
                it.occluderLayerId in layerIds || it.occludedLayerId in layerIds
            }.forEach { relation ->
                val id = "${relation.occluderLayerId}:${relation.occludedLayerId}:${relation.maskId}"
                addComponent(
                    id, VideoSceneComponentKind.OCCLUSION_RELATION, relation.reviewStatus,
                    VideoInputDependency("$prefix.occlusion.${digest(id)}", digest(relation.toString())),
                )
            }
        }
        val coordinateDependencies = scene.coordinateSpaces.filter { it.id in spaceIds }.map { space ->
            VideoInputDependency("$prefix.space.${space.id}", digest(space.toString()))
        }
        return ConsumedSceneSlice(components, coordinateDependencies)
    }
}

data class VideoSceneMotionRequest(
    /** Exact user-authored motion text. Appearance authority remains in [VideoSceneLook]. */
    val motionPrompt: String,
    val controls: List<VideoSceneMotionControlRequest>,
    /** Explicit UI/user intent; null means no appearance change was requested. */
    val appearanceRedesign: String? = null,
) {
    init {
        requireFreeFormText(motionPrompt, "Motion prompt")
        require(controls.size <= 128) { "At most 128 prepared motion controls are supported" }
        appearanceRedesign?.let { requireFreeFormText(it, "Appearance redesign request", 2_000) }
    }
}

data class VideoSceneMotionControlRequest(
    val id: String,
    val intent: VideoSceneMotionIntent,
    val capabilityId: String,
    val minimum: Double,
    val maximum: Double,
    val defaultValue: Double,
) {
    init {
        require(SAFE_ID.matches(id)) { "Motion input ID must be a safe stable identifier" }
        require(SAFE_ID.matches(capabilityId)) { "Motion capability ID must be a safe stable identifier" }
        require(minimum.isFinite() && maximum.isFinite() && defaultValue.isFinite() && minimum <= maximum) {
            "Requested motion bounds must be finite and ordered"
        }
    }
}

enum class VideoSceneMotionIntent(val label: String, val nextAction: String) {
    CAMERA_OR_AMBIENT(
        "camera or ambient whole-image motion",
        "Choose the finished-scene IMAGE_TO_VIDEO capability.",
    ),
    BLINK(
        "blinking",
        "Supply an externally finished and aligned blink pose, then prepare its POSE_BLEND capability.",
    ),
    CHARACTER_ACTION(
        "independent character action",
        "Supply a separated character, clean background and any required external pose/pivot assets.",
    ),
    SCENERY_TRAVEL(
        "moving scenery",
        "Supply extended coherent external scenery with enough declared coverage for the requested travel.",
    ),
    EFFECT(
        "an anchored effect",
        "Place and review an explicit effect anchor on a supplied layer.",
    );

    internal fun supports(capability: VideoMotionCapability, scene: VideoPreparedScene): Boolean = when (this) {
        CAMERA_OR_AMBIENT -> capability.control == VideoMotionControl.IMAGE_TO_VIDEO
        BLINK -> capability.targetType == VideoMotionTargetType.POSE && capability.control == VideoMotionControl.POSE_BLEND
        CHARACTER_ACTION -> when (capability.targetType) {
            VideoMotionTargetType.POSE -> capability.control == VideoMotionControl.POSE_BLEND
            VideoMotionTargetType.LAYER -> scene.layers.singleOrNull { it.id == capability.targetId }?.kind == VideoLayerKind.SUBJECT &&
                capability.control in CHARACTER_LAYER_CONTROLS
            else -> false
        }
        SCENERY_TRAVEL -> capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE &&
            capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y)
        EFFECT -> capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR && capability.control == VideoMotionControl.EFFECT_RATE
    }
}

enum class VideoSceneMotionMode { FLAT_IMAGE_TO_VIDEO, CONTROLLED_REGIONAL_MOTION }
enum class VideoScenePreparationStatus { READY, REVIEW_REQUIRED }

data class VideoPreparedSceneMotion(
    val status: VideoScenePreparationStatus,
    val mode: VideoSceneMotionMode,
    val look: VideoSceneLook,
    val scene: VideoPreparedScene,
    val primaryMotionPrompt: String,
    val backendMotionPrompt: String,
    val promptIssues: List<VideoPromptIssue>,
    val controls: List<VideoPreparedMotionInput>,
    val componentReviews: List<VideoSceneComponentReview>,
    val dependencies: List<VideoInputDependency>,
    val requestFingerprint: String,
) {
    /** Only actionable blockers reject preparation; review state remains explicit for V19/V21. */
    val canDispatch: Boolean get() = true
}

data class VideoPreparedMotionInput(
    val id: String,
    val intent: VideoSceneMotionIntent,
    val capability: VideoMotionCapability,
    val requestedMinimum: Double,
    val requestedMaximum: Double,
    val requestedDefaultValue: Double,
    val dependencies: List<VideoInputDependency>,
    val fingerprint: String,
)

data class VideoSceneComponentReview(
    val componentId: String,
    val kind: VideoSceneComponentKind,
    val status: VideoComponentReviewStatus,
)

enum class VideoSceneComponentKind(val label: String) {
    FINISHED_LOOK("finished look"),
    LAYER("layer"),
    MASK("mask"),
    POSE("pose"),
    SUBJECT_LANDMARK("subject landmark"),
    DEPTH_RELATION("depth relation"),
    OCCLUSION_RELATION("occlusion relation"),
    EFFECT_ANCHOR("effect anchor"),
    SCENERY_COVERAGE("scenery coverage"),
    MOTION_CAPABILITY("motion capability"),
}

sealed interface VideoScenePreparationResult {
    data class Prepared(val input: VideoPreparedSceneMotion) : VideoScenePreparationResult
    data class Rejected(val problems: List<VideoScenePreparationProblem>) : VideoScenePreparationResult {
        init { require(problems.isNotEmpty()) { "Rejected scene preparation needs an actionable problem" } }
    }
}

data class VideoScenePreparationProblem(
    val code: VideoScenePreparationProblemCode,
    val message: String,
    val nextAction: String,
    val targetId: String? = null,
)

enum class VideoScenePreparationProblemCode {
    SOURCE_PIN_MISMATCH,
    REPLACEMENT_ARTWORK_REQUIRED,
    MISSING_MOTION_CONTROL,
    DUPLICATE_CONTROL_ID,
    UNSUPPORTED_ACTION,
    UNSUPPORTED_MOTION_RANGE,
    REJECTED_COMPONENT,
    PROMPT_INVALID,
    PROMPT_COMPILATION_BLOCKED,
}

data class VideoSceneMotionInvalidation(
    val invalidatedPendingMotionInputIds: List<String>,
    val unaffectedPendingMotionInputIds: List<String>,
    val retainedCompletedResultIds: List<VideoVersionedId>,
)

private data class ResolvedControl(
    val request: VideoSceneMotionControlRequest,
    val capability: VideoMotionCapability,
    val consumed: ConsumedSceneSlice,
)

/** Ephemeral admission data, never a new scene descriptor or durable store. */
private data class ConsumedSceneSlice(
    val components: List<ConsumedSceneComponent>,
    val coordinateDependencies: List<VideoInputDependency>,
) {
    val dependencies: List<VideoInputDependency>
        get() = components.flatMap(ConsumedSceneComponent::dependencies) + coordinateDependencies
}

private data class ConsumedSceneComponent(
    val review: VideoSceneComponentReview,
    val dependencies: List<VideoInputDependency>,
)

private fun VideoAssetIdentityReview.toComponentReview(): VideoComponentReviewStatus = when (this) {
    VideoAssetIdentityReview.UNREVIEWED -> VideoComponentReviewStatus.UNREVIEWED
    VideoAssetIdentityReview.APPROVED -> VideoComponentReviewStatus.APPROVED
    VideoAssetIdentityReview.REJECTED -> VideoComponentReviewStatus.REJECTED
}

private fun problem(
    code: VideoScenePreparationProblemCode,
    message: String,
    nextAction: String,
    targetId: String? = null,
) = VideoScenePreparationProblem(code, message, nextAction, targetId)

private fun VideoVersionedId.display(): String = "$id v$version"

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")
private val CHARACTER_LAYER_CONTROLS = setOf(
    VideoMotionControl.TRANSLATE_X,
    VideoMotionControl.TRANSLATE_Y,
    VideoMotionControl.ROTATE,
    VideoMotionControl.SCALE,
    VideoMotionControl.OPACITY,
)
