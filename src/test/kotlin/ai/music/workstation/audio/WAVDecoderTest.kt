package ai.music.workstation.audio

import ai.music.workstation.model.ErrorReporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class WAVDecoderTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `decode mono 16-bit WAV`() {
        val tempFile = createTestWav(
            channels = 1,
            sampleRate = 44100,
            bitDepth = 16,
            samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f, 0.0f)
        )

        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)

            assertEquals(5, buffer.length)
            assertEquals(44100, buffer.format.sampleRate)
            assertEquals(1, buffer.format.channels)
            assertEquals(16, buffer.format.bitDepth)
            assertEquals(5.0 / 44100, buffer.duration, 0.001)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `decode stereo 16-bit WAV`() {
        val tempFile = createTestWav(
            channels = 2,
            sampleRate = 48000,
            bitDepth = 16,
            samples = floatArrayOf(0.6f, 0.4f, -0.6f, -0.4f)
        )

        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)

            assertEquals(2, buffer.length)
            assertEquals(48000, buffer.format.sampleRate)
            assertEquals(2, buffer.format.channels)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `decode 8-bit WAV`() {
        val tempFile = createTestWav(
            channels = 1,
            sampleRate = 22050,
            bitDepth = 8,
            samples = floatArrayOf(0f, 0.5f, -0.5f)
        )

        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)

            assertEquals(3, buffer.length)
            assertEquals(22050, buffer.format.sampleRate)
            assertEquals(8, buffer.format.bitDepth)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `decode 24-bit WAV`() {
        val tempFile = createTestWav(
            channels = 1,
            sampleRate = 44100,
            bitDepth = 24,
            samples = floatArrayOf(0.75f, -0.75f, 0.5f)
        )

        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)

            assertEquals(3, buffer.length)
            assertEquals(44100, buffer.format.sampleRate)
            assertEquals(24, buffer.format.bitDepth)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `decode 32-bit float WAV`() {
        val tempFile = createTestWav(
            channels = 1,
            sampleRate = 96000,
            bitDepth = 32,
            samples = floatArrayOf(0.9f, -0.9f, 0.0f)
        )

        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)

            assertEquals(3, buffer.length)
            assertEquals(96000, buffer.format.sampleRate)
            assertEquals(32, buffer.format.bitDepth)
            assertTrue(buffer.format.isFloat)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `throws error for non-existent file`() {
        val decoder = WAVDecoder(errorReporter)
        assertThrows(Exception::class.java) {
            decoder.decode(Path.of("/nonexistent/file.wav"))
        }
    }

    @Test
    fun `throws error for file too small`() {
        val tempFile = Files.createTempFile("test", ".wav")
        Files.write(tempFile, byteArrayOf(0x52, 0x49, 0x46, 0x46))

        try {
            val decoder = WAVDecoder(errorReporter)
            assertThrows(Exception::class.java) {
                decoder.decode(tempFile)
            }
        } finally {
            Files.delete(tempFile)
        }
    }

    private fun createTestWav(
        channels: Int,
        sampleRate: Int,
        bitDepth: Int,
        samples: FloatArray
    ): Path {
        val tempFile = Files.createTempFile("test", ".wav")
        val bytesPerSample = bitDepth / 8
        val frameSize = channels * bytesPerSample
        val sampleCount = samples.size / channels
        val dataSize = sampleCount * frameSize

        FileOutputStream(tempFile.toFile()).use { fos ->
            val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put("RIFF".toByteArray())
            buffer.putInt(36 + dataSize)
            buffer.put("WAVE".toByteArray())

            // fmt chunk
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16)
            buffer.putShort(1) // PCM
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(sampleRate * frameSize)
            buffer.putShort(frameSize.toShort())
            buffer.putShort(bitDepth.toShort())

            // data chunk
            buffer.put("data".toByteArray())
            buffer.putInt(dataSize)

            // Write samples
            for (i in 0 until sampleCount) {
                for (ch in 0 until channels) {
                    val sampleIndex = i * channels + ch
                    if (sampleIndex < samples.size) {
                        val sample = samples[sampleIndex]
                        when (bitDepth) {
                            8 -> {
                                val value = ((sample + 1) * 127.5).toInt().toByte()
                                buffer.put(value)
                            }
                            16 -> {
                                val value = (sample * 32767).toInt().toShort()
                                buffer.putShort(value)
                            }
                            24 -> {
                                val value = (sample * 8388607).toInt()
                                buffer.put((value and 0xFF).toByte())
                                buffer.put(((value shr 8) and 0xFF).toByte())
                                buffer.put(((value shr 16) and 0xFF).toByte())
                            }
                            32 -> buffer.putFloat(sample)
                        }
                    }
                }
            }

            buffer.flip()
            fos.write(buffer.array(), 0, buffer.limit())
        }

        return tempFile
    }
}
