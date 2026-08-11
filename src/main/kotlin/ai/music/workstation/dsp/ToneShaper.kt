package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class ToneShaper(
    val warmth: Double = 0.5,
    val brightness: Double = 0.5
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        val warmthBoost = warmth.toFloat() * 0.15f
        val brightnessCut = 1.0f - brightness.toFloat() * 0.1f

        for (i in input.indices) {
            // Apply warmth (low-mid boost) and brightness adjustment
            var sample = input[i]
            sample = (sample + sample * warmthBoost).coerceIn(-1.0f, 1.0f)
            sample = (sample * brightnessCut).coerceIn(-1.0f, 1.0f)
            output[i] = sample
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(warmth = warmth)
    }
}
