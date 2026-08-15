package app.melotrail.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    WAV, FLAC, MP3, OGG, AIFF, BWF
}
