package ai.music.workstation.cli

import ai.music.workstation.dsp.DSPChain
import ai.music.workstation.dsp.LOFIPresets
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.Logger
import ai.music.workstation.model.LoudnessReport
import ai.music.workstation.worker.AnalyzeCommand
import ai.music.workstation.worker.AnalyzeOptions
import ai.music.workstation.worker.HttpWorkerClient
import ai.music.workstation.worker.MP3ConvertCommand
import ai.music.workstation.worker.MasterCommand
import ai.music.workstation.worker.RepairCommand
import ai.music.workstation.worker.RepairSpec
import ai.music.workstation.worker.WorkerStatus
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Orchestrates the full audio processing pipeline.
 *
 * Default pipeline:
 *   1. MP3 -> WAV conversion when needed
 *   2. Analysis
 *   3. Conservative repair
 *   4. Mastering
 *
 * LoFi DSP is intentionally NOT part of the default pipeline because it is a
 * creative/destructive effect. It is only executed when explicitly requested
 * through --stages lofi (or another caller explicitly includes LOFI).
 *
 * Stage order when LOFI is requested:
 *   Analysis -> Repair -> LoFi DSP -> Mastering
 *
 * The Python worker is an independent HTTP service and must be running on
 * http://127.0.0.1:8081.
 */
class AudioPipeline(
    private val cliArgs: CliArgs,
    private val logger: Logger,
    private val errorReporter: ErrorReporter
) {

    private val workerClient: HttpWorkerClient by lazy {
        HttpWorkerClient(
            baseUrl = "http://127.0.0.1:8081",
            timeout = Duration.parse("PT10M"),
            logger = logger,
            errorReporter = errorReporter
        )
    }

    private val cliErrorReporter: CliErrorReporter by lazy {
        CliErrorReporter(logger)
    }

    suspend fun run(): PipelineResult {
        var result = PipelineResult()

        /*
         * Do not silently apply LoFi processing. The old pipeline always added
         * LOFI to the default stage list, which meant a normal piano recording
         * was always passed through sample-rate reduction, noise, saturation,
         * modulation, etc.
         *
         * Explicit --stages still has full control over the pipeline.
         */
        val stagesToRun = if (cliArgs.stages.isEmpty()) {
            buildList {
                add(PipelineStage.ANALYZE)

                if (cliArgs.enableRepair) {
                    add(PipelineStage.REPAIR)
                }

                if (cliArgs.enableMastering) {
                    add(PipelineStage.MASTER)
                }
            }
        } else {
            cliArgs.stages.mapNotNull { PipelineStage.fromString(it) }
        }

        if (stagesToRun.isEmpty()) {
            throw IllegalArgumentException("No valid pipeline stages were selected.")
        }

        val workDir = Files.createTempDirectory("ai-music-workstation-cli-")
        logger.info("Pipeline", "Working directory: $workDir")
        logger.info("Pipeline", "Stages: ${stagesToRun.joinToString(" -> ")}")

        if (!workerClient.healthCheck()) {
            throw RuntimeException(
                "Python worker is not running. Start it with `make worker` before running the CLI."
            )
        }

        try {
            var currentInput = Path.of(cliArgs.inputPath)
                .toAbsolutePath()
                .normalize()

            require(Files.exists(currentInput)) {
                "Input audio file does not exist: $currentInput"
            }

            /*
             * MP3 conversion is a format conversion, not a DSP operation.
             * Keep the decoded audio as WAV before entering the processing
             * stages.
             */
            if (currentInput.toString().endsWith(".mp3", ignoreCase = true)) {
                val wavPath = workDir.resolve("00_input.wav")

                println("▶ Converting MP3 to WAV...")
                if (!convertMp3ToWav(currentInput, wavPath)) {
                    throw RuntimeException("Failed to convert MP3 to WAV")
                }

                currentInput = wavPath
                println("  ✓ MP3 conversion complete")
            }

            var stageNumber = 1
            val totalStages = stagesToRun.size

            if (PipelineStage.ANALYZE in stagesToRun) {
                println("▶ [$stageNumber/$totalStages] Analyzing audio...")
                val analysisResult = runAnalysis(currentInput)
                result = result.copy(analysis = analysisResult)

                println(
                    "  ✓ Analysis complete, BPM: ${analysisResult.bpm}, " +
                            "Key: ${analysisResult.key}, " +
                            "Duration: ${analysisResult.duration}s"
                )

                stageNumber++
            }

            if (PipelineStage.REPAIR in stagesToRun) {
                val repairedPath = workDir.resolve("01_repaired.wav")

                println("▶ [$stageNumber/$totalStages] Repairing audio...")
                if (runRepair(currentInput, repairedPath)) {
                    result = result.copy(repairedPath = repairedPath)
                    currentInput = repairedPath
                    println("  ✓ Repair complete")
                } else {
                    /*
                     * Repair is deliberately non-fatal. A failed repair should
                     * never cause the pipeline to lose the original audio.
                     */
                    logger.warning(
                        "Pipeline",
                        "Repair failed; continuing with original input"
                    )
                }

                stageNumber++
            }

            if (PipelineStage.LOFI in stagesToRun) {
                val lofiPath = workDir.resolve("02_lofi.wav")

                println("▶ [$stageNumber/$totalStages] Applying LoFi DSP (${cliArgs.preset})...")
                if (runLoFiDSP(currentInput, lofiPath)) {
                    result = result.copy(lofiPath = lofiPath)
                    currentInput = lofiPath
                    println("  ✓ LoFi DSP complete")
                } else {
                    logger.warning(
                        "Pipeline",
                        "LoFi DSP failed; continuing with previous input"
                    )
                }

                stageNumber++
            }

            if (PipelineStage.MASTER in stagesToRun) {
                val masteredPath = Path.of(cliArgs.outputPath)
                    .toAbsolutePath()
                    .normalize()

                require(masteredPath.fileName.toString().endsWith(".wav", ignoreCase = true)) {
                    "Master output must be a .wav file. MP3 export is a separate final step."
                }
                masteredPath.parent?.let { Files.createDirectories(it) }

                println("▶ [$stageNumber/$totalStages] Mastering audio...")
                val (masterSuccess, loudnessReport) =
                    runMastering(currentInput, masteredPath)

                if (masterSuccess) {
                    result = result.copy(
                        masteredPath = masteredPath,
                        loudnessReport = loudnessReport
                    )
                    println("  ✓ Mastering complete")
                } else {
                    logger.warning("Pipeline", "Mastering failed")
                }
            }
        } finally {
            deleteRecursively(workDir)
            logger.info("Pipeline", "Cleaned up working directory")
        }

        return result
    }

    /**
     * Convert MP3 to WAV using the Python worker.
     *
     * This is intentionally kept separate from DSP so the processing stages
     * always operate on a lossless PCM representation.
     */
    private suspend fun convertMp3ToWav(
        inputPath: Path,
        outputPath: Path
    ): Boolean {
        return try {
            val response = workerClient.execute(
                MP3ConvertCommand(
                    path = inputPath.toString(),
                    outputPath = outputPath.toString()
                )
            )

            if (response.status != WorkerStatus.COMPLETED) {
                val errorMsg = response.error?.message ?: "Unknown error"
                logger.error("Pipeline", "MP3 conversion failed: $errorMsg")
                false
            } else {
                verifyAudioFile(outputPath, "MP3 conversion")
            }
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "MP3 conversion exception: ${e.message}"
            )
            false
        }
    }

    /**
     * Stage 1: Run audio analysis via worker.
     */
    private suspend fun runAnalysis(inputPath: Path): AnalysisResult {
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

            analysisResultFrom(response.output ?: emptyMap())
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "Analysis exception: ${e.message}"
            )
            throw e
        }
    }

    /**
     * Stage 2: Conservative repair.
     *
     * Piano has a large amount of useful low-level information in its decay,
     * pedal resonance and room ambience. Therefore the default repair stage
     * deliberately does NOT run threshold-based noise reduction or automatic
     * normalization.
     *
     * These operations are available in the Python worker and should be
     * explicitly requested when needed.
     */
    private suspend fun runRepair(
        inputPath: Path,
        outputPath: Path
    ): Boolean {
        return try {
            val repairs = listOf(
                /*
                 * DC removal is effectively transparent to musical content.
                 */
                RepairSpec(
                    "dc_offset",
                    emptyMap()
                ),

                /*
                 * Only repair obvious clipping. 0.999 is intentionally more
                 * conservative than the previous 0.99 threshold.
                 */
                RepairSpec(
                    "clip_removal",
                    mapOf(
                        "threshold" to 0.999,
                        "max_run_samples" to 12
                    )
                )
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
                verifyAudioFile(outputPath, "Repair")
            }
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "Repair exception: ${e.message}"
            )
            false
        }
    }

    /**
     * Optional LoFi stage.
     *
     * This stage is intentionally opt-in. It is a creative effect and should
     * not be applied to a normal piano recording unless the user explicitly
     * asks for the LoFi character.
     */
    private fun runLoFiDSP(
        inputPath: Path,
        outputPath: Path
    ): Boolean {
        return try {
            val preset = LOFIPresets.getByName(cliArgs.preset)
                ?: LOFIPresets.DEFAULT_PRESETS.first()

            val audioBuffer = decodeAudio(inputPath)

            if (audioBuffer == null) {
                logger.error(
                    "Pipeline",
                    "Failed to decode input for LoFi: $inputPath"
                )
                return false
            }

            val dspChain = DSPChain.createDefaultChain(
                settings = preset.settings,
                sampleRate = audioBuffer.format.sampleRate,
                channels = audioBuffer.format.channels
            )
            val processed = dspChain.process(audioBuffer)

            /*
             * WAVExporterSimple must preserve the source sample rate and write
             * a standards-compliant little-endian WAV. The exporter itself is
             * responsible for those details.
             */
            val exporter = WAVExporterSimple()
            exporter.export(processed, outputPath)

            verifyAudioFile(outputPath, "LoFi DSP")
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "LoFi DSP failed: ${e.message}"
            )
            e.printStackTrace()
            false
        }
    }

    /**
     * Stage 4: Transparent/gentle mastering.
     *
     * The old defaults were too aggressive for piano:
     *   - 4:1 compression at -24 dB
     *   - tape saturation at 50%
     *   - automatic stereo processing
     *
     * These settings now prioritize preservation of the original piano tone.
     */
    private suspend fun runMastering(
        inputPath: Path,
        outputPath: Path
    ): Pair<Boolean, LoudnessReport?> {
        return try {
            val masteringSettings = mapOf(
                "eq_enabled" to true,
                "eq" to mapOf(
                    "bands" to listOf(
                        /*
                         * Very gentle air lift. This is intentionally small
                         * because piano already contains strong high-frequency
                         * harmonics.
                         */
                        mapOf(
                            "type" to "highshelf",
                            "frequency" to 10000.0,
                            "gain" to 0.5,
                            "q" to 0.707
                        ),

                        /*
                         * Tiny presence adjustment.
                         */
                        mapOf(
                            "type" to "peaking",
                            "frequency" to 3500.0,
                            "gain" to 0.25,
                            "q" to 0.9
                        ),

                        /*
                         * Keep the fundamental region essentially unchanged.
                         */
                        mapOf(
                            "type" to "peaking",
                            "frequency" to 1000.0,
                            "gain" to 0.0,
                            "q" to 1.0
                        ),

                        /*
                         * Slight low-mid cleanup, but not enough to thin the
                         * body of the piano.
                         */
                        mapOf(
                            "type" to "peaking",
                            "frequency" to 220.0,
                            "gain" to -0.25,
                            "q" to 0.8
                        ),

                        mapOf(
                            "type" to "lowshelf",
                            "frequency" to 80.0,
                            "gain" to 0.0,
                            "q" to 0.707
                        )
                    )
                ),

                /*
                 * Gentle bus compression. 2:1 is much safer for piano than
                 * the previous 4:1 setting.
                 */
                "compressor_enabled" to true,
                "compressor" to mapOf(
                    "threshold_db" to -18.0,
                    "ratio" to 2.0,
                    "attack_ms" to 30.0,
                    "release_ms" to 180.0,
                    "knee_db" to 6.0,
                    "makeup_gain_db" to 0.0
                ),

                /*
                 * Saturation is OFF by default. It can be enabled explicitly
                 * later when a colored sound is wanted.
                 */
                "saturation_enabled" to false,
                "saturation" to mapOf(
                    "mode" to "tape",
                    "amount" to 0.1
                ),

                /*
                 * Preserve the recorded stereo image. Do not widen it by
                 * default because widening can exaggerate phase problems.
                 */
                "stereo_enabled" to false,
                "stereo" to mapOf(
                    "width" to 1.0
                ),

                /*
                 * Transparent final peak protection.
                 */
                "limiter_enabled" to true,
                "limiter" to mapOf(
                    "ceiling_db" to -1.0,
                    "release_ms" to 100.0
                ),

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
                logger.error(
                    "Pipeline",
                    "Mastering failed: $errorMsg"
                )
                Pair(false, null)
            } else if (!verifyAudioFile(outputPath, "Mastering")) {
                Pair(false, null)
            } else {
                val output = response.output ?: emptyMap()
                val loudness = output["loudness"] as? Map<*, *>

                val report = loudness?.let {
                    LoudnessReport(
                        integratedLUFS =
                            (it["integrated_lufs"] as? Number)?.toDouble()
                                ?: -14.0,
                        truePeak =
                            (it["true_peak_db"] as? Number)?.toDouble()
                                ?: -1.0,
                        rms =
                            (it["rms_db"] as? Number)?.toDouble()
                                ?: -18.0
                    )
                }

                Pair(true, report)
            }
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "Mastering exception: ${e.message}"
            )
            Pair(false, null)
        }
    }

    /**
     * Decode audio file to AudioBuffer.
     */
    private fun decodeAudio(
        path: Path
    ): ai.music.workstation.audio.AudioBuffer? {
        return when {
            path.toString().endsWith(".wav", ignoreCase = true) -> {
                ai.music.workstation.audio.WAVDecoder(cliErrorReporter)
                    .decode(path)
            }

            path.toString().endsWith(".flac", ignoreCase = true) -> {
                ai.music.workstation.audio.FLACDecoder(cliErrorReporter)
                    .decode(path)
            }

            else -> {
                logger.warning(
                    "Pipeline",
                    "Unsupported format, trying WAV decoder: $path"
                )
                ai.music.workstation.audio.WAVDecoder(cliErrorReporter)
                    .decode(path)
            }
        }
    }

    /**
     * Basic pipeline-level validation.
     *
     * This catches a very common class of failures where a worker reports
     * success but the output file is missing/empty.
     */
    private fun verifyAudioFile(
        path: Path,
        stage: String
    ): Boolean {
        return try {
            if (!Files.exists(path)) {
                logger.error(
                    "Pipeline",
                    "$stage succeeded but output file does not exist: $path"
                )
                return false
            }

            val size = Files.size(path)

            if (size < 128) {
                logger.error(
                    "Pipeline",
                    "$stage produced an invalid/empty audio file: $path ($size bytes)"
                )
                return false
            }

            true
        } catch (e: Exception) {
            logger.error(
                "Pipeline",
                "$stage output validation failed: ${e.message}"
            )
            false
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { file ->
                try {
                    Files.delete(file)
                } catch (_: Exception) {
                    // Ignore cleanup errors.
                }
            }
    }
}

/** Maps the worker's JSON element contract without lossy Any/Number casts. */
internal fun analysisResultFrom(output: Map<String, JsonElement>): AnalysisResult {
    val loudness = output["loudness"]?.let { runCatching { it.jsonObject }.getOrNull() }
    return AnalysisResult(
        bpm = output["bpm"]?.jsonPrimitive?.doubleOrNull,
        key = output["key"]?.let { runCatching { it.jsonObject["root"]?.jsonPrimitive?.contentOrNull }.getOrNull() },
        duration = output["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        sampleRate = output["sampleRate"]?.jsonPrimitive?.intOrNull ?: 44_100,
        channels = output["channels"]?.jsonPrimitive?.intOrNull ?: 2,
        loudness = loudness?.let {
            LoudnessInfo(
                integratedLUFS = it["integratedLUFS"]?.jsonPrimitive?.doubleOrNull ?: -14.0,
                truePeak = it["truePeak"]?.jsonPrimitive?.doubleOrNull ?: -1.0,
                rms = it["rms"]?.jsonPrimitive?.doubleOrNull ?: -18.0
            )
        },
        qualityIssues = emptyList()
    )
}
