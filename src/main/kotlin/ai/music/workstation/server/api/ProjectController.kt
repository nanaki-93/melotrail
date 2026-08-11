package ai.music.workstation.server.api

import ai.music.workstation.model.ProjectService
import ai.music.workstation.model.ProjectTrack
import ai.music.workstation.model.TrackType
import ai.music.workstation.server.dto.*
import ai.music.workstation.server.service.toDTO
import kotlinx.datetime.Clock
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/projects")
class ProjectController(private val service: ProjectService) {

    @GetMapping
    fun list() = service.list().map { it.toDTO() }

    @PostMapping
    fun create(@RequestBody request: ProjectCreateRequest): ResponseEntity<ProjectResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(request.title, request.artist).toDTO())

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> =
        service.load(id).fold(
            onSuccess = { ResponseEntity.ok<Any>(it.toDTO()) },
            onFailure = { ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to (it.message ?: "Not found"))) }
        )

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody request: ProjectUpdateRequest): ResponseEntity<Any> =
        service.load(id).fold(
            onSuccess = { existing ->
                val updated = existing.copy(
                    title = request.title ?: existing.title,
                    artist = request.artist ?: existing.artist,
                    updatedAt = Clock.System.now()
                )
                service.save(updated).fold(
                    onSuccess = { ResponseEntity.ok<Any>(updated.toDTO()) },
                    onFailure = { ResponseEntity.internalServerError().body<Any>(mapOf("error" to (it.message ?: "Save failed"))) }
                )
            },
            onFailure = { ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to (it.message ?: "Not found"))) }
        )

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Any> =
        service.delete(id).fold(
            onSuccess = { ResponseEntity.noContent().build<Any>() },
            onFailure = { ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to (it.message ?: "Not found"))) }
        )

    @GetMapping("/{id}/tracks")
    fun tracks(@PathVariable id: String): ResponseEntity<Any> =
        service.load(id).fold(
            onSuccess = { ResponseEntity.ok<Any>(it.tracks.map { track -> track.toDTO() }) },
            onFailure = { ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Project not found")) }
        )

    @PostMapping("/{id}/tracks")
    fun createTrack(@PathVariable id: String, @RequestBody request: ProjectTrackCreateRequest): ResponseEntity<Any> {
        val project = service.load(id).getOrElse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Project not found"))
        }
        val track = ProjectTrack(
            id = UUID.randomUUID().toString(),
            name = request.name,
            type = TrackType.fromName(request.type),
            filePath = request.filePath,
            gain = request.gain, pan = request.pan, muted = request.muted, solo = request.solo
        )
        return service.save(project.copy(tracks = project.tracks + track)).fold(
            onSuccess = { ResponseEntity.status(HttpStatus.CREATED).body<Any>(track.toDTO()) },
            onFailure = { ResponseEntity.internalServerError().body<Any>(mapOf("error" to (it.message ?: "Failed to create track"))) }
        )
    }

    @DeleteMapping("/{id}/tracks/{trackId}")
    fun deleteTrack(@PathVariable id: String, @PathVariable trackId: String): ResponseEntity<Any> {
        val project = service.load(id).getOrElse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Project not found"))
        }
        return service.save(project.copy(tracks = project.tracks.filterNot { it.id == trackId })).fold(
            onSuccess = { ResponseEntity.noContent().build<Any>() },
            onFailure = { ResponseEntity.internalServerError().body<Any>(mapOf("error" to (it.message ?: "Failed to delete track"))) }
        )
    }

    @GetMapping("/{id}/analysis")
    fun analysis(@PathVariable id: String): ResponseEntity<Any> {
        val project = service.load(id).getOrElse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Project not found"))
        }
        return ResponseEntity.ok<Any>(mapOf("bpm" to project.bpm, "key" to project.key, "tracks" to project.tracks.size))
    }

    @GetMapping("/{id}/provenance")
    fun provenance(@PathVariable id: String): ResponseEntity<Any> {
        service.load(id).getOrElse {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Project not found"))
        }
        return ResponseEntity.ok<Any>(emptyList<Map<String, String>>())
    }
}
