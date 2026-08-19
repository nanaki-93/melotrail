package app.melotrail.application

import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.MidiTempoChange
import app.melotrail.arrangement.MidiTimeSignature
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class ArrangementApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `deterministic generation writes an approved inspectable arrangement snapshot`() = runBlocking {
        val root = project("approved")
        generateApprovedCohesion(root)
        val result = DefaultArrangementApplicationService(libraryRoot = Path.of("sounds")).generate(GenerateArrangementRequest(root, instruments = listOf("piano", "bass")))

        assertTrue(Files.isRegularFile(root.resolve("song_plan.json")))
        assertTrue(Files.isRegularFile(root.resolve("section_variations.json")))
        assertTrue(Files.isRegularFile(root.resolve("arrangement.json")))
        assertTrue(result.approved)
        assertFalse(result.approvalRequired)
        assertFalse(result.stale)
        assertTrue(result.sections.single().instruments.any { it.name == "piano" })
    }

    @Test
    fun `Qwen mode always creates a draft that requires explicit approval`() = runBlocking {
        val root = project("draft")
        generateApprovedCohesion(root)
        val service = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            qwenGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            deterministicDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            qwenDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            libraryRoot = Path.of("sounds")
        )

        val draft = service.generate(GenerateArrangementRequest(root, ArrangementPlannerKind.QWEN, instruments = listOf("piano")))
        assertTrue(draft.approvalRequired)
        assertFalse(draft.approved)
        assertTrue(Files.isRegularFile(root.resolve("arrangement.draft.json")))
        assertFalse(Files.isRegularFile(root.resolve("arrangement.json")))

        val approved = service.approve(root)
        assertTrue(approved.approved)
        assertTrue(Files.isRegularFile(root.resolve("arrangement.json")))
    }

    @Test
    fun `new arrangements persist and consume the exact approved cohesion boundary`() = runBlocking {
        val root = project("cohesion-boundary", structure = listOf("A", "A"))
        generateApprovedCohesion(root)

        DefaultArrangementApplicationService(libraryRoot = Path.of("sounds"))
            .generate(GenerateArrangementRequest(root, instruments = listOf("piano", "drums")))

        val arrangement = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
            .decodeFromString(app.melotrail.arrangement.DetailedArrangement.serializer(), Files.readString(root.resolve("arrangement.json")))
        val cohesion = ProjectStore.read(root).workflow.cohesion!!
        val approved = cohesion.boundaries.single().approved!!

        assertEquals(cohesion.inputSha256, arrangement.cohesion!!.inputSha256)
        assertEquals(listOf("occ-A-1" to "occ-A-2"), arrangement.cohesion!!.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })
        assertEquals(approved.sha256, arrangement.cohesion!!.boundaries.single().approvedSha256)
        assertEquals(sha256(root.resolve("cohesion/boundaries/occ-A-1--occ-A-2/bridge.mid")), arrangement.cohesion!!.boundaries.single().bridgeSha256)
        assertEquals(app.melotrail.arrangement.TransitionType.BRIDGE, arrangement.sections.first().transitionOut.type)
    }

    private fun project(name: String, structure: List<String> = listOf("A")): Path {
        val root = tempDir.resolve(name)
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean"))
        writeMidi(root.resolve("source/A.mid")); writeMidi(root.resolve("midi/clean/A.mid"))
        val project = Project(Project.CURRENT_VERSION, name, listOf(Part("A", "source/A.mid", "verse", midi = MidiReferences(clean = "midi/clean/A.mid"))), structure, RenderFormat())
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A"))
        return root
    }

    private fun writeMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun sha256(path: Path): String = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
