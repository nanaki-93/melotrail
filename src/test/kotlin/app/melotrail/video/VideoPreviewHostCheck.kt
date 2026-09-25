package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Opt-in host check; never invoked by ordinary tests or application startup. No model is involved. */
object VideoPreviewHostCheck {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "Usage: videoPreviewProbe <absolute-tools> <absolute-node> <absolute-canvas-manifest> <absolute-new-output>" }
        val paths = args.map(Path::of)
        val configuration = PreviewPreflight.validate(paths[0], paths[1], paths[2], paths[3])
        PreviewProductionRun.run(configuration)
    }
}

internal data class PreviewBudget(
    val wallMillis: Long = 600_000,
    val memoryBytes: Long = 4L * 1024 * 1024 * 1024,
    val stagingBytes: Long = 2L * 1024 * 1024 * 1024,
    val outputBytes: Long = 2L * 1024 * 1024 * 1024,
    val diskReserveBytes: Long = 512L * 1024 * 1024,
    val concurrentNativeProcesses: Int = 1,
) {
    fun policy() = VideoLocalExecutionPolicy(wallMillis, memoryBytes, Math.addExact(stagingBytes, outputBytes))
    fun verify() {
        require(wallMillis in 60_000..600_000 && memoryBytes in 512L * 1024 * 1024..4L * 1024 * 1024 * 1024 &&
            stagingBytes in 64L * 1024 * 1024..2L * 1024 * 1024 * 1024 &&
            outputBytes in 64L * 1024 * 1024..2L * 1024 * 1024 * 1024 &&
            diskReserveBytes in 64L * 1024 * 1024..512L * 1024 * 1024 && concurrentNativeProcesses == 1 &&
            diskReserveBytes <= policy().diskLimitBytes / 8) {
            "Preview budget cannot be enforced by the controlled stage; reduce the requested limits."
        }
    }
}

internal data class PreviewConfiguration(val output: Path, val runtime: List<VideoGenerationDependencyPin>,
    val budget: PreviewBudget, val tools: Path)

/** Validate *before* creating an output or submitting a job. Probes run only pinned, owned local tools. */
internal object PreviewPreflight {
    fun validate(tools: Path, node: Path, canvas: Path, output: Path,
        budget: PreviewBudget = PreviewBudget(),
        host: String = "${System.getProperty("os.name")}/${System.getProperty("os.arch")}",
        freeMemory: () -> Long = { (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1 },
        probe: (VideoMediaProcessRequest) -> String = { VideoMediaProcess().run(it).stdout.text },
        expectedMediaHashes: Pair<String, String> = VideoMediaProbe.FFMPEG_SHA256 to VideoMediaProbe.FFPROBE_SHA256,
        scriptPath: Path = Path.of(System.getProperty("user.dir")).toRealPath().resolve("tools/video-motion/render.cjs"),
        usableDisk: (Path) -> Long = { Files.getFileStore(it).usableSpace },
        allowedOutputRoot: Path = Path.of(System.getProperty("user.dir")).toRealPath().resolve("build/vg2"),
    ): PreviewConfiguration {
        require(host in setOf("Mac OS X/arm64", "Mac OS X/aarch64")) { "Owned native process supervision requires macOS arm64: $host" }
        budget.verify()
        fun safe(path: Path, label: String, exists: Boolean): Path {
            require(path.isAbsolute && path.normalize() == path && path.toString().split('/').none { it == "." || it == ".." }) {
                "$label must be an absolute normalized path without traversal: $path"
            }
            var ancestor: Path? = if (exists) path else path.parent
            while (ancestor != null) {
                require(!Files.isSymbolicLink(ancestor)) { "$label has a symbolic-link ancestor: $ancestor" }
                ancestor = ancestor.parent
            }
            require((if (exists) path else path.parent).toRealPath() == (if (exists) path else path.parent)) {
                "$label has an unresolved filesystem alias: $path"
            }
            return path
        }
        val t = safe(tools, "videoToolsDirectory", true)
        val n = safe(node, "videoNodeExecutable", true)
        val c = safe(canvas, "videoCanvasManifest", true)
        val o = safe(output, "videoPreviewOutput", false)
        require(Files.isDirectory(t, NOFOLLOW_LINKS) && Files.isDirectory(o.parent, NOFOLLOW_LINKS) &&
            !Files.exists(o, NOFOLLOW_LINKS)) { "Tools/output parent must exist and preview destination must be absent: $o" }
        require(c == c.parent.parent.parent.resolve("@napi-rs/canvas/package.json")) { "Canvas must be the selected node_modules package." }
        val manifest = c.parent.parent.parent // node_modules
        val files = listOf("index.js", "js-binding.js", "geometry.js", "load-image.js").map(c.parent::resolve) +
            listOf(c.parent.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node"))
        val media = t.resolve(VideoMediaProbe.MANIFEST_NAME)
        val ffmpeg = t.resolve("ffmpeg")
        val ffprobe = t.resolve("ffprobe")
        val script = scriptPath
        val scenery = script.parent.resolve("scenery.cjs")
        val pins = (listOf(n, c, media, ffmpeg, ffprobe, script, scenery) + files).map { path ->
            safe(path, "Pinned runtime file", true)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Missing runtime pin: $path" }
            path
        }
        require(Files.isExecutable(n) && Files.isExecutable(ffmpeg) && Files.isExecutable(ffprobe)) { "Selected native tool is not executable." }
        val repository = Path.of(System.getProperty("user.dir")).toRealPath()
        generateSequence(o.parent) { it.parent }.forEach { ancestor ->
            require(!Files.exists(ancestor.resolve(VideoProjectStore.MIDI_PROJECT_FILE), NOFOLLOW_LINKS)) {
                "Preview output is inside a protected MIDI project: $ancestor"
            }
        }
        require(listOf(t, n, c, manifest, script, scenery, media, ffmpeg, ffprobe).none { o.startsWith(it) || it.startsWith(o) } &&
            o.startsWith(allowedOutputRoot) && !repository.startsWith(o)) {
            "Preview output overlaps pinned inputs or repository references."
        }
        val mediaJson = Json.parseToJsonElement(Files.readString(media)).jsonObject
        fun requiredOptions(options: List<String>): Boolean = VideoMediaProbe.REQUIRED_BUILD_OPTIONS.all { required ->
            if (!required.startsWith("--enable-") || '=' !in required) required in options
            else options.any { it.startsWith(required.substringBefore('=') + "=") &&
                required.substringAfter('=') in it.substringAfter('=').split(',') }
        }
        val mediaOptions = mediaJson.getValue("buildOptions").jsonArray.map { it.jsonPrimitive.content }
        require(mediaJson.getValue("schema").jsonPrimitive.content == "melotrail-video-media-tools" &&
            mediaJson.getValue("version").jsonPrimitive.content == "1" &&
            mediaJson.getValue("distributionId").jsonPrimitive.content == VideoMediaProbe.DISTRIBUTION_ID &&
            mediaJson.getValue("installation").jsonPrimitive.content == VideoMediaProbe.INSTALLATION_STRATEGY &&
            mediaJson.getValue("sourceUrl").jsonPrimitive.content == VideoMediaProbe.SOURCE_URL &&
            mediaJson.getValue("sourceRevision").jsonPrimitive.content == VideoMediaProbe.SOURCE_REVISION &&
            mediaJson.getValue("sourceSha256").jsonPrimitive.content == VideoMediaProbe.SOURCE_SHA256 &&
            requiredOptions(mediaOptions) &&
            mediaJson.getValue("notices").jsonArray.any { it.jsonPrimitive.content.isNotBlank() } &&
            mediaJson.getValue("ffmpegSha256").jsonPrimitive.content == expectedMediaHashes.first &&
            mediaJson.getValue("ffprobeSha256").jsonPrimitive.content == expectedMediaHashes.second) {
            "Pinned FFmpeg manifest differs from selected installed tools."
        }
        require(sha(ffmpeg) == expectedMediaHashes.first && sha(ffprobe) == expectedMediaHashes.second) {
            "Installed media executable digest differs from the selected pin."
        }
        val packageJson = Json.parseToJsonElement(Files.readString(c)).jsonObject
        require(packageJson.getValue("name").jsonPrimitive.content == "@napi-rs/canvas" &&
            packageJson.getValue("version").jsonPrimitive.content == "0.1.80") { "Selected Canvas 0.1.80 is not installed." }
        val resolvedCanvas = generateSequence(script.parent) { it.parent }
            .map { it.resolve("node_modules/@napi-rs/canvas/package.json") }.firstOrNull(Files::exists) ?: c
        require(resolvedCanvas == c) { "Node will resolve a different Canvas installation." }
        val free = usableDisk(o.parent)
        require(free >= Math.addExact(budget.policy().diskLimitBytes, budget.diskReserveBytes) &&
            freeMemory() >= budget.memoryBytes) { "Insufficient verified free disk or memory for the bounded preview." }
        // Version/capability probes execute only pinned tools through the production supervisor.
        fun version(label: String, file: Path, args: List<String>): String = probe(VideoMediaProcessRequest(
            file, sha(file), args, o.parent.resolve(".preview-preflight-$label-${java.util.UUID.randomUUID()}"),
            Duration.ofSeconds(15), memoryLimitBytes = budget.memoryBytes,
            environment = mapOf("LC_ALL" to "C", "LANG" to "C")))
        require(version("node", n, listOf("--version")).trim().matches(Regex("v(2[0-9]|[3-9][0-9])\\.[0-9]+\\.[0-9]+"))) {
            "Pinned Node runtime must be version 20 or newer."
        }
        val encoderVersion = version("ffmpeg", ffmpeg, listOf("-hide_banner", "-version"))
        require(encoderVersion.startsWith("ffmpeg version 9.0.1") &&
            requiredOptions(encoderVersion.split(Regex("\\s+"))) &&
            version("ffprobe", ffprobe, listOf("-hide_banner", "-version")).startsWith("ffprobe version 9.0.1") &&
            listOf("image2" to "-demuxers", "h264_videotoolbox" to "-encoders", "mp4" to "-muxers").all { (name, flag) ->
                version(flag.removePrefix("-"), ffmpeg, listOf("-hide_banner", flag))
                    .lineSequence().any { it.trim().split(Regex("\\s+")).getOrNull(1) == name }
            }) { "Pinned media tools lack supported image2/H.264/MP4 capabilities." }
        val byId = linkedMapOf("node" to n, "compositor" to script, "scenery" to scenery,
            "canvas-manifest" to c, "ffmpeg" to ffmpeg, "ffprobe" to ffprobe, "media-manifest" to media)
        files.forEachIndexed { i, file -> byId["canvas-artifact-$i"] = file }
        require(pins.containsAll(byId.values))
        return PreviewConfiguration(o, byId.map { (id, file) -> VideoGenerationDependencyPin(id, sha(file), file.toString()) }, budget, t)
    }

    internal fun sha(path: Path): String {
        val hash = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input -> val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}

internal data class PreviewTicket(val requestId: String, val attemptId: String)
internal data class PreviewObservation(val status: VideoGenerationAttemptStatus?, val outputId: String?)
internal data class PreviewImported(val takeId: String, val sourceSha256: String, val publishedSha256: String,
    val sourceBytes: Long, val publishedBytes: Long, val revision: Long)
internal interface PreviewJobServices {
    fun admit(frames: Long): PreviewTicket
    fun observe(ticket: PreviewTicket): PreviewObservation
    fun import(ticket: PreviewTicket, outputId: String, cancellation: VideoMediaProcessCancellation): PreviewImported
    fun cancel(ticket: PreviewTicket)
}

/** A single sequential admission domain. Never retry a failed or uncertain attempt. */
internal object PreviewLadder {
    fun execute(output: Path, budget: PreviewBudget, services: PreviewJobServices,
        frames: List<Long> = listOf(150, 600, 900),
        now: () -> Long = System::nanoTime, pause: () -> Unit = { Thread.sleep(200) },
        sample: ((Long, Long) -> Long)? = null,
        freeMemory: () -> Long = { (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1L }) {
        require(frames == listOf(150L, 600L, 900L)) { "Preview proof requires exactly 150, 600 and 900 native frames in order." }
        for (count in frames) {
            // Earlier successful takes and attempts are immutable retained evidence, not
            // staging charged again to a later job. Snapshot before admitting this job.
            val baseline = projectBytes(output.resolve("project"), count)
            val baselineTakes = projectBytes(output.resolve("project/takes"), count)
            val observedDisk = java.util.concurrent.atomic.AtomicLong(0)
            fun sampleJob() {
                val used = sample?.invoke(count, baseline) ?: checkBudget(output, budget, count, baseline, baselineTakes, freeMemory)
                observedDisk.accumulateAndGet(used, ::maxOf)
            }
            val started = now()
            val deadline = Math.addExact(started, Duration.ofMillis(budget.wallMillis).toNanos())
            var ticket: PreviewTicket? = null
            val cancellation = VideoMediaProcessCancellation()
            val violation = AtomicReference<Throwable?>()
            val cancelSent = AtomicBoolean(false)
            fun cancelAttempt() {
                ticket?.let { if (cancelSent.compareAndSet(false, true)) services.cancel(it) }
            }
            fun checkLimits() {
                check(now() < deadline) { "Preview $count exceeded its configured deadline; reconcile the owned attempt before another run." }
                sampleJob()
            }
            val monitor = Thread {
                while (!Thread.currentThread().isInterrupted && !cancellation.isCancelled()) {
                    try {
                        checkLimits()
                        Thread.sleep(50)
                    } catch (_: InterruptedException) { break }
                    catch (error: Throwable) {
                        violation.compareAndSet(null, error)
                        cancellation.cancel()
                        runCatching { cancelAttempt() }
                        break
                    }
                }
            }.apply { name = "video-preview-budget-$count"; isDaemon = true; start() }
            try {
                checkLimits()
                Files.writeString(output.resolve("job-$count-budget.json"),
                    """{"frames":$count,"wallMillis":${budget.wallMillis},"memoryBytes":${budget.memoryBytes},"stagingBytes":${budget.stagingBytes},"outputBytes":${budget.outputBytes},"diskReserveBytes":${budget.diskReserveBytes},"concurrentNativeProcesses":${budget.concurrentNativeProcesses}}""", CREATE_NEW)
                ticket = services.admit(count)
                violation.get()?.let { throw it }
                val admitted = requireNotNull(ticket)
                checkLimits()
                Files.writeString(output.resolve("job-$count.json"),
                    """{"frames":$count,"requestId":"${admitted.requestId}","attemptId":"${admitted.attemptId}","budget":"budgets.json","status":"ADMITTED"}""", CREATE_NEW)
                var observed = services.observe(admitted)
                while (observed.status?.isTerminal != true) {
                    violation.get()?.let { throw it }
                    checkLimits()
                    pause()
                    observed = services.observe(admitted)
                }
                violation.get()?.let { throw it }
                checkLimits()
                check(observed.status == VideoGenerationAttemptStatus.SUCCEEDED) { "Controlled attempt failed for $count: ${observed.status}" }
                val outputId = requireNotNull(observed.outputId) { "Successful preview has no current durable output." }
                val take = services.import(admitted, outputId, cancellation)
                violation.get()?.let { throw it }
                checkLimits()
                Files.writeString(output.resolve("job-$count-result.json"),
                    """{"status":"IMPORTED","takeId":"${take.takeId}","requestId":"${admitted.requestId}","attemptId":"${admitted.attemptId}","outputId":"$outputId","sourceSha256":"${take.sourceSha256}","publishedSha256":"${take.publishedSha256}","sourceBytes":${take.sourceBytes},"publishedBytes":${take.publishedBytes},"projectRevision":${take.revision},"elapsedMillis":${Duration.ofNanos(now()-started).toMillis()},"observedPeakMemoryBytes":null,"observedPeakDiskBytes":${observedDisk.get()}}""", CREATE_NEW)
            } catch (error: Exception) {
                cancellation.cancel()
                runCatching { cancelAttempt() }
                val cause = violation.get() ?: error
                Files.writeString(output.resolve("job-$count-failure.txt"), cause.stackTraceToString(), CREATE_NEW)
                throw cause // retain partial evidence; no automatic retry or budget expansion
            } finally {
                monitor.interrupt()
                monitor.join()
            }
        }
    }

    internal fun checkBudget(output: Path, budget: PreviewBudget, count: Long, baseline: Long, baselineTakes: Long,
        freeMemory: () -> Long = { (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1L }): Long {
        val project = output.resolve("project")
        check(Files.getFileStore(output).usableSpace >= budget.diskReserveBytes) {
            "Preview $count exhausted its disk reserve."
        }
        // The controlled attempt enforces its own staging/output limits; this sampler
        // also covers take validation and the independent copy during guarded import.
        val current = projectBytes(project, count)
        val used = (current - baseline).coerceAtLeast(0)
        check(used <= Math.addExact(budget.stagingBytes, budget.outputBytes)) {
            "Preview $count exceeded its project staging/output ceiling."
        }
        val takeBytes = projectBytes(project.resolve("takes"), count)
        check(takeBytes - baselineTakes <= budget.outputBytes) {
            "Preview $count exceeded the retained take output ceiling."
        }
        check(freeMemory() >= budget.memoryBytes) { "Preview $count cannot retain its reserved memory capacity." }
        return used
    }

    private fun projectBytes(root: Path, count: Long): Long {
        if (!Files.exists(root, NOFOLLOW_LINKS)) return 0L
        var total = 0L
        // Publication removes private staging names atomically. A disappearing entry
        // is not disk growth; permission/I/O errors and aliases must still fail closed.
        Files.walkFileTree(root, object : java.nio.file.SimpleFileVisitor<Path>() {
            override fun visitFile(path: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                check(!attrs.isSymbolicLink) { "Preview $count has an unsafe output alias: $path" }
                if (attrs.isRegularFile) total = Math.addExact(total, attrs.size())
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(path: Path, error: java.io.IOException): java.nio.file.FileVisitResult {
                if (error !is java.nio.file.NoSuchFileException) throw error
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
        return total
    }
}

/** Bounded native evidence for this owned blink fixture, not a general artistic evaluator. */
internal object PreviewFrameEvidence {
    // Independent implementation of the pinned compositor's absolute-time blink schedule.
    internal fun blink(frame: Long): Double {
        fun unit(label: String, slot: Long): Double {
            val bytes = MessageDigest.getInstance("SHA-256").digest("73:blink:$label:$slot".toByteArray())
            return (java.nio.ByteBuffer.wrap(bytes).int.toLong() and 0xffffffffL) / 4294967296.0
        }
        fun smooth(value: Double): Double = value.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        val time = frame / 30.0
        val epoch = (time / 7).toLong()
        return ((epoch - 1)..(epoch + 1)).maxOf { slot ->
            val start = slot * 7 + 1 + unit("start", slot) * 4.5
            val close = 0.08 + unit("close", slot) * 0.05
            val open = 0.11 + unit("open", slot) * 0.06
            when {
                time >= start && time < start + close -> smooth((time - start) / close)
                time >= start + close && time < start + close + open -> 1 - smooth((time - start - close) / open)
                else -> 0.0
            }
        } * 0.5
    }

    internal fun sampleFrames(count: Long): List<Long> {
        require(count in listOf(150L, 600L, 900L))
        return (listOf(0L, count / 2, count - 1) +
            (300 until count step 300).flatMap { listOf(it - 1, it) } +
            (0 until count step 210).map { start -> (start until minOf(start + 210, count)).maxBy(::blink) })
            .distinct().sorted()
    }

    internal fun readSample(path: Path): BufferedImage {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) { "Missing decoded frame: $path" }
        return requireNotNull(ImageIO.read(path.toFile())) { "Invalid decoded frame: $path" }
    }

    internal fun checkPixels(expected: BufferedImage, decoded: BufferedImage) {
        require(expected.width == decoded.width && expected.height == decoded.height) { "Decoded frame viewport differs" }
        var error = 0L
        // Compare the authored subject, not a whole-canvas mean that can hide lost motion.
        for (y in 12 until 38) for (x in 12 until 28) for (shift in listOf(0, 8, 16))
            error += kotlin.math.abs(((expected.getRGB(x, y) shr shift) and 255) - ((decoded.getRGB(x, y) shr shift) and 255))
        require(error.toDouble() / (26 * 16 * 3) <= 8.0) { "Decoded motion sample differs from its absolute rendered frame" }
    }

    fun inspect(config: PreviewConfiguration, input: VideoControlledMotionGenerationInput, source: Path,
        published: Path, evidence: Path, cancellation: VideoMediaProcessCancellation,
        run: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult) {
        val count = input.motion.endFrameExclusive - input.motion.startFrame
        require(input.motion.startFrame == 0L)
        val directory = Files.createDirectory(evidence)
        val probe = PreviewProductionRun.importProbe(config.budget, run)
        val frames = sampleFrames(count)
        val records = mutableListOf<String>()
        for ((label, clip) in listOf("source" to source, "published" to published)) {
            val digest = PreviewPreflight.sha(clip)
            val measurement = probe.inspectTake(VideoMediaProbeRequest(config.tools, clip, directory.resolve("$label-measurement")), cancellation)
            require(measurement.sha256 == digest && measurement.videoCodec == "h264" &&
                measurement.width == input.motion.descriptor.width && measurement.height == input.motion.descriptor.height &&
                measurement.sampleAspectRatio == VideoMediaRational(1, 1) && measurement.frameRate == VideoMediaRational(30, 1) &&
                measurement.decodedFrameCount == count && measurement.videoStreamCount == 1 &&
                measurement.audioStreamCount == 0 && measurement.otherStreamCount == 0 &&
                java.math.BigInteger.valueOf(measurement.videoDurationPts) * java.math.BigInteger.valueOf(measurement.videoTimeBase.numerator) * java.math.BigInteger.valueOf(30) ==
                java.math.BigInteger.valueOf(count) * java.math.BigInteger.valueOf(measurement.videoTimeBase.denominator)) { "Native preview media contract differs" }
            val decoded = mutableMapOf<Long, BufferedImage>()
            for (frame in frames) {
                val png = directory.resolve("$label-$frame.png")
                val tool = input.motion.descriptor.runtime.ffmpeg
                run(VideoMediaProcessRequest(Path.of(tool.ownedPath!!), tool.sha256,
                    listOf("-nostdin", "-hide_banner", "-v", "error", "-xerror", "-protocol_whitelist", "file,pipe",
                        "-i", clip.toString(), "-vf", "select=eq(n\\,$frame)", "-vsync", "0", "-frames:v", "1",
                        "-an", "-sn", "-dn", "-c:v", "png", "-f", "image2", png.toString()),
                    directory.resolve("$label-$frame-process"), Duration.ofSeconds(30), memoryLimitBytes = config.budget.memoryBytes), cancellation)
                val image = readSample(png)
                val rendered = source.parent.resolve("encode-frames/frame-${frame.toString().padStart(8, '0')}.png")
                checkPixels(readSample(rendered), image)
                decoded[frame] = image
                records += """{"clip":"$label","clipSha256":"$digest","frame":$frame,"width":${image.width},"height":${image.height},"pngSha256":"${PreviewPreflight.sha(png)}","renderedSha256":"${PreviewPreflight.sha(rendered)}","blink":${blink(frame)}}"""
            }
            val closed = frames.maxBy(::blink)
            require(blink(closed) > 0.25) { "Missing authored blink phase" }
            val a = decoded.getValue(0).getRGB(20, 25)
            val b = decoded.getValue(closed).getRGB(20, 25)
            require(listOf(0, 8, 16).maxOf { kotlin.math.abs(((a shr it) and 255) - ((b shr it) and 255)) } >= 15) {
                "Authored blink phase is not visible in decoded subject pixels"
            }
            require(PreviewPreflight.sha(clip) == digest) { "Clip changed during frame inspection" }
        }
        Files.writeString(directory.resolve("frames.json"), """{"frameCount":$count,"samples":[${records.joinToString(",")}],"approval":"NOT_REVIEWED"}""", CREATE_NEW)
    }
}

/** The fixture is technical artwork, not a human-approved production image. */
internal object PreviewProductionRun {
    internal fun boundedImportRequest(request: VideoMediaProcessRequest, budget: PreviewBudget) =
        request.copy(memoryLimitBytes = minOf(request.memoryLimitBytes ?: budget.memoryBytes, budget.memoryBytes))

    internal fun importProbe(budget: PreviewBudget,
        run: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult =
            { request, cancellation -> VideoMediaProcess().run(request, cancellation) }): VideoMediaProbe =
        VideoMediaProbe { request, cancellation ->
            // An import must never remove or raise a native ceiling; the same owned
            // process supervisor covers probing, full decode, and silent remux.
            run(boundedImportRequest(request, budget), cancellation)
        }

    internal fun recover(config: PreviewConfiguration, ticket: PreviewTicket, outputId: String,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()) {
        val protected = listOf(Path.of(System.getProperty("user.dir")).toRealPath().resolve("docs"))
        val root = config.output.resolve("project")
        val publication = root.resolve("controlled-outputs")
        val store = VideoProjectStore(protected)
        val before = store.open(root)
        val jobs = VideoJobStore(config.output.resolve("jobs"), before.id, protected)
        val ledger = jobs.snapshot()
        val noNative: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult = { _, _ ->
            error("Recovery must not launch native work")
        }
        val stage = VideoControlledMediaStage(jobs, before.id, root, publication,
            renderer = VideoMotionRenderer(runProcess = noNative), runProcess = noNative)
        val service = VideoClipGeneration(VideoScenePreparation(), VideoJobCoordinator(before.id, jobs, listOf(stage)),
            VideoResultImport(store, importProbe(config.budget, noNative), controlledOutputRoot = publication),
            VideoMotionRenderer(), VideoControlledMediaStage.BACKEND_ID, config.budget.policy(), projects = store)
        val session = VideoProjectSession(root, before)
        val observed = service.reconcile(session, ticket.requestId, ticket.attemptId) as? VideoJobResult.Accepted
            ?: error("Reconstructed attempt could not be reconciled")
        require(observed.attempt?.status == VideoGenerationAttemptStatus.SUCCEEDED && observed.job.currentOutputId == outputId)
        val imported = service.importCompleted(VideoCompletedTakeImport(session, before.revision,
            ticket.requestId, ticket.attemptId, outputId), cancellation) as? VideoClipGenerationResult.Imported
            ?: error("Reconstructed exact reimport rejected")
        require(imported.result.project == before && store.open(root) == before && jobs.snapshot() == ledger) {
            "Recovery changed publication, selection, revision or attempts"
        }
    }

    fun run(config: PreviewConfiguration,
        runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult =
            { request, cancellation -> VideoMediaProcess().run(request, cancellation) },
        freeMemory: () -> Long = {
            (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)?.freeMemorySize ?: -1L
        },
        sample: ((Long, Long) -> Long)? = null) {
        val output = Files.createDirectory(config.output) // never reuse partial evidence
        val budget = config.budget
        val policy = budget.policy()
        val budgets = output.resolve("budgets.json")
        Files.writeString(budgets, """{"jobs":[150,600,900],"wallMillis":${budget.wallMillis},"memoryBytes":${budget.memoryBytes},"stagingBytes":${budget.stagingBytes},"outputBytes":${budget.outputBytes},"diskReserveBytes":${budget.diskReserveBytes},"concurrentNativeProcesses":${budget.concurrentNativeProcesses},"observedPeakMemoryBytes":null}""", CREATE_NEW)
        // This receipt precedes *all* native work. Partial jobs and failed reports stay in this directory.
        val protected = listOf(Path.of(System.getProperty("user.dir")).toRealPath().resolve("docs"))
        val store = VideoProjectStore(protected)
        val lifecycle = VideoProjectLifecycle(store)
        val projectRoot = output.resolve("project")
        var session = (lifecycle.create(CreateVideoProject(projectRoot, "Owned preview proof", "preview-proof"))
            as VideoProjectLifecycleResult.Opened).session
        val images = output.resolve("artwork").also(Files::createDirectory)
        fun picture(name: String, color: Color, transparent: Boolean, width: Int, height: Int): Path {
            val file = images.resolve("$name.png")
            if (Files.exists(file, NOFOLLOW_LINKS)) return file
            val image = BufferedImage(width, height, if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            try {
                g.color = color
                if (transparent) g.fillOval(2, 2, width - 4, height - 4) else g.fillRect(0, 0, width, height)
            } finally { g.dispose() }
            check(ImageIO.write(image, "png", file.toFile()))
            return file
        }
        fun imported(id: String, color: Color, transparent: Boolean, width: Int, height: Int, role: VideoReferenceRole): VideoVersionedId {
            val source = picture(id, color, transparent, width, height)
            val result = VideoAssetImport(lifecycle, VideoImageFiles(), idFactory = { id }).import(session, ImportVideoAsset(source, role))
            return when (result) {
                is VideoAssetImportResult.Imported -> { session = result.session; result.asset.id }
                is VideoAssetImportResult.Reused -> { session = result.session; result.asset.id }
                else -> error("Artwork import: $result")
            }
        }
        val scenes = VideoPreparedSceneStore(store)
        val guidelines = VideoPromptCompiler().decodeGuidelines(checkNotNull(javaClass.getResourceAsStream("/video/video-generation-guidelines.json")).use { it.readBytes() })
        val capabilities = VideoPromptBackendCapabilities("controlled-local", "preview-v1", true, 1,
            setOf(VideoReferenceRole.COMPLETE_SCENE), false, VideoGuidanceKind.entries.toSet(), 1, 30,
            emptyList(), emptyList(), emptyList(), VideoDependencyPin("controlled-preview", "a".repeat(64)))
        val jobs = VideoJobStore(output.resolve("jobs"), "preview-proof", protected)
        val publication = Files.createDirectory(projectRoot.resolve("controlled-outputs"))
        val coordinator = VideoJobCoordinator("preview-proof", jobs,
            listOf(VideoControlledMediaStage(jobs, "preview-proof", projectRoot, publication,
                renderer = VideoMotionRenderer(runProcess = runProcess), hostFreeMemoryBytes = freeMemory,
                runProcess = runProcess)))
        val boundedProbe = importProbe(budget, runProcess)
        val service = VideoClipGeneration(VideoScenePreparation(), coordinator,
            VideoResultImport(store, boundedProbe, controlledOutputRoot = publication, protectedMidiRoots = protected),
            VideoMotionRenderer(), VideoControlledMediaStage.BACKEND_ID, policy, projects = store)
        PreviewLadder.execute(output, budget, object : PreviewJobServices {
            override fun admit(frames: Long): PreviewTicket {
                // Reopen each imported original through the production services. Identical
                // artwork is reused immutably; each prepared scene is a new versioned input.
                val finished = imported("finished", Color(45, 55, 75), false, 320, 180, VideoReferenceRole.COMPLETE_SCENE)
                val subject = imported("subject", Color(170, 100, 150), true, 20, 30, VideoReferenceRole.SUBJECT)
                val clean = imported("clean", Color(50, 60, 80), false, 320, 180, VideoReferenceRole.ENVIRONMENT)
                val pose = imported("pose", Color(230, 220, 60), true, 20, 30, VideoReferenceRole.SUBJECT)
                val bounds = VideoRect("scene", 10.0, 10.0, 20.0, 30.0)
                val preparation = VideoPreparedSceneImport(store, scenes).import(projectRoot, session.project.revision,
                    PrepareVideoAnimationAssets(VideoVersionedId("scene-$frames", 1), finished,
                        subjectLayers = listOf(VideoPlacedAnimationAsset("subject", subject, bounds)),
                        cleanBackground = VideoPlacedAnimationAsset("clean", clean, VideoRect("scene", 0.0, 0.0, 320.0, 180.0)),
                        poses = listOf(VideoPoseAnimationAsset(VideoPlacedAnimationAsset("blink", pose, bounds), "subject"))))
                val prepared = preparation as? VideoPreparedSceneImportResult.Saved ?: error("Scene preparation: $preparation")
                session = VideoProjectSession(projectRoot, prepared.project)
                val scene = scenes.load(projectRoot, prepared.scene.id)
                val library = VideoAssetImport(lifecycle, VideoImageFiles()).open(projectRoot) as VideoAssetLibraryResult.Loaded
                val look = (VideoSceneLooks().select(session.project, library.assets, finished) as VideoSceneLookSelectionResult.Selected).look
                val record = session.project.preparedSceneVersions.single { it.id == scene.id }
                val pins = (listOf(record.artifact) + record.consumedArtifacts).distinct().mapIndexed { i, artifact ->
                    VideoGenerationDependencyPin("prepared-$i", artifact.sha256, store.resolveArtifact(projectRoot, artifact).toString())
                }
                val controls = scene.motionCapabilities.single { it.control == VideoMotionControl.POSE_BLEND }
                val admission = service.generate(VideoClipGenerationRequest(session.project, session.project.revision,
                    look, scene, VideoSceneMotionRequest("Blink gently without changing the artwork.", listOf(
                        VideoSceneMotionControlRequest("blink", VideoSceneMotionIntent.BLINK, controls.id, 0.0, 1.0, 0.5))),
                    capabilities, guidelines, pins, config.runtime, 0, frames, 73,
                    maximumAttempts = 1, requestId = "preview-$frames", projectRoot = projectRoot, expectedCanvasVersion = "0.1.80"))
                val admitted = admission as? VideoClipGenerationResult.Admitted ?: error("Controlled admission: $admission")
                val attempt = requireNotNull((admitted.result as VideoJobResult.Accepted).attempt)
                return PreviewTicket(admitted.request.id, attempt.id)
            }
            override fun observe(ticket: PreviewTicket): PreviewObservation {
                val result = service.reconcile(session, ticket.requestId, ticket.attemptId) as? VideoJobResult.Accepted
                    ?: error("Durable preview attempt could not be reconciled: ${ticket.requestId}")
                return PreviewObservation(result.attempt?.status, result.job.currentOutputId)
            }
            override fun import(ticket: PreviewTicket, outputId: String, cancellation: VideoMediaProcessCancellation): PreviewImported {
                val result = service.importCompleted(VideoCompletedTakeImport(session, session.project.revision,
                    ticket.requestId, ticket.attemptId, outputId), cancellation) as? VideoClipGenerationResult.Imported
                    ?: error("Guarded take import rejected for ${ticket.requestId}")
                session = VideoProjectSession(projectRoot, result.result.project)
                val job = jobs.snapshot().jobs.single { it.request.id == ticket.requestId }
                val input = job.request.input as VideoControlledMotionGenerationInput
                val source = publication.resolve(requireNotNull(job.outputs.single { it.id == outputId }.relativePath))
                PreviewFrameEvidence.inspect(config, input, source,
                    store.resolveArtifact(projectRoot, result.result.take.artifact),
                    output.resolve("frames-${input.motion.endFrameExclusive}"), cancellation, runProcess)
                recover(config, ticket, outputId, cancellation)
                return PreviewImported(result.result.take.id.id,
                    requireNotNull(result.result.take.sourceMeasurement).sha256,
                    requireNotNull(result.result.take.publishedMeasurement).sha256,
                    requireNotNull(result.result.take.sourceMeasurement).bytes,
                    requireNotNull(result.result.take.publishedMeasurement).bytes,
                    result.result.project.revision)
            }
            override fun cancel(ticket: PreviewTicket) {
                service.cancel(session, ticket.requestId, ticket.attemptId)
            }
        }, sample = sample, freeMemory = freeMemory)
        println("VIDEO_PREVIEW_PROBE_COMPLETED output=$output (technical fixture; real host proof/review is separate)")
    }
}
