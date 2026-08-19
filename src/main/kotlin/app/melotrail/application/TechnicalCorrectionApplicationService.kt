package app.melotrail.application

import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.TechnicalCorrectionArtifactPaths
import app.melotrail.arrangement.TechnicalCorrectionConfiguration
import app.melotrail.arrangement.TechnicalCorrectionContext
import app.melotrail.arrangement.TechnicalCorrectionContextFactory
import app.melotrail.arrangement.TechnicalCorrectionPlan
import app.melotrail.arrangement.TechnicalCorrectionPlanner
import app.melotrail.arrangement.TechnicalCorrectionProcessor
import app.melotrail.arrangement.TechnicalCorrectionReferences
import app.melotrail.arrangement.TechnicalCorrectionReport
import app.melotrail.arrangement.TechnicalCorrectionSelection
import app.melotrail.arrangement.DeterministicTechnicalCorrectionPlanner
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

data class CreateTechnicalCorrectionRequest(val root: Path, val partId: String)

/** UI-safe correction detail; paths and planner internals remain at the application boundary. */
data class TechnicalCorrectionSnapshot(
    val partId: String,
    val inputSha256: String,
    val outputSha256: String,
    val edits: List<app.melotrail.arrangement.TechnicalCorrectionEdit>,
    val warnings: List<String>,
    val approvalRequired: Boolean,
    val selected: Boolean,
    val available: Boolean
)

interface TechnicalCorrectionApplicationService {
    suspend fun create(request: CreateTechnicalCorrectionRequest): TechnicalCorrectionSnapshot
    fun load(root: Path, partId: String): TechnicalCorrectionSnapshot
    fun selectCorrected(root: Path, partId: String): TechnicalCorrectionSnapshot
    fun selectBase(root: Path, partId: String): TechnicalCorrectionSnapshot?
}

/**
 * Typed correction boundary. The default planner is deterministic; a future
 * advisor must supply the same path-free plan and pass the same validator.
 */
class DefaultTechnicalCorrectionApplicationService(
    private val configuration: TechnicalCorrectionConfiguration = TechnicalCorrectionConfiguration(),
    private val planner: TechnicalCorrectionPlanner = DeterministicTechnicalCorrectionPlanner(configuration),
    private val processor: TechnicalCorrectionProcessor = TechnicalCorrectionProcessor(configuration)
) : TechnicalCorrectionApplicationService {
    override suspend fun create(request: CreateTechnicalCorrectionRequest): TechnicalCorrectionSnapshot = withContext(Dispatchers.IO) {
        ProjectMutationCoordinator.lock(request.root.toAbsolutePath().normalize()).withLock {
                val root = request.root.toAbsolutePath().normalize()
                val current = current(root, request.partId)
                val plan = planner.plan(current.context).also { it.requireValid(current.context, configuration) }
                val outputRelative = TechnicalCorrectionArtifactPaths.output(request.partId, current.context.inputSha256)
                val reportRelative = TechnicalCorrectionArtifactPaths.report(request.partId, current.context.inputSha256)
                val planRelative = TechnicalCorrectionArtifactPaths.plan(request.partId, current.context.inputSha256)
                val output = root.resolve(outputRelative)
                val temporary = root.resolve("workflow-runs/work/correction-${request.partId}/corrected.mid")
                Files.createDirectories(requireNotNull(temporary.parent)); Files.deleteIfExists(temporary)
                val report = processor.correct(current.input, temporary, current.context, plan)
                publish(temporary, output)
                write(root.resolve(reportRelative), json.encodeToString(report))
                write(root.resolve(planRelative), json.encodeToString(plan))
                val refs = TechnicalCorrectionReferences(
                    input = WorkflowArtifactReference(current.reference, current.context.inputSha256),
                    output = WorkflowArtifactReference(outputRelative, report.outputSha256),
                    report = WorkflowArtifactReference(reportRelative, sha256(root.resolve(reportRelative))),
                    contextSha256 = report.contextSha256
                )
                val project = ProjectStore.read(root)
                ProjectStore.write(root, project.copy(parts = project.parts.map { part ->
                    if (part.id == request.partId) part.copy(midi = requireNotNull(part.midi).copy(technicalCorrection = refs)) else part
                }, workflow = project.workflow.markCurrent(WorkflowArtifact.CORRECTED_MIDI)))
                snapshot(root, request.partId, refs, report, selected = false)
        }
    }

    override fun load(root: Path, partId: String): TechnicalCorrectionSnapshot = locked(root) { normalized ->
        val current = current(normalized, partId); val refs = requireNotNull(ProjectStore.read(normalized).parts.single { it.id == partId }.midi?.technicalCorrection) { "No technical correction exists. Create one first." }
        refs.requireCanonical(partId); require(refs.input.sha256 == current.context.inputSha256) { "Technical correction is stale. Create it again." }
        val report = readReport(normalized.resolve(refs.report.file)); require(report.inputSha256 == refs.input.sha256 && report.outputSha256 == refs.output.sha256 && report.contextSha256 == refs.contextSha256) { "Technical-correction report is stale." }
        snapshot(normalized, partId, refs, report, ProjectStore.read(normalized).parts.single { it.id == partId }.midi!!.technicalCorrectionSelection == TechnicalCorrectionSelection.CORRECTED)
    }

    override fun selectCorrected(root: Path, partId: String): TechnicalCorrectionSnapshot {
        val loaded = load(root, partId)
        return locked(root) { normalized ->
        require(!loaded.approvalRequired) { "Technical correction has low-confidence warnings and remains unchanged until reviewed." }
        val project = ProjectStore.read(normalized); val part = project.parts.single { it.id == partId }; val midi = requireNotNull(part.midi)
        ProjectStore.write(normalized, project.copy(parts = project.parts.map {
            if (it.id == partId) it.copy(analysis = null, midi = midi.copy(technicalCorrectionSelection = TechnicalCorrectionSelection.CORRECTED,
                aiFixSelection = app.melotrail.arrangement.MidiAiFixSelection.SKIP, analysisInput = app.melotrail.arrangement.MidiAnalysisInput.CURRENT, feel = null)) else it
        }, workflow = project.workflow.invalidate(WorkflowChange.CORRECTION_SELECTION).markCurrent(WorkflowArtifact.CORRECTED_MIDI)))
            loaded.copy(selected = true)
        }
    }

    override fun selectBase(root: Path, partId: String): TechnicalCorrectionSnapshot? = locked(root) { normalized ->
        val project = ProjectStore.read(normalized); val part = project.parts.singleOrNull { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        val midi = requireNotNull(part.midi); val refs = midi.technicalCorrection ?: return@locked null
        val wasSelected = midi.technicalCorrectionSelection == TechnicalCorrectionSelection.CORRECTED
        ProjectStore.write(normalized, project.copy(parts = project.parts.map {
            if (it.id == partId) it.copy(analysis = if (wasSelected) null else it.analysis, midi = midi.copy(technicalCorrectionSelection = TechnicalCorrectionSelection.BASE,
                analysisInput = app.melotrail.arrangement.MidiAnalysisInput.CURRENT, feel = null)) else it
        }, workflow = if (wasSelected) project.workflow.invalidate(WorkflowChange.CORRECTION_SELECTION).markCurrent(WorkflowArtifact.CORRECTED_MIDI) else project.workflow))
        val report = readReport(normalized.resolve(refs.report.file)); snapshot(normalized, partId, refs, report, false)
    }

    private data class Current(val input: Path, val reference: String, val context: TechnicalCorrectionContext)
    private fun current(root: Path, partId: String): Current {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        require(project.version >= Project.MIDI_FIRST_VERSION) { "Technical correction requires a MIDI-first project." }
        val part = project.parts.singleOrNull { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no MIDI evidence." }
        val reference = midi.transposed ?: midi.normalized ?: midi.clean ?: throw IllegalArgumentException("Part '$partId' has no cleaned MIDI.")
        val relative = Path.of(reference); val input = root.resolve(relative).normalize()
        require(!relative.isAbsolute && input.startsWith(root) && Files.isRegularFile(input) && input.toRealPath().startsWith(root.toRealPath())) { "Technical-correction input is missing or unsafe." }
        return Current(input, reference, TechnicalCorrectionContextFactory.build(project, partId, input))
    }

    private fun snapshot(root: Path, partId: String, refs: TechnicalCorrectionReferences, report: TechnicalCorrectionReport, selected: Boolean): TechnicalCorrectionSnapshot {
        val available = runCatching {
            refs.requireCanonical(partId); sha256(root.resolve(refs.input.file)) == refs.input.sha256 && sha256(root.resolve(refs.output.file)) == refs.output.sha256 &&
                sha256(root.resolve(refs.report.file)) == refs.report.sha256
        }.getOrDefault(false)
        return TechnicalCorrectionSnapshot(partId, report.inputSha256, report.outputSha256, report.edits, report.warnings, report.approvalRequired, selected, available)
    }

    private fun <T> locked(root: Path, block: (Path) -> T): T {
        val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized)
        check(lock.tryLock()) { "Another technical-correction operation is already running: $normalized" }
        return try { block(normalized) } finally { lock.unlock() }
    }

    private fun publish(temporary: Path, target: Path) {
        Files.createDirectories(requireNotNull(target.parent))
        val staged = target.resolveSibling(".${target.fileName}.save")
        try {
            Files.copy(temporary, staged, StandardCopyOption.REPLACE_EXISTING)
            try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for corrected MIDI.", error) }
        } finally { Files.deleteIfExists(staged); Files.deleteIfExists(temporary) }
    }

    private fun write(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.save")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for technical-correction evidence.", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun readReport(path: Path): TechnicalCorrectionReport = try { json.decodeFromString(TechnicalCorrectionReport.serializer(), Files.readString(path, StandardCharsets.UTF_8)) }
    catch (error: Exception) { throw IllegalArgumentException("Technical-correction report is malformed.", error) }

    @OptIn(ExperimentalSerializationApi::class)
    private companion object { val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}
