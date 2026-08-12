package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings

/**
 * Deliberate sample-and-hold rate reduction.
 *
 * The previous implementation indexed the downsampled array with the
 * original sample index, causing almost the entire output to become zero.
 *
 * This implementation works with interleaved stereo/mono samples. A frame
 * contains one sample per channel; channel layout is preserved.
 */
data class SampleRateReduction(
    val factor: Int = 2,
    val channels: Int = 2
) : DSPEffect() {
    override fun process(input: FloatArray): FloatArray {
        if (factor <= 1 || input.isEmpty()) return input.clone()

        val ch = channels.coerceIn(1, 2)
        if (input.size % ch != 0) return input.clone()

        val frames = input.size / ch
        val output = input.clone()

        var frame = 0
        while (frame < frames) {
            val sourceFrame = (frame / factor) * factor
            val sourceBase = sourceFrame * ch
            val outBase = frame * ch

            for (channel in 0 until ch) {
                output[outBase + channel] = input[sourceBase + channel]
            }
            frame++
        }

        return output
    }

    override fun getSettings(): DSPSettings =
        DSPSettings(sampleRateReduction = factor)
}
