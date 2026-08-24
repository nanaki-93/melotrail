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
    companion object { val DETERMINISTIC = EnsembleCohesionModelIdentity("deterministic", "cohesion-boundary-v7", "0".repeat(64)) }
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
    val generatedRoles: List<GeneratedRoleEvidence> = emptyList(),
    /** Exact approved source-feel map used by every rendered bass or drum bridge. */
    val acceptedFullSongGrooveMap: FullSongGrooveMap? = null
) { companion object { const val VERSION = 7 } }

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
    val transitionPolicy: TransitionPolicyEvidence,
    /** The local roles that can actually take part at this saved adjacent boundary. */
    val roles: TransitionBoundaryRoleEvidence = TransitionBoundaryRoleEvidence(),
    /** Canonical global offsets keep bridge rhythm on the accepted full-song grid. */
    val outgoingStartTick: Long = 0L,
    val incomingStartTick: Long = 0L
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
@Serializable data class TransitionInstrumentEvidence(val instrument: String, val role: String, val density: Double?, val generated: Boolean = true)
@Serializable data class TransitionPolicyEvidence(val profileId: String, val moodId: String, val policySha256: String, val allowedActions: List<TransitionRoleAction>)

/**
 * Boundary-local arrangement roles, derived from the two adjacent approved
 * sections instead of a whole-song instrument list. A bridge may use only a
 * supported role; a continuity no-op additionally requires a continuing role.
 */
@Serializable data class TransitionBoundaryRoleEvidence(
    val outgoingActive: List<String> = emptyList(),
    val incomingActive: List<String> = emptyList(),
    val entering: List<String> = emptyList(),
    val exiting: List<String> = emptyList(),
    val continuing: List<String> = emptyList(),
    val supported: List<String> = emptyList()
) {
    init {
        val all = listOf(outgoingActive, incomingActive, entering, exiting, continuing, supported)
        require(all.all { it == it.distinct().sorted() } && entering == (incomingActive - outgoingActive).sorted() &&
            exiting == (outgoingActive - incomingActive).sorted() && continuing == (outgoingActive intersect incomingActive).sorted() &&
            supported == (outgoingActive + incomingActive).distinct().sorted()) {
            "Boundary role evidence is not canonical"
        }
    }
}

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
    val boundaries: List<EnsembleCohesionBoundaryEvidence>,
    val intensity: EnsembleCohesionEnhancementIntensity
)
@Serializable private data class EnsembleCohesionBoundaryEvidence(
    val outgoing: EnsembleCohesionMusicalSummary,
    val incoming: EnsembleCohesionMusicalSummary,
    val allowedRoleActions: List<TransitionRoleAction>,
    val roles: TransitionBoundaryRoleEvidence
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

/** The only decisions a model is allowed to make for one boundary. */
@Serializable private data class EnsembleCohesionModelDecision(
    val roleAction: TransitionRoleAction,
    val harmonicHandoff: HarmonicHandoff,
    val rhythmicGesture: RhythmicGesture,
    val energyContour: EnergyContour,
    val rationale: String,
    val leadBeats: Int? = null,
    val tailBeats: Int = 0
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
    val instrument: String,
    val harmonicHandoff: HarmonicHandoff,
    val rhythmicGesture: RhythmicGesture,
    val energyContour: EnergyContour,
    val tempoHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val meterHandoff: TimingHandoff = TimingHandoff.PRESERVE,
    val rationale: String,
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
@Serializable enum class TransitionPlacement { OVERLAY_BOUNDARY, NO_OP }
data class EnsembleCohesionValidationResult(val errors: List<String>) { val isValid get() = errors.isEmpty() }

/** Actual rendered bridge notes retained beside the planner's bounded intent for review. */
@Serializable data class CohesionBoundaryAudit(
    val plan: TransitionBridgePlan,
    val bridgeSha256: String,
    val renderedNotes: List<CohesionMelodyNote>
)

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
        input.boundaries.zip(plan.boundaries).forEachIndexed { index, (source, bridge) ->
            val label = "Boundary ${index + 1}"
            if (!id.matches(bridge.outgoingInstanceId) || !id.matches(bridge.incomingInstanceId)) errors += "$label has an invalid occurrence ID"
            if (!hash.matches(bridge.outgoingHash) || !hash.matches(bridge.incomingHash)) errors += "$label has an invalid source hash"
            if (bridge.outgoingHash != source.outgoing.sourceHash || bridge.incomingHash != source.incoming.sourceHash) errors += "$label source hash is stale"
            if (bridge.arrangementSha256 != input.arrangementSha256 || bridge.contextSha256 != input.contextSha256) errors += "$label arrangement or context hash is stale"
            if (source.roles.supported.isEmpty()) errors += "$label has no active generated role evidence"
            val continuity = bridge.roleAction == TransitionRoleAction.CONTINUITY
            if (continuity) {
                if (bridge.placement != TransitionPlacement.NO_OP || bridge.leadBeats != 0 || bridge.tailBeats != 0 || bridge.instrument !in source.roles.continuing) {
                    errors += "$label continuity must be an explicit no-op on a continuing role"
                }
            } else if (bridge.placement != TransitionPlacement.OVERLAY_BOUNDARY || bridge.leadBeats !in 1..source.outgoing.meter.numerator || bridge.tailBeats !in 0..1) {
                errors += "$label has an invalid boundary overlay window"
            }
            if (bridge.instrument !in source.roles.supported) errors += "$label uses a role inactive on both sides: '${bridge.instrument}'"
            if (bridge.roleAction !in source.allowedRoleActions || bridge.roleAction !in source.transitionPolicy.allowedActions) errors += "$label uses a disallowed role action"
            if (!rationale.matches(bridge.rationale)) errors += "$label rationale must be bounded musical text"
            if (source.outgoing.ppq != source.incoming.ppq || source.outgoing.ppq !in 1..9600) errors += "$label has incompatible PPQ timing"
            if (!tempoValid(source.outgoing.tempo) || !tempoValid(source.incoming.tempo) || !meterValid(source.outgoing.meter) || !meterValid(source.incoming.meter)) errors += "$label has invalid timing evidence"
            if (!bridgeCompatible(bridge)) errors += "$label bridge type, role action, and instrument do not match"
            if (bridge.instrument in setOf("bass", "drums") && input.acceptedFullSongGrooveMap == null) errors += "$label requires the accepted full-song groove map"
            input.acceptedFullSongGrooveMap?.let { map ->
                if (map.ppq != source.outgoing.ppq || map.meterDenominator != source.outgoing.meter.denominator) {
                    errors += "$label full-song groove map does not match canonical timing"
                }
            }
        }
        return EnsembleCohesionValidationResult(errors)
    }
    fun requireValid(plan: EnsembleCohesionPlan, input: EnsembleCohesionInput) { val result = validate(plan, input); require(result.isValid) { result.errors.joinToString("; ") } }
    private fun tempoValid(tempo: MidiTempoChange) = tempo.tick >= 0 && tempo.bpm.isFinite() && tempo.bpm in 20.0..300.0
    private fun meterValid(meter: MidiTimeSignature) = meter.tick >= 0 && meter.numerator in 1..12 && meter.denominator in setOf(1, 2, 4, 8, 16)
    private fun bridgeCompatible(bridge: TransitionBridgePlan): Boolean = when (bridge.roleAction) {
        TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> bridge.instrument == "drums" && bridge.bridgeType in setOf(BridgeType.DRUM_FILL, BridgeType.BUILD)
        TransitionRoleAction.BASS_MOTION -> bridge.instrument == "bass" && bridge.bridgeType == BridgeType.BASS_WALK
        TransitionRoleAction.CHORD_MOTION -> bridge.instrument in setOf("pad", "strings") && bridge.bridgeType == BridgeType.CHORD_MOTION
        TransitionRoleAction.SUSTAINED_TEXTURE -> bridge.instrument in setOf("pad", "strings") && bridge.bridgeType == BridgeType.PAD_SUSTAIN
        TransitionRoleAction.CONTINUITY -> bridge.instrument in setOf("drums", "bass", "pad", "strings") && bridge.bridgeType == BridgeType.CONTINUITY
    }

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
            val details = bridgeDetails(decision.roleAction, source.roles)
            TransitionBridgePlan(
                outgoingInstanceId = source.outgoingInstanceId,
                incomingInstanceId = source.incomingInstanceId,
                outgoingHash = source.outgoing.sourceHash,
                incomingHash = source.incoming.sourceHash,
                arrangementSha256 = input.arrangementSha256,
                contextSha256 = input.contextSha256,
                roleAction = decision.roleAction,
                bridgeType = details.bridgeType,
                instrument = details.instrument,
                harmonicHandoff = decision.harmonicHandoff,
                rhythmicGesture = decision.rhythmicGesture,
                energyContour = decision.energyContour,
                rationale = boundedRationale(decision.rationale),
                placement = details.placement,
                leadBeats = if (details.placement == TransitionPlacement.NO_OP) 0 else decision.leadBeats ?: 1,
                tailBeats = if (details.placement == TransitionPlacement.NO_OP) 0 else decision.tailBeats
            )
        }
        val plan = EnsembleCohesionPlan(inputHash = input.inputHash, arrangementSha256 = input.arrangementSha256, contextSha256 = input.contextSha256, model = model, boundaries = boundaries, intensity = input.intensity)
        val validation = EnsembleCohesionValidator.validate(plan, input)
        require(validation.isValid) { "Qwen returned an invalid transition-cohesion plan: ${validation.errors.joinToString("; ")}" }
        plan.copy(validation = EnsembleCohesionValidationReport())
    }

    /** Model prose is display-only; make it safe and bounded before persistence. */
    private fun boundedRationale(value: String): String = value
        .replace(Regex("[^A-Za-z0-9 ,.'-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(180)
        .ifBlank { "Cohesive boundary handoff" }

    /** These are mechanical compatibility rules, not musical choices for the model. */
    private fun bridgeDetails(action: TransitionRoleAction, roles: TransitionBoundaryRoleEvidence): BridgeDetails = when (action) {
        TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> BridgeDetails("drums", BridgeType.DRUM_FILL)
        TransitionRoleAction.BASS_MOTION -> BridgeDetails("bass", BridgeType.BASS_WALK)
        TransitionRoleAction.CHORD_MOTION -> BridgeDetails(textureInstrument(roles.supported), BridgeType.CHORD_MOTION)
        TransitionRoleAction.SUSTAINED_TEXTURE -> BridgeDetails(textureInstrument(roles.supported), BridgeType.PAD_SUSTAIN)
        TransitionRoleAction.CONTINUITY -> BridgeDetails(
            listOf("drums", "bass", "pad", "strings").firstOrNull { it in roles.continuing }
                ?: throw IllegalArgumentException("No continuing instrument can perform a continuity no-op"),
            BridgeType.CONTINUITY, TransitionPlacement.NO_OP
        )
    }.also { details -> require(details.instrument in roles.supported) { "Qwen selected $action, but ${details.instrument} is not active at this boundary" } }

    private fun textureInstrument(supported: List<String>) = when {
        "pad" in supported -> "pad"
        "strings" in supported -> "strings"
        else -> throw IllegalArgumentException("Qwen selected a texture action without a supported texture instrument")
    }

    private data class BridgeDetails(
        val instrument: String,
        val bridgeType: BridgeType,
        val placement: TransitionPlacement = TransitionPlacement.OVERLAY_BOUNDARY
    )

    private fun modelInput(input: EnsembleCohesionInput) = EnsembleCohesionModelInput(
        boundaries = input.boundaries.map { boundary ->
            EnsembleCohesionBoundaryEvidence(
                outgoing = summary(boundary.outgoing, useLastChord = true),
                incoming = summary(boundary.incoming, useLastChord = false),
                allowedRoleActions = boundary.allowedRoleActions,
                roles = boundary.roles
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
            Every boundary object has exactly these keys: roleAction, harmonicHandoff, rhythmicGesture,
            energyContour, rationale, leadBeats, tailBeats. Choose roleAction only from that boundary's allowedRoleActions.
            The application selects the compatible instrument, bridge type, and no-op placement from boundary-local role evidence.
            leadBeats is 1 through the outgoing meter numerator and tailBeats is 0 or 1. The window overlays the
            existing boundary and never extends the song. Preserve tempo and meter.
            harmonicHandoff is HOLD or STEP_TO_INCOMING; rhythmicGesture is FILL, PICKUP, or SUSTAIN;
            energyContour is HOLD, RISE, or FALL. rationale is brief plain musical text. The approved full melody is
            immutable in Cohesion; a post-connection melody change must publish and pass review as a new full candidate.
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
        BridgeType.BASS_WALK to EnsembleTransitionPattern.BASS_WALK,
        BridgeType.CHORD_MOTION to EnsembleTransitionPattern.CHORD_MOTION,
        BridgeType.PAD_SUSTAIN to EnsembleTransitionPattern.PAD_SUSTAIN
    )
}

private enum class EnsembleTransitionPattern { DRUM_FILL, BASS_WALK, CHORD_MOTION, PAD_SUSTAIN }

/** Deterministic renderer consumes only pattern-library strategies; melody source is never read or changed. */
object DeterministicTransitionBridgeEngine {
    /** Render one validated local bridge; continuity is intentionally an empty, auditable no-op. */
    fun write(path: Path, cohesion: EnsembleCohesionInput, input: TransitionContext, plan: TransitionBridgePlan) {
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
        if (plan.placement == TransitionPlacement.NO_OP) {
            require(plan.bridgeType == BridgeType.CONTINUITY) { "Only continuity can publish a no-op bridge" }
        } else when (requireNotNull(EnsembleTransitionPatternLibrary.forBridge[plan.bridgeType]) { "Unsupported Ensemble Cohesion bridge pattern" }) {
            EnsembleTransitionPattern.DRUM_FILL -> {
                drumNotes(track, requireNotNull(cohesion.acceptedFullSongGrooveMap) { "Drum Cohesion requires the accepted full-song groove map" }, input, plan, boundary, length, velocityBase)
            }
            EnsembleTransitionPattern.BASS_WALK -> {
                val from = requireNotNull(outgoing) { "Bass Cohesion requires outgoing harmony or key evidence" }
                val to = requireNotNull(incoming) { "Bass Cohesion requires incoming harmony or key evidence" }
                requireNotNull(cohesion.acceptedFullSongGrooveMap) { "Bass Cohesion requires the accepted full-song groove map" }
                pitchedStarts(plan, boundary, beat).forEach { start ->
                    val selected = handoffHarmony(plan, from, to, start, boundary, beat)
                    note(track, 0, 36 + selected.root, velocityBase, start, noteEnd(plan, start, length, boundary, beat))
                }
            }
            EnsembleTransitionPattern.CHORD_MOTION -> {
                val from = requireNotNull(outgoing) { "Chord-motion Cohesion requires outgoing harmony or key evidence" }
                val to = requireNotNull(incoming) { "Chord-motion Cohesion requires incoming harmony or key evidence" }
                pitchedStarts(plan, boundary, beat).forEach { start ->
                    val selected = handoffHarmony(plan, from, to, start, boundary, beat)
                    selected.intervals.forEach { interval -> note(track, 0, 60 + selected.root + interval, velocityBase - 10, start, noteEnd(plan, start, length, boundary, beat)) }
                }
            }
            EnsembleTransitionPattern.PAD_SUSTAIN -> {
                val from = requireNotNull(outgoing ?: incoming) { "Sustained Cohesion requires harmony or key evidence" }
                val to = incoming ?: from
                val carried = carriedPitches(cohesion, plan.instrument, input)
                pitchedStarts(plan, boundary, beat).forEach { start ->
                    val selected = handoffHarmony(plan, from, to, start, boundary, beat)
                    val common = from.pitchClasses().firstOrNull { it in selected.pitchClasses() }
                    val retained = carried.filter { pitch -> pitch.mod(12) in selected.pitchClasses() }
                    (retained.ifEmpty { listOf(60 + (common ?: selected.root)) }).forEach { pitch ->
                        note(track, 0, pitch, velocityBase - 12, start, noteEnd(plan, start, length, boundary, beat))
                    }
                }
            }
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), length)); publishMidi(path, sequence, ppq, length, plan.placement == TransitionPlacement.NO_OP)
    }
    /** Map bridge gestures to real local time; no persisted field is merely descriptive. */
    private fun pitchedStarts(plan: TransitionBridgePlan, boundary: Long, beat: Long): List<Long> = (when (plan.rhythmicGesture) {
        RhythmicGesture.SUSTAIN -> listOf(0L)
        RhythmicGesture.PICKUP -> listOf((boundary - beat).coerceAtLeast(0L))
        RhythmicGesture.FILL -> generateSequence(0L) { it + beat }.takeWhile { it < boundary }.toList().ifEmpty { listOf(0L) }
    } + if (plan.tailBeats > 0) listOf(boundary) else emptyList()).distinct().sorted()
    private fun noteEnd(plan: TransitionBridgePlan, start: Long, length: Long, boundary: Long, beat: Long): Long = when (plan.rhythmicGesture) {
        RhythmicGesture.SUSTAIN -> if (start < boundary && boundary < length) boundary else length
        RhythmicGesture.PICKUP, RhythmicGesture.FILL -> minOf(length, start + (beat * 3L / 4L).coerceAtLeast(1L))
    }
    private fun handoffHarmony(plan: TransitionBridgePlan, outgoing: Harmony, incoming: Harmony, tick: Long, boundary: Long, beat: Long): Harmony = when (plan.harmonicHandoff) {
        HarmonicHandoff.HOLD -> if (tick >= boundary) incoming else outgoing
        HarmonicHandoff.STEP_TO_INCOMING -> if (tick >= (boundary - beat).coerceAtLeast(0L)) incoming else outgoing
    }
    /** Reuse an actual continuing sustained voicing instead of adding a reset-octave bridge chord. */
    private fun carriedPitches(cohesion: EnsembleCohesionInput, role: String, input: TransitionContext): List<Int> {
        val boundary = input.outgoingStartTick + input.outgoing.durationTicks
        return cohesion.generatedRoles.singleOrNull { it.role == role }?.notes
            ?.filter { it.startTick < boundary && it.endTick >= boundary }
            ?.map(CohesionMelodyNote::pitch)?.distinct()?.sorted().orEmpty()
    }
    private fun drumNotes(track: javax.sound.midi.Track, map: FullSongGrooveMap, input: TransitionContext, plan: TransitionBridgePlan, boundary: Long, length: Long, velocityBase: Int) {
        val startGlobal = input.outgoingStartTick + input.outgoing.durationTicks - boundary
        val endGlobal = startGlobal + length
        val candidates = map.points.filter { point ->
            point.occurrenceId in setOf(input.outgoingInstanceId, input.incomingInstanceId) && point.globalTick in startGlobal until endGlobal
        }.mapNotNull { point ->
            (point.globalTick + point.deviationTicks - startGlobal).takeIf { it in 0 until length }?.let { tick -> point to tick }
        }.sortedBy { it.second }
        require(candidates.isNotEmpty()) { "Drum Cohesion has no approved groove points in its active boundary span" }
        val selected = when (plan.rhythmicGesture) {
            RhythmicGesture.FILL -> candidates
            RhythmicGesture.PICKUP -> candidates.takeLast(2)
            RhythmicGesture.SUSTAIN -> candidates.filter { it.first.subdivision == 0 }.takeLast(1)
        }
        selected.forEachIndexed { index, (point, tick) ->
            val pitch = when {
                point.subdivision == 0 -> 36
                index == selected.lastIndex -> 38
                else -> 42
            }
            val step = candidates.zipWithNext().firstOrNull { it.first.second == tick }?.let { it.second.second - tick } ?: (map.ppq.toLong() / map.subdivisionsPerBeat).coerceAtLeast(1L)
            note(track, 9, pitch, (velocityBase + index * 4).coerceAtMost(112), tick, minOf(length, tick + (step / 2).coerceAtLeast(1L)))
        }
    }
    private fun publishMidi(path: Path, sequence: Sequence, ppq: Int, length: Long, allowNoNotes: Boolean) {
        Files.createDirectories(path.parent); val temporary = path.resolveSibling(".${path.fileName}.tmp")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write transition MIDI" }
            val reread = MidiSystem.getSequence(temporary.toFile())
            require(reread.divisionType == Sequence.PPQ && reread.resolution == ppq && reread.tickLength >= length) { "Transition MIDI round-trip timing mismatch" }
            val messages = reread.tracks.flatMap { track -> (0 until track.size()).map { track[it].message as? ShortMessage } }.filterNotNull()
            val on = messages.count { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }; val off = messages.count { it.command == ShortMessage.NOTE_OFF || it.command == ShortMessage.NOTE_ON && it.data2 == 0 }
            require(on == off && (allowNoNotes || on > 0)) { "Transition MIDI has invalid note pairs" }; move(temporary, path)
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
    private data class Harmony(val root: Int, val intervals: List<Int>) {
        fun pitchClasses(): List<Int> = intervals.map { (root + it).mod(12) }
    }
    private fun move(from: Path, to: Path) { try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) } }
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
        val merged = roleNotes(targetTrack).toMutableList()
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
                // A bridge replaces its exact same-pitch overlap; it never stacks an
                // unreviewed duplicate attack over the accepted role performance.
                val colliding = merged.filter { it.channel == shifted.channel && it.pitch == shifted.pitch && it.start < shifted.end && shifted.start < it.end }
                colliding.forEach { existing ->
                    merged.remove(existing)
                    if (existing.start < shifted.start) merged += existing.copy(end = shifted.start)
                    if (shifted.end < existing.end) merged += existing.copy(start = shifted.end)
                }
                merged += shifted
            }
        }
        (targetTrack.size() - 1 downTo 0).forEach { index ->
            val event = targetTrack[index]; val message = event.message as? ShortMessage
            if (message?.command == ShortMessage.NOTE_ON || message?.command == ShortMessage.NOTE_OFF) targetTrack.remove(event)
        }
        merged.sortedWith(compareBy<RoleNote> { it.start }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.end }).forEach { note ->
            targetTrack.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, note.channel, note.pitch, note.velocity), note.start))
            targetTrack.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, note.channel, note.pitch, 0), note.end))
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
    private fun roleNotes(track: javax.sound.midi.Track): List<RoleNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val result = mutableListOf<RoleNote>()
        (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                active[key]?.removeFirstOrNull()?.let { result += RoleNote(message.channel, message.data1, it.second, it.first, event.tick) }
            }
        }
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
        plan.boundaries.forEachIndexed { index, bridge ->
            val bridgePath = root.resolve(bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId))
            DeterministicTransitionBridgeEngine.write(bridgePath, input, input.boundaries[index], bridge)
            atomicWrite(root.resolve(audit(bridge.outgoingInstanceId, bridge.incomingInstanceId)), json.encodeToString(
                CohesionBoundaryAudit.serializer(), CohesionBoundaryAudit(bridge, digest(bridgePath), bridgeMidiEvidence(bridgePath))
            ))
        }
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
        val views = OccurrenceMidiArtifactResolver().resolve(root, project, project.envelope.structureOccurrences.mapIndexed { index, occurrence ->
            SectionInstance(index, occurrence.partId, occurrence.id)
        }).associateBy(OccurrenceMidiArtifact::occurrenceId)
        val existing = project.workflow.cohesion?.takeIf { it.inputSha256 == input.inputHash }
        val occurrences = if (approved && existing != null) {
            require(existing.occurrences.map(CohesionOccurrenceReference::instanceId) == project.envelope.structureOccurrences.map { it.id }) {
                "Reviewed Cohesion occurrences no longer match Structure"
            }
            existing.occurrences.map { reference ->
                val source = views.getValue(reference.instanceId)
                val path = root.resolve(reference.result.file)
                require(reference.sourceSha256 == source.canonicalFullMelodySha256 && reference.cohesionInputSha256 == input.inputHash &&
                    Files.isRegularFile(path) && digest(path) == reference.result.sha256) {
                    "Reviewed Cohesion occurrence '${reference.instanceId}' changed before approval"
                }
                reference.copy(approved = true)
            }
        } else project.envelope.structureOccurrences.map { occurrence ->
            val source = views.getValue(occurrence.id)
            val relative = CohesionOccurrenceArtifactPaths.enhancedOutput(input.inputHash, occurrence.id)
            val evidence = occurrenceEvidence[occurrence.id]
            if (evidence == null) {
                require(plan.boundaries.isEmpty()) { "Cohesion is missing melody evidence for occurrence '${occurrence.id}'" }
                Files.createDirectories(root.resolve(relative).parent)
                Files.copy(source.path, root.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
            } else {
                require(source.canonicalFullMelodySha256 == evidence.sourceHash) { "Cohesion occurrence source is stale" }
                Files.createDirectories(root.resolve(relative).parent)
                Files.copy(source.path, root.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
            }
            CohesionOccurrenceReference(occurrence.id, source.canonicalFullMelodySha256, WorkflowArtifactReference(relative, digest(root.resolve(relative))), approved, input.inputHash)
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
    private fun bridgeMidiEvidence(path: Path): List<CohesionMelodyNote> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val notes = mutableListOf<CohesionMelodyNote>()
        MidiSystem.getSequence(path.toFile()).tracks.forEach { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
            else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                active[key]?.removeFirstOrNull()?.let { start ->
                    notes += CohesionMelodyNote("bridge-${notes.size.toString().padStart(4, '0')}", message.channel, message.data1, start.second, start.first, event.tick)
                }
            }
        } }
        require(active.values.all { it.isEmpty() }) { "Rendered Cohesion bridge has an unclosed note" }
        return notes.sortedWith(compareBy<CohesionMelodyNote> { it.startTick }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.endTick })
    }
    private fun digest(path: Path) = sha256(Files.readAllBytes(path)); private fun digestText(text: String) = sha256(text.toByteArray(StandardCharsets.UTF_8))
}

/** Builds evidence from approved full-melody views, saved Structure, approved arrangement, and profile/mood settings. */
object EnsembleTransitionContextFactory {
    /** Build one fully hash-bound Cohesion input from approved adjacent arrangement and groove evidence. */
    fun build(root: Path, project: Project, planning: SongPlanningInput, arrangement: DetailedArrangement, arrangementSha256: String, contextSha256: String, acceptedFullSongGrooveMap: FullSongGrooveMap, intensity: EnsembleCohesionEnhancementIntensity = EnsembleCohesionEnhancementIntensity.BALANCED): EnsembleCohesionInput {
        require(HASH.matches(arrangementSha256) && HASH.matches(contextSha256)) { "Cohesion requires approved arrangement and context identities" }
        val sections = planning.sectionsWithIdentity(); require(arrangement.sections.map { it.instanceId } == sections.map { it.instanceId }) { "Approved arrangement does not match saved Structure" }
        val artifacts = OccurrenceMidiArtifactResolver().resolve(root, project, sections.map { section ->
            SectionInstance(section.index, section.partId, section.instanceId)
        }).associateBy(OccurrenceMidiArtifact::occurrenceId)
        val evidence = sections.associate { section ->
            val artifact = artifacts.getValue(section.instanceId); val analysis = planning.analyses.getValue(section.partId); val arranged = arrangement.sections.first { it.instanceId == section.instanceId }; val reference = requireNotNull(project.parts.first { it.id == section.partId }.analysis)
            require(artifact.endTick - artifact.startTick == analysis.durationTicks && artifact.ppq == analysis.ppq) {
                "Approved full-melody window '${section.instanceId}' does not match arrangement timing."
            }
            section.instanceId to TransitionMusicalEvidence(
                section.partId, artifact.canonicalFullMelodySha256, sha256(Files.readAllBytes(root.resolve(reference.file))), analysis.ppq,
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
        val generatedRoles = supported.map { role ->
            val path = root.resolve("midi/generated/$role.mid")
            require(Files.isRegularFile(path)) { "Baseline generated $role MIDI is missing" }
            val sequence = MidiSystem.getSequence(path.toFile())
            GeneratedRoleEvidence(role, sha256(Files.readAllBytes(path)), sequence.resolution, sequence.tickLength, melody(path))
        }
        val policy = policy(project, contextSha256, supported)
        val boundaries = if (supported.isEmpty()) emptyList() else sections.zipWithNext().map { (outgoing, incoming) ->
            val outgoingEvidence = evidence.getValue(outgoing.instanceId)
            val incomingEvidence = evidence.getValue(incoming.instanceId)
            val outgoingStart = sections.take(outgoing.index).sumOf { evidence.getValue(it.instanceId).durationTicks }
            val roles = boundaryRoles(generatedRoles, outgoingStart, outgoingStart + outgoingEvidence.durationTicks, outgoingStart + outgoingEvidence.durationTicks + incomingEvidence.durationTicks)
            TransitionContext(
                outgoing.instanceId, incoming.instanceId, outgoingEvidence, incomingEvidence,
                localActions(policy.allowedActions, roles), policy, roles,
                outgoingStart, outgoingStart + outgoingEvidence.durationTicks
            )
        }
        val seed = EnsembleCohesionInput(inputHash = "", structureSha256 = sha256(project.envelope.structureOccurrences.joinToString("|") { "${it.id}:${it.partId}:${it.revision}" }.toByteArray()), arrangementSha256 = arrangementSha256, contextSha256 = contextSha256, supportedInstruments = supported, boundaries = boundaries, intensity = intensity, occurrences = sections.map { SongOccurrenceEvidence(it.instanceId, evidence.getValue(it.instanceId)) }, generatedRoles = generatedRoles, acceptedFullSongGrooveMap = acceptedFullSongGrooveMap)
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
    /** Derive boundary activity from accepted generated MIDI, so a deliberate rest never becomes a bridge target. */
    private fun boundaryRoles(generatedRoles: List<GeneratedRoleEvidence>, outgoingStart: Long, boundary: Long, incomingEnd: Long): TransitionBoundaryRoleEvidence {
        fun active(start: Long, end: Long) = generatedRoles
            .filter { it.role in GENERATED_ROLES && it.notes.any { note -> note.startTick < end && start < note.endTick } }
            .map(GeneratedRoleEvidence::role).distinct().sorted()
        val outgoingActive = active(outgoingStart, boundary); val incomingActive = active(boundary, incomingEnd)
        return TransitionBoundaryRoleEvidence(
            outgoingActive = outgoingActive, incomingActive = incomingActive,
            entering = (incomingActive - outgoingActive).sorted(), exiting = (outgoingActive - incomingActive).sorted(),
            continuing = (outgoingActive intersect incomingActive).sorted(),
            supported = (outgoingActive + incomingActive).distinct().sorted()
        )
    }
    private fun localActions(actions: List<TransitionRoleAction>, roles: TransitionBoundaryRoleEvidence): List<TransitionRoleAction> = actions.filter { action -> when (action) {
        TransitionRoleAction.DRUM_FILL, TransitionRoleAction.DYNAMICS_AUTOMATION -> "drums" in roles.supported
        TransitionRoleAction.BASS_MOTION -> "bass" in roles.supported
        TransitionRoleAction.CHORD_MOTION, TransitionRoleAction.SUSTAINED_TEXTURE -> roles.supported.any { it in setOf("pad", "strings") }
        TransitionRoleAction.CONTINUITY -> roles.continuing.isNotEmpty()
    } }.distinct()
    private fun policyAction(value: String): TransitionRoleAction? = when (value) {
        "drum-fill" -> TransitionRoleAction.DRUM_FILL
        "bass-motion" -> TransitionRoleAction.BASS_MOTION
        "chord-motion" -> TransitionRoleAction.CHORD_MOTION
        "sustained-texture" -> TransitionRoleAction.SUSTAINED_TEXTURE
        "dynamics-automation" -> TransitionRoleAction.DYNAMICS_AUTOMATION
        "continuity" -> TransitionRoleAction.CONTINUITY
        else -> null
    }
    private fun arrangementEvidence(section: SongPlanningSectionInstance, arrangement: DetailedArrangementSection) = TransitionArrangementEvidence(section.occurrenceHash, arrangement.role, arrangement.instruments.map { TransitionInstrumentEvidence(it.name, it::class.simpleName.orEmpty(), density(it), it.mode == InstrumentMode.GENERATED) }.sortedBy { it.instrument }, sha256(Json { encodeDefaults = true }.encodeToString(StructureVariationOverrides.serializer(), section.variationOverrides).toByteArray()))
    private fun density(instrument: DetailedInstrumentPlan): Double? = when (instrument) { is BassInstrumentPlan -> instrument.density; is DrumsInstrumentPlan -> instrument.density; is PadInstrumentPlan -> instrument.density; is StringsInstrumentPlan -> instrument.density; else -> null }
    private fun boundary(path: Path, duration: Long): TransitionBoundarySummary { val sequence = MidiSystem.getSequence(path.toFile()); val notes = sequence.tracks.flatMap { track -> (0 until track.size()).map { track[it] } }.mapNotNull { event -> (event.message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { event.tick } }; return TransitionBoundarySummary(notes.any { it == 0L }, notes.any { it >= duration }, notes.minOrNull(), notes.maxOrNull()) }
    private fun melody(path: Path): List<CohesionMelodyNote> {
        val sequence = MidiSystem.getSequence(path.toFile())
        return MelodyIdentityBuilder.build(path, sequence.resolution * 4L).notes.map { note ->
            CohesionMelodyNote(note.id.value, note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick)
        }
    }
    private val HASH = Regex("[0-9a-f]{64}")
    private val GENERATED_ROLES = setOf("bass", "drums", "pad", "strings")
}
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
