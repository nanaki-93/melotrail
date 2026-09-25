package app.melotrail.video

import app.melotrail.video.adapter.VideoAtomicWriteObserver
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.application.VideoProjectSaveException
import app.melotrail.video.application.VideoProjectSession
import app.melotrail.video.application.VideoTakeReview
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeMeasurementRecord
import app.melotrail.video.domain.VideoTakeProvenanceRecord
import app.melotrail.video.domain.VideoTakeRationalRecord
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoTakeReviewDecision
import app.melotrail.video.domain.VideoTakeReviewEvent
import app.melotrail.video.domain.VideoTakeReviewStatus
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoTakeReviewTest {
    @TempDir lateinit var root: Path
    private val takeId = VideoVersionedId("take", 1)
    private val clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC)
    private fun store(observer: VideoAtomicWriteObserver = VideoAtomicWriteObserver.NONE) =
        VideoProjectStore(listOf(root.resolve("midi")), observer)
    private val projectRoot get() = root.resolve("video")
    private val document get() = projectRoot.resolve(VideoProjectStore.PROJECT_FILE)

    private fun fixture(selected: Boolean = false): VideoProjectSession {
        val storage = store()
        val original = storage.create(projectRoot, VideoProject("video-1", "Video", "2026-09-13T00:00:00Z"))
        val path = projectRoot.resolve("takes/take.mp4")
        Files.createDirectories(path.parent)
        Files.writeString(path, "owned synthetic fixture")
        val digest = sha(path)
        val artifact = VideoArtifact("takes/take.mp4", digest)
        val timing = VideoTakeRationalRecord(1, 1)
        val measurement = VideoTakeMeasurementRecord(digest, Files.size(path), "h264", 100, 60,
            timing, VideoTakeRationalRecord(30, 1), 30, VideoTakeRationalRecord(1, 30), 0, 30, 1, 0, 0)
        val take = VideoTakeRecord(takeId, artifact, null, "2026-09-13T00:01:00Z",
            measurement, measurement, "NONE", VideoTakeProvenanceRecord("video-1", "request", "attempt",
                "output", "comfyui-local", "a".repeat(64), "b".repeat(64), null, null, null,
                listOf(VideoGenerationDependencyPin("source", "c".repeat(64)))))
        return VideoProjectSession(projectRoot, storage.save(projectRoot, 0,
            original.copy(takeVersions = listOf(take), selectedTakeIds = if (selected) listOf(takeId) else emptyList(), revision = 1)))
    }

    private fun reviewer(storage: VideoProjectStore = store(), id: () -> String = { "event-${java.util.UUID.randomUUID()}" }) =
        VideoTakeReview(storage, clock, id)

    @Test fun `multiple decisions persist in append order and never change selection`() {
        var session = fixture()
        val storage = store()
        assertEquals(VideoTakeReviewStatus.UNREVIEWED, session.project.reviewStatus(takeId))
        val first = reviewer(storage, { "event-1" }).review(session, 1, takeId,
            VideoTakeReviewDecision.APPROVED, "synthetic-test-reviewer", "test only")
        session = first.session
        assertEquals(VideoTakeReviewStatus.APPROVED, first.status)
        assertTrue(session.project.selectedTakeIds.isEmpty())
        val second = reviewer(storage, { "event-2" }).review(session, 2, takeId,
            VideoTakeReviewDecision.REJECTED, "synthetic-test-reviewer", null)
        val reopened = store().open(projectRoot)
        assertEquals(listOf("event-1", "event-2"), reopened.takeReviewEvents.map { it.id })
        assertEquals("2026-09-13T12:00:00Z", reopened.takeReviewEvents.single { it.id == "event-1" }.createdAt)
        assertEquals(VideoTakeReviewStatus.REJECTED, reopened.reviewStatus(takeId))
        assertEquals(second.session.project, reopened)
        assertEquals(session.project.takeVersions, reopened.takeVersions)
        assertTrue(reopened.selectedTakeIds.isEmpty())
    }

    @Test fun `stale revision invalid take and malformed event leave bytes unchanged`() {
        val initial = fixture()
        val storage = store()
        val before = Files.readAllBytes(document)
        val service = reviewer(storage, { "event-1" })
        assertFailsWith<IllegalArgumentException> {
            service.review(initial, 1, VideoVersionedId("absent", 1), VideoTakeReviewDecision.APPROVED, "test", null)
        }
        listOf(" ", "x".repeat(121)).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                service.review(initial, 1, takeId, VideoTakeReviewDecision.APPROVED, invalid, null)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            service.review(initial, 1, takeId, VideoTakeReviewDecision.APPROVED, "test", "n".repeat(1001))
        }
        assertFailsWith<IllegalArgumentException> {
            reviewer(storage, { "unsafe/id" }).review(initial, 1, takeId, VideoTakeReviewDecision.APPROVED, "test", null)
        }
        assertContentEquals(before, Files.readAllBytes(document))
        val updated = service.review(initial, 1, takeId, VideoTakeReviewDecision.APPROVED, "synthetic-test-reviewer", null)
        val after = Files.readAllBytes(document)
        assertFailsWith<VideoProjectConcurrencyException> {
            service.review(initial, 1, takeId, VideoTakeReviewDecision.REJECTED, "test", null)
        }
        assertFailsWith<IllegalArgumentException> {
            service.review(initial, 0, takeId, VideoTakeReviewDecision.REJECTED, "test", null)
        }
        assertContentEquals(after, Files.readAllBytes(document))
        assertEquals(updated.session.project, storage.open(projectRoot))
    }

    @Test fun `direct saves cannot forge malformed references duplicate IDs or rewrite history`() {
        val session = fixture()
        val storage = store()
        val first = reviewer(storage, { "event-1" }).review(session, 1, takeId,
            VideoTakeReviewDecision.APPROVED, "synthetic-test-reviewer", null).session.project
        val before = Files.readAllBytes(document)
        val event = first.takeReviewEvents.single()
        val missing = event.copy(id = "event-2", takeId = VideoVersionedId("missing", 1))
        assertFailsWith<IllegalArgumentException> { first.copy(takeReviewEvents = first.takeReviewEvents + missing) }
        assertFailsWith<IllegalArgumentException> { first.copy(takeReviewEvents = first.takeReviewEvents + event) }
        assertFailsWith<IllegalArgumentException> { VideoTakeReviewEvent("bad/id", takeId, VideoTakeReviewDecision.REJECTED, "test", event.createdAt) }
        assertFailsWith<IllegalArgumentException> { VideoTakeReviewEvent("ok", takeId, VideoTakeReviewDecision.REJECTED, "test", "not-a-time") }
        listOf(emptyList(), listOf(event.copy(note = "changed"))).forEach { rewritten ->
            assertFailsWith<IllegalArgumentException> {
                storage.save(projectRoot, first.revision, first.copy(takeReviewEvents = rewritten, revision = first.revision + 1))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            reviewer(storage, { "event-1" }).review(VideoProjectSession(projectRoot, first), first.revision,
                takeId, VideoTakeReviewDecision.REJECTED, "test", null)
        }
        assertContentEquals(before, Files.readAllBytes(document))
        assertEquals(first, storage.open(projectRoot))
    }

    @Test fun `selected take must be deselected before rejection including direct saves`() {
        val session = fixture(selected = true)
        val storage = store()
        val before = Files.readAllBytes(document)
        assertFailsWith<IllegalArgumentException> {
            reviewer(storage).review(session, 1, takeId, VideoTakeReviewDecision.REJECTED, "test", null)
        }
        assertFailsWith<IllegalArgumentException> {
            session.project.copy(takeReviewEvents = listOf(VideoTakeReviewEvent("reject", takeId,
                VideoTakeReviewDecision.REJECTED, "test", "2026-09-13T12:00:00Z")), revision = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            storage.save(projectRoot, 1, session.project.copy(selectedTakeIds = emptyList(),
                takeReviewEvents = listOf(VideoTakeReviewEvent("reject", takeId,
                    VideoTakeReviewDecision.REJECTED, "test", "2026-09-13T12:00:00Z")), revision = 2))
        }
        assertContentEquals(before, Files.readAllBytes(document))
        val deselected = storage.save(projectRoot, 1, session.project.copy(selectedTakeIds = emptyList(), revision = 2))
        val result = reviewer(storage).review(VideoProjectSession(projectRoot, deselected), 2,
            takeId, VideoTakeReviewDecision.REJECTED, "synthetic-test-reviewer", null)
        assertEquals(VideoTakeReviewStatus.REJECTED, store().open(projectRoot).reviewStatus(takeId))
        assertTrue(result.session.project.selectedTakeIds.isEmpty())
    }

    @Test fun `failed publication unsupported schema and corrupted media preserve previous state`() {
        val session = fixture()
        val before = Files.readAllBytes(document)
        val failing = store(VideoAtomicWriteObserver { _, _ -> error("injected failure") })
        assertFailsWith<VideoProjectSaveException> {
            reviewer(failing).review(session, 1, takeId, VideoTakeReviewDecision.APPROVED, "test", null)
        }
        assertContentEquals(before, Files.readAllBytes(document))
        Files.writeString(projectRoot.resolve("takes/take.mp4"), "tampered fixture")
        assertFailsWith<InvalidVideoProjectException> {
            reviewer().review(session, 1, takeId, VideoTakeReviewDecision.APPROVED, "test", null)
        }
        assertContentEquals(before, Files.readAllBytes(document))
        Files.writeString(document, """{"schema":"melotrail-video-project","version":3,"project":{}}""")
        val unsupported = Files.readAllBytes(document)
        assertFailsWith<UnsupportedVideoProjectException> {
            reviewer().review(session, 1, takeId, VideoTakeReviewDecision.APPROVED, "test", null)
        }
        assertContentEquals(unsupported, Files.readAllBytes(document))
    }

    private fun sha(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
