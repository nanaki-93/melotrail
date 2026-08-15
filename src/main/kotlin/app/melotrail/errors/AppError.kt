package app.melotrail.errors

import java.nio.file.Path

sealed class AppError(
    open val category: ErrorCategory,
    open val severity: ErrorSeverity,
    open val userMessage: String,
    open val technicalMessage: String
) {
    data class AudioDecodeError(
        val filePath: Path,
        val format: String
    ) : AppError(
        category = ErrorCategory.AUDIO,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Could not decode audio file: $filePath",
        technicalMessage = "Format: $format, File: $filePath"
    )

    data class AudioPlaybackError(
        val message: String
    ) : AppError(
        category = ErrorCategory.AUDIO,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Audio playback error: $message",
        technicalMessage = message
    )

    data class AudioExportError(
        val outputPath: Path,
        val format: String,
        val reason: String
    ) : AppError(
        category = ErrorCategory.AUDIO,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Could not export audio to $outputPath: $reason",
        technicalMessage = "Format: $format, Reason: $reason"
    )

    data class WorkerError(
        val command: String,
        val exitCode: Int?,
        val stderr: String
    ) : AppError(
        category = ErrorCategory.WORKER,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Worker failed to execute: $command",
        technicalMessage = "Exit code: $exitCode, stderr: $stderr"
    )

    data class FileNotFoundError(
        val path: Path
    ) : AppError(
        category = ErrorCategory.FILE,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "File not found: $path",
        technicalMessage = "Path: $path"
    )

    data class FileCopyError(
        val source: Path,
        val target: Path,
        val reason: String
    ) : AppError(
        category = ErrorCategory.FILE,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Could not copy file from $source to $target: $reason",
        technicalMessage = "Source: $source, Target: $target, Reason: $reason"
    )

    data class ModelError(
        val modelName: String,
        val reason: String
    ) : AppError(
        category = ErrorCategory.MODEL,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Model error: $modelName — $reason",
        technicalMessage = "Model: $modelName, Reason: $reason"
    )

    data class ConfigError(
        val field: String,
        val message: String
    ) : AppError(
        category = ErrorCategory.CONFIG,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Configuration error: $message",
        technicalMessage = "Field: $field, Message: $message"
    )

    data class NetworkError(
        val url: String,
        val reason: String
    ) : AppError(
        category = ErrorCategory.NETWORK,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "Network error: $reason",
        technicalMessage = "URL: $url, Reason: $reason"
    )

    data class UserValidationError(
        val field: String,
        val message: String
    ) : AppError(
        category = ErrorCategory.USER,
        severity = ErrorSeverity.RECOVERABLE,
        userMessage = message,
        technicalMessage = "Field: $field, Message: $message"
    )

    data class UnsupportedFormatError(
        val format: String,
        val filePath: Path
    ) : AppError(
        category = ErrorCategory.AUDIO,
        severity = ErrorSeverity.RECOVERABLE,
        userMessage = "Unsupported audio format: $format",
        technicalMessage = "Format: $format, File: $filePath"
    )

    data class UnknownError(
        override val category: ErrorCategory,
        override val severity: ErrorSeverity,
        override val userMessage: String,
        override val technicalMessage: String
    ) : AppError(
        category = category,
        severity = severity,
        userMessage = userMessage,
        technicalMessage = technicalMessage
    )

    data class CorruptedFileError(
        val filePath: Path,
        val reason: String
    ) : AppError(
        category = ErrorCategory.FILE,
        severity = ErrorSeverity.SERIOUS,
        userMessage = "File appears corrupted: $filePath",
        technicalMessage = "File: $filePath, Reason: $reason"
    )

    companion object {
        fun fromException(exception: Throwable): AppError {
            return when (exception) {
                is AppError -> exception
                is IllegalArgumentException -> UserValidationError(
                    field = "unknown",
                    message = exception.message ?: "Invalid input"
                )
                is java.io.IOException -> FileNotFoundError(
                    Path.of(exception.message ?: "unknown")
                )
                else -> UnknownError(
                    category = ErrorCategory.UNKNOWN,
                    severity = ErrorSeverity.SERIOUS,
                    userMessage = "An unexpected error occurred",
                    technicalMessage = exception.stackTraceToString()
                )
            }
        }
    }
}
