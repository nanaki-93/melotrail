package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/** Stable identity for a note in one immutable selected MIDI input. */
@Serializable
@JvmInline
value class MelodyNoteId(val value: String) {
    init { require(PATTERN.matches(value)) { "Melody note ID is invalid" } }
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("m-[0-9a-f]{64}")

        fun derive(sourceSha256: String, track: Int, channel: Int, noteOnOrdinal: Int, pitch: Int, startTick: Long, endTick: Long): MelodyNoteId {
            require(HASH.matches(sourceSha256) && track >= 0 && channel in 0..15 && noteOnOrdinal >= 0 && pitch in 0..127 && startTick >= 0 && endTick > startTick) {
                "Melody note identity input is invalid"
            }
            return MelodyNoteId("m-" + digest("melody-note-v1|$sourceSha256|$track|$channel|$noteOnOrdinal|$pitch|$startTick|$endTick"))
        }
    }
}

@Serializable
data class MelodyIdentityNote(
    val id: MelodyNoteId,
    val track: Int,
    val channel: Int,
    val noteOnOrdinal: Int,
    val pitch: Int,
    val velocity: Int,
    val originalStartTick: Long,
    val originalEndTick: Long,
    val phraseId: String,
    val occurrenceId: String? = null
) {
    init {
        require(track >= 0 && channel in 0..15 && noteOnOrdinal >= 0 && pitch in 0..127 && velocity in 1..127 &&
            originalStartTick >= 0 && originalEndTick > originalStartTick && PHRASE.matches(phraseId) && (occurrenceId == null || OCCURRENCE.matches(occurrenceId))) {
            "Melody identity note is invalid"
        }
    }
}

@Serializable
data class MelodyPhrase(val id: String, val noteIds: List<MelodyNoteId>) {
    init { require(PHRASE.matches(id) && noteIds.isNotEmpty() && noteIds.distinct().size == noteIds.size) { "Melody phrase is invalid" } }
}

/** Canonical occurrence bounds are optional for a part that has not been structured yet. */
@Serializable
data class MelodyOccurrenceWindow(val occurrenceId: String, val startTick: Long, val endTick: Long) {
    init { require(OCCURRENCE.matches(occurrenceId) && startTick >= 0 && endTick > startTick) { "Melody occurrence window is invalid" } }
}

/** Current analyzed phrase evidence may be supplied; stale evidence is ignored, never guessed from. */
@Serializable
data class MelodyPhraseEvidence(val version: Int = VERSION, val sourceSha256: String, val phrases: List<List<MelodyNoteId>>) {
    init { require(version == VERSION && HASH.matches(sourceSha256) && phrases.all { it.isNotEmpty() && it.distinct().size == it.size }) { "Melody phrase evidence is invalid" } }
    companion object { const val VERSION = 1 }
}

@Serializable
data class MelodyIdentity(
    val version: Int = VERSION,
    val sourceSha256: String,
    val ppq: Int,
    val canonicalBeatTicks: Long,
    val notes: List<MelodyIdentityNote>,
    val phrases: List<MelodyPhrase>,
    val anchorIds: List<MelodyNoteId>,
    val occurrenceWindows: List<MelodyOccurrenceWindow> = emptyList(),
    val schemaSha256: String = SCHEMA_SHA256
) {
    init {
        require(version == VERSION && HASH.matches(sourceSha256) && ppq in 24..9_600 && canonicalBeatTicks > 0 &&
            schemaSha256 == SCHEMA_SHA256 && notes.size <= MAX_NOTES && notes.map(MelodyIdentityNote::id).distinct().size == notes.size &&
            phrases.map(MelodyPhrase::id).distinct().size == phrases.size && occurrenceWindows.map(MelodyOccurrenceWindow::occurrenceId).distinct().size == occurrenceWindows.size &&
            anchorIds.distinct().size == anchorIds.size && anchorIds.all { id -> notes.any { it.id == id } }) { "Melody identity is invalid" }
        require(phrases.flatMap(MelodyPhrase::noteIds).toSet() == notes.map(MelodyIdentityNote::id).toSet() &&
            phrases.all { phrase -> phrase.noteIds.all { id -> notes.single { it.id == id }.phraseId == phrase.id } }) { "Melody phrase mapping is invalid" }
    }

    fun note(id: MelodyNoteId): MelodyIdentityNote = requireNotNull(notes.singleOrNull { it.id == id }) { "Unknown melody note ID" }
    fun isAnchor(id: MelodyNoteId): Boolean = id in anchorIds

    companion object {
        const val VERSION = 1
        const val MAX_NOTES = 4_000
        val SCHEMA_SHA256: String = digest("melody-identity|v1|source-sha256|track|channel|note-on-ordinal|pitch|start|end|phrase|anchors|occurrences")
    }
}

/** Builds the one identity projection shared by note-mutating stages. */
object MelodyIdentityBuilder {
    fun build(
        selectedInput: Path,
        canonicalBeatTicks: Long,
        occurrenceWindows: List<MelodyOccurrenceWindow> = emptyList(),
        phraseEvidence: MelodyPhraseEvidence? = null
    ): MelodyIdentity {
        require(canonicalBeatTicks > 0) { "Canonical beat must be positive" }
        require(occurrenceWindows.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) { "Melody occurrence windows overlap or are unordered" }
        val sourceSha256 = melodySha256(selectedInput)
        val sequence = try { MidiSystem.getSequence(selectedInput.toFile()) } catch (error: Exception) {
            throw IllegalArgumentException("Selected melody MIDI is malformed", error)
        }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution in 24..9_600) { "Selected melody MIDI must use supported PPQ timing" }
        val raw = collect(sequence, sourceSha256)
        require(raw.size <= MelodyIdentity.MAX_NOTES) { "Selected melody has too many notes" }
        val phraseGroups = currentPhraseGroups(raw.map(RawNote::id), sourceSha256, phraseEvidence)
            ?: fallbackPhraseGroups(raw, canonicalBeatTicks)
        val phraseById = phraseGroups.flatMapIndexed { index, ids -> ids.map { it to "p-${index.toString().padStart(5, '0')}" } }.toMap()
        val notes = raw.map { note ->
            val occurrence = occurrenceWindows.singleOrNull { note.startTick >= it.startTick && note.endTick <= it.endTick }?.occurrenceId
            require(occurrence != null || occurrenceWindows.isEmpty()) { "Melody note crosses or falls outside canonical occurrence bounds" }
            MelodyIdentityNote(note.id, note.track, note.channel, note.noteOnOrdinal, note.pitch, note.velocity, note.startTick, note.endTick, phraseById.getValue(note.id), occurrence)
        }
        val phrases = phraseGroups.mapIndexed { index, ids -> MelodyPhrase("p-${index.toString().padStart(5, '0')}", ids) }
        val anchors = anchors(notes, phrases, canonicalBeatTicks)
        return MelodyIdentity(sourceSha256 = sourceSha256, ppq = sequence.resolution, canonicalBeatTicks = canonicalBeatTicks,
            notes = notes, phrases = phrases, anchorIds = anchors, occurrenceWindows = occurrenceWindows)
    }

    private fun currentPhraseGroups(ids: List<MelodyNoteId>, sourceHash: String, evidence: MelodyPhraseEvidence?): List<List<MelodyNoteId>>? {
        if (evidence == null || evidence.sourceSha256 != sourceHash) return null
        val flattened = evidence.phrases.flatten()
        return evidence.phrases.takeIf { flattened.size == ids.size && flattened.toSet() == ids.toSet() }
    }

    private fun fallbackPhraseGroups(notes: List<RawNote>, beat: Long): List<List<MelodyNoteId>> {
        if (notes.isEmpty()) return emptyList()
        val ordered = notes.sortedWith(RAW_ORDER)
        val groups = mutableListOf<MutableList<MelodyNoteId>>()
        var phraseEnd = Long.MIN_VALUE
        ordered.forEach { note ->
            if (groups.isEmpty() || note.startTick - phraseEnd >= beat) groups.add(mutableListOf())
            groups.last() += note.id
            phraseEnd = maxOf(phraseEnd, note.endTick)
        }
        return groups
    }

    private fun anchors(notes: List<MelodyIdentityNote>, phrases: List<MelodyPhrase>, beat: Long): List<MelodyNoteId> {
        val byId = notes.associateBy(MelodyIdentityNote::id)
        val anchors = linkedSetOf<MelodyNoteId>()
        phrases.forEach { phrase ->
            val ordered = phrase.noteIds.map(byId::getValue).sortedWith(NOTE_ORDER)
            anchors += ordered.first().id
            anchors += ordered.last().id
            ordered.filter { it.originalEndTick - it.originalStartTick >= beat }.forEach { anchors += it.id }
            ordered.filter { it.originalEndTick - it.originalStartTick >= beat / 2 }.let { held ->
                held.minByOrNull(MelodyIdentityNote::pitch)?.let { anchors += it.id }
                held.maxByOrNull(MelodyIdentityNote::pitch)?.let { anchors += it.id }
            }
        }
        return anchors.sortedBy(MelodyNoteId::value)
    }

    private fun collect(sequence: Sequence, sourceSha256: String): List<RawNote> {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Start>>()
        val ordinal = mutableMapOf<Pair<Int, Int>, Int>()
        val result = mutableListOf<RawNote>()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            (0 until track.size()).forEach { eventIndex ->
                val event = track[eventIndex]; val message = event.message as? ShortMessage ?: return@forEach
                val key = Triple(trackIndex, message.channel, message.data1)
                val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
                val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
                when {
                    on -> {
                        val ordinalKey = trackIndex to message.channel
                        val noteOnOrdinal = ordinal.getOrDefault(ordinalKey, 0)
                        ordinal[ordinalKey] = noteOnOrdinal + 1
                        active.getOrPut(key) { ArrayDeque() }.addLast(Start(event, message.data2, noteOnOrdinal))
                    }
                    off -> {
                        val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Selected melody MIDI has an unmatched note-off")
                        require(event.tick > start.event.tick) { "Selected melody MIDI has a non-positive note" }
                        val id = MelodyNoteId.derive(sourceSha256, trackIndex, message.channel, start.ordinal, message.data1, start.event.tick, event.tick)
                        result += RawNote(id, trackIndex, message.channel, start.ordinal, message.data1, start.velocity, start.event.tick, event.tick)
                    }
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Selected melody MIDI has unclosed notes" }
        return result.sortedWith(RAW_ORDER)
    }

    private data class Start(val event: MidiEvent, val velocity: Int, val ordinal: Int)
    private data class RawNote(val id: MelodyNoteId, val track: Int, val channel: Int, val noteOnOrdinal: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)
    private val RAW_ORDER = compareBy<RawNote> { it.startTick }.thenBy { it.track }.thenBy { it.channel }.thenBy { it.noteOnOrdinal }.thenBy { it.pitch }.thenBy { it.endTick }
    private val NOTE_ORDER = compareBy<MelodyIdentityNote> { it.originalStartTick }.thenBy { it.track }.thenBy { it.channel }.thenBy { it.noteOnOrdinal }.thenBy { it.pitch }.thenBy { it.originalEndTick }
}

@Serializable
enum class MidiMutationStage { AI_FIX, ENHANCE, COHESION, FULL_SONG_ENHANCE, HUMANIZATION }
@Serializable
enum class MidiMutationOperation { TIMING, VELOCITY, PITCH, DURATION, ADD, REMOVE }
@Serializable
enum class MidiMutationReasonCode { TIMING_REPAIR, COLLISION_REPAIR, DUPLICATE_REPAIR, RANGE_REPAIR, HARMONY_REPAIR, PHRASE_SHAPING, DENSITY_REDUCTION, TRANSITION_SMOOTHING, HUMANIZATION, NO_OP }
@Serializable
enum class MidiMutationRejectionCode { UNKNOWN_NOTE, ANCHOR_MUTATION, PITCH_DELTA, NOTE_BUDGET, WINDOW_BOUNDS, TIMING_CHANGED, DUPLICATE_OPERATION, INVALID_EVIDENCE }

@Serializable
data class MidiMutationValues(val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long) {
    init { require(channel in 0..15 && pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) { "MIDI mutation values are invalid" } }
}

@Serializable
data class MidiMutationBudget(val originalNoteCount: Int, val changedNotes: Int, val additions: Int, val deletions: Int, val maximumChanges: Int, val maximumAdditions: Int, val maximumDeletions: Int) {
    init { require(originalNoteCount >= 0 && changedNotes >= 0 && additions >= 0 && deletions >= 0 && maximumChanges >= 0 && maximumAdditions >= 0 && maximumDeletions >= 0) { "MIDI mutation budget is invalid" } }
}

@Serializable
data class MidiMutation(
    val operation: MidiMutationOperation,
    val noteId: MelodyNoteId,
    val before: MidiMutationValues? = null,
    val after: MidiMutationValues? = null,
    val reasonCode: MidiMutationReasonCode,
    val stageReasonCode: String? = null
) {
    init {
        val validValues = when (operation) {
            MidiMutationOperation.ADD -> before == null && after != null
            MidiMutationOperation.REMOVE -> before != null && after == null
            else -> before != null && after != null
        }
        require(validValues) { "MIDI mutation before/after evidence is invalid" }
        require(stageReasonCode == null || STAGE_REASON.matches(stageReasonCode)) { "MIDI mutation stage reason is invalid" }
    }
}

@Serializable
data class MidiMutationReport(
    val version: Int = VERSION,
    val inputSha256: String,
    val outputSha256: String?,
    val contextSha256: String,
    val target: String,
    val stage: MidiMutationStage,
    val mutations: List<MidiMutation>,
    val budget: MidiMutationBudget,
    val warnings: List<String> = emptyList(),
    val rejectionSummary: List<MidiMutationRejectionCode> = emptyList()
) {
    fun requireValid() {
        require(version == VERSION && HASH.matches(inputSha256) && (outputSha256 == null || HASH.matches(outputSha256)) && HASH.matches(contextSha256) && TARGET.matches(target) &&
            mutations.map(MidiMutation::noteId).distinct().size == mutations.size && warnings.size <= 16 && warnings.all(::safeDiagnostic) &&
            rejectionSummary.distinct().size == rejectionSummary.size) { "MIDI mutation report is invalid" }
        MidiMutationInvariants.requireBudget(budget)
        MidiMutationInvariants.requireDeterministicOrdering(mutations)
    }
    companion object { const val VERSION = 1 }
}

/** Reusable safety gates for every note-mutating stage. */
object MidiMutationInvariants {
    fun requireAnchorPreservation(identity: MelodyIdentity, mutations: List<MidiMutation>) {
        mutations.forEach { mutation ->
            if (mutation.operation == MidiMutationOperation.ADD) return@forEach
            val original = requireNotNull(identity.notes.singleOrNull { it.id == mutation.noteId }) { "MIDI mutation references an unknown note" }
            if (identity.isAnchor(original.id)) require(mutation.operation != MidiMutationOperation.REMOVE && mutation.after?.pitch == original.pitch) {
                "A melody anchor cannot be deleted or repitched"
            }
        }
    }

    fun requireAllowedPitchDelta(identity: MelodyIdentity, mutations: List<MidiMutation>, maximumSemitones: Int) {
        require(maximumSemitones >= 0)
        mutations.forEach { mutation ->
            if (mutation.operation == MidiMutationOperation.ADD) return@forEach
            val original = requireNotNull(identity.notes.singleOrNull { it.id == mutation.noteId }) { "MIDI mutation references an unknown note" }
            mutation.after?.let { require(kotlin.math.abs(it.pitch - original.pitch) <= maximumSemitones) { "MIDI pitch edit exceeds its allowed delta" } }
        }
    }

    fun requireBudget(budget: MidiMutationBudget) {
        require(budget.changedNotes <= budget.maximumChanges && budget.additions <= budget.maximumAdditions && budget.deletions <= budget.maximumDeletions) { "MIDI mutation exceeds its budget" }
    }

    fun requireOccurrenceWindow(identity: MelodyIdentity, mutation: MidiMutation) {
        val note = requireNotNull(identity.notes.singleOrNull { it.id == mutation.noteId }) { "MIDI mutation references an unknown note" }
        val occurrence = identity.occurrenceWindows.singleOrNull { it.occurrenceId == note.occurrenceId } ?: return
        mutation.after?.let { require(it.startTick >= occurrence.startTick && it.endTick <= occurrence.endTick) { "MIDI mutation escapes its occurrence window" } }
    }

    fun requireTempoMeterPreserved(before: Sequence, after: Sequence) {
        require(before.divisionType == after.divisionType && before.resolution == after.resolution && timing(before) == timing(after)) { "MIDI mutation changed tempo or meter" }
    }

    fun requireDeterministicOrdering(mutations: List<MidiMutation>) {
        val ordered = mutations.sortedWith(compareBy<MidiMutation> { it.noteId.value }.thenBy { it.operation.ordinal })
        require(mutations == ordered) { "MIDI mutation evidence is not deterministically ordered" }
    }

    private fun timing(sequence: Sequence): List<String> = sequence.tracks.flatMap { track ->
        (0 until track.size()).mapNotNull { index -> (track[index].message as? MetaMessage)?.takeIf { it.type == 0x51 || it.type == 0x58 }?.let { "${it.type}:${track[index].tick}:${it.data.joinToString(",")}" } }
    }.sorted()
}

private val HASH = Regex("[0-9a-f]{64}")
private val PHRASE = Regex("p-[0-9]{5}")
private val OCCURRENCE = Regex("[A-Za-z0-9_-]{1,80}")
private val TARGET = Regex("[A-Za-z0-9_-]{1,80}")
private val STAGE_REASON = Regex("[a-z0-9_-]{1,64}")
private fun safeDiagnostic(value: String): Boolean = value.length in 1..160 && value.none { it.isISOControl() } && '/' !in value && '\\' !in value
private fun melodySha256(path: Path): String = Files.newInputStream(path).use { input ->
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    generateSequence { input.read(buffer).takeIf { it > 0 } }.forEach { digest.update(buffer, 0, it) }
    digest.digest().joinToString("") { "%02x".format(it) }
}
private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
