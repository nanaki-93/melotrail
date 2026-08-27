package app.melotrail.midi.adapter

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiReaderIssue
import app.melotrail.midi.domain.MidiReaderIssueCode
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.midi.domain.MidiMarkerEvent
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiTempoEvent
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import app.melotrail.midi.domain.MidiTrackNameEvent
import app.melotrail.midi.domain.MidiUnsupportedEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class JdkMidiReaderTest {
    @TempDir lateinit var root: Path
    private val reader = JdkMidiReader()

    @Test
    fun `all owned valid MIDI fixtures parse with their declared source facts`() {
        val paths = fixtures()

        OwnedMidiFixtures.all.filter { fixture -> fixture.kind == OwnedMidiFixtures.Kind.VALID }.forEach { fixture ->
            val result = reader.inspect(paths.getValue(fixture.fileName))

            assertEquals(fixture.expectedFormat, result.sequence.source.format, fixture.fileName)
            assertEquals(fixture.expectedTracks, result.sequence.tracks.size, fixture.fileName)
            assertEquals(fixture.sha256, result.sequence.source.sha256, fixture.fileName)
        }
    }

    @Test
    fun `reads owned SMF 0 into ordered semantic events without changing source bytes`() {
        val path = fixtures().getValue("smf0-melody.mid")
        val before = Files.readAllBytes(path)

        val result = reader.inspect(path)

        assertContentEquals(before, Files.readAllBytes(path))
        assertEquals(OwnedMidiFixtures.all.first { it.fileName == path.fileName.toString() }.sha256, result.sequence.source.sha256)
        assertEquals(0, result.sequence.source.format)
        assertEquals(480, result.sequence.source.ppq.value)
        assertEquals(480, result.sourceEndTick)
        assertEquals("Melody", result.trackSummaries.single().name)
        assertEquals(480, result.trackSummaries.single().durationTicks)
        assertEquals(1, result.trackSummaries.single().channels.single().noteCount)
        assertEquals(
            listOf(MidiTempoEvent::class, MidiTimeSignatureEvent::class, MidiTrackNameEvent::class, MidiNoteEvent::class),
            result.sequence.orderedEvents().map { event -> event::class },
        )
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `inspects SMF 1 track channels markers and supported expression facts`() {
        val references = reader.inspect(fixtures().getValue("smf1-reference-tracks.mid"))
        val expressive = reader.inspect(fixtures().getValue("expressive-controller-pitch.mid"))
        val markers = reader.inspect(fixtures().getValue("sub-bar-harmony.mid"))

        assertEquals(listOf("Conductor", "Melody", "Reference"), references.trackSummaries.map(MidiTrackSummary::name))
        assertEquals(0, references.trackSummaries[1].channels.single().channel)
        assertEquals(1, references.trackSummaries[2].channels.single().channel)
        assertEquals(1, references.trackSummaries[2].channels.single().noteCount)
        assertEquals(2, expressive.sequence.orderedEvents().filterIsInstance<MidiControlChangeEvent>().size)
        assertEquals(1, expressive.sequence.orderedEvents().filterIsInstance<MidiPitchBendEvent>().size)
        assertEquals(listOf(0L, 240L), markers.sequence.orderedEvents().filterIsInstance<MidiMarkerEvent>().map { it.orderingKey.tick })
    }

    @Test
    fun `treats note-on velocity zero as note-off and preserves release velocity`() {
        val result = reader.inspect(fixtures().getValue("velocity-zero-note-off.mid"))
        val note = result.sequence.orderedEvents().filterIsInstance<MidiNoteEvent>().single()

        assertEquals(0, note.orderingKey.tick)
        assertEquals(480, note.endTick)
        assertEquals(0, note.releaseVelocity)
        assertTrue(result.findings.isEmpty())
    }

    @Test
    fun `reports recoverable pairing findings and rejects ambiguous same-pitch overlap`() {
        val pairing = root.resolve("pairing.mid")
        writeSequence(pairing) { track ->
            track.add(MidiEvent(short(ShortMessage.NOTE_OFF, 0, 60, 0), 0))
            track.add(MidiEvent(short(ShortMessage.NOTE_ON, 0, 61, 90), 10))
        }

        val result = reader.inspect(pairing)

        assertEquals(listOf(MidiReaderIssueCode.ORPHAN_NOTE_OFF, MidiReaderIssueCode.UNCLOSED_NOTE_ON), result.findings.map(MidiReaderIssue::code))
        val ambiguous = root.resolve("ambiguous.mid")
        writeSequence(ambiguous) { track ->
            track.add(MidiEvent(short(ShortMessage.NOTE_ON, 0, 60, 90), 0))
            track.add(MidiEvent(short(ShortMessage.NOTE_ON, 0, 60, 80), 10))
        }
        assertTrue(assertFailsWith<IllegalArgumentException> { reader.inspect(ambiguous) }.message.orEmpty().contains("Ambiguous overlapping"))
    }

    @Test
    fun `captures channel pressure and records omitted channel messages as findings`() {
        val path = root.resolve("channel-messages.mid")
        writeSequence(path) { track ->
            track.add(MidiEvent(short(ShortMessage.CHANNEL_PRESSURE, 2, 55, 0), 0))
            track.add(MidiEvent(short(ShortMessage.PROGRAM_CHANGE, 2, 10, 0), 20))
        }

        val result = reader.inspect(path)

        assertEquals(1, result.sequence.orderedEvents().filterIsInstance<MidiChannelPressureEvent>().size)
        assertEquals(1, result.sequence.orderedEvents().filterIsInstance<MidiUnsupportedEvent>().size)
        assertEquals(listOf(MidiReaderIssueCode.UNSUPPORTED_MESSAGE), result.findings.map(MidiReaderIssue::code))
        assertEquals(2, result.trackSummaries.single().channels.single().channel)
    }

    @Test
    fun `rejects bounded malformed unsupported format and SMPTE sources`() {
        val paths = fixtures()

        assertTrue(assertFailsWith<IllegalArgumentException> { reader.inspect(paths.getValue("truncated-header.mid")) }.message.orEmpty().contains("unreadable"))
        assertTrue(assertFailsWith<IllegalArgumentException> { reader.inspect(paths.getValue("format-2.mid")) }.message.orEmpty().contains("format 2"))
        assertTrue(assertFailsWith<IllegalArgumentException> { reader.inspect(paths.getValue("smpte-division.mid")) }.message.orEmpty().contains("timing division"))
    }

    private fun fixtures(): Map<String, Path> = OwnedMidiFixtures.writeAll(root).associateBy { it.fileName.toString() }

    private fun writeSequence(path: Path, events: (javax.sound.midi.Track) -> Unit) {
        val sequence = Sequence(Sequence.PPQ, 480)
        events(sequence.createTrack())
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun short(command: Int, channel: Int, firstData: Int, secondData: Int) = ShortMessage(command, channel, firstData, secondData)
}
