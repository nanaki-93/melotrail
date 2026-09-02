package app.melotrail.midi.domain

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiProtectedMelodySelectorTest {
    @TempDir lateinit var root: Path

    @Test
    fun `projects the single melody channel and preserves supported expression`() {
        val source = OwnedMidiFixtures.writeAll(root).single { it.fileName.toString() == "expressive-controller-pitch.mid" }
        val sequence = JdkMidiReader().inspect(source).sequence

        val view = MidiProtectedMelodySelector().select(sequence, MidiMelodySelection(1, 0))

        assertEquals(1, view.notes.size)
        assertEquals(2, view.events.filterIsInstance<MidiControlChangeEvent>().size)
        assertEquals(1, view.events.filterIsInstance<MidiPitchBendEvent>().size)
        assertTrue(view.protectedAnchorIds.isNotEmpty())
        assertTrue(view.events.all { event ->
            when (event) {
                is MidiNoteEvent -> event.channel == MidiProtectedMelodyView.OUTPUT_CHANNEL
                is MidiControlChangeEvent -> event.channel == MidiProtectedMelodyView.OUTPUT_CHANNEL
                is MidiPitchBendEvent -> event.channel == MidiProtectedMelodyView.OUTPUT_CHANNEL
                is MidiChannelPressureEvent -> event.channel == MidiProtectedMelodyView.OUTPUT_CHANNEL
                else -> false
            }
        })
    }
}
