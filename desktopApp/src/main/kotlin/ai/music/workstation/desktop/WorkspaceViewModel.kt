package ai.music.workstation.desktop

import ai.music.workstation.application.AnalyzePartRequest
import ai.music.workstation.application.ArrangementApplicationService
import ai.music.workstation.application.ArrangementPlannerKind
import ai.music.workstation.application.ArrangementSnapshot
import ai.music.workstation.application.CreateProjectRequest
import ai.music.workstation.application.DefaultArrangementApplicationService
import ai.music.workstation.application.GenerateArrangementRequest
import ai.music.workstation.application.ImportPartRequest
import ai.music.workstation.application.MixApplicationService
import ai.music.workstation.application.MixSnapshot
import ai.music.workstation.application.PersistedMixSettings
import ai.music.workstation.application.LogicalMixSetting
import ai.music.workstation.application.DefaultMixApplicationService
import ai.music.workstation.application.BuildApplicationService
import ai.music.workstation.application.BuildSongRequest
import ai.music.workstation.application.PartPreviewApplicationService
import ai.music.workstation.application.OperationProgress
import ai.music.workstation.application.PartSourceType
import ai.music.workstation.application.PartAnalysisStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.nio.file.Path

data class WorkspaceDispatchers(
    val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO
)

data class WorkspaceUiState(
    val project: ProjectSnapshot? = null,
    val arrangement: ArrangementSnapshot? = null,
    val mix: MixSnapshot? = null,
    val buildOptions: BuildOptionsDraft = BuildOptionsDraft(),
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val arrangementDraft: ArrangementDraft = ArrangementDraft(),
    val selectedArrangementSection: Int? = null,
    val operation: WorkspaceOperation = WorkspaceOperation.Idle,
    val notification: String? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val dialog: WorkspaceDialog? = null,
    val structureDraft: List<String> = emptyList(),
    val downstreamArtifactsStale: Boolean = false,
    val arrangementDraftDirty: Boolean = false,
    val retry: WorkspaceRetry? = null
)

data class BuildOptionsDraft(val loFi: Boolean = false, val mp3: Boolean = false)
data class PlaybackSnapshot(
    val source: PlaybackSource = PlaybackSource.DRY,
    val state: ai.music.workstation.audio.PlaybackState = ai.music.workstation.audio.PlaybackState.STOPPED,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val volume: Double = 1.0
)
enum class PlaybackSource { DRY, LOFI, MASTER }

data class ArrangementDraft(
    val planner: ArrangementPlannerKind = ArrangementPlannerKind.DETERMINISTIC,
    val style: String = "",
    val instruments: Set<String> = setOf("piano")
)

sealed interface WorkspaceOperation {
    data object Idle : WorkspaceOperation
    data class OpeningProject(val root: Path) : WorkspaceOperation
    data class CreatingProject(val root: Path) : WorkspaceOperation
    data class ImportingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class AnalyzingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class UpdatingPartRole(val id: String) : WorkspaceOperation
    data object SavingStructure : WorkspaceOperation
    data class GeneratingArrangement(val progress: OperationProgress? = null) : WorkspaceOperation
    data class ApplyingMix(val progress: OperationProgress? = null) : WorkspaceOperation
    data class BuildingSong(val progress: OperationProgress? = null) : WorkspaceOperation
    data object ApprovingArrangement : WorkspaceOperation
    data class OpenFailed(val message: String) : WorkspaceOperation
    data class Failed(val action: String, val message: String) : WorkspaceOperation
}

val WorkspaceOperation.isMutating: Boolean
    get() = this is WorkspaceOperation.OpeningProject || this is WorkspaceOperation.CreatingProject ||
        this is WorkspaceOperation.ImportingPart || this is WorkspaceOperation.AnalyzingPart ||
        this is WorkspaceOperation.UpdatingPartRole || this is WorkspaceOperation.SavingStructure ||
        this is WorkspaceOperation.GeneratingArrangement || this is WorkspaceOperation.ApprovingArrangement
        || this is WorkspaceOperation.ApplyingMix || this is WorkspaceOperation.BuildingSong

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
    data class ConfirmDiscardDraft(val root: Path? = null, val createProject: Boolean = false) : WorkspaceDialog
    data object ConfirmClose : WorkspaceDialog
}

sealed interface WorkspaceRetry {
    data class Import(val request: ImportPartRequest) : WorkspaceRetry
    data class Analyze(val root: Path, val partId: String) : WorkspaceRetry
    data class GenerateArrangement(val request: GenerateArrangementRequest) : WorkspaceRetry
}

sealed interface WorkspaceIntent {
    data object ChooseProject : WorkspaceIntent
    data object RestoreLastProject : WorkspaceIntent
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
    data class PreviewPart(val partId: String) : WorkspaceIntent
    data class ShowRoleEditor(val partId: String) : WorkspaceIntent
    data class UpdateRole(val role: String) : WorkspaceIntent
    data object SaveRole : WorkspaceIntent
    data class AddStructurePart(val partId: String) : WorkspaceIntent
    data class DuplicateStructurePart(val index: Int) : WorkspaceIntent
    data class RemoveStructurePart(val index: Int) : WorkspaceIntent
    data class MoveStructurePart(val fromIndex: Int, val toIndex: Int) : WorkspaceIntent
    data object ClearStructure : WorkspaceIntent
    data class UpdateArrangementPlanner(val planner: ArrangementPlannerKind) : WorkspaceIntent
    data class UpdateArrangementStyle(val style: String) : WorkspaceIntent
    data class ToggleArrangementInstrument(val instrument: String) : WorkspaceIntent
    data class UpdateMixSetting(val instrument: String, val setting: LogicalMixSetting) : WorkspaceIntent
    data object ResetMix : WorkspaceIntent
    data class UpdateBuildOptions(val options: BuildOptionsDraft) : WorkspaceIntent
    data object BuildSong : WorkspaceIntent
    data object CancelOperation : WorkspaceIntent
    data class SelectPlaybackSource(val source: PlaybackSource) : WorkspaceIntent
    data object PlayPause : WorkspaceIntent
    data object StopPlayback : WorkspaceIntent
    data class SeekPlayback(val seconds: Double) : WorkspaceIntent
    data class SetPlaybackVolume(val volume: Double) : WorkspaceIntent
    data object GenerateArrangement : WorkspaceIntent
    data object PreviewArrangement : WorkspaceIntent
    data object ApproveArrangement : WorkspaceIntent
    data class SelectArrangementSection(val index: Int?) : WorkspaceIntent
    data object Retry : WorkspaceIntent
    data object DismissDialog : WorkspaceIntent
    data object DismissNotification : WorkspaceIntent
    data object ConfirmDiscardDraft : WorkspaceIntent
    data object RequestClose : WorkspaceIntent
    data object ConfirmClose : WorkspaceIntent
}

class WorkspaceViewModel(
    private val projectService: ProjectApplicationService,
    private val fileDialogs: DesktopFileDialogs,
    dispatchers: WorkspaceDispatchers = WorkspaceDispatchers(),
    private val runtimeReadinessService: RuntimeReadinessService = UnavailableRuntimeReadinessService,
    private val arrangementService: ArrangementApplicationService = DefaultArrangementApplicationService(),
    private val mixService: MixApplicationService = DefaultMixApplicationService(),
    private val buildService: BuildApplicationService? = null,
    private val player: ArtifactAudioPlayer? = null,
    private val partPreviewService: PartPreviewApplicationService? = null,
    private val preferences: DesktopPreferences = NoOpDesktopPreferences,
    private val operationLogger: DesktopOperationLogger = NoOpDesktopOperationLogger
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val ioDispatcher = dispatchers.io
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    private var mixCommit: Job? = null
    private var buildJob: Job? = null

    val state: StateFlow<WorkspaceUiState> = mutableState.asStateFlow()

    init {
        player?.let { monitor ->
            scope.launch { monitor.state.collect { updatePlayback() } }
            scope.launch { monitor.currentPosition.collect { updatePlayback() } }
            scope.launch { monitor.totalDuration.collect { updatePlayback() } }
            scope.launch { monitor.volume.collect { updatePlayback() } }
        }
    }

    fun accept(intent: WorkspaceIntent) {
        when (intent) {
            WorkspaceIntent.ChooseProject -> chooseProject()
            WorkspaceIntent.RestoreLastProject -> restoreLastProject()
            WorkspaceIntent.ShowCreateProject -> showCreateProject()
            WorkspaceIntent.ChooseCreateProjectDirectory -> chooseCreateProjectDirectory()
            is WorkspaceIntent.UpdateCreateProject -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.CreateProject -> createProject()
            is WorkspaceIntent.OpenProject -> requestOpenProject(intent.root)
            WorkspaceIntent.RefreshRuntimeReadiness -> refreshRuntimeReadiness()
            is WorkspaceIntent.ShowImportPart -> showImportPart(intent.audio)
            WorkspaceIntent.ChooseImportSource -> chooseImportSource()
            is WorkspaceIntent.ImportSourceChosen -> updateImportSource(intent.source)
            is WorkspaceIntent.UpdateImportPart -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.ImportPart -> importPart()
            is WorkspaceIntent.AnalyzePart -> analyzePart(intent.partId)
            is WorkspaceIntent.PreviewPart -> previewPart(intent.partId)
            is WorkspaceIntent.ShowRoleEditor -> showRoleEditor(intent.partId)
            is WorkspaceIntent.UpdateRole -> updateRole(intent.role)
            WorkspaceIntent.SaveRole -> saveRole()
            is WorkspaceIntent.AddStructurePart -> saveStructure(state.value.structureDraft + intent.partId)
            is WorkspaceIntent.DuplicateStructurePart -> duplicateStructurePart(intent.index)
            is WorkspaceIntent.RemoveStructurePart -> removeStructurePart(intent.index)
            is WorkspaceIntent.MoveStructurePart -> moveStructurePart(intent.fromIndex, intent.toIndex)
            WorkspaceIntent.ClearStructure -> saveStructure(emptyList())
            is WorkspaceIntent.UpdateArrangementPlanner -> updateArrangementPlanner(intent.planner)
            is WorkspaceIntent.UpdateArrangementStyle -> mutableState.update { it.copy(arrangementDraft = it.arrangementDraft.copy(style = intent.style), arrangementDraftDirty = true) }
            is WorkspaceIntent.ToggleArrangementInstrument -> toggleArrangementInstrument(intent.instrument)
            is WorkspaceIntent.UpdateMixSetting -> updateMixSetting(intent.instrument, intent.setting)
            WorkspaceIntent.ResetMix -> resetMix()
            is WorkspaceIntent.UpdateBuildOptions -> mutableState.update { it.copy(buildOptions = intent.options) }
            WorkspaceIntent.BuildSong -> buildSong()
            WorkspaceIntent.CancelOperation -> buildJob?.cancel()
            is WorkspaceIntent.SelectPlaybackSource -> selectPlaybackSource(intent.source)
            WorkspaceIntent.PlayPause -> playPause()
            WorkspaceIntent.StopPlayback -> player?.stop()
            is WorkspaceIntent.SeekPlayback -> player?.seek(intent.seconds)
            is WorkspaceIntent.SetPlaybackVolume -> player?.setVolume(intent.volume)
            WorkspaceIntent.GenerateArrangement -> generateArrangement()
            WorkspaceIntent.PreviewArrangement -> previewArrangement()
            WorkspaceIntent.ApproveArrangement -> approveArrangement()
            is WorkspaceIntent.SelectArrangementSection -> mutableState.update { it.copy(selectedArrangementSection = intent.index) }
            WorkspaceIntent.Retry -> retry()
            WorkspaceIntent.DismissDialog -> mutableState.update { it.copy(dialog = null) }
            WorkspaceIntent.DismissNotification -> mutableState.update { it.copy(notification = null) }
            WorkspaceIntent.ConfirmDiscardDraft -> confirmDiscardDraft()
            WorkspaceIntent.RequestClose -> requestClose()
            WorkspaceIntent.ConfirmClose -> close()
        }
    }

    private fun chooseProject() = scope.launch {
        fileDialogs.chooseProjectDirectory()?.let(::requestOpenProject)
    }

    private fun restoreLastProject() {
        if (state.value.project != null || state.value.operation.isMutating) return
        preferences.lastOpenedProject()?.let { root ->
            openProject(root, restoring = true)
        }
    }

    private fun showCreateProject() {
        if (state.value.operation.isMutating) return busy("create a project")
        if (hasUnsavedDraft()) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmDiscardDraft(createProject = true)) }
        } else {
            mutableState.update { it.copy(dialog = WorkspaceDialog.CreateProject(), notification = null) }
        }
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

    private fun requestOpenProject(root: Path) {
        if (state.value.operation.isMutating) return busy("switch projects")
        if (hasUnsavedDraft()) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmDiscardDraft(root = root)) }
        } else {
            openProject(root)
        }
    }

    private fun confirmDiscardDraft() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmDiscardDraft ?: return
        mutableState.update { it.copy(dialog = null, arrangementDraftDirty = false) }
        when {
            dialog.root != null -> openProject(dialog.root)
            dialog.createProject -> mutableState.update { it.copy(dialog = WorkspaceDialog.CreateProject(), notification = null) }
        }
    }

    private fun openProject(root: Path, restoring: Boolean = false) {
        if (state.value.operation.isMutating) return busy("open a project")
        val normalized = root.toAbsolutePath().normalize()
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(normalized), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.open(normalized) } }
            .onSuccess { opened(it, "Opened ${it.name}") }
                .onFailure { failure ->
                    if (restoring) preferences.clearLastOpenedProject()
                    operationLogger.event("open-project", "failed", normalized, failure)
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

    private fun previewPart(partId: String) {
        val project = state.value.project ?: return
        val monitor = player ?: return fail("preview part", "Local audio playback is not configured.")
        val previews = partPreviewService ?: return fail("preview part", "Part preview service is not configured for this desktop session.")
        scope.launch {
            runCatching { withContext(ioDispatcher) { previews.preview(project.root, partId) } }
                .onSuccess { path -> monitor.play(path); mutableState.update { it.copy(notification = "Previewing $partId") } }
                .onFailure { fail("preview part", it.message ?: "Unable to preview $partId.") }
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
        is WorkspaceRetry.GenerateArrangement -> runGenerateArrangement(action.request)
        null -> Unit
    }

    private fun updateArrangementPlanner(planner: ArrangementPlannerKind) {
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(arrangementDraft = it.arrangementDraft.copy(planner = planner), arrangementDraftDirty = true, notification = null) }
    }

    private fun toggleArrangementInstrument(instrument: String) {
        if (state.value.operation.isMutating || instrument == "piano" || instrument !in arrangementInstruments) return
        mutableState.update { current ->
            val selected = current.arrangementDraft.instruments
            current.copy(arrangementDraft = current.arrangementDraft.copy(instruments = if (instrument in selected) selected - instrument else selected + instrument), arrangementDraftDirty = true)
        }
    }

    private fun generateArrangement() {
        val project = state.value.project ?: return fail("generate arrangement", "Open a project before arranging.")
        if (state.value.operation.isMutating) return
        val missing = state.value.structureDraft.toSet().filter { id -> project.parts.find { it.id == id }?.analysis?.status != PartAnalysisStatus.MIDI }
        when {
            state.value.structureDraft.isEmpty() -> fail("generate arrangement", "Add at least one section to the song structure before arranging.")
            missing.isNotEmpty() -> fail("generate arrangement", "Analyze every structure part before arranging: ${missing.joinToString(", ")}.")
            state.value.arrangementDraft.style.trim().length > MAX_STYLE_LENGTH -> fail("generate arrangement", "Style must be at most $MAX_STYLE_LENGTH characters.")
            else -> runGenerateArrangement(
                GenerateArrangementRequest(project.root, state.value.arrangementDraft.planner, state.value.arrangementDraft.style.trim().ifBlank { null }, arrangementInstruments.filter { it in state.value.arrangementDraft.instruments })
            )
        }
    }

    private fun runGenerateArrangement(request: GenerateArrangementRequest) {
        mutableState.update { it.copy(operation = WorkspaceOperation.GeneratingArrangement(), notification = null, retry = null) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    arrangementService.generate(request) { progress ->
                        scope.launch { updateProgress(WorkspaceOperation.GeneratingArrangement(progress)) }
                    }
                }
            }.onSuccess { arrangement ->
                mutableState.update { it.copy(arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, arrangementDraftDirty = false, operation = WorkspaceOperation.Idle, notification = if (arrangement.approvalRequired) "Arrangement draft is ready for review and explicit approval." else "Approved deterministic arrangement generated.", retry = null) }
            }.onFailure { fail("generate arrangement", it.message ?: "Unable to generate arrangement.", WorkspaceRetry.GenerateArrangement(request)) }
        }
    }

    private fun previewArrangement() {
        val project = state.value.project ?: return
        scope.launch {
            runCatching { withContext(ioDispatcher) { arrangementService.preview(project.root) } }
                .onSuccess { arrangement -> mutableState.update { it.copy(arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, notification = "Draft preview is validated. Approve it explicitly to make it current.") } }
                .onFailure { fail("preview arrangement", it.message ?: "Unable to preview arrangement.") }
        }
    }

    private fun approveArrangement() {
        val project = state.value.project ?: return
        val arrangement = state.value.arrangement
        if (arrangement?.approvalRequired != true || arrangement.stale) return
        mutableState.update { it.copy(operation = WorkspaceOperation.ApprovingArrangement, notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { arrangementService.approve(project.root) } }
                .onSuccess { arrangement -> mutableState.update { it.copy(arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, operation = WorkspaceOperation.Idle, notification = "Arrangement approved.") } }
                .onFailure { fail("approve arrangement", it.message ?: "Unable to approve arrangement.") }
        }
    }

    private fun updateProgress(operation: WorkspaceOperation) {
        val progress = when (operation) {
            is WorkspaceOperation.ImportingPart -> operation.progress
            is WorkspaceOperation.AnalyzingPart -> operation.progress
            is WorkspaceOperation.GeneratingArrangement -> operation.progress
            is WorkspaceOperation.ApplyingMix -> operation.progress
            is WorkspaceOperation.BuildingSong -> operation.progress
            else -> null
        }
        progress?.let { operationLogger.event(it.operation, "${it.stageIndex}-of-${it.stageCount}", it.artifact) }
        mutableState.update { current -> if (current.operation.isMutating) current.copy(operation = operation) else current }
    }

    private fun updateMixSetting(instrument: String, setting: LogicalMixSetting) {
        val mix = state.value.mix ?: return
        val settings = mix.settings.copy(tracks = mix.settings.tracks + (instrument to setting))
        mutableState.update { it.copy(mix = mix.copy(settings = settings)) }
        mixCommit?.cancel()
        mixCommit = scope.launch {
            delay(250)
            applyMix(settings)
        }
    }

    private fun resetMix() {
        val root = state.value.project?.root ?: return
        val settings = PersistedMixSettings()
        mutableState.update { current -> current.copy(mix = current.mix?.copy(settings = settings)) }
        applyMix(settings, root)
    }

    private fun applyMix(settings: PersistedMixSettings, root: Path? = state.value.project?.root) {
        val projectRoot = root ?: return
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.ApplyingMix(), notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { mixService.apply(ai.music.workstation.application.ApplyMixRequest(projectRoot, settings)) { progress -> scope.launch { updateProgress(WorkspaceOperation.ApplyingMix(progress)) } } } }
                .onSuccess { snapshot -> mutableState.update { it.copy(mix = snapshot, operation = WorkspaceOperation.Idle, notification = "Updated lossless dry mix from existing stems.") } }
                .onFailure { fail("apply mix", it.message ?: "Unable to apply mix settings.") }
        }
    }

    private fun buildSong() {
        val project = state.value.project ?: return fail("build song", "Open a project before building.")
        val service = buildService ?: return fail("build song", "Build service is not configured for this desktop session.")
        val arrangement = state.value.arrangement
        if (arrangement == null || arrangement.stale || arrangement.approvalRequired || !arrangement.approved) return fail("build song", "Build Song requires a current approved arrangement.")
        val options = state.value.buildOptions
        mutableState.update { it.copy(operation = WorkspaceOperation.BuildingSong(), notification = null, retry = null) }
        buildJob = scope.launch {
            runCatching { withContext(ioDispatcher) { service.build(BuildSongRequest(project.root, options.loFi, options.mp3)) { progress -> scope.launch { updateProgress(WorkspaceOperation.BuildingSong(progress)) } } } }
                .onSuccess {
                    val refreshed = withContext(ioDispatcher) { projectService.open(project.root) }
                    mutableState.update { current -> current.copy(project = refreshed, mix = mixService.load(project.root), operation = WorkspaceOperation.Idle, notification = "Build complete: ${it.master}") }
                }.onFailure {
                    if (it is CancellationException) mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = "Build cancellation requested; the current atomic stage was allowed to finish safely.") }
                    else fail("build song", it.message ?: "Build Song failed.")
                }
        }
    }

    private fun selectPlaybackSource(source: PlaybackSource) {
        player?.stop()
        mutableState.update { it.copy(playback = it.playback.copy(source = source)) }
    }

    private fun playPause() {
        val monitor = player ?: return fail("playback", "Local audio playback is not configured.")
        when (monitor.state.value) {
            ai.music.workstation.audio.PlaybackState.PLAYING -> monitor.pause()
            ai.music.workstation.audio.PlaybackState.PAUSED -> monitor.resume()
            ai.music.workstation.audio.PlaybackState.STOPPED -> {
                val root = state.value.project?.root ?: return
                val artifact = when (state.value.playback.source) {
                    PlaybackSource.DRY -> root.resolve("mix/dry.wav")
                    PlaybackSource.LOFI -> root.resolve("mix/lofi.wav")
                    PlaybackSource.MASTER -> root.resolve("output/master.wav")
                }
                runCatching { monitor.play(artifact) }.onFailure { fail("playback", it.message ?: "Unable to play selected artifact.") }
            }
        }
    }

    private fun updatePlayback() {
        val monitor = player ?: return
        mutableState.update { current -> current.copy(playback = current.playback.copy(state = monitor.state.value, positionSeconds = monitor.currentPosition.value, durationSeconds = monitor.totalDuration.value, volume = monitor.volume.value)) }
    }

    private fun ProjectSnapshot.refreshed(): ProjectSnapshot = projectService.open(root)

    private fun opened(project: ProjectSnapshot, message: String, stale: Boolean = false) {
        player?.stop()
        preferences.saveLastOpenedProject(project.root)
        operationLogger.event("project", "opened", project.root)
        mutableState.update {
            it.copy(
                project = project,
                arrangement = null,
                mix = runCatching { mixService.load(project.root) }.getOrNull(),
                operation = WorkspaceOperation.Idle,
                notification = message,
                dialog = null,
                structureDraft = project.structure.map { section -> section.partId },
                downstreamArtifactsStale = stale,
                arrangementDraftDirty = false,
                retry = null
            )
        }
        refreshArrangement(project.root)
    }

    private fun refreshArrangement(root: Path) = scope.launch {
        val arrangement = withContext(ioDispatcher) { runCatching { arrangementService.load(root) }.getOrNull() }
        mutableState.update { current ->
            if (current.project?.root == root) current.copy(arrangement = arrangement, selectedArrangementSection = arrangement?.sections?.firstOrNull()?.index) else current
        }
    }

    private fun fail(action: String, message: String, retry: WorkspaceRetry? = null) {
        operationLogger.event(action, "failed", failure = IllegalStateException(message))
        mutableState.update { it.copy(operation = WorkspaceOperation.Failed(action, message), notification = message, retry = retry) }
    }

    private fun hasUnsavedDraft(): Boolean = state.value.project != null && state.value.arrangementDraftDirty

    private fun busy(action: String) {
        mutableState.update { it.copy(notification = "Wait for the current operation to reach a safe boundary before you $action.") }
    }

    /** Returns true when the caller may close immediately; otherwise it opens a confirmation dialog. */
    fun requestClose(): Boolean {
        if (state.value.operation.isMutating) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmClose) }
            return false
        }
        if (hasUnsavedDraft()) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmClose) }
            return false
        }
        return true
    }

    override fun close() { mixCommit?.cancel(); buildJob?.cancel(); player?.close(); scope.cancel() }

    private object UnavailableRuntimeReadinessService : RuntimeReadinessService {
        override suspend fun check(): RuntimeReadiness = RuntimeReadiness(
            worker = DependencyReadiness(false, "Worker readiness has not been configured."),
            renderer = DependencyReadiness(false, "Renderer readiness has not been configured.")
        )
    }

    private companion object {
        val arrangementInstruments = listOf("piano", "bass", "drums", "pad", "strings")
        const val MAX_STYLE_LENGTH = 160
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
