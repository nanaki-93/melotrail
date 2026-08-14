package ai.music.workstation.arrangement

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

/** The only first-pass pad composition technique: sustained analyzed harmony. */
enum class PadRole { SUSTAINED_CHORDS }

/** One sustained pad note in the single, project-wide MIDI timeline. */
data class PadMidiNote(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

/**
 * Validated local input for one detailed-arrangement section. Chord ticks are
 * section-relative; [sectionStartTick] anchors generated notes in the full song.
 * The release gap is one twenty-fourth of a quarter note (at least one tick),
 * shortened only for very short analyzed chord segments.
 */
data class PadGenerationRequest(
    val sectionIndex: Int,
    val sectionStartTick: Long,
    val ppq: Int,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val sectionLengthTicks: Long,
    val key: MidiKey?,
    val chords: List<MidiChord>,
    val energy: Double,
    val density: Double,
    val role: PadRole = PadRole.SUSTAINED_CHORDS,
    val register: String,
    val midiChannel: Int = 0,
    val midiProgram: Int? = null
) {
    fun requireValid() {
        require(sectionIndex >= 0 && sectionStartTick >= 0) { "Pad section index and start tick must not be negative" }
        require(ppq in 24..9_600) { "Pad PPQ must be from 24 to 9600" }
        require(sectionLengthTicks > 0) { "Pad section length must be positive" }
        require(energy.isFinite() && energy in 0.0..1.0) { "Pad energy must be from 0.0 to 1.0" }
        require(density.isFinite() && density in 0.0..1.0) { "Pad density must be from 0.0 to 1.0" }
        require(role == PadRole.SUSTAINED_CHORDS) { "Only sustained_chords pad generation is supported" }
        require(register in REGISTER_RANGES) { "Unsupported pad register '$register'. Allowed registers: ${REGISTER_RANGES.keys.joinToString()}" }
        require(midiChannel in 0..15) { "Pad MIDI channel must be 0..15" }
        require(midiProgram == null || midiProgram in 0..127) { "Pad MIDI program must be 0..127" }
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L) { "Pad tempo map must start at tick 0" }
        require(timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "Pad time-signature map must start at tick 0" }
        tempoMap.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Pad tempo changes must be ordered" } }
        timeSignatures.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Pad time-signature changes must be ordered" } }
        tempoMap.forEach { require(it.tick in 0..sectionLengthTicks && it.bpm.isFinite() && it.bpm > 0.0) { "Pad tempo map contains an invalid change" } }
        timeSignatures.forEach { signature ->
            require(signature.tick in 0..sectionLengthTicks && signature.numerator > 0 && signature.denominator in setOf(1, 2, 4, 8, 16, 32)) {
                "Pad time-signature map contains an invalid change"
            }
            require((ppq * 4) % signature.denominator == 0) { "Pad PPQ cannot represent ${signature.numerator}/${signature.denominator}" }
        }
        chords.zipWithNext().forEach { (first, second) -> require(first.endTick <= second.startTick) { "Pad chords must not overlap" } }
        chords.forEach {
            require(it.startTick >= 0 && it.endTick > it.startTick && it.endTick <= sectionLengthTicks && it.confidence.isFinite() && it.confidence in 0.0..1.0) {
                "Pad chord segment is invalid"
            }
        }
    }

    companion object {
        /** Mid is C3--B4; mid_high is C4--B5, safely inside the pad SFZ coverage. */
        val REGISTER_RANGES = mapOf("mid" to (48..71), "mid_high" to (60..83))
    }
}

data class PadGenerationResult(val notes: List<PadMidiNote>, val diagnostics: List<String>)

/**
 * Deterministic sustained harmony. Supported symbols are major/minor triads,
 * dominant/major/minor sevenths, and sus2/sus4 chords, with an optional sharp
 * or flat root. Chords need >= 0.75 confidence. A weak chord may use only the
 * analyzed key tonic at >= 0.70 confidence; unsupported confident symbols and
 * missing chord segments remain silent rather than being guessed.
 */
class DeterministicPadMidiGenerator {
    fun generate(request: PadGenerationRequest): PadGenerationResult {
        request.requireValid()
        if (request.density == 0.0) return PadGenerationResult(emptyList(), listOf("Pad density is 0.0; wrote silence."))

        val notes = mutableListOf<PadMidiNote>()
        val diagnostics = mutableListOf<String>()
        var previousVoicing: List<Int>? = null
        selectedChords(request.chords, request.density).forEach { chord ->
            val harmony = harmonyFor(request, chord, diagnostics) ?: return@forEach
            val voicing = selectVoicing(harmony, request.energy, request.register, previousVoicing)
            val gap = minOf(releaseGapTicks(request.ppq), chord.endTick - chord.startTick - 1)
            val end = chord.endTick - gap
            notes += voicing.map { pitch ->
                PadMidiNote(request.sectionStartTick + chord.startTick, request.sectionStartTick + end, pitch, velocity(request.energy))
            }
            previousVoicing = voicing
        }
        validateNotes(notes, request)
        if (notes.isEmpty() && diagnostics.isEmpty()) diagnostics += "No pad chord segments were selected."
        return PadGenerationResult(notes.sortedWith(compareBy<PadMidiNote> { it.startTick }.thenBy { it.pitch }), diagnostics.distinct())
    }

    private fun selectedChords(chords: List<MidiChord>, density: Double): List<MidiChord> {
        if (chords.isEmpty()) return emptyList()
        val count = ceil(chords.size * density).toInt().coerceIn(1, chords.size)
        return (0 until count).map { index -> chords[index * chords.size / count] }
    }

    private fun harmonyFor(request: PadGenerationRequest, chord: MidiChord, diagnostics: MutableList<String>): ChordHarmony? {
        if (chord.confidence >= CHORD_CONFIDENCE) {
            parseChord(chord.symbol)?.let { return it }
            diagnostics += "Unsupported or unknown confident chord '${chord.symbol ?: "unknown"}' in section ${request.sectionIndex + 1} at tick ${chord.startTick}; left silent."
            return null
        }
        val key = request.key?.takeIf { it.confidence >= KEY_CONFIDENCE }
        val tonic = key?.tonic?.let(::pitchClass)
        if (tonic == null || key.mode !in setOf("major", "minor")) {
            diagnostics += "No confident harmony for section ${request.sectionIndex + 1} at tick ${chord.startTick}; left silent."
            return null
        }
        diagnostics += "Used ${key.tonic} ${key.mode} tonic fallback for weak chord in section ${request.sectionIndex + 1} at tick ${chord.startTick}."
        return ChordHarmony(tonic, if (key.mode == "major") intArrayOf(0, 4, 7) else intArrayOf(0, 3, 7))
    }

    private fun parseChord(symbol: String?): ChordHarmony? {
        val value = symbol?.trim().orEmpty()
        val match = CHORD_SYMBOL.matchEntire(value) ?: return null
        val root = pitchClass(match.groupValues[1]) ?: return null
        val quality = match.groupValues[2].lowercase()
        val tones = when (quality) {
            "" -> intArrayOf(0, 4, 7)
            "m", "min" -> intArrayOf(0, 3, 7)
            "7" -> intArrayOf(0, 4, 7, 10)
            "maj7" -> intArrayOf(0, 4, 7, 11)
            "m7", "min7" -> intArrayOf(0, 3, 7, 10)
            "sus2" -> intArrayOf(0, 2, 7)
            "sus4", "sus" -> intArrayOf(0, 5, 7)
            else -> return null
        }
        return ChordHarmony(root, tones)
    }

    private fun selectVoicing(harmony: ChordHarmony, energy: Double, register: String, previous: List<Int>?): List<Int> {
        val tones = when {
            energy < REDUCED_VOICING_ENERGY -> intArrayOf(harmony.intervals.first(), harmony.intervals.first { it % 12 == 7 })
            energy < SEVENTH_VOICING_ENERGY -> harmony.intervals.take(3).toIntArray()
            else -> harmony.intervals
        }.map { (harmony.root + it) % 12 }
        val candidates = candidates(tones, requireNotNull(PadGenerationRequest.REGISTER_RANGES[register]))
        require(candidates.isNotEmpty()) { "Pad chord cannot be voiced in $register register" }
        return candidates.minWith(compareBy<List<Int>> {
            previous?.zip(it)?.sumOf { (before, after) -> abs(after - before) } ?: 0
        }.thenBy { voicing -> voicing.sumOf { abs(it - registerCenter(register)) } }.thenBy { it.joinToString(",") })
    }

    private fun candidates(pitchClasses: List<Int>, range: IntRange): List<List<Int>> = buildList {
        pitchClasses.indices.forEach { inversion ->
            val ordered = pitchClasses.drop(inversion) + pitchClasses.take(inversion)
            (range.first..range.last).filter { it % 12 == ordered.first() }.forEach { first ->
                val voicing = mutableListOf(first)
                ordered.drop(1).forEach { pitchClass -> voicing += nextAtOrAbove(voicing.last() + 1, pitchClass) }
                if (voicing.all { it in range }) add(voicing)
            }
        }
    }

    private fun nextAtOrAbove(minimum: Int, pitchClass: Int): Int = minimum + ((pitchClass - minimum) % 12 + 12) % 12

    private fun registerCenter(register: String): Int {
        val range = requireNotNull(PadGenerationRequest.REGISTER_RANGES[register])
        return (range.first + range.last) / 2
    }

    private fun releaseGapTicks(ppq: Int): Long = maxOf(1, ppq / RELEASE_GAP_DIVISOR).toLong()

    private fun velocity(energy: Double): Int =
        (MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * energy).roundToInt().coerceIn(MIN_VELOCITY, MAX_VELOCITY)

    private fun pitchClass(value: String): Int? {
        val base = when (value.firstOrNull()?.uppercaseChar()) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null
        }
        return when (value.getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base }
    }

    private fun validateNotes(notes: List<PadMidiNote>, request: PadGenerationRequest) {
        val range = requireNotNull(PadGenerationRequest.REGISTER_RANGES[request.register])
        val lastEndByPitch = mutableMapOf<Int, Long>()
        notes.sortedWith(compareBy<PadMidiNote> { it.startTick }.thenBy { it.pitch }).forEach { note ->
            require(note.pitch in range) { "Generated pad pitch is outside ${request.register} register" }
            require(note.velocity in 1..127 && note.endTick > note.startTick) { "Generated pad note has invalid MIDI values" }
            require(note.startTick >= request.sectionStartTick && note.endTick < request.sectionStartTick + request.sectionLengthTicks) { "Generated pad note escapes its section" }
            require(note.startTick >= (lastEndByPitch[note.pitch] ?: Long.MIN_VALUE)) { "Generated pad has a same-pitch overlap" }
            lastEndByPitch[note.pitch] = note.endTick
        }
    }

    private data class ChordHarmony(val root: Int, val intervals: IntArray)

    private companion object {
        const val CHORD_CONFIDENCE = 0.75
        const val KEY_CONFIDENCE = 0.70
        const val REDUCED_VOICING_ENERGY = 0.35
        const val SEVENTH_VOICING_ENERGY = 0.75
        const val MIN_VELOCITY = 34
        const val MAX_VELOCITY = 76
        const val RELEASE_GAP_DIVISOR = 24
        val CHORD_SYMBOL = Regex("^([A-G](?:#|b)?)(|m|min|7|maj7|m7|min7|sus2|sus4|sus)$", RegexOption.IGNORE_CASE)
    }
}

data class GeneratedPadMidi(val path: Path, val ppq: Int, val notes: List<PadMidiNote>, val diagnostics: List<String>)

/** Converts approved v3 pad controls into a full-timeline, registry-mapped MIDI artifact. */
class PadMidiGenerationAdapter(
    private val composer: DeterministicPadMidiGenerator = DeterministicPadMidiGenerator(),
    private val libraryRoot: Path
) {
    fun generate(projectRoot: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): GeneratedPadMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val pad = InstrumentRegistryLoader(libraryRoot).load().resolve(LogicalInstrument.PAD.wireName)
        val requests = mutableListOf<PadGenerationRequest>()
        val timeline = mutableListOf<TimelineSegment>()
        var start = 0L
        var ppq: Int? = null
        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            require(analysis.ppq > 0 && analysis.durationTicks > 0) { "MIDI analysis for '${section.partId}' has invalid timing" }
            if (ppq == null) ppq = analysis.ppq else require(ppq == analysis.ppq) { "All arranged MIDI parts must use the same PPQ" }
            timeline += TimelineSegment(start, analysis.tempoMap, analysis.timeSignatures)
            val plans = section.instruments.filterIsInstance<PadInstrumentPlan>()
            require(plans.size <= 1) { "Detailed arrangement section ${section.index + 1} contains duplicate pad plans" }
            plans.singleOrNull()?.let { plan ->
                require(plan.name == LogicalInstrument.PAD.wireName && plan.mode == InstrumentMode.GENERATED) {
                    "Detailed arrangement section ${section.index + 1} has an invalid pad plan"
                }
                requests += PadGenerationRequest(
                    position, start, analysis.ppq, analysis.tempoMap, analysis.timeSignatures, analysis.durationTicks,
                    analysis.key, analysis.chords, section.energy, plan.density, PadRole.SUSTAINED_CHORDS,
                    plan.register.toPadRegister(), pad.midiChannelZeroBased ?: 0, pad.midiProgram
                )
            }
            start = Math.addExact(start, analysis.durationTicks)
        }
        require(requests.isNotEmpty()) { "Detailed arrangement does not contain a generated pad instrument" }
        val results = requests.map { it to composer.generate(it) }
        val output = root.resolve("midi/generated/pad.mid")
        writeMidi(output, checkNotNull(ppq), start, pad.midiChannelZeroBased ?: 0, pad.midiProgram, timeline, results)
        return GeneratedPadMidi(output, checkNotNull(ppq), results.flatMap { it.second.notes }, results.flatMap { it.second.diagnostics })
    }

    private fun MusicalRegister.toPadRegister(): String = when (this) {
        // The v3 schema's LOW is a broad arrangement hint. Pads deliberately
        // do not enter the low register, where they would compete with bass.
        MusicalRegister.LOW, MusicalRegister.MID -> "mid"
        MusicalRegister.HIGH -> "mid_high"
    }

    private fun writeMidi(
        output: Path,
        ppq: Int,
        endTick: Long,
        channel: Int,
        program: Int?,
        timeline: List<TimelineSegment>,
        results: List<Pair<PadGenerationRequest, PadGenerationResult>>
    ) {
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val sequence = Sequence(Sequence.PPQ, ppq)
            val meta = sequence.createTrack()
            val notes = sequence.createTrack()
            timeline.forEach { segment ->
                segment.tempoMap.forEach { meta.add(MidiEvent(tempoMessage(it.bpm), segment.startTick + it.tick)) }
                segment.timeSignatures.forEach { meta.add(MidiEvent(signatureMessage(it), segment.startTick + it.tick)) }
            }
            program?.let { notes.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, it, 0), 0)) }
            val expected = results.flatMap { it.second.notes }.sortedWith(compareBy<PadMidiNote> { it.startTick }.thenBy { it.pitch })
            expected.forEach { note ->
                notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, note.pitch, note.velocity), note.startTick))
                notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, note.pitch, 0), note.endTick))
            }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), endTick))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write pad MIDI" }
            validateWrittenMidi(temporary, ppq, endTick, channel, expected)
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException("Atomic publish is not supported for generated pad MIDI '$output'", error)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateWrittenMidi(path: Path, ppq: Int, endTick: Long, channel: Int, expected: List<PadMidiNote>) {
        val sequence = MidiSystem.getSequence(path.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == ppq && sequence.tickLength >= endTick) { "Generated pad MIDI timing did not round-trip" }
        val parsed = mutableListOf<PadMidiNote>()
        val active = mutableMapOf<Int, Pair<Long, Int>>()
        sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.sortedBy { it.tick }.forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            if (message.channel != channel) return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) {
                require(active.put(message.data1, event.tick to message.data2) == null) { "Generated pad MIDI has overlapping active pitches" }
            } else if (off) {
                val start = requireNotNull(active.remove(message.data1)) { "Generated pad MIDI has a hanging note-off" }
                parsed += PadMidiNote(start.first, event.tick, message.data1, start.second)
            }
        }
        require(active.isEmpty()) { "Generated pad MIDI has hanging notes" }
        require(parsed.sortedWith(compareBy<PadMidiNote> { it.startTick }.thenBy { it.pitch }) == expected) { "Generated pad MIDI events did not round-trip" }
    }

    private fun tempoMessage(bpm: Double): MetaMessage {
        val micros = (60_000_000.0 / bpm).roundToInt()
        return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }

    private fun signatureMessage(signature: MidiTimeSignature): MetaMessage = MetaMessage(
        0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4
    )

    private data class TimelineSegment(val startTick: Long, val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
}
