package app.melotrail.audio

import app.melotrail.model.ErrorReporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class AudioImportServiceTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `wav decoder supports wav format`() {
        val decoder = WAVDecoder(errorReporter)
        assertEquals(setOf("wav", "wave"), decoder.supportedFormats)
    }

    @Test
    fun `validate non-existent file`() {
        val decoder = WAVDecoder(errorReporter)
        
        val result = try {
            decoder.decode(Path.of("/nonexistent/file.wav"))
            null
        } catch (e: Exception) {
            "error"
        }
        
        assertTrue(result == "error")
    }

    @Test
    fun `create temp wav file`() {
        val tempFile = Files.createTempFile("test", ".wav")
        try {
            assertTrue(Files.exists(tempFile))
            assertEquals("wav", tempFile.fileName.toString().substringAfterLast("."))
        } finally {
            Files.delete(tempFile)
        }
    }
}
