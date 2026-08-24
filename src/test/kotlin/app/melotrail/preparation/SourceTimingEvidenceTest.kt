package app.melotrail.preparation

import app.melotrail.arrangement.ImportEvidence
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.sha256
import app.melotrail.application.MeasureSourceTimingRequest
import app.melotrail.application.SourceTimingEvidenceApplicationService
import app.melotrail.worker.AnalyzeCommand
import app.melotrail.worker.WorkerCommand
import app.melotrail.worker.WorkerGateway
import app.melotrail.worker.WorkerResponse
import app.melotrail.worker.WorkerStatus
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for QP-002 timing evidence, path confinement, and immutable persistence. */
class SourceTimingEvidenceTest {
    @TempDir lateinit var root: Path
    @TempDir lateinit var externalRoot: Path

    @Test
    fun `groove template excludes pickup tempo drift and unsupported bins without inventing feel`() {
        val beats = listOf(
            beat(10, 0.50), beat(50, 1.00), beat(90, 1.50), beat(130, 2.00), beat(250, 3.50)
        )
        val onsets = listOf(
            onset(1, 0.10), // pickup before the first accepted body beat
            onset(12, 0.52), onset(32, 0.80), onset(49, 0.98), // 0.98 is a rejected late-bar outlier
            onset(52, 1.02), onset(72, 1.27), onset(92, 1.52)
        )

        val groove = SourceGrooveTemplateDeriver.derive("a".repeat(64), beats, onsets)

        assertEquals(1, groove.excludedPickupOnsets)
        assertEquals(1, groove.excludedTempoDriftIntervals)
        assertEquals(0, groove.excludedMissingOnsetIntervals)
        assertEquals(1, groove.excludedOutlierOnsets)
        assertTrue(groove.bins.any { it.status == SourceGrooveBinStatus.MEASURED })
        assertTrue(groove.bins.any { it.status == SourceGrooveBinStatus.NEUTRAL_UNKNOWN && it.deviationFractionOfBeat == 0.0 })
        assertEquals(0.04, groove.bins.single { it.subdivision == 0 }.deviationFractionOfBeat, 0.000001)
        assertEquals(SourceGrooveTemplateStatus.REVIEW_REQUIRED, groove.status)
        assertTrue(groove.confidence < SourceGrooveTemplate.REVIEW_CONFIDENCE)
        groove.requireValid("a".repeat(64))
    }

    @Test
    fun `worker boundary maps only valid v2 timing evidence and confines its input`() = kotlinx.coroutines.runBlocking {
        val source = createProjectSource("source/A.wav")
        val identity = InspectionSourceIdentity("source/A.wav", sha256(source))
        val gateway = TimingGateway(validOutput())
        val result = WorkerSourceTimingBoundary(gateway).measure(SourceTimingMeasurementRequest(root, "A", identity))

        val measured = result as SourceTimingMeasurementResult.Measured
        assertEquals(2, measured.observation.workerContractVersion)
        assertEquals(listOf(10, 50, 90, 130), measured.observation.beats.map(SourceTimingPoint::frame))
        assertEquals(2, (gateway.command as AnalyzeCommand).version)
        assertTrue((gateway.command as AnalyzeCommand).path.startsWith(root.toString()))

        val external = Files.createTempFile("melotrail-timing-external", ".wav")
        Files.write(external, byteArrayOf(7, 8, 9))
        val escaped = root.resolve("source/escaped.wav")
        Files.createSymbolicLink(escaped, external)
        val escapedResult = WorkerSourceTimingBoundary(gateway).measure(
            SourceTimingMeasurementRequest(root, "A", InspectionSourceIdentity("source/escaped.wav", sha256(external)))
        )
        assertEquals(SourceTimingErrorCode.INVALID_REQUEST, (escapedResult as SourceTimingMeasurementResult.Rejected).error.code)
        Files.deleteIfExists(external)
    }

    @Test
    fun `empty timing payload remains explicit unknown evidence`() = kotlinx.coroutines.runBlocking {
        val source = createProjectSource("source/A.wav")
        val identity = InspectionSourceIdentity("source/A.wav", sha256(source))
        val result = WorkerSourceTimingBoundary(TimingGateway(emptyOutput())).measure(
            SourceTimingMeasurementRequest(root, "A", identity)
        )

        val report = (result as SourceTimingMeasurementResult.Measured).observation.toEvidence("A", identity)
        assertEquals(DownbeatEvidenceStatus.UNKNOWN, report.downbeat.status)
        assertEquals(DownbeatEvidenceReason.INSUFFICIENT_BEAT_EVIDENCE, report.downbeat.reason)
        assertTrue(report.groove.bins.all { it.status == SourceGrooveBinStatus.NEUTRAL_UNKNOWN })
    }

    @Test
    fun `timing report store rejects a symlinked project path`() = kotlinx.coroutines.runBlocking {
        val source = createProjectSource("source/A.wav")
        val identity = InspectionSourceIdentity("source/A.wav", sha256(source))
        val observation = (WorkerSourceTimingBoundary(TimingGateway(emptyOutput())).measure(
            SourceTimingMeasurementRequest(root, "A", identity)
        ) as SourceTimingMeasurementResult.Measured).observation
        Files.createDirectories(root.resolve("analysis/timing"))
        Files.createSymbolicLink(root.resolve("analysis/timing/A"), externalRoot)

        assertFailsWith<IllegalArgumentException> { SourceTimingEvidenceStore.write(root, observation.toEvidence("A", identity)) }
        assertTrue(Files.list(externalRoot).use { it.noneMatch { _ -> true } })
    }

    @Test
    fun `application service persists source bound timing evidence without changing source bytes`() = kotlinx.coroutines.runBlocking {
        val source = createProjectSource("source/A.wav")
        val sourceBefore = Files.readAllBytes(source)
        writeMidi(root.resolve("midi/raw/A.mid"))
        ProjectStore.write(root, Project(
            name = "timing-fixture",
            renderFormat = RenderFormat(),
            parts = listOf(SongPart(
                id = "A",
                file = "source/A.wav",
                midi = MidiReferences(raw = "midi/raw/A.mid"),
                importEvidence = ImportEvidence(sha256(source), sha256(root.resolve("midi/raw/A.mid")))
            ))
        ))
        val observation = (WorkerSourceTimingBoundary(TimingGateway(validOutput())).measure(
            SourceTimingMeasurementRequest(root, "A", InspectionSourceIdentity("source/A.wav", sha256(source)))
        ) as SourceTimingMeasurementResult.Measured).observation
        val service = SourceTimingEvidenceApplicationService(SourceTimingBoundary { SourceTimingMeasurementResult.Measured(observation) })

        val result = service.measure(MeasureSourceTimingRequest(root, "A"))
        val persisted = ProjectStore.read(root).parts.single().sourceTimingEvidence

        assertTrue(result.reviewRequired)
        assertEquals(result.reference, persisted)
        assertEquals(result.report, SourceTimingEvidenceStore.read(root, requireNotNull(persisted).report))
        assertTrue(Files.readAllBytes(source).contentEquals(sourceBefore))
        assertTrue(persisted.report.path.startsWith("analysis/timing/A/"))
    }

    @Test
    fun `invalid worker payload and source mutation cannot become timing evidence`() = kotlinx.coroutines.runBlocking {
        val source = createProjectSource("source/A.wav")
        val identity = InspectionSourceIdentity("source/A.wav", sha256(source))
        val malformed = WorkerSourceTimingBoundary(TimingGateway(validOutput().replace("\"confidence\":0.8", "\"confidence\":2.0"))).measure(
            SourceTimingMeasurementRequest(root, "A", identity)
        )
        assertEquals(SourceTimingErrorCode.INVALID_MEASUREMENT, (malformed as SourceTimingMeasurementResult.Rejected).error.code)

        writeMidi(root.resolve("midi/raw/A.mid"))
        ProjectStore.write(root, Project(
            name = "timing-mutation-fixture",
            renderFormat = RenderFormat(),
            parts = listOf(SongPart(
                id = "A",
                file = "source/A.wav",
                midi = MidiReferences(raw = "midi/raw/A.mid"),
                importEvidence = ImportEvidence(sha256(source), sha256(root.resolve("midi/raw/A.mid")))
            ))
        ))
        val observation = (WorkerSourceTimingBoundary(TimingGateway(validOutput())).measure(
            SourceTimingMeasurementRequest(root, "A", identity)
        ) as SourceTimingMeasurementResult.Measured).observation
        val changing = SourceTimingEvidenceApplicationService(SourceTimingBoundary {
            Files.write(source, byteArrayOf(9))
            SourceTimingMeasurementResult.Measured(observation)
        })
        assertFailsWith<IllegalArgumentException> { changing.measure(MeasureSourceTimingRequest(root, "A")) }
        assertFalse(Files.exists(root.resolve("analysis/timing")))
    }

    private fun beat(frame: Int, time: Double) = SourceTimingPoint(frame, time, confidence = 0.8)
    private fun onset(frame: Int, time: Double) = SourceTimingPoint(frame, time, strength = 1.0)

    private fun createProjectSource(relative: String): Path = root.resolve(relative).also { path ->
        Files.createDirectories(requireNotNull(path.parent))
        Files.write(path, byteArrayOf(1, 2, 3, 4))
    }

    private fun writeMidi(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun validOutput() = """{
        "analysisVersion":2,
        "beats":[
          {"frame":10,"timeSeconds":0.10,"confidence":0.8},
          {"frame":50,"timeSeconds":0.50,"confidence":0.8},
          {"frame":90,"timeSeconds":0.90,"confidence":0.8},
          {"frame":130,"timeSeconds":1.30,"confidence":0.8}
        ],
        "onsets":[
          {"frame":11,"timeSeconds":0.11,"strength":1.0},
          {"frame":51,"timeSeconds":0.51,"strength":1.0},
          {"frame":91,"timeSeconds":0.91,"strength":1.0}
        ],
        "tempoCandidates":[{"bpm":150.0,"confidence":0.5,"supportingIntervals":3}],
        "leadingActivity":{"frame":4410,"timeSeconds":0.10,"confidence":1.0},
        "downbeat":{"status":"REVIEW_REQUIRED","reason":"AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE","candidateBeatIndex":0,"frame":10,"timeSeconds":0.10,"confidence":0.4}
    }"""

    private fun emptyOutput() = """{
        "analysisVersion":2,
        "beats":[],
        "onsets":[],
        "tempoCandidates":[],
        "downbeat":{"status":"UNKNOWN","reason":"INSUFFICIENT_BEAT_EVIDENCE"}
    }"""

    private class TimingGateway(private val payload: String) : WorkerGateway {
        var command: WorkerCommand? = null

        override suspend fun execute(command: WorkerCommand): WorkerResponse {
            this.command = command
            return WorkerResponse(jobId = "test", status = WorkerStatus.COMPLETED, output = Json.parseToJsonElement(payload).jsonObject)
        }

        override suspend fun healthCheck(): Boolean = true
        override suspend fun supportsTimingAnalysis(version: Int): Boolean = version == 2
    }
}
