package ai.music.workstation.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    @SerialName("id")
    val id: String = "",
    @SerialName("title")
    var title: String = "",
    @SerialName("artist")
    var artist: String = "",
    @SerialName("tracks")
    var tracks: List<ProjectTrack> = emptyList(),
    @SerialName("bpm")
    var bpm: Double = 0.0,
    @SerialName("key")
    var key: String = "",
    @SerialName("createdAt")
    val createdAt: Instant = Clock.System.now(),
    @SerialName("updatedAt")
    var updatedAt: Instant = Clock.System.now(),
    @SerialName("path")
    val path: String? = null
) {
    fun addTrack(track: ProjectTrack): Project = copy(tracks = tracks + track)
    fun removeTrack(trackId: String): Project = copy(tracks = tracks.filter { it.id != trackId })
    fun updateTrack(trackId: String, update: (ProjectTrack) -> ProjectTrack): Project = copy(
        tracks = tracks.map { if (it.id == trackId) update(it) else it }
    )
}

interface ProjectService {
    fun create(title: String, artist: String): Project
    fun save(project: Project): Result<Unit>
    fun load(projectId: String): Result<Project>
    fun delete(projectId: String): Result<Unit>
    fun list(): List<Project>
}
