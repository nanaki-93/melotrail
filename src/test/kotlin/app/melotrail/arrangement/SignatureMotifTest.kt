package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class SignatureMotifTest {
    @TempDir lateinit var root: Path

    @Test
    fun `confirmed motif produces reproducible lineage metrics and a clear surviving occurrence`() {
        val midi = root.resolve("source.mid").also(::writeSource)
        val identity = MelodyIdentityBuilder.build(midi, canonicalBeatTicks = 480)
        val phrase = identity.phrases.single()
        val motif = SignatureMotif(partId = "A", sourceSha256 = identity.sourceSha256, phraseId = phrase.id, sourceNoteIds = phrase.noteIds).confirm()
        val occurrence = candidate(identity, "verse-1", 1_920)

        val first = SignatureMotifRecognizer.evaluate(identity, motif, listOf(occurrence))
        val second = SignatureMotifRecognizer.evaluate(identity, motif, listOf(occurrence))

        assertEquals(first, second)
        assertTrue(first.passed)
        assertEquals(1, first.clearOccurrenceCount)
        val report = first.occurrenceReports.single()
        assertEquals(1.0, report.intervalContourSimilarity)
        assertEquals(1.0, report.rhythmSimilarity)
        assertEquals(1.0, report.anchorRetention)
        assertEquals(1.0, report.matchedNoteCoverage)
        assertTrue(report.lineage.all { it.status == SignatureMotifLineageStatus.MATCHED })
    }

    @Test
    fun `anchor loss is explicit in lineage and blocks the release gate`() {
        val midi = root.resolve("source.mid").also(::writeSource)
        val identity = MelodyIdentityBuilder.build(midi, canonicalBeatTicks = 480)
        val phrase = identity.phrases.single()
        val motif = SignatureMotif(partId = "A", sourceSha256 = identity.sourceSha256, phraseId = phrase.id, sourceNoteIds = phrase.noteIds).confirm()
        val original = candidate(identity, "verse-1", 0)
        val missingFirstAnchor = original.copy(notes = original.notes.drop(1))

        val result = SignatureMotifRecognizer.evaluate(identity, motif, listOf(missingFirstAnchor))

        assertFalse(result.passed)
        assertTrue("no-clear-surviving-occurrence" in result.reasons)
        assertTrue("motif-anchor-loss" in result.reasons)
        assertTrue(result.occurrenceReports.single().lineage.any { it.status == SignatureMotifLineageStatus.MISSING })
    }

    private fun candidate(identity: MelodyIdentity, occurrence: String, offset: Long): SignatureMotifCandidateOccurrence =
        SignatureMotifCandidateOccurrence(occurrence, offset, identity.notes.sortedBy { it.originalStartTick }.mapIndexed { index, note ->
            SignatureMotifCandidateNote("c-" + (index + 1).toString(16).padStart(64, '0'), note.pitch, note.originalStartTick + offset, note.originalEndTick + offset)
        })

    private fun writeSource(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        listOf(60 to 0L, 64 to 480L, 67 to 960L, 72 to 1_440L).forEachIndexed { index, (pitch, tick) ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), tick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), tick + if (index == 3) 480 else 240))
        }
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
    }
}
