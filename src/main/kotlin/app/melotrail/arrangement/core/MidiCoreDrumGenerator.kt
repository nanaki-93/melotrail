package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure Drum-generation result containing semantic hits and its validation evidence. */
data class MidiCoreDrumGenerationResult(
    val context: MidiCoreGenerationContext,
    val candidate: MidiCoreRoleCandidate,
    val validation: MidiCoreRoleValidationResult,
) {
    init {
        require(context.role == CandidateRole.DRUMS) { "Drum generation context must select the Drums role" }
        require(candidate.role == CandidateRole.DRUMS && candidate.occurrenceId == context.occurrence.id) {
            "Drum candidate must remain scoped to the generation context"
        }
        require(validation.report.contextSha256 == context.contextSha256 && validation.report.role == CandidateRole.DRUMS) {
            "Drum validation evidence must bind the generation context"
        }
    }

    /** True when this candidate passed every blocking target-role policy. */
    val accepted: Boolean get() = validation is MidiCoreRoleValidationResult.Accepted
}

/** Deterministic Drum generator over complete authored variants and semantic dependencies. */
object MidiCoreDrumGenerator {
    /** Zero-based MIDI channel for the musician-facing Drum channel 10. */
    const val MIDI_CHANNEL = 9

    /** Generate one semantic Drum candidate and validate it before publication. */
    fun generate(context: MidiCoreGenerationContext): MidiCoreDrumGenerationResult {
        require(context.role == CandidateRole.DRUMS) { "Drum generation requires a Drums context" }
        val candidate = MidiCoreRoleCandidate(
            role = CandidateRole.DRUMS,
            occurrenceId = context.occurrence.id,
            channel = MIDI_CHANNEL,
            events = generateNotes(context),
        )
        return MidiCoreDrumGenerationResult(context, candidate, MidiCoreRoleValidator.validate(context, candidate))
    }

    /** Generate a deterministic family of complete authored-groove alternatives for one occurrence. */
    fun generateAlternatives(
        context: MidiCoreGenerationContext,
        count: Int = 2,
    ): List<MidiCoreDrumGenerationResult> {
        require(context.role == CandidateRole.DRUMS) { "Drum alternatives require a Drums context" }
        require(count in 1..MidiCorePatternCatalog.drumGrooves.size) {
            "Drum alternative count must be between 1 and ${MidiCorePatternCatalog.drumGrooves.size}"
        }
        val patterns = MidiCorePatternCatalog.drumGrooves
        val first = patterns.indexOfFirst { it.id == context.patternId }.takeIf { it >= 0 } ?: 0
        return (0 until count).map { index ->
            val pattern = patterns[(first + index) % patterns.size]
            val alternativeContext = context.copy(
                patternId = pattern.id,
                generator = context.generator.copy(
                    patternId = pattern.id,
                    seed = if (index == 0) context.seed else context.seed + index.toLong() * SEED_STEP,
                ),
            )
            generate(alternativeContext)
        }
    }

    /** Expand one complete groove, optional phrase fill, and accepted Bass kick intent into note events. */
    private fun generateNotes(context: MidiCoreGenerationContext): List<MidiCoreCandidateEvent.Note> {
        if (context.sectionPolicy.density == 0.0) return emptyList()
        val groove = selectedGroove(context)
        val attacks = authoredAttacks(context, groove).toMutableMap()
        addPhraseFill(context, attacks)
        addAcceptedBassKicks(context, attacks)
        return attacks.values
            .sortedWith(compareBy<DrumAttack> { it.startTick }.thenBy { it.hit.ordinal })
            .map { attack ->
                MidiCoreCandidateEvent.Note(
                    startTick = attack.startTick,
                    endTick = noteEnd(context, attack),
                    pitch = attack.pitch,
                    velocity = velocity(context, attack),
                )
            }
    }

    /** Resolve a requested groove and choose another whole authored variant only when density requires it. */
    private fun selectedGroove(context: MidiCoreGenerationContext): MidiCoreDrumPattern {
        val requested = MidiCorePatternCatalog.drumGrooves.singleOrNull { it.id == context.patternId }
            ?: grooveForDirectFill(context)
        val budget = densityBudget(context)
        val compatible = MidiCorePatternCatalog.drumGrooves.filter { authoredAttackCount(context, it) <= budget }
        if (requested in compatible) return requested
        return compatible.minWithOrNull(
            compareBy<MidiCoreDrumPattern> { abs(it.steps.size - requested.steps.size) }
                .thenBy { it.id },
        ) ?: requested
    }

    /** Pair a directly selected transition fill with a whole groove that matches explicit section intent. */
    private fun grooveForDirectFill(context: MidiCoreGenerationContext): MidiCoreDrumPattern {
        val id = when {
            context.sectionPolicy.purpose == MidiCoreSectionPurpose.BRIDGE -> MidiCoreDrumGroovePatternId.HALF_TIME_POCKET.id
            context.sectionPolicy.purpose in setOf(MidiCoreSectionPurpose.CHORUS, MidiCoreSectionPurpose.PRE_CHORUS) &&
                context.sectionPolicy.energy >= HIGH_ENERGY_THRESHOLD -> MidiCoreDrumGroovePatternId.LIFT_BUILD.id
            context.sectionPolicy.purpose in setOf(MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO) ||
                context.sectionPolicy.energy <= LOW_ENERGY_THRESHOLD -> MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id
            else -> MidiCoreDrumGroovePatternId.LAZY_SWING.id
        }
        return MidiCorePatternCatalog.drumGroove(id)
    }

    /** Count complete authored attacks, including the selected phrase fill, without deleting any steps. */
    private fun authoredAttackCount(context: MidiCoreGenerationContext, groove: MidiCoreDrumPattern): Int {
        val keys = mutableSetOf<Pair<Long, MidiCoreDrumHit>>()
        val bars = barWindows(context)
        bars.forEach { (start, end) ->
            groove.steps.forEach { step ->
                val attack = start + step.sixteenth.toLong() * context.tickGrid.ticksPerSubdivision
                if (attack in context.occurrence.startTick until end) keys += attack to step.hit
            }
        }
        val fill = effectiveFill(context)?.let(MidiCorePatternCatalog::drumFill)
        if (fill != null && bars.isNotEmpty()) {
            val (start, end) = bars.last()
            fill.steps.forEach { step ->
                val attack = start + step.sixteenth.toLong() * context.tickGrid.ticksPerSubdivision
                if (attack in context.occurrence.startTick until end) keys += attack to step.hit
            }
        }
        return keys.size
    }

    /** Materialize every authored groove step on each occurrence bar without density decimation. */
    private fun authoredAttacks(
        context: MidiCoreGenerationContext,
        groove: MidiCoreDrumPattern,
    ): Map<Pair<Long, MidiCoreDrumHit>, DrumAttack> {
        val attacks = linkedMapOf<Pair<Long, MidiCoreDrumHit>, DrumAttack>()
        barWindows(context).forEach { (start, end) ->
            groove.steps.forEach { step ->
                val attack = start + step.sixteenth.toLong() * context.tickGrid.ticksPerSubdivision
                if (attack in context.occurrence.startTick until end) {
                    attacks[attack to step.hit] = DrumAttack(attack, step.hit, step.velocityOffset, false)
                }
            }
        }
        return attacks
    }

    /** Apply one explicit fill to the final occurrence bar, replacing only same-hit boundary attacks. */
    private fun addPhraseFill(
        context: MidiCoreGenerationContext,
        attacks: MutableMap<Pair<Long, MidiCoreDrumHit>, DrumAttack>,
    ) {
        val fill = effectiveFill(context)?.let(MidiCorePatternCatalog::drumFill) ?: return
        val bar = barWindows(context).lastOrNull() ?: return
        fill.steps.forEach { step ->
            val attack = bar.first + step.sixteenth.toLong() * context.tickGrid.ticksPerSubdivision
            if (attack in context.occurrence.startTick until bar.second) {
                attacks[attack to step.hit] = DrumAttack(attack, step.hit, step.velocityOffset, true)
            }
        }
    }

    /** Add restrained off-beat kick intent from accepted Bass without rewriting either authored pattern. */
    private fun addAcceptedBassKicks(
        context: MidiCoreGenerationContext,
        attacks: MutableMap<Pair<Long, MidiCoreDrumHit>, DrumAttack>,
    ) {
        val bass = context.dependency(CandidateRole.BASS)?.notes.orEmpty()
        if (bass.isEmpty()) return
        val available = (densityBudget(context) - attacks.size).coerceAtLeast(0)
        val perBarLimit = contextualKickLimitPerBar(context)
        if (available == 0 || perBarLimit == 0) return
        val grid = context.tickGrid.ticksPerSubdivision
        val beat = context.tickGrid.ticksPerBeat
        val existingKicks = attacks.values.filter { it.hit == MidiCoreDrumHit.KICK }.map { it.startTick }.toSet()
        val existingSnares = attacks.values.filter { it.hit == MidiCoreDrumHit.SNARE }.map { it.startTick }.toSet()
        val transitionBar = effectiveFill(context)?.let { barWindows(context).lastOrNull()?.first }
        bass.asSequence()
            .map(MidiCoreGenerationNote::startTick)
            .filter { it in context.occurrence.startTick until context.occurrence.endTick }
            .filter { it % grid == 0L }
            .filter { (it - context.occurrence.startTick) % beat != 0L }
            .filter { sixteenthInBar(context, it) in CONTEXTUAL_KICK_SIXTEENTHS }
            .filter { it !in existingKicks && it !in existingSnares }
            .filter { transitionBar == null || it < transitionBar }
            .distinct()
            .sorted()
            .groupBy { barStart(context, it) }
            .toSortedMap()
            .values
            .flatMap { starts -> starts.take(perBarLimit) }
            .take(minOf(available, MAX_CONTEXTUAL_KICKS))
            .forEach { start ->
                attacks[start to MidiCoreDrumHit.KICK] = DrumAttack(start, MidiCoreDrumHit.KICK, 1, false, true)
            }
    }

    /** Limit dependency-derived kick support so low-energy sections and transitions retain their authored shape. */
    private fun contextualKickLimitPerBar(context: MidiCoreGenerationContext): Int = when {
        context.sectionPolicy.purpose in setOf(MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO) -> 0
        context.sectionPolicy.energy <= LOW_ENERGY_THRESHOLD -> 1
        else -> MAX_CONTEXTUAL_KICKS_PER_BAR
    }

    /** Return one occurrence-relative bar start without relying on global source bar alignment. */
    private fun barStart(context: MidiCoreGenerationContext, tick: Long): Long =
        context.occurrence.startTick + (tick - context.occurrence.startTick) / context.tickGrid.ticksPerBar * context.tickGrid.ticksPerBar

    /** Return the sixteenth position inside an occurrence-relative bar for authored kick placement. */
    private fun sixteenthInBar(context: MidiCoreGenerationContext, tick: Long): Int =
        ((tick - barStart(context, tick)) / context.tickGrid.ticksPerSubdivision).toInt()

    /** End a hit on the shared grid and never cross its bar or occurrence boundary. */
    private fun noteEnd(context: MidiCoreGenerationContext, attack: DrumAttack): Long {
        val barEnd = barWindows(context).firstOrNull { attack.startTick in it.first until it.second }?.second
            ?: context.occurrence.endTick
        return minOf(context.occurrence.endTick, barEnd, attack.startTick + context.tickGrid.ticksPerSubdivision)
    }

    /** Shape deterministic velocity from profile, energy, purpose, phrase position, and authored accent. */
    private fun velocity(context: MidiCoreGenerationContext, attack: DrumAttack): Int {
        val profileLift = ((context.sectionPolicy.energy - 0.5) * ENERGY_VELOCITY_SPAN).roundToInt()
        val hitLift = when (attack.hit) {
            MidiCoreDrumHit.KICK -> 4
            MidiCoreDrumHit.SNARE -> 2
            MidiCoreDrumHit.CLOSED_HAT -> -8
            MidiCoreDrumHit.OPEN_HAT -> -2
        }
        val purposeLift = when (context.sectionPolicy.purpose) {
            MidiCoreSectionPurpose.CHORUS -> 4
            MidiCoreSectionPurpose.PRE_CHORUS -> 2
            MidiCoreSectionPurpose.BRIDGE -> -2
            MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO -> -4
            else -> 0
        }
        val phraseLift = if (attack.startTick == context.occurrence.startTick ||
            (attack.startTick - context.occurrence.startTick) % context.tickGrid.ticksPerBar == 0L
        ) 3 else 0
        val fillLift = if (attack.fill) 2 else 0
        val seedJitter = Math.floorMod(context.seed + attack.startTick, 3L).toInt() - 1
        return (context.performanceProfile.velocity + profileLift + attack.velocityOffset + hitLift + purposeLift + phraseLift + fillLift + seedJitter)
            .coerceIn(1, 127)
    }

    /** Return all non-empty bar windows of the selected occurrence, clipping a final partial bar safely. */
    private fun barWindows(context: MidiCoreGenerationContext): List<Pair<Long, Long>> = buildList {
        var start = context.occurrence.startTick
        while (start < context.occurrence.endTick) {
            val end = minOf(context.occurrence.endTick, start + context.tickGrid.ticksPerBar)
            if (end > start) add(start to end)
            start = end
        }
    }

    /** Resolve the explicit phrase fill, including a fill pattern used directly as the requested pattern. */
    private fun effectiveFill(context: MidiCoreGenerationContext): String? = context.sectionPolicy.fillPatternId
        ?: MidiCoreDrumFillPatternId.entries.singleOrNull { it.id == context.patternId }?.id

    /** Calculate the target role's deterministic drum-hit budget for this occurrence. */
    private fun densityBudget(context: MidiCoreGenerationContext): Int =
        ceil((context.occurrence.endTick - context.occurrence.startTick).toDouble() / context.tickGrid.ticksPerQuarter * 8.0 * context.sectionPolicy.density)
            .toInt()

    private data class DrumAttack(
        val startTick: Long,
        val hit: MidiCoreDrumHit,
        val velocityOffset: Int,
        val fill: Boolean,
        val contextualBassKick: Boolean = false,
    ) {
        val pitch: Int get() = when (hit) {
            MidiCoreDrumHit.KICK -> 36
            MidiCoreDrumHit.SNARE -> 38
            MidiCoreDrumHit.CLOSED_HAT -> 42
            MidiCoreDrumHit.OPEN_HAT -> 46
        }
    }

    private const val ENERGY_VELOCITY_SPAN = 20.0
    private const val LOW_ENERGY_THRESHOLD = 0.33
    private const val HIGH_ENERGY_THRESHOLD = 0.67
    private const val MAX_CONTEXTUAL_KICKS = 4
    private const val MAX_CONTEXTUAL_KICKS_PER_BAR = 2
    private val CONTEXTUAL_KICK_SIXTEENTHS = setOf(6, 10)
    private const val SEED_STEP = 7_919L
}
