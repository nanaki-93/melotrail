package ai.music.workstation.cli

import ai.music.workstation.arrangement.Arrangement
import ai.music.workstation.arrangement.ArrangementSection
import ai.music.workstation.arrangement.ArrangementStore
import ai.music.workstation.arrangement.BassNote
import ai.music.workstation.arrangement.BassRenderRequest
import ai.music.workstation.arrangement.DeterministicTestBassRenderer
import ai.music.workstation.arrangement.InstrumentPlan
import ai.music.workstation.arrangement.InstrumentMode
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.PartAnalysisStore
import ai.music.workstation.arrangement.Project
import kotlinx.serialization.decodeFromString
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

    @Test
    fun `arrange explicitly selects deterministic planner and writes arrangement json`() {
        val projectRoot = createProject("demo")
        val source = tempDir.resolve("piano.wav")
        Files.writeString(source, "original source bytes")
        addPart(projectRoot, "A", source)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val copiedSourceBefore = Files.readString(copiedSource)

        val result = ArrangementProjectCommands.execute(
            arrayOf(
                "arrange", "--project", projectRoot.toString(), "--planner", "deterministic",
                "--structure", "A A", "--instruments", "piano,bass", "--style", "warm"
            )
        )

        val arrangement = json.decodeFromString<Arrangement>(
            Files.readString(projectRoot.resolve("arrangement.json"))
        )
        assertTrue(ArrangementProjectCommands.handles(arrayOf("arrange")))
        assertTrue(result.contains("Created deterministic arrangement"))
        assertEquals(listOf("A", "A"), arrangement.sections.map { it.partId })
        assertEquals(InstrumentMode.SOURCE, arrangement.sections.first().instruments.first().mode)
        assertEquals(InstrumentMode.GENERATED, arrangement.sections.first().instruments[1].mode)
        assertEquals(copiedSourceBefore, Files.readString(copiedSource))
    }

    @Test
    fun `arrange rejects unknown planners without writing an arrangement`() {
        val projectRoot = createProject("demo")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.execute(
                arrayOf("arrange", "--project", projectRoot.toString(), "--planner", "unknown")
            )
        }

        assertEquals("Unsupported planner: unknown. Available planners: deterministic, qwen", exception.message)
        assertFalse(Files.exists(projectRoot.resolve("arrangement.json")))
    }

    @Test
    fun `generate bass writes a stem under the project without changing the source`() {
        val projectRoot = createProject("demo")
        val source = tempDir.resolve("piano.wav")
        Files.writeString(source, "original source bytes")
        addPart(projectRoot, "A", source)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readString(copiedSource)
        val project = readProject(projectRoot)
        PartAnalysisStore.write(
            projectRoot,
            project,
            "A",
            PartAnalysis(0.01, 32_000, 1, 320, 0.5, 0.25, false)
        )
        val updatedProject = readProject(projectRoot)
        ArrangementStore.write(
            projectRoot,
            updatedProject,
            Arrangement(
                sections = listOf(
                    ArrangementSection(
                        index = 0,
                        partId = "A",
                        instruments = listOf(
                            InstrumentPlan("piano", InstrumentMode.SOURCE),
                            InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 0.5)
                        )
                    )
                )
            )
        )

        val result = ArrangementProjectCommands.execute(
            arrayOf("generate", "bass", "--project", projectRoot.toString())
        )

        assertTrue(ArrangementProjectCommands.handles(arrayOf("generate")))
        assertTrue(result.contains("Generated bass stem"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("stems/bass.wav")))
        assertEquals(sourceBefore, Files.readString(copiedSource))
    }

    @Test
    fun `mix creates full and dry lossless WAV files from source and generated bass stems`() {
        val projectRoot = createProject("mix-demo")
        val source = tempDir.resolve("piano.wav")
        DeterministicTestBassRenderer().render(
            BassRenderRequest(
                notes = listOf(BassNote(0, 320, velocity = 1.0)),
                sampleRate = 32_000,
                channels = 1,
                frameCount = 320
            ),
            source
        )
        addPart(projectRoot, "A", source)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)
        val project = readProject(projectRoot)
        PartAnalysisStore.write(
            projectRoot,
            project,
            "A",
            PartAnalysis(0.01, 32_000, 1, 320, 0.5, 0.25, false)
        )
        val updatedProject = readProject(projectRoot)
        ArrangementStore.write(
            projectRoot,
            updatedProject,
            Arrangement(
                sections = listOf(
                    ArrangementSection(
                        index = 0,
                        partId = "A",
                        instruments = listOf(
                            InstrumentPlan("piano", InstrumentMode.SOURCE),
                            InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 0.5)
                        )
                    )
                )
            )
        )
        ArrangementProjectCommands.execute(arrayOf("generate", "bass", "--project", projectRoot.toString()))

        val fullResult = ArrangementProjectCommands.execute(arrayOf("mix", "--project", projectRoot.toString()))
        val mixPath = projectRoot.resolve("mix/mix.wav")
        val fullMix = Files.readAllBytes(mixPath)
        val dryResult = ArrangementProjectCommands.execute(
            arrayOf("mix", "--project", projectRoot.toString(), "--dry")
        )
        val dryMix = Files.readAllBytes(mixPath)

        assertTrue(ArrangementProjectCommands.handles(arrayOf("mix")))
        assertTrue(fullResult.contains("Created mix"))
        assertTrue(dryResult.contains("Created dry mix"))
        assertEquals("RIFF", fullMix.copyOfRange(0, 4).decodeToString())
        assertEquals("RIFF", dryMix.copyOfRange(0, 4).decodeToString())
        assertFalse(fullMix.contentEquals(dryMix))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `build runs deterministic arrangement through preserved WAV intermediates and custom output`() {
        val projectRoot = createProject("build-demo")
        val source = tempDir.resolve("piano.wav")
        DeterministicTestBassRenderer().render(
            BassRenderRequest(
                notes = listOf(BassNote(0, 320, velocity = 0.7)),
                sampleRate = 32_000,
                channels = 1,
                frameCount = 320
            ),
            source
        )
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)
        val output = projectRoot.resolve("rendered")

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf(
                "build", "--project", projectRoot.toString(),
                "--output-dir", "rendered", "--no-ai"
            ),
            CopyingBuildWorker()
        )

        assertTrue(result.contains("[1/10] Loaded project"))
        assertTrue(result.contains("[10/10] Build complete"))
        assertTrue(Files.isRegularFile(projectRoot.resolve("analysis/A.json")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("arrangement.json")))
        assertTrue(Files.isRegularFile(projectRoot.resolve("stems/bass.wav")))
        assertWav(projectRoot.resolve("mix/mix.wav"))
        assertWav(output.resolve("repair.wav"), sampleRate = 32_000, channels = 1)
        assertWav(output.resolve("lofi.wav"), sampleRate = 32_000, channels = 1)
        assertWav(output.resolve("master.wav"), sampleRate = 32_000, channels = 1)
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `build dry run validates project but does not write derived files or require worker`() {
        val projectRoot = createProject("dry-run-demo")
        val source = tempDir.resolve("piano.wav")
        DeterministicTestBassRenderer().render(
            BassRenderRequest(
                notes = listOf(BassNote(0, 64, velocity = 0.7)),
                sampleRate = 22_050,
                channels = 1,
                frameCount = 64
            ),
            source
        )
        addPart(projectRoot, "A", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("A")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/A.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)

        val result = ArrangementProjectCommands.executeBuildForTest(
            arrayOf("build", "--project", projectRoot.toString(), "--dry-run"),
            object : ArrangementProjectCommands.BuildWorker {
                override suspend fun healthCheck(): Boolean = error("worker must not be used for dry run")
                override suspend fun analyze(path: Path): PartAnalysis = error("worker must not be used for dry run")
                override suspend fun repair(inputPath: Path, outputPath: Path) = error("worker must not be used for dry run")
                override suspend fun master(inputPath: Path, outputPath: Path) = error("worker must not be used for dry run")
            }
        )

        assertTrue(result.contains("[DRY RUN] Project is valid"))
        assertFalse(Files.exists(projectRoot.resolve("analysis/A.json")))
        assertFalse(Files.exists(projectRoot.resolve("arrangement.json")))
        assertFalse(Files.exists(projectRoot.resolve("stems/bass.wav")))
        assertFalse(Files.exists(projectRoot.resolve("output/master.wav")))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
    }

    @Test
    fun `build rejects an output directory that would overwrite a source part`() {
        val projectRoot = createProject("protected-source-demo")
        val source = tempDir.resolve("master.wav")
        DeterministicTestBassRenderer().render(
            BassRenderRequest(
                notes = listOf(BassNote(0, 64, velocity = 0.7)),
                sampleRate = 22_050,
                channels = 1,
                frameCount = 64
            ),
            source
        )
        addPart(projectRoot, "master", source)
        writeProject(readProject(projectRoot).copy(structure = listOf("master")), projectRoot)
        val copiedSource = projectRoot.resolve("parts/master.wav")
        val sourceBefore = Files.readAllBytes(copiedSource)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executeBuildForTest(
                arrayOf("build", "--project", projectRoot.toString(), "--output-dir", "parts", "--dry-run"),
                CopyingBuildWorker()
            )
        }

        assertTrue(exception.message.orEmpty().contains("would overwrite a source audio file"))
        assertTrue(Files.readAllBytes(copiedSource).contentEquals(sourceBefore))
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

    private fun writeProject(project: Project, projectRoot: Path) {
        Files.writeString(projectRoot.resolve("project.json"), json.encodeToString(project))
    }

    private fun assertWav(path: Path, sampleRate: Int? = null, channels: Int? = null) {
        assertTrue(Files.size(path) >= 44)
        val bytes = Files.readAllBytes(path)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        if (sampleRate != null || channels != null) {
            val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            sampleRate?.let { assertEquals(it, header.getInt(24)) }
            channels?.let { assertEquals(it, header.getShort(22).toInt()) }
        }
    }

    private class CopyingBuildWorker : ArrangementProjectCommands.BuildWorker {
        override suspend fun healthCheck(): Boolean = true

        override suspend fun analyze(path: Path): PartAnalysis = PartAnalysis(
            duration = 0.01,
            sampleRate = 32_000,
            channels = 1,
            frameCount = 320,
            peak = 0.5,
            rms = 0.25,
            nearSilence = false
        )

        override suspend fun repair(inputPath: Path, outputPath: Path) {
            Files.copy(inputPath, outputPath)
        }

        override suspend fun master(inputPath: Path, outputPath: Path) {
            Files.copy(inputPath, outputPath)
        }
    }
}
