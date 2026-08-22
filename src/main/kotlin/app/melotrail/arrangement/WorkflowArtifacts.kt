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
    ARRANGEMENT,
    GENERATED_MIDI,
    COHESION,
    CRITIC,
    FULL_SONG_ENHANCEMENT,
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
    /** Baseline ensemble MIDI changed after Arrangement approval. */
    GENERATED_MIDI,
    COHESION,
    CRITIC,
    FULL_SONG_ENHANCEMENT_SELECTION,
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
    private val orderedDerivedArtifacts = listOf(
        WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI,
        WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.ARRANGEMENT,
        WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.COHESION, WorkflowArtifact.CRITIC,
        WorkflowArtifact.FULL_SONG_ENHANCEMENT, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
        WorkflowArtifact.COMMERCIAL_EXPORT
    )

    private fun from(artifact: WorkflowArtifact) = orderedDerivedArtifacts.drop(orderedDerivedArtifacts.indexOf(artifact).coerceAtLeast(0)).toSet()

    fun invalidatedBy(change: WorkflowChange): Set<WorkflowArtifact> = when (change) {
        WorkflowChange.SOURCE_OR_RAW -> setOf(WorkflowArtifact.CLEAN_MIDI) + from(WorkflowArtifact.TRANSPOSED_MIDI)
        WorkflowChange.CLEANED_MIDI, WorkflowChange.SOURCE_KEY -> from(WorkflowArtifact.TRANSPOSED_MIDI)
        WorkflowChange.CORRECTION_SELECTION, WorkflowChange.ENHANCEMENT_SELECTION, WorkflowChange.AI_FIX_SELECTION -> from(WorkflowArtifact.ENHANCED_MIDI)
        WorkflowChange.MIDI_FEEL -> from(WorkflowArtifact.ANALYSIS)
        WorkflowChange.ANALYSIS, WorkflowChange.STRUCTURE, WorkflowChange.PART_SECTION -> from(WorkflowArtifact.ARRANGEMENT)
        WorkflowChange.ARRANGEMENT -> from(WorkflowArtifact.GENERATED_MIDI)
        WorkflowChange.GENERATED_MIDI -> from(WorkflowArtifact.COHESION)
        WorkflowChange.COHESION -> from(WorkflowArtifact.CRITIC)
        WorkflowChange.CRITIC -> from(WorkflowArtifact.FULL_SONG_ENHANCEMENT)
        WorkflowChange.FULL_SONG_ENHANCEMENT_SELECTION -> from(WorkflowArtifact.HUMANIZATION)
        WorkflowChange.HUMANIZATION -> from(WorkflowArtifact.HUMANIZATION)
        WorkflowChange.COMPOSITION_KEY -> from(WorkflowArtifact.TRANSPOSED_MIDI)
        WorkflowChange.COMPOSITION_TEMPO_OR_METER -> from(WorkflowArtifact.ARRANGEMENT)
        WorkflowChange.COMPOSITION_PROFILE_OR_MOOD -> from(WorkflowArtifact.ENHANCED_MIDI)
        WorkflowChange.HARMONY -> from(WorkflowArtifact.CORRECTED_MIDI)
        WorkflowChange.MIX_ONLY -> from(WorkflowArtifact.DRY_MIX)
        WorkflowChange.AUDIO_TEXTURE -> from(WorkflowArtifact.AUDIO_TEXTURE)
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
enum class MidiAiFixSelection { PENDING, SKIP, APPROVED }

/**
 * The draft is review evidence only. [approved] is selectable only when it is
 * bound to the exact selected corrected-MIDI fingerprint in [inputSha256].
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

/** One derived melody per stable occurrence; the selected part MIDI remains immutable. */
object CohesionOccurrenceArtifactPaths {
    fun output(instanceId: String, inputSha256: String): String {
        require(SHA_256.matches(inputSha256)) { "Cohesion occurrence input fingerprint is invalid" }
        return "cohesion/occurrences/${safeId(instanceId, "cohesion occurrence")}/$inputSha256/cohesive.mid"
    }
    fun enhancedOutput(inputSha256: String, instanceId: String): String {
        require(SHA_256.matches(inputSha256)) { "Cohesion input fingerprint is invalid" }
        return "cohesion/runs/$inputSha256/occurrences/${safeId(instanceId, "cohesion occurrence")}.mid"
    }
}

object CohesionRoleArtifactPaths {
    fun output(inputSha256: String, role: String): String {
        require(SHA_256.matches(inputSha256)) { "Cohesion input fingerprint is invalid" }
        return "cohesion/runs/$inputSha256/roles/${safeId(role, "cohesion role")}.mid"
    }
    fun baselinePreview(inputSha256: String) = "cohesion/runs/$inputSha256/preview/baseline.wav"
    fun enhancedPreview(inputSha256: String) = "cohesion/runs/$inputSha256/preview/enhanced.wav"
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
    val approved: Boolean,
    val cohesionInputSha256: String? = null
) {
    init {
        require(SAFE_ID.matches(instanceId)) { "Cohesion occurrence ID is invalid" }
        require(SHA_256.matches(sourceSha256)) { "Cohesion source fingerprint is invalid" }
        cohesionInputSha256?.let { require(SHA_256.matches(it)) { "Cohesion occurrence input fingerprint is invalid" } }
        val expected = cohesionInputSha256?.let { CohesionOccurrenceArtifactPaths.enhancedOutput(it, instanceId) }
            ?: CohesionOccurrenceArtifactPaths.output(instanceId, sourceSha256)
        require(result.file == expected) { "Cohesion occurrence artifact path is not canonical" }
    }
}

@Serializable
data class CohesionRoleReference(
    val role: String,
    val sourceSha256: String,
    val result: WorkflowArtifactReference,
    val approved: Boolean,
    val cohesionInputSha256: String? = null
) {
    init {
        require(SAFE_ID.matches(role) && SHA_256.matches(sourceSha256)) { "Cohesion role reference is invalid" }
        val inputHash = cohesionInputSha256 ?: result.file.split('/').getOrNull(2).orEmpty()
        require(SHA_256.matches(inputHash) && result.file == CohesionRoleArtifactPaths.output(inputHash, role)) { "Cohesion role artifact path is not canonical" }
    }
}

@Serializable
data class CohesionPreviewReferences(
    val baseline: WorkflowArtifactReference,
    val enhanced: WorkflowArtifactReference
)

@Serializable
data class GeneratedMidiArtifactReference(
    val id: String,
    val artifact: WorkflowArtifactReference,
    /** Deterministic, persisted output validation evidence for this role. */
    val validationReport: WorkflowArtifactReference
) {
    init {
        require(SAFE_ID.matches(id)) { "Generated MIDI artifact ID is invalid" }
        require(validationReport.file == GeneratedMidiArtifactPaths.validationReport(id)) {
            "Generated MIDI validation-report path is not canonical"
        }
    }
}

object GeneratedMidiArtifactPaths {
    fun validationReport(id: String): String {
        require(SAFE_ID.matches(id)) { "Generated MIDI artifact ID is invalid" }
        return "midi/generated/$id.validation.json"
    }
}

@Serializable
data class GeneratedMidiWorkflowReferences(
    val arrangementSha256: String,
    val authoritySha256: String,
    val registrySha256: String,
    val generatorVersion: String,
    val seed: Long,
    val artifacts: List<GeneratedMidiArtifactReference>
) {
    init {
        require(SHA_256.matches(arrangementSha256) && SHA_256.matches(authoritySha256) && SHA_256.matches(registrySha256) &&
            generatorVersion == "arrangement-generators-v1" && artifacts.map { it.id }.distinct().size == artifacts.size) {
            "Generated MIDI workflow references are invalid"
        }
    }
}

@Serializable
data class CohesionWorkflowReferences(
    val inputSha256: String,
    val plan: WorkflowArtifactReference,
    val occurrences: List<CohesionOccurrenceReference>,
    val approved: Boolean,
    /** Per-boundary plan and bridge evidence used to derive [occurrences]. */
    val boundaries: List<CohesionBoundaryReference> = emptyList(),
    /** The saved Structure occurrence sequence that produced [inputSha256]. */
    val structureSha256: String = "",
    /** Generated-role derivatives containing only validated Cohesion boundary bridges. */
    val roles: List<CohesionRoleReference> = emptyList(),
    val intensity: CohesionEnhancementIntensity = CohesionEnhancementIntensity.BALANCED,
    val previews: CohesionPreviewReferences? = null
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
        require(roles.map(CohesionRoleReference::role).distinct().size == roles.size) { "Cohesion role IDs must be unique" }
        require(!approved || roles.all(CohesionRoleReference::approved)) { "Approved cohesion requires approved generated-role results" }
        previews?.let {
            require(it.baseline.file == CohesionRoleArtifactPaths.baselinePreview(inputSha256) &&
                it.enhanced.file == CohesionRoleArtifactPaths.enhancedPreview(inputSha256)) {
                "Cohesion preview artifact paths are not canonical"
            }
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
    val planSha256: String,
    val authoritySha256: String,
    val registrySha256: String
) {
    init {
        require(SHA_256.matches(structureSha256) && SHA_256.matches(occurrenceSha256) &&
            SHA_256.matches(contextSha256) && SHA_256.matches(planSha256) && SHA_256.matches(authoritySha256) && SHA_256.matches(registrySha256)) {
            "Arrangement approval fingerprints are invalid"
        }
        require(arrangement.file == "arrangement.json") { "Approved arrangement path is not canonical" }
    }
}

/** Pointer to the selected immutable Task 027 release-lineage manifest. */
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
        require(SHA_256.matches(inputsSha256) && processorVersion in setOf("seeded-humanization-v1", "seeded-humanization-v2")) { "Humanization run identity is invalid" }
        require(artifacts.isNotEmpty() && artifacts.map(HumanizationArtifactReference::id).distinct().size == artifacts.size) { "Humanization artifacts are invalid" }
        require(legacyGrooveInputs.all(SAFE_ID::matches) && legacyGrooveInputs.all { it in artifacts.map(HumanizationArtifactReference::id) }) { "Humanization legacy-groove evidence is invalid" }
    }
}

/** Immutable, hash-bound evidence produced by the later deterministic critic. */
@Serializable
data class CriticWorkflowReferences(
    val inputSha256: String,
    val report: WorkflowArtifactReference
) {
    init {
        require(SHA_256.matches(inputSha256)) { "Critic input fingerprint is invalid" }
        require(report.file == CriticArtifactPaths.report(inputSha256)) { "Critic report path is not canonical" }
    }
}

object CriticArtifactPaths {
    fun report(inputSha256: String): String {
        require(SHA_256.matches(inputSha256)) { "Critic input fingerprint is invalid" }
        return "critic/$inputSha256/report.json"
    }
}

/** The selected downstream source. Drafts are retained evidence, not a selection. */
@Serializable
enum class FullSongEnhancementSelection { UNRESOLVED, BYPASS, NO_OP, APPROVED }

@Serializable
enum class FullSongEnhancementCandidateStatus { DRAFT, APPROVED }

/** One selected full-song candidate output; the input is always verified before rendering. */
@Serializable
data class FullSongEnhancementArtifactReference(
    val id: String,
    val input: WorkflowArtifactReference,
    val output: WorkflowArtifactReference
) {
    init { require(SAFE_ID.matches(id)) { "Full-song enhancement artifact ID is invalid" } }
}

/**
 * Hash-bound candidate and selection evidence.  A draft remains here while the
 * selection is UNRESOLVED; it is never inferred from files on disk.
 */
@Serializable
data class FullSongEnhancementReferences(
    val criticInputSha256: String,
    val criticReportSha256: String? = null,
    val cohesionInputSha256: String,
    val status: FullSongEnhancementCandidateStatus? = null,
    val artifacts: List<FullSongEnhancementArtifactReference> = emptyList(),
    val plan: WorkflowArtifactReference? = null,
    val report: WorkflowArtifactReference? = null
) {
    init {
        require(SHA_256.matches(criticInputSha256) && SHA_256.matches(cohesionInputSha256) &&
            (criticReportSha256 == null || SHA_256.matches(criticReportSha256)) &&
            artifacts.map(FullSongEnhancementArtifactReference::id).distinct().size == artifacts.size &&
            ((status == null && artifacts.isEmpty() && plan == null && report == null) ||
                (status != null && artifacts.isNotEmpty() && plan != null && report != null))) {
            "Full-song enhancement references are invalid"
        }
    }
}

object FullSongEnhancementArtifactPaths {
    private fun directory(criticInputSha256: String, revision: String): String {
        require(SHA_256.matches(criticInputSha256) && SAFE_ID.matches(revision)) { "Full-song enhancement path is invalid" }
        return "midi/full-song-enhance/$criticInputSha256/$revision"
    }
    fun output(criticInputSha256: String, revision: String, id: String): String = "${directory(criticInputSha256, revision)}/${safeId(id, "Full-song enhancement artifact")}.mid"
    fun plan(criticInputSha256: String, revision: String): String = "${directory(criticInputSha256, revision)}/plan.json"
    fun report(criticInputSha256: String, revision: String): String = "${directory(criticInputSha256, revision)}/report.json"
}

@Serializable
data class ProjectWorkflowReferences(
    /** Required field: serialized projects from the superseded shape must fail at open. */
    val fullSongEnhancementSelection: FullSongEnhancementSelection,
    val stale: Set<WorkflowArtifact> = emptySet(),
    val cohesion: CohesionWorkflowReferences? = null,
    val critic: CriticWorkflowReferences? = null,
    val fullSongEnhancement: FullSongEnhancementReferences? = null,
    val arrangement: ArrangementApprovalReferences? = null,
    val humanizationSelection: HumanizationSelection = HumanizationSelection.BYPASS,
    val humanization: HumanizationWorkflowReferences? = null,
    val generatedMidi: GeneratedMidiWorkflowReferences? = null,
    val commercialProvenance: CommercialProvenanceReferences? = null
) {
    init {
        require(when (fullSongEnhancementSelection) {
            FullSongEnhancementSelection.UNRESOLVED -> fullSongEnhancement == null || fullSongEnhancement.status == FullSongEnhancementCandidateStatus.DRAFT
            FullSongEnhancementSelection.APPROVED -> fullSongEnhancement?.status == FullSongEnhancementCandidateStatus.APPROVED
            FullSongEnhancementSelection.NO_OP, FullSongEnhancementSelection.BYPASS -> fullSongEnhancement != null && fullSongEnhancement.status == null
        }) {
            "Full-song enhancement selection does not match its retained evidence"
        }
    }

    fun invalidate(change: WorkflowChange): ProjectWorkflowReferences = copy(
        stale = stale + WorkflowArtifactGraph.invalidatedBy(change)
    )

    fun markCurrent(vararg artifacts: WorkflowArtifact): ProjectWorkflowReferences = copy(stale = stale - artifacts.toSet())

    companion object {
        fun initial() = ProjectWorkflowReferences(FullSongEnhancementSelection.UNRESOLVED)
    }
}

/** Small atomic metadata boundary used after a stage has actually published output. */
object ProjectWorkflowStore {
    fun update(root: Path, transform: (ProjectWorkflowReferences) -> ProjectWorkflowReferences) {
        val project = ProjectStore.read(root)
        ProjectStore.write(root, project.copy(workflow = transform(project.workflow)))
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256 = Regex("[0-9a-f]{64}")
private fun safeId(value: String, label: String): String {
    require(SAFE_ID.matches(value)) { "$label ID is invalid" }
    return value
}
