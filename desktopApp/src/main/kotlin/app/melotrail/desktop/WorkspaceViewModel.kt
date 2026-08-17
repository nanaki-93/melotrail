package app.melotrail.desktop

import app.melotrail.application.AnalyzePartRequest
import app.melotrail.application.ArrangementApplicationService
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.CreateProjectRequest
import app.melotrail.application.CohesionApplicationService
import app.melotrail.application.CohesionPlannerKind
import app.melotrail.application.CohesionSnapshot
import app.melotrail.application.DefaultCohesionApplicationService
import app.melotrail.application.GenerateCohesionRequest
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
import app.melotrail.application.PrepareMidiRequest
import app.melotrail.application.PrepareMidiOutcome
import app.melotrail.application.ProjectApplicationService
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.RetryMidiCleanupRequest
import app.melotrail.application.SelectMidiFeelRequest
import app.melotrail.application.AudioPreparationApplicationService
import app.melotrail.application.AudioPreparationAvailability
import app.melotrail.application.AudioPreparationSnapshot
import app.melotrail.application.PreviewAudioSource
import app.melotrail.application.PreviewMidiSource
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.UpdatePartRoleRequest
import app.melotrail.application.WorkflowReadModel
import app.melotrail.application.WorkflowReadModelDeriver
import app.melotrail.application.DefaultReleaseExportApplicationService
import app.melotrail.application.ReleaseExportApplicationService
import app.melotrail.application.ReleaseExportFormat
import app.melotrail.application.ReleaseExportInspection
import app.melotrail.application.ReleaseExportRequest
import app.melotrail.application.LocalSoundLibraryInventory
import app.melotrail.application.LocalSoundLibraryInventoryReader
import app.melotrail.application.LocalSoundLibraryInventoryState
import app.melotrail.application.RegistryLocalSoundLibraryInventoryReader
import app.melotrail.application.filtered
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.MidiCleanupProfile
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.application.MidiQualityStatus
import app.melotrail.preparation.InputCleanupMode
import app.melotrail.preparation.TranscriptionInputArtifact
import app.melotrail.commercial.SourceRightsAttestation
import app.melotrail.commercial.SourceRightsClaim
import app.melotrail.commercial.CommercialProvenanceService
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
import java.time.Instant

data class WorkspaceDispatchers(
    val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO
)

data class WorkspaceUiState(
    val project: ProjectSnapshot? = null,
    /** Immutable review model; composables never inspect cohesion files. */
    val cohesion: CohesionSnapshot? = null,
    val arrangement: ArrangementSnapshot? = null,
    val mix: MixSnapshot? = null,
    val buildOptions: BuildOptionsDraft = BuildOptionsDraft(),
    val export: ExportUiState = ExportUiState(),
    val playbackSession: PlaybackSession = PlaybackSession(),
    val selectedPartId: String? = null,
    /** UI-only selected canonical artifact; it is never written to project files. */
    val selectedArtifact: CreationArtifactReference? = null,
    /** A feel choice is deliberately pending until the one apply/re-analysis action runs. */
    val pendingMidiFeel: MidiAnalysisInput? = null,
    val midiQualityReview: MidiQualityReviewDraft = MidiQualityReviewDraft(),
    val audioPreparation: AudioPreparationUiState = AudioPreparationUiState(),
    val arrangementDraft: ArrangementDraft = ArrangementDraft(),
    val cohesionDraft: CohesionDraft = CohesionDraft(),
    /** UI navigation only; planner fields remain in [arrangementDraft] until generation. */
    val arrangeTab: ArrangeTab = ArrangeTab.ARRANGEMENT,
    val selectedArrangementSection: Int? = null,
    val operation: WorkspaceOperation = WorkspaceOperation.Idle,
    val operationFeedback: OperationFeedback = OperationFeedback.idle(),
    val notification: String? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val soundLibrary: SoundLibrarySettingsState = SoundLibrarySettingsState(),
    /** Settings navigation is ephemeral; it never changes a project or playback session. */
    val settingsReturnSection: WorkspaceSection? = null,
    /** Read-only inventory from the validated registry boundary; never project data. */
    val libraryBrowser: LibraryBrowserState = LibraryBrowserState(),
    val dialog: WorkspaceDialog? = null,
    val structureDraft: List<String> = emptyList(),
    /** A canonical occurrence ID, never a row position. It is UI selection only. */
    val selectedStructureOccurrenceId: String? = null,
    val downstreamArtifactsStale: Boolean = false,
    val arrangementDraftDirty: Boolean = false,
    val retry: WorkspaceRetry? = null,
    val workspaceSection: WorkspaceSection = WorkspaceSection.OVERVIEW
) {
    val creationSelection: CreationSelection
        get() = CreationSelection(selectedPartId, selectedArrangementSection, selectedArtifact)
    /** Shared application read model; it never creates a second navigation row. */
    val workflow: WorkflowReadModel
        get() = WorkflowReadModelDeriver.derive(project, arrangement)
}

enum class WorkspaceSection(val label: String) {
    OVERVIEW("Overview"),
    IMPORT("Import"),
    STRUCTURE("Structure"),
    ARRANGE("Arrange"),
    MIX_MASTER("Mix & Master"),
    LIBRARY("Library"),
    VIDEO_PREVIEW("Video Preview"),
    EXPORT("Export"),
    SETTINGS("Settings")
}

enum class LibraryLayout { GRID, LIST }

data class LibraryBrowserState(
    val inventory: LocalSoundLibraryInventory = LocalSoundLibraryInventory(LocalSoundLibraryInventoryState.UNCONFIGURED),
    val query: String = "",
    val category: String? = null,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val selectedId: String? = null,
    val refreshError: String? = null
)

/** The UI exposes three named cleanup choices only; no worker parameters are editable here. */
data class MidiQualityReviewDraft(val profile: MidiCleanupProfile = MidiCleanupProfile.TRANSCRIPTION_SAFE)

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
data class ExportDraft(
    val format: ReleaseExportFormat = ReleaseExportFormat.WAV,
    val filename: String = "song.wav",
    val destination: Path? = null
)
data class ExportUiState(
    val inspection: ReleaseExportInspection? = null,
    val draft: ExportDraft = ExportDraft(),
    val inspecting: Boolean = false
)
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
    data class Part(override val projectRoot: Path, val partId: String, val audioSource: PreviewAudioSource, val midiSource: PreviewMidiSource? = null) : PlaybackRequest
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

/** Supported Arrange views. They expose existing bounded controls and evidence only. */
enum class ArrangeTab(val label: String) {
    ARRANGEMENT("Arrangement"),
    INSTRUMENTS("Instruments"),
    TRANSITIONS("Transitions"),
    PLANNER("Planner")
}

data class CohesionDraft(val planner: CohesionPlannerKind = CohesionPlannerKind.DETERMINISTIC)

sealed interface WorkspaceOperation {
    data object Idle : WorkspaceOperation
    data class OpeningProject(val root: Path) : WorkspaceOperation
    data class CreatingProject(val root: Path) : WorkspaceOperation
    data class ImportingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class AnalyzingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class InspectingPart(val id: String) : WorkspaceOperation
    data class ApplyingAudioCleanup(val id: String) : WorkspaceOperation
    data class RetryingMidiCleanup(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class PreparingMidi(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class SelectingMidiFeel(val id: String) : WorkspaceOperation
    data class TranscribingPart(val id: String) : WorkspaceOperation
    data class UpdatingPartRole(val id: String) : WorkspaceOperation
    data object SavingStructure : WorkspaceOperation
    data class GeneratingCohesion(val progress: OperationProgress? = null) : WorkspaceOperation
    data object ApprovingCohesion : WorkspaceOperation
    data class GeneratingArrangement(val progress: OperationProgress? = null) : WorkspaceOperation
    data class ApplyingMix(val progress: OperationProgress? = null) : WorkspaceOperation
    data class BuildingSong(val progress: OperationProgress? = null) : WorkspaceOperation
    data object ExportingCommercialProvenance : WorkspaceOperation
    data object ExportingRelease : WorkspaceOperation
    data object ApprovingArrangement : WorkspaceOperation
    data class OpenFailed(val message: String) : WorkspaceOperation
    data class Failed(val action: String, val message: String) : WorkspaceOperation
}

val WorkspaceOperation.isMutating: Boolean
    get() = this is WorkspaceOperation.OpeningProject || this is WorkspaceOperation.CreatingProject ||
        this is WorkspaceOperation.ImportingPart || this is WorkspaceOperation.AnalyzingPart ||
        this is WorkspaceOperation.InspectingPart || this is WorkspaceOperation.ApplyingAudioCleanup || this is WorkspaceOperation.TranscribingPart ||
        this is WorkspaceOperation.RetryingMidiCleanup ||
        this is WorkspaceOperation.PreparingMidi ||
        this is WorkspaceOperation.SelectingMidiFeel ||
        this is WorkspaceOperation.UpdatingPartRole || this is WorkspaceOperation.SavingStructure ||
        this is WorkspaceOperation.GeneratingCohesion || this is WorkspaceOperation.ApprovingCohesion ||
        this is WorkspaceOperation.GeneratingArrangement || this is WorkspaceOperation.ApprovingArrangement
        || this is WorkspaceOperation.ApplyingMix || this is WorkspaceOperation.BuildingSong || this is WorkspaceOperation.ExportingCommercialProvenance || this is WorkspaceOperation.ExportingRelease

sealed interface WorkspaceDialog {
    data class CreateProject(
        val root: Path? = null,
        val name: String = "",
        val sampleRate: String = "44100",
        val channels: String = "2"
    ) : WorkspaceDialog

    data class ImportPart(
        val audio: Boolean = false,
        val source: Path? = null,
        val id: String = "",
        val role: String = "",
        val detectedType: ImportSourceKind? = null,
        val sourceSizeBytes: Long? = null,
        val validationMessage: String? = null,
        /** An explicit conservative default; commercial export remains blocked until changed if applicable. */
        val rightsClaim: SourceRightsClaim = SourceRightsClaim.NOT_ESTABLISHED,
        val preference: ImportPreference = ImportPreference.ANY,
        val detailsExpanded: Boolean = false
    ) : WorkspaceDialog

    data class EditRole(val partId: String, val role: String) : WorkspaceDialog
    /** The selected canonical part and its return target travel together; no row-local selection is inferred. */
    data class PartDetails(val partId: String, val focusReturn: PartDetailsFocusReturn) : WorkspaceDialog
    data class ConfirmSafeCleanup(val partId: String) : WorkspaceDialog
    data class ConfirmTightenTiming(val partId: String) : WorkspaceDialog
    data class ConfirmDiscardDraft(val root: Path? = null, val createProject: Boolean = false) : WorkspaceDialog
    data object ConfirmClearSoundLibraryRoot : WorkspaceDialog
    data object ConfirmClose : WorkspaceDialog
}

sealed interface PartDetailsFocusReturn {
    data class ImportedRow(val partId: String) : PartDetailsFocusReturn
    data object ImportPrimaryAction : PartDetailsFocusReturn
}

enum class ImportPreference { ANY, MIDI, AUDIO }

enum class ImportSourceKind(val label: String, val isAudio: Boolean) {
    MIDI("MIDI", false),
    WAV("WAV", true),
    MP3("MP3", true),
    UNSUPPORTED("Unsupported", false)
}

/** Exactly one next action is derived from canonical artifact state, never from a row-local flag. */
sealed interface PartPrimaryAction {
    data class PrepareMidi(val partId: String) : PartPrimaryAction
    data class ReviewRepair(val partId: String) : PartPrimaryAction
    data class InspectOrTranscribeAudio(val partId: String, val inspected: Boolean) : PartPrimaryAction
    data class ApplyLoFiChange(val partId: String) : PartPrimaryAction
    data class Analyze(val partId: String) : PartPrimaryAction
    /** The only canonical state permitted to enter the saved song structure. */
    data class AddToStructure(val partId: String) : PartPrimaryAction
    data class FixIssue(val partId: String) : PartPrimaryAction
}

internal fun primaryPartAction(part: app.melotrail.application.PartSummary, pendingMidiFeel: MidiAnalysisInput? = null): PartPrimaryAction = when {
    pendingMidiFeel != null && pendingMidiFeel != part.preparation.midiFeel.selected -> PartPrimaryAction.ApplyLoFiChange(part.id)
    part.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED -> PartPrimaryAction.ReviewRepair(part.id)
    part.preparation.rawMidi && part.preparation.midiQuality.status == MidiQualityStatus.STALE_OR_INVALID -> PartPrimaryAction.PrepareMidi(part.id)
    part.preparation.rawMidi && part.preparation.midiQuality.status == MidiQualityStatus.LEGACY_UNKNOWN -> PartPrimaryAction.FixIssue(part.id)
    part.preparation.warnings.isNotEmpty() -> PartPrimaryAction.FixIssue(part.id)
    part.sourceType == PartSourceType.AUDIO && !part.preparation.rawMidi && part.analysis?.status != PartAnalysisStatus.MIDI -> PartPrimaryAction.InspectOrTranscribeAudio(part.id, part.preparation.inspected)
    part.preparation.midiQuality.status == MidiQualityStatus.CURRENT && (!part.preparation.analyzed || part.analysis?.status != PartAnalysisStatus.MIDI) -> PartPrimaryAction.Analyze(part.id)
    part.analysis?.status == PartAnalysisStatus.MIDI -> PartPrimaryAction.AddToStructure(part.id)
    else -> PartPrimaryAction.FixIssue(part.id)
}

internal fun PartPrimaryAction.label(): String = when (this) {
    is PartPrimaryAction.PrepareMidi -> "Prepare MIDI"
    is PartPrimaryAction.ReviewRepair -> "Review repair"
    is PartPrimaryAction.InspectOrTranscribeAudio -> if (inspected) "Transcribe solo piano" else "Inspect audio"
    is PartPrimaryAction.ApplyLoFiChange -> "Apply Lo-fi change"
    is PartPrimaryAction.Analyze -> "Analyze"
    is PartPrimaryAction.AddToStructure -> "Go to Structure"
    is PartPrimaryAction.FixIssue -> "Fix issue"
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
    data class PrepareMidi(val request: PrepareMidiRequest) : WorkspaceRetry
    data class ApplyMidiFeel(val root: Path, val partId: String, val input: MidiAnalysisInput) : WorkspaceRetry
    data class Transcribe(val root: Path, val partId: String, val selectedInput: TranscriptionInputArtifact) : WorkspaceRetry
    data class SaveStructure(val root: Path, val partIds: List<String>, val selectedIndex: Int?) : WorkspaceRetry
    data class GenerateArrangement(val request: GenerateArrangementRequest) : WorkspaceRetry
    data class GenerateCohesion(val request: GenerateCohesionRequest) : WorkspaceRetry
}

sealed interface WorkspaceIntent {
    data class SelectWorkspaceSection(val section: WorkspaceSection) : WorkspaceIntent
    /** Opens the one Settings destination while retaining a safe return destination. */
    data object OpenSettings : WorkspaceIntent
    data object BackFromSettings : WorkspaceIntent
    data object ChooseProject : WorkspaceIntent
    data object RestoreLastProject : WorkspaceIntent
    data object ShowCreateProject : WorkspaceIntent
    data object ChooseCreateProjectDirectory : WorkspaceIntent
    data class UpdateCreateProject(val draft: WorkspaceDialog.CreateProject) : WorkspaceIntent
    data object CreateProject : WorkspaceIntent
    data class OpenProject(val root: Path) : WorkspaceIntent
    data object MigrateProject : WorkspaceIntent
    data object RefreshRuntimeReadiness : WorkspaceIntent
    data object ChooseSoundLibraryRoot : WorkspaceIntent
    data object RequestClearSoundLibraryRoot : WorkspaceIntent
    data object ConfirmClearSoundLibraryRoot : WorkspaceIntent
    data object RefreshSoundLibrary : WorkspaceIntent
    data class UpdateLibrarySearch(val query: String) : WorkspaceIntent
    data class SelectLibraryCategory(val category: String?) : WorkspaceIntent
    data class SelectLibraryLayout(val layout: LibraryLayout) : WorkspaceIntent
    data class SelectLibraryInstrument(val id: String?) : WorkspaceIntent
    data class ShowImportPart(val audio: Boolean) : WorkspaceIntent
    data object ShowAddPart : WorkspaceIntent
    data object ChooseImportSource : WorkspaceIntent
    data class ImportSourceChosen(val source: Path?) : WorkspaceIntent
    data class UpdateImportPart(val draft: WorkspaceDialog.ImportPart) : WorkspaceIntent
    data object ImportPart : WorkspaceIntent
    data class PrepareMidi(val partId: String) : WorkspaceIntent
    data class ShowPartDetails(
        val partId: String,
        val focusReturn: PartDetailsFocusReturn = PartDetailsFocusReturn.ImportedRow(partId)
    ) : WorkspaceIntent
    data class AnalyzePart(val partId: String) : WorkspaceIntent
    data class SelectPart(val partId: String) : WorkspaceIntent
    data object InspectSelectedPart : WorkspaceIntent
    data class SelectCleanupMode(val mode: InputCleanupMode) : WorkspaceIntent
    data object ApplySelectedCleanup : WorkspaceIntent
    data object ConfirmSafeCleanup : WorkspaceIntent
    data class SelectMidiCleanupProfile(val profile: MidiCleanupProfile) : WorkspaceIntent
    data object RetryMidiCleanup : WorkspaceIntent
    data object ApproveMidiRepair : WorkspaceIntent
    data class SelectMidiFeel(val input: MidiAnalysisInput) : WorkspaceIntent
    data object ApplyMidiFeelAndReanalyze : WorkspaceIntent
    data object ConfirmTightenTiming : WorkspaceIntent
    data class SelectTranscriptionInput(val input: TranscriptionInputArtifact) : WorkspaceIntent
    data object TranscribeSelectedPart : WorkspaceIntent
    data class PreviewPart(val partId: String) : WorkspaceIntent
    data class PreviewMidiPart(val partId: String, val source: PreviewMidiSource) : WorkspaceIntent
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
    data class SelectStructureOccurrence(val instanceId: String) : WorkspaceIntent
    data class DuplicateStructureOccurrence(val instanceId: String) : WorkspaceIntent
    data class RemoveStructureOccurrence(val instanceId: String) : WorkspaceIntent
    data class MoveStructureOccurrence(val instanceId: String, val earlier: Boolean) : WorkspaceIntent
    /** Index intents remain for existing adapters; new UI routes through canonical occurrence IDs. */
    data class DuplicateStructurePart(val index: Int) : WorkspaceIntent
    data class RemoveStructurePart(val index: Int) : WorkspaceIntent
    data class MoveStructurePart(val fromIndex: Int, val toIndex: Int) : WorkspaceIntent
    data object ClearStructure : WorkspaceIntent
    data class SelectArrangeTab(val tab: ArrangeTab) : WorkspaceIntent
    data class UpdateArrangementPlanner(val planner: ArrangementPlannerKind) : WorkspaceIntent
    data class UpdateCohesionPlanner(val planner: CohesionPlannerKind) : WorkspaceIntent
    data class UpdateArrangementStyle(val style: String) : WorkspaceIntent
    data class ToggleArrangementInstrument(val instrument: String) : WorkspaceIntent
    data class UpdateMixSetting(val instrument: String, val setting: LogicalMixSetting) : WorkspaceIntent
    data object ResetMix : WorkspaceIntent
    data class UpdateBuildOptions(val options: BuildOptionsDraft) : WorkspaceIntent
    data object BuildSong : WorkspaceIntent
    data object ExportCommercialProvenance : WorkspaceIntent
    data object RefreshExport : WorkspaceIntent
    data class UpdateExportDraft(val draft: ExportDraft) : WorkspaceIntent
    data object ChooseExportDestination : WorkspaceIntent
    data object ExportSong : WorkspaceIntent
    data object CancelOperation : WorkspaceIntent
    data class SelectPlaybackSource(val source: PlaybackSource) : WorkspaceIntent
    data object PlayPause : WorkspaceIntent
    data object StopPlayback : WorkspaceIntent
    data class SeekPlayback(val seconds: Double) : WorkspaceIntent
    data class SetPlaybackVolume(val volume: Double) : WorkspaceIntent
    data object GenerateArrangement : WorkspaceIntent
    data object GenerateCohesion : WorkspaceIntent
    data object ApproveCohesion : WorkspaceIntent
    data object RejectCohesion : WorkspaceIntent
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
    private val cohesionService: CohesionApplicationService = DefaultCohesionApplicationService(),
    private val mixService: MixApplicationService = DefaultMixApplicationService(),
    private val buildService: BuildApplicationService? = null,
    private val player: ArtifactAudioPlayer? = null,
    private val partPreviewService: PartPreviewApplicationService? = null,
    private val audioPreparationService: AudioPreparationApplicationService? = null,
    private val preferences: DesktopPreferences = NoOpDesktopPreferences,
    private val soundLibrarySettings: SoundLibrarySettingsService = SoundLibrarySettingsService(preferences),
    private val soundLibraryInventory: LocalSoundLibraryInventoryReader = RegistryLocalSoundLibraryInventoryReader,
    private val operationLogger: DesktopOperationLogger = NoOpDesktopOperationLogger,
    private val commercialProvenanceService: CommercialProvenanceService = CommercialProvenanceService(libraryRoot),
    private val releaseExportService: ReleaseExportApplicationService = DefaultReleaseExportApplicationService()
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val ioDispatcher = dispatchers.io
    private val mutableState = MutableStateFlow(WorkspaceUiState())
    private var mixCommit: Job? = null
    private var buildJob: Job? = null
    /** Import and guided preparation are cancellable at their artifact-safe boundaries. */
    private var importPreparationJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackSessionId = 0L
    private var playbackFeedbackSessionId: Long? = null
    private var playbackFeedbackId: String? = null
    private var readinessGeneration = 0L
    private val feedbackTracker = OperationFeedbackTracker()

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
            WorkspaceIntent.OpenSettings -> openSettings()
            WorkspaceIntent.BackFromSettings -> backFromSettings()
            WorkspaceIntent.ChooseProject -> chooseProject()
            WorkspaceIntent.RestoreLastProject -> restoreLastProject()
            WorkspaceIntent.ShowCreateProject -> showCreateProject()
            WorkspaceIntent.ChooseCreateProjectDirectory -> chooseCreateProjectDirectory()
            is WorkspaceIntent.UpdateCreateProject -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.CreateProject -> createProject()
            is WorkspaceIntent.OpenProject -> requestOpenProject(intent.root)
            WorkspaceIntent.MigrateProject -> migrateProject()
            WorkspaceIntent.RefreshRuntimeReadiness -> refreshRuntimeReadiness()
            WorkspaceIntent.ChooseSoundLibraryRoot -> chooseSoundLibraryRoot()
            WorkspaceIntent.RequestClearSoundLibraryRoot -> requestClearSoundLibraryRoot()
            WorkspaceIntent.ConfirmClearSoundLibraryRoot -> clearSoundLibraryRoot()
            WorkspaceIntent.RefreshSoundLibrary -> refreshSoundLibrary()
            is WorkspaceIntent.UpdateLibrarySearch -> mutableState.update { it.copy(libraryBrowser = it.libraryBrowser.copy(query = intent.query, selectedId = selectedLibraryId(it.libraryBrowser, intent.query, it.libraryBrowser.category))) }
            is WorkspaceIntent.SelectLibraryCategory -> mutableState.update { it.copy(libraryBrowser = it.libraryBrowser.copy(category = intent.category, selectedId = selectedLibraryId(it.libraryBrowser, it.libraryBrowser.query, intent.category))) }
            is WorkspaceIntent.SelectLibraryLayout -> mutableState.update { it.copy(libraryBrowser = it.libraryBrowser.copy(layout = intent.layout)) }
            is WorkspaceIntent.SelectLibraryInstrument -> mutableState.update { current ->
                val valid = intent.id?.takeIf { id -> current.libraryBrowser.inventory.instruments.any { it.id == id } }
                current.copy(libraryBrowser = current.libraryBrowser.copy(selectedId = valid))
            }
            is WorkspaceIntent.ShowImportPart -> showImportPart(intent.audio)
            WorkspaceIntent.ShowAddPart -> showAddPart()
            WorkspaceIntent.ChooseImportSource -> chooseImportSource()
            is WorkspaceIntent.ImportSourceChosen -> updateImportSource(intent.source)
            is WorkspaceIntent.UpdateImportPart -> mutableState.update { it.copy(dialog = intent.draft) }
            WorkspaceIntent.ImportPart -> importPart()
            is WorkspaceIntent.PrepareMidi -> prepareMidi(intent.partId)
            is WorkspaceIntent.ShowPartDetails -> showPartDetails(intent)
            is WorkspaceIntent.AnalyzePart -> analyzePart(intent.partId)
            is WorkspaceIntent.SelectPart -> selectPart(intent.partId)
            WorkspaceIntent.InspectSelectedPart -> inspectSelectedPart()
            is WorkspaceIntent.SelectCleanupMode -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(cleanupMode = intent.mode)) }
            WorkspaceIntent.ApplySelectedCleanup -> applySelectedCleanup()
            WorkspaceIntent.ConfirmSafeCleanup -> confirmSafeCleanup()
            is WorkspaceIntent.SelectMidiCleanupProfile -> mutableState.update { it.copy(midiQualityReview = it.midiQualityReview.copy(profile = intent.profile)) }
            WorkspaceIntent.RetryMidiCleanup -> retryMidiCleanup()
            WorkspaceIntent.ApproveMidiRepair -> approveMidiRepair()
            is WorkspaceIntent.SelectMidiFeel -> selectMidiFeel(intent.input)
            WorkspaceIntent.ApplyMidiFeelAndReanalyze -> applyMidiFeelAndReanalyze()
            WorkspaceIntent.ConfirmTightenTiming -> confirmTightenTiming()
            is WorkspaceIntent.SelectTranscriptionInput -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(transcriptionInput = intent.input)) }
            WorkspaceIntent.TranscribeSelectedPart -> transcribeSelectedPart()
            is WorkspaceIntent.PreviewPart -> previewPart(intent.partId)
            is WorkspaceIntent.PreviewMidiPart -> previewMidiPart(intent.partId, intent.source)
            is WorkspaceIntent.PreviewPreparation -> previewPreparation(intent.source)
            WorkspaceIntent.RetryPreview -> retryPlaybackSession()
            WorkspaceIntent.PausePreview -> pausePreview()
            WorkspaceIntent.ResumePreview -> resumePreview()
            WorkspaceIntent.StopPreview -> stopPreview()
            is WorkspaceIntent.SeekPreview -> seekPreview(intent.seconds)
            is WorkspaceIntent.ShowRoleEditor -> showRoleEditor(intent.partId)
            is WorkspaceIntent.UpdateRole -> updateRole(intent.role)
            WorkspaceIntent.SaveRole -> saveRole()
            is WorkspaceIntent.AddStructurePart -> addStructurePart(intent.partId)
            is WorkspaceIntent.SelectStructureOccurrence -> selectStructureOccurrence(intent.instanceId)
            is WorkspaceIntent.DuplicateStructureOccurrence -> structureIndex(intent.instanceId)?.let(::duplicateStructurePart)
            is WorkspaceIntent.RemoveStructureOccurrence -> structureIndex(intent.instanceId)?.let(::removeStructurePart)
            is WorkspaceIntent.MoveStructureOccurrence -> structureIndex(intent.instanceId)?.let { index ->
                moveStructurePart(index, index + if (intent.earlier) -1 else 1)
            }
            is WorkspaceIntent.DuplicateStructurePart -> duplicateStructurePart(intent.index)
            is WorkspaceIntent.RemoveStructurePart -> removeStructurePart(intent.index)
            is WorkspaceIntent.MoveStructurePart -> moveStructurePart(intent.fromIndex, intent.toIndex)
            WorkspaceIntent.ClearStructure -> saveStructure(emptyList())
            is WorkspaceIntent.SelectArrangeTab -> mutableState.update { it.copy(arrangeTab = intent.tab) }
            is WorkspaceIntent.UpdateArrangementPlanner -> updateArrangementPlanner(intent.planner)
            is WorkspaceIntent.UpdateCohesionPlanner -> mutableState.update { it.copy(cohesionDraft = it.cohesionDraft.copy(planner = intent.planner), notification = null) }
            is WorkspaceIntent.UpdateArrangementStyle -> mutableState.update { it.copy(arrangementDraft = it.arrangementDraft.copy(style = intent.style), arrangementDraftDirty = true) }
            is WorkspaceIntent.ToggleArrangementInstrument -> toggleArrangementInstrument(intent.instrument)
            is WorkspaceIntent.UpdateMixSetting -> updateMixSetting(intent.instrument, intent.setting)
            WorkspaceIntent.ResetMix -> resetMix()
            is WorkspaceIntent.UpdateBuildOptions -> mutableState.update { it.copy(buildOptions = intent.options) }
            WorkspaceIntent.BuildSong -> buildSong()
            WorkspaceIntent.ExportCommercialProvenance -> exportCommercialProvenance()
            WorkspaceIntent.RefreshExport -> refreshExport()
            is WorkspaceIntent.UpdateExportDraft -> mutableState.update { it.copy(export = it.export.copy(draft = intent.draft)) }
            WorkspaceIntent.ChooseExportDestination -> chooseExportDestination()
            WorkspaceIntent.ExportSong -> exportSong()
            WorkspaceIntent.CancelOperation -> cancelOperation()
            is WorkspaceIntent.SelectPlaybackSource -> selectPlaybackSource(intent.source)
            WorkspaceIntent.PlayPause -> playPause()
            WorkspaceIntent.StopPlayback -> stopPlaybackSession()
            is WorkspaceIntent.SeekPlayback -> seekPlaybackSession(intent.seconds)
            is WorkspaceIntent.SetPlaybackVolume -> setPlaybackVolume(intent.volume)
            WorkspaceIntent.GenerateArrangement -> generateArrangement()
            WorkspaceIntent.GenerateCohesion -> generateCohesion()
            WorkspaceIntent.ApproveCohesion -> approveCohesion()
            WorkspaceIntent.RejectCohesion -> rejectCohesion()
            WorkspaceIntent.PreviewArrangement -> previewArrangement()
            WorkspaceIntent.ApproveArrangement -> approveArrangement()
            is WorkspaceIntent.SelectArrangementSection -> selectArrangementSection(intent.index)
            WorkspaceIntent.Retry -> retry()
            WorkspaceIntent.DismissDialog -> mutableState.update { it.copy(dialog = null) }
            WorkspaceIntent.DismissNotification -> mutableState.update { current ->
                feedbackTracker.dismiss(current.operationFeedback.sessionId)
                current.copy(notification = null, operationFeedback = feedbackTracker.current)
            }
            WorkspaceIntent.ConfirmDiscardDraft -> confirmDiscardDraft()
            WorkspaceIntent.RequestClose -> requestClose()
            WorkspaceIntent.ConfirmClose -> close()
        }
    }

    private fun selectWorkspaceSection(section: WorkspaceSection) {
        mutableState.update { current ->
            current.copy(
                workspaceSection = section,
                settingsReturnSection = when {
                    section == WorkspaceSection.SETTINGS && current.workspaceSection != WorkspaceSection.SETTINGS -> current.workspaceSection
                    section != WorkspaceSection.SETTINGS -> null
                    else -> current.settingsReturnSection
                }
            )
        }
        if (section == WorkspaceSection.EXPORT) refreshExport()
        if (section == WorkspaceSection.LIBRARY) refreshSoundLibrary()
        if (section == WorkspaceSection.SETTINGS) {
            refreshSoundLibrary()
            refreshRuntimeReadiness()
        }
    }

    private fun openSettings() = selectWorkspaceSection(WorkspaceSection.SETTINGS)

    private fun backFromSettings() {
        val destination = state.value.settingsReturnSection ?: WorkspaceSection.OVERVIEW
        mutableState.update { it.copy(workspaceSection = destination, settingsReturnSection = null) }
        if (destination == WorkspaceSection.EXPORT) refreshExport()
        if (destination == WorkspaceSection.LIBRARY) refreshSoundLibrary()
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
        val feedbackId = beginFeedback(OperationKind.PROJECT_OPEN, OperationPhase.LOCAL, "Creating project ${root.fileName}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.CreatingProject(root), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.create(request).refreshed() } }
                .onSuccess { opened(it, "Created ${it.name}", feedbackId, resetWorkspace = true) }
                .onFailure { fail("create project", it.message ?: "Unable to create project.", sessionId = feedbackId) }
        }
    }

    private fun requestOpenProject(root: Path) {
        if (state.value.operation.isMutating) {
            if (state.value.operation is WorkspaceOperation.ImportingPart || state.value.operation is WorkspaceOperation.PreparingMidi) {
                importPreparationJob?.cancel()
                mutableState.update { it.copy(operation = WorkspaceOperation.Idle, retry = null) }
                openProject(root)
            } else busy("switch projects")
            return
        }
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
        val feedbackId = beginFeedback(OperationKind.PROJECT_OPEN, OperationPhase.LOCAL, "Opening ${normalized.fileName}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(normalized), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.open(normalized) } }
            .onSuccess { opened(it, "Opened ${it.name}", feedbackId, resetWorkspace = true) }
                .onFailure { failure ->
                    if (restoring) preferences.clearLastOpenedProject()
                    operationLogger.event("open-project", "failed", normalized, failure)
                    mutableState.update {
                        it.copy(
                            operation = WorkspaceOperation.OpenFailed(failure.message ?: "Unable to open project."),
                            notification = "Unable to open project: ${failure.message ?: "Unknown error"}",
                            operationFeedback = feedbackTracker.fail(feedbackId, "Unable to open project: ${failure.message ?: "Unknown error"}") ?: it.operationFeedback
                        )
                    }
                }
        }
    }

    private fun migrateProject() {
        val project = state.value.project ?: return fail("project migration", "Open a v2 project first.")
        if (project.version != 2) return fail("project migration", "Only readable v2 projects require migration.")
        if (state.value.operation.isMutating) return busy("migrate project")
        val feedbackId = beginFeedback(OperationKind.PROJECT_OPEN, OperationPhase.LOCAL, "Migrating ${project.name} to schema v3…")
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(project.root), operationFeedback = feedbackTracker.current, notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.migrateV2(project.root) } }
                .onSuccess { opened(it, "Migrated ${it.name} to project schema v3", feedbackId) }
                .onFailure { fail("project migration", it.message ?: "Unable to migrate project.", sessionId = feedbackId) }
        }
    }

    private fun refreshRuntimeReadiness() = scope.launch {
        val generation = ++readinessGeneration
        mutableState.update { it.copy(runtimeReadiness = RuntimeReadiness.checking()) }
        runCatching { withContext(ioDispatcher) { runtimeReadinessService.check() } }
            .onSuccess { readiness -> if (generation == readinessGeneration) mutableState.update { it.copy(runtimeReadiness = readiness) } }
            .onFailure { failure -> if (generation == readinessGeneration) mutableState.update { it.copy(notification = "Could not check local readiness: ${failure.message}") } }
    }

    private fun refreshSoundLibrary() {
        val settings = soundLibrarySettings.refresh()
        updateSoundLibrary(settings)
    }

    private fun updateSoundLibrary(settings: SoundLibrarySettingsState) {
        val inventoryResult = runCatching { soundLibraryInventory.read(settings.resolvedRoot) }
        mutableState.update { current ->
            val inventory = inventoryResult.getOrElse {
                LocalSoundLibraryInventory(
                    LocalSoundLibraryInventoryState.INVALID,
                    recoveryMessage = it.message ?: "The local sound-library inventory could not be refreshed."
                )
            }
            val browser = current.libraryBrowser.copy(
                inventory = inventory,
                selectedId = selectedLibraryId(current.libraryBrowser.copy(inventory = inventory), current.libraryBrowser.query, current.libraryBrowser.category),
                refreshError = inventoryResult.exceptionOrNull()?.message
            )
            current.copy(
                soundLibrary = settings,
                libraryBrowser = browser,
                dialog = current.dialog
            )
        }
    }

    private fun selectedLibraryId(browser: LibraryBrowserState, query: String, category: String?): String? {
        val filtered = browser.inventory.filtered(query, category)
        return browser.selectedId?.takeIf { selected -> filtered.any { it.id == selected } } ?: filtered.firstOrNull()?.id
    }

    private fun chooseSoundLibraryRoot() = scope.launch {
        runCatching { fileDialogs.chooseSoundLibraryDirectory() }
            .onSuccess { root ->
                if (root == null) {
                    mutableState.update { it.copy(notification = "Sound-library selection cancelled; the current setting was kept.") }
                } else {
                    updateSoundLibrary(soundLibrarySettings.select(root))
                    refreshRuntimeReadiness()
                }
            }
            .onFailure { fail("sound library", it.message ?: "The library chooser could not be opened.") }
    }

    private fun requestClearSoundLibraryRoot() {
        if (state.value.soundLibrary.selectionDisabledReason != null) return
        if (state.value.soundLibrary.resolvedRoot == null) return
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmClearSoundLibraryRoot) }
    }

    private fun clearSoundLibraryRoot() {
        updateSoundLibrary(soundLibrarySettings.clear())
        mutableState.update { it.copy(dialog = null, notification = "Cleared the saved sound-library preference.") }
        refreshRuntimeReadiness()
    }

    private fun showImportPart(audio: Boolean) {
        if (state.value.project == null) return fail("import part", "Create or open a project before adding a part.")
        if (state.value.operation.isMutating) return busy("add a part")
        mutableState.update {
            it.copy(dialog = WorkspaceDialog.ImportPart(audio = audio, preference = if (audio) ImportPreference.AUDIO else ImportPreference.MIDI), notification = null)
        }
    }

    private fun showAddPart() {
        if (state.value.project == null) return fail("import part", "Create or open a project before adding a part.")
        if (state.value.operation.isMutating) return busy("add a part")
        mutableState.update { it.copy(dialog = WorkspaceDialog.ImportPart(), notification = null) }
    }

    private fun chooseImportSource() = scope.launch {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return@launch
        runCatching { fileDialogs.choosePartSource(draft.preference) }
            .onSuccess(::updateImportSource)
            .onFailure { failure ->
                mutableState.update { it.copy(dialog = draft.copy(validationMessage = failure.message ?: "The source chooser could not be opened.")) }
                fail("import part", failure.message ?: "The source chooser could not be opened.")
            }
    }

    private fun updateImportSource(source: Path?) {
        if (source == null) return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: WorkspaceDialog.ImportPart()
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
                id = autoPartId(source, it.project?.parts.orEmpty()),
                detectedType = type,
                sourceSizeBytes = size,
                validationMessage = message
            ))
        }
    }

    private fun autoPartId(source: Path, parts: List<app.melotrail.application.PartSummary>): String {
        val base = source.fileName.toString().substringBeforeLast('.').lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifBlank { "part" }.take(48)
        val existing = parts.map { it.id.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }.map { index -> if (index == 1) base else "$base-$index" }
            .first { it.lowercase() !in existing }
    }

    private fun importPart() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        val source = draft.source ?: return failImportDraft(draft, "Choose a MIDI, WAV, or MP3 source first.")
        if (draft.detectedType == ImportSourceKind.UNSUPPORTED) return failImportDraft(draft, draft.validationMessage ?: "Unsupported source type.")
        if (draft.id.isBlank()) return failImportDraft(draft, "Part ID is required and remains stable after import.")
        if (project.parts.any { it.id == draft.id }) return failImportDraft(draft, "Part ID already exists: ${draft.id}")
        if (draft.detectedType?.isAudio == true) state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let { return failImportDraft(draft, it, "import audio") }
        val request = ImportPartRequest(
            project.root, draft.id, source, draft.role, transcribe = draft.audio,
            sourceAttestation = SourceRightsAttestation(draft.rightsClaim, Instant.now().toString())
        )
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
        val feedbackId = beginFeedback(OperationKind.IMPORT, OperationPhase.VALIDATING, "Validating import for ${request.id}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ImportingPart(request.id), notification = null, retry = null, dialog = null, operationFeedback = feedbackTracker.current) }
        importPreparationJob = scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.importPart(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.ImportingPart(request.id, progress)) }
                    }.refreshed()
                }
            }.onSuccess { opened(it, "Imported ${request.id}", feedbackId) }
                .onFailure { failure -> if (failure !is CancellationException) fail("import part", failure.message ?: "Unable to import ${request.id}.", WorkspaceRetry.Import(request), feedbackId) }
        }
    }

    private fun analyzePart(partId: String) {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating || project.parts.none { it.id == partId }) return
        val request = AnalyzePartRequest(project.root, partId)
        val feedbackId = beginFeedback(OperationKind.COHESION, OperationPhase.VALIDATING, "Analyzing ${partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.AnalyzingPart(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.analyzePart(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.AnalyzingPart(partId, progress)) }
                    }.refreshed()
                }
            }.onSuccess { opened(it, "Analyzed $partId", feedbackId) }
                .onFailure { fail("analyze part", it.message ?: "Unable to analyze $partId.", WorkspaceRetry.Analyze(project.root, partId), feedbackId) }
        }
    }

    private fun prepareMidi(partId: String) {
        val project = state.value.project ?: return fail("Prepare MIDI", "Open a project before preparing MIDI.")
        val part = project.parts.find { it.id == partId } ?: return fail("Prepare MIDI", "Part '$partId' is no longer available.")
        if (!part.preparation.rawMidi) return fail("Prepare MIDI", "Part '$partId' has no immutable raw MIDI to prepare.")
        if (state.value.operation.isMutating) return
        val request = PrepareMidiRequest(project.root, partId)
        val feedbackId = beginFeedback(OperationKind.MIDI_REPAIR, OperationPhase.WAITING_FOR_WORKER, "Preparing MIDI for $partId…")
        mutableState.update { it.copy(operation = WorkspaceOperation.PreparingMidi(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        importPreparationJob = scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.prepareMidi(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.PreparingMidi(partId, progress)) }
                    }
                }
            }.onSuccess { result ->
                cancelPlaybackSession(resetState = true)
                val message = when (result.outcome) {
                    PrepareMidiOutcome.APPROVAL_REQUIRED -> "Repair is ready for review before analysis."
                    PrepareMidiOutcome.READY_FOR_STRUCTURE -> "MIDI prepared and analyzed. Add $partId to structure when ready."
                }
                mutableState.update { current ->
                    current.copy(project = result.project, selectedPartId = partId, operation = WorkspaceOperation.Idle,
                        notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback,
                        retry = null, downstreamArtifactsStale = true)
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                fail("Prepare MIDI", failure.message ?: "Unable to prepare MIDI for $partId.", WorkspaceRetry.PrepareMidi(request), feedbackId)
            }
        }
    }

    private fun showPartDetails(intent: WorkspaceIntent.ShowPartDetails) {
        val project = state.value.project ?: return
        val part = project.parts.find { it.id == intent.partId } ?: return
        mutableState.update {
            it.copy(
                selectedPartId = part.id,
                selectedArtifact = CreationArtifactReference(CreationArtifactKind.PART_SOURCE, part.id),
                midiQualityReview = MidiQualityReviewDraft(),
                audioPreparation = AudioPreparationUiState(partId = part.id),
                dialog = WorkspaceDialog.PartDetails(part.id, intent.focusReturn),
                notification = null
            )
        }
        if (part.sourceType == PartSourceType.AUDIO) loadPreparation(project.root, part.id)
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
        val feedbackId = beginFeedback(OperationKind.INSPECTION, OperationPhase.WAITING_FOR_WORKER, "Inspecting preserved source for ${partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.InspectingPart(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.inspect(project.root, partId) } }
                .onSuccess { completedPreparation(it, "Inspection report is ready. Choose inspect-only or review the measured safe cleanup recommendation.", feedbackId) }
                .onFailure { fail("inspect part", it.message ?: "Unable to inspect $partId.", WorkspaceRetry.Inspect(project.root, partId), feedbackId) }
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
            MidiQualityStatus.APPROVAL_REQUIRED -> return fail("MIDI repair", "Review the repair report and explicitly approve it before analysis.")
        }
        val profile = state.value.midiQualityReview.profile
        if (profile == MidiCleanupProfile.TIGHTEN_TIMING) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmTightenTiming(partId)) }
        } else {
            runMidiCleanupRetry(RetryMidiCleanupRequest(project.root, partId, namedMidiCleanupOptions(profile)))
        }
    }

    private fun approveMidiRepair() {
        val project = state.value.project ?: return fail("MIDI repair", "Open a project before approving MIDI repair.")
        val partId = state.value.selectedPartId ?: return fail("MIDI repair", "Select a part before approving MIDI repair.")
        if (state.value.operation.isMutating) return
        val request = PrepareMidiRequest(project.root, partId)
        val feedbackId = beginFeedback(OperationKind.MIDI_REPAIR, OperationPhase.VALIDATING, "Approving repair and analyzing $partId…")
        mutableState.update { it.copy(operation = WorkspaceOperation.PreparingMidi(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.approveMidiRepairAndAnalyze(request) } }
                .onSuccess { result ->
                    val message = "Repair approved and MIDI analyzed. Add $partId to structure when ready."
                    mutableState.update { current -> current.copy(project = result.project, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback, downstreamArtifactsStale = true) }
                }
                .onFailure { failure -> if (failure !is CancellationException) fail("MIDI repair", failure.message ?: "Unable to approve MIDI repair for $partId.", WorkspaceRetry.PrepareMidi(request), feedbackId) }
        }
    }

    private fun selectMidiFeel(input: MidiAnalysisInput) {
        val project = state.value.project ?: return fail("Lo-fi MIDI Feel", "Open a project before choosing MIDI feel.")
        val partId = state.value.selectedPartId ?: return fail("Lo-fi Feel", "Select a repaired MIDI part first.")
        val part = project.parts.find { it.id == partId } ?: return fail("Lo-fi Feel", "Selected part is no longer available.")
        if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT) return fail("Lo-fi Feel", "Approve a current MIDI repair before choosing a MIDI feel.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(pendingMidiFeel = input, notification = null, retry = null) }
    }

    private fun applyMidiFeelAndReanalyze() {
        val project = state.value.project ?: return fail("Lo-fi MIDI Feel", "Open a project before applying MIDI feel.")
        val partId = state.value.selectedPartId ?: return fail("Lo-fi MIDI Feel", "Select a repaired MIDI part first.")
        val input = state.value.pendingMidiFeel ?: return
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.MIDI_REPAIR, OperationPhase.VALIDATING, "Applying ${if (input == MidiAnalysisInput.LOFI_FEEL) "Lo-fi MIDI Feel · 80 BPM + 58% swing" else "Original MIDI"} and re-analyzing…")
        mutableState.update { it.copy(operation = WorkspaceOperation.SelectingMidiFeel(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.selectMidiFeel(SelectMidiFeelRequest(project.root, partId, input))
                    projectService.analyzePart(AnalyzePartRequest(project.root, partId))
                }
            }
                .onSuccess { snapshot ->
                    cancelPlaybackSession(resetState = true)
                    val message = if (input == MidiAnalysisInput.LOFI_FEEL) "Lo-fi MIDI Feel · 80 BPM + 58% swing applied and analyzed." else "Original MIDI applied and analyzed."
                    mutableState.update { current -> current.copy(project = snapshot, pendingMidiFeel = null, arrangement = null, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback, downstreamArtifactsStale = true) }
                }
                .onFailure { fail("Lo-fi MIDI Feel", it.message ?: "Unable to apply MIDI feel for $partId.", WorkspaceRetry.ApplyMidiFeel(project.root, partId, input), feedbackId) }
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
        val feedbackId = beginFeedback(OperationKind.MIDI_REPAIR, OperationPhase.WAITING_FOR_WORKER, "Repairing MIDI for ${request.partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.RetryingMidiCleanup(request.partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.retryMidiCleanup(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.RetryingMidiCleanup(request.partId, progress)) }
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
                        operationFeedback = feedbackTracker.complete(feedbackId, "MIDI repair complete for ${request.partId}.", OperationSeverity.SUCCESS) ?: it.operationFeedback,
                        downstreamArtifactsStale = true,
                        retry = null
                    )
                }
            }.onFailure { failure ->
                fail("MIDI cleanup", failure.message ?: "Unable to retry MIDI cleanup for ${request.partId}.", WorkspaceRetry.MidiCleanup(request), feedbackId)
            }
        }
    }

    private fun runCleanup(partId: String, mode: InputCleanupMode, confirmed: Boolean) {
        val project = state.value.project ?: return
        workerFailure()?.let { return fail("audio cleanup", it) }
        val service = audioPreparationService ?: return fail("audio cleanup", "Audio preparation is not configured for this desktop session.")
        val feedbackId = beginFeedback(OperationKind.AUDIO_CLEANUP, OperationPhase.WAITING_FOR_WORKER, "Applying selected cleanup for ${partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApplyingAudioCleanup(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.applyCleanup(project.root, partId, mode, confirmed) } }
                .onSuccess { completedPreparation(it, if (mode == InputCleanupMode.INSPECT_ONLY) "Inspect-only choice recorded. Original audio remains selected for transcription." else "Prepared audio is ready for A/B monitoring and transcription selection.", feedbackId) }
                .onFailure { fail("audio cleanup", it.message ?: "Unable to apply cleanup for $partId.", WorkspaceRetry.Cleanup(project.root, partId, mode), feedbackId) }
        }
    }

    private fun transcribeSelectedPart() {
        val project = state.value.project ?: return
        val partId = state.value.selectedPartId ?: return fail("transcribe", "Select an audio part before transcription.")
        state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let { return fail("transcribe", it) }
        val service = audioPreparationService ?: return fail("transcribe", "Audio preparation is not configured for this desktop session.")
        val input = state.value.audioPreparation.transcriptionInput
        val feedbackId = beginFeedback(OperationKind.TRANSCRIPTION, OperationPhase.WAITING_FOR_WORKER, "Running transcription quality gate for ${partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.TranscribingPart(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { service.transcribe(project.root, partId, input) } }
                .onSuccess { completedPreparation(it, "Transcription quality gate passed. Analyze $partId next.", feedbackId) }
                .onFailure { fail("transcribe", it.message ?: "Transcription failed for $partId.", WorkspaceRetry.Transcribe(project.root, partId, input), feedbackId) }
        }
    }

    private fun completedPreparation(result: app.melotrail.application.AudioPreparationOperation, message: String, feedbackId: String) {
        mutableState.update { current ->
            current.copy(
                project = result.project,
                selectedPartId = result.preparation.partId,
                audioPreparation = current.audioPreparation.copy(partId = result.preparation.partId, snapshot = result.preparation),
                operation = WorkspaceOperation.Idle,
                notification = message,
                operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback,
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

    private fun previewMidiPart(partId: String, source: PreviewMidiSource) {
        val project = state.value.project ?: return
        startPlaybackSession(PlaybackRequest.Part(project.root, partId, PreviewAudioSource.ORIGINAL, source), RuntimeCapability.MIDI_PREVIEW)
    }

    private fun pausePreview() = pausePlaybackSession()

    private fun resumePreview() = resumePlaybackSession()

    private fun stopPreview() = stopPlaybackSession()

    private fun seekPreview(seconds: Double) = seekPlaybackSession(seconds)

    private fun startPlaybackSession(request: PlaybackRequest, capability: RuntimeCapability? = null) {
        cancelPlaybackSession(resetState = false)
        val id = ++playbackSessionId
        val kind = request.sourceKind()
        val feedbackId = beginFeedback(OperationKind.PREVIEW_DECODE_RENDER, OperationPhase.VALIDATING, "Checking preview prerequisites…")
        playbackFeedbackSessionId = id
        playbackFeedbackId = feedbackId
        mutableState.update { it.copy(playbackSession = PlaybackSession(id, request, kind, volume = player?.volume?.value ?: it.playbackSession.volume, phase = PlaybackSessionPhase.RESOLVING), operationFeedback = feedbackTracker.current) }
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
                feedbackTracker.progress(feedbackId, OperationPhase.LOCAL, "Decoding or rendering preview audio…")?.let { feedback ->
                    mutableState.update { current -> if (current.playbackSession.id == id) current.copy(operationFeedback = feedback) else current }
                }
                updatePlaybackSession(id) { it.copy(artifact = artifact, phase = PlaybackSessionPhase.PREPARING) }
                when (val prepared = monitor.prepare(artifact.path)) {
                    is PlaybackPrepareResult.Failed -> failPlaybackSession(id, prepared.failure.stage, prepared.failure.message)
                    is PlaybackPrepareResult.Ready -> {
                        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.READY, durationSeconds = prepared.durationSeconds) }
                        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.STARTING) }
                        when (val started = monitor.start()) {
                            PlaybackStartResult.Started -> {
                                updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.PLAYING) }
                                feedbackTracker.complete(feedbackId, "Preview playback started.")?.let { feedback ->
                                    mutableState.update { current -> if (current.playbackSession.id == id) current.copy(operationFeedback = feedback) else current }
                                }
                            }
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
        return when (val resolved = withContext(ioDispatcher) { previews.resolve(PreviewRequest(request.projectRoot, request.partId, request.audioSource, request.midiSource)) }) {
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
        playbackFeedbackSessionId = null
        playbackFeedbackId = null
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

    private fun failPlaybackSession(id: Long, stage: PlaybackFailureStage, message: String) {
        updatePlaybackSession(id) { it.copy(phase = PlaybackSessionPhase.FAILED, failureStage = stage, failureMessage = message, retryAction = PlaybackRetryAction.RETRY_SAME_SELECTION) }
        if (playbackFeedbackSessionId == id) playbackFeedbackId?.let { feedbackId ->
            feedbackTracker.fail(feedbackId, message, OperationRetryAction.RETRY_SAFE_OPERATION)?.let { feedback ->
                mutableState.update { current -> if (current.playbackSession.id == id) current.copy(operationFeedback = feedback) else current }
            }
        }
    }

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
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.LOCAL, "Saving ${draft.partId} role…")
        mutableState.update { it.copy(operation = WorkspaceOperation.UpdatingPartRole(draft.partId), notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.updatePart(UpdatePartRoleRequest(project.root, draft.partId, draft.role)).refreshed()
                }
            }.onSuccess { opened(it, "Updated ${draft.partId} role", feedbackId) }
                .onFailure { fail("update role", it.message ?: "Unable to update role.", sessionId = feedbackId) }
        }
    }

    private fun duplicateStructurePart(index: Int) {
        val draft = state.value.structureDraft
        if (index !in draft.indices) return
        saveStructure(draft.toMutableList().apply { add(index + 1, draft[index]) }, selectedIndex = index + 1)
    }

    private fun addStructurePart(partId: String) {
        val project = state.value.project ?: return fail("add to structure", "Open a project before adding a structure section.")
        val part = project.parts.find { it.id == partId }
            ?: return fail("add to structure", "Part '$partId' is no longer available.")
        if (primaryPartAction(part, state.value.pendingMidiFeel) !is PartPrimaryAction.AddToStructure) {
            return fail("add to structure", "Part '$partId' must finish current MIDI analysis before it can be added to structure.")
        }
        saveStructure(state.value.structureDraft + partId, selectedIndex = state.value.structureDraft.size)
    }

    private fun removeStructurePart(index: Int) {
        val draft = state.value.structureDraft
        if (index !in draft.indices) return
        saveStructure(draft.filterIndexed { current, _ -> current != index }, selectedIndex = (index - 1).coerceAtLeast(0))
    }

    private fun moveStructurePart(from: Int, to: Int) {
        val draft = state.value.structureDraft
        if (from !in draft.indices || to !in draft.indices || from == to) return
        saveStructure(draft.toMutableList().apply { add(to, removeAt(from)) }, selectedIndex = to)
    }

    private fun selectStructureOccurrence(instanceId: String) {
        if (state.value.project?.structure?.any { it.instanceId == instanceId } == true) {
            mutableState.update { it.copy(selectedStructureOccurrenceId = instanceId) }
        }
    }

    private fun structureIndex(instanceId: String): Int? =
        state.value.project?.structure?.indexOfFirst { it.instanceId == instanceId }?.takeIf { it >= 0 }

    private fun saveStructure(partIds: List<String>, selectedIndex: Int? = null) {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating) return
        val existing = state.value.structureDraft
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.LOCAL, "Saving song structure…")
        mutableState.update { it.copy(operation = WorkspaceOperation.SavingStructure, notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.saveStructure(SaveStructureRequest(project.root, partIds)).refreshed() } }
                .onSuccess { snapshot ->
                    val artifactsExist = snapshot.readiness.let {
                        it.songPlanAvailable || it.arrangementAvailable || it.generatedMidiAvailable || it.stemsAvailable ||
                            it.dryMixAvailable || it.loFiMixAvailable || it.masterAvailable
                    }
                    opened(snapshot, if (partIds.isEmpty()) "Cleared structure" else "Saved song structure", feedbackId, stale =
                        state.value.downstreamArtifactsStale || (existing != partIds && artifactsExist))
                    mutableState.update { current ->
                        current.copy(selectedStructureOccurrenceId = selectedIndex?.let(snapshot.structure::getOrNull)?.instanceId
                            ?: current.selectedStructureOccurrenceId?.takeIf { id -> snapshot.structure.any { it.instanceId == id } }
                            ?: snapshot.structure.firstOrNull()?.instanceId)
                    }
                }
                .onFailure { fail("save structure", it.message ?: "Unable to save structure.", WorkspaceRetry.SaveStructure(project.root, partIds, selectedIndex), feedbackId) }
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
        is WorkspaceRetry.PrepareMidi -> {
            mutableState.update { it.copy(selectedPartId = action.request.partId) }
            prepareMidi(action.request.partId)
        }
        is WorkspaceRetry.ApplyMidiFeel -> {
            mutableState.update { it.copy(selectedPartId = action.partId, pendingMidiFeel = action.input) }
            applyMidiFeelAndReanalyze()
        }
        is WorkspaceRetry.Transcribe -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = it.audioPreparation.copy(partId = action.partId, transcriptionInput = action.selectedInput)) }
            transcribeSelectedPart()
        }
        is WorkspaceRetry.SaveStructure -> saveStructure(action.partIds, action.selectedIndex)
        is WorkspaceRetry.GenerateArrangement -> runGenerateArrangement(action.request)
        is WorkspaceRetry.GenerateCohesion -> runGenerateCohesion(action.request)
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

    private fun generateCohesion() {
        val project = state.value.project ?: return fail("generate cohesion", "Open a project before generating cohesion.")
        if (state.value.operation.isMutating) return
        val missing = state.value.structureDraft.toSet().filter { id -> project.parts.find { it.id == id }?.analysis?.status != PartAnalysisStatus.MIDI }
        when {
            state.value.structureDraft.isEmpty() -> fail("generate cohesion", "Save at least one structure occurrence before generating cohesion.")
            missing.isNotEmpty() -> fail("generate cohesion", "Analyze every structure part before generating cohesion: ${missing.joinToString(", ")}.")
            else -> runGenerateCohesion(GenerateCohesionRequest(project.root, state.value.cohesionDraft.planner))
        }
    }

    private fun runGenerateCohesion(request: GenerateCohesionRequest) {
        val feedbackId = beginFeedback(OperationKind.COHESION, OperationPhase.VALIDATING, "Validating occurrence MIDI for cohesion…")
        mutableState.update { it.copy(operation = WorkspaceOperation.GeneratingCohesion(), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) { cohesionService.generate(request) { progress -> scope.launch { updateProgress(feedbackId, WorkspaceOperation.GeneratingCohesion(progress)) } } }
            }.onSuccess { cohesion ->
                val message = if (cohesion.approved) "Safe deterministic cohesion is approved for every occurrence." else "Cohesion draft is ready for review and explicit approval."
                val refreshed = runCatching { projectService.open(request.root) }.getOrNull()
                mutableState.update { it.copy(project = refreshed ?: it.project, cohesion = cohesion, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message, if (cohesion.approved) OperationSeverity.SUCCESS else OperationSeverity.WARNING) ?: it.operationFeedback) }
            }.onFailure { fail("generate cohesion", it.message ?: "Unable to generate cohesion.", WorkspaceRetry.GenerateCohesion(request), feedbackId) }
        }
    }

    private fun approveCohesion() {
        val project = state.value.project ?: return
        val cohesion = state.value.cohesion ?: return fail("approve cohesion", "Generate a cohesion draft before approving it.")
        if (cohesion.approved || cohesion.stale || state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.APPROVAL, OperationPhase.VALIDATING, "Approving validated cohesion…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApprovingCohesion, notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { cohesionService.approve(project.root) } }
                .onSuccess { approved ->
                    val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                    mutableState.update { it.copy(project = refreshed ?: it.project, cohesion = approved, operation = WorkspaceOperation.Idle, notification = "Cohesion approved for every occurrence.", operationFeedback = feedbackTracker.complete(feedbackId, "Cohesion approved for every occurrence.") ?: it.operationFeedback) }
                }
                .onFailure { fail("approve cohesion", it.message ?: "Unable to approve cohesion.", sessionId = feedbackId) }
        }
    }

    private fun rejectCohesion() {
        val project = state.value.project ?: return
        val cohesion = state.value.cohesion ?: return
        if (cohesion.approved || state.value.operation.isMutating) return
        scope.launch {
            runCatching { withContext(ioDispatcher) { cohesionService.reject(project.root) } }
                .onSuccess { rejected ->
                    val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                    mutableState.update { it.copy(project = refreshed ?: it.project, cohesion = rejected, notification = "Cohesion draft rejected. The last approved cohesion, if any, is preserved.") }
                }
                .onFailure { fail("reject cohesion", it.message ?: "Unable to reject cohesion.") }
        }
    }

    private fun generateArrangement() {
        val project = state.value.project ?: return fail("generate arrangement", "Open a project before arranging.")
        if (state.value.operation.isMutating) return
        val missing = state.value.structureDraft.toSet().filter { id -> project.parts.find { it.id == id }?.analysis?.status != PartAnalysisStatus.MIDI }
        when {
            state.value.structureDraft.isEmpty() -> fail("generate arrangement", "Add at least one section to the song structure before arranging.")
            missing.isNotEmpty() -> fail("generate arrangement", "Analyze every structure part before arranging: ${missing.joinToString(", ")}.")
            project.version >= 3 && !project.readiness.cohesionReady -> fail("generate arrangement", "Generate and approve current cohesion for every structure occurrence before arranging.")
            state.value.arrangementDraft.style.trim().length > MAX_STYLE_LENGTH -> fail("generate arrangement", "Style must be at most $MAX_STYLE_LENGTH characters.")
            else -> runGenerateArrangement(
                GenerateArrangementRequest(project.root, state.value.arrangementDraft.planner, state.value.arrangementDraft.style.trim().ifBlank { null }, arrangementInstruments.filter { it in state.value.arrangementDraft.instruments })
            )
        }
    }

    private fun runGenerateArrangement(request: GenerateArrangementRequest) {
        val feedbackId = beginFeedback(OperationKind.ARRANGEMENT, OperationPhase.VALIDATING, "Validating analyses before arranging…")
        mutableState.update { it.copy(operation = WorkspaceOperation.GeneratingArrangement(), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    arrangementService.generate(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.GeneratingArrangement(progress)) }
                    }
                }
            }.onSuccess { arrangement ->
                val message = if (arrangement.approvalRequired) "Arrangement draft is ready for review and explicit approval." else "Approved deterministic arrangement generated."
                mutableState.update { it.copy(arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, arrangementDraftDirty = false, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message, if (arrangement.approvalRequired) OperationSeverity.WARNING else OperationSeverity.SUCCESS) ?: it.operationFeedback, retry = null) }
            }.onFailure { fail("generate arrangement", it.message ?: "Unable to generate arrangement.", WorkspaceRetry.GenerateArrangement(request), feedbackId) }
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
        val feedbackId = beginFeedback(OperationKind.APPROVAL, OperationPhase.VALIDATING, "Approving validated arrangement…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApprovingArrangement, notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { arrangementService.approve(project.root) } }
                .onSuccess { arrangement -> mutableState.update { it.copy(arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, operation = WorkspaceOperation.Idle, notification = "Arrangement approved.", operationFeedback = feedbackTracker.complete(feedbackId, "Arrangement approved.") ?: it.operationFeedback) } }
                .onFailure { fail("approve arrangement", it.message ?: "Unable to approve arrangement.", sessionId = feedbackId) }
        }
    }

    private fun updateProgress(feedbackId: String, operation: WorkspaceOperation) {
        val progress = when (operation) {
            is WorkspaceOperation.ImportingPart -> operation.progress
            is WorkspaceOperation.AnalyzingPart -> operation.progress
            is WorkspaceOperation.RetryingMidiCleanup -> operation.progress
            is WorkspaceOperation.GeneratingCohesion -> operation.progress
            is WorkspaceOperation.GeneratingArrangement -> operation.progress
            is WorkspaceOperation.ApplyingMix -> operation.progress
            is WorkspaceOperation.BuildingSong -> operation.progress
            else -> null
        }
        val feedback = progress?.let {
            feedbackTracker.progress(
                feedbackId, OperationProgressFeedbackPhase(it), it.message, it.stageIndex, it.stageCount,
                it.artifact?.fileName?.toString(), OperationProgressFeedbackKind(it)
            )
        } ?: return
        operationLogger.operationEvent(feedback.sessionId, feedback.kind, feedback.phase, progress.artifact)
        mutableState.update { current ->
            if (current.operation.isMutating && current.operationFeedback.sessionId == feedbackId) current.copy(operation = operation, operationFeedback = feedback) else current
        }
    }

    private fun updateMixSetting(instrument: String, setting: LogicalMixSetting) {
        val mix = state.value.mix ?: return
        if (instrument !in mix.availableStems) return fail("apply mix", "Mix setting '$instrument' is unavailable because its rendered stem is missing.")
        runCatching { setting.requireValid(instrument) }.onFailure { return fail("apply mix", it.message ?: "Invalid mix setting.") }
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
        val feedbackId = beginFeedback(OperationKind.MIXING, OperationPhase.VALIDATING, "Validating rendered stems…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApplyingMix(), notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { mixService.apply(app.melotrail.application.ApplyMixRequest(projectRoot, settings)) { progress -> scope.launch { updateProgress(feedbackId, WorkspaceOperation.ApplyingMix(progress)) } } } }
                .onSuccess { snapshot ->
                    val refreshed = withContext(ioDispatcher) { runCatching { projectService.open(projectRoot) } }
                    mutableState.update { current ->
                        val refreshWarning = refreshed.exceptionOrNull()?.message
                        current.copy(
                            project = refreshed.getOrNull() ?: current.project,
                            mix = snapshot,
                            operation = WorkspaceOperation.Idle,
                            notification = refreshWarning?.let { "Updated lossless dry mix. Reopen the project to refresh artifact readiness: $it" }
                                ?: "Updated lossless dry mix from existing stems.",
                            operationFeedback = feedbackTracker.complete(
                                feedbackId,
                                if (refreshWarning == null) "Updated lossless dry mix from existing stems." else "Mix updated; canonical readiness refresh needs recovery.",
                                if (refreshWarning == null) OperationSeverity.SUCCESS else OperationSeverity.WARNING
                            ) ?: current.operationFeedback
                        )
                    }
                }
                .onFailure { fail("apply mix", it.message ?: "Unable to apply mix settings.", sessionId = feedbackId) }
        }
    }

    private fun buildSong() {
        val project = state.value.project ?: return fail("build song", "Open a project before building.")
        val service = buildService ?: return fail("build song", "Build service is not configured for this desktop session.")
        val arrangement = state.value.arrangement
        if (arrangement == null || arrangement.stale || arrangement.approvalRequired || !arrangement.approved) return fail("build song", "Build Song requires a current approved arrangement.")
        state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.BUILD_SONG)?.let { return fail("build song", it) }
        val options = state.value.buildOptions
        val feedbackId = beginFeedback(OperationKind.MASTERING, OperationPhase.VALIDATING, "Validating release pipeline…", cancellableAtBoundary = true)
        mutableState.update { it.copy(operation = WorkspaceOperation.BuildingSong(), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        buildJob = scope.launch {
            runCatching { withContext(ioDispatcher) { service.build(BuildSongRequest(project.root, options.loFi, options.mp3)) { progress -> scope.launch { updateProgress(feedbackId, WorkspaceOperation.BuildingSong(progress)) } } } }
                .onSuccess { result ->
                    val (refreshed, loadedMix) = withContext(ioDispatcher) {
                        projectService.open(project.root) to runCatching { mixService.load(project.root) }
                    }
                    mutableState.update { current ->
                        current.copy(
                            project = refreshed,
                            mix = loadedMix.getOrNull(),
                            operation = WorkspaceOperation.Idle,
                            notification = loadedMix.exceptionOrNull()?.message?.let { warning -> "Build complete: ${result.master}. Mix controls could not be loaded: $warning" }
                                ?: "Build complete: ${result.master}",
                            operationFeedback = feedbackTracker.complete(feedbackId, "Build complete: ${result.master.fileName}", if (loadedMix.isFailure) OperationSeverity.WARNING else OperationSeverity.SUCCESS, result.master.fileName.toString()) ?: current.operationFeedback
                        )
                    }
                }.onFailure {
                    if (it is CancellationException) mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = "Build cancellation requested; the current atomic stage was allowed to finish safely.", operationFeedback = feedbackTracker.complete(feedbackId, "Build cancellation completed at a safe boundary.", OperationSeverity.INFORMATION) ?: current.operationFeedback) }
                    else fail("build song", it.message ?: "Build Song failed.", sessionId = feedbackId)
                }
        }
    }

    private fun exportCommercialProvenance() {
        val project = state.value.project ?: return fail("commercial provenance", "Open a project before creating commercial evidence.")
        if (!project.readiness.releaseAvailable || state.value.operation.isMutating) return fail("commercial provenance", "Build a current master and release metadata before creating commercial evidence.")
        mutableState.update { it.copy(operation = WorkspaceOperation.ExportingCommercialProvenance, notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { commercialProvenanceService.export(project.root) } }
                .onSuccess { result ->
                    val message = if (result.readiness.ready) "Commercial provenance exported. Review the report and YouTube checklist before release."
                    else "Commercial evidence exported with blocking warnings. It is not labeled Commercial-ready."
                    mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = message) }
                }
                .onFailure { failure -> fail("commercial provenance", failure.message ?: "Unable to create commercial evidence.") }
        }
    }

    private fun refreshExport() {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(export = it.export.copy(inspecting = true)) }
        scope.launch {
            val inspection = withContext(ioDispatcher) { releaseExportService.inspect(project.root) }
            mutableState.update { current ->
                if (current.project?.root != project.root) current
                else {
                    val defaultName = project.name.ifBlank { "song" }.replace(Regex("[^A-Za-z0-9 _-]"), "_")
                    val extension = current.export.draft.format.extension
                    val filename = current.export.draft.filename.takeUnless { it == "song.wav" } ?: "$defaultName.$extension"
                    current.copy(export = current.export.copy(inspection = inspection, inspecting = false,
                        draft = current.export.draft.copy(filename = filename, destination = current.export.draft.destination ?: project.root.resolve("output"))))
                }
            }
        }
    }

    private fun chooseExportDestination() = scope.launch {
        runCatching { fileDialogs.chooseExportDirectory() }
            .onSuccess { selected -> selected?.let { directory ->
                mutableState.update { current -> current.copy(export = current.export.copy(draft = current.export.draft.copy(destination = directory))) }
            } }
            .onFailure { fail("export destination", it.message ?: "The export folder chooser could not be opened.") }
    }

    private fun exportSong() {
        val project = state.value.project ?: return fail("export song", "Open a project before exporting.")
        val draft = state.value.export.draft
        val inspection = state.value.export.inspection
        if (state.value.operation.isMutating) return
        if (inspection?.summary == null || draft.format !in inspection.supportedFormats) return fail("export song", inspection?.blockedReason ?: "Build a current master and release metadata first.")
        val destination = draft.destination ?: return fail("export song", "Choose the project output folder before exporting.")
        val feedbackId = beginFeedback(OperationKind.EXPORT, OperationPhase.VALIDATING, "Validating release export…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ExportingRelease, notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { releaseExportService.export(ReleaseExportRequest(project.root, draft.format, draft.filename, destination)) } }
                .onSuccess { result ->
                    mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = "Exported ${result.output.fileName}.",
                        operationFeedback = feedbackTracker.complete(feedbackId, "Exported ${result.output.fileName}.", artifactLabel = result.output.fileName.toString()) ?: current.operationFeedback) }
                    refreshExport()
                }
                .onFailure { failure -> fail("export song", failure.message ?: "Export Song failed.", sessionId = feedbackId) }
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

    private fun opened(
        project: ProjectSnapshot,
        message: String,
        feedbackId: String? = null,
        stale: Boolean = false,
        resetWorkspace: Boolean = false
    ) {
        if (resetWorkspace) cancelPlaybackSession(resetState = true)
        preferences.saveLastOpenedProject(project.root)
        operationLogger.event("project", "opened", project.root)
        val openedMessage = if (project.version == 1) {
            "$message · Legacy v1 project opened. Re-import parts as MIDI-first sources to unlock the current arrangement workflow."
        } else message
        mutableState.update { current ->
            current.copy(
                project = project,
                cohesion = if (resetWorkspace) null else current.cohesion,
                arrangement = if (resetWorkspace) null else current.arrangement,
                mix = if (resetWorkspace) null else current.mix,
                selectedPartId = if (resetWorkspace) null else current.selectedPartId,
                selectedArtifact = if (resetWorkspace) null else current.selectedArtifact,
                midiQualityReview = if (resetWorkspace) MidiQualityReviewDraft() else current.midiQualityReview,
                audioPreparation = if (resetWorkspace) AudioPreparationUiState() else current.audioPreparation,
                operation = WorkspaceOperation.Idle,
                notification = openedMessage,
                operationFeedback = feedbackId?.let { feedbackTracker.complete(it, openedMessage) } ?: current.operationFeedback,
                dialog = null,
                structureDraft = project.structure.map { section -> section.partId },
                selectedStructureOccurrenceId = current.selectedStructureOccurrenceId
                    ?.takeIf { selected -> project.structure.any { it.instanceId == selected } }
                    ?: project.structure.firstOrNull()?.instanceId,
                downstreamArtifactsStale = stale,
                arrangementDraftDirty = if (resetWorkspace) false else current.arrangementDraftDirty,
                retry = null,
                workspaceSection = if (resetWorkspace) WorkspaceSection.OVERVIEW else current.workspaceSection
            )
        }
        hydrateProject(project, openedMessage, resetWorkspace)
    }

    private fun hydrateProject(project: ProjectSnapshot, openedMessage: String, resetWorkspace: Boolean) = scope.launch {
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.LOCAL, "Loading project artifacts…")
        mutableState.update { current ->
            if (current.project?.root == project.root) current.copy(operationFeedback = feedbackTracker.current) else current
        }
        val hydration = withContext(ioDispatcher) {
            val mix = runCatching { mixService.load(project.root) }
            val arrangement = runCatching { arrangementService.load(project.root) }
            val cohesion = runCatching { cohesionService.load(project.root) }
            Triple(mix, arrangement, cohesion)
        }
        val warnings = buildList {
            hydration.first.exceptionOrNull()?.message?.let { add("mix settings could not be loaded: $it") }
            if (project.readiness.arrangementAvailable || project.readiness.songPlanAvailable) {
                hydration.second.exceptionOrNull()?.message?.let { add("arrangement artifacts could not be loaded: $it") }
            }
            if (project.readiness.cohesionReady || project.readiness.cohesionApprovalRequired) {
                hydration.third.exceptionOrNull()?.message?.let { add("cohesion artifacts could not be loaded: $it") }
            }
        }
        mutableState.update { current ->
            if (current.project?.root != project.root) current
            else {
                val arrangement = hydration.second.getOrNull()
                current.copy(
                    mix = hydration.first.getOrNull(),
                    arrangement = arrangement,
                    cohesion = hydration.third.getOrNull(),
                    selectedArrangementSection = if (resetWorkspace) arrangement?.sections?.firstOrNull()?.index else current.selectedArrangementSection,
                    notification = if (warnings.isEmpty()) current.notification ?: openedMessage
                    else "$openedMessage Some optional artifacts need attention: ${warnings.joinToString("; ")}",
                    operationFeedback = feedbackTracker.complete(
                        feedbackId,
                        if (warnings.isEmpty()) openedMessage else "Project opened with optional artifact warnings.",
                        if (warnings.isEmpty()) OperationSeverity.SUCCESS else OperationSeverity.WARNING
                    ) ?: current.operationFeedback
                )
            }
        }
    }

    private fun beginFeedback(
        kind: OperationKind,
        phase: OperationPhase,
        message: String,
        artifactLabel: String? = null,
        cancellableAtBoundary: Boolean = false
    ): String {
        val feedback = feedbackTracker.begin(kind, phase, message, artifactLabel, cancellableAtBoundary)
        operationLogger.operationEvent(feedback.sessionId, feedback.kind, feedback.phase)
        return feedback.sessionId
    }

    private fun fail(action: String, message: String, retry: WorkspaceRetry? = null, sessionId: String? = null) {
        val feedback = if (sessionId != null) {
            feedbackTracker.fail(sessionId, message, retry?.let { OperationRetryAction.RETRY_SAFE_OPERATION })
        } else {
            val id = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.VALIDATING, "Validating $action…")
            feedbackTracker.fail(id, message, retry?.let { OperationRetryAction.RETRY_SAFE_OPERATION })
        }
        val visible = feedback ?: state.value.operationFeedback
        operationLogger.operationEvent(visible.sessionId, visible.kind, visible.phase, failure = IllegalStateException(message))
        mutableState.update { current ->
            if (sessionId != null && current.operationFeedback.sessionId != sessionId) current
            else current.copy(operation = WorkspaceOperation.Failed(action, message), notification = message, retry = retry, operationFeedback = visible)
        }
    }

    private fun cancelOperation() {
        val feedback = feedbackTracker.cancelAtBoundary(state.value.operationFeedback.sessionId, "Cancellation requested; finishing the current safe boundary…") ?: return
        mutableState.update { it.copy(operationFeedback = feedback) }
        operationLogger.operationEvent(feedback.sessionId, feedback.kind, feedback.phase)
        importPreparationJob?.cancel()
        buildJob?.cancel()
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

    override fun close() { mixCommit?.cancel(); importPreparationJob?.cancel(); buildJob?.cancel(); cancelPlaybackSession(resetState = true); player?.close(); scope.cancel() }

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
