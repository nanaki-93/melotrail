package app.melotrail.arrangement

import app.melotrail.preparation.SourceGrooveBin
import app.melotrail.preparation.SourceGrooveBinStatus
import app.melotrail.preparation.SourceGrooveTemplate
import app.melotrail.preparation.SourceGrooveTemplateStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FullSongGrooveMapTest {
    @Test
    fun `repeated source occurrences retain one local template while an unsafe seam is review required`() {
        val repeated = listOf(window("a-one", "A", 0, evidence(0.02)), window("a-two", "A", 1_920, evidence(0.02)))

        val continuous = FullSongGrooveMapBuilder.build(480, 4, repeated)

        assertEquals(continuous.occurrenceTemplateFingerprints.single { it.occurrenceId == "a-one" }.fingerprint,
            continuous.occurrenceTemplateFingerprints.single { it.occurrenceId == "a-two" }.fingerprint)
        assertEquals(FullSongGrooveBoundaryStatus.CONTINUOUS, continuous.boundaries.single().status)

        val discontinuous = FullSongGrooveMapBuilder.build(480, 4, listOf(window("a-one", "A", 0, evidence(0.125)), window("b-one", "B", 1_920, evidence(-0.125))))

        assertEquals(FullSongGrooveBoundaryStatus.REVIEW_REQUIRED, discontinuous.boundaries.single().status)
    }

    @Test
    fun `different groove evidence for the same repeated source is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            FullSongGrooveMapBuilder.build(480, 4, listOf(window("a-one", "A", 0, evidence(0.02)), window("a-two", "A", 1_920, evidence(-0.02))))
        }
    }

    private fun window(id: String, part: String, start: Long, groove: SourceSongGrooveEvidence): FullMelodyOccurrenceWindow = FullMelodyOccurrenceWindow(
        occurrenceId = id, sectionRole = SectionTypeId.VERSE, sourcePartId = part, startBar = start / 1_920, endBar = start / 1_920 + 1,
        startTick = start, endTick = start + 1_920, pickupStartTick = start, pickupEndTick = start,
        bodyStartTick = start, bodyEndTick = start + 1_920, tailStartTick = start + 1_920, tailEndTick = start + 1_920,
        markerText = "occurrence=$id;role=verse;part=$part", sourceMidiSha256 = "a".repeat(64),
        monophonicPreparationReport = null, harmonyFitReport = null, groove = groove
    )

    private fun evidence(deviation: Double): SourceSongGrooveEvidence = SourceSongGrooveEvidence(
        status = SourceSongGrooveStatus.MEASURED,
        sourceTimingReport = WorkflowArtifactReference("analysis/timing/a/evidence.json", "b".repeat(64)),
        template = SourceGrooveTemplate(
            sourceSha256 = "c".repeat(64),
            bins = (0 until SourceGrooveTemplate.SUBDIVISIONS_PER_BEAT).map { subdivision ->
                SourceGrooveBin(subdivision, SourceGrooveBinStatus.MEASURED, deviation, 0.8, 4)
            },
            confidence = 0.8,
            status = SourceGrooveTemplateStatus.MEASURED,
            excludedPickupOnsets = 0,
            excludedTempoDriftIntervals = 0,
            excludedMissingOnsetIntervals = 0,
            excludedOutlierOnsets = 0
        )
    )
}
