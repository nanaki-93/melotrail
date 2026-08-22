package app.melotrail.arrangement

import app.melotrail.application.ArrangementGenerationProjection
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import kotlinx.serialization.Serializable
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
    val maximumWarnings: Int = 64,
    val maximumViolations: Int = 128
) {
    init {
        require(version == CURRENT_VERSION && sustainedMinimumBeats == 0.5 && densityNotesPerBeatAtFullDensity == 16.0) {
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
    val passed: Boolean
) {
    init {
        require(version == 1 && role in SUPPORTED_ROLES && SHA.matches(outputSha256) && policyVersion == RoleValidationPolicy.CURRENT_VERSION)
        require(inputHashes == inputHashes.sortedBy(RoleValidationHash::name) && metrics == metrics.sortedBy(RoleValidationMetric::name))
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
    val policy: RoleValidationPolicy = RoleValidationPolicy(),
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
        if (active.isNotEmpty() && notes.isEmpty()) violations += "Activated role has no notes"
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
        when (input.role) {
            "piano", "pad", "strings" -> validateSustainedHarmony(notes, input, violations)
            "bass" -> validateBass(notes, input, warnings, violations)
            "drums" -> validateDrums(notes, input, active, violations)
        }
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

    private fun validateDrums(notes: List<Note>, input: GeneratedRoleValidationInput, active: List<MusicalOccurrence>, violations: MutableList<String>) {
        active.forEach { occurrence ->
            val density = input.arrangement.sections.single { it.instanceId == occurrence.occurrenceId }.instruments.filterIsInstance<DrumsInstrumentPlan>().singleOrNull()?.density ?: return@forEach
            val beats = (occurrence.endTick - occurrence.startTick).toDouble() / input.projection.harmonyPpq
            val maximum = ceil(beats * (1.0 + density * input.policy.densityNotesPerBeatAtFullDensity)).toInt()
            if (notes.count { it.start in occurrence.startTick until occurrence.endTick } > maximum) violations += "Drum density exceeds approved arrangement bound"
        }
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
        return RoleValidationReport(
            role = input.role, target = RoleValidationTarget(occurrenceIds = activeOccurrences(input.role, input.arrangement, input.projection.occurrences).map { it.occurrenceId }.sorted()),
            inputHashes = listOf(RoleValidationHash("arrangement", input.arrangementSha256), RoleValidationHash("authority", input.projection.contextSha256), RoleValidationHash("registry", input.registrySha256)).sortedBy(RoleValidationHash::name),
            outputSha256 = digest(input.midi), policyVersion = input.policy.version,
            metrics = listOf(RoleValidationMetric("noteCount", notes.size.toLong()), RoleValidationMetric("ppq", runCatching { MidiSystem.getSequence(input.midi.toFile()).resolution.toLong() }.getOrDefault(0))).sortedBy(RoleValidationMetric::name),
            warnings = warnings, violations = violations, passed = violations.isEmpty()
        )
    }

    private data class Note(val channel: Int, val pitch: Int, val velocity: Int, val start: Long, val end: Long)
    private data class Indexed(val event: MidiEvent, val track: Int, val index: Int)
    private fun priority(message: ShortMessage?) = if (message?.command == ShortMessage.NOTE_OFF || message?.command == ShortMessage.NOTE_ON && message.data2 == 0) 0 else 1
    private fun tempo(message: MetaMessage): Double { val d = message.data; val micros = ((d[0].toInt() and 255) shl 16) or ((d[1].toInt() and 255) shl 8) or (d[2].toInt() and 255); return 60_000_000.0 / micros }
    private fun chordTones(entry: HarmonicTimelineEntry): Set<Int> = entry.chord.quality.intervals
        .map { (it + entry.chord.rootChromatic) % 12 }.toSet()
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
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
