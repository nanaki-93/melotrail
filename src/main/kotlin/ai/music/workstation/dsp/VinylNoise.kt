package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class VinylNoise(
    val amount: Double = 0.1
) : DSPEffect() {
    private val random = java.util.Random(42)

    override fun process(input: FloatArray): FloatArray {
        val output = input.clone()
        val amountF = amount.toFloat()

        for (i in input.indices) {
            // Generate pink-ish noise
            val noise = (random.nextFloat() * 2 - 1) * amountF * 0.3f
            output[i] = (output[i] + noise).coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(vinyl = amount)
    }
}
