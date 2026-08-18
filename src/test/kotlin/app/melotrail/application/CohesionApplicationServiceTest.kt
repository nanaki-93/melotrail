package app.melotrail.application

import app.melotrail.arrangement.CohesionModelIdentity
import app.melotrail.arrangement.DeterministicMelodyCohesionPlanner
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.OccurrenceMidiArtifactResolver
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.PartAnalysisReference
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.MelodyCohesionInputFactory
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.AnalysisKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun `deterministic cohesion auto approves every occurrence and resolver keeps repeated occurrences distinct`() = runBlocking {
        project()
        val snapshot = DefaultCohesionApplicationService().generate(GenerateCohesionRequest(root))

        assertTrue(snapshot.approved)
        assertFalse(snapshot.approvalRequired)
        assertEquals(listOf("A1", "A2"), snapshot.occurrences.map { it.instanceId })
        assertTrue(snapshot.structureSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(snapshot.occurrences.all { it.sourceHash.matches(Regex("[0-9a-f]{64}")) })
        assertEquals(listOf("A1" to "A2"), snapshot.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        val project = ProjectStore.read(root)
        val input = cohesionInput(project)
        assertEquals(snapshot.structureSha256, input.structureSha256)
        assertEquals(listOf("A1" to "A2"), input.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        assertTrue(input.occurrences.all { it.analysisSha256.matches(Regex("[0-9a-f]{64}")) })
        val resolved = OccurrenceMidiArtifactResolver().resolve(root, project, input)
        assertEquals(listOf("A1", "A2"), resolved.map { it.occurrenceId })
        assertTrue(resolved.all { it.source.name == "APPROVED_COHESION" })
        assertTrue(resolved[0].path != resolved[1].path)
    }

    @Test
    fun `qwen cohesion remains a draft until explicit approval`() = runBlocking {
        project()
        val service = DefaultCohesionApplicationService { input ->
            DeterministicMelodyCohesionPlanner().plan(input).copy(model = CohesionModelIdentity("qwen", "1", "1".repeat(64)))
        }
        val draft = service.generate(GenerateCohesionRequest(root, CohesionPlannerKind.QWEN))
        assertFalse(draft.approved)
        assertTrue(draft.approvalRequired)
        assertTrue(service.approve(root).approved)
    }

    @Test
    fun `single and empty structures derive no cohesion boundaries`() {
        project()
        val project = ProjectStore.read(root)
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve("analysis/A.midi.json")))

        val single = MelodyCohesionInputFactory.build(
            root,
            project.copy(structure = listOf("A")),
            SongPlanningInput(project.name, project.version, mapOf("A" to analysis), listOf(SectionInstance(0, "A")), LogicalInstrument.entries.map { it.wireName })
        ).first
        val empty = MelodyCohesionInputFactory.build(
            root,
            project.copy(structure = emptyList()),
            SongPlanningInput(project.name, project.version, emptyMap(), emptyList(), LogicalInstrument.entries.map { it.wireName })
        ).first

        assertEquals(listOf("A1"), single.occurrences.map { it.instanceId })
        assertTrue(single.boundaries.isEmpty())
        assertTrue(empty.occurrences.isEmpty())
        assertTrue(empty.boundaries.isEmpty())
        assertFalse(single.structureSha256 == empty.structureSha256)
    }

    private fun project() {
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean")); Files.createDirectories(root.resolve("analysis"))
        writeMidi(root.resolve("source/A.mid")); Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        val analysis = MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A")
        Files.writeString(root.resolve("analysis/A.midi.json"), Json.encodeToString(MidiAnalysis.serializer(), analysis))
        ProjectStore.write(root, Project(
            version = Project.CURRENT_VERSION, name = "cohesion", renderFormat = RenderFormat(),
            parts = listOf(Part("A", "source/A.mid", analysis = PartAnalysisReference("analysis/A.midi.json", AnalysisKind.MIDI), midi = MidiReferences(clean = "midi/clean/A.mid"))),
            structure = listOf("A", "A")
        ))
    }

    private fun cohesionInput(project: Project): app.melotrail.arrangement.MelodyCohesionInput {
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve("analysis/A.midi.json")))
        val planning = SongPlanningInput(project.name, project.version, mapOf("A" to analysis), listOf(SectionInstance(0, "A"), SectionInstance(1, "A")), LogicalInstrument.entries.map { it.wireName })
        return MelodyCohesionInputFactory.build(root, project, planning).first
    }

    private fun writeMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }
}
