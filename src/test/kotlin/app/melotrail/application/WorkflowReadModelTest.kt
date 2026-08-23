package app.melotrail.application

import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.FullSongEnhancementSelection
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SafeFailureCode
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageSubject
import app.melotrail.arrangement.WorkflowArtifact
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowReadModelTest {
    @Test
    fun `workflow exposes the complete ordered sequence and exact typed recovery`() {
        val base = project()
        val current = WorkflowReadModelDeriver.derive(base)

        assertEquals(WorkflowStageOrder.ordered, current.steps.map(WorkflowStep::stage))
        assertEquals(WorkflowState.CURRENT, current[WorkflowStage.ARRANGEMENT].state)
        assertEquals(WorkflowState.BLOCKED, current[WorkflowStage.COHESION].state)
        assertEquals(WorkflowAction.GENERATE_ARRANGEMENT, current.current.nextAction)
        assertEquals(WorkflowPrerequisite.APPROVED_ARRANGEMENT, current.current.prerequisite)

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
        assertEquals(WorkflowStage.ARRANGEMENT, workflow.current.stage)
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
    fun `critic and full-song enhancement expose exact blocked current review stale complete bypass and no-op states`() {
        val base = project()
        val approvedArrangement = ArrangementSnapshot(base.root, base.structure.map {
            ArrangementSectionSnapshot(it.index, it.instanceId, it.partId, "verse", 0.5, emptyList(), "none", it.durationSeconds)
        }, approvalRequired = false, approved = true, stale = false, artifact = base.root.resolve("arrangement.json"))
        fun ready(selection: FullSongEnhancementSelection, candidateAvailable: Boolean = false, stale: Set<WorkflowArtifact> = emptySet(), critic: Boolean = true) =
            base.copy(readiness = base.readiness.copy(
                generatedMidiAvailable = true, cohesionReady = true, criticAvailable = critic,
                fullSongEnhancementSelection = selection, fullSongEnhancementAvailable = candidateAvailable,
                staleArtifacts = stale
            ))

        assertEquals(WorkflowState.CURRENT, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.UNRESOLVED), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.BLOCKED, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.UNRESOLVED, critic = false), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.CURRENT, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.UNRESOLVED, critic = false), approvedArrangement)[WorkflowStage.CRITIC].state)
        assertEquals(WorkflowState.STALE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.UNRESOLVED, stale = setOf(WorkflowArtifact.CRITIC)), approvedArrangement)[WorkflowStage.CRITIC].state)
        assertEquals(WorkflowState.REVIEW, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.UNRESOLVED, candidateAvailable = true), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.STALE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.APPROVED), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.COMPLETE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.APPROVED, candidateAvailable = true), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.COMPLETE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.BYPASS), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.COMPLETE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.NO_OP), approvedArrangement)[WorkflowStage.FULL_SONG_ENHANCE].state)
        assertEquals(WorkflowState.COMPLETE, WorkflowReadModelDeriver.derive(ready(FullSongEnhancementSelection.BYPASS), approvedArrangement)[WorkflowStage.HUMANIZATION].state)
    }

    @Test
    fun `normalized workflow contract exposes job lifecycle and immutable artifact versions`() {
        val failedClean = StageRunSnapshot(
            runId = "run-clean", stage = StageId.CLEANED, subject = StageSubject.Part("A"),
            status = StageRunStatus.FAILED, retryable = true, failure = SafeFailureCode.INTERRUPTED
        )
        val completedExtraction = StageRunSnapshot(
            runId = "run-extract", stage = StageId.EXTRACTED, subject = StageSubject.Part("A"),
            status = StageRunStatus.COMPLETED, retryable = false,
            outputs = listOf(StageArtifactSnapshot("run-extract:output:0", "a".repeat(64))),
            reports = listOf(StageArtifactSnapshot("run-extract:report:0", "b".repeat(64)))
        )
        val snapshot = project().copy(readiness = project().readiness.copy(
            stageRuns = listOf(completedExtraction, failedClean)
        ))
        val workflow = WorkflowReadModelDeriver.derive(snapshot)

        val clean = workflow[WorkflowStage.CLEAN_MIDI]
        assertEquals(WorkflowStageStatus.FAILED, clean.status)
        assertEquals("run-clean", clean.job?.id)
        assertEquals(SafeFailureCode.INTERRUPTED, clean.job?.failure)
        assertEquals(WorkflowStage.CLEAN_MIDI, workflow.current.stage)

        val transcription = workflow[WorkflowStage.TRANSCRIPTION]
        assertEquals(WorkflowStageStatus.COMPLETE, transcription.status)
        assertEquals(
            listOf("run-extract:output:0", "run-extract:report:0"),
            transcription.artifactVersions.map(WorkflowArtifactVersion::id)
        )
    }

    @Test
    fun `workflow exposes review and approval separately from completion`() {
        val base = project()
        val sections = base.structure.map {
            ArrangementSectionSnapshot(it.index, it.instanceId, it.partId, "verse", 0.5, emptyList(), "none", it.durationSeconds)
        }
        val review = WorkflowReadModelDeriver.derive(
            base,
            ArrangementSnapshot(base.root, sections, approvalRequired = true, approved = false, stale = false, artifact = base.root.resolve("arrangement.json"))
        )[WorkflowStage.ARRANGEMENT]
        val approved = WorkflowReadModelDeriver.derive(
            base,
            ArrangementSnapshot(base.root, sections, approvalRequired = false, approved = true, stale = false, artifact = base.root.resolve("arrangement.json"))
        )[WorkflowStage.ARRANGEMENT]

        assertEquals(WorkflowStageStatus.REVIEW_REQUIRED, review.status)
        assertEquals(WorkflowApprovalState.WAITING, review.approval)
        assertEquals(WorkflowStageStatus.APPROVED, approved.status)
        assertEquals(WorkflowApprovalState.APPROVED, approved.approval)
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
