package ai.music.workstation.dsp

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.model.DSPSettings

class DSPChain(
    private val effects: List<DSPEffect> = emptyList()
) {
    fun process(input: AudioBuffer): AudioBuffer {
        var output = input.samples.clone()

        for (effect in effects) {
            output = effect.process(output)
        }

        return AudioBuffer(
            samples = output,
            format = input.format,
            duration = input.duration
        )
    }

    fun process(input: FloatArray): FloatArray {
        var output = input.clone()

        for (effect in effects) {
            output = effect.process(output)
        }

        return output
    }

    companion object {
        fun createDefaultChain(settings: DSPSettings): DSPChain {
            val effects = mutableListOf<DSPEffect>()

            if (settings.sampleRateReduction != null && settings.sampleRateReduction > 1) {
                effects.add(SampleRateReduction(factor = settings.sampleRateReduction))
            }
            if (settings.bitDepthReduction != null) {
                effects.add(BitDepthReduction(bits = settings.bitDepthReduction))
            }
            effects.add(ToneShaper(warmth = settings.warmth, brightness = 1.0 - settings.warmth))
            if (settings.lowPassCutoff != null) {
                effects.add(LowPassFilter(cutoffHz = settings.lowPassCutoff))
            }
            if (settings.tape > 0) {
                effects.add(TapeSaturation(amount = settings.tape))
            }
            if (settings.compression != null && settings.compression > 0) {
                effects.add(Compression(amount = settings.compression))
            }
            if (settings.softClip) {
                effects.add(SoftClip(amount = 0.8))
            }
            if (settings.wowFlutter > 0) {
                effects.add(WowFlutter(amount = settings.wowFlutter.toDouble()))
            }
            if (settings.wowFlutter > 0) {
                effects.add(PitchDrift(amount = settings.wowFlutter.toDouble()))
            }
            if (settings.stereoWidth != 1.0) {
                effects.add(StereoShaper(width = settings.stereoWidth.toDouble()))
            }
            if (settings.vinyl > 0) {
                effects.add(VinylNoise(amount = settings.vinyl))
            }
            if (settings.noise > 0) {
                effects.add(TapeHiss(amount = settings.noise))
            }

            return DSPChain(effects)
        }
    }
}
