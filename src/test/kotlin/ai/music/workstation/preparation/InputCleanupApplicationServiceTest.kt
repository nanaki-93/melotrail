package ai.music.workstation.preparation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class InputCleanupApplicationServiceTest {
    @Test
    fun `deterministic planning defaults to inspect only and applies documented thresholds`() {
        val report = report(measurements = measurements(dcOffset = 0.006, clippedRuns = 1, clippedFrames = 2, hum = SignalIndicator(EvidenceLevel.HIGH, 0.2), noise = SignalIndicator(EvidenceLevel.MODERATE, 0.16)))

        val inspectOnly = DeterministicInputCleanupPlanner.select(report)
        val safe = DeterministicInputCleanupPlanner.select(report, InputCleanupMode.SAFE_CLEANUP)

        assertEquals(InputCleanupMode.INSPECT_ONLY, inspectOnly.mode)
        assertTrue(inspectOnly.operations.isEmpty())
        assertEquals(listOf(CleanupOperationType.DC_REMOVAL, CleanupOperationType.CLIP_REPAIR, CleanupOperationType.HUM_REMOVAL, CleanupOperationType.NOISE_REDUCTION), safe.operations.map { it.type })
        assertEquals(TranscriptionInputArtifact.CLEAN_WAV, safe.transcriptionInput)
    }

    @Test
    fun `candidate ranking accepts only complete supplied strict json and otherwise falls back`() {
        val candidates = listOf(CleanupOperationType.DC_REMOVAL, CleanupOperationType.HUM_REMOVAL)
        assertEquals(listOf(CleanupOperationType.HUM_REMOVAL, CleanupOperationType.DC_REMOVAL), CleanupCandidateRanking.parseOrNull("""{"version":1,"operationTypes":["HUM_REMOVAL","DC_REMOVAL"]}""", candidates))
        assertEquals(null, CleanupCandidateRanking.parseOrNull("""{"version":1,"operationTypes":["DC_REMOVAL","DECLICK"]}""", candidates))
        assertEquals(null, CleanupCandidateRanking.parseOrNull("""{"version":1,"operationTypes":["DC_REMOVAL"],"path":"/tmp/a"}""", candidates))
    }

    @Test
    fun `safe cleanup requires confirmation and inspect plan updates report without worker`() = runBlocking {
        val root = project(); var calls = 0
        val service = InputCleanupApplicationService(AudioCleanupBoundary { calls++; error("not called") })
        val inspect = DeterministicInputCleanupPlanner.select(InputInspectionReportStore.read(root, "A"))

        val result = service.apply(ApplyInputCleanupRequest(root, "A", inspect))

        assertFalse(result.reused); assertEquals(0, calls)
        assertEquals(inspect, assertNotNull(InputInspectionReportStore.read(root, "A").cleanup).plan)
        val safe = DeterministicInputCleanupPlanner.select(InputInspectionReportStore.read(root, "A"), InputCleanupMode.SAFE_CLEANUP)
        assertFailsWith<IllegalArgumentException> { service.apply(ApplyInputCleanupRequest(root, "A", safe)) }
    }

    @Test
    fun `cleanup publishes validated artifact and report provenance idempotently without source mutation`() = runBlocking {
        val root = project(); var calls = 0
        val service = InputCleanupApplicationService(AudioCleanupBoundary { request ->
            calls++; writePcm24(request.output, 22_050, 1, 4)
            result(22_050, 1, 4)
        })
        val before = Files.readAllBytes(root.resolve("source/A.wav"))
        val plan = DeterministicInputCleanupPlanner.select(InputInspectionReportStore.read(root, "A"), InputCleanupMode.SAFE_CLEANUP)

        val first = service.apply(ApplyInputCleanupRequest(root, "A", plan, confirmedSafeCleanup = true))
        val second = service.apply(ApplyInputCleanupRequest(root, "A", plan, confirmedSafeCleanup = true))

        assertFalse(first.reused); assertTrue(second.reused); assertEquals(1, calls)
        assertEquals(before.toList(), Files.readAllBytes(root.resolve("source/A.wav")).toList())
        val output = assertNotNull(first.report.cleanup).output
        assertNotNull(output); assertEquals("clean.wav", output.relativePath); assertTrue(Files.isRegularFile(root.resolve("prepared/A/clean.wav")))
    }

    @Test
    fun `worker mismatch or failure never replaces an existing clean artifact or report`() = runBlocking {
        val root = project(); val clean = root.resolve("prepared/A/clean.wav")
        Files.createDirectories(clean.parent); Files.writeString(clean, "old-clean")
        val originalReport = Files.readString(root.resolve("prepared/A/report.json"))
        val service = InputCleanupApplicationService(AudioCleanupBoundary { request ->
            writePcm24(request.output, 22_050, 1, 4)
            result(44_100, 1, 4)
        })
        val plan = DeterministicInputCleanupPlanner.select(InputInspectionReportStore.read(root, "A"), InputCleanupMode.SAFE_CLEANUP)

        assertFailsWith<IllegalArgumentException> { service.apply(ApplyInputCleanupRequest(root, "A", plan, true)) }

        assertEquals("old-clean", Files.readString(clean))
        assertEquals(originalReport, Files.readString(root.resolve("prepared/A/report.json")))
    }

    @Test
    fun `stale inspection source cannot be cleaned`() = runBlocking {
        val root = project(); val plan = DeterministicInputCleanupPlanner.select(InputInspectionReportStore.read(root, "A"), InputCleanupMode.SAFE_CLEANUP)
        Files.write(root.resolve("source/A.wav"), Files.readAllBytes(root.resolve("source/A.wav")).also { it[it.lastIndex] = 1 })
        val service = InputCleanupApplicationService(AudioCleanupBoundary { error("must not run") })

        assertFailsWith<IllegalArgumentException> { service.apply(ApplyInputCleanupRequest(root, "A", plan, true)) }
    }

    private fun project(): Path {
        val root = Files.createTempDirectory("cleanup-service")
        val source = root.resolve("source/A.wav"); writePcm24(source, 22_050, 1, 4)
        InputInspectionReportStore.write(root, report(source = InspectionSourceIdentity("source/A.wav", digest(source))))
        return root
    }

    private fun report(
        source: InspectionSourceIdentity = InspectionSourceIdentity("source/A.wav", "a".repeat(64)),
        measurements: AudioInspectionMeasurements = measurements()
    ) = InputInspectionReport(
        partId = "A", source = source, detectedInput = DetectedInput(InputContainer.RIFF_WAVE, "PCM_24", "wav"),
        durationSeconds = 4.0 / 22_050.0, audioFormat = DetectedAudioFormat(22_050, 1, 24), measurements = measurements
    )

    private fun measurements(
        dcOffset: Double = 0.006, clippedRuns: Long = 0, clippedFrames: Long = 0,
        hum: SignalIndicator = SignalIndicator(EvidenceLevel.NONE, 0.0), noise: SignalIndicator = SignalIndicator(EvidenceLevel.NONE, 0.0)
    ) = AudioInspectionMeasurements(0.2, 0.1, dcOffset, clippedRuns, clippedFrames, SilenceEvidence(0, 0), hum, noise)

    private fun result(rate: Int, channels: Int, frames: Long) = AudioCleanupResult(
        rate, channels, frames, CleanupMetrics(0.2, 0.1, 0.006, 0, 0, 0.1, 0.0, 0.0),
        CleanupMetrics(0.2, 0.1, 0.0, 0, 0, 0.1, 0.0, 0.0), listOf(CleanupOperationType.DC_REMOVAL), emptyList(), emptyList(), mapOf("audio-cleanup" to "1.0")
    )

    private fun writePcm24(path: Path, rate: Int, channels: Int, frames: Int) {
        Files.createDirectories(checkNotNull(path.parent)); val dataBytes = frames * channels * 3
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + dataBytes); header.put("WAVEfmt ".toByteArray()); header.putInt(16)
        header.putShort(1); header.putShort(channels.toShort()); header.putInt(rate); header.putInt(rate * channels * 3)
        header.putShort((channels * 3).toShort()); header.putShort(24); header.put("data".toByteArray()); header.putInt(dataBytes)
        Files.write(path, header.array() + ByteArray(dataBytes))
    }

    private fun digest(path: Path): String = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
