package app.melotrail.video.adapter

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Pinned preparation and explicit host-probe boundary for the local video candidate. */
object LocalVideoProfileBoundary {
    const val PROFILE_SCHEMA = "melotrail-local-video-profile"
    const val REQUEST_SCHEMA = "melotrail-local-video-probe-request"
    const val SCHEMA_VERSION = 1
    const val SUPPORTED_BACKEND_ID = "draw-things-cli"
    const val MAX_VIDEO_PROFILES = 2
    const val MAX_TAKES_PER_PROFILE = 3
    const val REPORT_SCHEMA = "melotrail-local-video-probe-report"
    private const val MAX_PROMPT_CHARACTERS = 20_000
    private const val MAX_STAGE_SECONDS = 24 * 60 * 60L

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Null measurement/selection fields are material: NOT_RUN reports must show them explicitly.
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun loadBundledProfile(): LocalVideoProfileDocument {
        val text = checkNotNull(LocalVideoProfileBoundary::class.java.getResource("/video/local-profile.json")) {
            "Bundled local video profile is missing: /video/local-profile.json"
        }.readText()
        return decodeProfile(text)
    }

    fun decodeProfile(text: String): LocalVideoProfileDocument = decode("local video profile") {
        json.decodeFromString<LocalVideoProfileDocument>(text).also(::validateProfile)
    }

    fun decodeRequest(text: String): LocalVideoProbeRequest = decode("local video probe request") {
        json.decodeFromString<LocalVideoProbeRequest>(text).also(::validateRequestShape)
    }

    /**
     * Reads and hashes pinned inputs, and validates unused output names. It deliberately performs no
     * process launch, network access, directory creation, or output write.
     */
    fun prepare(profile: LocalVideoProfileDocument, request: LocalVideoProbeRequest): LocalVideoProbePreparationReport {
        validateProfile(profile)
        validateRequestShape(request)
        require(request.profileId == profile.id) {
            "Probe profileId '${request.profileId}' does not match bundled profile '${profile.id}'"
        }
        require(request.backendId == profile.backend.id) {
            "Probe backendId '${request.backendId}' does not match profile backend '${profile.backend.id}'"
        }
        require(request.tool.id == request.backendId) {
            "Tool pin id '${request.tool.id}' must match backendId '${request.backendId}'"
        }
        require(request.profiles.size <= profile.limits.maxVideoProfiles &&
            request.profiles.all { it.takes.size <= profile.limits.maxTakesPerProfile }) {
            "Probe request exceeds the bundled profile limits"
        }

        val candidateProfiles = profile.videoProfiles.associateBy(LocalVideoProfileCandidate::id)
        val pinnedInputs = buildList {
            add(validatePin(request.tool, "Tool"))
            request.references.forEach { add(validatePin(it.pin, "Reference '${it.id}'")) }
            request.profiles.forEach { probe ->
                require(candidateProfiles.containsKey(probe.profileId)) {
                    "Unknown candidate video profile '${probe.profileId}'"
                }
                probe.modelPins.forEach { add(validatePin(it, "Model '${probe.profileId}/${it.id}'")) }
            }
        }

        val outputDirectory = absoluteNormalized(request.outputDirectory, "outputDirectory")
        require(!Files.exists(outputDirectory, NOFOLLOW_LINKS) || Files.isDirectory(outputDirectory, NOFOLLOW_LINKS)) {
            "outputDirectory must be a directory when it already exists: $outputDirectory"
        }
        val plannedOutputs = listOf(request.reportPath) + request.profiles.flatMap { profileRequest ->
            listOfNotNull(profileRequest.keyframe?.outputPath) +
                profileRequest.takes.map(LocalVideoProbeTake::outputPath)
        }
        val normalizedOutputs = plannedOutputs.map { raw ->
            absoluteNormalized(raw, "Output path").also { output ->
                require(output.startsWith(outputDirectory) && output != outputDirectory) {
                    "Output path must be a child of outputDirectory: $output"
                }
                require(!Files.exists(output, NOFOLLOW_LINKS)) {
                    "Refusing to replace existing output: $output"
                }
                requireNearestExistingParentIsDirectory(output)
            }
        }
        val canonicalInputs = pinnedInputs.map { it.path.toRealPath() }
        val canonicalOutputDirectory = canonicalFuturePath(outputDirectory)
        val canonicalOutputs = normalizedOutputs.map(::canonicalFuturePath)
        canonicalOutputs.forEachIndexed { index, output ->
            require(hasResolvedPrefix(output, canonicalOutputDirectory) && output.nameCount > canonicalOutputDirectory.nameCount) {
                "Output path must resolve to a child of outputDirectory: ${normalizedOutputs[index]}"
            }
            canonicalOutputs.drop(index + 1).forEach { other ->
                require(!hasResolvedPrefix(output, other, portableNames = true) &&
                    !hasResolvedPrefix(other, output, portableNames = true)) {
                    "Every report and take must have a distinct output path with no ancestor/descendant overlap: $output and $other"
                }
            }
        }
        require(canonicalInputs.none { input -> hasResolvedPrefix(input, canonicalOutputDirectory, portableNames = true) }) {
            "outputDirectory contains a pinned tool, model, or reference input"
        }
        canonicalOutputs.forEach { output ->
            require(canonicalInputs.none { input ->
                hasResolvedPrefix(input, output, portableNames = true) ||
                    hasResolvedPrefix(output, input, portableNames = true)
            }) {
                "Unsafe output overlap with a pinned tool, model, or reference: $output"
            }
        }

        return LocalVideoProbePreparationReport(
            status = LocalVideoProbeStatus.NOT_RUN,
            reason = "Preparation validated pins and reserved output names only; V11 must explicitly run and measure the local backend.",
            profileId = profile.id,
            profileEvidence = profile.evidence,
            backendId = profile.backend.id,
            validatedInputPins = pinnedInputs.size,
            preparedVideoProfiles = request.profiles.size,
            preparedTakes = request.profiles.sumOf { it.takes.size },
            outputLocations = normalizedOutputs.map(Path::toString),
            measurements = null,
            selectedProfileId = null,
        )
    }

    fun prepare(profileText: String, requestText: String): LocalVideoProbePreparationReport =
        prepare(decodeProfile(profileText), decodeRequest(requestText))

    fun encodeReport(report: LocalVideoProbePreparationReport): String = json.encodeToString(report)

    fun encodeReport(report: LocalVideoProbeRunReport): String = json.encodeToString(report)

    /**
     * Runs only the released, hash-pinned Draw Things CLI through [VideoMediaProcess]. The CLI has
     * one image input and no control/reference inputs, so more than one bound reference is rejected
     * before launch instead of being silently ignored.
     */
    fun run(profile: LocalVideoProfileDocument, request: LocalVideoProbeRequest): LocalVideoProbeRunReport {
        prepare(profile, request)
        val execution = requireNotNull(request.execution) {
            "A real host probe requires an execution block; requests without it remain NOT_RUN preparation"
        }
        val outputDirectory = absoluteNormalized(request.outputDirectory, "outputDirectory")
        require(!Files.exists(outputDirectory, NOFOLLOW_LINKS)) {
            "Real probe outputDirectory must be absent so prior evidence is never reused or replaced: $outputDirectory"
        }

        val unsupportedReferenceRequest = request.profiles.any { it.referenceBindings.size != 1 }
        val referenceUsage = request.references.map { reference ->
            LocalVideoReferenceUsage(
                id = reference.id,
                role = reference.role,
                path = reference.pin.path,
                sha256 = reference.pin.sha256,
                disposition = LocalVideoReferenceDisposition.NOT_CONSUMED,
            )
        }
        val baseReport = LocalVideoProbeRunReport(
            status = LocalVideoProbeRunStatus.FAILED,
            reason = "Probe did not start.",
            profileId = profile.id,
            backendId = profile.backend.id,
            backendRelease = profile.backend.release ?: execution.toolRelease,
            backendSourceRevision = profile.backend.sourceRevision ?: execution.sourceRevision,
            tool = request.tool,
            modelPins = request.profiles.flatMap(LocalVideoProbeProfile::modelPins).distinctBy { it.id },
            referenceUsage = referenceUsage,
            offline = true,
            downloadMissing = false,
            stages = emptyList(),
            selectedProfileId = null,
            measurements = LocalVideoHostMeasurements(
                processElapsedMillis = 0,
                coldLoadMillis = null,
                warmLoadMillis = null,
                peakMemoryBytes = null,
                peakSwapBytes = null,
                status = LocalVideoMeasurementStatus.NOT_AVAILABLE,
            ),
            mediaInspection = LocalVideoMediaInspection(
                status = LocalVideoMediaInspectionStatus.PENDING_EXTERNAL_INSPECTION,
                actualDurationMillis = null,
                actualFramesPerSecond = null,
                actualFrameCount = null,
                audioStreamPresent = null,
            ),
            recommendation = LocalVideoProbeRecommendation.DO_NOT_SELECT_RELEASED_CLI_FOR_MULTI_REFERENCE_WITHOUT_FURTHER_PROOF,
            limitations = listOf(
                "Released Draw Things CLI accepts exactly one --image and exposes no additional reference/control inputs.",
                "An explicit, separately measured composite image or lower-level supported API could be assessed later; neither is proven here.",
                "Every JSGenerationConfiguration field is fixed by the request-derived safe override; mutable recommended settings cannot add LoRAs, controls, hires fix, upscalers, restoration or refiners.",
                "Peak memory, swap, decoded cadence, duration, and audio-stream presence require coordinator host inspection.",
                "LTX-2.3 may emit synchronized audio; a silent prompt is not evidence of a silent output stream.",
                "No visual acceptance is inferred from process success.",
            ),
        )

        if (unsupportedReferenceRequest) {
            require(Path.of(request.reportPath).parent == outputDirectory) {
                "Real probe report must be a direct child of outputDirectory"
            }
            createPrivateOutputDirectory(outputDirectory)
            return writeRunReport(
                Path.of(request.reportPath),
                baseReport.copy(
                    reason = "Released Draw Things CLI supports exactly one pixel-conditioned --image; multiple reference roles were rejected before launch.",
                ),
            )
        }

        validateExecution(profile, request, execution, outputDirectory)

        createPrivateOutputDirectory(outputDirectory)
        val stages = mutableListOf<LocalVideoProbeStageReport>()
        var totalElapsed = 0L
        var actualReferenceUsage = referenceUsage
        val modelWorkspace = LocalVideoModelWorkspace(
            outputDirectory, request.profiles.single().modelPins, execution.cancellationFile?.let(Path::of),
        )
        fun finish(report: LocalVideoProbeRunReport): LocalVideoProbeRunReport {
            val models = modelWorkspace.inspect()
            return writeRunReport(Path.of(request.reportPath), report.copy(
                status = if (models.failure == null) report.status else LocalVideoProbeRunStatus.FAILED,
                reason = if (models.failure == null) report.reason else "${report.reason} Model audit: ${models.failure}",
                modelWorkspace = models,
            ))
        }
        try {
            modelWorkspace.stage()

            request.profiles.forEach { requestedProfile ->
                val keyframe = checkNotNull(requestedProfile.keyframe)
                val keyframeReference = request.references.single { it.id == keyframe.referenceBinding }
                val keyframeModel = requestedProfile.modelPins.single { it.id == keyframe.modelPinId }
                val keyframeArguments = keyframeArguments(
                    request.prompt,
                    modelWorkspace.directory.toString(),
                    keyframeModel,
                    keyframeReference.pin.path,
                    keyframe,
                )
                actualReferenceUsage = referenceUsage.map { usage ->
                    if (usage.id == keyframe.referenceBinding) {
                        usage.copy(disposition = LocalVideoReferenceDisposition.REQUESTED_PIXEL_BINDING)
                    } else {
                        usage
                    }
                }
                modelWorkspace.requireReady()
                val keyframeStage = executeStage(
                    request = request,
                    execution = execution,
                    stageId = "${requestedProfile.profileId}-keyframe",
                    arguments = keyframeArguments,
                    outputPath = Path.of(keyframe.outputPath),
                    expectedKind = LocalVideoArtifactKind.PNG_KEYFRAME,
                    resourceIssue = modelWorkspace::resourceIssue,
                )
                stages += keyframeStage
                totalElapsed += keyframeStage.elapsedMillis
                if (keyframeStage.status != LocalVideoProbeStageStatus.SUCCEEDED) {
                    return finish(
                        baseReport.copy(
                            reason = "FLUX keyframe stage failed; no video take was launched.",
                            stages = stages,
                            referenceUsage = actualReferenceUsage,
                            measurements = baseReport.measurements.copy(
                                processElapsedMillis = totalElapsed,
                                status = LocalVideoMeasurementStatus.STAGE_WALL_TIME_ONLY,
                            ),
                        ),
                    )
                }
                actualReferenceUsage = referenceUsage.map { usage ->
                    if (usage.id == keyframe.referenceBinding) {
                        usage.copy(disposition = LocalVideoReferenceDisposition.PIXEL_CONDITIONED)
                    } else {
                        usage
                    }
                }

                val video = checkNotNull(requestedProfile.video)
                val videoModel = requestedProfile.modelPins.single { it.id == video.modelPinId }
                requestedProfile.takes.forEach { take ->
                    val arguments = videoArguments(
                        request.prompt,
                        modelWorkspace.directory.toString(),
                        videoModel,
                        keyframe.outputPath,
                        video,
                        checkNotNull(take.seed),
                        take.outputPath,
                    )
                    modelWorkspace.requireReady()
                    val takeStage = executeStage(
                        request = request,
                        execution = execution,
                        stageId = "${requestedProfile.profileId}-${take.id}",
                        arguments = arguments,
                        outputPath = Path.of(take.outputPath),
                        expectedKind = LocalVideoArtifactKind.MP4_VIDEO,
                        resourceIssue = modelWorkspace::resourceIssue,
                        requestedFrames = video.frames,
                        expectedFramesPerSecond = video.expectedFramesPerSecond,
                    )
                    stages += takeStage
                    totalElapsed += takeStage.elapsedMillis
                    if (takeStage.status != LocalVideoProbeStageStatus.SUCCEEDED) {
                        return finish(
                            baseReport.copy(
                                reason = "Video stage '${take.id}' failed; later takes were not launched.",
                                stages = stages,
                                referenceUsage = actualReferenceUsage,
                                measurements = baseReport.measurements.copy(
                                    processElapsedMillis = totalElapsed,
                                    status = LocalVideoMeasurementStatus.STAGE_WALL_TIME_ONLY,
                                ),
                            ),
                        )
                    }
                }
            }
        } catch (error: Exception) {
            return finish(
                baseReport.copy(
                    reason = "Probe orchestration failed: ${usefulMessage(error)}",
                    stages = stages,
                    referenceUsage = actualReferenceUsage,
                    measurements = baseReport.measurements.copy(
                        processElapsedMillis = totalElapsed,
                        status = if (stages.isEmpty()) {
                            LocalVideoMeasurementStatus.NOT_AVAILABLE
                        } else {
                            LocalVideoMeasurementStatus.STAGE_WALL_TIME_ONLY
                        },
                    ),
                ),
            )
        }

        return finish(
            baseReport.copy(
                status = LocalVideoProbeRunStatus.COMPLETED_UNREVIEWED,
                reason = "Pinned single-reference FLUX keyframe and LTX-2.3 I2V processes completed; media facts and visual quality remain unreviewed.",
                stages = stages,
                referenceUsage = actualReferenceUsage,
                measurements = baseReport.measurements.copy(
                    processElapsedMillis = totalElapsed,
                    status = LocalVideoMeasurementStatus.STAGE_WALL_TIME_ONLY,
                ),
            ),
        )
    }

    private fun executeStage(
        request: LocalVideoProbeRequest,
        execution: LocalVideoProbeExecution,
        stageId: String,
        arguments: List<String>,
        outputPath: Path,
        expectedKind: LocalVideoArtifactKind,
        resourceIssue: () -> String?,
        requestedFrames: Int? = null,
        expectedFramesPerSecond: Double? = null,
    ): LocalVideoProbeStageReport {
        val workingDirectory = Path.of(request.outputDirectory).resolve(".work-$stageId")
        val started = System.nanoTime()
        val cancellation = VideoMediaProcessCancellation()
        val monitorDone = AtomicBoolean(false)
        val cancellationPath = execution.cancellationFile?.let(Path::of)
        if (cancellationPath != null && Files.exists(cancellationPath, NOFOLLOW_LINKS)) {
            cancellation.cancel()
        }
        val resourceFailure = AtomicReference<String?>(null)
        val monitor = thread(name = "video-probe-cancellation-$stageId", isDaemon = true) {
            while (!monitorDone.get()) {
                val issue = resourceIssue()
                if (issue != null) resourceFailure.compareAndSet(null, issue)
                if (issue != null || (cancellationPath != null && Files.exists(cancellationPath, NOFOLLOW_LINKS))) {
                    cancellation.cancel()
                    return@thread
                }
                try {
                    Thread.sleep(200)
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }
        return try {
            val result = VideoMediaProcess().run(VideoMediaProcessRequest(
                executable = Path.of(request.tool.path),
                executableSha256 = request.tool.sha256,
                arguments = arguments,
                workingDirectory = workingDirectory,
                timeout = Duration.ofSeconds(execution.timeoutSecondsPerStage),
                maxStdoutBytes = execution.maxStdoutBytes,
                maxStderrBytes = execution.maxStderrBytes,
            ), cancellation)
            check(resourceFailure.get() == null) { checkNotNull(resourceFailure.get()) }
            require(!Files.isSymbolicLink(outputPath) && Files.isRegularFile(outputPath, NOFOLLOW_LINKS)) {
                "Pinned CLI exited successfully without the requested regular output: $outputPath"
            }
            require(Files.size(outputPath) > 0) { "Pinned CLI wrote an empty output: $outputPath" }
            LocalVideoProbeStageReport(
                id = stageId,
                status = LocalVideoProbeStageStatus.SUCCEEDED,
                arguments = arguments,
                elapsedMillis = result.elapsed.toMillis(),
                stdout = LocalVideoRecordedOutput(
                    result.stdout.text,
                    result.stdout.totalBytes,
                    result.stdout.truncated,
                ),
                stderr = LocalVideoRecordedOutput(
                    result.stderr.text,
                    result.stderr.totalBytes,
                    result.stderr.truncated,
                ),
                failure = null,
                output = LocalVideoOutputArtifact(
                    kind = expectedKind,
                    path = outputPath.toString(),
                    bytes = Files.size(outputPath),
                    sha256 = sha256(outputPath),
                    requestedFrames = requestedFrames,
                    expectedFramesPerSecond = expectedFramesPerSecond,
                    requestedDurationMillis = requestedFrames?.let { frames ->
                        (frames * 1_000.0 / checkNotNull(expectedFramesPerSecond)).toLong()
                    },
                ),
            )
        } catch (error: Exception) {
            val process = error as? VideoMediaProcessException
            LocalVideoProbeStageReport(
                id = stageId,
                status = LocalVideoProbeStageStatus.FAILED,
                arguments = arguments,
                elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis(),
                stdout = process?.stdout?.let {
                    LocalVideoRecordedOutput(it.text, it.totalBytes, it.truncated)
                } ?: LocalVideoRecordedOutput("", 0, false),
                stderr = process?.stderr?.let {
                    LocalVideoRecordedOutput(it.text, it.totalBytes, it.truncated)
                } ?: LocalVideoRecordedOutput("", 0, false),
                failure = LocalVideoProbeStageFailure(
                    type = process?.failure?.name ?: error.javaClass.simpleName,
                    message = resourceFailure.get() ?: usefulMessage(error),
                    exitCode = process?.exitCode,
                    signal = process?.signal,
                ),
                output = if (!Files.isSymbolicLink(outputPath) && Files.isRegularFile(outputPath, NOFOLLOW_LINKS)) {
                    LocalVideoOutputArtifact(
                        kind = expectedKind,
                        path = outputPath.toString(),
                        bytes = Files.size(outputPath),
                        sha256 = sha256(outputPath),
                        requestedFrames = requestedFrames,
                        expectedFramesPerSecond = expectedFramesPerSecond,
                        requestedDurationMillis = requestedFrames?.let { frames ->
                            (frames * 1_000.0 / checkNotNull(expectedFramesPerSecond)).toLong()
                        },
                    )
                } else {
                    null
                },
            )
        } finally {
            monitorDone.set(true)
            monitor.interrupt()
            monitor.join(1_000)
        }
    }

    private fun writeRunReport(path: Path, report: LocalVideoProbeRunReport): LocalVideoProbeRunReport {
        Files.writeString(path, encodeReport(report), CREATE_NEW, WRITE)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        return report
    }

    private fun createPrivateOutputDirectory(path: Path) {
        Files.createDirectory(
            path,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
        )
    }

    private fun keyframeArguments(
        prompt: String,
        modelsDirectory: String,
        model: PinnedLocalFile,
        image: String,
        settings: LocalVideoKeyframeProbe,
    ): List<String> = listOf(
        "generate", "--models-dir", modelsDirectory, "--model", Path.of(model.path).fileName.toString(),
        "--prompt", prompt, "--negative-prompt", "", "--config-json", safeGenerationConfigJson(
            settings.sampler,
            settings.shift,
            settings.resolutionDependentShift,
            settings.clipSkip,
            settings.speedUpWithGuidanceEmbed,
        ),
        "--image", image, "--strength", settings.strength.toString(),
        "--steps", settings.steps.toString(), "--cfg", settings.cfg.toString(),
        "--width", settings.width.toString(), "--height", settings.height.toString(),
        "--seed", settings.seed.toString(), "--output", settings.outputPath,
        "--no-download-missing", "--disable-preview", "--offline",
    )

    private fun videoArguments(
        prompt: String,
        modelsDirectory: String,
        model: PinnedLocalFile,
        image: String,
        settings: LocalVideoProbeVideo,
        seed: Long,
        output: String,
    ): List<String> = listOf(
        "generate", "--models-dir", modelsDirectory, "--model", Path.of(model.path).fileName.toString(),
        "--prompt", prompt, "--negative-prompt", "", "--config-json", safeGenerationConfigJson(
            settings.sampler,
            settings.shift,
            settings.resolutionDependentShift,
            settings.clipSkip,
            settings.speedUpWithGuidanceEmbed,
        ),
        "--image", image, "--strength", settings.strength.toString(),
        "--steps", settings.steps.toString(), "--cfg", settings.cfg.toString(),
        "--width", settings.width.toString(), "--height", settings.height.toString(),
        "--frames", settings.frames.toString(), "--seed", seed.toString(),
        "--output", output, "--video-format", "h264",
        "--no-download-missing", "--disable-preview", "--offline",
    )

    /** Every field in the pinned revision's JSGenerationConfiguration, before explicit CLI flags. */
    private fun safeGenerationConfigJson(
        sampler: Int,
        shift: Double,
        resolutionDependentShift: Boolean,
        clipSkip: Int,
        speedUpWithGuidanceEmbed: Boolean,
    ): String = """{
        "id":0,
        "width":1024,
        "height":576,
        "seed":0,
        "steps":20,
        "guidanceScale":4.5,
        "strength":1.0,
        "model":null,
        "sampler":$sampler,
        "hiresFix":false,
        "hiresFixWidth":448,
        "hiresFixHeight":448,
        "hiresFixStrength":0.7,
        "tiledDecoding":false,
        "decodingTileWidth":640,
        "decodingTileHeight":640,
        "decodingTileOverlap":128,
        "tiledDiffusion":false,
        "diffusionTileWidth":1024,
        "diffusionTileHeight":1024,
        "diffusionTileOverlap":128,
        "upscaler":null,
        "upscalerScaleFactor":0,
        "imageGuidanceScale":1.5,
        "seedMode":2,
        "clipSkip":$clipSkip,
        "controls":[],
        "loras":[],
        "maskBlur":1.5,
        "maskBlurOutset":0,
        "sharpness":0.0,
        "faceRestoration":null,
        "clipWeight":1.0,
        "negativePromptForImagePrior":true,
        "imagePriorSteps":5,
        "refinerModel":null,
        "originalImageHeight":0,
        "originalImageWidth":0,
        "cropTop":0,
        "cropLeft":0,
        "targetImageHeight":0,
        "targetImageWidth":0,
        "aestheticScore":6.0,
        "negativeAestheticScore":2.5,
        "zeroNegativePrompt":false,
        "refinerStart":0.85,
        "negativeOriginalImageHeight":0,
        "negativeOriginalImageWidth":0,
        "batchCount":1,
        "batchSize":1,
        "numFrames":14,
        "fps":5,
        "motionScale":127,
        "guidingFrameNoise":0.02,
        "startFrameGuidance":1.0,
        "shift":$shift,
        "stage2Steps":10,
        "stage2Guidance":1.0,
        "stage2Shift":1.0,
        "stochasticSamplingGamma":0.3,
        "preserveOriginalAfterInpaint":true,
        "t5TextEncoder":true,
        "separateClipL":false,
        "clipLText":null,
        "separateOpenClipG":false,
        "openClipGText":null,
        "speedUpWithGuidanceEmbed":$speedUpWithGuidanceEmbed,
        "guidanceEmbed":3.5,
        "resolutionDependentShift":$resolutionDependentShift,
        "teaCache":false,
        "teaCacheStart":5,
        "teaCacheEnd":-1,
        "teaCacheThreshold":0.06,
        "teaCacheMaxSkipSteps":3,
        "separateT5":false,
        "t5Text":null,
        "causalInference":0,
        "causalInferencePad":0,
        "cfgZeroStar":false,
        "cfgZeroInitSteps":0,
        "compressionArtifacts":"disabled",
        "compressionArtifactsQuality":43.1
    }""".trimIndent()

    private fun validateExecution(
        profile: LocalVideoProfileDocument,
        request: LocalVideoProbeRequest,
        execution: LocalVideoProbeExecution,
        outputDirectory: Path,
    ) {
        require(profile.backend.release != null && profile.backend.executableSha256 != null &&
            profile.backend.sourceRevision != null) {
            "Bundled backend is not pinned for execution"
        }
        require(execution.toolRelease == profile.backend.release) {
            "Tool release '${execution.toolRelease}' does not match pinned release '${profile.backend.release}'"
        }
        require(execution.sourceRevision == profile.backend.sourceRevision) {
            "Tool source revision '${execution.sourceRevision}' does not match pinned revision '${profile.backend.sourceRevision}'"
        }
        require(request.tool.sha256 == profile.backend.executableSha256) {
            "Tool digest does not match the released executable pinned by the profile"
        }
        require(execution.timeoutSecondsPerStage in 1..MAX_STAGE_SECONDS) {
            "timeoutSecondsPerStage must be within 1..$MAX_STAGE_SECONDS"
        }
        require(execution.maxStdoutBytes in 64..4_194_304 && execution.maxStderrBytes in 64..4_194_304) {
            "stdout/stderr limits must each be within 64..4194304 bytes"
        }
        execution.cancellationFile?.let { raw ->
            val path = absoluteNormalized(raw, "cancellationFile")
            require(!Files.exists(path, NOFOLLOW_LINKS)) {
                "cancellationFile must be absent before the probe starts: $path"
            }
            requireNearestExistingParentIsDirectory(path)
            val canonicalCancellation = canonicalFuturePath(path)
            val canonicalOutput = canonicalFuturePath(outputDirectory)
            require(!hasResolvedPrefix(canonicalCancellation, canonicalOutput, portableNames = true) &&
                !hasResolvedPrefix(canonicalOutput, canonicalCancellation, portableNames = true)) {
                "cancellationFile must not contain or be contained by outputDirectory"
            }
        }
        val modelsDirectory = absoluteNormalized(execution.modelsDirectory, "modelsDirectory")
        require(!Files.isSymbolicLink(modelsDirectory) && Files.isDirectory(modelsDirectory, NOFOLLOW_LINKS)) {
            "modelsDirectory must be an existing non-symbolic-link directory: $modelsDirectory"
        }
        require(request.profiles.size == 1) {
            "The pinned released CLI route runs one profile at a time; use a separate preserved request for an alternative"
        }
        val requestedProfile = request.profiles.single()
        val candidate = profile.videoProfiles.singleOrNull { it.id == requestedProfile.profileId }
            ?: error("Unknown executable profile '${requestedProfile.profileId}'")
        val required = candidate.requiredModels.associateBy(LocalVideoRequiredModel::id)
        val supplied = requestedProfile.modelPins.associateBy(PinnedLocalFile::id)
        require(required.keys == supplied.keys) {
            "Real probe must pin the complete model bundle: expected ${required.keys.sorted()}, found ${supplied.keys.sorted()}"
        }
        required.forEach { (id, model) ->
            val pin = checkNotNull(supplied[id])
            val path = Path.of(pin.path)
            require(path.parent == modelsDirectory && path.fileName.toString() == model.fileName) {
                "Model '$id' must be the direct modelsDirectory file '${model.fileName}'"
            }
            require(pin.sha256 == model.sha256 && Files.size(path) == model.bytes) {
                "Model '$id' does not match the pinned ${model.quantization} artifact"
            }
        }
        require(requestedProfile.referenceBindings.size == 1) {
            "Released Draw Things CLI supports exactly one pixel-conditioned --image; multiple reference roles are unsupported"
        }
        val referenceId = requestedProfile.referenceBindings.single()
        val keyframe = requireNotNull(requestedProfile.keyframe) {
            "Real probe requires a FLUX keyframe stage"
        }
        val video = requireNotNull(requestedProfile.video) {
            "Real probe requires an LTX-2.3 I2V stage"
        }
        require(keyframe.referenceBinding == referenceId) {
            "The keyframe input must be the profile's one bound reference '$referenceId'"
        }
        require(keyframe.modelPinId == candidate.keyframeModelId && video.modelPinId == candidate.videoModelId) {
            "Real probe must use pinned FLUX keyframe model '${candidate.keyframeModelId}' and LTX video model '${candidate.videoModelId}'"
        }
        require(keyframe.sampler == 16 && keyframe.shift == 3.0 && !keyframe.resolutionDependentShift &&
            keyframe.clipSkip == 2 && keyframe.speedUpWithGuidanceEmbed) {
            "FLUX keyframe must use the pinned released config baseline: sampler 16, shift 3, fixed shift, clipSkip 2"
        }
        require(video.sampler == 19 && video.shift == 5.0 && !video.resolutionDependentShift &&
            video.clipSkip == 1 && video.speedUpWithGuidanceEmbed) {
            "LTX video must use the pinned released config baseline: sampler 19, shift 5, fixed shift, clipSkip 1"
        }
        validateImageSettings(
            keyframe.width,
            keyframe.height,
            keyframe.steps,
            keyframe.cfg,
            keyframe.strength,
            keyframe.sampler,
            keyframe.shift,
        )
        validateImageSettings(
            video.width,
            video.height,
            video.steps,
            video.cfg,
            video.strength,
            video.sampler,
            video.shift,
        )
        require(video.frames in 1..249 && video.frames % 8 == 1 && video.expectedFramesPerSecond == 25.0) {
            "LTX-2.3 probe frames must be 1 + 8n within 1..249 and pin the released CLI cadence at 25 fps"
        }
        val requestedSeconds = video.frames / video.expectedFramesPerSecond
        require(requestedSeconds in 5.0..10.0) {
            "LTX-2.3 probe must request 5..10 seconds; found $requestedSeconds seconds"
        }
        require(requestedProfile.takes.all { it.seed != null }) {
            "Every real probe take requires an explicit seed"
        }
        require(keyframe.seed in 0..UInt.MAX_VALUE.toLong() &&
            requestedProfile.takes.all { checkNotNull(it.seed) in 0..UInt.MAX_VALUE.toLong() }) {
            "Draw Things seeds must fit the released CLI UInt32 range"
        }
        val allOutputs = listOf(Path.of(request.reportPath), Path.of(keyframe.outputPath)) +
            requestedProfile.takes.map { Path.of(it.outputPath) }
        require(allOutputs.all { it.parent == outputDirectory }) {
            "Real probe report, keyframe and take outputs must be direct children of outputDirectory"
        }
        require(Path.of(keyframe.outputPath).fileName.toString().lowercase().endsWith(".png")) {
            "Keyframe output must use .png"
        }
        require(requestedProfile.takes.all { it.outputPath.lowercase().endsWith(".mp4") }) {
            "Video take outputs must use .mp4"
        }
        val internalNames = buildList {
            add(".models")
            add(".model-staging.json")
            add(".work-${requestedProfile.profileId}-keyframe")
            requestedProfile.takes.forEach { add(".work-${requestedProfile.profileId}-${it.id}") }
        }
        val outputNames = allOutputs.map { it.fileName.toString() }
        require(internalNames.none { internal -> outputNames.any { it.equals(internal, ignoreCase = true) } }) {
            "Output names collide with private probe working directories"
        }
    }

    private fun validateImageSettings(
        width: Int,
        height: Int,
        steps: Int,
        cfg: Double,
        strength: Double,
        sampler: Int = 18,
        shift: Double = 1.0,
    ) {
        require(width in 256..2048 && width % 64 == 0 && height in 256..2048 && height % 64 == 0) {
            "Probe width and height must be multiples of 64 within 256..2048"
        }
        require(steps in 1..100) { "Probe steps must be within 1..100" }
        require(cfg in 0.0..30.0) { "Probe CFG must be within 0..30" }
        require(strength in 0.0..1.0) { "Probe strength must be within 0..1" }
        require(sampler in 0..19) { "Probe sampler raw value must match the pinned CLI range 0..19" }
        require(shift.isFinite() && shift in -100.0..100.0) { "Probe shift must be finite and within -100..100" }
    }

    private fun usefulMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName

    private fun validateProfile(profile: LocalVideoProfileDocument) {
        require(profile.schema == PROFILE_SCHEMA && profile.version == SCHEMA_VERSION) {
            "Unsupported local video profile schema/version '${profile.schema}' v${profile.version}; expected $PROFILE_SCHEMA v$SCHEMA_VERSION"
        }
        requireToken(profile.id, "Profile id")
        require(profile.backend.id == SUPPORTED_BACKEND_ID) {
            "Unsupported local backend '${profile.backend.id}'; this candidate supports only '$SUPPORTED_BACKEND_ID'"
        }
        require(profile.backend.label.isNotBlank()) { "Backend label must not be blank" }
        profile.backend.executableSha256?.let { requireHash(it, "Backend executable SHA-256") }
        profile.backend.sourceRevision?.let {
            require(Regex("[0-9a-f]{40}").matches(it)) { "Backend source revision must be a 40-character lowercase Git SHA" }
        }
        require(profile.backend.maxInputImagesPerInvocation == 1 && !profile.backend.supportsMultipleReferenceRoles) {
            "Released Draw Things CLI capability must remain the measured one-image/no-role-input boundary"
        }
        require(listOf(profile.backend.release, profile.backend.executableSha256, profile.backend.sourceRevision)
            .all { it != null } || listOf(
            profile.backend.release,
            profile.backend.executableSha256,
            profile.backend.sourceRevision,
        ).all { it == null }) {
            "Backend release, executable digest and source revision must be pinned together"
        }
        require(profile.limits.maxVideoProfiles in 1..MAX_VIDEO_PROFILES &&
            profile.limits.maxTakesPerProfile in 1..MAX_TAKES_PER_PROFILE) {
            "Profile limits must allow at least one and at most $MAX_VIDEO_PROFILES video profiles / $MAX_TAKES_PER_PROFILE takes"
        }
        require(profile.videoProfiles.isNotEmpty() && profile.videoProfiles.size <= MAX_VIDEO_PROFILES) {
            "Profile must declare between one and $MAX_VIDEO_PROFILES candidate video profiles"
        }
        require(profile.videoProfiles.map(LocalVideoProfileCandidate::id).distinct().size == profile.videoProfiles.size) {
            "Candidate video profile ids must be unique"
        }
        validateEvidence(profile.evidence, "Local profile")
        profile.videoProfiles.forEach { candidate ->
            requireToken(candidate.id, "Candidate video profile id")
            require(candidate.label.isNotBlank()) { "Candidate video profile label must not be blank" }
            requireToken(candidate.keyframeModelId, "Keyframe model id")
            requireToken(candidate.videoModelId, "Video model id")
            require(candidate.requiredModels.isNotEmpty() &&
                candidate.requiredModels.map(LocalVideoRequiredModel::id).distinct().size == candidate.requiredModels.size) {
                "Candidate video profile '${candidate.id}' must declare a unique required model bundle"
            }
            candidate.requiredModels.forEach { model ->
                requireToken(model.id, "Required model id")
                require(model.fileName == model.id) { "Required model id must equal its exact filename" }
                requireHash(model.sha256, "Required model SHA-256")
                require(model.bytes > 0) { "Required model byte count must be positive" }
                require(model.quantization.isNotBlank()) { "Required model quantization must not be blank" }
            }
            require(candidate.requiredModels.any { it.id == candidate.keyframeModelId } &&
                candidate.requiredModels.any { it.id == candidate.videoModelId }) {
                "Candidate stage models must be present in its required model bundle"
            }
            validateEvidence(candidate.evidence, "Candidate video profile '${candidate.id}'")
        }
    }

    private fun validateEvidence(evidence: LocalVideoEvidence, owner: String) {
        when (evidence.status) {
            LocalVideoEvidenceStatus.CANDIDATE_UNVERIFIED -> require(
                evidence.measurementRecord == null && evidence.selectionRecord == null,
            ) { "$owner is candidate/unverified and cannot contain measured or selected facts" }
            LocalVideoEvidenceStatus.MEASURED -> require(
                evidence.measurementRecord != null && evidence.selectionRecord == null,
            ) { "$owner measured evidence requires only a pinned measurement record" }
            LocalVideoEvidenceStatus.SELECTED -> require(
                evidence.measurementRecord != null && evidence.selectionRecord != null,
            ) { "$owner selected evidence requires pinned measurement and selection records" }
        }
        evidence.measurementRecord?.let { validateEvidencePin(it, "$owner measurement record") }
        evidence.selectionRecord?.let { validateEvidencePin(it, "$owner selection record") }
    }

    private fun validateEvidencePin(pin: EvidenceRecordPin, label: String) {
        require(pin.id.isNotBlank()) { "$label id must not be blank" }
        requireHash(pin.sha256, "$label SHA-256")
    }

    private fun validateRequestShape(request: LocalVideoProbeRequest) {
        require(request.schema == REQUEST_SCHEMA && request.version == SCHEMA_VERSION) {
            "Unsupported local video probe request schema/version '${request.schema}' v${request.version}; expected $REQUEST_SCHEMA v$SCHEMA_VERSION"
        }
        requireToken(request.profileId, "profileId")
        require(request.backendId == SUPPORTED_BACKEND_ID) {
            "Unsupported local backend '${request.backendId}'; use '$SUPPORTED_BACKEND_ID' for this candidate"
        }
        require(request.prompt.isNotBlank() && request.prompt.length <= MAX_PROMPT_CHARACTERS) {
            "Prompt must contain 1..$MAX_PROMPT_CHARACTERS characters"
        }
        require(request.profiles.isNotEmpty() && request.profiles.size <= MAX_VIDEO_PROFILES) {
            "Probe request must contain between one and $MAX_VIDEO_PROFILES video profiles"
        }
        require(request.profiles.map(LocalVideoProbeProfile::profileId).distinct().size == request.profiles.size) {
            "Each candidate video profile may appear only once"
        }
        require(request.references.isNotEmpty()) { "Probe request requires at least one pinned reference" }
        require(request.references.map(LocalVideoReferencePin::id).distinct().size == request.references.size) {
            "Reference ids must be unique"
        }
        request.references.forEach { reference ->
            requireToken(reference.id, "Reference id")
        }
        val referenceIds = request.references.mapTo(mutableSetOf(), LocalVideoReferencePin::id)
        request.profiles.forEach { profile ->
            requireToken(profile.profileId, "Probe video profile id")
            require(profile.modelPins.isNotEmpty()) { "Video profile '${profile.profileId}' requires at least one pinned model artifact" }
            require(profile.modelPins.map(PinnedLocalFile::id).distinct().size == profile.modelPins.size) {
                "Model pin ids must be unique within video profile '${profile.profileId}'"
            }
            val modelIds = profile.modelPins.mapTo(mutableSetOf(), PinnedLocalFile::id)
            require(profile.takes.isNotEmpty() && profile.takes.size <= MAX_TAKES_PER_PROFILE) {
                "Video profile '${profile.profileId}' must request between one and $MAX_TAKES_PER_PROFILE takes"
            }
            require(profile.takes.map(LocalVideoProbeTake::id).distinct().size == profile.takes.size) {
                "Take ids must be unique within video profile '${profile.profileId}'"
            }
            profile.takes.forEach { requireToken(it.id, "Take id") }
            require(profile.referenceBindings.distinct().size == profile.referenceBindings.size) {
                "Reference bindings must be unique within video profile '${profile.profileId}'"
            }
            require(profile.referenceBindings.all(referenceIds::contains)) {
                "Video profile '${profile.profileId}' contains an unknown reference binding"
            }
            profile.keyframe?.let { keyframe ->
                require(keyframe.referenceBinding in referenceIds) {
                    "Video profile '${profile.profileId}' keyframe contains an unknown reference binding"
                }
                require(keyframe.modelPinId in modelIds) {
                    "Video profile '${profile.profileId}' keyframe contains an unknown model pin"
                }
            }
            profile.video?.let { video ->
                require(video.modelPinId in modelIds) {
                    "Video profile '${profile.profileId}' video stage contains an unknown model pin"
                }
            }
        }
    }

    private fun validatePin(pin: PinnedLocalFile, label: String): ValidatedLocalPin {
        requireToken(pin.id, "$label id")
        requireHash(pin.sha256, "$label SHA-256")
        val path = absoluteNormalized(pin.path, "$label path")
        require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            "$label pin is missing or is not a regular non-symbolic-link file: $path"
        }
        val actual = sha256(path)
        require(actual == pin.sha256) {
            "$label pin mismatch for '$path': expected ${pin.sha256}, found $actual"
        }
        return ValidatedLocalPin(pin.id, path)
    }

    private fun absoluteNormalized(raw: String, label: String): Path {
        val path = try {
            Path.of(raw)
        } catch (error: Exception) {
            throw IllegalArgumentException("$label is not a valid path: ${error.message}", error)
        }
        require(path.isAbsolute) { "$label must be absolute: $raw" }
        // Lexical normalization before following symlinks can change the named input or output.
        // Reject traversal components instead of validating a different path from the request.
        require(path.none { it.toString() == "." || it.toString() == ".." }) {
            "$label must be normalized without '.' or '..' path components: $raw"
        }
        return path
    }

    private fun requireNearestExistingParentIsDirectory(path: Path) {
        var ancestor = path.parent
        while (ancestor != null && !Files.exists(ancestor, NOFOLLOW_LINKS)) ancestor = ancestor.parent
        require(ancestor != null && Files.isDirectory(ancestor, NOFOLLOW_LINKS)) {
            "Output path has no existing directory ancestor: $path"
        }
    }

    private fun canonicalFuturePath(path: Path): Path {
        var existing = path
        val missing = ArrayDeque<String>()
        while (!Files.exists(existing, NOFOLLOW_LINKS)) {
            val name = existing.fileName.toString()
            require(Regex("[A-Za-z0-9._-]+").matches(name)) {
                "Absent output path components must use portable ASCII letters, digits, dot, underscore or hyphen: $path"
            }
            missing.addFirst(name)
            existing = checkNotNull(existing.parent) { "Path has no existing ancestor: $path" }
        }
        var canonical = existing.toRealPath()
        missing.forEach { canonical = canonical.resolve(it) }
        return canonical.normalize()
    }

    /**
     * Existing prefixes use native file identity when their spelling differs. Missing components
     * are restricted to ASCII above, so collision checks can conservatively ignore case without
     * guessing a volume's Unicode rules. Containment keeps case-sensitive names distinct unless
     * native identity proves an alias; otherwise folding could admit a symlink escape on a
     * case-sensitive volume. No check creates a filesystem probe or rewrites the requested paths.
     */
    private fun hasResolvedPrefix(path: Path, prefix: Path, portableNames: Boolean = false): Boolean {
        if (path.root != prefix.root || path.nameCount < prefix.nameCount) return false
        var pathPart = path.root
        var prefixPart = prefix.root
        for (index in 0 until prefix.nameCount) {
            val pathName = path.getName(index).toString()
            val prefixName = prefix.getName(index).toString()
            pathPart = pathPart.resolve(pathName)
            prefixPart = prefixPart.resolve(prefixName)
            if (pathName == prefixName || (portableNames && pathName.equals(prefixName, ignoreCase = true))) continue
            if (!Files.exists(pathPart, NOFOLLOW_LINKS) || !Files.exists(prefixPart, NOFOLLOW_LINKS) ||
                !Files.isSameFile(pathPart, prefixPart)) return false
        }
        return true
    }

    private fun requireToken(value: String, label: String) {
        require(Regex("[a-z0-9][a-z0-9._-]{0,127}").matches(value)) {
            "$label must be a lowercase stable token"
        }
    }

    private fun requireHash(value: String, label: String) {
        require(Regex("[0-9a-f]{64}").matches(value)) { "$label must be 64 lowercase hexadecimal characters" }
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

    private inline fun <T> decode(label: String, block: () -> T): T = try {
        block()
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid $label JSON: ${error.message ?: error.javaClass.simpleName}", error)
    }

    private data class ValidatedLocalPin(val id: String, val path: Path)
}

@Serializable
data class LocalVideoProfileDocument(
    val schema: String,
    val version: Int,
    val id: String,
    val backend: LocalVideoBackendCandidate,
    val evidence: LocalVideoEvidence,
    val videoProfiles: List<LocalVideoProfileCandidate>,
    val limits: LocalVideoProbeLimits,
)

@Serializable
data class LocalVideoBackendCandidate(
    val id: String,
    val label: String,
    val release: String? = null,
    val executableSha256: String? = null,
    val sourceRevision: String? = null,
    val maxInputImagesPerInvocation: Int = 1,
    val supportsMultipleReferenceRoles: Boolean = false,
)

@Serializable
data class LocalVideoProfileCandidate(
    val id: String,
    val label: String,
    val evidence: LocalVideoEvidence,
    val keyframeModelId: String = "",
    val videoModelId: String = "",
    val requiredModels: List<LocalVideoRequiredModel> = emptyList(),
)

@Serializable
data class LocalVideoRequiredModel(
    val id: String,
    val fileName: String,
    val sha256: String,
    val bytes: Long,
    val quantization: String,
    val purpose: String,
    val terms: String,
)

@Serializable
data class LocalVideoEvidence(
    val status: LocalVideoEvidenceStatus,
    val measurementRecord: EvidenceRecordPin? = null,
    val selectionRecord: EvidenceRecordPin? = null,
)

@Serializable
enum class LocalVideoEvidenceStatus { CANDIDATE_UNVERIFIED, MEASURED, SELECTED }

@Serializable
data class EvidenceRecordPin(val id: String, val sha256: String)

@Serializable
data class LocalVideoProbeLimits(val maxVideoProfiles: Int, val maxTakesPerProfile: Int)

@Serializable
data class LocalVideoProbeRequest(
    val schema: String,
    val version: Int,
    val profileId: String,
    val backendId: String,
    val tool: PinnedLocalFile,
    val prompt: String,
    val references: List<LocalVideoReferencePin> = emptyList(),
    val profiles: List<LocalVideoProbeProfile>,
    val outputDirectory: String,
    val reportPath: String,
    val execution: LocalVideoProbeExecution? = null,
)

@Serializable
data class LocalVideoProbeExecution(
    val toolRelease: String,
    val sourceRevision: String,
    val modelsDirectory: String,
    val timeoutSecondsPerStage: Long,
    val maxStdoutBytes: Int = 1_048_576,
    val maxStderrBytes: Int = 1_048_576,
    val cancellationFile: String? = null,
)

@Serializable
data class PinnedLocalFile(val id: String, val path: String, val sha256: String)

@Serializable
data class LocalVideoReferencePin(
    val id: String,
    val pin: PinnedLocalFile,
    val role: LocalVideoReferenceRole? = null,
)

@Serializable
enum class LocalVideoReferenceRole { SUBJECT, CHARACTER, ENVIRONMENT, STYLE, COMPLETE_SCENE }

@Serializable
data class LocalVideoProbeProfile(
    val profileId: String,
    val modelPins: List<PinnedLocalFile>,
    val referenceBindings: List<String> = emptyList(),
    val takes: List<LocalVideoProbeTake>,
    val keyframe: LocalVideoKeyframeProbe? = null,
    val video: LocalVideoProbeVideo? = null,
)

@Serializable
data class LocalVideoKeyframeProbe(
    val modelPinId: String,
    val referenceBinding: String,
    val outputPath: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val cfg: Double,
    val strength: Double,
    val seed: Long,
    val sampler: Int = 16,
    val shift: Double = 3.0,
    val resolutionDependentShift: Boolean = false,
    val clipSkip: Int = 2,
    val speedUpWithGuidanceEmbed: Boolean = true,
)

@Serializable
data class LocalVideoProbeVideo(
    val modelPinId: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val expectedFramesPerSecond: Double,
    val steps: Int,
    val cfg: Double,
    val strength: Double,
    val sampler: Int = 19,
    val shift: Double = 5.0,
    val resolutionDependentShift: Boolean = false,
    val clipSkip: Int = 1,
    val speedUpWithGuidanceEmbed: Boolean = true,
)

@Serializable
data class LocalVideoProbeTake(val id: String, val outputPath: String, val seed: Long? = null)

@Serializable
enum class LocalVideoProbeStatus { NOT_RUN }

@Serializable
data class LocalVideoProbePreparationReport(
    val status: LocalVideoProbeStatus,
    val reason: String,
    val profileId: String,
    val profileEvidence: LocalVideoEvidence,
    val backendId: String,
    val validatedInputPins: Int,
    val preparedVideoProfiles: Int,
    val preparedTakes: Int,
    val outputLocations: List<String>,
    val measurements: LocalVideoProbeMeasurements? = null,
    val selectedProfileId: String? = null,
)

/** Reserved for real V11 evidence. V11a always reports this field as null. */
@Serializable
data class LocalVideoProbeMeasurements(
    val measurementRecord: EvidenceRecordPin,
    val coldLoadMillis: Long,
    val generatedVideoMillis: Long,
    val wallClockMillis: Long,
    val peakMemoryBytes: Long,
    val peakSwapBytes: Long,
)

@Serializable
enum class LocalVideoProbeRunStatus { COMPLETED_UNREVIEWED, FAILED }

@Serializable
enum class LocalVideoReferenceDisposition { REQUESTED_PIXEL_BINDING, PIXEL_CONDITIONED, NOT_CONSUMED }

@Serializable
data class LocalVideoReferenceUsage(
    val id: String,
    val role: LocalVideoReferenceRole?,
    val path: String,
    val sha256: String,
    val disposition: LocalVideoReferenceDisposition,
)

@Serializable
enum class LocalVideoProbeStageStatus { SUCCEEDED, FAILED }

@Serializable
enum class LocalVideoArtifactKind { PNG_KEYFRAME, MP4_VIDEO }

@Serializable
data class LocalVideoOutputArtifact(
    val kind: LocalVideoArtifactKind,
    val path: String,
    val bytes: Long,
    val sha256: String,
    val requestedFrames: Int? = null,
    val expectedFramesPerSecond: Double? = null,
    val requestedDurationMillis: Long? = null,
)

@Serializable
data class LocalVideoProbeStageFailure(
    val type: String,
    val message: String,
    val exitCode: Int? = null,
    val signal: Int? = null,
)

@Serializable
data class LocalVideoRecordedOutput(
    val text: String,
    val totalBytes: Long,
    val truncated: Boolean,
)

@Serializable
data class LocalVideoProbeStageReport(
    val id: String,
    val status: LocalVideoProbeStageStatus,
    val arguments: List<String>,
    val elapsedMillis: Long,
    val stdout: LocalVideoRecordedOutput,
    val stderr: LocalVideoRecordedOutput,
    val failure: LocalVideoProbeStageFailure? = null,
    val output: LocalVideoOutputArtifact? = null,
)

@Serializable
enum class LocalVideoMeasurementStatus { NOT_AVAILABLE, STAGE_WALL_TIME_ONLY }

@Serializable
data class LocalVideoHostMeasurements(
    val processElapsedMillis: Long,
    val coldLoadMillis: Long?,
    val warmLoadMillis: Long?,
    val peakMemoryBytes: Long?,
    val peakSwapBytes: Long?,
    val status: LocalVideoMeasurementStatus,
)

@Serializable
enum class LocalVideoMediaInspectionStatus { PENDING_EXTERNAL_INSPECTION }

@Serializable
data class LocalVideoMediaInspection(
    val status: LocalVideoMediaInspectionStatus,
    val actualDurationMillis: Long?,
    val actualFramesPerSecond: Double?,
    val actualFrameCount: Long?,
    val audioStreamPresent: Boolean?,
)

@Serializable
enum class LocalVideoProbeRecommendation {
    DO_NOT_SELECT_RELEASED_CLI_FOR_MULTI_REFERENCE_WITHOUT_FURTHER_PROOF,
}

@Serializable
data class LocalVideoProbeRunReport(
    val schema: String = LocalVideoProfileBoundary.REPORT_SCHEMA,
    val version: Int = LocalVideoProfileBoundary.SCHEMA_VERSION,
    val status: LocalVideoProbeRunStatus,
    val reason: String,
    val profileId: String,
    val backendId: String,
    val backendRelease: String,
    val backendSourceRevision: String,
    val tool: PinnedLocalFile,
    val modelPins: List<PinnedLocalFile>,
    val referenceUsage: List<LocalVideoReferenceUsage>,
    val offline: Boolean,
    val downloadMissing: Boolean,
    val stages: List<LocalVideoProbeStageReport>,
    val selectedProfileId: String?,
    val measurements: LocalVideoHostMeasurements,
    val mediaInspection: LocalVideoMediaInspection,
    val recommendation: LocalVideoProbeRecommendation,
    val limitations: List<String>,
    val modelWorkspace: LocalVideoModelWorkspaceReport? = null,
)

/** V11 per-probe scratch bundle, retained as evidence; this is not a reusable model cache. */
internal class LocalVideoModelWorkspace(
    private val outputDirectory: Path,
    private val pins: List<PinnedLocalFile>,
    private val cancellationFile: Path?,
    private val usableSpace: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) {
    val directory: Path = outputDirectory.resolve(".models")
    private val copies = mutableListOf<LocalVideoModelCopy>()
    private var copyElapsedMillis = 0L
    private var sourceBytes = 0L
    private var requiredFreeBytes = 0L
    private var initialFreeBytes: Long? = null

    fun stage() {
        val start = System.nanoTime()
        try {
            require(pins.isNotEmpty()) { "Cannot stage an empty model bundle" }
            val names = pins.map { Path.of(it.path).fileName.toString() }
            require(names.distinct().size == names.size) { "Staged model filenames must be distinct" }
            sourceBytes = pins.fold(0L) { total, pin -> Math.addExact(total, Files.size(Path.of(pin.path))) }
            // Full copy + potential tensor sidecars/SQLite rewrite, with 4 GiB left for output/headroom.
            requiredFreeBytes = Math.addExact(Math.multiplyExact(sourceBytes, 2L), 4L * 1024 * 1024 * 1024)
            initialFreeBytes = usableSpace(outputDirectory)
            require(checkNotNull(initialFreeBytes) >= requiredFreeBytes) {
                "Model staging needs $requiredFreeBytes free bytes (two bundles plus 4 GiB); found $initialFreeBytes"
            }
            checkCancellation()
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
            // ModelZoo prefers external files but otherwise falls back to ~/Documents/Models.
            // Explicit empty registries prevent installed custom specifications/embeddings changing dependencies.
            for (name in listOf("custom.json", "custom_textual_inversions.json", "custom_lora.json")) {
                Files.writeString(directory.resolve(name), "[]\n", CREATE_NEW, WRITE)
            }
            Files.writeString(outputDirectory.resolve(".model-staging.json"), Json { encodeDefaults = true }.encodeToString(
                LocalVideoModelStagingManifest(pins = pins, sourceBytes = sourceBytes, requiredFreeBytes = requiredFreeBytes),
            ), CREATE_NEW, WRITE)
            val buffer = ByteArray(1024 * 1024)
            for (pin in pins) {
                checkCancellation()
                val source = Path.of(pin.path)
                require(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "Model source is not a regular file: $source" }
                val expectedBytes = Files.size(source)
                val destination = directory.resolve(source.fileName)
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                // CREATE_NEW and streaming bytes produce an independent inode, never a hardlink/symlink.
                Files.newInputStream(source, NOFOLLOW_LINKS).use { input ->
                    Files.newOutputStream(destination, CREATE_NEW, WRITE).use { output ->
                        while (true) {
                            checkCancellation()
                            if (copied % (64L * 1024 * 1024) == 0L) {
                                require(usableSpace(directory) >= 4L * 1024 * 1024 * 1024) {
                                    "Model staging reached 4 GiB free-disk reserve; partial copies preserved"
                                }
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied = Math.addExact(copied, read.toLong())
                            require(copied <= expectedBytes) { "Model source grew during staging: $source" }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                    }
                }
                Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-------"))
                val copiedHash = digest.digest().joinToString("") { "%02x".format(it) }
                require(copied == expectedBytes && copiedHash == pin.sha256) { "Model pin changed during staging: $source" }
                val stagedHash = hash(destination)
                require(stagedHash == pin.sha256 && !Files.isSameFile(source, destination)) {
                    "Staged model must be an independent verified copy: $destination"
                }
                copies += LocalVideoModelCopy(pin, destination.toString(), copied, stagedHash)
            }
            checkCancellation()
        } finally {
            copyElapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis()
        }
    }

    /** Metadata-only watchdog; cancels rather than growing an unbounded private model store. */
    fun resourceIssue(): String? = try {
        require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Private model directory was replaced" }
        require(usableSpace(directory) >= 4L * 1024 * 1024 * 1024) { "Private model job reached 4 GiB free-disk reserve" }
        val entries = Files.list(directory).use { it.limit(65).toList() }
        require(entries.size <= 64) { "Private model artifact count exceeds 64" }
        var bytes = 0L
        for (entry in entries) {
            // SQLite transient journals may vanish between listing and stat.
            if (!Files.exists(entry, NOFOLLOW_LINKS)) continue
            require(Files.isRegularFile(entry, NOFOLLOW_LINKS)) { "Unexpected non-regular model artifact: $entry" }
            try {
                bytes = Math.addExact(bytes, Files.size(entry))
            } catch (_: java.nio.file.NoSuchFileException) {
                continue
            }
        }
        require(bytes <= requiredFreeBytes) { "Private model files exceeded $requiredFreeBytes logical bytes" }
        null
    } catch (error: Exception) {
        error.message ?: error.javaClass.simpleName
    }

    fun requireReady() {
        checkCancellation()
        for (pin in pins) {
            val staged = directory.resolve(Path.of(pin.path).fileName)
            require(Files.isRegularFile(staged, NOFOLLOW_LINKS) && !Files.isSameFile(staged, Path.of(pin.path))) {
                "Missing or unsafe staged model; refusing ModelZoo fallback: $staged"
            }
        }
        for (name in listOf("custom.json", "custom_textual_inversions.json", "custom_lora.json")) {
            val registry = directory.resolve(name)
            require(Files.isRegularFile(registry, NOFOLLOW_LINKS) && Files.size(registry) == 3L &&
                Files.readString(registry) == "[]\n") { "Private model registry was changed: $registry" }
        }
    }

    /** Always run after process ownership has settled, including failed/cancelled runs. Never removes files. */
    fun inspect(): LocalVideoModelWorkspaceReport {
        val started = System.nanoTime()
        val artifacts = mutableListOf<LocalVideoDerivedModelArtifact>()
        val failures = mutableListOf<String>()
        val sourceHashes = pins.map { pin ->
            val actual: String? = try {
                hash(Path.of(pin.path))
            } catch (error: Exception) {
                failures += "Could not inspect source '${pin.id}': ${error.message}"
                null
            }
            if (actual != pin.sha256) failures += "Installed source pin changed: ${pin.id}"
            LocalVideoSourceModelAudit(pin.id, actual, actual == pin.sha256)
        }
        try {
            if (Files.exists(directory, NOFOLLOW_LINKS)) {
                require(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "Private model directory was replaced" }
                val entries = Files.list(directory).use { it.limit(65).toList() }
                require(entries.size <= 64) { "Private model artifact count exceeds 64" }
                var total = 0L
                for (entry in entries.sortedBy { it.fileName.toString() }) {
                    require(Files.isRegularFile(entry, NOFOLLOW_LINKS)) { "Unexpected non-regular model artifact: $entry" }
                    val bytes = Files.size(entry)
                    total = Math.addExact(total, bytes)
                    require(total <= Math.addExact(Math.multiplyExact(sourceBytes, 2L), 4L * 1024 * 1024 * 1024)) {
                        "Derived model evidence exceeds the staging disk allowance"
                    }
                    val name = entry.fileName.toString()
                    val source = pins.singleOrNull { name == Path.of(it.path).fileName.toString() ||
                        name.startsWith(Path.of(it.path).fileName.toString() + "-") }
                    artifacts += LocalVideoDerivedModelArtifact(entry.toString(), bytes, hash(entry), source?.id)
                }
            }
        } catch (error: Exception) {
            failures += error.message ?: error.javaClass.simpleName
        }
        return LocalVideoModelWorkspaceReport(
            directory.toString(), "STREAM_COPY_1_MIB", sourceBytes, requiredFreeBytes, initialFreeBytes,
            copyElapsedMillis, Duration.ofNanos(System.nanoTime() - started).toMillis(), copies.toList(),
            sourceHashes, artifacts, failures.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
    }

    private fun checkCancellation() {
        check(!Thread.currentThread().isInterrupted &&
            (cancellationFile == null || !Files.exists(cancellationFile, NOFOLLOW_LINKS))) {
            "Model staging cancelled; partial owned copies preserved, no inference launched"
        }
    }

    private fun hash(path: Path): String {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Expected regular model evidence: $path" }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Serializable
internal data class LocalVideoModelStagingManifest(
    val pins: List<PinnedLocalFile>,
    val sourceBytes: Long,
    val requiredFreeBytes: Long,
    val method: String = "STREAM_COPY_1_MIB",
    val reuse: Boolean = false,
)

@Serializable
data class LocalVideoModelCopy(val source: PinnedLocalFile, val path: String, val bytes: Long, val initialSha256: String)

@Serializable
data class LocalVideoSourceModelAudit(val id: String, val actualSha256: String?, val matchesPin: Boolean)

@Serializable
data class LocalVideoDerivedModelArtifact(val path: String, val bytes: Long, val sha256: String, val sourceModelId: String?)

@Serializable
data class LocalVideoModelWorkspaceReport(
    val directory: String,
    val method: String,
    val sourceBytes: Long,
    val requiredFreeBytes: Long,
    val initialFreeBytes: Long?,
    val copyAndVerifyElapsedMillis: Long,
    val finalAuditElapsedMillis: Long,
    val copies: List<LocalVideoModelCopy>,
    val sourceAudit: List<LocalVideoSourceModelAudit>,
    val derivedArtifacts: List<LocalVideoDerivedModelArtifact>,
    val failure: String?,
)
