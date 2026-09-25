package app.melotrail.video

import app.melotrail.video.adapter.VideoProjectStore
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
        rejection(command, "not yet connected")
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
        val service = VideoClipGeneration(VideoScenePreparation(), VideoJobCoordinator("domain", persistence, emptyList()),
            VideoResultImport(store, probe, idFactory = { "imported" }, controlledOutputRoot = publicationRoot),
            VideoMotionRenderer(), backendId, policy, projects = store)
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
