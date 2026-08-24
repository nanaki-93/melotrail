package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.TranscriptionCleanupProfile
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiQualityRecommendation
import app.melotrail.arrangement.MidiQualityReport
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiQualityReporter
import app.melotrail.arrangement.MidiQualityWarning
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.MidiNormalizationPolicy
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.MidiAiFixArtifactPaths
import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.MidiFeelProfile
import app.melotrail.arrangement.MidiFeelReferences
import app.melotrail.arrangement.MidiFeelReport
import app.melotrail.arrangement.MidiFeelReportStore
import app.melotrail.arrangement.TechnicalCorrectionSelection
import app.melotrail.arrangement.MidiLoFiFeelTransformer
import app.melotrail.arrangement.MidiTranspositionReportStore
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.ArrangementHarmonyContext
import app.melotrail.arrangement.EnsembleTransitionContextFactory
import app.melotrail.arrangement.EnsembleCohesionStore
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.toSectionInstance
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.SectionTypeCatalog
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.PartAnalysisReference
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.ProcessorIdentity
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.StageRunStore
import app.melotrail.arrangement.StageRunRecord
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageSubject
import app.melotrail.arrangement.artifactRef
import app.melotrail.arrangement.sha256Hex
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.ImportEvidence
import app.melotrail.arrangement.SourceKeyEvidence
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.StructureVariationOverrides
import app.melotrail.commercial.SourceRightsAttestation
import app.melotrail.music.MusicalKey
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionError
import app.melotrail.preparation.InputInspectionErrorCode
import app.melotrail.preparation.InputInspectionPaths
import app.melotrail.preparation.InputInspectionReportStore
import app.melotrail.preparation.InputInspectionRequest
import app.melotrail.preparation.InputInspectionResult
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InspectionSourceIdentity
import app.melotrail.preparation.PreparationStatus
import app.melotrail.preparation.RunTranscriptionQualityGateRequest
import app.melotrail.preparation.TranscriptionInputArtifact
import app.melotrail.preparation.TranscriptionQualityGateResult
import app.melotrail.preparation.TranscriptionQualityGateService
import app.melotrail.profile.BundledCompositionProfileCatalog
import app.melotrail.profile.CompositionProfileCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.sound.midi.MidiSystem

/** UI- and CLI-neutral boundary for the local, file-backed arranger project. */
interface ProjectApplicationService {
    fun open(root: Path): ProjectSnapshot
    /**
     * The one normalized workflow contract for local UI/CLI adapters.  It
     * exposes canonical lifecycle, approval, job, and artifact-version state;
     * callers must not inspect project files to reconstruct it.
     */
    fun getWorkflowStatus(command: GetWorkflowStatus): WorkflowReadModel =
        WorkflowReadModelDeriver.derive(open(command.root), command.arrangement)
    fun getHarmony(command: GetHarmony): HarmonyView =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun createHarmonyEvent(command: CreateHarmonyEvent): HarmonyMutationResult =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun updateHarmonyEvent(command: UpdateHarmonyEvent): HarmonyMutationResult =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun deleteHarmonyEvent(command: DeleteHarmonyEvent): HarmonyMutationResult =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun reorderHarmonyEvent(command: ReorderHarmonyEvent): HarmonyMutationResult =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun setHarmonyProgression(command: SetHarmonyProgression): HarmonyMutationResult =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun getHarmonySectionContext(command: GetHarmonySectionContext): HarmonySectionContext =
        throw UnsupportedOperationException("This project service does not support harmony.")
    fun getCompositionSettings(command: GetCompositionSettings): GetCompositionSettingsResult =
        throw UnsupportedOperationException("This project service does not support composition settings.")
    fun previewSettingsChange(command: PreviewSettingsChange): PreviewSettingsChangeResult =
        throw UnsupportedOperationException("This project service does not support composition settings.")
    fun updateCompositionSettings(command: UpdateCompositionSettings): UpdateCompositionSettingsResult =
        throw UnsupportedOperationException("This project service does not support composition settings.")
    fun create(request: CreateProjectRequest): ProjectSnapshot
    /** Source-first automatic import. Its result describes the first durable run, never a final media promise. */
    suspend fun importSongPart(command: ImportSongPart): ImportSongPartResult =
        throw UnsupportedOperationException("This project service does not support automatic import.")
    suspend fun importPart(request: ImportPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    /** The one code-owned technical correction boundary. Analysis remains a separate stage. */
    suspend fun cleanMidi(request: CleanMidiRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    fun approveCleanMidi(root: Path, partId: String): ProjectSnapshot
    suspend fun normalizePart(request: NormalizePartRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support MIDI normalization.")
    fun confirmSourceKey(command: ConfirmSourceKey): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support source-key confirmation.")
    suspend fun transposePart(request: TransposePartRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support project-key transposition.")
    fun selectMidiFeel(request: SelectMidiFeelRequest): ProjectSnapshot
    fun selectEnhancement(request: SelectEnhancementRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support enhancement.")
    suspend fun inspectPart(request: InspectPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    suspend fun transcribeAudioPart(request: TranscribeAudioPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support audio transcription.")
    suspend fun analyzePart(request: AnalyzePartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    fun updateSongPartName(request: UpdateSongPartNameRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support song-part names.")
    fun updateSongPartSection(request: UpdateSongPartSectionRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support song-part sections.")
    /** Removes one Melody Parts entry and all of its structural uses. Source and derived files are retained. */
    fun removeSongPart(request: RemoveSongPartRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support removing song parts.")
    fun insertStructureOccurrence(request: InsertStructureOccurrenceRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun duplicateStructureOccurrence(request: DuplicateStructureOccurrenceRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun removeStructureOccurrence(request: RemoveStructureOccurrenceRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun moveStructureOccurrence(request: MoveStructureOccurrenceRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun updateStructureOccurrenceLabel(request: UpdateStructureOccurrenceLabelRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun updateStructureOccurrenceVariation(request: UpdateStructureOccurrenceVariationRequest): ProjectSnapshot =
        throw UnsupportedOperationException("This project service does not support structure occurrences.")
    fun saveStructure(request: SaveStructureRequest): ProjectSnapshot
}

data class GetWorkflowStatus(
    val root: Path,
    /** Arrangement review is an application snapshot owned by the arrangement service. */
    val arrangement: ArrangementSnapshot? = null
)

data class CreateProjectRequest(
    val root: Path,
    val name: String? = null,
    val renderFormat: RenderFormat = RenderFormat()
)

data class ImportPartRequest(
    val root: Path,
    val id: String,
    val source: Path,
    val role: String = "",
    /** New callers use an explicit name and catalog section; role remains input-only compatibility. */
    val name: String = id,
    val sectionType: SectionTypeId = SectionTypeCatalog.fromLegacyRole(role),
    val transcribe: Boolean = false,
    val cleanup: MidiCleanupOptions = MidiCleanupOptions(),
    /** Required by the desktop confirmation UI; null is retained only for legacy CLI compatibility. */
    val sourceAttestation: SourceRightsAttestation? = null
)

/** The fixed, allow-listed default for transparent enhancement processors. */
enum class DefaultEnhancementIntensity { NONE, SUBTLE, STANDARD }

/**
 * One idempotent import intent. [expectedRevision] is zero for a new part; a
 * retry supplies the existing part revision so an outdated client cannot
 * silently overwrite its identity or source evidence.
 */
data class ImportSongPart(
    val root: Path,
    val id: String,
    val file: Path,
    val name: String,
    val sectionType: SectionTypeId,
    val sourceAttestation: SourceRightsAttestation? = null,
    val expectedRevision: Long = 0,
    val defaultEnhancementIntensity: DefaultEnhancementIntensity = DefaultEnhancementIntensity.SUBTLE
)

data class ImportSongPartResult(
    val partId: String,
    val firstRun: StageRunResult,
    val snapshot: ProjectSnapshot
)

/** One Clean MIDI run may choose only an already validated named cleanup profile. */
data class CleanMidiRequest(
    val root: Path,
    val partId: String,
    val cleanup: MidiCleanupOptions
)

data class NormalizePartRequest(val root: Path, val partId: String)
/** Explicit musician decision used whenever detected key evidence is not trusted automatically. */
data class ConfirmSourceKey(val root: Path, val partId: String, val key: MusicalKey, val expectedRevision: Long)
data class TransposePartRequest(val root: Path, val partId: String)

/** Fixed named profile only. This is deliberately not a tempo/swing control surface. */
data class SelectMidiFeelRequest(val root: Path, val partId: String, val input: MidiAnalysisInput)
data class SelectEnhancementRequest(val root: Path, val partId: String, val intensity: app.melotrail.arrangement.EnhancementIntensity = app.melotrail.arrangement.EnhancementIntensity.SUBTLE, val seed: Long = 0L)

data class AnalyzePartRequest(val root: Path, val partId: String)
data class InspectPartRequest(val root: Path, val partId: String)
data class TranscribeAudioPartRequest(val root: Path, val partId: String, val selectedInput: TranscriptionInputArtifact)
data class UpdateSongPartNameRequest(val root: Path, val partId: String, val name: String, val expectedRevision: Long)
data class UpdateSongPartSectionRequest(val root: Path, val partId: String, val sectionType: SectionTypeId, val expectedRevision: Long)
data class RemoveSongPartRequest(val root: Path, val partId: String)
data class InsertStructureOccurrenceRequest(val root: Path, val partId: String, val afterOccurrenceId: String? = null, val expectedRevision: Long? = null)
data class DuplicateStructureOccurrenceRequest(val root: Path, val occurrenceId: String, val expectedRevision: Long)
data class RemoveStructureOccurrenceRequest(val root: Path, val occurrenceId: String, val expectedRevision: Long)
data class MoveStructureOccurrenceRequest(val root: Path, val occurrenceId: String, val afterOccurrenceId: String?, val expectedRevision: Long)
data class UpdateStructureOccurrenceLabelRequest(val root: Path, val occurrenceId: String, val label: String, val expectedRevision: Long)
data class UpdateStructureOccurrenceVariationRequest(val root: Path, val occurrenceId: String, val variationOverrides: StructureVariationOverrides, val expectedRevision: Long)
data class SaveStructureRequest(val root: Path, val partIds: List<String>)

data class OperationProgress(
    val operation: String,
    val stageIndex: Int,
    val stageCount: Int,
    val message: String,
    val artifact: Path? = null
)

fun interface ProgressSink {
    fun report(progress: OperationProgress)

    data object None : ProgressSink {
        override fun report(progress: OperationProgress) = Unit
    }
}

/** Worker boundary needed by the MIDI-first import workflow. */
interface MidiPreparationService {
    suspend fun transcribe(input: Path, output: Path)
    suspend fun clean(input: Path, output: Path)
    /** Compatibility bridge for existing worker fakes; production always receives validated options. */
    suspend fun clean(input: Path, output: Path, options: MidiCleanupOptions) = clean(input, output)
}

data class ProjectSnapshot(
    val root: Path,
    val version: Int,
    val name: String,
    val renderFormat: RenderFormat?,
    val parts: List<PartSummary>,
    val structure: List<StructureSectionSummary>,
    val readiness: ProjectReadiness
)

data class PartSummary(
    val id: String,
    @Deprecated("Use sectionType or name") val role: String,
    val sourceFile: String,
    val sourceName: String,
    val sourceType: PartSourceType,
    val analysis: PartAnalysisSummary?,
    val preparation: PartPreparationSummary = PartPreparationSummary(
        sourcePreserved = false,
        inspected = false,
        preparedAudio = false,
        rawMidi = false,
        cleanMidi = false,
        analyzed = false,
        ready = false,
        warnings = emptyList()
    ),
    /** Read by the application service from the canonical immutable source; UI never touches files. */
    val sourceSizeBytes: Long? = null,
    val name: String = id,
    val sectionType: SectionTypeId = SectionTypeCatalog.fromLegacyRole(role),
    val sourceSha256: String? = null,
    val sourceKeyConfirmed: Boolean = false,
    val sourceKey: SourceKeySummary? = null,
    val revision: Long = 1,
    val warnings: List<String> = emptyList()
)

data class SourceKeySummary(
    val detectedKey: MusicalKey?,
    val confidence: Double,
    val algorithmVersion: String?,
    val confirmedOverride: MusicalKey?,
    val confirmationRequired: Boolean
)

/** Truthful, artifact-derived input state for one canonical project part. */
data class PartPreparationSummary(
    val sourcePreserved: Boolean,
    val inspected: Boolean,
    val preparedAudio: Boolean,
    val rawMidi: Boolean,
    val cleanMidi: Boolean,
    val analyzed: Boolean,
    val ready: Boolean,
    val warnings: List<String>,
    val midiQuality: MidiQualitySummary = MidiQualitySummary.invalid(),
    val technicalCorrection: TechnicalCorrectionSummary = TechnicalCorrectionSummary(),
    val enhancement: EnhancementSummary = EnhancementSummary(),
    val midiFeel: MidiFeelSummary = MidiFeelSummary(),
    val midiAiFix: MidiAiFixSummary = MidiAiFixSummary(),
    val transposedMidi: Boolean = false
)

/** Artifact-derived state for the optional cleaned-vs-approved-AI base branch. */
data class MidiAiFixSummary(
    val selected: MidiAiFixSelection = MidiAiFixSelection.SKIP,
    val draftAvailable: Boolean = false,
    val approvedAvailable: Boolean = false
) {
    val selectedAvailable: Boolean get() = selected == MidiAiFixSelection.SKIP || (selected == MidiAiFixSelection.APPROVED && approvedAvailable)
}

/** Artifact-derived Task 017 status. Legacy AI-fix state remains separate and is never relabelled as correction. */
data class TechnicalCorrectionSummary(
    val selected: app.melotrail.arrangement.TechnicalCorrectionSelection = app.melotrail.arrangement.TechnicalCorrectionSelection.BASE,
    val available: Boolean = false,
    val warnings: List<String> = emptyList(),
    val approvalRequired: Boolean = false
) {
    val selectedAvailable: Boolean get() = selected == app.melotrail.arrangement.TechnicalCorrectionSelection.BASE || available
}

/** Honest task-018 DTO: current selection and capability, never a quality claim. */
data class EnhancementSummary(
    val intensity: app.melotrail.arrangement.EnhancementIntensity = app.melotrail.arrangement.EnhancementIntensity.OFF,
    val selected: app.melotrail.arrangement.EnhancementSelection = app.melotrail.arrangement.EnhancementSelection.CORRECTED,
    val available: Boolean = false,
    /** Rejected and draft evidence remains inspectable but is never a ready choice. */
    val approval: app.melotrail.arrangement.EnhancementApproval? = null,
    val capabilityLabel: String = "MVP placeholder — no musical edits"
) {
    val approvedAvailable: Boolean get() = available && approval == app.melotrail.arrangement.EnhancementApproval.APPROVED
    val selectedAvailable: Boolean get() = selected == app.melotrail.arrangement.EnhancementSelection.CORRECTED || (selected == app.melotrail.arrangement.EnhancementSelection.ENHANCED && approvedAvailable)
}

data class MidiFeelSummary(
    val selected: MidiAnalysisInput = MidiAnalysisInput.CURRENT,
    val available: Boolean = false,
    val report: MidiFeelReport? = null
)

enum class MidiQualityStatus { CURRENT, APPROVAL_REQUIRED, STALE_OR_INVALID }

data class MidiQualitySummary(
    val status: MidiQualityStatus,
    val cleanup: MidiCleanupOptions? = null,
    val warnings: List<MidiQualityWarning> = emptyList(),
    val recommendations: List<MidiQualityRecommendation> = emptyList(),
    /** Current canonical report only; it contains no external source path. */
    val report: MidiQualityReport? = null
) {
    companion object {
        fun invalid() = MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
    }
}

enum class PartSourceType { MIDI, AUDIO, UNKNOWN }
enum class PartAnalysisStatus { NONE, MIDI }

data class PartAnalysisSummary(
    val status: PartAnalysisStatus,
    val file: String,
    val bars: Int? = null,
    val durationSeconds: Double? = null,
    val key: String? = null
)

data class StructureSectionSummary(
    val index: Int,
    val partId: String,
    val occurrence: Int,
    val instanceId: String,
    val durationSeconds: Double?,
    val label: String = instanceId,
    val revision: Long = 1,
    val variationOverrides: StructureVariationOverrides = StructureVariationOverrides()
)

data class ProjectReadiness(
    val cleanMidiReady: Boolean,
    val analysesReady: Boolean,
    val structureReady: Boolean,
    val songPlanAvailable: Boolean,
    val arrangementAvailable: Boolean,
    val generatedMidiAvailable: Boolean,
    val stemsAvailable: Boolean,
    val dryMixAvailable: Boolean,
    val loFiMixAvailable: Boolean,
    val masterAvailable: Boolean,
    val midiQualityReportsReady: Boolean = false,
    /** A final release is complete only when metadata accompanies the validated master. */
    val releaseAvailable: Boolean = false,
    /** Durable invalidation evidence; availability above remains file-derived. */
    val staleArtifacts: Set<app.melotrail.arrangement.WorkflowArtifact> = emptySet(),
    val cohesionApprovalRequired: Boolean = false,
    /** True only for a complete, approved, non-stale arrangement-aware boundary result. */
    val cohesionReady: Boolean = false,
    /** Creator attestation is evidence only; absence always blocks commercial-ready status. */
    val commercialSourceAttestationsComplete: Boolean = false,
    /** Missing or catalog-incompatible settings block creative derivation, not source inspection or historical export. */
    val compositionSettingsReady: Boolean = true,
    /** Required progression completeness is independent from setup persistence. */
    val harmonyReady: Boolean = true,
    /** Durable application state; UI observes this rather than polling files. */
    val stageRuns: List<StageRunSnapshot> = emptyList(),
    /** A current critic report is required before an enhancement selection is meaningful. */
    val criticAvailable: Boolean = false,
    val fullSongEnhancementSelection: app.melotrail.arrangement.FullSongEnhancementSelection = app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED,
    /** True only when the approved candidate's exact inputs and outputs are current. */
    val fullSongEnhancementAvailable: Boolean = false,
    val humanizationSelection: app.melotrail.arrangement.HumanizationSelection = app.melotrail.arrangement.HumanizationSelection.BYPASS,
    val humanizationAvailable: Boolean = false
)

class DefaultProjectApplicationService(
    private val midiPreparation: MidiPreparationService,
    private val midiPartAnalyzer: MidiPartAnalyzer = MidiPartAnalyzer(),
    private val midiQualityReporter: MidiQualityReporter = MidiQualityReporter(),
    private val midiFeelTransformer: MidiLoFiFeelTransformer = MidiLoFiFeelTransformer(),
    private val inputInspection: InputInspectionBoundary = object : InputInspectionBoundary {
        override suspend fun inspect(request: InputInspectionRequest): InputInspectionResult =
            InputInspectionResult.Rejected(InputInspectionError(
                InputInspectionErrorCode.MEASUREMENT_FAILED,
                "Input inspection is not configured."
            ))
    },
    private val transcriptionQualityGate: TranscriptionQualityGateService? = null,
    private val compositionProfiles: CompositionProfileCatalog = BundledCompositionProfileCatalog.load(),
    private val stageRunRecovery: ProjectOpenStageRunRecovery = StageRunner(StageProcessorRegistry(emptyList())),
    private val automaticImportRunner: StageRunner? = null
) : ProjectApplicationService {
    private val compositionSettings = CompositionSettingsApplicationService(compositionProfiles)
    private val harmony = HarmonyApplicationService()

    override fun open(root: Path): ProjectSnapshot {
        val normalizedRoot = root.normalizeRoot()
        require(Files.isRegularFile(normalizedRoot.resolve(ProjectStore.FILE_NAME))) {
            "Project file not found: ${normalizedRoot.resolve(ProjectStore.FILE_NAME)}"
        }
        stageRunRecovery.recover(normalizedRoot)
        val project = readValidProject(normalizedRoot)
        return snapshot(normalizedRoot, project)
    }

    override fun getCompositionSettings(command: GetCompositionSettings): GetCompositionSettingsResult {
        val root = command.root.normalizeRoot()
        return compositionSettings.query(readValidProject(root))
    }

    override fun getHarmony(command: GetHarmony): HarmonyView {
        val root = command.root.normalizeRoot()
        return harmony.query(readValidProject(root))
    }

    override fun createHarmonyEvent(command: CreateHarmonyEvent): HarmonyMutationResult = mutate(command.root) { root ->
        val prepared = harmony.create(readValidProject(root), command)
        ProjectStore.write(root, prepared.project)
        HarmonyMutationResult(snapshot(root, prepared.project), prepared.harmony, prepared.invalidation)
    }

    override fun updateHarmonyEvent(command: UpdateHarmonyEvent): HarmonyMutationResult = mutate(command.root) { root ->
        val prepared = harmony.update(readValidProject(root), command)
        ProjectStore.write(root, prepared.project)
        HarmonyMutationResult(snapshot(root, prepared.project), prepared.harmony, prepared.invalidation)
    }

    override fun deleteHarmonyEvent(command: DeleteHarmonyEvent): HarmonyMutationResult = mutate(command.root) { root ->
        val prepared = harmony.delete(readValidProject(root), command)
        ProjectStore.write(root, prepared.project)
        HarmonyMutationResult(snapshot(root, prepared.project), prepared.harmony, prepared.invalidation)
    }

    override fun reorderHarmonyEvent(command: ReorderHarmonyEvent): HarmonyMutationResult = mutate(command.root) { root ->
        val prepared = harmony.reorder(readValidProject(root), command)
        ProjectStore.write(root, prepared.project)
        HarmonyMutationResult(snapshot(root, prepared.project), prepared.harmony, prepared.invalidation)
    }

    override fun setHarmonyProgression(command: SetHarmonyProgression): HarmonyMutationResult = mutate(command.root) { root ->
        val prepared = harmony.setProgression(readValidProject(root), command)
        ProjectStore.write(root, prepared.project)
        HarmonyMutationResult(snapshot(root, prepared.project), prepared.harmony, prepared.invalidation)
    }

    override fun getHarmonySectionContext(command: GetHarmonySectionContext): HarmonySectionContext {
        val root = command.root.normalizeRoot()
        return harmony.context(readValidProject(root), command.sectionType)
    }

    override fun previewSettingsChange(command: PreviewSettingsChange): PreviewSettingsChangeResult {
        val root = command.root.normalizeRoot()
        return compositionSettings.preview(readValidProject(root), command.expectedRevision, command.settings)
    }

    override fun updateCompositionSettings(command: UpdateCompositionSettings): UpdateCompositionSettingsResult = mutate(command.root) { root ->
        val prepared = compositionSettings.update(readValidProject(root), command.expectedRevision, command.settings)
        ProjectStore.write(root, prepared.project)
        UpdateCompositionSettingsResult(snapshot(root, prepared.project), prepared.preview.candidate, prepared.preview.invalidation)
    }

    override fun create(request: CreateProjectRequest): ProjectSnapshot = mutate(request.root) { root ->
        require(!Files.exists(root) || Files.isDirectory(root)) { "Project path is not a directory: $root" }
        require(!Files.exists(root.resolve(ProjectStore.FILE_NAME))) { "Project already exists: ${root.resolve(ProjectStore.FILE_NAME)}" }
        val name = request.name ?: root.fileName?.toString().orEmpty()
        require(name.isNotBlank()) { "Project directory must have a name" }
        listOf("source", "midi/raw", "midi/clean", "midi/quality", "midi/normalized", "midi/normalization", "midi/transposed", "midi/transposition", "midi/derived", "midi/feel", "midi/generated").forEach { Files.createDirectories(root.resolve(it)) }
        val project = ProjectStore.create(root, name, request.renderFormat)
        snapshot(root, project)
    }

    override suspend fun importSongPart(command: ImportSongPart): ImportSongPartResult {
        val runner = requireNotNull(automaticImportRunner) { "Automatic import is not configured." }
        require(PART_ID.matches(command.id)) { "Part ID must contain only letters, numbers, underscores, or hyphens: ${command.id}" }
        require(command.name.isNotBlank() && command.name.length <= 120 && command.name.none { it.isISOControl() }) { "Part name is invalid" }
        require(command.expectedRevision >= 0) { "Expected part revision must not be negative" }
        val root = command.root.normalizeRoot()
        val file = command.file.toAbsolutePath().normalize()
        require(Files.isRegularFile(file)) { "Input file not found: $file" }
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_EXTENSIONS) { "Unsupported input file extension: ${if (extension.isEmpty()) "(none)" else extension}" }
        requireImportSourceFormat(file, extension, extension in MIDI_EXTENSIONS)
        val sourceSha256 = sha256(file)
        val registration = withContext(Dispatchers.IO) {
            val lock = ProjectMutationCoordinator.lock(root)
            require(lock.tryLock()) { "Another project mutation is already running: $root" }
            try {
                val project = readValidProject(root)
                require(project.version == Project.CURRENT_VERSION) { "Unsupported project version: ${project.version}." }
                val existing = project.parts.firstOrNull { it.id.equals(command.id, ignoreCase = true) }
                if (existing != null) {
                    require(existing.id == command.id && existing.revision == command.expectedRevision) {
                        "Part '${existing.id}' changed; refresh before retrying import."
                    }
                    require(existing.file == "source/${command.id}.$extension" && Files.isRegularFile(root.resolve(existing.file)) &&
                        sha256(root.resolve(existing.file)) == sourceSha256) { "Part '${command.id}' already has different import evidence." }
                    val sourceRecord = StageRunStore().read(root, project.envelope.stageRuns).lastOrNull {
                        it.stage == StageId.SOURCE && it.subject == StageSubject.Part(command.id) && it.status == StageRunStatus.COMPLETED
                    } ?: throw IllegalArgumentException("Part '${command.id}' has no durable source stage; re-import with a new part ID.")
                    return@withContext SourceRegistration(artifactRef(root, existing.file))
                }
                require(command.expectedRevision == 0L) { "New parts require expected revision 0." }
                val relative = "source/${command.id}.$extension"
                val destination = safeDestination(root, relative)
                require(file != destination && !(Files.exists(destination) && Files.isSameFile(file, destination))) { "Input and destination paths must differ" }
                publishImmutableSource(file, destination, sourceSha256)
                requireImportSourceFormat(destination, extension, extension in MIDI_EXTENSIONS)
                require(sha256(destination) == sourceSha256) { "Preserved source changed during import" }

                val store = StageRunStore()
                val initialReference = project.envelope.stageRuns.takeIf { it.index != null } ?: store.initialize(root)
                val sourceArtifact = artifactRef(root, relative)
                val sourceRunId = "import-${command.id}-${sourceSha256.take(16)}"
                val sourceRecordedAt = Instant.now().toString()
                val sourceRecord = StageRunRecord(
                    runId = sourceRunId,
                    stage = StageId.SOURCE,
                    subject = StageSubject.Part(command.id),
                    status = StageRunStatus.COMPLETED,
                    processor = ProcessorIdentity("source-import", "1"),
                    createdAt = sourceRecordedAt,
                    finishedAt = sourceRecordedAt,
                    outputArtifacts = listOf(sourceArtifact)
                )
                val reference = store.transition(root, initialReference, sourceRecord)
                val pending = SongPart(
                    id = command.id,
                    file = relative,
                    name = command.name,
                    sectionType = command.sectionType,
                    sourceAttestation = command.sourceAttestation,
                    importPending = true
                )
                val saved = project.copy(
                    parts = project.parts + pending,
                    workflow = project.workflow.invalidate(WorkflowChange.SOURCE_OR_RAW),
                    envelope = project.envelope.copy(stageRuns = reference)
                )
                ProjectStore.write(root, saved)
                SourceRegistration(sourceArtifact)
            } finally { lock.unlock() }
        }
        val configuration = sha256Hex("automatic-import|${command.defaultEnhancementIntensity.name}")
        val firstRun = runner.run(RunStage(
            root = root,
            stage = StageId.EXTRACTED,
            subject = StageSubject.Part(command.id),
            inputArtifacts = listOf(registration.source),
            configurationSha256 = configuration
        ))
        require(firstRun.snapshot.status == StageRunStatus.COMPLETED) {
            "Audio/MIDI extraction failed; inspect the recorded stage failure before retrying."
        }
        if (command.file.fileName.toString().substringAfterLast('.', "").lowercase() !in MIDI_EXTENSIONS) {
            val cleanup = runner.run(RunStage(
                root = root,
                stage = StageId.CLEANED,
                subject = StageSubject.Part(command.id),
                configurationSha256 = sha256Hex("transcription-cleanup|${TranscriptionCleanupProfile.DEFAULT}")
            ))
            require(cleanup.snapshot.status == StageRunStatus.COMPLETED) {
                "Mandatory transcription cleanup failed; raw MIDI remains available for inspection."
            }
        }
        return ImportSongPartResult(command.id, firstRun, open(root))
    }

    override suspend fun importPart(request: ImportPartRequest, progress: ProgressSink): ProjectSnapshot {
        if (automaticImportRunner != null) {
            val extension = request.source.fileName.toString().substringAfterLast('.', "").lowercase()
            if (extension !in MIDI_EXTENSIONS) require(request.transcribe) { "Audio input requires --transcribe so immutable raw MIDI can be prepared" }
            progress.report(OperationProgress("import-part", 1, 1, "Starting automatic import", request.source))
            return importSongPart(ImportSongPart(
                root = request.root,
                id = request.id,
                file = request.source,
                name = request.name,
                sectionType = request.sectionType,
                sourceAttestation = request.sourceAttestation
            )).snapshot
        }
        val isAudio = request.source.fileName.toString().substringAfterLast('.', "").lowercase() !in MIDI_EXTENSIONS
        val imported = mutateSuspend(request.root) { root ->
        require(PART_ID.matches(request.id)) { "Part ID must contain only letters, numbers, underscores, or hyphens: ${request.id}" }
        val source = request.source.toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Input file not found: $source" }
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_EXTENSIONS) { "Unsupported input file extension: ${if (extension.isEmpty()) "(none)" else extension}" }
        val isMidi = extension in MIDI_EXTENSIONS
        requireImportSourceFormat(source, extension, isMidi)
        require(!(isMidi && request.transcribe)) { "--transcribe is only valid for audio input" }
        val project = readValidProject(root)
        require(isMidi || request.transcribe) { "Audio input requires --transcribe so immutable raw MIDI can be prepared" }
        val sourceSha256 = sha256(source)
        val existingPart = project.parts.firstOrNull { it.id.equals(request.id, ignoreCase = true) }
        if (existingPart != null) {
            require(existingPart.id == request.id) { "Part ID collides with existing part '${existingPart.id}': ${request.id}" }
            require(currentImportMatches(root, existingPart, sourceSha256, extension)) {
                "Part ID already exists with different or stale import evidence: ${request.id}"
            }
            progress.report(OperationProgress("import-part", 1, 1, "Reusing current unified import", root.resolve(ProjectStore.FILE_NAME)))
            return@mutateSuspend snapshot(root, project)
        }
        require(isMidi || request.transcribe) { "Audio input requires --transcribe so immutable raw MIDI can be prepared" }
        val relativeFile = "source/${request.id}.$extension"
        val destination = safeDestination(root, relativeFile)
        require(source != destination && !(Files.exists(destination) && Files.isSameFile(source, destination))) {
            "Input and destination paths must differ"
        }
        progress.report(OperationProgress("import-part", 1, if (isMidi) 3 else 4, if (Files.exists(destination)) "Reusing preserved source" else "Copying source", destination))
        publishImmutableSource(source, destination, sourceSha256)
        requireImportSourceFormat(destination, extension, isMidi)
        require(sha256(destination) == sourceSha256) { "Preserved source changed during import: $destination" }

        val raw = "midi/raw/${request.id}.mid"
        val rawPath = safeDestination(root, raw)
        val rawWork = temporaryMidi(rawPath)
        try {
            if (isMidi && Files.isRegularFile(rawPath) && sha256(rawPath) == sourceSha256) {
                requireMidiArtifact(rawPath, "Existing raw MIDI")
                require(Files.mismatch(destination, rawPath) == -1L) { "Existing raw MIDI is not byte-identical to its preserved source" }
                progress.report(OperationProgress("import-part", 2, 3, "Reusing immutable raw MIDI", rawPath))
            } else {
                if (isMidi) {
                    progress.report(OperationProgress("import-part", 2, 3, "Publishing immutable raw MIDI", rawPath))
                    Files.copy(destination, rawWork)
                    requireMidiArtifact(rawWork, "MIDI import")
                    require(sha256(rawWork) == sourceSha256) { "Direct MIDI publication changed the imported bytes" }
                    atomicReplace(rawWork, rawPath, "raw MIDI import")
                } else {
                    progress.report(OperationProgress("import-part", 2, 4, "Validating eligible solo-piano audio", destination))
                    inspectAudioImport(root, request.id, relativeFile, destination, extension)
                    progress.report(OperationProgress("import-part", 3, 4, "Transcribing eligible solo-piano audio", rawPath))
                    runTranscriptionGate(root, request.id, TranscriptionInputArtifact.SOURCE)
                }
            }
            requireMidiArtifact(rawPath, if (isMidi) "MIDI import" else "Transcription")
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Part '${request.id}' was not registered. Source preserved at $destination; raw MIDI publication failed: ${exception.message}",
                exception
            )
        } finally {
            Files.deleteIfExists(rawWork)
        }
        val rawSha256 = sha256(rawPath)
        require(sha256(destination) == sourceSha256) { "Preserved source changed before project registration" }

        val updated = project.copy(
            parts = project.parts + SongPart(
                id = request.id,
                file = relativeFile,
                name = request.name,
                sectionType = request.sectionType,
                midi = MidiReferences(raw = raw),
                sourceAttestation = request.sourceAttestation,
                importEvidence = ImportEvidence(sourceSha256, rawSha256)
            ),
            workflow = project.workflow.invalidate(WorkflowChange.SOURCE_OR_RAW)
        )
        val saved = updated.also { ProjectStore.write(root, it) }
        val finalStage = if (isMidi) 3 else 4
        progress.report(OperationProgress("import-part", finalStage, finalStage, "Registered raw MIDI; Clean MIDI is next", root.resolve(ProjectStore.FILE_NAME)))
        snapshot(root, saved)
    }
        return if (isAudio) {
            progress.report(OperationProgress("import-part", 4, 5, "Applying mandatory transcription cleanup", request.root.resolve("midi/clean/${request.id}.mid")))
            cleanMidi(CleanMidiRequest(request.root, request.id, TranscriptionCleanupProfile.DEFAULT.toMidiCleanupOptions()), progress)
        } else imported
    }

    override suspend fun cleanMidi(request: CleanMidiRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        request.cleanup.requireValid()
        val project = readProjectForCleanMidi(root, request.partId)
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${request.partId}' has no raw MIDI artifact to clean." }
        val rawReference = requireNotNull(midi.raw) {
            "Part '${request.partId}' predates explicit raw MIDI evidence. Re-import it before cleaning."
        }
        val rawPath = safeDestination(root, rawReference)
        val cleanPath = safeDestination(root, "midi/clean/${request.partId}.mid")
        requireMidiArtifact(rawPath, "Raw MIDI")
        requireCurrentImportEvidence(root, part)
        val rawSha256 = sha256(rawPath)

        val rawWork = temporaryMidi(rawPath)
        val cleanWork = temporaryMidi(cleanPath)
        val cleanBackup = cleanPath.takeIf(Files::isRegularFile)?.let(::temporaryMidi)
        val reportPath = MidiQualityReportStore.path(root, request.partId)
        val reportBackup = reportPath.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
        cleanBackup?.let { Files.copy(cleanPath, it, StandardCopyOption.REPLACE_EXISTING) }
        var cleanPublished = false
        var reportPublished = false
        try {
            Files.copy(rawPath, rawWork)
            progress.report(OperationProgress("clean-midi", 1, 3, "Cleaning MIDI with ${request.cleanup.profile.name.lowercase().replace('_', '-')}", cleanPath))
            midiPreparation.clean(rawWork, cleanWork, request.cleanup)
            require(sha256(rawPath) == rawSha256) { "Raw MIDI changed during Clean MIDI" }
            requireMidiArtifact(cleanWork, "MIDI cleanup")
            val report = midiQualityReporter.report(request.partId, rawPath, cleanWork, request.cleanup)
            require(report.raw.sha256 == rawSha256 && sha256(rawPath) == rawSha256) {
                "Raw MIDI changed while producing Clean MIDI quality evidence"
            }
            atomicReplace(cleanWork, cleanPath, "MIDI cleanup")
            cleanPublished = true
            val publishedReport = MidiQualityReportStore.write(root, report)
            reportPublished = true
            val qualityReference = root.relativize(publishedReport).toString().replace('\\', '/')
            // Melody Parts is a single-action pipeline: validated cleanup evidence is
            // recorded as approved here rather than exposing a second review control.
            val approval = MidiQualityReportStore.approval(root, qualityReference, report)
            val updated = project.copy(parts = project.parts.map {
                if (it.id == request.partId) it.copy(
                    analysis = null,
                    sourceKeyEvidence = null,
                    midi = midi.copy(
                        clean = root.relativize(cleanPath).toString().replace('\\', '/'),
                        cleanup = request.cleanup,
                        quality = qualityReference,
                        normalized = null,
                        normalization = null,
                        transposed = null,
                        transposition = null,
                        cleanApproval = approval,
                        technicalCorrectionSelection = TechnicalCorrectionSelection.BASE,
                        technicalCorrection = null,
                        aiFixSelection = MidiAiFixSelection.PENDING,
                        aiFix = null,
                        enhancementSelection = app.melotrail.arrangement.EnhancementSelection.PENDING,
                        enhancement = null,
                        analysisInput = MidiAnalysisInput.CURRENT,
                        feel = null
                    )
                ) else it
            }, workflow = project.workflow.invalidate(WorkflowChange.CLEANED_MIDI).markCurrent(WorkflowArtifact.CLEAN_MIDI))
            ProjectStore.write(root, updated)
            progress.report(OperationProgress("clean-midi", 3, 3, "Saved Clean MIDI quality report; analyze this part again", publishedReport))
            snapshot(root, updated)
        } catch (failure: Exception) {
            if (cleanPublished) runCatching {
                if (cleanBackup == null) Files.deleteIfExists(cleanPath)
                else atomicReplace(cleanBackup, cleanPath, "MIDI cleanup rollback")
            }
            if (reportPublished) runCatching {
                if (reportBackup == null) Files.deleteIfExists(reportPath)
                else atomicWrite(reportPath, reportBackup)
            }
            throw failure
        } finally {
            Files.deleteIfExists(rawWork)
            Files.deleteIfExists(cleanWork)
            cleanBackup?.let(Files::deleteIfExists)
        }
    }

    override fun approveCleanMidi(root: Path, partId: String): ProjectSnapshot = mutate(root) { projectRoot ->
        val project = readValidProject(projectRoot)
        val part = project.parts.find { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no MIDI cleanup." }
        val raw = requireNotNull(midi.raw) { "Part '$partId' predates explicit MIDI cleanup evidence." }
        val clean = requireNotNull(midi.clean) { "Part '$partId' has no cleaned MIDI." }
        val cleanup = requireNotNull(midi.cleanup) { "Part '$partId' has no MIDI cleanup report." }
        val quality = requireNotNull(midi.quality) { "Part '$partId' has no MIDI cleanup report." }
        MidiQualityReportStore.requireCurrent(projectRoot, partId, raw, clean, cleanup, quality)
        val report = MidiQualityReportStore.read(projectRoot, quality)
        require(report.approvalRequired) { "Part '$partId' does not require Clean MIDI approval." }
        val approval = MidiQualityReportStore.approval(projectRoot, quality, report)
        val updated = project.copy(parts = project.parts.map {
            if (it.id == partId) it.copy(midi = midi.copy(cleanApproval = approval)) else it
        })
        ProjectStore.write(projectRoot, updated)
        snapshot(projectRoot, updated)
    }

    override suspend fun normalizePart(request: NormalizePartRequest): ProjectSnapshot {
        val runner = requireNotNull(automaticImportRunner) { "MIDI normalization is not configured." }
        val root = request.root.normalizeRoot()
        val project = readValidProject(root)
        val part = project.parts.singleOrNull { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI evidence." }
        val clean = requireNotNull(midi.clean) { "Clean MIDI before normalization." }
        val raw = requireNotNull(midi.raw) { "Part '${part.id}' has no raw MIDI evidence." }
        val quality = requireNotNull(midi.quality) { "Part '${part.id}' has no Clean MIDI quality report." }
        MidiQualityReportStore.requireCurrent(root, part.id, raw, clean, requireNotNull(midi.cleanup), quality)
        require(MidiQualityReportStore.isApproved(root, quality, midi.cleanApproval)) {
            "Review and approve Clean MIDI before normalization."
        }
        val config = MidiNormalizationPolicy.resolve(project, compositionProfiles)
        runner.run(RunStage(
            root = root,
            stage = StageId.NORMALIZED,
            subject = StageSubject.Part(part.id),
            inputArtifacts = listOf(artifactRef(root, clean)),
            configurationSha256 = config.sha256()
        ))
        return open(root)
    }

    override fun confirmSourceKey(command: ConfirmSourceKey): ProjectSnapshot = mutate(command.root) { root ->
        require(command.key.isExecutable) { "Confirmed source key is not executable." }
        val project = readValidProject(root)
        val part = project.parts.singleOrNull { it.id == command.partId } ?: throw IllegalArgumentException("Part not found: ${command.partId}")
        require(part.revision == command.expectedRevision) { "Part '${part.id}' changed; refresh before confirming its source key." }
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI evidence." }
        val normalized = requireNotNull(midi.normalized) { "Normalize MIDI before confirming its source key." }
        val evidence = part.sourceKeyEvidence ?: SourceKeyEvidence()
        require(evidence.inputSha256 == null || evidence.inputSha256 == sha256(safeDestination(root, normalized))) {
            "Detected source-key evidence is stale; normalize MIDI again."
        }
        val updated = project.copy(
            parts = project.parts.map {
                if (it.id == part.id) it.copy(
                    sourceKeyEvidence = evidence.copy(confirmedOverride = command.key),
                    analysis = null,
                    revision = it.revision + 1,
                    midi = midi.copy(transposed = null, transposition = null, aiFixSelection = MidiAiFixSelection.PENDING, aiFix = null,
                        analysisInput = MidiAnalysisInput.CURRENT, feel = null)
                ) else it
            },
            workflow = project.workflow.invalidate(WorkflowChange.SOURCE_KEY)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override suspend fun transposePart(request: TransposePartRequest): ProjectSnapshot {
        val runner = requireNotNull(automaticImportRunner) { "Project-key transposition is not configured." }
        val root = request.root.normalizeRoot()
        val project = readValidProject(root)
        val part = project.parts.singleOrNull { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi)
        val normalized = requireNotNull(midi.normalized) { "Normalize MIDI before transposition." }
        val source = part.sourceKeyEvidence?.effectiveKey
            ?: throw IllegalStateException("Confirm the detected source key before transposition.")
        val target = project.envelope.compositionSettings?.takeIf { it.complete }?.key
            ?: throw IllegalStateException("Complete project Setup before transposition.")
        val input = artifactRef(root, normalized)
        val configuration = sha256Hex("${source.tonic.chromatic}|${source.modeId.value}|${target.tonic.chromatic}|${target.modeId.value}|${SourceKeyEvidence.CONFIDENCE_THRESHOLD}")
        runner.run(RunStage(root, StageId.TRANSPOSED, StageSubject.Part(part.id), inputArtifacts = listOf(input), configurationSha256 = configuration))
        return open(root)
    }

    override fun selectMidiFeel(request: SelectMidiFeelRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no cleaned MIDI." }
        val clean = requireNotNull(midi.clean) { "Part '${part.id}' has no cleaned MIDI. Run Clean MIDI first." }
        val raw = requireNotNull(midi.raw) { "Part '${part.id}' has incomplete cleanup provenance. Re-import it before selecting Lo-fi Feel." }
        MidiQualityReportStore.requireCurrent(root, part.id, raw, clean, requireNotNull(midi.cleanup), requireNotNull(midi.quality))
        require(MidiQualityReportStore.isApproved(root, requireNotNull(midi.quality), midi.cleanApproval)) {
            "Part '${part.id}' Clean MIDI requires current approval before selecting Lo-fi Feel."
        }
        val selectedMidi = when (request.input) {
            MidiAnalysisInput.CURRENT -> midi.copy(analysisInput = MidiAnalysisInput.CURRENT)
            MidiAnalysisInput.LOFI_FEEL -> {
                val profile = MidiFeelProfile.LOFI_80_SWING_V1
                val derived = MidiFeelReportStore.derivedPath(root, part.id, profile)
                val reportPath = MidiFeelReportStore.reportPath(root, part.id, profile)
                val baseProject = project.copy(parts = project.parts.map {
                    if (it.id == part.id) it.copy(midi = midi.copy(analysisInput = MidiAnalysisInput.CURRENT)) else it
                })
                val base = app.melotrail.arrangement.SelectedMidiArtifactResolver().resolve(root, baseProject, part.id)
                val existing = midi.feel?.takeIf { it.profile == profile && MidiFeelReportStore.isCurrent(root, part.id, base.projectRelativePath, it) }
                val feel = existing ?: publishMidiFeel(root, part.id, base.projectRelativePath, derived, reportPath, profile)
                midi.copy(analysisInput = MidiAnalysisInput.LOFI_FEEL, feel = feel)
            }
        }
        if (selectedMidi == midi) return@mutate snapshot(root, project)
        val updated = project.copy(
            parts = project.parts.map { if (it.id == part.id) it.copy(analysis = null, midi = selectedMidi) else it },
            workflow = project.workflow.invalidate(WorkflowChange.MIDI_FEEL).markCurrent(WorkflowArtifact.MIDI_FEEL)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    /** Bounded Task 018 enhancement: Off never calls a planner/processor; the MVP other modes are explicitly no-op. */
    override fun selectEnhancement(request: SelectEnhancementRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.singleOrNull { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no MIDI." }
        require(request.intensity == app.melotrail.arrangement.EnhancementIntensity.OFF) {
            "Create a reviewable enhancement draft through EnhancementApplicationService; selection never invokes a model."
        }
        require(midi.technicalCorrectionSelection == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) {
            "Select the corrected MIDI baseline before enhancement."
        }
        val correction = requireNotNull(midi.technicalCorrection) { "Part '${part.id}' has no corrected MIDI evidence." }
        correction.requireCanonical(part.id)
        val corrected = safeDestination(root, correction.output.file)
        require(sha256(corrected) == correction.output.sha256) { "Corrected MIDI is stale; recreate correction before enhancement." }
        if (request.intensity == app.melotrail.arrangement.EnhancementIntensity.OFF) {
            if (midi.enhancementSelection == app.melotrail.arrangement.EnhancementSelection.CORRECTED && midi.analysisInput == MidiAnalysisInput.CURRENT) return@mutate snapshot(root, project)
            val updated = project.copy(parts = project.parts.map {
                if (it.id == part.id) it.copy(analysis = null, midi = midi.copy(enhancementSelection = app.melotrail.arrangement.EnhancementSelection.CORRECTED, analysisInput = MidiAnalysisInput.CURRENT)) else it
            }, workflow = project.workflow.invalidate(WorkflowChange.ENHANCEMENT_SELECTION))
            ProjectStore.write(root, updated)
            return@mutate snapshot(root, updated)
        }
        val context = app.melotrail.arrangement.MusicalProcessingContextFactory.build(
            MusicalAuthorityBuilder().partEnhancement(root, part.id), corrected, request.intensity, request.seed,
            profiles = compositionProfiles
        )
        val destination = root.resolve(app.melotrail.arrangement.EnhancementArtifactPaths.output(part.id, context.contextSha256))
        val reportPath = root.resolve(app.melotrail.arrangement.EnhancementArtifactPaths.report(part.id, context.contextSha256))
        val existing = midi.enhancement?.takeIf { refs ->
            refs.intensity == request.intensity && refs.contextSha256 == context.contextSha256 && refs.input == correction.output &&
                runCatching { sha256(safeDestination(root, refs.output.file)) == refs.output.sha256 && sha256(safeDestination(root, refs.report.file)) == refs.report.sha256 }.getOrDefault(false)
        }
        val references = existing ?: run {
            val outputTemp = destination.resolveSibling(".${destination.fileName}.tmp")
            val reportTemp = reportPath.resolveSibling(".${reportPath.fileName}.tmp")
            try {
                val processor = app.melotrail.arrangement.TransparentNoOpEnhancementProcessor()
                val report = app.melotrail.arrangement.EnhancementExecutionService(processor, processor).enhance(corrected, outputTemp, context)
                Files.createDirectories(requireNotNull(reportTemp.parent))
                Files.writeString(reportTemp, kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(app.melotrail.arrangement.EnhancementEditReport.serializer(), report))
                atomicReplace(outputTemp, destination, "enhanced MIDI")
                atomicReplace(reportTemp, reportPath, "enhancement report")
                app.melotrail.arrangement.EnhancementReferences(request.intensity, correction.output,
                    app.melotrail.arrangement.WorkflowArtifactReference(root.relativize(destination).toString().replace('\\', '/'), sha256(destination)),
                    app.melotrail.arrangement.WorkflowArtifactReference(root.relativize(reportPath).toString().replace('\\', '/'), sha256(reportPath)), context.contextSha256)
            } finally { Files.deleteIfExists(outputTemp); Files.deleteIfExists(reportTemp) }
        }
        val updated = project.copy(parts = project.parts.map {
            if (it.id == part.id) it.copy(analysis = null, midi = midi.copy(enhancementSelection = app.melotrail.arrangement.EnhancementSelection.ENHANCED, enhancement = references, analysisInput = MidiAnalysisInput.CURRENT)) else it
        }, workflow = project.workflow.invalidate(WorkflowChange.ENHANCEMENT_SELECTION).markCurrent(WorkflowArtifact.ENHANCED_MIDI))
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override suspend fun analyzePart(request: AnalyzePartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val selected = app.melotrail.arrangement.SelectedMidiArtifactResolver().resolve(root, project, part)
        val analysisMidi = selected.path
        progress.report(OperationProgress("analyze-part", 1, 2, "Analyzing ${if (selected.kind == app.melotrail.arrangement.SelectedMidiArtifactKind.LOFI_FEEL) "Lo-fi MIDI Feel" else "Original MIDI"}", analysisMidi))
        val analysisPath = MidiAnalysisStore.write(root, project, request.partId, midiPartAnalyzer.analyze(analysisMidi, request.partId))
        progress.report(OperationProgress("analyze-part", 2, 2, "Saved analysis", analysisPath))
        snapshot(root, readValidProject(root))
    }

    override suspend fun inspectPart(request: InspectPartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        require(part.file.startsWith("source/")) {
            "Part '${part.id}' has no canonical source/ artifact and cannot be inspected."
        }
        val sourcePath = safeDestination(root, part.file)
        require(Files.isRegularFile(sourcePath)) { "Part source is missing: ${part.file}" }
        requireCurrentImportEvidence(root, part)
        val source = InspectionSourceIdentity(part.file, sha256(sourcePath))
        val inspectionRequest = InputInspectionRequest(root, part.id, source).also { it.requireValid() }

        val existing = runCatching { InputInspectionReportStore.read(root, part.id) }.getOrNull()
        if (existing?.source == source) {
            progress.report(OperationProgress("inspect-part", 1, 1, "Reusing current inspection report", InputInspectionPaths.report(root, part.id)))
            return@mutateSuspend snapshot(root, project)
        }

        progress.report(OperationProgress("inspect-part", 1, 2, "Inspecting preserved source", sourcePath))
        val result = inputInspection.inspect(inspectionRequest)
        val report = when (result) {
            is InputInspectionResult.Inspected -> result.report
            is InputInspectionResult.Rejected -> {
                result.error.requireValid()
                throw IllegalStateException("Input inspection failed (${result.error.code}): ${result.error.message}")
            }
        }
        require(report.partId == part.id && report.source == source) {
            "Input inspection returned a report for a different project source."
        }
        report.requireValid()
        InputInspectionReportStore.write(root, report)
        progress.report(OperationProgress("inspect-part", 2, 2, "Saved inspection report", InputInspectionPaths.report(root, part.id)))
        snapshot(root, project)
    }

    override suspend fun transcribeAudioPart(
        request: TranscribeAudioPartRequest,
        progress: ProgressSink
    ): ProjectSnapshot {
        mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        require(sourceType(part.file) == PartSourceType.AUDIO) { "Part '${part.id}' is not an eligible audio source." }
        requireCurrentImportEvidence(root, part)
        val rawReference = requireNotNull(part.midi?.raw) { "Part '${part.id}' has no canonical raw MIDI reference." }
        require(rawReference == "midi/raw/${part.id}.mid") { "Part '${part.id}' raw MIDI reference is not canonical." }

        progress.report(OperationProgress("transcribe-audio", 1, 2, "Running transcription quality gate", safeDestination(root, rawReference)))
        val rawPath = runTranscriptionGate(root, part.id, request.selectedInput)
        requireMidiArtifact(rawPath, "Transcription")
        val sourcePath = safeDestination(root, part.file)
        val sourceSha256 = sha256(sourcePath)
        require(sourceSha256 == part.importEvidence?.sourceSha256) { "Preserved source changed during transcription." }
        val refreshed = part.copy(
            analysis = null,
            midi = MidiReferences(raw = rawReference),
            importEvidence = ImportEvidence(sourceSha256, sha256(rawPath))
        )
        val updated = project.copy(
            parts = project.parts.map { if (it.id == part.id) refreshed else it },
            workflow = project.workflow.invalidate(WorkflowChange.SOURCE_OR_RAW)
        )
        ProjectStore.write(root, updated)
        progress.report(OperationProgress("transcribe-audio", 2, 2, "Registered validated raw MIDI; Clean MIDI is next", root.resolve(ProjectStore.FILE_NAME)))
        snapshot(root, updated)
    }
        progress.report(OperationProgress("transcribe-audio", 2, 3, "Applying mandatory transcription cleanup", request.root.resolve("midi/clean/${request.partId}.mid")))
        return cleanMidi(CleanMidiRequest(request.root, request.partId, TranscriptionCleanupProfile.DEFAULT.toMidiCleanupOptions()), progress)
    }

    override fun updateSongPartName(request: UpdateSongPartNameRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        require(part.revision == request.expectedRevision) { "Part '${part.id}' changed; refresh before updating its name." }
        require(request.name.isNotBlank() && request.name.length <= 120 && request.name.none { it.isISOControl() }) { "Part name is invalid" }
        if (part.name == request.name) return@mutate snapshot(root, project)
        val updated = project.copy(parts = project.parts.map {
            if (it.id == request.partId) it.copy(name = request.name, revision = it.revision + 1) else it
        })
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override fun updateSongPartSection(request: UpdateSongPartSectionRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        require(part.revision == request.expectedRevision) { "Part '${part.id}' changed; refresh before updating its section." }
        if (part.sectionType == request.sectionType) return@mutate snapshot(root, project)
        val updated = project.copy(
            parts = project.parts.map {
                if (it.id == request.partId) it.copy(sectionType = request.sectionType, revision = it.revision + 1) else it
            },
            workflow = project.workflow.invalidate(WorkflowChange.PART_SECTION)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override fun removeSongPart(request: RemoveSongPartRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        require(project.parts.any { it.id == request.partId }) { "Part not found: ${request.partId}" }
        val retainedOccurrences = project.envelope.structureOccurrences.filterNot { it.partId == request.partId }
        val retainedOccurrenceIds = retainedOccurrences.map(StructureOccurrence::id).toSet()
        val updated = project.copy(
            parts = project.parts.filterNot { it.id == request.partId },
            envelope = project.envelope.copy(
                evolvedParts = project.envelope.evolvedParts.filterNot { it.partId == request.partId },
                structureOccurrences = retainedOccurrences,
                arrangementAssignments = project.envelope.arrangementAssignments.filter { it.occurrenceId in retainedOccurrenceIds }
            ),
            workflow = project.workflow.invalidate(WorkflowChange.STRUCTURE)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override fun insertStructureOccurrence(request: InsertStructureOccurrenceRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        require(project.parts.any { it.id == request.partId }) { "Unknown part ID in structure: ${request.partId}" }
        request.afterOccurrenceId?.let { afterId ->
            val after = project.envelope.structureOccurrences.firstOrNull { it.id == afterId }
                ?: throw IllegalArgumentException("Structure occurrence '$afterId' no longer exists; refresh before inserting.")
            request.expectedRevision?.let { require(after.revision == it) { "Structure occurrence '$afterId' changed; refresh before inserting." } }
        }
        requireCurrentStructureAnalysis(root, project, request.partId)
        val id = nextOccurrenceId(project)
        val ordinal = project.envelope.structureOccurrences.count { it.partId == request.partId } + 1
        val occurrence = StructureOccurrence(id, request.partId, label = "${request.partId}$ordinal")
        val occurrences = project.envelope.structureOccurrences.toMutableList()
        val insertAt = request.afterOccurrenceId?.let { after -> occurrences.indexOfFirst { it.id == after } + 1 } ?: occurrences.size
        occurrences.add(insertAt, occurrence)
        writeStructure(root, project, occurrences)
    }

    override fun duplicateStructureOccurrence(request: DuplicateStructureOccurrenceRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val source = currentOccurrence(project, request.occurrenceId, request.expectedRevision)
        requireCurrentStructureAnalysis(root, project, source.partId)
        val occurrences = project.envelope.structureOccurrences.toMutableList()
        val copy = source.copy(id = nextOccurrenceId(project), label = "${source.label} copy", revision = 1)
        occurrences.add(occurrences.indexOfFirst { it.id == source.id } + 1, copy)
        writeStructure(root, project, occurrences)
    }

    override fun removeStructureOccurrence(request: RemoveStructureOccurrenceRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        currentOccurrence(project, request.occurrenceId, request.expectedRevision)
        writeStructure(root, project, project.envelope.structureOccurrences.filterNot { it.id == request.occurrenceId })
    }

    override fun moveStructureOccurrence(request: MoveStructureOccurrenceRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val occurrence = currentOccurrence(project, request.occurrenceId, request.expectedRevision)
        val occurrences = project.envelope.structureOccurrences.toMutableList()
        occurrences.remove(occurrence)
        val destination = request.afterOccurrenceId?.let { afterId ->
            require(afterId != occurrence.id) { "A structure occurrence cannot be moved after itself." }
            occurrences.indexOfFirst { it.id == afterId }.also { require(it >= 0) { "Structure occurrence '$afterId' no longer exists; refresh before moving." } } + 1
        } ?: 0
        occurrences.add(destination, occurrence)
        if (occurrences == project.envelope.structureOccurrences) snapshot(root, project) else writeStructure(root, project, occurrences)
    }

    override fun updateStructureOccurrenceLabel(request: UpdateStructureOccurrenceLabelRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val occurrence = currentOccurrence(project, request.occurrenceId, request.expectedRevision)
        if (occurrence.label == request.label) snapshot(root, project)
        else writeStructure(root, project, project.envelope.structureOccurrences.map {
            if (it.id == occurrence.id) it.copy(label = request.label, revision = it.revision + 1) else it
        })
    }

    override fun updateStructureOccurrenceVariation(request: UpdateStructureOccurrenceVariationRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val occurrence = currentOccurrence(project, request.occurrenceId, request.expectedRevision)
        if (occurrence.variationOverrides == request.variationOverrides) snapshot(root, project)
        else writeStructure(root, project, project.envelope.structureOccurrences.map {
            if (it.id == occurrence.id) it.copy(variationOverrides = request.variationOverrides, revision = it.revision + 1) else it
        })
    }

    override fun saveStructure(request: SaveStructureRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val knownIds = project.parts.map { it.id }.toSet()
        request.partIds.forEach { id -> require(id in knownIds) { "Unknown part ID in structure: $id" } }
        request.partIds.distinct().forEach { partId -> requireCurrentStructureAnalysis(root, project, partId) }
        if (project.envelope.structureOccurrences.map(StructureOccurrence::partId) == request.partIds) return@mutate snapshot(root, project)
        val available = project.envelope.structureOccurrences.groupBy(StructureOccurrence::partId).mapValues { (_, values) -> values.toMutableList() }.toMutableMap()
        val occurrences = request.partIds.mapIndexed { index, partId ->
            available[partId]?.removeFirstOrNull() ?: StructureOccurrence(nextOccurrenceId(project, index), partId, "$partId${index + 1}")
        }
        writeStructure(root, project, occurrences)
    }

    private fun currentOccurrence(project: Project, id: String, expectedRevision: Long): StructureOccurrence =
        project.envelope.structureOccurrences.firstOrNull { it.id == id }
            ?.also { require(it.revision == expectedRevision) { "Structure occurrence '$id' changed; refresh before editing." } }
            ?: throw IllegalArgumentException("Structure occurrence '$id' no longer exists; refresh before editing.")

    private fun nextOccurrenceId(project: Project, salt: Int = 0): String {
        val existing = project.envelope.structureOccurrences.map(StructureOccurrence::id).toSet()
        var candidate: String
        do candidate = "occ-${UUID.randomUUID().toString().replace("-", "").take(24)}${if (salt == 0) "" else "-$salt"}"
        while (candidate in existing)
        return candidate
    }

    private fun writeStructure(root: Path, project: Project, occurrences: List<StructureOccurrence>): ProjectSnapshot {
        val updated = project.copy(
            envelope = project.envelope.copy(structureOccurrences = occurrences),
            workflow = project.workflow.invalidate(WorkflowChange.STRUCTURE)
        )
        ProjectStore.write(root, updated)
        return snapshot(root, updated)
    }

    /**
     * Structure is only a handoff: it never selects a MIDI branch, repairs
     * notes, or writes a transition. It verifies the selected branch and its
     * exact analysis before atomically recording a changed occurrence sequence.
     */
    private fun requireCurrentStructureAnalysis(root: Path, project: Project, partId: String) {
        require(project.version == Project.CURRENT_VERSION) {
            "Structure handoff requires a MIDI-first v3 project."
        }
        val part = project.parts.first { it.id == partId }
        val selected = SelectedMidiArtifactResolver().resolve(root, project, part)
        val reference = requireNotNull(part.analysis) {
            "Missing MIDI analysis for part '$partId'. Run part analyze first."
        }
        require(reference.kind == AnalysisKind.MIDI) {
            "MIDI analysis is required for part '$partId'. Run part analyze first."
        }
        val analysisPath = safeDestination(root, reference.file)
        val persisted = runCatching {
            json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath))
        }.getOrElse {
            throw IllegalArgumentException("MIDI analysis is malformed for part '$partId'. Run part analyze first.", it)
        }
        require(persisted.partId == partId && midiPartAnalyzer.analyze(selected.path, partId) == persisted) {
            "MIDI analysis is stale for the selected MIDI of part '$partId'. Run part analyze first."
        }
    }

    private fun readValidProject(root: Path): Project {
        require(Files.isRegularFile(root.resolve(ProjectStore.FILE_NAME))) { "Project file not found: ${root.resolve(ProjectStore.FILE_NAME)}" }
        return ProjectStore.read(root).also { it.requireValid(root) }
    }

    /**
     * Re-running Clean MIDI is the recovery boundary for a part's derived
     * evidence. A missing or altered old quality/correction report must not
     * prevent that recovery: the new clean run replaces every downstream
     * reference for this part. Other project validation failures still block
     * the operation.
     */
    private fun readProjectForCleanMidi(root: Path, partId: String): Project = try {
        readValidProject(root)
    } catch (validationFailure: IllegalArgumentException) {
        val project = runCatching { ProjectStore.read(root) }.getOrElse { throw validationFailure }
        val part = project.parts.singleOrNull { it.id == partId } ?: throw validationFailure
        val midi = part.midi ?: throw validationFailure
        val recoverable = project.copy(parts = project.parts.map {
            if (it.id == partId) it.copy(
                analysis = null,
                sourceKeyEvidence = null,
                midi = midi.copy(
                    clean = null,
                    cleanup = null,
                    quality = null,
                    normalized = null,
                    normalization = null,
                    transposed = null,
                    transposition = null,
                    cleanApproval = null,
                    technicalCorrectionSelection = TechnicalCorrectionSelection.BASE,
                    technicalCorrection = null,
                    aiFixSelection = MidiAiFixSelection.PENDING,
                    aiFix = null,
                    enhancementSelection = app.melotrail.arrangement.EnhancementSelection.PENDING,
                    enhancement = null,
                    analysisInput = MidiAnalysisInput.CURRENT,
                    feel = null
                )
            ) else it
        })
        if (!recoverable.validate(root).isValid) throw validationFailure
        project
    }

    private fun snapshot(root: Path, project: Project): ProjectSnapshot {
        fun current(artifact: WorkflowArtifact) = artifact !in project.workflow.stale
        val projectKey = project.envelope.compositionSettings?.takeIf { it.complete }?.key
        val summaries = project.parts.map { part ->
            part.summary(
                root,
                analysisCurrent = current(WorkflowArtifact.ANALYSIS),
                projectKey = projectKey
            )
        }
        val durationById = summaries.associate { it.id to it.analysis?.durationSeconds }
        val occurrenceCounts = mutableMapOf<String, Int>()
        val structure = project.envelope.structureOccurrences.mapIndexed { index, occurrence ->
            val number = (occurrenceCounts[occurrence.partId] ?: 0) + 1
            occurrenceCounts[occurrence.partId] = number
            StructureSectionSummary(index, occurrence.partId, number, occurrence.id, durationById[occurrence.partId], occurrence.label, occurrence.revision, occurrence.variationOverrides)
        }
        return ProjectSnapshot(
            root = root,
            version = project.version,
            name = project.name,
            renderFormat = project.renderFormat,
            parts = summaries,
            structure = structure,
            readiness = ProjectReadiness(
                cleanMidiReady = summaries.isNotEmpty() && summaries.all { it.preparation.cleanMidi },
                analysesReady = summaries.isNotEmpty() && summaries.all { it.preparation.analyzed },
                structureReady = project.envelope.structureOccurrences.isNotEmpty(),
                songPlanAvailable = Files.isRegularFile(root.resolve("song_plan.json")) && current(WorkflowArtifact.ARRANGEMENT),
                arrangementAvailable = Files.isRegularFile(root.resolve("arrangement_plan.json")) && current(WorkflowArtifact.ARRANGEMENT),
                generatedMidiAvailable = current(WorkflowArtifact.GENERATED_MIDI) && project.workflow.generatedMidi?.let { generated ->
                    generated.artifacts.all { reference ->
                        val path = root.resolve(reference.artifact.file).normalize()
                        path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == reference.artifact.sha256
                    }
                } == true,
                criticAvailable = project.workflow.critic?.let { critic ->
                    current(WorkflowArtifact.CRITIC) && root.resolve(critic.report.file).normalize().let { path ->
                        path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == critic.report.sha256
                    }
                } == true,
                fullSongEnhancementSelection = project.workflow.fullSongEnhancementSelection,
                fullSongEnhancementAvailable = project.workflow.fullSongEnhancement?.let { enhancement ->
                    enhancement.status != null && current(WorkflowArtifact.FULL_SONG_ENHANCEMENT) && enhancement.artifacts.all { artifact ->
                        val input = root.resolve(artifact.input.file).normalize()
                        val output = root.resolve(artifact.output.file).normalize()
                        input.startsWith(root) && output.startsWith(root) && Files.isRegularFile(input) && Files.isRegularFile(output) &&
                            sha256(input) == artifact.input.sha256 && sha256(output) == artifact.output.sha256
                    } && enhancement.plan?.let { plan -> root.resolve(plan.file).normalize().let { Files.isRegularFile(it) && sha256(it) == plan.sha256 } } == true &&
                        enhancement.report?.let { report -> root.resolve(report.file).normalize().let { Files.isRegularFile(it) && sha256(it) == report.sha256 } } == true
                } == true,
                humanizationSelection = project.workflow.humanizationSelection,
                humanizationAvailable = project.workflow.humanization != null && current(WorkflowArtifact.HUMANIZATION),
                stemsAvailable = Files.isDirectory(root.resolve("stems")) && current(WorkflowArtifact.STEMS) && Files.list(root.resolve("stems")).use { it.anyMatch { Files.isRegularFile(it) } },
                dryMixAvailable = Files.isRegularFile(root.resolve("mix/dry.wav")) && current(WorkflowArtifact.DRY_MIX),
                loFiMixAvailable = Files.isRegularFile(root.resolve("mix/lofi.wav")) && current(WorkflowArtifact.AUDIO_TEXTURE),
                masterAvailable = Files.isRegularFile(root.resolve("output/master.wav")) && current(WorkflowArtifact.MASTER),
                midiQualityReportsReady = summaries.isNotEmpty() && summaries.all { it.preparation.midiQuality.status == MidiQualityStatus.CURRENT },
                releaseAvailable = Files.isRegularFile(root.resolve("output/release.json")) && current(WorkflowArtifact.RELEASE),
                staleArtifacts = project.workflow.stale,
                cohesionApprovalRequired = project.workflow.cohesion?.let { !it.approved && WorkflowArtifact.COHESION !in project.workflow.stale } == true,
                cohesionReady = currentCohesion(root, project),
                commercialSourceAttestationsComplete = project.parts.isNotEmpty() && project.parts.all { it.sourceAttestation?.supportsCommercialUse == true },
                compositionSettingsReady = compositionSettings.isReady(project),
                harmonyReady = harmony.query(project).ready,
                stageRuns = stageRunSnapshots(root, project)
            )
        )
    }

    private fun stageRunSnapshots(root: Path, project: Project): List<StageRunSnapshot> = runCatching {
        val reference = project.envelope.stageRuns
        if (reference.index == null) emptyList() else StageRunStore().read(root, reference).map { record ->
            StageRunSnapshot(record.runId, record.stage, record.subject, record.status,
                retryable = record.status == app.melotrail.arrangement.StageRunStatus.FAILED,
                failure = record.failure?.code,
                outputs = record.outputArtifacts.mapIndexed { index, artifact ->
                    StageArtifactSnapshot("${record.runId}:output:$index", artifact.sha256)
                },
                reports = record.reportArtifacts.mapIndexed { index, artifact ->
                    StageArtifactSnapshot("${record.runId}:report:$index", artifact.sha256)
                })
        }
    }.getOrDefault(emptyList())

    /** Rebuilds the bounded cohesion input so readiness cannot be inferred from a plan file. */
    private fun currentCohesion(root: Path, project: Project): Boolean = runCatching {
        val cohesion = project.workflow.cohesion ?: return false
        if (!cohesion.approved || WorkflowArtifact.COHESION in project.workflow.stale || project.envelope.structureOccurrences.isEmpty()) return false
        val structure = project.envelope.structureOccurrences.mapIndexed { index, occurrence -> occurrence.toSectionInstance(index) }
        val analyses = structure.map(SectionInstance::partId).distinct().associateWith { partId ->
            val part = project.parts.first { it.id == partId }
            val reference = requireNotNull(part.analysis)
            require(reference.kind == AnalysisKind.MIDI)
            val analysis = json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(reference.file)))
            ArrangementHarmonyContext.apply(analysis, part.sectionType, project.envelope.harmony)
        }
        val input = SongPlanningInput(project.name, project.version, analyses, structure, LogicalInstrument.entries.map { it.wireName })
        val arrangement = project.workflow.arrangement ?: return false
        if (WorkflowArtifact.ARRANGEMENT in project.workflow.stale) return false
        val arrangementPath = root.resolve(arrangement.arrangement.file).normalize()
        require(arrangementPath.startsWith(root) && Files.isRegularFile(arrangementPath) && sha256(arrangementPath) == arrangement.arrangement.sha256)
        val detailed = json.decodeFromString(DetailedArrangement.serializer(), Files.readString(arrangementPath))
        val transitionInput = EnsembleTransitionContextFactory.build(root, project, input, detailed, arrangement.arrangement.sha256, arrangement.contextSha256, cohesion.intensity)
        return EnsembleCohesionStore.isApprovedCurrent(root, transitionInput)
    }.getOrDefault(false)

    private fun SongPart.summary(root: Path, analysisCurrent: Boolean, projectKey: MusicalKey?): PartSummary {
        val sourcePath = Path.of(file)
        val sourceType = sourceType(file)
        val evidence = importEvidence
        val sourceFileCurrent = sourcePath.startsWith("source") && isProjectFile(root, file)
        val sourcePreserved = sourceFileCurrent &&
            (evidence == null || runCatching { sha256(root.resolve(file)) == evidence.sourceSha256 }.getOrDefault(false))
        val source = if (sourceFileCurrent) InspectionSourceIdentity(file, sha256(root.resolve(file))) else null
        val report = runCatching { InputInspectionReportStore.read(root, id) }.getOrNull()
        val inspected = source != null && report?.source == source
        val warnings = when {
            report == null && Files.isRegularFile(InputInspectionPaths.report(root, id)) -> listOf("Inspection report is invalid; inspect again.")
            report != null && source != null && report.source != source -> listOf("Inspection report is stale; inspect again.")
            inspected -> report.warnings
            else -> emptyList()
        }
        val safeWarnings = warnings + listOfNotNull(unsupportedSectionWarning)
        val rawMidi = midi?.raw?.let { reference ->
            isMidiArtifact(root, reference) && (evidence == null || runCatching {
                sha256(safeDestination(root, reference)) == evidence.rawMidiSha256
            }.getOrDefault(false))
        } ?: false
        val cleanMidi = midi?.clean?.let { isMidiArtifact(root, it) } ?: false
        val sourceEvidence = sourceKeyEvidence
        val transposedMidi = if (sourceEvidence == null) true else runCatching {
            val sourceKey = requireNotNull(sourceEvidence.effectiveKey)
            val normalized = requireNotNull(midi?.normalized)
            val transposed = requireNotNull(midi.transposed)
            val reportReference = requireNotNull(midi.transposition)
            MidiTranspositionReportStore.isCurrent(root, id, safeDestination(root, normalized), safeDestination(root, transposed), sourceKey,
                requireNotNull(projectKey), reportReference)
        }.getOrDefault(false)
        val quality = midiQuality(root, this, cleanMidi)
        val correction = technicalCorrection(root, this)
        val enhancement = enhancement(root, this)
        val analyzed = analysisCurrent && (analysis?.let { runCatching { it.summary(root) }.isSuccess } ?: false)
        val preparedAudio = sourceType == PartSourceType.AUDIO && inspected &&
            report?.preparation == PreparationStatus.CLEANED && isWaveArtifact(InputInspectionPaths.cleanWav(root, id))
        val aiFix = midiAiFix(root, this, cleanMidi)
        val feel = midiFeel(root, this, cleanMidi, aiFix)
        val feelSelectedAvailable = midi?.analysisInput != MidiAnalysisInput.LOFI_FEEL || feel.available
        val preparation = PartPreparationSummary(
            sourcePreserved = sourcePreserved,
            inspected = inspected,
            preparedAudio = preparedAudio,
            rawMidi = rawMidi,
            cleanMidi = cleanMidi,
            transposedMidi = transposedMidi,
            analyzed = analyzed,
            ready = sourcePreserved && inspected && cleanMidi && transposedMidi && analyzed && quality.status == MidiQualityStatus.CURRENT &&
                correction.selectedAvailable && enhancement.selectedAvailable && aiFix.selectedAvailable && feelSelectedAvailable,
            warnings = safeWarnings + quality.warnings.map { it.message },
            midiQuality = quality,
            technicalCorrection = correction,
            enhancement = enhancement,
            midiFeel = feel,
            midiAiFix = aiFix
        )
        val sourceSize = root.resolve(file).takeIf { sourcePreserved }?.let { path ->
            runCatching { Files.size(path) }.getOrNull()
        }
        return PartSummary(
            id = id,
            role = sectionType.value,
            name = name,
            sectionType = sectionType,
            sourceFile = file,
            sourceName = sourcePath.fileName.toString(),
            sourceType = sourceType,
            analysis = analysis?.summary(root),
            preparation = preparation,
            sourceSizeBytes = sourceSize,
            sourceSha256 = importEvidence?.sourceSha256,
            sourceKeyConfirmed = sourceKeyEvidence?.effectiveKey != null,
            sourceKey = sourceKeyEvidence?.let { evidence ->
                SourceKeySummary(evidence.detectedKey, evidence.confidence, evidence.algorithmVersion, evidence.confirmedOverride, evidence.confirmationRequired)
            },
            revision = revision,
            warnings = safeWarnings
        )
    }

    private fun midiQuality(root: Path, part: SongPart, cleanMidi: Boolean): MidiQualitySummary {
        val midi = part.midi ?: return MidiQualitySummary.invalid()
        if (midi.cleanup == null || midi.quality == null || !cleanMidi) return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        val rawReference = requireNotNull(midi.raw)
        val report = runCatching { MidiQualityReportStore.read(root, midi.quality) }.getOrNull()
            ?: return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        if (!MidiQualityReportStore.isCurrent(root, part.id, rawReference, requireNotNull(midi.clean), midi.cleanup, midi.quality)) {
            return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        }
        val approved = MidiQualityReportStore.isApproved(root, midi.quality, midi.cleanApproval)
        val status = when {
            approved -> MidiQualityStatus.CURRENT
            report.approvalRequired && midi.cleanApproval == null -> MidiQualityStatus.APPROVAL_REQUIRED
            else -> MidiQualityStatus.STALE_OR_INVALID
        }
        return MidiQualitySummary(status, report.cleanup, report.warnings, report.recommendations, report)
    }

    private fun midiAiFix(root: Path, part: SongPart, cleanMidi: Boolean): MidiAiFixSummary {
        val midi = part.midi ?: return MidiAiFixSummary()
        val references = midi.aiFix ?: return MidiAiFixSummary(midi.aiFixSelection)
        val clean = midi.clean ?: return MidiAiFixSummary(midi.aiFixSelection)
        val correction = midi.technicalCorrection ?: return MidiAiFixSummary(midi.aiFixSelection)
        // Technical Correction operates on the selected pre-correction
        // baseline, which may be normalized or transposed rather than clean.
        // Do not mislabel that valid lineage as stale when reopening a project.
        val correctionInput = midi.transposed ?: midi.normalized ?: clean
        val inputCurrent = cleanMidi && runCatching {
            references.requireCanonical(part.id)
            midi.technicalCorrectionSelection == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED &&
                correction.input.file == correctionInput &&
                correction.input.sha256 == sha256(safeDestination(root, correctionInput)) &&
                references.inputSha256 == correction.output.sha256 &&
                sha256(safeDestination(root, correction.output.file)) == correction.output.sha256
        }.getOrDefault(false)
        // AI-fix validity is part-local. A project-wide stale marker can be
        // introduced by importing another part, which must not make this
        // part's independently fingerprinted approved output unavailable.
        fun current(reference: app.melotrail.arrangement.WorkflowArtifactReference?, canonical: String): Boolean =
            inputCurrent && reference?.file == canonical && runCatching {
                val path = safeDestination(root, reference.file)
                isMidiArtifact(root, reference.file) && sha256(path) == reference.sha256
            }.getOrDefault(false)
        return MidiAiFixSummary(
            selected = midi.aiFixSelection,
            draftAvailable = current(references.draft, MidiAiFixArtifactPaths.draft(part.id)),
            approvedAvailable = current(references.approved, MidiAiFixArtifactPaths.approved(part.id))
        )
    }

    private fun technicalCorrection(root: Path, part: SongPart): TechnicalCorrectionSummary {
        val midi = part.midi ?: return TechnicalCorrectionSummary()
        val refs = midi.technicalCorrection ?: return TechnicalCorrectionSummary(midi.technicalCorrectionSelection)
        val available = runCatching {
            refs.requireCanonical(part.id)
            val input = safeDestination(root, refs.input.file)
            val output = safeDestination(root, refs.output.file)
            val report = safeDestination(root, refs.report.file)
            sha256(input) == refs.input.sha256 && sha256(output) == refs.output.sha256 && sha256(report) == refs.report.sha256
        }.getOrDefault(false)
        val report = if (available) runCatching {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = false }.decodeFromString(
                app.melotrail.arrangement.TechnicalCorrectionReport.serializer(), Files.readString(safeDestination(root, refs.report.file))
            )
        }.getOrNull() else null
        return TechnicalCorrectionSummary(midi.technicalCorrectionSelection, available, report?.warnings.orEmpty(), report?.approvalRequired == true)
    }

    private fun enhancement(root: Path, part: SongPart): EnhancementSummary {
        val midi = part.midi ?: return EnhancementSummary()
        val refs = midi.enhancement ?: return EnhancementSummary(selected = midi.enhancementSelection)
        val available = runCatching {
            refs.requireCanonical(part.id)
            sha256(safeDestination(root, refs.input.file)) == refs.input.sha256 &&
                sha256(safeDestination(root, refs.output.file)) == refs.output.sha256 &&
                sha256(safeDestination(root, refs.report.file)) == refs.report.sha256
        }.getOrDefault(false)
        return EnhancementSummary(refs.intensity, midi.enhancementSelection, available, refs.approval)
    }

    private fun midiFeel(root: Path, part: SongPart, cleanMidi: Boolean, aiFix: MidiAiFixSummary): MidiFeelSummary {
        val midi = part.midi ?: return MidiFeelSummary()
        val references = midi.feel ?: return MidiFeelSummary(midi.analysisInput)
        val base = when (midi.aiFixSelection) {
            MidiAiFixSelection.PENDING -> null
            MidiAiFixSelection.SKIP -> midi.technicalCorrection?.output?.file
            MidiAiFixSelection.APPROVED -> midi.aiFix?.approved?.file.takeIf { aiFix.approvedAvailable }
        } ?: midi.clean ?: return MidiFeelSummary(midi.analysisInput)
        val enhancement = midi.enhancement?.takeIf {
            midi.enhancementSelection == app.melotrail.arrangement.EnhancementSelection.ENHANCED &&
                it.approval == app.melotrail.arrangement.EnhancementApproval.APPROVED &&
                it.input.file == base && runCatching { sha256(safeDestination(root, it.output.file)) == it.output.sha256 }.getOrDefault(false)
        }
        val selectedBase = enhancement?.output?.file ?: base
        val report = runCatching { MidiFeelReportStore.read(root, references.report) }.getOrNull()
        // Like AI-fix, this artifact is tied to the part's selected base, not
        // to the aggregate project workflow marker.
        val current = cleanMidi && MidiFeelReportStore.isCurrent(root, part.id, selectedBase, references)
        return MidiFeelSummary(midi.analysisInput, current, report?.takeIf { current })
    }

    private fun isProjectFile(root: Path, reference: String): Boolean = runCatching {
        val path = safeDestination(root, reference)
        Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())
    }.getOrDefault(false)

    private fun isMidiArtifact(root: Path, reference: String): Boolean = runCatching {
        val path = safeDestination(root, reference)
        Files.isRegularFile(path) && Files.size(path) >= 14 &&
            Files.newInputStream(path).use { it.readNBytes(4).decodeToString() == "MThd" }
    }.getOrDefault(false)

    private fun isWaveArtifact(path: Path): Boolean = runCatching {
        Files.isRegularFile(path) && Files.newInputStream(path).use {
            val header = it.readNBytes(12)
            header.size == 12 && header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                header.copyOfRange(8, 12).decodeToString() == "WAVE"
        }
    }.getOrDefault(false)

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun PartAnalysisReference.summary(root: Path): PartAnalysisSummary {
        val path = root.resolve(file).normalize()
        val analysis = json.decodeFromString(MidiAnalysis.serializer(), Files.readString(path))
        return PartAnalysisSummary(PartAnalysisStatus.MIDI, file, analysis.bars, analysis.durationSeconds, analysis.key?.let { "${it.tonic} ${it.mode}" })
    }

    private fun <T> mutate(root: Path, action: (Path) -> T): T {
        val normalizedRoot = root.normalizeRoot()
        val lock = ProjectMutationCoordinator.lock(normalizedRoot)
        require(lock.tryLock()) { "Another project mutation is already running: $normalizedRoot" }
        return try { action(normalizedRoot) } finally { lock.unlock() }
    }

    private suspend fun mutateSuspend(root: Path, action: suspend (Path) -> ProjectSnapshot): ProjectSnapshot {
        val normalizedRoot = root.normalizeRoot()
        val lock = ProjectMutationCoordinator.lock(normalizedRoot)
        require(lock.tryLock()) { "Another project mutation is already running: $normalizedRoot" }
        return try { withContext(Dispatchers.IO) { action(normalizedRoot) } } finally { lock.unlock() }
    }

    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()

    private fun safeDestination(projectRoot: Path, reference: String): Path {
        val relative = Path.of(reference)
        require(!relative.isAbsolute && reference.isNotBlank()) { "Project destination must be relative: $reference" }
        val destination = projectRoot.resolve(relative).normalize()
        require(destination.startsWith(projectRoot)) { "Project destination escapes the project root: $reference" }
        val realRoot = projectRoot.toRealPath()
        var existing: Path? = destination
        while (existing != null && !Files.exists(existing)) existing = existing.parent
        if (existing != null) require(existing.toRealPath().startsWith(realRoot)) {
            "Project destination escapes the project root through a symlink: $reference"
        }
        return destination
    }

    private fun requireMidiArtifact(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "$stage did not create a MIDI file: $path" }
        val header = Files.newInputStream(path).use { it.readNBytes(4).decodeToString() }
        require(header == "MThd") { "$stage did not create a MIDI file: $path" }
        runCatching { MidiSystem.getSequence(path.toFile()) }.getOrElse {
            throw IllegalArgumentException("$stage is not a valid Standard MIDI file: $path", it)
        }
    }

    private fun publishImmutableSource(source: Path, destination: Path, expectedSha256: String) {
        if (Files.exists(destination)) {
            require(Files.isRegularFile(destination) && sha256(destination) == expectedSha256 && Files.mismatch(source, destination) == -1L) {
                "Part destination already exists with different source content: $destination"
            }
            return
        }
        Files.createDirectories(checkNotNull(destination.parent))
        val temporary = destination.resolveSibling(".${destination.fileName}.import-${UUID.randomUUID()}.tmp")
        try {
            Files.copy(source, temporary)
            require(sha256(temporary) == expectedSha256) { "Source changed while it was being preserved: $source" }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException("Atomic source publication is not supported for '$destination'", error)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private suspend fun inspectAudioImport(root: Path, partId: String, relativeFile: String, source: Path, extension: String) {
        val identity = InspectionSourceIdentity(relativeFile, sha256(source))
        val expectedContainer = if (extension == "mp3") InputContainer.MPEG_AUDIO else InputContainer.RIFF_WAVE
        val existing = runCatching { InputInspectionReportStore.read(root, partId) }.getOrNull()
        val report = if (existing?.source == identity && existing.detectedInput.container == expectedContainer && existing.detectedInput.extension == extension) {
            existing
        } else {
            val request = InputInspectionRequest(root, partId, identity).also { it.requireValid() }
            when (val result = inputInspection.inspect(request)) {
                is InputInspectionResult.Inspected -> result.report
                is InputInspectionResult.Rejected -> {
                    result.error.requireValid()
                    throw IllegalStateException("Input validation failed (${result.error.code}): ${result.error.message}")
                }
            }
        }
        require(report.partId == partId && report.source == identity) { "Input validation returned evidence for a different project source." }
        require(report.detectedInput.container == expectedContainer && report.detectedInput.extension == extension) {
            "Input extension '$extension' does not match the detected ${report.detectedInput.container.name} container."
        }
        report.requireValid()
        InputInspectionReportStore.write(root, report)
    }

    private suspend fun runTranscriptionGate(root: Path, partId: String, selectedInput: TranscriptionInputArtifact): Path {
        val gate = requireNotNull(transcriptionQualityGate) { "The transcription quality gate is not configured." }
        return when (val result = gate.run(RunTranscriptionQualityGateRequest(root, partId, selectedInput))) {
            is TranscriptionQualityGateResult.Succeeded -> result.rawMidi
            is TranscriptionQualityGateResult.Failed -> throw IllegalStateException(
                "Transcription stopped during ${result.stage.name.lowercase().replace('_', ' ')}."
            )
        }
    }

    private fun requireCurrentImportEvidence(root: Path, part: SongPart) {
        val evidence = part.importEvidence ?: return
        evidence.requireValid()
        val sourcePath = safeDestination(root, part.file)
        val rawReference = checkNotNull(part.midi?.raw) { "Part '${part.id}' import evidence has no raw MIDI reference." }
        val rawPath = safeDestination(root, rawReference)
        check(Files.isRegularFile(sourcePath) && sha256(sourcePath) == evidence.sourceSha256) {
            "Part '${part.id}' preserved source is stale or changed."
        }
        requireMidiArtifact(rawPath, "Part '${part.id}' raw MIDI")
        check(sha256(rawPath) == evidence.rawMidiSha256) { "Part '${part.id}' raw MIDI is stale or changed." }
    }

    private fun currentImportMatches(root: Path, part: SongPart, sourceSha256: String, extension: String): Boolean = runCatching {
        val evidence = requireNotNull(part.importEvidence)
        evidence.requireValid()
        require(part.file == "source/${part.id}.$extension")
        require(evidence.sourceSha256 == sourceSha256)
        requireCurrentImportEvidence(root, part)
        true
    }.getOrDefault(false)

    /** Extension is only a chooser hint. Validate the container before publishing immutable source evidence. */
    private fun requireImportSourceFormat(source: Path, extension: String, isMidi: Boolean) {
        when {
            isMidi -> requireMidiArtifact(source, "MIDI import")
            extension in setOf("wav", "wave") -> require(isWaveArtifact(source)) {
                "WAV import is not a RIFF/WAVE file: $source"
            }
            extension == "mp3" -> {
                val header = Files.newInputStream(source).use { it.readNBytes(10) }
                val startsWithFrame = header.size >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0
                val id3Size = if (header.size == 10 && header.copyOfRange(0, 3).decodeToString() == "ID3" && header.copyOfRange(6, 10).all { it.toInt() and 0x80 == 0 }) {
                    header.copyOfRange(6, 10).fold(0L) { size, byte -> (size shl 7) or (byte.toLong() and 0x7F) }
                } else null
                val frameAfterId3 = id3Size?.let { size ->
                    val offset = 10L + size
                    if (offset + 2 > Files.size(source)) false else Files.newInputStream(source).use { input ->
                        input.skipNBytes(offset)
                        val frame = input.readNBytes(2)
                        frame.size == 2 && frame[0] == 0xFF.toByte() && (frame[1].toInt() and 0xE0) == 0xE0
                    }
                } ?: false
                require(startsWithFrame || frameAfterId3) { "MP3 import does not contain a valid MPEG frame sync: $source" }
            }
        }
    }

    private fun temporaryMidi(target: Path): Path = target.resolveSibling(".${target.fileName}.prepare-${UUID.randomUUID()}.mid")

    private fun atomicReplace(source: Path, target: Path, stage: String) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for $stage output '$target'", error)
        }
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        val temporary = target.resolveSibling(".${target.fileName}.restore-${UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, bytes)
            atomicReplace(temporary, target, "MIDI quality report rollback")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun publishMidiFeel(root: Path, partId: String, inputReference: String, derived: Path, reportPath: Path, profile: MidiFeelProfile): MidiFeelReferences {
        val work = temporaryMidi(derived)
        val oldDerived = derived.takeIf(Files::isRegularFile)?.let { Files.readAllBytes(it) }
        val oldReport = reportPath.takeIf(Files::isRegularFile)?.let { Files.readAllBytes(it) }
        var derivedPublished = false
        var reportPublished = false
        try {
            val result = midiFeelTransformer.transform(safeDestination(root, inputReference), work, partId, profile)
            requireMidiArtifact(work, "Lo-fi Feel")
            atomicReplace(work, derived, "Lo-fi Feel MIDI")
            derivedPublished = true
            val report = MidiFeelReportStore.write(root, result.report)
            reportPublished = true
            return MidiFeelReferences(profile, root.relativize(derived).toString().replace('\\', '/'), root.relativize(report).toString().replace('\\', '/'))
        } catch (failure: Exception) {
            if (derivedPublished) runCatching { restoreArtifact(derived, oldDerived, "Lo-fi Feel MIDI rollback") }
            if (reportPublished) runCatching { restoreArtifact(reportPath, oldReport, "Lo-fi Feel report rollback") }
            throw failure
        } finally { Files.deleteIfExists(work) }
    }

    private fun restoreArtifact(target: Path, old: ByteArray?, stage: String) {
        if (old == null) Files.deleteIfExists(target) else {
            val temporary = target.resolveSibling(".${target.fileName}.restore-${UUID.randomUUID()}.tmp")
            try { Files.write(temporary, old); atomicReplace(temporary, target, stage) } finally { Files.deleteIfExists(temporary) }
        }
    }

    private fun sourceType(file: String): PartSourceType = when (file.substringAfterLast('.', "").lowercase()) {
        in MIDI_EXTENSIONS -> PartSourceType.MIDI
        "wav", "wave", "mp3" -> PartSourceType.AUDIO
        else -> PartSourceType.UNKNOWN
    }

    private data class SourceRegistration(val source: ArtifactRef)

    private companion object {
        val json = Json { ignoreUnknownKeys = false }
        val PART_ID = Regex("[A-Za-z0-9_-]+")
        val MIDI_EXTENSIONS = setOf("mid", "midi")
        val SUPPORTED_EXTENSIONS = MIDI_EXTENSIONS + setOf("wav", "wave", "mp3")
    }
}
