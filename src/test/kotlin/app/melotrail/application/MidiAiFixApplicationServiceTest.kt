package app.melotrail.application

import app.melotrail.arrangement.LocalQwenClient
import app.melotrail.arrangement.LocalQwenMidiAiFixPlanner
import app.melotrail.arrangement.MidiAiFixEdit
import app.melotrail.arrangement.MidiAiFixEditKind
import app.melotrail.arrangement.MidiAiFixModelIdentity
import app.melotrail.arrangement.MidiAiFixPlan
import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.ProjectStore
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

class MidiAiFixApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `valid bounded draft preserves source evidence until explicit approval then can return to cleaned`() = runBlocking {
        val projectService = projectService()
        projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid"))
        projectService.importPart(ImportPartRequest(root, "A", source))
        projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        val cleanBefore = Files.readAllBytes(root.resolve("midi/clean/A.mid"))
        val sourceBefore = Files.readAllBytes(source)
        val service = DefaultMidiAiFixApplicationService(planner = { input ->
            MidiAiFixPlan(partId = input.partId, cleanedSha256 = input.cleanedSha256, inputHash = input.inputHash,
                model = MidiAiFixModelIdentity("fake", "1", "1".repeat(64), "Apache-2.0"),
                edits = listOf(MidiAiFixEdit(MidiAiFixEditKind.VELOCITY, noteId = input.notes.first().id, velocity = input.notes.first().velocity - 10)))
        })

        val draft = service.create(CreateMidiAiFixRequest(root, "A"))

        assertFalse(draft.approved)
        assertTrue(draft.draftAvailable)
        assertEquals(MidiAiFixSelection.SKIP, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(source)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
        assertTrue(cleanBefore.contentEquals(Files.readAllBytes(root.resolve("midi/clean/A.mid"))))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/diff.json")))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/audit.json")))
        assertTrue(Files.isRegularFile(root.resolve("midi/ai-fix/A/provenance.json")))

        val approved = service.approve(root, "A")
        assertTrue(approved.approved)
        assertEquals(MidiAiFixSelection.APPROVED, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertFalse(projectService.open(root).parts.single().preparation.analyzed)

        service.returnToCleaned(root, "A")
        assertEquals(MidiAiFixSelection.SKIP, ProjectStore.read(root).parts.single().midi!!.aiFixSelection)
        assertTrue(cleanBefore.contentEquals(Files.readAllBytes(root.resolve("midi/clean/A.mid"))))
    }

    @Test
    fun `model parser rejects unknown fields stale identity paths and unbounded edits before output`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid")); projectService.importPart(ImportPartRequest(root, "A", source)); projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        val input = app.melotrail.arrangement.MidiAiFixInputFactory.build("A", root.resolve("midi/clean/A.mid"))
        listOf(
            "{\"version\":1,\"partId\":\"A\",\"cleanedSha256\":\"${input.cleanedSha256}\",\"inputHash\":\"${input.inputHash}\",\"model\":{\"name\":\"fake\",\"version\":\"1\",\"hash\":\"${"1".repeat(64)}\",\"license\":\"Apache-2.0\"},\"edits\":[],\"path\":\"/tmp/x\"}",
            "{\"version\":1,\"partId\":\"A\",\"cleanedSha256\":\"${"0".repeat(64)}\",\"inputHash\":\"${input.inputHash}\",\"model\":{\"name\":\"fake\",\"version\":\"1\",\"hash\":\"${"1".repeat(64)}\",\"license\":\"Apache-2.0\"},\"edits\":[]}",
            "{\"version\":1,\"partId\":\"A\",\"cleanedSha256\":\"${input.cleanedSha256}\",\"inputHash\":\"${input.inputHash}\",\"model\":{\"name\":\"fake\",\"version\":\"1\",\"hash\":\"${"1".repeat(64)}\",\"license\":\"Apache-2.0\"},\"edits\":[{\"kind\":\"velocity\",\"noteId\":\"${input.notes.first().id}\",\"velocity\":127}]}")
            .forEach { response -> assertThrows(IllegalArgumentException::class.java) { LocalQwenMidiAiFixPlanner(LocalQwenClient { _, _ -> response }).plan(input) } }
        assertFalse(Files.exists(root.resolve("midi/ai-fix/A/draft.mid")))
    }

    @Test
    fun `model response omits code-owned provenance and uses the concrete edit schema`() = runBlocking {
        val projectService = projectService(); projectService.create(CreateProjectRequest(root))
        val source = writeMidi(root.resolveSibling("input.mid")); projectService.importPart(ImportPartRequest(root, "A", source)); projectService.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        val input = app.melotrail.arrangement.MidiAiFixInputFactory.build("A", root.resolve("midi/clean/A.mid"))
        val trustedModel = MidiAiFixModelIdentity("qwen", "local", "a".repeat(64), "unknown")
        val response = "{\"version\":1,\"partId\":\"${input.partId}\",\"cleanedSha256\":\"${input.cleanedSha256}\",\"inputHash\":\"${input.inputHash}\",\"edits\":[{\"kind\":\"timing\",\"noteId\":\"${input.notes.first().id}\",\"startTick\":1}]}"

        val plan = LocalQwenMidiAiFixPlanner(LocalQwenClient { _, _ -> response }, trustedModel).plan(input)

        assertEquals(trustedModel, plan.model)
        assertEquals(MidiAiFixEditKind.TIMING, plan.edits.single().kind)
        assertEquals(1, plan.edits.single().startTick)
    }

    private fun projectService() = DefaultProjectApplicationService(
        object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Files.copy(input, output).let { Unit }
            override suspend fun clean(input: Path, output: Path) = Files.copy(input, output).let { Unit }
        },
        LegacyPartAnalysisService { error("legacy analysis is not used") }
    )

    private fun writeMidi(path: Path): Path {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 480))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 720))
        MidiSystem.write(sequence, 1, path.toFile()); return path
    }
}
