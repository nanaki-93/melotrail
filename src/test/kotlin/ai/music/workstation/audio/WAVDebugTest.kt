package ai.music.workstation.audio

import ai.music.workstation.model.ErrorReporter
import org.junit.jupiter.api.Test
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class WAVDebugTest {
    @Test
    fun `debug wav file creation`() {
        val tempFile = createTestWav(
            channels = 1,
            sampleRate = 44100,
            bitDepth = 16,
            samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f, 0.0f)
        )
        
        println("File: $tempFile")
        println("Size: ${Files.size(tempFile)}")
        
        val bytes = Files.readAllBytes(tempFile)
        println("First 50 bytes: ${bytes.take(50).joinToString(",") { "%02x".format(it) }}")
        println("String representation: ${bytes.take(12).map { it.toInt().toChar() }.joinToString("")}")
        
        // Try to decode
        val errorReporter = object : ErrorReporter {
            override fun report(message: String) { println("Error: $message") }
            override fun report(message: String, cause: Throwable) { println("Error: $message, Cause: ${cause.message}") }
        }
        
        try {
            val decoder = WAVDecoder(errorReporter)
            val buffer = decoder.decode(tempFile)
            println("Decoded: ${buffer.length} samples")
        } catch (e: Exception) {
            println("Decode failed: ${e.message}")
            e.printStackTrace()
        }
        
        Files.delete(tempFile)
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
            // Use ByteBuffer in little-endian mode
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
                        val value = (sample * 32767).toInt()
                        buffer.putShort(value.toShort())
                    }
                }
            }
            
            buffer.flip()
            fos.write(buffer.array(), 0, buffer.limit())
        }

        return tempFile
    }
}
