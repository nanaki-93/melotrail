package ai.music.workstation.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ProjectMigrationTest {

    @Test
    fun `create project with defaults`() {
        val project = Project(
            id = "test-id",
            title = "Test Project",
            artist = "Unknown Artist"
        )

        assertEquals("test-id", project.id)
        assertEquals("Test Project", project.title)
        assertEquals("Unknown Artist", project.artist)
        assertEquals(0.0, project.bpm)
        assertEquals("", project.key)
        assertEquals(0, project.tracks.size)
    }

    @Test
    fun `addTrack adds to track list`() {
        val project = Project(
            id = "test-id",
            title = "Test",
            tracks = emptyList()
        )

        val newTrack = ProjectTrack(
            id = "track-1",
            name = "New Track",
            type = TrackType.DRUMS
        )

        val updated = project.addTrack(newTrack)

        assertEquals(1, updated.tracks.size)
        assertEquals("New Track", updated.tracks[0].name)
        assertEquals(0, project.tracks.size) // Original unchanged
    }

    @Test
    fun `removeTrack removes from track list`() {
        val track1 = ProjectTrack(
            id = "track-1",
            name = "Track 1",
            type = TrackType.DRUMS
        )
        val track2 = ProjectTrack(
            id = "track-2",
            name = "Track 2",
            type = TrackType.BASS
        )
        val project = Project(
            id = "test-id",
            title = "Test",
            tracks = listOf(track1, track2)
        )

        val updated = project.removeTrack("track-1")

        assertEquals(1, updated.tracks.size)
        assertEquals("track-2", updated.tracks[0].id)
    }

    @Test
    fun `updateTrack modifies specific track`() {
        val track = ProjectTrack(
            id = "track-1",
            name = "Original",
            type = TrackType.GUITAR
        )
        val project = Project(
            id = "test-id",
            title = "Test",
            tracks = listOf(track)
        )

        val updated = project.updateTrack("track-1") {
            it.withGain(0.75).withMuted(true)
        }

        assertEquals(0.75, updated.tracks[0].gain)
        assertTrue(updated.tracks[0].muted)
        assertEquals("Original", updated.tracks[0].name) // Name unchanged
    }

    @Test
    fun `dsp settings defaults`() {
        val settings = DSPSettings()

        assertEquals(0.5, settings.amount)
        assertEquals(0.0, settings.tape)
        assertEquals(0.0, settings.vinyl)
        assertEquals(0.0, settings.noise)
        assertEquals(0.5, settings.warmth)
        assertEquals(1.0, settings.stereoWidth)
    }

    @Test
    fun `dsp settings with custom values`() {
        val settings = DSPSettings(
            amount = 0.8,
            tape = 0.6,
            vinyl = 0.4,
            warmth = 0.9,
            stereoWidth = 1.2
        )

        assertEquals(0.8, settings.amount)
        assertEquals(0.6, settings.tape)
        assertEquals(0.4, settings.vinyl)
        assertEquals(0.9, settings.warmth)
        assertEquals(1.2, settings.stereoWidth)
    }

    @Test
    fun `project track with gain and mute`() {
        val track = ProjectTrack(
            id = "track-1",
            name = "Test Track",
            type = TrackType.PADS
        )

        val withGain = track.withGain(0.5)
        assertEquals(0.5, withGain.gain)
        assertEquals(0.0, track.gain) // Original unchanged

        val withMute = withGain.withMuted(true)
        assertTrue(withMute.muted)
        assertEquals(0.5, withGain.gain) // Gain preserved
    }

    @Test
    fun `project track with pan`() {
        val track = ProjectTrack(
            id = "track-1",
            name = "Test Track",
            type = TrackType.VOCALS
        )

        val panned = track.withPan(-0.5)
        assertEquals(-0.5, panned.pan)
    }

    @Test
    fun `track type enum values`() {
        val types = TrackType.values()
        assertEquals(9, types.size)
        assertTrue(types.contains(TrackType.DRUMS))
        assertTrue(types.contains(TrackType.BASS))
        assertTrue(types.contains(TrackType.GUITAR))
    }

    @Test
    fun `track type from name`() {
        assertEquals(TrackType.DRUMS, TrackType.fromName("DRUMS"))
        assertEquals(TrackType.BASS, TrackType.fromName("bass"))
        assertEquals(TrackType.OTHER, TrackType.fromName("Unknown"))
    }

    @Test
    fun `dsp settings defaults object`() {
        val defaults = DSPSettingsDefaults.DEFAULT
        assertEquals(0.5, defaults.amount)
        assertEquals(0.0, defaults.tape)
    }
}
