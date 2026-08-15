package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Versioned project-file boundary. V1 remains readable without changing its
 * metadata or files; only a fully prepared project is written as v2.
 */
object ProjectStore {
    const val FILE_NAME = "project.json"

    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun create(root: Path, name: String, renderFormat: RenderFormat): Project {
        val project = Project(version = Project.CURRENT_VERSION, name = name, renderFormat = renderFormat)
        write(root, project)
        return project
    }

    fun read(root: Path): Project {
        val text = Files.readString(root.resolve(FILE_NAME), StandardCharsets.UTF_8)
        val element = json.parseToJsonElement(text).jsonObject
        return when (element["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1) {
            1 -> json.decodeFromString<ProjectV1Dto>(text).toProject()
            2 -> json.decodeFromString<ProjectV2Dto>(text).toProject()
            else -> throw IllegalArgumentException("Unsupported project version: ${element["version"]?.jsonPrimitive?.content}")
        }
    }

    fun write(root: Path, project: Project) {
        project.requireValid(root)
        val serialized = when (project.version) {
            1 -> json.encodeToString(project.toV1Dto())
            2 -> json.encodeToString(project.toV2Dto())
            else -> throw IllegalArgumentException("Unsupported project version: ${project.version}")
        }
        atomicWrite(root.resolve(FILE_NAME), serialized)
    }

    /** Upgrades metadata only. Source files remain exactly where v1 stored them. */
    fun upgrade(root: Path, project: Project, parts: List<Part>): Project {
        require(project.version == 1) { "Only version 1 projects can be upgraded" }
        val upgraded = project.copy(
            version = Project.CURRENT_VERSION,
            parts = parts,
            renderFormat = project.renderFormat ?: RenderFormat()
        )
        upgraded.requireValid(root)
        write(root, upgraded)
        return upgraded
    }

    private fun atomicWrite(path: Path, text: String) {
        Files.createDirectories(checkNotNull(path.parent))
        val temporary = path.resolveSibling(".${path.fileName}.tmp")
        Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Serializable private data class ProjectV1Dto(val version: Int = 1, val name: String, val parts: List<PartV1Dto> = emptyList(), val structure: List<String> = emptyList())
    @Serializable private data class PartV1Dto(val id: String, val file: String, val role: String = "", val analysis: PartAnalysisReference? = null)
    @Serializable private data class ProjectV2Dto(val version: Int = 2, val name: String, val renderFormat: RenderFormat, val parts: List<PartV2Dto> = emptyList(), val structure: List<String> = emptyList())
    @Serializable private data class PartV2Dto(val id: String, val role: String = "", val sourceFile: String, val midi: MidiReferences, val analysis: PartAnalysisReference? = null)

    private fun ProjectV1Dto.toProject() = Project(1, name, parts.map { Part(it.id, it.file, it.role, it.analysis) }, structure)
    private fun ProjectV2Dto.toProject() = Project(2, name, parts.map { Part(it.id, it.sourceFile, it.role, it.analysis, it.midi) }, structure, renderFormat)
    private fun Project.toV1Dto() = ProjectV1Dto(name = name, parts = parts.map { PartV1Dto(it.id, it.file, it.role, it.analysis) }, structure = structure)
    private fun Project.toV2Dto() = ProjectV2Dto(name = name, renderFormat = requireNotNull(renderFormat), parts = parts.map { PartV2Dto(it.id, it.role, it.file, requireNotNull(it.midi), it.analysis) }, structure = structure)
}
