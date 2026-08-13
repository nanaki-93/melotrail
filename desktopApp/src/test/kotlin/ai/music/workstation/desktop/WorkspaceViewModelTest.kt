package ai.music.workstation.desktop

import ai.music.workstation.application.AnalyzePartRequest
import ai.music.workstation.application.CreateProjectRequest
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
}

private class FakeFileDialogs : DesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = null
    override suspend fun chooseNewProjectDirectory(): Path? = null
    override suspend fun choosePartSource(audio: Boolean): Path? = null
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
