package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Lists and compares immutable role alternatives and delegates explicit review decisions. */
class MidiCoreCandidateReview(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val lifecycle: MidiCoreCandidateLifecycle = MidiCoreCandidateLifecycle(artifacts = artifacts),
    private val generation: MidiCoreCandidateGeneration = MidiCoreCandidateGeneration(artifacts = artifacts),
    private val reader: JdkMidiReader = JdkMidiReader(),
) {
    /** List all inspectable candidates for one role and occurrence in deterministic order. */
    fun list(request: ListMidiCoreCandidates): MidiCoreCandidateReviewResult {
        val loaded = when (val result = load(request.session, request.expectedRevision)) {
            is ReviewLoad.Ready -> result
            is ReviewLoad.Rejected -> return result.result
        }
        val currentAuthority = try {
            MidiCoreAuthorityHasher.from(loaded.project)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                "Candidate review requires current musical authority.",
                "Complete tempo, meter, key, structure, and harmony before reviewing candidates.",
            )
        }
        val items = loaded.project.candidates
            .filter { it.role == request.role && it.occurrenceId == request.occurrenceId }
            .sortedWith(compareBy<MidiCoreCandidate> { it.createdAt }.thenBy { it.id })
            .map { candidate ->
                when (val item = inspectCandidate(loaded.root, loaded.project, currentAuthority, candidate, request.role, request.occurrenceId)) {
                    is ItemLoad.Ready -> item.item
                    is ItemLoad.Rejected -> return item.result
                }
            }
        return MidiCoreCandidateReviewResult.Listed(
            MidiCoreProjectSession(loaded.root, loaded.project),
            loaded.project.revision,
            items,
        )
    }

    /** Compare two same-scope candidates using deterministic semantic note additions, removals, and changes. */
    fun compare(request: CompareMidiCoreCandidates): MidiCoreCandidateReviewResult {
        val loaded = when (val result = load(request.session, request.expectedRevision)) {
            is ReviewLoad.Ready -> result
            is ReviewLoad.Rejected -> return result.result
        }
        val currentAuthority = try {
            MidiCoreAuthorityHasher.from(loaded.project)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                "Candidate comparison requires current musical authority.",
                "Complete tempo, meter, key, structure, and harmony before comparing candidates.",
            )
        }
        val first = when (val result = loadItem(
            loaded,
            currentAuthority,
            request.role,
            request.occurrenceId,
            request.firstCandidateId,
        )) {
            is ItemLoad.Ready -> result.item
            is ItemLoad.Rejected -> return result.result
        }
        val second = when (val result = loadItem(
            loaded,
            currentAuthority,
            request.role,
            request.occurrenceId,
            request.secondCandidateId,
        )) {
            is ItemLoad.Ready -> result.item
            is ItemLoad.Rejected -> return result.result
        }
        val differences = MidiCoreCandidateDiff.differences(first.notes, second.notes)
        return MidiCoreCandidateReviewResult.Compared(
            MidiCoreProjectSession(loaded.root, loaded.project),
            loaded.project.revision,
            first,
            second,
            differences,
        )
    }

    /** Apply a user-authorized acceptance with the caller's optimistic project revision. */
    fun accept(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        lifecycle.accept(request.withDefaultRevision())

    /** Apply a user-authored rejection without deleting the candidate evidence. */
    fun reject(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        lifecycle.reject(request.withDefaultRevision())

    /** Lock the selected accepted role/occurrence reference against broad replacement. */
    fun lock(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        lifecycle.lock(request.withDefaultRevision())

    /** Explicitly unlock the selected accepted role/occurrence reference. */
    fun unlock(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        lifecycle.unlock(request.withDefaultRevision())

    /** Restore a previously accepted candidate after revalidating its authority and artifact digests. */
    fun restore(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        lifecycle.restore(request.withDefaultRevision())

    /** Regenerate exactly one role and occurrence without changing any accepted pointer. */
    suspend fun regenerate(request: RegenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult {
        val loaded = when (val result = load(request.generation.session, request.expectedRevision)) {
            is ReviewLoad.Ready -> result
            is ReviewLoad.Rejected -> return MidiCoreCandidateGenerationResult.Rejected(result.result.problem)
        }
        return generation.generate(request.generation.copy(session = MidiCoreProjectSession(loaded.root, loaded.project)))
    }

    private fun loadItem(
        loaded: ReviewLoad.Ready,
        currentAuthority: app.melotrail.project.MidiCoreAuthorityFingerprint,
        role: CandidateRole,
        occurrenceId: String,
        candidateId: String,
    ): ItemLoad {
        val candidate = loaded.project.candidates.singleOrNull { it.id == candidateId }
        if (candidate == null) {
            return ItemLoad.Rejected(rejected(
                MidiCoreCandidateProblemCode.CANDIDATE_NOT_FOUND,
                "The requested candidate does not exist in this project.",
                "Choose an inspectable candidate from the selected role and occurrence.",
            ))
        }
        return inspectCandidate(loaded.root, loaded.project, currentAuthority, candidate, role, occurrenceId)
    }

    private fun inspectCandidate(
        root: Path,
        project: MidiCoreProject,
        currentAuthority: app.melotrail.project.MidiCoreAuthorityFingerprint,
        candidate: MidiCoreCandidate,
        role: CandidateRole,
        occurrenceId: String,
    ): ItemLoad {
        if (candidate.role != role || candidate.occurrenceId != occurrenceId) {
            return ItemLoad.Rejected(
                rejected(
                    MidiCoreCandidateProblemCode.CANDIDATE_SCOPE_MISMATCH,
                    "The candidate does not belong to the selected role and occurrence.",
                    "Choose a candidate recorded for the selected review scope.",
                ),
            )
        }
        val report: MidiCoreRoleValidationReport
        val notes: List<MidiCoreReviewNote>
        try {
            val reportPath = artifacts.verify(root, candidate.validationReport)
            report = MidiCoreRoleValidationReportJson.decode(Files.readString(reportPath))
            require(report.role == candidate.role && report.occurrenceId == candidate.occurrenceId) {
                "Validation report scope does not match candidate scope"
            }
            val midiPath = artifacts.verify(root, candidate.midi)
            notes = readNotes(midiPath, project, role)
            require(report.noteCount == notes.size) { "Validation report note count does not match candidate MIDI" }
        } catch (error: Exception) {
            return ItemLoad.Rejected(
                rejected(
                    MidiCoreCandidateProblemCode.DIGEST_MISMATCH,
                    "Candidate MIDI or validation evidence cannot be revalidated.",
                    "Restore the immutable evidence or publish a new candidate before reviewing it.",
                ),
            )
        }
        val authorityCurrent = candidate.authorityHash == currentAuthority.scopeHash(candidate.occurrenceId, candidate.role)
        val acceptance = project.acceptances.singleOrNull {
            it.candidateId == candidate.id && it.role == candidate.role && it.occurrenceId == candidate.occurrenceId
        }
        return ItemLoad.Ready(
            MidiCoreCandidateReviewItem(
                candidate = candidate,
                validation = report,
                notes = notes,
                authorityCurrent = authorityCurrent,
                accepted = acceptance != null,
                locked = acceptance?.locked == true,
            ),
        )
    }

    private fun readNotes(path: Path, project: MidiCoreProject, role: CandidateRole): List<MidiCoreReviewNote> {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Candidate MIDI is missing" }
        val inspected = reader.inspect(path)
        require(inspected.sequence.source.format == 1)
        require(inspected.sequence.source.ppq.value == requireNotNull(project.sourceMidi).ppq)
        require(inspected.trackSummaries.map { it.name } == listOf("Conductor", exportRole(role).trackName))
        val expectedChannel = exportRole(role).channel
        return inspected.sequence.tracks.drop(1).flatMap { track ->
            track.events.filterIsInstance<MidiNoteEvent>().map { note ->
                require(note.channel == expectedChannel) { "Candidate MIDI channel does not match role" }
                MidiCoreReviewNote(note.orderingKey.tick, note.endTick, note.channel, note.pitch, note.velocity)
            }
        }.sortedWith(compareBy<MidiCoreReviewNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.velocity })
    }

    private fun load(session: MidiCoreProjectSession, expectedRevision: Long?): ReviewLoad {
        if (expectedRevision != null && expectedRevision < 0L) {
            return ReviewLoad.Rejected(rejected(
                MidiCoreCandidateProblemCode.INVALID_CANDIDATE,
                "The expected project revision is invalid.",
                "Reload the project and retry the review operation.",
            ))
        }
        val root = session.root.toAbsolutePath().normalize()
        val project = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            val code = if (error.message.orEmpty().contains("digest", ignoreCase = true)) {
                MidiCoreCandidateProblemCode.DIGEST_MISMATCH
            } else {
                MidiCoreCandidateProblemCode.INVALID_PROJECT
            }
            return ReviewLoad.Rejected(rejected(code, "The project cannot be verified for candidate review.", "Open a valid MIDI Core project and retry."))
        }
        if (expectedRevision != null && project.revision != expectedRevision) {
            return ReviewLoad.Rejected(rejected(
                MidiCoreCandidateProblemCode.REVISION_CONFLICT,
                "The project changed from revision $expectedRevision to ${project.revision}.",
                "Reload the Review page before applying another decision.",
            ))
        }
        if (project != session.project) {
            return ReviewLoad.Rejected(rejected(
                MidiCoreCandidateProblemCode.STALE_PROJECT,
                "The project changed since this Review view was opened.",
                "Reopen the Review page before continuing.",
            ))
        }
        return ReviewLoad.Ready(root, project)
    }

    private fun AcceptMidiCoreCandidate.withDefaultRevision(): AcceptMidiCoreCandidate =
        if (expectedRevision == null) copy(expectedRevision = session.project.revision) else this

    private fun RejectMidiCoreCandidate.withDefaultRevision(): RejectMidiCoreCandidate =
        if (expectedRevision == null) copy(expectedRevision = session.project.revision) else this

    private fun LockMidiCoreCandidate.withDefaultRevision(): LockMidiCoreCandidate =
        if (expectedRevision == null) copy(expectedRevision = session.project.revision) else this

    private fun UnlockMidiCoreCandidate.withDefaultRevision(): UnlockMidiCoreCandidate =
        if (expectedRevision == null) copy(expectedRevision = session.project.revision) else this

    private fun RestoreMidiCoreCandidate.withDefaultRevision(): RestoreMidiCoreCandidate =
        if (expectedRevision == null) copy(expectedRevision = session.project.revision) else this

    private fun rejected(code: MidiCoreCandidateProblemCode, message: String, nextAction: String) =
        MidiCoreCandidateReviewResult.Rejected(MidiCoreCandidateProblem(code, message, nextAction))

    private fun exportRole(role: CandidateRole): app.melotrail.midi.domain.MidiExportRole = when (role) {
        CandidateRole.CHORDS -> app.melotrail.midi.domain.MidiExportRole.CHORDS
        CandidateRole.BASS -> app.melotrail.midi.domain.MidiExportRole.BASS
        CandidateRole.DRUMS -> app.melotrail.midi.domain.MidiExportRole.DRUMS
    }

    private sealed interface ReviewLoad {
        data class Ready(val root: Path, val project: MidiCoreProject) : ReviewLoad
        data class Rejected(val result: MidiCoreCandidateReviewResult.Rejected) : ReviewLoad
    }

    private sealed interface ItemLoad {
        data class Ready(val item: MidiCoreCandidateReviewItem) : ItemLoad
        data class Rejected(val result: MidiCoreCandidateReviewResult.Rejected) : ItemLoad
    }
}

data class ListMidiCoreCandidates(
    val session: MidiCoreProjectSession,
    val role: CandidateRole,
    val occurrenceId: String,
    val expectedRevision: Long? = session.project.revision,
)

data class CompareMidiCoreCandidates(
    val session: MidiCoreProjectSession,
    val role: CandidateRole,
    val occurrenceId: String,
    val firstCandidateId: String,
    val secondCandidateId: String,
    val expectedRevision: Long? = session.project.revision,
)

data class RegenerateMidiCoreCandidate(
    val generation: GenerateMidiCoreCandidate,
    val expectedRevision: Long? = generation.session.project.revision,
)

data class MidiCoreCandidateReviewItem(
    val candidate: MidiCoreCandidate,
    val validation: MidiCoreRoleValidationReport,
    val notes: List<MidiCoreReviewNote>,
    val authorityCurrent: Boolean,
    val accepted: Boolean,
    val locked: Boolean,
)

data class MidiCoreReviewNote(
    val startTick: Long,
    val endTick: Long,
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
)

private val NOTE_ORDER = compareBy<MidiCoreReviewNote> { it.startTick }
    .thenBy { it.endTick }
    .thenBy { it.channel }
    .thenBy { it.pitch }
    .thenBy { it.velocity }

enum class MidiCoreCandidateDifferenceKind { ADDED, REMOVED, CHANGED }

data class MidiCoreCandidateDifference(
    val kind: MidiCoreCandidateDifferenceKind,
    val first: MidiCoreReviewNote?,
    val second: MidiCoreReviewNote?,
)

data class MidiCoreCandidateDifferenceSummary(
    val additions: Int,
    val removals: Int,
    val changes: Int,
) {
    init {
        require(additions >= 0 && removals >= 0 && changes >= 0) { "Candidate difference counts must not be negative" }
    }

    val total: Int get() = additions + removals + changes
}

/** Compares semantic note events in deterministic start-tick and event-value order. */
object MidiCoreCandidateDiff {
    fun differences(
        first: List<MidiCoreReviewNote>,
        second: List<MidiCoreReviewNote>,
    ): List<MidiCoreCandidateDifference> {
        val firstByTick = first.sortedWith(NOTE_ORDER).groupBy(MidiCoreReviewNote::startTick)
        val secondByTick = second.sortedWith(NOTE_ORDER).groupBy(MidiCoreReviewNote::startTick)
        return (firstByTick.keys + secondByTick.keys).distinct().sorted().flatMap { tick ->
            val left = firstByTick[tick].orEmpty()
            val right = secondByTick[tick].orEmpty()
            val common = minOf(left.size, right.size)
            buildList {
                (0 until common).forEach { index ->
                    if (left[index] != right[index]) {
                        add(MidiCoreCandidateDifference(MidiCoreCandidateDifferenceKind.CHANGED, left[index], right[index]))
                    }
                }
                left.drop(common).forEach { note -> add(MidiCoreCandidateDifference(MidiCoreCandidateDifferenceKind.REMOVED, note, null)) }
                right.drop(common).forEach { note -> add(MidiCoreCandidateDifference(MidiCoreCandidateDifferenceKind.ADDED, null, note)) }
            }
        }
    }

    fun summary(differences: List<MidiCoreCandidateDifference>): MidiCoreCandidateDifferenceSummary =
        MidiCoreCandidateDifferenceSummary(
            additions = differences.count { it.kind == MidiCoreCandidateDifferenceKind.ADDED },
            removals = differences.count { it.kind == MidiCoreCandidateDifferenceKind.REMOVED },
            changes = differences.count { it.kind == MidiCoreCandidateDifferenceKind.CHANGED },
        )
}

sealed interface MidiCoreCandidateReviewResult {
    data class Listed(
        val session: MidiCoreProjectSession,
        val revision: Long,
        val candidates: List<MidiCoreCandidateReviewItem>,
    ) : MidiCoreCandidateReviewResult

    data class Compared(
        val session: MidiCoreProjectSession,
        val revision: Long,
        val first: MidiCoreCandidateReviewItem,
        val second: MidiCoreCandidateReviewItem,
        val differences: List<MidiCoreCandidateDifference>,
    ) : MidiCoreCandidateReviewResult {
        val summary: MidiCoreCandidateDifferenceSummary get() = MidiCoreCandidateDiff.summary(differences)
    }

    data class Rejected(val problem: MidiCoreCandidateProblem) : MidiCoreCandidateReviewResult
}
