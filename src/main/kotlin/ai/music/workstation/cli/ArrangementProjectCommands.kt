package ai.music.workstation.cli

import ai.music.workstation.arrangement.ArrangementInput
import ai.music.workstation.arrangement.ArrangementStore
import ai.music.workstation.arrangement.ArrangementRenderer
import ai.music.workstation.arrangement.BassMidiGenerationAdapter
import ai.music.workstation.arrangement.DeterministicArrangementPlanner
import ai.music.workstation.arrangement.DeterministicGlobalSongPlanner
import ai.music.workstation.arrangement.DeterministicSectionVariationPlanner
import ai.music.workstation.arrangement.DeterministicStemMixer
import ai.music.workstation.arrangement.LocalQwenGlobalSongPlanner
import ai.music.workstation.arrangement.MixSettings
import ai.music.workstation.arrangement.MixTrack
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.PartAnalysisStore
import ai.music.workstation.arrangement.MidiReferences
import ai.music.workstation.arrangement.MidiAnalysisStore
import ai.music.workstation.arrangement.MidiAnalysis
import ai.music.workstation.arrangement.MidiPartAnalyzer
import ai.music.workstation.arrangement.InstrumentRegistryLoader
import ai.music.workstation.arrangement.LogicalInstrument
import ai.music.workstation.arrangement.Arrangement
import ai.music.workstation.arrangement.AnalysisKind
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import ai.music.workstation.arrangement.SectionInstance
import ai.music.workstation.arrangement.SectionVariationStore
import ai.music.workstation.arrangement.StructureParser
import ai.music.workstation.arrangement.GlobalSongPlanner
import ai.music.workstation.arrangement.SongPlanStore
import ai.music.workstation.arrangement.SongPlanningInput
import ai.music.workstation.audio.WAVDecoder
import ai.music.workstation.audio.AudioResampler
import ai.music.workstation.dsp.DSPChain
import ai.music.workstation.dsp.LOFIPresets
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.MasterCommand
import ai.music.workstation.worker.MP3ExportCommand
import ai.music.workstation.worker.RepairCommand
import ai.music.workstation.worker.RepairSpec
import ai.music.workstation.worker.TranscribeCommand
import ai.music.workstation.worker.MidiCleanCommand
import ai.music.workstation.worker.WorkerClient
import ai.music.workstation.worker.WorkerStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption

/**
 * Small file-based commands for the arranger project format.
 *
 * This deliberately does not share the web application's project storage:
 * arranger projects are self-contained directories with project.json and
 * copied source files under parts/.
 */
object ArrangementProjectCommands {
    private const val PROJECT_FILE = "project.json"
    private val supportedExtensions = setOf("mid", "midi", "wav", "wave", "mp3")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun handles(args: Array<String>): Boolean =
        args.firstOrNull() in setOf("project", "part", "arrange", "generate", "mix", "render", "preview", "approve", "build", "transcribe", "midi-clean", "licenses")

    /** Small boundary that lets the end-to-end command be tested without a running HTTP worker. */
    internal interface BuildWorker {
        suspend fun healthCheck(): Boolean
        suspend fun analyze(path: Path): PartAnalysis
        suspend fun repair(inputPath: Path, outputPath: Path)
        suspend fun master(inputPath: Path, outputPath: Path)
        suspend fun exportMp3(inputPath: Path, outputPath: Path): Boolean = false
    }

    /** Testable boundary for the two worker calls used while importing a part. */
    internal interface MidiPreparationWorker {
        suspend fun transcribe(input: Path, output: Path)
        suspend fun clean(input: Path, output: Path)
    }

    fun execute(args: Array<String>): String = runBlocking {
        executeAsync(args)
    }

    suspend fun executeAsync(args: Array<String>): String = when (args.firstOrNull()) {
        "project" -> createProject(args)
        "part" -> when (args.getOrNull(1)) {
            "add" -> addPart(args, createMidiPreparationWorker())
            "analyze" -> analyzePart(args)
            else -> throw IllegalArgumentException(
                "Usage: part add <project-directory> ... or part analyze <project-directory> --id <id>"
            )
        }
        "arrange" -> arrange(args)
        "generate" -> generateBass(args)
        "mix", "render" -> mixStems(args)
        "preview" -> previewDraft(args)
        "approve" -> approveDraft(args)
        "build" -> buildProject(args, createBuildWorker())
        "transcribe" -> transcribe(args)
        "midi-clean" -> midiClean(args)
        "licenses" -> licenses(args)
        else -> throw IllegalArgumentException("Unknown arranger command")
    }

    internal fun executeBuildForTest(args: Array<String>, worker: BuildWorker): String = runBlocking {
        buildProject(args, worker)
    }

    internal fun executePartAddForTest(args: Array<String>, worker: MidiPreparationWorker): String = runBlocking {
        addPart(args, worker)
    }

    private suspend fun transcribe(args: Array<String>): String {
        val options = parseTranscribeOptions(args.drop(1))
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        val response = client.execute(
            TranscribeCommand(options.input.toString(), options.output.toString(), options.instrument)
        )
        if (response.status != WorkerStatus.COMPLETED) {
            throw IllegalArgumentException(transcriptionFailureMessage(response.error))
        }
        val output = response.output.orEmpty()
        val notes = output["notes"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("Transcription failed during MIDI output validation: worker returned no note count")
        val duration = output["duration"]?.jsonPrimitive?.doubleOrNull
            ?: throw IllegalArgumentException("Transcription failed during MIDI output validation: worker returned no duration")
        val engine = output["engine"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val engineVersion = output["engineVersion"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val outputPath = output["output"]?.jsonPrimitive?.contentOrNull ?: options.output.toString()
        return "Transcribed ${options.input} -> $outputPath (${notes} notes, ${"%.2f".format(duration)}s, $engine $engineVersion)"
    }

    private suspend fun midiClean(args: Array<String>): String {
        val options = parseMidiCleanOptions(args.drop(1))
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        val response = client.execute(
            MidiCleanCommand(
                path = options.input.toString(),
                outputPath = options.output.toString(),
                quantize = options.quantize,
                strength = options.strength,
                minNoteMs = options.minNoteMs,
                minVelocity = options.minVelocity,
                normalizeVelocity = options.normalizeVelocity,
                cleanSustain = options.cleanSustain
            )
        )
        if (response.status != WorkerStatus.COMPLETED) {
            throw IllegalArgumentException(midiCleanupFailureMessage(response.error))
        }
        val output = response.output.orEmpty()
        fun stat(name: String): Long = output[name]?.jsonPrimitive?.longOrNull
            ?: throw IllegalArgumentException("MIDI cleanup failed during output validation: worker returned no $name")
        val outputPath = output["output"]?.jsonPrimitive?.contentOrNull ?: options.output.toString()
        return "Cleaned ${options.input} -> $outputPath (notes ${stat("inputNoteCount")} -> ${stat("outputNoteCount")}, " +
            "duplicates ${stat("duplicatesRemoved")}, short ${stat("shortNotesRemoved")}, " +
            "low velocity ${stat("lowVelocityNotesRemoved")}, overlaps ${stat("overlapsRepaired")}, " +
            "quantized ${stat("quantizedNotes")})"
    }

    internal fun transcriptionFailureMessage(error: ai.music.workstation.worker.WorkerError?): String {
        val stage = when (error?.type) {
            "ValidationError" -> "validation"
            "DecodeError" -> "MP3 decode"
            "ModelError" -> "model inference"
            "OutputValidationError" -> "MIDI output validation"
            else -> "worker"
        }
        return "Transcription failed during $stage: ${error?.message ?: "Unknown worker error"}"
    }

    internal fun midiCleanupFailureMessage(error: ai.music.workstation.worker.WorkerError?): String {
        val stage = when (error?.type) {
            "MidiCleanupValidationError" -> "validation"
            "MidiCleanupOutputValidationError" -> "MIDI output validation"
            else -> "worker"
        }
        return "MIDI cleanup failed during $stage: ${error?.message ?: "Unknown worker error"}"
    }

    private fun createMidiPreparationWorker(): MidiPreparationWorker {
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        return object : MidiPreparationWorker {
            override suspend fun transcribe(input: Path, output: Path) {
                Files.createDirectories(checkNotNull(output.parent))
                val response = client.execute(TranscribeCommand(input.toString(), output.toString(), "piano"))
                require(response.status == WorkerStatus.COMPLETED) { transcriptionFailureMessage(response.error) }
                requireMidiArtifact(output, "Transcription")
            }

            override suspend fun clean(input: Path, output: Path) {
                Files.createDirectories(checkNotNull(output.parent))
                val response = client.execute(MidiCleanCommand(input.toString(), output.toString()))
                require(response.status == WorkerStatus.COMPLETED) { midiCleanupFailureMessage(response.error) }
                requireMidiArtifact(output, "MIDI cleanup")
            }
        }
    }

    private fun createProject(args: Array<String>): String {
        require(args.size >= 3 && args[1] == "create") { "Usage: project create <project-directory> [--sample-rate 44100] [--channels 2]" }
        val format = parseCreateOptions(args.drop(3))

        val projectRoot = projectRoot(args[2])
        require(!Files.exists(projectRoot) || Files.isDirectory(projectRoot)) {
            "Project path is not a directory: $projectRoot"
        }

        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(!Files.exists(projectFile)) {
            "Project already exists: $projectFile"
        }

        listOf("source", "midi/raw", "midi/clean", "midi/generated").forEach { Files.createDirectories(projectRoot.resolve(it)) }
        val name = projectRoot.fileName?.toString().orEmpty()
        require(name.isNotBlank()) { "Project directory must have a name" }
        ProjectStore.create(projectRoot, name, format)

        return "Created project: $projectRoot"
    }

    private suspend fun addPart(args: Array<String>, worker: MidiPreparationWorker): String {
        require(args.size >= 3 && args[1] == "add") {
            "Usage: part add <project-directory> --id <id> --file <midi-or-audio-file> [--role <role>] [--transcribe]"
        }

        val projectRoot = projectRoot(args[2])
        val options = parseAddOptions(args.drop(3))
        val id = options.getValue("--id")
        require(PART_ID.matches(id)) {
            "Part ID must contain only letters, numbers, underscores, or hyphens: $id"
        }

        val source = Path.of(options.getValue("--file")).toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Input file not found: $source" }
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in supportedExtensions) {
            "Unsupported input file extension: ${if (extension.isEmpty()) "(none)" else extension}"
        }
        val isMidi = extension in MIDI_EXTENSIONS
        val transcribe = "--transcribe" in options
        require(!(isMidi && transcribe)) { "--transcribe is only valid for audio input" }

        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        require(project.parts.none { it.id == id }) { "Part ID already exists: $id" }

        // Keep the old v1 import behavior readable for existing projects. New
        // v2 projects are MIDI-first and require transcription for audio.
        if (project.version == 1 && !isMidi && !transcribe) {
            val relativeFile = "parts/$id.$extension"
            val destination = safeDestination(projectRoot, relativeFile)
            require(source != destination) { "Input and destination paths must differ" }
            require(!Files.exists(destination)) { "Part destination already exists: $destination" }
            Files.createDirectories(destination.parent)
            Files.copy(source, destination)
            ProjectStore.write(projectRoot, project.copy(parts = project.parts + Part(id, relativeFile, options["--role"].orEmpty())))
            return "Added legacy audio part '$id' to $projectRoot. Transcribe it before MIDI-only processing."
        }
        require(isMidi || transcribe) { "Audio input requires --transcribe so a clean MIDI artifact can be prepared" }

        val relativeFile = "source/$id.$extension"
        val destination = safeDestination(projectRoot, relativeFile)
        require(source != destination && !(Files.exists(destination) && Files.isSameFile(source, destination))) { "Input and destination paths must differ" }
        require(!Files.exists(destination)) { "Part destination already exists: $destination" }

        Files.createDirectories(destination.parent)
        Files.copy(source, destination)

        val raw = if (isMidi) null else "midi/raw/$id.mid"
        val clean = "midi/clean/$id.mid"
        try {
            if (raw != null) worker.transcribe(destination, safeDestination(projectRoot, raw))
            worker.clean(safeDestination(projectRoot, raw ?: relativeFile), safeDestination(projectRoot, clean))
            requireMidiArtifact(projectRoot.resolve(clean), "MIDI cleanup")
        } catch (exception: Exception) {
            throw IllegalStateException("Part '$id' was not registered. Source preserved at $destination; unregistered MIDI artifacts remain for diagnosis: ${raw ?: "none"}, $clean. ${exception.message}", exception)
        }

        val part = Part(id, relativeFile, options["--role"].orEmpty(), midi = MidiReferences(raw, clean))
        val updated = project.copy(parts = project.parts + part)
        if (project.version == 1) ProjectStore.upgrade(projectRoot, project, updated.parts) else ProjectStore.write(projectRoot, updated)

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

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val part = project.parts.find { it.id == id }
            ?: throw IllegalArgumentException("Part not found: $id")
        if (project.version == Project.CURRENT_VERSION) {
            val cleanMidi = projectRoot.resolve(requireNotNull(part.midi).clean).normalize()
            val analysisPath = MidiAnalysisStore.write(projectRoot, project, id, MidiPartAnalyzer().analyze(cleanMidi, id))
            return "Analyzed MIDI part '$id': $analysisPath"
        }
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
                    detectBPM = true,
                    detectKey = true,
                    detectLoudness = true,
                    detectOnsets = true,
                    detectBeats = true,
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

    private fun licenses(args: Array<String>): String {
        require(args.size in 2..3 && (args.size == 2 || args[2] == "--commercial")) {
            "Usage: licenses <project-directory> [--commercial]"
        }
        val root = projectRoot(args[1])
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val arrangementPath = listOf(root.resolve("arrangement.json"), root.resolve(ArrangementStore.DRAFT_FILE)).firstOrNull(Files::isRegularFile)
            ?: throw IllegalArgumentException("No approved or draft arrangement found in $root")
        val arrangement = json.decodeFromString<Arrangement>(Files.readString(arrangementPath, StandardCharsets.UTF_8))
        arrangement.requireValid(project.parts.map { it.id })
        val used = arrangement.sections.flatMap { it.instruments }.map { it.name }.filterNot { it == "source" }.distinct().sorted()
        val registry = InstrumentRegistryLoader().load()
        val commercial = args.getOrNull(2) == "--commercial"
        val lines = used.map { name ->
            val descriptor = try { registry.resolve(name) } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Arrangement uses unverified instrument '$name': ${error.message}", error)
            }
            val license = descriptor.license
            if (commercial && !license.commercialUse) throw IllegalArgumentException("Instrument '$name' is not licensed for commercial export")
            "$name: license=${descriptor.license.displayName}; commercialUse=${license.commercialUse}; attribution=${if (license.attributionRequired) license.attributionText else "none"}"
        }
        return if (lines.isEmpty()) "No generated logical instruments require a sound-library license." else lines.joinToString("\n")
    }

    private fun arrange(args: Array<String>): String {
        val options = parseArrangeOptions(args.drop(1))
        val plannerName = options["--planner"] ?: DETERMINISTIC_PLANNER
        val planner = globalPlannerFor(plannerName)

        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)

        val structure = options["--structure"]?.let { StructureParser.parse(it, project) }
            ?: project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        val availableInstruments = LogicalInstrument.entries.map { it.wireName }
        val requestedInstruments = parseInstrumentList(options["--instruments"])
        val allowedInstruments = if (requestedInstruments.isEmpty()) availableInstruments else requestedInstruments
        require(allowedInstruments.all { it in availableInstruments }) {
            "Requested instruments must be available in the local sound library: ${availableInstruments.joinToString(", ")}"
        }
        val partsById = project.parts.associateBy { it.id }
        val analyses = structure.map { it.partId }.distinct().associateWith { partId ->
            val part = checkNotNull(partsById[partId]) { "Structure references unknown part '$partId'" }
            val reference = requireNotNull(part.analysis) {
                "Missing MIDI analysis for part '${part.id}'. Run part analyze first."
            }
            require(reference.kind == AnalysisKind.MIDI) {
                "Global song planning requires MIDI analysis for part '${part.id}'. Run part analyze after MIDI cleanup."
            }
            json.decodeFromString<MidiAnalysis>(
                Files.readString(projectRoot.resolve(reference.file), StandardCharsets.UTF_8)
            )
        }
        val input = SongPlanningInput(
            projectName = project.name,
            projectVersion = project.version,
            analyses = analyses,
            structure = structure,
            allowedInstruments = allowedInstruments,
            style = options["--style"]
        )
        val songPlan = planner.plan(input)
        val songPlanPath = SongPlanStore.write(projectRoot, input, songPlan)
        val variationPath = SectionVariationStore.write(
            projectRoot,
            input,
            songPlan,
            DeterministicSectionVariationPlanner.plan(input, songPlan)
        )
        return if (plannerName == QWEN_PLANNER) {
            "Created Qwen global song plan: $songPlanPath and section variations: $variationPath. Review both before creating a detailed arrangement."
        } else {
            "Created $plannerName global song plan: $songPlanPath and section variations: $variationPath"
        }
    }

    private fun globalPlannerFor(name: String): GlobalSongPlanner = when (name) {
        DETERMINISTIC_PLANNER -> DeterministicGlobalSongPlanner()
        QWEN_PLANNER -> LocalQwenGlobalSongPlanner()
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

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(
            Files.readString(arrangementFile)
        )
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Bass MIDI generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<ai.music.workstation.arrangement.MidiAnalysis>(
                Files.readString(projectRoot.resolve(reference.file))
            )
        }
        val bass = BassMidiGenerationAdapter().generate(projectRoot, project, arrangement, analyses)
        val suffix = if (bass.diagnostics.isEmpty()) "" else "; ${bass.diagnostics.joinToString(" ")}"
        return "Generated bass MIDI: ${bass.path} (${bass.notes.size} notes)$suffix"
    }

    private fun mixStems(args: Array<String>): String {
        val options = parseMixOptions(args.drop(1))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve("arrangement.json")
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Arrangement file not found: $arrangementFile" }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(
            Files.readString(arrangementFile)
        )
        arrangement.requireValid(project.parts.map { it.id })
        require(arrangement.sections.isNotEmpty()) { "Arrangement must contain at least one section to mix" }

        val rendered = renderProject(projectRoot, project, arrangement)
        val renderedBass = projectRoot.resolve("stems/bass.wav").takeIf(Files::isRegularFile)?.let { path ->
            MixTrack("bass", WAVDecoder(NoOpErrorReporter).decode(path), generated = true)
        }
        val tracks = rendered.tracks + listOfNotNull(renderedBass)
        val mixer = DeterministicStemMixer()
        val mixed = mixer.mix(
            tracks,
            MixSettings(targetSampleRate = rendered.sampleRate, dry = "--dry" in options)
        )
        Files.createDirectories(projectRoot.resolve("stems"))
        rendered.tracks.filter { it.generated }.forEach { track ->
            mixer.writeWav(mixer.mix(listOf(track), MixSettings(targetSampleRate = rendered.sampleRate)), projectRoot.resolve("stems/${track.name}.wav"))
        }
        val output = mixer.writeWav(mixed, projectRoot.resolve("mix/mix.wav"))
        return "Created ${if ("--dry" in options) "dry " else ""}mix: $output (${rendered.boundaries.size} rendered transitions)"
    }

    private fun previewDraft(args: Array<String>): String {
        val projectRoot = parseProjectOnly(args, "preview")
        val project = readProject(projectRoot)
        project.requireValid(projectRoot)
        val draftPath = projectRoot.resolve(ArrangementStore.DRAFT_FILE)
        require(Files.isRegularFile(draftPath)) { "Arrangement draft not found: $draftPath. Run arrange --planner qwen first." }
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(Files.readString(draftPath))
        arrangement.requireValid(project.parts.map { it.id })
        val rendered = renderProject(projectRoot, project, arrangement)
        val mixer = DeterministicStemMixer()
        val mixed = mixer.mix(rendered.tracks, MixSettings(targetSampleRate = rendered.sampleRate))
        val previewDir = projectRoot.resolve("previews")
        Files.createDirectories(previewDir)
        rendered.boundaries.forEach { boundary ->
            val before = rendered.sampleRate * 2
            val from = maxOf(0, boundary.startFrame - before)
            val until = minOf(mixed.buffer.length, boundary.endFrame + before)
            val samples = mixed.buffer.samples.copyOfRange(from * mixed.buffer.format.channels, until * mixed.buffer.format.channels)
            mixer.writeWav(
                ai.music.workstation.arrangement.MixedStem(mixed.buffer.copy(samples = samples, duration = (until - from).toDouble() / rendered.sampleRate), mixed.includedTracks),
                previewDir.resolve("boundary-${boundary.index + 1}.wav")
            )
        }
        Files.writeString(previewDir.resolve("manifest.txt"), rendered.boundaries.joinToString("\n") { "${it.index + 1}: ${it.description}" }, StandardCharsets.UTF_8)
        return "Created ${rendered.boundaries.size} boundary previews in $previewDir. Listen to them, then run `approve --project $projectRoot`."
    }

    private fun approveDraft(args: Array<String>): String {
        val projectRoot = parseProjectOnly(args, "approve")
        val project = readProject(projectRoot)
        val draft = projectRoot.resolve(ArrangementStore.DRAFT_FILE)
        require(Files.isRegularFile(draft)) { "Arrangement draft not found: $draft" }
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(Files.readString(draft))
        arrangement.requireValid(project.parts.map { it.id })
        val temp = projectRoot.resolve("arrangement.approving.json")
        Files.copy(draft, temp, StandardCopyOption.REPLACE_EXISTING)
        Files.move(temp, projectRoot.resolve("arrangement.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return "Approved arrangement: ${projectRoot.resolve("arrangement.json")}"
    }

    private fun renderProject(projectRoot: Path, project: Project, arrangement: ai.music.workstation.arrangement.Arrangement): ArrangementRenderer.RenderedArrangement {
        val decoder = WAVDecoder(NoOpErrorReporter)
        val sources = arrangement.sections.map { section ->
            val part = project.parts.first { it.id == section.partId }
            require(part.file.substringAfterLast('.', "").lowercase() in WAV_EXTENSIONS) { "Rendering requires WAV source parts: ${part.file}" }
            decoder.decode(projectRoot.resolve(part.file))
        }
        return ArrangementRenderer().render(arrangement, sources, loadAnalysesIfPresent(projectRoot, project))
    }

    private fun parseProjectOnly(args: Array<String>, command: String): Path {
        require(args.size == 3 && args[1] == "--project") { "Usage: $command --project <project-directory>" }
        return projectRoot(args[2])
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
        val mp3Path = outputRoot.resolve("youtube.mp3")
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

        val approvedArrangement = projectRoot.resolve("arrangement.json")
        if (!Files.isRegularFile(approvedArrangement)) {
            runBuildStage("Create deterministic arrangement") {
                val analyses = loadAnalyses(projectRoot, project)
                val arrangement = DeterministicArrangementPlanner().plan(
                    ArrangementInput(
                        project = project,
                        analyses = analyses,
                        structure = structure,
                        requestedInstruments = listOf("source", "bass", "drums", "pad")
                    )
                )
                ArrangementStore.write(projectRoot, project, arrangement)
            }
            complete(4, "Created deterministic arrangement")
        } else {
            complete(4, "Reused approved arrangement.json")
        }

        complete(5, "Prepared local generated-instrument render")

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
        val mp3Created = runBuildStage("Export upload-ready MP3") { worker.exportMp3(masterPath, mp3Path) }
        if (mp3Created) {
            require(Files.isRegularFile(mp3Path) && Files.size(mp3Path) > 0) { "MP3 export did not create $mp3Path" }
            Files.writeString(outputRoot.resolve("release.json"), "{\n  \"master\": \"master.wav\",\n  \"youtubeMp3\": \"youtube.mp3\",\n  \"targetLufs\": -14,\n  \"truePeakCeilingDb\": -1\n}\n", StandardCharsets.UTF_8)
            complete(10, "Build complete: $masterPath and $mp3Path")
        } else {
            complete(10, "Build complete: $masterPath (MP3 export unavailable in this worker)")
        }
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
                            detectBPM = true,
                            detectKey = true,
                            detectLoudness = true,
                            detectOnsets = true,
                            detectBeats = true,
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
                            "eq_enabled" to true,
                            "eq" to mapOf("bands" to listOf(mapOf("type" to "lowshelf", "frequency" to 180.0, "gain" to 1.5))),
                            "compressor_enabled" to true,
                            "compressor" to mapOf("threshold_db" to -18.0, "ratio" to 2.0, "attack_ms" to 15.0, "release_ms" to 150.0),
                            "saturation_enabled" to true,
                            "saturation" to mapOf("drive" to 1.08, "mix" to 0.08),
                            "stereo_enabled" to false,
                            "limiter_enabled" to true,
                            "limiter" to mapOf("ceiling_db" to -1.0, "release_ms" to 100.0),
                            "target_peak_db" to -1.0,
                            "target_lufs" to -14.0
                        )
                    )
                )
                requireCompleted(response.status, response.error?.message, "master")
            }

            override suspend fun exportMp3(inputPath: Path, outputPath: Path): Boolean {
                val response = client.execute(MP3ExportCommand(inputPath.toString(), outputPath.toString()))
                if (response.status == WorkerStatus.COMPLETED) return true
                val message = response.error?.message.orEmpty()
                if (message.contains("requires lameenc")) return false
                throw IllegalStateException("Worker mp3 export failed: $message")
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
        val preset = checkNotNull(LOFIPresets.getByName("Bedroom LoFi"))
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

    private fun loadAnalysesIfPresent(projectRoot: Path, project: Project): Map<String, PartAnalysis> =
        project.parts.mapNotNull { part ->
            part.analysis?.takeIf { it.kind != AnalysisKind.MIDI }?.let { reference ->
                val path = projectRoot.resolve(reference.file)
                if (Files.isRegularFile(path)) part.id to json.decodeFromString<PartAnalysis>(Files.readString(path)) else null
            }
        }.toMap()

    private fun readProject(projectRoot: Path): Project = ProjectStore.read(projectRoot)

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
                ?: throw IllegalArgumentException("Worker analysis did not return nearSilence"),
            bpm = output["bpm"]?.jsonPrimitive?.doubleOrNull,
            keyRoot = (output["key"] as? kotlinx.serialization.json.JsonObject)?.get("root")?.jsonPrimitive?.contentOrNull,
            keyMode = (output["key"] as? kotlinx.serialization.json.JsonObject)?.get("mode")?.jsonPrimitive?.contentOrNull,
            keyConfidence = output["keyConfidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            leadingSilenceSeconds = output["leadingSilenceSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            trailingSilenceSeconds = output["trailingSilenceSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            onsetsSeconds = output["onsets"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
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
            if (option == "--transcribe") {
                require(option !in options) { "Duplicate part option: $option" }
                options[option] = ""
                index++
                continue
            }
            require(index + 1 < arguments.size) { "Missing value for $option" }
            require(option !in options) { "Duplicate part option: $option" }
            options[option] = arguments[index + 1]
            index += 2
        }
        require("--id" in options) { "Missing required option: --id" }
        require("--file" in options) { "Missing required option: --file" }
        return options
    }

    private fun parseCreateOptions(arguments: List<String>): RenderFormat {
        var sampleRate = 44_100
        var channels = 2
        var index = 0
        val seen = mutableSetOf<String>()
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in setOf("--sample-rate", "--channels")) { "Unknown project create option: $option" }
            require(seen.add(option)) { "Duplicate project create option: $option" }
            require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) { "Missing value for $option" }
            val value = arguments[index + 1].toIntOrNull()
                ?: throw IllegalArgumentException("$option must be an integer")
            when (option) {
                "--sample-rate" -> sampleRate = value
                "--channels" -> channels = value
            }
            index += 2
        }
        require(sampleRate in 8_000..384_000) { "--sample-rate must be from 8000 to 384000" }
        require(channels in 1..32) { "--channels must be from 1 to 32" }
        return RenderFormat(sampleRate, channels, 24)
    }

    private fun parseAnalyzeOptions(arguments: List<String>): Map<String, String> {
        require(arguments.size == 2 && arguments[0] == "--id") {
            "Usage: part analyze <project-directory> --id <id>"
        }
        return mapOf("--id" to arguments[1])
    }

    internal data class TranscribeOptions(val input: Path, val output: Path, val instrument: String)

    internal data class MidiCleanOptions(
        val input: Path,
        val output: Path,
        val quantize: String?,
        val strength: Double,
        val minNoteMs: Int,
        val minVelocity: Int,
        val normalizeVelocity: Boolean,
        val cleanSustain: Boolean
    )

    internal fun parseTranscribeOptions(arguments: List<String>): TranscribeOptions {
        val values = mutableMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in setOf("--input", "--output", "--instrument")) {
                "Usage: transcribe --input <audio-file> --output <midi-file> --instrument piano"
            }
            require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) {
                "Missing value for $option"
            }
            require(values.put(option, arguments[index + 1]) == null) { "Duplicate option: $option" }
            index += 2
        }
        val rawInput = values["--input"]
            ?: throw IllegalArgumentException("Missing required option: --input <audio-file>")
        val rawOutput = values["--output"]
            ?: throw IllegalArgumentException("Missing required option: --output <midi-file>")
        val instrument = values["--instrument"]
            ?: throw IllegalArgumentException("Missing required option: --instrument piano")
        val input = Path.of(rawInput).toAbsolutePath().normalize()
        val output = Path.of(rawOutput).toAbsolutePath().normalize()
        require(Files.isRegularFile(input)) { "Input audio file not found: $input" }
        require(input != output && !(Files.exists(output) && Files.isSameFile(input, output))) {
            "Input and output paths must differ"
        }
        require(input.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("wav", "wave", "mp3")) {
            "Input must use a .wav, .wave, or .mp3 extension"
        }
        require(output.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("mid", "midi")) {
            "Output must use a .mid or .midi extension"
        }
        require(instrument.lowercase() == "piano") { "Unsupported instrument: $instrument. Supported instruments: piano" }
        require(!Files.isDirectory(output)) { "Output path is a directory: $output" }
        return TranscribeOptions(input, output, instrument.lowercase())
    }

    internal fun parseMidiCleanOptions(arguments: List<String>): MidiCleanOptions {
        val valueOptions = setOf("--input", "--output", "--quantize", "--strength", "--min-note-ms", "--min-velocity")
        val flagOptions = setOf("--normalize-velocity", "--clean-sustain")
        val values = mutableMapOf<String, String>()
        val flags = mutableSetOf<String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in valueOptions || option in flagOptions) {
                "Usage: midi-clean --input <raw.mid> --output <clean.mid> [--quantize 1/16 --strength 0.4]"
            }
            if (option in flagOptions) {
                require(flags.add(option)) { "Duplicate option: $option" }
                index++
            } else {
                require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) {
                    "Missing value for $option"
                }
                require(values.put(option, arguments[index + 1]) == null) { "Duplicate option: $option" }
                index += 2
            }
        }
        val rawInput = values["--input"]
            ?: throw IllegalArgumentException("Missing required option: --input <raw.mid>")
        val rawOutput = values["--output"]
            ?: throw IllegalArgumentException("Missing required option: --output <clean.mid>")
        val input = Path.of(rawInput).toAbsolutePath().normalize()
        val output = Path.of(rawOutput).toAbsolutePath().normalize()
        require(Files.isRegularFile(input)) { "Input MIDI file not found: $input" }
        require(input != output && !(Files.exists(output) && Files.isSameFile(input, output))) {
            "Input and output paths must differ"
        }
        require(input.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("mid", "midi")) {
            "Input must use a .mid or .midi extension"
        }
        require(output.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("mid", "midi")) {
            "Output must use a .mid or .midi extension"
        }
        require(!Files.isDirectory(output)) { "Output path is a directory: $output" }
        val quantize = values["--quantize"]
        require(quantize == null || quantize in setOf("1/4", "1/8", "1/16", "1/32")) {
            "Quantize must be one of: 1/4, 1/8, 1/16, 1/32"
        }
        val strength = values["--strength"]?.toDoubleOrNull()
            ?: if (quantize != null) 0.4 else 0.0
        require(strength in 0.0..1.0) { "Strength must be from 0.0 to 1.0" }
        require(quantize != null || strength == 0.0) { "--strength requires --quantize" }
        val minNoteMs = values["--min-note-ms"]?.toIntOrNull() ?: 50
        val minVelocity = values["--min-velocity"]?.toIntOrNull() ?: 8
        require(minNoteMs in 0..60_000) { "--min-note-ms must be from 0 to 60000" }
        require(minVelocity in 0..127) { "--min-velocity must be from 0 to 127" }
        return MidiCleanOptions(
            input, output, quantize, strength, minNoteMs, minVelocity,
            "--normalize-velocity" in flags, "--clean-sustain" in flags
        )
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

    private fun safeDestination(projectRoot: Path, reference: String): Path {
        val relative = Path.of(reference)
        require(!relative.isAbsolute && reference.isNotBlank()) { "Project destination must be relative: $reference" }
        val root = projectRoot.toAbsolutePath().normalize()
        val destination = root.resolve(relative).normalize()
        require(destination.startsWith(root)) { "Project destination escapes the project root: $reference" }
        val realRoot = root.toRealPath()
        var existing = destination.parent
        while (existing != null && !Files.exists(existing)) existing = existing.parent
        if (existing != null) require(existing.toRealPath().startsWith(realRoot)) {
            "Project destination escapes the project root through a symlink: $reference"
        }
        return destination
    }

    private fun requireMidiArtifact(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "$stage did not create a MIDI file: $path" }
        val header = Files.newInputStream(path).use { it.readNBytes(4).decodeToString() }
        require(header == "MThd") { "$stage did not create a MIDI file: $path" }
    }

    private val PART_ID = Regex("[A-Za-z0-9_-]+")
    private val ADD_OPTIONS = setOf("--id", "--file", "--role", "--transcribe")
    private val ARRANGE_OPTIONS = setOf("--project", "--planner", "--structure", "--instruments", "--style")
    private const val DETERMINISTIC_PLANNER = "deterministic"
    private const val QWEN_PLANNER = "qwen"
    private val WAV_EXTENSIONS = setOf("wav", "wave")
    private val MIDI_EXTENSIONS = setOf("mid", "midi")

    private object NoOpErrorReporter : ai.music.workstation.model.ErrorReporter {
        override fun report(message: String) = Unit
        override fun report(message: String, cause: Throwable) = Unit
    }
}
