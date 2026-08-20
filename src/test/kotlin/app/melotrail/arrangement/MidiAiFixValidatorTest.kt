package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class MidiAiFixValidatorTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `rejects a bounded timing edit that creates a same-pitch collision`() {
        val source = directory.resolve("part.mid")
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = MidiAiFixInputFactory.build("A", source)
        val plan = MidiAiFixPlan(
            partId = input.partId,
            cleanedSha256 = input.cleanedSha256,
            inputHash = input.inputHash,
            model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
            edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.TIMING, noteId = input.notes[1].id, startTick = 120))
        )

        val error = assertThrows(IllegalArgumentException::class.java) { plan.requireValid(input) }

        assertEquals("AI-fix plan produces a note collision between n-00000 and n-00001", error.message)
    }

    @Test
    fun `retries unsafe collision plans then returns a no-change plan`() {
        val source = directory.resolve("retry-part.mid")
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = MidiAiFixInputFactory.build("A", source)
        val unsafe = """{"version":1,"partId":"${input.partId}","cleanedSha256":"${input.cleanedSha256}","inputHash":"${input.inputHash}","edits":[{"kind":"timing","noteId":"${input.notes[1].id}","startTick":120}]}"""
        val prompts = mutableListOf<String>()

        val plan = LocalQwenMidiAiFixPlanner(
            LocalQwenClient { _, prompt -> prompts += prompt; unsafe },
            MidiAiFixModelIdentity("qwen", "local", "a".repeat(64), "unknown")
        ).plan(input)

        assertTrue(plan.edits.isEmpty())
        assertEquals(3, prompts.size)
        assertTrue(prompts.drop(1).all { it.contains("previous candidate was rejected") })
        assertTrue(prompts.drop(1).all { it.contains("n-00000") && it.contains("n-00001") })
    }
}
