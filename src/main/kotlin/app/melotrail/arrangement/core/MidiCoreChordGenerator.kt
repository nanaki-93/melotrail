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
            if (rhythm.isEmpty()) return emptyList()
            val voicing = selectVoicing(context, window, rhythm, previousVoicing, windowIndex)
            if (voicing == null) return emptyList()
            rhythm.forEach { attack ->
                val endTick = noteEnd(context, attack)
                voicing.forEach { pitch ->
                    notes += MidiCoreCandidateEvent.Note(
                        startTick = attack.startTick,
                        endTick = endTick,
                        pitch = pitch,
                        velocity = velocity(context, attack.velocityOffset + if (attack.phraseBoundary) PHRASE_ACCENT else 0),
                    )
                }
            }
            previousVoicing = voicing
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
            return listOf(RhythmWindow(window.startTick, window.endTick, 0, phraseBoundary = true))
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
                        add(RhythmWindow(start, end, step.velocityOffset, phraseBoundary = step.sixteenth == 0))
                    }
                }
                barStart += barTicks
            }
        }
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
        if (spaceSafe.isEmpty()) return null
        val movementSafe = previous?.let { prior ->
            spaceSafe.filter { voicing -> voiceMovement(prior, voicing).maximumDistance <= MAX_VOICE_MOVEMENT }
        }.orEmpty()
        val pool = movementSafe.ifEmpty { spaceSafe }
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

    /** Score a voicing against its bounded voice movement, retained common tones, and section-aware register target. */
    private fun voiceLeadingScore(
        context: MidiCoreGenerationContext,
        voicing: List<Int>,
        previous: List<Int>?,
    ): Long {
        val center = preferredRegisterCenter(context)
        val registerDistance = voicing.sumOf { pitch -> abs(pitch - center).toLong() }
        if (previous == null) return registerDistance
        val movement = voiceMovement(previous, voicing)
        return movement.totalDistance + movement.unmatchedVoices.toLong() * VOICE_COUNT_PENALTY -
            movement.commonPitches.toLong() * COMMON_TONE_BONUS + registerDistance
    }

    /** Place section energy and purpose inside, rather than outside, the selected MIDI register. */
    private fun preferredRegisterCenter(context: MidiCoreGenerationContext): Int {
        val range = context.performanceProfile.register
        val base = (range.first + range.last) / 2
        val purposeOffset = when (context.sectionPolicy.purpose) {
            MidiCoreSectionPurpose.CHORUS -> 5
            MidiCoreSectionPurpose.PRE_CHORUS -> 3
            MidiCoreSectionPurpose.BRIDGE -> 2
            MidiCoreSectionPurpose.INTRO, MidiCoreSectionPurpose.OUTRO -> -4
            MidiCoreSectionPurpose.VERSE, MidiCoreSectionPurpose.UNSPECIFIED -> 0
        }
        val energyOffset = ((context.sectionPolicy.energy - 0.5) * ENERGY_REGISTER_SPAN).roundToInt()
        return (base + purposeOffset + energyOffset).coerceIn(range.first, range.last)
    }

    /** Align two ordered voicings with a bounded dynamic-programming movement metric. */
    private fun voiceMovement(previous: List<Int>, current: List<Int>): VoiceMovement {
        val memo = mutableMapOf<Pair<Int, Int>, VoiceMovement>()
        /** Resolve the lowest-cost suffix while retaining ordering and exact common tones. */
        fun align(previousIndex: Int, currentIndex: Int): VoiceMovement = memo.getOrPut(previousIndex to currentIndex) {
            when {
                previousIndex == previous.size && currentIndex == current.size -> VoiceMovement()
                previousIndex == previous.size -> VoiceMovement(unmatchedVoices = current.size - currentIndex)
                currentIndex == current.size -> VoiceMovement(unmatchedVoices = previous.size - previousIndex)
                else -> {
                    val distance = abs(previous[previousIndex] - current[currentIndex])
                    val matched = align(previousIndex + 1, currentIndex + 1).withMatch(distance, previous[previousIndex] == current[currentIndex])
                    val skippedPrevious = align(previousIndex + 1, currentIndex).withUnmatchedVoice()
                    val skippedCurrent = align(previousIndex, currentIndex + 1).withUnmatchedVoice()
                    listOf(matched, skippedPrevious, skippedCurrent).minWith(VOICE_MOVEMENT_ORDER)
                }
            }
        }
        return align(0, 0)
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

    private data class RhythmWindow(
        val startTick: Long,
        val endTick: Long,
        val velocityOffset: Int,
        val phraseBoundary: Boolean,
    )

    private data class VoiceMovement(
        val totalDistance: Long = 0,
        val maximumDistance: Int = 0,
        val commonPitches: Int = 0,
        val unmatchedVoices: Int = 0,
    ) {
        /** Extend the metric with one matched, order-preserving pair of voices. */
        fun withMatch(distance: Int, commonPitch: Boolean): VoiceMovement = copy(
            totalDistance = totalDistance + distance,
            maximumDistance = maxOf(maximumDistance, distance),
            commonPitches = commonPitches + if (commonPitch) 1 else 0,
        )

        /** Extend the metric when an extension or omitted chord tone has no matching voice. */
        fun withUnmatchedVoice(): VoiceMovement = copy(unmatchedVoices = unmatchedVoices + 1)
    }

    private const val MAX_VOICE_SPACING = 12
    private const val MAX_VOICE_MOVEMENT = 12
    private const val VOICE_COUNT_PENALTY = 24L
    private const val COMMON_TONE_BONUS = 10L
    private const val BASS_SPACE_SEMITONES = 5
    private const val ENERGY_VELOCITY_SPAN = 16.0
    private const val ENERGY_REGISTER_SPAN = 10.0
    private const val PHRASE_ACCENT = 3
    private const val SEED_STEP = 7_919L

    private val VOICE_MOVEMENT_ORDER = compareBy<VoiceMovement> {
        it.totalDistance + it.unmatchedVoices.toLong() * VOICE_COUNT_PENALTY
    }.thenBy { it.maximumDistance }
        .thenByDescending { it.commonPitches }
        .thenBy { it.unmatchedVoices }
}
