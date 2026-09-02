package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiFindingCode
import app.melotrail.midi.domain.MidiFindingScope
import app.melotrail.midi.domain.MidiFindingSeverity
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.SelectedMelodyTrack
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreMidiPageTest {
    @Test
    fun `MIDI page imports one source and shows the automatically protected channel`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val source = Path.of("build/midi-page-source.mid")
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = projectWithoutSourceState(),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                    midiActions = MidiCoreMidiPageActions { source },
                )
            }
        }

        onNodeWithTag(MidiCoreMidiPageTags.IMPORT).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(MidiCoreWorkspaceIntent.ImportSource(source.toAbsolutePath().normalize()), intents.single())

        intents.clear()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = importedState(format = 0),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                )
            }
        }
        onNodeWithText("Format 0 · PPQ 480").assertExists()
        onNodeWithText("Protected automatically").performScrollTo().assertExists()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun `MIDI page shows format one facts findings and immutable identity evidence`() = runComposeUiTest {
        val findings = listOf(
            MidiFinding(
                MidiFindingCode.TEMPO_MAP_UNSUPPORTED,
                MidiFindingSeverity.BLOCKING,
                MidiFindingScope.TEMPO,
                "Tempo changes are not supported in MIDI Core V1.",
                "Use one fixed tempo before importing.",
            ),
            MidiFinding(
                MidiFindingCode.POLYPHONY,
                MidiFindingSeverity.ADVISORY,
                MidiFindingScope.CHANNEL,
                "The selected melody channel is polyphonic.",
                "Review the melody selection; polyphony is preserved and is not rejected.",
                trackIndex = 1,
                channel = 0,
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = importedState(format = 1, findings = findings),
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                )
            }
        }

        onNodeWithTag(MidiCoreMidiPageTags.SOURCE_FACTS).assertExists()
        onNodeWithText("Format 1 · PPQ 480").assertExists()
        onNodeWithText("Duration 960 ticks").assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.BLOCKING_FINDINGS).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.ADVISORY_FINDINGS).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.IMMUTABILITY).assertExists()
        onNodeWithText("The selected melody channel is polyphonic.").assertExists()
    }

    @Test
    fun `source transport exposes play pause stop seek and loop without audio actions`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = importedState(
                        format = 1,
                        selected = SelectedMelodyTrack(1, 0, "b".repeat(64)),
                        audition = MidiAuditionState(scope = MidiAuditionScope.SourceMelody, window = app.melotrail.audition.MidiAuditionWindow(0, 960), playback = MidiAuditionPlaybackState.PLAYING, positionTick = 120),
                    ),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                )
            }
        }

        onNodeWithTag(MidiCoreMidiPageTags.PLAY).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreMidiPageTags.PAUSE).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreMidiPageTags.STOP).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreMidiPageTags.LOOP).performScrollTo().assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.PlaySourceMelody,
                MidiCoreWorkspaceIntent.PauseAudition,
                MidiCoreWorkspaceIntent.StopAudition,
                MidiCoreWorkspaceIntent.SetAuditionLoop(app.melotrail.audition.MidiAuditionLoop(0, 960)),
            ),
            intents,
        )
        onNodeWithTag(MidiCoreMidiPageTags.SEEK).assertExists()
    }

    @Test
    fun `MIDI page has no superseded preparation controls`() {
        val source = java.nio.file.Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreMidiPage.kt"))
        listOf("audio import", "provenance", "inspect source", "clean source", "transcribe", "normalize MIDI", "transpose MIDI", "AI-fix", "enhance", "MIDI-feel").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "MIDI page must not contain $forbidden")
        }
    }

    private fun projectWithoutSourceState() = MidiCoreWorkspaceState(
        project = MidiCoreProject(
            id = ProjectId("midi-page-project"),
            metadata = ProjectMetadata("MIDI page project", "2026-08-28T00:00:00Z"),
            revision = 1L,
        ),
        projectRoot = Path.of("build/midi-page-project"),
        blockers = listOf(
            MidiCoreWorkspaceBlocker(
                MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED,
                "A source MIDI file has not been imported.",
                "Import one Standard MIDI source.",
            ),
        ),
    )

    private fun importedState(
        format: Int,
        findings: List<MidiFinding> = emptyList(),
        selected: SelectedMelodyTrack = SelectedMelodyTrack(1, 0, "b".repeat(64)),
        audition: MidiAuditionState = MidiAuditionState(),
    ) = MidiCoreWorkspaceState(
        project = projectWithSource(format, selected),
        projectRoot = Path.of("build/midi-page-project"),
        source = MidiCoreSourceUiState(
            status = MidiCoreSourceStatus.IMPORTED,
            originalFilename = "source-format-$format.mid",
            sha256 = "a".repeat(64),
            format = format,
            ppq = 480,
            sourceEndTick = 960,
            trackSummaries = listOf(
                MidiTrackSummary(0, "Conductor", emptyList(), 0),
                MidiTrackSummary(
                    1,
                    "Lead",
                    listOf(MidiChannelSummary(0, 2, 60, 72, 1, listOf(MidiTrackRoleHint.MELODY))),
                    960,
                ),
            ),
            findings = findings,
            reportAvailable = true,
        ),
        melody = MidiCoreMelodyUiState(selected),
        audition = audition,
        blockers = emptyList(),
    )

    private fun projectWithSource(format: Int, selected: SelectedMelodyTrack): MidiCoreProject = MidiCoreProject(
        id = ProjectId("midi-page-project"),
        metadata = ProjectMetadata("MIDI page project", "2026-08-28T00:00:00Z"),
        sourceMidi = app.melotrail.project.SourceMidiRecord(
            "source-format-$format.mid",
            "a".repeat(64),
            format,
            480,
            ProjectArtifact(ProjectRelativePath("source.mid"), "a".repeat(64)),
            ProjectArtifact(ProjectRelativePath("report.json"), "b".repeat(64)),
            listOf(MidiTrackSummary(0, "Conductor", emptyList(), 0), MidiTrackSummary(1, "Lead", emptyList(), 960)),
            960,
        ),
        selectedMelody = selected,
        revision = 2L,
    )

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { java.nio.file.Files.isRegularFile(it) }
}
