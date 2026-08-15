package app.melotrail.audio

import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class MP3ExporterTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `MP3 export requires library`() = runTest {
        val exporter = MP3Exporter(errorReporter)
        val samples = floatArrayOf(0.5f, -0.5f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 2.0 / 44100)
        val tempFile = java.nio.file.Files.createTempFile("export", ".mp3")

        try {
            val settings = ExportSettings(
                format = ExportFormat.MP3,
                sampleRate = 44100,
                bitDepth = 16
            )

            // Should throw because MP3 library is not available
            try {
                exporter.export(buffer, settings, tempFile) { }
                assertTrue(false, "Expected export to throw")
            } catch (e: Exception) {
                // Expected
            }
        } finally {
            java.nio.file.Files.delete(tempFile)
        }
    }

    @Test
    fun `get supported formats`() {
        val exporter = MP3Exporter(errorReporter)
        assertEquals(setOf(ExportFormat.MP3), exporter.supportedFormats)
    }

    @Test
    fun `get supported bit depths for MP3`() {
        val exporter = MP3Exporter(errorReporter)
        assertEquals(listOf(16), exporter.getSupportedBitDepths(ExportFormat.MP3))
    }

    @Test
    fun `validate MP3 with float bit depth`() {
        val exporter = MP3Exporter(errorReporter)
        val settings = ExportSettings(
            format = ExportFormat.MP3,
            sampleRate = 44100,
            bitDepth = 32,
            float = true
        )

        val errors = exporter.validateSettings(settings)
        assertTrue(errors.size >= 1)
        assertTrue(errors.any { "float" in it.lowercase() || "bit depth" in it.lowercase() })
    }

    @Test
    fun `validate MP3 with unsupported bitrate`() {
        val exporter = MP3Exporter(errorReporter)
        val settings = ExportSettings(
            format = ExportFormat.MP3,
            sampleRate = 44100,
            bitDepth = 16,
            mp3Bitrate = 256
        )

        // This should pass because 256 is a valid bitrate
        val errors = exporter.validateSettings(settings)
        assertTrue(errors.isEmpty())
    }
}
