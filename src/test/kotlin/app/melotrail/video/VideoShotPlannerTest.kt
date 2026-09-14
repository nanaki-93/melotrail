package app.melotrail.video

import app.melotrail.video.application.VideoEstimateStatus
import app.melotrail.video.application.VideoLocalGenerationMeasurement
import app.melotrail.video.application.VideoPlaybackDirection
import app.melotrail.video.application.VideoPromptCompiler
import app.melotrail.video.application.VideoRepeatReview
import app.melotrail.video.application.VideoShotPlanProblemCode
import app.melotrail.video.application.VideoShotPlanResult
import app.melotrail.video.application.VideoShotPlanner
import app.melotrail.video.application.VideoShotProposal
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoBrief
import app.melotrail.video.domain.VideoBriefGuidance
import app.melotrail.video.domain.VideoBriefReference
import app.melotrail.video.domain.VideoDependencyPin
import app.melotrail.video.domain.VideoFootageMode
import app.melotrail.video.domain.VideoGuidanceKind
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoShotOverride
import app.melotrail.video.domain.VideoVersionedId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VideoShotPlannerTest {
    private val compiler = VideoPromptCompiler()
    private val planner = VideoShotPlanner()
    private val guidelineSet = compiler.decodeGuidelines(
        checkNotNull(javaClass.getResource("/video/video-generation-guidelines.json")).readBytes(),
    )

    @Test
    fun `duration boundaries produce exact unique footage and default four-minute count`() {
        assertFailsWith<IllegalArgumentException> { brief(duration = 179) }
        assertFailsWith<IllegalArgumentException> { brief(duration = 301) }

        val minimum = plan(brief(duration = 180))
        val default = plan(brief())
        val maximum = plan(brief(duration = 300))

        assertEquals(30, minimum.requests.size)
        assertEquals(40, default.requests.size)
        assertEquals(50, maximum.requests.size)
        listOf(minimum, default, maximum).forEach { proposal ->
            assertEquals(proposal.durationSeconds, proposal.uniqueFootageSeconds)
            assertEquals(0, proposal.reusedFootageSeconds)
            assertEquals(proposal.durationSeconds, proposal.placements.sumOf { it.durationSeconds })
            assertTrue(proposal.placements.all { it.repeatReview == VideoRepeatReview.NOT_A_REPEAT })
            assertTrue(proposal.placements.all { it.direction == VideoPlaybackDirection.FORWARD })
        }
    }

    @Test
    fun `per-shot text is an exact refinement of the retained primary prompt and duration stays exact`() {
        val primary = "An ink drawing changes slowly.\nKeep the composition sparse."
        val overrideText = "  Hold on the left edge.\nAdd one small ripple.  "
        val brief = brief(
            prompt = primary,
            overrides = listOf(VideoShotOverride(1, prompt = overrideText, durationSeconds = 8)),
        )
        val proposal = plan(brief)
        val first = proposal.requests.first()

        assertEquals(primary, first.primaryPrompt)
        assertTrue(first.backendPrompt.startsWith(primary))
        assertTrue(first.backendPrompt.contains("Shot override:\n$overrideText"))
        assertEquals(8, first.durationSeconds)
        assertEquals(240, proposal.requests.sumOf { it.durationSeconds })
        assertTrue(proposal.requests.drop(1).all { !it.backendPrompt.contains(overrideText) })
    }

    @Test
    fun `explicit reuse has bounded distinct takes exact disclosure and review-required forward repeats`() {
        val minimum = plan(brief(duration = 180), VideoFootageMode.EXPLICIT_REUSE)
        val default = plan(brief(), VideoFootageMode.EXPLICIT_REUSE)
        val maximum = plan(brief(duration = 300), VideoFootageMode.EXPLICIT_REUSE)

        assertEquals(12, minimum.requests.size)
        assertEquals(15, default.requests.size)
        assertEquals(18, maximum.requests.size)
        assertEquals(96, minimum.uniqueFootageSeconds)
        assertEquals(84, minimum.reusedFootageSeconds)
        assertEquals(120, default.uniqueFootageSeconds)
        assertEquals(120, default.reusedFootageSeconds)
        assertEquals(144, maximum.uniqueFootageSeconds)
        assertEquals(156, maximum.reusedFootageSeconds)
        listOf(minimum, default, maximum).forEach { proposal ->
            assertTrue(proposal.repeatReviewRequired)
            assertEquals(proposal.durationSeconds, proposal.uniqueFootageSeconds + proposal.reusedFootageSeconds)
            assertEquals(proposal.durationSeconds, proposal.placements.sumOf { it.durationSeconds })
            assertTrue(proposal.placements.drop(proposal.requests.size).all {
                it.repeatReview == VideoRepeatReview.REVIEW_REQUIRED && it.direction == VideoPlaybackDirection.FORWARD
            })
        }
    }

    @Test
    fun `local estimate is unknown without evidence and scales only from supplied accepted footage`() {
        val uniqueUnknown = plan(brief())
        val reuseUnknown = plan(brief(), VideoFootageMode.EXPLICIT_REUSE)
        assertEquals(VideoEstimateStatus.UNKNOWN, uniqueUnknown.localEstimate.status)
        assertNull(uniqueUnknown.localEstimate.estimatedWallClockMillis)
        assertEquals(VideoEstimateStatus.UNKNOWN, reuseUnknown.localEstimate.status)

        val measurement = VideoLocalGenerationMeasurement(
            evidence = VideoDependencyPin("owned-host-measurement", "9".repeat(64)),
            wallClockMillis = 60_000,
            attemptedGeneratedSeconds = 20,
            acceptedUsefulSeconds = 10,
            profile = capabilities(),
        )
        val measuredUnique = plan(brief(), measurement = measurement)
        val measuredReuse = plan(brief(), VideoFootageMode.EXPLICIT_REUSE, measurement)
        assertEquals(VideoEstimateStatus.MEASURED, measuredUnique.localEstimate.status)
        assertEquals(1_440_000L, measuredUnique.localEstimate.estimatedWallClockMillis)
        assertEquals(720_000L, measuredReuse.localEstimate.estimatedWallClockMillis)
        assertTrue(measuredReuse.dependencies.any { it.key == "estimate.measurement.owned-host-measurement" })
        assertNotEquals(measuredUnique.proposalFingerprint, measuredReuse.proposalFingerprint)
    }

    @Test
    fun `backend clip-duration bounds block unsupported generic and reuse choices visibly`() {
        val fiveSecondOnly = capabilities(minimumClipSeconds = 5, maximumClipSeconds = 5)
        val unique = planner.plan(
            brief(), fiveSecondOnly, guidelineSet, VideoFootageMode.UNIQUE,
        )
        val reuse = planner.plan(
            brief(), fiveSecondOnly, guidelineSet, VideoFootageMode.EXPLICIT_REUSE,
        )

        assertEquals(
            VideoShotPlanProblemCode.BACKEND_CLIP_DURATION_UNSUPPORTED,
            assertIs<VideoShotPlanResult.Blocked>(unique).problems.single().code,
        )
        assertEquals(
            VideoShotPlanProblemCode.BACKEND_CLIP_DURATION_UNSUPPORTED,
            assertIs<VideoShotPlanResult.Blocked>(reuse).problems.single().code,
        )
    }

    @Test
    fun `prompt-only shot change invalidates its dependent pending request and retains earlier takes`() {
        val originalBrief = brief()
        val changedBrief = brief(overrides = listOf(VideoShotOverride(2, prompt = "A user-authored close detail.")))
        val original = plan(originalBrief)
        val changed = plan(changedBrief)
        val completed = listOf(VideoVersionedId("take-previous", 1))

        val invalidation = planner.invalidatePending(
            previous = original,
            current = changed,
            pendingRequestIds = original.requests.mapTo(linkedSetOf()) { it.id },
            completedTakeIds = completed,
        )

        assertEquals(listOf("shot-002"), invalidation.invalidatedPendingRequestIds)
        assertEquals(39, invalidation.unaffectedPendingRequestIds.size)
        assertEquals(completed, invalidation.retainedCompletedTakeIds)
        assertNotEquals(
            original.requests.single { it.id == "shot-002" }.requestFingerprint,
            changed.requests.single { it.id == "shot-002" }.requestFingerprint,
        )
        assertEquals(
            original.requests.single { it.id == "shot-001" }.requestFingerprint,
            changed.requests.single { it.id == "shot-001" }.requestFingerprint,
        )
    }

    @Test
    fun `changed prompt asset model or workflow invalidates every dependent pending request`() {
        val baseBrief = brief()
        val base = plan(baseBrief)
        val pending = base.requests.mapTo(linkedSetOf()) { it.id }
        val changedCases = listOf(
            brief(prompt = "A materially contrasting free-form request.") to capabilities(),
            brief(assetSha = "b".repeat(64)) to capabilities(),
            baseBrief to capabilities(modelSha = "4".repeat(64)),
            baseBrief to capabilities(workflowSha = "5".repeat(64)),
        )

        changedCases.forEach { (changedBrief, changedCapabilities) ->
            val changed = proposed(planner.plan(changedBrief, changedCapabilities, guidelineSet))
            val invalidation = planner.invalidatePending(base, changed, pending, emptyList())
            assertEquals(pending.sorted(), invalidation.invalidatedPendingRequestIds)
            assertTrue(invalidation.unaffectedPendingRequestIds.isEmpty())
        }
    }

    @Test
    fun `invalid or impossible shot overrides return bounded typed problems`() {
        val unknown = brief(overrides = listOf(VideoShotOverride(41, prompt = "Outside the proposal.")))
        val tooLong = brief(overrides = listOf(VideoShotOverride(1, durationSeconds = 11)))
        val impossible = brief(overrides = (1..40).map { VideoShotOverride(it, durationSeconds = 5) })

        assertEquals(
            VideoShotPlanProblemCode.UNKNOWN_SHOT_OVERRIDE,
            assertIs<VideoShotPlanResult.Blocked>(rawPlan(unknown)).problems.single().code,
        )
        assertEquals(
            VideoShotPlanProblemCode.SHOT_OVERRIDE_DURATION_OUT_OF_RANGE,
            assertIs<VideoShotPlanResult.Blocked>(rawPlan(tooLong)).problems.single().code,
        )
        assertEquals(
            VideoShotPlanProblemCode.SHOT_OVERRIDE_DURATION_CONFLICT,
            assertIs<VideoShotPlanResult.Blocked>(rawPlan(impossible)).problems.single().code,
        )
    }

    @Test
    fun `planner compiles current reference bytes roles and guidance instead of reusing a prior preview`() {
        val originalBrief = brief()
        val originalReference = originalBrief.selectedReferences.single()
        val preview = compiler.compile(originalBrief, capabilities(), guidelineSet)
        val changedReference = originalReference.copy(
            original = originalReference.original.copy(sha256 = "b".repeat(64)),
            role = VideoReferenceRole.STYLE,
        )
        val action = "  One small shape unfolds.\nThen it holds.  "
        val current = originalBrief.copy(
            selectedReferences = listOf(changedReference),
            guidance = VideoBriefGuidance(action = action),
        )
        val proposal = plan(current)

        proposal.requests.forEach { request ->
            assertEquals(current.prompt, request.primaryPrompt)
            assertTrue(request.backendPrompt.contains("Action:\n$action"))
            assertEquals(changedReference.original, request.bindings.single().artifact)
            assertEquals(VideoReferenceRole.STYLE, request.bindings.single().requestedRole)
            assertEquals("b".repeat(64), request.dependencies.single { it.key.endsWith(".asset") }.sha256)
        }
        assertEquals(originalReference.original, preview.bindings.single().artifact)
        assertTrue(!requireNotNull(preview.backendPrompt).contains(action))
    }

    @Test
    fun `current optional guidance template and profile changes replace every dependent request`() {
        val originalBrief = brief()
        val capabilitySet = capabilities()
        val original = plan(originalBrief)
        val pending = original.requests.mapTo(linkedSetOf()) { it.id }
        val changedGuidance = listOf(
            VideoBriefGuidance(action = "A folded shape opens."),
            VideoBriefGuidance(camera = "Move closer slowly."),
            VideoBriefGuidance(motion = "A gentle rotation."),
            VideoBriefGuidance(style = "Layered paper."),
            VideoBriefGuidance(guidelines = listOf("Retain the uneven edges.")),
        )
        val changedGuidelines = compiler.decodeGuidelines(
            checkNotNull(javaClass.getResource("/video/video-generation-guidelines.json")).readText()
                .replace("generic-prompt-v1", "generic-prompt-fixture-v2")
                .replace("duration-shots-v1", "duration-shots-fixture-v2")
                .replace("Keep intended objects coherent", "Preserve intended object shapes"),
        )
        val cases = changedGuidance.map { Triple(originalBrief.copy(guidance = it), capabilitySet, guidelineSet) } +
            changedProfiles(capabilitySet).map { Triple(originalBrief, it, guidelineSet) } +
            listOf(Triple(originalBrief, capabilitySet, changedGuidelines))

        cases.forEach { (currentBrief, currentCapabilities, currentGuidelines) ->
            val current = proposed(planner.plan(currentBrief, currentCapabilities, currentGuidelines))
            val invalidation = planner.invalidatePending(original, current, pending, emptyList())
            assertEquals(pending.sorted(), invalidation.invalidatedPendingRequestIds)
            assertTrue(invalidation.unaffectedPendingRequestIds.isEmpty())
        }
        val changedTemplate = proposed(planner.plan(originalBrief, capabilitySet, changedGuidelines))
        assertTrue(changedTemplate.requests.all { it.backendPrompt.contains("Preserve intended object shapes") })
    }

    @Test
    fun `current unsupported reference role or guidance blocks planning after a ready preview`() {
        val originalBrief = brief()
        assertTrue(compiler.compile(originalBrief, capabilities(), guidelineSet).canPrepareRequests)
        val unsupportedRole = capabilities().copy(supportedReferenceRoles = setOf(VideoReferenceRole.STYLE))
        val unsupportedGuidance = capabilities().copy(supportedGuidance = setOf(VideoGuidanceKind.STANDARD_GUIDELINE))
        val cases = listOf(
            originalBrief to unsupportedRole,
            originalBrief.copy(guidance = VideoBriefGuidance(camera = "Move closer.")) to unsupportedGuidance,
            originalBrief to capabilities().copy(supportsPrimaryPrompt = false),
        )
        cases.forEach { (currentBrief, currentCapabilities) ->
            val blocked = assertIs<VideoShotPlanResult.Blocked>(
                planner.plan(currentBrief, currentCapabilities, guidelineSet),
            )
            assertEquals(VideoShotPlanProblemCode.PROMPT_COMPILATION_BLOCKED, blocked.problems.single().code)
        }
    }

    @Test
    fun `extending unique duration retains forty unchanged six-second requests and shrinking removes only the added shot`() {
        val original = plan(brief(duration = 240))
        val extended = plan(brief(duration = 246))
        val originalPending = original.requests.mapTo(linkedSetOf()) { it.id }
        val extendedPending = extended.requests.mapTo(linkedSetOf()) { it.id }
        val completed = listOf(VideoVersionedId("retained-take", 1))

        assertEquals(41, extended.requests.size)
        assertEquals(original.requests, extended.requests.take(40))
        assertNotEquals(original.proposalFingerprint, extended.proposalFingerprint)
        val extension = planner.invalidatePending(original, extended, originalPending, completed)
        assertTrue(extension.invalidatedPendingRequestIds.isEmpty())
        assertEquals(originalPending.sorted(), extension.unaffectedPendingRequestIds)
        assertEquals(completed, extension.retainedCompletedTakeIds)
        val shrink = planner.invalidatePending(extended, original, extendedPending, completed)
        assertEquals(listOf("shot-041"), shrink.invalidatedPendingRequestIds)
        assertEquals(originalPending.sorted(), shrink.unaffectedPendingRequestIds)
        assertEquals(completed, shrink.retainedCompletedTakeIds)
    }

    @Test
    fun `reuse duration changes proposal and placement identity without invalidating unchanged generation requests`() {
        val original = plan(brief(duration = 240), VideoFootageMode.EXPLICIT_REUSE)
        val extended = plan(brief(duration = 246), VideoFootageMode.EXPLICIT_REUSE)
        val pending = original.requests.mapTo(linkedSetOf()) { it.id }

        assertEquals(original.requests, extended.requests)
        assertEquals(120, extended.uniqueFootageSeconds)
        assertEquals(126, extended.reusedFootageSeconds)
        assertNotEquals(original.proposalFingerprint, extended.proposalFingerprint)
        assertNotEquals(
            original.dependencies.single { it.key == "planner.placements" },
            extended.dependencies.single { it.key == "planner.placements" },
        )
        assertTrue(extended.repeatReviewRequired)
        assertEquals(VideoRepeatReview.REVIEW_REQUIRED, extended.placements.last().repeatReview)
        assertTrue(planner.invalidatePending(original, extended, pending, emptyList()).invalidatedPendingRequestIds.isEmpty())
    }

    @Test
    fun `timing evidence without a profile or for changed backend model workflow settings or output stays unknown`() {
        val capabilitySet = capabilities()
        val measurement = VideoLocalGenerationMeasurement(
            evidence = VideoDependencyPin("owned-host-measurement", "9".repeat(64)),
            wallClockMillis = 60_000,
            attemptedGeneratedSeconds = 20,
            acceptedUsefulSeconds = 10,
            profile = capabilitySet,
        )
        val matched = plan(brief(), measurement = measurement)
        assertEquals(VideoEstimateStatus.MEASURED, matched.localEstimate.status)
        val unbound = plan(brief(), measurement = measurement.copy(profile = null))
        assertEquals(VideoEstimateStatus.UNKNOWN, unbound.localEstimate.status)
        assertNull(unbound.localEstimate.estimatedWallClockMillis)
        assertNull(unbound.localEstimate.measurementEvidence)
        assertNotEquals(matched.proposalFingerprint, unbound.proposalFingerprint)
        assertEquals(matched.requests, unbound.requests)

        changedProfiles(capabilitySet).forEach { currentProfile ->
            val changed = proposed(planner.plan(brief(), currentProfile, guidelineSet, measurement = measurement))
            assertEquals(VideoEstimateStatus.UNKNOWN, changed.localEstimate.status)
            assertNull(changed.localEstimate.estimatedWallClockMillis)
            assertNull(changed.localEstimate.measurementEvidence)
            val foreignEvidence = plan(brief(), measurement = measurement.copy(profile = currentProfile))
            assertEquals(VideoEstimateStatus.UNKNOWN, foreignEvidence.localEstimate.status)
            assertNotEquals(matched.proposalFingerprint, foreignEvidence.proposalFingerprint)
            assertEquals(matched.requests, foreignEvidence.requests)
        }
    }

    private fun changedProfiles(original: VideoPromptBackendCapabilities): List<VideoPromptBackendCapabilities> = listOf(
        original.copy(backendId = "other-synthetic-backend"),
        original.copy(capabilityVersion = "fixture-v2"),
        original.copy(maximumReferenceImages = 4),
        original.copy(supportsReferencesWithoutRole = false),
        original.copy(modelDependencies = listOf(VideoDependencyPin("model-fixture", "4".repeat(64)))),
        original.copy(workflowDependencies = listOf(VideoDependencyPin("workflow-fixture", "5".repeat(64)))),
        original.copy(settingsDependencies = listOf(VideoDependencyPin("settings-fixture", "7".repeat(64)))),
        original.copy(outputConfiguration = VideoDependencyPin("output-fixture", "8".repeat(64))),
    )

    private fun plan(
        brief: VideoBrief,
        mode: VideoFootageMode = VideoFootageMode.UNIQUE,
        measurement: VideoLocalGenerationMeasurement? = null,
    ): VideoShotProposal = proposed(rawPlan(brief, mode, measurement))

    private fun rawPlan(
        brief: VideoBrief,
        mode: VideoFootageMode = VideoFootageMode.UNIQUE,
        measurement: VideoLocalGenerationMeasurement? = null,
    ): VideoShotPlanResult {
        val capabilitySet = capabilities()
        return planner.plan(
            brief,
            capabilitySet,
            guidelineSet,
            mode,
            measurement,
        )
    }

    private fun proposed(result: VideoShotPlanResult): VideoShotProposal =
        assertIs<VideoShotPlanResult.Proposed>(result).proposal

    private fun brief(
        prompt: String = "Create an original abstract silent video from this reference.",
        duration: Int = 240,
        assetSha: String = "a".repeat(64),
        overrides: List<VideoShotOverride> = emptyList(),
    ): VideoBrief = VideoBrief(
        prompt = prompt,
        selectedReferences = listOf(
            VideoBriefReference(
                VideoVersionedId("reference", 1),
                VideoArtifact("references/reference.png", assetSha),
                VideoReferenceRole.SUBJECT,
            ),
        ),
        durationSeconds = duration,
        shotOverrides = overrides,
    )

    private fun capabilities(
        minimumClipSeconds: Int = 5,
        maximumClipSeconds: Int = 10,
        modelSha: String = "1".repeat(64),
        workflowSha: String = "2".repeat(64),
    ): VideoPromptBackendCapabilities = VideoPromptBackendCapabilities(
        backendId = "synthetic-capability-fixture",
        capabilityVersion = "fixture-v1",
        supportsPrimaryPrompt = true,
        maximumReferenceImages = 8,
        supportedReferenceRoles = VideoReferenceRole.entries.toSet(),
        supportsReferencesWithoutRole = true,
        supportedGuidance = VideoGuidanceKind.entries.toSet(),
        minimumClipDurationSeconds = minimumClipSeconds,
        maximumClipDurationSeconds = maximumClipSeconds,
        modelDependencies = listOf(VideoDependencyPin("model-fixture", modelSha)),
        workflowDependencies = listOf(VideoDependencyPin("workflow-fixture", workflowSha)),
        settingsDependencies = listOf(VideoDependencyPin("settings-fixture", "3".repeat(64))),
        outputConfiguration = VideoDependencyPin("output-fixture", "6".repeat(64)),
    )
}
