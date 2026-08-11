package ai.music.workstation.server.dto

data class ProjectCreateRequest(
    val title: String,
    val artist: String = ""
)

data class ProjectUpdateRequest(
    val title: String? = null,
    val artist: String? = null
)

data class ProjectResponse(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Double,
    val key: String,
    val tracks: List<ProjectTrackDTO>,
    val createdAt: String,
    val updatedAt: String
)
