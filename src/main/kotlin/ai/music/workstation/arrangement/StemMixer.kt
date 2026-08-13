package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import ai.music.workstation.audio.AudioResampler
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
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
    /** Optional deterministic safety ceiling applied once to the completed mix. */
    val peakCeiling: Double? = null
)

data class MixedStem(
    val buffer: AudioBuffer,
    val includedTracks: List<String>
)

/**
 * Deterministic, frame-based mixer for the first arranger output. It supports
 * mono/stereo source material, explicitly resamples to one selected rate, and
 * clamps only the final mixed samples to prevent digital overflow.
 */
class DeterministicStemMixer {
    fun mix(tracks: List<MixTrack>, settings: MixSettings = MixSettings()): MixedStem {
        val activeTracks = tracks.filterNot { it.muted || (settings.dry && it.generated) }
        require(activeTracks.isNotEmpty()) { "No unmuted tracks available for mixing" }
        activeTracks.forEach(::validateTrack)

        val sampleRate = settings.targetSampleRate ?: activeTracks.first().buffer.format.sampleRate
        require(sampleRate > 0) { "Target sample rate must be positive" }
        val outputChannels = activeTracks.maxOf { it.buffer.format.channels }
        require(outputChannels in 1..32) { "The simple stem mixer supports one through 32 channels" }
        settings.peakCeiling?.let { ceiling ->
            require(ceiling.isFinite() && ceiling > 0.0 && ceiling < 1.0) {
                "Mix peak ceiling must be a finite value between 0 and 1"
            }
        }

        val preparedTracks = activeTracks.map { track ->
            val resampled = AudioResampler.resample(track.buffer, sampleRate)
            track to convertChannels(resampled, outputChannels)
        }
        val outputFrames = preparedTracks.maxOf { (track, buffer) ->
            Math.addExact(track.startFrame, buffer.length)
        }
        require(outputFrames > 0) { "Mix must contain at least one frame" }
        require(outputFrames <= Int.MAX_VALUE / outputChannels) { "Mix is too large to render" }

        val output = FloatArray(outputFrames * outputChannels)
        preparedTracks.forEach { (track, buffer) -> addTrack(output, outputChannels, track, buffer) }
        val peak = output.maxOf { kotlin.math.abs(it) }
        settings.peakCeiling?.takeIf { peak > it }?.let { ceiling ->
            val gain = (ceiling / peak).toFloat()
            output.indices.forEach { index -> output[index] *= gain }
        }
        output.indices.forEach { index -> output[index] = output[index].coerceIn(-1f, 1f) }

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
            includedTracks = activeTracks.map { it.name }
        )
    }

    fun writeWav(mix: MixedStem, outputPath: Path): Path {
        val buffer = mix.buffer
        val dataSize = Math.multiplyExact(buffer.samples.size, BYTES_PER_PCM_SAMPLE)
        val byteRate = Math.multiplyExact(
            buffer.format.sampleRate,
            Math.multiplyExact(buffer.format.channels, BYTES_PER_PCM_SAMPLE)
        )
        Files.createDirectories(checkNotNull(outputPath.parent))
        DataOutputStream(BufferedOutputStream(FileOutputStream(outputPath.toFile()))).use { output ->
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
        val value = (sample.coerceIn(-1f, 1f) * PCM_24_MAX).toInt()
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
