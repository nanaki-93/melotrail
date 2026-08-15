package app.melotrail.server.service

import app.melotrail.model.Project
import app.melotrail.model.ProjectService
import app.melotrail.model.ProjectTrack
import app.melotrail.server.dto.ProjectResponse
import app.melotrail.server.dto.ProjectTrackDTO
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

class ProjectServiceAdapter(
    private val storagePath: Path = Paths.get("data/projects")
) : ProjectService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    init {
        Files.createDirectories(storagePath)
    }

    override fun create(title: String, artist: String): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            path = storagePath.resolve("${UUID.randomUUID()}.json").toString()
        )
        save(project)
        return project
    }

    override fun save(project: Project): Result<Unit> = try {
        val path = Paths.get(project.path ?: storagePath.resolve("${project.id}.json").toString())
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(Project.serializer(), project))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun load(projectId: String): Result<Project> = try {
        val path = storagePath.resolve("$projectId.json")
        if (!Files.exists(path)) {
            findProjectById(projectId)?.let { return Result.success(it) }
            Result.failure(Exception("Project not found: $projectId"))
        } else {
            Result.success(json.decodeFromString(Project.serializer(), Files.readString(path)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun delete(projectId: String): Result<Unit> = try {
        val path = storagePath.resolve("$projectId.json")
        if (Files.exists(path)) Files.delete(path)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun list(): List<Project> = try {
        Files.newDirectoryStream(storagePath, "*.json").use { stream ->
            stream.mapNotNull {
                try {
                    json.decodeFromString(Project.serializer(), Files.readString(it))
                } catch (_: Exception) {
                    null
                }
            }.toList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun findProjectById(id: String): Project? = list().find { it.id == id }
}

fun Project.toDTO() = ProjectResponse(
    id = id, title = title, artist = artist, bpm = bpm, key = key,
    tracks = tracks.map { it.toDTO() },
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString()
)

fun ProjectTrack.toDTO() = ProjectTrackDTO(
    id = id, name = name, type = type.name, filePath = filePath ?: "",
    gain = gain, pan = pan, muted = muted, solo = solo
)
