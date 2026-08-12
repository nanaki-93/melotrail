package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class StemMixerTest {
    @TempDir
    lateinit var outputDirectory: Path

    @Test
    fun `piano only mix preserves frames sample rate and WAV output`() {
        val piano = buffer(44_100, 1, 0.25f, -0.25f)
        val mixer = DeterministicStemMixer()

        val mix = mixer.mix(listOf(MixTrack("piano", piano)))
        val output = mixer.writeWav(mix, outputDirectory.resolve("mix/mix.wav"))

        assertEquals(44_100, mix.buffer.format.sampleRate)
        assertEquals(1, mix.buffer.format.channels)
        assertEquals(2, mix.buffer.length)
        assertEquals(0.25f, mix.buffer.samples[0])
        assertEquals(-0.25f, mix.buffer.samples[1])
        val header = ByteBuffer.wrap(Files.readAllBytes(output)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", Files.readAllBytes(output).copyOfRange(0, 4).decodeToString())
        assertEquals(44_100, header.getInt(24))
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(24, header.getShort(34).toInt())
    }

    @Test
    fun `piano and bass mix applies gain pan mute dry mode and safe clipping`() {
        val piano = buffer(44_100, 1, 0.8f, 0.8f)
        val bass = buffer(44_100, 2, 0.8f, 0.8f, 0.8f, 0.8f)
        val mixer = DeterministicStemMixer()

        val full = mixer.mix(
            listOf(
                MixTrack("piano", piano),
                MixTrack("bass", bass, pan = 1.0, generated = true),
                MixTrack("muted", piano, muted = true),
                MixTrack("quiet", piano, gainDb = -6.0, startFrame = 1)
            )
        )
        val dry = mixer.mix(
            listOf(MixTrack("piano", piano), MixTrack("bass", bass, generated = true)),
            MixSettings(dry = true)
        )

        assertEquals(2, full.buffer.format.channels)
        assertEquals(0.8f, full.buffer.getSample(0, 0))
        assertEquals(1.0f, full.buffer.getSample(1, 0))
        assertEquals(listOf("piano"), dry.includedTracks)
        assertEquals(0.8f, dry.buffer.getSample(0, 0))
        assertEquals(0.8f, dry.buffer.getSample(1, 0))
    }

    @Test
    fun `mono and stereo tracks resample explicitly and place frames on timeline`() {
        val stereo = buffer(4, 2, 0.1f, 0.2f, 0.3f, 0.4f)
        val mono = buffer(2, 1, 0f, 1f)

        val mix = DeterministicStemMixer().mix(
            listOf(
                MixTrack("stereo", stereo),
                MixTrack("mono", mono, startFrame = 1)
            )
        )

        assertEquals(4, mix.buffer.format.sampleRate)
        assertEquals(2, mix.buffer.format.channels)
        assertEquals(5, mix.buffer.length)
        assertEquals(0.1f, mix.buffer.getSample(0, 0))
        assertEquals(0.2f, mix.buffer.getSample(1, 0))
        assertTrue(mix.buffer.getSample(0, 2) > mix.buffer.getSample(0, 1))
        assertEquals(mix.buffer.getSample(0, 2), mix.buffer.getSample(1, 2))
    }

    private fun buffer(sampleRate: Int, channels: Int, vararg samples: Float): AudioBuffer = AudioBuffer(
        samples = samples,
        format = AudioFormat(sampleRate, channels, 24, false, false, "WAV"),
        duration = samples.size.toDouble() / channels / sampleRate
    )
}
