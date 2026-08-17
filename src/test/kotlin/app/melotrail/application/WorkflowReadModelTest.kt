package app.melotrail.application

import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.WorkflowArtifact
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowReadModelTest {
    @Test
    fun `workflow reports blocked current review stale and complete without UI navigation state`() {
        val base = project()
        val current = WorkflowReadModelDeriver.derive(base)
        assertEquals(WorkflowState.CURRENT, current[WorkflowStage.COHESION].state)
        assertEquals(WorkflowState.BLOCKED, current[WorkflowStage.ARRANGEMENT].state)

        val review = WorkflowReadModelDeriver.derive(base.copy(readiness = base.readiness.copy(songPlanAvailable = true, cohesionReady = true)), ArrangementSnapshot(base.root, emptyList(), true, false, false, base.root.resolve("arrangement.draft.json")))
        assertEquals(WorkflowState.REVIEW, review[WorkflowStage.ARRANGEMENT].state)

        val stale = WorkflowReadModelDeriver.derive(base.copy(readiness = base.readiness.copy(staleArtifacts = setOf(WorkflowArtifact.ANALYSIS))))
        assertEquals(WorkflowState.STALE, stale[WorkflowStage.ANALYSIS].state)

        val complete = WorkflowReadModelDeriver.derive(base.copy(readiness = base.readiness.copy(songPlanAvailable = true, cohesionReady = true, stemsAvailable = true, dryMixAvailable = true, masterAvailable = true, releaseAvailable = true)), ArrangementSnapshot(base.root, emptyList(), false, true, false, base.root.resolve("arrangement.json")))
        assertEquals(WorkflowState.COMPLETE, complete[WorkflowStage.MASTER].state)
    }

    private fun project(): ProjectSnapshot {
        val preparation = PartPreparationSummary(true, true, false, true, true, true, true, emptyList(), MidiQualitySummary(MidiQualityStatus.CURRENT), MidiFeelSummary(MidiAnalysisInput.REPAIRED))
        val part = PartSummary("A", "verse", "source/A.mid", "A.mid", PartSourceType.MIDI, PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json"), preparation)
        return ProjectSnapshot(Path.of("workflow"), 3, "workflow", app.melotrail.arrangement.RenderFormat(), listOf(part), listOf(StructureSectionSummary(0, "A", 1, "A1", 1.0)), ProjectReadiness(true, true, true, false, false, false, false, false, false, false))
    }
}
