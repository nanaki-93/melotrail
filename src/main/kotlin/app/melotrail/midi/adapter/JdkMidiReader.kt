package app.melotrail.midi.adapter

import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiInspectionResult
import app.melotrail.midi.domain.MidiReaderIssue
import app.melotrail.midi.domain.MidiReaderIssueCode
import app.melotrail.midi.domain.MidiMarkerEvent
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiSourceEventIdentity
import app.melotrail.midi.domain.MidiSourceIdentity
import app.melotrail.midi.domain.MidiTempoEvent
import app.melotrail.midi.domain.MidiTextEvent
import app.melotrail.midi.domain.MidiTextKind
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import app.melotrail.midi.domain.MidiTrackNameEvent
import app.melotrail.midi.domain.MidiUnsupportedEvent
import app.melotrail.midi.domain.SemanticMidiEvent
import app.melotrail.midi.domain.SemanticMidiSequence
import app.melotrail.midi.domain.SemanticMidiTrack
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/** The one target adapter that translates JDK MIDI objects into semantic MIDI. */
class JdkMidiReader {
    fun inspect(path: Path): MidiInspectionResult {
        require(Files.isRegularFile(path)) { "MIDI source is not a regular file: $path" }
        val fileFormat = try {
            MidiSystem.getMidiFileFormat(path.toFile())
        } catch (error: Exception) {
            throw MidiReadException("MIDI source is unreadable: ${error.message ?: error.javaClass.simpleName}", error)
        }
        require(fileFormat.type in setOf(0, 1)) { "Unsupported Standard MIDI format ${fileFormat.type}; only format 0 and 1 are supported" }
        val jdkSequence = try {
            MidiSystem.getSequence(path.toFile())
        } catch (error: Exception) {
            throw MidiReadException("MIDI source is unreadable: ${error.message ?: error.javaClass.simpleName}", error)
        }
        require(jdkSequence.divisionType == Sequence.PPQ) { "Unsupported MIDI timing division; only PPQ is supported" }
        require(jdkSequence.resolution > 0) { "MIDI PPQ must be positive" }

        val source = MidiSourceIdentity(
            sha256 = sha256(path),
            originalFilename = path.fileName.toString(),
            format = fileFormat.type,
            ppq = MidiPpq(jdkSequence.resolution),
        )
        val translated = jdkSequence.tracks.mapIndexed { trackIndex, track -> translateTrack(trackIndex, (0 until track.size()).map(track::get)) }
        val sequence = SemanticMidiSequence(source, translated.map(TranslatedTrack::track))
        val summaries = translated.map(TranslatedTrack::summary)
        val findings = translated.flatMap(TranslatedTrack::findings).sortedWith(MIDI_READER_ISSUE_ORDER)
        return MidiInspectionResult(sequence, summaries, findings, jdkSequence.tickLength)
    }

    private fun translateTrack(trackIndex: Int, events: List<MidiEvent>): TranslatedTrack {
        val semanticEvents = mutableListOf<SemanticMidiEvent>()
        val findings = mutableListOf<MidiReaderIssue>()
        val activeNotes = mutableMapOf<Pair<Int, Int>, PendingNote>()
        val channelFacts = mutableMapOf<Int, MutableChannelFacts>()

        events.forEachIndexed { eventIndex, event ->
            val sourceEvent = MidiSourceEventIdentity(trackIndex, eventIndex)
            when (val message = event.message) {
                is MetaMessage -> translateMeta(event.tick, sourceEvent, message, semanticEvents, findings)
                is ShortMessage -> translateShort(event.tick, sourceEvent, message, semanticEvents, findings, activeNotes, channelFacts)
                else -> unsupported(event.tick, sourceEvent, message.javaClass.simpleName, "Message is outside the V1 semantic model", semanticEvents, findings)
            }
        }
        activeNotes.values.sortedBy { pending -> pending.sourceEvent.eventIndex }.forEach { pending ->
            findings += MidiReaderIssue(
                MidiReaderIssueCode.UNCLOSED_NOTE_ON,
                trackIndex,
                pending.sourceEvent.eventIndex,
                pending.startTick,
                "Note-on has no matching note-off (channel ${pending.channel}, pitch ${pending.pitch})",
            )
        }
        return TranslatedTrack(
            SemanticMidiTrack(trackIndex, semanticEvents),
            MidiTrackSummary(trackIndex, trackName(semanticEvents), channelFacts.values.map(MutableChannelFacts::summary).sortedBy(MidiChannelSummary::channel)),
            findings,
        )
    }

    private fun translateMeta(
        tick: Long,
        sourceEvent: MidiSourceEventIdentity,
        message: MetaMessage,
        semanticEvents: MutableList<SemanticMidiEvent>,
        findings: MutableList<MidiReaderIssue>,
    ) {
        val data = message.data
        when (message.type) {
            TEMPO -> if (data.size == 3) {
                val micros = ((data[0].toInt() and 0xff) shl 16) or ((data[1].toInt() and 0xff) shl 8) or (data[2].toInt() and 0xff)
                if (micros > 0) semanticEvents += MidiTempoEvent(key(tick, MidiSemanticEventKind.TEMPO, sourceEvent), micros)
                else unsupported(tick, sourceEvent, "tempo", "Tempo microseconds per quarter must be positive", semanticEvents, findings)
            } else unsupported(tick, sourceEvent, "tempo", "Tempo meta event must contain three data bytes", semanticEvents, findings)
            TIME_SIGNATURE -> if (data.size >= 4 && (data[1].toInt() and 0xff) <= 30) {
                semanticEvents += MidiTimeSignatureEvent(
                    key(tick, MidiSemanticEventKind.TIME_SIGNATURE, sourceEvent),
                    data[0].toInt() and 0xff,
                    data[1].toInt() and 0xff,
                    data[2].toInt() and 0xff,
                    data[3].toInt() and 0xff,
                )
            } else unsupported(tick, sourceEvent, "time-signature", "Time-signature meta event is not representable", semanticEvents, findings)
            TRACK_NAME -> text(data).takeIf(String::isNotBlank)?.let { semanticEvents += MidiTrackNameEvent(key(tick, MidiSemanticEventKind.TRACK_NAME, sourceEvent), it) }
                ?: unsupported(tick, sourceEvent, "track-name", "Track name is blank", semanticEvents, findings)
            MARKER -> text(data).takeIf(String::isNotBlank)?.let { semanticEvents += MidiMarkerEvent(key(tick, MidiSemanticEventKind.MARKER, sourceEvent), it) }
                ?: unsupported(tick, sourceEvent, "marker", "Marker text is blank", semanticEvents, findings)
            TEXT, COPYRIGHT, LYRIC, CUE, SEQUENCE_NAME -> semanticEvents += MidiTextEvent(
                key(tick, MidiSemanticEventKind.TEXT, sourceEvent), textKind(message.type), text(data),
            )
            END_OF_TRACK -> Unit
            else -> unsupported(tick, sourceEvent, "meta-${message.type.toString(16)}", "Meta message is not retained by the V1 semantic model", semanticEvents, findings)
        }
    }

    private fun translateShort(
        tick: Long,
        sourceEvent: MidiSourceEventIdentity,
        message: ShortMessage,
        semanticEvents: MutableList<SemanticMidiEvent>,
        findings: MutableList<MidiReaderIssue>,
        activeNotes: MutableMap<Pair<Int, Int>, PendingNote>,
        channelFacts: MutableMap<Int, MutableChannelFacts>,
    ) {
        when {
            message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                val noteKey = message.channel to message.data1
                require(activeNotes[noteKey] == null) {
                    "Ambiguous overlapping note-on events on track ${sourceEvent.trackIndex}, channel ${message.channel}, pitch ${message.data1}"
                }
                activeNotes[noteKey] = PendingNote(sourceEvent, tick, message.channel, message.data1, message.data2)
            }
            message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0) -> {
                val noteKey = message.channel to message.data1
                val start = activeNotes.remove(noteKey)
                if (start == null) {
                    findings += MidiReaderIssue(MidiReaderIssueCode.ORPHAN_NOTE_OFF, sourceEvent.trackIndex, sourceEvent.eventIndex, tick,
                        "Note-off has no matching note-on (channel ${message.channel}, pitch ${message.data1})")
                } else if (tick <= start.startTick) {
                    findings += MidiReaderIssue(MidiReaderIssueCode.NON_POSITIVE_NOTE_DURATION, sourceEvent.trackIndex, sourceEvent.eventIndex, tick,
                        "Note duration must be positive (started at tick ${start.startTick})")
                } else {
                    semanticEvents += MidiNoteEvent(key(start.startTick, MidiSemanticEventKind.NOTE, start.sourceEvent), tick, start.channel, start.pitch, start.velocity,
                        if (message.command == ShortMessage.NOTE_OFF) message.data2 else 0)
                    channelFacts.getOrPut(start.channel) { MutableChannelFacts(start.channel) }.note(start.pitch)
                }
            }
            message.command == ShortMessage.CONTROL_CHANGE -> {
                semanticEvents += MidiControlChangeEvent(key(tick, MidiSemanticEventKind.CONTROL_CHANGE, sourceEvent), message.channel, message.data1, message.data2)
                channelFacts.getOrPut(message.channel) { MutableChannelFacts(message.channel) }.controller()
            }
            message.command == ShortMessage.PITCH_BEND -> semanticEvents += MidiPitchBendEvent(
                key(tick, MidiSemanticEventKind.PITCH_BEND, sourceEvent), message.channel, ((message.data2 shl 7) or message.data1) - 8192,
            ).also { channelFacts.getOrPut(message.channel) { MutableChannelFacts(message.channel) } }
            message.command == ShortMessage.CHANNEL_PRESSURE -> semanticEvents += MidiChannelPressureEvent(
                key(tick, MidiSemanticEventKind.CHANNEL_PRESSURE, sourceEvent), message.channel, message.data1,
            ).also { channelFacts.getOrPut(message.channel) { MutableChannelFacts(message.channel) } }
            else -> unsupported(tick, sourceEvent, "short-${message.command.toString(16)}", "Channel message is not retained by the V1 semantic model", semanticEvents, findings)
        }
    }

    private fun unsupported(
        tick: Long,
        sourceEvent: MidiSourceEventIdentity,
        messageType: String,
        detail: String,
        semanticEvents: MutableList<SemanticMidiEvent>,
        findings: MutableList<MidiReaderIssue>,
    ) {
        semanticEvents += MidiUnsupportedEvent(key(tick, MidiSemanticEventKind.UNSUPPORTED, sourceEvent), messageType, detail)
        findings += MidiReaderIssue(MidiReaderIssueCode.UNSUPPORTED_MESSAGE, sourceEvent.trackIndex, sourceEvent.eventIndex, tick, "$messageType: $detail")
    }

    private fun key(tick: Long, kind: MidiSemanticEventKind, sourceEvent: MidiSourceEventIdentity) =
        MidiEventOrderingKey(tick, kind, sourceEvent = sourceEvent)

    private fun text(bytes: ByteArray): String = bytes.toString(StandardCharsets.UTF_8)

    private fun textKind(metaType: Int): MidiTextKind = when (metaType) {
        TEXT -> MidiTextKind.TEXT
        COPYRIGHT -> MidiTextKind.COPYRIGHT
        LYRIC -> MidiTextKind.LYRIC
        CUE -> MidiTextKind.CUE
        SEQUENCE_NAME -> MidiTextKind.SEQUENCE_NAME
        else -> error("Unexpected text meta type: $metaType")
    }

    private fun trackName(events: List<SemanticMidiEvent>): String? = events.filterIsInstance<MidiTrackNameEvent>().firstOrNull()?.name

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class PendingNote(
        val sourceEvent: MidiSourceEventIdentity,
        val startTick: Long,
        val channel: Int,
        val pitch: Int,
        val velocity: Int,
    )

    private data class TranslatedTrack(val track: SemanticMidiTrack, val summary: MidiTrackSummary, val findings: List<MidiReaderIssue>)

    private class MutableChannelFacts(val channel: Int) {
        private var noteCount = 0
        private var minimumPitch: Int? = null
        private var maximumPitch: Int? = null
        private var controllerCount = 0

        fun note(pitch: Int) {
            noteCount++
            minimumPitch = minOf(minimumPitch ?: pitch, pitch)
            maximumPitch = maxOf(maximumPitch ?: pitch, pitch)
        }

        fun controller() {
            controllerCount++
        }

        fun summary(): MidiChannelSummary = MidiChannelSummary(channel, noteCount, minimumPitch, maximumPitch, controllerCount, roleHints())

        private fun roleHints(): List<MidiTrackRoleHint> = buildList {
            if (channel == PERCUSSION_CHANNEL) add(MidiTrackRoleHint.DRUMS)
            if (minimumPitch != null && maximumPitch != null && maximumPitch!! <= 52) add(MidiTrackRoleHint.BASS)
            if (noteCount > 0 && channel != PERCUSSION_CHANNEL && (maximumPitch!! - minimumPitch!! <= 24)) add(MidiTrackRoleHint.MELODY)
            if (noteCount > 1 && channel != PERCUSSION_CHANNEL && !contains(MidiTrackRoleHint.MELODY)) add(MidiTrackRoleHint.CHORDS)
        }
    }

    private companion object {
        const val TEMPO = 0x51
        const val TIME_SIGNATURE = 0x58
        const val TRACK_NAME = 0x03
        const val MARKER = 0x06
        const val TEXT = 0x01
        const val COPYRIGHT = 0x02
        const val LYRIC = 0x05
        const val CUE = 0x07
        const val SEQUENCE_NAME = 0x00
        const val END_OF_TRACK = 0x2f
        const val PERCUSSION_CHANNEL = 9
        val MIDI_READER_ISSUE_ORDER = compareBy<MidiReaderIssue> { it.trackIndex }.thenBy { it.eventIndex }.thenBy { it.tick }.thenBy { it.code.name }
    }
}

class MidiReadException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
