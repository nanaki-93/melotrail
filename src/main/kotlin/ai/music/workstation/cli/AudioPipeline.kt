package ai.music.workstation.cli

import ai.music.workstation.dsp.DSPChain
import ai.music.workstation.dsp.LOFIPresets
import ai.music.workstation.errors.AppError
import ai.music.workstation.model.ErrorReporter as ModelErrorReporter
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.logging.Logger
import ai.music.workstation.model.LoudnessReport
import ai.music.workstation.worker.*
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration

/**
 * Orchestrates the full audio processing pipeline.
 *
 * Stage order:
 * 1. Analysis → worker (analyze)
 * 2. Repair → worker (repair)
 * 3. LoFi DSP → Kotlin DSPChain (local, no worker needed)
 * 4. Mastering → worker (master)
 */
class AudioPipeline(
    private val cliArgs: CliArgs,
    private val logger: Logger,
    private val errorReporter: ErrorReporter
) {

    private val workerClient: WorkerClient by lazy {
        WorkerClient(
            baseUrl = cliArgs.workerUrl,
            timeout = Duration.parse("PT10M"),
            logger = logger,
            errorReporter = errorReporter
        )
    }

    private val cliErrorReporter: CliErrorReporter by lazy {
        CliErrorReporter(logger)
    }

    /**
     * Runs the full pipeline and returns the result.
     */
    suspend fun run(): PipelineResult {
        var result = PipelineResult()

        // Determine which stages to run
        val stagesToRun = if (cliArgs.stages.isEmpty()) {
            listOf(
                PipelineStage.ANALYZE,
                PipelineStage.REPAIR,
                PipelineStage.LOFI,
                PipelineStage.MASTER
            ).filter { stage ->
                when (stage) {
                    PipelineStage.ANALYZE -> true
                    PipelineStage.REPAIR -> cliArgs.enableRepair
                    PipelineStage.LOFI -> true
                    PipelineStage.MASTER -> cliArgs.enableMastering
                }
            }
        } else {
            cliArgs.stages.mapNotNull { PipelineStage.fromString(it) }
        }

        // Current working path for intermediate files
        val workDir = Files.createTempDirectory("ai-music-workstation-cli-")
        logger.info("Pipeline", "Working directory: $workDir")

        try {
            var currentInput = Path.of(cliArgs.inputPath).toAbsolutePath().normalize()

            // Pre-process: Convert MP3 to WAV if needed
            if (currentInput.toString().endsWith(".mp3", ignoreCase = true)) {
                val wavPath = workDir.resolve("input_converted.wav")
                println("▶ [0/4] Converting MP3 to WAV...")
                val convertSuccess = convertMp3ToWav(currentInput, wavPath)
                if (convertSuccess) {
                    currentInput = wavPath
                    println("  ✓ MP3 conversion complete")
                } else {
                    throw RuntimeException("Failed to convert MP3 to WAV")
                }
            }

            // Stage 1: Analysis
            if (PipelineStage.ANALYZE in stagesToRun) {
                println("▶ [1/4] Analyzing audio...")
                val analysisResult = runAnalysis(currentInput)
                result = result.copy(analysis = analysisResult)
                println("  ✓ Analysis complete")
            }

            // Stage 2: Repair
            if (PipelineStage.REPAIR in stagesToRun) {
                val repairedPath = workDir.resolve("01_repaired.wav")
                println("▶ [2/4] Repairing audio...")
                val repairSuccess = runRepair(currentInput, repairedPath)
                if (repairSuccess) {
                    result = result.copy(repairedPath = repairedPath)
                    currentInput = repairedPath
                    println("  ✓ Repair complete")
                } else {
                    logger.warning("Pipeline", "Repair failed, continuing with original")
                }
            }

            // Stage 3: LoFi DSP
            if (PipelineStage.LOFI in stagesToRun) {
                val lofiPath = workDir.resolve("02_lofi.wav")
                println("▶ [3/4] Applying LoFi DSP (${cliArgs.preset})...")
                val lofiSuccess = runLoFiDSP(currentInput, lofiPath)
                if (lofiSuccess) {
                    result = result.copy(lofiPath = lofiPath)
                    currentInput = lofiPath
                    println("  ✓ LoFi DSP complete")
                } else {
                    logger.warning("Pipeline", "LoFi DSP failed, continuing")
                }
            }

            // Stage 4: Mastering
            if (PipelineStage.MASTER in stagesToRun) {
                val masteredPath = Path.of(cliArgs.outputPath).toAbsolutePath().normalize()
                Files.createDirectories(masteredPath.parent)
                println("▶ [4/4] Mastering audio...")
                val (masterSuccess, loudnessReport) = runMastering(currentInput, masteredPath)
                if (masterSuccess) {
                    result = result.copy(masteredPath = masteredPath, loudnessReport = loudnessReport)
                    println("  ✓ Mastering complete")
                } else {
                    logger.warning("Pipeline", "Mastering failed")
                }
            }

        } finally {
            // Cleanup temp directory
            deleteRecursively(workDir)
            logger.info("Pipeline", "Cleaned up working directory")
        }

        return result
    }

    /**
     * Convert MP3 to WAV using the Python worker.
     */
    private suspend fun convertMp3ToWav(inputPath: Path, outputPath: Path): Boolean {
        return try {
            workerClient.start()
            val response = workerClient.execute(
                MP3ConvertCommand(
                    path = inputPath.toString(),
                    outputPath = outputPath.toString()
                )
            )
            workerClient.stop()

            if (response.status != WorkerStatus.COMPLETED) {
                val errorMsg = response.error?.message ?: "Unknown error"
                logger.error("Pipeline", "MP3 conversion failed: $errorMsg")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Pipeline", "MP3 conversion exception: ${e.message}")
            false
        }
    }

    /**
     * Stage 1: Run audio analysis via worker.
     */
    private suspend fun runAnalysis(inputPath: Path): AnalysisResult {
        workerClient.start()

        return try {
            val response = workerClient.execute(
                AnalyzeCommand(
                    path = inputPath.toString(),
                    options = AnalyzeOptions(
                        detectBPM = true,
                        detectKey = true,
                        detectLoudness = true,
                        detectOnsets = true,
                        detectBeats = true,
                        detectSections = true
                    )
                )
            )

            if (response.status != WorkerStatus.COMPLETED) {
                val errorMsg = response.error?.message ?: "Unknown error"
                throw RuntimeException("Analysis failed: $errorMsg")
            }

            val output = response.output ?: emptyMap()
            val loudness = output["loudness"] as? Map<*, *>

            AnalysisResult(
                bpm = (output["bpm"] as? Number)?.toDouble(),
                key = (output["key"] as? Map<*, *>)?.get("root")?.toString(),
                duration = (output["duration"] as? Number)?.toDouble() ?: 0.0,
                sampleRate = (output["sampleRate"] as? Number)?.toInt() ?: 44100,
                channels = (output["channels"] as? Number)?.toInt() ?: 2,
                loudness = loudness?.let {
                    LoudnessInfo(
                        integratedLUFS = (it["integratedLUFS"] as? Number)?.toDouble() ?: -14.0,
                        truePeak = (it["truePeak"] as? Number)?.toDouble() ?: -1.0,
                        rms = (it["rms"] as? Number)?.toDouble() ?: -18.0
                    )
                },
                qualityIssues = emptyList()
            )
        } finally {
            workerClient.stop()
        }
    }

    /**
     * Stage 2: Run repair via worker.
     */
    private suspend fun runRepair(inputPath: Path, outputPath: Path): Boolean {
        workerClient.start()

        return try {
            val repairs = listOf(
                RepairSpec("dc_offset", emptyMap()),
                RepairSpec("clip_removal", mapOf("threshold" to 0.99)),
                RepairSpec("normalize", mapOf("peak" to -1.0)),
                RepairSpec("noise_reduction", mapOf("threshold" to -40))
            )

            val response = workerClient.execute(
                RepairCommand(
                    path = inputPath.toString(),
                    repairs = repairs,
                    outputPath = outputPath.toString()
                )
            )

            if (response.status != WorkerStatus.COMPLETED) {
                val errorMsg = response.error?.message ?: "Unknown error"
                logger.error("Pipeline", "Repair failed: $errorMsg")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Pipeline", "Repair exception: ${e.message}")
            false
        } finally {
            workerClient.stop()
        }
    }

    /**
     * Stage 3: Apply LoFi DSP using Kotlin DSPChain (local processing).
     */
    private fun runLoFiDSP(inputPath: Path, outputPath: Path): Boolean {
        return try {
            // Get the preset
            val preset = LOFIPresets.getByName(cliArgs.preset)
                ?: LOFIPresets.DEFAULT_PRESETS.first()

            // Decode audio
            val audioBuffer = decodeAudio(inputPath)
            if (audioBuffer == null) {
                logger.error("Pipeline", "Failed to decode input for LoFi: $inputPath")
                return false
            }

            // Apply DSP chain
            val dspChain = DSPChain.createDefaultChain(preset.settings)
            val processed = dspChain.process(audioBuffer)

            // Export as WAV (48kHz, 24-bit)
            val exporter = WAVExporterSimple()
            exporter.export(processed, outputPath)

            logger.info("Pipeline", "LoFi DSP exported to: $outputPath")
            true
        } catch (e: Exception) {
            logger.error("Pipeline", "LoFi DSP failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Stage 4: Run mastering via worker.
     * @return Pair of (success, loudnessReport)
     */
    private suspend fun runMastering(inputPath: Path, outputPath: Path): Pair<Boolean, LoudnessReport?> {
        workerClient.start()

        return try {
            val masteringSettings = mapOf(
                "eq_enabled" to true,
                "eq" to mapOf(
                    "bands" to listOf(
                        mapOf("type" to "highshelf", "frequency" to 10000.0, "gain" to 1.0, "q" to 0.707),
                        mapOf("type" to "peaking", "frequency" to 5000.0, "gain" to 0.5, "q" to 1.0),
                        mapOf("type" to "peaking", "frequency" to 1000.0, "gain" to 0.0, "q" to 1.0),
                        mapOf("type" to "peaking", "frequency" to 200.0, "gain" to -0.5, "q" to 1.0),
                        mapOf("type" to "lowshelf", "frequency" to 100.0, "gain" to 0.0, "q" to 0.707)
                    )
                ),
                "compressor_enabled" to true,
                "compressor" to mapOf(
                    "threshold_db" to -24.0,
                    "ratio" to 4.0,
                    "attack_ms" to 10.0,
                    "release_ms" to 100.0
                ),
                "saturation_enabled" to true,
                "saturation" to mapOf("mode" to "tape", "amount" to 0.5),
                "stereo_enabled" to true,
                "stereo" to mapOf("width" to 1.0),
                "limiter_enabled" to true,
                "limiter" to mapOf("ceiling_db" to -1.0),
                "target_peak_db" to -1.0
            )

            val response = workerClient.execute(
                MasterCommand(
                    path = inputPath.toString(),
                    settings = masteringSettings,
                    outputPath = outputPath.toString()
                )
            )

            if (response.status != WorkerStatus.COMPLETED) {
                val errorMsg = response.error?.message ?: "Unknown error"
                logger.error("Pipeline", "Mastering failed: $errorMsg")
                Pair(false, null as LoudnessReport?)
            } else {
                // Extract loudness report from response
                val output = response.output ?: emptyMap()
                val loudness = output["loudness"] as? Map<*, *>
                val report: LoudnessReport? = loudness?.let {
                    LoudnessReport(
                        integratedLUFS = (it["integrated_lufs"] as? Number)?.toDouble() ?: -14.0,
                        truePeak = (it["true_peak_db"] as? Number)?.toDouble() ?: -1.0,
                        rms = (it["rms_db"] as? Number)?.toDouble() ?: -18.0
                    )
                }
                Pair(true, report)
            }
        } catch (e: Exception) {
            logger.error("Pipeline", "Mastering exception: ${e.message}")
            Pair(false, null as LoudnessReport?)
        } finally {
            workerClient.stop()
        }
    }

    /**
     * Decode audio file to AudioBuffer.
     */
    private fun decodeAudio(path: Path): ai.music.workstation.audio.AudioBuffer? {
        return when {
            path.toString().endsWith(".wav", ignoreCase = true) -> {
                ai.music.workstation.audio.WAVDecoder(cliErrorReporter).decode(path)
            }
            path.toString().endsWith(".flac", ignoreCase = true) -> {
                ai.music.workstation.audio.FLACDecoder(cliErrorReporter).decode(path)
            }
            else -> {
                logger.warning("Pipeline", "Unsupported format, trying WAV decoder: $path")
                ai.music.workstation.audio.WAVDecoder(cliErrorReporter).decode(path)
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { file ->
                    try {
                        Files.delete(file)
                    } catch (_: Exception) {
                        // Ignore cleanup errors
                    }
                }
        }
    }
}
