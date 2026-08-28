package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.arrangement.core.MidiCoreRoleFinding
import app.melotrail.arrangement.core.MidiCoreRoleFindingCode
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class MidiCoreArrangePageTest {
    @Test
    fun `Arrange destination is rendered through the focused workspace shell`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = arrangeState(),
                    initialDestination = MidiCoreWorkspaceDestination.ARRANGE,
                )
            }
        }

        onNodeWithTag(MidiCoreArrangePageTags.ROOT).assertExists()
    }

    @Test
    fun `Arrange exposes only curated profiles and patterns for every core role`() = runComposeUiTest {
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(), {}, {}) } }

        CandidateRole.entries.forEach { role ->
            onNodeWithTag(MidiCoreArrangePageTags.role(role)).performScrollTo().assertIsEnabled().performClick()
            waitForIdle()
            onNodeWithContentDescription("Select ${role.name.lowercase().replaceFirstChar(Char::uppercaseChar)} role. Selected.").assertExists()
            app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog.allowedProfileIds(role).forEach { profile ->
                onNodeWithTag(MidiCoreArrangePageTags.profile(profile)).assertExists()
            }
            app.melotrail.arrangement.core.MidiCorePatternCatalog.allowedPatternIds(role).forEach { pattern ->
                onNodeWithTag(MidiCoreArrangePageTags.pattern(pattern)).assertExists()
            }
        }
    }

    @Test
    fun `Arrange scopes deterministic alternatives and routes one exact scope to Review`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        var destination: MidiCoreWorkspaceDestination? = null
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(arrangeState(), intents::add, { destination = it })
            }
        }

        onNodeWithTag(MidiCoreArrangePageTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.candidate("candidate-existing")).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.role(CandidateRole.BASS)).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.SelectReviewScope(CandidateRole.BASS, "verse-1"),
                MidiCoreWorkspaceIntent.LoadCandidates(CandidateRole.BASS, "verse-1"),
            ),
            intents,
        )

        onNodeWithTag(MidiCoreArrangePageTags.profile("bass.muted-plucked")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreArrangePageTags.pattern("bass.root-fifth")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreArrangePageTags.SEED).performScrollTo().performTextReplacement("17")
        waitForIdle()
        onNodeWithTag(MidiCoreArrangePageTags.GENERATE).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        val generated = intents.filterIsInstance<MidiCoreWorkspaceIntent.GenerateCandidate>().single()
        assertEquals(CandidateRole.BASS, generated.role)
        assertEquals("verse-1", generated.occurrenceId)
        assertEquals("bass.muted-plucked", generated.performanceProfileId)
        assertEquals("bass.root-fifth", generated.patternId)
        assertEquals(17L, generated.generator.seed)

        onNodeWithTag(MidiCoreArrangePageTags.ALTERNATIVE).performScrollTo().performClick()
        waitForIdle()
        val alternative = intents.filterIsInstance<MidiCoreWorkspaceIntent.GenerateCandidate>().last()
        assertEquals(18L, alternative.generator.seed)
        assertEquals("verse-1", alternative.occurrenceId)

        onNodeWithTag(MidiCoreArrangePageTags.REGENERATE).performScrollTo().performClick()
        waitForIdle()
        val regenerated = intents.filterIsInstance<MidiCoreWorkspaceIntent.RegenerateCandidate>().single()
        assertEquals(CandidateRole.BASS, regenerated.role)
        assertEquals("verse-1", regenerated.occurrenceId)
        assertEquals(18L, regenerated.generator.seed)

        onNodeWithTag(MidiCoreArrangePageTags.REVIEW).performScrollTo().performClick()
        waitForIdle()
        assertEquals(MidiCoreWorkspaceDestination.REVIEW, destination)
        assertEquals(MidiCoreWorkspaceIntent.SelectReviewScope(CandidateRole.BASS, "verse-1"), intents.last())
    }

    @Test
    fun `Arrange shows validation and rejected candidate evidence without an approval control`() = runComposeUiTest {
        setContent { MelotrailTheme { MidiCoreArrangePage(arrangeState(), {}, {}) } }

        onNodeWithTag(MidiCoreArrangePageTags.CANDIDATES).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.candidate("candidate-existing")).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.REVIEW).assertIsEnabled()
    }

    @Test
    fun `Arrange exposes cancellable asynchronous generation progress`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    arrangeState(
                        operation = MidiCoreWorkspaceOperation(
                            id = 7L,
                            kind = MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION,
                            phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                            message = "Generating bass candidate…",
                            cancellableAtBoundary = true,
                        ),
                    ),
                    intents::add,
                    {},
                )
            }
        }

        onNodeWithTag(MidiCoreArrangePageTags.CANCEL).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.CancelOperation), intents)
    }

    @Test
    fun `Arrange explains incomplete authority and blocks generation`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreArrangePage(
                    MidiCoreWorkspaceState(
                        project = MidiCoreProject(ProjectId("arrange-blocked"), ProjectMetadata("Blocked", "2026-08-28T00:00:00Z")),
                        blockers = listOf(
                            MidiCoreWorkspaceBlocker(
                                MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED,
                                "No authoritative chord windows are defined.",
                                "Enter gap-free chord windows for every section occurrence.",
                            ),
                        ),
                    ),
                    {},
                    {},
                )
            }
        }

        onNodeWithTag(MidiCoreArrangePageTags.EMPTY).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.BLOCKERS).assertExists()
        onNodeWithTag(MidiCoreArrangePageTags.GENERATE).assertDoesNotExist()
    }

    @Test
    fun `Arrange source contains no superseded generation controls`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreArrangePage.kt")).lowercase()
        listOf("planner", "model selector", "instrument catalog", "render-stem", "build-song", "auto-approval").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Arrange page must not contain $forbidden")
        }
    }

    private fun arrangeState(operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle()): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse 1", 0L, 1920L),
                ProjectSectionOccurrence("verse-2", "verse", "Verse 2", 1920L, 3840L),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1920L),
                AuthoritativeChordEvent("chord-2", "verse-2", "G", 1920L, 3840L),
            ),
        )
        val candidate = MidiCoreCandidate(
            id = "candidate-existing",
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            generatorVersion = "midi-core-v1",
            authorityHash = "a".repeat(64),
            seed = 5L,
            midi = ProjectArtifact(ProjectRelativePath("candidates/chords/verse-1/candidate-existing.mid"), "b".repeat(64)),
            validationReport = ProjectArtifact(ProjectRelativePath("reports/candidates/candidate-existing.json"), "c".repeat(64)),
            createdAt = "2026-08-28T00:00:00Z",
            profileId = "chords.sustained",
            patternId = "chords.rhythm.sustained",
            status = MidiCoreCandidateStatus.REJECTED,
            rejectionReason = "Try another rhythm.",
        )
        val report = MidiCoreRoleValidationReport(
            contextSha256 = "d".repeat(64),
            candidateSha256 = "e".repeat(64),
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            noteCount = 4,
            findings = listOf(
                MidiCoreRoleFinding(
                    MidiCoreRoleFindingCode.DENSITY_EXCEEDED,
                    MidiCoreRoleFindingSeverity.ADVISORY,
                    CandidateRole.CHORDS,
                    "verse-1",
                    null,
                    null,
                    "Chord density is intentionally conservative.",
                ),
            ),
        )
        return MidiCoreWorkspaceState(
            project = MidiCoreProject(
                id = ProjectId("arrange-project"),
                metadata = ProjectMetadata("Arrange project", "2026-08-28T00:00:00Z"),
                sourceMidi = SourceMidiRecord(
                    "source.mid",
                    "f".repeat(64),
                    1,
                    480,
                    ProjectArtifact(ProjectRelativePath("source/original.mid"), "f".repeat(64)),
                    ProjectArtifact(ProjectRelativePath("reports/import.json"), "0".repeat(64)),
                    listOf(MidiTrackSummary(0, "Lead", emptyList(), 3840L)),
                    3840L,
                ),
                selectedMelody = SelectedMelodyTrack(0, 0, "1".repeat(64)),
                authority = authority,
                candidates = listOf(candidate),
                revision = 3L,
            ),
            projectRoot = Path.of("build/arrange-project"),
            review = MidiCoreCandidateReviewUiState(
                role = CandidateRole.CHORDS,
                occurrenceId = "verse-1",
                candidates = listOf(MidiCoreCandidateReviewItem(candidate, report, emptyList(), authorityCurrent = true, accepted = false, locked = false)),
            ),
            operation = operation,
        )
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}
