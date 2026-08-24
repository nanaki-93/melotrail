package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageRunStore
import app.melotrail.preparation.DetectedInput
import app.melotrail.preparation.DetectedAudioFormat
import app.melotrail.preparation.AudioInspectionMeasurements
import app.melotrail.preparation.EvidenceLevel
import app.melotrail.preparation.SignalIndicator
import app.melotrail.preparation.SilenceEvidence
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionReport
import app.melotrail.preparation.InputInspectionResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    fun `source-first import preserves raw MIDI until the explicit Clean MIDI step`() = runBlocking {
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

        val project = ProjectStore.read(root)
        val part = project.parts.single()
        val runs = StageRunStore().read(root, project.envelope.stageRuns)

        assertEquals("intro", result.partId)
        assertEquals(StageId.EXTRACTED, result.firstRun.snapshot.stage)
        assertTrue(Files.readAllBytes(input).contentEquals(Files.readAllBytes(root.resolve("source/intro.mid"))))
        assertFalse(part.importPending)
        assertEquals("midi/raw/intro.mid", part.midi?.raw)
        assertEquals(null, part.midi?.clean)
        assertEquals(null, part.midi?.normalized)
        assertEquals(listOf(StageId.SOURCE, StageId.EXTRACTED), runs.map { it.stage })

        val retry = service.importSongPart(ImportSongPart(root, "intro", input, "Intro piano", SectionTypeId("intro"), expectedRevision = 1))
        assertTrue(retry.firstRun.cacheHit)
        assertEquals(2, StageRunStore().read(root, ProjectStore.read(root).envelope.stageRuns).size)
    }

    @Test
    fun `audio import always cleans validated raw MIDI before returning the project snapshot`() = runBlocking {
        val input = tempDir.resolve("source.wav").also(::writeWav)
        val service = service()
        val root = tempDir.resolve("audio-project")
        service.create(CreateProjectRequest(root))

        service.importSongPart(ImportSongPart(
            root = root,
            id = "verse",
            file = input,
            name = "Verse piano",
            sectionType = SectionTypeId("verse")
        ))

        val project = ProjectStore.read(root)
        val part = project.parts.single()
        val runs = StageRunStore().read(root, project.envelope.stageRuns)
        assertEquals("midi/raw/verse.mid", part.midi?.raw)
        assertTrue(part.midi?.clean?.startsWith("midi/clean/verse-") == true)
        assertEquals(15, part.midi?.cleanup?.minVelocity)
        assertTrue(Files.isRegularFile(root.resolve(requireNotNull(part.midi?.raw))))
        assertTrue(Files.isRegularFile(root.resolve(requireNotNull(part.midi?.clean))))
        assertEquals(listOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED), runs.map { it.stage })
    }

    @Test
    fun `clean MIDI can be normalized through the project service before source key confirmation`() = runBlocking {
        val input = tempDir.resolve("normalize.wav").also(::writeWav)
        val service = service()
        val root = tempDir.resolve("normalize-project")
        service.create(CreateProjectRequest(root))
        service.importSongPart(ImportSongPart(root, "verse", input, "Verse piano", SectionTypeId("verse")))

        val snapshot = service.normalizePart(NormalizePartRequest(root, "verse"))
        val project = ProjectStore.read(root)
        val part = project.parts.single()
        val runs = StageRunStore().read(root, project.envelope.stageRuns)

        assertTrue(Files.isRegularFile(root.resolve(requireNotNull(part.midi?.normalized))))
        assertTrue(Files.isRegularFile(root.resolve(requireNotNull(part.midi?.normalization))))
        assertTrue(snapshot.parts.single().sourceKey?.detectedKey != null)
        assertEquals(listOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED, StageId.NORMALIZED), runs.map { it.stage })
    }

    private fun service(): DefaultProjectApplicationService {
        val preparation = object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) { writeMidi(output, 60) }
            override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
        }
        val inspection = InputInspectionBoundary { request ->
            val audio = request.source.relativePath.endsWith(".wav")
            InputInspectionResult.Inspected(InputInspectionReport(
                partId = request.partId,
                source = request.source,
                detectedInput = DetectedInput(if (audio) InputContainer.RIFF_WAVE else InputContainer.MIDI, if (audio) "PCM" else "SMF", if (audio) "wav" else "mid"),
                durationSeconds = 1.0,
                audioFormat = if (audio) DetectedAudioFormat(1_000, 1, 24) else null,
                measurements = if (audio) measurements() else null
            ))
        }
        val runner = StageRunner(AutomaticImportProcessors(inspection, preparation).registry())
        return DefaultProjectApplicationService(
            midiPreparation = preparation,
            inputInspection = inspection,
            stageRunRecovery = runner,
            automaticImportRunner = runner
        )
    }

    private fun writeMidi(path: Path, note: Int) {
        Files.createDirectories(checkNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writeWav(path: Path) {
        val dataSize = 1_000 * 3
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(1_000).putInt(3_000).putShort(3).putShort(24)
        bytes.put("data".toByteArray()).putInt(dataSize)
        Files.write(path, bytes.array())
    }

    private fun measurements() = AudioInspectionMeasurements(
        peak = 0.0, rms = 0.0, dcOffset = 0.0, clippedRunCount = 0, clippedFrameCount = 0,
        silence = SilenceEvidence(0, 0), hum = SignalIndicator(EvidenceLevel.NONE, 0.0),
        noise = SignalIndicator(EvidenceLevel.NONE, 0.0)
    )
}
