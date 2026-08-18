package app.melotrail.audio

import app.melotrail.dsp.DSPChain
import app.melotrail.dsp.LOFIPresets
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin

class AudioComparisonTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `identical mono stereo and high-rate inputs have zero difference`() {
        listOf(
            fixture(sampleRate = 44_100, channels = 1),
            fixture(sampleRate = 48_000, channels = 2),
            fixture(sampleRate = 96_000, channels = 2)
        ).forEach { audio ->
            val report = AudioComparison.compare(audio, audio)

            assertEquals(0.0, report.meanAbsoluteSampleDifference)
            assertEquals(0.0, report.maxAbsoluteSampleDifference)
            assertEquals(0.0, report.changedFrameRatio)
            assertEquals(0.0, report.nullDifferenceRms)
            assertEquals(0.0, report.spectralCentroidDeltaHz)
        }
    }

    @Test
    fun `known gain and one-sample changes produce expected measurements`() {
        val base = buffer(48_000, 1, floatArrayOf(0.1f, -0.1f, 0.2f, -0.2f))
        val gained = buffer(48_000, 1, floatArrayOf(0.2f, -0.2f, 0.4f, -0.4f))
        val gainReport = AudioComparison.compare(base, gained)
        assertTrue(gainReport.b.rms > gainReport.a.rms)
        assertEquals(6.0206, gainReport.rmsDeltaDb, 0.001)
        assertTrue(gainReport.peakAbsoluteDelta > 0.19)

        val changed = buffer(48_000, 1, floatArrayOf(0.1f, -0.1f, 0.45f, -0.2f))
        val sampleReport = AudioComparison.compare(base, changed)
        assertEquals(0.0625, sampleReport.meanAbsoluteSampleDifference, 1.0e-7)
        assertEquals(0.25, sampleReport.maxAbsoluteSampleDifference, 1.0e-7)
        assertEquals(0.25, sampleReport.changedFrameRatio, 1.0e-7)
    }

    @Test
    fun `channel and spectral changes preserve channel-aware frame measurements`() {
        val source = fixture(sampleRate = 48_000, channels = 2)
        val channelChanged = source.copy(samples = source.samples.clone().also { it[3] *= 0.5f })
        val channelReport = AudioComparison.compare(source, channelChanged)
        assertEquals(1.0 / source.length, channelReport.changedFrameRatio, 1.0e-9)

        val filtered = source.copy(samples = source.samples.clone())
        for (frame in 0 until filtered.length) {
            val low = sin(2.0 * PI * 300.0 * frame / filtered.format.sampleRate).toFloat() * 0.35f
            val high = sin(2.0 * PI * 8_000.0 * frame / filtered.format.sampleRate).toFloat() * 0.35f
            filtered.samples[frame * 2] = low
            filtered.samples[frame * 2 + 1] = low
            source.samples[frame * 2] = (low + high).coerceIn(-1f, 1f)
            source.samples[frame * 2 + 1] = (low + high).coerceIn(-1f, 1f)
        }
        val spectralReport = AudioComparison.compare(source, filtered)
        assertTrue(spectralReport.spectralCentroidDeltaHz < 0.0)
        assertTrue(spectralReport.highBandEnergyDeltaDb < 0.0)
    }

    @Test
    fun `mismatched timelines and formats reject unless diagnostic alignment is explicit`() {
        val a = fixture(sampleRate = 44_100, channels = 1, frames = 2_048)
        val shorter = fixture(sampleRate = 44_100, channels = 1, frames = 1_024)
        assertThrows(IllegalArgumentException::class.java) { AudioComparison.compare(a, shorter) }

        val aligned = AudioComparison.compare(a, shorter, allowAlignment = true)
        assertTrue(aligned.alignmentMismatch)
        assertEquals(1_024, aligned.comparedFrameCount)

        assertThrows(IllegalArgumentException::class.java) {
            AudioComparison.compare(a, fixture(sampleRate = 48_000, channels = 1, frames = 2_048))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioComparison.compare(a, fixture(sampleRate = 44_100, channels = 2, frames = 2_048))
        }
    }

    @Test
    fun `non-finite and malformed inputs are rejected`() {
        val valid = fixture(sampleRate = 44_100, channels = 1)
        val nonFinite = valid.copy(samples = valid.samples.clone().also { it[0] = Float.NaN })
        assertThrows(IllegalArgumentException::class.java) { AudioComparison.compare(valid, nonFinite) }

        val malformed = tempDir.resolve("malformed.wav")
        Files.writeString(malformed, "not a WAV")
        assertThrows(Exception::class.java) { AudioComparison.compareFiles(malformed, malformed) }
    }

    @Test
    fun `Bedroom LoFi is deterministic format preserving and bounded`() {
        val dry = fixture(sampleRate = 44_100, channels = 2, frames = 4_096)
        val preset = checkNotNull(LOFIPresets.getByName("Bedroom LoFi"))
        val first = DSPChain.createDefaultChain(preset.settings, dry.format.sampleRate, dry.format.channels).process(dry)
        val second = DSPChain.createDefaultChain(preset.settings, dry.format.sampleRate, dry.format.channels).process(dry)
        val report = AudioComparison.compare(dry, first)

        assertArrayEquals(first.samples, second.samples)
        assertEquals(dry.format, first.format)
        assertEquals(dry.length, first.length)
        assertTrue(first.samples.all { it.isFinite() && kotlin.math.abs(it) < 0.999f })
        assertTrue(report.changedFrameRatio > 0.01)
        assertTrue(report.meanAbsoluteSampleDifference < 0.35)
    }

    private fun fixture(sampleRate: Int, channels: Int, frames: Int = 4_096): AudioBuffer {
        val samples = FloatArray(frames * channels)
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * 300.0 * frame / sampleRate) * 0.32 +
                sin(2.0 * PI * 7_500.0 * frame / sampleRate) * 0.18).toFloat()
            for (channel in 0 until channels) samples[frame * channels + channel] = value * (1.0f - channel * 0.1f)
        }
        return buffer(sampleRate, channels, samples)
    }

    private fun buffer(sampleRate: Int, channels: Int, samples: FloatArray): AudioBuffer =
        AudioBuffer(samples, AudioFormat(sampleRate, channels, 24, false, false, "WAV"), samples.size.toDouble() / channels / sampleRate)

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
