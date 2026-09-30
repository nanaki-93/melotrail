package app.melotrail.video.domain

import kotlinx.serialization.Serializable

/** Absolute-frame, held supplied poses. Null selects the unchanged neutral subject.
 * No blend, morph, inferred in-between pixels or whole-footage loop is performed. */
@Serializable
data class VideoPoseSequence(val steps: List<VideoPoseSequenceStep>) {
    init {
        require(steps.size in 3..16) { "A pose sequence needs 3..16 explicit steps including neutral entry and return" }
        require(steps.first().poseId == null && steps.last().poseId == null) {
            "A pose sequence must enter and return to the neutral subject"
        }
        require(steps.zipWithNext().all { (a, b) -> a.frame < b.frame }) {
            "Pose sequence frames must be strictly increasing"
        }
        require(steps.last().frame - steps.first().frame in 2..9000) { "A pose sequence spans at most 9000 frames" }
        require(steps.any { it.poseId != null }) { "A pose sequence needs a supplied action pose" }
    }

    fun poseAt(frame: Long): String? {
        require(frame in 0..MAX_JAVASCRIPT_SAFE_INTEGER) { "Pose frame must be a non-negative safe integer" }
        return steps.lastOrNull { it.frame <= frame }?.poseId
    }

    /** Shared placement and source-alpha proof, not artistic/contact approval. */
    fun subject(scene: VideoPreparedScene, capabilityId: String): VideoPreparedLayer {
        val capability = scene.motionCapabilities.single { it.id == capabilityId }
        require(capability.control == VideoMotionControl.POSE_REPLACE && capability.targetType == VideoMotionTargetType.POSE &&
            capability.unit == VideoMotionUnit.RATIO && capability.minimum == 0.0 && capability.maximum == 1.0 &&
            capability.reviewStatus != VideoComponentReviewStatus.REJECTED) { "Pose sequence needs a non-rejected POSE_REPLACE capability" }
        val poseIds = steps.mapNotNull { it.poseId }.distinct()
        require(capability.targetId in poseIds) { "Pose sequence must consume its selected capability's pose" }
        val poses = poseIds.map { id -> scene.poses.single { it.id == id } }
        val subject = scene.layers.single { it.id == poses.first().subjectLayerId }
        require(subject.kind == VideoLayerKind.SUBJECT && subject.reviewStatus != VideoComponentReviewStatus.REJECTED &&
            subject.alpha?.isUsableCutout == true) { "Pose sequence needs one usable separated neutral subject" }
        require(poses.all { it.subjectLayerId == subject.id && it.bounds == subject.bounds && it.transform == subject.transform &&
            it.pivot == subject.pivot && it.reviewStatus != VideoComponentReviewStatus.REJECTED && it.alpha?.isUsableCutout == true }) {
            "Every sequence pose must align exactly with the same neutral subject, placement, scale and pivot"
        }
        val viewport = scene.layers.single { it.kind == VideoLayerKind.FINISHED_SCENE }.bounds
        require(scene.layers.count { layer -> layer.kind == VideoLayerKind.ENVIRONMENT && layer.bounds == viewport &&
            layer.reviewStatus != VideoComponentReviewStatus.REJECTED && layer.alpha?.let { alpha ->
                !alpha.hasNonOpaquePixels && alpha.opaquePixels == layer.image.width.toLong() * layer.image.height
            } == true } == 1) { "Pose replacement requires one measured opaque clean full-viewport plate" }
        return subject
    }
}

@Serializable
data class VideoPoseSequenceStep(val frame: Long, val poseId: String? = null) {
    init {
        require(frame in 0..MAX_JAVASCRIPT_SAFE_INTEGER) { "Pose sequence frame must be a non-negative safe integer" }
        require(poseId == null || Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}").matches(poseId)) { "Pose sequence ID must be safe" }
    }
}
