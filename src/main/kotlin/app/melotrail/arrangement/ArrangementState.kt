package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

/**
 * Immutable, versioned ensemble state used while building generated roles.
 * A role enters this state only after its candidate MIDI has passed the
 * generated-role validation boundary.
 */
data class ArrangementState(
    val version: Int = CURRENT_VERSION,
    val ppq: Int,
    val acceptedTracks: List<AcceptedArrangementTrack>
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported arrangement state version: $version" }
        require(ppq > 0) { "Arrangement state PPQ must be positive" }
        require(acceptedTracks.isNotEmpty()) { "Arrangement state requires an accepted piano track" }
        require(acceptedTracks.map(AcceptedArrangementTrack::role).distinct().size == acceptedTracks.size) {
            "Arrangement state cannot contain duplicate roles"
        }
        require(acceptedTracks.first().role == PIANO) { "Arrangement state must start from accepted piano MIDI" }
        require(acceptedTracks.all { it.ppq == ppq }) { "Accepted arrangement MIDI must share one PPQ" }
    }

    fun track(role: String): AcceptedArrangementTrack? = acceptedTracks.singleOrNull { it.role == role }
    fun requireTrack(role: String): AcceptedArrangementTrack = requireNotNull(track(role)) { "Arrangement state has no accepted $role track" }
    fun hasTrack(role: String): Boolean = track(role) != null

    fun summary(role: String): ArrangementTrackSummary = requireTrack(role).summary

    /** The complete accepted MIDI notes, never a rendered or flattened audio representation. */
    fun fullAcceptedMidi(): List<MidiNote> = acceptedTracks.flatMap(AcceptedArrangementTrack::notes)
        .sortedWith(compareBy<MidiNote> { it.startTick }.thenBy(MidiNote::channel).thenBy(MidiNote::pitch).thenBy(MidiNote::endTick))

    fun relevantExcerpt(startTick: Long, endTick: Long, maximumNotes: Int = MAX_EXCERPT_NOTES): List<ArrangementExcerptNote> {
        require(startTick >= 0 && endTick > startTick) { "Arrangement excerpt bounds are invalid" }
        require(maximumNotes in 1..MAX_EXCERPT_NOTES) { "Arrangement excerpt note limit is invalid" }
        return acceptedTracks.flatMap { track -> track.notes.filter { it.startTick < endTick && startTick < it.endTick }
            .map { note -> ArrangementExcerptNote(track.role, note.pitch, note.velocity, note.startTick, note.endTick) } }
            .sortedWith(compareBy<ArrangementExcerptNote> { it.startTick }.thenBy(ArrangementExcerptNote::role).thenBy(ArrangementExcerptNote::pitch))
            .take(maximumNotes)
    }

    /** Bounded state for a planner: aggregate facts plus a small exact MIDI excerpt. */
    fun plannerContext(startTick: Long = 0, endTick: Long = timelineEndTick()): ArrangementPlannerContext = ArrangementPlannerContext(
        version = CURRENT_VERSION,
        tracks = acceptedTracks.map { track -> track.summary.copy(onsets = track.summary.onsets.take(MAX_SUMMARY_ONSETS)) },
        excerpt = relevantExcerpt(startTick, endTick)
    )

    fun acceptValidated(role: String, midi: Path): ArrangementState {
        require(role in GENERATED_ROLES) { "Unsupported generated arrangement role '$role'" }
        require(!hasTrack(role)) { "Arrangement state already has an accepted $role track" }
        return copy(acceptedTracks = acceptedTracks + fromMidi(role, midi, ppq))
    }

    private fun timelineEndTick(): Long = acceptedTracks.maxOf { track -> track.notes.maxOfOrNull(MidiNote::endTick) ?: 0L }.coerceAtLeast(1)

    companion object {
        const val CURRENT_VERSION = 1
        const val PIANO = "piano"
        private const val MAX_EXCERPT_NOTES = 64
        private const val MAX_SUMMARY_ONSETS = 32
        private val GENERATED_ROLES = setOf("bass", "drums", "pad", "strings")

        fun fromAcceptedPiano(ppq: Int, notes: List<MidiNote>, sourceSha256: String): ArrangementState = ArrangementState(
            ppq = ppq,
            acceptedTracks = listOf(AcceptedArrangementTrack(PIANO, ppq, sourceSha256, notes.sortedNotes()))
        )

        fun fromMidi(role: String, path: Path, expectedPpq: Int): AcceptedArrangementTrack {
            require(Files.isRegularFile(path)) { "Accepted $role MIDI is missing: $path" }
            val sequence = MidiSystem.getSequence(path.toFile())
            require(sequence.divisionType == javax.sound.midi.Sequence.PPQ && sequence.resolution == expectedPpq) {
                "Accepted $role MIDI does not match arrangement-state PPQ"
            }
            val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
            val notes = mutableListOf<MidiNote>()
            sequence.tracks.flatMapIndexed { trackIndex, track -> (0 until track.size()).map { eventIndex -> IndexedMidiEvent(track[eventIndex], trackIndex, eventIndex) } }
                .sortedWith(compareBy<IndexedMidiEvent> { it.event.tick }.thenBy { priority(it.event.message as? ShortMessage) }.thenBy(IndexedMidiEvent::track).thenBy(IndexedMidiEvent::index))
                .forEach { indexed ->
                    val message = indexed.event.message as? ShortMessage ?: return@forEach
                    val key = message.channel to message.data1
                    when {
                        message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(indexed.event.tick to message.data2)
                        message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                            val start = active[key]?.removeFirstOrNull()
                                ?: throw IllegalArgumentException("Accepted $role MIDI has an unmatched note-off")
                            require(indexed.event.tick > start.first) { "Accepted $role MIDI has a non-positive note duration" }
                            notes += MidiNote(message.channel, message.data1, start.second, start.first, indexed.event.tick)
                        }
                    }
                }
            require(active.values.all { it.isEmpty() }) { "Accepted $role MIDI has an unmatched note-on" }
            return AcceptedArrangementTrack(role, expectedPpq, sha256(path), notes.sortedNotes())
        }

        private fun priority(message: ShortMessage?): Int = if (message?.command == ShortMessage.NOTE_OFF || message?.command == ShortMessage.NOTE_ON && message.data2 == 0) 0 else 1
        private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    }
}

data class AcceptedArrangementTrack(val role: String, val ppq: Int, val sha256: String, val notes: List<MidiNote>) {
    init {
        require(role in setOf("piano", "bass", "drums", "pad", "strings")) { "Unsupported arrangement-state role '$role'" }
        require(ppq > 0 && sha256.matches(Regex("[0-9a-f]{64}"))) { "Arrangement-state track identity is invalid" }
        require(notes.all { it.pitch in 0..127 && it.velocity in 1..127 && it.startTick >= 0 && it.endTick > it.startTick }) { "Arrangement-state MIDI note is invalid" }
    }

    val summary: ArrangementTrackSummary = ArrangementTrackSummary(
        role, notes.size, notes.map(MidiNote::startTick).distinct().sorted(),
        notes.takeIf { it.isNotEmpty() }?.let { it.minOf(MidiNote::pitch)..it.maxOf(MidiNote::pitch) },
        notes.size.toDouble() / maxOf(1L, notes.maxOfOrNull(MidiNote::endTick) ?: 1L)
    )
}

data class ArrangementTrackSummary(val role: String, val noteCount: Int, val onsets: List<Long>, val register: IntRange?, val densityPerTick: Double)
data class ArrangementExcerptNote(val role: String, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)
data class ArrangementPlannerContext(val version: Int, val tracks: List<ArrangementTrackSummary>, val excerpt: List<ArrangementExcerptNote>)

private data class IndexedMidiEvent(val event: MidiEvent, val track: Int, val index: Int)
private fun List<MidiNote>.sortedNotes(): List<MidiNote> = sortedWith(compareBy<MidiNote> { it.startTick }.thenBy(MidiNote::channel).thenBy(MidiNote::pitch).thenBy(MidiNote::endTick))
