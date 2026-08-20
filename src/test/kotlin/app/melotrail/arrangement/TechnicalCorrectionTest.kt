package app.melotrail.arrangement

import app.melotrail.application.CleanMidiRequest
import app.melotrail.application.CreateProjectRequest
import app.melotrail.application.DefaultProjectApplicationService
import app.melotrail.application.DefaultTechnicalCorrectionApplicationService
import app.melotrail.application.ImportPartRequest
import app.melotrail.application.LegacyPartAnalysisService
import app.melotrail.application.MidiPreparationService
import app.melotrail.application.CreateTechnicalCorrectionRequest
import kotlinx.coroutines.runBlocking
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

class TechnicalCorrectionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `deterministic correction only applies explained technical edits and preserves input`() {
        val input = writeProblemMidi(root.resolve("input.mid"))
        val sourceBefore = Files.readAllBytes(input)
        val context = TechnicalCorrectionContextFactory.build(Project(name = "test"), "A", input)
        val plan = DeterministicTechnicalCorrectionPlanner().plan(context)

        assertTrue(plan.edits.any { it.reason == TechnicalCorrectionReason.DUPLICATE })
        assertTrue(plan.edits.any { it.reason == TechnicalCorrectionReason.OUT_OF_RANGE })
        assertTrue(plan.edits.all { it.kind in setOf(TechnicalCorrectionEditKind.REMOVE, TechnicalCorrectionEditKind.SET_PITCH, TechnicalCorrectionEditKind.SET_DURATION, TechnicalCorrectionEditKind.SET_VELOCITY) })
        assertFalse(plan.edits.any { it.reason == TechnicalCorrectionReason.STRONGLY_UNSUPPORTED })

        val output = root.resolve("corrected.mid")
        val report = TechnicalCorrectionProcessor().correct(input, output, context, plan)

        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)))
        assertTrue(Files.isRegularFile(output))
        assertEquals(context.inputSha256, report.inputSha256)
        assertFalse(report.approvalRequired)
        assertTrue(report.edits.all { it.confidence >= 0.95 })
    }

    @Test
    fun `validator rejects malformed stale and enhancement-only plans`() {
        val input = writeProblemMidi(root.resolve("input.mid"))
        val context = TechnicalCorrectionContextFactory.build(Project(name = "test"), "A", input)
        val note = context.notes.first()
        val plans = listOf(
            TechnicalCorrectionPlan(partId = "A", inputSha256 = "0".repeat(64), contextSha256 = context.contextSha256, edits = emptyList()),
            TechnicalCorrectionPlan(partId = "A", inputSha256 = context.inputSha256, contextSha256 = context.contextSha256,
                edits = listOf(TechnicalCorrectionEdit(TechnicalCorrectionEditKind.SET_PITCH, TechnicalCorrectionReason.OUT_OF_RANGE, 1.0, note.id, pitch = note.pitch))),
            TechnicalCorrectionPlan(partId = "A", inputSha256 = context.inputSha256, contextSha256 = context.contextSha256,
                edits = listOf(TechnicalCorrectionEdit(TechnicalCorrectionEditKind.REMOVE, TechnicalCorrectionReason.COLLISION, 1.0, note.id)))
        )
        plans.forEach { assertThrows(IllegalArgumentException::class.java) { it.requireValid(context) } }
    }

    @Test
    fun `corrected artifact is selectable while legacy approved AI fix remains compatibility evidence`() = runBlocking {
        val projects = projectService()
        projects.create(CreateProjectRequest(root))
        val source = writeProblemMidi(root.resolveSibling("source.mid"))
        projects.importPart(ImportPartRequest(root, "A", source))
        projects.cleanMidi(CleanMidiRequest(root, "A", MidiCleanupOptions()))
        val correction = DefaultTechnicalCorrectionApplicationService()

        val created = correction.create(CreateTechnicalCorrectionRequest(root, "A"))
        assertTrue(created.available)
        assertTrue(created.selected)
        val selected = correction.selectCorrected(root, "A")

        assertTrue(selected.selected)
        assertEquals(TechnicalCorrectionSelection.CORRECTED, ProjectStore.read(root).parts.single().midi!!.technicalCorrectionSelection)
        assertEquals(SelectedMidiArtifactKind.CORRECTED, SelectedMidiArtifactResolver().resolve(root, ProjectStore.read(root), "A").kind)

        val legacy = ProjectStore.read(root).copy(parts = ProjectStore.read(root).parts.map { part ->
            part.copy(midi = part.midi!!.copy(aiFixSelection = MidiAiFixSelection.APPROVED,
                aiFix = MidiAiFixReferences(part.midi!!.technicalCorrection!!.input.sha256, approved = WorkflowArtifactReference("midi/ai-fix/A/approved.mid", part.midi!!.technicalCorrection!!.output.sha256))))
        })
        assertFalse(LegacyV3StageRunMapper.map(legacy).any { it.stage == StageId.CORRECTED && it.artifactPath.contains("ai-fix") })
    }

    private fun projectService() = DefaultProjectApplicationService(
        object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Files.copy(input, output).let { Unit }
            override suspend fun clean(input: Path, output: Path) = Files.copy(input, output).let { Unit }
        },
        LegacyPartAnalysisService { error("unused") }
    )

    private fun writeProblemMidi(path: Path): Path {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        fun note(pitch: Int, start: Long, end: Long) {
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 100), start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), end))
        }
        note(60, 0, 480); note(60, 0, 480) // exact duplicate
        note(120, 960, 1_440) // outside the conservative piano range
        MidiSystem.write(sequence, 1, path.toFile()); return path
    }
}
