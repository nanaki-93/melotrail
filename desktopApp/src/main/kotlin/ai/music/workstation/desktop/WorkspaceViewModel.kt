package ai.music.workstation.desktop

import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.application.ProjectSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

data class WorkspaceDispatchers(
    val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO
)

data class WorkspaceUiState(
    val project: ProjectSnapshot? = null,
    val operation: WorkspaceOperation = WorkspaceOperation.Idle,
    val notification: String? = null
)

sealed interface WorkspaceOperation {
    data object Idle : WorkspaceOperation
    data class OpeningProject(val root: Path) : WorkspaceOperation
    data class OpenFailed(val message: String) : WorkspaceOperation
}

sealed interface WorkspaceIntent {
    data object ChooseProject : WorkspaceIntent
    data class OpenProject(val root: Path) : WorkspaceIntent
    data object DismissNotification : WorkspaceIntent
}

class WorkspaceViewModel(
    private val projectService: ProjectApplicationService,
    private val fileDialogs: DesktopFileDialogs,
    dispatchers: WorkspaceDispatchers = WorkspaceDispatchers()
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val ioDispatcher = dispatchers.io
    private val mutableState = MutableStateFlow(WorkspaceUiState())

    val state: StateFlow<WorkspaceUiState> = mutableState.asStateFlow()

    fun accept(intent: WorkspaceIntent) {
        when (intent) {
            WorkspaceIntent.ChooseProject -> chooseProject()
            is WorkspaceIntent.OpenProject -> openProject(intent.root)
            WorkspaceIntent.DismissNotification -> mutableState.update { it.copy(notification = null) }
        }
    }

    private fun chooseProject() {
        scope.launch {
            val root = fileDialogs.chooseProjectDirectory() ?: return@launch
            openProject(root)
        }
    }

    private fun openProject(root: Path) {
        val normalized = root.toAbsolutePath().normalize()
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(normalized), notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.open(normalized) } }
                .onSuccess(::opened)
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(
                            operation = WorkspaceOperation.OpenFailed(failure.message ?: "Unable to open project."),
                            notification = "Unable to open project: ${failure.message ?: "Unknown error"}"
                        )
                    }
                }
        }
    }

    private fun opened(project: ProjectSnapshot) {
        mutableState.update {
            it.copy(project = project, operation = WorkspaceOperation.Idle, notification = "Opened ${project.name}")
        }
    }

    override fun close() {
        scope.cancel()
    }
}
