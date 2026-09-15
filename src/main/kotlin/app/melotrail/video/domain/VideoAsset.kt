package app.melotrail.video.domain

import java.time.Instant
import kotlinx.serialization.Serializable

/** Immutable, hash-pinned description of one imported still-image reference. */
@Serializable
data class VideoAsset(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: VideoVersionedId,
    val original: VideoAssetImage,
    val thumbnail: VideoAssetImage,
    val role: VideoReferenceRole? = null,
    val source: VideoAssetSource,
    val rights: VideoAssetRights? = null,
    val usageIntent: VideoAssetUsageIntent? = null,
    val creationProvenance: VideoAssetCreationProvenance? = null,
    val identityReview: VideoAssetIdentityReview = VideoAssetIdentityReview.UNREVIEWED,
    val createdAt: String,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported video asset descriptor schema $schemaVersion"
        }
        require(original.artifact.relativePath != thumbnail.artifact.relativePath) {
            "Original and thumbnail must use distinct immutable paths"
        }
        require(thumbnail.format == VideoImageFormat.PNG) { "Video asset thumbnails must be PNG" }
        require(thumbnail.width <= THUMBNAIL_MAX_DIMENSION && thumbnail.height <= THUMBNAIL_MAX_DIMENSION) {
            "Video asset thumbnail dimensions exceed the descriptor limit"
        }
        require(runCatching { Instant.parse(createdAt) }.isSuccess) {
            "Video asset creation timestamp must be an ISO-8601 instant"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val THUMBNAIL_MAX_DIMENSION = 512
    }
}

@Serializable
data class VideoAssetImage(
    val artifact: VideoArtifact,
    val format: VideoImageFormat,
    val mediaType: String,
    val width: Int,
    val height: Int,
    val encodedBytes: Long,
    val hasAlphaChannel: Boolean,
    val hasTransparentPixels: Boolean,
) {
    init {
        require(mediaType == format.mediaType) { "Image media type must match its decoded format" }
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(encodedBytes > 0L) { "Image byte size must be positive" }
        require(!hasTransparentPixels || hasAlphaChannel) {
            "Transparent pixels require an alpha channel"
        }
    }
}

@Serializable
enum class VideoImageFormat(val mediaType: String, val fileExtension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
}

@Serializable
enum class VideoReferenceRole {
    SUBJECT,
    CHARACTER,
    OUTFIT,
    ENVIRONMENT,
    STYLE,
    COMPLETE_SCENE,
}

/** The selected source is provenance only; it is never needed after the owned copy is published. */
@Serializable
data class VideoAssetSource(
    val originalFileName: String,
    val originalLocation: String? = null,
    val description: String? = null,
) {
    init {
        requireText(originalFileName, "Original filename", 512)
        originalLocation?.let { requireText(it, "Original location", 4_096) }
        description?.let { requireText(it, "Source description", 2_000) }
    }
}

/** All fields are user-supplied provenance. Their absence means rights are unknown. */
@Serializable
data class VideoAssetRights(
    val creator: String? = null,
    val license: String? = null,
    val ownershipStatement: String? = null,
    val permittedUses: List<String> = emptyList(),
) {
    init {
        creator?.let { requireText(it, "Creator", 500) }
        license?.let { requireText(it, "License", 1_000) }
        ownershipStatement?.let { requireText(it, "Ownership statement", 2_000) }
        require(permittedUses.size <= 32 && permittedUses.distinct().size == permittedUses.size) {
            "Permitted uses must be unique and bounded"
        }
        permittedUses.forEach { requireText(it, "Permitted use", 500) }
        require(creator != null || license != null || ownershipStatement != null || permittedUses.isNotEmpty()) {
            "An empty rights statement must be omitted"
        }
    }
}

@Serializable
enum class VideoAssetUsageIntent {
    PRODUCTION_REFERENCE,
    INSPIRATION_ONLY,
}

/** Optional factual provenance for an already-generated input; import does not infer any field. */
@Serializable
data class VideoAssetCreationProvenance(
    val provider: String? = null,
    val model: String? = null,
    val prompt: String? = null,
    val referenceIds: List<VideoVersionedId> = emptyList(),
) {
    init {
        provider?.let { requireText(it, "Creation provider", 500) }
        model?.let { requireText(it, "Creation model", 500) }
        prompt?.let { requireText(it, "Creation prompt", 20_000) }
        require(referenceIds.size <= 64 && referenceIds.distinct().size == referenceIds.size) {
            "Creation reference IDs must be unique and bounded"
        }
        require(provider != null || model != null || prompt != null || referenceIds.isNotEmpty()) {
            "Empty creation provenance must be omitted"
        }
    }
}

@Serializable
enum class VideoAssetIdentityReview {
    UNREVIEWED,
    APPROVED,
    REJECTED,
}

private fun requireText(value: String, label: String, maximumLength: Int) {
    require(value.isNotBlank() && value.length <= maximumLength && value.none(Char::isISOControl)) {
        "$label is invalid"
    }
}
