package app.melotrail.server.dto

data class WaveformDTO(
    val samples: List<Float>,
    val sampleRate: Int,
    val duration: Double,
    val channels: Int
)

data class UploadResult(
    val projectId: String,
    val trackId: String,
    val fileName: String,
    val filePath: String
)

data class AudioExportRequest(
    val projectId: String,
    val trackId: String,
    val format: String,
    val sampleRate: Int = 48000,
    val bitDepth: Int = 24
)
