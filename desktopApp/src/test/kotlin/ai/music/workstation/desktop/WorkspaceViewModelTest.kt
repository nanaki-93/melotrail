package ai.music.workstation.desktop

import ai.music.workstation.application.AnalyzePartRequest
import ai.music.workstation.application.ArrangementApplicationService
import ai.music.workstation.application.ArrangementPlannerKind
import ai.music.workstation.application.ArrangementSectionSnapshot
import ai.music.workstation.application.ArrangementSnapshot
import ai.music.workstation.application.CreateProjectRequest
import ai.music.workstation.application.GenerateArrangementRequest
import ai.music.workstation.application.GeneratedMidiSnapshot
import ai.music.workstation.application.ImportPartRequest
import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.application.ProjectSnapshot
import ai.music.workstation.application.SaveStructureRequest
import ai.music.workstation.application.UpdatePartRoleRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun `keeps the workspace empty when opening fails`() = runTest {
        val viewModel = WorkspaceViewModel(FakeProjectService(failure = IllegalArgumentException("Project file not found")), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.OpenProject(Path.of("missing")))
        advanceUntilIdle()

        assertNull(viewModel.state.value.project)
        assertEquals("Project file not found", assertIs<WorkspaceOperation.OpenFailed>(viewModel.state.value.operation).message)
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

        val node = Preferences.userRoot().node("ai-music-workstation-test-${UUID.randomUUID()}")
        try {
            node.put("last-successfully-opened-project", "   ")
            assertNull(JvmDesktopPreferences(node).lastOpenedProject())
            val soundRoot = java.nio.file.Files.createTempDirectory("desktop-preference-library")
            val desktopPreferences = JvmDesktopPreferences(node)
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
        val snapshot = projectSnapshot(root).copy(parts = listOf(part("A"), part("B")))
        val service = FakeProjectService(result = snapshot)
        val viewModel = WorkspaceViewModel(service, FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)))

        viewModel.accept(WorkspaceIntent.ShowCreateProject)
        viewModel.accept(WorkspaceIntent.UpdateCreateProject(WorkspaceDialog.CreateProject(root, "test-project", "44100", "2")))
        viewModel.accept(WorkspaceIntent.CreateProject)
        advanceUntilIdle()
        assertEquals(root, service.created?.root)

        viewModel.accept(WorkspaceIntent.ShowImportPart(audio = false))
        viewModel.accept(WorkspaceIntent.UpdateImportPart(WorkspaceDialog.ImportPart(false, Path.of("input.mid"), "A", "verse")))
        viewModel.accept(WorkspaceIntent.ImportPart)
        assertIs<WorkspaceOperation.ImportingPart>(viewModel.state.value.operation)
        advanceUntilIdle()
        assertEquals("A", service.imported?.id)

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
        viewModel.close()
    }

    @Test
    fun `validates planner inputs then generates an approved deterministic arrangement`() = runTest {
        val root = Path.of("build/arrangement-project")
        val project = projectSnapshot(root).copy(
            parts = listOf(analyzedPart("A")),
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
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
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
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
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 4.0))
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
        val settings = SoundLibrarySettingsService(preferences, ai.music.workstation.arrangement.SoundLibraryLocator(emptyMap()), SoundLibraryValidator { Result.success(Unit) }, environment = emptyMap())
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
    fun `readiness refresh failure is visible and unavailable audio import keeps its reason`() = runTest {
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
        assertEquals("Start the Python worker with make worker.", assertIs<WorkspaceOperation.Failed>(viewModel.state.value.operation).message)
        viewModel.close()

        val failed = WorkspaceViewModel(FakeProjectService(), FakeFileDialogs(), testDispatchers(StandardTestDispatcher(testScheduler)), runtimeReadinessService = RuntimeReadinessService { throw IllegalStateException("probe failed") })
        failed.accept(WorkspaceIntent.RefreshRuntimeReadiness); advanceUntilIdle()
        assertTrue(failed.state.value.notification!!.contains("probe failed"))
        failed.close()
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
        readiness = ai.music.workstation.application.ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
    )

    private fun part(id: String) = ai.music.workstation.application.PartSummary(
        id = id,
        role = "",
        sourceFile = "source/$id.mid",
        sourceName = "$id.mid",
        sourceType = ai.music.workstation.application.PartSourceType.MIDI,
        analysis = null
    )

    private fun analyzedPart(id: String) = part(id).copy(analysis = ai.music.workstation.application.PartAnalysisSummary(
        ai.music.workstation.application.PartAnalysisStatus.MIDI, "analysis/$id.midi.json", bars = 2, durationSeconds = 4.0, key = "C major"
    ))

    private fun arrangementSnapshot(root: Path, approvalRequired: Boolean = false, approved: Boolean = true) = ArrangementSnapshot(
        root = root,
        sections = listOf(ArrangementSectionSnapshot(0, "A1", "A", "introduction", 0.4, listOf(
            ai.music.workstation.application.ArrangementInstrumentSnapshot("piano", "source", null, null),
            ai.music.workstation.application.ArrangementInstrumentSnapshot("bass", "generated", "bass", 0.4)
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
    override suspend fun choosePartSource(audio: Boolean): Path? = null
    override suspend fun chooseSoundLibraryDirectory(): Path? = nextLibraryRoot.also { nextLibraryRoot = null }
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
    private val failureOnImport: Throwable? = null
) : ProjectApplicationService {
    private var current: ProjectSnapshot? = result
    var created: CreateProjectRequest? = null
    var imported: ImportPartRequest? = null
    var analyzed: AnalyzePartRequest? = null
    var updatedRole: UpdatePartRoleRequest? = null
    var savedStructure: SaveStructureRequest? = null
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

    override suspend fun importPart(request: ImportPartRequest, progress: ai.music.workstation.application.ProgressSink): ProjectSnapshot {
        imported = request
        failureOnImport?.let { throw it }
        progress.report(ai.music.workstation.application.OperationProgress("import-part", 2, 4, "Cleaning MIDI"))
        return checkNotNull(current)
    }

    override suspend fun analyzePart(request: AnalyzePartRequest, progress: ai.music.workstation.application.ProgressSink): ProjectSnapshot {
        analyzed = request
        return checkNotNull(current)
    }

    override fun updatePart(request: UpdatePartRoleRequest): ProjectSnapshot {
        updatedRole = request
        return checkNotNull(current)
    }

    override fun saveStructure(request: SaveStructureRequest): ProjectSnapshot {
        savedStructure = request
        current = checkNotNull(current).copy(structure = request.partIds.mapIndexed { index, id ->
            ai.music.workstation.application.StructureSectionSummary(index, id, request.partIds.take(index + 1).count { it == id }, "$id${request.partIds.take(index + 1).count { it == id }}", null)
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

    override suspend fun generate(request: GenerateArrangementRequest, progress: ai.music.workstation.application.ProgressSink): ArrangementSnapshot {
        generatedRequest = request
        failureOnGenerate?.let { throw it }
        progress.report(ai.music.workstation.application.OperationProgress("arrange", 2, 3, "Creating reviewed song plan"))
        return checkNotNull(generated)
    }

    override suspend fun generateRequiredMidi(root: Path, progress: ai.music.workstation.application.ProgressSink): GeneratedMidiSnapshot = GeneratedMidiSnapshot(emptyList())

    override suspend fun renderApprovedStems(
        root: Path,
        renderer: ai.music.workstation.arrangement.InstrumentRenderer,
        progress: ai.music.workstation.application.ProgressSink
    ): ai.music.workstation.arrangement.StemRenderResult = error("Not used by this fake")

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
