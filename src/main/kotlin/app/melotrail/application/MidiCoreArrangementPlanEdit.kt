package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.arrangement.core.MidiCoreInvalidationPreview
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException

/** A non-mutating inspection of exactly the work a proposed plan edit would make stale. */
data class PreviewMidiCoreArrangementPlanEdit(
    val session: MidiCoreProjectSession,
    val plan: MidiCoreArrangementPlan,
)

/** An explicit confirmation of an already previewable whole-song plan edit. */
data class ConfirmMidiCoreArrangementPlanEdit(
    val session: MidiCoreProjectSession,
    val plan: MidiCoreArrangementPlan,
)

sealed interface MidiCoreArrangementPlanEditResult {
    data class Previewed(val invalidation: MidiCoreInvalidationPreview) : MidiCoreArrangementPlanEditResult

    data class Confirmed(
        val session: MidiCoreProjectSession,
        val plan: MidiCoreArrangementPlan,
        val invalidation: MidiCoreInvalidationPreview,
    ) : MidiCoreArrangementPlanEditResult

    data class Rejected(val problem: MidiCoreArrangementPlanEditProblem) : MidiCoreArrangementPlanEditResult
}

data class MidiCoreArrangementPlanEditProblem(
    val code: MidiCoreArrangementPlanEditProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreArrangementPlanEditProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    PLAN_REQUIRED,
    INVALID_PLAN,
    SAVE_FAILED,
}

/**
 * Keeps plan replacement explicit.  A preview never writes; confirmation only
 * changes plan authority and candidate currentness, leaving immutable artifacts
 * and acceptance/lock references intact for review or restoration.
 */
class MidiCoreArrangementPlanEdit(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
) {
    fun preview(request: PreviewMidiCoreArrangementPlanEdit): MidiCoreArrangementPlanEditResult {
        val current = loadCurrent(request.session) ?: return invalidProject()
        if (current != request.session.project) return staleProject()
        return preview(current, request.plan)
    }

    fun confirm(request: ConfirmMidiCoreArrangementPlanEdit): MidiCoreArrangementPlanEditResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { confirmLocked(request) }

    /** The load, stale-session guard, invalidation decision, and save are one project-state transaction. */
    private fun confirmLocked(request: ConfirmMidiCoreArrangementPlanEdit): MidiCoreArrangementPlanEditResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = loadCurrent(request.session) ?: return invalidProject()
        if (current != request.session.project) return staleProject()
        val preview = preview(current, request.plan)
        if (preview is MidiCoreArrangementPlanEditResult.Rejected) return preview
        val invalidation = (preview as MidiCoreArrangementPlanEditResult.Previewed).invalidation
        if (request.plan == current.arrangementPlan) {
            return MidiCoreArrangementPlanEditResult.Confirmed(
                MidiCoreProjectSession(root, current),
                request.plan,
                invalidation,
            )
        }
        val updated = current.copy(arrangementPlan = request.plan, revision = current.revision + 1L)
            .withInvalidatedCandidates(invalidation.staleCandidateIds)
        return try {
            artifacts.saveProject(root, updated)
            MidiCoreArrangementPlanEditResult.Confirmed(
                MidiCoreProjectSession(root, updated),
                request.plan,
                invalidation,
            )
        } catch (_: MidiCoreProjectSaveException) {
            rejected(
                MidiCoreArrangementPlanEditProblemCode.SAVE_FAILED,
                "The arrangement-plan edit could not be saved safely.",
                "Retry confirmation; the last known-good project remains available.",
            )
        } catch (_: Exception) {
            rejected(
                MidiCoreArrangementPlanEditProblemCode.INVALID_PROJECT,
                "The arrangement-plan edit could not be saved to this project.",
                "Check the project artifacts and try again.",
            )
        }
    }

    private fun preview(
        current: app.melotrail.project.MidiCoreProject,
        plan: MidiCoreArrangementPlan,
    ): MidiCoreArrangementPlanEditResult {
        val authority = current.authority ?: return rejected(
            MidiCoreArrangementPlanEditProblemCode.AUTHORITY_REQUIRED,
            "Confirmed structure and harmony are required before editing an arrangement plan.",
            "Complete Structure & Harmony, then confirm a plan.",
        )
        if (current.arrangementPlan == null) return rejected(
            MidiCoreArrangementPlanEditProblemCode.PLAN_REQUIRED,
            "There is no confirmed arrangement plan to edit.",
            "Choose a style and explicitly confirm its proposed plan first.",
        )
        try {
            plan.requireMatches(authority)
        } catch (error: IllegalArgumentException) {
            return rejected(
                MidiCoreArrangementPlanEditProblemCode.INVALID_PLAN,
                error.message ?: "The arrangement plan does not match current section authority.",
                "Review every occurrence intent and keep the confirmed occurrence order.",
            )
        }
        val before = MidiCoreAuthorityHasher.from(current)
        val after = MidiCoreAuthorityHasher.from(current.copy(arrangementPlan = plan))
        return MidiCoreArrangementPlanEditResult.Previewed(
            MidiCoreInvalidationPlanner.preview(
                before,
                after,
                current.candidates.map { candidate ->
                    MidiCoreCandidateDependency(
                        candidate.id,
                        candidate.role,
                        candidate.occurrenceId,
                        candidate.authorityHash,
                        candidate.acceptedDependencyIds,
                        candidate.draftDependencyRests,
                    )
                },
                current.exportSnapshots.map { snapshot -> MidiCoreExportDependency(snapshot.id, snapshot.authorityHash) },
            ),
        )
    }

    private fun loadCurrent(session: MidiCoreProjectSession) = try {
        artifacts.openProject(session.root.toAbsolutePath().normalize())
    } catch (_: Exception) {
        null
    }

    private fun invalidProject() = rejected(
        MidiCoreArrangementPlanEditProblemCode.INVALID_PROJECT,
        "The project cannot be verified before editing its arrangement plan.",
        "Open a valid MIDI Core project and try again.",
    )

    private fun staleProject() = rejected(
        MidiCoreArrangementPlanEditProblemCode.STALE_PROJECT,
        "The project changed since this arrangement view was opened.",
        "Reload the project, review the affected scopes, and try the edit again.",
    )

    private fun rejected(
        code: MidiCoreArrangementPlanEditProblemCode,
        message: String,
        nextAction: String,
    ) = MidiCoreArrangementPlanEditResult.Rejected(MidiCoreArrangementPlanEditProblem(code, message, nextAction))
}
