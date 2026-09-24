package app.melotrail.video.adapter

import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.controlledMotionInvocationDescriptors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/** Explicitly configured runtime; no executable is resolved through the development PATH. */
data class VideoMotionRuntime(
    val node: VideoGenerationDependencyPin,
    val compositor: VideoGenerationDependencyPin,
    /** The compositor loads this sibling module for scenery and continuation state. */
    val scenery: VideoGenerationDependencyPin,
    val canvasPackageManifest: VideoGenerationDependencyPin,
    /** Explicit pins for every JS/native artifact loaded from this package. */
    val canvasArtifacts: List<VideoGenerationDependencyPin>,
    val ffmpeg: VideoGenerationDependencyPin,
    val expectedCanvasVersion: String,
)

data class VideoMotionRenderRequest(
    val runtime: VideoMotionRuntime,
    val projectRoot: Path,
    val requestJson: Path,
    val outputParent: Path,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val timeoutPerInvocation: Duration,
)

data class VideoMotionInvocationResult(val startFrame: Long, val endFrameExclusive: Long, val outputDirectory: Path, val receipt: Path)
data class VideoMotionRenderResult(val invocations: List<VideoMotionInvocationResult>)

/** Executes the existing renderer in bounded, isolated invocations under the native process owner. */
class VideoMotionRenderer(
    private val onValidatedFrame: (Long) -> Unit = {},
    private val runProcess: (VideoMediaProcessRequest, VideoMediaProcessCancellation) -> VideoMediaProcessResult,
) {
    constructor() : this(runProcess = { request, cancellation -> VideoMediaProcess().run(request, cancellation) })

    fun render(request: VideoMotionRenderRequest, cancellation: VideoMediaProcessCancellation = VideoMediaProcessCancellation()): VideoMotionRenderResult {
        require(request.timeoutPerInvocation > Duration.ZERO)
        require(request.startFrame >= 0 && request.endFrameExclusive > request.startFrame)
        val node = pinnedFile(request.runtime.node, true)
        val script = pinnedFile(request.runtime.compositor, false)
        val scenery = pinnedFile(request.runtime.scenery, false)
        require(scenery == script.parent.resolve("scenery.cjs")) { "Pinned scenery module must be the compositor's loaded sibling." }
        val canvasManifest = pinnedFile(request.runtime.canvasPackageManifest, false)
        val ffmpeg = pinnedFile(request.runtime.ffmpeg, true)
        val canvas = Json.parseToJsonElement(Files.readString(canvasManifest)).jsonObject
        require(canvas["name"]?.jsonPrimitive?.content == "@napi-rs/canvas" &&
            canvas["version"]?.jsonPrimitive?.content == request.runtime.expectedCanvasVersion) { "Configured Canvas package identity/version does not match the pinned runtime." }
        require(request.runtime.canvasArtifacts.isNotEmpty()) { "Canvas implementation artifacts must be explicitly pinned." }
        val artifactHashes = request.runtime.canvasArtifacts.associate { pin ->
            val artifact = pinnedFile(pin, false)
            artifact.toRealPath() to pin.sha256
        }
        require(artifactHashes.size == request.runtime.canvasArtifacts.size) { "Duplicate Canvas artifact pin." }
        val parent = request.outputParent.toAbsolutePath().normalize()
        require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent) && parent.toRealPath() == parent) { "Output parent must be an existing real directory." }
        val runtimeProbe = "const fs=require('node:fs'),{createRequire}=require('node:module');const r=createRequire(process.argv[1]);const p=fs.realpathSync(r.resolve('@napi-rs/canvas'));const manifest=fs.realpathSync(r.resolve('@napi-rs/canvas/package.json'));const m=r('@napi-rs/canvas/package.json');r('@napi-rs/canvas');const artifacts=Object.keys(require.cache).map(x=>fs.realpathSync(x)).filter(x=>x!==manifest&&(x.startsWith(require('node:path').dirname(manifest)+'/')||x.includes('/@napi-rs/canvas-darwin-arm64/')));process.stdout.write(JSON.stringify({path:p,manifest,version:m.version,artifacts}));"
        runProcess(VideoMediaProcessRequest(node, request.runtime.node.sha256, listOf("-e", runtimeProbe, script.toString()), parent.resolve(".motion-node-probe-${java.util.UUID.randomUUID()}"), request.timeoutPerInvocation, environment = mapOf("NODE_PATH" to canvasManifest.parent.parent.parent.toString())), cancellation).also { probe ->
            val loaded = Json.parseToJsonElement(probe.stdout.text).jsonObject
            require(Path.of(loaded["manifest"]!!.jsonPrimitive.content) == canvasManifest.toRealPath() &&
                Path.of(loaded["path"]!!.jsonPrimitive.content).startsWith(canvasManifest.parent.toRealPath())) { "Node loaded a different Canvas package than the configured pin." }
            require(loaded["version"]!!.jsonPrimitive.content == request.runtime.expectedCanvasVersion) { "Node loaded a mismatched Canvas package version." }
            val loadedArtifacts = loaded["artifacts"]!!.jsonArray.map { Path.of(it.jsonPrimitive.content).toRealPath() }.toSet()
            require(loadedArtifacts == artifactHashes.keys && loadedArtifacts.all { artifactHashes[it] == sha256(it) }) { "Node consumed Canvas artifacts outside the verified package pin." }
        }
        runProcess(VideoMediaProcessRequest(ffmpeg, request.runtime.ffmpeg.sha256, listOf("-version"), parent.resolve(".motion-ffmpeg-probe-${java.util.UUID.randomUUID()}"), request.timeoutPerInvocation), cancellation)
        checkNotCancelled(cancellation)
        val root = request.projectRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && !Files.isSymbolicLink(root) && root.toRealPath() == root) { "Motion project root must be a real, non-symlink directory." }
        val descriptor = request.requestJson.toAbsolutePath().normalize()
        require(descriptor.startsWith(root) && Files.isRegularFile(descriptor, NOFOLLOW_LINKS) && !Files.isSymbolicLink(descriptor)) { "Motion request must be a regular file inside the project root." }
        val requestText = Files.readString(descriptor)
        val base = Json.parseToJsonElement(requestText).jsonObject
        val range = base["frameRange"]?.jsonObject ?: error("Motion request has no frameRange object")
        val originalStart = range["startFrame"]!!.jsonPrimitive.content.toLong()
        val originalCount = range["frameCount"]!!.jsonPrimitive.content.toLong()
        require(originalStart == request.startFrame && originalStart + originalCount == request.endFrameExclusive) { "Configured render range does not match the descriptor's absolute range." }
        val results = mutableListOf<VideoMotionInvocationResult>()
        var start = request.startFrame
        var index = 0
        var continuationState: kotlinx.serialization.json.JsonObject? = null
        val chunks = controlledMotionInvocationDescriptors(requestText).iterator()
        while (start < request.endFrameExclusive) {
            if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Controlled motion was cancelled between render chunks.")
            val end = minOf(request.endFrameExclusive, start + MAX_FRAMES)
            val output = parent.resolve("motion-${request.startFrame}-$index")
            require(!Files.exists(output, NOFOLLOW_LINKS)) { "Controlled-motion output collision: $output" }
            val job = parent.resolve(".motion-job-${request.startFrame}-$index")
            require(!Files.exists(job, NOFOLLOW_LINKS))
            Files.createDirectory(job)
            try {
                val chunkRequest = job.resolve("request.json")
                val chunkObject = Json.parseToJsonElement(chunks.next()).jsonObject.toMutableMap().apply {
                    if (continuationState != null) {
                        require(get("scenery") != null) { "A scenery continuation state exists without a scenery request." }
                        put("initialState", continuationState!!)
                    }
                }
                val chunkText = kotlinx.serialization.json.JsonObject(chunkObject).toString()
                Files.writeString(chunkRequest, chunkText)
                runProcess(VideoMediaProcessRequest(
                    executable = node,
                    executableSha256 = request.runtime.node.sha256,
                    arguments = listOf(script.toString(), "--request", chunkRequest.toString(), "--project-root", root.toString(), "--output", output.toString()),
                    workingDirectory = job.resolve("work"),
                    timeout = request.timeoutPerInvocation,
                    environment = mapOf("NODE_PATH" to canvasManifest.parent.parent.parent.toString(), "MELOTRAIL_FFMPEG_PATH" to ffmpeg.toString()),
                ), cancellation)
                // The sibling module is loaded by Node, not by the pinned executable.
                // Recheck immediately after execution and again at receipt acceptance so
                // mutation during rendering or receipt validation cannot be published.
                verifyCanvasArtifacts(request.runtime.canvasArtifacts, artifactHashes)
                verifyRuntimePins(request.runtime, canvasManifest, ffmpeg)
                require(Files.isDirectory(output, NOFOLLOW_LINKS) && !Files.isSymbolicLink(output)) { "Renderer did not create its unique output directory." }
                val receipt = listOf(
                    output.resolve("render-receipt-${start.toString().padStart(8, '0')}-${(end - start).toString().padStart(8, '0')}.json"),
                    output.resolve("render-receipt.json"),
                ).singleOrNull { Files.isRegularFile(it, NOFOLLOW_LINKS) && !Files.isSymbolicLink(it) }
                    ?: error("Renderer must produce exactly one valid receipt for absolute range $start..$end.")
                // Node's JSON.stringify canonicalizes numeric literals (e.g. 0.0 -> 0).
                // Use the pinned interpreter on the exact written chunk, not a Kotlin JSON re-encoding.
                val canonicalProbe = "const fs=require('node:fs'),crypto=require('node:crypto');const r=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));const {schema,preparedScene,seed,fps,canvas,frameRange,controls,scenery,initialState}=r;process.stdout.write(crypto.createHash('sha256').update(JSON.stringify({schema,preparedScene,seed,fps,canvas,frameRange,controls,scenery,initialState})).digest('hex'));"
                val canonicalHash = runProcess(VideoMediaProcessRequest(node, request.runtime.node.sha256,
                    listOf("-e", canonicalProbe, chunkRequest.toString()), job.resolve("canonical-work"), request.timeoutPerInvocation), cancellation).stdout.text
                validateReceipt(receipt, request.runtime, chunkText, canonicalHash, root, start, end, output, cancellation)
                verifyCanvasArtifacts(request.runtime.canvasArtifacts, artifactHashes)
                verifyRuntimePins(request.runtime, canvasManifest, ffmpeg)
                checkNotCancelled(cancellation)
                val receiptJson = Json.parseToJsonElement(Files.readString(receipt)).jsonObject
                val sceneryReceipt = receiptJson["scenery"]?.jsonObject
                if (end < request.endFrameExclusive && base["scenery"] != null) {
                    continuationState = sceneryReceipt?.get("finalState")?.jsonObject
                        ?: error("A scenery chunk omitted its continuation state; later chunks cannot safely render.")
                }
                results += VideoMotionInvocationResult(start, end, output, receipt)
            } finally {
                // Keep private invocation evidence and partial output for diagnosis/recovery.
            }
            start = end
            index++
        }
        require(!chunks.hasNext()) { "Controlled descriptor contains unconsumed frame chunks." }
        checkNotCancelled(cancellation)
        verifyCanvasArtifacts(request.runtime.canvasArtifacts, artifactHashes)
        verifyRuntimePins(request.runtime, canvasManifest, ffmpeg)
        checkNotCancelled(cancellation)
        return VideoMotionRenderResult(results)
    }

    private fun validateReceipt(path: Path, runtime: VideoMotionRuntime, request: String, canonicalHash: String, root: Path, start: Long, end: Long, output: Path, cancellation: VideoMediaProcessCancellation) {
        checkNotCancelled(cancellation)
        val obj = Json.parseToJsonElement(Files.readString(path)).jsonObject
        require(obj["schema"]?.jsonPrimitive?.content == "melotrail-controlled-motion-receipt-v1")
        require(obj["tool"]?.jsonObject?.get("version")?.jsonPrimitive?.content == "1.1.0")
        val frameRange = obj["frameRange"]!!.jsonObject
        require(frameRange["startFrame"]!!.jsonPrimitive.content.toLong() == start && frameRange["frameCount"]!!.jsonPrimitive.content.toLong() == end - start)
        val expectedPins = Json.parseToJsonElement(request).jsonObject["preparedScene"]?.jsonObject?.let { scene ->
            sequenceOf("layers", "poses", "masks").flatMap { key -> scene[key]?.jsonArray.orEmpty().asSequence() }
                .mapNotNull { it.jsonObject["image"]?.jsonObject?.get("artifact")?.jsonObject?.get("sha256")?.jsonPrimitive?.content }.toSet()
        } ?: Regex("\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"").findAll(request).map { it.groupValues[1] }.toSet()
        val actualPins = obj["sourcePins"]!!.let { it as kotlinx.serialization.json.JsonArray }.map { it.jsonPrimitive.content }.toSet()
        require(actualPins == expectedPins) { "Receipt source pins do not exactly match the supplied descriptor." }
        val receiptRequestHash = obj["requestSha256"]?.jsonPrimitive?.content ?: error("Receipt has no chunk request identity.")
        require(canonicalHash.matches(Regex("[0-9a-f]{64}")) && receiptRequestHash == canonicalHash) { "Receipt request identity does not match this chunk." }
        require(runtime.compositor.ownedPath == null || sha256(Path.of(runtime.compositor.ownedPath)) == runtime.compositor.sha256)
        require(root.toRealPath() == root)
        val frames = obj["frames"] as? kotlinx.serialization.json.JsonArray ?: error("Receipt has no frame list")
        checkNotCancelled(cancellation)
        require(frames.size.toLong() == end - start) { "Receipt frame count does not cover the requested range." }
        frames.forEachIndexed { offset, element ->
            checkNotCancelled(cancellation)
            val frame = element.jsonObject
            require(frame["frame"]!!.jsonPrimitive.content.toLong() == start + offset) { "Receipt frame sequence is not contiguous and absolute." }
            val name = frame["file"]!!.jsonPrimitive.content
            require(name == "frame-${(start + offset).toString().padStart(8, '0')}.png") { "Receipt frame path does not match its absolute frame." }
            val artifact = output.resolve(name).normalize()
            require(artifact.parent == output && Files.isRegularFile(artifact, NOFOLLOW_LINKS) && !Files.isSymbolicLink(artifact)) { "Receipt references a missing or unsafe frame artifact." }
            require(sha256(artifact) == frame["sha256"]!!.jsonPrimitive.content) { "Receipt frame digest mismatch at frame ${start + offset}." }
            onValidatedFrame(start + offset)
            checkNotCancelled(cancellation)
        }
        checkNotCancelled(cancellation)
    }

    private fun checkNotCancelled(cancellation: VideoMediaProcessCancellation) {
        if (cancellation.isCancelled()) throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "Controlled motion was cancelled during validation.")
    }

    private fun verifyRuntimePins(runtime: VideoMotionRuntime, canvasManifest: Path, ffmpeg: Path) {
        // These files are consumed by the child, but are not covered by the
        // executable-only identity checks in VideoMediaProcess.
        pinnedFile(runtime.node, true)
        pinnedFile(runtime.compositor, false)
        pinnedFile(runtime.scenery, false)
        require(pinnedFile(runtime.canvasPackageManifest, false) == canvasManifest) { "Canvas manifest identity changed." }
        require(pinnedFile(runtime.ffmpeg, true) == ffmpeg) { "FFmpeg identity changed." }
    }

    private fun verifyCanvasArtifacts(pins: List<VideoGenerationDependencyPin>, expected: Map<Path, String>) {
        pins.forEach { pin ->
            val path = pinnedFile(pin, false)
            require(expected[path] == pin.sha256) { "Canvas artifact identity changed: ${pin.id}." }
        }
    }

    private fun requirePinnedCanvasArtifact(path: Path) {
        require(path.isAbsolute && !Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Pinned Canvas artifact is missing or unsafe: $path." }
        require(path.normalize() == path) { "Pinned Canvas artifact path is not normalized: $path." }
    }

    private fun pinnedFile(pin: VideoGenerationDependencyPin, executable: Boolean): Path {
        val raw = requireNotNull(pin.ownedPath) { "Runtime pin '${pin.id}' requires an explicit absolute path." }
        val path = Path.of(raw)
        require(path.isAbsolute && path.normalize() == path && !Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Pinned runtime file '${pin.id}' is missing or unsafe." }
        require(!executable || Files.isExecutable(path)) { "Pinned runtime '${pin.id}' is not executable." }
        require(sha256(path) == pin.sha256) { "Pinned runtime digest changed: ${pin.id}." }
        return path
    }

    private fun sha256(text: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(text).joinToString("") { "%02x".format(it) }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object { const val MAX_FRAMES = 300 }
}
