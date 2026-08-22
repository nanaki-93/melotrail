package app.melotrail.arrangement

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

/** The MIDI-only transition vocabulary. */
enum class MidiTransitionType { NONE, DRUM_FILL, BASS_WALK, PAD_SUSTAIN, BUILD, DROP, CYMBAL }

data class MidiTransitionPlan(val type: MidiTransitionType = MidiTransitionType.NONE, val bars: Int = 0) {
    fun requireValid() {
        when (type) {
            MidiTransitionType.NONE, MidiTransitionType.DROP -> require(bars == 0) { "$type transition must use 0 bars" }
            else -> require(bars in 1..2) { "$type transition must use 1 or 2 bars" }
        }
    }
}

/** A section-relative analysis plus the logical generated instruments active in that section. */
data class TransitionSectionContext(
    val sectionIndex: Int,
    val partId: String,
    val ppq: Int,
    val durationTicks: Long,
    val tempoMap: List<MidiTempoChange>,
    val timeSignatures: List<MidiTimeSignature>,
    val key: MidiKey?,
    val chords: List<MidiChord>,
    val instruments: Set<LogicalInstrument>,
    val energy: Double
) {
    fun requireValid() {
        require(sectionIndex >= 0 && partId.isNotBlank()) { "Transition section identity is invalid" }
        require(ppq in 24..9_600 && durationTicks > 0) { "Transition section timing is invalid" }
        require(tempoMap.isNotEmpty() && tempoMap.first().tick == 0L) { "Transition tempo map must start at tick 0" }
        require(timeSignatures.isNotEmpty() && timeSignatures.first().tick == 0L) { "Transition time-signature map must start at tick 0" }
        require(energy.isFinite() && energy in 0.0..1.0) { "Transition energy must be from 0.0 to 1.0" }
        timeSignatures.forEach { signature ->
            require(signature.tick in 0 until durationTicks && signature.numerator > 0 && signature.denominator in setOf(1, 2, 4, 8, 16, 32)) {
                "Transition time-signature map contains an invalid change"
            }
            require((ppq * 4) % signature.denominator == 0) { "Transition PPQ cannot represent ${signature.numerator}/${signature.denominator}" }
        }
        chords.zipWithNext().forEach { (left, right) -> require(left.endTick <= right.startTick) { "Transition chords must not overlap" } }
        chords.forEach { chord ->
            require(chord.startTick >= 0 && chord.endTick > chord.startTick && chord.endTick <= durationTicks && chord.confidence.isFinite() && chord.confidence in 0.0..1.0) {
                "Transition chord segment is invalid"
            }
        }
    }
}

data class TransitionInstrument(val channel: Int, val program: Int? = null) {
    init {
        require(channel in 0..15) { "Transition MIDI channel must be 0..15" }
        require(program == null || program in 0..127) { "Transition MIDI program must be 0..127" }
    }
}

data class TransitionMidiEvent(
    val instrument: LogicalInstrument,
    val startTick: Long,
    val endTick: Long,
    val pitch: Int,
    val velocity: Int
)

/** Exact half-open song-tick window in which a generated transition may sound. */
data class TransitionMidiWindow(val startTick: Long, val endTick: Long) {
    init { require(startTick >= 0 && endTick > startTick) { "Transition MIDI window is invalid" } }
    operator fun contains(tick: Long): Boolean = tick in startTick until endTick
}

data class TransitionSectionPlacement(val sectionIndex: Int, val startTick: Long, val endTick: Long, val insertedTicksAfter: Long)
data class TransitionGenerationResult(
    val ppq: Int,
    val placements: List<TransitionSectionPlacement>,
    val events: List<TransitionMidiEvent>,
    val diagnostics: List<String>
)

/**
 * Pure deterministic transition composer. It writes no source or generated instrument MIDI;
 * callers may publish the returned events as a separate, inspectable transition artifact.
 */
class DeterministicMidiTransitionEngine {
    fun generate(
        sections: List<TransitionSectionContext>,
        plans: List<MidiTransitionPlan>,
        available: Map<LogicalInstrument, TransitionInstrument>,
        drumMap: Map<String, Int>,
        occupied: List<TransitionMidiEvent> = emptyList()
    ): TransitionGenerationResult {
        require(sections.isNotEmpty()) { "Transition generation requires at least one section" }
        require(plans.size == sections.size) { "Transition plan count must match section count" }
        sections.forEachIndexed { index, section ->
            section.requireValid()
            require(section.sectionIndex == index) { "Transition section index ${section.sectionIndex}; expected $index" }
        }
        val ppq = sections.first().ppq
        require(sections.all { it.ppq == ppq }) { "All transition sections must use the same PPQ" }
        plans.forEach(MidiTransitionPlan::requireValid)
        require(plans.last().type == MidiTransitionType.NONE) { "Final section cannot request a transition without a next section" }
        occupied.forEach(::validateEvent)

        val placements = mutableListOf<TransitionSectionPlacement>()
        var cursor = 0L
        sections.forEachIndexed { index, section ->
            val insertion = if (index == sections.lastIndex) 0L else insertedTicks(plans[index], sections[index + 1])
            placements += TransitionSectionPlacement(index, cursor, cursor + section.durationTicks, insertion)
            cursor = Math.addExact(cursor + section.durationTicks, insertion)
        }

        val events = mutableListOf<TransitionMidiEvent>()
        val diagnostics = mutableListOf<String>()
        sections.dropLast(1).forEachIndexed { index, current ->
            val previous = sections.getOrNull(index - 1)
            val next = sections[index + 1]
            val placement = placements[index]
            val plan = plans[index]
            val generated = gesture(previous, current, next, plan, placement.endTick, available, drumMap, diagnostics)
            events += withoutCollisions(generated, occupied + events, diagnostics)
        }
        return TransitionGenerationResult(ppq, placements, events.sortedWith(compareBy<TransitionMidiEvent> { it.startTick }.thenBy { it.instrument.name }.thenBy { it.pitch }), diagnostics.distinct())
    }

    private fun insertedTicks(plan: MidiTransitionPlan, next: TransitionSectionContext): Long {
        if (plan.type == MidiTransitionType.NONE || plan.type == MidiTransitionType.DROP) return 0
        val meter = next.timeSignatures.first()
        return Math.multiplyExact(plan.bars.toLong(), (next.ppq * 4L / meter.denominator) * meter.numerator)
    }

    private fun gesture(
        previous: TransitionSectionContext?, current: TransitionSectionContext,
        next: TransitionSectionContext, plan: MidiTransitionPlan, start: Long,
        available: Map<LogicalInstrument, TransitionInstrument>, drumMap: Map<String, Int>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> {
        val length = insertedTicks(plan, next)
        return when (plan.type) {
            MidiTransitionType.NONE -> emptyList()
            MidiTransitionType.DROP -> {
                diagnostics += "Section ${current.sectionIndex + 1}: drop inserts no MIDI; later rendering must simplify/remove activity."
                emptyList()
            }
            MidiTransitionType.CYMBAL -> throw IllegalArgumentException(
                "Section ${current.sectionIndex + 1}: cymbal transition is unavailable because the validated starter drum map has no cymbal sample."
            )
            MidiTransitionType.BASS_WALK -> bassWalk(current, next, start, length, available, diagnostics)
            MidiTransitionType.PAD_SUSTAIN -> padSustain(current, next, start, length, available, diagnostics)
            MidiTransitionType.DRUM_FILL -> drumFill(current, next, start, length, available, drumMap, diagnostics)
            MidiTransitionType.BUILD -> build(previous, current, next, start, length, available, drumMap, diagnostics)
        }
    }

    private fun bassWalk(
        current: TransitionSectionContext, next: TransitionSectionContext, start: Long, length: Long,
        available: Map<LogicalInstrument, TransitionInstrument>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> {
        if (LogicalInstrument.BASS !in current.instruments && LogicalInstrument.BASS !in next.instruments) {
            diagnostics += "Section ${current.sectionIndex + 1}: bass_walk degraded to none because bass is not active around the boundary."
            return emptyList()
        }
        requireAvailable(LogicalInstrument.BASS, available)
        val from = current.chords.lastOrNull { it.confidence >= HARMONY_CONFIDENCE }?.let(::parseHarmony)
        val to = next.chords.firstOrNull { it.confidence >= HARMONY_CONFIDENCE }?.let(::parseHarmony)
        if (from == null || to == null) {
            diagnostics += "Section ${current.sectionIndex + 1}: bass_walk degraded to none because boundary harmony is missing, unsupported, or low-confidence."
            return emptyList()
        }
        val beat = ticksPerBeat(next)
        val count = (length / beat).toInt().coerceAtLeast(1)
        val delta = shortestPitchClassDistance(from.root, to.root)
        return (0 until count).map { index ->
            val ratio = if (count == 1) 1.0 else index.toDouble() / (count - 1)
            val root = (from.root + (delta * ratio).roundToInt()).mod(12)
            val noteStart = start + index * beat
            TransitionMidiEvent(LogicalInstrument.BASS, noteStart, minOf(start + length, noteStart + (beat * 3 / 4).coerceAtLeast(1)), bassPitch(root), 72)
        }
    }

    private fun padSustain(
        current: TransitionSectionContext, next: TransitionSectionContext, start: Long, length: Long,
        available: Map<LogicalInstrument, TransitionInstrument>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> {
        if (LogicalInstrument.PAD !in current.instruments && LogicalInstrument.PAD !in next.instruments) {
            diagnostics += "Section ${current.sectionIndex + 1}: pad_sustain degraded to none because pad is not active around the boundary."
            return emptyList()
        }
        requireAvailable(LogicalInstrument.PAD, available)
        val harmony = (current.chords.lastOrNull { it.confidence >= HARMONY_CONFIDENCE }
            ?: next.chords.firstOrNull { it.confidence >= HARMONY_CONFIDENCE })?.let(::parseHarmony)
        if (harmony == null) {
            diagnostics += "Section ${current.sectionIndex + 1}: pad_sustain degraded to none because final harmony is missing, unsupported, or low-confidence."
            return emptyList()
        }
        val root = 48 + harmony.root
        val end = start + length - minOf((current.ppq / 24).toLong().coerceAtLeast(1), length - 1)
        return harmony.intervals.map { interval -> TransitionMidiEvent(LogicalInstrument.PAD, start, end, root + interval, 58) }
    }

    private fun drumFill(
        current: TransitionSectionContext, next: TransitionSectionContext, start: Long, length: Long,
        available: Map<LogicalInstrument, TransitionInstrument>, drumMap: Map<String, Int>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> {
        if (LogicalInstrument.DRUMS !in current.instruments && LogicalInstrument.DRUMS !in next.instruments) {
            diagnostics += "Section ${current.sectionIndex + 1}: drum_fill degraded to none because drums are not active around the boundary."
            return emptyList()
        }
        requireAvailable(LogicalInstrument.DRUMS, available)
        val snare = drumMap["snare"] ?: throw IllegalArgumentException("drum_fill requires the validated 'snare' registry note map entry")
        val beat = ticksPerBeat(next)
        val step = (beat / 4).coerceAtLeast(1)
        val fillStart = start + (length - beat).coerceAtLeast(0)
        return (0 until 4).map { index ->
            val noteStart = fillStart + index * step
            TransitionMidiEvent(LogicalInstrument.DRUMS, noteStart, minOf(start + length, noteStart + minOf(step, 60L)), snare, 76 + index * 7)
        }
    }

    private fun build(
        previous: TransitionSectionContext?, current: TransitionSectionContext, next: TransitionSectionContext,
        start: Long, length: Long, available: Map<LogicalInstrument, TransitionInstrument>, drumMap: Map<String, Int>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> {
        val result = mutableListOf<TransitionMidiEvent>()
        if (LogicalInstrument.DRUMS in current.instruments || LogicalInstrument.DRUMS in next.instruments) result += drumFill(current, next, start, length, available, drumMap, diagnostics)
        if (LogicalInstrument.BASS in current.instruments || LogicalInstrument.BASS in next.instruments) result += bassWalk(current, next, start, length, available, diagnostics)
        if (LogicalInstrument.PAD in current.instruments || LogicalInstrument.PAD in next.instruments) {
            result += padSustain(current, next, start, length, available, diagnostics)
        }
        if (result.isEmpty()) diagnostics += "Section ${current.sectionIndex + 1}: build degraded to none because no supported generated instrument is active around the boundary${previous?.let { " (previous section ${it.sectionIndex + 1})" }.orEmpty()}."
        return result
    }

    private fun withoutCollisions(
        candidates: List<TransitionMidiEvent>, occupied: List<TransitionMidiEvent>, diagnostics: MutableList<String>
    ): List<TransitionMidiEvent> = candidates.sortedWith(compareBy<TransitionMidiEvent> { it.startTick }.thenBy { it.instrument.name }.thenBy { it.pitch }).filter { candidate ->
        validateEvent(candidate)
        val collision = occupied.any { existing -> existing.instrument == candidate.instrument && existing.pitch == candidate.pitch && existing.startTick < candidate.endTick && candidate.startTick < existing.endTick }
        if (collision) diagnostics += "Dropped colliding ${candidate.instrument.wireName} transition note at tick ${candidate.startTick}."
        !collision
    }

    private fun requireAvailable(instrument: LogicalInstrument, available: Map<LogicalInstrument, TransitionInstrument>) {
        require(instrument in available) { "Transition requires unavailable instrument '${instrument.wireName}'" }
    }

    private fun ticksPerBeat(section: TransitionSectionContext): Long = (section.ppq * 4L / section.timeSignatures.first().denominator)
    private fun bassPitch(root: Int): Int = 36 + root
    private fun shortestPitchClassDistance(from: Int, to: Int): Int = ((to - from + 18) % 12) - 6
    private fun validateEvent(event: TransitionMidiEvent) = require(event.startTick >= 0 && event.endTick > event.startTick && event.pitch in 0..127 && event.velocity in 1..127) { "Transition MIDI event is invalid" }

    private fun parseHarmony(chord: MidiChord): Harmony? {
        val match = CHORD.matchEntire(chord.symbol?.trim().orEmpty()) ?: return null
        val root = pitchClass(match.groupValues[1]) ?: return null
        val intervals = when (match.groupValues[2].lowercase()) {
            "" -> intArrayOf(0, 4, 7)
            "m", "min" -> intArrayOf(0, 3, 7)
            "7" -> intArrayOf(0, 4, 7, 10)
            "maj7" -> intArrayOf(0, 4, 7, 11)
            "m7", "min7" -> intArrayOf(0, 3, 7, 10)
            "sus2" -> intArrayOf(0, 2, 7)
            "sus4", "sus" -> intArrayOf(0, 5, 7)
            else -> return null
        }
        return Harmony(root, intervals)
    }

    private fun pitchClass(value: String): Int? {
        val base = when (value.firstOrNull()?.uppercaseChar()) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> return null }
        return when (value.getOrNull(1)) { '#' -> (base + 1) % 12; 'b' -> (base + 11) % 12; else -> base }
    }

    private data class Harmony(val root: Int, val intervals: IntArray)
    private companion object {
        const val HARMONY_CONFIDENCE = 0.75
        val CHORD = Regex("^([A-G](?:#|b)?)(|m|min|7|maj7|m7|min7|sus2|sus4|sus)$", RegexOption.IGNORE_CASE)
    }
}

data class GeneratedTransitionMidi(
    val path: Path,
    val result: TransitionGenerationResult,
    /** Exact half-open windows in the generated MIDI timeline. */
    val validationWindows: List<TransitionMidiWindow>,
    /** End of the generated timeline, including any inserted transition bars. */
    val validationTimelineEndTick: Long
)

/** Publishes the pure engine output as midi/generated/transitions.mid, atomically and idempotently. */
class MidiTransitionGenerationAdapter(
    private val engine: DeterministicMidiTransitionEngine = DeterministicMidiTransitionEngine(),
    private val libraryRoot: Path
) {
    fun generate(projectRoot: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): GeneratedTransitionMidi {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val registry = InstrumentRegistryLoader(libraryRoot).load()
        val active = arrangement.sections.flatMap { section ->
            section.instruments.filter { it.mode == InstrumentMode.GENERATED }.map { LogicalInstrument.parse(it.name) }
        }.toSet()
        val available = active.associateWith { logical ->
            val descriptor = registry.resolveApprovedRole(project, logical)
            TransitionInstrument(descriptor.midiChannelZeroBased ?: 0, descriptor.midiProgram)
        }
        val sections = arrangement.sections.mapIndexed { index, section ->
            val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
            TransitionSectionContext(index, section.partId, analysis.ppq, analysis.durationTicks, analysis.tempoMap, analysis.timeSignatures, analysis.key, analysis.chords,
                section.instruments.filter { it.mode == InstrumentMode.GENERATED }.map { LogicalInstrument.parse(it.name) }.toSet(), section.energy)
        }
        approvedCohesionReferences(root, project)?.let { cohesion ->
            return generateApprovedCohesion(root, arrangement, cohesion, sections, available)
        }
        val plans = arrangement.sections.mapIndexed { index, section ->
            if (index == arrangement.sections.lastIndex) MidiTransitionPlan() else section.transitionOut.toMidiPlan()
        }
        val drumMap = active.takeIf { LogicalInstrument.DRUMS in it }
            ?.let { registry.resolveApprovedRole(project, LogicalInstrument.DRUMS).noteMap }
            .orEmpty()
        val result = engine.generate(sections, plans, available, drumMap)
        val output = root.resolve("midi/generated/transitions.mid")
        writeMidi(output, result, available, sections)
        val validationWindows = result.placements.dropLast(1).mapIndexedNotNull { index, placement ->
            plans[index].takeIf { it.type != MidiTransitionType.NONE && it.type != MidiTransitionType.DROP && placement.insertedTicksAfter > 0 }
                ?.let { TransitionMidiWindow(placement.endTick, placement.endTick + placement.insertedTicksAfter) }
        }
        val timelineEnd = result.placements.last().let { it.endTick + it.insertedTicksAfter }
        return GeneratedTransitionMidi(output, result, validationWindows, timelineEnd)
    }

    /**
     * Task 117 path: publish the already approved Cohesion bridge MIDI at its
     * shifted boundary. This does not compose a replacement gesture.
     */
    private fun generateApprovedCohesion(
        root: Path,
        arrangement: DetailedArrangement,
        cohesion: ArrangementCohesionReferences,
        sections: List<TransitionSectionContext>,
        available: Map<LogicalInstrument, TransitionInstrument>
    ): GeneratedTransitionMidi {
        val expectedPairs = arrangement.sections.zipWithNext().map { (outgoing, incoming) -> outgoing.instanceId to incoming.instanceId }
        require(cohesion.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } == expectedPairs) {
            "Arrangement Cohesion boundaries do not match the rendered section order."
        }
        require(sections.isNotEmpty()) { "Cohesion transition generation requires at least one section." }
        val ppq = sections.first().ppq
        require(sections.all { it.ppq == ppq }) { "Cohesion transition MIDI requires one project PPQ." }
        val bridges = cohesion.boundaries.map { reference ->
            val approved = root.resolve(CohesionBoundaryArtifactPaths.approved(reference.outgoingInstanceId, reference.incomingInstanceId))
            require(Files.isRegularFile(approved) && digest(approved) == reference.approvedSha256) {
                "Approved Cohesion boundary '${reference.outgoingInstanceId} -> ${reference.incomingInstanceId}' has changed; regenerate cohesion."
            }
            Json { ignoreUnknownKeys = false }.decodeFromString(TransitionBridgePlan.serializer(), Files.readString(approved)).also { bridge ->
                require(bridge.outgoingInstanceId == reference.outgoingInstanceId && bridge.incomingInstanceId == reference.incomingInstanceId) {
                    "Approved Cohesion boundary identity is inconsistent."
                }
            }
        }
        val placements = placements(sections)
        val output = root.resolve("midi/generated/transitions.mid")
        writeApprovedMidi(output, ppq, sections, placements, bridges, cohesion, available, root)
        val events = bridges.flatMapIndexed { index, bridge ->
            val sequence = MidiSystem.getSequence(root.resolve(TransitionCohesionStore.bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId)).toFile())
            val logical = LogicalInstrument.parse(bridge.instrument)
            sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.mapNotNull { event ->
                val message = event.message as? ShortMessage
                message?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let {
                    val offset = bridgeOverlayStart(placements[index], sections[index], bridge)
                    TransitionMidiEvent(logical, offset + event.tick, offset + event.tick + 1, it.data1, it.data2)
                }
            }
        }
        val validationWindows = bridges.mapIndexed { index, bridge ->
            val outgoingBeat = ppq * 4L / sections[index].timeSignatures.first().denominator
            val incomingBeat = ppq * 4L / sections[index + 1].timeSignatures.first().denominator
            val start = bridgeOverlayStart(placements[index], sections[index], bridge)
            TransitionMidiWindow(start, start + outgoingBeat * bridge.leadBeats + incomingBeat * bridge.tailBeats)
        }
        val timelineEnd = placements.last().let { it.endTick + it.insertedTicksAfter }
        return GeneratedTransitionMidi(output, TransitionGenerationResult(ppq, placements, events, emptyList()), validationWindows, timelineEnd)
    }

    private fun bridgesIndex(cohesion: ArrangementCohesionReferences, reference: ArrangementCohesionBoundaryReference): Int =
        cohesion.boundaries.indexOf(reference).also { require(it >= 0) }

    private fun approvedCohesionReferences(root: Path, project: Project): ArrangementCohesionReferences? {
        val workflow = project.workflow.cohesion ?: return null
        if (!workflow.approved || WorkflowArtifact.COHESION in project.workflow.stale) return null
        val boundaries = workflow.boundaries.map { boundary ->
            val approved = requireNotNull(boundary.approved) { "Approved Cohesion boundary evidence is missing." }
            val bridge = root.resolve(TransitionCohesionStore.bridgeMidi(boundary.outgoingInstanceId, boundary.incomingInstanceId))
            val bridgeHash = requireNotNull(boundary.bridgeSha256) { "Historical Cohesion bridge evidence cannot be used for a new build; regenerate Cohesion." }
            require(Files.isRegularFile(bridge) && digest(bridge) == bridgeHash) { "Approved Cohesion bridge MIDI has changed; regenerate Cohesion." }
            ArrangementCohesionBoundaryReference(boundary.outgoingInstanceId, boundary.incomingInstanceId, approved.sha256, bridgeHash)
        }
        return ArrangementCohesionReferences(workflow.inputSha256, boundaries)
    }

    private fun placements(sections: List<TransitionSectionContext>): List<TransitionSectionPlacement> {
        var cursor = 0L
        return sections.mapIndexed { index, section ->
            val inserted = 0L
            TransitionSectionPlacement(index, cursor, cursor + section.durationTicks, inserted).also { cursor += section.durationTicks + inserted }
        }
    }

    private fun bridgeOverlayStart(placement: TransitionSectionPlacement, outgoing: TransitionSectionContext, bridge: TransitionBridgePlan): Long {
        val meter = outgoing.timeSignatures.first()
        val beat = outgoing.ppq * 4L / meter.denominator
        return (placement.endTick - beat * bridge.leadBeats).coerceAtLeast(placement.startTick)
    }

    private fun writeApprovedMidi(
        output: Path,
        ppq: Int,
        sections: List<TransitionSectionContext>,
        placements: List<TransitionSectionPlacement>,
        bridges: List<TransitionBridgePlan>,
        cohesion: ArrangementCohesionReferences,
        instruments: Map<LogicalInstrument, TransitionInstrument>,
        root: Path
    ) {
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val sequence = Sequence(Sequence.PPQ, ppq)
            val meta = sequence.createTrack()
            placements.forEach { placement ->
                val context = sections[placement.sectionIndex]
                context.tempoMap.forEach { meta.add(MidiEvent(tempoMessage(it.bpm), placement.startTick + it.tick)) }
                context.timeSignatures.forEach { meta.add(MidiEvent(signatureMessage(it), placement.startTick + it.tick)) }
                if (placement.insertedTicksAfter > 0L) {
                    val incoming = sections[placement.sectionIndex + 1]
                    meta.add(MidiEvent(tempoMessage(incoming.tempoMap.first().bpm), placement.endTick))
                    meta.add(MidiEvent(signatureMessage(incoming.timeSignatures.first()), placement.endTick))
                }
            }
            bridges.forEachIndexed { index, bridge ->
                val source = root.resolve(TransitionCohesionStore.bridgeMidi(bridge.outgoingInstanceId, bridge.incomingInstanceId))
                val reference = cohesion.boundaries[index]
                require(Files.isRegularFile(source) && digest(source) == reference.bridgeSha256) {
                    "Approved Cohesion bridge MIDI has changed for '${bridge.outgoingInstanceId} -> ${bridge.incomingInstanceId}'; regenerate cohesion."
                }
                val sourceSequence = MidiSystem.getSequence(source.toFile())
                require(sourceSequence.divisionType == Sequence.PPQ && sourceSequence.resolution == ppq) { "Approved Cohesion bridge MIDI has incompatible timing." }
                val logical = LogicalInstrument.parse(bridge.instrument)
                val target = sequence.createTrack()
                val instrument = requireNotNull(instruments[logical])
                val offset = bridgeOverlayStart(placements[index], sections[index], bridge)
                instrument.program?.let { target.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, instrument.channel, it, 0), offset)) }
                val outgoingBeat = ppq * 4L / sections[index].timeSignatures.first().denominator
                val incomingBeat = ppq * 4L / sections[index + 1].timeSignatures.first().denominator
                val maximum = outgoingBeat * bridge.leadBeats + incomingBeat * bridge.tailBeats
                sourceSequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
                    .mapNotNull { event -> (event.message as? ShortMessage)?.let { event to it } }
                    .filter { (event, message) -> message.command in setOf(ShortMessage.NOTE_ON, ShortMessage.NOTE_OFF) && event.tick <= maximum }
                    .forEach { (event, message) ->
                        target.add(MidiEvent(ShortMessage(message.command, instrument.channel, message.data1, message.data2), offset + event.tick))
                    }
            }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), placements.last().endTick))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write approved Cohesion transition MIDI" }
            validateWrittenMidi(temporary, TransitionGenerationResult(ppq, placements, emptyList(), emptyList()))
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is not supported for generated transition MIDI '$output'", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun TransitionBridgePlan.toTransitionPlan(): TransitionPlan = TransitionPlan(
        TransitionType.BRIDGE,
        bars,
        bridge = BridgePlan(
            energy = when (energyContour) { EnergyContour.HOLD -> 0.5; EnergyContour.RISE -> 0.7; EnergyContour.FALL -> 0.3 },
            elements = when (bridgeType) {
                BridgeType.DRUM_FILL, BridgeType.BUILD, BridgeType.CONTINUITY -> listOf(BridgeElement.DRUM_FILL)
                BridgeType.BASS_WALK -> listOf(BridgeElement.BASS_PICKUP)
                BridgeType.PAD_SUSTAIN, BridgeType.CHORD_MOTION -> listOf(BridgeElement.PAD_SWELL)
            }
        )
    )

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun writeMidi(output: Path, result: TransitionGenerationResult, instruments: Map<LogicalInstrument, TransitionInstrument>, sections: List<TransitionSectionContext>) {
        Files.createDirectories(requireNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.${UUID.randomUUID()}.tmp")
        try {
            val sequence = Sequence(Sequence.PPQ, result.ppq)
            val meta = sequence.createTrack()
            result.placements.forEach { placement ->
                val context = sections[placement.sectionIndex]
                context.tempoMap.forEach { meta.add(MidiEvent(tempoMessage(it.bpm), placement.startTick + it.tick)) }
                context.timeSignatures.forEach { meta.add(MidiEvent(signatureMessage(it), placement.startTick + it.tick)) }
            }
            result.placements.dropLast(1).forEach { placement ->
                if (placement.insertedTicksAfter > 0) {
                    // The inserted bar belongs to the incoming meter/tempo; the following section
                    // repeats these tick-zero meta events at its shifted timeline start.
                    val incoming = sections[placement.sectionIndex + 1]
                    meta.add(MidiEvent(tempoMessage(incoming.tempoMap.first().bpm), placement.endTick))
                    meta.add(MidiEvent(signatureMessage(incoming.timeSignatures.first()), placement.endTick))
                }
            }
            result.events.groupBy { it.instrument }.toSortedMap(compareBy { it.wireName }).forEach { (logical, events) ->
                val track = sequence.createTrack()
                val instrument = requireNotNull(instruments[logical])
                instrument.program?.let { track.add(MidiEvent(ShortMessage(ShortMessage.PROGRAM_CHANGE, instrument.channel, it, 0), 0)) }
                events.forEach { event ->
                    track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, instrument.channel, event.pitch, event.velocity), event.startTick))
                    track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, instrument.channel, event.pitch, 0), event.endTick))
                }
            }
            meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), result.placements.last().endTick + result.placements.last().insertedTicksAfter))
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write transition MIDI" }
            validateWrittenMidi(temporary, result)
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (error: AtomicMoveNotSupportedException) { throw IllegalStateException("Atomic publish is not supported for generated transition MIDI '$output'", error) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun validateWrittenMidi(path: Path, expected: TransitionGenerationResult) {
        val sequence = MidiSystem.getSequence(path.toFile())
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == expected.ppq) { "Generated transition MIDI timing did not round-trip" }
        require(sequence.tickLength >= expected.placements.last().endTick + expected.placements.last().insertedTicksAfter) { "Generated transition MIDI does not cover the timeline" }
        sequence.tracks.forEach { track ->
            val active = mutableSetOf<Pair<Int, Int>>()
            (0 until track.size()).map(track::get).sortedBy { it.tick }.forEach { event ->
                val message = event.message as? ShortMessage ?: return@forEach
                val key = message.channel to message.data1
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) require(active.add(key)) { "Generated transition MIDI has a same-track collision" }
                if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) require(active.remove(key)) { "Generated transition MIDI has a hanging note-off" }
            }
            require(active.isEmpty()) { "Generated transition MIDI has hanging notes" }
        }
    }

    private fun tempoMessage(bpm: Double): MetaMessage {
        val micros = (60_000_000.0 / bpm).roundToInt()
        return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3)
    }
    private fun signatureMessage(signature: MidiTimeSignature): MetaMessage = MetaMessage(0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4)

    /** Preserve the approved bridge vocabulary instead of turning every bridge into a generic build. */
    private fun TransitionPlan.toMidiPlan(): MidiTransitionPlan = when (type) {
        TransitionType.NONE, TransitionType.CROSSFADE -> MidiTransitionPlan()
        TransitionType.BRIDGE -> {
            val elements = requireNotNull(bridge).elements.toSet()
            val type = when {
                elements.size > 1 -> MidiTransitionType.BUILD
                BridgeElement.BASS_PICKUP in elements -> MidiTransitionType.BASS_WALK
                BridgeElement.DRUM_FILL in elements -> MidiTransitionType.DRUM_FILL
                BridgeElement.PAD_SWELL in elements -> MidiTransitionType.PAD_SUSTAIN
                // There is no generated melody voice. BUILD is the musical,
                // registry-safe fallback for a melody pickup request.
                else -> MidiTransitionType.BUILD
            }
            MidiTransitionPlan(type, bars)
        }
    }
}
