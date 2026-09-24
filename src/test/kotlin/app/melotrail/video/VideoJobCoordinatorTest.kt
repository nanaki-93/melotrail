package app.melotrail.video

import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.adapter.VideoJobAtomicWriteObserver
import app.melotrail.video.application.VideoAvailableModel
import app.melotrail.video.application.VideoBackendAvailability
import app.melotrail.video.application.VideoBackendAvailabilityStatus
import app.melotrail.video.application.VideoBackendCancellation
import app.melotrail.video.application.VideoBackendObservation
import app.melotrail.video.application.VideoBackendOutput
import app.melotrail.video.application.VideoBackendSetup
import app.melotrail.video.application.VideoBackendSubmission
import app.melotrail.video.application.VideoBackendSubmissionCommand
import app.melotrail.video.application.VideoGenerationBackendPort
import app.melotrail.video.application.VideoGenerationInputKind
import app.melotrail.video.application.VideoJobCoordinator
import app.melotrail.video.application.VideoJobPersistence
import app.melotrail.video.application.VideoJobProblemCode
import app.melotrail.video.application.VideoJobResult
import app.melotrail.video.application.VideoOwnedBackendAttempt
import app.melotrail.video.application.VideoSetupAction
import app.melotrail.video.application.VideoSetupActionKind
import app.melotrail.video.application.VideoSetupCapabilityPort
import app.melotrail.video.application.VideoSetupRequirement
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.VideoControlledMotionRequest
import app.melotrail.video.domain.VideoControlledMotionDescriptor
import app.melotrail.video.domain.VideoControlledMotionRuntimeBinding
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import app.melotrail.video.domain.VideoComfyReferenceInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoHostedExecutionPolicy
import app.melotrail.video.domain.VideoJobLedger
import app.melotrail.video.domain.VideoKeyframeGenerationInput
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoModelRequirement
import app.melotrail.video.domain.VideoSubmissionPhase
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun motionRuntime(pin: VideoGenerationDependencyPin): VideoControlledMotionRuntimeBinding {
    fun sibling(id: String) = pin.copy(id = id, ownedPath = "/runtime/$id")
    return VideoControlledMotionRuntimeBinding(sibling("node"), pin.copy(id = "compositor"), sibling("scenery"),
        sibling("canvas-manifest"), listOf(sibling("canvas-artifact-core")), sibling("ffmpeg"),
        sibling("ffprobe"), "0.1.80")
}

internal fun motionDescriptor(
    runtime: List<VideoGenerationDependencyPin>, start: Long, end: Long, seed: Long,
    project: String = "video-project",
): VideoControlledMotionDescriptor = VideoControlledMotionDescriptor(
    projectId = project,
    sourceIdentity = "1".repeat(64),
    requestJson = """{"schema":"melotrail-controlled-motion-request-v1","preparedScene":{"schemaVersion":2,"id":{"id":"scene","version":1},"source":{"references":[{"id":{"id":"reference","version":1},"descriptorArtifact":{"relativePath":"references/reference.json","sha256":"${"1".repeat(64)}"},"original":{"artifact":{"relativePath":"references/original.png","sha256":"${"2".repeat(64)}"},"format":"PNG","mediaType":"image/png","width":320,"height":180,"encodedBytes":100,"hasAlphaChannel":false,"hasTransparentPixels":false}}]},"coordinateSpaces":[{"id":"space","width":320,"height":180}],"layers":[{"id":"finished","kind":"FINISHED_SCENE","image":{"artifact":{"relativePath":"references/original.png","sha256":"${"2".repeat(64)}"},"format":"PNG","mediaType":"image/png","width":320,"height":180,"encodedBytes":100,"hasAlphaChannel":false,"hasTransparentPixels":false},"bounds":{"coordinateSpaceId":"space","x":0,"y":0,"width":320,"height":180}},{"id":"background","kind":"ENVIRONMENT","image":{"artifact":{"relativePath":"references/background.png","sha256":"${"3".repeat(64)}"},"format":"PNG","mediaType":"image/png","width":320,"height":180,"encodedBytes":100,"hasAlphaChannel":false,"hasTransparentPixels":false},"bounds":{"coordinateSpaceId":"space","x":0,"y":0,"width":320,"height":180}},{"id":"subject","kind":"SUBJECT","image":{"artifact":{"relativePath":"references/subject.png","sha256":"${"4".repeat(64)}"},"format":"PNG","mediaType":"image/png","width":40,"height":40,"encodedBytes":100,"hasAlphaChannel":true,"hasTransparentPixels":true},"bounds":{"coordinateSpaceId":"space","x":10,"y":10,"width":40,"height":40}}],"poses":[{"id":"pose","subjectLayerId":"subject","image":{"artifact":{"relativePath":"references/pose.png","sha256":"${"5".repeat(64)}"},"format":"PNG","mediaType":"image/png","width":40,"height":40,"encodedBytes":100,"hasAlphaChannel":true,"hasTransparentPixels":true},"bounds":{"coordinateSpaceId":"space","x":10,"y":10,"width":40,"height":40}}],"motionCapabilities":[{"id":"blink-capability","targetType":"POSE","targetId":"pose","control":"POSE_BLEND","unit":"RATIO","minimum":0,"maximum":1,"defaultValue":0.25}],"dependencies":[{"id":"scene-input","version":"1","sha256":"${"6".repeat(64)}"}],"createdAt":"2026-09-24T00:00:00Z"},"seed":$seed,"fps":30,"canvas":{"width":320,"height":180,"coordinateSpaceId":"space"},"frameRange":{"startFrame":$start,"frameCount":${end - start}},"controls":[{"id":"blink","kind":"blink","capabilityId":"blink-capability","amount":0.25}]}""",
    runtime = motionRuntime(runtime.single()),
)

class VideoJobCoordinatorTest {
    @Test
    fun `cancellation after durable admission but before launch prevents initial and retry backend invocation`() {
        listOf(false, true).forEach { hosted ->
            listOf(false, true).forEach { retry ->
                val fixture = fixture()
                val backend = ControlledBackend()
                val request = if (hosted) hostedRequest("request-a", 'a', 60L, 100L) else localRequest("request-a", 'a')
                val other = fixture.coordinator(backend)
                if (retry) {
                    backend.submitBehavior = { VideoBackendSubmission.Rejected("Owned fixture rejection", true) }
                    accepted(other.submit(request))
                }
                backend.submitBehavior = { VideoBackendSubmission.Accepted("provider-${it.ownedAttempt.attemptId}") }
                val admitted = CountDownLatch(1)
                val releaseAdmission = CountDownLatch(1)
                val targetNumber = if (retry) 2 else 1
                val pausedStore = afterWrite(fixture.store) { ledger ->
                    val attempt = ledger.jobs.single().attempts.last()
                    if (attempt.number == targetNumber && attempt.submissionPhase == VideoSubmissionPhase.READY) {
                        // The underlying CAS has returned and released its locks.
                        // This pause is outside submit, before launch can claim work.
                        admitted.countDown()
                        assertTrue(releaseAdmission.await(5, TimeUnit.SECONDS))
                    }
                }
                val submittingCoordinator = coordinator(pausedStore, backend, fixture.ids)
                val pool = Executors.newSingleThreadExecutor()
                try {
                    val pending = pool.submit<VideoJobResult> {
                        if (retry) submittingCoordinator.retry(request.id) else submittingCoordinator.submit(request)
                    }
                    assertTrue(admitted.await(5, TimeUnit.SECONDS))
                    val durable = fixture.store.snapshot().jobs.single().attempts.last()
                    assertEquals(VideoSubmissionPhase.READY, durable.submissionPhase)
                    val initialSubmissionCount = if (retry) 1 else 0
                    assertEquals(initialSubmissionCount, backend.submissions.size)
                    val cancelled = accepted(other.cancel(request.id, durable.id)).attempt!!
                    assertEquals(VideoGenerationAttemptStatus.CANCELLED, cancelled.status)
                    assertEquals(VideoSubmissionPhase.NOT_STARTED, cancelled.submissionPhase)
                    assertEquals(if (hosted) 0L else null, cancelled.actualCostMicros)
                    assertTrue(cancelled.retryable)
                    assertTrue(backend.cancellations.isEmpty())
                    assertTrue(other.recover().isEmpty())
                    assertEquals(cancelled, accepted(other.reconcile(request.id, durable.id)).attempt)
                    assertTrue(backend.observations.isEmpty())

                    // Both the local slot and hosted maximum are released before
                    // the original caller resumes, even for the retry entry point.
                    val next = accepted(other.retry(request.id)).attempt!!
                    assertNotEquals(durable.id, next.id)
                    assertNotEquals(durable.ownershipToken, next.ownershipToken)
                    releaseAdmission.countDown()
                    val resumed = accepted(pending.get(5, TimeUnit.SECONDS))
                    assertEquals(cancelled, resumed.attempt)
                    assertTrue(!resumed.launchedByCaller)
                    assertEquals(initialSubmissionCount + 1, backend.submissions.size)
                    assertTrue(backend.submissions.none { it.ownedAttempt.attemptId == durable.id })
                    assertEquals(next, fixture.store.snapshot().jobs.single().attempts.last())
                } finally {
                    releaseAdmission.countDown()
                    pool.shutdownNow()
                }
            }
        }
    }

    @Test
    fun `recovery closing an unclaimed admission fences a suspended launch caller`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        val admitted = CountDownLatch(1)
        val releaseAdmission = CountDownLatch(1)
        val pausedStore = afterWrite(fixture.store) { ledger ->
            if (ledger.jobs.single().attempts.first().submissionPhase == VideoSubmissionPhase.READY) {
                admitted.countDown()
                assertTrue(releaseAdmission.await(5, TimeUnit.SECONDS))
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val pending = pool.submit<VideoJobResult> {
                coordinator(pausedStore, backend, fixture.ids).submit(localRequest("request-a", 'a'))
            }
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            val restarted = fixture.coordinator(backend)
            val recovered = accepted(restarted.recover().single()).attempt!!
            assertEquals(VideoGenerationAttemptStatus.FAILED, recovered.status)
            assertEquals(VideoSubmissionPhase.NOT_STARTED, recovered.submissionPhase)
            assertTrue(backend.submissions.isEmpty())
            assertTrue(backend.observations.isEmpty())
            val retry = accepted(restarted.retry("request-a")).attempt!!
            releaseAdmission.countDown()
            val resumed = accepted(pending.get(5, TimeUnit.SECONDS))
            assertEquals(recovered, resumed.attempt)
            assertTrue(!resumed.launchedByCaller)
            assertEquals(retry.id, backend.submissions.single().ownedAttempt.attemptId)
            assertEquals(retry, fixture.store.snapshot().jobs.single().attempts.last())
        } finally {
            releaseAdmission.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `launch claim winning before invocation retains admission through cancellation and unknown recovery`() {
        listOf(false, true).forEach { hosted ->
            val fixture = fixture()
            val backend = ControlledBackend().apply {
                cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
                observeBehavior = { VideoBackendObservation.Unknown("No acknowledgement yet") }
            }
            val claimed = CountDownLatch(1)
            val releaseClaim = CountDownLatch(1)
            val pausedStore = afterWrite(fixture.store) { ledger ->
                val attempt = ledger.jobs.first().attempts.single()
                if (attempt.submissionPhase == VideoSubmissionPhase.PENDING &&
                    attempt.status == VideoGenerationAttemptStatus.SUBMITTING
                ) {
                    claimed.countDown()
                    assertTrue(releaseClaim.await(5, TimeUnit.SECONDS))
                }
            }
            val request = if (hosted) hostedRequest("request-a", 'a', 60L, 100L) else localRequest("request-a", 'a')
            val nextRequest = if (hosted) hostedRequest("request-b", 'b', 60L, 100L) else localRequest("request-b", 'b')
            val busy = if (hosted) VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED else VideoJobProblemCode.LOCAL_SLOT_BUSY
            val other = fixture.coordinator(backend)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val pending = pool.submit<VideoJobResult> { coordinator(pausedStore, backend, fixture.ids).submit(request) }
                assertTrue(claimed.await(5, TimeUnit.SECONDS))
                assertTrue(backend.submissions.isEmpty())
                val cancelled = accepted(other.cancel(request.id)).attempt!!
                assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, cancelled.status)
                assertEquals(VideoSubmissionPhase.PENDING, cancelled.submissionPhase)
                assertNull(cancelled.actualCostMicros)
                assertEquals(cancelled, accepted(other.submit(request)).attempt)
                assertEquals(VideoJobProblemCode.RETRY_NOT_ALLOWED, assertIs<VideoJobResult.Rejected>(other.retry(request.id)).problem.code)
                val recovered = accepted(other.recover().single()).attempt!!
                assertEquals(VideoSubmissionPhase.PENDING, recovered.submissionPhase)
                assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, recovered.status)
                assertEquals(busy, assertIs<VideoJobResult.Rejected>(other.submit(nextRequest)).problem.code)
                assertTrue(backend.submissions.isEmpty())
                releaseClaim.countDown()
                val finished = accepted(pending.get(5, TimeUnit.SECONDS))
                assertTrue(finished.launchedByCaller)
                assertEquals(VideoGenerationAttemptStatus.CANCELLED, finished.attempt!!.status)
                assertEquals(VideoSubmissionPhase.ACKNOWLEDGED, finished.attempt!!.submissionPhase)
                assertNull(finished.attempt!!.actualCostMicros)
                assertEquals(1, backend.submissions.size)
                assertTrue(backend.cancellations.any { it.providerWorkId != null })
                if (hosted) {
                    // A confirmed stop is not proof of a zero charge.
                    assertEquals(busy, assertIs<VideoJobResult.Rejected>(other.submit(nextRequest)).problem.code)
                } else accepted(other.submit(nextRequest))
            } finally {
                releaseClaim.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `crashes after admission and after launch claim recover without automatic resubmission`() {
        listOf(false, true).forEach { hosted ->
            listOf(VideoSubmissionPhase.READY, VideoSubmissionPhase.PENDING).forEach { crashPhase ->
                val fixture = fixture()
                val backend = ControlledBackend().apply {
                    observeBehavior = { VideoBackendObservation.Unknown("Submission outcome unavailable") }
                    cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
                }
                val request = if (hosted) hostedRequest("request-a", 'a', 60L, 100L) else localRequest("request-a", 'a')
                val crashingStore = afterWrite(fixture.store) { ledger ->
                    if (ledger.jobs.single().attempts.single().submissionPhase == crashPhase) {
                        throw IOException("Simulated caller loss after durable $crashPhase CAS")
                    }
                }
                assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                    assertIs<VideoJobResult.Rejected>(coordinator(crashingStore, backend, fixture.ids).submit(request)).problem.code)
                assertTrue(backend.submissions.isEmpty())
                val durable = fixture.store.snapshot().jobs.single().attempts.single()
                assertEquals(crashPhase, durable.submissionPhase)
                val reopened = VideoJobStore(fixture.root.resolve("jobs"), DOMAIN, listOf(fixture.midiRoot))
                val ledger = reopened.snapshot()
                val bypass = durable.copy(submissionPhase = if (crashPhase == VideoSubmissionPhase.READY) {
                    VideoSubmissionPhase.ACKNOWLEDGED
                } else VideoSubmissionPhase.READY)
                assertFailsWith<IllegalArgumentException> {
                    reopened.compareAndSet(ledger.revision, ledger.copy(
                        revision = ledger.revision + 1,
                        jobs = listOf(ledger.jobs.single().copy(attempts = listOf(bypass))),
                    ))
                }
                assertEquals(ledger, reopened.snapshot())
                val restarted = coordinator(reopened, backend, fixture.ids)
                var recovered = accepted(restarted.recover().single()).attempt!!
                assertEquals(durable.id, recovered.id)
                assertEquals(durable.ownershipToken, recovered.ownershipToken)
                assertTrue(backend.submissions.isEmpty())
                if (crashPhase == VideoSubmissionPhase.PENDING) {
                    assertEquals(VideoSubmissionPhase.PENDING, recovered.submissionPhase)
                    assertEquals(VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN, recovered.status)
                    assertNull(recovered.actualCostMicros)
                    assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, accepted(restarted.cancel(request.id)).attempt!!.status)
                    assertEquals(VideoJobProblemCode.RETRY_NOT_ALLOWED,
                        assertIs<VideoJobResult.Rejected>(restarted.retry(request.id)).problem.code)
                    val otherRequest = if (hosted) hostedRequest("request-b", 'b', 60L, 100L) else localRequest("request-b", 'b')
                    assertEquals(if (hosted) VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED else VideoJobProblemCode.LOCAL_SLOT_BUSY,
                        assertIs<VideoJobResult.Rejected>(restarted.submit(otherRequest)).problem.code)
                    backend.observeBehavior = { VideoBackendObservation.NotStarted }
                    recovered = accepted(restarted.reconcile(request.id)).attempt!!
                    assertEquals(VideoGenerationAttemptStatus.CANCELLED, recovered.status)
                } else {
                    assertEquals(VideoGenerationAttemptStatus.FAILED, recovered.status)
                    assertTrue(backend.observations.isEmpty())
                    assertTrue(backend.cancellations.isEmpty())
                }
                assertEquals(VideoSubmissionPhase.NOT_STARTED, recovered.submissionPhase)
                assertEquals(if (hosted) 0L else null, recovered.actualCostMicros)
                assertTrue(recovered.retryable)
                assertTrue(restarted.recover().isEmpty())
                assertTrue(backend.submissions.isEmpty())
                val retry = accepted(restarted.retry(request.id)).attempt!!
                assertNotEquals(durable.id, retry.id)
                assertEquals(1, backend.submissions.size)
            }
        }
    }

    @Test
    fun `replaced admission root cannot create controls or admit another local job`() {
        listOf(false, true).forEach { linkToMidi ->
            val fixture = fixture()
            val backend = ControlledBackend()
            val coordinator = fixture.coordinator(backend)
            accepted(coordinator.submit(localRequest("request-a", 'a')))
            val ledger = fixture.store.snapshot()
            val jobsRoot = fixture.root.resolve("jobs")
            val before = Files.readAllBytes(jobsRoot.resolve(VideoJobStore.DOCUMENT))
            val midiFile = fixture.midiRoot.resolve("project.json")
            Files.writeString(midiFile, "protected MIDI fixture")
            val retained = fixture.root.resolve("retained-jobs")
            Files.move(jobsRoot, retained)
            if (linkToMidi) Files.createSymbolicLink(jobsRoot, fixture.midiRoot)
            else Files.createDirectory(jobsRoot)

            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", 'b'))).problem.code)
            assertFailsWith<IllegalArgumentException> { fixture.store.snapshot() }
            assertFailsWith<IllegalArgumentException> {
                fixture.store.compareAndSet(ledger.revision, ledger.copy(revision = ledger.revision + 1))
            }
            assertEquals(1, backend.submissions.size)
            assertContentEquals(before, Files.readAllBytes(retained.resolve(VideoJobStore.DOCUMENT)))
            assertEquals("protected MIDI fixture", Files.readString(midiFile))
            assertEquals(if (linkToMidi) setOf("project.json") else emptySet(), childNames(jobsRoot))
            assertEquals(ledger, VideoJobStore(retained, DOMAIN, listOf(fixture.midiRoot)).snapshot())
        }
    }

    @Test
    fun `ancestor rebinding fails even when the original admission directory is moved back beneath it`() {
        listOf(false, true).forEach { symbolicAncestor ->
            val root = Files.createTempDirectory("melotrail-video-job-ancestor-")
            val midi = Files.createDirectory(root.resolve("midi"))
            val parent = Files.createDirectory(root.resolve("parent"))
            val jobsRoot = parent.resolve("jobs")
            val store = VideoJobStore(jobsRoot, DOMAIN, listOf(midi))
            val backend = ControlledBackend()
            val coordinator = coordinator(store, backend, AtomicInteger())
            accepted(coordinator.submit(localRequest("request-a", 'a')))
            val ledger = store.snapshot()
            val before = Files.readAllBytes(jobsRoot.resolve(VideoJobStore.DOCUMENT))
            val retained = root.resolve("retained-parent")
            Files.move(parent, retained)
            if (symbolicAncestor) {
                val replacement = Files.createDirectory(root.resolve("replacement-parent"))
                Files.createDirectory(replacement.resolve("jobs"))
                Files.createSymbolicLink(parent, replacement)
            } else {
                Files.createDirectory(parent)
                // Checking only the leaf inode would miss this ancestor replacement.
                Files.move(retained.resolve("jobs"), jobsRoot)
            }

            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", 'b'))).problem.code)
            assertFailsWith<IllegalArgumentException> { store.loadOrCreate(DOMAIN, NOW) }
            assertFailsWith<IllegalArgumentException> { store.snapshot() }
            assertFailsWith<IllegalArgumentException> {
                store.compareAndSet(ledger.revision, ledger.copy(revision = ledger.revision + 1))
            }
            assertEquals(1, backend.submissions.size)
            val originalJobs = if (symbolicAncestor) retained.resolve("jobs") else jobsRoot
            assertContentEquals(before, Files.readAllBytes(originalJobs.resolve(VideoJobStore.DOCUMENT)))
            if (symbolicAncestor) assertTrue(childNames(jobsRoot).isEmpty())
            else assertEquals(setOf(VideoJobStore.DOCUMENT, VideoJobStore.LOCK), childNames(jobsRoot))
            assertTrue(childNames(midi).isEmpty())
        }
    }

    @Test
    fun `new MIDI markers and protected root rebinding block existing job stores before control writes`() {
        listOf(false, true).forEach { rebindProtected ->
            val fixture = fixture()
            val backend = ControlledBackend()
            val coordinator = fixture.coordinator(backend)
            accepted(coordinator.submit(localRequest("request-a", 'a')))
            val jobsRoot = fixture.root.resolve("jobs")
            val before = Files.readAllBytes(jobsRoot.resolve(VideoJobStore.DOCUMENT))
            if (rebindProtected) {
                Files.move(fixture.midiRoot, fixture.root.resolve("retained-midi"))
                Files.createSymbolicLink(fixture.midiRoot, jobsRoot)
            } else Files.writeString(jobsRoot.resolve("project.json"), "new MIDI marker")
            val names = childNames(jobsRoot)

            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", 'b'))).problem.code)
            assertEquals(1, backend.submissions.size)
            assertContentEquals(before, Files.readAllBytes(jobsRoot.resolve(VideoJobStore.DOCUMENT)))
            assertEquals(names, childNames(jobsRoot))
        }
    }

    @Test
    fun `root rebind at atomic publication preserves original ledger and staged recovery without writing rebound location`() {
        listOf(false, true).forEach { linkToMidi ->
            val fixture = fixture()
            val jobsRoot = fixture.root.resolve("jobs")
            val retained = fixture.root.resolve("retained-jobs")
            val midiFile = fixture.midiRoot.resolve("project.json")
            Files.writeString(midiFile, "protected MIDI fixture")
            var armed = false
            var stagedName: String? = null
            val store = VideoJobStore(jobsRoot, DOMAIN, listOf(fixture.midiRoot), VideoJobAtomicWriteObserver { temporary, _ ->
                if (armed) {
                    armed = false
                    stagedName = temporary.fileName.toString()
                    Files.move(jobsRoot, retained)
                    if (linkToMidi) Files.createSymbolicLink(jobsRoot, fixture.midiRoot)
                    else Files.createDirectory(jobsRoot)
                }
            })
            val backend = ControlledBackend()
            val coordinator = coordinator(store, backend, AtomicInteger())
            accepted(coordinator.submit(localRequest("request-a", 'a')))
            val before = Files.readAllBytes(jobsRoot.resolve(VideoJobStore.DOCUMENT))
            armed = true

            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.cancel("request-a")).problem.code)
            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", 'b'))).problem.code)
            assertEquals(1, backend.submissions.size)
            assertTrue(backend.cancellations.isEmpty())
            assertContentEquals(before, Files.readAllBytes(retained.resolve(VideoJobStore.DOCUMENT)))
            assertTrue(Files.readString(retained.resolve(requireNotNull(stagedName))).contains("CANCELLATION_REQUESTED"))
            assertEquals(if (linkToMidi) setOf("project.json") else emptySet(), childNames(jobsRoot))
            assertEquals("protected MIDI fixture", Files.readString(midiFile))
        }
    }

    @Test
    fun `invalid completed output paths preserve active attempt and previous successful output`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        val coordinator = fixture.coordinator(backend)
        accepted(coordinator.submit(localRequest("request-a", 'a')))
        backend.observeBehavior = { owned ->
            VideoBackendObservation.Completed(owned.providerWorkId, VideoBackendOutput("good-output", "takes/good.mp4", HASH_1, 10))
        }
        val completed = accepted(coordinator.reconcile("request-a")).job
        accepted(coordinator.submit(localRequest("request-b", 'b')))
        val before = fixture.store.snapshot()
        val bytes = Files.readAllBytes(fixture.root.resolve("jobs").resolve(VideoJobStore.DOCUMENT))
        val invalidPaths = listOf(
            "C:/take.mp4", "takes/bad?.mp4", "../take.mp4", "takes\\take.mp4",
            "video-project.json", ".video-project.lock", "takes/VIDEO-PROJECT.JSON. ",
            "takes/.VIDEO-PROJECT.LOCK. ", ".video-project.json.save-fixture.tmp",
            "takes/.VIDEO-PROJECT.JSON.RECOVERY-fixture.json",
            "video-jobs.json", ".video-jobs.lock", "takes/VIDEO-JOBS.JSON. ",
            "takes/.VIDEO-JOBS.LOCK. ", ".video-jobs.save-fixture.tmp",
            "takes/.VIDEO-JOBS.RECOVERY-fixture.json",
        )
        invalidPaths.forEach { path ->
            backend.observeBehavior = { owned ->
                VideoBackendObservation.Completed(owned.providerWorkId, VideoBackendOutput("bad-output", path, HASH_2, 11))
            }
            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.reconcile("request-b"), path).problem.code, path)
            assertEquals(before, fixture.store.snapshot(), path)
            assertContentEquals(bytes, Files.readAllBytes(fixture.root.resolve("jobs").resolve(VideoJobStore.DOCUMENT)), path)
            assertEquals(completed, fixture.store.snapshot().jobs.first(), path)
        }
        assertEquals(2, backend.submissions.size)
    }

    @Test
    fun `invalid immediate completion preserves durable submission ownership and blocks another local launch`() {
        listOf("C:/take.mp4", "takes/.VIDEO-JOBS.LOCK. ").forEach { path ->
            val fixture = fixture()
            val backend = ControlledBackend()
            val coordinator = fixture.coordinator(backend)
            var durableBytes: ByteArray? = null
            backend.submitBehavior = { command ->
                durableBytes = Files.readAllBytes(fixture.root.resolve("jobs").resolve(VideoJobStore.DOCUMENT))
                VideoBackendSubmission.Completed("provider-${command.ownedAttempt.attemptId}",
                    VideoBackendOutput("bad-output", path, HASH_1, 10))
            }

            assertEquals(VideoJobProblemCode.PERSISTENCE_FAILED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-a", 'a'))).problem.code)
            val preserved = fixture.store.snapshot().jobs.single()
            assertEquals(VideoGenerationAttemptStatus.SUBMITTING, preserved.attempts.single().status)
            assertEquals(VideoSubmissionPhase.PENDING, preserved.attempts.single().submissionPhase)
            assertTrue(preserved.outputs.isEmpty())
            assertNull(preserved.currentOutputId)
            assertContentEquals(requireNotNull(durableBytes),
                Files.readAllBytes(fixture.root.resolve("jobs").resolve(VideoJobStore.DOCUMENT)))
            assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", 'b'))).problem.code)
            assertEquals(1, backend.submissions.size)
        }
    }

    @Test
    fun `executable descriptor round trips and rejects changed bindings unsafe numbers and unsupported ledger`() {
        val scene = VideoGenerationDependencyPin("scene", HASH_1, "/owned/scene.json")
        val runtime = VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/renderer.cjs")
        val descriptor = motionDescriptor(listOf(runtime), 120, 420, 17)
        val motion = VideoControlledMotionRequest(listOf(scene), 120, 420, 17, descriptor)
        val input = VideoControlledMotionGenerationInput("prompt with guidance", listOf(scene) + descriptor.runtime.allPins, motion, "prompt")
        val base = localRequest("controlled-binding", 'a').copy(input = input)
        val request = base.copy(requestFingerprint = controlledMotionRequestFingerprint(base.backendId, input, base.modelRequirements))
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            availabilityValue = availabilityValue.copy(supportedInputs = availabilityValue.supportedInputs + VideoGenerationInputKind.CONTROLLED_MOTION)
            submitBehavior = { VideoBackendSubmission.Rejected("fixture", true) }
        }
        accepted(fixture.coordinator(backend).submit(request))
        val reopened = VideoJobStore(fixture.root.resolve("jobs"), DOMAIN, listOf(fixture.midiRoot)).snapshot()
        assertEquals(descriptor, (reopened.jobs.single().request.input as VideoControlledMotionGenerationInput).motion.descriptor)
        assertEquals("prompt", (reopened.jobs.single().request.input as VideoControlledMotionGenerationInput).primaryPrompt)
        for (count in listOf(150L, 600L, 900L)) {
            val first = 120L
            val full = motionDescriptor(listOf(runtime), first, first + count, 17)
            val chunks = full.invocationDescriptors().map { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.toList()
            val ranges = chunks.map { it.getValue("frameRange").jsonObject }
            assertEquals((count + 299) / 300, ranges.size.toLong())
            var next = first
            ranges.forEach { range ->
                assertEquals(next, range.getValue("startFrame").jsonPrimitive.content.toLong())
                val size = range.getValue("frameCount").jsonPrimitive.content.toLong()
                assertTrue(size in 1..300)
                next += size
            }
            assertEquals(first + count, next)
            assertTrue(chunks.all { it.getValue("preparedScene") ==
                kotlinx.serialization.json.Json.parseToJsonElement(full.requestJson).jsonObject.getValue("preparedScene") })
        }
        val upper = app.melotrail.video.domain.MAX_JAVASCRIPT_SAFE_INTEGER
        val edge = motionDescriptor(listOf(runtime), upper - 900, upper, 17)
        assertEquals(3, edge.invocationDescriptors().count())
        fun identity(other: VideoControlledMotionDescriptor) = controlledMotionRequestFingerprint(base.backendId,
            input.copy(dependencyPins = listOf(scene) + other.runtime.allPins, motion = motion.copy(descriptor = other)), base.modelRequirements)
        assertNotEquals(request.requestFingerprint, identity(descriptor.copy(projectId = "other-project")))
        assertNotEquals(request.requestFingerprint, identity(descriptor.copy(sourceIdentity = HASH_2)))
        assertNotEquals(request.requestFingerprint, identity(descriptor.copy(requestJson = descriptor.requestJson.replace("0.25", "0.26"))))
        assertNotEquals(request.requestFingerprint, identity(descriptor.copy(runtime = descriptor.runtime.copy(compositor = descriptor.runtime.compositor.copy(sha256 = HASH_1)))))
        assertNotEquals(request.requestFingerprint, identity(descriptor.copy(runtime = descriptor.runtime.copy(expectedCanvasVersion = "0.1.81"))))
        val binding = descriptor.runtime
        assertEquals(setOf("node", "compositor", "scenery", "canvas-manifest", "canvas-artifact-core", "ffmpeg", "ffprobe"),
            (reopened.jobs.single().request.input as VideoControlledMotionGenerationInput).motion.descriptor.runtime.allPins.map { it.id }.toSet())
        assertFailsWith<IllegalArgumentException> { binding.copy(canvasArtifacts = emptyList()) }
        assertFailsWith<IllegalArgumentException> { binding.copy(ffprobe = binding.ffprobe.copy(id = "probe-missing")) }
        assertFailsWith<IllegalArgumentException> { binding.copy(scenery = binding.scenery.copy(ownedPath = null)) }
        assertFailsWith<IllegalArgumentException> { binding.copy(canvasArtifacts = listOf(binding.node)) }
        assertFailsWith<IllegalArgumentException> { input.copy(dependencyPins = input.dependencyPins - binding.ffprobe) }
        assertFailsWith<IllegalArgumentException> { motion.copy(seed = app.melotrail.video.domain.MAX_JAVASCRIPT_SAFE_INTEGER + 1) }
        assertFailsWith<IllegalArgumentException> { motion.copy(endFrameExclusive = Long.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("\"width\":320,\"height\":180,\"coordinateSpaceId\"", "\"width\":321,\"height\":180,\"coordinateSpaceId\"")) }
        // Old acceptance validated only the viewport's layer shape. Node rejects these
        // incomplete or mismatched controls before rendering a single frame.
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"createdAt\":\"2026-09-24T00:00:00Z\"", "\"createdAtMissing\":\"2026-09-24T00:00:00Z\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"references\":[{\"id\":{\"id\":\"reference\",\"version\":1},", "\"references\":[{\"id\":{\"id\":\"bad id\",\"version\":1},")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"dependencies\":[{\"id\":\"scene-input\",\"version\":\"1\",\"sha256\":\"${"6".repeat(64)}\"}]", "\"dependencies\":[]")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"id\":\"blink\"", "\"id\":\"bad id\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"capabilityId\":\"blink-capability\"", "\"capabilityId\":\"missing-capability\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace(
            "\"kind\":\"SUBJECT\"", "\"kind\":\"FOREGROUND\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("0.25", "1e999")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("0.25", "1.5")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("0.25", "\"0.25\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("\"fps\":30", "\"fps\":\"30\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("\"width\":320,\"height\":180,\"coordinateSpaceId\"", "\"width\":320.5,\"height\":180,\"coordinateSpaceId\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("\"frameCount\":300", "\"frameCount\":9001")) }
        val sceneryOnly = descriptor.requestJson.replace(
            "\"controls\":[{\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25}]",
            "\"controls\":[],\"scenery\":{}",
        )
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = sceneryOnly) }
        // A present null is not absence: scenery.cjs rejects it even if a valid
        // control would otherwise make the descriptor executable.
        val controlledWithNullScenery = descriptor.requestJson.replace(
            "\"amount\":0.25}]}", "\"amount\":0.25}],\"scenery\":null}",
        )
        assertNotEquals(descriptor.requestJson, controlledWithNullScenery)
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = controlledWithNullScenery) }
        val malformedScenery = descriptor.requestJson.replace(
            "\"controls\":[{\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25}]",
            "\"controls\":[],\"scenery\":{\"schema\":\"melotrail-rigid-scenery-v1\",\"viewport\":{},\"mode\":\"static\",\"camera\":{},\"planes\":[]}",
        )
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = malformedScenery) }
        val sceneryWithInvalidSection = descriptor.requestJson.replace(
            "\"controls\":[{\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25}]",
            "\"controls\":[],\"scenery\":{\"schema\":\"melotrail-rigid-scenery-v1\",\"viewport\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":320,\"height\":180},\"mode\":\"static\",\"camera\":{\"startFrame\":0,\"durationFrames\":1,\"travelXPixels\":0,\"travelYPixels\":0},\"planes\":[{\"id\":\"plane\",\"sections\":[{\"worldX\":0,\"worldY\":0,\"startFrame\":0,\"endFrameExclusive\":1}]}]}",
        )
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = sceneryWithInvalidSection) }
        // A structurally valid section still has to obey Node's prepared coverage
        // review, source bounds, placement and trajectory rules.
        val sceneryScene = descriptor.requestJson.replace(
            "\"motionCapabilities\":[", "\"sceneryCoverage\":[{\"id\":\"coverage\",\"layerId\":\"background\",\"bounds\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":320,\"height\":180}}],\"motionCapabilities\":[",
        )
        val validScenery = sceneryScene.replace(
            "\"controls\":[{\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25}]",
            "\"controls\":[],\"scenery\":{\"schema\":\"melotrail-rigid-scenery-v1\",\"viewport\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":320,\"height\":180},\"mode\":\"static\",\"camera\":{\"startFrame\":0,\"durationFrames\":1,\"travelXPixels\":0,\"travelYPixels\":0},\"planes\":[{\"id\":\"plane\",\"sections\":[{\"coverageId\":\"coverage\",\"worldX\":0,\"worldY\":0,\"startFrame\":120,\"endFrameExclusive\":420}]}]}",
        )
        descriptor.copy(requestJson = validScenery)
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = validScenery.replace(
            "\"worldX\":0", "\"worldX\":1")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = validScenery.replace(
            "\"id\":\"coverage\",\"layerId\"", "\"id\":\"coverage\",\"reviewStatus\":\"REJECTED\",\"layerId\"")) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = validScenery.replace(
            "\"width\":320,\"height\":180}}],\"motionCapabilities\"", "\"x\":1,\"width\":319,\"height\":180}}],\"motionCapabilities\"")) }
        val headGesture = descriptor.requestJson
            .replace("\"id\":\"blink-capability\",\"targetType\":\"POSE\",\"targetId\":\"pose\",\"control\":\"POSE_BLEND\",\"unit\":\"RATIO\",\"minimum\":0,\"maximum\":1,\"defaultValue\":0.25", "\"id\":\"head-capability\",\"targetType\":\"LAYER\",\"targetId\":\"subject\",\"control\":\"ROTATE\",\"unit\":\"DEGREES\",\"minimum\":-3,\"maximum\":3,\"defaultValue\":0")
            .replace("\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25", "\"kind\":\"headGesture\",\"capabilityId\":\"head-capability\",\"amplitudeDegrees\":1")
            .replace("\"poses\":[{\"id\":\"pose\"", "\"masks\":[" +
                "{\"id\":\"head-ok\",\"image\":{\"artifact\":{\"relativePath\":\"assets/head.png\",\"sha256\":\"${HASH_2}\"},\"format\":\"PNG\",\"mediaType\":\"image/png\",\"width\":2,\"height\":2,\"encodedBytes\":4,\"hasAlphaChannel\":true,\"hasTransparentPixels\":true},\"bounds\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":40,\"height\":40},\"layerIds\":[\"subject\"],\"purpose\":\"HEAD_REGION\",\"alpha\":{\"opaquePixels\":1,\"translucentPixels\":1,\"transparentPixels\":2},\"reviewStatus\":\"ACCEPTED\"}," +
                "{\"id\":\"head-rejected\",\"image\":{\"artifact\":{\"relativePath\":\"assets/head-rejected.png\",\"sha256\":\"${HASH_2}\"},\"format\":\"PNG\",\"mediaType\":\"image/png\",\"width\":2,\"height\":2,\"encodedBytes\":4,\"hasAlphaChannel\":true,\"hasTransparentPixels\":true},\"bounds\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":40,\"height\":40},\"layerIds\":[\"subject\"],\"purpose\":\"HEAD_REGION\",\"alpha\":{\"opaquePixels\":1,\"translucentPixels\":1,\"transparentPixels\":2},\"reviewStatus\":\"REJECTED\"}],\"poses\":[{\"id\":\"pose\"")
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = headGesture) }
        // A valid prepared-scene mask without measured counts must not become executable.
        val headMaskWithoutMeasuredAlpha = headGesture.replace(
            "\"alpha\":{\"opaquePixels\":1,\"translucentPixels\":1,\"transparentPixels\":2}", "\"alpha\":null",
        )
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = headMaskWithoutMeasuredAlpha) }
        // Renderer admission validates every prepared-scene mask, not only masks
        // selected by a headGesture control. This valid blink scene carries an
        // unrelated, otherwise valid mask with absent measured alpha.
        val unrelatedMaskWithoutAlpha = descriptor.requestJson.replace(
            "\"poses\":[", "\"masks\":[{\"id\":\"unrelated-mask\",\"image\":{\"artifact\":{\"relativePath\":\"assets/unrelated.png\",\"sha256\":\"${HASH_2}\"},\"format\":\"PNG\",\"mediaType\":\"image/png\",\"width\":2,\"height\":2,\"encodedBytes\":4,\"hasAlphaChannel\":true,\"hasTransparentPixels\":true},\"bounds\":{\"coordinateSpaceId\":\"space\",\"x\":10,\"y\":10,\"width\":40,\"height\":40},\"layerIds\":[\"subject\"],\"purpose\":\"HEAD_REGION\",\"alpha\":null,\"reviewStatus\":\"ACCEPTED\"}],\"poses\":[",
        )
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = unrelatedMaskWithoutAlpha) }
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = descriptor.requestJson.replace("\"seed\":17", "\"seed\":9007199254740992")) }
        assertFailsWith<IllegalArgumentException> { request.copy(projectId = "other-project") }
        val path = fixture.root.resolve("jobs").resolve(VideoJobStore.DOCUMENT)
        val validBytes = Files.readAllBytes(path)
        val changedBinding = validBytes.toString(Charsets.UTF_8).replace(
            "1".repeat(64), "2".repeat(64),
        )
        assertNotEquals(validBytes.toString(Charsets.UTF_8), changedBinding)
        Files.writeString(path, changedBinding)
        val tamperedBytes = Files.readAllBytes(path)
        val reopenedStore = VideoJobStore(fixture.root.resolve("jobs"), DOMAIN, listOf(fixture.midiRoot))
        assertFailsWith<IllegalArgumentException> { reopenedStore.snapshot() }
        assertFailsWith<IllegalArgumentException> { reopenedStore.loadOrCreate(DOMAIN, NOW) }
        assertContentEquals(tamperedBytes, Files.readAllBytes(path))
        Files.writeString(path, validBytes.toString(Charsets.UTF_8).replace("\"version\": 3", "\"version\": 999"))
        val unsupportedBytes = Files.readAllBytes(path)
        val unsupportedStore = VideoJobStore(fixture.root.resolve("jobs"), DOMAIN, listOf(fixture.midiRoot))
        assertFailsWith<IllegalArgumentException> { unsupportedStore.snapshot() }
        assertFailsWith<IllegalArgumentException> { unsupportedStore.loadOrCreate(DOMAIN, NOW) }
        assertContentEquals(unsupportedBytes, Files.readAllBytes(path))
        Files.writeString(path, validBytes.toString(Charsets.UTF_8).replace("\"version\": 3", "\"version\": 2"))
        val oldBytes = Files.readAllBytes(path)
        assertFailsWith<IllegalArgumentException> { unsupportedStore.snapshot() }
        assertFailsWith<IllegalArgumentException> { unsupportedStore.loadOrCreate(DOMAIN, NOW) }
        assertContentEquals(oldBytes, Files.readAllBytes(path))
    }

    @Test
    fun `controlled steam binding requires the resolved anchor in the output canvas space`() {
        val descriptor = motionDescriptor(listOf(VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/renderer.cjs")), 120, 420, 17)
        val steam = descriptor.requestJson
            .replace("\"poses\":[", "\"effectAnchors\":[{\"id\":\"steam-anchor\",\"layerId\":\"subject\",\"position\":{\"coordinateSpaceId\":\"space\",\"point\":{\"x\":20,\"y\":20}}}],\"poses\":[")
            .replace("\"id\":\"blink-capability\",\"targetType\":\"POSE\",\"targetId\":\"pose\",\"control\":\"POSE_BLEND\",\"unit\":\"RATIO\",\"minimum\":0,\"maximum\":1,\"defaultValue\":0.25",
                "\"id\":\"steam-capability\",\"targetType\":\"EFFECT_ANCHOR\",\"targetId\":\"steam-anchor\",\"control\":\"EFFECT_RATE\",\"unit\":\"PER_SECOND\",\"minimum\":0.01,\"maximum\":8,\"defaultValue\":1")
            .replace("\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25",
                "\"id\":\"steam\",\"kind\":\"steam\",\"capabilityId\":\"steam-capability\",\"ratePerSecond\":1")
        descriptor.copy(requestJson = steam)
        val otherSpace = steam
            .replace("\"coordinateSpaces\":[{\"id\":\"space\",\"width\":320,\"height\":180}]",
                "\"coordinateSpaces\":[{\"id\":\"space\",\"width\":320,\"height\":180},{\"id\":\"other\",\"width\":320,\"height\":180}]")
            .replace("\"coordinateSpaceId\":\"space\",\"x\":10,\"y\":10,\"width\":40,\"height\":40",
                "\"coordinateSpaceId\":\"other\",\"x\":10,\"y\":10,\"width\":40,\"height\":40")
            .replace("\"coordinateSpaceId\":\"space\",\"point\":{\"x\":20,\"y\":20}",
                "\"coordinateSpaceId\":\"other\",\"point\":{\"x\":20,\"y\":20}")
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = otherSpace) }
    }

    @Test
    fun `moving scenery cannot start the first invocation mid trajectory without a continuation receipt`() {
        val descriptor = motionDescriptor(listOf(VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/renderer.cjs")), 120, 420, 17)
        val backgroundPin = "\"relativePath\":\"references/background.png\",\"sha256\":\"${"3".repeat(64)}\"},\"format\":\"PNG\",\"mediaType\":\"image/png\",\"width\":320"
        val widened = descriptor.requestJson
            .replace("\"id\":\"space\",\"width\":320,\"height\":180", "\"id\":\"space\",\"width\":321,\"height\":180")
            .replace(backgroundPin, backgroundPin.replace("\"width\":320", "\"width\":321"))
            .replace("\"width\":320,\"height\":180}},{\"id\":\"subject\"", "\"width\":321,\"height\":180}},{\"id\":\"subject\"")
            .replace("\"motionCapabilities\":[", "\"sceneryCoverage\":[{\"id\":\"coverage\",\"layerId\":\"background\",\"bounds\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":321,\"height\":180}}],\"motionCapabilities\":[{\"id\":\"scenery-capability\",\"targetType\":\"SCENERY_COVERAGE\",\"targetId\":\"coverage\",\"control\":\"TRANSLATE_X\",\"unit\":\"PIXELS\",\"minimum\":-1,\"maximum\":0,\"defaultValue\":0},")
        val moving = widened.replace(
            "\"controls\":[{\"id\":\"blink\",\"kind\":\"blink\",\"capabilityId\":\"blink-capability\",\"amount\":0.25}]",
            "\"controls\":[],\"scenery\":{\"schema\":\"melotrail-rigid-scenery-v1\",\"viewport\":{\"coordinateSpaceId\":\"space\",\"x\":0,\"y\":0,\"width\":320,\"height\":180},\"mode\":\"moving\",\"camera\":{\"startFrame\":120,\"durationFrames\":300,\"travelXPixels\":1,\"travelYPixels\":0,\"motionBlurSamples\":1,\"shutterFraction\":0},\"planes\":[{\"id\":\"plane\",\"sections\":[{\"coverageId\":\"coverage\",\"worldX\":0,\"worldY\":0,\"startFrame\":120,\"endFrameExclusive\":420}]}]}"
        )
        descriptor.copy(requestJson = moving)
        val late = moving.replace("\"startFrame\":120,\"durationFrames\":300", "\"startFrame\":0,\"durationFrames\":420")
            .replace("\"worldX\":0,\"worldY\":0,\"startFrame\":120,\"endFrameExclusive\":420",
                "\"worldX\":0,\"worldY\":0,\"startFrame\":0,\"endFrameExclusive\":420")
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = late) }
        val startsAfterTrajectory = moving.replace("\"startFrame\":120,\"durationFrames\":300", "\"startFrame\":0,\"durationFrames\":420")
        assertFailsWith<IllegalArgumentException> { descriptor.copy(requestJson = startsAfterTrajectory) }
    }

    @Test
    fun `deduplication never returns another project's job and conflicting IDs never reach backend`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Rejected("fixture finished without work", false) }
        }
        val first = localRequest("first", 'a')
        val coordinator = fixture.coordinator(backend)
        assertEquals("first", accepted(coordinator.submit(first)).job.request.id)
        val duplicate = accepted(coordinator.submit(first.copy(id = "same-project")))
        assertEquals("first", duplicate.job.request.id)
        assertTrue(!duplicate.launchedByCaller)

        val wrongProject = first.copy(id = "other", projectId = "other-project")
        assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(wrongProject)).problem.code)
        val conflictingId = first.copy(projectId = "other-project")
        assertEquals(VideoJobProblemCode.REQUEST_ID_CONFLICT,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(conflictingId)).problem.code)
        val conflictingInputs = first.copy(id = "different-input", input = (first.input as VideoKeyframeGenerationInput).copy(prompt = "changed"))
        assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(conflictingInputs)).problem.code)
        assertEquals(1, backend.submissions.size)
        assertEquals(listOf("first"), fixture.store.snapshot().jobs.map { it.request.id })
    }

    @Test
    fun `identical controlled scenes from different projects receive distinct durable attempts`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            availabilityValue = availabilityValue.copy(supportedInputs = availabilityValue.supportedInputs + VideoGenerationInputKind.CONTROLLED_MOTION)
            submitBehavior = { VideoBackendSubmission.Rejected("fixture finished without work", false) }
        }
        val coordinator = fixture.coordinator(backend)
        val scene = VideoGenerationDependencyPin("scene", HASH_1, "/owned/scene.json")
        val runtime = VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/renderer.cjs")
        fun request(project: String, id: String): VideoGenerationJobRequest {
            val descriptor = motionDescriptor(listOf(runtime), 0, 150, 19, project)
            val input = VideoControlledMotionGenerationInput("same prompt", listOf(scene) + descriptor.runtime.allPins,
                VideoControlledMotionRequest(listOf(scene), 0, 150, 19, descriptor), "same prompt")
            val base = localRequest(id, 'a').copy(projectId = project, input = input)
            return base.copy(requestFingerprint = controlledMotionRequestFingerprint(base.backendId, input, base.modelRequirements))
        }
        val first = request("video-project", "first")
        val second = request("second-project", "second")
        assertNotEquals(first.requestFingerprint, second.requestFingerprint)
        assertEquals("first", accepted(coordinator.submit(first)).job.request.id)
        assertEquals("second", accepted(coordinator.submit(second)).job.request.id)
        assertEquals(2, fixture.store.snapshot().jobs.size)
        assertEquals(2, backend.submissions.size)
        assertEquals("first", accepted(coordinator.submit(request("video-project", "third"))).job.request.id)
        assertEquals(2, backend.submissions.size)
    }

    @Test
    fun `controlled requests are durably deduplicated by fingerprint and backend capability`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        val coordinator = fixture.coordinator(backend)
        val pins = listOf(
            VideoGenerationDependencyPin("scene", HASH_1, "/owned/scene.json"),
            VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/renderer.cjs"),
        )
        fun request(id: String, sceneHash: String = HASH_1) : VideoGenerationJobRequest {
            val actualPins = listOf(pins[0].copy(sha256 = sceneHash), pins[1])
            val input = VideoControlledMotionGenerationInput(
                "exact prompt text", listOf(actualPins[0]) + motionRuntime(actualPins[1]).allPins,
                VideoControlledMotionRequest(
                    preparedPins = listOf(actualPins[0]), startFrame = 120, endFrameExclusive = 420, seed = 17,
                    descriptor = motionDescriptor(listOf(actualPins[1]), 120, 420, 17),
                ),
                "exact prompt text",
            )
            val request = localRequest(id, 'a').copy(input = input)
            return request.copy(requestFingerprint = controlledMotionRequestFingerprint(request.backendId, input, request.modelRequirements))
        }
        val original = request("motion-a")
        backend.availabilityValue = backend.availabilityValue.copy(
            supportedInputs = backend.availabilityValue.supportedInputs + VideoGenerationInputKind.CONTROLLED_MOTION,
        )
        val admitted = accepted(coordinator.submit(original))
        assertTrue(admitted.launchedByCaller)
        val duplicate = accepted(coordinator.submit(request("motion-b")))
        assertEquals(original.id, duplicate.job.request.id)
        assertTrue(!duplicate.launchedByCaller)
        assertEquals(1, backend.submissions.size)
        val otherProject = request("motion-other").let { originalRequest ->
            val originalInput = originalRequest.input as VideoControlledMotionGenerationInput
            val otherInput = originalInput.copy(motion = originalInput.motion.copy(descriptor =
                motionDescriptor(listOf(pins[1]), 120, 420, 17, "other-project")))
            originalRequest.copy(projectId = "other-project", input = otherInput,
                requestFingerprint = controlledMotionRequestFingerprint(originalRequest.backendId, otherInput, originalRequest.modelRequirements))
        }
        assertNotEquals(original.requestFingerprint, otherProject.requestFingerprint)
        // The local slot still belongs to the first project; no cross-project reuse.
        assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(otherProject)).problem.code)
        backend.submitBehavior = { VideoBackendSubmission.Accepted("provider-${it.ownedAttempt.attemptId}") }
        accepted(coordinator.cancel("motion-a"))
        backend.cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        accepted(coordinator.cancel("motion-a"))
        backend.cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        accepted(coordinator.cancel("motion-a"))
        val changedScene = accepted(coordinator.submit(request("motion-c", "f".repeat(64))))
        assertEquals("motion-c", changedScene.job.request.id)
        accepted(coordinator.cancel("motion-c"))
        val changedRuntime = request("motion-d").let { changed ->
            val input = changed.input as VideoControlledMotionGenerationInput
            val runtimePin = input.motion.descriptor.runtime.compositor.copy(sha256 = "e".repeat(64))
            val pinsWithRuntimeChange = input.dependencyPins.map { if (it.id == runtimePin.id) runtimePin else it }
            val changedInput = input.copy(
                dependencyPins = pinsWithRuntimeChange,
                motion = input.motion.copy(descriptor = input.motion.descriptor.copy(runtime = input.motion.descriptor.runtime.copy(compositor = runtimePin))),
            )
            changed.copy(input = changedInput,
                requestFingerprint = controlledMotionRequestFingerprint(changed.backendId, changedInput, changed.modelRequirements))
        }
        val staleRuntime = changedRuntime.copy(id = "motion-stale", requestFingerprint = request("motion-stale").requestFingerprint)
        val staleResult = assertIs<VideoJobResult.Rejected>(coordinator.submit(staleRuntime))
        assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED, staleResult.problem.code)
        assertEquals(2, fixture.store.snapshot().jobs.size)
        val changedRuntimeResult = accepted(coordinator.submit(changedRuntime))
        assertEquals("motion-d", changedRuntimeResult.job.request.id)
        assertEquals(3, backend.submissions.size)
        assertEquals(3, fixture.store.snapshot().jobs.size)

        val unsupportedRequest = request("motion-unsupported", "d".repeat(64))
        val unsupportedBackend = ControlledBackend().apply {
            availabilityValue = availabilityValue.copy(supportedInputs = setOf(VideoGenerationInputKind.KEYFRAME, VideoGenerationInputKind.VIDEO))
        }
        val unavailable = coordinator(fixture.store, unsupportedBackend, AtomicInteger())
        assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED,
            assertIs<VideoJobResult.Rejected>(unavailable.submit(unsupportedRequest)).problem.code)
    }

    @Test
    fun `newline prompt cannot impersonate an additional durable dependency pin`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            availabilityValue = availabilityValue.copy(supportedInputs = availabilityValue.supportedInputs + VideoGenerationInputKind.CONTROLLED_MOTION)
            submitBehavior = { VideoBackendSubmission.Rejected("fixture complete", false) }
        }
        val coordinator = fixture.coordinator(backend)
        val scene = VideoGenerationDependencyPin("scene", HASH_1, "/owned/scene.json")
        val runtime = VideoGenerationDependencyPin("renderer", HASH_2, "/runtime/render.cjs")
        val extra = VideoGenerationDependencyPin("a-extra", "e".repeat(64), "/owned/extra.png")
        val motion = VideoControlledMotionRequest(listOf(scene), 0, 150, 9, motionDescriptor(listOf(runtime), 0, 150, 9))
        fun request(id: String, prompt: String, pins: List<VideoGenerationDependencyPin>): VideoGenerationJobRequest {
            val input = VideoControlledMotionGenerationInput(prompt, pins, motion, "exact primary prompt")
            val base = localRequest(id, 'a').copy(input = input)
            return base.copy(requestFingerprint = controlledMotionRequestFingerprint(base.backendId, input, base.modelRequirements))
        }
        // In the old newline format, the injected line is indistinguishable from the
        // sorted extra pin record, before the shared renderer and scene records.
        val injected = request("motion-injected", "x\npin=${extra.id}:${extra.sha256}:${extra.ownedPath}", listOf(scene) + motion.descriptor.runtime.allPins)
        val distinct = request("motion-extra", "x", listOf(extra, scene) + motion.descriptor.runtime.allPins)
        assertNotEquals(injected.requestFingerprint, distinct.requestFingerprint)
        assertEquals("motion-injected", accepted(coordinator.submit(injected)).job.request.id)
        assertEquals("motion-extra", accepted(coordinator.submit(distinct)).job.request.id)
        assertEquals(2, backend.submissions.size)
        assertEquals(2, fixture.store.snapshot().jobs.size)
    }

    private fun childNames(path: Path): Set<String> = Files.list(path).use { children ->
        children.map { it.fileName.toString() }.toList().toSet()
    }

    @Test
    fun `durable intent precedes submit and restart reconciles the same owned attempt without resubmission`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        backend.submitBehavior = { command ->
            val durable = fixture.store.snapshot().jobs.single().attempts.single()
            assertEquals(command.ownedAttempt.attemptId, durable.id)
            assertEquals(VideoGenerationAttemptStatus.SUBMITTING, durable.status)
            backend.knownWork[durable.id] = "provider-owned-1"
            VideoBackendSubmission.Uncertain("acknowledgement connection closed")
        }
        val first = fixture.coordinator(backend).submit(localRequest("request-a", 'a'))
        val uncertain = accepted(first).attempt!!
        assertEquals(VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN, uncertain.status)
        assertEquals(VideoSubmissionPhase.UNCERTAIN, uncertain.submissionPhase)
        assertNull(uncertain.progressPercent)

        backend.observeBehavior = { owned ->
            VideoBackendObservation.Running(backend.knownWork[owned.attemptId], null)
        }
        val restarted = fixture.coordinator(backend)
        val recovery = restarted.recover().single()
        val active = accepted(recovery).attempt!!
        assertEquals(uncertain.id, active.id)
        assertEquals(uncertain.ownershipToken, active.ownershipToken)
        assertEquals(VideoGenerationAttemptStatus.ACTIVE, active.status)
        assertNull(active.progressPercent)
        assertEquals(1, backend.submissions.size)
    }

    @Test
    fun `unknown observation during pending submission keeps local slot until late acknowledgement is owned and stopped`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        val submitEntered = CountDownLatch(1)
        val releaseSubmit = CountDownLatch(1)
        backend.submitBehavior = { command ->
            submitEntered.countDown()
            assertTrue(releaseSubmit.await(5, TimeUnit.SECONDS))
            backend.knownWork[command.ownedAttempt.attemptId] = "provider-late"
            VideoBackendSubmission.Accepted("provider-late")
        }
        backend.cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        backend.observeBehavior = { VideoBackendObservation.Unknown("submit is still in flight") }
        val pool = Executors.newSingleThreadExecutor()
        val submitting = pool.submit<VideoJobResult> { fixture.coordinator(backend).submit(localRequest("request-a", 'b')) }
        assertTrue(submitEntered.await(5, TimeUnit.SECONDS))
        val durable = fixture.store.snapshot().jobs.single().attempts.single()

        val observation = accepted(fixture.coordinator(backend).reconcile("request-a", durable.id)).attempt!!
        assertEquals(VideoSubmissionPhase.PENDING, observation.submissionPhase)
        assertEquals(durable.ownershipToken, observation.ownershipToken)
        val cancellation = fixture.coordinator(backend).cancel("request-a", durable.id)
        assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, accepted(cancellation).attempt!!.status)
        assertEquals(VideoSubmissionPhase.PENDING, accepted(cancellation).attempt!!.submissionPhase)
        val blocked = fixture.coordinator(backend).submit(localRequest("request-b", 'c'))
        assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY, assertIs<VideoJobResult.Rejected>(blocked).problem.code)

        releaseSubmit.countDown()
        val finished = accepted(submitting.get(5, TimeUnit.SECONDS)).attempt!!
        assertEquals(VideoGenerationAttemptStatus.CANCELLED, finished.status)
        assertEquals("provider-late", finished.providerWorkId)
        assertTrue(backend.cancellations.count { it.attemptId == durable.id } >= 2)
        pool.shutdownNow()
    }

    @Test
    fun `a pre-acknowledgement cancellation response cannot release newly acknowledged work`() {
        val fixture = fixture()
        val backend = ControlledBackend()
        val submitEntered = CountDownLatch(1)
        val releaseSubmit = CountDownLatch(1)
        val cancelEntered = CountDownLatch(1)
        val releaseCancel = CountDownLatch(1)
        backend.submitBehavior = {
            submitEntered.countDown()
            assertTrue(releaseSubmit.await(5, TimeUnit.SECONDS))
            VideoBackendSubmission.Accepted("provider-late")
        }
        backend.cancelBehavior = { owned ->
            if (owned.providerWorkId == null) {
                cancelEntered.countDown()
                assertTrue(releaseCancel.await(5, TimeUnit.SECONDS))
                VideoBackendCancellation.ConfirmedStopped
            } else VideoBackendCancellation.Requested
        }
        val coordinator = fixture.coordinator(backend)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val submitting = pool.submit<VideoJobResult> { coordinator.submit(localRequest("request-a", '5')) }
            assertTrue(submitEntered.await(5, TimeUnit.SECONDS))
            val cancelling = pool.submit<VideoJobResult> { coordinator.cancel("request-a") }
            assertTrue(cancelEntered.await(5, TimeUnit.SECONDS))
            releaseSubmit.countDown()
            val acknowledged = accepted(submitting.get(5, TimeUnit.SECONDS)).attempt!!
            assertEquals(VideoSubmissionPhase.ACKNOWLEDGED, acknowledged.submissionPhase)
            releaseCancel.countDown()
            val stillOwned = accepted(cancelling.get(5, TimeUnit.SECONDS)).attempt!!
            assertEquals(acknowledged.id, stillOwned.id)
            assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, stillOwned.status)
            assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", '6'))).problem.code)
            backend.cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
            assertEquals(VideoGenerationAttemptStatus.CANCELLED, accepted(coordinator.cancel("request-a")).attempt!!.status)
            accepted(coordinator.submit(localRequest("request-b", '6')))
        } finally {
            releaseSubmit.countDown()
            releaseCancel.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `late submit responses preserve acknowledged running and stopped work`() {
        val responses = listOf(
            VideoBackendSubmission.Accepted("provider-work"),
            VideoBackendSubmission.Uncertain("acknowledgement lost"),
            VideoBackendSubmission.Rejected("stale submission rejection", true),
        )
        responses.flatMap { response -> listOf(response to false, response to true) }.forEach { (response, stopBeforeReply) ->
            val fixture = fixture()
            val backend = ControlledBackend()
            val submitEntered = CountDownLatch(1)
            val releaseSubmit = CountDownLatch(1)
            backend.submitBehavior = {
                submitEntered.countDown()
                assertTrue(releaseSubmit.await(5, TimeUnit.SECONDS))
                response
            }
            backend.observeBehavior = { VideoBackendObservation.Running("provider-work", 12) }
            backend.cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
            val coordinator = fixture.coordinator(backend)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val submitting = pool.submit<VideoJobResult> { coordinator.submit(localRequest("request-a", '7')) }
                assertTrue(submitEntered.await(5, TimeUnit.SECONDS))
                val observed = accepted(coordinator.reconcile("request-a")).attempt!!
                assertEquals(VideoSubmissionPhase.ACKNOWLEDGED, observed.submissionPhase)
                val expected = if (stopBeforeReply) {
                    accepted(coordinator.cancel("request-a")).attempt!!.also {
                        assertEquals(VideoGenerationAttemptStatus.CANCELLED, it.status)
                    }
                } else observed
                backend.submitBehavior = { VideoBackendSubmission.Accepted("provider-other") }
                if (stopBeforeReply) accepted(coordinator.submit(localRequest("request-b", '8')))
                releaseSubmit.countDown()
                assertEquals(expected, accepted(submitting.get(5, TimeUnit.SECONDS)).attempt)
                if (!stopBeforeReply) {
                    assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY,
                        assertIs<VideoJobResult.Rejected>(coordinator.submit(localRequest("request-b", '8'))).problem.code)
                }
                assertEquals(1, fixture.store.snapshot().jobs.flatMap { it.attempts }.count { !it.status.isTerminal })
            } finally {
                releaseSubmit.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `rejected submission atomically records no-start and releases its hosted reservation for explicit retry`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Rejected("No work was started", true) }
        }
        val coordinator = fixture.coordinator(backend)
        val rejected = accepted(coordinator.submit(hostedRequest("request-a", '9', 60L, 100L))).attempt!!
        assertEquals(VideoGenerationAttemptStatus.FAILED, rejected.status)
        assertEquals(VideoSubmissionPhase.NOT_STARTED, rejected.submissionPhase)
        assertEquals(0L, rejected.actualCostMicros)
        assertTrue(rejected.retryable)
        backend.submitBehavior = { VideoBackendSubmission.Accepted("provider-retry") }
        val retry = accepted(coordinator.retry("request-a")).attempt!!
        assertNotEquals(rejected.id, retry.id)
        assertEquals(2, backend.submissions.size)
    }

    @Test
    fun `confirmed stop without acknowledgement retains ambiguous hosted reservation until no-start tombstone`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Uncertain("provider may still accept") }
            cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        }
        val coordinator = fixture.coordinator(backend)
        val original = accepted(coordinator.submit(hostedRequest("request-a", 'a', 60L, 100L))).attempt!!
        val cancelled = accepted(coordinator.cancel("request-a")).attempt!!
        assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, cancelled.status)
        assertEquals(original.ownershipToken, cancelled.ownershipToken)
        assertNull(cancelled.actualCostMicros)
        assertEquals(VideoJobProblemCode.RETRY_NOT_ALLOWED,
            assertIs<VideoJobResult.Rejected>(coordinator.retry("request-a")).problem.code)
        assertEquals(VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(hostedRequest("request-b", 'b', 60L, 100L))).problem.code)
        backend.observeBehavior = { VideoBackendObservation.NotStarted }
        val stopped = accepted(coordinator.reconcile("request-a")).attempt!!
        assertEquals(VideoGenerationAttemptStatus.CANCELLED, stopped.status)
        assertEquals(VideoSubmissionPhase.NOT_STARTED, stopped.submissionPhase)
        assertEquals(0L, stopped.actualCostMicros)
        accepted(coordinator.retry("request-a"))
        assertEquals(2, backend.submissions.size)
    }

    @Test
    fun `hosted reservation is atomic across store instances and unknown submission retains maximum cost`() {
        val fixture = fixture()
        val storeAlias = VideoJobStore(fixture.root.resolve("jobs/../jobs"), DOMAIN, listOf(fixture.midiRoot))
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Uncertain("provider may have charged the request") }
            observeBehavior = { VideoBackendObservation.Unknown("provider lookup timed out") }
        }
        val ids = AtomicInteger()
        val first = coordinator(fixture.store, backend, ids)
        val second = coordinator(storeAlias, backend, ids)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val results = listOf(
            pool.submit<VideoJobResult> { start.await(); first.submit(hostedRequest("request-a", 'd', 60L, 100L)) },
            pool.submit<VideoJobResult> { start.await(); second.submit(hostedRequest("request-b", 'e', 60L, 100L)) },
        )
        start.countDown()
        val completed = results.map { it.get(5, TimeUnit.SECONDS) }
        assertEquals(1, completed.count { it is VideoJobResult.Accepted })
        assertEquals(1, completed.count { (it as? VideoJobResult.Rejected)?.problem?.code == VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED })
        assertEquals(1, backend.submissions.size)
        assertEquals(VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN, fixture.store.snapshot().jobs.single().attempts.single().status)
        first.recover()
        assertEquals(1, backend.submissions.size)
        val stillBlocked = second.submit(hostedRequest("request-c", 'f', 60L, 100L))
        assertEquals(VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED, assertIs<VideoJobResult.Rejected>(stillBlocked).problem.code)
        pool.shutdownNow()
    }

    @Test
    fun `retry is explicit bounded and starts only after conclusive no-work reconciliation`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Uncertain("submit outcome unknown") }
            observeBehavior = { VideoBackendObservation.Unknown("lookup outcome unknown") }
        }
        val coordinator = fixture.coordinator(backend)
        coordinator.submit(localRequest("request-a", '1', maximumAttempts = 2))
        coordinator.recover()
        assertEquals(1, backend.submissions.size)
        val premature = coordinator.retry("request-a")
        assertEquals(VideoJobProblemCode.RETRY_NOT_ALLOWED, assertIs<VideoJobResult.Rejected>(premature).problem.code)

        backend.observeBehavior = { VideoBackendObservation.NotStarted }
        val failed = accepted(coordinator.reconcile("request-a")).attempt!!
        assertEquals(VideoGenerationAttemptStatus.FAILED, failed.status)
        assertEquals(VideoSubmissionPhase.NOT_STARTED, failed.submissionPhase)
        assertTrue(failed.retryable)
        backend.submitBehavior = { VideoBackendSubmission.Accepted("provider-retry") }
        val retry = accepted(coordinator.retry("request-a"))
        assertEquals(2, retry.job.attempts.size)
        assertEquals(2, backend.submissions.size)
        assertNotEquals(retry.job.attempts[0].id, retry.job.attempts[1].id)
        assertNotEquals(retry.job.attempts[0].ownershipToken, retry.job.attempts[1].ownershipToken)
        val exhaustedAfterStop = run {
            backend.observeBehavior = { VideoBackendObservation.Failed("provider-retry", "failed", true) }
            coordinator.reconcile("request-a")
            coordinator.retry("request-a")
        }
        assertEquals(VideoJobProblemCode.ATTEMPT_LIMIT_REACHED, assertIs<VideoJobResult.Rejected>(exhaustedAfterStop).problem.code)
    }

    @Test
    fun `late completion is retained on its attempt and cannot replace the newer output`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { command -> VideoBackendSubmission.Accepted("work-${command.ownedAttempt.attemptId}") }
            cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        }
        val coordinator = fixture.coordinator(backend)
        val first = accepted(coordinator.submit(localRequest("request-a", '2', maximumAttempts = 2))).attempt!!
        assertEquals(VideoGenerationAttemptStatus.CANCELLED, accepted(coordinator.cancel("request-a", first.id)).attempt!!.status)
        val second = accepted(coordinator.retry("request-a")).attempt!!

        backend.observeBehavior = { owned ->
            if (owned.attemptId == first.id) {
                VideoBackendObservation.Completed(owned.providerWorkId, VideoBackendOutput("old-output", "staged/old.mp4", HASH_1, 10L))
            } else {
                VideoBackendObservation.Completed(owned.providerWorkId, VideoBackendOutput("new-output", "staged/new.mp4", HASH_2, 11L))
            }
        }
        val oldCompletion = accepted(coordinator.reconcile("request-a", first.id)).job
        assertEquals(1, oldCompletion.outputs.size)
        assertNull(oldCompletion.currentOutputId)
        val newCompletion = accepted(coordinator.reconcile("request-a", second.id)).job
        assertEquals(2, newCompletion.outputs.size)
        assertEquals("new-output", newCompletion.outputs.single { it.id == newCompletion.currentOutputId }.backendOutputId)
        assertEquals("old-output", newCompletion.outputs.single { it.attemptId == first.id }.backendOutputId)
    }

    @Test
    fun `stale running observation cannot regress a newer confirmed cancellation`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Accepted("provider-work") }
            cancelBehavior = { VideoBackendCancellation.ConfirmedStopped }
        }
        val coordinator = fixture.coordinator(backend)
        val attempt = accepted(coordinator.submit(localRequest("request-a", '3'))).attempt!!
        val observeEntered = CountDownLatch(1)
        val releaseObserve = CountDownLatch(1)
        backend.observeBehavior = {
            observeEntered.countDown()
            assertTrue(releaseObserve.await(5, TimeUnit.SECONDS))
            VideoBackendObservation.Running("provider-work", 42)
        }
        val pool = Executors.newSingleThreadExecutor()
        val stale = pool.submit<VideoJobResult> { coordinator.reconcile("request-a", attempt.id) }
        assertTrue(observeEntered.await(5, TimeUnit.SECONDS))
        assertEquals(VideoGenerationAttemptStatus.CANCELLED, accepted(coordinator.cancel("request-a", attempt.id)).attempt!!.status)
        releaseObserve.countDown()
        assertEquals(VideoGenerationAttemptStatus.CANCELLED, accepted(stale.get(5, TimeUnit.SECONDS)).attempt!!.status)
        pool.shutdownNow()
    }

    @Test
    fun `setup capability is declarative and unavailable models prevent durable admission`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            availabilityValue = availabilityValue.copy(availableModels = emptyList())
        }
        val setup = object : VideoSetupCapabilityPort {
            override val backendId = BACKEND
            override fun describeSetup() = VideoBackendSetup(
                BACKEND,
                VideoBackendAvailabilityStatus.SETUP_REQUIRED,
                listOf(VideoSetupRequirement("model", "Pinned model is missing", false)),
                listOf(VideoSetupAction("choose-model", VideoSetupActionKind.SELECT_MODEL, "Choose model", "Select an installed compatible model.")),
            )
        }
        val coordinator = fixture.coordinator(backend, setup)
        assertEquals(VideoSetupActionKind.SELECT_MODEL, coordinator.setup(BACKEND)!!.actions.single().kind)
        val result = coordinator.submit(localRequest("request-a", '4'))
        assertEquals(VideoJobProblemCode.MODEL_REQUIREMENTS_UNMET, assertIs<VideoJobResult.Rejected>(result).problem.code)
        assertTrue(fixture.store.snapshot().jobs.isEmpty())
        assertTrue(backend.submissions.isEmpty())
    }

    @Test
    fun `ComfyUI workflow bindings and owned pins survive uncertain submission for restart reconciliation`() {
        val fixture = fixture()
        val backend = ControlledBackend().apply {
            submitBehavior = { VideoBackendSubmission.Uncertain("acknowledgement lost") }
        }
        val binding = VideoComfyWorkflowRequest(
            workflowDependencyId = "workflow",
            promptInput = VideoComfyInputSlot("1", "text"),
            referenceInputs = listOf(VideoComfyReferenceInput("reference", VideoComfyInputSlot("2", "image"), "reference.png")),
            output = VideoComfyOutputBinding("3", setOf("mp4")),
        )
        val request = localRequest("request-comfy", 'c').copy(
            input = VideoKeyframeGenerationInput(
                prompt = "Treat ../graph.json as prose",
                dependencyPins = listOf(
                    VideoGenerationDependencyPin("workflow", HASH_1, fixture.root.resolve("owned-workflow.json").toAbsolutePath().toString()),
                    VideoGenerationDependencyPin("reference", HASH_2, fixture.root.resolve("owned-reference.png").toAbsolutePath().toString()),
                ),
                width = 640,
                height = 360,
                comfyWorkflow = binding,
            ),
        )

        val uncertain = accepted(fixture.coordinator(backend).submit(request)).attempt!!
        assertEquals(VideoSubmissionPhase.UNCERTAIN, uncertain.submissionPhase)
        val reopened = VideoJobStore(fixture.root.resolve("jobs"), DOMAIN, listOf(fixture.midiRoot)).snapshot().jobs.single().request
        assertEquals(request.input, reopened.input)
        assertEquals(binding, reopened.input.comfyWorkflow)
        assertEquals("../graph.json", reopened.input.prompt.substringAfter("Treat ").substringBefore(" as prose"))
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("melotrail-video-jobs-")
        val midi = Files.createDirectory(root.resolve("midi-protected"))
        val jobsRoot = root.resolve("jobs")
        val store = VideoJobStore(jobsRoot, DOMAIN, listOf(midi))
        return Fixture(root, midi, store)
    }

    private fun Fixture.coordinator(
        backend: ControlledBackend,
        setup: VideoSetupCapabilityPort? = null,
    ): VideoJobCoordinator = coordinator(store, backend, ids, setup)

    private fun coordinator(
        store: VideoJobPersistence,
        backend: ControlledBackend,
        ids: AtomicInteger,
        setup: VideoSetupCapabilityPort? = null,
    ) = VideoJobCoordinator(
        DOMAIN,
        store,
        listOf(backend),
        listOfNotNull(setup),
        FIXED_CLOCK,
        attemptIdFactory = { "attempt-${ids.incrementAndGet()}" },
        ownershipTokenFactory = { "owner-${ids.incrementAndGet()}" },
        outputIdFactory = { "output-${ids.incrementAndGet()}" },
    )

    private fun afterWrite(
        store: VideoJobPersistence,
        action: (VideoJobLedger) -> Unit,
    ): VideoJobPersistence = object : VideoJobPersistence {
        override fun loadOrCreate(admissionDomainId: String, createdAt: String) = store.loadOrCreate(admissionDomainId, createdAt)

        override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger {
            val persisted = store.compareAndSet(expectedRevision, replacement)
            action(persisted)
            return persisted
        }
    }

    private fun localRequest(id: String, hashCharacter: Char, maximumAttempts: Int = 3) = VideoGenerationJobRequest(
        id = id,
        projectId = "video-project",
        backendId = BACKEND,
        modelRequirements = listOf(VideoModelRequirement(MODEL, "1", HASH_0)),
        input = VideoKeyframeGenerationInput(
            prompt = "An owned fixture scene",
            dependencyPins = listOf(VideoGenerationDependencyPin("reference", hashCharacter.toString().repeat(64))),
            width = 640,
            height = 360,
        ),
        requestFingerprint = hashCharacter.toString().repeat(64),
        maximumAttempts = maximumAttempts,
        createdAt = NOW,
        execution = VideoLocalExecutionPolicy(60_000L, 8_000_000_000L, 4_000_000_000L),
    )

    private fun hostedRequest(id: String, hashCharacter: Char, estimate: Long, cap: Long) = localRequest(id, hashCharacter).copy(
        execution = VideoHostedExecutionPolicy(
            budgetId = "owned-batch",
            currency = "USD",
            estimatedMaximumCostMicros = estimate,
            estimateCreatedAt = "2026-09-13T23:00:00Z",
            estimateExpiresAt = "2026-09-14T01:00:00Z",
            authorizedSpendCapMicros = cap,
        ),
    )

    private fun accepted(result: VideoJobResult): VideoJobResult.Accepted = assertIs(result)

    private data class Fixture(
        val root: Path,
        val midiRoot: Path,
        val store: VideoJobStore,
        val ids: AtomicInteger = AtomicInteger(),
    )

    private class ControlledBackend : VideoGenerationBackendPort {
        override val backendId: String = BACKEND
        @Volatile var availabilityValue = VideoBackendAvailability(
            BACKEND,
            VideoBackendAvailabilityStatus.AVAILABLE,
            NOW,
            setOf(VideoGenerationInputKind.KEYFRAME, VideoGenerationInputKind.VIDEO),
            listOf(VideoAvailableModel(MODEL, "1", HASH_0)),
            "Available for owned fake requests.",
        )
        @Volatile var submitBehavior: (VideoBackendSubmissionCommand) -> VideoBackendSubmission = {
            VideoBackendSubmission.Accepted("provider-${it.ownedAttempt.attemptId}")
        }
        @Volatile var observeBehavior: (VideoOwnedBackendAttempt) -> VideoBackendObservation = {
            VideoBackendObservation.Running(it.providerWorkId, null)
        }
        @Volatile var cancelBehavior: (VideoOwnedBackendAttempt) -> VideoBackendCancellation = {
            VideoBackendCancellation.Requested
        }
        val submissions = Collections.synchronizedList(mutableListOf<VideoBackendSubmissionCommand>())
        val cancellations = Collections.synchronizedList(mutableListOf<VideoOwnedBackendAttempt>())
        val observations = Collections.synchronizedList(mutableListOf<VideoOwnedBackendAttempt>())
        val knownWork = ConcurrentHashMap<String, String>()

        override fun availability(): VideoBackendAvailability = availabilityValue
        override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
            submissions += command
            return submitBehavior(command)
        }
        override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation {
            observations += ownedAttempt
            return observeBehavior(ownedAttempt)
        }
        override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation {
            cancellations += ownedAttempt
            return cancelBehavior(ownedAttempt)
        }
    }

    companion object {
        private const val DOMAIN = "video-admission"
        private const val BACKEND = "fake-backend"
        private const val MODEL = "fake-model"
        private const val NOW = "2026-09-14T00:00:00Z"
        private val FIXED_CLOCK = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        private val HASH_0 = "0".repeat(64)
        private val HASH_1 = "1".repeat(64)
        private val HASH_2 = "2".repeat(64)
    }
}
