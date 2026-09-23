package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.project.MidiCoreAcceptedCandidateReference
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
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.MidiCoreGrooveDrive
import app.melotrail.project.MidiCoreGrooveFeel
import app.melotrail.project.MidiCoreGrooveSubdivision
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCorePlannedRest
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreRegisterPreference
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.MidiCoreSharedGrooveIntent
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
    fun `Export keeps MIDI publication independent of video and preserves saved snapshots`() = runComposeUiTest {
        val snapshot = currentSnapshotState()
        setContent { MelotrailTheme { MidiCoreExportPage(snapshot, {}) } }
        onNodeWithTag(MidiCoreExportPageTags.SNAPSHOT_STATUS).performScrollTo()
            .assertTextEquals("Matches current accepted work")
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsEnabled()
        onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().assertIsEnabled()
        onNodeWithText("Open in TABI…").assertDoesNotExist()
    }

    private fun currentSnapshotState(): MidiCoreWorkspaceState {
        val state = exportState()
        val project = requireNotNull(state.project)
        val references = project.candidates.sortedWith(compareBy<MidiCoreCandidate> { it.occurrenceId }.thenBy { it.role.ordinal }).map {
            MidiCoreAcceptedCandidateReference(it.occurrenceId, it.role, it.id, it.midi.sha256, it.validationReport.sha256,
                it.authorityHash, it.generatorVersion, it.profileId, it.patternId, it.seed)
        }
        val snapshot = exportSnapshot().copy(
            authorityHash = app.melotrail.project.MidiCoreAuthorityHasher.from(project).sha256,
            acceptedCandidates = references,
            generatorVersions = references.associate { "${it.occurrenceId}.${it.role.name.lowercase()}" to it.generatorVersion },
        )
        return state.copy(project = project.copy(exportSnapshots = listOf(snapshot)), export = MidiCoreExportUiState(latestSnapshot = snapshot))
    }

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
    fun `Export accepts a current planned rest as complete scoped work`() = runComposeUiTest {
        val plan = exportPlan(inactiveRole = CandidateRole.BASS)
        val initial = exportState(arrangementPlan = plan)
        val project = requireNotNull(initial.project)
        val rest = MidiCorePlannedRest(
            occurrenceId = "verse-1",
            role = CandidateRole.BASS,
            authorityHash = app.melotrail.project.MidiCoreAuthorityHasher.from(project).scopeHash("verse-1", CandidateRole.BASS),
        )
        val state = initial.copy(project = project.copy(
            acceptances = project.acceptances.filterNot { it.role == CandidateRole.BASS },
            acceptedPlannedRests = listOf(rest),
        ))
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()

        setContent { MelotrailTheme { MidiCoreExportPage(state, intents::add) } }

        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ExportPackage), intents)
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
    fun `Export shows atomic progress without offering a cancellation it cannot honor`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val exporting = exportState(
            operation = MidiCoreWorkspaceOperation(
                id = 7L,
                kind = MidiCoreWorkspaceOperationKind.EXPORT,
                phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                message = "Publishing MIDI package…",
                cancellableAtBoundary = false,
            ),
        )
        setContent { MelotrailTheme { MidiCoreExportPage(exporting, intents::add) } }

        onNodeWithTag(MidiCoreExportPageTags.PROGRESS).assertExists()
        onNodeWithTag(MidiCoreExportPageTags.CANCEL).assertDoesNotExist()
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).assertDoesNotExist()
        assertEquals(emptyList(), intents)
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
        onNodeWithText("Logic Pro").assertExists()
        onNodeWithText("1. Import complete-song.mid at bar 1. Alternatively, import the individual role files together at that same origin; their leading silence is intentional.").assertExists()
        assertEquals(Path.of("build/export-project/exports/export-ready"), revealed)
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.Retry), intents)
    }

    @Test
    fun `Export readiness checks scoped authority even when candidate status still says accepted`() = runComposeUiTest {
        val initial = exportState()
        val project = requireNotNull(initial.project)
        val state = initial.copy(project = project.copy(candidates = project.candidates.map {
            if (it.role == CandidateRole.BASS) it.copy(authorityHash = "0".repeat(64)) else it
        }))
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        var destination: MidiCoreWorkspaceDestination? = null
        setContent { MelotrailTheme { MidiCoreExportPage(state, intents::add, onNavigate = { destination = it }) } }
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Review Verse 1 · Bar 1 · Bass").performScrollTo().performClick()
        assertEquals(listOf(
            MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-1"),
            MidiCoreWorkspaceIntent.SelectReviewScope(CandidateRole.BASS, "verse-1"),
        ), intents)
        assertEquals(MidiCoreWorkspaceDestination.REVIEW, destination)
    }

    @Test
    fun `Export never labels an empty structure ready`() = runComposeUiTest {
        val initial = exportState(acceptances = emptyList())
        val project = requireNotNull(initial.project)
        val state = initial.copy(project = project.copy(
            authority = requireNotNull(project.authority).copy(occurrences = emptyList(), chordEvents = emptyList()),
            candidates = emptyList(),
        ))
        setContent { MelotrailTheme { MidiCoreExportPage(state, {}) } }
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsNotEnabled()
        onNodeWithText("Define the song sections before exporting.").assertExists()
    }

    @Test
    fun `Export inventory distinguishes accepted whole-song rest and its lock from an omitted missing role`() = runComposeUiTest {
        val initial = exportState(arrangementPlan = exportPlan(CandidateRole.BASS))
        val project = requireNotNull(initial.project)
        val rest = MidiCorePlannedRest("verse-1", CandidateRole.BASS,
            app.melotrail.project.MidiCoreAuthorityHasher.from(project).scopeHash("verse-1", CandidateRole.BASS), locked = true)
        val state = initial.copy(project = project.copy(
            acceptances = project.acceptances.filterNot { it.role == CandidateRole.BASS },
            acceptedPlannedRests = listOf(rest),
        ))
        setContent { MelotrailTheme { MidiCoreExportPage(state, {}) } }
        onNodeWithText("Bass — 0 accepted · 1 rests · whole-song rest · file omitted").assertExists()
        onNodeWithTag(MidiCoreExportPageTags.INVENTORY).performScrollTo().performClick()
        onNodeWithTag(MidiCoreExportPageTags.scope("verse-1", CandidateRole.BASS))
            .assertTextContains("Verse 1 · Bar 1 · Bass: Accepted planned rest · Locked")
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsEnabled()
    }

    @Test
    fun `Export retains an earlier snapshot result and reports failed folder reveal`() = runComposeUiTest {
        val snapshot = exportSnapshot()
        val state = exportState(acceptances = emptyList(), snapshot = snapshot)
        setContent { MelotrailTheme {
            MidiCoreExportPage(state, {}, MidiCoreExportPageActions { error("Finder unavailable") })
        } }
        onNodeWithTag(MidiCoreExportPageTags.SNAPSHOT_STATUS).performScrollTo()
            .assertTextContains("Earlier accepted work — this saved package is unchanged")
        onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().performClick()
        onNodeWithTag(MidiCoreExportPageTags.REVEAL_ERROR).assertExists()
        snapshot.files.forEach { onNodeWithTag(MidiCoreExportPageTags.file(it.kind)).assertExists() }
        onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `Export source contains no audio release controls`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreExportPage.kt")).lowercase()
        listOf("audio format", "sample-rate", "master preview", "credits", "commercial evidence", "mix/master").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Export page must not contain $forbidden")
        }
    }

    @Test
    fun `Export source makes no GarageBand support claim`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreExportPage.kt"))
        assertFalse(source.contains("GarageBand"))
    }

    private fun exportState(
        acceptances: List<CandidateAcceptance> = CandidateRole.entries.map { role -> CandidateAcceptance("verse-1", role, "${role.name.lowercase()}-candidate", false) },
        candidateStatus: MidiCoreCandidateStatus = MidiCoreCandidateStatus.ACCEPTED,
        snapshot: MidiCoreExportSnapshot? = null,
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
        arrangementPlan: MidiCoreArrangementPlan? = null,
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
                arrangementPlan = arrangementPlan,
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
        ).let { state ->
            val project = requireNotNull(state.project)
            val fingerprint = app.melotrail.project.MidiCoreAuthorityHasher.from(project)
            state.copy(project = project.copy(candidates = project.candidates.map {
                it.copy(authorityHash = fingerprint.scopeHash(it.occurrenceId, it.role))
            }))
        }
    }

    private fun exportPlan(inactiveRole: CandidateRole? = null) = MidiCoreArrangementPlan(
        version = 1,
        sharedGroove = MidiCoreSharedGrooveIntent(
            MidiCoreGrooveFeel.STRAIGHT,
            MidiCoreGrooveSubdivision.EIGHTH,
            MidiCoreGrooveDrive.STEADY,
        ),
        occurrences = listOf(
            MidiCoreOccurrenceArrangementPlan(
                occurrenceId = "verse-1",
                purpose = MidiCoreArrangementPurpose.VERSE,
                phraseGroupId = "phrase-verse-1",
                repeatFamilyId = "repeat-verse-1",
                repeatOrdinal = 1,
                energy = 50,
                roleSettings = CandidateRole.entries.map { role ->
                    MidiCoreRolePlanSettings(
                        role,
                        if (role == inactiveRole) MidiCoreRoleActivity.INACTIVE else MidiCoreRoleActivity.SUPPORTING,
                        if (role == inactiveRole) 0 else 50,
                        MidiCoreRegisterPreference.MID,
                    )
                },
                entryIntent = MidiCoreBoundaryIntent.NONE,
                exitIntent = MidiCoreBoundaryIntent.NONE,
            ),
        ),
    )

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
