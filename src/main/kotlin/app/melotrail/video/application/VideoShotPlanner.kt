package app.melotrail.video.application

import app.melotrail.video.domain.VideoBrief
import app.melotrail.video.domain.VideoDependencyPin
import app.melotrail.video.domain.VideoFootageMode
import app.melotrail.video.domain.VideoInputDependency
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoShotOverride
import app.melotrail.video.domain.VideoVersionedId
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** Creates bounded duration cards only. It does not interpret the prompt or infer a story. */
class VideoShotPlanner {
    /** Compiles the current inputs together so a stale compilation cannot be paired with a new brief or profile. */
    fun plan(
        brief: VideoBrief,
        capabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
        footageMode: VideoFootageMode = VideoFootageMode.UNIQUE,
        measurement: VideoLocalGenerationMeasurement? = null,
    ): VideoShotPlanResult {
        val compiler = VideoPromptCompiler()
        val compilation = compiler.compile(brief, capabilities, guidelineSet)
        if (!compilation.canPrepareRequests) {
            return VideoShotPlanResult.Blocked(
                listOf(VideoShotPlanProblem(
                    VideoShotPlanProblemCode.PROMPT_COMPILATION_BLOCKED,
                    "Resolve the prompt/reference capability issues before planning inference requests.",
                )),
            )
        }
        val compiledBackendPrompt = checkNotNull(compilation.backendPrompt) {
            "Inference-ready compilation must contain a backend prompt"
        }

        val uniqueCount = when (footageMode) {
            VideoFootageMode.UNIQUE -> divideRoundingUp(brief.durationSeconds, UNIQUE_SHOT_SECONDS)
            VideoFootageMode.EXPLICIT_REUSE ->
                ((brief.durationSeconds + REUSE_SECONDS_PER_UNIQUE / 2) / REUSE_SECONDS_PER_UNIQUE)
                    .coerceIn(MIN_REUSE_UNIQUE_TAKES, MAX_REUSE_UNIQUE_TAKES)
        }
        val overrideProblems = brief.shotOverrides.mapNotNull { override ->
            when {
                override.shotNumber > uniqueCount -> VideoShotPlanProblem(
                    VideoShotPlanProblemCode.UNKNOWN_SHOT_OVERRIDE,
                    "Shot override ${override.shotNumber} is outside the proposed 1..$uniqueCount unique-shot range.",
                )
                override.durationSeconds != null && override.durationSeconds !in MIN_USEFUL_SHOT_SECONDS..MAX_USEFUL_SHOT_SECONDS ->
                    VideoShotPlanProblem(
                        VideoShotPlanProblemCode.SHOT_OVERRIDE_DURATION_OUT_OF_RANGE,
                        "Shot override ${override.shotNumber} duration must be within $MIN_USEFUL_SHOT_SECONDS..$MAX_USEFUL_SHOT_SECONDS seconds.",
                    )
                else -> null
            }
        }
        if (overrideProblems.isNotEmpty()) return VideoShotPlanResult.Blocked(overrideProblems)

        val durations = when (footageMode) {
            VideoFootageMode.UNIQUE -> distributeUniqueDuration(brief.durationSeconds, uniqueCount, brief.shotOverrides)
                ?: return VideoShotPlanResult.Blocked(listOf(VideoShotPlanProblem(
                    VideoShotPlanProblemCode.SHOT_OVERRIDE_DURATION_CONFLICT,
                    "Per-shot durations cannot be combined into exactly ${brief.durationSeconds} seconds of unique footage.",
                )))
            VideoFootageMode.EXPLICIT_REUSE -> List(uniqueCount) { index ->
                brief.shotOverrides.singleOrNull { it.shotNumber == index + 1 }?.durationSeconds ?: REUSE_SHOT_SECONDS
            }
        }
        val unsupportedDurations = durations.distinct().filterNot(capabilities::supportsClipDuration)
        if (unsupportedDurations.isNotEmpty()) {
            return VideoShotPlanResult.Blocked(listOf(VideoShotPlanProblem(
                VideoShotPlanProblemCode.BACKEND_CLIP_DURATION_UNSUPPORTED,
                "Backend '${capabilities.backendId}' supports ${capabilities.minimumClipDurationSeconds}..${capabilities.maximumClipDurationSeconds}-second clips; proposed generated duration(s) ${unsupportedDurations.sorted()} are unsupported.",
            )))
        }

        val overrides = brief.shotOverrides.associateBy(VideoShotOverride::shotNumber)
        val modeDependency = VideoInputDependency("planner.footage-mode", digest(footageMode.name))
        val templateDependency = VideoInputDependency(
            "template.shot-planner",
            digest(compilation.shotPlannerVersion, UNIQUE_SHOT_SECONDS.toString(), REUSE_SHOT_SECONDS.toString()),
        )
        val requests = durations.mapIndexed { index, duration ->
            val shotNumber = index + 1
            val override = overrides[shotNumber]
            val prompt = buildString {
                append(compiledBackendPrompt)
                override?.prompt?.let {
                    append("\n\nShot override:\n")
                    append(it)
                }
            }
            val specificDependencies = listOf(
                VideoInputDependency(
                    "shot.$shotNumber.prompt-override",
                    sha256((override?.prompt ?: NO_OVERRIDE).toByteArray(UTF_8)),
                ),
                VideoInputDependency("shot.$shotNumber.duration", digest(duration.toString())),
                VideoInputDependency("shot.$shotNumber.ordinal", digest(shotNumber.toString())),
            )
            val dependencies = compilation.dependencies + modeDependency + templateDependency + specificDependencies
            VideoShotGenerationRequest(
                id = "shot-${shotNumber.toString().padStart(3, '0')}",
                shotNumber = shotNumber,
                durationSeconds = duration,
                primaryPrompt = compilation.primaryPrompt,
                backendPrompt = prompt,
                bindings = compilation.bindings.filter { it.status == VideoReferenceBindingStatus.BOUND },
                dependencies = dependencies,
                requestFingerprint = fingerprint(dependencies),
            )
        }

        val placements = mutableListOf<VideoTimelinePlacement>()
        var start = 0
        requests.forEach { request ->
            placements += VideoTimelinePlacement(
                id = "placement-${(placements.size + 1).toString().padStart(3, '0')}",
                sourceRequestId = request.id,
                startSeconds = start,
                durationSeconds = request.durationSeconds,
                sourceOffsetSeconds = 0,
                direction = VideoPlaybackDirection.FORWARD,
                repeatReview = VideoRepeatReview.NOT_A_REPEAT,
            )
            start += request.durationSeconds
        }
        if (footageMode == VideoFootageMode.EXPLICIT_REUSE) {
            var sourceIndex = 0
            while (start < brief.durationSeconds) {
                val request = requests[sourceIndex % requests.size]
                val duration = minOf(request.durationSeconds, brief.durationSeconds - start)
                placements += VideoTimelinePlacement(
                    id = "placement-${(placements.size + 1).toString().padStart(3, '0')}",
                    sourceRequestId = request.id,
                    startSeconds = start,
                    durationSeconds = duration,
                    sourceOffsetSeconds = 0,
                    direction = VideoPlaybackDirection.FORWARD,
                    repeatReview = VideoRepeatReview.REVIEW_REQUIRED,
                )
                start += duration
                sourceIndex++
            }
        }
        check(start == brief.durationSeconds) { "Shot planner failed to cover the exact requested duration" }

        val uniqueSeconds = requests.sumOf(VideoShotGenerationRequest::durationSeconds)
        val reusedSeconds = brief.durationSeconds - uniqueSeconds
        val profileFingerprint = fingerprint(compiler.generationProfileDependencies(capabilities))
        val measurementProfileFingerprint = measurement?.profile?.let {
            fingerprint(compiler.generationProfileDependencies(it))
        }
        val estimate = estimate(uniqueSeconds, measurement, profileFingerprint, measurementProfileFingerprint)
        val planDependencies = buildList {
            addAll(requests.flatMap(VideoShotGenerationRequest::dependencies).distinct())
            add(VideoInputDependency("planner.duration", digest(brief.durationSeconds.toString())))
            add(VideoInputDependency(
                "planner.placements",
                digest(*placements.flatMap { placement ->
                    listOf(
                        placement.id, placement.sourceRequestId, placement.startSeconds.toString(),
                        placement.durationSeconds.toString(), placement.sourceOffsetSeconds.toString(),
                        placement.direction.name, placement.repeatReview.name,
                    )
                }.toTypedArray()),
            ))
            measurement?.let {
                add(VideoInputDependency("estimate.measurement.${it.evidence.id}", it.evidence.sha256))
                add(VideoInputDependency(
                    "estimate.profile",
                    measurementProfileFingerprint ?: digest("measurement-profile-missing"),
                ))
                add(VideoInputDependency(
                    "estimate.observations",
                    digest(it.wallClockMillis.toString(), it.attemptedGeneratedSeconds.toString(), it.acceptedUsefulSeconds.toString()),
                ))
            }
        }
        return VideoShotPlanResult.Proposed(
            VideoShotProposal(
                durationSeconds = brief.durationSeconds,
                footageMode = footageMode,
                requests = requests,
                placements = placements,
                uniqueFootageSeconds = uniqueSeconds,
                reusedFootageSeconds = reusedSeconds,
                repeatReviewRequired = placements.any { it.repeatReview == VideoRepeatReview.REVIEW_REQUIRED },
                localEstimate = estimate,
                dependencies = planDependencies,
                proposalFingerprint = fingerprint(planDependencies),
            ),
        )
    }

    /**
     * Compares request fingerprints and marks only pending work stale. Completed take IDs are
     * returned unchanged even when their originating inputs differ from the new proposal.
     */
    fun invalidatePending(
        previous: VideoShotProposal,
        current: VideoShotProposal,
        pendingRequestIds: Set<String>,
        completedTakeIds: List<VideoVersionedId>,
    ): VideoPendingRequestInvalidation {
        val oldRequests = previous.requests.associateBy(VideoShotGenerationRequest::id)
        val newRequests = current.requests.associateBy(VideoShotGenerationRequest::id)
        require(pendingRequestIds.all(oldRequests::containsKey)) {
            "Pending request IDs must belong to the previous proposal"
        }
        val invalidated = pendingRequestIds.filter { id ->
            newRequests[id]?.requestFingerprint != oldRequests.getValue(id).requestFingerprint
        }.sorted()
        return VideoPendingRequestInvalidation(
            invalidatedPendingRequestIds = invalidated,
            unaffectedPendingRequestIds = (pendingRequestIds - invalidated.toSet()).sorted(),
            retainedCompletedTakeIds = completedTakeIds,
        )
    }

    private fun distributeUniqueDuration(
        total: Int,
        count: Int,
        overrides: List<VideoShotOverride>,
    ): List<Int>? {
        val fixed = overrides.mapNotNull { override -> override.durationSeconds?.let { override.shotNumber to it } }.toMap()
        val openCount = count - fixed.size
        val remaining = total - fixed.values.sum()
        if (openCount == 0) return if (remaining == 0) List(count) { fixed.getValue(it + 1) } else null
        if (remaining < openCount * MIN_USEFUL_SHOT_SECONDS || remaining > openCount * MAX_USEFUL_SHOT_SECONDS) return null
        val base = remaining / openCount
        val longer = remaining % openCount
        var openIndex = 0
        return List(count) { index ->
            fixed[index + 1] ?: (base + if (openIndex++ < longer) 1 else 0)
        }
    }

    private fun estimate(
        uniqueSeconds: Int,
        measurement: VideoLocalGenerationMeasurement?,
        profileFingerprint: String,
        measurementProfileFingerprint: String?,
    ): VideoLocalWorkEstimate {
        if (measurement == null || measurementProfileFingerprint != profileFingerprint) {
            return VideoLocalWorkEstimate(
                status = VideoEstimateStatus.UNKNOWN,
                estimatedWallClockMillis = null,
                measurementEvidence = null,
                reason = when {
                    measurement == null -> "No measured local timing and accepted-useful-footage evidence was supplied."
                    measurementProfileFingerprint == null -> "Supplied timing evidence has no generation profile identity."
                    else -> "Supplied timing evidence belongs to a different generation profile or output configuration."
                },
            )
        }
        val numerator = BigInteger.valueOf(measurement.wallClockMillis)
            .multiply(BigInteger.valueOf(uniqueSeconds.toLong()))
        val denominator = BigInteger.valueOf(measurement.acceptedUsefulSeconds.toLong())
        val quotientAndRemainder = numerator.divideAndRemainder(denominator)
        val roundedUp = quotientAndRemainder[0] + if (quotientAndRemainder[1].signum() == 0) BigInteger.ZERO else BigInteger.ONE
        require(roundedUp <= BigInteger.valueOf(Long.MAX_VALUE)) { "Measured local estimate exceeds supported duration" }
        return VideoLocalWorkEstimate(
            status = VideoEstimateStatus.MEASURED,
            estimatedWallClockMillis = roundedUp.toLong(),
            measurementEvidence = measurement.evidence,
            reason = "Scaled from supplied wall time and accepted useful seconds; no retry rate was invented.",
        )
    }

    companion object {
        const val MIN_USEFUL_SHOT_SECONDS = 5
        const val MAX_USEFUL_SHOT_SECONDS = 10
        const val UNIQUE_SHOT_SECONDS = 6
        const val REUSE_SHOT_SECONDS = 8
        const val MIN_REUSE_UNIQUE_TAKES = 12
        const val MAX_REUSE_UNIQUE_TAKES = 18
        private const val REUSE_SECONDS_PER_UNIQUE = 16
        private const val NO_OVERRIDE = "<no-shot-prompt-override>"
    }
}

sealed interface VideoShotPlanResult {
    data class Proposed(val proposal: VideoShotProposal) : VideoShotPlanResult
    data class Blocked(val problems: List<VideoShotPlanProblem>) : VideoShotPlanResult {
        init { require(problems.isNotEmpty()) { "A blocked shot plan requires at least one problem" } }
    }
}

enum class VideoShotPlanProblemCode {
    PROMPT_COMPILATION_BLOCKED,
    UNKNOWN_SHOT_OVERRIDE,
    SHOT_OVERRIDE_DURATION_OUT_OF_RANGE,
    SHOT_OVERRIDE_DURATION_CONFLICT,
    BACKEND_CLIP_DURATION_UNSUPPORTED,
}

data class VideoShotPlanProblem(val code: VideoShotPlanProblemCode, val message: String)

data class VideoShotProposal(
    val durationSeconds: Int,
    val footageMode: VideoFootageMode,
    val requests: List<VideoShotGenerationRequest>,
    val placements: List<VideoTimelinePlacement>,
    val uniqueFootageSeconds: Int,
    val reusedFootageSeconds: Int,
    val repeatReviewRequired: Boolean,
    val localEstimate: VideoLocalWorkEstimate,
    val dependencies: List<VideoInputDependency>,
    val proposalFingerprint: String,
) {
    init {
        require(requests.isNotEmpty()) { "A shot proposal requires generated requests" }
        require(uniqueFootageSeconds == requests.sumOf(VideoShotGenerationRequest::durationSeconds))
        require(uniqueFootageSeconds + reusedFootageSeconds == durationSeconds)
        require(placements.sumOf(VideoTimelinePlacement::durationSeconds) == durationSeconds)
        require(placements.zipWithNext().all { (left, right) -> left.startSeconds + left.durationSeconds == right.startSeconds })
        require(placements.first().startSeconds == 0)
        require(placements.all { it.direction == VideoPlaybackDirection.FORWARD })
        require(repeatReviewRequired == placements.any { it.repeatReview == VideoRepeatReview.REVIEW_REQUIRED })
    }
}

data class VideoShotGenerationRequest(
    val id: String,
    val shotNumber: Int,
    val durationSeconds: Int,
    val primaryPrompt: String,
    val backendPrompt: String,
    val bindings: List<VideoCompiledReferenceBinding>,
    val dependencies: List<VideoInputDependency>,
    val requestFingerprint: String,
)

enum class VideoPlaybackDirection { FORWARD }

enum class VideoRepeatReview { NOT_A_REPEAT, REVIEW_REQUIRED }

data class VideoTimelinePlacement(
    val id: String,
    val sourceRequestId: String,
    val startSeconds: Int,
    val durationSeconds: Int,
    val sourceOffsetSeconds: Int,
    val direction: VideoPlaybackDirection,
    val repeatReview: VideoRepeatReview,
)

data class VideoLocalGenerationMeasurement(
    val evidence: VideoDependencyPin,
    val wallClockMillis: Long,
    val attemptedGeneratedSeconds: Int,
    val acceptedUsefulSeconds: Int,
    /** Profile captured with the observations; never relabel evidence with the currently requested profile. */
    val profile: VideoPromptBackendCapabilities? = null,
) {
    init {
        require(wallClockMillis > 0L) { "Measured wall time must be positive" }
        require(attemptedGeneratedSeconds > 0) { "Measured attempted duration must be positive" }
        require(acceptedUsefulSeconds in 1..attemptedGeneratedSeconds) {
            "Measured accepted useful seconds must be positive and no greater than attempted seconds"
        }
    }
}

enum class VideoEstimateStatus { UNKNOWN, MEASURED }

data class VideoLocalWorkEstimate(
    val status: VideoEstimateStatus,
    val estimatedWallClockMillis: Long?,
    val measurementEvidence: VideoDependencyPin?,
    val reason: String,
)

data class VideoPendingRequestInvalidation(
    val invalidatedPendingRequestIds: List<String>,
    val unaffectedPendingRequestIds: List<String>,
    val retainedCompletedTakeIds: List<VideoVersionedId>,
)

private fun divideRoundingUp(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
