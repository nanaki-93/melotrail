package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.ScaleModeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceKeyEvidenceTest {
    @Test
    fun `low confidence cannot silently select a source key but explicit override can`() {
        val cMajor = MusicalKey(PitchClass.canonical(0), ScaleModeId.MAJOR)
        val low = SourceKeyEvidence(cMajor, 0.69, SourceKeyEvidence.ALGORITHM_VERSION, "a".repeat(64))

        assertTrue(low.confirmationRequired)
        assertNull(low.effectiveKey)
        assertEquals(cMajor, low.copy(confirmedOverride = cMajor).effectiveKey)
    }

    @Test
    fun `confirmation and project key changes invalidate transposition onward`() {
        val stale = WorkflowArtifactGraph.invalidatedBy(WorkflowChange.SOURCE_KEY)
        val projectKeyStale = WorkflowArtifactGraph.invalidatedBy(WorkflowChange.COMPOSITION_KEY)

        assertTrue(WorkflowArtifact.TRANSPOSED_MIDI in stale)
        assertTrue(WorkflowArtifact.ANALYSIS in stale)
        assertTrue(WorkflowArtifact.TRANSPOSED_MIDI in projectKeyStale)
        assertTrue(WorkflowArtifact.MASTER in projectKeyStale)
    }
}
