package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreArrangementDraftValidationSummary
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
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
    fun `song map distinguishes not generated attention accepted stale and draft role states`() {
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
        assertEquals(MidiCoreSongMapRoleState.NOT_GENERATED, midiCoreSongMap(project).first().roleStates.getValue(CandidateRole.BASS))
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
        onNodeWithTag(MidiCoreArrangePageTags.RETRY_DRAFT).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(retry), intents)
    }

    @Test
    fun `section repair and role controls retain the selected global style behind disclosure`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(styleId = "late-night"), intents::add, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.REGENERATE_SECTION).performScrollTo().assertIsEnabled().performClick()
        assertEquals(MidiCoreWorkspaceIntent.RegenerateArrangementSection("verse-1", "late-night", 1L), intents.single())
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
    fun `Arrange source contains no superseded dropdown first flow`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreArrangePage.kt")).lowercase()
        listOf(
            "numbered scope",
            "choose a section and role only",
            "generate next alternative",
            "listen and choose",
            "occurrence-menu",
        ).forEach { forbidden -> assertFalse(source.contains(forbidden), "Arrange page must not contain $forbidden") }
    }

    @Test
    fun `wide Arrange keeps its selected section inspector alongside factual contextual evidence`() =
        runSkikoComposeUiTest(size = Size(1280f, 900f)) {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        state = arrangeState(styleId = "late-night"),
                        initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                    )
                }
            }
            onNodeWithTag(MidiCoreArrangePageTags.INSPECTOR).assertExists()
            onNodeWithContentDescription("Arrange contextual inspector").assertExists()
            writeSongMapFixture("wide-song-map.png", onRoot().captureToImage().toAwtImage())
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
            onNodeWithTag(MidiCoreArrangePageTags.ADVANCED).performScrollTo().assertExists()
            onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertExists()
            writeSongMapFixture("compact-song-map-scrolled.png", onRoot().captureToImage().toAwtImage())
        }

    private fun arrangeState(
        styleId: String? = null,
        selectedOccurrenceId: String? = "verse-1",
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
    ): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000), meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0L, 1920L),
                ProjectSectionOccurrence("verse-2", "verse", "Verse", 1920L, 3840L),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1920L),
                AuthoritativeChordEvent("chord-2", "verse-2", "G", 1920L, 3840L),
            ),
        )
        val candidate = MidiCoreCandidate(
            id = "candidate-existing", role = CandidateRole.CHORDS, occurrenceId = "verse-1", generatorVersion = "midi-core-v1",
            authorityHash = "a".repeat(64), seed = 5L,
            midi = ProjectArtifact(ProjectRelativePath("candidates/chords/verse-1/candidate-existing.mid"), "b".repeat(64)),
            validationReport = ProjectArtifact(ProjectRelativePath("reports/candidates/candidate-existing.json"), "c".repeat(64)),
            createdAt = "2026-08-28T00:00:00Z", profileId = "chords.sustained", patternId = "chords.rhythm.sustained",
            status = MidiCoreCandidateStatus.REJECTED, rejectionReason = "Try another rhythm.",
        )
        val report = MidiCoreRoleValidationReport(
            contextSha256 = "d".repeat(64), candidateSha256 = "e".repeat(64), role = CandidateRole.CHORDS, occurrenceId = "verse-1", noteCount = 4,
            findings = emptyList(),
        )
        return MidiCoreWorkspaceState(
            project = MidiCoreProject(
                id = ProjectId("arrange-project"), metadata = ProjectMetadata("Arrange", "2026-08-28T00:00:00Z"),
                sourceMidi = SourceMidiRecord("source.mid", "f".repeat(64), 1, 480, ProjectArtifact(ProjectRelativePath("source/original.mid"), "f".repeat(64)), ProjectArtifact(ProjectRelativePath("reports/import.json"), "0".repeat(64)), listOf(MidiTrackSummary(0, "Lead", emptyList(), 3840L)), 3840L),
                selectedMelody = SelectedMelodyTrack(0, 0, "1".repeat(64)), authority = authority, candidates = listOf(candidate), revision = 3L,
            ),
            stylePreview = MidiCoreArrangementStyleUiState(selectedStyleId = styleId, occurrenceId = selectedOccurrenceId),
            arrangement = MidiCoreArrangementUiState(selectedOccurrenceId = selectedOccurrenceId),
            review = MidiCoreCandidateReviewUiState(role = CandidateRole.CHORDS, occurrenceId = "verse-1", candidates = listOf(MidiCoreCandidateReviewItem(candidate, report, emptyList(), authorityCurrent = true, accepted = false, locked = false))),
            operation = operation,
        )
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(Path.of(relativePath), Path.of("desktopApp").resolve(relativePath)).first { Files.isRegularFile(it) }

    private fun writeSongMapFixture(name: String, image: BufferedImage) {
        assertEquals(if (name.startsWith("wide")) 1280 else 720, image.width)
        assertEquals(900, image.height)
        val target = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            .resolve("build/test-results/midi-core-arrange-song-map").resolve(name)
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(image, "png", target.toFile()))
    }
}
