package app.melotrail.desktop

import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.MixSnapshot
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.WorkflowAction
import app.melotrail.application.WorkflowReadModelDeriver
import app.melotrail.application.WorkflowStage
import app.melotrail.application.WorkflowState
import app.melotrail.application.WorkflowStep

/** Desktop adapter over the canonical ordered workflow. */
enum class CreationStage {
    PROJECT,
    IMPORT_AND_INSPECTION,
    TRANSCRIPTION,
    CLEAN_MIDI,
    AI_FIX,
    MIDI_FEEL,
    ANALYSIS,
    STRUCTURE,
    ARRANGEMENT,
    COHESION,
    MIX_AND_MASTER
}

enum class CreationStageStatus { NOT_STARTED, CURRENT, COMPLETE, BLOCKED, STALE }

enum class CreationIntent {
    CREATE_OR_OPEN_PROJECT,
    MIGRATE_PROJECT,
    CONFIGURE_COMPOSITION_SETTINGS,
    CONFIGURE_HARMONY,
    IMPORT_PART,
    INSPECT_PART,
    TRANSCRIBE_PART,
    CLEAN_MIDI,
    APPROVE_CLEAN_MIDI,
    CREATE_AI_FIX,
    APPROVE_AI_FIX,
    SELECT_MIDI_FEEL,
    ANALYZE_PART,
    SAVE_STRUCTURE,
    GENERATE_COHESION,
    APPROVE_COHESION,
    GENERATE_ARRANGEMENT,
    APPROVE_ARRANGEMENT,
    CONFIGURE_BUILD_DEPENDENCY,
    BUILD_SONG,
    RETRY_BUILD
}

/** Logical artifacts only; no source path can enter desktop state through this contract. */
enum class CreationArtifactKind {
    PROJECT_FILE,
    PART_SOURCE,
    PREPARATION_REPORT,
    RAW_MIDI,
    CLEAN_MIDI,
    AI_FIX_DRAFT,
    AI_FIX_APPROVED,
    LOFI_FEEL_MIDI,
    ANALYSIS,
    STRUCTURE,
    COHESION,
    ARRANGEMENT_DRAFT,
    ARRANGEMENT,
    DRY_MIX,
    LOFI_MIX,
    MASTER,
    RELEASE
}

data class CreationArtifactReference(
    val kind: CreationArtifactKind,
    val partId: String? = null,
    val sectionIndex: Int? = null
) {
    init {
        require(partId == null || partId.isNotBlank()) { "Part artifact references require a non-blank part ID." }
        require(sectionIndex == null || sectionIndex >= 0) { "Section index must not be negative." }
    }
}

data class CreationNextAction(
    val intent: CreationIntent,
    val prerequisite: String,
    val artifact: CreationArtifactReference
)

data class CreationStageProgress(
    val stage: CreationStage,
    val status: CreationStageStatus,
    val reason: String?,
    val expectedArtifact: CreationArtifactReference,
    val nextAction: CreationNextAction
) {
    val allowedNextIntents: List<CreationIntent> get() = listOf(nextAction.intent)
}

/** Transient operation evidence, never a project completion flag. */
sealed interface BuildEvidence {
    data object None : BuildEvidence
    data class Failed(val reason: String) : BuildEvidence {
        init { require(reason.isNotBlank()) { "Build failure reason must not be blank." } }
    }
}

data class CreationProgressInput(
    val project: ProjectSnapshot?,
    val arrangement: ArrangementSnapshot? = null,
    val mix: MixSnapshot? = null,
    val runtimeReadiness: RuntimeReadiness? = null,
    val buildEvidence: BuildEvidence = BuildEvidence.None
)

data class CreationProgress(val stages: List<CreationStageProgress>) {
    init { require(stages.map(CreationStageProgress::stage) == CreationStage.entries) { "Creation stages must be ordered and complete." } }

    operator fun get(stage: CreationStage): CreationStageProgress = stages.first { it.stage == stage }
    val nextAction: CreationNextAction get() = stages.firstOrNull { it.status != CreationStageStatus.COMPLETE }?.nextAction
        ?: stages.last().nextAction
}

/** Immutable UI-only linkage; it is intentionally absent from project artifacts. */
data class CreationSelection(
    val partId: String? = null,
    val sectionIndex: Int? = null,
    val artifact: CreationArtifactReference? = null
)

object CreationProgressDeriver {
    private val orderedWorkflowStages = listOf(
        WorkflowStage.PROJECT,
        WorkflowStage.IMPORT_AND_INSPECTION,
        WorkflowStage.TRANSCRIPTION,
        WorkflowStage.CLEAN_MIDI,
        WorkflowStage.AI_FIX,
        WorkflowStage.MIDI_FEEL,
        WorkflowStage.ANALYSIS,
        WorkflowStage.STRUCTURE,
        WorkflowStage.ARRANGEMENT,
        WorkflowStage.COHESION
    )

    fun derive(input: CreationProgressInput): CreationProgress {
        val workflow = WorkflowReadModelDeriver.derive(input.project, input.arrangement)
        val stages = orderedWorkflowStages.map { stage -> fromWorkflow(workflow[stage], input.project == null) }
        val cohesion = stages.last()
        return CreationProgress(stages + mixAndMaster(input, cohesion))
    }

    private fun fromWorkflow(step: WorkflowStep, noProject: Boolean): CreationStageProgress {
        val stage = when (step.stage) {
            WorkflowStage.PROJECT -> CreationStage.PROJECT
            WorkflowStage.IMPORT_AND_INSPECTION -> CreationStage.IMPORT_AND_INSPECTION
            WorkflowStage.TRANSCRIPTION -> CreationStage.TRANSCRIPTION
            WorkflowStage.CLEAN_MIDI -> CreationStage.CLEAN_MIDI
            WorkflowStage.AI_FIX -> CreationStage.AI_FIX
            WorkflowStage.MIDI_FEEL -> CreationStage.MIDI_FEEL
            WorkflowStage.ANALYSIS -> CreationStage.ANALYSIS
            WorkflowStage.STRUCTURE -> CreationStage.STRUCTURE
            WorkflowStage.COHESION -> CreationStage.COHESION
            WorkflowStage.ARRANGEMENT -> CreationStage.ARRANGEMENT
            else -> error("Unsupported creation workflow stage: ${step.stage}")
        }
        val status = when (step.state) {
            WorkflowState.COMPLETE -> CreationStageStatus.COMPLETE
            WorkflowState.STALE -> CreationStageStatus.STALE
            WorkflowState.BLOCKED -> if (noProject) CreationStageStatus.NOT_STARTED else CreationStageStatus.BLOCKED
            WorkflowState.CURRENT, WorkflowState.REVIEW -> CreationStageStatus.CURRENT
        }
        val artifact = artifact(step)
        val reason = workflowDescription(step).takeUnless { step.state == WorkflowState.COMPLETE }
        return CreationStageProgress(
            stage,
            status,
            reason,
            artifact,
            CreationNextAction(intent(step.nextAction), workflowDescription(step), artifact)
        )
    }

    private fun mixAndMaster(input: CreationProgressInput, cohesion: CreationStageProgress): CreationStageProgress {
        val release = CreationArtifactReference(CreationArtifactKind.RELEASE)
        if (cohesion.status != CreationStageStatus.COMPLETE) return CreationStageProgress(
            CreationStage.MIX_AND_MASTER,
            if (input.project == null) CreationStageStatus.NOT_STARTED else CreationStageStatus.BLOCKED,
            "Build requires current approved full-song Cohesion & Enhance.",
            release,
            cohesion.nextAction.copy(artifact = release)
        )
        val project = requireNotNull(input.project)
        if (project.readiness.masterAvailable && project.readiness.releaseAvailable) return stage(
            CreationStageStatus.COMPLETE, null, CreationIntent.BUILD_SONG, "The final master and release metadata are current."
        )
        val capability = input.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)
        if (capability == null || !capability.available) return stage(
            CreationStageStatus.BLOCKED,
            capability?.reason ?: "Local build readiness is still being checked.",
            CreationIntent.CONFIGURE_BUILD_DEPENDENCY,
            "Make the local build dependencies ready."
        )
        if (input.buildEvidence is BuildEvidence.Failed) return stage(
            CreationStageStatus.BLOCKED,
            "Build failed: ${input.buildEvidence.reason}",
            CreationIntent.RETRY_BUILD,
            "Retry Build Song after resolving the reported failure."
        )
        if (project.readiness.masterAvailable || input.mix?.stale == true) return stage(
            CreationStageStatus.STALE,
            "Rendered release artifacts are stale or incomplete.",
            CreationIntent.BUILD_SONG,
            "Run Build Song to publish a current release."
        )
        return stage(CreationStageStatus.CURRENT, "No final master has been built.", CreationIntent.BUILD_SONG, "Build the approved arrangement into a validated master.")
    }

    private fun stage(status: CreationStageStatus, reason: String?, intent: CreationIntent, prerequisite: String): CreationStageProgress {
        val artifact = CreationArtifactReference(CreationArtifactKind.RELEASE)
        return CreationStageProgress(CreationStage.MIX_AND_MASTER, status, reason, artifact, CreationNextAction(intent, prerequisite, artifact))
    }

    private fun artifact(step: WorkflowStep): CreationArtifactReference {
        val kind = when (step.stage) {
            WorkflowStage.PROJECT -> CreationArtifactKind.PROJECT_FILE
            WorkflowStage.IMPORT_AND_INSPECTION -> if (step.nextAction == WorkflowAction.INSPECT) CreationArtifactKind.PREPARATION_REPORT else CreationArtifactKind.PART_SOURCE
            WorkflowStage.TRANSCRIPTION -> CreationArtifactKind.RAW_MIDI
            WorkflowStage.CLEAN_MIDI -> CreationArtifactKind.CLEAN_MIDI
            WorkflowStage.AI_FIX -> if (step.nextAction == WorkflowAction.APPROVE_AI_FIX) CreationArtifactKind.AI_FIX_DRAFT else CreationArtifactKind.AI_FIX_APPROVED
            WorkflowStage.MIDI_FEEL -> CreationArtifactKind.LOFI_FEEL_MIDI
            WorkflowStage.ANALYSIS -> CreationArtifactKind.ANALYSIS
            WorkflowStage.STRUCTURE -> CreationArtifactKind.STRUCTURE
            WorkflowStage.COHESION -> CreationArtifactKind.COHESION
            WorkflowStage.ARRANGEMENT -> if (step.nextAction == WorkflowAction.APPROVE_ARRANGEMENT) CreationArtifactKind.ARRANGEMENT_DRAFT else CreationArtifactKind.ARRANGEMENT
            else -> error("Unsupported creation artifact stage: ${step.stage}")
        }
        return CreationArtifactReference(kind, step.partId)
    }

    private fun intent(action: WorkflowAction): CreationIntent = when (action) {
        WorkflowAction.CREATE_OR_OPEN -> CreationIntent.CREATE_OR_OPEN_PROJECT
        WorkflowAction.MIGRATE_PROJECT -> CreationIntent.MIGRATE_PROJECT
        WorkflowAction.UPDATE_COMPOSITION_SETTINGS -> CreationIntent.CONFIGURE_COMPOSITION_SETTINGS
        WorkflowAction.UPDATE_HARMONY -> CreationIntent.CONFIGURE_HARMONY
        WorkflowAction.IMPORT -> CreationIntent.IMPORT_PART
        WorkflowAction.INSPECT -> CreationIntent.INSPECT_PART
        WorkflowAction.TRANSCRIBE -> CreationIntent.TRANSCRIBE_PART
        WorkflowAction.CLEAN_MIDI -> CreationIntent.CLEAN_MIDI
        WorkflowAction.APPROVE_CLEAN_MIDI -> CreationIntent.APPROVE_CLEAN_MIDI
        WorkflowAction.CREATE_AI_FIX -> CreationIntent.CREATE_AI_FIX
        WorkflowAction.APPROVE_AI_FIX -> CreationIntent.APPROVE_AI_FIX
        WorkflowAction.SELECT_MIDI_FEEL -> CreationIntent.SELECT_MIDI_FEEL
        WorkflowAction.ANALYZE -> CreationIntent.ANALYZE_PART
        WorkflowAction.SAVE_STRUCTURE -> CreationIntent.SAVE_STRUCTURE
        WorkflowAction.GENERATE_COHESION -> CreationIntent.GENERATE_COHESION
        WorkflowAction.APPROVE_COHESION -> CreationIntent.APPROVE_COHESION
        WorkflowAction.GENERATE_ARRANGEMENT -> CreationIntent.GENERATE_ARRANGEMENT
        WorkflowAction.APPROVE_ARRANGEMENT -> CreationIntent.APPROVE_ARRANGEMENT
        WorkflowAction.GENERATE_HUMANIZATION -> CreationIntent.CONFIGURE_BUILD_DEPENDENCY
        WorkflowAction.RENDER, WorkflowAction.MIX, WorkflowAction.MASTER,
        WorkflowAction.REVIEW_COMMERCIAL_PROVENANCE -> CreationIntent.BUILD_SONG
    }
}
