package app.melotrail.midi.adapter

import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiSourceEventIdentity
import app.melotrail.midi.domain.MidiTempoEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JdkMidiWriterTest {
    @TempDir lateinit var root: Path
    private val writer = JdkMidiWriter()
    private val reader = JdkMidiReader()

    @Test
    fun `writes deterministic format one conductor and core role tracks`() {
        val first = root.resolve("complete-first.mid")
        val second = root.resolve("complete-second.mid")

        writer.writeComplete(song(), first)
        writer.writeComplete(song(), second)

        assertContentEquals(Files.readAllBytes(first), Files.readAllBytes(second))
        val output = MidiSystem.getSequence(first.toFile())
        assertEquals(SequenceFormat.FORMAT_1, MidiSystem.getMidiFileFormat(first.toFile()).type)
        assertEquals(5, output.tracks.size)
        assertEquals(listOf("Conductor", "Melody", "Chords", "Bass", "Drums"), output.tracks.map(::trackName))
        assertEquals(listOf(0, 1, 2, 9), output.tracks.drop(1).map(::noteChannels).map(List<Int>::single))
        assertEquals(listOf("1:Intro A", "2:Verse"), output.tracks.first().meta(0x06).map { it.data.toString(Charsets.UTF_8) })
        assertEquals(480, output.tickLength)
        assertEquals(1, output.tracks.first().meta(0x51).size)
        assertEquals(1, output.tracks.first().meta(0x58).size)
        assertTrue(output.tracks.flatMap(::events).none { (it.message as? ShortMessage)?.command == ShortMessage.PROGRAM_CHANGE })
    }

    @Test
    fun `writes an aligned conductor plus one named role file`() {
        val output = root.resolve("bass.mid")

        writer.writeRole(song(), MidiExportRole.BASS, output)

        val inspected = reader.inspect(output)
        assertEquals(1, inspected.sequence.source.format)
        assertEquals(listOf("Conductor", "Bass"), inspected.trackSummaries.map { it.name })
        assertEquals(480, inspected.sourceEndTick)
        assertEquals(listOf(2), inspected.trackSummaries[1].channels.map { it.channel })
    }

    @Test
    fun `rejects forbidden generated role events and events beyond the song boundary`() {
        assertFailsWith<IllegalArgumentException> {
            MidiExportRoleTrack(MidiExportRole.BASS, listOf(MidiTempoEvent(key(0, MidiSemanticEventKind.TEMPO, 10), 500_000)))
        }

        val overrun = note(500, 600, 1)
        assertFailsWith<IllegalArgumentException> { song(roles = listOf(MidiExportRoleTrack(MidiExportRole.BASS, listOf(overrun))), end = 480) }
    }

    private fun song(roles: List<MidiExportRoleTrack> = listOf(
        MidiExportRoleTrack(MidiExportRole.MELODY, listOf(MidiControlChangeEvent(key(0, MidiSemanticEventKind.CONTROL_CHANGE, 2), 3, 64, 127), note(0, 240, 1), MidiPitchBendEvent(key(120, MidiSemanticEventKind.PITCH_BEND, 3), 3, 90), MidiChannelPressureEvent(key(180, MidiSemanticEventKind.CHANNEL_PRESSURE, 4), 3, 80))),
        MidiExportRoleTrack(MidiExportRole.CHORDS, listOf(note(0, 480, 5, 64))),
        MidiExportRoleTrack(MidiExportRole.BASS, listOf(note(0, 480, 6, 40))),
        MidiExportRoleTrack(MidiExportRole.DRUMS, listOf(note(0, 120, 7, 36))),
    ), end: Long = 480) = MidiExportSong(
        MidiPpq(480), "Arrangement", 500_000, 4, 2,
        listOf(MidiExportMarker(1, "Intro\u0000 A", 0), MidiExportMarker(2, "Verse", 240)), roles, end,
    )

    private fun note(start: Long, end: Long, id: Long, pitch: Int = 60) = MidiNoteEvent(key(start, MidiSemanticEventKind.NOTE, id), end, 7, pitch, 96, 44)

    private fun key(tick: Long, kind: MidiSemanticEventKind, id: Long) = MidiEventOrderingKey(tick, kind, generatedEventKey = id)

    private fun trackName(track: javax.sound.midi.Track): String = track.meta(0x03).single().data.toString(Charsets.UTF_8)

    private fun noteChannels(track: javax.sound.midi.Track): List<Int> = events(track).mapNotNull { (it.message as? ShortMessage)?.takeIf { message -> message.command == ShortMessage.NOTE_ON && message.data2 > 0 }?.channel }

    private fun javax.sound.midi.Track.meta(type: Int): List<MetaMessage> = events(this).mapNotNull { it.message as? MetaMessage }.filter { it.type == type }

    private fun events(track: javax.sound.midi.Track): List<javax.sound.midi.MidiEvent> = (0 until track.size()).map(track::get)

    private object SequenceFormat { const val FORMAT_1 = 1 }
}
