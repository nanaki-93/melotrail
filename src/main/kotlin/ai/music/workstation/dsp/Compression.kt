package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class Compression(
    val amount: Double = 0.5,
    val threshold: Double = -12.0,
    val ratio: Double = 4.0
) : DSPEffect() {
    private val thresholdLinear: Double = 10.0 * (threshold / 20.0)

    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        var prevOutput = 0.0

        for (i in input.indices) {
            val absInput = Math.abs(input[i])
            val thresholdLinearAbs = Math.abs(thresholdLinear)

            var gainReduction = 1.0
            if (absInput > thresholdLinearAbs) {
                val excess = absInput - thresholdLinearAbs
                gainReduction = 1.0 + excess / (ratio * thresholdLinearAbs + 0.001)
            }

            val processed = input[i] * gainReduction
            // Smooth transition
            prevOutput = prevOutput * 0.95f + processed * 0.05f
            output[i] = prevOutput.toFloat().coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(compression = amount)
    }
}
