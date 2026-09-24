package app.melotrail.video.domain

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Durable, provider-neutral state for one admission domain. */
@Serializable
data class VideoJobLedger(
    val admissionDomainId: String,
    val createdAt: String,
    val jobs: List<VideoGenerationJob> = emptyList(),
    val revision: Long = 0L,
) {
    init {
        requireVideoJobId(admissionDomainId, "Admission domain")
        requireVideoJobTimestamp(createdAt, "Admission domain creation")
        require(revision >= 0L) { "Video job ledger revision must not be negative" }
        require(jobs.map { it.request.id }.distinct().size == jobs.size) { "Video request IDs must be unique" }
        require(jobs.map { it.request.requestFingerprint }.distinct().size == jobs.size) {
            "Video request fingerprints must be unique within an admission domain"
        }
        val attemptIds = jobs.flatMap { it.attempts }.map(VideoGenerationAttempt::id)
        val ownershipTokens = jobs.flatMap { it.attempts }.map(VideoGenerationAttempt::ownershipToken)
        val outputIds = jobs.flatMap { it.outputs }.map(VideoGenerationOutput::id)
        require(attemptIds.distinct().size == attemptIds.size) { "Video attempt IDs must be unique" }
        require(ownershipTokens.distinct().size == ownershipTokens.size) { "Video attempt ownership tokens must be unique" }
        require(outputIds.distinct().size == outputIds.size) { "Video output IDs must be unique" }
    }
}

@Serializable
data class VideoGenerationJob(
    val request: VideoGenerationJobRequest,
    val attempts: List<VideoGenerationAttempt> = emptyList(),
    val outputs: List<VideoGenerationOutput> = emptyList(),
    /** Set only by a completion for the newest attempt. Older late results remain inspectable. */
    val currentOutputId: String? = null,
) {
    init {
        require(attempts.size <= request.maximumAttempts) { "A video request exceeded its bounded attempt count" }
        require(attempts.all { it.requestId == request.id }) { "Every attempt must belong to its request" }
        require(attempts.map(VideoGenerationAttempt::number) == (1..attempts.size).toList()) {
            "Video attempt numbers must be contiguous and start at one"
        }
        val attemptIds = attempts.map(VideoGenerationAttempt::id).toSet()
        require(outputs.all { it.attemptId in attemptIds }) { "Every video output must belong to a persisted attempt" }
        require(outputs.map(VideoGenerationOutput::id).distinct().size == outputs.size) { "Video output IDs must be unique" }
        require(outputs.map(VideoGenerationOutput::attemptId).distinct().size == outputs.size) {
            "One video attempt can publish at most one immutable output"
        }
        require(outputs.all { output -> attempts.single { it.id == output.attemptId }.status == VideoGenerationAttemptStatus.SUCCEEDED }) {
            "Only a succeeded attempt can own an output"
        }
        require(currentOutputId == null || outputs.any { it.id == currentOutputId }) {
            "The current output must identify a persisted output"
        }
        require(currentOutputId == null || outputs.single { it.id == currentOutputId }.attemptId == attempts.last().id) {
            "Only the newest attempt can provide the current output"
        }
    }
}

@Serializable
data class VideoGenerationJobRequest(
    val id: String,
    val projectId: String,
    val backendId: String,
    val modelRequirements: List<VideoModelRequirement>,
    val input: VideoGenerationInput,
    val requestFingerprint: String,
    val maximumAttempts: Int,
    val createdAt: String,
    val execution: VideoExecutionPolicy,
) {
    init {
        requireVideoJobId(id, "Video request")
        requireVideoJobId(projectId, "Video project")
        requireVideoJobId(backendId, "Video backend")
        require(modelRequirements.map(VideoModelRequirement::id).distinct().size == modelRequirements.size) {
            "Video model requirement IDs must be unique"
        }
        requireVideoJobSha256(requestFingerprint, "Video request fingerprint")
        (input as? VideoControlledMotionGenerationInput)?.let { controlled ->
            require(controlled.motion.descriptor.projectId == projectId) { "Controlled descriptor belongs to another project" }
            require(execution is VideoLocalExecutionPolicy && controlled.media.execution == execution) {
                "Controlled media limits must match the durable local execution policy"
            }
        }
        require(maximumAttempts in 1..MAX_VIDEO_GENERATION_ATTEMPTS) {
            "Video generation must use 1..$MAX_VIDEO_GENERATION_ATTEMPTS attempts"
        }
        requireVideoJobTimestamp(createdAt, "Video request creation")
    }
}

@Serializable
sealed interface VideoGenerationInput {
    val prompt: String
    val dependencyPins: List<VideoGenerationDependencyPin>
    /** Persisted executable binding; the free-form prompt is never parsed as a graph or path. */
    val comfyWorkflow: VideoComfyWorkflowRequest?
    /** Null for existing keyframe/ComfyUI inputs; non-null bindings are compositor-only. */
    val controlledMotion: VideoControlledMotionRequest? get() = null
}

@Serializable
@SerialName("keyframe")
data class VideoKeyframeGenerationInput(
    override val prompt: String,
    override val dependencyPins: List<VideoGenerationDependencyPin>,
    val width: Int,
    val height: Int,
    override val comfyWorkflow: VideoComfyWorkflowRequest? = null,
) : VideoGenerationInput {
    init {
        requireVideoPrompt(prompt)
        requireVideoDependencyPins(dependencyPins)
        requireComfyDependencies(dependencyPins, comfyWorkflow)
        require(width in 64..8192 && height in 64..8192) { "Keyframe dimensions are outside supported orchestration bounds" }
    }
}

@Serializable
@SerialName("video")
data class VideoClipGenerationInput(
    override val prompt: String,
    override val dependencyPins: List<VideoGenerationDependencyPin>,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val framesPerSecond: Int,
    override val comfyWorkflow: VideoComfyWorkflowRequest? = null,
) : VideoGenerationInput {
    init {
        requireVideoPrompt(prompt)
        requireVideoDependencyPins(dependencyPins)
        requireComfyDependencies(dependencyPins, comfyWorkflow)
        require(durationMillis in 100L..60_000L) { "One generated video request must be 0.1..60 seconds" }
        require(width in 64..8192 && height in 64..8192) { "Video dimensions are outside supported orchestration bounds" }
        require(framesPerSecond in 1..120) { "Video frame rate is outside supported orchestration bounds" }
    }
}

@Serializable
@SerialName("controlled-motion")
data class VideoControlledMotionGenerationInput(
    override val prompt: String,
    override val dependencyPins: List<VideoGenerationDependencyPin>,
    val motion: VideoControlledMotionRequest,
    /** Exact user-authored motion text; [prompt] contains the distinct backend guidance. */
    val primaryPrompt: String,
    val media: VideoControlledMediaBinding,
) : VideoGenerationInput {
    override val comfyWorkflow: VideoComfyWorkflowRequest? = null
    override val controlledMotion: VideoControlledMotionRequest get() = motion

    init {
        requireVideoPrompt(prompt)
        requireVideoPrompt(primaryPrompt)
        requireVideoDependencyPins(dependencyPins)
        require(motion.descriptor.projectId.isNotBlank())
        require(motion.descriptor.fps == 30) { "Controlled preview encoding requires a 30 fps descriptor" }
        require(media.execution.memoryLimitBytes >= motion.descriptor.width.toLong() * motion.descriptor.height * 4L) {
            "Controlled memory admission cannot hold even one decoded frame"
        }
        require(media.maximumStagingBytes >= motion.endFrameExclusive - motion.startFrame &&
            media.maximumOutputBytes >= motion.endFrameExclusive - motion.startFrame) {
            "Controlled media limits cannot hold the requested frames"
        }
        require((motion.preparedPins + motion.descriptor.runtime.allPins).all { pin -> dependencyPins.any { it == pin } }) {
            "Every prepared and runtime motion pin must be included in the durable input pins"
        }
    }
}

/** Canonical controlled-motion identity. Length-prefixed UTF-8 fields prevent prompt/control
 * text from impersonating pin or range records; version changes invalidate older identities. */
fun controlledMotionRequestFingerprint(
    backendId: String,
    input: VideoControlledMotionGenerationInput,
    models: List<VideoModelRequirement>,
    maximumAttempts: Int,
): String {
    require(maximumAttempts in 1..MAX_VIDEO_GENERATION_ATTEMPTS) { "Controlled attempt limit is invalid" }
    val digest = MessageDigest.getInstance("SHA-256")
    fun number(value: Long) { digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()) }
    fun count(value: Int) { digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()) }
    fun field(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        count(encoded.size)
        digest.update(encoded)
    }
    fun pins(values: List<VideoGenerationDependencyPin>) {
        count(values.size)
        values.sortedBy { it.id }.forEach { pin ->
            field(pin.id)
            field(pin.sha256)
            // A null path must not alias an empty path even for an invalid persisted request.
            field(if (pin.ownedPath == null) "absent" else "present")
            pin.ownedPath?.let(::field)
        }
    }
    field("melotrail-controlled-motion-v6")
    field(backendId)
    count(maximumAttempts)
    field(input.prompt)
    field(input.primaryPrompt)
    field(input.media.encodingProfile)
    number(input.media.execution.wallClockLimitMillis)
    number(input.media.execution.memoryLimitBytes)
    number(input.media.execution.diskLimitBytes)
    number(input.media.maximumStagingBytes)
    number(input.media.maximumOutputBytes)
    number(input.media.minimumFreeDiskBytes)
    count(input.media.maximumConcurrentNativeProcesses)
    count(input.media.maximumBufferedFrames)
    pins(input.dependencyPins)
    number(input.motion.startFrame)
    number(input.motion.endFrameExclusive)
    number(input.motion.seed)
    field(input.motion.descriptor.projectId)
    field(input.motion.descriptor.sourceIdentity)
    field(input.motion.descriptor.requestJson)
    pins(input.motion.preparedPins)
    pins(input.motion.descriptor.runtime.allPins)
    field(input.motion.descriptor.runtime.expectedCanvasVersion)
    count(models.size)
    models.sortedBy { it.id }.forEach { model ->
        field(model.id)
        field(model.version)
        field(if (model.sha256 == null) "absent" else "present")
        model.sha256?.let(::field)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Immutable admission ceilings, not measured usage. The claimed media stage (VG2-01's
 * next slice) must enforce remaining whole-attempt time, disk/byte and resource bounds.
 * Free-space reserve is additional to the per-attempt disk usage limit.
 */
@Serializable
data class VideoControlledMediaBinding(
    val execution: VideoLocalExecutionPolicy,
    val maximumStagingBytes: Long,
    val maximumOutputBytes: Long,
    val minimumFreeDiskBytes: Long,
    val maximumConcurrentNativeProcesses: Int,
    val maximumBufferedFrames: Int,
    val encodingProfile: String,
) {
    init {
        require(encodingProfile == "image2-h264-yuv420p-silent-square-v1") { "Unsupported controlled media encoding profile" }
        require(maximumConcurrentNativeProcesses == 1 && maximumBufferedFrames == 1) {
            "Controlled media execution must be sequential and frame-bounded"
        }
        require(maximumStagingBytes > 0 && maximumOutputBytes > 0 && minimumFreeDiskBytes > 0 &&
            Math.addExact(maximumStagingBytes, maximumOutputBytes) <= execution.diskLimitBytes) {
            "Controlled staging and output must fit the local disk budget"
        }
    }
}

/** Pinned full-range template. Each Node invocation consumes a derived <=300-frame
 * descriptor; scenery continuation is added from the preceding verified receipt. */
@Serializable
data class VideoControlledMotionDescriptor(
    val projectId: String,
    val sourceIdentity: String,
    val requestJson: String,
    val runtime: VideoControlledMotionRuntimeBinding,
) {
    init {
        requireVideoJobId(projectId, "Controlled project")
        requireVideoJobSha256(sourceIdentity, "Controlled source identity")
        require(requestJson.length in 2..4_000_000) { "Controlled renderer descriptor size is invalid" }
        val json = Json.parseToJsonElement(requestJson).jsonObject
        require(json.keys == setOf("schema", "preparedScene", "seed", "fps", "canvas", "frameRange", "controls", "scenery") ||
            json.keys == setOf("schema", "preparedScene", "seed", "fps", "canvas", "frameRange", "controls")) {
            "Controlled descriptor must contain only the executable renderer request fields"
        }
        require(json["schema"]?.jsonPrimitive?.content == CONTROLLED_MOTION_DESCRIPTOR_SCHEMA)
        val sceneElement = json["preparedScene"] ?: throw IllegalArgumentException("Controlled prepared scene is missing")
        val sceneObject = sceneElement as? JsonObject ?: throw IllegalArgumentException("Controlled prepared scene must be an object")
        require(sceneObject["schemaVersion"]?.jsonPrimitive?.content == "2") { "Renderer requires prepared-scene schema version 2" }
        val scene = Json.decodeFromJsonElement<VideoPreparedScene>(sceneElement)
        val safeId = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        require(safeId.matches(scene.id.id) && scene.id.version > 0) { "Renderer requires a versioned prepared-scene ID" }
        require(scene.masks.all { mask ->
            val alpha = mask.alpha ?: return@all false
            val pixels = mask.image.width.toLong() * mask.image.height.toLong()
            val measured = runCatching {
                Math.addExact(Math.addExact(alpha.opaquePixels, alpha.translucentPixels), alpha.transparentPixels)
            }.getOrNull()
            alpha.opaquePixels >= 0 && alpha.translucentPixels >= 0 && alpha.transparentPixels >= 0 &&
                pixels in 1..MAX_JAVASCRIPT_SAFE_INTEGER && measured == pixels
        }) { "Every prepared-scene mask requires valid measured source-alpha counts for rendering" }
        require(runCatching { java.time.Instant.parse(scene.createdAt) }.isSuccess) { "Renderer requires prepared-scene createdAt" }
        val source = sceneObject["source"]?.jsonObject ?: throw IllegalArgumentException("Renderer prepared-scene source is missing")
        fun artifactPin(value: kotlinx.serialization.json.JsonElement?) {
            val pin = value?.jsonObject ?: throw IllegalArgumentException("Renderer source artifact pin is missing")
            require(pin["relativePath"]?.jsonPrimitive?.isString == true &&
                Regex("[0-9a-f]{64}").matches(pin["sha256"]?.jsonPrimitive?.content ?: "")) {
                "Renderer source artifact pin is invalid"
            }
        }
        val look = source["look"]
        val references = source["references"] as? JsonArray ?: JsonArray(emptyList())
        require(look != null || references.isNotEmpty()) { "Renderer needs a pinned source look or reference" }
        if (look != null && look !is JsonPrimitive) {
            val lookObject = look.jsonObject
            require(lookObject["id"]?.jsonObject?.let { id -> safeId.matches(id["id"]?.jsonPrimitive?.content ?: "") &&
                (id["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) > 0 } == true) { "Renderer source look ID is invalid" }
            artifactPin(lookObject["artifact"])
        }
        references.forEach { reference ->
            val ref = reference.jsonObject
            require(ref["id"]?.jsonObject?.let { id -> safeId.matches(id["id"]?.jsonPrimitive?.content ?: "") &&
                (id["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) > 0 } == true) { "Renderer source reference ID is invalid" }
            artifactPin(ref["descriptorArtifact"])
            artifactPin(ref["original"]?.jsonObject?.get("artifact"))
        }
        val dependencies = sceneObject["dependencies"] as? JsonArray
            ?: throw IllegalArgumentException("Renderer prepared-scene dependencies are missing")
        require(dependencies.isNotEmpty() && dependencies.map { it.jsonObject["id"]?.jsonPrimitive?.content }.distinct().size == dependencies.size &&
            dependencies.all { dependency -> dependency.jsonObject.let { pin ->
                safeId.matches(pin["id"]?.jsonPrimitive?.content ?: "") &&
                    !pin["version"]?.jsonPrimitive?.content.isNullOrBlank() &&
                    Regex("[0-9a-f]{64}").matches(pin["sha256"]?.jsonPrimitive?.content ?: "")
            } }) { "Renderer prepared-scene dependency pins are invalid" }
        fun integer(element: kotlinx.serialization.json.JsonElement): Long {
            val primitive = element.jsonPrimitive
            require(!primitive.isString && primitive.content.matches(Regex("(0|[1-9][0-9]*)"))) { "Renderer integer is not exact" }
            return primitive.content.toLong()
        }
        val controls = json["controls"] as? JsonArray ?: throw IllegalArgumentException("Controlled controls must be structured")
        val scenery = json["scenery"]
        require("scenery" !in json || scenery is JsonObject) {
            "Present controlled scenery must be an executable object"
        }
        require(controls.size <= 16 && (controls.isNotEmpty() || scenery is JsonObject))
        if (scenery is JsonObject) {
            require(scenery["schema"]?.jsonPrimitive?.content == "melotrail-rigid-scenery-v1") {
                "Controlled scenery schema is invalid"
            }
            val viewport = scenery["viewport"] as? JsonObject
                ?: throw IllegalArgumentException("Controlled scenery viewport is missing")
            val canvas = json.getValue("canvas").jsonObject
            require(viewport["coordinateSpaceId"]?.jsonPrimitive?.content == canvas.getValue("coordinateSpaceId").jsonPrimitive.content &&
                parsedLong(viewport.getValue("x")) == 0L && parsedLong(viewport.getValue("y")) == 0L &&
                parsedLong(viewport.getValue("width")) == parsedLong(canvas.getValue("width")) &&
                parsedLong(viewport.getValue("height")) == parsedLong(canvas.getValue("height"))) {
                "Controlled scenery viewport must match the output canvas"
            }
            require(scenery["mode"]?.jsonPrimitive?.content in setOf("static", "moving") &&
                scenery["planes"] is JsonArray && (scenery["planes"] as JsonArray).isNotEmpty()) {
                "Controlled scenery requires a supported mode and at least one plane"
            }
            val camera = scenery["camera"] as? JsonObject
                ?: throw IllegalArgumentException("Controlled scenery camera is missing")
            val mode = scenery.getValue("mode").jsonPrimitive.content
            val travelX = camera["travelXPixels"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val travelY = camera["travelYPixels"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val duration = camera["durationFrames"]?.let(::parsedLong) ?: 1L
            require(travelX.isFinite() && travelY.isFinite() && duration in 1..9000 &&
                (mode != "moving" || duration >= 2 && (travelX != 0.0 || travelY != 0.0)) &&
                (mode != "static" || duration == 1L && travelX == 0.0 && travelY == 0.0)) {
                "Controlled scenery camera request is not executable"
            }
            val planes = scenery["planes"] as JsonArray
            val coverageIds = (sceneObject["sceneryCoverage"] as? JsonArray)?.mapNotNull { item ->
                (item as? JsonObject)?.get("id")?.jsonPrimitive?.content
            }?.toSet().orEmpty()
            val layers = (sceneObject["layers"] as? JsonArray)?.associateBy { layer ->
                (layer as? JsonObject)?.get("id")?.jsonPrimitive?.content
            }.orEmpty()
            val usedCoverage = mutableSetOf<String>()
            val usedLayers = mutableSetOf<String>()
            require(planes.all { plane ->
                if (plane !is JsonObject) return@all false
                val planeId = plane["id"]?.jsonPrimitive?.content
                val sections = plane["sections"] as? JsonArray ?: return@all false
                if (!safeId.matches(planeId.orEmpty()) || sections.isEmpty()) return@all false
                val executable = sections.mapNotNull { section ->
                    if (section !is JsonObject) return@mapNotNull null
                    val coverageId = section["coverageId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val start = section["startFrame"]?.let(::integer) ?: return@mapNotNull null
                    val end = section["endFrameExclusive"]?.let(::integer) ?: return@mapNotNull null
                    val layerId = coverageId.let { id ->
                        (sceneObject["sceneryCoverage"] as? JsonArray)?.firstOrNull { c ->
                            (c as? JsonObject)?.get("id")?.jsonPrimitive?.content == id
                        }?.let { c -> (c as JsonObject)["layerId"]?.jsonPrimitive?.content }
                    } ?: return@mapNotNull null
                    if (!safeId.matches(coverageId) || coverageId !in coverageIds || !usedCoverage.add(coverageId) ||
                        !usedLayers.add(layerId) || layerId !in layers || start < 0 || end <= start ||
                        start > MAX_JAVASCRIPT_SAFE_INTEGER || end > MAX_JAVASCRIPT_SAFE_INTEGER) return@mapNotNull null
                    if (listOf("worldX", "worldY").any { key ->
                        val value = section[key]?.jsonPrimitive?.content?.toDoubleOrNull()
                        value == null || !value.isFinite() || kotlin.math.abs(value) > 16384
                    }) return@mapNotNull null
                    start to end
                }
                executable.size == sections.size && executable.size <= 32 && executable.isNotEmpty() &&
                    executable.sortedBy { it.first }.let { ordered ->
                        val requiredStart = if (mode == "moving") parsedLong(camera.getValue("startFrame")) else parsedLong(json.getValue("frameRange").jsonObject.getValue("startFrame"))
                        val requiredEnd = if (mode == "moving") Math.addExact(requiredStart, duration) else {
                            val range = json.getValue("frameRange").jsonObject
                            Math.addExact(parsedLong(range.getValue("startFrame")), parsedLong(range.getValue("frameCount")))
                        }
                        ordered.first().first <= requiredStart && ordered.last().second >= requiredEnd &&
                            ordered.zipWithNext().all { (a, b) -> a.second == b.first }
                    }
            }) { "Controlled scenery requires complete executable source sections with coverage IDs and absolute frame bounds" }
            validateExecutableScenery(scene, scenery, json.getValue("frameRange").jsonObject, width, height)
        }
        val subjectKinds = mutableSetOf<String>()
        var controlledSubject: String? = null
        require(controls.all { control ->
            if (control !is JsonObject || control["id"] !is JsonPrimitive || control["capabilityId"] !is JsonPrimitive ||
                control["id"]?.jsonPrimitive?.isString != true || control["capabilityId"]?.jsonPrimitive?.isString != true) return@all false
            val numeric = when (control["kind"]?.jsonPrimitive?.content) {
                "blink" -> Triple("amount", 0.0, 1.0)
                "breathing" -> Triple("amplitudePixels", 0.0, 4.0)
                "headGesture" -> Triple("amplitudeDegrees", 0.0, 3.0)
                "steam" -> Triple("ratePerSecond", 0.01, 8.0)
                else -> return@all false
            }
            val id = control.getValue("id").jsonPrimitive.content
            val capabilityId = control.getValue("capabilityId").jsonPrimitive.content
            if (!safeId.matches(id) || !safeId.matches(capabilityId)) return@all false
            val kind = control.getValue("kind").jsonPrimitive.content
            val capability = scene.motionCapabilities.singleOrNull { it.id == capabilityId }
                ?: return@all false
            if (capability.reviewStatus == VideoComponentReviewStatus.REJECTED) return@all false
            if (kind in setOf("blink", "breathing", "headGesture") && !subjectKinds.add(kind)) return@all false
            val subject = when (kind) {
                "blink" -> {
                    val pose = scene.poses.singleOrNull { it.id == capability.targetId } ?: return@all false
                    val layer = scene.layers.singleOrNull { it.id == pose.subjectLayerId } ?: return@all false
                    if (capability.targetType != VideoMotionTargetType.POSE || capability.control != VideoMotionControl.POSE_BLEND ||
                        layer.kind != VideoLayerKind.SUBJECT || pose.bounds != layer.bounds ||
                        pose.reviewStatus == VideoComponentReviewStatus.REJECTED) return@all false
                    layer
                }
                "breathing", "headGesture" -> {
                    val layer = scene.layers.singleOrNull { it.id == capability.targetId } ?: return@all false
                    if (layer.kind != VideoLayerKind.SUBJECT || capability.targetType != VideoMotionTargetType.LAYER ||
                        capability.control != if (kind == "breathing") VideoMotionControl.TRANSLATE_Y else VideoMotionControl.ROTATE) return@all false
                    if (kind == "headGesture") {
                        val matchingMasks = scene.masks.filter {
                            it.purpose == VideoMaskPurpose.HEAD_REGION && it.layerIds == listOf(layer.id)
                        }
                        if (matchingMasks.size != 1) return@all false
                        val mask = matchingMasks.single()
                        if (mask.reviewStatus == VideoComponentReviewStatus.REJECTED || mask.alpha == null ||
                            mask.alpha.opaquePixels < 0 || mask.alpha.translucentPixels < 0 || mask.alpha.transparentPixels < 0 ||
                            mask.image.width.toLong() * mask.image.height != mask.alpha.let { it.opaquePixels + it.translucentPixels + it.transparentPixels } ||
                            mask.alpha.opaquePixels + mask.alpha.translucentPixels + mask.alpha.transparentPixels <= 0L) return@all false
                    }
                    layer
                }
                else -> {
                    val anchor = scene.effectAnchors.singleOrNull { it.id == capability.targetId } ?: return@all false
                    val position = anchor.position ?: scene.subjectLandmarks.singleOrNull { it.id == anchor.landmarkId }?.position
                    if (capability.targetType != VideoMotionTargetType.EFFECT_ANCHOR || capability.control != VideoMotionControl.EFFECT_RATE ||
                        anchor.reviewStatus == VideoComponentReviewStatus.REJECTED || position == null ||
                        position.coordinateSpaceId != json.getValue("canvas").jsonObject.getValue("coordinateSpaceId").jsonPrimitive.content) return@all false
                    null
                }
            }
            if (subject != null) {
                if (subject.reviewStatus == VideoComponentReviewStatus.REJECTED ||
                    subject.bounds.coordinateSpaceId != json.getValue("canvas").jsonObject.getValue("coordinateSpaceId").jsonPrimitive.content ||
                    (controlledSubject != null && controlledSubject != subject.id)) return@all false
                controlledSubject = subject.id
            }
            val allowed = setOf("id", "capabilityId", "kind", numeric.first) +
                if (numeric.first == "ratePerSecond") setOf("risePixelsPerSecond") else emptySet()
            val value = control[numeric.first]?.jsonPrimitive?.content?.toDoubleOrNull()
            val compatible = when (kind) {
                "blink" -> value != null && value * capability.maximum in capability.minimum..capability.maximum
                "breathing" -> value != null && 0.0 in capability.minimum..capability.maximum &&
                    value in capability.minimum..capability.maximum
                "headGesture" -> value != null && value in capability.minimum..capability.maximum &&
                    -value in capability.minimum..capability.maximum
                else -> value != null && value in capability.minimum..capability.maximum
            }
            compatible && control.keys.all { it in allowed } &&
                (control[numeric.first] as? JsonPrimitive)?.let { !it.isString && it.content.toDoubleOrNull()?.let { n -> n.isFinite() && n in numeric.second..numeric.third } == true } == true &&
                (numeric.first != "ratePerSecond" || control["risePixelsPerSecond"] == null ||
                    (control["risePixelsPerSecond"] as? JsonPrimitive)?.let { !it.isString && it.content.toDoubleOrNull()?.let { n -> n.isFinite() && n in 0.01..28.0 } == true } == true)
        } && controls.map { it.jsonObject.getValue("id").jsonPrimitive.content }.distinct().size == controls.size &&
            (controlledSubject == null || scene.layers.count {
                it.kind == VideoLayerKind.ENVIRONMENT && it.bounds.coordinateSpaceId ==
                    json.getValue("canvas").jsonObject.getValue("coordinateSpaceId").jsonPrimitive.content &&
                    it.bounds.x == 0.0 && it.bounds.y == 0.0 && it.bounds.width == width.toDouble() &&
                    it.bounds.height == height.toDouble()
            } == 1)) {
            "Controlled renderer controls must be supported, finite and bounded"
        }
        fun finite(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is JsonObject -> element.values.forEach(::finite)
                is JsonArray -> element.forEach(::finite)
                is JsonPrimitive -> if (!element.isString && element.content != "null" && element.content != "true" && element.content != "false") {
                    require(element.content.toDoubleOrNull()?.isFinite() == true) { "Non-finite renderer number" }
                }
            }
        }
        finite(json)
        require(parsedLong(json.getValue("seed")) in 0..MAX_JAVASCRIPT_SAFE_INTEGER)
        require(parsedLong(json.getValue("frameRange").jsonObject.getValue("startFrame")) in 0..MAX_JAVASCRIPT_SAFE_INTEGER)
        require(parsedLong(json.getValue("frameRange").jsonObject.getValue("frameCount")) in 1..9_000L)
        // Long durable ranges are templates: executionDescriptors() deterministically splits them
        // into renderer requests, each satisfying render.cjs's 1..300 invocation bound.
        require(integer(json.getValue("fps")) in 1..120 && integer(json.getValue("canvas").jsonObject.getValue("width")) in 1..3840 &&
            integer(json.getValue("canvas").jsonObject.getValue("height")) in 1..2160) { "Renderer geometry and cadence must be exact integers" }
        require(startFrame in 0..MAX_JAVASCRIPT_SAFE_INTEGER && frameCount in 1..MAX_JAVASCRIPT_SAFE_INTEGER &&
            Math.addExact(startFrame, frameCount) <= MAX_JAVASCRIPT_SAFE_INTEGER)
        require(width.toLong() * height <= 12_000_000)
        val space = json.getValue("canvas").jsonObject.getValue("coordinateSpaceId").jsonPrimitive.content
        require(scene.coordinateSpaces.count { it.id == space && it.width >= width && it.height >= height } == 1)
        require(scene.layers.count { layer ->
            layer.kind == VideoLayerKind.FINISHED_SCENE && layer.bounds.coordinateSpaceId == space &&
                layer.bounds.x == 0.0 && layer.bounds.y == 0.0 &&
                layer.bounds.width == width.toDouble() && layer.bounds.height == height.toDouble()
        } == 1) { "Controlled viewport must match the prepared finished scene" }
    }

    private fun parsedLong(element: kotlinx.serialization.json.JsonElement): Long {
        val primitive = element.jsonPrimitive
        require(!primitive.isString && primitive.content.matches(Regex("(0|[1-9][0-9]*)"))) { "Renderer integer is not exact" }
        return primitive.content.toLong()
    }
    private val parsed get() = Json.parseToJsonElement(requestJson).jsonObject
    val seed get() = parsedLong(parsed.getValue("seed"))
    val fps get() = parsed.getValue("fps").jsonPrimitive.content.toInt()
    val width get() = parsed.getValue("canvas").jsonObject.getValue("width").jsonPrimitive.content.toInt()
    val height get() = parsed.getValue("canvas").jsonObject.getValue("height").jsonPrimitive.content.toInt()
    val startFrame get() = parsedLong(parsed.getValue("frameRange").jsonObject.getValue("startFrame"))
    val frameCount get() = parsedLong(parsed.getValue("frameRange").jsonObject.getValue("frameCount"))

    /** Deterministic, lazy absolute-frame chunks of this pinned full-range template. */
    fun invocationDescriptors(): Sequence<String> = controlledMotionInvocationDescriptors(requestJson)

}

/** The renderer consumes these derived Node requests, not the full-range template directly.
 * Continuation state is attached only after verifying the preceding chunk receipt. */
fun controlledMotionInvocationDescriptors(requestJson: String): Sequence<String> = sequence {
    val original = Json.parseToJsonElement(requestJson).jsonObject
    val range = original.getValue("frameRange").jsonObject
    fun exact(key: String): Long {
        val value = range.getValue(key).jsonPrimitive
        require(!value.isString && value.content.matches(Regex("(0|[1-9][0-9]*)")))
        return value.content.toLong()
    }
    val begin = exact("startFrame")
    val count = exact("frameCount")
    require(begin in 0..MAX_JAVASCRIPT_SAFE_INTEGER && count in 1..9_000 &&
        Math.addExact(begin, count) <= MAX_JAVASCRIPT_SAFE_INTEGER)
    val end = begin + count
    var start = begin
    while (start < end) {
        val size = minOf(300L, end - start)
        yield(JsonObject(original.toMutableMap().apply {
            put("frameRange", JsonObject(range.toMutableMap().apply {
                put("startFrame", JsonPrimitive(start))
                put("frameCount", JsonPrimitive(size))
            }))
        }).toString())
        start = Math.addExact(start, size)
    }
}

/** Each executable dependency has one role; loaded Canvas files are an explicit nonempty set. */
@Serializable
data class VideoControlledMotionRuntimeBinding(
    val node: VideoGenerationDependencyPin,
    val compositor: VideoGenerationDependencyPin,
    val scenery: VideoGenerationDependencyPin,
    val canvasManifest: VideoGenerationDependencyPin,
    val canvasArtifacts: List<VideoGenerationDependencyPin>,
    val ffmpeg: VideoGenerationDependencyPin,
    val ffprobe: VideoGenerationDependencyPin,
    val mediaManifest: VideoGenerationDependencyPin,
    val expectedCanvasVersion: String,
) {
    val allPins: List<VideoGenerationDependencyPin>
        get() = listOf(node, compositor, scenery, canvasManifest) + canvasArtifacts + listOf(ffmpeg, ffprobe, mediaManifest)

    init {
        require(canvasArtifacts.isNotEmpty()) { "Canvas loaded artifacts must be pinned" }
        require(expectedCanvasVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?"))) {
            "Canvas runtime version must be explicit"
        }
        requireVideoDependencyPins(allPins)
        require(allPins.all { it.ownedPath != null }) { "Every controlled runtime role needs an owned path" }
        require(node.id == "node" && compositor.id == "compositor" && scenery.id == "scenery" &&
            canvasManifest.id == "canvas-manifest" && ffmpeg.id == "ffmpeg" && ffprobe.id == "ffprobe" &&
            mediaManifest.id == "media-manifest" && canvasArtifacts.all { it.id.startsWith("canvas-artifact-") }) { "Controlled runtime pin roles are incomplete or mislabelled" }
    }
}

/* Admission mirrors scenery.cjs validation, including full-trajectory shutter coverage.
 * Do not equate a well-shaped section list with executable scenery. */
private fun validateExecutableScenery(
    scene: VideoPreparedScene, raw: JsonObject, range: JsonObject, width: Int, height: Int,
) {
    fun long(value: kotlinx.serialization.json.JsonElement?): Long {
        val p = requireNotNull(value).jsonPrimitive
        require(!p.isString && p.content.matches(Regex("(0|[1-9][0-9]*)")))
        return p.content.toLong().also { require(it in 0..MAX_JAVASCRIPT_SAFE_INTEGER) }
    }
    fun numeric(value: kotlinx.serialization.json.JsonElement?, default: Double): Double =
        value?.jsonPrimitive?.let { p ->
            require(!p.isString)
            p.content.toDouble().also { require(it.isFinite()) }
        } ?: default
    val mode = raw.getValue("mode").jsonPrimitive.content
    val camera = raw.getValue("camera").jsonObject
    val first = camera["startFrame"]?.let(::long) ?: 0L
    val duration = camera["durationFrames"]?.let(::long) ?: 1L
    val last = Math.addExact(first, duration)
    require(last <= MAX_JAVASCRIPT_SAFE_INTEGER)
    val renderStart = long(range.getValue("startFrame"))
    val renderEnd = Math.addExact(renderStart, long(range.getValue("frameCount")))
    // The first renderer invocation has no verified prior finalState. Node accepts
    // a moving range beginning later only when that continuation receipt exists.
    require(mode != "moving" || renderStart == first && renderEnd <= last) {
        "Moving scenery must begin at the camera trajectory start without verified continuation state"
    }
    val travelX = numeric(camera["travelXPixels"], 0.0)
    val travelY = numeric(camera["travelYPixels"], 0.0)
    require(kotlin.math.abs(travelX) <= 16384 && kotlin.math.abs(travelY) <= 16384)
    val blur = camera["motionBlurSamples"]?.let(::long) ?: if (mode == "moving") 3L else 1L
    val shutter = numeric(camera["shutterFraction"], if (mode == "moving") 0.5 else 0.0)
    require(blur in 1..8 && shutter in 0.0..1.0)
    val aperture = VideoRect(raw.getValue("viewport").jsonObject.getValue("coordinateSpaceId").jsonPrimitive.content,
        0.0, 0.0, width.toDouble(), height.toDouble())
    data class Section(val coverage: VideoSceneryCoverage, val layer: VideoPreparedLayer,
        val x: Double, val y: Double, val start: Long, val end: Long)
    data class Plane(val id: String, val sections: List<Section>, var depth: Int = 1)
    val planes = (raw.getValue("planes") as JsonArray).map { element ->
        val plane = element.jsonObject
        Plane(plane.getValue("id").jsonPrimitive.content, (plane.getValue("sections") as JsonArray).map { item ->
            val section = item.jsonObject
            val coverage = scene.sceneryCoverage.single { it.id == section.getValue("coverageId").jsonPrimitive.content }
            val layer = scene.layers.single { it.id == coverage.layerId }
            require(coverage.reviewStatus != VideoComponentReviewStatus.REJECTED &&
                layer.reviewStatus != VideoComponentReviewStatus.REJECTED && layer.bounds.containsScenery(coverage.bounds)) {
                "Selected scenery coverage must be reviewed and inside its placed layer"
            }
            Section(coverage, layer, numeric(section["worldX"], Double.NaN), numeric(section["worldY"], Double.NaN),
                long(section["startFrame"]), long(section["endFrameExclusive"]))
        }.sortedWith(compareBy({ it.start }, { it.coverage.id })))
    }
    require(planes.size in 1..16 && planes.map { it.id }.distinct().size == planes.size &&
        planes.sumOf { it.sections.size } <= 32)
    val selected = planes.flatMap { it.sections }.map { it.layer.id }.toSet()
    val byLayer = planes.flatMap { plane -> plane.sections.map { it.layer.id to plane.id } }.toMap()
    val relations = scene.depthRelations.filter { it.nearerLayerId in selected && it.fartherLayerId in selected }
    require(scene.depthRelations.none { it.reviewStatus == VideoComponentReviewStatus.REJECTED &&
        (it.nearerLayerId in selected || it.fartherLayerId in selected) })
    require(relations.none { byLayer[it.nearerLayerId] == byLayer[it.fartherLayerId] })
    if (mode == "moving" && planes.size > 1) {
        require(relations.isNotEmpty()) { "Moving depth planes need prepared depth relations" }
        val connected = mutableSetOf(planes.first().id)
        var changed: Boolean
        do {
            val before = connected.size
            relations.forEach { r ->
                val near = byLayer.getValue(r.nearerLayerId)
                val far = byLayer.getValue(r.fartherLayerId)
                if (near in connected || far in connected) { connected += near; connected += far }
            }
            changed = connected.size != before
        } while (changed)
        require(connected.size == planes.size)
        fun rank(id: String): Int = relations.filter { byLayer[it.nearerLayerId] == id }
            .maxOfOrNull { rank(byLayer.getValue(it.fartherLayerId)) + 1 } ?: 0
        planes.forEach { it.depth = 1 + rank(it.id) }
    }
    scene.occlusionRelations.filter { it.occluderLayerId in selected || it.occludedLayerId in selected }.forEach { relation ->
        require(relation.reviewStatus != VideoComponentReviewStatus.REJECTED && relation.occluderLayerId !in selected)
        val occluder = scene.layers.single { it.id == relation.occluderLayerId }
        val mask = scene.masks.single { it.id == relation.maskId }
        require(occluder.reviewStatus != VideoComponentReviewStatus.REJECTED &&
            mask.reviewStatus != VideoComponentReviewStatus.REJECTED &&
            relation.occluderLayerId in mask.layerIds && relation.occludedLayerId in mask.layerIds)
    }
    fun samples(): List<Double> = if (blur == 1L) listOf(0.0) else (0 until blur.toInt()).map {
        ((it + 0.5) / blur - 0.5) * shutter
    }
    fun rectangle(plane: Plane, section: Section, frame: Double): VideoRect {
        val progress = if (mode == "static") 0.0 else
            ((frame - first) / (duration - 1)).coerceIn(0.0, 1.0)
        return section.coverage.bounds.copy(x = section.x - travelX * progress * plane.depth,
            y = section.y - travelY * progress * plane.depth)
    }
    fun covers(rectangles: List<VideoRect>): Boolean {
        val xEdges = (listOf(0.0, width.toDouble()) + rectangles.flatMap { listOf(
            it.x.coerceIn(0.0, width.toDouble()), (it.x + it.width).coerceIn(0.0, width.toDouble())) }).distinct().sorted()
        return xEdges.zipWithNext().all { (left, right) ->
            if (left == right) true else {
                val intervals = rectangles.filter { it.x <= left && it.x + it.width >= right }
                    .map { it.y to it.y + it.height }.sortedBy { it.first }
                var bottom = 0.0
                intervals.forEach { (top, end) -> if (top <= bottom) bottom = maxOf(bottom, end) }
                bottom >= height
            }
        }
    }
    planes.forEach { plane ->
        plane.sections.forEach { section ->
            if (mode == "static") require(section.x == section.coverage.bounds.x && section.y == section.coverage.bounds.y) {
                "Static scenery must retain prepared placement"
            } else listOf(Triple("X", travelX, section.x), Triple("Y", travelY, section.y)).forEach { (axis, travel, world) ->
                val prepared = if (axis == "X") section.coverage.bounds.x else section.coverage.bounds.y
                if (travel == 0.0) require(world == prepared) { "Scenery cannot move on an unconfigured axis" }
                else {
                    val capabilities = scene.motionCapabilities.filter { it.targetType == VideoMotionTargetType.SCENERY_COVERAGE &&
                        it.targetId == section.coverage.id && it.control == (if (axis == "X") VideoMotionControl.TRANSLATE_X else VideoMotionControl.TRANSLATE_Y) &&
                        it.unit == VideoMotionUnit.PIXELS }
                    require(capabilities.size == 1 && capabilities.single().reviewStatus != VideoComponentReviewStatus.REJECTED) {
                        "Moving scenery needs a reviewed translation capability"
                    }
                    val capability = capabilities.single()
                    listOf(section.start.toDouble(), section.end - 1e-6).forEach { frame ->
                        val rect = rectangle(plane, section, frame)
                        val offset = (if (axis == "X") rect.x else rect.y) - prepared
                        require(offset in capability.minimum..capability.maximum) { "Scenery travel exceeds its capability" }
                    }
                }
            }
        }
        plane.sections.zipWithNext().forEach { (outgoing, incoming) ->
            val boundary = incoming.start.toDouble()
            val frames = listOf(boundary, boundary - 1e-6) + samples().flatMap { listOf(boundary - 1 + it, boundary + it) }
            require(frames.all { frame -> rectangle(plane, outgoing, frame).containsScenery(aperture) &&
                rectangle(plane, incoming, frame).containsScenery(aperture) }) { "Scenery join is visible" }
        }
    }
    val evaluationStart = if (mode == "moving") first else renderStart
    val evaluationEnd = if (mode == "moving") last else renderEnd
    for (frame in evaluationStart until evaluationEnd) for (offset in samples()) {
        val sample = frame + offset
        val visible = planes.map { plane ->
            val section = plane.sections.firstOrNull { sample >= it.start && sample < it.end }
                ?: if (sample < plane.sections.first().start) plane.sections.first() else plane.sections.last()
            rectangle(plane, section, sample)
        }
        require(covers(visible)) { "Scenery coverage leaves a visible hole at frame $frame" }
    }
}

private fun VideoRect.containsScenery(inner: VideoRect): Boolean =
    coordinateSpaceId == inner.coordinateSpaceId && inner.x >= x && inner.y >= y &&
        inner.x + inner.width <= x + width && inner.y + inner.height <= y + height

const val CONTROLLED_MOTION_DESCRIPTOR_SCHEMA = "melotrail-controlled-motion-request-v1"
const val MAX_JAVASCRIPT_SAFE_INTEGER = 9_007_199_254_740_991L

@Serializable
data class VideoControlledMotionRequest(
    val preparedPins: List<VideoGenerationDependencyPin>,
    val startFrame: Long,
    val endFrameExclusive: Long,
    val seed: Long,
    val descriptor: VideoControlledMotionDescriptor,
) {
    init {
        requireVideoDependencyPins(preparedPins)
        require(preparedPins.isNotEmpty() && preparedPins.all { it.ownedPath != null })
        require(startFrame in 0..MAX_JAVASCRIPT_SAFE_INTEGER && endFrameExclusive in 1..MAX_JAVASCRIPT_SAFE_INTEGER &&
            endFrameExclusive > startFrame && Math.subtractExact(endFrameExclusive, startFrame) in 1..9_000L)
        require(seed in 0..MAX_JAVASCRIPT_SAFE_INTEGER && descriptor.seed == seed &&
            descriptor.startFrame == startFrame && Math.addExact(descriptor.startFrame, descriptor.frameCount) == endFrameExclusive)
        require((preparedPins + descriptor.runtime.allPins).map(VideoGenerationDependencyPin::id).distinct().size ==
            preparedPins.size + descriptor.runtime.allPins.size)
    }
}

@Serializable
data class VideoGenerationDependencyPin(
    val id: String,
    val sha256: String,
    /** Absolute owned file path when this dependency is consumed by a local workflow. */
    val ownedPath: String? = null,
) {
    init {
        requireVideoJobId(id, "Video generation dependency")
        requireVideoJobSha256(sha256, "Video generation dependency")
        ownedPath?.let {
            require(it.isNotBlank() && it.length <= 8_192 && it.none(Char::isISOControl)) {
                "Video generation dependency path is invalid"
            }
        }
    }
}

/** API-format ComfyUI node bindings persisted with the immutable request for restart recovery. */
@Serializable
data class VideoComfyWorkflowRequest(
    val workflowDependencyId: String,
    val promptInput: VideoComfyInputSlot,
    val referenceInputs: List<VideoComfyReferenceInput> = emptyList(),
    val widthInput: VideoComfyInputSlot? = null,
    val heightInput: VideoComfyInputSlot? = null,
    val frameCountInput: VideoComfyInputSlot? = null,
    val framesPerSecondInput: VideoComfyInputSlot? = null,
    val output: VideoComfyOutputBinding,
) {
    init {
        requireVideoJobId(workflowDependencyId, "ComfyUI workflow dependency")
        require(referenceInputs.size <= 32) { "A ComfyUI workflow can consume at most 32 references" }
        require(referenceInputs.map(VideoComfyReferenceInput::dependencyId).distinct().size == referenceInputs.size) {
            "ComfyUI reference dependencies must be unique"
        }
        require(referenceInputs.map(VideoComfyReferenceInput::slot).distinct().size == referenceInputs.size) {
            "ComfyUI reference input slots must be unique"
        }
        val scalarSlots = listOfNotNull(promptInput, widthInput, heightInput, frameCountInput, framesPerSecondInput)
        require(scalarSlots.distinct().size == scalarSlots.size) { "ComfyUI scalar input slots must be unique" }
        require(referenceInputs.none { it.slot in scalarSlots }) { "ComfyUI reference and scalar slots must be distinct" }
    }
}

@Serializable
data class VideoComfyInputSlot(val nodeId: String, val inputName: String) {
    init {
        require(COMFY_NODE_ID.matches(nodeId)) { "ComfyUI node ID is invalid" }
        require(COMFY_INPUT_NAME.matches(inputName)) { "ComfyUI input name is invalid" }
    }
}

@Serializable
data class VideoComfyReferenceInput(
    val dependencyId: String,
    val slot: VideoComfyInputSlot,
    val uploadFileName: String,
) {
    init {
        requireVideoJobId(dependencyId, "ComfyUI reference dependency")
        require(COMFY_UPLOAD_NAME.matches(uploadFileName) && uploadFileName !in setOf(".", "..")) {
            "ComfyUI upload filename is invalid"
        }
    }
}

@Serializable
data class VideoComfyOutputBinding(
    val nodeId: String,
    val allowedExtensions: Set<String>,
) {
    init {
        require(COMFY_NODE_ID.matches(nodeId)) { "ComfyUI output node ID is invalid" }
        require(allowedExtensions.isNotEmpty() && allowedExtensions.size <= 8 &&
            allowedExtensions.all { COMFY_EXTENSION.matches(it) }
        ) { "ComfyUI output extensions must be lowercase safe extensions" }
    }
}

@Serializable
data class VideoModelRequirement(
    val id: String,
    val version: String,
    val sha256: String? = null,
) {
    init {
        requireVideoJobId(id, "Video model")
        require(version.isNotBlank() && version.length <= 160 && version.none(Char::isISOControl)) {
            "Video model version is invalid"
        }
        sha256?.let { requireVideoJobSha256(it, "Video model") }
    }
}

@Serializable
sealed interface VideoExecutionPolicy

@Serializable
@SerialName("local")
data class VideoLocalExecutionPolicy(
    val wallClockLimitMillis: Long,
    val memoryLimitBytes: Long,
    val diskLimitBytes: Long,
) : VideoExecutionPolicy {
    init {
        require(wallClockLimitMillis in 1_000L..86_400_000L) { "Local wall-clock limit must be 1 second..24 hours" }
        require(memoryLimitBytes > 0L) { "Local memory limit must be positive" }
        require(diskLimitBytes > 0L) { "Local disk limit must be positive" }
    }
}

@Serializable
@SerialName("hosted")
data class VideoHostedExecutionPolicy(
    val budgetId: String,
    val currency: String,
    val estimatedMaximumCostMicros: Long,
    val estimateCreatedAt: String,
    val estimateExpiresAt: String,
    val authorizedSpendCapMicros: Long,
) : VideoExecutionPolicy {
    init {
        requireVideoJobId(budgetId, "Hosted budget")
        require(CURRENCY.matches(currency)) { "Hosted budget currency must be a three-letter uppercase code" }
        require(estimatedMaximumCostMicros >= 0L) { "Hosted maximum cost estimate must not be negative" }
        require(authorizedSpendCapMicros >= 0L) { "Hosted spend cap must not be negative" }
        requireVideoJobTimestamp(estimateCreatedAt, "Hosted estimate creation")
        requireVideoJobTimestamp(estimateExpiresAt, "Hosted estimate expiration")
        require(Instant.parse(estimateExpiresAt).isAfter(Instant.parse(estimateCreatedAt))) {
            "Hosted estimate expiration must be after its creation"
        }
    }
}

@Serializable
data class VideoGenerationAttempt(
    val id: String,
    val requestId: String,
    val number: Int,
    val ownershipToken: String,
    val status: VideoGenerationAttemptStatus,
    /** Tracks the crash window independently from runtime status and cancellation intent. */
    val submissionPhase: VideoSubmissionPhase,
    val admittedAt: String,
    val providerWorkId: String? = null,
    /** Null is deliberately distinct from zero and remains unknown until the backend reports it. */
    val progressPercent: Int? = null,
    val lastObservedAt: String? = null,
    val finishedAt: String? = null,
    val retryable: Boolean = false,
    val failure: String? = null,
    val actualCostMicros: Long? = null,
    val resourceUsage: VideoGenerationResourceUsage? = null,
) {
    init {
        requireVideoJobId(id, "Video attempt")
        requireVideoJobId(requestId, "Video request")
        require(number > 0) { "Video attempt number must be positive" }
        requireVideoJobId(ownershipToken, "Video attempt ownership")
        requireVideoJobTimestamp(admittedAt, "Video attempt admission")
        require(providerWorkId == null || providerWorkId.isNotBlank() && providerWorkId.length <= 512 && providerWorkId.none(Char::isISOControl)) {
            "Provider work identity is invalid"
        }
        require(progressPercent == null || progressPercent in 0..100) { "Video progress must be 0..100 or unknown" }
        lastObservedAt?.let { requireVideoJobTimestamp(it, "Video attempt observation") }
        finishedAt?.let { requireVideoJobTimestamp(it, "Video attempt completion") }
        require(actualCostMicros == null || actualCostMicros >= 0L) { "Actual hosted cost must not be negative" }
        require(failure == null || failure.isNotBlank() && failure.length <= 2_000 && failure.none(Char::isISOControl)) {
            "Video attempt failure is invalid"
        }
        if (status.isTerminal) require(finishedAt != null) { "A terminal video attempt needs a completion time" }
        if (!status.isTerminal) require(finishedAt == null) { "A non-terminal video attempt cannot have a completion time" }
        if (submissionPhase == VideoSubmissionPhase.READY) require(
            status == VideoGenerationAttemptStatus.SUBMITTING && providerWorkId == null &&
                progressPercent == null && actualCostMicros == null && resourceUsage == null
        ) { "An unclaimed attempt cannot have backend activity" }
        if (submissionPhase == VideoSubmissionPhase.PENDING) require(!status.isTerminal) {
            "A pending submission call cannot release its admission"
        }
        if (submissionPhase == VideoSubmissionPhase.NOT_STARTED) require(status.isTerminal) {
            "A conclusive not-started submission must be terminal"
        }
    }
}

@Serializable
enum class VideoGenerationAttemptStatus(val isTerminal: Boolean) {
    SUBMITTING(false),
    SUBMISSION_UNCERTAIN(false),
    ACTIVE(false),
    CANCELLATION_REQUESTED(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true),
}

@Serializable
enum class VideoSubmissionPhase {
    /** Admission exists, but no caller has claimed permission to invoke the backend. */
    READY,
    /** Launch was claimed durably; submit may be imminent, in flight, or interrupted by a crash. */
    PENDING,
    ACKNOWLEDGED,
    UNCERTAIN,
    NOT_STARTED,
}

@Serializable
data class VideoGenerationResourceUsage(
    val wallClockMillis: Long?,
    val peakMemoryBytes: Long?,
    val diskBytes: Long?,
) {
    init {
        require(wallClockMillis == null || wallClockMillis >= 0L) { "Measured wall-clock time must not be negative" }
        require(peakMemoryBytes == null || peakMemoryBytes >= 0L) { "Measured peak memory must not be negative" }
        require(diskBytes == null || diskBytes >= 0L) { "Measured disk use must not be negative" }
    }
}

@Serializable
data class VideoGenerationOutput(
    val id: String,
    val attemptId: String,
    val backendOutputId: String,
    val createdAt: String,
    val relativePath: String? = null,
    val sha256: String? = null,
    val byteCount: Long? = null,
) {
    init {
        requireVideoJobId(id, "Video output")
        requireVideoJobId(attemptId, "Video attempt")
        require(backendOutputId.isNotBlank() && backendOutputId.length <= 512 && backendOutputId.none(Char::isISOControl)) {
            "Backend output identity is invalid"
        }
        requireVideoJobTimestamp(createdAt, "Video output creation")
        require((relativePath == null) == (sha256 == null)) { "Output path and digest must be recorded together" }
        relativePath?.let { path ->
            // Use the artifact contract itself so a completed job can always be
            // imported into the existing project representation.
            VideoArtifact(path, requireNotNull(sha256))
            require(path.split('/').none(VideoJobControlPaths::isReservedName)) {
                "Video outputs must not use job document, lock, staging or recovery paths"
            }
        }
        sha256?.let { requireVideoJobSha256(it, "Video output") }
        require(byteCount == null || byteCount >= 0L) { "Video output byte count must not be negative" }
    }
}

internal fun requireVideoJobId(value: String, label: String) {
    require(VIDEO_JOB_SAFE_ID.matches(value)) { "$label ID must be a safe stable identifier" }
}

internal fun requireVideoJobTimestamp(value: String, label: String) {
    require(runCatching { Instant.parse(value) }.isSuccess) { "$label timestamp must be an ISO-8601 instant" }
}

internal fun requireVideoJobSha256(value: String, label: String) {
    require(VIDEO_JOB_SHA_256.matches(value)) { "$label SHA-256 must be lowercase hexadecimal" }
}

private fun requireVideoPrompt(value: String) {
    require(value.isNotBlank() && value.length <= 20_000 && value.none { it == '\u0000' }) { "Video generation prompt is invalid" }
}

private fun requireVideoDependencyPins(value: List<VideoGenerationDependencyPin>) {
    require(value.map(VideoGenerationDependencyPin::id).distinct().size == value.size) {
        "Video generation dependency IDs must be unique"
    }
}

private fun requireComfyDependencies(
    pins: List<VideoGenerationDependencyPin>,
    workflow: VideoComfyWorkflowRequest?,
) {
    if (workflow == null) return
    val byId = pins.associateBy(VideoGenerationDependencyPin::id)
    val consumed = listOf(workflow.workflowDependencyId) + workflow.referenceInputs.map(VideoComfyReferenceInput::dependencyId)
    require(consumed.all { byId[it]?.ownedPath != null }) {
        "Every consumed ComfyUI workflow/reference dependency needs a digest-pinned owned path"
    }
}

private val COMFY_NODE_ID = Regex("[A-Za-z0-9._~-]{1,256}")
private val COMFY_INPUT_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,127}")
private val COMFY_UPLOAD_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
private val COMFY_EXTENSION = Regex("[a-z0-9]{1,12}")

/** Persistence controls cannot become output artifacts, including filesystem aliases. */
internal object VideoJobControlPaths {
    const val DOCUMENT = "video-jobs.json"
    const val LOCK = ".video-jobs.lock"
    const val STAGING_PREFIX = ".video-jobs.save-"
    const val RECOVERY_PREFIX = ".video-jobs.recovery-"

    fun isReservedName(segment: String): Boolean {
        val name = segment.trimEnd(' ', '.')
        return name.equals(DOCUMENT, ignoreCase = true) || name.equals(LOCK, ignoreCase = true) ||
            name.startsWith(STAGING_PREFIX, ignoreCase = true) ||
            name.startsWith(RECOVERY_PREFIX, ignoreCase = true)
    }
}

const val MAX_VIDEO_GENERATION_ATTEMPTS = 3
private val VIDEO_JOB_SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
private val VIDEO_JOB_SHA_256 = Regex("[0-9a-f]{64}")
private val CURRENCY = Regex("[A-Z]{3}")
