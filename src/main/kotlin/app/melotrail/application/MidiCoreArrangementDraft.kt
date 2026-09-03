package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateAcceptanceHistory
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreArrangementDraftAcceptanceHistory
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreArrangementDraftValidationSummary
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** A stable draft scope in authoritative occurrence order and Chords → Bass → Drums order. */
data class MidiCoreArrangementDraftScope(val occurrenceId: String, val role: CandidateRole)

/** Incremental, monotonic evidence for a resumable full-draft generation request. */
data class MidiCoreArrangementDraftProgress(
    val draftId: String,
    val totalScopes: Int,
    val completedScopes: List<MidiCoreArrangementDraftScope>,
    val activeScope: MidiCoreArrangementDraftScope? = null,
) {
    init {
        require(totalScopes > 0 && completedScopes.distinct().size == completedScopes.size && completedScopes.size <= totalScopes) {
            "Arrangement draft progress is invalid"
        }
    }

    val completedCount: Int get() = completedScopes.size
}

/** One full style realization. The [draftId] can be retained to retry only incomplete scopes. */
data class GenerateMidiCoreArrangementDraft(
    val session: MidiCoreProjectSession,
    val styleId: String,
    val rootSeed: Long,
    val draftId: String? = null,
    val cancellation: MidiCoreGenerationCancellation = MidiCoreGenerationCancellation.NONE,
    val onProgress: (MidiCoreArrangementDraftProgress) -> Unit = {},
) {
    init {
        require(styleId.isNotBlank()) { "Arrangement draft style is required" }
        require(draftId == null || SAFE_ID.matches(draftId)) { "Arrangement draft identifier is invalid" }
    }
}

sealed interface MidiCoreArrangementDraftGenerationResult {
    data class Completed(
        val session: MidiCoreProjectSession,
        val draft: MidiCoreArrangementDraft,
        val progress: MidiCoreArrangementDraftProgress,
    ) : MidiCoreArrangementDraftGenerationResult

    /** Existing valid candidates are retained; callers reuse [draftId] to retry only the failed scope. */
    data class Incomplete(
        val session: MidiCoreProjectSession,
        val draftId: String,
        val progress: MidiCoreArrangementDraftProgress,
        val problem: MidiCoreArrangementDraftProblem,
    ) : MidiCoreArrangementDraftGenerationResult

    data class Cancelled(
        val session: MidiCoreProjectSession,
        val draftId: String,
        val progress: MidiCoreArrangementDraftProgress,
    ) : MidiCoreArrangementDraftGenerationResult
}

data class MidiCoreArrangementDraftProblem(
    val code: MidiCoreArrangementDraftProblemCode,
    val message: String,
    val nextAction: String,
    val scope: MidiCoreArrangementDraftScope? = null,
)

enum class MidiCoreArrangementDraftProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    STYLE_NOT_FOUND,
    CANDIDATE_FAILURE,
    DRAFT_NOT_FOUND,
    DRAFT_STALE,
    DRAFT_INVALID,
    LOCKED,
    SAVE_FAILED,
    REVISION_CONFLICT,
    DIGEST_MISMATCH,
}

/**
 * Generates one complete style draft in deterministic dependency order. Each
 * candidate is published by the existing immutable scoped-candidate boundary;
 * only a complete, revalidated reference set becomes a persisted draft.
 */
class MidiCoreArrangementDraftGeneration(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val candidates: MidiCoreCandidateGeneration = MidiCoreCandidateGeneration(artifacts = artifacts),
    private val drafts: MidiCoreArrangementDraftLifecycle = MidiCoreArrangementDraftLifecycle(artifacts = artifacts),
    private val draftIdFactory: () -> String = { "draft-${UUID.randomUUID()}" },
) {
    suspend fun generate(request: GenerateMidiCoreArrangementDraft): MidiCoreArrangementDraftGenerationResult {
        val style = try {
            MidiCoreArrangementStyleCatalog.require(request.styleId)
        } catch (_: IllegalArgumentException) {
            return incomplete(request.session, request.draftId ?: safeDraftId(), emptyList(), problem(
                MidiCoreArrangementDraftProblemCode.STYLE_NOT_FOUND,
                "The selected arrangement style is no longer available.",
                "Choose one of the current named styles and try again.",
            ))
        }
        val draftId = request.draftId ?: try {
            draftIdFactory().also { require(SAFE_ID.matches(it)) }
        } catch (_: Exception) {
            return incomplete(request.session, safeDraftId(), emptyList(), problem(
                MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
                "A stable draft identifier could not be created.",
                "Retry creating the full draft with a valid project identifier factory.",
            ))
        }
        var session = request.session
        val initial = loadCurrent(session) ?: return incomplete(session, draftId, emptyList(), problem(
            MidiCoreArrangementDraftProblemCode.STALE_PROJECT,
            "The project changed before complete-draft generation began.",
            "Reload the project and create the draft again.",
        ))
        session = initial
        val authority = session.project.authority ?: return incomplete(session, draftId, emptyList(), problem(
            MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED,
            "A protected source melody and confirmed authority are required before creating a full draft.",
            "Import MIDI and complete Structure & Harmony first.",
        ))
        if (authority.occurrences.isEmpty()) return incomplete(session, draftId, emptyList(), problem(
            MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED,
            "At least one saved section occurrence is required before creating a full draft.",
            "Define a contiguous occurrence timeline, then try again.",
        ))
        val orderedScopes = CandidateRole.entries.flatMap { role ->
            authority.occurrences.map { occurrence -> MidiCoreArrangementDraftScope(occurrence.id, role) }
        }
        val referenceScopes = authority.occurrences.flatMap { occurrence ->
            CandidateRole.entries.map { role -> MidiCoreArrangementDraftScope(occurrence.id, role) }
        }
        session.project.arrangementDrafts.singleOrNull { it.id == draftId }?.let { existing ->
            if (existing.styleId == style.id && existing.styleVersion == MidiCoreArrangementStyleCatalog.VERSION &&
                existing.rootSeed == request.rootSeed && validateDraft(session.root, session.project, existing, artifacts) == null
            ) {
                return MidiCoreArrangementDraftGenerationResult.Completed(
                    session,
                    existing,
                    MidiCoreArrangementDraftProgress(draftId, orderedScopes.size, orderedScopes),
                )
            }
            return incomplete(session, draftId, orderedScopes.size, emptyList(), problem(
                MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
                "This draft identifier already belongs to different or stale complete-draft evidence.",
                "Create a new draft identifier after reviewing the current authority and style.",
            ))
        }
        val completed = mutableListOf<MidiCoreArrangementDraftScope>()
        val selected = linkedMapOf<MidiCoreArrangementDraftScope, MidiCoreCandidate>()
        orderedScopes.forEach { scope ->
            if (request.cancellation.isCancelled()) return cancelled(session, draftId, orderedScopes.size, completed)
            val active = MidiCoreArrangementDraftProgress(draftId, orderedScopes.size, completed.toList(), scope)
            request.onProgress(active)
            val choice = style.role(scope.role)
            val dependencies = CandidateRole.entries.take(scope.role.ordinal).map { dependencyRole ->
                selected[MidiCoreArrangementDraftScope(scope.occurrenceId, dependencyRole)]?.id
                    ?: return incomplete(session, draftId, orderedScopes.size, completed, problem(
                        MidiCoreArrangementDraftProblemCode.CANDIDATE_FAILURE,
                        "The ${dependencyRole.name.lowercase()} draft dependency is unavailable for '${scope.occurrenceId}'.",
                        "Retry the incomplete draft; completed valid scopes will be retained.",
                        scope,
                    ))
            }
            val currentAuthority = try {
                MidiCoreAuthorityHasher.from(session.project)
            } catch (_: IllegalArgumentException) {
                return incomplete(session, draftId, orderedScopes.size, completed, problem(
                    MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED,
                    "Musical authority changed while the draft was being created.",
                    "Reload the project and retry the affected draft scopes.",
                    scope,
                ))
            }
            val scopeHash = try {
                currentAuthority.scopeHash(scope.occurrenceId, scope.role)
            } catch (_: IllegalArgumentException) {
                return incomplete(session, draftId, orderedScopes.size, completed, problem(
                    MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED,
                    "The draft scope is no longer part of current authority.",
                    "Restore the occurrence and harmony, then retry the draft.",
                    scope,
                ))
            }
            val seed = derivedSeed(request.rootSeed, scope)
            var attempt = 0
            var candidateId = candidateId(draftId, scopeHash, scope, attempt)
            var existing = session.project.candidates.singleOrNull { it.id == candidateId }
            while (existing != null && !validExisting(existing, scope, scopeHash, seed, choice.performanceProfileId, choice.patternId, dependencies, session.root)) {
                attempt += 1
                if (attempt > MAXIMUM_SCOPE_ATTEMPTS) return incomplete(session, draftId, orderedScopes.size, completed, problem(
                    MidiCoreArrangementDraftProblemCode.CANDIDATE_FAILURE,
                    "No collision-free immutable candidate identifier remains for this stale draft scope.",
                    "Create a new draft identifier; existing immutable evidence was preserved.",
                    scope,
                ))
                candidateId = candidateId(draftId, scopeHash, scope, attempt)
                existing = session.project.candidates.singleOrNull { it.id == candidateId }
            }
            val candidate = if (existing != null && validExisting(existing, scope, scopeHash, seed, choice.performanceProfileId, choice.patternId, dependencies, session.root)) {
                existing
            } else {
                when (val generated = candidates.generate(
                    GenerateMidiCoreCandidate(
                        session = session,
                        role = scope.role,
                        occurrenceId = scope.occurrenceId,
                        performanceProfileId = choice.performanceProfileId,
                        patternId = choice.patternId,
                        generator = MidiCoreGeneratorInput(
                            generatorId = "midi-core-style",
                            generatorVersion = "midi-core-style-v${MidiCoreArrangementStyleCatalog.VERSION}",
                            patternId = choice.patternId,
                            seed = seed,
                        ),
                        sectionPolicy = choice.sectionPolicy,
                        candidateId = candidateId,
                        draftDependencyIds = dependencies,
                        cancellation = request.cancellation,
                    ),
                )) {
                    is MidiCoreCandidateGenerationResult.Published -> {
                        session = generated.session
                        generated.candidate
                    }
                    is MidiCoreCandidateGenerationResult.Cancelled -> return cancelled(session, draftId, orderedScopes.size, completed)
                    is MidiCoreCandidateGenerationResult.ValidationRejected -> return incomplete(session, draftId, orderedScopes.size, completed, problem(
                        MidiCoreArrangementDraftProblemCode.CANDIDATE_FAILURE,
                        "The ${scope.role.name.lowercase()} draft scope failed role validation.",
                        "Adjust authority or choose another style, then retry this incomplete draft.",
                        scope,
                    ))
                    is MidiCoreCandidateGenerationResult.Rejected -> return incomplete(session, draftId, orderedScopes.size, completed, problem(
                        generated.problem.code.toDraftProblemCode(),
                        generated.problem.message,
                        generated.problem.nextAction,
                        scope,
                    ))
                }
            }
            selected[scope] = candidate
            completed += scope
            request.onProgress(MidiCoreArrangementDraftProgress(draftId, orderedScopes.size, completed.toList()))
        }
        val references = referenceScopes.map { scope -> reference(requireNotNull(selected[scope])) }
        val summary = validationSummary(session.root, references, session.project)
            ?: return incomplete(session, draftId, orderedScopes.size, completed, problem(
                MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
                "One generated draft candidate no longer has readable passing validation evidence.",
                "Retry only the affected stale or failed draft scope.",
            ))
        val draft = MidiCoreArrangementDraft(
            id = draftId,
            styleId = style.id,
            styleVersion = MidiCoreArrangementStyleCatalog.VERSION,
            authorityHash = MidiCoreAuthorityHasher.from(session.project).sha256,
            rootSeed = request.rootSeed,
            candidateReferences = references,
            validation = summary,
            createdAt = Instant.now().toString(),
        )
        return when (val published = drafts.publish(PublishMidiCoreArrangementDraft(session, draft))) {
            is MidiCoreArrangementDraftLifecycleResult.Published -> MidiCoreArrangementDraftGenerationResult.Completed(
                published.session,
                published.draft,
                MidiCoreArrangementDraftProgress(draftId, orderedScopes.size, completed),
            )
            is MidiCoreArrangementDraftLifecycleResult.Reused -> MidiCoreArrangementDraftGenerationResult.Completed(
                published.session,
                published.draft,
                MidiCoreArrangementDraftProgress(draftId, orderedScopes.size, completed),
            )
            is MidiCoreArrangementDraftLifecycleResult.Rejected -> incomplete(session, draftId, orderedScopes.size, completed, published.problem)
        }
    }

    private fun loadCurrent(session: MidiCoreProjectSession): MidiCoreProjectSession? = try {
        val project = artifacts.openProject(session.root)
        if (project == session.project) MidiCoreProjectSession(session.root.toAbsolutePath().normalize(), project) else null
    } catch (_: Exception) {
        null
    }

    private fun validExisting(
        candidate: MidiCoreCandidate,
        scope: MidiCoreArrangementDraftScope,
        authorityHash: String,
        seed: Long,
        profileId: String,
        patternId: String,
        dependencies: List<String>,
        root: Path,
    ): Boolean = candidate.role == scope.role && candidate.occurrenceId == scope.occurrenceId &&
        candidate.authorityHash == authorityHash && candidate.seed == seed && candidate.profileId == profileId &&
        candidate.patternId == patternId && candidate.draftDependencyIds == dependencies &&
        candidate.status !in setOf(MidiCoreCandidateStatus.REJECTED, MidiCoreCandidateStatus.STALE) &&
        runCatching {
            artifacts.verify(root, candidate.midi)
            val report = MidiCoreRoleValidationReportJson.decode(Files.readString(artifacts.verify(root, candidate.validationReport)))
            report.passed && report.role == scope.role && report.occurrenceId == scope.occurrenceId
        }.getOrDefault(false)

    private fun incomplete(
        session: MidiCoreProjectSession,
        draftId: String,
        total: Int,
        completed: List<MidiCoreArrangementDraftScope>,
        problem: MidiCoreArrangementDraftProblem,
    ) = MidiCoreArrangementDraftGenerationResult.Incomplete(
        session,
        draftId,
        MidiCoreArrangementDraftProgress(draftId, total, completed),
        problem,
    )

    private fun incomplete(
        session: MidiCoreProjectSession,
        draftId: String,
        completed: List<MidiCoreArrangementDraftScope>,
        problem: MidiCoreArrangementDraftProblem,
    ) = incomplete(session, draftId, 1, completed, problem)

    private fun cancelled(
        session: MidiCoreProjectSession,
        draftId: String,
        total: Int,
        completed: List<MidiCoreArrangementDraftScope>,
    ) = MidiCoreArrangementDraftGenerationResult.Cancelled(session, draftId, MidiCoreArrangementDraftProgress(draftId, total, completed))

    private fun safeDraftId(): String = "draft-${UUID.randomUUID()}"

    private companion object {
        const val MAXIMUM_SCOPE_ATTEMPTS = 16
    }
}

/** Persist a complete draft only after every selected immutable candidate revalidates. */
class MidiCoreArrangementDraftLifecycle(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
) {
    fun publish(request: PublishMidiCoreArrangementDraft): MidiCoreArrangementDraftLifecycleResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { publishLocked(request) }

    private fun publishLocked(request: PublishMidiCoreArrangementDraft): MidiCoreArrangementDraftLifecycleResult {
        val loaded = load(request.session, request.expectedRevision) ?: return rejected(
            MidiCoreArrangementDraftProblemCode.STALE_PROJECT,
            "The project changed before the complete draft could be saved.",
            "Reload the project and retry the complete draft.",
        )
        val existing = loaded.project.arrangementDrafts.singleOrNull { it.id == request.draft.id }
        if (existing != null) return if (existing == request.draft) {
            MidiCoreArrangementDraftLifecycleResult.Reused(loaded, existing)
        } else {
            rejected(
                MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
                "A different complete draft already uses this stable draft identifier.",
                "Retry with a new draft identifier; existing draft evidence was preserved.",
            )
        }
        validateDraft(loaded.root, loaded.project, request.draft)?.let { return MidiCoreArrangementDraftLifecycleResult.Rejected(it) }
        val next = try {
            loaded.project.copy(
                arrangementDrafts = loaded.project.arrangementDrafts + request.draft,
                revision = Math.addExact(loaded.project.revision, 1L),
            )
        } catch (_: ArithmeticException) {
            return rejected(MidiCoreArrangementDraftProblemCode.SAVE_FAILED, "The project revision cannot advance safely.", "Save a new project copy before creating another complete draft.")
        }
        return try {
            artifacts.saveProject(loaded.root, next)
            MidiCoreArrangementDraftLifecycleResult.Published(MidiCoreProjectSession(loaded.root, next), request.draft)
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreArrangementDraftProblemCode.SAVE_FAILED, "The complete draft could not be saved safely.", "Retry the save; immutable candidate evidence remains available.")
        } catch (_: Exception) {
            rejected(MidiCoreArrangementDraftProblemCode.SAVE_FAILED, "The complete draft could not be persisted.", "Check project permissions and retry without regenerating valid scopes.")
        }
    }

    private fun load(session: MidiCoreProjectSession, expectedRevision: Long?): MidiCoreProjectSession? = try {
        val root = session.root.toAbsolutePath().normalize()
        val project = artifacts.openProject(root)
        if (project == session.project && (expectedRevision == null || project.revision == expectedRevision)) MidiCoreProjectSession(root, project) else null
    } catch (_: Exception) {
        null
    }

    private fun rejected(code: MidiCoreArrangementDraftProblemCode, message: String, nextAction: String) =
        MidiCoreArrangementDraftLifecycleResult.Rejected(MidiCoreArrangementDraftProblem(code, message, nextAction))
}

data class PublishMidiCoreArrangementDraft(
    val session: MidiCoreProjectSession,
    val draft: MidiCoreArrangementDraft,
    val expectedRevision: Long? = session.project.revision,
)

sealed interface MidiCoreArrangementDraftLifecycleResult {
    data class Published(val session: MidiCoreProjectSession, val draft: MidiCoreArrangementDraft) : MidiCoreArrangementDraftLifecycleResult
    data class Reused(val session: MidiCoreProjectSession, val draft: MidiCoreArrangementDraft) : MidiCoreArrangementDraftLifecycleResult
    data class Rejected(val problem: MidiCoreArrangementDraftProblem) : MidiCoreArrangementDraftLifecycleResult
}

/** Atomically promotes every reference in one valid draft or leaves all acceptances untouched. */
class MidiCoreArrangementDraftAcceptance(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "draft-accept-${UUID.randomUUID()}" },
    private val historyIdFactory: () -> String = { "accept-${UUID.randomUUID()}" },
) {
    fun use(request: UseMidiCoreArrangementDraft): MidiCoreArrangementDraftAcceptanceResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { useLocked(request) }

    private fun useLocked(request: UseMidiCoreArrangementDraft): MidiCoreArrangementDraftAcceptanceResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val project = try { artifacts.openProject(root) } catch (_: Exception) {
            return rejected(MidiCoreArrangementDraftProblemCode.INVALID_PROJECT, "The project cannot be verified before accepting this draft.", "Open a valid project and try again.")
        }
        if (project != request.session.project || (request.expectedRevision != null && project.revision != request.expectedRevision)) {
            return rejected(MidiCoreArrangementDraftProblemCode.REVISION_CONFLICT, "The project changed before this draft could be accepted.", "Reload Review and choose the draft again.")
        }
        val draft = project.arrangementDrafts.singleOrNull { it.id == request.draftId }
            ?: return rejected(MidiCoreArrangementDraftProblemCode.DRAFT_NOT_FOUND, "The requested complete draft is unavailable.", "Reload the project and choose a persisted complete draft.")
        validateDraft(root, project, draft)?.let { return MidiCoreArrangementDraftAcceptanceResult.Rejected(it) }
        val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
        val selected = draft.candidateReferences.map { reference -> requireNotNull(candidates[reference.candidateId]) }
        val existing = project.acceptances.associateBy { it.occurrenceId to it.role }
        val locked = selected.firstOrNull { candidate ->
            existing[candidate.occurrenceId to candidate.role]?.let { it.locked && it.candidateId != candidate.id } == true
        }
        if (locked != null) return rejected(
            MidiCoreArrangementDraftProblemCode.LOCKED,
            "A locked acceptance prevents using this complete draft at '${locked.occurrenceId}' ${locked.role.name.lowercase()}.",
            "Explicitly unlock that scoped acceptance before using the draft.",
        )
        val applied = selected.map { candidate ->
            existing[candidate.occurrenceId to candidate.role]
                ?.takeIf { it.candidateId == candidate.id && it.locked }
                ?: CandidateAcceptance(candidate.occurrenceId, candidate.role, candidate.id, request.locked)
        }
        val prior = applied.mapNotNull { acceptance -> existing[acceptance.occurrenceId to acceptance.role] }
        val now = Instant.now(clock).toString()
        val batch = try {
            MidiCoreArrangementDraftAcceptanceHistory(idFactory(), draft.id, prior, applied, now)
        } catch (_: Exception) {
            return rejected(MidiCoreArrangementDraftProblemCode.DRAFT_INVALID, "A unique batch acceptance history identifier could not be created.", "Retry without changing the draft.")
        }
        if (project.arrangementDraftAcceptanceHistory.any { it.id == batch.id }) return rejected(
            MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
            "The batch acceptance history identifier already exists.",
            "Retry the draft acceptance with a new history identifier.",
        )
        val individual = try {
            selected.map { candidate ->
                CandidateAcceptanceHistory(
                    historyIdFactory().also { require(SAFE_ID.matches(it)) },
                    candidate.occurrenceId,
                    candidate.role,
                    candidate.id,
                    if (existing.containsKey(candidate.occurrenceId to candidate.role)) MidiCoreAcceptanceAction.REPLACED else MidiCoreAcceptanceAction.ACCEPTED,
                    now,
                )
            }
        } catch (_: Exception) {
            return rejected(MidiCoreArrangementDraftProblemCode.DRAFT_INVALID, "Unique candidate acceptance history identifiers could not be created.", "Retry without changing the draft.")
        }
        if (individual.map(CandidateAcceptanceHistory::id).distinct().size != individual.size ||
            project.acceptanceHistory.any { old -> individual.any { it.id == old.id } }) return rejected(
            MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
            "Candidate acceptance history identifiers collided with existing evidence.",
            "Retry the draft acceptance with new history identifiers.",
        )
        val selectedIds = selected.map(MidiCoreCandidate::id).toSet()
        val replacedIds = prior.map(CandidateAcceptance::candidateId).toSet() - selectedIds
        val nextAcceptances = (project.acceptances.filterNot { acceptance ->
            acceptance.occurrenceId to acceptance.role in applied.map { it.occurrenceId to it.role }.toSet()
        } + applied).sortedWith(compareBy<CandidateAcceptance> { acceptance ->
            project.authority!!.occurrences.indexOfFirst { it.id == acceptance.occurrenceId }
        }.thenBy { it.role.ordinal })
        val next = try {
            project.copy(
                candidates = project.candidates.map { candidate ->
                    when {
                        candidate.id in selectedIds -> candidate.copy(status = MidiCoreCandidateStatus.ACCEPTED, rejectionReason = null)
                        candidate.id in replacedIds && candidate.status == MidiCoreCandidateStatus.ACCEPTED -> candidate.copy(status = MidiCoreCandidateStatus.CURRENT)
                        else -> candidate
                    }
                },
                acceptances = nextAcceptances,
                acceptanceHistory = project.acceptanceHistory + individual,
                arrangementDraftAcceptanceHistory = project.arrangementDraftAcceptanceHistory + batch,
                revision = Math.addExact(project.revision, 1L),
            )
        } catch (_: Exception) {
            return rejected(MidiCoreArrangementDraftProblemCode.DRAFT_INVALID, "The complete draft acceptance could not be prepared atomically.", "Resolve the listed scoped problem and retry; no acceptance changed.")
        }
        return try {
            artifacts.saveProject(root, next)
            MidiCoreArrangementDraftAcceptanceResult.Applied(MidiCoreProjectSession(root, next), draft, batch)
        } catch (_: Exception) {
            rejected(MidiCoreArrangementDraftProblemCode.SAVE_FAILED, "The complete draft acceptance could not be saved safely.", "Retry; no partial acceptance was recorded.")
        }
    }

    private fun rejected(code: MidiCoreArrangementDraftProblemCode, message: String, nextAction: String) =
        MidiCoreArrangementDraftAcceptanceResult.Rejected(MidiCoreArrangementDraftProblem(code, message, nextAction))
}

data class UseMidiCoreArrangementDraft(
    val session: MidiCoreProjectSession,
    val draftId: String,
    val locked: Boolean = false,
    val expectedRevision: Long? = session.project.revision,
)

sealed interface MidiCoreArrangementDraftAcceptanceResult {
    data class Applied(
        val session: MidiCoreProjectSession,
        val draft: MidiCoreArrangementDraft,
        val history: MidiCoreArrangementDraftAcceptanceHistory,
    ) : MidiCoreArrangementDraftAcceptanceResult
    data class Rejected(val problem: MidiCoreArrangementDraftProblem) : MidiCoreArrangementDraftAcceptanceResult
}

/** Revalidates the exact draft membership, candidate artifacts, authority, and reports without mutating state. */
internal fun validateDraft(
    root: Path,
    project: MidiCoreProject,
    draft: MidiCoreArrangementDraft,
    artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
): MidiCoreArrangementDraftProblem? {
    if (draft.styleVersion != MidiCoreArrangementStyleCatalog.VERSION || runCatching { MidiCoreArrangementStyleCatalog.require(draft.styleId) }.isFailure) {
        return problem(MidiCoreArrangementDraftProblemCode.DRAFT_STALE, "The draft style definition is no longer available at its recorded version.", "Create a new complete draft with a current style.")
    }
    val authority = try { MidiCoreAuthorityHasher.from(project) } catch (_: IllegalArgumentException) {
        return problem(MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED, "Current musical authority is required before using this draft.", "Restore source melody, tempo, meter, structure, and harmony.")
    }
    if (draft.authorityHash != authority.sha256) return problem(
        MidiCoreArrangementDraftProblemCode.DRAFT_STALE,
        "This complete draft was created for different musical authority.",
        "Create a new draft after reviewing the authority change.",
    )
    val expectedScopes = project.authority!!.occurrences.flatMap { occurrence -> CandidateRole.entries.map { occurrence.id to it } }
    if (draft.candidateReferences.map { it.occurrenceId to it.role } != expectedScopes) return problem(
        MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
        "The draft no longer covers every current occurrence and role in order.",
        "Create a new complete draft from current authority.",
    )
    val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
    val summary = validationSummary(root, draft.candidateReferences, project, artifacts)
        ?: return problem(MidiCoreArrangementDraftProblemCode.DIGEST_MISMATCH, "A draft candidate or validation report cannot be verified.", "Restore immutable candidate evidence or regenerate the affected scope.")
    if (summary != draft.validation) return problem(
        MidiCoreArrangementDraftProblemCode.DRAFT_INVALID,
        "The draft validation summary no longer matches its immutable candidate reports.",
        "Create a new complete draft from validated candidates.",
    )
    draft.candidateReferences.forEach { reference ->
        val candidate = candidates[reference.candidateId]
            ?: return problem(MidiCoreArrangementDraftProblemCode.DRAFT_INVALID, "A draft candidate record is missing.", "Restore project state or create a new complete draft.")
        if (candidate.role != reference.role || candidate.occurrenceId != reference.occurrenceId ||
            candidate.midi.sha256 != reference.midiSha256 || candidate.validationReport.sha256 != reference.validationReportSha256 ||
            candidate.authorityHash != reference.authorityHash || candidate.status in setOf(MidiCoreCandidateStatus.REJECTED, MidiCoreCandidateStatus.STALE) ||
            candidate.authorityHash != authority.scopeHash(candidate.occurrenceId, candidate.role)) {
            return problem(MidiCoreArrangementDraftProblemCode.DRAFT_STALE, "A draft candidate is stale or no longer matches its immutable reference.", "Regenerate the affected scope and create a new complete draft.")
        }
    }
    return null
}

internal fun validationSummary(
    root: Path,
    references: List<MidiCoreArrangementDraftCandidateReference>,
    project: MidiCoreProject,
    artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
): MidiCoreArrangementDraftValidationSummary? = runCatching {
    val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
    val reports = references.map { reference ->
        val candidate = requireNotNull(candidates[reference.candidateId])
        require(candidate.role == reference.role && candidate.occurrenceId == reference.occurrenceId)
        val report = MidiCoreRoleValidationReportJson.decode(Files.readString(artifacts.verify(root, candidate.validationReport)))
        require(report.passed && report.role == reference.role && report.occurrenceId == reference.occurrenceId)
        report
    }
    MidiCoreArrangementDraftValidationSummary(
        scopeCount = reports.size,
        noteCount = reports.sumOf { it.noteCount },
        allPassed = reports.all { it.passed },
        reportDigestSha256 = sha256(references.joinToString("|") { it.validationReportSha256 }),
    )
}.getOrNull()

private fun reference(candidate: MidiCoreCandidate) = MidiCoreArrangementDraftCandidateReference(
    candidate.occurrenceId,
    candidate.role,
    candidate.id,
    candidate.midi.sha256,
    candidate.validationReport.sha256,
    candidate.authorityHash,
)

private fun derivedSeed(rootSeed: Long, scope: MidiCoreArrangementDraftScope): Long =
    sha256("$rootSeed|${scope.occurrenceId}|${scope.role.name}").take(16).toULong(16).toLong()

private fun candidateId(draftId: String, authorityHash: String, scope: MidiCoreArrangementDraftScope, attempt: Int): String =
    "draft-${sha256("$draftId|$authorityHash|${scope.occurrenceId}|${scope.role.name}|$attempt").take(48)}"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun MidiCoreCandidateProblemCode.toDraftProblemCode(): MidiCoreArrangementDraftProblemCode = when (this) {
    MidiCoreCandidateProblemCode.STALE_PROJECT -> MidiCoreArrangementDraftProblemCode.STALE_PROJECT
    MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED -> MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED
    MidiCoreCandidateProblemCode.SAVE_FAILED -> MidiCoreArrangementDraftProblemCode.SAVE_FAILED
    MidiCoreCandidateProblemCode.DIGEST_MISMATCH -> MidiCoreArrangementDraftProblemCode.DIGEST_MISMATCH
    else -> MidiCoreArrangementDraftProblemCode.CANDIDATE_FAILURE
}

private fun problem(
    code: MidiCoreArrangementDraftProblemCode,
    message: String,
    nextAction: String,
    scope: MidiCoreArrangementDraftScope? = null,
) = MidiCoreArrangementDraftProblem(code, message, nextAction, scope)

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
