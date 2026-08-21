package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertArrayEquals
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

class CohesionMelodyApplierTest {
    @TempDir lateinit var root: Path

    @Test
    fun `cohesion publishes occurrence-local note repairs and preserves source plus anchors`() {
        val source = root.resolve("selected.mid")
        writeMidi(source)
        val sourceBytes = Files.readAllBytes(source)
        val evidence = evidence(source)
        val target = root.resolve("cohesion/occurrences/A1/cohesive.mid")
        val edits = listOf(
            CohesionMelodyEdit("A1", CohesionMelodyEditKind.REMOVE_NOTE, "n-00017", reason = "remove repeated boundary note"),
            CohesionMelodyEdit(
                occurrenceInstanceId = "A1", kind = CohesionMelodyEditKind.ADD_NOTE, noteId = "add-00000",
                pitch = 62, velocity = 68, startTick = 8_160, durationTicks = 120, channel = 0,
                anchorNoteId = "n-00016", reason = "connect into the next phrase"
            )
        )

        CohesionMelodyApplier.write(source, target, evidence, edits)
        val notes = midiNotes(target)

        assertArrayEquals(sourceBytes, Files.readAllBytes(source))
        assertEquals(20, notes.size)
        assertEquals(60 to 60, notes.first().second to notes.last().second)
        assertTrue(notes.any { (start, pitch, end) -> start == 8_160L && pitch == 62 && end == 8_280L })
    }

    @Test
    fun `cohesion refuses to remove a melody endpoint and publishes nothing`() {
        val source = root.resolve("selected.mid")
        writeMidi(source)
        val target = root.resolve("cohesion/occurrences/A1/rejected.mid")

        assertThrows(IllegalArgumentException::class.java) {
            CohesionMelodyApplier.write(
                source, target, evidence(source),
                listOf(CohesionMelodyEdit("A1", CohesionMelodyEditKind.REMOVE_NOTE, "n-00000", reason = "unsafe endpoint removal"))
            )
        }
        assertFalse(Files.exists(target))
    }

    @Test
    fun `cohesion refuses to retime or repitch melody endpoints`() {
        val source = root.resolve("selected.mid"); writeMidi(source)
        listOf(
            CohesionMelodyEdit("A1", CohesionMelodyEditKind.SET_PITCH, "n-00000", value = 62, reason = "unsafe endpoint pitch"),
            CohesionMelodyEdit("A1", CohesionMelodyEditKind.SET_START, "n-00019", value = 9_000, reason = "unsafe endpoint timing")
        ).forEachIndexed { index, edit ->
            val target = root.resolve("cohesion/rejected-$index.mid")
            assertThrows(IllegalArgumentException::class.java) { CohesionMelodyApplier.write(source, target, evidence(source), listOf(edit)) }
            assertFalse(Files.exists(target))
        }
    }

    private fun evidence(source: Path): TransitionMusicalEvidence = TransitionMusicalEvidence(
        partId = "A", sourceHash = sha256(source), analysisHash = "a".repeat(64), ppq = 480, durationTicks = 9_600,
        key = MidiKey("C", "major", 1.0), chords = emptyList(), tempo = MidiTempoChange(0, 80.0),
        meter = MidiTimeSignature(0, 4, 4), energy = 0.5,
        boundary = TransitionBoundarySummary(true, false, 0, 9_120),
        arrangement = TransitionArrangementEvidence("b".repeat(64), SongSectionPurpose.DEVELOPMENT, emptyList(), "c".repeat(64)),
        melodyNotes = List(20) { index ->
            val start = index * 480L
            CohesionMelodyNote("n-${index.toString().padStart(5, '0')}", 0, 60, 70, start, start + 240)
        }
    )

    private fun writeMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        repeat(20) { index ->
            val start = index * 480L
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 70), start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), start + 240))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun midiNotes(path: Path): List<Triple<Long, Int, Long>> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Long>>()
        val notes = mutableListOf<Triple<Long, Int, Long>>()
        MidiSystem.getSequence(path.toFile()).tracks.forEach { track ->
            (0 until track.size()).forEach { index ->
                val event = track[index]
                val message = event.message as? ShortMessage ?: return@forEach
                val key = message.channel to message.data1
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                    active.getOrPut(key) { ArrayDeque() }.addLast(event.tick)
                } else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                    notes += Triple(active.getValue(key).removeFirst(), message.data1, event.tick)
                }
            }
        }
        return notes.sortedWith(compareBy<Triple<Long, Int, Long>> { it.first }.thenBy { it.second })
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
