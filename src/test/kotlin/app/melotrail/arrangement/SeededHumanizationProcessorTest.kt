package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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

class SeededHumanizationProcessorTest {
    @TempDir lateinit var root: Path

    @Test
    fun `one humanization edit can retain every applicable reason`() {
        val edit = HumanizationEdit(
            noteId = "m-" + "a".repeat(64), channel = 0, pitch = 60,
            originalStartTick = 120, originalEndTick = 360, originalVelocity = 80,
            startTick = 125, endTick = 370, velocity = 86,
            reasons = listOf("timing", "duration", "velocity", "chord-stagger", "collision-repair")
        )

        assertEquals(5, edit.reasons.size)
    }

    @Test
    fun `same input config seed and version produces identical evidence without source mutation`() {
        val input = root.resolve("input.mid").also(::midi)
        val source = Files.readAllBytes(input)
        val one = root.resolve("one.mid")
        val two = root.resolve("two.mid")
        val config = HumanizationConfig(amountPercent = 100, timingMaxMs = 20, velocityMaxDelta = 12, durationMaxMs = 10, chordStaggerMs = 8, swingPercent = 58)

        val first = SeededHumanizationProcessor().transform(input, one, HumanizationRole.PIANO, config, 41)
        val second = SeededHumanizationProcessor().transform(input, two, HumanizationRole.PIANO, config, 41)

        assertTrue(Files.readAllBytes(one).contentEquals(Files.readAllBytes(two)))
        assertEquals(first.report, second.report)
        assertTrue(source.contentEquals(Files.readAllBytes(input)))
    }

    @Test
    fun `different seed produces a distinct permitted variation while retaining anchors pitches count and meter`() {
        val input = root.resolve("input.mid").also(::midi)
        val one = root.resolve("one.mid")
        val two = root.resolve("two.mid")
        val config = HumanizationConfig(amountPercent = 100, timingMaxMs = 25, velocityMaxDelta = 16, durationMaxMs = 12, chordStaggerMs = 10, swingPercent = 58)
        SeededHumanizationProcessor().transform(input, one, HumanizationRole.DRUMS, config, 1)
        SeededHumanizationProcessor().transform(input, two, HumanizationRole.DRUMS, config, 2)

        assertNotEquals(notes(one), notes(two))
        assertEquals(notes(input).map { it.channel to it.pitch }.sortedBy { it.toString() }, notes(one).map { it.channel to it.pitch }.sortedBy { it.toString() })
        assertEquals(notes(input).size, notes(one).size)
        assertEquals(tempoAndMeter(input), tempoAndMeter(one))
        assertEquals(notes(input).first().start, notes(one).first().start, "tick-zero anchor is protected")
        assertEquals(notes(input).last().end, notes(one).last().end, "section-end anchor is protected")
    }

    @Test
    fun `legacy groove suppresses second swing and all edits remain bounded with legal durations`() {
        val input = root.resolve("input.mid").also(::midi)
        val output = root.resolve("output.mid")
        val config = HumanizationConfig(amountPercent = 100, timingMaxMs = 30, velocityMaxDelta = 20, durationMaxMs = 20, chordStaggerMs = 12, swingPercent = 70)
        val result = SeededHumanizationProcessor().transform(input, output, HumanizationRole.BASS, config, 99, legacyGrooveApplied = true)

        assertTrue(result.report.warnings.single().contains("suppressed"))
        assertTrue(result.report.edits.all { edit -> edit.velocity in 1..127 && edit.endTick > edit.startTick && edit.pitch in 0..127 })
        assertTrue(notes(output).all { it.end > it.start })
        assertTrue(result.report.edits.none { "swing" in it.reasons })
    }

    private fun midi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        val micros = 500_000
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        note(track, 0, 220, 60, 88)
        note(track, 240, 430, 64, 92)
        note(track, 240, 460, 67, 95)
        note(track, 480, 700, 62, 100)
        note(track, 1_680, 1_920, 55, 90)
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
    }
    private fun note(track: javax.sound.midi.Track, start: Long, end: Long, pitch: Int, velocity: Int) {
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, velocity), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), end))
    }
    private data class Note(val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private fun notes(path: Path): List<Note> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        return MidiSystem.getSequence(path.toFile()).tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@mapNotNull null
            val key = message.channel to message.data1
            when {
                message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> { active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2); null }
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                    val (start, velocity) = active.getValue(key).removeFirst(); Note(message.channel, message.data1, velocity, start, event.tick)
                }
                else -> null
            }
        } }.sortedBy { it.start }
    }
    private fun tempoAndMeter(path: Path): List<Pair<Int, Pair<Long, List<Byte>>>> = MidiSystem.getSequence(path.toFile()).tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
        val message = track[index].message as? MetaMessage ?: return@mapNotNull null
        message.type.takeIf { it in setOf(0x51, 0x58) }?.let { it to (track[index].tick to message.data.toList()) }
    } }
}
