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
}

private class FakeFileDialogs : DesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = null
}

private class FakeProjectService(
    private val result: ProjectSnapshot? = null,
    private val failure: Throwable? = null
) : ProjectApplicationService {
    override fun open(root: Path): ProjectSnapshot {
        failure?.let { throw it }
        return checkNotNull(result)
    }

    override fun create(request: CreateProjectRequest): ProjectSnapshot = error("Not used")
    override suspend fun importPart(request: ImportPartRequest, progress: ai.music.workstation.application.ProgressSink): ProjectSnapshot = error("Not used")
    override suspend fun analyzePart(request: AnalyzePartRequest, progress: ai.music.workstation.application.ProgressSink): ProjectSnapshot = error("Not used")
    override fun updatePart(request: UpdatePartRoleRequest): ProjectSnapshot = error("Not used")
    override fun saveStructure(request: SaveStructureRequest): ProjectSnapshot = error("Not used")
}
