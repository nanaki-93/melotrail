package app.melotrail.midi.domain

/** Parser evidence is semantic data, so validators can classify it without JDK MIDI dependencies. */
enum class MidiReaderIssueCode { ORPHAN_NOTE_OFF, UNCLOSED_NOTE_ON, NON_POSITIVE_NOTE_DURATION, UNSUPPORTED_MESSAGE }

data class MidiReaderIssue(
    val code: MidiReaderIssueCode,
    val trackIndex: Int,
    val eventIndex: Int,
    val tick: Long,
    val message: String,
) {
    init {
        require(trackIndex >= 0 && eventIndex >= 0 && tick >= 0) { "MIDI reader issue location must be non-negative" }
        require(message.isNotBlank()) { "MIDI reader issue message must not be blank" }
    }
}

enum class MidiTrackRoleHint { MELODY, CHORDS, BASS, DRUMS }

data class MidiChannelSummary(
    val channel: Int,
    val noteCount: Int,
    val minimumPitch: Int?,
    val maximumPitch: Int?,
    val controllerCount: Int,
    val likelyRoles: List<MidiTrackRoleHint>,
) {
    init {
        require(channel in 0..15 && noteCount >= 0 && controllerCount >= 0) { "MIDI channel summary values are invalid" }
        require((minimumPitch == null) == (maximumPitch == null)) { "MIDI pitch range must be complete or absent" }
        require(minimumPitch == null || (minimumPitch in 0..127 && maximumPitch != null && maximumPitch in minimumPitch..127)) { "MIDI pitch range is invalid" }
        require(likelyRoles == likelyRoles.distinct()) { "MIDI role hints must be unique" }
    }
}

data class MidiTrackSummary(
    val trackIndex: Int,
    val name: String?,
    val channels: List<MidiChannelSummary>,
    val durationTicks: Long = 0L,
) {
    init {
        require(trackIndex >= 0) { "MIDI track summary index must not be negative" }
        require(channels.map(MidiChannelSummary::channel) == channels.map(MidiChannelSummary::channel).sorted()) { "MIDI channel summaries must be ordered" }
        require(durationTicks >= 0L) { "MIDI track duration must not be negative" }
    }
}

data class MidiInspectionResult(
    val sequence: SemanticMidiSequence,
    val trackSummaries: List<MidiTrackSummary>,
    val findings: List<MidiReaderIssue>,
    val sourceEndTick: Long,
) {
    init {
        require(trackSummaries.size == sequence.tracks.size) { "Every semantic MIDI track must have one summary" }
        require(trackSummaries.map(MidiTrackSummary::trackIndex) == sequence.tracks.map(SemanticMidiTrack::index)) { "MIDI track summaries must follow semantic tracks" }
        require(sourceEndTick >= sequence.endTick) { "MIDI source end tick cannot precede semantic events" }
    }
}
