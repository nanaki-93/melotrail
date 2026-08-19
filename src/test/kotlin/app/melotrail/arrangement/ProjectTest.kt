package app.melotrail.arrangement

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ProjectTest {
    private val json = Json { encodeDefaults = true }

    @TempDir
    lateinit var projectRoot: Path

    @Test
    fun `project JSON round trips with parts analysis reference and ordered structure`() {
        createFile("parts/A.wav", "source-a")
        createFile("parts/B.wav", "source-b")
        createFile("analysis/A.json", "{\"frames\": 100}")
        val project = Project(
            name = "demo",
            parts = listOf(
                Part("A", "parts/A.wav", "verse", PartAnalysisReference("analysis/A.json")),
                Part("B", "parts/B.wav", "chorus")
            ),
            structure = listOf("A", "A", "B", "B", "A")
        )

        val decoded = json.decodeFromString<Project>(json.encodeToString(project))

        assertEquals(project.parts.map { it.id to it.file }, decoded.parts.map { it.id to it.file })
        assertEquals(listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS), decoded.parts.map { it.sectionType })
        assertEquals(listOf("A", "B"), decoded.parts.map { it.name })
        assertTrue(decoded.validate(projectRoot).isValid)
    }

    @Test
    fun `validation accepts existing relative files without modifying source audio`() {
        val source = createFile("parts/A.wav", "original source bytes")
        val before = Files.readString(source)
        val project = Project(name = "demo", parts = listOf(Part("A", "parts/A.wav")))

        project.requireValid(projectRoot)

        assertEquals(before, Files.readString(source))
    }

    @Test
    fun `validation rejects duplicate part IDs`() {
        createFile("parts/A.wav", "source-a")
        createFile("parts/B.wav", "source-b")
        val project = Project(
            name = "demo",
            parts = listOf(Part("A", "parts/A.wav"), Part("A", "parts/B.wav"))
        )

        val validation = project.validate(projectRoot)

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it == "Duplicate part IDs: A" })
        assertThrows(IllegalArgumentException::class.java) { project.requireValid(projectRoot) }
    }

    @Test
    fun `validation rejects missing absolute and escaping paths`() {
        createFile("parts/A.wav", "source-a")
        val project = Project(
            name = "demo",
            parts = listOf(
                Part("missing", "parts/missing.wav"),
                Part("absolute", projectRoot.resolve("parts/A.wav").toString()),
                Part("escape", "../outside.wav")
            )
        )

        val validation = project.validate(projectRoot)

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("missing.wav") })
        assertTrue(validation.errors.any { it.contains("absolute") && it.contains("relative") })
        assertTrue(validation.errors.any { it.contains("escapes the project root") })
    }

    @Test
    fun `validation rejects structure entries without a matching part`() {
        createFile("parts/A.wav", "source-a")
        val project = Project(
            name = "demo",
            parts = listOf(Part("A", "parts/A.wav")),
            envelope = ProjectV4Envelope(structureOccurrences = listOf(
                StructureOccurrence("occ-A-1", "A"),
                StructureOccurrence("occ-B-1", "B")
            ))
        )

        val validation = project.validate(projectRoot)

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("unknown part ID 'B'") })
    }

    @Test
    fun `v2 validation rejects a source symlink that escapes the project root`() {
        val outside = projectRoot.resolveSibling("outside")
        Files.createDirectories(outside)
        Files.writeString(outside.resolve("A.mid"), "outside")
        Files.createSymbolicLink(projectRoot.resolve("source"), outside)
        createFile("midi/clean/A.mid", "clean")
        val project = Project(
            version = 2,
            name = "demo",
            renderFormat = RenderFormat(),
            parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid")))
        )

        val validation = project.validate(projectRoot)

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("source") && it.contains("escapes the project root") })
    }

    private fun createFile(relativePath: String, contents: String): Path {
        val path = projectRoot.resolve(relativePath)
        Files.createDirectories(path.parent)
        return Files.writeString(path, contents)
    }
}
