package app.melotrail.preparation

import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.sha256
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** QP-003 regression fixtures for local timing maps, global occurrence bounds, and reviewed windows. */
class MidiTimeMappingTest {
    @TempDir lateinit var root: Path

    @Test
    fun `fractional source beats map to zero phase accumulation without changing source midi`() {
        val input = root.resolve("source.mid").also(::writeBodyMidi)
        val sourceBytes = Files.readAllBytes(input)
        val evidence = evidence()
        val decision = decision(input, evidence, occurrenceId = "verse-1", targetStartBar = 0)
        val first = root.resolve("mapped-a.mid")
        val second = root.resolve("mapped-b.mid")

        val firstReport = MidiTimeMapper().map(input, first, decision, evidence)
        val secondReport = MidiTimeMapper().map(input, second, decision, evidence)
        val sequence = MidiSystem.getSequence(first.toFile())

        assertContentEquals(sourceBytes, Files.readAllBytes(input))
        assertContentEquals(Files.readAllBytes(first), Files.readAllBytes(second))
        assertEquals(firstReport, secondReport)
        assertEquals(listOf(0L, 480L, 960L, 1_440L, 1_920L), firstReport.targetBeats.map { it.localTick })
        assertEquals(listOf(0L, 503L, 1_011L, 1_512L, 2_025L), firstReport.sourceBeats.map { it.sourceMidiTick })
        assertEquals(0.9, firstReport.mappingConfidence)
        assertEquals(0L, firstReport.residuals.maximumAnchorResidualTicks)
        assertEquals(0L, firstReport.residuals.accumulatedAnchorPhaseTicks)
        assertEquals(480, sequence.resolution)
        assertTrue(meta(sequence).any { it.type == 0x51 })
        assertTrue(meta(sequence).any { it.type == 0x58 })
        assertEquals(notes(MidiSystem.getSequence(input.toFile())).map { it.channel to it.pitch }.sortedBy { it.toString() },
            notes(sequence).map { it.channel to it.pitch }.sortedBy { it.toString() })
        assertTrue(controllers(sequence).isNotEmpty())
    }

    @Test
    fun `repeated source use keeps local mapping identical while occurrence bounds stay distinct`() {
        val input = root.resolve("source.mid").also(::writeBodyMidi)
        val evidence = evidence()
        val first = MidiTimeMapper().map(input, root.resolve("first.mid"), decision(input, evidence, "verse-1", 0), evidence)
        val second = MidiTimeMapper().map(input, root.resolve("second.mid"), decision(input, evidence, "chorus-1", 4), evidence)

        assertContentEquals(Files.readAllBytes(root.resolve("first.mid")), Files.readAllBytes(root.resolve("second.mid")))
        assertEquals(first.targetBeats.map { it.localTick }, second.targetBeats.map { it.localTick })
        assertEquals(0L, first.windows.single { it.kind == TimingWindowKind.BODY }.songStartTick)
        assertEquals(7_680L, second.windows.single { it.kind == TimingWindowKind.BODY }.songStartTick)
        assertEquals(9_600L, second.windows.single { it.kind == TimingWindowKind.BODY }.songEndTick)
    }

    @Test
    fun `pickup and tail stay typed outside whole body bars and pending review cannot publish`() {
        val input = root.resolve("pickup.mid").also(::writePickupMidi)
        val evidence = evidence(sourceTicks = listOf(100L, 603L, 1_111L, 1_612L, 2_125L))
        val pending = decision(input, evidence, "intro-1", 1, sourceTicks = listOf(100L, 603L, 1_111L, 1_612L, 2_125L),
            pickup = ExplicitTimingWindow(0, 100, 120), tail = ExplicitTimingWindow(2_125, 2_225, 120), review = MidiTimeMappingReview())

        assertFailsWith<IllegalArgumentException> { MidiTimeMapper().map(input, root.resolve("pending.mid"), pending, evidence) }

        val approved = pending.copy(review = approvedReview())
        val report = MidiTimeMapper().map(input, root.resolve("approved.mid"), approved, evidence)
        val pickup = report.windows.single { it.kind == TimingWindowKind.PICKUP }
        val body = report.windows.single { it.kind == TimingWindowKind.BODY }
        val tail = report.windows.single { it.kind == TimingWindowKind.TAIL }

        assertEquals(0L, pickup.localStartTick)
        assertEquals(1_920L, body.songStartTick)
        assertEquals(3_840L, body.songEndTick)
        assertEquals(3_840L, tail.songStartTick)
        assertEquals(3_960L, tail.songEndTick)
    }

    @Test
    fun `approved explicit grid fallback maps sparse timing evidence without indexing absent anchors`() {
        val input = root.resolve("sparse.mid").also(::writeBodyMidi)
        val evidence = evidence(sourceTicks = listOf(0L, 503L, 1_011L))
        val decision = decision(
            input = input,
            evidence = evidence,
            occurrenceId = "intro-1",
            targetStartBar = 0,
            review = approvedReview()
        )

        val report = MidiTimeMapper().map(input, root.resolve("sparse-mapped.mid"), decision, evidence)

        assertEquals(0.0, report.mappingConfidence)
        assertTrue(report.reviewReasons.contains(MidiTimeMappingReviewReason.DOWNBEAT_REVIEW_REQUIRED))
        assertTrue(!report.acceptedSourceGroove)
    }

    private fun decision(
        input: Path,
        evidence: SourceTimingEvidence,
        occurrenceId: String,
        targetStartBar: Long,
        sourceTicks: List<Long> = listOf(0L, 503L, 1_011L, 1_512L, 2_025L),
        pickup: ExplicitTimingWindow? = null,
        tail: ExplicitTimingWindow? = null,
        review: MidiTimeMappingReview = approvedReview()
    ) = SourceTimingDecision(
        partId = "A",
        occurrenceId = occurrenceId,
        sourceTimingReport = ArtifactRef("analysis/timing/A/${"b".repeat(64)}.json", "b".repeat(64)),
        sourceMidi = ArtifactRef("midi/normalized/A.mid", sha256(input)),
        sourcePpq = 480,
        targetPpq = 480,
        targetTempoBpm = 120,
        targetMeterNumerator = 4,
        targetMeterDenominator = 4,
        sourceDownbeatBeatIndex = 0,
        sourceBeats = sourceTicks.mapIndexed(::SourceBeatTickAnchor),
        targetStartBar = targetStartBar,
        targetBarCount = 1,
        pickup = pickup,
        tail = tail,
        review = review
    )

    private fun evidence(sourceTicks: List<Long> = listOf(0L, 503L, 1_011L, 1_512L, 2_025L)): SourceTimingEvidence {
        val beats = sourceTicks.indices.map { index -> SourceTimingPoint(index * 10, index * 0.5, confidence = 0.9) }
        return SourceTimingEvidence(
            partId = "A",
            source = InspectionSourceIdentity("source/A.wav", "a".repeat(64)),
            workerContractVersion = 2,
            beats = beats,
            onsets = emptyList(),
            tempoCandidates = listOf(TempoCandidate(120.0, 0.9, 4)),
            downbeat = DownbeatEvidence(DownbeatEvidenceStatus.REVIEW_REQUIRED, DownbeatEvidenceReason.AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE,
                candidateBeatIndex = 0, frame = 0, timeSeconds = 0.0, confidence = 0.4),
            groove = SourceGrooveTemplateDeriver.derive("a".repeat(64), beats, emptyList())
        ).also(SourceTimingEvidence::requireValid)
    }

    private fun approvedReview() = MidiTimeMappingReview(MidiTimeMappingReviewState.APPROVED, "test-user", "2026-08-24T00:00:00Z")

    private fun writeBodyMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(tempo(500_000), 0))
            add(MidiEvent(meter(4, 4), 0))
            add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 1, 0), 0))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 251))
            add(MidiEvent(ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 64, 127), 755))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_011))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 1_512))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 2_025))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writePickupMidi(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(tempo(500_000), 0))
            add(MidiEvent(meter(4, 4), 0))
            add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 1, 0), 0))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 50))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 99))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 100))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 603))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 67, 100), 2_125))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 67, 0), 2_200))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun notes(sequence: Sequence): List<Note> = sequence.tracks.flatMap { track ->
        (0 until track.size()).mapNotNull { index ->
            val event = track[index]
            val message = event.message as? ShortMessage
            if (message?.command == ShortMessage.NOTE_ON && message.data2 > 0) Note(event.tick, message.channel, message.data1) else null
        }
    }

    private fun controllers(sequence: Sequence): List<MidiEvent> = sequence.tracks.flatMap { track ->
        (0 until track.size()).map(track::get).filter { (it.message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE }
    }

    private fun meta(sequence: Sequence): List<MetaMessage> = sequence.tracks.flatMap { track ->
        (0 until track.size()).map(track::get).mapNotNull { it.message as? MetaMessage }
    }

    private data class Note(val tick: Long, val channel: Int, val pitch: Int)

    private fun tempo(value: Int) = MetaMessage().also { it.setMessage(0x51, byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()), 3) }
    private fun meter(numerator: Int, denominator: Int) = MetaMessage().also { it.setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }
}
