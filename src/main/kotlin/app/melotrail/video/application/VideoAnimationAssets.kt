package app.melotrail.video.application

import app.melotrail.video.domain.VideoAsset
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoCoordinateSpace
import app.melotrail.video.domain.VideoDepthRelation
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoLayerTransform
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoMotionCapability
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoOcclusionRelation
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPreparedDependencyPin
import app.melotrail.video.domain.VideoPreparedLayer
import app.melotrail.video.domain.VideoPreparedMask
import app.melotrail.video.domain.VideoPreparedPose
import app.melotrail.video.domain.VideoPreparedReferencePin
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPreparedSceneSource
import app.melotrail.video.domain.VideoRect
import app.melotrail.video.domain.VideoSceneryCoverage
import app.melotrail.video.domain.VideoSubjectLandmark
import app.melotrail.video.domain.VideoVersionedId
import java.security.MessageDigest
import kotlin.math.ceil

/** Pure V18b2 validation and capability derivation for user-prepared still artwork. */
class VideoAnimationAssets {
    internal fun validate(
        request: PrepareVideoAnimationAssets,
        resolvedAssets: Map<VideoVersionedId, ResolvedVideoAnimationAsset>,
        createdAt: String,
    ): VideoAnimationAssetsResult {
        val deficiencies = mutableListOf<VideoAnimationAssetDeficiency>()
        val allInputs = request.allInputs()
        val inputIds = buildList {
            add(FINISHED_SCENE_LAYER_ID)
            addAll(request.subjectLayers.map(VideoPlacedAnimationAsset::id))
            request.cleanBackground?.let { add(it.id) }
            addAll(request.scenery.map { it.asset.id })
            addAll(request.foregroundLayers.map(VideoPlacedAnimationAsset::id))
        }
        if (inputIds.distinct().size != inputIds.size) {
            deficiencies += deficiency(
                VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                "Prepared layer IDs must be unique.",
                "Give each finished scene, subject, background, scenery, and foreground layer a distinct stable ID.",
            )
        }
        val missingReferences = allInputs.map(VideoPlacedAnimationAsset::referenceId).distinct()
            .filterNot(resolvedAssets::containsKey)
        missingReferences.forEach { id ->
            deficiencies += deficiency(
                VideoAnimationAssetDeficiencyCode.MISSING_ASSET,
                "Imported asset ${id.id} version ${id.version} is not available with its exact immutable pin.",
                "Reopen the video project and select the existing imported asset version, or import the replacement as a new version.",
                id.id,
            )
        }
        if (deficiencies.isNotEmpty()) return VideoAnimationAssetsResult.Rejected(deficiencies)

        val finished = resolvedAssets.getValue(request.finishedSceneReferenceId)
        val finishedBounds = VideoRect(
            request.coordinateSpaceId,
            0.0,
            0.0,
            finished.asset.original.width.toDouble(),
            finished.asset.original.height.toDouble(),
        )
        val viewport = finishedBounds
        val placedInputs = allInputs.drop(1)
        placedInputs.forEach { input ->
            if (!input.rotationDegrees.isFinite() || input.rotationDegrees != 0.0) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    "Asset '${input.id}' has an unsupported initial rotation; ready-asset coverage requires axis-aligned placement.",
                    "Export the intended rotation into a new external image, import it, and place it with zero initial rotation.",
                    input.id,
                )
            }
            if (input.bounds.coordinateSpaceId != request.coordinateSpaceId ||
                input.pivot?.coordinateSpaceId?.let { it != request.coordinateSpaceId } == true
            ) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    "Asset '${input.id}' does not use the prepared scene coordinate space '${request.coordinateSpaceId}'.",
                    "Place the asset and its pivot in the visible scene coordinate space before preparing motion.",
                    input.id,
                )
            }
        }

        request.subjectLayers.forEach { input ->
            requireUsableCutout(input, resolvedAssets.getValue(input.referenceId), "subject layer", deficiencies)
            if (!viewport.contains(input.bounds)) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    "Subject '${input.id}' extends outside the finished scene viewport.",
                    "Place the entire subject bounds inside the visible scene before deriving independent motion ranges.",
                    input.id,
                )
            }
        }
        request.poses.forEach { pose ->
            requireUsableCutout(pose.asset, resolvedAssets.getValue(pose.asset.referenceId), "pose", deficiencies)
            val subject = request.subjectLayers.singleOrNull { it.id == pose.subjectLayerId }
            if (subject == null) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.MISSING_SUBJECT_LAYER,
                    "Pose '${pose.asset.id}' does not identify a supplied subject layer.",
                    "Choose the subject layer that this externally prepared pose aligns with.",
                    pose.asset.id,
                )
            } else if (subject.bounds != pose.asset.bounds || subject.pivot != pose.asset.pivot) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    "Pose '${pose.asset.id}' is not aligned to subject layer '${subject.id}'.",
                    "Place the pose at the same bounds and pivot as its subject layer, then retry.",
                    pose.asset.id,
                )
            }
        }
        request.foregroundLayers.forEach { input ->
            requireUsableCutout(input, resolvedAssets.getValue(input.referenceId), "foreground layer", deficiencies)
        }
        request.masks.forEach { mask ->
            val resolved = resolvedAssets.getValue(mask.asset.referenceId)
            if (!resolved.alpha.isUsableCutout && !resolved.hasMaskContrast) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.UNUSABLE_MASK,
                    "Mask '${mask.asset.id}' has neither usable decoded alpha separation nor visible mask contrast.",
                    "Export a non-empty alpha mask or a contrasting opaque grayscale mask and retry.",
                    mask.asset.id,
                )
            }
        }
        request.cleanBackground?.let { background ->
            val resolved = resolvedAssets.getValue(background.referenceId)
            if (!background.bounds.contains(viewport) || resolved.alpha.hasNonOpaquePixels ||
                background.bounds.width > resolved.asset.original.width ||
                background.bounds.height > resolved.asset.original.height
            ) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.INCOMPLETE_CLEAN_BACKGROUND,
                    "Clean background '${background.id}' does not provide opaque decoded pixels across the finished scene viewport.",
                    "Supply or place an opaque clean background that covers the full visible scene behind controlled subject motion.",
                    background.id,
                )
            }
        }
        request.scenery.forEach { scenery ->
            val resolved = resolvedAssets.getValue(scenery.asset.referenceId)
            if (!scenery.asset.bounds.contains(scenery.coverageBounds) ||
                scenery.coverageBounds.coordinateSpaceId != request.coordinateSpaceId ||
                !scenery.coverageBounds.contains(viewport) || resolved.alpha.hasNonOpaquePixels ||
                scenery.asset.bounds.width > resolved.asset.original.width ||
                scenery.asset.bounds.height > resolved.asset.original.height
            ) {
                deficiencies += deficiency(
                    VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE,
                    "Scenery '${scenery.asset.id}' does not provide opaque decoded content and declared coverage for the initial viewport.",
                    "Supply fully opaque external scenery and place its declared coverage across the full viewport; transparent holes and translucent pixels cannot establish coverage.",
                    scenery.asset.id,
                )
            }
        }
        if (deficiencies.isNotEmpty()) return VideoAnimationAssetsResult.Rejected(deficiencies)

        val maximumX = sequenceOf(viewport.right)
            .plus(placedInputs.asSequence().map { it.bounds.right })
            .plus(request.scenery.asSequence().map { it.coverageBounds.right })
            .maxOrNull() ?: viewport.right
        val maximumY = sequenceOf(viewport.bottom)
            .plus(placedInputs.asSequence().map { it.bounds.bottom })
            .plus(request.scenery.asSequence().map { it.coverageBounds.bottom })
            .maxOrNull() ?: viewport.bottom
        if (maximumX > Int.MAX_VALUE || maximumY > Int.MAX_VALUE) {
            return VideoAnimationAssetsResult.Rejected(
                listOf(deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    "Prepared asset coordinates exceed the supported scene size.",
                    "Use bounded scene placement coordinates and retry.",
                )),
            )
        }
        val space = VideoCoordinateSpace(request.coordinateSpaceId, ceil(maximumX).toInt(), ceil(maximumY).toInt())

        val layers = buildList {
            add(finished.toLayer(FINISHED_SCENE_LAYER_ID, VideoLayerKind.FINISHED_SCENE, finishedBounds, null, 0.0))
            request.subjectLayers.forEach { add(resolvedAssets.getValue(it.referenceId).toLayer(it, VideoLayerKind.SUBJECT)) }
            request.cleanBackground?.let { add(resolvedAssets.getValue(it.referenceId).toLayer(it, VideoLayerKind.ENVIRONMENT)) }
            request.scenery.forEach { add(resolvedAssets.getValue(it.asset.referenceId).toLayer(it.asset, VideoLayerKind.SCENERY)) }
            request.foregroundLayers.forEach { add(resolvedAssets.getValue(it.referenceId).toLayer(it, VideoLayerKind.FOREGROUND)) }
        }
        val poses = request.poses.map { pose ->
            val resolved = resolvedAssets.getValue(pose.asset.referenceId)
            VideoPreparedPose(
                id = pose.asset.id,
                subjectLayerId = pose.subjectLayerId,
                image = resolved.asset.original,
                bounds = pose.asset.bounds,
                transform = transform(resolved.asset, pose.asset),
                pivot = pose.asset.pivot,
                alpha = resolved.alpha,
            )
        }
        val masks = request.masks.map { mask ->
            val resolved = resolvedAssets.getValue(mask.asset.referenceId)
            VideoPreparedMask(
                id = mask.asset.id,
                image = resolved.asset.original,
                bounds = mask.asset.bounds,
                layerIds = mask.layerIds,
                purpose = mask.purpose,
                transform = transform(resolved.asset, mask.asset),
                pivot = mask.asset.pivot,
                alpha = resolved.alpha,
            )
        }
        val coverage = request.scenery.map {
            VideoSceneryCoverage(it.coverageId, it.asset.id, it.coverageBounds)
        }
        val derivedCapabilities = deriveCapabilities(request, viewport, layers, poses, coverage)
        requestedDeficiencies(request, layers, poses, coverage, derivedCapabilities).let(deficiencies::addAll)
        if (deficiencies.isNotEmpty()) return VideoAnimationAssetsResult.Rejected(deficiencies)

        val scene = try {
            VideoPreparedScene(
                id = request.sceneId,
                source = VideoPreparedSceneSource(
                    references = allInputs.map(VideoPlacedAnimationAsset::referenceId).distinct().map { id ->
                        resolvedAssets.getValue(id).pin
                    },
                ),
                coordinateSpaces = listOf(space),
                layers = layers,
                poses = poses,
                masks = masks,
                subjectLandmarks = request.subjectLandmarks,
                effectAnchors = request.effectAnchors,
                depthRelations = request.depthRelations,
                occlusionRelations = request.occlusionRelations,
                sceneryCoverage = coverage,
                motionCapabilities = derivedCapabilities,
                dependencies = listOf(
                    VideoPreparedDependencyPin(
                        id = VALIDATOR_ID,
                        version = VALIDATOR_VERSION,
                        sha256 = sha256("$VALIDATOR_ID:$VALIDATOR_VERSION"),
                    ),
                ),
                createdAt = createdAt,
            )
        } catch (error: IllegalArgumentException) {
            return VideoAnimationAssetsResult.Rejected(
                listOf(deficiency(
                    VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                    error.message ?: "Prepared scene geometry is invalid.",
                    "Correct the visible placement, pivot, mask, depth, occlusion, or anchor metadata and retry.",
                )),
            )
        }
        return VideoAnimationAssetsResult.Validated(scene)
    }

    private fun deriveCapabilities(
        request: PrepareVideoAnimationAssets,
        viewport: VideoRect,
        layers: List<VideoPreparedLayer>,
        poses: List<VideoPreparedPose>,
        coverage: List<VideoSceneryCoverage>,
    ): List<VideoMotionCapability> {
        val capabilities = mutableListOf<VideoMotionCapability>()
        var nextId = 1
        fun add(
            targetType: VideoMotionTargetType,
            targetId: String,
            control: VideoMotionControl,
            unit: VideoMotionUnit,
            minimum: Double,
            maximum: Double,
            defaultValue: Double,
        ) {
            capabilities += VideoMotionCapability(
                id = "motion-${nextId++}",
                targetType = targetType,
                targetId = targetId,
                control = control,
                unit = unit,
                minimum = minimum,
                maximum = maximum,
                defaultValue = defaultValue,
            )
        }

        add(VideoMotionTargetType.LAYER, FINISHED_SCENE_LAYER_ID, VideoMotionControl.IMAGE_TO_VIDEO, VideoMotionUnit.RATIO, 0.0, 1.0, 1.0)
        val cleanBackgroundReady = request.cleanBackground?.bounds?.contains(viewport) == true
        if (cleanBackgroundReady) {
            layers.filter { it.kind == VideoLayerKind.SUBJECT && it.alpha?.isUsableCutout == true }.forEach { subject ->
                val minimumX = viewport.x - subject.bounds.x
                val maximumX = viewport.right - subject.bounds.right
                if (minimumX < maximumX) {
                    add(VideoMotionTargetType.LAYER, subject.id, VideoMotionControl.TRANSLATE_X, VideoMotionUnit.PIXELS, minimumX, maximumX, 0.0)
                }
                val minimumY = viewport.y - subject.bounds.y
                val maximumY = viewport.bottom - subject.bounds.bottom
                if (minimumY < maximumY) {
                    add(VideoMotionTargetType.LAYER, subject.id, VideoMotionControl.TRANSLATE_Y, VideoMotionUnit.PIXELS, minimumY, maximumY, 0.0)
                }
                if (subject.pivot != null) {
                    val maximumRotation = safeSymmetricRotation(subject.bounds, subject.pivot.point, viewport)
                    if (maximumRotation > 0.0) {
                        add(
                            VideoMotionTargetType.LAYER,
                            subject.id,
                            VideoMotionControl.ROTATE,
                            VideoMotionUnit.DEGREES,
                            -maximumRotation,
                            maximumRotation,
                            0.0,
                        )
                    }
                    val maximumScale = safeMaximumScale(subject.bounds, subject.pivot.point, viewport)
                    if (maximumScale >= 1.0) {
                        add(VideoMotionTargetType.LAYER, subject.id, VideoMotionControl.SCALE, VideoMotionUnit.RATIO, 0.01, maximumScale, 1.0)
                    }
                }
            }
            poses.forEach { pose ->
                add(VideoMotionTargetType.POSE, pose.id, VideoMotionControl.POSE_BLEND, VideoMotionUnit.RATIO, 0.0, 1.0, 0.0)
            }
        }
        coverage.forEach { item ->
            val minimumX = viewport.right - item.bounds.right
            val maximumX = viewport.x - item.bounds.x
            if (minimumX < maximumX) {
                add(VideoMotionTargetType.SCENERY_COVERAGE, item.id, VideoMotionControl.TRANSLATE_X, VideoMotionUnit.PIXELS, minimumX, maximumX, 0.0)
            }
            val minimumY = viewport.bottom - item.bounds.bottom
            val maximumY = viewport.y - item.bounds.y
            if (minimumY < maximumY) {
                add(VideoMotionTargetType.SCENERY_COVERAGE, item.id, VideoMotionControl.TRANSLATE_Y, VideoMotionUnit.PIXELS, minimumY, maximumY, 0.0)
            }
        }
        request.effectAnchors.forEach { anchor ->
            add(VideoMotionTargetType.EFFECT_ANCHOR, anchor.id, VideoMotionControl.EFFECT_RATE, VideoMotionUnit.PER_SECOND, 0.0, 60.0, 0.0)
        }
        return capabilities
    }

    private fun requestedDeficiencies(
        request: PrepareVideoAnimationAssets,
        layers: List<VideoPreparedLayer>,
        poses: List<VideoPreparedPose>,
        coverage: List<VideoSceneryCoverage>,
        capabilities: List<VideoMotionCapability>,
    ): List<VideoAnimationAssetDeficiency> = request.requestedMotions.mapNotNull { requested ->
        val capability = capabilities.singleOrNull {
            it.control == requested.control && it.targetId == requested.targetId &&
                (requested.targetType == null || requested.targetType == it.targetType)
        }
        if (capability != null && requested.minimum >= capability.minimum && requested.maximum <= capability.maximum &&
            requested.defaultValue in requested.minimum..requested.maximum
        ) return@mapNotNull null

        val layer = layers.singleOrNull { it.id == requested.targetId }
        val code = when {
            requested.control in SUBJECT_CONTROLS && layer?.kind == VideoLayerKind.SUBJECT && request.cleanBackground == null ->
                VideoAnimationAssetDeficiencyCode.MISSING_CLEAN_BACKGROUND
            requested.control in setOf(VideoMotionControl.ROTATE, VideoMotionControl.SCALE) &&
                layer?.kind == VideoLayerKind.SUBJECT && layer.pivot == null ->
                VideoAnimationAssetDeficiencyCode.MISSING_PIVOT
            requested.control == VideoMotionControl.POSE_BLEND && poses.none { it.id == requested.targetId } ->
                VideoAnimationAssetDeficiencyCode.MISSING_POSE
            requested.control in setOf(VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y) &&
                coverage.any { it.id == requested.targetId } -> VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE
            requested.targetType == VideoMotionTargetType.SCENERY_COVERAGE ->
                VideoAnimationAssetDeficiencyCode.MISSING_SCENERY
            requested.control == VideoMotionControl.EFFECT_RATE -> VideoAnimationAssetDeficiencyCode.MISSING_EFFECT_ANCHOR
            else -> VideoAnimationAssetDeficiencyCode.UNSUPPORTED_MOTION
        }
        val action = when (code) {
            VideoAnimationAssetDeficiencyCode.MISSING_CLEAN_BACKGROUND ->
                "Supply an externally prepared clean background covering the scene before requesting independent subject motion."
            VideoAnimationAssetDeficiencyCode.MISSING_PIVOT ->
                "Place a visible pivot inside the subject layer before requesting rotation."
            VideoAnimationAssetDeficiencyCode.MISSING_POSE ->
                "Supply and align the externally prepared pose image requested by this motion."
            VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE ->
                "Supply more coherent external scenery or reduce the requested travel so every translated viewport remains covered."
            VideoAnimationAssetDeficiencyCode.MISSING_SCENERY ->
                "Supply and place an externally prepared scenery image with declared coverage for this motion."
            VideoAnimationAssetDeficiencyCode.MISSING_EFFECT_ANCHOR ->
                "Place an explicit effect anchor; a generic foreground or occlusion mask does not establish one."
            else -> "Choose one of the motion capabilities supported by the supplied immutable assets."
        }
        deficiency(
            code,
            "Requested ${requested.control.name.lowercase()} motion for '${requested.targetId}' is not supported by the supplied assets and range.",
            action,
            requested.targetId,
        )
    }

    private fun requireUsableCutout(
        input: VideoPlacedAnimationAsset,
        resolved: ResolvedVideoAnimationAsset,
        label: String,
        deficiencies: MutableList<VideoAnimationAssetDeficiency>,
    ) {
        if (!resolved.asset.original.hasAlphaChannel || !resolved.alpha.isUsableCutout) {
            deficiencies += deficiency(
                VideoAnimationAssetDeficiencyCode.UNUSABLE_ALPHA,
                "${label.replaceFirstChar { it.uppercase() }} '${input.id}' is not a usable separated image: it needs both visible and transparent decoded pixels.",
                "Export a PNG with real transparent surroundings and visible artwork; an opaque RGBA or fully transparent image cannot control a separate layer.",
                input.id,
            )
        }
    }

    private companion object {
        const val FINISHED_SCENE_LAYER_ID = "finished-scene"
        const val VALIDATOR_ID = "prepared-animation-assets"
        const val VALIDATOR_VERSION = "2"
        val SUBJECT_CONTROLS = setOf(
            VideoMotionControl.TRANSLATE_X,
            VideoMotionControl.TRANSLATE_Y,
            VideoMotionControl.ROTATE,
            VideoMotionControl.SCALE,
        )
    }
}

data class PrepareVideoAnimationAssets(
    val sceneId: VideoVersionedId,
    val finishedSceneReferenceId: VideoVersionedId,
    val coordinateSpaceId: String = "scene",
    val subjectLayers: List<VideoPlacedAnimationAsset> = emptyList(),
    /** User-declared clean plate; validation checks pins, dimensions and coverage, not its artistic contents. */
    val cleanBackground: VideoPlacedAnimationAsset? = null,
    val scenery: List<VideoSceneryAnimationAsset> = emptyList(),
    val foregroundLayers: List<VideoPlacedAnimationAsset> = emptyList(),
    val poses: List<VideoPoseAnimationAsset> = emptyList(),
    val masks: List<VideoMaskAnimationAsset> = emptyList(),
    val subjectLandmarks: List<VideoSubjectLandmark> = emptyList(),
    val effectAnchors: List<VideoEffectAnchor> = emptyList(),
    val depthRelations: List<VideoDepthRelation> = emptyList(),
    val occlusionRelations: List<VideoOcclusionRelation> = emptyList(),
    val requestedMotions: List<VideoRequestedMotion> = emptyList(),
) {
    internal fun allInputs(): List<VideoPlacedAnimationAsset> = buildList {
        add(VideoPlacedAnimationAsset("finished-scene-source", finishedSceneReferenceId, VideoRect(coordinateSpaceId, 0.0, 0.0, 1.0, 1.0)))
        addAll(subjectLayers)
        cleanBackground?.let(::add)
        addAll(scenery.map(VideoSceneryAnimationAsset::asset))
        addAll(foregroundLayers)
        addAll(poses.map(VideoPoseAnimationAsset::asset))
        addAll(masks.map(VideoMaskAnimationAsset::asset))
    }
}

data class VideoPlacedAnimationAsset(
    val id: String,
    val referenceId: VideoVersionedId,
    val bounds: VideoRect,
    val pivot: VideoPlacedPoint? = null,
    val rotationDegrees: Double = 0.0,
)

data class VideoPoseAnimationAsset(val asset: VideoPlacedAnimationAsset, val subjectLayerId: String)

data class VideoMaskAnimationAsset(
    val asset: VideoPlacedAnimationAsset,
    val layerIds: List<String>,
    val purpose: VideoMaskPurpose = VideoMaskPurpose.GENERIC,
)

data class VideoSceneryAnimationAsset(
    val asset: VideoPlacedAnimationAsset,
    /** User-declared coherent pixels inside the supplied scenery, measured in scene coordinates. */
    val coverageBounds: VideoRect,
    val coverageId: String = "${asset.id}-coverage",
)

data class VideoRequestedMotion(
    val control: VideoMotionControl,
    val targetId: String,
    val minimum: Double,
    val maximum: Double,
    val defaultValue: Double = 0.0,
    val targetType: VideoMotionTargetType? = null,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && defaultValue.isFinite() && minimum <= maximum) {
            "Requested motion bounds must be finite and ordered"
        }
    }
}

sealed interface VideoAnimationAssetsResult {
    data class Validated(val scene: VideoPreparedScene) : VideoAnimationAssetsResult
    data class Rejected(val deficiencies: List<VideoAnimationAssetDeficiency>) : VideoAnimationAssetsResult {
        init { require(deficiencies.isNotEmpty()) { "Rejected animation assets need an actionable deficiency" } }
    }
}

data class VideoAnimationAssetDeficiency(
    val code: VideoAnimationAssetDeficiencyCode,
    val message: String,
    val nextAction: String,
    val targetId: String? = null,
)

enum class VideoAnimationAssetDeficiencyCode {
    MISSING_ASSET,
    UNUSABLE_ALPHA,
    UNUSABLE_MASK,
    MALFORMED_GEOMETRY,
    MISSING_SUBJECT_LAYER,
    MISSING_CLEAN_BACKGROUND,
    INCOMPLETE_CLEAN_BACKGROUND,
    MISSING_POSE,
    MISSING_PIVOT,
    MISSING_EFFECT_ANCHOR,
    MISSING_SCENERY,
    INSUFFICIENT_SCENERY_COVERAGE,
    UNSUPPORTED_MOTION,
    PROJECT_CHANGED,
    PROJECT_INVALID,
}

internal data class ResolvedVideoAnimationAsset(
    val pin: VideoPreparedReferencePin,
    val asset: VideoAsset,
    val alpha: VideoMeasuredAlpha,
    val hasMaskContrast: Boolean,
) {
    fun toLayer(
        id: String,
        kind: VideoLayerKind,
        bounds: VideoRect,
        pivot: VideoPlacedPoint?,
        rotationDegrees: Double,
    ) = VideoPreparedLayer(
        id = id,
        kind = kind,
        image = asset.original,
        bounds = bounds,
        transform = VideoLayerTransform(
            bounds.x,
            bounds.y,
            bounds.width / asset.original.width,
            bounds.height / asset.original.height,
            rotationDegrees,
        ),
        pivot = pivot,
        alpha = alpha,
        reviewStatus = VideoComponentReviewStatus.UNREVIEWED,
    )

    fun toLayer(input: VideoPlacedAnimationAsset, kind: VideoLayerKind) =
        toLayer(input.id, kind, input.bounds, input.pivot, input.rotationDegrees)
}

private fun transform(asset: VideoAsset, input: VideoPlacedAnimationAsset) = VideoLayerTransform(
    input.bounds.x,
    input.bounds.y,
    input.bounds.width / asset.original.width,
    input.bounds.height / asset.original.height,
    input.rotationDegrees,
)

private fun VideoRect.contains(other: VideoRect): Boolean =
    coordinateSpaceId == other.coordinateSpaceId && other.x >= x && other.y >= y &&
        other.right <= right && other.bottom <= bottom

private val VideoRect.right: Double get() = x + width
private val VideoRect.bottom: Double get() = y + height

private fun safeMaximumScale(bounds: VideoRect, pivot: app.melotrail.video.domain.VideoPoint, viewport: VideoRect): Double {
    val limits = buildList {
        val left = pivot.x - bounds.x
        val right = bounds.right - pivot.x
        val top = pivot.y - bounds.y
        val bottom = bounds.bottom - pivot.y
        if (left > 0.0) add((pivot.x - viewport.x) / left)
        if (right > 0.0) add((viewport.right - pivot.x) / right)
        if (top > 0.0) add((pivot.y - viewport.y) / top)
        if (bottom > 0.0) add((viewport.bottom - pivot.y) / bottom)
    }
    return limits.minOrNull() ?: 1.0
}

private fun safeSymmetricRotation(
    bounds: VideoRect,
    pivot: app.melotrail.video.domain.VideoPoint,
    viewport: VideoRect,
): Double {
    val corners = listOf(
        app.melotrail.video.domain.VideoPoint(bounds.x, bounds.y),
        app.melotrail.video.domain.VideoPoint(bounds.right, bounds.y),
        app.melotrail.video.domain.VideoPoint(bounds.x, bounds.bottom),
        app.melotrail.video.domain.VideoPoint(bounds.right, bounds.bottom),
    )
    fun fitsThroughout(degrees: Double): Boolean {
        val extent = Math.toRadians(degrees)
        return corners.all { corner ->
            val offsetX = corner.x - pivot.x
            val offsetY = corner.y - pivot.y
            // Endpoints alone (even sampled each degree) miss excursions between samples.
            // Each corner coordinate is sinusoidal; inspect every derivative zero as well.
            val angles = buildList {
                add(-extent)
                add(extent)
                val xExtremum = kotlin.math.atan2(-offsetY, offsetX)
                val yExtremum = kotlin.math.atan2(offsetX, offsetY)
                for (turn in -2..2) {
                    listOf(xExtremum, yExtremum).forEach { extremum ->
                        val angle = extremum + turn * Math.PI
                        if (angle in -extent..extent) add(angle)
                    }
                }
            }
            angles.all { radians ->
                val cosine = kotlin.math.cos(radians)
                val sine = kotlin.math.sin(radians)
                val x = pivot.x + offsetX * cosine - offsetY * sine
                val y = pivot.y + offsetX * sine + offsetY * cosine
                x >= viewport.x && x <= viewport.right && y >= viewport.y && y <= viewport.bottom
            }
        }
    }
    for (degrees in 1..180) {
        if (!fitsThroughout(degrees.toDouble())) return (degrees - 1).toDouble()
    }
    return 180.0
}

private fun deficiency(
    code: VideoAnimationAssetDeficiencyCode,
    message: String,
    nextAction: String,
    targetId: String? = null,
) = VideoAnimationAssetDeficiency(code, message, nextAction, targetId)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
