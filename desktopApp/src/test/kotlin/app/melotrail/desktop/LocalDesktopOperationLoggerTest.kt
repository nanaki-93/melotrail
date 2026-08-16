package app.melotrail.desktop

import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class LocalDesktopOperationLoggerTest {
    @Test
    fun `diagnostics classify artifacts without recording absolute paths or file names`() {
        val captured = mutableListOf<String>()
        val logger = Logger.getLogger("desktop-operation-test-${System.nanoTime()}").apply {
            useParentHandlers = false
            level = Level.INFO
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) { captured += record.message }
                override fun flush() = Unit
                override fun close() = Unit
            })
        }

        LocalDesktopOperationLogger(logger).event(
            operation = "open project",
            stage = "opened",
            artifact = Path.of("/Users/artist/Private Songs/secret-title.wav")
        )

        val message = captured.single()
        assertContains(message, "operation=open_project")
        assertContains(message, "artifact=\"wav\"")
        assertFalse(message.contains("/Users/artist"))
        assertFalse(message.contains("secret-title"))
    }
}
