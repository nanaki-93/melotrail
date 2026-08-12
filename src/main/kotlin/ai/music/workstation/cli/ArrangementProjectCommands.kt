package ai.music.workstation.cli

import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.PartAnalysisStore
import ai.music.workstation.arrangement.Project
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.WorkerClient
import ai.music.workstation.worker.WorkerStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Small file-based commands for the arranger project format.
 *
 * This deliberately does not share the web application's project storage:
 * arranger projects are self-contained directories with project.json and
 * copied source files under parts/.
 */
object ArrangementProjectCommands {
    private const val PROJECT_FILE = "project.json"
    private val supportedExtensions = setOf("wav", "wave", "mp3", "flac", "fla")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun handles(args: Array<String>): Boolean = args.firstOrNull() in setOf("project", "part")

    fun execute(args: Array<String>): String = runBlocking {
        executeAsync(args)
    }

    suspend fun executeAsync(args: Array<String>): String = when (args.firstOrNull()) {
        "project" -> createProject(args)
        "part" -> when (args.getOrNull(1)) {
            "add" -> addPart(args)
            "analyze" -> analyzePart(args)
            else -> throw IllegalArgumentException(
                "Usage: part add <project-directory> ... or part analyze <project-directory> --id <id>"
            )
        }
        else -> throw IllegalArgumentException("Unknown arranger command")
    }

    private fun createProject(args: Array<String>): String {
        require(args.size == 3 && args[1] == "create") {
            "Usage: project create <project-directory>"
        }

        val projectRoot = projectRoot(args[2])
        require(!Files.exists(projectRoot) || Files.isDirectory(projectRoot)) {
            "Project path is not a directory: $projectRoot"
        }

        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(!Files.exists(projectFile)) {
            "Project already exists: $projectFile"
        }

        Files.createDirectories(projectRoot.resolve("parts"))
        val name = projectRoot.fileName?.toString().orEmpty()
        require(name.isNotBlank()) { "Project directory must have a name" }
        writeNewProject(projectFile, Project(name = name))

        return "Created project: $projectRoot"
    }

    private fun addPart(args: Array<String>): String {
        require(args.size >= 3 && args[1] == "add") {
            "Usage: part add <project-directory> --id <id> --file <audio-file> [--role <role>]"
        }

        val projectRoot = projectRoot(args[2])
        val options = parseAddOptions(args.drop(3))
        val id = options.getValue("--id")
        require(PART_ID.matches(id)) {
            "Part ID must contain only letters, numbers, underscores, or hyphens: $id"
        }

        val source = Path.of(options.getValue("--file")).toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Input audio file not found: $source" }
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in supportedExtensions) {
            "Unsupported audio file extension: ${if (extension.isEmpty()) "(none)" else extension}"
        }

        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        val project = json.decodeFromString<Project>(Files.readString(projectFile))
        project.requireValid(projectRoot)
        require(project.parts.none { it.id == id }) { "Part ID already exists: $id" }

        val relativeFile = "parts/$id.$extension"
        val destination = projectRoot.resolve(relativeFile)
        require(!Files.exists(destination)) { "Part destination already exists: $destination" }

        Files.createDirectories(destination.parent)
        Files.copy(source, destination)

        val updated = project.copy(
            parts = project.parts + Part(
                id = id,
                file = relativeFile,
                role = options["--role"].orEmpty()
            )
        )
        updated.requireValid(projectRoot)
        Files.writeString(projectFile, json.encodeToString(updated), StandardCharsets.UTF_8)

        return "Added part '$id' to $projectRoot"
    }

    private suspend fun analyzePart(args: Array<String>): String {
        require(args.size >= 3 && args[1] == "analyze") {
            "Usage: part analyze <project-directory> --id <id>"
        }

        val projectRoot = projectRoot(args[2])
        val id = parseAnalyzeOptions(args.drop(3)).getValue("--id")
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }

        val project = json.decodeFromString<Project>(Files.readString(projectFile))
        project.requireValid(projectRoot)
        val part = project.parts.find { it.id == id }
            ?: throw IllegalArgumentException("Part not found: $id")
        val source = projectRoot.resolve(part.file).normalize()

        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")
                ?.takeIf { it.isNotBlank() }
                ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        val response = client.execute(
            AnalyzeCommand(
                path = source.toString(),
                options = AnalyzeOptions(
                    detectBPM = false,
                    detectKey = false,
                    detectLoudness = false,
                    detectOnsets = false,
                    detectBeats = false,
                    detectSections = false
                )
            )
        )
        val workerError = response.error?.message ?: "Unknown worker error"
        require(response.status == WorkerStatus.COMPLETED) {
            "Part analysis failed: $workerError"
        }

        val analysisPath = PartAnalysisStore.write(
            projectRoot,
            project,
            id,
            analysisFrom(response.output.orEmpty())
        )
        return "Analyzed part '$id': $analysisPath"
    }

    private fun analysisFrom(output: Map<String, kotlinx.serialization.json.JsonElement>): PartAnalysis =
        PartAnalysis(
            duration = requiredDouble(output, "duration"),
            sampleRate = requiredLong(output, "sampleRate").toInt(),
            channels = requiredLong(output, "channels").toInt(),
            frameCount = requiredLong(output, "frameCount"),
            peak = requiredDouble(output, "peak"),
            rms = requiredDouble(output, "rms"),
            nearSilence = output["nearSilence"]?.jsonPrimitive?.booleanOrNull
                ?: throw IllegalArgumentException("Worker analysis did not return nearSilence")
        )

    private fun requiredDouble(
        output: Map<String, kotlinx.serialization.json.JsonElement>,
        name: String
    ): Double = output[name]?.jsonPrimitive?.doubleOrNull
        ?: throw IllegalArgumentException("Worker analysis did not return $name")

    private fun requiredLong(
        output: Map<String, kotlinx.serialization.json.JsonElement>,
        name: String
    ): Long = output[name]?.jsonPrimitive?.longOrNull
        ?: throw IllegalArgumentException("Worker analysis did not return $name")

    private fun parseAddOptions(arguments: List<String>): Map<String, String> {
        val options = mutableMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in ADD_OPTIONS) { "Unknown part option: $option" }
            require(index + 1 < arguments.size) { "Missing value for $option" }
            require(option !in options) { "Duplicate part option: $option" }
            options[option] = arguments[index + 1]
            index += 2
        }
        require("--id" in options) { "Missing required option: --id" }
        require("--file" in options) { "Missing required option: --file" }
        return options
    }

    private fun parseAnalyzeOptions(arguments: List<String>): Map<String, String> {
        require(arguments.size == 2 && arguments[0] == "--id") {
            "Usage: part analyze <project-directory> --id <id>"
        }
        return mapOf("--id" to arguments[1])
    }

    private fun projectRoot(path: String): Path = Path.of(path).toAbsolutePath().normalize()

    private fun writeNewProject(projectFile: Path, project: Project) {
        Files.newBufferedWriter(
            projectFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        ).use { writer ->
            writer.write(json.encodeToString(project))
        }
    }

    private val PART_ID = Regex("[A-Za-z0-9_-]+")
    private val ADD_OPTIONS = setOf("--id", "--file", "--role")
}
