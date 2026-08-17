package app.melotrail.application

import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.DeterministicMelodyCohesionPlanner
import app.melotrail.arrangement.LocalQwenMelodyCohesionPlanner
import app.melotrail.arrangement.MelodyCohesionInput
import app.melotrail.arrangement.MelodyCohesionInputFactory
import app.melotrail.arrangement.MelodyCohesionPlan
import app.melotrail.arrangement.MelodyCohesionStore
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlanningInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Bounded planner choice. The UI never supplies model output, paths, or model configuration. */
enum class CohesionPlannerKind { DETERMINISTIC, QWEN }

data class GenerateCohesionRequest(val root: Path, val planner: CohesionPlannerKind = CohesionPlannerKind.DETERMINISTIC)

data class CohesionOccurrenceSnapshot(
    val instanceId: String,
    val partId: String,
    val sourceHash: String,
    val hasDerivedMidi: Boolean,
    val approved: Boolean,
    val rationale: String
)

data class CohesionSnapshot(
    val root: Path,
    val planner: CohesionPlannerKind,
    val inputHash: String,
    val occurrences: List<CohesionOccurrenceSnapshot>,
    val approvalRequired: Boolean,
    val approved: Boolean,
    val stale: Boolean,
    val artifact: Path
)

interface CohesionApplicationService {
    suspend fun generate(request: GenerateCohesionRequest, progress: ProgressSink = ProgressSink.None): CohesionSnapshot
    fun load(root: Path): CohesionSnapshot
    fun approve(root: Path): CohesionSnapshot
    fun reject(root: Path): CohesionSnapshot
    suspend fun regenerate(request: GenerateCohesionRequest, progress: ProgressSink = ProgressSink.None): CohesionSnapshot = generate(request, progress)
}

/**
 * Application boundary for reviewable per-occurrence cohesion. It rebuilds the
 * path-free input from current canonical evidence on every command, so an old
 * draft cannot be approved after its source, analysis, or structure changed.
 */
class DefaultCohesionApplicationService(
    private val qwenPlanner: ((MelodyCohesionInput) -> MelodyCohesionPlan) = { input ->
        LocalQwenMelodyCohesionPlanner(
            model = CohesionModelIdentity("qwen", "local", "0".repeat(64))
        ).plan(input)
    }
) : CohesionApplicationService {
    override suspend fun generate(request: GenerateCohesionRequest, progress: ProgressSink): CohesionSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("cohesion", 1, 3, "Validating current selected MIDI"))
        val (input, sources) = currentInput(root)
        progress.report(OperationProgress("cohesion", 2, 3, "Creating bounded per-occurrence cohesion plan"))
        val plan = when (request.planner) {
            CohesionPlannerKind.DETERMINISTIC -> DeterministicMelodyCohesionPlanner().plan(input)
            CohesionPlannerKind.QWEN -> qwenPlanner(input)
        }
        MelodyCohesionStore.writeDraft(root, input, plan)
        return@mutate if (request.planner == CohesionPlannerKind.DETERMINISTIC) {
            progress.report(OperationProgress("cohesion", 3, 3, "Publishing safe deterministic cohesion"))
            MelodyCohesionStore.approve(root, input, sources)
            snapshot(root, input, plan, CohesionPlannerKind.DETERMINISTIC, approved = true, stale = false)
        } else {
            progress.report(OperationProgress("cohesion", 3, 3, "Cohesion draft is ready for review"))
            snapshot(root, input, plan, CohesionPlannerKind.QWEN, approved = false, stale = false)
        }
    }

    override fun load(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val (input, _) = currentInput(normalized)
        val workflow = ProjectStore.read(normalized).workflow.cohesion
        val approved = workflow?.approved == true && workflow.inputSha256 == input.inputHash
        val artifact = if (approved) normalized.resolve(MelodyCohesionStore.APPROVED_FILE) else normalized.resolve(MelodyCohesionStore.DRAFT_FILE)
        require(Files.isRegularFile(artifact)) { "No cohesion plan found. Generate cohesion first." }
        val plan = if (approved) MelodyCohesionStore.readApproved(normalized, input) else MelodyCohesionStore.readDraft(normalized, input)
        snapshot(normalized, input, plan, if (plan.model == CohesionModelIdentity.DETERMINISTIC) CohesionPlannerKind.DETERMINISTIC else CohesionPlannerKind.QWEN, approved, stale = false)
    }

    override fun approve(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val (input, sources) = currentInput(normalized)
        val plan = MelodyCohesionStore.readDraft(normalized, input)
        MelodyCohesionStore.approve(normalized, input, sources)
        snapshot(normalized, input, plan, if (plan.model == CohesionModelIdentity.DETERMINISTIC) CohesionPlannerKind.DETERMINISTIC else CohesionPlannerKind.QWEN, approved = true, stale = false)
    }

    override fun reject(root: Path): CohesionSnapshot = locked(root) { normalized ->
        val (input, _) = currentInput(normalized)
        val plan = MelodyCohesionStore.readDraft(normalized, input)
        val artifact = MelodyCohesionStore.reject(normalized, input)
        snapshot(normalized, input, plan, if (plan.model == CohesionModelIdentity.DETERMINISTIC) CohesionPlannerKind.DETERMINISTIC else CohesionPlannerKind.QWEN, approved = false, stale = false).copy(artifact = artifact)
    }

    private fun currentInput(root: Path): Pair<MelodyCohesionInput, Map<String, List<app.melotrail.arrangement.MidiNote>>> {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        require(project.version == Project.CURRENT_VERSION) { "Cohesion requires a MIDI-first v3 project." }
        val structure = project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        require(structure.isNotEmpty()) { "Save a non-empty structure before generating cohesion." }
        val analyses = structure.map(SectionInstance::partId).distinct().associateWith { partId -> analysis(root, project, partId) }
        val planning = SongPlanningInput(project.name, project.version, analyses, structure, app.melotrail.arrangement.LogicalInstrument.entries.map { it.wireName })
        planning.requireValid()
        return MelodyCohesionInputFactory.build(root, project, planning)
    }

    private fun analysis(root: Path, project: Project, partId: String): MidiAnalysis {
        val part = project.parts.firstOrNull { it.id == partId } ?: error("Structure references unknown part '$partId'.")
        val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$partId'. Run part analyze first." }
        require(reference.kind?.name == "MIDI") { "MIDI analysis is required for part '$partId'. Run part analyze first." }
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = false }.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(reference.file)))
    }

    private fun snapshot(root: Path, input: MelodyCohesionInput, plan: MelodyCohesionPlan, planner: CohesionPlannerKind, approved: Boolean, stale: Boolean): CohesionSnapshot =
        CohesionSnapshot(root, planner, input.inputHash, plan.occurrences.map { occurrence ->
            CohesionOccurrenceSnapshot(occurrence.instanceId, occurrence.partId, occurrence.sourceHash, Files.isRegularFile(MelodyCohesionStore.derivedMidi(root, occurrence.instanceId)), approved, occurrence.rationale)
        }, approvalRequired = !approved, approved = approved, stale = stale, artifact = root.resolve(if (approved) MelodyCohesionStore.APPROVED_FILE else MelodyCohesionStore.DRAFT_FILE))

    private suspend fun <T> mutate(root: Path, block: suspend (Path) -> T): T {
        val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }
        check(lock.tryLock()) { "Another cohesion operation is already running: $normalized" }
        return try { withContext(Dispatchers.IO) { block(normalized) } } finally { lock.unlock() }
    }
    private fun <T> locked(root: Path, block: (Path) -> T): T {
        val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }
        check(lock.tryLock()) { "Another cohesion operation is already running: $normalized" }
        return try { block(normalized) } finally { lock.unlock() }
    }
    private companion object { val locks = ConcurrentHashMap<Path, Mutex>() }
}
