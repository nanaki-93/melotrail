package app.melotrail.application

import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.arrangement.MidiTempoChange
import app.melotrail.arrangement.MidiTimeSignature
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.TestSoundLibrary
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
        val result = DefaultArrangementApplicationService(libraryRoot = TestSoundLibrary.root()).generate(GenerateArrangementRequest(root, instruments = listOf("piano", "bass")))

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
        val service = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            qwenGlobalPlanner = app.melotrail.arrangement.DeterministicGlobalSongPlanner(),
            deterministicDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            qwenDetailedPlanner = app.melotrail.arrangement.DeterministicDetailedArrangementPlanner(),
            libraryRoot = TestSoundLibrary.root()
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
    fun `arrangements complete without Cohesion and publish exact approval context`() = runBlocking {
        val root = project("cohesion-boundary", structure = listOf("A", "A"))

        DefaultArrangementApplicationService(libraryRoot = TestSoundLibrary.root())
            .generate(GenerateArrangementRequest(root, instruments = listOf("piano", "drums")))

        val arrangement = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
            .decodeFromString(app.melotrail.arrangement.DetailedArrangement.serializer(), Files.readString(root.resolve("arrangement.json")))
        val approval = ProjectStore.read(root).workflow.arrangement!!

        assertEquals(null, arrangement.cohesion)
        assertEquals(sha256(root.resolve("arrangement.json")), approval.arrangement.sha256)
        assertTrue(approval.structureSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.occurrenceSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.contextSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(approval.planSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(arrangement.sections.first().transitionOut.type in setOf(app.melotrail.arrangement.TransitionType.BRIDGE, app.melotrail.arrangement.TransitionType.CROSSFADE))
    }

    private fun project(name: String, structure: List<String> = listOf("A")): Path {
        val root = tempDir.resolve(name)
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean"))
        writeMidi(root.resolve("source/A.mid")); writeMidi(root.resolve("midi/clean/A.mid"))
        val project = Project(
            name = name,
            parts = listOf(Part("A", "source/A.mid", "verse", midi = canonicalMidiReferences(root, "A"))),
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(structureOccurrences = structure.mapIndexed { index, partId -> StructureOccurrence("occ-$index", partId) })
        )
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
