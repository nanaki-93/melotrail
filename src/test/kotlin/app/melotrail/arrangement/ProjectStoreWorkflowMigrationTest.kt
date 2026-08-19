package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectStoreWorkflowMigrationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v1 v2 and v3 migrate explicitly without mutating existing artifact bytes`() {
        val cases = listOf(
            LegacyCase(1, "parts/A.mid", null),
            LegacyCase(2, "source/A.mid", "midi/clean/A.mid"),
            LegacyCase(3, "source/A.mid", "midi/raw/A.mid")
        )

        cases.forEach { fixture ->
            val projectRoot = root.resolve("v${fixture.version}")
            write(projectRoot, fixture.source)
            fixture.midi?.let { write(projectRoot, it) }
            val references = fixture.midi?.let { path ->
                if (fixture.version == 3) MidiReferences(raw = path) else MidiReferences(clean = path)
            }
            writeLegacyProjectFixture(projectRoot, Project(
                version = fixture.version,
                name = "v${fixture.version}",
                renderFormat = RenderFormat().takeIf { fixture.version >= 2 },
                parts = listOf(Part("A", fixture.source, midi = references))
            ))
            val projectBefore = Files.readAllBytes(projectRoot.resolve(ProjectStore.FILE_NAME))
            val artifactHashes = (listOf(fixture.source) + listOfNotNull(fixture.midi))
                .associateWith { sha256(projectRoot.resolve(it)) }

            assertEquals(fixture.version, ProjectStore.read(projectRoot).version)
            assertTrue(projectBefore.contentEquals(Files.readAllBytes(projectRoot.resolve(ProjectStore.FILE_NAME))), "open must not rewrite v${fixture.version}")
            val planned = ProjectStore.readMigration(projectRoot)

            assertEquals(fixture.version, planned.sourceVersion)
            assertEquals(Project.CURRENT_VERSION, planned.project.version)
            assertTrue(projectBefore.contentEquals(Files.readAllBytes(projectRoot.resolve(ProjectStore.FILE_NAME))), "open/migration planning must not rewrite v${fixture.version}")

            val saved = ProjectStore.migrateAndSave(projectRoot)

            assertEquals(Project.CURRENT_VERSION, saved.migration.project.version)
            assertEquals(Project.CURRENT_VERSION, ProjectStore.read(projectRoot).version)
            artifactHashes.forEach { (path, expectedHash) ->
                assertEquals(expectedHash, sha256(projectRoot.resolve(path)), "migration must preserve $path from v${fixture.version}")
            }
            if (fixture.version == 2) {
                val migrated = Files.readString(projectRoot.resolve(ProjectStore.FILE_NAME))
                val v4 = ProjectStore.read(projectRoot)
                assertTrue(v4.envelope.stageRuns.index != null)
                assertEquals(setOf(StageId.SOURCE, StageId.CLEANED), StageRunStore().read(projectRoot, v4.envelope.stageRuns).map(StageRunRecord::stage).toSet())
                assertTrue(migrated.contains("\"analysisInput\": \"REPAIRED\""))
                assertEquals(MidiAiFixSelection.SKIP, v4.parts.single().midi?.aiFixSelection)
                assertEquals(Project.CURRENT_VERSION, ProjectStore.migrateAndSave(projectRoot).migration.project.version)
                assertEquals(migrated, Files.readString(projectRoot.resolve(ProjectStore.FILE_NAME)))
            }
        }
    }

    @Test
    fun `corrupt optional v3 workflow artifacts remain an actionable stale state rather than a partial-open failure`() {
        write("source/A.mid"); write("midi/clean/A.mid")
        val stale = setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT)
        ProjectStore.write(root, Project(
            version = Project.CURRENT_VERSION,
            name = "partial",
            renderFormat = RenderFormat(),
            parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))),
            workflow = ProjectWorkflowReferences(stale = stale)
        ))

        val opened = ProjectStore.read(root)

        assertEquals(stale, opened.workflow.stale)
        assertTrue(opened.validate(root).isValid)
        assertFalse(Files.exists(root.resolve("cohesion/cohesion.json")))
    }

    @Test
    fun `failed explicit migration leaves the complete v2 file unchanged`() {
        val projectFile = root.resolve(ProjectStore.FILE_NAME)
        val invalidV2 = """
            {
              "version": 2,
              "name": "invalid",
              "renderFormat": {"sampleRate": 44100, "channels": 2, "bitDepth": 24},
              "parts": [{
                "id": "A",
                "role": "verse",
                "sourceFile": "source/missing.mid",
                "midi": {"clean": "midi/clean/missing.mid"}
              }],
              "structure": []
            }
        """.trimIndent()
        Files.writeString(projectFile, invalidV2)

        assertFailsWith<IllegalArgumentException> { ProjectStore.migrateAndSave(root) }
        assertEquals(invalidV2, Files.readString(projectFile))
    }

    private fun write(relative: String) = write(root, relative)

    private fun write(projectRoot: Path, relative: String) {
        val path = projectRoot.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, "fixture")
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private data class LegacyCase(val version: Int, val source: String, val midi: String?)
}
