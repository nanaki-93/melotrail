package app.melotrail.audio

import app.melotrail.model.ErrorReporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AudioBufferTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `create mono audio buffer`() {
        val samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 4 * 1.0 / 44100)

        assertEquals(4, buffer.length)
        assertEquals(44100, buffer.format.sampleRate)
        assertEquals(1, buffer.format.channels)
    }

    @Test
    fun `create stereo audio buffer`() {
        val samples = floatArrayOf(0.5f, 0.3f, -0.5f, -0.3f, 0.25f, 0.15f)
        val format = AudioFormat(44100, 2, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 3 * 1.0 / 44100)

        assertEquals(6, buffer.samples.size)
        assertEquals(44100, buffer.format.sampleRate)
        assertEquals(2, buffer.format.channels)
        assertEquals(3, buffer.length) // samples per channel
    }

    @Test
    fun `get mono sample from mono buffer`() {
        val samples = floatArrayOf(0.5f, -0.5f, 0.25f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 3.0 / 44100)

        assertEquals(0.5f, buffer.getSample(0, 0))
        assertEquals(-0.5f, buffer.getSample(0, 1))
        assertEquals(0.25f, buffer.getSample(0, 2))
    }

    @Test
    fun `get sample at specific channel from stereo buffer`() {
        val samples = floatArrayOf(0.6f, 0.4f, -0.6f, -0.4f)
        val format = AudioFormat(44100, 2, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 2.0 / 44100)

        assertEquals(0.6f, buffer.getSample(0, 0)) // Left channel, first sample
        assertEquals(0.4f, buffer.getSample(1, 0)) // Right channel, first sample
        assertEquals(-0.6f, buffer.getSample(0, 1)) // Left channel, second sample
    }

    @Test
    fun `mono downmix converts stereo to mono`() {
        val samples = floatArrayOf(0.6f, 0.4f, -0.6f, -0.4f)
        val format = AudioFormat(44100, 2, 16, false, false, "PCM")
        val stereoBuffer = AudioBuffer(samples, format, 2.0 / 44100)

        val monoBuffer = stereoBuffer.monoDownmix()

        assertEquals(2, monoBuffer.length)
        assertEquals(1, monoBuffer.format.channels)
        assertEquals(0.5f, monoBuffer.samples[0]) // Average of 0.6 and 0.4
        assertEquals(-0.5f, monoBuffer.samples[1]) // Average of -0.6 and -0.4
    }

    @Test
    fun `withGain scales samples`() {
        val samples = floatArrayOf(0.5f, -0.5f, 0.25f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 3.0 / 44100)

        val gainApplied = buffer.withGain(2.0)

        assertEquals(1.0f, gainApplied.samples[0]) // 0.5 * 2
        assertEquals(-1.0f, gainApplied.samples[1]) // -0.5 * 2
        assertEquals(0.5f, gainApplied.samples[2]) // 0.25 * 2
        // Original unchanged
        assertEquals(0.5f, buffer.samples[0])
    }

    @Test
    fun `mix combines two buffers`() {
        val samples1 = floatArrayOf(0.6f, 0.4f)
        val samples2 = floatArrayOf(0.2f, 0.8f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer1 = AudioBuffer(samples1, format, 2.0 / 44100)
        val buffer2 = AudioBuffer(samples2, format, 2.0 / 44100)

        val mixed = buffer1.mix(buffer2)

        assertEquals(0.4f, mixed.samples[0]) // (0.6 + 0.2) / 2
        assertEquals(0.6f, mixed.samples[1]) // (0.4 + 0.8) / 2
    }

    @Test
    fun `buffer content comparison`() {
        val samples1 = floatArrayOf(0.5f, -0.5f)
        val samples2 = floatArrayOf(0.5f, -0.5f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer1 = AudioBuffer(samples1, format, 2.0 / 44100)
        val buffer2 = AudioBuffer(samples2, format, 2.0 / 44100)

        // Check that samples arrays are equal
        assertTrue(buffer1.samples.contentEquals(buffer2.samples))
    }

    @Test
    fun `buffer content differs with different samples`() {
        val samples1 = floatArrayOf(0.5f, -0.5f)
        val samples2 = floatArrayOf(0.5f, 0.5f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer1 = AudioBuffer(samples1, format, 2.0 / 44100)
        val buffer2 = AudioBuffer(samples2, format, 2.0 / 44100)

        // Check that samples arrays are not equal
        assertTrue(!buffer1.samples.contentEquals(buffer2.samples))
    }

    @Test
    fun `buffer preserves duration`() {
        val samples = floatArrayOf(0.5f, -0.5f)
        val format = AudioFormat(44100, 1, 16, false, false, "PCM")
        val buffer = AudioBuffer(samples, format, 2.0 / 44100)

        assertEquals(2.0 / 44100, buffer.duration, 0.0001)
    }
}
