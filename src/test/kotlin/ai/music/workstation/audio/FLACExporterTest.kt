package ai.music.workstation.audio

import ai.music.workstation.model.ErrorReporter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

class FLACExporterTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `FLAC export requires library`() = runTest {
        val exporter = FLACExporter(errorReporter)
        val samples = floatArrayOf(0.5f, -0.5f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 2.0 / 44100)
        val tempFile = java.nio.file.Files.createTempFile("export", ".flac")

        try {
            val settings = ExportSettings(
                format = ExportFormat.FLAC,
                sampleRate = 44100,
                bitDepth = 16
            )

            // Should throw because FLAC library is not available
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
        val exporter = FLACExporter(errorReporter)
        assertEquals(setOf(ExportFormat.FLAC), exporter.supportedFormats)
    }

    @Test
    fun `get supported bit depths for FLAC`() {
        val exporter = FLACExporter(errorReporter)
        assertEquals(listOf(16, 24, 32), exporter.getSupportedBitDepths(ExportFormat.FLAC))
    }
}
