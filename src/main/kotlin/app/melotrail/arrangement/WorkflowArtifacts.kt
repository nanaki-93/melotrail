package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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
    @SerialName("MIDI_REPAIR") CLEAN_MIDI,
    TRANSPOSED_MIDI,
    AI_FIX,
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
    @SerialName("REPAIRED_MIDI") CLEANED_MIDI,
    SOURCE_KEY,
    AI_FIX_SELECTION,
    MIDI_FEEL,
    ANALYSIS,
    STRUCTURE,
    /** Section context changes planning/arrangement, but not immutable MIDI or its analysis. */
    PART_SECTION,
    COHESION,
    COMPOSITION_KEY,
    COMPOSITION_TEMPO_OR_METER,
    COMPOSITION_PROFILE_OR_MOOD,
    HARMONY,
    MIX_ONLY,
    AUDIO_TEXTURE
}

/** Centralized, deliberately non-destructive invalidation matrix. */
object WorkflowArtifactGraph {
    private val allAfterRepair = setOf(
        WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION,
        WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
        WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    private val allAfterSelection = setOf(
        WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION,
        WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
        WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    private val allAfterAnalysis = setOf(
        WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI,
        WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE,
        WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    fun invalidatedBy(change: WorkflowChange): Set<WorkflowArtifact> = when (change) {
        WorkflowChange.SOURCE_OR_RAW -> setOf(WorkflowArtifact.CLEAN_MIDI) + allAfterRepair
        WorkflowChange.CLEANED_MIDI -> allAfterRepair
        WorkflowChange.SOURCE_KEY -> allAfterRepair
        WorkflowChange.AI_FIX_SELECTION -> allAfterSelection
        WorkflowChange.MIDI_FEEL -> setOf(WorkflowArtifact.ANALYSIS) + allAfterAnalysis
        WorkflowChange.ANALYSIS, WorkflowChange.STRUCTURE, WorkflowChange.PART_SECTION -> allAfterAnalysis
        WorkflowChange.COHESION -> setOf(
            WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** A project-key decision invalidates its derived transposition and every dependent artifact. */
        WorkflowChange.COMPOSITION_KEY -> setOf(
            WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS,
            WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX,
            WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
            WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Tempo/meter are declared arrangement-normalization inputs. */
        WorkflowChange.COMPOSITION_TEMPO_OR_METER -> setOf(
            WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Profile and mood can change MIDI-feel policy, then every artifact derived from that selected input. */
        WorkflowChange.COMPOSITION_PROFILE_OR_MOOD -> setOf(
            WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI,
            WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE,
            WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Harmony is planner context, never source, extraction, cleanup, or analysis evidence. */
        WorkflowChange.HARMONY -> setOf(
            WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.COHESION,
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
        val relative = runCatching { Path.of(file) }.getOrElse { throw IllegalArgumentException("Workflow artifact path is invalid", it) }
        val canonical = relative.normalize().toString().replace('\\', '/')
        require(
            file.isNotBlank() && !relative.isAbsolute && '\\' !in file && ':' !in file &&
                relative.none { it.toString() == ".." } && canonical == file
        ) { "Workflow artifact path must be canonical and project-relative" }
        require(SHA_256.matches(sha256)) { "Workflow artifact fingerprint is invalid" }
    }
}

/** Stable project-relative locations reserved for the later bounded AI-fix processor. */
object MidiAiFixArtifactPaths {
    fun draft(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/draft.mid"
    fun approved(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/approved.mid"
    fun plan(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/plan.json"
    fun diff(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/diff.json"
    fun audit(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/audit.json"
    fun provenance(partId: String): String = "midi/ai-fix/${safeId(partId, "AI-fix part")}/provenance.json"
}

@Serializable
enum class MidiAiFixSelection { SKIP, APPROVED }

/**
 * The draft is review evidence only. [approved] is selectable only when it is
 * bound to the exact cleaned-MIDI fingerprint in [inputSha256].
 */
@Serializable
data class MidiAiFixReferences(
    val inputSha256: String,
    val draft: WorkflowArtifactReference? = null,
    val approved: WorkflowArtifactReference? = null
) {
    init { require(SHA_256.matches(inputSha256)) { "AI-fix input fingerprint is invalid" } }

    fun requireCanonical(partId: String) {
        draft?.let { require(it.file == MidiAiFixArtifactPaths.draft(partId)) { "AI-fix draft path is not canonical for '$partId'" } }
        approved?.let { require(it.file == MidiAiFixArtifactPaths.approved(partId)) { "Approved AI-fix path is not canonical for '$partId'" } }
    }
}

/** Stable project-relative locations reserved for adjacent Cohesion boundaries. */
object CohesionBoundaryArtifactPaths {
    private fun directory(outgoingInstanceId: String, incomingInstanceId: String): String =
        "cohesion/boundaries/${safeId(outgoingInstanceId, "outgoing occurrence")}--${safeId(incomingInstanceId, "incoming occurrence")}"

    fun draft(outgoingInstanceId: String, incomingInstanceId: String): String =
        "${directory(outgoingInstanceId, incomingInstanceId)}/boundary.draft.json"

    fun approved(outgoingInstanceId: String, incomingInstanceId: String): String =
        "${directory(outgoingInstanceId, incomingInstanceId)}/boundary.json"
}

/** One fingerprinted boundary between adjacent, stable Structure occurrences. */
@Serializable
data class CohesionBoundaryReference(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val inputSha256: String,
    val draft: WorkflowArtifactReference? = null,
    val approved: WorkflowArtifactReference? = null
) {
    init {
        require(SAFE_ID.matches(outgoingInstanceId) && SAFE_ID.matches(incomingInstanceId)) { "Cohesion boundary occurrence ID is invalid" }
        require(outgoingInstanceId != incomingInstanceId) { "Cohesion boundary occurrences must be distinct" }
        require(SHA_256.matches(inputSha256)) { "Cohesion boundary input fingerprint is invalid" }
        draft?.let {
            require(it.file == CohesionBoundaryArtifactPaths.draft(outgoingInstanceId, incomingInstanceId)) {
                "Cohesion boundary draft path is not canonical"
            }
        }
        approved?.let {
            require(it.file == CohesionBoundaryArtifactPaths.approved(outgoingInstanceId, incomingInstanceId)) {
                "Approved Cohesion boundary path is not canonical"
            }
        }
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
    val approved: Boolean,
    /** Empty for the supported legacy per-occurrence Cohesion representation. */
    val boundaries: List<CohesionBoundaryReference> = emptyList(),
    /** The saved Structure occurrence sequence that produced [inputSha256]. */
    val structureSha256: String = ""
) {
    init {
        require(SHA_256.matches(inputSha256)) { "Cohesion input fingerprint is invalid" }
        require(structureSha256.isEmpty() || SHA_256.matches(structureSha256)) { "Cohesion structure fingerprint is invalid" }
        require(occurrences.map(CohesionOccurrenceReference::instanceId).distinct().size == occurrences.size) {
            "Cohesion occurrence IDs must be unique"
        }
        require(!approved || occurrences.all(CohesionOccurrenceReference::approved)) {
            "Approved cohesion requires approved results for every occurrence"
        }
        require(boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }.distinct().size == boundaries.size) {
            "Cohesion boundary identities must be unique"
        }
        require(!approved || boundaries.all { it.approved != null }) {
            "Approved cohesion requires an approved artifact for every boundary"
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
        stale = stale + WorkflowArtifactGraph.invalidatedBy(change)
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
private fun safeId(value: String, label: String): String {
    require(SAFE_ID.matches(value)) { "$label ID is invalid" }
    return value
}
