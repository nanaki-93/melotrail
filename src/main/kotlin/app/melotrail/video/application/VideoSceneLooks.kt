package app.melotrail.video.application

import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAsset
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoBriefReference
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoVersionedId

/** Selects an immutable imported finished picture as motion input; it never creates picture bytes. */
class VideoSceneLooks {
    fun select(
        project: VideoProject,
        loadedAssets: Collection<VideoAsset>,
        referenceId: VideoVersionedId,
    ): VideoSceneLookSelectionResult {
        val record = project.referenceVersions.singleOrNull { it.id == referenceId }
            ?: return rejected(
                VideoSceneLookProblemCode.MISSING_IMPORTED_VERSION,
                "Finished scene ${referenceId.display()} is not an imported version in this video project.",
                "Reopen the project and select an existing imported finished scene, or import replacement artwork as a new version.",
            )
        val matches = loadedAssets.filter { it.id == referenceId }
        if (matches.size != 1) {
            return rejected(
                VideoSceneLookProblemCode.MISSING_IMPORTED_VERSION,
                "Finished scene ${referenceId.display()} is not available with one verified immutable descriptor and original.",
                "Reopen the imported asset library so its descriptor, original and thumbnail pins are verified before selection.",
            )
        }
        val asset = matches.single()
        if (asset.role != null && asset.role != VideoReferenceRole.COMPLETE_SCENE) {
            return rejected(
                VideoSceneLookProblemCode.NOT_A_FINISHED_SCENE,
                "Imported asset ${referenceId.display()} is assigned to ${asset.role}; that role is not a finished scene.",
                "Select a COMPLETE_SCENE or unassigned import, or import the externally finished picture as a new asset version.",
            )
        }
        if (asset.identityReview == VideoAssetIdentityReview.REJECTED) {
            return rejected(
                VideoSceneLookProblemCode.REJECTED_APPEARANCE,
                "Imported asset ${referenceId.display()} has a rejected identity/appearance review.",
                "Select a different imported version or import replacement external artwork; no restyling is performed in Melotrail.",
            )
        }
        if (asset.usageIntent == VideoAssetUsageIntent.INSPIRATION_ONLY) {
            return rejected(
                VideoSceneLookProblemCode.INSPIRATION_ONLY,
                "Imported asset ${referenceId.display()} is marked as inspiration only and cannot become generation input.",
                "Import the externally finished production picture with production-reference intent, preserving the inspiration asset.",
            )
        }
        return VideoSceneLookSelectionResult.Selected(
            VideoSceneLook(
                id = referenceId,
                sourceDescriptor = record.artifact,
                original = asset.original,
                identityReview = asset.identityReview,
                appearancePolicy = VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN,
            ),
        )
    }
}

data class VideoSceneLook(
    val id: VideoVersionedId,
    val sourceDescriptor: VideoArtifact,
    val original: VideoAssetImage,
    val identityReview: VideoAssetIdentityReview,
    val appearancePolicy: VideoSceneAppearancePolicy,
) {
    init {
        require(appearancePolicy == VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN) {
            "Finished scene appearance must remain controlled by its imported pixels"
        }
    }

    fun asBriefReference(): VideoBriefReference =
        VideoBriefReference(id, original.artifact, VideoReferenceRole.COMPLETE_SCENE)
}

enum class VideoSceneAppearancePolicy { PRESERVE_AS_DRAWN }

sealed interface VideoSceneLookSelectionResult {
    data class Selected(val look: VideoSceneLook) : VideoSceneLookSelectionResult
    data class Rejected(val problems: List<VideoSceneLookProblem>) : VideoSceneLookSelectionResult {
        init { require(problems.isNotEmpty()) { "Rejected look selection needs an actionable problem" } }
    }
}

data class VideoSceneLookProblem(
    val code: VideoSceneLookProblemCode,
    val message: String,
    val nextAction: String,
)

enum class VideoSceneLookProblemCode {
    MISSING_IMPORTED_VERSION,
    NOT_A_FINISHED_SCENE,
    INSPIRATION_ONLY,
    REJECTED_APPEARANCE,
}

private fun rejected(
    code: VideoSceneLookProblemCode,
    message: String,
    nextAction: String,
): VideoSceneLookSelectionResult =
    VideoSceneLookSelectionResult.Rejected(listOf(VideoSceneLookProblem(code, message, nextAction)))

private fun VideoVersionedId.display(): String = "$id v$version"
