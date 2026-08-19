package app.melotrail.arrangement

import app.melotrail.commercial.SourceRightsAttestation
import app.melotrail.harmony.HarmonySettings
import app.melotrail.music.MusicalKey
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * In-memory local arranger project. ProjectStore owns the versioned JSON format
 * boundary; this model deliberately keeps source references uniform for the
 * existing arranger code.
 *
 * This is intentionally separate from the existing web application's
 * track-based Project model. Its file references are always relative to the
 * directory containing project.json.
 */
@Serializable
data class Project(
    val version: Int = 1,
    val name: String,
    val parts: List<SongPart> = emptyList(),
    val structure: List<String> = emptyList(),
    val renderFormat: RenderFormat? = null,
    /** v3 durable stale evidence and bounded cross-stage references. */
    val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences(),
    /** V4-only canonical persistence scaffold. Null creative choices mean setup is required. */
    val envelope: ProjectV4Envelope = ProjectV4Envelope(),
    /** Read-time compatibility evidence; it is never silently persisted as canonical project data. */
    @Transient val compatibility: ProjectCompatibility = ProjectCompatibility()
) {
    fun validate(projectRoot: Path): ProjectValidationResult =
        ProjectValidator.validate(this, projectRoot)

    fun requireValid(projectRoot: Path) {
        val validation = validate(projectRoot)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    /** Boundary for MIDI-first stages introduced after the v1 source-audio format. */
    fun requireSelectedMidi(projectRoot: Path): List<SelectedMidiArtifact> {
        requireValid(projectRoot)
        val resolver = SelectedMidiArtifactResolver()
        return parts.map { resolver.resolve(projectRoot, this, it) }
    }

    /**
     * Compatibility path-only guard for generators that consume already validated analysis.
     * New source consumers must retain [SelectedMidiArtifact] through [requireSelectedMidi].
     */
    fun requireCleanMidi(projectRoot: Path): List<Path> {
        return requireSelectedMidi(projectRoot).map(SelectedMidiArtifact::path)
    }

    companion object {
        const val MIDI_FIRST_VERSION = 2
        const val CURRENT_VERSION = 4
    }
}

/** Typed, queryable state produced while reading a supported legacy document. */
data class ProjectCompatibility(
    val sourceVersion: Int = Project.CURRENT_VERSION,
    val warnings: List<String> = emptyList()
)

/** Setup is deliberately not inferred while opening or migrating a project. */
@Serializable
enum class ProjectSetupRequirement { COMPOSITION_SETTINGS, HARMONY }

/**
 * V4's durable aggregate scaffold. Musical values are intentionally absent until
 * their owning setup/harmony contracts land; a null value is an explicit setup
 * requirement, never an implicit default.
 */
@Serializable
data class ProjectV4Envelope(
    val compositionSettings: CompositionSettings? = null,
    val harmony: HarmonySettings? = null,
    val evolvedParts: List<EvolvedPartReference> = emptyList(),
    val structureOccurrences: List<StructureOccurrence> = emptyList(),
    /** Hash-bound index for immutable per-run records; no run output lives in project.json. */
    val stageRuns: ProjectStageRunManifestReference = ProjectStageRunManifestReference(),
    val arrangementAssignments: List<ArrangementAssignmentReference> = emptyList()
) {
    fun setupRequirements(): Set<ProjectSetupRequirement> = buildSet {
        if (compositionSettings?.complete != true) add(ProjectSetupRequirement.COMPOSITION_SETTINGS)
        if (harmony == null) add(ProjectSetupRequirement.HARMONY)
    }

    fun requireWellFormed(partIds: Set<String>) {
        require(evolvedParts.map(EvolvedPartReference::partId).distinct().size == evolvedParts.size) {
            "V4 evolved part references must be unique"
        }
        require(evolvedParts.all { it.partId in partIds }) { "V4 evolved part references an unknown part" }
        require(structureOccurrences.map(StructureOccurrence::instanceId).distinct().size == structureOccurrences.size) {
            "V4 structure occurrence IDs must be unique"
        }
        require(structureOccurrences.all { it.partId in partIds }) { "V4 structure occurrence references an unknown part" }
        require(arrangementAssignments.map { it.occurrenceId to it.instrumentId }.distinct().size == arrangementAssignments.size) {
            "V4 arrangement assignments must be unique"
        }
        require(arrangementAssignments.all { assignment -> structureOccurrences.any { it.instanceId == assignment.occurrenceId } }) {
            "V4 arrangement assignment references an unknown occurrence"
        }
    }
}

/** Explicit composition context. Null at the envelope boundary still means setup is required. */
@Serializable
data class CompositionSettings(
    /** Schema revision for this persisted record, not the user's optimistic revision. */
    val revision: Int = 1,
    val key: MusicalKey,
    val tempo: Tempo,
    val timeSignature: TimeSignature,
    /** Absent only on projects written before the typed settings service. */
    val profile: CompositionProfileRef? = null,
    /** Absent only on projects written before the typed settings service. */
    val mood: MoodRef? = null,
    /** Increments for every accepted settings decision. Zero denotes the compatible incomplete form. */
    val decisionRevision: Long = 0,
    /** Fingerprint of the catalog resolution that informed this decision. */
    val resolvedProfileSha256: String = "",
    /** Fingerprint of the complete user decision, including the project name. */
    val decisionSha256: String = ""
) {
    init { require(revision == 1) { "Unsupported composition settings revision: $revision" } }

    val complete: Boolean
        get() = profile != null && mood != null && decisionRevision > 0 &&
            SHA_256_DIGEST.matches(resolvedProfileSha256) && SHA_256_DIGEST.matches(decisionSha256)

    fun requireWellFormed() {
        profile?.requireValid()
        mood?.requireValid()
        require((profile == null) == (mood == null)) { "Composition profile and mood must be present together" }
        if (profile != null) {
            require(complete) { "Complete composition settings require decision fingerprints and a positive revision" }
        } else {
            require(decisionRevision == 0L && resolvedProfileSha256.isEmpty() && decisionSha256.isEmpty()) {
                "Incomplete composition settings cannot carry decision evidence"
            }
        }
    }
}

@Serializable
data class EvolvedPartReference(val partId: String) {
    init { require(SAFE_PROJECT_ID.matches(partId)) { "V4 part ID is invalid" } }
}

@Serializable
data class StructureOccurrence(val instanceId: String, val partId: String) {
    init {
        require(SAFE_PROJECT_ID.matches(instanceId)) { "V4 structure occurrence ID is invalid" }
        require(SAFE_PROJECT_ID.matches(partId)) { "V4 structure occurrence part ID is invalid" }
    }
}

/** Portable provenance snapshot: identifiers and hashes only, never renderer filenames or local library paths. */
@Serializable
data class LibraryProvenanceSnapshot(
    val libraryId: String,
    val licenseSha256: String,
    val provenanceSha256: String
) {
    init {
        require(SAFE_PROJECT_ID.matches(libraryId)) { "Library ID is invalid" }
        require(SHA_256_DIGEST.matches(licenseSha256) && SHA_256_DIGEST.matches(provenanceSha256)) {
            "Library provenance fingerprints are invalid"
        }
    }
}

/** Stable arrangement decision evidence; it intentionally has no engine filename or filesystem path field. */
@Serializable
data class ArrangementAssignmentReference(
    val occurrenceId: String,
    val instrumentId: String,
    val decisionSha256: String,
    val libraryProvenance: LibraryProvenanceSnapshot
) {
    init {
        require(SAFE_PROJECT_ID.matches(occurrenceId) && SAFE_PROJECT_ID.matches(instrumentId)) {
            "Arrangement assignment identity is invalid"
        }
        require(SHA_256_DIGEST.matches(decisionSha256)) { "Arrangement decision fingerprint is invalid" }
    }
}

/** Canonical melody/performance part persisted by schema-v4 projects. */
@Serializable
data class SongPart(
    val id: String,
    /** Source file for v1 and v2 projects. It is always relative to project.json. */
    val file: String,
    /** Read-only source compatibility input. New writes always use [sectionType]. */
    @Transient val role: String = "",
    val analysis: PartAnalysisReference? = null,
    val midi: MidiReferences? = null,
    /** Null is a legacy/unattested source; it can never be commercial-ready. */
    val sourceAttestation: SourceRightsAttestation? = null,
    /** Optional only for compatible reads; every new unified import records both immutable boundaries. */
    val importEvidence: ImportEvidence? = null,
    /** User-facing display name; unlike [id], it can change. */
    val name: String = id,
    val sectionType: SectionTypeId = SectionTypeCatalog.fromLegacyRole(role),
    /** Optional analysis/user confirmation of the source key; this never changes project key. */
    val sourceKeyEvidence: SourceKeyEvidence? = null,
    /** Reserved stable run reference for Task 011's stage manifests; never a filesystem path. */
    val stageManifestRef: String? = null,
    /** Optimistic revision for explicit name/section decisions. */
    val revision: Long = 1,
    /** A source-first Task 013 import has durable source evidence but no extracted MIDI yet. */
    val importPending: Boolean = false,
    /** Typed v1 compatibility data. It preserves a legacy source without pretending it is MIDI. */
    val legacySourceOnly: Boolean = false
) {
    init {
        require(id.isNotBlank() && SAFE_PROJECT_ID.matches(id)) { "Song part ID is invalid" }
        require(name.isNotBlank() && name.length <= 120 && name.none { it.isISOControl() }) { "Song part name is invalid" }
        require(revision > 0) { "Song part revision must be positive" }
        stageManifestRef?.let { require(SAFE_PROJECT_ID.matches(it)) { "Stage manifest reference is invalid" } }
    }

    val unsupportedSectionWarning: String?
        get() = if (SectionTypeCatalog.isSupported(sectionType)) null
        else "Unsupported section type '${sectionType.value}' was preserved from legacy project data."

}

@Serializable
data class SourceKeyEvidence(
    val key: MusicalKey,
    val confirmed: Boolean = false
)

/** Source compatibility alias. Runtime code uses [SongPart]. */
@Deprecated("Use SongPart")
typealias Part = SongPart

@Serializable
data class ImportEvidence(
    val sourceSha256: String,
    val rawMidiSha256: String
) {
    fun requireValid() {
        require(SHA_256.matches(sourceSha256)) { "Source fingerprint must be a lowercase SHA-256 digest" }
        require(SHA_256.matches(rawMidiSha256)) { "Raw MIDI fingerprint must be a lowercase SHA-256 digest" }
    }

    private companion object {
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

@Serializable
data class RenderFormat(
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    val bitDepth: Int = 24
)

@Serializable
data class MidiReferences(
    /** Immutable project-confined evidence produced by import or transcription. */
    val raw: String? = null,
    /** Absent until the user explicitly runs Clean MIDI. */
    val clean: String? = null,
    /** Null only for pre-quality-report projects, which remain readable as legacy/unknown. */
    val cleanup: MidiCleanupOptions? = null,
    val quality: String? = null,
    /** Absent for legacy/manual Clean MIDI until Normalize is explicitly executed. */
    val normalized: String? = null,
    /** Hash-bound report for [normalized]; it is never fabricated during legacy reads. */
    val normalization: String? = null,
    /** Legacy read adapter only. New approval is always fingerprint-bound in [cleanApproval]. */
    val approvedRepair: Boolean = false,
    /** Exact automatic or explicit approval of raw, clean, options, and report evidence. */
    val cleanApproval: MidiCleanupApproval? = null,
    /** Explicit optional base branch; a draft is never selected. */
    val aiFixSelection: MidiAiFixSelection = MidiAiFixSelection.SKIP,
    /** Optional retained draft/approval evidence, fingerprinted against [clean]. */
    val aiFix: MidiAiFixReferences? = null,
    /** The sole MIDI artifact used by analysis and all downstream MIDI-first stages. */
    val analysisInput: MidiAnalysisInput = MidiAnalysisInput.CURRENT,
    /** Optional derived MIDI; cleaned MIDI remains immutable evidence. */
    val feel: MidiFeelReferences? = null
)

/** Reference to the analysis JSON generated for a part, when available. */
@Serializable
data class PartAnalysisReference(
    val file: String,
    /** Null means legacy audio analysis; MIDI is deliberately a distinct JSON contract. */
    val kind: AnalysisKind? = null
)

@Serializable
enum class AnalysisKind { AUDIO, MIDI }

data class ProjectValidationResult(
    val errors: List<String>,
    val setupRequirements: Set<ProjectSetupRequirement> = emptySet()
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/** Validates the local project boundary without copying, modifying, or decoding audio. */
object ProjectValidator {
    fun validate(project: Project, projectRoot: Path): ProjectValidationResult {
        val errors = mutableListOf<String>()
        val root = projectRoot.toAbsolutePath().normalize()

        if (project.version !in setOf(1, 2, 3, Project.CURRENT_VERSION)) {
            errors += "Unsupported project version: ${project.version}"
        }
        if (project.version >= Project.MIDI_FIRST_VERSION) {
            val format = project.renderFormat
            if (format == null) {
                errors += "MIDI-first projects require an explicit render format"
            } else {
                if (format.sampleRate !in 8_000..384_000) errors += "Render sample rate must be from 8000 to 384000"
                if (format.channels !in 1..32) errors += "Render channels must be from 1 to 32"
                if (format.bitDepth != 24) errors += "Render bit depth must be PCM-24"
            }
        }
        if (project.name.isBlank()) {
            errors += "Project name must not be blank"
        }

        project.envelope.compositionSettings?.let { settings ->
            runCatching(settings::requireWellFormed).exceptionOrNull()?.let { error ->
                errors += "Composition settings are invalid: ${error.message}"
            }
        }
        project.envelope.harmony?.let { harmony ->
            runCatching { harmony.requireWellFormed(project.envelope.compositionSettings?.key) }.exceptionOrNull()?.let { error ->
                errors += "Harmony settings are invalid: ${error.message}"
            }
        }

        val duplicateIds = project.parts
            .groupingBy { it.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate part IDs: ${duplicateIds.sorted().joinToString(", ")}"
        }

        project.parts.forEach { part ->
            if (part.id.isBlank()) {
                errors += "Part ID must not be blank"
            }
            if (part.name.isBlank() || part.name.length > 120 || part.name.any { it.isISOControl() }) {
                errors += "Part '${part.id}' name is invalid"
            }
            if (part.revision <= 0) errors += "Part '${part.id}' revision must be positive"
            part.stageManifestRef?.let { if (!SAFE_PROJECT_ID.matches(it)) errors += "Part '${part.id}' stage manifest reference is invalid" }
            validateFileReference(root, part.file, "Part '${part.id}' source", errors)
            part.importEvidence?.let { evidence ->
                runCatching(evidence::requireValid).exceptionOrNull()?.let { error ->
                    errors += "Part '${part.id}' import evidence is invalid: ${error.message}"
                }
            }
            if (project.version >= Project.MIDI_FIRST_VERSION) {
                val midi = part.midi
                if (midi == null) {
                    if (!(project.version == Project.CURRENT_VERSION && (part.legacySourceOnly || part.importPending))) {
                        errors += "Part '${part.id}' requires raw MIDI; import it before Clean MIDI"
                    }
                } else {
                    if (part.legacySourceOnly || part.importPending) errors += "Part '${part.id}' cannot be both MIDI-first and source-only"
                    midi.raw?.let { validateFileReference(root, it, "Part '${part.id}' raw MIDI", errors) }
                    midi.clean?.let { validateFileReference(root, it, "Part '${part.id}' cleaned MIDI", errors) }
                    midi.normalized?.let { validateFileReference(root, it, "Part '${part.id}' normalized MIDI", errors) }
                    if (midi.raw != null && midi.clean == null && (midi.cleanup != null || midi.quality != null)) {
                        errors += "Part '${part.id}' has cleanup evidence without cleaned MIDI"
                    }
                    if (midi.raw != null && midi.clean != null && (midi.cleanup == null || midi.quality == null)) {
                        errors += "Part '${part.id}' cleaned MIDI requires a quality report"
                    }
                    if (midi.raw == null && midi.clean == null) {
                        errors += "Part '${part.id}' requires a cleaned MIDI reference"
                    }
                    if ((midi.cleanup == null) != (midi.quality == null)) {
                        errors += "Part '${part.id}' MIDI cleanup provenance and quality report must be present together"
                    }
                    if ((midi.normalized == null) != (midi.normalization == null)) {
                        errors += "Part '${part.id}' MIDI normalization output and report must be present together"
                    }
                    if (midi.normalized != null && midi.clean == null) {
                        errors += "Part '${part.id}' normalized MIDI requires cleaned MIDI evidence"
                    }
                    if (midi.approvedRepair && (midi.cleanup == null || midi.quality == null)) {
                        errors += "Part '${part.id}' has an invalid legacy MIDI cleanup approval flag"
                    }
                    midi.cleanApproval?.let { approval ->
                        runCatching(approval::requireValid).exceptionOrNull()?.let { error ->
                            errors += "Part '${part.id}' MIDI cleanup approval is invalid: ${error.message}"
                        }
                        if (midi.cleanup == null || midi.quality == null || midi.clean == null || midi.raw == null) {
                            errors += "Part '${part.id}' cannot approve missing MIDI cleanup evidence"
                        }
                    }
                    runCatching { midi.aiFix?.requireCanonical(part.id) }.exceptionOrNull()?.let { error ->
                        errors += "Part '${part.id}' AI-fix references are invalid: ${error.message}"
                    }
                    if (midi.aiFixSelection == MidiAiFixSelection.APPROVED && midi.aiFix?.approved == null) {
                        errors += "Part '${part.id}' selects an approved AI fix without an approved artifact"
                    }
                    if (midi.aiFixSelection == MidiAiFixSelection.APPROVED) {
                        midi.aiFix?.approved?.let {
                            validateFileReference(root, it.file, "Part '${part.id}' approved AI-fix MIDI", errors)
                        }
                    }
                    midi.cleanup?.let {
                        runCatching(it::requireValid).exceptionOrNull()?.let { error ->
                            errors += "Part '${part.id}' MIDI cleanup options are invalid: ${error.message}"
                        }
                    }
                    midi.quality?.let { validateFileReference(root, it, "Part '${part.id}' MIDI quality report", errors) }
                    midi.normalization?.let { validateFileReference(root, it, "Part '${part.id}' MIDI normalization report", errors) }
                    midi.feel?.let { feel ->
                        validateFileReference(root, feel.derived, "Part '${part.id}' Lo-fi Feel MIDI", errors)
                        validateFileReference(root, feel.report, "Part '${part.id}' Lo-fi Feel report", errors)
                    }
                    if (midi.analysisInput == MidiAnalysisInput.LOFI_FEEL && midi.feel == null) {
                        errors += "Part '${part.id}' selects Lo-fi Feel without a derived MIDI artifact"
                    }
                }
            }
            if (part.importPending && (part.midi != null || part.importEvidence != null || part.analysis != null || part.legacySourceOnly)) {
                errors += "Part '${part.id}' has invalid pending-import state"
            }
            part.analysis?.let {
                validateFileReference(root, it.file, "Part '${part.id}' analysis", errors)
            }
        }

        val knownPartIds = project.parts.map { it.id }.toSet()
        project.structure.forEachIndexed { index, partId ->
            when {
                partId.isBlank() -> errors += "Structure entry ${index + 1} must not be blank"
                partId !in knownPartIds ->
                    errors += "Structure entry ${index + 1} references unknown part ID '$partId'"
            }
        }

        if (project.version == Project.CURRENT_VERSION) {
            runCatching { project.envelope.requireWellFormed(knownPartIds) }.exceptionOrNull()?.let { error ->
                errors += "V4 envelope is invalid: ${error.message}"
            }
            runCatching { project.envelope.stageRuns.requireCanonical() }.exceptionOrNull()?.let { error ->
                errors += "Stage-run manifest reference is invalid: ${error.message}"
            }
            project.envelope.stageRuns.index?.let { reference ->
                validateArtifactReference(root, reference, "Stage-run index", errors)
            }
        }

        return ProjectValidationResult(
            errors,
            if (project.version == Project.CURRENT_VERSION) project.envelope.setupRequirements() else emptySet()
        )
    }

    private fun validateFileReference(
        projectRoot: Path,
        reference: String,
        label: String,
        errors: MutableList<String>
    ) {
        val relativePath = try {
            Path.of(reference)
        } catch (_: Exception) {
            errors += "$label path is invalid: $reference"
            return
        }

        if (reference.isBlank() || relativePath.isAbsolute) {
            errors += "$label path must be relative to the project root: $reference"
            return
        }

        val resolvedPath = projectRoot.resolve(relativePath).normalize()
        if (!resolvedPath.startsWith(projectRoot)) {
            errors += "$label path escapes the project root: $reference"
            return
        }
        if (!Files.isRegularFile(resolvedPath)) {
            errors += "$label file does not exist: $reference"
            return
        }

        /*
         * A relative symlink can otherwise resolve outside the project after
         * the lexical path check above. Do not allow project metadata to point
         * at files outside its own directory.
         */
        try {
            if (!resolvedPath.toRealPath().startsWith(projectRoot.toRealPath())) {
                errors += "$label path escapes the project root: $reference"
            }
        } catch (_: Exception) {
            errors += "$label file cannot be resolved: $reference"
        }
    }

    private fun validateArtifactReference(
        projectRoot: Path,
        reference: WorkflowArtifactReference,
        label: String,
        errors: MutableList<String>
    ) {
        val before = errors.size
        validateFileReference(projectRoot, reference.file, label, errors)
        if (errors.size != before) return
        val actual = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(projectRoot.resolve(reference.file)).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrElse {
            errors += "$label fingerprint cannot be read: ${reference.file}"
            return
        }
        if (actual != reference.sha256) errors += "$label fingerprint does not match: ${reference.file}"
    }

    private fun validateArtifactReference(
        projectRoot: Path,
        reference: ArtifactRef,
        label: String,
        errors: MutableList<String>
    ) = validateArtifactReference(projectRoot, WorkflowArtifactReference(reference.path, reference.sha256), label, errors)
}

private val SAFE_PROJECT_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256_DIGEST = Regex("[0-9a-f]{64}")
