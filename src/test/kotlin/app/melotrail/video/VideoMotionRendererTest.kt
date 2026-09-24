package app.melotrail.video

import app.melotrail.video.adapter.*
import app.melotrail.video.domain.VideoGenerationDependencyPin
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class VideoMotionRendererTest {
    @Test fun `real compositor carries scenery across a bounded chunk seam`() {
        // Use the Kotlin importer's actual descriptor and a real Node/Canvas process, not a receipt stub.
        VideoMotionDescriptorFixtureTest().`emit deterministic production importer bundles for controlled motion pixel comparisons`()
        val fixtures = java.nio.file.Path.of(System.getProperty("user.dir"), "build/video-motion-fixtures/wide-scenery").toRealPath()
        val original = Json.parseToJsonElement(Files.readString(fixtures.resolve("request.json"))).jsonObject
        val descriptor = fixtures.resolve("project/multi-chunk-request.json")
        Files.writeString(descriptor, JsonObject(original.toMutableMap().apply {
            put("frameRange", buildJsonObject { put("startFrame", 0); put("frameCount", 301) })
        }).toString())
        val node = java.nio.file.Path.of("/opt/homebrew/bin/node").toRealPath()
        val script = java.nio.file.Path.of(System.getProperty("user.dir"), "tools/video-motion/render.cjs").toRealPath()
        val manifest = script.parent.resolve("node_modules/@napi-rs/canvas/package.json").toRealPath()
        val version = Json.parseToJsonElement(Files.readString(manifest)).jsonObject.getValue("version").jsonPrimitive.content
        val pin = { id: String, path: java.nio.file.Path -> VideoGenerationDependencyPin(id, hash(path), path.toString()) }
        val output = Files.createTempDirectory("motion-real-chunks").toRealPath()
        val ffmpegProbe = output.resolve("owned-ffmpeg-probe")
        Files.writeString(ffmpegProbe, "#!/bin/sh\nprintf 'owned ffmpeg probe\\n'\n")
        ffmpegProbe.toFile().setExecutable(true)
        val result = VideoMotionRenderer().render(VideoMotionRenderRequest(
            VideoMotionRuntime(pin("node", node), pin("compositor", script), pin("scenery", script.parent.resolve("scenery.cjs")), pin("canvas", manifest), canvasPins(manifest), pin("ffmpeg-probe", ffmpegProbe), version),
            fixtures.resolve("project").toRealPath(), descriptor, output, 0, 301, Duration.ofMinutes(3),
        ))
        assertEquals(listOf(300L, 1L), result.invocations.map { it.endFrameExclusive - it.startFrame })
        assertEquals(300, Json.parseToJsonElement(Files.readString(result.invocations.last().receipt)).jsonObject
            .getValue("frameRange").jsonObject.getValue("startFrame").jsonPrimitive.int)
    }
    @Test fun `splits long absolute range preserving scenery state and validates identities`() {
        fixtureRender()
    }

    @Test fun `rejects wrong loaded Canvas package`() {
        assertFailsWith<IllegalArgumentException> { fixtureRender(canvasPathMismatch = true) }
    }

    @Test fun `probe resolves Canvas beside compositor rather than the configured NODE_PATH package`() {
        val root = Files.createTempDirectory("motion-conflicting-canvas").toRealPath()
        val scriptDir = Files.createDirectories(root.resolve("compositor"))
        val script = scriptDir.resolve("render.cjs")
        Files.writeString(script, "// compositor")
        val scenery = scriptDir.resolve("scenery.cjs")
        Files.writeString(scenery, "// scenery")
        fun canvas(base: java.nio.file.Path, version: String): java.nio.file.Path {
            val dir = Files.createDirectories(base.resolve("node_modules/@napi-rs/canvas"))
            Files.writeString(dir.resolve("index.js"), "module.exports = {};")
            return dir.resolve("package.json").also {
                Files.writeString(it, """{"name":"@napi-rs/canvas","version":"$version","main":"index.js"}""")
            }
        }
        val configuredCanvas = canvas(root.resolve("configured"), "0.1.80")
        canvas(scriptDir, "0.1.80")
        val node = java.nio.file.Path.of("/opt/homebrew/bin/node").toRealPath()
        val ffmpeg = root.resolve("ffmpeg")
        Files.writeString(ffmpeg, "#!/bin/sh\nexit 0\n")
        ffmpeg.toFile().setExecutable(true)
        fun pin(id: String, path: java.nio.file.Path) = VideoGenerationDependencyPin(id, hash(path), path.toString())
        val runtime = VideoMotionRuntime(pin("node", node), pin("script", script), pin("scenery", scenery), pin("canvas", configuredCanvas), canvasPins(configuredCanvas), pin("ffmpeg", ffmpeg), "0.1.80")
        val error = assertFailsWith<IllegalArgumentException> {
            VideoMotionRenderer().render(VideoMotionRenderRequest(runtime, root, root.resolve("missing.json"), root, 0, 1, Duration.ofSeconds(15)))
        }
        assertContains(error.message.orEmpty(), "Node loaded a different Canvas package")
    }

    @Test fun `caller cancellation stops each runtime probe before rendering`() {
        for (probe in listOf("canvas", "ffmpeg")) {
            val root = Files.createTempDirectory("motion-probe-cancel").toRealPath()
            fun pin(name: String, contents: String, executable: Boolean = false): VideoGenerationDependencyPin {
                val path = root.resolve(name)
                Files.createDirectories(path.parent)
                Files.writeString(path, contents)
                if (executable) path.toFile().setExecutable(true)
                return VideoGenerationDependencyPin(name.replace(Regex("[^A-Za-z0-9_-]"), "-"), hash(path), path.toString())
            }
            val node = pin("node", "node", true)
            val script = pin("render.cjs", "script")
            val scenery = pin("scenery.cjs", "scenery")
            val canvasPackage = root.resolve("node_modules/@napi-rs/canvas")
            Files.createDirectories(canvasPackage)
            Files.writeString(canvasPackage.resolve("index.js"), "module.exports = {}; ")
            val manifest = pin("node_modules/@napi-rs/canvas/package.json", """{"name":"@napi-rs/canvas","version":"0.1.80","main":"index.js"}""")
            val ffmpeg = pin("ffmpeg", "ffmpeg", true)
            val token = VideoMediaProcessCancellation()
            var calls = 0
            val renderer = VideoMotionRenderer { process, cancellation ->
                assertSame(token, cancellation, "${probe} probe must retain caller cancellation ownership")
                calls++
                if (probe == "canvas" || process.arguments.first() == "-version") {
                    token.cancel()
                    throw VideoMediaProcessException(VideoMediaProcessFailure.CANCELLED, "probe cancelled")
                }
                VideoMediaProcessResult(0, VideoMediaProcessOutput("""{"path":"${root.resolve("node_modules/@napi-rs/canvas/index.js")}","manifest":"${manifest.ownedPath}","version":"0.1.80","artifacts":["${root.resolve("node_modules/@napi-rs/canvas/index.js")}"]}""", 0, false), VideoMediaProcessOutput("", 0, false), Duration.ZERO, process.workingDirectory)
            }
            val error = assertFailsWith<VideoMediaProcessException> {
                renderer.render(VideoMotionRenderRequest(VideoMotionRuntime(node, script, scenery, manifest, canvasPins(java.nio.file.Path.of(manifest.ownedPath!!)), ffmpeg, "0.1.80"), root, root.resolve("missing.json"), root, 0, 1, Duration.ofSeconds(5)), token)
            }
            assertEquals(VideoMediaProcessFailure.CANCELLED, error.failure)
            assertEquals(if (probe == "canvas") 1 else 2, calls)
        }
    }

    @Test fun `rejects omitted source pins`() {
        assertFailsWith<IllegalArgumentException> { fixtureRender(omitSourcePin = true) }
    }

    @Test fun `rejects wrong chunk request identity`() {
        assertFailsWith<IllegalArgumentException> { fixtureRender(wrongRequestHash = true) }
    }

    @Test fun `cancellation during receipt frame validation never completes`() {
        val error = assertFailsWith<VideoMediaProcessException> { fixtureRender(cancelDuringValidation = true) }
        assertEquals(VideoMediaProcessFailure.CANCELLED, error.failure)
    }

    @Test fun `rejects scenery changed during rendering before accepting the first chunk`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateSceneryDuringRender = true, mutateSceneryDuringValidation = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: scenery")
    }

    @Test fun `rejects scenery changed while validating the final chunk before returning`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateSceneryDuringValidation = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: scenery")
    }

    @Test fun `rejects scenery changed after receipt validation before accepting receipt`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateSceneryAfterValidation = true, mutateSceneryDuringValidation = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: scenery")
    }

    @Test fun `rejects Canvas artifact changed during rendering before accepting receipt`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateCanvasDuringRender = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: canvas-artifact-0")
    }

    @Test fun `rejects Canvas manifest changed during rendering`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateManifestDuringRender = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: node_modules--napi-rs-canvas-package-json")
    }

    @Test fun `rejects FFmpeg changed during rendering`() {
        val error = assertFailsWith<IllegalArgumentException> { fixtureRender(mutateFfmpegDuringRender = true) }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: ffmpeg")
    }

    @Test fun `absolute preview ranges split into exact bounded chunks`() {
        for ((start, count, sizes) in listOf(
            Triple(0, 150, listOf(150)), Triple(47, 600, listOf(300, 300)),
            Triple(73, 900, listOf(300, 300, 300)),
        )) fixtureRender(startFrame = start, frameCount = count, expectedSizes = sizes)
    }

    @Test fun `missing and mutated receipt frames reject without removing earlier chunks`() {
        for (missing in listOf(true, false)) {
            assertFailsWith<IllegalArgumentException> { fixtureRender(missingFrame = missing, mutateFrame = !missing) }
        }
    }

    @Test fun `one deadline and staging budget stop later chunks but preserve earlier frames`() {
        for (deadline in listOf(true, false)) {
            val failure = assertFailsWith<VideoMediaProcessException> {
                fixtureRender(expireAfterFirstChunk = deadline, budgetAfterFirstChunk = !deadline)
            }
            assertEquals(if (deadline) VideoMediaProcessFailure.TIMED_OUT else VideoMediaProcessFailure.OUTPUT_LIMIT, failure.failure)
        }
    }

    private fun fixtureRender(canvasPathMismatch: Boolean = false, omitSourcePin: Boolean = false, wrongRequestHash: Boolean = false, cancelDuringValidation: Boolean = false, mutateSceneryDuringRender: Boolean = false, mutateSceneryDuringValidation: Boolean = false, mutateSceneryAfterValidation: Boolean = false, mutateCanvasDuringRender: Boolean = false, mutateManifestDuringRender: Boolean = false, mutateFfmpegDuringRender: Boolean = false,
        startFrame: Int = 100, frameCount: Int = 650, expectedSizes: List<Int> = listOf(300, 300, 50),
        missingFrame: Boolean = false, mutateFrame: Boolean = false, expireAfterFirstChunk: Boolean = false, budgetAfterFirstChunk: Boolean = false) {
        val root = Files.createTempDirectory("motion-render-test").toRealPath()
        fun file(name: String, text: String, executable: Boolean = false): VideoGenerationDependencyPin {
            val path = root.resolve(name); Files.createDirectories(path.parent); Files.writeString(path, text)
            if (executable) path.toFile().setExecutable(true)
            return VideoGenerationDependencyPin(name.replace(Regex("[^A-Za-z0-9_-]"), "-"), hash(path), path.toString())
        }
        val node = file("node", "node", true)
        val script = file("render.cjs", "renderer")
        val scenery = file("scenery.cjs", "scenery")
        val packageDir = Files.createDirectories(root.resolve("node_modules/@napi-rs/canvas"))
        Files.writeString(packageDir.resolve("index.js"), "module.exports = {};")
        val canvas = file("node_modules/@napi-rs/canvas/package.json", "{\"name\":\"@napi-rs/canvas\",\"version\":\"0.1.80\",\"main\":\"index.js\"}")
        val canvasArtifact = packageDir.resolve("index.js")
        val ffmpeg = file("ffmpeg", "ffmpeg", true)
        val sourceHash = "a".repeat(64)
        val descriptor = root.resolve("request.json")
        Files.writeString(descriptor, """{"schema":"melotrail-controlled-motion-request-v1","frameRange":{"startFrame":$startFrame,"frameCount":$frameCount},"pins":[{"sha256":"$sourceHash"}],"scenery":{"mode":"moving"}}""")
        var completedChunks = 0
        var currentCanonical = ""
        val token = VideoMediaProcessCancellation()
        val renderer = VideoMotionRenderer(onValidatedFrame = { frame ->
            if (frame == startFrame + 299L) completedChunks++
            if (cancelDuringValidation) token.cancel()
            if ((mutateSceneryDuringValidation && frame == startFrame + frameCount - 1L) || (mutateSceneryAfterValidation && frame == startFrame.toLong())) Files.writeString(java.nio.file.Path.of(scenery.ownedPath!!), "changed during validation")
        }, runProcess = { process, _ ->
            val args = process.arguments
            if (args.firstOrNull() == "-e") return@VideoMotionRenderer VideoMediaProcessResult(0, VideoMediaProcessOutput(
                if (args[1].contains("createRequire")) "{\"path\":\"${if (canvasPathMismatch) root.resolve("other-canvas/index.js") else packageDir.resolve("index.js")}\",\"manifest\":\"${canvas.ownedPath}\",\"version\":\"0.1.80\",\"artifacts\":[\"${packageDir.resolve("index.js")}\"]}" else currentCanonical,
                0, false), VideoMediaProcessOutput("", 0, false), Duration.ZERO, process.workingDirectory)
            if (args.firstOrNull() == "-version") return@VideoMotionRenderer VideoMediaProcessResult(0, VideoMediaProcessOutput("ffmpeg version pinned", 0, false), VideoMediaProcessOutput("", 0, false), Duration.ZERO, process.workingDirectory)
            assertEquals(ffmpeg.ownedPath, process.environment["MELOTRAIL_FFMPEG_PATH"])
            assertEquals(packageDir.parent.parent.toString(), process.environment["NODE_PATH"])
            val out = java.nio.file.Path.of(args[args.indexOf("--output") + 1]); Files.createDirectory(out)
            val requestPath = java.nio.file.Path.of(args[args.indexOf("--request") + 1])
            val text = Files.readString(requestPath)
            val chunk = Json.parseToJsonElement(text).jsonObject
            val canonicalObject = buildJsonObject {
                for (key in listOf("schema", "preparedScene", "seed", "fps", "canvas", "frameRange", "controls", "scenery", "initialState")) {
                    chunk[key]?.let { put(key, it) }
                }
            }
            // The descriptor is deliberately a minimal fixture; canonical-request shape mirrors the compositor.
            currentCanonical = hashBytes(canonicalObject.toString().toByteArray())
            val range = chunk["frameRange"]!!.jsonObject
            val start = range["startFrame"]!!.jsonPrimitive.int
            val count = range["frameCount"]!!.jsonPrimitive.int
            if (start > startFrame) assertEquals("state-${start - 1}", chunk["initialState"]!!.jsonObject["token"]!!.jsonPrimitive.content)
            val frameRecords = (0 until count).map { offset ->
                val frame = start + offset
                val name = "frame-${frame.toString().padStart(8, '0')}.png"
                Files.write(out.resolve(name), byteArrayOf(frame.toByte()))
                buildJsonObject { put("frame", frame); put("file", name); put("sha256", hash(out.resolve(name))) }
            }
            Files.writeString(out.resolve("render-receipt-${start.toString().padStart(8,'0')}-${count.toString().padStart(8,'0')}.json"), buildJsonObject {
                put("schema", "melotrail-controlled-motion-receipt-v1")
                put("tool", buildJsonObject { put("version", "1.1.0") })
                put("frameRange", buildJsonObject { put("startFrame", start); put("frameCount", count) })
                put("sourcePins", JsonArray(if (omitSourcePin) emptyList() else listOf(JsonPrimitive(sourceHash))))
                put("requestSha256", if (wrongRequestHash) "0".repeat(64) else currentCanonical)
                put("requestCanonicalSha256", if (wrongRequestHash) "0".repeat(64) else currentCanonical)
                put("scenery", buildJsonObject { put("finalState", buildJsonObject { put("token", "state-${start + count - 1}") }) })
                put("frames", JsonArray(frameRecords))
            }.toString())
            if (mutateSceneryDuringRender && start == startFrame) Files.writeString(java.nio.file.Path.of(scenery.ownedPath!!), "changed during rendering")
            if (mutateSceneryDuringValidation && start == startFrame + 600) Files.writeString(java.nio.file.Path.of(scenery.ownedPath!!), "changed during final validation")
            if (mutateCanvasDuringRender && start == startFrame) Files.writeString(canvasArtifact, "changed during rendering")
            if (mutateManifestDuringRender && start == startFrame) Files.writeString(java.nio.file.Path.of(canvas.ownedPath!!), "changed manifest")
            if (mutateFfmpegDuringRender && start == startFrame) Files.writeString(java.nio.file.Path.of(ffmpeg.ownedPath!!), "changed ffmpeg")
            if (missingFrame && start > startFrame) Files.delete(out.resolve("frame-${start.toString().padStart(8, '0')}.png"))
            if (mutateFrame && start > startFrame) Files.writeString(out.resolve("frame-${start.toString().padStart(8, '0')}.png"), "changed")
            VideoMediaProcessResult(0, VideoMediaProcessOutput("", 0, false), VideoMediaProcessOutput("", 0, false), Duration.ZERO, process.workingDirectory)
        })
        val result = try {
            renderer.render(VideoMotionRenderRequest(VideoMotionRuntime(node, script, scenery, canvas, canvasPins(java.nio.file.Path.of(canvas.ownedPath!!)), ffmpeg, "0.1.80"), root, descriptor, root, startFrame.toLong(), startFrame + frameCount.toLong(), Duration.ofMinutes(1),
                remainingTime = if (expireAfterFirstChunk) ({ if (completedChunks > 0) Duration.ZERO else Duration.ofMinutes(1) }) else null,
                checkBudget = { if (budgetAfterFirstChunk && completedChunks > 0) throw VideoMediaProcessException(VideoMediaProcessFailure.OUTPUT_LIMIT, "Staging full") }), token)
        } catch (error: Exception) {
            if (expireAfterFirstChunk || budgetAfterFirstChunk || missingFrame || mutateFrame) {
                val first = root.resolve("motion-$startFrame-0")
                assertTrue(Files.isRegularFile(first.resolve("frame-${startFrame.toString().padStart(8, '0')}.png")))
                assertTrue(Files.isRegularFile(first.resolve("render-receipt-${startFrame.toString().padStart(8, '0')}-${minOf(300, frameCount).toString().padStart(8, '0')}.json")))
            }
            throw error
        }
        assertEquals(expectedSizes.map(Int::toLong), result.invocations.map { it.endFrameExclusive - it.startFrame })
        assertEquals(expectedSizes.indices.map { startFrame.toLong() + expectedSizes.take(it).sum() }, result.invocations.map { it.startFrame })
        assertTrue(Files.isRegularFile(result.invocations.last().receipt))
    }

    @Test fun `rejects changed explicit tool pin before launching`() {
        val root = Files.createTempDirectory("motion-pin-test").toRealPath()
        val path = root.resolve("tool"); Files.writeString(path, "changed"); path.toFile().setExecutable(true)
        val pin = VideoGenerationDependencyPin("node", "0".repeat(64), path.toString())
        val renderer = VideoMotionRenderer { _, _ -> fail("must not launch") }
        assertFailsWith<IllegalArgumentException> { renderer.render(VideoMotionRenderRequest(VideoMotionRuntime(pin, pin.copy(id = "script"), pin.copy(id = "scenery"), pin.copy(id = "canvas"), listOf(pin.copy(id = "canvas-artifact")), pin.copy(id = "ffmpeg"), "0.1.80"), root, root.resolve("missing"), root, 0, 1, Duration.ofSeconds(1))) }
    }

    private fun canvasPins(manifest: java.nio.file.Path): List<VideoGenerationDependencyPin> {
        val dir = manifest.parent
        val native = dir.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node")
        return (listOf("index.js", "js-binding.js", "geometry.js", "load-image.js").mapNotNull { dir.resolve(it).takeIf(Files::exists) } +
            listOfNotNull(native.takeIf(Files::exists))).mapIndexed { index, path ->
            VideoGenerationDependencyPin("canvas-artifact-$index", hash(path), path.toString())
        }
    }

    @Test fun `rejects changed Canvas implementation bytes before any process launch`() {
        val root = Files.createTempDirectory("canvas-mutation").toRealPath()
        val dir = Files.createDirectories(root.resolve("node_modules/@napi-rs/canvas"))
        Files.writeString(dir.resolve("index.js"), "original implementation")
        val manifest = dir.resolve("package.json")
        Files.writeString(manifest, """{"name":"@napi-rs/canvas","version":"0.1.80","main":"index.js"}""")
        val nodePath = root.resolve("node"); Files.writeString(nodePath, "node"); nodePath.toFile().setExecutable(true)
        val script = root.resolve("render.cjs"); Files.writeString(script, "script")
        val scenery = root.resolve("scenery.cjs"); Files.writeString(scenery, "scenery")
        val ffmpeg = root.resolve("ffmpeg"); Files.writeString(ffmpeg, "ffmpeg"); ffmpeg.toFile().setExecutable(true)
        val pin = { id: String, path: java.nio.file.Path -> VideoGenerationDependencyPin(id, hash(path), path.toString()) }
        val runtime = VideoMotionRuntime(pin("node", nodePath), pin("script", script), pin("scenery", scenery), pin("manifest", manifest), canvasPins(manifest), pin("ffmpeg", ffmpeg), "0.1.80")
        Files.writeString(dir.resolve("index.js"), "changed implementation")
        val renderer = VideoMotionRenderer { _, _ -> fail("changed artifact must be rejected before execution") }
        assertFailsWith<IllegalArgumentException> { renderer.render(VideoMotionRenderRequest(runtime, root, root.resolve("missing"), root, 0, 1, Duration.ofSeconds(1))) }
    }

    @Test fun `rejects changed scenery module before any process launch`() {
        val root = Files.createTempDirectory("scenery-mutation").toRealPath()
        val node = root.resolve("node"); Files.writeString(node, "node"); node.toFile().setExecutable(true)
        val script = root.resolve("render.cjs"); Files.writeString(script, "renderer")
        val scenery = root.resolve("scenery.cjs"); Files.writeString(scenery, "original scenery")
        val canvas = root.resolve("node_modules/@napi-rs/canvas/package.json")
        Files.createDirectories(canvas.parent)
        Files.writeString(canvas.parent.resolve("index.js"), "module.exports = {}")
        Files.writeString(canvas, """{"name":"@napi-rs/canvas","version":"0.1.80","main":"index.js"}""")
        val ffmpeg = root.resolve("ffmpeg"); Files.writeString(ffmpeg, "ffmpeg"); ffmpeg.toFile().setExecutable(true)
        fun pin(id: String, path: java.nio.file.Path) = VideoGenerationDependencyPin(id, hash(path), path.toString())
        val runtime = VideoMotionRuntime(pin("node", node), pin("compositor", script), pin("scenery", scenery), pin("canvas", canvas), canvasPins(canvas), pin("ffmpeg", ffmpeg), "0.1.80")
        Files.writeString(scenery, "changed scenery")
        val renderer = VideoMotionRenderer { _, _ -> fail("changed scenery must be rejected before execution") }
        val error = assertFailsWith<IllegalArgumentException> {
            renderer.render(VideoMotionRenderRequest(runtime, root, root.resolve("missing"), root, 0, 1, Duration.ofSeconds(1)))
        }
        assertContains(error.message.orEmpty(), "Pinned runtime digest changed: scenery")
    }

    private fun hash(path: java.nio.file.Path): String = hashBytes(Files.readAllBytes(path))
    private fun hashBytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
