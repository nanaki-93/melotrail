package app.melotrail.application

import app.melotrail.arrangement.WorkflowArtifactReference
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QualityReviewEvidenceServiceTest {
    @Test
    fun `publishes hash bound MIDI WAV debug comparisons with an explicitly pending listening record`() {
        val root = Files.createTempDirectory("quality-review")
        try {
            Files.writeString(root.resolve("project.json"), "{\"version\":4}")
            val beforeMidi = writeMidi(root.resolve("midi/before.mid"), 60)
            val afterMidi = writeMidi(root.resolve("midi/after.mid"), 62)
            val dry = writeWav(root.resolve("mix/dry.wav"), byteArrayOf(1, 2, 3))
            val master = writeWav(root.resolve("output/master.wav"), byteArrayOf(3, 2, 1))
            val sourceHash = digest(beforeMidi)
            val service = QualityReviewEvidenceService()

            val recordReference = service.publishPending(root, listOf(
                QualityDebugPair("melody-connected", QualityReviewArtifactKind.MIDI, reference(root, beforeMidi), reference(root, afterMidi)),
                QualityDebugPair("dry-master", QualityReviewArtifactKind.WAV, reference(root, dry), reference(root, master))
            ), listOf("renderer not run", "listening session not recorded"))
            val record = service.load(root, recordReference)
            val decisionReference = service.recordDecision(root, recordReference,
                ListeningReviewDecision(ListeningReviewStatus.ACCEPTED, "Listener", "2026-08-25T12:00:00+08:00", "Reference headphones", "Bounded fixture review."))
            val decision = service.load(root, decisionReference)

            assertEquals(ListeningReviewStatus.PENDING_HUMAN_REVIEW, record.status)
            assertEquals(ListeningReviewStatus.ACCEPTED, decision.status)
            assertEquals(2, record.comparisons.size)
            assertEquals(sourceHash, digest(beforeMidi), "Debug publication must not mutate the selected MIDI source")
            assertTrue(record.comparisons.all { pair -> Files.isRegularFile(root.resolve(pair.debugBefore.file)) && Files.isRegularFile(root.resolve(pair.debugAfter.file)) })
            assertTrue(record.unverifiedDependencies.contains("listening session not recorded"))
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `rejects comparisons that do not include both MIDI and WAV evidence`() {
        val root = Files.createTempDirectory("quality-review-missing-wav")
        try {
            Files.writeString(root.resolve("project.json"), "{\"version\":4}")
            val before = writeMidi(root.resolve("midi/before.mid"), 60)
            val after = writeMidi(root.resolve("midi/after.mid"), 62)

            assertFailsWith<IllegalArgumentException> {
                QualityReviewEvidenceService().publishPending(root, listOf(
                    QualityDebugPair("melody-connected", QualityReviewArtifactKind.MIDI, reference(root, before), reference(root, after))
                ), emptyList())
            }
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun writeMidi(path: Path, pitch: Int): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 240))
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
        return path
    }

    private fun writeWav(path: Path, payload: ByteArray): Path {
        Files.createDirectories(requireNotNull(path.parent))
        Files.write(path, payload)
        return path
    }

    private fun reference(root: Path, path: Path) = WorkflowArtifactReference(root.relativize(path).toString(), digest(path))
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
