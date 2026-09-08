package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure Chords-generation result containing semantic notes and its validation evidence. */
data class MidiCoreChordGenerationResult(
    val context: MidiCoreGenerationContext,
    val candidate: MidiCoreRoleCandidate,
    val validation: MidiCoreRoleValidationResult,
    /** Explicit output for a following Chords occurrence; never read from mutable accepted state. */
    val outgoingPianoVoicingBoundary: MidiCorePianoVoicingBoundarySummary? = null,
) {
    init {
        require(context.role == CandidateRole.CHORDS) { "Chord generation context must select the Chords role" }
        require(candidate.role == CandidateRole.CHORDS && candidate.occurrenceId == context.occurrence.id) {
            "Chord candidate must remain scoped to the generation context"
        }
        require(validation.report.contextSha256 == context.contextSha256 && validation.report.role == CandidateRole.CHORDS) {
            "Chord validation evidence must bind the generation context"
        }
        require(outgoingPianoVoicingBoundary == null || (
            validation is MidiCoreRoleValidationResult.Accepted &&
                outgoingPianoVoicingBoundary.sourceOccurrenceId == context.occurrence.id &&
                outgoingPianoVoicingBoundary.boundaryTick == context.occurrence.endTick &&
                outgoingPianoVoicingBoundary.authorityHash == context.authorityHash
            )) { "Chord boundary output must describe this accepted occurrence" }
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
        val plan = pianoVoicingPlan(context)
        val candidate = MidiCoreRoleCandidate(
            role = CandidateRole.CHORDS,
            occurrenceId = context.occurrence.id,
            channel = MIDI_CHANNEL,
            events = notesForPlan(context, plan),
        )
        val validation = MidiCoreRoleValidator.validate(context, candidate)
        val boundary = if (validation is MidiCoreRoleValidationResult.Accepted) {
            plan?.voicings?.lastOrNull { it.isNotEmpty() }?.let { pitches ->
                MidiCorePianoVoicingBoundarySummary(
                    context.occurrence.id,
                    context.occurrence.endTick,
                    pitches,
                    context.authorityHash,
                )
            }
        } else {
            null
        }
        return MidiCoreChordGenerationResult(context, candidate, validation, boundary)
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

    /**
     * Search all chord windows as one bounded phrase. Each level retains at most
     * [MAX_PIANO_VOICING_BEAM_WIDTH] stable paths; an empty safe pool is the
     * deterministic generation failure, while an over-large movement falls back
     * to the otherwise safe pool rather than silently rewriting the harmony.
     */
    internal fun pianoVoicingPlan(context: MidiCoreGenerationContext): MidiCorePianoVoicingPlan? {
        if (context.sectionPolicy.density == 0.0) return MidiCorePianoVoicingPlan(emptyList(), emptyList(), emptyList())
        val expansions = context.chordWindows.map { window -> rhythmWindows(context, window) }
        if (expansions.any { it.attacks.isEmpty() && !it.intentionalRest }) return null
        val rhythms = expansions.map { it.attacks }
        if (context.chordWindows.size == 1) {
            return singleWindowPlan(context, context.chordWindows.single(), rhythms.single())
        }
        var beam = listOf(VoicingPath(0L, context.pianoVoicingBoundary?.pitches, emptyList()))
        val widths = mutableListOf<Int>()
        context.chordWindows.forEachIndexed { windowIndex, window ->
            if (expansions[windowIndex].intentionalRest) {
                beam = beam.map { it.copy(voicings = it.voicings + listOf(emptyList())) }
                widths += beam.size
                return@forEachIndexed
            }
            val safe = legalVoicingCandidates(context, window).filter { candidate ->
                !hasAnchorCollision(context, candidate.pitches, rhythms[windowIndex]) &&
                    !hasBassCollision(context, candidate.pitches, rhythms[windowIndex])
            }
            if (safe.isEmpty()) return null
            val next = beam.flatMap { path ->
                val movementSafe = path.lastVoicing?.let { previous ->
                    safe.filter { candidate -> voiceMovement(previous, candidate.pitches).maximumDistance <= MAX_VOICE_MOVEMENT }
                }.orEmpty()
                val pool = movementSafe.ifEmpty { safe }
                pool.map { candidate ->
                    path.advance(candidate, selectionScore(context, window, rhythms[windowIndex], candidate, path.lastVoicing))
                }
            }.sortedWith(VOICING_PATH_ORDER)
                .take(MAX_PIANO_VOICING_BEAM_WIDTH)
            if (next.isEmpty()) return null
            widths += next.size
            beam = next
        }
        val best = beam.firstOrNull() ?: return null
        val variationPool = beam.takeWhile { path -> path.score <= best.score + MAX_SEED_VARIATION_COST }
        val variationCount = minOf(MAX_SEED_VARIATIONS, variationPool.size)
        val selected = variationPool[Math.floorMod(context.seed + context.chordWindows.size.toLong(), variationCount.toLong()).toInt()]
        return MidiCorePianoVoicingPlan(selected.voicings, rhythms, widths)
    }

    /** Preserve the established one-window ranker exactly; lookahead changes only actual phrases. */
    private fun singleWindowPlan(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        rhythm: List<RhythmWindow>,
    ): MidiCorePianoVoicingPlan? {
        val safe = legalVoicingCandidates(context, window).filter { candidate ->
            !hasAnchorCollision(context, candidate.pitches, rhythm) && !hasBassCollision(context, candidate.pitches, rhythm)
        }
        if (safe.isEmpty()) return null
        val previous = context.pianoVoicingBoundary?.pitches
        val movementSafe = previous?.let { prior ->
            safe.filter { candidate -> voiceMovement(prior, candidate.pitches).maximumDistance <= MAX_VOICE_MOVEMENT }
        }.orEmpty()
        val pool = movementSafe.ifEmpty { safe }
        val ranked = pool.sortedWith(
            compareBy<MidiCorePianoVoicingCandidate> { candidate -> selectionScore(context, window, rhythm, candidate, previous) }
                .thenBy { it.kind.ordinal }
                .thenBy { it.pitches.joinToString(",") },
        )
        val variationPool = if (context.melodyHarmonyAnalysis == null) {
            ranked
        } else {
            val bestScore = selectionScore(context, window, rhythm, ranked.first(), previous)
            ranked.takeWhile { candidate -> selectionScore(context, window, rhythm, candidate, previous) <= bestScore + MAX_SEED_VARIATION_COST }
        }
        val variationCount = minOf(MAX_SEED_VARIATIONS, variationPool.size)
        val selected = variationPool[Math.floorMod(context.seed, variationCount.toLong()).toInt()]
        return MidiCorePianoVoicingPlan(listOf(selected.pitches), listOf(rhythm), listOf(1))
    }

    /** Expand a bounded phrase plan into complete, clipped semantic note events. */
    private fun notesForPlan(
        context: MidiCoreGenerationContext,
        plan: MidiCorePianoVoicingPlan?,
    ): List<MidiCoreCandidateEvent.Note> {
        if (plan == null || plan.voicings.isEmpty()) return emptyList()
        val notes = mutableListOf<MidiCoreCandidateEvent.Note>()
        plan.voicings.forEachIndexed { windowIndex, voicing ->
            plan.rhythms[windowIndex].forEach { attack ->
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
        }
        return notes.sortedWith(
            compareBy<MidiCoreCandidateEvent.Note> { it.startTick }
                .thenBy { it.endTick }
                .thenBy { it.pitch }
                .thenBy { it.velocity },
        )
    }

    /**
     * Expand the selected meter realization on the song grid, never restarting it at a
     * harmonic change. Pulsed attacks outside a chord window are rests. Sustained support
     * intersects the bar span with each harmony window, changing voicing at an offbeat
     * harmony boundary while retaining the next bar attack. Both ends are clipped.
     */
    private fun rhythmWindows(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): RhythmExpansion {
        val pattern = MidiCorePatternCatalog.chordRhythm(context.patternId)
        val stepTicks = context.tickGrid.ticksPerSubdivision
        val barTicks = context.tickGrid.ticksPerBar
        val pickupTicks = context.authority.pickupTicks
        val steps = pattern.stepsFor(context.authority.meter)
        var intentionalRest = true
        val attacks = buildList {
            var barStart = metricalBarStart(window.startTick, pickupTicks, barTicks)
            while (barStart < window.endTick) {
                val barEnd = if (barStart < pickupTicks) pickupTicks else barStart + barTicks
                val use = compingUse(
                    context,
                    pattern,
                    maxOf(context.occurrence.startTick, barStart),
                    minOf(context.occurrence.endTick, barEnd),
                )
                intentionalRest = intentionalRest && use == MidiCoreChordCompingUse.REST
                MidiCoreChordCompingPhrasePatterns.stepsFor(context.authority.meter, use, steps).forEach { step ->
                    val metricalStart = barStart + step.sixteenth.toLong() * stepTicks
                    val start = if (
                        use == MidiCoreChordCompingUse.SUPPORT || pattern.id == MidiCoreChordRhythmPatternId.SUSTAINED
                    ) {
                        maxOf(window.startTick, metricalStart)
                    } else metricalStart
                    val end = minOf(window.endTick, barEnd, metricalStart + step.durationSixteenths.toLong() * stepTicks)
                    if (start >= window.startTick && start < minOf(window.endTick, barEnd) && end > start &&
                        (use == MidiCoreChordCompingUse.SUPPORT || !isPhraseTerminalRest(context, start)) &&
                        (use != MidiCoreChordCompingUse.ANSWER || melodyOccupiedTicks(context, start, end) == 0L)
                    ) {
                        add(
                            RhythmWindow(
                                start,
                                end,
                                step.velocityOffset,
                                phraseBoundary = start == barStart,
                                support = use == MidiCoreChordCompingUse.SUPPORT,
                            ),
                        )
                    }
                }
                barStart = barEnd
            }
        }
        return RhythmExpansion(attacks, intentionalRest)
    }

    private data class RhythmExpansion(val attacks: List<RhythmWindow>, val intentionalRest: Boolean)

    /** Resolve one complete comping use per metrical bar fragment from immutable melody activity. */
    private fun compingUse(
        context: MidiCoreGenerationContext,
        pattern: MidiCoreChordRhythmPattern,
        startTick: Long,
        endTick: Long,
    ): MidiCoreChordCompingUse = when {
        startTick >= endTick -> MidiCoreChordCompingUse.REST
        isInterPhraseBreath(context, startTick, endTick) -> MidiCoreChordCompingUse.REST
        pattern.id == MidiCoreChordRhythmPatternId.SUSTAINED && melodyOccupiedTicks(context, startTick, endTick) > 0L ->
            MidiCoreChordCompingUse.SUPPORT
        isDenseMelody(context, startTick, endTick) -> MidiCoreChordCompingUse.SUPPORT
        else -> MidiCoreChordCompingUse.ANSWER
    }

    /** Leave the first empty bar after a phrase as breathing room when another phrase follows. */
    private fun isInterPhraseBreath(context: MidiCoreGenerationContext, startTick: Long, endTick: Long): Boolean {
        if (melodyOccupiedTicks(context, startTick, endTick) != 0L) return false
        val phraseJustEnded = phraseEndTicks(context).any { end ->
            end <= startTick && startTick - end < context.tickGrid.ticksPerBar
        }
        val nextPhrase = context.melodyHarmonyAnalysis?.notes?.any { it.startTick >= endTick }
            ?: context.protectedMelodyNotes.any { it.startTick >= endTick }
        return phraseJustEnded && nextPhrase
    }

    /** A phrase ending owns its final beat; a new pulsed answer there would erase the melodic breath. */
    private fun isPhraseTerminalRest(context: MidiCoreGenerationContext, attackStartTick: Long): Boolean {
        val beat = phraseRestTicks(context)
        return phraseEndTicks(context).any { phraseEnd ->
            attackStartTick >= phraseEnd - beat && attackStartTick < phraseEnd
        }
    }

    /** In 6/8 the audible pulse is a dotted quarter, not one isolated eighth-note denominator unit. */
    private fun phraseRestTicks(context: MidiCoreGenerationContext): Long =
        if (context.authority.meter.numerator == 6 && context.authority.meter.denominator == 8L) {
            Math.multiplyExact(context.tickGrid.ticksPerBeat, 3L)
        } else {
            context.tickGrid.ticksPerBeat
        }

    /** Prefer M02's sustain-aware phrase hints; direct contexts derive the same one-beat rest rule. */
    private fun phraseEndTicks(context: MidiCoreGenerationContext): List<Long> {
        val analyzed = context.melodyHarmonyAnalysis?.phrases.orEmpty().map(MidiCoreMelodyPhraseHint::endTick)
        if (analyzed.isNotEmpty()) return analyzed
        val notes = context.protectedMelodyNotes.sortedBy(MidiCoreProtectedMelodyNote::startTick)
        if (notes.isEmpty()) return emptyList()
        val ends = mutableListOf<Long>()
        var phraseEnd = notes.first().endTick
        notes.drop(1).forEach { note ->
            if (note.startTick - phraseEnd >= context.tickGrid.ticksPerBeat) ends += phraseEnd
            phraseEnd = maxOf(phraseEnd, note.endTick)
        }
        ends += phraseEnd
        return ends
    }

    /** Treat coverage of two thirds of a bar fragment as busy, avoiding pulse-for-pulse crowding. */
    private fun isDenseMelody(context: MidiCoreGenerationContext, startTick: Long, endTick: Long): Boolean {
        val span = endTick - startTick
        return span > 0L && melodyOccupiedTicks(context, startTick, endTick) * DENSE_MELODY_DENOMINATOR >=
            span * DENSE_MELODY_NUMERATOR
    }

    /** Measure union coverage so polyphony cannot inflate activity; M02 sounding ends include supported CC64. */
    private fun melodyOccupiedTicks(context: MidiCoreGenerationContext, startTick: Long, endTick: Long): Long {
        val soundingSpans = context.melodyHarmonyAnalysis?.notes?.map { note ->
            note.startTick to note.soundingEndTick
        } ?: context.protectedMelodyNotes.map { note ->
            note.startTick to note.endTick
        }
        val overlaps = soundingSpans.mapNotNull { (noteStart, noteEnd) ->
            val overlapStart = maxOf(startTick, noteStart)
            val overlapEnd = minOf(endTick, noteEnd)
            (overlapStart to overlapEnd).takeIf { (start, end) -> end > start }
        }.sortedBy { it.first }
        var occupied = 0L
        var coveredUntil = Long.MIN_VALUE
        overlaps.forEach { (overlapStart, overlapEnd) ->
            val uncoveredStart = maxOf(overlapStart, coveredUntil)
            if (overlapEnd > uncoveredStart) occupied += overlapEnd - uncoveredStart
            coveredUntil = maxOf(coveredUntil, overlapEnd)
        }
        return occupied
    }

    /** The pickup is a partial leading bar; regular meter bars begin at its exact endpoint. */
    private fun metricalBarStart(tick: Long, pickupTicks: Long, barTicks: Long): Long = when {
        tick < pickupTicks -> 0L
        else -> pickupTicks + Math.floorDiv(tick - pickupTicks, barTicks) * barTicks
    }

    private fun selectionScore(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        rhythm: List<RhythmWindow>,
        candidate: MidiCorePianoVoicingCandidate,
        previous: List<Int>?,
    ): Long = melodyEvidenceScore(context, window, rhythm, candidate.pitches) +
        voiceLeadingScore(context, candidate.pitches, previous) + candidate.kind.selectionPenalty

    /**
     * Rank only from M02's immutable observations. A close note costs more when M02 identifies
     * it as tension, then only according to its overlap and metrical accent. Passing tension
     * remains legal and deliberately receives a much smaller multiplier than sustained tension;
     * ordinary consonant melody overlap is not recast as a clash. Low melody register pressure
     * remains independent of chord-tone classification.
     */
    private fun melodyEvidenceScore(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        rhythm: List<RhythmWindow>,
        voicing: List<Int>,
    ): Long {
        val evidence = context.melodyHarmonyAnalysis?.windows?.singleOrNull { it.chordEventId == window.event.id } ?: return 0L
        val findingByNoteId = evidence.findings.groupBy(MidiCoreHarmonyTensionFinding::noteId)
        val lowRegisterCeiling = evidence.activity.register
            ?.takeIf { it.lowestPitch <= LOW_MELODY_REGISTER_THRESHOLD }
            ?.lowestPitch
            ?.plus(LOW_MELODY_REGISTER_CLEARANCE)

        return rhythm.sumOf { attack ->
            val generatedEnd = noteEnd(context, attack)
            voicing.sumOf { chordPitch ->
                evidence.activity.soundingNotes.sumOf { melody ->
                    val overlap = minOf(generatedEnd, melody.soundingEndTick) - maxOf(attack.startTick, melody.startTick)
                    if (overlap <= 0L) {
                        0L
                    } else {
                        val durationUnits = (overlap / context.tickGrid.ticksPerSubdivision).coerceAtLeast(1L)
                        val tensionFindings = findingByNoteId[melody.id].orEmpty()
                        val closeIntervalCost = if (tensionFindings.any { it.cause != MidiCoreHarmonyTensionCause.PITCH_BEND_RANGE_UNKNOWN }) {
                            closeIntervalCost(abs(chordPitch - melody.pitch))
                        } else {
                            0L
                        }
                        val tensionMultiplier = tensionMultiplier(tensionFindings)
                        val prominenceMultiplier = findingProminenceMultiplier(tensionFindings)
                        val registerCost = if (lowRegisterCeiling != null && chordPitch <= lowRegisterCeiling) {
                            LOW_MELODY_REGISTER_PENALTY
                        } else {
                            0L
                        }
                        durationUnits * (closeIntervalCost * tensionMultiplier * prominenceMultiplier + registerCost)
                    }
                }
            }
        }
    }

    private fun closeIntervalCost(interval: Int): Long = when (interval) {
        0 -> 40L
        1 -> 32L
        2 -> 24L
        3, 4, 5 -> 12L
        else -> 0L
    }

    private fun tensionMultiplier(findings: List<MidiCoreHarmonyTensionFinding>): Long = when {
        findings.any { it.cause == MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION } -> SUSTAINED_TENSION_MULTIPLIER
        findings.any { it.cause == MidiCoreHarmonyTensionCause.HELD_SUSPENSION } -> HELD_TENSION_MULTIPLIER
        findings.any { it.cause == MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE } -> PASSING_TENSION_MULTIPLIER
        else -> 1L
    }

    /** M02 reports the strongest metrical point inside each overlap, including notes held across a chord boundary. */
    internal fun findingProminenceMultiplier(findings: List<MidiCoreHarmonyTensionFinding>): Long {
        val prominence = findings
            .filter { it.cause != MidiCoreHarmonyTensionCause.PITCH_BEND_RANGE_UNKNOWN }
            .maxOfOrNull(MidiCoreHarmonyTensionFinding::beatProminence)
            ?: return UNACCENTED_PROMINENCE_MULTIPLIER
        return when {
            prominence >= DOWNBEAT_PROMINENCE -> DOWNBEAT_PROMINENCE_MULTIPLIER
            prominence >= GROUP_ACCENT_PROMINENCE -> GROUP_ACCENT_PROMINENCE_MULTIPLIER
            else -> UNACCENTED_PROMINENCE_MULTIPLIER
        }
    }

    /**
     * Enumerate a small, stable pool before any melody-aware ranking. Complete inversions,
     * open shapes, guide tones, and other reduced spellings all retain authoritative chord
     * identity; reduced choices are therefore available before collision fallback.
     */
    internal fun legalVoicingCandidates(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): List<MidiCorePianoVoicingCandidate> {
        val closed = completeVoicingCandidates(context, window)
        val guideClasses = guideTonePitchClasses(window)
        val rootMayBeOmitted = bassSuppliesRoot(context, window)
        val requiredClasses = requiredPitchClasses(window, guideClasses, rootMayBeOmitted)
        val guideCandidateClasses = if (rootMayBeOmitted) requiredClasses else guideClasses
        val candidatesByKind = linkedMapOf(
            MidiCorePianoVoicingKind.CLOSED to closed,
            MidiCorePianoVoicingKind.OPEN to closed.flatMap(::openVoicings),
            MidiCorePianoVoicingKind.GUIDE_TONE to closed.flatMap { voicing ->
                subsets(voicing).filter { candidate ->
                    candidate.size >= 2 && candidate.pitchClasses() == guideCandidateClasses && candidate.containsPitchClasses(requiredClasses)
                }
            },
            MidiCorePianoVoicingKind.REDUCED to closed.flatMap { voicing ->
                subsets(voicing).filter { candidate ->
                    candidate.size in 2 until voicing.size && candidate.pitchClasses() != guideClasses &&
                        candidate.containsPitchClasses(requiredClasses)
                }
            },
        )
        return candidatesByKind.flatMap { (kind, pitches) ->
            pitches.asSequence()
                .filter { isLegalVoicing(context, it, kind) }
                .distinct()
                .sortedWith(compareBy<List<Int>> { registerDistance(context, it) }.thenBy { it.joinToString(",") })
                .take(MAX_VOICINGS_PER_KIND)
                .map { MidiCorePianoVoicingCandidate(it, kind) }
                .toList()
        }.distinctBy(MidiCorePianoVoicingCandidate::pitches)
            .take(MAX_PIANO_VOICING_CANDIDATES)
    }

    /** Build open positions by moving one upper voice by an octave while preserving voice order. */
    private fun openVoicings(voicing: List<Int>): List<List<Int>> = voicing.drop(1).map { voice ->
        (voicing.filterNot { it == voice } + (voice + OCTAVE)).sorted()
    }.filter { candidate -> candidate.last() - candidate.first() >= MIN_OPEN_VOICING_SPAN }

    /** Enumerate proper two-or-more-voice subsets in stable mask order. */
    private fun subsets(voicing: List<Int>): List<List<Int>> = buildList {
        for (mask in 1 until (1 shl voicing.size) - 1) {
            voicing.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.takeIf { it.size >= 2 }?.let(::add)
        }
    }

    /** Retain the root, defining tones, and every extension; only the neutral fifth may be omitted. */
    private fun guideTonePitchClasses(window: app.melotrail.structure.MidiCoreResolvedChordWindow): Set<Int> {
        return window.chord.quality.intervals
            .filter { interval -> Math.floorMod(interval, 12) != PERFECT_FIFTH }
            .map { interval -> Math.floorMod(window.chord.rootPitchClass + interval, 12) }
            .toSet()
    }

    /** Slash bass is always retained; root omission is legal only when an accepted bass supplies it. */
    private fun requiredPitchClasses(
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
        guideClasses: Set<Int>,
        rootMayBeOmitted: Boolean,
    ): Set<Int> = buildSet {
        addAll(guideClasses)
        if (rootMayBeOmitted) remove(window.chord.rootPitchClass)
        window.chord.bass?.let { add(window.chord.bassPitchClass) }
    }

    /** One voicing serves the whole window, so root omission needs continuous bass-root coverage. */
    private fun bassSuppliesRoot(
        context: MidiCoreGenerationContext,
        window: app.melotrail.structure.MidiCoreResolvedChordWindow,
    ): Boolean {
        val roots = context.dependency(CandidateRole.BASS)?.notes.orEmpty()
            .filter { it.pitch % 12 == window.chord.rootPitchClass && it.startTick < window.endTick && it.endTick > window.startTick }
            .sortedBy { it.startTick }
        var coveredUntil = window.startTick
        for (bass in roots) {
            if (bass.startTick > coveredUntil) return false
            coveredUntil = maxOf(coveredUntil, bass.endTick)
            if (coveredUntil >= window.endTick) return true
        }
        return false
    }

    /** Keep every candidate in range, strictly ordered, and inside compact/open spacing limits. */
    private fun isLegalVoicing(
        context: MidiCoreGenerationContext,
        pitches: List<Int>,
        kind: MidiCorePianoVoicingKind,
    ): Boolean = pitches.size >= 2 && pitches.all(context.performanceProfile.register::contains) &&
        pitches.zipWithNext().all { (low, high) -> high > low && high - low <= kind.maximumAdjacentSpacing }

    private fun List<Int>.pitchClasses(): Set<Int> = map { Math.floorMod(it, 12) }.toSet()

    private fun List<Int>.containsPitchClasses(required: Set<Int>): Boolean = pitchClasses().containsAll(required)

    /** Enumerate all complete chord-tone inversions that fit the selected performance register. */
    private fun completeVoicingCandidates(
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
                voices.takeIf { candidate -> candidate.last() <= range.last && candidate.zipWithNext().all { (low, high) -> high - low in 1..MAX_VOICE_SPACING } }
            }
        }
    }

    private fun registerDistance(context: MidiCoreGenerationContext, pitches: List<Int>): Long =
        pitches.sumOf { pitch -> abs(pitch - preferredRegisterCenter(context)).toLong() }

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
        if (attack.support) return attack.endTick
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

    internal data class RhythmWindow(
        val startTick: Long,
        val endTick: Long,
        val velocityOffset: Int,
        val phraseBoundary: Boolean,
        val support: Boolean = false,
    )

    /** A fully selected bounded lookahead path, exposed to focused engine regressions only. */
    internal data class MidiCorePianoVoicingPlan(
        val voicings: List<List<Int>>,
        internal val rhythms: List<List<RhythmWindow>>,
        val beamWidths: List<Int>,
    )

    private data class VoicingPath(
        val score: Long,
        val lastVoicing: List<Int>?,
        val voicings: List<List<Int>>,
    ) {
        fun advance(candidate: MidiCorePianoVoicingCandidate, addedScore: Long): VoicingPath = VoicingPath(
            score = Math.addExact(score, addedScore),
            lastVoicing = candidate.pitches,
            voicings = voicings + listOf(candidate.pitches),
        )

        val stableKey: String get() = voicings.joinToString(";") { it.joinToString(",") }
    }

    /** Candidate kind is internal to the generator so later ranking can remain explainable. */
    internal enum class MidiCorePianoVoicingKind(
        val selectionPenalty: Long,
        val maximumAdjacentSpacing: Int,
    ) {
        CLOSED(selectionPenalty = 0, maximumAdjacentSpacing = MAX_VOICE_SPACING),
        OPEN(selectionPenalty = OPEN_VOICING_PENALTY, maximumAdjacentSpacing = MAX_OPEN_VOICE_SPACING),
        GUIDE_TONE(selectionPenalty = REDUCED_VOICING_PENALTY, maximumAdjacentSpacing = MAX_VOICE_SPACING),
        REDUCED(selectionPenalty = REDUCED_VOICING_PENALTY, maximumAdjacentSpacing = MAX_VOICE_SPACING),
    }

    /** An immutable legal candidate; generated notes expose only its selected pitches. */
    internal data class MidiCorePianoVoicingCandidate(
        val pitches: List<Int>,
        val kind: MidiCorePianoVoicingKind,
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
    private const val MAX_OPEN_VOICE_SPACING = 19
    private const val MIN_OPEN_VOICING_SPAN = 15
    private const val MAX_VOICE_MOVEMENT = 12
    private const val VOICE_COUNT_PENALTY = 24L
    private const val COMMON_TONE_BONUS = 10L
    private const val BASS_SPACE_SEMITONES = 5
    private const val OCTAVE = 12
    private const val PERFECT_FIFTH = 7
    private const val OPEN_VOICING_PENALTY = 16L
    private const val REDUCED_VOICING_PENALTY = 24L
    internal const val MAX_PIANO_VOICING_CANDIDATES = 48
    /** Maximum retained paths per phrase window; expansion is at most 12 × 48 candidates. */
    internal const val MAX_PIANO_VOICING_BEAM_WIDTH = 12
    private const val MAX_VOICINGS_PER_KIND = 12
    private const val ENERGY_VELOCITY_SPAN = 16.0
    private const val ENERGY_REGISTER_SPAN = 10.0
    private const val PHRASE_ACCENT = 3
    private const val SEED_STEP = 7_919L
    private const val MAX_SEED_VARIATIONS = 3
    private const val MAX_SEED_VARIATION_COST = 16L
    private const val DOWNBEAT_PROMINENCE = 1.0
    private const val GROUP_ACCENT_PROMINENCE = 0.75
    private const val DOWNBEAT_PROMINENCE_MULTIPLIER = 3L
    private const val GROUP_ACCENT_PROMINENCE_MULTIPLIER = 2L
    private const val UNACCENTED_PROMINENCE_MULTIPLIER = 1L
    private const val SUSTAINED_TENSION_MULTIPLIER = 4L
    private const val HELD_TENSION_MULTIPLIER = 2L
    private const val PASSING_TENSION_MULTIPLIER = 1L
    private const val LOW_MELODY_REGISTER_THRESHOLD = 55
    private const val LOW_MELODY_REGISTER_CLEARANCE = 12
    private const val LOW_MELODY_REGISTER_PENALTY = 18L
    private const val DENSE_MELODY_NUMERATOR = 2L
    private const val DENSE_MELODY_DENOMINATOR = 3L

    private val VOICE_MOVEMENT_ORDER = compareBy<VoiceMovement> {
        it.totalDistance + it.unmatchedVoices.toLong() * VOICE_COUNT_PENALTY
    }.thenBy { it.maximumDistance }
        .thenByDescending { it.commonPitches }
        .thenBy { it.unmatchedVoices }

    private val VOICING_PATH_ORDER = compareBy<VoicingPath> { it.score }
        .thenBy { it.stableKey }
}
