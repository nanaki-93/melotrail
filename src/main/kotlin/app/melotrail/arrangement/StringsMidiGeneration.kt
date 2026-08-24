package app.melotrail.arrangement

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The only deterministic strings techniques. They are decisions, never raw model-supplied notes. */
enum class StringsMidiRole { SUSTAINED_HARMONY, CLIMAX_REINFORCEMENT, LONG_NOTES, SIMPLE_COUNTERMELODY }

data class StringsMidiNote(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

/**
 * Section-local, validated data for strings generation. Strings remain above
 * bass and use a distinct register from pads: low G3--C5, mid C4--G5, high
 * G4--E6. Source range facts are used only as conservative collision guards.
 */
data class StringsGenerationRequest(
    val sectionIndex: Int,
    val sectionPurpose: SongSectionPurpose,
    val sectionStartTick: Long,
    val ppq: Int,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val sectionLengthTicks: Long,
    val key: MidiKey?,
    val chords: List<MidiChord>,
    val sourcePitchRange: MidiIntRange?,
    val sourceMelodicRange: Int?,
    val sourceNoteDensity: Double,
    val sourceRhythmicDensity: Double,
    val energy: Double,
    val density: Double,
    val role: StringsMidiRole,
    val register: String,
    val midiChannel: Int = 0,
    val midiProgram: Int? = null,
    /** Complete accepted core arrangement, not just the source analysis. */
    val arrangementState: ArrangementState? = null,
    /** Section capacity registered from the approved core before strings run. */
    val densityBudget: DensityBudget? = null,
    /** Last accepted strings voicing from the immediately preceding generated section. */
    val previousAcceptedVoicing: List<Int> = emptyList()
) {
    fun requireValid() {
        require(sectionIndex >= 0 && sectionStartTick >= 0 && sectionLengthTicks > 0) { "Strings section timing is invalid" }
        require(ppq in 24..9_600) { "Strings PPQ must be from 24 to 9600" }
        require(energy.isFinite() && energy in 0.0..1.0 && density.isFinite() && density in 0.0..1.0) { "Strings energy and density must be from 0.0 to 1.0" }
        require(sourceNoteDensity.isFinite() && sourceNoteDensity in 0.0..1.0 && sourceRhythmicDensity.isFinite() && sourceRhythmicDensity in 0.0..1.0) { "Strings source density facts must be from 0.0 to 1.0" }
        require(register in REGISTER_RANGES) { "Unsupported strings register '$register'. Allowed registers: ${REGISTER_RANGES.keys.joinToString()}" }
        require(midiChannel in 0..15 && (midiProgram == null || midiProgram in 0..127)) { "Strings MIDI routing is invalid" }
        arrangementState?.requireTrack(ArrangementState.PIANO)
        require(previousAcceptedVoicing.size <= AcceptedPadStringVoicing.MAXIMUM_VOICES && previousAcceptedVoicing.all { it in 0..127 } &&
            previousAcceptedVoicing == previousAcceptedVoicing.distinct().sorted()) { "Previous accepted strings voicing is invalid" }
        densityBudget?.let {
            require(it.startTick == sectionStartTick && it.endTick == sectionStartTick + sectionLengthTicks) {
                "Strings density budget does not match its section"
            }
        }
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L && timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "Strings maps must start at tick 0" }
        tempoMap.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Strings tempo changes must be ordered" } }
        timeSignatures.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Strings time signatures must be ordered" } }
        tempoMap.forEach { require(it.tick in 0..sectionLengthTicks && it.bpm.isFinite() && it.bpm > 0.0) { "Strings tempo map contains an invalid change" } }
        timeSignatures.forEach { signature ->
            require(signature.tick in 0..sectionLengthTicks && signature.numerator > 0 && signature.denominator in setOf(1, 2, 4, 8, 16, 32) && (ppq * 4) % signature.denominator == 0) { "Strings time-signature map contains an invalid change" }
        }
        chords.zipWithNext().forEach { (first, second) -> require(first.endTick <= second.startTick) { "Strings chords must not overlap" } }
        chords.forEach { require(it.startTick >= 0 && it.endTick > it.startTick && it.endTick <= sectionLengthTicks && it.confidence.isFinite() && it.confidence in 0.0..1.0) { "Strings chord segment is invalid" } }
        sourcePitchRange?.let { require(it.min in 0..127 && it.max in it.min..127) { "Strings source pitch range is invalid" } }
        sourceMelodicRange?.let { require(it >= 0) { "Strings source melodic range must not be negative" } }
    }

    companion object {
        val REGISTER_RANGES = mapOf("low" to (55..72), "mid" to (60..79), "high" to (67..88))
    }
}

data class StringsGenerationResult(val notes: List<StringsMidiNote>, val diagnostics: List<String>)

class DeterministicStringsMidiGenerator {
    fun generate(request: StringsGenerationRequest): StringsGenerationResult {
        request.requireValid()
        if (request.density == 0.0) return StringsGenerationResult(emptyList(), listOf("Strings density is 0.0; wrote silence."))
        if (request.densityBudget?.permitsOptionalLayer == false) {
            return StringsGenerationResult(emptyList(), listOf("Strings resolved OFF: approved core density budget is fully occupied."))
        }
        if (request.role == StringsMidiRole.CLIMAX_REINFORCEMENT && request.sectionPurpose != SongSectionPurpose.CLIMAX) {
            return StringsGenerationResult(emptyList(), listOf("Climax reinforcement is silent outside a climax section."))
        }
        if (request.role == StringsMidiRole.SIMPLE_COUNTERMELODY) {
            val counter = counterMelody(request)
            if (counter != null) return counter
            val fallback = sustained(request, StringsMidiRole.SUSTAINED_HARMONY)
            return fallback.copy(diagnostics = listOf("Countermelody did not meet strict harmony/source-space gates; fell back to sustained harmony.") + fallback.diagnostics)
        }
        return sustained(request, request.role)
    }

    private fun sustained(request: StringsGenerationRequest, role: StringsMidiRole): StringsGenerationResult {
        val diagnostics = mutableListOf<String>()
        val range = collisionSafeRange(request)
        if (range == null) return StringsGenerationResult(emptyList(), listOf("Strings register has no practical space above the source range; wrote silence."))
        var previous: List<Int>? = request.previousAcceptedVoicing.takeIf { it.isNotEmpty() }
        val notes = selectedChords(request.chords, request.density).flatMap { chord ->
            val harmony = harmonyFor(request, chord, diagnostics) ?: return@flatMap emptyList()
            val selected = selectVoicing(harmony, role, request.energy, range, previous, request, chord)
            if (selected == null) {
                diagnostics += "No strings voicing fits the collision-safe ${request.register} register at tick ${chord.startTick}; left silent."
                return@flatMap emptyList()
            }
            if (!densityPermits(request, selected.pitches.size)) {
                diagnostics += "Strings resolved OFF at tick ${request.sectionStartTick + chord.startTick}: density budget has ${request.densityBudget?.remaining ?: 0} remaining slots for ${selected.pitches.size} voices."
                return@flatMap emptyList()
            }
            previous = selected.pitches
            if (selected.pitches.size < preferredVoiceCount(harmony, role, request.energy)) {
                diagnostics += "Reduced strings to ${selected.pitches.size} collision-free voice(s) at tick ${chord.startTick}."
            }
            val chordStart = request.sectionStartTick + chord.startTick
            val chordEnd = request.sectionStartTick + chord.endTick
            if (selected.startTick != chordStart || selected.endTick != chordEnd) {
                diagnostics += "Placed strings in a source-melody gap at ticks ${selected.startTick}-${selected.endTick}."
            }
            val gap = minOf(releaseGapTicks(request.ppq), selected.endTick - selected.startTick - 1)
            selected.pitches.map { pitch -> StringsMidiNote(selected.startTick, selected.endTick - gap, pitch, velocity(request.energy, role)) }
        }
        validate(notes, request, range)
        if (notes.isEmpty() && diagnostics.isEmpty()) diagnostics += "No strings chord segments were selected."
        return StringsGenerationResult(notes.sortedWith(compareBy<StringsMidiNote> { it.startTick }.thenBy { it.pitch }), diagnostics.distinct())
    }

    private fun counterMelody(request: StringsGenerationRequest): StringsGenerationResult? {
        if (!counterMelodyAllowed(request)) return null
        val range = collisionSafeRange(request) ?: return null
        val diagnostics = mutableListOf<String>()
        val notes = mutableListOf<StringsMidiNote>()
        var previous: Int? = null
        selectedChords(request.chords, request.density).forEachIndexed { index, chord ->
            val harmony = harmonyFor(request, chord, diagnostics) ?: return null
            val beat = (request.ppq * 4L / request.timeSignatures.last { it.tick <= chord.startTick }.denominator).coerceAtLeast(1)
            val start = request.sectionStartTick + chord.startTick
            val responseEnd = minOf(request.sectionStartTick + chord.endTick, start + beat)
            if (request.arrangementState?.melodyIsActive(start, responseEnd) == true) {
                diagnostics += "Countermelody skipped tick $start because the source melody is active."
                return@forEachIndexed
            }
            if (!densityPermits(request, 1)) {
                diagnostics += "Countermelody skipped tick $start because the density budget has no remaining slot."
                return@forEachIndexed
            }
            val pitch = contourPitch(harmony, range, previous, index) ?: return null
            val end = minOf(chord.endTick, chord.startTick + beat) - minOf(releaseGapTicks(request.ppq), beat - 1)
            if (end <= chord.startTick) return null
            notes += StringsMidiNote(request.sectionStartTick + chord.startTick, request.sectionStartTick + end, pitch, velocity(request.energy, StringsMidiRole.SIMPLE_COUNTERMELODY))
            previous = pitch
        }
        if (notes.isEmpty()) return null
        diagnostics += "Countermelody used ${notes.size} melodic response gap(s)."
        validate(notes, request, range)
        return StringsGenerationResult(notes, diagnostics.distinct())
    }

    private fun counterMelodyAllowed(request: StringsGenerationRequest): Boolean {
        val selected = selectedChords(request.chords, request.density)
        return request.key?.confidence?.let { it > COUNTER_KEY_CONFIDENCE } == true && selected.isNotEmpty() &&
            selected.all { it.confidence > COUNTER_CHORD_CONFIDENCE && parseChord(it.symbol) != null } &&
            (request.sourcePitchRange == null || request.sourcePitchRange.max < COUNTER_SOURCE_MAX_PITCH) &&
            (request.sourceMelodicRange == null || request.sourceMelodicRange <= COUNTER_SOURCE_MAX_RANGE) &&
            request.sourceNoteDensity <= COUNTER_SOURCE_MAX_DENSITY && request.sourceRhythmicDensity <= COUNTER_SOURCE_MAX_RHYTHMIC_DENSITY
    }

    private fun contourPitch(harmony: Harmony, range: IntRange, previous: Int?, index: Int): Int? {
        val preferred = (harmony.root + harmony.intervals[index % minOf(3, harmony.intervals.size)]) % 12
        val tones = harmony.intervals.map { (harmony.root + it) % 12 }.distinct()
        val candidates = (range.first..range.last).filter { it % 12 in tones }
        val ordered = candidates.sortedWith(compareBy<Int> { if (it % 12 == preferred) 0 else 1 }.thenBy { previous?.let { before -> abs(it - before) } ?: abs(it - registerCenter(range)) }.thenBy { it })
        val chosen = ordered.firstOrNull() ?: return null
        return chosen.takeIf { previous == null || abs(it - previous) <= COUNTER_MAX_STEP }
    }

    private fun collisionSafeRange(request: StringsGenerationRequest): IntRange? {
        val configured = requireNotNull(StringsGenerationRequest.REGISTER_RANGES[request.register])
        val sourceTop = request.sourcePitchRange?.max ?: return configured
        val start = maxOf(configured.first, sourceTop + SOURCE_CLEARANCE_SEMITONES)
        // Prefer a globally separate register when one exists. Dense, wide-range
        // piano transcriptions instead use exact time-local collision checks.
        return if (configured.last - start >= MIN_PRACTICAL_REGISTER_SPAN) start..configured.last else configured
    }

    private fun densityPermits(request: StringsGenerationRequest, voices: Int): Boolean =
        request.densityBudget?.remaining?.let { it >= voices } ?: true

    private fun selectedChords(chords: List<MidiChord>, density: Double): List<MidiChord> {
        if (chords.isEmpty()) return emptyList()
        val count = ceil(chords.size * density).toInt().coerceIn(1, chords.size)
        return (0 until count).map { chords[it * chords.size / count] }
    }

    private fun harmonyFor(request: StringsGenerationRequest, chord: MidiChord, diagnostics: MutableList<String>): Harmony? {
        if (chord.confidence >= CHORD_CONFIDENCE) {
            parseChord(chord.symbol)?.let { return it }
            diagnostics += "Unsupported or unknown confident chord '${chord.symbol ?: "unknown"}' in section ${request.sectionIndex + 1} at tick ${chord.startTick}; left silent."
            return null
        }
        val evidence = request.key?.takeIf { it.confidence >= KEY_CONFIDENCE } ?: run {
            diagnostics += "No confident harmony for section ${request.sectionIndex + 1} at tick ${chord.startTick}; left silent."
            return null
        }
        val key = evidence.toMusicalKeyOrNull()
        val mode = key?.modeId?.executable
        val tonic = key?.tonic?.chromatic
        if (tonic == null || mode == null) {
            diagnostics += "No confident harmony for section ${request.sectionIndex + 1} at tick ${chord.startTick}; left silent."
            return null
        }
        diagnostics += "Used ${key.displayName} tonic fallback for weak chord in section ${request.sectionIndex + 1} at tick ${chord.startTick}."
        return Harmony(tonic, if (mode == app.melotrail.music.ExecutableScaleMode.MAJOR) intArrayOf(0, 4, 7) else intArrayOf(0, 3, 7))
    }

    private fun parseChord(symbol: String?): Harmony? {
        val match = CHORD_SYMBOL.matchEntire(symbol?.trim().orEmpty()) ?: return null
        val root = pitchClass(match.groupValues[1]) ?: return null
        val intervals = when (match.groupValues[2].lowercase()) {
            "" -> intArrayOf(0, 4, 7); "m", "min" -> intArrayOf(0, 3, 7); "7" -> intArrayOf(0, 4, 7, 10)
            "maj7" -> intArrayOf(0, 4, 7, 11); "m7", "min7" -> intArrayOf(0, 3, 7, 10)
            "maj9" -> intArrayOf(0, 4, 7, 11, 14); "m9", "min9" -> intArrayOf(0, 3, 7, 10, 14)
            "add9" -> intArrayOf(0, 4, 7, 14)
            "sus2" -> intArrayOf(0, 2, 7); "sus4", "sus" -> intArrayOf(0, 5, 7)
            else -> return null
        }
        return Harmony(root, intervals)
    }

    private fun selectVoicing(
        harmony: Harmony,
        role: StringsMidiRole,
        energy: Double,
        range: IntRange,
        previous: List<Int>?,
        request: StringsGenerationRequest,
        chord: MidiChord
    ): SelectedVoicing? {
        val intervals = preferredIntervals(harmony, role, energy)
        val intervalOptions = buildList {
            add(intervals.toList())
            if (intervals.size > 2) {
                add(listOf(intervals.first(), intervals.firstOrNull { it % 12 == 7 } ?: intervals.last()))
            }
            intervals.forEach { add(listOf(it)) }
        }.distinct()
        val candidates = intervalOptions.flatMap { option -> voicingCandidates(harmony, option, range) }.distinct()
            .filter { densityPermits(request, it.size) }
        val withWindows = candidates.mapNotNull { pitches -> collisionFreeWindow(request, chord, pitches)?.let { window -> SelectedVoicing(pitches, window.first, window.second) } }
        val chordStart = request.sectionStartTick + chord.startTick
        val chordEnd = request.sectionStartTick + chord.endTick
        val fullWindow = withWindows.filter { it.startTick == chordStart && it.endTick == chordEnd }
        val minimumUsefulDuration = minOf(request.ppq.toLong(), chordEnd - chordStart)
        val usefulWindow = withWindows.filter { it.endTick - it.startTick >= minimumUsefulDuration }
        val eligible = when {
            fullWindow.isNotEmpty() -> fullWindow
            usefulWindow.isNotEmpty() -> usefulWindow
            else -> withWindows
        }
        return eligible.minWithOrNull(compareByDescending<SelectedVoicing> { it.pitches.size }
            .thenByDescending { it.endTick - it.startTick }
            .thenBy { candidate -> SustainedVoicingContinuity.selectionScore(previous, candidate.pitches, range) }
            .thenBy { candidate -> candidate.pitches.sumOf { pitch -> abs(pitch - registerCenter(range)) } }
            .thenBy { it.pitches.joinToString(",") })
    }

    private fun preferredIntervals(harmony: Harmony, role: StringsMidiRole, energy: Double): IntArray =
        when (role) {
            StringsMidiRole.LONG_NOTES -> intArrayOf(harmony.intervals.first(), harmony.intervals.firstOrNull { it % 12 == 7 } ?: harmony.intervals.last())
            StringsMidiRole.SUSTAINED_HARMONY -> if (energy < 0.45) harmony.intervals.take(3).toIntArray() else harmony.intervals
            StringsMidiRole.CLIMAX_REINFORCEMENT -> harmony.intervals
            StringsMidiRole.SIMPLE_COUNTERMELODY -> error("Countermelody does not use sustained voicings")
        }

    private fun preferredVoiceCount(harmony: Harmony, role: StringsMidiRole, energy: Double): Int =
        preferredIntervals(harmony, role, energy).size

    private fun voicingCandidates(harmony: Harmony, intervals: List<Int>, range: IntRange): List<List<Int>> {
        val tones = intervals.map { (harmony.root + it) % 12 }
        return buildList {
            tones.indices.forEach { inversion ->
                val ordered = tones.drop(inversion) + tones.take(inversion)
                (range.first..range.last).filter { it % 12 == ordered.first() }.forEach { first ->
                    val voicing = mutableListOf(first)
                    ordered.drop(1).forEach { pitchClass -> voicing += nextAtOrAbove(voicing.last() + 1, pitchClass) }
                    if (voicing.all { it in range }) add(voicing)
                }
            }
        }
    }

    private fun collisionFreeWindow(request: StringsGenerationRequest, chord: MidiChord, pitches: List<Int>): Pair<Long, Long>? {
        val start = request.sectionStartTick + chord.startTick
        val end = request.sectionStartTick + chord.endTick
        val piano = request.arrangementState?.requireTrack(ArrangementState.PIANO)?.notes.orEmpty()
            .filter { it.pitch in pitches && it.startTick < end && start < it.endTick }
        val boundaries = (listOf(start, end) + piano.flatMap { listOf(maxOf(start, it.startTick), minOf(end, it.endTick)) })
            .distinct().sorted()
        return boundaries.zipWithNext()
            .filter { (windowStart, windowEnd) -> windowEnd > windowStart && pitches.none { pitch -> request.arrangementState?.melodyCollides(windowStart, windowEnd, pitch, 0) == true } }
            .maxWithOrNull(compareBy<Pair<Long, Long>> { it.second - it.first }.thenByDescending { it.first })
    }

    private fun nextAtOrAbove(minimum: Int, pitchClass: Int): Int = minimum + ((pitchClass - minimum) % 12 + 12) % 12
    private fun registerCenter(range: IntRange): Int = (range.first + range.last) / 2
    private fun releaseGapTicks(ppq: Int): Long = maxOf(1, ppq / RELEASE_GAP_DIVISOR).toLong()
    private fun velocity(energy: Double, role: StringsMidiRole): Int = (MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * energy + if (role == StringsMidiRole.CLIMAX_REINFORCEMENT) CLIMAX_VELOCITY_BOOST else 0).roundToInt().coerceIn(MIN_VELOCITY, MAX_VELOCITY)
    private fun pitchClass(value: String): Int? {
        val base = when (value.firstOrNull()?.uppercaseChar()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }
        return when (value.getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base }
    }

    private fun validate(notes: List<StringsMidiNote>, request: StringsGenerationRequest, range: IntRange) {
        val lastEnd = mutableMapOf<Int, Long>()
        notes.sortedWith(compareBy<StringsMidiNote> { it.startTick }.thenBy { it.pitch }).forEach { note ->
            require(note.pitch in range && note.velocity in 1..127 && note.endTick > note.startTick) { "Generated strings note is invalid" }
            require(note.startTick >= request.sectionStartTick && note.endTick < request.sectionStartTick + request.sectionLengthTicks) { "Generated strings note escapes its section" }
            require(note.startTick >= (lastEnd[note.pitch] ?: Long.MIN_VALUE)) { "Generated strings have a same-pitch overlap" }
            require(request.arrangementState?.melodyCollides(note.startTick, note.endTick, note.pitch, 0) != true) {
                "Generated strings note collides with accepted source melody"
            }
            lastEnd[note.pitch] = note.endTick
        }
    }

    private data class Harmony(val root: Int, val intervals: IntArray)
    private data class SelectedVoicing(val pitches: List<Int>, val startTick: Long, val endTick: Long)

    private companion object {
        const val CHORD_CONFIDENCE = 0.75; const val KEY_CONFIDENCE = 0.70; const val COUNTER_KEY_CONFIDENCE = 0.85; const val COUNTER_CHORD_CONFIDENCE = 0.85
        const val COUNTER_SOURCE_MAX_PITCH = 72; const val COUNTER_SOURCE_MAX_RANGE = 18; const val COUNTER_SOURCE_MAX_DENSITY = 0.35; const val COUNTER_SOURCE_MAX_RHYTHMIC_DENSITY = 0.50; const val COUNTER_MAX_STEP = 5
        const val SOURCE_CLEARANCE_SEMITONES = 2; const val MIN_VELOCITY = 42; const val MAX_VELOCITY = 82; const val CLIMAX_VELOCITY_BOOST = 6; const val RELEASE_GAP_DIVISOR = 24
        const val MIN_PRACTICAL_REGISTER_SPAN = 7
        val CHORD_SYMBOL = Regex("^([A-G](?:#|b)?)(|m|min|7|maj7|m7|min7|maj9|m9|min9|add9|sus2|sus4|sus)$", RegexOption.IGNORE_CASE)
    }
}

data class GeneratedStringsMidi(val path: Path, val ppq: Int, val notes: List<StringsMidiNote>, val diagnostics: List<String>)

/** Converts approved v3 strings controls into one atomic, inspectable MIDI artifact. */
class StringsMidiGenerationAdapter(
    private val composer: DeterministicStringsMidiGenerator = DeterministicStringsMidiGenerator(),
    private val libraryRoot: Path
) {
    fun generate(projectRoot: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>, arrangementState: ArrangementState? = null, output: Path? = null): GeneratedStringsMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val strings = InstrumentRegistryLoader(libraryRoot).load().resolveApprovedRole(project, LogicalInstrument.STRINGS)
        val requests = mutableListOf<StringsGenerationRequest>()
        val timeline = mutableListOf<TimelineSegment>()
        var start = 0L
        var ppq: Int? = null
        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            require(analysis.ppq > 0 && analysis.durationTicks > 0) { "MIDI analysis for '${section.partId}' has invalid timing" }
            if (ppq == null) ppq = analysis.ppq else require(ppq == analysis.ppq) { "All arranged MIDI parts must use the same PPQ" }
            timeline += TimelineSegment(start, analysis.tempoMap, analysis.timeSignatures)
            val plans = section.instruments.filterIsInstance<StringsInstrumentPlan>()
            require(plans.size <= 1) { "Detailed arrangement section ${section.index + 1} contains duplicate strings plans" }
            plans.singleOrNull()?.let { plan ->
                require(plan.name == LogicalInstrument.STRINGS.wireName && plan.mode == InstrumentMode.GENERATED) { "Detailed arrangement section ${section.index + 1} has an invalid strings plan" }
                requests += StringsGenerationRequest(position, section.role, start, analysis.ppq, analysis.tempoMap, analysis.timeSignatures, analysis.durationTicks, analysis.key, analysis.chords, analysis.pitchRange, analysis.melodicRange, analysis.noteDensity, analysis.rhythmicDensity, section.energy, plan.density, plan.role.toMidiRole(), plan.register.toStringsRegister(), strings.midiChannelZeroBased ?: 0, strings.midiProgram, arrangementState, arrangementState?.densityBudget(start, start + analysis.durationTicks))
            }
            start = Math.addExact(start, analysis.durationTicks)
        }
        require(requests.isNotEmpty()) { "Detailed arrangement does not contain a generated strings instrument" }
        var previousVoicing: List<Int> = emptyList()
        val results = requests.map { request ->
            val contextual = request.copy(previousAcceptedVoicing = previousVoicing)
            val result = composer.generate(contextual)
            previousVoicing = result.notes.groupBy(StringsMidiNote::startTick).toSortedMap().values.lastOrNull()
                ?.map(StringsMidiNote::pitch)?.sorted() ?: previousVoicing
            contextual to result
        }
        val target = output ?: root.resolve("midi/generated/strings.mid")
        writeMidi(target, checkNotNull(ppq), start, strings.midiChannelZeroBased ?: 0, strings.midiProgram, timeline, results)
        return GeneratedStringsMidi(target, checkNotNull(ppq), results.flatMap { it.second.notes }, results.flatMap { it.second.diagnostics })
    }

    private fun StringsRole.toMidiRole(): StringsMidiRole = when (this) {
        StringsRole.SUSTAINED_HARMONY, StringsRole.LEGACY_TEXTURE -> StringsMidiRole.SUSTAINED_HARMONY
        StringsRole.CLIMAX_REINFORCEMENT -> StringsMidiRole.CLIMAX_REINFORCEMENT
        StringsRole.LONG_NOTES, StringsRole.LEGACY_SUSTAINED -> StringsMidiRole.LONG_NOTES
        StringsRole.SIMPLE_COUNTERMELODY -> StringsMidiRole.SIMPLE_COUNTERMELODY
    }
    private fun MusicalRegister.toStringsRegister(): String = when (this) { MusicalRegister.LOW -> "low"; MusicalRegister.MID -> "mid"; MusicalRegister.HIGH -> "high" }

    private fun writeMidi(output: Path, ppq: Int, endTick: Long, channel: Int, program: Int?, timeline: List<TimelineSegment>, results: List<Pair<StringsGenerationRequest, StringsGenerationResult>>) {
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val sequence = Sequence(Sequence.PPQ, ppq); val meta = sequence.createTrack(); val notes = sequence.createTrack()
            timeline.forEach { segment -> segment.tempoMap.forEach { meta.add(MidiEvent(tempoMessage(it.bpm), segment.startTick + it.tick)) }; segment.timeSignatures.forEach { meta.add(MidiEvent(signatureMessage(it), segment.startTick + it.tick)) } }
            program?.let { notes.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, it, 0), 0)) }
            val expected = results.flatMap { it.second.notes }.sortedWith(compareBy<StringsMidiNote> { it.startTick }.thenBy { it.pitch })
            expected.forEach { note -> notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, note.pitch, note.velocity), note.startTick)); notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, note.pitch, 0), note.endTick)) }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), endTick))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write strings MIDI" }
            validateWrittenMidi(temporary, ppq, endTick, channel, expected)
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) } catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is not supported for generated strings MIDI '$output'", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun validateWrittenMidi(path: Path, ppq: Int, endTick: Long, channel: Int, expected: List<StringsMidiNote>) {
        val sequence = MidiSystem.getSequence(path.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == ppq && sequence.tickLength >= endTick) { "Generated strings MIDI timing did not round-trip" }
        val parsed = mutableListOf<StringsMidiNote>(); val active = mutableMapOf<Int, Pair<Long, Int>>()
        sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.sortedBy { it.tick }.forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            if (message.channel != channel) return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0; val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) require(active.put(message.data1, event.tick to message.data2) == null) { "Generated strings MIDI has overlapping active pitches" }
            else if (off) { val start = requireNotNull(active.remove(message.data1)) { "Generated strings MIDI has a hanging note-off" }; parsed += StringsMidiNote(start.first, event.tick, message.data1, start.second) }
        }
        require(active.isEmpty()) { "Generated strings MIDI has hanging notes" }
        require(parsed.sortedWith(compareBy<StringsMidiNote> { it.startTick }.thenBy { it.pitch }) == expected) { "Generated strings MIDI events did not round-trip" }
    }

    private fun tempoMessage(bpm: Double): MetaMessage { val micros = (60_000_000.0 / bpm).roundToInt(); return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }
    private fun signatureMessage(signature: MidiTimeSignature): MetaMessage = MetaMessage(0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4)
    private data class TimelineSegment(val startTick: Long, val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
}
