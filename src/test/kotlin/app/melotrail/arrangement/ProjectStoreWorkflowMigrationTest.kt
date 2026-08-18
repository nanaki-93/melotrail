package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectStoreWorkflowMigrationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v1 v2 and v3 fixtures open without rewriting project json`() {
        write("parts/A.mid")
        ProjectStore.write(root, Project(version = 1, name = "v1", parts = listOf(Part("A", "parts/A.mid"))))
        val v1Before = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        assertEquals(1, ProjectStore.read(root).version)
        assertEquals(v1Before, Files.readString(root.resolve(ProjectStore.FILE_NAME)))

        write("source/A.mid"); write("midi/clean/A.mid")
        ProjectStore.write(root, Project(version = 2, name = "v2", renderFormat = RenderFormat(), parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid")))))
        val v2Before = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        assertEquals(2, ProjectStore.read(root).version)
        assertEquals(v2Before, Files.readString(root.resolve(ProjectStore.FILE_NAME)))

        ProjectStore.migrateV2(root)
        assertEquals(Project.CURRENT_VERSION, ProjectStore.read(root).version)
        assertTrue(Files.readString(root.resolve(ProjectStore.FILE_NAME)).contains("\"workflow\""))
        val migrated = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        assertTrue(migrated.contains("\"analysisInput\": \"REPAIRED\""))
        assertEquals(MidiAiFixSelection.SKIP, ProjectStore.read(root).parts.single().midi?.aiFixSelection)

        assertEquals(Project.CURRENT_VERSION, ProjectStore.migrateV2(root).version)
        assertEquals(migrated, Files.readString(root.resolve(ProjectStore.FILE_NAME)))
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

        assertFailsWith<IllegalArgumentException> { ProjectStore.migrateV2(root) }
        assertEquals(invalidV2, Files.readString(projectFile))
    }

    private fun write(relative: String) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, "fixture")
    }
}
