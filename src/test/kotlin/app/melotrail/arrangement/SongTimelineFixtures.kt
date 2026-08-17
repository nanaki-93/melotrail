package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/** Reusable, temporary-root-only MIDI evidence for the Task 073 timing contract. */
object SongTimelineFixtures {
    fun write(root: Path): SongTimelineInput {
        val a = root.resolve("midi/clean/A.mid")
        val b = root.resolve("midi/clean/B.mid")
        writePartA(a)
        writePartB(b)
        listOf("piano", "bass", "drums", "pad", "strings", "transitions").forEachIndexed { index, instrument ->
            writeInstrument(root.resolve("midi/generated/$instrument.mid"), 960, index)
        }
        return SongTimelineInput(
            occurrences = listOf(
                input("A-1", "A", "A-1-midi", a, 480, 3_840, listOf(MidiTempoChange(0, 120.0), MidiTempoChange(1_920, 90.0)), listOf(MidiTimeSignature(0, 4, 4)), listOf(0, 240, 3_600, 3_840)),
                input("A-2", "A", "A-2-midi", a, 480, 3_840, listOf(MidiTempoChange(0, 120.0), MidiTempoChange(1_920, 90.0)), listOf(MidiTimeSignature(0, 4, 4)), listOf(0, 240, 3_600, 3_840)),
                input("B-1", "B", "B-1-midi", b, 960, 5_760, listOf(MidiTempoChange(0, 100.0), MidiTempoChange(2_880, 110.0)), listOf(MidiTimeSignature(0, 3, 4)), listOf(0, 480, 5_760))
            ),
            transitions = listOf(SongTimelineTransitionRequest("A-2-to-B-1", "A-2", 1))
        )
    }

    private fun input(occurrenceId: String, partId: String, inputId: String, path: Path, ppq: Int, duration: Long, tempos: List<MidiTempoChange>, meters: List<MidiTimeSignature>, evidence: List<Long>) =
        SongTimelineOccurrenceInput(occurrenceId, partId, inputId, sha256(Files.readAllBytes(path)), ppq, duration, tempos, meters, evidence)

    private fun writePartA(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        tempo(track, 0, 120); tempo(track, 1_920, 90); signature(track, 0, 4, 4)
        note(track, 0, 120, 60); note(track, 240, 360, 62); note(track, 3_600, 3_840, 64) // boundary note-off is intentional.
        write(sequence, path)
    }

    private fun writePartB(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 960); val track = sequence.createTrack()
        tempo(track, 0, 100); tempo(track, 2_880, 110); signature(track, 0, 3, 4)
        note(track, 0, 480, 48); note(track, 2_880, 3_360, 50); note(track, 5_280, 5_760, 52)
        write(sequence, path)
    }

    private fun writeInstrument(path: Path, ppq: Int, offset: Int) {
        val sequence = Sequence(Sequence.PPQ, ppq); val track = sequence.createTrack()
        tempo(track, 0, 120); signature(track, 0, 4, 4)
        note(track, offset.toLong(), 240L + offset, 36 + offset)
        write(sequence, path)
    }

    private fun write(sequence: Sequence, path: Path) { Files.createDirectories(requireNotNull(path.parent)); MidiSystem.write(sequence, 1, path.toFile()) }
    private fun note(track: javax.sound.midi.Track, start: Long, end: Long, pitch: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 100), start)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), end)) }
    private fun tempo(track: javax.sound.midi.Track, tick: Long, bpm: Int) { val micros = 60_000_000 / bpm; track.add(MidiEvent(MetaMessage().apply { setMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }, tick)) }
    private fun signature(track: javax.sound.midi.Track, tick: Long, numerator: Int, denominator: Int) { track.add(MidiEvent(MetaMessage().apply { setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }, tick)) }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
