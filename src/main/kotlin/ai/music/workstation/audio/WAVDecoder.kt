package ai.music.workstation.audio

import ai.music.workstation.errors.AppError
import ai.music.workstation.errors.AppErrorException
import ai.music.workstation.errors.ErrorCategory
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.model.ErrorReporter as ErrorReporterInterface
import kotlinx.coroutines.Dispatchers
import java.io.DataInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ln
import kotlin.math.pow

class WAVDecoder(
    private val errorReporter: ErrorReporterInterface
) : BaseDecoder(setOf("wav", "wave")) {

    override fun decode(path: Path): AudioBuffer {
        if (!Files.exists(path)) {
            errorReporter.report("WAV file not found: $path")
            throw AppErrorException(AppError.FileNotFoundError(path))
        }

        val fileSize = Files.size(path)
        if (fileSize < 44) {
            errorReporter.report("File too small to be a valid WAV: $path")
            throw AppErrorException(AppError.CorruptedFileError(path, "File too small to be a valid WAV"))
        }

        return try {
            val inputStream = FileInputStream(path.toFile()).channel.use { channel ->
                val buffer = ByteBuffer.allocate(channel.size().toInt()).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(buffer)
                buffer.flip()
                DataInputStream(buffer.asInputStream())
            }

            decodeWav(inputStream, path)
        } catch (e: AppErrorException) {
            throw e
        } catch (e: Exception) {
            errorReporter.report("Failed to decode WAV file: ${e.message}", e)
            throw AppErrorException(AppError.AudioDecodeError(path, "WAV"))
        }
    }

    private fun decodeWav(inputStream: DataInputStream, path: Path): AudioBuffer {
        val header = readWavHeader(inputStream)
        val sampleCount = header.dataSize / (header.bitsPerSample / 8)
        val samples = FloatArray(sampleCount)

        val bytesPerSample = header.bitsPerSample / 8
        for (i in 0 until sampleCount) {
            val rawValue = readSignedInteger(inputStream, bytesPerSample)
            samples[i] = normalizeSample(rawValue, header.bitsPerSample)
        }

        val format = AudioFormat(
            sampleRate = header.sampleRate,
            channels = header.channels,
            bitDepth = header.bitsPerSample,
            isFloat = header.bitsPerSample == 32,
            isBigEndian = false,
            encoding = "WAV"
        )

        val duration = sampleCount.toDouble() / header.sampleRate

        return AudioBuffer(samples, format, duration)
    }

    private fun readWavHeader(inputStream: DataInputStream): WavHeader {
        val chunkId = readFourCC(inputStream)
        if (chunkId != "RIFF") {
            errorReporter.report("Invalid RIFF chunk ID: $chunkId")
            throw AppErrorException(AppError.CorruptedFileError(
                java.nio.file.Path.of("unknown"),
                "Invalid RIFF chunk ID: $chunkId"
            ))
        }

        readLittleEndianInt(inputStream) // File size - 8 (skip)

        val riffType = readFourCC(inputStream)
        if (riffType != "WAVE") {
            errorReporter.report("Invalid RIFF type: $riffType")
            throw AppErrorException(AppError.CorruptedFileError(
                java.nio.file.Path.of("unknown"),
                "Invalid RIFF type: $riffType"
            ))
        }

        var dataSize = 0
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0

        var chunkCount = 0
        while (inputStream.available() > 0) {
            val subChunkId = readFourCC(inputStream)
            val subChunkSize = readLittleEndianInt(inputStream)
            chunkCount++
            errorReporter.report("Chunk $chunkCount: $subChunkId, size: $subChunkSize")
            if (chunkCount > 100) {
                errorReporter.report("Too many chunks in WAV file, stopping")
                break
            }

            when (subChunkId) {
                "fmt " -> {
                    val audioFormat = readLittleEndianShort(inputStream)
                    channels = readLittleEndianShort(inputStream).toInt()
                    sampleRate = readLittleEndianInt(inputStream)
                    readLittleEndianInt(inputStream) // byte rate (skip)
                    readLittleEndianShort(inputStream) // block align (skip)
                    bitsPerSample = readLittleEndianShort(inputStream).toInt()
                    errorReporter.report("fmt chunk: audioFormat=$audioFormat, channels=$channels, sampleRate=$sampleRate, bitsPerSample=$bitsPerSample")

                    // Skip any extra bytes
                    val extraBytes = subChunkSize - 16
                    if (extraBytes > 0) {
                        inputStream.skip(extraBytes.toLong())
                    }
                }
                "data" -> {
                    dataSize = subChunkSize
                    errorReporter.report("data chunk: dataSize=$dataSize")
                    // Don't skip data - leave position at beginning of data for decodeWav to read
                    // Break to avoid reading garbage past the data chunk
                    break
                }
                else -> {
                    errorReporter.report("Skipping chunk: $subChunkId, size: $subChunkSize")
                    inputStream.skip(subChunkSize.toLong())
                }
            }
        }

        if (dataSize <= 0) {
            errorReporter.report("No data chunk found in WAV file")
            throw AppErrorException(AppError.CorruptedFileError(
                java.nio.file.Path.of("unknown"),
                "No data chunk found in WAV file"
            ))
        }

        if (bitsPerSample !in listOf(8, 16, 24, 32)) {
            errorReporter.report("Unsupported bit depth: $bitsPerSample")
            throw AppErrorException(AppError.AudioDecodeError(
                java.nio.file.Path.of("unknown"),
                "Unsupported bit depth: $bitsPerSample"
            ))
        }

        return WavHeader(
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            dataSize = dataSize
        )
    }

    private fun readFourCC(inputStream: DataInputStream): String {
        return buildString {
            for (i in 0 until 4) {
                append(inputStream.readByte().toChar())
            }
        }
    }

    private fun readLittleEndianInt(inputStream: DataInputStream): Int {
        val b0 = inputStream.readByte().toInt() and 0xFF
        val b1 = inputStream.readByte().toInt() and 0xFF
        val b2 = inputStream.readByte().toInt() and 0xFF
        val b3 = inputStream.readByte().toInt()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readLittleEndianShort(inputStream: DataInputStream): Short {
        val b0 = inputStream.readByte().toInt() and 0xFF
        val b1 = inputStream.readByte().toInt()
        return (b0 or (b1 shl 8)).toShort()
    }

    private fun readSignedInteger(inputStream: DataInputStream, bytes: Int): Int {
        return when (bytes) {
            1 -> inputStream.readByte().toInt() and 0xFF
            2 -> readLittleEndianShort(inputStream).toInt()
            3 -> read24BitInteger(inputStream)
            4 -> readLittleEndianInt(inputStream)
            else -> throw IllegalArgumentException("Unsupported byte count: $bytes")
        }
    }

    private fun read24BitInteger(inputStream: DataInputStream): Int {
        val b0 = inputStream.readByte().toInt() and 0xFF
        val b1 = inputStream.readByte().toInt() and 0xFF
        val b2 = inputStream.readByte().toInt()
        val result = (b2 shl 16) or (b1 shl 8) or b0
        // Sign extend
        return if (result and 0x800000 != 0) result or 0xFF000000L.toInt() else result
    }

    private fun normalizeSample(rawValue: Int, bitDepth: Int): Float {
        return when (bitDepth) {
            8 -> (rawValue - 128) / 128.0f
            16 -> rawValue / 32768.0f
            24 -> rawValue / 8388608.0f
            32 -> rawValue.toFloat() / 2147483648.0f
            else -> rawValue / 32768.0f
        }
    }

    private fun ByteBuffer.asInputStream(): DataInputStream {
        // Create a new byte array with only the buffer's content
        val bytes = ByteArray(limit() - position())
        slice().get(bytes)
        return DataInputStream(java.io.ByteArrayInputStream(bytes))
    }

    data class WavHeader(
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataSize: Int
    )
}
