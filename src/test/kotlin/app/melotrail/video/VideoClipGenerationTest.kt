package app.melotrail.video

import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.ComfyShortI2VBinding
import app.melotrail.video.adapter.LocalVideoBackend
import app.melotrail.video.adapter.comfyRequestFingerprint
import app.melotrail.video.adapter.VideoResultImport
import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMotionRenderer
import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoPreparedSceneImport
import app.melotrail.video.adapter.VideoPreparedSceneImportResult
import app.melotrail.video.adapter.VideoMediaProcessResult
import app.melotrail.video.adapter.VideoMediaProcessOutput
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.adapter.VideoMediaProcessException
import app.melotrail.video.adapter.VideoMediaProcessFailure
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.time.Duration
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertIs
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Files
import java.nio.file.Path
import java.io.RandomAccessFile
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VideoClipGenerationTest {
    @Test fun `capabilities and durable projections remain read only and distinguish blockers and unknown progress`() {
        val root = Files.createTempDirectory("video-capability-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val pin = VideoGenerationDependencyPin("prepared", "a".repeat(64), "/owned/scene")
        val runtime = motionRuntime(VideoGenerationDependencyPin("compositor", "b".repeat(64), "/runtime/compositor"))
        val input = VideoControlledMotionGenerationInput("Move", listOf(pin) + runtime.allPins,
            VideoControlledMotionRequest(listOf(pin), 0, 150, 42,
                motionDescriptor(listOf(runtime.compositor), 0, 150, 42, project.id)), "Move", motionMedia())
        val policy = input.media.execution
        val request = VideoGenerationJobRequest("request", project.id, "controlled-local", emptyList(), input,
            controlledMotionRequestFingerprint("controlled-local", input, emptyList(), 2), 2,
            Instant.now().toString(), policy)
        val active = VideoGenerationAttempt("attempt", request.id, 1, "owner", VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN,
            VideoSubmissionPhase.UNCERTAIN, Instant.now().toString())
        var ledger = VideoJobLedger("domain", Instant.now().toString(), listOf(VideoGenerationJob(request, listOf(active))))
        val persistence = object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String) = ledger
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger {
                ledger = replacement
                return ledger
            }
        }
        var backendStatus = VideoBackendAvailabilityStatus.AVAILABLE
        var supportedInputs = setOf(VideoGenerationInputKind.CONTROLLED_MOTION)
        val backend = object : VideoGenerationBackendPort {
            override val backendId = request.backendId
            override fun availability() = VideoBackendAvailability(backendId, backendStatus, Instant.now().toString(),
                supportedInputs, emptyList(), "test backend")
            override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission = error("No launch permitted")
            override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation = error("No observation permitted")
            override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendCancellation = error("No cancellation permitted")
        }
        val coordinator = VideoJobCoordinator("domain", persistence, listOf(backend),
            clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC))
        val service = VideoClipGeneration(VideoScenePreparation(), coordinator,
            VideoResultImport(store, VideoMediaProbe()), VideoMotionRenderer(), request.backendId, policy, projects = store)
        val session = VideoProjectSession(root, project)
        val before = ledger
        val capabilities = service.capabilities(session)
        assertEquals(setOf(VideoClipRoute.CONTROLLED_MOTION, VideoClipRoute.FLAT_IMAGE_I2V), capabilities.map { it.route }.toSet())
        assertEquals(setOf(VideoCapabilityBlockerCode.MISSING_ARTWORK, VideoCapabilityBlockerCode.UNSUPPORTED_MOTION,
            VideoCapabilityBlockerCode.MISSING_TOOL, VideoCapabilityBlockerCode.OCCUPIED_ADMISSION),
            capabilities.first().blockers.map { it.code }.toSet())
        assertEquals(setOf(VideoCapabilityBlockerCode.MISSING_ARTWORK, VideoCapabilityBlockerCode.MISSING_TOOL,
            VideoCapabilityBlockerCode.UNAVAILABLE_RUNTIME, VideoCapabilityBlockerCode.OCCUPIED_ADMISSION),
            capabilities.last().blockers.map { it.code }.toSet())
        assertTrue(capabilities.last().supportedControls.isEmpty())
        assertEquals(129, capabilities.last().maximumNativeFrames)
        assertEquals(25, capabilities.last().nativeFramesPerSecond)
        assertEquals(5_160, capabilities.last().maximumNativeDurationMillis)
        assertEquals(30, capabilities.first().nativeFramesPerSecond)
        assertEquals(300_000, capabilities.first().maximumNativeDurationMillis)
        val view = service.jobs(session).single()
        assertEquals(VideoClipRoute.CONTROLLED_MOTION, view.route)
        assertEquals(active.id, view.attemptId)
        assertEquals(VideoGenerationAttemptStatus.SUBMISSION_UNCERTAIN, view.status)
        assertEquals(null, view.observedProgressPercent)
        assertEquals(null, view.controlledStage)
        assertEquals(1, view.remainingAttempts)
        assertFalse(view.retryEligible)
        assertTrue(view.takes.isEmpty())
        assertEquals(before, ledger)
        assertEquals(project, store.open(root))
        val other = VideoProject("other", "Other", project.createdAt)
        val otherRoot = Files.createTempDirectory("video-other-capability-")
        store.create(otherRoot, other)
        assertTrue(service.jobs(VideoProjectSession(otherRoot, other)).isEmpty())
        assertFailsWith<IllegalArgumentException> { service.cancel(VideoProjectSession(otherRoot, other), request.id, active.id) }
        assertFailsWith<IllegalArgumentException> { service.retry(VideoProjectSession(otherRoot, other), request.id) }
        assertFailsWith<IllegalArgumentException> { service.reconcile(VideoProjectSession(otherRoot, other), request.id, active.id) }
        assertEquals(before, ledger)
        ledger = ledger.copy(jobs = listOf(VideoGenerationJob(request, listOf(active.copy(
            status = VideoGenerationAttemptStatus.FAILED, submissionPhase = VideoSubmissionPhase.NOT_STARTED,
            finishedAt = Instant.now().toString(), retryable = true)))))
        val retry = service.jobs(session).single()
        assertTrue(retry.retryEligible)
        assertEquals(1, retry.remainingAttempts)
        backendStatus = VideoBackendAvailabilityStatus.OFFLINE
        assertFalse(service.jobs(session).single().retryEligible, "Offline backend cannot accept a retry")
        backendStatus = VideoBackendAvailabilityStatus.AVAILABLE
        supportedInputs = emptySet()
        assertFalse(service.jobs(session).single().retryEligible, "Unsupported input cannot be retried")
        supportedInputs = setOf(VideoGenerationInputKind.CONTROLLED_MOTION)
        assertTrue(service.jobs(session).single().retryEligible)
        assertEquals(VideoJobProblemCode.ATTEMPT_NOT_FOUND,
            assertIs<VideoJobResult.Rejected>(service.cancel(session, request.id, "wrong-attempt")).problem.code)
        assertEquals(VideoGenerationAttemptStatus.FAILED,
            assertIs<VideoJobResult.Accepted>(service.reconcile(session, request.id, active.id)).attempt?.status)
        assertTrue(service.recover().isEmpty())
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(maximumAttempts = 1,
            requestFingerprint = controlledMotionRequestFingerprint(request.backendId, input, emptyList(), 1)))))
        assertFalse(service.jobs(session).single().retryEligible)
        assertEquals(0, service.jobs(session).single().remainingAttempts)
        val otherInput = VideoKeyframeGenerationInput("Still", listOf(pin), 320, 180)
        val otherRequest = request.copy(id = "keyframe", input = otherInput, requestFingerprint = "d".repeat(64))
        ledger = ledger.copy(jobs = ledger.jobs + VideoGenerationJob(otherRequest))
        assertEquals(VideoClipRoute.OTHER_JOB, service.jobs(session).single { it.requestId == "keyframe" }.route)
        supportedInputs = setOf(VideoGenerationInputKind.KEYFRAME)
        val hosted = VideoHostedExecutionPolicy("budget", "USD", 60,
            "2026-09-24T00:00:00Z", "2026-09-24T01:00:00Z", 120)
        val hostedAttempt = active.copy(id = "host-attempt", requestId = otherRequest.id,
            status = VideoGenerationAttemptStatus.FAILED, submissionPhase = VideoSubmissionPhase.NOT_STARTED,
            finishedAt = Instant.now().toString(), retryable = true)
        fun hostedJob(policy: VideoHostedExecutionPolicy) = VideoGenerationJob(
            otherRequest.copy(execution = policy), listOf(hostedAttempt))
        ledger = ledger.copy(jobs = listOf(hostedJob(hosted)))
        assertFalse(service.jobs(session).single().retryEligible, "An expired estimate cannot admit a retry")
        assertEquals(VideoJobProblemCode.HOSTED_ESTIMATE_STALE,
            assertIs<VideoJobResult.Rejected>(coordinator.retry(otherRequest.id)).problem.code)
        ledger = ledger.copy(jobs = listOf(hostedJob(hosted.copy(estimateExpiresAt = "2026-09-26T00:00:00Z",
            authorizedSpendCapMicros = 100))))
        assertFalse(service.jobs(session).single().retryEligible, "The prior reservation and retry exceed the budget")
        assertEquals(VideoJobProblemCode.HOSTED_BUDGET_EXCEEDED,
            assertIs<VideoJobResult.Rejected>(coordinator.retry(otherRequest.id)).problem.code)
        ledger = ledger.copy(jobs = listOf(hostedJob(hosted.copy(estimateExpiresAt = "2026-09-26T00:00:00Z"))))
        assertTrue(service.jobs(session).single().retryEligible, "A current estimate with room for both attempts is eligible")
    }

    @Test fun `invalid or JavaScript unsafe controlled ranges cannot form a durable request`() {
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        // Domain validation also protects direct ledger callers, without filesystem I/O.
        val pin = VideoGenerationDependencyPin("scene", "a".repeat(64), "/owned/scene")
        val runtime = VideoGenerationDependencyPin("renderer", "b".repeat(64), "/runtime/renderer")
        val descriptor = motionDescriptor(listOf(runtime), 0, 150, 1, project.id)
        val motion = VideoControlledMotionRequest(listOf(pin), 0, 150, 1, descriptor)
        assertFailsWith<IllegalArgumentException> { motion.copy(endFrameExclusive = Long.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { motion.copy(endFrameExclusive = MAX_JAVASCRIPT_SAFE_INTEGER + 1) }
        assertFailsWith<IllegalArgumentException> { motion.copy(endFrameExclusive = 0) }
        assertFailsWith<IllegalArgumentException> { motion.copy(endFrameExclusive = 9_001) }
        assertFailsWith<IllegalArgumentException> { motion.copy(startFrame = MAX_JAVASCRIPT_SAFE_INTEGER) }
    }
    @Test fun `identifier-only import rejects wrong project revision attempt and output before touching media`() {
        val root = Files.createTempDirectory("video-completed-guard-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val pin = VideoGenerationDependencyPin("scene", "a".repeat(64), "/owned/scene.json")
        val runtime = VideoGenerationDependencyPin("runtime", "b".repeat(64), "/runtime/renderer.cjs")
        val input = VideoControlledMotionGenerationInput("move", listOf(pin) + motionRuntime(runtime).allPins,
            VideoControlledMotionRequest(listOf(pin), 0, 150, 42, motionDescriptor(listOf(runtime), 0, 150, 42, project.id)), "move",
            motionMedia(VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000)))
        val backendId = "controlled-local"
        val request = VideoGenerationJobRequest("request", project.id, backendId, emptyList(), input,
            controlledMotionRequestFingerprint(backendId, input, emptyList(), 1), 1, Instant.now().toString(),
            VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000))
        val attempt = VideoGenerationAttempt("attempt", "request", 1, "owner", VideoGenerationAttemptStatus.SUCCEEDED,
            VideoSubmissionPhase.ACKNOWLEDGED, Instant.now().toString(), finishedAt = Instant.now().toString(),
            controlledEvidence = VideoControlledAttemptEvidence(VideoControlledStage.COMPLETED,
                VideoControlledOutputEvidence("attempt-preview", "attempt/preview.mp4", "c".repeat(64), 100)))
        val output = VideoGenerationOutput("attempt-preview", "attempt", "attempt-preview", Instant.now().toString(),
            "attempt/preview.mp4", "c".repeat(64), 100)
        var ledger = VideoJobLedger("domain", Instant.now().toString(), listOf(VideoGenerationJob(request,
            listOf(attempt), listOf(output), output.id)))
        val persistence = object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String) = ledger
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger = error("No write permitted")
        }
        val coordinator = VideoJobCoordinator("domain", persistence, emptyList())
        val service = VideoClipGeneration(VideoScenePreparation(), coordinator,
            VideoResultImport(store, VideoMediaProbe()), VideoMotionRenderer(), backendId,
            VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000), projects = store)
        val session = VideoProjectSession(root, project)
        fun rejection(command: VideoCompletedTakeImport, fragment: String) {
            val result = service.importCompleted(command)
            assertTrue(result is VideoClipGenerationResult.Rejected && result.reason.contains(fragment), "$result")
        }
        val command = VideoCompletedTakeImport(session, 0, "request", "attempt", "attempt-preview")
        val cancelled = app.melotrail.video.adapter.VideoMediaProcessCancellation().also { it.cancel() }
        val cancelledResult = service.importCompleted(command, cancelled)
        assertTrue(cancelledResult is VideoClipGenerationResult.Rejected && cancelledResult.reason.contains("cancelled"))
        rejection(command.copy(expectedRevision = 1), "revision")
        rejection(command.copy(session = session.copy(project = project.copy(id = "other"))), "revision")
        rejection(command.copy(attemptId = "older"), "latest")
        rejection(command.copy(outputId = "different"), "output")
        val newer = attempt.copy(id = "attempt-2", number = 2, ownershipToken = "owner-2",
            controlledEvidence = VideoControlledAttemptEvidence(VideoControlledStage.COMPLETED,
                VideoControlledOutputEvidence("attempt-2-preview", "attempt-2/preview.mp4", "e".repeat(64), 100)))
        val newerOutput = output.copy(id = "attempt-2-preview", attemptId = newer.id,
            backendOutputId = "attempt-2-preview", relativePath = "attempt-2/preview.mp4", sha256 = "e".repeat(64))
        val twoAttemptRequest = request.copy(maximumAttempts = 2, requestFingerprint =
            controlledMotionRequestFingerprint(backendId, input, emptyList(), 2))
        ledger = ledger.copy(jobs = listOf(VideoGenerationJob(twoAttemptRequest,
            listOf(attempt, newer), listOf(output, newerOutput), newerOutput.id)))
        rejection(command, "latest")
        ledger = ledger.copy(jobs = listOf(VideoGenerationJob(request, listOf(attempt), listOf(output), output.id)))
        rejection(command.copy(requestId = "missing"), "not found")
        val alteredPolicy = VideoLocalExecutionPolicy(1_000_000, 1_000_000, 61_000)
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(
            input = input.copy(media = input.media.copy(execution = alteredPolicy)), execution = alteredPolicy))))
        rejection(command, "policy")
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(
            input = VideoKeyframeGenerationInput("move", listOf(pin), 320, 180)))))
        rejection(command, "not connected")
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(
            requestFingerprint = "d".repeat(64)))))
        rejection(command, "fingerprint")
        val alteredModels = listOf(VideoModelRequirement("changed-model", "v2", "a".repeat(64)))
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(
            modelRequirements = alteredModels, requestFingerprint = controlledMotionRequestFingerprint(
                backendId, input, alteredModels, 1)))))
        rejection(command, "fingerprint")
        val otherInput = input.copy(motion = input.motion.copy(descriptor = input.motion.descriptor.copy(projectId = "other")))
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(
            projectId = "other", input = otherInput, requestFingerprint = controlledMotionRequestFingerprint(
                backendId, otherInput, emptyList(), 1)))))
        rejection(command, "identity")
        ledger = ledger.copy(jobs = listOf(ledger.jobs.single().copy(request = request.copy(backendId = "other"))))
        rejection(command, "identity")
        assertEquals(project, store.open(root))
        assertFalse(Files.exists(root.resolve("take-validation-attempt")))
    }
    @Test fun `identifier-only controlled import publishes a verified take from persisted scene and output root`() {
        val root = Files.createTempDirectory("video-controlled-import-").toRealPath()
        val projectRoot = root.resolve("project")
        val store = VideoProjectStore(listOf(root.resolve("protected-midi")))
        val lifecycle = VideoProjectLifecycle(store)
        var session = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle.create(
            CreateVideoProject(projectRoot, "Controlled import", "project"))).session
        fun picture(name: String, alpha: Boolean): Path {
            val path = root.resolve("inputs/$name.png")
            Files.createDirectories(path.parent)
            val image = BufferedImage(if (alpha) 20 else 100, if (alpha) 30 else 60,
                if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = when (name) {
                    "pose" -> Color(120, 70, 100)
                    "clean" -> Color(70, 90, 110)
                    "inspiration" -> Color(50, 120, 80)
                    else -> Color(90, 100, 120)
                }
                if (alpha) graphics.fillOval(2, 2, 16, 26) else graphics.fillRect(0, 0, 100, 60)
            } finally { graphics.dispose() }
            assertTrue(ImageIO.write(image, "png", path.toFile()))
            return path
        }
        fun importImage(id: String, role: VideoReferenceRole, alpha: Boolean): VideoVersionedId {
            val imported = assertIs<VideoAssetImportResult.Imported>(VideoAssetImport(lifecycle, VideoImageFiles(),
                idFactory = { id }).import(session, ImportVideoAsset(picture(id, alpha), role)))
            session = imported.session
            return imported.asset.id
        }
        val finished = importImage("finished", VideoReferenceRole.COMPLETE_SCENE, false)
        val subject = importImage("subject", VideoReferenceRole.SUBJECT, true)
        val pose = importImage("pose", VideoReferenceRole.SUBJECT, true)
        val clean = importImage("clean", VideoReferenceRole.ENVIRONMENT, false)
        val scenes = VideoPreparedSceneStore(store)
        val bounds = VideoRect("scene", 10.0, 10.0, 20.0, 30.0)
        val saved = assertIs<VideoPreparedSceneImportResult.Saved>(VideoPreparedSceneImport(store, scenes).import(
            projectRoot, session.project.revision, PrepareVideoAnimationAssets(
                VideoVersionedId("scene", 1), finished,
                subjectLayers = listOf(VideoPlacedAnimationAsset("subject", subject, bounds)),
                cleanBackground = VideoPlacedAnimationAsset("clean", clean, VideoRect("scene", 0.0, 0.0, 100.0, 60.0)),
                poses = listOf(VideoPoseAnimationAsset(VideoPlacedAnimationAsset("blink", pose, bounds), "subject")),
            )))
        session = VideoProjectSession(projectRoot, saved.project)
        val scene = scenes.load(projectRoot, saved.scene.id)
        val record = session.project.preparedSceneVersions.single()
        val reference = scene.source.references.single { it.id == finished }
        val sourceString = "${session.project.id}:${session.project.revision}:${reference.id.id}:${reference.id.version}:${reference.descriptorArtifact.sha256}:${reference.original.artifact.sha256}:${record.id.id}:${record.id.version}:${record.artifact.sha256}:" +
            scene.consumedArtifacts().sortedBy { it.relativePath }.joinToString("|") { "${it.relativePath}:${it.sha256}" }
        val sourceIdentity = MessageDigest.getInstance("SHA-256").digest(sourceString.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val tools = Files.createDirectory(root.resolve("tools"))
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        for (name in listOf("ffmpeg", "ffprobe")) {
            val binary = Files.copy(java, tools.resolve(name))
            Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"))
        }
        Files.writeString(tools.resolve(VideoMediaProbe.MANIFEST_NAME), """
            {"schema":"melotrail-video-media-tools","version":1,
             "distributionId":"${VideoMediaProbe.DISTRIBUTION_ID}",
             "installation":"${VideoMediaProbe.INSTALLATION_STRATEGY}",
             "sourceUrl":"${VideoMediaProbe.SOURCE_URL}",
             "sourceRevision":"${VideoMediaProbe.SOURCE_REVISION}",
             "sourceSha256":"${VideoMediaProbe.SOURCE_SHA256}",
             "ffmpegSha256":"${VideoMediaProbe.FFMPEG_SHA256}",
             "ffprobeSha256":"${VideoMediaProbe.FFPROBE_SHA256}",
             "buildOptions":[${VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString { "\"$it\"" }}],
             "notices":["test"]}
        """.trimIndent())
        val runtime = motionRuntime(VideoGenerationDependencyPin("compositor", "a".repeat(64), "/runtime/compositor"))
            .copy(mediaManifest = VideoGenerationDependencyPin("media-manifest", sha(tools.resolve(VideoMediaProbe.MANIFEST_NAME)),
                tools.resolve(VideoMediaProbe.MANIFEST_NAME).toString()))
        val pins = (listOf(record.artifact) + record.consumedArtifacts).distinct().mapIndexed { index, artifact ->
            VideoGenerationDependencyPin("prepared-$index", artifact.sha256, store.resolveArtifact(projectRoot, artifact).toString())
        }
        val descriptor = VideoControlledMotionDescriptor(session.project.id, sourceIdentity, buildJsonObject {
            put("schema", CONTROLLED_MOTION_DESCRIPTOR_SCHEMA)
            put("preparedScene", Json { encodeDefaults = true }.encodeToJsonElement(VideoPreparedScene.serializer(), scene))
            put("seed", 42)
            put("fps", 30)
            put("canvas", buildJsonObject {
                put("width", 100); put("height", 60); put("coordinateSpaceId", "scene")
            })
            put("frameRange", buildJsonObject { put("startFrame", 0); put("frameCount", 30) })
            put("controls", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("id", "blink"); put("kind", "blink")
                    put("capabilityId", scene.motionCapabilities.single { it.control == VideoMotionControl.POSE_BLEND }.id)
                    put("amount", 0.5)
                })
            })
        }.toString(), runtime)
        val policy = VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000)
        val input = VideoControlledMotionGenerationInput("Blink gently.", pins + runtime.allPins,
            VideoControlledMotionRequest(pins, 0, 30, 42, descriptor), "Blink gently.", motionMedia(policy))
        val backendId = "controlled-local"
        val request = VideoGenerationJobRequest("request", session.project.id, backendId, emptyList(), input,
            controlledMotionRequestFingerprint(backendId, input, emptyList(), 1), 1, Instant.now().toString(), policy)
        val publicationRoot = Files.createDirectories(projectRoot.resolve("controlled-outputs"))
        val source = publicationRoot.resolve("attempt/preview.mp4")
        Files.createDirectories(source.parent)
        Files.writeString(source, "owned silent controlled preview")
        val output = VideoGenerationOutput("attempt-preview", "attempt", "attempt-preview", Instant.now().toString(),
            "attempt/preview.mp4", sha(source), Files.size(source))
        val attempt = VideoGenerationAttempt("attempt", request.id, 1, "owner", VideoGenerationAttemptStatus.SUCCEEDED,
            VideoSubmissionPhase.ACKNOWLEDGED, Instant.now().toString(), finishedAt = Instant.now().toString(),
            controlledEvidence = VideoControlledAttemptEvidence(VideoControlledStage.COMPLETED,
                VideoControlledOutputEvidence(output.backendOutputId, output.relativePath!!, output.sha256!!, output.byteCount!!)))
        val ledger = VideoJobLedger("domain", Instant.now().toString(), listOf(VideoGenerationJob(request,
            listOf(attempt), listOf(output), output.id)))
        val persistence = object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String) = ledger
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger = error("Import cannot write job ledger")
        }
        var probes = 0
        var durationTicks = 30
        fun videoStream() =  """{"codec_type":"video","codec_name":"h264","width":100,"height":60,
            "sample_aspect_ratio":"1:1","avg_frame_rate":"30/1","nb_read_frames":"30",
            "time_base":"1/30","start_pts":"0","duration_ts":"$durationTicks"}"""
        val probe = VideoMediaProbe { job, _ ->
            probes++
            Files.createDirectory(job.workingDirectory)
            val text = when {
                "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                job.executable.fileName.toString() == "ffprobe" && "-show_frames" in job.arguments ->
                    """{"frames":[${(0 until 30).joinToString { "{\"best_effort_timestamp\":$it}" }}]}"""
                job.executable.fileName.toString() == "ffprobe" -> """{"streams":[${videoStream()}],"format":{"duration":"1.0"}}"""
                else -> ""
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        val resultImport = VideoResultImport(store, probe, idFactory = { "imported" }, controlledOutputRoot = publicationRoot)
        val service = VideoClipGeneration(VideoScenePreparation(), VideoJobCoordinator("domain", persistence, emptyList()),
            resultImport, VideoMotionRenderer(), backendId, policy, projects = store)
        val capability = service.capabilities(session, scene.id)
        assertEquals(setOf(VideoSceneMotionIntent.BLINK, VideoSceneMotionIntent.BREATHING), capability.first().supportedControls.toSet())
        assertTrue(VideoCapabilityBlockerCode.MISSING_TOOL in capability.first().blockers.map { it.code })
        assertEquals(100, capability.first().preparedWidth)
        assertEquals(60, capability.first().preparedHeight)
        assertTrue(capability.last().supportedControls.isEmpty())
        // Complete, resolved Canvas bindings are required even when all role names and
        // the package manifest itself are present. This query never launches Node.
        val packageRoot = Files.createDirectories(root.resolve("node_modules/@napi-rs/canvas"))
        val loaded = listOf("index.js", "js-binding.js", "geometry.js", "load-image.js")
            .map(packageRoot::resolve) + listOf(packageRoot.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node"))
        loaded.forEach { Files.createDirectories(it.parent); Files.writeString(it, "pinned $it") }
        val canvasManifest = packageRoot.resolve("package.json")
        Files.writeString(canvasManifest, """{"name":"@napi-rs/canvas","version":"0.1.80"}""")
        val runtimePaths = mapOf(
            "node" to root.resolve("node"), "compositor" to root.resolve("render.cjs"),
            "scenery" to root.resolve("scenery.cjs"), "canvas-manifest" to canvasManifest,
            "ffmpeg" to tools.resolve("ffmpeg"), "ffprobe" to tools.resolve("ffprobe"),
            "media-manifest" to tools.resolve(VideoMediaProbe.MANIFEST_NAME),
        ) + loaded.mapIndexed { index, path -> "canvas-artifact-$index" to path }.toMap()
        runtimePaths.values.filterNot(Files::exists).forEach { Files.writeString(it, "pinned $it") }
        val completePins = runtimePaths.map { (id, path) -> VideoGenerationDependencyPin(id, sha(path), path.toRealPath().toString()) }
        // The manifest and one artifact used to pass this readiness test.
        val incompletePins = completePins.filter { it.id in setOf("node", "compositor", "scenery", "canvas-manifest",
            "ffmpeg", "ffprobe", "media-manifest", "canvas-artifact-0") }
        assertTrue(VideoCapabilityBlockerCode.MISSING_TOOL in service.capabilities(session, scene.id, incompletePins)
            .first().blockers.map { it.code })
        val wrongArtifact = root.resolve("other-canvas.bin")
        Files.writeString(wrongArtifact, "pinned other artifact")
        assertTrue(VideoCapabilityBlockerCode.MISSING_TOOL in service.capabilities(session, scene.id,
            completePins.map { if (it.id == "canvas-artifact-1") it.copy(ownedPath = wrongArtifact.toString(),
                sha256 = sha(wrongArtifact)) else it }).first().blockers.map { it.code })
        assertFalse(VideoCapabilityBlockerCode.MISSING_TOOL in service.capabilities(session, scene.id, completePins)
            .first().blockers.map { it.code })
        val command = VideoCompletedTakeImport(session, session.project.revision, request.id, attempt.id, output.id)
        val preparedPath = store.resolveArtifact(projectRoot, record.consumedArtifacts.first())
        val originalPrepared = Files.readAllBytes(preparedPath)
        try {
            Files.writeString(preparedPath, "changed after admission")
            assertIs<VideoClipGenerationResult.Rejected>(service.importCompleted(command))
        } finally { Files.write(preparedPath, originalPrepared) }
        assertTrue(store.open(projectRoot).takeVersions.isEmpty())
        durationTicks = 31
        assertIs<VideoClipGenerationResult.Rejected>(service.importCompleted(command))
        assertTrue(store.open(projectRoot).takeVersions.isEmpty())
        durationTicks = 30
        // Mutate in the third durable-job refresh, after the outside-lock
        // digest check but before the publication guard. Retain inode, length
        // and mtime: the content seal must also detect the changed ctime.
        for (path in listOf(source, preparedPath)) {
            val original = Files.readAllBytes(path)
            val timestamp = Files.getLastModifiedTime(path)
            var refreshes = 0
            try {
                assertFailsWith<IllegalArgumentException> {
                    resultImport.import(session, session.project.revision, output, input, request,
                        currentJob = {
                            if (++refreshes == 3) {
                                val changed = original.clone()
                                changed[0] = (changed[0].toInt() xor 1).toByte()
                                Files.write(path, changed)
                                Files.setLastModifiedTime(path, timestamp)
                                assertEquals(original.size.toLong(), Files.size(path))
                                assertEquals(timestamp, Files.getLastModifiedTime(path))
                            }
                            ledger.jobs.single()
                        })
                }
                assertEquals(3, refreshes, "Mutation must occur at the final publication check")
            } finally {
                Files.write(path, original)
                Files.setLastModifiedTime(path, timestamp)
            }
            assertTrue(store.open(projectRoot).takeVersions.isEmpty())
        }
        val result = service.importCompleted(command)
        val imported = assertIs<VideoClipGenerationResult.Imported>(result, result.toString())
        assertTrue(probes > 0, "The persisted output must pass independent media validation")
        assertEquals(30, imported.result.facts.frameCount)
        assertEquals(30.0, imported.result.facts.frameRate)
        assertEquals(record.sourceLookId, imported.result.take.lookId)
        val take = imported.result.take
        assertEquals(session.project.id, take.provenance?.projectId)
        assertEquals(request.id, take.provenance?.requestId)
        assertEquals(attempt.id, take.provenance?.attemptId)
        assertEquals(output.id, take.provenance?.outputId)
        assertEquals(backendId, take.provenance?.backendId)
        assertEquals(request.requestFingerprint, take.provenance?.executableFingerprint)
        assertEquals(sourceIdentity, take.provenance?.sourceIdentity)
        assertEquals(finished, take.provenance?.finishedReferenceId)
        assertEquals(record.id, take.provenance?.preparedSceneId)
        assertEquals(record.sourceLookId, take.provenance?.persistedLookId)
        assertEquals(input.dependencyPins, take.provenance?.consumedPins)
        assertEquals("NONE", take.conversion)
        assertEquals(sha(source), take.sourceMeasurement?.sha256)
        assertEquals(Files.size(source), take.sourceMeasurement?.bytes)
        assertEquals(take.sourceMeasurement, take.publishedMeasurement)
        assertEquals(30L, take.publishedMeasurement?.decodedFrameCount)
        assertEquals(0, take.publishedMeasurement?.audioStreamCount)
        assertEquals(listOf(imported.result.take), store.open(projectRoot).takeVersions)
        assertTrue(store.open(projectRoot).selectedTakeIds.isEmpty())
        assertEquals(sha(source), sha(store.resolveArtifact(projectRoot, imported.result.take.artifact)))
        assertEquals(sha(source), output.sha256)
        assertEquals("owned silent controlled preview", Files.readString(source))
        val refreshed = VideoProjectSession(projectRoot, store.open(projectRoot))
        val replay = service.importCompleted(command.copy(session = refreshed, expectedRevision = refreshed.project.revision))
        val reused = assertIs<VideoClipGenerationResult.Imported>(replay, replay.toString())
        assertEquals(take, reused.result.take)
        assertEquals(refreshed.project, reused.result.project)
        assertEquals(1, store.open(projectRoot).takeVersions.size)
        val jobView = service.jobs(VideoProjectSession(projectRoot, store.open(projectRoot))).single()
        assertEquals(VideoControlledStage.COMPLETED, jobView.controlledStage)
        assertEquals(output.id, jobView.currentOutputId)
        assertEquals(null, jobView.observedProgressPercent, "A persisted fake completion without an observation must not invent progress")
        val unreviewed = jobView.takes.single()
        assertEquals(take.id, unreviewed.id)
        assertEquals(VideoTakeReviewStatus.UNREVIEWED, unreviewed.review)
        assertFalse(unreviewed.selected)
        val beforeReview = store.open(projectRoot)
        val selected = store.save(projectRoot, beforeReview.revision, beforeReview.copy(
            selectedTakeIds = listOf(take.id), revision = beforeReview.revision + 1))
        val selectedView = service.jobs(VideoProjectSession(projectRoot, selected)).single().takes.single()
        assertEquals(VideoTakeReviewStatus.UNREVIEWED, selectedView.review)
        assertTrue(selectedView.selected)
        // A persisted but renderer-incompatible subject range must not be advertised.
        val breathing = scene.motionCapabilities.single { it.control == VideoMotionControl.TRANSLATE_Y &&
            it.targetType == VideoMotionTargetType.LAYER }
        // Invalid units cannot be persisted as a prepared scene in the first place.
        assertFailsWith<IllegalArgumentException> {
            scene.copy(motionCapabilities = scene.motionCapabilities.map {
                if (it.id == breathing.id) it.copy(unit = VideoMotionUnit.RATIO) else it
            })
        }
        val incompatible = scene.copy(id = VideoVersionedId("scene-other", 1), motionCapabilities =
            scene.motionCapabilities.map { if (it.id == breathing.id) it.copy(minimum = 0.0, maximum = 0.0, defaultValue = 0.0) else it })
        val revised = scenes.save(projectRoot, selected.revision, incompatible)
        val projection = service.capabilities(VideoProjectSession(projectRoot, revised), incompatible.id, completePins).first()
        assertTrue(VideoSceneMotionIntent.BLINK in projection.supportedControls)
        assertFalse(VideoSceneMotionIntent.BREATHING in projection.supportedControls)
        // Keep the eligible image in the library, but pin a different, inspiration-only
        // image as this prepared scene's actual finished layer and source reference.
        val inspiration = assertIs<VideoAssetImportResult.Imported>(VideoAssetImport(lifecycle, VideoImageFiles(),
            idFactory = { "inspiration" }).import(VideoProjectSession(projectRoot, revised),
                ImportVideoAsset(picture("inspiration", false), VideoReferenceRole.STYLE,
                    usageIntent = VideoAssetUsageIntent.INSPIRATION_ONLY)))
        val inspirationPin = VideoPreparedReferencePin(inspiration.asset.id,
            inspiration.session.project.referenceVersions.single { it.id == inspiration.asset.id }.artifact,
            inspiration.asset.original)
        val mismatched = scene.copy(id = VideoVersionedId("scene-mismatch", 1),
            source = scene.source.copy(references = scene.source.references.map {
                if (it.id == finished) inspirationPin else it
            }), layers = scene.layers.map {
                if (it.kind == VideoLayerKind.FINISHED_SCENE) it.copy(image = inspiration.asset.original) else it
            })
        val mismatchedProject = scenes.save(projectRoot, inspiration.session.project.revision, mismatched)
        val mismatchedCapability = service.capabilities(VideoProjectSession(projectRoot, mismatchedProject),
            mismatched.id, completePins).first()
        assertTrue(VideoCapabilityBlockerCode.MISSING_ARTWORK in mismatchedCapability.blockers.map { it.code })
        assertFalse(mismatchedCapability.available)
        assertFalse(VideoCapabilityBlockerCode.MISSING_ARTWORK in service.capabilities(
            VideoProjectSession(projectRoot, mismatchedProject), incompatible.id, completePins).first().blockers.map { it.code })
    }

    @Test fun `cancellation interrupts immutable take hashing and copying before publication`() {
        val root = Files.createTempDirectory("video-cancellable-take-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val source = root.resolve("large.mp4")
        RandomAccessFile(source.toFile(), "rw").use { it.setLength(256L * 1024 * 1024) }
        val digest = sha(source)
        val executor = Executors.newSingleThreadExecutor()
        try {
            // The importer independently hashes the pinned output (and later the validated
            // derivative). Exercise its streaming loop, not just the store's copy path.
            val importer = VideoResultImport(store, VideoMediaProbe())
            val importHash = VideoResultImport::class.java.getDeclaredMethod(
                "sha256", Path::class.java, VideoMediaProcessCancellation::class.java,
            ).also { it.isAccessible = true }
            val importCancellation = VideoMediaProcessCancellation()
            val importStarted = CountDownLatch(1)
            val importHashing = executor.submit<Throwable?> {
                importStarted.countDown()
                try {
                    importHash.invoke(importer, source, importCancellation)
                    null
                } catch (error: InvocationTargetException) { error.cause }
            }
            assertTrue(importStarted.await(5, TimeUnit.SECONDS))
            Thread.sleep(5)
            assertFalse(importHashing.isDone, "Importer hash must still be in progress")
            importCancellation.cancel()
            val importError = assertIs<VideoMediaProcessException>(importHashing.get(20, TimeUnit.SECONDS))
            assertEquals(VideoMediaProcessFailure.CANCELLED, importError.failure)

            val hashingCancellation = VideoMediaProcessCancellation()
            val started = CountDownLatch(1)
            val hashing = executor.submit<Throwable?> {
                started.countDown()
                runCatching {
                    store.copyImmutableArtifact(root, source, "takes/hash/v1/preview.mp4", digest, hashingCancellation)
                }.exceptionOrNull()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            Thread.sleep(5)
            assertFalse(hashing.isDone, "Hashing must still be in progress when cancellation arrives")
            hashingCancellation.cancel()
            val hashError = assertIs<VideoMediaProcessException>(hashing.get(20, TimeUnit.SECONDS))
            assertEquals(VideoMediaProcessFailure.CANCELLED, hashError.failure)
            assertFalse(Files.exists(root.resolve("takes/hash/v1/preview.mp4")))

            // A temporary .take-import file exists only once the store has finished hashing
            // the source and entered its long copy. Cancellation must stop that copy itself.
            val cancellation = VideoMediaProcessCancellation()
            val target = "takes/copy/v1/preview.mp4"
            val copying = executor.submit<Throwable?> {
                runCatching { store.copyImmutableArtifact(root, source, target, digest, cancellation) }.exceptionOrNull()
            }
            val parent = root.resolve("takes/copy/v1")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var observed = false
            while (System.nanoTime() < deadline && !copying.isDone) {
                if (Files.isDirectory(parent)) {
                    Files.list(parent).use { paths ->
                        observed = paths.anyMatch { it.fileName.toString().startsWith(".take-import-") &&
                            runCatching { Files.size(it) > 0 }.getOrDefault(false) }
                    }
                    if (observed) break
                }
                Thread.sleep(1)
            }
            assertTrue(observed, "The cancellation test must reach an in-progress copy")
            cancellation.cancel()
            val error = assertIs<VideoMediaProcessException>(copying.get(20, TimeUnit.SECONDS))
            assertEquals(VideoMediaProcessFailure.CANCELLED, error.failure)
            assertFalse(Files.exists(root.resolve(target)))
            assertTrue(store.open(root).takeVersions.isEmpty())
        } finally { executor.shutdownNow() }
    }

    @Test fun `immutable take bytes survive source replacement and reopen without implicit selection`() {
        val root = Files.createTempDirectory("video-take-import-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val source = root.resolve("source.mp4")
        Files.write(source, byteArrayOf(1, 2, 3))
        val sourceHash = sha(source)
        val artifact = store.copyImmutableArtifact(root, source, "takes/take-1/v1/preview.mp4", sourceHash)
        val take = fixtureTake(project, root, VideoVersionedId("take-1", 1), artifact, "2026-09-24T00:00:01Z")
        val saved = store.save(root, 0, project.copy(takeVersions = listOf(take), revision = 1))
        assertEquals(1, saved.revision)
        assertTrue(saved.selectedTakeIds.isEmpty())
        Files.write(source, byteArrayOf(9, 8, 7))
        val reopened = store.open(root)
        assertEquals(saved, reopened)
        assertEquals(sourceHash, sha(store.resolveArtifact(root, artifact)))
        assertNotEquals(sourceHash, sha(source))
    }

    @Test fun `replacement appends a distinct version and preserves earlier take`() {
        val root = Files.createTempDirectory("video-take-replace-")
        val store = VideoProjectStore(listOf(Files.createTempDirectory("midi-protected-")))
        val project = VideoProject("project", "Project", "2026-09-24T00:00:00Z")
        store.create(root, project)
        val firstSource = root.resolve("first.mp4"); Files.write(firstSource, byteArrayOf(1, 1))
        val firstArtifact = store.copyImmutableArtifact(root, firstSource, "takes/take-1/v1/preview.mp4", sha(firstSource))
        val first = fixtureTake(project, root, VideoVersionedId("take-1", 1), firstArtifact, "2026-09-24T00:00:01Z")
        val afterFirst = store.save(root, 0, project.copy(takeVersions = listOf(first), revision = 1))
        val secondSource = root.resolve("second.mp4"); Files.write(secondSource, byteArrayOf(2, 2))
        val secondArtifact = store.copyImmutableArtifact(root, secondSource, "takes/take-2/v1/preview.mp4", sha(secondSource))
        val second = fixtureTake(project, root, VideoVersionedId("take-2", 1), secondArtifact, "2026-09-24T00:00:02Z")
        val afterSecond = store.save(root, 1, afterFirst.copy(takeVersions = afterFirst.takeVersions + second, revision = 2))
        assertEquals(listOf(first, second), afterSecond.takeVersions)
        assertFalse(afterSecond.selectedTakeIds.isNotEmpty())
        assertEquals(firstArtifact.sha256, sha(store.resolveArtifact(root, firstArtifact)))
    }

    @Test fun `flat admission verifies one finished image and pinned short route before durable fake submission`() {
        val root = Files.createTempDirectory("flat-admission-").toRealPath()
        val projectRoot = root.resolve("video-project")
        val store = VideoProjectStore(listOf(root.resolve("protected-midi")))
        val lifecycle = VideoProjectLifecycle(store)
        val initial = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle.create(
            CreateVideoProject(projectRoot, "Flat", "flat-project"))).session
        val picture = root.resolve("finished.png")
        val image = BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { graphics.color = Color.BLUE; graphics.fillRect(0, 0, 100, 60) } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", picture.toFile()))
        val imported = assertIs<VideoAssetImportResult.Imported>(VideoAssetImport(lifecycle, VideoImageFiles(),
            idFactory = { "finished" }).import(initial, ImportVideoAsset(picture, VideoReferenceRole.COMPLETE_SCENE)))
        val session = imported.session
        val graph = root.resolve("short-shot-api.json")
        Files.write(graph, checkNotNull(javaClass.getResourceAsStream("/video/comfyui/short-shot-api.json")).use { it.readBytes() })
        val runtime = root.resolve("runtime-profile.json")
        Files.write(runtime, checkNotNull(javaClass.getResourceAsStream("/video/comfyui/runtime-profile.json")).use { it.readBytes() })
        val graphPin = VideoGenerationDependencyPin(ComfyShortI2VBinding.WORKFLOW_ID, sha(graph), graph.toString())
        val runtimePin = VideoGenerationDependencyPin("flat-runtime-profile", sha(runtime), runtime.toString())
        val models = listOf(
            VideoModelRequirement("ltx-2-3-distilled-q4", "22b-distilled-1.1-Q4_K_M", "5d09efdc0b8ec2054c44a05366cd7c6634ffa333b379b1f8baf018a78974b73d"),
            VideoModelRequirement("gemma-3-12b-it-qat", "Q4_K_XL", "da98f81c86916ed1c76b3eeda56b25cb7b8352b01093e2edb8028110fe2cb53b"),
            VideoModelRequirement("ltx-2-3-connectors", "distilled-1.1", "c61cbb396e2a8175d8b2da51f0fdac885a4ccd22c9f64dafa5aa2c455dc8a507"),
            VideoModelRequirement("ltx-2-3-video-vae", "distilled-1.1", "e68d6d8f8a42942ac9b862cc315beb3bc30805a8876c7ad63ba5bf7a2b8e168a"),
        )
        val policy = VideoLocalExecutionPolicy(1_000_000, 1_000_000, 60_000)
        var ledger = VideoJobLedger("shared", Instant.now().toString())
        var launches = 0
        var lastInput: VideoClipGenerationInput? = null
        var fakeCompletedOutput: VideoBackendOutput? = null
        val backend = object : VideoGenerationBackendPort {
            override val backendId = LocalVideoBackend.BACKEND_ID
            override fun availability() = VideoBackendAvailability(backendId, VideoBackendAvailabilityStatus.AVAILABLE,
                Instant.now().toString(), setOf(VideoGenerationInputKind.VIDEO),
                models.map { VideoAvailableModel(it.id, it.version, it.sha256) }, "owned fake")
            override fun submit(command: VideoBackendSubmissionCommand): VideoBackendSubmission {
                launches++
                lastInput = assertIs<VideoClipGenerationInput>(command.input)
                return VideoBackendSubmission.Uncertain("Fake submission; no model launched")
            }
            override fun observe(ownedAttempt: VideoOwnedBackendAttempt): VideoBackendObservation =
                fakeCompletedOutput?.let { VideoBackendObservation.Completed("fake-owned", it) }
                    ?: VideoBackendObservation.Unknown("fake")
            override fun requestCancellation(ownedAttempt: VideoOwnedBackendAttempt) = VideoBackendCancellation.Unknown("fake")
        }
        val persistence = object : VideoJobPersistence {
            override fun loadOrCreate(admissionDomainId: String, createdAt: String) = ledger
            override fun compareAndSet(expectedRevision: Long, replacement: VideoJobLedger): VideoJobLedger {
                assertEquals(ledger.revision, expectedRevision)
                ledger = replacement
                return ledger
            }
        }
        val coordinator = VideoJobCoordinator("shared", persistence, listOf(backend))
        fun service(graph: VideoGenerationDependencyPin = graphPin, runtimePins: List<VideoGenerationDependencyPin> = listOf(runtimePin),
                    selectedModels: List<VideoModelRequirement> = models,
                    selectedPolicy: VideoExecutionPolicy = policy) = VideoClipGeneration(VideoScenePreparation(), coordinator,
            VideoResultImport(store, VideoMediaProbe()), VideoMotionRenderer(), "controlled-local", selectedPolicy,
            selectedModels, projects = store, flatGraph = graph, flatRuntimePins = runtimePins)
        val command = VideoFlatGenerationRequest(session, session.project.revision, imported.asset.id,
            "  Drift across the view.\nKeep the drawing.  ", 5_160, requestId = "stable-flat")
        fun flatCapability(generator: VideoClipGeneration = service()) =
            generator.capabilities(session).single { it.route == VideoClipRoute.FLAT_IMAGE_I2V }
        assertTrue(flatCapability().available)
        assertTrue(flatCapability().blockers.isEmpty())
        fun blockedConfiguration(generator: VideoClipGeneration) {
            val capability = flatCapability(generator)
            assertFalse(capability.available)
            assertTrue(VideoCapabilityBlockerCode.MISSING_TOOL in capability.blockers.map { it.code }, capability.toString())
            assertEquals(0, launches, "Capability queries must not submit work")
        }
        blockedConfiguration(service(graphPin.copy(sha256 = "f".repeat(64))))
        blockedConfiguration(service(runtimePins = listOf(runtimePin.copy(sha256 = "f".repeat(64)))))
        val originalRuntime = Files.readAllBytes(runtime)
        try {
            Files.writeString(runtime, "changed runtime")
            blockedConfiguration(service())
        } finally { Files.write(runtime, originalRuntime) }
        val originalGraph = Files.readAllBytes(graph)
        try {
            Files.writeString(graph, "changed graph")
            blockedConfiguration(service())
        } finally { Files.write(graph, originalGraph) }
        val unsafeGraph = root.resolve("unsafe-graph")
        Files.createSymbolicLink(unsafeGraph, graph)
        blockedConfiguration(service(graphPin.copy(ownedPath = unsafeGraph.toString())))
        blockedConfiguration(service(runtimePins = emptyList()))
        blockedConfiguration(service(runtimePins = listOf(runtimePin.copy(id = "flat-runtime-other"))))
        blockedConfiguration(service(runtimePins = listOf(runtimePin, runtimePin.copy(id = "flat-runtime-other"))))
        blockedConfiguration(service(selectedModels = models.dropLast(1)))
        blockedConfiguration(service(selectedModels = models.map { if (it.id == "gemma-3-12b-it-qat") it.copy(sha256 = "a".repeat(64)) else it }))
        val hosted = VideoHostedExecutionPolicy("budget", "USD", 1, "2026-09-25T00:00:00Z", "2026-09-26T00:00:00Z", 1)
        val nonlocal = service(selectedPolicy = hosted)
        assertFalse(flatCapability(nonlocal).available)
        assertTrue(VideoCapabilityBlockerCode.UNAVAILABLE_RUNTIME in flatCapability(nonlocal).blockers.map { it.code })
        assertTrue(flatCapability().available)
        fun rejected(request: VideoFlatGenerationRequest, fragment: String, generator: VideoClipGeneration = service()) {
            val before = ledger
            val result = assertIs<VideoClipGenerationResult.Rejected>(generator.generateFlat(request))
            assertTrue(result.reason.contains(fragment, ignoreCase = true), result.reason)
            assertEquals(before, ledger, "A rejection must not reserve a durable attempt")
            assertEquals(0, launches)
        }
        rejected(command.copy(expectedRevision = 0), "revision")
        rejected(command.copy(finishedReferenceId = VideoVersionedId("missing", 1)), "not an imported")
        rejected(command.copy(durationMillis = Long.MAX_VALUE), "overflow")
        rejected(command.copy(durationMillis = 5_000), "8*n+1")
        rejected(command.copy(durationMillis = 20_000), "8*n+1")
        rejected(command.copy(durationMillis = 30_000), "8*n+1")
        rejected(command.copy(durationMillis = 20_520), "129 frames") // 513 legal LTX frames, still unsupported here.
        rejected(command.copy(durationMillis = 30_120), "129 frames") // 753 legal LTX frames, still unsupported here.
        rejected(command.copy(framesPerSecond = 30), "25 fps")
        rejected(command.copy(width = 576), "768x448")
        rejected(command.copy(regionalControls = listOf(VideoSceneMotionControlRequest("region", VideoSceneMotionIntent.BLINK,
            "unsupported", 0.0, 1.0, 0.5))), "regional")
        rejected(command, "graph", service(graphPin.copy(sha256 = "f".repeat(64))))
        rejected(command, "runtime", service(runtimePins = listOf(runtimePin.copy(sha256 = "f".repeat(64)))))
        rejected(command, "models", service(selectedModels = emptyList()))
        rejected(command, "models", service(selectedModels = models.dropLast(1)))
        rejected(command, "models", service(selectedModels = models.map { if (it.id == "gemma-3-12b-it-qat") it.copy(sha256 = "a".repeat(64)) else it }))
        rejected(command, "runtime", service(runtimePins = listOf(runtimePin.copy(id = "flat-runtime-other"))))
        rejected(command, "runtime", service(runtimePins = listOf(runtimePin, runtimePin.copy(id = "flat-runtime-other"))))
        rejected(command, "local policy", nonlocal)
        val original = Files.readAllBytes(store.resolveArtifact(projectRoot, imported.asset.original.artifact))
        val originalPath = store.resolveArtifact(projectRoot, imported.asset.original.artifact)
        try {
            Files.writeString(originalPath, "changed original")
            rejected(command, "pinned byte")
        } finally { Files.write(originalPath, original) }
        try {
            Files.delete(originalPath)
            rejected(command, "missing")
            Files.createSymbolicLink(originalPath, picture)
            rejected(command, "symbolic link")
        } finally {
            Files.deleteIfExists(originalPath)
            Files.write(originalPath, original)
        }
        val storedGraph = Files.readAllBytes(graph)
        try {
            Files.writeString(graph, "altered graph")
            rejected(command, "graph")
        } finally { Files.write(graph, storedGraph) }
        val symlink = root.resolve("graph-symlink.json")
        Files.createSymbolicLink(symlink, graph)
        rejected(command, "unsafe", service(graphPin.copy(ownedPath = symlink.toString())))
        val accepted = assertIs<VideoClipGenerationResult.Admitted>(service().generateFlat(command))
        assertEquals(1, launches)
        assertEquals(1, ledger.jobs.size)
        val bound = requireNotNull(lastInput)
        assertEquals(command.primaryPrompt, bound.primaryPrompt)
        assertTrue(bound.prompt.startsWith(command.primaryPrompt))
        assertTrue(bound.prompt.length > command.primaryPrompt.length, "Derived guidance must remain distinguishable")
        assertEquals(5_160L, bound.durationMillis)
        assertEquals(25, bound.framesPerSecond)
        assertEquals(ComfyShortI2VBinding.workflow, bound.comfyWorkflow)
        assertEquals(setOf(ComfyShortI2VBinding.WORKFLOW_ID, ComfyShortI2VBinding.IMAGE_ID,
            "flat-runtime-profile", "flat-guidance", "flat-guideline-source"), bound.dependencyPins.map { it.id }.toSet())
        assertEquals(comfyRequestFingerprint(session.project.id, LocalVideoBackend.BACKEND_ID, bound, models),
            accepted.request.requestFingerprint)
        assertEquals(1, accepted.result.let { assertIs<VideoJobResult.Accepted>(it).job.attempts.size })
        assertIs<VideoClipGenerationResult.Admitted>(service().generateFlat(command.copy(requestId = "another-click")))
        assertEquals(1, launches, "Same immutable intent reuses the in-flight attempt")
        assertEquals(1, ledger.jobs.size)
        val collision = assertIs<VideoClipGenerationResult.Rejected>(service().generateFlat(command.copy(primaryPrompt = "Different motion")))
        assertTrue(collision.reason.contains("different immutable inputs", ignoreCase = true), collision.reason)
        assertEquals(1, launches)
        assertEquals(session.project, store.open(projectRoot))

        // Fake transport completed output: the import path still independently decodes
        // both media identities via the injected probe, without loading any model.
        Files.createDirectory(root.resolve("protected-midi"))
        val publication = Files.createDirectory(root.resolve("comfy-publication"))
        val attemptId = assertIs<VideoJobResult.Accepted>(accepted.result).attempt!!.id
        val relative = "generated/stable-flat/$attemptId/prompt-preview.mp4"
        val media = publication.resolve(relative)
        Files.createDirectories(media.parent)
        Files.writeString(media, "owned flat output")
        fakeCompletedOutput = VideoBackendOutput("prompt:16:/preview.mp4", relative, sha(media), Files.size(media))
        val completion = assertIs<VideoJobResult.Accepted>(coordinator.reconcile(accepted.request.id, attemptId))
        assertEquals(VideoGenerationAttemptStatus.SUCCEEDED, completion.attempt?.status)
        val output = completion.job.outputs.single()
        val attempt = completion.job.attempts.single()
        assertEquals(completion.job, ledger.jobs.single())
        val tools = Files.createDirectory(root.resolve("media-tools"))
        val java = Path.of(System.getProperty("java.home"), "bin", "java")
        for (name in listOf("ffmpeg", "ffprobe")) {
            val binary = Files.copy(java, tools.resolve(name))
            Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"))
        }
        Files.writeString(tools.resolve(VideoMediaProbe.MANIFEST_NAME), """
            {"schema":"melotrail-video-media-tools","version":1,
             "distributionId":"${VideoMediaProbe.DISTRIBUTION_ID}",
             "installation":"${VideoMediaProbe.INSTALLATION_STRATEGY}",
             "sourceUrl":"${VideoMediaProbe.SOURCE_URL}",
             "sourceRevision":"${VideoMediaProbe.SOURCE_REVISION}",
             "sourceSha256":"${VideoMediaProbe.SOURCE_SHA256}",
             "ffmpegSha256":"${VideoMediaProbe.FFMPEG_SHA256}",
             "ffprobeSha256":"${VideoMediaProbe.FFPROBE_SHA256}",
             "buildOptions":[${VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString { "\"$it\"" }}],
             "notices":["test"]}
        """.trimIndent())
        var ticks = 129
        var firstPts = 0
        var probes = 0
        val probe = VideoMediaProbe { job, _ ->
            probes++
            Files.createDirectory(job.workingDirectory)
            if (job.workingDirectory.fileName.toString() == "strip-incidental-audio")
                Files.writeString(Path.of(job.arguments.last()), "independent silent derivative")
            val text = when {
                "-version" in job.arguments -> "${job.executable.fileName} version 9.0.1\nconfiguration: " +
                    VideoMediaProbe.REQUIRED_BUILD_OPTIONS.sorted().joinToString(" ")
                "-show_frames" in job.arguments ->
                    """{"frames":[${(firstPts until firstPts + 129).joinToString { "{\"best_effort_timestamp\":$it}" }}]}"""
                else -> """{"streams":[{"codec_type":"video","codec_name":"h264","width":768,"height":448,
                    "sample_aspect_ratio":"1:1","avg_frame_rate":"25/1","nb_read_frames":"129",
                    "time_base":"1/25","start_pts":"$firstPts","duration_ts":"$ticks"}${if (job.arguments.last().endsWith("silent-take.mp4")) "" else ", {\"codec_type\":\"audio\"}"}],"format":{"duration":"5.16"}}"""
            }
            VideoMediaProcessResult(0, VideoMediaProcessOutput(text, text.length.toLong(), false),
                VideoMediaProcessOutput("", 0, false), Duration.ZERO, job.workingDirectory)
        }
        fun importer(rootPath: Path = publication) = VideoResultImport(store, probe, idFactory = { "flat-take" },
            comfyOutputRoot = rootPath, protectedMidiRoots = listOf(root.resolve("protected-midi")),
            flatMediaToolsDirectory = tools)
        fun importWith(imp: VideoResultImport = importer(), graph: VideoGenerationDependencyPin = graphPin,
                       selectedModels: List<VideoModelRequirement> = models,
                       cmd: VideoCompletedTakeImport = VideoCompletedTakeImport(session, session.project.revision,
                           accepted.request.id, attempt.id, output.id)): VideoClipGenerationResult =
            VideoClipGeneration(VideoScenePreparation(), coordinator, imp, VideoMotionRenderer(),
                "controlled-local", policy, selectedModels, projects = store, flatGraph = graph,
                flatRuntimePins = listOf(runtimePin)).importCompleted(cmd)
        fun fails(fragment: String, result: VideoClipGenerationResult) {
            val rejected = assertIs<VideoClipGenerationResult.Rejected>(result)
            assertTrue(rejected.reason.contains(fragment, ignoreCase = true), rejected.reason)
            assertTrue(store.open(projectRoot).takeVersions.isEmpty())
        }
        val flatCommand = VideoCompletedTakeImport(session, session.project.revision,
            accepted.request.id, attempt.id, output.id)
        fails("latest", importWith(cmd = flatCommand.copy(attemptId = "older")))
        val wrongRoot = Files.createDirectory(projectRoot.resolve("controlled-outputs"))
        fails("root", importWith(imp = importer(wrongRoot)))
        fails("policy", importWith(selectedModels = models.dropLast(1)))
        fails("graph", importWith(graph = graphPin.copy(sha256 = "f".repeat(64))))
        val graphBytes = Files.readAllBytes(graph)
        try {
            Files.writeString(graph, "changed graph after completion")
            fails("graph", importWith())
        } finally { Files.write(graph, graphBytes) }
        val runtimeBytes = Files.readAllBytes(runtime)
        try {
            Files.writeString(runtime, "changed runtime after completion")
            fails("runtime", importWith())
        } finally { Files.write(runtime, runtimeBytes) }
        val pinnedJob = ledger.jobs.single()
        ledger = ledger.copy(jobs = listOf(pinnedJob.copy(request = accepted.request.copy(
            backendId = "controlled-local", requestFingerprint = comfyRequestFingerprint(
                session.project.id, "controlled-local", bound, models)))))
        fails("backend", importWith())
        ledger = ledger.copy(jobs = listOf(pinnedJob))
        val changedModels = models.map { if (it.id == models.first().id) it.copy(sha256 = "f".repeat(64)) else it }
        ledger = ledger.copy(jobs = listOf(pinnedJob.copy(request = accepted.request.copy(
            modelRequirements = changedModels, requestFingerprint = comfyRequestFingerprint(
                session.project.id, LocalVideoBackend.BACKEND_ID, bound, changedModels)))))
        fails("policy", importWith())
        ledger = ledger.copy(jobs = listOf(pinnedJob))
        ticks = 128
        fails("measured", importWith())
        ticks = 129
        val link = publication.resolve(relative)
        val escaped = root.resolve("protected-midi/private.mp4")
        Files.move(link, escaped)
        Files.createSymbolicLink(link, escaped)
        fails("symbolic", importWith())
        Files.delete(link)
        Files.move(escaped, link)
        val imagePath = store.resolveArtifact(projectRoot, imported.asset.original.artifact)
        val imageBytes = Files.readAllBytes(imagePath)
        try {
            Files.writeString(imagePath, "changed image")
            fails("pinned", importWith())
        } finally { Files.write(imagePath, imageBytes) }
        val first = assertIs<VideoClipGenerationResult.Imported>(importWith())
        assertTrue(probes > 0)
        assertEquals(129L, first.result.take.sourceMeasurement?.decodedFrameCount)
        assertEquals(1, first.result.take.sourceMeasurement?.audioStreamCount)
        assertEquals(0, first.result.take.publishedMeasurement?.audioStreamCount)
        assertEquals("AUDIO_REMUX", first.result.take.conversion)
        assertTrue(first.result.take.sourceMeasurement?.sha256 != first.result.take.publishedMeasurement?.sha256)
        assertEquals(imported.asset.id, first.result.take.provenance?.finishedReferenceId)
        assertEquals(null, first.result.take.provenance?.preparedSceneId)
        assertEquals(null, first.result.take.lookId)
        assertEquals(bound.dependencyPins, first.result.take.provenance?.consumedPins)
        assertTrue(first.result.project.selectedTakeIds.isEmpty())
        val replaySession = VideoProjectSession(projectRoot, store.open(projectRoot))
        val replay = assertIs<VideoClipGenerationResult.Imported>(importWith(cmd = flatCommand.copy(
            session = replaySession, expectedRevision = replaySession.project.revision)))
        assertEquals(first.result.take, replay.result.take)
        assertEquals(replaySession.project, replay.result.project)
        assertEquals(1, replay.result.project.takeVersions.size)
        assertEquals(1, launches)
        val priorBytes = Files.readAllBytes(store.resolveArtifact(projectRoot, first.result.take.artifact))
        firstPts = 1
        val mediaCollision = assertIs<VideoClipGenerationResult.Rejected>(importWith(cmd = flatCommand.copy(
            session = replaySession, expectedRevision = replaySession.project.revision)))
        assertTrue(mediaCollision.reason.contains("collid", ignoreCase = true), mediaCollision.reason)
        firstPts = 0
        val sourceBytes = Files.readAllBytes(media)
        Files.writeString(media, "changed flat output")
        assertIs<VideoClipGenerationResult.Rejected>(importWith(cmd = flatCommand.copy(
            session = replaySession, expectedRevision = replaySession.project.revision)))
        Files.write(media, sourceBytes)
        assertEquals(replaySession.project, store.open(projectRoot))
        val originalJob = ledger.jobs.single()
        val newerAttempt = attempt.copy(id = "newer-flat-attempt", number = 2,
            ownershipToken = "newer-owned-flat")
        val newerOutput = output.copy(id = "newer-flat-output", attemptId = newerAttempt.id,
            relativePath = "generated/stable-flat/${newerAttempt.id}/prompt-preview.mp4")
        ledger = ledger.copy(jobs = listOf(originalJob.copy(attempts = originalJob.attempts + newerAttempt,
            outputs = originalJob.outputs + newerOutput, currentOutputId = newerOutput.id)))
        val older = assertIs<VideoClipGenerationResult.Rejected>(importWith(cmd = flatCommand.copy(
            session = replaySession, expectedRevision = replaySession.project.revision)))
        assertTrue(older.reason.contains("latest", ignoreCase = true), older.reason)
        ledger = ledger.copy(jobs = listOf(originalJob))
        assertEquals(replaySession.project, store.open(projectRoot))
        assertTrue(priorBytes.contentEquals(Files.readAllBytes(store.resolveArtifact(projectRoot, first.result.take.artifact))))
        assertEquals(first.result.take.publishedMeasurement?.sha256,
            sha(store.resolveArtifact(projectRoot, first.result.take.artifact)))
        assertEquals(sha(media), first.result.take.sourceMeasurement?.sha256)
    }

    private fun fixtureTake(project: VideoProject, root: Path, id: VideoVersionedId,
                            artifact: VideoArtifact, createdAt: String): VideoTakeRecord {
        val measurement = VideoTakeMeasurementRecord(artifact.sha256, Files.size(root.resolve(artifact.relativePath)),
            "h264", 100, 60, VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(30, 1),
            30, VideoTakeRationalRecord(1, 30), 0, 30, 1, 0, 0)
        val provenance = VideoTakeProvenanceRecord(project.id, "request-${id.id}", "attempt-${id.id}",
            "output-${id.id}", "comfyui-local", "a".repeat(64), "b".repeat(64),
            null, null, null, listOf(VideoGenerationDependencyPin("fixture", "c".repeat(64))))
        return VideoTakeRecord(id, artifact, null, createdAt, measurement, measurement, "NONE", provenance)
    }

    private fun sha(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
