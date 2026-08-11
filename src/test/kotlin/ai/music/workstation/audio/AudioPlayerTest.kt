package ai.music.workstation.audio

import ai.music.workstation.model.ErrorReporter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AudioPlayerTest {

    @Test
    fun `playback state enum values`() {
        val states = PlaybackState.values()
        assertEquals(3, states.size)
        assertTrue(states.contains(PlaybackState.STOPPED))
        assertTrue(states.contains(PlaybackState.PLAYING))
        assertTrue(states.contains(PlaybackState.PAUSED))
    }

    @Test
    fun `audio format properties`() {
        val format = AudioFormat(44100, 2, 16, false, false, "PCM")
        assertEquals(44100, format.sampleRate)
        assertEquals(2, format.channels)
        assertEquals(16, format.bitDepth)
        assertFalse(format.isFloat)
        assertFalse(format.isBigEndian)
        assertEquals("PCM", format.encoding)
    }

    @Test
    fun `audio format float`() {
        val format = AudioFormat(48000, 1, 32, true, false, "PCM_FLOAT")
        assertEquals(48000, format.sampleRate)
        assertEquals(1, format.channels)
        assertEquals(32, format.bitDepth)
        assertTrue(format.isFloat)
    }

    private fun assertFalse(value: Boolean) {
        assertTrue(!value)
    }
}
