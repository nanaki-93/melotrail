package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Gentle wow/flutter implemented as time-varying sample interpolation.
 *
 * `sampleRate` is used as the actual time base. The previous implementation
 * derived time from input.size / 1000, so its modulation frequency changed
 * with the length of the recording.
 */
data class WowFlutter(
    val amount: Double = 0.03,
    val speed: Double = 0.6,
    val sampleRate: Int = 48000,
    val channels: Int = 2
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty() || amount <= 0.0) return input.clone()

        val ch = channels.coerceIn(1, 2)
        if (input.size % ch != 0) return input.clone()

        val frames = input.size / ch
        val output = FloatArray(input.size)
        val depthFrames =
            (sampleRate * (amount.coerceIn(0.0, 1.0) * 0.002))
                .coerceAtLeast(0.0)

        fun read(frame: Double, channel: Int): Float {
            val clamped = frame.coerceIn(0.0, (frames - 1).toDouble())
            val a = clamped.toInt()
            val b = (a + 1).coerceAtMost(frames - 1)
            val frac = clamped - a
            val va = input[a * ch + channel]
            val vb = input[b * ch + channel]
            return (va + (vb - va) * frac).toFloat()
        }

        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate.coerceAtLeast(8000)
            val modulation = sin(2.0 * PI * speed.coerceAtLeast(0.01) * t)
            val sourceFrame = frame - modulation * depthFrames

            for (c in 0 until ch) {
                output[frame * ch + c] = read(sourceFrame, c)
            }
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(wowFlutter = amount)
}
