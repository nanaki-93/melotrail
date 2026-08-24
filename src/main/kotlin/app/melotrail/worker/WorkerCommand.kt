package app.melotrail.worker

import app.melotrail.model.DSPSettings
import kotlinx.serialization.Serializable

sealed class WorkerCommand {
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
    /** Pinned protocol revision for bounded beat/onset/downbeat evidence. */
    val version: Int = 2,
    val options: AnalyzeOptions = AnalyzeOptions()
) : WorkerCommand() {
    init { require(version == 2) { "Unsupported analyze request version: $version" } }
}

data class ApplyDSPCommand(
    val path: String,
    val settings: DSPSettings,
    val outputFormat: String? = null
) : WorkerCommand() {
}

data class RepairCommand(
    val path: String,
    val repairs: List<RepairSpec>,
    val outputPath: String? = null
) : WorkerCommand() {
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
}

data class MP3ConvertCommand(
    val path: String,
    val outputPath: String
) : WorkerCommand() {
}

data class MP3ExportCommand(
    val path: String,
    val outputPath: String,
    val bitrateKbps: Int = 320
) : WorkerCommand() {
}

/** One local, lossless decode preview for the selected lossless delivery master. */
data class CodecPreviewCommand(
    val path: String,
    val codec: String,
    val encodedPath: String,
    val decodedPath: String,
    val bitrateKbps: Int = 320
) : WorkerCommand() {
    init { require(codec in setOf("aac", "mp3")) { "Unsupported delivery-preview codec: $codec" } }
}

data class TranscribeCommand(
    val path: String,
    val outputPath: String,
    val instrument: String
) : WorkerCommand() {
}

data class CleanMidiCommand(
    val path: String,
    val outputPath: String,
    val version: Int = 2,
    val profile: String = "transcription-safe",
    val quantize: String? = null,
    val strength: Double = 0.0,
    val minNoteMs: Int = 50,
    val minVelocity: Int = 8,
    val normalizeVelocity: Boolean = false,
    val cleanSustain: Boolean = false,
    val preserveGraceNotes: Boolean = false,
    val graceNoteMaxMs: Int = 80,
    val graceVelocityMax: Int = 32,
    val duplicateOnsetWindowMs: Int = 35
) : WorkerCommand() {
}

/** Read-only validation and measurement of one already project-confined input. */
data class InputInspectionCommand(
    val path: String
) : WorkerCommand() {
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
}
