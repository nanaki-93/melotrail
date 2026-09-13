package app.melotrail.video

import app.melotrail.video.adapter.VideoAtomicWriteObserver
import app.melotrail.video.adapter.VideoProjectStore
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
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoReferenceRecord
import app.melotrail.video.domain.VideoTakeRecord
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
        val secondTake = VideoTakeRecord(
            secondTakeId,
            artifact(videoRoot, "takes/take-1-v2.mp4", "take-two"),
            reopened.project.selectedLookId,
            // Completion may be recorded later while retaining an earlier provider timestamp.
            "2026-09-12T23:59:00Z",
        )
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
        val unsupported = """{"schema":"melotrail-video-project","version":99,"project":{}}"""
        Files.writeString(projectFile, unsupported)
        val beforeUnsupported = Files.readAllBytes(projectFile)

        assertFailsWith<UnsupportedVideoProjectException> {
            projectStore.save(videoRoot, 0L, original.copy(revision = 1L))
        }
        assertContentEquals(beforeUnsupported, Files.readAllBytes(projectFile))

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
                VideoTakeRecord(
                    takeId,
                    artifact(videoRoot, "takes/take-1.mp4", "take"),
                    lookId,
                    "2026-09-13T00:03:00Z",
                ),
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
