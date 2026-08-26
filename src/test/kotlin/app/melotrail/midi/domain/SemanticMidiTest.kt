package app.melotrail.midi.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SemanticMidiTest {
    private val source = MidiSourceIdentity(
        sha256 = "a".repeat(64),
        originalFilename = "melody.mid",
        format = 1,
        ppq = MidiPpq(480),
    )

    @Test
    fun `semantic sequence applies the documented stable ordering and protects caller collections`() {
        val unordered = mutableListOf(
            note(tick = 0, sourceTrack = 1, sourceIndex = 4, endTick = 480),
            MidiTempoEvent(key(0, MidiSemanticEventKind.TEMPO, 1, 2), 500_000),
            MidiMarkerEvent(key(0, MidiSemanticEventKind.MARKER, 1, 3), "1:Verse"),
        )
        val track = SemanticMidiTrack(1, unordered)
        unordered.clear()
        val sequence = SemanticMidiSequence(source, listOf(track, SemanticMidiTrack(0, emptyList())))

        assertEquals(
            listOf(MidiSemanticEventKind.TEMPO, MidiSemanticEventKind.MARKER, MidiSemanticEventKind.NOTE),
            sequence.orderedEvents().map(SemanticMidiEvent::kind),
        )
        assertEquals(480, sequence.endTick)
        assertFailsWith<UnsupportedOperationException> { (track.events as MutableList<SemanticMidiEvent>).clear() }
        assertFailsWith<UnsupportedOperationException> { (sequence.tracks as MutableList<SemanticMidiTrack>).clear() }
    }

    @Test
    fun `event identity and value ranges reject unsafe semantic data`() {
        assertFailsWith<IllegalArgumentException> { MidiSourceIdentity("A".repeat(64), "a.mid", 1, MidiPpq(480)) }
        assertFailsWith<IllegalArgumentException> { MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE) }
        assertFailsWith<IllegalArgumentException> { MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE, MidiSourceEventIdentity(0, 0), 0) }
        assertFailsWith<IllegalArgumentException> { MidiNoteEvent(key(10, MidiSemanticEventKind.NOTE, 0, 0), 10, 0, 60, 100) }
        assertFailsWith<IllegalArgumentException> { MidiControlChangeEvent(key(0, MidiSemanticEventKind.CONTROL_CHANGE, 0, 0), 16, 1, 1) }
        assertFailsWith<IllegalArgumentException> { MidiPitchBendEvent(key(0, MidiSemanticEventKind.PITCH_BEND, 0, 0), 0, 8_192) }
        assertFailsWith<IllegalArgumentException> { SemanticMidiSequence(source, listOf(SemanticMidiTrack(1, emptyList()))) }
        assertFailsWith<IllegalArgumentException> { SemanticMidiTrack(0, listOf(note(0, 1, 0, 1))) }
    }

    @Test
    fun `rational beat conversion is exact where possible and otherwise rounds nearest with ties up`() {
        val ppq = MidiPpq(480)

        assertEquals(160, MidiBeatPosition.of(1, 3).toTicks(ppq))
        assertEquals(69, MidiBeatPosition.of(1, 7).toTicks(ppq))
        assertEquals(1, MidiBeatPosition.of(1, 960).toTicks(ppq))
        assertEquals(0, MidiBeatPosition.of(1, 1_440).toTicks(ppq))
        assertEquals(MidiBeatPosition.of(3, 2), MidiBeatPosition.fromTicks(720, ppq))
        assertTrue(MidiBeatPosition.of(1, 3) < MidiBeatPosition.of(1, 2))
        assertFailsWith<ArithmeticException> { MidiBeatPosition.of(Long.MAX_VALUE, 1).toTicks(MidiPpq(2)) }
    }

    @Test
    fun `sequence retains all supported semantic event categories`() {
        val events = listOf<SemanticMidiEvent>(
            MidiTimeSignatureEvent(key(0, MidiSemanticEventKind.TIME_SIGNATURE, 0, 0), 4, 2, 24, 8),
            MidiTrackNameEvent(key(0, MidiSemanticEventKind.TRACK_NAME, 0, 1), "Melody"),
            MidiTextEvent(key(0, MidiSemanticEventKind.TEXT, 0, 2), MidiTextKind.LYRIC, "la"),
            MidiControlChangeEvent(key(0, MidiSemanticEventKind.CONTROL_CHANGE, 0, 3), 0, 64, 127),
            MidiPitchBendEvent(key(0, MidiSemanticEventKind.PITCH_BEND, 0, 4), 0, -512),
            MidiChannelPressureEvent(key(0, MidiSemanticEventKind.CHANNEL_PRESSURE, 0, 5), 0, 80),
            note(0, 0, 6, 240),
            MidiUnsupportedEvent(key(240, MidiSemanticEventKind.UNSUPPORTED, 0, 7), "system-exclusive", "Preserved as an import finding"),
        )

        assertEquals(events.map(SemanticMidiEvent::kind).toSet(), SemanticMidiSequence(source, listOf(SemanticMidiTrack(0, events))).orderedEvents().map(SemanticMidiEvent::kind).toSet())
    }

    @Test
    fun `generated ordering keys are stable after semantic priority and source keys`() {
        val generatedFirst = MidiEventOrderingKey(240, MidiSemanticEventKind.NOTE, generatedEventKey = 1)
        val generatedSecond = MidiEventOrderingKey(240, MidiSemanticEventKind.NOTE, generatedEventKey = 2)
        val sourceEvent = key(240, MidiSemanticEventKind.NOTE, 0, 9)

        assertTrue(sourceEvent < generatedFirst)
        assertTrue(generatedFirst < generatedSecond)
    }

    private fun note(tick: Long, sourceTrack: Int, sourceIndex: Int, endTick: Long) =
        MidiNoteEvent(key(tick, MidiSemanticEventKind.NOTE, sourceTrack, sourceIndex), endTick, 0, 60, 100)

    private fun key(tick: Long, kind: MidiSemanticEventKind, sourceTrack: Int, sourceIndex: Int) =
        MidiEventOrderingKey(tick, kind, sourceEvent = MidiSourceEventIdentity(sourceTrack, sourceIndex))
}
