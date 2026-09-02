package app.melotrail.midi.domain

/** One source track/channel resolved automatically at import is the protected melody authority. */
data class MidiMelodySelection(val trackIndex: Int, val channel: Int) {
    init {
        require(trackIndex >= 0) { "Selected melody track must not be negative" }
        require(channel in 0..15) { "Selected melody channel must be in 0..15" }
    }
}

/** Optional key evidence is advisory only; it cannot substitute project harmony. */
data class MidiValidationContext(
    val selectedMelody: MidiMelodySelection? = null,
    val advisoryKeyPitchClasses: Set<Int>? = null,
) {
    init {
        require(advisoryKeyPitchClasses?.all { it in 0..11 } != false) { "Advisory key pitch classes must be in 0..11" }
    }
}

enum class MidiFindingSeverity { BLOCKING, AWAITING_AUTHORITY, ADVISORY }

enum class MidiFindingScope { SOURCE, TEMPO, METER, MELODY_SELECTION, TRACK, CHANNEL, EVENT }

enum class MidiFindingCode {
    NO_PAIRABLE_NOTE_STREAM,
    SINGLE_MELODY_TRACK_REQUIRED,
    SINGLE_MELODY_CHANNEL_REQUIRED,
    MISSING_TEMPO,
    MISSING_TIME_SIGNATURE,
    TEMPO_NOT_AT_ORIGIN,
    TIME_SIGNATURE_NOT_AT_ORIGIN,
    TEMPO_MAP_UNSUPPORTED,
    TIME_SIGNATURE_MAP_UNSUPPORTED,
    MELODY_SELECTION_NOT_FOUND,
    MELODY_HAS_NO_NOTES,
    UNSAFE_SELECTED_MELODY_PAIRING,
    INVALID_NOTE_TIMING,
    ORPHAN_NOTE_OFF,
    UNSUPPORTED_EVENT,
    POLYPHONY,
    CHROMATIC_MELODY,
}

data class MidiFinding(
    val code: MidiFindingCode,
    val severity: MidiFindingSeverity,
    val scope: MidiFindingScope,
    val message: String,
    val action: String,
    val trackIndex: Int? = null,
    val channel: Int? = null,
    val tick: Long? = null,
) {
    init {
        require(message.isNotBlank() && action.isNotBlank()) { "MIDI finding text must not be blank" }
        require(trackIndex == null || trackIndex >= 0) { "MIDI finding track must not be negative" }
        require(channel == null || channel in 0..15) { "MIDI finding channel must be in 0..15" }
        require(tick == null || tick >= 0) { "MIDI finding tick must not be negative" }
    }
}

enum class MidiImportDisposition { ACCEPTED, REJECTED, AWAITING_AUTHORITY }

data class MidiImportValidationResult(val findings: List<MidiFinding>) {
    init {
        require(findings == findings.sortedWith(MIDI_FINDING_ORDER)) { "MIDI validation findings must have stable ordering" }
    }

    val disposition: MidiImportDisposition
        get() = when {
            findings.any { it.severity == MidiFindingSeverity.BLOCKING } -> MidiImportDisposition.REJECTED
            findings.any { it.severity == MidiFindingSeverity.AWAITING_AUTHORITY } -> MidiImportDisposition.AWAITING_AUTHORITY
            else -> MidiImportDisposition.ACCEPTED
        }

    companion object {
        internal val MIDI_FINDING_ORDER = compareBy<MidiFinding>(
            { it.trackIndex ?: -1 },
            { it.channel ?: -1 },
            { it.tick ?: -1L },
            { it.code.ordinal },
        )
    }
}

/** Classifies source facts exactly at the MIDI contract's import boundary. */
class MidiImportValidator {
    fun validate(inspection: MidiInspectionResult, context: MidiValidationContext = MidiValidationContext()): MidiImportValidationResult {
        val effectiveContext = if (context.selectedMelody == null) {
            context.copy(selectedMelody = automaticMelodySelection(inspection))
        } else {
            context
        }
        val findings = buildList {
            validatePairableNotes(inspection, this)
            validateSingleMelodySource(inspection, this)
            validateTempo(inspection.sequence, this)
            validateMeter(inspection.sequence, this)
            validateMelody(inspection, effectiveContext, this)
            validateReaderIssues(inspection.findings, effectiveContext.selectedMelody, this)
            validateAdvisories(inspection.sequence, effectiveContext, this)
        }.distinctBy { listOf(it.code, it.trackIndex, it.channel, it.tick) }
            .sortedWith(MidiImportValidationResult.MIDI_FINDING_ORDER)
        return MidiImportValidationResult(findings)
    }

    private fun validatePairableNotes(inspection: MidiInspectionResult, findings: MutableList<MidiFinding>) {
        if (inspection.sequence.orderedEvents().none { it is MidiNoteEvent }) {
            findings += finding(MidiFindingCode.NO_PAIRABLE_NOTE_STREAM, MidiFindingSeverity.BLOCKING, MidiFindingScope.SOURCE,
                "The MIDI file contains no safely pairable note stream.", "Choose a Standard MIDI file with at least one complete note.")
        }
    }

    private fun validateSingleMelodySource(inspection: MidiInspectionResult, findings: MutableList<MidiFinding>) {
        val noteTracks = inspection.trackSummaries.filter { track -> track.channels.any { it.noteCount > 0 } }
        if (noteTracks.size != 1) {
            findings += finding(
                MidiFindingCode.SINGLE_MELODY_TRACK_REQUIRED,
                MidiFindingSeverity.BLOCKING,
                MidiFindingScope.SOURCE,
                "MIDI Core requires exactly one note-bearing melody track; this file contains ${noteTracks.size}.",
                "Export the complete song as one melody track. Meta-only conductor tracks are allowed.",
            )
            return
        }
        val noteChannels = noteTracks.single().channels.filter { it.noteCount > 0 }
        if (noteChannels.size != 1) {
            findings += finding(
                MidiFindingCode.SINGLE_MELODY_CHANNEL_REQUIRED,
                MidiFindingSeverity.BLOCKING,
                MidiFindingScope.TRACK,
                "The melody track must use exactly one note-bearing MIDI channel; it contains ${noteChannels.size}.",
                "Export the melody notes on one MIDI channel and retry the import.",
                trackIndex = noteTracks.single().trackIndex,
            )
        }
    }

    private fun automaticMelodySelection(inspection: MidiInspectionResult): MidiMelodySelection? {
        val noteTrack = inspection.trackSummaries.singleOrNull { track -> track.channels.any { it.noteCount > 0 } } ?: return null
        val noteChannel = noteTrack.channels.singleOrNull { it.noteCount > 0 } ?: return null
        return MidiMelodySelection(noteTrack.trackIndex, noteChannel.channel)
    }

    private fun validateTempo(sequence: SemanticMidiSequence, findings: MutableList<MidiFinding>) {
        val tempos = sequence.orderedEvents().filterIsInstance<MidiTempoEvent>()
        if (tempos.isEmpty()) {
            findings += finding(MidiFindingCode.MISSING_TEMPO, MidiFindingSeverity.AWAITING_AUTHORITY, MidiFindingScope.TEMPO,
                "The MIDI file has no tempo metadata.", "Confirm one project tempo before arranging.")
            return
        }
        if (tempos.none { it.orderingKey.tick == 0L }) findings += finding(MidiFindingCode.TEMPO_NOT_AT_ORIGIN, MidiFindingSeverity.BLOCKING, MidiFindingScope.TEMPO,
            "Tempo metadata must start at tick zero.", "Export or edit the source so its fixed tempo starts at tick zero.")
        if (tempos.map(MidiTempoEvent::microsecondsPerQuarter).distinct().size > 1) findings += finding(MidiFindingCode.TEMPO_MAP_UNSUPPORTED, MidiFindingSeverity.BLOCKING, MidiFindingScope.TEMPO,
            "Tempo changes are not supported in MIDI Core V1.", "Use one fixed tempo before importing.")
    }

    private fun validateMeter(sequence: SemanticMidiSequence, findings: MutableList<MidiFinding>) {
        val meters = sequence.orderedEvents().filterIsInstance<MidiTimeSignatureEvent>()
        if (meters.isEmpty()) {
            findings += finding(MidiFindingCode.MISSING_TIME_SIGNATURE, MidiFindingSeverity.AWAITING_AUTHORITY, MidiFindingScope.METER,
                "The MIDI file has no time-signature metadata.", "Confirm one project time signature before arranging.")
            return
        }
        if (meters.none { it.orderingKey.tick == 0L }) findings += finding(MidiFindingCode.TIME_SIGNATURE_NOT_AT_ORIGIN, MidiFindingSeverity.BLOCKING, MidiFindingScope.METER,
            "Time-signature metadata must start at tick zero.", "Export or edit the source so its fixed meter starts at tick zero.")
        if (meters.map { it.numerator to it.denominatorExponent }.distinct().size > 1) findings += finding(MidiFindingCode.TIME_SIGNATURE_MAP_UNSUPPORTED, MidiFindingSeverity.BLOCKING, MidiFindingScope.METER,
            "Time-signature changes are not supported in MIDI Core V1.", "Use one fixed time signature before importing.")
    }

    private fun validateMelody(inspection: MidiInspectionResult, context: MidiValidationContext, findings: MutableList<MidiFinding>) {
        val selection = context.selectedMelody ?: return
        val track = inspection.trackSummaries.getOrNull(selection.trackIndex)
        val channel = track?.channels?.singleOrNull { it.channel == selection.channel }
        if (channel == null) {
            findings += finding(MidiFindingCode.MELODY_SELECTION_NOT_FOUND, MidiFindingSeverity.BLOCKING, MidiFindingScope.MELODY_SELECTION,
                "The protected melody track or channel is not present in the imported MIDI.", "Create a new project and import one valid single-track melody source.", selection.trackIndex, selection.channel)
        } else if (channel.noteCount == 0) {
            findings += finding(MidiFindingCode.MELODY_HAS_NO_NOTES, MidiFindingSeverity.BLOCKING, MidiFindingScope.MELODY_SELECTION,
                "The protected melody channel contains no complete notes.", "Create a new project and import a melody source with complete notes.", selection.trackIndex, selection.channel)
        }
    }

    private fun validateReaderIssues(issues: List<MidiReaderIssue>, selection: MidiMelodySelection?, findings: MutableList<MidiFinding>) {
        issues.forEach { issue -> when (issue.code) {
            MidiReaderIssueCode.NON_POSITIVE_NOTE_DURATION -> findings += finding(MidiFindingCode.INVALID_NOTE_TIMING, MidiFindingSeverity.BLOCKING, MidiFindingScope.EVENT,
                issue.message, "Repair the malformed note timing before importing.", issue.trackIndex, tick = issue.tick)
            MidiReaderIssueCode.UNCLOSED_NOTE_ON -> findings += finding(MidiFindingCode.UNSAFE_SELECTED_MELODY_PAIRING,
                MidiFindingSeverity.BLOCKING,
                MidiFindingScope.EVENT, issue.message,
                if (selection?.trackIndex == issue.trackIndex) "Repair the melody's missing note-off and import it into a new project." else "Remove note messages from extra tracks; only the complete-song melody track may contain notes.",
                issue.trackIndex, tick = issue.tick)
            MidiReaderIssueCode.ORPHAN_NOTE_OFF -> findings += finding(MidiFindingCode.ORPHAN_NOTE_OFF, MidiFindingSeverity.ADVISORY, MidiFindingScope.EVENT,
                issue.message, "Review the orphan note-off; it is ignored because no matching note-on exists.", issue.trackIndex, tick = issue.tick)
            MidiReaderIssueCode.UNSUPPORTED_MESSAGE -> findings += finding(MidiFindingCode.UNSUPPORTED_EVENT, MidiFindingSeverity.ADVISORY, MidiFindingScope.EVENT,
                issue.message, "Review the omitted message; it is not used by generated roles or default export.", issue.trackIndex, tick = issue.tick)
        } }
    }

    private fun validateAdvisories(sequence: SemanticMidiSequence, context: MidiValidationContext, findings: MutableList<MidiFinding>) {
        val selection = context.selectedMelody ?: return
        val notes = sequence.tracks.getOrNull(selection.trackIndex)?.events.orEmpty().filterIsInstance<MidiNoteEvent>().filter { it.channel == selection.channel }
        if (notes.any { left -> notes.any { right -> left !== right && left.orderingKey.tick < right.endTick && right.orderingKey.tick < left.endTick } }) {
            findings += finding(MidiFindingCode.POLYPHONY, MidiFindingSeverity.ADVISORY, MidiFindingScope.CHANNEL,
                "The protected melody channel is polyphonic.", "Review the melody performance; polyphony is preserved and is not rejected.", selection.trackIndex, selection.channel)
        }
        context.advisoryKeyPitchClasses?.let { scale ->
            if (notes.any { it.pitch % 12 !in scale }) findings += finding(MidiFindingCode.CHROMATIC_MELODY, MidiFindingSeverity.ADVISORY, MidiFindingScope.CHANNEL,
                "The selected melody contains notes outside the advisory key.", "Review the key suggestion; chromatic notes remain valid authority.", selection.trackIndex, selection.channel)
        }
    }

    private fun finding(
        code: MidiFindingCode,
        severity: MidiFindingSeverity,
        scope: MidiFindingScope,
        message: String,
        action: String,
        trackIndex: Int? = null,
        channel: Int? = null,
        tick: Long? = null,
    ) = MidiFinding(code, severity, scope, message, action, trackIndex, channel, tick)
}
