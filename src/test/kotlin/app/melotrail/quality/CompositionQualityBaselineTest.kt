package app.melotrail.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Locks QP-001 defect exposure before corrective musical behavior is introduced. */
class CompositionQualityBaselineTest {
    private val fixture = CompositionQualityFixtures.baseline()

    @Test
    fun `fixture exposes timing harmony monophony and boundary defects at PPQ scaled values`() {
        assertEquals(480, CompositionQualityMeasurements.barResidual(fixture.fractionalOccurrence, CompositionQualityFixtures.BAR_TICKS))
        assertEquals(60, CompositionQualityMeasurements.occurrencePhaseResidual(fixture.phaseShiftedOccurrence))
        assertEquals(1, CompositionQualityMeasurements.scaleViolations(fixture.modeMismatch))
        assertEquals(1, CompositionQualityMeasurements.exposedChordClashes(fixture.modeMismatch))
        assertEquals(2, CompositionQualityMeasurements.maximumWrittenPolyphony(fixture.overlappingMelody))
        assertEquals(2, CompositionQualityMeasurements.maximumEffectivePolyphony(fixture.pedalTail))
        assertEquals(1, CompositionQualityMeasurements.sustainTailCollisions(fixture.pedalTail, CompositionQualityFixtures.PPQ * 2))
    }

    @Test
    fun `fixture exposes arrangement groove and role defects`() {
        assertEquals(0, CompositionQualityMeasurements.densityContrast(fixture.flatArrangement))
        assertTrue(CompositionQualityMeasurements.boundaryRoleIsUnsafe(fixture.unsafeBoundary))
        assertEquals(36, CompositionQualityMeasurements.crossSectionVoiceMovement(fixture.resetVoicings))
        assertEquals(48, CompositionQualityMeasurements.sharedGrooveResidual(fixture.groove))
        assertEquals(48, CompositionQualityMeasurements.rolePhaseResidual(fixture.audio.pianoOnsetFrames, fixture.audio.gridRoleOnsetFrames))
    }

    @Test
    fun `fixture exposes low end codec critic and lineage evidence`() {
        assertEquals(1.28, CompositionQualityMeasurements.kickBassOverlap(fixture.audio), 0.0001)
        assertTrue(CompositionQualityMeasurements.peak(fixture.audio.decodedLossyPreviewSamples) > 1.0)
        assertTrue(CompositionQualityMeasurements.peak(fixture.audio.decodedLossyPreviewSamples) > CompositionQualityMeasurements.peak(fixture.audio.selectedMasterSamples))
        assertEquals(9, CompositionQualityMeasurements.criticTotals(fixture.critic))
        assertTrue(CompositionQualityMeasurements.lineageIsComplete(fixture.lineage))
        assertFalse(CompositionQualityMeasurements.lineageIsComplete(fixture.lineage.copy(downstreamHashes = emptyMap())))
    }
}
