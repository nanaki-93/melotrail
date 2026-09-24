package app.melotrail.video

import app.melotrail.video.adapter.VideoMediaProcess
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.adapter.VideoMediaProcessException
import app.melotrail.video.adapter.VideoMediaProcessFailure
import app.melotrail.video.adapter.VideoMediaProcessRequest
import app.melotrail.video.adapter.VideoMediaProcessTestHooks
import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMediaProbeException
import app.melotrail.video.adapter.VideoMediaProbeFailure
import app.melotrail.video.adapter.VideoMediaProbeRequest
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.concurrent.thread
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class VideoMediaProcessTest {
    @TempDir lateinit var root: Path

    private lateinit var javaExecutable: Path
    private lateinit var javaSha256: String
    private lateinit var classpath: String
    private val jobNumber = AtomicInteger()
    private val fixtureOwnerArgument = "-Dmelotrail.fixture.owner=${UUID.randomUUID()}"

    @BeforeEach
    fun requireNativeFixtureHost() {
        assertEquals("Mac OS X", System.getProperty("os.name"), "V12a native fixtures must run on macOS")
        assertTrue(System.getProperty("os.arch") in setOf("aarch64", "arm64"), "V12a fixtures require arm64")
        javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath()
        javaSha256 = digest(javaExecutable)
        val launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val loaderEntries = generateSequence(Thread.currentThread().contextClassLoader) { it.parent }
            .filterIsInstance<URLClassLoader>()
            .flatMap { it.urLs.asSequence() }
            .filter { it.protocol == "file" }
            .map { Path.of(it.toURI()).toString() }
        val propertyEntries = System.getProperty("java.class.path").split(File.pathSeparator).asSequence().map {
            Path.of(it).let { entry -> if (entry.isAbsolute) entry else launchDirectory.resolve(entry).normalize() }.toString()
        }
        classpath = (loaderEntries + propertyEntries).distinct().joinToString(File.pathSeparator)
    }

    @AfterEach
    fun noFixtureProcessShouldRemain() {
        // A separate run of this same class has a different token and must never be signaled.
        val survivors = ProcessHandle.allProcesses().filter { process ->
            val arguments = process.info().arguments().orElse(emptyArray()).toList()
            fixtureOwnerArgument in arguments && VideoMediaProcessFixture::class.java.name in arguments
        }.toList()
        survivors.forEach { it.destroyForcibly() }
        assertTrue(survivors.isEmpty(), "Owned fixture processes escaped supervision: ${survivors.map { it.pid() }}")
    }

    @Test
    fun `owned process memory growth terminates the native group instead of exceeding its persisted ceiling`() {
        val ready = root.resolve("memory-growth-ready")
        val exceeded = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "allocate-memory", listOf(ready.toString())).copy(
                memoryLimitBytes = 192L * 1024 * 1024, timeout = Duration.ofSeconds(8)))
        }
        assertEquals(VideoMediaProcessFailure.OUTPUT_LIMIT, exceeded.failure)
        assertTrue(Files.exists(ready), "The child must have started allocating; prelaunch rejection is not a memory-growth regression")
        assertTrue(exceeded.message.orEmpty().contains("memory limit"), exceeded.message)
    }

    @Test
    fun `launches pinned executable with literal Unicode and spaced arguments in private cwd`() {
        val input = Files.writeString(root.resolve("référence input.txt"), "immutable café bytes")
        val before = Files.readAllBytes(input)
        val shellExpansion = root.resolve("must-not-exist")
        val job = nextJob("Unicode job café")

        val result = VideoMediaProcess().run(request(
            job = job,
            selector = "report",
            extra = listOf(input.toString(), "hello world", "東京", "$(touch ${shellExpansion})"),
        ))

        assertEquals(0, result.exitCode)
        assertEquals(job.toRealPath(), Path.of(lineValue(result.stdout.text, "cwd")).toRealPath())
        assertTrue(result.stdout.text.contains("arg=hello world"))
        assertTrue(result.stdout.text.contains("arg=東京"))
        assertTrue(result.stdout.text.contains("arg=$(touch ${shellExpansion})"))
        assertTrue(result.stdout.text.contains("input=immutable café bytes"))
        assertFalse(Files.exists(shellExpansion), "argv must never be interpreted by a shell")
        assertContentEquals(before, Files.readAllBytes(input))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(job)))
    }

    @Test
    fun `rejects bad executable paths pins and existing jobs before launch`() {
        val wrongPin = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "exit", listOf("0")).copy(executableSha256 = "0".repeat(64)))
        }
        assertEquals(VideoMediaProcessFailure.PIN_MISMATCH, wrongPin.failure)
        assertTrue(wrongPin.message.orEmpty().contains("found"))

        val symlink = Files.createSymbolicLink(root.resolve("java alias"), javaExecutable)
        val symlinkFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "exit", listOf("0")).copy(executable = symlink))
        }
        assertEquals(VideoMediaProcessFailure.INVALID_REQUEST, symlinkFailure.failure)

        val missingFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "exit", listOf("0")).copy(executable = root.resolve("missing")))
        }
        assertEquals(VideoMediaProcessFailure.INVALID_REQUEST, missingFailure.failure)

        val dotted = javaExecutable.parent.resolve(".").resolve(javaExecutable.fileName)
        val dottedFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "exit", listOf("0")).copy(executable = dotted))
        }
        assertEquals(VideoMediaProcessFailure.INVALID_REQUEST, dottedFailure.failure)
        assertTrue(dottedFailure.message.orEmpty().contains("'.' or '..'"))

        val dottedJob = root.resolve(".").resolve("redirected-job")
        val dottedJobFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(dottedJob, "exit", listOf("0")))
        }
        assertEquals(VideoMediaProcessFailure.INVALID_REQUEST, dottedJobFailure.failure)
        assertFalse(Files.exists(root.resolve("redirected-job")))

        val existingJob = Files.createDirectory(nextJob()).also { Files.writeString(it.resolve("keep.txt"), "keep") }
        val existingFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(existingJob, "exit", listOf("0")))
        }
        assertEquals(VideoMediaProcessFailure.INVALID_REQUEST, existingFailure.failure)
        assertEquals("keep", Files.readString(existingJob.resolve("keep.txt")))
    }

    @Test
    fun `bounds and drains noisy stdout and stderr without hanging`() {
        listOf("stdout", "stderr").forEach { stream ->
            val failure = assertFailsWith<VideoMediaProcessException> {
                VideoMediaProcess().run(request(nextJob(), "noisy", listOf(stream, "200000")).copy(
                    maxStdoutBytes = 1_024,
                    maxStderrBytes = 1_024,
                ))
            }

            assertEquals(VideoMediaProcessFailure.OUTPUT_LIMIT, failure.failure)
            val output = if (stream == "stdout") failure.stdout else failure.stderr
            assertTrue(output.truncated)
            assertTrue(output.totalBytes > 1_024)
            assertTrue(output.text.length < 2_000)
        }
    }

    @Test
    fun `times out and cancels only its owned process group`() {
        val timedOut = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "sleep", listOf("30000")).copy(
                timeout = Duration.ofMillis(150),
            ))
        }
        assertEquals(VideoMediaProcessFailure.TIMED_OUT, timedOut.failure)

        val unrelated = ProcessBuilder(fixtureArguments("sleep", "5000")).start()
        try {
            val ready = root.resolve("owned-ready.txt")
            val cancellation = VideoMediaProcessCancellation()
            val future = CompletableFuture.supplyAsync {
                runCatching {
                    VideoMediaProcess().run(request(nextJob(), "ready-and-sleep", listOf(ready.toString(), "30000")), cancellation)
                }.exceptionOrNull()
            }
            awaitFile(ready)
            cancellation.cancel()
            val cancelled = future.get(5, TimeUnit.SECONDS)
            assertTrue(cancelled is VideoMediaProcessException)
            assertEquals(VideoMediaProcessFailure.CANCELLED, (cancelled as VideoMediaProcessException).failure)
            assertTrue(unrelated.isAlive, "Cancellation must not signal an unrelated process")
        } finally {
            unrelated.destroyForcibly()
            unrelated.waitFor(3, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `fixture PID readiness stays absent until empty and partial writes complete`() {
        val ready = root.resolve("atomic-ready.txt")
        val pid = 12_345L
        var stagedPath: Path? = null

        publishFixturePid(ready, pid) { staged, value ->
            stagedPath = staged
            assertEquals(0L, Files.size(staged))
            assertFalse(Files.exists(ready), "An empty staged file must not admit cancellation")

            Files.writeString(staged, value.take(2))
            assertEquals("12", Files.readString(staged))
            assertFalse(Files.exists(ready), "Even a parseable partial PID must not admit cancellation")

            Files.writeString(staged, value)
            assertFalse(Files.exists(ready), "Readiness must wait for the completed write to return")
        }

        awaitFile(ready)
        assertEquals(pid.toString(), Files.readString(ready))
        assertEquals(pid, Files.readString(ready).trim().toLong())
        assertFalse(Files.exists(checkNotNull(stagedPath)), "Publication must move the staged file")
    }

    @Test
    fun `exit racing group signal clears Darwin EPERM only after confirmed cleanup`() {
        // Exercise both initial termination and escalation with real kernel calls. The hook
        // creates an exit after the supervisor's liveness query, without reaping its leader.
        listOf(15 to false, 9 to false, 9 to true).forEach { (racedSignal, holdEof) ->
            val ready = root.resolve("signal-race-$racedSignal-$holdEof.txt")
            val cancellation = VideoMediaProcessCancellation()
            val events = mutableListOf<String>()
            val future = CompletableFuture.supplyAsync {
                runCatching {
                    VideoMediaProcess(VideoMediaProcessTestHooks(holdOutputEof = holdEof, onLifecycle = { event, pid ->
                        events.add(event)
                        if (event == "signal:$racedSignal") {
                            check(FixtureLibC.instance.kill(pid, 9) == 0)
                            val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
                            var waitable = false
                            Memory(104).use { info ->
                                while (!waitable && System.nanoTime() < deadline) {
                                    info.clear()
                                    check(FixtureLibC.instance.waitid(1, pid, info, 4 or 1 or 32) == 0)
                                    waitable = info.getInt(12) == pid
                                    if (!waitable) Thread.sleep(1)
                                }
                            }
                            check(waitable) { "Owned leader must be a real unreaped zombie before the raced signal" }
                        }
                    })).run(request(nextJob(), "ignore-term-and-sleep", listOf(ready.toString())), cancellation)
                }.exceptionOrNull()
            }
            awaitFile(ready)
            cancellation.cancel()
            val failure = future.get(6, TimeUnit.SECONDS)
            assertTrue(failure is VideoMediaProcessException)
            assertEquals(VideoMediaProcessFailure.CANCELLED, failure.failure)
            assertEquals(9, failure.signal, "Final waitpid must retain the actual native exit status")
            assertTrue("signal-error:1" in events, "Fixture must reproduce Darwin group-kill EPERM")
            if (holdEof) {
                assertTrue(failure.message.orEmpty().contains("Cleanup incomplete"))
                assertTrue(failure.message.orEmpty().contains("Operation not permitted"))
                assertTrue(failure.suppressed.flatMap { it.suppressed.toList() }.any {
                    it.message.orEmpty().contains("Operation not permitted")
                }, "Unconfirmed cleanup must retain the original group permission failure")
            } else {
                assertTrue(failure.suppressed.isEmpty(), "Confirmed cleanup must not retain a stale permission failure")
                assertFalse(failure.message.orEmpty().contains("Cleanup incomplete"))
            }
            assertEquals("reaped", events.last(), "No group observation or signal may follow the final reap")
            assertEquals(1, events.count { it == "reaped" })
            awaitProcessExit(Files.readString(ready).trim().toLong())
        }
    }

    @Test
    fun `cancellation before or racing launch creates no surviving child`() {
        val beforeLaunch = VideoMediaProcessCancellation().also { it.cancel() }
        val absentJob = nextJob()
        val failure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(absentJob, "sleep", listOf("30000")), beforeLaunch)
        }
        assertEquals(VideoMediaProcessFailure.CANCELLED, failure.failure)
        assertFalse(Files.exists(absentJob))

        repeat(8) {
            val cancellation = VideoMediaProcessCancellation()
            val job = nextJob("launch-race-$it")
            val future = CompletableFuture.supplyAsync {
                runCatching {
                    VideoMediaProcess().run(request(job, "sleep", listOf("30000")), cancellation)
                }.exceptionOrNull()
            }
            cancellation.cancel()
            val raced = future.get(5, TimeUnit.SECONDS)
            assertTrue(raced is VideoMediaProcessException)
            assertEquals(VideoMediaProcessFailure.CANCELLED, (raced as VideoMediaProcessException).failure)
        }
    }

    @Test
    fun `early parent exit terminates and reports its live descendant`() {
        val pidFile = root.resolve("descendant-pid.txt")
        val failure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(
                request(nextJob(), "fork-live-descendant", listOf(pidFile.toString(), "30000")),
            )
        }

        assertEquals(VideoMediaProcessFailure.UNEXPECTED_DESCENDANTS, failure.failure)
        assertTrue(failure.message.orEmpty().contains("descendants remained"))
        assertTrue(Files.exists(pidFile), "Parent fixture must observe the live descendant before exiting")
        val descendantPid = Files.readString(pidFile).trim().toLong()
        awaitProcessExit(descendantPid)
    }

    @Test
    fun `retains exited leader identity until owned group signaling finishes`() {
        val pidFile = root.resolve("retained-leader-descendant.txt")
        val events = mutableListOf<String>()
        var nativeLeaderStillWaitable = false
        val failure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess(VideoMediaProcessTestHooks(onLifecycle = { event, pid ->
                events.add(event)
                if (event == "observed") {
                    Memory(104).use { info ->
                        info.clear()
                        val result = FixtureLibC.instance.waitid(1, pid, info, 4 or 1 or 32)
                        nativeLeaderStillWaitable = result == 0 && info.getInt(12) == pid
                    }
                }
            })).run(request(nextJob(), "fork-live-descendant", listOf(pidFile.toString(), "30000")))
        }
        assertEquals(VideoMediaProcessFailure.UNEXPECTED_DESCENDANTS, failure.failure)
        assertTrue(nativeLeaderStillWaitable, "WNOWAIT must retain the real exited leader before group signaling")
        assertTrue(events.any { it.startsWith("signal:") })
        assertEquals("reaped", events.last(), "The final native reap must follow every group signal")
        assertEquals(1, events.count { it == "reaped" })
        awaitProcessExit(Files.readString(pidFile).trim().toLong())
    }

    @Test
    fun `reports launch nonzero disk and signal failures with bounded diagnostics`() {
        val broken = Files.writeString(root.resolve("broken executable"), "not a Mach-O executable")
        Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("rwx------"))
        val launchFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "unused").copy(
                executable = broken,
                executableSha256 = digest(broken),
                arguments = emptyList(),
            ))
        }
        assertEquals(VideoMediaProcessFailure.LAUNCH_FAILED, launchFailure.failure)
        assertTrue(launchFailure.message.orEmpty().contains("launch"))

        val diskFailure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess().run(request(nextJob(), "disk-error"))
        }
        assertEquals(VideoMediaProcessFailure.NONZERO_EXIT, diskFailure.failure)
        assertEquals(28, diskFailure.exitCode)
        assertTrue(diskFailure.stderr.text.contains("No space left on device"))
        assertTrue(diskFailure.message.orEmpty().contains("No space left on device"))

        val signalFailure = assertFailsWith<VideoMediaProcessException> {
            // SIGTERM can be translated into System.exit(143) by the JVM's shutdown handler.
            VideoMediaProcess().run(request(nextJob(), "signal-self", listOf("9")))
        }
        assertEquals(VideoMediaProcessFailure.SIGNAL_EXIT, signalFailure.failure)
        assertEquals(9, signalFailure.signal)
    }

    @Test
    fun `bounds missing EOF cleanup after a native timeout`() {
        val closed = mutableListOf<Int>()
        val started = System.nanoTime()
        val failure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess(VideoMediaProcessTestHooks(
                holdOutputEof = true,
                onDescriptorClosed = { closed.add(it) },
            )).run(request(nextJob(), "sleep", listOf("30000")).copy(timeout = Duration.ofMillis(150)))
        }
        assertEquals(VideoMediaProcessFailure.TIMED_OUT, failure.failure)
        assertIncompleteCleanup(failure, started, closed)
        assertTrue(failure.message.orEmpty().contains("stdout EOF=false"))
    }

    @Test
    fun `bounds unconfirmed completion after native output overflow`() {
        val closed = mutableListOf<Int>()
        val started = System.nanoTime()
        val failure = assertFailsWith<VideoMediaProcessException> {
            VideoMediaProcess(VideoMediaProcessTestHooks(
                holdCompletion = true,
                onDescriptorClosed = { closed.add(it) },
            )).run(request(nextJob(), "noisy", listOf("stderr", "200000")).copy(maxStderrBytes = 1_024))
        }
        assertEquals(VideoMediaProcessFailure.OUTPUT_LIMIT, failure.failure)
        assertIncompleteCleanup(failure, started, closed)
        assertTrue(failure.stderr.truncated)
        assertTrue(failure.stderr.totalBytes > 1_024)
        assertTrue(failure.stderr.text.length < 2_000)
    }

    @Test
    fun `interruption during run and cleanup preserves cancellation and reaps native child`() {
        val ready = root.resolve("interrupted-ready.txt")
        val closed = mutableListOf<Int>()
        val result = CompletableFuture<Pair<Throwable?, Boolean>>()
        val started = System.nanoTime()
        val worker = thread(name = "owned-video-interruption-test") {
            val failure = runCatching {
                VideoMediaProcess(VideoMediaProcessTestHooks(
                    holdOutputEof = true,
                    onDescriptorClosed = { closed.add(it) },
                )).run(request(nextJob(), "ready-and-sleep", listOf(ready.toString(), "30000")))
            }.exceptionOrNull()
            result.complete(failure to Thread.currentThread().isInterrupted)
        }
        try {
            awaitFile(ready)
            worker.interrupt()
            // Repeated interrupts must not bypass cleanup or restart its deadline.
            repeat(10) {
                Thread.sleep(20)
                worker.interrupt()
            }
            val (error, interruptRestored) = result.get(6, TimeUnit.SECONDS)
            assertTrue(error is VideoMediaProcessException)
            val failure = error as VideoMediaProcessException
            assertEquals(VideoMediaProcessFailure.CANCELLED, failure.failure)
            assertIncompleteCleanup(failure, started, closed)
            assertTrue(interruptRestored)
            awaitProcessExit(Files.readString(ready).trim().toLong())
        } finally {
            worker.interrupt()
            worker.join(6_000)
            assertFalse(worker.isAlive, "Supervisor thread must finish bounded cleanup")
        }
    }

    @Test
    fun `failed native launch closes each pipe once despite descriptor reuse`() {
        val broken = Files.writeString(root.resolve("invalid native executable"), "not executable machine code")
        Files.setPosixFilePermissions(broken, PosixFilePermissions.fromString("rwx------"))
        repeat(8) {
            val closed = mutableListOf<Int>()
            val replacementDescriptors = mutableListOf<Int>()
            try {
                val failure = assertFailsWith<VideoMediaProcessException> {
                    VideoMediaProcess(VideoMediaProcessTestHooks(onDescriptorClosed = { descriptor ->
                        closed.add(descriptor)
                        // Reuse freed numbers immediately with new, unrelated native resources.
                        val replacement = IntArray(2)
                        check(FixtureLibC.instance.pipe(replacement) == 0)
                        replacementDescriptors.addAll(replacement.toList())
                    })).run(request(nextJob(), "unused").copy(
                        executable = broken,
                        executableSha256 = digest(broken),
                        arguments = emptyList(),
                    ))
                }
                assertEquals(VideoMediaProcessFailure.LAUNCH_FAILED, failure.failure)
                assertEquals(4, closed.size)
                assertEquals(4, closed.distinct().size, "Each of the four pipe descriptors has one close owner")
                assertTrue(closed.any { it in replacementDescriptors }, "The fixture must exercise descriptor reuse")
                replacementDescriptors.forEach { descriptor ->
                    assertTrue(FixtureLibC.instance.fcntl(descriptor, 1, 0) >= 0, "Reused descriptor $descriptor was closed")
                }
                assertNoDrainThreads()
            } finally {
                replacementDescriptors.forEach { FixtureLibC.instance.close(it) }
            }
        }
    }

    @Test
    fun `media probe confines literal local input and creates distinct owned artifacts`() {
        val tools = mediaTools()
        val input = Files.write(root.resolve("source café 東京.mp4"), "immutable-owned-video".encodeToByteArray())
        val before = Files.readAllBytes(input)
        val requests = mutableListOf<VideoMediaProcessRequest>()
        val probe = VideoMediaProbe { request, _ ->
            requests += request
            fakeMediaProcess(request)
        }

        val result = probe.run(VideoMediaProbeRequest(tools, input, root.resolve("evidence café")))

        assertContentEquals(before, Files.readAllBytes(input))
        assertTrue(Files.isRegularFile(result.report))
        assertTrue(Files.isRegularFile(result.firstFrame))
        assertTrue(Files.isRegularFile(result.seekFrame))
        assertTrue(Files.isRegularFile(result.encodedVideo))
        assertFalse(digest(result.firstFrame) == digest(result.seekFrame))
        assertEquals(0, result.inputMetadata.audioStreamCount)
        assertEquals("h264", result.encodedMetadata.videoCodec)
        assertTrue(requests.all { it.arguments.none { argument -> argument.startsWith("http://") || argument.startsWith("https://") } })
        assertTrue(requests.filter { "-i" in it.arguments }.all { request ->
            val inputArguments = request.arguments.indices.filter { request.arguments[it] == "-i" }.map { request.arguments[it + 1] }
            inputArguments.all { it == input.toRealPath().toString() || it == result.encodedVideo.toString() }
        })
        assertTrue(requests.filter { "-i" in it.arguments }.all { request ->
            request.arguments.windowed(2).any { it == listOf("-protocol_whitelist", "file,pipe") }
        })
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(result.outputDirectory)))
    }

    @Test
    fun `media probe rejects URLs missing tools and existing outputs before launch`() {
        val tools = mediaTools()
        val input = Files.write(root.resolve("fixture.mp4"), byteArrayOf(1, 2, 3))
        var launches = 0
        val probe = VideoMediaProbe { request, _ ->
            launches += 1
            fakeMediaProcess(request)
        }

        val url = assertFailsWith<VideoMediaProbeException> {
            probe.run(VideoMediaProbeRequest(tools, Path.of("https://example.test/video.mp4"), root.resolve("url-output")))
        }
        assertEquals(VideoMediaProbeFailure.INVALID_REQUEST, url.failure)

        val existing = Files.createDirectory(root.resolve("existing-output"))
        Files.writeString(existing.resolve("keep.txt"), "keep")
        val collision = assertFailsWith<VideoMediaProbeException> {
            probe.run(VideoMediaProbeRequest(tools, input, existing))
        }
        assertEquals(VideoMediaProbeFailure.INVALID_REQUEST, collision.failure)
        assertEquals("keep", Files.readString(existing.resolve("keep.txt")))

        val missingTools = Files.createDirectory(root.resolve("missing-tools"))
        Files.writeString(missingTools.resolve(VideoMediaProbe.MANIFEST_NAME), mediaManifest(
            VideoMediaProbe.FFMPEG_SHA256,
            VideoMediaProbe.FFPROBE_SHA256,
        ))
        val missing = assertFailsWith<VideoMediaProbeException> {
            probe.run(VideoMediaProbeRequest(missingTools, input, root.resolve("missing-output")))
        }
        assertEquals(VideoMediaProbeFailure.TOOL_CONFIGURATION, missing.failure)

        val wrongPins = mediaTools()
        Files.writeString(wrongPins.resolve(VideoMediaProbe.MANIFEST_NAME), mediaManifest("0".repeat(64), VideoMediaProbe.FFPROBE_SHA256))
        val pinFailure = assertFailsWith<VideoMediaProbeException> {
            probe.run(VideoMediaProbeRequest(wrongPins, input, root.resolve("wrong-pin-output")))
        }
        assertEquals(VideoMediaProbeFailure.TOOL_CONFIGURATION, pinFailure.failure)
        assertEquals(0, launches)
    }

    @Test
    fun `media probe rejects malformed manifest types before launch or output creation`() {
        val tools = mediaTools()
        val manifestPath = tools.resolve(VideoMediaProbe.MANIFEST_NAME)
        val valid = Json.parseToJsonElement(Files.readString(manifestPath)).jsonObject
        val input = Files.write(root.resolve("immutable manifest fixture.mp4"), byteArrayOf(1, 2, 3))
        val inputBytes = Files.readAllBytes(input)
        val toolBytes = listOf("ffmpeg", "ffprobe").associateWith { Files.readAllBytes(tools.resolve(it)) }
        var launches = 0
        val probe = VideoMediaProbe { request, _ ->
            launches += 1
            fakeMediaProcess(request)
        }
        val wrongStringTypes = listOf<JsonElement>(
            JsonNull, JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()), JsonArray(emptyList()),
        )
        val cases = buildList<Pair<String, String>> {
            fun field(name: String, value: JsonElement?) {
                val fields = valid.toMutableMap()
                if (value == null) fields.remove(name) else fields[name] = value
                add("$name=$value" to JsonObject(fields).toString())
            }
            listOf<JsonElement?>(
                null, JsonNull, JsonPrimitive("x"), JsonPrimitive("1"), JsonPrimitive(true),
                JsonPrimitive(1.5), JsonPrimitive(2147483648L), JsonPrimitive(2),
                JsonObject(emptyMap()), JsonArray(emptyList()),
            ).forEach { field("version", it) }
            listOf(
                "schema", "distributionId", "installation", "sourceUrl", "sourceRevision",
                "sourceSha256", "ffmpegSha256", "ffprobeSha256",
            ).forEach { name ->
                field(name, null)
                wrongStringTypes.forEach { field(name, it) }
            }
            listOf("buildOptions", "notices").forEach { name ->
                field(name, null)
                listOf(JsonNull, JsonPrimitive("text"), JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()))
                    .forEach { field(name, it) }
                val validEntries = valid.getValue(name) as JsonArray
                // Retain required options so rejection proves the element type check itself.
                wrongStringTypes.forEach { field(name, JsonArray(validEntries + it)) }
            }
            listOf("sourceSha256", "ffmpegSha256", "ffprobeSha256").forEach { name ->
                listOf("x", "0".repeat(64), "A".repeat(64)).forEach { field(name, JsonPrimitive(it)) }
            }
            listOf("{", "[]", "null", "true", "1", "\"manifest\"").forEach { add("root=$it" to it) }
        }

        cases.forEachIndexed { index, (label, manifest) ->
            Files.writeString(manifestPath, manifest)
            val manifestBytes = Files.readAllBytes(manifestPath)
            val output = root.resolve("rejected-manifest-$index")

            val failure = assertFailsWith<VideoMediaProbeException>(label) {
                probe.run(VideoMediaProbeRequest(tools, input, output))
            }

            assertEquals(VideoMediaProbeFailure.TOOL_CONFIGURATION, failure.failure, label)
            assertEquals(0, launches, label)
            assertFalse(Files.exists(output), label)
            assertContentEquals(inputBytes, Files.readAllBytes(input), label)
            assertContentEquals(manifestBytes, Files.readAllBytes(manifestPath), label)
        }
        toolBytes.forEach { (name, bytes) -> assertContentEquals(bytes, Files.readAllBytes(tools.resolve(name)), name) }
    }

    @Test
    fun `media probe retains input and partial evidence on reported disk exhaustion`() {
        val tools = mediaTools()
        val input = Files.write(root.resolve("disk-fixture.mp4"), "owned".encodeToByteArray())
        val before = Files.readAllBytes(input)
        val output = root.resolve("disk-evidence")
        val probe = VideoMediaProbe { request, _ ->
            Files.createDirectory(request.workingDirectory)
            if (request.arguments.lastOrNull() == "encoded-silent.mp4") {
                throw VideoMediaProcessException(
                    VideoMediaProcessFailure.NONZERO_EXIT,
                    "Video media process exited with code 1: No space left on device",
                    stderr = app.melotrail.video.adapter.VideoMediaProcessOutput(
                        "av_interleaved_write_frame(): No space left on device",
                        58,
                        false,
                    ),
                    exitCode = 1,
                )
            }
            fakeMediaProcess(request, createWorkingDirectory = false)
        }

        val failure = assertFailsWith<VideoMediaProbeException> {
            probe.run(VideoMediaProbeRequest(tools, input, output))
        }

        assertEquals(VideoMediaProbeFailure.DISK_EXHAUSTED, failure.failure)
        assertEquals("silent-h264-encode", failure.operation)
        assertContentEquals(before, Files.readAllBytes(input))
        assertTrue(Files.isDirectory(output))
        assertTrue(Files.isRegularFile(output.resolve("first-frame/first-frame.png")))
        assertFalse(Files.exists(output.resolve("video-media-probe-report.json")))
    }

    @Test
    fun `fixture cleanup preserves another invocation token`() {
        val foreignToken = "-Dmelotrail.fixture.owner=${UUID.randomUUID()}"
        val unrelated = ProcessBuilder(fixtureArguments("sleep", "30000").map {
            if (it == fixtureOwnerArgument) foreignToken else it
        }).start()
        try {
            noFixtureProcessShouldRemain()
            assertTrue(unrelated.isAlive, "Cleanup must not kill another invocation of the same fixture class")
        } finally {
            unrelated.destroyForcibly()
            unrelated.waitFor(3, TimeUnit.SECONDS)
        }
    }

    private fun assertIncompleteCleanup(failure: VideoMediaProcessException, started: Long, closed: List<Int>) {
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(8), "Cleanup exceeded its bound")
        assertTrue(failure.message.orEmpty().contains("Cleanup incomplete"))
        assertTrue(failure.suppressed.any {
            it is VideoMediaProcessException && it.failure == VideoMediaProcessFailure.SUPERVISION_FAILED
        })
        assertTrue(failure.exitCode != null || failure.signal != null, "The native direct child must actually be reaped")
        assertEquals(4, closed.size)
        assertEquals(4, closed.distinct().size)
        assertNoDrainThreads()
    }

    private fun assertNoDrainThreads() {
        assertFalse(Thread.getAllStackTraces().keys.any {
            it.isAlive && it.name.startsWith("melotrail-video-") && it.name.endsWith("-drain")
        }, "No background pipe readers may survive a run")
    }

    private fun request(job: Path, selector: String, extra: List<String> = emptyList()) = VideoMediaProcessRequest(
        executable = javaExecutable,
        executableSha256 = javaSha256,
        arguments = listOf(fixtureOwnerArgument, "-cp", classpath, VideoMediaProcessFixture::class.java.name, selector) + extra,
        workingDirectory = job,
        timeout = Duration.ofSeconds(5),
        maxStdoutBytes = 16 * 1024,
        maxStderrBytes = 16 * 1024,
    )

    private fun fixtureArguments(vararg arguments: String): List<String> =
        listOf(javaExecutable.toString(), fixtureOwnerArgument, "-cp", classpath, VideoMediaProcessFixture::class.java.name) + arguments

    private fun nextJob(label: String = "job-${jobNumber.incrementAndGet()}"): Path = root.resolve(label)

    private fun mediaTools(): Path {
        val tools = Files.createDirectory(nextJob("media tools ${jobNumber.incrementAndGet()}"))
        listOf("ffmpeg", "ffprobe").forEach { name ->
            val executable = Files.copy(javaExecutable, tools.resolve(name))
            Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwx------"))
        }
        Files.writeString(tools.resolve(VideoMediaProbe.MANIFEST_NAME), mediaManifest(
            VideoMediaProbe.FFMPEG_SHA256,
            VideoMediaProbe.FFPROBE_SHA256,
        ))
        return tools
    }

    private fun mediaManifest(ffmpegSha256: String, ffprobeSha256: String): String = """
        {
          "schema": "melotrail-video-media-tools",
          "version": 1,
          "distributionId": "${VideoMediaProbe.DISTRIBUTION_ID}",
          "installation": "${VideoMediaProbe.INSTALLATION_STRATEGY}",
          "sourceUrl": "${VideoMediaProbe.SOURCE_URL}",
          "sourceRevision": "${VideoMediaProbe.SOURCE_REVISION}",
          "sourceSha256": "${VideoMediaProbe.SOURCE_SHA256}",
          "ffmpegSha256": "$ffmpegSha256",
          "ffprobeSha256": "$ffprobeSha256",
          "buildOptions": [${VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString { "\"$it\"" }}],
          "notices": ["FFmpeg LGPL-2.1-or-later; installed separately"]
        }
    """.trimIndent()

    private fun fakeMediaProcess(
        request: VideoMediaProcessRequest,
        createWorkingDirectory: Boolean = true,
    ): app.melotrail.video.adapter.VideoMediaProcessResult {
        if (createWorkingDirectory) Files.createDirectory(request.workingDirectory)
        val stdout = when {
            "-version" in request.arguments -> buildString {
                appendLine("${request.executable.fileName} version 9.0.1")
                append("configuration: ")
                append(VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" "))
            }
            request.executable.fileName.toString() == "ffprobe" -> """
                {
                  "streams": [{
                    "codec_name": "h264",
                    "codec_type": "video",
                    "width": 320,
                    "height": 180,
                    "avg_frame_rate": "24/1",
                    "nb_read_frames": "72"
                  }],
                  "format": {"duration": "3.000000"}
                }
            """.trimIndent()
            else -> ""
        }
        when (request.arguments.lastOrNull()) {
            "first-frame.png" -> Files.write(request.workingDirectory.resolve("first-frame.png"), byteArrayOf(1, 2, 3))
            "seek-frame.png" -> Files.write(request.workingDirectory.resolve("seek-frame.png"), byteArrayOf(4, 5, 6))
            "encoded-silent.mp4" -> Files.write(request.workingDirectory.resolve("encoded-silent.mp4"), byteArrayOf(7, 8, 9))
        }
        return app.melotrail.video.adapter.VideoMediaProcessResult(
            0,
            app.melotrail.video.adapter.VideoMediaProcessOutput(stdout, stdout.encodeToByteArray().size.toLong(), false),
            app.melotrail.video.adapter.VideoMediaProcessOutput("", 0, false),
            Duration.ofMillis(2),
            request.workingDirectory,
        )
    }

    private fun awaitFile(path: Path) {
        val deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos()
        while (!Files.exists(path) && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(Files.exists(path), "Fixture did not create $path")
    }

    private fun awaitProcessExit(pid: Long) {
        val deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos()
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), "Owned descendant $pid survived")
    }

    private fun lineValue(text: String, key: String): String = text.lineSequence()
        .first { it.startsWith("$key=") }
        .substringAfter('=')

    private fun digest(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private fun publishFixturePid(
    ready: Path,
    pid: Long = ProcessHandle.current().pid(),
    writePid: (Path, String) -> Unit = { staged, value -> Files.writeString(staged, value) },
) {
    // Existence is the cancellation handshake, so incomplete bytes must stay at a
    // sibling path until the writer has closed and an atomic rename publishes them.
    val staged = Files.createTempFile(ready.parent, ".fixture-pid-", ".tmp")
    try {
        writePid(staged, pid.toString())
        Files.move(staged, ready, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(staged)
    }
}

/** Native fixture entry point launched by [VideoMediaProcessTest]. */
object VideoMediaProcessFixture {
    @JvmStatic
    fun main(arguments: Array<String>) {
        when (val selector = arguments.firstOrNull()) {
            "report" -> {
                println("cwd=${Path.of("").toAbsolutePath()}")
                println("input=${Files.readString(Path.of(arguments[1]))}")
                arguments.drop(2).forEach { println("arg=$it") }
            }
            "noisy" -> {
                val bytes = ByteArray(arguments[2].toInt()) { if (arguments[1] == "stdout") 'O'.code.toByte() else 'E'.code.toByte() }
                if (arguments[1] == "stdout") System.out.write(bytes) else System.err.write(bytes)
                if (arguments[1] == "stdout") System.out.flush() else System.err.flush()
                Thread.sleep(30_000)
            }
            "sleep" -> Thread.sleep(arguments[1].toLong())
            "allocate-memory" -> {
                publishFixturePid(Path.of(arguments[1]))
                val blocks = ArrayList<ByteArray>()
                repeat(32) {
                    blocks += ByteArray(16 * 1024 * 1024) { 1 }
                    Thread.sleep(35)
                }
                check(blocks.size == 32)
                Thread.sleep(30_000)
            }
            "ready-and-sleep" -> {
                publishFixturePid(Path.of(arguments[1]))
                Thread.sleep(arguments[2].toLong())
            }
            "ignore-term-and-sleep" -> {
                FixtureLibC.instance.signal(15, Pointer(1)) // SIG_IGN; force the supervisor's bounded escalation.
                publishFixturePid(Path.of(arguments[1]))
                Thread.sleep(30_000)
            }
            "exit" -> exitProcess(arguments[1].toInt())
            "disk-error" -> {
                System.err.println("write failed: No space left on device (owned fixture)")
                exitProcess(28)
            }
            "signal-self" -> {
                FixtureLibC.instance.kill(ProcessHandle.current().pid().toInt(), arguments[1].toInt())
                Thread.sleep(30_000)
            }
            "fork-live-descendant" -> {
                ProcessBuilder(descendantCommand("ready-and-sleep", arguments[1], arguments[2])).inheritIO().start()
                val ready = Path.of(arguments[1])
                val deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos()
                while (!Files.exists(ready) && System.nanoTime() < deadline) Thread.sleep(10)
                check(Files.exists(ready)) { "Descendant did not report readiness" }
            }
            else -> error("Unknown owned fixture selector: $selector")
        }
    }

    private fun descendantCommand(selector: String, path: String, millis: String): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-Dmelotrail.fixture.owner=${System.getProperty("melotrail.fixture.owner")}",
        "-cp",
        System.getProperty("java.class.path"),
        VideoMediaProcessFixture::class.java.name,
        selector,
        path,
        millis,
    )
}

private interface FixtureLibC : Library {
    fun kill(pid: Int, signal: Int): Int
    fun signal(signal: Int, handler: Pointer): Pointer?
    fun pipe(descriptors: IntArray): Int
    fun close(descriptor: Int): Int
    fun fcntl(descriptor: Int, command: Int, vararg arguments: Any): Int
    fun waitid(idType: Int, pid: Int, info: Pointer, options: Int): Int

    companion object {
        val instance: FixtureLibC by lazy { Native.load("System", FixtureLibC::class.java) }
    }
}
