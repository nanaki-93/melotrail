package ai.music.workstation.desktop

import ai.music.workstation.application.ArrangementSectionSnapshot
import ai.music.workstation.application.ArrangementSnapshot
import ai.music.workstation.application.MidiQualityStatus
import ai.music.workstation.application.MidiQualitySummary
import ai.music.workstation.application.PartAnalysisStatus
import ai.music.workstation.application.PartAnalysisSummary
import ai.music.workstation.application.PartPreparationSummary
import ai.music.workstation.application.PartSourceType
import ai.music.workstation.application.PartSummary
import ai.music.workstation.application.ProjectReadiness
import ai.music.workstation.application.ProjectSnapshot
import ai.music.workstation.application.StructureSectionSummary
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreationProgressTest {
    @Test
    fun `derives every creation stage deterministically from canonical snapshots`() {
        val ready = readyProject()
        val approved = arrangement(ready)
        val cases = listOf(
            Case("no project", CreationProgressInput(null), mapOf(
                CreationStage.PROJECT to CreationStageStatus.CURRENT,
                CreationStage.PREPARE to CreationStageStatus.NOT_STARTED,
                CreationStage.STRUCTURE to CreationStageStatus.NOT_STARTED,
                CreationStage.ARRANGE to CreationStageStatus.NOT_STARTED,
                CreationStage.MIX_AND_MASTER to CreationStageStatus.NOT_STARTED
            ), CreationIntent.CREATE_OR_OPEN_PROJECT),
            Case("empty project", CreationProgressInput(emptyProject()), mapOf(
                CreationStage.PROJECT to CreationStageStatus.COMPLETE,
                CreationStage.PREPARE to CreationStageStatus.CURRENT,
                CreationStage.STRUCTURE to CreationStageStatus.BLOCKED,
                CreationStage.ARRANGE to CreationStageStatus.BLOCKED,
                CreationStage.MIX_AND_MASTER to CreationStageStatus.BLOCKED
            ), CreationIntent.IMPORT_PART),
            Case("partial preparation", CreationProgressInput(ready.copy(parts = listOf(ready.parts.first().copy(preparation = ready.parts.first().preparation.copy(analyzed = false, ready = false))))), mapOf(
                CreationStage.PREPARE to CreationStageStatus.CURRENT,
                CreationStage.STRUCTURE to CreationStageStatus.BLOCKED,
                CreationStage.ARRANGE to CreationStageStatus.BLOCKED
            ), CreationIntent.ANALYZE_PART),
            Case("stale MIDI quality", CreationProgressInput(ready.copy(parts = listOf(ready.parts.first().copy(preparation = ready.parts.first().preparation.copy(ready = false, midiQuality = MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)))))), mapOf(
                CreationStage.PREPARE to CreationStageStatus.STALE
            ), CreationIntent.RETRY_MIDI_CLEANUP),
            Case("structure needs saving", CreationProgressInput(ready.copy(structure = emptyList(), readiness = ready.readiness.copy(structureReady = false))), mapOf(
                CreationStage.PREPARE to CreationStageStatus.COMPLETE,
                CreationStage.STRUCTURE to CreationStageStatus.CURRENT,
                CreationStage.ARRANGE to CreationStageStatus.BLOCKED
            ), CreationIntent.SAVE_STRUCTURE),
            Case("stale structure arrangement", CreationProgressInput(ready.copy(structure = listOf(StructureSectionSummary(0, "A", 2, "A2", 4.0))), approved), mapOf(
                CreationStage.STRUCTURE to CreationStageStatus.STALE,
                CreationStage.ARRANGE to CreationStageStatus.STALE
            ), CreationIntent.GENERATE_ARRANGEMENT),
            Case("Qwen draft", CreationProgressInput(ready, arrangement(ready, approvalRequired = true, approved = false)), mapOf(
                CreationStage.ARRANGE to CreationStageStatus.CURRENT,
                CreationStage.MIX_AND_MASTER to CreationStageStatus.BLOCKED
            ), CreationIntent.APPROVE_ARRANGEMENT),
            Case("missing build dependency", CreationProgressInput(ready, approved, runtimeReadiness = unavailableBuildReadiness()), mapOf(
                CreationStage.ARRANGE to CreationStageStatus.COMPLETE,
                CreationStage.MIX_AND_MASTER to CreationStageStatus.BLOCKED
            ), CreationIntent.CONFIGURE_BUILD_DEPENDENCY),
            Case("build failure retries build", CreationProgressInput(ready, approved, runtimeReadiness = readyBuildReadiness(), buildEvidence = BuildEvidence.Failed("renderer exited with code 1")), mapOf(
                CreationStage.MIX_AND_MASTER to CreationStageStatus.BLOCKED
            ), CreationIntent.RETRY_BUILD),
            Case("completed release", CreationProgressInput(ready.copy(readiness = ready.readiness.copy(masterAvailable = true, releaseAvailable = true)), approved), mapOf(
                CreationStage.MIX_AND_MASTER to CreationStageStatus.COMPLETE
            ), CreationIntent.BUILD_SONG)
        )

        cases.forEach { case ->
            val first = CreationProgressDeriver.derive(case.input)
            val second = CreationProgressDeriver.derive(case.input)
            assertEquals(first, second, case.name)
            case.statuses.forEach { (stage, status) -> assertEquals(status, first[stage].status, "${case.name}: $stage") }
            assertEquals(case.next, first.nextAction.intent, case.name)
            val nextStage = first.stages.firstOrNull { it.status != CreationStageStatus.COMPLETE } ?: first.stages.last()
            assertEquals(first.nextAction.artifact, nextStage.expectedArtifact, case.name)
        }
    }

    @Test
    fun `blocks impossible structure and approval combinations with a safe recovery target`() {
        val project = readyProject().copy(structure = listOf(StructureSectionSummary(0, "missing", 1, "missing1", 4.0)))
        val invalidStructure = CreationProgressDeriver.derive(CreationProgressInput(project))
        assertEquals(CreationStageStatus.BLOCKED, invalidStructure[CreationStage.STRUCTURE].status)
        assertEquals(CreationIntent.SAVE_STRUCTURE, invalidStructure[CreationStage.STRUCTURE].nextAction.intent)

        val impossibleApproval = CreationProgressDeriver.derive(CreationProgressInput(readyProject(), arrangement(readyProject(), approvalRequired = true, approved = true)))
        assertEquals(CreationStageStatus.BLOCKED, impossibleApproval[CreationStage.ARRANGE].status)
        assertEquals(CreationIntent.GENERATE_ARRANGEMENT, impossibleApproval[CreationStage.ARRANGE].nextAction.intent)
    }

    @Test
    fun `selection remains immutable UI state and never accepts unsafe identifiers`() {
        val state = WorkspaceUiState(selectedPartId = "A", selectedArrangementSection = 1, selectedArtifact = CreationArtifactReference(CreationArtifactKind.ANALYSIS, "A"))
        assertEquals(CreationSelection("A", 1, CreationArtifactReference(CreationArtifactKind.ANALYSIS, "A")), state.creationSelection)
        assertFailsWith<IllegalArgumentException> { CreationArtifactReference(CreationArtifactKind.PART_SOURCE, " ") }
    }

    private data class Case(val name: String, val input: CreationProgressInput, val statuses: Map<CreationStage, CreationStageStatus>, val next: CreationIntent)

    private fun emptyProject() = ProjectSnapshot(Path("build/creation-progress"), 2, "progress", null, emptyList(), emptyList(), readiness())

    private fun readyProject(): ProjectSnapshot {
        val part = PartSummary("A", "verse", "source/A.mid", "A.mid", PartSourceType.MIDI,
            PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json", 4, 4.0, "C major"),
            PartPreparationSummary(true, true, false, true, true, true, true, emptyList(), MidiQualitySummary(MidiQualityStatus.CURRENT)))
        return emptyProject().copy(parts = listOf(part), structure = listOf(StructureSectionSummary(0, "A", 1, "A1", 4.0)), readiness = readiness(cleanMidiReady = true, analysesReady = true, structureReady = true))
    }

    private fun arrangement(project: ProjectSnapshot, approvalRequired: Boolean = false, approved: Boolean = true) = ArrangementSnapshot(
        project.root, project.structure.map { ArrangementSectionSnapshot(it.index, it.instanceId, it.partId, "verse", 0.5, emptyList(), "none", it.durationSeconds) }, approvalRequired, approved, false,
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
