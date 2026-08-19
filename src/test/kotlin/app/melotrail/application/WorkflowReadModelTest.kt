package app.melotrail.application

import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.WorkflowArtifact
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowReadModelTest {
    @Test
    fun `workflow exposes the complete ordered sequence and exact typed recovery`() {
        val base = project()
        val current = WorkflowReadModelDeriver.derive(base)

        assertEquals(WorkflowStage.entries, current.steps.map(WorkflowStep::stage))
        assertEquals(WorkflowState.CURRENT, current[WorkflowStage.COHESION].state)
        assertEquals(WorkflowState.BLOCKED, current[WorkflowStage.ARRANGEMENT].state)
        assertEquals(WorkflowAction.GENERATE_COHESION, current.current.nextAction)
        assertEquals(WorkflowPrerequisite.APPROVED_COHESION, current.current.prerequisite)

        val review = WorkflowReadModelDeriver.derive(
            base.copy(readiness = base.readiness.copy(songPlanAvailable = true, cohesionReady = true)),
            ArrangementSnapshot(
                base.root,
                listOf(ArrangementSectionSnapshot(0, "A1", "A", "verse", 0.5, emptyList(), "none", 1.0)),
                true,
                false,
                false,
                base.root.resolve("arrangement.draft.json")
            )
        )
        assertEquals(WorkflowState.REVIEW, review[WorkflowStage.ARRANGEMENT].state)
        assertEquals(WorkflowAction.APPROVE_ARRANGEMENT, review.current.nextAction)
    }

    @Test
    fun `skipped optional branches progress even when retained optional artifacts are stale`() {
        val project = project().copy(readiness = project().readiness.copy(
            staleArtifacts = setOf(WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL)
        ))

        val workflow = WorkflowReadModelDeriver.derive(project)

        assertEquals(WorkflowState.COMPLETE, workflow[WorkflowStage.AI_FIX].state)
        assertEquals(WorkflowState.COMPLETE, workflow[WorkflowStage.MIDI_FEEL].state)
        assertEquals(WorkflowStage.COHESION, workflow.current.stage)
    }

    @Test
    fun `selected optional artifact must be current and never falls back`() {
        val base = project()
        val unavailable = base.parts.single().copy(preparation = base.parts.single().preparation.copy(
            midiAiFix = MidiAiFixSummary(MidiAiFixSelection.APPROVED, draftAvailable = true, approvedAvailable = false),
            ready = false
        ))

        val workflow = WorkflowReadModelDeriver.derive(base.copy(parts = listOf(unavailable)))

        assertEquals(WorkflowState.STALE, workflow[WorkflowStage.AI_FIX].state)
        assertEquals(WorkflowAction.CREATE_AI_FIX, workflow.current.nextAction)
        assertEquals(WorkflowPrerequisite.APPROVED_AI_FIX, workflow.current.prerequisite)
        assertEquals("A", workflow.current.partId)
        assertEquals(WorkflowState.BLOCKED, workflow[WorkflowStage.MIDI_FEEL].state)
    }

    @Test
    fun `file availability cannot override durable stale evidence`() {
        val base = project()
        val stale = WorkflowReadModelDeriver.derive(base.copy(readiness = base.readiness.copy(
            analysesReady = true,
            staleArtifacts = setOf(WorkflowArtifact.ANALYSIS)
        )))

        assertEquals(WorkflowState.STALE, stale[WorkflowStage.ANALYSIS].state)
        assertEquals(WorkflowAction.ANALYZE, stale.current.nextAction)
        assertEquals(WorkflowPrerequisite.CURRENT_ANALYSIS, stale.current.prerequisite)
    }

    @Test
    fun `legacy v2 stays readable but requires explicit migration`() {
        val workflow = WorkflowReadModelDeriver.derive(project().copy(version = 2))

        assertEquals(WorkflowState.REVIEW, workflow[WorkflowStage.PROJECT].state)
        assertEquals(WorkflowAction.MIGRATE_PROJECT, workflow.current.nextAction)
        assertEquals(WorkflowPrerequisite.SCHEMA_V4, workflow.current.prerequisite)
    }

    private fun project(): ProjectSnapshot {
        val preparation = PartPreparationSummary(
            sourcePreserved = true,
            inspected = true,
            preparedAudio = false,
            rawMidi = true,
            cleanMidi = true,
            analyzed = true,
            ready = true,
            warnings = emptyList(),
            midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT),
            midiFeel = MidiFeelSummary(MidiAnalysisInput.CURRENT),
            midiAiFix = MidiAiFixSummary(MidiAiFixSelection.SKIP)
        )
        val part = PartSummary(
            "A",
            "verse",
            "source/A.mid",
            "A.mid",
            PartSourceType.MIDI,
            PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json"),
            preparation
        )
        return ProjectSnapshot(
            Path.of("workflow"),
            Project.CURRENT_VERSION,
            "workflow",
            RenderFormat(),
            listOf(part),
            listOf(StructureSectionSummary(0, "A", 1, "A1", 1.0)),
            ProjectReadiness(true, true, true, false, false, false, false, false, false, false)
        )
    }
}
