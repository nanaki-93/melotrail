package app.melotrail.application

import app.melotrail.arrangement.Project
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactGraph
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId
import app.melotrail.profile.CompositionProfileRef

/** Read the complete, ordered harmony aggregate for one canonical project. */
data class GetHarmony(val root: java.nio.file.Path)

/** A create operation may introduce one future section ID, but never an unaddressed list item. */
data class CreateHarmonyEvent(
    val root: java.nio.file.Path,
    val expectedProjectRevision: Long,
    val expectedHarmonyRevision: Int,
    val sectionType: SectionTypeId,
    val event: ChordEvent,
    val atIndex: Int? = null
)

data class UpdateHarmonyEvent(
    val root: java.nio.file.Path,
    val expectedProjectRevision: Long,
    val expectedHarmonyRevision: Int,
    val sectionType: SectionTypeId,
    val event: ChordEvent
)

data class DeleteHarmonyEvent(
    val root: java.nio.file.Path,
    val expectedProjectRevision: Long,
    val expectedHarmonyRevision: Int,
    val sectionType: SectionTypeId,
    val eventId: ChordEventId
)

data class ReorderHarmonyEvent(
    val root: java.nio.file.Path,
    val expectedProjectRevision: Long,
    val expectedHarmonyRevision: Int,
    val sectionType: SectionTypeId,
    val eventId: ChordEventId,
    val toIndex: Int
)

enum class HarmonyValidationCode {
    MISSING_PROJECT_CONTEXT,
    MISSING_REQUIRED_PROGRESSION,
    EMPTY_REQUIRED_PROGRESSION,
    UNSUPPORTED_EXECUTION_FIELD
}

data class HarmonyValidationError(
    val code: HarmonyValidationCode,
    val sectionType: SectionTypeId? = null,
    val eventId: ChordEventId? = null,
    val message: String
)

/** Completeness is profile policy, deliberately separate from structural validity. */
data class HarmonyCompleteness(
    val requiredSections: List<SectionTypeId>,
    val missingSections: List<SectionTypeId>,
    val emptySections: List<SectionTypeId>
) {
    val complete: Boolean get() = missingSections.isEmpty() && emptySections.isEmpty()
}

data class HarmonyView(
    val projectRevision: Long,
    val revision: Int?,
    val progressions: List<ChordProgression>,
    val validationErrors: List<HarmonyValidationError>,
    val completeness: HarmonyCompleteness
) {
    val valid: Boolean get() = validationErrors.none { it.code != HarmonyValidationCode.MISSING_PROJECT_CONTEXT }
    val ready: Boolean get() = valid && completeness.complete
}

data class HarmonyInvalidationPreview(
    val changes: Set<WorkflowChange>,
    val artifacts: Set<WorkflowArtifact>,
    val affectedStages: Set<WorkflowStage>
)

data class HarmonyMutationResult(
    val snapshot: ProjectSnapshot,
    val harmony: HarmonyView,
    val invalidation: HarmonyInvalidationPreview
)

data class HarmonySectionContext(
    val projectRevision: Long,
    val harmonyRevision: Int,
    val sectionType: SectionTypeId,
    val progression: ChordProgression
)

data class GetHarmonySectionContext(val root: java.nio.file.Path, val sectionType: SectionTypeId)

/** Profile-owned defaults; they create editable slots but never choose musical content. */
object HarmonySectionPolicy {
    private val loFiRequired = listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS, SectionTypeId.BRIDGE)

    fun requiredSections(profile: CompositionProfileRef?): List<SectionTypeId> =
        if (profile?.id == "lofi") loFiRequired else emptyList()

    fun emptySeed(profile: CompositionProfileRef?): HarmonySettings? =
        requiredSections(profile).takeIf(List<SectionTypeId>::isNotEmpty)
            ?.let { sections -> HarmonySettings(progressions = sections.map(::ChordProgression)) }
}

/**
 * Pure harmony policy. The project facade supplies a validated aggregate while
 * holding its mutex and is solely responsible for atomic publication.
 */
class HarmonyApplicationService {
    fun query(project: Project): HarmonyView {
        val harmony = project.envelope.harmony
        val required = HarmonySectionPolicy.requiredSections(project.envelope.compositionSettings?.profile)
        val progressions = harmony?.progressions.orEmpty()
        val bySection = progressions.associateBy(ChordProgression::sectionType)
        val missing = required.filterNot(bySection::containsKey)
        val empty = required.filter { bySection[it]?.events?.isEmpty() == true }
        val errors = buildList {
            if (harmony != null && progressions.isNotEmpty() && project.envelope.compositionSettings == null) {
                add(HarmonyValidationError(HarmonyValidationCode.MISSING_PROJECT_CONTEXT, message = "Harmony needs saved composition settings with an executable key."))
            }
            missing.forEach { section ->
                add(HarmonyValidationError(HarmonyValidationCode.MISSING_REQUIRED_PROGRESSION, section, message = "Required ${section.value} progression is missing."))
            }
            empty.forEach { section ->
                add(HarmonyValidationError(HarmonyValidationCode.EMPTY_REQUIRED_PROGRESSION, section, message = "Required ${section.value} progression needs at least one chord."))
            }
            progressions.forEach { progression -> progression.events.forEach { event ->
                event.unsupportedExecutionFields().forEach { field ->
                    add(HarmonyValidationError(HarmonyValidationCode.UNSUPPORTED_EXECUTION_FIELD, progression.sectionType, event.id, "Chord '${event.id.value}' uses unsupported execution field '$field'."))
                }
            } }
        }
        return HarmonyView(
            projectRevision = project.envelope.compositionSettings?.decisionRevision ?: 0,
            revision = harmony?.revision,
            progressions = progressions,
            validationErrors = errors,
            completeness = HarmonyCompleteness(required, missing, empty)
        )
    }

    fun create(project: Project, command: CreateHarmonyEvent): PreparedHarmonyUpdate = mutate(project, command.expectedProjectRevision, command.expectedHarmonyRevision) { harmony ->
        val progression = harmony.progressions.firstOrNull { it.sectionType == command.sectionType }
        val updated = (progression ?: ChordProgression(command.sectionType)).add(command.event, command.atIndex ?: progression?.events?.size ?: 0)
        harmony.copy(progressions = harmony.progressions.replace(command.sectionType, updated))
    }

    fun update(project: Project, command: UpdateHarmonyEvent): PreparedHarmonyUpdate = mutate(project, command.expectedProjectRevision, command.expectedHarmonyRevision) { harmony ->
        val progression = harmony.requireProgression(command.sectionType)
        harmony.copy(progressions = harmony.progressions.replace(command.sectionType, progression.edit(command.event)))
    }

    fun delete(project: Project, command: DeleteHarmonyEvent): PreparedHarmonyUpdate = mutate(project, command.expectedProjectRevision, command.expectedHarmonyRevision) { harmony ->
        val progression = harmony.requireProgression(command.sectionType)
        harmony.copy(progressions = harmony.progressions.replace(command.sectionType, progression.remove(command.eventId)))
    }

    fun reorder(project: Project, command: ReorderHarmonyEvent): PreparedHarmonyUpdate = mutate(project, command.expectedProjectRevision, command.expectedHarmonyRevision) { harmony ->
        val progression = harmony.requireProgression(command.sectionType)
        harmony.copy(progressions = harmony.progressions.replace(command.sectionType, progression.move(command.eventId, command.toIndex)))
    }

    fun context(project: Project, sectionType: SectionTypeId): HarmonySectionContext {
        val view = query(project)
        require(view.valid) { "Harmony is not valid: ${view.validationErrors.joinToString { it.message }}" }
        val progression = view.progressions.firstOrNull { it.sectionType == sectionType }
            ?: throw IllegalArgumentException("No harmony progression exists for section '${sectionType.value}'.")
        require(progression.events.isNotEmpty()) { "Harmony progression '${sectionType.value}' is empty." }
        progression.requireExecutable()
        return HarmonySectionContext(view.projectRevision, requireNotNull(view.revision), sectionType, progression)
    }

    private fun mutate(
        project: Project,
        expectedProjectRevision: Long,
        expectedHarmonyRevision: Int,
        transform: (HarmonySettings) -> HarmonySettings
    ): PreparedHarmonyUpdate {
        val view = query(project)
        require(expectedProjectRevision == view.projectRevision) {
            "Project settings changed from revision $expectedProjectRevision to ${view.projectRevision}; reload harmony before saving."
        }
        val current = requireNotNull(project.envelope.harmony) { "Harmony slots are unavailable until composition settings are saved." }
        require(expectedHarmonyRevision == current.revision) {
            "Harmony changed from revision $expectedHarmonyRevision to ${current.revision}; reload before saving."
        }
        val next = transform(current).copy(revision = current.revision + 1)
        next.requireWellFormed(project.envelope.compositionSettings?.key)
        val invalidation = invalidation()
        val updated = project.copy(
            envelope = project.envelope.copy(harmony = next),
            workflow = invalidation.changes.fold(project.workflow) { workflow, change -> workflow.invalidate(change) }
        )
        return PreparedHarmonyUpdate(updated, query(updated), invalidation)
    }

    private fun HarmonySettings.requireProgression(sectionType: SectionTypeId): ChordProgression =
        progressions.firstOrNull { it.sectionType == sectionType }
            ?: throw IllegalArgumentException("Harmony progression '${sectionType.value}' does not exist.")

    private fun List<ChordProgression>.replace(sectionType: SectionTypeId, replacement: ChordProgression): List<ChordProgression> =
        if (any { it.sectionType == sectionType }) map { if (it.sectionType == sectionType) replacement else it } else this + replacement

    private fun invalidation(): HarmonyInvalidationPreview {
        val changes = setOf(WorkflowChange.HARMONY)
        val artifacts = changes.flatMapTo(linkedSetOf(), WorkflowArtifactGraph::invalidatedBy)
        return HarmonyInvalidationPreview(changes, artifacts, artifacts.mapTo(linkedSetOf(), ::stageFor))
    }

    private fun stageFor(artifact: WorkflowArtifact): WorkflowStage = when (artifact) {
        WorkflowArtifact.AI_FIX -> WorkflowStage.AI_FIX
        WorkflowArtifact.MIDI_FEEL -> WorkflowStage.MIDI_FEEL
        WorkflowArtifact.COHESION -> WorkflowStage.COHESION
        WorkflowArtifact.ARRANGEMENT -> WorkflowStage.ARRANGEMENT
        WorkflowArtifact.GENERATED_MIDI, WorkflowArtifact.STEMS -> WorkflowStage.RENDER
        WorkflowArtifact.DRY_MIX, WorkflowArtifact.AUDIO_TEXTURE -> WorkflowStage.MIX
        WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE -> WorkflowStage.MASTER
        WorkflowArtifact.COMMERCIAL_EXPORT -> WorkflowStage.COMMERCIAL_EXPORT
        else -> WorkflowStage.ANALYSIS
    }
}

data class PreparedHarmonyUpdate(
    val project: Project,
    val harmony: HarmonyView,
    val invalidation: HarmonyInvalidationPreview
)
