package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class TapeSaturation(
    val amount: Double = 0.5
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val drive = 1.0f + amount.toFloat() * 3.0f
        val output = FloatArray(input.size)

        for (i in input.indices) {
            val saturated = Math.tanh(input[i] * drive.toDouble()).toFloat()
            output[i] = (saturated * (1.0f - amount.toFloat() * 0.3f)).coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(tape = amount)
    }
}
