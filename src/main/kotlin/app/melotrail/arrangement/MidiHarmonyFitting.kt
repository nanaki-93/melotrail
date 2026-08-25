package app.melotrail.arrangement

import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
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
import kotlin.math.roundToLong

/** One authoritative harmonic span mapped from the global timeline into one prepared-source MIDI file. */
@Serializable
data class MelodyHarmonyFitSpan(
    val bar: Long,
    val localStartTick: Long,
    val localEndTick: Long,
    val rootChromatic: Int,
    val rootSymbol: String,
    val quality: ChordQuality
) {
    init {
        require(bar >= 0 && localStartTick >= 0 && localEndTick > localStartTick && rootChromatic in 0..11 && rootSymbol.isNotBlank()) {
            "Harmony-fit span is invalid"
        }
    }

    /** Return all pitch classes explicitly authorized by this user-authored chord. */
    fun chordTones(): Set<Int> = quality.intervals.map { (rootChromatic + it).mod(12) }.toSet()
}

/** Immutable occurrence-local authority supplied to the deterministic harmony fitter. */
@Serializable
data class MelodyHarmonyFitContext(
    val authorityContextSha256: String,
    val partId: String,
    val occurrenceId: String,
    val projectKey: MusicalKey,
    val tempoBpm: Double,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val ppq: Int,
    val harmonicSpans: List<MelodyHarmonyFitSpan>
) {
    /** Reject a non-authoritative, non-local, or non-executable context before a MIDI file is changed. */
    fun requireValid() {
        require(HASH.matches(authorityContextSha256) && IDENTIFIER.matches(partId) && IDENTIFIER.matches(occurrenceId) && projectKey.isExecutable &&
            tempoBpm.isFinite() && tempoBpm in 20.0..400.0 && meterNumerator in 1..32 &&
            meterDenominator in setOf(1, 2, 4, 8, 16, 32) && ppq > 0 && harmonicSpans.isNotEmpty()) {
            "Harmony-fit context is invalid"
        }
        require(harmonicSpans.first().localStartTick == 0L && harmonicSpans.zipWithNext().all { (left, right) ->
            left.localEndTick == right.localStartTick && left.bar + 1 == right.bar
        }) { "Harmony-fit spans must form one contiguous occurrence-local timeline" }
        require(harmonicSpans.all { it.localEndTick > it.localStartTick }) { "Harmony-fit spans contain an empty interval" }
    }
}

/** Fully verified input to QP-006; the input must be the exact QP-005 candidate. */
data class MelodyHarmonyFitRequest(
    val root: Path,
    val input: MelodyPreparationArtifactReference,
    val monophonicPreparationReport: WorkflowArtifactReference,
    val context: MelodyHarmonyFitContext
) {
    /** Validate static references before resolving them beneath the project root. */
    fun requireValid() {
        context.requireValid()
        require(input.path.isNotBlank() && HASH.matches(input.sha256) && input.ppq == context.ppq &&
            monophonicPreparationReport.file.isNotBlank() && HASH.matches(monophonicPreparationReport.sha256)) {
            "Harmony-fit request is invalid"
        }
    }
}

/** Whether the QP-006 stage published a candidate or retained a blocking report for review. */
@Serializable
enum class MelodyHarmonyFitStatus { COMPLETED, BLOCKED }

/** The versioned permission by which a published note is allowed to sound. */
@Serializable
enum class MelodyHarmonyEligibility { CHORD_TONE, WEAK_SCALE_PASSING_TONE, COMMON_TONE_TIE, DELIBERATE_SUSPENSION }

/** A deterministic reason for a note-level repair or preservation decision. */
@Serializable
enum class MelodyHarmonyFitReason {
    ALREADY_CHORD_TONE,
    AUTHORIZED_CHROMATIC_CHORD_TONE,
    PRESERVED_WEAK_SCALE_PASSING_TONE,
    REPAIRED_TO_ACTIVE_CHORD_TONE,
    PRESERVED_COMMON_TONE_TIE,
    PRESERVED_DELIBERATE_SUSPENSION,
    SHORTENED_INCOMPATIBLE_BOUNDARY
}

/** A failure mode that prevents a speculative or excessive harmony repair from being published. */
@Serializable
enum class MelodyHarmonyFitIssueKind {
    AMBIGUOUS_NEAREST_PITCH,
    EXCESSIVE_PITCH_MOVEMENT,
    EXCESSIVE_EDIT_BUDGET,
    UNSATISFIABLE_BOUNDARY_GAP,
    INVALID_OUTPUT_ELIGIBILITY
}

/** One blocking condition with the affected source-note evidence. */
@Serializable
data class MelodyHarmonyFitIssue(
    val kind: MelodyHarmonyFitIssueKind,
    val noteId: String? = null,
    val boundaryTick: Long? = null,
    val observed: Long? = null,
    val limit: Long? = null
) {
    init {
        require(noteId != null || boundaryTick != null) { "Harmony-fit issue requires note or boundary evidence" }
        require(boundaryTick == null || boundaryTick >= 0) { "Harmony-fit issue boundary is invalid" }
        require((observed == null) == (limit == null)) { "Harmony-fit issue measurement is incomplete" }
    }
}

/** Exact input or output values used for note-level repair evidence. */
@Serializable
data class MelodyHarmonyFitNoteValues(
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long
) {
    init {
        require(pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) {
            "Harmony-fit note values are invalid"
        }
    }
}

/** Before/after evidence for every input melody note, including unchanged notes. */
@Serializable
data class MelodyHarmonyFitNoteDecision(
    val noteId: String,
    val before: MelodyHarmonyFitNoteValues,
    val after: MelodyHarmonyFitNoteValues? = null,
    val eligibility: MelodyHarmonyEligibility? = null,
    val reasons: List<MelodyHarmonyFitReason> = emptyList()
) {
    init {
        require(NOTE_ID.matches(noteId) && reasons == reasons.distinct().sortedBy(MelodyHarmonyFitReason::ordinal) &&
            ((after == null && eligibility == null && reasons.isEmpty()) || (after != null && eligibility != null && reasons.isNotEmpty()))) {
            "Harmony-fit note decision is invalid"
        }
    }
}

/** Each harmonic, measure, or occurrence boundary is explicitly evaluated after controller materialization. */
@Serializable
data class MelodyHarmonyFitBoundaryEvidence(
    val tick: Long,
    val kinds: List<MelodyHarmonyBoundaryKind>,
    val carriedNoteId: String? = null,
    val decision: MelodyHarmonyBoundaryDecision,
    val incomingRootChromatic: Int? = null,
    val resolutionNoteId: String? = null,
    val controllerBehavior: MelodyHarmonyControllerBehavior
) {
    init {
        require(tick >= 0 && kinds.isNotEmpty() && kinds == kinds.distinct().sortedBy(MelodyHarmonyBoundaryKind::ordinal) &&
            (carriedNoteId == null || NOTE_ID.matches(carriedNoteId)) &&
            (resolutionNoteId == null || NOTE_ID.matches(resolutionNoteId)) &&
            ((decision == MelodyHarmonyBoundaryDecision.NO_CARRIED_NOTE && carriedNoteId == null) ||
                (decision != MelodyHarmonyBoundaryDecision.NO_CARRIED_NOTE && carriedNoteId != null))) {
            "Harmony-fit boundary evidence is invalid"
        }
    }
}

/** Boundary classes jointly evaluated by the QP-006 release policy. */
@Serializable
enum class MelodyHarmonyBoundaryKind { CHORD, MEASURE, OCCURRENCE_END }

/** The action taken for a note that reaches an authoritative boundary. */
@Serializable
enum class MelodyHarmonyBoundaryDecision {
    NO_CARRIED_NOTE,
    PRESERVED_COMMON_TONE_TIE,
    PRESERVED_DELIBERATE_SUSPENSION,
    SHORTENED_INCOMPATIBLE_NOTE
}

/** QP-005 materializes sustain before this stage and QP-006 emits controller-free MIDI. */
@Serializable
enum class MelodyHarmonyControllerBehavior { MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT }

/** The measured release gap: tempo/PPQ derived, capped by the calibrated 50 ms reference. */
@Serializable
data class MelodyHarmonyGapPolicy(
    val version: Int = VERSION,
    val tempoBpm: Double,
    val ppq: Int,
    val calibratedUpperReferenceMilliseconds: Int = 50,
    val maximumFractionOfBeat: Int = 16,
    val derivedGapTicks: Long
) {
    init {
        require(version == VERSION && tempoBpm.isFinite() && tempoBpm in 20.0..400.0 && ppq > 0 &&
            calibratedUpperReferenceMilliseconds == 50 && maximumFractionOfBeat == 16 && derivedGapTicks > 0) {
            "Harmony-fit gap policy is invalid"
        }
    }

    companion object { const val VERSION = 1 }
}

/** Immutable MIDI output note and its originating input-note ID. */
@Serializable
data class HarmonyFittedMelodyNote(
    val noteId: String,
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long,
    val eligibility: MelodyHarmonyEligibility
) {
    init {
        require(NOTE_ID.matches(noteId) && pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) {
            "Harmony-fitted melody note is invalid"
        }
    }
}

/** Hash-bound, replayable QP-006 evidence for one harmony-fitted occurrence candidate. */
@Serializable
data class MelodyHarmonyFitReport(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val status: MelodyHarmonyFitStatus,
    val context: MelodyHarmonyFitContext,
    val input: MelodyPreparationArtifactReference,
    val monophonicPreparationReport: WorkflowArtifactReference,
    val output: MelodyPreparationArtifactReference? = null,
    val gapPolicy: MelodyHarmonyGapPolicy,
    val noteDecisions: List<MelodyHarmonyFitNoteDecision>,
    val boundaries: List<MelodyHarmonyFitBoundaryEvidence>,
    val outputNotes: List<HarmonyFittedMelodyNote>,
    /** Derived only after the complete deterministic output passes the eligibility check. */
    val protectedAnchorNoteIds: List<String>,
    val issues: List<MelodyHarmonyFitIssue>
) {
    /** Validate report version, ordering, lineage, output eligibility, and completed/blocked forms. */
    fun requireValid() {
        context.requireValid()
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION && input.ppq == context.ppq &&
            noteDecisions.map(MelodyHarmonyFitNoteDecision::noteId).distinct().size == noteDecisions.size &&
            noteDecisions == noteDecisions.sortedBy(MelodyHarmonyFitNoteDecision::noteId) &&
            boundaries == boundaries.sortedWith(compareBy<MelodyHarmonyFitBoundaryEvidence> { it.tick }.thenBy { it.kinds.joinToString() }) &&
            outputNotes == outputNotes.sortedWith(OUTPUT_NOTE_ORDER) && outputNotes.zipWithNext().all { (left, right) -> left.endTick <= right.startTick } &&
            protectedAnchorNoteIds == protectedAnchorNoteIds.distinct().sorted() &&
            issues == issues.sortedWith(ISSUE_ORDER)) { "Harmony-fit report is invalid" }
        when (status) {
            MelodyHarmonyFitStatus.COMPLETED -> {
                require(output != null && output.noteCount == outputNotes.size && outputNotes.isNotEmpty() && issues.isEmpty() &&
                    outputNotes.map(HarmonyFittedMelodyNote::noteId) == noteDecisions.filter { it.after != null }.map(MelodyHarmonyFitNoteDecision::noteId).sorted() &&
                    protectedAnchorNoteIds.all { id -> outputNotes.any { it.noteId == id } }) { "Completed harmony-fit report is incomplete" }
            }
            MelodyHarmonyFitStatus.BLOCKED -> require(output == null && outputNotes.isEmpty() && protectedAnchorNoteIds.isEmpty() && issues.isNotEmpty()) {
                "Blocked harmony-fit report is invalid"
            }
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val PROCESSOR_VERSION = "2"
        private val OUTPUT_NOTE_ORDER = compareBy<HarmonyFittedMelodyNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.noteId }
        private val ISSUE_ORDER = compareBy<MelodyHarmonyFitIssue> { it.kind.ordinal }.thenBy { it.noteId.orEmpty() }.thenBy { it.boundaryTick ?: Long.MAX_VALUE }
    }
}

/** An immutable QP-006 candidate plus the report that binds it to authority and QP-005 input. */
data class MelodyHarmonyFitArtifact(
    val midi: MelodyPreparationArtifactReference,
    val report: WorkflowArtifactReference,
    val fitting: MelodyHarmonyFitReport
)

/** Content-addressed locations for harmony-fitted occurrence candidates and their evidence. */
object MelodyHarmonyFitArtifactPaths {
    /** Return the output MIDI location for one part/occurrence/context combination. */
    fun midi(partId: String, occurrenceId: String, contextSha256: String): String =
        "midi/harmony-fit/${identifier(partId)}/${identifier(occurrenceId)}/${context(contextSha256)}.mid"

    /** Return the sidecar location for one part/occurrence/context combination. */
    fun report(partId: String, occurrenceId: String, contextSha256: String): String =
        "analysis/harmony-fit/${identifier(partId)}/${identifier(occurrenceId)}/${context(contextSha256)}.json"

    /** Validate one path-safe part or occurrence identifier before incorporating it into a candidate path. */
    private fun identifier(value: String): String {
        require(IDENTIFIER.matches(value)) { "Harmony-fit identifier is invalid" }
        return value
    }

    /** Validate the fixed SHA-256 path segment used to separate immutable candidates. */
    private fun context(value: String): String {
        require(HASH.matches(value)) { "Harmony-fit context fingerprint is invalid" }
        return value
    }
}

/**
 * Fits a QP-005 controller-materialized monophonic melody to one authoritative
 * occurrence timeline. It writes a new candidate only after every note passes
 * the versioned eligibility policy; selected MIDI and QP-005 evidence remain immutable.
 */
class MidiHarmonyFitter {
    /** Prepare or verify the immutable QP-006 candidate for [request]. */
    fun fit(request: MelodyHarmonyFitRequest): MelodyHarmonyFitArtifact {
        request.requireValid()
        val root = request.root.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Harmony-fit project root is missing" }
        val inputPath = verified(root, request.input.path, request.input.sha256, "Harmony-fit input")
        verifyMonophonicPreparation(root, request.monophonicPreparationReport, request.input, request.context.partId)
        val sequence = try { MidiSystem.getSequence(inputPath.toFile()) }
        catch (error: Exception) { throw IllegalArgumentException("Harmony-fit input is malformed", error) }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == request.context.ppq &&
            sequence.tickLength == request.context.harmonicSpans.last().localEndTick) {
            "Harmony-fit input does not match the occurrence-local authority timeline"
        }
        val inputNotes = readMonophonicNotes(sequence)
        require(inputNotes.size == request.input.noteCount && inputNotes.isNotEmpty()) { "Harmony-fit input note evidence is stale" }
        val contextHash = contextHash(request)
        val outputRelative = MelodyHarmonyFitArtifactPaths.midi(request.context.partId, request.context.occurrenceId, contextHash)
        val reportRelative = MelodyHarmonyFitArtifactPaths.report(request.context.partId, request.context.occurrenceId, contextHash)
        val outputPath = root.resolve(outputRelative).normalize()
        val reportPath = root.resolve(reportRelative).normalize()
        require(outputPath.startsWith(root) && reportPath.startsWith(root)) { "Harmony-fit output escapes the project root" }
        val policy = gapPolicy(request.context)
        val result = repair(inputNotes, request.context, policy)
        if (result.issues.isNotEmpty()) {
            val report = MelodyHarmonyFitReport(
                status = MelodyHarmonyFitStatus.BLOCKED, context = request.context, input = request.input,
                monophonicPreparationReport = request.monophonicPreparationReport, gapPolicy = policy,
                noteDecisions = result.decisions, boundaries = result.boundaries, outputNotes = emptyList(),
                protectedAnchorNoteIds = emptyList(), issues = result.issues
            ).also(MelodyHarmonyFitReport::requireValid)
            publishText(reportPath, JSON.encodeToString(MelodyHarmonyFitReport.serializer(), report))
            throw IllegalArgumentException("Harmony fitting is blocked; inspect $reportRelative")
        }
        val outputNotes = result.notes.map { note -> HarmonyFittedMelodyNote(note.id, note.pitch, note.velocity, note.startTick, note.endTick, requireNotNull(note.eligibility)) }
        require(outputNotes.all { isEligible(it, outputNotes, request.context) }) { "Harmony-fit output violates the versioned eligibility rule" }
        val anchors = deriveProtectedAnchors(outputNotes, request.context)
        Files.createDirectories(requireNotNull(outputPath.parent))
        val temporary = Files.createTempFile(outputPath.parent, ".harmony-fit-", ".mid")
        try {
            write(sequence, temporary, outputNotes)
            val output = MelodyPreparationArtifactReference(outputRelative, sha256(temporary), sequence.resolution, outputNotes.size)
            val report = MelodyHarmonyFitReport(
                status = MelodyHarmonyFitStatus.COMPLETED, context = request.context, input = request.input,
                monophonicPreparationReport = request.monophonicPreparationReport, output = output, gapPolicy = policy,
                noteDecisions = result.decisions, boundaries = result.boundaries, outputNotes = outputNotes,
                protectedAnchorNoteIds = anchors, issues = emptyList()
            ).also(MelodyHarmonyFitReport::requireValid)
            publishOrVerify(temporary, outputPath, output.sha256, "harmony-fitted MIDI")
            publishText(reportPath, JSON.encodeToString(MelodyHarmonyFitReport.serializer(), report))
            return MelodyHarmonyFitArtifact(output, WorkflowArtifactReference(reportRelative, sha256(reportPath)), report)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Repair pitch and boundary tails in a deterministic order, retaining a durable blocking result when needed. */
    private fun repair(input: List<InputNote>, context: MelodyHarmonyFitContext, policy: MelodyHarmonyGapPolicy): RepairResult {
        val notes = input.map { Candidate(it.id, it.pitch, it.velocity, it.startTick, it.endTick) }.toMutableList()
        val issues = mutableListOf<MelodyHarmonyFitIssue>()
        val pitchReasons = notes.associate { it.id to mutableListOf<MelodyHarmonyFitReason>() }
        notes.indices.forEach { index ->
            val note = notes[index]
            val span = spanAt(context, note.startTick)
            if (note.pitch.mod(12) in span.chordTones()) {
                pitchReasons.getValue(note.id) += if (note.pitch.mod(12) in scale(context)) MelodyHarmonyFitReason.ALREADY_CHORD_TONE
                else MelodyHarmonyFitReason.AUTHORIZED_CHROMATIC_CHORD_TONE
            } else if (isPassingTone(note, index, notes, context)) {
                pitchReasons.getValue(note.id) += MelodyHarmonyFitReason.PRESERVED_WEAK_SCALE_PASSING_TONE
            } else {
                val nextDifferentInput = input.drop(index + 1).firstOrNull { following -> following.pitch != note.pitch }
                when (val choice = nearestChordTone(note, notes.getOrNull(index - 1), input.getOrNull(index - 1), nextDifferentInput, span)) {
                    is PitchChoice.Selected -> {
                        if (choice.distance > MAX_PITCH_MOVEMENT) issues += MelodyHarmonyFitIssue(
                            MelodyHarmonyFitIssueKind.EXCESSIVE_PITCH_MOVEMENT, note.id, observed = choice.distance.toLong(), limit = MAX_PITCH_MOVEMENT.toLong()
                        )
                        else {
                            note.pitch = choice.pitch
                            pitchReasons.getValue(note.id) += MelodyHarmonyFitReason.REPAIRED_TO_ACTIVE_CHORD_TONE
                        }
                    }
                    PitchChoice.Ambiguous -> issues += MelodyHarmonyFitIssue(MelodyHarmonyFitIssueKind.AMBIGUOUS_NEAREST_PITCH, note.id)
                }
            }
        }
        val boundaries = evaluateBoundaries(notes, context, policy, pitchReasons, issues)
        val decisions = decisions(input, notes, context, pitchReasons, boundaries, issues)
        // The edit budget protects recognizable melody from pitch rewrites.
        // Required chord-boundary releases and sub-sixteenth transcription
        // ornaments are not melodic replacements, so neither may make an
        // otherwise bounded chord-tone correction fail publication.
        val minimumRecognizableDuration = beatTicks(context) / 4L
        val pitchChanges = decisions.count { decision ->
            decision.after?.pitch != decision.before.pitch &&
                decision.before.endTick - decision.before.startTick >= minimumRecognizableDuration
        }
        val maximumEdits = maxOf(1, (input.size + 1) / MAX_EDIT_DIVISOR)
        if (pitchChanges > maximumEdits) issues += MelodyHarmonyFitIssue(
            MelodyHarmonyFitIssueKind.EXCESSIVE_EDIT_BUDGET, observed = pitchChanges.toLong(), limit = maximumEdits.toLong(), noteId = notes.first().id
        )
        notes.forEach { note -> note.eligibility = eligibility(note, notes, context, boundaries) }
        val output = notes.map { note -> HarmonyFittedMelodyNote(note.id, note.pitch, note.velocity, note.startTick, note.endTick, requireNotNull(note.eligibility)) }
        if (issues.isEmpty() && output.any { !isEligible(it, output, context) }) {
            issues += MelodyHarmonyFitIssue(MelodyHarmonyFitIssueKind.INVALID_OUTPUT_ELIGIBILITY, output.first { !isEligible(it, output, context) }.noteId)
        }
        return RepairResult(notes, decisions, boundaries, issues.distinct().sortedWith(ISSUE_ORDER))
    }

    /** Evaluate every span start and occurrence end after the QP-005 sustain interpretation has become written timing. */
    private fun evaluateBoundaries(
        notes: List<Candidate>,
        context: MelodyHarmonyFitContext,
        policy: MelodyHarmonyGapPolicy,
        reasons: Map<String, MutableList<MelodyHarmonyFitReason>>,
        issues: MutableList<MelodyHarmonyFitIssue>
    ): List<MelodyHarmonyFitBoundaryEvidence> {
        val boundaries = linkedMapOf<Long, MutableSet<MelodyHarmonyBoundaryKind>>()
        context.harmonicSpans.drop(1).forEach { span -> boundaries.getOrPut(span.localStartTick) { linkedSetOf() }.addAll(listOf(MelodyHarmonyBoundaryKind.CHORD, MelodyHarmonyBoundaryKind.MEASURE)) }
        boundaries.getOrPut(context.harmonicSpans.last().localEndTick) { linkedSetOf() }.add(MelodyHarmonyBoundaryKind.OCCURRENCE_END)
        return boundaries.entries.sortedBy(Map.Entry<Long, MutableSet<MelodyHarmonyBoundaryKind>>::key).map { (boundary, kinds) ->
            val carried = notes.singleOrNull { it.startTick < boundary && it.endTick > boundary }
            if (carried == null) return@map MelodyHarmonyFitBoundaryEvidence(
                boundary, kinds.sortedBy(MelodyHarmonyBoundaryKind::ordinal), decision = MelodyHarmonyBoundaryDecision.NO_CARRIED_NOTE,
                controllerBehavior = MelodyHarmonyControllerBehavior.MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT
            )
            val incoming = context.harmonicSpans.singleOrNull { boundary in it.localStartTick until it.localEndTick }
            val suspension = incoming?.let { suspensionResolution(carried, notes, boundary, it, context) }
            when {
                incoming != null && carried.pitch.mod(12) in spanAt(context, boundary - 1).chordTones() && carried.pitch.mod(12) in incoming.chordTones() -> {
                    reasons.getValue(carried.id) += MelodyHarmonyFitReason.PRESERVED_COMMON_TONE_TIE
                    MelodyHarmonyFitBoundaryEvidence(boundary, kinds.sortedBy(MelodyHarmonyBoundaryKind::ordinal), carried.id,
                        MelodyHarmonyBoundaryDecision.PRESERVED_COMMON_TONE_TIE, incoming.rootChromatic,
                        controllerBehavior = MelodyHarmonyControllerBehavior.MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT)
                }
                incoming != null && suspension != null -> {
                    val resolution = requireNotNull(suspension)
                    reasons.getValue(carried.id) += MelodyHarmonyFitReason.PRESERVED_DELIBERATE_SUSPENSION
                    MelodyHarmonyFitBoundaryEvidence(boundary, kinds.sortedBy(MelodyHarmonyBoundaryKind::ordinal), carried.id,
                        MelodyHarmonyBoundaryDecision.PRESERVED_DELIBERATE_SUSPENSION, incoming.rootChromatic, resolution.id,
                        MelodyHarmonyControllerBehavior.MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT)
                }
                else -> {
                    val shortenedEnd = boundary - policy.derivedGapTicks
                    if (shortenedEnd <= carried.startTick) {
                        issues += MelodyHarmonyFitIssue(MelodyHarmonyFitIssueKind.UNSATISFIABLE_BOUNDARY_GAP, carried.id, boundary, boundary - carried.startTick, policy.derivedGapTicks)
                    } else {
                        carried.endTick = minOf(carried.endTick, shortenedEnd)
                        reasons.getValue(carried.id) += MelodyHarmonyFitReason.SHORTENED_INCOMPATIBLE_BOUNDARY
                    }
                    MelodyHarmonyFitBoundaryEvidence(boundary, kinds.sortedBy(MelodyHarmonyBoundaryKind::ordinal), carried.id,
                        MelodyHarmonyBoundaryDecision.SHORTENED_INCOMPATIBLE_NOTE, incoming?.rootChromatic,
                        controllerBehavior = MelodyHarmonyControllerBehavior.MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT)
                }
            }
        }
    }

    /** Retain a suspension only when outgoing eligibility, stepwise resolution, and controller materialization are all explicit. */
    private fun suspensionResolution(
        carried: Candidate,
        notes: List<Candidate>,
        boundary: Long,
        incoming: MelodyHarmonyFitSpan,
        context: MelodyHarmonyFitContext
    ): Candidate? {
        if (carried.pitch.mod(12) !in spanAt(context, boundary - 1).chordTones()) return null
        val beat = beatTicks(context)
        return notes.firstOrNull { next -> next.startTick >= carried.endTick && next.startTick in boundary..boundary + beat &&
            abs(next.pitch - carried.pitch) in 1..2 && next.pitch.mod(12) in incoming.chordTones() }
    }

    /** Turn final candidates into complete, reason-coded before/after evidence. */
    private fun decisions(
        input: List<InputNote>,
        notes: List<Candidate>,
        context: MelodyHarmonyFitContext,
        reasons: Map<String, MutableList<MelodyHarmonyFitReason>>,
        boundaries: List<MelodyHarmonyFitBoundaryEvidence>,
        issues: List<MelodyHarmonyFitIssue>
    ): List<MelodyHarmonyFitNoteDecision> = input.zip(notes).map { (before, after) ->
        val beforeValues = MelodyHarmonyFitNoteValues(before.pitch, before.velocity, before.startTick, before.endTick)
        if (issues.any { it.noteId == after.id }) MelodyHarmonyFitNoteDecision(after.id, beforeValues)
        else MelodyHarmonyFitNoteDecision(after.id, beforeValues, MelodyHarmonyFitNoteValues(after.pitch, after.velocity, after.startTick, after.endTick),
            eligibility(after, notes, context, boundaries), reasons.getValue(after.id).distinct().sortedBy(MelodyHarmonyFitReason::ordinal))
    }.sortedBy(MelodyHarmonyFitNoteDecision::noteId)

    /** Classify the final permission for a note after cross-boundary evidence has been emitted. */
    private fun eligibility(
        note: Candidate,
        notes: List<Candidate>,
        context: MelodyHarmonyFitContext,
        boundaries: List<MelodyHarmonyFitBoundaryEvidence>
    ): MelodyHarmonyEligibility {
        val boundary = boundaries.firstOrNull { it.carriedNoteId == note.id && it.decision == MelodyHarmonyBoundaryDecision.PRESERVED_DELIBERATE_SUSPENSION }
        if (boundary != null) return MelodyHarmonyEligibility.DELIBERATE_SUSPENSION
        if (boundaries.any { it.carriedNoteId == note.id && it.decision == MelodyHarmonyBoundaryDecision.PRESERVED_COMMON_TONE_TIE }) return MelodyHarmonyEligibility.COMMON_TONE_TIE
        val index = notes.indexOf(note)
        return if (isPassingTone(note, index, notes, context)) MelodyHarmonyEligibility.WEAK_SCALE_PASSING_TONE else MelodyHarmonyEligibility.CHORD_TONE
    }

    /** Enforce the complete policy independently of the repair path before output publication. */
    private fun isEligible(note: HarmonyFittedMelodyNote, all: List<HarmonyFittedMelodyNote>, context: MelodyHarmonyFitContext): Boolean {
        val spans = context.harmonicSpans.filter { it.localStartTick < note.endTick && note.startTick < it.localEndTick }
        if (spans.isEmpty()) return false
        val primary = spanAt(context, note.startTick)
        return when (note.eligibility) {
            MelodyHarmonyEligibility.CHORD_TONE -> note.pitch.mod(12) in primary.chordTones() &&
                spans.filter { it != primary }.all { note.pitch.mod(12) in it.chordTones() }
            MelodyHarmonyEligibility.WEAK_SCALE_PASSING_TONE -> isPassingTone(
                Candidate(note.noteId, note.pitch, note.velocity, note.startTick, note.endTick),
                all.indexOfFirst { it.noteId == note.noteId }, all.map { Candidate(it.noteId, it.pitch, it.velocity, it.startTick, it.endTick) }, context
            ) && note.endTick <= primary.localEndTick
            MelodyHarmonyEligibility.COMMON_TONE_TIE -> spans.all { note.pitch.mod(12) in it.chordTones() }
            MelodyHarmonyEligibility.DELIBERATE_SUSPENSION -> {
                val incoming = spans.last()
                note.pitch.mod(12) in primary.chordTones() && all.any { next -> next.startTick >= note.endTick && next.startTick <= incoming.localStartTick + beatTicks(context) &&
                    abs(next.pitch - note.pitch) in 1..2 && next.pitch.mod(12) in incoming.chordTones() }
            }
        }
    }

    /** Permit only a short, weak, project-scale motion resolving stepwise to the active chord by the next beat. */
    private fun isPassingTone(note: Candidate, index: Int, notes: List<Candidate>, context: MelodyHarmonyFitContext): Boolean {
        if (index !in notes.indices || note.pitch.mod(12) !in scale(context)) return false
        val span = spanAt(context, note.startTick)
        if (note.pitch.mod(12) in span.chordTones() || note.endTick - note.startTick >= beatTicks(context) / 2 || note.startTick % beatTicks(context) == 0L) return false
        val next = notes.drop(index + 1).firstOrNull { it.startTick >= note.endTick } ?: return false
        return next.startTick <= note.startTick + beatTicks(context) && abs(next.pitch - note.pitch) in 1..2 &&
            next.pitch.mod(12) in spanAt(context, next.startTick).chordTones()
    }

    /** Select the nearest active chord tone, using explicit local contour only to resolve an otherwise equal movement. */
    private fun nearestChordTone(
        note: Candidate,
        previous: Candidate?,
        previousInput: InputNote?,
        nextDifferentInput: InputNote?,
        span: MelodyHarmonyFitSpan
    ): PitchChoice {
        val candidates = (0..127).filter { it.mod(12) in span.chordTones() }
        val minimum = candidates.minOf { abs(it - note.pitch) }
        var nearest = candidates.filter { abs(it - note.pitch) == minimum }
        if (nearest.size > 1 && previous != null && previousInput != null) {
            val originalDirection = (note.pitch - previousInput.pitch).compareTo(0)
            val directed = nearest.filter { (it - previous.pitch).compareTo(0) == originalDirection }
            if (directed.isNotEmpty()) nearest = directed
            val continuity = nearest.minOf { abs(it - previous.pitch) }
            nearest = nearest.filter { abs(it - previous.pitch) == continuity }
        }
        if (nearest.size > 1 && nextDifferentInput != null) {
            val continuity = nearest.minOf { abs(it - nextDifferentInput.pitch) }
            nearest = nearest.filter { abs(it - nextDifferentInput.pitch) == continuity }
        }
        return if (nearest.size == 1) PitchChoice.Selected(nearest.single(), minimum) else PitchChoice.Ambiguous
    }

    /** Derive post-validation anchors with the same phrase/held-note principles used by melody identity. */
    private fun deriveProtectedAnchors(notes: List<HarmonyFittedMelodyNote>, context: MelodyHarmonyFitContext): List<String> {
        val beat = beatTicks(context)
        val anchors = linkedSetOf<String>()
        anchors += notes.first().noteId
        anchors += notes.last().noteId
        notes.filter { it.endTick - it.startTick >= beat }.forEach { anchors += it.noteId }
        notes.filter { it.endTick - it.startTick >= beat / 2 }.let { held ->
            held.minByOrNull(HarmonyFittedMelodyNote::pitch)?.let { anchors += it.noteId }
            held.maxByOrNull(HarmonyFittedMelodyNote::pitch)?.let { anchors += it.noteId }
        }
        return anchors.sorted()
    }

    /** Produce a bounded release gap from the declared tempo and PPQ, never from a raw seconds subtraction. */
    private fun gapPolicy(context: MelodyHarmonyFitContext): MelodyHarmonyGapPolicy {
        val upperReferenceTicks = (context.ppq.toDouble() * context.tempoBpm * 50.0 / 60_000.0).roundToLong().coerceAtLeast(1L)
        val beatCap = (beatTicks(context) / 16L).coerceAtLeast(1L)
        return MelodyHarmonyGapPolicy(tempoBpm = context.tempoBpm, ppq = context.ppq, derivedGapTicks = minOf(upperReferenceTicks, beatCap))
    }

    /** Parse the controller-free QP-005 output and reject any hidden polyphony or malformed pair. */
    private fun readMonophonicNotes(sequence: Sequence): List<InputNote> {
        require(sequence.tracks.size == 1) { "Harmony-fit input must contain exactly one prepared melody track" }
        val active = mutableMapOf<Int, ArrayDeque<Pair<Long, Int>>>()
        val notes = mutableListOf<InputNote>()
        var ordinal = 0
        sequence.tracks.single().let { track ->
            (0 until track.size()).forEach { index ->
                val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
                require(message.command != ShortMessage.CONTROL_CHANGE) { "Harmony-fit input must not retain controller state" }
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                        require(message.channel == MELODY_CHANNEL) { "Harmony-fit input must use the canonical melody channel" }
                        active.getOrPut(message.data1) { ArrayDeque() }.addLast(event.tick to message.data2)
                    }
                    message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                        val queue = requireNotNull(active[message.data1]) { "Harmony-fit input has an unmatched note-off" }
                        val start = if (queue.isEmpty()) throw IllegalArgumentException("Harmony-fit input has an unmatched note-off") else queue.removeFirst()
                        if (queue.isEmpty()) active.remove(message.data1)
                        require(event.tick > start.first) { "Harmony-fit input has a non-positive note" }
                        notes += InputNote("n-${ordinal++.toString().padStart(5, '0')}", message.data1, start.second, start.first, event.tick)
                    }
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Harmony-fit input has an unclosed note" }
        val ordered = notes.sortedWith(INPUT_ORDER)
        require(ordered.zipWithNext().all { (left, right) -> left.endTick <= right.startTick }) { "Harmony-fit input is not monophonic" }
        return ordered.mapIndexed { index, note -> note.copy(id = "n-${index.toString().padStart(5, '0')}") }
    }

    /** Write only canonical one-track note pairs; controllers were already materialized by QP-005. */
    private fun write(input: Sequence, target: Path, notes: List<HarmonyFittedMelodyNote>) {
        val output = Sequence(Sequence.PPQ, input.resolution)
        val track = output.createTrack()
        notes.forEach { note ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, MELODY_CHANNEL, note.pitch, note.velocity), note.startTick))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, MELODY_CHANNEL, note.pitch, 0), note.endTick))
        }
        track.add(MidiEvent(MetaMessage().also { it.setMessage(END_OF_TRACK, byteArrayOf(), 0) }, input.tickLength))
        MidiSystem.write(output, 1, target.toFile())
        val reparsed = MidiSystem.getSequence(target.toFile())
        require(reparsed.resolution == input.resolution && reparsed.tickLength == input.tickLength && readMonophonicNotes(reparsed).size == notes.size) {
            "Harmony-fit output violates its one-track timing contract"
        }
    }

    /** Ensure the QP-005 report, rather than filename convention, binds this fitter input. */
    private fun verifyMonophonicPreparation(root: Path, reference: WorkflowArtifactReference, input: MelodyPreparationArtifactReference, partId: String) {
        val reportPath = verified(root, reference.file, reference.sha256, "Monophonic melody-preparation report")
        val report = try { JSON.decodeFromString(MonophonicMelodyPreparationReport.serializer(), Files.readString(reportPath)) }
        catch (error: Exception) { throw IllegalArgumentException("Monophonic melody-preparation report is malformed", error) }
        report.requireValid()
        require(report.status == MelodyPreparationStatus.COMPLETED && report.partId == partId && report.output == input) {
            "Monophonic melody-preparation report does not bind the harmony-fit input"
        }
    }

    /** Resolve a content-addressed project-relative artifact without accepting an escaping or stale file. */
    private fun verified(root: Path, relative: String, expectedSha256: String, label: String): Path {
        val candidate = try { Path.of(relative) } catch (error: Exception) { throw IllegalArgumentException("$label path is invalid", error) }
        require(relative.isNotBlank() && !candidate.isAbsolute && candidate.none { it.toString() == ".." }) { "$label path must be project-relative" }
        val path = root.resolve(candidate).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath()) && sha256(path) == expectedSha256) {
            "$label is missing or stale"
        }
        return path
    }

    /** Serialize a canonical request fingerprint so every authority or source change gets a distinct candidate. */
    private fun contextHash(request: MelodyHarmonyFitRequest): String = sha256Text(buildString {
        append(MelodyHarmonyFitReport.PROCESSOR_VERSION).append('|').append(request.input.sha256).append('|')
        append(request.monophonicPreparationReport.sha256).append('|').append(request.context.authorityContextSha256).append('|')
        append(request.context.partId).append('|').append(request.context.occurrenceId).append('|').append(request.context.projectKey.displayName).append('|')
        append(request.context.tempoBpm).append('|').append(request.context.meterNumerator).append('/').append(request.context.meterDenominator).append('|').append(request.context.ppq)
        request.context.harmonicSpans.forEach { span -> append('|').append(span.bar).append(':').append(span.localStartTick).append('-').append(span.localEndTick).append(':').append(span.rootChromatic).append(':').append(span.quality.name) }
    })

    /** Return the one half-open authoritative span containing a local MIDI tick. */
    private fun spanAt(context: MelodyHarmonyFitContext, tick: Long): MelodyHarmonyFitSpan =
        requireNotNull(context.harmonicSpans.singleOrNull { tick in it.localStartTick until it.localEndTick }) { "No authoritative harmony exists at local tick $tick" }

    /** Return project scale pitch classes without allowing inferred source harmony to participate. */
    private fun scale(context: MelodyHarmonyFitContext): Set<Int> = context.projectKey.scalePitchClasses().map { it.chromatic }.toSet()

    /** Return exact beat duration for the declared PPQ and meter denominator. */
    private fun beatTicks(context: MelodyHarmonyFitContext): Long = context.ppq.toLong() * 4L / context.meterDenominator

    /** Preserve an existing candidate only when byte-identical, preventing known-good MIDI replacement. */
    private fun publishOrVerify(temporary: Path, target: Path, expectedSha256: String, label: String) {
        if (Files.exists(target)) {
            require(Files.isRegularFile(target) && sha256(target) == expectedSha256) { "Existing $label differs; preserving the known-good candidate" }
            return
        }
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
    }

    /** Preserve report evidence once published, requiring exact textual replay on later runs. */
    private fun publishText(path: Path, text: String) {
        Files.createDirectories(requireNotNull(path.parent))
        if (Files.exists(path)) {
            require(Files.readString(path) == text) { "Existing harmony-fit report differs; preserving the known-good evidence" }
            return
        }
        val temporary = Files.createTempFile(path.parent, ".harmony-fit-", ".json")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path) }
        } finally { Files.deleteIfExists(temporary) }
    }

    /** Compute a SHA-256 fingerprint without accepting a caller-controlled hash. */
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    /** Fingerprint a canonical request-string payload using UTF-8 before it becomes a path component. */
    private fun sha256Text(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private sealed interface PitchChoice {
        data class Selected(val pitch: Int, val distance: Int) : PitchChoice
        data object Ambiguous : PitchChoice
    }
    private data class InputNote(val id: String, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)
    private data class Candidate(val id: String, var pitch: Int, val velocity: Int, val startTick: Long, var endTick: Long, var eligibility: MelodyHarmonyEligibility? = null)
    private data class RepairResult(val notes: List<Candidate>, val decisions: List<MelodyHarmonyFitNoteDecision>, val boundaries: List<MelodyHarmonyFitBoundaryEvidence>, val issues: List<MelodyHarmonyFitIssue>)

    private companion object {
        const val MELODY_CHANNEL = 0
        const val END_OF_TRACK = 0x2F
        const val MAX_PITCH_MOVEMENT = 2
        /** At most half of recognizable notes may receive a bounded pitch repair. */
        const val MAX_EDIT_DIVISOR = 2
        val INPUT_ORDER = compareBy<InputNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.id }
        val ISSUE_ORDER = compareBy<MelodyHarmonyFitIssue> { it.kind.ordinal }.thenBy { it.noteId.orEmpty() }.thenBy { it.boundaryTick ?: Long.MAX_VALUE }
        val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false; prettyPrint = true }
    }
}

private val HASH = Regex("[0-9a-f]{64}")
private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")
private val NOTE_ID = Regex("n-[0-9]{5}")
