package app.melotrail.application

import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** The representation of a debug comparison; only MIDI and lossless WAV remain reviewable here. */
@Serializable
enum class QualityReviewArtifactKind { MIDI, WAV }

/** An exact before/after pair whose immutable copies are made available for a human comparison. */
@Serializable
data class QualityDebugPair(
    val id: String,
    val kind: QualityReviewArtifactKind,
    val before: WorkflowArtifactReference,
    val after: WorkflowArtifactReference
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9-]{1,63}")) && before != after) {
            "Quality debug pair is invalid"
        }
    }
}

/** Hash-bound, project-local debug copies of one requested comparison. */
@Serializable
data class PublishedQualityDebugPair(
    val id: String,
    val kind: QualityReviewArtifactKind,
    val before: WorkflowArtifactReference,
    val after: WorkflowArtifactReference,
    val debugBefore: WorkflowArtifactReference,
    val debugAfter: WorkflowArtifactReference
)

/** Manual listening status is never inferred from generated files or green automated tests. */
@Serializable
enum class ListeningReviewStatus { PENDING_HUMAN_REVIEW, ACCEPTED, REJECTED }

/** A real listener's bounded decision for an already published pending review form. */
data class ListeningReviewDecision(
    val status: ListeningReviewStatus,
    val listener: String,
    val reviewedAtIso8601: String,
    val device: String,
    val reason: String
) {
    init {
        require(status != ListeningReviewStatus.PENDING_HUMAN_REVIEW && listener.isNotBlank() && device.isNotBlank() && reason.isNotBlank() &&
            reviewedAtIso8601.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[^\\s]+"))) {
            "Listening decision is invalid"
        }
    }
}

/**
 * A review form bound to debug copies and current project context.
 *
 * The pending template deliberately contains no listener, device, date, or
 * decision; those require a real listening session outside automated tests.
 */
@Serializable
data class QualityListeningRecord(
    val version: Int = VERSION,
    val projectContextSha256: String,
    val status: ListeningReviewStatus = ListeningReviewStatus.PENDING_HUMAN_REVIEW,
    val listener: String? = null,
    val reviewedAtIso8601: String? = null,
    val device: String? = null,
    val decisionReason: String? = null,
    val comparisons: List<PublishedQualityDebugPair>,
    val requiredComparisons: List<String> = REQUIRED_COMPARISONS,
    val unverifiedDependencies: List<String>
) {
    init {
        require(version == VERSION && HASH.matches(projectContextSha256) && comparisons.isNotEmpty() &&
            comparisons.map(PublishedQualityDebugPair::id).distinct().size == comparisons.size &&
            comparisons == comparisons.sortedBy(PublishedQualityDebugPair::id) &&
            requiredComparisons == REQUIRED_COMPARISONS && unverifiedDependencies == unverifiedDependencies.distinct().sorted()) {
            "Quality listening record is invalid"
        }
        when (status) {
            ListeningReviewStatus.PENDING_HUMAN_REVIEW -> require(listener == null && reviewedAtIso8601 == null && device == null && decisionReason == null) {
                "Pending listening evidence cannot claim a listener or decision"
            }
            ListeningReviewStatus.ACCEPTED, ListeningReviewStatus.REJECTED -> require(
                !listener.isNullOrBlank() && !reviewedAtIso8601.isNullOrBlank() && !device.isNullOrBlank() && !decisionReason.isNullOrBlank()
            ) { "Completed listening evidence requires listener, date, device, and reason" }
        }
    }

    companion object {
        const val VERSION = 1
        private val HASH = Regex("[0-9a-f]{64}")
        val REQUIRED_COMPARISONS = listOf(
            "prepared-section-vs-selected-input", "hard-concatenation-vs-connected-melody",
            "full-melody-vs-core-arrangement", "core-arrangement-vs-cohesion", "pre-polish-vs-selected-polish",
            "grid-vs-approved-groove", "pad-strings-boundaries", "low-end-before-after", "dry-production-master-lossy"
        )
    }
}

/** Publishes hash-bound MIDI/WAV debug copies and an explicitly pending human listening form. */
class QualityReviewEvidenceService {
    /**
     * Produce immutable debug copies for one current project context without
     * changing sources, selected candidates, or workflow state.
     */
    fun publishPending(root: Path, pairs: List<QualityDebugPair>, unverifiedDependencies: List<String>): WorkflowArtifactReference {
        val projectRoot = root.toAbsolutePath().normalize()
        val project = projectRoot.resolve("project.json")
        require(Files.isRegularFile(project)) { "Quality review requires a canonical project." }
        require(pairs.isNotEmpty() && pairs.any { it.kind == QualityReviewArtifactKind.MIDI } && pairs.any { it.kind == QualityReviewArtifactKind.WAV }) {
            "Quality review requires at least one MIDI and one WAV comparison."
        }
        require(pairs.map(QualityDebugPair::id).distinct().size == pairs.size) { "Quality review repeats a debug comparison id." }
        val context = digest((digest(project) + "|" + pairs.sortedBy(QualityDebugPair::id).joinToString("|") { pair ->
            "${pair.id}:${pair.kind}:${pair.before.sha256}:${pair.after.sha256}"
        }).toByteArray(StandardCharsets.UTF_8))
        val directory = projectRoot.resolve("debug/quality/$context")
        val comparisons = pairs.sortedBy(QualityDebugPair::id).map { pair ->
            val before = verify(projectRoot, pair.before, pair.kind)
            val after = verify(projectRoot, pair.after, pair.kind)
            PublishedQualityDebugPair(pair.id, pair.kind, pair.before, pair.after,
                copyForReview(projectRoot, directory, pair.id, "before", before),
                copyForReview(projectRoot, directory, pair.id, "after", after))
        }
        val record = QualityListeningRecord(projectContextSha256 = context, comparisons = comparisons,
            unverifiedDependencies = unverifiedDependencies.map(String::trim).filter(String::isNotBlank).distinct().sorted())
        val target = directory.resolve("listening-record.json")
        publish(target, json.encodeToString(record))
        return WorkflowArtifactReference(projectRoot.relativize(target).toString().replace('\\', '/'), digest(target))
    }

    /** Read and revalidate a published form; retained debug files alone never imply a completed listening review. */
    fun load(root: Path, reference: WorkflowArtifactReference): QualityListeningRecord {
        val projectRoot = root.toAbsolutePath().normalize()
        val path = verify(projectRoot, reference, null)
        val record = json.decodeFromString(QualityListeningRecord.serializer(), Files.readString(path, StandardCharsets.UTF_8))
        record.comparisons.forEach { pair ->
            verify(projectRoot, pair.debugBefore, pair.kind)
            verify(projectRoot, pair.debugAfter, pair.kind)
        }
        return record
    }

    /** Persist one human decision beside its immutable pending form; it cannot alter selected music or audio artifacts. */
    fun recordDecision(root: Path, pending: WorkflowArtifactReference, decision: ListeningReviewDecision): WorkflowArtifactReference {
        val projectRoot = root.toAbsolutePath().normalize()
        val form = load(projectRoot, pending)
        require(form.status == ListeningReviewStatus.PENDING_HUMAN_REVIEW) { "Listening review already has a decision." }
        val completed = form.copy(status = decision.status, listener = decision.listener.trim(),
            reviewedAtIso8601 = decision.reviewedAtIso8601, device = decision.device.trim(), decisionReason = decision.reason.trim())
        val target = projectRoot.resolve(pending.file).normalize().resolveSibling("listening-decision.json")
        publish(target, json.encodeToString(completed))
        return WorkflowArtifactReference(projectRoot.relativize(target).toString().replace('\\', '/'), digest(target))
    }

    /** Verify a reference is project-confined, unchanged, and has the requested reviewable extension. */
    private fun verify(root: Path, reference: WorkflowArtifactReference, expectedKind: QualityReviewArtifactKind?): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && digest(path) == reference.sha256) {
            "Quality review artifact is missing, unsafe, or changed."
        }
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
        when (expectedKind) {
            QualityReviewArtifactKind.MIDI -> require(extension in setOf("mid", "midi")) { "Quality MIDI comparison has the wrong extension." }
            QualityReviewArtifactKind.WAV -> require(extension in setOf("wav", "wave")) { "Quality WAV comparison has the wrong extension." }
            null -> Unit
        }
        return path
    }

    /** Copy only immutable evidence to a content-addressed debug path, preserving every selected artifact. */
    private fun copyForReview(root: Path, directory: Path, pairId: String, side: String, source: Path): WorkflowArtifactReference {
        val extension = source.fileName.toString().substringAfterLast('.', "")
        val target = directory.resolve("$pairId-$side.${if (extension.isBlank()) "artifact" else extension}")
        Files.createDirectories(directory)
        if (Files.exists(target)) require(digest(target) == digest(source)) { "Existing debug comparison differs; preserving it for inspection." }
        else {
            val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
            try {
                Files.copy(source, temporary)
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
            } finally { Files.deleteIfExists(temporary) }
        }
        return WorkflowArtifactReference(root.relativize(target).toString().replace('\\', '/'), digest(target))
    }

    /** Publish deterministic record bytes once; a conflicting record is retained as evidence instead of overwritten. */
    private fun publish(target: Path, contents: String) {
        Files.createDirectories(requireNotNull(target.parent))
        if (Files.exists(target)) {
            require(Files.readString(target, StandardCharsets.UTF_8) == contents) { "Existing listening record differs; preserving it for inspection." }
            return
        }
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
        } finally { Files.deleteIfExists(temporary) }
    }

    /** Return the lowercase SHA-256 for exact artifact and context lineage. */
    private fun digest(path: Path): String = digest(Files.readAllBytes(path))

    /** Return the lowercase SHA-256 for a canonical byte payload. */
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object { val json = Json { encodeDefaults = true; prettyPrint = true } }
}
