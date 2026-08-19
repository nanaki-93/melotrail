package app.melotrail.arrangement

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

class MidiNormalizationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `normalization is deterministic preserves notes drums and expressive off grid timing`() {
        val input = root.resolve("clean.mid").also(::writeInput)
        val inputBytes = Files.readAllBytes(input)
        val first = root.resolve("normalized-a.mid")
        val second = root.resolve("normalized-b.mid")
        val config = MidiNormalizationConfig(timingToleranceMs = 0, velocityMinimum = 12, velocityMaximum = 120)

        val firstReport = MidiNormalizer().normalize("intro", input, first, config)
        val secondReport = MidiNormalizer().normalize("intro", input, second, config)
        val sequence = MidiSystem.getSequence(first.toFile())
        val notes = noteOns(sequence)

        assertEquals(960, sequence.resolution)
        assertEquals(Files.readAllBytes(first).toList(), Files.readAllBytes(second).toList())
        assertEquals(inputBytes.toList(), Files.readAllBytes(input).toList())
        assertEquals(firstReport, secondReport)
        assertEquals(0, firstReport.changes.createdNotes)
        assertEquals(0, firstReport.changes.deletedNotes)
        assertEquals(0, firstReport.changes.changedPitches)
        assertEquals(listOf(36, 60, 64), notes.map(NoteOn::pitch).sorted())
        assertEquals(12, notes.single { it.channel == 0 && it.pitch == 60 }.velocity)
        assertEquals(2, notes.single { it.channel == 9 }.velocity)
        assertEquals(34L, notes.single { it.channel == 0 && it.pitch == 60 }.tick)
        assertTrue(firstReport.warnings.any { it.code == "EXPRESSIVE_TIMING_PRESERVED" })
        assertTrue(firstReport.warnings.any { it.code == "DRUM_VELOCITY_PRESERVED" })
        assertEquals(sha256(input), firstReport.input.sha256)
    }

    @Test
    fun `typed policy can conform tempo meter and only snap within conservative tolerance`() {
        val input = root.resolve("clean.mid").also(::writeInput)
        val output = root.resolve("normalized.mid")
        val report = MidiNormalizer().normalize("intro", input, output, MidiNormalizationConfig(
            timingToleranceMs = 12, targetTempoBpm = 100, targetMeterNumerator = 3, targetMeterDenominator = 4
        ))
        val sequence = MidiSystem.getSequence(output.toFile())
        val meta = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.mapNotNull { it.message as? MetaMessage }

        assertEquals(960, sequence.resolution)
        assertTrue(meta.any { it.type == 0x51 })
        assertTrue(meta.any { it.type == 0x58 })
        assertTrue(report.changes.replacedTempoEvents > 0)
        assertTrue(report.changes.replacedMeterEvents > 0)
        assertEquals(3, report.input.noteCount)
        assertEquals(3, report.output.noteCount)
    }

    private fun writeInput(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(tempo(500_000), 0))
        track.add(MidiEvent(meter(4, 4), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 1), 17))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 127), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 9, 36, 2), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 199))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 480))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 9, 36, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun noteOns(sequence: Sequence): List<NoteOn> = sequence.tracks.flatMap { track ->
        (0 until track.size()).mapNotNull { index ->
            val event = track[index]
            val message = event.message as? ShortMessage
            if (message?.command == ShortMessage.NOTE_ON && message.data2 > 0) NoteOn(event.tick, message.channel, message.data1, message.data2) else null
        }
    }

    private data class NoteOn(val tick: Long, val channel: Int, val pitch: Int, val velocity: Int)

    private fun tempo(value: Int) = MetaMessage().also { it.setMessage(0x51, byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()), 3) }
    private fun meter(numerator: Int, denominator: Int) = MetaMessage().also { it.setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }
}
