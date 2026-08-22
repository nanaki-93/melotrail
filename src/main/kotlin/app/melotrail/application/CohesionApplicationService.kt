package app.melotrail.application

import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.CohesionEnhancementIntensity
import app.melotrail.arrangement.CohesionPreviewReferences
import app.melotrail.arrangement.ArrangementHarmonyContext
import app.melotrail.arrangement.LocalQwenTransitionCohesionPlanner
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SongPlanStore
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.TransitionCohesionInput
import app.melotrail.arrangement.TransitionCohesionPlan
import app.melotrail.arrangement.TransitionCohesionStore
import app.melotrail.arrangement.toSectionInstance
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Cohesion is one bounded post-arrangement transition-planning stage. */
enum class CohesionPlannerKind { QWEN }
data class GenerateCohesionRequest(
    val root: Path,
    val planner: CohesionPlannerKind = CohesionPlannerKind.QWEN,
    val intensity: CohesionEnhancementIntensity = CohesionEnhancementIntensity.BALANCED
)
data class CohesionBoundarySnapshot(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val bridgeMidi: Path,
    val rationale: String,
    val reviewed: Boolean
)
data class CohesionSnapshot(
    val root: Path,
    val planner: CohesionPlannerKind,
    val inputHash: String,
    val structureSha256: String,
    val boundaries: List<CohesionBoundarySnapshot>,
    val approvalRequired: Boolean,
    val approved: Boolean,
    val stale: Boolean,
    val artifact: Path,
    val intensity: CohesionEnhancementIntensity = CohesionEnhancementIntensity.BALANCED,
    val melodyEditCount: Int = 0,
    val accompanimentEditCount: Int = 0,
    val baselinePreview: Path? = null,
    val enhancedPreview: Path? = null
)

fun interface EnsembleMidiPreparation {
    suspend fun prepare(root: Path, progress: ProgressSink)
}

fun interface CohesionPreviewPreparation {
    suspend fun render(root: Path, input: TransitionCohesionInput): CohesionPreviewReferences?
}

interface CohesionApplicationService {
    suspend fun generate(request: GenerateCohesionRequest, progress: ProgressSink = ProgressSink.None): CohesionSnapshot
    fun load(root: Path): CohesionSnapshot
    fun reviewBoundary(root: Path, outgoingInstanceId: String, incomingInstanceId: String): CohesionSnapshot
    fun approve(root: Path): CohesionSnapshot
    fun reject(root: Path): CohesionSnapshot
    suspend fun regenerate(request: GenerateCohesionRequest, progress: ProgressSink = ProgressSink.None) = generate(request, progress)
}

/** UI-neutral orchestration for baseline generation, bounded boundary planning, A/B review, and exact promotion. */
class DefaultCohesionApplicationService(
    private val ensemblePreparation: EnsembleMidiPreparation = EnsembleMidiPreparation { _, _ -> },
    private val previewPreparation: CohesionPreviewPreparation = CohesionPreviewPreparation { _, _ -> null },
    private val qwenPlanner: (TransitionCohesionInput) -> TransitionCohesionPlan = { input ->
        LocalQwenTransitionCohesionPlanner(model = CohesionModelIdentity("qwen", "local", "0".repeat(64))).plan(input)
    }
) : CohesionApplicationService {
    constructor(qwenPlanner: (TransitionCohesionInput) -> TransitionCohesionPlan) : this(
        EnsembleMidiPreparation { _, _ -> }, CohesionPreviewPreparation { _, _ -> null }, qwenPlanner
    )

    override suspend fun generate(request: GenerateCohesionRequest, progress: ProgressSink): CohesionSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("cohesion", 1, 5, "Preparing the approved arrangement ensemble"))
        ensemblePreparation.prepare(root, progress)
        progress.report(OperationProgress("cohesion", 2, 5, "Validating adjacent-boundary musical evidence"))
        val input = currentInput(root, request.intensity)
        progress.report(OperationProgress("cohesion", 3, 5, "Requesting bounded Cohesion plan"))
        val plan = qwenPlanner(input)
        progress.report(OperationProgress("cohesion", 4, 5, "Publishing boundary Cohesion MIDI"))
        TransitionCohesionStore.writeDraft(root, input, plan)
        persistComparisons(root, input)
        progress.report(OperationProgress("cohesion", 5, 5, "Rendering Cohesion baseline and preview"))
        previewPreparation.render(root, input)?.let { TransitionCohesionStore.attachPreviews(root, input, it) }
        snapshot(root, input, plan, false)
    }

    override fun load(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized))
        val approved = TransitionCohesionStore.isApprovedCurrent(normalized, input)
        val plan = if (approved) TransitionCohesionStore.readApproved(normalized, input) else TransitionCohesionStore.readDraft(normalized, input)
        snapshot(normalized, input, plan, approved)
    }

    override fun reviewBoundary(root: Path, outgoingInstanceId: String, incomingInstanceId: String): CohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = TransitionCohesionStore.readDraft(normalized, input)
        TransitionCohesionStore.markReviewed(normalized, input, outgoingInstanceId, incomingInstanceId)
        snapshot(normalized, input, plan, false)
    }

    override fun approve(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = TransitionCohesionStore.readDraft(normalized, input)
        TransitionCohesionStore.approve(normalized, input)
        snapshot(normalized, input, plan, true)
    }

    override fun reject(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = TransitionCohesionStore.readDraft(normalized, input)
        val artifact = TransitionCohesionStore.reject(normalized, input)
        snapshot(normalized, input, plan, false).copy(artifact = artifact)
    }

    private fun currentInput(root: Path, intensity: CohesionEnhancementIntensity): TransitionCohesionInput {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        require(project.version == Project.CURRENT_VERSION) { "Cohesion requires a MIDI-first v3 project." }
        require(project.envelope.structureOccurrences.isNotEmpty()) { "Save a non-empty structure before generating cohesion." }
        val structure = project.envelope.structureOccurrences.mapIndexed { index, occurrence -> occurrence.toSectionInstance(index) }
        val analyses = structure.map(SectionInstance::partId).distinct().associateWith { partId -> analysis(root, project, partId) }
        val planning = SongPlanningInput(project.name, project.version, analyses, structure, LogicalInstrument.entries.map { it.wireName })
        planning.requireValid()
        val approval = requireNotNull(project.workflow.arrangement) {
            "Cohesion requires a current approved arrangement. Generate and approve Arrangement first."
        }
        require(app.melotrail.arrangement.WorkflowArtifact.ARRANGEMENT !in project.workflow.stale) {
            "Cohesion requires a current approved arrangement. Regenerate and approve Arrangement first."
        }
        require(WorkflowArtifact.GENERATED_MIDI !in project.workflow.stale) {
            "Cohesion requires current baseline ensemble MIDI for the approved arrangement."
        }
        val generated = requireNotNull(project.workflow.generatedMidi) {
            "Cohesion requires fingerprinted baseline ensemble MIDI."
        }
        require(generated.arrangementSha256 == approval.arrangement.sha256) {
            "Baseline ensemble MIDI belongs to another arrangement."
        }
        generated.artifacts.forEach { reference ->
            val path = root.resolve(reference.artifact.file).normalize()
            require(path.startsWith(root) && Files.isRegularFile(path) && digest(path) == reference.artifact.sha256) {
                "Baseline ensemble MIDI '${reference.id}' is missing or changed."
            }
        }
        val arrangement = root.resolve(approval.arrangement.file).normalize()
        require(arrangement.startsWith(root) && Files.isRegularFile(arrangement) && digest(arrangement) == approval.arrangement.sha256) {
            "Approved arrangement evidence has changed. Regenerate and approve Arrangement first."
        }
        val structureEvidence = project.envelope.structureOccurrences.joinToString("|") { "${it.id}:${it.partId}:${it.revision}" }
        val occurrences = planning.sectionsWithIdentity().joinToString("|") { "${it.index}:${it.instanceId}:${it.occurrenceHash}" }
        require(digest(structureEvidence) == approval.structureSha256 && digest(occurrences) == approval.occurrenceSha256) {
            "Approved arrangement does not match the current Structure. Regenerate and approve Arrangement first."
        }
        val planPath = root.resolve(SongPlanStore.FILE_NAME)
        require(Files.isRegularFile(planPath) && digest(planPath) == approval.planSha256) {
            "Approved arrangement plan has changed. Regenerate and approve Arrangement first."
        }
        val plan = Json { ignoreUnknownKeys = false }.decodeFromString(SongPlan.serializer(), Files.readString(planPath))
        require(plan.contextHash == null || plan.contextHash == approval.contextSha256) {
            "Approved arrangement context has changed. Regenerate and approve Arrangement first."
        }
        val detailed = Json { ignoreUnknownKeys = false }
            .decodeFromString(DetailedArrangement.serializer(), Files.readString(arrangement))
        return app.melotrail.arrangement.TransitionCohesionInputFactory.build(
            root, project, planning, detailed, approval.arrangement.sha256, approval.contextSha256, intensity
        )
    }
    private fun savedIntensity(root: Path): CohesionEnhancementIntensity =
        ProjectStore.read(root).workflow.cohesion?.intensity ?: CohesionEnhancementIntensity.BALANCED
    private fun analysis(root: Path, project: Project, partId: String): MidiAnalysis {
        val part = project.parts.firstOrNull { it.id == partId } ?: error("Structure references unknown part '$partId'.")
        val ref = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$partId'. Run part analyze first." }
        require(ref.kind?.name == "MIDI") { "MIDI analysis is required for part '$partId'. Run part analyze first." }
        val analysis = Json { ignoreUnknownKeys = false }.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(ref.file)))
        return ArrangementHarmonyContext.apply(analysis, part.sectionType, project.envelope.harmony)
    }
    private fun snapshot(root: Path, input: TransitionCohesionInput, plan: TransitionCohesionPlan, approved: Boolean): CohesionSnapshot {
        val cohesionWorkflow = ProjectStore.read(root).workflow.cohesion
        val reviewed = cohesionWorkflow?.boundaries.orEmpty().filter { it.approved != null }.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet()
        return CohesionSnapshot(root, CohesionPlannerKind.QWEN, input.inputHash, input.structureSha256,
            plan.boundaries.map { bridge -> CohesionBoundarySnapshot(bridge.outgoingInstanceId, bridge.incomingInstanceId, root.resolve(TransitionCohesionStore.bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId)), "${bridge.roleAction.name.lowercase().replace('_', ' ')}: ${bridge.rationale}", bridge.outgoingInstanceId to bridge.incomingInstanceId in reviewed) },
            approvalRequired = !approved, approved = approved, stale = false,
            artifact = root.resolve(if (approved) TransitionCohesionStore.APPROVED_FILE else TransitionCohesionStore.DRAFT_FILE),
            intensity = input.intensity,
            melodyEditCount = plan.boundaries.sumOf { it.melodyEdits.size },
            accompanimentEditCount = plan.boundaries.size,
            baselinePreview = cohesionWorkflow?.previews?.baseline?.file?.let(root::resolve),
            enhancedPreview = cohesionWorkflow?.previews?.enhanced?.file?.let(root::resolve))
    }
    private fun persistComparisons(root: Path, input: TransitionCohesionInput) {
        val project = ProjectStore.read(root)
        val cohesion = requireNotNull(project.workflow.cohesion)
        cohesion.occurrences.sortedBy { it.instanceId }.forEach { occurrence ->
            val selected = app.melotrail.arrangement.SelectedMidiArtifactResolver().resolve(root, project, occurrence.instanceId.let { id -> project.envelope.structureOccurrences.single { it.instanceId == id }.partId })
            val before = WorkflowArtifactReference(selected.projectRelativePath, selected.sha256)
            val output = occurrence.result
            val beforeEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, before, input.contextSha256, role = "piano", occurrenceId = occurrence.instanceId)
            val afterEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, output, input.contextSha256, StageEvidenceStatus.DRAFT, "piano", occurrence.instanceId)
            StageComparisonReportStore.write(root, afterEvidence, StageComparisonService().compare(root, beforeEvidence, afterEvidence))
        }
        val generated = project.workflow.generatedMidi?.artifacts.orEmpty().associateBy { it.id }
        cohesion.roles.sortedBy { it.role }.forEach { output ->
            val source = requireNotNull(generated[output.role]) { "Cohesion role '${output.role}' has no generated MIDI source." }.artifact
            val beforeEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, source, input.contextSha256, role = output.role)
            val afterEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, output.result, input.contextSha256, StageEvidenceStatus.DRAFT, output.role)
            StageComparisonReportStore.write(root, afterEvidence, StageComparisonService().compare(root, beforeEvidence, afterEvidence))
        }
    }
    private fun digest(path: Path): String = digest(Files.readAllBytes(path))
    private fun digest(value: String): String = digest(value.toByteArray())
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private suspend fun <T> mutate(root: Path, block: suspend (Path) -> T): T { val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }; check(lock.tryLock()) { "Another cohesion operation is already running: $normalized" }; return try { withContext(Dispatchers.IO) { block(normalized) } } finally { lock.unlock() } }
    private fun <T> locked(root: Path, block: (Path) -> T): T { val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }; check(lock.tryLock()) { "Another cohesion operation is already running: $normalized" }; return try { block(normalized) } finally { lock.unlock() } }
    private companion object { val locks = ConcurrentHashMap<Path, Mutex>() }
}
