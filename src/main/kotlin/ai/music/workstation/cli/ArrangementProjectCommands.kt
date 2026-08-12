package ai.music.workstation.cli

import ai.music.workstation.arrangement.ArrangementInput
import ai.music.workstation.arrangement.ArrangementPlanner
import ai.music.workstation.arrangement.ArrangementStore
import ai.music.workstation.arrangement.BassStemGenerationAdapter
import ai.music.workstation.arrangement.DeterministicArrangementPlanner
import ai.music.workstation.arrangement.DeterministicStemMixer
import ai.music.workstation.arrangement.LocalQwenArrangementPlanner
import ai.music.workstation.arrangement.MixSettings
import ai.music.workstation.arrangement.MixTrack
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.PartAnalysisStore
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.SectionInstance
import ai.music.workstation.arrangement.StructureParser
import ai.music.workstation.audio.WAVDecoder
import ai.music.workstation.audio.AudioResampler
import ai.music.workstation.dsp.DSPChain
import ai.music.workstation.dsp.LOFIPresets
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.MasterCommand
import ai.music.workstation.worker.RepairCommand
import ai.music.workstation.worker.RepairSpec
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

    fun handles(args: Array<String>): Boolean =
        args.firstOrNull() in setOf("project", "part", "arrange", "generate", "mix", "build")

    /** Small boundary that lets the end-to-end command be tested without a running HTTP worker. */
    internal interface BuildWorker {
        suspend fun healthCheck(): Boolean
        suspend fun analyze(path: Path): PartAnalysis
        suspend fun repair(inputPath: Path, outputPath: Path)
        suspend fun master(inputPath: Path, outputPath: Path)
    }

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
        "arrange" -> arrange(args)
        "generate" -> generateBass(args)
        "mix" -> mixStems(args)
        "build" -> buildProject(args, createBuildWorker())
        else -> throw IllegalArgumentException("Unknown arranger command")
    }

    internal fun executeBuildForTest(args: Array<String>, worker: BuildWorker): String = runBlocking {
        buildProject(args, worker)
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

    private fun arrange(args: Array<String>): String {
        val options = parseArrangeOptions(args.drop(1))
        val plannerName = options["--planner"] ?: DETERMINISTIC_PLANNER
        val planner = plannerFor(plannerName)

        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        val project = json.decodeFromString<Project>(Files.readString(projectFile))
        project.requireValid(projectRoot)

        val structure = options["--structure"]?.let { StructureParser.parse(it, project) }
            ?: project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        val analyses = project.parts.mapNotNull { part ->
            part.analysis?.let { reference ->
                part.id to json.decodeFromString<PartAnalysis>(
                    Files.readString(projectRoot.resolve(reference.file))
                )
            }
        }.toMap()
        val arrangement = planner.plan(
            ArrangementInput(
                project = project,
                analyses = analyses,
                structure = structure,
                requestedInstruments = parseInstrumentList(options["--instruments"]),
                style = options["--style"]
            )
        )
        val arrangementPath = ArrangementStore.write(projectRoot, project, arrangement)
        return "Created $plannerName arrangement: $arrangementPath"
    }

    private fun plannerFor(name: String): ArrangementPlanner = when (name) {
        DETERMINISTIC_PLANNER -> DeterministicArrangementPlanner()
        QWEN_PLANNER -> LocalQwenArrangementPlanner()
        else -> throw IllegalArgumentException(
            "Unsupported planner: $name. Available planners: $DETERMINISTIC_PLANNER, $QWEN_PLANNER"
        )
    }

    private fun generateBass(args: Array<String>): String {
        require(args.size >= 2 && args[1] == "bass") {
            "Usage: generate bass --project <project-directory>"
        }
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve("arrangement.json")
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Arrangement file not found: $arrangementFile" }

        val project = json.decodeFromString<Project>(Files.readString(projectFile))
        project.requireValid(projectRoot)
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(
            Files.readString(arrangementFile)
        )
        val analyses = project.parts.mapNotNull { part ->
            part.analysis?.let { reference ->
                part.id to json.decodeFromString<PartAnalysis>(
                    Files.readString(projectRoot.resolve(reference.file))
                )
            }
        }.toMap()
        val stem = BassStemGenerationAdapter().generate(projectRoot, project, arrangement, analyses)
        return "Generated bass stem: ${stem.path} (${stem.sampleRate} Hz, ${stem.channels} ch, ${stem.frameCount} frames)"
    }

    private fun mixStems(args: Array<String>): String {
        val options = parseMixOptions(args.drop(1))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve("arrangement.json")
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Arrangement file not found: $arrangementFile" }

        val project = json.decodeFromString<Project>(Files.readString(projectFile))
        project.requireValid(projectRoot)
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(
            Files.readString(arrangementFile)
        )
        arrangement.requireValid(project.parts.map { it.id })
        require(arrangement.sections.isNotEmpty()) { "Arrangement must contain at least one section to mix" }

        val decoder = WAVDecoder(NoOpErrorReporter)
        val decodedSources = arrangement.sections.map { section ->
            val part = project.parts.first { it.id == section.partId }
            val source = projectRoot.resolve(part.file)
            require(source.fileName.toString().substringAfterLast('.', "").lowercase() in WAV_EXTENSIONS) {
                "Simple stem mixing currently supports WAV source parts: ${part.file}"
            }
            section to decoder.decode(source)
        }
        val targetSampleRate = decodedSources.first().second.format.sampleRate
        var startFrame = 0
        val sourceTracks = decodedSources.map { (section, decoded) ->
            val buffer = AudioResampler.resample(decoded, targetSampleRate)
            val track = MixTrack(
                name = "source-${section.index}-${section.partId}",
                buffer = buffer,
                startFrame = startFrame
            )
            startFrame = Math.addExact(startFrame, buffer.length)
            track
        }
        val bassPath = projectRoot.resolve("stems/bass.wav")
        val generatedTracks = if (Files.isRegularFile(bassPath)) {
            listOf(MixTrack("bass", decoder.decode(bassPath), generated = true))
        } else {
            emptyList()
        }
        val mixer = DeterministicStemMixer()
        val mixed = mixer.mix(
            sourceTracks + generatedTracks,
            MixSettings(targetSampleRate = targetSampleRate, dry = "--dry" in options)
        )
        val output = mixer.writeWav(mixed, projectRoot.resolve("mix/mix.wav"))
        return "Created ${if ("--dry" in options) "dry " else ""}mix: $output"
    }

    /**
     * Creates one local arrangement from a validated project. Each lossless
     * intermediate stays in the project directory so a failed later stage can
     * be diagnosed or resumed without touching a source part.
     */
    private suspend fun buildProject(args: Array<String>, worker: BuildWorker): String {
        val options = parseBuildOptions(args.drop(1))
        val projectRoot = projectRoot(options.projectPath)
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }

        var project = readProject(projectRoot)
        project.requireValid(projectRoot)
        val structure = project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        require(structure.isNotEmpty()) { "Project structure must contain at least one part" }

        val outputRoot = resolveOutputRoot(projectRoot, options.outputDirectory)
        val repairedPath = outputRoot.resolve("repair.wav")
        val lofiPath = outputRoot.resolve("lofi.wav")
        val masterPath = outputRoot.resolve("master.wav")
        requireNoSourceOverwrite(projectRoot, project, listOf(repairedPath, lofiPath, masterPath))

        if (options.dryRun) {
            return buildString {
                appendLine("[DRY RUN] Project is valid: $projectRoot")
                appendLine("[DRY RUN] Planner: deterministic${if (options.noAi) " (--no-ai)" else ""}")
                appendLine("[DRY RUN] Output directory: $outputRoot")
                append("[DRY RUN] No files were created or changed.")
            }
        }

        require(worker.healthCheck()) {
            "Stage worker health failed: Python worker is not running. Start it with `make worker`."
        }

        val progress = mutableListOf<String>()
        fun complete(stage: Int, text: String) {
            val message = "[$stage/10] $text"
            println("✓ $message")
            progress += message
        }

        complete(1, "Loaded project '${project.name}'")
        complete(2, "Validated ${structure.size} structure sections")

        val missingAnalyses = project.parts.filter { it.analysis == null }
        missingAnalyses.forEach { part ->
            val source = projectRoot.resolve(part.file).normalize()
            val analysis = runBuildStage("Analyze part '${part.id}'") {
                worker.analyze(source)
            }
            runBuildStage("Store analysis for part '${part.id}'") {
                PartAnalysisStore.write(projectRoot, project, part.id, analysis)
            }
            project = readProject(projectRoot)
        }
        complete(3, if (missingAnalyses.isEmpty()) "Reused existing analyses" else "Analyzed ${missingAnalyses.size} missing part(s)")

        runBuildStage("Create deterministic arrangement") {
            val analyses = loadAnalyses(projectRoot, project)
            val arrangement = DeterministicArrangementPlanner().plan(
                ArrangementInput(
                    project = project,
                    analyses = analyses,
                    structure = structure,
                    requestedInstruments = listOf("source", "bass")
                )
            )
            ArrangementStore.write(projectRoot, project, arrangement)
        }
        complete(4, "Created deterministic arrangement")

        runBuildStage("Generate bass stem") {
            val arrangement = readArrangement(projectRoot)
            BassStemGenerationAdapter().generate(projectRoot, project, arrangement, loadAnalyses(projectRoot, project))
        }
        complete(5, "Generated stems/bass.wav")

        runBuildStage("Mix stems") {
            mixStems(arrayOf("mix", "--project", projectRoot.toString()))
        }
        val mixPath = projectRoot.resolve("mix/mix.wav")
        requireWavOutput(mixPath, "Mix")
        complete(6, "Created mix/mix.wav")

        Files.createDirectories(outputRoot)
        runBuildStage("Repair mix") { worker.repair(mixPath, repairedPath) }
        requireWavOutput(repairedPath, "Repair")
        complete(7, "Created ${outputRoot.relativizeOrName(repairedPath)}")

        runBuildStage("Apply LoFi") { applyLoFi(repairedPath, lofiPath) }
        requireWavOutput(lofiPath, "LoFi")
        complete(8, "Created ${outputRoot.relativizeOrName(lofiPath)}")

        runBuildStage("Master audio") { worker.master(lofiPath, masterPath) }
        requireWavOutput(masterPath, "Master")
        complete(9, "Created ${outputRoot.relativizeOrName(masterPath)}")
        complete(10, "Build complete: $masterPath")
        return progress.joinToString(separator = "\n")
    }

    private fun createBuildWorker(): BuildWorker {
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")
                ?.takeIf { it.isNotBlank() }
                ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        return object : BuildWorker {
            override suspend fun healthCheck(): Boolean = client.healthCheck()

            override suspend fun analyze(path: Path): PartAnalysis {
                val response = client.execute(
                    AnalyzeCommand(
                        path = path.toString(),
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
                requireCompleted(response.status, response.error?.message, "analyze")
                return analysisFrom(response.output.orEmpty())
            }

            override suspend fun repair(inputPath: Path, outputPath: Path) {
                val response = client.execute(
                    RepairCommand(
                        path = inputPath.toString(),
                        outputPath = outputPath.toString(),
                        repairs = listOf(
                            RepairSpec("dc_offset"),
                            RepairSpec("clip_removal", mapOf("threshold" to 0.999, "max_run_samples" to 12))
                        )
                    )
                )
                requireCompleted(response.status, response.error?.message, "repair")
            }

            override suspend fun master(inputPath: Path, outputPath: Path) {
                val response = client.execute(
                    MasterCommand(
                        path = inputPath.toString(),
                        outputPath = outputPath.toString(),
                        settings = mapOf(
                            "eq_enabled" to false,
                            "compressor_enabled" to false,
                            "saturation_enabled" to false,
                            "stereo_enabled" to false,
                            "limiter_enabled" to true,
                            "limiter" to mapOf("ceiling_db" to -1.0, "release_ms" to 100.0),
                            "target_peak_db" to -1.0
                        )
                    )
                )
                requireCompleted(response.status, response.error?.message, "master")
            }
        }
    }

    private fun requireCompleted(status: WorkerStatus, error: String?, operation: String) {
        require(status == WorkerStatus.COMPLETED) {
            "Worker $operation failed: ${error ?: "Unknown worker error"}"
        }
    }

    private fun applyLoFi(inputPath: Path, outputPath: Path) {
        val input = WAVDecoder(NoOpErrorReporter).decode(inputPath)
        val preset = LOFIPresets.DEFAULT_PRESETS.first()
        val processed = DSPChain.createDefaultChain(
            settings = preset.settings,
            sampleRate = input.format.sampleRate,
            channels = input.format.channels
        ).process(input)
        Files.createDirectories(checkNotNull(outputPath.parent))
        WAVExporterSimple().export(processed, outputPath)
    }

    private fun loadAnalyses(projectRoot: Path, project: Project): Map<String, PartAnalysis> =
        project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) {
                "Missing analysis for part '${part.id}' after analysis stage"
            }
            part.id to json.decodeFromString<PartAnalysis>(Files.readString(projectRoot.resolve(reference.file)))
        }

    private fun readProject(projectRoot: Path): Project =
        json.decodeFromString(Files.readString(projectRoot.resolve(PROJECT_FILE)))

    private fun readArrangement(projectRoot: Path): ai.music.workstation.arrangement.Arrangement =
        json.decodeFromString(Files.readString(projectRoot.resolve("arrangement.json")))

    private fun parseBuildOptions(arguments: List<String>): BuildOptions {
        var projectPath: String? = null
        var outputDirectory: String? = null
        var noAi = false
        var dryRun = false
        var index = 0
        while (index < arguments.size) {
            when (val option = arguments[index]) {
                "--project" -> {
                    require(projectPath == null && index + 1 < arguments.size) { "Missing or duplicate value for --project" }
                    projectPath = arguments[++index]
                }
                "--output-dir" -> {
                    require(outputDirectory == null && index + 1 < arguments.size) { "Missing or duplicate value for --output-dir" }
                    outputDirectory = arguments[++index]
                }
                "--no-ai" -> {
                    require(!noAi) { "Duplicate build option: --no-ai" }
                    noAi = true
                }
                "--dry-run" -> {
                    require(!dryRun) { "Duplicate build option: --dry-run" }
                    dryRun = true
                }
                else -> throw IllegalArgumentException("Unknown build option: $option")
            }
            index++
        }
        require(projectPath != null) {
            "Usage: build --project <project-directory> [--output-dir <directory>] [--no-ai] [--dry-run]"
        }
        return BuildOptions(projectPath, outputDirectory, noAi, dryRun)
    }

    private fun resolveOutputRoot(projectRoot: Path, configuredPath: String?): Path {
        val path = configuredPath?.let(Path::of)
        return (if (path == null) projectRoot.resolve("output") else if (path.isAbsolute) path else projectRoot.resolve(path))
            .toAbsolutePath()
            .normalize()
    }

    private fun requireNoSourceOverwrite(projectRoot: Path, project: Project, outputs: List<Path>) {
        val sources = project.parts.map { projectRoot.resolve(it.file).normalize() }.toSet()
        val conflictingOutput = outputs.firstOrNull { it in sources }
        require(conflictingOutput == null) {
            "Build output would overwrite a source audio file: $conflictingOutput"
        }
    }

    private fun requireWavOutput(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 44) {
            "$stage stage did not create a valid WAV file: $path"
        }
        val header = Files.newInputStream(path).use { input -> input.readNBytes(12).decodeToString() }
        require(header.startsWith("RIFF") && header.endsWith("WAVE")) {
            "$stage stage did not create a WAV container: $path"
        }
    }

    private fun Path.relativizeOrName(path: Path): String =
        try { relativize(path).toString() } catch (_: IllegalArgumentException) { path.fileName.toString() }

    private suspend fun <T> runBuildStage(name: String, action: suspend () -> T): T =
        try {
            action()
        } catch (exception: Exception) {
            throw IllegalStateException("$name failed: ${exception.message}", exception)
        }

    private data class BuildOptions(
        val projectPath: String,
        val outputDirectory: String?,
        val noAi: Boolean,
        val dryRun: Boolean
    )

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

    private fun parseArrangeOptions(arguments: List<String>): Map<String, String> {
        val options = mutableMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in ARRANGE_OPTIONS) { "Unknown arrange option: $option" }
            require(index + 1 < arguments.size) { "Missing value for $option" }
            require(option !in options) { "Duplicate arrange option: $option" }
            options[option] = arguments[index + 1]
            index += 2
        }
        require("--project" in options) {
            "Usage: arrange --project <project-directory> [--planner deterministic|qwen] [--structure <structure>] [--instruments <comma-separated>] [--style <style>]"
        }
        return options
    }

    private fun parseGenerateOptions(arguments: List<String>): Map<String, String> {
        require(arguments.size == 2 && arguments[0] == "--project") {
            "Usage: generate bass --project <project-directory>"
        }
        return mapOf("--project" to arguments[1])
    }

    private fun parseMixOptions(arguments: List<String>): Map<String, String> {
        require(arguments.isNotEmpty() && arguments[0] == "--project") {
            "Usage: mix --project <project-directory> [--dry]"
        }
        require(arguments.size in 2..3) { "Usage: mix --project <project-directory> [--dry]" }
        require(arguments.size == 2 || arguments[2] == "--dry") {
            "Unknown mix option: ${arguments.getOrNull(2)}"
        }
        return buildMap {
            put("--project", arguments[1])
            if (arguments.size == 3) put("--dry", "")
        }
    }

    private fun parseInstrumentList(value: String?): List<String> {
        if (value == null) return emptyList()
        val instruments = value.split(',').map { it.trim() }
        require(instruments.none { it.isEmpty() }) {
            "Instruments must be a comma-separated list of non-blank names"
        }
        return instruments
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
    private val ARRANGE_OPTIONS = setOf("--project", "--planner", "--structure", "--instruments", "--style")
    private const val DETERMINISTIC_PLANNER = "deterministic"
    private const val QWEN_PLANNER = "qwen"
    private val WAV_EXTENSIONS = setOf("wav", "wave")

    private object NoOpErrorReporter : ai.music.workstation.model.ErrorReporter {
        override fun report(message: String) = Unit
        override fun report(message: String, cause: Throwable) = Unit
    }
}
