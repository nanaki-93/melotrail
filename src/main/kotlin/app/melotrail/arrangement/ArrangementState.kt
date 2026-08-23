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

    /**
     * Exact piano/bass rhythm evidence for the drum generator.  This is MIDI
     * state, not a rendered mix: onsets describe attacks and activity retains
     * held notes so a groove can distinguish a rest from a sustained harmony.
     */
    fun pianoBassRhythmMap(startTick: Long, endTick: Long): ArrangementRhythmMap =
        rhythmMap(startTick, endTick, setOf(PIANO, "bass"))

    /**
     * Register and activity evidence for a potential sustained layer.  It is
     * intentionally derived only from accepted tracks, before the candidate
     * pad itself is admitted to the arrangement state.
     */
    fun ensembleSpaceMap(startTick: Long, endTick: Long): EnsembleSpaceMap {
        require(startTick >= 0 && endTick > startTick) { "Ensemble-space bounds are invalid" }
        // Drums contribute rhythmic density but do not occupy pitched register
        // space, so they must not make a pad rest by themselves.
        val notes = acceptedTracks.filterNot { it.role == "drums" }.flatMap { track -> track.notes.filter { it.startTick < endTick && startTick < it.endTick }
            .map { EnsembleSpaceNote(track.role, it.pitch, maxOf(startTick, it.startTick), minOf(endTick, it.endTick)) } }
        val boundaries = (listOf(startTick, endTick) + notes.flatMap { listOf(it.startTick, it.endTick) }).distinct().sorted()
        val maximumSimultaneousNotes = boundaries.dropLast(1).maxOfOrNull { tick -> notes.count { it.startTick <= tick && tick < it.endTick } } ?: 0
        val piano = notes.filter { it.role == PIANO }
        val bass = notes.filter { it.role == "bass" }
        return EnsembleSpaceMap(
            startTick, endTick, notes.sortedWith(compareBy<EnsembleSpaceNote> { it.startTick }.thenBy { it.role }.thenBy { it.pitch }),
            maximumSimultaneousNotes,
            piano.map(EnsembleSpaceNote::pitch).distinct().sorted(), bass.map(EnsembleSpaceNote::pitch).distinct().sorted()
        )
    }

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

    private fun rhythmMap(startTick: Long, endTick: Long, roles: Set<String>): ArrangementRhythmMap {
        require(startTick >= 0 && endTick > startTick) { "Arrangement rhythm-map bounds are invalid" }
        val tracks = acceptedTracks.filter { it.role in roles }.map { track ->
            val clipped = track.notes.filter { it.startTick < endTick && startTick < it.endTick }
            ArrangementTrackRhythm(
                track.role,
                clipped.filter { it.startTick in startTick until endTick }.map(MidiNote::startTick).distinct().sorted(),
                clipped.map { ArrangementActivityWindow(maxOf(startTick, it.startTick), minOf(endTick, it.endTick)) }.distinct().sortedBy(ArrangementActivityWindow::startTick)
            )
        }
        return ArrangementRhythmMap(startTick, endTick, tracks.sortedBy(ArrangementTrackRhythm::role))
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

data class ArrangementActivityWindow(val startTick: Long, val endTick: Long) {
    init { require(startTick >= 0 && endTick > startTick) { "Arrangement activity window is invalid" } }
    fun contains(tick: Long): Boolean = tick in startTick until endTick
}

data class ArrangementTrackRhythm(val role: String, val onsets: List<Long>, val activity: List<ArrangementActivityWindow>) {
    init { require(onsets == onsets.distinct().sorted() && activity == activity.sortedBy(ArrangementActivityWindow::startTick)) }
    fun hasOnsetNear(tick: Long, toleranceTicks: Long): Boolean = onsets.any { kotlin.math.abs(it - tick) <= toleranceTicks }
    fun isActiveAt(tick: Long): Boolean = activity.any { it.contains(tick) }
}

data class ArrangementRhythmMap(val startTick: Long, val endTick: Long, val tracks: List<ArrangementTrackRhythm>) {
    init { require(startTick >= 0 && endTick > startTick && tracks.map(ArrangementTrackRhythm::role).distinct().size == tracks.size) }
    fun track(role: String): ArrangementTrackRhythm? = tracks.singleOrNull { it.role == role }
    fun hasOnsetNear(tick: Long, toleranceTicks: Long): Boolean = tracks.any { it.hasOnsetNear(tick, toleranceTicks) }
    fun isActiveAt(tick: Long): Boolean = tracks.any { it.isActiveAt(tick) }
}

data class EnsembleSpaceNote(val role: String, val pitch: Int, val startTick: Long, val endTick: Long)

data class EnsembleSpaceMap(
    val startTick: Long,
    val endTick: Long,
    val notes: List<EnsembleSpaceNote>,
    val maximumSimultaneousNotes: Int,
    val pianoPitches: List<Int>,
    val bassPitches: List<Int>
) {
    init { require(startTick >= 0 && endTick > startTick && maximumSimultaneousNotes >= 0) }
    /** Six concurrent accepted notes is intentionally treated as a dense core: pads may rest. */
    val isDense: Boolean get() = maximumSimultaneousNotes >= DENSE_CORE_NOTE_COUNT
    companion object { const val DENSE_CORE_NOTE_COUNT = 6 }
}

private data class IndexedMidiEvent(val event: MidiEvent, val track: Int, val index: Int)
private fun List<MidiNote>.sortedNotes(): List<MidiNote> = sortedWith(compareBy<MidiNote> { it.startTick }.thenBy(MidiNote::channel).thenBy(MidiNote::pitch).thenBy(MidiNote::endTick))
