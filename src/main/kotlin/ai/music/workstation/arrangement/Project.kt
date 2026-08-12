package ai.music.workstation.arrangement

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Version 1 of the local arranger project format stored in project.json.
 *
 * This is intentionally separate from the existing web application's
 * track-based Project model. Its file references are always relative to the
 * directory containing project.json.
 */
@Serializable
data class Project(
    val version: Int = CURRENT_VERSION,
    val name: String,
    val parts: List<Part> = emptyList(),
    val structure: List<String> = emptyList()
) {
    fun validate(projectRoot: Path): ProjectValidationResult =
        ProjectValidator.validate(this, projectRoot)

    fun requireValid(projectRoot: Path) {
        val validation = validate(projectRoot)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class Part(
    val id: String,
    val file: String,
    val role: String = "",
    val analysis: PartAnalysisReference? = null
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

        if (project.version != Project.CURRENT_VERSION) {
            errors += "Unsupported project version: ${project.version}"
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
