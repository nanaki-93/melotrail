package app.melotrail.arrangement

import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalAuthorityDiagnostic
import app.melotrail.application.MusicalOccurrence
import app.melotrail.application.PartRepairProjection
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** Path-free, bounded evidence supplied to the optional local AI advisor. */
@Serializable
enum class MidiAiFixContextScope {
    /** The part is placed in the saved song structure and has declared harmony. */
    DECLARED_SONG,
    /** The part is being repaired before song structure exists; musical rewrites are disabled. */
    PART_LOCAL
}

/** Path-free, bounded evidence supplied to the optional local AI advisor. */
@Serializable
data class MidiAiFixInput(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    /** Hash of the selected corrected MIDI. This is not a cleaned-MIDI fallback. */
    val selectedInputSha256: String,
    val inputHash: String,
    val ppq: Int,
    /** PPQ used by the canonical occurrence and harmonic timelines. */
    val harmonicPpq: Int,
    /** Version and hash of the declared musical projection supplied to the advisor. */
    val contextSchemaVersion: Int,
    val contextSha256: String,
    /** Whether this repair has song-level declared harmony available. */
    val contextScope: MidiAiFixContextScope = MidiAiFixContextScope.DECLARED_SONG,
    /** Declared settings are authoritative; analysis is diagnostic only. */
    val declaredKey: MusicalKey? = null,
    val declaredTempo: Tempo? = null,
    val declaredMeter: TimeSignature? = null,
    val occurrenceTimeline: List<MusicalOccurrence> = emptyList(),
    val harmonicTimeline: List<HarmonicTimelineEntry> = emptyList(),
    val analyzedObservations: List<MusicalAuthorityDiagnostic> = emptyList(),
    val melodyIdentity: MelodyIdentity,
    /** Code-owned repair budget supplied to the advisor; model output cannot relax it. */
    val limits: MidiAiFixLimits,
    val pitchRange: MidiIntRange? = null,
    val noteDensity: Double,
    val rhythmicDensity: Double,
    val noteCount: Int,
    val notes: List<MidiAiFixNote>,
    val problemRegions: List<MidiAiFixProblemRegion>
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported AI-fix input version" }
        require(SAFE_ID.matches(partId) && HASH.matches(selectedInputSha256) && HASH.matches(inputHash) &&
            contextSchemaVersion > 0 && HASH.matches(contextSha256)) { "AI-fix input identity is invalid" }
        require(ppq in 24..9_600 && harmonicPpq >= ppq && harmonicPpq % ppq == 0) { "AI-fix timing context is invalid" }
        when (contextScope) {
            MidiAiFixContextScope.DECLARED_SONG -> {
                require(declaredKey?.isExecutable == true && declaredTempo?.bpm?.isFinite() == true && declaredTempo.bpm > 0.0 && declaredMeter != null) {
                    "AI-fix declared musical context is invalid"
                }
                require(occurrenceTimeline.isNotEmpty() && occurrenceTimeline.map(MusicalOccurrence::occurrenceId).distinct().size == occurrenceTimeline.size &&
                    harmonicTimeline.isNotEmpty() && harmonicTimeline.all { it.occurrenceId in occurrenceTimeline.map(MusicalOccurrence::occurrenceId).toSet() }) {
                    "AI-fix occurrence or harmonic context is invalid"
                }
            }
            MidiAiFixContextScope.PART_LOCAL -> require(
                declaredKey == null && declaredTempo == null && declaredMeter == null &&
                    occurrenceTimeline.isEmpty() && harmonicTimeline.isEmpty() && analyzedObservations.isEmpty()
            ) { "Part-local AI-fix context must not claim undeclared song authority" }
        }
        require(noteDensity.isFinite() && rhythmicDensity.isFinite() && noteDensity in 0.0..1.0 && rhythmicDensity in 0.0..1.0 && noteCount == notes.size) { "AI-fix note summary is invalid" }
        require(notes.size <= MAX_NOTES && notes.map(MidiAiFixNote::id).distinct().size == notes.size) { "AI-fix note input is invalid" }
        require(problemRegions.size <= MAX_REGIONS && problemRegions.map(MidiAiFixProblemRegion::id).distinct().size == problemRegions.size) { "AI-fix problem regions are invalid" }
        require(melodyIdentity.sourceSha256 == selectedInputSha256 && melodyIdentity.ppq == ppq &&
            melodyIdentity.notes.map { it.id.value }.toSet() == notes.map(MidiAiFixNote::id).toSet()) { "AI-fix melody identity is stale or incomplete" }
        require(limits == MidiAiFixLimits.codeOwned()) { "AI-fix limits must be code-owned" }
        notes.forEach { it.requireValid() }; problemRegions.forEach { it.requireValid() }
    }

    val hasDeclaredSongHarmony: Boolean get() = contextScope == MidiAiFixContextScope.DECLARED_SONG

    companion object { const val CURRENT_VERSION = 2; const val MAX_NOTES = 4_000; const val MAX_REGIONS = 64 }
}

@Serializable
data class MidiAiFixLimits(
    val maximumEdits: Int,
    val maximumAdditions: Int,
    val maximumTimingShiftBeats: Long,
    val maximumDurationExtensionBeats: Long,
    val maximumVelocityDelta: Int,
    val maximumPitchDelta: Int
) {
    init {
        require(maximumEdits in 1..64 && maximumAdditions in 0..8 && maximumTimingShiftBeats in 0..4 &&
            maximumDurationExtensionBeats in 0..4 && maximumVelocityDelta in 0..127 && maximumPitchDelta in 0..12) {
            "AI-fix limits are invalid"
        }
    }

    companion object {
        fun codeOwned() = MidiAiFixLimits(
            MidiAiFixValidator.MAX_EDITS,
            MidiAiFixValidator.MAX_ADDITIONS,
            MidiAiFixValidator.MAX_TIMING_SHIFT_BEATS,
            MidiAiFixValidator.MAX_DURATION_EXTENSION_BEATS,
            MidiAiFixValidator.MAX_VELOCITY_DELTA,
            MidiAiFixValidator.MAX_PITCH_DELTA
        )
    }
}

@Serializable
data class MidiAiFixNote(val id: String, val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long) {
    fun requireValid() = require(SAFE_NOTE_ID.matches(id) && channel in 0..15 && pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) { "AI-fix note is invalid" }
}

@Serializable
enum class MidiAiFixProblemKind { @SerialName("timing") TIMING, @SerialName("collision") COLLISION, @SerialName("duplicate") DUPLICATE, @SerialName("local_gap") LOCAL_GAP }

@Serializable
data class MidiAiFixProblemRegion(val id: String, val kind: MidiAiFixProblemKind, val startTick: Long, val endTick: Long, val noteIds: List<String> = emptyList()) {
    fun requireValid() = require(SAFE_REGION_ID.matches(id) && startTick >= 0 && endTick > startTick && noteIds.size <= 8 && noteIds.all(SAFE_NOTE_ID::matches)) { "AI-fix problem region is invalid" }
}

/** Code-owned model identity and licence provenance; the model cannot provide arbitrary prose. */
@Serializable
data class MidiAiFixModelIdentity(val name: String, val version: String, val hash: String, val license: String) {
    fun requireValid() = require(SAFE_ID.matches(name) && SAFE_ID.matches(version) && HASH.matches(hash) && SAFE_LICENSE.matches(license)) { "AI-fix model identity is invalid" }
}

@Serializable
enum class MidiAiFixEditKind {
    @SerialName("timing") TIMING,
    @SerialName("duration") DURATION,
    @SerialName("velocity") VELOCITY,
    @SerialName("remove_collision_or_duplicate") REMOVE_COLLISION_OR_DUPLICATE,
    @SerialName("pitch") PITCH,
    @SerialName("add_local_gap_note") ADD_LOCAL_GAP_NOTE
}

/** Exactly one edit shape is accepted for each enum; unused fields are rejected by [MidiAiFixValidator]. */
@Serializable
data class MidiAiFixEdit(
    val kind: MidiAiFixEditKind,
    val noteId: String? = null,
    val startTick: Long? = null,
    val durationTicks: Long? = null,
    val velocity: Int? = null,
    val pitch: Int? = null,
    val gapId: String? = null,
    val endTick: Long? = null
)

@Serializable
data class MidiAiFixPlan(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val selectedInputSha256: String,
    val inputHash: String,
    val contextSchemaVersion: Int,
    val contextSha256: String,
    val model: MidiAiFixModelIdentity,
    val edits: List<MidiAiFixEdit>
) {
    fun requireValid(input: MidiAiFixInput) = MidiAiFixValidator.requireValid(this, input)
    companion object { const val CURRENT_VERSION = 2 }
}

/**
 * The untrusted model is deliberately not allowed to select its own provenance.
 * [LocalQwenMidiAiFixPlanner] attaches the configured identity after parsing this
 * response shape.
 */
@Serializable
private data class MidiAiFixModelResponse(
    val version: Int = MidiAiFixPlan.CURRENT_VERSION,
    val partId: String,
    val selectedInputSha256: String,
    val inputHash: String,
    val contextSchemaVersion: Int,
    val contextSha256: String,
    val edits: List<MidiAiFixEdit>
)

/** Code-owned hard limits. The model is advisory; these checks decide every applied byte. */
object MidiAiFixValidator {
    const val MAX_EDITS = 32
    const val MAX_ADDITIONS = 2
    const val MAX_TIMING_SHIFT_BEATS = 1L
    const val MAX_DURATION_EXTENSION_BEATS = 1L
    const val MAX_VELOCITY_DELTA = 24
    const val MAX_PITCH_DELTA = 2

    fun requireValid(plan: MidiAiFixPlan, input: MidiAiFixInput) {
        input.requireValid()
        require(plan.version == MidiAiFixPlan.CURRENT_VERSION && plan.partId == input.partId &&
            plan.selectedInputSha256 == input.selectedInputSha256 && plan.inputHash == input.inputHash &&
            plan.contextSchemaVersion == input.contextSchemaVersion && plan.contextSha256 == input.contextSha256) {
            "AI-fix plan identity or canonical context is stale or invalid"
        }
        plan.model.requireValid()
        require(plan.edits.size <= MAX_EDITS) { "AI-fix plan exceeds the $MAX_EDITS-edit limit" }
        // An empty plan is a code-owned, safe fallback after bounded replanning.
        // It deliberately bypasses output simulation because no draft MIDI will be
        // published for it.
        if (plan.edits.isEmpty()) return
        val notes = input.notes.associateBy(MidiAiFixNote::id)
        val regions = input.problemRegions.associateBy(MidiAiFixProblemRegion::id)
        val edited = mutableSetOf<String>(); var additions = 0
        plan.edits.forEach { edit ->
            fun target(): MidiAiFixNote {
                val id = requireNotNull(edit.noteId) { "AI-fix edit requires noteId" }
                require(edited.add(id)) { "AI-fix plan edits a note more than once" }
                return requireNotNull(notes[id]) { "AI-fix edit references an unknown note" }
            }
            when (edit.kind) {
                MidiAiFixEditKind.TIMING -> {
                    val note = target(); val tick = requireNotNull(edit.startTick) { "Timing edit requires startTick" }
                    require(edit.durationTicks == null && edit.velocity == null && edit.pitch == null && edit.gapId == null && edit.endTick == null) { "Timing edit contains unsupported fields" }
                    val maxShift = input.ppq * MAX_TIMING_SHIFT_BEATS / 4
                    require(tick >= 0 && tick != note.startTick && abs(tick - note.startTick) <= maxShift) {
                        "Timing edit for '${note.id}' must move startTick ${note.startTick} by 1..$maxShift ticks"
                    }
                }
                MidiAiFixEditKind.DURATION -> {
                    val note = target(); val duration = requireNotNull(edit.durationTicks) { "Duration edit requires durationTicks" }
                    require(edit.startTick == null && edit.velocity == null && edit.pitch == null && edit.gapId == null && edit.endTick == null) { "Duration edit contains unsupported fields" }
                    require(duration != note.endTick - note.startTick && duration in maxOf(1, input.ppq / 32).toLong()..(note.endTick - note.startTick + input.ppq * MAX_DURATION_EXTENSION_BEATS)) { "Duration edit exceeds the bounded range or is a no-op" }
                }
                MidiAiFixEditKind.VELOCITY -> {
                    val note = target(); val velocity = requireNotNull(edit.velocity) { "Velocity edit requires velocity" }
                    require(edit.startTick == null && edit.durationTicks == null && edit.pitch == null && edit.gapId == null && edit.endTick == null) { "Velocity edit contains unsupported fields" }
                    require(velocity in 1..127 && velocity != note.velocity && abs(velocity - note.velocity) <= MAX_VELOCITY_DELTA) { "Velocity edit exceeds the bounded range or is a no-op" }
                }
                MidiAiFixEditKind.PITCH -> {
                    val note = target(); val pitch = requireNotNull(edit.pitch) { "Pitch edit requires pitch" }
                    require(edit.startTick == null && edit.durationTicks == null && edit.velocity == null && edit.gapId == null && edit.endTick == null) { "Pitch edit contains unsupported fields" }
                    require(pitch in 0..127 && pitch != note.pitch && abs(pitch - note.pitch) <= MAX_PITCH_DELTA) { "Pitch edit exceeds the bounded range or is a no-op" }
                }
                MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> {
                    val note = target()
                    require(edit.startTick == null && edit.durationTicks == null && edit.velocity == null && edit.pitch == null && edit.gapId == null && edit.endTick == null) { "Removal edit contains unsupported fields" }
                    require(input.problemRegions.any { it.kind in setOf(MidiAiFixProblemKind.COLLISION, MidiAiFixProblemKind.DUPLICATE) && note.id in it.noteIds }) { "Removal is only allowed for a detected collision or duplicate" }
                }
                MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> {
                    require(edit.noteId == null && edit.startTick != null && edit.endTick != null && edit.pitch != null && edit.velocity != null && edit.durationTicks == null) { "Gap-note edit fields are invalid" }
                    val region = requireNotNull(regions[requireNotNull(edit.gapId)]) { "Gap-note edit references an unknown gap" }
                    require(region.kind == MidiAiFixProblemKind.LOCAL_GAP) { "Notes may only be added to a detected local gap" }
                    require(++additions <= MAX_ADDITIONS && plan.edits.count { it.gapId == region.id } == 1) { "Only one bounded note may be added per local gap" }
                    require(edit.startTick!! >= region.startTick && edit.endTick!! <= region.endTick && edit.endTick > edit.startTick && edit.endTick - edit.startTick <= input.ppq) { "Gap-note edit exceeds its local gap" }
                    val range = requireNotNull(input.pitchRange) { "Gap-note edit needs an existing pitch range" }
                    require(edit.pitch!! in range.min..range.max && edit.velocity!! in 40..110) { "Gap-note edit exceeds musical bounds" }
                }
            }
        }
        validateResultingNotes(plan, input)
        validateCanonicalHarmony(plan, input)
        validateMelodyIdentity(plan, input)
    }

    private fun validateMelodyIdentity(plan: MidiAiFixPlan, input: MidiAiFixInput) {
        val mutations = plan.edits.mapNotNull { edit ->
            val note = edit.noteId?.let { id -> input.melodyIdentity.notes.singleOrNull { it.id.value == id } } ?: return@mapNotNull null
            val before = MidiMutationValues(note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)
            val operation = when (edit.kind) {
                MidiAiFixEditKind.TIMING -> MidiMutationOperation.TIMING
                MidiAiFixEditKind.DURATION -> MidiMutationOperation.DURATION
                MidiAiFixEditKind.VELOCITY -> MidiMutationOperation.VELOCITY
                MidiAiFixEditKind.PITCH -> MidiMutationOperation.PITCH
                MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> MidiMutationOperation.REMOVE
                MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> return@mapNotNull null
            }
            val after = when (edit.kind) {
                MidiAiFixEditKind.TIMING -> before.copy(startTick = edit.startTick!!)
                MidiAiFixEditKind.DURATION -> before.copy(endTick = note.originalStartTick + edit.durationTicks!!)
                MidiAiFixEditKind.VELOCITY -> before.copy(velocity = edit.velocity!!)
                MidiAiFixEditKind.PITCH -> before.copy(pitch = edit.pitch!!)
                MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> null
                MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> null
            }
            MidiMutation(operation, note.id, before, after, MidiMutationReasonCode.TIMING_REPAIR)
        }
        MidiMutationInvariants.requireAnchorPreservation(input.melodyIdentity, mutations)
        MidiMutationInvariants.requireAllowedPitchDelta(input.melodyIdentity, mutations, MAX_PITCH_DELTA)
        mutations.forEach { MidiMutationInvariants.requireOccurrenceWindow(input.melodyIdentity, it) }
    }

    /** A repeated source part is rendered at every stable occurrence, so a pitch must fit every active declared chord. */
    private fun validateCanonicalHarmony(plan: MidiAiFixPlan, input: MidiAiFixInput) {
        if (!input.hasDeclaredSongHarmony) {
            require(plan.edits.none { it.kind == MidiAiFixEditKind.PITCH || it.kind == MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE }) {
                "Save Structure and declared harmony before using AI-fix pitch or added-note edits"
            }
            return
        }
        fun requireFit(pitch: Int, start: Long, end: Long) {
            require(requireNotNull(input.declaredKey).contains(PitchClass.canonical(pitch % 12))) { "AI-fix pitch violates declared project scale" }
            val scale = (input.harmonicPpq / input.ppq).toLong()
            val canonicalStart = Math.multiplyExact(start, scale)
            val canonicalEnd = Math.multiplyExact(end, scale)
            val matches = input.occurrenceTimeline.flatMap { occurrence ->
                input.harmonicTimeline.filter { chord -> chord.occurrenceId == occurrence.occurrenceId &&
                    chord.startTick < occurrence.startTick + canonicalEnd && occurrence.startTick + canonicalStart < chord.endTick }
            }
            require(matches.isNotEmpty()) { "AI-fix pitch has no resolved canonical harmonic position" }
            require(matches.all { chord -> (pitch % 12) in chord.chord.quality.intervals.map { (chord.chord.rootChromatic + it) % 12 } }) {
                "AI-fix pitch clashes with the declared active chord"
            }
        }
        val notes = input.notes.associateBy(MidiAiFixNote::id)
        plan.edits.forEach { edit -> when (edit.kind) {
            MidiAiFixEditKind.PITCH -> notes.getValue(edit.noteId!!).let { requireFit(edit.pitch!!, it.startTick, it.endTick) }
            MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> requireFit(edit.pitch!!, edit.startTick!!, edit.endTick!!)
            else -> Unit
        } }
    }

    /**
     * An edit can be individually bounded while its combination with the rest of
     * the part is not playable (for example, moving a note onto a same-pitch
     * neighbour). Validate the complete proposed result before the transformer
     * is allowed to write a draft.
     */
    private fun validateResultingNotes(plan: MidiAiFixPlan, input: MidiAiFixInput) {
        val resulting = input.notes.map {
            ResultingNote(it.id, it.channel, it.pitch, it.velocity, it.startTick, it.endTick)
        }.toMutableList()
        val byId = input.notes.mapIndexed { index, note -> note.id to resulting[index] }.toMap()
        var additionIndex = 0

        plan.edits.forEach { edit -> when (edit.kind) {
            MidiAiFixEditKind.TIMING -> byId.getValue(edit.noteId!!).start = edit.startTick!!
            MidiAiFixEditKind.DURATION -> byId.getValue(edit.noteId!!).let { it.end = it.start + edit.durationTicks!! }
            MidiAiFixEditKind.VELOCITY -> byId.getValue(edit.noteId!!).velocity = edit.velocity!!
            MidiAiFixEditKind.PITCH -> byId.getValue(edit.noteId!!).pitch = edit.pitch!!
            MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> byId.getValue(edit.noteId!!).removed = true
            MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> resulting += ResultingNote("added-${++additionIndex}", 0, edit.pitch!!, edit.velocity!!, edit.startTick!!, edit.endTick!!)
        } }

        val kept = resulting.filterNot(ResultingNote::removed)
        require(kept.all { it.start >= 0 && it.end > it.start && it.pitch in 0..127 && it.velocity in 1..127 }) {
            "AI-fix plan produces an invalid note"
        }
        require(kept.maxOf(ResultingNote::end) == input.notes.maxOf(MidiAiFixNote::endTick)) {
            "AI-fix plan changes MIDI duration"
        }
        val collision = kept.groupBy { it.channel to it.pitch }.values
            .asSequence()
            .flatMap { it.sortedBy { note -> note.start }.zipWithNext().asSequence() }
            .firstOrNull { (a, b) -> a.end > b.start }
        collision?.let {
            throw IllegalArgumentException("AI-fix plan produces a note collision between ${it.first.id} and ${it.second.id}")
        }
    }

    private data class ResultingNote(
        val id: String,
        val channel: Int,
        var pitch: Int,
        var velocity: Int,
        var start: Long,
        var end: Long,
        var removed: Boolean = false
    )
}

@Serializable
data class MidiAiFixDiff(
    val version: Int = 2,
    val partId: String,
    val inputSha256: String,
    val outputSha256: String,
    val edits: List<MidiAiFixEdit>,
    val mutationReport: MidiMutationReport
)
@Serializable
data class MidiAiFixAudit(val version: Int = 1, val partId: String, val inputSha256: String, val outputSha256: String, val planSha256: String, val editCount: Int, val roundTripValid: Boolean)
@Serializable
data class MidiAiFixProvenance(val version: Int = 1, val partId: String, val inputSha256: String, val outputSha256: String, val model: MidiAiFixModelIdentity, val approved: Boolean = false)

/** Strict local model adapter. The prompt contains only [MidiAiFixInput], never a filesystem path or command. */
class LocalQwenMidiAiFixPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    /** Local runtime configuration, never a model-supplied provenance claim. */
    private val model: MidiAiFixModelIdentity = MidiAiFixModelIdentity("qwen", "local", "0".repeat(64), "unknown")
) {
    fun plan(input: MidiAiFixInput): MidiAiFixPlan {
        input.requireValid()
        var safetyFeedback: String? = null
        repeat(MAX_SAFETY_ATTEMPTS) {
            val response = if (client is JsonSchemaLocalQwenClient) {
                client.completeJsonSchema(SYSTEM_PROMPT, request(input, safetyFeedback), RESPONSE_SCHEMA)
            } else {
                client.complete(SYSTEM_PROMPT, request(input, safetyFeedback))
            }
            val parsed = try { json.decodeFromString(MidiAiFixModelResponse.serializer(), response) } catch (error: Exception) {
                throw IllegalArgumentException("Local model returned invalid AI-fix JSON: ${error.message}", error)
            }
            val candidate = MidiAiFixPlan(
                // The model may only propose edits. Canonical identity comes
                // from the code-owned input, so an imprecise echoed hash cannot
                // make a safe proposal look stale or select another context.
                version = MidiAiFixPlan.CURRENT_VERSION,
                partId = input.partId,
                selectedInputSha256 = input.selectedInputSha256,
                inputHash = input.inputHash,
                contextSchemaVersion = input.contextSchemaVersion,
                contextSha256 = input.contextSha256,
                model = model,
                edits = parsed.edits
            )
            try {
                candidate.requireValid(input)
                return candidate
            } catch (error: IllegalArgumentException) {
                if (!isRetryableSafetyRejection(error)) {
                    throw IllegalArgumentException("Local model returned an invalid AI-fix plan: ${error.message}", error)
                }
                safetyFeedback = error.message
            }
        }
        // Never apply an unsafe proposal. The service interprets this empty,
        // validated plan as a successful no-safe-change outcome.
        return MidiAiFixPlan(
            partId = input.partId,
            selectedInputSha256 = input.selectedInputSha256,
            inputHash = input.inputHash,
            contextSchemaVersion = input.contextSchemaVersion,
            contextSha256 = input.contextSha256,
            model = model,
            edits = emptyList()
        ).also { it.requireValid(input) }
    }

    private fun request(input: MidiAiFixInput, safetyFeedback: String?): String = buildString {
        append(json.encodeToString(input))
        if (safetyFeedback != null) {
            append("\n\nA previous candidate was rejected: ")
            append(safetyFeedback)
            append(". Return a different plan that leaves no overlapping notes with the same MIDI channel and pitch.")
        }
    }

    private fun isRetryableSafetyRejection(error: IllegalArgumentException): Boolean =
        error.message?.startsWith("AI-fix plan produces a note collision") == true ||
            error.message == "AI-fix plan produces an invalid note" ||
            error.message?.startsWith("Timing edit for '") == true ||
            error.message == "Removal is only allowed for a detected collision or duplicate" ||
            error.message?.startsWith("Save Structure and declared harmony") == true

    @OptIn(ExperimentalSerializationApi::class)
    private companion object {
        val json = Json { ignoreUnknownKeys = false; explicitNulls = false }
        val SYSTEM_PROMPT = """
            Return one JSON object only: no markdown, prose, reasoning, or fields other than the exact response schema below.
            {
              "version": 2,
              "partId": "copy the supplied value exactly",
              "selectedInputSha256": "copy the supplied value exactly",
              "inputHash": "copy the supplied value exactly",
              "contextSchemaVersion": "copy the supplied value exactly",
              "contextSha256": "copy the supplied value exactly",
              "edits": [{ "kind": "timing", "noteId": "a supplied note id", "startTick": 0 }]
            }
            The model field is code-owned: never include it. Return at most 8 edits, edit each note at most once, and return [] when no safe repair is available.
            When contextScope is DECLARED_SONG, declaredKey, declaredTempo, declaredMeter, occurrenceTimeline, and harmonicTimeline are canonical project authority. analyzedObservations are diagnostics only and never override declared values.
            When contextScope is PART_LOCAL, the part has not been placed in a song: do not return pitch or add_local_gap_note edits; use only timing, duration, velocity, or removal of an identified collision/duplicate.
            Only edit a note named in problemRegions. Do not quantize, align, or otherwise rewrite every supplied note. Do not set multiple notes to the same start tick unless they already shared it and the edit is an identified collision/duplicate removal.
            The complete resulting MIDI must have no overlapping notes sharing the same MIDI channel and pitch. Check every timing, duration, pitch, and added-note edit against all supplied notes before responding.
            Use only these edit objects, with exactly the fields stated for that kind:
            - timing: {"kind":"timing","noteId":"supplied id","startTick":new non-negative integer}; startTick must differ from the note's supplied startTick and move it by no more than ppq / 4 ticks.
            - duration: {"kind":"duration","noteId":"supplied id","durationTicks":new positive integer}.
            - velocity: {"kind":"velocity","noteId":"supplied id","velocity":integer 1..127}.
            - pitch: {"kind":"pitch","noteId":"supplied id","pitch":integer 0..127}.
            - remove_collision_or_duplicate: {"kind":"remove_collision_or_duplicate","noteId":"supplied id"}; only for a supplied collision or duplicate region.
            - add_local_gap_note: {"kind":"add_local_gap_note","gapId":"supplied local-gap id","startTick":integer,"endTick":integer,"pitch":integer,"velocity":integer}; at most two additions.
            Do not use action, align, quantize, a model field, or any other keys. Prefer the smallest safe set of concrete edits. Never return paths, commands, code, prompts, instruments, or new phrases.
        """.trimIndent()
        val RESPONSE_SCHEMA = Json.parseToJsonElement(
            """{
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "version":{"type":"integer"},
                "partId":{"type":"string"},
                "selectedInputSha256":{"type":"string"},
                "inputHash":{"type":"string"},
                "contextSchemaVersion":{"type":"integer"},
                "contextSha256":{"type":"string"},
                "edits":{"type":"array","maxItems":32,"items":{
                  "type":"object",
                  "additionalProperties":false,
                  "properties":{
                    "kind":{"type":"string","enum":["timing","duration","velocity","pitch","remove_collision_or_duplicate","add_local_gap_note"]},
                    "noteId":{"type":"string"},
                    "startTick":{"type":"integer"},
                    "durationTicks":{"type":"integer"},
                    "velocity":{"type":"integer"},
                    "pitch":{"type":"integer"},
                    "gapId":{"type":"string"},
                    "endTick":{"type":"integer"}
                  },
                  "required":["kind"]
                }}
              },
              "required":["version","partId","selectedInputSha256","inputHash","contextSchemaVersion","contextSha256","edits"]
            }"""
        ).jsonObject
        const val MAX_SAFETY_ATTEMPTS = 3
    }
}

/** Deterministic MIDI writer. It applies an already validated plan and verifies its own round trip before publication. */
class MidiAiFixTransformer {
    fun apply(input: Path, output: Path, plan: MidiAiFixPlan, modelInput: MidiAiFixInput): MidiAiFixDiff {
        plan.requireValid(modelInput)
        val before = sha256(input)
        require(before == modelInput.selectedInputSha256) { "Selected MIDI changed before applying the AI fix" }
        val sequence = read(input)
        require(sequence.resolution == modelInput.ppq) { "Cleaned MIDI PPQ no longer matches AI-fix input" }
        val notes = notes(sequence, input)
        val byId = notes.associateBy { it.id }
        val transformed = notes.associate { it.id to it.toMutable() }.toMutableMap()
        val additions = mutableListOf<MutableNote>()
        plan.edits.forEach { edit -> when (edit.kind) {
            MidiAiFixEditKind.TIMING -> transformed.getValue(edit.noteId!!).start = edit.startTick!!
            MidiAiFixEditKind.DURATION -> transformed.getValue(edit.noteId!!).end = transformed.getValue(edit.noteId!!).start + edit.durationTicks!!
            MidiAiFixEditKind.VELOCITY -> transformed.getValue(edit.noteId!!).velocity = edit.velocity!!
            MidiAiFixEditKind.PITCH -> transformed.getValue(edit.noteId!!).pitch = edit.pitch!!
            MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> transformed.getValue(edit.noteId!!).removed = true
            MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> additions += MutableNote(null, 0, edit.pitch!!, edit.velocity!!, edit.startTick!!, edit.endTick!!)
        } }
        val all = transformed.values.filterNot { it.removed } + additions
        require(all.all { it.start >= 0 && it.end > it.start && it.pitch in 0..127 && it.velocity in 1..127 }) { "AI-fix application produced an invalid note" }
        require(noCollisions(all)) { "AI-fix application produced a note collision" }
        publish(sequence, notes, transformed, additions, output)
        require(sha256(input) == before) { "Cleaned MIDI changed while applying the AI fix" }
        val result = read(output); val outputNotes = notes(result, output)
        require(result.resolution == sequence.resolution && tempoMap(result) == tempoMap(sequence) && signatures(result) == signatures(sequence)) { "AI-fix output changed MIDI timing metadata" }
        require(outputNotes.size == all.size && noCollisions(outputNotes.map { it.toMutable() })) { "AI-fix output did not round-trip" }
        val outputHash = sha256(output)
        return MidiAiFixDiff(
            partId = modelInput.partId,
            inputSha256 = before,
            outputSha256 = outputHash,
            edits = plan.edits,
            mutationReport = mutationReport(modelInput, plan, outputHash)
        ).also { it.mutationReport.requireValid() }
    }

    private fun mutationReport(input: MidiAiFixInput, plan: MidiAiFixPlan, outputSha256: String): MidiMutationReport {
        val notes = input.melodyIdentity.notes.associateBy { it.id.value }
        var additionOrdinal = input.melodyIdentity.notes.maxOfOrNull { it.noteOnOrdinal }?.plus(1) ?: 0
        val mutations = plan.edits.map { edit ->
            if (edit.kind == MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE) {
                val values = MidiMutationValues(0, edit.pitch!!, edit.velocity!!, edit.startTick!!, edit.endTick!!)
                MidiMutation(MidiMutationOperation.ADD, MelodyNoteId.derive(input.selectedInputSha256, 0, 0, additionOrdinal++, values.pitch, values.startTick, values.endTick), null, values, MidiMutationReasonCode.TIMING_REPAIR)
            } else {
                val note = notes.getValue(edit.noteId!!)
                val before = MidiMutationValues(note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)
                val operation = when (edit.kind) {
                    MidiAiFixEditKind.TIMING -> MidiMutationOperation.TIMING
                    MidiAiFixEditKind.DURATION -> MidiMutationOperation.DURATION
                    MidiAiFixEditKind.VELOCITY -> MidiMutationOperation.VELOCITY
                    MidiAiFixEditKind.PITCH -> MidiMutationOperation.PITCH
                    MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> MidiMutationOperation.REMOVE
                    MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> error("unreachable")
                }
                val after = when (edit.kind) {
                    MidiAiFixEditKind.TIMING -> before.copy(startTick = edit.startTick!!)
                    MidiAiFixEditKind.DURATION -> before.copy(endTick = before.startTick + edit.durationTicks!!)
                    MidiAiFixEditKind.VELOCITY -> before.copy(velocity = edit.velocity!!)
                    MidiAiFixEditKind.PITCH -> before.copy(pitch = edit.pitch!!)
                    MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> null
                    MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> error("unreachable")
                }
                val reason = when (edit.kind) {
                    MidiAiFixEditKind.TIMING -> MidiMutationReasonCode.TIMING_REPAIR
                    MidiAiFixEditKind.DURATION, MidiAiFixEditKind.VELOCITY, MidiAiFixEditKind.PITCH -> MidiMutationReasonCode.HARMONY_REPAIR
                    MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE -> MidiMutationReasonCode.COLLISION_REPAIR
                    MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE -> error("unreachable")
                }
                MidiMutation(operation, note.id, before, after, reason)
            }
        }.sortedWith(compareBy<MidiMutation> { it.noteId.value }.thenBy { it.operation.ordinal })
        val additions = plan.edits.count { it.kind == MidiAiFixEditKind.ADD_LOCAL_GAP_NOTE }
        val deletions = plan.edits.count { it.kind == MidiAiFixEditKind.REMOVE_COLLISION_OR_DUPLICATE }
        return MidiMutationReport(
            inputSha256 = input.selectedInputSha256,
            outputSha256 = outputSha256,
            contextSha256 = input.contextSha256,
            target = "part-${input.partId}",
            stage = MidiMutationStage.AI_FIX,
            mutations = mutations,
            budget = MidiMutationBudget(input.noteCount, plan.edits.size - additions, additions, deletions,
                MidiAiFixValidator.MAX_EDITS, MidiAiFixValidator.MAX_ADDITIONS, MidiAiFixValidator.MAX_EDITS)
        )
    }

    private fun publish(source: Sequence, notes: List<Note>, transformed: Map<String, MutableNote>, additions: List<MutableNote>, output: Path) {
        val sequence = Sequence(Sequence.PPQ, source.resolution)
        val starts = notes.associateBy { it.startRef }; val ends = notes.associateBy { it.endRef }
        source.tracks.forEachIndexed { trackIndex, track ->
            val destination = sequence.createTrack()
            (0 until track.size()).forEach { index ->
                val ref = EventRef(trackIndex, index)
                val note = starts[ref] ?: ends[ref]
                val state = note?.let { transformed.getValue(it.id) }
                if (state?.removed == true) return@forEach
                val event = track[index]
                if (note == null || state == null) destination.add(MidiEvent(event.message.clone() as MidiMessage, event.tick))
                else {
                    val original = event.message as ShortMessage
                    val message = ShortMessage(original.command, original.channel, state.pitch, if (starts.containsKey(ref)) state.velocity else original.data2)
                    destination.add(MidiEvent(message, if (starts.containsKey(ref)) state.start else state.end))
                }
            }
            if (trackIndex == 0) additions.sortedWith(compareBy<MutableNote> { it.start }.thenBy { it.pitch }).forEach { note ->
                destination.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, note.channel, note.pitch, note.velocity), note.start))
                destination.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, note.channel, note.pitch, 0), note.end))
            }
        }
        Files.createDirectories(checkNotNull(output.parent)); val temporary = output.resolveSibling(".${output.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write AI-fix draft MIDI" }
            read(temporary)
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publication is not supported for AI-fix draft '$output'.", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun notes(sequence: Sequence, path: Path): List<Note> {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Triple<EventRef, MidiEvent, Int>>>()
        val ordinal = mutableMapOf<Pair<Int, Int>, Int>()
        val sourceSha256 = sha256(path)
        val result = mutableListOf<Note>()
        sequence.tracks.forEachIndexed { trackIndex, track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            val key = Triple(trackIndex, message.channel, message.data1)
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                val ordinalKey = trackIndex to message.channel
                val noteOnOrdinal = ordinal.getOrDefault(ordinalKey, 0)
                ordinal[ordinalKey] = noteOnOrdinal + 1
                active.getOrPut(key) { ArrayDeque() }.addLast(Triple(EventRef(trackIndex, index), event, noteOnOrdinal))
            }
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Invalid cleaned MIDI '$path': unmatched note-off")
                require(event.tick > start.second.tick) { "Invalid cleaned MIDI '$path': non-positive note duration" }
                result += Note(MelodyNoteId.derive(sourceSha256, trackIndex, message.channel, start.third, message.data1, start.second.tick, event.tick).value, start.first, EventRef(trackIndex, index), message.channel, message.data1, (start.second.message as ShortMessage).data2, start.second.tick, event.tick)
            }
        } }
        require(active.values.all { it.isEmpty() }) { "Invalid cleaned MIDI '$path': unclosed note-on" }
        return result
    }
    private fun read(path: Path): Sequence = try {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "Cleaned MIDI is missing or invalid" }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "Cleaned MIDI is missing or invalid" } }
        MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "AI fix requires PPQ MIDI" } }
    } catch (error: IllegalArgumentException) { throw error } catch (error: Exception) { throw IllegalArgumentException("Cleaned MIDI is invalid", error) }
    private fun tempoMap(sequence: Sequence): List<Pair<Long, String>> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? MetaMessage)?.takeIf { it.type == 0x51 }?.let { track[index].tick to it.data.joinToString(",") } } }.sortedBy { it.first }
    private fun signatures(sequence: Sequence): List<Pair<Long, String>> = sequence.tracks.flatMap { track -> (0 until track.size()).mapNotNull { index -> (track[index].message as? MetaMessage)?.takeIf { it.type == 0x58 }?.let { track[index].tick to it.data.joinToString(",") } } }.sortedBy { it.first }
    private fun noCollisions(notes: List<MutableNote>): Boolean = notes.groupBy { it.channel to it.pitch }.values.all { group -> group.sortedBy { it.start }.zipWithNext().all { (a, b) -> a.end <= b.start } }
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private data class EventRef(val track: Int, val index: Int)
    private data class Note(val id: String, val startRef: EventRef, val endRef: EventRef, val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long) { fun toMutable() = MutableNote(id, channel, pitch, velocity, start, end) }
    private data class MutableNote(val id: String?, val channel: Int, var pitch: Int, var velocity: Int, var start: Long, var end: Long, var removed: Boolean = false)
}

/** Atomic persistence for the reviewable draft, human-readable evidence, and approved selection. */
object MidiAiFixStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun writeDraft(root: Path, input: MidiAiFixInput, plan: MidiAiFixPlan, diff: MidiAiFixDiff): MidiAiFixReferences {
        plan.requireValid(input)
        require(diff.version == 2 && diff.inputSha256 == input.selectedInputSha256 && HASH.matches(diff.outputSha256) &&
            diff.mutationReport.inputSha256 == input.selectedInputSha256 && diff.mutationReport.outputSha256 == diff.outputSha256 &&
            diff.mutationReport.contextSha256 == input.contextSha256 && diff.mutationReport.stage == MidiMutationStage.AI_FIX) {
            "AI-fix draft evidence is invalid"
        }
        diff.mutationReport.requireValid()
        val normalized = root.toAbsolutePath().normalize(); val partId = input.partId
        val draft = normalized.resolve(MidiAiFixArtifactPaths.draft(partId)); require(sha256(draft) == diff.outputSha256) { "AI-fix draft MIDI hash does not match its evidence" }
        write(normalized.resolve(MidiAiFixArtifactPaths.plan(partId)), json.encodeToString(plan))
        write(normalized.resolve(MidiAiFixArtifactPaths.diff(partId)), json.encodeToString(diff))
        val planHash = sha256(normalized.resolve(MidiAiFixArtifactPaths.plan(partId)))
        write(normalized.resolve(MidiAiFixArtifactPaths.audit(partId)), json.encodeToString(MidiAiFixAudit(partId = partId, inputSha256 = input.selectedInputSha256, outputSha256 = diff.outputSha256, planSha256 = planHash, editCount = plan.edits.size, roundTripValid = true)))
        write(normalized.resolve(MidiAiFixArtifactPaths.provenance(partId)), json.encodeToString(MidiAiFixProvenance(partId = partId, inputSha256 = input.selectedInputSha256, outputSha256 = diff.outputSha256, model = plan.model)))
        val references = MidiAiFixReferences(input.selectedInputSha256, WorkflowArtifactReference(MidiAiFixArtifactPaths.draft(partId), diff.outputSha256))
        val project = ProjectStore.read(normalized); val part = project.parts.singleOrNull { it.id == partId } ?: error("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no cleaned MIDI" }
        val selectedInput = when (midi.technicalCorrectionSelection) {
            TechnicalCorrectionSelection.CORRECTED -> requireNotNull(midi.technicalCorrection).output.file
            TechnicalCorrectionSelection.BASE -> requireNotNull(midi.clean)
        }
        require(sha256(normalized.resolve(selectedInput)) == input.selectedInputSha256) { "Selected MIDI changed before AI-fix draft publication" }
        ProjectStore.write(normalized, project.copy(parts = project.parts.map { if (it.id == partId) it.copy(midi = midi.copy(aiFixSelection = MidiAiFixSelection.PENDING, aiFix = references)) else it }, workflow = project.workflow.markCurrent(WorkflowArtifact.AI_FIX)))
        return references
    }

    fun approve(root: Path, partId: String, input: MidiAiFixInput): MidiAiFixReferences {
        val normalized = root.toAbsolutePath().normalize(); val project = ProjectStore.read(normalized); val part = project.parts.singleOrNull { it.id == partId } ?: error("Part not found: $partId")
        val midi = requireNotNull(part.midi); val refs = requireNotNull(midi.aiFix) { "No AI-fix draft exists. Create one first." }
        require(refs.inputSha256 == input.selectedInputSha256 && refs.draft != null) { "AI-fix draft is stale. Regenerate it." }
        val draft = normalized.resolve(refs.draft.file); require(sha256(draft) == refs.draft.sha256) { "AI-fix draft is stale. Regenerate it." }
        val plan = readPlan(normalized, partId); plan.requireValid(input)
        val diff = readDiff(normalized, partId); require(diff.version == 2 && diff.inputSha256 == input.selectedInputSha256 && diff.outputSha256 == refs.draft.sha256 && diff.mutationReport.contextSha256 == input.contextSha256) { "AI-fix diff is stale" }
        val approved = normalized.resolve(MidiAiFixArtifactPaths.approved(partId)); copyAtomically(draft, approved)
        val approvedRef = WorkflowArtifactReference(MidiAiFixArtifactPaths.approved(partId), sha256(approved))
        val updatedRefs = refs.copy(approved = approvedRef)
        write(normalized.resolve(MidiAiFixArtifactPaths.provenance(partId)), json.encodeToString(MidiAiFixProvenance(partId = partId, inputSha256 = input.selectedInputSha256, outputSha256 = approvedRef.sha256, model = plan.model, approved = true)))
        ProjectStore.write(normalized, project.copy(parts = project.parts.map { if (it.id == partId) it.copy(analysis = null, midi = midi.copy(aiFixSelection = MidiAiFixSelection.APPROVED, aiFix = updatedRefs)) else it }, workflow = project.workflow.invalidate(WorkflowChange.AI_FIX_SELECTION).markCurrent(WorkflowArtifact.AI_FIX)))
        return updatedRefs
    }

    fun selectCleaned(root: Path, partId: String): MidiAiFixReferences? {
        val normalized = root.toAbsolutePath().normalize(); val project = ProjectStore.read(normalized); val part = project.parts.singleOrNull { it.id == partId } ?: error("Part not found: $partId"); val midi = requireNotNull(part.midi)
        val changed = midi.aiFixSelection != MidiAiFixSelection.SKIP
        val updated = project.copy(parts = project.parts.map { if (it.id == partId) it.copy(analysis = if (changed) null else it.analysis, midi = midi.copy(aiFixSelection = MidiAiFixSelection.SKIP)) else it }, workflow = if (changed) project.workflow.invalidate(WorkflowChange.AI_FIX_SELECTION).markCurrent(WorkflowArtifact.AI_FIX) else project.workflow)
        ProjectStore.write(normalized, updated); return midi.aiFix
    }

    /** Records a completed AI-fix stage when the model cannot produce a safe change. */
    fun recordNoSafeFix(root: Path, partId: String) {
        val normalized = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(normalized)
        val part = project.parts.singleOrNull { it.id == partId } ?: error("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no cleaned MIDI" }
        ProjectStore.write(normalized, project.copy(
            parts = project.parts.map {
                if (it.id == partId) it.copy(
                    analysis = null,
                    midi = midi.copy(
                        aiFixSelection = MidiAiFixSelection.SKIP,
                        aiFix = null
                    )
                ) else it
            },
            workflow = project.workflow.invalidate(WorkflowChange.AI_FIX_SELECTION).markCurrent(WorkflowArtifact.AI_FIX)
        ))
    }

    fun readPlan(root: Path, partId: String): MidiAiFixPlan = read(root.resolve(MidiAiFixArtifactPaths.plan(partId)), MidiAiFixPlan.serializer())
    fun readDiff(root: Path, partId: String): MidiAiFixDiff = read(root.resolve(MidiAiFixArtifactPaths.diff(partId)), MidiAiFixDiff.serializer())
    private fun <T> read(path: Path, serializer: kotlinx.serialization.KSerializer<T>): T = try { json.decodeFromString(serializer, Files.readString(path, StandardCharsets.UTF_8)) } catch (error: Exception) { throw IllegalArgumentException("AI-fix evidence is malformed: $path", error) }
    private fun write(path: Path, text: String) { Files.createDirectories(checkNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.tmp"); try { Files.writeString(temporary, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } finally { Files.deleteIfExists(temporary) } }
    private fun copyAtomically(source: Path, target: Path) { Files.createDirectories(checkNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp"); try { Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING); Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } finally { Files.deleteIfExists(temporary) } }
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

/** Builds a bounded advisor DTO from the canonical part-repair projection and selected MIDI. */
object MidiAiFixInputFactory {
    fun build(projection: PartRepairProjection, selectedInput: Path): MidiAiFixInput {
        require(Files.isRegularFile(selectedInput) && sha256(selectedInput) == projection.part.sha256) { "Selected MIDI changed before AI-fix context assembly" }
        val analysis = projection.analysis.analysis
        require(analysis.partId == projection.part.partId && projection.analysis.selectedMidiSha256 == projection.part.sha256) {
            "AI-fix analysis does not describe the selected canonical MIDI"
        }
        require(projection.harmonyPpq % analysis.ppq == 0) { "AI-fix harmonic timeline cannot represent selected MIDI timing" }
        val beatTicks = analysis.ppq * 4L / projection.meter.denominator
        val notes = collectNotes(selectedInput, beatTicks)
        require(notes.size == analysis.noteCount && notes.size <= MidiAiFixInput.MAX_NOTES) { "Selected MIDI is too large or inconsistent for bounded AI fix" }
        val regions = problems(notes, analysis.ppq)
        val identity = MelodyIdentityBuilder.build(selectedInput, beatTicks)
        val withoutHash = MidiAiFixInput(
            partId = projection.part.partId,
            selectedInputSha256 = projection.part.sha256,
            inputHash = "0".repeat(64),
            ppq = analysis.ppq,
            harmonicPpq = projection.harmonyPpq,
            contextSchemaVersion = projection.schemaVersion,
            contextSha256 = projection.contextSha256,
            declaredKey = projection.projectKey,
            declaredTempo = projection.tempo,
            declaredMeter = projection.meter,
            occurrenceTimeline = projection.occurrences,
            harmonicTimeline = projection.harmony,
            analyzedObservations = projection.diagnostics,
            melodyIdentity = identity,
            limits = MidiAiFixLimits.codeOwned(),
            pitchRange = analysis.pitchRange,
            noteDensity = analysis.noteDensity,
            rhythmicDensity = analysis.rhythmicDensity,
            noteCount = notes.size,
            notes = notes,
            problemRegions = regions
        )
        return withoutHash.copy(inputHash = sha256(json.encodeToString(withoutHash.copy(inputHash = "")))) .also(MidiAiFixInput::requireValid)
    }

    /**
     * AI Fix is available during import, before a part has a song occurrence.
     * This context deliberately carries no inferred key or harmony as declared
     * authority; the validator consequently permits only non-harmonic repairs.
     */
    fun buildPartLocal(partId: String, selectedInput: Path): MidiAiFixInput {
        val selectedInputSha256 = sha256(selectedInput)
        val analysis = MidiPartAnalyzer().analyze(selectedInput, partId)
        val beatTicks = analysis.ppq * 4L / analysis.timeSignatures.first().denominator
        val notes = collectNotes(selectedInput, beatTicks)
        require(notes.size == analysis.noteCount && notes.size <= MidiAiFixInput.MAX_NOTES) { "Selected MIDI is too large or inconsistent for bounded AI fix" }
        val identity = MelodyIdentityBuilder.build(selectedInput, beatTicks)
        val contextSha256 = sha256(json.encodeToString(PartLocalRepairContext(partId, selectedInputSha256, analysis)))
        val withoutHash = MidiAiFixInput(
            partId = partId,
            selectedInputSha256 = selectedInputSha256,
            inputHash = "0".repeat(64),
            ppq = analysis.ppq,
            harmonicPpq = analysis.ppq,
            contextSchemaVersion = PART_LOCAL_CONTEXT_SCHEMA_VERSION,
            contextSha256 = contextSha256,
            contextScope = MidiAiFixContextScope.PART_LOCAL,
            melodyIdentity = identity,
            limits = MidiAiFixLimits.codeOwned(),
            pitchRange = analysis.pitchRange,
            noteDensity = analysis.noteDensity,
            rhythmicDensity = analysis.rhythmicDensity,
            noteCount = notes.size,
            notes = notes,
            problemRegions = problems(notes, analysis.ppq)
        )
        return withoutHash.copy(inputHash = sha256(json.encodeToString(withoutHash.copy(inputHash = "")))).also(MidiAiFixInput::requireValid)
    }

    @Serializable
    private data class PartLocalRepairContext(val partId: String, val selectedInputSha256: String, val analysis: MidiAnalysis)

    private fun collectNotes(path: Path, canonicalBeatTicks: Long): List<MidiAiFixNote> =
        MelodyIdentityBuilder.build(path, canonicalBeatTicks).notes.map { note ->
            MidiAiFixNote(note.id.value, note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)
        }
    private fun problems(notes: List<MidiAiFixNote>, ppq: Int): List<MidiAiFixProblemRegion> {
        val regions = mutableListOf<MidiAiFixProblemRegion>(); var index = 0
        fun add(kind: MidiAiFixProblemKind, start: Long, end: Long, ids: List<String> = emptyList()) { if (regions.size < MidiAiFixInput.MAX_REGIONS) regions += MidiAiFixProblemRegion("r-${index++.toString().padStart(3, '0')}", kind, start, end, ids) }
        notes.groupBy { it.channel to it.pitch }.values.forEach { group -> group.sortedBy { it.startTick }.zipWithNext().forEach { (a, b) -> if (a.endTick > b.startTick) add(MidiAiFixProblemKind.COLLISION, b.startTick, a.endTick, listOf(a.id, b.id)); if (a.startTick == b.startTick && a.endTick == b.endTick && a.velocity == b.velocity) add(MidiAiFixProblemKind.DUPLICATE, a.startTick, a.endTick, listOf(a.id, b.id)) } }
        notes.filter { it.startTick % (ppq / 4L).coerceAtLeast(1L) !in 0L..1L }.forEach { add(MidiAiFixProblemKind.TIMING, it.startTick, it.endTick, listOf(it.id)) }
        notes.sortedBy { it.startTick }.zipWithNext().forEach { (a, b) -> if (b.startTick - a.endTick in (ppq / 2L)..ppq.toLong()) add(MidiAiFixProblemKind.LOCAL_GAP, a.endTick, b.startTick) }
        return regions
    }
    private fun sha256(path: Path): String = sha256(Files.readAllBytes(path))
    private fun sha256(text: String): String = sha256(text.toByteArray(StandardCharsets.UTF_8))
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @OptIn(ExperimentalSerializationApi::class) private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    private const val PART_LOCAL_CONTEXT_SCHEMA_VERSION = 1
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SAFE_NOTE_ID = Regex("m-[0-9a-f]{64}")
private val SAFE_REGION_ID = Regex("r-[0-9]{3}")
private val SAFE_LICENSE = Regex("[A-Za-z0-9._+-]{1,80}")
private val HASH = Regex("[0-9a-f]{64}")
