package app.melotrail.video.application

import app.melotrail.video.domain.VideoBrief
import app.melotrail.video.domain.VideoBriefReference
import app.melotrail.video.domain.VideoGuidanceKind
import app.melotrail.video.domain.VideoInputDependency
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.requireFreeFormText
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Pure prompt preparation. Resource bytes and backend facts are supplied by the composition root. */
class VideoPromptCompiler {
    fun decodeGuidelines(bytes: ByteArray): VideoPromptGuidelineSet {
        val text = bytes.toString(UTF_8)
        val document = try {
            JSON.decodeFromString<VideoPromptGuidelineDocument>(text)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Invalid video generation guidelines JSON: ${error.message ?: error.javaClass.simpleName}",
                error,
            )
        }
        require(document.schema == GUIDELINE_SCHEMA && document.version == GUIDELINE_VERSION) {
            "Unsupported video generation guidelines '${document.schema}' v${document.version}"
        }
        requireStableId(document.promptTemplateVersion, "Prompt template version")
        requireStableId(document.shotPlannerVersion, "Shot planner version")
        require(document.standardGuidelines.isNotEmpty() && document.standardGuidelines.size <= 32) {
            "Video generation guidelines must contain between 1 and 32 standard guidelines"
        }
        require(document.standardGuidelines.map(VideoStandardGuideline::id).distinct().size ==
            document.standardGuidelines.size) { "Standard guideline IDs must be unique" }
        document.standardGuidelines.forEach { guideline ->
            requireStableId(guideline.id, "Standard guideline ID")
            requireFreeFormText(guideline.text, "Standard guideline")
        }
        require(document.motionGuidelines.isNotEmpty() && document.motionGuidelines.size <= 16) {
            "Video generation guidelines must contain between 1 and 16 finished-artwork motion guidelines"
        }
        require(document.motionGuidelines.map(VideoStandardGuideline::id).distinct().size ==
            document.motionGuidelines.size) { "Finished-artwork motion guideline IDs must be unique" }
        document.motionGuidelines.forEach { guideline ->
            requireStableId(guideline.id, "Finished-artwork motion guideline ID")
            requireFreeFormText(guideline.text, "Finished-artwork motion guideline")
        }
        require((document.standardGuidelines + document.motionGuidelines).map(VideoStandardGuideline::id).distinct().size ==
            document.standardGuidelines.size + document.motionGuidelines.size) {
            "Standard and finished-artwork motion guideline IDs must not overlap"
        }
        require(document.referenceRoleGuidance.size == VideoReferenceRole.entries.size) {
            "Video generation guidelines must define every reference role exactly once"
        }
        require(document.referenceRoleGuidance.map(VideoReferenceRoleGuidance::role).toSet() ==
            VideoReferenceRole.entries.toSet()) {
            "Video generation guidelines must define every reference role exactly once"
        }
        require(document.referenceRoleGuidance.map(VideoReferenceRoleGuidance::id).distinct().size ==
            document.referenceRoleGuidance.size) { "Reference-role guideline IDs must be unique" }
        document.referenceRoleGuidance.forEach { guideline ->
            requireStableId(guideline.id, "Reference-role guideline ID")
            requireStableId(guideline.label, "Reference-role guideline label")
            requireFreeFormText(guideline.text, "Reference-role guideline")
        }
        return VideoPromptGuidelineSet(document, sha256(bytes))
    }

    fun decodeGuidelines(text: String): VideoPromptGuidelineSet = decodeGuidelines(text.toByteArray(UTF_8))

    /**
     * Compiles a motion-only request around one already-finished scene. The exact user text remains
     * [VideoPromptCompilation.primaryPrompt]; the immutable uploaded picture is the only look binding.
     */
    fun compileMotion(
        motionPrompt: String,
        look: VideoSceneLook,
        capabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
    ): VideoPromptCompilation = compileRequest(
        VideoBrief(
            prompt = motionPrompt,
            selectedReferences = listOf(look.asBriefReference()),
        ),
        capabilities,
        guidelineSet,
        guidelineSet.document.motionGuidelines,
        requireAppearancePreservation = true,
    )

    fun compile(
        brief: VideoBrief,
        capabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
    ): VideoPromptCompilation = compileRequest(brief, capabilities, guidelineSet, emptyList())

    private fun compileRequest(
        brief: VideoBrief,
        capabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
        additionalStandardGuidelines: List<VideoStandardGuideline>,
        requireAppearancePreservation: Boolean = false,
    ): VideoPromptCompilation {
        val issues = mutableListOf<VideoPromptIssue>()
        if (!capabilities.supportsPrimaryPrompt) {
            issues += issue(
                VideoPromptIssueCode.PRIMARY_PROMPT_UNSUPPORTED,
                "Backend '${capabilities.backendId}' cannot consume the required primary prompt.",
            )
        }

        val duplicateAssetIds = brief.selectedReferences.groupBy(VideoBriefReference::assetId)
            .filterValues { it.size > 1 }
            .keys
        duplicateAssetIds.forEach { id ->
            val roles = brief.selectedReferences.filter { it.assetId == id }.map { it.role }.distinct()
            issues += issue(
                if (roles.size > 1) VideoPromptIssueCode.CONFLICTING_REFERENCE_ROLES
                else VideoPromptIssueCode.DUPLICATE_REFERENCE_SELECTION,
                if (roles.size > 1) {
                    "Reference ${id.display()} has conflicting selected roles; choose one role before inference."
                } else {
                    "Reference ${id.display()} is selected more than once; choose one binding before inference."
                },
            )
        }

        val roleGuidanceByRole = guidelineSet.document.referenceRoleGuidance.associateBy { it.role }
        var boundCount = 0
        val bindings = brief.selectedReferences.map { reference ->
            val unsupportedReason = when {
                reference.assetId in duplicateAssetIds -> VideoReferenceBindingProblem.CONFLICTING_SELECTION
                reference.role == null && !capabilities.supportsReferencesWithoutRole ->
                    VideoReferenceBindingProblem.UNASSIGNED_ROLE_UNSUPPORTED
                reference.role != null && reference.role !in capabilities.supportedReferenceRoles ->
                    VideoReferenceBindingProblem.ROLE_UNSUPPORTED
                boundCount >= capabilities.maximumReferenceImages -> VideoReferenceBindingProblem.CAPACITY_EXCEEDED
                else -> null
            }
            if (unsupportedReason == null) boundCount++
            VideoCompiledReferenceBinding(
                assetId = reference.assetId,
                artifact = reference.original,
                requestedRole = reference.role,
                roleGuidance = reference.role?.let(roleGuidanceByRole::getValue),
                status = if (unsupportedReason == null) VideoReferenceBindingStatus.BOUND else VideoReferenceBindingStatus.NOT_BOUND,
                problem = unsupportedReason,
            )
        }
        bindings.filter { it.problem != null }.forEach { binding ->
            val code = when (binding.problem) {
                VideoReferenceBindingProblem.CONFLICTING_SELECTION -> null // already reported once per asset
                VideoReferenceBindingProblem.UNASSIGNED_ROLE_UNSUPPORTED -> VideoPromptIssueCode.UNASSIGNED_REFERENCE_UNSUPPORTED
                VideoReferenceBindingProblem.ROLE_UNSUPPORTED -> VideoPromptIssueCode.REFERENCE_ROLE_UNSUPPORTED
                VideoReferenceBindingProblem.CAPACITY_EXCEEDED -> VideoPromptIssueCode.REFERENCE_CAPACITY_EXCEEDED
                null -> null
            }
            if (code != null) {
                issues += issue(code, binding.problemMessage(capabilities))
            }
        }

        val requestedGuidance = buildList {
            brief.guidance.action?.let { add(VideoAppliedGuidance(VideoGuidanceKind.ACTION, "Action", it, false)) }
            brief.guidance.camera?.let { add(VideoAppliedGuidance(VideoGuidanceKind.CAMERA, "Camera", it, false)) }
            brief.guidance.motion?.let { add(VideoAppliedGuidance(VideoGuidanceKind.MOTION, "Motion", it, false)) }
            brief.guidance.style?.let { add(VideoAppliedGuidance(VideoGuidanceKind.STYLE, "Style", it, false)) }
            brief.guidance.guidelines.forEachIndexed { index, text ->
                add(VideoAppliedGuidance(VideoGuidanceKind.USER_GUIDELINE, "User guideline ${index + 1}", text, false))
            }
            brief.selectedReferences.mapNotNull(VideoBriefReference::role).distinct().forEach { role ->
                val guideline = roleGuidanceByRole.getValue(role)
                add(VideoAppliedGuidance(
                    kind = VideoGuidanceKind.STANDARD_GUIDELINE,
                    label = guideline.label,
                    text = guideline.text,
                    standard = true,
                    referenceRole = role,
                ))
            }
            additionalStandardGuidelines.forEach { guideline ->
                add(VideoAppliedGuidance(VideoGuidanceKind.STANDARD_GUIDELINE, guideline.id, guideline.text, true))
            }
            guidelineSet.document.standardGuidelines.forEach { guideline ->
                add(VideoAppliedGuidance(VideoGuidanceKind.STANDARD_GUIDELINE, guideline.id, guideline.text, true))
            }
        }
        val appliedGuidance = requestedGuidance.filter { it.kind in capabilities.supportedGuidance }
        val omittedGuidance = requestedGuidance.filterNot { it.kind in capabilities.supportedGuidance }
        omittedGuidance.forEach { guidance ->
            // Motion preparation must represent preservation in the backend request. Ordinary
            // compile() standards remain advisory; this does not assert artistic fidelity.
            val requiredPreservation = requireAppearancePreservation &&
                (guidance.referenceRole == VideoReferenceRole.COMPLETE_SCENE ||
                    additionalStandardGuidelines.any { it.id == guidance.label })
            issues += VideoPromptIssue(
                code = when {
                    requiredPreservation -> VideoPromptIssueCode.MOTION_PRESERVATION_UNSUPPORTED
                    guidance.standard -> VideoPromptIssueCode.STANDARD_GUIDANCE_UNSUPPORTED
                    else -> VideoPromptIssueCode.USER_GUIDANCE_UNSUPPORTED
                },
                message = "Backend '${capabilities.backendId}' cannot consume ${guidance.label} guidance; it remains visible and is not in the backend prompt.",
                blocksInference = requiredPreservation || !guidance.standard,
            )
        }

        val backendPrompt = if (capabilities.supportsPrimaryPrompt) {
            buildString {
                append(brief.prompt)
                appliedGuidance.forEach { guidance ->
                    append("\n\n")
                    append(guidance.label)
                    append(":\n")
                    append(guidance.text)
                }
            }
        } else null
        val dependencies = dependencies(brief, capabilities, guidelineSet, requestedGuidance)
        val status = when {
            issues.any(VideoPromptIssue::blocksInference) -> VideoPromptCompilationStatus.BLOCKED
            issues.isNotEmpty() -> VideoPromptCompilationStatus.REVIEW_REQUIRED
            else -> VideoPromptCompilationStatus.READY
        }
        return VideoPromptCompilation(
            status = status,
            primaryPrompt = brief.prompt,
            backendPrompt = backendPrompt,
            bindings = bindings,
            appliedGuidance = appliedGuidance,
            omittedGuidance = omittedGuidance,
            issues = issues,
            dependencies = dependencies,
            requestFingerprint = fingerprint(dependencies),
            promptTemplateVersion = guidelineSet.document.promptTemplateVersion,
            shotPlannerVersion = guidelineSet.document.shotPlannerVersion,
        )
    }

    private fun dependencies(
        brief: VideoBrief,
        capabilities: VideoPromptBackendCapabilities,
        guidelineSet: VideoPromptGuidelineSet,
        requestedGuidance: List<VideoAppliedGuidance>,
    ): List<VideoInputDependency> = buildList {
        add(VideoInputDependency("prompt.primary", sha256(brief.prompt.toByteArray(UTF_8))))
        brief.selectedReferences.forEachIndexed { index, reference ->
            val prefix = "reference.${index + 1}.${reference.assetId.id}.v${reference.assetId.version}"
            add(VideoInputDependency("$prefix.asset", reference.original.sha256))
            add(VideoInputDependency(
                "$prefix.binding",
                digest(reference.original.relativePath, reference.role?.name ?: "UNASSIGNED"),
            ))
        }
        requestedGuidance.forEachIndexed { index, guidance ->
            add(VideoInputDependency(
                "guidance.${index + 1}.${guidance.kind.name.lowercase()}",
                digest(
                    guidance.label,
                    guidance.text,
                    guidance.standard.toString(),
                    guidance.referenceRole?.name ?: "ALL_REFERENCES",
                ),
            ))
        }
        add(VideoInputDependency("template.guidelines", guidelineSet.sourceSha256))
        add(VideoInputDependency("template.prompt-version", digest(guidelineSet.document.promptTemplateVersion)))
        addAll(generationProfileDependencies(capabilities))
    }

    /** Canonical profile identity shared by request fingerprints and measured timing evidence. */
    internal fun generationProfileDependencies(
        capabilities: VideoPromptBackendCapabilities,
    ): List<VideoInputDependency> = buildList {
        add(VideoInputDependency(
            "backend.capabilities",
            digest(
                capabilities.backendId,
                capabilities.capabilityVersion,
                capabilities.supportsPrimaryPrompt.toString(),
                capabilities.maximumReferenceImages.toString(),
                capabilities.supportedReferenceRoles.map { it.name }.sorted().joinToString(","),
                capabilities.supportsReferencesWithoutRole.toString(),
                capabilities.supportedGuidance.map { it.name }.sorted().joinToString(","),
                capabilities.minimumClipDurationSeconds.toString(),
                capabilities.maximumClipDurationSeconds.toString(),
            ),
        ))
        capabilities.modelDependencies.forEach { add(VideoInputDependency("model.${it.id}", it.sha256)) }
        capabilities.workflowDependencies.forEach { add(VideoInputDependency("workflow.${it.id}", it.sha256)) }
        capabilities.settingsDependencies.forEach { add(VideoInputDependency("settings.${it.id}", it.sha256)) }
        add(VideoInputDependency("output.${capabilities.outputConfiguration.id}", capabilities.outputConfiguration.sha256))
    }

    companion object {
        const val GUIDELINE_SCHEMA = "melotrail-video-generation-guidelines"
        const val GUIDELINE_VERSION = 2
        private val JSON = Json { ignoreUnknownKeys = false }
    }
}

data class VideoPromptGuidelineSet(
    val document: VideoPromptGuidelineDocument,
    val sourceSha256: String,
)

@Serializable
data class VideoPromptGuidelineDocument(
    val schema: String,
    val version: Int,
    val promptTemplateVersion: String,
    val shotPlannerVersion: String,
    val referenceRoleGuidance: List<VideoReferenceRoleGuidance>,
    val standardGuidelines: List<VideoStandardGuideline>,
    val motionGuidelines: List<VideoStandardGuideline>,
)

@Serializable
data class VideoReferenceRoleGuidance(
    val id: String,
    val role: VideoReferenceRole,
    val label: String,
    val text: String,
)

@Serializable
data class VideoStandardGuideline(val id: String, val text: String)

enum class VideoPromptCompilationStatus { READY, REVIEW_REQUIRED, BLOCKED }

data class VideoPromptCompilation(
    val status: VideoPromptCompilationStatus,
    /** The exact user-authored primary input, including line endings and surrounding whitespace. */
    val primaryPrompt: String,
    /** The exact inspectable text for a backend after capability filtering, or null if text is unsupported. */
    val backendPrompt: String?,
    val bindings: List<VideoCompiledReferenceBinding>,
    val appliedGuidance: List<VideoAppliedGuidance>,
    val omittedGuidance: List<VideoAppliedGuidance>,
    val issues: List<VideoPromptIssue>,
    val dependencies: List<VideoInputDependency>,
    val requestFingerprint: String,
    val promptTemplateVersion: String,
    val shotPlannerVersion: String,
) {
    /** Capability representation only; backend selection/availability is owned by later tasks. */
    val canPrepareRequests: Boolean get() = status != VideoPromptCompilationStatus.BLOCKED
}

data class VideoAppliedGuidance(
    val kind: VideoGuidanceKind,
    val label: String,
    val text: String,
    val standard: Boolean,
    val referenceRole: VideoReferenceRole? = null,
)

enum class VideoReferenceBindingStatus { BOUND, NOT_BOUND }

enum class VideoReferenceBindingProblem {
    CONFLICTING_SELECTION,
    UNASSIGNED_ROLE_UNSUPPORTED,
    ROLE_UNSUPPORTED,
    CAPACITY_EXCEEDED,
}

data class VideoCompiledReferenceBinding(
    val assetId: VideoVersionedId,
    val artifact: app.melotrail.video.domain.VideoArtifact,
    val requestedRole: VideoReferenceRole?,
    val roleGuidance: VideoReferenceRoleGuidance?,
    val status: VideoReferenceBindingStatus,
    val problem: VideoReferenceBindingProblem?,
)

enum class VideoPromptIssueCode {
    PRIMARY_PROMPT_UNSUPPORTED,
    DUPLICATE_REFERENCE_SELECTION,
    CONFLICTING_REFERENCE_ROLES,
    UNASSIGNED_REFERENCE_UNSUPPORTED,
    REFERENCE_ROLE_UNSUPPORTED,
    REFERENCE_CAPACITY_EXCEEDED,
    USER_GUIDANCE_UNSUPPORTED,
    STANDARD_GUIDANCE_UNSUPPORTED,
    MOTION_PRESERVATION_UNSUPPORTED,
}

data class VideoPromptIssue(val code: VideoPromptIssueCode, val message: String, val blocksInference: Boolean)

private fun issue(code: VideoPromptIssueCode, message: String): VideoPromptIssue =
    VideoPromptIssue(code, message, blocksInference = true)

private fun VideoCompiledReferenceBinding.problemMessage(capabilities: VideoPromptBackendCapabilities): String = when (problem) {
    VideoReferenceBindingProblem.UNASSIGNED_ROLE_UNSUPPORTED ->
        "Backend '${capabilities.backendId}' cannot bind unassigned reference ${assetId.display()}; choose a supported role."
    VideoReferenceBindingProblem.ROLE_UNSUPPORTED ->
        "Backend '${capabilities.backendId}' cannot bind ${requestedRole?.name} reference ${assetId.display()}."
    VideoReferenceBindingProblem.CAPACITY_EXCEEDED ->
        "Backend '${capabilities.backendId}' accepts ${capabilities.maximumReferenceImages} reference image(s); ${assetId.display()} was not bound."
    VideoReferenceBindingProblem.CONFLICTING_SELECTION ->
        "Reference ${assetId.display()} has a conflicting selection."
    null -> error("A bound reference has no problem")
}

private fun VideoVersionedId.display(): String = "$id v$version"

private fun requireStableId(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 200 && value.none(Char::isISOControl)) { "$label is invalid" }
}

internal fun digest(vararg parts: String): String {
    val canonical = buildString {
        parts.forEach { part -> append(part.length).append(':').append(part) }
    }
    return sha256(canonical.toByteArray(UTF_8))
}

internal fun fingerprint(dependencies: List<VideoInputDependency>): String = digest(
    *dependencies.sortedWith(compareBy(VideoInputDependency::key, VideoInputDependency::sha256))
        .flatMap { listOf(it.key, it.sha256) }
        .toTypedArray(),
)

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
