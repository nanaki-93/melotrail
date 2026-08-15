package app.melotrail.desktop

import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.MixSnapshot
import app.melotrail.application.ProjectSnapshot

/**
 * A UI-neutral view of the canonical creation artifacts. It deliberately does
 * not persist workflow flags: callers refresh the source snapshots after each
 * operation and derive this model again.
 */
enum class CreationStage { PROJECT, PREPARE, STRUCTURE, ARRANGE, MIX_AND_MASTER }

enum class CreationStageStatus { NOT_STARTED, CURRENT, COMPLETE, BLOCKED, STALE }

enum class CreationIntent {
    CREATE_OR_OPEN_PROJECT,
    IMPORT_PART,
    INSPECT_PART,
    RETRY_MIDI_CLEANUP,
    ANALYZE_PART,
    SAVE_STRUCTURE,
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
    CLEAN_MIDI,
    ANALYSIS,
    STRUCTURE,
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
    fun derive(input: CreationProgressInput): CreationProgress {
        val project = input.project
        if (project == null) return CreationProgress(
            listOf(
                stage(CreationStage.PROJECT, CreationStageStatus.CURRENT, "Create or open a project first.", projectFile(), CreationIntent.CREATE_OR_OPEN_PROJECT, "A project root is required."),
                stage(CreationStage.PREPARE, CreationStageStatus.NOT_STARTED, "Project artifact is missing.", CreationArtifactReference(CreationArtifactKind.PART_SOURCE), CreationIntent.CREATE_OR_OPEN_PROJECT, "Create or open a project first."),
                stage(CreationStage.STRUCTURE, CreationStageStatus.NOT_STARTED, "Project artifact is missing.", structureArtifact(), CreationIntent.CREATE_OR_OPEN_PROJECT, "Create or open a project first."),
                stage(CreationStage.ARRANGE, CreationStageStatus.NOT_STARTED, "Project artifact is missing.", arrangementArtifact(), CreationIntent.CREATE_OR_OPEN_PROJECT, "Create or open a project first."),
                stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.NOT_STARTED, "Project artifact is missing.", releaseArtifact(), CreationIntent.CREATE_OR_OPEN_PROJECT, "Create or open a project first.")
            )
        )

        val preparation = preparation(project)
        val structure = structure(project, preparation, input.arrangement)
        val arrangement = arrangement(project, preparation, structure, input.arrangement)
        val mixAndMaster = mixAndMaster(project, arrangement, input.mix, input.runtimeReadiness, input.buildEvidence)
        return CreationProgress(listOf(projectStage(project), preparation, structure, arrangement, mixAndMaster))
    }

    private fun projectStage(project: ProjectSnapshot) = stage(
        CreationStage.PROJECT, CreationStageStatus.COMPLETE, null, projectFile(), CreationIntent.IMPORT_PART, "The canonical project is open."
    )

    private fun preparation(project: ProjectSnapshot): CreationStageProgress {
        if (project.parts.isEmpty()) return stage(
            CreationStage.PREPARE, CreationStageStatus.CURRENT, "No source parts have been imported.", CreationArtifactReference(CreationArtifactKind.PART_SOURCE), CreationIntent.IMPORT_PART, "Import a MIDI, WAV, or MP3 source."
        )
        val incomplete = project.parts.firstOrNull { !it.preparation.ready }
        if (incomplete == null) return stage(CreationStage.PREPARE, CreationStageStatus.COMPLETE, null, CreationArtifactReference(CreationArtifactKind.ANALYSIS), CreationIntent.SAVE_STRUCTURE, "Prepared analyses are available for every part.")
        val preparation = incomplete.preparation
        return when {
            preparation.midiQuality.status.name == "STALE_OR_INVALID" -> stage(CreationStage.PREPARE, CreationStageStatus.STALE, "${incomplete.id} has stale or invalid clean-MIDI quality evidence.", cleanMidi(incomplete.id), CreationIntent.RETRY_MIDI_CLEANUP, "Regenerate clean MIDI quality evidence for ${incomplete.id}.")
            !preparation.sourcePreserved -> stage(CreationStage.PREPARE, CreationStageStatus.STALE, "${incomplete.id} no longer has a preserved project source.", CreationArtifactReference(CreationArtifactKind.PART_SOURCE, incomplete.id), CreationIntent.IMPORT_PART, "Import ${incomplete.id} again to preserve its source.")
            !preparation.inspected -> stage(CreationStage.PREPARE, CreationStageStatus.CURRENT, "${incomplete.id} has not been inspected.", CreationArtifactReference(CreationArtifactKind.PREPARATION_REPORT, incomplete.id), CreationIntent.INSPECT_PART, "Inspect the preserved source for ${incomplete.id}.")
            !preparation.cleanMidi -> stage(CreationStage.PREPARE, CreationStageStatus.CURRENT, "${incomplete.id} has no validated clean MIDI.", cleanMidi(incomplete.id), CreationIntent.RETRY_MIDI_CLEANUP, "Create clean MIDI for ${incomplete.id}.")
            else -> stage(CreationStage.PREPARE, CreationStageStatus.CURRENT, "${incomplete.id} still needs musical analysis.", analysis(incomplete.id), CreationIntent.ANALYZE_PART, "Analyze ${incomplete.id} after preparation.")
        }
    }

    private fun structure(project: ProjectSnapshot, preparation: CreationStageProgress, arrangement: ArrangementSnapshot?): CreationStageProgress {
        val unknown = project.structure.firstOrNull { section -> project.parts.none { it.id == section.partId } }
        if (unknown != null) return stage(CreationStage.STRUCTURE, CreationStageStatus.BLOCKED, "Structure references unknown part ${unknown.partId}.", structureArtifact(), CreationIntent.SAVE_STRUCTURE, "Save a structure containing only imported parts.")
        if (preparation.status != CreationStageStatus.COMPLETE) return stage(CreationStage.STRUCTURE, CreationStageStatus.BLOCKED, "Every part must be prepared before the structure can be arranged.", structureArtifact(), preparation.nextAction.intent, preparation.nextAction.prerequisite)
        if (project.structure.isEmpty() || !project.readiness.structureReady) return stage(CreationStage.STRUCTURE, CreationStageStatus.CURRENT, "No saved song structure is available.", structureArtifact(), CreationIntent.SAVE_STRUCTURE, "Save at least one section.")
        if (arrangement != null && !matchesStructure(project, arrangement)) return stage(CreationStage.STRUCTURE, CreationStageStatus.STALE, "The saved structure no longer matches the arrangement artifact.", arrangementArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "Regenerate the arrangement from the saved structure.")
        return stage(CreationStage.STRUCTURE, CreationStageStatus.COMPLETE, null, structureArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "The saved structure is ready for arrangement.")
    }

    private fun arrangement(project: ProjectSnapshot, preparation: CreationStageProgress, structure: CreationStageProgress, snapshot: ArrangementSnapshot?): CreationStageProgress {
        if (preparation.status != CreationStageStatus.COMPLETE) return stage(CreationStage.ARRANGE, CreationStageStatus.BLOCKED, "Arrangement requires prepared analyses for every part.", arrangementArtifact(), preparation.nextAction.intent, preparation.nextAction.prerequisite)
        if (structure.status == CreationStageStatus.STALE) return stage(CreationStage.ARRANGE, CreationStageStatus.STALE, "Arrangement artifact is stale for the saved structure.", arrangementArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "Regenerate the arrangement from the saved structure.")
        if (structure.status != CreationStageStatus.COMPLETE) return stage(CreationStage.ARRANGE, CreationStageStatus.BLOCKED, "Arrangement requires a current saved structure.", arrangementArtifact(), structure.nextAction.intent, structure.nextAction.prerequisite)
        if (snapshot == null) {
            val status = if (project.readiness.arrangementAvailable || project.readiness.songPlanAvailable) CreationStageStatus.STALE else CreationStageStatus.CURRENT
            val reason = if (status == CreationStageStatus.STALE) "Arrangement artifacts could not be loaded or are stale." else "No approved arrangement has been generated."
            return stage(CreationStage.ARRANGE, status, reason, arrangementArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "Generate a validated arrangement.")
        }
        if (snapshot.root != project.root || snapshot.stale || !matchesStructure(project, snapshot)) return stage(CreationStage.ARRANGE, CreationStageStatus.STALE, "Arrangement artifact is stale for the current project structure.", arrangementArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "Regenerate the arrangement from canonical analyses and structure.")
        if (snapshot.approvalRequired && snapshot.approved) return stage(CreationStage.ARRANGE, CreationStageStatus.BLOCKED, "Arrangement snapshot cannot be both approved and awaiting approval.", arrangementArtifact(), CreationIntent.GENERATE_ARRANGEMENT, "Regenerate a valid arrangement artifact.")
        if (snapshot.approvalRequired || !snapshot.approved) return stage(CreationStage.ARRANGE, CreationStageStatus.CURRENT, "The Qwen arrangement draft requires explicit approval.", CreationArtifactReference(CreationArtifactKind.ARRANGEMENT_DRAFT), CreationIntent.APPROVE_ARRANGEMENT, "Review and explicitly approve the draft.")
        return stage(CreationStage.ARRANGE, CreationStageStatus.COMPLETE, null, arrangementArtifact(), CreationIntent.BUILD_SONG, "The approved arrangement can be built.")
    }

    private fun mixAndMaster(project: ProjectSnapshot, arrangement: CreationStageProgress, mix: MixSnapshot?, readiness: RuntimeReadiness?, evidence: BuildEvidence): CreationStageProgress {
        if (arrangement.status != CreationStageStatus.COMPLETE) return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.BLOCKED, "Build requires a current approved arrangement.", releaseArtifact(), arrangement.nextAction.intent, arrangement.nextAction.prerequisite)
        if (project.readiness.masterAvailable && project.readiness.releaseAvailable) return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.COMPLETE, null, releaseArtifact(), CreationIntent.BUILD_SONG, "The final master and release metadata are available.")
        val capability = readiness?.capability(RuntimeCapability.BUILD_SONG)
        if (capability == null || !capability.available) return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.BLOCKED, capability?.reason ?: "Local build readiness is still being checked.", releaseArtifact(), CreationIntent.CONFIGURE_BUILD_DEPENDENCY, "Make the local build dependencies ready.")
        if (evidence is BuildEvidence.Failed) return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.BLOCKED, "Build failed: ${evidence.reason}", releaseArtifact(), CreationIntent.RETRY_BUILD, "Retry Build Song after resolving the reported failure.")
        if (project.readiness.masterAvailable || (mix != null && mix.stale)) return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.STALE, if (project.readiness.masterAvailable) "Master exists without current release metadata." else "Rendered mix artifacts are stale.", releaseArtifact(), CreationIntent.BUILD_SONG, "Run Build Song to publish a current release.")
        return stage(CreationStage.MIX_AND_MASTER, CreationStageStatus.CURRENT, "No final master has been built.", releaseArtifact(), CreationIntent.BUILD_SONG, "Build the approved arrangement into a validated master.")
    }

    private fun matchesStructure(project: ProjectSnapshot, arrangement: ArrangementSnapshot): Boolean =
        project.structure.map { it.instanceId to it.partId } == arrangement.sections.map { it.instanceId to it.partId }

    private fun stage(stage: CreationStage, status: CreationStageStatus, reason: String?, artifact: CreationArtifactReference, intent: CreationIntent, prerequisite: String) =
        CreationStageProgress(stage, status, reason, artifact, CreationNextAction(intent, prerequisite, artifact))

    private fun projectFile() = CreationArtifactReference(CreationArtifactKind.PROJECT_FILE)
    private fun structureArtifact() = CreationArtifactReference(CreationArtifactKind.STRUCTURE)
    private fun arrangementArtifact() = CreationArtifactReference(CreationArtifactKind.ARRANGEMENT)
    private fun releaseArtifact() = CreationArtifactReference(CreationArtifactKind.RELEASE)
    private fun cleanMidi(partId: String) = CreationArtifactReference(CreationArtifactKind.CLEAN_MIDI, partId)
    private fun analysis(partId: String) = CreationArtifactReference(CreationArtifactKind.ANALYSIS, partId)
}
