package app.melotrail.arrangement

import app.melotrail.application.CanonicalAnalyzedPartFacts
import app.melotrail.application.CanonicalChord
import app.melotrail.application.CanonicalSelectedPartArtifact
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import app.melotrail.application.PartRepairProjection
import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
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
        val input = input(source)
        val plan = MidiAiFixPlan(
            partId = input.partId,
            selectedInputSha256 = input.selectedInputSha256,
            inputHash = input.inputHash,
            contextSchemaVersion = input.contextSchemaVersion,
            contextSha256 = input.contextSha256,
            model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
            edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.TIMING, noteId = input.notes[1].id, startTick = 120))
        )

        val error = assertThrows(IllegalArgumentException::class.java) { plan.requireValid(input) }

        assertEquals("AI-fix plan produces a note collision between ${input.notes[0].id} and ${input.notes[1].id}", error.message)
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
        val input = input(source)
        val unsafe = """{"version":2,"partId":"${input.partId}","selectedInputSha256":"${input.selectedInputSha256}","inputHash":"${input.inputHash}","contextSchemaVersion":${input.contextSchemaVersion},"contextSha256":"${input.contextSha256}","edits":[{"kind":"timing","noteId":"${input.notes[1].id}","startTick":120}]}"""
        val prompts = mutableListOf<String>()

        val plan = LocalQwenMidiAiFixPlanner(
            LocalQwenClient { _, prompt -> prompts += prompt; unsafe },
            MidiAiFixModelIdentity("qwen", "local", "a".repeat(64), "unknown")
        ).plan(input)

        assertTrue(plan.edits.isEmpty())
        assertEquals(3, prompts.size)
        assertTrue(prompts.drop(1).all { it.contains("previous candidate was rejected") })
        assertTrue(prompts.drop(1).all { it.contains(input.notes[0].id) && it.contains(input.notes[1].id) })
    }

    @Test
    fun `retries no-op timing plans with the note-specific bounded range`() {
        val source = directory.resolve("retry-noop.mid")
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = input(source)
        val unsafe = """{"version":2,"partId":"${input.partId}","selectedInputSha256":"${input.selectedInputSha256}","inputHash":"${input.inputHash}","contextSchemaVersion":${input.contextSchemaVersion},"contextSha256":"${input.contextSha256}","edits":[{"kind":"timing","noteId":"${input.notes.single().id}","startTick":0}]}"""
        val prompts = mutableListOf<String>()

        val plan = LocalQwenMidiAiFixPlanner(LocalQwenClient { _, prompt -> prompts += prompt; unsafe }).plan(input)

        assertTrue(plan.edits.isEmpty())
        assertEquals(3, prompts.size)
        assertTrue(prompts.drop(1).all { it.contains("must move startTick 0 by 1..120 ticks") })
    }

    @Test
    fun `retries a removal that is not supported by collision evidence`() {
        val source = directory.resolve("retry-removal.mid")
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = input(source)
        val unsafe = """{"version":2,"partId":"${input.partId}","selectedInputSha256":"${input.selectedInputSha256}","inputHash":"${input.inputHash}","contextSchemaVersion":${input.contextSchemaVersion},"contextSha256":"${input.contextSha256}","edits":[{"kind":"remove_collision_or_duplicate","noteId":"${input.notes.single().id}"}]}"""
        val prompts = mutableListOf<String>()

        val plan = LocalQwenMidiAiFixPlanner(LocalQwenClient { _, prompt -> prompts += prompt; unsafe }).plan(input)

        assertTrue(plan.edits.isEmpty())
        assertEquals(3, prompts.size)
        assertTrue(prompts.drop(1).all { it.contains("Removal is only allowed for a detected collision or duplicate") })
    }

    @Test
    fun `rejects pitch that clashes with declared chord even when analysis could infer otherwise`() {
        val source = directory.resolve("chord.mid")
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = input(source)
        val plan = MidiAiFixPlan(partId = "A", selectedInputSha256 = input.selectedInputSha256, inputHash = input.inputHash,
            contextSchemaVersion = input.contextSchemaVersion, contextSha256 = input.contextSha256,
            model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
            edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.PITCH, input.notes.single().id, pitch = 62)))

        assertEquals("AI-fix pitch clashes with the declared active chord", assertThrows(IllegalArgumentException::class.java) { plan.requireValid(input) }.message)
    }

    @Test
    fun `repeated occurrences retain separate canonical chord evidence at identical local ticks`() {
        val source = directory.resolve("repeated.mid")
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, source.toFile())

        val input = input(source, repeated = true)
        val localStarts = input.occurrenceTimeline.associate { occurrence ->
            occurrence.occurrenceId to input.harmonicTimeline.single { it.occurrenceId == occurrence.occurrenceId }.startTick - occurrence.startTick
        }

        assertEquals(mapOf("verse-1" to 0L, "verse-2" to 0L), localStarts)
        assertEquals(listOf("C", "C"), input.harmonicTimeline.map { it.chord.symbol })
    }

    @Test
    fun `rejects anchor range and budget violations before mutation`() {
        val source = directory.resolve("limits.mid")
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, source.toFile())
        val input = input(source)
        fun plan(edits: List<MidiAiFixEdit>) = MidiAiFixPlan(partId = "A", selectedInputSha256 = input.selectedInputSha256,
            inputHash = input.inputHash, contextSchemaVersion = input.contextSchemaVersion, contextSha256 = input.contextSha256,
            model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"), edits = edits)

        assertThrows(IllegalArgumentException::class.java) { plan(listOf(MidiAiFixEdit(MidiAiFixEditKind.PITCH, input.notes.single().id, pitch = 64))).requireValid(input) }
        assertThrows(IllegalArgumentException::class.java) { plan(listOf(MidiAiFixEdit(MidiAiFixEditKind.PITCH, input.notes.single().id, pitch = 128))).requireValid(input) }
        assertThrows(IllegalArgumentException::class.java) { plan(List(MidiAiFixValidator.MAX_EDITS + 1) { MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, input.notes.single().id, velocity = 90) }).requireValid(input) }
    }

    private fun input(source: Path, repeated: Boolean = false): MidiAiFixInput {
        val hash = sha256(source)
        val analysis = MidiPartAnalyzer().analyze(source, "A")
        val duration = analysis.durationTicks
        val occurrences = if (repeated) listOf(
            MusicalOccurrence("verse-1", "A", SectionTypeId.VERSE, 0, 1, 0, duration),
            MusicalOccurrence("verse-2", "A", SectionTypeId.VERSE, 1, 2, duration, duration * 2)
        ) else listOf(MusicalOccurrence("verse-1", "A", SectionTypeId.VERSE, 0, 1, 0, duration))
        val projection = PartRepairProjection(
            contextSha256 = "a".repeat(64),
            part = CanonicalSelectedPartArtifact("A", "midi/corrected/A.mid", hash, analysis.ppq, "corrected"),
            projectKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
            tempo = Tempo(120.0), meter = TimeSignature(4, 4), occurrences = occurrences,
            harmony = occurrences.mapIndexed { index, occurrence -> HarmonicTimelineEntry(occurrence.occurrenceId, SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), index.toLong(), occurrence.startTick, occurrence.endTick) },
            harmonyPpq = analysis.ppq,
            analysis = CanonicalAnalyzedPartFacts("A", hash, "b".repeat(64), analysis),
            melodyEvidence = emptyList(), diagnostics = emptyList()
        )
        return MidiAiFixInputFactory.build(projection, source)
    }
}
