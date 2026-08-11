package ai.music.workstation.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
    val autoSaveIntervalMinutes: Long = 5,
    val maxRecentProjects: Int = 10
)
