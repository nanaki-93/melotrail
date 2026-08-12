package ai.music.workstation.dsp

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import ai.music.workstation.model.DSPSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DSPChainTest {
    @Test
    fun `default chain should contain expected effects`() {
        val settings = DSPSettings(
            amount = 0.5,
            tape = 0.7,
            vinyl = 0.3,
            noise = 0.2,
            wowFlutter = 0.4,
            warmth = 0.7,
            sampleRateReduction = 4,
            bitDepthReduction = 14,
            lowPassCutoff = 6000.0,
            softClip = true,
            compression = 0.3,
            stereoWidth = 0.9
        )

        val chain = DSPChain.createDefaultChain(settings)
        assertEquals(4, chain.process(floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f)).size)
    }

    @Test
    fun `empty chain should pass through unchanged`() {
        val chain = DSPChain(emptyList())
        val input = floatArrayOf(0.5f, -0.3f, 0.8f)
        val output = chain.process(input)

        assertEquals(input.size, output.size)
    }

    @Test
    fun `LoFi off keeps the dry signal free of injected noise`() {
        val input = AudioBuffer(
            samples = floatArrayOf(0.15f, -0.15f, 0.3f, -0.3f),
            format = AudioFormat(32_000, 2, 24, false, false, "WAV"),
            duration = 2.0 / 32_000
        )

        val output = DSPChain.createDefaultChain(
            settings = DSPSettings(),
            sampleRate = input.format.sampleRate,
            channels = input.format.channels
        ).process(input)

        assertArrayEquals(input.samples, output.samples)
        assertEquals(input.format, output.format)
    }

    @Test
    fun `LoFi on changes samples but retains the input format`() {
        val input = AudioBuffer(
            samples = floatArrayOf(0.1f, -0.1f, 0.8f, -0.8f, -0.2f, 0.2f),
            format = AudioFormat(32_000, 2, 24, false, false, "WAV"),
            duration = 3.0 / 32_000
        )

        val output = DSPChain.createDefaultChain(
            settings = DSPSettings(sampleRateReduction = 2),
            sampleRate = input.format.sampleRate,
            channels = input.format.channels
        ).process(input)

        assertTrue(!input.samples.contentEquals(output.samples))
        assertEquals(input.format, output.format)
        assertEquals(input.length, output.length)
    }

    @Test
    fun `sample rate reduction should reduce resolution`() {
        val effect = SampleRateReduction(factor = 2)
        val input = FloatArray(100) { i -> (Math.sin(i.toDouble() * 0.1) * 0.5).toFloat() }
        val output = effect.process(input)

        assertEquals(input.size, output.size)
        // Output should have lower resolution
        var differentCount = 0
        for (i in input.indices step 2) {
            if (Math.abs(input[i] - output[i]) > 0.001) {
                differentCount++
            }
        }
        assertTrue(differentCount > 0)
    }

    @Test
    fun `bit depth reduction should quantize`() {
        val effect = BitDepthReduction(bits = 8)
        val input = floatArrayOf(0.123456f, 0.654321f, -0.987654f)
        val output = effect.process(input)

        // Values should be quantized
        val levels = ((1 shl (8 - 1)) - 1).toDouble()
        for (i in output.indices) {
            val quantized = Math.round(output[i] * levels) / levels
            assertTrue(Math.abs(output[i] - quantized) < 0.001)
        }
    }

    @Test
    fun `low pass filter should attenuate high frequencies`() {
        val effect = LowPassFilter(cutoffHz = 1000.0, sampleRate = 48000)
        val input = FloatArray(1000) { i ->
            // High frequency sine wave (above cutoff)
            Math.sin(i * 0.5).toFloat()
        }
        val output = effect.process(input)

        // Output should be attenuated
        val inputRMS = Math.sqrt(input.map { it * it }.average())
        val outputRMS = Math.sqrt(output.map { it * it }.average())
        assertTrue(outputRMS < inputRMS)
    }

    @Test
    fun `tape saturation should add non-linearity`() {
        val effect = TapeSaturation(amount = 0.5)
        val input = floatArrayOf(0.1f, 0.5f, 0.9f, -0.5f)
        val output = effect.process(input)

        // Output should be clipped/saturated
        for (i in output.indices) {
            assertTrue(output[i] >= -1.0f && output[i] <= 1.0f)
        }
    }
}
