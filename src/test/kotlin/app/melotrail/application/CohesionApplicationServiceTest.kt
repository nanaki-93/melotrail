package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.BridgeType
import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.EnergyContour
import app.melotrail.arrangement.HarmonicHandoff
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.PartAnalysisReference
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RhythmicGesture
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.TimingHandoff
import app.melotrail.arrangement.TransitionRoleAction
import app.melotrail.arrangement.TransitionBridgePlan
import app.melotrail.arrangement.TransitionCohesionInput
import app.melotrail.arrangement.TransitionCohesionInputFactory
import app.melotrail.arrangement.TransitionCohesionPlan
import kotlinx.coroutines.runBlocking
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
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class CohesionApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test fun `each adjacent boundary needs current explicit review before aggregate approval`() = runBlocking {
        project(listOf("A", "A", "A"))
        arrange()
        val service = DefaultCohesionApplicationService(::plan)
        val sourceBefore = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("midi/clean/A.mid"))).joinToString("") { "%02x".format(it) }
        val draft = service.generate(GenerateCohesionRequest(root, CohesionPlannerKind.QWEN))
        assertFalse(draft.approved)
        assertEquals(listOf("occ-A-1" to "occ-A-2", "occ-A-2" to "occ-A-3"), draft.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        assertTrue(draft.boundaries.all { Files.isRegularFile(it.bridgeMidi) && !it.reviewed })
        service.reviewBoundary(root, "occ-A-1", "occ-A-2")
        assertThrows(IllegalArgumentException::class.java) { service.approve(root) }
        service.reviewBoundary(root, "occ-A-2", "occ-A-3")
        assertTrue(service.approve(root).approved)
        assertEquals(sourceBefore, java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("midi/clean/A.mid"))).joinToString("") { "%02x".format(it) })
    }

    @Test fun `Cohesion rejects an absent or rerun arrangement`() = runBlocking {
        project(listOf("A", "A"))
        val service = DefaultCohesionApplicationService(::plan)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { service.generate(GenerateCohesionRequest(root)) } }

        arrange()
        service.generate(GenerateCohesionRequest(root)).boundaries.forEach { boundary ->
            service.reviewBoundary(root, boundary.outgoingInstanceId, boundary.incomingInstanceId)
        }
        service.approve(root)
        arrange()

        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.generate(GenerateCohesionRequest(root)).approved)
    }

    @Test fun `reordered structure invalidates draft identity and one occurrence needs no model plan`() = runBlocking {
        project(listOf("A", "A")); arrange(); val service = DefaultCohesionApplicationService(::plan)
        val first = service.generate(GenerateCohesionRequest(root))
        val project = ProjectStore.read(root); ProjectStore.write(root, project.copy(envelope = project.envelope.copy(structureOccurrences = project.envelope.structureOccurrences.take(1))))
        arrange()
        val single = service.generate(GenerateCohesionRequest(root))
        assertFalse(first.inputHash == single.inputHash)
        assertTrue(single.boundaries.isEmpty())
        assertTrue(service.approve(root).approved)
    }

    @Test fun `rejected cohesion is stale evidence and generation retries from current input`() = runBlocking {
        project(listOf("A", "A")); arrange()
        val service = DefaultCohesionApplicationService(::plan)
        service.generate(GenerateCohesionRequest(root))
        service.reject(root)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in ProjectStore.read(root).workflow.stale)
        assertFalse(service.regenerate(GenerateCohesionRequest(root)).approved)
    }

    private fun plan(input: TransitionCohesionInput): TransitionCohesionPlan = TransitionCohesionPlan(
        inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256,
        model = CohesionModelIdentity("qwen", "1", "1".repeat(64)),
        boundaries = input.boundaries.map { b -> TransitionBridgePlan(b.outgoingInstanceId, b.incomingInstanceId, b.outgoing.sourceHash, b.incoming.sourceHash, input.arrangementSha256, input.contextSha256, TransitionRoleAction.DRUM_FILL, BridgeType.DRUM_FILL, 1, "drums", HarmonicHandoff.HOLD, RhythmicGesture.FILL, EnergyContour.RISE, TimingHandoff.PRESERVE, TimingHandoff.PRESERVE, "Carry energy into the next section") }
    )

    private fun project(structure: List<String>) {
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean")); Files.createDirectories(root.resolve("analysis"))
        writeMidi(root.resolve("source/A.mid")); Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        val analysis = MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A")
        Files.writeString(root.resolve("analysis/A.midi.json"), Json.encodeToString(MidiAnalysis.serializer(), analysis))
        ProjectStore.write(root, Project(Project.CURRENT_VERSION, "cohesion", listOf(Part("A", "source/A.mid", analysis = PartAnalysisReference("analysis/A.midi.json", AnalysisKind.MIDI), midi = MidiReferences(clean = "midi/clean/A.mid"))), structure, RenderFormat()))
    }
    private suspend fun arrange() {
        DefaultArrangementApplicationService(libraryRoot = root).generate(
            GenerateArrangementRequest(root, instruments = listOf("piano", "drums"))
        )
    }
    private fun writeMidi(path: Path) { val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack(); track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0)); track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920)); MidiSystem.write(sequence, 1, path.toFile()) }
}
