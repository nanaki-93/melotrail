package app.melotrail.arrangement

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Metadata from one validated project-format WAV stem. */
data class RenderResult(
    val output: Path,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val frameCount: Long,
    val durationSeconds: Double,
    val peak: Double,
    val rendererIdentity: String,
    val rendererVersion: String,
    val stdout: String,
    val stderr: String
)

/** Boundary between logical instruments and a local, offline sampler. */
interface InstrumentRenderer {
    suspend fun render(
        midi: Path,
        instrument: LogicalInstrument,
        output: Path,
        format: RenderFormat,
        expectedFrames: Long
    ): RenderResult
}

data class RendererProcessResult(
    val exitCode: Int?,
    val timedOut: Boolean,
    val stdout: String,
    val stderr: String
)

fun interface RendererProcess {
    suspend fun run(arguments: List<String>, timeoutMillis: Long): RendererProcessResult
}

/** Runs a local executable with an argument list; it never invokes a shell. */
class LocalRendererProcess : RendererProcess {
    override suspend fun run(arguments: List<String>, timeoutMillis: Long): RendererProcessResult = withContext(Dispatchers.IO) {
        require(arguments.isNotEmpty()) { "Renderer command is empty" }
        val process = try {
            ProcessBuilder(arguments).start()
        } catch (error: Exception) {
            throw IllegalStateException("Could not start SFZ renderer '${arguments.first()}': ${error.message ?: error.javaClass.simpleName}", error)
        }
        val stdout = CompletableFuture.supplyAsync { process.inputStream.readBoundedUtf8() }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.readBoundedUtf8() }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        RendererProcessResult(
            exitCode = if (finished) process.exitValue() else null,
            timedOut = !finished,
            stdout = stdout.get(2, TimeUnit.SECONDS),
            stderr = stderr.get(2, TimeUnit.SECONDS)
        )
    }

    private fun java.io.InputStream.readBoundedUtf8(): String {
        val kept = ByteArrayOutputStreamBounded(MAX_DIAGNOSTIC_BYTES)
        BufferedInputStream(this).use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                kept.write(buffer, 0, count)
            }
        }
        return kept.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private companion object {
        const val MAX_DIAGNOSTIC_BYTES = 16 * 1024
    }
}

/** sfizz_render is stereo-only, so this adapter converts its WAV to the project's PCM-24 layout. */
class SfizzInstrumentRenderer(
    private val registryLoader: InstrumentRegistryLoader,
    private val process: RendererProcess = LocalRendererProcess(),
    private val executable: String = System.getenv("SFZ_RENDERER_PATH")?.takeIf { it.isNotBlank() } ?: "sfizz_render",
    private val rendererVersion: String = System.getenv("SFZ_RENDERER_VERSION")?.takeIf { it.isNotBlank() } ?: "not reported by renderer",
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : InstrumentRenderer {
    override suspend fun render(
        midi: Path,
        instrument: LogicalInstrument,
        output: Path,
        format: RenderFormat,
        expectedFrames: Long
    ): RenderResult = render(midi, instrument.wireName, output, format, expectedFrames)

    /** Stable-ID renderer entry point used by the catalog path. */
    suspend fun render(
        midi: Path,
        instrumentId: String,
        output: Path,
        format: RenderFormat,
        expectedFrames: Long
    ): RenderResult {
        validateFormat(format, expectedFrames)
        val normalizedMidi = validateMidi(midi)
        val descriptor = registryLoader.load().resolve(instrumentId)
        val target = output.toAbsolutePath().normalize()
        validateOutput(target, normalizedMidi, descriptor)
        Files.createDirectories(requireNotNull(target.parent))

        val raw = siblingTemp(target, "sfizz-raw")
        val verified = siblingTemp(target, "sfizz-verified")
        try {
            val command = listOf(
                executable,
                "--sfz", descriptor.sfzPath.toString(),
                "--midi", normalizedMidi.toString(),
                "--wav", raw.toString(),
                "--samplerate", format.sampleRate.toString(),
                "--use-eot"
            )
            val processResult = process.run(command, timeoutMillis)
            if (processResult.timedOut) {
                throw IllegalStateException("SFZ renderer timed out after ${timeoutMillis}ms. ${diagnostics(processResult)}")
            }
            if (processResult.exitCode != 0) {
                throw IllegalStateException("SFZ renderer failed with exit code ${processResult.exitCode}. ${diagnostics(processResult)}")
            }
            require(Files.isRegularFile(raw) && Files.size(raw) > 0L) {
                "SFZ renderer completed without creating a WAV output. ${diagnostics(processResult)}"
            }

            val source = WavFile.inspect(raw)
            require(source.sampleRate == format.sampleRate) {
                "SFZ renderer wrote ${source.sampleRate} Hz, expected ${format.sampleRate} Hz"
            }
            // sfizz_render's offline client deliberately writes a stereo WAV.
            require(source.channels == SFIZZ_OUTPUT_CHANNELS) {
                "SFZ renderer wrote ${source.channels} channels; sfizz_render output must be stereo before project conversion"
            }
            require(source.frameCount > 0) { "SFZ renderer wrote an empty WAV" }
            val maxFrames = Math.addExact(expectedFrames, tailPolicyFrames(format.sampleRate))
            require(source.frameCount <= maxFrames) {
                "SFZ renderer output is too long (${source.frameCount} frames; expected $expectedFrames plus at most ${tailPolicyFrames(format.sampleRate)} tail frames)"
            }

            WavFile.convertToPcm24(source, verified, format.sampleRate, format.channels, expectedFrames)
            val finalWav = WavFile.inspect(verified)
            require(finalWav.sampleRate == format.sampleRate && finalWav.channels == format.channels && finalWav.bitsPerSample == 24 && finalWav.pcm) {
                "Validated SFZ output does not match requested PCM-24 WAV format"
            }
            require(finalWav.frameCount == expectedFrames) {
                "Validated SFZ output has ${finalWav.frameCount} frames, expected $expectedFrames"
            }
            require(finalWav.peak < 1.0) { "Validated SFZ output is clipped (peak ${finalWav.peak})" }
            atomicReplace(verified, target)
            return RenderResult(
                output = target,
                sampleRate = finalWav.sampleRate,
                channels = finalWav.channels,
                bitDepth = finalWav.bitsPerSample,
                frameCount = finalWav.frameCount,
                durationSeconds = finalWav.frameCount.toDouble() / finalWav.sampleRate,
                peak = finalWav.peak,
                rendererIdentity = executable,
                rendererVersion = rendererVersion,
                stdout = processResult.stdout,
                stderr = processResult.stderr
            )
        } finally {
            Files.deleteIfExists(raw)
            Files.deleteIfExists(verified)
        }
    }

    private fun validateFormat(format: RenderFormat, expectedFrames: Long) {
        require(format.sampleRate in 8_000..384_000) { "Render sample rate must be from 8000 to 384000" }
        require(format.channels in 1..32) { "Render channels must be from 1 to 32" }
        require(format.bitDepth == 24) { "SFZ stems must use PCM-24" }
        require(expectedFrames > 0) { "Expected frame count must be positive" }
    }

    private fun validateMidi(midi: Path): Path {
        val normalized = midi.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalized)) { "MIDI file not found: $normalized" }
        val sequence = try {
            MidiSystem.getSequence(normalized.toFile())
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid MIDI file '$normalized': ${error.message ?: error.javaClass.simpleName}", error)
        }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution > 0) {
            "Unsupported MIDI timing in '$normalized': only PPQ MIDI is supported"
        }
        return normalized
    }

    private fun validateOutput(target: Path, midi: Path, descriptor: ValidatedInstrumentDescriptor) {
        require(!target.startsWith(registryLoader.libraryRoot.toAbsolutePath().normalize())) {
            "SFZ render output must not be inside the sound library: $target"
        }
        require(target.fileName.toString().endsWith(".wav", ignoreCase = true)) { "SFZ render output must be a .wav file" }
        val protected = listOf(midi, descriptor.sfzPath) + descriptor.samplePaths
        protected.forEach { source ->
            if (sameFileOrPath(target, source)) throw IllegalArgumentException("SFZ render output must not overwrite source file: $source")
        }
    }

    private fun sameFileOrPath(first: Path, second: Path): Boolean =
        first == second.toAbsolutePath().normalize() || (Files.exists(first) && Files.isSameFile(first, second))

    private fun siblingTemp(target: Path, stage: String): Path =
        target.resolveSibling(".${target.fileName}.$stage-${UUID.randomUUID()}.wav")

    private fun atomicReplace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for SFZ output '$target'", error)
        }
    }

    private fun diagnostics(result: RendererProcessResult): String =
        "stdout=${result.stdout.ifBlank { "<empty>" }.take(MAX_DIAGNOSTIC_BYTES)}, stderr=${result.stderr.ifBlank { "<empty>" }.take(MAX_DIAGNOSTIC_BYTES)}"

    private fun tailPolicyFrames(sampleRate: Int): Long = sampleRate.toLong() * MAX_RENDERER_TAIL_SECONDS

    private companion object {
        const val SFIZZ_OUTPUT_CHANNELS = 2
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val MAX_RENDERER_TAIL_SECONDS = 2L
        const val MAX_DIAGNOSTIC_BYTES = 16 * 1024
    }
}

/** Minimal strict WAV reader/writer used to validate untrusted renderer output. */
private data class WavFile(
    val path: Path,
    val pcm: Boolean,
    val floatingPoint: Boolean,
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSize: Long,
    val frameCount: Long,
    val peak: Double
) {
    companion object {
        fun inspect(path: Path): WavFile {
            RandomAccessFile(path.toFile(), "r").use { input ->
                require(input.length() >= 44) { "Malformed WAV: file is smaller than a RIFF header" }
                require(input.readFourCc() == "RIFF" && input.readUInt32() >= 36 && input.readFourCc() == "WAVE") { "Malformed WAV: missing RIFF/WAVE header" }
                var pcm = false
                var floatingPoint = false
                var channels = 0
                var sampleRate = 0
                var bits = 0
                var blockAlign = 0
                var dataOffset = -1L
                var dataSize = -1L
                while (input.filePointer + 8 <= input.length()) {
                    val id = input.readFourCc()
                    val size = input.readUInt32()
                    require(size <= input.length() - input.filePointer) { "Malformed WAV: chunk '$id' exceeds file length" }
                    when (id) {
                        "fmt " -> {
                            require(size >= 16) { "Malformed WAV: short fmt chunk" }
                            val encoding = input.readUInt16()
                            channels = input.readUInt16()
                            sampleRate = input.readUInt32().toInt()
                            input.readUInt32()
                            blockAlign = input.readUInt16()
                            bits = input.readUInt16()
                            pcm = encoding == 1
                            floatingPoint = encoding == 3
                            input.seek(input.filePointer + size - 16)
                        }
                        "data" -> {
                            dataOffset = input.filePointer
                            dataSize = size
                            input.seek(input.filePointer + size)
                        }
                        else -> input.seek(input.filePointer + size)
                    }
                    if (size and 1L == 1L) input.seek(input.filePointer + 1)
                }
                require(pcm || floatingPoint) { "Malformed WAV: only PCM or IEEE float encoding is supported" }
                require(channels in 1..32 && sampleRate > 0 && bits in setOf(8, 16, 24, 32)) { "Malformed WAV: invalid audio format" }
                require(!(floatingPoint && bits != 32)) { "Malformed WAV: only 32-bit IEEE float is supported" }
                val bytesPerSample = bits / 8
                require(blockAlign == channels * bytesPerSample) { "Malformed WAV: inconsistent block alignment" }
                require(dataOffset >= 0 && dataSize > 0 && dataSize % blockAlign == 0L) { "Malformed WAV: empty or incomplete data chunk" }
                val frames = dataSize / blockAlign
                input.seek(dataOffset)
                var peak = 0.0
                repeatExact(frames) {
                    repeat(channels) {
                        val value = input.readSample(pcm, bits)
                        require(value.isFinite()) { "Malformed WAV: non-finite sample" }
                        peak = maxOf(peak, abs(value))
                    }
                }
                return WavFile(path, pcm, floatingPoint, channels, sampleRate, bits, dataOffset, dataSize, frames, peak)
            }
        }

        fun convertToPcm24(source: WavFile, output: Path, sampleRate: Int, channels: Int, expectedFrames: Long) {
            require(source.frameCount > 0) { "Cannot convert an empty WAV" }
            RandomAccessFile(source.path.toFile(), "r").use { input ->
                Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { stream ->
                    DataOutputStream(BufferedOutputStream(stream)).use { out ->
                        writePcm24Header(out, sampleRate, channels, expectedFrames)
                        input.seek(source.dataOffset)
                        repeatExact(expectedFrames) { frame ->
                            val left: Double
                            val right: Double
                            if (frame < source.frameCount) {
                                left = input.readSample(source.pcm, source.bitsPerSample)
                                right = input.readSample(source.pcm, source.bitsPerSample)
                            } else {
                                left = 0.0
                                right = 0.0
                            }
                            require(left.isFinite() && right.isFinite()) { "Renderer WAV contains non-finite samples" }
                            require(abs(left) < 1.0 && abs(right) < 1.0) { "Renderer WAV is clipped" }
                            repeat(channels) { channel ->
                                val sample = when (channels) {
                                    1 -> (left + right) / 2.0
                                    else -> if (channel % 2 == 0) left else right
                                }
                                out.writePcm24(sample)
                            }
                        }
                    }
                }
            }
        }

        private fun writePcm24Header(out: DataOutputStream, sampleRate: Int, channels: Int, frames: Long) {
            val dataSize = Math.multiplyExact(Math.multiplyExact(frames, channels.toLong()), 3L)
            require(dataSize <= Int.MAX_VALUE.toLong()) { "Rendered WAV is too large for RIFF PCM-24" }
            val byteRate = Math.multiplyExact(sampleRate.toLong(), channels.toLong() * 3L)
            out.writeBytes("RIFF")
            out.writeIntLE((36L + dataSize).toInt())
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)
            out.writeShortLE(channels)
            out.writeIntLE(sampleRate)
            out.writeIntLE(byteRate.toInt())
            out.writeShortLE(channels * 3)
            out.writeShortLE(24)
            out.writeBytes("data")
            out.writeIntLE(dataSize.toInt())
        }

        private fun repeatExact(times: Long, action: (Long) -> Unit) {
            var index = 0L
            while (index < times) {
                action(index)
                index++
            }
        }
    }
}

private class ByteArrayOutputStreamBounded(private val limit: Int) {
    private val bytes = java.io.ByteArrayOutputStream(limit)
    fun write(buffer: ByteArray, offset: Int, length: Int) {
        val available = limit - bytes.size()
        if (available > 0) bytes.write(buffer, offset, minOf(length, available))
    }
    fun toByteArray(): ByteArray = bytes.toByteArray()
}

private fun RandomAccessFile.readFourCc(): String = ByteArray(4).also { readFully(it) }.toString(StandardCharsets.US_ASCII)
private fun RandomAccessFile.readUInt16(): Int = read() or (read() shl 8)
private fun RandomAccessFile.readUInt32(): Long = readUInt16().toLong() or (readUInt16().toLong() shl 16)
private fun RandomAccessFile.readSample(pcm: Boolean, bits: Int): Double = when {
    !pcm && bits == 32 -> java.lang.Float.intBitsToFloat(readUInt32().toInt()).toDouble()
    pcm && bits == 8 -> (read().toDouble() - 128.0) / 128.0
    pcm && bits == 16 -> readUInt16().toShort().toDouble() / 32_768.0
    pcm && bits == 24 -> {
        val value = read() or (read() shl 8) or (read() shl 16)
        (if (value and 0x80_0000 != 0) value or -0x1_000000 else value).toDouble() / 8_388_608.0
    }
    pcm && bits == 32 -> readUInt32().toInt().toDouble() / 2_147_483_648.0
    else -> error("Unsupported WAV sample format")
}
private fun DataOutputStream.writeIntLE(value: Int) { writeByte(value); writeByte(value ushr 8); writeByte(value ushr 16); writeByte(value ushr 24) }
private fun DataOutputStream.writeShortLE(value: Int) { writeByte(value); writeByte(value ushr 8) }
private fun DataOutputStream.writePcm24(sample: Double) {
    require(sample.isFinite() && abs(sample) < 1.0) { "Cannot write clipped or non-finite PCM sample" }
    val value = (sample * 8_388_607.0).toInt().coerceIn(-8_388_608, 8_388_607)
    writeByte(value); writeByte(value ushr 8); writeByte(value ushr 16)
}
