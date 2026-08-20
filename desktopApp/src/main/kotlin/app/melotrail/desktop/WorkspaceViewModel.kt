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
import app.melotrail.application.CreateMidiAiFixRequest
import app.melotrail.application.DefaultMidiAiFixApplicationService
import app.melotrail.application.MidiAiFixApplicationService
import app.melotrail.application.MidiAiFixSnapshot
import app.melotrail.application.EnhancementApplicationService
import app.melotrail.application.DefaultEnhancementApplicationService
import app.melotrail.application.EnhancementSnapshot
import app.melotrail.application.CreateEnhancementRequest
import app.melotrail.application.ApproveEnhancementRequest
import app.melotrail.application.SelectApprovedEnhancementRequest
import app.melotrail.application.CreateTechnicalCorrectionRequest
import app.melotrail.application.DefaultTechnicalCorrectionApplicationService
import app.melotrail.application.TechnicalCorrectionApplicationService
import app.melotrail.application.DefaultArrangementApplicationService
import app.melotrail.application.GenerateArrangementRequest
import app.melotrail.application.ImportPartRequest
import app.melotrail.application.ImportSongPart
import app.melotrail.application.MixApplicationService
import app.melotrail.application.MixSnapshot
import app.melotrail.application.PersistedMixSettings
import app.melotrail.application.LogicalMixSetting
import app.melotrail.application.DefaultMixApplicationService
import app.melotrail.application.BuildApplicationService
import app.melotrail.application.BuildSongRequest
import app.melotrail.application.HumanizationApplicationService
import app.melotrail.application.DefaultHumanizationApplicationService
import app.melotrail.application.HumanizationSnapshot
import app.melotrail.application.GenerateHumanizationRequest
import app.melotrail.application.PartPreviewApplicationService
import app.melotrail.application.PreviewRequest
import app.melotrail.application.PreviewResult
import app.melotrail.application.OperationProgress
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartAnalysisStatus
import app.melotrail.application.ProjectApplicationService
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.GetHarmony
import app.melotrail.application.CreateHarmonyEvent
import app.melotrail.application.UpdateHarmonyEvent
import app.melotrail.application.DeleteHarmonyEvent
import app.melotrail.application.ReorderHarmonyEvent
import app.melotrail.application.SetHarmonyProgression
import app.melotrail.application.GetCompositionSettings
import app.melotrail.application.PreviewSettingsChange
import app.melotrail.application.UpdateCompositionSettings
import app.melotrail.application.CleanMidiRequest
import app.melotrail.application.ConfirmSourceKey
import app.melotrail.application.TransposePartRequest
import app.melotrail.application.SelectMidiFeelRequest
import app.melotrail.application.AudioPreparationApplicationService
import app.melotrail.application.AudioPreparationAvailability
import app.melotrail.application.AudioPreparationSnapshot
import app.melotrail.application.PreviewAudioSource
import app.melotrail.application.PreviewMidiSource
import app.melotrail.application.SaveStructureRequest
import app.melotrail.application.InsertStructureOccurrenceRequest
import app.melotrail.application.DuplicateStructureOccurrenceRequest
import app.melotrail.application.RemoveStructureOccurrenceRequest
import app.melotrail.application.MoveStructureOccurrenceRequest
import app.melotrail.application.UpdateSongPartSectionRequest
import app.melotrail.application.RemoveSongPartRequest
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
import app.melotrail.arrangement.ArrangementRole
import app.melotrail.arrangement.ArrangementRoleSelection
import app.melotrail.arrangement.SoundTrait
import app.melotrail.application.MidiQualityStatus
import app.melotrail.preparation.InputCleanupMode
import app.melotrail.preparation.TranscriptionInputArtifact
import app.melotrail.commercial.SourceRightsAttestation
import app.melotrail.commercial.SourceRightsClaim
import app.melotrail.commercial.CommercialProvenanceService
import app.melotrail.commercial.ReleaseCreditsService
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonyTemplateId
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.PitchClass
import app.melotrail.music.MusicalKey
import app.melotrail.music.ScaleModeId
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
    /** Immutable AI-fix review evidence; composables never read draft files. */
    val midiAiFix: MidiAiFixSnapshot? = null,
    /** Task 019 review evidence; no composable reads enhancement files. */
    val enhancementReview: EnhancementSnapshot? = null,
    val arrangement: ArrangementSnapshot? = null,
    val mix: MixSnapshot? = null,
    val humanization: HumanizationSnapshot? = null,
    val buildOptions: BuildOptionsDraft = BuildOptionsDraft(),
    val export: ExportUiState = ExportUiState(),
    val commercialEvidence: CommercialEvidenceUiState? = null,
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
    /** Typed setup query and its UI-only draft; persistence remains in the application service. */
    val projectSetup: ProjectSetupUiState = ProjectSetupUiState.Empty,
    /** Harmony command/query data is owned by the typed application service. */
    val harmony: HarmonyEditorUiState = HarmonyEditorUiState(),
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
    SETUP("Setup"),
    /** Read-only project snapshot; the legacy identifier preserves route compatibility. */
    OVERVIEW("Info"),
    IMPORT("Melody Parts"),
    HARMONY("Harmony"),
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
/** Presentation-safe outcome from the typed release-lineage application service. */
data class CommercialEvidenceUiState(
    val commercialReady: Boolean,
    val unresolvedActions: List<String>,
    val releaseId: String,
    val requiredAttribution: List<String>,
    val creditsReference: String? = null,
    /** Project-relative files only; no local absolute path is exposed. */
    val reportReference: String,
    val manifestReference: String
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
    val audioSource: PreviewAudioSource? = null,
    /** The validated representation identity, not the disposable preview-cache filename. */
    val source: app.melotrail.application.PreviewArtifactIdentity? = null
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
    /** Compatibility-only: the Arrange UI no longer sends free-form style to planners. */
    val style: String = "",
    /** Compatibility-only: resolved logical instruments remain readable until Task 022B. */
    val instruments: Set<String> = setOf("piano"),
    val roles: Set<ArrangementRole> = setOf(ArrangementRole.MELODY),
    val attackTraits: Set<SoundTrait> = setOf(SoundTrait.SOFT),
    val toneTraits: Set<SoundTrait> = setOf(SoundTrait.WARM),
    val articulationTraits: Set<SoundTrait> = emptySet(),
    val pinnedInstrumentIds: Map<ArrangementRole, String> = emptyMap()
)

/** Supported Arrange views. They expose existing bounded controls and evidence only. */
enum class ArrangeTab(val label: String) {
    ARRANGEMENT("Arrangement"),
    INSTRUMENTS("Instruments"),
    TRANSITIONS("Transitions"),
    PLANNER("Planner")
}

data class CohesionDraft(val planner: CohesionPlannerKind = CohesionPlannerKind.QWEN)

sealed interface WorkspaceOperation {
    data object Idle : WorkspaceOperation
    data class OpeningProject(val root: Path) : WorkspaceOperation
    data object SavingProjectSetup : WorkspaceOperation
    data object SavingHarmony : WorkspaceOperation
    data class CreatingProject(val root: Path) : WorkspaceOperation
    data class ImportingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class AnalyzingPart(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class InspectingPart(val id: String) : WorkspaceOperation
    data class ApplyingAudioCleanup(val id: String) : WorkspaceOperation
    data class CleaningMidi(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class CorrectingMidi(val id: String) : WorkspaceOperation
    data class RemovingSongPart(val id: String) : WorkspaceOperation
    data class SelectingMidiFeel(val id: String) : WorkspaceOperation
    data class SelectingEnhancement(val id: String) : WorkspaceOperation
    data class CreatingMidiAiFix(val id: String, val progress: OperationProgress? = null) : WorkspaceOperation
    data class ApprovingMidiAiFix(val id: String) : WorkspaceOperation
    data class TranscribingPart(val id: String) : WorkspaceOperation
    data class UpdatingPartRole(val id: String) : WorkspaceOperation
    data object SavingStructure : WorkspaceOperation
    data class GeneratingCohesion(val progress: OperationProgress? = null) : WorkspaceOperation
    data class ReviewingCohesion(val outgoingInstanceId: String, val incomingInstanceId: String) : WorkspaceOperation
    data object ApprovingCohesion : WorkspaceOperation
    data class GeneratingArrangement(val progress: OperationProgress? = null) : WorkspaceOperation
    data class ApplyingMix(val progress: OperationProgress? = null) : WorkspaceOperation
    data object Humanizing : WorkspaceOperation
    data class BuildingSong(val progress: OperationProgress? = null) : WorkspaceOperation
    data object ExportingCommercialProvenance : WorkspaceOperation
    data object ExportingRelease : WorkspaceOperation
    data object ApprovingArrangement : WorkspaceOperation
    data class OpenFailed(val message: String) : WorkspaceOperation
    data class Failed(val action: String, val message: String) : WorkspaceOperation
}

val WorkspaceOperation.isMutating: Boolean
    get() = this is WorkspaceOperation.OpeningProject || this is WorkspaceOperation.CreatingProject ||
        this is WorkspaceOperation.SavingProjectSetup || this is WorkspaceOperation.SavingHarmony ||
        this is WorkspaceOperation.ImportingPart || this is WorkspaceOperation.AnalyzingPart ||
        this is WorkspaceOperation.InspectingPart || this is WorkspaceOperation.ApplyingAudioCleanup || this is WorkspaceOperation.TranscribingPart ||
        this is WorkspaceOperation.CleaningMidi ||
        this is WorkspaceOperation.CorrectingMidi ||
        this is WorkspaceOperation.RemovingSongPart ||
        this is WorkspaceOperation.SelectingMidiFeel ||
        this is WorkspaceOperation.SelectingEnhancement ||
        this is WorkspaceOperation.CreatingMidiAiFix || this is WorkspaceOperation.ApprovingMidiAiFix ||
        this is WorkspaceOperation.UpdatingPartRole || this is WorkspaceOperation.SavingStructure ||
        this is WorkspaceOperation.GeneratingCohesion || this is WorkspaceOperation.ReviewingCohesion || this is WorkspaceOperation.ApprovingCohesion ||
        this is WorkspaceOperation.GeneratingArrangement || this is WorkspaceOperation.ApprovingArrangement
        || this is WorkspaceOperation.ApplyingMix || this is WorkspaceOperation.Humanizing || this is WorkspaceOperation.BuildingSong || this is WorkspaceOperation.ExportingCommercialProvenance || this is WorkspaceOperation.ExportingRelease

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
        val detailsExpanded: Boolean = false,
        /** Rights are recorded explicitly before the immutable source is published. */
        val provenanceConfirmed: Boolean = false,
        /** A confirmation/readiness error is kept separate from source-format inspection evidence. */
        val confirmationMessage: String? = null,
        val name: String = "",
        val sectionType: app.melotrail.arrangement.SectionTypeId = app.melotrail.arrangement.SectionTypeId.VERSE
    ) : WorkspaceDialog

    /** Legacy name retained for intent compatibility; this edits the catalog section, not a free-form role. */
    data class EditRole(val partId: String, val role: String) : WorkspaceDialog
    data class ConfirmSectionChange(val partId: String, val sectionType: app.melotrail.arrangement.SectionTypeId) : WorkspaceDialog
    data class ConfirmPartStructureChange(val partId: String, val instanceId: String? = null) : WorkspaceDialog
    data class ConfirmRemoveSongPart(val partId: String) : WorkspaceDialog
    /** The selected canonical part and its return target travel together; no row-local selection is inferred. */
    data class PartDetails(val partId: String, val focusReturn: PartDetailsFocusReturn) : WorkspaceDialog
    data class ConfirmSafeCleanup(val partId: String) : WorkspaceDialog
    data class ConfirmTightenTiming(val partId: String) : WorkspaceDialog
    data class ConfirmSourceKey(val partId: String, val selected: MusicalKey) : WorkspaceDialog
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

/** The short import flow is presentation state only; the typed import service remains authoritative. */
enum class ImportFlowStep(val number: Int, val label: String) {
    SELECT_SOURCE(1, "Select source"),
    INSPECT_AND_VALIDATE(2, "Inspect and validate"),
    CONFIRM_PROVENANCE(3, "Confirm source rights"),
    NEXT_ACTION(4, "Next action")
}

internal fun importFlowStep(draft: WorkspaceDialog.ImportPart): ImportFlowStep = when {
    draft.source == null -> ImportFlowStep.SELECT_SOURCE
    draft.detectedType == ImportSourceKind.UNSUPPORTED || draft.validationMessage != null -> ImportFlowStep.INSPECT_AND_VALIDATE
    !draft.provenanceConfirmed -> ImportFlowStep.CONFIRM_PROVENANCE
    else -> ImportFlowStep.NEXT_ACTION
}

internal fun importRoute(kind: ImportSourceKind?): String = when (kind) {
    ImportSourceKind.MIDI -> "Direct MIDI import"
    ImportSourceKind.WAV, ImportSourceKind.MP3 -> "Solo-piano transcription"
    else -> "Choose a supported source"
}

/** Exactly one next action is derived from canonical artifact state, never from a row-local flag. */
sealed interface PartPrimaryAction {
    data class CleanMidi(val partId: String) : PartPrimaryAction
    data class ReviewCleanMidi(val partId: String) : PartPrimaryAction
    data class InspectOrTranscribeAudio(val partId: String, val inspected: Boolean) : PartPrimaryAction
    data class ApplyLoFiChange(val partId: String) : PartPrimaryAction
    data class Analyze(val partId: String) : PartPrimaryAction
    /** The only canonical state permitted to enter the saved song structure. */
    data class AddToStructure(val partId: String) : PartPrimaryAction
    data class FixIssue(val partId: String) : PartPrimaryAction
}

internal fun primaryPartAction(part: app.melotrail.application.PartSummary, pendingMidiFeel: MidiAnalysisInput? = null): PartPrimaryAction = when {
    pendingMidiFeel != null && pendingMidiFeel != part.preparation.midiFeel.selected -> PartPrimaryAction.ApplyLoFiChange(part.id)
    part.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED -> PartPrimaryAction.ReviewCleanMidi(part.id)
    part.preparation.rawMidi && part.preparation.midiQuality.status == MidiQualityStatus.STALE_OR_INVALID -> PartPrimaryAction.CleanMidi(part.id)
    part.preparation.rawMidi && part.preparation.midiQuality.status == MidiQualityStatus.LEGACY_UNKNOWN -> PartPrimaryAction.FixIssue(part.id)
    part.preparation.warnings.isNotEmpty() -> PartPrimaryAction.FixIssue(part.id)
    part.sourceType == PartSourceType.AUDIO && !part.preparation.rawMidi && part.analysis?.status != PartAnalysisStatus.MIDI -> PartPrimaryAction.InspectOrTranscribeAudio(part.id, part.preparation.inspected)
    part.preparation.midiQuality.status == MidiQualityStatus.CURRENT && (!part.preparation.analyzed || part.analysis?.status != PartAnalysisStatus.MIDI) -> PartPrimaryAction.Analyze(part.id)
    part.analysis?.status == PartAnalysisStatus.MIDI -> PartPrimaryAction.AddToStructure(part.id)
    else -> PartPrimaryAction.FixIssue(part.id)
}

internal fun PartPrimaryAction.label(): String = when (this) {
    is PartPrimaryAction.CleanMidi -> "Clean MIDI"
    is PartPrimaryAction.ReviewCleanMidi -> "Review Clean MIDI"
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
    data class AutomaticImport(val command: app.melotrail.application.ImportSongPart, val draft: WorkspaceDialog.ImportPart) : WorkspaceRetry
    data class Analyze(val root: Path, val partId: String) : WorkspaceRetry
    data class Transpose(val root: Path, val partId: String) : WorkspaceRetry
    data class Inspect(val root: Path, val partId: String) : WorkspaceRetry
    data class Cleanup(val root: Path, val partId: String, val mode: InputCleanupMode) : WorkspaceRetry
    data class CleanMidi(val request: CleanMidiRequest) : WorkspaceRetry
    data class TechnicalCorrection(val request: CreateTechnicalCorrectionRequest) : WorkspaceRetry
    data class ApplyMidiFeel(val root: Path, val partId: String, val input: MidiAnalysisInput) : WorkspaceRetry
    data class Enhancement(val root: Path, val partId: String, val intensity: app.melotrail.arrangement.EnhancementIntensity) : WorkspaceRetry
    data class CreateMidiAiFix(val request: CreateMidiAiFixRequest) : WorkspaceRetry
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
    data class UpdateProjectSetup(val draft: ProjectSetupDraft) : WorkspaceIntent
    data object SaveProjectSetup : WorkspaceIntent
    data object ConfirmProjectSetupSave : WorkspaceIntent
    data class SelectHarmonySection(val section: SectionTypeId) : WorkspaceIntent
    data class SelectHarmonyTemplate(val templateId: HarmonyTemplateId) : WorkspaceIntent
    data class SelectHarmonyEvent(val eventId: ChordEventId?) : WorkspaceIntent
    data class SetHarmonyRoot(val root: PitchClass) : WorkspaceIntent
    data class SetHarmonyQuality(val quality: ChordQuality) : WorkspaceIntent
    data object AddHarmonyEvent : WorkspaceIntent
    data object SaveHarmonyEvent : WorkspaceIntent
    data object DeleteHarmonyEvent : WorkspaceIntent
    data class MoveHarmonyEvent(val earlier: Boolean) : WorkspaceIntent
    data object ConfirmHarmonyMutation : WorkspaceIntent
    data object CancelHarmonyMutation : WorkspaceIntent
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
    data object ConfirmImportProvenance : WorkspaceIntent
    data object ImportPart : WorkspaceIntent
    data class CleanMidi(val partId: String) : WorkspaceIntent
    data class ShowPartDetails(
        val partId: String,
        val focusReturn: PartDetailsFocusReturn = PartDetailsFocusReturn.ImportedRow(partId)
    ) : WorkspaceIntent
    data class AnalyzePart(val partId: String) : WorkspaceIntent
    data class ShowSourceKeyConfirmation(val partId: String) : WorkspaceIntent
    data class SelectConfirmedSourceKey(val key: MusicalKey) : WorkspaceIntent
    data object ConfirmSourceKey : WorkspaceIntent
    data class TransposePart(val partId: String) : WorkspaceIntent
    data class SelectPart(val partId: String) : WorkspaceIntent
    data object InspectSelectedPart : WorkspaceIntent
    data class SelectCleanupMode(val mode: InputCleanupMode) : WorkspaceIntent
    data object ApplySelectedCleanup : WorkspaceIntent
    data object ConfirmSafeCleanup : WorkspaceIntent
    data class SelectMidiCleanupProfile(val profile: MidiCleanupProfile) : WorkspaceIntent
    data object ApproveCleanMidi : WorkspaceIntent
    data object CreateTechnicalCorrection : WorkspaceIntent
    data class RequestRemoveSongPart(val partId: String) : WorkspaceIntent
    data object ConfirmRemoveSongPart : WorkspaceIntent
    data object CreateMidiAiFix : WorkspaceIntent
    data object ApproveMidiAiFix : WorkspaceIntent
    data object RejectMidiAiFix : WorkspaceIntent
    data object ReturnToCleanedMidi : WorkspaceIntent
    data object RegenerateMidiAiFix : WorkspaceIntent
    data class SelectMidiFeel(val input: MidiAnalysisInput) : WorkspaceIntent
    data class SelectEnhancement(val intensity: app.melotrail.arrangement.EnhancementIntensity) : WorkspaceIntent
    data object ApproveEnhancement : WorkspaceIntent
    data object RejectEnhancement : WorkspaceIntent
    data object SelectApprovedEnhancement : WorkspaceIntent
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
    data object ConfirmSectionChange : WorkspaceIntent
    data class RequestAddPartToStructure(val partId: String) : WorkspaceIntent
    data class RequestRemovePartFromStructure(val partId: String, val instanceId: String) : WorkspaceIntent
    data object ConfirmPartStructureChange : WorkspaceIntent
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
    data class ToggleArrangementRole(val role: ArrangementRole) : WorkspaceIntent
    data class ToggleArrangementTrait(val trait: SoundTrait) : WorkspaceIntent
    data class PinArrangementInstrument(val role: ArrangementRole, val instrumentId: String?) : WorkspaceIntent
    data class UpdateMixSetting(val instrument: String, val setting: LogicalMixSetting) : WorkspaceIntent
    data object ResetMix : WorkspaceIntent
    data object GenerateHumanization : WorkspaceIntent
    data object BypassHumanization : WorkspaceIntent
    data class UpdateBuildOptions(val options: BuildOptionsDraft) : WorkspaceIntent
    data object BuildSong : WorkspaceIntent
    data object ExportCommercialProvenance : WorkspaceIntent
    data object RefreshExport : WorkspaceIntent
    data class UpdateExportDraft(val draft: ExportDraft) : WorkspaceIntent
    data object ChooseExportDestination : WorkspaceIntent
    data object ExportSong : WorkspaceIntent
    data object ExportCommercialSong : WorkspaceIntent
    data object CancelOperation : WorkspaceIntent
    data class SelectPlaybackSource(val source: PlaybackSource) : WorkspaceIntent
    data object PlayPause : WorkspaceIntent
    data object StopPlayback : WorkspaceIntent
    data class SeekPlayback(val seconds: Double) : WorkspaceIntent
    data class SetPlaybackVolume(val volume: Double) : WorkspaceIntent
    data object GenerateArrangement : WorkspaceIntent
    data object GenerateCohesion : WorkspaceIntent
    data class ReviewCohesionBoundary(val outgoingInstanceId: String, val incomingInstanceId: String) : WorkspaceIntent
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
    private val midiAiFixService: MidiAiFixApplicationService = DefaultMidiAiFixApplicationService(),
    private val technicalCorrectionService: TechnicalCorrectionApplicationService = DefaultTechnicalCorrectionApplicationService(),
    private val enhancementService: EnhancementApplicationService = DefaultEnhancementApplicationService(),
    private val mixService: MixApplicationService = DefaultMixApplicationService(),
    private val humanizationService: HumanizationApplicationService = DefaultHumanizationApplicationService(),
    private val buildService: BuildApplicationService? = null,
    private val player: ArtifactAudioPlayer? = null,
    private val partPreviewService: PartPreviewApplicationService? = null,
    private val audioPreparationService: AudioPreparationApplicationService? = null,
    private val preferences: DesktopPreferences = NoOpDesktopPreferences,
    private val soundLibrarySettings: SoundLibrarySettingsService = SoundLibrarySettingsService(preferences),
    private val soundLibraryInventory: LocalSoundLibraryInventoryReader = RegistryLocalSoundLibraryInventoryReader,
    private val operationLogger: DesktopOperationLogger = NoOpDesktopOperationLogger,
    private val commercialProvenanceService: CommercialProvenanceService = CommercialProvenanceService(libraryRoot),
    private val releaseExportService: ReleaseExportApplicationService = DefaultReleaseExportApplicationService(creditsService = ReleaseCreditsService())
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
            is WorkspaceIntent.UpdateProjectSetup -> mutableState.update { current ->
                current.copy(projectSetup = current.projectSetup.copy(draft = intent.draft, validationError = null, invalidationPreview = null))
            }
            WorkspaceIntent.SaveProjectSetup -> saveProjectSetup()
            WorkspaceIntent.ConfirmProjectSetupSave -> confirmProjectSetupSave()
            is WorkspaceIntent.SelectHarmonySection -> selectHarmonySection(intent.section)
            is WorkspaceIntent.SelectHarmonyTemplate -> requestHarmonyMutation(HarmonyMutation.ApplyTemplate(intent.templateId))
            is WorkspaceIntent.SelectHarmonyEvent -> selectHarmonyEvent(intent.eventId)
            is WorkspaceIntent.SetHarmonyRoot -> mutableState.update { it.copy(harmony = it.harmony.copy(draftRoot = intent.root, dirty = it.harmony.selectedEventId != null, error = null)) }
            is WorkspaceIntent.SetHarmonyQuality -> mutableState.update { it.copy(harmony = it.harmony.copy(draftQuality = intent.quality, dirty = it.harmony.selectedEventId != null, error = null)) }
            WorkspaceIntent.AddHarmonyEvent -> requestHarmonyMutation(HarmonyMutation.Add)
            WorkspaceIntent.SaveHarmonyEvent -> requestHarmonyMutation(HarmonyMutation.Save)
            WorkspaceIntent.DeleteHarmonyEvent -> requestHarmonyMutation(HarmonyMutation.Delete)
            is WorkspaceIntent.MoveHarmonyEvent -> requestHarmonyMutation(HarmonyMutation.Move(intent.earlier))
            WorkspaceIntent.ConfirmHarmonyMutation -> state.value.harmony.pendingMutation?.let(::performHarmonyMutation)
            WorkspaceIntent.CancelHarmonyMutation -> mutableState.update { it.copy(harmony = it.harmony.copy(pendingMutation = null)) }
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
            WorkspaceIntent.ConfirmImportProvenance -> confirmImportProvenance()
            WorkspaceIntent.ImportPart -> importPart()
            is WorkspaceIntent.CleanMidi -> cleanMidi(intent.partId)
            is WorkspaceIntent.ShowPartDetails -> showPartDetails(intent)
            is WorkspaceIntent.AnalyzePart -> analyzePart(intent.partId)
            is WorkspaceIntent.ShowSourceKeyConfirmation -> showSourceKeyConfirmation(intent.partId)
            is WorkspaceIntent.SelectConfirmedSourceKey -> selectConfirmedSourceKey(intent.key)
            WorkspaceIntent.ConfirmSourceKey -> confirmSourceKey()
            is WorkspaceIntent.TransposePart -> transposePart(intent.partId)
            is WorkspaceIntent.SelectPart -> selectPart(intent.partId)
            WorkspaceIntent.InspectSelectedPart -> inspectSelectedPart()
            is WorkspaceIntent.SelectCleanupMode -> mutableState.update { it.copy(audioPreparation = it.audioPreparation.copy(cleanupMode = intent.mode)) }
            WorkspaceIntent.ApplySelectedCleanup -> applySelectedCleanup()
            WorkspaceIntent.ConfirmSafeCleanup -> confirmSafeCleanup()
            is WorkspaceIntent.SelectMidiCleanupProfile -> mutableState.update { it.copy(midiQualityReview = it.midiQualityReview.copy(profile = intent.profile)) }
            WorkspaceIntent.ApproveCleanMidi -> approveCleanMidi()
            WorkspaceIntent.CreateTechnicalCorrection -> createTechnicalCorrection()
            is WorkspaceIntent.RequestRemoveSongPart -> requestRemoveSongPart(intent.partId)
            WorkspaceIntent.ConfirmRemoveSongPart -> removeSongPart()
            WorkspaceIntent.CreateMidiAiFix -> createMidiAiFix()
            WorkspaceIntent.ApproveMidiAiFix -> approveMidiAiFix()
            WorkspaceIntent.RejectMidiAiFix -> rejectMidiAiFix()
            WorkspaceIntent.ReturnToCleanedMidi -> returnToCleanedMidi()
            WorkspaceIntent.RegenerateMidiAiFix -> regenerateMidiAiFix()
            is WorkspaceIntent.SelectMidiFeel -> selectMidiFeel(intent.input)
            is WorkspaceIntent.SelectEnhancement -> selectEnhancement(intent.intensity)
            WorkspaceIntent.ApproveEnhancement -> approveEnhancement()
            WorkspaceIntent.RejectEnhancement -> rejectEnhancement()
            WorkspaceIntent.SelectApprovedEnhancement -> selectApprovedEnhancement()
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
            WorkspaceIntent.ConfirmSectionChange -> confirmSectionChange()
            is WorkspaceIntent.RequestAddPartToStructure -> requestAddPartToStructure(intent.partId)
            is WorkspaceIntent.RequestRemovePartFromStructure -> requestRemovePartFromStructure(intent.partId, intent.instanceId)
            WorkspaceIntent.ConfirmPartStructureChange -> confirmPartStructureChange()
            is WorkspaceIntent.AddStructurePart -> addStructurePart(intent.partId)
            is WorkspaceIntent.SelectStructureOccurrence -> selectStructureOccurrence(intent.instanceId)
            is WorkspaceIntent.DuplicateStructureOccurrence -> duplicateStructureOccurrence(intent.instanceId)
            is WorkspaceIntent.RemoveStructureOccurrence -> removeStructureOccurrence(intent.instanceId)
            is WorkspaceIntent.MoveStructureOccurrence -> moveStructureOccurrence(intent.instanceId, intent.earlier)
            is WorkspaceIntent.DuplicateStructurePart -> duplicateStructurePart(intent.index)
            is WorkspaceIntent.RemoveStructurePart -> removeStructurePart(intent.index)
            is WorkspaceIntent.MoveStructurePart -> moveStructurePart(intent.fromIndex, intent.toIndex)
            WorkspaceIntent.ClearStructure -> saveStructure(emptyList())
            is WorkspaceIntent.SelectArrangeTab -> mutableState.update { it.copy(arrangeTab = intent.tab) }
            is WorkspaceIntent.UpdateArrangementPlanner -> updateArrangementPlanner(intent.planner)
            is WorkspaceIntent.UpdateCohesionPlanner -> mutableState.update { it.copy(cohesionDraft = it.cohesionDraft.copy(planner = intent.planner), notification = null) }
            is WorkspaceIntent.UpdateArrangementStyle -> mutableState.update { it.copy(arrangementDraft = it.arrangementDraft.copy(style = intent.style), arrangementDraftDirty = true) }
            is WorkspaceIntent.ToggleArrangementInstrument -> toggleArrangementInstrument(intent.instrument)
            is WorkspaceIntent.ToggleArrangementRole -> toggleArrangementRole(intent.role)
            is WorkspaceIntent.ToggleArrangementTrait -> toggleArrangementTrait(intent.trait)
            is WorkspaceIntent.PinArrangementInstrument -> pinArrangementInstrument(intent.role, intent.instrumentId)
            is WorkspaceIntent.UpdateMixSetting -> updateMixSetting(intent.instrument, intent.setting)
            WorkspaceIntent.ResetMix -> resetMix()
            WorkspaceIntent.GenerateHumanization -> generateHumanization()
            WorkspaceIntent.BypassHumanization -> bypassHumanization()
            is WorkspaceIntent.UpdateBuildOptions -> mutableState.update { it.copy(buildOptions = intent.options) }
            WorkspaceIntent.BuildSong -> buildSong()
            WorkspaceIntent.ExportCommercialProvenance -> exportCommercialProvenance()
            WorkspaceIntent.RefreshExport -> refreshExport()
            is WorkspaceIntent.UpdateExportDraft -> mutableState.update { it.copy(export = it.export.copy(draft = intent.draft)) }
            WorkspaceIntent.ChooseExportDestination -> chooseExportDestination()
            WorkspaceIntent.ExportSong -> exportSong()
            WorkspaceIntent.ExportCommercialSong -> exportSong(commercial = true)
            WorkspaceIntent.CancelOperation -> cancelOperation()
            is WorkspaceIntent.SelectPlaybackSource -> selectPlaybackSource(intent.source)
            WorkspaceIntent.PlayPause -> playPause()
            WorkspaceIntent.StopPlayback -> stopPlaybackSession()
            is WorkspaceIntent.SeekPlayback -> seekPlaybackSession(intent.seconds)
            is WorkspaceIntent.SetPlaybackVolume -> setPlaybackVolume(intent.volume)
            WorkspaceIntent.GenerateArrangement -> generateArrangement()
            WorkspaceIntent.GenerateCohesion -> generateCohesion()
            is WorkspaceIntent.ReviewCohesionBoundary -> reviewCohesionBoundary(intent.outgoingInstanceId, intent.incomingInstanceId)
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
        if (section == WorkspaceSection.SETUP) loadProjectSetup(state.value.project)
        if (section == WorkspaceSection.HARMONY) loadHarmony()
    }

    private fun loadHarmony(conflictMessage: String? = null) {
        val project = state.value.project ?: return mutableState.update {
            it.copy(harmony = it.harmony.copy(loading = false, error = "Open a project and save Setup before editing harmony."))
        }
        if (project.migration.requiresMigration || !project.readiness.compositionSettingsReady) {
            mutableState.update { it.copy(harmony = it.harmony.copy(loading = false, error = "Save Setup before adding structured harmony.")) }
            return
        }
        mutableState.update { it.copy(harmony = it.harmony.copy(loading = true, error = null, pendingMutation = null)) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.getHarmony(GetHarmony(project.root)) } }
                .onSuccess { view -> mutableState.update { current ->
                    if (current.project?.root != project.root) current else {
                        val section = current.harmony.selectedSection.takeIf { selected ->
                            view.progressions.any { it.sectionType == selected } || view.completeness.requiredSections.contains(selected)
                        } ?: view.completeness.requiredSections.firstOrNull() ?: SectionTypeId.VERSE
                        val event = view.progressions.firstOrNull { it.sectionType == section }?.events?.firstOrNull()
                        current.copy(harmony = current.harmony.copy(
                            view = view, selectedSection = section, selectedEventId = event?.id,
                            draftRoot = event?.root ?: PitchClass.canonical(0), draftQuality = event?.quality ?: ChordQuality.MAJOR,
                            dirty = false, loading = false, error = conflictMessage, pendingMutation = null
                        ))
                    }
                } }
                .onFailure { failure -> mutableState.update { current ->
                    if (current.project?.root != project.root) current else current.copy(harmony = current.harmony.copy(loading = false, error = failure.message ?: "Unable to load harmony."))
                } }
        }
    }

    private fun selectHarmonySection(section: SectionTypeId) {
        val event = state.value.harmony.view?.progressions?.firstOrNull { it.sectionType == section }?.events?.firstOrNull()
        mutableState.update { current -> current.copy(harmony = current.harmony.copy(
            selectedSection = section, selectedEventId = event?.id,
            draftRoot = event?.root ?: PitchClass.canonical(0), draftQuality = event?.quality ?: ChordQuality.MAJOR,
            dirty = false, error = null, pendingMutation = null
        )) }
    }

    private fun selectHarmonyEvent(eventId: ChordEventId?) {
        val event = state.value.harmony.view?.progressions?.firstOrNull { it.sectionType == state.value.harmony.selectedSection }
            ?.events?.firstOrNull { it.id == eventId }
        mutableState.update { current -> current.copy(harmony = current.harmony.copy(
            selectedEventId = event?.id, draftRoot = event?.root ?: current.harmony.draftRoot,
            draftQuality = event?.quality ?: current.harmony.draftQuality, dirty = false, error = null, pendingMutation = null
        )) }
    }

    private fun requestHarmonyMutation(mutation: HarmonyMutation) {
        val project = state.value.project ?: return fail("harmony", "Open a project before editing harmony.")
        val editor = state.value.harmony
        val view = editor.view ?: return loadHarmony()
        if (editor.loading || state.value.operation.isMutating) return busy("edit harmony")
        if (view.revision == null) return mutableState.update { it.copy(harmony = it.harmony.copy(error = "Save Setup before adding structured harmony.")) }
        val selected = view.progressions.firstOrNull { it.sectionType == editor.selectedSection }
            ?.events?.firstOrNull { it.id == editor.selectedEventId }
        if (mutation !is HarmonyMutation.Add && mutation !is HarmonyMutation.ApplyTemplate && selected == null) return mutableState.update { it.copy(harmony = it.harmony.copy(error = "Select a chord first.")) }
        if (mutation is HarmonyMutation.Save && !editor.dirty) return
        val processed = project.parts.any { it.preparation.midiAiFix.draftAvailable || it.preparation.midiAiFix.approvedAvailable || it.preparation.midiFeel.available } ||
            project.readiness.songPlanAvailable || project.readiness.cohesionReady || project.readiness.arrangementAvailable || project.readiness.generatedMidiAvailable || project.readiness.stemsAvailable || project.readiness.dryMixAvailable || project.readiness.masterAvailable || project.readiness.releaseAvailable
        if (processed) mutableState.update { it.copy(harmony = it.harmony.copy(pendingMutation = mutation, error = null)) }
        else performHarmonyMutation(mutation)
    }

    private fun performHarmonyMutation(mutation: HarmonyMutation) {
        val project = state.value.project ?: return
        val editor = state.value.harmony
        val view = editor.view ?: return loadHarmony()
        val revision = view.revision ?: return
        val progression = view.progressions.firstOrNull { it.sectionType == editor.selectedSection }
        val selected = progression?.events?.firstOrNull { it.id == editor.selectedEventId }
        val event = selected?.copy(root = editor.draftRoot, quality = editor.draftQuality)
        mutableState.update { it.copy(operation = WorkspaceOperation.SavingHarmony, harmony = it.harmony.copy(loading = true, pendingMutation = null, error = null)) }
        scope.launch {
            runCatching { withContext(ioDispatcher) {
                when (mutation) {
                    is HarmonyMutation.ApplyTemplate -> projectService.setHarmonyProgression(SetHarmonyProgression(project.root, view.projectRevision, revision, editor.selectedSection, mutation.templateId))
                    HarmonyMutation.Add -> {
                        val nextId = nextHarmonyEventId(editor.selectedSection, progression?.events.orEmpty())
                        projectService.createHarmonyEvent(CreateHarmonyEvent(project.root, view.projectRevision, revision, editor.selectedSection, ChordEvent(nextId, editor.draftRoot, editor.draftQuality, 0)))
                    }
                    HarmonyMutation.Save -> projectService.updateHarmonyEvent(UpdateHarmonyEvent(project.root, view.projectRevision, revision, editor.selectedSection, requireNotNull(event)))
                    HarmonyMutation.Delete -> projectService.deleteHarmonyEvent(DeleteHarmonyEvent(project.root, view.projectRevision, revision, editor.selectedSection, requireNotNull(selected).id))
                    is HarmonyMutation.Move -> {
                        val currentIndex = requireNotNull(progression).events.indexOf(requireNotNull(selected))
                        projectService.reorderHarmonyEvent(ReorderHarmonyEvent(project.root, view.projectRevision, revision, editor.selectedSection, selected.id, currentIndex + if (mutation.earlier) -1 else 1))
                    }
                }
            } }.onSuccess { result -> mutableState.update { current ->
                if (current.project?.root != project.root) current else {
                    val selectedId = when (mutation) {
                        is HarmonyMutation.ApplyTemplate -> result.harmony.progressions.firstOrNull { it.sectionType == editor.selectedSection }?.events?.firstOrNull()?.id
                        HarmonyMutation.Add -> result.harmony.progressions.firstOrNull { it.sectionType == editor.selectedSection }?.events?.lastOrNull()?.id
                        HarmonyMutation.Delete -> null
                        else -> editor.selectedEventId
                    }
                    val selectedEvent = result.harmony.progressions.firstOrNull { it.sectionType == editor.selectedSection }?.events?.firstOrNull { it.id == selectedId }
                    current.copy(
                        project = result.snapshot, operation = WorkspaceOperation.Idle,
                        downstreamArtifactsStale = result.snapshot.readiness.staleArtifacts.isNotEmpty(),
                        notification = "Harmony saved. ${result.invalidation.artifacts.size} downstream artifact type(s) are stale evidence.",
                        harmony = current.harmony.copy(view = result.harmony, selectedEventId = selectedEvent?.id,
                            draftRoot = selectedEvent?.root ?: PitchClass.canonical(0), draftQuality = selectedEvent?.quality ?: ChordQuality.MAJOR,
                            dirty = false, loading = false, error = null)
                    )
                }
            } }.onFailure { failure ->
                val message = failure.message ?: "Unable to save harmony."
                if (message.contains("reload harmony", ignoreCase = true) || message.contains("Harmony changed", ignoreCase = true)) {
                    mutableState.update { it.copy(operation = WorkspaceOperation.Idle) }
                    loadHarmony("Harmony changed elsewhere. The current revision was refreshed; review it before saving again.")
                } else mutableState.update { current -> current.copy(operation = WorkspaceOperation.Failed("harmony", message), harmony = current.harmony.copy(loading = false, error = message)) }
            }
        }
    }

    private fun nextHarmonyEventId(section: SectionTypeId, events: List<ChordEvent>): ChordEventId {
        var index = 1
        while (events.any { it.id.value == "h-${section.value}-$index" }) index++
        return ChordEventId("h-${section.value}-$index")
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
            if (state.value.operation is WorkspaceOperation.ImportingPart || state.value.operation is WorkspaceOperation.CleaningMidi) {
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
        val project = state.value.project ?: return fail("project migration", "Open a legacy project first.")
        if (!project.migration.requiresMigration && project.version >= 4) return fail("project migration", "This project is already schema v4.")
        if (state.value.operation.isMutating) return busy("migrate project")
        val feedbackId = beginFeedback(OperationKind.PROJECT_OPEN, OperationPhase.LOCAL, "Migrating ${project.name} to schema v4…")
        mutableState.update { it.copy(operation = WorkspaceOperation.OpeningProject(project.root), operationFeedback = feedbackTracker.current, notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.migrateProject(project.root) } }
                .onSuccess { opened(it, "Migrated ${it.name} to project schema v4", feedbackId) }
                .onFailure { fail("project migration", it.message ?: "Unable to migrate project.", sessionId = feedbackId) }
        }
    }

    private fun loadProjectSetup(project: ProjectSnapshot?) {
        project ?: return
        if (project.migration.requiresMigration) return
        mutableState.update { current ->
            if (current.project?.root == project.root) current.copy(projectSetup = current.projectSetup.copy(loading = true, validationError = null)) else current
        }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.getCompositionSettings(GetCompositionSettings(project.root)) } }
                .onSuccess { result -> mutableState.update { current ->
                    if (current.project?.root == project.root) current.copy(projectSetup = ProjectSetupUiState.from(result, project.name)) else current
                } }
                .onFailure { failure -> mutableState.update { current ->
                    if (current.project?.root == project.root) current.copy(projectSetup = ProjectSetupUiState(validationError = failure.message ?: "Unable to load setup choices.")) else current
                } }
        }
    }

    private fun saveProjectSetup() {
        val project = state.value.project ?: return fail("project setup", "Open a project before saving setup.")
        if (project.migration.requiresMigration) return fail("project setup", "Save this legacy project as v4 before changing setup.")
        val setup = state.value.projectSetup
        val input = setup.draft?.inputOrError()?.getOrElse { return updateSetupError(it.message ?: "Setup is invalid.") }
            ?: return updateSetupError("Setup choices are still loading.")
        if (state.value.operation.isMutating) return busy("save project setup")
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.previewSettingsChange(PreviewSettingsChange(project.root, setup.saved?.decisionRevision ?: 0, input)) } }
                .onSuccess { preview ->
                    if (setup.saved != null && preview.invalidation.artifacts.isNotEmpty()) {
                        mutableState.update { current -> current.copy(projectSetup = current.projectSetup.copy(invalidationPreview = preview.invalidation, validationError = null)) }
                    } else updateProjectSetup(project, input)
                }
                .onFailure { updateSetupError(it.message ?: "Unable to validate setup.") }
        }
    }

    private fun confirmProjectSetupSave() {
        val project = state.value.project ?: return
        val input = state.value.projectSetup.draft?.inputOrError()?.getOrNull() ?: return
        if (state.value.projectSetup.invalidationPreview == null || state.value.operation.isMutating) return
        updateProjectSetup(project, input)
    }

    private fun updateProjectSetup(project: ProjectSnapshot, input: app.melotrail.application.CompositionSettingsInput) {
        val revision = state.value.projectSetup.saved?.decisionRevision ?: 0
        mutableState.update { it.copy(operation = WorkspaceOperation.SavingProjectSetup, notification = null, projectSetup = it.projectSetup.copy(validationError = null)) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.updateCompositionSettings(UpdateCompositionSettings(project.root, revision, input)) } }
                .onSuccess { result ->
                    mutableState.update { current ->
                        current.copy(
                            project = result.snapshot,
                            projectSetup = current.projectSetup.copy(saved = result.settings, draft = ProjectSetupDraft(result.settings.name, result.settings.key.tonic, result.settings.key.modeId, result.settings.tempo.bpm.toString(), result.settings.timeSignature, result.settings.profile, result.settings.mood), invalidationPreview = null),
                            operation = WorkspaceOperation.Idle,
                            notification = "Project setup saved.",
                            downstreamArtifactsStale = current.downstreamArtifactsStale || result.invalidation.artifacts.isNotEmpty()
                        )
                    }
                    hydrateProject(result.snapshot, "Project setup saved.", resetWorkspace = false)
                }
                .onFailure { updateSetupError(it.message ?: "Unable to save setup.") }
        }
    }

    private fun updateSetupError(message: String) = mutableState.update { current ->
        current.copy(operation = WorkspaceOperation.Idle, projectSetup = current.projectSetup.copy(validationError = message, invalidationPreview = null), notification = message)
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
            it.copy(dialog = WorkspaceDialog.ImportPart(), notification = null)
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
                role = defaultPartRole(source),
                name = source.fileName.toString().substringBeforeLast('.').ifBlank { "Melody part" },
                sectionType = app.melotrail.arrangement.SectionTypeCatalog.fromLegacyRole(defaultPartRole(source)),
                detectedType = type,
                sourceSizeBytes = size,
                validationMessage = message,
                preference = ImportPreference.ANY,
                provenanceConfirmed = false,
                confirmationMessage = null
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

    private fun defaultPartRole(source: Path): String {
        val name = source.fileName.toString().substringBeforeLast('.').lowercase()
        return when {
            "intro" in name -> "intro"
            "chorus" in name || "hook" in name -> "chorus"
            "bridge" in name -> "bridge"
            "outro" in name -> "outro"
            else -> "verse"
        }
    }

    private fun confirmImportProvenance() {
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        if (draft.source == null || draft.detectedType == ImportSourceKind.UNSUPPORTED || draft.validationMessage != null) {
            return failImportDraft(draft, "Choose a supported source before confirming source rights.")
        }
        mutableState.update { it.copy(dialog = draft.copy(provenanceConfirmed = true, confirmationMessage = null), notification = null) }
    }

    private fun importPart() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.ImportPart ?: return
        val source = draft.source ?: return failImportDraft(draft, "Choose a MIDI, WAV, or MP3 source first.")
        if (draft.detectedType == ImportSourceKind.UNSUPPORTED) return failImportDraft(draft, draft.validationMessage ?: "Unsupported source type.")
        if (!draft.provenanceConfirmed) return failImportDraft(draft, "Confirm the source-rights record before importing.", ImportFlowStep.CONFIRM_PROVENANCE)
        if (draft.id.isBlank()) return failImportDraft(draft, "Part ID is required and remains stable after import.", ImportFlowStep.NEXT_ACTION)
        if (project.parts.any { it.id.equals(draft.id, ignoreCase = true) }) return failImportDraft(draft, "Part ID already exists: ${draft.id}", ImportFlowStep.NEXT_ACTION)
        if (draft.detectedType?.isAudio == true) state.value.runtimeReadiness.capabilityFailure(RuntimeCapability.AUDIO_IMPORT)?.let {
            return failImportDraft(draft, it, ImportFlowStep.NEXT_ACTION, action = "import audio")
        }
        val command = ImportSongPart(
            root = project.root,
            id = draft.id,
            file = source,
            name = draft.name.ifBlank { draft.id },
            sectionType = draft.sectionType,
            sourceAttestation = SourceRightsAttestation(draft.rightsClaim, Instant.now().toString())
        )
        runAutomaticImport(command, draft)
    }

    private fun failImportDraft(
        draft: WorkspaceDialog.ImportPart,
        message: String,
        step: ImportFlowStep = if (draft.source == null || draft.detectedType == ImportSourceKind.UNSUPPORTED) ImportFlowStep.INSPECT_AND_VALIDATE else ImportFlowStep.NEXT_ACTION,
        action: String = "import part"
    ) {
        operationLogger.event(action, "validation-failed", draft.source, IllegalArgumentException(message))
        mutableState.update {
            it.copy(
                operation = WorkspaceOperation.Failed(action, message),
                notification = message,
                dialog = draft.copy(
                    validationMessage = if (step == ImportFlowStep.INSPECT_AND_VALIDATE) message else draft.validationMessage,
                    confirmationMessage = if (step == ImportFlowStep.INSPECT_AND_VALIDATE) null else message
                )
            )
        }
    }

    private fun runAutomaticImport(command: ImportSongPart, draft: WorkspaceDialog.ImportPart) {
        val feedbackId = beginFeedback(OperationKind.IMPORT, OperationPhase.VALIDATING, "Validating import for ${command.id}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ImportingPart(command.id), notification = null, retry = null, dialog = null, operationFeedback = feedbackTracker.current) }
        importPreparationJob = scope.launch {
            try {
                val snapshot = withContext(ioDispatcher) { projectService.importSongPart(command).snapshot.refreshed() }
                if (reduceMelodyPartCard(snapshot, snapshot.parts.first { it.id == command.id }).retryable) {
                    failAutomaticImport(draft, command, snapshot, feedbackId)
                } else opened(snapshot, "Imported ${command.name}. Raw MIDI is ready; run Clean MIDI when you are ready to continue.", feedbackId)
            } catch (failure: Throwable) {
                if (failure !is CancellationException) failAutomaticImport(draft, command, null, feedbackId, failure.message ?: "Unable to import ${command.id}.")
            }
        }
    }

    private fun failAutomaticImport(
        draft: WorkspaceDialog.ImportPart,
        command: ImportSongPart,
        snapshot: ProjectSnapshot?,
        feedbackId: String,
        fallback: String = "The automatic import stage did not complete. Review the required input and retry."
    ) {
        val message = snapshot?.parts?.firstOrNull { it.id == command.id }?.let { part ->
            reduceMelodyPartCard(snapshot, part).stages.firstOrNull { it.status == MelodyPartStageStatus.FAILED }?.detail
        } ?: fallback
        val feedback = feedbackTracker.fail(feedbackId, message, OperationRetryAction.RETRY_SAFE_OPERATION) ?: state.value.operationFeedback
        mutableState.update { current ->
            current.copy(project = snapshot ?: current.project, operation = WorkspaceOperation.Failed("import part", message), notification = message,
                retry = WorkspaceRetry.AutomaticImport(command.copy(expectedRevision = snapshot?.parts?.firstOrNull { it.id == command.id }?.revision ?: command.expectedRevision), draft),
                dialog = draft.copy(validationMessage = message), operationFeedback = feedback)
        }
    }

    private fun failImportFromService(draft: WorkspaceDialog.ImportPart, request: ImportPartRequest, message: String, feedbackId: String) {
        val feedback = feedbackTracker.fail(feedbackId, message, OperationRetryAction.RETRY_SAFE_OPERATION) ?: state.value.operationFeedback
        operationLogger.operationEvent(feedback.sessionId, feedback.kind, feedback.phase, failure = IllegalStateException(message))
        mutableState.update { current ->
            current.copy(
                operation = WorkspaceOperation.Failed("import part", message),
                notification = message,
                retry = WorkspaceRetry.Import(request),
                dialog = draft.copy(validationMessage = message, confirmationMessage = null),
                operationFeedback = feedback
            )
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

    private fun showSourceKeyConfirmation(partId: String) {
        val part = state.value.project?.parts?.singleOrNull { it.id == partId } ?: return
        if (state.value.operation.isMutating) return
        val selected = part.sourceKey?.detectedKey ?: MusicalKey(PitchClass.canonical(0), ScaleModeId.MAJOR)
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmSourceKey(partId, selected), notification = null) }
    }

    private fun selectConfirmedSourceKey(key: MusicalKey) {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmSourceKey ?: return
        mutableState.update { it.copy(dialog = dialog.copy(selected = key)) }
    }

    private fun confirmSourceKey() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmSourceKey ?: return
        val project = state.value.project ?: return
        val part = project.parts.singleOrNull { it.id == dialog.partId } ?: return
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.VALIDATING, "Confirming source key…")
        mutableState.update { it.copy(operation = WorkspaceOperation.AnalyzingPart(part.id), dialog = null, notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.confirmSourceKey(ConfirmSourceKey(project.root, part.id, dialog.selected, part.revision)).refreshed() } }
                .onSuccess { opened(it, "Confirmed source key for ${part.name}", feedbackId) }
                .onFailure { fail("confirm source key", it.message ?: "Unable to confirm the source key.", sessionId = feedbackId) }
        }
    }

    private fun transposePart(partId: String) {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.VALIDATING, "Transposing $partId to the project key…")
        mutableState.update { it.copy(operation = WorkspaceOperation.AnalyzingPart(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.transposePart(TransposePartRequest(project.root, partId)).refreshed() } }
                .onSuccess { opened(it, "Transposed $partId to the project key", feedbackId) }
                .onFailure { fail("transpose part", it.message ?: "Unable to transpose the part.", WorkspaceRetry.Transpose(project.root, partId), feedbackId) }
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
        loadEnhancementReview(project.root, part.id)
    }

    /** Retained draft/rejected evidence is loaded for review, never promoted to a selectable artifact. */
    private fun loadEnhancementReview(root: Path, partId: String) {
        scope.launch {
            runCatching { withContext(ioDispatcher) { enhancementService.load(root, partId) } }
                .onSuccess { review ->
                    mutableState.update { current ->
                        if (current.project?.root == root && current.selectedPartId == partId) current.copy(enhancementReview = review) else current
                    }
                }
        }
    }

    private fun selectPart(partId: String) {
        val project = state.value.project ?: return
        val part = project.parts.find { it.id == partId } ?: return
        if (part.sourceType != PartSourceType.AUDIO) {
            mutableState.update { it.copy(selectedPartId = partId, selectedArtifact = CreationArtifactReference(CreationArtifactKind.PART_SOURCE, partId), pendingMidiFeel = null, midiQualityReview = MidiQualityReviewDraft(), audioPreparation = AudioPreparationUiState(partId = partId), notification = "Audio preparation is available for WAV/MP3 parts only.") }
            return
        }
        mutableState.update { it.copy(selectedPartId = partId, selectedArtifact = CreationArtifactReference(CreationArtifactKind.PART_SOURCE, partId), pendingMidiFeel = null, midiQualityReview = MidiQualityReviewDraft(), audioPreparation = AudioPreparationUiState(partId = partId), notification = null) }
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

    private fun cleanMidi(partId: String) {
        val project = state.value.project ?: return fail("Clean MIDI", "Open a project before cleaning MIDI.")
        val part = project.parts.find { it.id == partId } ?: return fail("Clean MIDI", "Part '$partId' is no longer available.")
        if (!part.preparation.rawMidi) return fail("Clean MIDI", "Part '$partId' has no immutable raw MIDI to clean.")
        when (part.preparation.midiQuality.status) {
            MidiQualityStatus.LEGACY_UNKNOWN -> return fail("Clean MIDI", "This legacy part has no raw-to-clean provenance. Re-import it to create current quality evidence.")
            MidiQualityStatus.CURRENT, MidiQualityStatus.STALE_OR_INVALID, MidiQualityStatus.APPROVAL_REQUIRED -> Unit
        }
        val profile = state.value.midiQualityReview.profile
        mutableState.update { it.copy(selectedPartId = partId) }
        if (profile == MidiCleanupProfile.TIGHTEN_TIMING) {
            mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmTightenTiming(partId)) }
        } else {
            runCleanMidi(CleanMidiRequest(project.root, partId, namedMidiCleanupOptions(profile)))
        }
    }

    private fun approveCleanMidi() {
        val project = state.value.project ?: return fail("Clean MIDI", "Open a project before approving Clean MIDI.")
        val partId = state.value.selectedPartId ?: return fail("Clean MIDI", "Select a part before approving Clean MIDI.")
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.MIDI_CLEANUP, OperationPhase.VALIDATING, "Approving Clean MIDI for $partId…")
        mutableState.update { it.copy(operation = WorkspaceOperation.CleaningMidi(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.approveCleanMidi(project.root, partId) } }
                .onSuccess { snapshot ->
                    val message = "Clean MIDI approved. Analyze $partId when ready."
                    mutableState.update { current -> current.copy(project = snapshot, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback, downstreamArtifactsStale = true) }
                }
                .onFailure { failure -> if (failure !is CancellationException) fail("Clean MIDI", failure.message ?: "Unable to approve Clean MIDI for $partId.", sessionId = feedbackId) }
        }
    }

    private fun createTechnicalCorrection() {
        val project = state.value.project ?: return fail("Technical Correction", "Open a project before correcting MIDI.")
        val partId = state.value.selectedPartId ?: return fail("Technical Correction", "Select a cleaned MIDI part before correcting it.")
        val part = project.parts.find { it.id == partId } ?: return fail("Technical Correction", "Selected part is no longer available.")
        if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT) return fail("Technical Correction", "Clean MIDI before technical correction.")
        if (state.value.operation.isMutating) return
        val request = CreateTechnicalCorrectionRequest(project.root, partId)
        val feedbackId = beginFeedback(OperationKind.MIDI_CLEANUP, OperationPhase.LOCAL, "Correcting technical MIDI issues for $partId…")
        mutableState.update { it.copy(operation = WorkspaceOperation.CorrectingMidi(partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { technicalCorrectionService.create(request); projectService.open(project.root) } }
                .onSuccess { snapshot ->
                    val message = "Technical Correction applied. AI Fix is ready."
                    mutableState.update { current -> current.copy(project = snapshot, operation = WorkspaceOperation.Idle, notification = message,
                        operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback, downstreamArtifactsStale = true) }
                }
                .onFailure { fail("Technical Correction", it.message ?: "Unable to correct $partId.", WorkspaceRetry.TechnicalCorrection(request), feedbackId) }
        }
    }

    private fun requestRemoveSongPart(partId: String) {
        val project = state.value.project ?: return
        if (state.value.operation.isMutating) return busy("remove a Melody track")
        if (project.parts.none { it.id == partId }) return
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmRemoveSongPart(partId)) }
    }

    private fun removeSongPart() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmRemoveSongPart ?: return
        val project = state.value.project ?: return
        val part = project.parts.firstOrNull { it.id == dialog.partId } ?: run {
            mutableState.update { it.copy(dialog = null) }
            return
        }
        if (state.value.operation.isMutating) return
        cancelPlaybackSession(resetState = true)
        mutableState.update { it.copy(operation = WorkspaceOperation.RemovingSongPart(part.id), dialog = null, notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { projectService.removeSongPart(RemoveSongPartRequest(project.root, part.id)) } }
                .onSuccess { snapshot ->
                    val message = "Removed ${part.name} from Melody Parts. Its source and derived files were retained."
                    opened(snapshot, message, stale = snapshot.readiness.staleArtifacts.isNotEmpty())
                    mutableState.update { current -> current.copy(
                        selectedPartId = snapshot.parts.firstOrNull()?.id,
                        midiAiFix = current.midiAiFix?.takeIf { it.partId != part.id },
                        enhancementReview = current.enhancementReview?.takeIf { it.partId != part.id },
                        pendingMidiFeel = null
                    ) }
                }
                .onFailure { fail("remove Melody track", it.message ?: "Unable to remove ${part.name}.") }
        }
    }

    private fun createMidiAiFix() {
        val project = state.value.project ?: return fail("AI fix", "Open a project before creating an AI fix.")
        val partId = state.value.selectedPartId ?: return fail("AI fix", "Select a cleaned MIDI part before creating an AI fix.")
        val part = project.parts.find { it.id == partId } ?: return fail("AI fix", "Selected part is no longer available.")
        if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT) return fail("AI fix", "Approve current Clean MIDI before creating an AI fix.")
        if (state.value.operation.isMutating) return
        runCreateMidiAiFix(CreateMidiAiFixRequest(project.root, partId))
    }

    private fun regenerateMidiAiFix() {
        val project = state.value.project ?: return fail("AI fix", "Open a project before regenerating an AI fix.")
        val partId = state.value.selectedPartId ?: return fail("AI fix", "Select a cleaned MIDI part before regenerating an AI fix.")
        if (state.value.operation.isMutating) return
        runCreateMidiAiFix(CreateMidiAiFixRequest(project.root, partId))
    }

    private fun runCreateMidiAiFix(request: CreateMidiAiFixRequest) {
        val feedbackId = beginFeedback(OperationKind.AI_FIX, OperationPhase.WAITING_FOR_MODEL, "Preparing a bounded AI-fix draft…")
        mutableState.update { it.copy(operation = WorkspaceOperation.CreatingMidiAiFix(request.partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    midiAiFixService.create(request) { progress -> scope.launch { updateProgress(feedbackId, WorkspaceOperation.CreatingMidiAiFix(request.partId, progress)) } }
                }
            }.onSuccess { fix ->
                val refreshed = runCatching { projectService.open(request.root) }.getOrNull()
                val message = if (fix.noSafeFix) "AI Fix found no safe change. ${fix.noSafeFixReason ?: "Corrected MIDI remains selected."}" else "AI Fix is ready. Accept it, refuse it, or regenerate it."
                mutableState.update { current -> current.copy(project = refreshed ?: current.project, midiAiFix = fix, arrangement = null, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message, OperationSeverity.WARNING) ?: current.operationFeedback, downstreamArtifactsStale = current.downstreamArtifactsStale) }
            }.onFailure { fail("AI fix", it.message ?: "Unable to create an AI-fix draft. Keep cleaned MIDI and retry after recovering the local model.", WorkspaceRetry.CreateMidiAiFix(request), feedbackId) }
        }
    }

    private fun approveMidiAiFix() {
        val project = state.value.project ?: return fail("AI fix", "Open a project before approving an AI fix.")
        val partId = state.value.selectedPartId ?: return fail("AI fix", "Select an AI-fix draft before approving it.")
        val fix = state.value.midiAiFix ?: return fail("AI fix", "Create and review an AI-fix draft before approving it.")
        if (fix.partId != partId || fix.approved || state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.APPROVAL, OperationPhase.VALIDATING, "Approving validated AI-fix draft…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ApprovingMidiAiFix(partId), notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { midiAiFixService.approve(project.root, partId) } }
                .onSuccess { approved ->
                    val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                    val message = "AI Fix accepted. AI Enhance is ready."
                    mutableState.update { current -> current.copy(project = refreshed ?: current.project, midiAiFix = approved, arrangement = null, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message) ?: current.operationFeedback, downstreamArtifactsStale = true) }
                }
                .onFailure { fail("AI fix", it.message ?: "Unable to approve this AI-fix draft.", sessionId = feedbackId) }
        }
    }

    private fun rejectMidiAiFix() = returnToCleanedMidi(rejected = true)

    private fun returnToCleanedMidi(rejected: Boolean = false) {
        val project = state.value.project ?: return fail("AI fix", "Open a project before returning to cleaned MIDI.")
        val partId = state.value.selectedPartId ?: return fail("AI fix", "Select a part before returning to cleaned MIDI.")
        if (state.value.operation.isMutating) return
        scope.launch {
            runCatching { withContext(ioDispatcher) { if (rejected) midiAiFixService.reject(project.root, partId) else midiAiFixService.skip(project.root, partId) } }
                .onSuccess { retained ->
                    val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                    val message = if (rejected) "AI Fix refused. Corrected MIDI remains selected." else "AI Fix skipped. Corrected MIDI remains selected."
                    mutableState.update { current -> current.copy(project = refreshed ?: current.project, midiAiFix = retained, arrangement = null, notification = message, downstreamArtifactsStale = current.downstreamArtifactsStale || refreshed?.readiness?.staleArtifacts?.isNotEmpty() == true) }
                }
                .onFailure { fail("AI fix", it.message ?: "Unable to return to cleaned MIDI.") }
        }
    }

    private fun selectMidiFeel(input: MidiAnalysisInput) {
        val project = state.value.project ?: return fail("Lo-fi MIDI Feel", "Open a project before choosing MIDI feel.")
        val partId = state.value.selectedPartId ?: return fail("Lo-fi Feel", "Select a cleaned MIDI part first.")
        val part = project.parts.find { it.id == partId } ?: return fail("Lo-fi Feel", "Selected part is no longer available.")
        if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT) return fail("Lo-fi Feel", "Approve current Clean MIDI before choosing a MIDI feel.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(pendingMidiFeel = input, notification = null, retry = null) }
    }

    private fun applyMidiFeelAndReanalyze() {
        val project = state.value.project ?: return fail("Lo-fi MIDI Feel", "Open a project before applying MIDI feel.")
        val partId = state.value.selectedPartId ?: return fail("Lo-fi MIDI Feel", "Select a cleaned MIDI part first.")
        val input = state.value.pendingMidiFeel ?: return
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.MIDI_CLEANUP, OperationPhase.VALIDATING, "Applying ${if (input == MidiAnalysisInput.LOFI_FEEL) "Lo-fi MIDI Feel · 80 BPM + 58% swing" else "Original MIDI"} and re-analyzing…")
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

    private fun selectEnhancement(intensity: app.melotrail.arrangement.EnhancementIntensity) {
        val project = state.value.project ?: return fail("Enhancement", "Open a project before choosing enhancement.")
        val partId = state.value.selectedPartId ?: return fail("Enhancement", "Select a corrected MIDI part first.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.SelectingEnhancement(partId), notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) {
                if (intensity == app.melotrail.arrangement.EnhancementIntensity.OFF) {
                    projectService.selectEnhancement(app.melotrail.application.SelectEnhancementRequest(project.root, partId, intensity)) to null
                } else {
                    val review = enhancementService.create(CreateEnhancementRequest(project.root, partId, intensity))
                    projectService.open(project.root) to review
                }
            } }.onSuccess { (snapshot, review) ->
                val message = if (intensity == app.melotrail.arrangement.EnhancementIntensity.OFF) "Corrected MIDI selected; previous enhancement evidence was retained." else "Enhancement draft generated. Preview it, then approve or reject it."
                mutableState.update { current -> current.copy(project = snapshot, enhancementReview = review ?: current.enhancementReview, arrangement = null, operation = WorkspaceOperation.Idle, notification = message, downstreamArtifactsStale = review != null) }
            }.onFailure { fail("Enhancement", it.message ?: "Unable to select enhancement for $partId.", WorkspaceRetry.Enhancement(project.root, partId, intensity)) }
        }
    }

    private fun approveEnhancement() {
        val project = state.value.project ?: return fail("Enhancement", "Open a project first.")
        val review = state.value.enhancementReview ?: return fail("Enhancement", "Generate and preview an enhancement draft first.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.SelectingEnhancement(review.partId), notification = null) }
        scope.launch { runCatching { withContext(ioDispatcher) {
            val approved = enhancementService.approve(ApproveEnhancementRequest(project.root, review.partId, review.draftSha256, review.inputSha256, review.contextSha256))
            projectService.open(project.root) to approved
        } }.onSuccess { (snapshot, approved) ->
            mutableState.update { it.copy(project = snapshot, enhancementReview = approved, operation = WorkspaceOperation.Idle, notification = "Enhancement approved and selected.", downstreamArtifactsStale = true) }
        }.onFailure { fail("Enhancement approval", it.message ?: "Unable to approve enhancement.") } }
    }

    private fun rejectEnhancement() {
        val project = state.value.project ?: return fail("Enhancement", "Open a project first.")
        val review = state.value.enhancementReview ?: return fail("Enhancement", "No enhancement draft is available.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.SelectingEnhancement(review.partId), notification = null) }
        scope.launch { runCatching { withContext(ioDispatcher) {
            val rejected = enhancementService.reject(project.root, review.partId)
            projectService.open(project.root) to rejected
        } }.onSuccess { (snapshot, rejected) ->
            mutableState.update { it.copy(project = snapshot, enhancementReview = rejected, operation = WorkspaceOperation.Idle, notification = "Enhancement rejected; corrected MIDI remains selected.") }
        }.onFailure { fail("Enhancement rejection", it.message ?: "Unable to reject enhancement.") } }
    }

    private fun selectApprovedEnhancement() {
        val project = state.value.project ?: return fail("Enhancement", "Open a project first.")
        val part = project.parts.singleOrNull { it.id == state.value.selectedPartId } ?: return fail("Enhancement", "Select a part before selecting retained enhancement evidence.")
        val review = state.value.enhancementReview?.takeIf { it.partId == part.id && it.approval == app.melotrail.arrangement.EnhancementApproval.APPROVED }
            ?: return fail("Enhancement", "Load approved enhancement evidence before selecting it.")
        if (state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.SelectingEnhancement(part.id), notification = null, retry = null) }
        scope.launch { runCatching { withContext(ioDispatcher) {
            val selected = enhancementService.selectApproved(SelectApprovedEnhancementRequest(project.root, part.id, review.draftSha256, review.inputSha256, review.contextSha256, part.revision))
            projectService.open(project.root) to selected
        } }.onSuccess { (snapshot, selected) ->
            mutableState.update { it.copy(project = snapshot, enhancementReview = selected, arrangement = null, operation = WorkspaceOperation.Idle, notification = "Approved Enhanced MIDI selected; downstream artifacts need regeneration.", downstreamArtifactsStale = true) }
        }.onFailure { fail("Enhancement selection", it.message ?: "Unable to select retained enhancement.") } }
    }

    private fun confirmTightenTiming() {
        val dialog = state.value.dialog as? WorkspaceDialog.ConfirmTightenTiming ?: return
        val project = state.value.project ?: return
        mutableState.update { it.copy(dialog = null) }
        runCleanMidi(
            CleanMidiRequest(project.root, dialog.partId, namedMidiCleanupOptions(MidiCleanupProfile.TIGHTEN_TIMING))
        )
    }

    private fun runCleanMidi(request: CleanMidiRequest) {
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.MIDI_CLEANUP, OperationPhase.WAITING_FOR_WORKER, "Cleaning MIDI for ${request.partId}…")
        mutableState.update { it.copy(operation = WorkspaceOperation.CleaningMidi(request.partId), notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    projectService.cleanMidi(request) { progress ->
                        scope.launch { updateProgress(feedbackId, WorkspaceOperation.CleaningMidi(request.partId, progress)) }
                    }
                }
            }.onSuccess { snapshot ->
                // A Clean MIDI run changes the digest. Do not leave the old monitor artifact selected.
                cancelPlaybackSession(resetState = true)
                val reviewRequired = snapshot.parts.firstOrNull { it.id == request.partId }?.preparation?.midiQuality?.status == MidiQualityStatus.APPROVAL_REQUIRED
                val message = if (reviewRequired) "Clean MIDI is ready for A/B review and approval." else "Clean MIDI is current. Analyze ${request.partId} when ready."
                mutableState.update {
                    it.copy(
                        project = snapshot,
                        arrangement = null,
                        operation = WorkspaceOperation.Idle,
                        notification = message,
                        operationFeedback = feedbackTracker.complete(feedbackId, message, if (reviewRequired) OperationSeverity.WARNING else OperationSeverity.SUCCESS) ?: it.operationFeedback,
                        downstreamArtifactsStale = true,
                        retry = null
                    )
                }
            }.onFailure { failure ->
                fail("Clean MIDI", failure.message ?: "Unable to clean MIDI for ${request.partId}.", WorkspaceRetry.CleanMidi(request), feedbackId)
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
                .onSuccess { completedPreparation(it, "Transcription quality gate passed. Clean MIDI is next for $partId.", feedbackId) }
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
            is PreviewResult.Resolved -> PlaybackArtifactIdentity(request.projectRoot, resolved.artifact, request.partId, request.audioSource, resolved.source)
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
        if (state.value.operation.isMutating || reduceMelodyPartCard(state.value.project ?: return, part).processing) return busy("change a part section")
        mutableState.update { it.copy(dialog = WorkspaceDialog.EditRole(part.id, part.sectionType.value)) }
    }

    private fun updateRole(role: String) {
        val draft = state.value.dialog as? WorkspaceDialog.EditRole ?: return
        mutableState.update { it.copy(dialog = draft.copy(role = role)) }
    }

    private fun saveRole() {
        val draft = state.value.dialog as? WorkspaceDialog.EditRole ?: return
        val section = runCatching { app.melotrail.arrangement.SectionTypeCatalog.fromLegacyRole(draft.role) }.getOrNull()
            ?: return fail("update section", "Choose a valid section.")
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmSectionChange(draft.partId, section)) }
    }

    private fun confirmSectionChange() {
        val project = state.value.project ?: return
        val draft = state.value.dialog as? WorkspaceDialog.ConfirmSectionChange ?: return
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.LOCAL, "Saving ${draft.partId} role…")
        mutableState.update { it.copy(operation = WorkspaceOperation.UpdatingPartRole(draft.partId), notification = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    val part = project.parts.first { it.id == draft.partId }
                    projectService.updateSongPartSection(
                        UpdateSongPartSectionRequest(
                            project.root,
                            draft.partId,
                            draft.sectionType,
                            part.revision
                        )
                    ).refreshed()
                }
            }.onSuccess { opened(it, "Updated ${draft.partId} section", feedbackId) }
                .onFailure { fail("update section", it.message ?: "Unable to update section.", sessionId = feedbackId) }
        }
    }

    private fun requestAddPartToStructure(partId: String) {
        val project = state.value.project ?: return
        val part = project.parts.firstOrNull { it.id == partId } ?: return
        if (state.value.operation.isMutating || reduceMelodyPartCard(project, part).processing) return busy("add a part to structure")
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmPartStructureChange(partId)) }
    }

    private fun requestRemovePartFromStructure(partId: String, instanceId: String) {
        if (state.value.operation.isMutating) return busy("remove a part from structure")
        mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmPartStructureChange(partId, instanceId)) }
    }

    private fun confirmPartStructureChange() {
        val draft = state.value.dialog as? WorkspaceDialog.ConfirmPartStructureChange ?: return
        if (draft.instanceId == null) addStructurePart(draft.partId)
        else removeStructureOccurrence(draft.instanceId)
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
        val after = state.value.project?.structure?.lastOrNull()
        mutateStructure("add to structure", partId) {
            projectService.insertStructureOccurrence(InsertStructureOccurrenceRequest(project.root, partId, after?.instanceId, after?.revision)).refreshed()
        }
    }

    private fun duplicateStructureOccurrence(instanceId: String) {
        val occurrence = state.value.project?.structure?.firstOrNull { it.instanceId == instanceId } ?: return
        mutateStructure("duplicate structure", instanceId, selectInsertedAfter = instanceId) {
            projectService.duplicateStructureOccurrence(DuplicateStructureOccurrenceRequest(occurrenceRoot(), instanceId, occurrence.revision)).refreshed()
        }
    }

    private fun removeStructureOccurrence(instanceId: String) {
        val occurrence = state.value.project?.structure?.firstOrNull { it.instanceId == instanceId } ?: return
        mutateStructure("remove structure", instanceId) {
            projectService.removeStructureOccurrence(RemoveStructureOccurrenceRequest(occurrenceRoot(), instanceId, occurrence.revision)).refreshed()
        }
    }

    private fun moveStructureOccurrence(instanceId: String, earlier: Boolean) {
        val sections = state.value.project?.structure.orEmpty()
        val index = sections.indexOfFirst { it.instanceId == instanceId }
        val occurrence = sections.getOrNull(index) ?: return
        val target = index + if (earlier) -1 else 1
        if (target !in sections.indices) return
        val after = if (earlier) sections.getOrNull(target - 1)?.instanceId else sections[target].instanceId
        mutateStructure("reorder structure", instanceId) {
            projectService.moveStructureOccurrence(MoveStructureOccurrenceRequest(occurrenceRoot(), instanceId, after, occurrence.revision)).refreshed()
        }
    }

    private fun occurrenceRoot(): Path = requireNotNull(state.value.project).root

    private fun mutateStructure(action: String, selectedId: String, selectInsertedAfter: String? = null, operation: () -> ProjectSnapshot) {
        if (state.value.operation.isMutating) return
        val feedbackId = beginFeedback(OperationKind.PROJECT_HYDRATION, OperationPhase.LOCAL, "Saving song structure…")
        mutableState.update { it.copy(operation = WorkspaceOperation.SavingStructure, notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { operation() } }
                .onSuccess { snapshot ->
                    opened(snapshot, "Updated song structure", feedbackId, stale = state.value.downstreamArtifactsStale)
                    mutableState.update { current -> current.copy(selectedStructureOccurrenceId =
                        selectInsertedAfter?.let { source -> snapshot.structure.getOrNull(snapshot.structure.indexOfFirst { it.instanceId == source } + 1)?.instanceId }
                            ?: snapshot.structure.firstOrNull { it.instanceId == selectedId }?.instanceId
                            ?: snapshot.structure.firstOrNull()?.instanceId) }
                }
                .onFailure { fail(action, it.message ?: "Unable to update song structure.", sessionId = feedbackId) }
        }
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
        is WorkspaceRetry.AutomaticImport -> runAutomaticImport(action.command, action.draft)
        is WorkspaceRetry.Import -> {
            val draft = (state.value.dialog as? WorkspaceDialog.ImportPart) ?: WorkspaceDialog.ImportPart(
                audio = action.request.transcribe,
                source = action.request.source,
                id = action.request.id,
                role = action.request.role,
                detectedType = detectImportSourceKind(action.request.source),
                rightsClaim = action.request.sourceAttestation?.claim ?: SourceRightsClaim.NOT_ESTABLISHED,
                provenanceConfirmed = true
            )
            runAutomaticImport(
                ImportSongPart(action.request.root, action.request.id, action.request.source, action.request.name,
                    action.request.sectionType, action.request.sourceAttestation),
                draft
            )
        }
        is WorkspaceRetry.Analyze -> analyzePart(action.partId)
        is WorkspaceRetry.Transpose -> transposePart(action.partId)
        is WorkspaceRetry.Inspect -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = AudioPreparationUiState(partId = action.partId)) }
            inspectSelectedPart()
        }
        is WorkspaceRetry.Cleanup -> {
            mutableState.update { it.copy(selectedPartId = action.partId, audioPreparation = it.audioPreparation.copy(partId = action.partId, cleanupMode = action.mode)) }
            if (action.mode == InputCleanupMode.SAFE_CLEANUP) mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmSafeCleanup(action.partId)) }
            else runCleanup(action.partId, action.mode, confirmed = false)
        }
        is WorkspaceRetry.CleanMidi -> {
            mutableState.update { it.copy(selectedPartId = action.request.partId, midiQualityReview = MidiQualityReviewDraft(action.request.cleanup.profile)) }
            if (action.request.cleanup.profile == MidiCleanupProfile.TIGHTEN_TIMING) {
                mutableState.update { it.copy(dialog = WorkspaceDialog.ConfirmTightenTiming(action.request.partId)) }
            } else runCleanMidi(action.request)
        }
        is WorkspaceRetry.TechnicalCorrection -> {
            mutableState.update { it.copy(selectedPartId = action.request.partId) }
            createTechnicalCorrection()
        }
        is WorkspaceRetry.ApplyMidiFeel -> {
            mutableState.update { it.copy(selectedPartId = action.partId, pendingMidiFeel = action.input) }
            applyMidiFeelAndReanalyze()
        }
        is WorkspaceRetry.Enhancement -> {
            mutableState.update { it.copy(selectedPartId = action.partId) }
            selectEnhancement(action.intensity)
        }
        is WorkspaceRetry.CreateMidiAiFix -> runCreateMidiAiFix(action.request)
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

    private fun toggleArrangementRole(role: ArrangementRole) {
        if (state.value.operation.isMutating || role == ArrangementRole.MELODY) return
        mutableState.update { current ->
            val selected = current.arrangementDraft.roles
            current.copy(arrangementDraft = current.arrangementDraft.copy(roles = if (role in selected) selected - role else selected + role), arrangementDraftDirty = true)
        }
    }

    private fun toggleArrangementTrait(trait: SoundTrait) {
        if (state.value.operation.isMutating) return
        mutableState.update { current ->
            val draft = current.arrangementDraft
            val selected = when (trait) {
                SoundTrait.SOFT, SoundTrait.HARD, SoundTrait.BRUSHED -> draft.attackTraits
                SoundTrait.WARM, SoundTrait.DARK, SoundTrait.BRIGHT, SoundTrait.MUTED, SoundTrait.AIRY -> draft.toneTraits
                else -> draft.articulationTraits
            }
            val changed = if (trait in selected) selected - trait else selected + trait
            val updated = when (trait) {
                SoundTrait.SOFT, SoundTrait.HARD, SoundTrait.BRUSHED -> draft.copy(attackTraits = changed)
                SoundTrait.WARM, SoundTrait.DARK, SoundTrait.BRIGHT, SoundTrait.MUTED, SoundTrait.AIRY -> draft.copy(toneTraits = changed)
                else -> draft.copy(articulationTraits = changed)
            }
            current.copy(arrangementDraft = updated, arrangementDraftDirty = true)
        }
    }

    private fun pinArrangementInstrument(role: ArrangementRole, instrumentId: String?) {
        if (state.value.operation.isMutating || role !in state.value.arrangementDraft.roles) return
        val updated = state.value.arrangementDraft.pinnedInstrumentIds.toMutableMap()
        if (instrumentId == null) updated.remove(role) else updated[role] = instrumentId
        mutableState.update { current ->
            current.copy(arrangementDraft = current.arrangementDraft.copy(pinnedInstrumentIds = updated), arrangementDraftDirty = true, notification = null)
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

    private fun reviewCohesionBoundary(outgoingInstanceId: String, incomingInstanceId: String) {
        val project = state.value.project ?: return
        val cohesion = state.value.cohesion ?: return fail("review cohesion", "Generate a cohesion draft before reviewing its boundaries.")
        val boundary = cohesion.boundaries.firstOrNull { it.outgoingInstanceId == outgoingInstanceId && it.incomingInstanceId == incomingInstanceId }
            ?: return fail("review cohesion", "The selected cohesion boundary is no longer current. Regenerate cohesion.")
        if (boundary.reviewed || cohesion.approved || cohesion.stale || state.value.operation.isMutating) return
        mutableState.update { it.copy(operation = WorkspaceOperation.ReviewingCohesion(outgoingInstanceId, incomingInstanceId), notification = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { cohesionService.reviewBoundary(project.root, outgoingInstanceId, incomingInstanceId) } }
                .onSuccess { reviewed ->
                    val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                    val message = "Reviewed cohesion boundary $outgoingInstanceId → $incomingInstanceId."
                    mutableState.update { it.copy(project = refreshed ?: it.project, cohesion = reviewed, operation = WorkspaceOperation.Idle, notification = message) }
                }
                .onFailure { fail("review cohesion", it.message ?: "Unable to review cohesion boundary.") }
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
            else -> runGenerateArrangement(
                GenerateArrangementRequest(
                    root = project.root,
                    planner = state.value.arrangementDraft.planner,
                    roleSelections = state.value.arrangementDraft.roles.sortedBy { it.name }.map { role ->
                        ArrangementRoleSelection(
                            role, state.value.arrangementDraft.attackTraits, state.value.arrangementDraft.toneTraits,
                            state.value.arrangementDraft.articulationTraits,
                            pinnedInstrumentId = state.value.arrangementDraft.pinnedInstrumentIds[role]
                        )
                    }
                )
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
                // Arrangement invalidates Cohesion in the project workflow.
                // Refresh the canonical snapshot before rendering controls so
                // the next required action is visible immediately.
                val refreshedProject = runCatching { projectService.open(request.root) }.getOrNull()
                val message = when (request.planner) {
                    ArrangementPlannerKind.QWEN -> "Qwen arrangement draft is ready for review and explicit approval."
                    ArrangementPlannerKind.DETERMINISTIC -> "Approved deterministic arrangement generated."
                }
                mutableState.update { it.copy(project = refreshedProject ?: it.project, arrangement = arrangement, selectedArrangementSection = arrangement.sections.firstOrNull()?.index, arrangementDraftDirty = false, operation = WorkspaceOperation.Idle, notification = message, operationFeedback = feedbackTracker.complete(feedbackId, message, if (arrangement.approvalRequired) OperationSeverity.WARNING else OperationSeverity.SUCCESS) ?: it.operationFeedback, retry = null) }
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
            is WorkspaceOperation.CleaningMidi -> operation.progress
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
        val readiness = project.readiness
        if (!readiness.cohesionReady) {
            val action = if (readiness.cohesionApprovalRequired) "Review and approve Cohesion." else "Generate and approve Cohesion."
            return fail("build song", "Build Song requires current approved arrangement-aware Cohesion. $action")
        }
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

    private fun generateHumanization() {
        val project = state.value.project ?: return fail("humanization", "Open a project before creating a variation.")
        mutableState.update { it.copy(operation = WorkspaceOperation.Humanizing, notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { humanizationService.generate(GenerateHumanizationRequest(project.root)) } }
                .onSuccess { snapshot ->
                    val refreshed = withContext(ioDispatcher) { projectService.open(project.root) }
                    mutableState.update { current -> current.copy(project = refreshed, humanization = snapshot, operation = WorkspaceOperation.Idle,
                        notification = "Humanization variation ${snapshot.seed} selected; ${snapshot.changedNotes} exact edits recorded.") }
                }
                .onFailure { fail("humanization", it.message ?: "Unable to create humanization evidence.") }
        }
    }

    private fun bypassHumanization() {
        val project = state.value.project ?: return fail("humanization", "Open a project before selecting bypass.")
        runCatching { humanizationService.selectBypass(project.root) }
            .onSuccess { snapshot ->
                val refreshed = runCatching { projectService.open(project.root) }.getOrNull()
                mutableState.update { current -> current.copy(project = refreshed ?: current.project, humanization = snapshot,
                    notification = "Humanization bypass selected; rendering will use cohesive MIDI input.") }
            }
            .onFailure { fail("humanization", it.message ?: "Unable to select humanization bypass.") }
    }

    private fun exportCommercialProvenance() {
        val project = state.value.project ?: return fail("commercial provenance", "Open a project before creating commercial evidence.")
        if (!project.readiness.releaseAvailable || state.value.operation.isMutating) return fail("commercial provenance", "Build a current master and release metadata before creating commercial evidence.")
        mutableState.update { it.copy(operation = WorkspaceOperation.ExportingCommercialProvenance, notification = null, retry = null) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { commercialProvenanceService.export(project.root) } }
                .onSuccess { result ->
                    val evidence = CommercialEvidenceUiState(
                        commercialReady = result.readiness.ready,
                        unresolvedActions = result.readiness.reasons,
                        releaseId = checkNotNull(result.releaseId),
                        requiredAttribution = result.readiness.attribution,
                        reportReference = project.root.relativize(checkNotNull(result.report)).toString().replace('\\', '/'),
                        manifestReference = project.root.relativize(checkNotNull(result.manifest)).toString().replace('\\', '/')
                    )
                    val message = if (result.readiness.ready) "Commercial evidence is hash-verified. Review the report and YouTube checklist before release."
                    else "Commercial evidence was saved with unresolved actions; it is not Commercial-ready."
                    mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle, notification = message, commercialEvidence = evidence) }
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

    private fun exportSong(commercial: Boolean = false) {
        val project = state.value.project ?: return fail("export song", "Open a project before exporting.")
        val draft = state.value.export.draft
        val inspection = state.value.export.inspection
        if (state.value.operation.isMutating) return
        if (inspection?.summary == null || draft.format !in inspection.supportedFormats) return fail("export song", inspection?.blockedReason ?: "Build a current master and release metadata first.")
        val destination = draft.destination ?: return fail("export song", "Choose the project output folder before exporting.")
        val commercialReleaseId = if (commercial) state.value.commercialEvidence?.takeIf { it.commercialReady }?.releaseId
            ?: return fail("commercial export", "Create commercial-ready evidence and resolve its missing attribution before exporting commercially.") else null
        val feedbackId = beginFeedback(OperationKind.EXPORT, OperationPhase.VALIDATING, "Validating release export…")
        mutableState.update { it.copy(operation = WorkspaceOperation.ExportingRelease, notification = null, retry = null, operationFeedback = feedbackTracker.current) }
        scope.launch {
            runCatching { withContext(ioDispatcher) { releaseExportService.export(ReleaseExportRequest(project.root, draft.format, draft.filename, destination, commercialReleaseId = commercialReleaseId)) } }
                .onSuccess { result ->
                    val credits = result.credits
                    val creditsPath = credits?.relativePath
                    mutableState.update { current -> current.copy(operation = WorkspaceOperation.Idle,
                        notification = if (creditsPath == null) "Exported ${result.output.fileName}." else "Exported ${result.output.fileName} with ${creditsPath.substringAfterLast('/')}.",
                        commercialEvidence = result.credits?.let { artifact -> current.commercialEvidence?.copy(creditsReference = artifact.relativePath) } ?: current.commercialEvidence,
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
                midiAiFix = if (resetWorkspace) null else current.midiAiFix,
                humanization = if (resetWorkspace) null else current.humanization,
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
                projectSetup = if (resetWorkspace) ProjectSetupUiState.Empty else current.projectSetup,
                workspaceSection = if (resetWorkspace && (!project.readiness.compositionSettingsReady || project.migration.requiresMigration)) WorkspaceSection.SETUP else if (resetWorkspace) WorkspaceSection.OVERVIEW else current.workspaceSection
            )
        }
        loadProjectSetup(project)
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
            val aiFix = project.parts.firstOrNull { it.preparation.midiAiFix.draftAvailable || it.preparation.midiAiFix.approvedAvailable }
                ?.let { part -> runCatching { midiAiFixService.load(project.root, part.id) } }
            val humanization = runCatching { humanizationService.load(project.root) }
            listOf(mix, arrangement, cohesion, aiFix, humanization)
        }
        val warnings = buildList {
            hydration[0]?.exceptionOrNull()?.message?.let { add("mix settings could not be loaded: $it") }
            if (project.readiness.arrangementAvailable || project.readiness.songPlanAvailable) {
                hydration[1]?.exceptionOrNull()?.message?.let { add("arrangement artifacts could not be loaded: $it") }
            }
            if (project.readiness.cohesionReady || project.readiness.cohesionApprovalRequired) {
                hydration[2]?.exceptionOrNull()?.message?.let { add("cohesion artifacts could not be loaded: $it") }
            }
        }
        mutableState.update { current ->
            if (current.project?.root != project.root) current
            else {
                val arrangement = hydration[1]?.getOrNull() as? ArrangementSnapshot
                current.copy(
                    mix = hydration[0]?.getOrNull() as? MixSnapshot,
                    arrangement = arrangement,
                    cohesion = hydration[2]?.getOrNull() as? CohesionSnapshot,
                    midiAiFix = hydration[3]?.getOrNull() as? MidiAiFixSnapshot,
                    humanization = hydration[4]?.getOrNull() as? HumanizationSnapshot,
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
