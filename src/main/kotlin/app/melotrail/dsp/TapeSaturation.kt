package app.melotrail.dsp

import app.melotrail.model.DSPSettings
import kotlin.math.tanh

/**
 * Parallel, unity-ish tape saturation. Low amounts should preserve the
 * original piano instead of replacing it with a heavily driven waveform.
 */
data class TapeSaturation(
    val amount: Double = 0.1
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val amountSafe = amount.coerceIn(0.0, 1.0)
        if (amountSafe <= 0.0) return input.clone()

        val drive = 1.0 + amountSafe * 1.5
        val normalizer = tanh(drive)
        val output = FloatArray(input.size)

        for (i in input.indices) {
            val dry = input[i].toDouble()
            val wet = tanh(dry * drive) / normalizer
            output[i] = (dry * (1.0 - amountSafe) + wet * amountSafe)
                .toFloat()
                .coerceIn(-1f, 1f)
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(tape = amount)
}
