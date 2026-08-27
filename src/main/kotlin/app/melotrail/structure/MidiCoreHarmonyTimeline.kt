package app.melotrail.structure

import app.melotrail.music.core.MidiCoreChordSymbol
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectAuthority

enum class MidiCoreHarmonyFindingSeverity { BLOCKING, ADVISORY }

enum class MidiCoreHarmonyFindingCode {
    NO_OCCURRENCES,
    MISSING_CHORD_WINDOW,
    INVALID_CHORD_SYMBOL,
    UNKNOWN_OCCURRENCE,
    CHORD_OUTSIDE_OCCURRENCE,
    CHORD_WINDOW_GAP,
    CHORD_WINDOW_OVERLAP,
    CHORD_EVENT_ORDER,
    DUPLICATE_CHORD_EVENT,
    CHROMATIC_CHORD,
}

data class MidiCoreHarmonyFinding(
    val code: MidiCoreHarmonyFindingCode,
    val severity: MidiCoreHarmonyFindingSeverity,
    val occurrenceId: String? = null,
    val chordEventId: String? = null,
    val tick: Long? = null,
    val message: String,
    val action: String,
) {
    init {
        require(message.isNotBlank() && action.isNotBlank()) { "Harmony finding text must not be blank" }
        require(tick == null || tick >= 0) { "Harmony finding tick must not be negative" }
    }
}

data class MidiCoreResolvedChordWindow(
    val event: AuthoritativeChordEvent,
    val chord: MidiCoreChordSymbol,
) {
    val startTick: Long get() = event.startTick
    val endTick: Long get() = event.endTick
    val durationTicks: Long get() = endTick - startTick
}

data class MidiCoreHarmonyValidation(
    val findings: List<MidiCoreHarmonyFinding>,
    val windows: List<MidiCoreResolvedChordWindow>,
) {
    val valid: Boolean get() = findings.none { it.severity == MidiCoreHarmonyFindingSeverity.BLOCKING }
    val hasAdvisories: Boolean get() = findings.any { it.severity == MidiCoreHarmonyFindingSeverity.ADVISORY }
}

/** Validates explicit chord windows and exposes only the resolved authority to generators. */
object MidiCoreHarmonyValidator {
    fun validate(authority: ProjectAuthority, events: List<AuthoritativeChordEvent> = authority.chordEvents): MidiCoreHarmonyValidation {
        val findings = mutableListOf<MidiCoreHarmonyFinding>()
        val occurrences = authority.occurrences.associateBy { it.id }
        if (authority.occurrences.isEmpty()) {
            findings += finding(MidiCoreHarmonyFindingCode.NO_OCCURRENCES, "There are no section occurrences to receive authoritative harmony.", "Define at least one section occurrence first.")
        }

        events.groupBy(AuthoritativeChordEvent::id).filterValues { it.size > 1 }.keys.sorted().forEach { id ->
            findings += finding(MidiCoreHarmonyFindingCode.DUPLICATE_CHORD_EVENT, "A chord event ID is repeated: '$id'.", "Give every chord window a unique stable ID.", chordEventId = id)
        }

        val resolved = events.mapNotNull { event ->
            val occurrence = occurrences[event.occurrenceId]
            if (occurrence == null) {
                findings += finding(
                    MidiCoreHarmonyFindingCode.UNKNOWN_OCCURRENCE,
                    "Chord event '${event.id}' refers to unknown occurrence '${event.occurrenceId}'.",
                    "Choose an occurrence from the saved structure.",
                    occurrenceId = event.occurrenceId,
                    chordEventId = event.id,
                    tick = event.startTick,
                )
                return@mapNotNull null
            }
            if (event.startTick < occurrence.startTick || event.endTick > occurrence.endTick) {
                findings += finding(
                    MidiCoreHarmonyFindingCode.CHORD_OUTSIDE_OCCURRENCE,
                    "Chord '${event.id}' lies outside occurrence '${occurrence.id}'.",
                    "Set the chord start and duration inside the occurrence boundary.",
                    occurrenceId = occurrence.id,
                    chordEventId = event.id,
                    tick = event.startTick,
                )
            }
            val chord = MidiCoreChordSymbol.parse(event.symbol)
            if (chord == null) {
                findings += finding(
                    MidiCoreHarmonyFindingCode.INVALID_CHORD_SYMBOL,
                    "Chord '${event.symbol}' cannot be realized by MIDI Core V1.",
                    "Use a supported root, quality, extension, and optional slash bass.",
                    occurrenceId = occurrence.id,
                    chordEventId = event.id,
                    tick = event.startTick,
                )
                return@mapNotNull null
            }
            if (chord.pitchClasses.any { it !in authority.key.advisoryPitchClasses }) {
                findings += finding(
                    MidiCoreHarmonyFindingCode.CHROMATIC_CHORD,
                    "Chord '${event.symbol}' contains tones outside the advisory project key; it remains authoritative.",
                    "Review the key compatibility advisory; do not replace this approved chord automatically.",
                    severity = MidiCoreHarmonyFindingSeverity.ADVISORY,
                    occurrenceId = occurrence.id,
                    chordEventId = event.id,
                    tick = event.startTick,
                )
            }
            MidiCoreResolvedChordWindow(event, chord)
        }

        authority.occurrences.forEach { occurrence ->
            val windows = resolved.filter { it.event.occurrenceId == occurrence.id && it.startTick >= occurrence.startTick && it.endTick <= occurrence.endTick }
                .sortedWith(compareBy<MidiCoreResolvedChordWindow> { it.startTick }.thenBy { it.event.id })
            if (windows.isEmpty()) {
                findings += finding(
                    MidiCoreHarmonyFindingCode.MISSING_CHORD_WINDOW,
                    "Occurrence '${occurrence.id}' has no complete authoritative chord coverage.",
                    "Add chord events whose durations cover the entire occurrence.",
                    occurrenceId = occurrence.id,
                    tick = occurrence.startTick,
                )
            } else {
                var cursor = occurrence.startTick
                windows.forEach { window ->
                    when {
                        window.startTick < cursor -> findings += finding(
                            MidiCoreHarmonyFindingCode.CHORD_WINDOW_OVERLAP,
                            "Chord windows overlap in occurrence '${occurrence.id}'.",
                            "End the preceding chord exactly where this chord begins.",
                            occurrenceId = occurrence.id,
                            chordEventId = window.event.id,
                            tick = window.startTick,
                        )
                        window.startTick > cursor -> findings += finding(
                            MidiCoreHarmonyFindingCode.CHORD_WINDOW_GAP,
                            "Chord coverage stops before tick ${window.startTick} in occurrence '${occurrence.id}'.",
                            "Add or extend an authoritative chord to cover every tick.",
                            occurrenceId = occurrence.id,
                            chordEventId = window.event.id,
                            tick = cursor,
                        )
                    }
                    if (window.startTick >= cursor) cursor = window.endTick
                }
                if (cursor < occurrence.endTick) {
                    findings += finding(
                        MidiCoreHarmonyFindingCode.CHORD_WINDOW_GAP,
                        "Chord coverage stops at tick $cursor before occurrence '${occurrence.id}' ends.",
                        "Extend the final chord to the occurrence end tick.",
                        occurrenceId = occurrence.id,
                        tick = cursor,
                    )
                }
            }
        }

        val expectedOrder = events.sortedWith(compareBy<AuthoritativeChordEvent> { occurrences[it.occurrenceId]?.startTick ?: Long.MAX_VALUE }.thenBy(AuthoritativeChordEvent::startTick).thenBy(AuthoritativeChordEvent::id))
        if (events != expectedOrder) {
            findings += finding(MidiCoreHarmonyFindingCode.CHORD_EVENT_ORDER, "Chord events are not in deterministic occurrence/tick order.", "Save chord windows in occurrence order and then start-tick order.")
        }

        val orderedFindings = findings.distinctBy { listOf(it.code, it.occurrenceId, it.chordEventId, it.tick) }
            .sortedWith(compareBy<MidiCoreHarmonyFinding> { it.occurrenceId ?: "" }.thenBy { it.tick ?: -1L }.thenBy { it.chordEventId ?: "" }.thenBy { it.code.ordinal })
        return MidiCoreHarmonyValidation(orderedFindings, resolved.sortedWith(compareBy<MidiCoreResolvedChordWindow> { occurrences[it.event.occurrenceId]?.startTick ?: Long.MAX_VALUE }.thenBy { it.startTick }.thenBy { it.event.id }))
    }

    private fun finding(
        code: MidiCoreHarmonyFindingCode,
        message: String,
        action: String,
        severity: MidiCoreHarmonyFindingSeverity = MidiCoreHarmonyFindingSeverity.BLOCKING,
        occurrenceId: String? = null,
        chordEventId: String? = null,
        tick: Long? = null,
    ) = MidiCoreHarmonyFinding(code, severity, occurrenceId, chordEventId, tick, message, action)
}

/** Read-only exact lookup over a validated authoritative harmony timeline. */
class MidiCoreHarmonyTimeline private constructor(
    val windows: List<MidiCoreResolvedChordWindow>,
) {
    fun forOccurrence(occurrenceId: String): List<MidiCoreResolvedChordWindow> = windows.filter { it.event.occurrenceId == occurrenceId }.also {
        require(it.isNotEmpty()) { "No authoritative harmony exists for occurrence '$occurrenceId'" }
    }

    fun atTick(tick: Long): MidiCoreResolvedChordWindow = windows.singleOrNull { tick >= it.startTick && tick < it.endTick }
        ?: throw IllegalArgumentException("No authoritative chord exists at tick $tick")

    companion object {
        fun build(authority: ProjectAuthority): MidiCoreHarmonyTimeline {
            val validation = MidiCoreHarmonyValidator.validate(authority)
            require(validation.valid) { validation.findings.joinToString("; ") { it.message } }
            return MidiCoreHarmonyTimeline(validation.windows)
        }
    }
}
