package app.melotrail.arrangement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowArtifactsTest {
    @Test
    fun `invalidation matrix is exact and never marks an upstream artifact stale`() {
        val expected = mapOf(
            WorkflowChange.SOURCE_OR_RAW to setOf(WorkflowArtifact.CLEAN_MIDI, WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.CLEANED_MIDI to setOf(WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.SOURCE_KEY to setOf(WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.CORRECTION_SELECTION to setOf(WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.ENHANCEMENT_SELECTION to setOf(WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.AI_FIX_SELECTION to setOf(WorkflowArtifact.ENHANCED_MIDI, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.MIDI_FEEL to setOf(WorkflowArtifact.ANALYSIS, WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.ANALYSIS to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.STRUCTURE to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.ARRANGEMENT, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.ARRANGEMENT to setOf(WorkflowArtifact.COHESION, WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.COHESION to setOf(WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.MIX_ONLY to setOf(WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowChange.AUDIO_TEXTURE to setOf(WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT)
        )

        expected.forEach { (change, stale) ->
            val expectedWithHumanization = stale + if (change in setOf(WorkflowChange.MIX_ONLY, WorkflowChange.AUDIO_TEXTURE)) emptySet() else setOf(WorkflowArtifact.HUMANIZATION)
            assertEquals(expectedWithHumanization, WorkflowArtifactGraph.invalidatedBy(change), change.name)
            assertFalse(WorkflowArtifact.RAW_SOURCE in expectedWithHumanization, change.name)
        }
        assertEquals(
            setOf(WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE, WorkflowArtifact.COMMERCIAL_EXPORT),
            WorkflowArtifactGraph.invalidatedBy(WorkflowChange.HUMANIZATION)
        )
    }

    @Test
    fun `marking a rebuilt artifact current preserves unrelated stale evidence`() {
        val state = ProjectWorkflowReferences().invalidate(WorkflowChange.CLEANED_MIDI)
            .markCurrent(WorkflowArtifact.ANALYSIS)

        assertFalse(WorkflowArtifact.ANALYSIS in state.stale)
        assertTrue(WorkflowArtifact.COHESION in state.stale)
        assertTrue(WorkflowArtifact.MASTER in state.stale)
    }

    @Test
    fun `invalidation retains inspectable references and canonical paths reject unsafe aliases`() {
        val hash = "a".repeat(64)
        val cohesion = CohesionWorkflowReferences(
            hash,
            WorkflowArtifactReference("cohesion/cohesion.json", hash),
            emptyList(),
            approved = false
        )
        val retained = ProjectWorkflowReferences(cohesion = cohesion).invalidate(WorkflowChange.STRUCTURE)

        assertEquals(cohesion, retained.cohesion)
        assertTrue(WorkflowArtifact.COHESION in retained.stale)
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("cohesion/../outside.json", hash) }
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("/outside.json", hash) }
    }

    @Test
    fun `target order migration invalidates Cohesion onward exactly once and retains references`() {
        val hash = "c".repeat(64)
        val historical = CohesionWorkflowReferences(hash, WorkflowArtifactReference("cohesion/cohesion.json", hash), emptyList(), approved = true)
        val first = ProjectWorkflowReferences(cohesion = historical).migrateCohesionOrderIfNeeded()
        val second = first.migrateCohesionOrderIfNeeded()

        assertEquals(1, first.cohesionOrderMigration)
        assertEquals(first, second)
        assertEquals(historical, first.cohesion)
        assertTrue(WorkflowArtifact.COHESION in first.stale)
        assertTrue(WorkflowArtifact.MASTER in first.stale)
        assertFalse(WorkflowArtifact.ARRANGEMENT in first.stale)
    }

    @Test
    fun `AI fix and cohesion boundary references use one canonical project layout`() {
        val hash = "b".repeat(64)
        val ai = MidiAiFixReferences(
            hash,
            WorkflowArtifactReference(MidiAiFixArtifactPaths.draft("A"), hash),
            WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), hash)
        )
        ai.requireCanonical("A")
        val boundary = CohesionBoundaryReference(
            "A1",
            "B1",
            hash,
            WorkflowArtifactReference(CohesionBoundaryArtifactPaths.draft("A1", "B1"), hash),
            WorkflowArtifactReference(CohesionBoundaryArtifactPaths.approved("A1", "B1"), hash)
        )

        assertEquals("midi/ai-fix/A/approved.mid", ai.approved?.file)
        assertEquals("cohesion/boundaries/A1--B1/boundary.json", boundary.approved?.file)
        assertFailsWith<IllegalArgumentException> { ai.requireCanonical("B") }
    }
}
