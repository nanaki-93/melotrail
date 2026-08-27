package app.melotrail.midi.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** The only supported V1 expression policy for the protected melody export projection. */
enum class MidiMelodyExpressionPolicy {
    PRESERVE_SELECTED_CHANNEL_CONTROL_CHANGE_PITCH_BEND_AND_CHANNEL_PRESSURE,
}

@JvmInline
value class MidiProtectedMelodyNoteId(val value: String) {
    init { require(PATTERN.matches(value)) { "Protected melody note ID is invalid" } }

    companion object {
        private val PATTERN = Regex("pmn-[0-9a-f]{64}")
    }
}

/** Immutable source identity for one note in the protected melody channel. */
data class MidiProtectedMelodyNote(
    val id: MidiProtectedMelodyNoteId,
    val sourceEvent: MidiSourceEventIdentity,
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int,
    val releaseVelocity: Int?,
) {
    init {
        require(startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 0..127) {
            "Protected melody note values are invalid"
        }
        require(releaseVelocity == null || releaseVelocity in 0..127) { "Protected melody release velocity is invalid" }
    }
}

/**
 * Deterministic, channel-1-ready view over exactly one immutable source channel.
 * Source event identities remain intact so the view can be re-derived and compared.
 */
data class MidiProtectedMelodyView(
    val sourceSha256: String,
    val sourceTrackIndex: Int,
    val sourceChannel: Int,
    val ppq: MidiPpq,
    val expressionPolicy: MidiMelodyExpressionPolicy,
    val events: List<SemanticMidiEvent>,
    val notes: List<MidiProtectedMelodyNote>,
    val protectedAnchorIds: List<MidiProtectedMelodyNoteId>,
    val identitySha256: String,
) {
    init {
        require(SHA_256.matches(sourceSha256) && sourceTrackIndex >= 0 && sourceChannel in 0..15 && SHA_256.matches(identitySha256)) {
            "Protected melody source identity is invalid"
        }
        require(events == events.sortedBy(SemanticMidiEvent::orderingKey) && events.isNotEmpty()) { "Protected melody events must be ordered and non-empty" }
        require(events.map(SemanticMidiEvent::orderingKey).distinct().size == events.size) { "Protected melody events must be unique" }
        require(events.all { event ->
            event is MidiNoteEvent || event is MidiControlChangeEvent || event is MidiPitchBendEvent || event is MidiChannelPressureEvent
        }) { "Protected melody contains an unsupported event" }
        require(events.all { event ->
            event.orderingKey.sourceEvent?.trackIndex == sourceTrackIndex && outputChannel(event) == OUTPUT_CHANNEL
        }) { "Protected melody event does not belong to the selected source channel projection" }
        val eventNotes = events.filterIsInstance<MidiNoteEvent>().map { note ->
            MidiProtectedMelodyNote(noteId(sourceSha256, sourceTrackIndex, sourceChannel, note), requireNotNull(note.orderingKey.sourceEvent),
                note.orderingKey.tick, note.endTick, note.pitch, note.velocity, note.releaseVelocity)
        }
        require(notes == eventNotes) { "Protected melody notes must exactly match projected note events" }
        require(protectedAnchorIds == protectedAnchorIds.distinct().sortedBy(MidiProtectedMelodyNoteId::value) && protectedAnchorIds.all { it in notes.map(MidiProtectedMelodyNote::id) }) {
            "Protected melody anchors are invalid"
        }
        require(identitySha256 == identity(sourceSha256, sourceTrackIndex, sourceChannel, ppq, expressionPolicy, events, protectedAnchorIds)) {
            "Protected melody identity digest does not match the immutable view"
        }
    }

    companion object {
        const val OUTPUT_CHANNEL = 0

        internal fun noteId(sourceSha256: String, trackIndex: Int, sourceChannel: Int, note: MidiNoteEvent): MidiProtectedMelodyNoteId {
            val sourceEvent = requireNotNull(note.orderingKey.sourceEvent) { "Protected melody note must have source identity" }
            return MidiProtectedMelodyNoteId("pmn-" + sha256(
                "melotrail-midi-protected-note-v1|$sourceSha256|$trackIndex|$sourceChannel|${sourceEvent.eventIndex}|${note.orderingKey.tick}|${note.endTick}|${note.pitch}|${note.velocity}|${note.releaseVelocity}",
            ))
        }

        internal fun identity(
            sourceSha256: String,
            trackIndex: Int,
            sourceChannel: Int,
            ppq: MidiPpq,
            expressionPolicy: MidiMelodyExpressionPolicy,
            events: List<SemanticMidiEvent>,
            anchors: List<MidiProtectedMelodyNoteId>,
        ): String = sha256(buildString {
            append("melotrail-midi-protected-view-v1\n")
            append(sourceSha256).append('|').append(trackIndex).append('|').append(sourceChannel).append('|').append(ppq.value).append('|').append(expressionPolicy.name).append('\n')
            events.forEach { event -> append(eventIdentity(event)).append('\n') }
            anchors.forEach { anchor -> append("anchor|").append(anchor.value).append('\n') }
        })

        private fun eventIdentity(event: SemanticMidiEvent): String {
            val source = requireNotNull(event.orderingKey.sourceEvent) { "Protected melody event must have source identity" }
            val prefix = "${event.kind.name}|${source.trackIndex}|${source.eventIndex}|${event.orderingKey.tick}|"
            return when (event) {
                is MidiNoteEvent -> "$prefix${event.endTick}|${event.pitch}|${event.velocity}|${event.releaseVelocity}"
                is MidiControlChangeEvent -> "$prefix${event.controller}|${event.value}"
                is MidiPitchBendEvent -> "$prefix${event.value}"
                is MidiChannelPressureEvent -> "$prefix${event.pressure}"
                else -> error("Unsupported protected melody event: ${event.kind}")
            }
        }

        private fun outputChannel(event: SemanticMidiEvent): Int = when (event) {
            is MidiNoteEvent -> event.channel
            is MidiControlChangeEvent -> event.channel
            is MidiPitchBendEvent -> event.channel
            is MidiChannelPressureEvent -> event.channel
            else -> -1
        }
    }
}

/** Stable failures for the one-track/one-channel protected melody boundary. */
enum class MidiMelodySelectionFailure {
    TRACK_NOT_FOUND,
    CHANNEL_NOT_FOUND,
    NO_COMPLETE_NOTES,
    UNSUPPORTED_MPE_LIKE_EXPRESSION,
}

class MidiMelodySelectionException(val failure: MidiMelodySelectionFailure, message: String) : IllegalArgumentException(message)

/** Extracts the source-preserving, channel-1 projection consumed by target arrangement/export work. */
class MidiProtectedMelodySelector {
    fun select(sequence: SemanticMidiSequence, selection: MidiMelodySelection): MidiProtectedMelodyView {
        val track = sequence.tracks.getOrNull(selection.trackIndex) ?: throw MidiMelodySelectionException(
            MidiMelodySelectionFailure.TRACK_NOT_FOUND,
            "Selected melody track ${selection.trackIndex} is not present in the immutable source.",
        )
        rejectMpeLikeExpression(track, selection)
        val selectedEvents = track.events.filter { event -> sourceChannel(event) == selection.channel }
            .filter { event -> event is MidiNoteEvent || event is MidiControlChangeEvent || event is MidiPitchBendEvent || event is MidiChannelPressureEvent }
            .map(::toOutputChannel)
        if (selectedEvents.none { it is MidiNoteEvent }) throw MidiMelodySelectionException(
            if (track.events.any { sourceChannel(it) == selection.channel }) MidiMelodySelectionFailure.NO_COMPLETE_NOTES else MidiMelodySelectionFailure.CHANNEL_NOT_FOUND,
            "Selected melody track/channel has no complete notes.",
        )
        val notes = selectedEvents.filterIsInstance<MidiNoteEvent>().map { note ->
            MidiProtectedMelodyNote(
                MidiProtectedMelodyView.noteId(sequence.source.sha256, selection.trackIndex, selection.channel, note),
                requireNotNull(note.orderingKey.sourceEvent),
                note.orderingKey.tick,
                note.endTick,
                note.pitch,
                note.velocity,
                note.releaseVelocity,
            )
        }
        val anchors = protectedAnchors(notes, sequence.source.ppq).sortedBy(MidiProtectedMelodyNoteId::value)
        val policy = MidiMelodyExpressionPolicy.PRESERVE_SELECTED_CHANNEL_CONTROL_CHANGE_PITCH_BEND_AND_CHANNEL_PRESSURE
        return MidiProtectedMelodyView(
            sequence.source.sha256,
            selection.trackIndex,
            selection.channel,
            sequence.source.ppq,
            policy,
            selectedEvents,
            notes,
            anchors,
            MidiProtectedMelodyView.identity(sequence.source.sha256, selection.trackIndex, selection.channel, sequence.source.ppq, policy, selectedEvents, anchors),
        )
    }

    private fun rejectMpeLikeExpression(track: SemanticMidiTrack, selection: MidiMelodySelection) {
        val noteChannels = track.events.filterIsInstance<MidiNoteEvent>().map(MidiNoteEvent::channel).toSet()
        val expressiveNoteChannels = track.events.filter { it is MidiPitchBendEvent || it is MidiChannelPressureEvent }
            .map(::sourceChannel)
            .filter { it in noteChannels }
            .toSet()
        if (selection.channel in expressiveNoteChannels && expressiveNoteChannels.size > 1) throw MidiMelodySelectionException(
            MidiMelodySelectionFailure.UNSUPPORTED_MPE_LIKE_EXPRESSION,
            "The selected track uses expression across multiple note channels and cannot be a V1 protected melody.",
        )
    }

    private fun protectedAnchors(notes: List<MidiProtectedMelodyNote>, ppq: MidiPpq): List<MidiProtectedMelodyNoteId> {
        val ordered = notes.sortedWith(compareBy<MidiProtectedMelodyNote> { it.startTick }.thenBy { it.sourceEvent.eventIndex })
        val phrases = mutableListOf<MutableList<MidiProtectedMelodyNote>>()
        var phraseEnd = Long.MIN_VALUE
        ordered.forEach { note ->
            if (phrases.isEmpty() || note.startTick - phraseEnd >= ppq.value.toLong()) phrases.add(mutableListOf())
            phrases.last() += note
            phraseEnd = maxOf(phraseEnd, note.endTick)
        }
        return buildSet {
            phrases.forEach { phrase ->
                add(phrase.first().id)
                add(phrase.last().id)
                phrase.filter { it.endTick - it.startTick >= ppq.value }.forEach { add(it.id) }
                phrase.filter { it.endTick - it.startTick >= ppq.value / 2L }.let { held ->
                    held.minByOrNull(MidiProtectedMelodyNote::pitch)?.let { add(it.id) }
                    held.maxByOrNull(MidiProtectedMelodyNote::pitch)?.let { add(it.id) }
                }
            }
        }.toList()
    }

    private fun toOutputChannel(event: SemanticMidiEvent): SemanticMidiEvent = when (event) {
        is MidiNoteEvent -> event.copy(channel = MidiProtectedMelodyView.OUTPUT_CHANNEL)
        is MidiControlChangeEvent -> event.copy(channel = MidiProtectedMelodyView.OUTPUT_CHANNEL)
        is MidiPitchBendEvent -> event.copy(channel = MidiProtectedMelodyView.OUTPUT_CHANNEL)
        is MidiChannelPressureEvent -> event.copy(channel = MidiProtectedMelodyView.OUTPUT_CHANNEL)
        else -> error("Unsupported protected melody event: ${event.kind}")
    }

    private fun sourceChannel(event: SemanticMidiEvent): Int? = when (event) {
        is MidiNoteEvent -> event.channel
        is MidiControlChangeEvent -> event.channel
        is MidiPitchBendEvent -> event.channel
        is MidiChannelPressureEvent -> event.channel
        else -> null
    }
}

private val SHA_256 = Regex("[0-9a-f]{64}")

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
