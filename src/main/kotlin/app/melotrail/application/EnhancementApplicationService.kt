package app.melotrail.application

import app.melotrail.arrangement.EnhancementApproval
import app.melotrail.arrangement.EnhancementArtifactPaths
import app.melotrail.arrangement.EnhancementEditReport
import app.melotrail.arrangement.EnhancementEdit
import app.melotrail.arrangement.EnhancementExecutionService
import app.melotrail.arrangement.EnhancementIntensity
import app.melotrail.arrangement.EnhancementModelIdentity
import app.melotrail.arrangement.EnhancementPlan
import app.melotrail.arrangement.EnhancementPlanner
import app.melotrail.arrangement.EnhancementReferences
import app.melotrail.arrangement.EnhancementSelection
import app.melotrail.arrangement.LocalQwenEnhancementPlanner
import app.melotrail.arrangement.MusicalProcessingContextFactory
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ValidatedEnhancementMidiApplier
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.sha256
import app.melotrail.profile.BundledCompositionProfileCatalog
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

data class CreateEnhancementRequest(val root: Path, val partId: String, val intensity: EnhancementIntensity = EnhancementIntensity.SUBTLE, val seed: Long = 0)
data class ApproveEnhancementRequest(val root: Path, val partId: String, val draftSha256: String, val inputSha256: String, val contextSha256: String)

data class EnhancementSnapshot(
    val partId: String, val intensity: EnhancementIntensity, val inputSha256: String, val draftSha256: String,
    val contextSha256: String, val approval: EnhancementApproval, val edits: Int, val reasons: List<String>, val placeholder: Boolean
)

interface EnhancementApplicationService {
    suspend fun create(request: CreateEnhancementRequest): EnhancementSnapshot
    fun load(root: Path, partId: String): EnhancementSnapshot
    fun approve(request: ApproveEnhancementRequest): EnhancementSnapshot
    fun reject(root: Path, partId: String): EnhancementSnapshot
}

/** File-owning enhancement boundary. The model returns a plan only; this service owns all publication and selection. */
class DefaultEnhancementApplicationService(
    private val planner: EnhancementPlanner = LocalQwenEnhancementPlanner(
        identity = EnhancementModelIdentity("qwen", "local", System.getenv("QWEN_ENHANCEMENT_VERSION") ?: "1", System.getenv("QWEN_ENHANCEMENT_LICENSE") ?: "unknown")
    ),
    private val applier: app.melotrail.arrangement.EnhancementPlanApplier = ValidatedEnhancementMidiApplier()
) : EnhancementApplicationService {
    override suspend fun create(request: CreateEnhancementRequest): EnhancementSnapshot = withContext(Dispatchers.IO) {
        ProjectMutationCoordinator.lock(request.root.toAbsolutePath().normalize()).withLock {
            require(request.intensity != EnhancementIntensity.OFF) { "Use rejection to select Corrected MIDI." }
            val root = request.root.toAbsolutePath().normalize(); val current = current(root, request.partId)
            val context = MusicalProcessingContextFactory.build(current.project, request.partId, current.input, request.intensity, request.seed, profiles = BundledCompositionProfileCatalog.load())
            val plan = planner.plan(context)
            val outputRelative = EnhancementArtifactPaths.output(request.partId, context.contextSha256)
            val reportRelative = EnhancementArtifactPaths.report(request.partId, context.contextSha256)
            val planRelative = EnhancementArtifactPaths.plan(request.partId, context.contextSha256)
            val provenanceRelative = EnhancementArtifactPaths.provenance(request.partId, context.contextSha256)
            val temporary = root.resolve("workflow-runs/work/enhancement-${request.partId}/enhanced.mid")
            Files.createDirectories(requireNotNull(temporary.parent)); Files.deleteIfExists(temporary)
            val report = EnhancementExecutionService(EnhancementPlanner { plan }, applier).enhance(current.input, temporary, context)
            publish(temporary, root.resolve(outputRelative), "enhanced MIDI")
            write(root.resolve(reportRelative), json.encodeToString(report))
            write(root.resolve(planRelative), json.encodeToString(plan))
            write(root.resolve(provenanceRelative), json.encodeToString(EnhancementProvenance(context.contextSha256, report.acceptedPlanSha256 ?: sha256(root.resolve(planRelative)), plan.model, plan.templateVersion)))
            val refs = EnhancementReferences(request.intensity, current.inputRef, WorkflowArtifactReference(outputRelative, report.outputSha256!!),
                WorkflowArtifactReference(reportRelative, sha256(root.resolve(reportRelative))), context.contextSha256, EnhancementApproval.DRAFT,
                WorkflowArtifactReference(planRelative, sha256(root.resolve(planRelative))), WorkflowArtifactReference(provenanceRelative, sha256(root.resolve(provenanceRelative))))
            update(root, request.partId, refs, EnhancementSelection.CORRECTED)
            snapshot(request.partId, refs, report)
        }
    }

    override fun load(root: Path, partId: String): EnhancementSnapshot = locked(root) { normalized ->
        val refs = requireNotNull(ProjectStore.read(normalized).parts.singleOrNull { it.id == partId }?.midi?.enhancement) { "No enhancement draft exists." }
        val report = readReport(normalized.resolve(refs.report.file)); require(report.contextSha256 == refs.contextSha256 && report.outputSha256 == refs.output.sha256) { "Enhancement evidence is stale." }
        snapshot(partId, refs, report)
    }

    override fun approve(request: ApproveEnhancementRequest): EnhancementSnapshot = locked(request.root) { root ->
        val current = current(root, request.partId); val refs = requireNotNull(current.midi.enhancement) { "No enhancement draft exists." }
        require(refs.approval == EnhancementApproval.DRAFT && refs.output.sha256 == request.draftSha256 && refs.input.sha256 == request.inputSha256 && refs.contextSha256 == request.contextSha256) { "Enhancement approval does not match the reviewed draft." }
        val report = readReport(root.resolve(refs.report.file)); require(report.acceptedPlanSha256 != null && report.anchorsRetained) { "Enhancement draft is missing validated plan evidence." }
        val approved = refs.copy(approval = EnhancementApproval.APPROVED); update(root, request.partId, approved, EnhancementSelection.ENHANCED)
        snapshot(request.partId, approved, report)
    }

    override fun reject(root: Path, partId: String): EnhancementSnapshot = locked(root) { normalized ->
        val refs = requireNotNull(ProjectStore.read(normalized).parts.singleOrNull { it.id == partId }?.midi?.enhancement) { "No enhancement draft exists." }
        val rejected = refs.copy(approval = EnhancementApproval.REJECTED); update(normalized, partId, rejected, EnhancementSelection.CORRECTED)
        snapshot(partId, rejected, readReport(normalized.resolve(refs.report.file)))
    }

    private data class Current(val project: app.melotrail.arrangement.Project, val midi: app.melotrail.arrangement.MidiReferences, val input: Path, val inputRef: WorkflowArtifactReference)
    private fun current(root: Path, partId: String): Current {
        val project = ProjectStore.read(root).also { it.requireValid(root) }; val part = project.parts.singleOrNull { it.id == partId } ?: error("Part not found: $partId")
        val midi = requireNotNull(part.midi); require(midi.technicalCorrectionSelection == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) { "Select corrected MIDI before enhancement." }
        val correction = requireNotNull(midi.technicalCorrection); val path = root.resolve(correction.output.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == correction.output.sha256) { "Corrected MIDI is missing or stale." }
        return Current(project, midi, path, correction.output)
    }
    private fun update(root: Path, partId: String, refs: EnhancementReferences, selection: EnhancementSelection) {
        val project = ProjectStore.read(root); ProjectStore.write(root, project.copy(parts = project.parts.map { part -> if (part.id == partId) part.copy(analysis = null, midi = requireNotNull(part.midi).copy(enhancement = refs, enhancementSelection = selection, analysisInput = app.melotrail.arrangement.MidiAnalysisInput.CURRENT, feel = null)) else part }, workflow = project.workflow.invalidate(WorkflowChange.ENHANCEMENT_SELECTION).markCurrent(WorkflowArtifact.ENHANCED_MIDI)))
    }
    private fun snapshot(partId: String, refs: EnhancementReferences, report: EnhancementEditReport) = EnhancementSnapshot(partId, refs.intensity, refs.input.sha256, refs.output.sha256, refs.contextSha256, refs.approval, report.appliedEdits.size, report.appliedEdits.map(EnhancementEdit::reason), report.placeholder)
    private fun locked(root: Path, block: (Path) -> EnhancementSnapshot): EnhancementSnapshot { val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized); check(lock.tryLock()) { "Another enhancement operation is already running." }; return try { block(normalized) } finally { lock.unlock() } }
    private fun publish(source: Path, target: Path, label: String) { Files.createDirectories(requireNotNull(target.parent)); val staged = target.resolveSibling(".${target.fileName}.save"); try { Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING); try { Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for $label.", error) } } finally { Files.deleteIfExists(staged); Files.deleteIfExists(source) } }
    private fun write(path: Path, text: String) { Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.save"); try { Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } finally { Files.deleteIfExists(temporary) } }
    private fun readReport(path: Path): EnhancementEditReport = try { json.decodeFromString(EnhancementEditReport.serializer(), Files.readString(path)) } catch (error: Exception) { throw IllegalArgumentException("Enhancement report is malformed.", error) }
    @OptIn(ExperimentalSerializationApi::class) private companion object { val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}

@kotlinx.serialization.Serializable
data class EnhancementProvenance(val contextSha256: String, val acceptedPlanSha256: String, val model: EnhancementModelIdentity?, val templateVersion: String)
