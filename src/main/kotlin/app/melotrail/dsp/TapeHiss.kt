package app.melotrail.dsp

import app.melotrail.model.DSPSettings

/**
 * Very quiet high-frequency hiss. It must never become the dominant signal.
 */
data class TapeHiss(
    val amount: Double = 0.01
) : DSPEffect() {
    private val random = java.util.Random(123)

    override fun process(input: FloatArray): FloatArray {
        val amountSafe = amount.coerceIn(0.0, 1.0)
        if (amountSafe <= 0.0) return input.clone()

        val output = input.clone()
        val amplitude = (amountSafe * 0.008).toFloat()

        for (i in input.indices) {
            val hiss = (random.nextFloat() * 2f - 1f) * amplitude
            output[i] = (input[i] + hiss).coerceIn(-1f, 1f)
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(noise = amount)
}
