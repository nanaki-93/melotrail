package app.melotrail.video

import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMediaProbeRequest
import app.melotrail.video.adapter.VideoTakeMediaFacts
import app.melotrail.video.adapter.VideoMotionRenderer
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.time.Instant
import java.time.Duration
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VideoClipGenerationTest {
    @Test fun `import rejects a mismatched project or frame count before touching media`() {
        val root = Files.createTempDirectory("video-completed-guard-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val pin = VideoGenerationDependencyPin("scene", "a".repeat(64), "/owned/scene.json")
        val runtime = VideoGenerationDependencyPin("runtime", "b".repeat(64), "/runtime/renderer.cjs")
        val input = VideoControlledMotionGenerationInput("move", listOf(pin) + motionRuntime(runtime).allPins,
            VideoControlledMotionRequest(listOf(pin), 0, 150, 42, motionDescriptor(listOf(runtime), 0, 150, 42, project.id)))
        val backendId = "controlled-local"
        val request = VideoGenerationJobRequest("request", project.id, backendId, emptyList(), input,
            controlledMotionRequestFingerprint(backendId, input, emptyList()), 1, Instant.now().toString(),
            VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000))
        val ledger = VideoJobLedger("domain", Instant.now().toString(), listOf(VideoGenerationJob(request)))
        val persistence = object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String) = ledger
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger = error("No write permitted")
        }
        val coordinator = VideoJobCoordinator("domain", persistence, emptyList())
        val service = VideoClipGeneration(VideoScenePreparation(), coordinator,
            VideoResultImport(store, VideoMediaProbe()), VideoMotionRenderer(), backendId,
            VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000))
        val probe = VideoMediaProbeRequest(root, root.resolve("absent.mp4"), root.resolve("probe"), Duration.ofSeconds(1))
        val facts = VideoTakeMediaFacts(149, 30.0, 320, 180, 320, 180, "none")
        val session = VideoProjectSession(root, project)
        val mismatch = service.importCompleted(VideoCompletedTakeImport(session, project, 0, "request", probe, null, facts))
        assertTrue(mismatch is VideoClipGenerationResult.Rejected && mismatch.reason.contains("frame count"))
        val other = project.copy(id = "other")
        val wrongProject = service.importCompleted(VideoCompletedTakeImport(session, other, 0, "request", probe, null, facts.copy(frameCount = 150)))
        assertTrue(wrongProject is VideoClipGenerationResult.Rejected && wrongProject.reason.contains("identity"))
        val wrongSession = VideoProjectSession(root, project.copy(id = "other"))
        val sessionMismatch = service.importCompleted(VideoCompletedTakeImport(wrongSession, project, 0, "request", probe, null, facts.copy(frameCount = 150)))
        assertTrue(sessionMismatch is VideoClipGenerationResult.Rejected && sessionMismatch.reason.contains("identity"))
        assertEquals(project, store.open(root))
        assertFalse(Files.exists(root.resolve("probe")))
    }
    @Test fun `immutable take bytes survive source replacement and reopen without implicit selection`() {
        val root = Files.createTempDirectory("video-take-import-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val source = root.resolve("source.mp4")
        Files.write(source, byteArrayOf(1, 2, 3))
        val sourceHash = sha(source)
        val artifact = store.copyImmutableArtifact(root, source, "takes/take-1/v1/preview.mp4", sourceHash)
        val take = VideoTakeRecord(VideoVersionedId("take-1", 1), artifact, null, "2026-09-24T00:00:01Z")
        val saved = store.save(root, 0, project.copy(takeVersions = listOf(take), revision = 1))
        assertEquals(1, saved.revision)
        assertTrue(saved.selectedTakeIds.isEmpty())
        Files.write(source, byteArrayOf(9, 8, 7))
        val reopened = store.open(root)
        assertEquals(saved, reopened)
        assertEquals(sourceHash, sha(store.resolveArtifact(root, artifact)))
        assertNotEquals(sourceHash, sha(source))
    }

    @Test fun `replacement appends a distinct version and preserves earlier take`() {
        val root = Files.createTempDirectory("video-take-replace-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val firstSource = root.resolve("first.mp4"); Files.write(firstSource, byteArrayOf(1, 1))
        val firstArtifact = store.copyImmutableArtifact(root, firstSource, "takes/take-1/v1/preview.mp4", sha(firstSource))
        val first = VideoTakeRecord(VideoVersionedId("take-1", 1), firstArtifact, null, "2026-09-24T00:00:01Z")
        val afterFirst = store.save(root, 0, project.copy(takeVersions = listOf(first), revision = 1))
        val secondSource = root.resolve("second.mp4"); Files.write(secondSource, byteArrayOf(2, 2))
        val secondArtifact = store.copyImmutableArtifact(root, secondSource, "takes/take-2/v1/preview.mp4", sha(secondSource))
        val second = VideoTakeRecord(VideoVersionedId("take-2", 1), secondArtifact, null, "2026-09-24T00:00:02Z")
        val afterSecond = store.save(root, 1, afterFirst.copy(takeVersions = afterFirst.takeVersions + second, revision = 2))
        assertEquals(listOf(first, second), afterSecond.takeVersions)
        assertFalse(afterSecond.selectedTakeIds.isNotEmpty())
        assertEquals(firstArtifact.sha256, sha(store.resolveArtifact(root, firstArtifact)))
    }

    private fun sha(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
