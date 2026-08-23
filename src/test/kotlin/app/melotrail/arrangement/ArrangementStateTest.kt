package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class ArrangementStateTest {
    @TempDir lateinit var temp: Path

    @Test
    fun `accepted state keeps full MIDI and compact summaries without accepting a rejected candidate`() {
        val piano = writeMidi("piano.mid", listOf(60 to (0L to 240L), 64 to (480L to 720L)))
        val bass = writeMidi("bass.mid", listOf(36 to (0L to 480L), 43 to (480L to 960L)))
        val state = ArrangementState.fromAcceptedPiano(480, ArrangementState.fromMidi("piano", piano, 480).notes, digest(piano))

        assertEquals(listOf(0L, 480L), state.summary("piano").onsets)
        assertEquals(60..64, state.summary("piano").register)
        assertEquals(listOf("piano"), state.plannerContext().tracks.map { it.role })
        assertEquals(2, state.relevantExcerpt(0, 960).size)

        val afterRejectedCandidate = runCatching {
            require(false) { "Generated bass MIDI failed validation" }
            state.acceptValidated("bass", bass)
        }.getOrElse { state }
        assertFalse(afterRejectedCandidate.hasTrack("bass"))

        val accepted = state.acceptValidated("bass", bass)
        assertEquals(listOf("piano", "bass"), accepted.acceptedTracks.map { it.role })
        assertEquals(4, accepted.fullAcceptedMidi().size)
        assertEquals(listOf(0L, 480L), accepted.summary("bass").onsets)
        val rhythm = accepted.pianoBassRhythmMap(0, 960)
        assertEquals(listOf(0L, 480L), rhythm.track("piano")?.onsets)
        assertTrue(rhythm.track("bass")!!.isActiveAt(600))
        assertTrue(rhythm.hasOnsetNear(480, 0))
        assertEquals(2, accepted.ensembleSpaceMap(0, 960).maximumSimultaneousNotes)
        assertThrows(IllegalArgumentException::class.java) { accepted.acceptValidated("bass", bass) }
        assertTrue(Files.isRegularFile(bass))

        val crowded = ArrangementState.fromAcceptedPiano(480, (0..64).map { MidiNote(0, 60, 90, it * 10L, it * 10L + 5) }, "c".repeat(64))
        assertEquals(65, crowded.summary("piano").onsets.size)
        assertEquals(32, crowded.plannerContext().tracks.single().onsets.size)
    }

    private fun writeMidi(name: String, notes: List<Pair<Int, Pair<Long, Long>>>): Path {
        val path = temp.resolve(name)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        notes.forEach { (pitch, timing) ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), timing.first))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), timing.second))
        }
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
