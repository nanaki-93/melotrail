package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.MidiProtectedMelodyNoteId
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.music.core.ProjectMeter
import app.melotrail.structure.MidiCoreResolvedChordWindow
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Read-only, deterministic melody/harmony evidence.  This is advisory analysis:
 * it neither changes protected MIDI events nor proposes a replacement chord.
 */
object MidiCoreMelodyHarmonyAnalyzer {
    const val ANALYSIS_VERSION: String = "melody-harmony-context-v1"

    /** Analyze the complete validated authority timeline against its selected protected melody. */
    fun analyze(
        authority: MidiCoreAuthoritySnapshot,
        protectedMelody: MidiProtectedMelodyView,
    ): MidiCoreMelodyHarmonyAnalysis {
        require(protectedMelody.sourceSha256 == authority.sourceSha256) {
            "Protected melody source does not match the authority snapshot"
        }
        require(protectedMelody.identitySha256 == authority.melodySha256) {
            "Protected melody identity does not match the authority snapshot"
        }
        require(protectedMelody.ppq == authority.ppq) {
            "Protected melody PPQ does not match the authority snapshot"
        }
        require(authority.harmonyWindows.isNotEmpty()) {
            "Melody/harmony analysis requires at least one authoritative chord window"
        }

        val analysisEnd = authority.harmonyWindows.maxOf(MidiCoreResolvedChordWindow::endTick)
        val soundingNotes = soundingNotes(protectedMelody, analysisEnd)
        val phrases = phrases(soundingNotes, authority.tickGrid().ticksPerBeat)
        val noteContexts = soundingNotes.associateBy { it.id }
        val beats = beatContexts(authority, noteContexts)
        val bars = barContexts(authority, noteContexts)
        val windows = authority.harmonyWindows.map { window ->
            windowContext(authority, window, noteContexts, phrases, protectedMelody)
        }
        val findings = windows.flatMap(MidiCoreMelodyHarmonyWindowContext::findings).sortedWith(findingOrder())
        return MidiCoreMelodyHarmonyAnalysis(
            version = ANALYSIS_VERSION,
            authorityHash = authority.authorityHash,
            melodySha256 = protectedMelody.identitySha256,
            notes = soundingNotes,
            beats = beats,
            bars = bars,
            phrases = phrases,
            windows = windows,
            findings = findings,
        )
    }

    // Analysis needs exact meter beats and bars, not the generators' sixteenth-note grid.
    private fun MidiCoreAuthoritySnapshot.tickGrid() = MidiCoreTickGrid(ppq, meter, subdivisionsPerQuarter = 1)

    private fun soundingNotes(
        melody: MidiProtectedMelodyView,
        analysisEnd: Long,
    ): List<MidiCoreMelodyNoteContext> {
        val sustainChanges = melody.events.filterIsInstance<MidiControlChangeEvent>().filter { it.controller == SUSTAIN_CONTROLLER }
        return melody.notes.map { note ->
            val soundingEnd = sustainedEnd(note, sustainChanges, analysisEnd)
            MidiCoreMelodyNoteContext(
                id = note.id.value,
                startTick = note.startTick,
                keyReleaseTick = note.endTick,
                soundingEndTick = soundingEnd,
                pitch = note.pitch,
                velocity = note.velocity,
                anchor = note.id in melody.protectedAnchorIds,
            )
        }.sortedWith(noteOrder())
    }

    /** CC64 is the supported pedal interpretation; an unclosed pedal sustains only to the analysis boundary. */
    private fun sustainedEnd(
        note: app.melotrail.midi.domain.MidiProtectedMelodyNote,
        sustainChanges: List<MidiControlChangeEvent>,
        analysisEnd: Long,
    ): Long {
        val sustainAtRelease = sustainChanges.lastOrNull { it.orderingKey.tick <= note.endTick }?.value ?: 0
        if (sustainAtRelease < SUSTAIN_ON_VALUE) return note.endTick
        return sustainChanges.firstOrNull { it.orderingKey.tick > note.endTick && it.value < SUSTAIN_ON_VALUE }
            ?.orderingKey?.tick?.coerceAtMost(analysisEnd)
            ?: analysisEnd
    }

    private fun phrases(notes: List<MidiCoreMelodyNoteContext>, ticksPerBeat: Long): List<MidiCoreMelodyPhraseHint> {
        if (notes.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<MidiCoreMelodyNoteContext>>()
        var phraseEnd = Long.MIN_VALUE
        notes.forEach { note ->
            if (groups.isEmpty() || note.startTick - phraseEnd >= ticksPerBeat) groups.add(mutableListOf())
            groups.last().add(note)
            phraseEnd = maxOf(phraseEnd, note.soundingEndTick)
        }
        val priorBySignature = mutableMapOf<String, Int>()
        return groups.mapIndexed { index, group ->
            val signature = motifSignature(group)
            val repeats = signature?.let { priorBySignature[it] }
            if (signature != null) priorBySignature.putIfAbsent(signature, index + 1)
            MidiCoreMelodyPhraseHint(
                index = index + 1,
                startTick = group.minOf(MidiCoreMelodyNoteContext::startTick),
                endTick = group.maxOf(MidiCoreMelodyNoteContext::soundingEndTick),
                noteIds = group.map(MidiCoreMelodyNoteContext::id),
                reason = if (index == 0) "Starts the melody." else "A sounding rest of at least one beat precedes this phrase.",
                repeatsPhraseIndex = repeats,
            )
        }
    }

    private fun motifSignature(notes: List<MidiCoreMelodyNoteContext>): String? {
        if (notes.size < 2) return null
        val first = notes.first()
        return notes.joinToString(",") { note ->
            "${note.pitch - first.pitch}:${note.keyReleaseTick - note.startTick}:${note.startTick - first.startTick}"
        }
    }

    private fun beatContexts(
        authority: MidiCoreAuthoritySnapshot,
        notes: Map<String, MidiCoreMelodyNoteContext>,
    ): List<MidiCoreMelodyBeatContext> {
        val grid = authority.tickGrid()
        val end = authority.harmonyWindows.maxOf(MidiCoreResolvedChordWindow::endTick)
        return metricalSlices(authority.pickupTicks, end, grid.ticksPerBeat).map { (start, endTick) ->
            val location = location(authority, start)
            val activity = activity(start, endTick, notes.values)
            MidiCoreMelodyBeatContext(start, endTick, location, prominence(authority.meter, location), activity)
        }
    }

    private fun barContexts(
        authority: MidiCoreAuthoritySnapshot,
        notes: Map<String, MidiCoreMelodyNoteContext>,
    ): List<MidiCoreMelodyBarContext> {
        val grid = authority.tickGrid()
        val end = authority.harmonyWindows.maxOf(MidiCoreResolvedChordWindow::endTick)
        return metricalSlices(authority.pickupTicks, end, grid.ticksPerBar).map { (start, endTick) ->
            MidiCoreMelodyBarContext(start, endTick, location(authority, start).barNumber, activity(start, endTick, notes.values))
        }
    }

    private fun generateSlices(start: Long, end: Long, size: Long): List<Pair<Long, Long>> {
        require(size > 0)
        return buildList {
            var cursor = start
            while (cursor < end) {
                val next = minOf(Math.addExact(cursor, size), end)
                add(cursor to next)
                cursor = next
            }
        }
    }

    /** Slice pickup beats within its partial bar; the regular grid starts immediately after it. */
    private fun metricalSlices(pickupTicks: Long, end: Long, size: Long): List<Pair<Long, Long>> = buildList {
        val pickupEnd = minOf(pickupTicks, end)
        if (pickupEnd > 0L) addAll(generateSlices(0L, pickupEnd, size))
        if (pickupEnd < end) addAll(generateSlices(pickupEnd, end, size))
    }

    private fun windowContext(
        authority: MidiCoreAuthoritySnapshot,
        window: MidiCoreResolvedChordWindow,
        notes: Map<String, MidiCoreMelodyNoteContext>,
        phrases: List<MidiCoreMelodyPhraseHint>,
        protectedMelody: MidiProtectedMelodyView,
    ): MidiCoreMelodyHarmonyWindowContext {
        val activity = activity(window.startTick, window.endTick, notes.values)
        val accentedNoteIds = activity.activeNotes.filter { note ->
            note.startTick in window.startTick until window.endTick &&
                prominence(authority.meter, location(authority, note.startTick)) >= STRONG_BEAT_PROMINENCE
        }.map(MidiCoreMelodyNoteContext::id)
        val phraseHints = phrases.filter { it.startTick < window.endTick && window.startTick < it.endTick }
        val bends = protectedMelody.events.filterIsInstance<MidiPitchBendEvent>()
        val findings = activity.soundingNotes.flatMap { note ->
            tensionFindings(authority, window, note, bends)
        }.sortedWith(findingOrder())
        return MidiCoreMelodyHarmonyWindowContext(
            chordEventId = window.event.id,
            occurrenceId = window.event.occurrenceId,
            chordSymbol = window.chord.canonicalSymbol,
            startTick = window.startTick,
            endTick = window.endTick,
            location = location(authority, window.startTick),
            activity = activity,
            accentedNoteIds = accentedNoteIds,
            phraseHints = phraseHints,
            findings = findings,
        )
    }

    private fun tensionFindings(
        authority: MidiCoreAuthoritySnapshot,
        window: MidiCoreResolvedChordWindow,
        note: MidiCoreMelodyNoteContext,
        bends: List<MidiPitchBendEvent>,
    ): List<MidiCoreHarmonyTensionFinding> {
        val start = maxOf(note.startTick, window.startTick)
        val end = minOf(note.soundingEndTick, window.endTick)
        if (start >= end) return emptyList()
        val metricalEvidence = strongestMetricalEvidence(authority, start, end)
        val bendIsAmbiguous = activeNonZeroBendAtOrDuring(bends, start, end)
        if (bendIsAmbiguous) return listOf(finding(
            MidiCoreHarmonyTensionCause.PITCH_BEND_RANGE_UNKNOWN,
            MidiCoreHarmonyTensionConfidence.LIMITED,
            window,
            note,
            start,
            end,
            metricalEvidence.location,
            metricalEvidence.prominence,
            "A non-zero pitch bend is active or changes during this overlap; MIDI does not state its semitone range, so exact acoustic consonance is unknown.",
        ))
        if (window.chord.containsPitchClass(note.pitch)) return emptyList()

        val overlap = end - start
        val beat = authority.tickGrid().ticksPerBeat
        val enteredBeforeWindow = note.startTick < window.startTick
        val shortMotion = overlap <= beat / 2L && note.keyReleaseTick - note.startTick <= beat / 2L
        val cause = when {
            enteredBeforeWindow -> MidiCoreHarmonyTensionCause.HELD_SUSPENSION
            shortMotion -> MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE
            overlap >= beat && metricalEvidence.prominence >= STRONG_BEAT_PROMINENCE -> MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION
            else -> MidiCoreHarmonyTensionCause.NON_CHORD_TONE
        }
        val confidence = when (cause) {
            MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION -> MidiCoreHarmonyTensionConfidence.HIGH
            MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE,
            MidiCoreHarmonyTensionCause.HELD_SUSPENSION -> MidiCoreHarmonyTensionConfidence.MEDIUM
            else -> MidiCoreHarmonyTensionConfidence.MEDIUM
        }
        return listOf(finding(
            cause,
            confidence,
            window,
            note,
            start,
            end,
            metricalEvidence.location,
            metricalEvidence.prominence,
            message(cause),
        ))
    }

    /** Report the earliest strongest metrical point touched by the half-open sounding overlap. */
    private fun strongestMetricalEvidence(
        authority: MidiCoreAuthoritySnapshot,
        start: Long,
        end: Long,
    ): MetricalEvidence {
        require(start >= 0L && start < end)
        val grid = authority.tickGrid()
        val candidateTicks = mutableSetOf(start)
        val pickupEnd = minOf(authority.pickupTicks, end)
        if (start < pickupEnd) {
            firstGridTickAtOrAfter(start, 0L, metricalGroupTicks(grid))
                ?.takeIf { it < pickupEnd }
                ?.let(candidateTicks::add)
        }
        if (end > authority.pickupTicks) {
            val regularStart = maxOf(start, authority.pickupTicks)
            firstGridTickAtOrAfter(regularStart, authority.pickupTicks, metricalGroupTicks(grid))
                ?.takeIf { it < end }
                ?.let(candidateTicks::add)
            firstGridTickAtOrAfter(regularStart, authority.pickupTicks, grid.ticksPerBar)
                ?.takeIf { it < end }
                ?.let(candidateTicks::add)
        }
        val strongestTick = candidateTicks.minWith(
            compareByDescending<Long> { prominence(authority.meter, location(authority, it)) }.thenBy { it },
        )
        val strongestLocation = location(authority, strongestTick)
        return MetricalEvidence(strongestLocation, prominence(authority.meter, strongestLocation))
    }

    private fun firstGridTickAtOrAfter(tick: Long, origin: Long, step: Long): Long? {
        require(tick >= origin && step > 0L)
        val remainder = (tick - origin) % step
        if (remainder == 0L) return tick
        val distance = step - remainder
        return if (tick <= Long.MAX_VALUE - distance) tick + distance else null
    }

    private fun activeNonZeroBendAtOrDuring(bends: List<MidiPitchBendEvent>, start: Long, end: Long): Boolean {
        val current = bends.lastOrNull { it.orderingKey.tick <= start }?.value ?: 0
        return current != 0 || bends.any { it.orderingKey.tick in (start + 1) until end && it.value != 0 }
    }

    private fun message(cause: MidiCoreHarmonyTensionCause): String = when (cause) {
        MidiCoreHarmonyTensionCause.SUSTAINED_ACCENTED_TENSION -> "A non-chord melody tone is sustained across a prominent beat. Review the authoritative chord and accompaniment space."
        MidiCoreHarmonyTensionCause.PASSING_OR_NEIGHBOR_TONE -> "A short non-chord melody tone may function as a passing or neighbor tone; it is not a harmony error."
        MidiCoreHarmonyTensionCause.HELD_SUSPENSION -> "A melody tone held into the new chord may be a suspension; it is not a harmony error."
        MidiCoreHarmonyTensionCause.NON_CHORD_TONE -> "A melody tone is outside the authoritative chord during this overlap; review it in musical context."
        MidiCoreHarmonyTensionCause.PITCH_BEND_RANGE_UNKNOWN -> error("Pitch-bend message is supplied directly")
    }

    private fun finding(
        cause: MidiCoreHarmonyTensionCause,
        confidence: MidiCoreHarmonyTensionConfidence,
        window: MidiCoreResolvedChordWindow,
        note: MidiCoreMelodyNoteContext,
        start: Long,
        end: Long,
        location: MidiCoreMelodyLocation,
        beatProminence: Double,
        message: String,
    ) = MidiCoreHarmonyTensionFinding(
        cause = cause,
        confidence = confidence,
        occurrenceId = window.event.occurrenceId,
        chordEventId = window.event.id,
        noteId = note.id,
        startTick = start,
        endTick = end,
        location = location,
        overlapTicks = end - start,
        beatProminence = beatProminence,
        intervalAboveRootSemitones = note.pitch - referenceRootPitch(window.chord.rootPitchClass),
        nearestChordToneDistanceSemitones = nearestChordToneDistance(note.pitch, window.chord.pitchClasses),
        message = message,
    )

    /** A fixed root register makes octave/compound interval evidence comparable without assuming accompaniment voicing. */
    private fun referenceRootPitch(rootPitchClass: Int): Int = ROOT_REFERENCE_OCTAVE + rootPitchClass

    private fun nearestChordToneDistance(pitch: Int, tones: Set<Int>): Int = tones.minOf { tone ->
        val distance = abs(Math.floorMod(pitch - tone, OCTAVE))
        minOf(distance, OCTAVE - distance)
    }

    private fun activity(start: Long, end: Long, notes: Collection<MidiCoreMelodyNoteContext>): MidiCoreMelodyActivity {
        val active = notes.filter { it.startTick < end && start < it.keyReleaseTick }.sortedWith(noteOrder())
        val sounding = notes.filter { it.startTick < end && start < it.soundingEndTick }.sortedWith(noteOrder())
        val rests = restSpans(start, end, sounding)
        return MidiCoreMelodyActivity(active, sounding, rests, register(sounding))
    }

    private fun restSpans(start: Long, end: Long, notes: List<MidiCoreMelodyNoteContext>): List<MidiCoreMelodyRestSpan> {
        var cursor = start
        return buildList {
            notes.forEach { note ->
                val noteStart = maxOf(note.startTick, start)
                if (cursor < noteStart) add(MidiCoreMelodyRestSpan(cursor, noteStart))
                cursor = maxOf(cursor, minOf(note.soundingEndTick, end))
            }
            if (cursor < end) add(MidiCoreMelodyRestSpan(cursor, end))
        }
    }

    private fun register(notes: List<MidiCoreMelodyNoteContext>): MidiCoreMelodyRegister? = notes.takeIf { it.isNotEmpty() }?.let {
        MidiCoreMelodyRegister(it.minOf(MidiCoreMelodyNoteContext::pitch), it.maxOf(MidiCoreMelodyNoteContext::pitch))
    }

    private fun location(authority: MidiCoreAuthoritySnapshot, tick: Long): MidiCoreMelodyLocation {
        val grid = authority.tickGrid()
        val pickup = authority.pickupTicks
        if (tick < pickup) return MidiCoreMelodyLocation(0, (tick / grid.ticksPerBeat).toInt() + 1, tick % grid.ticksPerBeat, true)
        val afterPickup = tick - pickup
        return MidiCoreMelodyLocation(
            barNumber = (afterPickup / grid.ticksPerBar).toInt() + 1,
            beatNumber = ((afterPickup % grid.ticksPerBar) / grid.ticksPerBeat).toInt() + 1,
            tickIntoBeat = afterPickup % grid.ticksPerBeat,
            pickup = false,
        )
    }

    private fun prominence(meter: ProjectMeter, location: MidiCoreMelodyLocation): Double = when {
        location.beatNumber == 1 && location.tickIntoBeat == 0L -> 1.0
        location.tickIntoBeat == 0L && isSecondaryGroupAccent(meter, location.beatNumber) -> 0.75
        else -> 0.5
    }

    private fun metricalGroupTicks(grid: MidiCoreTickGrid): Long = Math.multiplyExact(
        grid.ticksPerBeat,
        accentGroupSize(grid.meter).toLong(),
    )

    /** Compound meters accent each three-unit group; simple quadruple meters retain their midpoint accent. */
    private fun isSecondaryGroupAccent(meter: ProjectMeter, beatNumber: Int): Boolean {
        val groupSize = accentGroupSize(meter)
        return beatNumber > 1 && (beatNumber - 1) % groupSize == 0
    }

    private fun accentGroupSize(meter: ProjectMeter): Int = when {
        meter.numerator > 3 && meter.numerator % 3 == 0 -> 3
        meter.numerator == 4 -> 2
        else -> meter.numerator
    }

    private fun noteOrder() = compareBy<MidiCoreMelodyNoteContext> { it.startTick }
        .thenBy { it.keyReleaseTick }.thenBy { it.pitch }.thenBy { it.id }

    private fun findingOrder() = compareBy<MidiCoreHarmonyTensionFinding> { it.startTick }
        .thenBy { it.endTick }.thenBy { it.chordEventId }.thenBy { it.noteId }.thenBy { it.cause.ordinal }

    private const val SUSTAIN_CONTROLLER = 64
    private const val SUSTAIN_ON_VALUE = 64
    private const val OCTAVE = 12
    private const val ROOT_REFERENCE_OCTAVE = 48
    private const val STRONG_BEAT_PROMINENCE = 0.75

    private data class MetricalEvidence(
        val location: MidiCoreMelodyLocation,
        val prominence: Double,
    )
}

data class MidiCoreMelodyHarmonyAnalysis(
    val version: String,
    val authorityHash: String,
    val melodySha256: String,
    val notes: List<MidiCoreMelodyNoteContext>,
    val beats: List<MidiCoreMelodyBeatContext>,
    val bars: List<MidiCoreMelodyBarContext>,
    val phrases: List<MidiCoreMelodyPhraseHint>,
    val windows: List<MidiCoreMelodyHarmonyWindowContext>,
    val findings: List<MidiCoreHarmonyTensionFinding>,
) {
    init {
        require(version == MidiCoreMelodyHarmonyAnalyzer.ANALYSIS_VERSION)
        require(authorityHash.matches(SHA_256) && melodySha256.matches(SHA_256))
        require(notes == notes.sortedWith(compareBy<MidiCoreMelodyNoteContext> { it.startTick }.thenBy { it.keyReleaseTick }.thenBy { it.pitch }.thenBy { it.id }))
        require(windows == windows.sortedWith(compareBy<MidiCoreMelodyHarmonyWindowContext> { it.startTick }.thenBy { it.chordEventId }))
        require(findings == findings.sortedWith(compareBy<MidiCoreHarmonyTensionFinding> { it.startTick }.thenBy { it.endTick }.thenBy { it.chordEventId }.thenBy { it.noteId }.thenBy { it.cause.ordinal }))
    }

    /** Stable identity for this versioned read-only analysis, independent of data-class debug formatting. */
    val analysisSha256: String get() = sha256(analysisRecord(
        "analysis",
        listOf(
            "version" to version,
            "authority" to authorityHash,
            "melody" to melodySha256,
            "notes" to notes.joinToString(";") { it.analysisRecord() },
            "beats" to beats.joinToString(";") { it.analysisRecord() },
            "bars" to bars.joinToString(";") { it.analysisRecord() },
            "phrases" to phrases.joinToString(";") { it.analysisRecord() },
            "windows" to windows.joinToString(";") { it.analysisRecord() },
            "findings" to findings.joinToString(";") { it.analysisRecord() },
        ),
    ))
}

data class MidiCoreMelodyNoteContext(
    val id: String,
    val startTick: Long,
    val keyReleaseTick: Long,
    val soundingEndTick: Long,
    val pitch: Int,
    val velocity: Int,
    val anchor: Boolean,
) {
    init {
        require(MidiProtectedMelodyNoteId(id).value == id && startTick >= 0 && keyReleaseTick > startTick && soundingEndTick >= keyReleaseTick)
        require(pitch in 0..127 && velocity in 0..127)
    }
}

data class MidiCoreMelodyRestSpan(val startTick: Long, val endTick: Long) {
    init { require(startTick >= 0 && endTick > startTick) }
}

data class MidiCoreMelodyRegister(val lowestPitch: Int, val highestPitch: Int) {
    init { require(lowestPitch in 0..127 && highestPitch in lowestPitch..127) }
}

data class MidiCoreMelodyActivity(
    val activeNotes: List<MidiCoreMelodyNoteContext>,
    val soundingNotes: List<MidiCoreMelodyNoteContext>,
    val rests: List<MidiCoreMelodyRestSpan>,
    val register: MidiCoreMelodyRegister?,
)

data class MidiCoreMelodyLocation(
    val barNumber: Int,
    val beatNumber: Int,
    val tickIntoBeat: Long,
    val pickup: Boolean,
) {
    init { require(barNumber >= 0 && beatNumber >= 1 && tickIntoBeat >= 0) }
}

data class MidiCoreMelodyBeatContext(
    val startTick: Long,
    val endTick: Long,
    val location: MidiCoreMelodyLocation,
    val prominence: Double,
    val activity: MidiCoreMelodyActivity,
)

data class MidiCoreMelodyBarContext(
    val startTick: Long,
    val endTick: Long,
    val barNumber: Int,
    val activity: MidiCoreMelodyActivity,
)

data class MidiCoreMelodyPhraseHint(
    val index: Int,
    val startTick: Long,
    val endTick: Long,
    val noteIds: List<String>,
    val reason: String,
    val repeatsPhraseIndex: Int?,
) {
    init { require(index > 0 && startTick >= 0 && endTick > startTick && noteIds.isNotEmpty() && reason.isNotBlank()) }
}

data class MidiCoreMelodyHarmonyWindowContext(
    val chordEventId: String,
    val occurrenceId: String,
    val chordSymbol: String,
    val startTick: Long,
    val endTick: Long,
    val location: MidiCoreMelodyLocation,
    val activity: MidiCoreMelodyActivity,
    val accentedNoteIds: List<String>,
    val phraseHints: List<MidiCoreMelodyPhraseHint>,
    val findings: List<MidiCoreHarmonyTensionFinding>,
) {
    init { require(chordEventId.isNotBlank() && occurrenceId.isNotBlank() && chordSymbol.isNotBlank() && startTick >= 0 && endTick > startTick) }
}

enum class MidiCoreHarmonyTensionCause {
    SUSTAINED_ACCENTED_TENSION,
    PASSING_OR_NEIGHBOR_TONE,
    HELD_SUSPENSION,
    NON_CHORD_TONE,
    PITCH_BEND_RANGE_UNKNOWN,
}

enum class MidiCoreHarmonyTensionConfidence { HIGH, MEDIUM, LIMITED }

/** One explainable advisory location; it is not a musical-quality verdict or a validation failure. */
data class MidiCoreHarmonyTensionFinding(
    val cause: MidiCoreHarmonyTensionCause,
    val confidence: MidiCoreHarmonyTensionConfidence,
    val occurrenceId: String,
    val chordEventId: String,
    val noteId: String,
    val startTick: Long,
    val endTick: Long,
    val location: MidiCoreMelodyLocation,
    val overlapTicks: Long,
    val beatProminence: Double,
    val intervalAboveRootSemitones: Int,
    val nearestChordToneDistanceSemitones: Int,
    val message: String,
) {
    init {
        require(occurrenceId.isNotBlank() && chordEventId.isNotBlank() && MidiProtectedMelodyNoteId(noteId).value == noteId)
        require(startTick >= 0 && endTick > startTick && overlapTicks == endTick - startTick)
        require(beatProminence in 0.0..1.0 && nearestChordToneDistanceSemitones in 0..6 && message.isNotBlank())
    }
}

private val SHA_256 = Regex("[0-9a-f]{64}")

private fun analysisRecord(type: String, fields: List<Pair<String, String>>): String = buildString {
    append(type)
    fields.forEach { (name, value) -> append('|').append(name).append('=').append(value.length).append(':').append(value) }
}

private fun MidiCoreMelodyNoteContext.analysisRecord(): String = analysisRecord(
    "note",
    listOf("id" to id, "start" to startTick.toString(), "release" to keyReleaseTick.toString(), "sounding" to soundingEndTick.toString(), "pitch" to pitch.toString(), "velocity" to velocity.toString(), "anchor" to anchor.toString()),
)

private fun MidiCoreMelodyLocation.analysisRecord(): String = listOf(barNumber, beatNumber, tickIntoBeat, pickup).joinToString("|")

private fun MidiCoreMelodyActivity.analysisRecord(): String = analysisRecord(
    "activity",
    listOf(
        "active" to activeNotes.joinToString(",") { it.id },
        "sounding" to soundingNotes.joinToString(",") { it.id },
        "rests" to rests.joinToString(",") { "${it.startTick}-${it.endTick}" },
        "register" to register?.let { "${it.lowestPitch}-${it.highestPitch}" }.orEmpty(),
    ),
)

private fun MidiCoreMelodyBeatContext.analysisRecord(): String = analysisRecord(
    "beat",
    listOf("start" to startTick.toString(), "end" to endTick.toString(), "location" to location.analysisRecord(), "prominence" to prominence.toString(), "activity" to activity.analysisRecord()),
)

private fun MidiCoreMelodyBarContext.analysisRecord(): String = analysisRecord(
    "bar",
    listOf("start" to startTick.toString(), "end" to endTick.toString(), "number" to barNumber.toString(), "activity" to activity.analysisRecord()),
)

private fun MidiCoreMelodyPhraseHint.analysisRecord(): String = analysisRecord(
    "phrase",
    listOf("index" to index.toString(), "start" to startTick.toString(), "end" to endTick.toString(), "notes" to noteIds.joinToString(","), "reason" to reason, "repeat" to repeatsPhraseIndex?.toString().orEmpty()),
)

private fun MidiCoreMelodyHarmonyWindowContext.analysisRecord(): String = analysisRecord(
    "window",
    listOf("id" to chordEventId, "occurrence" to occurrenceId, "chord" to chordSymbol, "start" to startTick.toString(), "end" to endTick.toString(), "location" to location.analysisRecord(), "activity" to activity.analysisRecord(), "accents" to accentedNoteIds.joinToString(","), "phrases" to phraseHints.joinToString(",") { it.index.toString() }, "findings" to findings.joinToString(",") { "${it.noteId}:${it.cause.name}" }),
)

private fun MidiCoreHarmonyTensionFinding.analysisRecord(): String = analysisRecord(
    "finding",
    listOf("cause" to cause.name, "confidence" to confidence.name, "occurrence" to occurrenceId, "chord" to chordEventId, "note" to noteId, "start" to startTick.toString(), "end" to endTick.toString(), "location" to location.analysisRecord(), "overlap" to overlapTicks.toString(), "prominence" to beatProminence.toString(), "compound" to intervalAboveRootSemitones.toString(), "nearest" to nearestChordToneDistanceSemitones.toString(), "message" to message),
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
