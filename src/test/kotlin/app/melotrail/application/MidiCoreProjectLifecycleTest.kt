package app.melotrail.application

import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.SourceMidiRecord
import app.melotrail.project.adapter.AtomicWriteObserver
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreProjectLifecycleTest {
    @TempDir lateinit var root: Path

    @Test
    fun `create reopen and close preserve a MIDI Core project`() {
        val lifecycle = lifecycle()

        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle.create(CreateMidiCoreProject(root, "My MIDI Project", "project-1", "test-1")),
        )
        val reopened = assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle.open(root))

        assertEquals(created.session, reopened.session)
        assertEquals(created.session.project.id, assertIs<MidiCoreProjectCloseResult.Closed>(lifecycle.close(reopened.session)).projectId)
        assertTrue(Files.isRegularFile(root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `invalid create request is rejected before a project folder is created`() {
        val invalidRoot = root.resolve("invalid")

        val result = assertIs<MidiCoreProjectLifecycleResult.Rejected>(
            lifecycle().create(CreateMidiCoreProject(invalidRoot, "bad\nname", "project-1")),
        )

        assertEquals(MidiCoreProjectProblemCode.INVALID_REQUEST, result.problem.code)
        assertFalse(Files.exists(invalidRoot))
    }

    @Test
    fun `corrupted and missing project files return actionable rejection`() {
        val lifecycle = lifecycle()
        val missing = assertIs<MidiCoreProjectLifecycleResult.Rejected>(lifecycle.open(root.resolve("missing")))
        assertEquals(MidiCoreProjectProblemCode.PROJECT_NOT_FOUND, missing.problem.code)

        Files.writeString(root.resolve(MidiCoreArtifactStore.PROJECT_FILE), "not-json")
        val corrupt = assertIs<MidiCoreProjectLifecycleResult.Rejected>(lifecycle.open(root))
        assertEquals(MidiCoreProjectProblemCode.INVALID_PROJECT, corrupt.problem.code)
        assertTrue(corrupt.problem.nextAction.isNotBlank())
    }

    @Test
    fun `legacy project rejection never rewrites or migrates its document`() {
        val legacy = """{"version":4,"name":"old-audio-project"}"""
        val file = root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        Files.writeString(file, legacy)
        val before = Files.readAllBytes(file)

        val result = assertIs<MidiCoreProjectLifecycleResult.Rejected>(lifecycle().open(root))

        assertEquals(MidiCoreProjectProblemCode.UNSUPPORTED_PROJECT, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(file))
    }

    @Test
    fun `missing bound artifacts reject open before state is returned`() {
        val store = MidiCoreArtifactStore()
        val lifecycle = lifecycle(store)
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle.create(CreateMidiCoreProject(root, "My MIDI Project", "project-1")),
        )
        val hash = "a".repeat(64)
        val sourceBound = created.session.project.copy(
            sourceMidi = SourceMidiRecord(
                "source.mid",
                hash,
                1,
                480,
                ProjectArtifact(MidiCoreArtifactStore.SOURCE_MIDI, hash),
            ),
        )
        store.saveProject(root, created.session.project)
        Files.writeString(root.resolve(MidiCoreArtifactStore.PROJECT_FILE), app.melotrail.project.MidiCoreProjectSchema.encode(sourceBound))

        val result = assertIs<MidiCoreProjectLifecycleResult.Rejected>(lifecycle.open(root))
        assertEquals(MidiCoreProjectProblemCode.INVALID_PROJECT, result.problem.code)
    }

    @Test
    fun `failed save preserves the prior readable project`() {
        var failSave = false
        val store = MidiCoreArtifactStore(AtomicWriteObserver { temporary, _ ->
            if (failSave) {
                Files.writeString(temporary, "partial")
                throw IOException("simulated write failure")
            }
        })
        val lifecycle = lifecycle(store)
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle.create(CreateMidiCoreProject(root, "Original", "project-1")),
        )
        failSave = true

        val result = assertIs<MidiCoreProjectLifecycleResult.Rejected>(
            lifecycle.save(created.session, created.session.project.copy(metadata = created.session.project.metadata.copy(name = "Replacement"))),
        )

        assertEquals(MidiCoreProjectProblemCode.SAVE_FAILED, result.problem.code)
        assertEquals(created.session.project, assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle.open(root)).session.project)
    }

    private fun lifecycle(store: MidiCoreArtifactStore = MidiCoreArtifactStore()) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-project" },
    )
}
