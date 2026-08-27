package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure Chords-generation result containing semantic notes and its validation evidence. */
data class MidiCoreChordGenerationResult(
    val context: MidiCoreGenerationContext,
    val candidate: MidiCoreRoleCandidate,
    val validation: MidiCoreRoleValidationResult,
) {
    init {
        require(context.role == CandidateRole.CHORDS) { "Chord generation context must select the Chords role" }
        require(candidate.role == CandidateRole.CHORDS && candidate.occurrenceId == context.occurrence.id) {
            "Chord candidate must remain scoped to the generation context"
        }
        require(validation.report.contextSha256 == context.contextSha256 && validation.report.role == CandidateRole.CHORDS) {
            "Chord validation evidence must bind the generation context"
        }
    }

    /** True when this candidate passed every blocking target-role policy. */
    val accepted: Boolean get() = validation is MidiCoreRoleValidationResult.Accepted
}

/** Deterministic Chords/keys generator over the shared MIDI Core context. */
object MidiCoreChordGenerator {
    /** Zero-based MIDI channel for the musician-facing Chords channel 2. */
    const val MIDI_CHANNEL = 1

    /** Generate one semantic Chords candidate and validate it before publication. */
    fun generate(context: MidiCoreGenerationContext): MidiCoreChordGenerationResult {
        require(context.role == CandidateRole.CHORDS) { "Chord generation requires a Chords context" }
        val candidate = MidiCoreRoleCandidate(
            role = CandidateRole.CHORDS,
            occurrenceId = context.occurrence.id,
            channel = MIDI_CHANNEL,
            events = generateNotes(context),
        )
        return MidiCoreChordGenerationResult(context, candidate, MidiCoreRoleValidator.validate(context, candidate))
    }

    /** Generate a deterministic family of distinct curated-rhythm alternatives for one occurrence. */
    fun generateAlternatives(
        context: MidiCoreGenerationContext,
        count: Int = 2,
    ): List<MidiCoreChordGenerationResult> {
        require(context.role == CandidateRole.CHORDS) { "Chord alternatives require a Chords context" }
        require(count in 1..MidiCorePatternCatalog.chordRhythms.size) {
            "Chord alternative count must be between 1 and ${MidiCorePatternCatalog.chordRhythms.size}"
        }
        val patterns = MidiCorePatternCatalog.chordRhythms
        val first = patterns.indexOfFirst { it.id.id == context.patternId }
        require(first >= 0) { "Chord context pattern is not in the curated Chords catalog" }
        return (0 until count).map { index ->
            val pattern = patterns[(first + index) % patterns.size]
            val alternativeContext = context.copy(
                patternId = pattern.id.id,
                generator = context.generator.copy(
                    patternId = pattern.id.id,
                    seed = if (index == 0) context.seed else context.seed + index.toLong() * SEED_STEP,
                ),
            )
            generate(alternativeContext)
        }
    }

    /** Expand every authoritative chord window into complete, clipped semantic note events. */
    private fun generateNotes(context: MidiCoreGenerationContext): List<MidiCoreCandidateEvent.Note> {
        if (context.sectionPolicy.density == 0.0) return emptyList()
        val notes = mutableListOf<MidiCoreCandidateEvent.Note>()
        var previousVoicing: List<Int>? = null
        context.chordWindows.forEachIndexed { windowIndex, window ->
            val rhythm = rhythmWindows(context, window)
            val voicing = selectVoicing(context, window, rhythm, previousVoicing, windowIndex)
            if (voicing != null) {
                rhythm.forEach { attack ->
                    val endTick = noteEnd(context, attack)
                    voicing.forEach { pitch ->
                        notes += MidiCoreCandidateEvent.Note(
                            startTick = attack.startTick,
                            endTick = endTick,
                            pitch = pitch,
                            velocity = velocity(context, attack.velocityOffset),
                        )
                    }
                }
                previousVoicing = voicing
            }
        }
        return notes.sortedWith(
            compareBy<MidiCoreCandidateEvent.Note> { it.startTick }
                .thenBy { it.endTick }
                .thenBy { it.pitch }
                .thenBy { it.velocity },
        )
    }

    /** Repeat the selected authored rhythm from each window start without crossing harmony boundaries. */
    private fun rhythmWindows(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): List<RhythmWindow> {
        val pattern = MidiCorePatternCatalog.chordRhythm(context.patternId)
        if (pattern.id == MidiCoreChordRhythmPatternId.SUSTAINED) {
            return listOf(RhythmWindow(window.startTick, window.endTick, 0))
        }
        val stepTicks = context.tickGrid.ticksPerSubdivision
        val barTicks = context.tickGrid.ticksPerBar
        return buildList {
            var barStart = window.startTick
            while (barStart < window.endTick) {
                pattern.steps.forEach { step ->
                    val start = barStart + step.sixteenth.toLong() * stepTicks
                    val end = minOf(window.endTick, start + step.durationSixteenths.toLong() * stepTicks)
                    if (start >= window.startTick && start < window.endTick && end > start) {
                        add(RhythmWindow(start, end, step.velocityOffset))
                    }
                }
                barStart += barTicks
            }
        }.ifEmpty { listOf(RhythmWindow(window.startTick, window.endTick, 0)) }
    }

    /** Select a bounded inversion using voice continuity, melody/bass space, and an explicit seed tie-break. */
    private fun selectVoicing(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        rhythm: List<RhythmWindow>,
        previous: List<Int>?,
        windowIndex: Int,
    ): List<Int>? {
        val all = voicingCandidates(context, window)
        if (all.isEmpty()) return null
        val spaceSafe = all.filter { voicing ->
            !hasAnchorCollision(context, voicing, rhythm) && !hasBassCollision(context, voicing, rhythm)
        }
        val pool = spaceSafe.ifEmpty { all }
        val ranked = pool.sortedWith(
            compareBy<List<Int>> { voiceLeadingScore(context, it, previous) }
                .thenBy { it.joinToString(",") },
        )
        val variationCount = minOf(3, ranked.size)
        val variation = Math.floorMod(context.seed + windowIndex.toLong(), variationCount.toLong()).toInt()
        return ranked[variation]
    }

    /** Enumerate all complete chord-tone inversions that fit the selected performance register. */
    private fun voicingCandidates(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): List<List<Int>> {
        val range = context.performanceProfile.register
        val classes = window.chord.quality.intervals
            .map { interval -> Math.floorMod(window.chord.rootPitchClass + interval, 12) }
            .distinct()
        if (classes.isEmpty()) return emptyList()
        val rotations = classes.indices.map { offset -> classes.drop(offset) + classes.take(offset) }
        val ordered = if (window.chord.bass == null) rotations else rotations.filter { it.first() == window.chord.bassPitchClass }
        return ordered.flatMap { order ->
            (range.first..range.last).mapNotNull { first ->
                if (first % 12 != order.first()) return@mapNotNull null
                val voices = mutableListOf(first)
                order.drop(1).forEach { pitchClass ->
                    voices += nextAtOrAbove(voices.last() + 1, pitchClass)
                }
                voices.takeIf { candidate ->
                    candidate.last() <= range.last && candidate.zipWithNext().all { (low, high) -> high - low in 1..MAX_VOICE_SPACING }
                }
            }
        }
    }

    /** Score a voicing against the previous window and the center of the approved register. */
    private fun voiceLeadingScore(
        context: MidiCoreGenerationContext,
        voicing: List<Int>,
        previous: List<Int>?,
    ): Long {
        val center = (context.performanceProfile.register.first + context.performanceProfile.register.last) / 2
        if (previous == null) return voicing.sumOf { pitch -> abs(pitch - center).toLong() }
        val common = voicing.zip(previous).sumOf { (current, prior) -> abs(current - prior).toLong() }
        return common + abs(voicing.size - previous.size).toLong() * VOICE_COUNT_PENALTY
    }

    /** Reject a voicing only when a generated attack would overlap an exact protected melody anchor. */
    private fun hasAnchorCollision(
        context: MidiCoreGenerationContext,
        voicing: List<Int>,
        rhythm: List<RhythmWindow>,
    ): Boolean = rhythm.any { attack ->
        context.protectedMelodyNotes.any { melody ->
            melody.anchor && voicing.contains(melody.pitch) && melody.overlaps(attack.startTick, attack.endTick)
        }
    }

    /** Prefer chord voicings that leave a bounded five-semitone buffer above accepted bass notes. */
    private fun hasBassCollision(
        context: MidiCoreGenerationContext,
        voicing: List<Int>,
        rhythm: List<RhythmWindow>,
    ): Boolean = context.dependency(CandidateRole.BASS)?.notes.orEmpty().any { bass ->
        rhythm.any { attack ->
            bass.startTick < attack.endTick && attack.startTick < bass.endTick &&
                voicing.any { pitch -> abs(pitch - bass.pitch) <= BASS_SPACE_SEMITONES }
        }
    }

    /** Apply the profile's MIDI-only note-length intent to an authored rhythm window. */
    private fun noteEnd(context: MidiCoreGenerationContext, attack: RhythmWindow): Long {
        val duration = attack.endTick - attack.startTick
        val profile = context.performanceProfile
        val scaled = Math.multiplyExact(duration, profile.noteLengthNumerator.toLong()) / profile.noteLengthDenominator
        val grid = context.tickGrid.ticksPerSubdivision
        val representableDuration = (scaled / grid).coerceAtLeast(1L) * grid
        return minOf(attack.endTick, attack.startTick + representableDuration)
    }

    /** Shape velocity from section energy and authored accent without leaving MIDI bounds. */
    private fun velocity(context: MidiCoreGenerationContext, offset: Int): Int =
        (context.performanceProfile.velocity + ((context.sectionPolicy.energy - 0.5) * ENERGY_VELOCITY_SPAN).roundToInt() + offset)
            .coerceIn(1, 127)

    /** Place one chord pitch class at or above a previous voice without leaving an accidental duplicate. */
    private fun nextAtOrAbove(minimum: Int, pitchClass: Int): Int = minimum + Math.floorMod(pitchClass - minimum, 12)

    private data class RhythmWindow(val startTick: Long, val endTick: Long, val velocityOffset: Int)

    private const val MAX_VOICE_SPACING = 12
    private const val VOICE_COUNT_PENALTY = 24L
    private const val BASS_SPACE_SEMITONES = 5
    private const val ENERGY_VELOCITY_SPAN = 16.0
    private const val SEED_STEP = 7_919L
}
