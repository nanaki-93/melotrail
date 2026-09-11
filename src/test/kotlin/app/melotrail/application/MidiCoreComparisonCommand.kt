package app.melotrail.application

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlin.system.exitProcess

/** The owned M01 fixture/baseline corpus stays in test resources, outside the shipped application. */
object MidiCoreComparisonCommand {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = run(args.toList(), ::println)
        if (code != 0) exitProcess(code)
    }

    fun run(args: List<String>, output: (String) -> Unit): Int = try {
        require(args.size == 1) { "Usage: musicalComparison --args='<new-output-directory>'" }
        val failed = publishEvaluationDirectory(Path.of(args.single()), emptyList()) { staging ->
            val review = MidiCoreComparisonHarness(staging.resolve("work"))
                .writeSideBySideReview(M01ComparisonFixtures.cases, staging)
            val versions = evaluationJson.encodeToString(evaluationVersions()).encodeToByteArray()
            writeEvaluationFile(staging, "versions.json", versions)
            val header = "Development only. Missing final full songs: 5/5; missing unseen songs: 3/3. Listening is UNSCORED. Runtime versions SHA-256: ${evaluationSha256(versions)}.\n\n"
            Files.writeString(review.reviewForm, header + Files.readString(review.reviewForm))
            (review.baseline + review.candidate).any { it !is M01ComparisonCapture.Published }
        }
        output("M01 development comparison ${if (failed) "contains failed captures" else "prepared"}. Final songs: 0/5; unseen songs: 0/3. No musical ratings or Logic approval.")
        if (failed) 2 else 0
    } catch (error: Exception) {
        output("Development comparison rejected: ${error.message}")
        1
    }
}
