package app.melotrail.application

import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseReviewApplicationServiceTest {
    @Test
    fun `reads canonical release measurements and reports missing commercial gates without mutating project`() {
        val root = Files.createTempDirectory("release-review")
        try {
            Files.createDirectories(root.resolve("output"))
            ProjectStore.write(root, Project(Project.CURRENT_VERSION, "Release review", emptyList(), renderFormat = RenderFormat()))
            val master = root.resolve("output/master.wav")
            Files.writeString(master, "selected master evidence")
            Files.writeString(root.resolve("output/release.json"), releaseMetadata(digest(master)))

            val review = DefaultReleaseReviewApplicationService().load(root)

            assertEquals(-13.9, review.mastering?.integratedLufs)
            assertEquals(-1.1, review.mastering?.truePeakDbtp)
            assertTrue(review.mastering?.dynamicsPreserved == true)
            assertEquals(0, review.similarity?.comparisonCount)
            assertNull(review.commercial)
            assertTrue(review.blockers.any { it.contains("Commercial provenance") })
            assertTrue(review.blockers.any { it.contains("recognizability") })
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `does not present release measurements when the selected master fingerprint is stale`() {
        val root = Files.createTempDirectory("release-review-stale-master")
        try {
            Files.createDirectories(root.resolve("output"))
            ProjectStore.write(root, Project(Project.CURRENT_VERSION, "Release review", emptyList(), renderFormat = RenderFormat()))
            Files.writeString(root.resolve("output/master.wav"), "changed master")
            Files.writeString(root.resolve("output/release.json"), releaseMetadata("a".repeat(64)))

            val review = DefaultReleaseReviewApplicationService().load(root)

            assertNull(review.mastering)
            assertTrue(review.blockers.any { it.contains("does not match the selected lossless master") })
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private fun releaseMetadata(masterFingerprint: String) = """
        {
          "master": "master.wav",
          "masterFingerprint": "$masterFingerprint",
          "integratedLufs": -13.9,
          "truePeakDbtp": -1.1,
          "loudnessRangeLu": 6.3,
          "crestDb": 9.2,
          "loudnessReference": "Streaming reference",
          "dynamicsPreserved": true,
          "masteringQualityIssues": [],
          "similarityReview": {
            "version": 1,
            "fingerprint": {
              "version": 1,
              "sha256": "${"a".repeat(64)}",
              "structure": [],
              "energyCurve": [],
              "instrumentEntryExitSequence": [],
              "bassPatternSequence": [],
              "drumGrooveSequence": [],
              "transitionSequence": [],
              "tempoBpmMilli": 80000,
              "meter": "4/4",
              "swingProfile": [],
              "arrangementDensityCurve": []
            },
            "comparisonCount": 0,
            "highestSimilarityScore": null,
            "status": "NOT_COMPARED",
            "comparisons": []
          }
        }
    """.trimIndent()

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
