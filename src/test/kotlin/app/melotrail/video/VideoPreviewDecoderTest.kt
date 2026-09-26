package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.domain.*
import java.awt.image.BufferedImage
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.CRC32
import javax.imageio.ImageIO
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoPreviewDecoderTest {
    @TempDir lateinit var root: Path
    private val id = VideoVersionedId("take", 1)

    private fun png(name: String, width: Int = 2, height: Int = 2): Path {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x7f123456)
        image.setRGB(width - 1, height - 1, 0xffaabbcc.toInt())
        return root.resolve(name).also { assertTrue(ImageIO.write(image, "png", it.toFile())) }
    }

    @Test fun `extracted PNG has actual immutable pixels and no project or thumbnail side effects`() {
        val input = png("extracted.png")
        val before = Files.readAllBytes(input)
        val decoder = VideoPreviewDecoder(store()) { _, _ -> error("PNG decode must not launch tools") }
        val image = decoder.decodeExtractedPng(input)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals("ARGB8888", image.pixelFormat)
        assertEquals(0x7f123456, image.argbAt(0, 0))
        assertEquals(0xffaabbcc.toInt(), image.argbAt(1, 1))
        assertTrue(VideoPreviewImage::class.java.methods.none { it.returnType == IntArray::class.java },
            "No mutable backing array or unaccounted pixel copy may escape")
        assertEquals(0x7f123456, image.argbAt(0, 0))
        assertContentEquals(before, Files.readAllBytes(input))
        assertEquals(listOf("extracted.png"), Files.list(root).use { it.map { p -> p.fileName.toString() }.toList() })
        image.close()
        image.close()
        assertFailsWith<IllegalStateException> { image.argbAt(0, 0) }
    }

    @Test fun `RGB and palette PNGs convert to owned ARGB without leaking raster formats`() {
        val rgb = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, 0xff224466.toInt()) }
        val indexed = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED).apply { setRGB(0, 0, 0xffaabbcc.toInt()) }
        val decoder = VideoPreviewDecoder(store()) { _, _ -> error("No process allowed") }
        for ((name, image) in listOf("rgb" to rgb, "palette" to indexed)) {
            val path = root.resolve("$name.png")
            assertTrue(ImageIO.write(image, "png", path.toFile()))
            decoder.decodeExtractedPng(path).use { frame ->
                assertEquals("ARGB8888", frame.pixelFormat)
                assertEquals(image.getRGB(0, 0), frame.argbAt(0, 0), name)
            }
        }
    }

    @Test fun `truncated changed checksum oversize and compressed dimension bomb yield no frame`() {
        val valid = Files.readAllBytes(png("valid.png"))
        val truncated = root.resolve("truncated.png").also { Files.write(it, valid.copyOf(valid.size - 1)) }
        val badChecksum = root.resolve("checksum.png").also {
            Files.write(it, valid.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() })
        }
        val bomb = valid.copyOf()
        ByteBuffer.wrap(bomb).putInt(16, 8192).putInt(20, 8192)
        val crc = CRC32().apply { update(bomb, 12, 17) }
        ByteBuffer.wrap(bomb).putInt(29, crc.value.toInt())
        val bombPath = root.resolve("bomb.png").also { Files.write(it, bomb) }
        val oversized = root.resolve("oversized.png")
        RandomAccessFile(oversized.toFile(), "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        val decoder = VideoPreviewDecoder(store()) { _, _ -> error("No process allowed") }
        for (path in listOf(truncated, badChecksum, bombPath, oversized)) {
            val error = assertFailsWith<VideoPreviewImageException>(path.fileName.toString()) { decoder.decodeExtractedPng(path) }
            assertTrue(error.message.orEmpty().isNotBlank())
        }
        assertEquals(0L, Files.list(root).use { it.filter { p -> Files.isDirectory(p) }.count() })
        decoder.decodeExtractedPng(root.resolve("valid.png")).use { assertEquals(0x7f123456, it.argbAt(0, 0)) }
    }

    @Test fun `encoded staging and raster plus published pixel accounting enforce byte ceilings`() {
        val budget = VideoPreviewBufferBudget()
        budget.reserveEncoded(64L * 1024 * 1024)
        assertFailsWith<VideoPreviewImageException> { budget.reserveEncoded(1) }
        budget.releaseEncoded(64L * 1024 * 1024)
        budget.reserveEncoded(1)
        budget.releaseEncoded(1)

        val frameBytes = 8L * 1024 * 1024
        repeat(7) {
            budget.reserveDecode(frameBytes)
            budget.releaseDecode(frameBytes, published = true)
        }
        assertFailsWith<VideoPreviewImageException> { budget.reserveDecode(frameBytes) }
        budget.releaseFrame(frameBytes)
        budget.reserveDecode(frameBytes)
        budget.releaseDecode(frameBytes, published = false)
        repeat(6) { budget.releaseFrame(frameBytes) }
        budget.reserveDecode(frameBytes)
        budget.releaseDecode(frameBytes, published = false)
    }

    @Test fun `retained and in flight pixel reservations are bounded and released on close`() {
        val input = png("one.png")
        val decoder = VideoPreviewDecoder(store()) { _, _ -> error("No process allowed") }
        val frames = (1..7).map { decoder.decodeExtractedPng(input) }
        try {
            assertFailsWith<VideoPreviewImageException> { decoder.decodeExtractedPng(input) }
            frames.first().close()
            decoder.decodeExtractedPng(input).use { assertEquals(0x7f123456, it.argbAt(0, 0)) }
        } finally {
            frames.forEach { it.close() }
        }
        decoder.decodeExtractedPng(input).close()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun store() = VideoProjectStore(listOf(root.resolve("midi")))
    private fun persisted(count: Long = 72, start: Long = 0, duration: Long = 72): VideoPreviewOpen {
        val projectRoot = root.resolve("project")
        val projects = store()
        val empty = projects.create(projectRoot, VideoProject("video", "Video", "2026-09-13T00:00:00Z"))
        val media = projectRoot.resolve("takes/take.mp4")
        Files.createDirectories(media.parent)
        val bytes = "owned silent take".toByteArray()
        Files.write(media, bytes)
        val hash = digest(bytes)
        val facts = VideoTakeMeasurementRecord(hash, bytes.size.toLong(), "h264", 320, 180,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(24, 1), count,
            VideoTakeRationalRecord(1, 24), start, duration, 1, 0, 0)
        val take = VideoTakeRecord(id, VideoArtifact("takes/take.mp4", hash), null, "2026-09-13T00:01:00Z",
            facts, facts, "NONE", VideoTakeProvenanceRecord("video", "request", "attempt", "output", "comfyui-local",
                "a".repeat(64), "b".repeat(64), null, null, null,
                listOf(VideoGenerationDependencyPin("source", "c".repeat(64)))))
        projects.save(projectRoot, 0, empty.copy(takeVersions = listOf(take), revision = 1))
        return VideoPreviewOpen(projectRoot, id, root.resolve("tools"), root.resolve("preview-work"))
    }

    private fun tools(directory: Path) {
        Files.createDirectory(directory)
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        for (name in listOf("ffmpeg", "ffprobe")) {
            val file = Files.copy(java, directory.resolve(name))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"))
        }
        Files.writeString(directory.resolve(VideoMediaProbe.MANIFEST_NAME), """
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
    }

    @Test fun `bounded timing resolves actual decoded order at first interior and final with irregular rational cadence`() {
        val timestamps = listOf(3000L, 4001L, 5002L, 7004L, 8005L)
        val request = persisted(5, 3000, 6006)
        tools(request.toolsDirectory)
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            jobs += job
            Files.createDirectory(job.workingDirectory)
            val text = if ("-version" in job.arguments) {
                "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
            } else {
                assertEquals("%+#256", job.arguments[job.arguments.indexOf("-read_intervals") + 1])
                """{"frames":[${timestamps.joinToString { """{"best_effort_timestamp":$it}""" }}]}"""
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val admitted = assertIs<VideoPreviewOpenResult.Admitted>(decoder.open(request))
        for (i in listOf(0L, 2L, 4L)) {
            val time = decoder.presentationAt(admitted, i)
            assertEquals(timestamps[i.toInt()], time.pts)
            assertEquals(java.math.BigInteger.valueOf(timestamps[i.toInt()] - 3000), time.relativePts)
            assertEquals(VideoMediaRational(1, 24), time.timeBase)
            assertEquals(3000, time.streamStartPts)
        }
        val before = jobs.size
        for (i in listOf(-1L, 5L, Long.MAX_VALUE)) {
            assertEquals(VideoMediaProbeFailure.INVALID_REQUEST, assertFailsWith<VideoMediaProbeException> {
                decoder.presentationAt(admitted, i)
            }.failure)
        }
        assertEquals(before, jobs.size, "Invalid indices must not start metadata work")
        assertTrue(jobs.drop(2).all { it.maxStdoutBytes == 1_048_576 && it.memoryLimitBytes == 512L * 1024 * 1024 && it.timeout == Duration.ofSeconds(30) })
    }

    @Test fun `exactly 256 decoded frames complete at the bounded window edge`() {
        val request = persisted(256, 3000, 256)
        tools(request.toolsDirectory)
        var metadataCalls = 0
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            Files.createDirectory(job.workingDirectory)
            val text = if ("-version" in job.arguments) {
                "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
            } else {
                metadataCalls++
                assertEquals("%+#256", job.arguments[job.arguments.indexOf("-read_intervals") + 1])
                assertEquals(1_048_576, job.maxStdoutBytes)
                """{"frames":[${(3000L until 3256L).joinToString { """{"best_effort_timestamp":$it}""" }}]}"""
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val admitted = assertIs<VideoPreviewOpenResult.Admitted>(decoder.open(request))
        val last = decoder.presentationAt(admitted, 255)
        assertEquals(3255L, last.pts)
        assertEquals(java.math.BigInteger.valueOf(255), last.relativePts)
        assertEquals(VideoMediaRational(1, 24), last.timeBase)
        assertEquals(1, metadataCalls, "A complete window must not request an anchor-only follow-up")
    }

    @Test fun `long metadata scans overlap keyframe seeks without keeping a whole timeline`() {
        val request = persisted(520, 3000, 800)
        tools(request.toolsDirectory)
        val intervals = mutableListOf<String>()
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            Files.createDirectory(job.workingDirectory)
            val text = if ("-version" in job.arguments) "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ") else {
                val interval = job.arguments[job.arguments.indexOf("-read_intervals") + 1]
                intervals += interval
                val seek = if (interval.startsWith("%")) 0 else ((interval.substringBefore('%').toBigDecimal() * 24.toBigDecimal()).toLong() - 3000).toInt()
                val first = (seek.coerceAtLeast(0) / 250) * 250
                // FFprobe limits packets: delayed B-frames can make nonterminal
                // decoded-frame windows shorter than the packet limit.
                val pts = (first until minOf(first + 255, 520)).map { 3000L + it + it / 7 }
                """{"frames":[${pts.joinToString { """{"best_effort_timestamp":$it}""" }}]}"""
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val admitted = assertIs<VideoPreviewOpenResult.Admitted>(decoder.open(request))
        assertEquals(3000L + 519 + 519 / 7, decoder.presentationAt(admitted, 519).pts)
        assertEquals(3, intervals.size)
        assertTrue(intervals.all { it.endsWith("%+#256") })
    }

    @Test fun `missing non increasing contradictory and oversized timestamps fail closed`() {
        val request = persisted(3, 10, 10)
        tools(request.toolsDirectory)
        val outcomes = listOf(
            """{"frames":[{"best_effort_timestamp":10},{},{"best_effort_timestamp":13}]}""",
            """{"frames":[{"best_effort_timestamp":10},{"best_effort_timestamp":10},{"best_effort_timestamp":13}]}""",
            """{"frames":[{"best_effort_timestamp":10},{"best_effort_timestamp":12},{"best_effort_timestamp":20}]}""",
            """{"frames":[{"best_effort_timestamp":10},{"best_effort_timestamp":12}]}""",
            """{"frames":[{"best_effort_timestamp":10},{"best_effort_timestamp":12},{"best_effort_timestamp":13},{"best_effort_timestamp":14}]}""",
            """{"frames":[{"best_effort_timestamp":11},{"best_effort_timestamp":12},{"best_effort_timestamp":13}]}""",
        )
        outcomes.forEachIndexed { i, response ->
            val decoder = VideoPreviewDecoder(store()) { job, _ ->
                Files.createDirectory(job.workingDirectory)
                val text = if ("-version" in job.arguments) "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ") else response
                VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                    VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
            }
            val admitted = assertIs<VideoPreviewOpenResult.Admitted>(decoder.open(request.copy(scratchDirectory = root.resolve("scratch-$i"))))
            assertEquals(VideoMediaProbeFailure.INVALID_MEDIA, assertFailsWith<VideoMediaProbeException> {
                decoder.presentationAt(admitted, 1)
            }.failure)
        }
    }

    @Test fun `open is lazy read only and authenticates persisted silent take before any extraction`() {
        val request = persisted()
        tools(request.toolsDirectory)
        val projectFile = request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val original = Files.readAllBytes(projectFile)
        val media = Files.readAllBytes(request.projectRoot.resolve("takes/take.mp4"))
        val calls = mutableListOf<VideoMediaProcessRequest>()
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            calls += job
            assertEquals(listOf("-hide_banner", "-version"), job.arguments)
            assertEquals(if (job.executable.fileName.toString() == "ffmpeg") VideoMediaProbe.FFMPEG_SHA256 else VideoMediaProbe.FFPROBE_SHA256,
                job.executableSha256)
            Files.createDirectory(job.workingDirectory)
            val output = "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
            VideoMediaProcessResult(0, VideoMediaProcessOutput(output, output.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        assertFalse(Files.exists(request.scratchDirectory), "Construction must not touch disk")
        assertTrue(calls.isEmpty())
        val admitted = assertIs<VideoPreviewOpenResult.Admitted>(decoder.open(request))
        assertEquals("video", admitted.projectId)
        assertEquals(1, admitted.projectRevision)
        assertEquals(id, admitted.takeId)
        assertEquals(request.projectRoot.resolve("takes/take.mp4").toRealPath(), admitted.artifact)
        assertEquals(72, admitted.measurement.decodedFrameCount)
        assertEquals(2, calls.size)
        assertTrue(calls.all { it.workingDirectory.startsWith(request.scratchDirectory.toRealPath()) })
        assertContentEquals(original, Files.readAllBytes(projectFile))
        assertContentEquals(media, Files.readAllBytes(admitted.artifact))
        assertEquals(listOf(".video-project.lock", "takes", VideoProjectStore.PROJECT_FILE).sorted(), Files.list(request.projectRoot).use { it.map { p -> p.fileName.toString() }.sorted().toList() })
    }

    @Test fun `missing unsafe and changed persisted takes fail before tool launch or scratch creation`() {
        val request = persisted()
        val media = request.projectRoot.resolve("takes/take.mp4")
        var calls = 0
        val decoder = VideoPreviewDecoder(store()) { _, _ -> calls++; error("native work not allowed") }
        assertEquals(VideoPreviewOpenFailure.TAKE_NOT_FOUND,
            assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request.copy(takeId = VideoVersionedId("absent", 1)))).failure)
        val bytes = Files.readAllBytes(media)
        Files.write(media, "same size altered".toByteArray())
        assertEquals(VideoPreviewOpenFailure.UNSAFE_TAKE,
            assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request)).failure)
        Files.write(media, bytes)
        val outside = Files.writeString(root.resolve("outside.mp4"), "outside")
        Files.delete(media)
        Files.createSymbolicLink(media, outside)
        assertEquals(VideoPreviewOpenFailure.UNSAFE_TAKE,
            assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request)).failure)
        assertEquals(0, calls)
        assertFalse(Files.exists(request.scratchDirectory))
    }

    @Test fun `audio codec geometry and frame counts are rejected from persisted measurements`() {
        val request = persisted()
        val projectFile = request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val original = Files.readString(projectFile)
        val decoder = VideoPreviewDecoder(store()) { _, _ -> error("no process") }
        // Altering the documented measurement is an invalid persisted take, not a request to repair it.
        for ((from, to) in listOf(
            "\"audioStreamCount\": 0" to "\"audioStreamCount\": 1",
            "\"videoCodec\": \"h264\"" to "\"videoCodec\": \"mpeg4\"",
            "\"width\": 320" to "\"width\": 9000",
            "\"decodedFrameCount\": 72" to "\"decodedFrameCount\": 0",
        )) {
            assertTrue(from in original)
            val changed = original.replace(from, to)
            Files.writeString(projectFile, changed)
            val result = assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request))
            assertTrue(result.failure in setOf(VideoPreviewOpenFailure.UNSUPPORTED_TAKE, VideoPreviewOpenFailure.UNSAFE_TAKE))
            assertTrue(result.nextAction.isNotBlank())
            assertEquals(changed, Files.readString(projectFile))
            assertFalse(Files.exists(request.scratchDirectory))
        }
        Files.writeString(projectFile, original)
    }

    @Test fun `changed take during tool checks cannot be admitted and cancellation prevents native work`() {
        val request = persisted()
        tools(request.toolsDirectory)
        val originalProject = Files.readAllBytes(request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val media = request.projectRoot.resolve("takes/take.mp4")
        val originalMedia = Files.readAllBytes(media)
        val cancelled = VideoMediaProcessCancellation().apply { cancel() }
        var calls = 0
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            calls++
            Files.createDirectory(job.workingDirectory)
            if (calls == 1) Files.write(media, ByteArray(originalMedia.size) { 42 })
            val output = "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
            VideoMediaProcessResult(0, VideoMediaProcessOutput(output, output.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        assertEquals(VideoPreviewOpenFailure.CANCELLED, assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request, cancelled)).failure)
        assertEquals(0, calls)
        assertFalse(Files.exists(request.scratchDirectory))
        assertEquals(VideoPreviewOpenFailure.UNSAFE_TAKE, assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request)).failure)
        assertEquals(2, calls)
        assertContentEquals(originalProject, Files.readAllBytes(request.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)))
    }

    @Test fun `executable pin failure from owned process never falls back to a second executable`() {
        val request = persisted()
        tools(request.toolsDirectory)
        var calls = 0
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            calls++
            assertEquals(request.toolsDirectory.resolve("ffmpeg").toRealPath(), job.executable)
            assertEquals(VideoMediaProbe.FFMPEG_SHA256, job.executableSha256)
            throw VideoMediaProcessException(VideoMediaProcessFailure.PIN_MISMATCH, "Pinned ffmpeg bytes changed")
        }
        val result = assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request))
        assertEquals(VideoPreviewOpenFailure.TOOLS, result.failure)
        assertTrue(result.message.contains("pin", ignoreCase = true))
        assertEquals(1, calls)
        assertTrue(result.nextAction.contains("no PATH fallback"))
    }

    @Test fun `missing manifest wrong pins wrong reported build and unsafe scratch never fall back to PATH`() {
        val request = persisted()
        var calls = 0
        val decoder = VideoPreviewDecoder(store()) { job, _ ->
            calls++
            Files.createDirectory(job.workingDirectory)
            val text = "${job.executable.fileName} version 9.0.0\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.joinToString(" ")
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        assertEquals(VideoPreviewOpenFailure.TOOLS, assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request)).failure)
        assertFalse(Files.exists(request.scratchDirectory))
        tools(request.toolsDirectory)
        val manifest = request.toolsDirectory.resolve(VideoMediaProbe.MANIFEST_NAME)
        val original = Files.readString(manifest)
        Files.writeString(manifest, original.replace(VideoMediaProbe.FFMPEG_SHA256, "0".repeat(64)))
        assertEquals(VideoPreviewOpenFailure.TOOLS, assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request)).failure)
        assertEquals(0, calls)
        Files.writeString(manifest, original)
        val overlap = request.copy(scratchDirectory = request.projectRoot.resolve("scratch"))
        assertEquals(VideoPreviewOpenFailure.INVALID_REQUEST, assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(overlap)).failure)
        assertEquals(0, calls)
        val wrongBuild = assertIs<VideoPreviewOpenResult.Rejected>(decoder.open(request))
        assertEquals(VideoPreviewOpenFailure.TOOLS, wrongBuild.failure)
        assertTrue(wrongBuild.message.contains("9.0.1"))
        assertEquals(2, calls)
        assertTrue(Files.exists(request.scratchDirectory))
    }
}
