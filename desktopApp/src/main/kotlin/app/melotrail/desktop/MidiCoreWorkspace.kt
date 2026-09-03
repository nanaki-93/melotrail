package app.melotrail.desktop

import app.melotrail.application.AcceptMidiCoreCandidate
import app.melotrail.application.CompareMidiCoreCandidates
import app.melotrail.application.ConfirmMidiCoreAuthority
import app.melotrail.application.CreateMidiCoreProject
import app.melotrail.application.GenerateMidiCoreCandidate
import app.melotrail.application.GenerateMidiCoreArrangementDraft
import app.melotrail.application.ImportMidiCoreSource
import app.melotrail.application.ListMidiCoreCandidates
import app.melotrail.application.LockMidiCoreCandidate
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreCandidateGenerationResult
import app.melotrail.application.MidiCoreArrangementDraftGeneration
import app.melotrail.application.MidiCoreArrangementDraftGenerationResult
import app.melotrail.application.MidiCoreArrangementDraftProblem
import app.melotrail.application.MidiCoreArrangementStylePreview
import app.melotrail.application.MidiCoreArrangementStylePreviewResult
import app.melotrail.application.PrepareMidiCoreArrangementStylePreview
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreCandidateReviewResult
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.MidiCoreMidiPackageExportResult
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreProjectLifecycleResult
import app.melotrail.application.MidiCoreProjectSession
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreSourceImportResult
import app.melotrail.application.MidiCoreSourceAuditionResult
import app.melotrail.application.MidiCoreReviewAudition
import app.melotrail.application.MidiCoreReviewAuditionResult
import app.melotrail.application.PrepareMidiCoreAcceptedArrangementAudition
import app.melotrail.application.PrepareMidiCoreAcceptedOccurrenceAudition
import app.melotrail.application.PrepareMidiCoreAcceptedRoleAudition
import app.melotrail.application.PrepareMidiCoreCandidateAudition
import app.melotrail.application.PrepareMidiCoreOccurrenceAudition
import app.melotrail.application.PrepareMidiCoreSourceAudition
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.application.RejectMidiCoreCandidate
import app.melotrail.application.RegenerateMidiCoreCandidate
import app.melotrail.application.ReplaceMidiCoreHarmony
import app.melotrail.application.ReplaceMidiCoreStructure
import app.melotrail.application.RestoreMidiCoreCandidate
import app.melotrail.application.UnlockMidiCoreCandidate
import app.melotrail.application.MidiCoreCandidateLifecycleResult
import app.melotrail.application.MidiCoreCandidateProblem
import app.melotrail.application.MidiCoreAuthorityProblem
import app.melotrail.application.MidiCorePackageExportProblem
import app.melotrail.application.MidiCoreProjectProblem
import app.melotrail.application.MidiCoreSourceImportProblem
import app.melotrail.application.MidiCoreStructureTimelineProblem
import app.melotrail.application.MidiCoreAuthoritativeHarmonyProblem
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionPort
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionState
import app.melotrail.arrangement.core.MidiCoreSectionPolicy
import app.melotrail.arrangement.core.MidiCoreInvalidationPreview
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiImportValidationResult
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Target-only dispatchers used by the focused workspace state machine. */
data class MidiCoreWorkspaceDispatchers(
    val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
)

/** Application boundaries used by the target workspace reducer and its tests. */
interface MidiCoreWorkspaceUseCases {
    val audition: MidiAuditionPort

    fun create(request: CreateMidiCoreProject): MidiCoreProjectLifecycleResult
    fun open(root: Path): MidiCoreProjectLifecycleResult
    fun readCurrent(root: Path): MidiCoreProjectSession?
    fun close(session: app.melotrail.application.MidiCoreProjectSession): app.melotrail.application.MidiCoreProjectCloseResult
    fun importSource(request: ImportMidiCoreSource): MidiCoreSourceImportResult
    fun prepareSourceAudition(request: PrepareMidiCoreSourceAudition): MidiCoreSourceAuditionResult
    fun prepareOccurrenceAudition(request: PrepareMidiCoreOccurrenceAudition): MidiCoreSourceAuditionResult
    fun prepareCandidateAudition(request: PrepareMidiCoreCandidateAudition): MidiCoreReviewAuditionResult
    fun prepareAcceptedRoleAudition(request: PrepareMidiCoreAcceptedRoleAudition): MidiCoreReviewAuditionResult
    fun prepareAcceptedOccurrenceAudition(request: PrepareMidiCoreAcceptedOccurrenceAudition): MidiCoreReviewAuditionResult
    fun prepareAcceptedArrangementAudition(request: PrepareMidiCoreAcceptedArrangementAudition): MidiCoreReviewAuditionResult
    suspend fun previewArrangementStyle(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult
    fun confirmAuthority(request: ConfirmMidiCoreAuthority): app.melotrail.application.MidiCoreAuthorityResult
    fun replaceStructure(request: ReplaceMidiCoreStructure): app.melotrail.application.MidiCoreStructureTimelineResult
    fun replaceHarmony(request: ReplaceMidiCoreHarmony): app.melotrail.application.MidiCoreAuthoritativeHarmonyResult
    fun listCandidates(request: ListMidiCoreCandidates): MidiCoreCandidateReviewResult
    fun compareCandidates(request: CompareMidiCoreCandidates): MidiCoreCandidateReviewResult
    fun acceptCandidate(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult
    fun rejectCandidate(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult
    fun lockCandidate(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult
    fun unlockCandidate(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult
    fun restoreCandidate(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult
    suspend fun generateCandidate(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult
    suspend fun regenerateCandidate(request: RegenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult
    suspend fun generateArrangementDraft(request: GenerateMidiCoreArrangementDraft): MidiCoreArrangementDraftGenerationResult
    fun export(request: app.melotrail.application.ExportMidiCorePackage): MidiCoreMidiPackageExportResult
}

/** The small project-facing use-case adapter consumed by the target desktop state. */
class DefaultMidiCoreWorkspaceUseCases(
    private val project: MidiCoreProjectLifecycle,
    private val sourceImport: MidiCoreSourceImport,
    private val authority: MidiCoreMusicalAuthority,
    private val structure: MidiCoreStructureTimeline,
    private val harmony: MidiCoreAuthoritativeHarmony,
    private val generation: MidiCoreCandidateGeneration,
    private val review: MidiCoreCandidateReview,
    private val exporter: MidiCoreMidiPackageExporter,
    override val audition: MidiAuditionPort,
    private val sourceAudition: app.melotrail.application.MidiCoreSourceAudition = app.melotrail.application.MidiCoreSourceAudition(),
    private val reviewAudition: MidiCoreReviewAudition = MidiCoreReviewAudition(review),
    private val stylePreview: MidiCoreArrangementStylePreview = MidiCoreArrangementStylePreview(),
    private val draftGeneration: MidiCoreArrangementDraftGeneration = MidiCoreArrangementDraftGeneration(),
) : MidiCoreWorkspaceUseCases {
    override fun create(request: CreateMidiCoreProject): MidiCoreProjectLifecycleResult = project.create(request)

    override fun open(root: Path): MidiCoreProjectLifecycleResult = project.open(root)

    override fun readCurrent(root: Path): MidiCoreProjectSession? = when (val result = project.open(root)) {
        is MidiCoreProjectLifecycleResult.Opened -> result.session
        is MidiCoreProjectLifecycleResult.Rejected -> null
    }

    override fun close(session: app.melotrail.application.MidiCoreProjectSession) = project.close(session)

    override fun importSource(request: ImportMidiCoreSource): MidiCoreSourceImportResult = sourceImport.import(request)


    override fun prepareSourceAudition(request: PrepareMidiCoreSourceAudition): MidiCoreSourceAuditionResult = sourceAudition.prepare(request)

    override fun prepareOccurrenceAudition(request: PrepareMidiCoreOccurrenceAudition): MidiCoreSourceAuditionResult = sourceAudition.prepareOccurrence(request)

    override fun prepareCandidateAudition(request: PrepareMidiCoreCandidateAudition): MidiCoreReviewAuditionResult = reviewAudition.candidate(request)

    override fun prepareAcceptedRoleAudition(request: PrepareMidiCoreAcceptedRoleAudition): MidiCoreReviewAuditionResult = reviewAudition.role(request)

    override fun prepareAcceptedOccurrenceAudition(request: PrepareMidiCoreAcceptedOccurrenceAudition): MidiCoreReviewAuditionResult = reviewAudition.occurrence(request)

    override fun prepareAcceptedArrangementAudition(request: PrepareMidiCoreAcceptedArrangementAudition): MidiCoreReviewAuditionResult = reviewAudition.acceptedArrangement(request)

    override suspend fun previewArrangementStyle(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult = stylePreview.prepare(request)

    override fun confirmAuthority(request: ConfirmMidiCoreAuthority) = authority.confirm(request)

    override fun replaceStructure(request: ReplaceMidiCoreStructure) = structure.replace(request)

    override fun replaceHarmony(request: ReplaceMidiCoreHarmony) = harmony.replace(request)

    override fun listCandidates(request: ListMidiCoreCandidates): MidiCoreCandidateReviewResult = review.list(request)

    override fun compareCandidates(request: CompareMidiCoreCandidates): MidiCoreCandidateReviewResult = review.compare(request)

    override fun acceptCandidate(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult = review.accept(request)

    override fun rejectCandidate(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult = review.reject(request)

    override fun lockCandidate(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = review.lock(request)

    override fun unlockCandidate(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = review.unlock(request)

    override fun restoreCandidate(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult = review.restore(request)

    override suspend fun generateCandidate(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult = generation.generate(request)

    override suspend fun regenerateCandidate(request: RegenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult = review.regenerate(request)

    override suspend fun generateArrangementDraft(request: GenerateMidiCoreArrangementDraft): MidiCoreArrangementDraftGenerationResult =
        draftGeneration.generate(request)

    override fun export(request: app.melotrail.application.ExportMidiCorePackage): MidiCoreMidiPackageExportResult = exporter.export(request)

}

/** Immutable progress for one target workspace operation. */
data class MidiCoreWorkspaceOperationProgress(
    val completed: Int,
    val total: Int,
) {
    init {
        require(total > 0) { "Operation total must be positive" }
        require(completed in 0..total) { "Completed operation work must be within the known total" }
    }
}

enum class MidiCoreWorkspaceOperationKind {
    PROJECT,
    IMPORT,
    AUTHORITY,
    STRUCTURE,
    HARMONY,
    CANDIDATE_REVIEW,
    CANDIDATE_GENERATION,
    DRAFT_GENERATION,
    EXPORT,
    AUDITION,
}

enum class MidiCoreWorkspaceOperationPhase { IDLE, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }

enum class MidiCoreWorkspaceOperationOutcome { SUCCESS, FAILURE, CANCELLED }

/** UI-neutral operation feedback for the target workspace; it has no worker/audio phases. */
data class MidiCoreWorkspaceOperation(
    val id: Long = 0L,
    val kind: MidiCoreWorkspaceOperationKind? = null,
    val phase: MidiCoreWorkspaceOperationPhase = MidiCoreWorkspaceOperationPhase.IDLE,
    val message: String = "Ready.",
    val progress: MidiCoreWorkspaceOperationProgress? = null,
    val cancellableAtBoundary: Boolean = false,
    val retry: MidiCoreWorkspaceIntent? = null,
    val outcome: MidiCoreWorkspaceOperationOutcome? = null,
) {
    init {
        require(id >= 0L) { "Operation ID must not be negative" }
        require(phase !in setOf(MidiCoreWorkspaceOperationPhase.SUCCEEDED, MidiCoreWorkspaceOperationPhase.FAILED, MidiCoreWorkspaceOperationPhase.CANCELLED) || outcome != null) {
            "Finished target operations require an outcome"
        }
        require(phase != MidiCoreWorkspaceOperationPhase.RUNNING || kind != null) {
            "Running target operations require a kind"
        }
        require(phase !in setOf(MidiCoreWorkspaceOperationPhase.SUCCEEDED, MidiCoreWorkspaceOperationPhase.FAILED, MidiCoreWorkspaceOperationPhase.CANCELLED) || !cancellableAtBoundary) {
            "Finished target operations cannot be cancellable"
        }
    }

    val active: Boolean get() = phase == MidiCoreWorkspaceOperationPhase.RUNNING || phase == MidiCoreWorkspaceOperationPhase.CANCELLING

    companion object {
        /** Return the initial idle operation state. */
        fun idle() = MidiCoreWorkspaceOperation()
    }
}

enum class MidiCoreSourceStatus { EMPTY, IMPORTED, REJECTED }

/** Persisted and in-memory MIDI import findings projected for the target UI. */
data class MidiCoreSourceUiState(
    val status: MidiCoreSourceStatus = MidiCoreSourceStatus.EMPTY,
    val originalFilename: String? = null,
    val sha256: String? = null,
    val format: Int? = null,
    val ppq: Int? = null,
    val sourceEndTick: Long? = null,
    val trackSummaries: List<MidiTrackSummary> = emptyList(),
    val validation: MidiImportValidationResult? = null,
    val findings: List<MidiFinding> = emptyList(),
    val reportAvailable: Boolean = false,
)

/** Automatically protected melody identity and its last typed validation result. */
data class MidiCoreMelodyUiState(
    val selected: SelectedMelodyTrack? = null,
    val validation: MidiImportValidationResult? = null,
)

/** Typed authority draft kept in memory until the user explicitly confirms it. */
data class MidiCoreAuthorityDraft(
    val key: ProjectKey = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
    val tempo: ProjectTempo = ProjectTempo(500_000),
    val meter: ProjectMeter = ProjectMeter(4, 2),
) {
    /** Convert the draft to the immutable project authority request. */
    fun toRequest(
        session: app.melotrail.application.MidiCoreProjectSession,
    ): ConfirmMidiCoreAuthority = ConfirmMidiCoreAuthority(session, key, tempo, meter)

    companion object {
        /** Use fixed, transparent MIDI defaults until a musician edits the draft. */
        fun defaults() = MidiCoreAuthorityDraft()
    }
}

/** Authority state separates persisted confirmation from an unsaved UI draft. */
data class MidiCoreAuthorityUiState(
    val confirmed: ProjectAuthority? = null,
    val draft: MidiCoreAuthorityDraft = MidiCoreAuthorityDraft.defaults(),
    val draftDirty: Boolean = false,
    val suggestions: app.melotrail.application.MidiCoreAuthoritySuggestions? = null,
    val lastInvalidation: MidiCoreInvalidationPreview? = null,
)

/** Candidate review state contains inspectable evidence, never direct file reads. */
data class MidiCoreCandidateReviewUiState(
    val role: CandidateRole? = null,
    val occurrenceId: String? = null,
    val candidates: List<app.melotrail.application.MidiCoreCandidateReviewItem> = emptyList(),
    val comparison: MidiCoreCandidateComparison? = null,
    val selectedCandidateId: String? = null,
)

/** Candidate comparison projected without retaining a project session in Compose state. */
data class MidiCoreCandidateComparison(
    val first: app.melotrail.application.MidiCoreCandidateReviewItem,
    val second: app.melotrail.application.MidiCoreCandidateReviewItem,
    val differences: List<app.melotrail.application.MidiCoreCandidateDifference>,
)

/** Last successful target export and its current typed blocker. */
data class MidiCoreExportUiState(
    val latest: app.melotrail.application.MidiCoreExportedPackage? = null,
    val latestSnapshot: app.melotrail.project.MidiCoreExportSnapshot? = null,
)

enum class MidiCoreWorkspaceBlockerCode {
    PROJECT_REQUIRED,
    SOURCE_REQUIRED,
    MELODY_REQUIRED,
    AUTHORITY_REQUIRED,
    STRUCTURE_REQUIRED,
    HARMONY_REQUIRED,
    CANDIDATE_REVIEW_REQUIRED,
    EXPORT_NOT_READY,
    REVISION_CONFLICT,
    STALE_COMPLETION,
    OPERATION_BUSY,
    APPLICATION_FAILURE,
}

/** An actionable explanation shown next to the target UI action it blocks. */
data class MidiCoreWorkspaceBlocker(
    val code: MidiCoreWorkspaceBlockerCode,
    val message: String,
    val nextAction: String,
    val sourceCode: String? = null,
    val action: MidiCoreWorkspaceIntent? = null,
    val occurrenceId: String? = null,
    val role: CandidateRole? = null,
)

/** Small confirmation dialog state for an unsaved authority draft. */
sealed interface MidiCoreWorkspaceDialog {
    data class ConfirmDiscardAuthorityDraft(val pending: MidiCoreWorkspaceIntent) : MidiCoreWorkspaceDialog
}

/** Complete target state exposed to Compose; no legacy audio-production state is present. */
data class MidiCoreWorkspaceState(
    val project: MidiCoreProject? = null,
    val projectRoot: Path? = null,
    val source: MidiCoreSourceUiState = MidiCoreSourceUiState(),
    val melody: MidiCoreMelodyUiState = MidiCoreMelodyUiState(),
    val authority: MidiCoreAuthorityUiState = MidiCoreAuthorityUiState(),
    val review: MidiCoreCandidateReviewUiState = MidiCoreCandidateReviewUiState(),
    val stylePreview: MidiCoreArrangementStyleUiState = MidiCoreArrangementStyleUiState(),
    val arrangement: MidiCoreArrangementUiState = MidiCoreArrangementUiState(),
    val audition: MidiAuditionState = MidiAuditionState(),
    val export: MidiCoreExportUiState = MidiCoreExportUiState(),
    val operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
    val dialog: MidiCoreWorkspaceDialog? = null,
    val blockers: List<MidiCoreWorkspaceBlocker> = emptyList(),
    val notification: String? = null,
) {
    /** The revision admitted by the currently hydrated project, or null before opening one. */
    val projectRevision: Long? get() = project?.revision

    /** Whether a target operation is currently changing or validating project state. */
    val busy: Boolean get() = operation.active
}

/** Ephemeral Arrange selection and cache evidence; it is intentionally absent from project persistence. */
data class MidiCoreArrangementStyleUiState(
    val selectedStyleId: String? = null,
    val occurrenceId: String? = null,
    val cacheStatus: app.melotrail.application.MidiCoreArrangementStylePreviewCacheStatus? = null,
    val key: app.melotrail.application.MidiCoreArrangementStylePreviewKey? = null,
)

/** Ephemeral selection and retry identity for the whole-song arrangement workspace. */
data class MidiCoreArrangementUiState(
    val selectedOccurrenceId: String? = null,
    val incompleteDraftId: String? = null,
    val incompleteDraftStyleId: String? = null,
    val rootSeed: Long = 1L,
)

/** All target workspace mutations are represented as explicit intents. */
sealed interface MidiCoreWorkspaceIntent {
    data class CreateProject(
        val root: Path,
        val name: String,
        val id: String? = null,
        val applicationVersion: String? = null,
    ) : MidiCoreWorkspaceIntent

    data class OpenProject(val root: Path) : MidiCoreWorkspaceIntent
    data object OpenLastProject : MidiCoreWorkspaceIntent
    data object ReloadProject : MidiCoreWorkspaceIntent
    data object CloseProject : MidiCoreWorkspaceIntent
    data class ImportSource(val source: Path) : MidiCoreWorkspaceIntent
    data class UpdateAuthorityDraft(val draft: MidiCoreAuthorityDraft) : MidiCoreWorkspaceIntent
    data object ConfirmAuthority : MidiCoreWorkspaceIntent
    data class ReplaceStructure(
        val definitions: List<app.melotrail.project.ProjectSectionDefinition>,
        val occurrences: List<app.melotrail.structure.MidiCoreBarOccurrencePlacement>,
    ) : MidiCoreWorkspaceIntent
    data class ReplaceHarmony(val events: List<AuthoritativeChordEvent>) : MidiCoreWorkspaceIntent
    data class SelectReviewScope(val role: CandidateRole, val occurrenceId: String) : MidiCoreWorkspaceIntent
    data class LoadCandidates(val role: CandidateRole, val occurrenceId: String) : MidiCoreWorkspaceIntent
    data class CompareCandidates(val firstCandidateId: String, val secondCandidateId: String) : MidiCoreWorkspaceIntent
    data class GenerateCandidate(
        val role: CandidateRole,
        val occurrenceId: String,
        val performanceProfileId: String,
        val patternId: String,
        val generator: MidiCoreGeneratorInput,
        val sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(),
    ) : MidiCoreWorkspaceIntent
    data class RegenerateCandidate(
        val role: CandidateRole,
        val occurrenceId: String,
        val performanceProfileId: String,
        val patternId: String,
        val generator: MidiCoreGeneratorInput,
        val sectionPolicy: MidiCoreSectionPolicy = MidiCoreSectionPolicy(),
    ) : MidiCoreWorkspaceIntent
    data class AcceptCandidate(val candidateId: String, val locked: Boolean = false) : MidiCoreWorkspaceIntent
    data class RejectCandidate(val candidateId: String, val reason: String) : MidiCoreWorkspaceIntent
    data class LockCandidate(val candidateId: String) : MidiCoreWorkspaceIntent
    data class UnlockCandidate(val candidateId: String) : MidiCoreWorkspaceIntent
    data class RestoreCandidate(val candidateId: String, val role: CandidateRole, val occurrenceId: String, val locked: Boolean = false) : MidiCoreWorkspaceIntent
    data object ExportPackage : MidiCoreWorkspaceIntent
    data class SelectAudition(val plan: MidiAuditionPlaybackPlan) : MidiCoreWorkspaceIntent
    data object PlaySourceMelody : MidiCoreWorkspaceIntent
    data class PlayOccurrence(val occurrenceId: String) : MidiCoreWorkspaceIntent
    data class PlayCandidate(val candidateId: String, val role: CandidateRole, val occurrenceId: String) : MidiCoreWorkspaceIntent
    data class PlayAcceptedRole(val role: CandidateRole) : MidiCoreWorkspaceIntent
    data class PlayAcceptedOccurrence(val occurrenceId: String) : MidiCoreWorkspaceIntent
    data object PlayAcceptedArrangement : MidiCoreWorkspaceIntent
    data class PreviewArrangementStyle(
        val styleId: String,
        val occurrenceId: String,
        val seed: Long = PrepareMidiCoreArrangementStylePreview.DEFAULT_PREVIEW_SEED,
    ) : MidiCoreWorkspaceIntent
    data class SelectArrangementOccurrence(val occurrenceId: String) : MidiCoreWorkspaceIntent
    data class CreateArrangementDraft(
        val styleId: String,
        val rootSeed: Long = 1L,
        val draftId: String? = null,
    ) : MidiCoreWorkspaceIntent
    data class RegenerateArrangementSection(
        val occurrenceId: String,
        val styleId: String,
        val rootSeed: Long = 1L,
    ) : MidiCoreWorkspaceIntent
    data class PlayAudition(val plan: MidiAuditionPlaybackPlan? = null) : MidiCoreWorkspaceIntent
    data object PauseAudition : MidiCoreWorkspaceIntent
    data object StopAudition : MidiCoreWorkspaceIntent
    data class SeekAudition(val tick: Long) : MidiCoreWorkspaceIntent
    data class SetAuditionLoop(val loop: MidiAuditionLoop?) : MidiCoreWorkspaceIntent
    data class MuteAuditionRole(val role: app.melotrail.midi.domain.MidiExportRole, val muted: Boolean) : MidiCoreWorkspaceIntent
    data class SoloAuditionRole(val role: app.melotrail.midi.domain.MidiExportRole, val solo: Boolean) : MidiCoreWorkspaceIntent
    data class SelectAuditionOutputDevice(val outputDeviceId: String?) : MidiCoreWorkspaceIntent
    data object CancelOperation : MidiCoreWorkspaceIntent
    data object Retry : MidiCoreWorkspaceIntent
    data object DismissDialog : MidiCoreWorkspaceIntent
    data object ConfirmDiscardAuthorityDraft : MidiCoreWorkspaceIntent
}

/**
 * Focused target ViewModel. Blocking use cases run on the injected I/O
 * dispatcher and every result is admitted by operation ID, project revision,
 * and authority hash before it can update visible state.
 */
class MidiCoreWorkspaceViewModel(
    private val useCases: MidiCoreWorkspaceUseCases,
    private val preferences: MidiCoreDesktopPreferences = NoOpMidiCoreDesktopPreferences,
    private val logger: DesktopOperationLogger = NoOpDesktopOperationLogger,
    dispatchers: MidiCoreWorkspaceDispatchers = MidiCoreWorkspaceDispatchers(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.ui)
    private val _state = MutableStateFlow(MidiCoreWorkspaceState())
    private val dispatchers = dispatchers
    private var session: app.melotrail.application.MidiCoreProjectSession? = null
    private var nextOperationId = 0L
    private var activeJob: Job? = null
    private var activeCancellation: AtomicBoolean? = null
    private var closed = false

    /** Immutable state stream consumed by the focused Compose destinations. */
    val state: StateFlow<MidiCoreWorkspaceState> = _state.asStateFlow()

    /** Route one user intent through the target application boundaries. */
    fun accept(intent: MidiCoreWorkspaceIntent) {
        if (closed) return
        when (intent) {
            is MidiCoreWorkspaceIntent.CreateProject -> create(intent)
            is MidiCoreWorkspaceIntent.OpenProject -> open(intent.root, intent)
            MidiCoreWorkspaceIntent.OpenLastProject -> openLast()
            MidiCoreWorkspaceIntent.ReloadProject -> reload()
            MidiCoreWorkspaceIntent.CloseProject -> closeProject()
            is MidiCoreWorkspaceIntent.ImportSource -> importSource(intent)
            is MidiCoreWorkspaceIntent.UpdateAuthorityDraft -> updateAuthorityDraft(intent.draft)
            MidiCoreWorkspaceIntent.ConfirmAuthority -> confirmAuthority()
            is MidiCoreWorkspaceIntent.ReplaceStructure -> replaceStructure(intent)
            is MidiCoreWorkspaceIntent.ReplaceHarmony -> replaceHarmony(intent)
            is MidiCoreWorkspaceIntent.SelectReviewScope -> selectReviewScope(intent)
            is MidiCoreWorkspaceIntent.LoadCandidates -> loadCandidates(intent)
            is MidiCoreWorkspaceIntent.CompareCandidates -> compareCandidates(intent)
            is MidiCoreWorkspaceIntent.GenerateCandidate -> generateCandidate(intent, regenerate = false)
            is MidiCoreWorkspaceIntent.RegenerateCandidate -> generateCandidate(intent, regenerate = true)
            is MidiCoreWorkspaceIntent.AcceptCandidate -> acceptCandidate(intent)
            is MidiCoreWorkspaceIntent.RejectCandidate -> rejectCandidate(intent)
            is MidiCoreWorkspaceIntent.LockCandidate -> lockCandidate(intent)
            is MidiCoreWorkspaceIntent.UnlockCandidate -> unlockCandidate(intent)
            is MidiCoreWorkspaceIntent.RestoreCandidate -> restoreCandidate(intent)
            MidiCoreWorkspaceIntent.ExportPackage -> exportPackage()
            MidiCoreWorkspaceIntent.PlaySourceMelody -> playSourceMelody()
            is MidiCoreWorkspaceIntent.PlayOccurrence -> playOccurrence(intent)
            is MidiCoreWorkspaceIntent.PlayCandidate -> playReviewAudition(intent) { current ->
                useCases.prepareCandidateAudition(PrepareMidiCoreCandidateAudition(current, intent.candidateId, intent.role, intent.occurrenceId))
            }
            is MidiCoreWorkspaceIntent.PlayAcceptedRole -> playReviewAudition(intent) { current ->
                useCases.prepareAcceptedRoleAudition(PrepareMidiCoreAcceptedRoleAudition(current, intent.role))
            }
            is MidiCoreWorkspaceIntent.PlayAcceptedOccurrence -> playReviewAudition(intent) { current ->
                useCases.prepareAcceptedOccurrenceAudition(PrepareMidiCoreAcceptedOccurrenceAudition(current, intent.occurrenceId))
            }
            MidiCoreWorkspaceIntent.PlayAcceptedArrangement -> playReviewAudition(intent) { current ->
                useCases.prepareAcceptedArrangementAudition(PrepareMidiCoreAcceptedArrangementAudition(current))
            }
            is MidiCoreWorkspaceIntent.PreviewArrangementStyle -> previewArrangementStyle(intent)
            is MidiCoreWorkspaceIntent.SelectArrangementOccurrence -> selectArrangementOccurrence(intent)
            is MidiCoreWorkspaceIntent.CreateArrangementDraft -> generateArrangementDraft(intent)
            is MidiCoreWorkspaceIntent.RegenerateArrangementSection -> regenerateArrangementSection(intent)
            is MidiCoreWorkspaceIntent.SelectAudition -> audition { useCases.audition.selectScope(intent.plan) }
            is MidiCoreWorkspaceIntent.PlayAudition -> audition {
                intent.plan?.let { useCases.audition.play(it) } ?: useCases.audition.play()
            }
            MidiCoreWorkspaceIntent.PauseAudition -> audition { useCases.audition.pause() }
            MidiCoreWorkspaceIntent.StopAudition -> audition { useCases.audition.stop() }
            is MidiCoreWorkspaceIntent.SeekAudition -> audition { useCases.audition.seek(intent.tick) }
            is MidiCoreWorkspaceIntent.SetAuditionLoop -> audition { useCases.audition.setLoop(intent.loop) }
            is MidiCoreWorkspaceIntent.MuteAuditionRole -> audition { useCases.audition.setMutedRole(intent.role, intent.muted) }
            is MidiCoreWorkspaceIntent.SoloAuditionRole -> audition { useCases.audition.setSoloRole(intent.role, intent.solo) }
            is MidiCoreWorkspaceIntent.SelectAuditionOutputDevice -> audition { useCases.audition.selectOutputDevice(intent.outputDeviceId) }
            MidiCoreWorkspaceIntent.CancelOperation -> cancelOperation()
            MidiCoreWorkspaceIntent.Retry -> retry()
            MidiCoreWorkspaceIntent.DismissDialog -> _state.value = _state.value.copy(dialog = null)
            MidiCoreWorkspaceIntent.ConfirmDiscardAuthorityDraft -> confirmDiscardDraft()
        }
    }

    /** Alias for Compose callers that prefer dispatch terminology. */
    fun dispatch(intent: MidiCoreWorkspaceIntent) = accept(intent)

    override fun close() {
        if (closed) return
        closed = true
        activeCancellation?.set(true)
        activeJob?.cancel()
        activeJob = null
        activeCancellation = null
        useCases.audition.close()
        scope.cancel()
    }

    private fun create(intent: MidiCoreWorkspaceIntent.CreateProject) {
        if (guardProjectOperation(intent)) return
        startOperation(MidiCoreWorkspaceOperationKind.PROJECT, "Creating MIDI Core project…", intent) { _ ->
            when (val result = useCases.create(CreateMidiCoreProject(intent.root, intent.name, intent.id, intent.applicationVersion))) {
                is MidiCoreProjectLifecycleResult.Opened -> success("MIDI Core project created.", result.session) { clearAuditionForProjectTransition() }
                is MidiCoreProjectLifecycleResult.Rejected -> failure(projectBlocker(result.problem), intent)
            }
        }
    }

    private fun open(root: Path, pending: MidiCoreWorkspaceIntent = MidiCoreWorkspaceIntent.OpenProject(root)) {
        if (state.value.authority.draftDirty) {
            _state.value = _state.value.copy(dialog = MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft(pending))
            return
        }
        if (guardProjectOperation(pending)) return
        startOperation(MidiCoreWorkspaceOperationKind.PROJECT, "Opening MIDI Core project…", pending) { _ ->
            when (val result = useCases.open(root)) {
                is MidiCoreProjectLifecycleResult.Opened -> success("MIDI Core project opened.", result.session) { clearAuditionForProjectTransition() }
                is MidiCoreProjectLifecycleResult.Rejected -> failure(projectBlocker(result.problem), pending)
            }
        }
    }

    private fun openLast() {
        val root = preferences.lastOpenedProject()
        if (root == null) {
            failImmediately(blocker(
                MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED,
                "No previously opened MIDI Core project is available.",
                "Create a project or choose a project folder.",
                action = MidiCoreWorkspaceIntent.CreateProject(Path.of("."), "Untitled"),
            ))
        } else {
            open(root, MidiCoreWorkspaceIntent.OpenLastProject)
        }
    }

    private fun reload() {
        val root = session?.root ?: return failImmediately(blocker(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, "No MIDI Core project is open.", "Open or create a project before reloading."))
        if (state.value.authority.draftDirty) {
            _state.value = _state.value.copy(dialog = MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft(MidiCoreWorkspaceIntent.ReloadProject))
            return
        }
        open(root, MidiCoreWorkspaceIntent.ReloadProject)
    }

    private fun closeProject() {
        val intent = MidiCoreWorkspaceIntent.CloseProject
        if (state.value.authority.draftDirty) {
            _state.value = _state.value.copy(dialog = MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft(intent))
            return
        }
        val current = session ?: return
        if (state.value.busy) return busyBlocker()
        clearAuditionForProjectTransition()
        useCases.close(current)
        session = null
        _state.value = MidiCoreWorkspaceState(blockers = baseBlockers(null), notification = "MIDI Core project closed.")
    }

    private fun importSource(intent: MidiCoreWorkspaceIntent.ImportSource) {
        val current = requireSessionOrBlock() ?: return
        clearAuditionForProjectTransition()
        startOperation(MidiCoreWorkspaceOperationKind.IMPORT, "Importing source MIDI…", intent) { _ ->
            when (val result = useCases.importSource(ImportMidiCoreSource(current, intent.source))) {
                is MidiCoreSourceImportResult.Imported -> success("Source MIDI imported and preserved.", result.session) {
                    clearAuditionForProjectTransition()
                    hydrateSourceValidation(result.validation)
                }
                is MidiCoreSourceImportResult.Rejected -> failure(sourceBlocker(result.problem, result.validation), intent)
            }
        }
    }

    private fun updateAuthorityDraft(draft: MidiCoreAuthorityDraft) {
        if (session == null) {
            failImmediately(blocker(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, "Open a MIDI Core project before editing authority.", "Create or open a project first."))
            return
        }
        _state.value = _state.value.copy(
            authority = _state.value.authority.copy(draft = draft, draftDirty = draft != authorityDraft(_state.value.project?.authority)),
            notification = null,
        )
    }

    private fun confirmAuthority() {
        val current = requireSessionOrBlock() ?: return
        if (state.value.authority.draftDirty.not() && current.project.authority != null) {
            _state.value = _state.value.copy(notification = "Musical authority is already confirmed.")
            return
        }
        clearAuditionForProjectTransition()
        startOperation(MidiCoreWorkspaceOperationKind.AUTHORITY, "Confirming musical authority…", MidiCoreWorkspaceIntent.ConfirmAuthority) { _ ->
            when (val result = useCases.confirmAuthority(state.value.authority.draft.toRequest(current))) {
                is app.melotrail.application.MidiCoreAuthorityResult.Confirmed -> success("Musical authority confirmed.", result.session) {
                    clearAuditionForProjectTransition()
                    _state.value = _state.value.copy(
                        authority = _state.value.authority.copy(
                            confirmed = result.session.project.authority,
                            draft = authorityDraft(result.session.project.authority),
                            draftDirty = false,
                            suggestions = result.suggestions,
                            lastInvalidation = result.invalidation,
                        ),
                        source = _state.value.source.copy(validation = result.validation, findings = result.validation.findings),
                    )
                }
                is app.melotrail.application.MidiCoreAuthorityResult.Rejected -> failure(authorityBlocker(result.problem, result.validation), MidiCoreWorkspaceIntent.ConfirmAuthority)
            }
        }
    }

    private fun replaceStructure(intent: MidiCoreWorkspaceIntent.ReplaceStructure) {
        val current = requireSessionOrBlock() ?: return
        clearAuditionForProjectTransition()
        startOperation(MidiCoreWorkspaceOperationKind.STRUCTURE, "Saving structure timeline…", intent) { _ ->
            when (val result = useCases.replaceStructure(ReplaceMidiCoreStructure(current, intent.definitions, intent.occurrences))) {
                is app.melotrail.application.MidiCoreStructureTimelineResult.Updated -> success("Structure timeline saved.", result.session) {
                    clearAuditionForProjectTransition()
                    _state.value = _state.value.copy(
                        authority = _state.value.authority.copy(lastInvalidation = result.invalidation),
                    )
                }
                is app.melotrail.application.MidiCoreStructureTimelineResult.Rejected -> failure(structureBlocker(result.problem), intent)
            }
        }
    }

    private fun replaceHarmony(intent: MidiCoreWorkspaceIntent.ReplaceHarmony) {
        val current = requireSessionOrBlock() ?: return
        clearAuditionForProjectTransition()
        startOperation(MidiCoreWorkspaceOperationKind.HARMONY, "Saving authoritative harmony…", intent) { _ ->
            when (val result = useCases.replaceHarmony(ReplaceMidiCoreHarmony(current, intent.events))) {
                is app.melotrail.application.MidiCoreAuthoritativeHarmonyResult.Updated -> success("Authoritative harmony saved.", result.session) {
                    clearAuditionForProjectTransition()
                    _state.value = _state.value.copy(
                        authority = _state.value.authority.copy(lastInvalidation = result.invalidation),
                    )
                }
                is app.melotrail.application.MidiCoreAuthoritativeHarmonyResult.Rejected -> failure(harmonyBlocker(result.problem), intent)
            }
        }
    }

    private fun selectReviewScope(intent: MidiCoreWorkspaceIntent.SelectReviewScope) {
        _state.value = _state.value.copy(review = _state.value.review.copy(role = intent.role, occurrenceId = intent.occurrenceId, comparison = null))
    }

    private fun loadCandidates(intent: MidiCoreWorkspaceIntent.LoadCandidates) {
        val current = requireSessionOrBlock() ?: return
        startOperation(MidiCoreWorkspaceOperationKind.CANDIDATE_REVIEW, "Loading candidate evidence…", intent) { _ ->
            when (val result = useCases.listCandidates(ListMidiCoreCandidates(current, intent.role, intent.occurrenceId, current.project.revision))) {
                is MidiCoreCandidateReviewResult.Listed -> success("Candidate evidence loaded.") {
                    val selectedCandidateId = _state.value.review.selectedCandidateId
                        ?.takeIf { selected -> result.candidates.any { it.candidate.id == selected } }
                        ?: result.candidates.singleOrNull { it.accepted }?.candidate?.id
                        ?: result.candidates.lastOrNull { it.candidate.status != app.melotrail.project.MidiCoreCandidateStatus.REJECTED }?.candidate?.id
                        ?: result.candidates.lastOrNull()?.candidate?.id
                    _state.value = _state.value.copy(review = _state.value.review.copy(
                        role = intent.role,
                        occurrenceId = intent.occurrenceId,
                        candidates = result.candidates,
                        comparison = null,
                        selectedCandidateId = selectedCandidateId,
                    ))
                }
                is MidiCoreCandidateReviewResult.Rejected -> failure(candidateBlocker(result.problem), intent)
                is MidiCoreCandidateReviewResult.Compared -> error("Candidate listing returned comparison evidence")
            }
        }
    }

    private fun compareCandidates(intent: MidiCoreWorkspaceIntent.CompareCandidates) {
        val current = requireSessionOrBlock() ?: return
        val reviewState = state.value.review
        val role = reviewState.role ?: return failImmediately(blocker(MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED, "Choose a role before comparing candidates.", "Select a role and occurrence, then load its candidates."))
        val occurrence = reviewState.occurrenceId ?: return failImmediately(blocker(MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED, "Choose an occurrence before comparing candidates.", "Select a role and occurrence, then load its candidates."))
        startOperation(MidiCoreWorkspaceOperationKind.CANDIDATE_REVIEW, "Comparing candidate evidence…", intent) { _ ->
            when (val result = useCases.compareCandidates(CompareMidiCoreCandidates(current, role, occurrence, intent.firstCandidateId, intent.secondCandidateId, current.project.revision))) {
                is MidiCoreCandidateReviewResult.Compared -> success("Candidate differences calculated.") {
                    val compared = listOf(result.first, result.second).associateBy { it.candidate.id }
                    val currentCandidates = _state.value.review.candidates
                    _state.value = _state.value.copy(review = _state.value.review.copy(
                        candidates = if (currentCandidates.isEmpty()) compared.values.toList() else currentCandidates.map { compared[it.candidate.id] ?: it },
                        comparison = MidiCoreCandidateComparison(result.first, result.second, result.differences),
                    ))
                }
                is MidiCoreCandidateReviewResult.Rejected -> failure(candidateBlocker(result.problem), intent)
                is MidiCoreCandidateReviewResult.Listed -> error("Candidate comparison returned a list")
            }
        }
    }

    private fun generateCandidate(intent: MidiCoreWorkspaceIntent, regenerate: Boolean) {
        val current = requireSessionOrBlock() ?: return
        val request = when (intent) {
            is MidiCoreWorkspaceIntent.GenerateCandidate -> CandidateGenerationRequest(
                intent.role, intent.occurrenceId, intent.performanceProfileId, intent.patternId, intent.generator, intent.sectionPolicy,
            )
            is MidiCoreWorkspaceIntent.RegenerateCandidate -> CandidateGenerationRequest(
                intent.role, intent.occurrenceId, intent.performanceProfileId, intent.patternId, intent.generator, intent.sectionPolicy,
            )
            else -> return
        }
        startOperation(MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION, "Generating ${request.role.name.lowercase()} candidate…", intent) { cancellation ->
            val generatorRequest = GenerateMidiCoreCandidate(
                session = current,
                role = request.role,
                occurrenceId = request.occurrenceId,
                performanceProfileId = request.performanceProfileId,
                patternId = request.patternId,
                generator = request.generator,
                sectionPolicy = request.sectionPolicy,
                cancellation = app.melotrail.application.MidiCoreGenerationCancellation { cancellation.get() },
            )
            val result = if (regenerate) {
                useCases.regenerateCandidate(RegenerateMidiCoreCandidate(generatorRequest, current.project.revision))
            } else {
                useCases.generateCandidate(generatorRequest)
            }
            when (result) {
                is MidiCoreCandidateGenerationResult.Published -> {
                    val candidates = candidateReviewItems(result.session, request.role, request.occurrenceId)
                        ?: listOf(app.melotrail.application.MidiCoreCandidateReviewItem(
                            candidate = result.candidate,
                            validation = result.validation,
                            notes = emptyList(),
                            authorityCurrent = true,
                            accepted = false,
                            locked = false,
                        ))
                    success("Alternative ready to review.", result.session) {
                        _state.value = _state.value.copy(review = _state.value.review.copy(
                            role = request.role,
                            occurrenceId = request.occurrenceId,
                            candidates = candidates,
                            comparison = null,
                            selectedCandidateId = result.candidate.id,
                        ))
                    }
                }
                is MidiCoreCandidateGenerationResult.ValidationRejected -> failure(
                    blocker(MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE, "The generated candidate failed typed role validation.", "Choose another curated profile or pattern and retry.", sourceCode = "VALIDATION_REJECTED", action = intent, occurrenceId = request.occurrenceId, role = request.role),
                    intent,
                )
                is MidiCoreCandidateGenerationResult.Cancelled -> cancelled()
                is MidiCoreCandidateGenerationResult.Rejected -> failure(candidateBlocker(result.problem), intent)
            }
        }
    }

    private fun acceptCandidate(intent: MidiCoreWorkspaceIntent.AcceptCandidate) = candidateTransition(intent) { current ->
        useCases.acceptCandidate(AcceptMidiCoreCandidate(current, intent.candidateId, intent.locked, current.project.revision))
    }

    private fun rejectCandidate(intent: MidiCoreWorkspaceIntent.RejectCandidate) = candidateTransition(intent) { current ->
        useCases.rejectCandidate(RejectMidiCoreCandidate(current, intent.candidateId, intent.reason, current.project.revision))
    }

    private fun lockCandidate(intent: MidiCoreWorkspaceIntent.LockCandidate) = candidateTransition(intent) { current ->
        useCases.lockCandidate(LockMidiCoreCandidate(current, intent.candidateId, current.project.revision))
    }

    private fun unlockCandidate(intent: MidiCoreWorkspaceIntent.UnlockCandidate) = candidateTransition(intent) { current ->
        useCases.unlockCandidate(UnlockMidiCoreCandidate(current, intent.candidateId, current.project.revision))
    }

    private fun restoreCandidate(intent: MidiCoreWorkspaceIntent.RestoreCandidate) = candidateTransition(intent) { current ->
        useCases.restoreCandidate(RestoreMidiCoreCandidate(current, intent.occurrenceId, intent.role, intent.candidateId, intent.locked, current.project.revision))
    }

    private fun candidateTransition(
        intent: MidiCoreWorkspaceIntent,
        action: (app.melotrail.application.MidiCoreProjectSession) -> MidiCoreCandidateLifecycleResult,
    ) {
        val current = requireSessionOrBlock() ?: return
        val currentReviewItems = state.value.review.candidates
        startOperation(MidiCoreWorkspaceOperationKind.CANDIDATE_REVIEW, "Saving candidate review decision…", intent) { _ ->
            when (val result = action(current)) {
                is MidiCoreCandidateLifecycleResult.Updated -> {
                    val candidate = result.candidate
                    val candidates = candidateReviewItems(result.session, candidate.role, candidate.occurrenceId)
                        ?: currentReviewItems.map { item ->
                            val acceptance = result.session.project.acceptances.singleOrNull { it.candidateId == item.candidate.id }
                            item.copy(
                                candidate = if (item.candidate.id == candidate.id) candidate else item.candidate,
                                accepted = acceptance != null,
                                locked = acceptance?.locked == true,
                            )
                        }
                    success(candidateTransitionMessage(intent), result.session) {
                        _state.value = _state.value.copy(review = _state.value.review.copy(
                            role = candidate.role,
                            occurrenceId = candidate.occurrenceId,
                            candidates = candidates,
                            comparison = null,
                            selectedCandidateId = candidate.id,
                        ))
                    }
                }
                is MidiCoreCandidateLifecycleResult.Published -> success("Candidate published.", result.session)
                is MidiCoreCandidateLifecycleResult.Rejected -> failure(candidateBlocker(result.problem), intent)
            }
        }
    }

    private fun exportPackage() {
        val current = requireSessionOrBlock() ?: return
        startOperation(MidiCoreWorkspaceOperationKind.EXPORT, "Publishing MIDI package…", MidiCoreWorkspaceIntent.ExportPackage) { _ ->
            when (val result = useCases.export(app.melotrail.application.ExportMidiCorePackage(current, expectedRevision = current.project.revision))) {
                is MidiCoreMidiPackageExportResult.Exported -> success("MIDI package published.", result.packageResult.session) {
                    _state.value = _state.value.copy(export = MidiCoreExportUiState(result.packageResult, result.packageResult.snapshot))
                }
                is MidiCoreMidiPackageExportResult.Rejected -> failure(exportBlocker(result.problem), MidiCoreWorkspaceIntent.ExportPackage)
            }
        }
    }

    private fun playSourceMelody() {
        val current = requireSessionOrBlock() ?: return
        val intent = MidiCoreWorkspaceIntent.PlaySourceMelody
        startOperation(MidiCoreWorkspaceOperationKind.AUDITION, "Starting source MIDI audition…", intent) { cancellation ->
            if (cancellation.get()) return@startOperation cancelled()
            when (val prepared = useCases.prepareSourceAudition(PrepareMidiCoreSourceAudition(current))) {
                is MidiCoreSourceAuditionResult.Rejected -> failure(
                    sourceAuditionBlocker(prepared.problem),
                    intent,
                )
                is MidiCoreSourceAuditionResult.Ready -> {
                    if (cancellation.get()) return@startOperation cancelled()
                    when (val result = useCases.audition.play(prepared.plan)) {
                        is MidiAuditionResult.Applied -> success("Source MIDI audition started.") {
                            _state.value = _state.value.copy(audition = result.state, blockers = baseBlockers(session?.project))
                        }
                        is MidiAuditionResult.Failed -> failure(
                            blocker(
                                MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
                                result.problem.message,
                                result.problem.nextAction,
                                result.problem.code.name,
                            ),
                            intent,
                        ) {
                            _state.value = _state.value.copy(audition = result.state)
                        }
                    }
                }
            }
        }
    }

    private fun playOccurrence(intent: MidiCoreWorkspaceIntent.PlayOccurrence) {
        val current = requireSessionOrBlock() ?: return
        startOperation(MidiCoreWorkspaceOperationKind.AUDITION, "Starting occurrence MIDI audition…", intent) { cancellation ->
            if (cancellation.get()) return@startOperation cancelled()
            when (val prepared = useCases.prepareOccurrenceAudition(PrepareMidiCoreOccurrenceAudition(current, intent.occurrenceId))) {
                is MidiCoreSourceAuditionResult.Rejected -> failure(sourceAuditionBlocker(prepared.problem), intent)
                is MidiCoreSourceAuditionResult.Ready -> {
                    if (cancellation.get()) return@startOperation cancelled()
                    when (val result = useCases.audition.play(prepared.plan)) {
                        is MidiAuditionResult.Applied -> success("Occurrence MIDI audition started.") {
                            _state.value = _state.value.copy(audition = result.state, blockers = baseBlockers(session?.project))
                        }
                        is MidiAuditionResult.Failed -> failure(
                            blocker(
                                MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
                                result.problem.message,
                                result.problem.nextAction,
                                result.problem.code.name,
                            ),
                            intent,
                        ) {
                            _state.value = _state.value.copy(audition = result.state)
                        }
                    }
                }
            }
        }
    }

    private fun playReviewAudition(
        intent: MidiCoreWorkspaceIntent,
        prepare: (MidiCoreProjectSession) -> MidiCoreReviewAuditionResult,
    ) {
        val current = requireSessionOrBlock() ?: return
        startOperation(MidiCoreWorkspaceOperationKind.AUDITION, "Preparing Review MIDI audition…", intent) { cancellation ->
            if (cancellation.get()) return@startOperation cancelled()
            when (val prepared = prepare(current)) {
                is MidiCoreReviewAuditionResult.Rejected -> failure(reviewAuditionBlocker(prepared.problem), intent)
                is MidiCoreReviewAuditionResult.Ready -> {
                    if (cancellation.get()) return@startOperation cancelled()
                    when (val result = useCases.audition.play(prepared.plan)) {
                        is MidiAuditionResult.Applied -> success("Review MIDI audition started.") {
                            _state.value = _state.value.copy(audition = result.state, blockers = baseBlockers(session?.project))
                        }
                        is MidiAuditionResult.Failed -> failure(
                            blocker(
                                MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
                                result.problem.message,
                                result.problem.nextAction,
                                result.problem.code.name,
                            ),
                            intent,
                        ) {
                            _state.value = _state.value.copy(audition = result.state)
                        }
                    }
                }
            }
        }
    }

    /**
     * Replace an in-flight style preview with the newest selection. The selected
     * style is visible before opening the local output, so a missing device never
     * discards the musician's choice. Other project-changing operations remain
     * serialized by the regular workspace operation guard.
     */
    private fun previewArrangementStyle(intent: MidiCoreWorkspaceIntent.PreviewArrangementStyle) {
        val current = requireSessionOrBlock() ?: return
        _state.value = _state.value.copy(
            stylePreview = _state.value.stylePreview.copy(selectedStyleId = intent.styleId, occurrenceId = intent.occurrenceId),
        )
        val active = state.value.operation
        if (active.active && (active.kind != MidiCoreWorkspaceOperationKind.AUDITION || active.retry !is MidiCoreWorkspaceIntent.PreviewArrangementStyle)) {
            busyBlocker()
            return
        }
        startOperation(
            MidiCoreWorkspaceOperationKind.AUDITION,
            "Preparing ${intent.styleId.replace('-', ' ')} MIDI preview…",
            intent,
            supersedeActive = true,
        ) { cancellation ->
            if (cancellation.get()) return@startOperation cancelled()
            when (val prepared = useCases.previewArrangementStyle(PrepareMidiCoreArrangementStylePreview(current, intent.styleId, intent.occurrenceId, intent.seed))) {
                is MidiCoreArrangementStylePreviewResult.Rejected -> failure(
                    stylePreviewBlocker(prepared.problem),
                    intent,
                )
                is MidiCoreArrangementStylePreviewResult.Ready -> {
                    if (cancellation.get()) return@startOperation cancelled()
                    when (val result = useCases.audition.play(prepared.plan.copy(outputDeviceId = state.value.audition.outputDeviceId))) {
                        is MidiAuditionResult.Applied -> success("${intent.styleId.replace('-', ' ')} style preview started.") {
                            _state.value = _state.value.copy(
                                audition = result.state,
                                stylePreview = _state.value.stylePreview.copy(
                                    selectedStyleId = intent.styleId,
                                    occurrenceId = intent.occurrenceId,
                                    cacheStatus = prepared.cacheStatus,
                                    key = prepared.key,
                                ),
                                blockers = baseBlockers(session?.project),
                            )
                        }
                        is MidiAuditionResult.Failed -> failure(
                            blocker(
                                MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
                                result.problem.message,
                                result.problem.nextAction,
                                result.problem.code.name,
                            ),
                            intent,
                        ) {
                            _state.value = _state.value.copy(audition = result.state)
                        }
                    }
                }
            }
        }
    }

    /** Select one authoritative section without coupling the map to page-local candidate state. */
    private fun selectArrangementOccurrence(intent: MidiCoreWorkspaceIntent.SelectArrangementOccurrence) {
        val occurrence = state.value.project?.authority?.occurrences?.singleOrNull { it.id == intent.occurrenceId }
            ?: return failImmediately(blocker(
                MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED,
                "The selected song-map section is no longer part of current authority.",
                "Reload Structure & Harmony and select a saved section.",
                occurrenceId = intent.occurrenceId,
            ))
        _state.value = _state.value.copy(arrangement = _state.value.arrangement.copy(selectedOccurrenceId = occurrence.id))
        val window = state.value.audition.window
        when {
            state.value.busy -> Unit
            window != null && window.startTick <= occurrence.startTick && window.endTick >= occurrence.endTick -> {
                audition { useCases.audition.setLoop(MidiAuditionLoop(occurrence.startTick, occurrence.endTick)) }
            }
            state.value.stylePreview.selectedStyleId != null -> previewArrangementStyle(
                MidiCoreWorkspaceIntent.PreviewArrangementStyle(
                    styleId = requireNotNull(state.value.stylePreview.selectedStyleId),
                    occurrenceId = occurrence.id,
                ),
            )
        }
    }

    /** Create or resume one deterministic all-role draft while retaining scoped progress in workspace state. */
    private fun generateArrangementDraft(intent: MidiCoreWorkspaceIntent.CreateArrangementDraft) {
        val current = requireSessionOrBlock() ?: return
        val draftId = intent.draftId ?: state.value.arrangement.incompleteDraftId
            ?.takeIf { state.value.arrangement.incompleteDraftStyleId == intent.styleId }
        startOperation(
            MidiCoreWorkspaceOperationKind.DRAFT_GENERATION,
            "Creating ${intent.styleId.replace('-', ' ')} complete draft…",
            intent.copy(draftId = draftId),
        ) { cancellation ->
            val result = useCases.generateArrangementDraft(
                GenerateMidiCoreArrangementDraft(
                    session = current,
                    styleId = intent.styleId,
                    rootSeed = intent.rootSeed,
                    draftId = draftId,
                    cancellation = app.melotrail.application.MidiCoreGenerationCancellation { cancellation.get() },
                    onProgress = { progress -> publishDraftProgress(progress) },
                ),
            )
            when (result) {
                is MidiCoreArrangementDraftGenerationResult.Completed -> success(
                    "Complete draft ready for Review.",
                    result.session,
                ) {
                    _state.value = _state.value.copy(
                        arrangement = _state.value.arrangement.copy(
                            incompleteDraftId = null,
                            incompleteDraftStyleId = null,
                            rootSeed = intent.rootSeed,
                        ),
                    )
                }
                is MidiCoreArrangementDraftGenerationResult.Incomplete -> failure(
                    draftBlocker(result.problem, intent.copy(draftId = result.draftId)),
                    intent.copy(draftId = result.draftId),
                    result.session,
                ) {
                    _state.value = _state.value.copy(
                        arrangement = _state.value.arrangement.copy(
                            incompleteDraftId = result.draftId,
                            incompleteDraftStyleId = intent.styleId,
                            rootSeed = intent.rootSeed,
                        ),
                    )
                }
                is MidiCoreArrangementDraftGenerationResult.Cancelled -> cancelled(result.session) {
                    _state.value = _state.value.copy(
                        arrangement = _state.value.arrangement.copy(
                            incompleteDraftId = result.draftId,
                            incompleteDraftStyleId = intent.styleId,
                            rootSeed = intent.rootSeed,
                        ),
                    )
                }
            }
        }
    }

    /** Marshal synchronous generation callbacks onto Compose state without admitting stale operations. */
    private fun publishDraftProgress(progress: app.melotrail.application.MidiCoreArrangementDraftProgress) {
        scope.launch(dispatchers.ui) {
            val operation = _state.value.operation
            if (!operation.active || operation.kind != MidiCoreWorkspaceOperationKind.DRAFT_GENERATION) return@launch
            val active = progress.activeScope
            _state.value = _state.value.copy(
                operation = operation.copy(
                    progress = MidiCoreWorkspaceOperationProgress(progress.completedCount, progress.totalScopes),
                    message = active?.let { "Creating draft: ${it.occurrenceId} · ${it.role.name.lowercase()} (${progress.completedCount}/${progress.totalScopes})" }
                        ?: "Creating draft: ${progress.completedCount}/${progress.totalScopes} scopes complete",
                ),
            )
        }
    }

    /** Create three linked, immutable alternatives for one selected exception while retaining the global style. */
    private fun regenerateArrangementSection(intent: MidiCoreWorkspaceIntent.RegenerateArrangementSection) {
        val current = requireSessionOrBlock() ?: return
        val style = runCatching { MidiCoreArrangementStyleCatalog.require(intent.styleId) }.getOrElse {
            return failImmediately(blocker(
                MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED,
                "The selected arrangement style is no longer available.",
                "Choose a current style before regenerating this section.",
                action = intent,
                occurrenceId = intent.occurrenceId,
            ))
        }
        if (current.project.authority?.occurrences?.any { it.id == intent.occurrenceId } != true) {
            return failImmediately(blocker(
                MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED,
                "The selected section is no longer part of current authority.",
                "Reload Structure & Harmony and choose a saved section.",
                action = intent,
                occurrenceId = intent.occurrenceId,
            ))
        }
        startOperation(MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION, "Regenerating selected section…", intent) { cancellation ->
            var working = current
            val dependencies = mutableListOf<String>()
            CandidateRole.entries.forEachIndexed { index, role ->
                if (cancellation.get()) return@startOperation cancelled(working)
                publishSectionProgress(index, role)
                val choice = style.role(role)
                when (val result = useCases.generateCandidate(
                    GenerateMidiCoreCandidate(
                        session = working,
                        role = role,
                        occurrenceId = intent.occurrenceId,
                        performanceProfileId = choice.performanceProfileId,
                        patternId = choice.patternId,
                        generator = MidiCoreGeneratorInput(
                            generatorId = "midi-core-style-repair",
                            generatorVersion = "midi-core-style-v${MidiCoreArrangementStyleCatalog.VERSION}",
                            patternId = choice.patternId,
                            seed = intent.rootSeed + index,
                        ),
                        sectionPolicy = choice.sectionPolicy,
                        draftDependencyIds = dependencies.toList(),
                        cancellation = app.melotrail.application.MidiCoreGenerationCancellation { cancellation.get() },
                    ),
                )) {
                    is MidiCoreCandidateGenerationResult.Published -> {
                        working = result.session
                        dependencies += result.candidate.id
                    }
                    is MidiCoreCandidateGenerationResult.Cancelled -> return@startOperation cancelled(working)
                    is MidiCoreCandidateGenerationResult.ValidationRejected -> return@startOperation failure(
                        blocker(
                            MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED,
                            "The ${role.name.lowercase()} section repair did not pass validation.",
                            "Adjust roles or choose another style, then retry this section.",
                            action = intent,
                            occurrenceId = intent.occurrenceId,
                            role = role,
                        ),
                        intent,
                        working,
                    )
                    is MidiCoreCandidateGenerationResult.Rejected -> return@startOperation failure(candidateBlocker(result.problem), intent, working)
                }
            }
            val candidates = candidateReviewItems(working, CandidateRole.DRUMS, intent.occurrenceId).orEmpty()
            success("Section alternatives are ready for review.", working) {
                _state.value = _state.value.copy(
                    arrangement = _state.value.arrangement.copy(selectedOccurrenceId = intent.occurrenceId),
                    review = _state.value.review.copy(
                        role = CandidateRole.DRUMS,
                        occurrenceId = intent.occurrenceId,
                        candidates = candidates,
                        comparison = null,
                        selectedCandidateId = candidates.lastOrNull()?.candidate?.id,
                    ),
                )
            }
        }
    }

    private fun publishSectionProgress(completed: Int, activeRole: CandidateRole) {
        scope.launch(dispatchers.ui) {
            val operation = _state.value.operation
            if (!operation.active || operation.kind != MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION) return@launch
            _state.value = _state.value.copy(
                operation = operation.copy(
                    progress = MidiCoreWorkspaceOperationProgress(completed, CandidateRole.entries.size),
                    message = "Regenerating section: ${activeRole.name.lowercase()} (${completed}/${CandidateRole.entries.size})",
                ),
            )
        }
    }

    private fun audition(action: () -> MidiAuditionResult) {
        val result = try {
            action()
        } catch (error: Exception) {
            _state.value = _state.value.copy(blockers = listOf(blocker(MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE, "MIDI audition could not be updated.", "Stop audition, choose a valid MIDI view, and retry.", error.javaClass.simpleName)))
            return
        }
        _state.value = _state.value.copy(
            audition = result.state,
            operation = _state.value.operation.copy(
                kind = MidiCoreWorkspaceOperationKind.AUDITION,
                phase = if (result is MidiAuditionResult.Failed) MidiCoreWorkspaceOperationPhase.FAILED else MidiCoreWorkspaceOperationPhase.SUCCEEDED,
                message = if (result is MidiAuditionResult.Failed) result.problem.message else "MIDI audition updated.",
                outcome = if (result is MidiAuditionResult.Failed) MidiCoreWorkspaceOperationOutcome.FAILURE else MidiCoreWorkspaceOperationOutcome.SUCCESS,
            ),
            blockers = if (result is MidiAuditionResult.Failed) listOf(blocker(MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE, result.problem.message, result.problem.nextAction, result.problem.code.name)) else baseBlockers(session?.project),
        )
    }

    /** Stop and forget a selected view before a project transition can make it stale. */
    private fun clearAuditionForProjectTransition() {
        runCatching { useCases.audition.stop() }
        _state.value = _state.value.copy(audition = MidiAuditionState())
    }

    private fun cancelOperation() {
        val current = state.value.operation
        if (!current.active) return
        if (!current.cancellableAtBoundary) return
        activeCancellation?.set(true)
        _state.value = _state.value.copy(operation = current.copy(phase = MidiCoreWorkspaceOperationPhase.CANCELLING, message = "Cancelling ${current.kind?.name?.lowercase() ?: "operation"}…", cancellableAtBoundary = false))
        val cancelledJob = activeJob
        if (current.kind != MidiCoreWorkspaceOperationKind.DRAFT_GENERATION) cancelledJob?.cancel()
        if (cancelledJob == null) {
            finishCancelled(current.id)
        } else {
            scope.launch {
                cancelledJob.join()
                finishCancelled(current.id)
            }
        }
    }

    private fun retry() {
        val retry = state.value.operation.retry ?: return
        if (state.value.operation.active) return busyBlocker()
        accept(retry)
    }

    private fun confirmDiscardDraft() {
        val dialog = state.value.dialog as? MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft ?: return
        val pending = dialog.pending
        _state.value = _state.value.copy(
            authority = _state.value.authority.copy(
                draft = authorityDraft(_state.value.project?.authority),
                draftDirty = false,
            ),
            dialog = null,
        )
        accept(pending)
    }

    private fun startOperation(
        kind: MidiCoreWorkspaceOperationKind,
        message: String,
        retry: MidiCoreWorkspaceIntent?,
        supersedeActive: Boolean = false,
        work: suspend (AtomicBoolean) -> WorkspaceOutcome,
    ) {
        if (state.value.operation.active) {
            if (!supersedeActive) {
                busyBlocker()
                return
            }
            activeCancellation?.set(true)
            activeJob?.cancel()
        }
        val operationId = ++nextOperationId
        val admission = Admission(
            root = session?.root?.toAbsolutePath()?.normalize(),
            revision = session?.project?.revision,
            authorityHash = session?.project?.let { runCatching { MidiCoreAuthorityHasher.from(it).sha256 }.getOrNull() },
            sessionBound = session != null,
        )
        val cancellation = AtomicBoolean(false)
        activeCancellation = cancellation
        _state.value = _state.value.copy(
            operation = MidiCoreWorkspaceOperation(
                id = operationId,
                kind = kind,
                phase = MidiCoreWorkspaceOperationPhase.RUNNING,
                message = message,
                cancellableAtBoundary = true,
                retry = retry,
            ),
            notification = null,
        )
        logger.event("midi_core_${kind.name.lowercase()}", "operation-${operationId}-started")
        val job = scope.launch {
            try {
                val outcome = withContext(dispatchers.io) { work(cancellation) }
                val persisted = withContext(dispatchers.io) {
                    admission.root?.let { root -> useCases.readCurrent(root) }
                }
                withContext(dispatchers.ui) {
                    finishOperation(operationId, admission, outcome, persisted)
                }
            } catch (_: CancellationException) {
                withContext(NonCancellable + dispatchers.ui) { finishCancelled(operationId) }
            } catch (error: Throwable) {
                withContext(dispatchers.ui) {
                    finishFailure(
                        operationId,
                        blocker(MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE, "The MIDI Core operation failed safely.", "Retry the operation after checking the project and its artifacts.", error.javaClass.simpleName, retry),
                        retry,
                    )
                }
            }
        }
        activeJob = job
    }

    private fun finishOperation(
        operationId: Long,
        admission: Admission,
        outcome: WorkspaceOutcome,
        persisted: MidiCoreProjectSession?,
    ) {
        if (state.value.operation.id != operationId) return
        if (state.value.operation.phase == MidiCoreWorkspaceOperationPhase.CANCELLING && outcome !is WorkspaceOutcome.Cancelled) {
            finishCancelled(operationId)
            return
        }
        val expectedProject = when (outcome) {
            is WorkspaceOutcome.Success -> outcome.session?.project
            is WorkspaceOutcome.Failure -> outcome.session?.project
            is WorkspaceOutcome.Cancelled -> outcome.session?.project
        }
        if (!admitted(admission, persisted, expectedProject)) {
            val stale = blocker(
                MidiCoreWorkspaceBlockerCode.STALE_COMPLETION,
                "The operation completed against an older project revision or authority hash; its result was not admitted.",
                "Reload the project and retry the operation.",
                action = MidiCoreWorkspaceIntent.ReloadProject,
            )
            finishFailure(operationId, stale, state.value.operation.retry)
            return
        }
        when (outcome) {
            is WorkspaceOutcome.Success -> {
                outcome.session?.let(::hydrate)
                outcome.apply?.invoke()
                _state.value = _state.value.copy(
                    operation = state.value.operation.copy(
                        phase = MidiCoreWorkspaceOperationPhase.SUCCEEDED,
                        message = outcome.message,
                        cancellableAtBoundary = false,
                        outcome = MidiCoreWorkspaceOperationOutcome.SUCCESS,
                        retry = null,
                    ),
                    notification = outcome.message,
                )
            }
            is WorkspaceOutcome.Failure -> {
                outcome.session?.let(::hydrate)
                outcome.apply?.invoke()
                finishFailure(operationId, outcome.blocker, outcome.retry)
            }
            is WorkspaceOutcome.Cancelled -> {
                outcome.session?.let(::hydrate)
                outcome.apply?.invoke()
                finishCancelled(operationId)
            }
        }
        activeJob = null
        activeCancellation = null
    }

    private fun finishFailure(operationId: Long, failure: MidiCoreWorkspaceBlocker, retry: MidiCoreWorkspaceIntent?, log: Boolean = true) {
        if (state.value.operation.id != operationId) return
        _state.value = _state.value.copy(
            operation = state.value.operation.copy(
                phase = MidiCoreWorkspaceOperationPhase.FAILED,
                message = failure.message,
                cancellableAtBoundary = false,
                retry = retry,
                outcome = MidiCoreWorkspaceOperationOutcome.FAILURE,
            ),
            blockers = listOf(failure) + baseBlockers(session?.project).filterNot { it.code == failure.code },
            notification = null,
        )
        if (log) logger.event("midi_core_operation", "operation-${operationId}-failed")
        activeJob = null
        activeCancellation = null
    }

    private fun finishCancelled(operationId: Long) {
        if (state.value.operation.id != operationId) return
        _state.value = _state.value.copy(
            operation = state.value.operation.copy(
                phase = MidiCoreWorkspaceOperationPhase.CANCELLED,
                message = "Operation cancelled; the last known-good project remains current.",
                cancellableAtBoundary = false,
                retry = state.value.operation.retry,
                outcome = MidiCoreWorkspaceOperationOutcome.CANCELLED,
            ),
            notification = "Operation cancelled; the last known-good project remains current.",
        )
        activeJob = null
        activeCancellation = null
    }

    private fun failImmediately(failure: MidiCoreWorkspaceBlocker) {
        _state.value = _state.value.copy(
            operation = MidiCoreWorkspaceOperation(
                id = ++nextOperationId,
                phase = MidiCoreWorkspaceOperationPhase.FAILED,
                message = failure.message,
                retry = failure.action,
                outcome = MidiCoreWorkspaceOperationOutcome.FAILURE,
            ),
            blockers = listOf(failure) + baseBlockers(session?.project).filterNot { it.code == failure.code },
        )
    }

    private fun busyBlocker() {
        val failure = blocker(MidiCoreWorkspaceBlockerCode.OPERATION_BUSY, "Another MIDI Core operation is still running.", "Wait for it to finish or cancel it before starting another operation.", action = MidiCoreWorkspaceIntent.CancelOperation)
        _state.value = _state.value.copy(blockers = listOf(failure) + baseBlockers(session?.project).filterNot { it.code == failure.code })
    }

    private fun guardProjectOperation(intent: MidiCoreWorkspaceIntent): Boolean {
        if (state.value.authority.draftDirty) {
            _state.value = _state.value.copy(dialog = MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft(intent))
            return true
        }
        if (state.value.operation.active) {
            busyBlocker()
            return true
        }
        return false
    }

    private fun requireSessionOrBlock(): app.melotrail.application.MidiCoreProjectSession? = session ?: run {
        failImmediately(blocker(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, "Open a MIDI Core project before using this action.", "Create or open a project first."))
        null
    }

    private fun admitted(
        admission: Admission,
        persisted: MidiCoreProjectSession?,
        expectedProject: MidiCoreProject?,
    ): Boolean {
        if (!admission.sessionBound) return true
        val current = session
        if (current == null || current.root.toAbsolutePath().normalize() != admission.root) return false
        if (current.project.revision != admission.revision) return false
        val currentHash = runCatching { MidiCoreAuthorityHasher.from(current.project).sha256 }.getOrNull()
        if (currentHash != admission.authorityHash) return false
        if (persisted == null) return false
        val expectedRevision = expectedProject?.revision ?: admission.revision
        if (persisted.project.revision != expectedRevision) return false
        val expectedHash = expectedProject?.let { runCatching { MidiCoreAuthorityHasher.from(it).sha256 }.getOrNull() } ?: admission.authorityHash
        return runCatching { MidiCoreAuthorityHasher.from(persisted.project).sha256 }.getOrNull() == expectedHash
    }

    private fun hydrate(next: app.melotrail.application.MidiCoreProjectSession) {
        val previous = _state.value
        val sameProject = previous.project?.id == next.project.id &&
            previous.projectRoot?.toAbsolutePath()?.normalize() == next.root.toAbsolutePath().normalize()
        val reviewScope = if (sameProject) {
            previous.review.copy(candidates = emptyList(), comparison = null)
        } else {
            MidiCoreCandidateReviewUiState()
        }
        val previewScope = if (sameProject) previous.stylePreview else MidiCoreArrangementStyleUiState()
        val arrangementScope = if (sameProject) {
            previous.arrangement.copy(
                selectedOccurrenceId = previous.arrangement.selectedOccurrenceId
                    ?.takeIf { selected -> next.project.authority?.occurrences?.any { it.id == selected } == true }
                    ?: next.project.authority?.occurrences?.firstOrNull()?.id,
            )
        } else {
            MidiCoreArrangementUiState(selectedOccurrenceId = next.project.authority?.occurrences?.firstOrNull()?.id)
        }
        session = next
        preferences.saveLastOpenedProject(next.root)
        val project = next.project
        val source = project.sourceMidi
        val authority = project.authority
        _state.value = _state.value.copy(
            project = project,
            projectRoot = next.root,
            source = source?.let {
                MidiCoreSourceUiState(
                    status = MidiCoreSourceStatus.IMPORTED,
                    originalFilename = it.originalFilename,
                    sha256 = it.sha256,
                    format = it.format,
                    ppq = it.ppq,
                    sourceEndTick = it.sourceEndTick,
                    trackSummaries = it.trackSummaries,
                    reportAvailable = true,
                )
            } ?: MidiCoreSourceUiState(),
            melody = MidiCoreMelodyUiState(project.selectedMelody),
            authority = MidiCoreAuthorityUiState(
                confirmed = authority,
                draft = authorityDraft(authority),
                draftDirty = false,
            ),
            review = reviewScope,
            stylePreview = previewScope,
            arrangement = arrangementScope,
            audition = useCases.audition.state,
            export = MidiCoreExportUiState(latestSnapshot = project.exportSnapshots.lastOrNull()),
            blockers = baseBlockers(project),
            dialog = null,
        )
    }

    private fun candidateReviewItems(
        candidateSession: MidiCoreProjectSession,
        role: CandidateRole,
        occurrenceId: String,
    ): List<app.melotrail.application.MidiCoreCandidateReviewItem>? =
        when (val review = useCases.listCandidates(ListMidiCoreCandidates(candidateSession, role, occurrenceId, candidateSession.project.revision))) {
            is MidiCoreCandidateReviewResult.Listed -> review.candidates
            is MidiCoreCandidateReviewResult.Rejected,
            is MidiCoreCandidateReviewResult.Compared,
            -> null
        }

    private fun candidateTransitionMessage(intent: MidiCoreWorkspaceIntent): String = when (intent) {
        is MidiCoreWorkspaceIntent.AcceptCandidate -> "Alternative accepted."
        is MidiCoreWorkspaceIntent.RejectCandidate -> "Alternative rejected."
        is MidiCoreWorkspaceIntent.LockCandidate -> "Accepted work locked."
        is MidiCoreWorkspaceIntent.UnlockCandidate -> "Accepted work unlocked."
        is MidiCoreWorkspaceIntent.RestoreCandidate -> "Prior acceptance restored."
        else -> "Candidate review decision saved."
    }

    private fun hydrateSourceValidation(validation: MidiImportValidationResult) {
        _state.value = _state.value.copy(source = _state.value.source.copy(validation = validation, findings = validation.findings, reportAvailable = true))
    }

    private fun authorityDraft(authority: ProjectAuthority?): MidiCoreAuthorityDraft = authority?.let { MidiCoreAuthorityDraft(it.key, it.tempo, it.meter) } ?: MidiCoreAuthorityDraft.defaults()

    private fun stylePreviewBlocker(problem: app.melotrail.application.MidiCoreArrangementStylePreviewProblem) = blocker(
        MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
        problem.message,
        problem.nextAction,
        problem.code.name,
    )

    private fun draftBlocker(problem: MidiCoreArrangementDraftProblem, retry: MidiCoreWorkspaceIntent) = blocker(
        if (problem.code.name.contains("STALE") || problem.code.name.contains("REVISION")) MidiCoreWorkspaceBlockerCode.REVISION_CONFLICT
        else MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED,
        problem.message,
        problem.nextAction,
        problem.code.name,
        action = retry,
        occurrenceId = problem.scope?.occurrenceId,
        role = problem.scope?.role,
    )

    private fun baseBlockers(project: MidiCoreProject?): List<MidiCoreWorkspaceBlocker> = when {
        project == null -> listOf(blocker(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, "No MIDI Core project is open.", "Create or open a MIDI Core project."))
        project.sourceMidi == null -> listOf(blocker(MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED, "A source MIDI file has not been imported.", "Import one Standard MIDI source; the original will be preserved."))
        project.selectedMelody == null -> listOf(blocker(MidiCoreWorkspaceBlockerCode.MELODY_REQUIRED, "The imported source has no automatically protected melody.", "Create a new project and import one valid single-track melody source."))
        project.authority == null -> listOf(blocker(MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED, "Tempo, meter, key, and mode are not authoritative yet.", "Edit and explicitly confirm musical authority."))
        project.authority?.occurrences?.isEmpty() == true -> listOf(blocker(MidiCoreWorkspaceBlockerCode.STRUCTURE_REQUIRED, "The authoritative section timeline is empty.", "Define at least one contiguous section occurrence."))
        project.authority?.chordEvents?.isEmpty() == true -> listOf(blocker(MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED, "No authoritative chord windows are defined.", "Enter gap-free chord windows for every section occurrence."))
        else -> emptyList()
    }

    private fun blocker(
        code: MidiCoreWorkspaceBlockerCode,
        message: String,
        nextAction: String,
        sourceCode: String? = null,
        action: MidiCoreWorkspaceIntent? = null,
        occurrenceId: String? = null,
        role: CandidateRole? = null,
    ) = MidiCoreWorkspaceBlocker(code, message, nextAction, sourceCode, action, occurrenceId, role)

    private fun projectBlocker(problem: MidiCoreProjectProblem) = blocker(MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE, problem.message, problem.nextAction, problem.code.name)

    private fun sourceBlocker(problem: MidiCoreSourceImportProblem, validation: MidiImportValidationResult? = null) = blocker(
        if (validation?.findings?.isNotEmpty() == true) MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED else MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
        problem.message,
        problem.nextAction,
        problem.code.name,
    )

    private fun sourceAuditionBlocker(problem: app.melotrail.application.MidiCoreSourceAuditionProblem) = blocker(
        when (problem.code) {
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.SOURCE_REQUIRED -> MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.MELODY_REQUIRED,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.MELODY_IDENTITY_MISMATCH,
            -> MidiCoreWorkspaceBlockerCode.MELODY_REQUIRED
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.STALE_PROJECT -> MidiCoreWorkspaceBlockerCode.REVISION_CONFLICT
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.INVALID_PROJECT,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.SOURCE_DIGEST_MISMATCH,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.SOURCE_NOT_PLAYABLE,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.AUTHORITY_REQUIRED,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.OCCURRENCE_REQUIRED,
            app.melotrail.application.MidiCoreSourceAuditionProblemCode.OCCURRENCE_NOT_PLAYABLE,
            -> MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE
        },
        problem.message,
        problem.nextAction,
        problem.code.name,
    )

    private fun reviewAuditionBlocker(problem: app.melotrail.application.MidiCoreReviewAuditionProblem) = blocker(
        MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED,
        problem.message,
        problem.nextAction,
        "REVIEW_AUDITION",
    )

    private fun authorityBlocker(problem: MidiCoreAuthorityProblem, validation: MidiImportValidationResult? = null) = blocker(
        MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED,
        problem.message,
        problem.nextAction,
        problem.code.name,
    )

    private fun structureBlocker(problem: MidiCoreStructureTimelineProblem) = blocker(MidiCoreWorkspaceBlockerCode.STRUCTURE_REQUIRED, problem.message, problem.nextAction, problem.code.name)

    private fun harmonyBlocker(problem: MidiCoreAuthoritativeHarmonyProblem) = blocker(MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED, problem.message, problem.nextAction, problem.code.name)

    private fun candidateBlocker(problem: MidiCoreCandidateProblem) = blocker(
        if (problem.code.name.contains("REVISION")) MidiCoreWorkspaceBlockerCode.REVISION_CONFLICT else MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED,
        problem.message,
        problem.nextAction,
        problem.code.name,
    )

    private fun exportBlocker(problem: MidiCorePackageExportProblem) = blocker(
        if (problem.code.name.contains("REVISION")) MidiCoreWorkspaceBlockerCode.REVISION_CONFLICT else MidiCoreWorkspaceBlockerCode.EXPORT_NOT_READY,
        problem.message,
        problem.nextAction,
        problem.code.name,
        occurrenceId = problem.occurrenceId,
        role = problem.role,
    )

    private data class Admission(val root: Path?, val revision: Long?, val authorityHash: String?, val sessionBound: Boolean)

    private data class CandidateGenerationRequest(
        val role: CandidateRole,
        val occurrenceId: String,
        val performanceProfileId: String,
        val patternId: String,
        val generator: MidiCoreGeneratorInput,
        val sectionPolicy: MidiCoreSectionPolicy,
    )

    private sealed interface WorkspaceOutcome {
        data class Success(
            val message: String,
            val session: app.melotrail.application.MidiCoreProjectSession? = null,
            val apply: (() -> Unit)? = null,
        ) : WorkspaceOutcome

        data class Failure(
            val blocker: MidiCoreWorkspaceBlocker,
            val retry: MidiCoreWorkspaceIntent? = null,
            val session: app.melotrail.application.MidiCoreProjectSession? = null,
            val apply: (() -> Unit)? = null,
        ) : WorkspaceOutcome

        data class Cancelled(
            val session: app.melotrail.application.MidiCoreProjectSession? = null,
            val apply: (() -> Unit)? = null,
        ) : WorkspaceOutcome
    }

    private fun success(message: String, session: app.melotrail.application.MidiCoreProjectSession? = null, apply: (() -> Unit)? = null) = WorkspaceOutcome.Success(message, session, apply)

    private fun failure(
        blocker: MidiCoreWorkspaceBlocker,
        retry: MidiCoreWorkspaceIntent? = null,
        session: app.melotrail.application.MidiCoreProjectSession? = null,
        apply: (() -> Unit)? = null,
    ) = WorkspaceOutcome.Failure(blocker, retry, session, apply)

    private fun cancelled(
        session: app.melotrail.application.MidiCoreProjectSession? = null,
        apply: (() -> Unit)? = null,
    ) = WorkspaceOutcome.Cancelled(session, apply)
}
