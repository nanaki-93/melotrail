package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class BitDepthReduction(
    val bits: Int = 12
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val levels = (1 shl bits).toDouble()
        val output = FloatArray(input.size)
        for (i in input.indices) {
            val quantized = Math.round(input[i] * levels) / levels
            output[i] = quantized.toFloat().coerceIn(-1.0f, 1.0f)
        }
        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(bitDepthReduction = bits)
    }
}
