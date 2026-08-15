package app.melotrail.server.dto

data class ProjectTrackCreateRequest(
    val name: String,
    val type: String = "OTHER",
    val filePath: String = "",
    val gain: Double = 1.0,
    val pan: Double = 0.0,
    val muted: Boolean = false,
    val solo: Boolean = false
)

data class ProjectTrackDTO(
    val id: String,
    val name: String,
    val type: String,
    val filePath: String,
    val gain: Double,
    val pan: Double,
    val muted: Boolean,
    val solo: Boolean
)
