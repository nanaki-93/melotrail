package app.melotrail.video

import app.melotrail.video.ComfyVideoClientTest.Companion.interruptedHistory
import app.melotrail.video.ComfyVideoClientTest.Companion.json
import app.melotrail.video.ComfyVideoClientTest.Companion.successHistory
import app.melotrail.video.adapter.ComfyApiOutput
import app.melotrail.video.adapter.ComfyClientCancellation
import app.melotrail.video.adapter.ComfyClientObservation
import app.melotrail.video.adapter.ComfyClientSubmission
import app.melotrail.video.adapter.ComfyClientSubmissionResult
import app.melotrail.video.adapter.ComfyVideoApi
import app.melotrail.video.adapter.ComfyVideoRuntime
import app.melotrail.video.adapter.ComfyVideoRuntimeException
import app.melotrail.video.adapter.ComfyVideoRuntimeFailure
import app.melotrail.video.adapter.ComfyVideoSession
import app.melotrail.video.adapter.LocalVideoBackend
import app.melotrail.video.adapter.VideoJobStore
import app.melotrail.video.adapter.comfyRequestFingerprint
import app.melotrail.video.adapter.videoRequestFingerprint
import app.melotrail.video.application.VideoBackendObservation
import app.melotrail.video.application.VideoBackendSubmission
import app.melotrail.video.application.VideoBackendSubmissionCommand
import app.melotrail.video.application.VideoJobCoordinator
import app.melotrail.video.application.VideoJobProblemCode
import app.melotrail.video.application.VideoJobResult
import app.melotrail.video.application.VideoOwnedBackendAttempt
import app.melotrail.video.domain.VideoClipGenerationInput
import app.melotrail.video.domain.VideoComfyInputSlot
import app.melotrail.video.domain.VideoComfyOutputBinding
import app.melotrail.video.domain.VideoComfyReferenceInput
import app.melotrail.video.domain.VideoComfyWorkflowRequest
import app.melotrail.video.domain.VideoGenerationAttempt
import app.melotrail.video.domain.VideoControlledMotionGenerationInput
import app.melotrail.video.domain.VideoControlledMotionRequest
import app.melotrail.video.domain.controlledMotionRequestFingerprint
import app.melotrail.video.domain.VideoGenerationAttemptStatus
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoGenerationJobRequest
import app.melotrail.video.domain.VideoKeyframeGenerationInput
import app.melotrail.video.domain.VideoLocalExecutionPolicy
import app.melotrail.video.domain.VideoSubmissionPhase
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalVideoBackendTest {
    @Test
    fun `submit binds pinned owned files and completion publishes one immutable verified result`() {
        val fixture = fixture()
        val api = FakeApi()
        val closed = AtomicInteger()
        val request = fixture.request()
        val backend = fixture.backend(api, request, closed)
        val submitted = assertIs<VideoBackendSubmission.Accepted>(backend.submit(fixture.command(request)))
        val call = requireNotNull(api.submission)
        assertEquals(call.promptId, submitted.providerWorkId)
        assertEquals("Describe a calm scene", call.scalarInputs[VideoComfyInputSlot("1", "text")]?.content)
        assertEquals(fixture.reference.toRealPath(), call.uploads.single().source)
        assertEquals(49, call.scalarInputs[VideoComfyInputSlot("1", "frames")]?.content?.toInt())
        assertEquals(0, closed.get())

        val bytes = "owned generated video".toByteArray()
        val outputDirectory = fixture.session.outputDirectory.resolve("clips").createDirectories()
        Files.write(outputDirectory.resolve("take_00001_.mp4"), bytes)
        api.observation = ComfyClientObservation.Completed(ComfyApiOutput("3", "take_00001_.mp4", "clips", "output"))
        val completed = assertIs<VideoBackendObservation.Completed>(backend.observe(fixture.owned(submitted.providerWorkId)))

        assertEquals(1, closed.get())
        assertEquals(sha256(bytes), completed.output.sha256)
        assertEquals(bytes.size.toLong(), completed.output.byteCount)
        val published = fixture.publication.resolve(requireNotNull(completed.output.relativePath))
        assertContentEquals(bytes, Files.readAllBytes(published))
        assertTrue(completed.output.backendOutputId.contains("3:clips/take_00001_.mp4"))
    }

    @Test
    fun `repeated completion preserves published bytes when the source at the same name changes`() {
        val fixture = fixture()
        val api = FakeApi()
        val request = fixture.request()
        val backend = fixture.backend(api, request)
        val submitted = assertIs<VideoBackendSubmission.Accepted>(backend.submit(fixture.command(request)))
        val owned = fixture.owned(submitted.providerWorkId)
        val source = fixture.session.outputDirectory.resolve("take.mp4")
        val original = "original take".toByteArray()
        Files.write(source, original)
        api.observation = ComfyClientObservation.Completed(ComfyApiOutput("3", "take.mp4", "", "output"))
        val completed = assertIs<VideoBackendObservation.Completed>(backend.observe(owned))
        val published = fixture.publication.resolve(requireNotNull(completed.output.relativePath))
        assertEquals(completed, backend.observe(owned))

        Files.write(source, "conflict take".toByteArray())
        val restarted = fixture.backend(api, request)
        val conflict = assertIs<VideoBackendObservation.Failed>(restarted.observe(owned))

        assertTrue(conflict.reason.contains("changed while publishing"))
        assertContentEquals(original, Files.readAllBytes(published))
        assertEquals(completed.output.sha256, sha256(published))
    }

    @Test
    fun `non-object workflow is rejected before acquiring a slot or submitting`() {
        val fixture = fixture()
        Files.writeString(fixture.workflow, "[]")
        val request = fixture.request()
        val api = FakeApi()
        val slots = AtomicInteger()
        val backend = fixture.backend(api, request, acquire = {
            slots.incrementAndGet()
            true
        })

        val rejected = assertIs<VideoBackendSubmission.Rejected>(backend.submit(fixture.command(request)))

        assertTrue(rejected.reason.contains("API-format JSON object"))
        assertEquals(0, slots.get())
        assertEquals(0, api.submitCalls)
    }

    @Test
    fun `ambiguous acknowledgement reconstructs stable identity without a duplicate submit`() {
        val fixture = fixture()
        val request = fixture.request()
        val firstApi = FakeApi().apply { submitResult = ComfyClientSubmissionResult.Uncertain("reply closed") }
        val first = fixture.backend(firstApi, request)
        assertIs<VideoBackendSubmission.Uncertain>(first.submit(fixture.command(request)))
        val submittedPromptId = requireNotNull(firstApi.submission).promptId

        val restartedApi = FakeApi().apply { observation = ComfyClientObservation.Unknown("no current history") }
        val restarted = fixture.backend(restartedApi, request)
        val observed = restarted.observe(fixture.owned(providerWorkId = null))

        assertIs<VideoBackendObservation.Unknown>(observed)
        assertEquals(submittedPromptId, restartedApi.observedPromptId)
        assertEquals(1, firstApi.submitCalls)
        assertEquals(0, restartedApi.submitCalls)
    }

    @Test
    fun `changed reference pin and unbound fingerprint fail before slot or HTTP mutation`() {
        val fixture = fixture()
        val request = fixture.request()
        Files.writeString(fixture.reference, "changed bytes")
        val api = FakeApi()
        val slots = AtomicInteger()
        val backend = fixture.backend(api, request, acquire = {
            slots.incrementAndGet()
            true
        })

        val digestFailure = assertIs<VideoBackendSubmission.Rejected>(backend.submit(fixture.command(request)))
        assertTrue(digestFailure.reason.contains("digest") || digestFailure.reason.contains("changed"))
        assertEquals(0, api.submitCalls)
        assertEquals(0, slots.get())

        val wrongFingerprint = fixture.command(request).copy(
            ownedAttempt = fixture.owned().copy(requestFingerprint = "f".repeat(64)),
        )
        assertTrue(assertIs<VideoBackendSubmission.Rejected>(backend.submit(wrongFingerprint)).reason.contains("fingerprint"))
        assertEquals(0, api.submitCalls)
    }

    @Test
    fun `stale runtime and missing durable binding retain unknown recovery`() {
        val fixture = fixture()
        val request = fixture.request()
        val stale = fixture.backend(FakeApi(), request, ready = { false })
        assertEquals(emptySet(), stale.availability().supportedInputs)
        assertTrue(assertIs<VideoBackendSubmission.Rejected>(stale.submit(fixture.command(request))).reason.contains("unavailable"))
        assertTrue(assertIs<VideoBackendObservation.Unknown>(stale.observe(fixture.owned())).detail.contains("stale"))

        val missing = fixture.backend(FakeApi(), request = null)
        val result = missing.observe(fixture.owned(providerWorkId = null))
        assertTrue(assertIs<VideoBackendObservation.Unknown>(result).detail.contains("bindings"))
    }

    @Test
    fun `binding and consumed dependency changes alter canonical request fingerprint`() {
        val fixture = fixture()
        val request = fixture.request()
        val input = assertIs<VideoClipGenerationInput>(request.input)
        val binding = requireNotNull(input.comfyWorkflow)
        val changedOutput = input.copy(comfyWorkflow = binding.copy(
            output = binding.output.copy(allowedExtensions = setOf("webm")),
        ))
        assertTrue(comfyRequestFingerprint(request.projectId, LocalVideoBackend.BACKEND_ID, changedOutput, emptyList()) != request.requestFingerprint)
        val changedPin = input.copy(dependencyPins = input.dependencyPins.map {
            if (it.id == "subject") it.copy(sha256 = "e".repeat(64)) else it
        })
        assertTrue(comfyRequestFingerprint(request.projectId, LocalVideoBackend.BACKEND_ID, changedPin, emptyList()) != request.requestFingerprint)
    }

    @Test
    fun `same Comfy image in two projects gets two durable jobs and separate backend submissions`() {
        val fixture = fixture()
        val first = fixture.request()
        val second = first.copy(id = "second-request", projectId = "second-project",
            requestFingerprint = comfyRequestFingerprint("second-project", first.backendId, first.input, first.modelRequirements))
        val midi = fixture.root.resolve("midi-protected").createDirectories()
        val store = VideoJobStore(fixture.root.resolve("jobs"), "comfy-test", listOf(midi))
        val api = FakeApi().apply { submitResult = ComfyClientSubmissionResult.Rejected("owned fixture rejection") }
        val requests = mapOf(first.id to first, second.id to second)
        val backend = LocalVideoBackend(fixture.session, api, { true }, {}, { requests[it] },
            listOf(fixture.root), fixture.publication, emptyList(), { true },
            Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC))
        val coordinator = coordinator(store, backend)
        assertIs<VideoJobResult.Accepted>(coordinator.submit(first))
        assertIs<VideoJobResult.Accepted>(coordinator.submit(second))
        assertEquals(2, api.submitCalls)
        assertEquals(setOf(first.id, second.id), store.snapshot().jobs.map { it.request.id }.toSet())
        assertEquals(2, store.snapshot().jobs.map { it.request.requestFingerprint }.toSet().size)
        assertEquals(first.id, assertIs<VideoJobResult.Accepted>(coordinator.submit(first.copy(id = "duplicate"))).job.request.id)
        assertEquals(2, api.submitCalls)
        assertEquals(VideoJobProblemCode.REQUEST_ID_CONFLICT,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(first.copy(projectId = "second-project", requestFingerprint = second.requestFingerprint))).problem.code)
        assertEquals(2, api.submitCalls)
    }

    @Test
    fun `coordinator rejects forged Comfy fingerprints before deduplication or backend invocation`() {
        val fixture = fixture()
        val first = fixture.request()
        val midi = fixture.root.resolve("midi-protected").createDirectories()
        val store = VideoJobStore(fixture.root.resolve("jobs"), "comfy-test", listOf(midi))
        val api = FakeApi().apply { submitResult = ComfyClientSubmissionResult.Rejected("owned fixture rejection") }
        val backend = fixture.backend(api, first)
        val coordinator = coordinator(store, backend)
        val forged = first.copy(requestFingerprint = "f".repeat(64))
        val emptyLedger = coordinator.snapshot()
        assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED,
            assertIs<VideoJobResult.Rejected>(coordinator.submit(forged)).problem.code)
        assertEquals(emptyLedger, store.snapshot())
        assertEquals(0, api.submitCalls)

        assertIs<VideoJobResult.Accepted>(coordinator.submit(first))
        val original = store.snapshot()
        // Neither a new request ID nor the persisted ID may bypass canonical identity validation.
        for (candidate in listOf(forged.copy(id = "different-id"), forged,
            first.copy(id = "other-project-id", projectId = "other-project"))) {
            assertEquals(VideoJobProblemCode.INPUT_NOT_SUPPORTED,
                assertIs<VideoJobResult.Rejected>(coordinator.submit(candidate)).problem.code)
            assertEquals(original, store.snapshot())
            assertEquals(1, api.submitCalls)
        }
        assertEquals(first.id, assertIs<VideoJobResult.Accepted>(coordinator.submit(first.copy(id = "duplicate"))).job.request.id)
        assertEquals(1, api.submitCalls)
    }

    @Test
    fun `image free keyframe identity binds project workflow and geometry without requiring a reference`() {
        val fixture = fixture()
        val clip = fixture.request()
        val workflow = requireNotNull(clip.input.comfyWorkflow).copy(
            referenceInputs = emptyList(), frameCountInput = null, framesPerSecondInput = null,
        )
        val input = VideoKeyframeGenerationInput(
            "Draw a still", listOf(clip.input.dependencyPins.single { it.id == "workflow" }),
            512, 320, workflow,
        )
        fun fingerprint(project: String, keyframe: VideoKeyframeGenerationInput) =
            comfyRequestFingerprint(project, clip.backendId, keyframe, emptyList())
        val request = clip.copy(input = input, requestFingerprint = fingerprint(clip.projectId, input))
        assertEquals(request.requestFingerprint, fingerprint(clip.projectId, input.copy()))
        assertTrue(request.requestFingerprint != fingerprint("another-project", input))
        assertTrue(request.requestFingerprint != fingerprint(clip.projectId, input.copy(width = 640)))
        assertTrue(request.requestFingerprint != fingerprint(clip.projectId, input.copy(
            dependencyPins = listOf(input.dependencyPins.single().copy(sha256 = "a".repeat(64))),
        )))
        assertFailsWith<IllegalArgumentException> {
            val flat = assertIs<VideoClipGenerationInput>(clip.input)
            comfyRequestFingerprint(clip.projectId, clip.backendId, flat.copy(
                comfyWorkflow = workflow, dependencyPins = input.dependencyPins,
            ), emptyList())
        }
        val api = FakeApi()
        val result = assertIs<VideoBackendSubmission.Accepted>(fixture.backend(api, request).submit(fixture.command(request)))
        assertEquals(result.providerWorkId, api.submission?.promptId)
        assertTrue(requireNotNull(api.submission).uploads.isEmpty())
        val midi = fixture.root.resolve("midi-protected").createDirectories()
        val store = VideoJobStore(fixture.root.resolve("jobs"), "comfy-test", listOf(midi))
        val coordinator = coordinator(store, fixture.backend(FakeApi().apply {
            submitResult = ComfyClientSubmissionResult.Rejected("owned fixture rejection")
        }, request))
        assertIs<VideoJobResult.Accepted>(coordinator.submit(request))
        assertEquals(request.id, assertIs<VideoJobResult.Accepted>(coordinator.submit(request.copy(id = "duplicate"))).job.request.id)
        assertEquals(1, store.snapshot().jobs.size)
    }

    @Test
    fun `Comfy identity scopes the exact consumed image to its project without changing graph slots`() {
        val fixture = fixture()
        val request = fixture.request()
        val input = assertIs<VideoClipGenerationInput>(request.input)
        val otherProject = comfyRequestFingerprint("other-project", request.backendId, input, request.modelRequirements)
        assertTrue(otherProject != request.requestFingerprint)
        val changedImage = input.copy(dependencyPins = input.dependencyPins.map {
            if (it.id == "subject") it.copy(sha256 = "f".repeat(64)) else it
        })
        assertTrue(request.requestFingerprint != comfyRequestFingerprint(request.projectId, request.backendId, changedImage, request.modelRequirements))
        val injected = input.copy(prompt = "scene\nsubject:${"f".repeat(64)}:${fixture.reference}")
        assertTrue(comfyRequestFingerprint(request.projectId, request.backendId, injected, request.modelRequirements) !=
            comfyRequestFingerprint(request.projectId, request.backendId, changedImage, request.modelRequirements))
        assertFailsWith<IllegalArgumentException> {
            val unpinnedImage = input.copy(dependencyPins = input.dependencyPins.map {
                if (it.id == "subject") it.copy(ownedPath = null) else it
            })
            comfyRequestFingerprint(request.projectId, request.backendId, unpinnedImage, request.modelRequirements)
        }
        val slots = AtomicInteger()
        val api = FakeApi()
        val backend = fixture.backend(api, request, acquire = { slots.incrementAndGet(); true })
        val forged = fixture.command(request).copy(ownedAttempt = fixture.owned().copy(requestFingerprint = otherProject))
        assertIs<VideoBackendSubmission.Rejected>(backend.submit(forged))
        assertEquals(0, slots.get())
        assertEquals(0, api.submitCalls)
        assertEquals(VideoComfyInputSlot("2", "image"), input.comfyWorkflow!!.referenceInputs.single().slot)
    }

    @Test
    fun `controlled motion fingerprint binds prompt controls range seed and consumed runtime pins`() {
        val fixture = fixture()
        val ordinary = fixture.request()
        val scene = ordinary.input.dependencyPins.single { it.id == "subject" }
        val renderer = VideoGenerationDependencyPin("renderer", "e".repeat(64), fixture.root.resolve("render.cjs").toString())
        val input = VideoControlledMotionGenerationInput(
            "exact prompt", listOf(scene) + motionRuntime(renderer).allPins,
            VideoControlledMotionRequest(listOf(scene), 10, 20, 42, motionDescriptor(listOf(renderer), 10, 20, 42, ordinary.projectId)),
            "exact prompt",
        )
        val fingerprint = videoRequestFingerprint(ordinary.projectId, ordinary.backendId, input, ordinary.modelRequirements)
        assertEquals(controlledMotionRequestFingerprint(ordinary.backendId, input, ordinary.modelRequirements), fingerprint)
        assertEquals(fingerprint, videoRequestFingerprint(ordinary.projectId, ordinary.backendId, input.copy(), ordinary.modelRequirements))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId, input.copy(prompt = "changed"), ordinary.modelRequirements))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId, input.copy(primaryPrompt = "changed"), ordinary.modelRequirements))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId,
            input.copy(motion = input.motion.copy(descriptor = input.motion.descriptor.copy(
                requestJson = input.motion.descriptor.requestJson.replace("0.25", "0.2")))), ordinary.modelRequirements))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId,
            input.copy(motion = input.motion.copy(startFrame = 11, descriptor = motionDescriptor(listOf(renderer), 11, 20, 42, ordinary.projectId))), ordinary.modelRequirements))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId,
            input.copy(motion = input.motion.copy(seed = 43, descriptor = motionDescriptor(listOf(renderer), 10, 20, 43, ordinary.projectId))), ordinary.modelRequirements))
        val changedRuntime = renderer.copy(sha256 = "f".repeat(64))
        val changedRuntimeBinding = input.motion.descriptor.runtime.copy(compositor = changedRuntime.copy(id = "compositor"))
        val changedPins = input.copy(dependencyPins = listOf(scene) + changedRuntimeBinding.allPins,
            motion = input.motion.copy(descriptor = input.motion.descriptor.copy(runtime = changedRuntimeBinding)))
        assertTrue(fingerprint != videoRequestFingerprint(ordinary.projectId, ordinary.backendId, changedPins, ordinary.modelRequirements))
    }

    @Test
    fun `ComfyUI rejects typed controlled motion without leasing or submitting`() {
        val fixture = fixture()
        val ordinary = fixture.request()
        val scene = ordinary.input.dependencyPins.single { it.id == "subject" }
        val renderer = VideoGenerationDependencyPin("renderer", "e".repeat(64), fixture.root.resolve("render.cjs").toString())
        val input = VideoControlledMotionGenerationInput(
            "keep this exact prompt",
            listOf(scene) + motionRuntime(renderer).allPins,
            VideoControlledMotionRequest(
                listOf(scene), 300, 600, 99, motionDescriptor(listOf(renderer), 300, 600, 99, ordinary.projectId),
            ),
            "keep this exact prompt",
        )
        val request = ordinary.copy(input = input, requestFingerprint = videoRequestFingerprint(ordinary.projectId, ordinary.backendId, input, ordinary.modelRequirements))
        val api = FakeApi()
        val slotClaims = AtomicInteger()
        val backend = fixture.backend(api, request, acquire = { slotClaims.incrementAndGet(); true })

        val result = assertIs<VideoBackendSubmission.Rejected>(backend.submit(fixture.command(request)))

        assertTrue(result.reason.contains("media stage"))
        assertEquals(0, slotClaims.get())
        assertEquals(0, api.submitCalls)
    }

    @Test
    fun `enqueued HTTP 500 retains runtime and V16 admission until reconstructed backend publishes same job`() = withRuntime { fixture, runtime ->
        ComfyVideoClientTest.SocketComfyServer().use { server ->
            val prompt = AtomicReference<String>()
            val completed = AtomicBoolean(false)
            server.response.set { call -> when {
                call.path == "/object_info" -> json(LOCAL_OBJECT_INFO)
                call.path == "/upload/image" -> json("""{"name":"subject.png","subfolder":"","type":"input"}""")
                call.path == "/prompt" -> {
                    prompt.set(Json.parseToJsonElement(call.body.toString(Charsets.UTF_8)).jsonObject.getValue("prompt_id").jsonPrimitive.content)
                    json("enqueued before response failure", 500)
                }
                call.path.startsWith("/history/") -> if (completed.get()) json(successHistory(prompt.get())) else json("{}")
                call.path == "/queue" -> json("""{"queue_running":[[1,"${prompt.get()}",{},{}]],"queue_pending":[]}""")
                else -> json("{}", 404)
            } }
            val request = fixture.request()
            val backendA = fixture.runtimeBackend(runtime, server.client(), request)
            val store = VideoJobStore(
                fixture.root.resolve("jobs"), "comfy-test", listOf(fixture.root.resolve("midi-protected").createDirectories()),
            )
            val first = coordinator(store, backendA)
            val uncertain = assertIs<VideoJobResult.Accepted>(first.submit(request)).attempt!!
            assertEquals(VideoSubmissionPhase.UNCERTAIN, uncertain.submissionPhase)
            val oldLease = runtime.acquireInferenceSlot(fixture.session, prompt.get())
            assertEquals(ComfyVideoRuntimeFailure.INFERENCE_BUSY,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot(fixture.session, "competing") }.failure)

            val backendB = fixture.runtimeBackend(runtime, server.client(), request)
            val second = coordinator(store, backendB)
            val competingInput = assertIs<VideoClipGenerationInput>(request.input).copy(prompt = "Another scene")
            val competing = request.copy(id = "request-2", input = competingInput,
                requestFingerprint = comfyRequestFingerprint(request.projectId, request.backendId, competingInput, emptyList()))
            assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY, assertIs<VideoJobResult.Rejected>(second.submit(competing)).problem.code)
            assertEquals(VideoJobProblemCode.RETRY_NOT_ALLOWED, assertIs<VideoJobResult.Rejected>(second.retry(request.id)).problem.code)
            val running = assertIs<VideoJobResult.Accepted>(second.recover().single()).attempt!!
            assertEquals(prompt.get(), running.providerWorkId)
            assertEquals(VideoGenerationAttemptStatus.ACTIVE, running.status)
            assertIs<VideoBackendSubmission.Uncertain>(backendB.submit(VideoBackendSubmissionCommand(
                VideoOwnedBackendAttempt(request.id, request.requestFingerprint, uncertain.id, uncertain.ownershipToken, request.backendId, null),
                request.input, request.modelRequirements, request.execution,
            )))
            assertEquals(1, server.requests.count { it.path == "/prompt" })

            Files.writeString(fixture.session.outputDirectory.resolve("take.mp4"), "completed owned take")
            completed.set(true)
            val result = assertIs<VideoJobResult.Accepted>(second.recover().single())
            assertEquals(VideoGenerationAttemptStatus.SUCCEEDED, result.attempt!!.status)
            assertEquals(1, result.job.outputs.size)
            val next = runtime.acquireInferenceSlot(fixture.session, "next-attempt")
            oldLease.close()
            // A late terminal read of the old attempt also cannot release the new lease.
            second.reconcile(request.id)
            assertEquals(ComfyVideoRuntimeFailure.INFERENCE_BUSY,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot(fixture.session, "competing") }.failure)
            next.close()
            assertEquals(1, server.requests.count { it.path == "/prompt" })
        }
    }

    @Test
    fun `pending HTTP 500 cancellation waits for durable acknowledgement then recovery releases both admissions`() = withRuntime { fixture, runtime ->
        ComfyVideoClientTest.SocketComfyServer().use { server ->
            val jobs = fixture.root.resolve("jobs")
            val protectedRoots = listOf(fixture.root.resolve("midi-protected").createDirectories())
            val store = VideoJobStore(jobs, "comfy-test", protectedRoots)
            val prompt = AtomicReference<String>()
            val pending = AtomicBoolean(false)
            val persistedAtDelete = AtomicReference<VideoGenerationAttempt>()
            server.response.set { call -> when {
                call.path == "/object_info" -> json(LOCAL_OBJECT_INFO)
                call.path == "/upload/image" -> json("""{"name":"subject.png","subfolder":"","type":"input"}""")
                call.path == "/prompt" -> {
                    prompt.set(Json.parseToJsonElement(call.body.toString(Charsets.UTF_8)).jsonObject.getValue("prompt_id").jsonPrimitive.content)
                    pending.set(true)
                    json("enqueued before response failure", 500)
                }
                call.path.startsWith("/history/") -> json("{}")
                call.path == "/queue" && call.method == "POST" -> {
                    persistedAtDelete.set(store.snapshot().jobs.single().attempts.single())
                    pending.set(false)
                    json("{}")
                }
                call.path == "/queue" -> if (pending.get()) json("""{"queue_running":[],"queue_pending":[[1,"${prompt.get()}",{},{}]]}""")
                    else json("""{"queue_running":[],"queue_pending":[]}""")
                else -> json("{}", 404)
            } }
            val request = fixture.request()
            val first = coordinator(store, fixture.runtimeBackend(runtime, server.client(), request))
            val uncertain = assertIs<VideoJobResult.Accepted>(first.submit(request)).attempt!!
            assertEquals(VideoSubmissionPhase.UNCERTAIN, uncertain.submissionPhase)
            assertEquals(null, uncertain.providerWorkId)

            val cancellation = assertIs<VideoJobResult.Accepted>(first.cancel(request.id)).attempt!!
            assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, cancellation.status)
            assertEquals(VideoSubmissionPhase.UNCERTAIN, cancellation.submissionPhase)
            assertEquals(null, cancellation.providerWorkId)
            assertTrue(pending.get())
            assertTrue(server.requests.none { it.path == "/queue" && it.method == "POST" || it.path == "/interrupt" })
            assertEquals(ComfyVideoRuntimeFailure.INFERENCE_BUSY,
                assertFailsWith<ComfyVideoRuntimeException> { runtime.acquireInferenceSlot(fixture.session, "competing") }.failure)
            val competingInput = assertIs<VideoClipGenerationInput>(request.input).copy(prompt = "Another scene")
            val competing = request.copy(id = "request-2", input = competingInput,
                requestFingerprint = comfyRequestFingerprint(request.projectId, request.backendId, competingInput, emptyList()))
            assertEquals(VideoJobProblemCode.LOCAL_SLOT_BUSY, assertIs<VideoJobResult.Rejected>(first.submit(competing)).problem.code)

            val recoveredStore = VideoJobStore(jobs, "comfy-test", protectedRoots)
            val recovered = coordinator(recoveredStore, fixture.runtimeBackend(runtime, server.client(), request))
            val result = assertIs<VideoJobResult.Accepted>(recovered.recover().single())
            val acknowledged = requireNotNull(persistedAtDelete.get())
            assertEquals(uncertain.id, acknowledged.id)
            assertEquals(prompt.get(), acknowledged.providerWorkId)
            assertEquals(VideoSubmissionPhase.ACKNOWLEDGED, acknowledged.submissionPhase)
            assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, acknowledged.status)
            val terminal = result.attempt!!
            assertEquals(VideoGenerationAttemptStatus.CANCELLED, terminal.status)
            assertEquals(VideoSubmissionPhase.ACKNOWLEDGED, terminal.submissionPhase)
            assertEquals(prompt.get(), terminal.providerWorkId)
            assertTrue(!pending.get())
            assertTrue(result.job.outputs.isEmpty())
            runtime.acquireInferenceSlot(fixture.session, "next-attempt").close()

            val terminalLedger = recoveredStore.snapshot()
            assertEquals(result.job, terminalLedger.jobs.single())
            repeat(2) {
                val restarted = coordinator(VideoJobStore(jobs, "comfy-test", protectedRoots),
                    fixture.runtimeBackend(runtime, server.client(), request))
                assertTrue(restarted.recover().isEmpty())
                assertEquals(terminal, assertIs<VideoJobResult.Accepted>(restarted.reconcile(request.id)).attempt)
                val noOp = assertIs<VideoJobResult.Accepted>(restarted.cancel(request.id))
                assertEquals(null, noOp.attempt)
                assertEquals(false, noOp.launchedByCaller)
                assertEquals(result.job, noOp.job)
                assertEquals(terminalLedger, restarted.snapshot())
            }
            // Prove V16 can admit different work without issuing a second real /prompt.
            val admissionApi = FakeApi().apply { submitResult = ComfyClientSubmissionResult.Rejected("admission probe") }
            val next = coordinator(recoveredStore, fixture.runtimeBackend(runtime, admissionApi, competing))
            val admitted = assertIs<VideoJobResult.Accepted>(next.submit(competing))
            assertEquals(1, admissionApi.submitCalls)
            assertEquals(VideoGenerationAttemptStatus.FAILED, admitted.attempt!!.status)
            assertEquals("{\"delete\":[\"${prompt.get()}\"]}", server.requests.single { it.path == "/queue" && it.method == "POST" }.body.toString(Charsets.UTF_8))
            assertEquals(1, server.requests.count { it.path == "/prompt" && it.method == "POST" })
            assertTrue(server.requests.none { it.path == "/interrupt" })
        }
    }

    @Test
    fun `reconstructed backend recovers interrupted history and releases exact runtime lease`() = withRuntime { fixture, runtime ->
        ComfyVideoClientTest.SocketComfyServer().use { server ->
            val request = fixture.request()
            val api = FakeApi()
            val first = fixture.runtimeBackend(runtime, api, request)
            val submitted = assertIs<VideoBackendSubmission.Accepted>(first.submit(fixture.command(request)))
            assertEquals(app.melotrail.video.application.VideoBackendCancellation.Requested,
                first.requestCancellation(fixture.owned(submitted.providerWorkId)))
            server.response.set { json(interruptedHistory(submitted.providerWorkId)) }
            val recovered = fixture.runtimeBackend(runtime, server.client(), request)
            assertIs<VideoBackendObservation.Cancelled>(recovered.observe(fixture.owned(submitted.providerWorkId)))
            runtime.acquireInferenceSlot(fixture.session, "next-attempt").close()
        }
    }

    @Test
    fun `completion during interrupt remains publishable by V16 recovery and releases runtime lease`() = withRuntime { fixture, runtime ->
        ComfyVideoClientTest.SocketComfyServer().use { server ->
            val request = fixture.request()
            val api = FakeApi()
            val backendA = fixture.runtimeBackend(runtime, api, request)
            val store = VideoJobStore(
                fixture.root.resolve("jobs"), "comfy-test", listOf(fixture.root.resolve("midi-protected").createDirectories()),
            )
            val initial = assertIs<VideoJobResult.Accepted>(coordinator(store, backendA).submit(request))
            val promptId = requireNotNull(initial.attempt!!.providerWorkId)
            val completed = AtomicBoolean(false)
            server.response.set { call -> when (call.path) {
                "/history/$promptId" -> if (completed.get()) json(successHistory(promptId)) else json("{}")
                "/queue" -> if (completed.get()) json("""{"queue_running":[],"queue_pending":[]}""")
                    else json("""{"queue_running":[[1,"$promptId",{},{}]],"queue_pending":[]}""")
                "/interrupt" -> {
                    Files.writeString(fixture.session.outputDirectory.resolve("take.mp4"), "racing successful take")
                    completed.set(true)
                    json("{}")
                }
                else -> json("{}", 404)
            } }
            val backendB = fixture.runtimeBackend(runtime, server.client(), request)
            val recovering = coordinator(store, backendB)
            val cancellation = assertIs<VideoJobResult.Accepted>(recovering.cancel(request.id))
            assertEquals(VideoGenerationAttemptStatus.CANCELLATION_REQUESTED, cancellation.attempt!!.status)
            val result = assertIs<VideoJobResult.Accepted>(recovering.recover().single())
            assertEquals(VideoGenerationAttemptStatus.SUCCEEDED, result.attempt!!.status)
            assertEquals(1, result.job.outputs.size)
            assertTrue(Files.isRegularFile(fixture.publication.resolve(requireNotNull(result.job.outputs.single().relativePath))))
            runtime.acquireInferenceSlot(fixture.session, "next-attempt").close()
        }
    }

    private fun coordinator(store: VideoJobStore, backend: LocalVideoBackend) = VideoJobCoordinator(
        "comfy-test", store, listOf(backend), clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
    )

    private fun withRuntime(block: (Fixture, ComfyVideoRuntime) -> Unit) {
        val setup = ComfyVideoRuntimeTest.RuntimeFixture.create()
        val runtime = setup.runtime(ComfyVideoRuntimeTest.BlockingRunner(), { true }, nanoTime = { 0L })
        try {
            val session = runtime.start(setup.setup, setup.sessions, setup.freePort())
            listOf(session.inputDirectory, session.outputDirectory, session.temporaryDirectory, session.userDirectory).forEach { it.createDirectories() }
            block(fixture().copy(session = session), runtime)
        } finally { runtime.close() }
    }

    private class FakeApi : ComfyVideoApi {
        var submitResult: ComfyClientSubmissionResult? = null
        var observation: ComfyClientObservation = ComfyClientObservation.Running(null)
        var cancellation: ComfyClientCancellation = ComfyClientCancellation.Requested
        var submission: ComfyClientSubmission? = null
        var submitCalls = 0
        var observedPromptId: String? = null

        override fun submit(request: ComfyClientSubmission): ComfyClientSubmissionResult {
            submitCalls++
            submission = request
            return submitResult ?: ComfyClientSubmissionResult.Accepted(request.promptId)
        }

        override fun observe(promptId: String, clientId: String, output: VideoComfyOutputBinding): ComfyClientObservation {
            observedPromptId = promptId
            return observation
        }

        override fun cancel(promptId: String, acknowledged: Boolean) = cancellation
    }

    private data class Fixture(
        val root: Path,
        val session: ComfyVideoSession,
        val workflow: Path,
        val reference: Path,
        val publication: Path,
    ) {
        fun request(): VideoGenerationJobRequest {
            val input = VideoClipGenerationInput(
                prompt = "Describe a calm scene",
                dependencyPins = listOf(
                    VideoGenerationDependencyPin("workflow", sha256(workflow), workflow.toString()),
                    VideoGenerationDependencyPin("subject", sha256(reference), reference.toString()),
                ),
                durationMillis = 1_960,
                width = 512,
                height = 320,
                framesPerSecond = 25,
                comfyWorkflow = VideoComfyWorkflowRequest(
                    workflowDependencyId = "workflow",
                    promptInput = VideoComfyInputSlot("1", "text"),
                    referenceInputs = listOf(VideoComfyReferenceInput("subject", VideoComfyInputSlot("2", "image"), "subject.png")),
                    widthInput = VideoComfyInputSlot("1", "width"),
                    heightInput = VideoComfyInputSlot("1", "height"),
                    frameCountInput = VideoComfyInputSlot("1", "frames"),
                    framesPerSecondInput = VideoComfyInputSlot("1", "fps"),
                    output = VideoComfyOutputBinding("3", setOf("mp4")),
                ),
            )
            return VideoGenerationJobRequest(
                id = "request-1",
                projectId = "project-1",
                backendId = LocalVideoBackend.BACKEND_ID,
                modelRequirements = emptyList(),
                input = input,
                requestFingerprint = comfyRequestFingerprint("project-1", LocalVideoBackend.BACKEND_ID, input, emptyList()),
                maximumAttempts = 2,
                createdAt = NOW,
                execution = VideoLocalExecutionPolicy(60_000, 1_000_000_000, 1_000_000),
            )
        }

        fun owned(providerWorkId: String? = null) = VideoOwnedBackendAttempt(
            "request-1", request().requestFingerprint, "attempt-1", "owner-1", LocalVideoBackend.BACKEND_ID, providerWorkId,
        )

        fun command(request: VideoGenerationJobRequest) = VideoBackendSubmissionCommand(
            owned(providerWorkId = null).copy(requestFingerprint = request.requestFingerprint),
            request.input,
            request.modelRequirements,
            request.execution,
        )

        fun runtimeBackend(runtime: ComfyVideoRuntime, api: ComfyVideoApi, request: VideoGenerationJobRequest) = backend(
            api, request,
            acquire = { runtime.acquireInferenceSlot(session, it).claimSubmission() },
            release = { runtime.releaseInferenceSlot(session, it) },
        )

        fun backend(
            api: ComfyVideoApi,
            request: VideoGenerationJobRequest?,
            closed: AtomicInteger = AtomicInteger(),
            ready: () -> Boolean = { true },
            acquire: (String) -> Boolean = { true },
            release: (String) -> Unit = { closed.incrementAndGet(); Unit },
        ) = LocalVideoBackend(
            session, api, acquire, release, { id -> request?.takeIf { it.id == id } }, listOf(root), publication, emptyList(), ready,
            Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
        )
    }

    private fun fixture(): Fixture {
        val root = createTempDirectory("local-video-backend").toRealPath()
        val input = root.resolve("input").createDirectories()
        val output = root.resolve("output").createDirectories()
        val temp = root.resolve("temp").createDirectories()
        val user = root.resolve("user").createDirectories()
        val publication = root.resolve("published").createDirectories()
        val workflow = root.resolve("workflow.json")
        Files.writeString(workflow, """{
          "1":{"class_type":"Generator","inputs":{"text":"old","width":1,"height":1,"frames":1,"fps":1}},
          "2":{"class_type":"LoadImage","inputs":{"image":"old.png"}},
          "3":{"class_type":"SaveVideo","inputs":{"frames":["1",0]}}
        }""".trimIndent())
        val reference = root.resolve("reference.png")
        Files.write(reference, "owned reference".toByteArray())
        return Fixture(root, ComfyVideoSession("session-1", URI("http://127.0.0.1:1"), root, input, output, temp, user), workflow, reference, publication)
    }

    companion object {
        private val LOCAL_OBJECT_INFO = """{
          "Generator":{"input":{"required":{"text":["STRING",{}],"width":["INT",{}],"height":["INT",{}],"frames":["INT",{}],"fps":["INT",{}]}}},
          "LoadImage":{"input":{"required":{"image":["IMAGE",{}]}}},
          "SaveVideo":{"output_node":true,"input":{"required":{"frames":["IMAGE",{}]}}}
        }""".trimIndent()
        private const val NOW = "2026-09-14T00:00:00Z"
    }
}

private fun sha256(path: Path): String = sha256(Files.readAllBytes(path))
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
