package app.melotrail.video.adapter

import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import app.melotrail.video.domain.VideoComfyReferenceInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoGenerationDependencyPin
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** The bundled short-shot API graph is a fixed execution contract, not a caller-supplied template. */
object ComfyShortI2VBinding {
    const val WORKFLOW_ID = "comfyui-short-shot-v1"
    const val IMAGE_ID = "composed-scene-image"
    private const val GRAPH_SHA256 = "c1d27b28313ba882659c0a7f52d94e666cabce45bfa5ce0fa3d256cc58b79bc7"

    val workflow: VideoComfyWorkflowRequest get() = VideoComfyWorkflowRequest(
        workflowDependencyId = WORKFLOW_ID,
        promptInput = VideoComfyInputSlot("20", "value"),
        referenceInputs = listOf(VideoComfyReferenceInput(IMAGE_ID, VideoComfyInputSlot("4", "image"), "composed-scene.png")),
        widthInput = VideoComfyInputSlot("21", "value"),
        heightInput = VideoComfyInputSlot("22", "value"),
        frameCountInput = VideoComfyInputSlot("23", "value"),
        framesPerSecondInput = VideoComfyInputSlot("24", "value"),
        output = VideoComfyOutputBinding("16", setOf("mp4")),
    )

    /** The host and application supply owned, digest-pinned copies; neither supplies graph slots. */
    fun input(
        prompt: String,
        graph: VideoGenerationDependencyPin,
        image: VideoGenerationDependencyPin,
        durationMillis: Long,
        width: Int,
        height: Int,
        framesPerSecond: Int,
    ): VideoClipGenerationInput = VideoClipGenerationInput(
        prompt, listOf(graph, image), durationMillis, width, height, framesPerSecond, workflow,
    ).also(::verify)

    /** Also used at the production submission boundary, before a slot or transport is acquired. */
    fun verify(input: VideoClipGenerationInput) {
        require(input.comfyWorkflow == workflow) { "Pinned short-I2V graph slots or output binding changed." }
        require(input.dependencyPins.size == 2 && input.dependencyPins.map { it.id }.toSet() == setOf(WORKFLOW_ID, IMAGE_ID)) {
            "Pinned short-I2V graph and image dependencies are required."
        }
        val graph = input.dependencyPins.single { it.id == WORKFLOW_ID }
        require(graph.sha256 == GRAPH_SHA256 && digest(bundledGraph()) == GRAPH_SHA256) {
            "Pinned short-I2V bundled graph or graph digest changed."
        }
        require(digest(ownedFile(graph, "graph")) == GRAPH_SHA256) { "Pinned short-I2V graph bytes changed." }
        val image = input.dependencyPins.single { it.id == IMAGE_ID }
        require(digest(ownedFile(image, "image")) == image.sha256) { "Pinned short-I2V image bytes changed." }
    }

    private fun bundledGraph(): ByteArray = checkNotNull(
        ComfyShortI2VBinding::class.java.getResourceAsStream("/video/comfyui/short-shot-api.json"),
    ) { "Bundled short-I2V graph is missing." }.use { it.readBytes() }

    private fun ownedFile(pin: VideoGenerationDependencyPin, label: String): Path {
        val path = Path.of(requireNotNull(pin.ownedPath) { "Pinned short-I2V $label path is missing." })
        require(path.isAbsolute && path.none { it.toString() in setOf(".", "..") } &&
            Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.toRealPath() == path
        ) { "Pinned short-I2V $label path is missing or unsafe." }
        return path
    }

    private fun digest(path: Path): String = Files.newInputStream(path).use { stream ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            md.update(buffer, 0, count)
        }
        digestHex(md.digest())
    }

    private fun digest(bytes: ByteArray): String = digestHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun digestHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
