package ai.music.workstation.cli

import ai.music.workstation.audio.AudioComparison
import ai.music.workstation.audio.AudioComparisonReport
import java.nio.file.Path
import java.util.Locale

/** A small CLI boundary for deterministic, read-only dry/LoFi A/B checks. */
object AudioComparisonCommand {
    fun handles(args: Array<String>): Boolean = args.firstOrNull() == "compare"

    fun execute(args: Array<String>): String {
        if (args.getOrNull(1) in setOf("--help", "-h")) return usage()

        var json = false
        var align = false
        val paths = mutableListOf<String>()
        for (argument in args.drop(1)) {
            when (argument) {
                "--json" -> json = true
                "--align" -> align = true
                else -> {
                    require(!argument.startsWith("-")) { "Unknown compare option: $argument" }
                    paths += argument
                }
            }
        }
        require(paths.size == 2) { usage() }

        val report = AudioComparison.compareFiles(Path.of(paths[0]), Path.of(paths[1]), allowAlignment = align)
        return if (json) AudioComparison.renderJson(report) else renderHuman(report)
    }

    internal fun renderHuman(report: AudioComparisonReport): String = buildString {
        appendLine("Audio comparison (read-only)")
        appendLine("A: ${report.a.sampleRate} Hz, ${report.a.channels} ch, ${report.a.frameCount} frames, ${number(report.a.durationSeconds)} s")
        appendLine("B: ${report.b.sampleRate} Hz, ${report.b.channels} ch, ${report.b.frameCount} frames, ${number(report.b.durationSeconds)} s")
        appendLine("RMS: A ${number(report.a.rms)} (${number(report.a.rmsDbFs)} dBFS), B ${number(report.b.rms)} (${number(report.b.rmsDbFs)} dBFS), delta ${number(report.rmsAbsoluteDelta)} / ${number(report.rmsDeltaDb)} dB")
        appendLine("Peak: A ${number(report.a.peak)}, B ${number(report.b.peak)}, delta ${number(report.peakAbsoluteDelta)}")
        appendLine("Difference: mean ${number(report.meanAbsoluteSampleDifference)}, max ${number(report.maxAbsoluteSampleDifference)}, null RMS ${number(report.nullDifferenceRms)}")
        appendLine("Changed frames: ${number(report.changedFrameRatio * 100.0)}% (tolerance ${AudioComparison.CHANGE_TOLERANCE})")
        appendLine("Spectrum: centroid delta ${number(report.spectralCentroidDeltaHz)} Hz; low/mid/high ${number(report.lowBandEnergyDeltaDb)} / ${number(report.midBandEnergyDeltaDb)} / ${number(report.highBandEnergyDeltaDb)} dB")
        append("Timeline: ${if (report.alignmentMismatch) "mismatch (${report.frameCountDifference} frames, ${number(report.durationDifferenceSeconds)} s); diagnostic truncation used" else "matched"}; FFT ${AudioComparison.FFT_WINDOW_SIZE}-frame Hann, ${AudioComparison.FFT_HOP_SIZE}-frame hop")
    }

    private fun usage(): String = "Usage: music-cli compare <a.wav> <b.wav> [--json] [--align]"
    private fun number(value: Double): String = String.format(Locale.ROOT, "%.6f", value)
}
