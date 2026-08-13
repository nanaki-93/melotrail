package ai.music.workstation.arrangement

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-memory local arranger project. ProjectStore owns the v1/v2 JSON format
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
    val renderFormat: RenderFormat? = null
) {
    fun validate(projectRoot: Path): ProjectValidationResult =
        ProjectValidator.validate(this, projectRoot)

    fun requireValid(projectRoot: Path) {
        val validation = validate(projectRoot)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    /** Boundary for MIDI-first stages introduced after the v1 source-audio format. */
    fun requireCleanMidi(projectRoot: Path): List<Path> {
        require(version == CURRENT_VERSION) {
            "Project uses legacy v1 source audio. Prepare clean MIDI for every part before running MIDI-first commands."
        }
        requireValid(projectRoot)
        return parts.map { part -> projectRoot.resolve(requireNotNull(part.midi).clean).normalize() }
    }

    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class Part(
    val id: String,
    /** Source file for v1 and v2 projects. It is always relative to project.json. */
    val file: String,
    val role: String = "",
    val analysis: PartAnalysisReference? = null,
    val midi: MidiReferences? = null
)

@Serializable
data class RenderFormat(
    val sampleRate: Int = 44_100,
    val channels: Int = 2,
    val bitDepth: Int = 24
)

@Serializable
data class MidiReferences(
    val raw: String? = null,
    val clean: String
)

/** Reference to the analysis JSON generated for a part, when available. */
@Serializable
data class PartAnalysisReference(
    val file: String
)

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

        if (project.version !in setOf(1, Project.CURRENT_VERSION)) {
            errors += "Unsupported project version: ${project.version}"
        }
        if (project.version == Project.CURRENT_VERSION) {
            val format = project.renderFormat
            if (format == null) {
                errors += "Version 2 projects require an explicit render format"
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
            if (project.version == Project.CURRENT_VERSION) {
                val midi = part.midi
                if (midi == null) {
                    errors += "Part '${part.id}' requires a clean MIDI reference; import it with MIDI cleanup first"
                } else {
                    midi.raw?.let { validateFileReference(root, it, "Part '${part.id}' raw MIDI", errors) }
                    validateFileReference(root, midi.clean, "Part '${part.id}' clean MIDI", errors)
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
