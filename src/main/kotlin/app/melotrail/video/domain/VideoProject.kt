package app.melotrail.video.domain

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Independent persisted state for the Video workspace. Media-specific details
 * remain in their owning records; this aggregate retains only immutable pins,
 * current selections, export history, and optimistic-concurrency state.
 */
@Serializable
data class VideoProject(
    val id: String,
    val name: String,
    val createdAt: String,
    val applicationVersion: String? = null,
    val referenceVersions: List<VideoReferenceRecord> = emptyList(),
    val lookVersions: List<VideoLookRecord> = emptyList(),
    val preparedSceneVersions: List<VideoPreparedSceneRecord> = emptyList(),
    val takeVersions: List<VideoTakeRecord> = emptyList(),
    val selectedReferenceIds: List<VideoVersionedId> = emptyList(),
    val selectedLookId: VideoVersionedId? = null,
    val selectedTakeIds: List<VideoVersionedId> = emptyList(),
    val exportRecords: List<VideoExportRecord> = emptyList(),
    /** Monotonic optimistic-concurrency revision for the persisted document. */
    val revision: Long = 0L,
) {
    init {
        requireSafeId(id, "Video project")
        require(name.isNotBlank() && name.length <= 120 && name.none(Char::isISOControl)) {
            "Video project name is invalid"
        }
        requireTimestamp(createdAt, "Video project creation")
        require(applicationVersion == null || applicationVersion.isNotBlank() && applicationVersion.length <= 80) {
            "Application version is invalid"
        }
        require(revision >= 0L) { "Video project revision must not be negative" }

        requireUnique(referenceVersions.map(VideoReferenceRecord::id), "Reference")
        requireUnique(lookVersions.map(VideoLookRecord::id), "Look")
        requireUnique(preparedSceneVersions.map(VideoPreparedSceneRecord::id), "Prepared scene")
        requireUnique(takeVersions.map(VideoTakeRecord::id), "Take")
        requireUnique(exportRecords.map(VideoExportRecord::id), "Export")

        val references = referenceVersions.map(VideoReferenceRecord::id).toSet()
        val looks = lookVersions.map(VideoLookRecord::id).toSet()
        val takes = takeVersions.map(VideoTakeRecord::id).toSet()
        require(lookVersions.all { look -> look.referenceIds.all(references::contains) }) {
            "Every look reference must identify a persisted reference version"
        }
        require(preparedSceneVersions.all { scene ->
            (scene.sourceLookId == null || scene.sourceLookId in looks) &&
                scene.sourceReferenceIds.all(references::contains)
        }) {
            "Every prepared-scene source must identify persisted look and reference versions"
        }
        require(takeVersions.all { take -> take.lookId == null || take.lookId in looks }) {
            "Every take look must identify a persisted look version"
        }
        require(selectedReferenceIds.distinct().size == selectedReferenceIds.size && selectedReferenceIds.all(references::contains)) {
            "Selected references must be unique persisted reference versions"
        }
        require(selectedLookId == null || selectedLookId in looks) {
            "The selected look must identify a persisted look version"
        }
        require(selectedTakeIds.distinct().size == selectedTakeIds.size && selectedTakeIds.all(takes::contains)) {
            "Selected takes must be unique persisted take versions"
        }
        require(exportRecords.all { export -> export.takeIds.isNotEmpty() && export.takeIds.all(takes::contains) }) {
            "Every export must identify persisted take versions"
        }
        val artifactPaths = buildList {
            addAll(referenceVersions.map { it.artifact.relativePath })
            addAll(lookVersions.map { it.artifact.relativePath })
            addAll(preparedSceneVersions.map { it.artifact.relativePath })
            addAll(takeVersions.map { it.artifact.relativePath })
            addAll(exportRecords.map { it.artifact.relativePath })
        }
        require(artifactPaths.distinct().size == artifactPaths.size) {
            "Immutable video records must use distinct artifact paths"
        }
    }

    fun artifacts(): List<VideoArtifact> = buildList {
        addAll(referenceVersions.map(VideoReferenceRecord::artifact))
        addAll(lookVersions.map(VideoLookRecord::artifact))
        preparedSceneVersions.forEach { scene ->
            add(scene.artifact)
            addAll(scene.consumedArtifacts)
        }
        addAll(takeVersions.map(VideoTakeRecord::artifact))
        addAll(exportRecords.map(VideoExportRecord::artifact))
    }
}

@Serializable
data class VideoVersionedId(val id: String, val version: Int) {
    init {
        requireSafeId(id, "Video record")
        require(version > 0) { "Video record version must be positive" }
    }
}

@Serializable
data class VideoArtifact(val relativePath: String, val sha256: String) {
    init {
        requirePortableRelativePath(relativePath)
        require(SHA_256.matches(sha256)) { "Video artifact SHA-256 must be lowercase hexadecimal" }
    }
}

@Serializable
data class VideoReferenceRecord(
    val id: VideoVersionedId,
    val artifact: VideoArtifact,
    val createdAt: String,
) {
    init { requireTimestamp(createdAt, "Reference record") }
}

@Serializable
data class VideoLookRecord(
    val id: VideoVersionedId,
    val artifact: VideoArtifact,
    val referenceIds: List<VideoVersionedId>,
    val createdAt: String,
) {
    init {
        require(referenceIds.distinct().size == referenceIds.size) { "Look reference IDs must be unique" }
        requireTimestamp(createdAt, "Look record")
    }
}

@Serializable
data class VideoTakeRecord(
    val id: VideoVersionedId,
    val artifact: VideoArtifact,
    val lookId: VideoVersionedId?,
    val createdAt: String,
    val mediaFacts: VideoTakeMediaFactsRecord? = null,
) {
    init { requireTimestamp(createdAt, "Take record") }
}

@Serializable
data class VideoTakeMediaFactsRecord(
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

@Serializable
data class VideoExportRecord(
    val id: VideoVersionedId,
    val artifact: VideoArtifact,
    val takeIds: List<VideoVersionedId>,
    val createdAt: String,
) {
    init {
        require(takeIds.distinct().size == takeIds.size) { "Export take IDs must be unique" }
        requireTimestamp(createdAt, "Export record")
    }
}

private fun requireSafeId(value: String, label: String) {
    require(SAFE_ID.matches(value)) { "$label ID must be a safe stable identifier" }
}

private fun requirePortableRelativePath(value: String) {
    require(value.isNotBlank() && !value.startsWith('/') && !value.startsWith('\\') && !value.contains('\\')) {
        "Video artifact path must be portable and relative"
    }
    val segments = value.split('/')
    require(segments.all { segment ->
        segment.isNotBlank() && segment != "." && segment != ".." &&
            segment.none(Char::isISOControl) && segment.none { it in PORTABLE_PATH_FORBIDDEN }
    }) {
        "Video artifact path must not contain traversal or empty segments"
    }
    require(segments.none(VideoProjectControlPaths::isReservedName)) {
        "Video artifacts must not use project document, lock, staging or recovery paths"
    }
}

/** Reserved by persistence, including case aliases on case-insensitive filesystems. */
internal object VideoProjectControlPaths {
    const val DOCUMENT = "video-project.json"
    const val LOCK = ".video-project.lock"
    const val STAGING_PREFIX = ".$DOCUMENT.save-"
    const val RECOVERY_PREFIX = ".$DOCUMENT.recovery-"

    fun isReservedName(segment: String): Boolean {
        val name = segment.trimEnd(' ', '.')
        return name.equals(DOCUMENT, ignoreCase = true) || name.equals(LOCK, ignoreCase = true) ||
            name.startsWith(STAGING_PREFIX, ignoreCase = true) ||
            name.startsWith(RECOVERY_PREFIX, ignoreCase = true)
    }
}

private fun requireTimestamp(value: String, label: String) {
    require(runCatching { Instant.parse(value) }.isSuccess) { "$label timestamp must be an ISO-8601 instant" }
}

private fun <T> requireUnique(values: List<T>, label: String) {
    require(values.distinct().size == values.size) { "$label version IDs must be unique" }
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private val PORTABLE_PATH_FORBIDDEN = setOf(':', '*', '?', '\"', '<', '>', '|')
