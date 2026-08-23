package app.melotrail.application

import app.melotrail.arrangement.CriticArtifactPaths
import app.melotrail.arrangement.CriticWorkflowReferences
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DeterministicFullSongCritic
import app.melotrail.arrangement.FullSongCriticInput
import app.melotrail.arrangement.FullSongCriticMidiArtifact
import app.melotrail.arrangement.FullSongCriticReport
import app.melotrail.arrangement.FullSongCriticAdvisor
import app.melotrail.arrangement.MelodyIdentityBuilder
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RoleValidationReport
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

data class FullSongCriticSnapshot(val report: FullSongCriticReport, val artifact: Path, val current: Boolean)

/** UI-neutral, read-only critic orchestration. Its only writes are its atomically published report and workflow evidence. */
interface FullSongCriticApplicationService {
    fun run(root: Path): FullSongCriticSnapshot
    fun load(root: Path): FullSongCriticSnapshot
    fun analyzeCandidate(root: Path, outputs: Map<String, WorkflowArtifactReference>): FullSongCriticReport
}

class DefaultFullSongCriticApplicationService(
    private val authorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val critic: DeterministicFullSongCritic = DeterministicFullSongCritic(),
    private val advisor: FullSongCriticAdvisor? = null
) : FullSongCriticApplicationService {
    override fun run(root: Path): FullSongCriticSnapshot {
        val normalized = root.toAbsolutePath().normalize()
        val input = currentInput(normalized)
        val deterministic = critic.criticize(input)
        val report = advisor?.advise(deterministic)?.let { advice ->
            FullSongCriticReport.create(deterministic.inputSha256, deterministic.contextSha256, deterministic.aggregateMetrics, deterministic.issues, deterministic.warnings, advice)
        } ?: deterministic
        val relative = CriticArtifactPaths.report(input.inputSha256)
        val path = normalized.resolve(relative)
        atomicWrite(path, json.encodeToString(FullSongCriticReport.serializer(), report))
        val reference = WorkflowArtifactReference(relative, sha256(path))
        val project = ProjectStore.read(normalized)
        ProjectStore.write(normalized, project.copy(workflow = project.workflow.invalidate(WorkflowChange.CRITIC)
            .markCurrent(WorkflowArtifact.CRITIC).copy(critic = CriticWorkflowReferences(input.inputSha256, reference))))
        return FullSongCriticSnapshot(report, path, true)
    }

    override fun load(root: Path): FullSongCriticSnapshot {
        val normalized = root.toAbsolutePath().normalize()
        val input = currentInput(normalized)
        val project = ProjectStore.read(normalized)
        val reference = requireNotNull(project.workflow.critic) { "No Full-Song Critic report is available. Run Critic after Cohesion." }
        require(WorkflowArtifact.CRITIC !in project.workflow.stale && reference.inputSha256 == input.inputSha256) { "Full-Song Critic report is stale. Rerun Critic after Cohesion." }
        val path = verified(normalized, reference.report, "Full-Song Critic report")
        val report = json.decodeFromString(FullSongCriticReport.serializer(), Files.readString(path))
        require(report.inputSha256 == input.inputSha256 && report.contextSha256 == input.authority.contextSha256) { "Full-Song Critic report does not match current Cohesion inputs." }
        return FullSongCriticSnapshot(report, path, true)
    }

    /** Re-runs the deterministic critic against a complete, unselected candidate without mutating workflow state. */
    override fun analyzeCandidate(root: Path, outputs: Map<String, WorkflowArtifactReference>): FullSongCriticReport {
        val normalized = root.toAbsolutePath().normalize()
        require(outputs.isNotEmpty()) { "Candidate Critic requires candidate MIDI outputs." }
        return critic.criticize(currentInput(normalized, outputs))
    }

    private fun currentInput(root: Path, candidateOutputs: Map<String, WorkflowArtifactReference> = emptyMap()): FullSongCriticInput {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        require(project.version == Project.CURRENT_VERSION) { "Full-Song Critic requires a schema-v4 project." }
        val cohesion = requireNotNull(project.workflow.cohesion) { "Full-Song Critic requires approved Cohesion." }
        require(cohesion.approved && WorkflowArtifact.COHESION !in project.workflow.stale) { "Full-Song Critic requires current approved Cohesion." }
        val authority = authorityBuilder.wholeSongAnalysis(root)
        val arrangementRef = requireNotNull(project.workflow.arrangement?.arrangement) { "Full-Song Critic requires an approved arrangement." }
        val arrangementPath = verified(root, arrangementRef, "Approved arrangement")
        val arrangement = json.decodeFromString(DetailedArrangement.serializer(), Files.readString(arrangementPath))
        require(arrangement.sections.map { it.instanceId } == authority.occurrences.map { it.occurrenceId }) { "Approved arrangement no longer matches canonical occurrences." }
        val occurrences = cohesion.occurrences.sortedBy { it.instanceId }.map { occurrence ->
            require(occurrence.approved && occurrence.cohesionInputSha256 == cohesion.inputSha256) { "Cohesion occurrence '${occurrence.instanceId}' is not approved." }
            val authorityOccurrence = requireNotNull(authority.occurrences.singleOrNull { it.occurrenceId == occurrence.instanceId }) { "Cohesion occurrence '${occurrence.instanceId}' is not in the canonical timeline." }
            val id = "piano-${occurrence.instanceId}"
            val reference = candidateOutputs[id] ?: occurrence.result
            FullSongCriticMidiArtifact("piano", occurrence.instanceId, verified(root, reference, "Cohesion occurrence '${occurrence.instanceId}'"), reference, authorityOccurrence.startTick)
        }
        val roles = cohesion.roles.sortedBy { it.role }.map { role ->
            require(role.approved && role.cohesionInputSha256 == cohesion.inputSha256) { "Cohesion role '${role.role}' is not approved." }
            val reference = candidateOutputs[role.role] ?: role.result
            FullSongCriticMidiArtifact(role.role, null, verified(root, reference, "Cohesion role '${role.role}'"), reference)
        }
        val reports = project.workflow.generatedMidi?.artifacts.orEmpty().sortedBy { it.id }.map { generated ->
            val path = verified(root, generated.validationReport, "Generated role validation '${generated.id}'")
            json.decodeFromString(RoleValidationReport.serializer(), Files.readString(path)).also { report ->
                require(report.role == generated.id && report.outputSha256 == generated.artifact.sha256 &&
                    report.inputHashes.any { it.name == "arrangement" && it.sha256 == arrangementRef.sha256 } &&
                    report.inputHashes.any { it.name == "authority" && it.sha256 == authority.contextSha256 }) {
                    "Generated role validation '${generated.id}' does not match current arrangement and authority inputs."
                }
            }
        }
        val melody = cohesion.occurrences.sortedBy { it.instanceId }.firstOrNull()?.let { occurrence ->
            MelodyIdentityBuilder.build(verified(root, occurrence.result, "Cohesion melody '${occurrence.instanceId}'"), authority.harmonyPpq * 4L / authority.meter.denominator)
        }
        require(candidateOutputs.keys.all { it in (occurrences.map { "piano-${it.occurrenceId}" } + roles.map { it.role }) } &&
            (candidateOutputs.isEmpty() || candidateOutputs.size == occurrences.size + roles.size)) { "Candidate Critic outputs do not cover the approved Cohesion ensemble." }
        val hash = sha256(json.encodeToString(CriticInputHash(
            authority.contextSha256, cohesion.inputSha256, arrangementRef.sha256,
            occurrences.map { CriticArtifactHash(it.role, it.occurrenceId, it.reference.sha256) },
            roles.map { CriticArtifactHash(it.role, it.occurrenceId, it.reference.sha256) },
            reports.map { CriticRoleReportHash(it.role, it.outputSha256, it.passed) },
            melody?.sourceSha256
        )).toByteArray(StandardCharsets.UTF_8))
        return FullSongCriticInput(authority, occurrences, roles, arrangement, arrangementRef.sha256, melody, reports, inputSha256 = hash)
    }

    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && sha256(path) == reference.sha256) { "$label is missing or stale." }
        return path
    }

    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    @kotlinx.serialization.Serializable private data class CriticInputHash(val context: String, val cohesion: String, val arrangement: String, val occurrences: List<CriticArtifactHash>, val roles: List<CriticArtifactHash>, val reports: List<CriticRoleReportHash>, val melody: String?)
    @kotlinx.serialization.Serializable private data class CriticArtifactHash(val role: String, val occurrence: String?, val sha256: String)
    @kotlinx.serialization.Serializable private data class CriticRoleReportHash(val role: String, val output: String, val passed: Boolean)
    private fun sha256(path: Path) = sha256(Files.readAllBytes(path))
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object { val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}
