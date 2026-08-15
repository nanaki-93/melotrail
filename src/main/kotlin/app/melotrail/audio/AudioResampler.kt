package app.melotrail.audio

class AudioResampler {

    companion object {
        fun resample(
            buffer: AudioBuffer,
            targetSampleRate: Int
        ): AudioBuffer {
            if (buffer.format.sampleRate == targetSampleRate) return buffer

            val ratio = targetSampleRate.toDouble() / buffer.format.sampleRate
            val newLength = (buffer.length * ratio).toInt()
            val newSamples = FloatArray(newLength * buffer.format.channels)

            for (i in 0 until newLength) {
                val sourceIndex = (i / ratio)
                val sourceIndexInt = sourceIndex.toInt()
                val fraction = (sourceIndex - sourceIndexInt).toFloat()

                for (c in 0 until buffer.format.channels) {
                    val srcPos = sourceIndexInt * buffer.format.channels + c
                    val destPos = i * buffer.format.channels + c

                    if (srcPos + buffer.format.channels < buffer.samples.size) {
                        newSamples[destPos] = buffer.samples[srcPos] * (1f - fraction) +
                                buffer.samples[srcPos + buffer.format.channels] * fraction
                    } else if (srcPos < buffer.samples.size) {
                        newSamples[destPos] = buffer.samples[srcPos]
                    } else {
                        newSamples[destPos] = 0f
                    }
                }
            }

            val duration = newLength.toDouble() / targetSampleRate

            return AudioBuffer(
                samples = newSamples,
                format = buffer.format.copy(sampleRate = targetSampleRate),
                duration = duration
            )
        }

        fun resampleMono(
            samples: FloatArray,
            sourceSampleRate: Int,
            targetSampleRate: Int
        ): FloatArray {
            if (sourceSampleRate == targetSampleRate) return samples

            val ratio = targetSampleRate.toDouble() / sourceSampleRate
            val newLength = (samples.size * ratio).toInt()
            val newSamples = FloatArray(newLength)

            for (i in 0 until newLength) {
                val sourceIndex = (i / ratio).toDouble()
                val sourceIndexInt = sourceIndex.toInt()
                val fraction = sourceIndex - sourceIndexInt

                if (sourceIndexInt + 1 < samples.size) {
                    newSamples[i] = samples[sourceIndexInt] * (1f - fraction.toFloat()) +
                            samples[sourceIndexInt + 1] * fraction.toFloat()
                } else {
                    newSamples[i] = samples.lastOrNull() ?: 0f
                }
            }

            return newSamples
        }
    }
}
