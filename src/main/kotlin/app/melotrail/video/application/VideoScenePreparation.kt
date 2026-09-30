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
import app.melotrail.video.domain.VideoPoseSequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import app.melotrail.video.domain.VideoPreparedPose
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.MAX_JAVASCRIPT_SAFE_INTEGER
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
                    "Keep the request inside the measured asset range; check motion-runtime.json separately for renderer limits.",
                    control.id,
                )
                return@mapNotNull null
            }
            if (control.poseSequence != null || capability.control == VideoMotionControl.POSE_REPLACE) {
                val failure = runCatching {
                    require(control.intent == VideoSceneMotionIntent.CHARACTER_ACTION && control.minimum == 0.0 &&
                        control.maximum == 1.0 && control.defaultValue == 1.0) { "Select an enabled 0..1 supplied-pose sequence explicitly" }
                    requireNotNull(control.poseSequence) { "Supply absolute-frame pose steps with neutral entry and return" }
                        .subject(scene, capability.id)
                }.exceptionOrNull()
                if (failure != null) {
                    problems += unsupported(control, failure.message ?: "Invalid supplied-pose sequence.")
                    return@mapNotNull null
                }
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
            add(VideoInputDependency("look.identity-review", digest(look.identityReview.name)))
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
                poseSequence = control.poseSequence,
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
                promptIssues = compiledPrompt.issues + inputs.mapNotNull(::rendererLimitation),
                controls = inputs,
                componentReviews = reviews,
                dependencies = dependencies,
                requestFingerprint = fingerprint(dependencies),
            ),
        )
    }

    /** Compile only compositor semantics with an exact, auditable mapping to renderer operations. */
    fun compileControlledControls(input: VideoPreparedSceneMotion): List<kotlinx.serialization.json.JsonObject> {
        val compiled = input.controls.mapNotNull { control ->
            val capability = control.capability
            control.poseSequence?.let { sequence ->
                require(control.intent == VideoSceneMotionIntent.CHARACTER_ACTION && control.requestedMinimum == 0.0 &&
                    control.requestedMaximum == 1.0 && control.requestedDefaultValue == 1.0)
                sequence.subject(input.scene, capability.id)
                return@mapNotNull buildJsonObject {
                    put("id", control.id); put("capabilityId", capability.id); put("kind", "poseSequence")
                    put("sequence", Json.encodeToJsonElement(sequence))
                }
            }
            val (kind, field, value) = when (control.intent) {
                VideoSceneMotionIntent.BLINK -> {
                    require(capability.targetType == VideoMotionTargetType.POSE && capability.control == VideoMotionControl.POSE_BLEND)
                    require(control.requestedDefaultValue > 0 && control.requestedDefaultValue <= 1 && capability.maximum >= control.requestedDefaultValue) {
                        "${control.id}: blink needs a positive amount within the pose blend's 0..1 range."
                    }
                    Triple("blink", "amount", control.requestedDefaultValue)
                }
                VideoSceneMotionIntent.BREATHING, VideoSceneMotionIntent.HEAD_GESTURE -> when (capability.control) {
                    VideoMotionControl.TRANSLATE_Y -> {
                        require(control.intent == VideoSceneMotionIntent.BREATHING && capability.targetType == VideoMotionTargetType.LAYER && capability.unit == VideoMotionUnit.PIXELS) {
                            "${control.id}: generic translation is unsupported; explicitly select bounded breathing."
                        }
                        require(control.requestedMinimum == -control.requestedMaximum && control.requestedDefaultValue == 0.0) {
                            "${control.id}: breathing needs a symmetric requested range centered at zero; choose equal negative/positive bounds and a zero default."
                        }
                        val amplitude = control.requestedMaximum
                        require(amplitude in 0.0..4.0 && capability.minimum <= -amplitude && capability.maximum >= amplitude) {
                            "${control.id}: breathing needs symmetric subject motion within 4 px and the prepared capability bounds."
                        }
                        Triple("breathing", "amplitudePixels", amplitude)
                    }
                    VideoMotionControl.ROTATE -> {
                        require(control.intent == VideoSceneMotionIntent.HEAD_GESTURE && capability.targetType == VideoMotionTargetType.LAYER &&
                            input.scene.masks.count { it.purpose == VideoMaskPurpose.HEAD_REGION && it.layerIds == listOf(capability.targetId) &&
                                it.reviewStatus != VideoComponentReviewStatus.REJECTED && it.alpha?.isUsableCutout == true } == 1) {
                            "${control.id}: generic rotation is unsupported; explicitly select head gesture with one usable, reviewed HEAD_REGION mask."
                        }
                        require(control.requestedMinimum == -control.requestedMaximum && control.requestedDefaultValue == 0.0) {
                            "${control.id}: head gesture needs a symmetric requested range centered at zero; choose equal negative/positive bounds and a zero default."
                        }
                        val amplitude = control.requestedMaximum
                        require(amplitude in 0.0..3.0 && capability.minimum <= -amplitude && capability.maximum >= amplitude) {
                            "${control.id}: head gesture needs symmetric motion within 3° and the prepared capability bounds."
                        }
                        Triple("headGesture", "amplitudeDegrees", amplitude)
                    }
                    else -> throw IllegalArgumentException("${control.id}: generic character motion is unsupported; use bounded breathing or a masked head gesture.")
                }
                VideoSceneMotionIntent.CHARACTER_ACTION -> throw IllegalArgumentException("${control.id}: generic character motion is unsupported; select bounded breathing or masked head gesture explicitly.")
                VideoSceneMotionIntent.EFFECT -> throw IllegalArgumentException("${control.id}: generic effects are unsupported; explicitly select anchored steam.")
                VideoSceneMotionIntent.STEAM -> {
                    require(capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR && capability.control == VideoMotionControl.EFFECT_RATE)
                    require(control.requestedDefaultValue > 0 && control.requestedDefaultValue <= 8) {
                        "${control.id}: anchored steam needs a positive rate no greater than 8/s."
                    }
                    Triple("steam", "ratePerSecond", control.requestedDefaultValue)
                }
                VideoSceneMotionIntent.SCENERY_TRAVEL -> return@mapNotNull null
                VideoSceneMotionIntent.CAMERA_OR_AMBIENT -> throw IllegalArgumentException("${control.id}: whole-image motion is I2V, not a controlled compositor operation.")
            }
            require(value.isFinite()) { "${control.id}: compiled motion value must be finite." }
            kotlinx.serialization.json.buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive(control.id))
                put("capabilityId", kotlinx.serialization.json.JsonPrimitive(capability.id))
                put("kind", kotlinx.serialization.json.JsonPrimitive(kind))
                put(field, kotlinx.serialization.json.JsonPrimitive(value))
            }
        }
        require(compiled.size <= 16) { "At most 16 controlled operations are supported." }
        val kinds = compiled.map { it.getValue("kind").toString() }
        require(kinds.count { it == "\"poseSequence\"" } <= 1 &&
            ("\"poseSequence\"" !in kinds || kinds.none { it in listOf("\"blink\"", "\"breathing\"", "\"headGesture\"") })) {
            "A supplied-pose sequence cannot combine with blink, breathing or head gesture; supply those actions in the pose art."
        }
        require(listOf("blink", "breathing", "headGesture").all { kind -> kinds.count { it == "\"$kind\"" } <= 1 }) {
            "Only one blink, breathing and head gesture control per subject can be composed; remove ambiguous duplicates."
        }
        return compiled
    }

    /** One explicit rigid camera over one prepared coverage. The durable descriptor validates
     * every frame, source capability and shutter sample before admission. Never infer sections. */
    fun compileControlledScenery(input: VideoPreparedSceneMotion, start: Long, end: Long, width: Int, height: Int): JsonObject? {
        val travel = input.controls.filter { it.intent == VideoSceneMotionIntent.SCENERY_TRAVEL }
        if (travel.isEmpty()) return null
        require(travel.size == 1) { "Select one scenery trajectory; multiple independent camera controls are ambiguous." }
        val selected = travel.single()
        val capability = selected.capability
        require(capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE && capability.unit == VideoMotionUnit.PIXELS &&
            capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y)) {
            "${selected.id}: scenery needs a prepared pixel-translation coverage capability."
        }
        val amount = selected.requestedDefaultValue
        require(amount.isFinite() && amount != 0.0 && kotlin.math.abs(amount) <= 16384) {
            "${selected.id}: select an explicit nonzero scenery travel of at most 16,384 px."
        }
        val duration = Math.subtractExact(end, start)
        require(start in 0..MAX_JAVASCRIPT_SAFE_INTEGER && end in 1..MAX_JAVASCRIPT_SAFE_INTEGER && duration in 2..9000) {
            "${selected.id}: moving scenery needs 2..9000 frames with JavaScript-safe absolute bounds."
        }
        val coverage = input.scene.sceneryCoverage.single { it.id == capability.targetId }
        val layer = input.scene.layers.single { it.id == coverage.layerId }
        require(coverage.reviewStatus != VideoComponentReviewStatus.REJECTED && layer.reviewStatus != VideoComponentReviewStatus.REJECTED &&
            layer.kind in setOf(VideoLayerKind.ENVIRONMENT, VideoLayerKind.SCENERY)) {
            "${selected.id}: supply non-rejected, prepared scenery artwork and coverage."
        }
        val viewport = input.scene.layers.single { it.kind == VideoLayerKind.FINISHED_SCENE }.bounds
        require(viewport.x == 0.0 && viewport.y == 0.0 && viewport.width == width.toDouble() && viewport.height == height.toDouble() &&
            coverage.bounds.coordinateSpaceId == viewport.coordinateSpaceId) {
            "${selected.id}: scenery coverage must share the prepared output viewport."
        }
        // Camera travel opposes the selected layer displacement. Start at prepared placement;
        // the descriptor validator rejects insufficient artwork or capability at any sample.
        val x = if (capability.control == VideoMotionControl.TRANSLATE_X) -amount else 0.0
        val y = if (capability.control == VideoMotionControl.TRANSLATE_Y) -amount else 0.0
        return buildJsonObject {
            put("schema", "melotrail-rigid-scenery-v1")
            put("mode", "moving")
            put("viewport", buildJsonObject {
                put("coordinateSpaceId", viewport.coordinateSpaceId)
                put("x", 0); put("y", 0); put("width", width); put("height", height)
            })
            put("camera", buildJsonObject {
                put("startFrame", start); put("durationFrames", duration)
                put("travelXPixels", x); put("travelYPixels", y)
                put("motionBlurSamples", 3); put("shutterFraction", 0.5)
            })
            put("planes", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("id", coverage.id)
                    put("sections", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("coverageId", coverage.id)
                            put("worldX", coverage.bounds.x); put("worldY", coverage.bounds.y)
                            put("startFrame", start); put("endFrameExclusive", end)
                        })
                    })
                })
            })
        }
    }

    /** Asset eligibility is separate from the selected compositor's limited semantic controls. */
    private fun rendererLimitation(input: VideoPreparedMotionInput): VideoPromptIssue? {
        val detail = rendererUnsupportedReason(input) ?: when (input.intent) {
            VideoSceneMotionIntent.CAMERA_OR_AMBIENT, VideoSceneMotionIntent.BLINK -> return null
            VideoSceneMotionIntent.CHARACTER_ACTION -> if (input.poseSequence != null)
                "Supplied poses are held and replaced at explicit absolute frames, not blended or interpolated; held frames are not native articulated motion."
                else "Generic character actions are not executable; select bounded breathing or a masked head gesture explicitly."
            VideoSceneMotionIntent.BREATHING, VideoSceneMotionIntent.HEAD_GESTURE -> return null
            VideoSceneMotionIntent.SCENERY_TRAVEL -> "Scenery requires an explicit nonzero rigid-camera travel and full-trajectory coverage validation at descriptor compilation."
            VideoSceneMotionIntent.EFFECT -> "Generic effects are unsupported; explicitly select anchored steam (≤8/s, rise ≤28 px/s)."
            VideoSceneMotionIntent.STEAM -> return null
        }
        return VideoPromptIssue(
            VideoPromptIssueCode.INFORMATIONAL_LIMITATION,
            "Motion input '${input.id}': $detail Asset capability and canDispatch mean preparation eligibility, not renderer or VG2 execution readiness; see motion-runtime.json.",
            blocksInference = false,
        )
    }

    /** Definitive compositor mismatches; advisory trajectory/semantic checks remain separate. */
    internal fun rendererUnsupportedReason(input: VideoPreparedMotionInput): String? = when (input.intent) {
        VideoSceneMotionIntent.CAMERA_OR_AMBIENT -> null // I2V is not a compositor control; generation rejects flat mode.
        VideoSceneMotionIntent.BLINK -> if (input.requestedMinimum < 0 || input.requestedMaximum > 1)
            "Blink exceeds the compositor's 0..1 amount." else null
        VideoSceneMotionIntent.CHARACTER_ACTION -> when (input.capability.control) {
            VideoMotionControl.POSE_REPLACE -> if (input.poseSequence == null) "Supply an explicit neutral-entry/return pose sequence." else null
            VideoMotionControl.TRANSLATE_Y -> "Generic subject translation is not a compositor control; only bounded breathing (≤4 px) is implemented."
            VideoMotionControl.ROTATE -> "Generic subject rotation is not a compositor control; explicitly select a head gesture (≤3°) with a HEAD_REGION mask."
            VideoMotionControl.TRANSLATE_X -> "Independent subject translation is not implemented by the compositor."
            else -> "Independent subject ${input.capability.control} is not implemented by the compositor."
        }
        VideoSceneMotionIntent.BREATHING, VideoSceneMotionIntent.HEAD_GESTURE -> null
        VideoSceneMotionIntent.SCENERY_TRAVEL -> if (input.requestedMinimum < -16384 || input.requestedMaximum > 16384)
            "Travel exceeds the compositor's 16,384 px limit." else null
        VideoSceneMotionIntent.EFFECT -> "Generic effects are unsupported; explicitly select anchored steam."
        VideoSceneMotionIntent.STEAM -> if (input.requestedMinimum < 0 || input.requestedMaximum > 8)
            "Effect rate exceeds the compositor's steam limit of 8/s (rise ≤28 px/s); arbitrary effects are not implemented." else null
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
                    request.poseSequence.toString(),
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
        request.poseSequence?.steps?.mapNotNull { it.poseId }?.distinct()?.filter { it != capability.targetId }?.forEach { id ->
            addPose(scene.poses.single { it.id == id })
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
    val poseSequence: VideoPoseSequence? = null,
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
    BREATHING("bounded breathing", "Select a subject TRANSLATE_Y capability with a clean plate and motion within 4 px."),
    HEAD_GESTURE("masked head gesture", "Supply one usable HEAD_REGION mask and select a subject ROTATE capability within 3 degrees."),
    SCENERY_TRAVEL(
        "moving scenery",
        "Supply extended coherent external scenery with enough declared coverage for the requested travel.",
    ),
    STEAM("anchored steam", "Place and review an explicit steam anchor on a supplied layer."),
    EFFECT(
        "an anchored effect",
        "Place and review an explicit effect anchor on a supplied layer.",
    );

    internal fun supports(capability: VideoMotionCapability, scene: VideoPreparedScene): Boolean = when (this) {
        CAMERA_OR_AMBIENT -> capability.control == VideoMotionControl.IMAGE_TO_VIDEO
        BLINK -> capability.targetType == VideoMotionTargetType.POSE && capability.control == VideoMotionControl.POSE_BLEND
        CHARACTER_ACTION -> when (capability.targetType) {
            VideoMotionTargetType.POSE -> capability.control in setOf(VideoMotionControl.POSE_BLEND, VideoMotionControl.POSE_REPLACE)
            VideoMotionTargetType.LAYER -> scene.layers.singleOrNull { it.id == capability.targetId }?.kind == VideoLayerKind.SUBJECT &&
                capability.control in CHARACTER_LAYER_CONTROLS
            else -> false
        }
        BREATHING -> capability.targetType == VideoMotionTargetType.LAYER && capability.control == VideoMotionControl.TRANSLATE_Y &&
            scene.layers.singleOrNull { it.id == capability.targetId }?.kind == VideoLayerKind.SUBJECT
        HEAD_GESTURE -> capability.targetType == VideoMotionTargetType.LAYER && capability.control == VideoMotionControl.ROTATE &&
            scene.layers.singleOrNull { it.id == capability.targetId }?.kind == VideoLayerKind.SUBJECT
        SCENERY_TRAVEL -> capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE &&
            capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y)
        EFFECT, STEAM -> capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR && capability.control == VideoMotionControl.EFFECT_RATE
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
    /** Preparation-only eligibility; does not establish VG2 execution or renderer readiness. */
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
    val poseSequence: VideoPoseSequence? = null,
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
