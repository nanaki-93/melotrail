package app.melotrail.midi.domain

/** The deterministic, DAW-oriented subset written by the target MIDI adapter. */
enum class MidiExportRole(val trackName: String, val channel: Int) {
    MELODY("Melody", 0),
    CHORDS("Chords", 1),
    BASS("Bass", 2),
    DRUMS("Drums", 9),
}

data class MidiExportMarker(val ordinal: Int, val label: String, val tick: Long) {
    init {
        require(ordinal >= 1) { "Section marker ordinal must be positive" }
        require(label.isNotBlank()) { "Section marker label must not be blank" }
        require(tick >= 0) { "Section marker tick must not be negative" }
    }

    fun renderedLabel(): String = "$ordinal:${label.trim().replace(Regex("[\\p{Cntrl}]+"), " ").replace(Regex("\\s+"), " ")}".trim()
}

data class MidiExportRoleTrack(val role: MidiExportRole, val events: List<SemanticMidiEvent>) {
    init {
        require(events == events.sortedBy(SemanticMidiEvent::orderingKey)) { "Role events must use semantic ordering" }
        require(events.map(SemanticMidiEvent::orderingKey).distinct().size == events.size) { "Role events must have unique ordering keys" }
        if (role != MidiExportRole.MELODY) {
            require(events.all { it is MidiNoteEvent }) { "Generated ${role.trackName} export may contain note events only" }
        }
    }
}

data class MidiExportSong(
    val ppq: MidiPpq,
    val sequenceName: String,
    val tempoMicrosecondsPerQuarter: Int,
    val meterNumerator: Int,
    val meterDenominatorExponent: Int,
    val markers: List<MidiExportMarker>,
    val roles: List<MidiExportRoleTrack>,
    val songEndTick: Long,
) {
    init {
        require(sequenceName.isNotBlank()) { "MIDI export sequence name must not be blank" }
        require(tempoMicrosecondsPerQuarter > 0) { "MIDI export tempo must be positive" }
        require(meterNumerator in 1..255 && meterDenominatorExponent in 0..30) { "MIDI export meter is invalid" }
        require(markers == markers.sortedWith(compareBy(MidiExportMarker::tick, MidiExportMarker::ordinal))) { "Section markers must be ordered by tick and ordinal" }
        require(markers.map(MidiExportMarker::ordinal).distinct().size == markers.size) { "Section marker ordinals must be unique" }
        require(roles.map(MidiExportRoleTrack::role).distinct().size == roles.size) { "An export may contain each role once" }
        require(roles.map(MidiExportRoleTrack::role) == roles.map(MidiExportRoleTrack::role).sortedBy(MidiExportRole::ordinal)) { "Export roles must use deterministic role order" }
        require(songEndTick >= 0) { "MIDI export song end must not be negative" }
        require(roles.flatMap(MidiExportRoleTrack::events).all { eventEndTick(it) <= songEndTick }) { "MIDI export events must not exceed the song end" }
    }

    fun role(role: MidiExportRole): MidiExportRoleTrack = requireNotNull(roles.singleOrNull { it.role == role }) {
        "Export does not contain ${role.trackName}"
    }

    private fun eventEndTick(event: SemanticMidiEvent): Long = if (event is MidiNoteEvent) event.endTick else event.orderingKey.tick
}
