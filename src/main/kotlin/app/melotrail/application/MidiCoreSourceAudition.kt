package app.melotrail.application

import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionView
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiTempoEvent
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import app.melotrail.project.adapter.MidiCoreArtifactStore

/** Prepares an immutable source-melody view for MIDI-only audition without changing project state. */
class MidiCoreSourceAudition(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val melodySelector: MidiProtectedMelodySelector = MidiProtectedMelodySelector(),
) {
    /** Revalidate source identity and derive the one-role audition plan accepted by the MIDI port. */
    fun prepare(request: PrepareMidiCoreSourceAudition): MidiCoreSourceAuditionResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val project = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.INVALID_PROJECT,
                "The project cannot be verified before source audition.",
                "Reopen a valid MIDI Core project and retry source audition.",
            )
        }
        if (project != request.session.project) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.STALE_PROJECT,
                "The project changed while the MIDI page was open.",
                "Reload the project before starting source audition.",
            )
        }
        val source = project.sourceMidi ?: return rejected(
            MidiCoreSourceAuditionProblemCode.SOURCE_REQUIRED,
            "An imported source MIDI is required before audition.",
            "Import one Standard MIDI source first.",
        )
        val selectedMelody = project.selectedMelody ?: return rejected(
            MidiCoreSourceAuditionProblemCode.MELODY_REQUIRED,
            "A protected melody is required before source audition.",
            "Select one source track and channel on the MIDI page.",
        )
        val inspection = try {
            val inspected = reader.inspect(artifacts.verify(root, source.original))
            require(inspected.sequence.source.sha256 == source.sha256)
            require(inspected.sequence.source.format == source.format)
            require(inspected.sequence.source.ppq.value == source.ppq)
            require(inspected.sourceEndTick == source.sourceEndTick)
            inspected
        } catch (error: Exception) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.SOURCE_DIGEST_MISMATCH,
                "The preserved source MIDI no longer matches project identity.",
                "Restore the original source artifact and reopen the project.",
            )
        }
        val protectedMelody = try {
            melodySelector.select(
                inspection.sequence,
                MidiMelodySelection(selectedMelody.trackIndex, selectedMelody.channel),
            )
        } catch (error: Exception) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.MELODY_IDENTITY_MISMATCH,
                "The protected melody cannot be re-derived from the preserved source.",
                "Restore the original source artifact or create a new project and import it again.",
            )
        }
        if (protectedMelody.identitySha256 != selectedMelody.identitySha256 || protectedMelody.sourceSha256 != source.sha256) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.MELODY_IDENTITY_MISMATCH,
                "The protected melody identity no longer matches the preserved source.",
                "Restore the original source artifact or create a new project and import it again.",
            )
        }
        if (source.sourceEndTick <= 0L) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.SOURCE_NOT_PLAYABLE,
                "The imported source has no positive MIDI playback range.",
                "Choose a source with at least one complete note after tick zero.",
            )
        }

        val tempo = project.authority?.tempo?.microsecondsPerQuarter
            ?: inspection.sequence.orderedEvents().filterIsInstance<MidiTempoEvent>().firstOrNull { it.orderingKey.tick == 0L }?.microsecondsPerQuarter
            ?: DEFAULT_TEMPO_MICROSECONDS_PER_QUARTER
        val meter = project.authority?.meter?.let { it.numerator to it.denominatorExponent }
            ?: inspection.sequence.orderedEvents().filterIsInstance<MidiTimeSignatureEvent>().firstOrNull { it.orderingKey.tick == 0L }
                ?.let { it.numerator to it.denominatorExponent }
            ?: DEFAULT_METER
        val song = try {
            MidiExportSong(
                ppq = protectedMelody.ppq,
                sequenceName = project.metadata.name,
                tempoMicrosecondsPerQuarter = tempo,
                meterNumerator = meter.first,
                meterDenominatorExponent = meter.second,
                markers = emptyList(),
                roles = listOf(MidiExportRoleTrack(MidiExportRole.MELODY, protectedMelody.events)),
                songEndTick = source.sourceEndTick,
            )
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.SOURCE_NOT_PLAYABLE,
                error.message ?: "The protected melody cannot be opened for MIDI audition.",
                "Create a new project and import one valid single-track melody source.",
            )
        }
        return MidiCoreSourceAuditionResult.Ready(
            MidiAuditionPlaybackPlan(MidiAuditionView.sourceMelody(song)),
        )
    }

    /** Reuse the verified protected melody song for one exact authoritative occurrence window. */
    fun prepareOccurrence(request: PrepareMidiCoreOccurrenceAudition): MidiCoreSourceAuditionResult {
        if (request.occurrenceId.isBlank()) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.OCCURRENCE_REQUIRED,
                "Choose a named section occurrence before starting occurrence audition.",
                "Define and select one authoritative section occurrence.",
            )
        }
        val prepared = prepare(PrepareMidiCoreSourceAudition(request.session))
        if (prepared !is MidiCoreSourceAuditionResult.Ready) return prepared
        val authority = request.session.project.authority ?: return rejected(
            MidiCoreSourceAuditionProblemCode.AUTHORITY_REQUIRED,
            "Musical authority is required before auditioning an occurrence.",
            "Confirm tempo, meter, key, and mode first.",
        )
        val occurrence = authority.occurrences.singleOrNull { it.id == request.occurrenceId } ?: return rejected(
            MidiCoreSourceAuditionProblemCode.OCCURRENCE_REQUIRED,
            "The requested section occurrence is not part of the current authority.",
            "Reload the structure timeline and choose a saved occurrence.",
        )
        if (occurrence.endTick > prepared.plan.view.song.songEndTick) {
            return rejected(
                MidiCoreSourceAuditionProblemCode.OCCURRENCE_NOT_PLAYABLE,
                "The selected occurrence extends beyond the preserved source playback range.",
                "Save a contiguous structure that fits inside the imported source range.",
            )
        }
        return MidiCoreSourceAuditionResult.Ready(
            MidiAuditionPlaybackPlan(
                MidiAuditionView.occurrence(
                    occurrence.id,
                    prepared.plan.view.song,
                    occurrence.startTick,
                    occurrence.endTick,
                ),
            ),
        )
    }

    private fun rejected(
        code: MidiCoreSourceAuditionProblemCode,
        message: String,
        nextAction: String,
    ) = MidiCoreSourceAuditionResult.Rejected(MidiCoreSourceAuditionProblem(code, message, nextAction))

    private companion object {
        const val DEFAULT_TEMPO_MICROSECONDS_PER_QUARTER = 500_000
        val DEFAULT_METER = 4 to 2
    }
}

data class PrepareMidiCoreSourceAudition(val session: MidiCoreProjectSession)

data class PrepareMidiCoreOccurrenceAudition(
    val session: MidiCoreProjectSession,
    val occurrenceId: String,
)

sealed interface MidiCoreSourceAuditionResult {
    data class Ready(val plan: MidiAuditionPlaybackPlan) : MidiCoreSourceAuditionResult
    data class Rejected(val problem: MidiCoreSourceAuditionProblem) : MidiCoreSourceAuditionResult
}

data class MidiCoreSourceAuditionProblem(
    val code: MidiCoreSourceAuditionProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreSourceAuditionProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    SOURCE_REQUIRED,
    MELODY_REQUIRED,
    SOURCE_DIGEST_MISMATCH,
    MELODY_IDENTITY_MISMATCH,
    SOURCE_NOT_PLAYABLE,
    AUTHORITY_REQUIRED,
    OCCURRENCE_REQUIRED,
    OCCURRENCE_NOT_PLAYABLE,
}
