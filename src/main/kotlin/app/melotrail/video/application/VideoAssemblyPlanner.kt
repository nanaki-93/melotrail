package app.melotrail.video.application

import app.melotrail.video.domain.MAX_RENDER_FRAMES
import app.melotrail.video.domain.MAX_SAFE_FRAME_INTEGER
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssembly
import app.melotrail.video.domain.VideoAssemblyChunk
import app.melotrail.video.domain.VideoAssemblyAction
import app.melotrail.video.domain.VideoAssemblyActionKind
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoAssemblyFrameRange
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.VideoAssemblyDependency
import app.melotrail.video.domain.VideoAssemblyActionDependencies
import app.melotrail.video.domain.VideoAssemblyWork
import app.melotrail.video.domain.assemblyDigest
import app.melotrail.video.domain.canonicalAssemblyDependencies
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Inputs are values only: planning never opens source artwork or starts a media process. */
data class VideoAssemblyPlanningRequest(
    val id: VideoVersionedId,
    val projectId: String,
    val preparedSceneId: VideoVersionedId,
    val preparedSceneArtifact: VideoArtifact,
    val finishedReferenceId: VideoVersionedId,
    val finishedReferenceArtifact: VideoArtifact,
    val primaryText: String,
    val actionText: String? = null,
    val cameraText: String? = null,
    val motionText: String? = null,
    val styleText: String? = null,
    val guidelineTexts: List<String> = emptyList(),
    val durationSeconds: Long = 240,
    val seed: Long,
    val supportFramesPerSide: Int = 0,
    val maximumOutputFramesPerChunk: Int = 300,
    /** A preparation snapshot is required for actions; plain text alone grants no capability. */
    val preparedMotion: VideoPreparedSceneMotion? = null,
    val scheduledActions: List<VideoAssemblyScheduledAction> = emptyList(),
    /** Explicit requested motions that must not disappear when no control could be scheduled. */
    val requestedMotionIntents: List<VideoSceneMotionIntent> = emptyList(),
    /** Explicitly confirmed absence of controlled motion; never inferred from free text. */
    val motionFreeConfirmed: Boolean = false,
    /** Must include trajectory.camera, the digest of the consumed full-scene camera path. */
    val trajectoryDependencies: List<VideoAssemblyDependency>,
    val compilerVersion: String,
    val rendererVersion: String,
    val runtimeVersion: String,
)

data class VideoAssemblyScheduledAction(
    val id: String,
    val intent: VideoSceneMotionIntent,
    val controlId: String,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val value: Double,
)

enum class VideoAssemblyFindingCode {
    DURATION, OVERFLOW, UNSAFE_INTEGER, SEED, SUPPORT, CHUNK_SIZE, MALFORMED_PLAN,
    ACTION_ID, ACTION_RANGE, ACTION_CONFLICT, ACTION_UNSUPPORTED, ACTION_COMPONENT, PROMPT_COMPILATION,
}

data class VideoAssemblyFinding(
    val code: VideoAssemblyFindingCode,
    val explanation: String,
    val remedy: String,
    val componentId: String? = null,
    val startFrame: Long? = null,
    val endFrameExclusive: Long? = null,
)

sealed interface VideoAssemblyPlanResult {
    data class Proposed(
        val assembly: VideoAssembly,
        /** Advisory prompt findings remain visible; they never manufacture an action. */
        val findings: List<VideoAssemblyFinding> = emptyList(),
        /** None of these proposal states is artifact-verified executable readiness. */
        val motionState: VideoAssemblyProposalMotionState,
    ) : VideoAssemblyPlanResult
    data class Blocked(val findings: List<VideoAssemblyFinding>) : VideoAssemblyPlanResult {
        init { require(findings.isNotEmpty()) { "Blocked assembly needs an actionable finding" } }
    }
}

enum class VideoAssemblyProposalMotionState { MOTION_FREE, UNSCHEDULED, SCHEDULED_UNVERIFIED }

data class VideoAssemblyInvalidation(
    val invalidatedPendingWorkIds: List<String>,
    val unaffectedPendingWorkIds: List<String>,
    val retainedCompletedTakeIds: List<VideoVersionedId>,
)

/** Partitions the full scene, not short clips to be looped or frozen to fill. */
class VideoAssemblyPlanner {
    fun plan(request: VideoAssemblyPlanningRequest): VideoAssemblyPlanResult {
        val frames = try {
            Math.multiplyExact(request.durationSeconds, 30L)
        } catch (_: ArithmeticException) {
            return blocked(VideoAssemblyFindingCode.OVERFLOW, "Duration times 30 fps overflows a 64-bit frame count.",
                "Choose an integer duration between 180 and 300 seconds.")
        }
        if (frames !in 0..MAX_SAFE_FRAME_INTEGER) {
            return blocked(VideoAssemblyFindingCode.UNSAFE_INTEGER, "Frame count $frames is not a JavaScript-safe nonnegative integer.",
                "Choose a duration between 180 and 300 seconds before rendering.")
        }
        if (request.durationSeconds !in 180L..300L) {
            return blocked(VideoAssemblyFindingCode.DURATION, "Duration ${request.durationSeconds} is outside 180..300 seconds.",
                "Choose an integer duration between 180 and 300 seconds.")
        }
        if (request.seed !in 0..MAX_SAFE_FRAME_INTEGER) {
            return blocked(VideoAssemblyFindingCode.SEED, "Seed ${request.seed} is not a JavaScript-safe nonnegative integer.",
                "Choose a seed in 0..$MAX_SAFE_FRAME_INTEGER.")
        }
        val support = request.supportFramesPerSide
        if (support !in 0..149) {
            return blocked(VideoAssemblyFindingCode.SUPPORT, "Support $support per side cannot fit into 300 render frames.",
                "Declare zero support for stateless motion or at most 149 frames per side.")
        }
        val outputLimit = request.maximumOutputFramesPerChunk
        if (outputLimit !in 1..MAX_RENDER_FRAMES.toInt()) {
            return blocked(VideoAssemblyFindingCode.CHUNK_SIZE,
                "Output chunk limit $outputLimit is outside 1..300 frames.",
                "Choose a positive output limit of at most 300 frames; render support is reserved within that ceiling.")
        }
        val outputSize = minOf(outputLimit, MAX_RENDER_FRAMES.toInt() - 2 * support)
        val actionResult = admitActions(request, frames)
        if (actionResult.findings.isNotEmpty()) return VideoAssemblyPlanResult.Blocked(actionResult.findings)
        val chunks = buildList {
            var start = 0L
            while (start < frames) {
                val end = Math.addExact(start, outputSize.toLong()).coerceAtMost(frames)
                val before = minOf(support.toLong(), start).toInt()
                val after = minOf(support.toLong(), frames - end).toInt()
                add(VideoAssemblyChunk(
                    output = VideoAssemblyFrameRange(start, end),
                    render = VideoAssemblyFrameRange(start - before, end + after),
                    supportBefore = before,
                    supportAfter = after,
                ))
                start = end
            }
        }
        val advisories = request.preparedMotion?.promptIssues.orEmpty().filterNot { it.blocksInference }
            .map { issue -> VideoAssemblyFinding(VideoAssemblyFindingCode.PROMPT_COMPILATION,
                issue.message, "Keep this guidance visible; supply an executable prepared control for any requested action.") }
        return try {
            VideoAssemblyPlanResult.Proposed(VideoAssembly(
                id = request.id,
                projectId = request.projectId,
                preparedSceneId = request.preparedSceneId,
                preparedSceneArtifact = request.preparedSceneArtifact,
                finishedReferenceId = request.finishedReferenceId,
                finishedReferenceArtifact = request.finishedReferenceArtifact,
                primaryText = request.primaryText,
                actionText = request.actionText,
                cameraText = request.cameraText,
                motionText = request.motionText,
                styleText = request.styleText,
                guidelineTexts = request.guidelineTexts.toList(),
                durationSeconds = request.durationSeconds,
                seed = request.seed,
                requestedSupportFrames = support,
                chunks = chunks,
                actions = actionResult.actions,
                executionDependencies = globalDependencies(request),
                actionDependencies = actionResult.actions.map { action ->
                    VideoAssemblyActionDependencies(action.id, consumedActionDependencies(request, action,
                        actionResult.controlIds.getValue(action.id)))
                },
            ), advisories, when {
                actionResult.actions.isNotEmpty() -> VideoAssemblyProposalMotionState.SCHEDULED_UNVERIFIED
                request.motionFreeConfirmed -> VideoAssemblyProposalMotionState.MOTION_FREE
                else -> VideoAssemblyProposalMotionState.UNSCHEDULED
            })
        } catch (invalid: IllegalArgumentException) {
            blocked(VideoAssemblyFindingCode.MALFORMED_PLAN, invalid.message ?: "Invalid assembly identity or text.",
                "Correct the named assembly input and plan again; no media was opened.")
        }
    }

    /** Compare only pending chunks from the previous plan. Completed takes retain their original identity
     * and continuation; a matching work hash alone never changes the take's assembly binding. */
    fun invalidatePending(
        previous: VideoAssembly,
        current: VideoAssembly,
        pendingWorkIds: Set<String>,
        completedTakeIds: List<VideoVersionedId>,
    ): VideoAssemblyInvalidation {
        require(previous.projectId == current.projectId) { "Cannot compare work from different projects" }
        val before = previous.work.associateBy(VideoAssemblyWork::id)
        val after = current.work.associateBy(VideoAssemblyWork::id)
        require(pendingWorkIds.all(before::containsKey)) { "Pending work IDs must belong to the previous assembly" }
        val pending = previous.work.map { it.id }.filter(pendingWorkIds::contains)
        val stale = pending.filter { after[it]?.fingerprint != before.getValue(it).fingerprint }
        return VideoAssemblyInvalidation(stale, pending.filterNot(stale::contains), completedTakeIds.toList())
    }

    private fun globalDependencies(request: VideoAssemblyPlanningRequest): List<VideoAssemblyDependency> =
        canonicalAssemblyDependencies(buildList {
            fun addValue(key: String, value: String) { add(VideoAssemblyDependency(key, assemblyDigest(value))) }
            addValue("project", request.projectId)
            addValue("source.finished", Json.encodeToString(request.finishedReferenceArtifact))
            request.preparedMotion?.let { motion ->
                addValue("source.look-descriptor", Json.encodeToString(motion.look.sourceDescriptor))
                addValue("source.appearance-policy", motion.look.appearancePolicy.name)
                addValue("source.identity-review", motion.look.identityReview.name)
            }
            addValue("text.primary", request.primaryText)
            listOf("action" to request.actionText, "camera" to request.cameraText,
                "motion" to request.motionText, "style" to request.styleText).forEach { (key, value) ->
                addValue("text.$key", Json.encodeToString(value))
            }
            addValue("text.guidelines", Json.encodeToString(request.guidelineTexts))
            addValue("seed", request.seed.toString())
            addValue("output", "1920x1080:1/1:30:zero:0:${request.durationSeconds}:support:${request.supportFramesPerSide}")
            addValue("version.schema", app.melotrail.video.domain.CURRENT_ASSEMBLY_SCHEMA.toString())
            addValue("version.planner", app.melotrail.video.domain.CURRENT_ASSEMBLY_PLANNER.toString())
            listOf("compiler" to request.compilerVersion, "renderer" to request.rendererVersion,
                "runtime" to request.runtimeVersion).forEach { (key, version) ->
                require(version.isNotBlank() && version.length <= 160 && version.none(Char::isISOControl)) {
                    "Supply an explicit valid $key version"
                }
                addValue("version.$key", version)
            }
            require(request.trajectoryDependencies.any { it.key == "trajectory.camera" }) {
                "Supply the consumed full-scene trajectory.camera SHA-256 pin"
            }
            addAll(request.trajectoryDependencies)
            request.preparedMotion?.let { motion ->
                val scene = motion.scene
                // The base composition is rendered even when no action intersects a chunk. A
                // scenery layer without supplied coverage is not selected for rendering.
                val coverageLayers = scene.sceneryCoverage.map { it.layerId }.toSet()
                val renderedLayers = scene.layers.filter { it.kind != VideoLayerKind.SCENERY || it.id in coverageLayers }
                    .map { it.id }.toSet()
                val renderedMasks = scene.masks.filter { mask ->
                    mask.purpose == VideoMaskPurpose.OCCLUSION && mask.layerIds.any(renderedLayers::contains)
                }.map { it.id }.toSet()
                scene.layers.filter { it.id in renderedLayers }.forEach { addValue("layer.${it.id}", Json.encodeToString(it)) }
                scene.sceneryCoverage.filter { it.layerId in renderedLayers }.forEach {
                    addValue("coverage.${it.id}", Json.encodeToString(it))
                }
                scene.masks.filter { it.id in renderedMasks }.forEach { addValue("mask.${it.id}", Json.encodeToString(it)) }
                scene.coordinateSpaces.filter { space -> renderedLayers.any { id ->
                    scene.layers.any { it.id == id && it.bounds.coordinateSpaceId == space.id }
                } }.forEach { addValue("space.${it.id}", Json.encodeToString(it)) }
                scene.depthRelations.filter { it.nearerLayerId in renderedLayers && it.fartherLayerId in renderedLayers }
                    .forEach { addValue("depth.${it.nearerLayerId}.${it.fartherLayerId}", Json.encodeToString(it)) }
                scene.occlusionRelations.filter { it.occluderLayerId in renderedLayers &&
                    it.occludedLayerId in renderedLayers }.forEach {
                    addValue("occlusion.${it.occluderLayerId}.${it.occludedLayerId}.${it.maskId}", Json.encodeToString(it))
                }
                motion.componentReviews.filter { it.componentId in renderedLayers || it.componentId in renderedMasks ||
                    scene.sceneryCoverage.any { coverage -> coverage.id == it.componentId && coverage.layerId in renderedLayers }
                }.forEach { addValue("review.${it.kind}.${it.componentId}", it.status.name) }
                scene.dependencies.forEach { addValue("preparation.${it.id}", Json.encodeToString(it)) }
            }
        })

    private fun consumedActionDependencies(request: VideoAssemblyPlanningRequest, action: VideoAssemblyAction,
        controlId: String): List<VideoAssemblyDependency> {
        val motion = requireNotNull(request.preparedMotion)
        val scene = motion.scene
        val control = motion.controls.single { it.id == controlId }
        require(control.capability.id == action.capabilityId && control.capability.targetId == action.componentId &&
            control.intent.name == action.kind.name) { "Selected control '$controlId' does not match action '${action.id}'" }
        val capability = control.capability
        val layers = linkedSetOf<String>()
        val masks = linkedSetOf<String>()
        fun layer(id: String) { layers += id }
        when (capability.targetType) {
            VideoMotionTargetType.LAYER -> layer(capability.targetId)
            VideoMotionTargetType.POSE -> layer(scene.poses.single { it.id == capability.targetId }.subjectLayerId)
            VideoMotionTargetType.EFFECT_ANCHOR -> layer(scene.effectAnchors.single { it.id == capability.targetId }.layerId)
            VideoMotionTargetType.SCENERY_COVERAGE -> layer(scene.sceneryCoverage.single { it.id == capability.targetId }.layerId)
        }
        val subject = action.kind in setOf(VideoAssemblyActionKind.BLINK, VideoAssemblyActionKind.BREATHING,
            VideoAssemblyActionKind.HEAD_GESTURE)
        val spaces = scene.layers.filter { it.id in layers }.map { it.bounds.coordinateSpaceId }.toSet()
        if (subject) layers += scene.layers.filter { it.kind == VideoLayerKind.ENVIRONMENT &&
            it.bounds.coordinateSpaceId in spaces }.map { it.id }
        layers += scene.layers.filter { it.kind == VideoLayerKind.FINISHED_SCENE ||
            it.kind == VideoLayerKind.FOREGROUND && it.bounds.coordinateSpaceId in spaces }.map { it.id }
        do {
            val old = layers.size + masks.size
            scene.masks.filter { it.layerIds.any(layers::contains) }.forEach {
                masks += it.id; layers += it.layerIds
            }
            scene.occlusionRelations.filter { it.occluderLayerId in layers || it.occludedLayerId in layers }.forEach {
                layers += it.occluderLayerId; layers += it.occludedLayerId; masks += it.maskId
            }
            scene.depthRelations.filter { it.nearerLayerId in layers || it.fartherLayerId in layers }.forEach {
                layers += it.nearerLayerId; layers += it.fartherLayerId
            }
        } while (old != layers.size + masks.size)
        val pins = buildList {
            fun pin(key: String, value: String) { add(VideoAssemblyDependency(key, assemblyDigest(value))) }
            pin("control.${action.id}", listOf(control.id, control.intent.name, control.requestedMinimum,
                control.requestedMaximum, control.requestedDefaultValue).joinToString(":"))
            pin("capability.${capability.id}", Json.encodeToString(capability))
            scene.layers.filter { it.id in layers }.forEach { pin("layer.${it.id}", Json.encodeToString(it)) }
            scene.poses.filter { it.id == capability.targetId }.forEach { pin("pose.${it.id}", Json.encodeToString(it)) }
            scene.masks.filter { it.id in masks }.forEach { pin("mask.${it.id}", Json.encodeToString(it)) }
            scene.effectAnchors.filter { it.id == capability.targetId }.forEach { anchor ->
                pin("anchor.${anchor.id}", Json.encodeToString(anchor))
                scene.subjectLandmarks.filter { it.id == anchor.landmarkId }.forEach {
                    pin("landmark.${it.id}", Json.encodeToString(it))
                }
            }
            scene.sceneryCoverage.filter { it.id == capability.targetId }.forEach {
                pin("coverage.${it.id}", Json.encodeToString(it))
            }
            scene.coordinateSpaces.filter { space -> scene.layers.any { it.id in layers && it.bounds.coordinateSpaceId == space.id } }
                .forEach { pin("space.${it.id}", Json.encodeToString(it)) }
            scene.depthRelations.filter { it.nearerLayerId in layers && it.fartherLayerId in layers }.forEach {
                pin("depth.${it.nearerLayerId}.${it.fartherLayerId}", Json.encodeToString(it))
            }
            scene.occlusionRelations.filter { it.occluderLayerId in layers && it.occludedLayerId in layers }.forEach {
                pin("occlusion.${it.occluderLayerId}.${it.occludedLayerId}.${it.maskId}", Json.encodeToString(it))
            }
            motion.componentReviews.filter { it.componentId in layers || it.componentId in masks ||
                it.componentId == capability.id || it.componentId == capability.targetId ||
                it.componentId == "${motion.look.id.id}-v${motion.look.id.version}" }.forEach {
                pin("review.${it.kind}.${it.componentId}", it.status.name)
            }
            // Preparation/tool pins are needed only for work consuming this control.
            scene.dependencies.forEach { pin("preparation.${it.id}", Json.encodeToString(it)) }
            control.dependencies.filter { it.key.startsWith("control.${control.id}.") }.forEach {
                add(VideoAssemblyDependency("prepared.${it.key}", it.sha256))
            }
        }
        return canonicalAssemblyDependencies(pins)
    }

    private data class ActionAdmission(
        val actions: List<VideoAssemblyAction>,
        val findings: List<VideoAssemblyFinding>,
        val controlIds: Map<String, String> = emptyMap(),
    )

    private fun admitActions(request: VideoAssemblyPlanningRequest, frames: Long): ActionAdmission {
        val findings = mutableListOf<VideoAssemblyFinding>()
        val motion = request.preparedMotion
        fun report(code: VideoAssemblyFindingCode, item: VideoAssemblyScheduledAction, explanation: String, remedy: String) {
            findings += VideoAssemblyFinding(code, "Action '${item.id}' on '${item.controlId}' at [${item.startFrame}, ${item.endFrameExclusive}): $explanation",
                remedy, item.controlId, item.startFrame, item.endFrameExclusive)
        }
        if (request.scheduledActions.size > 256) {
            return ActionAdmission(emptyList(), listOf(VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_RANGE,
                "At most 256 scheduled actions are supported.", "Reduce the action schedule to at most 256 entries.")))
        }
        // Prompt blockers apply to the whole proposal, not only to scheduled controls.
        motion?.promptIssues?.filter { it.blocksInference }?.forEach { issue ->
            if (request.scheduledActions.isEmpty()) {
                findings += VideoAssemblyFinding(VideoAssemblyFindingCode.PROMPT_COMPILATION, issue.message,
                    "Resolve the blocking prompt-compilation finding before planning the scene.")
            } else request.scheduledActions.forEach { item ->
                report(VideoAssemblyFindingCode.PROMPT_COMPILATION, item, issue.message,
                    "Resolve the blocking prompt-compilation finding for this component before scheduling it.")
            }
        }
        if (request.motionFreeConfirmed && (request.requestedMotionIntents.isNotEmpty() ||
                motion?.controls?.isNotEmpty() == true || request.scheduledActions.isNotEmpty())) {
            findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_CONFLICT,
                "Motion-free confirmation conflicts with requested or scheduled controls.",
                "Remove the motion-free confirmation or explicitly remove the requested motion controls.")
        }
        val unsupported = setOf(VideoSceneMotionIntent.CHARACTER_ACTION, VideoSceneMotionIntent.EFFECT,
            VideoSceneMotionIntent.CAMERA_OR_AMBIENT)
        (request.requestedMotionIntents + motion?.controls.orEmpty().map { it.intent }).distinct()
            .filter { it in unsupported }.forEach { intent ->
                findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_UNSUPPORTED,
                    "Requested ${intent.label} has no full-duration controlled assembly operation.",
                    "${intent.nextAction} For other motions supply prepared artwork and a supported control; " +
                        "flat I2V is measured only at 129 frames at 25 fps.")
            }
        if (motion?.mode == VideoSceneMotionMode.FLAT_IMAGE_TO_VIDEO && request.requestedMotionIntents.isNotEmpty()) {
            findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_UNSUPPORTED,
                "Flat I2V cannot execute requested regional motion as a continuous scene.",
                "Prepare separate controlled components and schedule supported actions; flat I2V is measured only at 129 frames at 25 fps.")
        }
        if (motion != null && (request.requestedMotionIntents.isNotEmpty() || motion.controls.isNotEmpty()) &&
            (motion.scene.id != request.preparedSceneId || motion.look.id != request.finishedReferenceId ||
                motion.look.original.artifact != request.finishedReferenceArtifact ||
                motion.primaryMotionPrompt != request.primaryText)) {
            findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_COMPONENT,
                "Requested motion does not match the prepared scene, finished reference or exact motion prompt.",
                "Prepare the selected source and exact motion text again; do not infer controls from free text.")
        }
        if (request.scheduledActions.isNotEmpty()) {
            request.requestedMotionIntents.distinct().filter { requested ->
                requested !in unsupported && request.scheduledActions.none { it.intent == requested }
            }.forEach { requested ->
                findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_UNSUPPORTED,
                    "Requested ${requested.label} has no scheduled control in this plan.",
                    "Schedule a prepared ${requested.label} control with an absolute frame range, or remove the request explicitly.")
            }
        }
        if (request.scheduledActions.isEmpty() || findings.isNotEmpty()) return ActionAdmission(emptyList(), findings)
        val duplicates = request.scheduledActions.groupBy { it.id }.filterValues { it.size > 1 }.keys
        val selectedControls = mutableMapOf<String, String>()
        val actions = request.scheduledActions.mapNotNull { item ->
            if (!Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}").matches(item.id) || item.id in duplicates) {
                report(VideoAssemblyFindingCode.ACTION_ID, item, "ID is invalid or duplicated.", "Supply one unique stable ID per action.")
                return@mapNotNull null
            }
            if (item.startFrame < 0 || item.endFrameExclusive <= item.startFrame || item.endFrameExclusive > frames ||
                item.endFrameExclusive > MAX_SAFE_FRAME_INTEGER || !item.value.isFinite() ||
                (item.intent in setOf(VideoSceneMotionIntent.BLINK, VideoSceneMotionIntent.BREATHING,
                    VideoSceneMotionIntent.HEAD_GESTURE) && item.endFrameExclusive - item.startFrame < 3)) {
                report(VideoAssemblyFindingCode.ACTION_RANGE, item, "Range or value is not bounded by the full scene.",
                    "Supply finite parameters and an absolute range within [0, $frames); subject actions need at least three frames for zero-valued entry/exit.")
                return@mapNotNull null
            }
            if (item.intent in setOf(VideoSceneMotionIntent.CHARACTER_ACTION, VideoSceneMotionIntent.EFFECT,
                    VideoSceneMotionIntent.CAMERA_OR_AMBIENT)) {
                report(VideoAssemblyFindingCode.ACTION_UNSUPPORTED, item, "Complex actions and flat I2V are not full-duration compositor controls.",
                    "Select blink, breathing, masked head gesture, anchored steam or rigid scenery; supply new prepared artwork for other actions. Flat I2V is only 129 frames at 25 fps.")
                return@mapNotNull null
            }
            if (motion == null || motion.scene.id != request.preparedSceneId ||
                motion.look.id != request.finishedReferenceId || motion.look.original.artifact != request.finishedReferenceArtifact ||
                motion.look.identityReview == app.melotrail.video.domain.VideoAssetIdentityReview.REJECTED ||
                motion.scene.source.references.none { it.id == motion.look.id &&
                    it.original == motion.look.original && it.descriptorArtifact == motion.look.sourceDescriptor } ||
                motion.primaryMotionPrompt != request.primaryText ||
                motion.mode != VideoSceneMotionMode.CONTROLLED_REGIONAL_MOTION) {
                report(VideoAssemblyFindingCode.ACTION_COMPONENT, item, "Prepared scene, finished reference or exact prompt does not match the plan.",
                    "Prepare the selected source and exact motion text again; do not infer controls from free text.")
                return@mapNotNull null
            }
            if (motion.promptIssues.any { it.blocksInference }) {
                report(VideoAssemblyFindingCode.PROMPT_COMPILATION, item,
                    motion.promptIssues.filter { it.blocksInference }.joinToString { it.message },
                    "Resolve the blocking prompt-compilation findings before scheduling this component.")
                return@mapNotNull null
            }
            val control = motion.controls.singleOrNull { it.id == item.controlId && it.intent == item.intent }
            val capability = control?.capability
            if (capability == null || motion.scene.motionCapabilities.singleOrNull { it.id == capability.id } != capability) {
                report(VideoAssemblyFindingCode.ACTION_COMPONENT, item, "No matching prepared capability exists.",
                    "Select an explicitly prepared control and capability for this component.")
                return@mapNotNull null
            }
            val scene = motion.scene
            val component = when (capability.targetType) {
                VideoMotionTargetType.POSE -> scene.poses.singleOrNull { it.id == capability.targetId }
                VideoMotionTargetType.LAYER -> scene.layers.singleOrNull { it.id == capability.targetId }
                VideoMotionTargetType.EFFECT_ANCHOR -> scene.effectAnchors.singleOrNull { it.id == capability.targetId }
                VideoMotionTargetType.SCENERY_COVERAGE -> scene.sceneryCoverage.singleOrNull { it.id == capability.targetId }
            }
            val kind = when (item.intent) {
                VideoSceneMotionIntent.BLINK -> VideoAssemblyActionKind.BLINK
                VideoSceneMotionIntent.BREATHING -> VideoAssemblyActionKind.BREATHING
                VideoSceneMotionIntent.HEAD_GESTURE -> VideoAssemblyActionKind.HEAD_GESTURE
                VideoSceneMotionIntent.STEAM -> VideoAssemblyActionKind.STEAM
                VideoSceneMotionIntent.SCENERY_TRAVEL -> VideoAssemblyActionKind.SCENERY_TRAVEL
                else -> error("Unsupported intent already rejected")
            }
            val needsSubject = kind in setOf(VideoAssemblyActionKind.BLINK, VideoAssemblyActionKind.BREATHING,
                VideoAssemblyActionKind.HEAD_GESTURE)
            val viewport = scene.layers.singleOrNull { it.kind == VideoLayerKind.FINISHED_SCENE &&
                it.bounds.x == 0.0 && it.bounds.y == 0.0 && it.bounds.width == 1920.0 && it.bounds.height == 1080.0 }
            val cleanPlates = scene.layers.filter { it.kind == VideoLayerKind.ENVIRONMENT &&
                it.bounds == viewport?.bounds }
            val validControl = control.requestedMinimum.isFinite() && control.requestedMaximum.isFinite() &&
                control.requestedDefaultValue.isFinite() && control.requestedMinimum <= control.requestedDefaultValue &&
                control.requestedDefaultValue <= control.requestedMaximum &&
                control.requestedMinimum >= capability.minimum && control.requestedMaximum <= capability.maximum &&
                when (kind) {
                    VideoAssemblyActionKind.BREATHING, VideoAssemblyActionKind.HEAD_GESTURE ->
                        control.requestedMinimum == -control.requestedMaximum && control.requestedDefaultValue == 0.0
                    VideoAssemblyActionKind.BLINK -> control.requestedDefaultValue > 0.0 && control.requestedDefaultValue <= 1.0
                    VideoAssemblyActionKind.STEAM -> control.requestedDefaultValue > 0.0 && control.requestedDefaultValue <= 8.0
                    VideoAssemblyActionKind.SCENERY_TRAVEL -> control.requestedDefaultValue != 0.0 &&
                        kotlin.math.abs(control.requestedDefaultValue) <= 16384.0
                }
            val rejected = { id: String? -> id != null && motion.componentReviews.any {
                it.componentId == id && it.status == VideoComponentReviewStatus.REJECTED
            } }
            val subject = scene.layers.singleOrNull { it.id ==
                (if (component is app.melotrail.video.domain.VideoPreparedPose) component.subjectLayerId else capability.targetId) }
            val shape = validControl && viewport != null && viewport.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                !rejected(motion.look.id.id) && !rejected(viewport.id) && !rejected(capability.id) &&
                (!needsSubject || cleanPlates.size == 1 && cleanPlates.single().reviewStatus != VideoComponentReviewStatus.REJECTED &&
                    !rejected(cleanPlates.single().id)) && when (kind) {
                VideoAssemblyActionKind.BLINK -> capability.targetType == VideoMotionTargetType.POSE && capability.control == VideoMotionControl.POSE_BLEND &&
                    capability.unit == VideoMotionUnit.RATIO &&
                    item.value > 0 && item.value <= 1 &&
                    item.value * capability.maximum in capability.minimum..capability.maximum &&
                    scene.poses.singleOrNull { it.id == capability.targetId }?.let { pose ->
                        pose.alpha?.isUsableCutout == true && pose.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId &&
                            scene.layers.any { it.id == pose.subjectLayerId && it.bounds == pose.bounds &&
                                it.kind == VideoLayerKind.SUBJECT && it.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                                it.alpha?.isUsableCutout == true }
                    } == true
                VideoAssemblyActionKind.BREATHING -> capability.control == VideoMotionControl.TRANSLATE_Y &&
                    capability.unit == VideoMotionUnit.PIXELS &&
                    capability.targetType == VideoMotionTargetType.LAYER && scene.layers.any { it.id == capability.targetId && it.kind == VideoLayerKind.SUBJECT &&
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId && it.alpha?.isUsableCutout == true &&
                        it.reviewStatus != VideoComponentReviewStatus.REJECTED } && item.value in 0.0..4.0 &&
                    -item.value >= capability.minimum && item.value <= capability.maximum
                VideoAssemblyActionKind.HEAD_GESTURE -> capability.control == VideoMotionControl.ROTATE &&
                    capability.unit == VideoMotionUnit.DEGREES &&
                    capability.targetType == VideoMotionTargetType.LAYER && scene.layers.any { it.id == capability.targetId && it.kind == VideoLayerKind.SUBJECT &&
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId && it.alpha?.isUsableCutout == true &&
                        it.reviewStatus != VideoComponentReviewStatus.REJECTED } && item.value in 0.0..3.0 &&
                    -item.value >= capability.minimum && item.value <= capability.maximum &&
                    scene.masks.filter { it.purpose == VideoMaskPurpose.HEAD_REGION && it.layerIds == listOf(capability.targetId) }
                        .singleOrNull()?.let { it.alpha?.isUsableCutout == true && it.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                            it.bounds.coordinateSpaceId == subject?.bounds?.coordinateSpaceId && !rejected(it.id) } == true
                VideoAssemblyActionKind.STEAM -> capability.control == VideoMotionControl.EFFECT_RATE &&
                    capability.unit == VideoMotionUnit.PER_SECOND &&
                    capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR && item.value > 0 && item.value <= 8 &&
                    item.value in capability.minimum..capability.maximum &&
                    scene.effectAnchors.any { anchor -> anchor.id == capability.targetId &&
                        anchor.reviewStatus != VideoComponentReviewStatus.REJECTED && !rejected(anchor.id) &&
                        scene.layers.any { layer -> layer.id == anchor.layerId &&
                            layer.reviewStatus != VideoComponentReviewStatus.REJECTED && !rejected(layer.id) } &&
                        (anchor.position ?: scene.subjectLandmarks.singleOrNull { it.id == anchor.landmarkId &&
                            it.reviewStatus != VideoComponentReviewStatus.REJECTED && !rejected(it.id) }?.position)?.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId }
                VideoAssemblyActionKind.SCENERY_TRAVEL -> capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE &&
                    capability.unit == VideoMotionUnit.PIXELS &&
                    capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y) &&
                    item.value != 0.0 && kotlin.math.abs(item.value) <= 16384 && item.value in capability.minimum..capability.maximum &&
                    item.startFrame == 0L && item.endFrameExclusive == frames &&
                    scene.sceneryCoverage.any { it.id == capability.targetId &&
                        it.reviewStatus != VideoComponentReviewStatus.REJECTED && !rejected(it.id) &&
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId &&
                        scene.layers.any { layer -> layer.id == it.layerId &&
                            layer.kind in setOf(VideoLayerKind.ENVIRONMENT, VideoLayerKind.SCENERY) &&
                            layer.reviewStatus != VideoComponentReviewStatus.REJECTED && !rejected(layer.id) } }
            }
            val rejectedConsumed = rejectedConsumedComponents(motion, capability, needsSubject)
            if (rejectedConsumed.isNotEmpty()) {
                report(VideoAssemblyFindingCode.ACTION_COMPONENT, item,
                    "Required prepared components ${rejectedConsumed.joinToString()} have rejected reviews.",
                    "Replace or correct the rejected artwork, masks or composition relationships and prepare this action again.")
                return@mapNotNull null
            }
            val requestedValue = if (kind == VideoAssemblyActionKind.BLINK) item.value * capability.maximum else item.value
            if (!shape || (needsSubject && (subject?.reviewStatus == VideoComponentReviewStatus.REJECTED ||
                    rejected(subject?.id))) || control.requestedMinimum > (if (kind in setOf(VideoAssemblyActionKind.BREATHING, VideoAssemblyActionKind.HEAD_GESTURE)) -item.value else requestedValue) ||
                control.requestedMaximum < requestedValue || capability.reviewStatus == VideoComponentReviewStatus.REJECTED ||
                motion.componentReviews.any { it.status == VideoComponentReviewStatus.REJECTED &&
                    it.componentId in setOf(capability.targetId, capability.id,
                        scene.poses.singleOrNull { pose -> pose.id == capability.targetId }?.subjectLayerId,
                        scene.sceneryCoverage.singleOrNull { coverage -> coverage.id == capability.targetId }?.layerId,
                        scene.effectAnchors.singleOrNull { anchor -> anchor.id == capability.targetId }?.layerId,
                        scene.effectAnchors.singleOrNull { anchor -> anchor.id == capability.targetId }?.landmarkId,
                        scene.masks.singleOrNull { mask -> mask.purpose == VideoMaskPurpose.HEAD_REGION &&
                            mask.layerIds == listOf(capability.targetId) }?.id) } ||
                when (component) {
                    is app.melotrail.video.domain.VideoPreparedPose -> component.reviewStatus == VideoComponentReviewStatus.REJECTED
                    is app.melotrail.video.domain.VideoPreparedLayer -> component.reviewStatus == VideoComponentReviewStatus.REJECTED
                    is app.melotrail.video.domain.VideoEffectAnchor -> component.reviewStatus == VideoComponentReviewStatus.REJECTED
                    is app.melotrail.video.domain.VideoSceneryCoverage -> component.reviewStatus == VideoComponentReviewStatus.REJECTED
                    else -> true
                }) {
                report(VideoAssemblyFindingCode.ACTION_COMPONENT, item, "Component '${capability.targetId}' lacks usable reviewed artwork or the requested bounded ${kind.name} capability.",
                    "Supply a full-canvas clean ENVIRONMENT plate for subject actions, aligned non-rejected poses, mask/anchor/coverage as appropriate, and keep the parameter within prepared compositor bounds.")
                return@mapNotNull null
            }
            val channel = if (kind == VideoAssemblyActionKind.SCENERY_TRAVEL) "camera" else
                "${kind.name.lowercase()}:${if (component is app.melotrail.video.domain.VideoPreparedPose) component.subjectLayerId else capability.targetId}"
            selectedControls[item.id] = control.id
            VideoAssemblyAction(item.id, kind, VideoAssemblyFrameRange(item.startFrame, item.endFrameExclusive),
                channel, capability.targetId, capability.id, item.value)
        }
        val subjectActions = actions.filter { it.kind in setOf(VideoAssemblyActionKind.BLINK,
            VideoAssemblyActionKind.BREATHING, VideoAssemblyActionKind.HEAD_GESTURE) }
        subjectActions.forEachIndexed { index, a ->
            subjectActions.drop(index + 1).forEach { b ->
                if (a.channel.substringAfter(':') != b.channel.substringAfter(':') &&
                    a.range.start < b.range.endExclusive && b.range.start < a.range.endExclusive) {
                    findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_CONFLICT,
                        "Subject actions '${a.id}' and '${b.id}' overlap on independent subjects at " +
                            "[${maxOf(a.range.start, b.range.start)}, ${minOf(a.range.endExclusive, b.range.endExclusive)}).",
                        "Use one prepared subject per overlapping render range or schedule these actions separately.",
                        b.componentId, maxOf(a.range.start, b.range.start), minOf(a.range.endExclusive, b.range.endExclusive))
                }
            }
        }
        actions.groupBy { it.channel }.forEach { (channel, group) ->
            group.sortedBy { it.range.start }.zipWithNext().forEach { (a, b) ->
                if (a.range.endExclusive > b.range.start) {
                    findings += VideoAssemblyFinding(VideoAssemblyFindingCode.ACTION_CONFLICT,
                        "Channel '$channel' actions '${a.id}' and '${b.id}' overlap at [${b.range.start}, ${minOf(a.range.endExclusive, b.range.endExclusive)}).",
                        "Schedule disjoint ranges on this component/channel or remove one action.", b.componentId,
                        b.range.start, minOf(a.range.endExclusive, b.range.endExclusive))
                }
            }
        }
        return ActionAdmission(actions.sortedWith(compareBy({ it.range.start }, { it.id })), findings, selectedControls)
    }

    /** Follow the same consumed regional composition graph as scene preparation. A review on
     * a connected foreground/mask/relation must not be bypassed by selecting another target. */
    private fun rejectedConsumedComponents(
        motion: VideoPreparedSceneMotion,
        capability: app.melotrail.video.domain.VideoMotionCapability,
        needsSubject: Boolean,
    ): List<String> {
        val scene = motion.scene
        val layers = linkedSetOf<String>()
        val masks = linkedSetOf<String>()
        val ids = linkedSetOf(capability.id, capability.targetId,
            "${motion.look.id.id}-v${motion.look.id.version}")
        val spaces = linkedSetOf<String>()
        fun addLayer(id: String) {
            if (layers.add(id)) scene.layers.singleOrNull { it.id == id }?.let { spaces += it.bounds.coordinateSpaceId }
        }
        when (capability.targetType) {
            VideoMotionTargetType.LAYER -> addLayer(capability.targetId)
            VideoMotionTargetType.POSE -> scene.poses.singleOrNull { it.id == capability.targetId }?.let {
                spaces += it.bounds.coordinateSpaceId
                addLayer(it.subjectLayerId)
            }
            VideoMotionTargetType.EFFECT_ANCHOR -> scene.effectAnchors.singleOrNull { it.id == capability.targetId }?.let {
                addLayer(it.layerId)
                it.landmarkId?.let(ids::add)
            }
            VideoMotionTargetType.SCENERY_COVERAGE -> scene.sceneryCoverage.singleOrNull { it.id == capability.targetId }?.let {
                addLayer(it.layerId)
            }
        }
        if (needsSubject) scene.layers.filter { it.kind == VideoLayerKind.ENVIRONMENT && it.bounds.coordinateSpaceId in spaces }
            .forEach { addLayer(it.id) }
        scene.layers.filter { it.kind == VideoLayerKind.FOREGROUND && it.bounds.coordinateSpaceId in spaces }
            .forEach { addLayer(it.id) }
        // The viewport is always consumed; add it after selecting region-local artwork.
        scene.layers.filter { it.kind == VideoLayerKind.FINISHED_SCENE }.forEach { addLayer(it.id) }
        do {
            val previousSize = layers.size
            scene.masks.filter { mask -> mask.layerIds.any(layers::contains) }.forEach { mask ->
                masks += mask.id
                mask.layerIds.forEach(::addLayer)
            }
            scene.depthRelations.filter { it.nearerLayerId in layers || it.fartherLayerId in layers }.forEach {
                addLayer(it.nearerLayerId)
                addLayer(it.fartherLayerId)
                ids += "${it.nearerLayerId}:${it.fartherLayerId}"
            }
            scene.occlusionRelations.filter { it.occluderLayerId in layers || it.occludedLayerId in layers }.forEach {
                addLayer(it.occluderLayerId)
                addLayer(it.occludedLayerId)
                masks += it.maskId
                ids += "${it.occluderLayerId}:${it.occludedLayerId}:${it.maskId}"
            }
        } while (layers.size != previousSize)
        ids += layers
        ids += masks
        val rejectedInDescriptor = buildList {
            scene.layers.filter { it.id in layers && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.poses.filter { it.id == capability.targetId && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.masks.filter { it.id in masks && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.effectAnchors.filter { it.id == capability.targetId && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.subjectLandmarks.filter { it.id in ids && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.sceneryCoverage.filter { it.id == capability.targetId && it.reviewStatus == VideoComponentReviewStatus.REJECTED }.forEach { add(it.id) }
            scene.depthRelations.filter { "${it.nearerLayerId}:${it.fartherLayerId}" in ids && it.reviewStatus == VideoComponentReviewStatus.REJECTED }
                .forEach { add("${it.nearerLayerId}:${it.fartherLayerId}") }
            scene.occlusionRelations.filter { "${it.occluderLayerId}:${it.occludedLayerId}:${it.maskId}" in ids && it.reviewStatus == VideoComponentReviewStatus.REJECTED }
                .forEach { add("${it.occluderLayerId}:${it.occludedLayerId}:${it.maskId}") }
        }
        return (rejectedInDescriptor + motion.componentReviews.filter {
            it.status == VideoComponentReviewStatus.REJECTED && it.componentId in ids
        }.map { it.componentId }).distinct().sorted()
    }

    private fun blocked(code: VideoAssemblyFindingCode, explanation: String, remedy: String): VideoAssemblyPlanResult.Blocked =
        VideoAssemblyPlanResult.Blocked(listOf(VideoAssemblyFinding(code, explanation, remedy)))
}
