package app.melotrail.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class ProjectSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `serialize and deserialize project`() {
        val project = Project(
            id = "test-id",
            title = "Test Project",
            artist = "Test Artist",
            tracks = listOf(
                ProjectTrack(
                    id = "track-1",
                    name = "Original",
                    type = TrackType.DRUMS,
                    filePath = "source/original.wav",
                    duration = 180.0
                )
            ),
            bpm = 120.0,
            key = "C major"
        )

        val jsonStr = json.encodeToString(project)
        val deserialized = json.decodeFromString<Project>(jsonStr)

        assertEquals(project.id, deserialized.id)
        assertEquals(project.title, deserialized.title)
        assertEquals(project.artist, deserialized.artist)
        assertEquals(project.tracks.size, deserialized.tracks.size)
        assertEquals(project.tracks[0].name, deserialized.tracks[0].name)
    }

    @Test
    fun `serialize project with empty tracks`() {
        val project = Project(
            id = "test-id",
            title = "Test Project",
            artist = "",
            tracks = emptyList()
        )

        val jsonStr = json.encodeToString(project)
        val deserialized = json.decodeFromString<Project>(jsonStr)

        assertEquals(emptyList<ProjectTrack>(), deserialized.tracks)
    }

    @Test
    fun `serialize project track`() {
        val track = ProjectTrack(
            id = "track-1",
            name = "Generated Track",
            type = TrackType.BASS,
            filePath = "generated/bass.wav",
            duration = 60.0
        )

        val jsonStr = json.encodeToString(track)
        val deserialized = json.decodeFromString<ProjectTrack>(jsonStr)

        assertEquals("track-1", deserialized.id)
        assertEquals("Generated Track", deserialized.name)
        assertEquals(TrackType.BASS, deserialized.type)
    }

    @Test
    fun `serialize DSP settings`() {
        val dspSettings = DSPSettings(
            amount = 0.5,
            tape = 0.3,
            vinyl = 0.2,
            noise = 0.1,
            warmth = 0.8,
            stereoWidth = 1.5
        )

        val jsonStr = json.encodeToString(dspSettings)
        val deserialized = json.decodeFromString<DSPSettings>(jsonStr)

        assertEquals(0.5, deserialized.amount)
        assertEquals(0.3, deserialized.tape)
        assertEquals(0.2, deserialized.vinyl)
        assertEquals(0.1, deserialized.noise)
        assertEquals(0.8, deserialized.warmth)
        assertEquals(1.5, deserialized.stereoWidth)
    }

    @Test
    fun `serialize track type enum`() {
        val jsonStr = json.encodeToString(TrackType.DRUMS)
        assertEquals("\"DRUMS\"", jsonStr)

        val deserialized = json.decodeFromString<TrackType>(jsonStr)
        assertEquals(TrackType.DRUMS, deserialized)
    }

    @Test
    fun `serialize export format enum`() {
        val jsonStr = json.encodeToString(ExportFormat.WAV)
        assertEquals("\"WAV\"", jsonStr)

        val deserialized = json.decodeFromString<ExportFormat>(jsonStr)
        assertEquals(ExportFormat.WAV, deserialized)
    }

    @Test
    fun `deserialize project with missing optional fields uses defaults`() {
        val minimalJson = """
            {
                "id": "test-id",
                "title": "Test",
                "artist": "",
                "tracks": [],
                "bpm": 0.0,
                "key": ""
            }
        """.trimIndent()

        val project = json.decodeFromString<Project>(minimalJson)

        assertEquals("test-id", project.id)
        assertEquals("Test", project.title)
        assertEquals("", project.artist)
        assertEquals(0, project.tracks.size)
    }

    @Test
    fun `serialize mastering state`() {
        val masteringState = MasteringState()

        val jsonStr = json.encodeToString(masteringState)
        val deserialized = json.decodeFromString<MasteringState>(jsonStr)

        assertTrue(deserialized.eqEnabled)
        assertTrue(deserialized.compressorEnabled)
        assertTrue(deserialized.limiterEnabled)
    }

    @Test
    fun `project addTrack and removeTrack`() {
        val project = Project(
            id = "test-id",
            title = "Test",
            tracks = emptyList()
        )

        val track = ProjectTrack(
            id = "track-1",
            name = "New Track",
            type = TrackType.GUITAR
        )

        val withTrack = project.addTrack(track)
        assertEquals(1, withTrack.tracks.size)

        val withoutTrack = withTrack.removeTrack("track-1")
        assertEquals(0, withoutTrack.tracks.size)
    }

    @Test
    fun `project updateTrack`() {
        val track = ProjectTrack(
            id = "track-1",
            name = "Original",
            type = TrackType.DRUMS
        )
        val project = Project(
            id = "test-id",
            title = "Test",
            tracks = listOf(track)
        )

        val updated = project.updateTrack("track-1") {
            it.withGain(0.8).withMuted(true)
        }

        assertEquals(0.8, updated.tracks[0].gain)
        assertTrue(updated.tracks[0].muted)
        assertEquals("Original", updated.tracks[0].name)
    }
}
