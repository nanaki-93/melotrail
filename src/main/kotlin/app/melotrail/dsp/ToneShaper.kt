package app.melotrail.dsp

import app.melotrail.model.DSPSettings

/**
 * Very subtle tonal tilt. This is deliberately much less aggressive than the
 * previous per-sample gain boost/cut.
 */
data class ToneShaper(
    val warmth: Double = 0.5,
    val brightness: Double = 0.5
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val warmthSafe = warmth.coerceIn(0.0, 1.0)
        val brightnessSafe = brightness.coerceIn(0.0, 1.0)

        val gain =
            1.0 +
                (warmthSafe - 0.5) * 0.08 -
                (0.5 - brightnessSafe) * 0.04

        return FloatArray(input.size) { i ->
            (input[i] * gain).toFloat().coerceIn(-1f, 1f)
        }
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(warmth = warmth)
}
