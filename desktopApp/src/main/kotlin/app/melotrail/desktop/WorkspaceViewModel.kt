package app.melotrail.desktop

import app.melotrail.application.AnalyzePartRequest
import app.melotrail.application.ArrangementApplicationService
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.CreateProjectRequest
import app.melotrail.application.DefaultArrangementApplicationService
import app.melotrail.application.GenerateArrangementRequest
import app.melotrail.application.ImportPartRequest
import app.melotrail.application.MixApplicationService
import app.melotrail.application.MixSnapshot
import app.melotrail.application.PersistedMixSettings
import app.melotrail.application.LogicalMixSetting
import app.melotrail.application.DefaultMixApplicationService
import app.melotrail.application.BuildApplicationService
import app.melotrail.application.BuildSongRequest
import app.melotrail.application.PartPreviewApplicationService
import app.melotrail.application.PreviewRequest
import app.melotrail.application.PreviewResult
import app.melotrail.application.OperationProgress
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartAnalysisStatus
import app.melotrail.application.ProjectApplicationService
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.RetryMidiCleanupRequest
import app.melotrail.application.AudioPreparationApplicationService
import app.melotrail.application.AudioPreparationAvailability
import app.melotrail.application.AudioPreparationSnapshot
import app.melotrail.application.PreviewAudioSource
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.UpdatePartRoleRequest
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.MidiCleanupProfile
import app.melotrail.application.MidiQualityStatus
import app.melotrail.preparation.InputCleanupMode
import app.melotrail.preparation.TranscriptionInputArtifact
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
    val playbackSession: PlaybackSession = PlaybackSession(),
    val selectedPartId: String? = null,
    /** UI-only selected canonical artifact; it is never written to project files. */
    val selectedArtifact: CreationArtifactReference? = null,
    val midiQualityReview: MidiQualityReviewDraft = MidiQualityReviewDraft(),
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
    val retry: WorkspaceRetry? = null,
    val workspaceSection: WorkspaceSection = WorkspaceSection.PROJECT
) {
    val creationSelection: CreationSelection
        get() = CreationSelection(selectedPartId, selectedArrangementSection, selectedArtifact)
}

enum class WorkspaceSection(val label: String) {
    PROJECT("Project"),
    STRUCTURE("Structure"),
    ARRANGE("Arrange"),
    MIX_MASTER("Mix & Master"),
    LIBRARY("Library")
}

/** The UI exposes three named cleanup choices only; no worker parameters are editable here. */
data class MidiQualityReviewDraft(val profile: MidiCleanupProfile = MidiCleanupProfile.CONSERVATIVE)

internal fun namedMidiCleanupOptions(profile: MidiCleanupProfile): MidiCleanupOptions = when (profile) {
    MidiCleanupProfile.CONSERVATIVE -> MidiCleanupOptions(profile = profile)
    MidiCleanupProfile.TRANSCRIPTION_SAFE -> MidiCleanupOptions(profile = profile)
    MidiCleanupProfile.TIGHTEN_TIMING -> MidiCleanupOptions(profile = profile, quantize = "1/16", strength = 0.4)
}

data class AudioPreparationUiState(
    val partId: String? = null,
    val snapshot: AudioPreparationSnapshot? = null,
    val cleanupMode: InputCleanupMode = InputCleanupMode.INSPECT_ONLY,
    val transcriptionInput: TranscriptionInputArtifact = TranscriptionInputArtifact.SOURCE
)

data class BuildOptionsDraft(val loFi: Boolean = false, val mp3: Boolean = false)
data class PlaybackSnapshot(
    val source: PlaybackSource = PlaybackSource.DRY,
    val state: app.melotrail.audio.PlaybackState = app.melotrail.audio.PlaybackState.STOPPED,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val volume: Double = 1.0
)
enum class PlaybackSource { DRY, LOFI, MASTER }

/** One immutable source of truth for every audible artifact in the desktop workspace. */
data class PlaybackSession(
    val id: Long = 0L,
    val request: PlaybackRequest? = null,
    val sourceKind: PlaybackSourceKind? = null,
    val artifact: PlaybackArtifactIdentity? = null,
    val phase: PlaybackSessionPhase = PlaybackSessionPhase.STOPPED,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val volume: Double = 1.0,
    val failureStage: PlaybackFailureStage? = null,
    val failureMessage: String? = null,
    val retryAction: PlaybackRetryAction? = null
)

sealed interface PlaybackRequest {
    val projectRoot: Path
    data class Part(override val projectRoot: Path, val partId: String, val audioSource: PreviewAudioSource) : PlaybackRequest
    data class Mix(override val projectRoot: Path, val source: PlaybackSource) : PlaybackRequest
}

data class PlaybackArtifactIdentity(
    val projectRoot: Path,
    val path: Path,
    val partId: String? = null,
    val audioSource: PreviewAudioSource? = null
)

enum class PlaybackSourceKind { SOURCE_AUDIO, PREPARED_AUDIO, MIDI, DRY_MIX, LOFI_MIX, MASTER }
enum class PlaybackSessionPhase { RESOLVING, PREPARING, READY, STARTING, PLAYING, PAUSED, STOPPED, FAILED }
enum class PlaybackRetryAction { RETRY_SAME_SELECTION }

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

/** Compatibility views for the existing Compose widgets; neither owns playback state. */
val WorkspaceUiState.playback: PlaybackSnapshot
    get() {
        val request = playbackSession.request as? PlaybackRequest.Mix
        return PlaybackSnapshot(
            source = request?.source ?: PlaybackSource.DRY,
            state = when (playbackSession.phase) {
                PlaybackSessionPhase.PLAYING -> app.melotrail.audio.PlaybackState.PLAYING
                PlaybackSessionPhase.PAUSED -> app.melotrail.audio.PlaybackState.PAUSED
                else -> app.melotrail.audio.PlaybackState.STOPPED
            },
            positionSeconds = playbackSession.positionSeconds,
            durationSeconds = playbackSession.durationSeconds,
            volume = playbackSession.volume
        )
    }

val WorkspaceUiState.preview: PreviewUiState
    get() {
        val request = playbackSession.request as? PlaybackRequest.Part ?: return PreviewUiState()
        val artifact = playbackSession.artifact?.path
        return PreviewUiState(
            source = PreviewSourceIdentity(request.projectRoot, request.partId, artifact, request.audioSource),
            phase = when (playbackSession.phase) {
                PlaybackSessionPhase.RESOLVING -> PreviewPhase.CHECKING
                PlaybackSessionPhase.PREPARING -> PreviewPhase.PREPARING
                PlaybackSessionPhase.READY -> PreviewPhase.READY
                PlaybackSessionPhase.STARTING -> PreviewPhase.STARTING
                PlaybackSessionPhase.PLAYING -> PreviewPhase.PLAYING
                PlaybackSessionPhase.PAUSED -> PreviewPhase.PAUSED
                PlaybackSessionPhase.STOPPED -> PreviewPhase.STOPPED
                PlaybackSessionPhase.FAILED -> PreviewPhase.FAILED
            },
            reason = playbackSession.failureMessage,
            elapsedSeconds = playbackSession.positionSeconds,
            durationSeconds = playbackSession.durationSeconds
        )
    }

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
    data class RetryingMidiCleanup(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
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
        this is WorkspaceOperation.RetryingMidiCleanup ||
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
    data class ConfirmTightenTiming(val partId: String) : WorkspaceDialog
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
    data class MidiCleanup(val request: RetryMidiCleanupRequest) : WorkspaceRetry
    data class Transcribe(val root: Path, val partId: String, val selectedInput: TranscriptionInputArtifact) : WorkspaceRetry
    data class GenerateArrangement(val request: GenerateArrangementRequest) : WorkspaceRetry
}

sealed interface WorkspaceIntent {
    data class SelectWorkspaceSection(val section: WorkspaceSection) : WorkspaceIntent
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
    data class SelectMidiCleanupProfile(val profile: MidiCleanupProfile) : WorkspaceIntent
    data object RetryMidiCleanup : WorkspaceIntent
    data object ConfirmTightenTiming : WorkspaceIntent
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
    private val libraryRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "melotrail", "missing-sound-library"),
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
    private var playbackJob: Job? = null
    private var playbackSessionId = 0L
    private var readinessGeneration = 0L

    val state: StateFlow<WorkspaceUiState> = mutableState.asStateFlow()

    init {
        player?.let { monitor ->
            scope.launch { monitor.state.collect { updatePlaybackSession() } }
            scope.launch { monitor.currentPosition.collect { updatePlaybackSession() } }
            scope.launch { monitor.totalDuration.collect { updatePlaybackSession() } }
            scope.launch { monitor.volume.collect { updatePlaybackSession() } }
            scope.launch { monitor.failure.collect { failure -> failure?.let(::updatePlaybackFailure) } }
        }
    }

    fun accept(intent: WorkspaceIntent) {
        when (intent) {
            is WorkspaceIntent.SelectWorkspaceSection -> selectWorkspaceSection(intent.section)
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
            is WorkspaceIntent.SelectMidiCleanupProfile -> mutableState.update { it.copy(midiQualityReview = it.midiQualityReview.copy(profile = intent.profile)) }
            WorkspaceIntent.RetryMidiCleanup -> retryMidiCleanup()
            WorkspaceIntent.ConfirmTightenTiming -> confirmTightenTiming()
            is WorkspaceIntent.SelectTranscriptionInput -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(transcriptionInput = intent.input)) }
            WorkspaceIntent.TranscribeSelectedPart -> transcribeSelectedPart()
            is WorkspaceIntent.PreviewPart -> previewPart(intent.partId)
            is WorkspaceIntent.PreviewPreparation -> previewPreparation(intent.source)
            WorkspaceIntent.RetryPreview -> retryPlaybackSession()
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
            WorkspaceIntent.StopPlayback -> stopPlaybackSession()
            is WorkspaceIntent.SeekPlayback -> seekPlaybackSession(intent.seconds)
            is WorkspaceIntent.SetPlaybackVolume -> setPlaybackVolume(intent.volume)
            WorkspaceIntent.GenerateArrangement -> generateArrangement()
            WorkspaceIntent.PreviewArrangement -> previewArrangement()
            WorkspaceIntent.ApproveArrangement -> approveArrangement()
            is WorkspaceIntent.SelectArrangementSection -> selectArrangementSection(intent.index)
            WorkspaceIntent.Retry -> retry()
            WorkspaceIntent.DismissDialog -> mutableState.update { it.copy(dialog = null) }
            WorkspaceIntent.DismissNotification -> mutableState.update { it.copy(notification = null) }
            WorkspaceIntent.ConfirmDiscardDraft -> confirmDiscardDraft()
            WorkspaceIntent.RequestClose -> requestClose()
            WorkspaceIntent.ConfirmClose -> close()
        }
    }

    private fun selectWorkspaceSection(section: WorkspaceSection) {
        if (state.value.operation.isMutating) return busy("change workspace sections")
        mutableState.update { it.copy(workspaceSection = section, notification = null) }
    }

    private fun chooseProject() = scope.launch {
        runCatching { fileDialogs.chooseProjectDirectory() }
            .onSuccess { selected -> selected?.let(::requestOpenProject) }
            .onFailure { fail("open project", it.message ?: "The project chooser could not be opened.") }
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
        runCatching { fileDialogs.chooseNewProjectDirectory() }
            .onSuccess { root ->
                root ?: return@onSuccess
                val dialog = state.value.dialog as? WorkspaceDialog.CreateProject ?: return@onSuccess
                mutableState.update { it.copy(dialog = dialog.copy(root = root, name = dialog.name.ifBlank { root.fileName.toString() })) }
            }
            .onFailure { fail("create project", it.message ?: "The folder chooser could not be opened.") }
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
        cancelPlaybackSession(resetState = true)
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
        runCatching { fileDialogs.chooseSoundLibraryDirectory() }
            .onSuccess { root -> root?.let { mutableState.update { current -> current.copy(soundLibrary = soundLibrarySettings.select(it)) } } }
            .onFailure { fail("sound library", it.message ?: "The library chooser could not be opened.") }
    }

    private fun showImportPart(audio: Boolean) {
        if (state.value.project == null) return fail("import part", "Create or open a project before adding a part.")
        if (state.value.operation.isMutating) return busy("add a part")
        // The single dialog detects the source type after selection. Keep the former intent
        // parameter only as a compile-safe adapter for existing callers.
        mutableState.update { it.copy(dialog = WorkspaceDialog.ImportPart(audio = false), notification = null) }
    }

    private fun chooseImportSource() = scope.launch {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return@launch
        runCatching { fileDialogs.choosePartSource() }
            .onSuccess(::updateImportSource)
            .onFailure { failure ->
                mutableState.update { it.copy(dialog = draft.copy(validationMessage = failure.message ?: "The source chooser could not be opened.")) }
                fail("import part", failure.message ?: "The source chooser could not be opened.")
            }
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
        val source = draft.source ?: return failImportDraft(draft, "Choose a MIDI, WAV, or MP3 source first.")
        if (draft.detectedType == ImportSourceKind.UNSUPPORTED) return failImportDraft(draft, draft.validationMessage ?: "Unsupported source type.")
        if (draft.id.isBlank()) return failImportDraft(draft, "Part ID is required and remains stable after import.")
        if (project.parts.any { it.id == draft.id }) return failImportDraft(draft, "Part ID already exists: ${draft.id}")
        if (draft.detectedType?.isAudio == true) state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let { return failImportDraft(draft, it, "import audio") }
        val request = ImportPartRequest(project.root, draft.id, source, draft.role, transcribe = draft.audio)
        runImport(request)
    }

    private fun failImportDraft(draft: WorkspaceDialog.ImportPart, message: String, action: String = "import part") {
        operationLogger.event(action, "validation-failed", draft.source, IllegalArgumentException(message))
        mutableState.update {
            it.copy(
                operation = WorkspaceOperation.Failed(action, message),
                notification = message,
                dialog = draft.copy(validationMessage = message)
            )
        }
    }

    private fun runImport(request: ImportPartRequest) {
        mutableState.update { it.copy(operation = WorkspaceOperation.ImportingPart(request.id), notification = null, retry = null, dialog = null) }
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
            mutableState.update { it.copy(selectedPartId = partId, selectedArtifact = CreationArtifactReference(CreationArtifactKind.PART_SOURCE, partId), midiQualityReview = MidiQualityReviewDraft(), audioPreparation = AudioPreparationUiState(partId = partId), notification = "Audio preparation is available for WAV/MP3 parts only.") }
            return
        }
        mutableState.update { it.copy(selectedPartId = partId, selectedArtifact = CreationArtifactReference(CreationArtifactKind.PART_SOURCE, partId), midiQualityReview = MidiQualityReviewDraft(), audioPreparation = AudioPreparationUiState(partId = partId), notification = null) }
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

    private fun retryMidiCleanup() {
        val project = state.value.project ?: return fail("MIDI cleanup", "Open a project before retrying MIDI cleanup.")
        val partId = state.value.selectedPartId ?: return fail("MIDI cleanup", "Select a part before retrying MIDI cleanup.")
        val part = project.parts.find { it.id == partId } ?: return fail("MIDI cleanup", "Selected part is no longer available.")
        when (part.preparation.midiQuality.status) {
            MidiQualityStatus.LEGACY_UNKNOWN -> return fail("MIDI cleanup", "This legacy part has no cleanup provenance. Re-import it to create a reviewable raw-to-clean MIDI record.")
            MidiQualityStatus.CURRENT, MidiQualityStatus.STALE_OR_INVALID -> Unit
        }
        val profile = state.value.midiQualityReview.profile
        if (profile == MidiCleanupProfile.TIGHTEN_TIMING) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmTightenTiming(partId)) }
        } else {
            runMidiCleanupRetry(RetryMidiCleanupRequest(project.root, partId, namedMidiCleanupOptions(profile)))
        }
    }

    private fun confirmTightenTiming() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmTightenTiming ?: return
        val project = state.value.project ?: return
        mutableState.update { it.copy(dialog = null) }
        runMidiCleanupRetry(
            RetryMidiCleanupRequest(project.root, dialog.partId, namedMidiCleanupOptions(MidiCleanupProfile.TIGHTEN_TIMING))
        )
    }

    private fun runMidiCleanupRetry(request: RetryMidiCleanupRequest) {
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.RetryingMidiCleanup(request.partId), notification = null, retry = null) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.retryMidiCleanup(request) { progress ->
                        scope.launch { updateProgress(WorkspaceOperation.RetryingMidiCleanup(request.partId, progress)) }
                    }
                }
            }.onSuccess { snapshot ->
                // A MIDI retry changes the clean-MIDI digest. Do not leave the old monitor artifact selected.
                cancelPlaybackSession(resetState = true)
                mutableState.update {
                    it.copy(
                        project = snapshot,
                        arrangement = null,
                        operation = WorkspaceOperation.Idle,
                        notification = "MIDI cleanup retried with ${request.cleanup.profile.name.lowercase().replace('_', '-')}. Preview will resolve a fresh fingerprint; analyze ${request.partId} before arranging.",
                        downstreamArtifactsStale = true,
                        retry = null
                    )
                }
            }.onFailure { failure ->
                fail("MIDI cleanup", failure.message ?: "Unable to retry MIDI cleanup for ${request.partId}.", WorkspaceRetry.MidiCleanup(request))
            }
        }
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

    private fun completedPreparation(result: app.melotrail.application.AudioPreparationOperation, message: String) {
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
        val capability = if (part.sourceType == PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
        startPlaybackSession(PlaybackRequest.Part(project.root, partId, audioSource), capability)
    }

    private fun pausePreview() = pausePlaybackSession()

    private fun resumePreview() = resumePlaybackSession()

    private fun stopPreview() = stopPlaybackSession()

    private fun seekPreview(seconds: Double) = seekPlaybackSession(seconds)

    private fun startPlaybackSession(request: PlaybackRequest, capability: RuntimeCapability? = null) {
        cancelPlaybackSession(resetState = false)
        val id = ++playbackSessionId
        val kind = request.sourceKind()
        mutableState.update { it.copy(playbackSession = PlaybackSession(id, request, kind, volume = player?.volume?.value ?: it.playbackSession.volume, phase = PlaybackSessionPhase.RESOLVING)) }
        capability?.let { needed ->
            state.value.runtimeReadiness.capabilityFailure(needed)?.let { message ->
                return failPlaybackSession(id, PlaybackFailureStage.RESOLUTION, message)
            }
        }
        val monitor = player ?: return failPlaybackSession(id, PlaybackFailureStage.RUNTIME, "Local audio playback is not configured.")
        playbackJob = scope.launch {
            try {
                val artifact = when (request) {
                    is PlaybackRequest.Part -> resolvePartArtifact(request, id) ?: return@launch
                    is PlaybackRequest.Mix -> request.artifact()
                }
                if (!isCurrentPlaybackSession(id)) return@launch
                updatePlaybackSession(id) { it.copy(artifact = artifact, phase = PlaybackSessionPhase.PREPARING) }
                when (val prepared = monitor.prepare(artifact.path)) {
                    is PlaybackPrepareResult.Failed -> failPlaybackSession(id, prepared.failure.stage, prepared.failure.message)
                    is PlaybackPrepareResult.Ready -> {
                        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.READY, durationSeconds = prepared.durationSeconds) }
                        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.STARTING) }
                        when (val started = monitor.start()) {
                            PlaybackStartResult.Started -> updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.PLAYING) }
                            is PlaybackStartResult.Failed -> failPlaybackSession(id, started.failure.stage, started.failure.message)
                        }
                    }
                }
            } catch (_: CancellationException) {
                // The replacing session owns every subsequent callback.
            } catch (error: Throwable) {
                failPlaybackSession(id, PlaybackFailureStage.RUNTIME, error.message ?: "Unable to start local playback.")
            }
        }
    }

    private suspend fun resolvePartArtifact(request: PlaybackRequest.Part, id: Long): PlaybackArtifactIdentity? {
        val previews = partPreviewService ?: run {
            failPlaybackSession(id, PlaybackFailureStage.RUNTIME, "Part preview service is not configured for this desktop session.")
            return null
        }
        return when (val resolved = withContext(ioDispatcher) { previews.resolve(PreviewRequest(request.projectRoot, request.partId, request.audioSource)) }) {
            is PreviewResult.Resolved -> PlaybackArtifactIdentity(request.projectRoot, resolved.artifact, request.partId, request.audioSource)
            is PreviewResult.Prerequisite -> {
                val stage = if (resolved.stage == app.melotrail.application.PreviewStage.DECODE_OR_RENDER) PlaybackFailureStage.RUNTIME else PlaybackFailureStage.RESOLUTION
                failPlaybackSession(id, stage, resolved.message)
                null
            }
            is PreviewResult.Failed -> {
                val stage = if (resolved.stage == app.melotrail.application.PreviewStage.DECODE_OR_RENDER) PlaybackFailureStage.DECODE else PlaybackFailureStage.RESOLUTION
                failPlaybackSession(id, stage, resolved.message)
                null
            }
        }
    }

    private fun pausePlaybackSession() {
        if (state.value.playbackSession.phase != PlaybackSessionPhase.PLAYING) return
        player?.pause() ?: return
        updatePlaybackSession(state.value.playbackSession.id) { it.copy(phase = PlaybackSessionPhase.PAUSED) }
    }

    private fun resumePlaybackSession() {
        val session = state.value.playbackSession
        if (session.phase != PlaybackSessionPhase.PAUSED) return
        val monitor = player ?: return failPlaybackSession(session.id, PlaybackFailureStage.RUNTIME, "Local audio playback is not configured.")
        playbackJob = scope.launch {
            updatePlaybackSession(session.id) { it.copy(phase = PlaybackSessionPhase.STARTING) }
            when (val started = monitor.start()) {
                PlaybackStartResult.Started -> updatePlaybackSession(session.id) { it.copy(phase = PlaybackSessionPhase.PLAYING) }
                is PlaybackStartResult.Failed -> failPlaybackSession(session.id, started.failure.stage, started.failure.message)
            }
        }
    }

    private fun stopPlaybackSession() {
        val session = state.value.playbackSession
        cancelPlaybackSession(resetState = false)
        val stoppedId = ++playbackSessionId
        mutableState.update {
            it.copy(playbackSession = session.copy(id = stoppedId, phase = PlaybackSessionPhase.STOPPED, positionSeconds = 0.0, failureStage = null, failureMessage = null, retryAction = null))
        }
    }

    private fun seekPlaybackSession(seconds: Double) {
        val session = state.value.playbackSession
        if (session.artifact == null || session.phase !in setOf(PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED, PlaybackSessionPhase.STOPPED)) return
        player?.seek(seconds) ?: return
        updatePlaybackSession(session.id) { it.copy(positionSeconds = seconds.coerceIn(0.0, session.durationSeconds)) }
    }

    private fun setPlaybackVolume(volume: Double) {
        player?.setVolume(volume)
        val session = state.value.playbackSession
        updatePlaybackSession(session.id) { it.copy(volume = volume.coerceIn(0.0, 1.0)) }
    }

    private fun retryPlaybackSession() {
        state.value.playbackSession.takeIf { it.retryAction == PlaybackRetryAction.RETRY_SAME_SELECTION }?.request?.let { startPlaybackSession(it, it.requiredCapability()) }
    }

    private fun cancelPlaybackSession(resetState: Boolean) {
        ++playbackSessionId
        playbackJob?.cancel()
        playbackJob = null
        player?.stop()
        if (resetState) mutableState.update { it.copy(playbackSession = PlaybackSession(volume = player?.volume?.value ?: 1.0)) }
    }

    private fun isCurrentPlaybackSession(id: Long): Boolean = id == playbackSessionId && state.value.playbackSession.id == id

    private fun updatePlaybackSession(id: Long, update: (PlaybackSession) -> PlaybackSession) {
        if (!isCurrentPlaybackSession(id)) return
        mutableState.update { current ->
            if (current.playbackSession.id == id) current.copy(playbackSession = update(current.playbackSession)) else current
        }
    }

    private fun failPlaybackSession(id: Long, stage: PlaybackFailureStage, message: String) =
        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.FAILED, failureStage = stage, failureMessage = message, retryAction = PlaybackRetryAction.RETRY_SAME_SELECTION) }

    private fun PlaybackRequest.sourceKind(): PlaybackSourceKind = when (this) {
        is PlaybackRequest.Part -> when (audioSource) {
            PreviewAudioSource.PREPARED_CLEAN -> PlaybackSourceKind.PREPARED_AUDIO
            PreviewAudioSource.ORIGINAL -> if (state.value.project?.parts?.find { it.id == partId }?.sourceType == PartSourceType.MIDI) PlaybackSourceKind.MIDI else PlaybackSourceKind.SOURCE_AUDIO
        }
        is PlaybackRequest.Mix -> when (source) {
            PlaybackSource.DRY -> PlaybackSourceKind.DRY_MIX
            PlaybackSource.LOFI -> PlaybackSourceKind.LOFI_MIX
            PlaybackSource.MASTER -> PlaybackSourceKind.MASTER
        }
    }

    private fun PlaybackRequest.requiredCapability(): RuntimeCapability? = when (this) {
        is PlaybackRequest.Mix -> null
        is PlaybackRequest.Part -> state.value.project?.parts?.find { it.id == partId }?.let {
            if (it.sourceType == PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
        }
    }

    private fun PlaybackRequest.Mix.artifact(): PlaybackArtifactIdentity {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(
            when (source) {
                PlaybackSource.DRY -> "mix/dry.wav"
                PlaybackSource.LOFI -> "mix/lofi.wav"
                PlaybackSource.MASTER -> "output/master.wav"
            }
        ).normalize()
        require(path.startsWith(root)) { "Playback artifact must remain inside the selected project." }
        return PlaybackArtifactIdentity(root, path)
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
        is WorkspaceRetry.MidiCleanup -> {
            mutableState.update { it.copy(selectedPartId = action.request.partId, midiQualityReview = MidiQualityReviewDraft(action.request.cleanup.profile)) }
            if (action.request.cleanup.profile == MidiCleanupProfile.TIGHTEN_TIMING) {
                mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmTightenTiming(action.request.partId)) }
            } else runMidiCleanupRetry(action.request)
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
            is WorkspaceOperation.RetryingMidiCleanup -> operation.progress
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
            runCatching { withContext(ioDispatcher) { mixService.apply(app.melotrail.application.ApplyMixRequest(projectRoot, settings)) { progress -> scope.launch { updateProgress(WorkspaceOperation.ApplyingMix(progress)) } } } }
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
                    val (refreshed, loadedMix) = withContext(ioDispatcher) {
                        projectService.open(project.root) to runCatching { mixService.load(project.root) }
                    }
                    mutableState.update { current ->
                        current.copy(
                            project = refreshed,
                            mix = loadedMix.getOrNull(),
                            operation = WorkspaceOperation.Idle,
                            notification = loadedMix.exceptionOrNull()?.message?.let { warning -> "Build complete: ${it.master}. Mix controls could not be loaded: $warning" }
                                ?: "Build complete: ${it.master}"
                        )
                    }
                }.onFailure {
                    if (it is CancellationException) mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = "Build cancellation requested; the current atomic stage was allowed to finish safely.") }
                    else fail("build song", it.message ?: "Build Song failed.")
                }
        }
    }

    private fun selectPlaybackSource(source: PlaybackSource) {
        val root = state.value.project?.root ?: return
        cancelPlaybackSession(resetState = false)
        val artifact = when (source) {
            PlaybackSource.DRY -> CreationArtifactReference(CreationArtifactKind.DRY_MIX)
            PlaybackSource.LOFI -> CreationArtifactReference(CreationArtifactKind.LOFI_MIX)
            PlaybackSource.MASTER -> CreationArtifactReference(CreationArtifactKind.MASTER)
        }
        val request = PlaybackRequest.Mix(root, source)
        val id = ++playbackSessionId
        mutableState.update { it.copy(playbackSession = PlaybackSession(id, request, request.sourceKind(), request.artifact(), PlaybackSessionPhase.STOPPED, volume = player?.volume?.value ?: it.playbackSession.volume), selectedArtifact = artifact) }
    }

    private fun selectArrangementSection(index: Int?) {
        val current = state.value
        val arrangementPartId = index?.let { selected -> current.arrangement?.sections?.firstOrNull { it.index == selected }?.partId }
        val structurePartId = index?.let { selected -> current.project?.structure?.firstOrNull { it.index == selected }?.partId }
        val artifact = current.arrangement?.let {
            CreationArtifactReference(if (it.approvalRequired) CreationArtifactKind.ARRANGEMENT_DRAFT else CreationArtifactKind.ARRANGEMENT, sectionIndex = index)
        }
        mutableState.update {
            it.copy(
                selectedArrangementSection = index,
                selectedPartId = arrangementPartId ?: structurePartId ?: it.selectedPartId,
                selectedArtifact = artifact ?: it.selectedArtifact
            )
        }
    }

    private fun playPause() {
        val session = state.value.playbackSession
        when (session.phase) {
            PlaybackSessionPhase.PLAYING -> pausePlaybackSession()
            PlaybackSessionPhase.PAUSED -> resumePlaybackSession()
            else -> {
                val request = session.request ?: state.value.project?.root?.let { PlaybackRequest.Mix(it, PlaybackSource.DRY) } ?: return
                startPlaybackSession(request, request.requiredCapability())
            }
        }
    }

    private fun updatePlaybackSession() {
        val monitor = player ?: return
        mutableState.update { current ->
            val session = current.playbackSession
            if (session.artifact == null || session.phase !in setOf(PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED)) return@update current
            val phase = when (monitor.state.value) {
                app.melotrail.audio.PlaybackState.PLAYING -> PlaybackSessionPhase.PLAYING
                app.melotrail.audio.PlaybackState.PAUSED -> PlaybackSessionPhase.PAUSED
                app.melotrail.audio.PlaybackState.STOPPED -> PlaybackSessionPhase.STOPPED
            }
            current.copy(playbackSession = session.copy(phase = phase, positionSeconds = monitor.currentPosition.value, durationSeconds = monitor.totalDuration.value, volume = monitor.volume.value))
        }
    }

    private fun updatePlaybackFailure(failure: PlaybackFailure) {
        val session = state.value.playbackSession
        if (session.artifact != null && session.phase in setOf(PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED, PlaybackSessionPhase.STOPPED)) {
            failPlaybackSession(session.id, failure.stage, failure.message)
        }
    }

    private fun ProjectSnapshot.refreshed(): ProjectSnapshot = projectService.open(root)

    private fun opened(project: ProjectSnapshot, message: String, stale: Boolean = false) {
        cancelPlaybackSession(resetState = true)
        preferences.saveLastOpenedProject(project.root)
        operationLogger.event("project", "opened", project.root)
        val openedMessage = if (project.version == 1) {
            "$message · Legacy v1 project opened. Re-import parts as MIDI-first sources to unlock the current arrangement workflow."
        } else message
        mutableState.update {
            it.copy(
                project = project,
                arrangement = null,
                mix = null,
                selectedPartId = null,
                selectedArtifact = null,
                midiQualityReview = MidiQualityReviewDraft(),
                audioPreparation = AudioPreparationUiState(),
                operation = WorkspaceOperation.Idle,
                notification = openedMessage,
                dialog = null,
                structureDraft = project.structure.map { section -> section.partId },
                downstreamArtifactsStale = stale,
                arrangementDraftDirty = false,
                retry = null,
                workspaceSection = WorkspaceSection.PROJECT
            )
        }
        hydrateProject(project, openedMessage)
    }

    private fun hydrateProject(project: ProjectSnapshot, openedMessage: String) = scope.launch {
        val hydration = withContext(ioDispatcher) {
            val mix = runCatching { mixService.load(project.root) }
            val arrangement = runCatching { arrangementService.load(project.root) }
            mix to arrangement
        }
        val warnings = buildList {
            hydration.first.exceptionOrNull()?.message?.let { add("mix settings could not be loaded: $it") }
            if (project.readiness.arrangementAvailable || project.readiness.songPlanAvailable) {
                hydration.second.exceptionOrNull()?.message?.let { add("arrangement artifacts could not be loaded: $it") }
            }
        }
        mutableState.update { current ->
            if (current.project?.root != project.root) current
            else {
                val arrangement = hydration.second.getOrNull()
                current.copy(
                    mix = hydration.first.getOrNull(),
                    arrangement = arrangement,
                    selectedArrangementSection = arrangement?.sections?.firstOrNull()?.index,
                    notification = if (warnings.isEmpty()) current.notification ?: openedMessage
                    else "$openedMessage Some optional artifacts need attention: ${warnings.joinToString("; ")}"
                )
            }
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

    override fun close() { mixCommit?.cancel(); buildJob?.cancel(); cancelPlaybackSession(resetState = true); player?.close(); scope.cancel() }

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
