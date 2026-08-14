package ai.music.workstation.application

import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.preparation.DetectedInput
import ai.music.workstation.preparation.InputContainer
import ai.music.workstation.preparation.InputInspectionBoundary
import ai.music.workstation.preparation.InputInspectionError
import ai.music.workstation.preparation.InputInspectionErrorCode
import ai.music.workstation.preparation.InputInspectionReport
import ai.music.workstation.preparation.InputInspectionReportStore
import ai.music.workstation.preparation.InputInspectionRequest
import ai.music.workstation.preparation.InputInspectionResult
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class InputInspectionApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `inspection atomically saves a validated report and retries reuse its fingerprint`() = runBlocking {
        var calls = 0
        val service = service(InputInspectionBoundary { request ->
            calls++
            InputInspectionResult.Inspected(report(request))
        })
        val root = project(service)

        val first = service.inspectPart(InspectPartRequest(root, "A"))
        val retry = service.inspectPart(InspectPartRequest(root, "A"))

        assertEquals(1, calls)
        assertTrue(first.parts.single().preparation.inspected)
        assertTrue(retry.parts.single().preparation.sourcePreserved)
        assertEquals("source/A.mid", InputInspectionReportStore.read(root, "A").source.relativePath)
    }

    @Test
    fun `failed inspection preserves the old report and stale source is never reused`() = runBlocking {
        var reject = false
        val service = service(InputInspectionBoundary { request ->
            if (reject) InputInspectionResult.Rejected(InputInspectionError(InputInspectionErrorCode.DECODING_FAILED, "Cannot decode input."))
            else InputInspectionResult.Inspected(report(request))
        })
        val root = project(service)
        service.inspectPart(InspectPartRequest(root, "A"))
        val oldReport = Files.readString(root.resolve("prepared/A/report.json"))

        writeMidi(root.resolve("source/A.mid"), 64)
        reject = true
        assertFailsWith<IllegalStateException> { service.inspectPart(InspectPartRequest(root, "A")) }

        assertEquals(oldReport, Files.readString(root.resolve("prepared/A/report.json")))
        val snapshot = service.open(root).parts.single().preparation
        assertFalse(snapshot.inspected)
        assertTrue(snapshot.warnings.single().contains("stale"))
    }

    @Test
    fun `malformed worker measurements never replace a report`() = runBlocking {
        val service = service(InputInspectionBoundary { request ->
            InputInspectionResult.Inspected(report(request).copy(durationSeconds = Double.NaN))
        })
        val root = project(service)

        assertFailsWith<IllegalArgumentException> { service.inspectPart(InspectPartRequest(root, "A")) }

        assertFalse(Files.exists(root.resolve("prepared/A/report.json")))
        assertTrue(Files.isRegularFile(root.resolve("source/A.mid")))
    }

    @Test
    fun `snapshot derives preparation stages from validated artifacts and report`() = runBlocking {
        val service = service(InputInspectionBoundary { InputInspectionResult.Inspected(report(it)) })
        val root = project(service)
        service.inspectPart(InspectPartRequest(root, "A"))
        val inspected = service.open(root).parts.single().preparation
        assertTrue(inspected.sourcePreserved && inspected.inspected && inspected.cleanMidi)
        assertFalse(inspected.rawMidi || inspected.analyzed || inspected.ready)

        val analyzed = service.analyzePart(AnalyzePartRequest(root, "A")).parts.single().preparation
        assertTrue(analyzed.analyzed && analyzed.ready)
    }

    @Test
    fun `legacy project with no report has a truthful uninspected snapshot`() {
        val root = tempDir.resolve("legacy")
        Files.createDirectories(root.resolve("parts"))
        writeMidi(root.resolve("parts/A.mid"), 60)
        ProjectStore.write(root, Project(name = "legacy", parts = listOf(Part("A", "parts/A.mid"))))
        val service = service(InputInspectionBoundary { error("not called") })

        val preparation = service.open(root).parts.single().preparation

        assertFalse(preparation.sourcePreserved || preparation.inspected || preparation.ready)
        assertTrue(preparation.warnings.isEmpty())
    }

    @Test
    fun `inspection participates in the per project mutation mutex`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = service(InputInspectionBoundary { request ->
            started.complete(Unit)
            release.await()
            InputInspectionResult.Inspected(report(request))
        })
        val root = project(service)
        val inspecting = async { service.inspectPart(InspectPartRequest(root, "A")) }
        started.await()

        assertFailsWith<IllegalArgumentException> { service.saveStructure(SaveStructureRequest(root, emptyList())) }
        release.complete(Unit)
        inspecting.await()
    }

    private fun project(service: ProjectApplicationService): Path {
        val root = tempDir.resolve("song-${System.nanoTime()}")
        service.create(CreateProjectRequest(root))
        val input = tempDir.resolve("input-${System.nanoTime()}.mid")
        writeMidi(input, 60)
        runBlocking { service.importPart(ImportPartRequest(root, "A", input)) }
        return root
    }

    private fun service(inspector: InputInspectionBoundary): ProjectApplicationService = DefaultProjectApplicationService(
        midiPreparation = object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = error("not used")
            override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
        },
        legacyPartAnalysis = LegacyPartAnalysisService { error("not used") },
        inputInspection = inspector
    )

    private fun report(request: InputInspectionRequest) = InputInspectionReport(
        partId = request.partId,
        source = request.source,
        detectedInput = DetectedInput(InputContainer.MIDI, "SMF_1", "mid"),
        durationSeconds = 1.0,
        toolVersions = mapOf("input-inspector" to "1.0")
    )

    private fun writeMidi(path: Path, note: Int) {
        Files.createDirectories(checkNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }
}
