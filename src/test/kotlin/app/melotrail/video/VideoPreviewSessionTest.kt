package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPreviewSessionTest {
    @TempDir lateinit var root: Path
    private val takeId = VideoVersionedId("take", 1)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun fixture(): VideoPreviewOpen {
        val projects = VideoProjectStore(listOf(root.resolve("midi")))
        val folder = root.resolve("project")
        val project = projects.create(folder, VideoProject("video", "Video", "2026-09-13T00:00:00Z"))
        val media = folder.resolve("takes/take.mp4")
        Files.createDirectories(media.parent)
        val bytes = "owned silent take".toByteArray()
        Files.write(media, bytes)
        val hash = digest(bytes)
        val measurement = VideoTakeMeasurementRecord(hash, bytes.size.toLong(), "h264", 320, 180,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(24, 1), 3,
            VideoTakeRationalRecord(1, 24), 3000, 8, 1, 0, 0)
        val take = VideoTakeRecord(takeId, VideoArtifact("takes/take.mp4", hash), null, "2026-09-13T00:01:00Z",
            measurement, measurement, "NONE", VideoTakeProvenanceRecord("video", "request", "attempt", "output", "comfyui-local",
                "a".repeat(64), "b".repeat(64), null, null, null,
                listOf(VideoGenerationDependencyPin("source", "c".repeat(64)))))
        projects.save(folder, 0, project.copy(takeVersions = listOf(take), revision = 1))
        val tools = root.resolve("tools")
        Files.createDirectory(tools)
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        for (name in listOf("ffmpeg", "ffprobe")) {
            val file = Files.copy(java, tools.resolve(name))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"))
        }
        Files.writeString(tools.resolve(VideoMediaProbe.MANIFEST_NAME), """
            {"schema":"melotrail-video-media-tools","version":1,
             "distributionId":"${VideoMediaProbe.DISTRIBUTION_ID}",
             "installation":"${VideoMediaProbe.INSTALLATION_STRATEGY}",
             "sourceUrl":"${VideoMediaProbe.SOURCE_URL}",
             "sourceRevision":"${VideoMediaProbe.SOURCE_REVISION}",
             "sourceSha256":"${VideoMediaProbe.SOURCE_SHA256}",
             "ffmpegSha256":"${VideoMediaProbe.FFMPEG_SHA256}",
             "ffprobeSha256":"${VideoMediaProbe.FFPROBE_SHA256}",
             "buildOptions":[${VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString { "\"$it\"" }}],
             "notices":["test"]}
        """.trimIndent())
        return VideoPreviewOpen(folder, takeId, tools, root.resolve("preview-work"))
    }

    private fun decoder(onExtract: (Long) -> Unit = {}): Pair<VideoPreviewDecoder, MutableList<Long>> {
        val extracted = mutableListOf<Long>()
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, _ ->
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                "-read_intervals" in job.arguments -> """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                else -> {
                    val index = job.arguments[job.arguments.indexOf("-vf") + 1].substringAfter("n\\,").substringBefore("\\,").toLong()
                    extracted += index
                    onExtract(index)
                    val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB)
                    image.setRGB(0, 0, (0xff000000L + index).toInt())
                    assertTrue(ImageIO.write(image, "png", job.workingDirectory.resolve("frame-001.png").toFile()))
                    ""
                }
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        return decoder to extracted
    }

    private fun session(scheduler: TestCoroutineScheduler, decoder: VideoPreviewDecoder) = VideoPreviewSession(
        decoder, StandardTestDispatcher(scheduler), VideoPreviewClock { scheduler.currentTime * 1_000_000 },
    )

    @Test fun `rapid seeks coalesce and delayed cancelled extraction cannot publish stale pixels or failure`() = runBlocking {
        val request = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val extracted = mutableListOf<Long>()
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, cancellation ->
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                "-read_intervals" in job.arguments -> """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                else -> {
                    val index = job.arguments[job.arguments.indexOf("-vf") + 1].substringAfter("n\\,").substringBefore("\\,").toLong()
                    synchronized(extracted) { extracted += index }
                    if (index == 1L) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        check(cancellation.isCancelled()) // native cancellation, not just coroutine cancellation
                        throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "obsolete failure")
                    }
                    val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB)
                    image.setRGB(0, 0, (0xff000000L + index).toInt())
                    assertTrue(ImageIO.write(image, "png", job.workingDirectory.resolve("frame-001.png").toFile()))
                    ""
                }
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val preview = VideoPreviewSession(decoder, Dispatchers.IO)
        preview.open(request)
        // Wait for the initial actual frame before starting the delayed operation.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (preview.state.value !is VideoPreviewState.Paused && System.nanoTime() < deadline) Thread.sleep(5)
        assertIs<VideoPreviewState.Paused>(preview.state.value)
        preview.seek(1)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        repeat(100) { preview.seek(if (it == 99) 2 else 1) }
        release.countDown()
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (preview.state.value.let { it !is VideoPreviewState.Paused || it.frame.presentation.frameIndex != 2L } && System.nanoTime() < end) Thread.sleep(5)
        val final = assertIs<VideoPreviewState.Paused>(preview.state.value)
        assertEquals(2L, final.frame.presentation.frameIndex)
        assertEquals(0xff000002.toInt(), final.frame.image.argbAt(0, 0))
        assertEquals(listOf(0L, 1L, 2L), synchronized(extracted) { extracted.toList() })
        preview.closeAndJoin()
        preview.closeAndJoin()
        preview.open(request.copy(scratchDirectory = root.resolve("never-launched")))
        assertFalse(Files.exists(root.resolve("never-launched")))
        assertIs<VideoPreviewState.Closed>(preview.state.value)
    }

    @Test fun `commands are queued and opening publishes only actual pixels and rational identity`() = runTest {
        val request = fixture()
        val projectBefore = Files.readAllBytes(request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request)
        assertIs<VideoPreviewState.Empty>(preview.state.value)
        assertEquals(emptyList(), extracted)
        runCurrent()
        val paused = assertIs<VideoPreviewState.Paused>(preview.state.value)
        assertEquals(takeId, paused.takeId)
        assertEquals(1, paused.sessionId)
        assertEquals(0L, paused.frame.presentation.frameIndex)
        assertEquals(3000L, paused.frame.presentation.pts)
        assertEquals(0xff000000.toInt(), paused.frame.image.argbAt(0, 0))
        assertEquals(listOf(0L), extracted)
        assertContentEquals(projectBefore, Files.readAllBytes(request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        preview.closeAndJoin()
        assertIs<VideoPreviewState.Closed>(preview.state.value)
        assertFailsWith<IllegalStateException> { paused.frame.image.argbAt(0, 0) }
    }

    @Test fun `seek queued before open begins still admits the take and skips frame zero`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request)
        preview.seek(1); preview.seek(2)
        runCurrent()
        assertEquals(2L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        assertEquals(listOf(2L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `seek during slow admission waits for verified take and skips frame zero`() = runBlocking {
        val request = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val extracted = mutableListOf<Long>()
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, cancellation ->
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> {
                    if (job.executable.fileName.toString() == "ffmpeg") {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        assertFalse(cancellation.isCancelled(), "Pending seek must not invalidate admission")
                    }
                    "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                }
                "-read_intervals" in job.arguments -> """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                else -> {
                    val index = job.arguments[job.arguments.indexOf("-vf") + 1].substringAfter("n\\,").substringBefore("\\,").toLong()
                    extracted += index
                    assertTrue(ImageIO.write(BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB), "png",
                        job.workingDirectory.resolve("frame-001.png").toFile()))
                    ""
                }
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val preview = VideoPreviewSession(decoder, Dispatchers.IO)
        preview.open(request)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        preview.seek(1); preview.seek(2)
        release.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (preview.state.value !is VideoPreviewState.Paused && System.nanoTime() < deadline) Thread.sleep(5)
        assertEquals(2L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        assertEquals(listOf(2L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `cancelled playback timing handles pause or seek before starting another probe`() = runBlocking {
        val request = fixture()
        for (seekInsteadOfPause in listOf(false, true)) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val timingCalls = AtomicInteger()
            val extracted = mutableListOf<Long>()
            val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, cancellation ->
                Files.createDirectory(job.workingDirectory)
                val text = when {
                    "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                        VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                    "-read_intervals" in job.arguments -> {
                        if (timingCalls.incrementAndGet() == 2) {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            check(cancellation.isCancelled())
                            throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "cancelled playback timing")
                        }
                        """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                    }
                    else -> {
                        val index = job.arguments[job.arguments.indexOf("-vf") + 1].substringAfter("n\\,").substringBefore("\\,").toLong()
                        synchronized(extracted) { extracted += index }
                        assertTrue(ImageIO.write(BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB), "png",
                            job.workingDirectory.resolve("frame-001.png").toFile()))
                        ""
                    }
                }
                VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                    VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
            }
            val preview = VideoPreviewSession(decoder, Dispatchers.IO)
            preview.open(request.copy(scratchDirectory = root.resolve("scratch-$seekInsteadOfPause")))
            val readyBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (preview.state.value !is VideoPreviewState.Paused && System.nanoTime() < readyBy) Thread.sleep(5)
            assertIs<VideoPreviewState.Paused>(preview.state.value)
            preview.play()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            if (seekInsteadOfPause) preview.seek(2) else preview.pause()
            release.countDown()
            val doneBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (preview.state.value.let { it !is VideoPreviewState.Paused ||
                it.frame.presentation.frameIndex != if (seekInsteadOfPause) 2L else 0L } && System.nanoTime() < doneBy) Thread.sleep(5)
            val paused = assertIs<VideoPreviewState.Paused>(preview.state.value)
            assertEquals(if (seekInsteadOfPause) 2L else 0L, paused.frame.presentation.frameIndex)
            preview.closeAndJoin() // wait for the worker before inspecting invocation counts
            assertEquals(if (seekInsteadOfPause) 3 else 2, timingCalls.get())
            assertEquals(if (seekInsteadOfPause) listOf(0L, 2L) else listOf(0L), synchronized(extracted) { extracted.toList() })
        }
    }

    @Test fun `queued seek cannot mask a missing take admission error`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request.copy(takeId = VideoVersionedId("missing", 1)))
        preview.seek(2)
        runCurrent()
        val failure = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertFalse(failure.message.contains("outside the opened take"))
        assertTrue(failure.message.contains("take", ignoreCase = true), failure.message)
        assertNull(failure.frame)
        assertTrue(extracted.isEmpty())
        preview.closeAndJoin()
    }

    @Test fun `seek during rejected admission retains tool error rather than invalid-frame error`() = runTest {
        val request = fixture()
        val extracts = AtomicInteger()
        lateinit var preview: VideoPreviewSession
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, _ ->
            Files.createDirectory(job.workingDirectory)
            if ("-version" in job.arguments) {
                // Queue both seeks while admission is still in the worker's native call.
                preview.seek(1); preview.seek(2)
                val bad = "unsupported media tool build"
                VideoMediaProcessResult(0, VideoMediaProcessOutput(bad, bad.length.toLong(), false),
                    VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
            } else {
                extracts.incrementAndGet()
                error("No timing or extraction after rejected admission")
            }
        }
        preview = session(testScheduler, decoder)
        preview.open(request)
        runCurrent() // includes queued seeks, not just the first Failed publication
        val failure = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertTrue(failure.message.contains("Pinned ffmpeg did not report FFmpeg 9.0.1"), failure.message)
        assertFalse(failure.message.contains("outside the opened take"))
        assertNull(failure.frame)
        assertEquals(0, extracts.get())
        preview.closeAndJoin()
    }

    @Test fun `replacement open discards late failure from previous take and retains newest identity`() = runBlocking {
        val request = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val starts = AtomicInteger()
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, cancellation ->
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> {
                    if (starts.incrementAndGet() == 1) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        assertTrue(cancellation.isCancelled())
                        throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "obsolete open failed")
                    }
                    "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                }
                "-read_intervals" in job.arguments -> """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                else -> {
                    assertTrue(ImageIO.write(BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB), "png",
                        job.workingDirectory.resolve("frame-001.png").toFile()))
                    ""
                }
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val preview = VideoPreviewSession(decoder, Dispatchers.IO)
        preview.open(request)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        preview.open(request.copy(scratchDirectory = root.resolve("replacement")))
        release.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (preview.state.value !is VideoPreviewState.Paused && System.nanoTime() < deadline) Thread.sleep(5)
        val newest = assertIs<VideoPreviewState.Paused>(preview.state.value)
        assertEquals(2L, newest.sessionId)
        assertEquals(0L, newest.frame.presentation.frameIndex)
        preview.closeAndJoin()
    }

    @Test fun `close cancels the active owned extraction and cannot launch after joining`() = runBlocking {
        val request = fixture()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancellationSeen = CountDownLatch(1)
        val launches = AtomicInteger()
        val decoder = VideoPreviewDecoder(VideoProjectStore(listOf(root.resolve("midi")))) { job, cancellation ->
            launches.incrementAndGet()
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                "-read_intervals" in job.arguments -> """{"frames":[{"best_effort_timestamp":3000},{"best_effort_timestamp":3002},{"best_effort_timestamp":3007}]}"""
                else -> {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    if (cancellation.isCancelled()) cancellationSeen.countDown()
                    throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "confirmed native teardown")
                }
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val preview = VideoPreviewSession(decoder, Dispatchers.IO)
        preview.open(request)
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val closing = async(start = CoroutineStart.UNDISPATCHED) { preview.closeAndJoin() }
        release.countDown()
        closing.await()
        assertEquals(0L, cancellationSeen.count)
        assertIs<VideoPreviewState.Closed>(preview.state.value)
        val before = launches.get()
        preview.seek(2); preview.open(request.copy(scratchDirectory = root.resolve("after-close")))
        assertEquals(before, launches.get())
        assertFalse(Files.exists(root.resolve("after-close")))
    }

    @Test fun `uncertain native cleanup is an explicit failed close and blocks replacement`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder { index -> if (index == 1L) {
            throw VideoMediaProcessException(VideoMediaProcessFailure.SUPERVISION_FAILED, "Cleanup incomplete: owned child not reaped")
        } }
        val preview = session(testScheduler, decoder)
        preview.open(request); runCurrent()
        preview.seek(1); runCurrent()
        assertIs<VideoPreviewState.Failed>(preview.state.value)
        val calls = extracted.size
        preview.open(request.copy(scratchDirectory = root.resolve("replacement"))); runCurrent()
        assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertEquals(calls, extracted.size)
        val failure = assertFailsWith<VideoPreviewImageException> { preview.closeAndJoin() }
        assertTrue(failure.message.orEmpty().contains("unconfirmed"))
        val closed = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertNull(closed.frame)
        assertTrue(closed.nextAction.contains("Inspect"))
        assertFailsWith<VideoPreviewImageException> { preview.closeAndJoin() }
        preview.open(request.copy(scratchDirectory = root.resolve("after-close")))
        assertEquals(calls, extracted.size)
    }

    @Test fun `transport follows irregular timestamps buffers without advancing pixels and ends without looping`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request)
        runCurrent()
        preview.play()
        runCurrent()
        assertIs<VideoPreviewState.Playing>(preview.state.value)
        advanceTimeBy(83) // 3002 / 24 is 83.33 ms relative to the start
        runCurrent()
        assertEquals(0L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(207)
        runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2L, assertIs<VideoPreviewState.Ended>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(0L, 1L, 2L), extracted)
        preview.play()
        runCurrent()
        assertIs<VideoPreviewState.Ended>(preview.state.value)
        preview.closeAndJoin()
    }

    @Test fun `playback timing works with a negative monotonic clock origin`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = VideoPreviewSession(decoder, StandardTestDispatcher(testScheduler),
            VideoPreviewClock { Long.MIN_VALUE + testScheduler.currentTime * 1_000_000 })
        preview.open(request); runCurrent()
        preview.play(); runCurrent()
        advanceTimeBy(83); runCurrent()
        assertEquals(0L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(1); runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(207); runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        advanceTimeBy(1); runCurrent()
        assertEquals(2L, assertIs<VideoPreviewState.Ended>(preview.state.value).frame.presentation.frameIndex)
        assertEquals(listOf(0L, 1L, 2L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `saturated queue preserves open and stop and only worker publishes state`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request); runCurrent()
        val old = preview.state.value
        repeat(17) { preview.play() }
        preview.open(request.copy(scratchDirectory = root.resolve("preview-work-2")))
        assertSame(old, preview.state.value)
        runCurrent()
        assertEquals(2L, assertIs<VideoPreviewState.Paused>(preview.state.value).sessionId)
        assertEquals(listOf(0L, 0L), extracted)

        repeat(17) { preview.play() }
        preview.stop()
        assertIs<VideoPreviewState.Paused>(preview.state.value)
        runCurrent()
        assertEquals(0L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        assertEquals(listOf(0L, 0L), extracted)

        // A pending open cannot be discarded by a stop when the queue is saturated.
        repeat(16) { preview.play() }
        preview.open(request.copy(scratchDirectory = root.resolve("preview-work-3")))
        preview.stop()
        assertIs<VideoPreviewState.Paused>(preview.state.value)
        runCurrent()
        assertEquals(3L, assertIs<VideoPreviewState.Paused>(preview.state.value).sessionId)
        assertEquals(listOf(0L, 0L, 0L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `full queue rejects noncritical transport without publishing from caller`() = runTest {
        val request = fixture()
        val (decoder, _) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request); runCurrent()
        val before = preview.state.value
        repeat(17) { preview.pause() }
        assertTrue(assertFailsWith<IllegalStateException> { preview.seek(1) }.message!!.contains("queue is full"))
        assertSame(before, preview.state.value)
        runCurrent()
        assertIs<VideoPreviewState.Paused>(preview.state.value)
        preview.closeAndJoin()
    }

    @Test fun `decoder stall reports buffering with last actual frame and no fictional progress`() = runTest {
        val request = fixture()
        lateinit var preview: VideoPreviewSession
        val (decoder, extracted) = decoder { index ->
            if (index == 1L) {
                val buffering = assertIs<VideoPreviewState.Buffering>(preview.state.value)
                assertEquals(1L, buffering.requestedFrame)
                assertEquals(0L, buffering.frame?.presentation?.frameIndex)
                assertEquals(0xff000000.toInt(), buffering.frame?.image?.argbAt(0, 0))
            }
        }
        preview = session(testScheduler, decoder)
        preview.open(request); runCurrent()
        preview.play(); runCurrent()
        advanceTimeBy(84); runCurrent()
        assertEquals(listOf(0L, 1L), extracted)
        assertEquals(1L, assertIs<VideoPreviewState.Playing>(preview.state.value).frame.presentation.frameIndex)
        preview.closeAndJoin()
    }

    @Test fun `seek step pause and stop use actual clamped frames`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.open(request)
        runCurrent()
        preview.step(-1)
        runCurrent()
        assertEquals(listOf(0L), extracted)
        preview.seek(2)
        runCurrent()
        assertEquals(2L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        preview.step(1)
        runCurrent()
        assertEquals(listOf(0L, 2L), extracted)
        preview.step(-1)
        runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        preview.play(); runCurrent()
        preview.pause(); runCurrent()
        assertEquals(1L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        preview.stop(); runCurrent()
        assertEquals(0L, assertIs<VideoPreviewState.Paused>(preview.state.value).frame.presentation.frameIndex)
        assertEquals(listOf(0L, 2L, 1L, 0L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `rejected take and failed extraction never publish a synthetic success frame`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder { index -> if (index == 1L) error("damaged extracted image") }
        val preview = session(testScheduler, decoder)
        preview.open(request.copy(takeId = VideoVersionedId("missing", 1)))
        runCurrent()
        val missing = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertNull(missing.frame)
        assertTrue(missing.nextAction.isNotBlank())
        assertTrue(extracted.isEmpty())
        preview.open(request); runCurrent()
        val real = assertIs<VideoPreviewState.Paused>(preview.state.value)
        preview.seek(1); runCurrent()
        val failed = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertEquals(real.sessionId, failed.sessionId)
        assertSame(real.frame, failed.frame)
        assertEquals(0L, failed.frame?.presentation?.frameIndex)
        assertEquals(listOf(0L, 1L), extracted)
        preview.closeAndJoin()
    }

    @Test fun `invalid transport reports actionable failure without extraction and can recover`() = runTest {
        val request = fixture()
        val (decoder, extracted) = decoder()
        val preview = session(testScheduler, decoder)
        preview.step(0); runCurrent()
        assertTrue(assertIs<VideoPreviewState.Failed>(preview.state.value).nextAction.isNotBlank())
        assertTrue(extracted.isEmpty())
        preview.open(request); runCurrent()
        preview.seek(3); runCurrent()
        val failed = assertIs<VideoPreviewState.Failed>(preview.state.value)
        assertEquals(0L, failed.frame?.presentation?.frameIndex)
        assertEquals(listOf(0L), extracted)
        preview.seek(1); runCurrent()
        assertIs<VideoPreviewState.Paused>(preview.state.value)
        preview.closeAndJoin()
    }
}
