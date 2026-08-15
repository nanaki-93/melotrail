package app.melotrail.dsp

import app.melotrail.model.DSPSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Slow, smooth pitch drift. Unlike the old implementation this actually
 * changes the read position instead of applying random per-sample amplitude
 * changes.
 */
data class PitchDrift(
    val amount: Double = 0.02,
    val speed: Double = 0.12,
    val sampleRate: Int = 48000,
    val channels: Int = 2
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty() || amount <= 0.0) return input.clone()

        val ch = channels.coerceIn(1, 2)
        if (input.size % ch != 0) return input.clone()

        val frames = input.size / ch
        val output = FloatArray(input.size)

        fun read(frame: Double, channel: Int): Float {
            val clamped = frame.coerceIn(0.0, (frames - 1).toDouble())
            val a = clamped.toInt()
            val b = (a + 1).coerceAtMost(frames - 1)
            val frac = clamped - a
            return (
                input[a * ch + channel] +
                    (input[b * ch + channel] - input[a * ch + channel]) * frac
                ).toFloat()
        }

        val depth = amount.coerceIn(0.0, 1.0) * 0.002

        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate.coerceAtLeast(8000)
            val mod = sin(2.0 * PI * speed.coerceAtLeast(0.01) * t)
            val source = frame * (1.0 + mod * depth)

            for (c in 0 until ch) {
                output[frame * ch + c] = read(source, c)
            }
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(wowFlutter = amount)
}
