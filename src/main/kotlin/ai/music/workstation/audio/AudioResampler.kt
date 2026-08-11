package ai.music.workstation.audio

class AudioResampler {

    companion object {
        fun resample(
            buffer: AudioBuffer,
            targetSampleRate: Int
        ): AudioBuffer {
            if (buffer.format.sampleRate == targetSampleRate) return buffer

            val ratio = targetSampleRate.toDouble() / buffer.format.sampleRate
            val newLength = (buffer.length * ratio).toInt()
            val newSamples = FloatArray(newLength)

            for (i in 0 until newLength) {
                val sourceIndex = (i / ratio).toDouble()
                val sourceIndexInt = sourceIndex.toInt()
                val fraction = sourceIndex - sourceIndexInt

                if (sourceIndexInt + 1 < buffer.length) {
                    // Linear interpolation
                    newSamples[i] = buffer.samples[sourceIndexInt] * (1f - fraction.toFloat()) +
                            buffer.samples[sourceIndexInt + 1] * fraction.toFloat()
                } else {
                    newSamples[i] = buffer.samples.lastOrNull() ?: 0f
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
