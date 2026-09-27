package app.melotrail.video

import app.melotrail.video.application.VideoAssemblyFindingCode
import app.melotrail.video.application.VideoAssemblyPlanResult
import app.melotrail.video.application.VideoAssemblyPlanner
import app.melotrail.video.application.VideoAssemblyPlanningRequest
import app.melotrail.video.application.VideoAssemblyScheduledAction
import app.melotrail.video.application.VideoPreparedMotionInput
import app.melotrail.video.application.VideoPreparedSceneMotion
import app.melotrail.video.application.VideoSceneLook
import app.melotrail.video.application.VideoSceneAppearancePolicy
import app.melotrail.video.application.VideoSceneMotionIntent
import app.melotrail.video.application.VideoSceneMotionMode
import app.melotrail.video.application.VideoScenePreparationStatus
import app.melotrail.video.application.VideoPromptIssue
import app.melotrail.video.application.VideoPromptIssueCode
import app.melotrail.video.domain.MAX_RENDER_FRAMES
import app.melotrail.video.domain.MAX_SAFE_FRAME_INTEGER
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssemblyChunk
import app.melotrail.video.domain.VideoAssemblyFrameRange
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.VideoAssemblyActionKind
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoImageFormat
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPreparedSceneSource
import app.melotrail.video.domain.VideoPreparedReferencePin
import app.melotrail.video.domain.VideoCoordinateSpace
import app.melotrail.video.domain.VideoPreparedLayer
import app.melotrail.video.domain.VideoPreparedPose
import app.melotrail.video.domain.VideoPreparedMask
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoRect
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPoint
import app.melotrail.video.domain.VideoSceneryCoverage
import app.melotrail.video.domain.VideoMotionCapability
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoPreparedDependencyPin
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class VideoAssemblyPlannerTest {
    private val planner = VideoAssemblyPlanner()
    private val input = VideoAssemblyPlanningRequest(
        id = VideoVersionedId("assembly-1", 1), projectId = "owned-project",
        preparedSceneId = VideoVersionedId("prepared-1", 2),
        preparedSceneArtifact = VideoArtifact("prepared/prepared.json", "a".repeat(64)),
        finishedReferenceId = VideoVersionedId("finished-1", 1),
        finishedReferenceArtifact = VideoArtifact("references/finished.png", "b".repeat(64)),
        primaryText = "  Keep the drawn silhouette.\r\nMove gently.  ",
        actionText = "  Blink once.\n  ", cameraText = "  slow drift\r\n ",
        motionText = "  soft motion  ", styleText = "  preserve ink  ",
        guidelineTexts = listOf("  Retain the linework.\n"), durationSeconds = 240, seed = 73,
    )

    @Test
    fun `every supported integer duration partitions its exact frames once at zero support`() {
        for (seconds in 180L..300L) {
            val assembly = proposed(input.copy(durationSeconds = seconds))
            assertEquals(seconds * 30L, assembly.totalFrames)
            assertEquals(0L, assembly.frameZero)
            assertEquals(30, assembly.framesPerSecond)
            assertEquals(1920 to 1080, assembly.width to assembly.height)
            assertEquals(1 to 1, assembly.pixelAspectNumerator to assembly.pixelAspectDenominator)
            assertEquals(0, assembly.requestedSupportFrames)
            assertEquals(0L, assembly.chunks.first().output.start)
            assertEquals(assembly.totalFrames, assembly.chunks.last().output.endExclusive)
            assertEquals(assembly.totalFrames, assembly.chunks.sumOf { it.output.size })
            assembly.chunks.forEach { chunk ->
                assertEquals(chunk.output, chunk.render)
                assertEquals(0, chunk.supportBefore + chunk.supportAfter)
                assertTrue(chunk.render.size <= MAX_RENDER_FRAMES)
            }
            assertTrue(assembly.chunks.zipWithNext().all { (a, b) -> a.output.endExclusive == b.output.start })
        }
        assertEquals(5400L, proposed(input.copy(durationSeconds = 180)).totalFrames)
        assertEquals(7200L, proposed(input).totalFrames)
        assertEquals(9000L, proposed(input.copy(durationSeconds = 300)).totalFrames)
    }

    @Test
    fun `support counts are clipped at scene edges and included in the invocation ceiling not delivered`() {
        val assembly = proposed(input.copy(durationSeconds = 181, supportFramesPerSide = 8))
        assertEquals(284L, assembly.chunks.first().output.size)
        val first = assembly.chunks.first()
        val second = assembly.chunks[1]
        val last = assembly.chunks.last()
        assertEquals(0, first.supportBefore)
        assertEquals(8, first.supportAfter)
        assertEquals(8, second.supportBefore)
        assertEquals(8, second.supportAfter)
        assertEquals(0, last.supportAfter)
        assertEquals(assembly.totalFrames, assembly.chunks.sumOf { it.output.size })
        assertTrue(assembly.chunks.all { it.render.size <= 300 && it.render.start >= 0 &&
            it.render.endExclusive <= assembly.totalFrames })
        assertEquals(first.output.endExclusive, second.output.start)
        assertEquals(first.output.endExclusive + 8, first.render.endExclusive)
        assertEquals(second.output.start - 8, second.render.start)
    }

    @Test
    fun `same input replays with exact authored text and versioned pins`() {
        val a = proposed(input)
        assertEquals(a, proposed(input))
        assertEquals(input.id, a.id)
        assertEquals(input.projectId, a.projectId)
        assertEquals(input.preparedSceneId, a.preparedSceneId)
        assertEquals(input.preparedSceneArtifact, a.preparedSceneArtifact)
        assertEquals(input.finishedReferenceArtifact, a.finishedReferenceArtifact)
        assertEquals(input.primaryText, a.primaryText)
        assertEquals(input.actionText, a.actionText)
        assertEquals(input.cameraText, a.cameraText)
        assertEquals(input.motionText, a.motionText)
        assertEquals(input.styleText, a.styleText)
        assertEquals(input.guidelineTexts, a.guidelineTexts)
        assertEquals(73, a.seed)
        assertEquals(1, a.schemaVersion)
        assertEquals(1, a.plannerVersion)
        assertEquals(a, Json.decodeFromString<app.melotrail.video.domain.VideoAssembly>(Json.encodeToString(a)))
    }

    @Test
    fun `unsupported duration overflow unsafe integer and invalid chunk settings reject before IO`() {
        listOf(
            input.copy(durationSeconds = 179) to VideoAssemblyFindingCode.DURATION,
            input.copy(durationSeconds = 301) to VideoAssemblyFindingCode.DURATION,
            input.copy(durationSeconds = Long.MAX_VALUE) to VideoAssemblyFindingCode.OVERFLOW,
            input.copy(durationSeconds = MAX_SAFE_FRAME_INTEGER / 30 + 1) to VideoAssemblyFindingCode.UNSAFE_INTEGER,
            input.copy(seed = MAX_SAFE_FRAME_INTEGER + 1) to VideoAssemblyFindingCode.SEED,
            input.copy(seed = -1) to VideoAssemblyFindingCode.SEED,
            input.copy(supportFramesPerSide = -1) to VideoAssemblyFindingCode.SUPPORT,
            input.copy(supportFramesPerSide = 150) to VideoAssemblyFindingCode.SUPPORT,
            input.copy(maximumOutputFramesPerChunk = 301) to VideoAssemblyFindingCode.CHUNK_SIZE,
            input.copy(maximumOutputFramesPerChunk = 0) to VideoAssemblyFindingCode.CHUNK_SIZE,
            input.copy(projectId = "../escape") to VideoAssemblyFindingCode.MALFORMED_PLAN,
            input.copy(guidelineTexts = listOf("\u0000")) to VideoAssemblyFindingCode.MALFORMED_PLAN,
        ).forEach { (request, code) ->
            val finding = assertIs<VideoAssemblyPlanResult.Blocked>(planner.plan(request)).findings.single()
            assertEquals(code, finding.code)
            assertTrue(finding.explanation.isNotBlank() && finding.remedy.isNotBlank())
        }
    }

    @Test
    fun `validated assembly snapshots caller lists and cannot be mutated through exposed lists`() {
        val baseline = proposed(input)
        val chunks = baseline.chunks.toMutableList()
        val guidelines = mutableListOf("  Retain the linework.\n")
        val assembly = baseline.copy(chunks = chunks, guidelineTexts = guidelines)
        val originalJson = Json.encodeToString(assembly)

        // Removing an output chunk after validation previously produced a serializable hole.
        chunks.removeAt(chunks.lastIndex)
        guidelines[0] = "Different advice"
        assertEquals(baseline, assembly)
        assertEquals(originalJson, Json.encodeToString(assembly))
        assertEquals(baseline.totalFrames, assembly.chunks.last().output.endExclusive)
        val reopened = Json.decodeFromString<app.melotrail.video.domain.VideoAssembly>(originalJson)
        assertEquals(baseline, reopened)
        assertFailsWith<UnsupportedOperationException> {
            (reopened.chunks as MutableList).clear()
        }
        assertEquals(originalJson, Json.encodeToString(reopened))
        assertFailsWith<UnsupportedOperationException> {
            (assembly.chunks as MutableList).removeAt(assembly.chunks.lastIndex)
        }
        assertFailsWith<UnsupportedOperationException> {
            (assembly.guidelineTexts as MutableList)[0] = "Changed"
        }
    }

    @Test
    fun `forged partitions overlaps holes support and delivery settings fail closed`() {
        val valid = proposed(input.copy(supportFramesPerSide = 8, maximumOutputFramesPerChunk = 284))
        val first = valid.chunks.first()
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.drop(1))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.dropLast(1))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = listOf(first, first) + valid.chunks.drop(2))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.toMutableList().also {
                it[1] = it[1].copy(output = VideoAssemblyFrameRange(285, it[1].output.endExclusive))
            })
        }
        assertFailsWith<IllegalArgumentException> { valid.copy(width = 1280) }
        assertFailsWith<IllegalArgumentException> { valid.copy(frameZero = 1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(schemaVersion = 2) }
        assertFailsWith<IllegalArgumentException> { valid.copy(seed = MAX_SAFE_FRAME_INTEGER + 1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(requestedSupportFrames = 0) }
        assertFailsWith<IllegalArgumentException> {
            VideoAssemblyChunk(VideoAssemblyFrameRange(0, 300), VideoAssemblyFrameRange(0, 301), 0, 1)
        }
        assertFailsWith<IllegalArgumentException> { VideoAssemblyFrameRange(0, MAX_SAFE_FRAME_INTEGER + 1) }
    }

    @Test
    fun `scheduled supported actions bind to prepared components and replay with verbatim text`() {
        val prepared = preparedMotion()
        val scheduled = listOf(
            action("blink-1", VideoSceneMotionIntent.BLINK, "blink", 0, 15, 0.7),
            action("blink-2", VideoSceneMotionIntent.BLINK, "blink", 15, 30, 0.5),
            action("breath", VideoSceneMotionIntent.BREATHING, "breath", 0, 300, 2.0),
            action("gesture", VideoSceneMotionIntent.HEAD_GESTURE, "gesture", 300, 600, 1.5),
            action("steam", VideoSceneMotionIntent.STEAM, "steam", 0, 600, 3.0),
            action("travel", VideoSceneMotionIntent.SCENERY_TRAVEL, "travel", 0, 7200, 100.0),
        )
        val request = input.copy(preparedMotion = prepared, scheduledActions = scheduled)
        val assembly = proposed(request)
        assertEquals(assembly, proposed(request.copy(scheduledActions = scheduled.reversed())))
        assertEquals(assembly, Json.decodeFromString<app.melotrail.video.domain.VideoAssembly>(Json.encodeToString(assembly)))
        assertEquals(input.primaryText, assembly.primaryText)
        assertEquals(input.actionText, assembly.actionText)
        assertEquals(6, assembly.actions.size)
        assertEquals(VideoAssemblyActionKind.SCENERY_TRAVEL, assembly.actions.single { it.id == "travel" }.kind)
        assertEquals(7200L, assembly.actions.single { it.id == "travel" }.range.endExclusive)
        val blink = assembly.actions.single { it.id == "blink-1" }
        assertEquals(0.0, blink.activationAt(0))
        assertEquals(1.0, blink.activationAt(1))
        assertEquals(0.0, blink.activationAt(14))
        assertEquals(0.0, blink.activationAt(15))
        assertEquals(1.0, assembly.actions.single { it.id == "blink-2" }.activationAt(16))
        assertEquals(1.0, assembly.actions.single { it.id == "steam" }.activationAt(599))
        assertEquals(0.0, assembly.actions.single { it.id == "steam" }.activationAt(600))
        val mutable = assembly.actions.toMutableList()
        val snapshot = assembly.copy(actions = mutable)
        mutable.clear()
        assertEquals(assembly, snapshot)
        assertFailsWith<UnsupportedOperationException> { (snapshot.actions as MutableList).clear() }
        assertFailsWith<IllegalArgumentException> { assembly.copy(actions = assembly.actions + assembly.actions.first()) }
        assertFailsWith<IllegalArgumentException> { assembly.copy(actions = listOf(assembly.actions.first().copy(value = 10.0))) }
        assertFailsWith<IllegalArgumentException> { assembly.copy(actions = listOf(assembly.actions.first().copy(channel = "camera"))) }
    }

    @Test
    fun `duplicate conflicting unsupported and rejected actions have scoped remedies`() {
        val prepared = preparedMotion()
        val first = action("blink-1", VideoSceneMotionIntent.BLINK, "blink", 20, 50, 0.8)
        fun findings(motion: VideoPreparedSceneMotion? = prepared, vararg actions: VideoAssemblyScheduledAction) =
            assertIs<VideoAssemblyPlanResult.Blocked>(planner.plan(input.copy(preparedMotion = motion,
                scheduledActions = actions.toList()))).findings
        assertEquals(VideoAssemblyFindingCode.ACTION_ID, findings(prepared, first, first).first().code)
        val conflict = findings(prepared, first, first.copy(id = "blink-2", startFrame = 40))
        assertEquals(VideoAssemblyFindingCode.ACTION_CONFLICT, conflict.single().code)
        assertEquals("blink", conflict.single().componentId)
        assertEquals(40L, conflict.single().startFrame)
        assertEquals(VideoAssemblyFindingCode.ACTION_UNSUPPORTED, findings(prepared,
            action("drink", VideoSceneMotionIntent.CHARACTER_ACTION, "blink", 20, 50, 1.0)).single().code)
        assertTrue(findings(null, first).single().remedy.contains("Prepare"))
        assertEquals(VideoAssemblyFindingCode.ACTION_RANGE, findings(prepared, first.copy(endFrameExclusive = 7201)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared,
            first.copy(value = 2.0)).single().code)
        // The prepared compositor cannot compile asymmetric breathing or a nonzero rest value.
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            controls = prepared.controls.map { if (it.id == "breath") it.copy(requestedMinimum = -1.0) else it }),
            action("breath", VideoSceneMotionIntent.BREATHING, "breath", 0, 30, 1.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            controls = prepared.controls.map { if (it.id == "gesture") it.copy(requestedDefaultValue = 1.0) else it }),
            action("gesture", VideoSceneMotionIntent.HEAD_GESTURE, "gesture", 0, 30, 1.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_RANGE, findings(prepared,
            first.copy(endFrameExclusive = 21)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = prepared.scene.copy(
            poses = emptyList(), motionCapabilities = prepared.scene.motionCapabilities.filterNot { it.id == "blink-cap" })), first).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            mode = VideoSceneMotionMode.FLAT_IMAGE_TO_VIDEO), first).single().code)
        // Matching numeric bounds do not turn degrees into a pose-blend ratio.
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            controls = prepared.controls.map { if (it.id == "blink") it.copy(capability =
                it.capability.copy(unit = VideoMotionUnit.DEGREES)) else it }), first).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = prepared.scene.copy(
            poses = prepared.scene.poses.map { it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) })), first).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = prepared.scene.copy(
            layers = prepared.scene.layers.filterNot { it.id == "clean" })), first).single().code)
        // A full-canvas clean plate in another space cannot be composed behind this subject.
        val otherSpace = VideoCoordinateSpace("other", 1920, 1080)
        val mismatchedScene = prepared.scene.copy(coordinateSpaces = prepared.scene.coordinateSpaces + otherSpace,
            layers = prepared.scene.layers.map { if (it.id == "clean") it.copy(bounds = it.bounds.copy(coordinateSpaceId = "other")) else it })
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = mismatchedScene), first).single().code)
        val foreignSubject = prepared.scene.copy(coordinateSpaces = prepared.scene.coordinateSpaces + otherSpace,
            layers = prepared.scene.layers.map { if (it.id == "subject") it.copy(bounds = it.bounds.copy(coordinateSpaceId = "other")) else it },
            poses = prepared.scene.poses.map { it.copy(bounds = it.bounds.copy(coordinateSpaceId = "other")) },
            masks = prepared.scene.masks.map { it.copy(bounds = it.bounds.copy(coordinateSpaceId = "other")) },
            effectAnchors = prepared.scene.effectAnchors.map { it.copy(
                position = VideoPlacedPoint("other", VideoPoint(15.0, 15.0))) })
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = foreignSubject), first).single().code)
        val foreignAnchor = prepared.scene.copy(coordinateSpaces = prepared.scene.coordinateSpaces + otherSpace,
            layers = prepared.scene.layers + prepared.scene.layers.single { it.id == "subject" }.copy(
                id = "other-subject", bounds = prepared.scene.layers.single { it.id == "subject" }.bounds.copy(coordinateSpaceId = "other")),
            effectAnchors = prepared.scene.effectAnchors.map { it.copy(layerId = "other-subject",
                position = VideoPlacedPoint("other", VideoPoint(15.0, 15.0))) })
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = foreignAnchor),
            action("steam", VideoSceneMotionIntent.STEAM, "steam", 0, 30, 2.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(scene = prepared.scene.copy(
            masks = prepared.scene.masks.map { it.copy(reviewStatus = VideoComponentReviewStatus.REJECTED) })),
            action("nod", VideoSceneMotionIntent.HEAD_GESTURE, "gesture", 0, 30, 2.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            componentReviews = listOf(app.melotrail.video.application.VideoSceneComponentReview(
                "steam-anchor", app.melotrail.video.application.VideoSceneComponentKind.EFFECT_ANCHOR,
                VideoComponentReviewStatus.REJECTED))),
            action("vapor", VideoSceneMotionIntent.STEAM, "steam", 0, 30, 2.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared,
            action("i2v", VideoSceneMotionIntent.SCENERY_TRAVEL, "travel", 0, 129, 100.0)).single().code)
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT, findings(prepared.copy(
            controls = prepared.controls.map { if (it.id == "travel") it.copy(capability =
                it.capability.copy(unit = VideoMotionUnit.DEGREES)) else it }),
            action("travel", VideoSceneMotionIntent.SCENERY_TRAVEL, "travel", 0, 7200, 100.0)).single().code)
        val blockedPrompt = prepared.copy(promptIssues = listOf(VideoPromptIssue(
            VideoPromptIssueCode.USER_GUIDANCE_UNSUPPORTED, "Action guidance not compiled", true)))
        assertEquals(VideoAssemblyFindingCode.PROMPT_COMPILATION, findings(blockedPrompt, first).single().code)
        assertEquals(first, first.copy()) // immutable schedule inputs
    }

    @Test
    fun `blink amount is a fraction of the prepared pose capability maximum`() {
        val original = preparedMotion()
        val reduced = original.scene.motionCapabilities.map { if (it.id == "blink-cap") it.copy(maximum = 0.5) else it }
        val motion = original.copy(scene = original.scene.copy(motionCapabilities = reduced),
            controls = original.controls.map { if (it.id == "blink") it.copy(capability = reduced.first(),
                requestedMaximum = 0.5, requestedDefaultValue = 0.4) else it })
        val blink = action("blink", VideoSceneMotionIntent.BLINK, "blink", 0, 30, 0.8)
        assertEquals(0.8, proposed(input.copy(preparedMotion = motion, scheduledActions = listOf(blink))).actions.single().value)
        val tooNarrow = motion.copy(controls = motion.controls.map { if (it.id == "blink") it.copy(requestedMaximum = 0.3,
            requestedDefaultValue = 0.2) else it })
        assertEquals(VideoAssemblyFindingCode.ACTION_COMPONENT,
            assertIs<VideoAssemblyPlanResult.Blocked>(planner.plan(input.copy(preparedMotion = tooNarrow,
                scheduledActions = listOf(blink)))).findings.single().code)
    }

    @Test
    fun `nonblocking prompt guidance stays visible without turning text into an action`() {
        val advisory = VideoPromptIssue(VideoPromptIssueCode.STANDARD_GUIDANCE_UNSUPPORTED,
            "Selected backend cannot consume this guidance", blocksInference = false)
        val request = input.copy(actionText = "  Please make the character jump.\r\n  ",
            preparedMotion = preparedMotion().copy(promptIssues = listOf(advisory)))
        val proposal = assertIs<VideoAssemblyPlanResult.Proposed>(planner.plan(request))
        assertEquals(request.actionText, proposal.assembly.actionText)
        assertTrue(proposal.assembly.actions.isEmpty())
        assertEquals(listOf(VideoAssemblyFindingCode.PROMPT_COMPILATION), proposal.findings.map { it.code })
        assertTrue(proposal.findings.single().explanation.contains(advisory.message))
        val blocking = advisory.copy(code = VideoPromptIssueCode.USER_GUIDANCE_UNSUPPORTED, blocksInference = true)
        val finding = assertIs<VideoAssemblyPlanResult.Blocked>(planner.plan(request.copy(
            preparedMotion = request.preparedMotion!!.copy(promptIssues = listOf(blocking))))).findings.single()
        assertEquals(VideoAssemblyFindingCode.PROMPT_COMPILATION, finding.code)
    }

    private fun action(id: String, intent: VideoSceneMotionIntent, control: String, start: Long, end: Long, value: Double) =
        VideoAssemblyScheduledAction(id, intent, control, start, end, value)

    private fun preparedMotion(): VideoPreparedSceneMotion {
        fun image(name: String, width: Int, height: Int, cutout: Boolean = false) = VideoAssetImage(
            VideoArtifact("images/$name.png", "a".repeat(64)), VideoImageFormat.PNG, "image/png",
            width, height, 120, cutout, cutout)
        val finished = image("finished", 1920, 1080)
        val look = VideoSceneLook(input.finishedReferenceId, VideoArtifact("look/look.json", "c".repeat(64)),
            finished.copy(artifact = input.finishedReferenceArtifact), VideoAssetIdentityReview.UNREVIEWED,
            VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN)
        val bounds = VideoRect("scene", 10.0, 10.0, 20.0, 30.0)
        val subject = VideoPreparedLayer("subject", VideoLayerKind.SUBJECT, image("subject", 20, 30, true), bounds,
            alpha = VideoMeasuredAlpha(500, 0, 100))
        val scene = VideoPreparedScene(id = input.preparedSceneId,
            source = VideoPreparedSceneSource(references = listOf(VideoPreparedReferencePin(look.id, look.sourceDescriptor, look.original))),
            coordinateSpaces = listOf(VideoCoordinateSpace("scene", 1920, 1080)),
            layers = listOf(VideoPreparedLayer("finished", VideoLayerKind.FINISHED_SCENE, finished,
                VideoRect("scene", 0.0, 0.0, 1920.0, 1080.0)), subject,
                VideoPreparedLayer("clean", VideoLayerKind.ENVIRONMENT, image("clean", 1920, 1080),
                    VideoRect("scene", 0.0, 0.0, 1920.0, 1080.0)),
                VideoPreparedLayer("landscape", VideoLayerKind.SCENERY, image("landscape", 1920, 1080),
                    VideoRect("scene", 0.0, 0.0, 1920.0, 1080.0))),
            poses = listOf(VideoPreparedPose("blink", "subject", image("pose", 20, 30, true), bounds,
                alpha = VideoMeasuredAlpha(500, 0, 100))),
            masks = listOf(VideoPreparedMask("head", image("head", 20, 30, true), bounds, listOf("subject"),
                VideoMaskPurpose.HEAD_REGION, alpha = VideoMeasuredAlpha(500, 0, 100))),
            effectAnchors = listOf(VideoEffectAnchor("steam-anchor", "subject",
                position = VideoPlacedPoint("scene", VideoPoint(15.0, 15.0)))),
            sceneryCoverage = listOf(VideoSceneryCoverage("coverage", "landscape",
                VideoRect("scene", 0.0, 0.0, 1920.0, 1080.0))),
            motionCapabilities = listOf(
                VideoMotionCapability("blink-cap", VideoMotionTargetType.POSE, "blink", VideoMotionControl.POSE_BLEND,
                    VideoMotionUnit.RATIO, 0.0, 1.0, 0.0),
                VideoMotionCapability("breath-cap", VideoMotionTargetType.LAYER, "subject", VideoMotionControl.TRANSLATE_Y,
                    VideoMotionUnit.PIXELS, -4.0, 4.0, 0.0),
                VideoMotionCapability("gesture-cap", VideoMotionTargetType.LAYER, "subject", VideoMotionControl.ROTATE,
                    VideoMotionUnit.DEGREES, -3.0, 3.0, 0.0),
                VideoMotionCapability("steam-cap", VideoMotionTargetType.EFFECT_ANCHOR, "steam-anchor", VideoMotionControl.EFFECT_RATE,
                    VideoMotionUnit.PER_SECOND, 0.0, 8.0, 0.0),
                VideoMotionCapability("travel-cap", VideoMotionTargetType.SCENERY_COVERAGE, "coverage", VideoMotionControl.TRANSLATE_X,
                    VideoMotionUnit.PIXELS, 0.0, 200.0, 0.0),
            ), dependencies = listOf(VideoPreparedDependencyPin("test", "v1", "d".repeat(64))),
            createdAt = "2026-09-26T00:00:00Z")
        val inputs = listOf(
            Triple("blink", VideoSceneMotionIntent.BLINK, scene.motionCapabilities[0]),
            Triple("breath", VideoSceneMotionIntent.BREATHING, scene.motionCapabilities[1]),
            Triple("gesture", VideoSceneMotionIntent.HEAD_GESTURE, scene.motionCapabilities[2]),
            Triple("steam", VideoSceneMotionIntent.STEAM, scene.motionCapabilities[3]),
            Triple("travel", VideoSceneMotionIntent.SCENERY_TRAVEL, scene.motionCapabilities[4]),
        ).map { (id, intent, capability) -> VideoPreparedMotionInput(id, intent, capability,
            capability.minimum, capability.maximum, when (intent) {
                VideoSceneMotionIntent.BLINK -> 0.7
                VideoSceneMotionIntent.STEAM -> 3.0
                VideoSceneMotionIntent.SCENERY_TRAVEL -> 100.0
                else -> 0.0
            }, emptyList(), "e".repeat(64)) }
        return VideoPreparedSceneMotion(VideoScenePreparationStatus.REVIEW_REQUIRED,
            VideoSceneMotionMode.CONTROLLED_REGIONAL_MOTION, look, scene, input.primaryText, input.primaryText,
            emptyList(), inputs, emptyList(), emptyList(), "f".repeat(64))
    }

    private fun proposed(request: VideoAssemblyPlanningRequest) =
        assertIs<VideoAssemblyPlanResult.Proposed>(planner.plan(request)).assembly
}
