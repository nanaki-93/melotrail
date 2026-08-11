package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class StereoShaper(
    val width: Double = 1.0
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (input.size % 2 != 0) return input.clone()

        val output = FloatArray(input.size)
        val half = input.size / 2
        val widthF = width.toFloat()

        for (i in 0 until half) {
            val left = input[i * 2]
            val right = input[i * 2 + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f

            val newSide = side * widthF

            output[i * 2] = (mid + newSide).coerceIn(-1.0f, 1.0f)
            output[i * 2 + 1] = (mid - newSide).coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(stereoWidth = width)
    }
}
