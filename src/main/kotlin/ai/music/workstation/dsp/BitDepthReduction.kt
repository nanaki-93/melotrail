package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings
import kotlin.math.round

/**
 * Conservative quantizer. Uses the actual number of quantization steps
 * instead of unnecessarily pushing values outside the requested range.
 */
data class BitDepthReduction(
    val bits: Int = 16
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val safeBits = bits.coerceIn(8, 24)
        val levels = ((1 shl (safeBits - 1)) - 1).toDouble()
        val output = FloatArray(input.size)

        for (i in input.indices) {
            output[i] = (round(input[i].coerceIn(-1f, 1f) * levels) / levels)
                .toFloat()
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(bitDepthReduction = bits)
}
