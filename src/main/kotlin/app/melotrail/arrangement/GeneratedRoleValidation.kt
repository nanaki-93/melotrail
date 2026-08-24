package app.melotrail.arrangement

import app.melotrail.application.ArrangementGenerationProjection
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs
import kotlin.math.ceil

/** Code-owned limits for the deterministic generated-track validation boundary. */
@Serializable
data class RoleValidationPolicy(
    val version: Int = CURRENT_VERSION,
    val sustainedMinimumBeats: Double = 0.5,
    val densityNotesPerBeatAtFullDensity: Double = 16.0,
    val maximumBassGrooveResidualBeats: Double = 0.05,
    val maximumDrumGrooveResidualBeats: Double = 0.05,
    val maximumPianoFlamBeats: Double = 1.0 / 24.0,
    val maximumWarnings: Int = 64,
    val maximumViolations: Int = 128
) {
    init {
        require(version == CURRENT_VERSION && sustainedMinimumBeats == 0.5 && densityNotesPerBeatAtFullDensity == 16.0 &&
            maximumBassGrooveResidualBeats == 0.05 && maximumDrumGrooveResidualBeats == 0.05 && maximumPianoFlamBeats == 1.0 / 24.0) {
            "Unsupported generated-role validation policy"
        }
    }
    companion object { const val CURRENT_VERSION = 1 }
}

@Serializable
data class RoleValidationTarget(val kind: String = "aggregate", val occurrenceIds: List<String>) {
    init { require(kind == "aggregate" && occurrenceIds == occurrenceIds.sorted() && occurrenceIds.distinct().size == occurrenceIds.size) }
}

@Serializable data class RoleValidationHash(val name: String, val sha256: String)
@Serializable data class RoleValidationMetric(val name: String, val value: Long)

/** Stable instrument-map evidence retained with a generated-role validation report. */
@Serializable
data class RoleInstrumentMapEvidence(val role: String, val midiChannel: Int?, val notes: List<RoleInstrumentMapNote>) {
    init { require(role in RoleValidationReport.SUPPORTED_ROLES && (midiChannel == null || midiChannel in 0..15)) }
}

/** One named, registry-resolved MIDI note retained for later role-aware consumers. */
@Serializable
data class RoleInstrumentMapNote(val name: String, val pitch: Int) {
    init { require(name.isNotBlank() && pitch in 0..127) }
}

/** Drum-kick attacks and their source-groove residuals, recorded without coupling role generation to DSP. */
@Serializable
data class KickTimingEvidence(val midiChannel: Int, val note: Int, val attackTicks: List<Long>, val residualTicks: List<Long>) {
    init { require(midiChannel in 0..15 && note in 0..127 && attackTicks == attackTicks.sorted() && attackTicks.size == residualTicks.size && residualTicks.all { it >= 0 }) }
}

/** Persisted, deterministic evidence required before a generated role becomes current. */
@Serializable
data class RoleValidationReport(
    val version: Int = 1,
    val role: String,
    val target: RoleValidationTarget,
    val inputHashes: List<RoleValidationHash>,
    val outputSha256: String,
    val policyVersion: Int,
    val metrics: List<RoleValidationMetric>,
    val warnings: List<String>,
    val violations: List<String>,
    val passed: Boolean,
    /** Comparable accepted-ensemble facts before the candidate was admitted. */
    val acceptedStateMetrics: List<RoleValidationMetric> = emptyList(),
    val kickTimingEvidence: KickTimingEvidence? = null,
    val instrumentMapEvidence: RoleInstrumentMapEvidence? = null
) {
    init {
        require(version == 1 && role in SUPPORTED_ROLES && SHA.matches(outputSha256) && policyVersion == RoleValidationPolicy.CURRENT_VERSION)
        require(inputHashes == inputHashes.sortedBy(RoleValidationHash::name) && metrics == metrics.sortedBy(RoleValidationMetric::name) &&
            acceptedStateMetrics == acceptedStateMetrics.sortedBy(RoleValidationMetric::name))
        require(warnings.size <= 64 && violations.size <= 128 && warnings == warnings.sorted() && violations == violations.sorted())
        require(passed == violations.isEmpty())
    }
    companion object {
        val SUPPORTED_ROLES = setOf("piano", "bass", "drums", "pad", "strings", "transitions")
        private val SHA = Regex("[0-9a-f]{64}")
    }
}

data class GeneratedRoleValidationInput(
    val role: String,
    val midi: Path,
    val project: Project,
    val arrangement: DetailedArrangement,
    val projection: ArrangementGenerationProjection,
    val registry: ValidatedInstrumentRegistry,
    val arrangementSha256: String,
    val registrySha256: String,
    /** Exact approved source-groove map; bass and drums cannot validate without this evidence. */
    val acceptedFullSongGrooveMap: FullSongGrooveMap? = null,
    val policy: RoleValidationPolicy = RoleValidationPolicy(),
    /** Accepted core MIDI used to assess interaction before this candidate is admitted. */
    val arrangementState: ArrangementState? = null,
    /** Generator-owned evidence that an intentional rest was selected rather than a missing artifact. */
    val deliberateSilence: Boolean = false,
    /** Exact windows published by the transition generator. Required for the transitions role. */
    val transitionWindows: List<TransitionMidiWindow> = emptyList(),
    /** Exact generated transition timeline end. Required for the transitions role. */
    val transitionTimelineEndTick: Long? = null
)

fun interface GeneratedRoleValidator { fun validate(input: GeneratedRoleValidationInput): RoleValidationReport }

/** One owner for every generated arrangement-role invariant. */
class DeterministicGeneratedRoleValidator : GeneratedRoleValidator {
    override fun validate(input: GeneratedRoleValidationInput): RoleValidationReport {
        require(input.role in RoleValidationReport.SUPPORTED_ROLES) { "Unsupported generated role '${input.role}'" }
        val violations = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val sequence = runCatching { MidiSystem.getSequence(input.midi.toFile()) }.getOrElse {
            return report(input, emptyList(), listOf("MIDI is unreadable: ${it.message ?: it.javaClass.simpleName}"), emptyList())
        }
        if (sequence.divisionType != Sequence.PPQ || sequence.resolution <= 0) violations += "MIDI must use positive PPQ timing"
        val notes = readNotes(sequence, violations)
        val occurrences = input.projection.occurrences
        val active = activeOccurrences(input.role, input.arrangement, occurrences)
        if (active.isNotEmpty() && notes.isEmpty() && !silenceAllowed(input, active)) violations += "Activated role has no notes"
        if (notes.groupBy { listOf(it.channel, it.pitch, it.velocity, it.start, it.end) }.any { it.value.size > 1 }) violations += "MIDI contains exact duplicate notes"
        if (!metadataMatches(sequence, input)) violations += "MIDI tempo or meter does not preserve canonical settings"
        val songEnd = if (input.role == "transitions") requireNotNull(input.transitionTimelineEndTick) {
            "Transition validation requires the generator-owned timeline end."
        }
        else occurrences.lastOrNull()?.endTick.orEmpty()
        notes.forEach { note ->
            if (note.velocity !in 1..127) violations += "Note velocity is outside 1..127"
            if (note.end <= note.start) violations += "Note duration must be positive"
            if (note.start < 0 || note.end > songEnd) violations += "Note lies outside occurrence bounds"
        }
        if (input.role == "transitions") validateTransitions(notes, input, violations)
        else validateOccurrences(notes, input, active, violations)
        validateInstrument(notes, input, violations)
        validatePlannedDensity(notes, input, active, warnings, violations)
        validateGrooveAndFlam(notes, input, active, violations)
        when (input.role) {
            "piano", "pad", "strings" -> validateSustainedHarmony(notes, input, violations)
            "bass" -> validateBass(notes, input, warnings, violations)
            "drums" -> validateDrums(notes, input, active, violations)
        }
        if (input.role == "pad") validatePad(notes, input, violations)
        if (input.role == "strings") validateStrings(notes, input, violations)
        if (input.role in setOf("pad", "strings")) validateCrossSectionVoicing(notes, input, active, violations)
        return report(input, notes, violations, warnings)
    }

    private fun validateOccurrences(notes: List<Note>, input: GeneratedRoleValidationInput, active: List<MusicalOccurrence>, violations: MutableList<String>) {
        notes.forEach { note ->
            val occurrence = input.projection.occurrences.singleOrNull { note.start >= it.startTick && note.end <= it.endTick }
            if (occurrence == null) violations += "Note crosses an occurrence boundary"
            else if (occurrence !in active) violations += "Note occurs where role is not activated"
        }
    }

    private fun validateInstrument(notes: List<Note>, input: GeneratedRoleValidationInput, violations: MutableList<String>) {
        val descriptors = if (input.role == "transitions") LogicalInstrument.entries.mapNotNull { logical ->
            runCatching { input.registry.resolveApprovedRole(input.project, logical) }.getOrNull()
        } else listOf(input.registry.resolveApprovedRole(input.project, LogicalInstrument.parse(input.role)))
        notes.forEach { note ->
            val matching = descriptors.filter { descriptor -> note.pitch in descriptor.verifiedCapabilities.playableRange.low..descriptor.verifiedCapabilities.playableRange.high }
            if (matching.isEmpty()) violations += "Note is outside the resolved instrument range"
            if (input.role == "drums" && matching.none { note.channel == it.midiChannelZeroBased && note.pitch in it.noteMap.values }) {
                violations += "Drum note is not in the registered kit map or percussion channel"
            }
        }
    }

    private fun validateSustainedHarmony(notes: List<Note>, input: GeneratedRoleValidationInput, violations: MutableList<String>) {
        val scale = input.projection.projectKey.scalePitchClasses().map { it.chromatic }.toSet()
        notes.forEach { note ->
            val duration = (note.end - note.start).toDouble() / input.projection.harmonyPpq
            val chords = input.projection.harmony.filter { it.startTick < note.end && note.start < it.endTick }
            if (chords.isEmpty()) { violations += "Pitched note has no canonical harmony"; return@forEach }
            val chordTones = chords.flatMap { chordTones(it) }.toSet()
            if (duration >= input.policy.sustainedMinimumBeats && note.pitch % 12 !in chordTones) violations += "Sustained note is not a chord tone"
            if (duration < input.policy.sustainedMinimumBeats && note.pitch % 12 !in chordTones) {
                if (note.pitch % 12 !in scale) violations += "Short non-chord tone is outside the project scale"
                val nextBeat = ((note.start / input.projection.harmonyPpq) + 1) * input.projection.harmonyPpq
                val resolves = notes.any { candidate -> candidate.start in note.end..nextBeat && abs(candidate.pitch - note.pitch) in 1..2 && candidate.pitch % 12 in chordTones }
                if (!resolves) violations += "Short non-chord tone does not resolve by step by the next beat"
            }
        }
    }

    private fun validateBass(notes: List<Note>, input: GeneratedRoleValidationInput, warnings: MutableList<String>, violations: MutableList<String>) {
        val beatTicks = input.projection.harmonyPpq * 4L / input.projection.meter.denominator
        val beats = input.projection.harmony.flatMap { chord ->
            generateSequence(chord.startTick) { tick -> (tick + beatTicks).takeIf { it < chord.endTick } }.toList()
        }.distinct().sorted()
        notes.forEach { note -> beats.filter { it in note.start until note.end }.forEach { beat ->
            val chord = input.projection.harmony.singleOrNull { beat >= it.startTick && beat < it.endTick }
            if (chord == null || note.pitch % 12 !in chordTones(chord)) violations += "Bass note sounding on a canonical beat is not a chord tone"
        } }
        val ordered = notes.sortedBy(Note::start)
        ordered.zipWithNext().forEachIndexed { index, (left, right) ->
            val leap = right.pitch - left.pitch
            if (abs(leap) > 12) warnings += "Bass leap exceeds one octave"
            val next = ordered.getOrNull(index + 2)
            val resolves = abs(leap) > 19 && next != null && next.start >= right.end && abs(next.pitch - right.pitch) in 1..2 && (next.pitch - right.pitch) * leap < 0
            if (abs(leap) > 19 && !resolves) violations += "Bass leap exceeds nineteen semitones without opposite step resolution"
        }
    }

    /** Enforce the reviewed density ceiling and make a flat response to a changing plan visible in the report. */
    private fun validatePlannedDensity(
        notes: List<Note>,
        input: GeneratedRoleValidationInput,
        active: List<MusicalOccurrence>,
        warnings: MutableList<String>,
        violations: MutableList<String>
    ) {
        if (input.role == "transitions") return
        val actual = active.map { occurrence ->
            val beats = (occurrence.endTick - occurrence.startTick).toDouble() / input.projection.harmonyPpq
            val density = notes.count { it.start in occurrence.startTick until occurrence.endTick }.toDouble() / beats
            occurrence to density
        }
        actual.forEach { (occurrence, density) ->
            val planned = plannedDensity(input.role, input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId })
            if (planned == 0.0 && density > 0.0) violations += "Role has notes in a deliberate-silence section"
            val maximum = 1.0 + planned * input.policy.densityNotesPerBeatAtFullDensity
            if (density > maximum) violations += "Role density exceeds approved arrangement bound"
        }
        actual.zipWithNext().forEach { (before, after) ->
            val beforePlanned = plannedDensity(input.role, input.arrangement.sections.single { it.instanceId == before.first.occurrenceId })
            val afterPlanned = plannedDensity(input.role, input.arrangement.sections.single { it.instanceId == after.first.occurrenceId })
            if (kotlin.math.abs(afterPlanned - beforePlanned) >= 0.25 && (after.second - before.second) * (afterPlanned - beforePlanned) <= 0.0) {
                warnings += "Role density direction does not follow the approved section plan"
            }
        }
    }

    /** Bind bass and drums to the same active source-feel span and reject near-simultaneous piano flams. */
    private fun validateGrooveAndFlam(
        notes: List<Note>,
        input: GeneratedRoleValidationInput,
        active: List<MusicalOccurrence>,
        violations: MutableList<String>
    ) {
        if (input.role !in setOf("bass", "drums")) return
        val map = input.acceptedFullSongGrooveMap ?: run {
            violations += "Bass and drums require the accepted full-song groove map"
            return
        }
        if (map.ppq != input.projection.harmonyPpq || map.meterDenominator != input.projection.meter.denominator) {
            violations += "Accepted full-song groove map does not match canonical timing"
            return
        }
        val maximumResidual = (input.projection.harmonyPpq * if (input.role == "bass") input.policy.maximumBassGrooveResidualBeats else input.policy.maximumDrumGrooveResidualBeats).toLong()
        val flamWindow = (input.projection.harmonyPpq * input.policy.maximumPianoFlamBeats).toLong().coerceAtLeast(1)
        val pianoOnsets = input.arrangementState?.track(ArrangementState.PIANO)?.notes.orEmpty().map(MidiNote::startTick)
        notes.forEach { note ->
            val occurrence = active.singleOrNull { note.start in it.startTick until it.endTick } ?: return@forEach
            val expected = FullSongGrooveMapTiming.nearestExpectedTick(map, occurrence.occurrenceId, note.start)
            if (expected == null) violations += "Generated role has no active full-song groove-map span"
            else if (abs(note.start - expected) > maximumResidual) violations += "Generated role is off the approved groove-map phase"
            if (pianoOnsets.any { onset -> val residual = abs(note.start - onset); residual in 1..flamWindow }) {
                violations += "Generated role creates an audible piano flam"
            }
        }
    }

    private fun validateDrums(notes: List<Note>, input: GeneratedRoleValidationInput, active: List<MusicalOccurrence>, violations: MutableList<String>) {
        val kit = input.registry.resolveApprovedRole(input.project, LogicalInstrument.DRUMS)
        val kick = requireNotNull(kit.noteMap["kick"]); val snare = requireNotNull(kit.noteMap["snare"]); val closedHat = requireNotNull(kit.noteMap["closedHat"])
        val beat = input.projection.harmonyPpq * 4L / input.projection.meter.denominator
        active.forEach { occurrence ->
            val plan = input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId }.instruments.filterIsInstance<DrumsInstrumentPlan>().singleOrNull() ?: return@forEach
            val density = plan.density
            val beats = (occurrence.endTick - occurrence.startTick).toDouble() / input.projection.harmonyPpq
            val maximum = ceil(beats * (1.0 + density * input.policy.densityNotesPerBeatAtFullDensity)).toInt()
            if (notes.count { it.start in occurrence.startTick until occurrence.endTick } > maximum) violations += "Drum density exceeds approved arrangement bound"
            val occurrenceNotes = notes.filter { it.start in occurrence.startTick until occurrence.endTick }
            if (input.arrangementState != null && density > 0.0 && plan.snarePattern == SnarePattern.BEATS_2_4 && input.projection.meter.numerator == 4) {
                val backbeats = listOf(occurrence.startTick + beat, occurrence.startTick + beat * 3).filter { it < occurrence.endTick }
                val selected = backbeats.take(ceil(backbeats.size * density).toInt().coerceIn(1, backbeats.size)).map { expected ->
                    input.acceptedFullSongGrooveMap?.let { map -> FullSongGrooveMapTiming.expectedTick(map, expected) } ?: expected
                }
                if (selected.any { expected -> occurrenceNotes.none { it.pitch == snare && it.start == expected } }) {
                    violations += "Drum backbeat does not match the approved beats 2 and 4 pattern"
                }
            }
            if (input.arrangementState != null && input.acceptedFullSongGrooveMap == null && plan.swing > 0.0) {
                val swungHat = occurrenceNotes.any { note ->
                    note.pitch == closedHat && (note.start - occurrence.startTick) % beat > beat / 2
                }
                if (!swungHat) violations += "Drum swing has no delayed offbeat hi-hat"
            }
            if (input.arrangementState != null && plan.fillLastBar) {
                val finalBeat = occurrence.endTick - beat
                if (occurrenceNotes.count { it.pitch == snare && it.start >= finalBeat } < 2) violations += "Drum fill is missing from the final bar"
            }
            val bassOnsets = input.arrangementState?.track("bass")?.notes.orEmpty().map(MidiNote::startTick)
                .filter { it in occurrence.startTick until occurrence.endTick }
            if (bassOnsets.isNotEmpty() && occurrenceNotes.filter { it.pitch == kick }.none { drum -> bassOnsets.any { bass -> abs(drum.start - bass) <= beat / 4 } }) {
                violations += "Drum kick has no accepted bass onset interaction"
            }
        }
    }

    private fun validatePad(notes: List<Note>, input: GeneratedRoleValidationInput, violations: MutableList<String>) {
        val state = input.arrangementState ?: return
        val piano = state.track(ArrangementState.PIANO)?.notes.orEmpty()
        val bass = state.track("bass")?.notes.orEmpty()
        notes.forEach { pad ->
            if (piano.any { source -> source.startTick < pad.end && pad.start < source.endTick && source.pitch == pad.pitch }) {
                violations += "Pad note masks the accepted piano register"
            }
            if (bass.any { source -> source.startTick < pad.end && pad.start < source.endTick && pad.pitch <= source.pitch + 4 }) {
                violations += "Pad note overlaps the accepted bass register"
            }
        }
        notes.groupBy(Note::start).toSortedMap().values.map { group -> group.sortedBy(Note::pitch) }.zipWithNext().forEach { (left, right) ->
            left.zip(right).forEach { (before, after) -> if (abs(after.pitch - before.pitch) > 12) violations += "Pad voice leading exceeds one octave" }
        }
    }

    private fun validateStrings(notes: List<Note>, input: GeneratedRoleValidationInput, violations: MutableList<String>) {
        val state = input.arrangementState ?: return
        notes.forEach { strings ->
            if (state.melodyCollides(strings.start, strings.end, strings.pitch, 0)) {
                violations += "Strings note collides with accepted source melody"
            }
        }
        val budgets = activeOccurrences("strings", input.arrangement, input.projection.occurrences)
            .map { state.densityBudget(it.startTick, it.endTick) }
        notes.groupBy(Note::start).forEach { (tick, simultaneous) ->
            val budget = budgets.singleOrNull { tick in it.startTick until it.endTick }
            if (budget != null && simultaneous.size > budget.remaining) {
                violations += "Strings exceed the approved density budget"
            }
        }
    }

    /** Reject unreviewed cross-section octave resets while allowing a declared register change to enter or exit voices. */
    private fun validateCrossSectionVoicing(
        notes: List<Note>,
        input: GeneratedRoleValidationInput,
        active: List<MusicalOccurrence>,
        violations: MutableList<String>
    ) {
        active.zipWithNext().forEach { (outgoing, incoming) ->
            val left = notes.filter { it.start in outgoing.startTick until outgoing.endTick }.groupBy(Note::start).toSortedMap().values.lastOrNull()
                ?.map(Note::pitch)?.sorted().orEmpty()
            val right = notes.filter { it.start in incoming.startTick until incoming.endTick }.groupBy(Note::start).toSortedMap().values.firstOrNull()
                ?.map(Note::pitch)?.sorted().orEmpty()
            if (left.isEmpty() || right.isEmpty()) return@forEach
            val movement = SustainedVoicingContinuity.measure(left, right)
            if (movement.maximumVoiceMotion > 12 && !hasPlannedRegisterChange(input.role, input.arrangement, outgoing.occurrenceId, incoming.occurrenceId)) {
                violations += "Sustained role has an avoidable cross-section octave jump"
            }
        }
    }

    private fun silenceAllowed(input: GeneratedRoleValidationInput, active: List<MusicalOccurrence>): Boolean = when (input.role) {
        "bass", "drums" -> input.deliberateSilence && active.all { occurrence ->
            plannedDensity(input.role, input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId }) == 0.0
        }
        "pad" -> active.all { occurrence ->
            val plan = input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId }.instruments.filterIsInstance<PadInstrumentPlan>().single()
            plan.density == 0.0 || input.arrangementState?.ensembleSpaceMap(occurrence.startTick, occurrence.endTick)?.isDense == true
        }
        "strings" -> input.deliberateSilence && active.all { occurrence ->
            plannedDensity(input.role, input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId }) == 0.0 ||
                input.arrangementState?.densityBudget(occurrence.startTick, occurrence.endTick)?.permitsOptionalLayer == false
        }
        else -> false
    }

    private fun plannedDensity(role: String, section: DetailedArrangementSection): Double = when (role) {
        "bass" -> section.instruments.filterIsInstance<BassInstrumentPlan>().singleOrNull()?.density ?: 0.0
        "drums" -> section.instruments.filterIsInstance<DrumsInstrumentPlan>().singleOrNull()?.density ?: 0.0
        "pad" -> section.instruments.filterIsInstance<PadInstrumentPlan>().singleOrNull()?.density ?: 0.0
        "strings" -> section.instruments.filterIsInstance<StringsInstrumentPlan>().singleOrNull()?.density ?: 0.0
        else -> 0.0
    }

    private fun hasPlannedRegisterChange(role: String, arrangement: DetailedArrangement, outgoingId: String, incomingId: String): Boolean {
        fun register(sectionId: String): MusicalRegister? = arrangement.sections.single { it.instanceId == sectionId }.instruments.firstOrNull { plan ->
            plan.name == role && plan.mode == InstrumentMode.GENERATED
        }?.let { plan -> when (plan) {
            is PadInstrumentPlan -> plan.register
            is StringsInstrumentPlan -> plan.register
            else -> null
        } }
        return register(outgoingId) != register(incomingId)
    }

    private fun validateTransitions(notes: List<Note>, input: GeneratedRoleValidationInput, violations: MutableList<String>) {
        val windows = input.transitionWindows
        notes.forEach { note -> if (windows.none { note.start in it && note.end - 1 in it }) violations += "Transition note lies outside its supplied boundary window" }
    }

    private fun activeOccurrences(role: String, arrangement: DetailedArrangement, occurrences: List<MusicalOccurrence>): List<MusicalOccurrence> = occurrences.filter { occurrence ->
        arrangement.sections.single { it.instanceId == occurrence.occurrenceId }.instruments.any { it.name == role && it.mode == InstrumentMode.GENERATED }
    }

    private fun metadataMatches(sequence: Sequence, input: GeneratedRoleValidationInput): Boolean {
        val meta = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.mapNotNull { it.message as? MetaMessage }
        val tempoOk = meta.filter { it.type == 0x51 }.all { message -> message.data.size == 3 && abs(tempo(message) - input.projection.tempo.bpm) <= 0.001 }
        val meterOk = meta.filter { it.type == 0x58 }.all { message -> message.data.size >= 2 && (message.data[0].toInt() and 255) == input.projection.meter.numerator && (1 shl (message.data[1].toInt() and 255)) == input.projection.meter.denominator }
        return tempoOk && meterOk && meta.any { it.type == 0x51 } && meta.any { it.type == 0x58 }
    }

    private fun readNotes(sequence: Sequence, violations: MutableList<String>): List<Note> {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val notes = mutableListOf<Note>()
        sequence.tracks.flatMapIndexed { trackIndex, track -> (0 until track.size()).map { index -> Indexed(track[index], trackIndex, index) } }
            .sortedWith(compareBy<Indexed> { it.event.tick }.thenBy { priority(it.event.message as? ShortMessage) }.thenBy { it.track }.thenBy { it.index }).forEach { indexed ->
                val message = indexed.event.message as? ShortMessage ?: return@forEach
                val key = message.channel to message.data1
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(key) { ArrayDeque() }.addLast(indexed.event.tick to message.data2)
                else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                    val start = active[key]?.removeFirstOrNull()
                    if (start == null) violations += "Unmatched note-off event" else notes += Note(message.channel, message.data1, start.second, start.first, indexed.event.tick)
                }
            }
        if (active.values.any { it.isNotEmpty() }) violations += "Unmatched note-on event"
        return notes.sortedWith(compareBy<Note> { it.start }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.end }.thenBy { it.velocity })
    }

    private fun report(input: GeneratedRoleValidationInput, notes: List<Note>, rawViolations: List<String>, rawWarnings: List<String>): RoleValidationReport {
        val violations = rawViolations.distinct().sorted().take(input.policy.maximumViolations)
        val warnings = rawWarnings.distinct().sorted().take(input.policy.maximumWarnings)
        val active = activeOccurrences(input.role, input.arrangement, input.projection.occurrences)
        val densityMetrics = if (input.role == "strings" && input.arrangementState != null) {
            val budgets = activeOccurrences("strings", input.arrangement, input.projection.occurrences)
                .map { input.arrangementState.densityBudget(it.startTick, it.endTick) }
            listOf(
                RoleValidationMetric("densityBudgetCapacity", budgets.maxOfOrNull(DensityBudget::capacity)?.toLong() ?: 0),
                RoleValidationMetric("densityBudgetOccupied", budgets.maxOfOrNull(DensityBudget::occupied)?.toLong() ?: 0),
                RoleValidationMetric("densityBudgetRemaining", budgets.minOfOrNull(DensityBudget::remaining)?.toLong() ?: 0)
            )
        } else emptyList()
        val voicingMetrics = if (input.role in setOf("pad", "strings")) {
            val groups = notes.groupBy(Note::start).toSortedMap().values.map { group -> group.map(Note::pitch).sorted() }
            val movements = groups.zipWithNext().map { (left, right) -> SustainedVoicingContinuity.measure(left, right) }
            listOf(
                RoleValidationMetric("voicingCommonToneCount", movements.sumOf(SustainedVoicingMovement::commonToneCount).toLong()),
                RoleValidationMetric("voicingMovementTotal", movements.sumOf(SustainedVoicingMovement::totalSemitoneMotion))
            )
        } else emptyList()
        val roleMetrics = listOf(
            RoleValidationMetric("activeOccurrenceCount", active.size.toLong()),
            RoleValidationMetric("noteCount", notes.size.toLong()),
            RoleValidationMetric("noteOnsetCount", notes.map(Note::start).distinct().size.toLong()),
            RoleValidationMetric("ppq", runCatching { MidiSystem.getSequence(input.midi.toFile()).resolution.toLong() }.getOrDefault(0))
        )
        val acceptedStateMetrics = input.arrangementState?.let { state ->
            listOf(
                RoleValidationMetric("acceptedTrackCount", state.acceptedTracks.size.toLong()),
                RoleValidationMetric("acceptedNoteCount", state.fullAcceptedMidi().size.toLong()),
                RoleValidationMetric("acceptedPianoOnsetCount", state.requireTrack(ArrangementState.PIANO).notes.map(MidiNote::startTick).distinct().size.toLong())
            )
        }.orEmpty()
        return RoleValidationReport(
            role = input.role, target = RoleValidationTarget(occurrenceIds = active.map { it.occurrenceId }.sorted()),
            inputHashes = (listOf(RoleValidationHash("arrangement", input.arrangementSha256), RoleValidationHash("authority", input.projection.contextSha256), RoleValidationHash("registry", input.registrySha256)) +
                input.acceptedFullSongGrooveMap?.let { listOf(RoleValidationHash("grooveMap", grooveMapDigest(it))) }.orEmpty()).sortedBy(RoleValidationHash::name),
            outputSha256 = digest(input.midi), policyVersion = input.policy.version,
            metrics = (roleMetrics + densityMetrics + voicingMetrics).sortedBy(RoleValidationMetric::name),
            warnings = warnings, violations = violations, passed = violations.isEmpty(),
            acceptedStateMetrics = acceptedStateMetrics.sortedBy(RoleValidationMetric::name),
            kickTimingEvidence = kickTimingEvidence(input, notes, active),
            instrumentMapEvidence = instrumentMapEvidence(input)
        )
    }

    /** Persist the named registry map even when a role has no percussion notes, keeping later consumers role-aware. */
    private fun instrumentMapEvidence(input: GeneratedRoleValidationInput): RoleInstrumentMapEvidence? {
        if (input.role == "transitions") return null
        val descriptor = input.registry.resolveApprovedRole(input.project, LogicalInstrument.parse(input.role))
        return RoleInstrumentMapEvidence(
            role = input.role,
            midiChannel = descriptor.midiChannelZeroBased,
            notes = descriptor.noteMap.entries.sortedBy { it.key }.map { (name, pitch) -> RoleInstrumentMapNote(name, pitch) }
        )
    }

    /** Retain kick timing only for drum reports; it is evidence for later mixing, not a DSP instruction. */
    private fun kickTimingEvidence(input: GeneratedRoleValidationInput, notes: List<Note>, active: List<MusicalOccurrence>): KickTimingEvidence? {
        if (input.role != "drums") return null
        val kit = input.registry.resolveApprovedRole(input.project, LogicalInstrument.DRUMS)
        val channel = requireNotNull(kit.midiChannelZeroBased)
        val kick = requireNotNull(kit.noteMap["kick"])
        val map = input.acceptedFullSongGrooveMap
        val attacks = notes.filter { it.channel == channel && it.pitch == kick }.map(Note::start).sorted()
        val residuals = attacks.map { attack ->
            val occurrence = active.singleOrNull { attack in it.startTick until it.endTick }
            val expected = occurrence?.let { map?.let { source -> FullSongGrooveMapTiming.nearestExpectedTick(source, it.occurrenceId, attack) } }
            if (expected == null) 0L else abs(attack - expected)
        }
        return KickTimingEvidence(channel, kick, attacks, residuals)
    }

    private data class Note(val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private data class Indexed(val event: MidiEvent, val track: Int, val index: Int)
    private fun priority(message: ShortMessage?) = if (message?.command == ShortMessage.NOTE_OFF || message?.command == ShortMessage.NOTE_ON && message.data2 == 0) 0 else 1
    private fun tempo(message: MetaMessage): Double { val d = message.data; val micros = ((d[0].toInt() and 255) shl 16) or ((d[1].toInt() and 255) shl 8) or (d[2].toInt() and 255); return 60_000_000.0 / micros }
    private fun chordTones(entry: HarmonicTimelineEntry): Set<Int> = entry.chord.quality.intervals
        .map { (it + entry.chord.rootChromatic) % 12 }.toSet()
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun grooveMapDigest(map: FullSongGrooveMap): String = MessageDigest.getInstance("SHA-256")
        .digest(Json { encodeDefaults = true }.encodeToString(FullSongGrooveMap.serializer(), map).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun Long?.orEmpty() = this ?: 0L
}

private class ScopedGeneratedRoleValidator(private val role: String) : GeneratedRoleValidator {
    private val delegate = DeterministicGeneratedRoleValidator()
    override fun validate(input: GeneratedRoleValidationInput): RoleValidationReport {
        require(input.role == role) { "Expected generated role '$role', got '${input.role}'" }
        return delegate.validate(input)
    }
}

class PianoGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("piano")
class BassGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("bass")
class DrumsGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("drums")
class PadGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("pad")
class StringsGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("strings")
class TransitionsGeneratedRoleValidator : GeneratedRoleValidator by ScopedGeneratedRoleValidator("transitions")

/** Closed role dispatch; no generated role can bypass its typed validation boundary. */
object GeneratedRoleValidators : GeneratedRoleValidator {
    private val byRole = mapOf(
        "piano" to PianoGeneratedRoleValidator(), "bass" to BassGeneratedRoleValidator(), "drums" to DrumsGeneratedRoleValidator(),
        "pad" to PadGeneratedRoleValidator(), "strings" to StringsGeneratedRoleValidator(), "transitions" to TransitionsGeneratedRoleValidator()
    )
    override fun validate(input: GeneratedRoleValidationInput): RoleValidationReport = requireNotNull(byRole[input.role]) {
        "Unsupported generated role '${input.role}'"
    }.validate(input)
}
