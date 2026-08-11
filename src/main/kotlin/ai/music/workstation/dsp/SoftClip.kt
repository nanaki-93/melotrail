package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class SoftClip(
    val amount: Double = 0.8
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        val threshold = 1.0f - amount.toFloat() * 0.2f

        for (i in input.indices) {
            val absInput = Math.abs(input[i])
            output[i] = when {
                absInput <= threshold -> input[i]
                else -> {
                    val sign = if (input[i] >= 0) 1f else -1f
                    val clipped = sign * (threshold + (1.0f - threshold) * (1 - Math.exp(-(absInput - threshold) * 5.0)).toFloat())
                    clipped.coerceIn(-1.0f, 1.0f)
                }
            }
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(softClip = true)
    }
}
