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
import ai.music.workstation.application.PreviewRequest
import ai.music.workstation.application.PreviewResult
import ai.music.workstation.application.OperationProgress
import ai.music.workstation.application.PartSourceType
import ai.music.workstation.application.PartAnalysisStatus
import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.application.ProjectSnapshot
import ai.music.workstation.application.AudioPreparationApplicationService
import ai.music.workstation.application.AudioPreparationAvailability
import ai.music.workstation.application.AudioPreparationSnapshot
import ai.music.workstation.application.PreviewAudioSource
import ai.music.workstation.application.SaveStructureRequest
import ai.music.workstation.application.UpdatePartRoleRequest
import ai.music.workstation.arrangement.RenderFormat
import ai.music.workstation.preparation.InputCleanupMode
import ai.music.workstation.preparation.TranscriptionInputArtifact
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
import java.nio.file.Files
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
    val preview: PreviewUiState = PreviewUiState(),
    val selectedPartId: String? = null,
    val audioPreparation: AudioPreparationUiState = AudioPreparationUiState(),
    val arrangementDraft: ArrangementDraft = ArrangementDraft(),
    val selectedArrangementSection: Int? = null,
    val operation: WorkspaceOperation = WorkspaceOperation.Idle,
    val notification: String? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val soundLibrary: SoundLibrarySettingsState = SoundLibrarySettingsState(),
    val dialog: WorkspaceDialog? = null,
    val structureDraft: List<String> = emptyList(),
    val downstreamArtifactsStale: Boolean = false,
    val arrangementDraftDirty: Boolean = false,
    val retry: WorkspaceRetry? = null
)

data class AudioPreparationUiState(
    val partId: String? = null,
    val snapshot: AudioPreparationSnapshot? = null,
    val cleanupMode: InputCleanupMode = InputCleanupMode.INSPECT_ONLY,
    val transcriptionInput: TranscriptionInputArtifact = TranscriptionInputArtifact.SOURCE
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

/** Immutable monitor-only lifecycle for a selected part. It is independent of notifications. */
data class PreviewSourceIdentity(
    val projectRoot: Path,
    val partId: String,
    val artifact: Path? = null,
    val audioSource: PreviewAudioSource = PreviewAudioSource.ORIGINAL
)

enum class PreviewPhase { CHECKING, PREPARING, READY, STARTING, PLAYING, PAUSED, STOPPED, FAILED }

data class PreviewUiState(
    val source: PreviewSourceIdentity? = null,
    val phase: PreviewPhase = PreviewPhase.STOPPED,
    val reason: String? = null,
    val elapsedSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0
)

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
    data class InspectingPart(val id: String) : WorkspaceOperation
    data class ApplyingAudioCleanup(val id: String) : WorkspaceOperation
    data class TranscribingPart(val id: String) : WorkspaceOperation
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
        this is WorkspaceOperation.InspectingPart || this is WorkspaceOperation.ApplyingAudioCleanup || this is WorkspaceOperation.TranscribingPart ||
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
        val role: String = "",
        val detectedType: ImportSourceKind? = null,
        val sourceSizeBytes: Long? = null,
        val validationMessage: String? = null
    ) : WorkspaceDialog

    data class EditRole(val partId: String, val role: String) : WorkspaceDialog
    data class ConfirmSafeCleanup(val partId: String) : WorkspaceDialog
    data class ConfirmDiscardDraft(val root: Path? = null, val createProject: Boolean = false) : WorkspaceDialog
    data object ConfirmClose : WorkspaceDialog
    data object SoundLibrarySettings : WorkspaceDialog
}

enum class ImportSourceKind(val label: String, val isAudio: Boolean) {
    MIDI("MIDI", false),
    WAV("WAV", true),
    MP3("MP3", true),
    UNSUPPORTED("Unsupported", false)
}

internal fun detectImportSourceKind(source: Path): ImportSourceKind = when (
    source.fileName.toString().substringAfterLast('.', "").lowercase()
) {
    "mid", "midi" -> ImportSourceKind.MIDI
    "wav", "wave" -> ImportSourceKind.WAV
    "mp3" -> ImportSourceKind.MP3
    else -> ImportSourceKind.UNSUPPORTED
}

sealed interface WorkspaceRetry {
    data class Import(val request: ImportPartRequest) : WorkspaceRetry
    data class Analyze(val root: Path, val partId: String) : WorkspaceRetry
    data class Inspect(val root: Path, val partId: String) : WorkspaceRetry
    data class Cleanup(val root: Path, val partId: String, val mode: InputCleanupMode) : WorkspaceRetry
    data class Transcribe(val root: Path, val partId: String, val selectedInput: TranscriptionInputArtifact) : WorkspaceRetry
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
    data object ShowSoundLibrarySettings : WorkspaceIntent
    data object ChooseSoundLibraryRoot : WorkspaceIntent
    data object ClearSoundLibraryRoot : WorkspaceIntent
    data object RefreshSoundLibrary : WorkspaceIntent
    data class ShowImportPart(val audio: Boolean) : WorkspaceIntent
    data object ChooseImportSource : WorkspaceIntent
    data class ImportSourceChosen(val source: Path?) : WorkspaceIntent
    data class UpdateImportPart(val draft: WorkspaceDialog.ImportPart) : WorkspaceIntent
    data object ImportPart : WorkspaceIntent
    data class AnalyzePart(val partId: String) : WorkspaceIntent
    data class SelectPart(val partId: String) : WorkspaceIntent
    data object InspectSelectedPart : WorkspaceIntent
    data class SelectCleanupMode(val mode: InputCleanupMode) : WorkspaceIntent
    data object ApplySelectedCleanup : WorkspaceIntent
    data object ConfirmSafeCleanup : WorkspaceIntent
    data class SelectTranscriptionInput(val input: TranscriptionInputArtifact) : WorkspaceIntent
    data object TranscribeSelectedPart : WorkspaceIntent
    data class PreviewPart(val partId: String) : WorkspaceIntent
    data class PreviewPreparation(val source: PreviewAudioSource) : WorkspaceIntent
    data object RetryPreview : WorkspaceIntent
    data object PausePreview : WorkspaceIntent
    data object ResumePreview : WorkspaceIntent
    data object StopPreview : WorkspaceIntent
    data class SeekPreview(val seconds: Double) : WorkspaceIntent
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
    private val libraryRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "personal-ai-music-arranger", "missing-sound-library"),
    private val arrangementService: ArrangementApplicationService = DefaultArrangementApplicationService(libraryRoot = libraryRoot),
    private val mixService: MixApplicationService = DefaultMixApplicationService(),
    private val buildService: BuildApplicationService? = null,
    private val player: ArtifactAudioPlayer? = null,
    private val partPreviewService: PartPreviewApplicationService? = null,
    private val audioPreparationService: AudioPreparationApplicationService? = null,
    private val preferences: DesktopPreferences = NoOpDesktopPreferences,
    private val soundLibrarySettings: SoundLibrarySettingsService = SoundLibrarySettingsService(preferences),
    private val operationLogger: DesktopOperationLogger = NoOpDesktopOperationLogger
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val ioDispatcher = dispatchers.io
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    private var mixCommit: Job? = null
    private var buildJob: Job? = null
    private var previewJob: Job? = null
    private var previewGeneration = 0L
    private var readinessGeneration = 0L

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
            WorkspaceIntent.ShowSoundLibrarySettings -> mutableState.update { it.copy(dialog = WorkspaceDialog.SoundLibrarySettings, soundLibrary = soundLibrarySettings.refresh()) }
            WorkspaceIntent.ChooseSoundLibraryRoot -> chooseSoundLibraryRoot()
            WorkspaceIntent.ClearSoundLibraryRoot -> mutableState.update { it.copy(soundLibrary = soundLibrarySettings.clear()) }
            WorkspaceIntent.RefreshSoundLibrary -> mutableState.update { it.copy(soundLibrary = soundLibrarySettings.refresh()) }
            is WorkspaceIntent.ShowImportPart -> showImportPart(intent.audio)
            WorkspaceIntent.ChooseImportSource -> chooseImportSource()
            is WorkspaceIntent.ImportSourceChosen -> updateImportSource(intent.source)
            is WorkspaceIntent.UpdateImportPart -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.ImportPart -> importPart()
            is WorkspaceIntent.AnalyzePart -> analyzePart(intent.partId)
            is WorkspaceIntent.SelectPart -> selectPart(intent.partId)
            WorkspaceIntent.InspectSelectedPart -> inspectSelectedPart()
            is WorkspaceIntent.SelectCleanupMode -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(cleanupMode = intent.mode)) }
            WorkspaceIntent.ApplySelectedCleanup -> applySelectedCleanup()
            WorkspaceIntent.ConfirmSafeCleanup -> confirmSafeCleanup()
            is WorkspaceIntent.SelectTranscriptionInput -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(transcriptionInput = intent.input)) }
            WorkspaceIntent.TranscribeSelectedPart -> transcribeSelectedPart()
            is WorkspaceIntent.PreviewPart -> previewPart(intent.partId)
            is WorkspaceIntent.PreviewPreparation -> previewPreparation(intent.source)
            WorkspaceIntent.RetryPreview -> state.value.preview.source?.let { previewPart(it.partId) }
            WorkspaceIntent.PausePreview -> pausePreview()
            WorkspaceIntent.ResumePreview -> resumePreview()
            WorkspaceIntent.StopPreview -> stopPreview()
            is WorkspaceIntent.SeekPreview -> seekPreview(intent.seconds)
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
        cancelPreview(resetState = true)
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
        val generation = ++readinessGeneration
        mutableState.update { it.copy(runtimeReadiness = RuntimeReadiness.checking()) }
        runCatching { withContext(ioDispatcher) { runtimeReadinessService.check() } }
            .onSuccess { readiness -> if (generation == readinessGeneration) mutableState.update { it.copy(runtimeReadiness = readiness) } }
            .onFailure { failure -> if (generation == readinessGeneration) mutableState.update { it.copy(notification = "Could not check local readiness: ${failure.message}") } }
    }

    private fun chooseSoundLibraryRoot() = scope.launch {
        val root = fileDialogs.chooseSoundLibraryDirectory() ?: return@launch
        mutableState.update { it.copy(soundLibrary = soundLibrarySettings.select(root)) }
    }

    private fun showImportPart(audio: Boolean) {
        if (state.value.project == null || state.value.operation.isMutating) return
        // The single dialog detects the source type after selection. Keep the former intent
        // parameter only as a compile-safe adapter for existing callers.
        mutableState.update { it.copy(dialog = WorkspaceDialog.ImportPart(audio = false), notification = null) }
    }

    private fun chooseImportSource() = scope.launch {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return@launch
        updateImportSource(fileDialogs.choosePartSource())
    }

    private fun updateImportSource(source: Path?) {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        if (source == null) return
        val type = detectImportSourceKind(source)
        val size = runCatching { Files.size(source) }.getOrNull()
        val message = when {
            type == ImportSourceKind.UNSUPPORTED -> "Unsupported source type. Choose MIDI (.mid/.midi), WAV (.wav/.wave), or MP3 (.mp3)."
            else -> null
        }
        mutableState.update {
            it.copy(dialog = draft.copy(
                audio = type.isAudio,
                source = source,
                detectedType = type,
                sourceSizeBytes = size,
                validationMessage = message
            ))
        }
    }

    private fun importPart() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        val source = draft.source ?: return fail("import part", "Choose a ${if (draft.audio) "WAV or MP3" else "MIDI"} source first.")
        if (draft.detectedType == ImportSourceKind.UNSUPPORTED) return fail("import part", draft.validationMessage ?: "Unsupported source type.")
        if (draft.id.isBlank()) return fail("import part", "Part ID is required and remains stable after import.")
        if (project.parts.any { it.id == draft.id }) return fail("import part", "Part ID already exists: ${draft.id}")
        if (draft.detectedType?.isAudio == true) state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let { return fail("import audio", it) }
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

    private fun selectPart(partId: String) {
        val project = state.value.project ?: return
        val part = project.parts.find { it.id == partId } ?: return
        if (part.sourceType != PartSourceType.AUDIO) {
            mutableState.update { it.copy(selectedPartId = partId, audioPreparation = AudioPreparationUiState(partId = partId), notification = "Audio preparation is available for WAV/MP3 parts only.") }
            return
        }
        mutableState.update { it.copy(selectedPartId = partId, audioPreparation = AudioPreparationUiState(partId = partId), notification = null) }
        loadPreparation(project.root, partId)
    }

    private fun loadPreparation(root: Path, partId: String) {
        val service = audioPreparationService ?: return
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.load(root, partId) } }
                .onSuccess { snapshot ->
                    mutableState.update { current ->
                        if (current.project?.root == root && current.selectedPartId == partId) {
                            current.copy(audioPreparation = current.audioPreparation.copy(snapshot = snapshot))
                        } else current
                    }
                }
                .onFailure { failure ->
                    mutableState.update { current ->
                        if (current.selectedPartId == partId) current.copy(notification = "Could not load preparation report: ${failure.message ?: "unknown error"}") else current
                    }
                }
        }
    }

    private fun inspectSelectedPart() {
        val project = state.value.project ?: return
        val partId = state.value.selectedPartId ?: return fail("inspect part", "Select an audio part before inspection.")
        workerFailure()?.let { return fail("inspect part", it) }
        val service = audioPreparationService ?: return fail("inspect part", "Audio preparation is not configured for this desktop session.")
        mutableState.update { it.copy(operation = WorkspaceOperation.InspectingPart(partId), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.inspect(project.root, partId) } }
                .onSuccess { completedPreparation(it, "Inspection report is ready. Choose inspect-only or review the measured safe cleanup recommendation.") }
                .onFailure { fail("inspect part", it.message ?: "Unable to inspect $partId.", WorkspaceRetry.Inspect(project.root, partId)) }
        }
    }

    private fun applySelectedCleanup() {
        val partId = state.value.selectedPartId ?: return fail("audio cleanup", "Select an audio part before choosing cleanup.")
        val mode = state.value.audioPreparation.cleanupMode
        if (mode == InputCleanupMode.SAFE_CLEANUP) {
            val snapshot = state.value.audioPreparation.snapshot
            if (snapshot?.safeCleanupPlan == null) return fail("audio cleanup", "No measured safe cleanup recommendation is available for this source.")
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmSafeCleanup(partId)) }
        } else runCleanup(partId, mode, confirmed = false)
    }

    private fun confirmSafeCleanup() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmSafeCleanup ?: return
        mutableState.update { it.copy(dialog = null) }
        runCleanup(dialog.partId, InputCleanupMode.SAFE_CLEANUP, confirmed = true)
    }

    private fun runCleanup(partId: String, mode: InputCleanupMode, confirmed: Boolean) {
        val project = state.value.project ?: return
        workerFailure()?.let { return fail("audio cleanup", it) }
        val service = audioPreparationService ?: return fail("audio cleanup", "Audio preparation is not configured for this desktop session.")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApplyingAudioCleanup(partId), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.applyCleanup(project.root, partId, mode, confirmed) } }
                .onSuccess { completedPreparation(it, if (mode == InputCleanupMode.INSPECT_ONLY) "Inspect-only choice recorded. Original audio remains selected for transcription." else "Prepared audio is ready for A/B monitoring and transcription selection.") }
                .onFailure { fail("audio cleanup", it.message ?: "Unable to apply cleanup for $partId.", WorkspaceRetry.Cleanup(project.root, partId, mode)) }
        }
    }

    private fun transcribeSelectedPart() {
        val project = state.value.project ?: return
        val partId = state.value.selectedPartId ?: return fail("transcribe", "Select an audio part before transcription.")
        state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let { return fail("transcribe", it) }
        val service = audioPreparationService ?: return fail("transcribe", "Audio preparation is not configured for this desktop session.")
        val input = state.value.audioPreparation.transcriptionInput
        mutableState.update { it.copy(operation = WorkspaceOperation.TranscribingPart(partId), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.transcribe(project.root, partId, input) } }
                .onSuccess { completedPreparation(it, "Transcription quality gate passed. Analyze $partId next.") }
                .onFailure { fail("transcribe", it.message ?: "Transcription failed for $partId.", WorkspaceRetry.Transcribe(project.root, partId, input)) }
        }
    }

    private fun completedPreparation(result: ai.music.workstation.application.AudioPreparationOperation, message: String) {
        mutableState.update { current ->
            current.copy(
                project = result.project,
                selectedPartId = result.preparation.partId,
                audioPreparation = current.audioPreparation.copy(partId = result.preparation.partId, snapshot = result.preparation),
                operation = WorkspaceOperation.Idle,
                notification = message,
                retry = null
            )
        }
    }

    private fun workerFailure(): String? = state.value.runtimeReadiness?.dependency(RuntimeDependency.WORKER)
        ?.takeUnless { it.available }
        ?.detail ?: if (state.value.runtimeReadiness == null) "Checking local worker readiness before this operation." else null

    private fun previewPreparation(source: PreviewAudioSource) {
        val partId = state.value.selectedPartId ?: return fail("preview preparation", "Select an audio part before A/B preview.")
        previewPart(partId, source)
    }

    private fun previewPart(partId: String, audioSource: PreviewAudioSource = PreviewAudioSource.ORIGINAL) {
        val project = state.value.project ?: return
        val part = project.parts.find { it.id == partId } ?: return
        cancelPreview(resetState = false)
        val generation = previewGeneration
        val source = PreviewSourceIdentity(project.root, partId, audioSource = audioSource)
        val capability = if (part.sourceType == PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
        state.value.runtimeReadiness.capabilityFailure(capability)?.let {
            return setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, it))
        }
        val monitor = player ?: return setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, "Local audio playback is not configured."))
        val previews = partPreviewService ?: return setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, "Part preview service is not configured for this desktop session."))
        setPreview(generation, PreviewUiState(source, PreviewPhase.CHECKING))
        previewJob = scope.launch {
            try {
                val resolved = withContext(ioDispatcher) { previews.resolve(PreviewRequest(project.root, partId, audioSource)) }
                if (!isCurrentPreview(generation, source)) return@launch
                when (resolved) {
                    is PreviewResult.Prerequisite -> setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, resolved.message))
                    is PreviewResult.Failed -> setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, resolved.message))
                    is PreviewResult.Resolved -> {
                        val artifactSource = source.copy(artifact = resolved.artifact)
                        setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.PREPARING))
                        when (val prepared = monitor.prepare(resolved.artifact)) {
                            is PlaybackPrepareResult.Failed -> setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.FAILED, prepared.failure.message))
                            is PlaybackPrepareResult.Ready -> {
                                setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.READY, durationSeconds = prepared.durationSeconds))
                                setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.STARTING, durationSeconds = prepared.durationSeconds))
                                when (val started = monitor.start()) {
                                    PlaybackStartResult.Started -> setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.PLAYING, durationSeconds = prepared.durationSeconds))
                                    is PlaybackStartResult.Failed -> setPreview(generation, PreviewUiState(artifactSource, PreviewPhase.FAILED, started.failure.message, durationSeconds = prepared.durationSeconds))
                                }
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // A newer source, project switch, or close owns the next state.
            } catch (error: Throwable) {
                setPreview(generation, PreviewUiState(source, PreviewPhase.FAILED, error.message ?: "Unable to prepare this preview."))
            }
        }
    }

    private fun pausePreview() {
        if (state.value.preview.phase != PreviewPhase.PLAYING) return
        player?.pause() ?: return
        mutableState.update { it.copy(preview = it.preview.copy(phase = PreviewPhase.PAUSED)) }
    }

    private fun resumePreview() {
        if (state.value.preview.phase != PreviewPhase.PAUSED) return
        player?.resume() ?: return
        mutableState.update { it.copy(preview = it.preview.copy(phase = PreviewPhase.PLAYING)) }
    }

    private fun stopPreview() {
        cancelPreview(resetState = false)
        mutableState.update { current -> current.copy(preview = current.preview.copy(phase = PreviewPhase.STOPPED, reason = null, elapsedSeconds = 0.0)) }
    }

    private fun seekPreview(seconds: Double) {
        val preview = state.value.preview
        if (preview.source?.artifact == null || preview.phase !in setOf(PreviewPhase.READY, PreviewPhase.STARTING, PreviewPhase.PLAYING, PreviewPhase.PAUSED, PreviewPhase.STOPPED)) return
        player?.seek(seconds) ?: return
        mutableState.update { it.copy(preview = preview.copy(elapsedSeconds = seconds.coerceIn(0.0, preview.durationSeconds))) }
    }

    private fun cancelPreview(resetState: Boolean) {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        player?.stop()
        if (resetState) mutableState.update { it.copy(preview = PreviewUiState()) }
    }

    private fun isCurrentPreview(generation: Long, source: PreviewSourceIdentity): Boolean =
        generation == previewGeneration && state.value.project?.root == source.projectRoot && state.value.preview.source?.let { it.projectRoot == source.projectRoot && it.partId == source.partId } != false

    private fun setPreview(generation: Long, preview: PreviewUiState) {
        if (generation != previewGeneration || state.value.project?.root != preview.source?.projectRoot) return
        mutableState.update { it.copy(preview = preview) }
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
        is WorkspaceRetry.Inspect -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = AudioPreparationUiState(partId = action.partId)) }
            inspectSelectedPart()
        }
        is WorkspaceRetry.Cleanup -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = it.audioPreparation.copy(partId = action.partId, cleanupMode = action.mode)) }
            if (action.mode == InputCleanupMode.SAFE_CLEANUP) mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmSafeCleanup(action.partId)) }
            else runCleanup(action.partId, action.mode, confirmed = false)
        }
        is WorkspaceRetry.Transcribe -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = it.audioPreparation.copy(partId = action.partId, transcriptionInput = action.selectedInput)) }
            transcribeSelectedPart()
        }
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
        state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.BUILD_SONG)?.let { return fail("build song", it) }
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
                scope.launch {
                    when (val result = monitor.play(artifact)) {
                        PlaybackStartResult.Started -> Unit
                        is PlaybackStartResult.Failed -> fail("playback", result.failure.message)
                    }
                }
            }
        }
    }

    private fun updatePlayback() {
        val monitor = player ?: return
        mutableState.update { current ->
            val monitorState = monitor.state.value
            val preview = current.preview
            val previewPhase = when {
                preview.source?.artifact == null || preview.phase !in setOf(PreviewPhase.PLAYING, PreviewPhase.PAUSED, PreviewPhase.STARTING) -> preview.phase
                monitorState == ai.music.workstation.audio.PlaybackState.PLAYING -> PreviewPhase.PLAYING
                monitorState == ai.music.workstation.audio.PlaybackState.PAUSED -> PreviewPhase.PAUSED
                else -> PreviewPhase.STOPPED
            }
            current.copy(
                playback = current.playback.copy(state = monitorState, positionSeconds = monitor.currentPosition.value, durationSeconds = monitor.totalDuration.value, volume = monitor.volume.value),
                preview = preview.copy(phase = previewPhase, elapsedSeconds = monitor.currentPosition.value, durationSeconds = if (preview.source?.artifact == null) preview.durationSeconds else monitor.totalDuration.value)
            )
        }
    }

    private fun ProjectSnapshot.refreshed(): ProjectSnapshot = projectService.open(root)

    private fun opened(project: ProjectSnapshot, message: String, stale: Boolean = false) {
        cancelPreview(resetState = true)
        preferences.saveLastOpenedProject(project.root)
        operationLogger.event("project", "opened", project.root)
        mutableState.update {
            it.copy(
                project = project,
                arrangement = null,
                mix = runCatching { mixService.load(project.root) }.getOrNull(),
                selectedPartId = null,
                audioPreparation = AudioPreparationUiState(),
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

    override fun close() { mixCommit?.cancel(); buildJob?.cancel(); cancelPreview(resetState = true); player?.close(); scope.cancel() }

    private object UnavailableRuntimeReadinessService : RuntimeReadinessService {
        override suspend fun check(): RuntimeReadiness = RuntimeReadiness.checking()
    }

    private companion object {
        val arrangementInstruments = listOf("piano", "bass", "drums", "pad", "strings")
        const val MAX_STYLE_LENGTH = 160
    }
}

private fun RuntimeReadiness?.capabilityFailure(capability: RuntimeCapability): String? =
    when (this) {
        null -> "Checking local readiness before ${capability.name.lowercase().replace('_', ' ')}."
        else -> capability(capability).reason
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
