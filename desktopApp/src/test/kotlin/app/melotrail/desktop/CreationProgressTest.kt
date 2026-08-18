package app.melotrail.desktop

import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.MidiAiFixSummary
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.MidiQualitySummary
import app.melotrail.application.PartAnalysisStatus
import app.melotrail.application.PartAnalysisSummary
import app.melotrail.application.PartPreparationSummary
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.ProjectReadiness
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.StructureSectionSummary
import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.WorkflowArtifact
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreationProgressTest {
    @Test
    fun `creation progress follows the complete canonical sequence`() {
        val ready = readyProject()
        val approved = arrangement(ready)
        val cases = listOf(
            Case("no project", CreationProgressInput(null), CreationStage.PROJECT, CreationStageStatus.CURRENT, CreationIntent.CREATE_OR_OPEN_PROJECT),
            Case("empty project", CreationProgressInput(emptyProject()), CreationStage.IMPORT_AND_INSPECTION, CreationStageStatus.CURRENT, CreationIntent.IMPORT_PART),
            Case(
                "analysis needed",
                CreationProgressInput(ready.copy(parts = listOf(ready.parts.single().copy(preparation = ready.parts.single().preparation.copy(analyzed = false, ready = false))))),
                CreationStage.ANALYSIS,
                CreationStageStatus.CURRENT,
                CreationIntent.ANALYZE_PART
            ),
            Case(
                "stale cleaned MIDI",
                CreationProgressInput(ready.copy(
                    parts = listOf(ready.parts.single().copy(preparation = ready.parts.single().preparation.copy(ready = false, midiQuality = MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)))),
                    readiness = ready.readiness.copy(staleArtifacts = setOf(WorkflowArtifact.CLEAN_MIDI))
                )),
                CreationStage.CLEAN_MIDI,
                CreationStageStatus.STALE,
                CreationIntent.RETRY_MIDI_CLEANUP
            ),
            Case(
                "structure needs saving",
                CreationProgressInput(ready.copy(structure = emptyList(), readiness = ready.readiness.copy(structureReady = false))),
                CreationStage.STRUCTURE,
                CreationStageStatus.CURRENT,
                CreationIntent.SAVE_STRUCTURE
            ),
            Case(
                "stale arrangement structure",
                CreationProgressInput(ready.copy(structure = listOf(StructureSectionSummary(0, "A", 2, "A2", 4.0))), approved),
                CreationStage.ARRANGEMENT,
                CreationStageStatus.STALE,
                CreationIntent.GENERATE_ARRANGEMENT
            ),
            Case(
                "arrangement draft",
                CreationProgressInput(ready, arrangement(ready, approvalRequired = true, approved = false)),
                CreationStage.ARRANGEMENT,
                CreationStageStatus.CURRENT,
                CreationIntent.APPROVE_ARRANGEMENT
            ),
            Case(
                "missing build dependency",
                CreationProgressInput(ready, approved, runtimeReadiness = unavailableBuildReadiness()),
                CreationStage.MIX_AND_MASTER,
                CreationStageStatus.BLOCKED,
                CreationIntent.CONFIGURE_BUILD_DEPENDENCY
            ),
            Case(
                "build failure",
                CreationProgressInput(ready, approved, runtimeReadiness = readyBuildReadiness(), buildEvidence = BuildEvidence.Failed("renderer exited with code 1")),
                CreationStage.MIX_AND_MASTER,
                CreationStageStatus.BLOCKED,
                CreationIntent.RETRY_BUILD
            ),
            Case(
                "completed release",
                CreationProgressInput(ready.copy(readiness = ready.readiness.copy(masterAvailable = true, releaseAvailable = true)), approved),
                CreationStage.MIX_AND_MASTER,
                CreationStageStatus.COMPLETE,
                CreationIntent.BUILD_SONG
            )
        )

        cases.forEach { case ->
            val first = CreationProgressDeriver.derive(case.input)
            val second = CreationProgressDeriver.derive(case.input)
            assertEquals(first, second, case.name)
            assertEquals(CreationStage.entries, first.stages.map(CreationStageProgress::stage), case.name)
            assertEquals(case.stage, first.stages.firstOrNull { it.status != CreationStageStatus.COMPLETE }?.stage ?: first.stages.last().stage, case.name)
            assertEquals(case.status, first[case.stage].status, case.name)
            assertEquals(case.next, first.nextAction.intent, case.name)
            assertEquals(first.nextAction.artifact, first[case.stage].expectedArtifact, case.name)
        }
    }

    @Test
    fun `skipped optional stages do not block later creation steps`() {
        val ready = readyProject()
        val skipped = ready.parts.single().copy(preparation = ready.parts.single().preparation.copy(
            midiAiFix = MidiAiFixSummary(MidiAiFixSelection.SKIP, draftAvailable = false, approvedAvailable = false)
        ))
        val progress = CreationProgressDeriver.derive(CreationProgressInput(ready.copy(
            parts = listOf(skipped),
            readiness = ready.readiness.copy(staleArtifacts = setOf(WorkflowArtifact.AI_FIX, WorkflowArtifact.MIDI_FEEL))
        )))

        assertEquals(CreationStageStatus.COMPLETE, progress[CreationStage.AI_FIX].status)
        assertEquals(CreationStageStatus.COMPLETE, progress[CreationStage.MIDI_FEEL].status)
        assertEquals(CreationStage.ARRANGEMENT, progress.stages.first { it.status != CreationStageStatus.COMPLETE }.stage)
    }

    @Test
    fun `selection remains immutable UI state and rejects unsafe identifiers`() {
        val state = WorkspaceUiState(selectedPartId = "A", selectedArrangementSection = 1, selectedArtifact = CreationArtifactReference(CreationArtifactKind.ANALYSIS, "A"))
        assertEquals(CreationSelection("A", 1, CreationArtifactReference(CreationArtifactKind.ANALYSIS, "A")), state.creationSelection)
        assertFailsWith<IllegalArgumentException> { CreationArtifactReference(CreationArtifactKind.PART_SOURCE, " ") }
    }

    private data class Case(
        val name: String,
        val input: CreationProgressInput,
        val stage: CreationStage,
        val status: CreationStageStatus,
        val next: CreationIntent
    )

    private fun emptyProject() = ProjectSnapshot(
        Path("build/creation-progress"),
        3,
        "progress",
        RenderFormat(),
        emptyList(),
        emptyList(),
        readiness()
    )

    private fun readyProject(): ProjectSnapshot {
        val part = PartSummary(
            "A",
            "verse",
            "source/A.mid",
            "A.mid",
            PartSourceType.MIDI,
            PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json", 4, 4.0, "C major"),
            PartPreparationSummary(
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                emptyList(),
                MidiQualitySummary(MidiQualityStatus.CURRENT),
                midiAiFix = MidiAiFixSummary(MidiAiFixSelection.SKIP)
            )
        )
        return emptyProject().copy(
            parts = listOf(part),
            structure = listOf(StructureSectionSummary(0, "A", 1, "A1", 4.0)),
            readiness = readiness(cleanMidiReady = true, analysesReady = true, structureReady = true).copy(cohesionReady = true)
        )
    }

    private fun arrangement(project: ProjectSnapshot, approvalRequired: Boolean = false, approved: Boolean = true) = ArrangementSnapshot(
        project.root,
        project.structure.map { ArrangementSectionSnapshot(it.index, it.instanceId, it.partId, "verse", 0.5, emptyList(), "none", it.durationSeconds) },
        approvalRequired,
        approved,
        false,
        project.root.resolve(if (approvalRequired) "arrangement.draft.json" else "arrangement.json")
    )

    private fun readiness(cleanMidiReady: Boolean = false, analysesReady: Boolean = false, structureReady: Boolean = false) = ProjectReadiness(
        cleanMidiReady, analysesReady, structureReady, false, false, false, false, false, false, false
    )

    private fun readyBuildReadiness() = RuntimeReadiness.of(*RuntimeDependency.entries.map { it to DependencyReadiness(DependencyStatus.READY, "ready") }.toTypedArray())
    private fun unavailableBuildReadiness() = RuntimeReadiness.of(*RuntimeDependency.entries.map { dependency ->
        dependency to if (dependency == RuntimeDependency.RENDERER) DependencyReadiness(DependencyStatus.UNAVAILABLE, "Configure renderer") else DependencyReadiness(DependencyStatus.READY, "ready")
    }.toTypedArray())
}
