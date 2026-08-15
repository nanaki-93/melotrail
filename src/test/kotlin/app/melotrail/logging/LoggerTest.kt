package app.melotrail.logging

import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LoggerTest {

    @Test
    fun `log levels have correct integer values`() {
        assertEquals(0, LogLevel.DEBUG.toInt())
        assertEquals(1, LogLevel.INFO.toInt())
        assertEquals(2, LogLevel.WARNING.toInt())
        assertEquals(3, LogLevel.ERROR.toInt())
    }

    @Test
    fun `fromInt returns correct level`() {
        assertEquals(LogLevel.DEBUG, LogLevel.fromInt(0))
        assertEquals(LogLevel.INFO, LogLevel.fromInt(1))
        assertEquals(LogLevel.WARNING, LogLevel.fromInt(2))
        assertEquals(LogLevel.ERROR, LogLevel.fromInt(3))
    }

    @Test
    fun `default logger handles messages`() {
        val logger = DefaultLogger()
        // Should not throw
        logger.info("Test", "test message")
        logger.warning("Test", "warning message")
        logger.error("Test", "error message")
        logger.debug("Test", "debug message")
    }

    @Test
    fun `default logger handles exception`() {
        val logger = DefaultLogger()
        // Should not throw
        logger.error("Test", "error with exception", RuntimeException("test"))
    }

    @Test
    fun `log entry toString format`() {
        val entry = LogEntry(
            timestamp = Clock.System.now(),
            level = LogLevel.INFO,
            component = "Test",
            message = "Test message"
        )
        val str = entry.toString()
        assertTrue(str.contains("[INFO]"))
        assertTrue(str.contains("[Test]"))
        assertTrue(str.contains("Test message"))
    }

    @Test
    fun `log entry with exception`() {
        val entry = LogEntry(
            timestamp = Clock.System.now(),
            level = LogLevel.ERROR,
            component = "Test",
            message = "Error occurred",
            exception = "java.lang.RuntimeException: test"
        )
        val str = entry.toString()
        assertTrue(str.contains("Error occurred"))
        assertTrue(str.contains("Exception:"))
        assertTrue(str.contains("test"))
    }
}
