package app.melotrail.audio

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class ExportSettings(
    val format: ExportFormat,
    val sampleRate: Int,
    val bitDepth: Int,
    val float: Boolean = false,
    val mp3Bitrate: Int = 320,
    val mp3Quality: Int = 2
)

enum class ExportFormat {
    WAV, FLAC, MP3
}

interface AudioExporter {
    val supportedFormats: Set<ExportFormat>

    suspend fun export(
        buffer: AudioBuffer,
        settings: ExportSettings,
        outputPath: Path,
        progress: (Double) -> Unit
    ): Path

    fun getSupportedSampleRates(): List<Int> = listOf(44100, 48000)

    fun getSupportedBitDepths(format: ExportFormat): List<Int> {
        return when (format) {
            ExportFormat.WAV -> listOf(16, 24, 32)
            ExportFormat.FLAC -> listOf(16, 24, 32)
            ExportFormat.MP3 -> listOf(16)
        }
    }

    fun validateSettings(settings: ExportSettings): List<String> {
        val errors = mutableListOf<String>()
        if (settings.sampleRate !in getSupportedSampleRates()) {
            errors.add("Unsupported sample rate: ${settings.sampleRate}")
        }
        if (settings.bitDepth !in getSupportedBitDepths(settings.format)) {
            errors.add("Unsupported bit depth for ${settings.format}: ${settings.bitDepth}")
        }
        if (settings.format == ExportFormat.MP3 && settings.float) {
            errors.add("MP3 does not support float bit depth")
        }
        if (settings.format == ExportFormat.MP3 && settings.mp3Bitrate !in listOf(128, 192, 256, 320)) {
            errors.add("MP3 bitrate must be 128, 192, 256, or 320 kbps")
        }
        return errors
    }
}
