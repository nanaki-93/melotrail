package app.melotrail.video

import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoControlledMediaStage
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.adapter.VideoMediaProcessRequest
import app.melotrail.video.adapter.VideoMediaProcessException
import app.melotrail.video.adapter.VideoMediaProcessFailure
import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.domain.VideoControlledStage
import java.time.Duration
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoGenerationOutput
import java.time.Instant
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPreviewHostCheckTest {
    private data class Fixture(val root: Path, val tools: Path, val node: Path, val canvas: Path,
        val script: Path, val output: Path, val hashes: Pair<String, String>) {
        var launches = 0
        fun validate(budget: PreviewBudget = PreviewBudget(), output: Path = this.output,
            canvas: Path = this.canvas, free: Long = 8L * 1024 * 1024 * 1024,
            host: String = "Mac OS X/arm64"): PreviewConfiguration = PreviewPreflight.validate(
            tools, node, canvas, output, budget, host, { free }, { request ->
                launches++
                when (request.executable.fileName.toString()) {
                    "node" -> "v25.8.2"
                    "ffmpeg" -> "ffmpeg version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.joinToString(" ")
                    "ffprobe" -> "ffprobe version 9.0.1"
                    else -> error("Unexpected executable")
                }.let { text -> if (request.arguments.contains("-demuxers")) " D  image2 image sequence"
                    else if (request.arguments.contains("-encoders")) " V  h264_videotoolbox VideoToolbox"
                    else if (request.arguments.contains("-muxers")) " E  mp4 MP4" else text }
            }, hashes, script, { 8L * 1024 * 1024 * 1024 }, root)
    }
    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("preview-host-check-").toRealPath()
        val tools = Files.createDirectory(root.resolve("tools"))
        val node = Files.writeString(root.resolve("node"), "owned node")
        val ffmpeg = Files.writeString(tools.resolve("ffmpeg"), "owned media binary")
        val ffprobe = Files.writeString(tools.resolve("ffprobe"), "owned probe binary")
        listOf(node, ffmpeg, ffprobe).forEach { it.toFile().setExecutable(true) }
        val hashes = PreviewPreflight.sha(ffmpeg) to PreviewPreflight.sha(ffprobe)
        Files.writeString(tools.resolve(VideoMediaProbe.MANIFEST_NAME), """{"schema":"melotrail-video-media-tools","version":1,
          "distributionId":"${VideoMediaProbe.DISTRIBUTION_ID}","installation":"${VideoMediaProbe.INSTALLATION_STRATEGY}",
          "sourceUrl":"${VideoMediaProbe.SOURCE_URL}","sourceRevision":"${VideoMediaProbe.SOURCE_REVISION}",
          "sourceSha256":"${VideoMediaProbe.SOURCE_SHA256}","buildOptions":${VideoMediaProbe.REQUIRED_BUILD_OPTIONS.map { "\"$it\"" }},
          "notices":["fixture"],"ffmpegSha256":"${hashes.first}","ffprobeSha256":"${hashes.second}"}""")
        val script = Files.writeString(root.resolve("render.cjs"), "owned script")
        Files.writeString(root.resolve("scenery.cjs"), "owned scenery")
        val packageDir = Files.createDirectories(root.resolve("node_modules/@napi-rs/canvas"))
        val canvas = Files.writeString(packageDir.resolve("package.json"), """{"name":"@napi-rs/canvas","version":"0.1.80"}""")
        for (name in listOf("index.js", "js-binding.js", "geometry.js", "load-image.js")) Files.writeString(packageDir.resolve(name), "owned $name")
        Files.createDirectories(packageDir.parent.resolve("canvas-darwin-arm64"))
        Files.writeString(packageDir.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node"), "owned canvas")
        return Fixture(root, tools, node, canvas, script, root.resolve("fresh"), hashes)
    }

    @Test fun `preflight validates owned pins and finite persisted per-job policy without launching a job`() {
        val f = fixture()
        val ready = f.validate()
        assertEquals(12, ready.runtime.size)
        assertEquals(6, f.launches, "Only pinned version/capability probes may run during preflight")
        assertFalse(Files.exists(f.output))
        assertEquals(PreviewBudget().policy(), ready.budget.policy())
        assertEquals(1, ready.budget.concurrentNativeProcesses)
        assertEquals(512L * 1024 * 1024, ready.budget.diskReserveBytes)
        val changed = Files.readAllBytes(f.tools.resolve("ffmpeg"))
        Files.writeString(f.tools.resolve("ffmpeg"), "mutated")
        assertTrue(assertFailsWith<IllegalArgumentException> { f.validate() }.message!!.contains("digest"))
        Files.write(f.tools.resolve("ffmpeg"), changed)
        val canvasBytes = Files.readAllBytes(f.canvas)
        Files.writeString(f.canvas, "{\"name\":\"other\",\"version\":\"0.1.80\"}")
        assertTrue(assertFailsWith<IllegalArgumentException> { f.validate() }.message!!.contains("Canvas"))
        Files.write(f.canvas, canvasBytes)
        Files.delete(f.tools.resolve(VideoMediaProbe.MANIFEST_NAME))
        assertFailsWith<Exception> { f.validate() }
        assertFalse(Files.exists(f.output))
    }

    @Test fun `fixture process cannot execute before durable controlled admission and default probe is opt in`() {
        assertFailsWith<IllegalArgumentException> { VideoPreviewHostCheck.main(emptyArray()) }
        val f = fixture()
        assertFalse(Files.exists(f.output))
        val config = f.validate()
        var processes = 0
        val failed = assertFailsWith<Exception> {
            PreviewProductionRun.run(config, runProcess = { request, _ ->
                processes++
                assertTrue(Files.exists(f.output.resolve("job-150-budget.json")))
                val job = VideoJobStore(f.output.resolve("jobs"), "preview-proof", listOf(f.root.resolve("midi"))).snapshot().jobs.single()
                assertEquals("preview-150", job.request.id)
                assertEquals(VideoControlledMediaStage.BACKEND_ID, job.request.backendId)
                assertTrue(job.attempts.single().controlledEvidence?.stage in
                    setOf(VideoControlledStage.RENDERING, VideoControlledStage.ENCODING))
                // A supervised child failure terminates the claimed job without publication.
                throw VideoMediaProcessException(VideoMediaProcessFailure.NONZERO_EXIT, "fixture process stopped")
            }, freeMemory = { Long.MAX_VALUE }, sample = { _, _ -> 0 })
        }
        assertTrue(processes > 0, "Fixture must reach the claimed renderer process boundary: $failed")
        val attempt = VideoJobStore(f.output.resolve("jobs"), "preview-proof", listOf(f.root.resolve("midi")))
            .snapshot().jobs.single().attempts.single()
        assertEquals(VideoGenerationAttemptStatus.FAILED, attempt.status)
        assertEquals(VideoControlledStage.FAILED, attempt.controlledEvidence?.stage)
        assertTrue(attempt.failure.orEmpty().contains("fixture process stopped"))
        assertTrue(Files.exists(f.output.resolve("job-150-failure.txt")))
        assertFalse(Files.exists(f.output.resolve("job-600-budget.json")))
        assertFalse(Files.exists(f.output.resolve("project/takes")))
    }

    @Test fun `sequential production-service ladder retains budgets receipts and failure without retry`() {
        val root = Files.createTempDirectory("preview-ladder-")
        val budget = PreviewBudget()
        Files.writeString(root.resolve("budgets.json"), "owned budget receipt")
        val calls = mutableListOf<String>()
        var active: Long? = null
        val services = object : PreviewJobServices {
            override fun admit(frames: Long): PreviewTicket {
                assertEquals(null, active, "A previous job must finish importing before the next admission")
                calls += "admit:$frames"
                active = frames
                return PreviewTicket("request-$frames", "attempt-$frames")
            }
            override fun observe(ticket: PreviewTicket): PreviewObservation {
                calls += "observe:${active}"
                return PreviewObservation(if (active == 600L) VideoGenerationAttemptStatus.FAILED
                    else VideoGenerationAttemptStatus.SUCCEEDED, "output-${active}")
            }
            override fun import(ticket: PreviewTicket, outputId: String, cancellation: VideoMediaProcessCancellation): PreviewImported {
                calls += "import:${active}"
                assertEquals("output-${active}", outputId)
                val id = active!!
                active = null
                return PreviewImported("take-$id", "a".repeat(64), "a".repeat(64), 100, 100, id)
            }
            override fun cancel(ticket: PreviewTicket) { calls += "cancel:${ticket.requestId}" }
        }
        assertFailsWith<IllegalStateException> { PreviewLadder.execute(root, budget, services, sample = { _, _ -> 0L }) }
        assertEquals(listOf("admit:150", "observe:150", "import:150", "admit:600", "observe:600", "cancel:request-600"), calls)
        assertTrue(Files.readString(root.resolve("job-150-result.json")).contains("take-150"))
        assertTrue(Files.readString(root.resolve("job-600-failure.txt")).contains("Controlled attempt failed"))
        assertFalse(Files.exists(root.resolve("job-900.json")))
        assertEquals("owned budget receipt", Files.readString(root.resolve("budgets.json")))
        assertFailsWith<IllegalArgumentException> {
            PreviewLadder.execute(Files.createTempDirectory("wrong-ladder-"), budget, services, listOf(150), sample = { _, _ -> 0L })
        }
    }

    @Test fun `budget guard remains active during blocked guarded import and cancels its owned attempt`() {
        val root = Files.createTempDirectory("preview-import-budget-")
        val budget = PreviewBudget()
        val calls = mutableListOf<String>()
        val entered = java.util.concurrent.CountDownLatch(1)
        val stopped = java.util.concurrent.CountDownLatch(1)
        val services = object : PreviewJobServices {
            override fun admit(frames: Long) = PreviewTicket("request-$frames", "attempt-$frames")
            override fun observe(ticket: PreviewTicket) = PreviewObservation(VideoGenerationAttemptStatus.SUCCEEDED, "output")
            override fun import(ticket: PreviewTicket, outputId: String, cancellation: VideoMediaProcessCancellation): PreviewImported {
                entered.countDown()
                check(stopped.await(5, java.util.concurrent.TimeUnit.SECONDS))
                check(!cancellation.isCancelled()) { "Import cancelled by budget watcher" }
                error("Unexpected publication")
            }
            override fun cancel(ticket: PreviewTicket) { synchronized(calls) { calls += ticket.requestId }; stopped.countDown() }
        }
        val samples = java.util.concurrent.atomic.AtomicInteger()
        val failure = assertFailsWith<IllegalStateException> {
            PreviewLadder.execute(root, budget, services, sample = { _, _ ->
                if (entered.count == 0L && samples.incrementAndGet() > 0) error("Import disk ceiling exceeded")
                0L
            })
        }
        assertTrue(failure.message!!.contains("disk ceiling"))
        assertEquals(listOf("request-150"), calls)
        assertTrue(Files.readString(root.resolve("job-150-failure.txt")).contains("disk ceiling"))
        assertFalse(Files.exists(root.resolve("job-600.json")))
    }

    @Test fun `production controlled backend output resolves in the guarded importer`() {
        val root = Files.createTempDirectory("preview-controlled-root-").toRealPath()
        val project = root.resolve("project")
        val store = VideoProjectStore(listOf(Files.createDirectory(root.resolve("midi"))))
        store.create(project, VideoProject("preview", "Preview", Instant.now().toString()))
        val publication = Files.createDirectory(project.resolve("controlled-outputs"))
        val output = publication.resolve("attempt/preview.mp4")
        Files.createDirectories(output.parent)
        Files.writeString(output, "owned output")
        val pin = VideoGenerationOutput("output", "attempt", "attempt-preview", Instant.now().toString(),
            "attempt/preview.mp4", PreviewPreflight.sha(output), Files.size(output))
        val importer = VideoResultImport(store, VideoMediaProbe(), controlledOutputRoot = publication)
        assertEquals(output, importer.resolvePinnedOutput(project, VideoControlledMediaStage.BACKEND_ID, pin))
        assertFailsWith<IllegalArgumentException> {
            importer.resolvePinnedOutput(project, VideoControlledMediaStage.BACKEND_ID,
                pin.copy(relativePath = "../other/preview.mp4"))
        }
    }

    @Test fun `guarded import native children receive a hard group RSS ceiling`() {
        val root = Files.createTempDirectory("preview-import-memory-")
        val budget = PreviewBudget()
        val request = VideoMediaProcessRequest(root.resolve("ffprobe"), "a".repeat(64),
            listOf("-version"), root.resolve("process"), Duration.ofSeconds(30))
        assertEquals(budget.memoryBytes, PreviewProductionRun.boundedImportRequest(request, budget).memoryLimitBytes)
        assertEquals(1024L, PreviewProductionRun.boundedImportRequest(request.copy(memoryLimitBytes = 1024), budget).memoryLimitBytes)
        assertEquals(budget.memoryBytes, PreviewProductionRun.boundedImportRequest(request.copy(memoryLimitBytes = Long.MAX_VALUE), budget).memoryLimitBytes)
    }

    @Test fun `per-job disk accounting excludes immutable earlier jobs but bounds current import`() {
        val root = Files.createTempDirectory("preview-disk-").toRealPath()
        val project = Files.createDirectory(root.resolve("project"))
        val takes = Files.createDirectory(project.resolve("takes"))
        Files.write(takes.resolve("old"), ByteArray(300))
        val budget = PreviewBudget()
        val baseline = 300L
        Files.write(takes.resolve("new"), ByteArray(200))
        assertEquals(200, PreviewLadder.checkBudget(root, budget, 600, baseline, baseline, { Long.MAX_VALUE }))
        val tight = budget.copy(outputBytes = 100)
        assertFailsWith<IllegalStateException> {
            PreviewLadder.checkBudget(root, tight, 600, baseline, baseline, { Long.MAX_VALUE })
        }
    }

    @Test fun `missing unsafe occupied or overlapped paths and unavailable supervision reject before tool launch`() {
        val f = fixture()
        fun rejects(block: () -> Unit) {
            assertFailsWith<Exception> { block() }
            assertEquals(0, f.launches)
            assertFalse(Files.exists(f.output))
        }
        rejects { f.validate(host = "Linux/amd64") }
        rejects { f.validate(free = -1) }
        rejects { f.validate(output = Path.of("relative")) }
        rejects { f.validate(output = f.tools.resolve("in-tools")) }
        rejects { f.validate(output = f.node) }
        rejects { f.validate(output = f.root.resolve("node_modules/in-package")) }
        rejects { f.validate(budget = PreviewBudget(concurrentNativeProcesses = 2)) }
        rejects { f.validate(budget = PreviewBudget(wallMillis = 0)) }
        rejects { f.validate(budget = PreviewBudget(stagingBytes = 0)) }
        rejects { f.validate(budget = PreviewBudget(diskReserveBytes = 0)) }
        rejects { f.validate(budget = PreviewBudget(wallMillis = Long.MAX_VALUE)) }
        rejects { f.validate(budget = PreviewBudget(memoryBytes = Long.MAX_VALUE)) }
        rejects { f.validate(budget = PreviewBudget(stagingBytes = Long.MAX_VALUE)) }
        rejects { f.validate(budget = PreviewBudget(outputBytes = Long.MAX_VALUE)) }
        rejects { f.validate(budget = PreviewBudget(diskReserveBytes = Long.MAX_VALUE)) }
        val alias = f.root.resolve("alias")
        Files.createSymbolicLink(alias, f.root)
        rejects { f.validate(output = alias.resolve("fresh")) }
        Files.writeString(f.root.resolve(VideoProjectStore.MIDI_PROJECT_FILE), "protected")
        rejects { f.validate() }
        Files.delete(f.root.resolve(VideoProjectStore.MIDI_PROJECT_FILE))
        Files.createDirectory(f.output)
        assertFailsWith<IllegalArgumentException> { f.validate() }
        assertEquals(0, f.launches)
    }
}
