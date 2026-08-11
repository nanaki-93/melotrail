package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class WowFlutter(
    val amount: Double = 0.3,
    val speed: Double = 5.0
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        val samplesPerFrame = input.size / 1000 // ~1 second at 1000 frames

        for (i in input.indices) {
            val time = i.toDouble() / samplesPerFrame
            val modulation = Math.sin(2 * Math.PI * speed * time) * amount * 0.01
            val index = (i * (1 + modulation)).toInt().coerceIn(0, input.size - 1)
            output[i] = input[index]
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(wowFlutter = amount)
    }
}
