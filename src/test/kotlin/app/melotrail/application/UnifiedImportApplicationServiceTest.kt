package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnifiedImportApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `mid and midi imports bypass transcription preserve bytes and record both fingerprints`() = runBlocking {
        val harness = Harness()
        listOf("mid", "midi").forEach { extension ->
            val root = tempDir.resolve("direct-$extension")
            harness.service.create(CreateProjectRequest(root))
            val input = midi("direct-$extension.$extension", 60)
            val bytes = Files.readAllBytes(input)

            val imported = harness.service.importPart(ImportPartRequest(root, "A", input))

            assertEquals(0, harness.transcriptionCalls)
            assertTrue(bytes.contentEquals(Files.readAllBytes(root.resolve("source/A.$extension"))))
            assertTrue(bytes.contentEquals(Files.readAllBytes(root.resolve("midi/raw/A.mid"))))
            val evidence = assertNotNull(ProjectStore.read(root).parts.single().importEvidence)
            assertEquals(hash(input), evidence.sourceSha256)
            assertEquals(hash(input), evidence.rawMidiSha256)
            assertTrue(imported.parts.single().preparation.rawMidi)
            assertFalse(imported.parts.single().preparation.cleanMidi)
        }
    }

    @Test
    fun `wav wave and mp3 imports converge only after gated transcription without changing source`() = runBlocking {
        val harness = Harness()
        listOf("wav", "wave", "mp3").forEach { extension ->
            val root = tempDir.resolve("audio-$extension")
            harness.service.create(CreateProjectRequest(root))
            val input = if (extension == "mp3") mp3("source.$extension") else wav("source.$extension")
            val before = Files.readAllBytes(input)

            val imported = harness.service.importPart(ImportPartRequest(root, "A", input, transcribe = true))

            assertTrue(before.contentEquals(Files.readAllBytes(input)))
            assertTrue(before.contentEquals(Files.readAllBytes(root.resolve("source/A.$extension"))))
            val stored = ProjectStore.read(root).parts.single()
            val evidence = assertNotNull(stored.importEvidence)
            assertEquals(hash(input), evidence.sourceSha256)
            assertEquals(hash(root.resolve("midi/raw/A.mid")), evidence.rawMidiSha256)
            assertTrue(imported.parts.single().preparation.rawMidi)
            assertFalse(imported.parts.single().preparation.cleanMidi)
        }
        assertEquals(3, harness.transcriptionCalls)
    }

    @Test
    fun `unsupported corrupt and extension-content mismatches are rejected before registration`() = runBlocking {
        val cases = listOf(
            "corrupt.mid" to byteArrayOf(1, 2, 3),
            "wave-as-midi.mid" to Files.readAllBytes(wav("mismatch.wav")),
            "midi-as-wave.wav" to Files.readAllBytes(midi("mismatch.mid", 61)),
            "mp3-as-wave.wave" to Files.readAllBytes(mp3("mismatch.mp3")),
            "wave-as-mp3.mp3" to Files.readAllBytes(wav("mismatch-2.wav")),
            "unsupported.flac" to byteArrayOf(0x66, 0x4c, 0x61, 0x43)
        )
        cases.forEachIndexed { index, (name, bytes) ->
            val harness = Harness()
            val root = tempDir.resolve("invalid-$index")
            harness.service.create(CreateProjectRequest(root))
            val input = tempDir.resolve(name).also { Files.write(it, bytes) }

            assertFailsWith<Exception> {
                harness.service.importPart(ImportPartRequest(root, "A", input, transcribe = name.endsWith("wav") || name.endsWith("wave") || name.endsWith("mp3")))
            }

            assertTrue(ProjectStore.read(root).parts.isEmpty(), name)
            assertFalse(Files.exists(root.resolve("source/A.${name.substringAfterLast('.')}")), name)
        }
    }

    @Test
    fun `part traversal case collisions and destination collisions cannot escape or overwrite evidence`() = runBlocking {
        val harness = Harness()
        val root = tempDir.resolve("collisions")
        harness.service.create(CreateProjectRequest(root))
        val first = midi("first.mid", 60)

        assertFailsWith<IllegalArgumentException> {
            harness.service.importPart(ImportPartRequest(root, "../escape", first))
        }
        assertFalse(Files.exists(tempDir.resolve("escape.mid")))

        harness.service.importPart(ImportPartRequest(root, "A", first))
        harness.service.importPart(ImportPartRequest(root, "A", first))
        assertEquals(listOf("A"), ProjectStore.read(root).parts.map { it.id })
        assertFailsWith<IllegalArgumentException> {
            harness.service.importPart(ImportPartRequest(root, "a", first))
        }

        val occupied = root.resolve("source/B.mid")
        writeMidi(occupied, 62)
        val occupiedBefore = Files.readAllBytes(occupied)
        assertFailsWith<IllegalArgumentException> {
            harness.service.importPart(ImportPartRequest(root, "B", midi("second.mid", 63)))
        }
        assertTrue(occupiedBefore.contentEquals(Files.readAllBytes(occupied)))
    }

    @Test
    fun `interrupted worker publication never registers and retry replaces stale raw output idempotently`() = runBlocking {
        val harness = Harness(TranscriptionMode.FAIL_AFTER_OUTPUT)
        val root = tempDir.resolve("worker-retry")
        harness.service.create(CreateProjectRequest(root))
        val input = wav("retry.wav")
        val sourceHash = hash(input)

        assertFailsWith<IllegalStateException> {
            harness.service.importPart(ImportPartRequest(root, "A", input, transcribe = true))
        }
        assertTrue(ProjectStore.read(root).parts.isEmpty())
        assertEquals(sourceHash, hash(root.resolve("source/A.wav")))
        assertFalse(Files.exists(root.resolve("midi/raw/A.mid")))

        writeMidi(root.resolve("midi/raw/A.mid"), 72)
        val staleHash = hash(root.resolve("midi/raw/A.mid"))
        harness.mode = TranscriptionMode.SUCCEED
        harness.service.importPart(ImportPartRequest(root, "A", input, transcribe = true))
        val currentHash = hash(root.resolve("midi/raw/A.mid"))
        assertTrue(currentHash != staleHash)
        assertEquals(currentHash, ProjectStore.read(root).parts.single().importEvidence?.rawMidiSha256)

        harness.service.importPart(ImportPartRequest(root, "A", input, transcribe = true))
        assertEquals(2, harness.transcriptionCalls, "a completed retry must reuse current fingerprinted evidence")
        assertEquals(sourceHash, hash(input))
    }

    @Test
    fun `invalid transcription output and later artifact changes never become current raw MIDI`() = runBlocking {
        val invalidHarness = Harness(TranscriptionMode.INVALID_OUTPUT)
        val invalidRoot = tempDir.resolve("invalid-output")
        invalidHarness.service.create(CreateProjectRequest(invalidRoot))
        val audio = wav("invalid-output.wav")
        assertFailsWith<IllegalStateException> {
            invalidHarness.service.importPart(ImportPartRequest(invalidRoot, "A", audio, transcribe = true))
        }
        assertTrue(ProjectStore.read(invalidRoot).parts.isEmpty())
        assertFalse(Files.exists(invalidRoot.resolve("midi/raw/A.mid")))

        val harness = Harness()
        val root = tempDir.resolve("stale-evidence")
        harness.service.create(CreateProjectRequest(root))
        harness.service.importPart(ImportPartRequest(root, "A", midi("current.mid", 60)))
        writeMidi(root.resolve("midi/raw/A.mid"), 67)

        assertFalse(harness.service.open(root).parts.single().preparation.rawMidi)
        assertFailsWith<IllegalStateException> {
            harness.service.cleanMidi(CleanMidiRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
        }
    }

    private inner class Harness(initialMode: TranscriptionMode = TranscriptionMode.SUCCEED) {
        var mode = initialMode
        var transcriptionCalls = 0
        private val preparation = object : MidiPreparationService {
            override suspend fun transcribe(input: Path, output: Path) = error("unified import must use the quality-gate boundary")
            override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
        }
        val service = DefaultProjectApplicationService(
            midiPreparation = preparation,
            legacyPartAnalysis = LegacyPartAnalysisService { error("legacy analysis is not used") },
            inputInspection = InputInspectionBoundary { request ->
                val extension = request.source.relativePath.substringAfterLast('.')
                val container = if (extension == "mp3") InputContainer.MPEG_AUDIO else InputContainer.RIFF_WAVE
                InputInspectionResult.Inspected(InputInspectionReport(
                    partId = request.partId,
                    source = request.source,
                    detectedInput = DetectedInput(container, if (container == InputContainer.MPEG_AUDIO) "MPEG" else "PCM", extension),
                    durationSeconds = 0.5,
                    audioFormat = DetectedAudioFormat(1_000, 1, 24),
                    measurements = measurements()
                ))
            },
            transcriptionQualityGate = TranscriptionQualityGateService(TranscriptionBoundary { _, output ->
                transcriptionCalls++
                when (mode) {
                    TranscriptionMode.SUCCEED -> {
                        writeMidi(output, 60)
                        TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata("fake-basic-pitch", "1"))
                    }
                    TranscriptionMode.FAIL_AFTER_OUTPUT -> {
                        writeMidi(output, 65)
                        TranscriptionBoundaryResult.Failed(TranscriptionFailureStage.INFERENCE)
                    }
                    TranscriptionMode.INVALID_OUTPUT -> {
                        writeMidi(output, 10)
                        TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata("fake-basic-pitch", "1"))
                    }
                }
            })
        )
    }

    private enum class TranscriptionMode { SUCCEED, FAIL_AFTER_OUTPUT, INVALID_OUTPUT }

    private fun measurements() = AudioInspectionMeasurements(
        0.1, 0.05, 0.0, 0, 0,
        SilenceEvidence(0, 0), SignalIndicator(EvidenceLevel.NONE, 0.0), SignalIndicator(EvidenceLevel.NONE, 0.0)
    )

    private fun midi(name: String, note: Int): Path = tempDir.resolve(name).also { writeMidi(it, note) }

    private fun writeMidi(path: Path, note: Int) {
        Files.createDirectories(checkNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun wav(name: String): Path = tempDir.resolve(name).also { path ->
        val sampleRate = 1_000
        val frames = 500
        val dataSize = frames * 3
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 3).putShort(3).putShort(24)
        bytes.put("data".toByteArray()).putInt(dataSize)
        Files.write(path, bytes.array())
    }

    private fun mp3(name: String): Path = tempDir.resolve(name).also { path ->
        Files.write(path, byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0,
            0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64
        ))
    }

    private fun hash(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
