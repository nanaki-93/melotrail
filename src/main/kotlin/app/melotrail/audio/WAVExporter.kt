package app.melotrail.audio

import app.melotrail.errors.AppError
import app.melotrail.errors.AppErrorException
import app.melotrail.errors.ErrorReporter
import app.melotrail.model.ErrorReporter as ErrorReporterInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class WAVExporter(
    private val errorReporter: ErrorReporterInterface
) : AudioExporter {
    override val supportedFormats: Set<ExportFormat> = setOf(ExportFormat.WAV)

    override suspend fun export(
        buffer: AudioBuffer,
        settings: ExportSettings,
        outputPath: Path,
        progress: (Double) -> Unit
    ): Path = withContext(Dispatchers.IO) {
        val validationErrors = validateSettings(settings)
        if (validationErrors.isNotEmpty()) {
            throw AppErrorException(AppError.AudioExportError(
                outputPath,
                "WAV",
                validationErrors.joinToString("; ")
            ))
        }

        Files.createDirectories(outputPath.parent)

        // Resample if needed
        val resampledBuffer = if (buffer.format.sampleRate != settings.sampleRate) {
            AudioResampler.resample(buffer, settings.sampleRate)
        } else {
            buffer
        }

        // Convert float samples to target bit depth
        val convertedBuffer = convertBitDepth(resampledBuffer, settings.bitDepth, settings.float)

        writeWavFile(convertedBuffer, outputPath, settings.bitDepth)
        progress(1.0)
        outputPath
    }

    private fun writeWavFile(
        buffer: AudioBuffer,
        outputPath: Path,
        bitDepth: Int
    ) {
        val bytesPerSample = bitDepth / 8
        val dataSize = buffer.length * buffer.format.channels * bytesPerSample
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
                dos.writeLittleEndianShort(bitDepth)

                // data chunk
                dos.writeBytes("data")
                dos.writeLittleEndianInt(dataSize)

                // Write samples
                writeSamples(dos, buffer, bitDepth)
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

    private fun writeSamples(
        dos: DataOutputStream,
        buffer: AudioBuffer,
        bitDepth: Int
    ) {
        for (i in 0 until buffer.length) {
            for (c in 0 until buffer.format.channels) {
                val sample = buffer.getSample(c, i)
                when (bitDepth) {
                    16 -> dos.writeLittleEndianShort((sample * 32767).toInt())
                    24 -> {
                        val value = (sample * 8388607).toInt().coerceIn(-8388608, 8388607)
                        dos.writeByte(value and 0xFF)
                        dos.writeByte((value shr 8) and 0xFF)
                        dos.writeByte((value shr 16) and 0xFF)
                    }
                    32 -> {
                        val bits = java.lang.Float.floatToRawIntBits(sample)
                        dos.writeLittleEndianInt(bits)
                    }
                }
            }
        }
    }

    private fun convertBitDepth(
        buffer: AudioBuffer,
        targetBitDepth: Int,
        isFloat: Boolean
    ): AudioBuffer {
        if (isFloat && targetBitDepth == 32) return buffer
        if (!isFloat && targetBitDepth == 16) return buffer

        val convertedSamples = FloatArray(buffer.length)
        for (i in 0 until buffer.length) {
            val sample = if (buffer.format.channels == 1) {
                buffer.samples[i]
            } else {
                var sum = 0f
                for (c in 0 until buffer.format.channels) {
                    sum += buffer.samples[i * buffer.format.channels + c]
                }
                sum / buffer.format.channels
            }
            convertedSamples[i] = sample
        }
        return AudioBuffer(convertedSamples, buffer.format, buffer.duration)
    }
}
