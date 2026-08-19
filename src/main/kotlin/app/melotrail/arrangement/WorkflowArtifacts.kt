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
    CORRECTED_MIDI,
    ENHANCED_MIDI,
    AI_FIX,
    MIDI_FEEL,
    ANALYSIS,
    COHESION,
    ARRANGEMENT,
    GENERATED_MIDI,
    HUMANIZATION,
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
    CORRECTION_SELECTION,
    ENHANCEMENT_SELECTION,
    AI_FIX_SELECTION,
    MIDI_FEEL,
    ANALYSIS,
    STRUCTURE,
    /** Section context changes planning/arrangement, but not immutable MIDI or its analysis. */
    PART_SECTION,
    /** A newly approved arrangement changes the only supported Cohesion input. */
    ARRANGEMENT,
    COHESION,
    HUMANIZATION,
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
        WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION,
        WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
        WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    private val allAfterSelection = setOf(
        WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION,
        WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
        WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    private val allAfterAnalysis = setOf(
        WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION,
        WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE,
        WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
    )

    fun invalidatedBy(change: WorkflowChange): Set<WorkflowArtifact> = when (change) {
        WorkflowChange.SOURCE_OR_RAW -> setOf(WorkflowArtifact.CLEAN_MIDI) + allAfterRepair
        WorkflowChange.CLEANED_MIDI -> allAfterRepair
        WorkflowChange.SOURCE_KEY -> allAfterRepair
        WorkflowChange.CORRECTION_SELECTION -> allAfterSelection
        WorkflowChange.ENHANCEMENT_SELECTION -> allAfterSelection
        WorkflowChange.AI_FIX_SELECTION -> allAfterSelection
        WorkflowChange.MIDI_FEEL -> setOf(WorkflowArtifact.ANALYSIS) + allAfterAnalysis
        WorkflowChange.ANALYSIS, WorkflowChange.STRUCTURE, WorkflowChange.PART_SECTION -> allAfterAnalysis
        WorkflowChange.ARRANGEMENT -> setOf(
            WorkflowArtifact.COHESION, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        WorkflowChange.COHESION -> setOf(
            WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        WorkflowChange.HUMANIZATION -> setOf(
            WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX,
            WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
            WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** A project-key decision invalidates its derived transposition and every dependent artifact. */
        WorkflowChange.COMPOSITION_KEY -> setOf(
            WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS,
            WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX,
            WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
            WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Tempo/meter are declared arrangement-normalization inputs. */
        WorkflowChange.COMPOSITION_TEMPO_OR_METER -> setOf(
            WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.COHESION, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
            WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Profile and mood can change MIDI-feel policy, then every artifact derived from that selected input. */
        WorkflowChange.COMPOSITION_PROFILE_OR_MOOD -> setOf(
            WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION,
            WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE,
            WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
        )
        /** Harmony is planner context, never source, extraction, cleanup, or analysis evidence. */
        WorkflowChange.HARMONY -> setOf(
            WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.COHESION,
            WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
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

/** Canonical immutable output/report locations for Task 017 technical correction. */
object TechnicalCorrectionArtifactPaths {
    private fun directory(partId: String, inputSha256: String): String {
        require(SHA_256.matches(inputSha256)) { "Technical-correction input fingerprint is invalid" }
        return "midi/corrected/${safeId(partId, "technical-correction part")}/$inputSha256"
    }
    fun output(partId: String, inputSha256: String): String = "${directory(partId, inputSha256)}/corrected.mid"
    fun report(partId: String, inputSha256: String): String = "${directory(partId, inputSha256)}/report.json"
    fun plan(partId: String, inputSha256: String): String = "${directory(partId, inputSha256)}/plan.json"
}

/** Immutable, context-hash-bound locations for a bounded enhancement run. */
object EnhancementArtifactPaths {
    private fun directory(partId: String, contextSha256: String): String {
        require(SHA_256.matches(contextSha256)) { "Enhancement context fingerprint is invalid" }
        return "midi/enhancement/${safeId(partId, "enhancement part")}/$contextSha256"
    }
    fun output(partId: String, contextSha256: String): String = "${directory(partId, contextSha256)}/enhanced.mid"
    fun report(partId: String, contextSha256: String): String = "${directory(partId, contextSha256)}/report.json"
    fun plan(partId: String, contextSha256: String): String = "${directory(partId, contextSha256)}/plan.json"
    fun provenance(partId: String, contextSha256: String): String = "${directory(partId, contextSha256)}/provenance.json"
}

/** Draft evidence is inspectable but cannot become the selected MIDI until approved. */
@Serializable
enum class EnhancementApproval { DRAFT, APPROVED, REJECTED }

/** A completed enhancement is valid only for the exact corrected artifact and context. */
@Serializable
data class EnhancementReferences(
    val intensity: EnhancementIntensity,
    val input: WorkflowArtifactReference,
    val output: WorkflowArtifactReference,
    val report: WorkflowArtifactReference,
    val contextSha256: String,
    val approval: EnhancementApproval = EnhancementApproval.APPROVED,
    val plan: WorkflowArtifactReference? = null,
    val provenance: WorkflowArtifactReference? = null
) {
    init { require(SHA_256.matches(contextSha256)) { "Enhancement context fingerprint is invalid" } }
    fun requireCanonical(partId: String) {
        require(intensity != EnhancementIntensity.OFF) { "Off enhancement has no derived artifact" }
        require(output.file == EnhancementArtifactPaths.output(partId, contextSha256) && report.file == EnhancementArtifactPaths.report(partId, contextSha256)) {
            "Enhancement artifact paths are not canonical"
        }
        plan?.let { require(it.file == EnhancementArtifactPaths.plan(partId, contextSha256)) { "Enhancement plan path is not canonical" } }
        provenance?.let { require(it.file == EnhancementArtifactPaths.provenance(partId, contextSha256)) { "Enhancement provenance path is not canonical" } }
    }
}

@Serializable
enum class TechnicalCorrectionSelection { BASE, CORRECTED }

/** A corrected artifact is valid only for the exact selected-base fingerprint and structured context. */
@Serializable
data class TechnicalCorrectionReferences(
    val input: WorkflowArtifactReference,
    val output: WorkflowArtifactReference,
    val report: WorkflowArtifactReference,
    val contextSha256: String
) {
    init { require(SHA_256.matches(contextSha256)) { "Technical-correction context fingerprint is invalid" } }
    fun requireCanonical(partId: String) {
        require(output.file == TechnicalCorrectionArtifactPaths.output(partId, input.sha256)) { "Technical-correction output path is not canonical" }
        require(report.file == TechnicalCorrectionArtifactPaths.report(partId, input.sha256)) { "Technical-correction report path is not canonical" }
    }
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
    val approved: WorkflowArtifactReference? = null,
    /** Digest of code-owned bridge MIDI; absent only for historical target-order evidence. */
    val bridgeSha256: String? = null
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
        bridgeSha256?.let { require(SHA_256.matches(it)) { "Approved Cohesion bridge fingerprint is invalid" } }
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
    /** Historical whole-occurrence evidence only; new Cohesion writes this empty. */
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

/**
 * Approval evidence that Cohesion may consume. These hashes bind an approved
 * arrangement to the exact Structure, occurrence identities, planning context,
 * and song plan from which it was made.
 */
@Serializable
data class ArrangementApprovalReferences(
    val arrangement: WorkflowArtifactReference,
    val structureSha256: String,
    val occurrenceSha256: String,
    val contextSha256: String,
    val planSha256: String
) {
    init {
        require(SHA_256.matches(structureSha256) && SHA_256.matches(occurrenceSha256) &&
            SHA_256.matches(contextSha256) && SHA_256.matches(planSha256)) {
            "Arrangement approval fingerprints are invalid"
        }
        require(arrangement.file == "arrangement.json") { "Approved arrangement path is not canonical" }
    }
}

/** References only; Task 071 owns commercial decisions and manifest UI. */
@Serializable
data class CommercialProvenanceReferences(
    val manifest: WorkflowArtifactReference? = null,
    val release: WorkflowArtifactReference? = null
)

@Serializable
enum class HumanizationSelection { BYPASS, HUMANIZED }

/** A single input/output pair in a humanization run; both are canonical evidence. */
@Serializable
data class HumanizationArtifactReference(
    val id: String,
    val role: HumanizationRole,
    val input: WorkflowArtifactReference,
    val output: WorkflowArtifactReference
) {
    init { require(SAFE_ID.matches(id)) { "Humanization artifact ID is invalid" } }
}

/**
 * Selected, immutable run evidence. The configuration and seed are persisted
 * with the exact input/output hashes, so a later render cannot reinterpret a
 * variation from ambient preferences or a process working directory.
 */
@Serializable
data class HumanizationWorkflowReferences(
    val config: HumanizationConfig,
    val seed: Long,
    val processorVersion: String,
    val inputsSha256: String,
    val artifacts: List<HumanizationArtifactReference>,
    val report: WorkflowArtifactReference,
    val legacyGrooveInputs: Set<String> = emptySet()
) {
    init {
        config.requireValid()
        require(SHA_256.matches(inputsSha256) && processorVersion == "seeded-humanization-v1") { "Humanization run identity is invalid" }
        require(artifacts.isNotEmpty() && artifacts.map(HumanizationArtifactReference::id).distinct().size == artifacts.size) { "Humanization artifacts are invalid" }
        require(legacyGrooveInputs.all(SAFE_ID::matches) && legacyGrooveInputs.all { it in artifacts.map(HumanizationArtifactReference::id) }) { "Humanization legacy-groove evidence is invalid" }
    }
}

@Serializable
data class ProjectWorkflowReferences(
    val stale: Set<WorkflowArtifact> = emptySet(),
    val cohesion: CohesionWorkflowReferences? = null,
    val arrangement: ArrangementApprovalReferences? = null,
    val humanizationSelection: HumanizationSelection = HumanizationSelection.BYPASS,
    val humanization: HumanizationWorkflowReferences? = null,
    /** One-way marker for the Task 023 dependency migration. */
    val cohesionOrderMigration: Int = 0,
    val commercialProvenance: CommercialProvenanceReferences? = null
) {
    fun invalidate(change: WorkflowChange): ProjectWorkflowReferences = copy(
        stale = stale + WorkflowArtifactGraph.invalidatedBy(change)
    )

    fun markCurrent(vararg artifacts: WorkflowArtifact): ProjectWorkflowReferences = copy(stale = stale - artifacts.toSet())

    /** Retains old files as evidence while requiring Arrange → Cohesion lineage exactly once. */
    fun migrateCohesionOrderIfNeeded(): ProjectWorkflowReferences =
        if (cohesionOrderMigration >= 1 || cohesion == null || arrangement != null) this
        else copy(
            stale = stale + setOf(
                WorkflowArtifact.COHESION, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS,
                WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER,
                WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT
            ),
            cohesionOrderMigration = 1
        )
}

/** Small atomic metadata boundary used after a stage has actually published output. */
object ProjectWorkflowStore {
    fun update(root: Path, transform: (ProjectWorkflowReferences) -> ProjectWorkflowReferences) {
        val project = ProjectStore.read(root)
        if (project.version != Project.CURRENT_VERSION) return
        ProjectStore.write(root, project.copy(workflow = transform(project.workflow)))
    }

    /** A build request is explicit authority to migrate old target-order lineage, never project open. */
    fun migrateCohesionOrderForBuild(root: Path): Boolean {
        if (!java.nio.file.Files.isRegularFile(root.resolve(ProjectStore.FILE_NAME))) return false
        val project = ProjectStore.read(root)
        if (project.version != Project.CURRENT_VERSION) return false
        val migrated = project.workflow.migrateCohesionOrderIfNeeded()
        if (migrated == project.workflow) return false
        ProjectStore.write(root, project.copy(workflow = migrated))
        return true
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private fun safeId(value: String, label: String): String {
    require(SAFE_ID.matches(value)) { "$label ID is invalid" }
    return value
}
