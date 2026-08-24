package app.melotrail.application

import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.ImportEvidence
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.sha256
import app.melotrail.preparation.DownbeatEvidence
import app.melotrail.preparation.DownbeatEvidenceReason
import app.melotrail.preparation.DownbeatEvidenceStatus
import app.melotrail.preparation.MidiTimeMappingReview
import app.melotrail.preparation.MidiTimeMappingReviewState
import app.melotrail.preparation.SourceBeatTickAnchor
import app.melotrail.preparation.SourceGrooveTemplateDeriver
import app.melotrail.preparation.SourceTimingDecision
import app.melotrail.preparation.SourceTimingEvidence
import app.melotrail.preparation.SourceTimingEvidenceReference
import app.melotrail.preparation.SourceTimingEvidenceStore
import app.melotrail.preparation.SourceTimingPoint
import app.melotrail.preparation.TempoCandidate
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Integration coverage for QP-003 immutable candidate publication and project persistence. */
class SourceTimingAlignmentApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `alignment publishes a separate hash bound candidate without replacing normalized midi`() {
        val source = root.resolve("source/A.wav").also { Files.createDirectories(requireNotNull(it.parent)); Files.write(it, byteArrayOf(1, 2, 3)) }
        val normalized = root.resolve("midi/normalized/A.mid").also(::writeMidi)
        val normalizedBytes = Files.readAllBytes(normalized)
        val evidence = evidence(source)
        val timingReport = SourceTimingEvidenceStore.write(root, evidence)
        val timingReference = SourceTimingEvidenceReference(timingReport, sha256(source))
        writePlaceholder("midi/raw/A.mid"); writePlaceholder("midi/clean/A.mid"); writePlaceholder("midi/quality/A.json"); writePlaceholder("midi/normalization/A.json")
        ProjectStore.write(root, Project(
            name = "alignment-fixture",
            renderFormat = RenderFormat(),
            parts = listOf(SongPart(
                id = "A",
                file = "source/A.wav",
                midi = MidiReferences(
                    raw = "midi/raw/A.mid",
                    clean = "midi/clean/A.mid",
                    cleanup = MidiCleanupOptions(),
                    quality = "midi/quality/A.json",
                    normalized = "midi/normalized/A.mid",
                    normalization = "midi/normalization/A.json"
                ),
                importEvidence = ImportEvidence(sha256(source), sha256(root.resolve("midi/raw/A.mid"))),
                sourceTimingEvidence = timingReference
            ))
        ))
        val sourceMidi = ArtifactRef("midi/normalized/A.mid", sha256(normalized))
        val decision = SourceTimingDecision(
            partId = "A",
            occurrenceId = "verse-1",
            sourceTimingReport = timingReport,
            sourceMidi = sourceMidi,
            sourcePpq = 480,
            targetPpq = 480,
            targetTempoBpm = 120,
            targetMeterNumerator = 4,
            targetMeterDenominator = 4,
            sourceDownbeatBeatIndex = 0,
            sourceBeats = listOf(0L, 503L, 1_011L, 1_512L, 2_025L).mapIndexed(::SourceBeatTickAnchor),
            targetStartBar = 0,
            targetBarCount = 1,
            review = MidiTimeMappingReview(MidiTimeMappingReviewState.APPROVED, "test-user", "2026-08-24T00:00:00Z")
        )

        val result = SourceTimingAlignmentApplicationService().align(AlignSourceTimingRequest(root, "A", decision))
        val persisted = ProjectStore.read(root).parts.single().timingMappingEvidence

        assertEquals(result.reference, persisted)
        assertNotNull(persisted)
        assertTrue(persisted.candidate.path.startsWith("midi/timing/A/"))
        assertTrue(persisted.report.path.startsWith("analysis/timing-mapping/A/"))
        assertContentEquals(normalizedBytes, Files.readAllBytes(normalized))
        assertTrue(Files.exists(root.resolve(persisted.candidate.path)))
    }

    private fun evidence(source: Path): SourceTimingEvidence {
        val beats = (0..4).map { index -> SourceTimingPoint(index * 10, index * 0.5, confidence = 0.9) }
        return SourceTimingEvidence(
            partId = "A",
            source = app.melotrail.preparation.InspectionSourceIdentity("source/A.wav", sha256(source)),
            workerContractVersion = 2,
            beats = beats,
            onsets = emptyList(),
            tempoCandidates = listOf(TempoCandidate(120.0, 0.9, 4)),
            downbeat = DownbeatEvidence(DownbeatEvidenceStatus.REVIEW_REQUIRED, DownbeatEvidenceReason.AUDIO_ONLY_PHASE_IS_NOT_AUTHORITATIVE,
                candidateBeatIndex = 0, frame = 0, timeSeconds = 0.0, confidence = 0.4),
            groove = SourceGrooveTemplateDeriver.derive(sha256(source), beats, emptyList())
        )
    }

    private fun writePlaceholder(relative: String) {
        val path = root.resolve(relative)
        Files.createDirectories(requireNotNull(path.parent))
        Files.writeString(path, "evidence")
    }

    private fun writeMidi(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(tempo(500_000), 0))
            add(MidiEvent(meter(4, 4), 0))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 251))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_011))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 64, 100), 1_512))
            add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 64, 0), 2_025))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun tempo(value: Int) = MetaMessage().also { it.setMessage(0x51, byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()), 3) }
    private fun meter(numerator: Int, denominator: Int) = MetaMessage().also { it.setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }
}
