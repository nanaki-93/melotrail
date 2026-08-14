package ai.music.workstation.worker

import ai.music.workstation.model.DSPSettings
import kotlinx.serialization.Serializable

sealed class WorkerCommand {
    abstract val commandName: String
}

@Serializable
data class AnalyzeOptions(
    val detectBPM: Boolean = true,
    val detectKey: Boolean = true,
    val detectLoudness: Boolean = true,
    val detectOnsets: Boolean = true,
    val detectBeats: Boolean = true,
    val detectSections: Boolean = true
)

data class AnalyzeCommand(
    val path: String,
    val options: AnalyzeOptions = AnalyzeOptions()
) : WorkerCommand() {
    override val commandName: String = "analyze"
}

data class ApplyDSPCommand(
    val path: String,
    val settings: DSPSettings,
    val outputFormat: String? = null
) : WorkerCommand() {
    override val commandName: String = "apply_dsp"
}

data class RepairCommand(
    val path: String,
    val repairs: List<RepairSpec>,
    val outputPath: String? = null
) : WorkerCommand() {
    override val commandName: String = "repair"
}

data class RepairSpec(
    val type: String,
    val params: Map<String, Any> = emptyMap()
)

data class MasterCommand(
    val path: String,
    val settings: Map<String, Any>,
    val outputPath: String? = null
) : WorkerCommand() {
    override val commandName: String = "master"
}

object HealthCheck : WorkerCommand() {
    override val commandName: String = "health"
}

data class MP3ConvertCommand(
    val path: String,
    val outputPath: String
) : WorkerCommand() {
    override val commandName: String = "mp3_convert"
}

data class MP3ExportCommand(
    val path: String,
    val outputPath: String,
    val bitrateKbps: Int = 320
) : WorkerCommand() {
    override val commandName: String = "mp3_export"
}

data class TranscribeCommand(
    val path: String,
    val outputPath: String,
    val instrument: String
) : WorkerCommand() {
    override val commandName: String = "transcribe"
}

data class MidiCleanCommand(
    val path: String,
    val outputPath: String,
    val quantize: String? = null,
    val strength: Double = 0.0,
    val minNoteMs: Int = 50,
    val minVelocity: Int = 8,
    val normalizeVelocity: Boolean = false,
    val cleanSustain: Boolean = false
) : WorkerCommand() {
    override val commandName: String = "midi-clean"
}

/** Read-only validation and measurement of one already project-confined input. */
data class InputInspectionCommand(
    val path: String
) : WorkerCommand() {
    override val commandName: String = "inspect-input"
}
