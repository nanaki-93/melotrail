package app.melotrail.application

import app.melotrail.arrangement.WorkflowArtifact

/**
 * Read-only workflow truth shared by application adapters. It is calculated
 * from canonical snapshots, whose artifact checks occur at the project
 * boundary; no UI flag is persisted or trusted as completion evidence.
 */
enum class WorkflowStage {
    PROJECT, IMPORT_AND_INSPECTION, TRANSCRIPTION, MIDI_REPAIR, MIDI_FEEL,
    ANALYSIS, STRUCTURE, COHESION, ARRANGEMENT, RENDER, MIX, MASTER,
    COMMERCIAL_EXPORT
}

enum class WorkflowState { BLOCKED, CURRENT, REVIEW, STALE, COMPLETE }

enum class WorkflowAction {
    CREATE_OR_OPEN, MIGRATE_PROJECT, IMPORT, INSPECT, TRANSCRIBE, REPAIR_MIDI, APPROVE_REPAIR,
    SELECT_MIDI_FEEL, ANALYZE, SAVE_STRUCTURE, GENERATE_COHESION,
    APPROVE_COHESION, GENERATE_ARRANGEMENT, APPROVE_ARRANGEMENT, RENDER,
    MIX, MASTER, REVIEW_COMMERCIAL_PROVENANCE
}

data class WorkflowStep(
    val stage: WorkflowStage,
    val state: WorkflowState,
    val context: String,
    val nextAction: WorkflowAction
)

data class WorkflowReadModel(val steps: List<WorkflowStep>) {
    init { require(steps.map(WorkflowStep::stage) == WorkflowStage.entries) }
    operator fun get(stage: WorkflowStage): WorkflowStep = steps.first { it.stage == stage }
    val current: WorkflowStep get() = steps.firstOrNull { it.state != WorkflowState.COMPLETE } ?: steps.last()
}

object WorkflowReadModelDeriver {
    fun derive(project: ProjectSnapshot?, arrangement: ArrangementSnapshot? = null): WorkflowReadModel {
        if (project == null) return WorkflowReadModel(WorkflowStage.entries.mapIndexed { index, stage ->
            if (index == 0) step(stage, WorkflowState.CURRENT, "Create or open a project.", WorkflowAction.CREATE_OR_OPEN)
            else step(stage, WorkflowState.BLOCKED, "Open a project first.", WorkflowAction.CREATE_OR_OPEN)
        })
        if (project.version == 2) return WorkflowReadModel(WorkflowStage.entries.mapIndexed { index, stage ->
            if (index == 0) step(stage, WorkflowState.REVIEW, "Project schema v2 is readable; explicitly migrate it atomically before continuing.", WorkflowAction.MIGRATE_PROJECT)
            else step(stage, WorkflowState.BLOCKED, "Migrate the readable v2 project first.", WorkflowAction.MIGRATE_PROJECT)
        })
        val stale = project.readiness.staleArtifacts
        val imported = when {
            project.parts.isEmpty() -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.CURRENT, "Import a MIDI or eligible solo-piano audio source.", WorkflowAction.IMPORT)
            project.parts.any { !it.preparation.sourcePreserved } -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.STALE, "A canonical source is missing or invalid.", WorkflowAction.IMPORT)
            project.parts.any { !it.preparation.inspected } -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.CURRENT, "Inspect each preserved source before continuing.", WorkflowAction.INSPECT)
            else -> step(WorkflowStage.IMPORT_AND_INSPECTION, WorkflowState.COMPLETE, "All sources are preserved and inspected.", WorkflowAction.IMPORT)
        }
        val transcription = when {
            imported.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.TRANSCRIPTION, imported)
            project.parts.filter { it.sourceType == PartSourceType.AUDIO }.any { !it.preparation.rawMidi } -> step(WorkflowStage.TRANSCRIPTION, WorkflowState.CURRENT, "Transcribe the selected solo-piano source to immutable raw MIDI.", WorkflowAction.TRANSCRIBE)
            else -> step(WorkflowStage.TRANSCRIPTION, WorkflowState.COMPLETE, "Raw MIDI is available for every source that needs transcription.", WorkflowAction.TRANSCRIBE)
        }
        val repair = when {
            transcription.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.MIDI_REPAIR, transcription)
            WorkflowArtifact.MIDI_REPAIR in stale -> step(WorkflowStage.MIDI_REPAIR, WorkflowState.STALE, "Raw MIDI changed; repair it again before analysis.", WorkflowAction.REPAIR_MIDI)
            project.parts.any { it.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED } -> step(WorkflowStage.MIDI_REPAIR, WorkflowState.REVIEW, "Review the MIDI repair evidence before approving it.", WorkflowAction.APPROVE_REPAIR)
            project.parts.any { !it.preparation.cleanMidi || it.preparation.midiQuality.status == MidiQualityStatus.STALE_OR_INVALID } -> step(WorkflowStage.MIDI_REPAIR, WorkflowState.CURRENT, "Publish validated repaired MIDI and its quality report.", WorkflowAction.REPAIR_MIDI)
            else -> step(WorkflowStage.MIDI_REPAIR, WorkflowState.COMPLETE, "Current MIDI repair evidence is available.", WorkflowAction.REPAIR_MIDI)
        }
        val feel = when {
            repair.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.MIDI_FEEL, repair)
            WorkflowArtifact.MIDI_FEEL in stale -> step(WorkflowStage.MIDI_FEEL, WorkflowState.STALE, "The optional Lo-fi Feel artifact is stale; select Original feel or regenerate it.", WorkflowAction.SELECT_MIDI_FEEL)
            project.parts.any { it.preparation.midiFeel.selected.name == "LOFI_FEEL" && !it.preparation.midiFeel.available } -> step(WorkflowStage.MIDI_FEEL, WorkflowState.STALE, "Selected Lo-fi Feel MIDI is not current.", WorkflowAction.SELECT_MIDI_FEEL)
            else -> step(WorkflowStage.MIDI_FEEL, WorkflowState.COMPLETE, "The selected analysis MIDI is current.", WorkflowAction.SELECT_MIDI_FEEL)
        }
        val analysis = when {
            feel.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.ANALYSIS, feel)
            WorkflowArtifact.ANALYSIS in stale -> step(WorkflowStage.ANALYSIS, WorkflowState.STALE, "Selected MIDI changed; analyze it again.", WorkflowAction.ANALYZE)
            project.parts.any { !it.preparation.analyzed } -> step(WorkflowStage.ANALYSIS, WorkflowState.CURRENT, "Analyze every selected MIDI artifact.", WorkflowAction.ANALYZE)
            else -> step(WorkflowStage.ANALYSIS, WorkflowState.COMPLETE, "Current musical analysis is available.", WorkflowAction.ANALYZE)
        }
        val structure = when {
            analysis.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.STRUCTURE, analysis)
            project.structure.isEmpty() || !project.readiness.structureReady -> step(WorkflowStage.STRUCTURE, WorkflowState.CURRENT, "Save a structure using the current part IDs.", WorkflowAction.SAVE_STRUCTURE)
            else -> step(WorkflowStage.STRUCTURE, WorkflowState.COMPLETE, "The saved structure remains valid.", WorkflowAction.SAVE_STRUCTURE)
        }
        val cohesion = when {
            structure.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.COHESION, structure)
            WorkflowArtifact.COHESION in stale -> step(WorkflowStage.COHESION, WorkflowState.STALE, "Analysis or structure changed; regenerate cohesion.", WorkflowAction.GENERATE_COHESION)
            project.readiness.cohesionApprovalRequired -> step(WorkflowStage.COHESION, WorkflowState.REVIEW, "Review and explicitly approve the per-occurrence cohesion plan.", WorkflowAction.APPROVE_COHESION)
            !project.readiness.songPlanAvailable -> step(WorkflowStage.COHESION, WorkflowState.CURRENT, "Generate and review a per-occurrence cohesion plan.", WorkflowAction.GENERATE_COHESION)
            else -> step(WorkflowStage.COHESION, WorkflowState.COMPLETE, "A current cohesion plan is available.", WorkflowAction.GENERATE_COHESION)
        }
        val arrangementStep = when {
            cohesion.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.ARRANGEMENT, cohesion)
            WorkflowArtifact.ARRANGEMENT in stale || arrangement?.stale == true -> step(WorkflowStage.ARRANGEMENT, WorkflowState.STALE, "Arrangement inputs changed; regenerate the arrangement.", WorkflowAction.GENERATE_ARRANGEMENT)
            arrangement == null -> step(WorkflowStage.ARRANGEMENT, WorkflowState.CURRENT, "Generate a detailed arrangement from approved cohesion.", WorkflowAction.GENERATE_ARRANGEMENT)
            arrangement.approvalRequired || !arrangement.approved -> step(WorkflowStage.ARRANGEMENT, WorkflowState.REVIEW, "Review and explicitly approve the arrangement draft.", WorkflowAction.APPROVE_ARRANGEMENT)
            else -> step(WorkflowStage.ARRANGEMENT, WorkflowState.COMPLETE, "The arrangement is approved.", WorkflowAction.GENERATE_ARRANGEMENT)
        }
        val render = downstream(WorkflowStage.RENDER, arrangementStep, WorkflowArtifact.STEMS, stale, project.readiness.stemsAvailable, WorkflowAction.RENDER, "Render current stems from the approved arrangement.")
        val mix = downstream(WorkflowStage.MIX, render, WorkflowArtifact.DRY_MIX, stale, project.readiness.dryMixAvailable, WorkflowAction.MIX, "Create the current dry mix.")
        val master = downstream(WorkflowStage.MASTER, mix, WorkflowArtifact.MASTER, stale, project.readiness.masterAvailable && project.readiness.releaseAvailable, WorkflowAction.MASTER, "Master the current mix and validate its release metadata.")
        val commercial = when {
            master.state != WorkflowState.COMPLETE -> blocked(WorkflowStage.COMMERCIAL_EXPORT, master)
            !project.readiness.commercialSourceAttestationsComplete -> step(WorkflowStage.COMMERCIAL_EXPORT, WorkflowState.REVIEW, "Commercial-ready is blocked until every source has an ownership, permission, or public-domain attestation.", WorkflowAction.REVIEW_COMMERCIAL_PROVENANCE)
            else -> step(WorkflowStage.COMMERCIAL_EXPORT, WorkflowState.CURRENT, "Commercial provenance review is available after mastering.", WorkflowAction.REVIEW_COMMERCIAL_PROVENANCE)
        }
        return WorkflowReadModel(listOf(
            step(WorkflowStage.PROJECT, WorkflowState.COMPLETE, "The canonical project is open.", WorkflowAction.IMPORT),
            imported, transcription, repair, feel, analysis, structure, cohesion, arrangementStep, render, mix, master, commercial
        ))
    }

    private fun downstream(stage: WorkflowStage, upstream: WorkflowStep, artifact: WorkflowArtifact, stale: Set<WorkflowArtifact>, available: Boolean, action: WorkflowAction, context: String): WorkflowStep = when {
        upstream.state != WorkflowState.COMPLETE -> blocked(stage, upstream)
        artifact in stale -> step(stage, WorkflowState.STALE, "$context The previous artifact is retained for inspection only.", action)
        available -> step(stage, WorkflowState.COMPLETE, "Current artifact validated.", action)
        else -> step(stage, WorkflowState.CURRENT, context, action)
    }

    private fun blocked(stage: WorkflowStage, upstream: WorkflowStep) = step(stage, WorkflowState.BLOCKED, "${upstream.stage.name.lowercase().replace('_', ' ')} must be current first.", upstream.nextAction)
    private fun step(stage: WorkflowStage, state: WorkflowState, context: String, action: WorkflowAction) = WorkflowStep(stage, state, context, action)
}
