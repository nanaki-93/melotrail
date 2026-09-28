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
    val takeReviewEvents: List<VideoTakeReviewEvent> = emptyList(),
    val selectedReferenceIds: List<VideoVersionedId> = emptyList(),
    val selectedLookId: VideoVersionedId? = null,
    val selectedTakeIds: List<VideoVersionedId> = emptyList(),
    val exportRecords: List<VideoExportRecord> = emptyList(),
    /** Monotonic optimistic-concurrency revision for the persisted document. */
    val revision: Long = 0L,
    /** Saved continuous-plan proposals, not job checkpoints or executable readiness. */
    val assemblyVersions: List<VideoAssemblyRecord> = emptyList(),
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
        requireUnique(assemblyVersions.map(VideoAssemblyRecord::id), "Assembly")
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
        require(assemblyVersions.all { assembly ->
            val scene = preparedSceneVersions.singleOrNull { it.id == assembly.preparedSceneId }
            scene != null && scene.artifact == assembly.preparedSceneArtifact &&
                assembly.finishedReferenceId in scene.sourceReferenceIds &&
                assembly.finishedReferenceArtifact in scene.consumedArtifacts
        }) { "Every assembly must bind an existing prepared-scene version and one of its pinned source originals" }
        require(takeVersions.all { take ->
            val provenance = requireNotNull(take.provenance)
            val scene = preparedSceneVersions.find { it.id == provenance.preparedSceneId }
            (take.lookId == null || take.lookId in looks) &&
                provenance.projectId == id && provenance.persistedLookId == take.lookId &&
                (provenance.preparedSceneId == null || scene != null &&
                    scene.sourceLookId == provenance.persistedLookId &&
                    provenance.finishedReferenceId in scene.sourceReferenceIds) &&
                (provenance.finishedReferenceId == null || provenance.finishedReferenceId in references) &&
                take.publishedMeasurement?.sha256 == take.artifact.sha256
        }) { "Take provenance must identify this project and its persisted prepared-scene sources and published artifact" }
        require(selectedReferenceIds.distinct().size == selectedReferenceIds.size && selectedReferenceIds.all(references::contains)) {
            "Selected references must be unique persisted reference versions"
        }
        require(selectedLookId == null || selectedLookId in looks) {
            "The selected look must identify a persisted look version"
        }
        requireUnique(takeReviewEvents.map(VideoTakeReviewEvent::id), "Take review event")
        require(takeReviewEvents.all { it.takeId in takes }) {
            "Every take review must identify an existing take version"
        }
        require(selectedTakeIds.distinct().size == selectedTakeIds.size && selectedTakeIds.all(takes::contains)) {
            "Selected takes must be unique persisted take versions"
        }
        require(selectedTakeIds.none { reviewStatus(it) == VideoTakeReviewStatus.REJECTED }) {
            "A rejected take must be explicitly deselected before review"
        }
        require(exportRecords.all { export -> export.takeIds.isNotEmpty() && export.takeIds.all(takes::contains) }) {
            "Every export must identify persisted take versions"
        }
        val artifactPaths = buildList {
            addAll(referenceVersions.map { it.artifact.relativePath })
            addAll(lookVersions.map { it.artifact.relativePath })
            addAll(preparedSceneVersions.map { it.artifact.relativePath })
            addAll(assemblyVersions.map { it.artifact.relativePath })
            addAll(takeVersions.map { it.artifact.relativePath })
            addAll(exportRecords.map { it.artifact.relativePath })
        }
        require(artifactPaths.distinct().size == artifactPaths.size) {
            "Immutable video records must use distinct artifact paths"
        }
    }

    fun reviewStatus(takeId: VideoVersionedId): VideoTakeReviewStatus {
        require(takeVersions.any { it.id == takeId }) { "Review status needs an existing take version" }
        return when (takeReviewEvents.lastOrNull { it.takeId == takeId }?.decision) {
            VideoTakeReviewDecision.APPROVED -> VideoTakeReviewStatus.APPROVED
            VideoTakeReviewDecision.REJECTED -> VideoTakeReviewStatus.REJECTED
            null -> VideoTakeReviewStatus.UNREVIEWED
        }
    }

    fun artifacts(): List<VideoArtifact> = buildList {
        addAll(referenceVersions.map(VideoReferenceRecord::artifact))
        addAll(lookVersions.map(VideoLookRecord::artifact))
        preparedSceneVersions.forEach { scene ->
            add(scene.artifact)
            addAll(scene.consumedArtifacts)
        }
        assemblyVersions.forEach { assembly ->
            add(assembly.artifact)
            add(assembly.preparedSceneArtifact)
            add(assembly.finishedReferenceArtifact)
        }
        addAll(takeVersions.map(VideoTakeRecord::artifact))
        addAll(exportRecords.map(VideoExportRecord::artifact))
    }
}

enum class VideoTakeReviewDecision { APPROVED, REJECTED }
enum class VideoTakeReviewStatus { UNREVIEWED, APPROVED, REJECTED }

@Serializable
data class VideoTakeReviewEvent(
    val id: String,
    val takeId: VideoVersionedId,
    val decision: VideoTakeReviewDecision,
    val reviewer: String,
    val createdAt: String,
    val note: String? = null,
) {
    init {
        requireSafeId(id, "Take review event")
        require(reviewer.isNotBlank() && reviewer.length <= 120 && reviewer.none(Char::isISOControl)) {
            "Take reviewer must be a nonblank name of at most 120 characters without control characters"
        }
        requireTimestamp(createdAt, "Take review event")
        require(note == null || note.length <= 1000 && note.none(Char::isISOControl)) {
            "Take review note must be at most 1000 characters without control characters"
        }
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
    val sourceMeasurement: VideoTakeMeasurementRecord? = null,
    val publishedMeasurement: VideoTakeMeasurementRecord? = null,
    val conversion: String? = null,
    val provenance: VideoTakeProvenanceRecord? = null,
) {
    init {
        requireTimestamp(createdAt, "Take record")
        require(sourceMeasurement != null && publishedMeasurement != null && conversion != null && provenance != null) {
            "Every take needs complete source and published measurements, conversion and provenance"
        }
        if (conversion != null) {
            require(conversion in setOf("NONE", "AUDIO_REMUX", "AUDIO_REMUX_START_NORMALIZED")) { "Unsupported take conversion" }
            require((conversion == "NONE") == (sourceMeasurement == publishedMeasurement)) {
                "Unconverted take must retain identical source and published measurements"
            }
            if (conversion != "NONE") {
                val source = requireNotNull(sourceMeasurement)
                val published = requireNotNull(publishedMeasurement)
                require(source.audioStreamCount > 0 && published.audioStreamCount == 0 &&
                    source.sha256 != published.sha256 && source.videoCodec == published.videoCodec &&
                    source.width == published.width && source.height == published.height &&
                    source.sampleAspectRatio == published.sampleAspectRatio &&
                    source.frameRate == published.frameRate && source.decodedFrameCount == published.decodedFrameCount) {
                    "Audio remux must retain measured video geometry and frames and remove audio"
                }
            }
        }
    }
}

@Serializable
data class VideoTakeRationalRecord(val numerator: Long, val denominator: Long) {
    init { require(numerator > 0 && denominator > 0) { "Video timing rational must be positive" } }
}

@Serializable
data class VideoTakeMeasurementRecord(
    val sha256: String,
    val bytes: Long,
    val videoCodec: String,
    val width: Int,
    val height: Int,
    val sampleAspectRatio: VideoTakeRationalRecord,
    val frameRate: VideoTakeRationalRecord,
    val decodedFrameCount: Long,
    val videoTimeBase: VideoTakeRationalRecord,
    val videoStartPts: Long,
    val videoDurationPts: Long,
    val videoStreamCount: Int,
    val audioStreamCount: Int,
    val otherStreamCount: Int,
) {
    init {
        require(SHA_256.matches(sha256) && bytes > 0 && videoCodec.isNotBlank() && videoCodec.length <= 80)
        require(width > 0 && height > 0 && decodedFrameCount > 0 && videoDurationPts > 0)
        require(videoStreamCount == 1 && audioStreamCount >= 0 && otherStreamCount == 0)
    }
}

/** Execution and input identities copied only from the verified durable request and project records. */
@Serializable
data class VideoTakeProvenanceRecord(
    val projectId: String,
    val requestId: String,
    val attemptId: String,
    val outputId: String,
    val backendId: String,
    val executableFingerprint: String,
    val sourceIdentity: String,
    val finishedReferenceId: VideoVersionedId?,
    val preparedSceneId: VideoVersionedId?,
    val persistedLookId: VideoVersionedId?,
    val consumedPins: List<VideoGenerationDependencyPin>,
) {
    init {
        listOf(projectId, requestId, attemptId, outputId, backendId).forEach { requireSafeId(it, "Take provenance") }
        require(SHA_256.matches(executableFingerprint) && SHA_256.matches(sourceIdentity)) {
            "Take provenance fingerprints must be lowercase SHA-256"
        }
        require(consumedPins.isNotEmpty() && consumedPins.map { it.id }.distinct().size == consumedPins.size) {
            "Take consumed pins must be non-empty and unique"
        }
        if (backendId == "controlled-local") {
            require(finishedReferenceId != null && preparedSceneId != null) {
                "Controlled take needs its finished reference and persisted prepared scene"
            }
        }
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
