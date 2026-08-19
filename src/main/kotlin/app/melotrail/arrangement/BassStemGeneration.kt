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
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The only deterministic bass roles accepted at the composition boundary. */
enum class BassRole(val wireName: String) {
    ROOT("root"), ROOT_FIFTH("root_fifth"), OCTAVE("octave"), SUSTAINED("sustained"), SIMPLE_WALKING("simple_walking");

    companion object {
        fun parse(value: String?): BassRole = entries.firstOrNull { it.wireName == value?.lowercase() }
            ?: throw IllegalArgumentException("Unsupported bass role '${value ?: "(missing)"}'. Allowed roles: ${entries.joinToString { it.wireName }}")
    }
}

enum class BassMovement { STATIC, RISING, FALLING, BALANCED }

/** One generated event, expressed in the single, project-wide MIDI timeline. */
data class BassMidiNote(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

/**
 * Validated local input for one arranged section. Chord and meter ticks are
 * section-relative; [sectionStartTick] anchors the output in the full song.
 * Syncopation is a fraction of one beat in the conservative 0.0..0.25 range.
 */
data class BassGenerationRequest(
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
    val role: BassRole,
    val movement: BassMovement = BassMovement.BALANCED,
    val register: String = "low",
    val syncopation: Double = 0.0,
    val midiChannel: Int = 0,
    val midiProgram: Int? = null
) {
    fun requireValid() {
        require(sectionIndex >= 0) { "Bass section index must not be negative" }
        require(sectionStartTick >= 0) { "Bass section start tick must not be negative" }
        require(ppq in 24..9_600) { "Bass PPQ must be from 24 to 9600" }
        require(sectionLengthTicks > 0) { "Bass section length must be positive" }
        require(energy.isFinite() && energy in 0.0..1.0) { "Bass energy must be from 0.0 to 1.0" }
        require(density.isFinite() && density in 0.0..1.0) { "Bass density must be from 0.0 to 1.0" }
        require(register == "low") { "Only the low bass register is supported" }
        require(syncopation.isFinite() && syncopation in 0.0..MAX_SYNCOPATION) {
            "Bass syncopation must be from 0.0 to $MAX_SYNCOPATION beats"
        }
        require(midiChannel in 0..15) { "Bass MIDI channel must be 0..15" }
        require(midiProgram == null || midiProgram in 0..127) { "Bass MIDI program must be 0..127" }
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L) { "Bass tempo map must start at tick 0" }
        require(timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "Bass time-signature map must start at tick 0" }
        tempoMap.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Bass tempo changes must be ordered" } }
        timeSignatures.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Bass time-signature changes must be ordered" } }
        tempoMap.forEach { require(it.tick in 0..sectionLengthTicks && it.bpm.isFinite() && it.bpm > 0.0) { "Bass tempo map contains an invalid change" } }
        timeSignatures.forEach {
            require(it.tick in 0..sectionLengthTicks && it.numerator > 0 && it.denominator in setOf(1, 2, 4, 8, 16, 32)) {
                "Bass time-signature map contains an invalid change"
            }
            require((ppq * 4) % it.denominator == 0) { "Bass PPQ cannot represent ${it.numerator}/${it.denominator}" }
        }
        chords.zipWithNext().forEach { (first, second) -> require(first.endTick <= second.startTick) { "Bass chords must not overlap" } }
        chords.forEach { require(it.startTick >= 0 && it.endTick > it.startTick && it.endTick <= sectionLengthTicks && it.confidence.isFinite() && it.confidence in 0.0..1.0) { "Bass chord segment is invalid" } }
    }

    private companion object { const val MAX_SYNCOPATION = 0.25 }
}

data class BassGenerationResult(val notes: List<BassMidiNote>, val diagnostics: List<String>)

/**
 * Deterministic composition only. Chord symbols are used at >= 0.75 confidence;
 * a key tonic is used at >= 0.55 confidence when a chord is weak. Otherwise that
 * interval is intentionally silent. Pitches stay in E1..C3 (MIDI 28..48).
 */
class DeterministicBassMidiGenerator {
    fun generate(request: BassGenerationRequest): BassGenerationResult {
        request.requireValid()
        if (request.density == 0.0) return BassGenerationResult(emptyList(), listOf("Bass density is 0.0; wrote silence."))

        val notes = mutableListOf<BassMidiNote>()
        val diagnostics = mutableListOf<String>()
        intervals(request).forEach { interval ->
            val root = harmonyRoot(request, interval.start)
            if (root == null) {
                diagnostics += "No confident harmony for section ${request.sectionIndex + 1} at tick ${interval.start}; left silent."
            } else {
                notes += pattern(request, interval, root, nextRoot(request, interval.end))
            }
        }
        validateNotes(notes, request)
        if (notes.isEmpty() && diagnostics.isEmpty()) diagnostics += "No bass events were selected."
        return BassGenerationResult(notes.sortedBy { it.startTick }, diagnostics.distinct())
    }

    private fun intervals(request: BassGenerationRequest): List<TickInterval> {
        val boundaries = sortedSetOf(0L, request.sectionLengthTicks)
        request.chords.forEach { boundaries += it.startTick; boundaries += it.endTick }
        request.timeSignatures.forEach { signature ->
            var tick = signature.tick
            val next = request.timeSignatures.firstOrNull { it.tick > signature.tick }?.tick ?: request.sectionLengthTicks
            val bar = ticksPerBeat(request, signature) * signature.numerator
            while (tick < minOf(next, request.sectionLengthTicks)) { boundaries += tick; tick += bar }
        }
        return boundaries.toList().zipWithNext().map { TickInterval(it.first, it.second) }.filter { it.end > it.start }
    }

    private fun pattern(request: BassGenerationRequest, interval: TickInterval, root: Int, followingRoot: Int?): List<BassMidiNote> {
        val signature = request.timeSignatures.last { it.tick <= interval.start }
        val beat = ticksPerBeat(request, signature)
        val beats = ((interval.end - interval.start) / beat).toInt().coerceAtLeast(1)
        val slots = when (request.role) {
            BassRole.SUSTAINED -> listOf(BassSlot(0, 0, true))
            BassRole.ROOT -> (0 until beats).map { BassSlot(it, 0) }
            BassRole.ROOT_FIFTH -> (0 until beats).map { BassSlot(it, if (it % 2 == 0) 0 else 7) }
            BassRole.OCTAVE -> (0 until beats).map { BassSlot(it, if (it % 2 == 0) 0 else 12) }
            BassRole.SIMPLE_WALKING -> walkingSlots(beats, root, followingRoot)
        }
        val selected = if (request.role == BassRole.SUSTAINED) slots else slots.take(eventsForDensity(slots.size, request.density))
        return selected.mapIndexed { index, slot ->
            val originalStart = interval.start + slot.beat.toLong() * beat
            val nextBeat = minOf(interval.end, originalStart + beat)
            val offset = if (slot.sustained || originalStart == interval.start) 0L else (beat * request.syncopation).roundToInt().toLong()
            val start = originalStart + offset
            val end = if (slot.sustained) interval.end else minOf(nextBeat, start + (beat * NOTE_LENGTH_BEATS).roundToInt())
            BassMidiNote(
                startTick = request.sectionStartTick + start,
                endTick = request.sectionStartTick + end,
                pitch = applyMovement(normalizePitch(36 + root + slot.semitones), request.movement, index, selected.size),
                velocity = velocity(request.energy, slot.semitones >= 12)
            )
        }
    }

    private fun walkingSlots(beats: Int, root: Int, followingRoot: Int?): List<BassSlot> = (0 until beats).map { beat ->
        when (beat) {
            0 -> BassSlot(beat, 0)
            beats - 1 -> BassSlot(beat, followingRoot?.let { signedStep(root, it) } ?: 7)
            else -> BassSlot(beat, if (followingRoot == null) 7 else signedStep(root, followingRoot) * beat / beats)
        }
    }

    private fun signedStep(from: Int, to: Int): Int {
        val delta = ((to - from + 18) % 12) - 6
        return delta.coerceIn(-5, 6)
    }

    private fun eventsForDensity(slotCount: Int, density: Double): Int = ceil(slotCount * density).toInt().coerceIn(1, slotCount)

    private fun harmonyRoot(request: BassGenerationRequest, tick: Long): Int? {
        val chord = request.chords.firstOrNull { tick >= it.startTick && tick < it.endTick }
        chord?.takeIf { it.confidence >= CHORD_CONFIDENCE }?.symbol?.let(::pitchClass)?.let { return it }
        return request.key?.takeIf { it.confidence >= KEY_CONFIDENCE }?.toMusicalKeyOrNull()?.tonic?.chromatic
    }

    private fun nextRoot(request: BassGenerationRequest, tick: Long): Int? =
        request.chords.firstOrNull { it.startTick >= tick && it.confidence >= CHORD_CONFIDENCE }?.symbol?.let(::pitchClass)
            ?: request.key?.takeIf { it.confidence >= KEY_CONFIDENCE }?.toMusicalKeyOrNull()?.tonic?.chromatic

    private fun pitchClass(symbol: String): Int? {
        val value = symbol.trim()
        if (value.isEmpty()) return null
        val base = when (value[0].uppercaseChar()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }
        return when (value.getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base }
    }

    private fun normalizePitch(pitch: Int): Int {
        var result = pitch
        while (result < LOWEST_BASS_NOTE) result += 12
        while (result > HIGHEST_BASS_NOTE) result -= 12
        require(result in LOWEST_BASS_NOTE..HIGHEST_BASS_NOTE) { "Generated bass pitch is outside E1..C3" }
        return result
    }

    private fun applyMovement(pitch: Int, movement: BassMovement, index: Int, count: Int): Int = normalizePitch(
        pitch + when (movement) {
            BassMovement.STATIC -> 0
            BassMovement.RISING -> if (index == count - 1 && count > 1) 12 else 0
            BassMovement.FALLING -> if (index == 0 && count > 1) 12 else 0
            BassMovement.BALANCED -> if (index % 2 == 1) 12 else 0
        }
    )

    private fun velocity(energy: Double, octaveEmphasis: Boolean): Int =
        (MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * energy).roundToInt().plus(if (octaveEmphasis) 6 else 0).coerceIn(MIN_VELOCITY, MAX_VELOCITY)

    private fun ticksPerBeat(request: BassGenerationRequest, signature: MidiTimeSignature): Long =
        (request.ppq * 4 / signature.denominator).toLong()

    private fun validateNotes(notes: List<BassMidiNote>, request: BassGenerationRequest) {
        val lastEndByPitch = mutableMapOf<Int, Long>()
        notes.sortedBy { it.startTick }.forEach { note ->
            require(note.pitch in LOWEST_BASS_NOTE..HIGHEST_BASS_NOTE) { "Generated bass pitch is outside E1..C3" }
            require(note.velocity in 1..127) { "Generated bass velocity is invalid" }
            require(note.endTick > note.startTick) { "Generated bass note has non-positive duration" }
            require(note.startTick >= request.sectionStartTick && note.endTick <= request.sectionStartTick + request.sectionLengthTicks) { "Generated bass note escapes its section" }
            require(note.startTick >= (lastEndByPitch[note.pitch] ?: Long.MIN_VALUE)) { "Generated bass has a same-pitch overlap" }
            lastEndByPitch[note.pitch] = note.endTick
        }
    }

    private data class TickInterval(val start: Long, val end: Long)
    private data class BassSlot(val beat: Int, val semitones: Int, val sustained: Boolean = false)

    private companion object {
        const val CHORD_CONFIDENCE = 0.75
        const val KEY_CONFIDENCE = 0.55
        const val LOWEST_BASS_NOTE = 28
        const val HIGHEST_BASS_NOTE = 48
        const val NOTE_LENGTH_BEATS = 0.75
        const val MIN_VELOCITY = 52
        const val MAX_VELOCITY = 100
    }
}

/** Result of composing the one inspectable bass MIDI artifact for a project. */
data class GeneratedBassMidi(val path: Path, val ppq: Int, val notes: List<BassMidiNote>, val diagnostics: List<String>)

/** Kept as the generation boundary; rendering is deliberately a separate Task 007 concern. */
interface InstrumentStemGenerator {
    fun generate(projectRoot: Path, project: Project, arrangement: Arrangement, analyses: Map<String, MidiAnalysis>): GeneratedBassMidi
}

/** Maps validated arrangement choices and MIDI analysis into deterministic requests, never model-supplied notes. */
class BassMidiGenerationAdapter(
    private val composer: DeterministicBassMidiGenerator = DeterministicBassMidiGenerator(),
    private val libraryRoot: Path
) : InstrumentStemGenerator {
    override fun generate(projectRoot: Path, project: Project, arrangement: Arrangement, analyses: Map<String, MidiAnalysis>): GeneratedBassMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        arrangement.requireValid(project.parts.map { it.id })
        val bass = InstrumentRegistryLoader(libraryRoot).load().resolve(LogicalInstrument.BASS.wireName)
        val requests = mutableListOf<BassGenerationRequest>()
        var start = 0L
        var ppq: Int? = null
        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            require(analysis.ppq > 0 && analysis.durationTicks > 0) { "MIDI analysis for '${section.partId}' has invalid timing" }
            if (ppq == null) ppq = analysis.ppq else require(ppq == analysis.ppq) { "All arranged MIDI parts must use the same PPQ" }
            val plan = section.instruments.firstOrNull { it.mode == InstrumentMode.GENERATED && it.name.equals(LogicalInstrument.BASS.wireName, ignoreCase = true) }
            if (plan != null) requests += BassGenerationRequest(
                sectionIndex = position,
                sectionStartTick = start,
                ppq = analysis.ppq,
                tempoMap = analysis.tempoMap,
                timeSignatures = analysis.timeSignatures,
                sectionLengthTicks = analysis.durationTicks,
                key = analysis.key,
                chords = analysis.chords,
                energy = analysis.energy,
                density = requireNotNull(plan.density),
                role = BassRole.parse(plan.role),
                midiChannel = bass.midiChannelZeroBased ?: 0,
                midiProgram = bass.midiProgram
            )
            start = Math.addExact(start, analysis.durationTicks)
        }
        require(requests.isNotEmpty()) { "Arrangement does not contain a generated bass instrument" }
        val result = requests.map { it to composer.generate(it) }
        val output = root.resolve("midi/generated/bass.mid")
        writeMidi(output, checkNotNull(ppq), start, requests, result)
        return GeneratedBassMidi(output, checkNotNull(ppq), result.flatMap { it.second.notes }, result.flatMap { it.second.diagnostics })
    }

    /** Consumes the approved v3 controls while retaining the legacy adapter for older projects. */
    fun generate(projectRoot: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): GeneratedBassMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val bass = InstrumentRegistryLoader(libraryRoot).load().resolve(LogicalInstrument.BASS.wireName)
        val requests = mutableListOf<BassGenerationRequest>()
        var start = 0L
        var ppq: Int? = null
        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId]
                ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            require(analysis.ppq > 0 && analysis.durationTicks > 0) { "MIDI analysis for '${section.partId}' has invalid timing" }
            if (ppq == null) ppq = analysis.ppq else require(ppq == analysis.ppq) { "All arranged MIDI parts must use the same PPQ" }
            val plans = section.instruments.filterIsInstance<BassInstrumentPlan>()
            require(plans.size <= 1) { "Detailed arrangement section ${section.index + 1} contains duplicate bass plans" }
            plans.singleOrNull()?.let { plan ->
                require(plan.name == LogicalInstrument.BASS.wireName && plan.mode == InstrumentMode.GENERATED) {
                    "Detailed arrangement section ${section.index + 1} has an invalid bass plan"
                }
                requests += BassGenerationRequest(
                    sectionIndex = position,
                    sectionStartTick = start,
                    ppq = analysis.ppq,
                    tempoMap = analysis.tempoMap,
                    timeSignatures = analysis.timeSignatures,
                    sectionLengthTicks = analysis.durationTicks,
                    key = analysis.key,
                    chords = analysis.chords,
                    energy = section.energy,
                    density = plan.density,
                    role = plan.role.toBassRole(),
                    movement = plan.movement.toBassMovement(),
                    register = plan.register.name.lowercase(),
                    syncopation = plan.syncopation,
                    midiChannel = bass.midiChannelZeroBased ?: 0,
                    midiProgram = bass.midiProgram
                )
            }
            start = Math.addExact(start, analysis.durationTicks)
        }
        require(requests.isNotEmpty()) { "Detailed arrangement does not contain a generated bass instrument" }
        val result = requests.map { it to composer.generate(it) }
        val output = root.resolve("midi/generated/bass.mid")
        writeMidi(output, checkNotNull(ppq), start, requests, result)
        return GeneratedBassMidi(output, checkNotNull(ppq), result.flatMap { it.second.notes }, result.flatMap { it.second.diagnostics })
    }

    private fun DetailedBassRole.toBassRole(): BassRole = when (this) {
        DetailedBassRole.ROOT -> BassRole.ROOT
        DetailedBassRole.ROOT_FIFTH -> BassRole.ROOT_FIFTH
        DetailedBassRole.OCTAVE -> BassRole.OCTAVE
        DetailedBassRole.SUSTAINED -> BassRole.SUSTAINED
    }

    private fun DetailedBassMovement.toBassMovement(): BassMovement = when (this) {
        DetailedBassMovement.STATIC, DetailedBassMovement.ROOT_MOTION -> BassMovement.STATIC
        DetailedBassMovement.LEAPING, DetailedBassMovement.OCTAVES -> BassMovement.BALANCED
    }

    private fun writeMidi(
        output: Path,
        ppq: Int,
        endTick: Long,
        requests: List<BassGenerationRequest>,
        results: List<Pair<BassGenerationRequest, BassGenerationResult>>
    ) {
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val sequence = Sequence(Sequence.PPQ, ppq)
            val meta = sequence.createTrack()
            val notes = sequence.createTrack()
            requests.forEach { request ->
                request.tempoMap.forEach { tempo -> meta.add(MidiEvent(tempoMessage(tempo.bpm), request.sectionStartTick + tempo.tick)) }
                request.timeSignatures.forEach { signature -> meta.add(MidiEvent(signatureMessage(signature), request.sectionStartTick + signature.tick)) }
            }
            val first = requests.first()
            first.midiProgram?.let { notes.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, first.midiChannel, it, 0), 0)) }
            results.flatMap { it.second.notes }.sortedBy { it.startTick }.forEach { note ->
                notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, first.midiChannel, note.pitch, note.velocity), note.startTick))
                notes.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, first.midiChannel, note.pitch, 0), note.endTick))
            }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), endTick))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write bass MIDI" }
            validateWrittenMidi(temporary, ppq, endTick, results.flatMap { it.second.notes })
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException("Atomic publish is not supported for generated bass MIDI '$output'", error)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateWrittenMidi(path: Path, ppq: Int, endTick: Long, expected: List<BassMidiNote>) {
        val sequence = MidiSystem.getSequence(path.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == ppq) { "Generated bass MIDI timing did not round-trip" }
        val parsed = mutableListOf<BassMidiNote>()
        val active = mutableMapOf<Int, Pair<Long, Int>>()
        sequence.tracks.flatMap { track -> (0 until track.size()).map { track[it] } }.sortedBy { it.tick }.forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) {
                require(active.put(message.data1, event.tick to message.data2) == null) { "Generated bass MIDI has overlapping active pitches" }
            } else if (off) {
                val start = requireNotNull(active.remove(message.data1)) { "Generated bass MIDI has a hanging note-off" }
                parsed += BassMidiNote(start.first, event.tick, message.data1, start.second)
            }
        }
        require(active.isEmpty()) { "Generated bass MIDI has hanging notes" }
        require(parsed.sortedBy { it.startTick } == expected.sortedBy { it.startTick }) { "Generated bass MIDI events did not round-trip" }
        require(sequence.tickLength >= endTick) { "Generated bass MIDI does not cover the complete timeline" }
    }

    private fun tempoMessage(bpm: Double): MetaMessage {
        val micros = (60_000_000.0 / bpm).roundToInt()
        return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }

    private fun signatureMessage(signature: MidiTimeSignature): MetaMessage = MetaMessage(
        0x58,
        byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8),
        4
    )
}
