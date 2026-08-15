package ai.music.workstation.application

import ai.music.workstation.arrangement.MidiAnalysis
import ai.music.workstation.arrangement.MidiAnalysisStore
import ai.music.workstation.arrangement.MidiReferences
import ai.music.workstation.arrangement.MidiTempoChange
import ai.music.workstation.arrangement.MidiTimeSignature
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArrangementApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `deterministic generation writes an approved inspectable arrangement snapshot`() = runBlocking {
        val root = project("approved")
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
        val service = DefaultArrangementApplicationService(
            deterministicGlobalPlanner = ai.music.workstation.arrangement.DeterministicGlobalSongPlanner(),
            qwenGlobalPlanner = ai.music.workstation.arrangement.DeterministicGlobalSongPlanner(),
            deterministicDetailedPlanner = ai.music.workstation.arrangement.DeterministicDetailedArrangementPlanner(),
            qwenDetailedPlanner = ai.music.workstation.arrangement.DeterministicDetailedArrangementPlanner(),
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

    private fun project(name: String): Path {
        val root = tempDir.resolve(name)
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/clean"))
        Files.write(root.resolve("source/A.mid"), byteArrayOf(0x4d, 0x54, 0x68, 0x64))
        Files.write(root.resolve("midi/clean/A.mid"), byteArrayOf(0x4d, 0x54, 0x68, 0x64))
        val project = Project(Project.CURRENT_VERSION, name, listOf(Part("A", "source/A.mid", "verse", midi = MidiReferences(clean = "midi/clean/A.mid"))), listOf("A"), RenderFormat())
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiAnalysis(partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0, tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0, noteCount = 4, noteDensity = 0.25, rhythmicDensity = 0.5, energy = 0.5))
        return root
    }
}
