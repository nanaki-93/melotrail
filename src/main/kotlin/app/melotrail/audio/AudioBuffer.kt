package app.melotrail.audio

import kotlinx.serialization.Serializable

@Serializable
data class AudioBuffer(
    val samples: FloatArray,
    val format: AudioFormat,
    val duration: Double
) {
    val length: Int
        get() = if (format.channels == 1) samples.size else samples.size / format.channels

    fun getSample(channel: Int, index: Int): Float {
        return samples[index * format.channels + channel]
    }

    fun monoDownmix(): AudioBuffer {
        val monoSamples = FloatArray(length)
        for (i in 0 until length) {
            var sum = 0f
            for (c in 0 until format.channels) {
                sum += getSample(c, i)
            }
            monoSamples[i] = sum / format.channels
        }
        return AudioBuffer(
            samples = monoSamples,
            format = AudioFormat(
                sampleRate = format.sampleRate,
                channels = 1,
                bitDepth = format.bitDepth,
                isFloat = format.isFloat,
                isBigEndian = format.isBigEndian,
                encoding = format.encoding
            ),
            duration = duration
        )
    }

    fun mix(other: AudioBuffer): AudioBuffer {
        if (samples.size != other.samples.size) {
            throw IllegalArgumentException("Buffer size mismatch")
        }
        val mixed = FloatArray(samples.size)
        for (i in samples.indices) {
            mixed[i] = (samples[i] + other.samples[i]) / 2f
        }
        return AudioBuffer(mixed, format, duration)
    }

    fun withGain(gain: Double): AudioBuffer {
        val scaled = samples.map { it * gain.toFloat() }.toFloatArray()
        return AudioBuffer(scaled, format, duration)
    }
}

interface AudioEngine {
    fun load(path: String): AudioBuffer
    fun save(buffer: AudioBuffer, path: String): Result<Unit>
    fun mix(tracks: List<AudioBuffer>): AudioBuffer
    fun applyGain(buffer: AudioBuffer, gain: Double): AudioBuffer
    fun applyPanning(buffer: AudioBuffer, pan: Double): AudioBuffer
}
