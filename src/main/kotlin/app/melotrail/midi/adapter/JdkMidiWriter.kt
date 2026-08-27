package app.melotrail.midi.adapter

import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.SemanticMidiEvent
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import javax.sound.midi.Track

/** The one target adapter that writes deterministic SMF format-1 song and role files. */
class JdkMidiWriter {
    fun writeComplete(song: MidiExportSong, output: Path) = write(song, song.roles, output)

    fun writeRole(song: MidiExportSong, role: MidiExportRole, output: Path) = write(song, listOf(song.role(role)), output)

    /** Build the same target format-1 sequence used by file export without touching the filesystem. */
    fun toSequence(song: MidiExportSong, roles: List<MidiExportRoleTrack> = song.roles): Sequence {
        val sequence = Sequence(Sequence.PPQ, song.ppq.value)
        conductor(sequence.createTrack(), song)
        roles.forEach { roleTrack(sequence.createTrack(), it, song.songEndTick) }
        return sequence
    }

    private fun write(song: MidiExportSong, roles: List<MidiExportRoleTrack>, output: Path) {
        require(MidiSystem.write(toSequence(song, roles), 1, output.toFile()) > 0) {
            "Could not write Standard MIDI format 1 output: $output"
        }
    }

    private fun conductor(track: Track, song: MidiExportSong) {
        track.add(MidiEvent(textMeta(SEQUENCE_NAME, song.sequenceName), 0))
        track.add(MidiEvent(textMeta(TRACK_NAME, "Conductor"), 0))
        track.add(MidiEvent(MetaMessage(TEMPO, tempoData(song.tempoMicrosecondsPerQuarter), 3), 0))
        track.add(MidiEvent(MetaMessage(TIME_SIGNATURE, byteArrayOf(song.meterNumerator.toByte(), song.meterDenominatorExponent.toByte(), 24, 8), 4), 0))
        song.markers.forEach { marker -> track.add(MidiEvent(textMeta(MARKER, marker.renderedLabel()), marker.tick)) }
        end(track, song.songEndTick)
    }

    private fun roleTrack(track: Track, roleTrack: MidiExportRoleTrack, songEndTick: Long) {
        track.add(MidiEvent(textMeta(TRACK_NAME, roleTrack.role.trackName), 0))
        roleTrack.events.forEach { event -> writeEvent(track, event, roleTrack.role.channel) }
        end(track, songEndTick)
    }

    private fun writeEvent(track: Track, event: SemanticMidiEvent, channel: Int) {
        when (event) {
            is MidiNoteEvent -> {
                track.add(MidiEvent(short(ShortMessage.NOTE_ON, channel, event.pitch, event.velocity), event.orderingKey.tick))
                track.add(MidiEvent(short(ShortMessage.NOTE_OFF, channel, event.pitch, event.releaseVelocity ?: 0), event.endTick))
            }
            is MidiControlChangeEvent -> track.add(MidiEvent(short(ShortMessage.CONTROL_CHANGE, channel, event.controller, event.value), event.orderingKey.tick))
            is MidiPitchBendEvent -> {
                val unsigned = event.value + 8192
                track.add(MidiEvent(short(ShortMessage.PITCH_BEND, channel, unsigned and 0x7f, unsigned ushr 7), event.orderingKey.tick))
            }
            is MidiChannelPressureEvent -> track.add(MidiEvent(short(ShortMessage.CHANNEL_PRESSURE, channel, event.pressure, 0), event.orderingKey.tick))
            else -> error("${event.kind} events are not allowed in exported role tracks")
        }
    }

    private fun end(track: Track, tick: Long) = track.add(MidiEvent(MetaMessage(END_OF_TRACK, byteArrayOf(), 0), tick))

    private fun textMeta(type: Int, value: String) = MetaMessage(type, value.toByteArray(StandardCharsets.UTF_8), value.toByteArray(StandardCharsets.UTF_8).size)

    private fun short(command: Int, channel: Int, first: Int, second: Int) = ShortMessage(command, channel, first, second)

    private fun tempoData(microseconds: Int) = byteArrayOf((microseconds ushr 16).toByte(), (microseconds ushr 8).toByte(), microseconds.toByte())

    private companion object {
        const val SEQUENCE_NAME = 0x00
        const val TRACK_NAME = 0x03
        const val MARKER = 0x06
        const val TEMPO = 0x51
        const val TIME_SIGNATURE = 0x58
        const val END_OF_TRACK = 0x2f
    }
}
