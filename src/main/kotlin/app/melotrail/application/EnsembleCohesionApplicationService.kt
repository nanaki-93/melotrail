package app.melotrail.application

import app.melotrail.arrangement.EnsembleCohesionModelIdentity
import app.melotrail.arrangement.EnsembleCohesionEnhancementIntensity
import app.melotrail.arrangement.CohesionPreviewReferences
import app.melotrail.arrangement.ArrangementHarmonyContext
import app.melotrail.arrangement.LocalQwenEnsembleCohesionPlanner
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DeterministicContinuityEnsembleCohesionPlanner
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SongPlanStore
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.EnsembleCohesionInput
import app.melotrail.arrangement.EnsembleCohesionPlan
import app.melotrail.arrangement.EnsembleCohesionStore
import app.melotrail.arrangement.FullSongCriticReport
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

/** Ensemble Cohesion is one bounded post-arrangement transition-planning stage. */
enum class EnsembleCohesionPlannerKind { DETERMINISTIC, QWEN }
data class GenerateEnsembleCohesionRequest(
    val root: Path,
    val planner: EnsembleCohesionPlannerKind = EnsembleCohesionPlannerKind.QWEN,
    val intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED
)
data class EnsembleCohesionBoundarySnapshot(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val bridgeMidi: Path,
    val rationale: String,
    val reviewed: Boolean
)
data class EnsembleCohesionSnapshot(
    val root: Path,
    val planner: EnsembleCohesionPlannerKind,
    val inputHash: String,
    val structureSha256: String,
    val boundaries: List<EnsembleCohesionBoundarySnapshot>,
    val approvalRequired: Boolean,
    val approved: Boolean,
    val stale: Boolean,
    val artifact: Path,
    val intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED,
    val melodyEditCount: Int = 0,
    val accompanimentEditCount: Int = 0,
    val baselinePreview: Path? = null,
    val enhancedPreview: Path? = null
)

fun interface EnsembleMidiPreparation {
    suspend fun prepare(root: Path, progress: ProgressSink)
}

fun interface EnsembleCohesionPreviewPreparation {
    suspend fun render(root: Path, input: EnsembleCohesionInput, progress: ProgressSink): CohesionPreviewReferences?
}

/** Reject a draft that worsens either critic count used as Cohesion acceptance evidence. */
internal fun requireNoCohesionIssueIncrease(before: FullSongCriticReport, after: FullSongCriticReport) {
    fun metric(report: FullSongCriticReport, name: String) = report.aggregateMetrics.singleOrNull { it.name == name }?.value ?: 0.0
    require(metric(after, "blockingIssueCount") <= metric(before, "blockingIssueCount") &&
        metric(after, "criticalIssueCount") <= metric(before, "criticalIssueCount")) {
        "Cohesion approval would increase blocker or critical critic issues. Review or regenerate the boundary plan."
    }
}

interface EnsembleCohesionApplicationService {
    suspend fun generate(request: GenerateEnsembleCohesionRequest, progress: ProgressSink = ProgressSink.None): EnsembleCohesionSnapshot
    fun load(root: Path): EnsembleCohesionSnapshot
    fun reviewBoundary(root: Path, outgoingInstanceId: String, incomingInstanceId: String): EnsembleCohesionSnapshot
    fun approve(root: Path): EnsembleCohesionSnapshot
    fun reject(root: Path): EnsembleCohesionSnapshot
    suspend fun regenerate(request: GenerateEnsembleCohesionRequest, progress: ProgressSink = ProgressSink.None) = generate(request, progress)
}

/** UI-neutral orchestration for baseline generation, bounded boundary planning, A/B review, and exact promotion. */
class DefaultEnsembleCohesionApplicationService(
    private val ensemblePreparation: EnsembleMidiPreparation = EnsembleMidiPreparation { _, _ -> },
    private val previewPreparation: EnsembleCohesionPreviewPreparation = EnsembleCohesionPreviewPreparation { _, _, _ -> null },
    private val sourceSongCritic: SourceSongCriticApplicationService = DefaultSourceSongCriticApplicationService(),
    private val criticService: FullSongCriticApplicationService = DefaultFullSongCriticApplicationService(),
    private val deterministicPlanner: (EnsembleCohesionInput) -> EnsembleCohesionPlan =
        DeterministicContinuityEnsembleCohesionPlanner()::plan,
    private val qwenPlanner: (EnsembleCohesionInput) -> EnsembleCohesionPlan = { input ->
        LocalQwenEnsembleCohesionPlanner(model = EnsembleCohesionModelIdentity("qwen", "local", "0".repeat(64))).plan(input)
    }
) : EnsembleCohesionApplicationService {
    constructor(qwenPlanner: (EnsembleCohesionInput) -> EnsembleCohesionPlan) : this(
        EnsembleMidiPreparation { _, _ -> }, EnsembleCohesionPreviewPreparation { _, _, _ -> null },
        DefaultSourceSongCriticApplicationService(), DefaultFullSongCriticApplicationService(),
        DeterministicContinuityEnsembleCohesionPlanner()::plan, qwenPlanner
    )

    override suspend fun generate(request: GenerateEnsembleCohesionRequest, progress: ProgressSink): EnsembleCohesionSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("cohesion", 1, 5, "Preparing the approved arrangement ensemble"))
        ensemblePreparation.prepare(root, progress)
        progress.report(OperationProgress("cohesion", 2, 5, "Validating adjacent-boundary musical evidence"))
        val input = currentInput(root, request.intensity)
        progress.report(OperationProgress("cohesion", 3, 5, "Requesting bounded Ensemble Cohesion plan"))
        val plan = when (request.planner) {
            EnsembleCohesionPlannerKind.DETERMINISTIC -> deterministicPlanner(input)
            EnsembleCohesionPlannerKind.QWEN -> qwenPlanner(input)
        }
        progress.report(OperationProgress("cohesion", 4, 5, "Publishing boundary Ensemble Cohesion MIDI"))
        EnsembleCohesionStore.writeDraft(root, input, plan)
        persistComparisons(root, input)
        progress.report(OperationProgress("cohesion", 5, 5, "Rendering Ensemble Cohesion baseline and preview"))
        previewPreparation.render(root, input, progress)?.let { EnsembleCohesionStore.attachPreviews(root, input, it) }
        snapshot(root, input, plan, false)
    }

    override fun load(root: Path): EnsembleCohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized))
        val approved = EnsembleCohesionStore.isApprovedCurrent(normalized, input)
        val plan = if (approved) EnsembleCohesionStore.readApproved(normalized, input) else EnsembleCohesionStore.readDraft(normalized, input)
        snapshot(normalized, input, plan, approved)
    }

    override fun reviewBoundary(root: Path, outgoingInstanceId: String, incomingInstanceId: String): EnsembleCohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = EnsembleCohesionStore.readDraft(normalized, input)
        EnsembleCohesionStore.markReviewed(normalized, input, outgoingInstanceId, incomingInstanceId)
        snapshot(normalized, input, plan, false)
    }

    override fun approve(root: Path): EnsembleCohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = EnsembleCohesionStore.readDraft(normalized, input)
        requireCohesionImprovement(normalized)?.let { (baselineCritic, candidateCritic) ->
            persistComparisons(normalized, input, baselineCritic, candidateCritic, StageEvidenceStatus.APPROVED)
        }
        EnsembleCohesionStore.approve(normalized, input)
        snapshot(normalized, input, plan, true)
    }

    override fun reject(root: Path): EnsembleCohesionSnapshot = locked(root) { normalized ->
        val input = currentInput(normalized, savedIntensity(normalized)); val plan = EnsembleCohesionStore.readDraft(normalized, input)
        val artifact = EnsembleCohesionStore.reject(normalized, input)
        snapshot(normalized, input, plan, false).copy(artifact = artifact)
    }

    private fun currentInput(root: Path, intensity: EnsembleCohesionEnhancementIntensity): EnsembleCohesionInput {
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
        return app.melotrail.arrangement.EnsembleTransitionContextFactory.build(
            root, project, planning, detailed, approval.arrangement.sha256, approval.contextSha256,
            sourceSongCritic.requireApprovedMelody(root).sourceSong.fullMelody.grooveMap, intensity
        )
    }
    private fun savedIntensity(root: Path): EnsembleCohesionEnhancementIntensity =
        ProjectStore.read(root).workflow.cohesion?.intensity ?: EnsembleCohesionEnhancementIntensity.BALANCED
    private fun analysis(root: Path, project: Project, partId: String): MidiAnalysis {
        val part = project.parts.firstOrNull { it.id == partId } ?: error("Structure references unknown part '$partId'.")
        val ref = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$partId'. Run part analyze first." }
        require(ref.kind?.name == "MIDI") { "MIDI analysis is required for part '$partId'. Run part analyze first." }
        val analysis = Json { ignoreUnknownKeys = false }.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(ref.file)))
        return ArrangementHarmonyContext.apply(analysis, part.sectionType, project)
    }
    private fun snapshot(root: Path, input: EnsembleCohesionInput, plan: EnsembleCohesionPlan, approved: Boolean): EnsembleCohesionSnapshot {
        val cohesionWorkflow = ProjectStore.read(root).workflow.cohesion
        val reviewed = cohesionWorkflow?.boundaries.orEmpty().filter { it.approved != null }.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet()
        val planner = if (plan.model == app.melotrail.arrangement.EnsembleCohesionModelIdentity.DETERMINISTIC) {
            EnsembleCohesionPlannerKind.DETERMINISTIC
        } else EnsembleCohesionPlannerKind.QWEN
        return EnsembleCohesionSnapshot(root, planner, input.inputHash, input.structureSha256,
            plan.boundaries.map { bridge -> EnsembleCohesionBoundarySnapshot(bridge.outgoingInstanceId, bridge.incomingInstanceId, root.resolve(EnsembleCohesionStore.bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId)), "${bridge.roleAction.name.lowercase().replace('_', ' ')}: ${bridge.rationale}", bridge.outgoingInstanceId to bridge.incomingInstanceId in reviewed) },
            approvalRequired = !approved, approved = approved, stale = false,
            artifact = root.resolve(if (approved) EnsembleCohesionStore.APPROVED_FILE else EnsembleCohesionStore.DRAFT_FILE),
            intensity = input.intensity,
            accompanimentEditCount = plan.boundaries.size,
            baselinePreview = cohesionWorkflow?.previews?.baseline?.file?.let(root::resolve),
            enhancedPreview = cohesionWorkflow?.previews?.enhanced?.file?.let(root::resolve))
    }
    /** Refuse approval when the exact draft increases the deterministic blocker or critical evidence. */
    private fun requireCohesionImprovement(root: Path): Pair<FullSongCriticReport, FullSongCriticReport>? {
        val project = ProjectStore.read(root); val cohesion = requireNotNull(project.workflow.cohesion)
        val roles = cohesion.roles.map { it.role }.toSet()
        if (roles.isEmpty()) return null
        val baseline = project.workflow.generatedMidi?.artifacts.orEmpty().filter { it.id in roles }.associate { it.id to it.artifact }
        val candidate = cohesion.roles.associate { it.role to it.result }
        require(baseline.keys == roles && candidate.keys == roles) { "Cohesion candidate does not cover every generated role." }
        val before = criticService.analyzeCohesionCandidate(root, baseline)
        val after = criticService.analyzeCohesionCandidate(root, candidate)
        requireNoCohesionIssueIncrease(before, after)
        return before to after
    }
    private fun persistComparisons(
        root: Path,
        input: EnsembleCohesionInput,
        baselineCritic: FullSongCriticReport? = null,
        candidateCritic: FullSongCriticReport? = null,
        status: StageEvidenceStatus = StageEvidenceStatus.DRAFT
    ) {
        val project = ProjectStore.read(root)
        val cohesion = requireNotNull(project.workflow.cohesion)
        val views = app.melotrail.arrangement.OccurrenceMidiArtifactResolver().resolve(root, project,
            project.envelope.structureOccurrences.mapIndexed { index, occurrence -> occurrence.toSectionInstance(index) }
        ).associateBy(app.melotrail.arrangement.OccurrenceMidiArtifact::occurrenceId)
        cohesion.occurrences.sortedBy { it.instanceId }.forEach { occurrence ->
            val approved = views.getValue(occurrence.instanceId)
            val before = WorkflowArtifactReference(approved.projectRelativePath, approved.sha256)
            val output = occurrence.result
            val beforeEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, before, input.contextSha256, role = "piano", occurrenceId = occurrence.instanceId, criticReport = baselineCritic)
            val afterEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, output, input.contextSha256, status, "piano", occurrence.instanceId, criticReport = candidateCritic)
            StageComparisonReportStore.write(root, afterEvidence, StageComparisonService().compare(root, beforeEvidence, afterEvidence))
        }
        val generated = project.workflow.generatedMidi?.artifacts.orEmpty().associateBy { it.id }
        cohesion.roles.sortedBy { it.role }.forEach { output ->
            val source = requireNotNull(generated[output.role]) { "Cohesion role '${output.role}' has no generated MIDI source." }.artifact
            val beforeEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, source, input.contextSha256, role = output.role, criticReport = baselineCritic)
            val afterEvidence = StageComparisonArtifact(StageComparisonStage.COHESION, output.result, input.contextSha256, status, output.role, criticReport = candidateCritic)
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
