package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.serialization.json.Json
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiMonophonicMelodyPreparerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `chords octave doubles cross-pitch overlaps and channels reduce to one deterministic track`() {
        val input = input("chord.mid") { first ->
            first.note(0, 0, 60, 80, 480)
            first.note(0, 0, 64, 70, 480)
            first.note(0, 0, 72, 60, 480)
            first.track().note(1, 240, 67, 90, 720)
        }
        val before = Files.readAllBytes(input)

        val first = MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input))
        val second = MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input))
        val output = MidiSystem.getSequence(root.resolve(first.midi.path).toFile())

        assertEquals(first, second)
        assertContentEquals(before, Files.readAllBytes(input))
        assertEquals(listOf(Note(0, 240, 60, 80), Note(240, 720, 67, 90)), notes(output))
        assertEquals(1, output.tracks.size)
        assertTrue(output.tracks.all { track -> (0 until track.size()).none { index ->
            (track[index].message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE
        } })
        assertTrue(output.tracks.all { track -> (0 until track.size()).filter { index ->
            (track[index].message as? ShortMessage)?.command in setOf(ShortMessage.NOTE_ON, ShortMessage.NOTE_OFF)
        }.all { index -> (track[index].message as ShortMessage).channel == 0 } })
        assertEquals(4, first.preparation.maximumEffectivePolyphony)
        assertEquals(1, first.preparation.maximumOutputPolyphony)
        assertTrue(first.preparation.decisions.any { it.kind == MelodyPreparationDecisionKind.DEDUPLICATED || it.kind == MelodyPreparationDecisionKind.REMOVED_OVERLAP })
        assertTrue(first.preparation.decisions.any { it.kind == MelodyPreparationDecisionKind.TRIMMED_END })
    }

    @Test
    fun `transcription confidence wins repeated note-on overlap before velocity and duration`() {
        val input = input("confidence.mid") { track ->
            track.note(0, 0, 60, 120, 480)
            track.note(0, 120, 60, 20, 600)
        }
        val preparer = MidiMonophonicMelodyPreparer(TranscriptionConfidenceProvider { identity ->
            when (identity.id) {
                "t0-e0" -> 0.1
                "t0-e1" -> 0.9
                else -> null
            }
        })

        val artifact = preparer.prepare(root, "A", reference(input))

        assertEquals(listOf(Note(0, 120, 60, 120), Note(120, 600, 60, 20)), notes(MidiSystem.getSequence(root.resolve(artifact.midi.path).toFile())))
        assertTrue(artifact.preparation.decisions.any { it.kind == MelodyPreparationDecisionKind.TRIMMED_END && it.sourceNoteId == "t0-e0" })
        assertEquals(2, artifact.preparation.sourceNotes.size)
    }

    @Test
    fun `sustain is channel-local and materializes pedal-up EOF and repeated-control releases`() {
        val input = input("sustain.mid") { track ->
            track.control(0, 50, 64, 127)
            track.control(0, 60, 64, 127)
            track.note(0, 0, 60, 100, 120)
            track.control(0, 240, 64, 0)
            track.note(1, 180, 67, 80, 220)
            track.control(1, 250, 64, 127)
            track.control(0, 290, 64, 127)
            track.note(0, 300, 64, 90, 360)
            track.control(0, 480, 1, 0)
        }

        val artifact = MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input))
        val prepared = artifact.preparation

        assertEquals(listOf(Note(0, 240, 60, 100), Note(300, 480, 64, 90)), notes(MidiSystem.getSequence(root.resolve(artifact.midi.path).toFile())))
        assertEquals(MelodyPreparationReleaseKind.PEDAL_UP, prepared.sourceNotes.single { it.pitch == 60 }.releaseKind)
        assertEquals(MelodyPreparationReleaseKind.END_OF_FILE, prepared.sourceNotes.single { it.pitch == 64 }.releaseKind)
        assertEquals(2, prepared.maximumEffectivePolyphony)
        assertTrue(prepared.decisions.any { it.sourceNoteId == prepared.sourceNotes.single { it.pitch == 67 }.id && it.kind == MelodyPreparationDecisionKind.REMOVED_OVERLAP })
        assertTrue(prepared.controllers.any { it.action == MelodyPreparationControllerAction.SUSTAIN_REPEATED })
        assertTrue(prepared.controllers.any { it.source.channel == 1 && it.action == MelodyPreparationControllerAction.SUSTAIN_DOWN })
    }

    @Test
    fun `all-notes all-sound and reset controls release only their channel material`() {
        val input = input("channel-controls.mid") { track ->
            track.noteOn(0, 0, 60, 90)
            track.control(0, 120, 123, 0)
            track.note(0, 200, 62, 90, 300)
            track.control(0, 250, 64, 127)
            track.control(0, 360, 121, 0)
            track.noteOn(0, 400, 64, 90)
            track.control(0, 440, 120, 0)
        }

        val artifact = MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input))
        val releases = artifact.preparation.sourceNotes.associateBy { it.pitch }.mapValues { it.value.releaseKind }

        assertEquals(listOf(Note(0, 120, 60, 90), Note(200, 360, 62, 90), Note(400, 440, 64, 90)), notes(MidiSystem.getSequence(root.resolve(artifact.midi.path).toFile())))
        assertEquals(MelodyPreparationReleaseKind.ALL_NOTES_OFF, releases[60])
        assertEquals(MelodyPreparationReleaseKind.RESET_ALL_CONTROLLERS, releases[62])
        assertEquals(MelodyPreparationReleaseKind.ALL_SOUND_OFF, releases[64])
    }

    @Test
    fun `malformed pairs publish a blocked report without a candidate`() {
        val input = input("blocked.mid") { track ->
            track.noteOff(0, 0, 60)
            track.noteOn(0, 20, 62, 80)
        }

        val failure = assertFailsWith<IllegalArgumentException> { MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input)) }
        val reportPath = Files.list(root.resolve("analysis/melody-preparation/A")).use { paths -> paths.findFirst().orElseThrow() }
        val report = Json.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(reportPath))

        assertTrue(failure.message.orEmpty().contains("blocked"))
        assertEquals(MelodyPreparationStatus.BLOCKED, report.status)
        assertEquals(null, report.output)
        assertTrue(report.issues.any { it.kind == MelodyPreparationIssueKind.UNMATCHED_NOTE_OFF })
        assertTrue(report.issues.any { it.kind == MelodyPreparationIssueKind.UNMATCHED_NOTE_ON })
        assertTrue(Files.notExists(root.resolve("midi/prepared/A")))
    }

    @Test
    fun `equal-priority cross-pitch overlap blocks rather than guessing`() {
        val input = input("ambiguous.mid") { track ->
            track.note(0, 0, 60, 80, 120)
            track.note(1, 0, 65, 80, 120)
        }

        assertFailsWith<IllegalArgumentException> { MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input)) }
        val reportPath = Files.list(root.resolve("analysis/melody-preparation/A")).use { paths -> paths.findFirst().orElseThrow() }
        val report = Json.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(reportPath))

        assertEquals(MelodyPreparationStatus.BLOCKED, report.status)
        assertTrue(report.issues.any { it.kind == MelodyPreparationIssueKind.AMBIGUOUS_OVERLAP })
        assertTrue(report.decisions.any { it.kind == MelodyPreparationDecisionKind.AMBIGUOUS })
    }

    @Test
    fun `percussion-only material is blocked rather than published as a melody`() {
        val input = input("drums.mid") { track -> track.note(9, 0, 36, 100, 120) }

        assertFailsWith<IllegalArgumentException> { MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input)) }
        val reportPath = Files.list(root.resolve("analysis/melody-preparation/A")).use { paths -> paths.findFirst().orElseThrow() }
        val report = Json.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(reportPath))

        assertTrue(report.issues.any { it.kind == MelodyPreparationIssueKind.NO_MELODIC_NOTES })
        assertEquals(MelodyPreparationStatus.BLOCKED, report.status)
    }

    private fun input(name: String, populate: (TrackBuilder) -> Unit): Path {
        val path = root.resolve("midi/input/$name")
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        populate(TrackBuilder(sequence.createTrack(), sequence))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun reference(path: Path): MelodyPreparationArtifactReference {
        val sequence = MidiSystem.getSequence(path.toFile())
        val noteOnCount = sequence.tracks.sumOf { track -> (0 until track.size()).count { index ->
            val message = track[index].message as? ShortMessage
            message?.command == ShortMessage.NOTE_ON && message.data2 > 0
        } }
        return MelodyPreparationArtifactReference(root.relativize(path).toString(), hash(path), sequence.resolution, noteOnCount)
    }

    private fun notes(sequence: Sequence): List<Note> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .mapNotNull { event -> (event.message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { on ->
            val off = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.first { candidate ->
                candidate.tick > event.tick && (candidate.message as? ShortMessage)?.let { it.command == ShortMessage.NOTE_OFF && it.channel == on.channel && it.data1 == on.data1 } == true
            }
            Note(event.tick, off.tick, on.data1, on.data2)
        } }.sortedBy(Note::start)

    private fun hash(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private data class Note(val start: Long, val end: Long, val pitch: Int, val velocity: Int)

    private class TrackBuilder(private val track: javax.sound.midi.Track, private val sequence: Sequence) {
        fun track(): TrackBuilder = TrackBuilder(sequence.createTrack(), sequence)
        fun note(channel: Int, start: Long, pitch: Int, velocity: Int, end: Long) { noteOn(channel, start, pitch, velocity); noteOff(channel, end, pitch) }
        fun noteOn(channel: Int, tick: Long, pitch: Int, velocity: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), tick)) }
        fun noteOff(channel: Int, tick: Long, pitch: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), tick)) }
        fun control(channel: Int, tick: Long, controller: Int, value: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value), tick)) }
    }
}
