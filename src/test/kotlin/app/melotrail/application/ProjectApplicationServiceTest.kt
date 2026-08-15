package app.melotrail.application

import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.cli.ArrangementProjectCommands
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence

class ProjectApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `creates opens and rejects invalid projects`() {
        val service = service()
        val root = tempDir.resolve("song")

        val created = service.create(CreateProjectRequest(root, renderFormat = RenderFormat(48_000, 1)))

        assertEquals("song", created.name)
        assertEquals(48_000, created.renderFormat?.sampleRate)
        assertTrue(Files.isDirectory(root.resolve("midi/generated")))
        assertEquals(created, service.open(root))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { service.open(tempDir.resolve("missing")) }.message.orEmpty().contains("Project file not found"))
        val invalidRoot = tempDir.resolve("invalid")
        Files.createDirectories(invalidRoot)
        Files.writeString(invalidRoot.resolve("project.json"), "{\"version\":99}")
        assertTrue(assertThrows(IllegalArgumentException::class.java) { service.open(invalidRoot) }.message.orEmpty().contains("Unsupported project version"))
    }

    @Test
    fun `imports analyzes and snapshots MIDI without changing source`() {
        val service = service()
        val root = tempDir.resolve("song")
        service.create(CreateProjectRequest(root))
        val input = midi("verse.mid")
        val sourceBefore = Files.readAllBytes(input)

        blocking { service.importPart(ImportPartRequest(root, "A", input, role = "verse")) }
        val analyzed = blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }

        val part = analyzed.parts.single()
        assertEquals(PartSourceType.MIDI, part.sourceType)
        assertEquals(PartAnalysisStatus.MIDI, part.analysis?.status)
        assertEquals(1, part.analysis?.bars)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.mid"))))
    }

    @Test
    fun `failed audio preparation preserves source without registering part`() {
        val root = tempDir.resolve("failure")
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) { Files.copy(input, output); Unit }
            override suspend fun clean(input: Path, output: Path) = error("cleanup unavailable")
        })
        service.create(CreateProjectRequest(root))
        val audio = tempDir.resolve("input.wav").also { Files.writeString(it, "original audio") }
        val before = Files.readAllBytes(audio)

        assertTrue(assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { service.importPart(ImportPartRequest(root, "A", audio, transcribe = true)) }
        }.message.orEmpty().contains("was not registered"))

        assertTrue(before.contentEquals(Files.readAllBytes(audio)))
        assertTrue(before.contentEquals(Files.readAllBytes(root.resolve("source/A.wav"))))
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertTrue(ProjectStore.read(root).parts.isEmpty())
    }

    @Test
    fun `retrying a failed import reuses its preserved source only when bytes match`() {
        val root = tempDir.resolve("retry")
        var failCleanup = true
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) { Files.copy(midi("transcribed.mid"), output); Unit }
            override suspend fun clean(input: Path, output: Path) {
                if (failCleanup) error("cleanup unavailable")
                Files.copy(input, output)
            }
        })
        service.create(CreateProjectRequest(root))
        val audio = tempDir.resolve("retry.wav").also { Files.writeString(it, "original audio") }
        val sourceBefore = Files.readAllBytes(audio)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { service.importPart(ImportPartRequest(root, "A", audio, transcribe = true)) }
        }
        failCleanup = false

        val retried = blocking { service.importPart(ImportPartRequest(root, "A", audio, transcribe = true)) }

        assertEquals(listOf("A"), retried.parts.map { it.id })
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(audio)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.wav"))))
    }

    @Test
    fun `role and complete structure updates are atomic and expose occurrence labels`() {
        val service = service()
        val root = tempDir.resolve("song")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", midi("a.mid"))) }
        blocking { service.importPart(ImportPartRequest(root, "B", midi("b.mid"))) }

        val saved = service.saveStructure(SaveStructureRequest(root, listOf("B", "A", "B")))
        val updated = service.updatePart(UpdatePartRoleRequest(root, "A", "chorus"))

        assertEquals(listOf("B1", "A1", "B2"), saved.structure.map { it.instanceId })
        assertEquals("chorus", updated.parts.single { it.id == "A" }.role)
        assertEquals(listOf("B", "A", "B"), ProjectStore.read(root).structure)
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            service.saveStructure(SaveStructureRequest(root, listOf("B", "missing")))
        }.message.orEmpty().contains("Unknown part ID"))
        assertEquals(listOf("B", "A", "B"), ProjectStore.read(root).structure)
        assertTrue(service.saveStructure(SaveStructureRequest(root, emptyList())).structure.isEmpty())
    }

    @Test
    fun `rejects a conflicting mutation while an import is preparing MIDI`() = kotlinx.coroutines.runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Unit
            override suspend fun clean(input: Path, output: Path) {
                started.complete(Unit)
                release.await()
                Files.copy(input, output)
            }
        })
        val root = tempDir.resolve("conflict")
        service.create(CreateProjectRequest(root))

        val import = async { service.importPart(ImportPartRequest(root, "A", midi("a.mid"))) }
        started.await()

        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            service.saveStructure(SaveStructureRequest(root, emptyList()))
        }.message.orEmpty().contains("Another project mutation"))

        release.complete(Unit)
        import.await()
    }

    @Test
    fun `CLI adapter and service import produce equivalent canonical artifacts`() {
        val input = midi("verse.mid")
        val serviceRoot = tempDir.resolve("service/parity")
        val cliRoot = tempDir.resolve("cli/parity")
        val service = service()
        service.create(CreateProjectRequest(serviceRoot))
        ArrangementProjectCommands.execute(arrayOf("project", "create", cliRoot.toString()))

        blocking { service.importPart(ImportPartRequest(serviceRoot, "A", input, "verse")) }
        ArrangementProjectCommands.executePartAddForTest(
            arrayOf("part", "add", cliRoot.toString(), "--id", "A", "--file", input.toString(), "--role", "verse"),
            copyingPreparation()
        )

        assertEquals(Files.readString(serviceRoot.resolve("project.json")), Files.readString(cliRoot.resolve("project.json")))
        assertTrue(Files.readAllBytes(serviceRoot.resolve("source/A.mid")).contentEquals(Files.readAllBytes(cliRoot.resolve("source/A.mid"))))
        assertTrue(Files.readAllBytes(serviceRoot.resolve("midi/clean/A.mid")).contentEquals(Files.readAllBytes(cliRoot.resolve("midi/clean/A.mid"))))
    }

    private fun service(preparation: MidiPreparationService = copyingPreparation()) = DefaultProjectApplicationService(
        preparation,
        LegacyPartAnalysisService { error("legacy worker should not be used") }
    )

    private fun copyingPreparation() = object : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) { Files.copy(input, output); Unit }
        override suspend fun clean(input: Path, output: Path) { Files.copy(input, output); Unit }
    }

    private fun <T> blocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    private fun midi(name: String): Path {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        return tempDir.resolve(name).also { MidiSystem.write(sequence, 1, it.toFile()) }
    }
}
