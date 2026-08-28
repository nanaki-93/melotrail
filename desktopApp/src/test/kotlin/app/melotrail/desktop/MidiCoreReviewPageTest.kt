package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.arrangement.core.MidiCoreRoleFinding
import app.melotrail.arrangement.core.MidiCoreRoleFindingCode
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionOutputDevice
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.audition.MidiAuditionWindow
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class MidiCoreReviewPageTest {
    @Test
    fun `Review destination is rendered through the focused workspace shell`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = reviewState(),
                    initialDestination = MidiCoreWorkspaceDestination.REVIEW,
                )
            }
        }

        onNodeWithTag(MidiCoreReviewPageTags.ROOT).assertExists()
    }

    @Test
    fun `Review exposes explicit scoped decisions, semantic comparison, and Arrange handoff`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        var destination: MidiCoreWorkspaceDestination? = null
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(), intents::add, { destination = it }) } }

        onNodeWithTag(MidiCoreReviewPageTags.role(CandidateRole.BASS)).performScrollTo().assertIsEnabled()
        onNodeWithTag(MidiCoreReviewPageTags.occurrence("verse-2")).performScrollTo().assertIsEnabled()

        onNodeWithTag(MidiCoreReviewPageTags.select("candidate-current")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.select("candidate-accepted")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.COMPARE).performScrollTo().assertIsEnabled().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.accept("candidate-current")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.reject("candidate-current")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.restore("candidate-current")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.lock("candidate-accepted")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.unlock("candidate-locked")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.ARRANGE).performScrollTo().performClick()
        waitForIdle()

        assertEquals(MidiCoreWorkspaceDestination.ARRANGE, destination)
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.CompareCandidates("candidate-accepted", "candidate-current"),
                MidiCoreWorkspaceIntent.AcceptCandidate("candidate-current"),
                MidiCoreWorkspaceIntent.RejectCandidate("candidate-current", "Not selected in Review."),
                MidiCoreWorkspaceIntent.RestoreCandidate("candidate-current", CandidateRole.CHORDS, "verse-1"),
                MidiCoreWorkspaceIntent.LockCandidate("candidate-accepted"),
                MidiCoreWorkspaceIntent.UnlockCandidate("candidate-locked"),
                MidiCoreWorkspaceIntent.SelectReviewScope(CandidateRole.CHORDS, "verse-1"),
            ),
            intents,
        )
    }

    @Test
    fun `Review provides candidate role occurrence full and transport MIDI controls`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(), intents::add, {}) } }

        onNodeWithTag(MidiCoreReviewPageTags.playCandidate("candidate-current")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.PLAY_ARRANGEMENT).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.playRole(MidiExportRole.BASS)).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.playOccurrence("verse-1")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.PAUSE).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.STOP).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.SEEK_START).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.LOOP).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.mute(MidiExportRole.DRUMS)).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.solo(MidiExportRole.BASS)).performScrollTo().performClick()
        waitForIdle()

        assertEquals(
            listOf<MidiCoreWorkspaceIntent>(
                MidiCoreWorkspaceIntent.PlayCandidate("candidate-current", CandidateRole.CHORDS, "verse-1"),
                MidiCoreWorkspaceIntent.PlayAcceptedArrangement,
                MidiCoreWorkspaceIntent.PlayAcceptedRole(CandidateRole.BASS),
                MidiCoreWorkspaceIntent.PlayAcceptedOccurrence("verse-1"),
                MidiCoreWorkspaceIntent.PauseAudition,
                MidiCoreWorkspaceIntent.StopAudition,
                MidiCoreWorkspaceIntent.SeekAudition(0L),
                MidiCoreWorkspaceIntent.SetAuditionLoop(app.melotrail.audition.MidiAuditionLoop(0L, 3840L)),
                MidiCoreWorkspaceIntent.MuteAuditionRole(MidiExportRole.DRUMS, true),
                MidiCoreWorkspaceIntent.SoloAuditionRole(MidiExportRole.BASS, true),
            ),
            intents,
        )
    }

    @Test
    fun `Review exposes discovered MIDI outputs and the system fallback`() = runComposeUiTest {
        val device = MidiAuditionOutputDevice("test-output", "Test MIDI output", "Test", "Test receiver", "1")
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val selectedState = reviewState().copy(audition = reviewState().audition.copy(outputDeviceId = device.id, outputDevices = listOf(device)))
        setContent { MelotrailTheme { MidiCoreReviewPage(selectedState, intents::add, {}) } }

        onNodeWithTag(MidiCoreReviewPageTags.OUTPUT_DEFAULT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.SelectAuditionOutputDevice(null)), intents)

        intents.clear()
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState().copy(audition = reviewState().audition.copy(outputDevices = listOf(device))), intents::add, {}) } }
        onNodeWithTag(MidiCoreReviewPageTags.output(device.id)).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.SelectAuditionOutputDevice(device.id)), intents)
    }

    @Test
    fun `Review explains stale evidence and never offers it for acceptance`() = runComposeUiTest {
        val stale = reviewState().review.candidates.first().copy(authorityCurrent = false)
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(candidates = listOf(stale)), {}, {}) } }

        onNodeWithContentDescription("Candidate candidate-current: current, 4 notes, 0 blocking findings, 1 advisory findings. Stale for current authority.").assertExists()
        onNodeWithTag(MidiCoreReviewPageTags.accept("candidate-current")).assertDoesNotExist()
    }

    @Test
    fun `Review source contains no superseded review or audio comparison control`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreReviewPage.kt")).lowercase()
        listOf("source-song approval", "cohesion boundary", "critic", "full-song enhancement", "humanization", "dry/lo-fi", "matched-audio").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Review page must not contain $forbidden")
        }
    }

    private fun reviewState(candidates: List<MidiCoreCandidateReviewItem> = reviewCandidates()): MidiCoreWorkspaceState {
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
        return MidiCoreWorkspaceState(
            project = MidiCoreProject(
                id = ProjectId("review-project"),
                metadata = ProjectMetadata("Review project", "2026-08-28T00:00:00Z"),
                sourceMidi = SourceMidiRecord(
                    "source.mid", "a".repeat(64), 1, 480,
                    ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
                    ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
                    listOf(MidiTrackSummary(0, "Lead", emptyList(), 3840L)), 3840L,
                ),
                selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
                authority = authority,
                candidates = candidates.map(MidiCoreCandidateReviewItem::candidate),
                revision = 6L,
            ),
            projectRoot = Path.of("build/review-project"),
            review = MidiCoreCandidateReviewUiState(CandidateRole.CHORDS, "verse-1", candidates),
            audition = MidiAuditionState(
                playback = MidiAuditionPlaybackState.PLAYING,
                scope = MidiAuditionScope.AcceptedArrangement,
                window = MidiAuditionWindow(0L, 3840L),
                positionTick = 960L,
            ),
        )
    }

    private fun reviewCandidates(): List<MidiCoreCandidateReviewItem> = listOf(
        reviewCandidate("candidate-current", MidiCoreCandidateStatus.CURRENT, accepted = false, locked = false),
        reviewCandidate("candidate-accepted", MidiCoreCandidateStatus.ACCEPTED, accepted = true, locked = false),
        reviewCandidate("candidate-locked", MidiCoreCandidateStatus.ACCEPTED, accepted = true, locked = true),
    )

    private fun reviewCandidate(
        id: String,
        status: MidiCoreCandidateStatus,
        accepted: Boolean,
        locked: Boolean,
    ): MidiCoreCandidateReviewItem {
        val candidate = MidiCoreCandidate(
            id = id,
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            generatorVersion = "midi-core-v1",
            authorityHash = "d".repeat(64),
            seed = id.length.toLong(),
            midi = ProjectArtifact(ProjectRelativePath("candidates/chords/verse-1/$id.mid"), "e".repeat(64)),
            validationReport = ProjectArtifact(ProjectRelativePath("reports/candidates/$id.json"), "f".repeat(64)),
            createdAt = "2026-08-28T00:00:00Z",
            profileId = "chords.sustained",
            patternId = "chords.rhythm.sustained",
            status = status,
        )
        return MidiCoreCandidateReviewItem(
            candidate,
            MidiCoreRoleValidationReport(
                contextSha256 = "1".repeat(64),
                candidateSha256 = "2".repeat(64),
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
            ),
            emptyList(),
            authorityCurrent = true,
            accepted = accepted,
            locked = locked,
        )
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}
