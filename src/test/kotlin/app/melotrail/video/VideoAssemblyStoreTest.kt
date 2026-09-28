package app.melotrail.video

import app.melotrail.video.adapter.VideoAssemblyStore
import app.melotrail.video.adapter.VideoAtomicWriteObserver
import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoAssemblyStoreTest {
    @TempDir lateinit var root: Path

    @Test
    fun `all target durations reopen with exact actions support chunks sources and work fingerprints`() {
        val f = fixture()
        val original = files(f.root)
        var project = f.project
        val saved = listOf(180L, 240L, 300L).mapIndexed { index, seconds ->
            val plan = proposed(f.request.copy(id = VideoVersionedId("plan", index + 1), durationSeconds = seconds))
            project = assemblies().save(f.root, project.revision, plan)
            val reopened = assemblies().load(f.root, plan.id)
            assertEquals(plan, reopened)
            assertEquals(seconds * 30, reopened.totalFrames)
            assertEquals(plan.actions, reopened.actions)
            assertEquals(plan.chunks, reopened.chunks)
            assertEquals(plan.work, reopened.work)
            assertEquals(plan.provenanceFingerprint, project.assemblyVersions.last().provenanceFingerprint)
            plan
        }
        assertEquals(3, project.assemblyVersions.size)
        assertTrue(saved.first().actions.isNotEmpty())
        assertTrue(saved.first().chunks[1].supportBefore > 0)
        assertTrue(project.takeVersions.isEmpty() && project.takeReviewEvents.isEmpty() && project.selectedTakeIds.isEmpty())
        assertEquals(f.project.selectedReferenceIds, project.selectedReferenceIds)
        assertEquals(f.project.preparedSceneVersions, project.preparedSceneVersions)
        original.filterKeys { it != VideoProjectStore.PROJECT_FILE }.forEach { (path, digest) ->
            assertEquals(digest, sha(f.root.resolve(path)))
        }
        val renamed = projects().save(f.root, project.revision, project.copy(name = "Renamed", revision = project.revision + 1))
        assertEquals(project.assemblyVersions, renamed.assemblyVersions)
        saved.forEach { assertEquals(it, assemblies().load(f.root, it.id)) }
    }

    @Test
    fun `ordinary saves cannot revise remove or duplicate assembly history`() {
        val f = fixture()
        val project = assemblies().save(f.root, f.project.revision, proposed(f.request))
        val before = files(f.root)
        val record = project.assemblyVersions.single()
        for (records in listOf(emptyList(), listOf(record.copy(createdAt = "2026-09-28T00:00:01Z")),
            listOf(record.copy(provenanceFingerprint = "0".repeat(64))), listOf(record, record))) {
            assertFailsWith<IllegalArgumentException> {
                projects().save(f.root, project.revision, project.copy(assemblyVersions = records, revision = project.revision + 1))
            }
            assertEquals(before, files(f.root))
        }
        assertFailsWith<IllegalArgumentException> {
            project.copy(assemblyVersions = listOf(record.copy(preparedSceneArtifact = VideoArtifact("missing.json", "0".repeat(64)))))
        }
        assertFailsWith<IllegalArgumentException> { record.copy(artifact = record.artifact.copy(relativePath = "other.json")) }
    }

    @Test
    fun `stale saves create no descriptor and successful IDs are never overwritten`() {
        val f = fixture()
        val first = proposed(f.request)
        val current = assemblies().save(f.root, f.project.revision, first)
        val before = files(f.root)
        val next = proposed(f.request.copy(id = VideoVersionedId("plan", 2)))
        assertFailsWith<VideoProjectConcurrencyException> { assemblies().save(f.root, f.project.revision, next) }
        assertFalse(Files.exists(f.root.resolve("assemblies/plan/v2")))
        assertFailsWith<IllegalArgumentException> { assemblies().save(f.root, current.revision, first) }
        assertEquals(before, files(f.root))
    }

    @Test
    fun `independent writers share the project CAS and only one descriptor is published`() {
        val f = fixture()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = (1..2).map { version -> pool.submit<Boolean> {
                start.await()
                try {
                    assemblies().save(f.root, f.project.revision,
                        proposed(f.request.copy(id = VideoVersionedId("race", version))))
                    true
                } catch (_: VideoProjectConcurrencyException) { false }
            } }
            start.countDown()
            assertEquals(1, futures.count { it.get(30, TimeUnit.SECONDS) })
            assertEquals(1, projects().open(f.root).assemblyVersions.size)
            assertEquals(1, Files.walk(f.root.resolve("assemblies")).use { paths -> paths.filter { it.fileName.toString() == "assembly.json" }.count() })
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `failed document publication retains exact orphan and retry cannot substitute different bytes`() {
        val f = fixture()
        val plan = proposed(f.request)
        val doc = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(doc)
        val failing = VideoAssemblyStore(projects(VideoAtomicWriteObserver { _, _ -> error("injected publication failure") }), CLOCK)
        assertFailsWith<VideoProjectSaveException> { failing.save(f.root, f.project.revision, plan) }
        assertContentEquals(before, Files.readAllBytes(doc))
        assertTrue(projects().open(f.root).assemblyVersions.isEmpty())
        val orphan = f.root.resolve("assemblies/plan/v1/assembly.json")
        val orphanBytes = Files.readAllBytes(orphan)
        val changed = proposed(f.request.copy(seed = 74))
        assertFailsWith<InvalidVideoProjectException> { assemblies().save(f.root, f.project.revision, changed) }
        assertContentEquals(orphanBytes, Files.readAllBytes(orphan))
        assertContentEquals(before, Files.readAllBytes(doc))
        val saved = assemblies().save(f.root, f.project.revision, plan)
        assertEquals(1, saved.assemblyVersions.size)
        assertContentEquals(orphanBytes, Files.readAllBytes(orphan))
        assertEquals(plan, assemblies().load(f.root, plan.id))
    }

    @Test
    fun `foreign missing and mismatched prepared or finished source pins reject without writing`() {
        val f = fixture()
        val plan = proposed(f.request)
        val before = files(f.root)
        val wrongOriginal = proposed(f.request.copy(preparedMotion = null, scheduledActions = emptyList(),
            finishedReferenceArtifact = VideoArtifact("unknown.png", "0".repeat(64))))
        for (invalid in listOf(plan.copy(preparedSceneId = VideoVersionedId("missing", 1)),
            plan.copy(preparedSceneArtifact = plan.preparedSceneArtifact.copy(sha256 = "0".repeat(64))),
            plan.copy(finishedReferenceId = VideoVersionedId("missing", 1)), wrongOriginal,
            proposed(f.request.copy(projectId = "another-project")))) {
            assertFailsWith<InvalidVideoProjectException> { assemblies().save(f.root, f.project.revision, invalid) }
            assertEquals(before, files(f.root))
        }
    }

    @Test
    fun `matching source digests cannot bypass decoded prepared image validation`() {
        val f = fixture()
        val scene = requireNotNull(f.request.preparedMotion).scene
        val altered = scene.copy(layers = scene.layers.map { layer ->
            if (layer.id == "subject") layer.copy(image = layer.image.copy(encodedBytes = layer.image.encodedBytes + 1)) else layer
        })
        val sceneRecord = f.project.preparedSceneVersions.single()
        val scenePath = f.root.resolve(sceneRecord.artifact.relativePath)
        val json = Json { encodeDefaults = true }
        Files.writeString(scenePath, json.encodeToString(altered))
        val newPin = sceneRecord.artifact.copy(sha256 = sha(scenePath))
        val doc = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        Files.writeString(doc, Files.readString(doc).replace(sceneRecord.artifact.sha256, newPin.sha256))
        val before = files(f.root)
        val plan = proposed(f.request).copy(preparedSceneArtifact = newPin)
        val error = assertFailsWith<InvalidVideoProjectException> { assemblies().save(f.root, f.project.revision, plan) }
        assertTrue(error.message.orEmpty().contains("decoded image facts changed"))
        assertEquals(before, files(f.root))
    }

    @Test
    fun `changed and missing input bytes reject both save and reopen without project writes`() {
        val f = fixture()
        val plan = proposed(f.request)
        val saved = assemblies().save(f.root, f.project.revision, plan)
        val doc = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(doc)
        val source = f.root.resolve(plan.finishedReferenceArtifact.relativePath)
        val sourceBytes = Files.readAllBytes(source)
        for (missing in listOf(false, true)) {
            if (missing) Files.delete(source) else Files.writeString(source, "corrupted image")
            assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, plan.id) }
            assertFailsWith<InvalidVideoProjectException> { assemblies().save(f.root, saved.revision,
                proposed(f.request.copy(id = VideoVersionedId("next", 1)))) }
            assertContentEquals(before, Files.readAllBytes(doc))
            Files.write(source, sourceBytes)
        }
        assertEquals(plan, assemblies().load(f.root, plan.id))
    }

    @Test
    fun `changed descriptor and forged project provenance are rejected read only`() {
        val f = fixture()
        val plan = proposed(f.request)
        val saved = assemblies().save(f.root, f.project.revision, plan)
        val record = saved.assemblyVersions.single()
        val descriptor = f.root.resolve(record.artifact.relativePath)
        val bytes = Files.readAllBytes(descriptor)
        val document = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        val projectBytes = Files.readAllBytes(document)
        Files.writeString(descriptor, "changed")
        assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, plan.id) }
        assertContentEquals(projectBytes, Files.readAllBytes(document))
        Files.write(descriptor, bytes)
        val forged = String(projectBytes).replace(record.provenanceFingerprint, "0".repeat(64))
        assertNotEquals(String(projectBytes), forged)
        Files.writeString(document, forged)
        assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, plan.id) }
        assertEquals(forged, Files.readString(document))
        assertContentEquals(bytes, Files.readAllBytes(descriptor))
    }

    @Test
    fun `unsupported invalid missing-version and oversized descriptors preserve recorded bytes`() {
        val f = fixture()
        val plan = proposed(f.request)
        val saved = assemblies().save(f.root, f.project.revision, plan)
        val record = saved.assemblyVersions.single()
        val descriptor = f.root.resolve(record.artifact.relativePath)
        val original = Files.readString(descriptor)
        val doc = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        val project = Files.readString(doc)
        for ((text, unsupported) in listOf(
            original.replace("\"plannerVersion\": 2", "\"plannerVersion\": 1") to true,
            original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99") to true,
            original.replace("\"plannerVersion\": 2,", "") to true,
            original.replace("\"durationSeconds\": 240", "\"durationSeconds\": 179") to false,
            "{" to false,
            (original + " ".repeat(1_048_576)) to false,
        )) {
            assertNotEquals(original, text)
            Files.writeString(descriptor, text)
            val alteredProject = project.replace(record.artifact.sha256, sha(descriptor))
            Files.writeString(doc, alteredProject)
            if (unsupported) assertFailsWith<UnsupportedVideoProjectException> { assemblies().load(f.root, plan.id) }
            else assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, plan.id) }
            assertEquals(text, Files.readString(descriptor))
            assertEquals(alteredProject, Files.readString(doc))
        }
    }

    @Test
    fun `oversized proposal is refused before descriptor or project publication`() {
        val f = fixture()
        val plan = proposed(f.request.copy(durationSeconds = 300, maximumOutputFramesPerChunk = 1))
        val before = files(f.root)
        assertFailsWith<IllegalArgumentException> { assemblies().save(f.root, f.project.revision, plan) }
        assertEquals(before, files(f.root))
        assertFalse(Files.exists(f.root.resolve("assemblies")))
    }

    @Test
    fun `legacy project version is rejected without automatic migration or artifact changes`() {
        val f = fixture()
        val doc = f.root.resolve(VideoProjectStore.PROJECT_FILE)
        val current = Files.readString(doc)
        val legacy = current.replace("\"version\": 5", "\"version\": 4")
        assertNotEquals(current, legacy)
        Files.writeString(doc, legacy)
        val before = files(f.root)
        assertFailsWith<UnsupportedVideoProjectException> { assemblies().save(f.root, f.project.revision, proposed(f.request)) }
        assertFailsWith<UnsupportedVideoProjectException> { assemblies().load(f.root, VideoVersionedId("plan", 1)) }
        assertEquals(before, files(f.root))
    }

    @Test
    fun `symlinked assembly folder and protected MIDI location reject before any descriptor write`() {
        val f = fixture()
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.createSymbolicLink(f.root.resolve("assemblies"), outside)
        val document = Files.readAllBytes(f.root.resolve(VideoProjectStore.PROJECT_FILE))
        assertFailsWith<InvalidVideoProjectException> { assemblies().save(f.root, f.project.revision, proposed(f.request)) }
        assertContentEquals(document, Files.readAllBytes(f.root.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals(0, Files.list(outside).use { it.count() })
        val midi = Files.createDirectory(root.resolve("midi"))
        Files.writeString(midi.resolve("project.json"), "protected MIDI sentinel")
        val before = files(midi)
        assertFailsWith<UnsafeVideoProjectLocationException> { assemblies().save(midi, 0, proposed(f.request)) }
        assertEquals(before, files(midi))
    }

    @Test
    fun `symlinked descriptor and unknown assembly ID reject on reopen`() {
        val f = fixture()
        val plan = proposed(f.request)
        val saved = assemblies().save(f.root, f.project.revision, plan)
        assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, VideoVersionedId("absent", 1)) }
        val path = f.root.resolve(saved.assemblyVersions.single().artifact.relativePath)
        val outside = root.resolve("external-assembly.json")
        Files.copy(path, outside)
        Files.delete(path)
        Files.createSymbolicLink(path, outside)
        val before = Files.readAllBytes(f.root.resolve(VideoProjectStore.PROJECT_FILE))
        assertFailsWith<InvalidVideoProjectException> { assemblies().load(f.root, plan.id) }
        assertContentEquals(before, Files.readAllBytes(f.root.resolve(VideoProjectStore.PROJECT_FILE)))
    }

    private fun projects(observer: VideoAtomicWriteObserver = VideoAtomicWriteObserver.NONE) =
        VideoProjectStore(listOf(root.resolve("midi")), observer)
    private fun assemblies() = VideoAssemblyStore(projects(), CLOCK)
    private fun proposed(request: VideoAssemblyPlanningRequest) =
        assertIs<VideoAssemblyPlanResult.Proposed>(VideoAssemblyPlanner().plan(request)).assembly
    private data class Fixture(val root: Path, val project: VideoProject, val request: VideoAssemblyPlanningRequest)

    private fun fixture(): Fixture {
        val projectRoot = root.resolve("video")
        val storage = projects()
        val lifecycle = VideoProjectLifecycle(storage, CLOCK)
        val created = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle.create(
            CreateVideoProject(projectRoot, "Assembly test", "video-test"))).session
        val source = image(root.resolve("source.png"), 1920, 1080, false, Color.BLUE)
        val imported = assertIs<VideoAssetImportResult.Imported>(VideoAssetImport(lifecycle, VideoImageFiles(), CLOCK,
            idFactory = { "finished" }).import(created, ImportVideoAsset(source, VideoReferenceRole.COMPLETE_SCENE)))
        fun placed(name: String, color: Color): VideoAssetImage {
            val path = image(projectRoot.resolve("prepared-assets/$name.png"), 64, 64, true, color)
            val facts = VideoImageFiles().inspect(path)
            return VideoAssetImage(VideoArtifact("prepared-assets/$name.png", facts.sha256), facts.format,
                facts.format.mediaType, facts.width, facts.height, Files.size(path), facts.hasAlphaChannel, facts.hasTransparentPixels)
        }
        val subject = placed("subject", Color.RED)
        val pose = placed("closed", Color.GREEN)
        val original = imported.asset.original
        val sourcePin = imported.session.project.referenceVersions.single()
        val bounds = VideoRect("scene", 20.0, 20.0, 64.0, 64.0)
        val viewport = VideoRect("scene", 0.0, 0.0, 1920.0, 1080.0)
        val capability = VideoMotionCapability("blink-cap", VideoMotionTargetType.POSE, "closed", VideoMotionControl.POSE_BLEND,
            VideoMotionUnit.RATIO, 0.0, 1.0, 0.0)
        val scene = VideoPreparedScene(id = VideoVersionedId("prepared", 1),
            source = VideoPreparedSceneSource(references = listOf(VideoPreparedReferencePin(sourcePin.id, sourcePin.artifact, original))),
            coordinateSpaces = listOf(VideoCoordinateSpace("scene", 1920, 1080)),
            layers = listOf(VideoPreparedLayer("finished", VideoLayerKind.FINISHED_SCENE, original, viewport),
                VideoPreparedLayer("clean", VideoLayerKind.ENVIRONMENT, original, viewport),
                VideoPreparedLayer("subject", VideoLayerKind.SUBJECT, subject, bounds, alpha = VideoMeasuredAlpha(2048, 0, 2048))),
            poses = listOf(VideoPreparedPose("closed", "subject", pose, bounds, alpha = VideoMeasuredAlpha(2048, 0, 2048))),
            motionCapabilities = listOf(capability), dependencies = listOf(VideoPreparedDependencyPin("fixture", "1", "a".repeat(64))),
            createdAt = Instant.now(CLOCK).toString())
        val project = VideoPreparedSceneStore(storage).save(projectRoot, imported.session.project.revision, scene)
        val look = VideoSceneLook(sourcePin.id, sourcePin.artifact, original, VideoAssetIdentityReview.UNREVIEWED,
            VideoSceneAppearancePolicy.PRESERVE_AS_DRAWN)
        val primary = "  Preserve supplied art.\r\nBlink gently.  "
        val motion = VideoPreparedSceneMotion(VideoScenePreparationStatus.REVIEW_REQUIRED, VideoSceneMotionMode.CONTROLLED_REGIONAL_MOTION,
            look, scene, primary, primary, emptyList(), listOf(VideoPreparedMotionInput("blink", VideoSceneMotionIntent.BLINK,
                capability, 0.0, 1.0, 0.7, emptyList(), "b".repeat(64))), emptyList(), emptyList(), "c".repeat(64))
        return Fixture(projectRoot, project, VideoAssemblyPlanningRequest(id = VideoVersionedId("plan", 1), projectId = project.id,
            preparedSceneId = scene.id, preparedSceneArtifact = project.preparedSceneVersions.single().artifact,
            finishedReferenceId = sourcePin.id, finishedReferenceArtifact = original.artifact, primaryText = primary,
            actionText = "  one blink\n", seed = 73, supportFramesPerSide = 8, preparedMotion = motion,
            scheduledActions = listOf(VideoAssemblyScheduledAction("blink-once", VideoSceneMotionIntent.BLINK, "blink", 280, 285, 0.7)),
            trajectoryDependencies = listOf(VideoAssemblyDependency("trajectory.camera", "d".repeat(64))),
            compilerVersion = "test-compiler-v1", rendererVersion = "test-renderer-v1", runtimeVersion = "test-runtime-v1"))
    }

    private fun image(path: Path, width: Int, height: Int, alpha: Boolean, color: Color): Path {
        Files.createDirectories(path.parent)
        val image = BufferedImage(width, height, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, if (alpha) width / 2 else width, height)
        } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun files(directory: Path): Map<String, String> = Files.walk(directory).use { paths ->
        paths.filter(Files::isRegularFile).toList().associate { directory.relativize(it).toString() to sha(it) }
    }
    private fun sha(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC)
    }
}
