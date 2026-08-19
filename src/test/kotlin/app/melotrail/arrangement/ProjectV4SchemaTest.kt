package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectV4SchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v4 fixture reads as a portable pending-run envelope`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/fixtures/project/v4-pending-run.json")) { "Missing v4 fixture" }
        Files.copy(fixture, root.resolve(ProjectStore.FILE_NAME))

        val project = ProjectStore.read(root)

        assertEquals(Project.CURRENT_VERSION, project.version)
        assertEquals(ManifestRunStatus.PENDING, project.envelope.manifests.runs.single().status)
        assertTrue(project.validate(root).isValid)
    }

    @Test
    fun `legacy v3 migration is pure until explicit v4 save and preserves source evidence`() {
        write("source/A.mid", "source")
        write("midi/raw/A.mid", "raw-midi")
        val sourceHash = sha256(root.resolve("source/A.mid"))
        val rawHash = sha256(root.resolve("midi/raw/A.mid"))
        writeLegacyProjectFixture(root, Project(
            version = 3,
            name = "legacy",
            renderFormat = RenderFormat(),
            parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(raw = "midi/raw/A.mid"), importEvidence = ImportEvidence(sourceHash, rawHash)))
        ))
        val before = Files.readAllBytes(root.resolve(ProjectStore.FILE_NAME))

        val migration = ProjectStore.readMigration(root)

        assertEquals(3, migration.sourceVersion)
        assertEquals(Project.CURRENT_VERSION, migration.project.version)
        assertEquals(setOf(ProjectSetupRequirement.COMPOSITION_SETTINGS, ProjectSetupRequirement.HARMONY), migration.setupRequirements)
        assertTrue(before.contentEquals(Files.readAllBytes(root.resolve(ProjectStore.FILE_NAME))), "migration planning must not write")

        val saved = ProjectStore.migrateAndSave(root)

        assertEquals(Project.CURRENT_VERSION, ProjectStore.read(root).version)
        assertEquals(sourceHash, saved.migration.project.parts.single().importEvidence?.sourceSha256)
        assertEquals(rawHash, saved.migration.project.parts.single().importEvidence?.rawMidiSha256)
        assertTrue(ProjectStore.read(root).validate(root).isValid)
    }

    @Test
    fun `legacy unknown fields become explicit migration warnings and v4 rejects unknown fields`() {
        Files.writeString(root.resolve(ProjectStore.FILE_NAME), """
            {"version":3,"name":"legacy","renderFormat":{"sampleRate":44100,"channels":2,"bitDepth":24},"parts":[],"structure":[],"legacyFlag":"keep-visible"}
        """.trimIndent())

        val migration = ProjectStore.readMigration(root)

        assertTrue(migration.warnings.any { "legacyFlag" in it })
        ProjectStore.migrateAndSave(root)
        val v4 = Files.readString(root.resolve(ProjectStore.FILE_NAME)).replace("\"version\": 4", "\"version\": 4, \"machinePath\": \"/tmp/not-portable\"")
        Files.writeString(root.resolve(ProjectStore.FILE_NAME), v4)
        assertFalse(runCatching { ProjectStore.read(root) }.isSuccess, "v4 must not silently accept an unknown machine-local field")
    }

    @Test
    fun `pending and failed runs allow absent output while completed output is hash validated`() {
        write("source/A.mid", "source")
        val pending = ManifestRunReference("render", ManifestRunStatus.PENDING, listOf(WorkflowArtifactReference("output/not-yet.wav", "0".repeat(64))))
        val failed = ManifestRunReference("mix", ManifestRunStatus.FAILED)
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "runs",
            renderFormat = RenderFormat(),
            parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(raw = "source/A.mid"))),
            envelope = ProjectV4Envelope(manifests = ProjectManifestReferences(listOf(pending, failed)))
        )
        assertTrue(project.validate(root).isValid)

        write("output/master.wav", "final")
        val completed = ManifestRunReference("master", ManifestRunStatus.COMPLETED, listOf(WorkflowArtifactReference("output/master.wav", sha256(root.resolve("output/master.wav")))))
        assertTrue(project.copy(envelope = project.envelope.copy(manifests = ProjectManifestReferences(listOf(completed)))).validate(root).isValid)
        assertFalse(project.copy(envelope = project.envelope.copy(manifests = ProjectManifestReferences(listOf(
            completed.copy(artifacts = listOf(WorkflowArtifactReference("output/master.wav", "f".repeat(64))))
        )))).validate(root).isValid)
    }

    private fun write(relative: String, content: String) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val bytes = input.readBytes()
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
