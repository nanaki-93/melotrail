package ai.music.workstation.cli

import ai.music.workstation.dsp.LOFIPresets
import ai.music.workstation.model.DSPSettings
import ai.music.workstation.model.LoudnessReport
import ai.music.workstation.model.MasteringState
import ai.music.workstation.model.TrackType
import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * Parsed CLI arguments for the processing pipeline.
 */
@Serializable
data class CliArgs(
    val inputPath: String,
    val outputPath: String,
    val preset: String = "Warm Cassette",
    val enableRepair: Boolean = true,
    val enableMastering: Boolean = true,
    val mastering: MasteringConfig = MasteringConfig(),
    val dryRun: Boolean = false,
    val stages: List<String> = emptyList(),
    val verbose: Boolean = false
) {
    companion object {
        val VALID_STAGES = listOf("analyze", "repair", "lofi", "master")
        val VALID_PRESETS = LOFIPresets.DEFAULT_PRESETS.map { it.name }
    }
}

/**
 * Mastering configuration for the CLI.
 */
@Serializable
data class MasteringConfig(
    val eqEnabled: Boolean = true,
    val compressorEnabled: Boolean = true,
    val saturationEnabled: Boolean = true,
    val stereoEnabled: Boolean = true,
    val limiterEnabled: Boolean = true,
    val targetPeakDb: Double = -1.0,
    val targetLoudnessLufs: Double = -14.0
)

/**
 * Holds the results of each pipeline stage.
 */
data class PipelineResult(
    val analysis: AnalysisResult? = null,
    val repairedPath: Path? = null,
    val lofiPath: Path? = null,
    val masteredPath: Path? = null,
    val loudnessReport: LoudnessReport? = null,
    val totalDurationMs: Long = 0L
)

data class AnalysisResult(
    val bpm: Double?,
    val key: String?,
    val duration: Double,
    val sampleRate: Int,
    val channels: Int,
    val loudness: LoudnessInfo?,
    val qualityIssues: List<QualityIssue>
)

data class LoudnessInfo(
    val integratedLUFS: Double,
    val truePeak: Double,
    val rms: Double
)

data class QualityIssue(
    val type: String,
    val position: Double?,
    val severity: String,
    val description: String
)

/**
 * Enum for pipeline stages.
 */
enum class PipelineStage(val label: String) {
    ANALYZE("Analysis"),
    REPAIR("Repair"),
    LOFI("LoFi DSP"),
    MASTER("Mastering");

    companion object {
        fun fromString(name: String): PipelineStage? =
            values().find { it.name.lowercase() == name.lowercase() }
    }
}
