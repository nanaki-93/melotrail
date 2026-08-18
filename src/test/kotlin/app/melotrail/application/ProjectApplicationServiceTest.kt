package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.preparation.AudioInspectionMeasurements
import app.melotrail.preparation.DetectedAudioFormat
import app.melotrail.preparation.DetectedInput
import app.melotrail.preparation.EvidenceLevel
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionReport
import app.melotrail.preparation.InputInspectionResult
import app.melotrail.preparation.SignalIndicator
import app.melotrail.preparation.SilenceEvidence
import app.melotrail.preparation.TranscriptionBoundary
import app.melotrail.preparation.TranscriptionBoundaryResult
import app.melotrail.preparation.TranscriptionEngineMetadata
import app.melotrail.preparation.TranscriptionFailureStage
import app.melotrail.preparation.TranscriptionQualityGateService
import kotlinx.coroutines.async
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
    fun `imports immutable raw MIDI and requires explicit Clean MIDI before analysis`() {
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
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        val analyzed = blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }

        val part = analyzed.parts.single()
        assertEquals(PartSourceType.MIDI, part.sourceType)
        assertEquals(PartAnalysisStatus.MIDI, part.analysis?.status)
        assertEquals(1, part.analysis?.bars)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(input)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.mid"))))
    }

    @Test
    fun `audio transcription publishes raw MIDI without implicitly invoking Clean MIDI`() {
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
    fun `Clean MIDI can retry after a worker failure without changing raw MIDI`() {
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
            blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        }
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertEquals(null, ProjectStore.read(root).parts.single().midi?.clean)
        failCleanup = false

        val retried = blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertEquals(listOf("A"), retried.parts.map { it.id })
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(audio)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.wav"))))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
    }

    @Test
    fun `Clean MIDI gives the worker a disposable raw snapshot`() {
        val root = tempDir.resolve("raw-snapshot")
        var workerInput: Path? = null
        val service = service(object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = Unit
            override suspend fun clean(input: Path, output: Path) {
                workerInput = input
                Files.copy(input, output)
            }
        })
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", midi("snapshot.mid"))) }
        val canonicalRaw = root.resolve("midi/raw/A.mid")
        val rawBefore = Files.readAllBytes(canonicalRaw)

        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertFalse(workerInput == canonicalRaw)
        assertFalse(Files.exists(requireNotNull(workerInput)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(canonicalRaw)))
    }

    @Test
    fun `Clean MIDI is one atomic boundary and does not run analysis`() {
        val service = service()
        val root = tempDir.resolve("prepare-midi")
        val input = midi("prepare-source.mid")
        val sourceBefore = Files.readAllBytes(input)
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))

        val result = blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertEquals(null, result.parts.single().analysis)
        assertEquals(MidiQualityStatus.CURRENT, result.parts.single().preparation.midiQuality.status)
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
    fun `Structure handoff is occurrence-stable idempotent and invalidates only downstream evidence`() {
        val service = service()
        val root = tempDir.resolve("song")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", midi("a.mid"))) }
        blocking { service.importPart(ImportPartRequest(root, "B", midi("b.mid"))) }
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        blocking { service.cleanMidi(CleanMidiRequest(root, "B", app.melotrail.arrangement.MidiCleanupOptions())) }
        blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }
        blocking { service.analyzePart(AnalyzePartRequest(root, "B")) }
        val sourceBefore = Files.readAllBytes(root.resolve("source/A.mid"))

        val saved = service.saveStructure(SaveStructureRequest(root, listOf("B", "A", "B")))
        val updated = service.updatePart(UpdatePartRoleRequest(root, "A", "chorus"))

        assertEquals(listOf("B1", "A1", "B2"), saved.structure.map { it.instanceId })
        assertEquals("chorus", updated.parts.single { it.id == "A" }.role)
        assertEquals(listOf("B", "A", "B"), ProjectStore.read(root).structure)
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            service.saveStructure(SaveStructureRequest(root, listOf("B", "missing")))
        }.message.orEmpty().contains("Unknown part ID"))
        assertEquals(listOf("B", "A", "B"), ProjectStore.read(root).structure)

        val initialCohesion = blocking { generateApprovedCohesion(root); DefaultCohesionApplicationService { error("not used") }.load(root) }
        Files.writeString(root.resolve("arrangement.json"), "retained arrangement evidence")
        val beforeUnchangedSave = Files.readAllBytes(root.resolve(ProjectStore.FILE_NAME))
        val unchanged = service.saveStructure(SaveStructureRequest(root, listOf("B", "A", "B")))
        assertEquals(listOf("B1", "A1", "B2"), unchanged.structure.map { it.instanceId })
        assertTrue(beforeUnchangedSave.contentEquals(Files.readAllBytes(root.resolve(ProjectStore.FILE_NAME))))

        val reordered = service.saveStructure(SaveStructureRequest(root, listOf("B", "B", "A")))
        assertEquals(listOf("B1", "B2", "A1"), reordered.structure.map { it.instanceId })
        assertTrue(Files.isRegularFile(root.resolve("arrangement.json")), "old arrangement stays inspectable")
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("source/A.mid"))))
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.COHESION in reordered.readiness.staleArtifacts)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.ARRANGEMENT in reordered.readiness.staleArtifacts)
        val reorderedCohesion = blocking { generateApprovedCohesion(root); DefaultCohesionApplicationService { error("not used") }.load(root) }
        assertFalse(initialCohesion.structureSha256 == reorderedCohesion.structureSha256)
        assertEquals(listOf("B1" to "B2", "B2" to "A1"), reorderedCohesion.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId })

        assertEquals(listOf("B1", "A1", "B2", "A2"), service.saveStructure(SaveStructureRequest(root, listOf("B", "A", "B", "A"))).structure.map { it.instanceId })
        assertEquals(listOf("A1"), service.saveStructure(SaveStructureRequest(root, listOf("A"))).structure.map { it.instanceId })
        assertTrue(service.saveStructure(SaveStructureRequest(root, emptyList())).structure.isEmpty())
    }

    @Test
    fun `Structure handoff rejects missing and stale selected-MIDI analyses`() {
        val service = service()
        val root = tempDir.resolve("structure-analysis")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", midi("structure-analysis.mid"))) }
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }

        val analyzed = ProjectStore.read(root)
        ProjectStore.write(root, analyzed.copy(parts = analyzed.parts.map { it.copy(analysis = null) }))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            service.saveStructure(SaveStructureRequest(root, listOf("A")))
        }.message.orEmpty().contains("Missing MIDI analysis"))

        blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }
        val current = ProjectStore.read(root)
        val reference = requireNotNull(current.parts.single().analysis)
        Files.writeString(root.resolve(reference.file), Json.encodeToString(MidiPartAnalyzer().analyze(midi("different-analysis.mid", 65), "A")))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            service.saveStructure(SaveStructureRequest(root, listOf("A")))
        }.message.orEmpty().contains("stale for the selected MIDI"))
    }

    @Test
    fun `Clean MIDI marks only downstream artifacts stale and keeps last known good files immutable`() {
        val service = service()
        val root = tempDir.resolve("repair-invalidation")
        val input = midi("repair-source.mid")
        val sourceHash = Files.readAllBytes(input)
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        val rawBefore = Files.readAllBytes(root.resolve("midi/raw/A.mid"))
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        service.selectMidiFeel(SelectMidiFeelRequest(root, "A", app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL))
        listOf("analysis/A.json", "midi/generated/bass.mid", "cohesion/A.json", "stems/piano.wav", "mix/dry.wav", "output/master.wav", "arrangement.json").forEach { relative ->
            val path = root.resolve(relative)
            Files.createDirectories(checkNotNull(path.parent)); Files.writeString(path, "derived")
        }

        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }

        assertTrue(sourceHash.contentEquals(Files.readAllBytes(input)))
        assertTrue(rawBefore.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
        assertTrue(Files.isRegularFile(root.resolve("midi/clean/A.mid")))
        val selectedMidi = requireNotNull(ProjectStore.read(root).parts.single().midi)
        assertEquals(app.melotrail.arrangement.MidiAiFixSelection.SKIP, selectedMidi.aiFixSelection)
        assertEquals(null, selectedMidi.aiFix)
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.CURRENT, selectedMidi.analysisInput)
        assertEquals(null, selectedMidi.feel)
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
    fun `Lo-fi Feel publishes a separate fixed artifact selects canonical analysis input and restores cleaned MIDI`() {
        val service = service()
        val root = tempDir.resolve("lofi-feel")
        val input = midi("lofi-source.mid")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", input)) }
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
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

        service.selectMidiFeel(SelectMidiFeelRequest(root, "A", app.melotrail.arrangement.MidiAnalysisInput.CURRENT))
        assertEquals(app.melotrail.arrangement.MidiAnalysisInput.CURRENT, checkNotNull(ProjectStore.read(root).parts.single().midi).analysisInput)
    }

    @Test
    fun `existing analysis file remains inspectable but cannot make stale analysis ready`() {
        val service = service()
        val root = tempDir.resolve("stale-analysis")
        service.create(CreateProjectRequest(root))
        blocking { service.importPart(ImportPartRequest(root, "A", midi("stale-analysis.mid"))) }
        blocking { service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }
        blocking { service.analyzePart(AnalyzePartRequest(root, "A")) }
        val project = ProjectStore.read(root)
        val analysis = root.resolve(requireNotNull(project.parts.single().analysis).file)
        val analysisBefore = Files.readAllBytes(analysis)
        ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(app.melotrail.arrangement.WorkflowChange.MIDI_FEEL)))

        val opened = service.open(root)

        assertTrue(Files.isRegularFile(analysis))
        assertTrue(analysisBefore.contentEquals(Files.readAllBytes(analysis)))
        assertFalse(opened.parts.single().preparation.analyzed)
        assertFalse(opened.readiness.analysesReady)
        assertTrue(app.melotrail.arrangement.WorkflowArtifact.ANALYSIS in opened.readiness.staleArtifacts)
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



    private fun service(preparation: MidiPreparationService = copyingPreparation()) = DefaultProjectApplicationService(
        preparation,
        LegacyPartAnalysisService { error("legacy worker should not be used") },
        inputInspection = InputInspectionBoundary { request ->
            val extension = request.source.relativePath.substringAfterLast('.')
            val container = if (extension == "mp3") InputContainer.MPEG_AUDIO else InputContainer.RIFF_WAVE
            InputInspectionResult.Inspected(InputInspectionReport(
                partId = request.partId,
                source = request.source,
                detectedInput = DetectedInput(container, if (container == InputContainer.MPEG_AUDIO) "MPEG" else "PCM", extension),
                durationSeconds = 0.5,
                audioFormat = DetectedAudioFormat(44_100, 2, 24),
                measurements = AudioInspectionMeasurements(
                    0.1, 0.05, 0.0, 0, 0,
                    SilenceEvidence(0, 0), SignalIndicator(EvidenceLevel.NONE, 0.0), SignalIndicator(EvidenceLevel.NONE, 0.0)
                )
            ))
        },
        transcriptionQualityGate = TranscriptionQualityGateService(TranscriptionBoundary { input, output ->
            try {
                preparation.transcribe(input, output)
                TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata("fake", "1"))
            } catch (_: Exception) {
                TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.INFERENCE)
            }
        })
    )

    private fun copyingPreparation() = object : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) { Files.copy(input, output); Unit }
        override suspend fun clean(input: Path, output: Path) { Files.copy(input, output); Unit }
    }

    private fun <T> blocking(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    private fun midi(name: String, pitch: Int = 60): Path {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, pitch, 100), 0))
        track.add(javax.sound.midi.MidiEvent(javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, 0, pitch, 0), 480))
        return tempDir.resolve(name).also { MidiSystem.write(sequence, 1, it.toFile()) }
    }

    private fun wav(name: String): Path = tempDir.resolve(name).also { path ->
        Files.write(path, byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            4, 0, 0, 0, 'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()
        ))
    }
}
