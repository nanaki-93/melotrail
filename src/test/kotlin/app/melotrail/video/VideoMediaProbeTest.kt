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
        publishedStreams: String = streams.substringBeforeLast(","),
        publishedTimestamps: List<Long>? = null,
        sideDataFrames: Boolean = false,
    ): VideoMediaProbe = VideoMediaProbe { job, cancellation ->
        onProcess(job, cancellation)
        Files.createDirectory(job.workingDirectory)
        val text = if ("-version" in job.arguments) {
            "${job.executable.fileName} version 9.0.1\nconfiguration: " + VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
        } else if (job.executable.fileName.toString() == "ffprobe") {
            val published = job.arguments.last().endsWith("silent-take.mp4")
            val selectedStreams = if (published) publishedStreams else streams
            if ("-show_frames" in job.arguments) {
                val count = Regex(""""nb_read_frames":"(\d+)"""").find(selectedStreams)!!.groupValues[1].toInt()
                val start = Regex(""""start_pts":"(-?\d+)"""").find(selectedStreams)!!.groupValues[1].toLong()
                val timestamps = if (published) publishedTimestamps else null
                """{"frames":[${(timestamps ?: List(count) { start + it * 1001L }).mapIndexed { index, pts ->
                    """{"best_effort_timestamp":$pts${if (sideDataFrames && index == 0) ",\"side_data_list\":[{\"side_data_type\":\"H.26[45] User Data Unregistered SEI message\",\"uuid\":\"0123\"}]" else ""}}"""
                }.joinToString(",") }]}"""
            } else """{"streams":[$selectedStreams],"format":{"duration":"100.0"}}"""
        } else ""
        VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
            VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
    }

    @Test fun `preview tool admission shares manifest and reported build checks without media work`() {
        val request = request("preview-tools")
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val scratch = root.resolve("preview-tools-scratch")
        val pins = fake(onProcess = { job, _ -> jobs += job }).verifyPreviewTools(request.toolsDirectory, scratch)
        assertEquals(VideoMediaProbe.FFMPEG_SHA256, pins.ffmpegSha256)
        assertEquals(VideoMediaProbe.FFPROBE_SHA256, pins.ffprobeSha256)
        assertEquals(2, jobs.size)
        assertTrue(jobs.all { it.arguments == listOf("-hide_banner", "-version") && it.executableSha256 in
            setOf(VideoMediaProbe.FFMPEG_SHA256, VideoMediaProbe.FFPROBE_SHA256) })
        assertFailsWith<VideoMediaProbeException> { fake().verifyPreviewTools(request.toolsDirectory, scratch) }
        val changed = Files.readString(request.toolsDirectory.resolve(VideoMediaProbe.MANIFEST_NAME))
            .replace(VideoMediaProbe.FFMPEG_SHA256, "0".repeat(64))
        Files.writeString(request.toolsDirectory.resolve(VideoMediaProbe.MANIFEST_NAME), changed)
        val another = root.resolve("preview-tools-other")
        assertEquals(VideoMediaProbeFailure.TOOL_CONFIGURATION, assertFailsWith<VideoMediaProbeException> {
            fake().verifyPreviewTools(request.toolsDirectory, another)
        }.failure)
        assertFalse(Files.exists(another))
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

    @Test fun `native sized audio is remuxed and both byte identities and timing are independently verified`() {
        val request = request("remux")
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val result = fake(onProcess = { job, _ ->
            jobs += job
            if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                Files.writeString(request.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
        }).validateTake(request)
        assertEquals(VideoTakeConversion.AUDIO_REMUX, result.conversion)
        assertEquals(1, result.source.audioStreamCount)
        assertEquals(0, result.published.audioStreamCount)
        assertEquals(VideoMediaRational(30000, 1001), result.published.frameRate)
        assertEquals(320, result.published.width)
        assertEquals(90, result.published.decodedFrameCount)
        assertNotEquals(result.source.sha256, result.published.sha256)
        assertEquals(Files.size(result.validatedPath), result.published.bytes)
        assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-published-full-decode" })
        assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-published-streams" })
        assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-source-frames" &&
            it.arguments.contains("frame=best_effort_timestamp") })
        assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-published-frames" &&
            it.arguments.contains("frame=best_effort_timestamp") })
        assertContentEquals("original-remux".toByteArray(), Files.readAllBytes(request.input))
    }

    @Test fun `side data bearing frame output preserves timestamps and permits a verified remux`() {
        val request = request("side-data")
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val result = fake(sideDataFrames = true, onProcess = { job, _ ->
            jobs += job
            if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                Files.writeString(request.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
        }).validateTake(request)
        assertEquals(VideoTakeConversion.AUDIO_REMUX, result.conversion)
        assertEquals(90, result.published.decodedFrameCount)
        assertEquals(2, jobs.count { "-show_frames" in it.arguments })
        assertTrue(jobs.filter { "-show_frames" in it.arguments }.all {
            it.arguments.windowed(2).contains(listOf("-of", "json")) && it.maxStdoutBytes == 1_048_576
        })
        assertTrue(Files.exists(request.outputDirectory.resolve("take-validation.json")))
    }

    @Test fun `remux rejects changed frame count geometry cadence duration and extra streams`() {
        val video = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30000/1001","nb_read_frames":"90",
            "time_base":"1/30000","start_pts":"3000","duration_ts":"90090"}"""
        val changes = listOf(
            video.replace("\"90\"", "\"89\""), video.replace("320", "640"),
            video.replace("30000/1001", "30/1"), video.replace("90090", "90100"),
            "$video,{\"codec_type\":\"audio\"}", "$video,{\"codec_type\":\"subtitle\"}",
            video.replace("\"3000\"", "\"1500\""),
        )
        changes.forEachIndexed { index, published ->
            val request = request("changed-remux-$index")
            assertFailsWith<VideoMediaProbeException> {
                fake(publishedStreams = published, onProcess = { job, _ ->
                    if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                        Files.writeString(request.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
                }).validateTake(request)
            }
            assertFalse(Files.exists(request.outputDirectory.resolve("take-validation.json")))
        }
    }

    @Test fun `remux rejects an altered intermediate presentation timestamp with identical stream metadata`() {
        val video = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30000/1001","nb_read_frames":"90",
            "time_base":"1/30000","start_pts":"3000","duration_ts":"90090"}"""
        val shifted = List(90) { 3000L + it * 1001L }.toMutableList().apply { this[45] += 1L }
        for ((label, publishedStreams, timestamps) in listOf(
            Triple("middle", video, shifted),
            Triple("normalized-middle", video.replace("\"start_pts\":\"3000\"", "\"start_pts\":\"0\""),
                shifted.map { it - 3000L }),
        )) {
            val request = request(label)
            val jobs = mutableListOf<VideoMediaProcessRequest>()
            val error = assertFailsWith<VideoMediaProbeException> {
                fake(publishedStreams = publishedStreams, publishedTimestamps = timestamps, onProcess = { job, _ ->
                        jobs += job
                        if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                            Files.writeString(request.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
                    }).validateTake(request)
            }
            assertEquals(VideoMediaProbeFailure.INVALID_OUTPUT, error.failure)
            assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-source-frames" })
            assertTrue(jobs.any { it.workingDirectory.fileName.toString() == "take-published-frames" })
            assertFalse(Files.exists(request.outputDirectory.resolve("take-validation.json")))
        }
    }

    @Test fun `already silent media retains its original bytes with no remux`() {
        val request = request("silent")
        val video = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30000/1001","nb_read_frames":"90",
            "time_base":"1/30000","start_pts":"3000","duration_ts":"90090"}"""
        val jobs = mutableListOf<VideoMediaProcessRequest>()
        val result = fake(streams = video, onProcess = { job, _ -> jobs += job }).validateTake(request)
        assertEquals(VideoTakeConversion.NONE, result.conversion)
        assertEquals(result.source, result.published)
        assertEquals(request.input.toRealPath(), result.validatedPath)
        assertFalse(jobs.any { it.workingDirectory.fileName.toString() == "strip-incidental-audio" })
    }

    @Test fun `derivative truncation or mutation cannot publish a validation receipt`() {
        val truncated = request("derivative-truncated")
        assertEquals(VideoMediaProbeFailure.PROCESS_FAILED, assertFailsWith<VideoMediaProbeException> {
            fake(onProcess = { job, _ ->
                when (job.workingDirectory.fileName.toString()) {
                    "strip-incidental-audio" -> Files.writeString(truncated.outputDirectory.resolve("silent-take.mp4"), "partial")
                    "take-published-full-decode" -> throw VideoMediaProcessException(VideoMediaProcessFailure.NONZERO_EXIT, "truncated")
                }
            }).validateTake(truncated)
        }.failure)
        assertFalse(Files.exists(truncated.outputDirectory.resolve("take-validation.json")))
        val changed = request("derivative-changed")
        assertEquals(VideoMediaProbeFailure.INVALID_OUTPUT, assertFailsWith<VideoMediaProbeException> {
            fake(onProcess = { job, _ ->
                when (job.workingDirectory.fileName.toString()) {
                    "strip-incidental-audio" -> Files.writeString(changed.outputDirectory.resolve("silent-take.mp4"), "first")
                    "take-published-full-decode" -> Files.writeString(changed.outputDirectory.resolve("silent-take.mp4"), "second")
                }
            }).validateTake(changed)
        }.failure)
        assertFalse(Files.exists(changed.outputDirectory.resolve("take-validation.json")))
    }

    @Test fun `zero start normalization is explicit and unsupported codec or changed source rejects`() {
        val request = request("normalize")
        val published = """{"codec_type":"video","codec_name":"h264","width":320,"height":180,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30000/1001","nb_read_frames":"90",
            "time_base":"1/30000","start_pts":"0","duration_ts":"90090"}"""
        val result = fake(publishedStreams = published, onProcess = { job, _ ->
            if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                Files.writeString(request.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
        }).validateTake(request)
        assertEquals(VideoTakeConversion.AUDIO_REMUX_START_NORMALIZED, result.conversion)
        assertEquals(3000L, result.source.videoStartPts)
        assertEquals(0L, result.published.videoStartPts)
        assertTrue(Files.readString(request.outputDirectory.resolve("take-validation.json"))
            .contains("\"sourceStartPts\": 3000"))
        val bad = request("unsupported")
        assertEquals(VideoMediaProbeFailure.INVALID_MEDIA, assertFailsWith<VideoMediaProbeException> {
            fake(streams = published.replace("h264", "mpeg4")).validateTake(bad)
        }.failure)
        val changed = request("source-changed")
        assertEquals(VideoMediaProbeFailure.INVALID_OUTPUT, assertFailsWith<VideoMediaProbeException> {
            fake(onProcess = { job, _ ->
                if (job.workingDirectory.fileName.toString() == "strip-incidental-audio") {
                    Files.writeString(changed.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
                    Files.writeString(changed.input, "changed source")
                }
            }).validateTake(changed)
        }.failure)
    }

    @Test fun `validation cancellation propagates before launch and through derivative decode`() {
        val before = request("cancel-before")
        val cancelled = VideoMediaProcessCancellation().apply { cancel() }
        assertEquals(VideoMediaProcessFailure.CANCELLED, assertFailsWith<VideoMediaProcessException> {
            fake().validateTake(before, cancelled)
        }.failure)
        assertFalse(Files.exists(before.outputDirectory))
        val during = request("cancel-derivative")
        val active = VideoMediaProcessCancellation()
        assertEquals(VideoMediaProcessFailure.CANCELLED, assertFailsWith<VideoMediaProcessException> {
            fake(onProcess = { job, received ->
                assertSame(active, received)
                if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                    Files.writeString(during.outputDirectory.resolve("silent-take.mp4"), "silent derivative")
                if (job.workingDirectory.fileName.toString() == "take-published-full-decode") active.cancel()
            }).validateTake(during, active)
        }.failure)
        assertFalse(Files.exists(during.outputDirectory.resolve("take-validation.json")))
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
