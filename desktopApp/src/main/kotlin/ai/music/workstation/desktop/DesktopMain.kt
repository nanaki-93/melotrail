package ai.music.workstation.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ai.music.workstation.application.DefaultProjectApplicationService
import ai.music.workstation.application.DefaultArrangementApplicationService
import ai.music.workstation.application.DefaultMixApplicationService
import ai.music.workstation.application.DefaultBuildApplicationService
import ai.music.workstation.application.BuildAudioWorker
import ai.music.workstation.application.DefaultPartPreviewApplicationService
import ai.music.workstation.application.LegacyPartAnalysisService
import ai.music.workstation.application.MidiPreparationService
import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.application.DefaultAudioPreparationApplicationService
import ai.music.workstation.preparation.WorkerInputInspectionBoundary
import ai.music.workstation.preparation.InputCleanupApplicationService
import ai.music.workstation.preparation.WorkerAudioCleanupBoundary
import ai.music.workstation.preparation.TranscriptionQualityGateService
import ai.music.workstation.preparation.WorkerTranscriptionBoundary
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.InstrumentRegistryLoader
import ai.music.workstation.arrangement.SoundLibraryLocator
import ai.music.workstation.arrangement.SoundLibraryLocation
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.MidiCleanCommand
import ai.music.workstation.worker.MasterCommand
import ai.music.workstation.worker.MP3ExportCommand
import ai.music.workstation.worker.RepairCommand
import ai.music.workstation.worker.RepairSpec
import ai.music.workstation.worker.TranscribeCommand
import ai.music.workstation.worker.WorkerClient
import ai.music.workstation.worker.WorkerError
import ai.music.workstation.worker.WorkerStatus
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.Path

fun main() = application {
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
    val sfizzRenderer = ai.music.workstation.arrangement.SfizzInstrumentRenderer(
        InstrumentRegistryLoader(libraryRoot)
    )
    val viewModel = WorkspaceViewModel(
        projectService = projectService,
        fileDialogs = SwingDesktopFileDialogs(),
        runtimeReadinessService = defaultRuntimeReadinessService { librarySettings.refresh().resolvedRoot },
        libraryRoot = libraryRoot,
        arrangementService = arrangementService,
        mixService = mixService,
        buildService = DefaultBuildApplicationService(arrangementService, mixService, sfizzRenderer, DesktopBuildWorker(client)),
        player = player,
        partPreviewService = DefaultPartPreviewApplicationService(sfizzRenderer),
        audioPreparationService = DefaultAudioPreparationApplicationService(
            projectService,
            InputCleanupApplicationService(WorkerAudioCleanupBoundary(client)),
            TranscriptionQualityGateService(WorkerTranscriptionBoundary(client))
        ),
        preferences = preferences,
        soundLibrarySettings = SoundLibrarySettingsService(preferences, activeRoot = libraryRoot),
        operationLogger = operationLogger
    )
    Window(
        state = desktopWindowState,
        onCloseRequest = {
            if (viewModel.requestClose()) {
                viewModel.close()
                exitApplication()
            }
        },
        title = "Personal AI Music Arranger"
    ) {
        window.minimumSize = java.awt.Dimension(900, 620)
        MusicWorkstationTheme {
            WorkspaceApp(viewModel) {
                exitApplication()
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
    /** An inert, non-CWD fallback lets the settings screen recover a missing library. */
    fun unavailableLibraryRoot(): Path = Path.of(System.getProperty("java.io.tmpdir"), "personal-ai-music-arranger", "missing-sound-library")

    fun workerClient(): WorkerClient {
        val logger = DefaultLogger()
        return WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
    }

    fun projectService(): ProjectApplicationService {
        val client = workerClient()
        return DefaultProjectApplicationService(
            midiPreparation = DesktopMidiPreparationService(client),
            legacyPartAnalysis = DesktopLegacyPartAnalysisService(client),
            inputInspection = WorkerInputInspectionBoundary(client)
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
            Files.createDirectories(checkNotNull(output.parent))
            val response = client.execute(MidiCleanCommand(input.toString(), output.toString()))
            require(response.status == WorkerStatus.COMPLETED) { cleanupFailureMessage(response.error) }
            requireMidi(output, "MIDI cleanup")
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
