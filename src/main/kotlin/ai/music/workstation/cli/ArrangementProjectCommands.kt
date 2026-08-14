package ai.music.workstation.cli

import ai.music.workstation.arrangement.ArrangementInput
import ai.music.workstation.arrangement.ArrangementStore
import ai.music.workstation.arrangement.ArrangementRenderer
import ai.music.workstation.arrangement.BassMidiGenerationAdapter
import ai.music.workstation.arrangement.DrumMidiGenerationAdapter
import ai.music.workstation.arrangement.DeterministicArrangementPlanner
import ai.music.workstation.arrangement.DeterministicGlobalSongPlanner
import ai.music.workstation.arrangement.DeterministicDetailedArrangementPlanner
import ai.music.workstation.arrangement.DeterministicSectionVariationPlanner
import ai.music.workstation.arrangement.DetailedArrangement
import ai.music.workstation.arrangement.DetailedArrangementInput
import ai.music.workstation.arrangement.DetailedArrangementPlanner
import ai.music.workstation.arrangement.DetailedArrangementStore
import ai.music.workstation.arrangement.ArrangementCritic
import ai.music.workstation.arrangement.ArrangementCriticStore
import ai.music.workstation.arrangement.ArrangementCritiqueValidator
import ai.music.workstation.arrangement.DeterministicArrangementCritic
import ai.music.workstation.arrangement.LocalQwenArrangementCritic
import ai.music.workstation.arrangement.DeterministicStemMixer
import ai.music.workstation.arrangement.LocalQwenDetailedArrangementPlanner
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
import ai.music.workstation.arrangement.InstrumentMode
import ai.music.workstation.arrangement.InstrumentRegistryFile
import ai.music.workstation.arrangement.LicenseRegistryFile
import ai.music.workstation.arrangement.LogicalInstrument
import ai.music.workstation.arrangement.Arrangement
import ai.music.workstation.arrangement.AnalysisKind
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import ai.music.workstation.arrangement.SectionInstance
import ai.music.workstation.arrangement.SectionVariationStore
import ai.music.workstation.arrangement.StructureParser
import ai.music.workstation.arrangement.PianoBassQualityGate
import ai.music.workstation.arrangement.PadMidiGenerationAdapter
import ai.music.workstation.arrangement.StringsMidiGenerationAdapter
import ai.music.workstation.arrangement.MidiTransitionGenerationAdapter
import ai.music.workstation.arrangement.InstrumentRenderer
import ai.music.workstation.arrangement.SfizzInstrumentRenderer
import ai.music.workstation.arrangement.StemRenderingMixer
import ai.music.workstation.arrangement.SoundLibraryLocator
import ai.music.workstation.arrangement.SoundLibraryLocation
import ai.music.workstation.arrangement.GlobalSongPlanner
import ai.music.workstation.arrangement.SongPlanStore
import ai.music.workstation.arrangement.SongPlan
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
import ai.music.workstation.application.AnalyzePartRequest
import ai.music.workstation.application.CreateProjectRequest
import ai.music.workstation.application.DefaultProjectApplicationService
import ai.music.workstation.application.ImportPartRequest
import ai.music.workstation.application.LegacyPartAnalysisService
import ai.music.workstation.application.MidiPreparationService
import ai.music.workstation.application.BuildAudioWorker
import ai.music.workstation.application.BuildSongRequest
import ai.music.workstation.application.DefaultArrangementApplicationService
import ai.music.workstation.application.DefaultBuildApplicationService
import ai.music.workstation.application.DefaultMixApplicationService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Small file-based commands for the arranger project format.
 *
 * This deliberately does not share the web application's project storage:
 * arranger projects are self-contained directories with project.json and
 * copied source files under parts/.
 */
object ArrangementProjectCommands {
    private const val PROJECT_FILE = "project.json"
    private const val MASTER_TARGET_LOUDNESS_LUFS = -14.0
    private const val MASTER_TRUE_PEAK_CEILING_DB = -1.0
    private const val MAX_MASTER_DURATION_DELTA_SECONDS = 0.05
    private const val PCM_24_QUANTIZATION_TOLERANCE = 1.0 / 8_388_608.0
    private val SUPPORTED_MP3_BITRATES = setOf(128, 160, 192, 256, 320)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    internal sealed interface Mp3ExportResult {
        data class Created(val encoder: String? = null) : Mp3ExportResult
        data object Unavailable : Mp3ExportResult
    }

    fun handles(args: Array<String>): Boolean =
        args.firstOrNull() in setOf("project", "part", "arrange", "arrange-detail", "critic", "generate", "mix", "render", "preview", "approve", "build", "export-mp3", "quality-gate", "transcribe", "midi-clean", "licenses")

    fun usage(): String = """
        Arranger and project commands:
          project create <project> [--sample-rate <hz>] [--channels <count>]
          part add <project> --id <id> --file <path> [--role <role>] [--transcribe]
          part analyze <project> --id <id>
          transcribe --input <audio> --output <midi> --instrument piano
          midi-clean --input <raw.mid> --output <clean.mid> [cleanup options]
          licenses <project> [--commercial]
          arrange --project <project> [--planner deterministic|qwen] [planning options]
          arrange-detail --project <project> [--planner deterministic|qwen]
          critic --project <project> [--planner deterministic|qwen]
          preview --project <project>
          approve --project <project>
          generate bass|drums|pad|strings|transitions --project <project>
          quality-gate --project <project>
          render --project <project>
          mix --project <project> [--dry]
          build --project <project> [--lofi] [--mp3] [--dry-run]
          export-mp3 --input <master.wav> --output <song.mp3> [--bitrate <kbps>]
          compare <a.wav> <b.wav> [--json] [--align]
    """.trimIndent()

    /** Small boundary that lets the end-to-end command be tested without a running HTTP worker. */
    internal interface BuildWorker {
        suspend fun healthCheck(): Boolean
        suspend fun analyze(path: Path): PartAnalysis
        suspend fun repair(inputPath: Path, outputPath: Path)
        suspend fun master(inputPath: Path, outputPath: Path)
        suspend fun exportMp3(inputPath: Path, outputPath: Path, bitrateKbps: Int): Mp3ExportResult = Mp3ExportResult.Unavailable
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
        "arrange-detail" -> arrangeDetail(args)
        "critic" -> critiqueArrangement(args)
        "generate" -> generateMidi(args)
        "mix" -> mixStems(args)
        "render" -> renderAllStems(args, buildSfizzRenderer())
        "preview" -> previewDraft(args)
        "approve" -> approveDraft(args)
        "build" -> buildProject(args, createBuildWorker())
        "export-mp3" -> exportMp3(args, createBuildWorker())
        "quality-gate" -> pianoBassQualityGate(args, buildSfizzRenderer())
        "transcribe" -> transcribe(args)
        "midi-clean" -> midiClean(args)
        "licenses" -> licenses(args)
        else -> throw IllegalArgumentException("Unknown arranger command")
    }

    internal fun executeBuildForTest(args: Array<String>, worker: BuildWorker): String = runBlocking {
        buildProject(args, worker)
    }

    internal fun executeExportMp3ForTest(args: Array<String>, worker: BuildWorker): String = runBlocking {
        exportMp3(args, worker)
    }

    internal fun executePartAddForTest(args: Array<String>, worker: MidiPreparationService): String = runBlocking {
        addPart(args, worker)
    }

    internal fun executeQualityGateForTest(args: Array<String>, renderer: InstrumentRenderer): String = runBlocking {
        pianoBassQualityGate(args, renderer)
    }

    internal fun executeStemRenderingForTest(args: Array<String>, renderer: InstrumentRenderer): String = runBlocking {
        renderAllStems(args, renderer)
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
                version = 2,
                profile = options.profile,
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
        return "Cleaned ${options.input} -> $outputPath (${options.profile}, notes ${stat("inputNoteCount")} -> ${stat("outputNoteCount")}, " +
            "duplicates ${stat("duplicatesRemoved")}, short ${stat("shortNotesRemoved")}, " +
            "low velocity ${stat("lowVelocityNotesRemoved")}, orphan offs ${stat("orphanNoteOffsRemoved")}, " +
            "retriggers ${stat("overlapsRepaired")}, sustain ${stat("redundantSustainControlsRemoved")}, " +
            "velocity outliers ${stat("velocityOutliersLimited")}, quantized ${stat("quantizedNotes")})"
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

    private fun createMidiPreparationWorker(): MidiPreparationService {
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        return object : MidiPreparationService {
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

            override suspend fun clean(input: Path, output: Path, options: ai.music.workstation.arrangement.MidiCleanupOptions) {
                Files.createDirectories(checkNotNull(output.parent))
                val response = client.execute(MidiCleanCommand(
                    path = input.toString(), outputPath = output.toString(), version = options.requestVersion,
                    profile = options.profile.name.lowercase().replace('_', '-'), quantize = options.quantize,
                    strength = options.strength, minNoteMs = options.minNoteMs, minVelocity = options.minVelocity,
                    normalizeVelocity = options.normalizeVelocity, cleanSustain = options.cleanSustain
                ))
                require(response.status == WorkerStatus.COMPLETED) { midiCleanupFailureMessage(response.error) }
                requireMidiArtifact(output, "MIDI cleanup")
            }
        }
    }

    /** CLI-only construction of HTTP adapters; the application service owns the use case. */
    private fun defaultProjectService(
        midiPreparation: MidiPreparationService = createMidiPreparationWorker()
    ) = DefaultProjectApplicationService(midiPreparation, createLegacyPartAnalysisService())

    private fun createLegacyPartAnalysisService(): LegacyPartAnalysisService {
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")
                ?.takeIf { it.isNotBlank() }
                ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        return LegacyPartAnalysisService { source ->
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
            require(response.status == WorkerStatus.COMPLETED) { "Part analysis failed: $workerError" }
            analysisFrom(response.output.orEmpty())
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

        val name = projectRoot.fileName?.toString().orEmpty()
        defaultProjectService().create(CreateProjectRequest(projectRoot, name, format))

        return "Created project: $projectRoot"
    }

    private suspend fun addPart(args: Array<String>, worker: MidiPreparationService): String {
        require(args.size >= 3 && args[1] == "add") {
            "Usage: part add <project-directory> --id <id> --file <midi-or-audio-file> [--role <role>] [--transcribe]"
        }

        val projectRoot = projectRoot(args[2])
        val options = parseAddOptions(args.drop(3))
        val id = options.getValue("--id")
        val source = Path.of(options.getValue("--file"))
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        val isMidi = extension in MIDI_EXTENSIONS
        val transcribe = "--transcribe" in options
        val snapshot = defaultProjectService(worker).importPart(
            ImportPartRequest(projectRoot, id, source, options["--role"].orEmpty(), transcribe)
        )

        return if (snapshot.version == 1 && !isMidi && !transcribe) {
            "Added legacy audio part '$id' to $projectRoot. Transcribe it before MIDI-only processing."
        } else "Added part '$id' to $projectRoot"
    }

    private suspend fun analyzePart(args: Array<String>): String {
        require(args.size >= 3 && args[1] == "analyze") {
            "Usage: part analyze <project-directory> --id <id>"
        }

        val projectRoot = projectRoot(args[2])
        val id = parseAnalyzeOptions(args.drop(3)).getValue("--id")
        val snapshot = defaultProjectService().analyzePart(AnalyzePartRequest(projectRoot, id))
        val analysis = checkNotNull(snapshot.parts.single { it.id == id }.analysis)
        return if (analysis.status == ai.music.workstation.application.PartAnalysisStatus.MIDI) {
            "Analyzed MIDI part '$id': ${projectRoot.resolve(analysis.file)}"
        } else "Analyzed part '$id': ${projectRoot.resolve(analysis.file)}"
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
        val used = usedGeneratedInstrumentNames(root, project, arrangementPath)
        val registry = InstrumentRegistryLoader(resolveLibraryRoot().root).load()
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

    /** Expands the reviewed Task 009/010 artifacts into a v3 decision document. */
    private fun arrangeDetail(args: Array<String>): String {
        val options = parseArrangeDetailOptions(args.drop(1))
        val plannerName = options["--planner"] ?: DETERMINISTIC_PLANNER
        val projectRoot = projectRoot(options.getValue("--project"))
        val project = readProject(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val planner: DetailedArrangementPlanner = when (plannerName) {
            DETERMINISTIC_PLANNER -> DeterministicDetailedArrangementPlanner()
            QWEN_PLANNER -> LocalQwenDetailedArrangementPlanner()
            else -> throw IllegalArgumentException("Unsupported planner: $plannerName. Available planners: $DETERMINISTIC_PLANNER, $QWEN_PLANNER")
        }
        val arrangement = planner.plan(input)
        val path = if (plannerName == QWEN_PLANNER) {
            DetailedArrangementStore.writeDraft(projectRoot, input, arrangement)
        } else {
            DetailedArrangementStore.writeApproved(projectRoot, input, arrangement)
        }
        return if (plannerName == QWEN_PLANNER) {
            "Created Qwen detailed arrangement draft: $path. Review it, then run `approve --project $projectRoot`."
        } else {
            "Created deterministic detailed arrangement: $path"
        }
    }

    /** Creates a review-only draft from the approved v3 arrangement; approval stays explicit. */
    private fun critiqueArrangement(args: Array<String>): String {
        val options = parseCriticOptions(args.drop(1))
        val plannerName = options["--planner"] ?: DETERMINISTIC_PLANNER
        val projectRoot = projectRoot(options.getValue("--project"))
        val project = readProject(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val approvedPath = projectRoot.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(approvedPath)) {
            "Approved detailed arrangement not found: $approvedPath. Run arrange-detail and approve a draft first."
        }
        val approvedText = Files.readString(approvedPath, StandardCharsets.UTF_8)
        val approved = json.decodeFromString<DetailedArrangement>(approvedText)
        approved.requireValid(input)
        val critic: ArrangementCritic = when (plannerName) {
            DETERMINISTIC_PLANNER -> DeterministicArrangementCritic()
            QWEN_PLANNER -> LocalQwenArrangementCritic()
            else -> throw IllegalArgumentException("Unsupported critic planner: $plannerName. Available planners: $DETERMINISTIC_PLANNER, $QWEN_PLANNER")
        }
        val critique = critic.critique(input, approved)
        val draft = ArrangementCritiqueValidator.apply(input, approved, critique)
        val draftPath = ArrangementCriticStore.writeReviewArtifacts(projectRoot, input, approvedText, draft, critique)
        return "Created $plannerName arrangement-critic draft: $draftPath (snapshot: ${projectRoot.resolve(ArrangementCriticStore.PRE_CRITIC_FILE)}). Review it with `preview --project $projectRoot`, then run `approve --project $projectRoot`."
    }

    private fun generateMidi(args: Array<String>): String = when (args.getOrNull(1)) {
        "bass" -> generateBass(args)
        "drums" -> generateDrums(args)
        "pad" -> generatePad(args)
        "strings" -> generateStrings(args)
        "transitions" -> generateTransitions(args)
        else -> throw IllegalArgumentException("Usage: generate bass|drums|pad|strings|transitions --project <project-directory>")
    }

    private fun generateBass(args: Array<String>): String {
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve("arrangement.json")
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Arrangement file not found: $arrangementFile" }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val arrangementText = Files.readString(arrangementFile, StandardCharsets.UTF_8)
        val arrangementVersion = json.parseToJsonElement(arrangementText).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Bass MIDI generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<ai.music.workstation.arrangement.MidiAnalysis>(
                Files.readString(projectRoot.resolve(reference.file))
            )
        }
        val adapter = BassMidiGenerationAdapter(libraryRoot = resolveLibraryRoot().root)
        val bass = if (arrangementVersion == DetailedArrangement.CURRENT_VERSION) {
            val input = detailedArrangementInput(projectRoot, project)
            val arrangement = json.decodeFromString<DetailedArrangement>(arrangementText)
            arrangement.requireValid(input)
            adapter.generate(projectRoot, project, arrangement, analyses)
        } else {
            val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(arrangementText)
            adapter.generate(projectRoot, project, arrangement, analyses)
        }
        val suffix = if (bass.diagnostics.isEmpty()) "" else "; ${bass.diagnostics.joinToString(" ")}"
        return "Generated bass MIDI: ${bass.path} (${bass.notes.size} notes)$suffix"
    }

    private fun generateDrums(args: Array<String>): String {
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Detailed arrangement file not found: $arrangementFile. Run arrange-detail first." }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(arrangementFile, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Drum MIDI generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<MidiAnalysis>(Files.readString(projectRoot.resolve(reference.file)))
        }
        val drums = DrumMidiGenerationAdapter(libraryRoot = resolveLibraryRoot().root).generate(projectRoot, project, arrangement, analyses)
        val suffix = if (drums.diagnostics.isEmpty()) "" else "; ${drums.diagnostics.joinToString(" ")}"
        return "Generated drum MIDI: ${drums.path} (${drums.hits.size} hits)$suffix"
    }

    private fun generatePad(args: Array<String>): String {
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Detailed arrangement file not found: $arrangementFile. Run arrange-detail first." }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(arrangementFile, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Pad MIDI generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<MidiAnalysis>(Files.readString(projectRoot.resolve(reference.file)))
        }
        val pad = PadMidiGenerationAdapter(libraryRoot = resolveLibraryRoot().root).generate(projectRoot, project, arrangement, analyses)
        val suffix = if (pad.diagnostics.isEmpty()) "" else "; ${pad.diagnostics.joinToString(" ")}"
        return "Generated pad MIDI: ${pad.path} (${pad.notes.size} notes)$suffix"
    }

    private fun generateStrings(args: Array<String>): String {
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Detailed arrangement file not found: $arrangementFile. Run arrange-detail first." }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(arrangementFile, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Strings MIDI generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<MidiAnalysis>(Files.readString(projectRoot.resolve(reference.file)))
        }
        val strings = StringsMidiGenerationAdapter(libraryRoot = resolveLibraryRoot().root).generate(projectRoot, project, arrangement, analyses)
        val suffix = if (strings.diagnostics.isEmpty()) "" else "; ${strings.diagnostics.joinToString(" ")}"
        return "Generated strings MIDI: ${strings.path} (${strings.notes.size} notes)$suffix"
    }

    private fun generateTransitions(args: Array<String>): String {
        val options = parseGenerateOptions(args.drop(2))
        val projectRoot = projectRoot(options.getValue("--project"))
        val projectFile = projectRoot.resolve(PROJECT_FILE)
        val arrangementFile = projectRoot.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(projectFile)) { "Project file not found: $projectFile" }
        require(Files.isRegularFile(arrangementFile)) { "Detailed arrangement file not found: $arrangementFile. Run arrange-detail first." }

        val project = ProjectStore.read(projectRoot)
        project.requireValid(projectRoot)
        val input = detailedArrangementInput(projectRoot, project)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(arrangementFile, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Transition generation requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<MidiAnalysis>(Files.readString(projectRoot.resolve(reference.file)))
        }
        val transitions = MidiTransitionGenerationAdapter(libraryRoot = resolveLibraryRoot().root).generate(projectRoot, project, arrangement, analyses)
        val suffix = if (transitions.result.diagnostics.isEmpty()) "" else "; ${transitions.result.diagnostics.joinToString(" ")}"
        return "Generated transition MIDI: ${transitions.path} (${transitions.result.events.size} notes; ${transitions.result.placements.sumOf { it.insertedTicksAfter }} inserted ticks)$suffix"
    }

    private suspend fun pianoBassQualityGate(args: Array<String>, renderer: InstrumentRenderer): String {
        require(args.size == 3 && args[1] == "--project") {
            "Usage: quality-gate --project <project-directory>"
        }
        val result = PianoBassQualityGate(
            renderer,
            bassGenerator = BassMidiGenerationAdapter(libraryRoot = resolveLibraryRoot().root)
        ).run(projectRoot(args[2]))
        return result.progress.joinToString("\n") + "\nQuality gate ${if (result.reusedFinalArtifacts) "reused" else "created"} artifacts: " +
            "midi/generated/piano.mid, midi/generated/bass.mid, stems/piano.wav, stems/bass.wav, mix/dry.wav, quality-gate.json"
    }

    private suspend fun renderAllStems(args: Array<String>, renderer: InstrumentRenderer): String {
        val root = parseProjectOnly(args, "render")
        require(Files.isRegularFile(root.resolve(PROJECT_FILE))) { "Project file not found: ${root.resolve(PROJECT_FILE)}" }
        require(Files.isRegularFile(root.resolve(DetailedArrangementStore.APPROVED_FILE))) {
            "Detailed arrangement file not found: ${root.resolve(DetailedArrangementStore.APPROVED_FILE)}. Run arrange-detail first."
        }
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val input = detailedArrangementInput(root, project)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(root.resolve(DetailedArrangementStore.APPROVED_FILE), StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        val analyses = project.parts.associate { part ->
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '${part.id}'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Stem rendering requires MIDI analysis for '${part.id}'. Run part analyze after MIDI cleanup." }
            part.id to json.decodeFromString<MidiAnalysis>(Files.readString(root.resolve(reference.file), StandardCharsets.UTF_8))
        }
        val result = StemRenderingMixer(renderer, resolveLibraryRoot().root).render(root, project, arrangement, analyses)
        val report = result.report
        return "${if (result.reused) "Reused" else "Rendered"} ${report.stems.joinToString { it.path }} and ${report.dryMix} " +
            "(${report.timelineFrames} frames, ${report.sampleRate} Hz, ${report.channels} channels, peak gain ${"%.2f".format(report.appliedGainDb)} dB)"
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
        val arrangementText = Files.readString(arrangementFile, StandardCharsets.UTF_8)
        val arrangementVersion = json.parseToJsonElement(arrangementText).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (arrangementVersion == DetailedArrangement.CURRENT_VERSION) {
            val arrangement = json.decodeFromString<DetailedArrangement>(arrangementText)
            arrangement.requireValid(detailedArrangementInput(projectRoot, project))
            val dryPath = projectRoot.resolve("mix/dry.wav")
            require(Files.isRegularFile(dryPath)) {
                "Rendered v3 dry mix not found: $dryPath. Run render --project $projectRoot first."
            }
            val dry = requireWavOutput(dryPath, "Rendered v3 dry mix", requirePcm24 = true)
            val format = requireNotNull(project.renderFormat)
            require(dry.sampleRate == format.sampleRate && dry.channels == format.channels) {
                "Rendered v3 dry mix does not match the project render format"
            }
            val output = projectRoot.resolve("mix/mix.wav")
            publishCopy(dryPath, output, "v3 stem mix")
            return "Created ${if ("--dry" in options) "dry " else ""}mix from rendered v3 stems: $output"
        }
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(arrangementText)
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
        val draftVersion = json.parseToJsonElement(Files.readString(draftPath)).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (draftVersion == 3) {
            val arrangement = DetailedArrangementStore.readDraft(projectRoot, detailedArrangementInput(projectRoot, project))
            val previewDir = projectRoot.resolve("previews")
            Files.createDirectories(previewDir)
            val preview = previewDir.resolve("detailed-arrangement-preview.txt")
            Files.writeString(preview, detailedPreviewSummary(arrangement), StandardCharsets.UTF_8)
            return "Validated detailed arrangement draft: $draftPath (${arrangement.sections.size} sections). Created representative section preview manifest: $preview. Inspect it, then run `approve --project $projectRoot`."
        }
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
        val draftVersion = json.parseToJsonElement(Files.readString(draft)).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (draftVersion == 3) {
            val approved = DetailedArrangementStore.approve(projectRoot, detailedArrangementInput(projectRoot, project))
            return "Approved detailed arrangement: $approved"
        }
        val arrangement = json.decodeFromString<ai.music.workstation.arrangement.Arrangement>(Files.readString(draft))
        arrangement.requireValid(project.parts.map { it.id })
        val temp = projectRoot.resolve("arrangement.approving.json")
        Files.copy(draft, temp, StandardCopyOption.REPLACE_EXISTING)
        Files.move(temp, projectRoot.resolve("arrangement.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return "Approved arrangement: ${projectRoot.resolve("arrangement.json")}"
    }

    /** A path-free, deterministic review artifact for v3 drafts before any MIDI/stem re-render. */
    private fun detailedPreviewSummary(arrangement: DetailedArrangement): String = buildString {
        appendLine("Detailed arrangement draft review")
        arrangement.sections.forEach { section ->
            appendLine(
                "section=${section.index} instance=${section.instanceId} part=${section.partId} role=${section.role.name.lowercase()} " +
                    "energy=${section.energy} activeInstruments=${section.instruments.size} " +
                    "instruments=${section.instruments.joinToString(",") { it.name }} transition=${section.transitionOut.type.name.lowercase()}"
            )
        }
    }

    /** Reconstructs validation input only from reviewed, path-free planning artifacts and registered MIDI analyses. */
    private fun detailedArrangementInput(projectRoot: Path, project: Project): DetailedArrangementInput {
        val planPath = projectRoot.resolve(SongPlanStore.FILE_NAME)
        require(Files.isRegularFile(planPath)) { "Song plan not found: $planPath. Run arrange first." }
        val rawPlan = json.decodeFromString<SongPlan>(Files.readString(planPath, StandardCharsets.UTF_8))
        val structure = rawPlan.sections.map { SectionInstance(it.index, it.partId) }
        val allowedInstruments = rawPlan.sections.flatMap { it.instrumentProgression }.distinct()
        val partsById = project.parts.associateBy { it.id }
        val analyses = structure.map { it.partId }.distinct().associateWith { partId ->
            val part = checkNotNull(partsById[partId]) { "Song plan references unknown project part '$partId'" }
            val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$partId'. Run part analyze first." }
            require(reference.kind == AnalysisKind.MIDI) { "Detailed arrangement requires MIDI analysis for '$partId'" }
            json.decodeFromString<MidiAnalysis>(Files.readString(projectRoot.resolve(reference.file), StandardCharsets.UTF_8))
        }
        val planningInput = SongPlanningInput(
            projectName = project.name,
            projectVersion = project.version,
            analyses = analyses,
            structure = structure,
            allowedInstruments = allowedInstruments,
            style = rawPlan.style
        )
        val songPlan = SongPlanStore.read(projectRoot, planningInput)
        val variations = SectionVariationStore.read(projectRoot, planningInput, songPlan)
        return DetailedArrangementInput(planningInput, songPlan, variations)
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

    /** Exports only a validated final master; all arrangement processing remains WAV-only. */
    private suspend fun exportMp3(args: Array<String>, worker: BuildWorker): String {
        val options = parseMp3ExportOptions(args.drop(1))
        val master = options.input
        require(master.fileName.toString().equals("master.wav", ignoreCase = true)) {
            "MP3 export input must be the final master.wav artifact"
        }
        val masterWav = requireWavOutput(master, "MP3 export input", requirePcm24 = true)
        require(options.output.fileName.toString().endsWith(".mp3", ignoreCase = true)) {
            "MP3 export output must use a .mp3 extension"
        }
        require(!Files.isDirectory(options.output)) { "MP3 export output path is a directory: ${options.output}" }
        requireDistinctFiles(master, options.output, "MP3 export input and output paths must differ")
        requireMp3OutputIsSafe(master, options.output)

        when (val result = runBuildStage("Export MP3") {
            worker.exportMp3(master, options.output, options.bitrateKbps)
        }) {
            is Mp3ExportResult.Created -> {
                val mp3 = inspectMp3(options.output)
                updateExistingReleaseForMp3(master, options.output, options.bitrateKbps)
                return "Created ${options.output} from ${master.fileName} (${options.bitrateKbps} kbps, ${masterWav.sampleRate} Hz, ${masterWav.channels} channel(s); ${result.encoder ?: "local encoder"}; ${mp3.sizeBytes} bytes)"
            }
            Mp3ExportResult.Unavailable -> throw IllegalStateException(
                "MP3 export requires the optional local lameenc encoder; master WAV remains unchanged at $master"
            )
        }
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

        // The current detailed-arrangement build is shared with Compose. Keep
        // the established legacy path below for v1/v2 projects, dry runs, and
        // a caller-selected alternate output directory.
        val arrangementPath = projectRoot.resolve("arrangement.json")
        val existingArrangementVersion = arrangementPath.takeIf(Files::isRegularFile)?.let { path ->
            json.parseToJsonElement(Files.readString(path, StandardCharsets.UTF_8)).jsonObject["version"]
                ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        }
        if (existingArrangementVersion == DetailedArrangement.CURRENT_VERSION && !options.dryRun && options.outputDirectory == null) {
            val service = DefaultBuildApplicationService(
                DefaultArrangementApplicationService(libraryRoot = resolveLibraryRoot().root),
                DefaultMixApplicationService(),
                SfizzInstrumentRenderer(InstrumentRegistryLoader(resolveLibraryRoot().root)),
                object : BuildAudioWorker {
                    override suspend fun healthCheck(): Boolean = worker.healthCheck()
                    override suspend fun repair(input: Path, output: Path) = worker.repair(input, output)
                    override suspend fun master(input: Path, output: Path) = worker.master(input, output)
                    override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean =
                        worker.exportMp3(input, output, bitrateKbps) is Mp3ExportResult.Created
                }
            )
            val result = service.build(BuildSongRequest(projectRoot, options.enableLoFi, options.enableMp3, options.mp3BitrateKbps)) { progress ->
                println("✓ [${progress.stageIndex}/${progress.stageCount}] ${progress.message}")
            }
            return "Build complete: ${result.master}" + (result.mp3?.let { " and $it" } ?: "")
        }

        var project = readProject(projectRoot)
        project.requireValid(projectRoot)
        val structure = project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        require(structure.isNotEmpty()) { "Project structure must contain at least one part" }

        val outputRoot = resolveOutputRoot(projectRoot, options.outputDirectory)
        val dryPath = projectRoot.resolve("mix/dry.wav")
        val repairedPath = projectRoot.resolve("mix/repaired.wav")
        val lofiPath = projectRoot.resolve("mix/lofi.wav")
        val masterPath = outputRoot.resolve("master.wav")
        val mp3Path = outputRoot.resolve("song.mp3")
        requireNoSourceOverwrite(projectRoot, project, listOf(dryPath, repairedPath, lofiPath, masterPath, mp3Path))

        if (options.dryRun) {
            return buildString {
                appendLine("[DRY RUN] Project is valid: $projectRoot")
                appendLine("[DRY RUN] Planner: deterministic${if (options.noAi) " (--no-ai)" else ""}")
                appendLine("[DRY RUN] Output directory: $outputRoot")
                appendLine("[DRY RUN] LoFi: ${if (options.enableLoFi) "enabled" else "disabled"}")
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

        val arrangementText = Files.readString(approvedArrangement, StandardCharsets.UTF_8)
        val arrangementVersion = json.parseToJsonElement(arrangementText).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (arrangementVersion == DetailedArrangement.CURRENT_VERSION) {
            runBuildStage("Render detailed arrangement stems") {
                renderAllStems(arrayOf("render", "--project", projectRoot.toString()), SfizzInstrumentRenderer(InstrumentRegistryLoader(resolveLibraryRoot().root)))
            }
            complete(5, "Rendered or reused detailed arrangement stems")
        } else {
            complete(5, "Prepared local generated-instrument render")
        }

        runBuildStage("Mix stems") {
            mixStems(arrayOf("mix", "--project", projectRoot.toString()))
        }
        val mixPath = projectRoot.resolve("mix/mix.wav")
        val mixedWav = requireWavOutput(mixPath, "Mix", requirePcm24 = true)
        publishCopy(mixPath, dryPath, "dry mix")
        val dryWav = requireWavOutput(dryPath, "Dry mix", requirePcm24 = true)
        require(dryWav.sampleRate == mixedWav.sampleRate && dryWav.channels == mixedWav.channels && dryWav.frameCount == mixedWav.frameCount) {
            "Dry mix copy changed the mix format or duration"
        }
        require(dryWav.peak < 1.0) { "Dry mix is clipped (peak ${dryWav.peak}); fix the mix before mastering" }
        complete(6, "Created mix/dry.wav")

        Files.createDirectories(outputRoot)
        runBuildStage("Repair mix") {
            publishWorkerWav(repairedPath, "repair", action = { temporary -> worker.repair(dryPath, temporary) })
        }
        val repairedWav = requireWavOutput(repairedPath, "Repair", requirePcm24 = true)
        requireDerivedFormat(dryWav, repairedWav, "Repair")
        complete(7, "Created ${projectRoot.relativizeOrName(repairedPath)}")

        val masteringInput: Path
        val masteringInputWav: ValidatedWav
        if (options.enableLoFi) {
            runBuildStage("Apply LoFi") {
                publishTemporaryWav(lofiPath, "lofi") { temporary ->
                    applyLoFi(repairedPath, temporary)
                    requireWavOutput(temporary, "LoFi", requirePcm24 = true)
                }
            }
            val lofiWav = requireWavOutput(lofiPath, "LoFi", requirePcm24 = true)
            requireDerivedFormat(repairedWav, lofiWav, "LoFi")
            masteringInput = lofiPath
            masteringInputWav = lofiWav
            complete(8, "Created ${projectRoot.relativizeOrName(lofiPath)}; mastering input: mix/lofi.wav")
        } else {
            masteringInput = repairedPath
            masteringInputWav = repairedWav
            complete(8, "Skipped LoFi; mastering input: mix/repaired.wav")
        }

        val masterWav = runBuildStage("Master audio") {
            publishWorkerWav(
                target = masterPath,
                stage = "mastering",
                action = { temporary -> worker.master(masteringInput, temporary) },
                validate = { validated ->
                    requireDerivedFormat(masteringInputWav, validated, "Master")
                    require(validated.peak <= dbToAmplitude(MASTER_TRUE_PEAK_CEILING_DB) + PCM_24_QUANTIZATION_TOLERANCE) {
                        "Master exceeds the ${MASTER_TRUE_PEAK_CEILING_DB} dB peak ceiling (peak ${validated.peak})"
                    }
                }
            )
            requireWavOutput(masterPath, "Master", requirePcm24 = true)
        }
        complete(9, "Created ${outputRoot.relativizeOrName(masterPath)} from ${projectRoot.relativizeOrName(masteringInput)}")
        if (!options.enableMp3) {
            Files.deleteIfExists(mp3Path)
            writeReleaseMetadata(outputRoot, projectRoot, masteringInput, masteringInputWav, masterPath, masterWav, options.enableLoFi)
            complete(10, "Build complete: $masterPath (MP3 export not requested)")
        } else when (val mp3Result = runBuildStage("Export upload-ready MP3") {
            worker.exportMp3(masterPath, mp3Path, options.mp3BitrateKbps)
        }) {
            is Mp3ExportResult.Created -> {
                val mp3 = inspectMp3(mp3Path)
                writeReleaseMetadata(outputRoot, projectRoot, masteringInput, masteringInputWav, masterPath, masterWav, options.enableLoFi, mp3Path, options.mp3BitrateKbps)
                complete(10, "Build complete: $masterPath and $mp3Path (${mp3.sizeBytes} bytes)")
            }
            Mp3ExportResult.Unavailable -> {
                Files.deleteIfExists(mp3Path)
                writeReleaseMetadata(outputRoot, projectRoot, masteringInput, masteringInputWav, masterPath, masterWav, options.enableLoFi)
                complete(10, "Build complete: $masterPath (MP3 export unavailable: lameenc is not installed)")
            }
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

            override suspend fun exportMp3(inputPath: Path, outputPath: Path, bitrateKbps: Int): Mp3ExportResult {
                val response = client.execute(MP3ExportCommand(inputPath.toString(), outputPath.toString(), bitrateKbps))
                if (response.status == WorkerStatus.COMPLETED) {
                    return Mp3ExportResult.Created(response.output?.get("encoder")?.jsonPrimitive?.contentOrNull)
                }
                val message = response.error?.message.orEmpty()
                if (message.contains("requires lameenc")) return Mp3ExportResult.Unavailable
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
        var enableLoFi = false
        var enableMp3 = false
        var mp3BitrateKbps = 320
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
                "--lofi" -> {
                    require(!enableLoFi) { "Duplicate build option: --lofi" }
                    enableLoFi = true
                }
                "--mp3" -> {
                    require(!enableMp3) { "Duplicate build option: --mp3" }
                    enableMp3 = true
                }
                "--mp3-bitrate" -> {
                    require(index + 1 < arguments.size) { "Missing value for --mp3-bitrate" }
                    mp3BitrateKbps = arguments[++index].toIntOrNull()
                        ?: throw IllegalArgumentException("--mp3-bitrate must be an integer")
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
            "Usage: build --project <project-directory> [--output-dir <directory>] [--no-ai] [--lofi] [--mp3 [--mp3-bitrate <kbps>]] [--dry-run]"
        }
        require(mp3BitrateKbps in SUPPORTED_MP3_BITRATES) {
            "--mp3-bitrate must be one of ${SUPPORTED_MP3_BITRATES.joinToString(", ")}"
        }
        return BuildOptions(projectPath, outputDirectory, noAi, enableLoFi, enableMp3, mp3BitrateKbps, dryRun)
    }

    private fun parseMp3ExportOptions(arguments: List<String>): Mp3ExportOptions {
        var input: Path? = null
        var output: Path? = null
        var bitrateKbps = 320
        var index = 0
        val seen = mutableSetOf<String>()
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in setOf("--input", "--output", "--bitrate")) {
                "Usage: export-mp3 --input <output/master.wav> --output <output/song.mp3> [--bitrate <kbps>]"
            }
            require(seen.add(option)) { "Duplicate MP3 export option: $option" }
            require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) { "Missing value for $option" }
            when (option) {
                "--input" -> input = Path.of(arguments[index + 1]).toAbsolutePath().normalize()
                "--output" -> output = Path.of(arguments[index + 1]).toAbsolutePath().normalize()
                "--bitrate" -> bitrateKbps = arguments[index + 1].toIntOrNull()
                    ?: throw IllegalArgumentException("--bitrate must be an integer")
            }
            index += 2
        }
        require(input != null && Files.isRegularFile(input)) { "MP3 export input WAV not found: $input" }
        require(output != null) { "Missing required option: --output <output/song.mp3>" }
        require(bitrateKbps in SUPPORTED_MP3_BITRATES) {
            "--bitrate must be one of ${SUPPORTED_MP3_BITRATES.joinToString(", ")}"
        }
        return Mp3ExportOptions(input, output, bitrateKbps)
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

    private fun requireDistinctFiles(input: Path, output: Path, message: String) {
        require(input != output && !(Files.exists(output) && Files.isSameFile(input, output))) { message }
    }

    private fun requireMp3OutputIsSafe(master: Path, output: Path) {
        require(output.fileName.toString().lowercase() !in setOf("master.wav", "release.json", "project.json", "arrangement.json", "instruments.json", "licenses.json")) {
            "MP3 export output would overwrite a protected project artifact: $output"
        }
        val projectRoot = master.parent.parent?.takeIf { Files.isRegularFile(it.resolve(PROJECT_FILE)) }
        if (projectRoot != null) {
            val project = readProject(projectRoot)
            val protected = project.parts.map { projectRoot.resolve(it.file).normalize() } + listOf(
                master,
                projectRoot.resolve("arrangement.json"),
                projectRoot.resolve("sounds/instruments.json"),
                projectRoot.resolve("sounds/LICENSES.json")
            )
            require(protected.none { path -> output == path || (Files.exists(output) && Files.exists(path) && Files.isSameFile(output, path)) }) {
                "MP3 export output would overwrite a protected source, MIDI, or project artifact: $output"
            }
            require(!output.startsWith(projectRoot.resolve("midi")) && !output.startsWith(projectRoot.resolve("stems")) && !output.startsWith(projectRoot.resolve("mix"))) {
                "MP3 export output must not be placed in MIDI, stems, or mix artifacts: $output"
            }
        }
    }

    private fun requireWavOutput(path: Path, stage: String, requirePcm24: Boolean = false): ValidatedWav =
        try {
            val wav = inspectWav(path)
            require(!requirePcm24 || wav.pcm && wav.bitsPerSample == 24) {
                "$stage stage did not create PCM-24 WAV audio: $path"
            }
            wav
        } catch (error: Exception) {
            throw IllegalArgumentException("$stage stage did not create a valid lossless WAV file: ${error.message}", error)
        }

    private fun requireDerivedFormat(input: ValidatedWav, output: ValidatedWav, stage: String) {
        require(input.sampleRate == output.sampleRate) {
            "$stage changed sample rate from ${input.sampleRate} Hz to ${output.sampleRate} Hz"
        }
        require(input.channels == output.channels) {
            "$stage changed channel count from ${input.channels} to ${output.channels}"
        }
        val durationDelta = abs(input.durationSeconds - output.durationSeconds)
        require(durationDelta <= MAX_MASTER_DURATION_DELTA_SECONDS) {
            "$stage changed duration by ${"%.3f".format(durationDelta)} seconds (maximum $MAX_MASTER_DURATION_DELTA_SECONDS seconds)"
        }
    }

    private suspend fun publishWorkerWav(
        target: Path,
        stage: String,
        action: suspend (Path) -> Unit,
        validate: (ValidatedWav) -> Unit = {}
    ) {
        publishTemporaryWav(target, stage) { temporary ->
            action(temporary)
            validate(requireWavOutput(temporary, stage, requirePcm24 = true))
        }
    }

    private suspend fun publishTemporaryWav(target: Path, stage: String, action: suspend (Path) -> Unit) {
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = temporaryWav(target, stage)
        try {
            action(temporary)
            atomicReplace(temporary, target, stage)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun publishCopy(source: Path, target: Path, stage: String) {
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = temporaryWav(target, stage)
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            atomicReplace(temporary, target, stage)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun temporaryWav(target: Path, stage: String): Path =
        target.resolveSibling(".${target.fileName}.$stage-${UUID.randomUUID()}.wav")

    private fun atomicReplace(source: Path, target: Path, stage: String) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for $stage output '$target'", error)
        }
    }

    private fun writeReleaseMetadata(
        outputRoot: Path,
        projectRoot: Path,
        input: Path,
        inputWav: ValidatedWav,
        master: Path,
        masterWav: ValidatedWav,
        loFiEnabled: Boolean,
        mp3: Path? = null,
        mp3BitrateKbps: Int? = null
    ) {
        val metadata = MasterReleaseMetadata(
            inputArtifact = projectRoot.relativizeOrName(input),
            inputFingerprint = sha256(input),
            inputSampleRate = inputWav.sampleRate,
            inputChannels = inputWav.channels,
            inputPcmBitDepth = inputWav.bitsPerSample,
            master = master.fileName.toString(),
            masterFingerprint = sha256(master),
            sampleRate = masterWav.sampleRate,
            channels = masterWav.channels,
            pcmBitDepth = masterWav.bitsPerSample,
            frameCount = masterWav.frameCount,
            durationSeconds = masterWav.durationSeconds,
            peak = masterWav.peak,
            peakDb = amplitudeToDb(masterWav.peak),
            targetLufs = MASTER_TARGET_LOUDNESS_LUFS,
            truePeakCeilingDb = MASTER_TRUE_PEAK_CEILING_DB,
            repairEnabled = true,
            loFiEnabled = loFiEnabled,
            mp3 = mp3?.let {
                Mp3ReleaseMetadata(
                    name = it.fileName.toString(),
                    fingerprint = sha256(it),
                    bitrateKbps = mp3BitrateKbps ?: error("MP3 bitrate is required")
                )
            },
            instrumentLicenses = instrumentLicenseReport(projectRoot)
        )
        val target = outputRoot.resolve("release.json")
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(metadata), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
            atomicReplace(temporary, target, "release metadata")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** A direct export enriches the existing build release report when it belongs to a project. */
    private fun updateExistingReleaseForMp3(master: Path, mp3: Path, bitrateKbps: Int) {
        val projectRoot = master.parent.parent?.takeIf { Files.isRegularFile(it.resolve(PROJECT_FILE)) } ?: return
        val release = master.parent.resolve("release.json")
        if (!Files.isRegularFile(release)) return
        val existing = json.decodeFromString<MasterReleaseMetadata>(Files.readString(release, StandardCharsets.UTF_8))
        val updated = existing.copy(
            mp3 = Mp3ReleaseMetadata(mp3.fileName.toString(), sha256(mp3), bitrateKbps = bitrateKbps),
            instrumentLicenses = instrumentLicenseReport(projectRoot)
        )
        val temporary = release.resolveSibling(".${release.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(updated), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)
            atomicReplace(temporary, release, "release metadata")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun sha256(path: Path): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun dbToAmplitude(decibels: Double): Double = 10.0.pow(decibels / 20.0)
    private fun amplitudeToDb(amplitude: Double): Double = if (amplitude <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(amplitude)

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
        val enableLoFi: Boolean,
        val enableMp3: Boolean,
        val mp3BitrateKbps: Int,
        val dryRun: Boolean
    )

    private data class Mp3ExportOptions(val input: Path, val output: Path, val bitrateKbps: Int)

    @Serializable
    private data class MasterReleaseMetadata(
        val version: Int = 1,
        val master: String,
        val masterFingerprint: String,
        val inputArtifact: String,
        val inputFingerprint: String,
        val inputSampleRate: Int,
        val inputChannels: Int,
        val inputPcmBitDepth: Int,
        val sampleRate: Int,
        val channels: Int,
        val pcmBitDepth: Int,
        val frameCount: Long,
        val durationSeconds: Double,
        val peak: Double,
        val peakDb: Double,
        val targetLufs: Double,
        val truePeakCeilingDb: Double,
        val repairEnabled: Boolean,
        val loFiEnabled: Boolean,
        val mp3: Mp3ReleaseMetadata? = null,
        val instrumentLicenses: List<InstrumentLicenseMetadata> = emptyList(),
        val settings: MasteringSettingsMetadata = MasteringSettingsMetadata()
    )

    @Serializable
    private data class Mp3ReleaseMetadata(
        val name: String,
        val fingerprint: String,
        val format: String = "MP3",
        val bitrateKbps: Int
    )

    @Serializable
    private data class InstrumentLicenseMetadata(
        val instrument: String,
        val licenseId: String,
        val displayName: String,
        val commercialUse: Boolean,
        val attributionRequired: Boolean,
        val attribution: String? = null
    )

    @Serializable
    private data class MasteringSettingsMetadata(
        val repair: List<String> = listOf("dc_offset", "clip_removal(threshold=0.999,max_run_samples=12)"),
        val eqLowShelfHz: Double = 180.0,
        val eqLowShelfGainDb: Double = 1.5,
        val compressorThresholdDb: Double = -18.0,
        val compressorRatio: Double = 2.0,
        val compressorAttackMs: Double = 15.0,
        val compressorReleaseMs: Double = 150.0,
        val saturationDrive: Double = 1.08,
        val saturationMix: Double = 0.08,
        val limiterCeilingDb: Double = MASTER_TRUE_PEAK_CEILING_DB,
        val limiterReleaseMs: Double = 100.0
    )

    private data class ValidatedWav(
        val pcm: Boolean,
        val bitsPerSample: Int,
        val sampleRate: Int,
        val channels: Int,
        val frameCount: Long,
        val peak: Double
    ) {
        val durationSeconds: Double get() = frameCount.toDouble() / sampleRate
    }

    /** Strict enough for worker output: RIFF/WAVE, supported lossless samples, finite and non-empty. */
    private fun inspectWav(path: Path): ValidatedWav {
        require(Files.isRegularFile(path) && Files.size(path) >= 44) { "file is missing, empty, or smaller than a RIFF header: $path" }
        RandomAccessFile(path.toFile(), "r").use { input ->
            require(input.readFourCcLE() == "RIFF") { "missing RIFF container" }
            val riffSize = input.readUInt32LE()
            require(riffSize + 8L == input.length()) { "RIFF size does not match file length" }
            require(input.readFourCcLE() == "WAVE") { "missing WAVE container type" }

            var encoding = -1
            var channels = 0
            var sampleRate = 0
            var blockAlign = 0
            var bits = 0
            var dataOffset = -1L
            var dataSize = -1L
            while (input.filePointer + 8 <= input.length()) {
                val chunkId = input.readFourCcLE()
                val chunkSize = input.readUInt32LE()
                require(chunkSize <= input.length() - input.filePointer) { "chunk '$chunkId' exceeds file length" }
                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "fmt chunk is too short" }
                        encoding = input.readUInt16LE()
                        channels = input.readUInt16LE()
                        sampleRate = input.readUInt32LE().toInt()
                        input.readUInt32LE()
                        blockAlign = input.readUInt16LE()
                        bits = input.readUInt16LE()
                        input.seek(input.filePointer + chunkSize - 16)
                    }
                    "data" -> {
                        require(dataOffset < 0) { "multiple data chunks are not supported" }
                        dataOffset = input.filePointer
                        dataSize = chunkSize
                        input.seek(input.filePointer + chunkSize)
                    }
                    else -> input.seek(input.filePointer + chunkSize)
                }
                if (chunkSize and 1L == 1L) {
                    require(input.filePointer < input.length()) { "missing chunk padding" }
                    input.seek(input.filePointer + 1)
                }
            }
            val pcm = encoding == 1
            val float = encoding == 3
            require(pcm || float) { "WAV encoding must be PCM or IEEE float" }
            require(channels in 1..32 && sampleRate > 0) { "invalid sample rate or channel count" }
            require((pcm && bits in setOf(8, 16, 24, 32)) || (float && bits == 32)) { "unsupported WAV sample format" }
            require(blockAlign == channels * (bits / 8)) { "inconsistent block alignment" }
            require(dataOffset >= 0 && dataSize > 0 && dataSize % blockAlign == 0L) { "empty or incomplete data chunk" }

            val frames = dataSize / blockAlign
            var peak = 0.0
            input.seek(dataOffset)
            var frame = 0L
            while (frame < frames) {
                repeat(channels) {
                    val sample = input.readWavSample(pcm, bits)
                    require(sample.isFinite()) { "contains a non-finite sample" }
                    peak = maxOf(peak, abs(sample))
                }
                frame++
            }
            return ValidatedWav(pcm, bits, sampleRate, channels, frames, peak)
        }
    }

    private data class ValidatedMp3(val sizeBytes: Long)

    /** Rejects empty output and RIFF data disguised with an MP3 extension. */
    private fun inspectMp3(path: Path): ValidatedMp3 {
        require(Files.isRegularFile(path) && Files.size(path) > 0) { "MP3 output is missing or empty: $path" }
        val header = Files.newInputStream(path).use { it.readNBytes(10_240) }
        require(header.size >= 2) { "MP3 output is too short: $path" }
        require(!header.copyOfRange(0, minOf(4, header.size)).decodeToString().equals("RIFF", ignoreCase = false)) {
            "MP3 output is a RIFF/WAVE container disguised as .mp3: $path"
        }
        val hasId3 = header.size >= 3 && header.copyOfRange(0, 3).decodeToString() == "ID3"
        val hasFrame = (0 until header.size - 1).any { index ->
            (header[index].toInt() and 0xff) == 0xff && (header[index + 1].toInt() and 0xe0) == 0xe0
        }
        require(hasId3 || hasFrame) { "MP3 output has no ID3 or MPEG frame signature: $path" }
        return ValidatedMp3(Files.size(path))
    }

    /** Reads only license metadata so release generation does not require local sample copies. */
    private fun instrumentLicenseReport(projectRoot: Path): List<InstrumentLicenseMetadata> {
        val arrangementPath = projectRoot.resolve("arrangement.json")
        if (!Files.isRegularFile(arrangementPath)) return emptyList()
        val project = ProjectStore.read(projectRoot)
        val used = usedGeneratedInstrumentNames(projectRoot, project, arrangementPath)
        if (used.isEmpty()) return emptyList()
        val soundsRoot = Path.of(System.getenv("MUSIC_SOUNDS_ROOT")?.takeIf { it.isNotBlank() } ?: "sounds")
        val instruments = json.decodeFromString<InstrumentRegistryFile>(
            Files.readString(soundsRoot.resolve("instruments.json"), StandardCharsets.UTF_8)
        )
        val licenses = json.decodeFromString<LicenseRegistryFile>(
            Files.readString(soundsRoot.resolve("LICENSES.json"), StandardCharsets.UTF_8)
        )
        return used.map { name ->
            val entry = instruments.instruments[name]
                ?: throw IllegalArgumentException("Arrangement uses unlicensed instrument '$name'")
            val license = licenses.libraries[entry.licenseId]
                ?: throw IllegalArgumentException("Instrument '$name' references missing license '${entry.licenseId}'")
            InstrumentLicenseMetadata(
                instrument = name,
                licenseId = entry.licenseId,
                displayName = license.displayName,
                commercialUse = license.commercialUse,
                attributionRequired = license.attributionRequired,
                attribution = license.attributionText
            )
        }
    }

    private fun usedGeneratedInstrumentNames(projectRoot: Path, project: Project, arrangementPath: Path): List<String> {
        val arrangementText = Files.readString(arrangementPath, StandardCharsets.UTF_8)
        val version = json.parseToJsonElement(arrangementText).jsonObject["version"]
            ?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val names = if (version == DetailedArrangement.CURRENT_VERSION) {
            val arrangement = json.decodeFromString<DetailedArrangement>(arrangementText)
            arrangement.requireValid(detailedArrangementInput(projectRoot, project))
            arrangement.sections.flatMap { it.instruments }
                .filter { it.mode != InstrumentMode.SOURCE }
                .map { it.name }
        } else {
            val arrangement = json.decodeFromString<Arrangement>(arrangementText)
            arrangement.requireValid(project.parts.map { it.id })
            arrangement.sections.flatMap { it.instruments }
                .filter { it.mode != InstrumentMode.SOURCE }
                .map { it.name }
        }
        return names.distinct().sorted()
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
        val profile: String,
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
        val valueOptions = setOf("--input", "--output", "--profile", "--quantize", "--strength", "--min-note-ms", "--min-velocity")
        val flagOptions = setOf("--normalize-velocity", "--clean-sustain")
        val values = mutableMapOf<String, String>()
        val flags = mutableSetOf<String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in valueOptions || option in flagOptions) {
                "Usage: midi-clean --input <raw.mid> --output <clean.mid> [--profile conservative|transcription-safe|tighten-timing]"
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
        val profile = values["--profile"] ?: "conservative"
        require(profile in setOf("conservative", "transcription-safe", "tighten-timing")) {
            "Profile must be one of: conservative, transcription-safe, tighten-timing"
        }
        val quantize = values["--quantize"]
        require(quantize == null || quantize in setOf("1/4", "1/8", "1/16", "1/32")) {
            "Quantize must be one of: 1/4, 1/8, 1/16, 1/32"
        }
        val strength = values["--strength"]?.toDoubleOrNull() ?: 0.0
        require(strength in 0.0..1.0) { "Strength must be from 0.0 to 1.0" }
        require(quantize != null || strength == 0.0) { "--strength requires --quantize" }
        if (profile == "tighten-timing") {
            require(quantize != null) { "--profile tighten-timing requires --quantize" }
            require("--strength" in values && strength > 0.0) {
                "--profile tighten-timing requires --strength from 0.0 to 1.0 (exclusive of 0.0)"
            }
        } else {
            require(quantize == null) { "--quantize requires --profile tighten-timing" }
        }
        val minNoteMs = values["--min-note-ms"]?.toIntOrNull() ?: 50
        val minVelocity = values["--min-velocity"]?.toIntOrNull() ?: 8
        require(minNoteMs in 0..60_000) { "--min-note-ms must be from 0 to 60000" }
        require(minVelocity in 0..127) { "--min-velocity must be from 0 to 127" }
        require(profile != "conservative" || flags.intersect(setOf("--normalize-velocity", "--clean-sustain")).isEmpty()) {
            "--normalize-velocity and --clean-sustain require --profile transcription-safe or tighten-timing"
        }
        return MidiCleanOptions(
            input, output, profile, quantize, strength, minNoteMs, minVelocity,
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

    private fun parseArrangeDetailOptions(arguments: List<String>): Map<String, String> {
        val options = mutableMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in ARRANGE_DETAIL_OPTIONS) { "Unknown arrange-detail option: $option" }
            require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) { "Missing value for $option" }
            require(options.put(option, arguments[index + 1]) == null) { "Duplicate arrange-detail option: $option" }
            index += 2
        }
        require("--project" in options) {
            "Usage: arrange-detail --project <project-directory> [--planner deterministic|qwen]"
        }
        return options
    }

    private fun parseCriticOptions(arguments: List<String>): Map<String, String> {
        val options = mutableMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val option = arguments[index]
            require(option in CRITIC_OPTIONS) { "Unknown critic option: $option" }
            require(index + 1 < arguments.size && !arguments[index + 1].startsWith("--")) { "Missing value for $option" }
            require(options.put(option, arguments[index + 1]) == null) { "Duplicate critic option: $option" }
            index += 2
        }
        require("--project" in options) {
            "Usage: critic --project <project-directory> [--planner deterministic|qwen]"
        }
        return options
    }

    private fun parseGenerateOptions(arguments: List<String>): Map<String, String> {
        require(arguments.size == 2 && arguments[0] == "--project") {
            "Usage: generate bass|drums|pad|transitions --project <project-directory>"
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

    private fun requireMidiArtifact(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "$stage did not create a MIDI file: $path" }
        val header = Files.newInputStream(path).use { it.readNBytes(4).decodeToString() }
        require(header == "MThd") { "$stage did not create a MIDI file: $path" }
    }

    private val ADD_OPTIONS = setOf("--id", "--file", "--role", "--transcribe")
    private val ARRANGE_OPTIONS = setOf("--project", "--planner", "--structure", "--instruments", "--style")
    private val ARRANGE_DETAIL_OPTIONS = setOf("--project", "--planner")
    private val CRITIC_OPTIONS = setOf("--project", "--planner")
    private const val DETERMINISTIC_PLANNER = "deterministic"
    private const val QWEN_PLANNER = "qwen"
    private val WAV_EXTENSIONS = setOf("wav", "wave")
    private val MIDI_EXTENSIONS = setOf("mid", "midi")

    /** Resolve the validated sound-library root once per CLI invocation. */
    private fun resolveLibraryRoot(): SoundLibraryLocation.Success {
        val locator = SoundLibraryLocator()
        return when (val result = locator.locate(configuredRoot = null)) {
            is SoundLibraryLocation.Success -> result
            is SoundLibraryLocation.Failure -> throw IllegalArgumentException(
                "Sound library not found. Checked: ${result.candidates.joinToString("; ") { "${it.path} (${it.reason})" }}. " +
                    "Set MUSIC_SOUNDS_ROOT to an absolute path containing sounds/instruments.json."
            )
        }
    }

    /** Build a SfizzInstrumentRenderer wired to the resolved library root. */
    private fun buildSfizzRenderer(): SfizzInstrumentRenderer {
        val libRoot = resolveLibraryRoot().root
        return SfizzInstrumentRenderer(InstrumentRegistryLoader(libRoot))
    }

    private object NoOpErrorReporter : ai.music.workstation.model.ErrorReporter {
        override fun report(message: String) = Unit
        override fun report(message: String, cause: Throwable) = Unit
    }
}

private fun RandomAccessFile.readFourCcLE(): String = ByteArray(4).also { readFully(it) }.toString(StandardCharsets.US_ASCII)
private fun RandomAccessFile.readUInt16LE(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)
private fun RandomAccessFile.readUInt32LE(): Long = readUInt16LE().toLong() or (readUInt16LE().toLong() shl 16)
private fun RandomAccessFile.readWavSample(pcm: Boolean, bits: Int): Double = when {
    !pcm && bits == 32 -> java.lang.Float.intBitsToFloat(readUInt32LE().toInt()).toDouble()
    pcm && bits == 8 -> (readUnsignedByte().toDouble() - 128.0) / 128.0
    pcm && bits == 16 -> readUInt16LE().toShort().toDouble() / 32_768.0
    pcm && bits == 24 -> {
        val value = readUnsignedByte() or (readUnsignedByte() shl 8) or (readUnsignedByte() shl 16)
        (if (value and 0x80_0000 != 0) value or -0x1_000000 else value).toDouble() / 8_388_608.0
    }
    pcm && bits == 32 -> readUInt32LE().toInt().toDouble() / 2_147_483_648.0
    else -> error("Unsupported WAV sample format")
}
