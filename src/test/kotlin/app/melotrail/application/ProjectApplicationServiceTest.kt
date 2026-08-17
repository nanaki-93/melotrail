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
    fun `imports immutable raw MIDI and requires explicit repair before analysis`() {
        val service = service()
        val root = tempDir.resolve("song")
        service.create(CreateProjectRequest(root))
        val input = midi("verse.mid")
        val sourceBefore = Files.readAllBytes(input)

        val imported = blocking { service.importPart(ImportPartRequest(root, "A", input, role = "verse")) }
        assertTrue(Files.isRegularFile(root.resolve("midi/raw/A.mid")))
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertFalse(imported.readiness.cleanMidiReady)
        assertThrows(IllegalArgumentException::class.java) { blocking { service.analyzePart(AnalyzePartRequest(root, "A")) } }
        blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        val analyzed = blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }

        val part = analyzed.parts.single()
        assertEquals(PartSourceType.MIDI, part.sourceType)
        assertEquals(PartAnalysisStatus.MIDI, part.analysis?.status)
        assertEquals(1, part.analysis?.bars)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.mid"))))
    }

    @Test
    fun `audio transcription publishes raw MIDI without implicitly invoking repair`() {
        val root = tempDir.resolve("failure")
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) { Files.copy(midi("transcribed.mid"), output); Unit }
            override suspend fun clean(input: Path, output: Path) = error("cleanup must not run during import")
        })
        service.create(CreateProjectRequest(root))
        val audio = wav("input.wav")
        val before = Files.readAllBytes(audio)

        blocking { service.importPart(ImportPartRequest(root, "A", audio, transcribe = true)) }

        assertTrue(before.contentEquals(Files.readAllBytes(audio)))
        assertTrue(before.contentEquals(Files.readAllBytes(root.resolve("source/A.wav"))))
        assertTrue(Files.isRegularFile(root.resolve("midi/raw/A.mid")))
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertEquals(listOf("A"), ProjectStore.read(root).parts.map { it.id })
    }

    @Test
    fun `repair can retry after a worker failure without changing raw MIDI`() {
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
        val audio = wav("retry.wav")
        val sourceBefore = Files.readAllBytes(audio)

        blocking { service.importPart(ImportPartRequest(root, "A", audio, transcribe = true)) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        assertThrows(IllegalStateException::class.java) {
            blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        }
        failCleanup = false

        val retried = blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertEquals(listOf("A"), retried.parts.map { it.id })
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(audio)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.wav"))))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
    }

    @Test
    fun `Prepare MIDI atomically repairs then analyzes without mutating source or raw evidence`() {
        val service = service()
        val root = tempDir.resolve("prepare-midi")
        val input = midi("prepare-source.mid")
        val sourceBefore = Files.readAllBytes(input)
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))

        val result = blocking { service.prepareMidi(PrepareMidiRequest(root, "A")) }

        assertEquals(PrepareMidiOutcome.READY_FOR_STRUCTURE, result.outcome)
        assertEquals(PartAnalysisStatus.MIDI, result.project.parts.single().analysis?.status)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.mid"))))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
    }

    @Test
    fun `import rejects an extension disguised as MIDI before publishing source evidence`() {
        val service = service()
        val root = tempDir.resolve("invalid-midi")
        val invalid = tempDir.resolve("not-midi.mid").also { Files.writeString(it, "not a MIDI file") }
        service.create(CreateProjectRequest(root))

        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            blocking { service.importPart(ImportPartRequest(root, "A", invalid)) }
        }.message.orEmpty().contains("MIDI import did not create a MIDI file"))
        assertFalse(Files.exists(root.resolve("source/A.mid")))
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
    fun `repair marks only downstream artifacts stale and keeps last known good files immutable`() {
        val service = service()
        val root = tempDir.resolve("repair-invalidation")
        val input = midi("repair-source.mid")
        val sourceHash = Files.readAllBytes(input)
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        listOf("analysis/A.json", "midi/generated/bass.mid", "cohesion/A.json", "stems/piano.wav", "mix/dry.wav", "output/master.wav", "arrangement.json").forEach { relative ->
            val path = root.resolve(relative)
            Files.createDirectories(checkNotNull(path.parent)); Files.writeString(path, "derived")
        }

        blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertTrue(sourceHash.contentEquals(Files.readAllBytes(input)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
        assertTrue(Files.isRegularFile(root.resolve("midi/clean/A.mid")))
        listOf("analysis/A.json", "midi/generated/bass.mid", "cohesion/A.json", "stems/piano.wav", "mix/dry.wav", "output/master.wav", "arrangement.json").forEach { relative ->
            assertTrue(Files.exists(root.resolve(relative)), "$relative must remain inspectable after invalidation")
        }
        val stale = service.open(root).readiness.staleArtifacts
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.ANALYSIS in stale)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in stale)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.ARRANGEMENT in stale)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.MASTER in stale)
    }

    @Test
    fun `Lo-fi Feel publishes a separate fixed artifact selects canonical analysis input and restores repaired MIDI`() {
        val service = service()
        val root = tempDir.resolve("lofi-feel")
        val input = midi("lofi-source.mid")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        blocking { service.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        val cleanBefore = Files.readAllBytes(root.resolve("midi/clean/A.mid"))
        Files.createDirectories(root.resolve("analysis")); Files.writeString(root.resolve("analysis/A.json"), "stale")

        val selected = service.selectMidiFeel(SelectMidiFeelRequest(root, "A", app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL))
        val midi = checkNotNull(ProjectStore.read(root).parts.single().midi)
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL, midi.analysisInput)
        assertTrue(Files.isRegularFile(root.resolve(checkNotNull(midi.feel).derived)))
        assertTrue(Files.isRegularFile(root.resolve(midi.feel.report)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
        assertTrue(cleanBefore.contentEquals(Files.readAllBytes(root.resolve("midi/clean/A.mid"))))
        assertTrue(Files.exists(root.resolve("analysis/A.json")))
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.ANALYSIS in selected.readiness.staleArtifacts)
        assertFalse(selected.parts.single().preparation.analyzed)

        service.selectMidiFeel(SelectMidiFeelRequest(root, "A", app.melotrail.arrangement.MidiAnalysisInput.REPAIRED))
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.REPAIRED, checkNotNull(ProjectStore.read(root).parts.single().midi).analysisInput)
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
        assertTrue(Files.readAllBytes(serviceRoot.resolve("midi/raw/A.mid")).contentEquals(Files.readAllBytes(cliRoot.resolve("midi/raw/A.mid"))))
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

    private fun wav(name: String): Path = tempDir.resolve(name).also { path ->
        Files.write(path, byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            4, 0, 0, 0, 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()
        ))
    }
}
