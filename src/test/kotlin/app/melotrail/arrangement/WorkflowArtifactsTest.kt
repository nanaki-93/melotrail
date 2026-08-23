package app.melotrail.arrangement

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkflowArtifactsTest {
    @Test
    fun `every canonical stage ID serializes and round trips with its stable wire name`() {
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(ListSerializer(StageId.serializer()), StageId.entries)

        assertEquals(StageId.entries.toList(), json.decodeFromString(ListSerializer(StageId.serializer()), encoded))
        listOf("ai-fixed", "midi-feel", "critiqued", "full-song-enhanced", "humanized", "audio-textured").forEach { wire ->
            assertTrue("\"$wire\"" in encoded)
        }
        assertEquals(
            setOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED, StageId.NORMALIZED, StageId.TRANSPOSED,
                StageId.CORRECTED, StageId.AI_FIXED, StageId.ENHANCED, StageId.MIDI_FEEL, StageId.ANALYZED),
            StageId.entries.filter(StageId::isPartStage).toSet()
        )
    }

    @Test
    fun `invalidation matrix is exact and never marks an upstream artifact stale`() {
        val downstream = listOf(
            WorkflowArtifact.TRANSPOSED_MIDI, WorkflowArtifact.CORRECTED_MIDI, WorkflowArtifact.ENHANCED_MIDI,
            WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL, WorkflowArtifact.ANALYSIS, WorkflowArtifact.ARRANGEMENT,
            WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.CORE_ARRANGEMENT, WorkflowArtifact.COHESION, WorkflowArtifact.CRITIC,
            WorkflowArtifact.FULL_SONG_ENHANCEMENT, WorkflowArtifact.HUMANIZATION, WorkflowArtifact.STEMS,
            WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE, WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE,
            WorkflowArtifact.COMMERCIAL_EXPORT
        )
        fun from(artifact: WorkflowArtifact) = downstream.drop(downstream.indexOf(artifact)).toSet()
        val expected = mapOf(
            WorkflowChange.SOURCE_OR_RAW to setOf(WorkflowArtifact.CLEAN_MIDI) + from(WorkflowArtifact.TRANSPOSED_MIDI),
            WorkflowChange.CLEANED_MIDI to from(WorkflowArtifact.TRANSPOSED_MIDI), WorkflowChange.SOURCE_KEY to from(WorkflowArtifact.TRANSPOSED_MIDI),
            WorkflowChange.CORRECTION_SELECTION to from(WorkflowArtifact.ENHANCED_MIDI), WorkflowChange.ENHANCEMENT_SELECTION to from(WorkflowArtifact.ENHANCED_MIDI),
            WorkflowChange.AI_FIX_SELECTION to from(WorkflowArtifact.ENHANCED_MIDI), WorkflowChange.MIDI_FEEL to from(WorkflowArtifact.ANALYSIS),
            WorkflowChange.ANALYSIS to from(WorkflowArtifact.ARRANGEMENT), WorkflowChange.STRUCTURE to from(WorkflowArtifact.ARRANGEMENT), WorkflowChange.PART_SECTION to from(WorkflowArtifact.ARRANGEMENT),
            WorkflowChange.ARRANGEMENT to from(WorkflowArtifact.GENERATED_MIDI), WorkflowChange.GENERATED_MIDI to from(WorkflowArtifact.CORE_ARRANGEMENT),
            WorkflowChange.COHESION to from(WorkflowArtifact.CRITIC), WorkflowChange.CRITIC to from(WorkflowArtifact.FULL_SONG_ENHANCEMENT),
            WorkflowChange.FULL_SONG_ENHANCEMENT_SELECTION to from(WorkflowArtifact.HUMANIZATION), WorkflowChange.HUMANIZATION to from(WorkflowArtifact.HUMANIZATION),
            WorkflowChange.COMPOSITION_KEY to from(WorkflowArtifact.TRANSPOSED_MIDI), WorkflowChange.COMPOSITION_TEMPO_OR_METER to from(WorkflowArtifact.ARRANGEMENT),
            WorkflowChange.COMPOSITION_PROFILE_OR_MOOD to from(WorkflowArtifact.ENHANCED_MIDI), WorkflowChange.HARMONY to from(WorkflowArtifact.CORRECTED_MIDI),
            WorkflowChange.MIX_ONLY to from(WorkflowArtifact.DRY_MIX), WorkflowChange.AUDIO_TEXTURE to from(WorkflowArtifact.AUDIO_TEXTURE)
        )
        assertEquals(WorkflowChange.entries.toSet(), expected.keys)
        expected.forEach { (change, stale) ->
            assertEquals(stale, WorkflowArtifactGraph.invalidatedBy(change), change.name)
            assertFalse(WorkflowArtifact.RAW_SOURCE in stale, change.name)
        }
    }

    @Test
    fun `marking a rebuilt artifact current preserves unrelated stale evidence`() {
        val state = ProjectWorkflowReferences.initial().invalidate(WorkflowChange.CLEANED_MIDI)
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
        val retained = ProjectWorkflowReferences(FullSongEnhancementSelection.UNRESOLVED, signatureMotif = null, cohesion = cohesion).invalidate(WorkflowChange.STRUCTURE)

        assertEquals(cohesion, retained.cohesion)
        assertTrue(WorkflowArtifact.COHESION in retained.stale)
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("cohesion/../outside.json", hash) }
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("/outside.json", hash) }
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
