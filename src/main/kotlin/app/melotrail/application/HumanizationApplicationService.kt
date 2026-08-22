package app.melotrail.application

import app.melotrail.arrangement.HumanizationArtifactReference
import app.melotrail.arrangement.HumanizationConfig
import app.melotrail.arrangement.HumanizationReport
import app.melotrail.arrangement.HumanizationRole
import app.melotrail.arrangement.HumanizationSelection
import app.melotrail.arrangement.HumanizationWorkflowReferences
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.OccurrenceMidiArtifactResolver
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SeededHumanizationProcessor
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.sha256
import app.melotrail.arrangement.toSectionInstance
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
import java.nio.ByteBuffer

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
            require(project.workflow.critic != null && WorkflowArtifact.CRITIC !in project.workflow.stale) {
                "Humanization requires a current Critic report after Cohesion."
            }
            require(project.workflow.fullSongEnhancementSelection != app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED) {
                "Humanization requires an approved Full-Song Enhance candidate, recorded no-op, or explicit bypass."
            }
            require(project.workflow.fullSongEnhancementSelection != app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED ||
                (project.workflow.fullSongEnhancement != null && WorkflowArtifact.FULL_SONG_ENHANCEMENT !in project.workflow.stale)) {
                "Humanization requires current Full-Song Enhance candidate evidence."
            }
            val config = profileDefault(project).let { default ->
                request.amountPercent?.let { default.copy(amountPercent = it) } ?: default
            }.also(HumanizationConfig::requireValid)
            val occurrences = project.envelope.structureOccurrences.mapIndexed { index, occurrence -> occurrence.toSectionInstance(index) }
            val cohesiveOccurrences = OccurrenceMidiArtifactResolver().resolve(root, project, occurrences)
            val inputs = buildList {
                cohesiveOccurrences.forEach { artifact ->
                    val part = project.parts.single { it.id == artifact.partId }
                    val selected = fullSongEnhancedInput(root, project, "piano-${artifact.occurrenceId}", WorkflowArtifactReference(artifact.projectRelativePath, artifact.sha256))
                    add(Input("piano-${artifact.occurrenceId}", HumanizationRole.PIANO, selected.file, selected.sha256,
                        part.midi?.analysisInput == MidiAnalysisInput.LOFI_FEEL))
                }
                val cohesion = requireNotNull(project.workflow.cohesion)
                cohesion.roles.forEach { reference ->
                    require(reference.approved && reference.cohesionInputSha256 == cohesion.inputSha256) { "Cohesion role '${reference.role}' is stale." }
                    val path = root.resolve(reference.result.file).normalize()
                    require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == reference.result.sha256) { "Cohesion role '${reference.role}' is missing or changed." }
                    val role = when (reference.role) {
                        "bass" -> HumanizationRole.BASS
                        "drums" -> HumanizationRole.DRUMS
                        "pad" -> HumanizationRole.PAD
                        "strings" -> HumanizationRole.STRINGS
                        else -> error("Unsupported cohesive role '${reference.role}'.")
                    }
                    val selected = fullSongEnhancedInput(root, project, reference.role, reference.result)
                    add(Input(reference.role, role, selected.file, selected.sha256, false))
                }
            }
            require(inputs.isNotEmpty()) { "Humanization requires selected arrangement MIDI." }
            val inputHash = digest(inputs.joinToString("|") { "${it.id}:${it.sha256}" }.toByteArray(StandardCharsets.UTF_8))
            val seed = request.seed ?: (project.workflow.humanization?.seed?.plus(1) ?: 1L)
            val runId = digest("$inputHash|$config|$seed|seeded-humanization-v2".toByteArray(StandardCharsets.UTF_8))
            val reports = mutableListOf<HumanizationReport>()
            val references = inputs.map { input ->
                val outputRelative = "midi/humanized/$runId/${input.id}.mid"
                val output = root.resolve(outputRelative)
                val report = processor.transform(root.resolve(input.file), output, input.role, config, scopedSeed(seed, input.id), input.legacyGroove).report
                reports += report
                HumanizationArtifactReference(input.id, input.role, WorkflowArtifactReference(input.file, input.sha256), WorkflowArtifactReference(outputRelative, report.outputSha256))
            }
            val reportRelative = "midi/humanized/$runId/report.json"
            val aggregate = HumanizationRunReport(inputHash, config, seed, reports)
            atomicWrite(root.resolve(reportRelative), json.encodeToString(aggregate))
            val refs = HumanizationWorkflowReferences(config, seed, "seeded-humanization-v2", inputHash, references,
                WorkflowArtifactReference(reportRelative, sha256(root.resolve(reportRelative))), inputs.filter(Input::legacyGroove).map(Input::id).toSet())
            ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(WorkflowChange.HUMANIZATION)
                .markCurrent(WorkflowArtifact.HUMANIZATION)
                .copy(humanizationSelection = HumanizationSelection.HUMANIZED, humanization = refs)))
            persistComparisons(root, refs)
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
            require(WorkflowArtifact.HUMANIZATION !in workflow.stale) { "Humanization selection is stale. Regenerate it or select Bypass." }
            refs.artifacts.forEach { artifact ->
                val input = normalized.resolve(artifact.input.file).normalize()
                val output = normalized.resolve(artifact.output.file).normalize()
                require(input.startsWith(normalized) && output.startsWith(normalized) && Files.isRegularFile(input) && Files.isRegularFile(output) &&
                    sha256(input) == artifact.input.sha256 && sha256(output) == artifact.output.sha256) {
                    "Humanization MIDI '${artifact.id}' is missing or stale. Regenerate it or select Bypass."
                }
            }
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
    private fun persistComparisons(root: Path, refs: HumanizationWorkflowReferences) {
        refs.artifacts.sortedBy { it.id }.forEach { artifact ->
            val occurrence = artifact.id.removePrefix("piano-").takeIf { artifact.id.startsWith("piano-") }
            val before = StageComparisonArtifact(StageComparisonStage.HUMANIZATION, artifact.input, refs.inputsSha256,
                role = artifact.role.name.lowercase(), occurrenceId = occurrence)
            val after = StageComparisonArtifact(StageComparisonStage.HUMANIZATION, artifact.output, refs.inputsSha256,
                StageEvidenceStatus.APPROVED, artifact.role.name.lowercase(), occurrence)
            StageComparisonReportStore.write(root, after, StageComparisonService().compare(root, before, after))
        }
    }
    private fun decode(path: Path): HumanizationRunReport = try { json.decodeFromString(HumanizationRunReport.serializer(), Files.readString(path)) }
    catch (error: Exception) { throw IllegalArgumentException("Humanization report is malformed.", error) }
    private fun locked(root: Path, block: (Path) -> HumanizationSnapshot): HumanizationSnapshot {
        val normalized = root.toAbsolutePath().normalize(); val lock = ProjectMutationCoordinator.lock(normalized)
        check(lock.tryLock()) { "Another project mutation is already running." }; return try { block(normalized) } finally { lock.unlock() }
    }
    private fun fullSongEnhancedInput(root: Path, project: app.melotrail.arrangement.Project, id: String, input: WorkflowArtifactReference): WorkflowArtifactReference = when (project.workflow.fullSongEnhancementSelection) {
        app.melotrail.arrangement.FullSongEnhancementSelection.BYPASS, app.melotrail.arrangement.FullSongEnhancementSelection.NO_OP -> input
        app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED -> error("Full-Song Enhance selection is unresolved.")
        app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED -> {
            val run = requireNotNull(project.workflow.fullSongEnhancement) { "Full-Song Enhance selection has no evidence." }
            val artifact = requireNotNull(run.artifacts.singleOrNull { it.id == id }) { "Full-Song Enhance is missing MIDI for '$id'." }
            require(artifact.input == input && WorkflowArtifact.FULL_SONG_ENHANCEMENT !in project.workflow.stale) { "Full-Song Enhance input '$id' is stale." }
            val path = root.resolve(artifact.output.file).normalize(); require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == artifact.output.sha256) { "Full-Song Enhance MIDI '$id' is missing or stale." }
            artifact.output
        }
    }
    private fun atomicWrite(target: Path, text: String) {
        Files.createDirectories(requireNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try { Files.writeString(temporary, text, StandardCharsets.UTF_8); try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is unavailable for humanization evidence.", error) } } finally { Files.deleteIfExists(temporary) }
    }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    /** Stable per-occurrence scope prevents repeated sections from receiving an identical performance pass. */
    private fun scopedSeed(runSeed: Long, id: String): Long = ByteBuffer.wrap(
        MessageDigest.getInstance("SHA-256").digest("$runSeed|$id".toByteArray(StandardCharsets.UTF_8))
    ).long
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
