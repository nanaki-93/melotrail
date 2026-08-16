package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * The durable dependency graph for project-derived artifacts.  Entries in
 * [ProjectWorkflowReferences.stale] are invalidation evidence, never a claim
 * that a file is usable: readers still validate the referenced file and its
 * own fingerprint before enabling a stage.
 */
@Serializable
enum class WorkflowArtifact {
    RAW_SOURCE,
    MIDI_REPAIR,
    MIDI_FEEL,
    ANALYSIS,
    COHESION,
    ARRANGEMENT,
    GENERATED_MIDI,
    STEMS,
    DRY_MIX,
    AUDIO_TEXTURE,
    MASTER,
    RELEASE,
    COMMERCIAL_EXPORT
}

@Serializable
enum class WorkflowChange {
    SOURCE_OR_RAW,
    REPAIRED_MIDI,
    MIDI_FEEL,
    ANALYSIS,
    STRUCTURE,
    COHESION,
    MIX_ONLY,
    AUDIO_TEXTURE
}

/** Centralized, deliberately non-destructive invalidation matrix. */
object WorkflowArtifactGraph {
    private val allAfterRepair = setOf(
        WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION,
        WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
        WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    fun invalidatedBy(change: WorkflowChange): Set<WorkflowArtifact> = when (change) {
        WorkflowChange.SOURCE_OR_RAW -> setOf(WorkflowArtifact.MIDI_REPAIR) + allAfterRepair
        WorkflowChange.REPAIRED_MIDI -> allAfterRepair
        WorkflowChange.MIDI_FEEL, WorkflowChange.ANALYSIS, WorkflowChange.STRUCTURE -> setOf(
            WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI,
            WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE,
            WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        WorkflowChange.COHESION -> setOf(
            WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        WorkflowChange.MIX_ONLY -> setOf(
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        WorkflowChange.AUDIO_TEXTURE -> setOf(
            WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
            WorkflowArtifact.COMMERCIAL_EXPORT
        )
    }
}

/** Project-local, fingerprinted reference; it never accepts an absolute path. */
@Serializable
data class WorkflowArtifactReference(val file: String, val sha256: String) {
    init {
        require(file.isNotBlank() && !file.startsWith('/') && !file.contains("..")) { "Workflow artifact path must be project-relative" }
        require(SHA_256.matches(sha256)) { "Workflow artifact fingerprint is invalid" }
    }
}

/** One result for every stable structure occurrence, including its approval state. */
@Serializable
data class CohesionOccurrenceReference(
    val instanceId: String,
    val sourceSha256: String,
    val result: WorkflowArtifactReference,
    val approved: Boolean
) {
    init {
        require(SAFE_ID.matches(instanceId)) { "Cohesion occurrence ID is invalid" }
        require(SHA_256.matches(sourceSha256)) { "Cohesion source fingerprint is invalid" }
    }
}

@Serializable
data class CohesionWorkflowReferences(
    val inputSha256: String,
    val plan: WorkflowArtifactReference,
    val occurrences: List<CohesionOccurrenceReference>,
    val approved: Boolean
) {
    init {
        require(SHA_256.matches(inputSha256)) { "Cohesion input fingerprint is invalid" }
        require(occurrences.map(CohesionOccurrenceReference::instanceId).distinct().size == occurrences.size) {
            "Cohesion occurrence IDs must be unique"
        }
        require(!approved || occurrences.all(CohesionOccurrenceReference::approved)) {
            "Approved cohesion requires approved results for every occurrence"
        }
    }
}

/** References only; Task 071 owns commercial decisions and manifest UI. */
@Serializable
data class CommercialProvenanceReferences(
    val manifest: WorkflowArtifactReference? = null,
    val release: WorkflowArtifactReference? = null
)

@Serializable
data class ProjectWorkflowReferences(
    val stale: Set<WorkflowArtifact> = emptySet(),
    val cohesion: CohesionWorkflowReferences? = null,
    val commercialProvenance: CommercialProvenanceReferences? = null
) {
    fun invalidate(change: WorkflowChange): ProjectWorkflowReferences = copy(
        stale = stale + WorkflowArtifactGraph.invalidatedBy(change),
        cohesion = if (WorkflowArtifact.COHESION in WorkflowArtifactGraph.invalidatedBy(change)) null else cohesion,
        commercialProvenance = if (WorkflowArtifact.RELEASE in WorkflowArtifactGraph.invalidatedBy(change)) null else commercialProvenance
    )

    fun markCurrent(vararg artifacts: WorkflowArtifact): ProjectWorkflowReferences = copy(stale = stale - artifacts.toSet())
}

/** Small atomic metadata boundary used after a stage has actually published output. */
object ProjectWorkflowStore {
    fun update(root: Path, transform: (ProjectWorkflowReferences) -> ProjectWorkflowReferences) {
        val project = ProjectStore.read(root)
        if (project.version != Project.CURRENT_VERSION) return
        ProjectStore.write(root, project.copy(workflow = transform(project.workflow)))
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256 = Regex("[0-9a-f]{64}")
