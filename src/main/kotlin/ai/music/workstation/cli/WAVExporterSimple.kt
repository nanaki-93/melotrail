package ai.music.workstation.cli

import ai.music.workstation.audio.AudioBuffer
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Path

/**
 * Simple WAV exporter for CLI use.
 * Writes 24-bit PCM WAV files.
 */
class WAVExporterSimple {

    fun export(buffer: AudioBuffer, outputPath: Path) {
        require(buffer.format.sampleRate > 0) { "Sample rate must be positive" }
        require(buffer.format.channels > 0) { "Channel count must be positive" }

        // Keep the source format intact. LoFi is a processing stage, not an
        // implicit sample-rate conversion stage.
        val bytesPerSample = 3 // 24-bit
        val totalFrames = buffer.length
        val dataSize = totalFrames * buffer.format.channels * bytesPerSample
        val fileSize = 36 + dataSize

        FileOutputStream(outputPath.toFile()).use { fos ->
            DataOutputStream(fos).use { dos ->
                // RIFF header
                dos.writeBytes("RIFF")
                dos.writeLittleEndianInt(fileSize)
                dos.writeBytes("WAVE")

                // fmt chunk
                dos.writeBytes("fmt ")
                dos.writeLittleEndianInt(16) // chunk size
                dos.writeLittleEndianShort(1) // PCM
                dos.writeLittleEndianShort(buffer.format.channels)
                dos.writeLittleEndianInt(buffer.format.sampleRate)
                dos.writeLittleEndianInt(buffer.format.sampleRate * buffer.format.channels * bytesPerSample)
                dos.writeLittleEndianShort(buffer.format.channels * bytesPerSample)
                dos.writeLittleEndianShort(24) // 24-bit

                // data chunk
                dos.writeBytes("data")
                dos.writeLittleEndianInt(dataSize)

                // Write samples as 24-bit PCM
                for (i in 0 until totalFrames) {
                    for (c in 0 until buffer.format.channels) {
                        val sample = buffer.getSample(c, i)
                        val value = (sample * 8388607f).toInt().coerceIn(-8388608, 8388607)
                        dos.writeByte(value and 0xFF)
                        dos.writeByte((value shr 8) and 0xFF)
                        dos.writeByte((value shr 16) and 0xFF)
                    }
                }
            }
        }
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value shr 8) and 0xFF)
        writeByte((value shr 16) and 0xFF)
        writeByte((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value shr 8) and 0xFF)
    }

}
