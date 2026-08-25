package app.melotrail.arrangement

import app.melotrail.harmony.ChordProgression
import app.melotrail.music.MusicalKey
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

/** A stable, typed musical pattern identity. It never identifies a MIDI file. */
@Serializable
@JvmInline
value class PatternId(val value: String) {
    init { require(ID.matches(value)) { "Pattern ID is invalid: $value" } }

    private companion object { val ID = Regex("[a-z][a-z0-9-]{0,47}(\\.[a-z][a-z0-9-]{0,47})+") }
}

@Serializable
enum class BassPatternId(val id: PatternId) {
    @SerialName("sustained-root") SUSTAINED_ROOT(PatternId("bass.sustained-root")),
    @SerialName("root-fifth") ROOT_FIFTH(PatternId("bass.root-fifth")),
    @SerialName("octave") OCTAVE(PatternId("bass.octave")),
    @SerialName("walk-to-next-root") WALK_TO_NEXT_ROOT(PatternId("bass.walk-to-next-root")),
    @SerialName("diatonic-approach") DIATONIC_APPROACH(PatternId("bass.diatonic-approach"));
}

@Serializable
enum class PadVoicingPatternId(val id: PatternId) {
    @SerialName("sustained") SUSTAINED(PatternId("pad.sustained")),
    @SerialName("close") CLOSE(PatternId("pad.close")),
    @SerialName("open") OPEN(PatternId("pad.open")),
    @SerialName("common-tone") COMMON_TONE(PatternId("pad.common-tone")),
    @SerialName("minimal") MINIMAL(PatternId("pad.minimal"));
}

@Serializable
enum class DrumGroovePatternId(val id: PatternId) {
    @SerialName("dusty-straight") DUSTY_STRAIGHT(PatternId("drums.dusty-straight")),
    @SerialName("lazy-swing") LAZY_SWING(PatternId("drums.lazy-swing")),
    @SerialName("half-time-pocket") HALF_TIME_POCKET(PatternId("drums.half-time-pocket")),
    @SerialName("lift-build") LIFT_BUILD(PatternId("drums.lift-build"));
}

@Serializable
/** Stable identities for fills placed in the final bar of a section. */
enum class DrumFillPatternId(val id: PatternId) {
    @SerialName("soft-two-stroke") SOFT_TWO_STROKE(PatternId("drums.fill.soft-two-stroke")),
    @SerialName("dusty-snare-roll") DUSTY_SNARE_ROLL(PatternId("drums.fill.dusty-snare-roll")),
    @SerialName("kick-snare-turnaround") KICK_SNARE_TURNAROUND(PatternId("drums.fill.kick-snare-turnaround")),
    @SerialName("bridge-half-time-break") BRIDGE_HALF_TIME_BREAK(PatternId("drums.fill.bridge-half-time-break"));
}

@Serializable
/** Stable beat-relative identities for harmony-key comping rhythms. */
enum class ChordRhythmPatternId(val id: PatternId) {
    @SerialName("sustained") SUSTAINED(PatternId("chords.rhythm.sustained")),
    @SerialName("laid-back-quarters") LAID_BACK_QUARTERS(PatternId("chords.rhythm.laid-back-quarters")),
    @SerialName("dusty-offbeats") DUSTY_OFFBEATS(PatternId("chords.rhythm.dusty-offbeats")),
    @SerialName("broken-syncopation") BROKEN_SYNCOPATION(PatternId("chords.rhythm.broken-syncopation")),
    @SerialName("bridge-half-time") BRIDGE_HALF_TIME(PatternId("chords.rhythm.bridge-half-time"));
}

@Serializable
enum class TransitionPatternId(val id: PatternId) {
    @SerialName("drum-fill") DRUM_FILL(PatternId("transition.drum-fill")),
    @SerialName("bass-approach") BASS_APPROACH(PatternId("transition.bass-approach")),
    @SerialName("pad-sustain") PAD_SUSTAIN(PatternId("transition.pad-sustain")),
    @SerialName("drop-build") DROP_BUILD(PatternId("transition.drop-build"));
}

/** Each schema carries its seed even where the selected pattern has no variation. */
@Serializable data class BassPatternParameters(val pattern: BassPatternId, val seed: Long = 0, val velocity: Int = 76) {
    init { require(velocity in 1..127) { "Bass pattern velocity must be 1..127" } }
}
@Serializable data class PadPatternParameters(val pattern: PadVoicingPatternId, val seed: Long = 0, val velocity: Int = 58) {
    init { require(velocity in 1..127) { "Pad pattern velocity must be 1..127" } }
}
@Serializable data class DrumPatternParameters(val pattern: DrumGroovePatternId, val seed: Long = 0, val velocity: Int = 76) {
    init { require(velocity in 1..127) { "Drum pattern velocity must be 1..127" } }
}
@Serializable data class TransitionPatternParameters(val pattern: TransitionPatternId, val seed: Long = 0, val velocity: Int = 76) {
    init { require(velocity in 1..127) { "Transition pattern velocity must be 1..127" } }
}

/** Canonical project music, deliberately independent of analysis, prompts, or raw MIDI paths. */
data class CanonicalPatternContext(
    val key: MusicalKey,
    val tempo: Tempo,
    val meter: TimeSignature,
    val progression: ChordProgression,
    val ppq: Int = 480
) {
    init {
        require(key.isExecutable) { "Pattern generation requires an executable project key" }
        require(ppq in 24..9_600) { "Pattern PPQ must be 24..9600" }
        progression.requireWellFormed()
        progression.requireExecutable()
        require(progression.events.isNotEmpty()) { "Pattern generation requires canonical harmony" }
    }

    val ticksPerBeat: Long get() = (ppq * 4L) / meter.denominator
    val ticksPerMeasure: Long get() = ticksPerBeat * meter.numerator
    val durationTicks: Long get() = progression.events.sumOf { it.effectiveDurationMeasures.toLong() * ticksPerMeasure }
}

data class PatternMidiNote(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int) {
    init { require(startTick >= 0 && endTick > startTick && pitch in 0..127 && velocity in 1..127) { "Pattern MIDI note is invalid" } }
}
/** A bounded local harmony window for context-aware bass generation. */
data class BassPatternWindow(val startTick: Long, val endTick: Long, val beatTicks: Long, val root: Int, val nextRoot: Int) {
    init { require(startTick >= 0 && endTick > startTick && beatTicks > 0 && root in 0..11 && nextRoot in 0..11) { "Bass pattern window is invalid" } }
}
data class PatternDrumHit(val name: String, val startTick: Long, val endTick: Long, val velocity: Int) {
    init { require(name in DRUM_HIT_NAMES && startTick >= 0 && endTick > startTick && velocity in 1..127) { "Pattern drum hit is invalid" } }
    companion object { val DRUM_HIT_NAMES = setOf("kick", "snare", "closedHat", "openHat") }
}
data class TransitionPatternResult(val bass: List<PatternMidiNote> = emptyList(), val pad: List<PatternMidiNote> = emptyList(), val drums: List<PatternDrumHit> = emptyList(), val dropBuild: DropBuildInstruction? = null)
data class DropBuildInstruction(val dropMeasures: Int, val buildMeasures: Int) {
    init { require(dropMeasures >= 0 && buildMeasures > 0) { "Drop/build measures are invalid" } }
}

/** A named, in-code human-curated groove. The steps are musical positions, never file names. */
data class CuratedDrumGroove(val id: DrumGroovePatternId, val displayName: String, val steps: List<CuratedDrumStep>)
data class CuratedDrumStep(val hit: String, val sixteenth: Int, val velocityOffset: Int) {
    init { require(hit in PatternDrumHit.DRUM_HIT_NAMES && sixteenth in 0..15) { "Curated groove step is invalid" } }
}
/** One reviewable fill and its named starter-kit attacks. */
data class CuratedDrumFill(val id: DrumFillPatternId, val displayName: String, val steps: List<CuratedDrumStep>)
/** One reviewable harmony-key rhythm and its beat-relative attacks. */
data class CuratedChordRhythm(val id: ChordRhythmPatternId, val displayName: String, val steps: List<CuratedChordStep>)
/** One chord attack in a 16-step 4/4 bar, including its bounded duration and accent. */
data class CuratedChordStep(val sixteenth: Int, val durationSixteenths: Int, val velocityOffset: Int) {
    init {
        require(sixteenth in 0..15 && durationSixteenths in 1..16 && sixteenth + durationSixteenths <= 16) {
            "Curated chord-rhythm step is invalid"
        }
    }
}

/**
 * Deterministic implementations for the bounded arrangement vocabulary. These
 * algorithms may be selected by a future producer schema, but they never accept
 * prompt text, raw filenames, or arbitrary MIDI note lists.
 */
object MusicalPatternLibrary {
    val drumGrooves: List<CuratedDrumGroove> = listOf(
        CuratedDrumGroove(DrumGroovePatternId.DUSTY_STRAIGHT, "Dusty straight", listOf(
            step("kick", 0, 8), step("kick", 8, 2), step("snare", 4, 5), step("snare", 12, 7),
            *hats(0, 2, -10)
        )),
        CuratedDrumGroove(DrumGroovePatternId.LAZY_SWING, "Lazy swing", listOf(
            step("kick", 0, 8), step("kick", 10, 1), step("snare", 4, 5), step("snare", 12, 7),
            *hats(0, 2, -12)
        )),
        CuratedDrumGroove(DrumGroovePatternId.HALF_TIME_POCKET, "Half-time pocket", listOf(
            step("kick", 0, 8), step("kick", 10, 1), step("snare", 8, 7), *hats(0, 2, -11)
        )),
        CuratedDrumGroove(DrumGroovePatternId.LIFT_BUILD, "Lift build", listOf(
            step("kick", 0, 5), step("kick", 8, 5), step("snare", 4, 4), step("snare", 12, 6), *hats(0, 1, -9)
        ))
    )

    /** Section-ending fill vocabulary. Positions are sixteenths in the section's final 4/4 bar. */
    val drumFills: List<CuratedDrumFill> = listOf(
        CuratedDrumFill(DrumFillPatternId.SOFT_TWO_STROKE, "Soft two-stroke", listOf(
            step("snare", 14, -6), step("snare", 15, 2)
        )),
        CuratedDrumFill(DrumFillPatternId.DUSTY_SNARE_ROLL, "Dusty snare roll", listOf(
            step("snare", 12, 0), step("snare", 13, 4), step("snare", 14, 8), step("snare", 15, 12)
        )),
        CuratedDrumFill(DrumFillPatternId.KICK_SNARE_TURNAROUND, "Kick-snare turnaround", listOf(
            step("kick", 12, -2), step("snare", 14, 4), step("snare", 15, 9)
        )),
        CuratedDrumFill(DrumFillPatternId.BRIDGE_HALF_TIME_BREAK, "Bridge half-time break", listOf(
            step("openHat", 8, -10), step("kick", 10, -4), step("snare", 12, 1),
            step("kick", 14, -2), step("snare", 15, 7)
        ))
    )

    /** Lo-fi chord-comping vocabulary. Positions and durations are relative to a 4/4 bar. */
    val chordRhythms: List<CuratedChordRhythm> = listOf(
        CuratedChordRhythm(ChordRhythmPatternId.SUSTAINED, "Sustained", listOf(CuratedChordStep(0, 16, 0))),
        CuratedChordRhythm(ChordRhythmPatternId.LAID_BACK_QUARTERS, "Laid-back quarters", listOf(
            CuratedChordStep(0, 3, -2), CuratedChordStep(4, 3, -5), CuratedChordStep(8, 3, 0), CuratedChordStep(12, 3, -4)
        )),
        CuratedChordRhythm(ChordRhythmPatternId.DUSTY_OFFBEATS, "Dusty offbeats", listOf(
            CuratedChordStep(2, 2, -6), CuratedChordStep(6, 2, -3), CuratedChordStep(10, 2, -5), CuratedChordStep(14, 2, -1)
        )),
        CuratedChordRhythm(ChordRhythmPatternId.BROKEN_SYNCOPATION, "Broken syncopation", listOf(
            CuratedChordStep(0, 3, 0), CuratedChordStep(6, 2, -5), CuratedChordStep(10, 2, -3), CuratedChordStep(14, 2, -1)
        )),
        CuratedChordRhythm(ChordRhythmPatternId.BRIDGE_HALF_TIME, "Bridge half-time", listOf(
            CuratedChordStep(0, 6, -5), CuratedChordStep(8, 6, -2)
        ))
    )

    /** Resolve one validated drum-groove identity. */
    fun drumGroove(id: DrumGroovePatternId): CuratedDrumGroove = drumGrooves.single { it.id == id }
    /** Resolve one validated section-fill identity. */
    fun drumFill(id: DrumFillPatternId): CuratedDrumFill = drumFills.single { it.id == id }
    /** Resolve one validated chord-rhythm identity. */
    fun chordRhythm(id: ChordRhythmPatternId): CuratedChordRhythm = chordRhythms.single { it.id == id }

    /** Compose the selected bass pattern from the canonical progression. */
    fun bass(context: CanonicalPatternContext, parameters: BassPatternParameters): List<PatternMidiNote> {
        val chords = chordWindows(context)
        return chords.flatMapIndexed { index, chord ->
            val next = chords.getOrNull(index + 1)?.root ?: chord.root
            bassForChord(context, chord, next, parameters)
        }
    }

    /** Render a selected library pattern for one analysis-derived chord window. */
    fun bassWindow(window: BassPatternWindow, parameters: BassPatternParameters): List<PatternMidiNote> {
        val beats = ((window.endTick - window.startTick) / window.beatTicks).toInt().coerceAtLeast(1)
        val classes = when (parameters.pattern) {
            BassPatternId.SUSTAINED_ROOT -> listOf(window.root)
            BassPatternId.ROOT_FIFTH -> (0 until beats).map { if (it % 2 == 0) window.root else (window.root + 7) % 12 }
            BassPatternId.OCTAVE -> (0 until beats).map { if (it % 2 == 0) window.root else window.root + 12 }
            BassPatternId.WALK_TO_NEXT_ROOT -> (0 until beats).map { if (it == beats - 1) window.nextRoot else walkPitch(window.root, window.nextRoot, it, beats) }
            BassPatternId.DIATONIC_APPROACH -> (0 until beats).map { if (it == beats - 1) (window.nextRoot + 11) % 12 else window.root }
        }
        return classes.mapIndexed { index, pitchClass ->
            val start = window.startTick + index.toLong() * window.beatTicks
            PatternMidiNote(start, if (parameters.pattern == BassPatternId.SUSTAINED_ROOT) window.endTick else minOf(window.endTick, start + window.beatTicks * 3 / 4), lowBassPitch(pitchClass), parameters.velocity)
        }
    }

    /** Voice every canonical chord using the selected bounded pad strategy. */
    fun pad(context: CanonicalPatternContext, parameters: PadPatternParameters): List<PatternMidiNote> {
        var previous: List<Int>? = null
        return chordWindows(context).flatMap { chord ->
            val voicing = padVoicing(chord, parameters.pattern, previous)
            previous = voicing
            voicing.map { PatternMidiNote(chord.start, chord.end - releaseGap(context), it, parameters.velocity) }
        }
    }

    /** Repeat a curated musical drum grid across the canonical song duration. */
    fun drums(context: CanonicalPatternContext, parameters: DrumPatternParameters): List<PatternDrumHit> {
        val groove = drumGrooves.single { it.id == parameters.pattern }
        val sixteenth = (context.ticksPerBeat / 4).coerceAtLeast(1)
        val measures = (context.durationTicks + context.ticksPerMeasure - 1) / context.ticksPerMeasure
        return (0 until measures).flatMap { measure ->
            groove.steps.mapNotNull { step ->
                val nominal = measure * context.ticksPerMeasure + step.sixteenth * sixteenth
                if (nominal >= context.durationTicks) null else {
                    val shifted = (nominal + humanTickOffset(context, parameters.seed, measure, step.sixteenth, parameters.pattern)).coerceIn(0, context.durationTicks - 1)
                    PatternDrumHit(step.hit, shifted, minOf(context.durationTicks, shifted + minOf(60L, sixteenth)), (parameters.velocity + step.velocityOffset).coerceIn(1, 127))
                }
            }
        }.sortedWith(compareBy<PatternDrumHit> { it.startTick }.thenBy { it.name })
    }

    /** Produce the bounded accompaniment material or instruction for one transition type. */
    fun transition(context: CanonicalPatternContext, parameters: TransitionPatternParameters): TransitionPatternResult = when (parameters.pattern) {
        TransitionPatternId.DRUM_FILL -> TransitionPatternResult(drums = fill(context, parameters))
        TransitionPatternId.BASS_APPROACH -> TransitionPatternResult(bass = bass(context, BassPatternParameters(BassPatternId.DIATONIC_APPROACH, parameters.seed, parameters.velocity)))
        TransitionPatternId.PAD_SUSTAIN -> TransitionPatternResult(pad = pad(context, PadPatternParameters(PadVoicingPatternId.SUSTAINED, parameters.seed, parameters.velocity)))
        TransitionPatternId.DROP_BUILD -> TransitionPatternResult(dropBuild = DropBuildInstruction(dropMeasures = 1, buildMeasures = 1))
    }

    /** Render one chord window without permitting notes outside the bass register. */
    private fun bassForChord(context: CanonicalPatternContext, chord: ChordWindow, nextRoot: Int, parameters: BassPatternParameters): List<PatternMidiNote> {
        val beat = context.ticksPerBeat
        val beats = ((chord.end - chord.start) / beat).toInt().coerceAtLeast(1)
        val roots = when (parameters.pattern) {
            BassPatternId.SUSTAINED_ROOT -> listOf(0 to chord.root)
            BassPatternId.ROOT_FIFTH -> (0 until beats).map { it to if (it % 2 == 0) chord.root else (chord.root + 7) % 12 }
            BassPatternId.OCTAVE -> (0 until beats).map { it to chord.root + if (it % 2 == 0) 0 else 12 }
            BassPatternId.WALK_TO_NEXT_ROOT -> (0 until beats).map { beatIndex -> beatIndex to walkPitch(chord.root, nextRoot, beatIndex, beats) }
            BassPatternId.DIATONIC_APPROACH -> (0 until beats).map { beatIndex ->
                beatIndex to if (beatIndex == beats - 1 && nextRoot != chord.root) diatonicApproach(context.key, nextRoot, parameters.seed, chord.start) else chord.root
            }
        }
        return roots.map { (index, pitchClass) ->
            val start = chord.start + index * beat
            PatternMidiNote(start, if (parameters.pattern == BassPatternId.SUSTAINED_ROOT) chord.end else minOf(chord.end, start + (beat * 3 / 4).coerceAtLeast(1)), lowBassPitch(pitchClass), parameters.velocity)
        }
    }

    /** Build a four-stroke ascending snare fill in the final beat. */
    private fun fill(context: CanonicalPatternContext, parameters: TransitionPatternParameters): List<PatternDrumHit> {
        val step = (context.ticksPerBeat / 4).coerceAtLeast(1)
        val start = (context.durationTicks - context.ticksPerBeat).coerceAtLeast(0)
        return (0 until 4).mapNotNull { index ->
            val tick = start + index * step
            if (tick >= context.durationTicks) null else PatternDrumHit("snare", tick, minOf(context.durationTicks, tick + step), (parameters.velocity + index * 4).coerceIn(1, 127))
        }
    }

    /** Select a playable pad voicing while preserving the exact chord pitch classes. */
    private fun padVoicing(chord: ChordWindow, pattern: PadVoicingPatternId, previous: List<Int>?): List<Int> {
        val classes = chord.tones
        val candidates = when (pattern) {
            PadVoicingPatternId.MINIMAL -> listOf(listOf(closePitch(classes.first(), 55), closePitch(classes.first { (it - classes.first() + 12) % 12 == 7 }, 62)))
            PadVoicingPatternId.CLOSE, PadVoicingPatternId.SUSTAINED, PadVoicingPatternId.COMMON_TONE -> closeCandidates(classes)
            PadVoicingPatternId.OPEN -> openCandidates(classes)
        }
        return candidates.minWith(compareBy<List<Int>> {
            if (pattern == PadVoicingPatternId.COMMON_TONE && previous != null) previous.intersect(it.toSet()).size.unaryMinus()
            else previous?.zip(it)?.sumOf { (left, right) -> abs(left - right) } ?: 0
        }.thenBy { it.joinToString(",") })
    }

    /** Enumerate compact inversions inside the safe pad register. */
    private fun closeCandidates(classes: List<Int>): List<List<Int>> = (0 until classes.size).map { inversion ->
        val ordered = classes.drop(inversion) + classes.take(inversion)
        val first = closePitch(ordered.first(), 55)
        buildList { add(first); ordered.drop(1).forEach { add(nextAtOrAbove(last() + 1, it)) } }
    }.filter { it.all { pitch -> pitch in 48..83 } }

    /** Spread selected upper voices by an octave for an open voicing. */
    private fun openCandidates(classes: List<Int>): List<List<Int>> = closeCandidates(classes).map { close ->
        close.mapIndexed { index, pitch -> if (index > 0 && index % 2 == 0) pitch + 12 else pitch }.filter { it in 48..83 }
    }.filter { it.size >= 2 }

    /** Convert authoritative measure durations into the shared PPQ timeline. */
    private fun chordWindows(context: CanonicalPatternContext): List<ChordWindow> {
        var tick = 0L
        return context.progression.events.map { chord ->
            val end = tick + chord.effectiveDurationMeasures * context.ticksPerMeasure
            ChordWindow(tick, end, chord.root.chromatic, chord.quality.intervals.map { (chord.root.chromatic + it) % 12 }).also { tick = end }
        }
    }

    /** Move in the shortest chromatic direction towards the following root. */
    private fun walkPitch(from: Int, to: Int, index: Int, beats: Int): Int {
        if (beats == 1) return from
        val signed = ((to - from + 18) % 12) - 6
        return from + signed * index / beats
    }

    /** Pick either adjacent scale degree as a reproducible approach to the next root. */
    private fun diatonicApproach(key: MusicalKey, target: Int, seed: Long, position: Long): Int {
        val scale = key.scalePitchClasses().map { it.chromatic }
        val targetIndex = scale.indexOf(target)
        if (targetIndex < 0) return (target + if (seededBit(seed, position)) -1 else 1).floorClass()
        return scale[Math.floorMod(targetIndex + if (seededBit(seed, position)) -1 else 1, scale.size)]
    }

    /** Fold a pitch into E1--C3 while retaining an explicit octave request. */
    private fun lowBassPitch(pitchClass: Int): Int {
        var pitch = 36 + pitchClass
        while (pitch < 28) pitch += 12
        while (pitch > 48) pitch -= 12
        return pitch
    }
    /** Place a pitch class at or above a target register centre. */
    private fun closePitch(pitchClass: Int, center: Int): Int = center + Math.floorMod(pitchClass - center, 12)
    /** Place a pitch class at or above a previously selected voice. */
    private fun nextAtOrAbove(minimum: Int, pitchClass: Int): Int = minimum + Math.floorMod(pitchClass - minimum, 12)
    /** Normalize an arbitrary integer to its chromatic pitch class. */
    private fun Int.floorClass(): Int = Math.floorMod(this, 12)
    /** Reserve a short deterministic release before the next chord event. */
    private fun releaseGap(context: CanonicalPatternContext): Long = maxOf(1, context.ppq / 24).toLong()
    /** Produce one stable binary variation decision from persisted seed and position. */
    private fun seededBit(seed: Long, position: Long): Boolean = mix(seed xor position) and 1L == 0L
    /** Convert a bounded ten-millisecond human offset into ticks at the active tempo. */
    private fun humanTickOffset(context: CanonicalPatternContext, seed: Long, measure: Long, sixteenth: Int, id: DrumGroovePatternId): Long {
        val max = (context.tempo.bpm * context.ppq / 60_000.0 * 10.0).roundToLong().coerceIn(0, context.ticksPerBeat / 6)
        return (Math.floorMod(mix(seed xor measure xor sixteenth.toLong() xor id.ordinal.toLong()), max * 2 + 1) - max)
    }
    /** Mix deterministic inputs without using process-global randomness. */
    private fun mix(value: Long): Long {
        var mixed = value xor (value ushr 33)
        mixed *= -49064778989728563L
        mixed = mixed xor (mixed ushr 33)
        mixed *= -4265267296055464877L
        return mixed xor (mixed ushr 33)
    }
    /** Define one reviewable curated groove step. */
    private fun step(hit: String, sixteenth: Int, velocity: Int) = CuratedDrumStep(hit, sixteenth, velocity)
    /** Expand a regular closed-hat grid into explicit musical steps. */
    private fun hats(start: Int, step: Int, velocity: Int): Array<CuratedDrumStep> = (start..15 step step).map { CuratedDrumStep("closedHat", it, velocity) }.toTypedArray()
    private data class ChordWindow(val start: Long, val end: Long, val root: Int, val tones: List<Int>)
}

/** One reviewable section-aware lo-fi selection policy over the bounded catalogs above. */
object LoFiSectionPatternPolicy {
    /** Select a lo-fi drum pocket for one structural purpose. */
    fun drumGroove(purpose: SongSectionPurpose): DrumGroovePatternId = when (purpose) {
        SongSectionPurpose.INTRODUCTION -> DrumGroovePatternId.LAZY_SWING
        SongSectionPurpose.DEVELOPMENT -> DrumGroovePatternId.DUSTY_STRAIGHT
        SongSectionPurpose.CLIMAX -> DrumGroovePatternId.LIFT_BUILD
        SongSectionPurpose.RELEASE -> DrumGroovePatternId.HALF_TIME_POCKET
        SongSectionPurpose.CONCLUSION -> DrumGroovePatternId.LAZY_SWING
    }

    /** Select a distinct final-bar fill for one structural purpose. */
    fun drumFill(purpose: SongSectionPurpose): DrumFillPatternId = when (purpose) {
        SongSectionPurpose.INTRODUCTION, SongSectionPurpose.CONCLUSION -> DrumFillPatternId.SOFT_TWO_STROKE
        SongSectionPurpose.DEVELOPMENT -> DrumFillPatternId.DUSTY_SNARE_ROLL
        SongSectionPurpose.CLIMAX -> DrumFillPatternId.KICK_SNARE_TURNAROUND
        SongSectionPurpose.RELEASE -> DrumFillPatternId.BRIDGE_HALF_TIME_BREAK
    }

    /** Select a lo-fi harmony-key comping rhythm for one structural purpose. */
    fun chordRhythm(purpose: SongSectionPurpose): ChordRhythmPatternId = when (purpose) {
        SongSectionPurpose.INTRODUCTION, SongSectionPurpose.CONCLUSION -> ChordRhythmPatternId.SUSTAINED
        SongSectionPurpose.DEVELOPMENT -> ChordRhythmPatternId.LAID_BACK_QUARTERS
        SongSectionPurpose.CLIMAX -> ChordRhythmPatternId.BROKEN_SYNCOPATION
        SongSectionPurpose.RELEASE -> ChordRhythmPatternId.BRIDGE_HALF_TIME
    }
}
