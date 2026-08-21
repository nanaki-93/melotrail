package app.melotrail.dsp

import app.melotrail.audio.AudioBuffer
import app.melotrail.model.DSPSettings

class DSPChain(
    private val effects: List<DSPEffect> = emptyList(),
    private val wetMix: Double = 1.0
) {
    fun process(input: AudioBuffer): AudioBuffer {
        var output = input.samples.clone()

        for (effect in effects) {
            output = effect.process(output)
        }

        output = blend(input.samples, output)

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
        return blend(input, output)
    }

    private fun blend(dry: FloatArray, wet: FloatArray): FloatArray {
        val mix = wetMix.coerceIn(0.0, 1.0).toFloat()
        if (mix >= 1f) return wet
        if (mix <= 0f) return dry.clone()
        return FloatArray(dry.size) { index -> (dry[index] * (1f - mix) + wet[index] * mix).coerceIn(-1f, 1f) }
    }

    companion object {
        /**
         * Conservative LoFi chain.
         *
         * The old chain was effectively a noise generator for piano because
         * it combined severe sample-rate reduction with large injected noise,
         * incorrect compression, hardcoded 48 kHz filtering, and broken
         * modulation.
         *
         * These defaults keep the dry musical signal dominant.
         */
        fun createDefaultChain(
            settings: DSPSettings,
            sampleRate: Int = 48000,
            channels: Int = 2
        ): DSPChain {
            val effects = mutableListOf<DSPEffect>()

            if (settings.sampleRateReduction != null &&
                settings.sampleRateReduction > 1
            ) {
                effects.add(
                    SampleRateReduction(
                        factor = settings.sampleRateReduction.coerceIn(2, 16),
                        channels = channels
                    )
                )
            }

            if (settings.bitDepthReduction != null) {
                effects.add(
                    BitDepthReduction(
                        bits = settings.bitDepthReduction.coerceIn(8, 24)
                    )
                )
            }

            if (settings.warmth != 0.5) {
                effects.add(
                    ToneShaper(
                        warmth = settings.warmth,
                        brightness = 1.0 - settings.warmth
                    )
                )
            }

            if (settings.lowPassCutoff != null) {
                effects.add(
                    LowPassFilter(
                        cutoffHz = settings.lowPassCutoff,
                        sampleRate = sampleRate,
                        channels = channels
                    )
                )
            }

            if (settings.tape > 0.0) {
                effects.add(
                    TapeSaturation(
                        amount = settings.tape.coerceIn(0.0, 0.35)
                    )
                )
            }

            if (settings.compression != null &&
                settings.compression > 0.0
            ) {
                effects.add(
                    Compression(
                        amount = settings.compression.coerceIn(0.0, 0.5),
                        sampleRate = sampleRate,
                        channels = channels
                    )
                )
            }

            if (settings.softClip) {
                effects.add(SoftClip(amount = 0.12))
            }

            if (settings.wowFlutter > 0.0) {
                effects.add(
                    WowFlutter(
                        amount = settings.wowFlutter.coerceIn(0.0, 0.15),
                        sampleRate = sampleRate,
                        channels = channels
                    )
                )
            }

            /*
             * PitchDrift is intentionally not stacked with WowFlutter.
             * They both modulate time and doing both makes piano unstable.
             */

            if (settings.stereoWidth != 1.0 && channels >= 2) {
                effects.add(
                    StereoShaper(
                        width = settings.stereoWidth.toDouble()
                    )
                )
            }

            if (settings.vinyl > 0.0) {
                effects.add(
                    VinylNoise(
                        amount = settings.vinyl.coerceIn(0.0, 0.2)
                    )
                )
            }

            if (settings.noise > 0.0) {
                effects.add(
                    TapeHiss(
                        amount = settings.noise.coerceIn(0.0, 0.2)
                    )
                )
            }

            return DSPChain(effects, settings.amount)
        }
    }
}
