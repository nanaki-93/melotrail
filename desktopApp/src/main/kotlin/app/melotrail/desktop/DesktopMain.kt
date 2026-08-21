package app.melotrail.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.melotrail.application.DefaultProjectApplicationService
import app.melotrail.application.DefaultArrangementApplicationService
import app.melotrail.application.DefaultMixApplicationService
import app.melotrail.application.DefaultBuildApplicationService
import app.melotrail.application.DefaultCohesionApplicationService
import app.melotrail.application.EnsembleMidiPreparation
import app.melotrail.application.CohesionPreviewPreparation
import app.melotrail.application.BuildAudioWorker
import app.melotrail.application.DefaultPartPreviewApplicationService
import app.melotrail.application.DefaultReleaseExportApplicationService
import app.melotrail.application.ReleaseMp3Exporter
import app.melotrail.application.LegacyPartAnalysisService
import app.melotrail.application.MidiPreparationService
import app.melotrail.application.AutomaticImportProcessors
import app.melotrail.application.ProjectApplicationService
import app.melotrail.application.StageProcessorRegistry
import app.melotrail.application.StageRunner
import app.melotrail.application.DefaultAudioPreparationApplicationService
import app.melotrail.preparation.WorkerInputInspectionBoundary
import app.melotrail.preparation.InputCleanupApplicationService
import app.melotrail.preparation.WorkerAudioCleanupBoundary
import app.melotrail.preparation.TranscriptionQualityGateService
import app.melotrail.preparation.WorkerTranscriptionBoundary
import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.SoundLibraryLocator
import app.melotrail.arrangement.SoundLibraryLocation
import app.melotrail.profile.BundledCompositionProfileCatalog
import app.melotrail.profile.CompositionProfileCatalog
import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.DefaultLogger
import app.melotrail.worker.AnalyzeCommand
import app.melotrail.worker.AnalyzeOptions
import app.melotrail.worker.CleanMidiCommand
import app.melotrail.worker.MasterCommand
import app.melotrail.worker.MP3ExportCommand
import app.melotrail.worker.RepairCommand
import app.melotrail.worker.RepairSpec
import app.melotrail.worker.TranscribeCommand
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerError
import app.melotrail.worker.WorkerStatus
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

fun main() {
    // The catalog is application-owned data, loaded here so desktop and future adapters share one validated source.
    DesktopServiceComposition.compositionProfiles()
    application {
    val desktopWindowState = rememberWindowState(placement = WindowPlacement.Maximized)
    val preferences = JvmDesktopPreferences()
    val librarySettings = SoundLibrarySettingsService(preferences)
    val libraryRoot = librarySettings.refresh().resolvedRoot ?: DesktopServiceComposition.unavailableLibraryRoot()
    val arrangementService = DefaultArrangementApplicationService(libraryRoot = libraryRoot)
    val mixService = DefaultMixApplicationService()
    val client = DesktopServiceComposition.workerClient()
    val projectService = DesktopServiceComposition.projectService()
    val operationLogger = LocalDesktopOperationLogger()
    val player = JvmAudioPlayer(failureReporter = { failure ->
        operationLogger.event("playback", failure.stage.name.lowercase(), failure = failure.cause)
    })
    val sfizzRenderer = app.melotrail.arrangement.SfizzInstrumentRenderer(
        InstrumentRegistryLoader(libraryRoot)
    )
    val cohesionService = DefaultCohesionApplicationService(
        ensemblePreparation = EnsembleMidiPreparation { root, progress -> arrangementService.generateRequiredMidi(root, progress) },
        previewPreparation = CohesionPreviewPreparation { root, input ->
            app.melotrail.arrangement.FullSongCohesionPreviewRenderer(sfizzRenderer, libraryRoot).render(root, input)
        }
    )
    val viewModel = WorkspaceViewModel(
        projectService = projectService,
        fileDialogs = SwingDesktopFileDialogs(),
        runtimeReadinessService = defaultRuntimeReadinessService { librarySettings.refresh().resolvedRoot },
        libraryRoot = libraryRoot,
        arrangementService = arrangementService,
        cohesionService = cohesionService,
        mixService = mixService,
        buildService = DefaultBuildApplicationService(arrangementService, mixService, sfizzRenderer, DesktopBuildWorker(client), cohesionService),
        player = player,
        partPreviewService = DefaultPartPreviewApplicationService(sfizzRenderer),
        audioPreparationService = DefaultAudioPreparationApplicationService(
            projectService,
            InputCleanupApplicationService(WorkerAudioCleanupBoundary(client))
        ),
        preferences = preferences,
        soundLibrarySettings = SoundLibrarySettingsService(preferences, activeRoot = libraryRoot),
        operationLogger = operationLogger,
        releaseExportService = DefaultReleaseExportApplicationService(mp3Exporter = DesktopReleaseMp3Exporter(client))
    )
    Window(
        state = desktopWindowState,
        onCloseRequest = {
            if (viewModel.requestClose()) {
                viewModel.close()
                exitApplication()
            }
        },
        title = "Melotrail"
    ) {
        window.minimumSize = java.awt.Dimension(900, 620)
        MelotrailTheme {
            WorkspaceApp(viewModel) {
                exitApplication()
            }
        }
    }
    }
}

/**
 * The desktop adapter composes the same public application service used by the
 * CLI. It supplies only local worker boundaries; the service owns imports,
 * cleanup, analysis, registration, and atomic project writes.
 */
object DesktopServiceComposition {
    fun compositionProfiles(): CompositionProfileCatalog = BundledCompositionProfileCatalog.load()

    /** An inert, non-CWD fallback lets the settings screen recover a missing library. */
    fun unavailableLibraryRoot(): Path = Path.of(System.getProperty("java.io.tmpdir"), "melotrail", "missing-sound-library")

    fun workerClient(): WorkerClient {
        val logger = DefaultLogger()
        return WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
    }

    /** Compatibility helper for callers that need recovery without import processors. */
    fun stageRunner(): StageRunner = StageRunner(StageProcessorRegistry(emptyList()))

    fun projectService(stageRunner: StageRunner? = null): ProjectApplicationService {
        val client = workerClient()
        val preparation = DesktopMidiPreparationService(client)
        val inspection = WorkerInputInspectionBoundary(client)
        val importRunner = stageRunner ?: StageRunner(AutomaticImportProcessors(inspection, preparation).registry())
        return DefaultProjectApplicationService(
            midiPreparation = preparation,
            legacyPartAnalysis = DesktopLegacyPartAnalysisService(client),
            inputInspection = inspection,
            transcriptionQualityGate = TranscriptionQualityGateService(WorkerTranscriptionBoundary(client)),
            compositionProfiles = compositionProfiles(),
            stageRunRecovery = importRunner,
            automaticImportRunner = importRunner
        )
    }

    private class DesktopMidiPreparationService(private val client: WorkerClient) : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) {
            Files.createDirectories(checkNotNull(output.parent))
            val response = client.execute(TranscribeCommand(input.toString(), output.toString(), "piano"))
            require(response.status == WorkerStatus.COMPLETED) { transcriptionFailureMessage(response.error) }
            requireMidi(output, "Transcription")
        }

        override suspend fun clean(input: Path, output: Path) {
            clean(input, output, MidiCleanupOptions())
        }

        override suspend fun clean(input: Path, output: Path, options: MidiCleanupOptions) {
            Files.createDirectories(checkNotNull(output.parent))
            val profile = options.profile.name.lowercase().replace('_', '-')
            require(client.supportsMidiCleanup(options.requestVersion, profile)) {
                "Python worker does not support the required Clean MIDI profile version. Start the current local worker and retry."
            }
            val response = client.execute(CleanMidiCommand(
                input.toString(), output.toString(), options.requestVersion,
                profile, options.quantize, options.strength,
                options.minNoteMs, options.minVelocity, options.normalizeVelocity, options.cleanSustain
            ))
            require(response.status == WorkerStatus.COMPLETED) { cleanupFailureMessage(response.error) }
            requireMidi(output, "MIDI cleanup")
            val evidence = requireNotNull(response.output) { "MIDI cleanup worker returned no evidence" }
            require(evidence["version"]?.jsonPrimitive?.longOrNull == options.requestVersion.toLong()) { "MIDI cleanup worker returned the wrong contract version" }
            require(evidence["profile"]?.jsonPrimitive?.contentOrNull == options.profile.name.lowercase().replace('_', '-')) { "MIDI cleanup worker returned the wrong profile" }
            require(evidence["inputSha256"]?.jsonPrimitive?.contentOrNull == sha256(input)) { "MIDI cleanup worker input fingerprint did not match raw MIDI" }
            require(evidence["outputSha256"]?.jsonPrimitive?.contentOrNull == sha256(output)) { "MIDI cleanup worker output fingerprint did not match cleaned MIDI" }
        }
    }

    private class DesktopLegacyPartAnalysisService(private val client: WorkerClient) : LegacyPartAnalysisService {
        override suspend fun analyze(source: Path): PartAnalysis {
            val response = client.execute(AnalyzeCommand(source.toString(), AnalyzeOptions(detectSections = false)))
            require(response.status == WorkerStatus.COMPLETED) {
                "Part analysis failed: ${response.error?.message ?: "Unknown worker error"}"
            }
            val output = response.output.orEmpty()
            fun double(name: String) = output[name]?.jsonPrimitive?.doubleOrNull
                ?: throw IllegalArgumentException("Worker analysis did not return $name")
            fun long(name: String) = output[name]?.jsonPrimitive?.longOrNull
                ?: throw IllegalArgumentException("Worker analysis did not return $name")
            val key = output["key"]?.jsonObject
            return PartAnalysis(
                duration = double("duration"), sampleRate = long("sampleRate").toInt(), channels = long("channels").toInt(),
                frameCount = long("frameCount"), peak = double("peak"), rms = double("rms"),
                nearSilence = output["nearSilence"]?.jsonPrimitive?.booleanOrNull
                    ?: throw IllegalArgumentException("Worker analysis did not return nearSilence"),
                bpm = output["bpm"]?.jsonPrimitive?.doubleOrNull,
                keyRoot = key?.get("root")?.jsonPrimitive?.contentOrNull,
                keyMode = key?.get("mode")?.jsonPrimitive?.contentOrNull,
                keyConfidence = output["keyConfidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                leadingSilenceSeconds = output["leadingSilenceSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                trailingSilenceSeconds = output["trailingSilenceSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                onsetsSeconds = output["onsets"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull } ?: emptyList()
            )
        }
    }

    private fun requireMidi(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "$stage did not create a MIDI file: $path" }
        require(Files.newInputStream(path).use { it.readNBytes(4).decodeToString() } == "MThd") {
            "$stage did not create a MIDI file: $path"
        }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun transcriptionFailureMessage(error: WorkerError?): String {
        val stage = when (error?.type) {
            "ValidationError" -> "validation"
            "DecodeError" -> "MP3 decode"
            "ModelError" -> "model inference"
            "OutputValidationError" -> "MIDI output validation"
            else -> "worker"
        }
        return "Transcription failed during $stage: ${error?.message ?: "Unknown worker error"}"
    }

    private fun cleanupFailureMessage(error: WorkerError?): String =
        "MIDI cleanup failed during ${if (error?.type in setOf("MidiCleanupValidationError", "MidiCleanupOutputValidationError")) "validation" else "worker"}: ${error?.message ?: "Unknown worker error"}"
}

private class DesktopBuildWorker(private val client: WorkerClient) : BuildAudioWorker {
    override suspend fun healthCheck(): Boolean = client.healthCheck()
    override suspend fun repair(input: Path, output: Path) {
        val response = client.execute(RepairCommand(input.toString(), listOf(RepairSpec("dc_offset"), RepairSpec("clip_removal", mapOf("threshold" to 0.999, "max_run_samples" to 12))), output.toString()))
        require(response.status == WorkerStatus.COMPLETED) { "Repair failed: ${response.error?.message ?: "Unknown worker error"}" }
    }
    override suspend fun master(input: Path, output: Path) {
        val settings = mapOf<String, Any>(
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
        val response = client.execute(MasterCommand(input.toString(), settings, output.toString()))
        require(response.status == WorkerStatus.COMPLETED) { "Mastering failed: ${response.error?.message ?: "Unknown worker error"}" }
    }
    override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean {
        val response = client.execute(MP3ExportCommand(input.toString(), output.toString(), bitrateKbps))
        if (response.status == WorkerStatus.COMPLETED) return true
        if (response.error?.message?.contains("requires lameenc") == true) return false
        throw IllegalStateException("MP3 export failed: ${response.error?.message ?: "Unknown worker error"}")
    }
}

private class DesktopReleaseMp3Exporter(private val client: WorkerClient) : ReleaseMp3Exporter {
    override suspend fun available(): Boolean = client.runtimeStatus().mp3ExportAvailable
    override suspend fun export(input: Path, output: Path, bitrateKbps: Int): Boolean {
        val response = client.execute(MP3ExportCommand(input.toString(), output.toString(), bitrateKbps))
        if (response.status == WorkerStatus.COMPLETED) return true
        if (response.error?.message?.contains("requires lameenc") == true) return false
        throw IllegalStateException("MP3 export failed: ${response.error?.message ?: "Unknown worker error"}")
    }
}
