package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreExportSnapshot
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
class MidiCoreExportPageTest {
    @Test
    fun `Export destination is rendered through the focused workspace shell`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = exportState(),
                    initialDestination = MidiCoreWorkspaceDestination.EXPORT,
                )
            }
        }

        onNodeWithTag(MidiCoreExportPageTags.ROOT).assertExists()
    }

    @Test
    fun `Export publishes a new package only after each required role occurrence is accepted`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreExportPage(exportState(), intents::add) } }

        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ExportPackage), intents)
    }

    @Test
    fun `Export explains missing acceptance rather than exposing a ready publish action`() = runComposeUiTest {
        setContent { MelotrailTheme { MidiCoreExportPage(exportState(acceptances = emptyList()), {}) } }

        onNodeWithTag(MidiCoreExportPageTags.READINESS).assertExists()
        onNodeWithText("Accept one current Chords candidate for Verse 1 before exporting.").assertExists()
    }

    @Test
    fun `Export identifies stale accepted evidence before publishing`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreExportPage(exportState(candidateStatus = MidiCoreCandidateStatus.STALE), {})
            }
        }

        onNodeWithText("The accepted Chords candidate for Verse 1 is stale. Regenerate and explicitly accept a current candidate before exporting.").assertExists()
    }

    @Test
    fun `Export offers a cancellable boundary while publication is in progress`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val exporting = exportState(
            operation = MidiCoreWorkspaceOperation(
                id = 7L,
                kind = MidiCoreWorkspaceOperationKind.EXPORT,
                phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                message = "Publishing MIDI package…",
                cancellableAtBoundary = true,
            ),
        )
        setContent { MelotrailTheme { MidiCoreExportPage(exporting, intents::add) } }

        onNodeWithTag(MidiCoreExportPageTags.PROGRESS).assertExists()
        onNodeWithTag(MidiCoreExportPageTags.CANCEL).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.CancelOperation), intents)
    }

    @Test
    fun `Export exposes immutable snapshot hashes files reveal action retry and DAW guidance`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        var revealed: Path? = null
        val snapshot = exportSnapshot()
        val state = exportState(
            snapshot = snapshot,
            operation = MidiCoreWorkspaceOperation(
                id = 8L,
                kind = MidiCoreWorkspaceOperationKind.EXPORT,
                phase = MidiCoreWorkspaceOperationPhase.FAILED,
                message = "A fresh snapshot can be retried.",
                retry = MidiCoreWorkspaceIntent.ExportPackage,
                outcome = MidiCoreWorkspaceOperationOutcome.FAILURE,
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreExportPage(state, intents::add, MidiCoreExportPageActions { revealed = it })
            }
        }

        snapshot.files.forEach { file -> onNodeWithTag(MidiCoreExportPageTags.file(file.kind)).performScrollTo().assertExists() }
        onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().performClick()
        onNodeWithTag(MidiCoreExportPageTags.RETRY).performScrollTo().performClick()
        onNodeWithTag(MidiCoreExportPageTags.SUGGESTIONS).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreExportPageTags.DAW_GUIDANCE).performScrollTo().assertExists()
        assertEquals(Path.of("build/export-project/exports/export-ready"), revealed)
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.Retry), intents)
    }

    @Test
    fun `Export source contains no audio release controls`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreExportPage.kt")).lowercase()
        listOf("audio format", "sample-rate", "master preview", "credits", "commercial evidence", "mix/master").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Export page must not contain $forbidden")
        }
    }

    private fun exportState(
        acceptances: List<CandidateAcceptance> = CandidateRole.entries.map { role -> CandidateAcceptance("verse-1", role, "${role.name.lowercase()}-candidate", false) },
        candidateStatus: MidiCoreCandidateStatus = MidiCoreCandidateStatus.ACCEPTED,
        snapshot: MidiCoreExportSnapshot? = null,
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
    ): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse 1", 0L, 1920L)),
            chordEvents = listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1920L)),
        )
        return MidiCoreWorkspaceState(
            project = MidiCoreProject(
                id = ProjectId("export-project"),
                metadata = ProjectMetadata("Export project", "2026-08-28T00:00:00Z"),
                sourceMidi = SourceMidiRecord(
                    "source.mid", "a".repeat(64), 1, 480,
                    ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
                    ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
                    listOf(MidiTrackSummary(0, "Lead", emptyList(), 1920L)), 1920L,
                ),
                selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
                authority = authority,
                candidates = CandidateRole.entries.map { role ->
                    MidiCoreCandidate(
                        id = "${role.name.lowercase()}-candidate",
                        role = role,
                        occurrenceId = "verse-1",
                        generatorVersion = "midi-core-v1",
                        authorityHash = "d".repeat(64),
                        seed = role.ordinal.toLong(),
                        midi = ProjectArtifact(
                            ProjectRelativePath("candidates/${role.name.lowercase()}/verse-1/${role.name.lowercase()}-candidate.mid"),
                            "e".repeat(64),
                        ),
                        validationReport = ProjectArtifact(
                            ProjectRelativePath("reports/candidates/${role.name.lowercase()}-candidate.json"),
                            "f".repeat(64),
                        ),
                        createdAt = "2026-08-28T00:00:00Z",
                        profileId = "${role.name.lowercase()}.default",
                        patternId = "${role.name.lowercase()}.pattern.default",
                        status = candidateStatus,
                    )
                },
                acceptances = acceptances,
                exportSnapshots = snapshot?.let(::listOf).orEmpty(),
                revision = 7L,
            ),
            projectRoot = Path.of("build/export-project"),
            export = MidiCoreExportUiState(latestSnapshot = snapshot),
            operation = operation,
        )
    }

    private fun exportSnapshot(): MidiCoreExportSnapshot = MidiCoreExportSnapshot(
        id = "export-ready",
        sourceSha256 = "a".repeat(64),
        authorityHash = "d".repeat(64),
        files = ExportedFileKind.entries.map { kind ->
            ExportedSnapshotFile(kind, ProjectArtifact(ProjectRelativePath("exports/export-ready/${kind.name.lowercase()}.mid"), "e".repeat(64)))
        },
        createdAt = "2026-08-28T00:00:00Z",
    )

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}
