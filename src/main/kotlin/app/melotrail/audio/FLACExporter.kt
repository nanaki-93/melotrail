package app.melotrail.audio

import app.melotrail.errors.AppError
import app.melotrail.errors.AppErrorException
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class FLACExporter(
    private val errorReporter: ErrorReporter
) : AudioExporter {
    override val supportedFormats: Set<ExportFormat> = setOf(ExportFormat.FLAC)

    override suspend fun export(
        buffer: AudioBuffer,
        settings: ExportSettings,
        outputPath: Path,
        progress: (Double) -> Unit
    ): Path = withContext(Dispatchers.IO) {
        // FLAC encoding requires flac-jna or similar library
        // This is a placeholder implementation
        errorReporter.report("FLAC export requires flac-jna library")
        throw AppErrorException(AppError.AudioExportError(
            outputPath,
            "FLAC",
            "FLAC encoding requires flac-jna library"
        ))
    }
}
