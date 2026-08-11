package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class PitchDrift(
    val amount: Double = 0.1
) : DSPEffect() {
    private val random = java.util.Random(456)

    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        val frameSize = input.size / 100

        for (i in input.indices) {
            val frameIndex = i / frameSize
            // Slow random drift per frame
            val drift = (random.nextDouble() - 0.5) * amount * 0.001
            output[i] = input[i] * (1 + drift.toFloat())
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(wowFlutter = amount)
    }
}
