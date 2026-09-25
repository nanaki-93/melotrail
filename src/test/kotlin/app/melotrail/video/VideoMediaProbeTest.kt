package app.melotrail.video

import app.melotrail.video.adapter.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoMediaProbeTest {
    @TempDir lateinit var root: Path

    private fun request(label: String): VideoMediaProbeRequest {
        val tools = Files.createDirectory(root.resolve("tools-$label"))
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
        val input = Files.writeString(root.resolve("source-$label.mp4"), "original-$label")
        return VideoMediaProbeRequest(tools, input, root.resolve("output-$label"))
    }

    private fun fake(
        streams: String = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30000/1001","nb_read_frames":"90",
            "time_base":"1/30000","start_pts":"3000","duration_ts":"90090"},
            {"codec_type":"audio"}""",
        onProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> Unit = { _, _ -> },
    ): VideoMediaProbe = VideoMediaProbe { job, cancellation ->
        onProcess(job, cancellation)
        Files.createDirectory(job.workingDirectory)
        val text = if ("-version" in job.arguments) {
            "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
        } else if (job.executable.fileName.toString() == "ffprobe") {
            """{"streams":[$streams],"format":{"duration":"100.0"}}"""
        } else ""
        VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
            VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
    }

    @Test fun `audio tail does not extend video timing and original identity is measured`() {
        val request = request("audio-tail")
        val bytes = Files.readAllBytes(request.input)
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val measurement = fake(onProcess = { job, _ -> jobs += job }).inspectTake(request)
        assertEquals(3.003, measurement.durationSeconds, 0.000001)
        assertEquals(VideoMediaRational(30000, 1001), measurement.frameRate)
        assertEquals(VideoMediaRational(1, 1), measurement.sampleAspectRatio)
        assertEquals(VideoMediaRational(1, 30000), measurement.videoTimeBase)
        assertEquals(3000L, measurement.videoStartPts)
        assertEquals(90090L, measurement.videoDurationPts)
        assertEquals(90L, measurement.decodedFrameCount)
        assertEquals(1, measurement.videoStreamCount)
        assertEquals(1, measurement.audioStreamCount)
        assertEquals(0, measurement.otherStreamCount)
        assertEquals(bytes.size.toLong(), measurement.bytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, measurement.sha256)
        assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "source-full-decode" &&
            it.arguments.windowed(2).contains(listOf("-f", "null")) })
        assertContentEquals(bytes, Files.readAllBytes(request.input))
    }

    @Test fun `invalid rational streams zero frames and missing timing reject`() {
        val original = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30/1","nb_read_frames":"90",
            "time_base":"1/30","start_pts":"0","duration_ts":"90"}"""
        val cases = listOf(
            original.replace("30/1", "0/1"), original.replace("1/30", "1/0"),
            original.replace("1:1", "N/A"), original.replace("\"90\"", "\"0\""),
            original.replace("\"duration_ts\":\"90\"", "\"duration_ts\":\"N/A\""),
            "$original,$original", "$original,{\"codec_type\":\"subtitle\"}",
        )
        cases.forEachIndexed { index, streams ->
            val req = request("invalid-$index")
            val failure = assertFailsWith<VideoMediaProbeException> { fake(streams).inspectTake(req) }
            assertEquals(VideoMediaProbeFailure.INVALID_MEDIA, failure.failure)
            assertFalse(Files.exists(req.outputDirectory.resolve("take-validation.json")))
        }
    }

    @Test fun `truncation and source mutation fail without publication`() {
        val truncated = request("truncated")
        val failure = assertFailsWith<VideoMediaProbeException> {
            fake(onProcess = { job, _ ->
                if (job.workingDirectory.fileName.toString() == "source-full-decode")
                    throw VideoMediaProcessException(VideoMediaProcessFailure.NONZERO_EXIT, "truncated bitstream")
            }).inspectTake(truncated)
        }
        assertEquals(VideoMediaProbeFailure.PROCESS_FAILED, failure.failure)
        val changed = request("changed")
        val mismatch = assertFailsWith<VideoMediaProbeException> {
            fake(onProcess = { job, _ ->
                if (job.workingDirectory.fileName.toString() == "source-full-decode") Files.writeString(changed.input, "changed bytes")
            }).inspectTake(changed)
        }
        assertEquals(VideoMediaProbeFailure.INVALID_OUTPUT, mismatch.failure)
    }

    @Test fun `cancellation reaches the process and hashing boundary`() {
        val request = request("cancel")
        val cancelled = VideoMediaProcessCancellation().apply { cancel() }
        assertFailsWith<VideoMediaProcessException> { fake().inspectTake(request, cancelled) }.also {
            assertEquals(VideoMediaProcessFailure.CANCELLED, it.failure)
        }
        assertFalse(Files.exists(request.outputDirectory))
        val active = VideoMediaProcessCancellation()
        val process = fake(onProcess = { job, received ->
            assertSame(active, received)
            if (job.workingDirectory.fileName.toString() == "source-streams") active.cancel()
        })
        assertFailsWith<VideoMediaProcessException> { process.inspectTake(request, active) }.also {
            assertEquals(VideoMediaProcessFailure.CANCELLED, it.failure)
        }
    }
}
