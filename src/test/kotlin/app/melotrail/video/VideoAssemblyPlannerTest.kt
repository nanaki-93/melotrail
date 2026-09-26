package app.melotrail.video

import app.melotrail.video.application.VideoAssemblyFindingCode
import app.melotrail.video.application.VideoAssemblyPlanResult
import app.melotrail.video.application.VideoAssemblyPlanner
import app.melotrail.video.application.VideoAssemblyPlanningRequest
import app.melotrail.video.domain.MAX_RENDER_FRAMES
import app.melotrail.video.domain.MAX_SAFE_FRAME_INTEGER
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssemblyChunk
import app.melotrail.video.domain.VideoAssemblyFrameRange
import app.melotrail.video.domain.VideoVersionedId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class VideoAssemblyPlannerTest {
    private val planner = VideoAssemblyPlanner()
    private val input = VideoAssemblyPlanningRequest(
        id = VideoVersionedId("assembly-1", 1), projectId = "owned-project",
        preparedSceneId = VideoVersionedId("prepared-1", 2),
        preparedSceneArtifact = VideoArtifact("prepared/prepared.json", "a".repeat(64)),
        finishedReferenceId = VideoVersionedId("finished-1", 1),
        finishedReferenceArtifact = VideoArtifact("references/finished.png", "b".repeat(64)),
        primaryText = "  Keep the drawn silhouette.\r\nMove gently.  ",
        actionText = "  Blink once.\n  ", cameraText = "  slow drift\r\n ",
        motionText = "  soft motion  ", styleText = "  preserve ink  ",
        guidelineTexts = listOf("  Retain the linework.\n"), durationSeconds = 240, seed = 73,
    )

    @Test
    fun `every supported integer duration partitions its exact frames once at zero support`() {
        for (seconds in 180L..300L) {
            val assembly = proposed(input.copy(durationSeconds = seconds))
            assertEquals(seconds * 30L, assembly.totalFrames)
            assertEquals(0L, assembly.frameZero)
            assertEquals(30, assembly.framesPerSecond)
            assertEquals(1920 to 1080, assembly.width to assembly.height)
            assertEquals(1 to 1, assembly.pixelAspectNumerator to assembly.pixelAspectDenominator)
            assertEquals(0, assembly.requestedSupportFrames)
            assertEquals(0L, assembly.chunks.first().output.start)
            assertEquals(assembly.totalFrames, assembly.chunks.last().output.endExclusive)
            assertEquals(assembly.totalFrames, assembly.chunks.sumOf { it.output.size })
            assembly.chunks.forEach { chunk ->
                assertEquals(chunk.output, chunk.render)
                assertEquals(0, chunk.supportBefore + chunk.supportAfter)
                assertTrue(chunk.render.size <= MAX_RENDER_FRAMES)
            }
            assertTrue(assembly.chunks.zipWithNext().all { (a, b) -> a.output.endExclusive == b.output.start })
        }
        assertEquals(5400L, proposed(input.copy(durationSeconds = 180)).totalFrames)
        assertEquals(7200L, proposed(input).totalFrames)
        assertEquals(9000L, proposed(input.copy(durationSeconds = 300)).totalFrames)
    }

    @Test
    fun `support counts are clipped at scene edges and included in the invocation ceiling not delivered`() {
        val assembly = proposed(input.copy(durationSeconds = 181, supportFramesPerSide = 8))
        assertEquals(284L, assembly.chunks.first().output.size)
        val first = assembly.chunks.first()
        val second = assembly.chunks[1]
        val last = assembly.chunks.last()
        assertEquals(0, first.supportBefore)
        assertEquals(8, first.supportAfter)
        assertEquals(8, second.supportBefore)
        assertEquals(8, second.supportAfter)
        assertEquals(0, last.supportAfter)
        assertEquals(assembly.totalFrames, assembly.chunks.sumOf { it.output.size })
        assertTrue(assembly.chunks.all { it.render.size <= 300 && it.render.start >= 0 &&
            it.render.endExclusive <= assembly.totalFrames })
        assertEquals(first.output.endExclusive, second.output.start)
        assertEquals(first.output.endExclusive + 8, first.render.endExclusive)
        assertEquals(second.output.start - 8, second.render.start)
    }

    @Test
    fun `same input replays with exact authored text and versioned pins`() {
        val a = proposed(input)
        assertEquals(a, proposed(input))
        assertEquals(input.id, a.id)
        assertEquals(input.projectId, a.projectId)
        assertEquals(input.preparedSceneId, a.preparedSceneId)
        assertEquals(input.preparedSceneArtifact, a.preparedSceneArtifact)
        assertEquals(input.finishedReferenceArtifact, a.finishedReferenceArtifact)
        assertEquals(input.primaryText, a.primaryText)
        assertEquals(input.actionText, a.actionText)
        assertEquals(input.cameraText, a.cameraText)
        assertEquals(input.motionText, a.motionText)
        assertEquals(input.styleText, a.styleText)
        assertEquals(input.guidelineTexts, a.guidelineTexts)
        assertEquals(73, a.seed)
        assertEquals(1, a.schemaVersion)
        assertEquals(1, a.plannerVersion)
        assertEquals(a, Json.decodeFromString<app.melotrail.video.domain.VideoAssembly>(Json.encodeToString(a)))
    }

    @Test
    fun `unsupported duration overflow unsafe integer and invalid chunk settings reject before IO`() {
        listOf(
            input.copy(durationSeconds = 179) to VideoAssemblyFindingCode.DURATION,
            input.copy(durationSeconds = 301) to VideoAssemblyFindingCode.DURATION,
            input.copy(durationSeconds = Long.MAX_VALUE) to VideoAssemblyFindingCode.OVERFLOW,
            input.copy(durationSeconds = MAX_SAFE_FRAME_INTEGER / 30 + 1) to VideoAssemblyFindingCode.UNSAFE_INTEGER,
            input.copy(seed = MAX_SAFE_FRAME_INTEGER + 1) to VideoAssemblyFindingCode.SEED,
            input.copy(seed = -1) to VideoAssemblyFindingCode.SEED,
            input.copy(supportFramesPerSide = -1) to VideoAssemblyFindingCode.SUPPORT,
            input.copy(supportFramesPerSide = 150) to VideoAssemblyFindingCode.SUPPORT,
            input.copy(maximumOutputFramesPerChunk = 301) to VideoAssemblyFindingCode.CHUNK_SIZE,
            input.copy(maximumOutputFramesPerChunk = 0) to VideoAssemblyFindingCode.CHUNK_SIZE,
            input.copy(projectId = "../escape") to VideoAssemblyFindingCode.MALFORMED_PLAN,
            input.copy(guidelineTexts = listOf("\u0000")) to VideoAssemblyFindingCode.MALFORMED_PLAN,
        ).forEach { (request, code) ->
            val finding = assertIs<VideoAssemblyPlanResult.Blocked>(planner.plan(request)).findings.single()
            assertEquals(code, finding.code)
            assertTrue(finding.explanation.isNotBlank() && finding.remedy.isNotBlank())
        }
    }

    @Test
    fun `validated assembly snapshots caller lists and cannot be mutated through exposed lists`() {
        val baseline = proposed(input)
        val chunks = baseline.chunks.toMutableList()
        val guidelines = mutableListOf("  Retain the linework.\n")
        val assembly = baseline.copy(chunks = chunks, guidelineTexts = guidelines)
        val originalJson = Json.encodeToString(assembly)

        // Removing an output chunk after validation previously produced a serializable hole.
        chunks.removeAt(chunks.lastIndex)
        guidelines[0] = "Different advice"
        assertEquals(baseline, assembly)
        assertEquals(originalJson, Json.encodeToString(assembly))
        assertEquals(baseline.totalFrames, assembly.chunks.last().output.endExclusive)
        val reopened = Json.decodeFromString<app.melotrail.video.domain.VideoAssembly>(originalJson)
        assertEquals(baseline, reopened)
        assertFailsWith<UnsupportedOperationException> {
            (reopened.chunks as MutableList).clear()
        }
        assertEquals(originalJson, Json.encodeToString(reopened))
        assertFailsWith<UnsupportedOperationException> {
            (assembly.chunks as MutableList).removeAt(assembly.chunks.lastIndex)
        }
        assertFailsWith<UnsupportedOperationException> {
            (assembly.guidelineTexts as MutableList)[0] = "Changed"
        }
    }

    @Test
    fun `forged partitions overlaps holes support and delivery settings fail closed`() {
        val valid = proposed(input.copy(supportFramesPerSide = 8, maximumOutputFramesPerChunk = 284))
        val first = valid.chunks.first()
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.drop(1))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.dropLast(1))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = listOf(first, first) + valid.chunks.drop(2))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(chunks = valid.chunks.toMutableList().also {
                it[1] = it[1].copy(output = VideoAssemblyFrameRange(285, it[1].output.endExclusive))
            })
        }
        assertFailsWith<IllegalArgumentException> { valid.copy(width = 1280) }
        assertFailsWith<IllegalArgumentException> { valid.copy(frameZero = 1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(schemaVersion = 2) }
        assertFailsWith<IllegalArgumentException> { valid.copy(seed = MAX_SAFE_FRAME_INTEGER + 1) }
        assertFailsWith<IllegalArgumentException> { valid.copy(requestedSupportFrames = 0) }
        assertFailsWith<IllegalArgumentException> {
            VideoAssemblyChunk(VideoAssemblyFrameRange(0, 300), VideoAssemblyFrameRange(0, 301), 0, 1)
        }
        assertFailsWith<IllegalArgumentException> { VideoAssemblyFrameRange(0, MAX_SAFE_FRAME_INTEGER + 1) }
    }

    private fun proposed(request: VideoAssemblyPlanningRequest) =
        assertIs<VideoAssemblyPlanResult.Proposed>(planner.plan(request)).assembly
}
