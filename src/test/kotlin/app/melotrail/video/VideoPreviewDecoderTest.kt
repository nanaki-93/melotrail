package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.domain.*
import java.nio.file.Files
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
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun store() = VideoProjectStore(listOf(root.resolve("midi")))
    private fun persisted(): VideoPreviewOpen {
        val projectRoot = root.resolve("project")
        val projects = store()
        val empty = projects.create(projectRoot, VideoProject("video", "Video", "2026-09-13T00:00:00Z"))
        val media = projectRoot.resolve("takes/take.mp4")
        Files.createDirectories(media.parent)
        val bytes = "owned silent take".toByteArray()
        Files.write(media, bytes)
        val hash = digest(bytes)
        val facts = VideoTakeMeasurementRecord(hash, bytes.size.toLong(), "h264", 320, 180,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(24, 1), 72,
            VideoTakeRationalRecord(1, 24), 0, 72, 1, 0, 0)
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
