package ai.music.workstation.cli

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import ai.music.workstation.audio.WAVDecoder
import ai.music.workstation.model.ErrorReporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files

class WAVExporterSimpleTest {

    private val errorReporter = object : ai.music.workstation.model.ErrorReporter {
        override fun report(message: String) {
            println("Report: $message")
        }
        override fun report(message: String, cause: Throwable) {
            println("Report: $message - ${cause.message}")
        }
    }

    @Test
    fun `export and decode should be consistent`() {
        val sampleRate = 48000
        val channels = 2
        // A few frames of data
        val samples = floatArrayOf(
            0.5f, -0.5f,
            0.1f, -0.1f,
            0.8f, -0.8f,
            -0.3f, 0.3f
        )
        val format = AudioFormat(sampleRate, channels, 24, false, false, "WAV")
        val buffer = AudioBuffer(samples, format, samples.size / (channels.toDouble() * sampleRate))

        val tempFile = Files.createTempFile("simple_export", ".wav")
        try {
            val exporter = WAVExporterSimple()
            exporter.export(buffer, tempFile)

            // The decoder expects ai.music.workstation.model.ErrorReporter as well
            val decoder = WAVDecoder(errorReporter)
            val decoded = decoder.decode(tempFile)

            assertEquals(48000, decoded.format.sampleRate)
            assertEquals(2, decoded.format.channels)
            assertEquals(24, decoded.format.bitDepth)
            
            // Check some samples (they should be close despite 24-bit quantization)
            // 24-bit PCM has 2^23 - 1 as max value.
            for (i in samples.indices) {
                assertEquals(samples[i], decoded.samples[i], 0.0001f)
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `resampling should work and interpolate correctly`() {
        // Create 44.1kHz mono buffer
        val samples = floatArrayOf(0.0f, 1.0f, 0.0f, -1.0f)
        val format = AudioFormat(44100, 1, 16, false, false, "WAV")
        val buffer = AudioBuffer(samples, format, 4.0 / 44100.0)

        val tempFile = Files.createTempFile("resample_test", ".wav")
        try {
            val exporter = WAVExporterSimple()
            exporter.export(buffer, tempFile)

            val decoder = WAVDecoder(errorReporter)
            val decoded = decoder.decode(tempFile)

            // Exported should be 48kHz
            assertEquals(48000, decoded.format.sampleRate)
            
            // New length should be roughly 4 * 48000 / 44100 = 4.35 -> 4
            // Ratio is 48000 / 44100 = 1.0884
            // i=0: srcIndex=0, frac=0 -> sample=0
            // i=1: srcIndex=1/1.0884 = 0, frac=0.918 -> 0*0.08 + 1.0*0.918 = 0.918
            assertTrue(decoded.samples.size >= 4)
            assertEquals(0.0f, decoded.samples[0], 0.0001f)
            assertTrue(decoded.samples[1] > 0.8f)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `verify bytes are little-endian`() {
        val buffer = AudioBuffer(floatArrayOf(0f), AudioFormat(48000, 1, 24, false, false, "WAV"), 0.0)
        val tempFile = Files.createTempFile("endian_check", ".wav")
        try {
            WAVExporterSimple().export(buffer, tempFile)
            val bytes = Files.readAllBytes(tempFile)
            
            // RIFF header: 0-3 "RIFF"
            // File size: 4-7
            // WAVE: 8-11 "WAVE"
            // fmt : 12-15 "fmt "
            // fmt size: 16-19
            
            // "fmt " chunk size should be 16 (0x10 0x00 0x00 0x00 in little-endian)
            assertEquals(0x10.toByte(), bytes[16])
            assertEquals(0x00.toByte(), bytes[17])
            assertEquals(0x00.toByte(), bytes[18])
            assertEquals(0x00.toByte(), bytes[19])
            
            // AudioFormat should be 1 (0x01 0x00 in little-endian)
            assertEquals(0x01.toByte(), bytes[20])
            assertEquals(0x00.toByte(), bytes[21])
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
