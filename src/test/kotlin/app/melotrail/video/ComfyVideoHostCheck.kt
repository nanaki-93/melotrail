package app.melotrail.video

import app.melotrail.video.adapter.ComfyApiOutput
import app.melotrail.video.adapter.ComfyClientCancellation
import app.melotrail.video.adapter.ComfyClientObservation
import app.melotrail.video.adapter.ComfyClientSubmission
import app.melotrail.video.adapter.ComfyClientSubmissionResult
import app.melotrail.video.adapter.ComfyShortI2VBinding
import app.melotrail.video.adapter.ComfyHostResources
import app.melotrail.video.adapter.ComfyMemoryPressure
import app.melotrail.video.adapter.ComfyVideoApi
import app.melotrail.video.adapter.ComfyVideoRuntime
import app.melotrail.video.adapter.ComfyVideoSession
import app.melotrail.video.adapter.LocalVideoBackend
import app.melotrail.video.adapter.LocalVideoSetup
import app.melotrail.video.adapter.LocalVideoSetupState
import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMediaProbeRequest
import app.melotrail.video.adapter.comfyRequestFingerprint
import app.melotrail.video.application.VideoAvailableModel
import app.melotrail.video.application.VideoJobCoordinator
import app.melotrail.video.application.VideoJobResult
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import app.melotrail.video.domain.VideoComfyReferenceInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoGenerationAttempt
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoModelRequirement
import app.melotrail.video.domain.VideoSubmissionPhase
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.NativeLongByReference
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Explicit V17 host proof. It remains off every ordinary test and application-startup path. */
object ComfyVideoHostCheck {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 1) { "Usage: comfyVideoProbe <absolute-owned-request.json>" }
        check(System.getProperty("os.name") == "Mac OS X" && System.getProperty("os.arch") in setOf("arm64", "aarch64")) {
            "comfyVideoProbe requires macOS arm64; found ${System.getProperty("os.name")}/${System.getProperty("os.arch")}"
        }
        ComfyVideoHostProbe.run(Path.of(arguments.single()))
    }
}

internal object ComfyVideoHostProbe {
    private val json = Json { ignoreUnknownKeys = false; prettyPrint = true }

    fun run(requestPath: Path) {
        val validated = validateRequest(requestPath)
        val requestHashBefore = sha256(validated.requestPath)
        val imageHashBefore = sha256(validated.composedImage)
        val output = createPrivateDirectory(validated.outputDirectory)
        val started = System.nanoTime()
        var runtime: ComfyVideoRuntime? = null
        var sampler: ComfyProcessSampler? = null
        var sessionDirectory: Path? = null
        var runtimeStopped = false
        try {
            val setupStarted = System.nanoTime()
            val setupOwner = LocalVideoSetup.bundled()
            val initialSetup = setupOwner.inspect(validated.applicationSupportRoot)
            check(initialSetup.state == LocalVideoSetupState.READY && initialSetup.ready != null) {
                "Pinned ComfyUI setup is ${initialSetup.state}: ${initialSetup.issues.joinToString { "${it.component}: ${it.detail}" }}"
            }
            val readySetup = initialSetup.ready
            val setupMillis = elapsedMillis(setupStarted)

            val workflowDirectory = createPrivateDirectory(output.resolve("workflow-input"))
            val workflow = workflowDirectory.resolve("short-shot-api.json")
            val workflowBytes = bundledWorkflowBytes()
            Files.write(workflow, workflowBytes, CREATE_NEW)
            val workflowHash = sha256(workflow)
            check(workflowHash == sha256(workflowBytes)) { "Bundled workflow changed while it was copied into the owned run" }
            val cancellationWorkflowDirectory = createPrivateDirectory(output.resolve("cancellation-workflow-input"))
            val cancellationWorkflow = cancellationWorkflowDirectory.resolve("short-shot-api.json")
            Files.write(cancellationWorkflow, workflowBytes, CREATE_NEW)
            check(sha256(cancellationWorkflow) == workflowHash) { "Cancellation graph copy does not match the bundled workflow" }

            val jobsRoot = output.resolve("jobs")
            val publicationRoot = createPrivateDirectory(output.resolve("publication"))
            val store = VideoJobStore(jobsRoot, validated.runId, validated.protectedMidiRoots)
            val input = generationInput(validated, workflow, workflowHash)
            val modelRequirements = modelRequirements()
            val request = VideoGenerationJobRequest(
                id = "host-probe-request",
                projectId = "host-probe-project",
                backendId = LocalVideoBackend.BACKEND_ID,
                modelRequirements = modelRequirements,
                input = input,
                requestFingerprint = comfyRequestFingerprint("host-probe-project", LocalVideoBackend.BACKEND_ID, input, modelRequirements),
                maximumAttempts = 1,
                createdAt = Instant.now().toString(),
                execution = VideoLocalExecutionPolicy(
                    wallClockLimitMillis = INFERENCE_LIMIT.toMillis(),
                    memoryLimitBytes = LOCAL_MEMORY_ADMISSION_BYTES,
                    diskLimitBytes = LOCAL_DISK_ADMISSION_BYTES,
                ),
            )

            val runtimeStarted = System.nanoTime()
            val liveRuntime = ComfyVideoRuntime()
            runtime = liveRuntime
            val session = liveRuntime.start(readySetup, readySetup.applicationSupportRoot.resolve("runs"), validated.port)
            sessionDirectory = session.directory
            val runtimeStartMillis = elapsedMillis(runtimeStarted)
            sampler = ComfyProcessSampler(session.directory, validated.pollIntervalMillis).also { it.start() }

            fun backend() = LocalVideoBackend(
                runtime = liveRuntime,
                session = session,
                requestLookup = { id -> store.snapshot().jobs.singleOrNull { it.request.id == id }?.request },
                ownedInputRoots = listOf(workflowDirectory, cancellationWorkflowDirectory, validated.composedImage.parent),
                publicationRoot = publicationRoot,
                availableModels = availableModels(),
            )
            val submitCoordinator = VideoJobCoordinator(validated.runId, store, listOf(backend()))
            val inferenceStarted = System.nanoTime()
            val submitted = accepted(submitCoordinator.submit(request), "submit")
            val submittedAttempt = requireActiveSubmission(submitted.attempt, submitted.launchedByCaller, "host inference")

            // Reconstruct both production application/backend objects before observing the attempt.
            // This exercises V16/V17b recovery without submitting a second inference.
            val recoveryCoordinator = VideoJobCoordinator(validated.runId, store, listOf(backend()))
            val firstRecovery = recoveryCoordinator.recover().singleOrNull()
                ?: error("The reconstructed coordinator found no active persisted attempt")
            var current = accepted(firstRecovery, "initial recovery")
            val recoveredActiveAttempt = current.attempt?.id == submittedAttempt.id
            val deadline = System.nanoTime() + INFERENCE_LIMIT.toNanos()
            var observations = 1
            while (current.attempt?.status?.isTerminal != true) {
                check(System.nanoTime() < deadline) { "Host inference exceeded the 20-minute command bound" }
                check(liveRuntime.state.name == "READY") {
                    "Owned ComfyUI runtime left READY state: ${liveRuntime.lastFailure?.message ?: liveRuntime.state}"
                }
                Thread.sleep(validated.pollIntervalMillis)
                current = accepted(recoveryCoordinator.reconcile(request.id), "reconcile")
                observations++
            }
            check(current.attempt?.status == VideoGenerationAttemptStatus.SUCCEEDED) {
                "Production inference ended as ${current.attempt?.status}: ${current.attempt?.failure}"
            }
            check(current.job.outputs.size == 1 && current.job.currentOutputId == current.job.outputs.single().id) {
                "Production coordinator did not publish exactly one immutable current output"
            }
            val inferenceMillis = elapsedMillis(inferenceStarted)

            val published = current.job.outputs.single()
            val publishedPath = publicationRoot.resolve(requireNotNull(published.relativePath)).normalize()
            check(publishedPath.startsWith(publicationRoot) && Files.isRegularFile(publishedPath, NOFOLLOW_LINKS)) {
                "Published output is missing or escaped its owned publication root"
            }
            check(sha256(publishedPath) == published.sha256 && Files.size(publishedPath) == published.byteCount) {
                "Published output no longer matches the durable job record"
            }
            val successfulOutputHash = sha256(publishedPath)

            // A terminal cancellation request must preserve the completed output and avoid a global interrupt.
            requireTerminalCancellationPreservation(recoveryCoordinator, current, jobsRoot.resolve(VideoJobStore.DOCUMENT), publishedPath)

            // Exercise actual owned-server cancellation sequentially. The separately owned graph
            // copy changes durable path identity without changing the prompt, image, graph bytes,
            // dimensions or seed, and keeps both requests in the same production ledger.
            val cancellationInput = generationInput(validated, cancellationWorkflow, workflowHash)
            val cancellationRequest = request.copy(
                id = "host-probe-active-cancel",
                input = cancellationInput,
                requestFingerprint = comfyRequestFingerprint(request.projectId, LocalVideoBackend.BACKEND_ID, cancellationInput, modelRequirements),
                createdAt = Instant.now().toString(),
            )
            val cancellationStarted = System.nanoTime()
            val cancellationSubmitted = accepted(recoveryCoordinator.submit(cancellationRequest), "active-cancellation submit")
            requireActiveSubmission(cancellationSubmitted.attempt, cancellationSubmitted.launchedByCaller, "active-cancellation request")
            var cancelled = accepted(recoveryCoordinator.cancel(cancellationRequest.id), "active-cancellation request")
            val cancellationRecovery = VideoJobCoordinator(validated.runId, store, listOf(backend()))
            val cancellationDeadline = System.nanoTime() + CANCELLATION_LIMIT.toNanos()
            var cancellationObservations = 0
            while (cancelled.attempt?.status?.isTerminal != true) {
                check(System.nanoTime() < cancellationDeadline) {
                    "Owned ComfyUI cancellation did not reconcile within ${CANCELLATION_LIMIT.seconds} seconds"
                }
                Thread.sleep(validated.pollIntervalMillis)
                cancelled = accepted(cancellationRecovery.reconcile(cancellationRequest.id), "active-cancellation reconcile")
                cancellationObservations++
            }
            check(cancelled.attempt?.status == VideoGenerationAttemptStatus.CANCELLED && cancelled.job.outputs.isEmpty()) {
                "Owned active-cancellation request ended as ${cancelled.attempt?.status} with ${cancelled.job.outputs.size} outputs"
            }
            val cancellationMillis = elapsedMillis(cancellationStarted)
            check(sha256(publishedPath) == successfulOutputHash) { "Active cancellation changed the earlier immutable output" }
            val processEvidence = requireNotNull(sampler).finish()
            sampler = null

            val stoppingStarted = System.nanoTime()
            liveRuntime.stop()
            runtimeStopped = true
            val runtimeStopMillis = elapsedMillis(stoppingStarted)
            val finalSetup = setupOwner.inspect(validated.applicationSupportRoot)
            check(finalSetup.state == LocalVideoSetupState.READY && finalSetup.issues.isEmpty()) {
                "Pinned external setup changed during the host probe: ${finalSetup.issues.joinToString { "${it.component}: ${it.detail}" }}"
            }
            check(sha256(validated.requestPath) == requestHashBefore) { "Host request bytes changed during the probe" }
            check(sha256(validated.composedImage) == imageHashBefore) { "Composed input bytes changed during the probe" }
            check(sha256(workflow) == workflowHash) { "Owned workflow copy changed during the probe" }

            val mediaStarted = System.nanoTime()
            val media = VideoMediaProbe().run(
                VideoMediaProbeRequest(
                    toolsDirectory = validated.mediaToolsDirectory,
                    input = publishedPath,
                    outputDirectory = output.resolve("media-proof"),
                    timeoutPerProcess = Duration.ofMinutes(2),
                ),
            )
            val mediaMillis = elapsedMillis(mediaStarted)
            val metadata = media.inputMetadata
            check(metadata.width == validated.width && metadata.height == validated.height) {
                "Generated dimensions were ${metadata.width}x${metadata.height}, expected ${validated.width}x${validated.height}"
            }
            check(abs(metadata.frameRate - validated.framesPerSecond.toDouble()) <= 0.01) {
                "Generated cadence was ${metadata.frameRate}, expected ${validated.framesPerSecond} fps"
            }
            check(metadata.decodedFrameCount == validated.expectedFrameCount.toLong()) {
                "Full decode counted ${metadata.decodedFrameCount} frames, expected ${validated.expectedFrameCount}"
            }
            check(metadata.audioStreamCount == 0) { "Generated host result contains an audio stream" }
            val expectedDuration = validated.expectedFrameCount.toDouble() / validated.framesPerSecond
            check(abs(metadata.durationSeconds - expectedDuration) <= 0.08) {
                "Generated duration was ${metadata.durationSeconds}, expected approximately $expectedDuration seconds"
            }
            check(sha256(publishedPath) == published.sha256) { "Media validation changed the generated source" }

            val report = buildReport(
                validated = validated,
                requestHash = requestHashBefore,
                imageHash = imageHashBefore,
                workflow = workflow,
                workflowHash = workflowHash,
                sessionDirectory = session.directory,
                job = current,
                publishedPath = publishedPath,
                mediaReport = media.report,
                process = processEvidence,
                setupMillis = setupMillis,
                runtimeStartMillis = runtimeStartMillis,
                inferenceMillis = inferenceMillis,
                observations = observations,
                cancellationMillis = cancellationMillis,
                cancellationObservations = cancellationObservations,
                cancellationAttemptId = requireNotNull(cancelled.attempt).id,
                runtimeStopMillis = runtimeStopMillis,
                mediaMillis = mediaMillis,
                totalMillis = elapsedMillis(started),
                metadata = metadata,
                recoveredActiveAttempt = recoveredActiveAttempt,
            )
            val reportPath = output.resolve("result.json")
            Files.writeString(reportPath, json.encodeToString(JsonObject.serializer(), report), CREATE_NEW)
            println("COMFY_VIDEO_PROBE_PASSED")
            println("report=$reportPath")
            println("video=$publishedPath")
            println("mediaReport=${media.report}")
        } catch (error: Throwable) {
            val sample = runCatching { sampler?.finish() }.getOrNull()
            sampler = null
            if (!runtimeStopped) runCatching { runtime?.close() }
            val failurePath = output.resolve("failure.json")
            runCatching {
                val failure = buildJsonObject {
                    put("schemaVersion", 1)
                    put("status", "failed")
                    put("failure", useful(error))
                    put("request", validated.requestPath.toString())
                    put("requestSha256Before", requestHashBefore)
                    put("requestSha256After", runCatching { sha256(validated.requestPath) }.getOrNull())
                    put("composedImageSha256Before", imageHashBefore)
                    put("composedImageSha256After", runCatching { sha256(validated.composedImage) }.getOrNull())
                    put("sessionDirectory", sessionDirectory?.toString())
                    put("runtimeState", runtime?.state?.name)
                    put("runtimeFailure", runtime?.lastFailure?.message)
                    put("elapsedMillis", elapsedMillis(started))
                    sample?.let { put("resourceSamples", it.toJson()) }
                }
                Files.writeString(failurePath, json.encodeToString(JsonObject.serializer(), failure), CREATE_NEW)
            }
            throw error
        } finally {
            if (!runtimeStopped) runCatching { runtime?.close() }
        }
    }

    internal fun decodeRequest(text: String): HostRequest = json.decodeFromString(text)

    internal fun bundledWorkflow(): JsonObject = json.parseToJsonElement(bundledWorkflowBytes().decodeToString()).jsonObject

    internal fun requireFreshOutput(path: Path) {
        require(!Files.exists(path, NOFOLLOW_LINKS)) { "Refusing to reuse existing host-probe output directory: $path" }
    }

    internal fun validateRequest(rawRequestPath: Path): ValidatedRequest {
        val requestPath = existingRegularFile(rawRequestPath, "Request file")
        val requestParent = existingDirectory(requireNotNull(requestPath.parent), "Request parent")
        checkOwnerControlled(requestParent, "Request parent")
        checkOwnerControlled(requestPath, "Request file")
        val request = decodeRequest(Files.readString(requestPath))
        require(request.schemaVersion == 1) { "Unsupported comfyVideoProbe request schema ${request.schemaVersion}" }
        require(SAFE_ID.matches(request.runId)) { "runId must be a safe stable identifier" }

        val output = absoluteNormalized(Path.of(request.outputDirectory), "Output directory")
        require(output.parent == requestParent) { "Output directory must be a fresh direct child beside the owned request file" }
        requireFreshOutput(output)
        val applicationSupport = existingDirectory(Path.of(request.applicationSupportRoot), "Application-support root")
        val mediaTools = existingDirectory(Path.of(request.mediaToolsDirectory), "Media tools directory")
        val image = existingRegularFile(Path.of(request.composedImage), "Composed image")
        require(image.parent == requestParent.resolve("inputs")) {
            "Composed image must be a direct child of the request parent's owned inputs directory"
        }
        checkOwnerControlled(image.parent, "Request inputs directory")
        checkOwnerControlled(image, "Composed image")
        require(SHA256.matches(request.composedImageSha256) && sha256(image) == request.composedImageSha256) {
            "Composed image does not match its request SHA-256 pin"
        }
        require(request.prompt.isNotBlank() && request.prompt.length <= 20_000 && '\u0000' !in request.prompt) {
            "Prompt is blank or outside the production request bound"
        }
        require(request.width in 64..8192 && request.width % 32 == 0 && request.height in 64..8192 && request.height % 32 == 0) {
            "LTX host-probe dimensions must be 64..8192 and multiples of 32"
        }
        require(request.framesPerSecond in 1..120 && request.durationMillis in 4_500L..5_500L) {
            "Host probe must remain a bounded approximately five-second case"
        }
        val derivedFrames = Math.addExact(
            Math.multiplyExact(request.durationMillis, request.framesPerSecond.toLong()),
            999L,
        ) / 1_000L
        require(derivedFrames == request.expectedFrameCount.toLong() && (request.expectedFrameCount - 1) % 8 == 0) {
            "Expected frame count must equal ceil(duration*fps) and satisfy LTX 8*n+1"
        }
        require(request.port in 1024..65535) { "ComfyUI port must be non-privileged" }
        require(request.pollIntervalMillis in 1_000L..10_000L) { "Poll interval must be 1..10 seconds" }
        require(request.protectedMidiRoots.isNotEmpty()) { "At least one protected MIDI root is required" }
        val protected = request.protectedMidiRoots.map { existingDirectory(Path.of(it), "Protected MIDI root") }
        val ownedPaths = listOf(requestParent, output, image, applicationSupport, mediaTools)
        protected.forEach { root ->
            require(ownedPaths.none { it.startsWith(root) || root.startsWith(it) }) {
                "Host-probe request/output/runtime/media paths overlap protected MIDI root: $root"
            }
        }
        require(!output.startsWith(applicationSupport) && !applicationSupport.startsWith(output)) {
            "Evidence output and installed runtime roots must remain separate"
        }
        return ValidatedRequest(
            requestPath, requestParent, request.runId, applicationSupport, output, mediaTools, image,
            request.prompt, request.width, request.height, request.durationMillis, request.framesPerSecond,
            request.expectedFrameCount, request.port, request.pollIntervalMillis, protected,
        )
    }

    internal fun generationInput(request: ValidatedRequest, workflow: Path, workflowHash: String) = ComfyShortI2VBinding.input(
        prompt = request.prompt,
        graph = VideoGenerationDependencyPin(ComfyShortI2VBinding.WORKFLOW_ID, workflowHash, workflow.toString()),
        image = VideoGenerationDependencyPin(ComfyShortI2VBinding.IMAGE_ID, sha256(request.composedImage), request.composedImage.toString()),
        durationMillis = request.durationMillis,
        width = request.width,
        height = request.height,
        framesPerSecond = request.framesPerSecond,
    )

    private fun modelRequirements() = MODEL_PINS.map { VideoModelRequirement(it.id, it.version, it.sha256) }
    private fun availableModels() = MODEL_PINS.map { VideoAvailableModel(it.id, it.version, it.sha256) }

    private fun accepted(result: VideoJobResult, phase: String): VideoJobResult.Accepted =
        result as? VideoJobResult.Accepted ?: error("Production job $phase was rejected: ${(result as VideoJobResult.Rejected).problem}")

    internal fun requireTerminalCancellationPreservation(
        coordinator: VideoJobCoordinator,
        successful: VideoJobResult.Accepted,
        ledgerPath: Path,
        publishedPath: Path,
    ) {
        val attempt = requireNotNull(successful.attempt)
        val before = coordinator.snapshot()
        check(attempt.status == VideoGenerationAttemptStatus.SUCCEEDED &&
            successful.job.attempts.last() == attempt &&
            before.jobs.single { it.request.id == successful.job.request.id } == successful.job
        ) { "Terminal cancellation preservation requires the persisted successful attempt" }
        val published = successful.job.outputs.single()
        check(successful.job.currentOutputId == published.id &&
            Files.isRegularFile(publishedPath, NOFOLLOW_LINKS) &&
            sha256(publishedPath) == published.sha256 && Files.size(publishedPath) == published.byteCount
        ) { "Terminal cancellation preservation requires the immutable published output" }
        val ledgerHash = sha256(ledgerPath)
        repeat(2) {
            val cancelled = accepted(coordinator.cancel(successful.job.request.id), "terminal cancellation preservation")
            check(cancelled.attempt == null && !cancelled.launchedByCaller && cancelled.job == successful.job) {
                "Cancellation after success did not return the unchanged job with no attempt or launched work"
            }
            check(coordinator.snapshot() == before && sha256(ledgerPath) == ledgerHash) {
                "Terminal cancellation changed the persisted successful job or ledger bytes"
            }
            check(Files.isRegularFile(publishedPath, NOFOLLOW_LINKS) &&
                sha256(publishedPath) == published.sha256 && Files.size(publishedPath) == published.byteCount
            ) { "Terminal cancellation changed the immutable successful output" }
        }
    }

    internal fun requireActiveSubmission(
        attempt: VideoGenerationAttempt?,
        launchedByCaller: Boolean,
        phase: String,
    ): VideoGenerationAttempt {
        check(launchedByCaller && attempt?.status == VideoGenerationAttemptStatus.ACTIVE) {
            "Production coordinator did not persist and acknowledge $phase: " +
                "launchedByCaller=$launchedByCaller, attempt=${attempt?.id}, status=${attempt?.status}, " +
                "submissionPhase=${attempt?.submissionPhase}, failure=${attempt?.failure ?: "none recorded"}"
        }
        return requireNotNull(attempt)
    }

    private fun buildReport(
        validated: ValidatedRequest,
        requestHash: String,
        imageHash: String,
        workflow: Path,
        workflowHash: String,
        sessionDirectory: Path,
        job: VideoJobResult.Accepted,
        publishedPath: Path,
        mediaReport: Path,
        process: ProcessEvidence,
        setupMillis: Long,
        runtimeStartMillis: Long,
        inferenceMillis: Long,
        observations: Int,
        cancellationMillis: Long,
        cancellationObservations: Int,
        cancellationAttemptId: String,
        runtimeStopMillis: Long,
        mediaMillis: Long,
        totalMillis: Long,
        metadata: app.melotrail.video.adapter.VideoMediaMetadata,
        recoveredActiveAttempt: Boolean,
    ) = buildJsonObject {
        put("schemaVersion", 1)
        put("status", "passed")
        put("capability", "One short image-conditioned LTX-2.3 video from one already composed image")
        put("unproven", buildJsonArray {
            add(JsonPrimitive("automatic multi-reference scene preparation"))
            add(JsonPrimitive("automatic layers, masks, landmarks, anchors, or complex actions"))
            add(JsonPrimitive("arbitrary-scene visual quality"))
            add(JsonPrimitive("complete 180-300 second video generation and assembly"))
        })
        put("request", buildJsonObject {
            put("path", validated.requestPath.toString())
            put("sha256", requestHash)
            put("runId", validated.runId)
            put("promptSha256", sha256(validated.prompt.toByteArray()))
            put("composedImage", validated.composedImage.toString())
            put("composedImageSha256", imageHash)
            put("workflow", workflow.toString())
            put("workflowSha256", workflowHash)
            put("width", validated.width)
            put("height", validated.height)
            put("durationMillis", validated.durationMillis)
            put("framesPerSecond", validated.framesPerSecond)
            put("expectedFrameCount", validated.expectedFrameCount)
        })
        put("productionPath", buildJsonObject {
            put("runtime", ComfyVideoRuntime::class.qualifiedName)
            put("backend", LocalVideoBackend::class.qualifiedName)
            put("coordinator", VideoJobCoordinator::class.qualifiedName)
            put("store", VideoJobStore::class.qualifiedName)
            put("mediaProbe", VideoMediaProbe::class.qualifiedName)
            put("sessionDirectory", sessionDirectory.toString())
            put("jobLedger", validated.outputDirectory.resolve("jobs/video-jobs.json").toString())
            put("publishedVideo", publishedPath.toString())
            put("publishedVideoSha256", sha256(publishedPath))
            put("mediaReport", mediaReport.toString())
        })
        put("job", buildJsonObject {
            put("requestId", job.job.request.id)
            put("requestFingerprint", job.job.request.requestFingerprint)
            put("attemptId", job.attempt?.id)
            put("providerWorkId", job.attempt?.providerWorkId)
            put("status", job.attempt?.status?.name)
            put("outputId", job.job.currentOutputId)
        })
        put("lifecycle", buildJsonObject {
            put("reconstructedCoordinatorRecoveredActiveAttempt", recoveredActiveAttempt)
            put("observationCount", observations)
            put("terminalCancellationPreservedSuccessfulOutput", true)
            put("activeCancellation", "passed")
            put("activeCancellationAttemptId", cancellationAttemptId)
            put("activeCancellationObservationCount", cancellationObservations)
            put("activeCancellationPreservedSuccessfulOutput", true)
            put("ownedRuntimeStopped", true)
        })
        put("media", buildJsonObject {
            put("codec", metadata.videoCodec)
            put("width", metadata.width)
            put("height", metadata.height)
            put("durationSeconds", metadata.durationSeconds)
            put("framesPerSecond", metadata.frameRate)
            put("fullyDecodedFrames", metadata.decodedFrameCount)
            put("audioStreams", metadata.audioStreamCount)
            put("fullDecode", "passed")
        })
        put("resources", process.toJson())
        put("timingsMillis", buildJsonObject {
            put("setupVerification", setupMillis)
            put("runtimeStartup", runtimeStartMillis)
            put("inferenceAndRecovery", inferenceMillis)
            put("activeCancellationAndRecovery", cancellationMillis)
            put("runtimeStop", runtimeStopMillis)
            put("mediaValidation", mediaMillis)
            put("total", totalMillis)
        })
        put("preservation", buildJsonObject {
            put("requestSha256Before", requestHash)
            put("requestSha256After", sha256(validated.requestPath))
            put("composedImageSha256Before", imageHash)
            put("composedImageSha256After", sha256(validated.composedImage))
            put("bundledWorkflowCopyUnchanged", sha256(workflow) == workflowHash)
            put("pinnedSetupReadyBeforeAndAfter", true)
            put("externalInputsAndInstalledSourcesWritten", false)
        })
    }

    private fun existingRegularFile(raw: Path, label: String): Path {
        val path = absoluteNormalized(raw, label)
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) { "$label must be a regular non-symlink file: $path" }
        val real = path.toRealPath()
        require(real == path) { "$label must not traverse a symlink: $path" }
        return real
    }

    private fun existingDirectory(raw: Path, label: String): Path {
        val path = absoluteNormalized(raw, label)
        require(Files.isDirectory(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) { "$label must be an existing non-symlink directory: $path" }
        val real = path.toRealPath()
        require(real == path) { "$label must not traverse a symlink: $path" }
        return real
    }

    private fun absoluteNormalized(path: Path, label: String): Path {
        require(path.isAbsolute && path.none { it.toString() in setOf(".", "..") }) { "$label must be absolute without traversal: $path" }
        return path.normalize()
    }

    private fun checkOwnerControlled(path: Path, label: String) {
        val owner = Files.getOwner(path, NOFOLLOW_LINKS)
        val homeOwner = Files.getOwner(Path.of(System.getProperty("user.home")).toRealPath(), NOFOLLOW_LINKS)
        require(owner == homeOwner) { "$label is not owned by the current home owner: $path" }
        val permissions = Files.getPosixFilePermissions(path, NOFOLLOW_LINKS)
        require(PosixFilePermission.GROUP_WRITE !in permissions && PosixFilePermission.OTHERS_WRITE !in permissions) {
            "$label must not be group/world writable: $path"
        }
    }

    private fun createPrivateDirectory(path: Path): Path {
        requireFreshOutput(path)
        val parent = requireNotNull(path.parent)
        require(Files.isDirectory(parent, NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent)) { "Private-directory parent is unsafe: $parent" }
        return Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    }

    private fun bundledWorkflowBytes(): ByteArray = checkNotNull(
        ComfyVideoHostCheck::class.java.getResourceAsStream("/video/comfyui/short-shot-api.json"),
    ) { "Missing bundled V17 short-shot workflow" }.use { it.readBytes() }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun elapsedMillis(started: Long): Long = Duration.ofNanos(System.nanoTime() - started).toMillis()
    private fun useful(error: Throwable): String = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.name

    private const val LOCAL_MEMORY_ADMISSION_BYTES = 48L * 1024L * 1024L * 1024L
    private const val LOCAL_DISK_ADMISSION_BYTES = 10L * 1024L * 1024L * 1024L
    private val INFERENCE_LIMIT: Duration = Duration.ofMinutes(20)
    private val CANCELLATION_LIMIT: Duration = Duration.ofMinutes(2)
    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val MODEL_PINS = listOf(
        ModelPin("ltx-2-3-distilled-q4", "22b-distilled-1.1-Q4_K_M", "5d09efdc0b8ec2054c44a05366cd7c6634ffa333b379b1f8baf018a78974b73d"),
        ModelPin("ltx-2-3-connectors", "distilled-1.1", "c61cbb396e2a8175d8b2da51f0fdac885a4ccd22c9f64dafa5aa2c455dc8a507"),
        ModelPin("ltx-2-3-video-vae", "distilled-1.1", "e68d6d8f8a42942ac9b862cc315beb3bc30805a8876c7ad63ba5bf7a2b8e168a"),
        ModelPin("gemma-3-12b-it-qat", "Q4_K_XL", "da98f81c86916ed1c76b3eeda56b25cb7b8352b01093e2edb8028110fe2cb53b"),
    )
}

@Serializable
internal data class HostRequest(
    val schemaVersion: Int,
    val runId: String,
    val applicationSupportRoot: String,
    val outputDirectory: String,
    val mediaToolsDirectory: String,
    val composedImage: String,
    val composedImageSha256: String,
    val prompt: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val framesPerSecond: Int,
    val expectedFrameCount: Int,
    val port: Int,
    val pollIntervalMillis: Long,
    val protectedMidiRoots: List<String>,
)

internal data class ValidatedRequest(
    val requestPath: Path,
    val requestParent: Path,
    val runId: String,
    val applicationSupportRoot: Path,
    val outputDirectory: Path,
    val mediaToolsDirectory: Path,
    val composedImage: Path,
    val prompt: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val framesPerSecond: Int,
    val expectedFrameCount: Int,
    val port: Int,
    val pollIntervalMillis: Long,
    val protectedMidiRoots: List<Path>,
)

private data class ModelPin(val id: String, val version: String, val sha256: String)

private data class ProcessEvidence(
    val rootPid: Long,
    val sampleCount: Int,
    val peakResidentBytes: Long,
    val baselineSwapBytes: Long,
    val peakSwapBytes: Long,
    val pressureLevels: Set<ComfyMemoryPressure>,
    val note: String = "RSS sums the owned ComfyUI process tree and excludes some Metal allocations; pressure and swap are host-global.",
) {
    fun toJson() = buildJsonObject {
        put("ownedProcessRootPid", rootPid)
        put("sampleCount", sampleCount)
        put("peakProcessTreeRssBytes", peakResidentBytes)
        put("baselineHostSwapBytes", baselineSwapBytes)
        put("peakHostSwapBytes", peakSwapBytes)
        put("additionalSwapBytes", (peakSwapBytes - baselineSwapBytes).coerceAtLeast(0L))
        put("pressureLevels", JsonArray(pressureLevels.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
        put("measurementNote", note)
    }
}

/** Independent evidence only; runtime admission and stop decisions stay in ComfyVideoRuntime. */
private object ComfyHostTelemetry {
    fun sample(sysctl: HostEvidenceSysctl = HostEvidenceSysctl.instance): ComfyHostResources {
        fun read(name: String, bytes: Long, decode: (Memory) -> Long): Long = Memory(bytes).use { memory ->
            memory.clear()
            val size = NativeLongByReference(NativeLong(bytes))
            check(sysctl.sysctlbyname(name, memory, size, null, NativeLong(0)) == 0) {
                "Host evidence sysctl $name failed"
            }
            check(size.value.toLong() == bytes) { "Host evidence sysctl $name returned an unexpected size" }
            decode(memory)
        }
        val pressure = when (read("kern.memorystatus_vm_pressure_level", 4) { it.getInt(0).toLong() }) {
            1L -> ComfyMemoryPressure.NORMAL
            2L -> ComfyMemoryPressure.WARNING
            4L -> ComfyMemoryPressure.CRITICAL
            else -> ComfyMemoryPressure.UNKNOWN
        }
        // Darwin xsw_usage: uint64 total, available, used; uint32 page size and encrypted.
        val usedSwap = read("vm.swapusage", 32) { it.getLong(16) }
        check(usedSwap >= 0L) { "Host evidence sysctl vm.swapusage returned an invalid used-byte count" }
        return ComfyHostResources(pressure, usedSwap)
    }
}

private interface HostEvidenceSysctl : Library {
    fun sysctlbyname(
        name: String,
        oldValue: Pointer,
        oldLength: NativeLongByReference,
        newValue: Pointer?,
        newLength: NativeLong,
    ): Int

    companion object {
        val instance: HostEvidenceSysctl by lazy { Native.load("System", HostEvidenceSysctl::class.java) }
    }
}

/** Read-only host sampler; the production runtime remains the sole process owner and resource guard. */
private class ComfyProcessSampler(
    private val sessionDirectory: Path,
    private val intervalMillis: Long,
) {
    private val running = AtomicBoolean(false)
    private val failure = AtomicReference<Throwable?>()
    private val lock = Any()
    private val baseline: ComfyHostResources = ComfyHostTelemetry.sample()
    private var rootPid = -1L
    private var samples = 0
    private var peakResident = 0L
    private var peakSwap = baseline.usedSwapBytes
    private val pressures = linkedSetOf(baseline.pressure)
    private var thread: Thread? = null

    fun start() {
        check(running.compareAndSet(false, true))
        thread = Thread({ sampleLoop() }, "melotrail-comfy-host-evidence").apply { isDaemon = true; start() }
    }

    fun finish(): ProcessEvidence {
        running.set(false)
        thread?.interrupt()
        thread?.join(10_000)
        failure.get()?.let { throw IllegalStateException("Could not measure owned ComfyUI resources", it) }
        return synchronized(lock) {
            check(rootPid > 0 && samples > 0 && peakResident > 0) { "No owned ComfyUI process-memory sample was captured" }
            ProcessEvidence(rootPid, samples, peakResident, baseline.usedSwapBytes, peakSwap, pressures.toSet())
        }
    }

    private fun sampleLoop() {
        try {
            while (running.get()) {
                val root = findRootProcess()
                if (root != null) {
                    val handles = mutableListOf(root)
                    root.descendants().use { handles += it.toList() }
                    val resident = handles.filter(ProcessHandle::isAlive).sumOf { residentBytes(it.pid()) }
                    val host = ComfyHostTelemetry.sample()
                    synchronized(lock) {
                        rootPid = root.pid()
                        samples++
                        peakResident = maxOf(peakResident, resident)
                        peakSwap = maxOf(peakSwap, host.usedSwapBytes)
                        pressures += host.pressure
                    }
                }
                Thread.sleep(intervalMillis)
            }
        } catch (_: InterruptedException) {
            // Normal finish.
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
        }
    }

    private fun findRootProcess(): ProcessHandle? = ProcessHandle.current().descendants().use { processes ->
        processes.filter { process ->
            val arguments = process.info().arguments().orElse(emptyArray())
            arguments.any { it == sessionDirectory.toString() }
        }.findFirst().orElse(null)
    }

    private fun residentBytes(pid: Long): Long {
        if (pid !in 1..Int.MAX_VALUE.toLong()) return 0L
        val memory = Memory(256)
        memory.clear()
        return if (LibProc.INSTANCE.proc_pid_rusage(pid.toInt(), RUSAGE_INFO_V2, memory) == 0) {
            memory.getLong(RESIDENT_SIZE_OFFSET).coerceAtLeast(0L)
        } else 0L
    }

    private interface LibProc : Library {
        fun proc_pid_rusage(pid: Int, flavor: Int, buffer: Pointer): Int

        companion object { val INSTANCE: LibProc = Native.load("proc", LibProc::class.java) }
    }

    companion object {
        private const val RUSAGE_INFO_V2 = 2
        private const val RESIDENT_SIZE_OFFSET = 64L
    }
}

class ComfyVideoHostCheckTest {
    @Test
    fun `host generation input uses production pinned graph binding and canonical fingerprint`() {
        withHostRequestFixture { root, requestPath, request ->
            val validated = ComfyVideoHostProbe.validateRequest(requestPath)
            val graph = root.resolve("short-shot-api.json")
            Files.write(graph, checkNotNull(javaClass.getResourceAsStream("/video/comfyui/short-shot-api.json")).use { it.readBytes() })
            val graphDigest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(graph))
                .joinToString("") { "%02x".format(it) }
            val host = ComfyVideoHostProbe.generationInput(validated, graph, graphDigest)
            val production = ComfyShortI2VBinding.input(
                request.prompt, VideoGenerationDependencyPin(ComfyShortI2VBinding.WORKFLOW_ID, graphDigest, graph.toString()),
                VideoGenerationDependencyPin(ComfyShortI2VBinding.IMAGE_ID, request.composedImageSha256, request.composedImage),
                request.durationMillis, request.width, request.height, request.framesPerSecond,
            )
            assertEquals(production, host)
            assertEquals(comfyRequestFingerprint("host-probe-project", LocalVideoBackend.BACKEND_ID, production, emptyList()),
                comfyRequestFingerprint("host-probe-project", LocalVideoBackend.BACKEND_ID, host, emptyList()))
        }
    }

    @Test
    fun `host rejects protected MIDI roots above equal to or below application support before setup`() {
        assertProtectedRootOverlapRejected { Path.of(it.applicationSupportRoot) }
    }

    @Test
    fun `host rejects protected MIDI roots above equal to or below media tools before setup`() {
        assertProtectedRootOverlapRejected { Path.of(it.mediaToolsDirectory) }
    }

    @Test
    fun `host rejects protected MIDI roots above equal to or below request evidence before setup`() {
        assertProtectedRootOverlapRejected { Path.of(it.outputDirectory).parent }
    }

    @Test
    fun `host preflight accepts disjoint protected roots without creating outputs or inspecting setup`() {
        withHostRequestFixture { root, requestPath, request ->
            val before = hostFixtureSnapshot(root)
            val validated = ComfyVideoHostProbe.validateRequest(requestPath)
            assertEquals(Path.of(request.applicationSupportRoot), validated.applicationSupportRoot)
            assertEquals(Path.of(request.mediaToolsDirectory), validated.mediaToolsDirectory)
            assertEquals(Path.of(request.outputDirectory), validated.outputDirectory)
            assertEquals(request.protectedMidiRoots.map { Path.of(it) }, validated.protectedMidiRoots)
            assertEquals(before, hostFixtureSnapshot(root))
            assertFalse(Files.exists(validated.outputDirectory, NOFOLLOW_LINKS))
            assertFalse(Files.exists(validated.applicationSupportRoot.resolve("runs"), NOFOLLOW_LINKS))
        }
    }

    private fun assertProtectedRootOverlapRejected(ownedRoot: (HostRequest) -> Path) {
        listOf("ancestor", "equal", "descendant").forEach { relationship ->
            withHostRequestFixture { root, requestPath, request ->
                val owned = ownedRoot(request)
                val protected = when (relationship) {
                    "ancestor" -> owned.parent
                    "equal" -> owned
                    else -> Files.createDirectory(owned.resolve("protected-midi"))
                }
                // Keep a disjoint first entry to prove that every declared protected root is checked.
                val overlapping = request.copy(protectedMidiRoots = request.protectedMidiRoots + protected.toString())
                Files.writeString(requestPath, Json.encodeToString(HostRequest.serializer(), overlapping))
                val before = hostFixtureSnapshot(root)

                // Exercise run itself: its first side effect is output creation, followed by setup
                // inspection, runtime/session creation and backend work. The fixture deliberately
                // contains no installed runtime or media tools; none may be inspected or launched.
                val failure = assertFailsWith<IllegalArgumentException>("$relationship of $owned") {
                    ComfyVideoHostProbe.run(requestPath)
                }
                assertEquals(
                    "Host-probe request/output/runtime/media paths overlap protected MIDI root: $protected",
                    failure.message,
                )
                assertFalse(Files.exists(Path.of(request.outputDirectory), NOFOLLOW_LINKS))
                assertFalse(Files.exists(Path.of(request.applicationSupportRoot).resolve("runs"), NOFOLLOW_LINKS))
                assertEquals(before, hostFixtureSnapshot(root), "Rejected $relationship overlap changed the fixture")
            }
        }
    }

    private fun withHostRequestFixture(block: (Path, Path, HostRequest) -> Unit) {
        val root = Files.createTempDirectory("comfy-host-path-guard").toRealPath()
        try {
            fun directory(relative: String): Path = Files.createDirectories(root.resolve(relative)).also {
                Files.setPosixFilePermissions(it, PosixFilePermissions.fromString("rwx------"))
            }
            val evidence = directory("evidence/owned-request")
            val inputs = directory("evidence/owned-request/inputs")
            val applicationSupport = directory("runtime/application-support")
            val mediaTools = directory("media/tools")
            val protected = directory("midi/project")
            val image = Files.writeString(inputs.resolve("scene.png"), "owned preflight input fixture")
            Files.setPosixFilePermissions(image, PosixFilePermissions.fromString("rw-------"))
            val imageHash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image))
                .joinToString("") { "%02x".format(it) }
            val request = HostRequest(
                schemaVersion = 1, runId = "path-guard", applicationSupportRoot = applicationSupport.toString(),
                outputDirectory = evidence.resolve("fresh-output").toString(), mediaToolsDirectory = mediaTools.toString(),
                composedImage = image.toString(), composedImageSha256 = imageHash, prompt = "A calm generic scene",
                width = 768, height = 448, durationMillis = 5_160, framesPerSecond = 25, expectedFrameCount = 129,
                port = 8192, pollIntervalMillis = 2_000, protectedMidiRoots = listOf(protected.toString()),
            )
            val requestPath = Files.writeString(evidence.resolve("request.json"), Json.encodeToString(HostRequest.serializer(), request))
            Files.setPosixFilePermissions(requestPath, PosixFilePermissions.fromString("rw-------"))
            block(root, requestPath, request)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    private fun hostFixtureSnapshot(root: Path): Map<String, String?> = Files.walk(root).use { paths ->
        paths.toList().associate { path ->
            root.relativize(path).toString() to if (Files.isDirectory(path, NOFOLLOW_LINKS)) null else
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        }
    }

    @Test
    fun `successful production coordinator recovery passes repeated terminal cancellation host proof without backend work`() {
        val root = Files.createTempDirectory("comfy-host-terminal-cancel").toRealPath()
        try {
            fun directory(name: String) = Files.createDirectory(root.resolve(name))
            fun digest(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                .joinToString("") { "%02x".format(it) }
            val session = ComfyVideoSession(
                "host-fixture", URI("http://127.0.0.1:1"), root,
                directory("input"), directory("output"), directory("temp"), directory("user"),
            )
            val publication = directory("publication")
            val jobs = root.resolve("jobs")
            val protectedRoots = listOf(directory("midi-protected"))
            val workflow = root.resolve("workflow.json")
            Files.writeString(workflow, ComfyVideoHostProbe.bundledWorkflow().toString())
            val reference = root.resolve("scene.png")
            Files.writeString(reference, "owned reference fixture")
            val input = VideoClipGenerationInput(
                prompt = "A calm generic scene", durationMillis = 5_160, width = 768, height = 448, framesPerSecond = 25,
                dependencyPins = listOf(
                    VideoGenerationDependencyPin("workflow", digest(workflow), workflow.toString()),
                    VideoGenerationDependencyPin("scene", digest(reference), reference.toString()),
                ),
                comfyWorkflow = VideoComfyWorkflowRequest(
                    workflowDependencyId = "workflow", promptInput = VideoComfyInputSlot("20", "value"),
                    referenceInputs = listOf(VideoComfyReferenceInput("scene", VideoComfyInputSlot("4", "image"), "scene.png")),
                    widthInput = VideoComfyInputSlot("21", "value"), heightInput = VideoComfyInputSlot("22", "value"),
                    frameCountInput = VideoComfyInputSlot("23", "value"), framesPerSecondInput = VideoComfyInputSlot("24", "value"),
                    output = VideoComfyOutputBinding("16", setOf("mp4")),
                ),
            )
            val request = VideoGenerationJobRequest(
                id = "host-request", projectId = "host-project", backendId = LocalVideoBackend.BACKEND_ID,
                input = input, modelRequirements = emptyList(),
                requestFingerprint = comfyRequestFingerprint("host-project", LocalVideoBackend.BACKEND_ID, input, emptyList()),
                maximumAttempts = 1, createdAt = "2026-09-15T00:00:00Z",
                execution = VideoLocalExecutionPolicy(60_000, 1_000_000_000, 1_000_000),
            )
            val calls = mutableListOf<String>()
            val api = object : ComfyVideoApi {
                override fun submit(request: ComfyClientSubmission): ComfyClientSubmissionResult {
                    calls += "submit"
                    return ComfyClientSubmissionResult.Accepted(request.promptId)
                }
                override fun observe(promptId: String, clientId: String, output: VideoComfyOutputBinding): ComfyClientObservation {
                    calls += "observe"
                    return ComfyClientObservation.Completed(ComfyApiOutput("16", "take.mp4", "", "output"))
                }
                override fun cancel(promptId: String, acknowledged: Boolean): ComfyClientCancellation {
                    calls += "cancel"
                    error("A successful terminal job must never call the ComfyUI cancellation API")
                }
            }
            fun coordinator(): VideoJobCoordinator {
                val store = VideoJobStore(jobs, "host-fixture", protectedRoots)
                val backend = LocalVideoBackend(
                    session = session, client = api,
                    acquireSlot = { calls += "acquire"; true }, releaseSlot = { calls += "release" },
                    requestLookup = { id -> store.snapshot().jobs.singleOrNull { it.request.id == id }?.request },
                    ownedInputRoots = listOf(root), publicationRoot = publication, availableModels = emptyList(),
                )
                return VideoJobCoordinator("host-fixture", store, listOf(backend))
            }
            val submitted = assertIs<VideoJobResult.Accepted>(coordinator().submit(request))
            ComfyVideoHostProbe.requireActiveSubmission(submitted.attempt, submitted.launchedByCaller, "fixture inference")
            // Fixture bytes exercise publication integrity only; no inference or media-validity claim.
            Files.writeString(session.outputDirectory.resolve("take.mp4"), "owned generated result fixture")
            val successful = assertIs<VideoJobResult.Accepted>(coordinator().recover().single())
            val successfulAttempt = requireNotNull(successful.attempt)
            assertEquals(submitted.attempt!!.id, successfulAttempt.id)
            assertEquals(VideoGenerationAttemptStatus.SUCCEEDED, successfulAttempt.status)
            val published = publication.resolve(requireNotNull(successful.job.outputs.single().relativePath))
            val successfulLedger = Files.readAllBytes(jobs.resolve(VideoJobStore.DOCUMENT))
            val successfulBytes = Files.readAllBytes(published)
            assertEquals(listOf("acquire", "submit", "observe", "release"), calls)
            repeat(2) {
                val restarted = coordinator()
                assertTrue(restarted.recover().isEmpty())
                ComfyVideoHostProbe.requireTerminalCancellationPreservation(
                    restarted, successful, jobs.resolve(VideoJobStore.DOCUMENT), published,
                )
                assertEquals(listOf("acquire", "submit", "observe", "release"), calls)
                assertTrue(successfulLedger.contentEquals(Files.readAllBytes(jobs.resolve(VideoJobStore.DOCUMENT))))
                assertTrue(successfulBytes.contentEquals(Files.readAllBytes(published)))
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }

    @Test
    fun `host submission failure exposes persisted status phase and backend cause`() {
        val cause = "ComfyUI node '16' has unsupported inputs: format.codec, format.codec.encoding, format.codec.encoding.crf."
        val attempt = VideoGenerationAttempt(
            id = "attempt-rejected", requestId = "host-probe-request", number = 1, ownershipToken = "owner-1",
            status = VideoGenerationAttemptStatus.FAILED, submissionPhase = VideoSubmissionPhase.NOT_STARTED,
            admittedAt = "2026-09-14T15:56:44Z", finishedAt = "2026-09-14T15:56:45Z", failure = cause,
        )
        listOf("host inference", "active-cancellation request").forEach { phase ->
            val failure = assertFailsWith<IllegalStateException> {
                ComfyVideoHostProbe.requireActiveSubmission(attempt, true, phase)
            }
            assertTrue(failure.message.orEmpty().contains(phase))
            assertTrue(failure.message.orEmpty().contains("status=FAILED, submissionPhase=NOT_STARTED"))
            assertTrue(failure.message.orEmpty().contains(cause))
        }
    }

    @Test
    fun `independent host telemetry preserves pressure levels and 64 bit used swap with read only calls`() {
        val expected = mapOf(
            1 to ComfyMemoryPressure.NORMAL,
            2 to ComfyMemoryPressure.WARNING,
            4 to ComfyMemoryPressure.CRITICAL,
            0 to ComfyMemoryPressure.UNKNOWN,
            8 to ComfyMemoryPressure.UNKNOWN,
        )
        val usedSwap = 9L * 1024 * 1024 * 1024 + 123
        expected.forEach { (level, pressure) ->
            val calls = mutableListOf<String>()
            val resources = ComfyHostTelemetry.sample(telemetryFixture(level, usedSwap, calls))
            assertEquals(ComfyHostResources(pressure, usedSwap), resources)
            assertEquals(listOf("kern.memorystatus_vm_pressure_level", "vm.swapusage"), calls)
        }
    }

    @Test
    fun `independent host telemetry refuses failed and truncated native reads`() {
        listOf("kern.memorystatus_vm_pressure_level", "vm.swapusage").forEach { name ->
            val failed = assertFailsWith<IllegalStateException> {
                ComfyHostTelemetry.sample(telemetryFixture(failedName = name))
            }
            assertTrue(failed.message.orEmpty().contains("$name failed"))
            val truncated = assertFailsWith<IllegalStateException> {
                ComfyHostTelemetry.sample(telemetryFixture(truncatedName = name))
            }
            assertTrue(truncated.message.orEmpty().contains("$name returned an unexpected size"))
        }
    }

    @Test
    fun `independent host telemetry refuses an unrepresentable swap count`() {
        assertFailsWith<IllegalStateException> {
            ComfyHostTelemetry.sample(telemetryFixture(usedSwap = -1L))
        }
    }

    private fun telemetryFixture(
        pressure: Int = 1,
        usedSwap: Long = 0L,
        calls: MutableList<String> = mutableListOf(),
        failedName: String? = null,
        truncatedName: String? = null,
    ): HostEvidenceSysctl = object : HostEvidenceSysctl {
        override fun sysctlbyname(
            name: String,
            oldValue: Pointer,
            oldLength: NativeLongByReference,
            newValue: Pointer?,
            newLength: NativeLong,
        ): Int {
            calls += name
            assertEquals(null, newValue, "Telemetry must never supply a sysctl write value")
            assertEquals(0L, newLength.toLong(), "Telemetry must never request a sysctl write")
            when (name) {
                "kern.memorystatus_vm_pressure_level" -> {
                    assertEquals(4L, oldLength.value.toLong())
                    oldValue.setInt(0, pressure)
                }
                "vm.swapusage" -> {
                    assertEquals(32L, oldLength.value.toLong())
                    oldValue.setLong(0, 64L * 1024 * 1024 * 1024)
                    oldValue.setLong(8, 32L * 1024 * 1024 * 1024)
                    oldValue.setLong(16, usedSwap)
                }
                else -> error("Unexpected telemetry query: $name")
            }
            if (name == truncatedName) oldLength.value = NativeLong(oldLength.value.toLong() - 1)
            return if (name == failedName) -1 else 0
        }
    }

    @Test
    fun `bundled graph exposes only generic named host bindings`() {
        val graph = ComfyVideoHostProbe.bundledWorkflow()
        assertTrue(graph["4"]!!.jsonObject["inputs"]!!.jsonObject["image"]!!.jsonPrimitive.content.contains("CHOOSE_YOUR_SCENE_IMAGE"))
        assertTrue(graph["20"]!!.jsonObject["inputs"]!!.jsonObject["value"]!!.jsonPrimitive.content == "REPLACE_WITH_REQUEST_PROMPT")
        assertTrue(graph["16"]!!.jsonObject["class_type"]!!.jsonPrimitive.content == "SaveVideo")
        assertTrue(graph["2"]!!.jsonObject["inputs"]!!.jsonObject["clip_name1"]!!.jsonPrimitive.content.startsWith("gemma-3-12b"))
        assertTrue(graph["11"]!!.jsonObject["inputs"]!!.jsonObject["sigmas"]!!.jsonPrimitive.content.split(',').size == 9)
        assertTrue(graph["14"]!!.jsonObject["inputs"]!!.jsonObject["tile_size"]!!.jsonPrimitive.content == "512")
        assertTrue(graph["14"]!!.jsonObject["inputs"]!!.jsonObject["temporal_overlap"]!!.jsonPrimitive.content == "32")
        assertTrue(graph["25"]!!.jsonObject["inputs"]!!.jsonObject["value"]!!.jsonPrimitive.content == "20260914")
        assertFalse(graph.toString().contains("Tabi", ignoreCase = true))
        assertFalse(graph.toString().contains("/Users/"))
    }

    @Test
    fun `request decoder rejects unowned extension fields`() {
        val text = """{"schemaVersion":1,"runId":"run","applicationSupportRoot":"/tmp/a","outputDirectory":"/tmp/b","mediaToolsDirectory":"/tmp/c","composedImage":"/tmp/d","composedImageSha256":"${"0".repeat(64)}","prompt":"p","width":1024,"height":576,"durationMillis":5160,"framesPerSecond":25,"expectedFrameCount":129,"port":8192,"pollIntervalMillis":2000,"protectedMidiRoots":["/tmp/m"],"embeddedWorkflowPath":"/tmp/unowned.json"}"""
        assertFailsWith<SerializationException> { ComfyVideoHostProbe.decodeRequest(text) }
    }

    @Test
    fun `host output refuses a collision`() {
        val directory = Files.createTempDirectory("comfy-host-output-collision")
        try {
            assertFailsWith<IllegalArgumentException> { ComfyVideoHostProbe.requireFreshOutput(directory) }
        } finally {
            Files.deleteIfExists(directory)
        }
    }
}
