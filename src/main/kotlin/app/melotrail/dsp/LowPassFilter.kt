package app.melotrail.dsp

import app.melotrail.model.DSPSettings
import kotlin.math.PI
import kotlin.math.exp

/**
 * One-pole low-pass that supports interleaved mono/stereo samples.
 *
 * The cutoff is clamped against the supplied sample rate. The old
 * implementation always assumed 48 kHz and could therefore behave incorrectly
 * on 44.1 kHz material.
 */
data class LowPassFilter(
    val cutoffHz: Double = 12000.0,
    val sampleRate: Int = 48000,
    val channels: Int = 2
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input.clone()

        val ch = channels.coerceIn(1, 2)
        if (input.size % ch != 0) return input.clone()

        val cutoff = cutoffHz.coerceIn(
            20.0,
            sampleRate * 0.45
        )

        val dt = 1.0 / sampleRate.coerceAtLeast(8000)
        val rc = 1.0 / (2.0 * PI * cutoff)
        val alpha = (dt / (rc + dt)).coerceIn(0.0, 1.0)

        val output = FloatArray(input.size)
        val previous = DoubleArray(ch)

        for (c in 0 until ch) {
            previous[c] = input[c].toDouble()
        }

        for (frame in input.indices step ch) {
            for (c in 0 until ch) {
                val index = frame + c
                val y = previous[c] + alpha * (input[index] - previous[c])
                previous[c] = y
                output[index] = y.toFloat()
            }
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(lowPassCutoff = cutoffHz)
}
