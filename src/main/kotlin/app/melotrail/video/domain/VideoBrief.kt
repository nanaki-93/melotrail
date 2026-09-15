package app.melotrail.video.domain

/** User-authored authority for one proposed silent video. Text is retained code-unit-for-code-unit. */
data class VideoBrief(
    val prompt: String,
    val selectedReferences: List<VideoBriefReference>,
    val durationSeconds: Int = DEFAULT_DURATION_SECONDS,
    val guidance: VideoBriefGuidance = VideoBriefGuidance(),
    val shotOverrides: List<VideoShotOverride> = emptyList(),
) {
    init {
        requireFreeFormText(prompt, "Primary video prompt")
        require(selectedReferences.isNotEmpty() && selectedReferences.size <= MAX_REFERENCES) {
            "A video brief requires between 1 and $MAX_REFERENCES selected references"
        }
        require(durationSeconds in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
            "Video duration must be within $MIN_DURATION_SECONDS..$MAX_DURATION_SECONDS seconds"
        }
        require(shotOverrides.size <= MAX_SHOT_OVERRIDES) {
            "A video brief supports at most $MAX_SHOT_OVERRIDES per-shot overrides"
        }
        require(shotOverrides.map(VideoShotOverride::shotNumber).distinct().size == shotOverrides.size) {
            "Per-shot override numbers must be unique"
        }
    }

    companion object {
        const val MIN_DURATION_SECONDS = 180
        const val DEFAULT_DURATION_SECONDS = 240
        const val MAX_DURATION_SECONDS = 300
        const val MAX_REFERENCES = 64
        const val MAX_SHOT_OVERRIDES = 60
    }
}

/**
 * One explicitly selected immutable original. Thumbnail bytes are never generation input.
 * [role] is the role for this brief and may intentionally differ from the imported asset descriptor.
 */
data class VideoBriefReference(
    val assetId: VideoVersionedId,
    val original: VideoArtifact,
    val role: VideoReferenceRole? = null,
) {
    companion object {
        fun from(asset: VideoAsset, role: VideoReferenceRole? = asset.role): VideoBriefReference =
            VideoBriefReference(asset.id, asset.original.artifact, role)
    }
}

/** Optional user text. No field is inferred from the primary prompt. */
data class VideoBriefGuidance(
    val action: String? = null,
    val camera: String? = null,
    val motion: String? = null,
    val style: String? = null,
    val guidelines: List<String> = emptyList(),
) {
    init {
        action?.let { requireFreeFormText(it, "Action guidance") }
        camera?.let { requireFreeFormText(it, "Camera guidance") }
        motion?.let { requireFreeFormText(it, "Motion guidance") }
        style?.let { requireFreeFormText(it, "Style guidance") }
        require(guidelines.size <= 32) { "At most 32 user guidelines are supported" }
        guidelines.forEach { requireFreeFormText(it, "User guideline") }
    }
}

data class VideoShotOverride(
    /** One-based position among the proposed unique shots. */
    val shotNumber: Int,
    val prompt: String? = null,
    val durationSeconds: Int? = null,
) {
    init {
        require(shotNumber > 0) { "Shot override number must be positive" }
        prompt?.let { requireFreeFormText(it, "Per-shot prompt") }
        durationSeconds?.let { require(it > 0) { "Per-shot duration must be positive" } }
        require(prompt != null || durationSeconds != null) { "A per-shot override must change prompt or duration" }
    }
}

enum class VideoFootageMode { UNIQUE, EXPLICIT_REUSE }

enum class VideoGuidanceKind { ACTION, CAMERA, MOTION, STYLE, USER_GUIDELINE, STANDARD_GUIDELINE }

/**
 * A caller-provided capability snapshot. It describes syntax and bounds only; constructing it does
 * not assert that a backend is installed, measured, selected, or ready for inference.
 */
data class VideoPromptBackendCapabilities(
    val backendId: String,
    val capabilityVersion: String,
    val supportsPrimaryPrompt: Boolean,
    val maximumReferenceImages: Int,
    val supportedReferenceRoles: Set<VideoReferenceRole>,
    val supportsReferencesWithoutRole: Boolean,
    val supportedGuidance: Set<VideoGuidanceKind>,
    val minimumClipDurationSeconds: Int,
    val maximumClipDurationSeconds: Int,
    val modelDependencies: List<VideoDependencyPin>,
    val workflowDependencies: List<VideoDependencyPin>,
    val settingsDependencies: List<VideoDependencyPin>,
    /** Pins the requested output geometry, cadence and format, including native generation configuration. */
    val outputConfiguration: VideoDependencyPin,
) {
    init {
        requireStableToken(backendId, "Backend ID")
        requireStableToken(capabilityVersion, "Capability version")
        require(maximumReferenceImages >= 0) { "Maximum reference-image count must not be negative" }
        require(minimumClipDurationSeconds > 0 && maximumClipDurationSeconds >= minimumClipDurationSeconds) {
            "Backend clip-duration bounds are invalid"
        }
        requireUniquePins(modelDependencies, "Model")
        requireUniquePins(workflowDependencies, "Workflow")
        requireUniquePins(settingsDependencies, "Settings")
    }

    fun supportsClipDuration(seconds: Int): Boolean =
        seconds in minimumClipDurationSeconds..maximumClipDurationSeconds
}

/** Digest-pinned version/configuration input supplied by the selected backend adapter. */
data class VideoDependencyPin(val id: String, val sha256: String) {
    init {
        requireStableToken(id, "Dependency ID")
        require(SHA_256.matches(sha256)) { "Dependency SHA-256 must be lowercase hexadecimal" }
    }
}

/** Named hash used to compare only the inputs consumed by an individual pending request. */
data class VideoInputDependency(val key: String, val sha256: String) {
    init {
        require(key.isNotBlank() && key.length <= 512 && key.none(Char::isISOControl)) {
            "Input dependency key is invalid"
        }
        require(SHA_256.matches(sha256)) { "Input dependency SHA-256 must be lowercase hexadecimal" }
    }
}

internal fun requireFreeFormText(value: String, label: String, maximumLength: Int = 20_000) {
    require(value.isNotBlank() && value.length <= maximumLength && value.none(::isForbiddenPromptControl)) {
        "$label must contain 1..$maximumLength characters and no binary control characters"
    }
}

private fun isForbiddenPromptControl(character: Char): Boolean =
    character.isISOControl() && character != '\n' && character != '\r' && character != '\t'

private fun requireStableToken(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 200 && value.none(Char::isISOControl)) { "$label is invalid" }
}

private fun requireUniquePins(pins: List<VideoDependencyPin>, label: String) {
    require(pins.map(VideoDependencyPin::id).distinct().size == pins.size) { "$label dependency IDs must be unique" }
}

private val SHA_256 = Regex("[0-9a-f]{64}")
