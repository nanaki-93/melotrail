package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import kotlinx.serialization.json.*
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    /** Synthetic process facts only: the application, ledger, renderer receipts and importer remain real. */
    private class ProductionFixture(val f: Fixture) {
        val protected = listOf(f.root.resolve("midi"))
        val calls = mutableListOf<String>()
        var beforeProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> Unit = { _, _ -> }
        val config: PreviewConfiguration
        init {
            val validated = f.validate()
            // The fake process replaces executable attestation, not the selected manifest contract.
            val manifest = f.tools.resolve(VideoMediaProbe.MANIFEST_NAME)
            Files.writeString(manifest, Files.readString(manifest)
                .replace(f.hashes.first, VideoMediaProbe.FFMPEG_SHA256)
                .replace(f.hashes.second, VideoMediaProbe.FFPROBE_SHA256))
            config = validated.copy(runtime = validated.runtime.map {
                if (it.id == "media-manifest") it.copy(sha256 = PreviewPreflight.sha(manifest)) else it
            })
        }
        fun jobs() = VideoJobStore(f.output.resolve("jobs"), "preview-proof", protected)
        fun store() = VideoProjectStore(protected)
        val project get() = f.output.resolve("project")
        fun run(sample: ((Long, Long) -> Long)? = null) = PreviewProductionRun.run(config, ::process, { Long.MAX_VALUE }, sample)
        fun process(request: VideoMediaProcessRequest, cancellation: VideoMediaProcessCancellation): VideoMediaProcessResult {
            check(!cancellation.isCancelled())
            beforeProcess(request, cancellation)
            val args = request.arguments
            val inputPath = if ("-i" in args) args.getOrNull(args.indexOf("-i") + 1) else args.lastOrNull()
            val inputFrames = inputPath?.let { runCatching { Files.readString(Path.of(it)).removePrefix("synthetic video ").toLong() }.getOrNull() }
            val job = jobs().snapshot().jobs.let { jobs -> inputFrames?.let { n -> jobs.single { it.request.id == "preview-$n" } } ?: jobs.last() }
            val frames = (job.request.input as VideoControlledMotionGenerationInput).motion.endFrameExclusive
            check(Files.exists(f.output.resolve("job-$frames-budget.json")))
            check(job.attempts.single().controlledEvidence != null)
            if (frames > 150) check(Files.exists(f.output.resolve("job-${if (frames == 600L) 150 else 600}-result.json")))
            check(request.memoryLimitBytes == config.budget.memoryBytes)
            Files.createDirectories(request.workingDirectory)
            calls += "$frames:${request.workingDirectory.fileName}"
            fun argument(key: String) = args[args.indexOf(key) + 1]
            val text = when {
                "-version" in args -> "${request.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.joinToString(" ")
                "-demuxers" in args -> " D image2 image sequence\n"
                "-encoders" in args -> " V h264_videotoolbox encoder\n"
                "-muxers" in args -> " E mp4 muxer\n"
                "-e" in args && args[1].contains("createRequire") -> buildJsonObject {
                    put("manifest", f.canvas.toString()); put("path", f.canvas.parent.resolve("index.js").toString())
                    put("version", "0.1.80")
                    put("artifacts", buildJsonArray { config.runtime.filter { it.id.startsWith("canvas-artifact-") }.forEach { add(it.ownedPath!!) } })
                }.toString()
                "-e" in args -> PreviewPreflight.sha(Path.of(args.last()))
                "--request" in args -> {
                    val descriptorPath = Path.of(argument("--request"))
                    val descriptor = Json.parseToJsonElement(Files.readString(descriptorPath)).jsonObject
                    val range = descriptor.getValue("frameRange").jsonObject
                    val start = range.getValue("startFrame").jsonPrimitive.long
                    val count = range.getValue("frameCount").jsonPrimitive.long
                    val directory = Files.createDirectory(Path.of(argument("--output")))
                    val records = buildJsonArray {
                        for (n in start until start + count) {
                            val name = "frame-${n.toString().padStart(8, '0')}.png"
                            val file = directory.resolve(name)
                            val image = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
                            val g = image.createGraphics()
                            g.color = java.awt.Color(50, 60, 80); g.fillRect(0, 0, 320, 180)
                            val amount = PreviewFrameEvidence.blink(n)
                            g.color = java.awt.Color((170 + 60 * amount).toInt(), (100 + 120 * amount).toInt(), (150 - 90 * amount).toInt())
                            g.fillOval(10, 10, 20, 30); g.dispose()
                            check(javax.imageio.ImageIO.write(image, "png", file.toFile()))
                            add(buildJsonObject { put("frame", n); put("file", name); put("sha256", PreviewPreflight.sha(file)) })
                        }
                    }
                    val scene = descriptor.getValue("preparedScene").jsonObject
                    val pins = listOf("layers", "poses", "masks").flatMap { scene[it]?.jsonArray.orEmpty() }
                        .mapNotNull { it.jsonObject["image"]?.jsonObject?.get("artifact")?.jsonObject?.get("sha256")?.jsonPrimitive?.content }.toSet()
                    Files.writeString(directory.resolve("render-receipt.json"), buildJsonObject {
                        put("schema", "melotrail-controlled-motion-receipt-v1")
                        put("tool", buildJsonObject { put("version", "1.1.0") })
                        put("frameRange", range); put("frames", records)
                        put("sourcePins", buildJsonArray { pins.forEach { add(it) } })
                        put("requestSha256", PreviewPreflight.sha(descriptorPath))
                    }.toString())
                    ""
                }
                request.executable.fileName.toString() == "ffprobe" && "-show_frames" in args ->
                    """{"frames":[${(0 until frames).joinToString { "{\"best_effort_timestamp\":$it}" }}]}"""
                request.executable.fileName.toString() == "ffprobe" ->
                    """{"streams":[{"codec_type":"video","codec_name":"h264","width":320,"height":180,"sample_aspect_ratio":"1:1","avg_frame_rate":"30/1","nb_read_frames":"$frames","time_base":"1/30","start_pts":0,"duration_ts":$frames}],"format":{"duration":"${frames / 30.0}"}}"""
                "-vf" in args -> {
                    val frame = argument("-vf").substringAfter(',').substringBefore(')').toLong()
                    val rendered = project.resolve("controlled-outputs/${job.attempts.single().id}/encode-frames/frame-${frame.toString().padStart(8, '0')}.png")
                    Files.copy(rendered, Path.of(args.last())); ""
                }
                args.last() == "encoded.mp4" -> { Files.writeString(request.workingDirectory.resolve("encoded.mp4"), "synthetic video $frames"); "" }
                else -> { check("null" in args) { "Unhandled synthetic process: $args" }; "" }
            }
            return VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.toByteArray().size.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, request.workingDirectory)
        }
    }

    @Test fun `three complete production imports publish measured unselected immutable takes`() {
        val f = ProductionFixture(fixture())
        f.run()
        val store = f.store()
        val project = store.open(f.project)
        assertEquals(3, project.takeVersions.size)
        assertEquals(3, project.takeVersions.map { it.id }.toSet().size)
        assertTrue(project.takeReviewEvents.isEmpty())
        assertTrue(project.selectedTakeIds.isEmpty())
        val jobs = f.jobs().snapshot().jobs
        assertEquals(listOf("preview-150", "preview-600", "preview-900"), jobs.map { it.request.id })
        jobs.zip(listOf(150L, 600L, 900L)).forEach { (job, count) ->
            val input = assertIs<VideoControlledMotionGenerationInput>(job.request.input)
            assertEquals(VideoControlledMediaStage.BACKEND_ID, job.request.backendId)
            assertEquals(0, input.motion.startFrame)
            assertEquals(count, input.motion.endFrameExclusive)
            assertEquals(f.config.budget.policy(), job.request.execution)
            assertEquals(1, job.request.maximumAttempts)
            assertEquals(controlledMotionRequestFingerprint(job.request.backendId, input, job.request.modelRequirements, 1), job.request.requestFingerprint)
            input.dependencyPins.forEach { assertEquals(it.sha256, PreviewPreflight.sha(Path.of(it.ownedPath!!))) }
            val attempt = job.attempts.single()
            assertEquals(VideoGenerationAttemptStatus.SUCCEEDED, attempt.status)
            assertEquals(VideoControlledStage.COMPLETED, attempt.controlledEvidence?.stage)
            val output = job.outputs.single()
            assertEquals(output.id, job.currentOutputId)
            assertEquals(attempt.id, output.attemptId)
            val take = project.takeVersions.single { it.provenance?.requestId == job.request.id }
            val measurement = assertNotNull(take.sourceMeasurement)
            assertEquals(count, measurement.decodedFrameCount)
            assertEquals(measurement, take.publishedMeasurement)
            assertEquals(output.sha256, measurement.sha256)
            assertEquals(output.sha256, PreviewPreflight.sha(store.resolveArtifact(f.project, take.artifact)))
            val reopened = VideoProjectSession(f.project, store.open(f.project))
            val service = VideoClipGeneration(VideoScenePreparation(), VideoJobCoordinator("preview-proof", f.jobs(), emptyList()),
                VideoResultImport(store, PreviewProductionRun.importProbe(f.config.budget, f::process), controlledOutputRoot = f.project.resolve("controlled-outputs")),
                VideoMotionRenderer(), VideoControlledMediaStage.BACKEND_ID, f.config.budget.policy(), projects = store)
            val nativeCalls = f.calls.size
            val replay = service.importCompleted(VideoCompletedTakeImport(reopened,
                reopened.project.revision, job.request.id, attempt.id, output.id))
            val imported = assertIs<VideoClipGenerationResult.Imported>(replay, replay.toString())
            assertEquals(take.id, imported.result.take.id)
            assertEquals(nativeCalls, f.calls.size, "Exact reimport must not start any native work")
            assertEquals(project, imported.result.project)
        }
        assertEquals(jobs, f.jobs().snapshot().jobs)
        assertEquals(project, store.open(f.project))
        val library = assertIs<VideoAssetLibraryResult.Loaded>(VideoAssetImport(VideoProjectLifecycle(store), VideoImageFiles()).open(f.project))
        for (name in listOf("finished", "subject", "clean", "pose")) {
            val original = f.f.output.resolve("artwork/$name.png")
            val asset = library.assets.single { it.id.id == name }
            assertEquals(PreviewPreflight.sha(original), asset.original.artifact.sha256)
            assertEquals(asset.original.artifact.sha256, PreviewPreflight.sha(store.resolveArtifact(f.project, asset.original.artifact)))
        }
    }

    @Test fun `reconstructed recovery rejects changed output and noncurrent attempt without overwriting takes`() {
        val f = ProductionFixture(fixture())
        f.run()
        val job = f.jobs().snapshot().jobs.last()
        val attempt = job.attempts.single()
        val output = job.outputs.single()
        val project = f.store().open(f.project)
        val publishedHashes = project.takeVersions.associate { it.artifact to PreviewPreflight.sha(f.store().resolveArtifact(f.project, it.artifact)) }
        assertFailsWith<Exception> {
            PreviewProductionRun.recover(f.config, PreviewTicket(job.request.id, "older-attempt"), output.id)
        }
        val path = f.project.resolve("controlled-outputs").resolve(output.relativePath!!)
        val stamp = Files.getLastModifiedTime(path)
        val bytes = Files.readAllBytes(path)
        // Same size and restored timestamp must not disguise changed source content.
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        Files.write(path, bytes.map { (it.toInt() xor 1).toByte() }.toByteArray())
        Files.setLastModifiedTime(path, stamp)
        assertFailsWith<Exception> {
            PreviewProductionRun.recover(f.config, PreviewTicket(job.request.id, attempt.id), output.id)
        }
        assertEquals(project, f.store().open(f.project))
        publishedHashes.forEach { (artifact, hash) -> assertEquals(hash, PreviewPreflight.sha(f.store().resolveArtifact(f.project, artifact))) }
        assertEquals(1, f.jobs().snapshot().jobs.last().attempts.size)
    }

    @Test fun `frame evidence covers every boundary and authored phase and rejects discontinuity`() {
        for (count in listOf(150L, 600L, 900L)) {
            val samples = PreviewFrameEvidence.sampleFrames(count)
            assertTrue(samples.containsAll(listOf(0, count / 2, count - 1)))
            for (boundary in 300 until count step 300) assertTrue(samples.containsAll(listOf(boundary - 1, boundary)))
            assertTrue(samples.any { PreviewFrameEvidence.blink(it) > 0.25 })
        }
        val a = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val b = java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = b.createGraphics(); g.color = java.awt.Color.WHITE; g.fillRect(10, 10, 20, 30); g.dispose()
        assertFailsWith<IllegalArgumentException> { PreviewFrameEvidence.checkPixels(a, b) }
        assertFailsWith<IllegalArgumentException> {
            PreviewFrameEvidence.checkPixels(a, java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB))
        }
        assertFailsWith<IllegalArgumentException> { PreviewFrameEvidence.sampleFrames(299) }
        val missing = Files.createTempDirectory("preview-sample-").resolve("absent.png")
        assertFailsWith<IllegalArgumentException> { PreviewFrameEvidence.readSample(missing) }
        Files.writeString(missing, "not a decoded frame")
        assertFailsWith<IllegalArgumentException> { PreviewFrameEvidence.readSample(missing) }
    }

    @Test fun `production import cancellation preserves first take and stops the remaining ladder`() {
        val f = ProductionFixture(fixture())
        val blocked = java.util.concurrent.atomic.AtomicBoolean()
        var prior: VideoProject? = null
        var hashes = emptyMap<Path, String>()
        var ownedCancelled = false
        f.beforeProcess = { request, cancellation ->
            if (request.workingDirectory.fileName.toString() == "take-source-full-decode" && f.jobs().snapshot().jobs.size == 2) {
                assertEquals(f.config.budget.memoryBytes, request.memoryLimitBytes)
                prior = f.store().open(f.project)
                assertEquals(1, prior!!.takeVersions.size)
                val paths = prior!!.takeVersions.map { f.store().resolveArtifact(f.project, it.artifact) } +
                    listOf("finished", "subject", "clean", "pose").map { f.f.output.resolve("artwork/$it.png") }
                hashes = paths.associateWith(PreviewPreflight::sha)
                blocked.set(true)
                val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
                while (!cancellation.isCancelled() && System.nanoTime() < deadline) Thread.sleep(5)
                ownedCancelled = cancellation.isCancelled()
                check(ownedCancelled) { "Budget monitor failed to cancel the owned import child" }
                throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "owned import fixture cancelled")
            }
        }
        val failure = assertFailsWith<IllegalStateException> {
            f.run { _, _ -> check(!blocked.get()) { "fixture import resource ceiling" }; 0L }
        }
        assertTrue(failure.message.orEmpty().contains("resource ceiling"))
        assertTrue(ownedCancelled)
        val project = f.store().open(f.project)
        assertEquals(prior, project)
        hashes.forEach { (path, hash) -> assertEquals(hash, PreviewPreflight.sha(path)) }
        val jobs = f.jobs().snapshot().jobs
        assertEquals(listOf("preview-150", "preview-600"), jobs.map { it.request.id })
        assertTrue(jobs.all { it.attempts.size == 1 })
        assertEquals(1, project.takeVersions.size)
        assertTrue(project.selectedTakeIds.isEmpty())
        assertTrue(Files.exists(f.f.output.resolve("job-150-result.json")))
        assertTrue(Files.readString(f.f.output.resolve("job-600-failure.txt")).contains("resource ceiling"))
        assertFalse(Files.exists(f.f.output.resolve("job-600-result.json")))
        assertFalse(Files.exists(f.f.output.resolve("job-900-budget.json")))
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
