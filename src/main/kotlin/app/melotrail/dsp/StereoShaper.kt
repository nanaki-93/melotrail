package app.melotrail.dsp

import app.melotrail.model.DSPSettings

data class StereoShaper(
    val width: Double = 1.0
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.size % 2 != 0) return input.clone()

        val output = FloatArray(input.size)
        val widthF = width.coerceIn(0.0, 2.0).toFloat()

        for (i in input.indices step 2) {
            val left = input[i]
            val right = input[i + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthF

            output[i] = (mid + side).coerceIn(-1f, 1f)
            output[i + 1] = (mid - side).coerceIn(-1f, 1f)
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(stereoWidth = width)
}
