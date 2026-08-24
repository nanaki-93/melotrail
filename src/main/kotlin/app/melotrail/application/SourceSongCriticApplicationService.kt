package app.melotrail.application

import app.melotrail.arrangement.MelodyConnection
import app.melotrail.arrangement.MelodyConnectionArtifact
import app.melotrail.arrangement.MelodyConnectionPlanner
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SourceSong
import app.melotrail.arrangement.SourceSongArtifact
import app.melotrail.arrangement.SourceSongApproval
import app.melotrail.arrangement.SourceSongCritic
import app.melotrail.arrangement.SourceSongCriticArtifactPaths
import app.melotrail.arrangement.SourceSongCriticInput
import app.melotrail.arrangement.SourceSongCriticReport
import app.melotrail.arrangement.SourceSongIssueSeverity
import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** A critic run result with the exact connected MIDI that was assessed. */
data class SourceSongCriticSnapshot(
    val report: SourceSongCriticReport,
    val reportPath: Path,
    val connectedMidi: Path,
    val current: Boolean
)

/** A loaded, current user approval that satisfies the pre-arrangement gate. */
data class SourceSongApprovalSnapshot(val approval: SourceSongApproval, val approvalPath: Path)

/**
 * The only approved piano/melody input permitted after source-song review.
 * Every reference is project-relative and fingerprinted so downstream stages
 * cannot silently re-resolve an occurrence from a selected source part.
 */
data class ApprovedSourceSongMelody(
    val sourceSong: SourceSong,
    val sourceSongSidecar: WorkflowArtifactReference,
    val connection: MelodyConnection,
    val connectionSidecar: WorkflowArtifactReference,
    val connectedMidi: WorkflowArtifactReference,
    val criticReport: WorkflowArtifactReference,
    val approval: SourceSongApproval,
    val approvalSidecar: WorkflowArtifactReference
)

/** UI-neutral pre-arrangement source-song review and approval boundary. */
interface SourceSongCriticApplicationService {
    /** Analyze and atomically publish deterministic evidence without changing any MIDI. */
    fun run(root: Path): SourceSongCriticSnapshot

    /** Load the current report only when it still matches the assembled connected source. */
    fun load(root: Path): SourceSongCriticSnapshot

    /** Persist ordinary approval, or an explicit recorded override for current blocking issues. */
    fun approve(root: Path, overrideBlockingIssues: Boolean = false, overrideReason: String? = null): SourceSongApprovalSnapshot

    /** Require a current approval before arrangement may begin. */
    fun requireApproved(root: Path): SourceSongApprovalSnapshot

    /** Resolve the exact approved full melody and all of its immutable lineage. */
    fun requireApprovedMelody(root: Path): ApprovedSourceSongMelody
}

/** Default file-backed implementation for the deterministic source-song approval gate. */
class DefaultSourceSongCriticApplicationService(
    private val sourceSongService: SourceSongApplicationService = SourceSongApplicationService(),
    private val connectionPlanner: MelodyConnectionPlanner = MelodyConnectionPlanner(),
    private val authorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val critic: SourceSongCritic = SourceSongCritic()
) : SourceSongCriticApplicationService {
    /** Assemble/connect the current source, then write or verify its deterministic critic report. */
    override fun run(root: Path): SourceSongCriticSnapshot {
        val current = current(root)
        val report = critic.criticize(current.input)
        val relative = SourceSongCriticArtifactPaths.report(report.sourceSongContextSha256, report.connectedMidi.sha256)
        val path = current.root.resolve(relative)
        publish(path, json.encodeToString(SourceSongCriticReport.serializer(), report))
        return SourceSongCriticSnapshot(report, path, current.connectedMidi, true)
    }

    /** Load the exact report for the current assembled and connected source candidate. */
    override fun load(root: Path): SourceSongCriticSnapshot {
        val current = current(root)
        val relative = SourceSongCriticArtifactPaths.report(current.sourceSong.contextSha256, current.connection.outputMidi.sha256)
        val path = current.root.resolve(relative).normalize()
        require(path.startsWith(current.root) && Files.isRegularFile(path)) { "No current Source Song Critic report is available. Run the critic before arrangement." }
        val report = json.decodeFromString(SourceSongCriticReport.serializer(), Files.readString(path))
        require(report.sourceSongContextSha256 == current.sourceSong.contextSha256 && report.sourceMidiSha256 == current.sourceSong.assembledMidi.sha256 &&
            report.connectedMidi == current.connection.outputMidi) { "Source Song Critic report is stale. Run the critic again." }
        return SourceSongCriticSnapshot(report, path, current.connectedMidi, true)
    }

    /** Write a deliberate approval decision only after reviewing the exact current critic report. */
    override fun approve(root: Path, overrideBlockingIssues: Boolean, overrideReason: String?): SourceSongApprovalSnapshot {
        val snapshot = load(root)
        val blocking = snapshot.report.issues.filter { it.severity == SourceSongIssueSeverity.BLOCKING }.map { it.id }
        require(blocking.isEmpty() || overrideBlockingIssues) {
            "Source Song Critic found blocking issues. Record an explicit override with a reason before arranging."
        }
        require(!overrideBlockingIssues || blocking.isNotEmpty()) { "A source-song override is allowed only for current blocking issues." }
        if (overrideBlockingIssues) require(!overrideReason.isNullOrBlank()) { "A source-song override requires a reason." }
        val rootPath = root.toAbsolutePath().normalize()
        val reportReference = WorkflowArtifactReference(relative(snapshot.reportPath, rootPath), sha256(snapshot.reportPath))
        val approval = SourceSongApproval(
            sourceSongContextSha256 = snapshot.report.sourceSongContextSha256,
            sourceMidiSha256 = snapshot.report.sourceMidiSha256,
            connectedMidiSha256 = snapshot.report.connectedMidi.sha256,
            criticReport = reportReference,
            overriddenBlockingIssueIds = if (overrideBlockingIssues) blocking else emptyList(),
            overrideReason = if (overrideBlockingIssues) overrideReason?.trim() else null
        )
        val path = rootPath.resolve(SourceSongCriticArtifactPaths.approval(approval.sourceSongContextSha256, approval.connectedMidiSha256))
        publish(path, json.encodeToString(SourceSongApproval.serializer(), approval))
        return SourceSongApprovalSnapshot(approval, path)
    }

    /** Reject missing, stale, or incomplete approval evidence before any arrangement work starts. */
    override fun requireApproved(root: Path): SourceSongApprovalSnapshot = requireApprovedMelody(root).let {
        SourceSongApprovalSnapshot(it.approval, root.toAbsolutePath().normalize().resolve(it.approvalSidecar.file))
    }

    /** Reject missing or stale approval before exposing the canonical full melody to a downstream consumer. */
    override fun requireApprovedMelody(root: Path): ApprovedSourceSongMelody {
        val current = current(root)
        val report = load(current.root)
        val path = current.root.resolve(SourceSongCriticArtifactPaths.approval(report.report.sourceSongContextSha256, report.report.connectedMidi.sha256)).normalize()
        require(path.startsWith(current.root) && Files.isRegularFile(path)) { "Arrangement requires explicit Source Song approval. Review the Source Song Critic report first." }
        val approval = json.decodeFromString(SourceSongApproval.serializer(), Files.readString(path))
        val blocking = report.report.issues.filter { it.severity == SourceSongIssueSeverity.BLOCKING }.map { it.id }.sorted()
        require(approval.sourceSongContextSha256 == report.report.sourceSongContextSha256 && approval.sourceMidiSha256 == report.report.sourceMidiSha256 &&
            approval.connectedMidiSha256 == report.report.connectedMidi.sha256 && approval.criticReport.file == relative(report.reportPath, current.root) &&
            approval.criticReport.sha256 == sha256(report.reportPath)) { "Source Song approval is stale. Review and approve the current critic report." }
        require(approval.overriddenBlockingIssueIds.sorted() == blocking) {
            "Source Song approval does not cover the current blocking issues. Record a current explicit override."
        }
        return ApprovedSourceSongMelody(
            sourceSong = current.sourceSong,
            sourceSongSidecar = reference(current.root, current.sourceSongArtifact.metadataPath),
            connection = current.connection,
            connectionSidecar = reference(current.root, current.connectionArtifact.metadataPath),
            connectedMidi = current.connection.outputMidi,
            criticReport = WorkflowArtifactReference(relative(report.reportPath, current.root), sha256(report.reportPath)),
            approval = approval,
            approvalSidecar = reference(current.root, path)
        )
    }

    /** Resolve all current, immutable inputs used by every critic and approval operation. */
    private fun current(root: Path): CurrentSourceSong {
        val normalized = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(normalized).also { it.requireValid(normalized) }
        val sourceSongArtifact = sourceSongService.assemble(normalized)
        val sourceSong = sourceSongArtifact.song
        val connectionArtifact = connectionPlanner.connect(normalized, sourceSong)
        val connection = connectionArtifact.connection
        val connected = normalized.resolve(connection.outputMidi.file).normalize()
        require(connected.startsWith(normalized) && Files.isRegularFile(connected)) { "Connected source melody is missing." }
        val authority = authorityBuilder.build(normalized)
        return CurrentSourceSong(normalized, sourceSongArtifact, connectionArtifact, connected,
            SourceSongCriticInput(normalized, sourceSong, connection, authority.projectKey.scalePitchClasses().map { it.chromatic }.toSet()))
    }

    /** Atomically write immutable deterministic evidence, rejecting conflicting existing bytes. */
    private fun publish(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent))
        if (Files.exists(path)) {
            require(Files.readString(path) == text) { "Existing source-song evidence differs; preserving it for inspection." }
            return
        }
        val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path) }
        } finally { Files.deleteIfExists(temporary) }
    }

    /** Return a canonical project-relative path for portable approval evidence. */
    private fun relative(path: Path, root: Path): String {
        val normalizedRoot = root.toAbsolutePath().normalize(); val normalizedPath = path.toAbsolutePath().normalize()
        require(normalizedPath.startsWith(normalizedRoot)) { "Source-song evidence escapes project root" }
        return normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/')
    }

    /** Hash persisted evidence before it is bound into an approval decision. */
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    /** Bind an existing immutable sidecar or MIDI artifact into a portable reference. */
    private fun reference(root: Path, path: Path): WorkflowArtifactReference {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(root) && Files.isRegularFile(normalized)) { "Source-song artifact is missing or outside the project root" }
        return WorkflowArtifactReference(relative(normalized, root), sha256(normalized))
    }

    /** Internal immutable assembly used to avoid recomputing different source inputs within an operation. */
    private data class CurrentSourceSong(
        val root: Path,
        val sourceSongArtifact: SourceSongArtifact,
        val connectionArtifact: MelodyConnectionArtifact,
        val connectedMidi: Path,
        val input: SourceSongCriticInput
    ) {
        val sourceSong get() = sourceSongArtifact.song
        val connection get() = connectionArtifact.connection
    }

    private companion object { val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false } }
}
