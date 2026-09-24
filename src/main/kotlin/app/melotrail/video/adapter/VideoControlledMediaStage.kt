package app.melotrail.video.adapter

import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Claimed controlled-motion worker. Rendering/encoding is supplied by the media pipeline;
 * no native work is performed by submit or while the job ledger is locked. A missing worker
 * after restart is unknown, never a reason to relaunch a PENDING attempt. */
class VideoControlledMediaStage(
    private val jobs: VideoJobPersistence,
    private val admissionDomainId: String,
    private val execute: (VideoGenerationJobRequest, VideoOwnedBackendAttempt, VideoMediaProcessCancellation) -> VideoBackendObservation,
    private val clock: Clock = Clock.systemUTC(),
) : VideoGenerationBackendPort {
    override val backendId: String = BACKEND_ID

    override fun availability() = VideoBackendAvailability(
        backendId, VideoBackendAvailabilityStatus.AVAILABLE, Instant.now(clock).toString(),
        setOf(VideoGenerationInputKind.CONTROLLED_MOTION), emptyList(),
        "Controlled media worker configured; ComfyUI and models are not required.",
    )

    override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
        val owned = command.ownedAttempt
        val request = try { claimed(owned) } catch (error: Exception) {
            return VideoBackendSubmission.Rejected(error.message ?: "Claim verification failed.", false)
        } ?: return VideoBackendSubmission.Rejected("Current PENDING controlled attempt is not owned by this submission.", false)
        if (request.input != command.input || request.modelRequirements != command.modelRequirements || request.execution != command.execution) {
            return VideoBackendSubmission.Rejected("Submission fields differ from persisted controlled execution.", false)
        }
        val key = "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}"
        val work = Work()
        if (workers.putIfAbsent(key, work) != null) return VideoBackendSubmission.Uncertain("Controlled attempt was already submitted; observe its existing work.")
        try {
            pool.execute {
                try {
                    // Cancellation or a terminal reconciliation can win after submit. Never
                    // execute a caller's replacement command or run an unclaimed attempt.
                    val current = claimed(owned, allowAcknowledged = true)
                    work.result = if (current == null || work.cancellation.isCancelled()) {
                        VideoBackendObservation.Unknown("Claim changed or cancellation preceded controlled execution.")
                    } else execute(current, owned, work.cancellation)
                } catch (error: Exception) {
                    work.result = VideoBackendObservation.Unknown(error.message ?: "Controlled worker outcome is uncertain.")
                }
            }
        } catch (error: Exception) {
            // No task was enqueued; do not allow another invocation of this identity.
            work.result = VideoBackendObservation.Unknown("Controlled worker could not start: ${error.message}")
            return VideoBackendSubmission.Uncertain("Controlled worker could not start: ${error.message}")
        }
        return VideoBackendSubmission.Accepted(key)
    }

    override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation {
        val key = "$admissionDomainId:${ownedAttempt.requestId}:${ownedAttempt.attemptId}:${ownedAttempt.ownershipToken}"
        if (ownedAttempt.backendId != backendId ||
            ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key) {
            return VideoBackendObservation.Unknown("Controlled attempt identity does not match the worker.")
        }
        val work = workers[key] ?: return VideoBackendObservation.Unknown("Claimed controlled execution has no known worker; do not relaunch it.")
        return work.result ?: VideoBackendObservation.Running(key, null)
    }

    override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation {
        val key = "$admissionDomainId:${ownedAttempt.requestId}:${ownedAttempt.attemptId}:${ownedAttempt.ownershipToken}"
        if (ownedAttempt.backendId != backendId ||
            ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != key) {
            return VideoBackendCancellation.Unknown("Controlled attempt identity does not match the worker.")
        }
        val work = workers[key] ?: return VideoBackendCancellation.Unknown("No owned worker can be confirmed stopped.")
        work.cancellation.cancel()
        return VideoBackendCancellation.Requested
    }

    private fun claimed(owned: VideoOwnedBackendAttempt, allowAcknowledged: Boolean = false): VideoGenerationJobRequest? {
        if (owned.backendId != backendId || owned.providerWorkId != null) return null
        val ledger = jobs.loadOrCreate(admissionDomainId, Instant.now(clock).toString())
        require(ledger.admissionDomainId == admissionDomainId) { "Another admission domain was returned." }
        val job = ledger.jobs.singleOrNull { it.request.id == owned.requestId } ?: return null
        val attempt = job.attempts.lastOrNull() ?: return null
        val request = job.request
        val input = request.input as? VideoControlledMotionGenerationInput ?: return null
        if (attempt.id != owned.attemptId || attempt.ownershipToken != owned.ownershipToken ||
            !(attempt.submissionPhase == VideoSubmissionPhase.PENDING && attempt.status == VideoGenerationAttemptStatus.SUBMITTING ||
                allowAcknowledged && attempt.submissionPhase == VideoSubmissionPhase.ACKNOWLEDGED &&
                attempt.status == VideoGenerationAttemptStatus.ACTIVE && attempt.providerWorkId ==
                "$admissionDomainId:${owned.requestId}:${owned.attemptId}:${owned.ownershipToken}") ||
            (!allowAcknowledged && attempt.providerWorkId != null) || request.backendId != backendId ||
            request.requestFingerprint != owned.requestFingerprint || request.projectId != input.motion.descriptor.projectId ||
            request.execution !is VideoLocalExecutionPolicy || request.execution != input.media.execution ||
            request.requestFingerprint != controlledMotionRequestFingerprint(backendId, input, request.modelRequirements, request.maximumAttempts)
        ) return null
        return request
    }

    private class Work {
        val cancellation = VideoMediaProcessCancellation()
        @Volatile var result: VideoBackendObservation? = null
    }

    companion object {
        const val BACKEND_ID = "controlled-media"
        private val workers = ConcurrentHashMap<String, Work>()
        private val pool = Executors.newFixedThreadPool(1) { task ->
            Thread(task, "video-controlled-media").apply { isDaemon = true }
        }
    }
}
