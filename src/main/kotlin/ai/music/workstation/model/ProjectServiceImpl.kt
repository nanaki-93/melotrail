package ai.music.workstation.model

import ai.music.workstation.model.Project
import ai.music.workstation.model.ProjectTrack
import ai.music.workstation.model.TrackType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ProjectServiceImpl(private val projectDir: Path) : ProjectService {
    init {
        Files.createDirectories(projectDir)
    }

    override fun create(title: String, artist: String): Project {
        val id = UUID.randomUUID().toString()
        val project = Project(
            id = id,
            title = title,
            artist = artist,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            path = projectDir.resolve("$id.json").toString()
        )
        save(project)
        return project
    }

    override fun save(project: Project): Result<Unit> {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val filePath = java.nio.file.Paths.get(project.path!!)
            Files.writeString(filePath, kotlinx.serialization.json.Json.encodeToString(Project.serializer(), project))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun load(projectId: String): Result<Project> {
        return try {
            val path = projectDir.resolve("$projectId.json")
            if (!Files.exists(path)) {
                return Result.failure(Exception("Project not found: $projectId"))
            }
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val project = json.decodeFromString(Project.serializer(), Files.readString(path))
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(projectId: String): Result<Unit> {
        return try {
            val path = projectDir.resolve("$projectId.json")
            Files.deleteIfExists(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun list(): List<Project> {
        return try {
            val files = Files.list(projectDir)
                .filter { it.toString().endsWith(".json") }
                .toList()
            
            files.mapNotNull { filePath ->
                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    json.decodeFromString<Project>(Project.serializer(), Files.readString(filePath))
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
