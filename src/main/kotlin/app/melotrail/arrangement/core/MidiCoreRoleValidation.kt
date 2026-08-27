package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import kotlin.math.ceil
import kotlin.math.abs
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Severity used by the target role-validation report. */
enum class MidiCoreRoleFindingSeverity {
    BLOCKING,
    ADVISORY,
}

/** Stable typed policy findings returned before candidate publication. */
enum class MidiCoreRoleFindingCode {
    ROLE_MISMATCH,
    OCCURRENCE_MISMATCH,
    WRONG_CHANNEL,
    UNSUPPORTED_EVENT,
    EMPTY_OUTPUT,
    INVALID_TIMING,
    NON_POSITIVE_DURATION,
    OUTSIDE_OCCURRENCE,
    UNREPRESENTABLE_TICK,
    OUTSIDE_REGISTER,
    INVALID_VELOCITY,
    INVALID_DRUM_PITCH,
    DUPLICATE_NOTE,
    HARMONY_MISMATCH,
    PROTECTED_ANCHOR_COLLISION,
    MELODY_COLLISION,
    DENSITY_EXCEEDED,
}

/** One deterministic finding attached to a generated role event or candidate. */
data class MidiCoreRoleFinding(
    val code: MidiCoreRoleFindingCode,
    val severity: MidiCoreRoleFindingSeverity,
    val role: CandidateRole,
    val occurrenceId: String,
    val tick: Long?,
    val pitch: Int?,
    val message: String,
) {
    init {
        require(occurrenceId.isNotBlank() && message.isNotBlank()) { "Role finding identity and message must not be blank" }
        require(tick == null || tick >= 0) { "Role finding tick must not be negative" }
    }
}

/** Read-only semantic note event accepted as the only generated target event type. */
sealed interface MidiCoreCandidateEvent {
    val startTick: Long
    val endTick: Long

    /** Raw note values deliberately remain constructible so validation can report malformed output. */
    data class Note(
        override val startTick: Long,
        override val endTick: Long,
        val pitch: Int,
        val velocity: Int,
    ) : MidiCoreCandidateEvent

    /** Testable representation of any event type forbidden in generated role candidates. */
    data class Unsupported(
        val eventType: String,
        override val startTick: Long,
    ) : MidiCoreCandidateEvent {
        override val endTick: Long get() = startTick
    }
}

/** In-memory candidate envelope passed to validation; it contains no path or mutable project state. */
data class MidiCoreRoleCandidate(
    val role: CandidateRole,
    val occurrenceId: String,
    val channel: Int,
    val events: List<MidiCoreCandidateEvent>,
)

/** Stable validation evidence for one role/occurrence candidate. */
data class MidiCoreRoleValidationReport(
    val contextSha256: String,
    val candidateSha256: String,
    val role: CandidateRole,
    val occurrenceId: String,
    val noteCount: Int,
    val findings: List<MidiCoreRoleFinding>,
) {
    init {
        require(contextSha256.matches(SHA_256) && candidateSha256.matches(SHA_256)) {
            "Role validation hashes must be lowercase SHA-256 values"
        }
        require(noteCount >= 0 && findings == findings.sortedWith(findingOrder())) {
            "Role validation report must use deterministic evidence order"
        }
    }

    /** True only when no blocking finding remains. */
    val passed: Boolean get() = findings.none { it.severity == MidiCoreRoleFindingSeverity.BLOCKING }

    /** Blocking findings exposed for a concise rejection message. */
    val blockers: List<MidiCoreRoleFinding> get() = findings.filter { it.severity == MidiCoreRoleFindingSeverity.BLOCKING }
}

/** Typed result that prevents a rejected candidate from reaching publication code. */
sealed interface MidiCoreRoleValidationResult {
    val report: MidiCoreRoleValidationReport

    data class Accepted(override val report: MidiCoreRoleValidationReport) : MidiCoreRoleValidationResult
    data class Rejected(override val report: MidiCoreRoleValidationReport) : MidiCoreRoleValidationResult
}

/** Common and role-specific validation for semantic Chords, Bass, and Drums candidates. */
object MidiCoreRoleValidator {
    /** Validate an in-memory candidate against its one immutable generation context. */
    fun validate(context: MidiCoreGenerationContext, candidate: MidiCoreRoleCandidate): MidiCoreRoleValidationResult {
        val findings = mutableListOf<MidiCoreRoleFinding>()
        if (candidate.role != context.role) findings += finding(
            MidiCoreRoleFindingCode.ROLE_MISMATCH,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            message = "Candidate role does not match the generation context.",
        )
        if (candidate.occurrenceId != context.occurrence.id) findings += finding(
            MidiCoreRoleFindingCode.OCCURRENCE_MISMATCH,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            message = "Candidate occurrence does not match the generation context.",
        )
        expectedChannel(context.role).takeIf { it != candidate.channel }?.let { expected ->
            findings += finding(
                MidiCoreRoleFindingCode.WRONG_CHANNEL,
                MidiCoreRoleFindingSeverity.BLOCKING,
                context,
                message = "${context.role.name.lowercase()} candidates must use MIDI channel $expected.",
            )
        }

        val notes = candidate.events.mapNotNull { event ->
            when (event) {
                is MidiCoreCandidateEvent.Note -> event
                is MidiCoreCandidateEvent.Unsupported -> {
                    findings += finding(
                        MidiCoreRoleFindingCode.UNSUPPORTED_EVENT,
                        MidiCoreRoleFindingSeverity.BLOCKING,
                        context,
                        event.startTick.takeIf { it >= 0 },
                        message = "Generated roles may contain note events only; '${event.eventType}' is not allowed.",
                    )
                    null
                }
            }
        }

        if (notes.isEmpty() && context.sectionPolicy.density > 0.0) findings += finding(
            MidiCoreRoleFindingCode.EMPTY_OUTPUT,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            message = "A non-silent role selection produced no note events.",
        )
        notes.forEach { note -> validateCommon(context, note, findings) }
        validateDuplicates(context, notes, findings)
        validateDensity(context, notes, findings)
        if (candidate.role in setOf(CandidateRole.CHORDS, CandidateRole.BASS)) {
            notes.forEach { note -> validateHarmony(context, note, findings) }
        }
        notes.forEach { note -> validateMelodySpace(context, note, findings) }

        val report = MidiCoreRoleValidationReport(
            contextSha256 = context.contextSha256,
            candidateSha256 = candidateDigest(candidate),
            role = context.role,
            occurrenceId = context.occurrence.id,
            noteCount = notes.size,
            findings = findings.distinctBy { listOf(it.code, it.severity, it.tick, it.pitch, it.message) }
                .sortedWith(findingOrder()),
        )
        return if (report.passed) MidiCoreRoleValidationResult.Accepted(report) else MidiCoreRoleValidationResult.Rejected(report)
    }

    /** Validate notes produced by a generator without making it construct an event envelope. */
    fun validate(
        context: MidiCoreGenerationContext,
        notes: List<MidiCoreCandidateEvent.Note>,
        channel: Int = expectedChannel(context.role),
    ): MidiCoreRoleValidationResult = validate(
        context,
        MidiCoreRoleCandidate(context.role, context.occurrence.id, channel, notes),
    )

    /** Apply timing, grid, register, velocity, and role-specific drum checks to one note. */
    private fun validateCommon(
        context: MidiCoreGenerationContext,
        note: MidiCoreCandidateEvent.Note,
        findings: MutableList<MidiCoreRoleFinding>,
    ) {
        if (note.startTick < 0 || note.endTick < 0) findings += finding(
            MidiCoreRoleFindingCode.INVALID_TIMING,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated note timing must not be negative.",
        )
        if (note.endTick <= note.startTick) findings += finding(
            MidiCoreRoleFindingCode.NON_POSITIVE_DURATION,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated note duration must be positive.",
        )
        if (note.startTick < context.occurrence.startTick || note.endTick > context.occurrence.endTick) findings += finding(
            MidiCoreRoleFindingCode.OUTSIDE_OCCURRENCE,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated note must remain inside the selected occurrence.",
        )
        if (note.startTick >= 0 && note.endTick >= 0) {
            listOf(note.startTick, note.endTick).forEach { tick ->
                if (tick % context.tickGrid.ticksPerSubdivision != 0L) findings += finding(
                    MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK,
                    MidiCoreRoleFindingSeverity.BLOCKING,
                    context,
                    tick,
                    note.pitch,
                    "Generated note tick is not representable on the shared MIDI Core grid.",
                )
            }
        }
        if (note.pitch !in context.performanceProfile.register) findings += finding(
            MidiCoreRoleFindingCode.OUTSIDE_REGISTER,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated note is outside the selected MIDI performance register.",
        )
        if (note.velocity !in 1..127) findings += finding(
            MidiCoreRoleFindingCode.INVALID_VELOCITY,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated note velocity must be from 1 through 127.",
        )
        if (context.role == CandidateRole.DRUMS && note.pitch !in DRUM_PITCHES) findings += finding(
            MidiCoreRoleFindingCode.INVALID_DRUM_PITCH,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick.takeIf { it >= 0 },
            note.pitch,
            "Generated drums must use the curated GM starter pitches.",
        )
    }

    /** Reject exact repeated note values while retaining one deterministic finding. */
    private fun validateDuplicates(
        context: MidiCoreGenerationContext,
        notes: List<MidiCoreCandidateEvent.Note>,
        findings: MutableList<MidiCoreRoleFinding>,
    ) {
        notes.groupBy { NoteKey(it.startTick, it.endTick, it.pitch, it.velocity) }.filterValues { it.size > 1 }.forEach { (key, _) ->
            findings += finding(
                MidiCoreRoleFindingCode.DUPLICATE_NOTE,
                MidiCoreRoleFindingSeverity.BLOCKING,
                context,
                key.startTick.takeIf { it >= 0 },
                key.pitch,
                "Generated role contains an exact duplicate note.",
            )
        }
    }

    /** Enforce the role and section density ceiling without deleting generated events. */
    private fun validateDensity(
        context: MidiCoreGenerationContext,
        notes: List<MidiCoreCandidateEvent.Note>,
        findings: MutableList<MidiCoreRoleFinding>,
    ) {
        val beats = (context.occurrence.endTick - context.occurrence.startTick).toDouble() / context.tickGrid.ticksPerQuarter
        val ceilingPerBeat = when (context.role) {
            CandidateRole.CHORDS -> 4.0
            CandidateRole.BASS -> 2.0
            CandidateRole.DRUMS -> 8.0
        }
        val maximum = ceil(beats * ceilingPerBeat * context.sectionPolicy.density).toInt()
        if (notes.size > maximum) findings += finding(
            MidiCoreRoleFindingCode.DENSITY_EXCEEDED,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            context.occurrence.startTick,
            message = "Generated role exceeds the approved occurrence density budget.",
        )
    }

    /** Require every pitched note to be justified by the exact chord windows it occupies. */
    private fun validateHarmony(
        context: MidiCoreGenerationContext,
        note: MidiCoreCandidateEvent.Note,
        findings: MutableList<MidiCoreRoleFinding>,
    ) {
        val windows = context.chordWindows.filter { it.startTick < note.endTick && note.startTick < it.endTick }
        if (windows.isEmpty()) return
        val harmonic = windows.all { it.chord.containsPitchClass(note.pitch) }
        val approach = context.role == CandidateRole.BASS && note.endTick - note.startTick <= context.tickGrid.ticksPerSubdivision &&
            windows.drop(1).firstOrNull()?.chord?.containsPitchClass(note.pitch) == true
        if (!harmonic && !approach) findings += finding(
            MidiCoreRoleFindingCode.HARMONY_MISMATCH,
            MidiCoreRoleFindingSeverity.BLOCKING,
            context,
            note.startTick,
            note.pitch,
            "Generated pitched note is not contained by every authoritative chord window it occupies.",
        )
    }

    /** Classify protected-anchor collisions as blocking and close non-anchor proximity as advisory. */
    private fun validateMelodySpace(
        context: MidiCoreGenerationContext,
        note: MidiCoreCandidateEvent.Note,
        findings: MutableList<MidiCoreRoleFinding>,
    ) {
        context.protectedMelodyNotes.filter { it.overlaps(note.startTick, note.endTick) }.forEach { melody ->
            val distance = abs(melody.pitch - note.pitch)
            if (distance == 0 && melody.anchor) findings += finding(
                MidiCoreRoleFindingCode.PROTECTED_ANCHOR_COLLISION,
                MidiCoreRoleFindingSeverity.BLOCKING,
                context,
                note.startTick.takeIf { it >= 0 },
                note.pitch,
                "Generated note collides with a protected melody anchor.",
            ) else if (distance <= 2) findings += finding(
                MidiCoreRoleFindingCode.MELODY_COLLISION,
                MidiCoreRoleFindingSeverity.ADVISORY,
                context,
                note.startTick.takeIf { it >= 0 },
                note.pitch,
                "Generated note occupies the protected melody's close pitch space.",
            )
        }
    }

    /** Build a scope-bound finding and normalize malformed negative event ticks to absent evidence. */
    private fun finding(
        code: MidiCoreRoleFindingCode,
        severity: MidiCoreRoleFindingSeverity,
        context: MidiCoreGenerationContext,
        tick: Long? = null,
        pitch: Int? = null,
        message: String,
    ) = MidiCoreRoleFinding(code, severity, context.role, context.occurrence.id, tick?.takeIf { it >= 0 }, pitch, message)

    /** Hash candidate semantics in an event-order-independent canonical representation. */
    private fun candidateDigest(candidate: MidiCoreRoleCandidate): String = sha256(canonicalRecord(
        "candidate",
        listOf(
            "role" to candidate.role.name,
            "occurrence" to candidate.occurrenceId,
            "channel" to candidate.channel.toString(),
            "events" to candidate.events.sortedBy(::canonicalEvent).joinToString(";", transform = ::canonicalEvent),
        ),
    ))
}

private data class NoteKey(val startTick: Long, val endTick: Long, val pitch: Int, val velocity: Int)

/** Serialize one candidate event for deterministic digest ordering. */
private fun canonicalEvent(event: MidiCoreCandidateEvent): String = when (event) {
    is MidiCoreCandidateEvent.Note -> listOf("note", event.startTick, event.endTick, event.pitch, event.velocity).joinToString("|")
    is MidiCoreCandidateEvent.Unsupported -> listOf("unsupported", event.eventType, event.startTick).joinToString("|")
}

/** Compatibility name for callers that think of the boundary as candidate validation. */
object MidiCoreCandidateValidator {
    /** Validate one candidate through the shared target role validator. */
    fun validate(context: MidiCoreGenerationContext, candidate: MidiCoreRoleCandidate): MidiCoreRoleValidationResult =
        MidiCoreRoleValidator.validate(context, candidate)
}

/** Stable JSON boundary for validation evidence stored beside an immutable candidate. */
object MidiCoreRoleValidationReportJson {
    const val SCHEMA = "melotrail-midi-core-role-validation"
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    /** Encode the complete deterministic report without exposing persistence DTOs to callers. */
    fun encode(report: MidiCoreRoleValidationReport): String = json.encodeToString(
        ReportDto(
            schema = SCHEMA,
            version = VERSION,
            contextSha256 = report.contextSha256,
            candidateSha256 = report.candidateSha256,
            role = report.role.name,
            occurrenceId = report.occurrenceId,
            noteCount = report.noteCount,
            passed = report.passed,
            findings = report.findings.map { finding ->
                FindingDto(
                    code = finding.code.name,
                    severity = finding.severity.name,
                    role = finding.role.name,
                    occurrenceId = finding.occurrenceId,
                    tick = finding.tick,
                    pitch = finding.pitch,
                    message = finding.message,
                )
            },
        ),
    )

    /** Decode and validate persisted report evidence before review or export uses it. */
    fun decode(text: String): MidiCoreRoleValidationReport {
        val dto = json.decodeFromString<ReportDto>(text)
        require(dto.schema == SCHEMA && dto.version == VERSION) { "Unsupported MIDI Core role-validation report" }
        val report = MidiCoreRoleValidationReport(
            contextSha256 = dto.contextSha256,
            candidateSha256 = dto.candidateSha256,
            role = enumValue(dto.role, "role"),
            occurrenceId = dto.occurrenceId,
            noteCount = dto.noteCount,
            findings = dto.findings.map { finding ->
                MidiCoreRoleFinding(
                    code = enumValue(finding.code, "finding code"),
                    severity = enumValue(finding.severity, "finding severity"),
                    role = enumValue(finding.role, "finding role"),
                    occurrenceId = finding.occurrenceId,
                    tick = finding.tick,
                    pitch = finding.pitch,
                    message = finding.message,
                )
            },
        )
        require(dto.passed == report.passed) { "MIDI Core role-validation pass state is inconsistent" }
        return report
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T = try {
        enumValueOf<T>(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Unknown MIDI Core $label '$value'", error)
    }
}

@Serializable
private data class ReportDto(
    val schema: String,
    val version: Int,
    val contextSha256: String,
    val candidateSha256: String,
    val role: String,
    val occurrenceId: String,
    val noteCount: Int,
    val passed: Boolean,
    val findings: List<FindingDto>,
)

@Serializable
private data class FindingDto(
    val code: String,
    val severity: String,
    val role: String,
    val occurrenceId: String,
    val tick: Long? = null,
    val pitch: Int? = null,
    val message: String,
)

private val SHA_256 = Regex("[0-9a-f]{64}")
private val DRUM_PITCHES = setOf(36, 38, 42, 46)

/** Return the stable ordering used for every persisted validation finding. */
private fun findingOrder() = compareBy<MidiCoreRoleFinding> { it.tick ?: Long.MAX_VALUE }
    .thenBy { it.pitch ?: Int.MAX_VALUE }
    .thenBy { it.code.ordinal }
    .thenBy { it.severity.ordinal }
    .thenBy(MidiCoreRoleFinding::message)

/** Encode named fields with lengths so canonical validation records cannot be ambiguous. */
private fun canonicalRecord(type: String, fields: List<Pair<String, String>>): String = buildString {
    append(type)
    fields.forEach { (name, value) -> append('|').append(name).append('=').append(value.length).append(':').append(value) }
}

/** Hash one canonical validation record with the project-wide lowercase SHA-256 policy. */
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

/** Resolve the fixed zero-based MIDI channel required by each generated role. */
private fun expectedChannel(role: CandidateRole): Int = when (role) {
    CandidateRole.CHORDS -> 1
    CandidateRole.BASS -> 2
    CandidateRole.DRUMS -> 9
}
