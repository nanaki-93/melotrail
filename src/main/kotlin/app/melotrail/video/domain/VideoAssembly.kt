package app.melotrail.video.domain

import java.util.Collections
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A half-open interval on the shared, zero-based 30 fps clock. Never a seconds offset. */
@Serializable
data class VideoAssemblyFrameRange(val start: Long, val endExclusive: Long) {
    init {
        require(start >= 0 && endExclusive > start && endExclusive <= MAX_SAFE_FRAME_INTEGER) {
            "Frame range [$start, $endExclusive) must be nonempty, nonnegative and JavaScript-safe"
        }
    }

    val size: Long get() = endExclusive - start
}

/** Render support is decoded but NOT delivered; edges clip to the actual scene, not a held frame. */
@Serializable
enum class VideoAssemblyEdgePolicy { CLIP_TO_SCENE }

@Serializable
data class VideoAssemblyChunk(
    val output: VideoAssemblyFrameRange,
    val render: VideoAssemblyFrameRange,
    val supportBefore: Int,
    val supportAfter: Int,
    val edgePolicy: VideoAssemblyEdgePolicy = VideoAssemblyEdgePolicy.CLIP_TO_SCENE,
) {
    init {
        require(supportBefore >= 0 && supportAfter >= 0) { "Chunk support counts must be nonnegative" }
        require(render.start <= output.start && render.endExclusive >= output.endExclusive && render.size <= MAX_RENDER_FRAMES) {
            "Chunk render range must contain output and include at most $MAX_RENDER_FRAMES frames including support"
        }
        require(output.start - render.start == supportBefore.toLong() &&
            render.endExclusive - output.endExclusive == supportAfter.toLong()) {
            "Chunk support counts must equal the render/output range differences"
        }
    }
}

/** Versioned immutable proposal; publication and action/trajectory binding follow in later slices. */
@Serializable
class VideoAssembly private constructor(
    val id: VideoVersionedId,
    val projectId: String,
    val preparedSceneId: VideoVersionedId,
    val preparedSceneArtifact: VideoArtifact,
    val finishedReferenceId: VideoVersionedId,
    val finishedReferenceArtifact: VideoArtifact,
    val primaryText: String,
    val actionText: String?,
    val cameraText: String? = null,
    val motionText: String? = null,
    val styleText: String? = null,
    @SerialName("guidelineTexts") private val storedGuidelineTexts: List<String>,
    val durationSeconds: Long,
    val seed: Long,
    /** Requested support per side; effective support is clipped at both scene edges. */
    val requestedSupportFrames: Int,
    @SerialName("chunks") private val storedChunks: List<VideoAssemblyChunk>,
    val schemaVersion: Int = CURRENT_ASSEMBLY_SCHEMA,
    val plannerVersion: Int = CURRENT_ASSEMBLY_PLANNER,
    val width: Int = 1920,
    val height: Int = 1080,
    val pixelAspectNumerator: Int = 1,
    val pixelAspectDenominator: Int = 1,
    val framesPerSecond: Int = 30,
    val frameZero: Long = 0,
) {
    // Own both collections: a caller must not be able to change a validated partition or its text after construction.
    val guidelineTexts: List<String> get() = Collections.unmodifiableList(storedGuidelineTexts)
    val chunks: List<VideoAssemblyChunk> get() = Collections.unmodifiableList(storedChunks)

    val totalFrames: Long get() = Math.multiplyExact(durationSeconds, framesPerSecond.toLong())

    init {
        require(schemaVersion == CURRENT_ASSEMBLY_SCHEMA && plannerVersion == CURRENT_ASSEMBLY_PLANNER) {
            "Unsupported assembly schema/planner version; create a plan with the current planner"
        }
        require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}").matches(projectId)) { "Assembly project ID must be a safe stable ID" }
        require(primaryText.isNotBlank() && primaryText.length <= 20_000 && primaryText.none(::forbiddenAssemblyTextControl)) {
            "Assembly primary text must be nonblank, at most 20000 characters and free of binary controls"
        }
        listOf("Action" to actionText, "Camera" to cameraText, "Motion" to motionText, "Style" to styleText).forEach { (label, text) ->
            require(text == null || validAssemblyText(text)) { "Assembly $label text is invalid" }
        }
        require(guidelineTexts.size <= 32 && guidelineTexts.all(::validAssemblyText)) {
            "Assembly guideline text must contain at most 32 valid verbatim entries"
        }
        require(width == 1920 && height == 1080 && pixelAspectNumerator == 1 && pixelAspectDenominator == 1 &&
            framesPerSecond == 30 && frameZero == 0L) {
            "Assembly delivery must be 1920x1080 square-pixel 30 fps with frame-zero origin"
        }
        require(durationSeconds in 180L..300L) { "Assembly duration must be an integer 180..300 seconds" }
        require(seed in 0..MAX_SAFE_FRAME_INTEGER) { "Assembly seed must be a nonnegative JavaScript-safe integer" }
        require(requestedSupportFrames in 0..149) { "Requested support must fit within a 300-frame render invocation" }
        require(chunks.isNotEmpty() && chunks.size <= 9_000) { "Assembly needs a bounded output partition" }
        var next = 0L
        chunks.forEachIndexed { index, chunk ->
            require(chunk.output.start == next && chunk.output.endExclusive <= totalFrames) {
                "Chunk $index output must start at frame $next and stay within [0, $totalFrames); repair the gap/overlap"
            }
            require(chunk.supportBefore.toLong() == minOf(requestedSupportFrames.toLong(), chunk.output.start) &&
                chunk.supportAfter.toLong() == minOf(requestedSupportFrames.toLong(), totalFrames - chunk.output.endExclusive)) {
                "Chunk $index support must use the declared amount, clipped only at scene edges"
            }
            next = chunk.output.endExclusive
        }
        require(next == totalFrames) { "Output partition ends at $next instead of $totalFrames; supply the missing frames" }
    }

    companion object {
        operator fun invoke(
            id: VideoVersionedId,
            projectId: String,
            preparedSceneId: VideoVersionedId,
            preparedSceneArtifact: VideoArtifact,
            finishedReferenceId: VideoVersionedId,
            finishedReferenceArtifact: VideoArtifact,
            primaryText: String,
            actionText: String?,
            cameraText: String? = null,
            motionText: String? = null,
            styleText: String? = null,
            guidelineTexts: List<String> = emptyList(),
            durationSeconds: Long,
            seed: Long,
            requestedSupportFrames: Int,
            chunks: List<VideoAssemblyChunk>,
            schemaVersion: Int = CURRENT_ASSEMBLY_SCHEMA,
            plannerVersion: Int = CURRENT_ASSEMBLY_PLANNER,
            width: Int = 1920,
            height: Int = 1080,
            pixelAspectNumerator: Int = 1,
            pixelAspectDenominator: Int = 1,
            framesPerSecond: Int = 30,
            frameZero: Long = 0,
        ): VideoAssembly = VideoAssembly(id, projectId, preparedSceneId, preparedSceneArtifact,
            finishedReferenceId, finishedReferenceArtifact, primaryText, actionText, cameraText, motionText,
            styleText, ArrayList(guidelineTexts), durationSeconds, seed, requestedSupportFrames,
            ArrayList(chunks), schemaVersion, plannerVersion, width, height, pixelAspectNumerator,
            pixelAspectDenominator, framesPerSecond, frameZero)
    }

    fun copy(
        id: VideoVersionedId = this.id,
        projectId: String = this.projectId,
        preparedSceneId: VideoVersionedId = this.preparedSceneId,
        preparedSceneArtifact: VideoArtifact = this.preparedSceneArtifact,
        finishedReferenceId: VideoVersionedId = this.finishedReferenceId,
        finishedReferenceArtifact: VideoArtifact = this.finishedReferenceArtifact,
        primaryText: String = this.primaryText,
        actionText: String? = this.actionText,
        cameraText: String? = this.cameraText,
        motionText: String? = this.motionText,
        styleText: String? = this.styleText,
        guidelineTexts: List<String> = this.guidelineTexts,
        durationSeconds: Long = this.durationSeconds,
        seed: Long = this.seed,
        requestedSupportFrames: Int = this.requestedSupportFrames,
        chunks: List<VideoAssemblyChunk> = this.chunks,
        schemaVersion: Int = this.schemaVersion,
        plannerVersion: Int = this.plannerVersion,
        width: Int = this.width,
        height: Int = this.height,
        pixelAspectNumerator: Int = this.pixelAspectNumerator,
        pixelAspectDenominator: Int = this.pixelAspectDenominator,
        framesPerSecond: Int = this.framesPerSecond,
        frameZero: Long = this.frameZero,
    ) = Companion.invoke(id, projectId, preparedSceneId, preparedSceneArtifact, finishedReferenceId,
        finishedReferenceArtifact, primaryText, actionText, cameraText, motionText, styleText, guidelineTexts,
        durationSeconds, seed, requestedSupportFrames, chunks, schemaVersion, plannerVersion, width, height,
        pixelAspectNumerator, pixelAspectDenominator, framesPerSecond, frameZero)

    private fun values(): List<Any?> = listOf(id, projectId, preparedSceneId, preparedSceneArtifact,
        finishedReferenceId, finishedReferenceArtifact, primaryText, actionText, cameraText, motionText, styleText,
        guidelineTexts, durationSeconds, seed, requestedSupportFrames, chunks, schemaVersion, plannerVersion,
        width, height, pixelAspectNumerator, pixelAspectDenominator, framesPerSecond, frameZero)

    override fun equals(other: Any?): Boolean = this === other || other is VideoAssembly && values() == other.values()
    override fun hashCode(): Int = values().hashCode()
    override fun toString(): String = "VideoAssembly(${values()})"
}

const val MAX_SAFE_FRAME_INTEGER: Long = 9_007_199_254_740_991L
const val MAX_RENDER_FRAMES: Long = 300L
const val CURRENT_ASSEMBLY_SCHEMA = 1
const val CURRENT_ASSEMBLY_PLANNER = 1

private fun validAssemblyText(text: String): Boolean =
    text.isNotBlank() && text.length <= 20_000 && text.none(::forbiddenAssemblyTextControl)

private fun forbiddenAssemblyTextControl(c: Char): Boolean = c.isISOControl() && c != '\n' && c != '\r' && c != '\t'
