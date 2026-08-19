package app.melotrail.application

import app.melotrail.arrangement.HumanizationArtifactReference
import app.melotrail.arrangement.HumanizationConfig
import app.melotrail.arrangement.HumanizationReport
import app.melotrail.arrangement.HumanizationRole
import app.melotrail.arrangement.HumanizationSelection
import app.melotrail.arrangement.HumanizationWorkflowReferences
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SeededHumanizationProcessor
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.sha256
import app.melotrail.profile.BundledCompositionProfileCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class GenerateHumanizationRequest(
    val root: Path,
    val amountPercent: Int? = null,
    val seed: Long? = null
)

data class HumanizationSnapshot(
    val selection: HumanizationSelection,
    val config: HumanizationConfig?,
    val seed: Long?,
    val artifacts: Int,
    val changedNotes: Int,
    val warnings: List<String>
)

interface HumanizationApplicationService {
    suspend fun generate(request: GenerateHumanizationRequest): HumanizationSnapshot
    fun selectBypass(root: Path): HumanizationSnapshot
    fun load(root: Path): HumanizationSnapshot
}

/**
 * Project-owned post-Cohesion stage. It receives no model, worker, renderer or
 * local-library path: only already validated MIDI and bounded profile policy.
 */
class DefaultHumanizationApplicationService(
    private val processor: SeededHumanizationProcessor = SeededHumanizationProcessor()
) : HumanizationApplicationService {
    override suspend fun generate(request: GenerateHumanizationRequest): HumanizationSnapshot = withContext(Dispatchers.IO) {
        val root = request.root.toAbsolutePath().normalize()
        ProjectMutationCoordinator.lock(root).withLock {
            val project = ProjectStore.read(root).also { it.requireValid(root) }
            require(project.workflow.arrangement != null && WorkflowArtifact.ARRANGEMENT !in project.workflow.stale) {
                "Humanization requires a current approved arrangement."
            }
            require(project.workflow.cohesion?.approved == true && WorkflowArtifact.COHESION !in project.workflow.stale) {
                "Humanization requires current approved Cohesion."
            }
            val config = profileDefault(project).let { default ->
                request.amountPercent?.let { default.copy(amountPercent = it) } ?: default
            }.also(HumanizationConfig::requireValid)
            val selected = app.melotrail.arrangement.SelectedMidiArtifactResolver()
            val inputs = buildList {
                project.parts.sortedBy { it.id }.forEach { part ->
                    val artifact = selected.resolve(root, project, part.id)
                    add(Input("piano-${part.id}", HumanizationRole.PIANO, artifact.projectRelativePath, artifact.sha256,
                        part.midi?.analysisInput == MidiAnalysisInput.LOFI_FEEL))
                }
                listOf("bass" to HumanizationRole.BASS, "drums" to HumanizationRole.DRUMS, "pad" to HumanizationRole.PAD, "strings" to HumanizationRole.STRINGS, "transitions" to HumanizationRole.TRANSITIONS).forEach { (id, role) ->
                    val path = root.resolve("midi/generated/$id.mid")
                    if (Files.isRegularFile(path)) add(Input(id, role, "midi/generated/$id.mid", sha256(path), false))
                }
            }
            require(inputs.isNotEmpty()) { "Humanization requires selected arrangement MIDI." }
            val inputHash = digest(inputs.joinToString("|") { "${it.id}:${it.sha256}" }.toByteArray(StandardCharsets.UTF_8))
            val seed = request.seed ?: (project.workflow.humanization?.seed?.plus(1) ?: 1L)
            val runId = digest("$inputHash|$config|$seed|seeded-humanization-v1".toByteArray(StandardCharsets.UTF_8))
            val reports = mutableListOf<HumanizationReport>()
            val references = inputs.map { input ->
                val outputRelative = "midi/humanized/$runId/${input.id}.mid"
                val output = root.resolve(outputRelative)
                val report = processor.transform(root.resolve(input.file), output, input.role, config, seed, input.legacyGroove).report
                reports += report
                HumanizationArtifactReference(input.id, input.role, WorkflowArtifactReference(input.file, input.sha256), WorkflowArtifactReference(outputRelative, report.outputSha256))
            }
            val reportRelative = "midi/humanized/$runId/report.json"
            val aggregate = HumanizationRunReport(inputHash, config, seed, reports)
            atomicWrite(root.resolve(reportRelative), json.encodeToString(aggregate))
            val refs = HumanizationWorkflowReferences(config, seed, "seeded-humanization-v1", inputHash, references,
                WorkflowArtifactReference(reportRelative, sha256(root.resolve(reportRelative))), inputs.filter(Input::legacyGroove).map(Input::id).toSet())
            ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(WorkflowChange.HUMANIZATION)
                .markCurrent(WorkflowArtifact.HUMANIZATION)
                .copy(humanizationSelection = HumanizationSelection.HUMANIZED, humanization = refs)))
            snapshot(HumanizationSelection.HUMANIZED, refs, aggregate)
        }
    }

    override fun selectBypass(root: Path): HumanizationSnapshot = locked(root) { normalized ->
        val project = ProjectStore.read(normalized).also { it.requireValid(normalized) }
        ProjectStore.write(normalized, project.copy(workflow = project.workflow.invalidate(WorkflowChange.HUMANIZATION)
            .copy(humanizationSelection = HumanizationSelection.BYPASS)))
        HumanizationSnapshot(HumanizationSelection.BYPASS, project.workflow.humanization?.config, project.workflow.humanization?.seed, 0, 0,
            listOf("Bypass selected; Render uses cohesive MIDI input."))
    }

    override fun load(root: Path): HumanizationSnapshot = locked(root) { normalized ->
        val workflow = ProjectStore.read(normalized).workflow
        val refs = workflow.humanization
        if (workflow.humanizationSelection == HumanizationSelection.BYPASS || refs == null) {
            HumanizationSnapshot(HumanizationSelection.BYPASS, refs?.config, refs?.seed, 0, 0, listOf("Bypass selected; Render uses cohesive MIDI input."))
        } else {
            val report = decode(normalized.resolve(refs.report.file))
            require(report.inputSha256 == refs.inputsSha256 && report.seed == refs.seed && report.config == refs.config) { "Humanization report is stale." }
            snapshot(HumanizationSelection.HUMANIZED, refs, report)
        }
    }

    private fun profileDefault(project: app.melotrail.arrangement.Project): HumanizationConfig {
        val settings = requireNotNull(project.envelope.compositionSettings?.takeIf { it.complete }) { "Complete project Setup before humanization." }
        val resolved = BundledCompositionProfileCatalog.load().resolve(requireNotNull(settings.profile), settings.mood)
        return HumanizationConfig(
            amountPercent = 50,
            timingMaxMs = resolved.humanizationMs,
            velocityMaxDelta = resolved.velocityTolerance.coerceIn(0, 32),
            durationMaxMs = (resolved.humanizationMs / 2).coerceAtMost(40),
            chordStaggerMs = (resolved.humanizationMs / 3).coerceAtMost(24),
            swingPercent = resolved.swingPercent,
            drumTimingPercent = 70,
            bassTimingPercent = 80
        )
    }

    private fun snapshot(selection: HumanizationSelection, refs: HumanizationWorkflowReferences, report: HumanizationRunReport) =
        HumanizationSnapshot(selection, refs.config, refs.seed, refs.artifacts.size, report.reports.sumOf { it.summary.changedNotes }, report.reports.flatMap(HumanizationReport::warnings))
    private fun decode(path: Path): HumanizationRunReport = try { json.decodeFromString(HumanizationRunReport.serializer(), Files.readString(path)) }
    catch (error: Exception) { throw IllegalArgumentException("Humanization report is malformed.", error) }
    private fun locked(root: Path, block: (Path) -> HumanizationSnapshot): HumanizationSnapshot {
        val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized)
        check(lock.tryLock()) { "Another project mutation is already running." }; return try { block(normalized) } finally { lock.unlock() }
    }
    private fun atomicWrite(target: Path, text: String) {
        Files.createDirectories(requireNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try { Files.writeString(temporary, text, StandardCharsets.UTF_8); try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for humanization evidence.", error) } } finally { Files.deleteIfExists(temporary) }
    }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private data class Input(val id: String, val role: HumanizationRole, val file: String, val sha256: String, val legacyGroove: Boolean)
    private companion object { val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false } }
}

@Serializable
data class HumanizationRunReport(
    val inputSha256: String,
    val config: HumanizationConfig,
    val seed: Long,
    val reports: List<HumanizationReport>
) { init { require(reports.isNotEmpty()) { "Humanization run report is empty" } } }
