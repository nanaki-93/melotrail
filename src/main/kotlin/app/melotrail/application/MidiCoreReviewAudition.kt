package app.melotrail.application

import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionView
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.project.CandidateRole

/**
 * Prepares MIDI-only review audition views from digest-checked candidate and
 * accepted-song evidence. It owns no device and never changes project state.
 */
class MidiCoreReviewAudition(
    private val review: MidiCoreCandidateReview = MidiCoreCandidateReview(),
    private val assembly: MidiCoreAcceptedSongAssembly = MidiCoreAcceptedSongAssembly(),
) {
    /** Prepare one inspectable candidate, including stale evidence, for audition. */
    fun candidate(request: PrepareMidiCoreCandidateAudition): MidiCoreReviewAuditionResult {
        val listed = review.list(
            ListMidiCoreCandidates(
                request.session,
                request.role,
                request.occurrenceId,
                request.expectedRevision,
            ),
        )
        val candidates = when (listed) {
            is MidiCoreCandidateReviewResult.Listed -> listed.candidates
            is MidiCoreCandidateReviewResult.Rejected -> return rejected(listed.problem.message, listed.problem.nextAction)
            is MidiCoreCandidateReviewResult.Compared -> error("Candidate listing must not return comparison evidence")
        }
        val item = candidates.singleOrNull { it.candidate.id == request.candidateId }
            ?: return rejected(
                "The requested candidate is not available in the selected Review scope.",
                "Refresh candidate evidence and choose an inspectable candidate.",
            )
        val authority = request.session.project.authority ?: return rejected(
            "Confirmed musical authority is required before candidate audition.",
            "Complete tempo, meter, key, structure, and harmony before auditioning candidate evidence.",
        )
        val source = request.session.project.sourceMidi ?: return rejected(
            "An imported source MIDI record is required before candidate audition.",
            "Import and preserve one source MIDI before starting Review audition.",
        )
        val song = try {
            MidiExportSong(
                ppq = MidiPpq(source.ppq),
                sequenceName = request.session.project.metadata.name,
                tempoMicrosecondsPerQuarter = authority.tempo.microsecondsPerQuarter,
                meterNumerator = authority.meter.numerator,
                meterDenominatorExponent = authority.meter.denominatorExponent,
                markers = markers(authority),
                roles = listOf(
                    MidiExportRoleTrack(
                        exportRole(request.role),
                        item.notes.mapIndexed { index, note ->
                            MidiNoteEvent(
                                MidiEventOrderingKey(
                                    note.startTick,
                                    MidiSemanticEventKind.NOTE,
                                    generatedEventKey = index.toLong(),
                                ),
                                note.endTick,
                                note.channel,
                                note.pitch,
                                note.velocity,
                            )
                        },
                    ),
                ),
                songEndTick = authority.occurrences.lastOrNull()?.endTick
                    ?: return rejected(
                        "A complete occurrence timeline is required before candidate audition.",
                        "Save at least one contiguous section occurrence before starting Review audition.",
                    ),
            )
        } catch (error: IllegalArgumentException) {
            return rejected(
                error.message ?: "Candidate evidence cannot be prepared for MIDI audition.",
                "Restore the candidate evidence or choose another candidate.",
            )
        }
        return MidiCoreReviewAuditionResult.Ready(
            MidiAuditionPlaybackPlan(MidiAuditionView.candidate(item.candidate.id, exportRole(request.role), song)),
        )
    }

    /** Prepare the currently accepted material for one role across the whole song. */
    fun role(request: PrepareMidiCoreAcceptedRoleAudition): MidiCoreReviewAuditionResult =
        acceptedSong(request.session, request.expectedRevision, setOf(request.role)) { review ->
            val role = exportRole(request.role)
            MidiAuditionPlaybackPlan(MidiAuditionView.role(role, review.song.copy(roles = listOf(review.song.role(role)))))
        }

    /** Prepare all accepted roles and the protected melody inside one exact occurrence. */
    fun occurrence(request: PrepareMidiCoreAcceptedOccurrenceAudition): MidiCoreReviewAuditionResult {
        val occurrence = request.session.project.authority?.occurrences?.singleOrNull { it.id == request.occurrenceId }
            ?: return rejected(
                "The requested occurrence is not part of the current authority.",
                "Reload Structure & Harmony and choose a saved occurrence.",
            )
        return acceptedSong(request.session, request.expectedRevision, CandidateRole.entries.toSet()) { review ->
            MidiAuditionPlaybackPlan(MidiAuditionView.occurrence(occurrence.id, review.song, occurrence.startTick, occurrence.endTick))
        }
    }

    /** Prepare the protected melody plus every currently accepted core role. */
    fun acceptedArrangement(request: PrepareMidiCoreAcceptedArrangementAudition): MidiCoreReviewAuditionResult =
        acceptedSong(request.session, request.expectedRevision, CandidateRole.entries.toSet()) { review ->
            MidiAuditionPlaybackPlan(MidiAuditionView.accepted(review.song))
        }

    /** Prepare a validated persisted full draft before any acceptance pointer is changed. */
    fun draft(request: PrepareMidiCoreArrangementDraftAudition): MidiCoreReviewAuditionResult = when (
        val assembled = assembly.assembleDraft(AssembleMidiCoreArrangementDraft(request.session, request.draftId, request.expectedRevision))
    ) {
        is MidiCoreArrangementDraftAssemblyResult.Rejected -> rejected(assembled.problem.message, assembled.problem.nextAction)
        is MidiCoreArrangementDraftAssemblyResult.Assembled -> MidiCoreReviewAuditionResult.Ready(
            MidiAuditionPlaybackPlan(MidiAuditionView.draft(assembled.review.draft.id, assembled.review.song)),
        )
    }

    private fun acceptedSong(
        session: MidiCoreProjectSession,
        expectedRevision: Long?,
        roles: Set<CandidateRole>,
        view: (MidiCoreAcceptedSongReview) -> MidiAuditionPlaybackPlan,
    ): MidiCoreReviewAuditionResult {
        val assembled = assembly.assemble(AssembleMidiCoreSong(session, roles, expectedRevision))
        return when (assembled) {
            is MidiCoreAcceptedSongAssemblyResult.Rejected -> rejected(assembled.problem.message, assembled.problem.nextAction)
            is MidiCoreAcceptedSongAssemblyResult.Assembled -> MidiCoreReviewAuditionResult.Ready(view(assembled.review))
        }
    }

    private fun markers(authority: app.melotrail.project.ProjectAuthority): List<MidiExportMarker> =
        authority.occurrences.mapIndexed { index, occurrence -> MidiExportMarker(index + 1, occurrence.label, occurrence.startTick) }

    private fun exportRole(role: CandidateRole): MidiExportRole = when (role) {
        CandidateRole.CHORDS -> MidiExportRole.CHORDS
        CandidateRole.BASS -> MidiExportRole.BASS
        CandidateRole.DRUMS -> MidiExportRole.DRUMS
    }

    private fun rejected(message: String, nextAction: String) =
        MidiCoreReviewAuditionResult.Rejected(MidiCoreReviewAuditionProblem(message, nextAction))
}

data class PrepareMidiCoreCandidateAudition(
    val session: MidiCoreProjectSession,
    val candidateId: String,
    val role: CandidateRole,
    val occurrenceId: String,
    val expectedRevision: Long? = session.project.revision,
)

data class PrepareMidiCoreAcceptedRoleAudition(
    val session: MidiCoreProjectSession,
    val role: CandidateRole,
    val expectedRevision: Long? = session.project.revision,
)

data class PrepareMidiCoreAcceptedOccurrenceAudition(
    val session: MidiCoreProjectSession,
    val occurrenceId: String,
    val expectedRevision: Long? = session.project.revision,
)

data class PrepareMidiCoreAcceptedArrangementAudition(
    val session: MidiCoreProjectSession,
    val expectedRevision: Long? = session.project.revision,
)

data class PrepareMidiCoreArrangementDraftAudition(
    val session: MidiCoreProjectSession,
    val draftId: String,
    val expectedRevision: Long? = session.project.revision,
)

sealed interface MidiCoreReviewAuditionResult {
    data class Ready(val plan: MidiAuditionPlaybackPlan) : MidiCoreReviewAuditionResult
    data class Rejected(val problem: MidiCoreReviewAuditionProblem) : MidiCoreReviewAuditionResult
}

data class MidiCoreReviewAuditionProblem(
    val message: String,
    val nextAction: String,
)
