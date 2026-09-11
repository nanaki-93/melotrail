package app.melotrail.application

import java.nio.file.Path
import kotlin.system.exitProcess

/** Local developer/evaluation command; never opens devices, renders audio or modifies supplied projects. */
object MidiCoreMusicalEvaluationCommand {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args.toList(), ::println)
        if (code != 0) exitProcess(code)
    }

    fun run(args: List<String>, output: (String) -> Unit): Int = try {
        val evaluation = MidiCoreMusicalEvaluation()
        when {
            args.size == 3 && args[0] == "freeze" -> {
                val hash = evaluation.freeze(Path.of(args[1]), Path.of(args[2]))
                output("Frozen set SHA-256: $hash. Retain this hash for export; review.md records missing songs. No generation or listening has occurred.")
                0
            }
            args.size == 4 && args[0] == "export" -> {
                val report = evaluation.export(Path.of(args[1]), args[2], Path.of(args[3]))
                output("Prepared ${report.cases.count { it.status == "PREPARED" }}/${report.cases.size} cases. Missing final songs: ${report.missingFinalSongs}/5; missing unseen songs: ${report.missingUnseenSongs}/3. Listening: UNSCORED. See review.md and evaluation.json.")
                if (report.cases.any { it.status == "FAILED" }) 2 else 0
            }
            else -> {
                output("Usage: freeze <request.json> <new-directory> | export <frozen-directory> <frozen-set-sha256> <new-directory>")
                1
            }
        }
    } catch (error: Exception) {
        output("Evaluation rejected: ${error.message ?: error.javaClass.simpleName}")
        1
    }
}
