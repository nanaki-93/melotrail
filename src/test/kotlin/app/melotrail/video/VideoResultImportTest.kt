package app.melotrail.video

import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.domain.VideoGenerationOutput
import app.melotrail.video.domain.VideoProject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class VideoResultImportTest {
    @Test fun `backend publication roots are disjoint and pinned bytes are required`() {
        val base = Files.createTempDirectory("take-roots-").toRealPath()
        val midi = Files.createDirectory(base.resolve("midi"))
        val project = base.resolve("video")
        val store = VideoProjectStore(listOf(midi))
        store.create(project, VideoProject("video", "Video", Instant.now().toString()))
        val controlled = Files.createDirectory(project.resolve("controlled-outputs"))
        val comfy = Files.createDirectory(base.resolve("comfy-publication"))
        val controlledFile = put(controlled, "attempt/preview.mp4", "controlled")
        val comfyFile = put(comfy, "generated/request/attempt/preview.mp4", "comfy")
        val importer = VideoResultImport(store, VideoMediaProbe(), controlledOutputRoot = controlled,
            comfyOutputRoot = comfy, protectedMidiRoots = listOf(midi))
        val controlPin = pin("attempt/preview.mp4", controlledFile)
        val comfyPin = pin("generated/request/attempt/preview.mp4", comfyFile)
        assertEquals(controlledFile, importer.resolvePinnedOutput(project, "controlled-local", controlPin))
        assertEquals(comfyFile, importer.resolvePinnedOutput(project, "comfyui-local", comfyPin))
        assertFails { importer.resolvePinnedOutput(project, "comfyui-local", controlPin) }
        assertFails { importer.resolvePinnedOutput(project, "controlled-local", comfyPin) }
        assertFails { importer.resolvePinnedOutput(project, "unknown-backend", controlPin) }
        assertFails { importer.resolvePinnedOutput(project, "comfyui-local", comfyPin.copy(byteCount = 1)) }
        assertFails { importer.resolvePinnedOutput(project, "comfyui-local", comfyPin.copy(sha256 = "0".repeat(64))) }
        assertFails { importer.resolvePinnedOutput(project, "comfyui-local", comfyPin.copy(relativePath = "../video/controlled-outputs/attempt/preview.mp4")) }
        assertFails { importer.resolvePinnedOutput(project, "controlled-local", controlPin.copy(relativePath = "attempt/../attempt/preview.mp4")) }
        assertEquals(0, store.open(project).revision)
    }

    @Test fun `symlinked parents files and protected root aliases never resolve`() {
        val base = Files.createTempDirectory("take-unsafe-").toRealPath()
        val midi = Files.createDirectory(base.resolve("midi"))
        val project = base.resolve("video")
        val store = VideoProjectStore(listOf(midi))
        store.create(project, VideoProject("video", "Video", Instant.now().toString()))
        val controlled = Files.createDirectory(project.resolve("controlled-outputs"))
        val comfy = Files.createDirectory(base.resolve("comfy"))
        val outside = put(midi, "private.mp4", "protected")
        val safe = put(comfy, "safe.mp4", "safe")
        Files.createSymbolicLink(comfy.resolve("linked.mp4"), outside)
        Files.createSymbolicLink(comfy.resolve("folder"), midi)
        Files.createSymbolicLink(base.resolve("midi-alias"), midi)
        Files.createSymbolicLink(base.resolve("comfy-alias"), comfy)
        Files.createDirectory(comfy.resolve("directory.mp4"))
        fun importer(root: Path) = VideoResultImport(store, VideoMediaProbe(), controlledOutputRoot = controlled,
            comfyOutputRoot = root, protectedMidiRoots = listOf(base.resolve("midi-alias")))
        val normal = importer(comfy)
        assertFails { normal.resolvePinnedOutput(project, "comfyui-local", pin("linked.mp4", outside)) }
        assertFails { normal.resolvePinnedOutput(project, "comfyui-local", pin("folder/private.mp4", outside)) }
        assertFails { normal.resolvePinnedOutput(project, "comfyui-local", pin("directory.mp4", safe)) }
        assertFails { importer(base.resolve("midi-alias")).resolvePinnedOutput(project, "comfyui-local", pin("private.mp4", outside)) }
        assertFails { importer(base.resolve("comfy-alias")).resolvePinnedOutput(project, "comfyui-local", pin("safe.mp4", safe)) }
        assertFails { importer(project).resolvePinnedOutput(project, "comfyui-local", pin("safe.mp4", safe)) }
        assertEquals(0, store.open(project).revision)
    }

    private fun put(root: Path, relative: String, text: String): Path {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return path
    }

    private fun pin(relative: String, path: Path): VideoGenerationOutput = VideoGenerationOutput(
        "output", "attempt", "backend-output", Instant.now().toString(), relative,
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) },
        Files.size(path),
    )
}
