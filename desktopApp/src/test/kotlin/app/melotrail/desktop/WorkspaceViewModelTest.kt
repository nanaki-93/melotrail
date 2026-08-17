package app.melotrail.desktop

import app.melotrail.application.AnalyzePartRequest
import app.melotrail.application.ArrangementApplicationService
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.BuildApplicationService
import app.melotrail.application.BuildResult
import app.melotrail.application.BuildSongRequest
import app.melotrail.application.CreateProjectRequest
import app.melotrail.application.GenerateArrangementRequest
import app.melotrail.application.GeneratedMidiSnapshot
import app.melotrail.application.ImportPartRequest
import app.melotrail.application.ProjectApplicationService
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.PrepareMidiRequest
import app.melotrail.application.PrepareMidiResult
import app.melotrail.application.PrepareMidiOutcome
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.UpdatePartRoleRequest
import app.melotrail.application.PartPreviewApplicationService
import app.melotrail.application.PreviewRequest
import app.melotrail.application.PreviewResult
import app.melotrail.application.PreviewAudioSource
import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Path
import java.util.UUID
import java.util.prefs.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    @Test
    fun `starts idle without a project`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        assertNull(viewModel.state.value.project)
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        viewModel.close()
    }

    @Test
    fun `reports loading while opening then exposes the project`() = runTest {
        val root = Path.of("build/test-project")
        val project = projectSnapshot(root)
        val viewModel = WorkspaceViewModel(FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(root))

        assertIs<WorkspaceOperation.OpeningProject>(viewModel.state.value.operation)
        advanceUntilIdle()
        assertEquals(project, viewModel.state.value.project)
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        viewModel.close()
    }

    @Test
    fun `workspace navigation updates one explicit selected destination`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        WorkspaceSection.entries.forEach { destination ->
            viewModel.accept(WorkspaceIntent.SelectWorkspaceSection(destination))
            assertEquals(destination, viewModel.state.value.workspaceSection)
        }
        viewModel.close()
    }

    @Test
    fun `open lands on overview while an ordinary mutation preserves focused page and selection`() = runTest {
        val root = Path.of("build/task-083-navigation")
        val project = projectSnapshot(root).copy(
            version = 3,
            parts = listOf(part("A")),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
        )
        val viewModel = WorkspaceViewModel(FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        assertEquals(WorkspaceSection.OVERVIEW, viewModel.state.value.workspaceSection)
        viewModel.accept(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.ARRANGE))
        viewModel.accept(WorkspaceIntent.SelectPart("A"))
        viewModel.accept(WorkspaceIntent.SelectArrangementSection(0))
        viewModel.accept(WorkspaceIntent.UpdateArrangementStyle("warm lo-fi"))
        viewModel.accept(WorkspaceIntent.ShowRoleEditor("A"))
        viewModel.accept(WorkspaceIntent.UpdateRole("intro"))
        viewModel.accept(WorkspaceIntent.SaveRole); advanceUntilIdle()

        assertEquals(WorkspaceSection.ARRANGE, viewModel.state.value.workspaceSection)
        assertEquals("A", viewModel.state.value.selectedPartId)
        assertEquals(0, viewModel.state.value.selectedArrangementSection)
        assertEquals("warm lo-fi", viewModel.state.value.arrangementDraft.style)
        viewModel.close()
    }

    @Test
    fun `adding a part without a project produces visible guidance`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))

        assertEquals("Create or open a project before adding a part.", viewModel.state.value.notification)
        assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation)
        viewModel.close()
    }

    @Test
    fun `part primary actions are derived from canonical artifact state`() {
        val raw = part("raw").copy(preparation = preparation(rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID))
        val review = raw.copy(id = "review", preparation = preparation(rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED))
        val ready = analyzedPart("ready")

        assertIs<PartPrimaryAction.PrepareMidi>(primaryPartAction(raw))
        assertIs<PartPrimaryAction.ReviewRepair>(primaryPartAction(review))
        assertIs<PartPrimaryAction.AddToStructure>(primaryPartAction(ready))
        assertIs<PartPrimaryAction.ApplyLoFiChange>(primaryPartAction(ready, app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL))
    }

    @Test
    fun `prepare MIDI uses one orchestration command and exposes its single result`() = runTest {
        val root = Path.of("build/prepare-midi-project")
        val snapshot = projectSnapshot(root).copy(parts = listOf(part("intro").copy(
            preparation = preparation(rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID)
        )))
        val service = FakeProjectService(result = snapshot)
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PrepareMidi("intro")); advanceUntilIdle()

        assertEquals(PrepareMidiRequest(root, "intro"), service.prepared)
        assertEquals("MIDI prepared and analyzed. Add intro to structure when ready.", viewModel.state.value.notification)
        viewModel.close()
    }

    @Test
    fun `opening a legacy project is successful and visibly explains its limitations`() = runTest {
        val root = Path.of("build/legacy-project")
        val legacy = projectSnapshot(root).copy(version = 1, name = "legacy-song")
        val viewModel = WorkspaceViewModel(FakeProjectService(result = legacy), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()

        assertEquals(legacy, viewModel.state.value.project)
        assertTrue(viewModel.state.value.notification!!.contains("Legacy v1 project opened"))
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        viewModel.close()
    }

    @Test
    fun `opening a project surfaces optional artifact hydration failures`() = runTest {
        val root = Path.of("build/partial-project")
        val project = projectSnapshot(root).copy(
            readiness = projectSnapshot(root).readiness.copy(arrangementAvailable = true)
        )
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            arrangementService = FakeArrangementService()
        )

        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()

        assertEquals(project, viewModel.state.value.project)
        assertTrue(viewModel.state.value.notification!!.contains("arrangement artifacts could not be loaded"))
        viewModel.close()
    }

    @Test
    fun `keeps the workspace empty when opening fails`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(failure = IllegalArgumentException("Project file not found")), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(Path.of("missing")))
        advanceUntilIdle()

        assertNull(viewModel.state.value.project)
        assertEquals("Project file not found", assertIs<WorkspaceOperation.OpenFailed>(viewModel.state.value.operation).message)
        assertTrue(viewModel.state.value.notification!!.contains("Unable to open project"))
        viewModel.close()
    }

    @Test
    fun `file dialog cancellation leaves the workspace unchanged`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.ChooseProject)
        advanceUntilIdle()

        assertNull(viewModel.state.value.project)
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        viewModel.close()
    }

    @Test
    fun `project switching confirms unsaved arrangement controls and retains canonical project until confirmed`() = runTest {
        val current = Path.of("build/current-project")
        val next = Path.of("build/next-project")
        val service = FakeProjectService(result = projectSnapshot(current))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(current))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.UpdateArrangementStyle("warm lo-fi"))
        viewModel.accept(WorkspaceIntent.OpenProject(next))

        assertEquals(current, viewModel.state.value.project?.root)
        assertEquals(WorkspaceDialog.ConfirmDiscardDraft(root = next), viewModel.state.value.dialog)
        viewModel.accept(WorkspaceIntent.ConfirmDiscardDraft)
        advanceUntilIdle()
        assertEquals(current, viewModel.state.value.project?.root, "the fake service returns the canonical current fixture")
        assertTrue(!viewModel.state.value.arrangementDraftDirty)
        viewModel.close()
    }

    @Test
    fun `last successful project is restored as a preference and corrupt preferences are ignored`() = runTest {
        val root = Path.of("build/restored-project")
        val preferences = FakeDesktopPreferences(root)
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(root)), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), preferences = preferences
        )

        viewModel.accept(WorkspaceIntent.RestoreLastProject)
        advanceUntilIdle()
        assertEquals(root, viewModel.state.value.project?.root)
        assertEquals(root, preferences.saved)
        viewModel.close()

        val node = Preferences.userRoot().node("melotrail-test-${UUID.randomUUID()}")
        try {
            node.put("last-successfully-opened-project", "   ")
            assertNull(JvmDesktopPreferences(node, node).lastOpenedProject())
            val soundRoot = java.nio.file.Files.createTempDirectory("desktop-preference-library")
            val desktopPreferences = JvmDesktopPreferences(node, node)
            desktopPreferences.saveSoundLibraryRoot(soundRoot)
            assertEquals(soundRoot, desktopPreferences.soundLibraryRoot())
            node.put("sound-library-root", "   ")
            assertNull(desktopPreferences.soundLibraryRoot())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun `close requires confirmation while an operation is active`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(result = projectSnapshot(Path.of("build/close-project"))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(Path.of("build/close-project")))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.UpdateArrangementStyle("draft"))

        assertTrue(!viewModel.requestClose())
        assertEquals(WorkspaceDialog.ConfirmClose, viewModel.state.value.dialog)
        viewModel.close()
    }

    @Test
    fun `creates imports analyzes edits and saves a reloaded structure through the service`() = runTest {
        val root = Path.of("build/test-project")
        val snapshot = projectSnapshot(root).copy(parts = listOf(analyzedPart("A"), analyzedPart("B")))
        val service = FakeProjectService(result = snapshot)
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.ShowCreateProject)
        viewModel.accept(WorkspaceIntent.UpdateCreateProject(WorkspaceDialog.CreateProject(root, "test-project", "44100", "2")))
        viewModel.accept(WorkspaceIntent.CreateProject)
        advanceUntilIdle()
        assertEquals(root, service.created?.root)
        assertEquals(WorkspaceSection.OVERVIEW, viewModel.state.value.workspaceSection)

        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))
        viewModel.accept(WorkspaceIntent.UpdateImportPart(WorkspaceDialog.ImportPart(false, Path.of("input.mid"), "C", "verse")))
        viewModel.accept(WorkspaceIntent.ImportPart)
        assertIs<WorkspaceOperation.ImportingPart>(viewModel.state.value.operation)
        advanceUntilIdle()
        assertEquals("C", service.imported?.id)

        viewModel.accept(WorkspaceIntent.AnalyzePart("A"))
        advanceUntilIdle()
        assertEquals("A", service.analyzed?.partId)

        viewModel.accept(WorkspaceIntent.ShowRoleEditor("A"))
        viewModel.accept(WorkspaceIntent.UpdateRole("chorus"))
        viewModel.accept(WorkspaceIntent.SaveRole)
        advanceUntilIdle()
        assertEquals("chorus", service.updatedRole?.role)

        viewModel.accept(WorkspaceIntent.AddStructurePart("A"))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.DuplicateStructurePart(0))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.MoveStructurePart(1, 0))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RemoveStructurePart(1))
        advanceUntilIdle()
        assertEquals(listOf("A"), service.savedStructure?.partIds)
        assertEquals(listOf("A"), viewModel.state.value.structureDraft)
        assertTrue(service.openCalls >= 5, "successful mutations are refreshed from the canonical project")
        viewModel.close()
    }

    @Test
    fun `prepared MIDI and eligible transcribed audio enter structure only through canonical saves`() = runTest {
        val root = Path.of("build/task-085-structure")
        val rawMidi = part("M").copy(preparation = preparation(rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID))
        val preparedMidi = analyzedPart("M")
        val audio = audioPart("P")
        val transcribedAudio = audio.copy(analysis = app.melotrail.application.PartAnalysisSummary(app.melotrail.application.PartAnalysisStatus.MIDI, "analysis/P.midi.json", bars = 3))
        val service = FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(rawMidi, audio)))
        service.preparedResult = projectSnapshot(root).copy(parts = listOf(preparedMidi, audio))
        val preparation = FakeAudioPreparationService(
            projectSnapshot(root).copy(
                parts = listOf(preparedMidi, transcribedAudio),
                structure = listOf(app.melotrail.application.StructureSectionSummary(0, "M", 1, "M1", null))
            ),
            availablePreparation("P", recommended = false)
        )
        val viewModel = WorkspaceViewModel(
            service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, audioPreparationService = preparation
        )

        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PrepareMidi("M")); advanceUntilIdle()
        assertIs<PartPrimaryAction.AddToStructure>(primaryPartAction(checkNotNull(viewModel.state.value.project).parts.first { it.id == "M" }))
        viewModel.accept(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE))
        viewModel.accept(WorkspaceIntent.AddStructurePart("M")); advanceUntilIdle()
        assertEquals(listOf("M"), checkNotNull(service.savedStructure).partIds)

        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("P")); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.TranscribeSelectedPart); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.AddStructurePart("P")); advanceUntilIdle()

        assertEquals(listOf("M", "P"), checkNotNull(service.savedStructure).partIds)
        assertEquals(listOf("M1", "P1"), checkNotNull(viewModel.state.value.project).structure.map { it.instanceId })
        assertEquals(WorkspaceSection.STRUCTURE, viewModel.state.value.workspaceSection)
        viewModel.close()
    }

    @Test
    fun `structure mutations preserve canonical identities reject ineligible parts and recover from save failure`() = runTest {
        val root = Path.of("build/task-085-mutations")
        val ready = analyzedPart("A")
        val raw = part("raw").copy(preparation = preparation(rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID))
        val service = FakeProjectService(result = projectSnapshot(root).copy(
            parts = listOf(ready, raw),
            readiness = app.melotrail.application.ProjectReadiness(true, true, false, true, true, true, true, true, true, false)
        ))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE))
        viewModel.accept(WorkspaceIntent.AddStructurePart("raw")); advanceUntilIdle()
        assertNull(service.savedStructure)
        assertEquals(emptyList(), viewModel.state.value.structureDraft)

        viewModel.accept(WorkspaceIntent.AddStructurePart("A")); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.AddStructurePart("A")); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.DuplicateStructurePart(0)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.MoveStructurePart(2, 0)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RemoveStructurePart(1)); advanceUntilIdle()
        assertEquals(listOf("A", "A"), viewModel.state.value.structureDraft)
        assertEquals(listOf("A1", "A2"), checkNotNull(viewModel.state.value.project).structure.map { it.instanceId })
        assertTrue(viewModel.state.value.downstreamArtifactsStale)

        service.failureOnSave = IllegalStateException("disk full")
        viewModel.accept(WorkspaceIntent.ClearStructure); advanceUntilIdle()
        assertEquals(listOf("A", "A"), viewModel.state.value.structureDraft, "failed saves must retain the loaded canonical structure")
        assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation)
        service.failureOnSave = null
        viewModel.accept(WorkspaceIntent.ClearStructure); advanceUntilIdle()
        assertEquals(emptyList(), viewModel.state.value.structureDraft)
        assertEquals(WorkspaceSection.STRUCTURE, viewModel.state.value.workspaceSection)
        viewModel.close()
    }

    @Test
    fun `failed import exposes an explicit retry`() = runTest {
        val root = Path.of("build/test-project")
        val service = FakeProjectService(result = projectSnapshot(root), failureOnImport = IllegalStateException("worker unavailable"))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = true))
        viewModel.accept(WorkspaceIntent.UpdateImportPart(WorkspaceDialog.ImportPart(true, Path.of("input.wav"), "A", "verse")))
        viewModel.accept(WorkspaceIntent.ImportPart)
        advanceUntilIdle()

        assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation)
        assertIs<WorkspaceRetry.Import>(viewModel.state.value.retry)
        assertNull(viewModel.state.value.dialog, "the import modal must not hide progress or retryable failures")
        assertEquals("worker unavailable", viewModel.state.value.notification)
        viewModel.close()
    }

    @Test
    fun `MIDI quality retry uses only named profiles confirms timing and invalidates downstream state`() = runTest {
        val root = Path.of("build/quality-project")
        val currentQuality = app.melotrail.application.MidiQualitySummary(
            app.melotrail.application.MidiQualityStatus.CURRENT,
            app.melotrail.arrangement.MidiCleanupOptions()
        )
        val snapshot = projectSnapshot(root).copy(
            parts = listOf(part("A").copy(preparation = part("A").preparation.copy(midiQuality = currentQuality))),
            readiness = app.melotrail.application.ProjectReadiness(true, true, true, true, true, false, false, false, false, false, true)
        )
        val service = FakeProjectService(result = snapshot)
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A"))

        assertEquals(app.melotrail.arrangement.MidiCleanupProfile.TRANSCRIPTION_SAFE, viewModel.state.value.midiQualityReview.profile)
        viewModel.accept(WorkspaceIntent.RetryMidiCleanup); advanceUntilIdle()
        assertEquals(app.melotrail.arrangement.MidiCleanupProfile.TRANSCRIPTION_SAFE, service.midiCleanupRetry?.cleanup?.profile)
        viewModel.accept(WorkspaceIntent.SelectMidiCleanupProfile(app.melotrail.arrangement.MidiCleanupProfile.TRANSCRIPTION_SAFE))
        viewModel.accept(WorkspaceIntent.RetryMidiCleanup); advanceUntilIdle()
        assertEquals(app.melotrail.arrangement.MidiCleanupProfile.TRANSCRIPTION_SAFE, service.midiCleanupRetry?.cleanup?.profile)
        assertTrue(viewModel.state.value.downstreamArtifactsStale)
        assertNull(viewModel.state.value.arrangement)

        viewModel.accept(WorkspaceIntent.SelectMidiCleanupProfile(app.melotrail.arrangement.MidiCleanupProfile.TIGHTEN_TIMING))
        viewModel.accept(WorkspaceIntent.RetryMidiCleanup)
        assertEquals(WorkspaceDialog.ConfirmTightenTiming("A"), viewModel.state.value.dialog)
        assertEquals(app.melotrail.arrangement.MidiCleanupProfile.TRANSCRIPTION_SAFE, service.midiCleanupRetry?.cleanup?.profile)
        viewModel.accept(WorkspaceIntent.ConfirmTightenTiming); advanceUntilIdle()
        val options = checkNotNull(service.midiCleanupRetry).cleanup
        assertEquals(app.melotrail.arrangement.MidiCleanupProfile.TIGHTEN_TIMING, options.profile)
        assertEquals("1/16", options.quantize)
        assertEquals(0.4, options.strength)
        viewModel.close()
    }

    @Test
    fun `Lo-fi Feel is an opt-in canonical analysis choice and invalidates downstream state`() = runTest {
        val root = Path.of("build/lofi-feel-project")
        val quality = app.melotrail.application.MidiQualitySummary(app.melotrail.application.MidiQualityStatus.CURRENT, app.melotrail.arrangement.MidiCleanupOptions())
        val snapshot = projectSnapshot(root).copy(parts = listOf(part("A").copy(preparation = part("A").preparation.copy(midiQuality = quality))))
        val service = FakeProjectService(result = snapshot)
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A"))
        viewModel.accept(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL)); advanceUntilIdle()
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL, viewModel.state.value.pendingMidiFeel)
        viewModel.accept(WorkspaceIntent.ApplyMidiFeelAndReanalyze); advanceUntilIdle()

        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL, service.midiFeelSelection?.input)
        assertTrue(viewModel.state.value.downstreamArtifactsStale)
        assertNull(viewModel.state.value.arrangement)
        viewModel.accept(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.REPAIRED)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ApplyMidiFeelAndReanalyze); advanceUntilIdle()
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.REPAIRED, service.midiFeelSelection?.input)
        viewModel.close()
    }

    @Test
    fun `stale MIDI quality retry failure remains actionable`() = runTest {
        val root = Path.of("build/stale-quality-project")
        val stale = app.melotrail.application.MidiQualitySummary(app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID)
        val snapshot = projectSnapshot(root).copy(parts = listOf(part("A").copy(preparation = part("A").preparation.copy(midiQuality = stale))))
        val service = FakeProjectService(result = snapshot, failureOnMidiCleanup = IllegalStateException("worker unavailable"))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A"))
        viewModel.accept(WorkspaceIntent.RetryMidiCleanup); advanceUntilIdle()

        assertEquals("worker unavailable", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)
        assertIs<WorkspaceRetry.MidiCleanup>(viewModel.state.value.retry)
        viewModel.close()
    }

    @Test
    fun `guided import detects supported types and cancellation preserves the draft`() = runTest {
        val root = Path.of("build/import-project")
        val viewModel = WorkspaceViewModel(FakeProjectService(result = projectSnapshot(root)), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))
        viewModel.accept(WorkspaceIntent.ImportSourceChosen(Path.of("recording.MP3")))

        val selected = assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog)
        assertEquals(ImportSourceKind.MP3, selected.detectedType)
        assertTrue(selected.audio)
        viewModel.accept(WorkspaceIntent.ImportSourceChosen(null))
        assertEquals(selected, viewModel.state.value.dialog)
        assertEquals(ImportSourceKind.MIDI, detectImportSourceKind(Path.of("intro.midi")))
        assertEquals(ImportSourceKind.WAV, detectImportSourceKind(Path.of("intro.wave")))
        viewModel.close()
    }

    @Test
    fun `dropped source enters the same import draft and remains unimported until confirmation`() = runTest {
        val root = Path.of("build/dropped-import-project")
        val service = FakeProjectService(result = projectSnapshot(root))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()

        viewModel.accept(WorkspaceIntent.ImportSourceChosen(Path.of("dropped-song.wav")))

        val draft = assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog)
        assertEquals(ImportSourceKind.WAV, draft.detectedType)
        assertEquals("dropped-song", draft.id)
        assertNull(service.imported)
        viewModel.close()
    }

    @Test
    fun `guided import rejects unsupported source and duplicate ID before service work`() = runTest {
        val root = Path.of("build/import-project")
        val service = FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(part("A"))))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))
        viewModel.accept(WorkspaceIntent.ImportSourceChosen(Path.of("intro.txt")))
        viewModel.accept(WorkspaceIntent.UpdateImportPart(assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog).copy(id = "A")))
        viewModel.accept(WorkspaceIntent.ImportPart)
        assertTrue(assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message.contains("Unsupported source type"))
        assertTrue(assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog).validationMessage!!.contains("Unsupported source type"))
        assertNull(service.imported)

        viewModel.accept(WorkspaceIntent.UpdateImportPart(WorkspaceDialog.ImportPart(false, Path.of("intro.mid"), "A", "verse", ImportSourceKind.MIDI)))
        viewModel.accept(WorkspaceIntent.ImportPart)
        assertTrue(assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message.contains("Part ID already exists: A"))
        assertNull(service.imported)
        viewModel.close()
    }

    @Test
    fun `audio import checks worker and transcription prerequisites only on confirmation`() = runTest {
        val root = Path.of("build/import-project")
        val unavailable = RuntimeReadiness.of(
            RuntimeDependency.WORKER to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Start the Python worker with make worker."),
            RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Transcription needs the running Python worker."),
            RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
        )
        val service = FakeProjectService(result = projectSnapshot(root))
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), runtimeReadinessService = RuntimeReadinessService { unavailable })
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))
        viewModel.accept(WorkspaceIntent.ImportSourceChosen(Path.of("solo.wav")))
        viewModel.accept(WorkspaceIntent.UpdateImportPart(assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog).copy(id = "A")))
        viewModel.accept(WorkspaceIntent.ImportPart)

        assertEquals("Start the Python worker with make worker.", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)
        assertNull(service.imported)
        viewModel.close()
    }

    @Test
    fun `validates planner inputs then generates an approved deterministic arrangement`() = runTest {
        val root = Path.of("build/arrangement-project")
        val project = projectSnapshot(root).copy(
            parts = listOf(analyzedPart("A")),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
        )
        val arrangement = arrangementSnapshot(root)
        val service = FakeArrangementService(generated = arrangement)
        val viewModel = WorkspaceViewModel(FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), arrangementService = service)

        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ToggleArrangementInstrument("bass"))
        viewModel.accept(WorkspaceIntent.UpdateArrangementStyle("warm lo-fi"))
        viewModel.accept(WorkspaceIntent.GenerateArrangement)
        advanceUntilIdle()

        assertEquals(ArrangementPlannerKind.DETERMINISTIC, service.generatedRequest?.planner)
        assertEquals(listOf("piano", "bass"), service.generatedRequest?.instruments)
        assertEquals("warm lo-fi", service.generatedRequest?.style)
        assertEquals(arrangement, viewModel.state.value.arrangement)
        assertEquals(0, viewModel.state.value.selectedArrangementSection)
        viewModel.accept(WorkspaceIntent.SelectArrangementSection(null))
        assertNull(viewModel.state.value.selectedArrangementSection)

        viewModel.accept(WorkspaceIntent.UpdateArrangementStyle("x".repeat(161)))
        viewModel.accept(WorkspaceIntent.GenerateArrangement)
        assertEquals("Style must be at most 160 characters.", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)
        viewModel.close()
    }

    @Test
    fun `Qwen draft remains reviewable until explicit approval and reopens from artifacts`() = runTest {
        val root = Path.of("build/qwen-project")
        val project = projectSnapshot(root).copy(
            parts = listOf(analyzedPart("A")),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
        )
        val draft = arrangementSnapshot(root, approvalRequired = true, approved = false)
        val approved = arrangementSnapshot(root)
        val service = FakeArrangementService(loaded = approved, generated = draft, approved = approved)
        val viewModel = WorkspaceViewModel(FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), arrangementService = service)

        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()
        assertEquals(approved, viewModel.state.value.arrangement)
        viewModel.accept(WorkspaceIntent.UpdateArrangementPlanner(ArrangementPlannerKind.QWEN))
        viewModel.accept(WorkspaceIntent.GenerateArrangement)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.arrangement!!.approvalRequired)
        viewModel.accept(WorkspaceIntent.PreviewArrangement)
        advanceUntilIdle()
        assertEquals(1, service.previewCalls)
        viewModel.accept(WorkspaceIntent.ApproveArrangement)
        advanceUntilIdle()
        assertEquals(1, service.approveCalls)
        assertTrue(viewModel.state.value.arrangement!!.approved)
        viewModel.close()
    }

    @Test
    fun `invalid Qwen response remains an actionable generation failure`() = runTest {
        val root = Path.of("build/invalid-qwen-project")
        val project = projectSnapshot(root).copy(
            parts = listOf(analyzedPart("A")),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
        )
        val service = FakeArrangementService(failureOnGenerate = IllegalArgumentException("Qwen response is not valid arrangement JSON"))
        val viewModel = WorkspaceViewModel(FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), arrangementService = service)

        viewModel.accept(WorkspaceIntent.OpenProject(root))
        advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.UpdateArrangementPlanner(ArrangementPlannerKind.QWEN))
        viewModel.accept(WorkspaceIntent.GenerateArrangement)
        advanceUntilIdle()

        assertEquals("Qwen response is not valid arrangement JSON", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)
        assertIs<WorkspaceRetry.GenerateArrangement>(viewModel.state.value.retry)
        viewModel.close()
    }

    @Test
    fun `build dispatch remains gated by a Qwen draft without implicit approval`() = runTest {
        val root = Path.of("build/build-workspace-project")
        val project = projectSnapshot(root)
        val draft = arrangementSnapshot(root, approvalRequired = true, approved = false)
        val build = FakeBuildService()
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, arrangementService = FakeArrangementService(loaded = draft), buildService = build
        )

        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.BuildSong)
        assertEquals(0, build.calls)
        assertEquals("Build Song requires a current approved arrangement.", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)

        viewModel.close()
    }

    @Test
    fun `Build Song reports completion only after the build service returns and preserves failure recovery`() = runTest {
        val root = Path.of("build/task-087-build")
        val project = projectSnapshot(root)
        val build = FakeBuildService()
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, arrangementService = FakeArrangementService(loaded = arrangementSnapshot(root)), buildService = build
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.UpdateBuildOptions(BuildOptionsDraft(loFi = true, mp3 = true)))
        viewModel.accept(WorkspaceIntent.BuildSong); advanceUntilIdle()

        assertEquals(1, build.calls)
        assertEquals(BuildSongRequest(root, enableLoFi = true, enableMp3 = true), build.request)
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        assertTrue(viewModel.state.value.notification.orEmpty().startsWith("Build complete:"))
        viewModel.close()

        val failedBuild = FakeBuildService(failure = IllegalStateException("Worker disconnected"))
        val failingViewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, arrangementService = FakeArrangementService(loaded = arrangementSnapshot(root)), buildService = failedBuild
        )
        failingViewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        failingViewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        failingViewModel.accept(WorkspaceIntent.BuildSong); advanceUntilIdle()
        assertEquals("Worker disconnected", assertIs<WorkspaceOperation.Failed>(failingViewModel.state.value.operation).message)
        assertEquals(OperationSeverity.ERROR, failingViewModel.state.value.operationFeedback.outcomeSeverity)
        failingViewModel.close()
    }

    @Test
    fun `master volume remains on the existing shared playback session`() = runTest {
        val player = FakeArtifactAudioPlayer()
        val viewModel = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), player = player)

        viewModel.accept(WorkspaceIntent.SetPlaybackVolume(0.4))

        assertEquals(0.4, viewModel.state.value.playbackSession.volume)
        assertEquals(0.4, player.getVolume())
        assertEquals(0L, viewModel.state.value.playbackSession.id)
        viewModel.close()
    }

    @Test
    fun `sound library selection updates settings state and cancellation keeps last valid root`() = runTest {
        val root = java.nio.file.Files.createTempDirectory("desktop-library")
        val preferences = object : DesktopPreferences {
            var library: Path? = null
            override fun lastOpenedProject(): Path? = null
            override fun saveLastOpenedProject(root: Path) = Unit
            override fun clearLastOpenedProject() = Unit
            override fun soundLibraryRoot(): Path? = library
            override fun saveSoundLibraryRoot(root: Path) { library = root }
            override fun clearSoundLibraryRoot() { library = null }
        }
        val settings = SoundLibrarySettingsService(preferences, app.melotrail.arrangement.SoundLibraryLocator(emptyMap()), SoundLibraryValidator { Result.success(Unit) }, environment = emptyMap())
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(root)), FakeFileDialogs(libraryRoot = root), testDispatchers(StandardTestDispatcher(testScheduler)),
            preferences = preferences, soundLibrarySettings = settings
        )

        viewModel.accept(WorkspaceIntent.ShowSoundLibrarySettings)
        viewModel.accept(WorkspaceIntent.ChooseSoundLibraryRoot)
        advanceUntilIdle()
        assertEquals(root, viewModel.state.value.soundLibrary.resolvedRoot)
        viewModel.accept(WorkspaceIntent.ChooseSoundLibraryRoot) // chooser cancellation
        advanceUntilIdle()
        assertEquals(root, viewModel.state.value.soundLibrary.resolvedRoot)
        viewModel.close()
    }

    @Test
    fun `timeline weights preserve duration proportions with a safe unknown-duration fallback`() {
        assertEquals(2f, timelineSectionWeight(2.0))
        assertEquals(6f, timelineSectionWeight(6.0))
        assertEquals(1f, timelineSectionWeight(null))
        assertEquals(1f, timelineSectionWeight(0.0))
    }

    @Test
    fun `readiness refresh failure is visible and guided import remains available`() = runTest {
        val root = Path.of("build/readiness-project")
        val unavailable = RuntimeReadiness.of(
            RuntimeDependency.WORKER to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Start the Python worker with make worker."),
            RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Transcription needs the running Python worker."),
            RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
        )
        val readiness = object : RuntimeReadinessService { override suspend fun check() = unavailable }
        val viewModel = WorkspaceViewModel(FakeProjectService(result = projectSnapshot(root)), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), runtimeReadinessService = readiness)
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = true))
        assertIs<WorkspaceDialog.ImportPart>(viewModel.state.value.dialog)
        assertEquals(WorkspaceOperation.Idle, viewModel.state.value.operation)
        viewModel.close()

        val failed = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), runtimeReadinessService = RuntimeReadinessService { throw IllegalStateException("probe failed") })
        failed.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        assertTrue(failed.state.value.notification!!.contains("probe failed"))
        failed.close()
    }

    @Test
    fun `preview exposes resolver and player lifecycle without success notifications`() = runTest {
        val root = Path.of("build/preview-project")
        val player = FakeArtifactAudioPlayer()
        val previews = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/A.wav"), emptyList(), false))
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(part("A")))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = player, partPreviewService = previews
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPart("A")); advanceUntilIdle()

        assertEquals(PreviewPhase.PLAYING, viewModel.state.value.preview.phase)
        assertEquals("A", viewModel.state.value.preview.source?.partId)
        assertEquals(root.resolve("previews/A.wav"), viewModel.state.value.preview.source?.artifact)
        assertEquals("Opened test-project", viewModel.state.value.notification)
        assertNull(viewModel.state.value.preview.reason)
        viewModel.accept(WorkspaceIntent.SeekPreview(1.5))
        assertEquals(1.5, viewModel.state.value.preview.elapsedSeconds)
        viewModel.accept(WorkspaceIntent.PausePreview)
        assertEquals(PreviewPhase.PAUSED, viewModel.state.value.preview.phase)
        viewModel.accept(WorkspaceIntent.ResumePreview)
        advanceUntilIdle()
        assertEquals(PreviewPhase.PLAYING, viewModel.state.value.preview.phase)
        player.emit(PlaybackState.STOPPED)
        advanceUntilIdle()
        assertEquals(PreviewPhase.STOPPED, viewModel.state.value.preview.phase)
        viewModel.close()
        assertTrue(player.closed)
    }

    @Test
    fun `preview failures retry and project switches discard stale callbacks`() = runTest {
        val first = Path.of("build/first-preview-project")
        val second = Path.of("build/second-preview-project")
        val previews = FakePreviewService(PreviewResult.Prerequisite(app.melotrail.application.PreviewStage.VALIDATE, "Analyze A before previewing it."))
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(first).copy(parts = listOf(part("A")))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = FakeArtifactAudioPlayer(), partPreviewService = previews
        )
        viewModel.accept(WorkspaceIntent.OpenProject(first)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPart("A")); advanceUntilIdle()
        assertEquals(PreviewPhase.FAILED, viewModel.state.value.preview.phase)
        assertEquals("Analyze A before previewing it.", viewModel.state.value.preview.reason)
        previews.result = PreviewResult.Resolved(first.resolve("previews/A.wav"), emptyList(), true)
        viewModel.accept(WorkspaceIntent.RetryPreview); advanceUntilIdle()
        assertEquals(2, previews.calls)

        previews.delay = true
        viewModel.accept(WorkspaceIntent.PreviewPart("A"))
        runCurrent()
        viewModel.accept(WorkspaceIntent.OpenProject(second)); advanceUntilIdle()
        previews.release()
        advanceUntilIdle()
        assertEquals(PreviewPhase.STOPPED, viewModel.state.value.preview.phase)
        assertNull(viewModel.state.value.preview.source)
        viewModel.close()
    }

    @Test
    fun `prepared preview retry and shared transport retain one exact selection`() = runTest {
        val root = Path.of("build/prepared-session-project")
        val project = projectSnapshot(root).copy(parts = listOf(audioPart("A")))
        val previews = FakePreviewService(PreviewResult.Prerequisite(app.melotrail.application.PreviewStage.VALIDATE, "Prepared audio is stale."))
        val player = FakeArtifactAudioPlayer()
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = player, partPreviewService = previews
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A")); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPreparation(PreviewAudioSource.PREPARED_CLEAN)); advanceUntilIdle()
        assertEquals(PlaybackSessionPhase.FAILED, viewModel.state.value.playbackSession.phase)
        assertEquals(PlaybackFailureStage.RESOLUTION, viewModel.state.value.playbackSession.failureStage)

        previews.result = PreviewResult.Resolved(root.resolve("prepared/A/clean.wav"), emptyList(), true)
        viewModel.accept(WorkspaceIntent.RetryPreview); advanceUntilIdle()
        assertEquals(PreviewAudioSource.PREPARED_CLEAN, previews.lastRequest?.audioSource)
        assertEquals(PlaybackSourceKind.PREPARED_AUDIO, viewModel.state.value.playbackSession.sourceKind)
        assertEquals(PlaybackSessionPhase.PLAYING, viewModel.state.value.playbackSession.phase)

        viewModel.accept(WorkspaceIntent.StopPlayback)
        assertEquals(PlaybackSessionPhase.STOPPED, viewModel.state.value.playbackSession.phase)
        assertEquals("A", (viewModel.state.value.playbackSession.request as PlaybackRequest.Part).partId)
        viewModel.close()
    }

    @Test
    fun `replacing a mix session with a part preview cannot leave two logical sources active`() = runTest {
        val root = Path.of("build/unified-session-project")
        val project = projectSnapshot(root).copy(
            parts = listOf(part("A")),
            readiness = projectSnapshot(root).readiness.copy(dryMixAvailable = true)
        )
        val player = FakeArtifactAudioPlayer()
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = player,
            partPreviewService = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/A.wav"), emptyList(), true))
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.DRY))
        viewModel.accept(WorkspaceIntent.PlayPause); advanceUntilIdle()
        assertEquals(PlaybackSourceKind.DRY_MIX, viewModel.state.value.playbackSession.sourceKind)
        viewModel.accept(WorkspaceIntent.PreviewPart("A")); advanceUntilIdle()
        assertEquals(PlaybackSourceKind.MIDI, viewModel.state.value.playbackSession.sourceKind)
        assertIs<PlaybackRequest.Part>(viewModel.state.value.playbackSession.request)
        assertEquals(PlaybackSessionPhase.PLAYING, viewModel.state.value.playbackSession.phase)
        assertEquals(1, player.maxActive)
        viewModel.close()
    }

    @Test
    fun `a delayed callback from a replaced preview session cannot overwrite the new source`() = runTest {
        val root = Path.of("build/replaced-preview-session-project")
        val previews = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/current.wav"), emptyList(), true)).also { it.delay = true }
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(part("A"), part("B")))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = FakeArtifactAudioPlayer(), partPreviewService = previews
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPart("A")); runCurrent()
        viewModel.accept(WorkspaceIntent.PreviewPart("B")); runCurrent()
        previews.release(); advanceUntilIdle()

        assertEquals("B", (viewModel.state.value.playbackSession.request as PlaybackRequest.Part).partId)
        assertEquals(PlaybackSessionPhase.PLAYING, viewModel.state.value.playbackSession.phase)
        viewModel.close()
    }

    @Test
    fun `WAV MP3 and MIDI previews resolve to playing and a device failure is actionable`() = runTest {
        listOf("wav", "mp3", "midi").forEach { kind ->
            val root = Path.of("build/$kind-preview-project")
            val player = FakeArtifactAudioPlayer()
            val viewModel = WorkspaceViewModel(
                FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(part("A")))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
                runtimeReadinessService = ReadyReadinessService, player = player,
                partPreviewService = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/A.$kind.wav"), emptyList(), false))
            )
            viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
            viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
            viewModel.accept(WorkspaceIntent.PreviewPart("A")); advanceUntilIdle()
            assertEquals(PreviewPhase.PLAYING, viewModel.state.value.preview.phase, kind)
            viewModel.close()
        }

        val root = Path.of("build/device-preview-project")
        val player = FakeArtifactAudioPlayer(startFailure = "Audio output could not be started. Check the selected output device and retry.")
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = projectSnapshot(root).copy(parts = listOf(part("A")))), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = player,
            partPreviewService = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/A.wav"), emptyList(), false))
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPart("A")); advanceUntilIdle()
        assertEquals(PreviewPhase.FAILED, viewModel.state.value.preview.phase)
        assertTrue(viewModel.state.value.preview.reason!!.contains("selected output device"))
        viewModel.close()
    }

    @Test
    fun `audio preparation keeps inspect only selected until measured cleanup is explicitly confirmed`() = runTest {
        val root = Path.of("build/preparation-project")
        val project = projectSnapshot(root).copy(parts = listOf(audioPart("A")))
        val preparation = FakeAudioPreparationService(project, availablePreparation("A", recommended = true))
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, audioPreparationService = preparation
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A")); advanceUntilIdle()
        assertEquals(app.melotrail.preparation.InputCleanupMode.INSPECT_ONLY, viewModel.state.value.audioPreparation.cleanupMode)
        viewModel.accept(WorkspaceIntent.SelectCleanupMode(app.melotrail.preparation.InputCleanupMode.SAFE_CLEANUP))
        viewModel.accept(WorkspaceIntent.ApplySelectedCleanup)
        assertIs<WorkspaceDialog.ConfirmSafeCleanup>(viewModel.state.value.dialog)
        assertNull(preparation.cleanup)
        viewModel.accept(WorkspaceIntent.ConfirmSafeCleanup); advanceUntilIdle()
        assertEquals(true, preparation.cleanup?.confirmedSafeCleanup)
        viewModel.close()
    }

    @Test
    fun `preparation A B and transcription selections use bounded artifact identifiers and retry failures`() = runTest {
        val root = Path.of("build/preparation-preview-project")
        val project = projectSnapshot(root).copy(parts = listOf(audioPart("A")))
        val preparation = FakeAudioPreparationService(project, availablePreparation("A", recommended = true), transcriptionFailure = IllegalStateException("model runtime unavailable"))
        val previews = FakePreviewService(PreviewResult.Resolved(root.resolve("previews/A.wav"), emptyList(), true))
        val viewModel = WorkspaceViewModel(
            FakeProjectService(result = project), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)),
            runtimeReadinessService = ReadyReadinessService, player = FakeArtifactAudioPlayer(), partPreviewService = previews,
            audioPreparationService = preparation
        )
        viewModel.accept(WorkspaceIntent.OpenProject(root)); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.SelectPart("A")); advanceUntilIdle()
        viewModel.accept(WorkspaceIntent.PreviewPreparation(app.melotrail.application.PreviewAudioSource.PREPARED_CLEAN)); advanceUntilIdle()
        assertEquals(app.melotrail.application.PreviewAudioSource.PREPARED_CLEAN, previews.lastRequest?.audioSource)
        viewModel.accept(WorkspaceIntent.SelectTranscriptionInput(app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV))
        viewModel.accept(WorkspaceIntent.TranscribeSelectedPart); advanceUntilIdle()
        assertTrue(viewModel.state.value.retry is WorkspaceRetry.Transcribe)
        preparation.transcriptionFailure = null
        viewModel.accept(WorkspaceIntent.Retry); advanceUntilIdle()
        assertEquals(app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV, preparation.transcribedInput)
        viewModel.close()
    }

    private fun testDispatchers(dispatcher: TestDispatcher): WorkspaceDispatchers {
        return WorkspaceDispatchers(ui = dispatcher, io = dispatcher)
    }

    private fun projectSnapshot(root: Path) = ProjectSnapshot(
        root = root,
        version = 2,
        name = "test-project",
        renderFormat = null,
        parts = emptyList(),
        structure = emptyList(),
        readiness = app.melotrail.application.ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
    )

    private fun part(id: String) = app.melotrail.application.PartSummary(
        id = id,
        role = "",
        sourceFile = "source/$id.mid",
        sourceName = "$id.mid",
        sourceType = app.melotrail.application.PartSourceType.MIDI,
        analysis = null
    )

    private fun preparation(
        rawMidi: Boolean = false,
        quality: app.melotrail.application.MidiQualityStatus = app.melotrail.application.MidiQualityStatus.LEGACY_UNKNOWN
    ) = app.melotrail.application.PartPreparationSummary(
        sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = rawMidi, cleanMidi = quality == app.melotrail.application.MidiQualityStatus.CURRENT,
        analyzed = false, ready = false, warnings = emptyList(), midiQuality = app.melotrail.application.MidiQualitySummary(quality)
    )

    private fun audioPart(id: String) = part(id).copy(sourceFile = "source/$id.wav", sourceName = "$id.wav", sourceType = app.melotrail.application.PartSourceType.AUDIO)

    private fun analyzedPart(id: String) = part(id).copy(analysis = app.melotrail.application.PartAnalysisSummary(
        app.melotrail.application.PartAnalysisStatus.MIDI, "analysis/$id.midi.json", bars = 2, durationSeconds = 4.0, key = "C major"
    ))

    private fun arrangementSnapshot(root: Path, approvalRequired: Boolean = false, approved: Boolean = true) = ArrangementSnapshot(
        root = root,
        sections = listOf(ArrangementSectionSnapshot(0, "A1", "A", "introduction", 0.4, listOf(
            app.melotrail.application.ArrangementInstrumentSnapshot("piano", "source", null, null),
            app.melotrail.application.ArrangementInstrumentSnapshot("bass", "generated", "bass", 0.4)
        ), "none", 4.0)),
        approvalRequired = approvalRequired,
        approved = approved,
        stale = false,
        artifact = root.resolve(if (approvalRequired) "arrangement.draft.json" else "arrangement.json")
    )
}

private class FakeFileDialogs(libraryRoot: Path? = null) : DesktopFileDialogs {
    private var nextLibraryRoot = libraryRoot
    override suspend fun chooseProjectDirectory(): Path? = null
    override suspend fun chooseNewProjectDirectory(): Path? = null
    override suspend fun choosePartSource(): Path? = null
    override suspend fun chooseSoundLibraryDirectory(): Path? = nextLibraryRoot.also { nextLibraryRoot = null }
}

private object ReadyReadinessService : RuntimeReadinessService {
    override suspend fun check(): RuntimeReadiness = RuntimeReadiness.of(
        RuntimeDependency.WORKER to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
    )
}

private class FakePreviewService(var result: PreviewResult) : PartPreviewApplicationService {
    var calls = 0
    var delay = false
    var lastRequest: PreviewRequest? = null
    private var gate: CompletableDeferred<Unit>? = null
    override suspend fun resolve(request: PreviewRequest): PreviewResult {
        calls++
        lastRequest = request
        if (delay) (gate ?: CompletableDeferred<Unit>().also { gate = it }).await()
        return result
    }
    fun release() { gate?.complete(Unit) }
}

private data class CleanupCall(val mode: app.melotrail.preparation.InputCleanupMode, val confirmedSafeCleanup: Boolean)

private class FakeAudioPreparationService(
    private val project: ProjectSnapshot,
    private val snapshot: app.melotrail.application.AudioPreparationSnapshot,
    var transcriptionFailure: Throwable? = null
) : app.melotrail.application.AudioPreparationApplicationService {
    var cleanup: CleanupCall? = null
    var transcribedInput: app.melotrail.preparation.TranscriptionInputArtifact? = null

    override fun load(projectRoot: Path, partId: String) = snapshot
    override suspend fun inspect(projectRoot: Path, partId: String, progress: app.melotrail.application.ProgressSink) =
        app.melotrail.application.AudioPreparationOperation(project, snapshot)
    override suspend fun applyCleanup(projectRoot: Path, partId: String, mode: app.melotrail.preparation.InputCleanupMode, confirmedSafeCleanup: Boolean): app.melotrail.application.AudioPreparationOperation {
        cleanup = CleanupCall(mode, confirmedSafeCleanup)
        return app.melotrail.application.AudioPreparationOperation(project, snapshot)
    }
    override suspend fun transcribe(projectRoot: Path, partId: String, selectedInput: app.melotrail.preparation.TranscriptionInputArtifact): app.melotrail.application.AudioPreparationOperation {
        transcriptionFailure?.let { throw it }
        transcribedInput = selectedInput
        return app.melotrail.application.AudioPreparationOperation(project, snapshot)
    }
}

private fun availablePreparation(partId: String, recommended: Boolean): app.melotrail.application.AudioPreparationSnapshot {
    val source = app.melotrail.preparation.InspectionSourceIdentity("source/$partId.wav", "0".repeat(64))
    val measurements = app.melotrail.preparation.AudioInspectionMeasurements(
        peak = 0.8, rms = 0.2, dcOffset = 0.01, clippedRunCount = 0, clippedFrameCount = 0,
        silence = app.melotrail.preparation.SilenceEvidence(0, 0),
        hum = app.melotrail.preparation.SignalIndicator(app.melotrail.preparation.EvidenceLevel.NONE, 0.0),
        noise = app.melotrail.preparation.SignalIndicator(app.melotrail.preparation.EvidenceLevel.NONE, 0.0)
    )
    val report = app.melotrail.preparation.InputInspectionReport(
        partId = partId, source = source,
        detectedInput = app.melotrail.preparation.DetectedInput(app.melotrail.preparation.InputContainer.RIFF_WAVE, "pcm", "wav"),
        durationSeconds = 1.0, audioFormat = app.melotrail.preparation.DetectedAudioFormat(44100, 1, 24), measurements = measurements
    )
    val plan = if (!recommended) null else app.melotrail.preparation.InputCleanupPlan(
        partId = partId, source = source, mode = app.melotrail.preparation.InputCleanupMode.SAFE_CLEANUP,
        operations = listOf(app.melotrail.preparation.CleanupPlanOperation(app.melotrail.preparation.CleanupOperationType.DC_REMOVAL)),
        evidence = measurements, confidence = 0.5, transcriptionInput = app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV
    )
    return app.melotrail.application.AudioPreparationSnapshot(partId, app.melotrail.application.AudioPreparationAvailability.AVAILABLE, report, plan)
}

private class FakeArtifactAudioPlayer(private val startFailure: String? = null) : ArtifactAudioPlayer {
    private val mutableState = MutableStateFlow(PlaybackState.STOPPED)
    private val position = MutableStateFlow(0.0)
    private val duration = MutableStateFlow(3.0)
    private val volumeState = MutableStateFlow(1.0)
    override val state: StateFlow<PlaybackState> = mutableState
    override val currentPosition: StateFlow<Double> = position
    override val totalDuration: StateFlow<Double> = duration
    override val volume: StateFlow<Double> = volumeState
    override val failure: StateFlow<PlaybackFailure?> = MutableStateFlow(null)
    var closed = false
    var maxActive = 0
    private var active = 0

    override suspend fun prepare(path: Path): PlaybackPrepareResult = PlaybackPrepareResult.Ready(duration.value)
    override suspend fun start(): PlaybackStartResult {
        startFailure?.let { return PlaybackStartResult.Failed(PlaybackFailure(PlaybackFailureStage.DEVICE_START, it, IllegalStateException(it))) }
        active = 1
        maxActive = maxOf(maxActive, active)
        mutableState.value = PlaybackState.PLAYING
        return PlaybackStartResult.Started
    }
    override suspend fun play(path: Path): PlaybackStartResult { prepare(path); return start() }
    override fun play(buffer: AudioBuffer) = Unit
    override fun pause() { mutableState.value = PlaybackState.PAUSED }
    override fun resume() { mutableState.value = PlaybackState.PLAYING }
    override fun stop() { active = 0; mutableState.value = PlaybackState.STOPPED; position.value = 0.0 }
    override fun seek(position: Double) { this.position.value = position.coerceIn(0.0, duration.value) }
    override fun setVolume(volume: Double) { volumeState.value = volume }
    override fun getVolume(): Double = volumeState.value
    override fun close() { closed = true; stop() }
    fun emit(value: PlaybackState) { mutableState.value = value }
}

private class FakeDesktopPreferences(private val last: Path?) : DesktopPreferences {
    var saved: Path? = null
    override fun lastOpenedProject(): Path? = last
    override fun saveLastOpenedProject(root: Path) { saved = root }
    override fun clearLastOpenedProject() = Unit
    override fun soundLibraryRoot(): Path? = null
    override fun saveSoundLibraryRoot(root: Path) = Unit
    override fun clearSoundLibraryRoot() = Unit
}

private class FakeProjectService(
    private val result: ProjectSnapshot? = null,
    private val failure: Throwable? = null,
    private val failureOnImport: Throwable? = null,
    private val failureOnMidiCleanup: Throwable? = null
) : ProjectApplicationService {
    private var current: ProjectSnapshot? = result
    var created: CreateProjectRequest? = null
    var imported: ImportPartRequest? = null
    var midiCleanupRetry: app.melotrail.application.RetryMidiCleanupRequest? = null
    var prepared: PrepareMidiRequest? = null
    var midiFeelSelection: app.melotrail.application.SelectMidiFeelRequest? = null
    var analyzed: AnalyzePartRequest? = null
    var updatedRole: UpdatePartRoleRequest? = null
    var savedStructure: SaveStructureRequest? = null
    var preparedResult: ProjectSnapshot? = null
    var failureOnSave: Throwable? = null
    var openCalls = 0

    override fun open(root: Path): ProjectSnapshot {
        openCalls++
        failure?.let { throw it }
        return checkNotNull(current)
    }

    override fun create(request: CreateProjectRequest): ProjectSnapshot {
        created = request
        return checkNotNull(current)
    }

    override suspend fun importPart(request: ImportPartRequest, progress: app.melotrail.application.ProgressSink): ProjectSnapshot {
        imported = request
        failureOnImport?.let { throw it }
        progress.report(app.melotrail.application.OperationProgress("import-part", 2, 4, "Cleaning MIDI"))
        return checkNotNull(current)
    }

    override suspend fun retryMidiCleanup(
        request: app.melotrail.application.RetryMidiCleanupRequest,
        progress: app.melotrail.application.ProgressSink
    ): ProjectSnapshot {
        midiCleanupRetry = request
        failureOnMidiCleanup?.let { throw it }
        progress.report(app.melotrail.application.OperationProgress("retry-midi-cleanup", 2, 3, "Saving quality report"))
        return checkNotNull(current)
    }

    override suspend fun prepareMidi(request: PrepareMidiRequest, progress: app.melotrail.application.ProgressSink): PrepareMidiResult {
        prepared = request
        progress.report(app.melotrail.application.OperationProgress("prepare-midi", 2, 2, "Analyzing selected MIDI"))
        current = preparedResult ?: current
        return PrepareMidiResult(checkNotNull(current), PrepareMidiOutcome.READY_FOR_STRUCTURE)
    }

    override fun approveMidiRepair(root: Path, partId: String): ProjectSnapshot = checkNotNull(current)

    override fun selectMidiFeel(request: app.melotrail.application.SelectMidiFeelRequest): ProjectSnapshot {
        midiFeelSelection = request
        return checkNotNull(current)
    }

    override suspend fun inspectPart(
        request: app.melotrail.application.InspectPartRequest,
        progress: app.melotrail.application.ProgressSink
    ): ProjectSnapshot = checkNotNull(current)

    override suspend fun analyzePart(request: AnalyzePartRequest, progress: app.melotrail.application.ProgressSink): ProjectSnapshot {
        analyzed = request
        return checkNotNull(current)
    }

    override fun updatePart(request: UpdatePartRoleRequest): ProjectSnapshot {
        updatedRole = request
        return checkNotNull(current)
    }

    override fun saveStructure(request: SaveStructureRequest): ProjectSnapshot {
        savedStructure = request
        failureOnSave?.let { throw it }
        current = checkNotNull(current).copy(structure = request.partIds.mapIndexed { index, id ->
            app.melotrail.application.StructureSectionSummary(index, id, request.partIds.take(index + 1).count { it == id }, "$id${request.partIds.take(index + 1).count { it == id }}", null)
        })
        return checkNotNull(current)
    }
}

private class FakeArrangementService(
    private val loaded: ArrangementSnapshot? = null,
    private val generated: ArrangementSnapshot? = null,
    private val approved: ArrangementSnapshot? = null,
    private val failureOnGenerate: Throwable? = null
) : ArrangementApplicationService {
    var generatedRequest: GenerateArrangementRequest? = null
    var previewCalls = 0
    var approveCalls = 0

    override suspend fun generate(request: GenerateArrangementRequest, progress: app.melotrail.application.ProgressSink): ArrangementSnapshot {
        generatedRequest = request
        failureOnGenerate?.let { throw it }
        progress.report(app.melotrail.application.OperationProgress("arrange", 2, 3, "Creating reviewed song plan"))
        return checkNotNull(generated)
    }

    override suspend fun generateRequiredMidi(root: Path, progress: app.melotrail.application.ProgressSink): GeneratedMidiSnapshot = GeneratedMidiSnapshot(emptyList())

    override suspend fun renderApprovedStems(
        root: Path,
        renderer: app.melotrail.arrangement.InstrumentRenderer,
        progress: app.melotrail.application.ProgressSink
    ): app.melotrail.arrangement.StemRenderResult = error("Not used by this fake")

    override fun load(root: Path): ArrangementSnapshot = loaded ?: throw IllegalStateException("No detailed arrangement found")

    override fun preview(root: Path): ArrangementSnapshot {
        previewCalls++
        return checkNotNull(generated)
    }

    override fun approve(root: Path): ArrangementSnapshot {
        approveCalls++
        return checkNotNull(approved)
    }
}

private class FakeBuildService(private val failure: Throwable? = null) : BuildApplicationService {
    var calls = 0
    var request: BuildSongRequest? = null

    override suspend fun build(request: BuildSongRequest, progress: app.melotrail.application.ProgressSink): BuildResult {
        calls++
        this.request = request
        failure?.let { throw it }
        progress.report(app.melotrail.application.OperationProgress("build", 3, 9, "Rendering or reusing stems", request.root.resolve("stems/piano.wav")))
        return BuildResult(request.root, request.root.resolve("mix/dry.wav"), null, request.root.resolve("output/master.wav"), null, reusedStems = true)
    }
}
