package app.melotrail.video.domain

import java.time.Instant
import kotlinx.serialization.Serializable

/** Immutable, versioned description of assets and controls prepared for one continuous scene. */
@Serializable
data class VideoPreparedScene(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: VideoVersionedId,
    val source: VideoPreparedSceneSource,
    val coordinateSpaces: List<VideoCoordinateSpace>,
    val layers: List<VideoPreparedLayer>,
    val poses: List<VideoPreparedPose> = emptyList(),
    val masks: List<VideoPreparedMask> = emptyList(),
    val subjectLandmarks: List<VideoSubjectLandmark> = emptyList(),
    val effectAnchors: List<VideoEffectAnchor> = emptyList(),
    val depthRelations: List<VideoDepthRelation> = emptyList(),
    val occlusionRelations: List<VideoOcclusionRelation> = emptyList(),
    val sceneryCoverage: List<VideoSceneryCoverage> = emptyList(),
    val motionCapabilities: List<VideoMotionCapability> = emptyList(),
    val dependencies: List<VideoPreparedDependencyPin>,
    val reusePolicy: VideoMotionReusePolicy = VideoMotionReusePolicy(),
    val createdAt: String,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported prepared-scene descriptor schema $schemaVersion"
        }
        require(runCatching { Instant.parse(createdAt) }.isSuccess) {
            "Prepared-scene creation timestamp must be an ISO-8601 instant"
        }
        require(coordinateSpaces.isNotEmpty()) { "A prepared scene needs at least one coordinate space" }
        require(layers.isNotEmpty()) { "A prepared scene needs at least one layer" }
        require(dependencies.isNotEmpty()) { "A prepared scene must pin its preparation dependencies" }
        requireUnique(coordinateSpaces.map(VideoCoordinateSpace::id), "Coordinate space")
        requireUnique(layers.map(VideoPreparedLayer::id), "Layer")
        requireUnique(poses.map(VideoPreparedPose::id), "Pose")
        requireUnique(masks.map(VideoPreparedMask::id), "Mask")
        requireUnique(subjectLandmarks.map(VideoSubjectLandmark::id), "Subject landmark")
        requireUnique(effectAnchors.map(VideoEffectAnchor::id), "Effect anchor")
        requireUnique(motionCapabilities.map(VideoMotionCapability::id), "Motion capability")
        requireUnique(dependencies.map(VideoPreparedDependencyPin::id), "Prepared-scene dependency")

        val spaces = coordinateSpaces.associateBy(VideoCoordinateSpace::id)
        val layerById = layers.associateBy(VideoPreparedLayer::id)
        val poseIds = poses.map(VideoPreparedPose::id).toSet()
        val maskById = masks.associateBy(VideoPreparedMask::id)
        val landmarkById = subjectLandmarks.associateBy(VideoSubjectLandmark::id)
        val anchorIds = effectAnchors.map(VideoEffectAnchor::id).toSet()
        val coverageIds = sceneryCoverage.map(VideoSceneryCoverage::id).toSet()
        require(coverageIds.size == sceneryCoverage.size) { "Scenery coverage IDs must be unique" }

        layers.forEach { layer ->
            requireRect(layer.bounds, spaces, "Layer '${layer.id}'")
            requirePlacement(layer.image, layer.bounds, layer.transform, layer.pivot, layer.alpha, spaces, "Layer '${layer.id}'")
        }
        poses.forEach { pose ->
            val subject = layerById[pose.subjectLayerId]
                ?: throw IllegalArgumentException("Pose '${pose.id}' identifies a missing layer")
            require(subject.kind == VideoLayerKind.SUBJECT) {
                "Pose '${pose.id}' must identify a subject layer"
            }
            requireRect(pose.bounds, spaces, "Pose '${pose.id}'")
            require(subject.bounds.contains(pose.bounds)) { "Pose '${pose.id}' must lie within its subject layer geometry" }
            requirePlacement(pose.image, pose.bounds, pose.transform, pose.pivot, pose.alpha, spaces, "Pose '${pose.id}'")
        }
        masks.forEach { mask ->
            requireRect(mask.bounds, spaces, "Mask '${mask.id}'")
            require(mask.layerIds.all(layerById::containsKey)) { "Mask '${mask.id}' identifies a missing layer" }
            require(mask.layerIds.all { layerById.getValue(it).bounds.intersects(mask.bounds) }) {
                "Mask '${mask.id}' must overlap every target layer in the same coordinate space"
            }
            if (mask.purpose in SUBJECT_MASK_PURPOSES) {
                require(mask.layerIds.size == 1 && layerById.getValue(mask.layerIds.single()).kind == VideoLayerKind.SUBJECT) {
                    "Semantic subject mask '${mask.id}' must identify exactly one subject layer"
                }
            }
            requirePlacement(mask.image, mask.bounds, mask.transform, mask.pivot, mask.alpha, spaces, "Mask '${mask.id}'")
        }
        subjectLandmarks.forEach { landmark ->
            val subject = layerById[landmark.subjectLayerId]
                ?: throw IllegalArgumentException("Landmark '${landmark.id}' identifies a missing layer")
            require(subject.kind == VideoLayerKind.SUBJECT) {
                "Landmark '${landmark.id}' must identify a subject layer"
            }
            requirePoint(landmark.position, spaces, "Landmark '${landmark.id}'")
            require(landmark.position.coordinateSpaceId == subject.bounds.coordinateSpaceId &&
                subject.bounds.contains(landmark.position.point)
            ) { "Landmark '${landmark.id}' must be positioned inside its subject layer" }
        }
        effectAnchors.forEach { anchor ->
            val layer = layerById[anchor.layerId]
                ?: throw IllegalArgumentException("Effect anchor '${anchor.id}' identifies a missing layer")
            anchor.landmarkId?.let { landmarkId ->
                val landmark = landmarkById[landmarkId]
                require(landmark != null && landmark.subjectLayerId == anchor.layerId) {
                    "Effect anchor '${anchor.id}' identifies an unrelated landmark"
                }
            }
            anchor.position?.let { position ->
                requirePoint(position, spaces, "Effect anchor '${anchor.id}'")
                require(position.coordinateSpaceId == layer.bounds.coordinateSpaceId && layer.bounds.contains(position.point)) {
                    "Effect anchor '${anchor.id}' must be positioned inside its target layer"
                }
            }
        }
        depthRelations.forEach { relation ->
            require(relation.nearerLayerId in layerById && relation.fartherLayerId in layerById) {
                "Depth relation identifies a missing layer"
            }
            require(layerById.getValue(relation.nearerLayerId).bounds.coordinateSpaceId ==
                layerById.getValue(relation.fartherLayerId).bounds.coordinateSpaceId
            ) { "Depth relation layers must share a coordinate space" }
        }
        requireAcyclic(depthRelations.map { it.nearerLayerId to it.fartherLayerId }, "Depth relations")
        occlusionRelations.forEach { relation ->
            require(relation.occluderLayerId in layerById && relation.occludedLayerId in layerById) {
                "Occlusion relation identifies a missing layer"
            }
            val mask = maskById[relation.maskId]
            require(mask != null && relation.occluderLayerId in mask.layerIds && relation.occludedLayerId in mask.layerIds) {
                "Occlusion relation must use a mask that covers both related layers"
            }
        }
        requireAcyclic(occlusionRelations.map { it.occluderLayerId to it.occludedLayerId }, "Occlusion relations")
        sceneryCoverage.forEach { coverage ->
            val sceneryLayer = layerById[coverage.layerId]
                ?: throw IllegalArgumentException("Scenery coverage '${coverage.id}' identifies a missing layer")
            require(sceneryLayer.kind in setOf(VideoLayerKind.ENVIRONMENT, VideoLayerKind.SCENERY)) {
                "Scenery coverage '${coverage.id}' must identify an environment or scenery layer"
            }
            requireRect(coverage.bounds, spaces, "Scenery coverage '${coverage.id}'")
            require(coverage.bounds.coordinateSpaceId == sceneryLayer.bounds.coordinateSpaceId &&
                sceneryLayer.bounds.contains(coverage.bounds)
            ) { "Scenery coverage '${coverage.id}' must lie within its layer geometry" }
        }
        motionCapabilities.forEach { capability ->
            val validTarget = when (capability.targetType) {
                VideoMotionTargetType.LAYER -> capability.targetId in layerById
                VideoMotionTargetType.POSE -> capability.targetId in poseIds
                VideoMotionTargetType.EFFECT_ANCHOR -> capability.targetId in anchorIds
                VideoMotionTargetType.SCENERY_COVERAGE -> capability.targetId in coverageIds
            }
            require(validTarget) { "Motion capability '${capability.id}' identifies a missing target" }
            capability.requireSupportedShape()
        }
    }

    /** All project-owned bytes consumed by this exact prepared-scene version. */
    fun consumedArtifacts(): List<VideoArtifact> = buildList {
        source.look?.let { add(it.artifact) }
        source.references.forEach { reference ->
            add(reference.descriptorArtifact)
            add(reference.original.artifact)
        }
        layers.forEach { add(it.image.artifact) }
        poses.forEach { add(it.image.artifact) }
        masks.forEach { add(it.image.artifact) }
        dependencies.mapNotNullTo(this) { it.artifact }
    }.distinct()

    companion object { const val CURRENT_SCHEMA_VERSION = 2 }
}

/** Compact project-document entry; the descriptor owns the complete immutable scene metadata. */
@Serializable
data class VideoPreparedSceneRecord(
    val id: VideoVersionedId,
    val artifact: VideoArtifact,
    val sourceLookId: VideoVersionedId?,
    val sourceReferenceIds: List<VideoVersionedId>,
    val consumedArtifacts: List<VideoArtifact>,
    val createdAt: String,
) {
    init {
        require(sourceLookId != null || sourceReferenceIds.isNotEmpty()) {
            "A prepared-scene record must retain a source look or reference"
        }
        require(sourceReferenceIds.distinct().size == sourceReferenceIds.size) {
            "Prepared-scene source reference IDs must be unique"
        }
        require(consumedArtifacts.distinct().size == consumedArtifacts.size) {
            "Prepared-scene consumed artifact pins must be unique"
        }
        require(artifact !in consumedArtifacts) { "A prepared-scene descriptor cannot consume itself" }
        require(runCatching { Instant.parse(createdAt) }.isSuccess) {
            "Prepared-scene record timestamp must be an ISO-8601 instant"
        }
    }
}

@Serializable
data class VideoPreparedSceneSource(
    val look: VideoPreparedLookPin? = null,
    val references: List<VideoPreparedReferencePin> = emptyList(),
) {
    init {
        require(look != null || references.isNotEmpty()) { "A prepared scene must retain a source look or reference" }
        require(references.map(VideoPreparedReferencePin::id).distinct().size == references.size) {
            "Prepared-scene source references must be unique"
        }
    }
}

@Serializable
data class VideoPreparedLookPin(val id: VideoVersionedId, val artifact: VideoArtifact)

@Serializable
data class VideoPreparedReferencePin(
    val id: VideoVersionedId,
    val descriptorArtifact: VideoArtifact,
    /** The immutable imported original; thumbnails are never preparation inputs. */
    val original: VideoAssetImage,
)

@Serializable
data class VideoPreparedDependencyPin(
    val id: String,
    val version: String,
    val sha256: String,
    /** Present when the dependency itself is copied into project-owned storage. */
    val artifact: VideoArtifact? = null,
) {
    init {
        requireToken(id, "Prepared-scene dependency ID")
        requireText(version, "Prepared-scene dependency version", 500)
        require(SHA_256.matches(sha256)) { "Prepared-scene dependency SHA-256 must be lowercase hexadecimal" }
        require(artifact == null || artifact.sha256 == sha256) {
            "A project-owned dependency artifact must match its dependency SHA-256"
        }
    }
}

@Serializable
data class VideoCoordinateSpace(val id: String, val width: Int, val height: Int) {
    init {
        requireToken(id, "Coordinate-space ID")
        require(width > 0 && height > 0) { "Coordinate-space dimensions must be positive" }
    }
}

@Serializable
data class VideoPoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" } }
}

@Serializable
data class VideoRect(val coordinateSpaceId: String, val x: Double, val y: Double, val width: Double, val height: Double) {
    init {
        requireToken(coordinateSpaceId, "Rectangle coordinate-space ID")
        require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) {
            "Rectangle geometry must be finite with positive dimensions"
        }
    }
}

@Serializable
data class VideoPlacedPoint(val coordinateSpaceId: String, val point: VideoPoint) {
    init { requireToken(coordinateSpaceId, "Point coordinate-space ID") }
}

@Serializable
data class VideoPreparedLayer(
    val id: String,
    val kind: VideoLayerKind,
    val image: VideoAssetImage,
    val bounds: VideoRect,
    /** Exact source-pixel to scene-coordinate transform supplied by placement UI or import metadata. */
    val transform: VideoLayerTransform? = null,
    val pivot: VideoPlacedPoint? = null,
    val alpha: VideoMeasuredAlpha? = null,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) { init { requireToken(id, "Layer ID") } }

@Serializable
enum class VideoLayerKind { FINISHED_SCENE, SUBJECT, ENVIRONMENT, SCENERY, FOREGROUND, EFFECT }

@Serializable
data class VideoLayerTransform(
    val translateX: Double,
    val translateY: Double,
    val scaleX: Double,
    val scaleY: Double,
    val rotationDegrees: Double = 0.0,
) {
    init {
        require(
            translateX.isFinite() && translateY.isFinite() && scaleX.isFinite() && scaleY.isFinite() &&
                rotationDegrees.isFinite() && scaleX > 0.0 && scaleY > 0.0,
        ) { "Layer transform values must be finite with positive scales" }
    }
}

/** Pixel counts measured from the decoded immutable original, not inferred from its container. */
@Serializable
data class VideoMeasuredAlpha(
    val opaquePixels: Long,
    val translucentPixels: Long,
    val transparentPixels: Long,
) {
    init {
        require(opaquePixels >= 0L && translucentPixels >= 0L && transparentPixels >= 0L) {
            "Measured alpha pixel counts must not be negative"
        }
        require(opaquePixels > 0L || translucentPixels > 0L || transparentPixels > 0L) {
            "Measured alpha needs at least one decoded pixel"
        }
    }

    val visiblePixels: Long get() = opaquePixels + translucentPixels
    val totalPixels: Long get() = visiblePixels + transparentPixels
    val hasNonOpaquePixels: Boolean get() = translucentPixels > 0L || transparentPixels > 0L
    val isUsableCutout: Boolean get() = visiblePixels > 0L && transparentPixels > 0L
}

@Serializable
data class VideoPreparedPose(
    val id: String,
    val subjectLayerId: String,
    val image: VideoAssetImage,
    val bounds: VideoRect,
    val transform: VideoLayerTransform? = null,
    val pivot: VideoPlacedPoint? = null,
    val alpha: VideoMeasuredAlpha? = null,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Pose ID")
        requireToken(subjectLayerId, "Pose subject-layer ID")
    }
}

@Serializable
data class VideoPreparedMask(
    val id: String,
    val image: VideoAssetImage,
    val bounds: VideoRect,
    val layerIds: List<String>,
    /** A generic mask never implies semantic eye, head, or effect support. */
    val purpose: VideoMaskPurpose = VideoMaskPurpose.GENERIC,
    val transform: VideoLayerTransform? = null,
    val pivot: VideoPlacedPoint? = null,
    val alpha: VideoMeasuredAlpha? = null,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Mask ID")
        require(layerIds.isNotEmpty() && layerIds.distinct().size == layerIds.size) { "Mask layer IDs must be non-empty and unique" }
        layerIds.forEach { requireToken(it, "Mask layer ID") }
    }
}

@Serializable
enum class VideoMaskPurpose { GENERIC, OCCLUSION, SUBJECT_SEGMENTATION, EYE_REGION, HEAD_REGION, EFFECT_REGION }

@Serializable
data class VideoSubjectLandmark(
    val id: String,
    val subjectLayerId: String,
    val position: VideoPlacedPoint,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Subject-landmark ID")
        requireToken(subjectLayerId, "Landmark subject-layer ID")
    }
}

@Serializable
data class VideoEffectAnchor(
    val id: String,
    val layerId: String,
    val landmarkId: String? = null,
    val position: VideoPlacedPoint? = null,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Effect-anchor ID")
        requireToken(layerId, "Effect-anchor layer ID")
        require((landmarkId == null) xor (position == null)) { "An effect anchor needs exactly one landmark or explicit position" }
        landmarkId?.let { requireToken(it, "Effect-anchor landmark ID") }
    }
}

@Serializable
data class VideoDepthRelation(
    val nearerLayerId: String,
    val fartherLayerId: String,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(nearerLayerId, "Nearer-layer ID")
        requireToken(fartherLayerId, "Farther-layer ID")
        require(nearerLayerId != fartherLayerId) { "A layer cannot be nearer than itself" }
    }
}

@Serializable
data class VideoOcclusionRelation(
    val occluderLayerId: String,
    val occludedLayerId: String,
    val maskId: String,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(occluderLayerId, "Occluder-layer ID")
        requireToken(occludedLayerId, "Occluded-layer ID")
        requireToken(maskId, "Occlusion-mask ID")
        require(occluderLayerId != occludedLayerId) { "A layer cannot occlude itself" }
    }
}

@Serializable
data class VideoSceneryCoverage(
    val id: String,
    val layerId: String,
    val bounds: VideoRect,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Scenery-coverage ID")
        requireToken(layerId, "Scenery-coverage layer ID")
    }
}

/** Each range applies independently at the supplied placement; combined motion needs timeline validation. */
@Serializable
data class VideoMotionCapability(
    val id: String,
    val targetType: VideoMotionTargetType,
    val targetId: String,
    val control: VideoMotionControl,
    val unit: VideoMotionUnit,
    val minimum: Double,
    val maximum: Double,
    val defaultValue: Double,
    val reviewStatus: VideoComponentReviewStatus = VideoComponentReviewStatus.UNREVIEWED,
) {
    init {
        requireToken(id, "Motion-capability ID")
        requireToken(targetId, "Motion-capability target ID")
        require(minimum.isFinite() && maximum.isFinite() && defaultValue.isFinite() && minimum <= maximum) {
            "Motion-capability bounds must be finite and ordered"
        }
        require(defaultValue in minimum..maximum) { "Motion-capability default must be within its bounds" }
    }
}

@Serializable
enum class VideoMotionTargetType { LAYER, POSE, EFFECT_ANCHOR, SCENERY_COVERAGE }

@Serializable
enum class VideoMotionControl { IMAGE_TO_VIDEO, TRANSLATE_X, TRANSLATE_Y, ROTATE, SCALE, OPACITY, POSE_BLEND, EFFECT_RATE }

@Serializable
enum class VideoMotionUnit { PIXELS, DEGREES, RATIO, PER_SECOND }

@Serializable
enum class VideoComponentReviewStatus { UNREVIEWED, APPROVED, REJECTED }

@Serializable
data class VideoMotionReusePolicy(
    val subtleMotionPatterns: VideoSubtleMotionReusePolicy = VideoSubtleMotionReusePolicy.ALLOWED,
    val wholeFootage: VideoWholeFootageReusePolicy = VideoWholeFootageReusePolicy.FORBIDDEN,
) {
    init {
        require(subtleMotionPatterns == VideoSubtleMotionReusePolicy.ALLOWED) {
            "Prepared scenes must explicitly allow subtle-motion pattern reuse"
        }
        require(wholeFootage == VideoWholeFootageReusePolicy.FORBIDDEN) {
            "Prepared scenes must forbid whole-footage reuse to fill duration"
        }
    }
}

@Serializable
enum class VideoSubtleMotionReusePolicy { ALLOWED, DISALLOWED }

@Serializable
enum class VideoWholeFootageReusePolicy { FORBIDDEN, ALLOWED }

private fun requireRect(rect: VideoRect, spaces: Map<String, VideoCoordinateSpace>, label: String) {
    val space = spaces[rect.coordinateSpaceId] ?: throw IllegalArgumentException("$label identifies a missing coordinate space")
    require(rect.x >= 0.0 && rect.y >= 0.0 && rect.x + rect.width <= space.width && rect.y + rect.height <= space.height) {
        "$label geometry exceeds coordinate space '${space.id}'"
    }
}

private fun requirePoint(point: VideoPlacedPoint, spaces: Map<String, VideoCoordinateSpace>, label: String) {
    val space = spaces[point.coordinateSpaceId] ?: throw IllegalArgumentException("$label identifies a missing coordinate space")
    require(point.point.x >= 0.0 && point.point.y >= 0.0 && point.point.x <= space.width && point.point.y <= space.height) {
        "$label position exceeds coordinate space '${space.id}'"
    }
}

private fun VideoRect.contains(point: VideoPoint): Boolean =
    point.x >= x && point.y >= y && point.x <= x + width && point.y <= y + height

private fun VideoRect.contains(other: VideoRect): Boolean =
    coordinateSpaceId == other.coordinateSpaceId && other.x >= x && other.y >= y &&
        other.x + other.width <= x + width && other.y + other.height <= y + height

private fun VideoRect.intersects(other: VideoRect): Boolean =
    coordinateSpaceId == other.coordinateSpaceId && x < other.x + other.width && other.x < x + width &&
        y < other.y + other.height && other.y < y + height

private fun VideoMotionCapability.requireSupportedShape() {
    when (control) {
        VideoMotionControl.IMAGE_TO_VIDEO ->
            require(
                unit == VideoMotionUnit.RATIO && targetType == VideoMotionTargetType.LAYER &&
                    minimum == 0.0 && maximum == 1.0 && defaultValue == 1.0,
            ) { "Image-to-video eligibility requires a layer target and the enabled ratio" }
        VideoMotionControl.TRANSLATE_X, VideoMotionControl.TRANSLATE_Y ->
            require(unit == VideoMotionUnit.PIXELS && targetType in setOf(VideoMotionTargetType.LAYER, VideoMotionTargetType.SCENERY_COVERAGE)) {
                "Translation controls require a layer or scenery target and pixel units"
            }
        VideoMotionControl.ROTATE ->
            require(unit == VideoMotionUnit.DEGREES && targetType == VideoMotionTargetType.LAYER) {
                "Rotation controls require a layer target and degree units"
            }
        VideoMotionControl.SCALE ->
            require(unit == VideoMotionUnit.RATIO && targetType == VideoMotionTargetType.LAYER && minimum > 0.0) {
                "Scale controls require a layer target and positive ratio bounds"
            }
        VideoMotionControl.OPACITY ->
            require(unit == VideoMotionUnit.RATIO && targetType == VideoMotionTargetType.LAYER && minimum >= 0.0 && maximum <= 1.0) {
                "Opacity controls require a layer target and bounds from zero to one"
            }
        VideoMotionControl.POSE_BLEND ->
            require(unit == VideoMotionUnit.RATIO && targetType == VideoMotionTargetType.POSE && minimum >= 0.0 && maximum <= 1.0) {
                "Pose-blend controls require a pose target and bounds from zero to one"
            }
        VideoMotionControl.EFFECT_RATE ->
            require(unit == VideoMotionUnit.PER_SECOND && targetType == VideoMotionTargetType.EFFECT_ANCHOR && minimum >= 0.0) {
                "Effect-rate controls require an effect anchor and non-negative per-second bounds"
            }
    }
}

private fun requirePlacement(
    image: VideoAssetImage,
    bounds: VideoRect,
    transform: VideoLayerTransform?,
    pivot: VideoPlacedPoint?,
    alpha: VideoMeasuredAlpha?,
    spaces: Map<String, VideoCoordinateSpace>,
    label: String,
) {
    val tolerance = 0.000_001
    transform?.let {
        require(kotlin.math.abs(it.translateX - bounds.x) <= tolerance && kotlin.math.abs(it.translateY - bounds.y) <= tolerance) {
            "$label transform translation must match its placed bounds"
        }
        require(
            kotlin.math.abs(image.width * it.scaleX - bounds.width) <= tolerance &&
                kotlin.math.abs(image.height * it.scaleY - bounds.height) <= tolerance,
        ) { "$label transform scale must map its decoded pixels to its placed bounds" }
        require(it.rotationDegrees == 0.0 || pivot != null) {
            "$label needs an explicit pivot for a non-zero rotation"
        }
    }
    pivot?.let {
        requirePoint(it, spaces, "$label pivot")
        require(it.coordinateSpaceId == bounds.coordinateSpaceId && bounds.contains(it.point)) {
            "$label pivot must lie inside its placed bounds"
        }
        transform?.takeIf { placed -> placed.rotationDegrees != 0.0 }?.let { placed ->
            val radians = Math.toRadians(placed.rotationDegrees)
            val cosine = kotlin.math.cos(radians)
            val sine = kotlin.math.sin(radians)
            val rotatedCorners = listOf(
                VideoPoint(bounds.x, bounds.y),
                VideoPoint(bounds.x + bounds.width, bounds.y),
                VideoPoint(bounds.x, bounds.y + bounds.height),
                VideoPoint(bounds.x + bounds.width, bounds.y + bounds.height),
            ).map { corner ->
                val offsetX = corner.x - it.point.x
                val offsetY = corner.y - it.point.y
                VideoPoint(
                    it.point.x + offsetX * cosine - offsetY * sine,
                    it.point.y + offsetX * sine + offsetY * cosine,
                )
            }
            val space = spaces.getValue(bounds.coordinateSpaceId)
            require(rotatedCorners.all { corner ->
                corner.x >= -tolerance && corner.y >= -tolerance &&
                    corner.x <= space.width + tolerance && corner.y <= space.height + tolerance
            }) { "$label rotated geometry exceeds coordinate space '${space.id}'" }
        }
    }
    requireAlpha(image, alpha, label)
}

private fun requireAlpha(image: VideoAssetImage, alpha: VideoMeasuredAlpha?, label: String) {
    alpha ?: return
    require(alpha.totalPixels == image.width.toLong() * image.height.toLong()) {
        "$label measured alpha must cover every decoded pixel"
    }
    require(alpha.hasNonOpaquePixels == image.hasTransparentPixels) {
        "$label measured transparency must match its immutable image facts"
    }
    require(!alpha.hasNonOpaquePixels || image.hasAlphaChannel) {
        "$label transparent pixels require a decoded alpha channel"
    }
}

private fun requireAcyclic(edges: List<Pair<String, String>>, label: String) {
    require(edges.distinct().size == edges.size) { "$label must be unique" }
    val outgoing = edges.groupBy({ it.first }, { it.second })
    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    fun visit(node: String): Boolean {
        if (node in visiting) return false
        if (!visited.add(node)) return true
        visiting += node
        val valid = outgoing[node].orEmpty().all(::visit)
        visiting -= node
        return valid
    }
    require(edges.flatMap { listOf(it.first, it.second) }.distinct().all(::visit)) { "$label must not contain cycles" }
}

private fun <T> requireUnique(values: List<T>, label: String) {
    require(values.distinct().size == values.size) { "$label IDs must be unique" }
}

private fun requireToken(value: String, label: String) {
    require(SAFE_TOKEN.matches(value)) { "$label must be a safe stable identifier" }
}

private fun requireText(value: String, label: String, maximumLength: Int) {
    require(value.isNotBlank() && value.length <= maximumLength && value.none(Char::isISOControl)) { "$label is invalid" }
}

private val SAFE_TOKEN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private val SUBJECT_MASK_PURPOSES = setOf(
    VideoMaskPurpose.SUBJECT_SEGMENTATION,
    VideoMaskPurpose.EYE_REGION,
    VideoMaskPurpose.HEAD_REGION,
)
