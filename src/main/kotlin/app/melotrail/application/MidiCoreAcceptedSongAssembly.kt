package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path

/** Assembles an in-memory review sequence from immutable source and accepted MIDI evidence. */
class MidiCoreAcceptedSongAssembly(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val melodySelector: MidiProtectedMelodySelector = MidiProtectedMelodySelector(),
) {
    /** Build one deterministic protected-melody-plus-accepted-roles review sequence without writing artifacts. */
    fun assemble(request: AssembleMidiCoreSong): MidiCoreAcceptedSongAssemblyResult {
        val loaded = when (val result = load(request)) {
            is AssemblyLoad.Ready -> result
            is AssemblyLoad.Rejected -> return result.result
        }
        val project = loaded.project
        val authority = project.authority ?: return rejected(
            MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED,
            "Confirmed musical authority is required before assembling the song.",
            "Complete tempo, meter, key, structure, and harmony before reviewing the accepted song.",
        )
        if (authority.occurrences.isEmpty()) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED,
                "At least one section occurrence is required for an accepted-song review sequence.",
                "Define a contiguous section occurrence timeline before assembling the song.",
            )
        }
        val source = project.sourceMidi ?: return rejected(
            MidiCoreSongAssemblyProblemCode.SOURCE_REQUIRED,
            "An imported source MIDI is required before assembling the song.",
            "Import and preserve one source MIDI before reviewing accepted work.",
        )
        val selectedMelody = project.selectedMelody ?: return rejected(
            MidiCoreSongAssemblyProblemCode.MELODY_REQUIRED,
            "A protected melody is required before assembling the song.",
            "Select exactly one source track and channel before reviewing accepted work.",
        )
        val authorityFingerprint = try {
            MidiCoreAuthorityHasher.from(project)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED,
                error.message ?: "Current musical authority could not be resolved safely.",
                "Restore complete tempo, meter, key, structure, and harmony authority before reviewing the song.",
            )
        }
        try {
            app.melotrail.structure.MidiCoreHarmonyTimeline.build(authority)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED,
                error.message ?: "Authoritative harmony is incomplete or invalid.",
                "Provide gap-free chord windows for every section occurrence before reviewing the song.",
            )
        }

        val sourceInspection = try {
            val sourcePath = artifacts.verify(loaded.root, source.original)
            val inspected = reader.inspect(sourcePath)
            require(inspected.sequence.source.sha256 == source.sha256)
            require(inspected.sequence.source.format == source.format)
            require(inspected.sequence.source.ppq.value == source.ppq)
            require(inspected.sourceEndTick == source.sourceEndTick)
            inspected
        } catch (error: Exception) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.SOURCE_DIGEST_MISMATCH,
                "The preserved source MIDI cannot be revalidated against project identity.",
                "Restore the immutable source artifact before assembling the accepted song.",
            )
        }
        val protectedMelody = try {
            melodySelector.select(
                sourceInspection.sequence,
                MidiMelodySelection(selectedMelody.trackIndex, selectedMelody.channel),
            )
        } catch (error: Exception) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.MELODY_IDENTITY_MISMATCH,
                "The protected melody cannot be re-derived from the preserved source.",
                "Restore the original source artifact or create a new project and import one valid single-track melody source.",
            )
        }
        if (protectedMelody.identitySha256 != selectedMelody.identitySha256 || protectedMelody.sourceSha256 != source.sha256) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.MELODY_IDENTITY_MISMATCH,
                "The protected melody identity no longer matches the preserved source.",
                "Restore the original source artifact or create a new project and import it again.",
            )
        }

        val songEndTick = authority.occurrences.last().endTick
        if (protectedMelody.events.any { event ->
                event.orderingKey.tick > songEndTick || (event is MidiNoteEvent && event.endTick > songEndTick)
            }) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.MELODY_OVERFLOW,
                "Protected melody evidence extends beyond the authoritative song boundary.",
                "Correct the source so its declared end contains every melody event, then import it into a new project.",
            )
        }

        val duplicateScopes = project.acceptances.groupBy { it.occurrenceId to it.role }.filterValues { it.size > 1 }
        if (duplicateScopes.isNotEmpty()) {
            val scope = duplicateScopes.keys.sortedWith(compareBy<Pair<String, CandidateRole>> { it.first }.thenBy { it.second.ordinal }).first()
            return rejected(
                MidiCoreSongAssemblyProblemCode.DUPLICATE_ROLE_SCOPE,
                "More than one accepted pointer exists for ${scope.second.name} occurrence '${scope.first}'.",
                "Repair the project review state so each role and occurrence has one accepted candidate.",
                occurrenceId = scope.first,
                role = scope.second,
            )
        }

        val acceptedCandidates = mutableListOf<MidiCoreAcceptedSongCandidate>()
        val roleNotes = request.roles.associateWith { mutableListOf<MidiCoreReviewNote>() }
        request.roles.sortedBy(CandidateRole::ordinal).forEach { role ->
            authority.occurrences.forEach { occurrence ->
                val acceptance = project.acceptances.singleOrNull { it.role == role && it.occurrenceId == occurrence.id }
                    ?: return rejected(
                        MidiCoreSongAssemblyProblemCode.MISSING_ACCEPTANCE,
                        "No accepted $role candidate exists for occurrence '${occurrence.id}'.",
                        "Accept one candidate for every enabled role and occurrence before reviewing the song.",
                        occurrenceId = occurrence.id,
                        role = role,
                    )
                val candidate = project.candidates.singleOrNull { it.id == acceptance.candidateId }
                    ?: return rejected(
                        MidiCoreSongAssemblyProblemCode.CANDIDATE_SCOPE_MISMATCH,
                        "The accepted candidate '${acceptance.candidateId}' is missing from project state.",
                        "Restore the candidate record or choose another accepted candidate for this scope.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = acceptance.candidateId,
                    )
                if (candidate.role != role || candidate.occurrenceId != occurrence.id) {
                    return rejected(
                        MidiCoreSongAssemblyProblemCode.CANDIDATE_SCOPE_MISMATCH,
                        "The accepted candidate does not belong to ${role} occurrence '${occurrence.id}'.",
                        "Choose a candidate recorded for the exact role and occurrence.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = candidate.id,
                    )
                }
                if (candidate.status == MidiCoreCandidateStatus.STALE) {
                    return rejected(
                        MidiCoreSongAssemblyProblemCode.CANDIDATE_STALE,
                        "The accepted candidate '${candidate.id}' is stale for current authority.",
                        "Regenerate and explicitly accept a current candidate for this scope.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = candidate.id,
                    )
                }
                if (candidate.status != MidiCoreCandidateStatus.ACCEPTED) {
                    return rejected(
                        MidiCoreSongAssemblyProblemCode.CANDIDATE_NOT_ACCEPTED,
                        "Candidate '${candidate.id}' is not marked as the accepted candidate for this scope.",
                        "Accept the candidate through Review before assembling the song.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = candidate.id,
                    )
                }
                val expectedAuthorityHash = try {
                    authorityFingerprint.scopeHash(occurrence.id, role)
                } catch (error: IllegalArgumentException) {
                    return rejected(
                        MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED,
                        error.message ?: "The accepted candidate scope has no current authority.",
                        "Restore the current structure and harmony authority before reviewing the song.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = candidate.id,
                    )
                }
                if (candidate.authorityHash != expectedAuthorityHash) {
                    return rejected(
                        MidiCoreSongAssemblyProblemCode.CANDIDATE_STALE,
                        "Candidate '${candidate.id}' was generated against a different authority scope.",
                        "Regenerate and explicitly accept a current candidate for this scope.",
                        occurrenceId = occurrence.id,
                        role = role,
                        candidateId = candidate.id,
                    )
                }
                val notes = when (val result = readCandidateNotes(loaded.root, project, candidate, occurrence, role)) {
                    is CandidateNotesLoad.Ready -> result.notes
                    is CandidateNotesLoad.Rejected -> return result.result
                }
                roleNotes.getValue(role) += notes
                acceptedCandidates += MidiCoreAcceptedSongCandidate(
                    occurrence.id,
                    role,
                    candidate.id,
                    candidate.midi,
                    candidate.validationReport,
                )
            }
        }

        val song = try {
            MidiExportSong(
                ppq = app.melotrail.midi.domain.MidiPpq(source.ppq),
                sequenceName = project.metadata.name,
                tempoMicrosecondsPerQuarter = authority.tempo.microsecondsPerQuarter,
                meterNumerator = authority.meter.numerator,
                meterDenominatorExponent = authority.meter.denominatorExponent,
                markers = authority.occurrences.mapIndexed { index, occurrence ->
                    MidiExportMarker(index + 1, occurrence.label, occurrence.startTick)
                },
                roles = listOf(
                    MidiExportRoleTrack(MidiExportRole.MELODY, protectedMelody.events),
                    *request.roles.sortedBy(CandidateRole::ordinal).map { role -> roleTrack(role, roleNotes.getValue(role)) }.toTypedArray(),
                ),
                songEndTick = songEndTick,
            )
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreSongAssemblyProblemCode.SONG_OVERFLOW,
                error.message ?: "The assembled review sequence is outside the authoritative song boundary.",
                "Repair the source or accepted candidate timing before reviewing the song.",
            )
        }
        return MidiCoreAcceptedSongAssemblyResult.Assembled(
            MidiCoreAcceptedSongReview(
                session = MidiCoreProjectSession(loaded.root, project),
                sourceSha256 = source.sha256,
                selectedMelodyIdentitySha256 = selectedMelody.identitySha256,
                authorityHash = authorityFingerprint.sha256,
                acceptedCandidates = acceptedCandidates.sortedWith(
                    compareBy<MidiCoreAcceptedSongCandidate> { item ->
                        authority.occurrences.single { it.id == item.occurrenceId }.startTick
                    }.thenBy { it.role.ordinal },
                ),
                song = song,
            ),
        )
    }

    private fun readCandidateNotes(
        root: Path,
        project: MidiCoreProject,
        candidate: MidiCoreCandidate,
        occurrence: ProjectSectionOccurrence,
        role: CandidateRole,
    ): CandidateNotesLoad {
        val (midiPath, reportPath) = try {
            artifacts.verify(root, candidate.midi) to artifacts.verify(root, candidate.validationReport)
        } catch (error: Exception) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.DIGEST_MISMATCH,
                "Candidate '${candidate.id}' MIDI or validation evidence failed digest verification.",
                "Restore the immutable candidate evidence or publish and accept a new candidate.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        val report = try {
            MidiCoreRoleValidationReportJson.decode(Files.readString(reportPath))
        } catch (error: Exception) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.VALIDATION_FAILED,
                "Candidate '${candidate.id}' validation evidence is unreadable.",
                "Publish a candidate with a valid deterministic validation report.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        if (report.role != role || report.occurrenceId != occurrence.id) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.CANDIDATE_SCOPE_MISMATCH,
                "Candidate '${candidate.id}' validation evidence has the wrong role or occurrence.",
                "Choose a candidate whose report matches the selected role and occurrence.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        if (!report.passed) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.VALIDATION_FAILED,
                "Candidate '${candidate.id}' contains blocking validation findings.",
                "Regenerate and accept a candidate that passes the target role validator.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        val inspection = try {
            reader.inspect(midiPath)
        } catch (error: Exception) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.CANDIDATE_FORMAT_MISMATCH,
                "Candidate '${candidate.id}' MIDI could not be read as target role evidence.",
                "Regenerate the candidate and preserve its generated MIDI artifact.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        if (inspection.sequence.source.format != 1 || inspection.sequence.source.ppq.value != project.sourceMidi?.ppq ||
            inspection.sourceEndTick != occurrence.endTick ||
            inspection.trackSummaries.map { it.name } != listOf("Conductor", exportRole(role).trackName) ||
            inspection.sequence.tracks.size != 2
        ) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.CANDIDATE_FORMAT_MISMATCH,
                "Candidate '${candidate.id}' does not use the target role-file format or exact occurrence boundary.",
                "Regenerate the candidate with the current MIDI Core writer.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        val roleEvents = inspection.sequence.tracks[1].events
        if (roleEvents.any { event -> event !is app.melotrail.midi.domain.MidiTrackNameEvent && event !is MidiNoteEvent }) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.INVALID_ROLE_EVENT,
                "Candidate '${candidate.id}' contains an event that is not a generated role note.",
                "Regenerate the candidate without controllers, program changes, or unsupported MIDI events.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        val expectedChannel = exportRole(role).channel
        val noteEvents = roleEvents.filterIsInstance<MidiNoteEvent>()
        if (noteEvents.any { note -> note.channel != expectedChannel }) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.CANDIDATE_CHANNEL_MISMATCH,
                "Candidate '${candidate.id}' contains notes on a channel other than ${expectedChannel + 1}.",
                "Regenerate the role with the documented MIDI channel policy.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        if (noteEvents.any { note -> note.orderingKey.tick < occurrence.startTick || note.endTick > occurrence.endTick }) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.CANDIDATE_OVERFLOW,
                "Candidate '${candidate.id}' crosses the exact '${occurrence.id}' occurrence boundary.",
                "Regenerate the role so every note remains inside its authoritative occurrence.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        val notes = noteEvents.map { note ->
            MidiCoreReviewNote(note.orderingKey.tick, note.endTick, note.channel, note.pitch, note.velocity)
        }.sortedWith(compareBy<MidiCoreReviewNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.channel }.thenBy { it.pitch }.thenBy { it.velocity })
        if (report.noteCount != notes.size || notes.distinct().size != notes.size) {
            return CandidateNotesLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.VALIDATION_FAILED,
                "Candidate '${candidate.id}' validation evidence does not match its semantic role notes.",
                "Regenerate and accept a candidate with consistent MIDI and validation evidence.",
                occurrenceId = occurrence.id,
                role = role,
                candidateId = candidate.id,
            ))
        }
        return CandidateNotesLoad.Ready(notes)
    }

    private fun roleTrack(role: CandidateRole, notes: List<MidiCoreReviewNote>): MidiExportRoleTrack {
        val exportRole = exportRole(role)
        return MidiExportRoleTrack(
            exportRole,
            notes.mapIndexed { index, note ->
                MidiNoteEvent(
                    MidiEventOrderingKey(note.startTick, MidiSemanticEventKind.NOTE, generatedEventKey = index.toLong()),
                    note.endTick,
                    exportRole.channel,
                    note.pitch,
                    note.velocity,
                )
            },
        )
    }

    private fun load(request: AssembleMidiCoreSong): AssemblyLoad {
        if (request.expectedRevision != null && request.expectedRevision < 0L) {
            return AssemblyLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.REVISION_CONFLICT,
                "The expected project revision is invalid.",
                "Reload the project before assembling the accepted song.",
            ))
        }
        val root = request.session.root.toAbsolutePath().normalize()
        val project = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            val code = if (error.message.orEmpty().contains("digest", ignoreCase = true)) {
                MidiCoreSongAssemblyProblemCode.DIGEST_MISMATCH
            } else {
                MidiCoreSongAssemblyProblemCode.INVALID_PROJECT
            }
            return AssemblyLoad.Rejected(rejected(code, "The project cannot be verified for accepted-song review.", "Open a valid MIDI Core project and retry."))
        }
        if (request.expectedRevision != null && project.revision != request.expectedRevision) {
            return AssemblyLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.REVISION_CONFLICT,
                "The project changed from revision ${request.expectedRevision} to ${project.revision}.",
                "Reload the Review page before assembling the accepted song.",
            ))
        }
        if (project != request.session.project) {
            return AssemblyLoad.Rejected(rejected(
                MidiCoreSongAssemblyProblemCode.STALE_PROJECT,
                "The project changed since this review view was opened.",
                "Reopen the Review page before assembling the accepted song.",
            ))
        }
        return AssemblyLoad.Ready(root, project)
    }

    private fun exportRole(role: CandidateRole): MidiExportRole = when (role) {
        CandidateRole.CHORDS -> MidiExportRole.CHORDS
        CandidateRole.BASS -> MidiExportRole.BASS
        CandidateRole.DRUMS -> MidiExportRole.DRUMS
    }

    private fun rejected(
        code: MidiCoreSongAssemblyProblemCode,
        message: String,
        nextAction: String,
        occurrenceId: String? = null,
        role: CandidateRole? = null,
        candidateId: String? = null,
    ) = MidiCoreAcceptedSongAssemblyResult.Rejected(
        MidiCoreSongAssemblyProblem(code, message, nextAction, occurrenceId, role, candidateId),
    )

    private sealed interface AssemblyLoad {
        data class Ready(val root: Path, val project: MidiCoreProject) : AssemblyLoad
        data class Rejected(val result: MidiCoreAcceptedSongAssemblyResult.Rejected) : AssemblyLoad
    }

    private sealed interface CandidateNotesLoad {
        data class Ready(val notes: List<MidiCoreReviewNote>) : CandidateNotesLoad
        data class Rejected(val result: MidiCoreAcceptedSongAssemblyResult.Rejected) : CandidateNotesLoad
    }
}

data class AssembleMidiCoreSong(
    val session: MidiCoreProjectSession,
    val roles: Set<CandidateRole> = CandidateRole.entries.toSet(),
    val expectedRevision: Long? = session.project.revision,
) {
    init {
        require(roles.all { it in CandidateRole.entries }) { "Accepted-song roles must be target MIDI Core roles" }
    }
}

data class MidiCoreAcceptedSongCandidate(
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val midi: ProjectArtifact,
    val validationReport: ProjectArtifact,
)

data class MidiCoreAcceptedSongReview(
    val session: MidiCoreProjectSession,
    val sourceSha256: String,
    val selectedMelodyIdentitySha256: String,
    val authorityHash: String,
    val acceptedCandidates: List<MidiCoreAcceptedSongCandidate>,
    val song: MidiExportSong,
) {
    init {
        require(SHA_256.matches(sourceSha256) && SHA_256.matches(selectedMelodyIdentitySha256) && SHA_256.matches(authorityHash)) {
            "Accepted-song review identity must use SHA-256 values"
        }
        require(acceptedCandidates.map { it.occurrenceId to it.role }.distinct().size == acceptedCandidates.size) {
            "Accepted-song review candidates must have one role per occurrence"
        }
        require(song.roles.firstOrNull()?.role == MidiExportRole.MELODY) {
            "Accepted-song review must begin with the protected Melody role"
        }
    }
}

enum class MidiCoreSongAssemblyProblemCode {
    INVALID_PROJECT,
    REVISION_CONFLICT,
    STALE_PROJECT,
    SOURCE_REQUIRED,
    SOURCE_DIGEST_MISMATCH,
    MELODY_REQUIRED,
    MELODY_IDENTITY_MISMATCH,
    MELODY_OVERFLOW,
    AUTHORITY_REQUIRED,
    MISSING_ACCEPTANCE,
    DUPLICATE_ROLE_SCOPE,
    CANDIDATE_SCOPE_MISMATCH,
    CANDIDATE_NOT_ACCEPTED,
    CANDIDATE_STALE,
    DIGEST_MISMATCH,
    VALIDATION_FAILED,
    CANDIDATE_FORMAT_MISMATCH,
    INVALID_ROLE_EVENT,
    CANDIDATE_CHANNEL_MISMATCH,
    CANDIDATE_OVERFLOW,
    SONG_OVERFLOW,
}

data class MidiCoreSongAssemblyProblem(
    val code: MidiCoreSongAssemblyProblemCode,
    val message: String,
    val nextAction: String,
    val occurrenceId: String? = null,
    val role: CandidateRole? = null,
    val candidateId: String? = null,
)

sealed interface MidiCoreAcceptedSongAssemblyResult {
    data class Assembled(val review: MidiCoreAcceptedSongReview) : MidiCoreAcceptedSongAssemblyResult
    data class Rejected(val problem: MidiCoreSongAssemblyProblem) : MidiCoreAcceptedSongAssemblyResult
}

private val SHA_256 = Regex("[0-9a-f]{64}")
