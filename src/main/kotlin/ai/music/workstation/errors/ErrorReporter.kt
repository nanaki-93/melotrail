package ai.music.workstation.errors

import ai.music.workstation.logging.Logger

class ErrorReporter(private val logger: Logger) {
    fun report(error: Throwable, context: String = "") {
        logger.error("ErrorReporter", "Error in $context: ${error.message}", error)
    }

    fun report(message: String, context: String = "") {
        logger.error("ErrorReporter", "Error in $context: $message")
    }
}
