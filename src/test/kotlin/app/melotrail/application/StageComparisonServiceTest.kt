package app.melotrail.application

import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.sha256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class StageComparisonServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `compares identical timing pitch additions and deletions deterministically`() {
        val before = write("before.mid", listOf(Note(0, 120, 60, 80), Note(240, 360, 64, 90, 1)))
        val identical = write("identical.mid", listOf(Note(0, 120, 60, 80), Note(240, 360, 64, 90, 1)))
        val changed = write("changed.mid", listOf(Note(12, 132, 62, 75), Note(480, 600, 67, 100, 2)))

        val same = service().compare(root, historical(before), historical(identical))
        val report = service().compare(root, historical(before), historical(changed))

        assertEquals(0, same.totalDetailRows)
        assertEquals(1, report.metrics.modifications)
        assertEquals(1, report.metrics.additions)
        assertEquals(1, report.metrics.deletions)
        assertEquals(12, report.metrics.timing.maximumStartTickDelta)
        assertEquals(StageEvidenceStatus.STALE_HISTORICAL, report.evidenceStatus)
        assertEquals(report.details.sortedWith(compareBy<StageComparisonDetail> { it.occurrenceId.orEmpty() }.thenBy { it.role.orEmpty() }.thenBy { it.tick }.thenBy { it.noteId }.thenBy { it.change.ordinal }), report.details)
    }

    @Test
    fun `repeated occurrences retain occurrence-scoped metrics`() {
        val before = write("before.mid", listOf(Note(0, 120, 60, 80)))
        val after = write("after.mid", listOf(Note(0, 120, 62, 80)))

        val report = service().compare(root,
            historical(before, occurrence = "verse-1", role = "piano"),
            historical(after, occurrence = "verse-2", role = "piano"))

        assertEquals("occurrence-verse-2-noteCount", report.metrics.occurrenceMetrics.single().name)
        assertEquals("role-piano-noteCount", report.metrics.roleMetrics.single().name)
        assertEquals("verse-2", report.details.single().occurrenceId)
    }

    @Test
    fun `caps detailed rows while retaining aggregate totals`() {
        val before = write("many.mid", (0 until 501).map { index -> Note(index * 20L, index * 20L + 10, 48 + index % 12, 80) })
        val after = write("empty.mid", emptyList())

        val report = service().compare(root, historical(before), historical(after))

        assertTrue(report.truncated)
        assertEquals(501, report.totalDetailRows)
        assertEquals(500, report.details.size)
        assertEquals(501, report.metrics.deletions)
        assertTrue(report.warnings.contains(StageComparisonWarningCode.DETAIL_ROWS_TRUNCATED))
    }

    @Test
    fun `rejects unsafe and mismatched evidence`() {
        val midi = write("good.mid", listOf(Note(0, 120, 60, 80)))
        val wrongHash = WorkflowArtifactReference(midi.fileName.toString(), "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { service().compare(root, historical(midi), historical(midi, reference = wrongHash)) }
        assertThrows(IllegalArgumentException::class.java) {
            StageComparisonArtifact(StageComparisonStage.AI_FIX, WorkflowArtifactReference("../outside.mid", sha256(midi)), CONTEXT, StageEvidenceStatus.STALE_HISTORICAL)
        }
    }

    @Test
    fun `report hash and serialization are stable after relocation`() {
        val left = root.resolve("left").also(Files::createDirectories)
        val right = root.resolve("right").also(Files::createDirectories)
        val leftBefore = write(left, "before.mid", listOf(Note(0, 120, 60, 80)))
        val leftAfter = write(left, "after.mid", listOf(Note(0, 120, 63, 80)))
        val rightBefore = right.resolve("before.mid").also { Files.copy(leftBefore, it) }
        val rightAfter = right.resolve("after.mid").also { Files.copy(leftAfter, it) }

        val first = service().compare(left, historical(leftBefore), historical(leftAfter))
        val second = service().compare(right, historical(rightBefore), historical(rightAfter))

        assertEquals(first.reportSha256, second.reportSha256)
        assertEquals(Json.encodeToString(StageComparisonReport.serializer(), first), Json.encodeToString(StageComparisonReport.serializer(), second))
    }

    @Test
    fun `persists comparison beside owning evidence without changing its result`() {
        val before = write("before.mid", listOf(Note(0, 120, 60, 80)))
        val after = write("after.mid", listOf(Note(0, 120, 61, 80)))
        val report = service().compare(root, historical(before), historical(after))

        val stored = StageComparisonReportStore.write(root, historical(after), report)

        assertEquals("after.comparison.json", stored.file)
        assertTrue(Files.isRegularFile(root.resolve(stored.file)))
        assertFalse(Files.exists(root.resolve("project.json")))
    }

    private fun service() = StageComparisonService()
    private fun historical(path: Path, occurrence: String? = null, role: String? = null, reference: WorkflowArtifactReference = WorkflowArtifactReference(path.fileName.toString(), sha256(path))) =
        StageComparisonArtifact(StageComparisonStage.HUMANIZATION, reference, CONTEXT, StageEvidenceStatus.STALE_HISTORICAL, role, occurrence)

    private fun write(name: String, notes: List<Note>) = write(root, name, notes)
    private fun write(directory: Path, name: String, notes: List<Note>): Path {
        val path = directory.resolve(name); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, note.channel, note.pitch, note.velocity), note.start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, note.channel, note.pitch, 0), note.end))
        }
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
        return path
    }

    private data class Note(val start: Long, val end: Long, val pitch: Int, val velocity: Int, val channel: Int = 0)
    private companion object { val CONTEXT = "a".repeat(64) }
}
