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
    ) : VideoAssemblyPlanResult
    data class Blocked(val findings: List<VideoAssemblyFinding>) : VideoAssemblyPlanResult {
        init { require(findings.isNotEmpty()) { "Blocked assembly needs an actionable finding" } }
    }
}

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
            ), advisories)
        } catch (invalid: IllegalArgumentException) {
            blocked(VideoAssemblyFindingCode.MALFORMED_PLAN, invalid.message ?: "Invalid assembly identity or text.",
                "Correct the named assembly input and plan again; no media was opened.")
        }
    }

    private data class ActionAdmission(
        val actions: List<VideoAssemblyAction>,
        val findings: List<VideoAssemblyFinding>,
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
        if (request.scheduledActions.isEmpty() && motion != null && motion.promptIssues.any { it.blocksInference }) {
            return ActionAdmission(emptyList(), motion.promptIssues.filter { it.blocksInference }.map { issue ->
                VideoAssemblyFinding(VideoAssemblyFindingCode.PROMPT_COMPILATION, issue.message,
                    "Resolve the blocking prompt-compilation finding before planning the scene.")
            })
        }
        val duplicates = request.scheduledActions.groupBy { it.id }.filterValues { it.size > 1 }.keys
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
            val shape = validControl && viewport?.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                (!needsSubject || cleanPlates.size == 1 && cleanPlates.single().reviewStatus != VideoComponentReviewStatus.REJECTED) && when (kind) {
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
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId && it.alpha?.isUsableCutout == true } && item.value in 0.0..4.0 &&
                    -item.value >= capability.minimum && item.value <= capability.maximum
                VideoAssemblyActionKind.HEAD_GESTURE -> capability.control == VideoMotionControl.ROTATE &&
                    capability.unit == VideoMotionUnit.DEGREES &&
                    capability.targetType == VideoMotionTargetType.LAYER && scene.layers.any { it.id == capability.targetId && it.kind == VideoLayerKind.SUBJECT &&
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId && it.alpha?.isUsableCutout == true } && item.value in 0.0..3.0 &&
                    -item.value >= capability.minimum && item.value <= capability.maximum &&
                    scene.masks.filter { it.purpose == VideoMaskPurpose.HEAD_REGION && it.layerIds == listOf(capability.targetId) }
                        .singleOrNull()?.let { it.alpha?.isUsableCutout == true && it.reviewStatus != VideoComponentReviewStatus.REJECTED } == true
                VideoAssemblyActionKind.STEAM -> capability.control == VideoMotionControl.EFFECT_RATE &&
                    capability.unit == VideoMotionUnit.PER_SECOND &&
                    capability.targetType == VideoMotionTargetType.EFFECT_ANCHOR && item.value > 0 && item.value <= 8 &&
                    item.value in capability.minimum..capability.maximum &&
                    scene.effectAnchors.any { anchor -> anchor.id == capability.targetId &&
                        scene.layers.any { layer -> layer.id == anchor.layerId && layer.reviewStatus != VideoComponentReviewStatus.REJECTED } &&
                        (anchor.position ?: scene.subjectLandmarks.singleOrNull { it.id == anchor.landmarkId &&
                            it.reviewStatus != VideoComponentReviewStatus.REJECTED }?.position)?.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId }
                VideoAssemblyActionKind.SCENERY_TRAVEL -> capability.targetType == VideoMotionTargetType.SCENERY_COVERAGE &&
                    capability.unit == VideoMotionUnit.PIXELS &&
                    capability.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y) &&
                    item.value != 0.0 && kotlin.math.abs(item.value) <= 16384 && item.value in capability.minimum..capability.maximum &&
                    item.startFrame == 0L && item.endFrameExclusive == frames &&
                    scene.sceneryCoverage.any { it.id == capability.targetId &&
                        it.bounds.coordinateSpaceId == viewport?.bounds?.coordinateSpaceId &&
                        scene.layers.any { layer -> layer.id == it.layerId &&
                            layer.kind in setOf(VideoLayerKind.ENVIRONMENT, VideoLayerKind.SCENERY) &&
                            layer.reviewStatus != VideoComponentReviewStatus.REJECTED } }
            }
            val requestedValue = if (kind == VideoAssemblyActionKind.BLINK) item.value * capability.maximum else item.value
            if (!shape || control.requestedMinimum > (if (kind in setOf(VideoAssemblyActionKind.BREATHING, VideoAssemblyActionKind.HEAD_GESTURE)) -item.value else requestedValue) ||
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
        return ActionAdmission(actions.sortedWith(compareBy({ it.range.start }, { it.id })), findings)
    }

    private fun blocked(code: VideoAssemblyFindingCode, explanation: String, remedy: String): VideoAssemblyPlanResult.Blocked =
        VideoAssemblyPlanResult.Blocked(listOf(VideoAssemblyFinding(code, explanation, remedy)))
}
