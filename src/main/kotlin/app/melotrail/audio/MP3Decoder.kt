package app.melotrail.audio

import app.melotrail.errors.AppError
import app.melotrail.errors.AppErrorException
import app.melotrail.errors.ErrorCategory
import app.melotrail.model.ErrorReporter
import java.nio.file.Files
import java.nio.file.Path

class MP3Decoder(
    private val errorReporter: ErrorReporter
) : BaseDecoder(setOf("mp3", "mpeg")) {

    override fun decode(path: Path): AudioBuffer {
        if (!Files.exists(path)) {
            errorReporter.report("MP3 file not found: $path")
            throw AppErrorException(AppError.FileNotFoundError(path))
        }

        // MP3 decoding requires jlayer or similar library
        // This is a placeholder implementation
        // In production, use jlayer: https://github.com/eighthave/jlayer
        errorReporter.report("MP3 decoding requires jlayer library")
        throw AppErrorException(AppError.AudioDecodeError(path, "MP3"))
    }
}
