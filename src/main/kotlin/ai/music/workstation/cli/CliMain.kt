package ai.music.workstation.cli

import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.logging.Logger
import ai.music.workstation.model.ErrorReporter
import ai.music.workstation.model.LoudnessReport
import ai.music.workstation.errors.AppError
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Main entry point for the CLI processing tool.
 *
 * This tool runs the full audio processing pipeline without the desktop app:
 *   1. Analysis → BPM, key, loudness, quality issues
 *   2. Repair → DC offset, clipping, normalization
 *   3. LoFi DSP → Tape, vinyl, sample-rate reduction, etc.
 *   4. Mastering → EQ, compression, saturation, limiting
 */
fun main(args: Array<String>) = runBlocking {
    if (args.size == 1 && args[0] in setOf("--help", "-h")) {
        println(CliParser.usage())
        println()
        println(ArrangementProjectCommands.usage())
        return@runBlocking
    }

    if (AudioComparisonCommand.handles(args)) {
        try {
            println(AudioComparisonCommand.execute(args))
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            System.exit(1)
        }
        return@runBlocking
    }

    if (ArrangementProjectCommands.handles(args)) {
        try {
            println(ArrangementProjectCommands.executeAsync(args))
        } catch (e: IllegalArgumentException) {
            System.err.println("Error: ${e.message}")
            System.exit(1)
        }
        return@runBlocking
    }

    val logger = DefaultLogger()
    val errorReporter = ai.music.workstation.errors.ErrorReporter(logger)

    // Parse arguments
    val cliArgs: CliArgs
    try {
        cliArgs = CliParser.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("\nError: ${e.message}")
        System.exit(1)
        return@runBlocking
    }

    if (cliArgs.verbose) {
        logger.info("CLI", "Verbose mode enabled")
    }

    // Start pipeline
    val pipeline = AudioPipeline(
        cliArgs = cliArgs,
        logger = logger,
        errorReporter = errorReporter
    )

    println("\n╔══════════════════════════════════════════════════════════╗")
    println("║         AI Music Workstation — CLI Pipeline             ║")
    println("╚══════════════════════════════════════════════════════════╝")
    println()

    val startTime = Instant.now()

    try {
        // Dry run mode
        if (cliArgs.dryRun) {
            println("[DRY RUN] Validating inputs...")
            println("  Input:  ${cliArgs.inputPath}")
            println("  Output: ${cliArgs.outputPath}")
            println("  Preset: ${cliArgs.preset}")
            println("  Repair: ${cliArgs.enableRepair}")
            println("  Master: ${cliArgs.enableMastering}")
            println("  Stages: ${cliArgs.stages.ifEmpty { listOf("analyze", "repair", "lofi", "master") }}")
            println()
            println("[DRY RUN] All inputs valid. No processing performed.")
            return@runBlocking
        }

        // Run the full pipeline
        val result = pipeline.run()

        val endTime = Instant.now()
        val duration = java.time.Duration.between(startTime, endTime).toMillis()

        // Print final report
        printFinalReport(result, duration)

        // Exit with appropriate code
        if (result.masteredPath != null) {
            println()
            println("✅ Pipeline completed successfully in ${duration}ms")
            println("   Output: ${result.masteredPath}")
            System.exit(0)
        } else {
            System.err.println()
            System.err.println("❌ Pipeline failed. See errors above.")
            System.exit(1)
        }

    } catch (e: Exception) {
        System.err.println()
        System.err.println("❌ Pipeline failed: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    }
}

/**
 * Prints the final pipeline report.
 */
private fun printFinalReport(result: PipelineResult, durationMs: Long) {
    println("\n╔══════════════════════════════════════════════════════════╗")
    println("║                      Pipeline Report                    ║")
    println("╚══════════════════════════════════════════════════════════╝")
    println()

    // Analysis results
    result.analysis?.let { analysis ->
        println("📊 Analysis")
        println("  Duration:     ${"%.2f".format(analysis.duration)}s")
        println("  Sample Rate:  ${analysis.sampleRate} Hz")
        println("  Channels:     ${analysis.channels}")
        println("  BPM:          ${analysis.bpm?.let { "%.1f".format(it) } ?: "N/A"}")
        println("  Key:          ${analysis.key ?: "N/A"}")
        analysis.loudness?.let { loudness ->
            println("  Loudness:     ${"%.1f".format(loudness.integratedLUFS)} LUFS")
            println("  True Peak:    ${"%.1f".format(loudness.truePeak)} dB")
            println("  RMS:          ${"%.1f".format(loudness.rms)} dB")
        }
        if (analysis.qualityIssues.isNotEmpty()) {
            println("  Issues found: ${analysis.qualityIssues.size}")
            for (issue in analysis.qualityIssues) {
                println("    - [${issue.severity}] ${issue.description}")
            }
        }
        println()
    }

    // Repair status
    if (result.repairedPath != null) {
        println("🔧 Repair: ✅ Applied")
        println("   Output: ${result.repairedPath.fileName}")
        println()
    }

    // LoFi status
    if (result.lofiPath != null) {
        println("🎵 LoFi DSP: ✅ Applied")
        println("   Output: ${result.lofiPath.fileName}")
        println()
    }

    // Mastering status
    if (result.masteredPath != null) {
        println("🎚️ Mastering: ✅ Applied")
        println("   Output: ${result.masteredPath.fileName}")
        result.loudnessReport?.let { report ->
            println("   Integrated: ${"%.1f".format(report.integratedLUFS)} LUFS")
            println("   True Peak:  ${"%.1f".format(report.truePeak)} dB")
            println("   RMS:        ${"%.1f".format(report.rms)} dB")
        }
        println()
    }

    println("⏱️  Total duration: ${durationMs}ms")
}
