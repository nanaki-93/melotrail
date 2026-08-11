package ai.music.workstation.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectStatus {
    DRAFT, IN_PROGRESS, FINALIZED
}

@Serializable
enum class TrackType {
    DRUMS, BASS, RHODES, GUITAR, PADS, STRINGS, AMBIENT, VOCALS, OTHER;

    companion object {
        fun fromName(name: String): TrackType {
            return values().find { it.name.equals(name, ignoreCase = true) }
                ?: OTHER
        }
    }
}
