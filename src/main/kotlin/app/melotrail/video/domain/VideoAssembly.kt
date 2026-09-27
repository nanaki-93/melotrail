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

/** Supported schedule semantics v1: absolute frames; controls are zero outside [start,end).
 * Subject values enter/exit at zero with a one-frame linear envelope (blink pose is open
 * at the boundary); steam emits only inside the interval and existing particles may
 * decay naturally. Camera motion is a single full-scene trajectory, never a clip loop.
 * Node execution of this versioned schedule is a later integration slice.
 */
@Serializable
enum class VideoAssemblyActionKind { BLINK, BREATHING, HEAD_GESTURE, STEAM, SCENERY_TRAVEL }

@Serializable
data class VideoAssemblyAction(
    val id: String,
    val kind: VideoAssemblyActionKind,
    val range: VideoAssemblyFrameRange,
    val channel: String,
    val componentId: String,
    val capabilityId: String,
    val value: Double,
    val entryExit: VideoAssemblyActionBoundary = VideoAssemblyActionBoundary.ABSOLUTE_CLIPPED_V1,
) {
    init {
        require(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}").matches(id)) { "Action ID must be stable and safe" }
        require(channel.isNotBlank() && componentId.isNotBlank() && capabilityId.isNotBlank()) { "Action needs a channel, component and capability" }
        require(value.isFinite() && when (kind) {
            VideoAssemblyActionKind.BLINK -> value > 0 && value <= 1
            VideoAssemblyActionKind.BREATHING -> value in 0.0..4.0
            VideoAssemblyActionKind.HEAD_GESTURE -> value in 0.0..3.0
            VideoAssemblyActionKind.STEAM -> value > 0 && value <= 8
            VideoAssemblyActionKind.SCENERY_TRAVEL -> value != 0.0 && kotlin.math.abs(value) <= 16384
        }) { "Action '$id' value exceeds the supported ${kind.name} parameter bound" }
        require(if (kind == VideoAssemblyActionKind.SCENERY_TRAVEL) channel == "camera" else {
            val prefix = "${kind.name.lowercase()}:"
            channel.startsWith(prefix) && Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,159}").matches(channel.removePrefix(prefix))
        }) {
            "Action '$id' channel does not match its supported control kind"
        }
        require(kind in setOf(VideoAssemblyActionKind.STEAM, VideoAssemblyActionKind.SCENERY_TRAVEL) || range.size >= 3) {
            "Action '$id' needs three frames for deterministic zero-valued entry and exit"
        }
    }

    /** Absolute-frame gate; subject controls reach zero on both boundary frames.
     * Steam uses this only for new births; previously born particles may decay after exit.
     * Scenery uses its full-range camera trajectory, not an on/off gesture envelope.
     */
    fun activationAt(frame: Long): Double {
        if (frame < range.start || frame >= range.endExclusive) return 0.0
        if (kind == VideoAssemblyActionKind.STEAM || kind == VideoAssemblyActionKind.SCENERY_TRAVEL) return 1.0
        return minOf(1L, frame - range.start, range.endExclusive - 1 - frame).toDouble()
    }
}

@Serializable
enum class VideoAssemblyActionBoundary { ABSOLUTE_CLIPPED_V1 }

/** Versioned immutable proposal; publication and execution binding follow in later slices. */
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
    @SerialName("actions") private val storedActions: List<VideoAssemblyAction>,
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
    val actions: List<VideoAssemblyAction> get() = Collections.unmodifiableList(storedActions)

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
        require(actions.size <= 256 && actions.map { it.id }.distinct().size == actions.size) {
            "Assembly action IDs must be unique and limited to 256"
        }
        actions.forEach { action ->
            require(action.range.endExclusive <= totalFrames) { "Action '${action.id}' exceeds frame $totalFrames" }
            require(action.kind != VideoAssemblyActionKind.SCENERY_TRAVEL ||
                action.range == VideoAssemblyFrameRange(0, totalFrames)) {
                "Scenery action '${action.id}' must cover the complete camera trajectory"
            }
        }
        val subjects = actions.filter { it.kind in setOf(VideoAssemblyActionKind.BLINK,
            VideoAssemblyActionKind.BREATHING, VideoAssemblyActionKind.HEAD_GESTURE) }
        subjects.forEachIndexed { index, a ->
            subjects.drop(index + 1).forEach { b ->
                require(a.channel.substringAfter(':') == b.channel.substringAfter(':') ||
                    a.range.endExclusive <= b.range.start || b.range.endExclusive <= a.range.start) {
                    "Subject actions '${a.id}' and '${b.id}' overlap on independent subjects; schedule separately"
                }
            }
        }
        actions.groupBy { it.channel }.forEach { (channel, group) ->
            group.sortedBy { it.range.start }.zipWithNext().forEach { (a, b) ->
                require(a.range.endExclusive <= b.range.start) {
                    "Channel '$channel' actions '${a.id}' and '${b.id}' overlap; schedule disjoint ranges"
                }
            }
        }
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
            actions: List<VideoAssemblyAction> = emptyList(),
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
            ArrayList(chunks), ArrayList(actions), schemaVersion, plannerVersion, width, height, pixelAspectNumerator,
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
        actions: List<VideoAssemblyAction> = this.actions,
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
        durationSeconds, seed, requestedSupportFrames, chunks, actions, schemaVersion, plannerVersion, width, height,
        pixelAspectNumerator, pixelAspectDenominator, framesPerSecond, frameZero)

    private fun values(): List<Any?> = listOf(id, projectId, preparedSceneId, preparedSceneArtifact,
        finishedReferenceId, finishedReferenceArtifact, primaryText, actionText, cameraText, motionText, styleText,
        guidelineTexts, durationSeconds, seed, requestedSupportFrames, chunks, actions, schemaVersion, plannerVersion,
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
