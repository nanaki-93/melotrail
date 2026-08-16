package app.melotrail.arrangement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowArtifactsTest {
    @Test
    fun `invalidation matrix is exact and never marks an upstream artifact stale`() {
        val expected = mapOf(
            WorkflowChange.SOURCE_OR_RAW to setOf(WorkflowArtifact.MIDI_REPAIR, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.REPAIRED_MIDI to setOf(WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.MIDI_FEEL to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.ANALYSIS to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.STRUCTURE to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.COHESION to setOf(WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.MIX_ONLY to setOf(WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.AUDIO_TEXTURE to setOf(WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT)
        )

        expected.forEach { (change, stale) ->
            assertEquals(stale, WorkflowArtifactGraph.invalidatedBy(change), change.name)
            assertFalse(WorkflowArtifact.RAW_SOURCE in stale, change.name)
        }
    }

    @Test
    fun `marking a rebuilt artifact current preserves unrelated stale evidence`() {
        val state = ProjectWorkflowReferences().invalidate(WorkflowChange.REPAIRED_MIDI)
            .markCurrent(WorkflowArtifact.ANALYSIS)

        assertFalse(WorkflowArtifact.ANALYSIS in state.stale)
        assertTrue(WorkflowArtifact.COHESION in state.stale)
        assertTrue(WorkflowArtifact.MASTER in state.stale)
    }
}
