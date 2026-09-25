package app.melotrail.video

import app.melotrail.video.adapter.VideoAtomicWriteObserver
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsafeVideoProjectLocationException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.application.VideoProjectProblemCode
import app.melotrail.video.application.VideoProjectSaveException
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoExportRecord
import app.melotrail.video.domain.VideoLookRecord
import app.melotrail.video.domain.VideoPreparedSceneRecord
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoReferenceRecord
import app.melotrail.video.domain.VideoTakeRecord
import app.melotrail.video.domain.VideoTakeMeasurementRecord
import app.melotrail.video.domain.VideoTakeRationalRecord
import app.melotrail.video.domain.VideoTakeProvenanceRecord
import app.melotrail.video.domain.VideoGenerationDependencyPin
import app.melotrail.video.domain.VideoVersionedId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoProjectStoreTest {
    @TempDir lateinit var root: Path

    @Test
    fun `versioned records reopen independently and selections change without rewriting history`() {
        val videoRoot = root.resolve("video-project")
        val firstLifecycle = lifecycle(store())
        val created = assertIs<VideoProjectLifecycleResult.Opened>(
            firstLifecycle.create(CreateVideoProject(videoRoot, "Independent video", "video-1", "test-1")),
        ).session
        val populated = populatedProject(created.project, videoRoot)

        val saved = assertIs<VideoProjectLifecycleResult.Opened>(firstLifecycle.save(created, populated)).session
        val reopened = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle(store()).open(videoRoot)).session

        assertEquals(saved, reopened)
        assertEquals(1, reopened.project.referenceVersions.size)
        assertEquals(1, reopened.project.lookVersions.size)
        assertEquals(1, reopened.project.takeVersions.size)
        assertEquals(1, reopened.project.exportRecords.size)
        val firstTakeBytes = Files.readAllBytes(videoRoot.resolve("takes/take-1.mp4"))

        val secondTakeId = VideoVersionedId("take-1", 2)
        val secondTake = measuredTake(reopened.project, videoRoot, secondTakeId,
            artifact(videoRoot, "takes/take-1-v2.mp4", "take-two"), reopened.project.selectedLookId,
            // Completion may be recorded later while retaining an earlier provider timestamp.
            "2026-09-12T23:59:00Z")
        val reselection = reopened.project.copy(
            takeVersions = reopened.project.takeVersions + secondTake,
            selectedTakeIds = listOf(secondTakeId),
            revision = 2L,
        )
        val reselectionResult = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle(store()).save(reopened, reselection),
        ).session.project

        assertEquals(listOf(secondTakeId), reselectionResult.selectedTakeIds)
        assertEquals(populated.takeVersions, reselectionResult.takeVersions.take(populated.takeVersions.size))
        assertContentEquals(firstTakeBytes, Files.readAllBytes(videoRoot.resolve("takes/take-1.mp4")))
        assertEquals(reselectionResult, store().open(videoRoot))
    }

    @Test
    fun `stale sessions from independent stores cannot both save the same revision`() {
        val videoRoot = root.resolve("video-project")
        val firstStore = store()
        val secondStore = store()
        val firstLifecycle = lifecycle(firstStore)
        val created = assertIs<VideoProjectLifecycleResult.Opened>(
            firstLifecycle.create(CreateVideoProject(videoRoot, "Initial", "video-1")),
        ).session
        val stale = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle(secondStore).open(videoRoot)).session

        val winner = created.project.copy(name = "Winner", revision = 1L)
        firstStore.save(videoRoot, expectedRevision = 0L, winner)

        val staleResult = assertIs<VideoProjectLifecycleResult.Rejected>(
            lifecycle(secondStore).save(stale, stale.project.copy(name = "Stale", revision = 1L)),
        )
        assertEquals(VideoProjectProblemCode.CONCURRENT_WRITE, staleResult.problem.code)
        assertEquals("Winner", secondStore.open(videoRoot).name)
        assertEquals(1L, secondStore.open(videoRoot).revision)
    }

    @Test
    fun `ordinary saves cannot edit or remove immutable record history`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val created = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle(projectStore).create(CreateVideoProject(videoRoot, "Video", "video-1")),
        ).session
        val populated = populatedProject(created.project, videoRoot)
        projectStore.save(videoRoot, 0L, populated)
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val changedReference = populated.referenceVersions.single().copy(createdAt = "2026-09-13T00:09:00Z")

        val current = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle(projectStore).open(videoRoot)).session
        val result = assertIs<VideoProjectLifecycleResult.Rejected>(
            lifecycle(projectStore).save(
                current,
                populated.copy(referenceVersions = listOf(changedReference), revision = 2L),
            ),
        )

        assertEquals(VideoProjectProblemCode.IMMUTABLE_HISTORY, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals(populated, projectStore.open(videoRoot))
    }

    @Test
    fun `measured take provenance survives reopen and direct saves cannot revise either measurement`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val created = projectStore.create(videoRoot, emptyProject())
        val populated = populatedProject(created, videoRoot)
        val first = populated.takeVersions.single()
        val ratio = VideoTakeRationalRecord(1, 1)
        val source = VideoTakeMeasurementRecord("a".repeat(64), 100, "h264", 100, 60,
            ratio, VideoTakeRationalRecord(30, 1), 30, VideoTakeRationalRecord(1, 30), 0, 30, 1, 1, 0)
        val published = source.copy(sha256 = first.artifact.sha256, bytes = Files.size(videoRoot.resolve(first.artifact.relativePath)), audioStreamCount = 0)
        val provenance = requireNotNull(first.provenance)
        val measured = first.copy(sourceMeasurement = source, publishedMeasurement = published,
            conversion = "AUDIO_REMUX")
        val saved = projectStore.save(videoRoot, 0L, populated.copy(takeVersions = listOf(measured)))
        assertEquals(measured, projectStore.open(videoRoot).takeVersions.single())
        val document = videoRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)
        val takeBytes = Files.readAllBytes(videoRoot.resolve(first.artifact.relativePath))
        listOf(
            measured.copy(sourceMeasurement = source.copy(bytes = 101)),
            measured.copy(provenance = provenance.copy(requestId = "another-request")),
            measured.copy(conversion = "AUDIO_REMUX_START_NORMALIZED"),
        ).forEach { edited ->
            assertFailsWith<IllegalArgumentException> {
                projectStore.save(videoRoot, 1L, saved.copy(takeVersions = listOf(edited), revision = 2L))
            }
            assertContentEquals(before, Files.readAllBytes(document))
            assertContentEquals(takeBytes, Files.readAllBytes(videoRoot.resolve(first.artifact.relativePath)))
        }
        assertEquals(saved, projectStore.open(videoRoot))
        assertFailsWith<IllegalArgumentException> {
            measured.copy(publishedMeasurement = published.copy(decodedFrameCount = 29))
        }
        assertFailsWith<IllegalArgumentException> {
            populated.copy(takeVersions = listOf(measured.copy(publishedMeasurement = published.copy(sha256 = "f".repeat(64)))))
        }
        val badBytes = measured.copy(publishedMeasurement = published.copy(bytes = published.bytes + 1))
        assertFailsWith<IllegalArgumentException> {
            projectStore.save(videoRoot, 1L, saved.copy(takeVersions = saved.takeVersions + badBytes.copy(
                id = VideoVersionedId("take-2", 1),
                artifact = artifact(videoRoot, "takes/take-2.mp4", "take-two"),
                publishedMeasurement = published.copy(bytes = 999, sha256 = sha256(videoRoot.resolve("takes/take-2.mp4"))),
            ), revision = 2L))
        }
        assertContentEquals(before, Files.readAllBytes(document))
    }

    @Test
    fun `direct save cannot append a take without complete measurements and provenance`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = projectStore.create(videoRoot, emptyProject())
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val takeArtifact = artifact(videoRoot, "takes/incomplete.mp4", "take")
        assertFailsWith<IllegalArgumentException> {
            projectStore.save(videoRoot, 0L, original.copy(
                takeVersions = listOf(VideoTakeRecord(VideoVersionedId("incomplete", 1),
                    takeArtifact, null, "2026-09-13T00:03:00Z")), revision = 1L))
        }
        assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals(original, projectStore.open(videoRoot))
    }

    @Test
    fun `direct save rejects take source IDs from another prepared scene`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = projectStore.create(videoRoot, emptyProject())
        val referenceOne = VideoVersionedId("reference-one", 1)
        val referenceTwo = VideoVersionedId("reference-two", 1)
        val lookOne = VideoVersionedId("look-one", 1)
        val lookTwo = VideoVersionedId("look-two", 1)
        val sceneId = VideoVersionedId("scene-one", 1)
        val scene = VideoPreparedSceneRecord(sceneId,
            artifact(videoRoot, "prepared-scenes/scene-one/v1/scene.json", "scene"),
            lookOne, listOf(referenceOne), emptyList(), "2026-09-13T00:02:00Z")
        val sources = original.copy(
            referenceVersions = listOf(referenceOne, referenceTwo).map { id ->
                VideoReferenceRecord(id, artifact(videoRoot, "references/${id.id}.png", id.id), "2026-09-13T00:01:00Z")
            },
            lookVersions = listOf(lookOne, lookTwo).map { id ->
                VideoLookRecord(id, artifact(videoRoot, "looks/${id.id}.png", id.id),
                    listOf(if (id == lookOne) referenceOne else referenceTwo), "2026-09-13T00:01:00Z")
            },
            preparedSceneVersions = listOf(scene), revision = 1L)
        projectStore.save(videoRoot, 0L, sources)
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val take = measuredTake(sources, videoRoot, VideoVersionedId("take", 1),
            artifact(videoRoot, "takes/take.mp4", "take"), lookOne, "2026-09-13T00:03:00Z", referenceOne)
        val valid = take.copy(provenance = take.provenance!!.copy(
            backendId = "controlled-local", preparedSceneId = sceneId))
        // Both alternate IDs exist in the project, but neither belongs to this scene.
        listOf(
            valid.copy(provenance = valid.provenance!!.copy(finishedReferenceId = referenceTwo)),
            valid.copy(lookId = lookTwo, provenance = valid.provenance!!.copy(persistedLookId = lookTwo)),
        ).forEach { mismatched ->
            assertFailsWith<IllegalArgumentException> {
                projectStore.save(videoRoot, 1L, sources.copy(takeVersions = listOf(mismatched), revision = 2L))
            }
            assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        }
        val saved = projectStore.save(videoRoot, 1L, sources.copy(takeVersions = listOf(valid), revision = 2L))
        assertEquals(valid, projectStore.open(videoRoot).takeVersions.single())
        assertTrue(saved.selectedTakeIds.isEmpty())
    }

    @Test
    fun `identity publication converges across stores and never links source bytes`() {
        val videoRoot = root.resolve("video-project")
        val copying = java.util.concurrent.CountDownLatch(1)
        val resumeCopy = java.util.concurrent.CountDownLatch(1)
        val pauseCopy = java.util.concurrent.atomic.AtomicBoolean(false)
        val checking = java.util.concurrent.CountDownLatch(1)
        val resumeCheck = java.util.concurrent.CountDownLatch(1)
        val pauseCheck = java.util.concurrent.atomic.AtomicBoolean(false)
        val first = VideoProjectStore(listOf(root.resolve("midi-projects")), takeCopyObserver = {
            if (pauseCopy.get()) {
                copying.countDown()
                check(resumeCopy.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
        })
        val second = store()
        first.create(videoRoot, emptyProject())
        val source = Files.writeString(root.resolve("output.mp4"), "verified output")
        val digest = sha256(source)
        val target = VideoArtifact("takes/import-identity/v1/preview.mp4", digest)
        val measurement = VideoTakeMeasurementRecord(digest, Files.size(source), "h264", 100, 60,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(30, 1), 30,
            VideoTakeRationalRecord(1, 30), 0, 30, 1, 0, 0)
        fun take(id: String) = VideoTakeRecord(VideoVersionedId(id, 1), target, null,
            "2026-09-13T00:03:00Z", measurement, measurement, "NONE",
            VideoTakeProvenanceRecord("video-1", "request", "attempt", "output", "comfyui-local",
                "a".repeat(64), "b".repeat(64), null, null, null,
                listOf(VideoGenerationDependencyPin("source", "c".repeat(64)))))
        val cancel = VideoMediaProcessCancellation()
        val stage = first.stageTake(videoRoot, source, digest, cancel)
        try {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            val gate = java.util.concurrent.CountDownLatch(1)
            val (saved, published) = try {
                val one = pool.submit<Pair<VideoProject, VideoTakeRecord>> {
                    gate.await()
                    first.publishImportedTake(videoRoot, 0, take("first"), stage, cancel) { }
                }
                val two = pool.submit<Pair<VideoProject, VideoTakeRecord>> {
                    gate.await()
                    second.publishImportedTake(videoRoot, 0, take("first"), stage, cancel) { }
                }
                gate.countDown()
                fun resolved(future: java.util.concurrent.Future<Pair<VideoProject, VideoTakeRecord>>):
                    Pair<VideoProject, VideoTakeRecord> = try {
                    future.get(10, java.util.concurrent.TimeUnit.SECONDS)
                } catch (error: java.util.concurrent.ExecutionException) {
                    assertIs<app.melotrail.video.application.VideoProjectConcurrencyException>(error.cause)
                    // A losing writer refreshes before resolving the winning take.
                    second.publishImportedTake(videoRoot, 1, take("first"), stage, cancel) { }
                }
                val result = resolved(one)
                assertEquals(result, resolved(two))
                result
            } finally { pool.shutdownNow() }
            assertEquals(1, saved.takeVersions.size)
            assertEquals(take("first"), published)
            val publishedPath = first.resolveArtifact(videoRoot, target)
            Files.writeString(source, "changed source")
            assertEquals("verified output", Files.readString(publishedPath))
            val (reopened, reused) = second.publishImportedTake(videoRoot, 0, take("loser"), stage, cancel) { }
            assertEquals(saved, reopened)
            assertEquals(published, reused)
            assertEquals(1, second.open(videoRoot).takeVersions.size)
            assertFailsWith<IllegalArgumentException> {
                second.publishImportedTake(videoRoot, 1, take("collision").copy(
                    provenance = take("collision").provenance!!.copy(sourceIdentity = "d".repeat(64))), stage, cancel) { }
            }
            assertEquals(saved, second.open(videoRoot))
            // Hold the independent copy. Another store must still acquire the project
            // lock and commit a revision before this publication reaches its CAS.
            val different = take("other").copy(
                artifact = VideoArtifact("takes/another-import/v1/preview.mp4", digest),
                provenance = take("other").provenance!!.copy(requestId = "different"))
            pauseCopy.set(true)
            val writers = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val pending = writers.submit<Pair<VideoProject, VideoTakeRecord>> {
                    first.publishImportedTake(videoRoot, saved.revision, different, stage, cancel) { }
                }
                assertTrue(copying.await(10, java.util.concurrent.TimeUnit.SECONDS))
                val updated = writers.submit<VideoProject> {
                    second.save(videoRoot, saved.revision, saved.copy(name = "Unrelated edit", revision = saved.revision + 1))
                }.get(5, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals("Unrelated edit", updated.name)
                resumeCopy.countDown()
                val conflict = assertFailsWith<java.util.concurrent.ExecutionException> {
                    pending.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                assertIs<app.melotrail.video.application.VideoProjectConcurrencyException>(conflict.cause)
                assertEquals(updated, second.open(videoRoot))
                assertFalse(Files.exists(videoRoot.resolve(different.artifact.relativePath)))
                // A final source/pin digest check must likewise not hold the document
                // lock. A concurrent save succeeds, and the publisher loses its CAS.
                pauseCopy.set(false)
                pauseCheck.set(true)
                val finalCheck = writers.submit<Pair<VideoProject, VideoTakeRecord>> {
                    var checks = 0
                    first.publishImportedTake(videoRoot, updated.revision, different, stage, cancel) {
                        if (++checks == 2 && pauseCheck.get()) {
                            checking.countDown()
                            check(resumeCheck.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        }
                    }
                }
                assertTrue(checking.await(10, java.util.concurrent.TimeUnit.SECONDS))
                val next = second.save(videoRoot, updated.revision,
                    updated.copy(name = "Another edit", revision = updated.revision + 1))
                resumeCheck.countDown()
                val losingCheck = assertFailsWith<java.util.concurrent.ExecutionException> {
                    finalCheck.get(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                assertIs<app.melotrail.video.application.VideoProjectConcurrencyException>(losingCheck.cause)
                assertEquals(next, second.open(videoRoot))
                assertFalse(Files.exists(videoRoot.resolve(different.artifact.relativePath)))
            } finally { resumeCopy.countDown(); resumeCheck.countDown(); writers.shutdownNow() }
        } finally { Files.deleteIfExists(stage) }
    }

    @Test
    fun `cancelled and failed publication leave project intact and exact orphan is reusable`() {
        val videoRoot = root.resolve("video-project")
        var fail = false
        val guarded = VideoProjectStore(listOf(root.resolve("midi-projects")),
            VideoAtomicWriteObserver { _, _ -> if (fail) error("simulated document failure") })
        val original = guarded.create(videoRoot, emptyProject())
        val document = videoRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)
        val source = Files.writeString(root.resolve("output.mp4"), "output")
        val digest = sha256(source)
        val artifact = VideoArtifact("takes/import-once/v1/preview.mp4", digest)
        val measurement = VideoTakeMeasurementRecord(digest, Files.size(source), "h264", 100, 60,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(30, 1), 30,
            VideoTakeRationalRecord(1, 30), 0, 30, 1, 0, 0)
        val take = VideoTakeRecord(VideoVersionedId("take", 1), artifact, null,
            "2026-09-13T00:03:00Z", measurement, measurement, "NONE",
            VideoTakeProvenanceRecord("video-1", "request", "attempt", "output", "comfyui-local",
                "a".repeat(64), "b".repeat(64), null, null, null,
                listOf(VideoGenerationDependencyPin("source", "c".repeat(64)))))
        val cancellation = VideoMediaProcessCancellation()
        val stage = guarded.stageTake(videoRoot, source, digest, cancellation)
        try {
            val cancelled = VideoMediaProcessCancellation().also { it.cancel() }
            assertFailsWith<Exception> { guarded.publishImportedTake(videoRoot, 0, take, stage, cancelled) { } }
            assertFalse(Files.exists(videoRoot.resolve(artifact.relativePath)))
            assertContentEquals(before, Files.readAllBytes(document))
            Files.writeString(stage, "tampered")
            assertFailsWith<IllegalArgumentException> {
                guarded.publishImportedTake(videoRoot, 0, take, stage, cancellation) { }
            }
            assertFalse(Files.exists(videoRoot.resolve(artifact.relativePath)))
            Files.writeString(stage, "output")
            fail = true
            assertFailsWith<VideoProjectSaveException> {
                guarded.publishImportedTake(videoRoot, 0, take, stage, cancellation) { }
            }
            assertEquals(original, guarded.open(videoRoot))
            assertContentEquals(before, Files.readAllBytes(document))
            assertEquals("output", Files.readString(videoRoot.resolve(artifact.relativePath)))
            fail = false
            val (saved, _) = guarded.publishImportedTake(videoRoot, 0, take, stage, cancellation) { }
            assertEquals(listOf(take), saved.takeVersions)
            assertEquals(saved, guarded.open(videoRoot))
            val after = Files.readAllBytes(document)
            val late = VideoMediaProcessCancellation()
            guarded.publishImportedTake(videoRoot, 0, take, stage, late) { }
            late.cancel() // A cancellation after the committed reference cannot remove it.
            assertContentEquals(after, Files.readAllBytes(document))
            assertFailsWith<app.melotrail.video.application.VideoProjectConcurrencyException> {
                guarded.publishImportedTake(videoRoot, 0, take.copy(id = VideoVersionedId("other", 1),
                    provenance = take.provenance!!.copy(requestId = "other")), stage, cancellation) { }
            }
            assertContentEquals(after, Files.readAllBytes(document))
        } finally { Files.deleteIfExists(stage) }
    }

    @Test
    fun `prepared scene records are append-only and retain every consumed artifact pin`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = projectStore.create(videoRoot, emptyProject())
        val populated = populatedProject(original, videoRoot)
        projectStore.save(videoRoot, 0L, populated)
        val reference = populated.referenceVersions.single()
        val descriptor = artifact(videoRoot, "prepared-scenes/scene/v1/scene.json", "prepared scene descriptor")
        val layer = artifact(videoRoot, "prepared-assets/layer.png", "prepared layer")
        val scene = VideoPreparedSceneRecord(
            id = VideoVersionedId("scene", 1),
            artifact = descriptor,
            sourceLookId = populated.selectedLookId,
            sourceReferenceIds = listOf(reference.id),
            consumedArtifacts = listOf(reference.artifact, populated.lookVersions.single().artifact, layer),
            createdAt = "2026-09-13T00:05:00Z",
        )
        val withScene = populated.copy(preparedSceneVersions = listOf(scene), revision = 2L)
        projectStore.save(videoRoot, 1L, withScene)
        val reopened = assertIs<VideoProjectLifecycleResult.Opened>(lifecycle(projectStore).open(videoRoot)).session
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))

        val rejected = assertIs<VideoProjectLifecycleResult.Rejected>(
            lifecycle(projectStore).save(
                reopened,
                withScene.copy(
                    preparedSceneVersions = listOf(scene.copy(createdAt = "2026-09-13T00:06:00Z")),
                    revision = 3L,
                ),
            ),
        )

        assertEquals(VideoProjectProblemCode.IMMUTABLE_HISTORY, rejected.problem.code)
        assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals(listOf(reference.artifact, populated.lookVersions.single().artifact, layer), projectStore.open(videoRoot).preparedSceneVersions.single().consumedArtifacts)
    }

    @Test
    fun `corrupt staged save preserves the last known-good project`() {
        val videoRoot = root.resolve("video-project")
        var corrupt = false
        val projectStore = VideoProjectStore(
            protectedMidiRoots = listOf(root.resolve("midi-projects")),
            atomicWriteObserver = VideoAtomicWriteObserver { temporary, _ ->
                if (corrupt) Files.writeString(temporary, "{\"partial\":")
            },
        )
        val original = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle(projectStore).create(CreateVideoProject(videoRoot, "Original", "video-1")),
        ).session.project
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))
        corrupt = true

        val failure = assertFailsWith<VideoProjectSaveException> {
            projectStore.save(videoRoot, 0L, original.copy(name = "Replacement", revision = 1L))
        }

        assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals(original, projectStore.open(videoRoot))
        assertTrue(failure.recoveryEvidence?.let { Files.isRegularFile(it) } == true)
    }

    @Test
    fun `unsupported and malformed documents are rejected without rewriting their bytes`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle(projectStore).create(CreateVideoProject(videoRoot, "Original", "video-1")),
        ).session.project
        val projectFile = videoRoot.resolve(VideoProjectStore.PROJECT_FILE)
        listOf(1, 2, 4).forEach { unsupportedVersion ->
            val unsupported = """{"schema":"melotrail-video-project","version":$unsupportedVersion,"project":{}}"""
            Files.writeString(projectFile, unsupported)
            val beforeUnsupported = Files.readAllBytes(projectFile)
            assertFailsWith<UnsupportedVideoProjectException> {
                projectStore.save(videoRoot, 0L, original.copy(revision = 1L))
            }
            assertContentEquals(beforeUnsupported, Files.readAllBytes(projectFile))
        }

        Files.writeString(projectFile, "{\"schema\":")
        val beforeMalformed = Files.readAllBytes(projectFile)
        val result = assertIs<VideoProjectLifecycleResult.Rejected>(lifecycle(projectStore).open(videoRoot))
        assertEquals(VideoProjectProblemCode.INVALID_PROJECT, result.problem.code)
        assertContentEquals(beforeMalformed, Files.readAllBytes(projectFile))
    }

    @Test
    fun `missing protected roots marker files and symlinked ancestors reject video storage`() {
        val missingMidiRoot = root.resolve("missing-midi-project")
        val guarded = VideoProjectStore(listOf(missingMidiRoot))

        assertFailsWith<UnsafeVideoProjectLocationException> {
            guarded.create(missingMidiRoot.resolve("exports/video"), emptyProject())
        }
        assertFailsWith<UnsafeVideoProjectLocationException> {
            guarded.create(root, emptyProject())
        }
        assertFalse(Files.exists(missingMidiRoot))

        val markedMidi = root.resolve("marked-midi")
        Files.createDirectories(markedMidi)
        val marker = markedMidi.resolve(VideoProjectStore.MIDI_PROJECT_FILE)
        Files.writeString(marker, "corrupt but authoritative")
        val markerBytes = Files.readAllBytes(marker)
        val markerGuard = VideoProjectStore(listOf(root.resolve("another-midi-root")))
        assertFailsWith<UnsafeVideoProjectLocationException> {
            markerGuard.create(markedMidi.resolve("exports/video"), emptyProject())
        }
        assertContentEquals(markerBytes, Files.readAllBytes(marker))

        val realMidi = root.resolve("real-midi")
        Files.createDirectories(realMidi)
        val link = root.resolve("linked-parent")
        Files.createSymbolicLink(link, realMidi)
        val symlinkGuard = VideoProjectStore(listOf(realMidi))
        assertFailsWith<UnsafeVideoProjectLocationException> {
            symlinkGuard.create(link.resolve("future-video"), emptyProject())
        }
        assertFalse(Files.exists(realMidi.resolve("future-video")))
    }

    @Test
    fun `raw protected roots resolve symlink parent traversal for existing and missing exports`() {
        listOf(false, true).forEach { exportsExist ->
            val fixture = root.resolve("protected-$exportsExist")
            val actual = Files.createDirectories(fixture.resolve("actual/child")).parent
            val alias = Files.createDirectories(fixture.resolve("alias"))
            Files.createSymbolicLink(alias.resolve("link"), actual.resolve("child"))
            val exports = actual.resolve("midi-exports")
            val sentinel = if (exportsExist) {
                Files.createDirectories(exports).resolve("complete-song.mid")
            } else {
                actual.resolve("original.mid")
            }
            Files.writeString(sentinel, "protected MIDI bytes")
            val sentinelBytes = Files.readAllBytes(sentinel)
            val videoRoot = exports.resolve("video")
            val original = emptyProject()
            val originalBytes = if (exportsExist) {
                store().create(videoRoot, original)
                Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))
            } else {
                null
            }
            // There is deliberately no project.json marker: the supplied root must protect exports.
            val guarded = VideoProjectStore(listOf(alias.resolve("link/../midi-exports")))

            assertFailsWith<UnsafeVideoProjectLocationException> {
                guarded.create(exports.resolve("new-video"), original)
            }
            assertFailsWith<UnsafeVideoProjectLocationException> {
                guarded.save(videoRoot, 0L, original.copy(name = "Replacement", revision = 1L))
            }
            val directGuard = VideoProjectStore(listOf(exports))
            assertFailsWith<UnsafeVideoProjectLocationException> {
                directGuard.create(alias.resolve("link/../midi-exports/new-video"), original)
            }
            assertFailsWith<UnsafeVideoProjectLocationException> {
                directGuard.save(alias.resolve("link/../midi-exports/video"), 0L, original.copy(revision = 1L))
            }

            assertContentEquals(sentinelBytes, Files.readAllBytes(sentinel))
            assertFalse(Files.exists(exports.resolve("new-video")))
            assertFalse(Files.exists(alias.resolve("midi-exports")))
            if (originalBytes != null) {
                assertContentEquals(originalBytes, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
                assertEquals(original, store().open(videoRoot))
            } else {
                assertFalse(Files.exists(exports))
            }
        }
    }

    @Test
    fun `candidate and session paths retain symlink parent semantics through create save and reopen`() {
        val actual = Files.createDirectories(root.resolve("actual/child")).parent
        val alias = Files.createDirectories(root.resolve("alias"))
        Files.createSymbolicLink(alias.resolve("link"), actual.resolve("child"))
        val requestedRoot = alias.resolve("link/../missing-parent/video")
        val actualRoot = actual.resolve("missing-parent/video")
        val projectStore = VideoProjectStore(listOf(alias.resolve("link/../missing-exports")))
        val projectLifecycle = lifecycle(projectStore)
        val created = assertIs<VideoProjectLifecycleResult.Opened>(
            projectLifecycle.create(CreateVideoProject(requestedRoot, "Original", "video-1")),
        ).session
        val saved = assertIs<VideoProjectLifecycleResult.Opened>(
            projectLifecycle.save(created, created.project.copy(name = "Saved", revision = 1L)),
        ).session

        assertEquals(saved.project, projectStore.open(actualRoot))
        assertEquals(saved, assertIs<VideoProjectLifecycleResult.Opened>(projectLifecycle.open(requestedRoot)).session)
        assertFalse(Files.exists(alias.resolve("missing-parent")))
        assertFalse(Files.exists(actual.resolve("missing-exports")))

        val rootLink = root.resolve("video-alias")
        Files.createSymbolicLink(rootLink, actualRoot)
        assertEquals(saved.project, projectStore.open(rootLink))
    }

    @Test
    fun `parent traversal through a missing prefix fails closed without creating folders`() {
        val unresolved = root.resolve("missing/../video")
        assertFailsWith<UnsafeVideoProjectLocationException> { store().create(unresolved, emptyProject()) }
        val protected = VideoProjectStore(listOf(root.resolve("missing/../midi-exports")))
        assertFailsWith<UnsafeVideoProjectLocationException> { protected.create(root.resolve("video"), emptyProject()) }
        assertFalse(Files.exists(root.resolve("missing")))
        assertFalse(Files.exists(root.resolve("video")))
    }

    @Test
    fun `a document self reference is rejected before save and the original project remains reopenable`() {
        val videoRoot = root.resolve("video-project")
        var publications = 0
        val projectStore = VideoProjectStore(
            listOf(root.resolve("midi-projects")),
            VideoAtomicWriteObserver { _, _ -> publications++ },
        )
        val original = projectStore.create(videoRoot, emptyProject())
        val document = videoRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)

        assertFailsWith<IllegalArgumentException> {
            projectStore.save(
                videoRoot,
                0L,
                original.copy(
                    referenceVersions = listOf(
                        VideoReferenceRecord(
                            VideoVersionedId("metadata-reference", 1),
                            VideoArtifact(VideoProjectStore.PROJECT_FILE, sha256(document)),
                            "2026-09-13T00:01:00Z",
                        ),
                    ),
                    revision = 1L,
                ),
            )
        }

        assertEquals(1, publications)
        assertContentEquals(before, Files.readAllBytes(document))
        assertEquals(original, projectStore.open(videoRoot))
    }

    @Test
    fun `control paths and namespaces are rejected during construction and document decoding`() {
        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = projectStore.create(videoRoot, emptyProject())
        val populated = populatedProject(original, videoRoot)
        projectStore.save(videoRoot, 0L, populated)
        val document = videoRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val validDocument = Files.readString(document)
        val reservedPaths = listOf(
            "video-project.json",
            "VIDEO-PROJECT.JSON",
            "video-project.json. ",
            "video-project.json/child.png",
            ".video-project.lock",
            ".VIDEO-PROJECT.LOCK",
            ".video-project.lock/child.png",
            ".video-project.json.save-123.tmp",
            ".video-project.json.recovery-123.json",
            ".VIDEO-PROJECT.JSON.RECOVERY-123.JSON/child.png",
            "references/.video-project.json.save-123.tmp",
        )
        reservedPaths.forEach { reserved ->
            assertFailsWith<IllegalArgumentException>(reserved) { VideoArtifact(reserved, "a".repeat(64)) }
            val invalidDocument = validDocument.replace("references/reference-1.png", reserved)
            Files.writeString(document, invalidDocument)
            val before = Files.readAllBytes(document)

            val rejected = assertIs<VideoProjectLifecycleResult.Rejected>(lifecycle(projectStore).open(videoRoot))
            assertEquals(VideoProjectProblemCode.INVALID_PROJECT, rejected.problem.code, reserved)
            assertTrue(rejected.problem.message.contains("project document, lock, staging or recovery paths"), reserved)
            assertFailsWith<InvalidVideoProjectException>(reserved) {
                projectStore.save(videoRoot, 1L, populated.copy(revision = 2L))
            }
            assertContentEquals(before, Files.readAllBytes(document))
        }
        Files.writeString(document, validDocument)
        assertEquals(populated, projectStore.open(videoRoot))
    }

    @Test
    fun `create publication never replaces a target that appears after the absence check`() {
        val videoRoot = root.resolve("video-project")
        val sentinel = "another writer's project".toByteArray()
        var stagedBytes: ByteArray? = null
        val projectStore = VideoProjectStore(
            listOf(root.resolve("midi-projects")),
            VideoAtomicWriteObserver { temporary, target ->
                stagedBytes = Files.readAllBytes(temporary)
                Files.write(target, sentinel)
            },
        )

        val rejected = assertIs<VideoProjectLifecycleResult.Rejected>(
            lifecycle(projectStore).create(CreateVideoProject(videoRoot, "New", "video-1")),
        )

        assertEquals(VideoProjectProblemCode.PROJECT_ALREADY_EXISTS, rejected.problem.code)
        assertContentEquals(sentinel, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        val recovery = Files.list(videoRoot).use { files ->
            files.filter { it.fileName.toString().startsWith(".video-project.json.recovery-") }.toList().single()
        }
        assertContentEquals(requireNotNull(stagedBytes), Files.readAllBytes(recovery))
    }

    @Test
    fun `artifact traversal and symlink escape cannot replace current work`() {
        assertFailsWith<IllegalArgumentException> { VideoArtifact("../outside.mp4", "a".repeat(64)) }

        val videoRoot = root.resolve("video-project")
        val projectStore = store()
        val original = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle(projectStore).create(CreateVideoProject(videoRoot, "Original", "video-1")),
        ).session.project
        val outside = root.resolve("outside")
        Files.createDirectories(outside)
        Files.writeString(outside.resolve("reference.png"), "outside-reference")
        Files.createSymbolicLink(videoRoot.resolve("references"), outside)
        val escaped = original.copy(
            referenceVersions = listOf(
                VideoReferenceRecord(
                    VideoVersionedId("reference-1", 1),
                    VideoArtifact("references/reference.png", sha256(outside.resolve("reference.png"))),
                    "2026-09-13T00:01:00Z",
                ),
            ),
            revision = 1L,
        )
        val before = Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE))

        val result = assertIs<VideoProjectLifecycleResult.Rejected>(
            lifecycle(projectStore).save(
                assertIs<VideoProjectLifecycleResult.Opened>(lifecycle(projectStore).open(videoRoot)).session,
                escaped,
            ),
        )

        assertEquals(VideoProjectProblemCode.INVALID_PROJECT, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(videoRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertEquals("outside-reference", Files.readString(outside.resolve("reference.png")))
    }

    private fun store() = VideoProjectStore(listOf(root.resolve("midi-projects"), root.resolve("midi-exports")))

    private fun lifecycle(projectStore: VideoProjectStore) = VideoProjectLifecycle(
        projects = projectStore,
        clock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-video-project" },
    )

    private fun populatedProject(project: VideoProject, videoRoot: Path): VideoProject {
        val referenceId = VideoVersionedId("reference-1", 1)
        val lookId = VideoVersionedId("look-1", 1)
        val takeId = VideoVersionedId("take-1", 1)
        return project.copy(
            referenceVersions = listOf(
                VideoReferenceRecord(
                    referenceId,
                    artifact(videoRoot, "references/reference-1.png", "reference"),
                    "2026-09-13T00:01:00Z",
                ),
            ),
            lookVersions = listOf(
                VideoLookRecord(
                    lookId,
                    artifact(videoRoot, "looks/look-1.png", "look"),
                    listOf(referenceId),
                    "2026-09-13T00:02:00Z",
                ),
            ),
            takeVersions = listOf(
                measuredTake(project, videoRoot, takeId,
                    artifact(videoRoot, "takes/take-1.mp4", "take"), lookId,
                    "2026-09-13T00:03:00Z", referenceId),
            ),
            selectedReferenceIds = listOf(referenceId),
            selectedLookId = lookId,
            selectedTakeIds = listOf(takeId),
            exportRecords = listOf(
                VideoExportRecord(
                    VideoVersionedId("export-1", 1),
                    artifact(videoRoot, "exports/export-1.mp4", "export"),
                    listOf(takeId),
                    "2026-09-13T00:04:00Z",
                ),
            ),
            revision = 1L,
        )
    }

    private fun measuredTake(
        project: VideoProject, videoRoot: Path, id: VideoVersionedId, artifact: VideoArtifact,
        lookId: VideoVersionedId?, createdAt: String, referenceId: VideoVersionedId? = project.referenceVersions.firstOrNull()?.id,
    ): VideoTakeRecord {
        val measurement = VideoTakeMeasurementRecord(artifact.sha256,
            Files.size(videoRoot.resolve(artifact.relativePath)), "h264", 100, 60,
            VideoTakeRationalRecord(1, 1), VideoTakeRationalRecord(30, 1), 30,
            VideoTakeRationalRecord(1, 30), 0, 30, 1, 0, 0)
        val provenance = VideoTakeProvenanceRecord(project.id, "request-${id.id}-v${id.version}",
            "attempt-${id.id}-v${id.version}", "output-${id.id}-v${id.version}",
            "comfyui-local", "b".repeat(64), "c".repeat(64), referenceId, null, lookId,
            listOf(VideoGenerationDependencyPin("source-1", "d".repeat(64))))
        return VideoTakeRecord(id, artifact, lookId, createdAt, measurement, measurement, "NONE", provenance)
    }

    private fun emptyProject() = VideoProject("video-1", "Video", "2026-09-13T00:00:00Z")

    private fun artifact(projectRoot: Path, relativePath: String, contents: String): VideoArtifact {
        val file = projectRoot.resolve(relativePath)
        Files.createDirectories(requireNotNull(file.parent))
        Files.writeString(file, contents)
        return VideoArtifact(relativePath, sha256(file))
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
