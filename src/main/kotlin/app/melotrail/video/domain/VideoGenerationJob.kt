package app.melotrail.video.domain

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Durable, provider-neutral state for one admission domain. */
@Serializable
data class VideoJobLedger(
    val admissionDomainId: String,
    val createdAt: String,
    val jobs: List<VideoGenerationJob> = emptyList(),
    val revision: Long = 0L,
) {
    init {
        requireVideoJobId(admissionDomainId, "Admission domain")
        requireVideoJobTimestamp(createdAt, "Admission domain creation")
        require(revision >= 0L) { "Video job ledger revision must not be negative" }
        require(jobs.map { it.request.id }.distinct().size == jobs.size) { "Video request IDs must be unique" }
        require(jobs.map { it.request.requestFingerprint }.distinct().size == jobs.size) {
            "Video request fingerprints must be unique within an admission domain"
        }
        val attemptIds = jobs.flatMap { it.attempts }.map(VideoGenerationAttempt::id)
        val ownershipTokens = jobs.flatMap { it.attempts }.map(VideoGenerationAttempt::ownershipToken)
        val outputIds = jobs.flatMap { it.outputs }.map(VideoGenerationOutput::id)
        require(attemptIds.distinct().size == attemptIds.size) { "Video attempt IDs must be unique" }
        require(ownershipTokens.distinct().size == ownershipTokens.size) { "Video attempt ownership tokens must be unique" }
        require(outputIds.distinct().size == outputIds.size) { "Video output IDs must be unique" }
    }
}

@Serializable
data class VideoGenerationJob(
    val request: VideoGenerationJobRequest,
    val attempts: List<VideoGenerationAttempt> = emptyList(),
    val outputs: List<VideoGenerationOutput> = emptyList(),
    /** Set only by a completion for the newest attempt. Older late results remain inspectable. */
    val currentOutputId: String? = null,
) {
    init {
        require(attempts.size <= request.maximumAttempts) { "A video request exceeded its bounded attempt count" }
        require(attempts.all { it.requestId == request.id }) { "Every attempt must belong to its request" }
        require(attempts.map(VideoGenerationAttempt::number) == (1..attempts.size).toList()) {
            "Video attempt numbers must be contiguous and start at one"
        }
        val attemptIds = attempts.map(VideoGenerationAttempt::id).toSet()
        require(outputs.all { it.attemptId in attemptIds }) { "Every video output must belong to a persisted attempt" }
        require(outputs.map(VideoGenerationOutput::id).distinct().size == outputs.size) { "Video output IDs must be unique" }
        require(outputs.map(VideoGenerationOutput::attemptId).distinct().size == outputs.size) {
            "One video attempt can publish at most one immutable output"
        }
        require(outputs.all { output -> attempts.single { it.id == output.attemptId }.status == VideoGenerationAttemptStatus.SUCCEEDED }) {
            "Only a succeeded attempt can own an output"
        }
        require(currentOutputId == null || outputs.any { it.id == currentOutputId }) {
            "The current output must identify a persisted output"
        }
        require(currentOutputId == null || outputs.single { it.id == currentOutputId }.attemptId == attempts.last().id) {
            "Only the newest attempt can provide the current output"
        }
    }
}

@Serializable
data class VideoGenerationJobRequest(
    val id: String,
    val projectId: String,
    val backendId: String,
    val modelRequirements: List<VideoModelRequirement>,
    val input: VideoGenerationInput,
    val requestFingerprint: String,
    val maximumAttempts: Int,
    val createdAt: String,
    val execution: VideoExecutionPolicy,
) {
    init {
        requireVideoJobId(id, "Video request")
        requireVideoJobId(projectId, "Video project")
        requireVideoJobId(backendId, "Video backend")
        require(modelRequirements.map(VideoModelRequirement::id).distinct().size == modelRequirements.size) {
            "Video model requirement IDs must be unique"
        }
        requireVideoJobSha256(requestFingerprint, "Video request fingerprint")
        require(maximumAttempts in 1..MAX_VIDEO_GENERATION_ATTEMPTS) {
            "Video generation must use 1..$MAX_VIDEO_GENERATION_ATTEMPTS attempts"
        }
        requireVideoJobTimestamp(createdAt, "Video request creation")
    }
}

@Serializable
sealed interface VideoGenerationInput {
    val prompt: String
    val dependencyPins: List<VideoGenerationDependencyPin>
    /** Persisted executable binding; the free-form prompt is never parsed as a graph or path. */
    val comfyWorkflow: VideoComfyWorkflowRequest?
}

@Serializable
@SerialName("keyframe")
data class VideoKeyframeGenerationInput(
    override val prompt: String,
    override val dependencyPins: List<VideoGenerationDependencyPin>,
    val width: Int,
    val height: Int,
    override val comfyWorkflow: VideoComfyWorkflowRequest? = null,
) : VideoGenerationInput {
    init {
        requireVideoPrompt(prompt)
        requireVideoDependencyPins(dependencyPins)
        requireComfyDependencies(dependencyPins, comfyWorkflow)
        require(width in 64..8192 && height in 64..8192) { "Keyframe dimensions are outside supported orchestration bounds" }
    }
}

@Serializable
@SerialName("video")
data class VideoClipGenerationInput(
    override val prompt: String,
    override val dependencyPins: List<VideoGenerationDependencyPin>,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val framesPerSecond: Int,
    override val comfyWorkflow: VideoComfyWorkflowRequest? = null,
) : VideoGenerationInput {
    init {
        requireVideoPrompt(prompt)
        requireVideoDependencyPins(dependencyPins)
        requireComfyDependencies(dependencyPins, comfyWorkflow)
        require(durationMillis in 100L..60_000L) { "One generated video request must be 0.1..60 seconds" }
        require(width in 64..8192 && height in 64..8192) { "Video dimensions are outside supported orchestration bounds" }
        require(framesPerSecond in 1..120) { "Video frame rate is outside supported orchestration bounds" }
    }
}

@Serializable
data class VideoGenerationDependencyPin(
    val id: String,
    val sha256: String,
    /** Absolute owned file path when this dependency is consumed by a local workflow. */
    val ownedPath: String? = null,
) {
    init {
        requireVideoJobId(id, "Video generation dependency")
        requireVideoJobSha256(sha256, "Video generation dependency")
        ownedPath?.let {
            require(it.isNotBlank() && it.length <= 8_192 && it.none(Char::isISOControl)) {
                "Video generation dependency path is invalid"
            }
        }
    }
}

/** API-format ComfyUI node bindings persisted with the immutable request for restart recovery. */
@Serializable
data class VideoComfyWorkflowRequest(
    val workflowDependencyId: String,
    val promptInput: VideoComfyInputSlot,
    val referenceInputs: List<VideoComfyReferenceInput> = emptyList(),
    val widthInput: VideoComfyInputSlot? = null,
    val heightInput: VideoComfyInputSlot? = null,
    val frameCountInput: VideoComfyInputSlot? = null,
    val framesPerSecondInput: VideoComfyInputSlot? = null,
    val output: VideoComfyOutputBinding,
) {
    init {
        requireVideoJobId(workflowDependencyId, "ComfyUI workflow dependency")
        require(referenceInputs.size <= 32) { "A ComfyUI workflow can consume at most 32 references" }
        require(referenceInputs.map(VideoComfyReferenceInput::dependencyId).distinct().size == referenceInputs.size) {
            "ComfyUI reference dependencies must be unique"
        }
        require(referenceInputs.map(VideoComfyReferenceInput::slot).distinct().size == referenceInputs.size) {
            "ComfyUI reference input slots must be unique"
        }
        val scalarSlots = listOfNotNull(promptInput, widthInput, heightInput, frameCountInput, framesPerSecondInput)
        require(scalarSlots.distinct().size == scalarSlots.size) { "ComfyUI scalar input slots must be unique" }
        require(referenceInputs.none { it.slot in scalarSlots }) { "ComfyUI reference and scalar slots must be distinct" }
    }
}

@Serializable
data class VideoComfyInputSlot(val nodeId: String, val inputName: String) {
    init {
        require(COMFY_NODE_ID.matches(nodeId)) { "ComfyUI node ID is invalid" }
        require(COMFY_INPUT_NAME.matches(inputName)) { "ComfyUI input name is invalid" }
    }
}

@Serializable
data class VideoComfyReferenceInput(
    val dependencyId: String,
    val slot: VideoComfyInputSlot,
    val uploadFileName: String,
) {
    init {
        requireVideoJobId(dependencyId, "ComfyUI reference dependency")
        require(COMFY_UPLOAD_NAME.matches(uploadFileName) && uploadFileName !in setOf(".", "..")) {
            "ComfyUI upload filename is invalid"
        }
    }
}

@Serializable
data class VideoComfyOutputBinding(
    val nodeId: String,
    val allowedExtensions: Set<String>,
) {
    init {
        require(COMFY_NODE_ID.matches(nodeId)) { "ComfyUI output node ID is invalid" }
        require(allowedExtensions.isNotEmpty() && allowedExtensions.size <= 8 &&
            allowedExtensions.all { COMFY_EXTENSION.matches(it) }
        ) { "ComfyUI output extensions must be lowercase safe extensions" }
    }
}

@Serializable
data class VideoModelRequirement(
    val id: String,
    val version: String,
    val sha256: String? = null,
) {
    init {
        requireVideoJobId(id, "Video model")
        require(version.isNotBlank() && version.length <= 160 && version.none(Char::isISOControl)) {
            "Video model version is invalid"
        }
        sha256?.let { requireVideoJobSha256(it, "Video model") }
    }
}

@Serializable
sealed interface VideoExecutionPolicy

@Serializable
@SerialName("local")
data class VideoLocalExecutionPolicy(
    val wallClockLimitMillis: Long,
    val memoryLimitBytes: Long,
    val diskLimitBytes: Long,
) : VideoExecutionPolicy {
    init {
        require(wallClockLimitMillis in 1_000L..86_400_000L) { "Local wall-clock limit must be 1 second..24 hours" }
        require(memoryLimitBytes > 0L) { "Local memory limit must be positive" }
        require(diskLimitBytes > 0L) { "Local disk limit must be positive" }
    }
}

@Serializable
@SerialName("hosted")
data class VideoHostedExecutionPolicy(
    val budgetId: String,
    val currency: String,
    val estimatedMaximumCostMicros: Long,
    val estimateCreatedAt: String,
    val estimateExpiresAt: String,
    val authorizedSpendCapMicros: Long,
) : VideoExecutionPolicy {
    init {
        requireVideoJobId(budgetId, "Hosted budget")
        require(CURRENCY.matches(currency)) { "Hosted budget currency must be a three-letter uppercase code" }
        require(estimatedMaximumCostMicros >= 0L) { "Hosted maximum cost estimate must not be negative" }
        require(authorizedSpendCapMicros >= 0L) { "Hosted spend cap must not be negative" }
        requireVideoJobTimestamp(estimateCreatedAt, "Hosted estimate creation")
        requireVideoJobTimestamp(estimateExpiresAt, "Hosted estimate expiration")
        require(Instant.parse(estimateExpiresAt).isAfter(Instant.parse(estimateCreatedAt))) {
            "Hosted estimate expiration must be after its creation"
        }
    }
}

@Serializable
data class VideoGenerationAttempt(
    val id: String,
    val requestId: String,
    val number: Int,
    val ownershipToken: String,
    val status: VideoGenerationAttemptStatus,
    /** Tracks the crash window independently from runtime status and cancellation intent. */
    val submissionPhase: VideoSubmissionPhase,
    val admittedAt: String,
    val providerWorkId: String? = null,
    /** Null is deliberately distinct from zero and remains unknown until the backend reports it. */
    val progressPercent: Int? = null,
    val lastObservedAt: String? = null,
    val finishedAt: String? = null,
    val retryable: Boolean = false,
    val failure: String? = null,
    val actualCostMicros: Long? = null,
    val resourceUsage: VideoGenerationResourceUsage? = null,
) {
    init {
        requireVideoJobId(id, "Video attempt")
        requireVideoJobId(requestId, "Video request")
        require(number > 0) { "Video attempt number must be positive" }
        requireVideoJobId(ownershipToken, "Video attempt ownership")
        requireVideoJobTimestamp(admittedAt, "Video attempt admission")
        require(providerWorkId == null || providerWorkId.isNotBlank() && providerWorkId.length <= 512 && providerWorkId.none(Char::isISOControl)) {
            "Provider work identity is invalid"
        }
        require(progressPercent == null || progressPercent in 0..100) { "Video progress must be 0..100 or unknown" }
        lastObservedAt?.let { requireVideoJobTimestamp(it, "Video attempt observation") }
        finishedAt?.let { requireVideoJobTimestamp(it, "Video attempt completion") }
        require(actualCostMicros == null || actualCostMicros >= 0L) { "Actual hosted cost must not be negative" }
        require(failure == null || failure.isNotBlank() && failure.length <= 2_000 && failure.none(Char::isISOControl)) {
            "Video attempt failure is invalid"
        }
        if (status.isTerminal) require(finishedAt != null) { "A terminal video attempt needs a completion time" }
        if (!status.isTerminal) require(finishedAt == null) { "A non-terminal video attempt cannot have a completion time" }
        if (submissionPhase == VideoSubmissionPhase.READY) require(
            status == VideoGenerationAttemptStatus.SUBMITTING && providerWorkId == null &&
                progressPercent == null && actualCostMicros == null && resourceUsage == null
        ) { "An unclaimed attempt cannot have backend activity" }
        if (submissionPhase == VideoSubmissionPhase.PENDING) require(!status.isTerminal) {
            "A pending submission call cannot release its admission"
        }
        if (submissionPhase == VideoSubmissionPhase.NOT_STARTED) require(status.isTerminal) {
            "A conclusive not-started submission must be terminal"
        }
    }
}

@Serializable
enum class VideoGenerationAttemptStatus(val isTerminal: Boolean) {
    SUBMITTING(false),
    SUBMISSION_UNCERTAIN(false),
    ACTIVE(false),
    CANCELLATION_REQUESTED(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true),
}

@Serializable
enum class VideoSubmissionPhase {
    /** Admission exists, but no caller has claimed permission to invoke the backend. */
    READY,
    /** Launch was claimed durably; submit may be imminent, in flight, or interrupted by a crash. */
    PENDING,
    ACKNOWLEDGED,
    UNCERTAIN,
    NOT_STARTED,
}

@Serializable
data class VideoGenerationResourceUsage(
    val wallClockMillis: Long?,
    val peakMemoryBytes: Long?,
    val diskBytes: Long?,
) {
    init {
        require(wallClockMillis == null || wallClockMillis >= 0L) { "Measured wall-clock time must not be negative" }
        require(peakMemoryBytes == null || peakMemoryBytes >= 0L) { "Measured peak memory must not be negative" }
        require(diskBytes == null || diskBytes >= 0L) { "Measured disk use must not be negative" }
    }
}

@Serializable
data class VideoGenerationOutput(
    val id: String,
    val attemptId: String,
    val backendOutputId: String,
    val createdAt: String,
    val relativePath: String? = null,
    val sha256: String? = null,
    val byteCount: Long? = null,
) {
    init {
        requireVideoJobId(id, "Video output")
        requireVideoJobId(attemptId, "Video attempt")
        require(backendOutputId.isNotBlank() && backendOutputId.length <= 512 && backendOutputId.none(Char::isISOControl)) {
            "Backend output identity is invalid"
        }
        requireVideoJobTimestamp(createdAt, "Video output creation")
        require((relativePath == null) == (sha256 == null)) { "Output path and digest must be recorded together" }
        relativePath?.let { path ->
            // Use the artifact contract itself so a completed job can always be
            // imported into the existing project representation.
            VideoArtifact(path, requireNotNull(sha256))
            require(path.split('/').none(VideoJobControlPaths::isReservedName)) {
                "Video outputs must not use job document, lock, staging or recovery paths"
            }
        }
        sha256?.let { requireVideoJobSha256(it, "Video output") }
        require(byteCount == null || byteCount >= 0L) { "Video output byte count must not be negative" }
    }
}

internal fun requireVideoJobId(value: String, label: String) {
    require(VIDEO_JOB_SAFE_ID.matches(value)) { "$label ID must be a safe stable identifier" }
}

internal fun requireVideoJobTimestamp(value: String, label: String) {
    require(runCatching { Instant.parse(value) }.isSuccess) { "$label timestamp must be an ISO-8601 instant" }
}

internal fun requireVideoJobSha256(value: String, label: String) {
    require(VIDEO_JOB_SHA_256.matches(value)) { "$label SHA-256 must be lowercase hexadecimal" }
}

private fun requireVideoPrompt(value: String) {
    require(value.isNotBlank() && value.length <= 20_000 && value.none { it == '\u0000' }) { "Video generation prompt is invalid" }
}

private fun requireVideoDependencyPins(value: List<VideoGenerationDependencyPin>) {
    require(value.map(VideoGenerationDependencyPin::id).distinct().size == value.size) {
        "Video generation dependency IDs must be unique"
    }
}

private fun requireComfyDependencies(
    pins: List<VideoGenerationDependencyPin>,
    workflow: VideoComfyWorkflowRequest?,
) {
    if (workflow == null) return
    val byId = pins.associateBy(VideoGenerationDependencyPin::id)
    val consumed = listOf(workflow.workflowDependencyId) + workflow.referenceInputs.map(VideoComfyReferenceInput::dependencyId)
    require(consumed.all { byId[it]?.ownedPath != null }) {
        "Every consumed ComfyUI workflow/reference dependency needs a digest-pinned owned path"
    }
}

private val COMFY_NODE_ID = Regex("[A-Za-z0-9._~-]{1,256}")
private val COMFY_INPUT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
private val COMFY_UPLOAD_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
private val COMFY_EXTENSION = Regex("[a-z0-9]{1,12}")

/** Persistence controls cannot become output artifacts, including filesystem aliases. */
internal object VideoJobControlPaths {
    const val DOCUMENT = "video-jobs.json"
    const val LOCK = ".video-jobs.lock"
    const val STAGING_PREFIX = ".video-jobs.save-"
    const val RECOVERY_PREFIX = ".video-jobs.recovery-"

    fun isReservedName(segment: String): Boolean {
        val name = segment.trimEnd(' ', '.')
        return name.equals(DOCUMENT, ignoreCase = true) || name.equals(LOCK, ignoreCase = true) ||
            name.startsWith(STAGING_PREFIX, ignoreCase = true) ||
            name.startsWith(RECOVERY_PREFIX, ignoreCase = true)
    }
}

const val MAX_VIDEO_GENERATION_ATTEMPTS = 3
private val VIDEO_JOB_SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val VIDEO_JOB_SHA_256 = Regex("[0-9a-f]{64}")
private val CURRENCY = Regex("[A-Z]{3}")
