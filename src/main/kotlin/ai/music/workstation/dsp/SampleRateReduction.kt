package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class SampleRateReduction(
    val factor: Int = 4
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (factor <= 1) return input.clone()

        // Downsample
        val downsampled = FloatArray((input.size + factor - 1) / factor)
        for (i in downsampled.indices) {
            downsampled[i] = input[i * factor]
        }

        // Upsample
        val output = FloatArray(input.size)
        for (i in output.indices) {
            val srcIndex = (i * factor) / factor
            output[i] = downsampled.getOrElse(srcIndex) { 0f }
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(sampleRateReduction = factor)
    }
}
