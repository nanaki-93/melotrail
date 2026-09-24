package app.melotrail.video

import app.melotrail.video.adapter.VideoControlledMediaStage
import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import kotlinx.serialization.json.*
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

    @Test fun `production stage stages only the persisted descriptor after claim and retains failed attempt evidence`() {
        val root = Files.createTempDirectory("controlled-render-stage-").toRealPath()
        val project = Files.createDirectory(root.resolve("project"))
        val outputs = Files.createDirectory(project.resolve("attempts"))
        val domain = "domain-${root.fileName}"
        val store = VideoJobStore(root.resolve("jobs"), domain, listOf(Files.createDirectory(root.resolve("midi"))))
        val stage = VideoControlledMediaStage(store, domain, project, outputs, clock = clock)
        val input = input() // deliberately absent runtime pins: fail before native setup
        val request = VideoGenerationJobRequest("request", "video-project", stage.backendId, emptyList(), input,
            controlledMotionRequestFingerprint(stage.backendId, input, emptyList(), 2), 2, Instant.now(clock).toString(), input.media.execution)
        val coordinator = VideoJobCoordinator(domain, store, listOf(stage), clock = clock,
            attemptIdFactory = { "owned-attempt" }, ownershipTokenFactory = { "owned-token" })
        assertTrue(Files.list(outputs).use { it.count() } == 0L)
        val accepted = assertIs<VideoJobResult.Accepted>(coordinator.submit(request))
        val attempt = accepted.attempt!!
        val owned = VideoOwnedBackendAttempt(request.id, request.requestFingerprint, attempt.id, attempt.ownershipToken,
            stage.backendId, attempt.providerWorkId)
        val evidence = outputs.resolve(attempt.id).resolve("request.json")
        repeat(100) {
            if (Files.isRegularFile(evidence)) return@repeat
            Thread.sleep(10)
        }
        assertEquals(input.motion.descriptor.requestJson, Files.readString(evidence))
        repeat(100) {
            val observation = stage.observe(owned)
            if (observation is VideoBackendObservation.Unknown) {
                assertContains(observation.detail, "pin changed")
                assertEquals(input.motion.descriptor.requestJson, Files.readString(evidence))
                assertEquals(1L, Files.list(outputs).use { it.count() })
                return
            }
            Thread.sleep(10)
        }
        fail("Controlled preflight did not reach its retained failure evidence")
    }

    @Test fun `claimed stage refuses insufficient memory or total staging reservation before native setup`() {
        for (resource in listOf("memory", "disk")) {
            val root = Files.createTempDirectory("controlled-resource-$resource-").toRealPath()
            val project = Files.createDirectory(root.resolve("project"))
            val outputs = Files.createDirectory(project.resolve("attempts"))
            val domain = "domain-${root.fileName}"
            val store = VideoJobStore(root.resolve("jobs"), domain, listOf(Files.createDirectory(root.resolve("midi"))))
            val stage = VideoControlledMediaStage(store, domain, project, outputs, clock = clock)
            val ffprobe = root.resolve("ffprobe")
            val manifest = root.resolve("manifest.json")
            Files.writeString(ffprobe, "owned probe")
            Files.writeString(manifest, "owned manifest")
            val original = input()
            val runtime = original.motion.descriptor.runtime.copy(
                ffprobe = VideoGenerationDependencyPin("ffprobe", digest(ffprobe), ffprobe.toString()),
                mediaManifest = VideoGenerationDependencyPin("media-manifest", digest(manifest), manifest.toString()))
            val motion = original.motion.copy(descriptor = original.motion.descriptor.copy(runtime = runtime))
            val execution = if (resource == "memory") VideoLocalExecutionPolicy(60_000, Long.MAX_VALUE, 4_000_000_000L)
                else VideoLocalExecutionPolicy(60_000, 1_000_000, Long.MAX_VALUE - 1)
            val media = if (resource == "memory") motionMedia(execution) else VideoControlledMediaBinding(
                execution, Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, 1, 1, 1, "image2-h264-yuv420p-silent-square-v1")
            val input = original.copy(dependencyPins = runtime.allPins + motion.preparedPins, motion = motion, media = media)
            val request = VideoGenerationJobRequest("request", "video-project", stage.backendId, emptyList(), input,
                controlledMotionRequestFingerprint(stage.backendId, input, emptyList(), 2), 2, Instant.now(clock).toString(), execution)
            val coordinator = VideoJobCoordinator(domain, store, listOf(stage), clock = clock,
                attemptIdFactory = { "attempt" }, ownershipTokenFactory = { "owner" })
            val attempt = assertIs<VideoJobResult.Accepted>(coordinator.submit(request)).attempt!!
            val owned = VideoOwnedBackendAttempt(request.id, request.requestFingerprint, attempt.id,
                attempt.ownershipToken, stage.backendId, attempt.providerWorkId)
            val until = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            var observation: VideoBackendObservation
            do {
                observation = stage.observe(owned)
                if (observation is VideoBackendObservation.Running) Thread.sleep(10)
            } while (observation is VideoBackendObservation.Running && System.nanoTime() < until)
            val failed = assertIs<VideoBackendObservation.Unknown>(observation)
            assertContains(failed.detail, if (resource == "memory") "memory admission" else "disk admission")
            val evidence = outputs.resolve(attempt.id)
            assertEquals(motion.descriptor.requestJson, Files.readString(evidence.resolve("request.json")))
            assertEquals(listOf("request.json"), Files.list(evidence).use { entries ->
                entries.map { it.fileName.toString() }.sorted().toList()
            }) // No Node/FFmpeg probe, chunk, or native working directory was launched.
        }
    }

    @Test fun `claimed production stage verifies complete absolute preview ranges and retains chunk receipts`() {
        // The real importer supplies artwork/scene pins; the renderer launches the pinned
        // Node/Canvas compositor. No fake backend or caller-authored receipt is involved.
        VideoMotionDescriptorFixtureTest().`emit deterministic production importer bundles for controlled motion pixel comparisons`()
        val fixtures = Path.of(System.getProperty("user.dir"), "build/video-motion-fixtures").toRealPath()
        val script = Path.of(System.getProperty("user.dir"), "tools/video-motion/render.cjs").toRealPath()
        val canvas = script.parent.resolve("node_modules/@napi-rs/canvas/package.json").toRealPath()
        val node = Path.of("/opt/homebrew/bin/node").toRealPath()
        val version = Json.parseToJsonElement(Files.readString(canvas)).jsonObject.getValue("version").jsonPrimitive.content
        val root = Files.createTempDirectory("controlled-stage-ranges-").toRealPath()
        val ffmpeg = root.resolve("ffmpeg")
        Files.writeString(ffmpeg, "#!/bin/sh\nprintf 'owned ffmpeg probe\\n'\n")
        assertTrue(ffmpeg.toFile().setExecutable(true))
        val ffprobe = root.resolve("ffprobe")
        Files.writeString(ffprobe, "owned ffprobe pin")
        val manifest = root.resolve("media-manifest.json")
        Files.writeString(manifest, "owned media manifest")
        fun pin(id: String, path: Path) = VideoGenerationDependencyPin(id, digest(path), path.toString())
        val artifacts = (listOf("index.js", "js-binding.js", "geometry.js", "load-image.js")
            .mapNotNull { canvas.parent.resolve(it).takeIf(Files::exists) } +
            listOfNotNull(canvas.parent.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node").takeIf(Files::exists)))
            .mapIndexed { index, path -> pin("canvas-artifact-$index", path) }
        val runtime = VideoControlledMotionRuntimeBinding(pin("node", node), pin("compositor", script),
            pin("scenery", script.parent.resolve("scenery.cjs")), pin("canvas-manifest", canvas), artifacts,
            pin("ffmpeg", ffmpeg), pin("ffprobe", ffprobe), pin("media-manifest", manifest), version)
        for ((start, count) in listOf(11 to 150, 0 to 600, 0 to 900, 0 to 601)) {
            val fixture = fixtures.resolve(if (count == 150) "unit-scale" else "wide-scenery")
            val template = Json.parseToJsonElement(Files.readString(fixture.resolve("request.json"))).jsonObject
            val sourceProject = fixture.resolve("project").toRealPath()
            val project = Files.createDirectory(root.resolve("project-$count"))
            Files.walk(sourceProject).use { paths -> paths.forEach { source ->
                val target = project.resolve(sourceProject.relativize(source).toString())
                if (source != sourceProject) {
                    if (Files.isDirectory(source)) Files.createDirectories(target)
                    else Files.copy(source, target)
                }
            } }
            val output = Files.createDirectory(project.resolve("stage-output"))
            val domain = "stage-${root.fileName}-$count"
            val store = VideoJobStore(root.resolve("jobs-$count"), domain, listOf(Files.createDirectory(root.resolve("midi-$count"))))
            // Exercise the native per-process RSS guard independent of concurrent
            // host free-memory pressure; the separate admission test checks capacity.
            val stage = VideoControlledMediaStage(store, domain, project, output, clock = clock,
                hostFreeMemoryBytes = { Long.MAX_VALUE })
            val json = JsonObject(template.toMutableMap().apply {
                put("frameRange", buildJsonObject { put("startFrame", start); put("frameCount", count) })
            }).toString()
            val descriptor = VideoControlledMotionDescriptor("video-project", "1".repeat(64), json, runtime)
            val prepared = pin("prepared-scene", project.resolve("request.json").takeIf(Files::exists)
                ?: fixture.resolve("request.json"))
            val motion = VideoControlledMotionRequest(listOf(prepared), start.toLong(), (start + count).toLong(), 73, descriptor)
            val memoryGrowth = count == 601
            val policy = VideoLocalExecutionPolicy(180_000L, if (memoryGrowth) 160_000_000L else 512_000_000L,
                4_000_000_000L) // Node/Canvas legitimately exceeds 32 MB RSS
            val media = motionMedia(policy)
            val input = VideoControlledMotionGenerationInput("Backend guidance", runtime.allPins + prepared, motion,
                "Exact authored text", media)
            val request = VideoGenerationJobRequest("request-$count", descriptor.projectId, stage.backendId, emptyList(), input,
                controlledMotionRequestFingerprint(stage.backendId, input, emptyList(), 2), 2, Instant.now(clock).toString(), policy)
            val coordinator = VideoJobCoordinator(domain, store, listOf(stage), clock = clock,
                attemptIdFactory = { "attempt-$count" }, ownershipTokenFactory = { "owner-$count" })
            val attempt = assertIs<VideoJobResult.Accepted>(coordinator.submit(request)).attempt!!
            val owned = VideoOwnedBackendAttempt(request.id, request.requestFingerprint, attempt.id,
                attempt.ownershipToken, stage.backendId, attempt.providerWorkId)
            val evidence = output.resolve(attempt.id)
            val until = System.nanoTime() + Duration.ofMinutes(3).toNanos()
            var observation: VideoBackendObservation
            do {
                observation = stage.observe(owned)
                if (observation is VideoBackendObservation.Running) Thread.sleep(50)
            } while (observation is VideoBackendObservation.Running && System.nanoTime() < until)
            val finished = assertIs<VideoBackendObservation.Unknown>(observation)
            if (memoryGrowth) {
                assertContains(finished.detail, "memory limit", message = "Native memory growth must terminate the claimed attempt")
                assertEquals(json, Files.readString(evidence.resolve("request.json")))
                continue
            }
            assertTrue(finished.detail.contains("Verified frames retained"), "range $start/$count: ${finished.detail}")
            assertEquals(json, Files.readString(evidence.resolve("request.json")))
            var next = start
            val sizes = if (count == 150) listOf(150) else List(count / 300) { 300 }
            sizes.forEachIndexed { index, size ->
                val chunk = evidence.resolve("motion-$start-$index")
                val namedReceipt = chunk.resolve("render-receipt-${next.toString().padStart(8, '0')}-${size.toString().padStart(8, '0')}.json")
                val receipt = Json.parseToJsonElement(Files.readString(
                    if (Files.isRegularFile(namedReceipt)) namedReceipt else chunk.resolve("render-receipt.json"))).jsonObject
                assertEquals(next, receipt.getValue("frameRange").jsonObject.getValue("startFrame").jsonPrimitive.int)
                val frames = receipt.getValue("frames").jsonArray
                assertEquals(size, frames.size)
                frames.forEachIndexed { offset, record ->
                    val absolute = next + offset
                    assertEquals(absolute, record.jsonObject.getValue("frame").jsonPrimitive.int)
                    val file = record.jsonObject.getValue("file").jsonPrimitive.content
                    assertEquals("frame-${absolute.toString().padStart(8, '0')}.png", file)
                    assertEquals(record.jsonObject.getValue("sha256").jsonPrimitive.content, digest(chunk.resolve(file)))
                }
                if (index > 0 && "scenery" in template) assertTrue(Files.readString(evidence.resolve(".motion-job-$start-$index/request.json"))
                    .contains("\"initialState\""), "scenery continuation must enter each subsequent invocation")
                next += size
            }
            assertEquals(start + count, next)
            assertEquals(sizes.size, Files.list(evidence).use { entries -> entries.filter { it.fileName.toString().startsWith("motion-") }.count().toInt() })
        }
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private fun input(): VideoControlledMotionGenerationInput {
        val pin = VideoGenerationDependencyPin("compositor", "a".repeat(64), "/runtime/compositor")
        val descriptor = motionDescriptor(listOf(pin), 0, 150, 12)
        val prepared = pin.copy(id = "prepared-scene", ownedPath = "/runtime/prepared-scene")
        return VideoControlledMotionGenerationInput("Backend guidance", descriptor.runtime.allPins + prepared,
            VideoControlledMotionRequest(listOf(prepared), 0, 150, 12, descriptor), "Exact authored text", motionMedia())
    }
}
