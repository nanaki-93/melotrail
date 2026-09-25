package app.melotrail.video.adapter

import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import app.melotrail.video.domain.VideoComfyReferenceInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoModelRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/** The bundled short-shot API graph is a fixed execution contract, not a caller-supplied template. */
object ComfyShortI2VBinding {
    const val WORKFLOW_ID = "comfyui-short-shot-v1"
    const val IMAGE_ID = "composed-scene-image"
    private const val GRAPH_SHA256 = "c1d27b28313ba882659c0a7f52d94e666cabce45bfa5ce0fa3d256cc58b79bc7"
    private const val PROFILE_SHA256 = "f810b08dfde88e80fc753887fd568cb71f30594a6196882b0e3a620026641b06"
    private const val PROFILE_ID = "flat-runtime-profile"
    // These four loader slots are the only models consumed by the pinned graph; the
    // upscaler in the installation profile is not loaded by this short-shot graph.
    private val modelSlots = listOf(
        ModelSlot("1", "unet_name", "diffusion_models/ltx-2.3-22b-distilled-1.1-Q4_K_M.gguf", "ltx-2-3-distilled-q4", "22b-distilled-1.1-Q4_K_M"),
        ModelSlot("2", "clip_name1", "text_encoders/gemma-3-12b-it-qat-UD-Q4_K_XL.gguf", "gemma-3-12b-it-qat", "Q4_K_XL"),
        ModelSlot("2", "clip_name2", "text_encoders/ltx-2.3-22b-distilled_embeddings_connectors.safetensors", "ltx-2-3-connectors", "distilled-1.1"),
        ModelSlot("3", "vae_name", "vae/ltx-2.3-22b-distilled_video_vae.safetensors", "ltx-2-3-video-vae", "distilled-1.1"),
    )
    private data class ModelSlot(val node: String, val input: String, val path: String, val id: String, val version: String)

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
        primaryPrompt: String? = null,
        guidanceSha256: String? = null,
        guidelineSourceSha256: String? = null,
        runtimePins: List<VideoGenerationDependencyPin> = emptyList(),
    ): VideoClipGenerationInput = VideoClipGenerationInput(
        prompt, listOf(graph, image) + (guidanceSha256?.let { listOf(VideoGenerationDependencyPin("flat-guidance", it)) } ?: emptyList()) +
            (guidelineSourceSha256?.let { listOf(VideoGenerationDependencyPin("flat-guideline-source", it)) } ?: emptyList()) + runtimePins,
        durationMillis, width, height, framesPerSecond, workflow, primaryPrompt,
    ).also(::verify)

    /** Read-only graph/runtime readiness check; no image, native process or transport is needed. */
    fun verifyConfiguration(graph: VideoGenerationDependencyPin, runtimePins: List<VideoGenerationDependencyPin>,
                            models: List<VideoModelRequirement>) {
        require(runtimePins.map { it.id } == listOf(PROFILE_ID) && runtimePins.single().sha256 == PROFILE_SHA256) {
            "Pinned short-I2V runtime profile does not match the selected graph."
        }
        verifyGraph(graph)
        val profileBytes = bundledProfile()
        require(digest(profileBytes) == PROFILE_SHA256 && digest(ownedFile(runtimePins.single(), "runtime profile")) == PROFILE_SHA256) {
            "Pinned short-I2V runtime profile bytes changed."
        }
        val profile = Json.decodeFromString<LocalVideoRuntimeProfile>(profileBytes.decodeToString())
        require(profile.profileId == "comfyui-ltx23-v1") { "Pinned short-I2V runtime profile is unsupported." }
        val graphNodes = Json.parseToJsonElement(bundledGraph().decodeToString()).jsonObject
        val expected = modelSlots.map { slot ->
            val graphName = graphNodes.getValue(slot.node).jsonObject.getValue("inputs").jsonObject.getValue(slot.input).jsonPrimitive.content
            require(graphName == slot.path.substringAfterLast('/')) { "Pinned short-I2V graph model slot ${slot.node}.${slot.input} changed." }
            val model = profile.models.singleOrNull { it.relativePath == slot.path }
                ?: throw IllegalArgumentException("Pinned short-I2V runtime profile lacks graph model ${slot.path}.")
            VideoModelRequirement(slot.id, slot.version, model.sha256)
        }
        require(models.size == expected.size && models.toSet() == expected.toSet()) {
            "Selected short-I2V models do not match the pinned graph and runtime profile."
        }
    }

    private fun bundledProfile(): ByteArray = checkNotNull(
        ComfyShortI2VBinding::class.java.getResourceAsStream("/video/comfyui/runtime-profile.json"),
    ) { "Bundled short-I2V runtime profile is missing." }.use { it.readBytes() }

    private fun verifyGraph(graph: VideoGenerationDependencyPin) {
        require(graph.id == WORKFLOW_ID && graph.sha256 == GRAPH_SHA256 && digest(bundledGraph()) == GRAPH_SHA256) {
            "Pinned short-I2V bundled graph or graph digest changed."
        }
        require(digest(ownedFile(graph, "graph")) == GRAPH_SHA256) { "Pinned short-I2V graph bytes changed." }
    }

    /** Also used at the production submission boundary, before a slot or transport is acquired. */
    fun verify(input: VideoClipGenerationInput) {
        require(input.comfyWorkflow == workflow) { "Pinned short-I2V graph slots or output binding changed." }
        val extras = input.dependencyPins.filter { it.id !in setOf(WORKFLOW_ID, IMAGE_ID) }
        require(input.dependencyPins.map { it.id }.toSet().size == input.dependencyPins.size &&
            input.dependencyPins.count { it.id == WORKFLOW_ID } == 1 && input.dependencyPins.count { it.id == IMAGE_ID } == 1 &&
            (if (input.primaryPrompt == null) extras.isEmpty() else
                extras.map { it.id }.toSet() == setOf("flat-guidance", "flat-guideline-source", PROFILE_ID) &&
                    extras.single { it.id == "flat-guidance" }.ownedPath == null &&
                    extras.single { it.id == "flat-guideline-source" }.ownedPath == null &&
                    extras.single { it.id == PROFILE_ID }.ownedPath != null)) {
            "Pinned short-I2V graph, image, guidance and runtime dependencies are required."
        }
        if (input.primaryPrompt != null) {
            require(input.prompt.startsWith("${input.primaryPrompt}\n\n")) { "Flat primary prompt is not preserved separately from backend guidance." }
            require(input.dependencyPins.single { it.id == "flat-guidance" }.sha256 == digest(
                input.prompt.toByteArray(Charsets.UTF_8))) { "Flat guidance changed." }
            val guidelines = checkNotNull(ComfyShortI2VBinding::class.java.getResourceAsStream(
                "/video/video-generation-guidelines.json")) { "Bundled motion guidelines are missing." }.use { it.readBytes() }
            require(input.dependencyPins.single { it.id == "flat-guideline-source" }.sha256 == digest(guidelines)) {
                "Pinned flat motion guidelines changed."
            }
        }
        val graph = input.dependencyPins.single { it.id == WORKFLOW_ID }
        if (input.primaryPrompt != null) {
            // Model requirements live on the enclosing durable job; application admission
            // verifies them together with these pins before constructing that request.
            require(extras.count { it.id == PROFILE_ID } == 1) { "Pinned short-I2V runtime profile is required." }
            verifyGraph(graph)
            val profile = extras.single { it.id == PROFILE_ID }
            require(profile.sha256 == PROFILE_SHA256 && digest(bundledProfile()) == PROFILE_SHA256 &&
                digest(ownedFile(profile, "runtime profile")) == PROFILE_SHA256) { "Pinned short-I2V runtime profile changed." }
        } else verifyGraph(graph) // Older host jobs have no application runtime pins.
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
