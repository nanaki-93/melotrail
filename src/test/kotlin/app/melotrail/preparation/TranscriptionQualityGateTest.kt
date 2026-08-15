package app.melotrail.preparation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class TranscriptionQualityGateTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `successful original and explicitly selected prepared audio publish raw MIDI atomically`() = runBlocking {
        val root = project(seconds = 1)
        val service = TranscriptionQualityGateService(writingBoundary())

        val original = assertIs<TranscriptionQualityGateResult.Succeeded>(
            service.run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.SOURCE))
        )
        assertTrue(Files.isRegularFile(root.resolve("midi/raw/A.mid")))
        assertEquals(TranscriptionInputArtifact.SOURCE, original.report.transcription?.selectedInput)
        assertEquals(sha256(root.resolve("source/A.wav")), original.report.transcription?.selectedInputFingerprint)

        val clean = root.resolve("prepared/A/clean.wav")
        writeWav(clean, 1)
        val inspected = InputInspectionReportStore.read(root, "A")
        val plan = safeCleanupPlan(inspected)
        InputInspectionReportStore.write(root, inspected.copy(
            preparation = PreparationStatus.CLEANED,
            cleanup = CleanupPlanRecord(plan, cleanupOutput(clean))
        ))

        val prepared = assertIs<TranscriptionQualityGateResult.Succeeded>(
            service.run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.CLEAN_WAV))
        )
        assertEquals(TranscriptionInputArtifact.CLEAN_WAV, prepared.report.transcription?.selectedInput)
        assertEquals(sha256(clean), prepared.report.transcription?.selectedInputFingerprint)
        assertFalse(Files.exists(root.resolve("midi/diagnostics")))
    }

    @Test
    fun `empty corrupt overdense out of range and duration mismatched MIDI never replace canonical raw output`() = runBlocking {
        val cases = listOf(
            "empty" to TranscriptionBoundary { _, output -> writeMidi(output, emptyList()); completed() },
            "corrupt" to TranscriptionBoundary { _, output -> Files.writeString(output, "not midi"); completed() },
            "overdense" to TranscriptionBoundary { _, output -> writeMidi(output, List(41) { Note(60, it * 10L, it * 10L + 1) }); completed() },
            "range" to TranscriptionBoundary { _, output -> writeMidi(output, listOf(Note(10, 0, 480))); completed() },
            "duration" to TranscriptionBoundary { _, output -> writeMidi(output, listOf(Note(60, 0, 4_800))); completed() }
        )
        cases.forEach { (name, boundary) ->
            val root = project(seconds = 1, name = name)
            val raw = root.resolve("midi/raw/A.mid")
            writeMidi(raw, listOf(Note(60, 0, 480)))
            val before = Files.readAllBytes(raw)

            val result = assertIs<TranscriptionQualityGateResult.Failed>(
                TranscriptionQualityGateService(boundary).run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.SOURCE))
            )

            assertEquals(TranscriptionFailureStage.OUTPUT_VALIDATION, result.stage, name)
            assertTrue(before.contentEquals(Files.readAllBytes(raw)), name)
            assertTrue(Files.readAllBytes(root.resolve("source/A.wav")).isNotEmpty(), name)
        }
    }

    @Test
    fun `parseable failed output is retained only under its diagnostic project path`() = runBlocking {
        val root = project()
        val result = assertIs<TranscriptionQualityGateResult.Failed>(
            TranscriptionQualityGateService(TranscriptionBoundary { _, output -> writeMidi(output, listOf(Note(10, 0, 480))); completed() })
                .run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.SOURCE))
        )

        val diagnostic = assertNotNull(result.report?.transcription?.diagnosticRawMidi)
        assertTrue(diagnostic.startsWith("midi/diagnostics/A-"))
        assertTrue(Files.isRegularFile(root.resolve(diagnostic)))
    }

    @Test
    fun `reports each prerequisite decode cleanup selection model runtime and inference stage`() = runBlocking {
        val noReport = tempDir.resolve("no-report")
        assertEquals(TranscriptionFailureStage.PREREQUISITE, assertIs<TranscriptionQualityGateResult.Failed>(
            TranscriptionQualityGateService(writingBoundary()).run(RunTranscriptionQualityGateRequest(noReport, "A", TranscriptionInputArtifact.SOURCE))
        ).stage)

        val decodedRoot = project(name = "decoded")
        assertEquals(TranscriptionFailureStage.DECODE, assertIs<TranscriptionQualityGateResult.Failed>(
            TranscriptionQualityGateService(writingBoundary()).run(RunTranscriptionQualityGateRequest(decodedRoot, "A", TranscriptionInputArtifact.DECODED_WAV))
        ).stage)

        val cleanRoot = project(name = "clean")
        assertEquals(TranscriptionFailureStage.CLEANUP_SELECTION, assertIs<TranscriptionQualityGateResult.Failed>(
            TranscriptionQualityGateService(writingBoundary()).run(RunTranscriptionQualityGateRequest(cleanRoot, "A", TranscriptionInputArtifact.CLEAN_WAV))
        ).stage)

        listOf(TranscriptionFailureStage.MODEL_RUNTIME, TranscriptionFailureStage.INFERENCE).forEach { stage ->
            val root = project(name = stage.name)
            val result = assertIs<TranscriptionQualityGateResult.Failed>(
                TranscriptionQualityGateService(TranscriptionBoundary { _, _ -> TranscriptionBoundaryResult.Failed(stage) })
                    .run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.SOURCE))
            )
            assertEquals(stage, result.stage)
            assertEquals(stage, InputInspectionReportStore.read(root, "A").transcription?.failureStage)
        }
    }

    @Test
    fun `stale source fingerprint is rejected and source bytes remain untouched`() = runBlocking {
        val root = project()
        val source = root.resolve("source/A.wav")
        val before = Files.readAllBytes(source)
        Files.write(source, before + byteArrayOf(0))

        val result = assertIs<TranscriptionQualityGateResult.Failed>(
            TranscriptionQualityGateService(writingBoundary()).run(RunTranscriptionQualityGateRequest(root, "A", TranscriptionInputArtifact.SOURCE))
        )

        assertEquals(TranscriptionFailureStage.PREREQUISITE, result.stage)
        assertTrue((before + byteArrayOf(0)).contentEquals(Files.readAllBytes(source)))
        assertFalse(Files.exists(root.resolve("midi/raw/A.mid")))
    }

    private fun project(seconds: Int = 1, name: String = "song-${System.nanoTime()}"): Path {
        val root = tempDir.resolve(name)
        val source = root.resolve("source/A.wav")
        writeWav(source, seconds)
        val identity = InspectionSourceIdentity("source/A.wav", sha256(source))
        InputInspectionReportStore.write(root, InputInspectionReport(
            partId = "A", source = identity,
            detectedInput = DetectedInput(InputContainer.RIFF_WAVE, "PCM", "wav"),
            durationSeconds = seconds.toDouble(),
            audioFormat = DetectedAudioFormat(1_000, 1, 24),
            measurements = measurements(), toolVersions = mapOf("input-inspector" to "1")
        ))
        return root
    }

    private fun writingBoundary() = TranscriptionBoundary { _, output ->
        writeMidi(output, listOf(Note(60, 0, 480)))
        completed()
    }

    private fun completed() = TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata("basic-pitch", "1.0"))

    private fun safeCleanupPlan(report: InputInspectionReport) = InputCleanupPlan(
        partId = report.partId, source = report.source, mode = InputCleanupMode.SAFE_CLEANUP,
        operations = listOf(CleanupPlanOperation(CleanupOperationType.DC_REMOVAL)), evidence = checkNotNull(report.measurements),
        confidence = 0.5, transcriptionInput = TranscriptionInputArtifact.CLEAN_WAV
    )

    private fun cleanupOutput(path: Path) = CleanupOutputArtifact(
        sha256 = sha256(path), sampleRate = 1_000, channels = 1, frames = 1_000,
        before = cleanupMetrics(), after = cleanupMetrics(), appliedOperations = listOf(CleanupOperationType.DC_REMOVAL)
    )

    private fun measurements() = AudioInspectionMeasurements(0.0, 0.0, 0.0, 0, 0, SilenceEvidence(0, 0), SignalIndicator(EvidenceLevel.NONE, 0.0), SignalIndicator(EvidenceLevel.NONE, 0.0))
    private fun cleanupMetrics() = CleanupMetrics(0.0, 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0)

    private data class Note(val pitch: Int, val start: Long, val end: Long)

    private fun writeMidi(path: Path, notes: List<Note>) {
        Files.createDirectories(checkNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note.pitch, 100), note.start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note.pitch, 0), note.end))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writeWav(path: Path, seconds: Int) {
        Files.createDirectories(checkNotNull(path.parent))
        val sampleRate = 1_000; val channels = 1; val bits = 24; val frames = sampleRate * seconds; val dataSize = frames * channels * 3
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()); bytes.putInt(36 + dataSize); bytes.put("WAVEfmt ".toByteArray())
        bytes.putInt(16); bytes.putShort(1); bytes.putShort(channels.toShort()); bytes.putInt(sampleRate)
        bytes.putInt(sampleRate * channels * 3); bytes.putShort((channels * 3).toShort()); bytes.putShort(bits.toShort())
        bytes.put("data".toByteArray()); bytes.putInt(dataSize)
        Files.write(path, bytes.array())
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
