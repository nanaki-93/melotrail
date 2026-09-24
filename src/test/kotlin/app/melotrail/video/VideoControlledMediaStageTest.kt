package app.melotrail.video

import app.melotrail.video.adapter.VideoControlledMediaStage
import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import org.junit.jupiter.api.Test

class VideoControlledMediaStageTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC)

    @Test fun `only the durable current claimed binding can start work and duplicate direct calls cannot start it twice`() {
        val root = Files.createTempDirectory("controlled-stage-")
        val domain = "domain-${root.fileName}"
        val midi = Files.createDirectory(root.resolve("midi"))
        val store = VideoJobStore(root.resolve("jobs"), domain, listOf(midi))
        val second = VideoJobStore(root.resolve("jobs/../jobs"), domain, listOf(midi))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val worker: (VideoGenerationJobRequest, VideoOwnedBackendAttempt, app.melotrail.video.adapter.VideoMediaProcessCancellation) -> VideoBackendObservation = { request, owned, _ ->
            assertEquals(request.requestFingerprint, owned.requestFingerprint)
            calls.incrementAndGet()
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            VideoBackendObservation.Unknown("Media pipeline is not installed in this boundary fixture.")
        }
        val stage = VideoControlledMediaStage(store, domain, worker, clock)
        val alias = VideoControlledMediaStage(second, domain, worker, clock)
        assertEquals(setOf(VideoGenerationInputKind.CONTROLLED_MOTION), stage.availability().supportedInputs)
        assertEquals(VideoBackendAvailabilityStatus.AVAILABLE, stage.availability().status)
        val input = input()
        val request = VideoGenerationJobRequest("request", "video-project", stage.backendId, emptyList(), input,
            controlledMotionRequestFingerprint(stage.backendId, input, emptyList(), 2), 2, Instant.now(clock).toString(), input.media.execution)
        val premature = VideoOwnedBackendAttempt(request.id, request.requestFingerprint, "attempt", "owner", stage.backendId, null)
        assertIs<VideoBackendSubmission.Rejected>(stage.submit(VideoBackendSubmissionCommand(premature, input, emptyList(), request.execution)))
        val ids = AtomicInteger()
        fun coordinator(persistence: VideoJobPersistence, backend: VideoGenerationBackendPort) = VideoJobCoordinator(domain, persistence,
            listOf(backend), clock = clock, attemptIdFactory = { "attempt-${ids.incrementAndGet()}" },
            ownershipTokenFactory = { "owner-${ids.incrementAndGet()}" })
        val first = coordinator(store, stage)
        val other = coordinator(second, alias)
        try {
            val admitted = assertIs<VideoJobResult.Accepted>(first.submit(request))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val attempt = admitted.attempt!!
            val owned = VideoOwnedBackendAttempt(request.id, request.requestFingerprint, attempt.id,
                attempt.ownershipToken, stage.backendId, attempt.providerWorkId)
            val command = VideoBackendSubmissionCommand(owned.copy(providerWorkId = null), input, emptyList(), request.execution)
            assertIs<VideoBackendSubmission.Rejected>(alias.submit(command)) // no longer PENDING
            assertIs<VideoBackendSubmission.Rejected>(alias.submit(command.copy(input = input.copy(prompt = "replacement"))))
            assertIs<VideoBackendSubmission.Rejected>(alias.submit(command.copy(ownedAttempt = command.ownedAttempt.copy(ownershipToken = "forged"))))
            assertIs<VideoBackendSubmission.Rejected>(alias.submit(command.copy(ownedAttempt = command.ownedAttempt.copy(requestFingerprint = "f".repeat(64)))))
            assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY, assertIs<VideoJobResult.Rejected>(other.submit(request.copy(
                id = "other", input = input.copy(primaryPrompt = "different"),
                requestFingerprint = controlledMotionRequestFingerprint(stage.backendId, input.copy(primaryPrompt = "different"), emptyList(), 2),
            ))).problem.code)
            assertEquals(attempt.id, assertIs<VideoJobResult.Accepted>(other.submit(request)).attempt!!.id)
            assertEquals(1, calls.get())
            assertEquals(1, second.snapshot().jobs.size)
        } finally { release.countDown() }
    }

    @Test fun `unclaimed and older attempts never execute even with matching command fields`() {
        val root = Files.createTempDirectory("controlled-unclaimed-")
        val domain = "domain-${root.fileName}"
        val midi = Files.createDirectory(root.resolve("midi"))
        val store = VideoJobStore(root.resolve("jobs"), domain, listOf(midi))
        val calls = AtomicInteger()
        val stage = VideoControlledMediaStage(store, domain, { _, _, _ -> calls.incrementAndGet(); VideoBackendObservation.Unknown("not executed") }, clock)
        val input = input()
        val request = VideoGenerationJobRequest("request", "video-project", stage.backendId, emptyList(), input,
            controlledMotionRequestFingerprint(stage.backendId, input, emptyList(), 2), 2, Instant.now(clock).toString(), input.media.execution)
        val ledger = store.loadOrCreate(domain, Instant.now(clock).toString())
        val attempt = VideoGenerationAttempt("attempt", request.id, 1, "owner", VideoGenerationAttemptStatus.SUBMITTING,
            VideoSubmissionPhase.READY, Instant.now(clock).toString())
        store.compareAndSet(ledger.revision, ledger.copy(revision = ledger.revision + 1, jobs = listOf(VideoGenerationJob(request, listOf(attempt)))))
        val command = VideoBackendSubmissionCommand(VideoOwnedBackendAttempt(request.id, request.requestFingerprint, attempt.id,
            attempt.ownershipToken, stage.backendId, null), input, emptyList(), request.execution)
        assertIs<VideoBackendSubmission.Rejected>(stage.submit(command))
        assertEquals(0, calls.get())
        assertIs<VideoBackendObservation.Unknown>(stage.observe(command.ownedAttempt))
        assertIs<VideoBackendCancellation.Unknown>(stage.requestCancellation(command.ownedAttempt))
    }

    @Test fun `two adapter instances race on one pending claim and dispatch only persisted work off caller thread`() {
        val root = Files.createTempDirectory("controlled-race-")
        val domain = "domain-${root.fileName}"
        val midi = Files.createDirectory(root.resolve("midi"))
        val store = VideoJobStore(root.resolve("jobs"), domain, listOf(midi))
        val alias = VideoJobStore(root.resolve("jobs/../jobs"), domain, listOf(midi))
        val input = input()
        val request = VideoGenerationJobRequest("request", "video-project", VideoControlledMediaStage.BACKEND_ID,
            emptyList(), input, controlledMotionRequestFingerprint(VideoControlledMediaStage.BACKEND_ID, input, emptyList(), 2),
            2, Instant.now(clock).toString(), input.media.execution)
        val initial = store.loadOrCreate(domain, Instant.now(clock).toString())
        val attempt = VideoGenerationAttempt("attempt", request.id, 1, "owner", VideoGenerationAttemptStatus.SUBMITTING,
            VideoSubmissionPhase.READY, Instant.now(clock).toString())
        val admitted = store.compareAndSet(initial.revision, initial.copy(revision = initial.revision + 1,
            jobs = listOf(VideoGenerationJob(request, listOf(attempt)))))
        store.compareAndSet(admitted.revision, admitted.copy(revision = admitted.revision + 1,
            jobs = listOf(VideoGenerationJob(request, listOf(attempt.copy(submissionPhase = VideoSubmissionPhase.PENDING))))))
        val count = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker: (VideoGenerationJobRequest, VideoOwnedBackendAttempt, app.melotrail.video.adapter.VideoMediaProcessCancellation) -> VideoBackendObservation = { persisted, _, _ ->
            assertEquals(request, persisted)
            assertTrue(Thread.currentThread().name.startsWith("video-controlled-media"))
            count.incrementAndGet()
            entered.countDown()
            assertTrue(release.await(5, TimeUnit.SECONDS))
            VideoBackendObservation.Unknown("No media pipeline in fixture")
        }
        val stages = listOf(VideoControlledMediaStage(store, domain, worker, clock), VideoControlledMediaStage(alias, domain, worker, clock))
        val command = VideoBackendSubmissionCommand(VideoOwnedBackendAttempt(request.id, request.requestFingerprint,
            attempt.id, attempt.ownershipToken, VideoControlledMediaStage.BACKEND_ID, null), input, emptyList(), request.execution)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val results = stages.map { stage -> executor.submit<VideoBackendSubmission> { start.await(); stage.submit(command) } }
            start.countDown()
            val submissions = results.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, submissions.count { it is VideoBackendSubmission.Accepted })
            assertEquals(1, submissions.count { it is VideoBackendSubmission.Uncertain })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(1, count.get())
            assertEquals(VideoSubmissionPhase.PENDING, alias.snapshot().jobs.single().attempts.single().submissionPhase)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    private fun input(): VideoControlledMotionGenerationInput {
        val pin = VideoGenerationDependencyPin("compositor", "a".repeat(64), "/runtime/compositor")
        val descriptor = motionDescriptor(listOf(pin), 0, 150, 12)
        val prepared = pin.copy(id = "prepared-scene", ownedPath = "/runtime/prepared-scene")
        return VideoControlledMotionGenerationInput("Backend guidance", descriptor.runtime.allPins + prepared,
            VideoControlledMotionRequest(listOf(prepared), 0, 150, 12, descriptor), "Exact authored text", motionMedia())
    }
}
