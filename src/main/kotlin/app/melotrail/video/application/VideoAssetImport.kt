package app.melotrail.video.application

import app.melotrail.video.domain.VideoAsset
import app.melotrail.video.domain.VideoAssetCreationProvenance
import app.melotrail.video.domain.VideoAssetRights
import app.melotrail.video.domain.VideoAssetSource
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoImageFormat
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoReferenceRecord
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Filesystem boundary for bounded image decoding and immutable asset publication. */
interface VideoAssetFiles {
    fun inspect(source: Path): PreparedVideoImage
    fun publish(projectRoot: Path, request: PublishVideoAsset): PublishedVideoAsset
    fun load(projectRoot: Path, record: VideoReferenceRecord): VideoAsset
    /** Pixel facts and descriptor come from the same bounded, digest-verified original decode. */
    fun inspectOriginal(projectRoot: Path, record: VideoReferenceRecord): InspectedVideoAsset
    fun resolveOriginal(projectRoot: Path, record: VideoReferenceRecord): Path
}

/** Read-only facts about an immutable imported original, not its thumbnail. */
data class InspectedVideoAsset(
    val asset: VideoAsset,
    val alpha: VideoMeasuredAlpha,
    val hasVisibleContrast: Boolean,
)

/** Raw bytes are retained only across inspect/publication and are never transformed in place. */
class PreparedVideoImage internal constructor(
    internal val bytes: ByteArray,
    val sha256: String,
    val format: VideoImageFormat,
    val width: Int,
    val height: Int,
    val hasAlphaChannel: Boolean,
    val hasTransparentPixels: Boolean,
    internal val thumbnailBytes: ByteArray,
    val thumbnailSha256: String,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val thumbnailHasAlphaChannel: Boolean,
    val thumbnailHasTransparentPixels: Boolean,
    val originalFileName: String,
    val originalLocation: String,
)

data class PublishVideoAsset(
    val id: VideoVersionedId,
    val image: PreparedVideoImage,
    val role: VideoReferenceRole?,
    val source: VideoAssetSource,
    val rights: VideoAssetRights?,
    val usageIntent: VideoAssetUsageIntent?,
    val creationProvenance: VideoAssetCreationProvenance?,
    val createdAt: String,
)

data class PublishedVideoAsset(val record: VideoReferenceRecord, val asset: VideoAsset)

/** Imports one reference and commits its descriptor through V13's guarded append-only save. */
class VideoAssetImport(
    private val lifecycle: VideoProjectLifecycle,
    private val files: VideoAssetFiles,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "reference-${UUID.randomUUID()}" },
) {
    fun import(session: VideoProjectSession, request: ImportVideoAsset): VideoAssetImportResult {
        val loaded = loadCurrent(session)
        if (loaded is VideoAssetLibraryResult.Rejected) return VideoAssetImportResult.Rejected(loaded.problem)
        loaded as VideoAssetLibraryResult.Loaded

        val prepared = try {
            files.inspect(request.source)
        } catch (error: VideoAssetFileException) {
            return VideoAssetImportResult.Rejected(error.toProblem())
        }

        val duplicate = loaded.assets.firstOrNull { asset ->
            asset.original.artifact.sha256 == prepared.sha256 &&
                asset.original.encodedBytes == prepared.bytes.size.toLong()
        }
        if (duplicate != null) {
            val selected = selectIfNeeded(loaded.session, duplicate.id)
            return when (selected) {
                is VideoProjectLifecycleResult.Opened -> VideoAssetImportResult.Reused(selected.session, duplicate)
                is VideoProjectLifecycleResult.Rejected -> VideoAssetImportResult.Rejected(selected.problem.toAssetProblem())
            }
        }

        val id = try {
            nextUnusedId(loaded.session, loaded.assets)
        } catch (error: IllegalArgumentException) {
            return rejected(
                VideoAssetProblemCode.INVALID_METADATA,
                error.message ?: "The generated reference identity is invalid.",
                "Retry the import with a valid stable reference identity.",
            )
        }
        val createdAt = Instant.now(clock).toString()
        val published = try {
            files.publish(
                loaded.session.root,
                PublishVideoAsset(
                    id = id,
                    image = prepared,
                    role = request.role,
                    source = VideoAssetSource(
                        originalFileName = prepared.originalFileName,
                        originalLocation = prepared.originalLocation,
                        description = request.sourceDescription,
                    ),
                    rights = request.rights,
                    usageIntent = request.usageIntent,
                    creationProvenance = request.creationProvenance,
                    createdAt = createdAt,
                ),
            )
        } catch (error: VideoAssetFileException) {
            return VideoAssetImportResult.Rejected(error.toProblem())
        } catch (error: IllegalArgumentException) {
            return rejected(
                VideoAssetProblemCode.INVALID_METADATA,
                error.message ?: "The reference metadata is invalid.",
                "Correct the optional role, provenance, or rights details and retry.",
            )
        }

        val project = loaded.session.project
        val next = try {
            project.copy(
                referenceVersions = project.referenceVersions + published.record,
                selectedReferenceIds = project.selectedReferenceIds + published.record.id,
                revision = Math.addExact(project.revision, 1L),
            )
        } catch (error: IllegalArgumentException) {
            return rejected(
                VideoAssetProblemCode.INVALID_METADATA,
                error.message ?: "The reference could not be appended to this project.",
                "Reload the project and retry with a new reference.",
            )
        } catch (error: ArithmeticException) {
            return rejected(
                VideoAssetProblemCode.PROJECT_CHANGED,
                "The video project revision cannot advance safely.",
                "Create a new video project before importing more references.",
            )
        }
        return when (val saved = lifecycle.save(loaded.session, next)) {
            is VideoProjectLifecycleResult.Opened -> VideoAssetImportResult.Imported(saved.session, published.asset)
            is VideoProjectLifecycleResult.Rejected -> VideoAssetImportResult.Rejected(saved.problem.toAssetProblem())
        }
    }

    /** Reopens current project state and verifies every descriptor, raw source, and thumbnail pin. */
    fun open(projectRoot: Path): VideoAssetLibraryResult = when (val opened = lifecycle.open(projectRoot)) {
        is VideoProjectLifecycleResult.Opened -> load(opened.session)
        is VideoProjectLifecycleResult.Rejected -> VideoAssetLibraryResult.Rejected(opened.problem.toAssetProblem())
    }

    fun load(session: VideoProjectSession): VideoAssetLibraryResult {
        val assets = try {
            session.project.referenceVersions.map { files.load(session.root, it) }
        } catch (error: VideoAssetFileException) {
            return VideoAssetLibraryResult.Rejected(error.toProblem())
        }
        return VideoAssetLibraryResult.Loaded(session, assets)
    }

    private fun loadCurrent(session: VideoProjectSession): VideoAssetLibraryResult {
        val reopened = when (val result = lifecycle.open(session.root)) {
            is VideoProjectLifecycleResult.Opened -> result.session
            is VideoProjectLifecycleResult.Rejected -> return VideoAssetLibraryResult.Rejected(result.problem.toAssetProblem())
        }
        if (reopened.project.id != session.project.id || reopened.project.revision != session.project.revision) {
            return rejectedLibrary(
                VideoAssetProblemCode.PROJECT_CHANGED,
                "The video project changed from revision ${session.project.revision} to ${reopened.project.revision}.",
                "Reopen the project before importing this reference.",
            )
        }
        return load(reopened)
    }

    private fun selectIfNeeded(
        session: VideoProjectSession,
        id: VideoVersionedId,
    ): VideoProjectLifecycleResult {
        if (id in session.project.selectedReferenceIds) return VideoProjectLifecycleResult.Opened(session)
        val nextRevision = try {
            Math.addExact(session.project.revision, 1L)
        } catch (_: ArithmeticException) {
            return VideoProjectLifecycleResult.Rejected(
                VideoProjectProblem(
                    VideoProjectProblemCode.INVALID_REQUEST,
                    "The video project revision cannot advance safely.",
                    "Create a new video project before selecting more references.",
                ),
            )
        }
        val next = session.project.copy(
            selectedReferenceIds = session.project.selectedReferenceIds + id,
            revision = nextRevision,
        )
        return lifecycle.save(session, next)
    }

    private fun nextUnusedId(session: VideoProjectSession, assets: List<VideoAsset>): VideoVersionedId {
        val used = session.project.referenceVersions.mapTo(mutableSetOf()) { it.id }
        used.addAll(assets.map { it.id })
        repeat(32) {
            val candidate = VideoVersionedId(idFactory(), 1)
            if (candidate !in used) return candidate
        }
        throw IllegalArgumentException("Could not allocate a unique reference identity")
    }

    private fun rejected(code: VideoAssetProblemCode, message: String, nextAction: String) =
        VideoAssetImportResult.Rejected(VideoAssetProblem(code, message, nextAction))

    private fun rejectedLibrary(code: VideoAssetProblemCode, message: String, nextAction: String) =
        VideoAssetLibraryResult.Rejected(VideoAssetProblem(code, message, nextAction))
}

data class ImportVideoAsset(
    val source: Path,
    val role: VideoReferenceRole? = null,
    val sourceDescription: String? = null,
    val rights: VideoAssetRights? = null,
    val usageIntent: VideoAssetUsageIntent? = null,
    val creationProvenance: VideoAssetCreationProvenance? = null,
)

sealed interface VideoAssetImportResult {
    data class Imported(val session: VideoProjectSession, val asset: VideoAsset) : VideoAssetImportResult
    data class Reused(val session: VideoProjectSession, val asset: VideoAsset) : VideoAssetImportResult
    data class Rejected(val problem: VideoAssetProblem) : VideoAssetImportResult
}

sealed interface VideoAssetLibraryResult {
    data class Loaded(val session: VideoProjectSession, val assets: List<VideoAsset>) : VideoAssetLibraryResult
    data class Rejected(val problem: VideoAssetProblem) : VideoAssetLibraryResult
}

data class VideoAssetProblem(val code: VideoAssetProblemCode, val message: String, val nextAction: String)

enum class VideoAssetProblemCode {
    SOURCE_NOT_FOUND,
    UNSAFE_SOURCE,
    UNSUPPORTED_FORMAT,
    ENCODED_FILE_TOO_LARGE,
    IMAGE_DIMENSIONS_TOO_LARGE,
    CORRUPT_IMAGE,
    INVALID_METADATA,
    ASSET_MISSING,
    ASSET_CHANGED,
    PROJECT_CHANGED,
    PROJECT_INVALID,
    SAVE_FAILED,
    IO_FAILURE,
}

class VideoAssetFileException(
    val code: VideoAssetProblemCode,
    message: String,
    val nextAction: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private fun VideoAssetFileException.toProblem() = VideoAssetProblem(code, message ?: code.name, nextAction)

private fun VideoProjectProblem.toAssetProblem(): VideoAssetProblem {
    val assetCode = when (code) {
        VideoProjectProblemCode.CONCURRENT_WRITE -> VideoAssetProblemCode.PROJECT_CHANGED
        VideoProjectProblemCode.SAVE_FAILED -> VideoAssetProblemCode.SAVE_FAILED
        VideoProjectProblemCode.INVALID_PROJECT,
        VideoProjectProblemCode.UNSUPPORTED_PROJECT,
        VideoProjectProblemCode.IMMUTABLE_HISTORY,
        -> VideoAssetProblemCode.PROJECT_INVALID
        VideoProjectProblemCode.IO_FAILURE -> VideoAssetProblemCode.IO_FAILURE
        else -> VideoAssetProblemCode.PROJECT_INVALID
    }
    return VideoAssetProblem(assetCode, message, nextAction)
}
