package app.melotrail.video.application

import app.melotrail.video.domain.MAX_RENDER_FRAMES
import app.melotrail.video.domain.MAX_SAFE_FRAME_INTEGER
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssembly
import app.melotrail.video.domain.VideoAssemblyChunk
import app.melotrail.video.domain.VideoAssemblyFrameRange
import app.melotrail.video.domain.VideoVersionedId

/** Inputs are values only: planning never opens source artwork or starts a media process. */
data class VideoAssemblyPlanningRequest(
    val id: VideoVersionedId,
    val projectId: String,
    val preparedSceneId: VideoVersionedId,
    val preparedSceneArtifact: VideoArtifact,
    val finishedReferenceId: VideoVersionedId,
    val finishedReferenceArtifact: VideoArtifact,
    val primaryText: String,
    val actionText: String? = null,
    val cameraText: String? = null,
    val motionText: String? = null,
    val styleText: String? = null,
    val guidelineTexts: List<String> = emptyList(),
    val durationSeconds: Long = 240,
    val seed: Long,
    val supportFramesPerSide: Int = 0,
    val maximumOutputFramesPerChunk: Int = 300,
)

enum class VideoAssemblyFindingCode { DURATION, OVERFLOW, UNSAFE_INTEGER, SEED, SUPPORT, CHUNK_SIZE, MALFORMED_PLAN }

data class VideoAssemblyFinding(val code: VideoAssemblyFindingCode, val explanation: String, val remedy: String)

sealed interface VideoAssemblyPlanResult {
    data class Proposed(val assembly: VideoAssembly) : VideoAssemblyPlanResult
    data class Blocked(val findings: List<VideoAssemblyFinding>) : VideoAssemblyPlanResult {
        init { require(findings.isNotEmpty()) { "Blocked assembly needs an actionable finding" } }
    }
}

/** Partitions the full scene, not short clips to be looped or frozen to fill. */
class VideoAssemblyPlanner {
    fun plan(request: VideoAssemblyPlanningRequest): VideoAssemblyPlanResult {
        val frames = try {
            Math.multiplyExact(request.durationSeconds, 30L)
        } catch (_: ArithmeticException) {
            return blocked(VideoAssemblyFindingCode.OVERFLOW, "Duration times 30 fps overflows a 64-bit frame count.",
                "Choose an integer duration between 180 and 300 seconds.")
        }
        if (frames !in 0..MAX_SAFE_FRAME_INTEGER) {
            return blocked(VideoAssemblyFindingCode.UNSAFE_INTEGER, "Frame count $frames is not a JavaScript-safe nonnegative integer.",
                "Choose a duration between 180 and 300 seconds before rendering.")
        }
        if (request.durationSeconds !in 180L..300L) {
            return blocked(VideoAssemblyFindingCode.DURATION, "Duration ${request.durationSeconds} is outside 180..300 seconds.",
                "Choose an integer duration between 180 and 300 seconds.")
        }
        if (request.seed !in 0..MAX_SAFE_FRAME_INTEGER) {
            return blocked(VideoAssemblyFindingCode.SEED, "Seed ${request.seed} is not a JavaScript-safe nonnegative integer.",
                "Choose a seed in 0..$MAX_SAFE_FRAME_INTEGER.")
        }
        val support = request.supportFramesPerSide
        if (support !in 0..149) {
            return blocked(VideoAssemblyFindingCode.SUPPORT, "Support $support per side cannot fit into 300 render frames.",
                "Declare zero support for stateless motion or at most 149 frames per side.")
        }
        val outputLimit = request.maximumOutputFramesPerChunk
        if (outputLimit !in 1..MAX_RENDER_FRAMES.toInt()) {
            return blocked(VideoAssemblyFindingCode.CHUNK_SIZE,
                "Output chunk limit $outputLimit is outside 1..300 frames.",
                "Choose a positive output limit of at most 300 frames; render support is reserved within that ceiling.")
        }
        val outputSize = minOf(outputLimit, MAX_RENDER_FRAMES.toInt() - 2 * support)
        val chunks = buildList {
            var start = 0L
            while (start < frames) {
                val end = Math.addExact(start, outputSize.toLong()).coerceAtMost(frames)
                val before = minOf(support.toLong(), start).toInt()
                val after = minOf(support.toLong(), frames - end).toInt()
                add(VideoAssemblyChunk(
                    output = VideoAssemblyFrameRange(start, end),
                    render = VideoAssemblyFrameRange(start - before, end + after),
                    supportBefore = before,
                    supportAfter = after,
                ))
                start = end
            }
        }
        return try {
            VideoAssemblyPlanResult.Proposed(VideoAssembly(
                id = request.id,
                projectId = request.projectId,
                preparedSceneId = request.preparedSceneId,
                preparedSceneArtifact = request.preparedSceneArtifact,
                finishedReferenceId = request.finishedReferenceId,
                finishedReferenceArtifact = request.finishedReferenceArtifact,
                primaryText = request.primaryText,
                actionText = request.actionText,
                cameraText = request.cameraText,
                motionText = request.motionText,
                styleText = request.styleText,
                guidelineTexts = request.guidelineTexts.toList(),
                durationSeconds = request.durationSeconds,
                seed = request.seed,
                requestedSupportFrames = support,
                chunks = chunks,
            ))
        } catch (invalid: IllegalArgumentException) {
            blocked(VideoAssemblyFindingCode.MALFORMED_PLAN, invalid.message ?: "Invalid assembly identity or text.",
                "Correct the named assembly input and plan again; no media was opened.")
        }
    }

    private fun blocked(code: VideoAssemblyFindingCode, explanation: String, remedy: String): VideoAssemblyPlanResult.Blocked =
        VideoAssemblyPlanResult.Blocked(listOf(VideoAssemblyFinding(code, explanation, remedy)))
}
