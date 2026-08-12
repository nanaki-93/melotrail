package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings
import kotlin.math.max

/**
 * Very low-level vinyl texture.
 *
 * `amount` is intentionally interpreted as a percentage-like control, not
 * as a raw audio amplitude. The previous implementation injected enough
 * white noise to overpower quiet piano decay.
 */
data class VinylNoise(
    val amount: Double = 0.02
) : DSPEffect() {
    private val random = java.util.Random(42)

    override fun process(input: FloatArray): FloatArray {
        val amountSafe = amount.coerceIn(0.0, 1.0)
        if (amountSafe <= 0.0) return input.clone()

        val output = input.clone()
        // Max instantaneous noise amplitude is only ~1% at amount=1.
        val amplitude = (amountSafe * 0.01).toFloat()

        // Simple filtered noise state gives a less harsh texture than raw
        // full-band white noise.
        var low = 0.0f

        for (i in input.indices) {
            val white = (random.nextFloat() * 2f - 1f)
            low = low * 0.985f + white * 0.015f
            val noise = (white * 0.25f + low * 0.75f) * amplitude
            output[i] = (input[i] + noise).coerceIn(-1f, 1f)
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(vinyl = amount)
}
