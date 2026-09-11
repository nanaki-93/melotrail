package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.audition.MidiAuditionWindow
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreArrangementDraftValidationSummary
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCorePlannedRest
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectMetadata
import java.nio.file.Files
import java.nio.file.Path
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreArrangePageTest {
    @Test
    fun `advanced role generation records current role engine and pattern identity`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(), intents::add, {}) } }
        onNodeWithContentDescription("Show advanced role adjustment").performScrollTo().performClick()
        onNodeWithTag(MidiCoreArrangePageTags.GENERATE).performScrollTo().assertIsEnabled().performClick()
        val chords = kotlin.test.assertIs<MidiCoreWorkspaceIntent.GenerateCandidate>(intents.single())
        assertEquals("midi-core-desktop", chords.generator.generatorId)
        assertEquals("midi-core-v5-patterns-v2-comping-v1", chords.generator.generatorVersion)
        assertEquals(chords.patternId, chords.generator.patternId)

        onNodeWithTag(MidiCoreArrangePageTags.role(CandidateRole.BASS)).performClick()
        intents.clear()
        onNodeWithTag(MidiCoreArrangePageTags.GENERATE).performScrollTo().performClick()
        val bass = kotlin.test.assertIs<MidiCoreWorkspaceIntent.GenerateCandidate>(intents.single())
        assertEquals("midi-core-v5-patterns-v2-bass-drums-v1", bass.generator.generatorVersion)

        onNodeWithTag(MidiCoreArrangePageTags.role(CandidateRole.DRUMS)).performClick()
        intents.clear()
        onNodeWithTag(MidiCoreArrangePageTags.GENERATE).performScrollTo().performClick()
        val drums = kotlin.test.assertIs<MidiCoreWorkspaceIntent.GenerateCandidate>(intents.single())
        assertEquals("midi-core-v5-patterns-v2-bass-drums-v1", drums.generator.generatorVersion)
    }

    @Test
    fun `Arrange renders one song map style gallery draft action and selected section inspector`() = runComposeUiTest {
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(), {}, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.occurrence("verse-1")).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.occurrence("verse-2")).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.STYLES).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.DRAFT).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.INSPECTOR).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).assertIsNotEnabled()
    }

    @Test
    fun `song map keeps duplicate labels distinct and updates the selected occurrence by intent`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val project = requireNotNull(arrangeState().project)
        assertEquals(listOf("Verse 1", "Verse 2"), midiCoreSongMap(project).map(MidiCoreSongMapOccurrence::displayLabel))
        setContent { MelotrailTheme { MidiCoreArrangePage(MidiCoreWorkspaceState(project = project, arrangement = MidiCoreArrangementUiState("verse-1")), intents::add, {}) } }

        onNodeWithTag(MidiCoreSongMapTags.occurrence("verse-1")).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.occurrence("verse-2")).performClick()

        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-2")), intents)
    }

    @Test
    fun `song map distinguishes not generated draft rest accepted rest attention accepted stale and draft role states`() {
        val project = requireNotNull(arrangeState().project)
        val rejected = project.candidates.single()
        val authorityHash = MidiCoreAuthorityHasher.from(project).sha256
        fun chordState(candidate: MidiCoreCandidate, accept: Boolean = false): MidiCoreSongMapRoleState = midiCoreSongMap(
            project.copy(
                candidates = listOf(candidate),
                acceptances = if (accept) listOf(CandidateAcceptance("verse-1", CandidateRole.CHORDS, candidate.id, false)) else emptyList(),
            ),
        ).first().roleStates.getValue(CandidateRole.CHORDS)
        val draftCandidates = requireNotNull(project.authority).occurrences.flatMap { occurrence ->
            CandidateRole.entries.map { role ->
                rejected.copy(
                    id = "draft-${occurrence.id}-${role.name.lowercase()}", role = role, occurrenceId = occurrence.id,
                    authorityHash = authorityHash, status = MidiCoreCandidateStatus.CURRENT, rejectionReason = null,
                )
            }
        }
        val draftProject = project.copy(
            candidates = draftCandidates,
            arrangementDrafts = listOf(
                MidiCoreArrangementDraft(
                    id = "map-draft", styleId = "late-night", styleVersion = 1, authorityHash = authorityHash, rootSeed = 1L,
                    candidateReferences = draftCandidates.map { candidate -> MidiCoreArrangementDraftCandidateReference(
                        occurrenceId = candidate.occurrenceId, role = candidate.role, candidateId = candidate.id,
                        midiSha256 = candidate.midi.sha256, validationReportSha256 = candidate.validationReport.sha256, authorityHash = authorityHash,
                    ) },
                    validation = MidiCoreArrangementDraftValidationSummary(draftCandidates.size, 4, true, "d".repeat(64)), createdAt = "2026-09-04T00:00:00Z",
                ),
            ),
        )

        assertEquals(MidiCoreSongMapRoleState.ATTENTION, chordState(rejected))
        assertEquals(MidiCoreSongMapRoleState.ACCEPTED, chordState(rejected.copy(status = MidiCoreCandidateStatus.ACCEPTED, rejectionReason = null), accept = true))
        assertEquals(MidiCoreSongMapRoleState.STALE, chordState(rejected.copy(status = MidiCoreCandidateStatus.STALE, rejectionReason = null)))
        assertEquals(MidiCoreSongMapRoleState.DRAFT, midiCoreSongMap(draftProject).first().roleStates.getValue(CandidateRole.CHORDS))
        assertEquals(
            MidiCoreSongMapRoleState.ACCEPTED_PLANNED_REST,
            midiCoreSongMap(project.copy(acceptedPlannedRests = listOf(MidiCorePlannedRest(
                "verse-1", CandidateRole.BASS, MidiCoreAuthorityHasher.from(project).scopeHash("verse-1", CandidateRole.BASS),
            )))).first()
                .roleStates.getValue(CandidateRole.BASS),
        )
        assertEquals(MidiCoreSongMapRoleState.NOT_GENERATED, midiCoreSongMap(project).first().roleStates.getValue(CandidateRole.BASS))

        val bassDraft = draftCandidates.single { it.occurrenceId == "verse-1" && it.role == CandidateRole.BASS }
        val draftRest = MidiCorePlannedRest(
            "verse-1", CandidateRole.BASS, MidiCoreAuthorityHasher.from(project).scopeHash("verse-1", CandidateRole.BASS),
        )
        val currentDraft = draftProject.arrangementDrafts.single()
        val projectWithDraftRestAndAcceptedBass = draftProject.copy(
            arrangementDrafts = listOf(currentDraft.copy(
                candidateReferences = currentDraft.candidateReferences.filterNot { it.occurrenceId == "verse-1" && it.role == CandidateRole.BASS },
                plannedRests = listOf(draftRest),
            )),
            acceptances = listOf(CandidateAcceptance("verse-1", CandidateRole.BASS, bassDraft.id, false)),
        )
        val projectWithDraftRest = projectWithDraftRestAndAcceptedBass.copy(acceptances = emptyList())
        assertEquals(
            MidiCoreSongMapRoleState.PLANNED_REST_IN_DRAFT,
            midiCoreSongMap(projectWithDraftRest).first().roleStates.getValue(CandidateRole.BASS),
        )
        assertEquals(
            MidiCoreSongMapRoleState.ACCEPTED,
            midiCoreSongMap(projectWithDraftRestAndAcceptedBass).first().roleStates.getValue(CandidateRole.BASS),
        )
    }

    @Test
    fun `selected section inspector reports factual planned rest and active draft progress`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    arrangeState(
                        operation = MidiCoreWorkspaceOperation(
                            id = 5L,
                            kind = MidiCoreWorkspaceOperationKind.DRAFT_GENERATION,
                            phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                            message = "Creating draft",
                            progress = MidiCoreWorkspaceOperationProgress(2, 6),
                        ),
                    ).copy(
                        project = requireNotNull(arrangeState().project).copy(
                            acceptedPlannedRests = listOf(MidiCorePlannedRest(
                                "verse-1", CandidateRole.BASS,
                                MidiCoreAuthorityHasher.from(requireNotNull(arrangeState().project)).scopeHash("verse-1", CandidateRole.BASS),
                            )),
                        ),
                    ),
                    {},
                    {},
                )
            }
        }

        onNodeWithText("Bass: Accepted planned rest").assertExists()
        onNodeWithText("Draft progress: 2 of 6 scopes complete.").assertExists()
    }

    @Test
    fun `selected-section inspector exposes previous and next song-map navigation`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(selectedOccurrenceId = "verse-1"), intents::add, {}) } }

        onNodeWithTag(MidiCoreSongMapTags.PREVIOUS).assertIsNotEnabled()
        onNodeWithTag(MidiCoreSongMapTags.NEXT).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-2")), intents)
    }

    @Test
    fun `style preview uses the selected song-map occurrence`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(), intents::add, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.style("late-night")).performScrollTo().assertIsEnabled().performClick()
        assertEquals(MidiCoreWorkspaceIntent.PreviewArrangementStyle("late-night", "verse-1"), intents.single())
    }

    @Test
    fun `persistent player exposes catalog style and distinct section names`() = runComposeUiTest {
        val state = arrangeState(styleId = "late-night").copy(
            audition = MidiAuditionState(
                scope = MidiAuditionScope.StylePreview("late-night", "verse-1"),
                window = MidiAuditionWindow(0L, 1920L),
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(state, initialDestination = MidiCoreWorkspaceDestination.ARRANGE)
            }
        }

        onNodeWithContentDescription("Current playback target: Late Night style preview · Verse 1").assertExists()
        onAllNodesWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertCountEquals(1)
    }

    @Test
    fun `one full draft action remains visible for the selected style`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    arrangeState(styleId = "late-night", selectedOccurrenceId = "verse-2"),
                    {},
                    {},
                )
            }
        }
        waitForIdle()
        onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).assertIsEnabled()
        onNodeWithContentDescription("Create full Late Night arrangement draft from Verse 2").assertExists()
    }

    @Test
    fun `selected style and full draft action remain reachable at all Arrange reference sizes`() {
        listOf(Size(1536f, 1024f), Size(1280f, 900f), Size(720f, 900f)).forEach { size ->
            runSkikoComposeUiTest(size = size) {
                setContent {
                    MelotrailTheme {
                        MidiCoreWorkspaceShell(
                            state = arrangeState(styleId = "late-night").copy(visualEvidence = arrangeVisualEvidence()),
                            initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                        )
                    }
                }
                onNodeWithTag(MidiCoreArrangePageTags.style("late-night")).assertIsSelected()
                onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).performScrollTo().assertIsEnabled()
                val draftAction = onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).getUnclippedBoundsInRoot()
                val player = onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).getUnclippedBoundsInRoot()
                assertTrue(
                    draftAction.top.value >= 0f && draftAction.bottom.value <= player.top.value,
                    "The selected style's full-draft action must be reachable above the persistent player at ${size.width.toInt()}×${size.height.toInt()}",
                )
            }
        }
    }

    @Test
    fun `selected style exposes an explicit session-only song-plan proposal action`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(styleId = "late-night"), intents::add, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.PLAN_PROPOSAL).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.PROPOSE_PLAN).performClick()

        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night")), intents)
    }

    @Test
    fun `draft progress supports cancellation and exact retry without hiding selected section`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val retry = MidiCoreWorkspaceIntent.CreateArrangementDraft("late-night", 1L, "draft-retry")
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    arrangeState(
                        styleId = "late-night",
                        operation = MidiCoreWorkspaceOperation(
                            id = 3L,
                            kind = MidiCoreWorkspaceOperationKind.DRAFT_GENERATION,
                            phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                            message = "Creating draft: verse-2 · bass (4/6)",
                            progress = MidiCoreWorkspaceOperationProgress(4, 6),
                            cancellableAtBoundary = true,
                        ),
                    ),
                    intents::add,
                    {},
                )
            }
        }
        onNodeWithTag(MidiCoreArrangePageTags.CANCEL).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.CancelOperation), intents)
        onNodeWithTag(MidiCoreSongMapTags.occurrence("verse-1")).assertExists()

        intents.clear()
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    arrangeState(
                        styleId = "late-night",
                        operation = MidiCoreWorkspaceOperation(
                            id = 4L,
                            kind = MidiCoreWorkspaceOperationKind.DRAFT_GENERATION,
                            phase = MidiCoreWorkspaceOperationPhase.FAILED,
                            message = "Retry the incomplete draft.",
                            retry = retry,
                            outcome = MidiCoreWorkspaceOperationOutcome.FAILURE,
                        ),
                    ),
                    intents::add,
                    {},
                )
            }
        }
        onNodeWithContentDescription("Retry complete draft generation").assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.RETRY_DRAFT).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(retry), intents)
    }

    @Test
    fun `section repair previews a bounded contextual action and retains role controls behind disclosure`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(styleId = "late-night"), intents::add, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.repair(app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO)).performScrollTo().assertIsEnabled().performClick()
        assertEquals(MidiCoreWorkspaceIntent.PreviewMusicalRepair("verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO), intents.single())
        intents.clear()
        onNodeWithTag(MidiCoreArrangePageTags.PROFILE_MENU).assertDoesNotExist()
        onNodeWithContentDescription("Show advanced role adjustment").performScrollTo().performClick()
        onNodeWithTag(MidiCoreArrangePageTags.role(CandidateRole.BASS)).performScrollTo().performClick()
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.SelectReviewScope(CandidateRole.BASS, "verse-1"),
                MidiCoreWorkspaceIntent.LoadCandidates(CandidateRole.BASS, "verse-1"),
            ),
            intents,
        )
        onNodeWithTag(MidiCoreArrangePageTags.PROFILE_MENU).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.PATTERN_MENU).assertExists()
    }

    @Test
    fun `failed applied repair labels saved plan and retries the exact repair`() = runComposeUiTest {
        val initial = arrangeState(styleId = "late-night")
        val project = requireNotNull(initial.project)
        val plan = app.melotrail.project.MidiCoreArrangementPlan(
            version = 1,
            sharedGroove = app.melotrail.project.MidiCoreSharedGrooveIntent(
                app.melotrail.project.MidiCoreGrooveFeel.STRAIGHT,
                app.melotrail.project.MidiCoreGrooveSubdivision.EIGHTH,
                app.melotrail.project.MidiCoreGrooveDrive.STEADY,
            ),
            occurrences = requireNotNull(project.authority).occurrences.map { occurrence ->
                app.melotrail.project.MidiCoreOccurrenceArrangementPlan(
                    occurrence.id, app.melotrail.project.MidiCoreArrangementPurpose.VERSE,
                    "phrase-${occurrence.id}", "repeat-${occurrence.id}", 1, 50,
                    CandidateRole.entries.map { role -> app.melotrail.project.MidiCoreRolePlanSettings(
                        role, app.melotrail.project.MidiCoreRoleActivity.SUPPORTING, 50,
                        app.melotrail.project.MidiCoreRegisterPreference.MID,
                    ) },
                    app.melotrail.project.MidiCoreBoundaryIntent.NONE, app.melotrail.project.MidiCoreBoundaryIntent.NONE,
                )
            },
        )
        val before = project.copy(arrangementPlan = plan)
        val proposal = app.melotrail.application.MidiCoreMusicalRepairPlanner.propose(
            plan, "verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
        )
        val after = before.copy(arrangementPlan = proposal.plan)
        val prepared = app.melotrail.application.MidiCoreMusicalRepairResult.Prepared(
            proposal,
            app.melotrail.arrangement.core.MidiCoreInvalidationPlanner.preview(
                MidiCoreAuthorityHasher.from(before), MidiCoreAuthorityHasher.from(after),
            ), emptyList(), emptyList(),
        )
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(
            initial.copy(project = after, musicalRepair = MidiCoreMusicalRepairUiState(
                prepared = prepared, applied = true, noResultReason = "No distinct alternatives.",
            )), intents::add, {},
        ) } }
        onNodeWithText("Repair plan saved · alternatives not accepted").performScrollTo().assertExists()
        onNodeWithText("Repair preview · not saved").assertDoesNotExist()
        onNodeWithTag(MidiCoreArrangePageTags.APPLY_REPAIR).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ApplyMusicalRepair), intents)
        onNodeWithTag(MidiCoreArrangePageTags.CANCEL_REPAIR).performClick()
        assertEquals(MidiCoreWorkspaceIntent.CancelMusicalRepair, intents.last())
    }

    @Test
    fun `Arrange blocks missing authority and retains no retired scope selector`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    MidiCoreWorkspaceState(
                        project = MidiCoreProject(ProjectId("arrange-blocked"), ProjectMetadata("Blocked", "2026-08-28T00:00:00Z")),
                        blockers = listOf(MidiCoreWorkspaceBlocker(MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED, "No authoritative chord windows are defined.", "Enter gap-free chord windows.")),
                    ),
                    {},
                    {},
                )
            }
        }
        onNodeWithTag(MidiCoreArrangePageTags.EMPTY).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.ROOT).assertDoesNotExist()
        onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).assertDoesNotExist()
    }

    @Test
    fun `Arrange source keeps compact rectangular actions and no superseded dropdown first flow`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreArrangePage.kt"))
        listOf(
            "numbered scope",
            "choose a section and role only",
            "generate next alternative",
            "listen and choose",
            "occurrence-menu",
        ).forEach { forbidden -> assertFalse(source.lowercase().contains(forbidden), "Arrange page must not contain $forbidden") }
        assertTrue(source.contains("contentPadding = PaddingValues(horizontal = MusicWorkspaceTokens.Spacing.Sm)"))
        assertTrue(Regex("shape = RoundedCornerShape\\(MusicWorkspaceTokens.Radius.Control\\)").findAll(source).count() >= 7)
    }

    @Test
    fun `wide Arrange keeps real four lanes and the full draft action above the persistent player`() =
        runSkikoComposeUiTest(size = Size(1280f, 900f)) {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        state = arrangeState(styleId = "late-night").copy(
                            audition = MidiAuditionState(
                                scope = MidiAuditionScope.Occurrence("verse-1"),
                                window = MidiAuditionWindow(0L, 1920L),
                            ),
                            visualEvidence = arrangeVisualEvidence(),
                        ),
                        initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                    )
                }
            }
            onNodeWithTag(MidiCoreArrangePageTags.INSPECTOR).assertExists()
            onNodeWithTag(MidiCoreWorkspaceShellTags.CONTEXT).assertDoesNotExist()
            MidiCoreArrangementStyleCatalog.styles.forEach { style ->
                onNodeWithTag(MidiCoreArrangePageTags.style(style.id)).assertExists()
            }
            val draftAction = onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).getUnclippedBoundsInRoot()
            val player = onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).getUnclippedBoundsInRoot()
            val laneBounds = MidiExportRole.entries.map { role ->
                onNodeWithTag(MidiCoreVerifiedTimelineTags.lane(role)).getUnclippedBoundsInRoot()
            }
            assertEquals(4, laneBounds.size)
            laneBounds.forEach { lane ->
                assertEquals(52f, (lane.bottom - lane.top).value, "Each role keeps its independently specified 52 dp lane")
                assertEquals(laneBounds.first().left, lane.left, "All roles share the same song origin")
                assertEquals(laneBounds.first().right, lane.right, "All roles share the same song extent")
                assertTrue(lane.top.value >= 0f && lane.bottom.value <= player.top.value,
                    "Every role must remain visible above the persistent player")
            }
            laneBounds.zipWithNext().forEach { (previous, next) ->
                assertEquals(previous.bottom, next.top, "No missing or shifted role lane")
            }
            MidiCoreArrangementStyleCatalog.styles.forEach { style ->
                val card = onNodeWithTag(MidiCoreArrangePageTags.style(style.id)).getUnclippedBoundsInRoot()
                assertTrue(card.top.value >= 0f && card.bottom.value <= player.top.value - 24f, "The full ${style.displayName} style card must remain visible above the persistent player")
            }
            assertTrue(draftAction.top.value >= 0f && draftAction.bottom.value <= player.top.value, "The full-draft action must remain visible above the persistent player")
            onNodeWithContentDescription("Current playback target: Current Verse 1 section").assertExists()
            writeSongMapFixture("wide-song-map.png", onRoot().captureToImage().toAwtImage())
        }

    @Test
    fun `reference wide Arrange moves its one selected-section inspector into the 332 dp shell column`() =
        runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        state = arrangeState(styleId = "late-night").copy(visualEvidence = arrangeVisualEvidence()),
                        initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                    )
                }
            }
            val inspector = onNodeWithTag(MidiCoreWorkspaceShellTags.PAGE_INSPECTOR).getUnclippedBoundsInRoot()
            assertEquals(332f, (inspector.right - inspector.left).value)
            onNodeWithTag(MidiCoreArrangePageTags.INSPECTOR).assertExists()
            onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).assertIsEnabled()
            val player = onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).getUnclippedBoundsInRoot()
            MidiCoreArrangementStyleCatalog.styles.forEach { style ->
                val card = onNodeWithTag(MidiCoreArrangePageTags.style(style.id)).getUnclippedBoundsInRoot()
                assertTrue(card.top.value >= 0f && card.bottom.value <= player.top.value - 24f, "The reference-wide ${style.displayName} style card must remain visible above the persistent player")
            }
            onNodeWithTag(MidiCoreWorkspaceShellTags.CONTEXT).assertDoesNotExist()
            writeSongMapFixture("reference-wide-song-map.png", onRoot().captureToImage().toAwtImage())
        }

    @Test
    fun `compact Arrange keeps the horizontally navigable song map visible after page scrolling`() =
        runSkikoComposeUiTest(size = Size(720f, 900f)) {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        state = arrangeState(styleId = "late-night"),
                        initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                    )
                }
            }
            onNodeWithTag(MidiCoreSongMapTags.TRACK).assertExists()
            onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).performScrollTo().assertIsEnabled()
            val draftAction = onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).getUnclippedBoundsInRoot()
            val player = onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).getUnclippedBoundsInRoot()
            assertTrue(draftAction.top.value >= 0f && draftAction.bottom.value <= player.top.value, "The compact full-draft action must be reachable above the persistent player")
            onNodeWithTag(MidiCoreArrangePageTags.ADVANCED).performScrollTo().assertExists()
            onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertExists()
            onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_CONTEXT).assertDoesNotExist()
            writeSongMapFixture("compact-song-map-scrolled.png", onRoot().captureToImage().toAwtImage())
        }

    private fun arrangeState(
        styleId: String? = null,
        selectedOccurrenceId: String? = "verse-1",
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
    ) = MidiCoreVisualFixture.state(styleId, selectedOccurrenceId, operation)

    private fun arrangeVisualEvidence() = MidiCoreVisualFixture.evidence()

    private fun sourceFile(relativePath: String): Path = sequenceOf(Path.of(relativePath), Path.of("desktopApp").resolve(relativePath)).first { Files.isRegularFile(it) }

    private fun writeSongMapFixture(name: String, image: BufferedImage) {
        assertEquals(
            when {
                name.startsWith("reference-wide") -> 1536
                name.startsWith("wide") -> 1280
                else -> 720
            },
            image.width,
        )
        assertEquals(if (name.startsWith("reference-wide")) 1024 else 900, image.height)
        val target = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            .resolve("build/test-results/midi-core-arrange-song-map").resolve(name)
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(image, "png", target.toFile()))
    }
}
