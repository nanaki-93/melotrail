package app.melotrail.video.application

import app.melotrail.video.adapter.LocalVideoBackend
import app.melotrail.video.adapter.comfyRequestFingerprint
import app.melotrail.video.domain.VideoExecutionPolicy
import app.melotrail.video.domain.VideoGenerationAttempt
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoGenerationInput
import app.melotrail.video.domain.VideoGenerationJob
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoGenerationOutput
import app.melotrail.video.domain.VideoGenerationResourceUsage
import app.melotrail.video.domain.VideoHostedExecutionPolicy
import app.melotrail.video.domain.VideoJobLedger
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoKeyframeGenerationInput
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoSubmissionPhase
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Short, atomic persistence operations for a shared video admission domain. */
interface VideoJobPersistence {
    fun loadOrCreate(admissionDomainId: String, createdAt: String): VideoJobLedger
    fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger
}

class VideoJobConcurrencyException(message: String) : IllegalStateException(message)

/** Backend calls must be bounded. Stable attempt identity is the idempotency/reconciliation key. */
interface VideoGenerationBackendPort {
    val backendId: String
    fun availability(): VideoBackendAvailability
    fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission
    fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation
    fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation
}

/** Declarative setup information only. V16 never executes these actions. */
interface VideoSetupCapabilityPort {
    val backendId: String
    fun describeSetup(): VideoBackendSetup
}

data class VideoBackendAvailability(
    val backendId: String,
    val status: VideoBackendAvailabilityStatus,
    val checkedAt: String,
    val supportedInputs: Set<VideoGenerationInputKind>,
    val availableModels: List<VideoAvailableModel>,
    val detail: String,
) {
    init {
        require(backendId.isNotBlank())
        require(runCatching { Instant.parse(checkedAt) }.isSuccess)
        require(availableModels.map(VideoAvailableModel::id).distinct().size == availableModels.size)
        require(detail.isNotBlank())
    }
}

enum class VideoBackendAvailabilityStatus { AVAILABLE, SETUP_REQUIRED, OFFLINE }
enum class VideoGenerationInputKind { KEYFRAME, VIDEO, CONTROLLED_MOTION }

data class VideoAvailableModel(val id: String, val version: String, val sha256: String? = null)

data class VideoBackendSetup(
    val backendId: String,
    val status: VideoBackendAvailabilityStatus,
    val requirements: List<VideoSetupRequirement>,
    val actions: List<VideoSetupAction>,
)

data class VideoSetupRequirement(val id: String, val description: String, val satisfied: Boolean)

data class VideoSetupAction(
    val id: String,
    val kind: VideoSetupActionKind,
    val label: String,
    val description: String,
)

enum class VideoSetupActionKind { INSTALL_TOOL, SELECT_MODEL, DOWNLOAD_MODEL, CONFIGURE_CREDENTIALS, OPEN_SETTINGS }

data class VideoBackendSubmissionCommand(
    val ownedAttempt: VideoOwnedBackendAttempt,
    val input: VideoGenerationInput,
    val modelRequirements: List<app.melotrail.video.domain.VideoModelRequirement>,
    val execution: VideoExecutionPolicy,
)

data class VideoOwnedBackendAttempt(
    val requestId: String,
    val requestFingerprint: String,
    val attemptId: String,
    val ownershipToken: String,
    val backendId: String,
    val providerWorkId: String?,
)

sealed interface VideoBackendSubmission {
    data class Accepted(val providerWorkId: String) : VideoBackendSubmission
    data class Completed(
        val providerWorkId: String?,
        val output: VideoBackendOutput,
        val actualCost: VideoBackendCost? = null,
        val resources: VideoGenerationResourceUsage? = null,
    ) : VideoBackendSubmission
    data class Rejected(val reason: String, val retryable: Boolean) : VideoBackendSubmission
    data class Uncertain(val reason: String) : VideoBackendSubmission
}

sealed interface VideoBackendObservation {
    data class Running(val providerWorkId: String?, val progressPercent: Int?) : VideoBackendObservation
    data class Completed(
        val providerWorkId: String?,
        val output: VideoBackendOutput,
        val actualCost: VideoBackendCost? = null,
        val resources: VideoGenerationResourceUsage? = null,
    ) : VideoBackendObservation
    data class Failed(
        val providerWorkId: String?,
        val reason: String,
        val retryable: Boolean,
        val actualCost: VideoBackendCost? = null,
        val resources: VideoGenerationResourceUsage? = null,
    ) : VideoBackendObservation
    data class Cancelled(
        val providerWorkId: String?,
        val actualCost: VideoBackendCost? = null,
        val resources: VideoGenerationResourceUsage? = null,
    ) : VideoBackendObservation
    /** Conclusive backend tombstone: this stable attempt identity cannot later start. */
    data object NotStarted : VideoBackendObservation
    /** No conclusion was possible. The coordinator retains admission and unknown progress. */
    data class Unknown(val detail: String) : VideoBackendObservation
}

sealed interface VideoBackendCancellation {
    data object Requested : VideoBackendCancellation
    /** Confirms stopped acknowledged work, not a tombstone for a still-ambiguous submission. */
    data object ConfirmedStopped : VideoBackendCancellation
    data class Unknown(val detail: String) : VideoBackendCancellation
}

data class VideoBackendOutput(
    val backendOutputId: String,
    val relativePath: String? = null,
    val sha256: String? = null,
    val byteCount: Long? = null,
)

data class VideoBackendCost(val currency: String, val amountMicros: Long) {
    init { require(currency.matches(Regex("[A-Z]{3}")) && amountMicros >= 0L) }
}

class VideoJobCoordinator(
    private val admissionDomainId: String,
    private val jobs: VideoJobPersistence,
    backends: Collection<VideoGenerationBackendPort>,
    setupCapabilities: Collection<VideoSetupCapabilityPort> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
    private val attemptIdFactory: () -> String = { "attempt-${UUID.randomUUID()}" },
    private val ownershipTokenFactory: () -> String = { "owner-${UUID.randomUUID()}" },
    private val outputIdFactory: () -> String = { "output-${UUID.randomUUID()}" },
) {
    private val backendById = backends.associateBy(VideoGenerationBackendPort::backendId)
    private val setupById = setupCapabilities.associateBy(VideoSetupCapabilityPort::backendId)

    init {
        require(admissionDomainId.isNotBlank()) { "Admission domain ID is required" }
        require(backendById.size == backends.size) { "Video backend IDs must be unique" }
        require(setupById.size == setupCapabilities.size) { "Video setup capability IDs must be unique" }
    }

    fun snapshot(): VideoJobLedger = jobs.loadOrCreate(admissionDomainId, now())

    fun availability(backendId: String): VideoBackendAvailability? = backendById[backendId]?.availability()

    fun setup(backendId: String): VideoBackendSetup? = setupById[backendId]?.describeSetup()

    fun submit(request: VideoGenerationJobRequest): VideoJobResult {
        validateExecutableFingerprint(request)?.let { return VideoJobResult.Rejected(it) }
        val persisted = try { snapshot() } catch (error: Exception) { return persistenceRejected(error) }
        persisted.jobs.singleOrNull { it.request.id == request.id }?.let { existing ->
            return if (existing.request == request) VideoJobResult.Accepted(existing, existing.attempts.lastOrNull(), false)
            else rejected(VideoJobProblemCode.REQUEST_ID_CONFLICT, "Video request '${request.id}' already has different immutable inputs.")
        }
        persisted.jobs.singleOrNull { it.request.requestFingerprint == request.requestFingerprint }?.let { existing ->
            if (existing.request.projectId != request.projectId) return rejected(
                VideoJobProblemCode.INPUT_NOT_SUPPORTED,
                "Video request fingerprint is already owned by another project; bind the project to the executable identity.",
            )
            if (!sameDeduplicatedRequest(existing.request, request)) return rejected(
                VideoJobProblemCode.INPUT_NOT_SUPPORTED, "Video request fingerprint conflicts with different immutable inputs.",
            )
            return VideoJobResult.Accepted(existing, existing.attempts.lastOrNull(), false)
        }
        val backend = backendById[request.backendId]
            ?: return rejected(VideoJobProblemCode.BACKEND_NOT_CONFIGURED, "Video backend '${request.backendId}' is not configured.")
        availabilityProblem(request, backend)?.let { return VideoJobResult.Rejected(it) }

        val admission = try {
            mutate { ledger -> admit(ledger, request) }
        } catch (error: VideoJobRuleException) {
            return VideoJobResult.Rejected(error.problem)
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        if (!admission.created) return VideoJobResult.Accepted(admission.job, admission.job.attempts.lastOrNull(), false)
        return launch(backend, admission.job.request.id, admission.job.attempts.last().id)
    }

    fun retry(requestId: String): VideoJobResult {
        val current = snapshot().jobs.singleOrNull { it.request.id == requestId }
            ?: return rejected(VideoJobProblemCode.REQUEST_NOT_FOUND, "Video request '$requestId' does not exist.")
        val backend = backendById[current.request.backendId]
            ?: return rejected(VideoJobProblemCode.BACKEND_NOT_CONFIGURED, "Video backend '${current.request.backendId}' is not configured.")
        availabilityProblem(current.request, backend)?.let { return VideoJobResult.Rejected(it) }
        val admission = try {
            mutate { ledger -> admitRetry(ledger, requestId) }
        } catch (error: VideoJobRuleException) {
            return VideoJobResult.Rejected(error.problem)
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        return launch(backend, requestId, admission.job.attempts.last().id)
    }

    fun cancel(requestId: String, attemptId: String? = null): VideoJobResult {
        val marked = try {
            mutate { ledger -> markCancellationRequested(ledger, requestId, attemptId) }
        } catch (error: VideoJobRuleException) {
            return VideoJobResult.Rejected(error.problem)
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        val attempt = marked.attempt ?: return VideoJobResult.Accepted(marked.job, null, false)
        if (attempt.status.isTerminal) return VideoJobResult.Accepted(marked.job, attempt, false)
        val backend = backendById[marked.job.request.backendId]
            ?: return rejected(VideoJobProblemCode.BACKEND_NOT_CONFIGURED, "Video backend '${marked.job.request.backendId}' is not configured.")
        val result = runCatching { backend.requestCancellation(owned(marked.job, attempt)) }
            .getOrElse { VideoBackendCancellation.Unknown(it.message ?: it.javaClass.simpleName) }
        val updated = integrateCancellation(requestId, attempt, result)
        return VideoJobResult.Accepted(updated, updated.attempts.single { it.id == attempt.id }, false)
    }

    fun reconcile(requestId: String, attemptId: String? = null): VideoJobResult {
        val ledger = try { snapshot() } catch (error: Exception) { return persistenceRejected(error) }
        var job = ledger.jobs.singleOrNull { it.request.id == requestId }
            ?: return rejected(VideoJobProblemCode.REQUEST_NOT_FOUND, "Video request '$requestId' does not exist.")
        var attempt = (if (attemptId == null) job.attempts.lastOrNull() else job.attempts.singleOrNull { it.id == attemptId })
            ?: return rejected(VideoJobProblemCode.ATTEMPT_NOT_FOUND, "The requested video attempt does not exist.")
        if (attempt.submissionPhase == VideoSubmissionPhase.READY) {
            // Close an interrupted admission atomically against launch. A suspended
            // submitting caller then loses its claim and cannot start this attempt.
            val unclaimed = attempt
            job = try {
                mutate { current ->
                    updateAttempt(current, requestId, unclaimed.id, unclaimed.ownershipToken) { currentJob, currentAttempt ->
                        if (currentAttempt.submissionPhase != VideoSubmissionPhase.READY) currentJob else terminal(
                            currentJob, currentAttempt, VideoGenerationAttemptStatus.FAILED,
                            retryable = true,
                            failure = "Recovery closed an admission before launch; an explicit retry is required.",
                            actualCostMicros = hostedZeroCost(currentJob.request.execution),
                            resources = null,
                            submissionPhase = VideoSubmissionPhase.NOT_STARTED,
                        )
                    }
                }
            } catch (error: Exception) {
                return persistenceRejected(error)
            }
            attempt = job.attempts.single { it.id == unclaimed.id }
        }
        if (attempt.submissionPhase == VideoSubmissionPhase.NOT_STARTED) {
            return VideoJobResult.Accepted(job, attempt, false)
        }
        val backend = backendById[job.request.backendId]
            ?: return rejected(VideoJobProblemCode.BACKEND_NOT_CONFIGURED, "Video backend '${job.request.backendId}' is not configured.")
        val observation = runCatching { backend.observe(owned(job, attempt)) }
            .getOrElse { VideoBackendObservation.Unknown(it.message ?: it.javaClass.simpleName) }
        val updated = try {
            integrateObservation(requestId, attempt, observation)
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        val updatedAttempt = updated.attempts.single { it.id == attempt.id }
        val finalJob = if (updatedAttempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) {
            val cancellation = runCatching { backend.requestCancellation(owned(updated, updatedAttempt)) }
                .getOrElse { VideoBackendCancellation.Unknown(it.message ?: it.javaClass.simpleName) }
            try {
                integrateCancellation(requestId, updatedAttempt, cancellation)
            } catch (error: Exception) {
                return persistenceRejected(error)
            }
        } else updated
        return VideoJobResult.Accepted(finalJob, finalJob.attempts.single { it.id == attempt.id }, false)
    }

    /** Reconciles unresolved attempts exactly once in this call and never creates a retry. */
    fun recover(): List<VideoJobResult> {
        val unresolved = snapshot().jobs.flatMap { job ->
            job.attempts.filterNot { it.status.isTerminal }.map { job.request.id to it.id }
        }
        return unresolved.map { (requestId, attemptId) -> reconcile(requestId, attemptId) }
    }

    private fun launch(backend: VideoGenerationBackendPort, requestId: String, attemptId: String): VideoJobResult {
        val claim = try {
            mutate { ledger ->
                val index = ledger.jobs.indexOfFirst { it.request.id == requestId }
                val job = ledger.jobs[index]
                val attempt = job.attempts.single { it.id == attemptId }
                if (attempt.submissionPhase != VideoSubmissionPhase.READY ||
                    attempt.status != VideoGenerationAttemptStatus.SUBMITTING || job.attempts.last().id != attemptId
                ) Mutation(ledger, LaunchClaim(job, false)) else {
                    // This CAS is the launch-versus-cancel decision. Only its winner
                    // may invoke submit, outside the persistence lock. After a crash
                    // PENDING must be reconciled, never submitted a second time.
                    val updated = job.replaceAttempt(attempt.copy(submissionPhase = VideoSubmissionPhase.PENDING))
                    Mutation(ledger.copy(jobs = ledger.jobs.updated(index, updated), revision = nextRevision(ledger)), LaunchClaim(updated, true))
                }
            }
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        val before = claim.job
        val attempt = before.attempts.single { it.id == attemptId }
        if (!claim.claimed) return VideoJobResult.Accepted(before, attempt, false)
        val submission = runCatching {
            backend.submit(
                VideoBackendSubmissionCommand(
                    owned(before, attempt),
                    before.request.input,
                    before.request.modelRequirements,
                    before.request.execution,
                ),
            )
        }.getOrElse { VideoBackendSubmission.Uncertain(it.message ?: it.javaClass.simpleName) }
        val updated = try {
            integrateSubmission(requestId, attemptId, attempt.ownershipToken, submission)
        } catch (error: Exception) {
            return persistenceRejected(error)
        }
        val updatedAttempt = updated.attempts.single { it.id == attemptId }
        if (updatedAttempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) {
            val cancellation = runCatching { backend.requestCancellation(owned(updated, updatedAttempt)) }
                .getOrElse { VideoBackendCancellation.Unknown(it.message ?: it.javaClass.simpleName) }
            integrateCancellation(requestId, updatedAttempt, cancellation)
        }
        val finalJob = snapshot().jobs.single { it.request.id == requestId }
        return VideoJobResult.Accepted(finalJob, finalJob.attempts.single { it.id == attemptId }, true)
    }

    private fun validateExecutableFingerprint(request: VideoGenerationJobRequest): VideoJobProblem? {
        val controlled = request.input as? VideoControlledMotionGenerationInput
        if (controlled == null && request.backendId != LocalVideoBackend.BACKEND_ID) return null
        val expected = try {
            if (controlled != null) controlledMotionRequestFingerprint(request.backendId, controlled, request.modelRequirements, request.maximumAttempts)
            else comfyRequestFingerprint(request.projectId, request.backendId, request.input, request.modelRequirements)
        } catch (error: IllegalArgumentException) {
            return VideoJobProblem(VideoJobProblemCode.INPUT_NOT_SUPPORTED, error.message ?: "Video fingerprint inputs are invalid.")
        }
        return if (request.requestFingerprint == expected) null else VideoJobProblem(
            VideoJobProblemCode.INPUT_NOT_SUPPORTED,
            if (controlled != null) "Controlled-motion request fingerprint does not match its executable inputs and dependency pins."
            else "ComfyUI request fingerprint does not match its project and executable inputs and dependency pins.",
        )
    }

    private fun availabilityProblem(
        request: VideoGenerationJobRequest,
        backend: VideoGenerationBackendPort,
    ): VideoJobProblem? {
        val availability = try {
            backend.availability()
        } catch (error: Exception) {
            return VideoJobProblem(VideoJobProblemCode.BACKEND_UNAVAILABLE, error.message ?: "Video backend availability is unknown.")
        }
        if (availability.backendId != request.backendId || availability.status != VideoBackendAvailabilityStatus.AVAILABLE) {
            return VideoJobProblem(VideoJobProblemCode.BACKEND_UNAVAILABLE, availability.detail)
        }
        val inputKind = when (request.input) {
            is VideoKeyframeGenerationInput -> VideoGenerationInputKind.KEYFRAME
            is VideoClipGenerationInput -> VideoGenerationInputKind.VIDEO
            is VideoControlledMotionGenerationInput -> VideoGenerationInputKind.CONTROLLED_MOTION
        }
        if (inputKind !in availability.supportedInputs) {
            return VideoJobProblem(VideoJobProblemCode.INPUT_NOT_SUPPORTED, "Video backend '${request.backendId}' does not support $inputKind requests.")
        }
        val available = availability.availableModels.associateBy(VideoAvailableModel::id)
        val missing = request.modelRequirements.filter { required ->
            val found = available[required.id]
            found == null || found.version != required.version || required.sha256 != null && found.sha256 != required.sha256
        }
        return missing.takeIf { it.isNotEmpty() }?.let {
            VideoJobProblem(VideoJobProblemCode.MODEL_REQUIREMENTS_UNMET, "Required video models are unavailable: ${it.joinToString { item -> item.id }}")
        }
    }

    private fun admit(ledger: VideoJobLedger, request: VideoGenerationJobRequest): Mutation<Admission> {
        ledger.jobs.singleOrNull { it.request.id == request.id }?.let { existing ->
            if (existing.request != request) rule(VideoJobProblemCode.REQUEST_ID_CONFLICT, "Video request '${request.id}' already has different immutable inputs.")
            return Mutation(ledger, Admission(existing, false))
        }
        ledger.jobs.singleOrNull { it.request.requestFingerprint == request.requestFingerprint }?.let { existing ->
            if (existing.request.projectId != request.projectId) rule(
                VideoJobProblemCode.INPUT_NOT_SUPPORTED,
                "Video request fingerprint is already owned by another project; bind the project to the executable identity.",
            )
            if (!sameDeduplicatedRequest(existing.request, request)) rule(
                VideoJobProblemCode.INPUT_NOT_SUPPORTED, "Video request fingerprint conflicts with different immutable inputs.",
            )
            return Mutation(ledger, Admission(existing, false))
        }
        requireAdmission(ledger, request)
        val attempt = newAttempt(request, 1)
        val job = VideoGenerationJob(request, attempts = listOf(attempt))
        return Mutation(ledger.copy(jobs = ledger.jobs + job, revision = nextRevision(ledger)), Admission(job, true))
    }

    private fun sameDeduplicatedRequest(existing: VideoGenerationJobRequest, incoming: VideoGenerationJobRequest): Boolean =
        existing == incoming.copy(id = existing.id, createdAt = existing.createdAt)

    private fun admitRetry(ledger: VideoJobLedger, requestId: String): Mutation<Admission> {
        val index = ledger.jobs.indexOfFirst { it.request.id == requestId }
        if (index < 0) rule(VideoJobProblemCode.REQUEST_NOT_FOUND, "Video request '$requestId' does not exist.")
        val job = ledger.jobs[index]
        val previous = job.attempts.lastOrNull() ?: rule(VideoJobProblemCode.RETRY_NOT_ALLOWED, "The request has no prior attempt.")
        if (!previous.status.isTerminal || !previous.retryable) {
            rule(VideoJobProblemCode.RETRY_NOT_ALLOWED, "Only a confirmed terminal retryable attempt can be retried.")
        }
        if (job.attempts.size >= job.request.maximumAttempts) {
            rule(VideoJobProblemCode.ATTEMPT_LIMIT_REACHED, "The request reached its bounded attempt limit.")
        }
        requireAdmission(ledger, job.request)
        val attempt = newAttempt(job.request, job.attempts.size + 1)
        val updated = job.copy(attempts = job.attempts + attempt)
        return Mutation(ledger.copy(jobs = ledger.jobs.updated(index, updated), revision = nextRevision(ledger)), Admission(updated, true))
    }

    private fun requireAdmission(ledger: VideoJobLedger, request: VideoGenerationJobRequest) {
        when (val execution = request.execution) {
            is VideoLocalExecutionPolicy -> {
                if (ledger.jobs.any { job ->
                        job.request.execution is VideoLocalExecutionPolicy && job.attempts.any { !it.status.isTerminal }
                    }
                ) {
                    rule(VideoJobProblemCode.LOCAL_SLOT_BUSY, "One local inference is already admitted in this domain.")
                }
            }
            is VideoHostedExecutionPolicy -> {
                val now = Instant.now(clock)
                val created = Instant.parse(execution.estimateCreatedAt)
                val expires = Instant.parse(execution.estimateExpiresAt)
                if (now.isBefore(created) || !now.isBefore(expires)) {
                    rule(VideoJobProblemCode.HOSTED_ESTIMATE_STALE, "Hosted admission requires a current cost estimate.")
                }
                if (execution.estimatedMaximumCostMicros > execution.authorizedSpendCapMicros) {
                    rule(VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED, "The request estimate exceeds its explicit spend cap.")
                }
                val policies = ledger.jobs.mapNotNull { it.request.execution as? VideoHostedExecutionPolicy }
                    .filter { it.budgetId == execution.budgetId }
                if (policies.any { it.currency != execution.currency || it.authorizedSpendCapMicros != execution.authorizedSpendCapMicros }) {
                    rule(VideoJobProblemCode.HOSTED_BUDGET_CONFLICT, "A hosted budget ID cannot change currency or spend cap.")
                }
                val reserved = runCatching {
                    ledger.jobs.fold(0L) { total, job ->
                        val policy = job.request.execution as? VideoHostedExecutionPolicy
                        if (policy?.budgetId != execution.budgetId) total else job.attempts.fold(total) { attemptsTotal, attempt ->
                            Math.addExact(attemptsTotal, attempt.actualCostMicros ?: policy.estimatedMaximumCostMicros)
                        }
                    }
                }.getOrNull()
                val total = reserved?.let { alreadyReserved ->
                    runCatching { Math.addExact(alreadyReserved, execution.estimatedMaximumCostMicros) }.getOrNull()
                }
                if (reserved == null || total == null || total > execution.authorizedSpendCapMicros) {
                    rule(VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED, "Hosted in-flight and prior attempt reservations exceed the explicit spend cap.")
                }
            }
        }
    }

    private fun markCancellationRequested(
        ledger: VideoJobLedger,
        requestId: String,
        attemptId: String?,
    ): Mutation<MarkedAttempt> {
        val jobIndex = ledger.jobs.indexOfFirst { it.request.id == requestId }
        if (jobIndex < 0) rule(VideoJobProblemCode.REQUEST_NOT_FOUND, "Video request '$requestId' does not exist.")
        val job = ledger.jobs[jobIndex]
        val selected = if (attemptId == null) job.attempts.lastOrNull() else job.attempts.singleOrNull { it.id == attemptId }
        if (selected == null) rule(VideoJobProblemCode.ATTEMPT_NOT_FOUND, "The requested video attempt does not exist.")
        if (selected.status.isTerminal) return Mutation(ledger, MarkedAttempt(job, null))
        if (selected.submissionPhase == VideoSubmissionPhase.READY) {
            val updated = terminal(
                job, selected, VideoGenerationAttemptStatus.CANCELLED,
                retryable = true, failure = null,
                actualCostMicros = hostedZeroCost(job.request.execution), resources = null,
                submissionPhase = VideoSubmissionPhase.NOT_STARTED,
            )
            return Mutation(
                ledger.copy(jobs = ledger.jobs.updated(jobIndex, updated), revision = nextRevision(ledger)),
                MarkedAttempt(updated, updated.attempts.single { it.id == selected.id }),
            )
        }
        val changed = selected.copy(status = VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, lastObservedAt = now())
        val updated = job.replaceAttempt(changed)
        return Mutation(ledger.copy(jobs = ledger.jobs.updated(jobIndex, updated), revision = nextRevision(ledger)), MarkedAttempt(updated, changed))
    }

    private fun integrateSubmission(
        requestId: String,
        attemptId: String,
        ownershipToken: String,
        submission: VideoBackendSubmission,
    ): VideoGenerationJob = mutate { ledger ->
        updateAttempt(ledger, requestId, attemptId, ownershipToken) { job, attempt ->
            val isStale = job.attempts.last().id != attempt.id
            when (submission) {
                is VideoBackendSubmission.Accepted -> {
                    val workId = mergeProviderWorkId(attempt.providerWorkId, submission.providerWorkId)
                    // An observation may already have acknowledged and stopped this same work.
                    // A delayed submit acknowledgement must not reopen its released admission.
                    if (attempt.status.isTerminal && attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED) return@updateAttempt job
                    val status = if (isStale || attempt.status.isTerminal || attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) {
                        VideoGenerationAttemptStatus.CANCELLATION_REQUESTED
                    } else VideoGenerationAttemptStatus.ACTIVE
                    job.replaceAttempt(attempt.copy(providerWorkId = workId, status = status, submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED, finishedAt = null, retryable = false, lastObservedAt = now()))
                }
                is VideoBackendSubmission.Completed -> complete(job, attempt.copy(submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED), submission.providerWorkId, submission.output, submission.actualCost, submission.resources)
                is VideoBackendSubmission.Rejected -> if (attempt.status.isTerminal || attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED) job else terminal(
                    job,
                    attempt,
                    VideoGenerationAttemptStatus.FAILED,
                    submission.retryable,
                    submission.reason,
                    hostedZeroCost(job.request.execution),
                    null,
                    submissionPhase = VideoSubmissionPhase.NOT_STARTED,
                )
                is VideoBackendSubmission.Uncertain -> if (attempt.status.isTerminal || attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED) job else job.replaceAttempt(
                    attempt.copy(
                        status = if (attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) attempt.status else VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN,
                        submissionPhase = VideoSubmissionPhase.UNCERTAIN,
                        lastObservedAt = now(),
                        failure = submission.reason.cleanDetail(),
                        finishedAt = null,
                        retryable = false,
                    ),
                )
            }
        }
    }

    private fun integrateCancellation(
        requestId: String,
        cancelledAttempt: VideoGenerationAttempt,
        cancellation: VideoBackendCancellation,
    ): VideoGenerationJob = mutate { ledger ->
        updateAttempt(ledger, requestId, cancelledAttempt.id, cancelledAttempt.ownershipToken) { job, attempt ->
            // A stop requested before acknowledgement cannot stop work acknowledged later.
            if (attempt != cancelledAttempt || attempt.status.isTerminal) return@updateAttempt job
            when (cancellation) {
                VideoBackendCancellation.Requested,
                is VideoBackendCancellation.Unknown,
                -> job.replaceAttempt(attempt.copy(status = VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, lastObservedAt = now(), finishedAt = null, retryable = false))
                VideoBackendCancellation.ConfirmedStopped -> if (attempt.submissionPhase != VideoSubmissionPhase.ACKNOWLEDGED) {
                    job.replaceAttempt(attempt.copy(status = VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, lastObservedAt = now()))
                } else terminal(
                    job,
                    attempt,
                    VideoGenerationAttemptStatus.CANCELLED,
                    retryable = true,
                    failure = null,
                    actualCostMicros = attempt.actualCostMicros,
                    resources = attempt.resourceUsage,
                )
            }
        }
    }

    private fun integrateObservation(
        requestId: String,
        observedAttempt: VideoGenerationAttempt,
        observation: VideoBackendObservation,
    ): VideoGenerationJob = mutate { ledger ->
        updateAttempt(ledger, requestId, observedAttempt.id, observedAttempt.ownershipToken) { job, attempt ->
            // A response obtained from an older snapshot cannot overwrite a newer observation/cancel result.
            if (attempt != observedAttempt) return@updateAttempt job
            val stale = job.attempts.last().id != attempt.id
            when (observation) {
                is VideoBackendObservation.Running -> {
                    if (attempt.status.isTerminal) return@updateAttempt job
                    val status = if (stale || attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) {
                        VideoGenerationAttemptStatus.CANCELLATION_REQUESTED
                    } else VideoGenerationAttemptStatus.ACTIVE
                    job.replaceAttempt(
                        attempt.copy(
                            providerWorkId = mergeProviderWorkId(attempt.providerWorkId, observation.providerWorkId),
                            status = status,
                            submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED,
                            progressPercent = observation.progressPercent ?: attempt.progressPercent,
                            lastObservedAt = now(),
                            finishedAt = null,
                            retryable = false,
                        ),
                    )
                }
                is VideoBackendObservation.Completed -> complete(job, attempt.copy(submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED), observation.providerWorkId, observation.output, observation.actualCost, observation.resources)
                is VideoBackendObservation.Failed -> if (attempt.status.isTerminal) job else terminal(
                    job,
                    attempt.copy(providerWorkId = mergeProviderWorkId(attempt.providerWorkId, observation.providerWorkId), submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED),
                    VideoGenerationAttemptStatus.FAILED,
                    observation.retryable,
                    observation.reason,
                    costMicros(job.request.execution, observation.actualCost),
                    observation.resources,
                )
                is VideoBackendObservation.Cancelled -> if (attempt.status.isTerminal) job else terminal(
                    job,
                    attempt.copy(providerWorkId = mergeProviderWorkId(attempt.providerWorkId, observation.providerWorkId), submissionPhase = VideoSubmissionPhase.ACKNOWLEDGED),
                    VideoGenerationAttemptStatus.CANCELLED,
                    retryable = true,
                    failure = null,
                    actualCostMicros = costMicros(job.request.execution, observation.actualCost),
                    resources = observation.resources,
                )
                VideoBackendObservation.NotStarted -> if (attempt.status.isTerminal || attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED) job else terminal(
                    job,
                    attempt,
                    if (attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) VideoGenerationAttemptStatus.CANCELLED else VideoGenerationAttemptStatus.FAILED,
                    retryable = true,
                    failure = if (attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) null else "Backend confirmed that the durable attempt never started.",
                    actualCostMicros = hostedZeroCost(job.request.execution),
                    resources = null,
                    submissionPhase = VideoSubmissionPhase.NOT_STARTED,
                )
                is VideoBackendObservation.Unknown -> if (attempt.status.isTerminal) job else job.replaceAttempt(
                    attempt.copy(
                        status = if (attempt.status == VideoGenerationAttemptStatus.CANCELLATION_REQUESTED) attempt.status else VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN,
                        // Polling uncertainty says nothing about whether submit has returned.
                        submissionPhase = attempt.submissionPhase,
                        lastObservedAt = now(),
                        failure = observation.detail.cleanDetail(),
                        finishedAt = null,
                        retryable = false,
                    ),
                )
            }
        }
    }

    private fun complete(
        job: VideoGenerationJob,
        attempt: VideoGenerationAttempt,
        providerWorkId: String?,
        backendOutput: VideoBackendOutput,
        actualCost: VideoBackendCost?,
        resources: VideoGenerationResourceUsage?,
    ): VideoGenerationJob {
        val priorForAttempt = job.outputs.singleOrNull { it.attemptId == attempt.id }
        require(priorForAttempt == null || priorForAttempt.backendOutputId == backendOutput.backendOutputId) {
            "A backend cannot replace a persisted output for an attempt"
        }
        if (attempt.status == VideoGenerationAttemptStatus.SUCCEEDED && priorForAttempt != null) {
            require(
                priorForAttempt.relativePath == backendOutput.relativePath &&
                    priorForAttempt.sha256 == backendOutput.sha256 &&
                    priorForAttempt.byteCount == backendOutput.byteCount
            ) { "A backend cannot replace persisted output evidence" }
            return job
        }
        val existing = priorForAttempt
        val output = existing ?: VideoGenerationOutput(
            id = outputIdFactory(),
            attemptId = attempt.id,
            backendOutputId = backendOutput.backendOutputId,
            createdAt = now(),
            relativePath = backendOutput.relativePath,
            sha256 = backendOutput.sha256,
            byteCount = backendOutput.byteCount,
        )
        val completed = attempt.copy(
            providerWorkId = mergeProviderWorkId(attempt.providerWorkId, providerWorkId),
            status = VideoGenerationAttemptStatus.SUCCEEDED,
            progressPercent = 100,
            lastObservedAt = now(),
            finishedAt = now(),
            retryable = false,
            failure = null,
            actualCostMicros = mergeCost(attempt.actualCostMicros, costMicros(job.request.execution, actualCost)),
            resourceUsage = mergeResources(attempt.resourceUsage, resources),
        )
        val newest = job.attempts.last().id == attempt.id
        return job.copy(
            attempts = job.attempts.map { if (it.id == attempt.id) completed else it },
            outputs = if (existing == null) job.outputs + output else job.outputs,
            currentOutputId = if (newest && job.currentOutputId == null) output.id else job.currentOutputId,
        )
    }

    private fun terminal(
        job: VideoGenerationJob,
        attempt: VideoGenerationAttempt,
        status: VideoGenerationAttemptStatus,
        retryable: Boolean,
        failure: String?,
        actualCostMicros: Long?,
        resources: VideoGenerationResourceUsage?,
        submissionPhase: VideoSubmissionPhase = attempt.submissionPhase,
    ): VideoGenerationJob = job.replaceAttempt(
        attempt.copy(
            status = status,
            submissionPhase = submissionPhase,
            lastObservedAt = now(),
            finishedAt = now(),
            retryable = retryable,
            failure = failure?.cleanDetail(),
            actualCostMicros = mergeCost(attempt.actualCostMicros, actualCostMicros),
            resourceUsage = mergeResources(attempt.resourceUsage, resources),
        ),
    )

    private fun updateAttempt(
        ledger: VideoJobLedger,
        requestId: String,
        attemptId: String,
        ownershipToken: String,
        update: (VideoGenerationJob, VideoGenerationAttempt) -> VideoGenerationJob,
    ): Mutation<VideoGenerationJob> {
        val jobIndex = ledger.jobs.indexOfFirst { it.request.id == requestId }
        if (jobIndex < 0) rule(VideoJobProblemCode.REQUEST_NOT_FOUND, "Video request '$requestId' does not exist.")
        val job = ledger.jobs[jobIndex]
        val attempt = job.attempts.singleOrNull { it.id == attemptId && it.ownershipToken == ownershipToken }
            ?: rule(VideoJobProblemCode.ATTEMPT_NOT_FOUND, "The owned video attempt does not exist.")
        val updated = update(job, attempt)
        if (updated == job) return Mutation(ledger, job)
        return Mutation(ledger.copy(jobs = ledger.jobs.updated(jobIndex, updated), revision = nextRevision(ledger)), updated)
    }

    private fun newAttempt(request: VideoGenerationJobRequest, number: Int) = VideoGenerationAttempt(
        id = attemptIdFactory(),
        requestId = request.id,
        number = number,
        ownershipToken = ownershipTokenFactory(),
        status = VideoGenerationAttemptStatus.SUBMITTING,
        submissionPhase = VideoSubmissionPhase.READY,
        admittedAt = now(),
    )

    private fun owned(job: VideoGenerationJob, attempt: VideoGenerationAttempt) = VideoOwnedBackendAttempt(
        requestId = job.request.id,
        requestFingerprint = job.request.requestFingerprint,
        attemptId = attempt.id,
        ownershipToken = attempt.ownershipToken,
        backendId = job.request.backendId,
        providerWorkId = attempt.providerWorkId,
    )

    private fun costMicros(execution: VideoExecutionPolicy, cost: VideoBackendCost?): Long? = when (execution) {
        is VideoLocalExecutionPolicy -> {
            require(cost == null) { "A local backend cannot report hosted cost" }
            null
        }
        is VideoHostedExecutionPolicy -> cost?.also { require(it.currency == execution.currency) }?.amountMicros
    }

    private fun hostedZeroCost(execution: VideoExecutionPolicy): Long? = if (execution is VideoHostedExecutionPolicy) 0L else null

    private fun mergeCost(current: Long?, reported: Long?): Long? {
        require(current == null || reported == null || current == reported) { "A backend cannot replace recorded actual cost" }
        return current ?: reported
    }

    private fun mergeResources(
        current: VideoGenerationResourceUsage?,
        reported: VideoGenerationResourceUsage?,
    ): VideoGenerationResourceUsage? {
        if (current == null) return reported
        if (reported == null) return current
        fun merge(label: String, old: Long?, new: Long?): Long? {
            require(old == null || new == null || old == new) { "A backend cannot replace recorded $label" }
            return old ?: new
        }
        return VideoGenerationResourceUsage(
            merge("wall-clock usage", current.wallClockMillis, reported.wallClockMillis),
            merge("peak-memory usage", current.peakMemoryBytes, reported.peakMemoryBytes),
            merge("disk usage", current.diskBytes, reported.diskBytes),
        )
    }

    private fun mergeProviderWorkId(current: String?, reported: String?): String? {
        if (reported == null) return current
        require(reported.isNotBlank() && reported.length <= 512 && reported.none(Char::isISOControl)) { "Provider work identity is invalid" }
        require(current == null || current == reported) { "A backend cannot replace a persisted provider work identity" }
        return reported
    }

    private fun nextRevision(ledger: VideoJobLedger): Long = Math.addExact(ledger.revision, 1L)

    private fun now(): String = Instant.now(clock).toString()

    private fun <T> mutate(change: (VideoJobLedger) -> Mutation<T>): T {
        repeat(MAX_MUTATION_RETRIES) {
            val current = jobs.loadOrCreate(admissionDomainId, now())
            require(current.admissionDomainId == admissionDomainId) { "Video job persistence returned another admission domain" }
            val mutation = change(current)
            if (mutation.ledger === current || mutation.ledger == current) return mutation.result
            try {
                jobs.compareAndSet(current.revision, mutation.ledger)
                return mutation.result
            } catch (_: VideoJobConcurrencyException) {
                // Re-evaluate admission, cancellation or observation against the new durable state.
            }
        }
        throw VideoJobConcurrencyException("Video job admission stayed busy after $MAX_MUTATION_RETRIES bounded retries")
    }

    private fun persistenceRejected(error: Exception): VideoJobResult.Rejected = VideoJobResult.Rejected(
        VideoJobProblem(VideoJobProblemCode.PERSISTENCE_FAILED, error.message ?: "Video job state could not be persisted."),
    )

    private fun rejected(code: VideoJobProblemCode, message: String) = VideoJobResult.Rejected(VideoJobProblem(code, message))

    private fun rule(code: VideoJobProblemCode, message: String): Nothing = throw VideoJobRuleException(VideoJobProblem(code, message))

    private data class Mutation<T>(val ledger: VideoJobLedger, val result: T)
    private data class Admission(val job: VideoGenerationJob, val created: Boolean)
    private data class LaunchClaim(val job: VideoGenerationJob, val claimed: Boolean)
    private data class MarkedAttempt(val job: VideoGenerationJob, val attempt: VideoGenerationAttempt?)

    companion object { private const val MAX_MUTATION_RETRIES = 64 }
}

sealed interface VideoJobResult {
    data class Accepted(
        val job: VideoGenerationJob,
        val attempt: VideoGenerationAttempt?,
        val launchedByCaller: Boolean,
    ) : VideoJobResult
    data class Rejected(val problem: VideoJobProblem) : VideoJobResult
}

data class VideoJobProblem(val code: VideoJobProblemCode, val message: String)

enum class VideoJobProblemCode {
    BACKEND_NOT_CONFIGURED,
    BACKEND_UNAVAILABLE,
    MODEL_REQUIREMENTS_UNMET,
    INPUT_NOT_SUPPORTED,
    REQUEST_ID_CONFLICT,
    REQUEST_NOT_FOUND,
    ATTEMPT_NOT_FOUND,
    RETRY_NOT_ALLOWED,
    ATTEMPT_LIMIT_REACHED,
    LOCAL_SLOT_BUSY,
    HOSTED_ESTIMATE_STALE,
    HOSTED_BUDGET_CONFLICT,
    HOSTED_BUDGET_EXCEEDED,
    PERSISTENCE_FAILED,
}

private class VideoJobRuleException(val problem: VideoJobProblem) : IllegalStateException(problem.message)

private fun VideoGenerationJob.replaceAttempt(attempt: VideoGenerationAttempt): VideoGenerationJob =
    copy(attempts = attempts.map { if (it.id == attempt.id) attempt else it })

private fun <T> List<T>.updated(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

private fun String.cleanDetail(): String = trim().ifBlank { "Backend returned no detail." }.take(2_000).filterNot(Char::isISOControl)
