package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.ArrayDeque
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** Identifies one source MIDI event without exposing an absolute project path. */
@Serializable
data class MelodyPreparationEventIdentity(
    val trackIndex: Int,
    val eventIndex: Int,
    val tick: Long,
    val kind: String,
    val channel: Int,
    val data1: Int,
    val data2: Int
) {
    init {
        require(trackIndex >= 0 && eventIndex >= 0 && tick >= 0 && channel in 0..15 && data1 in 0..127 && data2 in 0..127) {
            "Melody-preparation event identity is invalid"
        }
    }

    val id: String get() = "t$trackIndex-e$eventIndex"
}

/** One paired non-percussion source note after per-channel controller materialization. */
@Serializable
data class MelodyPreparationSourceNote(
    val id: String,
    val noteOn: MelodyPreparationEventIdentity,
    val noteOff: MelodyPreparationEventIdentity? = null,
    val effectiveRelease: MelodyPreparationEventIdentity? = null,
    val pitch: Int,
    val channel: Int,
    val velocity: Int,
    val confidence: Double? = null,
    val startTick: Long,
    val writtenEndTick: Long? = null,
    val effectiveEndTick: Long? = null,
    val releaseKind: MelodyPreparationReleaseKind? = null
) {
    init {
        require(id.isNotBlank() && pitch in 0..127 && channel in 0..15 && velocity in 1..127 && startTick >= 0) {
            "Melody-preparation source note is invalid"
        }
        require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0) { "Melody-preparation confidence is invalid" }
        require((writtenEndTick == null) == (noteOff == null) && (effectiveEndTick == null) == (releaseKind == null)) {
            "Melody-preparation release evidence is incomplete"
        }
        writtenEndTick?.let { require(it >= startTick) { "Melody-preparation written note interval is invalid" } }
        effectiveEndTick?.let { require(it >= startTick) { "Melody-preparation effective note interval is invalid" } }
    }
}

/** Explains how a sounding source note ended after controller state was applied. */
@Serializable
enum class MelodyPreparationReleaseKind { NOTE_OFF, PEDAL_UP, ALL_NOTES_OFF, ALL_SOUND_OFF, RESET_ALL_CONTROLLERS, END_OF_FILE }

/** Records every source controller with overlap-relevant semantics on its own MIDI channel. */
@Serializable
data class MelodyPreparationControllerEvent(
    val source: MelodyPreparationEventIdentity,
    val controller: Int,
    val value: Int,
    val action: MelodyPreparationControllerAction
) {
    init { require(controller in 0..127 && value in 0..127) { "Melody-preparation controller event is invalid" } }
}

/** Deterministic controller interpretation used before melody-overlap decisions. */
@Serializable
enum class MelodyPreparationControllerAction {
    SUSTAIN_DOWN, SUSTAIN_UP, SUSTAIN_REPEATED, ALL_SOUND_OFF, ALL_NOTES_OFF, RESET_ALL_CONTROLLERS, IGNORED, IGNORED_PERCUSSION
}

/** One reason-coded decision taken while reducing source material to one melodic line. */
@Serializable
data class MelodyPreparationDecision(
    val sourceNoteId: String,
    val kind: MelodyPreparationDecisionKind,
    val conflictingSourceNoteId: String? = null,
    val outputStartTick: Long? = null,
    val outputEndTick: Long? = null
) {
    init {
        require(sourceNoteId.isNotBlank() && (outputStartTick == null) == (outputEndTick == null)) {
            "Melody-preparation decision is incomplete"
        }
        if (outputStartTick != null) require(outputStartTick >= 0 && requireNotNull(outputEndTick) > outputStartTick) {
            "Melody-preparation output interval is invalid"
        }
    }
}

/** Stable categories for selection, loss, deduplication, trimming, and safe blocking. */
@Serializable
enum class MelodyPreparationDecisionKind { SELECTED, REMOVED_OVERLAP, DEDUPLICATED, TRIMMED_START, TRIMMED_END, AMBIGUOUS }

/** A source-integrity or unresolved-musical issue that prevents candidate publication. */
@Serializable
data class MelodyPreparationIssue(
    val kind: MelodyPreparationIssueKind,
    val sourceEvent: MelodyPreparationEventIdentity? = null,
    val sourceNoteIds: List<String> = emptyList()
) {
    init {
        require(kind == MelodyPreparationIssueKind.NO_MELODIC_NOTES || sourceEvent != null || sourceNoteIds.isNotEmpty()) {
            "Melody-preparation issue requires source evidence"
        }
    }
}

/** Inputs that cannot safely produce a monophonic melody candidate. */
@Serializable
enum class MelodyPreparationIssueKind { NO_MELODIC_NOTES, UNMATCHED_NOTE_OFF, UNMATCHED_NOTE_ON, NON_POSITIVE_INTERVAL, AMBIGUOUS_OVERLAP }

/** One final note published on the prepared one-track melody candidate. */
@Serializable
data class PreparedMelodyNote(
    val sourceNoteId: String,
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int
) {
    init {
        require(sourceNoteId.isNotBlank() && startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 1..127) {
            "Prepared melody note is invalid"
        }
    }
}

/** Immutable input or output evidence bound into the preparation report. */
@Serializable
data class MelodyPreparationArtifactReference(val path: String, val sha256: String, val ppq: Int, val noteCount: Int) {
    init {
        require(path.isNotBlank() && SHA256.matches(sha256) && ppq > 0 && noteCount >= 0) { "Melody-preparation artifact reference is invalid" }
    }
}

/** Whether a source input yielded a publishable candidate or a durable blocking report. */
@Serializable
enum class MelodyPreparationStatus { COMPLETED, BLOCKED }

/** Hash-bound, replayable evidence for deterministic monophonic source reduction. */
@Serializable
data class MonophonicMelodyPreparationReport(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val partId: String,
    val status: MelodyPreparationStatus,
    val input: MelodyPreparationArtifactReference,
    val output: MelodyPreparationArtifactReference? = null,
    val sourceNotes: List<MelodyPreparationSourceNote>,
    val controllers: List<MelodyPreparationControllerEvent>,
    val decisions: List<MelodyPreparationDecision>,
    val issues: List<MelodyPreparationIssue>,
    val maximumEffectivePolyphony: Int,
    val maximumOutputPolyphony: Int,
    val outputNotes: List<PreparedMelodyNote>
) {
    fun requireValid() {
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION && PART_ID.matches(partId)) {
            "Melody-preparation report version or part is invalid"
        }
        require(sourceNotes.map(MelodyPreparationSourceNote::id).distinct().size == sourceNotes.size) { "Melody-preparation source-note IDs are duplicated" }
        require(controllers == controllers.sortedWith(CONTROLLER_ORDER) && decisions == decisions.sortedWith(DECISION_ORDER)) {
            "Melody-preparation evidence ordering is invalid"
        }
        require(maximumEffectivePolyphony >= 0 && maximumOutputPolyphony in 0..1) { "Melody-preparation polyphony evidence is invalid" }
        require(outputNotes == outputNotes.sortedWith(OUTPUT_NOTE_ORDER) && outputNotes.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) {
            "Prepared melody is not globally monophonic"
        }
        require(outputNotes.map(PreparedMelodyNote::sourceNoteId).distinct().size == outputNotes.size) { "Prepared melody contains duplicate source-note decisions" }
        when (status) {
            MelodyPreparationStatus.COMPLETED -> {
                require(output != null && output.noteCount == outputNotes.size && outputNotes.isNotEmpty() && issues.isEmpty() && maximumOutputPolyphony == 1) {
                    "Completed melody-preparation report is incomplete"
                }
            }
            MelodyPreparationStatus.BLOCKED -> require(output == null && outputNotes.isEmpty() && issues.isNotEmpty() && maximumOutputPolyphony == 0) {
                "Blocked melody-preparation report is invalid"
            }
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PROCESSOR_VERSION = "1"
        private val PART_ID = Regex("[A-Za-z0-9_-]{1,80}")
        private val CONTROLLER_ORDER = compareBy<MelodyPreparationControllerEvent> { it.source.tick }.thenBy { it.source.trackIndex }.thenBy { it.source.eventIndex }
        private val DECISION_ORDER = compareBy<MelodyPreparationDecision> { it.outputStartTick ?: Long.MAX_VALUE }.thenBy { it.sourceNoteId }.thenBy { it.kind.name }
        private val OUTPUT_NOTE_ORDER = compareBy<PreparedMelodyNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.sourceNoteId }
    }
}

/** Resolves optional transcription confidence from a source note-on identity. */
fun interface TranscriptionConfidenceProvider {
    fun confidence(noteOn: MelodyPreparationEventIdentity): Double?
}

/** One immutable prepared melody candidate and its exact preparation report. */
data class MonophonicMelodyPreparationArtifact(
    val midi: MelodyPreparationArtifactReference,
    val report: WorkflowArtifactReference,
    val preparation: MonophonicMelodyPreparationReport
)

/** Content-addressed paths keep a prepared candidate from overwriting an earlier known-good MIDI file. */
object MonophonicMelodyPreparationArtifactPaths {
    fun midi(partId: String, contextSha256: String): String = "midi/prepared/${part(partId)}/${context(contextSha256)}.mid"
    fun report(partId: String, contextSha256: String): String = "analysis/melody-preparation/${part(partId)}/${context(contextSha256)}.json"

    private fun part(value: String): String {
        require(PART_ID.matches(value)) { "Melody-preparation part ID is invalid" }
        return value
    }

    private fun context(value: String): String {
        require(SHA256.matches(value)) { "Melody-preparation context fingerprint is invalid" }
        return value
    }

    private val PART_ID = Regex("[A-Za-z0-9_-]{1,80}")
}

/**
 * Materializes controller-aware sounding intervals, resolves only deterministic
 * overlap choices, and publishes a new one-track melodic candidate. It never
 * modifies the selected source MIDI or any earlier evidence.
 */
class MidiMonophonicMelodyPreparer(
    private val confidenceProvider: TranscriptionConfidenceProvider = TranscriptionConfidenceProvider { null }
) {
    /** Prepare or verify the content-addressed monophonic candidate for one selected MIDI input. */
    fun prepare(root: Path, partId: String, input: MelodyPreparationArtifactReference): MonophonicMelodyPreparationArtifact {
        require(PART_ID.matches(partId) && input.path.isNotBlank()) { "Melody preparation requires a valid part and input" }
        val projectRoot = root.toAbsolutePath().normalize()
        val source = projectRoot.resolve(input.path).normalize()
        require(source.startsWith(projectRoot) && Files.isRegularFile(source) && source.toRealPath().startsWith(projectRoot.toRealPath())) {
            "Melody-preparation input is missing or escapes the project"
        }
        require(sha256(source) == input.sha256) { "Melody-preparation input changed after selection" }
        val sequence = try { MidiSystem.getSequence(source.toFile()) }
        catch (error: Exception) { throw IllegalArgumentException("Melody-preparation input is malformed", error) }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == input.ppq) { "Melody-preparation input timing is invalid" }

        val context = sha256Text("${MonophonicMelodyPreparationReport.PROCESSOR_VERSION}|$partId|${input.sha256}")
        val outputRelative = MonophonicMelodyPreparationArtifactPaths.midi(partId, context)
        val reportRelative = MonophonicMelodyPreparationArtifactPaths.report(partId, context)
        val outputPath = projectRoot.resolve(outputRelative).normalize()
        val reportPath = projectRoot.resolve(reportRelative).normalize()
        require(outputPath.startsWith(projectRoot) && reportPath.startsWith(projectRoot)) { "Melody-preparation output escapes the project" }

        val parsed = parse(sequence)
        val reduction = if (parsed.issues.isEmpty()) reduce(parsed.notes) else Reduction(emptyList(), emptyList(), parsed.issues)
        val issues = (parsed.issues + reduction.issues + if (parsed.notes.isEmpty()) listOf(MelodyPreparationIssue(MelodyPreparationIssueKind.NO_MELODIC_NOTES)) else emptyList())
            .distinct().sortedWith(ISSUE_ORDER)
        if (issues.isNotEmpty()) {
            val blocked = report(partId, MelodyPreparationStatus.BLOCKED, input, null, parsed.notes, parsed.controllers, reduction.decisions, issues,
                maximumPolyphony(parsed.notes), 0, emptyList())
            publishText(reportPath, JSON.encodeToString(MonophonicMelodyPreparationReport.serializer(), blocked))
            throw IllegalArgumentException("Melody preparation is blocked; inspect $reportRelative")
        }

        val preparedNotes = reduction.notes.map { candidate -> PreparedMelodyNote(candidate.note.id, candidate.startTick, candidate.endTick, candidate.note.pitch, candidate.note.velocity) }
        require(preparedNotes.isNotEmpty()) { "Melody preparation produced no note-bearing candidate" }
        Files.createDirectories(requireNotNull(outputPath.parent))
        val temporary = Files.createTempFile(outputPath.parent, ".melody-preparation-", ".mid")
        try {
            write(sequence, temporary, preparedNotes)
            val output = MelodyPreparationArtifactReference(outputRelative, sha256(temporary), sequence.resolution, preparedNotes.size)
            val completed = report(partId, MelodyPreparationStatus.COMPLETED, input, output, parsed.notes, parsed.controllers, reduction.decisions, emptyList(),
                maximumPolyphony(parsed.notes), 1, preparedNotes)
            completed.requireValid()
            publishOrVerify(temporary, outputPath, output.sha256, "prepared MIDI")
            publishText(reportPath, JSON.encodeToString(MonophonicMelodyPreparationReport.serializer(), completed))
            return MonophonicMelodyPreparationArtifact(output, WorkflowArtifactReference(reportRelative, sha256(reportPath)), completed)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Parse note pairing and relevant control changes without allowing controller state to cross channels. */
    private fun parse(sequence: Sequence): Parsed {
        val notes = mutableListOf<MutableSourceNote>()
        val controllers = mutableListOf<MelodyPreparationControllerEvent>()
        val issues = mutableListOf<MelodyPreparationIssue>()
        val channels = Array(16) { ChannelState() }
        events(sequence).forEach { event ->
            val message = event.message ?: return@forEach
            val channel = message.channel
            when {
                message.command == ShortMessage.CONTROL_CHANGE -> {
                    val action = controllerAction(channels[channel], message, channel == DRUM_CHANNEL)
                    controllers += MelodyPreparationControllerEvent(event.identity, message.data1, message.data2, action)
                    if (channel != DRUM_CHANNEL) applyController(channels[channel], message, event.identity, issues)
                }
                isNoteOn(message) && channel != DRUM_CHANNEL -> {
                    val confidence = confidenceProvider.confidence(event.identity)
                    require(confidence == null || confidence.isFinite() && confidence in 0.0..1.0) { "Transcription confidence is invalid" }
                    val note = MutableSourceNote(event.identity.id, event.identity, message.data1, channel, message.data2, confidence)
                    notes += note
                    channels[channel].pressed.getOrPut(message.data1) { ArrayDeque() }.addLast(note)
                }
                isNoteOff(message) && channel != DRUM_CHANNEL -> {
                    val queue = channels[channel].pressed[message.data1]
                    val note = if (queue.isNullOrEmpty()) null else queue.removeFirst()
                    if (queue?.isEmpty() == true) channels[channel].pressed.remove(message.data1)
                    if (note == null) issues += MelodyPreparationIssue(MelodyPreparationIssueKind.UNMATCHED_NOTE_OFF, event.identity)
                    else releaseWritten(note, event.identity, channels[channel], issues)
                }
            }
        }
        channels.forEach { state ->
            state.pressed.values.flatten().forEach { note -> issues += MelodyPreparationIssue(MelodyPreparationIssueKind.UNMATCHED_NOTE_ON, note.noteOn) }
            state.sustained.forEach { note -> release(note, sequence.tickLength, MelodyPreparationReleaseKind.END_OF_FILE, null, issues) }
        }
        return Parsed(notes.map(MutableSourceNote::freeze).sortedWith(SOURCE_NOTE_ORDER), controllers.sortedWith(CONTROLLER_ORDER), issues.sortedWith(ISSUE_ORDER))
    }

    /** Apply sustain, all-notes, all-sound, and reset semantics strictly to one MIDI channel. */
    private fun applyController(state: ChannelState, message: ShortMessage, identity: MelodyPreparationEventIdentity, issues: MutableList<MelodyPreparationIssue>) {
        when (message.data1) {
            SUSTAIN -> if (message.data2 < 64 && state.sustain) {
                state.sustain = false
                state.sustained.toList().forEach { note -> release(note, identity.tick, MelodyPreparationReleaseKind.PEDAL_UP, identity, issues) }
                state.sustained.clear()
            } else if (message.data2 >= 64) state.sustain = true
            ALL_SOUND_OFF -> {
                state.pressed.values.flatten().forEach { note -> release(note, identity.tick, MelodyPreparationReleaseKind.ALL_SOUND_OFF, identity, issues) }
                state.pressed.clear()
                state.sustained.forEach { note -> release(note, identity.tick, MelodyPreparationReleaseKind.ALL_SOUND_OFF, identity, issues) }
                state.sustained.clear()
            }
            ALL_NOTES_OFF -> {
                val active = state.pressed.values.flatten()
                state.pressed.clear()
                active.forEach { note ->
                    note.noteOff = identity
                    note.writtenEndTick = identity.tick
                    if (state.sustain) state.sustained += note
                    else release(note, identity.tick, MelodyPreparationReleaseKind.ALL_NOTES_OFF, identity, issues)
                }
            }
            RESET_ALL_CONTROLLERS -> {
                state.sustain = false
                state.sustained.toList().forEach { note -> release(note, identity.tick, MelodyPreparationReleaseKind.RESET_ALL_CONTROLLERS, identity, issues) }
                state.sustained.clear()
            }
        }
    }

    /** Close an ordinary key release, deferring its sounding end while sustain is down. */
    private fun releaseWritten(note: MutableSourceNote, identity: MelodyPreparationEventIdentity, state: ChannelState, issues: MutableList<MelodyPreparationIssue>) {
        note.noteOff = identity
        note.writtenEndTick = identity.tick
        if (state.sustain) state.sustained += note
        else release(note, identity.tick, MelodyPreparationReleaseKind.NOTE_OFF, identity, issues)
    }

    /** Materialize a positive effective sounding interval or retain an explicit blocking issue. */
    private fun release(note: MutableSourceNote, endTick: Long, kind: MelodyPreparationReleaseKind, identity: MelodyPreparationEventIdentity?, issues: MutableList<MelodyPreparationIssue>) {
        if (endTick <= note.startTick) {
            issues += MelodyPreparationIssue(MelodyPreparationIssueKind.NON_POSITIVE_INTERVAL, identity ?: note.noteOn, listOf(note.id))
            return
        }
        note.effectiveEndTick = endTick
        note.releaseKind = kind
        note.effectiveRelease = identity
    }

    /** Reduce effective intervals to one line, blocking ties that remain musically indistinguishable. */
    private fun reduce(sourceNotes: List<MelodyPreparationSourceNote>): Reduction {
        val decisions = mutableListOf<MelodyPreparationDecision>()
        val issues = mutableListOf<MelodyPreparationIssue>()
        val candidates = sourceNotes.mapNotNull { note -> note.effectiveEndTick?.let { Candidate(note, note.startTick, it) } }
        val deduplicated = candidates.groupBy { Triple(it.note.pitch, it.startTick, it.endTick) }.values.flatMap { group ->
            val winner = group.maxWithOrNull(candidateComparator(null)) ?: error("Missing melody candidate")
            group.filter { it != winner }.forEach { loser -> decisions += MelodyPreparationDecision(loser.note.id, MelodyPreparationDecisionKind.DEDUPLICATED, winner.note.id) }
            listOf(winner)
        }.sortedWith(CANDIDATE_ORDER)
        val selected = mutableListOf<Candidate>()
        deduplicated.forEach candidateLoop@{ candidate ->
            var current = candidate
            while (true) {
                val previous = selected.lastOrNull()
                if (previous == null || current.startTick >= previous.endTick) {
                    decisions += MelodyPreparationDecision(current.note.id, MelodyPreparationDecisionKind.SELECTED, outputStartTick = current.startTick, outputEndTick = current.endTick)
                    selected += current
                    return@candidateLoop
                }
                val beforePrevious = selected.getOrNull(selected.lastIndex - 1)
                val preference = candidateComparator(beforePrevious).compare(current, previous)
                if (preference == 0) {
                    decisions += MelodyPreparationDecision(current.note.id, MelodyPreparationDecisionKind.AMBIGUOUS, previous.note.id)
                    decisions += MelodyPreparationDecision(previous.note.id, MelodyPreparationDecisionKind.AMBIGUOUS, current.note.id)
                    issues += MelodyPreparationIssue(MelodyPreparationIssueKind.AMBIGUOUS_OVERLAP, sourceNoteIds = listOf(previous.note.id, current.note.id).sorted())
                    return@candidateLoop
                }
                if (preference > 0) {
                    if (previous.startTick < current.startTick) {
                        val trimmed = previous.copy(endTick = current.startTick)
                        selected[selected.lastIndex] = trimmed
                        decisions += MelodyPreparationDecision(previous.note.id, MelodyPreparationDecisionKind.TRIMMED_END, current.note.id, trimmed.startTick, trimmed.endTick)
                    } else {
                        selected.removeAt(selected.lastIndex)
                        decisions += MelodyPreparationDecision(previous.note.id, MelodyPreparationDecisionKind.REMOVED_OVERLAP, current.note.id)
                    }
                    continue
                }
                if (current.endTick > previous.endTick) {
                    current = current.copy(startTick = previous.endTick)
                    decisions += MelodyPreparationDecision(current.note.id, MelodyPreparationDecisionKind.TRIMMED_START, previous.note.id, current.startTick, current.endTick)
                    continue
                }
                decisions += MelodyPreparationDecision(current.note.id, MelodyPreparationDecisionKind.REMOVED_OVERLAP, previous.note.id)
                return@candidateLoop
            }
        }
        return Reduction(selected.sortedWith(CANDIDATE_ORDER), decisions.sortedWith(DECISION_ORDER), issues.sortedWith(ISSUE_ORDER))
    }

    /** Give confidence precedence, then preserve local contour, velocity, and duration; exact ties block. */
    private fun candidateComparator(previous: Candidate?): Comparator<Candidate> = Comparator { left, right ->
        val confidence = (left.note.confidence ?: -1.0).compareTo(right.note.confidence ?: -1.0)
        if (confidence != 0) return@Comparator confidence
        if (previous != null) {
            val continuity = abs(right.note.pitch - previous.note.pitch).compareTo(abs(left.note.pitch - previous.note.pitch))
            if (continuity != 0) return@Comparator continuity
        }
        val velocity = left.note.velocity.compareTo(right.note.velocity)
        if (velocity != 0) return@Comparator velocity
        (left.endTick - left.startTick).compareTo(right.endTick - right.startTick)
    }

    /** Write only the deterministic single melody track, explicitly dropping all source controller state. */
    private fun write(input: Sequence, target: Path, notes: List<PreparedMelodyNote>) {
        val output = Sequence(Sequence.PPQ, input.resolution)
        val track = output.createTrack()
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, MELODY_CHANNEL, note.pitch, note.velocity), note.startTick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, MELODY_CHANNEL, note.pitch, 0), note.endTick))
        }
        track.add(MidiEvent(MetaMessage().also { it.setMessage(END_OF_TRACK, byteArrayOf(), 0) }, input.tickLength))
        MidiSystem.write(output, 1, target.toFile())
        val reparsed = MidiSystem.getSequence(target.toFile())
        require(reparsed.resolution == input.resolution && reparsed.tickLength == input.tickLength && noteEvents(reparsed).size == notes.size * 2) {
            "Prepared melody output violates its timing or note-count contract"
        }
    }

    /** Bind report-only fields in one place so completed and blocked forms share the same validation. */
    private fun report(
        partId: String,
        status: MelodyPreparationStatus,
        input: MelodyPreparationArtifactReference,
        output: MelodyPreparationArtifactReference?,
        notes: List<MelodyPreparationSourceNote>,
        controllers: List<MelodyPreparationControllerEvent>,
        decisions: List<MelodyPreparationDecision>,
        issues: List<MelodyPreparationIssue>,
        sourcePolyphony: Int,
        outputPolyphony: Int,
        outputNotes: List<PreparedMelodyNote>
    ): MonophonicMelodyPreparationReport = MonophonicMelodyPreparationReport(
        partId = partId, status = status, input = input, output = output, sourceNotes = notes, controllers = controllers,
        decisions = decisions, issues = issues, maximumEffectivePolyphony = sourcePolyphony, maximumOutputPolyphony = outputPolyphony, outputNotes = outputNotes
    ).also(MonophonicMelodyPreparationReport::requireValid)

    /** Count cross-pitch and cross-channel sounding intervals as one global melody-polyphony measurement. */
    private fun maximumPolyphony(notes: List<MelodyPreparationSourceNote>): Int {
        val points = notes.mapNotNull { note -> note.effectiveEndTick?.let { end -> listOf(note.startTick to 1, end to -1) } }.flatten()
            .sortedWith(compareBy<Pair<Long, Int>> { it.first }.thenBy { it.second })
        var active = 0
        var maximum = 0
        points.forEach { (_, delta) -> active += delta; maximum = maxOf(maximum, active) }
        return maximum
    }

    /** Keep every event identity deterministic across tracks and equal-tick controller/note ordering. */
    private fun events(sequence: Sequence): List<SourceEvent> = sequence.tracks.flatMapIndexed { trackIndex, track ->
        (0 until track.size()).mapNotNull { eventIndex ->
            val event = track[eventIndex]
            val message = event.message as? ShortMessage ?: return@mapNotNull null
            SourceEvent(event, message, identity(trackIndex, eventIndex, event.tick, message))
        }
    }.sortedWith(compareBy<SourceEvent> { it.event.tick }.thenBy { messagePriority(it.message) }.thenBy { it.identity.trackIndex }.thenBy { it.identity.eventIndex })

    private fun identity(trackIndex: Int, eventIndex: Int, tick: Long, message: ShortMessage): MelodyPreparationEventIdentity =
        MelodyPreparationEventIdentity(trackIndex, eventIndex, tick, messageKind(message), message.channel, message.data1, message.data2)

    private fun messageKind(message: ShortMessage): String = when {
        isNoteOn(message) -> "note-on"
        isNoteOff(message) -> "note-off"
        message.command == ShortMessage.CONTROL_CHANGE -> "control-change"
        else -> "short-${message.command}"
    }

    private fun messagePriority(message: ShortMessage): Int = when {
        message.command == ShortMessage.CONTROL_CHANGE && message.data1 == ALL_SOUND_OFF -> 0
        message.command == ShortMessage.CONTROL_CHANGE && message.data1 == ALL_NOTES_OFF -> 1
        message.command == ShortMessage.CONTROL_CHANGE && message.data1 == RESET_ALL_CONTROLLERS -> 2
        message.command == ShortMessage.CONTROL_CHANGE && message.data1 == SUSTAIN -> 3
        isNoteOff(message) -> 4
        isNoteOn(message) -> 5
        else -> 6
    }

    private fun controllerAction(state: ChannelState, message: ShortMessage, percussion: Boolean): MelodyPreparationControllerAction = when {
        percussion -> MelodyPreparationControllerAction.IGNORED_PERCUSSION
        message.data1 == SUSTAIN && message.data2 >= 64 && !state.sustain -> MelodyPreparationControllerAction.SUSTAIN_DOWN
        message.data1 == SUSTAIN && message.data2 < 64 && state.sustain -> MelodyPreparationControllerAction.SUSTAIN_UP
        message.data1 == SUSTAIN -> MelodyPreparationControllerAction.SUSTAIN_REPEATED
        message.data1 == ALL_SOUND_OFF -> MelodyPreparationControllerAction.ALL_SOUND_OFF
        message.data1 == ALL_NOTES_OFF -> MelodyPreparationControllerAction.ALL_NOTES_OFF
        message.data1 == RESET_ALL_CONTROLLERS -> MelodyPreparationControllerAction.RESET_ALL_CONTROLLERS
        else -> MelodyPreparationControllerAction.IGNORED
    }

    private fun isNoteOn(message: ShortMessage): Boolean = message.command == ShortMessage.NOTE_ON && message.data2 > 0
    private fun isNoteOff(message: ShortMessage): Boolean = message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0
    private fun noteEvents(sequence: Sequence): List<MidiEvent> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .filter { event -> (event.message as? ShortMessage)?.let(::isNoteOn) == true || (event.message as? ShortMessage)?.let(::isNoteOff) == true }

    private fun publishOrVerify(temporary: Path, target: Path, expectedSha256: String, label: String) {
        if (Files.exists(target)) {
            require(Files.isRegularFile(target) && sha256(target) == expectedSha256) { "Existing $label differs; preserving the known-good candidate" }
            return
        }
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
    }

    private fun publishText(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent))
        if (Files.exists(path)) {
            require(Files.readString(path) == text) { "Existing melody-preparation report differs; preserving the known-good evidence" }
            return
        }
        val temporary = Files.createTempFile(path.parent, ".melody-preparation-", ".json")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun sha256Text(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private data class SourceEvent(val event: MidiEvent, val message: ShortMessage, val identity: MelodyPreparationEventIdentity)
    private data class ChannelState(val pressed: MutableMap<Int, ArrayDeque<MutableSourceNote>> = mutableMapOf(), val sustained: MutableList<MutableSourceNote> = mutableListOf(), var sustain: Boolean = false)
    private data class MutableSourceNote(
        val id: String, val noteOn: MelodyPreparationEventIdentity, val pitch: Int, val channel: Int, val velocity: Int, val confidence: Double?,
        var noteOff: MelodyPreparationEventIdentity? = null, var effectiveRelease: MelodyPreparationEventIdentity? = null,
        var writtenEndTick: Long? = null, var effectiveEndTick: Long? = null, var releaseKind: MelodyPreparationReleaseKind? = null
    ) {
        val startTick: Long get() = noteOn.tick
        fun freeze(): MelodyPreparationSourceNote = MelodyPreparationSourceNote(id, noteOn, noteOff, effectiveRelease, pitch, channel, velocity, confidence,
            startTick, writtenEndTick, effectiveEndTick, releaseKind)
    }
    private data class Parsed(val notes: List<MelodyPreparationSourceNote>, val controllers: List<MelodyPreparationControllerEvent>, val issues: List<MelodyPreparationIssue>)
    private data class Candidate(val note: MelodyPreparationSourceNote, val startTick: Long, val endTick: Long)
    private data class Reduction(val notes: List<Candidate>, val decisions: List<MelodyPreparationDecision>, val issues: List<MelodyPreparationIssue>)

    private companion object {
        const val DRUM_CHANNEL = 9
        const val MELODY_CHANNEL = 0
        const val SUSTAIN = 64
        const val ALL_SOUND_OFF = 120
        const val RESET_ALL_CONTROLLERS = 121
        const val ALL_NOTES_OFF = 123
        const val END_OF_TRACK = 0x2f
        val PART_ID = Regex("[A-Za-z0-9_-]{1,80}")
        val SOURCE_NOTE_ORDER = compareBy<MelodyPreparationSourceNote> { it.startTick }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.id }
        val CONTROLLER_ORDER = compareBy<MelodyPreparationControllerEvent> { it.source.tick }.thenBy { it.source.trackIndex }.thenBy { it.source.eventIndex }
        val DECISION_ORDER = compareBy<MelodyPreparationDecision> { it.outputStartTick ?: Long.MAX_VALUE }.thenBy { it.sourceNoteId }.thenBy { it.kind.name }
        val ISSUE_ORDER = compareBy<MelodyPreparationIssue> { it.kind.name }.thenBy { it.sourceEvent?.tick ?: Long.MAX_VALUE }.thenBy { it.sourceNoteIds.joinToString("|") }
        val CANDIDATE_ORDER = compareBy<Candidate> { it.startTick }.thenBy { it.endTick }.thenBy { it.note.pitch }.thenBy { it.note.id }
        val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false; prettyPrint = true }
    }
}

private val SHA256 = Regex("[0-9a-f]{64}")
