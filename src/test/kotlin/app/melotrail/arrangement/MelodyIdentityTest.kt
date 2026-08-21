package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
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

class MelodyIdentityTest {
    @TempDir lateinit var root: Path

    @Test
    fun `identity is stable for chords overlaps tracks channels duplicates and relocation`() {
        val original = root.resolve("original.mid").also(::writeComplexMidi)
        val relocated = root.resolve("relocated/selected.mid")
        Files.createDirectories(relocated.parent)
        Files.copy(original, relocated)

        val first = MelodyIdentityBuilder.build(original, canonicalBeatTicks = 480)
        val second = MelodyIdentityBuilder.build(relocated, canonicalBeatTicks = 480)

        assertEquals(first, second)
        assertEquals(6, first.notes.size)
        assertEquals(6, first.notes.map(MelodyIdentityNote::id).distinct().size)
        assertEquals(setOf(0, 1), first.notes.map(MelodyIdentityNote::track).toSet())
        assertEquals(setOf(0, 1), first.notes.map(MelodyIdentityNote::channel).toSet())
        assertTrue(first.notes.all { it.id.value.startsWith("m-") })
    }

    @Test
    fun `fallback phrases and anchors use exact canonical beat boundaries`() {
        val source = root.resolve("phrases.mid")
        writePhraseMidi(source)

        val identity = MelodyIdentityBuilder.build(source, canonicalBeatTicks = 480)

        assertEquals(listOf(2, 2), identity.phrases.map { it.noteIds.size })
        val notes = identity.notes.associateBy { it.originalStartTick }
        assertTrue(notes.getValue(0).id in identity.anchorIds) // phrase start
        assertTrue(notes.getValue(240).id in identity.anchorIds) // phrase end
        assertTrue(notes.getValue(960).id in identity.anchorIds) // one-beat held
        assertTrue(notes.getValue(1_680).id in identity.anchorIds) // local high/low held half beat
    }

    @Test
    fun `current phrase evidence wins while stale evidence falls back`() {
        val source = root.resolve("evidence.mid").also(::writePhraseMidi)
        val fallback = MelodyIdentityBuilder.build(source, 480)
        val evidence = MelodyPhraseEvidence(sourceSha256 = fallback.sourceSha256, phrases = listOf(fallback.notes.map(MelodyIdentityNote::id)))

        val current = MelodyIdentityBuilder.build(source, 480, phraseEvidence = evidence)
        val stale = MelodyIdentityBuilder.build(source, 480, phraseEvidence = evidence.copy(sourceSha256 = "0".repeat(64)))

        assertEquals(1, current.phrases.size)
        assertEquals(2, stale.phrases.size)
    }

    @Test
    fun `timing keeps identity while pitch or deletion of an anchor fails`() {
        val source = root.resolve("anchors.mid").also(::writePhraseMidi)
        val identity = MelodyIdentityBuilder.build(source, 480)
        val anchor = identity.anchorIds.first()
        val note = identity.note(anchor)
        val before = values(note)
        val retimed = MidiMutation(MidiMutationOperation.TIMING, anchor, before, before.copy(startTick = before.startTick + 12, endTick = before.endTick + 12), MidiMutationReasonCode.HUMANIZATION)

        MidiMutationInvariants.requireAnchorPreservation(identity, listOf(retimed))
        assertThrows(IllegalArgumentException::class.java) {
            MidiMutationInvariants.requireAnchorPreservation(identity, listOf(retimed.copy(operation = MidiMutationOperation.PITCH, after = before.copy(pitch = before.pitch + 1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MidiMutationInvariants.requireAnchorPreservation(identity, listOf(MidiMutation(MidiMutationOperation.REMOVE, anchor, before, null, MidiMutationReasonCode.DENSITY_REDUCTION)))
        }
    }

    @Test
    fun `budget window and timing validators reject one over limit`() {
        val source = root.resolve("windows.mid").also(::writePhraseMidi)
        val identity = MelodyIdentityBuilder.build(source, 480, listOf(MelodyOccurrenceWindow("A1", 0, 2_000)))
        val note = identity.notes.first()
        val before = values(note)
        val mutation = MidiMutation(MidiMutationOperation.TIMING, note.id, before, before.copy(endTick = 2_001), MidiMutationReasonCode.TRANSITION_SMOOTHING)

        assertThrows(IllegalArgumentException::class.java) { MidiMutationInvariants.requireBudget(MidiMutationBudget(10, 6, 0, 0, 5, 0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { MidiMutationInvariants.requireOccurrenceWindow(identity, mutation) }
        val changedTiming = Sequence(Sequence.PPQ, 480).also { it.createTrack().add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 1)) }
        val originalTiming = Sequence(Sequence.PPQ, 480).also { it.createTrack().add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0)) }
        assertThrows(IllegalArgumentException::class.java) { MidiMutationInvariants.requireTempoMeterPreserved(originalTiming, changedTiming) }
    }

    @Test
    fun `mutation reports reject duplicate operations invalid hashes unknown reason codes and control path leakage`() {
        val id = MelodyNoteId.derive("a".repeat(64), 0, 0, 0, 60, 0, 480)
        val change = MidiMutation(MidiMutationOperation.VELOCITY, id, MidiMutationValues(0, 60, 80, 0, 480), MidiMutationValues(0, 60, 82, 0, 480), MidiMutationReasonCode.PHRASE_SHAPING)
        val valid = MidiMutationReport(inputSha256 = "a".repeat(64), outputSha256 = "b".repeat(64), contextSha256 = "c".repeat(64), target = "part-A", stage = MidiMutationStage.ENHANCE,
            mutations = listOf(change), budget = MidiMutationBudget(1, 1, 0, 0, 1, 0, 0))
        valid.requireValid()

        assertThrows(IllegalArgumentException::class.java) { valid.copy(mutations = listOf(change, change)).requireValid() }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(inputSha256 = "bad").requireValid() }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(warnings = listOf("/private/path")).requireValid() }
        assertThrows(Exception::class.java) {
            Json.decodeFromString<MidiMutationReport>("""{"inputSha256":"${"a".repeat(64)}","contextSha256":"${"c".repeat(64)}","target":"part-A","stage":"ENHANCE","mutations":[],"budget":{"originalNoteCount":0,"changedNotes":0,"additions":0,"deletions":0,"maximumChanges":0,"maximumAdditions":0,"maximumDeletions":0},"rejectionSummary":["RAW_MODEL_PROSE"]}""")
        }
        assertEquals(valid.mutations.sortedWith(compareBy<MidiMutation> { it.noteId.value }.thenBy { it.operation.ordinal }), valid.mutations)
    }

    private fun values(note: MelodyIdentityNote) = MidiMutationValues(note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)

    private fun writeComplexMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val left = sequence.createTrack()
        val right = sequence.createTrack()
        note(left, 0, 60, 80, 0, 480)
        note(left, 0, 64, 80, 0, 480)
        note(left, 0, 60, 70, 240, 720)
        note(left, 1, 60, 75, 0, 480)
        note(right, 0, 60, 80, 0, 240)
        note(right, 0, 60, 80, 240, 480)
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writePhraseMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        note(track, 0, 60, 80, 0, 240)
        note(track, 0, 62, 80, 240, 480)
        note(track, 0, 64, 80, 960, 1_440)
        note(track, 0, 67, 80, 1_680, 1_920)
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun note(track: javax.sound.midi.Track, channel: Int, pitch: Int, velocity: Int, start: Long, end: Long) {
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), end))
    }
}
