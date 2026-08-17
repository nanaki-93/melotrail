package app.melotrail.arrangement

import app.melotrail.commercial.SourceRightsAttestation
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-memory local arranger project. ProjectStore owns the v1/v2/v3 JSON format
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
    val parts: List<Part> = emptyList(),
    val structure: List<String> = emptyList(),
    val renderFormat: RenderFormat? = null,
    /** v3 durable stale evidence and bounded cross-stage references. */
    val workflow: ProjectWorkflowReferences = ProjectWorkflowReferences()
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
        require(version >= MIDI_FIRST_VERSION) {
            "Project uses legacy v1 source audio. Prepare clean MIDI for every part before running MIDI-first commands."
        }
        requireValid(projectRoot)
        return parts.map { part ->
            val midi = requireNotNull(part.midi)
            val clean = requireNotNull(midi.clean) { "Part '${part.id}' has not been repaired. Run Repair MIDI before continuing." }
            if (midi.raw != null) {
                require(midi.cleanup != null && midi.quality != null) { "Part '${part.id}' has incomplete MIDI repair provenance." }
                val report = MidiQualityReportStore.read(projectRoot, requireNotNull(midi.quality))
                require(!report.approvalRequired || midi.approvedRepair) { "Part '${part.id}' needs explicit approval of its MIDI repair." }
                MidiQualityReportStore.requireCurrent(projectRoot, part.id, midi.raw, clean, requireNotNull(midi.cleanup), requireNotNull(midi.quality))
            }
            when (midi.analysisInput) {
                MidiAnalysisInput.REPAIRED -> projectRoot.resolve(clean).normalize()
                MidiAnalysisInput.LOFI_FEEL -> requireNotNull(midi.feel).also { MidiFeelReportStore.requireCurrent(projectRoot, part.id, clean, it) }.let { projectRoot.resolve(it.derived).normalize() }
            }
        }
    }

    companion object {
        const val MIDI_FIRST_VERSION = 2
        const val CURRENT_VERSION = 3
    }
}

@Serializable
data class Part(
    val id: String,
    /** Source file for v1 and v2 projects. It is always relative to project.json. */
    val file: String,
    val role: String = "",
    val analysis: PartAnalysisReference? = null,
    val midi: MidiReferences? = null,
    /** Null is a legacy/unattested source; it can never be commercial-ready. */
    val sourceAttestation: SourceRightsAttestation? = null
)

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
    /** Absent until the user explicitly runs Repair MIDI. */
    val clean: String? = null,
    /** Null only for pre-quality-report projects, which remain readable as legacy/unknown. */
    val cleanup: MidiCleanupOptions? = null,
    val quality: String? = null,
    /** True automatically for conservative repairs; explicit only above report thresholds. */
    val approvedRepair: Boolean = false,
    /** The sole MIDI artifact used by analysis and all downstream MIDI-first stages. */
    val analysisInput: MidiAnalysisInput = MidiAnalysisInput.REPAIRED,
    /** Optional derived MIDI; repaired MIDI remains immutable evidence. */
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
    val errors: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/** Validates the local project boundary without copying, modifying, or decoding audio. */
object ProjectValidator {
    fun validate(project: Project, projectRoot: Path): ProjectValidationResult {
        val errors = mutableListOf<String>()
        val root = projectRoot.toAbsolutePath().normalize()

        if (project.version !in setOf(1, 2, Project.CURRENT_VERSION)) {
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
            validateFileReference(root, part.file, "Part '${part.id}' source", errors)
            if (project.version >= Project.MIDI_FIRST_VERSION) {
                val midi = part.midi
                if (midi == null) {
                    errors += "Part '${part.id}' requires raw MIDI; import it before Repair MIDI"
                } else {
                    midi.raw?.let { validateFileReference(root, it, "Part '${part.id}' raw MIDI", errors) }
                    midi.clean?.let { validateFileReference(root, it, "Part '${part.id}' repaired MIDI", errors) }
                    if (midi.raw != null && midi.clean == null && (midi.cleanup != null || midi.quality != null)) {
                        errors += "Part '${part.id}' has repair evidence without repaired MIDI"
                    }
                    if (midi.raw != null && midi.clean != null && (midi.cleanup == null || midi.quality == null)) {
                        errors += "Part '${part.id}' repaired MIDI requires a quality report"
                    }
                    if (midi.raw == null && midi.clean == null) {
                        errors += "Part '${part.id}' requires a repaired MIDI reference"
                    }
                    if ((midi.cleanup == null) != (midi.quality == null)) {
                        errors += "Part '${part.id}' MIDI cleanup provenance and quality report must be present together"
                    }
                    if (midi.approvedRepair && (midi.cleanup == null || midi.quality == null)) {
                        errors += "Part '${part.id}' cannot approve a missing MIDI repair"
                    }
                    midi.cleanup?.let {
                        runCatching(it::requireValid).exceptionOrNull()?.let { error ->
                            errors += "Part '${part.id}' MIDI cleanup options are invalid: ${error.message}"
                        }
                    }
                    midi.quality?.let { validateFileReference(root, it, "Part '${part.id}' MIDI quality report", errors) }
                    midi.feel?.let { feel ->
                        validateFileReference(root, feel.derived, "Part '${part.id}' Lo-fi Feel MIDI", errors)
                        validateFileReference(root, feel.report, "Part '${part.id}' Lo-fi Feel report", errors)
                    }
                    if (midi.analysisInput == MidiAnalysisInput.LOFI_FEEL && midi.feel == null) {
                        errors += "Part '${part.id}' selects Lo-fi Feel without a derived MIDI artifact"
                    }
                }
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

        return ProjectValidationResult(errors)
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
}
