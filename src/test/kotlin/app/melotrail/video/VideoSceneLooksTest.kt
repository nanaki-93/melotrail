package app.melotrail.video

import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoAssetLibraryResult
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.application.VideoSceneAppearancePolicy
import app.melotrail.video.application.VideoSceneLookProblemCode
import app.melotrail.video.application.VideoSceneLookSelectionResult
import app.melotrail.video.application.VideoSceneLooks
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoVersionedId
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoSceneLooksTest {
    @TempDir lateinit var root: Path

    @Test
    fun `reopen selects exact imported finished version and replacement preserves the earlier look`() {
        val harness = harness()
        val firstSource = image(root.resolve("outside/first.png"), Color(60, 70, 90))
        val firstBytes = Files.readAllBytes(firstSource)
        val first = harness.import("finished", firstSource, VideoReferenceRole.COMPLETE_SCENE)
        val firstSelection = assertIs<VideoSceneLookSelectionResult.Selected>(
            VideoSceneLooks().select(first.session.project, listOf(first.asset), first.asset.id),
        ).look

        val replacementSource = image(root.resolve("outside/replacement.png"), Color(180, 100, 70))
        val replacement = harness.import("finished-replacement", replacementSource, VideoReferenceRole.COMPLETE_SCENE)
        val reopened = assertIs<VideoAssetLibraryResult.Loaded>(harness.importer("unused").open(harness.projectRoot))
        val replacementSelection = assertIs<VideoSceneLookSelectionResult.Selected>(
            VideoSceneLooks().select(reopened.session.project, reopened.assets, replacement.asset.id),
        ).look

        assertEquals(VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN, replacementSelection.appearancePolicy)
        assertEquals(replacement.asset.original, replacementSelection.original)
        assertEquals(replacement.session.project.referenceVersions.last().artifact, replacementSelection.sourceDescriptor)
        assertNotEquals(firstSelection.id, replacementSelection.id)
        assertNotEquals(firstSelection.original.artifact.sha256, replacementSelection.original.artifact.sha256)
        assertEquals(first.asset.original, firstSelection.original)
        assertTrue(reopened.session.project.referenceVersions.map { it.id }.containsAll(listOf(first.asset.id, replacement.asset.id)))
        assertContentEquals(firstBytes, Files.readAllBytes(firstSource))
        assertContentEquals(
            firstBytes,
            Files.readAllBytes(harness.projectRoot.resolve(firstSelection.original.artifact.relativePath)),
        )
    }

    @Test
    fun `missing wrong-role inspiration and rejected versions fail with replacement actions`() {
        val harness = harness()
        val subject = harness.import(
            "subject",
            image(root.resolve("outside/subject.png"), Color(70, 130, 100)),
            VideoReferenceRole.SUBJECT,
        )
        val inspiration = harness.import(
            "inspiration",
            image(root.resolve("outside/inspiration.png"), Color(140, 90, 160)),
            VideoReferenceRole.COMPLETE_SCENE,
            VideoAssetUsageIntent.INSPIRATION_ONLY,
        )
        val selector = VideoSceneLooks()

        val missing = assertIs<VideoSceneLookSelectionResult.Rejected>(
            selector.select(inspiration.session.project, listOf(subject.asset, inspiration.asset), VideoVersionedId("missing", 1)),
        )
        val wrongRole = assertIs<VideoSceneLookSelectionResult.Rejected>(
            selector.select(inspiration.session.project, listOf(subject.asset, inspiration.asset), subject.asset.id),
        )
        val inspirationOnly = assertIs<VideoSceneLookSelectionResult.Rejected>(
            selector.select(inspiration.session.project, listOf(subject.asset, inspiration.asset), inspiration.asset.id),
        )
        val rejected = assertIs<VideoSceneLookSelectionResult.Rejected>(
            selector.select(
                inspiration.session.project,
                listOf(subject.asset, inspiration.asset.copy(identityReview = VideoAssetIdentityReview.REJECTED)),
                inspiration.asset.id,
            ),
        )

        assertEquals(VideoSceneLookProblemCode.MISSING_IMPORTED_VERSION, missing.problems.single().code)
        assertEquals(VideoSceneLookProblemCode.NOT_A_FINISHED_SCENE, wrongRole.problems.single().code)
        assertEquals(VideoSceneLookProblemCode.INSPIRATION_ONLY, inspirationOnly.problems.single().code)
        assertEquals(VideoSceneLookProblemCode.REJECTED_APPEARANCE, rejected.problems.single().code)
        assertTrue(rejected.problems.single().nextAction.contains("replacement external artwork"))
        assertTrue(inspiration.session.project.lookVersions.isEmpty())
    }

    private fun harness(): Harness {
        val projectRoot = root.resolve("project")
        val store = VideoProjectStore(listOf(root.resolve("midi-projects"), root.resolve("midi-exports")))
        val lifecycle = VideoProjectLifecycle(store, CLOCK, idFactory = { "look-project" })
        val session = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle.create(CreateVideoProject(projectRoot, "Finished looks", "look-project")),
        ).session
        return Harness(projectRoot, lifecycle, session)
    }

    private inner class Harness(
        val projectRoot: Path,
        val lifecycle: VideoProjectLifecycle,
        var session: app.melotrail.video.application.VideoProjectSession,
    ) {
        fun importer(id: String) = VideoAssetImport(lifecycle, VideoImageFiles(), CLOCK, idFactory = { id })

        fun import(
            id: String,
            source: Path,
            role: VideoReferenceRole,
            usageIntent: VideoAssetUsageIntent = VideoAssetUsageIntent.PRODUCTION_REFERENCE,
        ): VideoAssetImportResult.Imported {
            val imported = assertIs<VideoAssetImportResult.Imported>(
                importer(id).import(session, ImportVideoAsset(source, role, usageIntent = usageIntent)),
            )
            session = imported.session
            return imported
        }
    }

    private fun image(path: Path, color: Color): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(96, 54, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
    }
}
