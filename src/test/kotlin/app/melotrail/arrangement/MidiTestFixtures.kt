package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

internal fun writeTestMidi(path: Path, pitch: Int = 60) {
    Files.createDirectories(requireNotNull(path.parent))
    val sequence = Sequence(Sequence.PPQ, 480)
    sequence.createTrack().apply {
        add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0))
        add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 240))
    }
    require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
}
