package app.melotrail.errors

import app.melotrail.logging.DefaultLogger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ErrorReporterTest {

    private val logger = DefaultLogger()
    private val reporter = ErrorReporter(logger)

    @Test
    fun `report message`() {
        // Should not throw
        reporter.report("Test error message")
    }

    @Test
    fun `report exception`() {
        // Should not throw
        reporter.report(RuntimeException("Test exception"), "test context")
    }

    @Test
    fun `report with context`() {
        // Should not throw
        reporter.report("Test error", "audio import")
    }
}
