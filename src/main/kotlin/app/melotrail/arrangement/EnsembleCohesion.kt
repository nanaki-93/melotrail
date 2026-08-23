package app.melotrail.arrangement

import app.melotrail.profile.BundledCompositionProfileCatalog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.roundToLong

@Serializable
data class EnsembleCohesionModelIdentity(val provider: String, val model: String, val sha256: String) {
    init { require(provider.matches(Regex("[a-z0-9_-]{1,40}")) && model.length in 1..80 && sha256.matches(Regex("[0-9a-f]{64}"))) { "Cohesion model identity is invalid" } }
    companion object { val DETERMINISTIC = EnsembleCohesionModelIdentity("deterministic", "cohesion-boundary-v6", "0".repeat(64)) }
}

@Serializable enum class EnsembleCohesionEnhancementIntensity { SUBTLE, BALANCED, CREATIVE }

data class EnsembleCohesionEnhancementPolicy(val melodyPercent: Int, val rolePercent: Int, val timingBeatFraction: Int, val velocityDelta: Int) {
    companion object {
        fun forIntensity(value: EnsembleCohesionEnhancementIntensity) = when (value) {
            EnsembleCohesionEnhancementIntensity.SUBTLE -> EnsembleCohesionEnhancementPolicy(5, 10, 8, 8)
            EnsembleCohesionEnhancementIntensity.BALANCED -> EnsembleCohesionEnhancementPolicy(10, 20, 4, 16)
            EnsembleCohesionEnhancementIntensity.CREATIVE -> EnsembleCohesionEnhancementPolicy(15, 30, 2, 28)
        }
    }
}

/** Path-free, arrangement-aware evidence for every adjacent saved occurrence. */
@Serializable data class EnsembleCohesionInput(
    val version: Int = VERSION,
    val inputHash: String,
    val structureSha256: String,
    val arrangementSha256: String,
    val contextSha256: String,
    val supportedInstruments: List<String>,
    val boundaries: List<TransitionContext>,
    val intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED,
    val occurrences: List<SongOccurrenceEvidence> = emptyList(),
    val generatedRoles: List<GeneratedRoleEvidence> = emptyList()
) { companion object { const val VERSION = 6 } }

@Serializable data class SongOccurrenceEvidence(val instanceId: String, val evidence: TransitionMusicalEvidence)
@Serializable data class GeneratedRoleEvidence(val role: String, val sourceHash: String, val ppq: Int, val durationTicks: Long, val notes: List<CohesionMelodyNote>)

/**
 * The authoritative context for one adjacent structure boundary.
 *
 * It deliberately combines only persisted source analysis with the approved
 * arrangement evidence for those two occurrences.  This prevents a transition
 * planner from widening an edit into a whole-song rewrite.
 */
@Serializable data class TransitionContext(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val outgoing: TransitionMusicalEvidence,
    val incoming: TransitionMusicalEvidence,
    val allowedRoleActions: List<TransitionRoleAction>,
    val transitionPolicy: TransitionPolicyEvidence
)

@Serializable data class TransitionMusicalEvidence(
    val partId: String,
    /** Hash of selected immutable MIDI; Ensemble Cohesion publishes only derived occurrence MIDI. */
    val sourceHash: String,
    val analysisHash: String,
    val ppq: Int,
    val durationTicks: Long,
    val key: MidiKey?,
    val chords: List<MidiChord>,
    val tempo: MidiTempoChange,
    val meter: MidiTimeSignature,
    val energy: Double,
    val boundary: TransitionBoundarySummary,
    val arrangement: TransitionArrangementEvidence,
    val melodyNotes: List<CohesionMelodyNote> = emptyList()
)

@Serializable data class CohesionMelodyNote(val id: String, val channel: Int, val pitch: Int, val velocity: Int, val startTick: Long, val endTick: Long)

@Serializable data class TransitionBoundarySummary(val startsWithSound: Boolean, val endsWithSound: Boolean, val firstNoteTick: Long?, val lastNoteEndTick: Long?)
@Serializable data class TransitionArrangementEvidence(val occurrenceHash: String, val purpose: SongSectionPurpose, val instruments: List<TransitionInstrumentEvidence>, val variationFingerprint: String)
@Serializable data class TransitionInstrumentEvidence(val instrument: String, val role: String, val density: Double?)
@Serializable data class TransitionPolicyEvidence(val profileId: String, val moodId: String, val policySha256: String, val allowedActions: List<TransitionRoleAction>)

@Serializable data class EnsembleCohesionPlan(
    val version: Int = EnsembleCohesionInput.VERSION,
    val inputHash: String,
    val arrangementSha256: String,
    val contextSha256: String,
    val model: EnsembleCohesionModelIdentity,
    val boundaries: List<TransitionBridgePlan>,
    val intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED,
    /** Code-owned result; untrusted planners cannot report their own validation. */
    val validation: EnsembleCohesionValidationReport = EnsembleCohesionValidationReport(),
    /** One code-owned approval promotes the exact reviewed boundary candidate. */
    val approval: EnsembleCohesionApproval = EnsembleCohesionApproval.DRAFT
)
@Serializable data class EnsembleCohesionValidationReport(val errors: List<String> = emptyList()) { val valid: Boolean get() = errors.isEmpty() }
@Serializable enum class EnsembleCohesionApproval { DRAFT, APPROVED }

/**
 * Compact, path-free evidence supplied to the model. Identity values are
 * intentionally absent: they are application-owned and bound after parsing.
 */
@Serializable private data class EnsembleCohesionModelInput(
    val supportedInstruments: List<String>,
    val boundaries: List<EnsembleCohesionBoundaryEvidence>,
    val intensity: EnsembleCohesionEnhancementIntensity
)
@Serializable private data class EnsembleCohesionBoundaryEvidence(
    val outgoing: EnsembleCohesionMusicalSummary,
    val incoming: EnsembleCohesionMusicalSummary,
    val allowedRoleActions: List<TransitionRoleAction>
)
@Serializable private data class EnsembleCohesionMusicalSummary(
    val key: MidiKey?,
    val boundaryChord: String?,
    val tempoBpm: Double,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val energy: Double,
    val startsWithSound: Boolean,
    val endsWithSound: Boolean,
    val purpose: SongSectionPurpose,
    val instruments: List<String>,
    val editableNotes: List<CohesionMelodyNote>
)

@Serializable private enum class CohesionMelodySide { OUTGOING, INCOMING }
@Serializable enum class CohesionMelodyEditKind { ADD_NOTE, REMOVE_NOTE, SET_PITCH, SET_START, SET_DURATION, SET_VELOCITY }
@Serializable private data class CohesionMelodyModelEdit(
    val side: CohesionMelodySide,
    val kind: CohesionMelodyEditKind,
    val noteId: String,
    val value: Long = 0,
    val pitch: Int? = null,
    val velocity: Int? = null,
    val startTick: Long? = null,
    val durationTicks: Long? = null,
    val channel: Int? = null,
    val anchorNoteId: String? = null,
    val reason: String
)

@Serializable data class CohesionMelodyEdit(
    val occurrenceInstanceId: String,
    val kind: CohesionMelodyEditKind,
    val noteId: String,
    val value: Long = 0,
    val pitch: Int? = null,
    val velocity: Int? = null,
    val startTick: Long? = null,
    val durationTicks: Long? = null,
    val channel: Int? = null,
    val anchorNoteId: String? = null,
    val reason: String
)

/** The only decisions a model is allowed to make for one boundary. */
@Serializable private data class EnsembleCohesionModelDecision(
    val roleAction: TransitionRoleAction,
    val bars: Int,
    val harmonicHandoff: HarmonicHandoff,
    val rhythmicGesture: RhythmicGesture,
    val energyContour: EnergyContour,
    val rationale: String,
    val leadBeats: Int? = null,
    val tailBeats: Int = 0,
    val melodyEdits: List<CohesionMelodyModelEdit> = emptyList()
)
@Serializable private data class EnsembleCohesionModelResponse(val boundaries: List<EnsembleCohesionModelDecision>)

@Serializable data class TransitionBridgePlan(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val outgoingHash: String,
    val incomingHash: String,
    val arrangementSha256: String,
    val contextSha256: String,
    val roleAction: TransitionRoleAction,
    val bridgeType: BridgeType,
    val bars: Int,
    val instrument: String,
    val harmonicHandoff: HarmonicHandoff,
    val rhythmicGesture: RhythmicGesture,
    val energyContour: EnergyContour,
    val tempoHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val meterHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val rationale: String,
    val melodyEdits: List<CohesionMelodyEdit> = emptyList(),
    val placement: TransitionPlacement = TransitionPlacement.OVERLAY_BOUNDARY,
    val leadBeats: Int = 1,
    val tailBeats: Int = 0
)
@Serializable enum class TransitionRoleAction { DRUM_FILL, BASS_MOTION, CHORD_MOTION, SUSTAINED_TEXTURE, DYNAMICS_AUTOMATION, CONTINUITY }
@Serializable enum class BridgeType { DRUM_FILL, BASS_WALK, CHORD_MOTION, PAD_SUSTAIN, BUILD, CONTINUITY }
@Serializable enum class HarmonicHandoff { HOLD, STEP_TO_INCOMING }
@Serializable enum class RhythmicGesture { FILL, PICKUP, SUSTAIN }
@Serializable enum class EnergyContour { HOLD, RISE, FALL }
@Serializable enum class TimingHandoff { PRESERVE }
@Serializable enum class TransitionPlacement { OVERLAY_BOUNDARY }
data class EnsembleCohesionValidationResult(val errors: List<String>) { val isValid get() = errors.isEmpty() }

object EnsembleCohesionValidator {
    private val id = Regex("[A-Za-z0-9_-]{1,80}")
    private val hash = Regex("[0-9a-f]{64}")
    private val rationale = Regex("[A-Za-z0-9 ,.'-]{1,180}")
    fun validate(plan: EnsembleCohesionPlan, input: EnsembleCohesionInput): EnsembleCohesionValidationResult {
        val errors = mutableListOf<String>()
        if (input.version != EnsembleCohesionInput.VERSION || plan.version != EnsembleCohesionInput.VERSION) errors += "Unsupported transition cohesion version"
        if (!hash.matches(input.arrangementSha256) || !hash.matches(input.contextSha256)) errors += "Transition cohesion requires approved arrangement and context identities"
        if (plan.inputHash != input.inputHash || plan.arrangementSha256 != input.arrangementSha256 || plan.contextSha256 != input.contextSha256) errors += "Transition cohesion plan hashes are stale"
        if (plan.intensity != input.intensity) errors += "Cohesion enhancement intensity is stale"
        val expected = input.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }
        val actual = plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }
        if (actual != expected || actual.size != input.boundaries.size) errors += "Transition cohesion plan must contain exactly n - 1 boundaries in saved Structure order"
        if (actual.distinct().size != actual.size) errors += "Transition cohesion plan contains duplicate boundaries"
        if (input.supportedInstruments != input.supportedInstruments.distinct().sorted()) errors += "Transition cohesion supported instruments must be sorted and unique"
        val editedTargets = mutableSetOf<Pair<String, String>>()
        val editCounts = mutableMapOf<String, Int>()
        input.boundaries.zip(plan.boundaries).forEachIndexed { index, (source, bridge) ->
            val label = "Boundary ${index + 1}"
            if (!id.matches(bridge.outgoingInstanceId) || !id.matches(bridge.incomingInstanceId)) errors += "$label has an invalid occurrence ID"
            if (!hash.matches(bridge.outgoingHash) || !hash.matches(bridge.incomingHash)) errors += "$label has an invalid source hash"
            if (bridge.outgoingHash != source.outgoing.sourceHash || bridge.incomingHash != source.incoming.sourceHash) errors += "$label source hash is stale"
            if (bridge.arrangementSha256 != input.arrangementSha256 || bridge.contextSha256 != input.contextSha256) errors += "$label arrangement or context hash is stale"
            if (bridge.bars !in 1..2) errors += "$label bridge length must be one or two bars"
            if (bridge.placement != TransitionPlacement.OVERLAY_BOUNDARY || bridge.leadBeats !in 1..source.outgoing.meter.numerator || bridge.tailBeats !in 0..1) {
                errors += "$label has an invalid boundary overlay window"
            }
            if (bridge.instrument !in input.supportedInstruments) errors += "$label uses unsupported instrument '${bridge.instrument}'"
            if (bridge.roleAction !in source.allowedRoleActions || bridge.roleAction !in source.transitionPolicy.allowedActions) errors += "$label uses a disallowed role action"
            if (!rationale.matches(bridge.rationale)) errors += "$label rationale must be bounded musical text"
            if (source.outgoing.ppq != source.incoming.ppq || source.outgoing.ppq !in 1..9600) errors += "$label has incompatible PPQ timing"
            if (!tempoValid(source.outgoing.tempo) || !tempoValid(source.incoming.tempo) || !meterValid(source.outgoing.meter) || !meterValid(source.incoming.meter)) errors += "$label has invalid timing evidence"
            if (!bridgeCompatible(bridge)) errors += "$label bridge type, role action, and instrument do not match"
            if (bridge.melodyEdits.size > 8) errors += "$label exceeds the melody edit budget"
            bridge.melodyEdits.forEach { edit ->
                validateMelodyEdit(label, edit, source, errors)
                if (!editedTargets.add(edit.occurrenceInstanceId to edit.noteId)) errors += "$label conflicts with another boundary edit for '${edit.noteId}'"
                editCounts[edit.occurrenceInstanceId] = editCounts.getOrDefault(edit.occurrenceInstanceId, 0) + 1
            }
        }
        val occurrenceEvidence = input.boundaries.flatMap {
            listOf(it.outgoingInstanceId to it.outgoing, it.incomingInstanceId to it.incoming)
        }.toMap()
        editCounts.forEach { (occurrenceId, count) ->
            val noteCount = occurrenceEvidence[occurrenceId]?.melodyNotes?.size ?: 0
            if (count * 100 / noteCount.coerceAtLeast(1) > EnsembleCohesionEnhancementPolicy.forIntensity(input.intensity).melodyPercent) {
                errors += "Cohesion melody edits for '$occurrenceId' exceed the recognizable identity budget"
            }
        }
        return EnsembleCohesionValidationResult(errors)
    }
    fun requireValid(plan: EnsembleCohesionPlan, input: EnsembleCohesionInput) { val result = validate(plan, input); require(result.isValid) { result.errors.joinToString("; ") } }
    private fun tempoValid(tempo: MidiTempoChange) = tempo.tick >= 0 && tempo.bpm.isFinite() && tempo.bpm in 20.0..300.0
    private fun meterValid(meter: MidiTimeSignature) = meter.tick >= 0 && meter.numerator in 1..12 && meter.denominator in setOf(1, 2, 4, 8, 16)
    private fun validateMelodyEdit(label: String, edit: CohesionMelodyEdit, source: TransitionContext, errors: MutableList<String>) {
        val evidence = when (edit.occurrenceInstanceId) {
            source.outgoingInstanceId -> source.outgoing
            source.incomingInstanceId -> source.incoming
            else -> { errors += "$label melody edit references an unrelated occurrence"; return }
        }
        val outgoing = edit.occurrenceInstanceId == source.outgoingInstanceId
        val barTicks = evidence.ppq * 4L / evidence.meter.denominator * evidence.meter.numerator
        val windowStart = if (outgoing) (evidence.durationTicks - barTicks).coerceAtLeast(0) else 0L
        val windowEnd = if (outgoing) evidence.durationTicks else minOf(barTicks, evidence.durationTicks)
        val notes = evidence.melodyNotes.associateBy(CohesionMelodyNote::id)
        if (!rationale.matches(edit.reason)) errors += "$label melody edit reason is invalid"
        when (edit.kind) {
            CohesionMelodyEditKind.ADD_NOTE -> {
                val anchor = notes[edit.anchorNoteId]
                val scale = evidence.key?.toMusicalKeyOrNull()?.scalePitchClasses()?.map { it.chromatic }
                if (!edit.noteId.matches(Regex("add-[0-9]{5}")) || edit.pitch !in 0..127 || edit.velocity !in 1..127 || edit.channel !in 0..15 ||
                    edit.startTick == null || edit.durationTicks == null || edit.durationTicks <= 0 || edit.anchorNoteId !in notes ||
                    edit.startTick !in windowStart until windowEnd || edit.startTick + edit.durationTicks > windowEnd ||
                    anchor != null && edit.pitch != null && kotlin.math.abs(edit.pitch - anchor.pitch) > 12 ||
                    scale != null && edit.pitch != null && edit.pitch.mod(12) !in scale) {
                    errors += "$label contains an invalid melody-note addition"
                }
            }
            else -> {
                val note = notes[edit.noteId]
                if (note == null || note.startTick !in windowStart until windowEnd || note.endTick > windowEnd) errors += "$label melody edit targets a note outside its boundary window"
                if (melodyAnchorMutation(edit.kind) && note != null && note.id in setOf(evidence.melodyNotes.firstOrNull()?.id, evidence.melodyNotes.lastOrNull()?.id)) {
                    errors += "$label cannot remove, retime, or repitch a recognizable melody anchor"
                }
                if (edit.kind == CohesionMelodyEditKind.SET_PITCH && (edit.value !in 0..127 || note != null && kotlin.math.abs(edit.value - note.pitch) > 2 ||
                        evidence.key?.toMusicalKeyOrNull()?.scalePitchClasses()?.none { it.chromatic == edit.value.toInt().mod(12) } == true)) {
                    errors += "$label replacement pitch is invalid"
                }
                if (edit.kind == CohesionMelodyEditKind.SET_VELOCITY && edit.value !in 1..127) errors += "$label replacement velocity is invalid"
                if (edit.kind == CohesionMelodyEditKind.SET_START && (edit.value !in windowStart until windowEnd ||
                        note != null && edit.value + (note.endTick - note.startTick) > windowEnd)) {
                    errors += "$label replacement start is outside its boundary window"
                }
                if (edit.kind == CohesionMelodyEditKind.SET_DURATION && (edit.value <= 0 || note != null && note.startTick + edit.value > windowEnd)) errors += "$label replacement duration is invalid"
            }
        }
    }
    private fun bridgeCompatible(bridge: TransitionBridgePlan): Boolean = when (bridge.roleAction) {
        TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> bridge.instrument == "drums" && bridge.bridgeType in setOf(BridgeType.DRUM_FILL, BridgeType.BUILD)
        TransitionRoleAction.BASS_MOTION -> bridge.instrument == "bass" && bridge.bridgeType == BridgeType.BASS_WALK
        TransitionRoleAction.CHORD_MOTION -> bridge.instrument in setOf("pad", "strings") && bridge.bridgeType == BridgeType.CHORD_MOTION
        TransitionRoleAction.SUSTAINED_TEXTURE -> bridge.instrument in setOf("pad", "strings") && bridge.bridgeType == BridgeType.PAD_SUSTAIN
        TransitionRoleAction.CONTINUITY -> bridge.instrument in setOf("drums", "bass", "pad", "strings") && bridge.bridgeType == BridgeType.CONTINUITY
    }

    private fun melodyAnchorMutation(kind: CohesionMelodyEditKind): Boolean = kind in setOf(
        CohesionMelodyEditKind.REMOVE_NOTE, CohesionMelodyEditKind.SET_PITCH, CohesionMelodyEditKind.SET_START
    )

}

class LocalQwenEnsembleCohesionPlanner(private val client: LocalQwenClient = LmStudioQwenClient(), private val model: EnsembleCohesionModelIdentity) {
    fun plan(input: EnsembleCohesionInput): EnsembleCohesionPlan = requestQwenWithAutomaticRetries(
        client, PROMPT, json.encodeToString(EnsembleCohesionModelInput.serializer(), modelInput(input))
    ) { response ->
        val modelResponse = try {
            json.decodeFromString(EnsembleCohesionModelResponse.serializer(), response)
        } catch (error: Exception) {
            throw IllegalArgumentException("Qwen returned invalid transition-cohesion JSON: ${error.message}", error)
        }
        val decisions = modelResponse.boundaries
        require(decisions.size == input.boundaries.size) {
            "Qwen returned ${decisions.size} cohesion decisions for ${input.boundaries.size} boundaries"
        }
        val boundaries = input.boundaries.zip(decisions).map { (source, decision) ->
            val details = bridgeDetails(decision.roleAction, input.supportedInstruments)
            TransitionBridgePlan(
                outgoingInstanceId = source.outgoingInstanceId,
                incomingInstanceId = source.incomingInstanceId,
                outgoingHash = source.outgoing.sourceHash,
                incomingHash = source.incoming.sourceHash,
                arrangementSha256 = input.arrangementSha256,
                contextSha256 = input.contextSha256,
                roleAction = decision.roleAction,
                bridgeType = details.bridgeType,
                bars = decision.bars,
                instrument = details.instrument,
                harmonicHandoff = decision.harmonicHandoff,
                rhythmicGesture = decision.rhythmicGesture,
                energyContour = decision.energyContour,
                rationale = decision.rationale,
                melodyEdits = decision.melodyEdits.map { edit ->
                    CohesionMelodyEdit(
                        occurrenceInstanceId = if (edit.side == CohesionMelodySide.OUTGOING) source.outgoingInstanceId else source.incomingInstanceId,
                        kind = edit.kind, noteId = edit.noteId, value = edit.value, pitch = edit.pitch, velocity = edit.velocity,
                        startTick = edit.startTick, durationTicks = edit.durationTicks, channel = edit.channel,
                        anchorNoteId = edit.anchorNoteId, reason = edit.reason
                    )
                },
                placement = TransitionPlacement.OVERLAY_BOUNDARY,
                leadBeats = decision.leadBeats ?: (decision.bars * source.outgoing.meter.numerator).coerceAtMost(source.outgoing.meter.numerator),
                tailBeats = decision.tailBeats
            )
        }
        val plan = EnsembleCohesionPlan(inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256, model = model, boundaries = boundaries, intensity = input.intensity)
        val validation = EnsembleCohesionValidator.validate(plan, input)
        require(validation.isValid) { "Qwen returned an invalid transition-cohesion plan: ${validation.errors.joinToString("; ")}" }
        plan.copy(validation = EnsembleCohesionValidationReport())
    }

    /** These are mechanical compatibility rules, not musical choices for the model. */
    private fun bridgeDetails(action: TransitionRoleAction, supported: List<String>): BridgeDetails = when (action) {
        TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> BridgeDetails("drums", BridgeType.DRUM_FILL)
        TransitionRoleAction.BASS_MOTION -> BridgeDetails("bass", BridgeType.BASS_WALK)
        TransitionRoleAction.CHORD_MOTION -> BridgeDetails(textureInstrument(supported), BridgeType.CHORD_MOTION)
        TransitionRoleAction.SUSTAINED_TEXTURE -> BridgeDetails(textureInstrument(supported), BridgeType.PAD_SUSTAIN)
        TransitionRoleAction.CONTINUITY -> BridgeDetails(
            listOf("drums", "bass", "pad", "strings").firstOrNull { it in supported }
                ?: throw IllegalArgumentException("No supported instrument can perform a continuity bridge"),
            BridgeType.CONTINUITY
        )
    }.also { details -> require(details.instrument in supported) { "Qwen selected $action, but ${details.instrument} is not supported" } }

    private fun textureInstrument(supported: List<String>) = when {
        "pad" in supported -> "pad"
        "strings" in supported -> "strings"
        else -> throw IllegalArgumentException("Qwen selected a texture action without a supported texture instrument")
    }

    private data class BridgeDetails(val instrument: String, val bridgeType: BridgeType)

    private fun modelInput(input: EnsembleCohesionInput) = EnsembleCohesionModelInput(
        supportedInstruments = input.supportedInstruments,
        boundaries = input.boundaries.map { boundary ->
            EnsembleCohesionBoundaryEvidence(
                outgoing = summary(boundary.outgoing, useLastChord = true),
                incoming = summary(boundary.incoming, useLastChord = false),
                allowedRoleActions = boundary.allowedRoleActions
            )
        },
        intensity = input.intensity
    )

    private fun summary(evidence: TransitionMusicalEvidence, useLastChord: Boolean): EnsembleCohesionMusicalSummary {
        val chords = if (useLastChord) evidence.chords.asReversed() else evidence.chords
        return EnsembleCohesionMusicalSummary(
            key = evidence.key,
            boundaryChord = chords.firstOrNull { it.symbol != null }?.symbol,
            tempoBpm = evidence.tempo.bpm,
            meterNumerator = evidence.meter.numerator,
            meterDenominator = evidence.meter.denominator,
            energy = evidence.energy,
            startsWithSound = evidence.boundary.startsWithSound,
            endsWithSound = evidence.boundary.endsWithSound,
            purpose = evidence.arrangement.purpose,
            instruments = evidence.arrangement.instruments.map { it.instrument }.distinct().sorted(),
            editableNotes = modelNotes(evidence.melodyNotes.filter { note ->
                val bar = evidence.ppq * 4L / evidence.meter.denominator * evidence.meter.numerator
                if (useLastChord) note.startTick >= (evidence.durationTicks - bar).coerceAtLeast(0) && note.endTick <= evidence.durationTicks
                else note.startTick < minOf(bar, evidence.durationTicks) && note.endTick <= minOf(bar, evidence.durationTicks)
            })
        )
    }

    /** Even sampling keeps each boundary window inside a predictable local-model context window. */
    private fun modelNotes(notes: List<CohesionMelodyNote>, maximum: Int = 512): List<CohesionMelodyNote> {
        if (notes.size <= maximum) return notes
        return (0 until maximum).map { index -> notes[(index.toLong() * notes.lastIndex / (maximum - 1)).toInt()] }
            .distinctBy(CohesionMelodyNote::id)
    }

    private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        const val PROMPT = """
            Return exactly one JSON object and no markdown or prose, with exactly the key boundaries.
            boundaries is an array containing exactly one object per supplied boundary, in the same order.
            Every boundary object has exactly these keys: roleAction, bars, harmonicHandoff, rhythmicGesture,
            energyContour, rationale, leadBeats, tailBeats, melodyEdits. Choose roleAction only from that boundary's allowedRoleActions.
            The application selects the compatible instrument and bridge type. bars is the compatibility value 1.
            leadBeats is 1 through the outgoing meter numerator and tailBeats is 0 or 1. The window overlays the
            existing boundary and never extends the song. Preserve tempo and meter.
            harmonicHandoff is HOLD or STEP_TO_INCOMING; rhythmicGesture is FILL, PICKUP, or SUSTAIN;
            energyContour is HOLD, RISE, or FALL. rationale is brief plain musical text.
            melodyEdits is an array of at most 8 optional boundary repairs. Existing-note edits use side OUTGOING or
            INCOMING, kind REMOVE_NOTE, SET_PITCH, SET_START, SET_DURATION, or SET_VELOCITY, a supplied noteId, value,
            reason, and null addition fields. ADD_NOTE uses noteId add-00000, value 0, pitch, velocity, startTick,
            durationTicks, channel, anchorNoteId, and reason. Add or remove notes only when it improves the handoff;
            keep phrase anchors, recognizable contour, tempo, meter, section length, and structure unchanged.
            Never return instrument, bridgeType, paths, commands, code, MIDI events, DSP values, samples, plugins,
            hashes, model fields, validation, approval, or any key outside this decision schema.
        """
    }
}

/**
 * The bounded, code-owned transition pattern library. A planner selects an
 * intent; only this mapping selects executable musical material.
 */
private object EnsembleTransitionPatternLibrary {
    val forBridge: Map<BridgeType, EnsembleTransitionPattern> = mapOf(
        BridgeType.DRUM_FILL to EnsembleTransitionPattern.DRUM_FILL,
        BridgeType.BUILD to EnsembleTransitionPattern.DRUM_FILL,
        BridgeType.CONTINUITY to EnsembleTransitionPattern.DRUM_FILL,
        BridgeType.BASS_WALK to EnsembleTransitionPattern.BASS_WALK,
        BridgeType.CHORD_MOTION to EnsembleTransitionPattern.CHORD_MOTION,
        BridgeType.PAD_SUSTAIN to EnsembleTransitionPattern.PAD_SUSTAIN
    )
}

private enum class EnsembleTransitionPattern { DRUM_FILL, BASS_WALK, CHORD_MOTION, PAD_SUSTAIN }

/** Deterministic renderer consumes only pattern-library strategies; melody source is never read or changed. */
object DeterministicTransitionBridgeEngine {
    fun write(path: Path, input: TransitionContext, plan: TransitionBridgePlan) {
        val ppq = input.incoming.ppq; val meter = input.incoming.meter
        val beat = ppq * 4L / input.outgoing.meter.denominator
        val incomingBeat = ppq * 4L / meter.denominator
        val boundary = beat * plan.leadBeats
        val length = boundary + incomingBeat * plan.tailBeats
        val sequence = Sequence(Sequence.PPQ, ppq); val track = sequence.createTrack()
        addTempo(track, input.outgoing.tempo, 0); addMeter(track, input.outgoing.meter, 0)
        if (plan.tailBeats > 0) { addTempo(track, input.incoming.tempo, boundary); addMeter(track, meter, boundary) }
        val outgoing = harmony(input.outgoing.chords.lastOrNull { it.symbol != null }?.symbol, input.outgoing.key)
        val incoming = harmony(input.incoming.chords.firstOrNull { it.symbol != null }?.symbol, input.incoming.key)
        val velocityBase = when (plan.energyContour) { EnergyContour.FALL -> 58; EnergyContour.HOLD -> 70; EnergyContour.RISE -> 82 }
        when (requireNotNull(EnsembleTransitionPatternLibrary.forBridge[plan.bridgeType]) { "Unsupported Ensemble Cohesion bridge pattern" }) {
            EnsembleTransitionPattern.DRUM_FILL -> {
                val step = (beat / 4).coerceAtLeast(1); val start = (boundary - beat).coerceAtLeast(0)
                generateSequence(start) { it + step }.takeWhile { it < boundary }.forEachIndexed { index, tick ->
                    note(track, 9, if (index < 2) 38 else 36, (velocityBase + index * 7).coerceAtMost(112), tick, (tick + step / 2).coerceAtMost(boundary))
                }
                if (plan.tailBeats > 0) note(track, 9, 36, (velocityBase + 10).coerceAtMost(115), boundary, minOf(length, boundary + step))
            }
            EnsembleTransitionPattern.BASS_WALK -> {
                val from = requireNotNull(outgoing) { "Bass Cohesion requires outgoing harmony or key evidence" }
                val to = requireNotNull(incoming) { "Bass Cohesion requires incoming harmony or key evidence" }
                val delta = ((to.root - from.root + 18) % 12) - 6
                repeat(plan.leadBeats) { index ->
                    val ratio = if (plan.leadBeats == 1) 1.0 else index.toDouble() / (plan.leadBeats - 1)
                    val root = (from.root + (delta * ratio).roundToLong().toInt()).mod(12)
                    val start = index * beat
                    note(track, 0, 36 + root, velocityBase, start, minOf(boundary, start + beat * 3 / 4))
                }
            }
            EnsembleTransitionPattern.CHORD_MOTION -> {
                val target = requireNotNull(incoming) { "Chord-motion Cohesion requires incoming harmony or key evidence" }
                target.intervals.forEach { interval -> note(track, 0, 60 + target.root + interval, velocityBase - 10, 0, length) }
            }
            EnsembleTransitionPattern.PAD_SUSTAIN -> {
                val from = requireNotNull(outgoing ?: incoming) { "Sustained Cohesion requires harmony or key evidence" }
                val common = incoming?.let { next -> from.intervals.map { (from.root + it).mod(12) }.firstOrNull { pc -> pc in next.intervals.map { (next.root + it).mod(12) } } }
                note(track, 0, 60 + (common ?: from.root), velocityBase - 12, 0, length)
            }
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), length)); publishMidi(path, sequence, ppq, length)
    }
    private fun publishMidi(path: Path, sequence: Sequence, ppq: Int, length: Long) {
        Files.createDirectories(path.parent); val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write transition MIDI" }
            val reread = MidiSystem.getSequence(temporary.toFile())
            require(reread.divisionType == Sequence.PPQ && reread.resolution == ppq && reread.tickLength >= length) { "Transition MIDI round-trip timing mismatch" }
            val messages = reread.tracks.flatMap { track -> (0 until track.size()).map { track[it].message as? ShortMessage } }.filterNotNull()
            val on = messages.count { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }; val off = messages.count { it.command == ShortMessage.NOTE_OFF || it.command == ShortMessage.NOTE_ON && it.data2 == 0 }
            require(on > 0 && on == off) { "Transition MIDI has invalid note pairs" }; move(temporary, path)
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun note(track: javax.sound.midi.Track, channel: Int, pitch: Int, velocity: Int, start: Long, end: Long) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch.coerceIn(0, 127), velocity), start)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch.coerceIn(0, 127), 0), end)) }
    private fun addTempo(track: javax.sound.midi.Track, tempo: MidiTempoChange, tick: Long) { val micros = (60_000_000.0 / tempo.bpm).roundToLong().toInt(); track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros ushr 16).toByte(), (micros ushr 8).toByte(), micros.toByte()), 3), tick)) }
    private fun addMeter(track: javax.sound.midi.Track, meter: MidiTimeSignature, tick: Long) { track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(meter.numerator.toByte(), Integer.numberOfTrailingZeros(meter.denominator).toByte(), 24, 8), 4), tick)) }
    private fun harmony(symbol: String?, key: MidiKey?): Harmony? {
        val match = Regex("^([A-G](?:#|b)?)(|m|min|7|maj7|m7|min7|maj9|m9|min9|add9|sus2|sus4|sus)$", RegexOption.IGNORE_CASE).matchEntire(symbol.orEmpty())
        val root = match?.groupValues?.get(1)?.let(::pitchClass) ?: key?.toMusicalKeyOrNull()?.tonic?.chromatic ?: return null
        val intervals = when (match?.groupValues?.get(2)?.lowercase()) {
            "m", "min" -> listOf(0, 3, 7)
            "7" -> listOf(0, 4, 7, 10)
            "maj7" -> listOf(0, 4, 7, 11)
            "m7", "min7" -> listOf(0, 3, 7, 10)
            "maj9" -> listOf(0, 4, 7, 11, 14)
            "m9", "min9" -> listOf(0, 3, 7, 10, 14)
            "add9" -> listOf(0, 4, 7, 14)
            "sus2" -> listOf(0, 2, 7)
            "sus4", "sus" -> listOf(0, 5, 7)
            else -> listOf(0, 4, 7)
        }
        return Harmony(root, intervals)
    }
    private fun pitchClass(value: String): Int? { val base = when (value.firstOrNull()?.uppercaseChar()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }; return when (value.getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base } }
    private data class Harmony(val root: Int, val intervals: List<Int>)
    private fun move(from: Path, to: Path) { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) } }
}

/** Applies reviewed, occurrence-local melody edits without ever overwriting the selected part MIDI. */
object CohesionMelodyApplier {
    fun write(source: Path, target: Path, evidence: TransitionMusicalEvidence, edits: List<CohesionMelodyEdit>, protectAnchors: Boolean = true, maximumIdentityPercent: Int = 25) {
        val before = sha256(Files.readAllBytes(source))
        require(before == evidence.sourceHash) { "Cohesion melody source changed before application" }
        val sequence = MidiSystem.getSequence(source.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == evidence.ppq) { "Cohesion melody timing is incompatible" }
        val identity = MelodyIdentityBuilder.build(source, evidence.ppq * 4L / evidence.meter.denominator)
        val notes = notes(sequence, evidence.sourceHash)
        require(notes.keys == evidence.melodyNotes.map(CohesionMelodyNote::id).toSet()) { "Cohesion melody note IDs are stale" }
        val protectedAnchorIds = identity.anchorIds.map(MelodyNoteId::value).toSet()
        require(!protectAnchors || edits.none {
            it.noteId in protectedAnchorIds && it.kind in setOf(CohesionMelodyEditKind.REMOVE_NOTE, CohesionMelodyEditKind.SET_PITCH, CohesionMelodyEditKind.SET_START)
        }) {
            "Cohesion would remove, retime, or repitch a recognizable melody anchor"
        }
        val anchors = protectedAnchorIds.associateWith { notes.getValue(it).pitch }
        require(edits.size * 100 / notes.size.coerceAtLeast(1) <= maximumIdentityPercent) { "Cohesion melody edits exceed the recognizable identity budget" }
        edits.filter { it.kind !in setOf(CohesionMelodyEditKind.ADD_NOTE, CohesionMelodyEditKind.REMOVE_NOTE) }.forEach { edit ->
            val note = notes.getValue(edit.noteId)
            when (edit.kind) {
                CohesionMelodyEditKind.SET_PITCH -> note.pitch = edit.value.toInt()
                CohesionMelodyEditKind.SET_START -> { val duration = note.end - note.start; note.start = edit.value; note.end = edit.value + duration }
                CohesionMelodyEditKind.SET_DURATION -> note.end = note.start + edit.value
                CohesionMelodyEditKind.SET_VELOCITY -> note.velocity = edit.value.toInt()
                else -> error("unreachable")
            }
        }
        edits.filter { it.kind == CohesionMelodyEditKind.REMOVE_NOTE }.forEach { notes.remove(it.noteId)?.remove() }
        edits.filter { it.kind == CohesionMelodyEditKind.ADD_NOTE }.forEach { edit ->
            val anchor = notes.getValue(requireNotNull(edit.anchorNoteId))
            val start = requireNotNull(edit.startTick); val duration = requireNotNull(edit.durationTicks)
            anchor.track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, requireNotNull(edit.channel), requireNotNull(edit.pitch), requireNotNull(edit.velocity)), start))
            anchor.track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, edit.channel, edit.pitch, 0), start + duration))
        }
        val after = notes(sequence, evidence.sourceHash).values.sortedWith(compareBy<EditableNote> { it.start }.thenBy { it.pitch })
        require(after.isNotEmpty() && (!protectAnchors || anchors.all { (id, pitch) -> notes[id]?.pitch == pitch })) { "Cohesion would alter recognizable melody anchors" }
        after.groupBy { it.channel to it.pitch }.values.forEach { samePitch ->
            samePitch.sortedBy(EditableNote::start).zipWithNext().forEach { (left, right) -> require(left.end <= right.start) { "Cohesion created overlapping melody notes" } }
        }
        require(after.all { it.start >= 0 && it.end > it.start && it.end <= evidence.durationTicks && it.pitch in 0..127 && it.velocity in 1..127 }) {
            "Cohesion created invalid melody events"
        }
        Files.createDirectories(requireNotNull(target.parent)); val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write cohesive occurrence MIDI" }
            require(sha256(Files.readAllBytes(source)) == before) { "Cohesion changed its selected melody input" }
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun notes(sequence: Sequence, sourceSha256: String): LinkedHashMap<String, EditableNote> {
        val found = linkedMapOf<String, EditableNote>()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Triple<MidiEvent, ShortMessage, Int>>>()
            val ordinal = mutableMapOf<Int, Int>()
            (0 until track.size()).forEach { index ->
                val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach; val key = message.channel to message.data1
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                    val noteOnOrdinal = ordinal.getOrDefault(message.channel, 0); ordinal[message.channel] = noteOnOrdinal + 1
                    active.getOrPut(key) { ArrayDeque() }.addLast(Triple(event, message, noteOnOrdinal))
                }
                else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                    val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Cohesion melody has unmatched note-off")
                    found[MelodyNoteId.derive(sourceSha256, trackIndex, message.channel, start.third, message.data1, start.first.tick, event.tick).value] = EditableNote(track, start.first, event, start.second, message)
                }
            }
            require(active.values.all { it.isEmpty() }) { "Cohesion melody has hanging notes" }
        }
        return found
    }

    private class EditableNote(val track: javax.sound.midi.Track, val onEvent: MidiEvent, val offEvent: MidiEvent, val on: ShortMessage, val off: ShortMessage) {
        val channel get() = on.channel
        var pitch: Int get() = on.data1; set(value) { on.setMessage(on.command, on.channel, value, on.data2); off.setMessage(off.command, off.channel, value, off.data2) }
        var velocity: Int get() = on.data2; set(value) { on.setMessage(on.command, on.channel, on.data1, value) }
        var start: Long get() = onEvent.tick; set(value) { onEvent.tick = value }
        var end: Long get() = offEvent.tick; set(value) { offEvent.tick = value }
        fun remove() { track.remove(onEvent); track.remove(offEvent) }
    }
}

/** Copies one generated role and folds only validated boundary bridges into its authoritative timeline. */
object CohesionRoleBridgeApplier {
    fun write(root: Path, source: Path, target: Path, role: String, input: EnsembleCohesionInput, plan: EnsembleCohesionPlan) {
        require(Files.isRegularFile(source)) { "Generated $role MIDI is missing before Cohesion" }
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.copy.tmp")
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            mergeBridges(root, temporary, role, input, plan)
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun mergeBridges(root: Path, target: Path, role: String, input: EnsembleCohesionInput, plan: EnsembleCohesionPlan) {
        val bridges = plan.boundaries.filter { it.instrument == role }
        if (bridges.isEmpty()) return
        val sequence = MidiSystem.getSequence(target.toFile())
        val targetTrack = sequence.tracks.firstOrNull { track -> (0 until track.size()).any { track[it].message is ShortMessage } } ?: sequence.createTrack()
        val channel = (0 until targetTrack.size()).mapNotNull { targetTrack[it].message as? ShortMessage }
            .firstOrNull { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.channel ?: if (role == "drums") 9 else 0
        val occupied = roleNotes(sequence).toMutableList()
        bridges.forEach { bridge ->
            val boundaryIndex = input.occurrences.indexOfFirst { it.instanceId == bridge.outgoingInstanceId }
            require(boundaryIndex >= 0) { "Cohesion bridge occurrence is missing" }
            val outgoing = input.occurrences[boundaryIndex].evidence
            val globalEnd = input.occurrences.take(boundaryIndex + 1).sumOf { it.evidence.durationTicks }
            val beat = outgoing.ppq * 4L / outgoing.meter.denominator
            val offset = globalEnd - beat * bridge.leadBeats
            val bridgePath = root.resolve(EnsembleCohesionStore.bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId))
            roleNotes(MidiSystem.getSequence(bridgePath.toFile())).forEach { note ->
                val shifted = note.copy(start = offset + note.start, end = offset + note.end, channel = channel)
                if (occupied.none { it.channel == shifted.channel && it.pitch == shifted.pitch && it.start < shifted.end && shifted.start < it.end }) {
                    targetTrack.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, shifted.pitch, shifted.velocity), shifted.start))
                    targetTrack.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, shifted.pitch, 0), shifted.end))
                    occupied += shifted
                }
            }
        }
        val temporary = target.resolveSibling(".${target.fileName}.bridges.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not publish enhanced role MIDI" }
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun roleNotes(sequence: Sequence): List<RoleNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val result = mutableListOf<RoleNote>()
        sequence.tracks.forEach { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                active[key]?.removeFirstOrNull()?.let { result += RoleNote(message.channel, message.data1, it.second, it.first, event.tick) }
            }
        } }
        return result
    }
    private data class RoleNote(val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
}

object EnsembleCohesionStore {
    const val DRAFT_FILE = "cohesion/cohesion.draft.json"; const val APPROVED_FILE = "cohesion/cohesion.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    fun bridgeMidi(outgoing: String, incoming: String) = "cohesion/boundaries/$outgoing--$incoming/bridge.mid"
    fun audit(outgoing: String, incoming: String) = "cohesion/boundaries/$outgoing--$incoming/audit.json"
    fun writeDraft(root: Path, input: EnsembleCohesionInput, plan: EnsembleCohesionPlan): Path {
        EnsembleCohesionValidator.requireValid(plan, input)
        plan.boundaries.forEachIndexed { index, bridge -> DeterministicTransitionBridgeEngine.write(root.resolve(bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId)), input.boundaries[index], bridge); atomicWrite(root.resolve(audit(bridge.outgoingInstanceId, bridge.incomingInstanceId)), json.encodeToString(TransitionBridgePlan.serializer(), bridge)) }
        val text = json.encodeToString(EnsembleCohesionPlan.serializer(), plan)
        return atomicWrite(root.resolve(DRAFT_FILE), text).also { persist(root, input, plan, false, emptySet()) }
    }
    fun readDraft(root: Path, input: EnsembleCohesionInput) = read(root.resolve(DRAFT_FILE), input)
    fun readApproved(root: Path, input: EnsembleCohesionInput) = read(root.resolve(APPROVED_FILE), input)
    fun markReviewed(root: Path, input: EnsembleCohesionInput, outgoing: String, incoming: String): Set<Pair<String, String>> { val plan = readDraft(root, input); val pair = outgoing to incoming; require(pair in plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }) { "Unknown cohesion boundary $outgoing -> $incoming" }; return (reviewed(root) + pair).also { persist(root, input, plan, false, it) } }
    fun approve(root: Path, input: EnsembleCohesionInput): Path { val plan = readDraft(root, input); val expected = plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet(); val approved = plan.copy(approval = EnsembleCohesionApproval.APPROVED); val text = json.encodeToString(EnsembleCohesionPlan.serializer(), approved); return atomicWrite(root.resolve(APPROVED_FILE), text).also { persist(root, input, approved, true, expected) } }
    fun reject(root: Path, input: EnsembleCohesionInput): Path { val rejected = atomicWrite(root.resolve("cohesion/rejected-${input.inputHash}.json"), Files.readString(root.resolve(DRAFT_FILE))); ProjectStore.read(root).takeIf { it.version == Project.CURRENT_VERSION }?.let { project -> ProjectStore.write(root, project.copy(workflow = project.workflow.invalidate(WorkflowChange.COHESION).copy(stale = project.workflow.stale + WorkflowArtifact.COHESION))) }; return rejected }
    fun attachPreviews(root: Path, input: EnsembleCohesionInput, previews: CohesionPreviewReferences) {
        val project = ProjectStore.read(root)
        val cohesion = requireNotNull(project.workflow.cohesion)
        require(cohesion.inputSha256 == input.inputHash && listOf(previews.baseline, previews.enhanced).all { reference ->
            val path = root.resolve(reference.file).normalize()
            path.startsWith(root) && Files.isRegularFile(path) && digest(path) == reference.sha256
        }) { "Cohesion previews are missing or stale" }
        ProjectStore.write(root, project.copy(workflow = project.workflow.copy(cohesion = cohesion.copy(previews = previews))))
    }
    fun isApprovedCurrent(root: Path, input: EnsembleCohesionInput): Boolean = runCatching {
        val workflow = ProjectStore.read(root).workflow.cohesion ?: return false
        workflow.approved && workflow.inputSha256 == input.inputHash && workflow.structureSha256 == input.structureSha256 &&
            workflow.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } == input.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } &&
            workflow.boundaries.all { it.approved != null && it.bridgeSha256 != null && Files.isRegularFile(root.resolve(bridgeMidi(it.outgoingInstanceId, it.incomingInstanceId))) && digest(root.resolve(bridgeMidi(it.outgoingInstanceId, it.incomingInstanceId))) == it.bridgeSha256 } &&
            workflow.occurrences.isNotEmpty() && workflow.occurrences.all { occurrence -> occurrence.approved && occurrence.cohesionInputSha256 == input.inputHash && Files.isRegularFile(root.resolve(occurrence.result.file)) && digest(root.resolve(occurrence.result.file)) == occurrence.result.sha256 } &&
            workflow.intensity == input.intensity && workflow.roles.map { it.role } == input.generatedRoles.map { it.role } &&
            workflow.roles.all { role -> role.approved && role.cohesionInputSha256 == input.inputHash && input.generatedRoles.single { it.role == role.role }.sourceHash == role.sourceSha256 && Files.isRegularFile(root.resolve(role.result.file)) && digest(root.resolve(role.result.file)) == role.result.sha256 } &&
            workflow.previews?.let { previews -> listOf(previews.baseline, previews.enhanced).all { reference -> Files.isRegularFile(root.resolve(reference.file)) && digest(root.resolve(reference.file)) == reference.sha256 } } != false
    }.getOrDefault(false)
    private fun reviewed(root: Path) = ProjectStore.read(root).workflow.cohesion?.boundaries.orEmpty().filter { it.approved != null }.map { it.outgoingInstanceId to it.incomingInstanceId }.toSet()
    private fun read(path: Path, input: EnsembleCohesionInput) = json.decodeFromString(EnsembleCohesionPlan.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also { EnsembleCohesionValidator.requireValid(it, input) }
    private fun persist(root: Path, input: EnsembleCohesionInput, plan: EnsembleCohesionPlan, approved: Boolean, reviewed: Set<Pair<String, String>>) {
        val text = json.encodeToString(EnsembleCohesionPlan.serializer(), plan)
        val boundaries = plan.boundaries.map { bridge -> val pair = bridge.outgoingInstanceId to bridge.incomingInstanceId; val draft = CohesionBoundaryArtifactPaths.draft(bridge.outgoingInstanceId, bridge.incomingInstanceId); atomicWrite(root.resolve(draft), json.encodeToString(TransitionBridgePlan.serializer(), bridge)); val approvedFile = CohesionBoundaryArtifactPaths.approved(bridge.outgoingInstanceId, bridge.incomingInstanceId); if (pair in reviewed) atomicWrite(root.resolve(approvedFile), json.encodeToString(TransitionBridgePlan.serializer(), bridge)); CohesionBoundaryReference(bridge.outgoingInstanceId, bridge.incomingInstanceId, input.inputHash, WorkflowArtifactReference(draft, digest(root.resolve(draft))), if (pair in reviewed) WorkflowArtifactReference(approvedFile, digest(root.resolve(approvedFile))) else null, digest(root.resolve(bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId)))) }
        val project = ProjectStore.read(root); if (project.version != Project.CURRENT_VERSION) return
        val occurrenceEvidence = input.occurrences.associate { it.instanceId to it.evidence }
        val selected = SelectedMidiArtifactResolver()
        val existing = project.workflow.cohesion?.takeIf { it.inputSha256 == input.inputHash }
        val occurrences = if (approved && existing != null) {
            require(existing.occurrences.map(CohesionOccurrenceReference::instanceId) == project.envelope.structureOccurrences.map { it.id }) {
                "Reviewed Cohesion occurrences no longer match Structure"
            }
            existing.occurrences.map { reference ->
                val source = selected.resolve(root, project, occurrenceEvidence.getValue(reference.instanceId).partId)
                val path = root.resolve(reference.result.file)
                require(reference.sourceSha256 == source.sha256 && reference.cohesionInputSha256 == input.inputHash &&
                    Files.isRegularFile(path) && digest(path) == reference.result.sha256) {
                    "Reviewed Cohesion occurrence '${reference.instanceId}' changed before approval"
                }
                reference.copy(approved = true)
            }
        } else project.envelope.structureOccurrences.map { occurrence ->
            val source = selected.resolve(root, project, occurrence.partId)
            val relative = CohesionOccurrenceArtifactPaths.enhancedOutput(input.inputHash, occurrence.id)
            val evidence = occurrenceEvidence[occurrence.id]
            if (evidence == null) {
                require(plan.boundaries.isEmpty()) { "Cohesion is missing melody evidence for occurrence '${occurrence.id}'" }
                Files.createDirectories(root.resolve(relative).parent)
                Files.copy(source.path, root.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
            } else {
                require(source.sha256 == evidence.sourceHash) { "Cohesion occurrence source is stale" }
                val boundaryEdits = plan.boundaries.flatMap(TransitionBridgePlan::melodyEdits).filter { it.occurrenceInstanceId == occurrence.id }
                CohesionMelodyApplier.write(source.path, root.resolve(relative), evidence, boundaryEdits, maximumIdentityPercent = EnsembleCohesionEnhancementPolicy.forIntensity(input.intensity).melodyPercent)
            }
            CohesionOccurrenceReference(occurrence.id, source.sha256, WorkflowArtifactReference(relative, digest(root.resolve(relative))), approved, input.inputHash)
        }
        val roles = if (approved && existing != null) {
            require(existing.roles.map(CohesionRoleReference::role) == input.generatedRoles.map(GeneratedRoleEvidence::role)) {
                "Reviewed Cohesion roles no longer match the arrangement"
            }
            existing.roles.map { reference ->
                val evidence = input.generatedRoles.single { it.role == reference.role }
                val path = root.resolve(reference.result.file)
                require(reference.sourceSha256 == evidence.sourceHash && reference.cohesionInputSha256 == input.inputHash &&
                    Files.isRegularFile(path) && digest(path) == reference.result.sha256) {
                    "Reviewed Cohesion role '${reference.role}' changed before approval"
                }
                reference.copy(approved = true)
            }
        } else input.generatedRoles.map { evidence ->
            val source = root.resolve("midi/generated/${evidence.role}.mid")
            require(Files.isRegularFile(source) && digest(source) == evidence.sourceHash) { "Generated ${evidence.role} MIDI changed before Cohesion" }
            val relative = CohesionRoleArtifactPaths.output(input.inputHash, evidence.role)
            CohesionRoleBridgeApplier.write(root, source, root.resolve(relative), evidence.role, input, plan)
            CohesionRoleReference(evidence.role, evidence.sourceHash, WorkflowArtifactReference(relative, digest(root.resolve(relative))), approved, input.inputHash)
        }
        val retainedPreviews = existing?.previews
        val workflow = project.workflow.invalidate(WorkflowChange.COHESION).markCurrent(WorkflowArtifact.COHESION).copy(cohesion = CohesionWorkflowReferences(
            inputSha256 = input.inputHash,
            plan = WorkflowArtifactReference(if (approved) APPROVED_FILE else DRAFT_FILE, digestText(text)),
            occurrences = occurrences,
            approved = approved,
            boundaries = boundaries,
            structureSha256 = input.structureSha256,
            roles = roles,
            intensity = input.intensity,
            previews = retainedPreviews
        ))
        ProjectStore.write(root, project.copy(workflow = workflow))
    }
    private fun atomicWrite(path: Path, text: String): Path { Files.createDirectories(path.parent); val tmp = path.resolveSibling(".${path.fileName}.tmp"); try { Files.writeString(tmp, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING) }; return path } finally { Files.deleteIfExists(tmp) } }
    private fun digest(path: Path) = sha256(Files.readAllBytes(path)); private fun digestText(text: String) = sha256(text.toByteArray(StandardCharsets.UTF_8))
}

/** Builds evidence from selected MIDI, saved Structure, approved arrangement, and profile/mood settings. */
object EnsembleTransitionContextFactory {
    fun build(root: Path, project: Project, planning: SongPlanningInput, arrangement: DetailedArrangement, arrangementSha256: String, contextSha256: String, intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED): EnsembleCohesionInput {
        require(HASH.matches(arrangementSha256) && HASH.matches(contextSha256)) { "Cohesion requires approved arrangement and context identities" }
        val sections = planning.sectionsWithIdentity(); require(arrangement.sections.map { it.instanceId } == sections.map { it.instanceId }) { "Approved arrangement does not match saved Structure" }
        val selected = SelectedMidiArtifactResolver(); val evidence = sections.associate { section ->
            val artifact = selected.resolve(root, project, section.partId); val analysis = planning.analyses.getValue(section.partId); val arranged = arrangement.sections.first { it.instanceId == section.instanceId }; val reference = requireNotNull(project.parts.first { it.id == section.partId }.analysis)
            section.instanceId to TransitionMusicalEvidence(
                section.partId, artifact.sha256, sha256(Files.readAllBytes(root.resolve(reference.file))), analysis.ppq,
                analysis.durationTicks, analysis.key, analysis.chords, analysis.tempoMap.first(), analysis.timeSignatures.first(),
                analysis.energy, boundary(artifact.path, analysis.durationTicks), arrangementEvidence(section, arranged), melody(artifact.path)
            )
        }
        val approvedRoles = arrangement.sections.flatMap { it.instruments }
            .filter { it.mode == InstrumentMode.GENERATED }
            .map { it.name }
            .filter { it != "piano" }
            .distinct()
        val supported = approvedRoles.sorted()
        val policy = policy(project, contextSha256, supported)
        val boundaries = if (supported.isEmpty()) emptyList() else sections.zipWithNext().map { (outgoing, incoming) ->
            TransitionContext(outgoing.instanceId, incoming.instanceId, evidence.getValue(outgoing.instanceId), evidence.getValue(incoming.instanceId), policy.allowedActions, policy)
        }
        val generatedRoles = supported.map { role ->
            val path = root.resolve("midi/generated/$role.mid")
            require(Files.isRegularFile(path)) { "Baseline generated $role MIDI is missing" }
            val sequence = MidiSystem.getSequence(path.toFile())
            GeneratedRoleEvidence(role, sha256(Files.readAllBytes(path)), sequence.resolution, sequence.tickLength, melody(path))
        }
        val seed = EnsembleCohesionInput(inputHash = "", structureSha256 = sha256(project.envelope.structureOccurrences.joinToString("|") { "${it.id}:${it.partId}:${it.revision}" }.toByteArray()), arrangementSha256 = arrangementSha256, contextSha256 = contextSha256, supportedInstruments = supported, boundaries = boundaries, intensity = intensity, occurrences = sections.map { SongOccurrenceEvidence(it.instanceId, evidence.getValue(it.instanceId)) }, generatedRoles = generatedRoles)
        return seed.copy(inputHash = sha256(Json { encodeDefaults = true; explicitNulls = false }.encodeToString(EnsembleCohesionInput.serializer(), seed).toByteArray()))
    }
    private fun policy(project: Project, contextHash: String, instruments: List<String>): TransitionPolicyEvidence {
        val settings = project.envelope.compositionSettings
        val profile = settings?.profile
        val mood = settings?.mood
        val profileActions = if (profile != null && mood != null) BundledCompositionProfileCatalog.load().resolve(profile, mood).transitionActions.mapNotNull(::policyAction) else TransitionRoleAction.entries
        val actions = profileActions.filter { action -> when (action) {
            TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> "drums" in instruments
            TransitionRoleAction.BASS_MOTION -> "bass" in instruments
            TransitionRoleAction.CHORD_MOTION, TransitionRoleAction.SUSTAINED_TEXTURE -> "pad" in instruments || "strings" in instruments
            TransitionRoleAction.CONTINUITY -> instruments.isNotEmpty()
        } }
        return TransitionPolicyEvidence(profile?.id ?: "legacy", mood?.id ?: "legacy", contextHash, actions.distinct())
    }
    private fun policyAction(value: String): TransitionRoleAction? = when (value) {
        "drum-fill" -> TransitionRoleAction.DRUM_FILL
        "bass-motion" -> TransitionRoleAction.BASS_MOTION
        "chord-motion" -> TransitionRoleAction.CHORD_MOTION
        "sustained-texture" -> TransitionRoleAction.SUSTAINED_TEXTURE
        "dynamics-automation" -> TransitionRoleAction.DYNAMICS_AUTOMATION
        "continuity" -> TransitionRoleAction.CONTINUITY
        else -> null
    }
    private fun arrangementEvidence(section: SongPlanningSectionInstance, arrangement: DetailedArrangementSection) = TransitionArrangementEvidence(section.occurrenceHash, arrangement.role, arrangement.instruments.map { TransitionInstrumentEvidence(it.name, it::class.simpleName.orEmpty(), density(it)) }.sortedBy { it.instrument }, sha256(Json { encodeDefaults = true }.encodeToString(StructureVariationOverrides.serializer(), section.variationOverrides).toByteArray()))
    private fun density(instrument: DetailedInstrumentPlan): Double? = when (instrument) { is BassInstrumentPlan -> instrument.density; is DrumsInstrumentPlan -> instrument.density; is PadInstrumentPlan -> instrument.density; is StringsInstrumentPlan -> instrument.density; else -> null }
    private fun boundary(path: Path, duration: Long): TransitionBoundarySummary { val sequence = MidiSystem.getSequence(path.toFile()); val notes = sequence.tracks.flatMap { track -> (0 until track.size()).map { track[it] } }.mapNotNull { event -> (event.message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { event.tick } }; return TransitionBoundarySummary(notes.any { it == 0L }, notes.any { it >= duration }, notes.minOrNull(), notes.maxOrNull()) }
    private fun melody(path: Path): List<CohesionMelodyNote> {
        val sequence = MidiSystem.getSequence(path.toFile())
        return MelodyIdentityBuilder.build(path, sequence.resolution * 4L).notes.map { note ->
            CohesionMelodyNote(note.id.value, note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)
        }
    }
    private val HASH = Regex("[0-9a-f]{64}")
}
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
