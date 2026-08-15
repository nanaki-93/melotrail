package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.audio.AudioResampler
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.log10
import kotlin.math.pow

/** One source or generated stem placed on the mix timeline in frames. */
data class MixTrack(
    val name: String,
    val buffer: AudioBuffer,
    val startFrame: Int = 0,
    val gainDb: Double = 0.0,
    val pan: Double = 0.0,
    val muted: Boolean = false,
    val generated: Boolean = false
)

data class MixSettings(
    val targetSampleRate: Int? = null,
    val dry: Boolean = false,
    /** A reference mix includes source tracks only, just like the legacy dry mode. */
    val reference: Boolean = false,
    /** Project-format stems must match exactly; legacy imports may opt out explicitly. */
    val requiredFormat: RenderFormat? = null,
    /** Optional deterministic safety ceiling applied once to the completed mix. */
    val peakCeiling: Double? = 0.95
)

data class MixedStem(
    val buffer: AudioBuffer,
    val includedTracks: List<String>,
    val predictedPeak: Float = 0f,
    val appliedGain: Float = 1f,
    val appliedGainDb: Double = 0.0
)

/**
 * Deterministic, frame-based mixer for the first arranger output. It supports
 * mono/stereo legacy material and exact project-format stems. The mix is summed
 * in floating point then, when needed, reduced once by a uniform peak-safe gain.
 */
class DeterministicStemMixer {
    fun mix(tracks: List<MixTrack>, settings: MixSettings = MixSettings()): MixedStem {
        val activeTracks = tracks.filterNot { it.muted || ((settings.dry || settings.reference) && it.generated) }
        require(activeTracks.isNotEmpty()) { "No unmuted tracks available for mixing" }
        activeTracks.forEach(::validateTrack)

        settings.requiredFormat?.let { required ->
            require(required.bitDepth == PCM_BIT_DEPTH) { "Project stem format must be PCM-24" }
            activeTracks.forEach { track ->
                val actual = track.buffer.format
                require(actual.sampleRate == required.sampleRate && actual.channels == required.channels && actual.bitDepth == PCM_BIT_DEPTH) {
                    "Mix track '${track.name}' does not match required project format ${required.sampleRate} Hz, ${required.channels} channels, PCM-24"
                }
            }
        }
        val sampleRate = settings.requiredFormat?.sampleRate ?: settings.targetSampleRate ?: activeTracks.first().buffer.format.sampleRate
        require(sampleRate > 0) { "Target sample rate must be positive" }
        val outputChannels = settings.requiredFormat?.channels ?: activeTracks.maxOf { it.buffer.format.channels }
        require(outputChannels in 1..32) { "The simple stem mixer supports one through 32 channels" }
        settings.peakCeiling?.let { ceiling ->
            require(ceiling.isFinite() && ceiling > 0.0 && ceiling < 1.0) {
                "Mix peak ceiling must be a finite value between 0 and 1"
            }
        }

        val preparedTracks = activeTracks.map { track ->
            val prepared = if (settings.requiredFormat != null) track.buffer else AudioResampler.resample(track.buffer, sampleRate)
            track to if (settings.requiredFormat != null) prepared else convertChannels(prepared, outputChannels)
        }
        val outputFrames = preparedTracks.maxOf { (track, buffer) ->
            Math.addExact(track.startFrame, buffer.length)
        }
        require(outputFrames > 0) { "Mix must contain at least one frame" }
        require(outputFrames <= Int.MAX_VALUE / outputChannels) { "Mix is too large to render" }

        val output = FloatArray(outputFrames * outputChannels)
        preparedTracks.forEach { (track, buffer) -> addTrack(output, outputChannels, track, buffer) }
        require(output.all { it.isFinite() }) { "Mix contains non-finite samples" }
        val peak = output.maxOf { kotlin.math.abs(it) }
        val appliedGain = settings.peakCeiling?.takeIf { peak > it }?.let { ceiling ->
            (ceiling / peak).toFloat().also { gain -> output.indices.forEach { index -> output[index] *= gain } }
        } ?: 1f
        require(output.all { it.isFinite() && kotlin.math.abs(it) <= 1f }) {
            "Mix exceeds PCM range; configure a finite peak ceiling below 1"
        }

        return MixedStem(
            buffer = AudioBuffer(
                samples = output,
                format = AudioFormat(
                    sampleRate = sampleRate,
                    channels = outputChannels,
                    bitDepth = PCM_BIT_DEPTH,
                    isFloat = false,
                    isBigEndian = false,
                    encoding = "WAV"
                ),
                duration = outputFrames.toDouble() / sampleRate
            ),
            includedTracks = activeTracks.map { it.name },
            predictedPeak = peak,
            appliedGain = appliedGain,
            appliedGainDb = if (appliedGain == 1f) 0.0 else 20.0 * log10(appliedGain.toDouble())
        )
    }

    fun writeWav(mix: MixedStem, outputPath: Path): Path {
        val buffer = mix.buffer
        val dataSize = Math.multiplyExact(buffer.samples.size, BYTES_PER_PCM_SAMPLE)
        val byteRate = Math.multiplyExact(
            buffer.format.sampleRate,
            Math.multiplyExact(buffer.format.channels, BYTES_PER_PCM_SAMPLE)
        )
        require(buffer.samples.all { it.isFinite() && kotlin.math.abs(it) <= 1f }) { "Cannot write non-finite or out-of-range PCM samples" }
        Files.createDirectories(checkNotNull(outputPath.parent))
        val temporary = outputPath.resolveSibling(".${outputPath.fileName}.${UUID.randomUUID()}.tmp")
        try {
        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary.toFile()))).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(PCM_FORMAT)
            output.writeLittleEndianShort(buffer.format.channels)
            output.writeLittleEndianInt(buffer.format.sampleRate)
            output.writeLittleEndianInt(byteRate)
            output.writeLittleEndianShort(buffer.format.channels * BYTES_PER_PCM_SAMPLE)
            output.writeLittleEndianShort(PCM_BIT_DEPTH)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            buffer.samples.forEach { sample -> output.writePcm24(sample) }
        }
        try {
            Files.move(temporary, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return outputPath
    }

    private fun validateTrack(track: MixTrack) {
        require(track.name.isNotBlank()) { "Mix track name must not be blank" }
        require(track.startFrame >= 0) { "Mix track '${track.name}' start frame must not be negative" }
        require(track.buffer.format.sampleRate > 0) { "Mix track '${track.name}' has an invalid sample rate" }
        require(track.buffer.format.channels in 1..32) {
            "Mix track '${track.name}' must have one through 32 channels"
        }
        require(track.buffer.length > 0) { "Mix track '${track.name}' has no frames" }
        require(track.gainDb.isFinite()) { "Mix track '${track.name}' gain must be finite" }
        require(track.pan.isFinite() && track.pan in -1.0..1.0) {
            "Mix track '${track.name}' pan must be between -1 and 1"
        }
        require(track.buffer.samples.all { it.isFinite() }) { "Mix track '${track.name}' contains non-finite samples" }
    }

    private fun convertChannels(buffer: AudioBuffer, targetChannels: Int): AudioBuffer {
        if (buffer.format.channels == targetChannels) return buffer
        val output = FloatArray(buffer.length * targetChannels)
        for (frame in 0 until buffer.length) {
            for (channel in 0 until targetChannels) {
                output[frame * targetChannels + channel] = when {
                    targetChannels == 1 -> (0 until buffer.format.channels).sumOf { buffer.getSample(it, frame).toDouble() }.div(buffer.format.channels).toFloat()
                    buffer.format.channels == 1 -> buffer.getSample(0, frame)
                    else -> buffer.getSample(minOf(channel, buffer.format.channels - 1), frame)
                }
            }
        }
        return AudioBuffer(
            samples = output,
            format = buffer.format.copy(channels = targetChannels),
            duration = buffer.length.toDouble() / buffer.format.sampleRate
        )
    }

    private fun addTrack(output: FloatArray, outputChannels: Int, track: MixTrack, buffer: AudioBuffer) {
        val gain = 10.0.pow(track.gainDb / 20.0).toFloat()
        val (leftPan, rightPan) = if (outputChannels == 2) {
            (1.0 - track.pan.coerceAtLeast(0.0)).toFloat() to
                (1.0 + track.pan.coerceAtMost(0.0)).toFloat()
        } else {
            1f to 1f
        }
        for (frame in 0 until buffer.length) {
            val destination = (track.startFrame + frame) * outputChannels
            if (outputChannels == 1) {
                output[destination] += buffer.getSample(0, frame) * gain
            } else if (outputChannels == 2) {
                output[destination] += buffer.getSample(0, frame) * gain * leftPan
                output[destination + 1] += buffer.getSample(1, frame) * gain * rightPan
            } else {
                for (channel in 0 until outputChannels) {
                    output[destination + channel] += buffer.getSample(channel, frame) * gain
                }
            }
        }
    }

    private fun DataOutputStream.writePcm24(sample: Float) {
        val value = (sample * PCM_24_MAX).toInt()
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    private companion object {
        const val BYTES_PER_PCM_SAMPLE = 3
        const val PCM_BIT_DEPTH = 24
        const val PCM_FORMAT = 1
        const val PCM_24_MAX = 8_388_607f
    }
}
