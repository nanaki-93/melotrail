package app.melotrail.video.adapter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale

data class VideoMediaProbeRequest(
    val toolsDirectory: Path,
    val input: Path,
    /** Must be an absent child of an existing directory. Partial evidence is retained on failure. */
    val outputDirectory: Path,
    val timeoutPerProcess: Duration = Duration.ofSeconds(30),
)

data class VideoMediaMetadata(
    val videoCodec: String,
    val width: Int,
    val height: Int,
    val durationSeconds: Double,
    val frameRate: Double,
    val decodedFrameCount: Long?,
    val audioStreamCount: Int,
)

data class VideoMediaProbeResult(
    val outputDirectory: Path,
    val report: Path,
    val firstFrame: Path,
    val seekFrame: Path,
    val encodedVideo: Path,
    val inputMetadata: VideoMediaMetadata,
    val encodedMetadata: VideoMediaMetadata,
)

enum class VideoMediaProbeFailure {
    INVALID_REQUEST,
    TOOL_CONFIGURATION,
    PROCESS_FAILED,
    DISK_EXHAUSTED,
    INVALID_MEDIA,
    INVALID_OUTPUT,
}

class VideoMediaProbeException(
    val failure: VideoMediaProbeFailure,
    message: String,
    val operation: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Real FFmpeg boundary for owned, local video files.
 *
 * This adapter is instantiated only by video work. Every native invocation delegates to
 * [VideoMediaProcess], and every generated artifact is a relative child of a fresh owned output
 * directory. The accepted input type is [Path], so URLs never enter FFmpeg's input position.
 */
class VideoMediaProbe internal constructor(
    private val runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
) {
    constructor() : this({ request, cancellation -> VideoMediaProcess().run(request, cancellation) })

    fun run(
        request: VideoMediaProbeRequest,
        cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation(),
    ): VideoMediaProbeResult {
        val validated = validate(request)
        val inputDigest = sha256(validated.input)
        val output = createPrivateDirectory(validated.outputDirectory, "probe output")
        val operations = mutableListOf<OperationEvidence>()

        try {
            val ffmpegVersion = invoke(
                "ffmpeg-version",
                validated.ffmpeg,
                listOf("-hide_banner", "-version"),
                output,
                request,
                cancellation,
                operations,
            ).stdout.text
            val ffprobeVersion = invoke(
                "ffprobe-version",
                validated.ffprobe,
                listOf("-hide_banner", "-version"),
                output,
                request,
                cancellation,
                operations,
            ).stdout.text
            verifyReportedBuild(ffmpegVersion, validated.manifest, "ffmpeg")
            verifyReportedBuild(ffprobeVersion, validated.manifest, "ffprobe")

            val inputMetadata = metadata(
                "input-metadata",
                validated.ffprobe,
                validated.input,
                output,
                request,
                cancellation,
                operations,
            )
            requireUsableVideo(inputMetadata, requireSilent = true, label = "Input fixture")

            decodeFully("input-decode", validated.ffmpeg, validated.input, output, request, cancellation, operations)

            val firstFrameJob = invoke(
                "first-frame",
                validated.ffmpeg,
                frameArguments(validated.input, null, FIRST_FRAME_NAME),
                output,
                request,
                cancellation,
                operations,
            ).workingDirectory
            val firstFrame = requireNewArtifact(firstFrameJob.resolve(FIRST_FRAME_NAME), "first decoded frame")

            val seekSeconds = minOf(1.0, inputMetadata.durationSeconds / 2.0)
            if (seekSeconds <= 0.0) invalidMedia("Input fixture does not have a seekable positive duration.")
            val seekFrameJob = invoke(
                "seek-frame",
                validated.ffmpeg,
                frameArguments(validated.input, seekSeconds, SEEK_FRAME_NAME),
                output,
                request,
                cancellation,
                operations,
            ).workingDirectory
            val seekFrame = requireNewArtifact(seekFrameJob.resolve(SEEK_FRAME_NAME), "seeked decoded frame")
            if (sha256(firstFrame) == sha256(seekFrame)) {
                invalidOutput("The first and ${formatSeconds(seekSeconds)} s seek frames are byte-identical; motion/seek was not proved.")
            }

            val encodeJob = invoke(
                "silent-h264-encode",
                validated.ffmpeg,
                encodeArguments(validated.input),
                output,
                request,
                cancellation,
                operations,
            ).workingDirectory
            val encoded = requireNewArtifact(encodeJob.resolve(ENCODED_VIDEO_NAME), "encoded silent MP4")
            val encodedMetadata = metadata(
                "encoded-metadata",
                validated.ffprobe,
                encoded,
                output,
                request,
                cancellation,
                operations,
            )
            requireUsableVideo(encodedMetadata, requireSilent = true, label = "Encoded output")
            if (encodedMetadata.videoCodec != "h264") invalidOutput("Encoded output codec is '${encodedMetadata.videoCodec}', expected h264.")
            if (encodedMetadata.width != inputMetadata.width || encodedMetadata.height != inputMetadata.height) {
                invalidOutput("Encoded output dimensions changed from ${inputMetadata.width}x${inputMetadata.height} to ${encodedMetadata.width}x${encodedMetadata.height}.")
            }
            decodeFully("encoded-decode", validated.ffmpeg, encoded, output, request, cancellation, operations)

            if (sha256(validated.input) != inputDigest) invalidOutput("Input bytes changed during the media probe: ${validated.input}")
            val report = output.resolve(REPORT_NAME)
            val reportJson = reportJson(
                validated,
                inputDigest,
                inputMetadata,
                encodedMetadata,
                firstFrame,
                seekFrame,
                seekSeconds,
                encoded,
                operations,
            )
            Files.writeString(report, REPORT_JSON.encodeToString(JsonObject.serializer(), reportJson), CREATE_NEW)
            return VideoMediaProbeResult(output, report, firstFrame, seekFrame, encoded, inputMetadata, encodedMetadata)
        } catch (error: VideoMediaProbeException) {
            throw error
        } catch (error: Exception) {
            throw storageFailure("Video media probe could not finish writing its owned evidence: ${usefulMessage(error)}", error)
        }
    }

    private fun validate(request: VideoMediaProbeRequest): ValidatedRequest {
        val tools = requireAbsoluteNormalized(request.toolsDirectory, "Tools directory")
        if (!Files.isDirectory(tools, NOFOLLOW_LINKS) || Files.isSymbolicLink(tools)) {
            invalidRequest("Tools directory must be an existing non-symbolic-link directory: $tools")
        }
        val realTools = tools.toRealPath()
        val manifestPath = realTools.resolve(MANIFEST_NAME)
        if (!Files.isRegularFile(manifestPath, NOFOLLOW_LINKS) || Files.isSymbolicLink(manifestPath)) {
            toolConfiguration("Pinned media manifest is missing or is not a regular file: $manifestPath")
        }
        val manifest = parseManifest(manifestPath)
        val ffmpeg = validateTool(realTools, "ffmpeg", manifest.ffmpegSha256)
        val ffprobe = validateTool(realTools, "ffprobe", manifest.ffprobeSha256)

        val input = requireAbsoluteNormalized(request.input, "Input video")
        if (!Files.isRegularFile(input, NOFOLLOW_LINKS) || Files.isSymbolicLink(input)) {
            invalidRequest("Input video must be an existing regular non-symbolic-link file: $input")
        }
        val realInput = input.toRealPath()
        val output = requireAbsoluteNormalized(request.outputDirectory, "Output directory")
        if (Files.exists(output, NOFOLLOW_LINKS)) invalidRequest("Refusing to reuse existing probe output directory: $output")
        val outputParent = output.parent ?: invalidRequest("Output directory must have a parent: $output")
        if (!Files.isDirectory(outputParent, NOFOLLOW_LINKS)) invalidRequest("Output-directory parent must exist: $outputParent")
        val resolvedOutput = outputParent.toRealPath().resolve(output.fileName.toString())
        if (realInput.startsWith(resolvedOutput) || resolvedOutput.startsWith(realInput)) {
            invalidRequest("Probe output must not contain or replace its input: $resolvedOutput")
        }
        if (request.timeoutPerProcess.isZero || request.timeoutPerProcess.isNegative || request.timeoutPerProcess > MAX_PROCESS_TIMEOUT) {
            invalidRequest("Per-process timeout must be greater than zero and no more than ${MAX_PROCESS_TIMEOUT.toMinutes()} minutes.")
        }
        return ValidatedRequest(realTools, realInput, resolvedOutput, ffmpeg, ffprobe, manifest)
    }

    private fun parseManifest(path: Path): ToolManifest {
        val objectValue = try {
            MANIFEST_JSON.parseToJsonElement(Files.readString(path)).jsonObject
        } catch (error: Exception) {
            toolConfiguration("Pinned media manifest is not valid JSON: $path (${usefulMessage(error)})", error)
        }
        fun text(name: String): String = (objectValue[name] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
            ?: toolConfiguration("Pinned media manifest '$name' must be a string: $path")
        val version = (objectValue["version"] as? JsonPrimitive)
            ?.takeUnless { it.isString }?.intOrNull
            ?: toolConfiguration("Pinned media manifest 'version' must be an integer: $path")
        if (text("schema") != MANIFEST_SCHEMA || version != MANIFEST_VERSION) {
            toolConfiguration("Unsupported media manifest schema/version in $path; expected $MANIFEST_SCHEMA v$MANIFEST_VERSION.")
        }
        if (text("distributionId") != DISTRIBUTION_ID || text("installation") != INSTALLATION_STRATEGY) {
            toolConfiguration("Media manifest does not select $DISTRIBUTION_ID as $INSTALLATION_STRATEGY.")
        }
        if (text("sourceUrl") != SOURCE_URL || text("sourceRevision") != SOURCE_REVISION || text("sourceSha256") != SOURCE_SHA256) {
            toolConfiguration("Media manifest source pin does not match the selected FFmpeg 9.0.1 commit archive.")
        }
        val ffmpegSha = text("ffmpegSha256")
        val ffprobeSha = text("ffprobeSha256")
        if (!SHA256.matches(ffmpegSha) || !SHA256.matches(ffprobeSha)) {
            toolConfiguration("Media manifest executable hashes must be lowercase SHA-256 values.")
        }
        if (ffmpegSha != FFMPEG_SHA256 || ffprobeSha != FFPROBE_SHA256) {
            toolConfiguration("Media manifest executable pins do not match the selected FFmpeg 9.0.1 build.")
        }
        val options = stringArray(objectValue, "buildOptions", path)
        if (options.isEmpty() || options.any { it.isBlank() } || options.distinct().size != options.size) {
            toolConfiguration("Media manifest build options must be non-empty, non-blank and unique.")
        }
        val missingOptions = REQUIRED_BUILD_OPTIONS.filterNot { requirement -> hasBuildRequirement(options, requirement) }
        if (missingOptions.isNotEmpty()) toolConfiguration("Media manifest omits required build options: ${missingOptions.joinToString()}.")
        val notices = stringArray(objectValue, "notices", path)
        if (notices.isEmpty() || notices.any { it.isBlank() }) toolConfiguration("Media manifest must record non-blank installed license/notices.")
        return ToolManifest(ffmpegSha, ffprobeSha, options, notices)
    }

    private fun stringArray(objectValue: JsonObject, name: String, path: Path): List<String> {
        val array = objectValue[name] as? JsonArray
            ?: toolConfiguration("Pinned media manifest '$name' must be an array: $path")
        return array.map {
            (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
                ?: toolConfiguration("Pinned media manifest '$name' must contain only strings: $path")
        }
    }

    private fun validateTool(directory: Path, name: String, expectedSha256: String): ToolPin {
        val path = directory.resolve(name)
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || !Files.isExecutable(path)) {
            toolConfiguration("Pinned $name binary is missing, symbolic, non-regular, or non-executable: $path")
        }
        return ToolPin(path, expectedSha256)
    }

    private fun invoke(
        operation: String,
        tool: ToolPin,
        arguments: List<String>,
        output: Path,
        request: VideoMediaProbeRequest,
        cancellation: VideoMediaProcessCancellation,
        evidence: MutableList<OperationEvidence>,
    ): VideoMediaProcessResult {
        val job = output.resolve(operation)
        return try {
            runProcess(
                VideoMediaProcessRequest(
                    executable = tool.path,
                    executableSha256 = tool.sha256,
                    arguments = arguments,
                    workingDirectory = job,
                    timeout = request.timeoutPerProcess,
                    maxStdoutBytes = 1_048_576,
                    maxStderrBytes = 1_048_576,
                    environment = mapOf("LC_ALL" to "C", "LANG" to "C"),
                ),
                cancellation,
            ).also { evidence += OperationEvidence(operation, it.elapsed.toMillis()) }
        } catch (error: VideoMediaProcessException) {
            val diskFull = hasDiskExhaustionDiagnostic(error.message, error.stderr.text)
            throw VideoMediaProbeException(
                if (diskFull) VideoMediaProbeFailure.DISK_EXHAUSTED else VideoMediaProbeFailure.PROCESS_FAILED,
                "$operation failed: ${error.message}",
                operation,
                error,
            )
        }
    }

    private fun metadata(
        operation: String,
        ffprobe: ToolPin,
        input: Path,
        output: Path,
        request: VideoMediaProbeRequest,
        cancellation: VideoMediaProcessCancellation,
        evidence: MutableList<OperationEvidence>,
    ): VideoMediaMetadata {
        val result = invoke(operation, ffprobe, listOf(
            "-v", "error",
            "-protocol_whitelist", "file,pipe",
            "-count_frames",
            "-show_entries", "stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames:format=duration",
            "-of", "json",
            input.toString(),
        ), output, request, cancellation, evidence)
        return parseMetadata(result.stdout.text, operation)
    }

    private fun decodeFully(
        operation: String,
        ffmpeg: ToolPin,
        input: Path,
        output: Path,
        request: VideoMediaProbeRequest,
        cancellation: VideoMediaProcessCancellation,
        evidence: MutableList<OperationEvidence>,
    ) {
        invoke(operation, ffmpeg, listOf(
            "-nostdin", "-hide_banner", "-v", "error", "-xerror",
            "-protocol_whitelist", "file,pipe",
            "-i", input.toString(),
            "-map", "0:v:0", "-an", "-sn", "-dn",
            "-c:v", "rawvideo", "-f", "null", "-",
        ), output, request, cancellation, evidence)
    }

    private fun frameArguments(input: Path, seekSeconds: Double?, filename: String): List<String> = buildList {
        addAll(listOf(
            "-nostdin", "-hide_banner", "-v", "error", "-xerror",
            "-protocol_whitelist", "file,pipe", "-i", input.toString(),
        ))
        if (seekSeconds != null) addAll(listOf("-ss", formatSeconds(seekSeconds)))
        addAll(listOf(
            "-map", "0:v:0", "-an", "-sn", "-dn",
            "-frames:v", "1", "-c:v", "png", "-f", "image2", filename,
        ))
    }

    private fun encodeArguments(input: Path): List<String> = listOf(
        "-nostdin", "-hide_banner", "-v", "error", "-xerror",
        "-protocol_whitelist", "file,pipe", "-i", input.toString(),
        "-map", "0:v:0", "-an", "-sn", "-dn",
        "-c:v", "h264_videotoolbox", "-allow_sw", "0", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", "-f", "mp4", ENCODED_VIDEO_NAME,
    )

    private fun parseMetadata(text: String, operation: String): VideoMediaMetadata {
        val root = try {
            MANIFEST_JSON.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            invalidMedia("$operation returned invalid ffprobe JSON: ${usefulMessage(error)}", operation, error)
        }
        val streams = root["streams"]?.jsonArray ?: invalidMedia("$operation returned no stream list.", operation)
        val videoStreams = streams.map { it.jsonObject }.filter { it["codec_type"]?.jsonPrimitive?.content == "video" }
        val audioCount = streams.count { it.jsonObject["codec_type"]?.jsonPrimitive?.content == "audio" }
        if (videoStreams.size != 1) invalidMedia("$operation found ${videoStreams.size} video streams; expected exactly one.", operation)
        val video = videoStreams.single()
        val format = root["format"]?.jsonObject ?: invalidMedia("$operation returned no format record.", operation)
        fun videoText(name: String) = video[name]?.jsonPrimitive?.content
            ?: invalidMedia("$operation video stream is missing '$name'.", operation)
        val duration = format["duration"]?.jsonPrimitive?.doubleOrNull
            ?: invalidMedia("$operation format is missing a numeric duration.", operation)
        return VideoMediaMetadata(
            videoCodec = videoText("codec_name"),
            width = videoText("width").toIntOrNull() ?: invalidMedia("$operation width is invalid.", operation),
            height = videoText("height").toIntOrNull() ?: invalidMedia("$operation height is invalid.", operation),
            durationSeconds = duration,
            frameRate = parseRate(videoText("avg_frame_rate"), operation),
            decodedFrameCount = video["nb_read_frames"]?.jsonPrimitive?.content?.takeUnless { it == "N/A" }?.toLongOrNull(),
            audioStreamCount = audioCount,
        )
    }

    private fun parseRate(value: String, operation: String): Double {
        val parts = value.split('/')
        if (parts.size != 2) invalidMedia("$operation frame rate '$value' is invalid.", operation)
        val numerator = parts[0].toDoubleOrNull() ?: invalidMedia("$operation frame rate '$value' is invalid.", operation)
        val denominator = parts[1].toDoubleOrNull() ?: invalidMedia("$operation frame rate '$value' is invalid.", operation)
        if (denominator == 0.0) invalidMedia("$operation frame rate denominator is zero.", operation)
        return numerator / denominator
    }

    private fun requireUsableVideo(metadata: VideoMediaMetadata, requireSilent: Boolean, label: String) {
        if (metadata.width <= 0 || metadata.height <= 0 || metadata.durationSeconds <= 0.0 || metadata.frameRate <= 0.0) {
            invalidMedia("$label has invalid geometry, duration, or cadence: $metadata")
        }
        if (metadata.decodedFrameCount == null || metadata.decodedFrameCount <= 0) {
            invalidMedia("$label did not yield a positive decoded frame count.")
        }
        if (requireSilent && metadata.audioStreamCount != 0) {
            invalidMedia("$label contains ${metadata.audioStreamCount} audio stream(s); V12 requires silent video.")
        }
    }

    private fun verifyReportedBuild(version: String, manifest: ToolManifest, executableName: String) {
        if (!version.lineSequence().firstOrNull().orEmpty().startsWith("$executableName version 9.0.1")) {
            toolConfiguration("Pinned $executableName did not report FFmpeg 9.0.1.")
        }
        val normalizedVersion = version.replace("'", "")
        val missing = manifest.buildOptions.filterNot { normalizedVersion.contains(it) }
        if (missing.isNotEmpty()) toolConfiguration("Pinned $executableName build does not report configured options: ${missing.joinToString()}.")
    }

    private fun requireNewArtifact(path: Path, label: String): Path {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || Files.size(path) <= 0) {
            invalidOutput("$label was not created as a non-empty regular file: $path")
        }
        return path.toRealPath()
    }

    private fun reportJson(
        validated: ValidatedRequest,
        inputSha256: String,
        inputMetadata: VideoMediaMetadata,
        outputMetadata: VideoMediaMetadata,
        firstFrame: Path,
        seekFrame: Path,
        seekSeconds: Double,
        encoded: Path,
        operations: List<OperationEvidence>,
    ) = buildJsonObject {
        put("schema", "melotrail-video-media-probe-report")
        put("version", 1)
        put("status", "PASSED")
        put("distributionId", DISTRIBUTION_ID)
        put("installation", INSTALLATION_STRATEGY)
        put("sourceUrl", SOURCE_URL)
        put("sourceRevision", SOURCE_REVISION)
        put("sourceSha256", SOURCE_SHA256)
        put("ffmpegSha256", validated.manifest.ffmpegSha256)
        put("ffprobeSha256", validated.manifest.ffprobeSha256)
        put("buildOptions", buildJsonArray { validated.manifest.buildOptions.forEach { add(JsonPrimitive(it)) } })
        put("notices", buildJsonArray { validated.manifest.notices.forEach { add(JsonPrimitive(it)) } })
        put("input", artifactJson(validated.input, inputSha256, inputMetadata))
        put("firstFrame", artifactJson(firstFrame, sha256(firstFrame), null))
        put("seekFrame", buildJsonObject {
            put("path", seekFrame.toString())
            put("sha256", sha256(seekFrame))
            put("seekSeconds", seekSeconds)
        })
        put("encodedVideo", artifactJson(encoded, sha256(encoded), outputMetadata))
        put("operations", buildJsonArray {
            operations.forEach { operation ->
                add(buildJsonObject {
                    put("name", operation.name)
                    put("exitCode", 0)
                    put("elapsedMillis", operation.elapsedMillis)
                })
            }
        })
    }

    private fun artifactJson(path: Path, digest: String, metadata: VideoMediaMetadata?) = buildJsonObject {
        put("path", path.toString())
        put("sha256", digest)
        metadata?.let {
            put("videoCodec", it.videoCodec)
            put("width", it.width)
            put("height", it.height)
            put("durationSeconds", it.durationSeconds)
            put("frameRate", it.frameRate)
            put("decodedFrameCount", it.decodedFrameCount?.let { count -> JsonPrimitive(count) } ?: JsonNull)
            put("audioStreamCount", it.audioStreamCount)
        }
    }

    private fun createPrivateDirectory(path: Path, label: String): Path = try {
        Files.createDirectory(path, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),
        ))
    } catch (error: Exception) {
        throw storageFailure("Could not create $label directory '$path': ${usefulMessage(error)}", error)
    }

    private fun requireAbsoluteNormalized(path: Path, label: String): Path {
        if (!path.isAbsolute) invalidRequest("$label must be absolute: $path")
        if (path.any { it.toString() == "." || it.toString() == ".." }) {
            invalidRequest("$label must not contain '.' or '..' path components: $path")
        }
        return path
    }

    private fun sha256(path: Path): String {
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

    private fun formatSeconds(seconds: Double) = String.format(Locale.ROOT, "%.6f", seconds)

    private fun invalidRequest(message: String): Nothing = throw VideoMediaProbeException(VideoMediaProbeFailure.INVALID_REQUEST, message)
    private fun toolConfiguration(message: String, cause: Throwable? = null): Nothing =
        throw VideoMediaProbeException(VideoMediaProbeFailure.TOOL_CONFIGURATION, message, cause = cause)
    private fun invalidMedia(message: String, operation: String? = null, cause: Throwable? = null): Nothing =
        throw VideoMediaProbeException(VideoMediaProbeFailure.INVALID_MEDIA, message, operation, cause)
    private fun invalidOutput(message: String): Nothing = throw VideoMediaProbeException(VideoMediaProbeFailure.INVALID_OUTPUT, message)
    private fun storageFailure(message: String, cause: Throwable): VideoMediaProbeException = VideoMediaProbeException(
        if (hasDiskExhaustionDiagnostic(message, usefulMessage(cause))) VideoMediaProbeFailure.DISK_EXHAUSTED else VideoMediaProbeFailure.INVALID_OUTPUT,
        message,
        cause = cause,
    )

    private data class ToolPin(val path: Path, val sha256: String)
    private data class ToolManifest(
        val ffmpegSha256: String,
        val ffprobeSha256: String,
        val buildOptions: List<String>,
        val notices: List<String>,
    )
    private data class ValidatedRequest(
        val toolsDirectory: Path,
        val input: Path,
        val outputDirectory: Path,
        val ffmpeg: ToolPin,
        val ffprobe: ToolPin,
        val manifest: ToolManifest,
    )
    private data class OperationEvidence(val name: String, val elapsedMillis: Long)

    companion object {
        const val MANIFEST_NAME = "melotrail-video-tools.json"
        const val DISTRIBUTION_ID = "ffmpeg-9.0.1-macos-arm64-melotrail-1"
        const val INSTALLATION_STRATEGY = "separately-installed-local-tools"
        const val SOURCE_REVISION = "bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa"
        const val SOURCE_URL = "https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/$SOURCE_REVISION"
        const val SOURCE_SHA256 = "fb1931fd4eb29297ee1c1017a24f800c4d8fbea35b4f2aaeb28308a48a9149b4"
        const val FFMPEG_SHA256 = "3eec1c025127efed8f81259f833081920e57f809c9558ef1528d03d1e600b9e9"
        const val FFPROBE_SHA256 = "666c4ecdff7d14153d53e35cd83f0b0f37bffb7250080e994b90b59c575cd264"
        val REQUIRED_BUILD_OPTIONS = setOf(
            "--disable-everything",
            "--disable-autodetect",
            "--disable-network",
            "--disable-gpl",
            "--disable-nonfree",
            "--disable-avdevice",
            "--enable-protocol=file",
            "--enable-protocol=pipe",
            "--enable-decoder=h264",
            "--enable-decoder=png",
            "--enable-decoder=rawvideo",
            "--enable-encoder=h264_videotoolbox",
            "--enable-encoder=png",
            "--enable-demuxer=mov",
            "--enable-demuxer=image2",
            "--enable-muxer=mp4",
            "--enable-muxer=image2",
            "--enable-muxer=null",
        )
        private const val MANIFEST_SCHEMA = "melotrail-video-media-tools"
        private const val MANIFEST_VERSION = 1
        private const val FIRST_FRAME_NAME = "first-frame.png"
        private const val SEEK_FRAME_NAME = "seek-frame.png"
        private const val ENCODED_VIDEO_NAME = "encoded-silent.mp4"
        private const val REPORT_NAME = "video-media-probe-report.json"
        private val MAX_PROCESS_TIMEOUT = Duration.ofMinutes(10)
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val MANIFEST_JSON = Json { ignoreUnknownKeys = false }
        private val REPORT_JSON = Json { prettyPrint = true }

        internal fun hasDiskExhaustionDiagnostic(vararg diagnostics: String?): Boolean = diagnostics
            .filterNotNull()
            .any { diagnostic ->
                diagnostic.contains("No space left on device", ignoreCase = true) ||
                    diagnostic.contains("ENOSPC", ignoreCase = true) ||
                    diagnostic.contains("disk full", ignoreCase = true)
            }

        private fun hasBuildRequirement(options: List<String>, requirement: String): Boolean {
            if (!requirement.startsWith("--enable-") || '=' !in requirement) return requirement in options
            val prefix = requirement.substringBefore('=') + "="
            val component = requirement.substringAfter('=')
            return options.any { option ->
                option.startsWith(prefix) && component in option.substringAfter('=').split(',')
            }
        }
    }
}

private fun usefulMessage(error: Throwable): String = error.message ?: error::class.simpleName ?: "unknown error"
