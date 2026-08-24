package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiProjectKeyTransposerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `all chromatic tonic intervals are deterministic and enharmonic spelling is metadata`() {
        val input = root.resolve("normalized.mid").also(::writeInput)
        val sourceBytes = Files.readAllBytes(input)
        (0..11).forEach { target ->
            val first = root.resolve("$target-a.mid")
            val second = root.resolve("$target-b.mid")
            val sourceKey = key(0, ScaleModeId.MAJOR)
            val projectKey = key(target, if (target % 2 == 0) ScaleModeId.MAJOR else ScaleModeId.NATURAL_MINOR)
            val firstReport = MidiProjectKeyTransposer().transpose("part", input, first, sourceKey, projectKey)
            val secondReport = MidiProjectKeyTransposer().transpose("part", input, second, sourceKey, projectKey)

            assertEquals(Files.readAllBytes(first).toList(), Files.readAllBytes(second).toList())
            assertEquals(firstReport, secondReport)
            assertEquals(target.takeIf { it <= 6 } ?: target - 12, firstReport.intervalSemitones)
        }
        val enharmonic = MidiProjectKeyTransposer().transpose(
            "part", input, root.resolve("enharmonic.mid"),
            MusicalKey(PitchClass.of(PitchSpelling.C_SHARP), ScaleModeId.MAJOR),
            MusicalKey(PitchClass.of(PitchSpelling.D_FLAT), ScaleModeId.NATURAL_MINOR)
        )
        assertEquals(0, enharmonic.intervalSemitones)
        assertEquals(sourceBytes.toList(), Files.readAllBytes(input).toList())
    }

    @Test
    fun `transposition preserves timing velocity controllers tempo meter and drums while reporting octave folds`() {
        val input = root.resolve("normalized.mid").also(::writeInput)
        val output = root.resolve("transposed.mid")
        val report = MidiProjectKeyTransposer().transpose("part", input, output, key(0, ScaleModeId.MAJOR), key(1, ScaleModeId.NATURAL_MINOR))
        val before = MidiSystem.getSequence(input.toFile())
        val after = MidiSystem.getSequence(output.toFile())
        val beforeEvents = events(before)
        val afterEvents = events(after)

        assertEquals(beforeEvents.filterNot { it.notePitch != null }.map { it.copy(notePitch = null) }, afterEvents.filterNot { it.notePitch != null }.map { it.copy(notePitch = null) })
        assertEquals(metaEvents(before), metaEvents(after))
        assertEquals(listOf(1, 36, 61, 116), noteOns(after).map { it.data1 }.sorted())
        assertEquals(36, noteOns(after).single { it.channel == 9 }.data1)
        assertTrue(report.movements.any { it.sourcePitch == 127 && it.outputPitch == 116 && it.octaveFolded })
        assertTrue(report.warnings.contains("OCTAVE_FOLD_APPLIED"))
        assertTrue(report.warnings.contains("PERCUSSION_CHANNEL_PRESERVED"))
        assertTrue(report.warnings.contains("MODE_AWARE_SCALE_DEGREES"))
        assertEquals(3, report.chordFit.noteOnsets)
        assertEquals(4, report.output.noteCount)
    }

    @Test
    fun `mode change maps recognized scale degrees and reports unresolved chromatic notes`() {
        val input = root.resolve("major.mid").also { writeScaleMidi(it, listOf(64, 66, 69, 71)) }
        val output = root.resolve("minor.mid")
        val report = MidiProjectKeyTransposer().transpose("part", input, output, key(0, ScaleModeId.MAJOR), key(0, ScaleModeId.NATURAL_MINOR))

        assertEquals(listOf(63, 66, 68, 70), noteOns(MidiSystem.getSequence(output.toFile())).map { it.data1 }.sorted())
        assertEquals(3, report.modeAdjustedMovements)
        assertEquals(listOf(66), report.unresolvedChromaticSourceNotes.map { it.sourcePitch })
        assertTrue(report.unresolvedChromaticSourceNotes.all { it.mappingKind == MidiPitchMappingKind.UNRESOLVED_CHROMATIC })
        assertTrue(report.warnings.contains("UNRESOLVED_CHROMATIC_SOURCE_NOTES"))
    }

    @Test
    fun `g major maps every recognized degree to corresponding c natural minor degree`() {
        val input = root.resolve("g-major.mid").also { writeScaleMidi(it, listOf(55, 57, 59, 60, 62, 64, 66)) }
        val output = root.resolve("c-minor.mid")
        val report = MidiProjectKeyTransposer().transpose("part", input, output, key(7, ScaleModeId.MAJOR), key(0, ScaleModeId.NATURAL_MINOR))

        assertEquals(listOf(60, 62, 63, 65, 67, 68, 70), noteOns(MidiSystem.getSequence(output.toFile())).map { it.data1 }.sorted())
        assertEquals(7, report.modeAdjustedMovements)
        assertTrue(report.unresolvedChromaticSourceNotes.isEmpty())
    }

    @Test
    fun `tonic-only report versions are rejected as stale`() {
        val input = root.resolve("source.mid").also { writeScaleMidi(it, listOf(64)) }
        val output = root.resolve("output.mid")
        val current = MidiProjectKeyTransposer().transpose("part", input, output, key(0, ScaleModeId.MAJOR), key(0, ScaleModeId.NATURAL_MINOR))
        val staleReference = "transposition-v1.json"
        Files.writeString(root.resolve(staleReference), Json.encodeToString(current.copy(version = 1, processorVersion = "1")))

        assertTrue(runCatching { current.copy(version = 1, processorVersion = "1").requireValid() }.isFailure)
        assertFalse(MidiTranspositionReportStore.isCurrent(root, "part", input, output, key(0, ScaleModeId.MAJOR), key(0, ScaleModeId.NATURAL_MINOR), staleReference))
    }

    private fun key(chromatic: Int, mode: ScaleModeId) = MusicalKey(PitchClass.canonical(chromatic), mode)

    private fun writeInput(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            add(MidiEvent(tempo(500_000), 0)); add(MidiEvent(meter(), 0))
            add(MidiEvent(ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 1, 73), 8))
            listOf(0 to 60, 12 to 0, 24 to 127, 36 to 36).forEach { (tick, pitch) ->
                val channel = if (pitch == 36) 9 else 0
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, 91), tick.toLong()))
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), (tick + 120).toLong()))
            }
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun writeScaleMidi(path: Path, pitches: List<Int>) {
        val sequence = Sequence(Sequence.PPQ, 480)
        sequence.createTrack().apply {
            pitches.forEachIndexed { index, pitch ->
                val tick = index * 240L
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 91), tick))
                add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), tick + 120))
            }
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun noteOns(sequence: Sequence): List<ShortEvent> = events(sequence).filter { it.command == ShortMessage.NOTE_ON && it.velocity > 0 }
    private fun metaEvents(sequence: Sequence): List<Pair<Long, Pair<Int, List<Byte>>>> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
        val event = track[index]; val meta = event.message as? MetaMessage ?: return@mapNotNull null
        if (meta.type == 0x2f) null else event.tick to (meta.type to meta.data.toList())
    } }.sortedBy { it.first }
    private fun events(sequence: Sequence): List<ShortEvent> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index ->
        val event = track[index]; val short = event.message as? ShortMessage ?: return@mapNotNull null
        ShortEvent(event.tick, short.command, short.channel, short.data1, short.data2, if (short.command == ShortMessage.NOTE_ON || short.command == ShortMessage.NOTE_OFF) short.data1 else null)
    } }.sortedWith(compareBy<ShortEvent> { it.tick }.thenBy { it.command }.thenBy { it.channel }.thenBy { it.data1 }.thenBy { it.velocity })

    private data class ShortEvent(val tick: Long, val command: Int, val channel: Int, val data1: Int, val velocity: Int, val notePitch: Int?)
    private fun tempo(value: Int) = MetaMessage().also { it.setMessage(0x51, byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte()), 3) }
    private fun meter() = MetaMessage().also { it.setMessage(0x58, byteArrayOf(4, 2, 24, 8), 4) }
}
