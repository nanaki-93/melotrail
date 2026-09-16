package app.melotrail.video

import app.melotrail.video.application.VideoPromptCompilationStatus
import app.melotrail.video.application.VideoPromptCompiler
import app.melotrail.video.application.VideoPromptIssueCode
import app.melotrail.video.application.VideoReferenceBindingProblem
import app.melotrail.video.application.VideoReferenceBindingStatus
import app.melotrail.video.application.VideoSceneAppearancePolicy
import app.melotrail.video.application.VideoSceneLook
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoBrief
import app.melotrail.video.domain.VideoBriefGuidance
import app.melotrail.video.domain.VideoBriefReference
import app.melotrail.video.domain.VideoDependencyPin
import app.melotrail.video.domain.VideoGuidanceKind
import app.melotrail.video.domain.VideoImageFormat
import app.melotrail.video.domain.VideoPromptBackendCapabilities
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoVersionedId
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VideoPromptCompilerTest {
    private val compiler = VideoPromptCompiler()

    @Test
    fun `multiline free-form prompt and user refinements remain exact and precede generic guidance`() {
        val prompt = "  A hand-painted paper world.\nKeep the subject still for a quiet pause.  "
        val action = "A leaf crosses the foreground.\nNothing else changes."
        val style = "Muted ink wash; preserve the supplied silhouette."
        val compilation = compiler.compile(
            brief(
                prompt = prompt,
                guidance = VideoBriefGuidance(action = action, style = style),
            ),
            capabilities(),
            guidelines(),
        )
        val backendPrompt = requireNotNull(compilation.backendPrompt)

        assertEquals(VideoPromptCompilationStatus.READY, compilation.status)
        assertEquals(prompt, compilation.primaryPrompt)
        assertTrue(backendPrompt.startsWith(prompt))
        assertTrue(backendPrompt.contains("Action:\n$action"))
        assertTrue(backendPrompt.contains("Style:\n$style"))
        assertTrue(backendPrompt.indexOf(style) < backendPrompt.indexOf("reference-fidelity"))
        assertEquals(listOf(VideoReferenceBindingStatus.BOUND), compilation.bindings.map { it.status })
        assertFalse(backendPrompt.contains("Tokyo", ignoreCase = true))
        assertFalse(backendPrompt.contains("train", ignoreCase = true))
        assertFalse(backendPrompt.contains("coffee", ignoreCase = true))
    }

    @Test
    fun `finished artwork motion compilation binds one exact look and forbids appearance redesign`() {
        val prompt = "  Blink once, then let the window light drift.\r\nKeep the camera still.  "
        val image = VideoAssetImage(
            VideoArtifact("references/finished/v2/original.png", "a".repeat(64)),
            VideoImageFormat.PNG,
            "image/png",
            1280,
            720,
            12_345,
            hasAlphaChannel = true,
            hasTransparentPixels = false,
        )
        val look = VideoSceneLook(
            VideoVersionedId("finished", 2),
            VideoArtifact("references/finished/v2/descriptor.json", "b".repeat(64)),
            image,
            VideoAssetIdentityReview.APPROVED,
            VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN,
        )

        val compilation = compiler.compileMotion(prompt, look, capabilities(maximumReferences = 1), guidelines())

        assertEquals(VideoPromptCompilationStatus.READY, compilation.status)
        assertEquals(prompt, compilation.primaryPrompt)
        assertEquals(listOf(look.id), compilation.bindings.map { it.assetId })
        assertEquals(listOf(VideoReferenceRole.COMPLETE_SCENE), compilation.bindings.map { it.requestedRole })
        assertEquals(image.artifact.sha256, compilation.dependencies.single { it.key.endsWith(".asset") }.sha256)
        assertTrue(requireNotNull(compilation.backendPrompt).contains("Preserve its identity, outfit, style"))
        assertTrue(requireNotNull(compilation.backendPrompt).contains("without redesigning"))
        assertFalse(requireNotNull(compilation.backendPrompt).contains("Tokyo", ignoreCase = true))

        val unsupported = capabilities(maximumReferences = 1).copy(
            supportedGuidance = VideoGuidanceKind.entries.toSet() - VideoGuidanceKind.STANDARD_GUIDELINE,
        )
        val blocked = compiler.compileMotion(prompt, look, unsupported, guidelines())
        assertEquals(VideoPromptCompilationStatus.BLOCKED, blocked.status)
        assertFalse(blocked.canPrepareRequests)
        assertEquals(2, blocked.issues.count {
            it.code == VideoPromptIssueCode.MOTION_PRESERVATION_UNSUPPORTED && it.blocksInference
        })
        assertEquals(prompt, blocked.backendPrompt)
        assertTrue(blocked.omittedGuidance.any { it.label == "finished-artwork-preservation" })
        val generic = compiler.compile(VideoBrief(prompt, listOf(look.asBriefReference())), unsupported, guidelines())
        assertEquals(VideoPromptCompilationStatus.REVIEW_REQUIRED, generic.status)
        assertTrue(generic.canPrepareRequests)
        assertTrue(generic.issues.none { it.blocksInference })
    }

    @Test
    fun `contrasting prompts and changed immutable assets produce distinct dependency fingerprints`() {
        val guidelineSet = guidelines()
        val capabilitySet = capabilities()
        val first = compiler.compile(brief("Slow abstract shapes drift upward."), capabilitySet, guidelineSet)
        val contrast = compiler.compile(brief("Rapid geometric cuts fill the frame."), capabilitySet, guidelineSet)
        val changedAsset = compiler.compile(
            brief("Slow abstract shapes drift upward.", assetSha = "b".repeat(64)),
            capabilitySet,
            guidelineSet,
        )

        assertNotEquals(first.requestFingerprint, contrast.requestFingerprint)
        assertNotEquals(first.requestFingerprint, changedAsset.requestFingerprint)
        assertEquals(
            sha256("Slow abstract shapes drift upward.".toByteArray()),
            first.dependencies.single { it.key == "prompt.primary" }.sha256,
        )
        assertEquals("a".repeat(64), first.dependencies.single { it.key.endsWith(".asset") }.sha256)
        assertEquals("b".repeat(64), changedAsset.dependencies.single { it.key.endsWith(".asset") }.sha256)
    }

    @Test
    fun `multiple character outfit and scenery pictures bind independently with role-scoped guidance`() {
        val prompt = "  Keep a traveler recognizable while the landscape changes.\nUse a quiet illustrated treatment.  "
        val references = listOf(
            reference("character-one", VideoReferenceRole.CHARACTER, "1".repeat(64)),
            reference("character-two", VideoReferenceRole.CHARACTER, "2".repeat(64)),
            reference("outfit-one", VideoReferenceRole.OUTFIT, "3".repeat(64)),
            reference("outfit-two", VideoReferenceRole.OUTFIT, "4".repeat(64)),
            reference("city-one", VideoReferenceRole.ENVIRONMENT, "5".repeat(64)),
            reference("city-two", VideoReferenceRole.ENVIRONMENT, "6".repeat(64)),
            reference("style", VideoReferenceRole.STYLE, "7".repeat(64)),
            reference("scene", VideoReferenceRole.COMPLETE_SCENE, "8".repeat(64)),
            reference("unassigned", null, "9".repeat(64)),
        )

        val compilation = compiler.compile(VideoBrief(prompt, references), capabilities(maximumReferences = 9), guidelines())
        val backendPrompt = requireNotNull(compilation.backendPrompt)

        assertEquals(VideoPromptCompilationStatus.READY, compilation.status)
        assertEquals(prompt, compilation.primaryPrompt)
        assertTrue(backendPrompt.startsWith(prompt))
        assertEquals(references.map { it.role }, compilation.bindings.map { it.requestedRole })
        assertEquals(references.map { it.original.sha256 }, compilation.bindings.map { it.artifact.sha256 })
        assertTrue(compilation.bindings.all { it.status == VideoReferenceBindingStatus.BOUND })
        compilation.bindings.filter { it.requestedRole != null }.forEach { binding ->
            assertEquals(binding.requestedRole, binding.roleGuidance?.role)
        }
        assertEquals(null, compilation.bindings.last().roleGuidance)
        assertTrue(backendPrompt.contains("poses and expressions"))
        assertTrue(backendPrompt.contains("without copying a depicted model's identity"))
        assertTrue(backendPrompt.contains("Do not transfer depicted people, clothing"))
        assertTrue(backendPrompt.contains("Retain the selected style"))
        assertEquals(9, compilation.dependencies.count { it.key.endsWith(".asset") })
        assertEquals(9, compilation.dependencies.count { it.key.endsWith(".binding") })
    }

    @Test
    fun `character outfit and scenery groups may each be absent`() {
        val compilation = compiler.compile(
            VideoBrief(
                "Use only the selected supplementary inspiration.",
                listOf(
                    reference("style-only", VideoReferenceRole.STYLE, "a".repeat(64)),
                    reference("unassigned", null, "b".repeat(64)),
                ),
            ),
            capabilities(maximumReferences = 2),
            guidelines(),
        )

        assertEquals(VideoPromptCompilationStatus.READY, compilation.status)
        assertTrue(compilation.appliedGuidance.none {
            it.referenceRole != null && it.referenceRole in setOf(
                VideoReferenceRole.SUBJECT,
                VideoReferenceRole.CHARACTER,
                VideoReferenceRole.OUTFIT,
                VideoReferenceRole.ENVIRONMENT,
            )
        })
        assertEquals(listOf(VideoReferenceRole.STYLE, null), compilation.bindings.map { it.requestedRole })
    }

    @Test
    fun `outfit image and explicit role changes alter identity while unrelated inputs stay pinned`() {
        val prompt = "Preserve the subject and landscape while applying the selected clothes."
        val originalReferences = listOf(
            reference("character", VideoReferenceRole.SUBJECT, "a".repeat(64)),
            reference("outfit", VideoReferenceRole.OUTFIT, "b".repeat(64)),
            reference("city", VideoReferenceRole.ENVIRONMENT, "c".repeat(64)),
            reference("style", VideoReferenceRole.STYLE, "d".repeat(64)),
        )
        val original = compiler.compile(VideoBrief(prompt, originalReferences), capabilities(), guidelines())
        val changedOutfit = compiler.compile(
            VideoBrief(prompt, originalReferences.toMutableList().also {
                it[1] = reference("outfit", VideoReferenceRole.OUTFIT, "e".repeat(64))
            }),
            capabilities(),
            guidelines(),
        )
        val reassignedOutfit = compiler.compile(
            VideoBrief(prompt, originalReferences.toMutableList().also {
                it[1] = it[1].copy(role = VideoReferenceRole.COMPLETE_SCENE)
            }),
            capabilities(),
            guidelines(),
        )

        assertNotEquals(original.requestFingerprint, changedOutfit.requestFingerprint)
        assertNotEquals(original.requestFingerprint, reassignedOutfit.requestFingerprint)
        assertEquals(
            original.dependencies.filter { ".character." in it.key || ".city." in it.key || ".style." in it.key },
            changedOutfit.dependencies.filter { ".character." in it.key || ".city." in it.key || ".style." in it.key },
        )
        assertEquals("b".repeat(64), original.bindings[1].artifact.sha256)
        assertEquals(VideoReferenceRole.OUTFIT, original.bindings[1].requestedRole)
        assertEquals(VideoReferenceRole.COMPLETE_SCENE, reassignedOutfit.bindings[1].requestedRole)
    }

    @Test
    fun `unsupported capacity roles and user guidance stay visible and block inference`() {
        val first = reference("first", VideoReferenceRole.SUBJECT, "a".repeat(64))
        val second = reference("second", VideoReferenceRole.OUTFIT, "b".repeat(64))
        val third = reference("third", null, "c".repeat(64))
        val limited = capabilities(
            maximumReferences = 1,
            roles = setOf(VideoReferenceRole.SUBJECT),
            supportsUnassigned = false,
            guidance = setOf(VideoGuidanceKind.STANDARD_GUIDELINE),
        )

        val compilation = compiler.compile(
            VideoBrief(
                prompt = "Use the selected references in a restrained composition.",
                selectedReferences = listOf(first, second, third),
                guidance = VideoBriefGuidance(camera = "Locked camera."),
            ),
            limited,
            guidelines(),
        )

        assertEquals(VideoPromptCompilationStatus.BLOCKED, compilation.status)
        assertFalse(compilation.canPrepareRequests)
        assertEquals(VideoReferenceBindingStatus.BOUND, compilation.bindings[0].status)
        assertEquals(VideoReferenceBindingProblem.ROLE_UNSUPPORTED, compilation.bindings[1].problem)
        assertEquals(VideoReferenceBindingProblem.UNASSIGNED_ROLE_UNSUPPORTED, compilation.bindings[2].problem)
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.REFERENCE_ROLE_UNSUPPORTED })
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.UNASSIGNED_REFERENCE_UNSUPPORTED })
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.USER_GUIDANCE_UNSUPPORTED })
        assertEquals("Locked camera.", compilation.omittedGuidance.single { it.label == "Camera" }.text)
        assertFalse(requireNotNull(compilation.backendPrompt).contains("Locked camera."))
        assertEquals(3, compilation.bindings.size)
    }

    @Test
    fun `reference overflow and conflicting duplicate roles never disappear from the binding report`() {
        val duplicatedSubject = reference("same", VideoReferenceRole.SUBJECT, "a".repeat(64))
        val duplicatedStyle = reference("same", VideoReferenceRole.STYLE, "a".repeat(64))
        val overflow = reference("overflow", VideoReferenceRole.SUBJECT, "b".repeat(64))
        val compilation = compiler.compile(
            VideoBrief("Animate only what the request specifies.", listOf(duplicatedSubject, duplicatedStyle, overflow)),
            capabilities(maximumReferences = 0),
            guidelines(),
        )

        assertEquals(3, compilation.bindings.size)
        assertEquals(
            listOf(
                VideoReferenceBindingProblem.CONFLICTING_SELECTION,
                VideoReferenceBindingProblem.CONFLICTING_SELECTION,
                VideoReferenceBindingProblem.CAPACITY_EXCEEDED,
            ),
            compilation.bindings.map { it.problem },
        )
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.CONFLICTING_REFERENCE_ROLES })
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.REFERENCE_CAPACITY_EXCEEDED })
    }

    @Test
    fun `unsupported primary text produces no backend text input`() {
        val unsupported = capabilities().copy(supportsPrimaryPrompt = false)
        val compilation = compiler.compile(brief("This exact prompt cannot be represented."), unsupported, guidelines())

        assertEquals(VideoPromptCompilationStatus.BLOCKED, compilation.status)
        assertEquals("This exact prompt cannot be represented.", compilation.primaryPrompt)
        assertEquals(null, compilation.backendPrompt)
        assertTrue(compilation.issues.any { it.code == VideoPromptIssueCode.PRIMARY_PROMPT_UNSUPPORTED })
    }

    @Test
    fun `model workflow settings capability and exact resource bytes are fingerprinted`() {
        val resource = guidelineBytes()
        val guidelineSet = compiler.decodeGuidelines(resource)
        val capabilitySet = capabilities()
        val compilation = compiler.compile(brief("A generic visual request."), capabilitySet, guidelineSet)

        assertEquals(sha256(resource), compilation.dependencies.single { it.key == "template.guidelines" }.sha256)
        assertEquals("1".repeat(64), compilation.dependencies.single { it.key == "model.model-fixture" }.sha256)
        assertEquals("2".repeat(64), compilation.dependencies.single { it.key == "workflow.workflow-fixture" }.sha256)
        assertEquals("3".repeat(64), compilation.dependencies.single { it.key == "settings.settings-fixture" }.sha256)
        assertTrue(compilation.dependencies.any { it.key == "backend.capabilities" })
        assertEquals("6".repeat(64), compilation.dependencies.single { it.key == "output.output-fixture" }.sha256)

        val changedModel = compiler.compile(
            brief("A generic visual request."),
            capabilitySet.copy(modelDependencies = listOf(VideoDependencyPin("model-fixture", "4".repeat(64)))),
            guidelineSet,
        )
        assertNotEquals(compilation.requestFingerprint, changedModel.requestFingerprint)
    }

    @Test
    fun `guideline decoder rejects unknown versions and prompt validation permits line endings but not binary controls`() {
        val unsupported = String(guidelineBytes()).replace("\"version\": 2", "\"version\": 3")
        assertFailsWith<IllegalArgumentException> { compiler.decodeGuidelines(unsupported) }
        assertFailsWith<IllegalArgumentException> { brief("visible\u0000binary") }

        val crlf = "First line\r\nSecond line"
        assertEquals(crlf, compiler.compile(brief(crlf), capabilities(), guidelines()).primaryPrompt)
    }

    @Test
    fun `total cut duration does not change the compiled clip prompt or its consumed dependencies`() {
        val originalBrief = brief("Keep the paper shapes gently moving.")
        val original = compiler.compile(originalBrief, capabilities(), guidelines())
        val longer = compiler.compile(originalBrief.copy(durationSeconds = 246), capabilities(), guidelines())

        assertEquals(original, longer)
        assertTrue(original.dependencies.none { it.key == "brief.duration" })
    }

    @Test
    fun `explicit prompt template version and output configuration change request identity`() {
        val originalBrief = brief("Keep the reference silhouette.")
        val originalGuidelines = guidelines()
        val original = compiler.compile(originalBrief, capabilities(), originalGuidelines)
        val versionChange = compiler.compile(
            originalBrief,
            capabilities(),
            originalGuidelines.copy(document = originalGuidelines.document.copy(promptTemplateVersion = "fixture-v2")),
        )
        val outputChange = compiler.compile(
            originalBrief,
            capabilities().copy(outputConfiguration = VideoDependencyPin("output-fixture", "7".repeat(64))),
            originalGuidelines,
        )

        assertEquals(original.backendPrompt, versionChange.backendPrompt)
        assertNotEquals(original.requestFingerprint, versionChange.requestFingerprint)
        assertEquals(original.backendPrompt, outputChange.backendPrompt)
        assertNotEquals(original.requestFingerprint, outputChange.requestFingerprint)
    }

    private fun brief(
        prompt: String,
        assetSha: String = "a".repeat(64),
        guidance: VideoBriefGuidance = VideoBriefGuidance(),
    ): VideoBrief = VideoBrief(prompt, listOf(reference("subject", VideoReferenceRole.SUBJECT, assetSha)), guidance = guidance)

    private fun reference(id: String, role: VideoReferenceRole?, sha256: String): VideoBriefReference =
        VideoBriefReference(VideoVersionedId(id, 1), VideoArtifact("references/$id.png", sha256), role)

    private fun capabilities(
        maximumReferences: Int = 8,
        roles: Set<VideoReferenceRole> = VideoReferenceRole.entries.toSet(),
        supportsUnassigned: Boolean = true,
        guidance: Set<VideoGuidanceKind> = VideoGuidanceKind.entries.toSet(),
        minimumClipSeconds: Int = 5,
        maximumClipSeconds: Int = 10,
    ): VideoPromptBackendCapabilities = VideoPromptBackendCapabilities(
        backendId = "synthetic-capability-fixture",
        capabilityVersion = "fixture-v1",
        supportsPrimaryPrompt = true,
        maximumReferenceImages = maximumReferences,
        supportedReferenceRoles = roles,
        supportsReferencesWithoutRole = supportsUnassigned,
        supportedGuidance = guidance,
        minimumClipDurationSeconds = minimumClipSeconds,
        maximumClipDurationSeconds = maximumClipSeconds,
        modelDependencies = listOf(VideoDependencyPin("model-fixture", "1".repeat(64))),
        workflowDependencies = listOf(VideoDependencyPin("workflow-fixture", "2".repeat(64))),
        settingsDependencies = listOf(VideoDependencyPin("settings-fixture", "3".repeat(64))),
        outputConfiguration = VideoDependencyPin("output-fixture", "6".repeat(64)),
    )

    private fun guidelines() = compiler.decodeGuidelines(guidelineBytes())

    private fun guidelineBytes(): ByteArray = checkNotNull(javaClass.getResource("/video/video-generation-guidelines.json"))
        .readBytes()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
