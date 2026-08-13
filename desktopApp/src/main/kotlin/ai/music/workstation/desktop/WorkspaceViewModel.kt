package ai.music.workstation.desktop

import ai.music.workstation.application.AnalyzePartRequest
import ai.music.workstation.application.CreateProjectRequest
import ai.music.workstation.application.ImportPartRequest
import ai.music.workstation.application.OperationProgress
import ai.music.workstation.application.PartSourceType
import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.application.ProjectSnapshot
import ai.music.workstation.application.SaveStructureRequest
import ai.music.workstation.application.UpdatePartRoleRequest
import ai.music.workstation.arrangement.RenderFormat
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
    val notification: String? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val dialog: WorkspaceDialog? = null,
    val structureDraft: List<String> = emptyList(),
    val downstreamArtifactsStale: Boolean = false,
    val retry: WorkspaceRetry? = null
)

sealed interface WorkspaceOperation {
    data object Idle : WorkspaceOperation
    data class OpeningProject(val root: Path) : WorkspaceOperation
    data class CreatingProject(val root: Path) : WorkspaceOperation
    data class ImportingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class AnalyzingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class UpdatingPartRole(val id: String) : WorkspaceOperation
    data object SavingStructure : WorkspaceOperation
    data class OpenFailed(val message: String) : WorkspaceOperation
    data class Failed(val action: String, val message: String) : WorkspaceOperation
}

val WorkspaceOperation.isMutating: Boolean
    get() = this is WorkspaceOperation.OpeningProject || this is WorkspaceOperation.CreatingProject ||
        this is WorkspaceOperation.ImportingPart || this is WorkspaceOperation.AnalyzingPart ||
        this is WorkspaceOperation.UpdatingPartRole || this is WorkspaceOperation.SavingStructure

sealed interface WorkspaceDialog {
    data class CreateProject(
        val root: Path? = null,
        val name: String = "",
        val sampleRate: String = "44100",
        val channels: String = "2"
    ) : WorkspaceDialog

    data class ImportPart(
        val audio: Boolean,
        val source: Path? = null,
        val id: String = "",
        val role: String = ""
    ) : WorkspaceDialog

    data class EditRole(val partId: String, val role: String) : WorkspaceDialog
}

sealed interface WorkspaceRetry {
    data class Import(val request: ImportPartRequest) : WorkspaceRetry
    data class Analyze(val root: Path, val partId: String) : WorkspaceRetry
}

sealed interface WorkspaceIntent {
    data object ChooseProject : WorkspaceIntent
    data object ShowCreateProject : WorkspaceIntent
    data object ChooseCreateProjectDirectory : WorkspaceIntent
    data class UpdateCreateProject(val draft: WorkspaceDialog.CreateProject) : WorkspaceIntent
    data object CreateProject : WorkspaceIntent
    data class OpenProject(val root: Path) : WorkspaceIntent
    data object RefreshRuntimeReadiness : WorkspaceIntent
    data class ShowImportPart(val audio: Boolean) : WorkspaceIntent
    data object ChooseImportSource : WorkspaceIntent
    data class ImportSourceChosen(val source: Path?) : WorkspaceIntent
    data class UpdateImportPart(val draft: WorkspaceDialog.ImportPart) : WorkspaceIntent
    data object ImportPart : WorkspaceIntent
    data class AnalyzePart(val partId: String) : WorkspaceIntent
    data class ShowRoleEditor(val partId: String) : WorkspaceIntent
    data class UpdateRole(val role: String) : WorkspaceIntent
    data object SaveRole : WorkspaceIntent
    data class AddStructurePart(val partId: String) : WorkspaceIntent
    data class DuplicateStructurePart(val index: Int) : WorkspaceIntent
    data class RemoveStructurePart(val index: Int) : WorkspaceIntent
    data class MoveStructurePart(val fromIndex: Int, val toIndex: Int) : WorkspaceIntent
    data object ClearStructure : WorkspaceIntent
    data object Retry : WorkspaceIntent
    data object DismissDialog : WorkspaceIntent
    data object DismissNotification : WorkspaceIntent
}

class WorkspaceViewModel(
    private val projectService: ProjectApplicationService,
    private val fileDialogs: DesktopFileDialogs,
    dispatchers: WorkspaceDispatchers = WorkspaceDispatchers(),
    private val runtimeReadinessService: RuntimeReadinessService = UnavailableRuntimeReadinessService
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val ioDispatcher = dispatchers.io
    private val mutableState = MutableStateFlow(WorkspaceUiState())

    val state: StateFlow<WorkspaceUiState> = mutableState.asStateFlow()

    fun accept(intent: WorkspaceIntent) {
        when (intent) {
            WorkspaceIntent.ChooseProject -> chooseProject()
            WorkspaceIntent.ShowCreateProject -> mutableState.update { it.copy(dialog = WorkspaceDialog.CreateProject(), notification = null) }
            WorkspaceIntent.ChooseCreateProjectDirectory -> chooseCreateProjectDirectory()
            is WorkspaceIntent.UpdateCreateProject -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.CreateProject -> createProject()
            is WorkspaceIntent.OpenProject -> openProject(intent.root)
            WorkspaceIntent.RefreshRuntimeReadiness -> refreshRuntimeReadiness()
            is WorkspaceIntent.ShowImportPart -> showImportPart(intent.audio)
            WorkspaceIntent.ChooseImportSource -> chooseImportSource()
            is WorkspaceIntent.ImportSourceChosen -> updateImportSource(intent.source)
            is WorkspaceIntent.UpdateImportPart -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.ImportPart -> importPart()
            is WorkspaceIntent.AnalyzePart -> analyzePart(intent.partId)
            is WorkspaceIntent.ShowRoleEditor -> showRoleEditor(intent.partId)
            is WorkspaceIntent.UpdateRole -> updateRole(intent.role)
            WorkspaceIntent.SaveRole -> saveRole()
            is WorkspaceIntent.AddStructurePart -> saveStructure(state.value.structureDraft + intent.partId)
            is WorkspaceIntent.DuplicateStructurePart -> duplicateStructurePart(intent.index)
            is WorkspaceIntent.RemoveStructurePart -> removeStructurePart(intent.index)
            is WorkspaceIntent.MoveStructurePart -> moveStructurePart(intent.fromIndex, intent.toIndex)
            WorkspaceIntent.ClearStructure -> saveStructure(emptyList())
            WorkspaceIntent.Retry -> retry()
            WorkspaceIntent.DismissDialog -> mutableState.update { it.copy(dialog = null) }
            WorkspaceIntent.DismissNotification -> mutableState.update { it.copy(notification = null) }
        }
    }

    private fun chooseProject() = scope.launch {
        fileDialogs.chooseProjectDirectory()?.let(::openProject)
    }

    private fun chooseCreateProjectDirectory() = scope.launch {
        val root = fileDialogs.chooseNewProjectDirectory() ?: return@launch
        val dialog = state.value.dialog as? WorkspaceDialog.CreateProject ?: return@launch
        mutableState.update { it.copy(dialog = dialog.copy(root = root, name = dialog.name.ifBlank { root.fileName.toString() })) }
    }

    private fun createProject() {
        val draft = state.value.dialog as? WorkspaceDialog.CreateProject ?: return
        val root = draft.root ?: return fail("create project", "Choose a project folder first.")
        val sampleRate = draft.sampleRate.toIntOrNull() ?: return fail("create project", "Sample rate must be a whole number.")
        val channels = draft.channels.toIntOrNull() ?: return fail("create project", "Channels must be a whole number.")
        val request = CreateProjectRequest(root, draft.name.ifBlank { null }, RenderFormat(sampleRate, channels))
        mutableState.update { it.copy(operation = WorkspaceOperation.CreatingProject(root), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.create(request).refreshed() } }
                .onSuccess { opened(it, "Created ${it.name}") }
                .onFailure { fail("create project", it.message ?: "Unable to create project.") }
        }
    }

    private fun openProject(root: Path) {
        if (state.value.operation.isMutating) return
        val normalized = root.toAbsolutePath().normalize()
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(normalized), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.open(normalized) } }
                .onSuccess { opened(it, "Opened ${it.name}") }
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

    private fun refreshRuntimeReadiness() = scope.launch {
        runCatching { withContext(ioDispatcher) { runtimeReadinessService.check() } }
            .onSuccess { readiness -> mutableState.update { it.copy(runtimeReadiness = readiness) } }
            .onFailure { failure -> mutableState.update { it.copy(notification = "Could not check local readiness: ${failure.message}") } }
    }

    private fun showImportPart(audio: Boolean) {
        if (state.value.project == null || state.value.operation.isMutating) return
        mutableState.update { it.copy(dialog = WorkspaceDialog.ImportPart(audio), notification = null) }
    }

    private fun chooseImportSource() = scope.launch {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return@launch
        updateImportSource(fileDialogs.choosePartSource(draft.audio))
    }

    private fun updateImportSource(source: Path?) {
        if (source == null) return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        mutableState.update { it.copy(dialog = draft.copy(source = source)) }
    }

    private fun importPart() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        val source = draft.source ?: return fail("import part", "Choose a ${if (draft.audio) "WAV or MP3" else "MIDI"} source first.")
        if (draft.id.isBlank()) return fail("import part", "Part ID is required and remains stable after import.")
        val request = ImportPartRequest(project.root, draft.id, source, draft.role, transcribe = draft.audio)
        runImport(request)
    }

    private fun runImport(request: ImportPartRequest) {
        mutableState.update { it.copy(operation = WorkspaceOperation.ImportingPart(request.id), notification = null, retry = null) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.importPart(request) { progress ->
                        scope.launch { updateProgress(WorkspaceOperation.ImportingPart(request.id, progress)) }
                    }.refreshed()
                }
            }.onSuccess { opened(it, "Imported ${request.id}") }
                .onFailure { fail("import part", it.message ?: "Unable to import ${request.id}.", WorkspaceRetry.Import(request)) }
        }
    }

    private fun analyzePart(partId: String) {
        val project = state.value.project ?: return
        val request = AnalyzePartRequest(project.root, partId)
        mutableState.update { it.copy(operation = WorkspaceOperation.AnalyzingPart(partId), notification = null, retry = null) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.analyzePart(request) { progress ->
                        scope.launch { updateProgress(WorkspaceOperation.AnalyzingPart(partId, progress)) }
                    }.refreshed()
                }
            }.onSuccess { opened(it, "Analyzed $partId") }
                .onFailure { fail("analyze part", it.message ?: "Unable to analyze $partId.", WorkspaceRetry.Analyze(project.root, partId)) }
        }
    }

    private fun showRoleEditor(partId: String) {
        val part = state.value.project?.parts?.find { it.id == partId } ?: return
        mutableState.update { it.copy(dialog = WorkspaceDialog.EditRole(part.id, part.role)) }
    }

    private fun updateRole(role: String) {
        val draft = state.value.dialog as? WorkspaceDialog.EditRole ?: return
        mutableState.update { it.copy(dialog = draft.copy(role = role)) }
    }

    private fun saveRole() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.EditRole ?: return
        mutableState.update { it.copy(operation = WorkspaceOperation.UpdatingPartRole(draft.partId), notification = null) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.updatePart(UpdatePartRoleRequest(project.root, draft.partId, draft.role)).refreshed()
                }
            }.onSuccess { opened(it, "Updated ${draft.partId} role") }
                .onFailure { fail("update role", it.message ?: "Unable to update role.") }
        }
    }

    private fun duplicateStructurePart(index: Int) {
        val draft = state.value.structureDraft
        if (index !in draft.indices) return
        saveStructure(draft.toMutableList().apply { add(index + 1, draft[index]) })
    }

    private fun removeStructurePart(index: Int) {
        val draft = state.value.structureDraft
        if (index !in draft.indices) return
        saveStructure(draft.filterIndexed { current, _ -> current != index })
    }

    private fun moveStructurePart(from: Int, to: Int) {
        val draft = state.value.structureDraft
        if (from !in draft.indices || to !in draft.indices || from == to) return
        saveStructure(draft.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun saveStructure(partIds: List<String>) {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating) return
        val existing = state.value.structureDraft
        mutableState.update { it.copy(operation = WorkspaceOperation.SavingStructure, notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.saveStructure(SaveStructureRequest(project.root, partIds)).refreshed() } }
                .onSuccess { snapshot ->
                    val artifactsExist = snapshot.readiness.let {
                        it.songPlanAvailable || it.arrangementAvailable || it.generatedMidiAvailable || it.stemsAvailable ||
                            it.dryMixAvailable || it.loFiMixAvailable || it.masterAvailable
                    }
                    opened(snapshot, if (partIds.isEmpty()) "Cleared structure" else "Saved song structure", stale =
                        state.value.downstreamArtifactsStale || (existing != partIds && artifactsExist))
                }
                .onFailure { fail("save structure", it.message ?: "Unable to save structure.") }
        }
    }

    private fun retry() = when (val action = state.value.retry) {
        is WorkspaceRetry.Import -> runImport(action.request)
        is WorkspaceRetry.Analyze -> analyzePart(action.partId)
        null -> Unit
    }

    private fun updateProgress(operation: WorkspaceOperation) {
        mutableState.update { current -> if (current.operation.isMutating) current.copy(operation = operation) else current }
    }

    private fun ProjectSnapshot.refreshed(): ProjectSnapshot = projectService.open(root)

    private fun opened(project: ProjectSnapshot, message: String, stale: Boolean = false) {
        mutableState.update {
            it.copy(
                project = project,
                operation = WorkspaceOperation.Idle,
                notification = message,
                dialog = null,
                structureDraft = project.structure.map { section -> section.partId },
                downstreamArtifactsStale = stale,
                retry = null
            )
        }
    }

    private fun fail(action: String, message: String, retry: WorkspaceRetry? = null) {
        mutableState.update { it.copy(operation = WorkspaceOperation.Failed(action, message), notification = message, retry = retry) }
    }

    override fun close() = scope.cancel()

    private object UnavailableRuntimeReadinessService : RuntimeReadinessService {
        override suspend fun check(): RuntimeReadiness = RuntimeReadiness(
            worker = DependencyReadiness(false, "Worker readiness has not been configured."),
            renderer = DependencyReadiness(false, "Renderer readiness has not been configured.")
        )
    }
}

fun WorkspaceUiState.partPreparationLabel(partId: String): String {
    val part = project?.parts?.find { it.id == partId } ?: return "Unknown"
    return when {
        part.analysis != null -> "Analyzed"
        project.version >= 2 -> "Prepared MIDI — analysis needed"
        part.sourceType == PartSourceType.AUDIO -> "Source audio — transcription needed"
        else -> "Preparation needed"
    }
}
