package ai.music.workstation.audio

import ai.music.workstation.errors.AppError
import ai.music.workstation.errors.AppErrorException
import ai.music.workstation.model.ErrorReporter
import java.nio.file.Files
import java.nio.file.Path

class FLACDecoder(
    private val errorReporter: ErrorReporter
) : BaseDecoder(setOf("flac", "fla")) {

    override fun decode(path: Path): AudioBuffer {
        if (!Files.exists(path)) {
            errorReporter.report("FLAC file not found: $path")
            throw AppErrorException(AppError.FileNotFoundError(path))
        }

        // FLAC decoding requires flac-jna or similar library
        // This is a placeholder implementation
        // In production, use flac-jna: https://github.com/bertramn/flac-jna
        errorReporter.report("FLAC decoding requires flac-jna library")
        throw AppErrorException(AppError.AudioDecodeError(path, "FLAC"))
    }
}
