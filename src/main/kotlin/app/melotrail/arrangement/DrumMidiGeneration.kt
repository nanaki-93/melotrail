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

/** One named starter-library drum hit in the project-wide MIDI timeline. */
data class DrumMidiHit(val name: String, val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

/**
 * Validated, deterministic input for one detailed-arrangement section. Only
 * 4/4 and 3/4 are intentionally supported by this first conservative engine.
 */
data class DrumGenerationRequest(
    val sectionIndex: Int,
    val sectionStartTick: Long,
    val ppq: Int,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val sectionLengthTicks: Long,
    val energy: Double,
    val density: Double,
    val role: DrumsRole,
    val kickDensity: Double,
    val snarePattern: SnarePattern,
    val hiHatDensity: Double,
    val swing: Double,
    val fillLastBar: Boolean,
    val transitionIntent: SongTransitionIntent,
    val midiChannel: Int,
    val noteMap: Map<String, Int>
) {
    fun requireValid() {
        require(sectionIndex >= 0 && sectionStartTick >= 0) { "Drum section index and start tick must not be negative" }
        require(ppq in 24..9_600) { "Drum PPQ must be from 24 to 9600" }
        require(sectionLengthTicks > 0) { "Drum section length must be positive" }
        listOf("energy" to energy, "density" to density, "kick density" to kickDensity, "hi-hat density" to hiHatDensity).forEach { (name, value) ->
            require(value.isFinite() && value in 0.0..1.0) { "Drum $name must be from 0.0 to 1.0" }
        }
        require(swing.isFinite() && swing in 0.0..MAX_SWING) { "Drum swing must be from 0.0 to $MAX_SWING" }
        require(midiChannel in 0..15) { "Drum MIDI channel must be 0..15" }
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L) { "Drum tempo map must start at tick 0" }
        require(timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "Drum time-signature map must start at tick 0" }
        tempoMap.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Drum tempo changes must be ordered" } }
        timeSignatures.zipWithNext().forEach { (first, second) -> require(first.tick < second.tick) { "Drum time-signature changes must be ordered" } }
        tempoMap.forEach { require(it.tick in 0..sectionLengthTicks && it.bpm.isFinite() && it.bpm > 0.0) { "Drum tempo map contains an invalid change" } }
        timeSignatures.forEach { signature ->
            require(signature.tick in 0..sectionLengthTicks && signature.denominator == 4 && signature.numerator in SUPPORTED_NUMERATORS) {
                "Unsupported drum meter ${signature.numerator}/${signature.denominator}; supported meters: 4/4, 3/4"
            }
            require((ppq * 4) % signature.denominator == 0) { "Drum PPQ cannot represent ${signature.numerator}/${signature.denominator}" }
        }
        require(noteMap.keys.containsAll(REQUIRED_HITS)) { "Drum note map is missing required hit(s): ${REQUIRED_HITS.filterNot(noteMap::containsKey).joinToString()}" }
        require(noteMap.values.distinct().size == noteMap.values.size) { "Drum note map must not assign one MIDI pitch to multiple named hits" }
        noteMap.forEach { (name, pitch) -> require(pitch in 0..127) { "Drum note map '$name' must be 0..127" } }
    }

    private companion object {
        const val MAX_SWING = 0.5
        val SUPPORTED_NUMERATORS = setOf(3, 4)
        val REQUIRED_HITS = setOf("kick", "snare", "closedHat", "openHat")
    }
}

data class DrumGenerationResult(val hits: List<DrumMidiHit>, val diagnostics: List<String>)

/**
 * Explicit beat-relative starter patterns. Density selects an even,
 * deterministic subset of each hit type; no random timing or velocity is used.
 */
class DeterministicDrumMidiGenerator {
    fun generate(request: DrumGenerationRequest): DrumGenerationResult {
        request.requireValid()
        if (request.density == 0.0) return DrumGenerationResult(emptyList(), listOf("Drum density is 0.0; wrote silence."))

        val bars = barWindows(request)
        val hits = linkedMapOf<Pair<Long, String>, DrumMidiHit>()
        bars.forEach { bar -> addPattern(request, bar, hits) }
        if (request.fillLastBar) addFill(request, bars.last(), hits)
        // A short PPQ combined with swing or a final-bar fill can place a later
        // same-pitch hit before the earlier hit's nominal end. Keep both hits,
        // but close the first one at the next attack so the generated MIDI has
        // one unambiguous active note per drum pitch.
        val result = shortenSamePitchHits(hits.values)
        validateHits(result, request)
        return DrumGenerationResult(result, emptyList())
    }

    private fun addPattern(request: DrumGenerationRequest, bar: BarWindow, output: MutableMap<Pair<Long, String>, DrumMidiHit>) {
        val beat = bar.ticksPerBeat
        val role = request.role
        val kickBeats = when (role) {
            DrumsRole.MINIMAL, DrumsRole.SOFT_LOFI, DrumsRole.HALF_TIME -> listOf(0)
            DrumsRole.STANDARD_GROOVE -> listOf(0, 2).filter { it < bar.numerator }
            DrumsRole.BUILD -> (0 until bar.numerator).toList()
        }
        addSlots(request, bar, output, "kick", kickBeats.map { Slot(it.toLong() * beat, beat, false) }, request.kickDensity)

        val snareBeats = when (request.snarePattern) {
            SnarePattern.NONE -> emptyList()
            SnarePattern.BEATS_2_4 -> listOf(1, 3).filter { it < bar.numerator }
            SnarePattern.BEAT_3 -> listOf(2).filter { it < bar.numerator }
        }
        addSlots(request, bar, output, "snare", snareBeats.map { Slot(it.toLong() * beat, beat, false) }, request.density)

        val subdivision = if (role == DrumsRole.BUILD) beat / 4 else beat / 2
        val hatSlots = buildList {
            var offset = 0L
            while (offset < bar.length) {
                add(Slot(offset, subdivision, offset % beat != 0L))
                offset += subdivision
            }
        }
        addSlots(request, bar, output, "closedHat", hatSlots, request.hiHatDensity)
        if (role == DrumsRole.SOFT_LOFI && request.energy >= 0.55 && bar.length >= beat) {
            addSlots(request, bar, output, "openHat", listOf(Slot(bar.length - beat / 2, beat / 2, true)), request.hiHatDensity)
        }
    }

    private fun addFill(request: DrumGenerationRequest, bar: BarWindow, output: MutableMap<Pair<Long, String>, DrumMidiHit>) {
        val beat = bar.ticksPerBeat
        val step = beat / 4
        val start = (bar.length - beat).coerceAtLeast(0)
        val slots = (0 until 4).map { Slot(start + it * step, step, it > 0) }.filter { it.offset < bar.length }
        slots.forEachIndexed { index, slot ->
            val lift = index * if (request.transitionIntent == SongTransitionIntent.BUILD) 6 else 4
            addHit(request, bar, output, "snare", slot, velocity(request.energy, lift), applySwing = false)
        }
    }

    private fun addSlots(
        request: DrumGenerationRequest,
        bar: BarWindow,
        output: MutableMap<Pair<Long, String>, DrumMidiHit>,
        name: String,
        slots: List<Slot>,
        density: Double
    ) {
        selectedSlots(slots, density).forEach { slot -> addHit(request, bar, output, name, slot, velocity(request.energy), applySwing = true) }
    }

    private fun selectedSlots(slots: List<Slot>, density: Double): List<Slot> {
        if (slots.isEmpty() || density == 0.0) return emptyList()
        val count = ceil(slots.size * density).toInt().coerceIn(1, slots.size)
        return (0 until count).map { index -> slots[index * slots.size / count] }
    }

    private fun addHit(
        request: DrumGenerationRequest,
        bar: BarWindow,
        output: MutableMap<Pair<Long, String>, DrumMidiHit>,
        name: String,
        slot: Slot,
        velocity: Int,
        applySwing: Boolean
    ) {
        val delayed = if (applySwing && slot.offBeat) (slot.subdivision * request.swing / 2.0).roundToInt().toLong() else 0L
        val offset = (slot.offset + delayed).coerceAtMost(bar.length - 1)
        val start = request.sectionStartTick + bar.start + offset
        val end = minOf(request.sectionStartTick + bar.start + bar.length, start + minOf(NOTE_LENGTH_TICKS, slot.subdivision.coerceAtLeast(1)))
        val hit = DrumMidiHit(name, start, end, requireNotNull(request.noteMap[name]) { "Drum note map is missing '$name'" }, velocity)
        output.putIfAbsent(start to name, hit)
    }

    private fun barWindows(request: DrumGenerationRequest): List<BarWindow> {
        val windows = mutableListOf<BarWindow>()
        request.timeSignatures.forEachIndexed { index, signature ->
            val until = request.timeSignatures.getOrNull(index + 1)?.tick ?: request.sectionLengthTicks
            val beat = (request.ppq * 4 / signature.denominator).toLong()
            val barLength = beat * signature.numerator
            var start = signature.tick
            while (start < until) {
                val end = minOf(start + barLength, until, request.sectionLengthTicks)
                if (end > start) windows += BarWindow(start, end - start, beat, signature.numerator)
                start = end
            }
        }
        return windows
    }

    private fun shortenSamePitchHits(hits: Collection<DrumMidiHit>): List<DrumMidiHit> =
        hits.groupBy(DrumMidiHit::pitch).values.flatMap { samePitch ->
            val ordered = samePitch.sortedBy(DrumMidiHit::startTick)
            ordered.mapIndexed { index, hit ->
                val nextStart = ordered.getOrNull(index + 1)?.startTick
                hit.copy(endTick = minOf(hit.endTick, nextStart ?: hit.endTick))
            }
        }.sortedWith(compareBy<DrumMidiHit> { it.startTick }.thenBy { it.pitch })

    private fun velocity(energy: Double, lift: Int = 0): Int =
        (MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * energy).roundToInt().plus(lift).coerceIn(MIN_VELOCITY, MAX_VELOCITY)

    private fun validateHits(hits: List<DrumMidiHit>, request: DrumGenerationRequest) {
        val simultaneous = mutableSetOf<Pair<Long, String>>()
        hits.forEach { hit ->
            require(hit.name in request.noteMap && hit.pitch == request.noteMap[hit.name]) { "Generated drum hit does not resolve through the registry map" }
            require(hit.pitch in 0..127 && hit.velocity in 1..127 && hit.endTick > hit.startTick) { "Generated drum hit has invalid MIDI values" }
            require(hit.startTick >= request.sectionStartTick && hit.endTick <= request.sectionStartTick + request.sectionLengthTicks) { "Generated drum hit escapes its section" }
            require(simultaneous.add(hit.startTick to hit.name)) { "Generated duplicate simultaneous drum hit '${hit.name}'" }
        }
        hits.groupBy(DrumMidiHit::pitch).values.forEach { samePitch ->
            samePitch.sortedBy(DrumMidiHit::startTick).zipWithNext().forEach { (first, second) ->
                require(first.endTick <= second.startTick) { "Generated drum hits overlap for MIDI pitch ${first.pitch}" }
            }
        }
    }

    private data class Slot(val offset: Long, val subdivision: Long, val offBeat: Boolean)
    private data class BarWindow(val start: Long, val length: Long, val ticksPerBeat: Long, val numerator: Int)

    private companion object {
        const val MIN_VELOCITY = 44
        const val MAX_VELOCITY = 104
        const val NOTE_LENGTH_TICKS = 60L
    }
}

data class GeneratedDrumMidi(val path: Path, val ppq: Int, val hits: List<DrumMidiHit>, val diagnostics: List<String>)

/** Converts reviewed v3 drum controls into a full-timeline, registry-mapped MIDI file. */
class DrumMidiGenerationAdapter(
    private val composer: DeterministicDrumMidiGenerator = DeterministicDrumMidiGenerator(),
    private val libraryRoot: Path
) {
    fun generate(projectRoot: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): GeneratedDrumMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val drums = InstrumentRegistryLoader(libraryRoot).load().resolve(LogicalInstrument.DRUMS.wireName)
        val requests = mutableListOf<DrumGenerationRequest>()
        val timeline = mutableListOf<TimelineSegment>()
        var start = 0L
        var ppq: Int? = null
        arrangement.sections.forEachIndexed { position, section ->
            val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            require(analysis.ppq > 0 && analysis.durationTicks > 0) { "MIDI analysis for '${section.partId}' has invalid timing" }
            if (ppq == null) ppq = analysis.ppq else require(ppq == analysis.ppq) { "All arranged MIDI parts must use the same PPQ" }
            timeline += TimelineSegment(start, analysis.tempoMap, analysis.timeSignatures)
            val drumPlans = section.instruments.filterIsInstance<DrumsInstrumentPlan>()
            require(drumPlans.size <= 1) { "Detailed arrangement section ${section.index + 1} contains duplicate drum plans" }
            drumPlans.singleOrNull()?.let { plan ->
                require(plan.name == LogicalInstrument.DRUMS.wireName && plan.mode == InstrumentMode.GENERATED) {
                    "Detailed arrangement section ${section.index + 1} has an invalid drum plan"
                }
                requests += DrumGenerationRequest(
                    position, start, analysis.ppq, analysis.tempoMap, analysis.timeSignatures, analysis.durationTicks,
                    section.energy, plan.density, plan.role, plan.kickDensity, plan.snarePattern, plan.hiHatDensity,
                    plan.swing, plan.fillLastBar, section.transitionOut.type.toSongTransitionIntent(),
                    requireNotNull(drums.midiChannelZeroBased) { "Validated drum registry has no MIDI channel" }, drums.noteMap
                )
            }
            start = Math.addExact(start, analysis.durationTicks)
        }
        require(requests.isNotEmpty()) { "Detailed arrangement does not contain a generated drums instrument" }
        val results = requests.map { it to composer.generate(it) }
        val output = root.resolve("midi/generated/drums.mid")
        writeMidi(output, checkNotNull(ppq), start, requireNotNull(drums.midiChannelZeroBased), timeline, results)
        return GeneratedDrumMidi(output, checkNotNull(ppq), results.flatMap { it.second.hits }, results.flatMap { it.second.diagnostics })
    }

    private fun writeMidi(
        output: Path,
        ppq: Int,
        endTick: Long,
        channel: Int,
        timeline: List<TimelineSegment>,
        results: List<Pair<DrumGenerationRequest, DrumGenerationResult>>
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
            results.flatMap { it.second.hits }.flatMap { hit ->
                listOf(DrumEvent(hit, noteOn = false), DrumEvent(hit, noteOn = true))
            }.sortedWith(compareBy<DrumEvent> { it.tick }.thenBy { if (it.noteOn) 1 else 0 }.thenBy { it.hit.pitch }).forEach { event ->
                val hit = event.hit
                val message = if (event.noteOn) ShortMessage(ShortMessage.NOTE_ON, channel, hit.pitch, hit.velocity)
                else ShortMessage(ShortMessage.NOTE_OFF, channel, hit.pitch, 0)
                notes.add(MidiEvent(message, event.tick))
            }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), endTick))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write drum MIDI" }
            validateWrittenMidi(temporary, ppq, endTick, channel, results.flatMap { it.second.hits })
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IllegalStateException("Atomic publish is not supported for generated drum MIDI '$output'", error)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateWrittenMidi(path: Path, ppq: Int, endTick: Long, channel: Int, expected: List<DrumMidiHit>) {
        val sequence = MidiSystem.getSequence(path.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == ppq && sequence.tickLength >= endTick) { "Generated drum MIDI timing did not round-trip" }
        val parsed = mutableListOf<DrumMidiHit>()
        val active = mutableMapOf<Int, Pair<Long, Int>>()
        sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
            .sortedWith(compareBy<MidiEvent> { it.tick }.thenBy { eventPriority(it.message as? ShortMessage) }).forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            if (message.channel != channel) return@forEach
            val on = message.command == ShortMessage.NOTE_ON && message.data2 > 0
            val off = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
            if (on) {
                require(active.put(message.data1, event.tick to message.data2) == null) { "Generated drum MIDI has overlapping active pitches" }
            } else if (off) {
                val start = requireNotNull(active.remove(message.data1)) { "Generated drum MIDI has a hanging note-off" }
                parsed += DrumMidiHit(expected.first { it.pitch == message.data1 && it.startTick == start.first }.name, start.first, event.tick, message.data1, start.second)
            }
        }
        require(active.isEmpty()) { "Generated drum MIDI has hanging notes" }
        require(parsed.sortedWith(compareBy<DrumMidiHit> { it.startTick }.thenBy { it.pitch }) == expected.sortedWith(compareBy<DrumMidiHit> { it.startTick }.thenBy { it.pitch })) {
            "Generated drum MIDI events did not round-trip"
        }
    }

    private fun TransitionType.toSongTransitionIntent(): SongTransitionIntent = when (this) {
        TransitionType.BRIDGE -> SongTransitionIntent.BUILD
        TransitionType.CROSSFADE -> SongTransitionIntent.RELEASE
        else -> SongTransitionIntent.NONE
    }

    private fun tempoMessage(bpm: Double): MetaMessage {
        val micros = (60_000_000.0 / bpm).roundToInt()
        return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }

    private fun signatureMessage(signature: MidiTimeSignature): MetaMessage = MetaMessage(
        0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4
    )

    private fun eventPriority(message: ShortMessage?): Int = when {
        message == null -> 2
        message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> 0
        message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> 1
        else -> 2
    }

    private data class DrumEvent(val hit: DrumMidiHit, val noteOn: Boolean) {
        val tick: Long get() = if (noteOn) hit.startTick else hit.endTick
    }

    private data class TimelineSegment(val startTick: Long, val tempoMap: List<MidiTempoChange>, val timeSignatures: List<MidiTimeSignature>)
}
