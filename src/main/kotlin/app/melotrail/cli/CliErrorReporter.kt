package app.melotrail.cli

import app.melotrail.logging.Logger
import app.melotrail.model.ErrorReporter

/**
 * Simple CLI ErrorReporter that delegates to the main Logger.
 */
class CliErrorReporter(
    private val logger: Logger
) : ErrorReporter {

    override fun report(message: String) {
        logger.error("CliErrorReporter", message)
    }

    override fun report(message: String, cause: Throwable) {
        logger.error("CliErrorReporter", "$message: ${cause.message}", cause)
    }
}
