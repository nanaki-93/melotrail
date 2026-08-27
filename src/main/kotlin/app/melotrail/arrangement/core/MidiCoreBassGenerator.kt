package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure Bass-generation result containing semantic notes and its validation evidence. */
data class MidiCoreBassGenerationResult(
    val context: MidiCoreGenerationContext,
    val candidate: MidiCoreRoleCandidate,
    val validation: MidiCoreRoleValidationResult,
) {
    init {
        require(context.role == CandidateRole.BASS) { "Bass generation context must select the Bass role" }
        require(candidate.role == CandidateRole.BASS && candidate.occurrenceId == context.occurrence.id) {
            "Bass candidate must remain scoped to the generation context"
        }
        require(validation.report.contextSha256 == context.contextSha256 && validation.report.role == CandidateRole.BASS) {
            "Bass validation evidence must bind the generation context"
        }
    }

    /** True when this candidate passed every blocking target-role policy. */
    val accepted: Boolean get() = validation is MidiCoreRoleValidationResult.Accepted
}

/** Deterministic Bass generator over exact authority, curated patterns, and semantic dependencies. */
object MidiCoreBassGenerator {
    /** Zero-based MIDI channel for the musician-facing Bass channel 3. */
    const val MIDI_CHANNEL = 2

    /** Generate one semantic Bass candidate and validate it before publication. */
    fun generate(context: MidiCoreGenerationContext): MidiCoreBassGenerationResult {
        require(context.role == CandidateRole.BASS) { "Bass generation requires a Bass context" }
        val candidate = MidiCoreRoleCandidate(
            role = CandidateRole.BASS,
            occurrenceId = context.occurrence.id,
            channel = MIDI_CHANNEL,
            events = generateNotes(context),
        )
        return MidiCoreBassGenerationResult(context, candidate, MidiCoreRoleValidator.validate(context, candidate))
    }

    /** Generate a deterministic family of distinct curated-pattern alternatives for one occurrence. */
    fun generateAlternatives(
        context: MidiCoreGenerationContext,
        count: Int = 2,
    ): List<MidiCoreBassGenerationResult> {
        require(context.role == CandidateRole.BASS) { "Bass alternatives require a Bass context" }
        require(count in 1..MidiCoreBassPatternId.entries.size) {
            "Bass alternative count must be between 1 and ${MidiCoreBassPatternId.entries.size}"
        }
        val patterns = MidiCoreBassPatternId.entries
        val first = patterns.indexOfFirst { it.id == context.patternId }
        require(first >= 0) { "Bass context pattern is not in the curated Bass catalog" }
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

    /** Expand each authoritative chord window into bounded, clipped semantic Bass notes. */
    private fun generateNotes(context: MidiCoreGenerationContext): List<MidiCoreCandidateEvent.Note> {
        if (context.sectionPolicy.density == 0.0) return emptyList()
        val authored = context.chordWindows.flatMapIndexed { windowIndex, window ->
            authoredAttacks(context, windowIndex, window)
        }
        val rhythmAware = applyAcceptedRhythmContext(context, authored)
        val selected = selectAttacks(context, rhythmAware)
        var previousPitch: Int? = null
        return selected.mapIndexed { position, attack ->
            val endTick = noteEnd(context, attack)
            val pitch = selectPitch(context, attack, endTick, previousPitch, position)
            previousPitch = pitch
            MidiCoreCandidateEvent.Note(
                startTick = attack.startTick,
                endTick = endTick,
                pitch = pitch,
                velocity = velocity(context, attack.velocityOffset),
            )
        }.sortedWith(
            compareBy<MidiCoreCandidateEvent.Note> { it.startTick }
                .thenBy { it.endTick }
                .thenBy { it.pitch }
                .thenBy { it.velocity },
        )
    }

    /** Build the authored beat slots for one exact harmony window. */
    private fun authoredAttacks(
        context: MidiCoreGenerationContext,
        windowIndex: Int,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): List<BassAttack> {
        val beat = context.tickGrid.ticksPerBeat
        val slots = if (MidiCorePatternCatalog.bassPattern(context.patternId) == MidiCoreBassPatternId.SUSTAINED_ROOT) {
            1
        } else {
            ceil(window.durationTicks.toDouble() / beat).toInt().coerceAtLeast(1)
        }
        return (0 until slots).mapNotNull { slot ->
            val start = window.startTick + slot.toLong() * beat
            if (start >= window.endTick) return@mapNotNull null
            val end = if (slots == 1 && MidiCorePatternCatalog.bassPattern(context.patternId) == MidiCoreBassPatternId.SUSTAINED_ROOT) {
                window.endTick
            } else {
                minOf(window.endTick, start + beat)
            }
            if (end <= start) return@mapNotNull null
            val next = context.chordWindows.getOrNull(windowIndex + 1)
            val pitchClass = pitchClassFor(context, window, next, slot, slots)
            BassAttack(
                windowIndex = windowIndex,
                windowStart = window.startTick,
                windowEnd = window.endTick,
                slot = slot,
                slotCount = slots,
                startTick = start,
                authoredEndTick = end,
                pitchClass = pitchClass,
                pitchTarget = pitchTarget(context, pitchClass, slot),
                velocityOffset = velocityOffset(context, window, slot, slots),
            )
        }
    }

    /** Resolve a pattern tone while keeping every generated pitch inside the current chord. */
    private fun pitchClassFor(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        next: app.melotrail.structure.MidiCoreResolvedChordWindow?,
        slot: Int,
        slotCount: Int,
    ): Int {
        val pattern = MidiCorePatternCatalog.bassPattern(context.patternId)
        val base = window.chord.bassPitchClass
        val root = window.chord.rootPitchClass
        val desired = when (pattern) {
            MidiCoreBassPatternId.SUSTAINED_ROOT -> base
            MidiCoreBassPatternId.ROOT_FIFTH -> if (slot % 2 == 0) base else Math.floorMod(root + 7, 12)
            MidiCoreBassPatternId.OCTAVE -> base
            MidiCoreBassPatternId.WALK_TO_NEXT_ROOT -> walkTarget(
                base,
                next?.chord?.bassPitchClass ?: base,
                slot,
                slotCount,
            )
            MidiCoreBassPatternId.DIATONIC_APPROACH -> if (slot == slotCount - 1) {
                diatonicApproach(context, window, next)
            } else {
                base
            }
        }
        return legalChordTone(window.chord.pitchClasses, desired, base)
    }

    /** Move toward the following bass root but quantize the path to legal current-chord tones. */
    private fun walkTarget(from: Int, to: Int, slot: Int, slotCount: Int): Int {
        if (slotCount <= 1) return to
        val signed = Math.floorMod(to - from + 18, 12) - 6
        return Math.floorMod(from + signed * slot / slotCount, 12)
    }

    /** Choose the current chord tone nearest to a requested approach or walking pitch class. */
    private fun legalChordTone(tones: Set<Int>, desired: Int, preferred: Int): Int = tones
        .sorted()
        .minWith(
            compareBy<Int> { circularDistance(it, desired) }
                .thenBy { if (it == preferred) 0 else 1 }
                .thenBy { it },
        )

    /** Select a diatonic neighbor of the next root when that neighbor is a current chord tone. */
    private fun diatonicApproach(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        next: app.melotrail.structure.MidiCoreResolvedChordWindow?,
    ): Int {
        val target = next?.chord?.bassPitchClass ?: window.chord.bassPitchClass
        if (next == null) return window.chord.bassPitchClass
        val scale = context.authority.key.mode.intervals
            .map { interval -> Math.floorMod(context.authority.key.tonic + interval, 12) }
            .sorted()
        val neighborClasses = if (target in scale) {
            val index = scale.indexOf(target)
            listOf(scale[Math.floorMod(index - 1, scale.size)], scale[(index + 1) % scale.size])
        } else {
            listOf(Math.floorMod(target - 1, 12), Math.floorMod(target + 1, 12))
        }
        val current = window.chord.pitchClasses
        return neighborClasses
            .filter { it in current }
            .minWithOrNull(compareBy<Int> { circularDistance(it, target) }.thenBy { it })
            ?: legalChordTone(current, target, window.chord.bassPitchClass)
    }

    /** Add deterministic accents at occurrence, bar, and harmony phrase boundaries. */
    private fun velocityOffset(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        slot: Int,
        slotCount: Int,
    ): Int {
        val pattern = MidiCorePatternCatalog.bassPattern(context.patternId)
        val patternOffset = when (pattern) {
            MidiCoreBassPatternId.SUSTAINED_ROOT -> 0
            MidiCoreBassPatternId.ROOT_FIFTH -> if (slot % 2 == 0) 2 else -3
            MidiCoreBassPatternId.OCTAVE -> if (slot % 2 == 0) 2 else -1
            MidiCoreBassPatternId.WALK_TO_NEXT_ROOT -> if (slot == slotCount - 1) 3 else 0
            MidiCoreBassPatternId.DIATONIC_APPROACH -> if (slot == slotCount - 1) 4 else 0
        }
        val barBoundary = window.startTick % context.tickGrid.ticksPerBar == 0L
        val phraseBoundary = window.startTick == context.occurrence.startTick || barBoundary
        val purposeOffset = when (context.sectionPolicy.purpose) {
            MidiCoreSectionPurpose.CHORUS -> 2
            MidiCoreSectionPurpose.BRIDGE -> -2
            MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO -> -1
            else -> 0
        }
        return patternOffset + (if (phraseBoundary) 2 else 0) + purposeOffset
    }

    /** Align authored attacks to accepted Chords onsets when that context is available and representable. */
    private fun applyAcceptedRhythmContext(
        context: MidiCoreGenerationContext,
        attacks: List<BassAttack>,
    ): List<BassAttack> {
        if (MidiCorePatternCatalog.bassPattern(context.patternId) == MidiCoreBassPatternId.SUSTAINED_ROOT) return attacks
        val chordNotes = context.dependency(CandidateRole.CHORDS)?.notes.orEmpty()
        val grid = context.tickGrid.ticksPerSubdivision
        val onsets = chordNotes.asSequence()
            .map(MidiCoreGenerationNote::startTick)
            .filter { it >= context.occurrence.startTick && it < context.occurrence.endTick && it % grid == 0L }
            .distinct()
            .sorted()
            .toList()
        if (onsets.isEmpty()) return attacks
        val used = mutableSetOf<Long>()
        return attacks.map { attack ->
            val beat = context.tickGrid.ticksPerBeat
            val onset = onsets
                .asSequence()
                .filter { it !in used && it >= attack.windowStart && it < attack.windowEnd }
                .filter { abs(it - attack.startTick) <= beat / 2 }
                .minWithOrNull(compareBy<Long> { abs(it - attack.startTick) }.thenBy { it })
            if (onset == null) {
                attack
            } else {
                used += onset
                attack.copy(
                    startTick = onset,
                    authoredEndTick = minOf(attack.windowEnd, onset + (attack.authoredEndTick - attack.startTick)),
                )
            }
        }
    }

    /** Select a density- and melody-aware subset while preserving phrase-edge attacks. */
    private fun selectAttacks(context: MidiCoreGenerationContext, attacks: List<BassAttack>): List<BassAttack> {
        if (attacks.isEmpty()) return emptyList()
        val beats = (context.occurrence.endTick - context.occurrence.startTick).toDouble() / context.tickGrid.ticksPerQuarter
        val melodyActivity = context.protectedMelodyNotes.count { it.overlaps(context.occurrence.startTick, context.occurrence.endTick) } / beats
        val activityFactor = when {
            melodyActivity >= 2.0 -> 0.5
            melodyActivity >= 1.0 -> 0.75
            else -> 1.0
        }
        val requested = ceil(attacks.size * context.sectionPolicy.density * activityFactor).toInt().coerceAtLeast(1)
        val validatorBudget = ceil(beats * 2.0 * context.sectionPolicy.density).toInt().coerceAtLeast(1)
        val target = minOf(attacks.size, requested, validatorBudget)
        if (target >= attacks.size) return attacks
        if (target == 1) return listOf(attacks.first())
        val selected = (0 until target).map { index ->
            attacks[(index.toLong() * (attacks.size - 1) / (target - 1)).toInt()]
        }
        return selected.distinctBy { it.startTick }
    }

    /** Choose a bounded register pitch with deterministic melody, low-end, and voice-continuity preferences. */
    private fun selectPitch(
        context: MidiCoreGenerationContext,
        attack: BassAttack,
        endTick: Long,
        previousPitch: Int?,
        position: Int,
    ): Int {
        val range = context.performanceProfile.register
        val candidates = (range.first..range.last).filter { Math.floorMod(it, 12) == attack.pitchClass }
        val anchorSafe = candidates.filter { pitch ->
            context.protectedMelodyNotes.none { melody -> melody.anchor && melody.pitch == pitch && melody.overlaps(attack.startTick, endTick) }
        }
        val lowEndSafe = anchorSafe.filter { pitch ->
            context.dependency(CandidateRole.CHORDS)?.notes.orEmpty().none { chordNote ->
                chordNote.pitch == pitch && chordNote.startTick < endTick && attack.startTick < chordNote.endTick
            }
        }
        val pool = lowEndSafe.ifEmpty { anchorSafe.ifEmpty { candidates } }
        val continuous = previousPitch?.let { previous -> pool.filter { abs(it - previous) <= MAX_LEAP } }.orEmpty()
        val usable = continuous.ifEmpty { pool }
        val ranked = usable.sortedWith(
            compareBy<Int> { abs(it - attack.pitchTarget).toLong() }
                .thenBy { previousPitch?.let { previous -> abs(it - previous) } ?: abs(it - REGISTER_CENTER) }
                .thenBy { it },
        )
        val variationCount = if (MidiCorePatternCatalog.bassPattern(context.patternId) == MidiCoreBassPatternId.OCTAVE) {
            1
        } else {
            minOf(3, ranked.size)
        }
        val variation = Math.floorMod(context.seed + attack.startTick + position.toLong(), variationCount.toLong()).toInt()
        return ranked[variation]
    }

    /** Apply the selected profile's representable duration to one authored attack. */
    private fun noteEnd(context: MidiCoreGenerationContext, attack: BassAttack): Long {
        val duration = attack.authoredEndTick - attack.startTick
        val profile = context.performanceProfile
        val scaled = Math.multiplyExact(duration, profile.noteLengthNumerator.toLong()) / profile.noteLengthDenominator
        val grid = context.tickGrid.ticksPerSubdivision
        val representable = (scaled / grid).coerceAtLeast(1L) * grid
        return minOf(attack.windowEnd, attack.startTick + representable)
    }

    /** Shape velocity from section energy, purpose, phrase, and authored pattern accents. */
    private fun velocity(context: MidiCoreGenerationContext, offset: Int): Int =
        (context.performanceProfile.velocity + ((context.sectionPolicy.energy - 0.5) * ENERGY_VELOCITY_SPAN).roundToInt() + offset)
            .coerceIn(1, 127)

    /** Place the requested pitch class at the closest available position to the low-register center. */
    private fun pitchTarget(context: MidiCoreGenerationContext, pitchClass: Int, slot: Int): Int {
        val candidates = context.performanceProfile.register.filter { Math.floorMod(it, 12) == pitchClass }
        val center = when {
            MidiCorePatternCatalog.bassPattern(context.patternId) != MidiCoreBassPatternId.OCTAVE -> REGISTER_CENTER
            slot % 2 == 1 -> REGISTER_CENTER + 6
            else -> REGISTER_CENTER - 6
        }
        return candidates.minWith(compareBy<Int> { abs(it - center) }.thenBy { it })
    }

    /** Return the shortest pitch-class distance on the chromatic circle. */
    private fun circularDistance(first: Int, second: Int): Int {
        val distance = abs(Math.floorMod(first, 12) - Math.floorMod(second, 12))
        return minOf(distance, 12 - distance)
    }

    private data class BassAttack(
        val windowIndex: Int,
        val windowStart: Long,
        val windowEnd: Long,
        val slot: Int,
        val slotCount: Int,
        val startTick: Long,
        val authoredEndTick: Long,
        val pitchClass: Int,
        val pitchTarget: Int,
        val velocityOffset: Int,
    )

    private const val REGISTER_CENTER = 42
    private const val MAX_LEAP = 12
    private const val ENERGY_VELOCITY_SPAN = 16.0
    private const val SEED_STEP = 7_919L
}
