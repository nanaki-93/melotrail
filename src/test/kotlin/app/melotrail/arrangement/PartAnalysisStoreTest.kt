package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PartAnalysisStoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @TempDir
    lateinit var projectRoot: Path

    @Test
    fun `stores analysis and preserves the imported source file`() {
        val source = projectRoot.resolve("parts/A.wav")
        Files.createDirectories(source.parent)
        Files.writeString(source, "source audio bytes")
        val sourceBefore = Files.readString(source)
        val project = Project(name = "demo", parts = listOf(Part("A", "parts/A.wav")))
        Files.writeString(projectRoot.resolve("project.json"), json.encodeToString(project))
        val analysis = PartAnalysis(
            duration = 2.0,
            sampleRate = 48_000,
            channels = 2,
            frameCount = 96_000,
            peak = 0.5,
            rms = 0.25,
            nearSilence = false
        )

        val analysisPath = PartAnalysisStore.write(projectRoot, project, "A", analysis)

        val storedAnalysis = json.decodeFromString<PartAnalysis>(Files.readString(analysisPath))
        val updatedProject = json.decodeFromString<Project>(
            Files.readString(projectRoot.resolve("project.json"))
        )
        assertEquals(sourceBefore, Files.readString(source))
        assertEquals(analysis, storedAnalysis)
        assertEquals("analysis/A.json", updatedProject.parts.single().analysis?.file)
        assertTrue(updatedProject.validate(projectRoot).isValid)
    }
}
