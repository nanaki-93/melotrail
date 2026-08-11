package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

data class LowPassFilter(
    val cutoffHz: Double = 5000.0,
    val sampleRate: Int = 48000
) : DSPEffect() {
    private val normalizedCutoff: Double = cutoffHz / (sampleRate / 2.0)
    private val alpha: Double = normalizedCutoff

    override fun process(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        var prevOutput = input[0]

        for (i in input.indices) {
            prevOutput = (alpha * input[i] + (1.0 - alpha) * prevOutput).toFloat()
            output[i] = prevOutput
        }

        return output
    }

    override fun getSettings(): DSPSettings {
        return DSPSettings(lowPassCutoff = cutoffHz)
    }
}
