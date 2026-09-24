package app.melotrail.video.application

import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoExecutionPolicy
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoModelRequirement
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.adapter.VideoImportedTake
import app.melotrail.video.adapter.VideoMediaProbeRequest
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.adapter.VideoTakeMediaFacts
import app.melotrail.video.adapter.VideoMotionRuntime
import app.melotrail.video.adapter.VideoMotionRenderer
import app.melotrail.video.adapter.VideoMotionRenderRequest
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import java.nio.file.Path
import java.time.Duration
import java.time.Clock
import java.time.Instant

/** Production orchestration from already prepared motion through durable job and immutable take. */
class VideoClipGeneration(
    private val preparation: VideoScenePreparation,
    private val coordinator: VideoJobCoordinator,
    private val resultImport: VideoResultImport,
    private val motionRenderer: VideoMotionRenderer,
    private val backendId: String,
    private val execution: VideoExecutionPolicy,
    private val modelRequirements: List<VideoModelRequirement> = emptyList(),
    private val clock: Clock = Clock.systemUTC(),
    private val requestIdFactory: () -> String = { "clip-${java.util.UUID.randomUUID()}" },
) {
    fun generate(request: VideoClipGenerationRequest): VideoClipGenerationResult {
        if (request.project.revision != request.expectedRevision) return VideoClipGenerationResult.Rejected("Project revision changed before generation.")
        if (execution !is VideoLocalExecutionPolicy) return VideoClipGenerationResult.Rejected("Controlled compositor execution requires an explicitly configured local execution policy.")
        val prepared = preparation.prepare(request.look, request.scene, request.motionRequest, request.backendCapabilities, request.guidelineSet)
        val preparedResult = (prepared as? VideoScenePreparationResult.Prepared)
            ?: return VideoClipGenerationResult.Rejected("Prepared scene inputs were rejected: ${(prepared as VideoScenePreparationResult.Rejected).problems.joinToString { it.message }}")
        // Preparation describes asset eligibility, not compositor execution. Do not persist a
        // controlled job for I2V or a control outside the selected renderer's implemented limits.
        if (preparedResult.input.controls.any { it.intent == VideoSceneMotionIntent.CAMERA_OR_AMBIENT }) {
            return VideoClipGenerationResult.Rejected("Composed-image I2V requires the separate local video stage; the controlled compositor cannot execute it. Select only supported compositor controls.")
        }
        val unsupported = preparedResult.input.controls.mapNotNull { input ->
            preparation.rendererUnsupportedReason(input)?.let { "${input.id}: $it" }
        }
        if (unsupported.isNotEmpty()) {
            return VideoClipGenerationResult.Rejected("Controlled compositor request is unsupported: ${unsupported.joinToString(" ")} Supply a supported reduced request; see motion-runtime.json.")
        }
        if (preparedResult.input.componentReviews.any { it.status == app.melotrail.video.domain.VideoComponentReviewStatus.REJECTED }) {
            return VideoClipGenerationResult.Rejected("Prepared motion includes rejected component review state.")
        }
        val runtime = request.runtimeDependencies
        if (runtime.isEmpty()) return VideoClipGenerationResult.Rejected("Explicit compositor runtime pins are required.")
        val pinById = (request.preparedDependencies + runtime).associateBy(VideoGenerationDependencyPin::id)
        if (pinById.size != request.preparedDependencies.size + runtime.size) return VideoClipGenerationResult.Rejected("Prepared/runtime dependency IDs must be unique.")
        val input = try {
            VideoControlledMotionGenerationInput(
                prompt = preparedResult.input.backendMotionPrompt,
                dependencyPins = pinById.values.toList(),
                motion = app.melotrail.video.domain.VideoControlledMotionRequest(
                    preparedPins = request.preparedDependencies,
                    controls = preparedResult.input.controls.associate { it.id to "${it.capability.control}:${it.requestedDefaultValue}" },
                    startFrame = request.startFrame,
                    endFrameExclusive = request.endFrameExclusive,
                    seed = request.seed,
                    runtimeDependencies = runtime,
                ),
            )
        } catch (error: IllegalArgumentException) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Controlled motion inputs are invalid.")
        }
        val fingerprint = app.melotrail.video.domain.controlledMotionRequestFingerprint(backendId, input, modelRequirements)
        val jobRequest = try {
            VideoGenerationJobRequest(request.requestId ?: requestIdFactory(), request.project.id, backendId, modelRequirements, input,
                fingerprint, request.maximumAttempts, Instant.now(clock).toString(), execution)
        } catch (error: IllegalArgumentException) {
            return VideoClipGenerationResult.Rejected(error.message ?: "Generation request is invalid.")
        }
        val admitted = coordinator.submit(jobRequest)
        if (admitted is VideoJobResult.Rejected) return VideoClipGenerationResult.Rejected(admitted.problem.message)
        admitted as VideoJobResult.Accepted
        if (admitted.attempt?.status == app.melotrail.video.domain.VideoGenerationAttemptStatus.FAILED ||
            admitted.attempt?.status == app.melotrail.video.domain.VideoGenerationAttemptStatus.CANCELLED) {
            return VideoClipGenerationResult.Rejected(admitted.attempt.failure ?: "Controlled motion attempt did not complete.")
        }
        return VideoClipGenerationResult.Admitted(admitted.job.request, admitted)
    }

    /** Called by the controlled-motion media stage only after the coordinator has persisted and claimed the attempt. */
    fun renderControlled(request: VideoControlledRenderRequest, cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()) =
        motionRenderer.render(VideoMotionRenderRequest(request.runtime, request.projectRoot, request.descriptor, request.outputParent,
            request.startFrame, request.endFrameExclusive, request.timeoutPerInvocation), cancellation)

    /** A result is importable only after its newest durable attempt succeeded. */
    fun importCompleted(request: VideoCompletedTakeImport): VideoClipGenerationResult {
        if (request.project.revision != request.expectedRevision) return VideoClipGenerationResult.Rejected("Project revision changed before result import.")
        val job = coordinator.snapshot().jobs.singleOrNull { it.request.id == request.requestId }
            ?: return VideoClipGenerationResult.Rejected("Durable generation job was not found.")
        val input = job.request.input as? VideoControlledMotionGenerationInput
            ?: return VideoClipGenerationResult.Rejected("The completed job is not a controlled-motion request.")
        if (job.request.projectId != request.project.id || request.session.project.id != request.project.id ||
            request.session.project.revision != request.expectedRevision || job.request.backendId != backendId ||
            job.request.requestFingerprint != controlledMotionRequestFingerprint(backendId, input, job.request.modelRequirements) ||
            job.request.execution !is VideoLocalExecutionPolicy ||
            input.motion.endFrameExclusive - input.motion.startFrame != request.facts.frameCount) {
            return VideoClipGenerationResult.Rejected("Completed motion identity, execution or measured frame count does not match this project.")
        }
        if (job.attempts.lastOrNull()?.status != app.melotrail.video.domain.VideoGenerationAttemptStatus.SUCCEEDED) {
            return VideoClipGenerationResult.Rejected("Only the latest successfully completed attempt can become a take.")
        }
        val output = job.outputs.singleOrNull { it.attemptId == job.attempts.last().id && it.id == job.currentOutputId }
            ?: return VideoClipGenerationResult.Rejected("Successful job has no current immutable output.")
        return try {
            VideoClipGenerationResult.Imported(resultImport.import(request.session, request.expectedRevision, output,
                request.probeRequest, request.lookId, request.facts))
        } catch (error: Exception) {
            VideoClipGenerationResult.Rejected(error.message ?: "Generated media could not be imported safely.")
        }
    }
}

data class VideoClipGenerationRequest(
    val project: VideoProject,
    val expectedRevision: Long,
    val look: VideoSceneLook,
    val scene: app.melotrail.video.domain.VideoPreparedScene,
    val motionRequest: VideoSceneMotionRequest,
    val backendCapabilities: app.melotrail.video.domain.VideoPromptBackendCapabilities,
    val guidelineSet: VideoPromptGuidelineSet,
    val preparedDependencies: List<VideoGenerationDependencyPin>,
    val runtimeDependencies: List<VideoGenerationDependencyPin>,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val seed: Long,
    val maximumAttempts: Int = 2,
    val requestId: String? = null,
) {
    init {
        require(expectedRevision >= 0 && startFrame >= 0 && endFrameExclusive > startFrame && maximumAttempts in 1..3)
        require(requestId == null || requestId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")))
    }
}

data class VideoControlledRenderRequest(
    val runtime: VideoMotionRuntime,
    val projectRoot: Path,
    val descriptor: Path,
    val outputParent: Path,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val timeoutPerInvocation: Duration,
)

data class VideoCompletedTakeImport(
    val session: VideoProjectSession,
    val project: VideoProject,
    val expectedRevision: Long,
    val requestId: String,
    val probeRequest: VideoMediaProbeRequest,
    val lookId: VideoVersionedId?,
    val facts: VideoTakeMediaFacts,
)

sealed interface VideoClipGenerationResult {
    data class Admitted(val request: VideoGenerationJobRequest, val result: VideoJobResult) : VideoClipGenerationResult
    data class Imported(val result: VideoImportedTake) : VideoClipGenerationResult
    data class Rejected(val reason: String) : VideoClipGenerationResult
}

