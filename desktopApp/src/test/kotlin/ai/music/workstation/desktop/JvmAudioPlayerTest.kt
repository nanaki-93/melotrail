package ai.music.workstation.desktop

import ai.music.workstation.audio.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmAudioPlayerTest {
    @Test
    fun `volume and stopped state are bounded and close safely without an audio device`() {
        val player = JvmAudioPlayer()
        player.setVolume(2.0)
        assertEquals(1.0, player.volume.value)
        player.setVolume(-1.0)
        assertEquals(0.0, player.volume.value)
        player.seek(4.0)
        player.stop()
        assertEquals(PlaybackState.STOPPED, player.state.value)
        assertEquals(0.0, player.currentPosition.value)
        player.close()
    }
}
