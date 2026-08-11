package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class TapeHiss(
    val amount: Double = 0.05
) : DSPEffect() {
    private val random = java.util.Random(123)

    override fun process(input: FloatArray): FloatArray {
        val output = input.clone()
        val amountF = amount.toFloat()

        for (i in input.indices) {
            // White noise
            val hiss = (random.nextFloat() * 2 - 1) * amountF * 0.1f
            output[i] = (output[i] + hiss).coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(noise = amount)
    }
}
