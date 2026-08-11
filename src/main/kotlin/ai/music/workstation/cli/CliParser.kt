package ai.music.workstation.cli

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Parses command-line arguments for the CLI processing tool.
 */
object CliParser {

    fun parse(args: Array<String>): CliArgs {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            printUsage()
            throw IllegalArgumentException("Use --help for usage information")
        }

        var inputPath: String? = null
        var outputPath: String? = null
        var preset = "Warm Cassette"
        var enableRepair = true
        var enableMastering = true
        var dryRun = false
        var stagesList = emptyList<String>()
        var verbose = false
        var workerUrl = "http://localhost:8081"

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input", "-i" -> {
                    inputPath = args[++i]
                }
                "--output", "-o" -> {
                    outputPath = args[++i]
                }
                "--preset" -> {
                    preset = args[++i]
                }
                "--no-repair" -> {
                    enableRepair = false
                }
                "--no-master" -> {
                    enableMastering = false
                }
                "--dry-run" -> {
                    dryRun = true
                }
                "--stages" -> {
                    stagesList = args[++i].split(",").map { it.trim() }
                }
                "--verbose", "-v" -> {
                    verbose = true
                }
                "--worker-url" -> {
                    workerUrl = args[++i]
                }
                "--help", "-h" -> {
                    printUsage()
                    throw IllegalArgumentException("Help requested")
                }
                else -> {
                    if (inputPath == null) {
                        inputPath = args[i]
                    } else if (outputPath == null) {
                        outputPath = args[i]
                    } else {
                        throw IllegalArgumentException("Unknown argument: ${args[i]}")
                    }
                }
            }
            i++
        }

        // Validate required args
        if (inputPath == null) {
            throw IllegalArgumentException("Missing required argument: --input <path>")
        }
        if (outputPath == null) {
            throw IllegalArgumentException("Missing required argument: --output <path>")
        }

        // Validate input file
        val inputPathObj = Path.of(inputPath)
        if (!inputPathObj.exists() || !inputPathObj.isRegularFile()) {
            throw IllegalArgumentException("Input file not found: $inputPath")
        }

        // Validate preset
        if (preset !in CliArgs.VALID_PRESETS) {
            throw IllegalArgumentException(
                "Unknown preset: $preset. Valid presets: ${CliArgs.VALID_PRESETS.joinToString(", ")}"
            )
        }

        // Validate stages
        if (stagesList.isNotEmpty()) {
            for (stage in stagesList) {
                if (stage !in CliArgs.VALID_STAGES) {
                    throw IllegalArgumentException(
                        "Unknown stage: $stage. Valid stages: ${CliArgs.VALID_STAGES.joinToString(", ")}"
                    )
                }
            }
        }

        return CliArgs(
            inputPath = inputPath,
            outputPath = outputPath,
            preset = preset,
            enableRepair = enableRepair,
            enableMastering = enableMastering,
            mastering = MasteringConfig(),
            dryRun = dryRun,
            stages = stagesList,
            verbose = verbose,
            workerUrl = workerUrl
        )
    }

    private fun printUsage() {
        val lines = listOf(
            "AI Music Workstation - CLI Processing Pipeline",
            "================================================",
            "",
            "Usage:",
            "  kotlin -jar cli.jar [options] <input> <output>",
            "",
            "Arguments:",
            "  <input>                        Input audio file (MP3, WAV, FLAC)",
            "  <output>                       Output WAV file path",
            "",
            "Options:",
            "  --input, -i <path>             Input audio file",
            "  --output, -o <path>            Output WAV file",
            "  --worker-url <url>             Worker HTTP URL (default: http://localhost:8081)",
            "  --preset <name>                LoFi preset to apply",
            "                                 [Warm Cassette, Dusty Vinyl, Bedroom LoFi,",
            "                                  Old Sampler, Late Night, Rainy Coffee Shop]",
            "                                 Default: Warm Cassette",
            "  --no-repair                    Skip the repair stage",
            "  --no-master                    Skip the mastering stage",
            "  --stages <stages>              Run only specific stages: analyze,repair,lofi,master",
            "                                 Example: --stages analyze,repair",
            "  --dry-run                      Validate inputs without running the pipeline",
            "  --verbose, -v                  Enable verbose logging",
            "  --help, -h                     Show this help message",
            "",
            "Examples:",
            "  ./gradlew :cli:run --args=\"--input input.wav --output output.wav --worker-url http://localhost:8081\"",
            "  ./gradlew :cli:run --args=\"--input input.wav --output output.wav --worker-url http://localhost:8081 --preset Dusty Vinyl\"",
            "  ./gradlew :cli:run --args=\"--input input.wav --output output.wav --worker-url http://localhost:8081 --stages analyze,repair\"",
            "  ./gradlew :cli:run --args=\"--input input.wav --output output.wav --worker-url http://localhost:8081 --dry-run\""
        )
        lines.forEach { println(it) }
    }
}
