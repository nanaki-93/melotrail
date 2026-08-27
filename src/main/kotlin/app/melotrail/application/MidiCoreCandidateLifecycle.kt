package app.melotrail.application

import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateAcceptanceHistory
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreAcceptedCandidateReference
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthoritySettings
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.adapter.MidiCoreArtifactCollisionException
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Applies a pure invalidation preview to durable candidate status without deleting evidence. */
internal fun MidiCoreProject.withInvalidatedCandidates(
    invalidatedCandidateIds: Collection<String>,
): MidiCoreProject {
    val stale = invalidatedCandidateIds.toSet()
    if (stale.isEmpty()) return this
    return copy(candidates = candidates.map { candidate ->
        if (candidate.id in stale && candidate.status in setOf(MidiCoreCandidateStatus.CURRENT, MidiCoreCandidateStatus.ACCEPTED)) {
            candidate.copy(status = MidiCoreCandidateStatus.STALE)
        } else {
            candidate
        }
    })
}

/** Publishes immutable candidates and performs explicit review state transitions. */
class MidiCoreCandidateLifecycle(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "candidate-${UUID.randomUUID()}" },
) {
    fun publish(request: PublishMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { publishLocked(request) }

    private fun publishLocked(request: PublishMidiCoreCandidate): MidiCoreCandidateLifecycleResult {
        val loaded = when (val result = load(request.session)) {
            is CandidateLoad.Ready -> result
            is CandidateLoad.Rejected -> return result.result
        }
        val current = loaded.project
        val candidateId = try {
            request.candidateId ?: idFactory()
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_CANDIDATE, "A stable candidate identifier could not be created.", "Retry candidate generation with a valid identifier factory.")
        }
        if (!SAFE_ID.matches(candidateId)) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_CANDIDATE, "The candidate identifier is not a safe stable identifier.", "Use letters, numbers, hyphens, or underscores for the candidate identifier.")
        }
        if (current.candidates.any { it.id == candidateId }) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION, "A candidate with this identifier already exists.", "Retry with a new candidate identifier; existing evidence was preserved.")
        }
        if (!SAFE_ID.matches(request.occurrenceId) || request.generatorVersion.isBlank() || request.generatorVersion.length > 120 ||
            request.generatorVersion.any(Char::isISOControl) ||
            !TOKEN.matches(request.profileId) || !TOKEN.matches(request.patternId) ||
            request.acceptedDependencyIds != request.acceptedDependencyIds.distinct() ||
            request.acceptedDependencyIds.any { !SAFE_ID.matches(it) }) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_CANDIDATE, "Candidate identity or generator metadata is invalid.", "Correct the role, occurrence, profile, pattern, version, and dependency metadata and retry.")
        }
        if (!Files.isRegularFile(request.midi, LinkOption.NOFOLLOW_LINKS)) {
            return rejected(MidiCoreCandidateProblemCode.ARTIFACT_FAILURE, "The generated candidate MIDI file is unavailable.", "Keep the generated MIDI file available and retry publication.")
        }
        try {
            Json.parseToJsonElement(request.validationReportJson).jsonObject
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_CANDIDATE, "The candidate validation report is not a JSON object.", "Publish the deterministic validation report together with the candidate.")
        }
        val expectedAuthorityHash = try {
            MidiCoreAuthorityHasher.from(current).scopeHash(request.occurrenceId, request.role)
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED, "The candidate scope is not available in current musical authority.", "Complete tempo, meter, key, structure, and harmony for this occurrence before publishing.")
        }
        if (!HASH.matches(request.authorityHash) || request.authorityHash != expectedAuthorityHash) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_STALE, "The candidate was generated against an older or different authority scope.", "Regenerate the selected role and occurrence from the current authority.")
        }
        val dependencies = current.candidates.associateBy(MidiCoreCandidate::id)
        request.acceptedDependencyIds.forEach { dependencyId ->
            val dependency = dependencies[dependencyId]
            val accepted = current.acceptances.any { it.candidateId == dependencyId }
            if (dependency == null || !accepted || dependency.status == MidiCoreCandidateStatus.REJECTED || dependency.status == MidiCoreCandidateStatus.STALE) {
                return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "The candidate references work that is not currently accepted.", "Accept and verify each dependency before publishing this candidate.")
            }
        }
        val createdAt = Instant.now(clock).toString()
        val candidate: MidiCoreCandidate
        try {
            val midi = artifacts.publishCandidateMidi(loaded.root, request.role, request.occurrenceId, candidateId, request.midi)
            val report = artifacts.publishCandidateReport(loaded.root, candidateId, request.validationReportJson)
            candidate = MidiCoreCandidate(
                id = candidateId,
                role = request.role,
                occurrenceId = request.occurrenceId,
                generatorVersion = request.generatorVersion,
                authorityHash = request.authorityHash,
                seed = request.seed,
                midi = midi,
                validationReport = report,
                createdAt = createdAt,
                profileId = request.profileId,
                patternId = request.patternId,
                acceptedDependencyIds = request.acceptedDependencyIds,
            )
            if (request.beforeProjectSave?.invoke(candidate) == false) {
                return rejected(
                    MidiCoreCandidateProblemCode.CANCELLED,
                    "Candidate generation was cancelled after immutable evidence publication.",
                    "The published evidence remains inspectable; retry generation if another candidate is required.",
                )
            }
            val updated = current.copy(
                candidates = current.candidates + candidate,
                revision = Math.addExact(current.revision, 1L),
            )
            save(loaded.root, updated)
        } catch (error: MidiCoreProjectSaveException) {
            return rejected(MidiCoreCandidateProblemCode.SAVE_FAILED, "The candidate evidence was published but project state could not be saved.", "Keep the immutable evidence and retry the project save; the last known-good project remains current.")
        } catch (error: MidiCoreArtifactCollisionException) {
            return rejected(MidiCoreCandidateProblemCode.ARTIFACT_COLLISION, "Candidate evidence collided with immutable files from another publication.", "Retry with a new candidate identifier; existing evidence was preserved.")
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.ARTIFACT_FAILURE, "Candidate MIDI or validation evidence could not be published safely.", "Check the generated files and project permissions, then retry.")
        }
        val updatedSession = MidiCoreProjectSession(
            loaded.root,
            current.copy(candidates = current.candidates + candidate, revision = Math.addExact(current.revision, 1L)),
        )
        return MidiCoreCandidateLifecycleResult.Published(updatedSession, candidate)
    }

    fun accept(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { acceptLocked(request) }

    private fun acceptLocked(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult {
        val loaded = when (val result = load(request.session, request.expectedRevision)) {
            is CandidateLoad.Ready -> result
            is CandidateLoad.Rejected -> return result.result
        }
        val current = loaded.project
        val candidate = current.candidates.singleOrNull { it.id == request.candidateId }
            ?: return rejected(MidiCoreCandidateProblemCode.CANDIDATE_NOT_FOUND, "The candidate does not exist in this project.", "Choose an inspectable candidate and retry.")
        ensureAcceptable(loaded.root, current, candidate)?.let { return it }
        val existing = current.acceptances.singleOrNull { it.occurrenceId == candidate.occurrenceId && it.role == candidate.role }
        if (existing?.candidateId == candidate.id && existing.locked && candidate.status != MidiCoreCandidateStatus.ACCEPTED) {
            return rejected(MidiCoreCandidateProblemCode.LOCKED, "The accepted candidate is locked for this occurrence and role.", "Explicitly unlock the current acceptance before changing it.")
        }
        if (existing?.candidateId == candidate.id && candidate.status == MidiCoreCandidateStatus.ACCEPTED) {
            return MidiCoreCandidateLifecycleResult.Updated(
                MidiCoreProjectSession(loaded.root, current),
                candidate,
                existing,
                null,
            )
        }
        if (existing?.locked == true && existing.candidateId != candidate.id) {
            return rejected(MidiCoreCandidateProblemCode.LOCKED, "The accepted candidate is locked for this occurrence and role.", "Explicitly unlock the current acceptance before choosing another candidate.")
        }
        val action = if (existing == null) MidiCoreAcceptanceAction.ACCEPTED else MidiCoreAcceptanceAction.REPLACED
        val acceptance = CandidateAcceptance(candidate.occurrenceId, candidate.role, candidate.id, request.locked)
        val historyEntry = try {
            history(current, candidate, action)
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION, "A unique acceptance history identifier could not be created.", "Retry the transition with a collision-free identifier factory.")
        }
        val updated = current.copy(
            candidates = current.candidates.map { item ->
                when {
                    item.id == candidate.id -> item.copy(status = MidiCoreCandidateStatus.ACCEPTED, rejectionReason = null)
                    existing?.candidateId == item.id && item.status == MidiCoreCandidateStatus.ACCEPTED -> item.copy(status = MidiCoreCandidateStatus.CURRENT)
                    else -> item
                }
            },
            acceptances = current.acceptances.filterNot { it.occurrenceId == candidate.occurrenceId && it.role == candidate.role } + acceptance,
            acceptanceHistory = current.acceptanceHistory + historyEntry,
        )
        return persistTransition(loaded.root, updated, candidate, acceptance)
    }

    fun reject(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { rejectLocked(request) }

    private fun rejectLocked(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult {
        val loaded = when (val result = load(request.session, request.expectedRevision)) {
            is CandidateLoad.Ready -> result
            is CandidateLoad.Rejected -> return result.result
        }
        val current = loaded.project
        val candidate = current.candidates.singleOrNull { it.id == request.candidateId }
            ?: return rejected(MidiCoreCandidateProblemCode.CANDIDATE_NOT_FOUND, "The candidate does not exist in this project.", "Choose an inspectable candidate and retry.")
        if (candidate.status == MidiCoreCandidateStatus.REJECTED) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "The candidate is already rejected.", "Choose a current candidate if another result is required.")
        }
        val acceptance = current.acceptances.singleOrNull { it.candidateId == candidate.id }
        if (acceptance?.locked == true) {
            return rejected(MidiCoreCandidateProblemCode.LOCKED, "A locked accepted candidate cannot be rejected.", "Explicitly unlock the accepted candidate before changing its review state.")
        }
        if (acceptance != null) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "An accepted candidate must be replaced or restored before it can be rejected.", "Choose another candidate or remove the acceptance through the review workflow.")
        }
        val reason = request.reason.trim()
        if (reason.isBlank() || reason.length > 240 || reason.any(Char::isISOControl)) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_CANDIDATE, "A concise rejection reason is required.", "Enter a non-empty rejection reason of at most 240 printable characters.")
        }
        val rejectedCandidate = candidate.copy(status = MidiCoreCandidateStatus.REJECTED, rejectionReason = reason)
        val historyEntry = try {
            history(current, candidate, MidiCoreAcceptanceAction.REJECTED)
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION, "A unique rejection history identifier could not be created.", "Retry the transition with a collision-free identifier factory.")
        }
        val updated = current.copy(
            candidates = current.candidates.map { if (it.id == candidate.id) rejectedCandidate else it },
            acceptanceHistory = current.acceptanceHistory + historyEntry,
        )
        return persistTransition(loaded.root, updated, rejectedCandidate, null)
    }

    fun lock(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) {
            setLockLocked(request.session, request.candidateId, true, request.expectedRevision)
        }

    fun unlock(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) {
            setLockLocked(request.session, request.candidateId, false, request.expectedRevision)
        }

    fun restore(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { restoreLocked(request) }

    private fun restoreLocked(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult {
        val loaded = when (val result = load(request.session, request.expectedRevision)) {
            is CandidateLoad.Ready -> result
            is CandidateLoad.Rejected -> return result.result
        }
        val current = loaded.project
        val candidate = current.candidates.singleOrNull { it.id == request.candidateId }
            ?: return rejected(MidiCoreCandidateProblemCode.CANDIDATE_NOT_FOUND, "The candidate does not exist in this project.", "Choose an inspectable candidate and retry.")
        if (candidate.role != request.role || candidate.occurrenceId != request.occurrenceId) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "The candidate does not belong to the requested role and occurrence.", "Restore the candidate from its recorded scope.")
        }
        ensureAcceptable(loaded.root, current, candidate)?.let { return it }
        if (current.acceptanceHistory.none {
                it.candidateId == candidate.id && it.role == candidate.role && it.occurrenceId == candidate.occurrenceId &&
                    it.action in setOf(MidiCoreAcceptanceAction.ACCEPTED, MidiCoreAcceptanceAction.REPLACED, MidiCoreAcceptanceAction.RESTORED)
            }) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "This candidate has no prior accepted reference to restore.", "Accept the candidate once before using restore.")
        }
        val existing = current.acceptances.singleOrNull { it.occurrenceId == request.occurrenceId && it.role == request.role }
        if (existing?.candidateId == candidate.id && candidate.status == MidiCoreCandidateStatus.ACCEPTED) {
            return MidiCoreCandidateLifecycleResult.Updated(MidiCoreProjectSession(loaded.root, current), candidate, existing, null)
        }
        if (existing?.locked == true && existing.candidateId != candidate.id) {
            return rejected(MidiCoreCandidateProblemCode.LOCKED, "The current accepted candidate is locked for this occurrence and role.", "Explicitly unlock the current acceptance before restoring another candidate.")
        }
        val acceptance = CandidateAcceptance(request.occurrenceId, request.role, candidate.id, request.locked)
        val historyEntry = try {
            history(current, candidate, MidiCoreAcceptanceAction.RESTORED)
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION, "A unique restore history identifier could not be created.", "Retry the transition with a collision-free identifier factory.")
        }
        val updated = current.copy(
            candidates = current.candidates.map { item ->
                when {
                    item.id == candidate.id -> item.copy(status = MidiCoreCandidateStatus.ACCEPTED, rejectionReason = null)
                    existing?.candidateId == item.id && item.status == MidiCoreCandidateStatus.ACCEPTED -> item.copy(status = MidiCoreCandidateStatus.CURRENT)
                    else -> item
                }
            },
            acceptances = current.acceptances.filterNot { it.occurrenceId == request.occurrenceId && it.role == request.role } + acceptance,
            acceptanceHistory = current.acceptanceHistory + historyEntry,
        )
        return persistTransition(loaded.root, updated, candidate, acceptance)
    }

    private fun setLockLocked(
        session: MidiCoreProjectSession,
        candidateId: String,
        locked: Boolean,
        expectedRevision: Long?,
    ): MidiCoreCandidateLifecycleResult {
        val loaded = when (val result = load(session, expectedRevision)) {
            is CandidateLoad.Ready -> result
            is CandidateLoad.Rejected -> return result.result
        }
        val current = loaded.project
        val candidate = current.candidates.singleOrNull { it.id == candidateId }
            ?: return rejected(MidiCoreCandidateProblemCode.CANDIDATE_NOT_FOUND, "The candidate does not exist in this project.", "Choose an inspectable candidate and retry.")
        ensureAcceptable(loaded.root, current, candidate)?.let { return it }
        if (candidate.status != MidiCoreCandidateStatus.ACCEPTED) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "Only the current accepted candidate can be locked or unlocked.", "Accept this candidate first, then change its lock state.")
        }
        val acceptance = current.acceptances.singleOrNull { it.candidateId == candidate.id }
            ?: return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "The candidate is not the current accepted reference.", "Accept the candidate before changing its lock state.")
        if (acceptance.locked == locked) {
            return MidiCoreCandidateLifecycleResult.Updated(MidiCoreProjectSession(loaded.root, current), candidate, acceptance, null)
        }
        val updatedAcceptance = acceptance.copy(locked = locked)
        val historyEntry = try {
            history(
                current,
                candidate,
                if (locked) MidiCoreAcceptanceAction.LOCKED else MidiCoreAcceptanceAction.UNLOCKED,
            )
        } catch (error: Exception) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION, "A unique lock history identifier could not be created.", "Retry the transition with a collision-free identifier factory.")
        }
        val updated = current.copy(
            acceptances = current.acceptances.map { if (it == acceptance) updatedAcceptance else it },
            acceptanceHistory = current.acceptanceHistory + historyEntry,
        )
        return persistTransition(loaded.root, updated, candidate, updatedAcceptance)
    }

    private fun ensureAcceptable(
        root: Path,
        project: MidiCoreProject,
        candidate: MidiCoreCandidate,
    ): MidiCoreCandidateLifecycleResult.Rejected? {
        if (candidate.status == MidiCoreCandidateStatus.REJECTED) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "A rejected candidate cannot become accepted.", "Publish a new candidate or choose a non-rejected alternative.")
        }
        if (candidate.status == MidiCoreCandidateStatus.STALE) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_STALE, "The candidate is stale for the current authority.", "Regenerate the affected role and occurrence before accepting it.")
        }
        val currentAuthorityHash = try {
            MidiCoreAuthorityHasher.from(project).scopeHash(candidate.occurrenceId, candidate.role)
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED, "The candidate scope is not available in current musical authority.", "Restore the current authority scope before reviewing this candidate.")
        }
        if (candidate.authorityHash != currentAuthorityHash) {
            return rejected(MidiCoreCandidateProblemCode.CANDIDATE_STALE, "The candidate authority hash no longer matches current authority.", "Regenerate the affected role and occurrence before accepting it.")
        }
        try {
            artifacts.verify(root, candidate.midi)
            artifacts.verify(root, candidate.validationReport)
        } catch (error: Exception) {
            return rejected(
                MidiCoreCandidateProblemCode.DIGEST_MISMATCH,
                "Candidate MIDI or validation evidence no longer matches its recorded digest.",
                "Restore the immutable candidate files or publish a new candidate; the current candidate cannot be accepted.",
            )
        }
        return null
    }

    private fun history(
        project: MidiCoreProject,
        candidate: MidiCoreCandidate,
        action: MidiCoreAcceptanceAction,
    ): CandidateAcceptanceHistory {
        val id = idFactory()
        require(SAFE_ID.matches(id) && project.acceptanceHistory.none { it.id == id }) { "Acceptance history identifier collides with existing history" }
        return CandidateAcceptanceHistory(id, candidate.occurrenceId, candidate.role, candidate.id, action, Instant.now(clock).toString())
    }

    private fun persistTransition(
        root: Path,
        project: MidiCoreProject,
        candidate: MidiCoreCandidate,
        acceptance: CandidateAcceptance?,
    ): MidiCoreCandidateLifecycleResult {
        val next = try {
            project.copy(revision = Math.addExact(project.revision, 1L))
        } catch (error: ArithmeticException) {
            return rejected(MidiCoreCandidateProblemCode.INVALID_STATE, "The project revision cannot advance safely.", "Save a new project copy before applying another review decision.")
        }
        return try {
            save(root, next)
            val persistedCandidate = next.candidates.singleOrNull { it.id == candidate.id } ?: candidate
            val persistedAcceptance = acceptance?.let { expected ->
                next.acceptances.singleOrNull { it.occurrenceId == expected.occurrenceId && it.role == expected.role }
            }
            MidiCoreCandidateLifecycleResult.Updated(
                MidiCoreProjectSession(root, next),
                persistedCandidate,
                persistedAcceptance,
                next.acceptanceHistory.lastOrNull(),
            )
        } catch (error: MidiCoreProjectSaveException) {
            rejected(MidiCoreCandidateProblemCode.SAVE_FAILED, "Candidate review state could not be saved safely.", "Retry the transition; the last known-good project remains current.")
        } catch (error: Exception) {
            rejected(MidiCoreCandidateProblemCode.ARTIFACT_FAILURE, "Candidate review state could not be persisted.", "Check project artifacts and permissions, then retry.")
        }
    }

    private fun save(root: Path, project: MidiCoreProject) {
        artifacts.saveProject(root, project)
    }

    private fun load(session: MidiCoreProjectSession, expectedRevision: Long? = null): CandidateLoad {
        val root = session.root.toAbsolutePath().normalize()
        if (expectedRevision != null && expectedRevision < 0L) {
            return CandidateLoad.Rejected(
                rejected(
                    MidiCoreCandidateProblemCode.INVALID_CANDIDATE,
                    "The expected project revision is invalid.",
                    "Reload the project and retry the review decision.",
                ),
            )
        }
        val current = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            val code = if (error.message.orEmpty().contains("digest", ignoreCase = true)) {
                MidiCoreCandidateProblemCode.DIGEST_MISMATCH
            } else {
                MidiCoreCandidateProblemCode.INVALID_PROJECT
            }
            return CandidateLoad.Rejected(rejected(code, "The project cannot be verified before changing candidate state.", "Open a valid MIDI Core project and retry."))
        }
        if (expectedRevision != null && current.revision != expectedRevision) {
            return CandidateLoad.Rejected(
                rejected(
                    MidiCoreCandidateProblemCode.REVISION_CONFLICT,
                    "The project changed from revision $expectedRevision to ${current.revision}.",
                    "Reload the Review page before applying another decision.",
                ),
            )
        }
        if (current != session.project) {
            return CandidateLoad.Rejected(rejected(MidiCoreCandidateProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before changing candidate state."))
        }
        return CandidateLoad.Ready(root, current)
    }

    private fun rejected(
        code: MidiCoreCandidateProblemCode,
        message: String,
        nextAction: String,
    ): MidiCoreCandidateLifecycleResult.Rejected =
        MidiCoreCandidateLifecycleResult.Rejected(MidiCoreCandidateProblem(code, message, nextAction))

    private sealed interface CandidateLoad {
        data class Ready(val root: Path, val project: MidiCoreProject) : CandidateLoad
        data class Rejected(val result: MidiCoreCandidateLifecycleResult.Rejected) : CandidateLoad
    }
}

/** Captures already-published MIDI files under an immutable export snapshot ID. */
class MidiCoreExportSnapshotLifecycle(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "export-${UUID.randomUUID()}" },
) {
    fun capture(request: CaptureMidiCoreExportSnapshot): MidiCoreExportSnapshotLifecycleResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_PROJECT, "The project cannot be verified before capturing an export snapshot.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) {
            return rejected(MidiCoreExportSnapshotProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before capturing export evidence.")
        }
        val source = current.sourceMidi ?: return rejected(MidiCoreExportSnapshotProblemCode.AUTHORITY_REQUIRED, "An imported source MIDI is required for an export snapshot.", "Import and select the source MIDI before exporting.")
        val authority = try {
            MidiCoreAuthorityHasher.from(current, MidiCoreAuthoritySettings(request.roleSettings))
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT, error.message ?: "Export role settings are invalid.", "Use bounded stable role settings and retry.")
        }
        val snapshotId = try {
            request.snapshotId ?: idFactory()
        } catch (error: Exception) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT, "A stable export snapshot identifier could not be created.", "Retry with a valid snapshot identifier factory.")
        }
        if (!SAFE_ID.matches(snapshotId)) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT, "The export snapshot identifier is not safe.", "Use letters, numbers, hyphens, or underscores for the snapshot identifier.")
        }
        val createdAt = try {
            request.createdAt ?: Instant.now(clock).toString()
        } catch (error: Exception) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT, "A stable export snapshot timestamp could not be created.", "Retry with a valid export timestamp.")
        }
        if (current.exportSnapshots.any { it.id == snapshotId }) {
            return rejected(MidiCoreExportSnapshotProblemCode.SNAPSHOT_ID_COLLISION, "An export snapshot with this identifier already exists.", "Retry with a new snapshot identifier; existing export evidence was preserved.")
        }
        val references = mutableListOf<MidiCoreAcceptedCandidateReference>()
        val candidates = current.candidates.associateBy(MidiCoreCandidate::id)
        val enabledRoles = request.enabledRoles.sortedBy(CandidateRole::ordinal)
        current.acceptances
            .filter { it.role in request.enabledRoles }
            .sortedWith(compareBy<CandidateAcceptance> { it.occurrenceId }.thenBy { it.role.ordinal }).forEach { acceptance ->
            val candidate = candidates[acceptance.candidateId]
                ?: return rejected(MidiCoreExportSnapshotProblemCode.EXPORT_NOT_READY, "The accepted candidate reference is missing.", "Repair the project acceptance state before exporting.")
            if (candidate.status != MidiCoreCandidateStatus.ACCEPTED) {
                return rejected(MidiCoreExportSnapshotProblemCode.STALE_SNAPSHOT, "An accepted role candidate is stale or not in an accepted state.", "Regenerate and accept the affected role before exporting.")
            }
            val expected = try {
                authority.scopeHash(candidate.occurrenceId, candidate.role)
            } catch (error: IllegalArgumentException) {
                return rejected(MidiCoreExportSnapshotProblemCode.STALE_SNAPSHOT, "An accepted candidate has no current authority scope.", "Regenerate the affected role and occurrence before exporting.")
            }
            if (candidate.authorityHash != expected) {
                return rejected(MidiCoreExportSnapshotProblemCode.STALE_SNAPSHOT, "An accepted candidate no longer matches current authority.", "Regenerate and accept the affected role before exporting.")
            }
            references += MidiCoreAcceptedCandidateReference(
                candidate.occurrenceId,
                candidate.role,
                candidate.id,
                candidate.midi.sha256,
                candidate.validationReport.sha256,
                candidate.authorityHash,
                candidate.generatorVersion,
                candidate.profileId,
                candidate.patternId,
                candidate.seed,
            )
        }
        val generatorVersions = references.associate { "${it.occurrenceId}.${it.role.name.lowercase()}" to it.generatorVersion }
        val snapshot: MidiCoreExportSnapshot
        try {
            snapshot = MidiCoreExportSnapshot(
                id = snapshotId,
                sourceSha256 = source.sha256,
                authorityHash = authority.sha256,
                files = request.files,
                createdAt = createdAt,
                acceptedCandidates = references,
                roleSettings = request.roleSettings.toSortedMap(),
                generatorVersions = generatorVersions.toSortedMap(),
                enabledRoles = enabledRoles,
            )
            request.files.forEach { file ->
                require(file.artifact.path == MidiCoreArtifactStore.exportFilePath(snapshotId, file.kind)) {
                    "Export file path is not canonical for snapshot $snapshotId"
                }
                artifacts.verify(root, file.artifact)
            }
            save(root, current.copy(
                exportSnapshots = current.exportSnapshots + snapshot,
                revision = Math.addExact(current.revision, 1L),
            ))
        } catch (error: MidiCoreProjectSaveException) {
            return rejected(MidiCoreExportSnapshotProblemCode.SAVE_FAILED, "The export snapshot could not be saved safely.", "Retry the save; existing export evidence remains immutable.")
        } catch (error: MidiCoreArtifactCollisionException) {
            return rejected(MidiCoreExportSnapshotProblemCode.ARTIFACT_COLLISION, "Export evidence collided with an immutable file.", "Retry with a new snapshot identifier; existing evidence was preserved.")
        } catch (error: Exception) {
            return rejected(MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT, error.message ?: "Export snapshot files are invalid or unavailable.", "Verify the staged export files and retry.")
        }
        return MidiCoreExportSnapshotLifecycleResult.Captured(
            MidiCoreProjectSession(
                root,
                current.copy(
                    exportSnapshots = current.exportSnapshots + snapshot,
                    revision = Math.addExact(current.revision, 1L),
                ),
            ),
            snapshot,
        )
    }

    fun isCurrent(project: MidiCoreProject, snapshot: MidiCoreExportSnapshot): Boolean = snapshot.isCurrent(project)

    private fun save(root: Path, project: MidiCoreProject) {
        artifacts.saveProject(root, project)
    }

    private fun rejected(
        code: MidiCoreExportSnapshotProblemCode,
        message: String,
        nextAction: String,
    ): MidiCoreExportSnapshotLifecycleResult.Rejected =
        MidiCoreExportSnapshotLifecycleResult.Rejected(MidiCoreExportSnapshotProblem(code, message, nextAction))
}

data class PublishMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val role: CandidateRole,
    val occurrenceId: String,
    val generatorVersion: String,
    val authorityHash: String,
    val seed: Long,
    val midi: Path,
    val validationReportJson: String,
    val candidateId: String? = null,
    val profileId: String = "default",
    val patternId: String = "unspecified",
    val acceptedDependencyIds: List<String> = emptyList(),
    /** Optional checkpoint invoked after immutable files exist and before project-state append. */
    val beforeProjectSave: ((MidiCoreCandidate) -> Boolean)? = null,
)

data class AcceptMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val candidateId: String,
    val locked: Boolean = false,
    val expectedRevision: Long? = null,
)
data class RejectMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val candidateId: String,
    val reason: String,
    val expectedRevision: Long? = null,
)
data class LockMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val candidateId: String,
    val expectedRevision: Long? = null,
)
data class UnlockMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val candidateId: String,
    val expectedRevision: Long? = null,
)
data class RestoreMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val locked: Boolean = false,
    val expectedRevision: Long? = null,
)

data class CaptureMidiCoreExportSnapshot(
    val session: MidiCoreProjectSession,
    val files: List<ExportedSnapshotFile>,
    val roleSettings: Map<String, String> = emptyMap(),
    val snapshotId: String? = null,
    val enabledRoles: Set<CandidateRole> = CandidateRole.entries.toSet(),
    val createdAt: String? = null,
) {
    init {
        require(enabledRoles.all { it in CandidateRole.entries }) { "Export snapshot roles must be target MIDI Core roles" }
        require(createdAt == null || createdAt.matches(ISO_INSTANT)) { "Export snapshot timestamp must be an ISO-8601 UTC instant" }
    }
}

sealed interface MidiCoreCandidateLifecycleResult {
    data class Published(val session: MidiCoreProjectSession, val candidate: MidiCoreCandidate) : MidiCoreCandidateLifecycleResult
    data class Updated(
        val session: MidiCoreProjectSession,
        val candidate: MidiCoreCandidate,
        val acceptance: CandidateAcceptance?,
        val history: CandidateAcceptanceHistory?,
    ) : MidiCoreCandidateLifecycleResult
    data class Rejected(val problem: MidiCoreCandidateProblem) : MidiCoreCandidateLifecycleResult
}

data class MidiCoreCandidateProblem(val code: MidiCoreCandidateProblemCode, val message: String, val nextAction: String)

enum class MidiCoreCandidateProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    INVALID_CANDIDATE,
    CANDIDATE_NOT_FOUND,
    CANDIDATE_ID_COLLISION,
    CANDIDATE_STALE,
    LOCKED,
    INVALID_STATE,
    ARTIFACT_FAILURE,
    ARTIFACT_COLLISION,
    SAVE_FAILED,
    CANCELLED,
    REVISION_CONFLICT,
    DIGEST_MISMATCH,
    CANDIDATE_SCOPE_MISMATCH,
}

sealed interface MidiCoreExportSnapshotLifecycleResult {
    data class Captured(val session: MidiCoreProjectSession, val snapshot: MidiCoreExportSnapshot) : MidiCoreExportSnapshotLifecycleResult
    data class Rejected(val problem: MidiCoreExportSnapshotProblem) : MidiCoreExportSnapshotLifecycleResult
}

data class MidiCoreExportSnapshotProblem(val code: MidiCoreExportSnapshotProblemCode, val message: String, val nextAction: String)

enum class MidiCoreExportSnapshotProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    INVALID_SNAPSHOT,
    EXPORT_NOT_READY,
    STALE_SNAPSHOT,
    SNAPSHOT_ID_COLLISION,
    ARTIFACT_COLLISION,
    SAVE_FAILED,
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val TOKEN = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,119}")
private val HASH = Regex("[0-9a-f]{64}")
private val ISO_INSTANT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z")
