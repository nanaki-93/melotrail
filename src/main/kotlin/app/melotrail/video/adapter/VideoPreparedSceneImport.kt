package app.melotrail.video.adapter

import app.melotrail.video.application.PrepareVideoAnimationAssets
import app.melotrail.video.application.ResolvedVideoAnimationAsset
import app.melotrail.video.application.VideoAnimationAssetDeficiency
import app.melotrail.video.application.VideoAnimationAssetDeficiencyCode
import app.melotrail.video.application.VideoAnimationAssets
import app.melotrail.video.application.VideoAnimationAssetsResult
import app.melotrail.video.application.VideoAssetFiles
import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoPreparedReferencePin
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Path
import java.time.Clock
import java.time.Instant

/** Resolves V14 immutable originals, validates their pixels, then appends one V18a descriptor. */
class VideoPreparedSceneImport(
    private val projects: VideoProjectStore,
    private val scenes: VideoPreparedSceneStore,
    private val images: VideoAssetFiles = VideoImageFiles(),
    private val validator: VideoAnimationAssets = VideoAnimationAssets(),
    private val clock: Clock = Clock.systemUTC(),
) {
    fun import(
        projectRoot: Path,
        expectedRevision: Long,
        request: PrepareVideoAnimationAssets,
    ): VideoPreparedSceneImportResult {
        val project = try {
            projects.open(projectRoot)
        } catch (error: Exception) {
            return rejected(
                VideoAnimationAssetDeficiencyCode.PROJECT_INVALID,
                "The video project and its immutable asset pins could not be opened: ${error.message ?: error::class.simpleName}.",
                "Restore or reopen the current video project before preparing animation assets.",
            )
        }
        if (project.revision != expectedRevision) {
            return rejected(
                VideoAnimationAssetDeficiencyCode.PROJECT_CHANGED,
                "The video project changed from revision $expectedRevision to ${project.revision}.",
                "Reopen the project and retry with its current imported asset pins.",
            )
        }

        val resolved = linkedMapOf<VideoVersionedId, ResolvedVideoAnimationAsset>()
        request.allReferenceIds().forEach { id ->
            val record = project.referenceVersions.singleOrNull { it.id == id }
                ?: return rejected(
                    VideoAnimationAssetDeficiencyCode.MISSING_ASSET,
                    "Imported asset ${id.id} version ${id.version} is not part of this video project.",
                    "Select an existing imported version or import the external picture before preparing the scene.",
                    id.id,
                )
            val inspected = try {
                images.inspectOriginal(projectRoot, record)
            } catch (error: Exception) {
                return rejected(
                    VideoAnimationAssetDeficiencyCode.PROJECT_INVALID,
                    "Imported asset ${id.id} version ${id.version} no longer matches its immutable descriptor and original pin.",
                    "Restore the exact imported asset bundle or import the changed picture as a new version.",
                    id.id,
                )
            }
            val asset = inspected.asset
            if (id == request.finishedSceneReferenceId && asset.role != null &&
                asset.role != app.melotrail.video.domain.VideoReferenceRole.COMPLETE_SCENE
            ) {
                return rejected(
                    VideoAnimationAssetDeficiencyCode.MISSING_ASSET,
                    "Finished scene ${id.id} is assigned to ${asset.role}, not the finished-scene role.",
                    "Select a COMPLETE_SCENE or unassigned imported image for the finished scene.",
                    id.id,
                )
            }
            if (id == request.finishedSceneReferenceId && asset.usageIntent == VideoAssetUsageIntent.INSPIRATION_ONLY) {
                return rejected(
                    VideoAnimationAssetDeficiencyCode.MISSING_ASSET,
                    "Finished scene ${id.id} is marked inspiration-only and cannot be prepared as generation input.",
                    "Import externally finished production artwork as a new production-reference asset; keep the inspiration unchanged.",
                    id.id,
                )
            }
            if (id == request.finishedSceneReferenceId && asset.identityReview == VideoAssetIdentityReview.REJECTED) {
                return rejected(
                    VideoAnimationAssetDeficiencyCode.MISSING_ASSET,
                    "Finished scene ${id.id} has a rejected identity/appearance review.",
                    "Select another imported version or import replacement external artwork.",
                    id.id,
                )
            }
            resolved[id] = ResolvedVideoAnimationAsset(
                pin = VideoPreparedReferencePin(record.id, record.artifact, asset.original),
                asset = asset,
                alpha = inspected.alpha,
                hasMaskContrast = inspected.hasVisibleContrast,
            )
        }

        val validation = try {
            validator.validate(request, resolved, Instant.now(clock).toString())
        } catch (error: IllegalArgumentException) {
            return rejected(
                VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY,
                error.message ?: "Prepared asset metadata is invalid.",
                "Correct the visible placement, transform, pivot, mask, depth, occlusion, anchor, or requested range and retry.",
            )
        }
        if (validation is VideoAnimationAssetsResult.Rejected) {
            return VideoPreparedSceneImportResult.Rejected(validation.deficiencies)
        }
        validation as VideoAnimationAssetsResult.Validated
        return try {
            val saved = scenes.save(projectRoot, expectedRevision, validation.scene)
            VideoPreparedSceneImportResult.Saved(saved, validation.scene)
        } catch (error: VideoProjectConcurrencyException) {
            rejected(
                VideoAnimationAssetDeficiencyCode.PROJECT_CHANGED,
                error.message ?: "The video project changed while preparing animation assets.",
                "Reopen the project and retry; no prepared-scene record was appended.",
            )
        } catch (error: Exception) {
            rejected(
                VideoAnimationAssetDeficiencyCode.PROJECT_INVALID,
                "The prepared-scene descriptor could not be appended without changing existing records: ${error.message ?: error::class.simpleName}.",
                "Correct the reported project or metadata problem and retry with a new prepared-scene version.",
            )
        }
    }

    private fun rejected(
        code: VideoAnimationAssetDeficiencyCode,
        message: String,
        nextAction: String,
        targetId: String? = null,
    ) = VideoPreparedSceneImportResult.Rejected(
        listOf(VideoAnimationAssetDeficiency(code, message, nextAction, targetId)),
    )
}

sealed interface VideoPreparedSceneImportResult {
    data class Saved(val project: VideoProject, val scene: VideoPreparedScene) : VideoPreparedSceneImportResult
    data class Rejected(val deficiencies: List<VideoAnimationAssetDeficiency>) : VideoPreparedSceneImportResult {
        init { require(deficiencies.isNotEmpty()) { "Rejected prepared-scene import needs an actionable deficiency" } }
    }
}

private fun PrepareVideoAnimationAssets.allReferenceIds(): List<VideoVersionedId> = buildList {
    add(finishedSceneReferenceId)
    addAll(subjectLayers.map { it.referenceId })
    cleanBackground?.let { add(it.referenceId) }
    addAll(scenery.map { it.asset.referenceId })
    addAll(foregroundLayers.map { it.referenceId })
    addAll(poses.map { it.asset.referenceId })
    addAll(masks.map { it.asset.referenceId })
}.distinct()
