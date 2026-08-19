package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiProjectKeyTransposerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `all chromatic tonic intervals are deterministic and enharmonic spelling is metadata`() {
        val input = root.resolve("normalized.mid").also(::writeInput)
        val sourceBytes = Files.readAllBytes(input)
        (0..11).forEach { target ->
            val first = root.resolve("$target-a.mid")
            val second = root.resolve("$target-b.mid")
            val sourceKey = key(0, ScaleModeId.MAJOR)
            val projectKey = key(target, if (target % 2 == 0) ScaleModeId.MAJOR else ScaleModeId.NATURAL_MINOR)
            val firstReport = MidiProjectKeyTransposer().transpose("part", input, first, sourceKey, projectKey)
            val secondReport = MidiProjectKeyTransposer().transpose("part", input, second, sourceKey, projectKey)

            assertEquals(Files.readAllBytes(first).toList(), Files.readAllBytes(second).toList())
            assertEquals(firstReport, secondReport)
            assertEquals(target.takeIf { it <= 6 } ?: target - 12, firstReport.intervalSemitones)
        }
        val enharmonic = MidiProjectKeyTransposer().transpose(
            "part", input, root.resolve("enharmonic.mid"),
            MusicalKey(PitchClass.of(PitchSpelling.C_SHARP), ScaleModeId.MAJOR),
            MusicalKey(PitchClass.of(PitchSpelling.D_FLAT), ScaleModeId.NATURAL_MINOR)
        )
        assertEquals(0, enharmonic.intervalSemitones)
        assertEquals(sourceBytes.toList(), Files.readAllBytes(input).toList())
    }

    @Test
    fun `transposition preserves timing velocity controllers tempo meter and drums while reporting octave folds`() {
        val input = root.resolve("normalized.mid").also(::writeInput)
        val output = root.resolve("transposed.mid")
        val report = MidiProjectKeyTransposer().transpose("part", input, output, key(0, ScaleModeId.MAJOR), key(1, ScaleModeId.NATURAL_MINOR))
        val before = MidiSystem.getSequence(input.toFile())
        val after = MidiSystem.getSequence(output.toFile())
        val beforeEvents = events(before)
        val afterEvents = events(after)

        assertEquals(beforeEvents.filterNot { it.notePitch != null }.map { it.copy(notePitch = null) }, afterEvents.filterNot { it.notePitch != null }.map { it.copy(notePitch = null) })
        assertEquals(metaEvents(before), metaEvents(after))
        assertEquals(listOf(1, 36, 61, 116), noteOns(after).map { it.data1 }.sorted())
        assertEquals(36, noteOns(after).single { it.channel == 9 }.data1)
        assertTrue(report.movements.any { it.sourcePitch == 127 && it.outputPitch == 116 && it.octaveFolded })
        assertTrue(report.warnings.contains("OCTAVE_FOLD_APPLIED"))
        assertTrue(report.warnings.contains("PERCUSSION_CHANNEL_PRESERVED"))
        assertEquals(3, report.chordFit.noteOnsets)
        assertEquals(4, report.output.noteCount)
    }

    private fun key(chromatic: Int, mode: ScaleModeId) = MusicalKey(PitchClass.canonical(chromatic), mode)

    private fun writeInput(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(tempo(500_000), 0)); add(MidiEvent(meter(), 0))
            add(MidiEvent(ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 1, 73), 8))
            listOf(0 to 60, 12 to 0, 24 to 127, 36 to 36).forEach { (tick, pitch) ->
                val channel = if (pitch == 36) 9 else 0
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, 91), tick.toLong()))
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), (tick + 120).toLong()))
            }
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun noteOns(sequence: Sequence): List<ShortEvent> = events(sequence).filter { it.command == ShortMessage.NOTE_ON && it.velocity > 0 }
    private fun metaEvents(sequence: Sequence): List<Pair<Long, Pair<Int, List<Byte>>>> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
        val event = track[index]; val meta = event.message as? MetaMessage ?: return@mapNotNull null
        if (meta.type == 0x2f) null else event.tick to (meta.type to meta.data.toList())
    } }.sortedBy { it.first }
    private fun events(sequence: Sequence): List<ShortEvent> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
        val event = track[index]; val short = event.message as? ShortMessage ?: return@mapNotNull null
        ShortEvent(event.tick, short.command, short.channel, short.data1, short.data2, if (short.command == ShortMessage.NOTE_ON || short.command == ShortMessage.NOTE_OFF) short.data1 else null)
    } }.sortedWith(compareBy<ShortEvent> { it.tick }.thenBy { it.command }.thenBy { it.channel }.thenBy { it.data1 }.thenBy { it.velocity })

    private data class ShortEvent(val tick: Long, val command: Int, val channel: Int, val data1: Int, val velocity: Int, val notePitch: Int?)
    private fun tempo(value: Int) = MetaMessage().also { it.setMessage(0x51, byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()), 3) }
    private fun meter() = MetaMessage().also { it.setMessage(0x58, byteArrayOf(4, 2, 24, 8), 4) }
}
