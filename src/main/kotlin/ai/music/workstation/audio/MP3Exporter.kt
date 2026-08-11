package ai.music.workstation.audio

import ai.music.workstation.errors.AppError
import ai.music.workstation.errors.AppErrorException
import ai.music.workstation.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class MP3Exporter(
    private val errorReporter: ErrorReporter
) : AudioExporter {
    override val supportedFormats: Set<ExportFormat> = setOf(ExportFormat.MP3)

    override suspend fun export(
        buffer: AudioBuffer,
        settings: ExportSettings,
        outputPath: Path,
        progress: (Double) -> Unit
    ): Path = withContext(Dispatchers.IO) {
        // MP3 encoding requires LAME or jlayer
        // This is a placeholder implementation
        errorReporter.report("MP3 export requires LAME library")
        throw AppErrorException(AppError.AudioExportError(
            outputPath,
            "MP3",
            "MP3 encoding requires LAME library"
        ))
    }
}
