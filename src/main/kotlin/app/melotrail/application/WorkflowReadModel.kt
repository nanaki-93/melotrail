package app.melotrail.application

import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.WorkflowArtifact

/**
 * Read-only workflow truth shared by application adapters. It is calculated
 * from canonical snapshots, whose artifact checks occur at the project
 * boundary; no UI flag or presentation copy is persisted or trusted.
 */
enum class WorkflowStage {
    PROJECT, IMPORT_AND_INSPECTION, TRANSCRIPTION, CLEAN_MIDI, AI_FIX, MIDI_FEEL,
    ANALYSIS, STRUCTURE, ARRANGEMENT, COHESION, HUMANIZATION, RENDER, MIX, MASTER,
    COMMERCIAL_EXPORT
}

enum class WorkflowState { BLOCKED, CURRENT, REVIEW, STALE, COMPLETE }

enum class WorkflowAction {
    CREATE_OR_OPEN, UPDATE_COMPOSITION_SETTINGS, IMPORT, INSPECT, TRANSCRIBE, CLEAN_MIDI, APPROVE_CLEAN_MIDI,
    CREATE_AI_FIX, APPROVE_AI_FIX, SELECT_MIDI_FEEL, ANALYZE, SAVE_STRUCTURE, GENERATE_COHESION,
    APPROVE_COHESION, UPDATE_HARMONY, GENERATE_ARRANGEMENT, APPROVE_ARRANGEMENT, GENERATE_HUMANIZATION, RENDER,
    MIX, MASTER, REVIEW_COMMERCIAL_PROVENANCE
}

/** Typed recovery requirements. Desktop copy belongs to the desktop adapter. */
enum class WorkflowPrerequisite {
    NONE,
    PROJECT_ROOT,
    COMPOSITION_SETTINGS,
    COMPLETE_HARMONY,
    IMPORTED_SOURCE,
    SOURCE_INSPECTION,
    RAW_MIDI,
    CLEANED_MIDI,
    CLEAN_MIDI_APPROVAL,
    APPROVED_AI_FIX,
    SELECTED_MIDI,
    CURRENT_ANALYSIS,
    SAVED_STRUCTURE,
    APPROVED_COHESION,
    APPROVED_ARRANGEMENT,
    HUMANIZATION_SELECTION,
    RENDERED_STEMS,
    DRY_MIX,
    MASTER,
    SOURCE_RIGHTS_ATTESTATION
}

data class WorkflowStep(
    val stage: WorkflowStage,
    val state: WorkflowState,
    val nextAction: WorkflowAction,
    val prerequisite: WorkflowPrerequisite,
    /** Present when one exact part is the next safe target. */
    val partId: String? = null,
    /** Runner-owned durable status; the UI never infers it from files. */
    val stageRun: StageRunSnapshot? = null
)

data class WorkflowReadModel(val steps: List<WorkflowStep>) {
    init { require(steps.map(WorkflowStep::stage) == WorkflowStage.entries) }
    operator fun get(stage: WorkflowStage): WorkflowStep = steps.first { it.stage == stage }
    val current: WorkflowStep get() = steps.firstOrNull { it.state != WorkflowState.COMPLETE } ?: steps.last()
}

object WorkflowReadModelDeriver {
    fun derive(project: ProjectSnapshot?, arrangement: ArrangementSnapshot? = null): WorkflowReadModel {
        if (project == null) return WorkflowReadModel(WorkflowStage.entries.mapIndexed { index, stage ->
            step(
                stage,
                if (index == 0) WorkflowState.CURRENT else WorkflowState.BLOCKED,
                WorkflowAction.CREATE_OR_OPEN,
                WorkflowPrerequisite.PROJECT_ROOT
            )
        })
        val stale = project.readiness.staleArtifacts
        val missingSource = project.parts.firstOrNull { !it.preparation.sourcePreserved }
        val uninspected = project.parts.firstOrNull { !it.preparation.inspected }
        val imported = when {
            project.parts.isEmpty() -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.CURRENT, WorkflowAction.IMPORT, WorkflowPrerequisite.IMPORTED_SOURCE)
            missingSource != null -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.STALE, WorkflowAction.IMPORT, WorkflowPrerequisite.IMPORTED_SOURCE, missingSource.id)
            uninspected != null -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.CURRENT, WorkflowAction.INSPECT, WorkflowPrerequisite.SOURCE_INSPECTION, uninspected.id)
            else -> complete(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowAction.IMPORT)
        }

        val needsTranscription = project.parts.firstOrNull { it.sourceType == PartSourceType.AUDIO && !it.preparation.rawMidi }
        val transcription = when {
            imported.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.TRANSCRIPTION, imported)
            needsTranscription != null -> step(WorkflowStage.TRANSCRIPTION, WorkflowState.CURRENT, WorkflowAction.TRANSCRIBE, WorkflowPrerequisite.RAW_MIDI, needsTranscription.id)
            else -> complete(WorkflowStage.TRANSCRIPTION, WorkflowAction.TRANSCRIBE)
        }

        val approvalRequired = project.parts.firstOrNull { it.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED }
        val needsCleaning = project.parts.firstOrNull {
            !it.preparation.cleanMidi || it.preparation.midiQuality.status == MidiQualityStatus.STALE_OR_INVALID
        }
        val clean = when {
            transcription.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.CLEAN_MIDI, transcription)
            WorkflowArtifact.CLEAN_MIDI in stale -> step(WorkflowStage.CLEAN_MIDI, WorkflowState.STALE, WorkflowAction.CLEAN_MIDI, WorkflowPrerequisite.CLEANED_MIDI, needsCleaning?.id ?: project.parts.firstOrNull()?.id)
            approvalRequired != null -> step(WorkflowStage.CLEAN_MIDI, WorkflowState.REVIEW, WorkflowAction.APPROVE_CLEAN_MIDI, WorkflowPrerequisite.CLEAN_MIDI_APPROVAL, approvalRequired.id)
            needsCleaning != null -> step(WorkflowStage.CLEAN_MIDI, WorkflowState.CURRENT, WorkflowAction.CLEAN_MIDI, WorkflowPrerequisite.CLEANED_MIDI, needsCleaning.id)
            else -> complete(WorkflowStage.CLEAN_MIDI, WorkflowAction.CLEAN_MIDI)
        }

        val invalidAiFix = project.parts.firstOrNull {
            it.preparation.midiAiFix.selected == MidiAiFixSelection.APPROVED && !it.preparation.midiAiFix.approvedAvailable
        }
        val aiFix = when {
            clean.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.AI_FIX, clean)
            invalidAiFix != null -> step(WorkflowStage.AI_FIX, WorkflowState.STALE, WorkflowAction.CREATE_AI_FIX, WorkflowPrerequisite.APPROVED_AI_FIX, invalidAiFix.id)
            else -> complete(WorkflowStage.AI_FIX, WorkflowAction.CREATE_AI_FIX)
        }

        val invalidFeel = project.parts.firstOrNull {
            it.preparation.midiFeel.selected == MidiAnalysisInput.LOFI_FEEL && !it.preparation.midiFeel.available
        }
        val feel = when {
            aiFix.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.MIDI_FEEL, aiFix)
            invalidFeel != null -> step(WorkflowStage.MIDI_FEEL, WorkflowState.STALE, WorkflowAction.SELECT_MIDI_FEEL, WorkflowPrerequisite.SELECTED_MIDI, invalidFeel.id)
            else -> complete(WorkflowStage.MIDI_FEEL, WorkflowAction.SELECT_MIDI_FEEL)
        }

        val unanalyzed = project.parts.firstOrNull { !it.preparation.analyzed }
        val composition = if (project.readiness.compositionSettingsReady) {
            complete(WorkflowStage.PROJECT, WorkflowAction.UPDATE_COMPOSITION_SETTINGS)
        } else {
            step(WorkflowStage.PROJECT, WorkflowState.CURRENT, WorkflowAction.UPDATE_COMPOSITION_SETTINGS, WorkflowPrerequisite.COMPOSITION_SETTINGS)
        }
        val analysis = when {
            feel.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.ANALYSIS, feel)
            composition.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.ANALYSIS, composition)
            WorkflowArtifact.ANALYSIS in stale -> step(WorkflowStage.ANALYSIS, WorkflowState.STALE, WorkflowAction.ANALYZE, WorkflowPrerequisite.CURRENT_ANALYSIS, unanalyzed?.id ?: project.parts.firstOrNull()?.id)
            unanalyzed != null -> step(WorkflowStage.ANALYSIS, WorkflowState.CURRENT, WorkflowAction.ANALYZE, WorkflowPrerequisite.CURRENT_ANALYSIS, unanalyzed.id)
            else -> complete(WorkflowStage.ANALYSIS, WorkflowAction.ANALYZE)
        }

        val structure = when {
            analysis.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.STRUCTURE, analysis)
            project.structure.isEmpty() || !project.readiness.structureReady -> step(WorkflowStage.STRUCTURE, WorkflowState.CURRENT, WorkflowAction.SAVE_STRUCTURE, WorkflowPrerequisite.SAVED_STRUCTURE)
            else -> complete(WorkflowStage.STRUCTURE, WorkflowAction.SAVE_STRUCTURE)
        }
        val arrangementStep = when {
            structure.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.ARRANGEMENT, structure)
            !project.readiness.harmonyReady -> step(WorkflowStage.ARRANGEMENT, WorkflowState.CURRENT, WorkflowAction.UPDATE_HARMONY, WorkflowPrerequisite.COMPLETE_HARMONY)
            WorkflowArtifact.ARRANGEMENT in stale || arrangement?.stale == true ||
                (arrangement != null && project.structure.map { it.instanceId to it.partId } != arrangement.sections.map { it.instanceId to it.partId }) ->
                step(WorkflowStage.ARRANGEMENT, WorkflowState.STALE, WorkflowAction.GENERATE_ARRANGEMENT, WorkflowPrerequisite.APPROVED_ARRANGEMENT)
            arrangement == null -> step(WorkflowStage.ARRANGEMENT, WorkflowState.CURRENT, WorkflowAction.GENERATE_ARRANGEMENT, WorkflowPrerequisite.APPROVED_ARRANGEMENT)
            arrangement.approvalRequired || !arrangement.approved -> step(WorkflowStage.ARRANGEMENT, WorkflowState.REVIEW, WorkflowAction.APPROVE_ARRANGEMENT, WorkflowPrerequisite.APPROVED_ARRANGEMENT)
            else -> complete(WorkflowStage.ARRANGEMENT, WorkflowAction.GENERATE_ARRANGEMENT)
        }
        val cohesion = when {
            arrangementStep.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.COHESION, arrangementStep)
            WorkflowArtifact.COHESION in stale -> step(WorkflowStage.COHESION, WorkflowState.STALE, WorkflowAction.GENERATE_COHESION, WorkflowPrerequisite.APPROVED_COHESION)
            project.readiness.cohesionApprovalRequired -> step(WorkflowStage.COHESION, WorkflowState.REVIEW, WorkflowAction.APPROVE_COHESION, WorkflowPrerequisite.APPROVED_COHESION)
            !project.readiness.cohesionReady -> step(WorkflowStage.COHESION, WorkflowState.CURRENT, WorkflowAction.GENERATE_COHESION, WorkflowPrerequisite.APPROVED_COHESION)
            else -> complete(WorkflowStage.COHESION, WorkflowAction.GENERATE_COHESION)
        }
        val humanization = when {
            cohesion.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.HUMANIZATION, cohesion)
            project.readiness.humanizationSelection == app.melotrail.arrangement.HumanizationSelection.BYPASS -> complete(WorkflowStage.HUMANIZATION, WorkflowAction.GENERATE_HUMANIZATION)
            WorkflowArtifact.HUMANIZATION in stale || !project.readiness.humanizationAvailable -> step(WorkflowStage.HUMANIZATION, WorkflowState.CURRENT, WorkflowAction.GENERATE_HUMANIZATION, WorkflowPrerequisite.HUMANIZATION_SELECTION)
            else -> complete(WorkflowStage.HUMANIZATION, WorkflowAction.GENERATE_HUMANIZATION)
        }
        val render = downstream(WorkflowStage.RENDER, humanization, WorkflowArtifact.STEMS, stale, project.readiness.stemsAvailable, WorkflowAction.RENDER, WorkflowPrerequisite.RENDERED_STEMS)
        val mix = downstream(WorkflowStage.MIX, render, WorkflowArtifact.DRY_MIX, stale, project.readiness.dryMixAvailable, WorkflowAction.MIX, WorkflowPrerequisite.DRY_MIX)
        val master = downstream(WorkflowStage.MASTER, mix, WorkflowArtifact.MASTER, stale, project.readiness.masterAvailable && project.readiness.releaseAvailable, WorkflowAction.MASTER, WorkflowPrerequisite.MASTER)
        val commercial = when {
            master.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.COMMERCIAL_EXPORT, master)
            !project.readiness.commercialSourceAttestationsComplete -> step(WorkflowStage.COMMERCIAL_EXPORT, WorkflowState.REVIEW, WorkflowAction.REVIEW_COMMERCIAL_PROVENANCE, WorkflowPrerequisite.SOURCE_RIGHTS_ATTESTATION)
            else -> step(WorkflowStage.COMMERCIAL_EXPORT, WorkflowState.CURRENT, WorkflowAction.REVIEW_COMMERCIAL_PROVENANCE, WorkflowPrerequisite.NONE)
        }
        val steps = listOf(
            composition, imported, transcription, clean, aiFix,
            feel, analysis, structure, arrangementStep, cohesion, humanization, render, mix, master, commercial
        )
        return WorkflowReadModel(steps.map { step -> step.copy(stageRun = project.readiness.stageRuns.lastOrNull { run ->
            workflowStage(run.stage) == step.stage && run.status in setOf(StageRunStatus.PROCESSING, StageRunStatus.FAILED)
        }) })
    }

    private fun downstream(
        stage: WorkflowStage,
        upstream: WorkflowStep,
        artifact: WorkflowArtifact,
        stale: Set<WorkflowArtifact>,
        available: Boolean,
        action: WorkflowAction,
        prerequisite: WorkflowPrerequisite
    ): WorkflowStep = when {
        upstream.state != WorkflowState.COMPLETE -> blocked(stage, upstream)
        artifact in stale -> step(stage, WorkflowState.STALE, action, prerequisite)
        available -> complete(stage, action)
        else -> step(stage, WorkflowState.CURRENT, action, prerequisite)
    }

    private fun blocked(stage: WorkflowStage, upstream: WorkflowStep) =
        step(stage, WorkflowState.BLOCKED, upstream.nextAction, upstream.prerequisite, upstream.partId)

    private fun complete(stage: WorkflowStage, action: WorkflowAction) =
        step(stage, WorkflowState.COMPLETE, action, WorkflowPrerequisite.NONE)

    private fun workflowStage(stage: StageId): WorkflowStage? = when (stage) {
        StageId.SOURCE -> WorkflowStage.IMPORT_AND_INSPECTION
        StageId.EXTRACTED -> WorkflowStage.TRANSCRIPTION
        StageId.CLEANED -> WorkflowStage.CLEAN_MIDI
        StageId.NORMALIZED, StageId.TRANSPOSED, StageId.CORRECTED -> WorkflowStage.AI_FIX
        StageId.ENHANCED -> WorkflowStage.MIDI_FEEL
        StageId.ANALYZED -> WorkflowStage.ANALYSIS
        StageId.STRUCTURED -> WorkflowStage.STRUCTURE
        StageId.COHESION -> WorkflowStage.COHESION
        StageId.ARRANGED, StageId.GENERATED -> WorkflowStage.ARRANGEMENT
        StageId.RENDERED -> WorkflowStage.RENDER
        StageId.MIXED -> WorkflowStage.MIX
        StageId.MASTERED -> WorkflowStage.MASTER
        StageId.EXPORTED -> WorkflowStage.COMMERCIAL_EXPORT
    }

    private fun step(
        stage: WorkflowStage,
        state: WorkflowState,
        action: WorkflowAction,
        prerequisite: WorkflowPrerequisite,
        partId: String? = null
    ) = WorkflowStep(stage, state, action, prerequisite, partId)
}
