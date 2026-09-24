package app.melotrail.video

import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneImport
import app.melotrail.video.adapter.VideoPreparedSceneImportResult
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMotionRenderer
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.PrepareVideoAnimationAssets
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoAssetLibraryResult
import app.melotrail.video.application.VideoPlacedAnimationAsset
import app.melotrail.video.application.VideoPoseAnimationAsset
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.application.VideoPromptCompiler
import app.melotrail.video.application.VideoPromptIssueCode
import app.melotrail.video.application.VideoSceneryAnimationAsset
import app.melotrail.video.application.VideoSceneLookSelectionResult
import app.melotrail.video.application.VideoSceneLooks
import app.melotrail.video.application.VideoSceneComponentKind
import app.melotrail.video.application.VideoSceneComponentReview
import app.melotrail.video.application.VideoSceneMotionControlRequest
import app.melotrail.video.application.VideoSceneMotionIntent
import app.melotrail.video.application.VideoSceneMotionMode
import app.melotrail.video.application.VideoSceneMotionRequest
import app.melotrail.video.application.VideoScenePreparation
import app.melotrail.video.application.VideoScenePreparationProblemCode
import app.melotrail.video.application.VideoScenePreparationResult
import app.melotrail.video.application.VideoScenePreparationStatus
import app.melotrail.video.application.VideoClipGeneration
import app.melotrail.video.application.VideoClipGenerationRequest
import app.melotrail.video.application.VideoClipGenerationResult
import app.melotrail.video.application.VideoJobCoordinator
import app.melotrail.video.application.VideoJobPersistence
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoDepthRelation
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoMotionCapability
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoOcclusionRelation
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPoint
import app.melotrail.video.domain.VideoPreparedLayer
import app.melotrail.video.domain.VideoPreparedMask
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoSubjectLandmark
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoDependencyPin
import app.melotrail.video.domain.VideoGuidanceKind
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoJobLedger
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoRect
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoScenePreparationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `reopened ready assets compile exact controlled motion and scoped fingerprints`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val reopenedScene = fixture.scenes.load(fixture.projectRoot, prepared.scene.id)
        val library = assertIs<VideoAssetLibraryResult.Loaded>(fixture.assetImporter("unused").open(fixture.projectRoot))
        val look = assertIs<VideoSceneLookSelectionResult.Selected>(
            VideoSceneLooks().select(library.session.project, library.assets, prepared.finishedId),
        ).look
        val camera = reopenedScene.motionCapabilities.single { it.control == VideoMotionControl.IMAGE_TO_VIDEO }
        val blink = reopenedScene.motionCapabilities.single { it.control == VideoMotionControl.POSE_BLEND }
        val travel = reopenedScene.motionCapabilities.single {
            it.control == VideoMotionControl.TRANSLATE_X && it.targetId == "travel-coverage"
        }
        val prompt = "  Blink once while the view moves left.\r\nKeep every drawn detail unchanged.  "
        val request = VideoSceneMotionRequest(
            motionPrompt = prompt,
            controls = listOf(
                control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT, camera.id, 0.0, 1.0, 1.0),
                control("blink", VideoSceneMotionIntent.BLINK, blink.id, 0.0, 1.0, 0.0),
                control("travel", VideoSceneMotionIntent.SCENERY_TRAVEL, travel.id, -100.0, 0.0, 0.0),
            ),
        )
        val service = VideoScenePreparation()

        val result = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, reopenedScene, request, capabilities(), guidelines()),
        ).input

        assertEquals(prompt, result.primaryMotionPrompt)
        assertTrue(result.backendMotionPrompt.startsWith(prompt))
        assertTrue(result.backendMotionPrompt.contains("without redesigning"))
        assertEquals(VideoSceneMotionMode.CONTROLLED_REGIONAL_MOTION, result.mode)
        assertEquals(VideoScenePreparationStatus.REVIEW_REQUIRED, result.status)
        assertTrue(result.canDispatch)
        assertEquals(listOf("ambient", "blink", "travel"), result.controls.map { it.id })
        assertEquals(prepared.finishedId, result.look.id)
        assertEquals(reopenedScene, result.scene)
        assertTrue(result.componentReviews.any { it.componentId == "blink" && it.status == VideoComponentReviewStatus.UNREVIEWED })

        val reopenedAgain = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, fixture.scenes.load(fixture.projectRoot, prepared.scene.id), request, capabilities(), guidelines()),
        ).input
        assertEquals(result.requestFingerprint, reopenedAgain.requestFingerprint)
        assertEquals(result.controls.map { it.fingerprint }, reopenedAgain.controls.map { it.fingerprint })

        val changedRequest = request.copy(controls = request.controls.map {
            if (it.id == "travel") it.copy(minimum = -90.0) else it
        })
        val changed = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, reopenedScene, changedRequest, capabilities(), guidelines()),
        ).input
        val completed = listOf(VideoVersionedId("completed-take", 1))
        val invalidation = service.invalidatePending(result, changed, result.controls.map { it.id }.toSet(), completed)

        assertEquals(listOf("travel"), invalidation.invalidatedPendingMotionInputIds)
        assertEquals(listOf("ambient", "blink"), invalidation.unaffectedPendingMotionInputIds)
        assertEquals(completed, invalidation.retainedCompletedResultIds)
        assertNotEquals(result.requestFingerprint, changed.requestFingerprint)

        val changedGeometryScene = reopenedScene.copy(
            sceneryCoverage = reopenedScene.sceneryCoverage.map {
                if (it.id == "travel-coverage") it.copy(bounds = it.bounds.copy(width = 210.0)) else it
            },
        )
        val changedGeometry = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, changedGeometryScene, request, capabilities(), guidelines()),
        ).input
        val geometryInvalidation = service.invalidatePending(
            result,
            changedGeometry,
            result.controls.map { it.id }.toSet(),
            completed,
        )
        assertEquals(listOf("travel"), geometryInvalidation.invalidatedPendingMotionInputIds)
        assertEquals(listOf("ambient", "blink"), geometryInvalidation.unaffectedPendingMotionInputIds)
    }

    @Test
    fun `inspiration and wrong-role imports cannot become finished scenes on reopen`() {
        val fixture = fixture()
        val source = rgb(root.resolve("outside/inspiration.png"), 100, 60, Color(70, 80, 90))
        val inspiration = assertIs<VideoAssetImportResult.Imported>(fixture.assetImporter("inspiration").import(
            fixture.session, ImportVideoAsset(source, VideoReferenceRole.COMPLETE_SCENE,
                usageIntent = VideoAssetUsageIntent.INSPIRATION_ONLY),
        ))
        fixture.session = inspiration.session
        val before = fixture.store.open(fixture.projectRoot)
        val request = PrepareVideoAnimationAssets(VideoVersionedId("forbidden", 1), inspiration.asset.id)
        val denied = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.sceneImporter.import(
            fixture.projectRoot, before.revision, request,
        ))
        assertTrue(denied.deficiencies.any { it.message.contains("inspiration-only") && it.nextAction.contains("production") })
        assertEquals(before, fixture.store.open(fixture.projectRoot))
        val library = assertIs<VideoAssetLibraryResult.Loaded>(fixture.assetImporter("unused").open(fixture.projectRoot))
        assertIs<VideoSceneLookSelectionResult.Rejected>(VideoSceneLooks().select(
            library.session.project, library.assets, inspiration.asset.id,
        ))
        val wrong = fixture.import("wrong-role", rgb(root.resolve("outside/wrong.png"), 100, 60, Color(10, 30, 50)),
            VideoReferenceRole.SUBJECT)
        val deniedRole = assertIs<VideoPreparedSceneImportResult.Rejected>(fixture.sceneImporter.import(
            fixture.projectRoot, wrong.session.project.revision, request.copy(
                sceneId = VideoVersionedId("wrong-scene", 1), finishedSceneReferenceId = wrong.asset.id),
        ))
        assertTrue(deniedRole.deficiencies.any { it.message.contains("not the finished-scene role") })
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.isEmpty())
    }

    @Test
    fun `flat image cannot satisfy blink and redesign requests replacement before dispatch`() {
        val fixture = fixture()
        val finished = fixture.import(
            "flat-finished",
            rgb(root.resolve("outside/flat.png"), 100, 60, Color(60, 70, 90)),
            VideoReferenceRole.COMPLETE_SCENE,
        )
        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.sceneImporter.import(
            fixture.projectRoot,
            finished.session.project.revision,
            PrepareVideoAnimationAssets(VideoVersionedId("flat-motion", 1), finished.asset.id),
        ))
        val library = assertIs<VideoAssetLibraryResult.Loaded>(fixture.assetImporter("unused").open(fixture.projectRoot))
        val look = assertIs<VideoSceneLookSelectionResult.Selected>(
            VideoSceneLooks().select(library.session.project, library.assets, finished.asset.id),
        ).look
        val imageToVideo = saved.scene.motionCapabilities.single()
        val service = VideoScenePreparation()

        listOf(
            VideoSceneMotionIntent.BLINK to "blink pose",
            VideoSceneMotionIntent.CHARACTER_ACTION to "separated character",
            VideoSceneMotionIntent.SCENERY_TRAVEL to "external scenery",
            VideoSceneMotionIntent.EFFECT to "effect anchor",
        ).forEach { (intent, expectedAction) ->
            val rejected = assertIs<VideoScenePreparationResult.Rejected>(service.prepare(
                look,
                saved.scene,
                VideoSceneMotionRequest(
                    "Perform the selected motion.",
                    listOf(control(intent.name.lowercase(), intent, imageToVideo.id, 0.0, 1.0, 1.0)),
                ),
                capabilities(),
                guidelines(),
            ))
            assertEquals(VideoScenePreparationProblemCode.UNSUPPORTED_ACTION, rejected.problems.single().code)
            assertTrue(rejected.problems.single().nextAction.contains(expectedAction))
        }

        val redesign = assertIs<VideoScenePreparationResult.Rejected>(service.prepare(
            look,
            saved.scene,
            VideoSceneMotionRequest(
                "Drift slowly.",
                listOf(control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT, imageToVideo.id, 0.0, 1.0, 1.0)),
                appearanceRedesign = "Change the coat and redraw the scene in oils.",
            ),
            capabilities(),
            guidelines(),
        ))
        assertTrue(redesign.problems.any { it.code == VideoScenePreparationProblemCode.REPLACEMENT_ARTWORK_REQUIRED })
        assertTrue(redesign.problems.single { it.code == VideoScenePreparationProblemCode.REPLACEMENT_ARTWORK_REQUIRED }
            .nextAction.contains("replacement artwork"))

        val noControl = assertIs<VideoScenePreparationResult.Rejected>(service.prepare(
            look,
            saved.scene,
            VideoSceneMotionRequest("Move gently.", emptyList()),
            capabilities(),
            guidelines(),
        ))
        assertTrue(noControl.problems.any { it.code == VideoScenePreparationProblemCode.MISSING_MOTION_CONTROL })

        val rejectedCapabilityScene = saved.scene.copy(
            motionCapabilities = listOf(imageToVideo.copy(reviewStatus = VideoComponentReviewStatus.REJECTED)),
        )
        val rejectedCapability = assertIs<VideoScenePreparationResult.Rejected>(service.prepare(
            look,
            rejectedCapabilityScene,
            VideoSceneMotionRequest(
                "Move gently.",
                listOf(control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT, imageToVideo.id, 0.0, 1.0, 1.0)),
            ),
            capabilities(),
            guidelines(),
        ))
        assertTrue(rejectedCapability.problems.any { it.code == VideoScenePreparationProblemCode.REJECTED_COMPONENT })
        assertTrue(fixture.store.open(fixture.projectRoot).preparedSceneVersions.size == 1)
    }

    @Test
    fun `asset controls disclose compositor gaps without claiming execution readiness`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val look = fixture.look(prepared.finishedId)
        val service = VideoScenePreparation()
        val scene = compositionScene(prepared.scene)
        val requests = allControls(scene)
        val result = assertIs<VideoScenePreparationResult.Prepared>(service.prepare(
            look, scene, requests, capabilities(), guidelines(),
        )).input
        val limitations = result.promptIssues.filter { it.code == VideoPromptIssueCode.INFORMATIONAL_LIMITATION }
        assertTrue(limitations.any { it.message.contains("subject") && it.message.contains("translation") })
        assertTrue(limitations.any { it.message.contains("effect") && it.message.contains("steam") })
        assertTrue(limitations.any { it.message.contains("travel") && it.message.contains("coverage") })
        assertTrue(limitations.all { !it.blocksInference && it.message.contains("VG2") })
        assertTrue(result.canDispatch) // preparation, not a production renderer or ComfyUI controlled-motion claim
        val flat = assertIs<VideoScenePreparationResult.Prepared>(service.prepare(
            look, prepared.scene, requests.copy(controls = requests.controls.take(1)), capabilities(), guidelines(),
        )).input
        assertTrue(flat.promptIssues.none { it.code == VideoPromptIssueCode.INFORMATIONAL_LIMITATION })
        val wideEffect = requests.copy(controls = requests.controls.filter { it.id == "effect" }.map {
            it.copy(maximum = 20.0)
        })
        val bounded = assertIs<VideoScenePreparationResult.Prepared>(service.prepare(
            look, scene, wideEffect, capabilities(), guidelines(),
        )).input
        assertTrue(bounded.promptIssues.any {
            it.code == VideoPromptIssueCode.INFORMATIONAL_LIMITATION &&
                it.message.contains("8/s") && it.message.contains("28 px/s")
        })
    }

    @Test
    fun `unsupported prepared controls never reach controlled job admission`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val scene = compositionScene(prepared.scene)
        val look = fixture.look(prepared.finishedId)
        val service = VideoScenePreparation()
        var admissionReads = 0
        val coordinator = VideoJobCoordinator("test-domain", object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String): VideoJobLedger {
                admissionReads++
                error("Unsupported motion reached the durable job ledger")
            }
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger =
                error("Unsupported motion reached durable publication")
        }, emptyList())
        val generator = VideoClipGeneration(service, coordinator, VideoResultImport(fixture.store, VideoMediaProbe()),
            VideoMotionRenderer(), "controlled-local", VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000))
        val controls = allControls(scene).controls.associateBy { it.id }
        val cases = listOf(
            listOf(controls.getValue("subject")) to "translation",
            listOf(controls.getValue("effect").copy(maximum = 20.0)) to "8/s",
            listOf(controls.getValue("ambient")) to "I2V",
            listOf(controls.getValue("blink"), controls.getValue("subject")) to "translation",
        )
        for ((selected, reason) in cases) {
            // These assets remain preparation-eligible; unsupported renderer controls must
            // still be stopped before an intent is persisted or submitted.
            assertIs<VideoScenePreparationResult.Prepared>(service.prepare(
                look, scene, VideoSceneMotionRequest("Move gently.", selected), capabilities(), guidelines(),
            ))
            val rejected = assertIs<VideoClipGenerationResult.Rejected>(generator.generate(VideoClipGenerationRequest(
                project = fixture.session.project, expectedRevision = fixture.session.project.revision,
                look = look, scene = scene, motionRequest = VideoSceneMotionRequest("Move gently.", selected),
                backendCapabilities = capabilities(), guidelineSet = guidelines(),
                preparedDependencies = listOf(VideoGenerationDependencyPin("scene", "a".repeat(64))),
                runtimeDependencies = listOf(VideoGenerationDependencyPin("runtime", "b".repeat(64))),
                startFrame = 0, endFrameExclusive = 30, seed = 42,
            )))
            assertTrue(rejected.reason.contains(reason), rejected.reason)
            assertEquals(0, admissionReads)
        }
    }

    @Test
    fun `replacement look cannot be paired with an older prepared scene pin`() {
        val fixture = fixture()
        val original = fixture.import(
            "original",
            rgb(root.resolve("outside/original.png"), 100, 60, Color(80, 90, 110)),
            VideoReferenceRole.COMPLETE_SCENE,
        )
        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.sceneImporter.import(
            fixture.projectRoot,
            original.session.project.revision,
            PrepareVideoAnimationAssets(VideoVersionedId("original-motion", 1), original.asset.id),
        ))
        fixture.session = app.melotrail.video.application.VideoProjectSession(fixture.projectRoot, saved.project)
        val replacement = fixture.import(
            "replacement",
            rgb(root.resolve("outside/replacement.png"), 100, 60, Color(150, 100, 80)),
            VideoReferenceRole.COMPLETE_SCENE,
        )
        val library = assertIs<VideoAssetLibraryResult.Loaded>(fixture.assetImporter("unused").open(fixture.projectRoot))
        val replacementLook = assertIs<VideoSceneLookSelectionResult.Selected>(
            VideoSceneLooks().select(library.session.project, library.assets, replacement.asset.id),
        ).look
        val capability = saved.scene.motionCapabilities.single()

        val rejected = assertIs<VideoScenePreparationResult.Rejected>(VideoScenePreparation().prepare(
            replacementLook,
            saved.scene,
            VideoSceneMotionRequest(
                "Move slowly.",
                listOf(control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT, capability.id, 0.0, 1.0, 1.0)),
            ),
            capabilities(),
            guidelines(),
        ))

        assertTrue(rejected.problems.any { it.code == VideoScenePreparationProblemCode.SOURCE_PIN_MISMATCH })
        assertTrue(rejected.problems.single { it.code == VideoScenePreparationProblemCode.SOURCE_PIN_MISMATCH }
            .nextAction.contains("new scene"))
        assertEquals(saved.scene, fixture.scenes.load(fixture.projectRoot, saved.scene.id))
    }

    @Test
    fun `unsupported preservation guidance rejects motion before a dispatchable input exists`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val look = fixture.look(prepared.finishedId)
        val camera = prepared.scene.motionCapabilities.single { it.control == VideoMotionControl.IMAGE_TO_VIDEO }
        val request = VideoSceneMotionRequest(
            "Drift slowly.",
            listOf(control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT, camera.id, 0.0, 1.0, 1.0)),
        )

        val result = assertIs<VideoScenePreparationResult.Rejected>(VideoScenePreparation().prepare(
            look, prepared.scene, request,
            capabilities().copy(supportedGuidance = VideoGuidanceKind.entries.toSet() - VideoGuidanceKind.STANDARD_GUIDELINE),
            guidelines(),
        ))

        assertEquals(VideoScenePreparationProblemCode.PROMPT_COMPILATION_BLOCKED, result.problems.single().code)
        assertTrue(result.problems.single().message.contains("finished-artwork-preservation"))
        assertTrue(result.problems.single().message.contains("Complete scene reference guidance"))
    }

    @Test
    fun `pending controls track clean plate pose and anchor dependencies independently`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val subject = prepared.scene.layers.single { it.id == "subject" }
        val point = VideoPlacedPoint("scene", VideoPoint(20.0, 25.0))
        val scene = prepared.scene.copy(
            layers = prepared.scene.layers.map { if (it.id == subject.id) it.copy(pivot = point) else it },
            poses = prepared.scene.poses.map { it.copy(pivot = point) },
            subjectLandmarks = listOf(VideoSubjectLandmark("mouth", "subject", point)),
            effectAnchors = listOf(VideoEffectAnchor("steam", "subject", landmarkId = "mouth")),
            motionCapabilities = prepared.scene.motionCapabilities + effectCapability(),
        )
        val request = allControls(scene)
        val service = VideoScenePreparation()
        val look = fixture.look(prepared.finishedId)
        fun prepare(candidate: VideoPreparedScene) = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, candidate, request, capabilities(), guidelines()),
        ).input
        val original = prepare(scene)
        val completed = listOf(VideoVersionedId("retained-result", 1))
        fun checkMutation(name: String, changed: VideoPreparedScene, affected: List<String>) {
            val actual = service.invalidatePending(original, prepare(changed), request.controls.map { it.id }.toSet(), completed)
            assertEquals(affected.sorted(), actual.invalidatedPendingMotionInputIds, name)
            assertEquals((request.controls.map { it.id } - affected.toSet()).sorted(), actual.unaffectedPendingMotionInputIds, name)
            assertEquals(completed, actual.retainedCompletedResultIds, name)
        }
        fun changeLayer(id: String, change: (VideoPreparedLayer) -> VideoPreparedLayer) =
            scene.copy(layers = scene.layers.map { if (it.id == id) change(it) else it })

        checkMutation("clean background bytes", changeLayer("clean-background") {
            it.copy(image = changedImage(it.image))
        }, listOf("blink", "subject"))
        checkMutation("clean background geometry", changeLayer("clean-background") {
            it.copy(pivot = point)
        }, listOf("blink", "subject"))
        checkMutation("subject bytes", changeLayer("subject") {
            it.copy(image = changedImage(it.image))
        }, listOf("blink", "subject", "effect"))
        checkMutation("subject transform", changeLayer("subject") {
            it.copy(transform = requireNotNull(it.transform).copy(rotationDegrees = 1.0))
        }, listOf("blink", "subject", "effect"))
        checkMutation("pose bytes", scene.copy(poses = scene.poses.map {
            it.copy(image = changedImage(it.image))
        }), listOf("blink"))
        checkMutation("pose transform", scene.copy(poses = scene.poses.map {
            it.copy(transform = requireNotNull(it.transform).copy(rotationDegrees = 1.0))
        }), listOf("blink"))
        checkMutation("pose pivot", scene.copy(poses = scene.poses.map {
            it.copy(pivot = point.copy(point = VideoPoint(21.0, 25.0)))
        }), listOf("blink"))
        checkMutation("pose measured alpha", scene.copy(poses = scene.poses.map {
            val alpha = requireNotNull(it.alpha)
            it.copy(alpha = alpha.copy(opaquePixels = alpha.opaquePixels - 1, translucentPixels = alpha.translucentPixels + 1))
        }), listOf("blink"))
        checkMutation("referenced landmark position", scene.copy(subjectLandmarks = scene.subjectLandmarks.map {
            it.copy(position = point.copy(point = VideoPoint(21.0, 25.0)))
        }), listOf("effect"))
        checkMutation("referenced landmark review", scene.copy(subjectLandmarks = scene.subjectLandmarks.map {
            it.copy(reviewStatus = VideoComponentReviewStatus.APPROVED)
        }), listOf("effect"))
        assertEquals(scene.poses.single().image, original.scene.poses.single().image)
    }

    @Test
    fun `reimported clean plate invalidates pending pose but retains unrelated work and earlier scene`() {
        val fixture = fixture()
        val initial = fixture.layeredScene()
        val replacement = fixture.import(
            "replacement-clean", rgb(root.resolve("outside/replacement-clean.png"), 100, 60, Color(70, 90, 120)),
            VideoReferenceRole.ENVIRONMENT,
        )
        fun placed(layer: VideoPreparedLayer) = VideoPlacedAnimationAsset(
            layer.id, initial.scene.source.references.single { it.original == layer.image }.id, layer.bounds,
        )
        val subject = initial.scene.layers.single { it.id == "subject" }
        val travel = initial.scene.layers.single { it.id == "travel" }
        val pose = initial.scene.poses.single()
        val changed = assertIs<VideoPreparedSceneImportResult.Saved>(fixture.sceneImporter.import(
            fixture.projectRoot, replacement.session.project.revision,
            PrepareVideoAnimationAssets(
                sceneId = initial.scene.id.copy(version = 2),
                finishedSceneReferenceId = initial.finishedId,
                subjectLayers = listOf(placed(subject)),
                cleanBackground = VideoPlacedAnimationAsset("clean-background", replacement.asset.id,
                    initial.scene.layers.single { it.id == "clean-background" }.bounds),
                scenery = listOf(VideoSceneryAnimationAsset(placed(travel), initial.scene.sceneryCoverage.single().bounds, "travel-coverage")),
                poses = listOf(VideoPoseAnimationAsset(VideoPlacedAnimationAsset(
                    pose.id, initial.scene.source.references.single { it.original == pose.image }.id, pose.bounds,
                ), subject.id)),
            ),
        ))
        val look = fixture.look(initial.finishedId)
        val request = allControls(initial.scene).let { it.copy(controls = it.controls.filter { control -> control.id != "effect" }) }
        val service = VideoScenePreparation()
        fun prepare(scene: VideoPreparedScene) = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, scene, request, capabilities(), guidelines()),
        ).input
        val previous = prepare(fixture.scenes.load(fixture.projectRoot, initial.scene.id))
        val current = prepare(fixture.scenes.load(fixture.projectRoot, changed.scene.id))
        val completed = listOf(VideoVersionedId("completed-take", 1))
        val actual = service.invalidatePending(previous, current, request.controls.map { it.id }.toSet(), completed)
        assertEquals(listOf("blink", "subject"), actual.invalidatedPendingMotionInputIds)
        assertEquals(listOf("ambient", "travel"), actual.unaffectedPendingMotionInputIds)
        assertEquals(completed, actual.retainedCompletedResultIds)
        assertEquals(initial.scene, fixture.scenes.load(fixture.projectRoot, initial.scene.id))
        assertEquals(initial.scene.poses, changed.scene.poses)
        assertNotEquals(initial.scene.layers.single { it.id == "clean-background" }.image, replacement.asset.original)
    }

    @Test
    fun `composition closure tracks foreground masks and transitive relations without invalidating flat input`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val point = VideoPlacedPoint("scene", VideoPoint(20.0, 25.0))
        val scene = compositionScene(prepared.scene)
        val service = VideoScenePreparation()
        val request = allControls(scene)
        val look = fixture.look(prepared.finishedId)
        fun prepare(candidate: VideoPreparedScene) = assertIs<VideoScenePreparationResult.Prepared>(
            service.prepare(look, candidate, request, capabilities(), guidelines()),
        ).input
        val original = prepare(scene)
        val completed = listOf(VideoVersionedId("retained-result", 1))
        fun checkMutation(name: String, candidate: VideoPreparedScene) {
            val invalidation = service.invalidatePending(original, prepare(candidate), request.controls.map { it.id }.toSet(), completed)
            assertEquals(listOf("blink", "effect", "subject", "travel"), invalidation.invalidatedPendingMotionInputIds, name)
            assertEquals(listOf("ambient"), invalidation.unaffectedPendingMotionInputIds, name)
            assertEquals(completed, invalidation.retainedCompletedResultIds, name)
        }
        fun changeMask(change: (VideoPreparedMask) -> VideoPreparedMask) =
            scene.copy(masks = scene.masks.map { if (it.id == "occlusion") change(it) else it })
        checkMutation("foreground bytes", scene.copy(layers = scene.layers.map {
            if (it.id == "foreground") it.copy(image = changedImage(it.image)) else it
        }))
        checkMutation("foreground placement", scene.copy(layers = scene.layers.map {
            if (it.id == "foreground") it.copy(transform = requireNotNull(it.transform).copy(rotationDegrees = 1.0)) else it
        }))
        checkMutation("transitive support bytes", scene.copy(layers = scene.layers.map {
            if (it.id == "effect-support") it.copy(image = changedImage(it.image)) else it
        }))
        checkMutation("mask bytes", changeMask { it.copy(image = changedImage(it.image)) })
        checkMutation("mask transform", changeMask {
            it.copy(transform = requireNotNull(it.transform).copy(rotationDegrees = 1.0))
        })
        checkMutation("mask pivot", changeMask { it.copy(pivot = point.copy(point = VideoPoint(21.0, 25.0))) })
        checkMutation("mask measured alpha", changeMask {
            val alpha = requireNotNull(it.alpha)
            it.copy(alpha = alpha.copy(opaquePixels = alpha.opaquePixels - 1, translucentPixels = alpha.translucentPixels + 1))
        })
        checkMutation("depth order", scene.copy(depthRelations = listOf(
            scene.depthRelations[0], VideoDepthRelation("effect-support", "foreground"),
        )))
        checkMutation("depth review", scene.copy(depthRelations = scene.depthRelations.map {
            it.copy(reviewStatus = VideoComponentReviewStatus.APPROVED)
        }))
        checkMutation("occlusion order", scene.copy(occlusionRelations = listOf(
            VideoOcclusionRelation("subject", "foreground", "occlusion"),
        )))
        checkMutation("occlusion mask binding", scene.copy(occlusionRelations = listOf(
            scene.occlusionRelations.single().copy(maskId = "alternative-occlusion"),
        )))
        checkMutation("occlusion review", scene.copy(occlusionRelations = scene.occlusionRelations.map {
            it.copy(reviewStatus = VideoComponentReviewStatus.APPROVED)
        }))
        // An unrelated pose/landmark is not pulled in through its subject's composition edges.
        val unrelated = scene.copy(
            poses = scene.poses + scene.poses.single().copy(id = "unused-pose"),
            subjectLandmarks = scene.subjectLandmarks + VideoSubjectLandmark("unused-point", "subject", point),
        )
        assertEquals(original.controls.map { it.fingerprint }, prepare(unrelated).controls.map { it.fingerprint })
        assertEquals(original.controls.map { it.fingerprint }, prepare(scene.copy(
            layers = scene.layers.reversed(), masks = scene.masks.reversed(), depthRelations = scene.depthRelations.reversed(),
        )).controls.map { it.fingerprint })
    }

    @Test
    fun `finished look review remains explicit at admission`() = checkReviewAdmission(VideoSceneComponentKind.FINISHED_LOOK)

    @Test
    fun `consumed foreground review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.LAYER)

    @Test
    fun `consumed mask review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.MASK)

    @Test
    fun `consumed pose review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.POSE)

    @Test
    fun `referenced landmark review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.SUBJECT_LANDMARK)

    @Test
    fun `transitive depth relation review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.DEPTH_RELATION)

    @Test
    fun `consumed occlusion relation review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.OCCLUSION_RELATION)

    @Test
    fun `consumed effect anchor review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.EFFECT_ANCHOR)

    @Test
    fun `consumed scenery coverage review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.SCENERY_COVERAGE)

    @Test
    fun `selected capability review controls admission`() = checkReviewAdmission(VideoSceneComponentKind.MOTION_CAPABILITY)

    private fun checkReviewAdmission(kind: VideoSceneComponentKind) {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val scene = reviewed(compositionScene(prepared.scene), VideoComponentReviewStatus.APPROVED)
        val look = fixture.look(prepared.finishedId).copy(identityReview = VideoAssetIdentityReview.APPROVED)
        val request = allControls(scene)
        val service = VideoScenePreparation()
        fun prepare(status: VideoComponentReviewStatus) = service.prepare(
            if (kind == VideoSceneComponentKind.FINISHED_LOOK) {
                look.copy(identityReview = VideoAssetIdentityReview.valueOf(status.name))
            } else look,
            withComponentReview(scene, kind, status), request, capabilities(), guidelines(),
        )
        val original = assertIs<VideoScenePreparationResult.Prepared>(prepare(VideoComponentReviewStatus.APPROVED)).input
        assertEquals(VideoScenePreparationStatus.READY, original.status)
        val expectedId = reviewId(kind)
        assertTrue(original.componentReviews.contains(
            VideoSceneComponentReview(expectedId, kind, VideoComponentReviewStatus.APPROVED),
        ))

        val rejected = assertIs<VideoScenePreparationResult.Rejected>(prepare(VideoComponentReviewStatus.REJECTED))
        assertEquals(VideoScenePreparationProblemCode.REJECTED_COMPONENT, rejected.problems.single().code)
        assertEquals(expectedId, rejected.problems.single().targetId)
        assertTrue(rejected.problems.single().message.contains(kind.label))

        val unreviewed = assertIs<VideoScenePreparationResult.Prepared>(prepare(VideoComponentReviewStatus.UNREVIEWED)).input
        assertEquals(VideoScenePreparationStatus.REVIEW_REQUIRED, unreviewed.status)
        assertTrue(unreviewed.canDispatch)
        assertEquals(
            listOf(VideoSceneComponentReview(expectedId, kind, VideoComponentReviewStatus.UNREVIEWED)),
            unreviewed.componentReviews.filter { it.status != VideoComponentReviewStatus.APPROVED },
        )
        assertNotEquals(original.requestFingerprint, unreviewed.requestFingerprint)
        if (kind == VideoSceneComponentKind.FINISHED_LOOK) {
            val completed = listOf(VideoVersionedId("completed-take", 1))
            val invalidation = service.invalidatePending(original, unreviewed,
                request.controls.map { it.id }.toSet(), completed)
            assertEquals(request.controls.map { it.id }.sorted(), invalidation.invalidatedPendingMotionInputIds)
            assertEquals(completed, invalidation.retainedCompletedResultIds)
        } else {
            val unaffected = when (kind) {
                VideoSceneComponentKind.POSE -> setOf("ambient", "subject", "travel", "effect")
                VideoSceneComponentKind.SUBJECT_LANDMARK, VideoSceneComponentKind.EFFECT_ANCHOR,
                VideoSceneComponentKind.MOTION_CAPABILITY -> setOf("ambient", "blink", "subject", "travel")
                VideoSceneComponentKind.SCENERY_COVERAGE -> setOf("ambient", "blink", "subject", "effect")
                else -> setOf("ambient")
            }
            for (control in request.controls.filter { it.id in unaffected }) {
                val scoped = assertIs<VideoScenePreparationResult.Prepared>(service.prepare(
                    look, withComponentReview(scene, kind, VideoComponentReviewStatus.REJECTED),
                    request.copy(controls = listOf(control)), capabilities(), guidelines(),
                ), control.id).input
                assertEquals(VideoScenePreparationStatus.READY, scoped.status, control.id)
                assertFalse(scoped.componentReviews.any { it.kind == kind && it.componentId == expectedId }, control.id)
                assertEquals(original.controls.single { it.id == control.id }.fingerprint,
                    scoped.controls.single().fingerprint, control.id)
            }
        }
    }

    @Test
    fun `transitive layer and mask rejection cannot hide behind approved direct targets`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val base = reviewed(compositionScene(prepared.scene), VideoComponentReviewStatus.APPROVED)
        val tail = base.layers.single { it.id == "effect-support" }.copy(id = "tail")
        val tailMask = base.masks.first().copy(id = "tail-mask", layerIds = listOf("effect-support", "tail"))
        val scene = base.copy(
            layers = base.layers + tail,
            masks = listOf(tailMask) + base.masks,
            occlusionRelations = listOf(VideoOcclusionRelation(
                "tail", "effect-support", "tail-mask", VideoComponentReviewStatus.APPROVED,
            )) + base.occlusionRelations,
        )
        val request = allControls(scene).let { it.copy(controls = it.controls.filter { control -> control.id == "subject" }) }
        val look = fixture.look(prepared.finishedId).copy(identityReview = VideoAssetIdentityReview.APPROVED)
        val service = VideoScenePreparation()
        for (id in listOf("effect-support", "tail", "tail-mask", "clean-background", "subject", "tail:effect-support:tail-mask")) {
            val candidate = scene.copy(
                layers = scene.layers.map { if (it.id == id) it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) else it },
                masks = scene.masks.map { if (it.id == id) it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) else it },
                occlusionRelations = scene.occlusionRelations.map {
                    if (id == "${it.occluderLayerId}:${it.occludedLayerId}:${it.maskId}") {
                        it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED)
                    } else it
                },
            )
            val rejected = assertIs<VideoScenePreparationResult.Rejected>(
                service.prepare(look, candidate, request, capabilities(), guidelines()), id,
            )
            assertEquals(VideoScenePreparationProblemCode.REJECTED_COMPONENT, rejected.problems.single().code, id)
            assertEquals(id, rejected.problems.single().targetId, id)
        }
    }

    @Test
    fun `rejected components outside the consumed closure do not affect reviews or fingerprints`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val scene = reviewed(compositionScene(prepared.scene), VideoComponentReviewStatus.APPROVED)
        val rejected = VideoComponentReviewStatus.REJECTED
        val subject = scene.layers.single { it.id == "subject" }
        val unusedSubject = subject.copy(id = "unused-subject", reviewStatus = rejected)
        val unusedScenery = subject.copy(id = "unused-scenery", kind = VideoLayerKind.SCENERY, reviewStatus = rejected)
        val unusedEnvironment = scene.layers.single { it.id == "clean-background" }.copy(
            id = "unused-environment", bounds = VideoRect("unused-space", 0.0, 0.0, 100.0, 60.0), reviewStatus = rejected,
        )
        val unusedForeground = subject.copy(
            id = "unused-foreground", kind = VideoLayerKind.FOREGROUND,
            bounds = subject.bounds.copy(coordinateSpaceId = "unused-space"), reviewStatus = rejected,
        )
        val unusedMask = scene.masks.first().copy(
            id = "unused-mask", layerIds = listOf("unused-subject", "unused-scenery"), reviewStatus = rejected,
        )
        val unrelated = scene.copy(
            coordinateSpaces = scene.coordinateSpaces + scene.coordinateSpaces.first().copy(id = "unused-space"),
            layers = scene.layers + listOf(unusedSubject, unusedScenery, unusedEnvironment, unusedForeground),
            masks = scene.masks + unusedMask,
            poses = scene.poses + scene.poses.single().copy(id = "unused-pose", reviewStatus = rejected),
            subjectLandmarks = scene.subjectLandmarks + scene.subjectLandmarks.single().copy(id = "unused-point", reviewStatus = rejected),
            effectAnchors = scene.effectAnchors + scene.effectAnchors.single().copy(id = "unused-anchor", reviewStatus = rejected),
            sceneryCoverage = scene.sceneryCoverage + scene.sceneryCoverage.single().copy(id = "unused-coverage", reviewStatus = rejected),
            motionCapabilities = scene.motionCapabilities + effectCapability().copy(id = "unused-capability", reviewStatus = rejected),
            depthRelations = scene.depthRelations + VideoDepthRelation("unused-subject", "unused-scenery", rejected),
            occlusionRelations = scene.occlusionRelations + VideoOcclusionRelation("unused-subject", "unused-scenery", "unused-mask", rejected),
        )
        val look = fixture.look(prepared.finishedId).copy(identityReview = VideoAssetIdentityReview.APPROVED)
        val service = VideoScenePreparation()
        // Independent calls prove scoping for each control, including subject/pose clean plates.
        for (control in allControls(scene).controls) {
            val request = VideoSceneMotionRequest("Move gently.", listOf(control))
            fun prepare(candidate: VideoPreparedScene) = assertIs<VideoScenePreparationResult.Prepared>(
                service.prepare(look, candidate, request, capabilities(), guidelines()), control.id,
            ).input
            val original = prepare(scene)
            val changed = prepare(unrelated)
            assertEquals(VideoScenePreparationStatus.READY, changed.status, control.id)
            assertTrue(changed.canDispatch)
            assertEquals(original.componentReviews, changed.componentReviews, control.id)
            assertEquals(original.requestFingerprint, changed.requestFingerprint, control.id)
            assertEquals(original.controls.single().fingerprint, changed.controls.single().fingerprint, control.id)
        }
    }

    @Test
    fun `review identities and consumed relation rejection are stable under reorder`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val base = reviewed(compositionScene(prepared.scene), VideoComponentReviewStatus.APPROVED)
        // Give both relation lists multiple unique, acyclic edges so reversal is meaningful.
        val scene = base.copy(
            masks = base.masks + base.masks.first().copy(
                id = "support-occlusion", layerIds = listOf("foreground", "effect-support"),
            ),
            occlusionRelations = base.occlusionRelations + VideoOcclusionRelation(
                "foreground", "effect-support", "support-occlusion", VideoComponentReviewStatus.APPROVED,
            ),
        )
        val look = fixture.look(prepared.finishedId).copy(identityReview = VideoAssetIdentityReview.APPROVED)
        val request = allControls(scene)
        fun prepare(candidate: VideoPreparedScene) = VideoScenePreparation().prepare(look, candidate, request, capabilities(), guidelines())
        fun reorder(candidate: VideoPreparedScene) = candidate.copy(
            layers = candidate.layers.reversed(), masks = candidate.masks.reversed(),
            depthRelations = candidate.depthRelations.reversed(), occlusionRelations = candidate.occlusionRelations.reversed(),
            motionCapabilities = candidate.motionCapabilities.reversed(),
        )
        val original = assertIs<VideoScenePreparationResult.Prepared>(prepare(scene)).input
        val reordered = assertIs<VideoScenePreparationResult.Prepared>(prepare(reorder(scene))).input
        assertEquals(VideoScenePreparationStatus.READY, original.status)
        assertEquals(original.componentReviews, reordered.componentReviews)
        assertEquals(original.requestFingerprint, reordered.requestFingerprint)
        assertEquals(original.controls.map { it.fingerprint }, reordered.controls.map { it.fingerprint })
        val relations = original.componentReviews.filter {
            it.kind in setOf(VideoSceneComponentKind.DEPTH_RELATION, VideoSceneComponentKind.OCCLUSION_RELATION)
        }
        assertEquals(setOf(
            "effect-support:travel", "foreground:effect-support",
            "foreground:subject:occlusion", "foreground:effect-support:support-occlusion",
        ), relations.map { it.componentId }.toSet())
        // Shared consumption by several controls produces one review per domain record.
        assertEquals(4, relations.size)
        for (review in relations) {
            val rejected = scene.copy(
                depthRelations = scene.depthRelations.map {
                    if (review.kind == VideoSceneComponentKind.DEPTH_RELATION &&
                        "${it.nearerLayerId}:${it.fartherLayerId}" == review.componentId
                    ) it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) else it
                },
                occlusionRelations = scene.occlusionRelations.map {
                    if (review.kind == VideoSceneComponentKind.OCCLUSION_RELATION &&
                        "${it.occluderLayerId}:${it.occludedLayerId}:${it.maskId}" == review.componentId
                    ) it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) else it
                },
            )
            val result = assertIs<VideoScenePreparationResult.Rejected>(prepare(rejected))
            val reorderedResult = assertIs<VideoScenePreparationResult.Rejected>(prepare(reorder(rejected)))
            assertEquals(result.problems, reorderedResult.problems)
            assertEquals(review.componentId, result.problems.single().targetId)
            assertEquals(VideoScenePreparationProblemCode.REJECTED_COMPONENT, result.problems.single().code)
        }
    }

    @Test
    fun `landmark and layer sharing an id keep separate review identities`() {
        val fixture = fixture()
        val prepared = fixture.layeredScene()
        val base = reviewed(compositionScene(prepared.scene), VideoComponentReviewStatus.APPROVED)
        val scene = base.copy(
            subjectLandmarks = base.subjectLandmarks.map { it.copy(id = "subject", reviewStatus = VideoComponentReviewStatus.UNREVIEWED) },
            effectAnchors = base.effectAnchors.map { it.copy(landmarkId = "subject") },
        )
        val look = fixture.look(prepared.finishedId).copy(identityReview = VideoAssetIdentityReview.APPROVED)
        val request = allControls(scene).let { it.copy(controls = it.controls.filter { control -> control.id == "effect" }) }
        val result = assertIs<VideoScenePreparationResult.Prepared>(
            VideoScenePreparation().prepare(look, scene, request, capabilities(), guidelines()),
        ).input
        assertEquals(setOf(
            VideoSceneComponentReview("subject", VideoSceneComponentKind.LAYER, VideoComponentReviewStatus.APPROVED),
            VideoSceneComponentReview("subject", VideoSceneComponentKind.SUBJECT_LANDMARK, VideoComponentReviewStatus.UNREVIEWED),
        ), result.componentReviews.filter { it.componentId == "subject" }.toSet())
        assertEquals(VideoScenePreparationStatus.REVIEW_REQUIRED, result.status)
        assertTrue(result.canDispatch)
    }

    private fun reviewId(kind: VideoSceneComponentKind): String = when (kind) {
        VideoSceneComponentKind.FINISHED_LOOK -> "finished-v1"
        VideoSceneComponentKind.LAYER -> "foreground"
        VideoSceneComponentKind.MASK -> "occlusion"
        VideoSceneComponentKind.POSE -> "blink"
        VideoSceneComponentKind.SUBJECT_LANDMARK -> "mouth"
        VideoSceneComponentKind.DEPTH_RELATION -> "effect-support:travel"
        VideoSceneComponentKind.OCCLUSION_RELATION -> "foreground:subject:occlusion"
        VideoSceneComponentKind.EFFECT_ANCHOR -> "steam"
        VideoSceneComponentKind.SCENERY_COVERAGE -> "travel-coverage"
        VideoSceneComponentKind.MOTION_CAPABILITY -> "steam-rate"
    }

    private fun withComponentReview(scene: VideoPreparedScene, kind: VideoSceneComponentKind, status: VideoComponentReviewStatus) = when (kind) {
        VideoSceneComponentKind.FINISHED_LOOK -> scene
        VideoSceneComponentKind.LAYER -> scene.copy(layers = scene.layers.map { if (it.id == "foreground") it.copy(reviewStatus = status) else it })
        VideoSceneComponentKind.MASK -> scene.copy(masks = scene.masks.map { if (it.id == "occlusion") it.copy(reviewStatus = status) else it })
        VideoSceneComponentKind.POSE -> scene.copy(poses = scene.poses.map { it.copy(reviewStatus = status) })
        VideoSceneComponentKind.SUBJECT_LANDMARK -> scene.copy(subjectLandmarks = scene.subjectLandmarks.map { it.copy(reviewStatus = status) })
        VideoSceneComponentKind.DEPTH_RELATION -> scene.copy(depthRelations = scene.depthRelations.map {
            if (it.nearerLayerId == "effect-support") it.copy(reviewStatus = status) else it
        })
        VideoSceneComponentKind.OCCLUSION_RELATION -> scene.copy(occlusionRelations = scene.occlusionRelations.map { it.copy(reviewStatus = status) })
        VideoSceneComponentKind.EFFECT_ANCHOR -> scene.copy(effectAnchors = scene.effectAnchors.map { it.copy(reviewStatus = status) })
        VideoSceneComponentKind.SCENERY_COVERAGE -> scene.copy(sceneryCoverage = scene.sceneryCoverage.map { it.copy(reviewStatus = status) })
        VideoSceneComponentKind.MOTION_CAPABILITY -> scene.copy(motionCapabilities = scene.motionCapabilities.map {
            if (it.id == "steam-rate") it.copy(reviewStatus = status) else it
        })
    }

    private fun reviewed(scene: VideoPreparedScene, status: VideoComponentReviewStatus) = scene.copy(
        layers = scene.layers.map { it.copy(reviewStatus = status) },
        masks = scene.masks.map { it.copy(reviewStatus = status) },
        poses = scene.poses.map { it.copy(reviewStatus = status) },
        subjectLandmarks = scene.subjectLandmarks.map { it.copy(reviewStatus = status) },
        effectAnchors = scene.effectAnchors.map { it.copy(reviewStatus = status) },
        sceneryCoverage = scene.sceneryCoverage.map { it.copy(reviewStatus = status) },
        depthRelations = scene.depthRelations.map { it.copy(reviewStatus = status) },
        occlusionRelations = scene.occlusionRelations.map { it.copy(reviewStatus = status) },
        motionCapabilities = scene.motionCapabilities.map { it.copy(reviewStatus = status) },
    )

    private fun compositionScene(base: VideoPreparedScene): VideoPreparedScene {
        val subject = base.layers.single { it.id == "subject" }
        val point = VideoPlacedPoint("scene", VideoPoint(20.0, 25.0))
        val foreground = subject.copy(id = "foreground", kind = VideoLayerKind.FOREGROUND, pivot = point)
        val support = subject.copy(id = "effect-support", kind = VideoLayerKind.EFFECT, pivot = point)
        val mask = VideoPreparedMask(
            "occlusion", subject.image, subject.bounds, listOf("foreground", "subject"),
            transform = subject.transform, pivot = point, alpha = subject.alpha,
        )
        return base.copy(
            layers = base.layers + foreground + support,
            masks = listOf(mask, mask.copy(id = "alternative-occlusion")),
            subjectLandmarks = listOf(VideoSubjectLandmark("mouth", "subject", point)),
            effectAnchors = listOf(VideoEffectAnchor("steam", "subject", landmarkId = "mouth")),
            // Deliberately order the farther edge first to require a second closure pass.
            depthRelations = listOf(
                VideoDepthRelation("effect-support", "travel"),
                VideoDepthRelation("foreground", "effect-support"),
            ),
            occlusionRelations = listOf(VideoOcclusionRelation("foreground", "subject", "occlusion")),
            motionCapabilities = base.motionCapabilities + effectCapability(),
        )
    }

    private fun changedImage(image: VideoAssetImage) =
        image.copy(artifact = image.artifact.copy(sha256 = "f".repeat(64)))

    private fun effectCapability() = VideoMotionCapability(
        "steam-rate", VideoMotionTargetType.EFFECT_ANCHOR, "steam", VideoMotionControl.EFFECT_RATE,
        VideoMotionUnit.PER_SECOND, 0.0, 60.0, 0.0,
    )

    private fun allControls(scene: VideoPreparedScene): VideoSceneMotionRequest {
        fun capability(target: String, type: VideoMotionControl) = scene.motionCapabilities.single {
            it.targetId == target && it.control == type
        }.id
        return VideoSceneMotionRequest("Move gently without changing the artwork.", listOf(
            control("ambient", VideoSceneMotionIntent.CAMERA_OR_AMBIENT,
                scene.motionCapabilities.single { it.control == VideoMotionControl.IMAGE_TO_VIDEO }.id, 0.0, 1.0, 1.0),
            control("blink", VideoSceneMotionIntent.BLINK, capability("blink", VideoMotionControl.POSE_BLEND), 0.0, 1.0, 0.0),
            control("subject", VideoSceneMotionIntent.CHARACTER_ACTION, capability("subject", VideoMotionControl.TRANSLATE_X), -5.0, 5.0, 0.0),
            control("travel", VideoSceneMotionIntent.SCENERY_TRAVEL, capability("travel-coverage", VideoMotionControl.TRANSLATE_X), -100.0, 0.0, 0.0),
            control("effect", VideoSceneMotionIntent.EFFECT, "steam-rate", 0.0, 1.0, 0.0),
        ))
    }

    private fun fixture(): Fixture {
        val projectRoot = root.resolve("project")
        val store = VideoProjectStore(listOf(root.resolve("midi-projects"), root.resolve("midi-exports")))
        val lifecycle = VideoProjectLifecycle(store, CLOCK, idFactory = { "motion-project" })
        val session = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle.create(CreateVideoProject(projectRoot, "Motion input", "motion-project")),
        ).session
        val scenes = VideoPreparedSceneStore(store, VideoImageFiles())
        return Fixture(projectRoot, store, lifecycle, scenes, session)
    }

    private inner class Fixture(
        val projectRoot: Path,
        val store: VideoProjectStore,
        val lifecycle: VideoProjectLifecycle,
        val scenes: VideoPreparedSceneStore,
        var session: app.melotrail.video.application.VideoProjectSession,
    ) {
        val sceneImporter = VideoPreparedSceneImport(store, scenes, VideoImageFiles(), clock = CLOCK)

        fun assetImporter(id: String) = VideoAssetImport(lifecycle, VideoImageFiles(), CLOCK, idFactory = { id })

        fun import(id: String, source: Path, role: VideoReferenceRole): VideoAssetImportResult.Imported {
            val result = assertIs<VideoAssetImportResult.Imported>(
                assetImporter(id).import(session, ImportVideoAsset(source, role)),
            )
            session = result.session
            return result
        }

        fun look(id: VideoVersionedId): app.melotrail.video.application.VideoSceneLook {
            val library = assertIs<VideoAssetLibraryResult.Loaded>(assetImporter("unused").open(projectRoot))
            return assertIs<VideoSceneLookSelectionResult.Selected>(
                VideoSceneLooks().select(library.session.project, library.assets, id),
            ).look
        }

        fun layeredScene(): LayeredScene {
            val finished = import("finished", rgb(root.resolve("outside/finished.png"), 100, 60, Color(45, 55, 75)), VideoReferenceRole.COMPLETE_SCENE)
            val subject = import("subject", cutout(root.resolve("outside/subject.png"), 20, 30, Color(170, 100, 150)), VideoReferenceRole.SUBJECT)
            val background = import("clean", rgb(root.resolve("outside/clean.png"), 100, 60, Color(50, 60, 80)), VideoReferenceRole.ENVIRONMENT)
            val scenery = import("scenery", rgb(root.resolve("outside/scenery.png"), 220, 60, Color(90, 130, 150)), VideoReferenceRole.ENVIRONMENT)
            val blink = import("blink", cutout(root.resolve("outside/blink.png"), 20, 30, Color(160, 90, 140)), VideoReferenceRole.SUBJECT)
            val subjectBounds = VideoRect("scene", 10.0, 10.0, 20.0, 30.0)
            val result = assertIs<VideoPreparedSceneImportResult.Saved>(sceneImporter.import(
                projectRoot,
                blink.session.project.revision,
                PrepareVideoAnimationAssets(
                    sceneId = VideoVersionedId("layered", 1),
                    finishedSceneReferenceId = finished.asset.id,
                    subjectLayers = listOf(VideoPlacedAnimationAsset("subject", subject.asset.id, subjectBounds)),
                    cleanBackground = VideoPlacedAnimationAsset(
                        "clean-background",
                        background.asset.id,
                        VideoRect("scene", 0.0, 0.0, 100.0, 60.0),
                    ),
                    scenery = listOf(VideoSceneryAnimationAsset(
                        VideoPlacedAnimationAsset("travel", scenery.asset.id, VideoRect("scene", 0.0, 0.0, 220.0, 60.0)),
                        VideoRect("scene", 0.0, 0.0, 220.0, 60.0),
                        "travel-coverage",
                    )),
                    poses = listOf(VideoPoseAnimationAsset(
                        VideoPlacedAnimationAsset("blink", blink.asset.id, subjectBounds),
                        "subject",
                    )),
                ),
            ))
            session = app.melotrail.video.application.VideoProjectSession(projectRoot, result.project)
            return LayeredScene(result.scene, finished.asset.id)
        }
    }

    private data class LayeredScene(
        val scene: app.melotrail.video.domain.VideoPreparedScene,
        val finishedId: VideoVersionedId,
    )

    private fun control(
        id: String,
        intent: VideoSceneMotionIntent,
        capabilityId: String,
        minimum: Double,
        maximum: Double,
        defaultValue: Double,
    ) = VideoSceneMotionControlRequest(id, intent, capabilityId, minimum, maximum, defaultValue)

    private fun capabilities() = VideoPromptBackendCapabilities(
        backendId = "motion-fixture",
        capabilityVersion = "v1",
        supportsPrimaryPrompt = true,
        maximumReferenceImages = 1,
        supportedReferenceRoles = setOf(VideoReferenceRole.COMPLETE_SCENE),
        supportsReferencesWithoutRole = false,
        supportedGuidance = VideoGuidanceKind.entries.toSet(),
        minimumClipDurationSeconds = 1,
        maximumClipDurationSeconds = 10,
        modelDependencies = listOf(VideoDependencyPin("model", "1".repeat(64))),
        workflowDependencies = listOf(VideoDependencyPin("workflow", "2".repeat(64))),
        settingsDependencies = listOf(VideoDependencyPin("settings", "3".repeat(64))),
        outputConfiguration = VideoDependencyPin("output", "4".repeat(64)),
    )

    private fun guidelines() = VideoPromptCompiler().decodeGuidelines(
        checkNotNull(javaClass.getResource("/video/video-generation-guidelines.json")).readBytes(),
    )

    private fun rgb(path: Path, width: Int, height: Int, color: Color): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
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
            graphics.fillOval(2, 2, width - 4, height - 4)
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
