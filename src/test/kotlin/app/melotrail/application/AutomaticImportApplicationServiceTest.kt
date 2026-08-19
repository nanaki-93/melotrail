package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageRunStore
import app.melotrail.preparation.DetectedInput
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionReport
import app.melotrail.preparation.InputInspectionResult
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticImportApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `source-first import records durable stages and automatically cleans immutable direct MIDI`() = runBlocking {
        val input = tempDir.resolve("source.mid").also { writeMidi(it, 60) }
        val service = service()
        val root = tempDir.resolve("project")
        service.create(CreateProjectRequest(root))

        val result = service.importSongPart(ImportSongPart(
            root = root,
            id = "intro",
            file = input,
            name = "Intro piano",
            sectionType = SectionTypeId("intro")
        ))

        repeat(50) {
            if (StageRunStore().read(root, ProjectStore.read(root).envelope.stageRuns).any { run -> run.stage == StageId.CLEANED && run.status == StageRunStatus.COMPLETED }) return@repeat
            delay(10)
        }
        val project = ProjectStore.read(root)
        val part = project.parts.single()
        val runs = StageRunStore().read(root, project.envelope.stageRuns)

        assertEquals("intro", result.partId)
        assertEquals(StageId.EXTRACTED, result.firstRun.snapshot.stage)
        assertTrue(Files.readAllBytes(input).contentEquals(Files.readAllBytes(root.resolve("source/intro.mid"))))
        assertFalse(part.importPending)
        assertEquals("midi/raw/intro.mid", part.midi?.raw)
        assertTrue(Files.isRegularFile(root.resolve(requireNotNull(part.midi?.clean))))
        assertEquals(listOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED), runs.map { it.stage })

        val retry = service.importSongPart(ImportSongPart(root, "intro", input, "Intro piano", SectionTypeId("intro"), expectedRevision = 1))
        assertTrue(retry.firstRun.cacheHit)
        assertEquals(3, StageRunStore().read(root, ProjectStore.read(root).envelope.stageRuns).size)
    }

    private fun service(): DefaultProjectApplicationService {
        val preparation = object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = error("not used")
            override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
        }
        val inspection = InputInspectionBoundary { request ->
            InputInspectionResult.Inspected(InputInspectionReport(
                partId = request.partId,
                source = request.source,
                detectedInput = DetectedInput(InputContainer.MIDI, "SMF", "mid"),
                durationSeconds = 1.0
            ))
        }
        val runner = StageRunner(AutomaticImportProcessors(inspection, preparation).registry())
        return DefaultProjectApplicationService(
            midiPreparation = preparation,
            legacyPartAnalysis = LegacyPartAnalysisService { error("not used") },
            inputInspection = inspection,
            stageRunRecovery = runner,
            automaticImportRunner = runner
        )
    }

    private fun writeMidi(path: Path, note: Int) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }
}
