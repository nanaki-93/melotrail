package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.SelectedMelodyTrack
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalTestApi::class)
class MidiCoreMidiPageTest {
    @TempDir
    lateinit var root: Path

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

        onNodeWithText("Drop one .mid or .midi file here", substring = true).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.IMPORT).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(MidiCoreWorkspaceIntent.ImportSource(source.toAbsolutePath().normalize()), intents.single())

        intents.clear()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = importedState(format = 0, visualEvidence = sourceVisualEvidence()),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                )
            }
        }
        onNodeWithText("Format 0 · PPQ 480").assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.IMPORT).assertIsNotEnabled()
        onNodeWithTag(MidiCoreMidiPageTags.NOTE_LANE).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.NOTE_LANE + "-canvas").assertExists()
        onNodeWithText("Protected automatically").performScrollTo().assertExists()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun `native drop accepts one MIDI file and stops accepting after source protection`() {
        val first = Files.createFile(root.resolve("first.MIDI"))
        val second = Files.createFile(root.resolve("second.mid"))
        val text = Files.createFile(root.resolve("notes.txt"))
        val imported = mutableListOf<Path>()
        var sourceAlreadyProtected = false
        val target = MidiCoreMidiDropTarget(
            canImport = { !sourceAlreadyProtected },
            onSource = imported::add,
        )

        assertTrue(target.dropFileUris(listOf(first.toUri().toString())))
        assertEquals(listOf(first.toAbsolutePath().normalize()), imported)
        assertFalse(target.dropFileUris(listOf(text.toUri().toString())))
        assertFalse(target.dropFileUris(listOf(first.toUri().toString(), second.toUri().toString())))

        sourceAlreadyProtected = true
        assertFalse(target.dropFileUris(listOf(second.toUri().toString())))
        assertEquals(listOf(first.toAbsolutePath().normalize()), imported)
    }

    @Test
    fun `keyboard Enter opens the retained MIDI chooser`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val source = Files.createFile(root.resolve("keyboard.mid"))
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

        onNodeWithTag(MidiCoreMidiPageTags.IMPORT)
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(1, intents.size)
        assertEquals(MidiCoreWorkspaceIntent.ImportSource(source.toAbsolutePath().normalize()), intents.single())
    }

    @Test
    fun `real parsed multi-track source renders exact track and channel counts`() = runComposeUiTest {
        val source = root.resolve("parsed-tracks.mid")
        val sequence = Sequence(Sequence.PPQ, 480, 3)
        addTrackName(sequence.tracks[0], "Conductor")
        addTrackName(sequence.tracks[1], "Melody")
        addTrackName(sequence.tracks[2], "Reference facts")
        listOf(60, 64, 67).forEachIndexed { index, pitch ->
            sequence.tracks[1].add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 2, pitch, 96), index * 240L))
            sequence.tracks[1].add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 2, pitch, 0), index * 240L + 180L))
        }
        require(MidiSystem.write(sequence, 1, source.toFile()) > 0)
        val inspection = JdkMidiReader().inspect(source)
        val selected = SelectedMelodyTrack(1, 2, inspection.sequence.source.sha256)
        val state = importedState(
            format = inspection.sequence.source.format,
            selected = selected,
            visualEvidence = sourceVisualEvidence(),
            trackSummaries = inspection.trackSummaries,
            sourceEndTick = inspection.sourceEndTick,
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(state = state, initialDestination = MidiCoreWorkspaceDestination.MIDI)
            }
        }

        onNodeWithTag(MidiCoreMidiPageTags.NOTE_LANE).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.TRACK_TABLE).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.TRACK_COUNT).assertExists()
        onNodeWithText("3 source tracks · 1 channel · 3 notes").assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.track(0)).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.track(1)).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.track(2)).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.channel(1, 2)).assertExists()
        onNodeWithText("Track 2: Reference facts").assertExists()
        onNodeWithText("Channel 3").assertExists()
    }

    @Test
    fun `fractional confirmed tempo is displayed consistently on Project and MIDI pages`() = runComposeUiTest {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(501_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = emptyList(),
            occurrences = emptyList(),
            chordEvents = emptyList(),
            arrangementEndTick = 960,
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(state = importedState(format = 1, authority = authority))
            }
        }

        onNodeWithText("119.76 BPM · C major · 4/4").performScrollTo().assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.MIDI)).performClick()
        onNodeWithText("CONFIRMED AUTHORITY · 119.76 BPM · C major · 4/4").performScrollTo().assertExists()
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
                MidiFindingCode.SINGLE_MELODY_CHANNEL_REQUIRED,
                MidiFindingSeverity.BLOCKING,
                MidiFindingScope.TRACK,
                "The melody track contains multiple note-bearing channels.",
                "Export the melody notes on one MIDI channel.",
                trackIndex = 3,
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
            MidiFinding(
                MidiFindingCode.UNSUPPORTED_EVENT,
                MidiFindingSeverity.ADVISORY,
                MidiFindingScope.EVENT,
                "One unsupported event is preserved but omitted from generated roles.",
                "Review the source event.",
                trackIndex = 2,
                channel = 4,
                tick = 720,
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
        onNodeWithTag(MidiCoreMidiPageTags.NOTE_LANE).assertExists()
        onNodeWithText("Format 1 · PPQ 480").assertExists()
        onNodeWithText("Duration 960 ticks").assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.BLOCKING_FINDINGS).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.ADVISORY_FINDINGS).assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.IMMUTABILITY).assertExists()
        onNodeWithText("The selected melody channel is polyphonic.").assertExists()
        onNodeWithText("Scope: Tempo").assertExists()
        onNodeWithText("Scope: Track · Track 3").assertExists()
        onNodeWithText("Scope: Channel · Track 1 · MIDI channel 1").assertExists()
        onNodeWithText("Scope: Event · Track 2 · MIDI channel 5 · Tick 720").assertExists()
        onNodeWithText("AUTHORITY NOT CONFIRMED · imported MIDI facts and suggestions do not change project settings.").assertExists()
    }

    @Test
    fun `source action chooses playback while the persistent player owns transport controls`() = runComposeUiTest {
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
        onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER_PLAY_PAUSE).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER_STOP).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER_LOOP).assertIsEnabled().performClick()
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
        onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER_POSITION).assertExists()
    }

    @Test
    fun `rejected source format keeps its corrective explanation without offering replacement`() = runComposeUiTest {
        val state = projectWithoutSourceState().copy(
            source = MidiCoreSourceUiState(status = MidiCoreSourceStatus.REJECTED),
            blockers = listOf(
                MidiCoreWorkspaceBlocker(
                    MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED,
                    "Format 2 is not supported; use a Standard MIDI File format 0 or 1.",
                    "Export the source as format 0 or 1, then choose it again.",
                    sourceCode = "UNSUPPORTED_FORMAT",
                ),
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(state = state, initialDestination = MidiCoreWorkspaceDestination.MIDI)
            }
        }

        onNodeWithText("Format 2 is not supported; use a Standard MIDI File format 0 or 1.").assertExists()
        onNodeWithText("Next: Export the source as format 0 or 1, then choose it again.").assertExists()
        onNodeWithText("Import MIDI source").assertExists()
    }

    @Test
    fun `rejected structural import renders its scoped findings and available recovery`() = runComposeUiTest {
        val finding = MidiFinding(
            MidiFindingCode.TEMPO_MAP_UNSUPPORTED,
            MidiFindingSeverity.BLOCKING,
            MidiFindingScope.TEMPO,
            "Tempo changes are not supported in MIDI Core V1.",
            "Use one fixed tempo before importing.",
        )
        val state = projectWithoutSourceState().copy(
            source = MidiCoreSourceUiState(
                status = MidiCoreSourceStatus.REJECTED,
                findings = listOf(finding),
            ),
            blockers = listOf(
                MidiCoreWorkspaceBlocker(
                    MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED,
                    "The MIDI source has blocking structural issues and was not imported.",
                    "Resolve the blocking findings shown in MIDI, then retry the import.",
                    sourceCode = "IMPORT_REJECTED",
                ),
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(state = state, initialDestination = MidiCoreWorkspaceDestination.MIDI)
            }
        }

        onNodeWithTag(MidiCoreMidiPageTags.FINDINGS).performScrollTo().assertExists()
        onNodeWithText("Scope: Tempo").assertExists()
        onNodeWithText("Tempo changes are not supported in MIDI Core V1.").assertExists()
        onNodeWithText("Action: Use one fixed tempo before importing.").assertExists()
        onNodeWithText("Next: Resolve the blocking findings shown in MIDI, then retry the import.").assertExists()
        onNodeWithTag(MidiCoreMidiPageTags.IMPORT).assertIsEnabled()
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
        visualEvidence: app.melotrail.application.MidiCoreVisualEvidenceProjection? = null,
        trackSummaries: List<MidiTrackSummary> = defaultTrackSummaries(),
        sourceEndTick: Long = 960,
        authority: ProjectAuthority? = null,
    ) = MidiCoreWorkspaceState(
        project = projectWithSource(format, selected, trackSummaries, sourceEndTick, authority),
        projectRoot = Path.of("build/midi-page-project"),
        source = MidiCoreSourceUiState(
            status = MidiCoreSourceStatus.IMPORTED,
            originalFilename = "source-format-$format.mid",
            sha256 = "a".repeat(64),
            format = format,
            ppq = 480,
            sourceEndTick = sourceEndTick,
            trackSummaries = trackSummaries,
            findings = findings,
            reportAvailable = true,
        ),
        melody = MidiCoreMelodyUiState(selected),
        authority = MidiCoreAuthorityUiState(confirmed = authority),
        visualEvidence = visualEvidence,
        audition = audition,
        blockers = emptyList(),
    )

    private fun sourceVisualEvidence(): app.melotrail.application.MidiCoreVisualEvidenceProjection {
        val source = app.melotrail.application.MidiCoreVisualEvidence.Available(
            app.melotrail.application.MidiCoreVisualEvidenceAvailable(
                scope = app.melotrail.application.MidiCoreVisualEvidenceScope.PROTECTED_SOURCE,
                identity = app.melotrail.application.MidiCoreVisualEvidenceIdentity("midi-page-project", "a".repeat(64), null),
                timing = app.melotrail.application.MidiCoreVisualEvidenceTiming(480, 960, 500_000, 4, 2, authoritative = false),
                lanes = listOf(
                    app.melotrail.application.MidiCoreVisualEvidenceLane(
                        app.melotrail.midi.domain.MidiExportRole.MELODY,
                        listOf(app.melotrail.application.MidiCoreVisualEvidenceEvent(0, 480, 0, 60, 96, percussion = false)),
                    ),
                ),
                currentness = app.melotrail.application.MidiCoreVisualEvidenceCurrentness.CURRENT,
                cacheStatus = app.melotrail.application.MidiCoreVisualEvidenceCacheStatus.COLD,
            ),
        )
        fun unavailable(scope: app.melotrail.application.MidiCoreVisualEvidenceScope) = app.melotrail.application.MidiCoreVisualEvidence.Unavailable(
            app.melotrail.application.MidiCoreVisualEvidenceUnavailable(scope, "NOT_READY", "Not ready for this fixture.", "Choose the required evidence."),
        )
        return app.melotrail.application.MidiCoreVisualEvidenceProjection(
            source = source,
            selectedCandidate = unavailable(app.melotrail.application.MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE),
            draft = unavailable(app.melotrail.application.MidiCoreVisualEvidenceScope.DRAFT),
            accepted = unavailable(app.melotrail.application.MidiCoreVisualEvidenceScope.ACCEPTED),
        )
    }

    private fun defaultTrackSummaries(): List<MidiTrackSummary> = listOf(
        MidiTrackSummary(0, "Conductor", emptyList(), 0),
        MidiTrackSummary(
            1,
            "Lead",
            listOf(MidiChannelSummary(0, 2, 60, 72, 1, listOf(MidiTrackRoleHint.MELODY))),
            960,
        ),
    )

    private fun addTrackName(track: javax.sound.midi.Track, name: String) {
        val bytes = name.encodeToByteArray()
        track.add(MidiEvent(MetaMessage(0x03, bytes, bytes.size), 0))
    }

    private fun projectWithSource(
        format: Int,
        selected: SelectedMelodyTrack,
        trackSummaries: List<MidiTrackSummary>,
        sourceEndTick: Long,
        authority: ProjectAuthority?,
    ): MidiCoreProject = MidiCoreProject(
        id = ProjectId("midi-page-project"),
        metadata = ProjectMetadata("MIDI page project", "2026-08-28T00:00:00Z"),
        sourceMidi = app.melotrail.project.SourceMidiRecord(
            "source-format-$format.mid",
            "a".repeat(64),
            format,
            480,
            ProjectArtifact(ProjectRelativePath("source.mid"), "a".repeat(64)),
            ProjectArtifact(ProjectRelativePath("report.json"), "b".repeat(64)),
            trackSummaries,
            sourceEndTick,
        ),
        selectedMelody = selected,
        authority = authority,
        revision = 2L,
    )

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { java.nio.file.Files.isRegularFile(it) }
}
