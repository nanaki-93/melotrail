package ai.music.workstation.audio

import ai.music.workstation.model.ErrorReporter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class WAVExporterTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `export mono 16-bit WAV`() = runBlocking {
        val samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 4.0 / 44100)

        val tempFile = java.nio.file.Files.createTempFile("export", ".wav")
        try {
            val exporter = WAVExporter(errorReporter)
            val settings = ExportSettings(
                format = ExportFormat.WAV,
                sampleRate = 44100,
                bitDepth = 16
            )

            val result = exporter.export(buffer, settings, tempFile) { }

            assertTrue(java.nio.file.Files.exists(result))
            // Verify it's a valid WAV file by checking RIFF header
            val header = java.nio.file.Files.readAllBytes(result).sliceArray(0..3)
            assertArrayEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46), header) // "RIFF"
        } finally {
            java.nio.file.Files.delete(tempFile)
        }
    }

    @Test
    fun `export stereo 24-bit WAV`() = runBlocking {
        val samples = floatArrayOf(0.6f, 0.4f, -0.6f, -0.4f)
        val format = AudioFormat(48000, 2, 24, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 2.0 / 48000)

        val tempFile = java.nio.file.Files.createTempFile("export", ".wav")
        try {
            val exporter = WAVExporter(errorReporter)
            val settings = ExportSettings(
                format = ExportFormat.WAV,
                sampleRate = 48000,
                bitDepth = 24
            )

            val result = exporter.export(buffer, settings, tempFile) { }

            assertTrue(java.nio.file.Files.exists(result))
            val header = java.nio.file.Files.readAllBytes(result).sliceArray(0..3)
            assertArrayEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46), header)
        } finally {
            java.nio.file.Files.delete(tempFile)
        }
    }

    @Test
    fun `export 32-bit float WAV`() = runBlocking {
        val samples = floatArrayOf(0.9f, -0.9f, 0.0f)
        val format = AudioFormat(44100, 1, 32, true, false, "PCM_FLOAT")
        val buffer = AudioBuffer(samples, format, 3.0 / 44100)

        val tempFile = java.nio.file.Files.createTempFile("export", ".wav")
        try {
            val exporter = WAVExporter(errorReporter)
            val settings = ExportSettings(
                format = ExportFormat.WAV,
                sampleRate = 44100,
                bitDepth = 32,
                float = true
            )

            val result = exporter.export(buffer, settings, tempFile) { }

            assertTrue(java.nio.file.Files.exists(result))
            val header = java.nio.file.Files.readAllBytes(result).sliceArray(0..3)
            assertArrayEquals(byteArrayOf(0x52, 0x49, 0x46, 0x46), header)
        } finally {
            java.nio.file.Files.delete(tempFile)
        }
    }

    @Test
    fun `validate settings with unsupported sample rate`() {
        val exporter = WAVExporter(errorReporter)
        val settings = ExportSettings(
            format = ExportFormat.WAV,
            sampleRate = 22050,
            bitDepth = 16
        )

        val errors = exporter.validateSettings(settings)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { "sample rate" in it.lowercase() })
    }

    @Test
    fun `validate settings with unsupported bit depth`() {
        val exporter = WAVExporter(errorReporter)
        val settings = ExportSettings(
            format = ExportFormat.WAV,
            sampleRate = 44100,
            bitDepth = 8
        )

        val errors = exporter.validateSettings(settings)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { "bit depth" in it.lowercase() })
    }

    @Test
    fun `validate valid settings`() {
        val exporter = WAVExporter(errorReporter)
        val settings = ExportSettings(
            format = ExportFormat.WAV,
            sampleRate = 44100,
            bitDepth = 16
        )

        val errors = exporter.validateSettings(settings)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `get supported sample rates`() {
        val exporter = WAVExporter(errorReporter)
        val rates = exporter.getSupportedSampleRates()
        assertEquals(listOf(44100, 48000), rates)
    }

    @Test
    fun `get supported bit depths for WAV`() {
        val exporter = WAVExporter(errorReporter)
        val depths = exporter.getSupportedBitDepths(ExportFormat.WAV)
        assertEquals(listOf(16, 24, 32), depths)
    }
}
