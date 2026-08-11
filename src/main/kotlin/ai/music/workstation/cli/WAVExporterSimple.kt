package ai.music.workstation.cli

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Path

/**
 * Simple WAV exporter for CLI use.
 * Writes 24-bit PCM WAV files.
 */
class WAVExporterSimple {

    fun export(buffer: AudioBuffer, outputPath: Path) {
        // Resample to 48kHz if needed
        val resampled = if (buffer.format.sampleRate != 48000) {
            resample(buffer, 48000)
        } else {
            buffer
        }

        // Write WAV file
        val bytesPerSample = 3 // 24-bit
        val totalSamples = resampled.length
        val dataSize = totalSamples * resampled.format.channels * bytesPerSample
        val fileSize = 36 + dataSize

        FileOutputStream(outputPath.toFile()).use { fos ->
            DataOutputStream(fos).use { dos ->
                // RIFF header
                dos.writeBytes("RIFF")
                dos.writeInt(fileSize)
                dos.writeBytes("WAVE")

                // fmt chunk
                dos.writeBytes("fmt ")
                dos.writeInt(16) // chunk size
                dos.writeShort(1) // PCM
                dos.writeShort(resampled.format.channels)
                dos.writeInt(resampled.format.sampleRate)
                dos.writeInt(resampled.format.sampleRate * resampled.format.channels * bytesPerSample)
                dos.writeShort(resampled.format.channels * bytesPerSample)
                dos.writeShort(24) // 24-bit

                // data chunk
                dos.writeBytes("data")
                dos.writeInt(dataSize)

                // Write samples as 24-bit PCM
                for (i in 0 until totalSamples) {
                    for (c in 0 until resampled.format.channels) {
                        val sample = resampled.getSample(c, i)
                        val value = (sample * 8388607f).toInt().coerceIn(-8388608, 8388607)
                        dos.writeByte(value and 0xFF)
                        dos.writeByte((value shr 8) and 0xFF)
                        dos.writeByte((value shr 16) and 0xFF)
                    }
                }
            }
        }
    }

    /**
     * Simple linear interpolation resampler.
     */
    private fun resample(buffer: AudioBuffer, targetSampleRate: Int): AudioBuffer {
        val ratio = targetSampleRate.toDouble() / buffer.format.sampleRate
        val newLength = (buffer.length * ratio).toInt()
        val newSamples = FloatArray(newLength * buffer.format.channels)

        for (i in 0 until newLength) {
            val srcIndex = (i / ratio).toInt()
            val frac = (i / ratio) - srcIndex

            for (c in 0 until buffer.format.channels) {
                val srcPos = srcIndex * buffer.format.channels + c
                if (srcPos + 1 < buffer.samples.size) {
                    newSamples[i * buffer.format.channels + c] =
                        buffer.samples[srcPos] * (1f - frac.toFloat()) +
                        buffer.samples[srcPos + 1] * frac.toFloat()
                } else {
                    newSamples[i * buffer.format.channels + c] = buffer.samples[srcPos]
                }
            }
        }

        return AudioBuffer(
            samples = newSamples,
            format = AudioFormat(
                sampleRate = targetSampleRate,
                channels = buffer.format.channels,
                bitDepth = 24,
                isFloat = false,
                isBigEndian = false,
                encoding = "WAV"
            ),
            duration = buffer.duration
        )
    }
}
