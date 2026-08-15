package app.melotrail.dsp

import app.melotrail.model.DSPSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Gentle frame/envelope compressor for interleaved mono/stereo audio.
 */
data class Compression(
    val amount: Double = 0.1,
    val threshold: Double = -18.0,
    val ratio: Double = 2.0,
    val sampleRate: Int = 48000,
    val channels: Int = 2
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty() || amount <= 0.0) return input.clone()

        val ch = channels.coerceIn(1, 2)
        if (input.size % ch != 0) return input.clone()

        val frames = input.size / ch
        val output = input.clone()
        val safeRatio = 1.0 + (ratio - 1.0).coerceAtLeast(0.0) * amount.coerceIn(0.0, 1.0)
        val thresholdLinear = 10.0.pow(threshold / 20.0)

        val attack = exp(-1.0 / (sampleRate.coerceAtLeast(8000) * 0.025))
        val release = exp(-1.0 / (sampleRate.coerceAtLeast(8000) * 0.150))

        var env = 0.0

        for (frame in 0 until frames) {
            var peak = 0.0
            val base = frame * ch
            for (c in 0 until ch) {
                peak = max(peak, abs(input[base + c].toDouble()))
            }

            val coeff = if (peak > env) attack else release
            env = coeff * env + (1.0 - coeff) * peak

            val envDb = 20.0 * log10(max(env, 1e-12))
            val over = envDb - threshold
            val reductionDb = if (over > 0.0) {
                -(over - over / safeRatio)
            } else {
                0.0
            }
            val gain = 10.0.pow(reductionDb / 20.0)

            for (c in 0 until ch) {
                output[base + c] = (input[base + c] * gain)
                    .toFloat()
                    .coerceIn(-1f, 1f)
            }
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(compression = amount)
}
