package app.melotrail.video.adapter

import app.melotrail.video.application.VideoAvailableModel
import app.melotrail.video.application.VideoBackendAvailability
import app.melotrail.video.application.VideoBackendAvailabilityStatus
import app.melotrail.video.application.VideoBackendCancellation
import app.melotrail.video.application.VideoBackendObservation
import app.melotrail.video.application.VideoBackendOutput
import app.melotrail.video.application.VideoBackendSubmission
import app.melotrail.video.application.VideoBackendSubmissionCommand
import app.melotrail.video.application.VideoGenerationBackendPort
import app.melotrail.video.application.VideoGenerationInputKind
import app.melotrail.video.application.VideoOwnedBackendAttempt
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoGenerationInput
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoKeyframeGenerationInput
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Recoverable V16 backend for one exact V17c-owned ComfyUI session. */
class LocalVideoBackend private constructor(
    private val session: ComfyVideoSession,
    private val client: ComfyVideoApi,
    private val acquireSlot: (String) -> Boolean,
    private val releaseSlot: (String) -> Unit,
    private val requestLookup: (String) -> VideoGenerationJobRequest?,
    private val ownedInputRoots: List<Path>,
    private val publicationRoot: Path,
    private val availableModels: List<VideoAvailableModel>,
    private val ready: () -> Boolean,
    private val clock: Clock,
) : VideoGenerationBackendPort {
    constructor(
        runtime: ComfyVideoRuntime,
        session: ComfyVideoSession,
        requestLookup: (String) -> VideoGenerationJobRequest?,
        ownedInputRoots: List<Path>,
        publicationRoot: Path,
        availableModels: List<VideoAvailableModel>,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        session = session,
        client = ComfyVideoClient(runtime.connection(session)),
        acquireSlot = { runtime.acquireInferenceSlot(session, it).claimSubmission() },
        releaseSlot = { runtime.releaseInferenceSlot(session, it) },
        requestLookup = requestLookup,
        ownedInputRoots = ownedInputRoots,
        publicationRoot = publicationRoot,
        availableModels = availableModels,
        ready = { runtime.state == ComfyVideoRuntimeState.READY && kotlin.runCatching { runtime.connection(session) }.isSuccess },
        clock = clock,
    )

    internal constructor(
        session: ComfyVideoSession,
        client: ComfyVideoApi,
        acquireSlot: (String) -> Boolean,
        releaseSlot: (String) -> Unit,
        requestLookup: (String) -> VideoGenerationJobRequest?,
        ownedInputRoots: List<Path>,
        publicationRoot: Path,
        availableModels: List<VideoAvailableModel>,
        ready: () -> Boolean = { true },
        clock: Clock = Clock.systemUTC(),
        @Suppress("UNUSED_PARAMETER") fixture: Unit = Unit,
    ) : this(session, client, acquireSlot, releaseSlot, requestLookup, ownedInputRoots, publicationRoot, availableModels, ready, clock)

    override val backendId: String = BACKEND_ID

    override fun availability(): VideoBackendAvailability {
        val isReady = ready()
        return VideoBackendAvailability(
            backendId = backendId,
            status = if (isReady) VideoBackendAvailabilityStatus.AVAILABLE else VideoBackendAvailabilityStatus.OFFLINE,
            checkedAt = Instant.now(clock).toString(),
            supportedInputs = if (isReady) setOf(VideoGenerationInputKind.KEYFRAME, VideoGenerationInputKind.VIDEO) else emptySet(),
            availableModels = if (isReady) availableModels else emptyList(),
            detail = if (isReady) "Verified owned ComfyUI session is ready." else "Verified owned ComfyUI session is unavailable or stale.",
        )
    }

    override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
        val invalid = validateCommand(command)
        if (invalid != null) return VideoBackendSubmission.Rejected(invalid, retryable = false)
        val workflowBinding = command.input.comfyWorkflow
            ?: return VideoBackendSubmission.Rejected("A persisted typed ComfyUI workflow binding is required.", retryable = false)
        val workflow = try {
            loadWorkflow(command.input, workflowBinding)
        } catch (error: Exception) {
            return VideoBackendSubmission.Rejected(usefulLocal(error), retryable = false)
        }
        val identity = stableIdentity(command.ownedAttempt)
        val request = try {
            clientRequest(command, workflow, workflowBinding, identity)
        } catch (error: Exception) {
            return VideoBackendSubmission.Rejected(usefulLocal(error), retryable = false)
        }
        val claimed = try {
            acquireSlot(identity.promptId)
        } catch (error: Exception) {
            return VideoBackendSubmission.Rejected("Could not acquire the owned ComfyUI inference slot: ${usefulLocal(error)}", retryable = true)
        }
        if (!claimed) {
            return VideoBackendSubmission.Uncertain("This persisted attempt already claimed a ComfyUI submission slot.")
        }
        return when (val result = client.submit(request)) {
            is ComfyClientSubmissionResult.Accepted -> VideoBackendSubmission.Accepted(result.promptId)
            is ComfyClientSubmissionResult.Uncertain -> VideoBackendSubmission.Uncertain(result.reason)
            is ComfyClientSubmissionResult.Rejected -> {
                releaseSlot(identity.promptId)
                VideoBackendSubmission.Rejected(result.reason, retryable = false)
            }
        }
    }

    override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation {
        if (ownedAttempt.backendId != backendId) return VideoBackendObservation.Unknown("Backend ownership does not match ComfyUI.")
        if (!ready()) return VideoBackendObservation.Unknown("Verified owned ComfyUI session is unavailable or stale.")
        val request = requestLookup(ownedAttempt.requestId)
            ?: return VideoBackendObservation.Unknown("Persisted request bindings are unavailable.")
        val binding = request.input.comfyWorkflow
            ?: return VideoBackendObservation.Unknown("Persisted ComfyUI workflow bindings are unavailable.")
        val identity = stableIdentity(ownedAttempt)
        if (ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != identity.promptId) {
            return VideoBackendObservation.Unknown("Persisted provider identity does not match the owned attempt.")
        }
        return when (val observation = client.observe(identity.promptId, identity.clientId, binding.output)) {
            is ComfyClientObservation.Running -> VideoBackendObservation.Running(identity.promptId, observation.progressPercent)
            ComfyClientObservation.Cancelled -> {
                releaseSlot(identity.promptId)
                VideoBackendObservation.Cancelled(identity.promptId)
            }
            is ComfyClientObservation.Failed -> {
                releaseSlot(identity.promptId)
                VideoBackendObservation.Failed(identity.promptId, observation.reason, retryable = true)
            }
            is ComfyClientObservation.Unknown -> VideoBackendObservation.Unknown(observation.detail)
            is ComfyClientObservation.Completed -> {
                val output = try {
                    validateAndPublish(request, ownedAttempt, identity.promptId, observation.output)
                } catch (error: Exception) {
                    releaseSlot(identity.promptId)
                    return VideoBackendObservation.Failed(identity.promptId, usefulLocal(error), retryable = false)
                }
                releaseSlot(identity.promptId)
                VideoBackendObservation.Completed(identity.promptId, output)
            }
        }
    }

    override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation {
        if (ownedAttempt.backendId != backendId) return VideoBackendCancellation.Unknown("Backend ownership does not match ComfyUI.")
        if (!ready()) return VideoBackendCancellation.Unknown("Verified owned ComfyUI session is unavailable or stale.")
        val identity = stableIdentity(ownedAttempt)
        if (ownedAttempt.providerWorkId != null && ownedAttempt.providerWorkId != identity.promptId) {
            return VideoBackendCancellation.Unknown("Persisted provider identity does not match the owned attempt.")
        }
        val result = client.cancel(identity.promptId, acknowledged = ownedAttempt.providerWorkId != null)
        if (result is ComfyClientCancellation.ConfirmedStopped) releaseSlot(identity.promptId)
        return when (result) {
            ComfyClientCancellation.Requested -> VideoBackendCancellation.Requested
            ComfyClientCancellation.ConfirmedStopped -> VideoBackendCancellation.ConfirmedStopped
            is ComfyClientCancellation.Unknown -> VideoBackendCancellation.Unknown(result.detail)
        }
    }

    private fun validateCommand(command: VideoBackendSubmissionCommand): String? {
        if (command.ownedAttempt.backendId != backendId) return "Attempt backend does not match ComfyUI."
        if (command.input is VideoControlledMotionGenerationInput) return "Controlled compositor requests are not supported by ComfyUI; use the dedicated media stage."
        if (command.execution !is VideoLocalExecutionPolicy) return "ComfyUI local backend requires a local execution policy."
        val binding = command.input.comfyWorkflow ?: return "A persisted typed ComfyUI workflow binding is required."
        val expected = videoRequestFingerprint(backendId, command.input, command.modelRequirements)
        if (expected != command.ownedAttempt.requestFingerprint) return "Request fingerprint does not include the exact executable bindings and dependency pins."
        val pins = command.input.dependencyPins.associateBy { it.id }
        val consumed = listOf(binding.workflowDependencyId) + binding.referenceInputs.map { it.dependencyId }
        if (consumed.any { pins[it]?.ownedPath == null }) return "Every consumed ComfyUI input must have a persisted owned path and digest."
        if (!ready()) return "Verified owned ComfyUI session is unavailable or stale."
        return null
    }

    private fun loadWorkflow(input: VideoGenerationInput, binding: VideoComfyWorkflowRequest): JsonObject {
        val pin = input.dependencyPins.single { it.id == binding.workflowDependencyId }
        val path = verifyOwnedFile(pin.ownedPath, pin.sha256, "workflow")
        if (Files.size(path) > MAX_WORKFLOW_BYTES) throw IllegalArgumentException("ComfyUI workflow exceeds $MAX_WORKFLOW_BYTES bytes.")
        return try { JSON.parseToJsonElement(Files.readString(path)).jsonObject }
        catch (error: Exception) { throw IllegalArgumentException("ComfyUI workflow is not an API-format JSON object.", error) }
    }

    private fun clientRequest(
        command: VideoBackendSubmissionCommand,
        workflow: JsonObject,
        binding: VideoComfyWorkflowRequest,
        identity: StableIdentity,
    ): ComfyClientSubmission {
        val pins = command.input.dependencyPins.associateBy { it.id }
        val uploads = binding.referenceInputs.map { reference ->
            val pin = requireNotNull(pins[reference.dependencyId])
            val source = verifyOwnedFile(pin.ownedPath, pin.sha256, "reference '${pin.id}'")
            require(Files.size(source) <= MAX_REFERENCE_BYTES) {
                "Consumed reference '${pin.id}' exceeds $MAX_REFERENCE_BYTES bytes."
            }
            ComfyUploadBinding(
                source = source,
                uploadFileName = reference.uploadFileName,
                slot = reference.slot,
            )
        }
        val scalar = linkedMapOf(binding.promptInput to JsonPrimitive(command.input.prompt))
        when (val input = command.input) {
            is VideoKeyframeGenerationInput -> {
                binding.widthInput?.let { scalar[it] = JsonPrimitive(input.width) }
                binding.heightInput?.let { scalar[it] = JsonPrimitive(input.height) }
                if (binding.frameCountInput != null || binding.framesPerSecondInput != null) {
                    throw IllegalArgumentException("Keyframe workflow cannot bind frame count or frame rate.")
                }
            }
            is VideoClipGenerationInput -> {
                binding.widthInput?.let { scalar[it] = JsonPrimitive(input.width) }
                binding.heightInput?.let { scalar[it] = JsonPrimitive(input.height) }
                binding.framesPerSecondInput?.let { scalar[it] = JsonPrimitive(input.framesPerSecond) }
                binding.frameCountInput?.let {
                    val frameMillis = Math.multiplyExact(input.durationMillis, input.framesPerSecond.toLong())
                    val frames = frameMillis / 1_000L + if (frameMillis % 1_000L == 0L) 0L else 1L
                    require(frames in 1..Int.MAX_VALUE.toLong()) { "Requested frame count is outside ComfyUI bounds." }
                    scalar[it] = JsonPrimitive(frames.toInt())
                }
            }
            is VideoControlledMotionGenerationInput -> throw IllegalArgumentException(
                "Controlled compositor requests cannot be submitted to ComfyUI."
            )
        }
        return ComfyClientSubmission(
            promptId = identity.promptId,
            clientId = identity.clientId,
            workflow = workflow,
            scalarInputs = scalar,
            uploads = uploads,
            output = binding.output,
            requestId = command.ownedAttempt.requestId,
            requestFingerprint = command.ownedAttempt.requestFingerprint,
            attemptId = command.ownedAttempt.attemptId,
            ownershipToken = command.ownedAttempt.ownershipToken,
        )
    }

    private fun validateAndPublish(
        request: VideoGenerationJobRequest,
        owned: VideoOwnedBackendAttempt,
        promptId: String,
        output: ComfyApiOutput,
    ): VideoBackendOutput {
        val binding = requireNotNull(request.input.comfyWorkflow)
        require(output.nodeId == binding.output.nodeId && output.type == "output") { "ComfyUI output identity does not match the persisted binding." }
        require(output.fileName.substringAfterLast('.', "") in binding.output.allowedExtensions) { "ComfyUI output extension is unsupported." }
        val backendOutputId = "$promptId:${output.nodeId}:${output.subfolder}/${output.fileName}"
        require(backendOutputId.length <= 512) { "ComfyUI output identity exceeds the durable job bound." }
        val source = resolveOutput(output)
        val sourceSize = Files.size(source)
        require(sourceSize > 0L) { "ComfyUI output file is empty." }
        val limit = (request.execution as? VideoLocalExecutionPolicy)?.diskLimitBytes
        require(limit == null || sourceSize <= limit) { "ComfyUI output exceeds the admitted disk bound." }
        val sourceDigest = sha256(source)

        val root = requireOwnedDirectory(publicationRoot, "publication root")
        val relative = Path.of("generated", request.id, owned.attemptId, "$promptId-${output.fileName}")
        var parent = root
        relative.parent.forEach { component ->
            parent = parent.resolve(component)
            if (Files.exists(parent, NOFOLLOW_LINKS)) {
                require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)) { "Immutable output parent is unsafe." }
            } else Files.createDirectory(parent)
        }
        val destination = root.resolve(relative)
        var temporary: Path? = null
        try {
            temporary = Files.createTempFile(parent, ".comfy-output-", ".part")
            Files.copy(source, temporary, REPLACE_EXISTING)
            require(Files.size(temporary) == sourceSize && sha256(temporary) == sourceDigest && sha256(source) == sourceDigest) {
                "ComfyUI output changed while staging publication."
            }
            // Publish complete staged bytes with an exclusive directory entry, as the video
            // stores do. ATOMIC_MOVE may replace an existing result; unsupported links fail closed.
            Files.createLink(destination, temporary)
        } catch (_: FileAlreadyExistsException) {
            // A recovery may republish only the exact same immutable bytes.
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
        require(Files.isRegularFile(destination, NOFOLLOW_LINKS) && !Files.isSymbolicLink(destination)) { "Published ComfyUI output is unsafe." }
        require(Files.size(destination) == sourceSize && sha256(destination) == sourceDigest && sha256(source) == sourceDigest) {
            "ComfyUI output changed while publishing."
        }
        return VideoBackendOutput(
            backendOutputId = backendOutputId,
            relativePath = relative.joinToString("/") { it.toString() },
            sha256 = sourceDigest,
            byteCount = sourceSize,
        )
    }

    private fun resolveOutput(output: ComfyApiOutput): Path {
        val root = requireOwnedDirectory(session.outputDirectory, "ComfyUI output directory")
        val subfolder = output.subfolder.takeIf { it.isNotEmpty() }?.let(Path::of)
        require(subfolder == null || !subfolder.isAbsolute && subfolder.none { it.toString() in setOf(".", "..") }) {
            "ComfyUI output subfolder is unsafe."
        }
        val candidate = (subfolder?.let(root::resolve) ?: root).resolve(output.fileName).normalize()
        require(candidate.startsWith(root) && Files.isRegularFile(candidate, NOFOLLOW_LINKS) && !Files.isSymbolicLink(candidate)) {
            "ComfyUI output file is missing or unsafe."
        }
        require(candidate.toRealPath() == candidate && candidate.startsWith(root)) { "ComfyUI output escaped its owned directory." }
        return candidate
    }

    private fun verifyOwnedFile(pathText: String?, expectedSha256: String, label: String): Path {
        val raw = try { Path.of(requireNotNull(pathText)) }
        catch (error: Exception) { throw IllegalArgumentException("Persisted $label path is invalid.", error) }
        require(raw.isAbsolute && raw.none { it.toString() in setOf(".", "..") }) { "Persisted $label path must be absolute without traversal." }
        val path = raw.normalize()
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) { "Persisted $label is missing or unsafe." }
        require(path.toRealPath() == path && sha256(path) == expectedSha256) { "Persisted $label digest or resolved identity changed." }
        require(ownedInputRoots.isNotEmpty() && ownedInputRoots.any { root -> path.startsWith(requireOwnedDirectory(root, "owned input root")) }) {
            "Persisted $label is outside the configured owned input roots."
        }
        return path
    }

    private fun requireOwnedDirectory(path: Path, label: String): Path {
        val normalized = path.toAbsolutePath().normalize()
        require(Files.isDirectory(normalized, NOFOLLOW_LINKS) && !Files.isSymbolicLink(normalized) && normalized.toRealPath() == normalized) {
            "$label is missing or unsafe."
        }
        return normalized
    }

    private fun stableIdentity(owned: VideoOwnedBackendAttempt): StableIdentity {
        val material = listOf(owned.requestId, owned.requestFingerprint, owned.attemptId, owned.ownershipToken, owned.backendId).joinToString("\u0000")
        val uuid = UUID.nameUUIDFromBytes(material.toByteArray(Charsets.UTF_8)).toString()
        return StableIdentity(uuid, "mt-$uuid")
    }

    private data class StableIdentity(val promptId: String, val clientId: String)

    companion object {
        const val BACKEND_ID = "comfyui-local"
        private const val MAX_WORKFLOW_BYTES = 4L * 1024L * 1024L
        private const val MAX_REFERENCE_BYTES = 64L * 1024L * 1024L
        private val JSON = Json { ignoreUnknownKeys = false }
    }
}

/** Canonical request identity binds all executable slots, pins, dimensions, prompt, and models. */
fun comfyRequestFingerprint(
    backendId: String,
    input: VideoGenerationInput,
    models: List<app.melotrail.video.domain.VideoModelRequirement>,
): String {
    require(input.comfyWorkflow != null) { "ComfyUI workflow binding is required" }
    return videoRequestFingerprint(backendId, input, models)
}

/** Canonical identity for either ComfyUI or controlled compositor requests. */
fun videoRequestFingerprint(
    backendId: String,
    input: VideoGenerationInput,
    models: List<app.melotrail.video.domain.VideoModelRequirement>,
): String {
    val workflow = input.comfyWorkflow
    if (input is VideoControlledMotionGenerationInput) {
        return controlledMotionRequestFingerprint(backendId, input, models)
    }
    val binding = requireNotNull(workflow) { "ComfyUI workflow binding is required" }
    val text = buildString {
        append("melotrail-comfy-request-v1\n").append(backendId).append('\n').append(input.prompt).append('\n')
        input.dependencyPins.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.sha256).append(':').append(it.ownedPath).append('\n') }
        models.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.version).append(':').append(it.sha256).append('\n') }
        append("workflow=").append(workflow.workflowDependencyId).append('\n')
        append("prompt=").append(workflow.promptInput.nodeId).append('.').append(workflow.promptInput.inputName).append('\n')
        workflow.referenceInputs.forEach { reference ->
            append("reference=").append(reference.dependencyId).append(':')
                .append(reference.slot.nodeId).append('.').append(reference.slot.inputName).append(':')
                .append(reference.uploadFileName).append('\n')
        }
        listOf(
            "width" to workflow.widthInput,
            "height" to workflow.heightInput,
            "frames" to workflow.frameCountInput,
            "fps" to workflow.framesPerSecondInput,
        ).forEach { (name, slot) ->
            append(name).append('=').append(slot?.nodeId).append('.').append(slot?.inputName).append('\n')
        }
        append("output=").append(workflow.output.nodeId).append(':')
            .append(workflow.output.allowedExtensions.sorted().joinToString(",")).append('\n')
        when (input) {
            is VideoKeyframeGenerationInput -> append("keyframe:${input.width}:${input.height}")
            is VideoClipGenerationInput -> append("video:${input.durationMillis}:${input.width}:${input.height}:${input.framesPerSecond}")
            is VideoControlledMotionGenerationInput -> error("Controlled motion uses its domain identity")
        }
    }
    return sha256(text.toByteArray(Charsets.UTF_8))
}

private fun sha256(path: Path): String = Files.newInputStream(path).use { stream ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1024 * 1024)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun usefulLocal(error: Throwable): String = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
