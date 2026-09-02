package app.melotrail.midi.domain

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiImportValidatorTest {
    @TempDir lateinit var root: Path
    private val reader = JdkMidiReader()
    private val validator = MidiImportValidator()

    @Test
    fun `classifies accepted awaiting-authority and rejected imports with stable findings`() {
        val paths = OwnedMidiFixtures.writeAll(root).associateBy { it.fileName.toString() }
        val valid = validator.validate(reader.inspect(paths.getValue("smf0-melody.mid")), MidiValidationContext(MidiMelodySelection(0, 0)))
        val missingAuthority = validator.validate(reader.inspect(writeSequence("missing-authority.mid") { track ->
            track.add(MidiEvent(noteOn(0, 60, 96), 0)); track.add(MidiEvent(noteOff(0, 60), 480))
        }), MidiValidationContext(MidiMelodySelection(0, 0)))
        val changingMaps = validator.validate(reader.inspect(writeSequence("changing-maps.mid") { track ->
            track.add(MidiEvent(tempo(500_000), 0)); track.add(MidiEvent(meter(4, 2), 0)); track.add(MidiEvent(tempo(400_000), 240)); track.add(MidiEvent(meter(3, 2), 240))
            track.add(MidiEvent(noteOn(0, 60, 96), 0)); track.add(MidiEvent(noteOff(0, 60), 480))
        }), MidiValidationContext(MidiMelodySelection(0, 0)))

        assertEquals(MidiImportDisposition.ACCEPTED, valid.disposition)
        assertEquals(MidiImportDisposition.AWAITING_AUTHORITY, missingAuthority.disposition)
        assertEquals(setOf(MidiFindingCode.MISSING_TEMPO, MidiFindingCode.MISSING_TIME_SIGNATURE), missingAuthority.findings.map(MidiFinding::code).toSet())
        assertEquals(MidiImportDisposition.REJECTED, changingMaps.disposition)
        assertEquals(setOf(MidiFindingCode.TEMPO_MAP_UNSUPPORTED, MidiFindingCode.TIME_SIGNATURE_MAP_UNSUPPORTED), changingMaps.findings.map(MidiFinding::code).toSet())
        assertEquals(changingMaps.findings.sortedWith(MidiImportValidationResult.MIDI_FINDING_ORDER), changingMaps.findings)
    }

    @Test
    fun `keeps polyphony and chromatic melody as advisories`() {
        val result = validator.validate(reader.inspect(writeSequence("polyphonic-chromatic.mid") { track ->
            track.add(MidiEvent(tempo(500_000), 0)); track.add(MidiEvent(meter(4, 2), 0))
            track.add(MidiEvent(noteOn(0, 60, 96), 0)); track.add(MidiEvent(noteOn(0, 61, 96), 120))
            track.add(MidiEvent(noteOff(0, 60), 360)); track.add(MidiEvent(noteOff(0, 61), 480))
        }), MidiValidationContext(MidiMelodySelection(0, 0), setOf(0, 2, 4, 5, 7, 9, 11)))

        assertEquals(MidiImportDisposition.ACCEPTED, result.disposition)
        assertEquals(listOf(MidiFindingCode.POLYPHONY, MidiFindingCode.CHROMATIC_MELODY), result.findings.map(MidiFinding::code))
        assertTrue(result.findings.all { it.severity == MidiFindingSeverity.ADVISORY })
    }

    @Test
    fun `blocks malformed timing and unsafe note pairing on every source track`() {
        val malformed = inspection(
            tracks = listOf(melodyTrack(), SemanticMidiTrack(1, emptyList())),
            summaries = listOf(MidiTrackSummary(0, "Melody", listOf(MidiChannelSummary(0, 1, 60, 60, 0, emptyList()))), MidiTrackSummary(1, "Reference", emptyList())),
            issues = listOf(
                MidiReaderIssue(MidiReaderIssueCode.NON_POSITIVE_NOTE_DURATION, 0, 2, 24, "Note duration must be positive"),
                MidiReaderIssue(MidiReaderIssueCode.UNCLOSED_NOTE_ON, 1, 4, 48, "Reference note is unclosed"),
            ),
        )

        val result = validator.validate(malformed, MidiValidationContext(MidiMelodySelection(0, 0)))

        assertEquals(MidiImportDisposition.REJECTED, result.disposition)
        assertEquals(MidiFindingSeverity.BLOCKING, result.findings.single { it.code == MidiFindingCode.INVALID_NOTE_TIMING }.severity)
        assertEquals(MidiFindingSeverity.BLOCKING, result.findings.single { it.code == MidiFindingCode.UNSAFE_SELECTED_MELODY_PAIRING }.severity)
    }

    @Test
    fun `infers the sole melody channel and blocks its unclosed notes`() {
        val input = inspection(
            tracks = listOf(melodyTrack(), SemanticMidiTrack(1, emptyList())),
            summaries = listOf(MidiTrackSummary(0, "Melody", listOf(MidiChannelSummary(0, 1, 60, 60, 0, emptyList()))), MidiTrackSummary(1, "Broken", emptyList())),
            issues = listOf(MidiReaderIssue(MidiReaderIssueCode.UNCLOSED_NOTE_ON, 0, 1, 12, "Melody note is unclosed")),
        )

        val rejected = validator.validate(input)

        assertEquals(MidiImportDisposition.REJECTED, rejected.disposition)
        assertEquals(MidiFindingSeverity.BLOCKING, rejected.findings.single { it.code == MidiFindingCode.UNSAFE_SELECTED_MELODY_PAIRING }.severity)
    }

    private fun inspection(tracks: List<SemanticMidiTrack>, summaries: List<MidiTrackSummary>, issues: List<MidiReaderIssue>): MidiInspectionResult =
        MidiInspectionResult(SemanticMidiSequence(source(), tracks), summaries, issues, 480)

    private fun source() = MidiSourceIdentity("a".repeat(64), "source.mid", 1, MidiPpq(480))

    private fun melodyTrack() = SemanticMidiTrack(
        0,
        listOf(MidiNoteEvent(MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE, sourceEvent = MidiSourceEventIdentity(0, 2)), 480, 0, 60, 96)),
    )

    private fun writeSequence(name: String, events: (javax.sound.midi.Track) -> Unit): Path {
        val sequence = Sequence(Sequence.PPQ, 480)
        events(sequence.createTrack())
        return root.resolve(name).also { require(MidiSystem.write(sequence, 1, it.toFile()) > 0) }
    }

    private fun noteOn(channel: Int, pitch: Int, velocity: Int) = ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity)
    private fun noteOff(channel: Int, pitch: Int) = ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0)
    private fun tempo(micros: Int) = MetaMessage(0x51, byteArrayOf((micros ushr 16).toByte(), (micros ushr 8).toByte(), micros.toByte()), 3)
    private fun meter(numerator: Int, denominatorExponent: Int) = MetaMessage(0x58, byteArrayOf(numerator.toByte(), denominatorExponent.toByte(), 24, 8), 4)
}
