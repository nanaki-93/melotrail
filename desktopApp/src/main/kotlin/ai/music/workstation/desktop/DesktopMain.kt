package ai.music.workstation.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.music.workstation.application.DefaultProjectApplicationService
import ai.music.workstation.application.LegacyPartAnalysisService
import ai.music.workstation.application.MidiPreparationService
import ai.music.workstation.application.ProjectApplicationService
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.MidiCleanCommand
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
    val viewModel = WorkspaceViewModel(
        projectService = DesktopServiceComposition.projectService(),
        fileDialogs = SwingDesktopFileDialogs(),
        runtimeReadinessService = defaultRuntimeReadinessService()
    )
    Window(
        onCloseRequest = {
            viewModel.close()
            exitApplication()
        },
        title = "Personal AI Music Arranger"
    ) {
        window.minimumSize = java.awt.Dimension(900, 620)
        window.size = java.awt.Dimension(1440, 900)
        MusicWorkstationTheme {
            WorkspaceApp(viewModel)
        }
    }
}

/**
 * The desktop adapter composes the same public application service used by the
 * CLI. It supplies only local worker boundaries; the service owns imports,
 * cleanup, analysis, registration, and atomic project writes.
 */
object DesktopServiceComposition {
    fun projectService(): ProjectApplicationService {
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger)
        )
        return DefaultProjectApplicationService(
            midiPreparation = DesktopMidiPreparationService(client),
            legacyPartAnalysis = DesktopLegacyPartAnalysisService(client)
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
