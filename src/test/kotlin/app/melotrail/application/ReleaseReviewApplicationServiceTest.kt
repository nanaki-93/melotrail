package app.melotrail.application

import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import java.nio.file.Files
import java.nio.file.Path
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
            Files.writeString(root.resolve("output/release.json"), releaseMetadata())

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

    private fun releaseMetadata() = """
        {
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
}
