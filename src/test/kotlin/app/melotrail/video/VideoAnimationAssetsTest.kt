package app.melotrail.video

import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneImport
import app.melotrail.video.adapter.VideoPreparedSceneImportResult
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.PrepareVideoAnimationAssets
import app.melotrail.video.application.VideoAnimationAssetDeficiencyCode
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoMaskAnimationAsset
import app.melotrail.video.application.VideoPlacedAnimationAsset
import app.melotrail.video.application.VideoPoseAnimationAsset
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.application.VideoRequestedMotion
import app.melotrail.video.application.VideoSceneryAnimationAsset
import app.melotrail.video.domain.VideoDepthRelation
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoOcclusionRelation
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPoint
import app.melotrail.video.domain.VideoRect
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoSubjectLandmark
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
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoAnimationAssetsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `finished scene alone retains exact original and exposes only image to video eligibility`() {
        val fixture = fixture()
        val source = rgbImage(root.resolve("outside/quiet-room.jpg"), 96, 54, Color(80, 90, 110), "jpg")
        val sourceBytes = Files.readAllBytes(source)
        val imported = fixture.import("quiet-room", source, VideoReferenceRole.COMPLETE_SCENE)

        val result = fixture.importer.import(
            fixture.projectRoot,
            imported.session.project.revision,
            PrepareVideoAnimationAssets(
                sceneId = VideoVersionedId("quiet-room-motion", 1),
                finishedSceneReferenceId = imported.asset.id,
            ),
        )

        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(result)
        assertEquals(listOf(imported.asset.id), saved.scene.source.references.map { it.id })
        assertEquals(imported.asset.original, saved.scene.source.references.single().original)
        assertEquals(VideoLayerKind.FINISHED_SCENE, saved.scene.layers.single().kind)
        assertEquals(VideoMotionControl.IMAGE_TO_VIDEO, saved.scene.motionCapabilities.single().control)
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
        assertContentEquals(sourceBytes, Files.readAllBytes(source))
    }

    @Test
    fun `stale revision import rejects without publishing a scene`() {
        val fixture = fixture()
        val finished = fixture.import("stale-scene", rgbImage(root.resolve("outside/stale-scene.png"), 40, 30, Color(45, 55, 65)), VideoReferenceRole.COMPLETE_SCENE)
        val expectedRevision = finished.session.project.revision
        val projectDocument = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectDocument)

        val result = fixture.importer.import(
            fixture.projectRoot,
            expectedRevision - 1L,
            PrepareVideoAnimationAssets(
                sceneId = VideoVersionedId("stale-scene-motion", 1),
                finishedSceneReferenceId = finished.asset.id,
            ),
        )

        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(result)
        val deficiency = rejected.deficiencies.single()
        assertEquals(VideoAnimationAssetDeficiencyCode.PROJECT_CHANGED, deficiency.code)
        assertTrue(deficiency.nextAction.isNotBlank())
        assertContentEquals(before, Files.readAllBytes(projectDocument))
        assertTrue(runCatching {
            fixture.scenes.load(fixture.projectRoot, VideoVersionedId("stale-scene-motion", 1))
        }.isFailure)
    }

    @Test
    fun `ready layers persist measured alpha placement pivots occlusion and coverage capabilities`() {
        val fixture = fixture()
        val finished = fixture.import("cabin", rgbImage(root.resolve("outside/cabin.png"), 100, 60, Color(40, 50, 70)), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("traveler", cutout(root.resolve("outside/traveler.png"), 20, 30, Color(180, 90, 150)), VideoReferenceRole.SUBJECT)
        val background = fixture.import("clean-cabin", rgbImage(root.resolve("outside/clean-cabin.png"), 100, 60, Color(50, 60, 80)), VideoReferenceRole.ENVIRONMENT)
        val scenery = fixture.import("long-view", rgbImage(root.resolve("outside/long-view.png"), 220, 60, Color(90, 130, 150)), VideoReferenceRole.ENVIRONMENT)
        val foreground = fixture.import("window-frame", cutout(root.resolve("outside/window-frame.png"), 100, 60, Color(100, 70, 60)), VideoReferenceRole.ENVIRONMENT)
        val pose = fixture.import("traveler-blink", cutout(root.resolve("outside/traveler-blink.png"), 20, 30, Color(170, 80, 140)), VideoReferenceRole.SUBJECT)
        val mask = fixture.import("window-mask", cutout(root.resolve("outside/window-mask.png"), 100, 60, Color.WHITE), VideoReferenceRole.ENVIRONMENT)
        val sceneSpace = "scene"
        val subjectBounds = VideoRect(sceneSpace, 10.0, 10.0, 20.0, 30.0)
        val subjectPivot = VideoPlacedPoint(sceneSpace, VideoPoint(20.0, 25.0))
        val request = PrepareVideoAnimationAssets(
            sceneId = VideoVersionedId("layered-cabin", 1),
            finishedSceneReferenceId = finished.asset.id,
            subjectLayers = listOf(VideoPlacedAnimationAsset("traveler", subject.asset.id, subjectBounds, subjectPivot)),
            cleanBackground = VideoPlacedAnimationAsset(
                "clean-background",
                background.asset.id,
                VideoRect(sceneSpace, 0.0, 0.0, 100.0, 60.0),
            ),
            scenery = listOf(
                VideoSceneryAnimationAsset(
                    VideoPlacedAnimationAsset("passing-view", scenery.asset.id, VideoRect(sceneSpace, 0.0, 0.0, 220.0, 60.0)),
                    VideoRect(sceneSpace, 0.0, 0.0, 220.0, 60.0),
                    "passing-view-coverage",
                ),
            ),
            foregroundLayers = listOf(
                VideoPlacedAnimationAsset("window-frame", foreground.asset.id, VideoRect(sceneSpace, 0.0, 0.0, 100.0, 60.0)),
            ),
            poses = listOf(
                VideoPoseAnimationAsset(
                    VideoPlacedAnimationAsset("blink", pose.asset.id, subjectBounds, subjectPivot),
                    "traveler",
                ),
            ),
            masks = listOf(
                VideoMaskAnimationAsset(
                    VideoPlacedAnimationAsset("foreground-mask", mask.asset.id, VideoRect(sceneSpace, 0.0, 0.0, 100.0, 60.0)),
                    listOf("window-frame", "passing-view"),
                    VideoMaskPurpose.GENERIC,
                ),
            ),
            subjectLandmarks = listOf(
                VideoSubjectLandmark("forehead", "traveler", VideoPlacedPoint(sceneSpace, VideoPoint(20.0, 15.0))),
            ),
            effectAnchors = listOf(VideoEffectAnchor("steam-source", "traveler", landmarkId = "forehead")),
            depthRelations = listOf(VideoDepthRelation("window-frame", "passing-view")),
            occlusionRelations = listOf(VideoOcclusionRelation("window-frame", "passing-view", "foreground-mask")),
            requestedMotions = listOf(
                VideoRequestedMotion(VideoMotionControl.ROTATE, "traveler", -5.0, 5.0),
                VideoRequestedMotion(VideoMotionControl.POSE_BLEND, "blink", 0.0, 1.0),
                VideoRequestedMotion(VideoMotionControl.TRANSLATE_X, "passing-view-coverage", -100.0, 0.0),
                VideoRequestedMotion(VideoMotionControl.EFFECT_RATE, "steam-source", 0.0, 3.0, 1.0),
            ),
        )

        val result = fixture.importer.import(fixture.projectRoot, mask.session.project.revision, request)

        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(result)
        assertEquals(7, saved.scene.source.references.size)
        assertTrue(saved.scene.layers.single { it.id == "traveler" }.alpha!!.isUsableCutout)
        assertEquals(subjectPivot, saved.scene.layers.single { it.id == "traveler" }.pivot)
        assertEquals(VideoMaskPurpose.GENERIC, saved.scene.masks.single().purpose)
        assertEquals(listOf("forehead"), saved.scene.subjectLandmarks.map { it.id })
        assertTrue(saved.scene.motionCapabilities.any { it.control == VideoMotionControl.POSE_BLEND && it.targetId == "blink" })
        assertTrue(saved.scene.motionCapabilities.any { it.control == VideoMotionControl.TRANSLATE_X && it.minimum == -120.0 })
        assertTrue(saved.scene.motionCapabilities.any { it.control == VideoMotionControl.EFFECT_RATE && it.targetId == "steam-source" })
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
    }

    @Test
    fun `misaligned poses duplicate IDs and invalid relationships reject without project mutation`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("actor", cutout(root.resolve("outside/actor.png"), 20, 20, Color.PINK), VideoReferenceRole.SUBJECT)
        val pose = fixture.import("pose", cutout(root.resolve("outside/pose.png"), 20, 20, Color.RED), VideoReferenceRole.SUBJECT)
        val revision = pose.session.project.revision
        val actor = VideoPlacedAnimationAsset("actor", subject.asset.id, VideoRect("scene", 10.0, 10.0, 20.0, 20.0), VideoPlacedPoint("scene", VideoPoint(20.0, 20.0)))
        val projectFile = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectFile)
        val malformed = listOf(
            PrepareVideoAnimationAssets(VideoVersionedId("misaligned", 1), finished.asset.id, subjectLayers = listOf(actor), poses = listOf(VideoPoseAnimationAsset(VideoPlacedAnimationAsset("blink", pose.asset.id, VideoRect("scene", 11.0, 10.0, 20.0, 20.0), actor.pivot), "actor"))) to "not aligned",
            PrepareVideoAnimationAssets(VideoVersionedId("duplicate", 1), finished.asset.id, subjectLayers = listOf(actor, actor.copy(referenceId = pose.asset.id))) to "IDs must be unique",
            PrepareVideoAnimationAssets(VideoVersionedId("bad-relations", 1), finished.asset.id, depthRelations = listOf(VideoDepthRelation("absent-a", "absent-b"))) to "depth relation",
        )
        malformed.forEach { (request, expectedMessage) ->
            val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(fixture.projectRoot, revision, request))
            assertTrue(rejected.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY && it.message.contains(expectedMessage, ignoreCase = true) })
            assertTrue(rejected.deficiencies.all { it.nextAction.isNotBlank() })
            assertContentEquals(before, Files.readAllBytes(projectFile))
        }
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `opaque rgba and fully transparent subject files do not count as separated layers`() {
        val fixture = fixture()
        val finished = fixture.import("scene", rgbImage(root.resolve("outside/scene.png"), 80, 50, Color.DARK_GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val opaque = fixture.import("opaque-subject", argbImage(root.resolve("outside/opaque.png"), 20, 20, 0xff884466.toInt()), VideoReferenceRole.SUBJECT)
        val empty = fixture.import("empty-subject", argbImage(root.resolve("outside/empty.png"), 20, 20, 0x00000000), VideoReferenceRole.SUBJECT)
        val revision = empty.session.project.revision

        listOf(opaque.asset.id to "opaque", empty.asset.id to "empty").forEach { (assetId, id) ->
            val result = fixture.importer.import(
                fixture.projectRoot,
                revision,
                PrepareVideoAnimationAssets(
                    VideoVersionedId("$id-scene", 1),
                    finished.asset.id,
                    subjectLayers = listOf(
                        VideoPlacedAnimationAsset("subject", assetId, VideoRect("scene", 10.0, 10.0, 20.0, 20.0)),
                    ),
                ),
            )
            val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(result)
            assertTrue(rejected.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.UNUSABLE_ALPHA })
        }
        assertEquals(revision, fixture.store.open(fixture.projectRoot).revision)
    }

    @Test
    fun `painted checkerboard cutouts and unusable masks reject through import without publishing`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val checker = fixture.import("checker", checkerboardCutout(root.resolve("outside/checker.png"), 20, 20), VideoReferenceRole.SUBJECT)
        val flatMask = fixture.import("flat-mask", rgbImage(root.resolve("outside/flat-mask.png"), 80, 50, Color.WHITE), VideoReferenceRole.ENVIRONMENT)
        val revision = flatMask.session.project.revision
        val projectFile = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectFile)
        val checkerResult = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, revision,
            PrepareVideoAnimationAssets(VideoVersionedId("painted-checker", 1), finished.asset.id,
                subjectLayers = listOf(VideoPlacedAnimationAsset("subject", checker.asset.id, VideoRect("scene", 0.0, 0.0, 20.0, 20.0)))),
        ))
        assertTrue(checkerResult.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.UNUSABLE_ALPHA })
        val maskResult = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, revision,
            PrepareVideoAnimationAssets(VideoVersionedId("flat-mask-scene", 1), finished.asset.id,
                masks = listOf(VideoMaskAnimationAsset(VideoPlacedAnimationAsset("mask", flatMask.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0)), listOf("finished-scene")))),
        ))
        assertTrue(maskResult.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.UNUSABLE_MASK })
        listOf(checkerResult, maskResult).forEach { rejected ->
            assertTrue(rejected.deficiencies.all { it.nextAction.isNotBlank() })
            assertContentEquals(before, Files.readAllBytes(projectFile))
        }
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `generic mask supplies neither clean plate nor semantic effect anchor`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("subject", cutout(root.resolve("outside/subject.png"), 20, 20, Color.PINK), VideoReferenceRole.SUBJECT)
        val mask = fixture.import("generic-mask", cutout(root.resolve("outside/generic-mask.png"), 80, 50, Color.WHITE), VideoReferenceRole.ENVIRONMENT)
        val request = PrepareVideoAnimationAssets(
            VideoVersionedId("missing-controls", 1),
            finished.asset.id,
            subjectLayers = listOf(
                VideoPlacedAnimationAsset("subject", subject.asset.id, VideoRect("scene", 10.0, 10.0, 20.0, 20.0)),
            ),
            masks = listOf(
                VideoMaskAnimationAsset(
                    VideoPlacedAnimationAsset("generic-mask", mask.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0)),
                    listOf("subject", "finished-scene"),
                ),
            ),
            requestedMotions = listOf(
                VideoRequestedMotion(VideoMotionControl.TRANSLATE_X, "subject", -2.0, 2.0),
                VideoRequestedMotion(VideoMotionControl.POSE_BLEND, "blink", 0.0, 1.0),
                VideoRequestedMotion(VideoMotionControl.EFFECT_RATE, "head", 0.0, 2.0),
            ),
        )

        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(
            fixture.importer.import(fixture.projectRoot, mask.session.project.revision, request),
        )
        assertTrue(rejected.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.MISSING_CLEAN_BACKGROUND })
        assertTrue(rejected.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.MISSING_POSE })
        assertTrue(rejected.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.MISSING_EFFECT_ANCHOR })
        assertTrue(rejected.deficiencies.single { it.code == VideoAnimationAssetDeficiencyCode.MISSING_EFFECT_ANCHOR }
            .nextAction.contains("generic foreground or occlusion mask"))
    }

    @Test
    fun `requested scenery travel beyond supplied coverage fails with replacement artwork action`() {
        val fixture = fixture()
        val finished = fixture.import("still", rgbImage(root.resolve("outside/still.png"), 100, 60, Color.BLACK), VideoReferenceRole.COMPLETE_SCENE)
        val scenery = fixture.import("short-scenery", rgbImage(root.resolve("outside/short.png"), 100, 60, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val request = PrepareVideoAnimationAssets(
            VideoVersionedId("short-travel", 1),
            finished.asset.id,
            scenery = listOf(
                VideoSceneryAnimationAsset(
                    VideoPlacedAnimationAsset("view", scenery.asset.id, VideoRect("scene", 0.0, 0.0, 100.0, 60.0)),
                    VideoRect("scene", 0.0, 0.0, 100.0, 60.0),
                    "view-coverage",
                ),
            ),
            requestedMotions = listOf(
                VideoRequestedMotion(VideoMotionControl.TRANSLATE_X, "view-coverage", -10.0, 0.0),
            ),
        )

        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(
            fixture.importer.import(fixture.projectRoot, scenery.session.project.revision, request),
        )
        val gap = rejected.deficiencies.single()
        assertEquals(VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE, gap.code)
        assertTrue(gap.nextAction.contains("external scenery"))
    }

    @Test
    fun `pivot outside supplied subject geometry fails without appending a descriptor`() {
        val fixture = fixture()
        val finished = fixture.import("base", rgbImage(root.resolve("outside/base.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("actor", cutout(root.resolve("outside/actor.png"), 20, 20, Color.GREEN), VideoReferenceRole.SUBJECT)
        val background = fixture.import("plate", rgbImage(root.resolve("outside/plate.png"), 80, 50, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val revision = background.session.project.revision
        val request = PrepareVideoAnimationAssets(
            VideoVersionedId("bad-pivot", 1),
            finished.asset.id,
            subjectLayers = listOf(
                VideoPlacedAnimationAsset(
                    "actor",
                    subject.asset.id,
                    VideoRect("scene", 10.0, 10.0, 20.0, 20.0),
                    VideoPlacedPoint("scene", VideoPoint(70.0, 40.0)),
                ),
            ),
            cleanBackground = VideoPlacedAnimationAsset("plate", background.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0)),
        )

        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(
            fixture.importer.import(fixture.projectRoot, revision, request),
        )
        assertEquals(VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY, rejected.deficiencies.single().code)
        assertTrue(rejected.deficiencies.single().message.contains("pivot must lie inside its placed bounds"))
        assertEquals(revision, fixture.store.open(fixture.projectRoot).revision)
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `undersized opaque plates reject through import and indexed alpha is measured on reopen`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val small = fixture.import("small-plate", rgbImage(root.resolve("outside/small.png"), 40, 25, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val indexedPath = indexedAlphaImage(root.resolve("outside/indexed.png"))
        val indexed = fixture.import("indexed-subject", indexedPath, VideoReferenceRole.SUBJECT)
        val revision = indexed.session.project.revision
        val projectFile = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectFile)
        val base = PrepareVideoAnimationAssets(VideoVersionedId("undersized", 1), finished.asset.id)
        val bg = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, revision, base.copy(cleanBackground = VideoPlacedAnimationAsset("plate", small.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0))),
        ))
        val scenery = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, revision, base.copy(scenery = listOf(VideoSceneryAnimationAsset(VideoPlacedAnimationAsset("wide", small.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0)), VideoRect("scene", 0.0, 0.0, 80.0, 50.0)))),
        ))
        assertTrue(bg.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.INCOMPLETE_CLEAN_BACKGROUND })
        assertTrue(scenery.deficiencies.any { it.code == VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE })
        listOf(bg, scenery).forEach { rejected ->
            assertTrue(rejected.deficiencies.all { it.nextAction.isNotBlank() })
            assertContentEquals(before, Files.readAllBytes(projectFile))
        }
        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.importer.import(
            fixture.projectRoot, revision,
            PrepareVideoAnimationAssets(VideoVersionedId("indexed-alpha", 1), finished.asset.id,
                subjectLayers = listOf(VideoPlacedAnimationAsset("indexed-subject", indexed.asset.id, VideoRect("scene", 0.0, 0.0, 20.0, 20.0)))),
        ))
        assertTrue(saved.scene.layers.single { it.id == "indexed-subject" }.alpha!!.isUsableCutout)
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
    }

    @Test
    fun `translucent finished artwork keeps its decoded alpha facts and original bytes`() {
        val fixture = fixture()
        val source = argbImage(root.resolve("outside/translucent-scene.png"), 80, 50, 0x80884466.toInt())
        val before = Files.readAllBytes(source)
        val imported = fixture.import("translucent-scene", source, VideoReferenceRole.COMPLETE_SCENE)
        assertTrue(imported.asset.original.hasTransparentPixels)

        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.importer.import(
            fixture.projectRoot,
            imported.session.project.revision,
            PrepareVideoAnimationAssets(VideoVersionedId("translucent-motion", 1), imported.asset.id),
        ))

        val alpha = saved.scene.layers.single().alpha!!
        assertEquals(4_000L, alpha.translucentPixels)
        assertEquals(0L, alpha.transparentPixels)
        assertEquals(0L, alpha.opaquePixels)
        assertEquals(VideoMotionControl.IMAGE_TO_VIDEO, saved.scene.motionCapabilities.single().control)
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
        assertContentEquals(before, Files.readAllBytes(source))
    }

    @Test
    fun `transparent scenery overlay imports only with measured opaque backing`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val far = fixture.import("far", rgbImage(root.resolve("outside/far.png"), 180, 50, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val near = fixture.import("near", cutout(root.resolve("outside/near.png"), 180, 50, Color.RED), VideoReferenceRole.ENVIRONMENT)
        val bounds = VideoRect("scene", 0.0, 0.0, 180.0, 50.0)
        fun scenery(id: String, asset: VideoAssetImportResult.Imported) = VideoSceneryAnimationAsset(
            VideoPlacedAnimationAsset(id, asset.asset.id, bounds), bounds, "$id-coverage",
        )
        val request = PrepareVideoAnimationAssets(
            VideoVersionedId("transparent-depth", 1), finished.asset.id,
            scenery = listOf(scenery("far", far), scenery("near", near)),
            depthRelations = listOf(VideoDepthRelation("near", "far")),
        )
        val before = Files.readAllBytes(fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE))
        assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, near.session.project.revision, request.copy(scenery = listOf(scenery("near", near)), depthRelations = emptyList()),
        ))
        assertContentEquals(before, Files.readAllBytes(fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.importer.import(
            fixture.projectRoot, near.session.project.revision, request,
        ))
        assertTrue(saved.scene.layers.single { it.id == "near" }.alpha!!.isUsableCutout)
        assertTrue(!saved.scene.layers.single { it.id == "far" }.alpha!!.hasNonOpaquePixels)
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
        assertTrue(saved.scene.motionCapabilities.any { it.targetId == "near-coverage" && it.control == VideoMotionControl.TRANSLATE_X })
    }

    @Test
    fun `clean backgrounds and scenery reject translucent pixels and transparent holes without writes`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 80, 50, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val translucent = fixture.import("translucent", argbImage(root.resolve("outside/translucent.png"), 80, 50, 0x80884466.toInt()), VideoReferenceRole.ENVIRONMENT)
        val holePath = rgbImage(root.resolve("outside/hole.png"), 80, 50, Color.BLUE)
        val holeImage = BufferedImage(80, 50, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 50) for (x in 0 until 80) holeImage.setRGB(x, y, 0xff4488aa.toInt())
        holeImage.setRGB(40, 25, 0)
        assertTrue(ImageIO.write(holeImage, "png", holePath.toFile()))
        val hole = fixture.import("hole", holePath, VideoReferenceRole.ENVIRONMENT)
        val subtle = fixture.import("subtle", sixteenBitTranslucentImage(root.resolve("outside/subtle.png"), 80, 50), VideoReferenceRole.ENVIRONMENT)
        val projectDocument = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectDocument)
        val revision = subtle.session.project.revision

        listOf(translucent, hole, subtle).forEach { imported ->
            val placed = VideoPlacedAnimationAsset("plate", imported.asset.id, VideoRect("scene", 0.0, 0.0, 80.0, 50.0))
            val base = PrepareVideoAnimationAssets(VideoVersionedId("invalid-coverage", 1), finished.asset.id)
            val backgroundResult = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
                fixture.projectRoot, revision, base.copy(cleanBackground = placed),
            ))
            assertEquals(VideoAnimationAssetDeficiencyCode.INCOMPLETE_CLEAN_BACKGROUND, backgroundResult.deficiencies.single().code)
            val sceneryResult = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
                fixture.projectRoot, revision, base.copy(scenery = listOf(VideoSceneryAnimationAsset(placed, placed.bounds))),
            ))
            assertEquals(VideoAnimationAssetDeficiencyCode.INSUFFICIENT_SCENERY_COVERAGE, sceneryResult.deficiencies.single().code)
            assertTrue(sceneryResult.deficiencies.single().nextAction.contains("fully opaque"))
            assertContentEquals(before, Files.readAllBytes(projectDocument))
        }
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `initial rotations cannot claim aligned poses or unrotated motion and coverage ranges`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 100, 100, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("actor", cutout(root.resolve("outside/actor.png"), 20, 20, Color.PINK), VideoReferenceRole.SUBJECT)
        val plate = fixture.import("plate", rgbImage(root.resolve("outside/plate.png"), 100, 100, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val actor = VideoPlacedAnimationAsset(
            "actor", subject.asset.id, VideoRect("scene", 30.0, 30.0, 20.0, 20.0),
            VideoPlacedPoint("scene", VideoPoint(40.0, 40.0)),
        )
        val background = VideoPlacedAnimationAsset(
            "plate", plate.asset.id, VideoRect("scene", 0.0, 0.0, 100.0, 100.0),
            VideoPlacedPoint("scene", VideoPoint(50.0, 50.0)),
        )
        val base = PrepareVideoAnimationAssets(
            VideoVersionedId("rotated-input", 1), finished.asset.id,
            subjectLayers = listOf(actor), cleanBackground = background,
        )
        val requests = listOf(
            base.copy(subjectLayers = listOf(actor.copy(rotationDegrees = 15.0))),
            base.copy(poses = listOf(VideoPoseAnimationAsset(actor.copy(id = "pose", rotationDegrees = 15.0), "actor"))),
            base.copy(cleanBackground = background.copy(rotationDegrees = 90.0)),
            base.copy(scenery = listOf(VideoSceneryAnimationAsset(background.copy(id = "scenery", rotationDegrees = 90.0), background.bounds))),
            base.copy(foregroundLayers = listOf(actor.copy(id = "foreground", rotationDegrees = 15.0))),
            base.copy(masks = listOf(VideoMaskAnimationAsset(actor.copy(id = "mask", rotationDegrees = 15.0), listOf("actor")))),
        )
        val projectDocument = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectDocument)
        requests.forEach { request ->
            val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
                fixture.projectRoot, plate.session.project.revision, request,
            ))
            assertTrue(rejected.deficiencies.any {
                it.code == VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY && it.nextAction.contains("zero initial rotation")
            })
            assertContentEquals(before, Files.readAllBytes(projectDocument))
        }
    }

    @Test
    fun `subjects outside the viewport fail before deriving ranges with an invalid zero default`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 100, 100, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("actor", cutout(root.resolve("outside/actor.png"), 20, 20, Color.PINK), VideoReferenceRole.SUBJECT)
        val plate = fixture.import("plate", rgbImage(root.resolve("outside/plate.png"), 100, 100, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot,
            plate.session.project.revision,
            PrepareVideoAnimationAssets(
                VideoVersionedId("offscreen-input", 1), finished.asset.id,
                subjectLayers = listOf(VideoPlacedAnimationAsset("actor", subject.asset.id, VideoRect("scene", 95.0, 10.0, 20.0, 20.0))),
                cleanBackground = VideoPlacedAnimationAsset("plate", plate.asset.id, VideoRect("scene", 0.0, 0.0, 100.0, 100.0)),
            ),
        ))
        assertEquals(VideoAnimationAssetDeficiencyCode.MALFORMED_GEOMETRY, rejected.deficiencies.single().code)
        assertTrue(rejected.deficiencies.single().message.contains("outside the finished scene viewport"))
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `rotation ranges contain no between-degree excursions outside the viewport`() {
        val fixture = fixture()
        val finished = fixture.import("room", rgbImage(root.resolve("outside/room.png"), 100, 100, Color.GRAY), VideoReferenceRole.COMPLETE_SCENE)
        val subject = fixture.import("actor", cutout(root.resolve("outside/actor.png"), 80, 60, Color.PINK), VideoReferenceRole.SUBJECT)
        val plate = fixture.import("plate", rgbImage(root.resolve("outside/plate.png"), 100, 100, Color.BLUE), VideoReferenceRole.ENVIRONMENT)
        // The corner radius is just over 50. It fits at every integer degree but
        // exceeds the viewport near 36.87 degrees, where a coordinate is maximal.
        val request = PrepareVideoAnimationAssets(
            VideoVersionedId("bounded-rotation", 1), finished.asset.id,
            subjectLayers = listOf(VideoPlacedAnimationAsset(
                "actor", subject.asset.id, VideoRect("scene", 9.9999875, 20.0, 80.000025, 60.0),
                VideoPlacedPoint("scene", VideoPoint(50.0, 50.0)),
            )),
            cleanBackground = VideoPlacedAnimationAsset("plate", plate.asset.id, VideoRect("scene", 0.0, 0.0, 100.0, 100.0)),
            requestedMotions = listOf(VideoRequestedMotion(VideoMotionControl.ROTATE, "actor", -37.0, 37.0)),
        )
        val rejected = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.importer.import(
            fixture.projectRoot, plate.session.project.revision, request,
        ))
        assertEquals(VideoAnimationAssetDeficiencyCode.UNSUPPORTED_MOTION, rejected.deficiencies.single().code)
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())

        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.importer.import(
            fixture.projectRoot, plate.session.project.revision, request.copy(requestedMotions = emptyList()),
        ))
        val rotation = saved.scene.motionCapabilities.single { it.control == VideoMotionControl.ROTATE }
        assertTrue(rotation.maximum in 1.0..36.0)
        assertEquals(-rotation.maximum, rotation.minimum)
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
    }

    private fun fixture(): Fixture {
        val projectRoot = root.resolve("video-project")
        val store = VideoProjectStore(listOf(root.resolve("midi-projects"), root.resolve("midi-exports")))
        val lifecycle = VideoProjectLifecycle(store, CLOCK, idFactory = { "video-project" })
        val session = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle.create(CreateVideoProject(projectRoot, "Animation assets", "video-project")),
        ).session
        val scenes = VideoPreparedSceneStore(store, VideoImageFiles())
        return Fixture(projectRoot, store, lifecycle, scenes, session)
    }

    private inner class Fixture(
        val projectRoot: Path,
        val store: VideoProjectStore,
        val lifecycle: VideoProjectLifecycle,
        val scenes: VideoPreparedSceneStore,
        initialSession: VideoProjectSession,
    ) {
        var session = initialSession
        val importer = VideoPreparedSceneImport(store, scenes, VideoImageFiles(), clock = CLOCK)

        fun import(id: String, source: Path, role: VideoReferenceRole): VideoAssetImportResult.Imported {
            val result = VideoAssetImport(lifecycle, VideoImageFiles(), CLOCK, idFactory = { id })
                .import(session, ImportVideoAsset(source, role))
            val imported = assertIs<VideoAssetImportResult.Imported>(result)
            session = imported.session
            return imported
        }
    }

    private fun rgbImage(path: Path, width: Int, height: Int, color: Color, format: String = "png"): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        assertTrue(ImageIO.write(image, format, path.toFile()))
        return path
    }

    private fun cutout(path: Path, width: Int, height: Int, color: Color): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.composite = java.awt.AlphaComposite.Clear
            graphics.fillRect(0, 0, width, height)
            graphics.composite = java.awt.AlphaComposite.Src
            graphics.color = color
            graphics.fillRect(1, 1, width - 2, height - 2)
        } finally {
            graphics.dispose()
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun checkerboardCutout(path: Path, width: Int, height: Int): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) {
            val channel = if ((x / 4 + y / 4) % 2 == 0) 0xff else 0x00
            image.setRGB(x, y, (0xff shl 24) or (channel shl 16) or (channel shl 8) or channel)
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun indexedAlphaImage(path: Path): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val model = java.awt.image.IndexColorModel(
            8, 2, byteArrayOf(0, 0xff.toByte()), byteArrayOf(0, 0x22), byteArrayOf(0, 0x66), byteArrayOf(0, 0xff.toByte()),
        )
        val raster = model.createCompatibleWritableRaster(20, 20)
        for (y in 0 until 20) for (x in 0 until 20) raster.setSample(x, y, 0, if (x in 4..15 && y in 4..15) 1 else 0)
        assertTrue(ImageIO.write(BufferedImage(model, raster, false, null), "png", path.toFile()))
        return path
    }

    private fun argbImage(path: Path, width: Int, height: Int, argb: Int): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, argb)
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun sixteenBitTranslucentImage(path: Path, width: Int, height: Int): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val colors = java.awt.image.ComponentColorModel(
            java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY),
            intArrayOf(16, 16), true, false, java.awt.Transparency.TRANSLUCENT,
            java.awt.image.DataBuffer.TYPE_USHORT,
        )
        val pixels = java.awt.image.Raster.createInterleavedRaster(java.awt.image.DataBuffer.TYPE_USHORT, width, height, 2, null)
        for (y in 0 until height) for (x in 0 until width) {
            pixels.setSample(x, y, 0, 32_768)
            pixels.setSample(x, y, 1, 65_534)
        }
        val image = BufferedImage(colors, pixels, false, null)
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        assertEquals(255, ImageIO.read(path.toFile()).getRGB(0, 0) ushr 24)
        return path
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-16T01:00:00Z"), ZoneOffset.UTC)
    }
}
