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
    val version: Int = 2,
    val profile: String = "conservative",
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

/** Strict schema shared with the worker's conservative `/cleanup` endpoint. */
sealed interface AudioCleanupOperation {
    val wireType: String

    data object DcRemoval : AudioCleanupOperation { override val wireType = "dc_removal" }

    data class ClipRepair(val threshold: Double = 0.999) : AudioCleanupOperation {
        override val wireType = "clip_repair"
        init { require(threshold in 0.95..1.0) { "clip threshold must be between 0.95 and 1.0" } }
    }

    data class Declick(val threshold: Double = 0.9) : AudioCleanupOperation {
        override val wireType = "declick"
        init { require(threshold in 0.5..0.99) { "declick threshold must be between 0.5 and 0.99" } }
    }

    data class HumRemoval(val frequencyHz: Int = 60) : AudioCleanupOperation {
        override val wireType = "hum_removal"
        init { require(frequencyHz == 50 || frequencyHz == 60) { "hum frequency must be 50 or 60 Hz" } }
    }

    data class NoiseReduction(val strength: Double = 0.35) : AudioCleanupOperation {
        override val wireType = "noise_reduction"
        init { require(strength in 0.05..0.5) { "noise strength must be between 0.05 and 0.5" } }
    }
}

data class AudioCleanupCommand(
    val path: String,
    val outputPath: String,
    val operations: List<AudioCleanupOperation>
) : WorkerCommand() {
    override val commandName: String = "cleanup"
}
