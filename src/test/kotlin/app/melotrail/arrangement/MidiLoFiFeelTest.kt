package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.roundToLong

class MidiLoFiFeelTest {
    @TempDir lateinit var root: Path

    @Test
    fun `fixed profile moves only eligible offbeats normalizes tempo and preserves signatures across PPQN`() {
        listOf(96, 480, 960).forEach { ppq ->
            val input = root.resolve("input-$ppq.mid")
            val output = root.resolve("output-$ppq.mid")
            midi(input, ppq)
            val sourceBefore = Files.readAllBytes(input)

            val result = MidiLoFiFeelTransformer().transform(input, output, "A")
            val sequence = MidiSystem.getSequence(output.toFile())

            assertEquals(80, result.report.outputTempoBpm)
            assertEquals(MidiFeelProfile.LOFI_80_SWING_V1, result.report.profile)
            assertEquals(2, result.report.movedNoteCount)
            assertEquals((ppq * 0.58).roundToLong() - ppq / 2L, result.report.maximumShiftTicks)
            assertEquals(listOf(0L, (ppq * 0.58).roundToLong(), ppq.toLong(), ppq + (ppq * 0.58).roundToLong()), noteStarts(sequence))
            assertEquals(listOf(0L, (ppq * 4).toLong()), timeSignatureTicks(sequence))
            assertEquals(listOf(80.0), tempos(sequence))
            assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)), "source is read only")
        }
    }

    @Test
    fun `repeat runs are byte deterministic and collision repair retains legal paired notes`() {
        val input = root.resolve("collision.mid")
        midi(input, 480, collision = true)
        val first = root.resolve("first.mid")
        val second = root.resolve("second.mid")

        val one = MidiLoFiFeelTransformer().transform(input, first, "A")
        val two = MidiLoFiFeelTransformer().transform(input, second, "A")

        assertTrue(Files.readAllBytes(first).contentEquals(Files.readAllBytes(second)))
        assertEquals(one.report, two.report)
        assertTrue(one.report.collisionRepairs > 0)
        val notes = notes(MidiSystem.getSequence(first.toFile()), 62)
        assertTrue(notes.all { it.second > it.first })
        assertTrue(notes.zipWithNext().all { (a, b) -> a.second <= b.first })
    }

    @Test
    fun `report store rejects stale source hashes and preserves the prior report on invalid publication`() {
        val clean = root.resolve("midi/clean/A.mid"); Files.createDirectories(clean.parent); midi(clean, 480)
        val derived = MidiFeelReportStore.derivedPath(root, "A", MidiFeelProfile.LOFI_80_SWING_V1)
        Files.createDirectories(derived.parent)
        val report = MidiLoFiFeelTransformer().transform(clean, derived, "A").report
        val reportPath = MidiFeelReportStore.write(root, report)
        val reportBefore = Files.readAllBytes(reportPath)
        val refs = MidiFeelReferences(report.profile, root.relativize(derived).toString(), root.relativize(reportPath).toString())
        assertTrue(MidiFeelReportStore.isCurrent(root, "A", "midi/clean/A.mid", refs))

        Files.write(clean, Files.readAllBytes(clean) + byteArrayOf(0))
        assertFalse(MidiFeelReportStore.isCurrent(root, "A", "midi/clean/A.mid", refs))
        assertThrows(IllegalArgumentException::class.java) { MidiFeelReportStore.write(root, report.copy(movedNoteCount = -1)) }
        assertTrue(reportBefore.contentEquals(Files.readAllBytes(reportPath)))
    }

    private fun midi(path: Path, ppq: Int, collision: Boolean = false) {
        val sequence = Sequence(Sequence.PPQ, ppq)
        val track = sequence.createTrack()
        tempo(track, 0, 120); tempo(track, ppq.toLong(), 100)
        signature(track, 0, 4, 4); signature(track, (ppq * 4).toLong(), 3, 4)
        note(track, 0, ppq / 4L, 60)
        note(track, ppq / 2L, ppq * 3L / 4L, 62)
        note(track, ppq.toLong(), ppq * 5L / 4L, 64)
        note(track, ppq * 3L / 2L, ppq * 7L / 4L, 65)
        if (collision) note(track, ppq * 3L / 4L, ppq.toLong(), 62)
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun note(track: javax.sound.midi.Track, start: Long, end: Long, pitch: Int) {
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 100), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), end))
    }
    private fun tempo(track: javax.sound.midi.Track, tick: Long, bpm: Int) { val micros = 60_000_000 / bpm; track.add(MidiEvent(MetaMessage().apply { setMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }, tick)) }
    private fun signature(track: javax.sound.midi.Track, tick: Long, numerator: Int, denominator: Int) { val exponent = Integer.numberOfTrailingZeros(denominator); track.add(MidiEvent(MetaMessage().apply { setMessage(0x58, byteArrayOf(numerator.toByte(), exponent.toByte(), 24, 8), 4) }, tick)) }
    private fun noteStarts(sequence: Sequence) = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { track[index].tick } } }.sorted()
    private fun timeSignatureTicks(sequence: Sequence) = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? MetaMessage)?.takeIf { it.type == 0x58 }?.let { track[index].tick } } }.sorted()
    private fun tempos(sequence: Sequence) = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? MetaMessage)?.takeIf { it.type == 0x51 }?.let { message -> val data = message.data; 60_000_000.0 / (((data[0].toInt() and 255) shl 16) or ((data[1].toInt() and 255) shl 8) or (data[2].toInt() and 255)) } } }.sorted()
    private fun notes(sequence: Sequence, pitch: Int): List<Pair<Long, Long>> { val active = ArrayDeque<Long>(); return sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> val event = track[index]; val message = event.message as? ShortMessage ?: return@mapNotNull null; if (message.data1 != pitch) return@mapNotNull null; when { message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> { active.addLast(event.tick); null }; message.command == ShortMessage.NOTE_OFF -> active.removeFirst() to event.tick; else -> null } } } }
}
