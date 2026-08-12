package ai.music.workstation.cli

import ai.music.workstation.arrangement.Project
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArrangementProjectCommandsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `project create initializes project json and parts directory`() {
        val projectRoot = tempDir.resolve("demo")

        val result = ArrangementProjectCommands.execute(
            arrayOf("project", "create", projectRoot.toString())
        )

        val project = readProject(projectRoot)
        assertTrue(result.contains("Created project"))
        assertTrue(Files.isDirectory(projectRoot.resolve("parts")))
        assertEquals(Project.CURRENT_VERSION, project.version)
        assertEquals("demo", project.name)
        assertTrue(project.parts.isEmpty())
    }

    @Test
    fun `part add copies supported audio preserves source and updates project json`() {
        val projectRoot = createProject("demo")
        val source = tempDir.resolve("piano.wav")
        Files.writeString(source, "original source bytes")
        val sourceBefore = Files.readString(source)

        val result = ArrangementProjectCommands.execute(
            arrayOf(
                "part", "add", projectRoot.toString(),
                "--id", "A", "--file", source.toString(), "--role", "verse"
            )
        )

        val copied = projectRoot.resolve("parts/A.wav")
        val project = readProject(projectRoot)
        assertTrue(result.contains("Added part 'A'"))
        assertEquals(sourceBefore, Files.readString(source))
        assertEquals(sourceBefore, Files.readString(copied))
        assertEquals(listOf("A"), project.parts.map { it.id })
        assertEquals("parts/A.wav", project.parts.single().file)
        assertEquals("verse", project.parts.single().role)
    }

    @Test
    fun `part add rejects duplicate ids without overwriting the imported file`() {
        val projectRoot = createProject("demo")
        val firstSource = tempDir.resolve("first.wav")
        val secondSource = tempDir.resolve("second.wav")
        Files.writeString(firstSource, "first source")
        Files.writeString(secondSource, "second source")
        addPart(projectRoot, "A", firstSource)
        val copiedBefore = Files.readString(projectRoot.resolve("parts/A.wav"))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            addPart(projectRoot, "A", secondSource)
        }

        assertTrue(exception.message.orEmpty().contains("Part ID already exists: A"))
        assertEquals(copiedBefore, Files.readString(projectRoot.resolve("parts/A.wav")))
        assertEquals(1, readProject(projectRoot).parts.size)
    }

    @Test
    fun `part add rejects unsupported input without modifying project`() {
        val projectRoot = createProject("demo")
        val unsupported = tempDir.resolve("notes.txt")
        Files.writeString(unsupported, "not audio")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            addPart(projectRoot, "notes", unsupported)
        }

        assertTrue(exception.message.orEmpty().contains("Unsupported audio file extension"))
        assertFalse(Files.exists(projectRoot.resolve("parts/notes.txt")))
        assertTrue(readProject(projectRoot).parts.isEmpty())
    }

    private fun createProject(name: String): Path {
        val projectRoot = tempDir.resolve(name)
        ArrangementProjectCommands.execute(arrayOf("project", "create", projectRoot.toString()))
        return projectRoot
    }

    private fun addPart(projectRoot: Path, id: String, source: Path) {
        ArrangementProjectCommands.execute(
            arrayOf("part", "add", projectRoot.toString(), "--id", id, "--file", source.toString())
        )
    }

    private fun readProject(projectRoot: Path): Project =
        json.decodeFromString(Files.readString(projectRoot.resolve("project.json")))
}
