package ai.music.workstation.arrangement

import ai.music.workstation.application.CreateProjectRequest
import ai.music.workstation.application.DefaultProjectApplicationService
import ai.music.workstation.application.ImportPartRequest
import ai.music.workstation.application.LegacyPartAnalysisService
import ai.music.workstation.application.MidiPreparationService
import ai.music.workstation.application.MidiQualityStatus
import kotlinx.coroutines.runBlocking
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

class MidiQualityReportTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `report measures musical facts and exact timing preservation`() {
        val raw = midi("raw.mid", listOf(Note(60, 0, 480, 80), Note(64, 0, 480, 100), Note(67, 480, 960, 60)))
        val clean = midi("clean.mid", listOf(Note(60, 24, 504, 80), Note(64, 0, 480, 100)))

        val report = MidiQualityReporter().report("A", raw, clean, MidiCleanupOptions())

        assertEquals(3, report.raw.noteCount)
        assertEquals(2, report.clean.noteCount)
        assertEquals(60, report.raw.pitchRange?.min)
        assertEquals(67, report.raw.pitchRange?.max)
        assertEquals(2, report.raw.maximumPolyphony)
        assertEquals(80.0, report.raw.velocity?.mean)
        assertEquals(2, report.timing.pairedNotes)
        assertEquals(1, report.timing.removedNotes)
        assertEquals(1, report.timing.changedStarts)
        assertEquals(24, report.timing.maxStartShiftTicks)
        assertTrue(report.tempoAndTimeSignaturesPreserved)
        assertEquals(listOf(MidiQualityRecommendation.REVIEW_TIMING), report.recommendations)
    }

    @Test
    fun `report emits bounded deterministic warnings and recommendations`() {
        val wideDenseNotes = listOf(Note(0, 0, 480, 80), Note(127, 0, 480, 80)) + List(12) { Note(60, 0, 480, 80) }
        val raw = midi("noisy-raw.mid", wideDenseNotes)
        val clean = midi("noisy-clean.mid", wideDenseNotes.map { it.copy(start = 300, end = 780) })

        val report = MidiQualityReporter().report("A", raw, clean, MidiCleanupOptions())

        assertTrue(report.warnings.map { it.code }.contains(MidiQualityWarningCode.HIGH_POLYPHONY))
        assertTrue(report.warnings.map { it.code }.contains(MidiQualityWarningCode.WIDE_PITCH_RANGE))
        assertTrue(report.warnings.map { it.code }.contains(MidiQualityWarningCode.LARGE_TIMING_SHIFT))
        assertTrue(report.recommendations.contains(MidiQualityRecommendation.REVIEW_CLEANUP_PROFILE))
        assertTrue(report.recommendations.contains(MidiQualityRecommendation.REVIEW_TIMING))
        assertTrue(report.warnings.size <= MidiQualityReport.MAX_WARNINGS)
    }

    @Test
    fun `quality store detects malformed and stale fingerprints`() {
        val root = tempDir.resolve("project")
        Files.createDirectories(root.resolve("midi/raw"))
        Files.createDirectories(root.resolve("midi/clean"))
        val raw = midi(root.resolve("midi/raw/A.mid"), listOf(Note(60, 0, 480, 90)))
        val clean = midi(root.resolve("midi/clean/A.mid"), listOf(Note(60, 0, 480, 90)))
        val options = MidiCleanupOptions()
        val report = MidiQualityReporter().report("A", raw, clean, options)
        val reference = root.relativize(MidiQualityReportStore.write(root, report)).toString()

        assertTrue(MidiQualityReportStore.isCurrent(root, "A", "midi/raw/A.mid", "midi/clean/A.mid", options, reference))
        assertFalse(MidiQualityReportStore.isCurrent(root, "A", "midi/raw/A.mid", "midi/clean/A.mid", MidiCleanupOptions(profile = MidiCleanupProfile.TRANSCRIPTION_SAFE), reference))
        midi(clean, listOf(Note(61, 0, 480, 90)))
        assertFalse(MidiQualityReportStore.isCurrent(root, "A", "midi/raw/A.mid", "midi/clean/A.mid", options, reference))
        Files.writeString(root.resolve(reference), "not-json")
        assertFalse(MidiQualityReportStore.isCurrent(root, "A", "midi/raw/A.mid", "midi/clean/A.mid", options, reference))
    }

    @Test
    fun `import publishes quality provenance reloads it and rejects stale clean MIDI for arrangement readiness`() = runBlocking {
        val root = tempDir.resolve("project")
        val input = midi("input.mid", listOf(Note(60, 0, 480, 90)))
        val used = mutableListOf<MidiCleanupOptions>()
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Unit
            override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
            override suspend fun clean(input: Path, output: Path, options: MidiCleanupOptions) {
                used += options
                Files.copy(input, output)
            }
        })
        val options = MidiCleanupOptions(profile = MidiCleanupProfile.TRANSCRIPTION_SAFE, normalizeVelocity = true)
        service.create(CreateProjectRequest(root))

        val imported = service.importPart(ImportPartRequest(root, "A", input, cleanup = options))
        val stored = ProjectStore.read(root).parts.single().midi!!

        assertEquals(listOf(options), used)
        assertEquals(options, stored.cleanup)
        assertEquals("midi/quality/A.json", stored.quality)
        assertEquals(MidiQualityStatus.CURRENT, imported.parts.single().preparation.midiQuality.status)
        assertTrue(imported.readiness.midiQualityReportsReady)
        assertEquals(MidiQualityStatus.CURRENT, service.open(root).parts.single().preparation.midiQuality.status)

        midi(root.resolve(stored.clean), listOf(Note(61, 0, 480, 90)))
        assertThrows(IllegalArgumentException::class.java) { ProjectStore.read(root).requireCleanMidi(root) }
        assertEquals(MidiQualityStatus.STALE_OR_INVALID, service.open(root).parts.single().preparation.midiQuality.status)
    }

    @Test
    fun `legacy project without a report is unknown rather than corrupt`() {
        val root = tempDir.resolve("legacy")
        Files.createDirectories(root.resolve("source"))
        Files.createDirectories(root.resolve("midi/clean"))
        midi(root.resolve("source/A.mid"), listOf(Note(60, 0, 480, 90)))
        midi(root.resolve("midi/clean/A.mid"), listOf(Note(60, 0, 480, 90)))
        ProjectStore.write(root, Project(Project.CURRENT_VERSION, "legacy", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat()))

        val snapshot = service().open(root)

        assertEquals(MidiQualityStatus.LEGACY_UNKNOWN, snapshot.parts.single().preparation.midiQuality.status)
        assertFalse(snapshot.readiness.midiQualityReportsReady)
        assertEquals(1, ProjectStore.read(root).requireCleanMidi(root).size)
    }

    @Test
    fun `quality publish failure does not register the part`() = runBlocking {
        val root = tempDir.resolve("publish-failure")
        val input = midi("input.mid", listOf(Note(60, 0, 480, 90)))
        val service = service()
        service.create(CreateProjectRequest(root))
        Files.createDirectories(root.resolve("midi/quality/A.json"))

        assertThrows(Exception::class.java) { runBlocking { service.importPart(ImportPartRequest(root, "A", input)) } }

        assertTrue(ProjectStore.read(root).parts.isEmpty())
        assertTrue(Files.isRegularFile(root.resolve("midi/clean/A.mid")))
    }

    private fun service(preparation: MidiPreparationService = object : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) = Unit
        override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
    }) = DefaultProjectApplicationService(preparation, LegacyPartAnalysisService { error("legacy analysis unused") })

    private fun midi(name: String, notes: List<Note>): Path = midi(tempDir.resolve(name), notes)

    private fun midi(path: Path, notes: List<Note>): Path {
        Files.createDirectories(checkNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(0x07, 0xA1.toByte(), 0x20), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note.pitch, note.velocity), note.start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note.pitch, 0), note.end))
        }
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private data class Note(val pitch: Int, val start: Long, val end: Long, val velocity: Int)
}
