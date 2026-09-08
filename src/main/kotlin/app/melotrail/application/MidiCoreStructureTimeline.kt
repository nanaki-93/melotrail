package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import app.melotrail.structure.MidiCoreOccurrenceTimeline

data class ReplaceMidiCoreStructure(
    val session: MidiCoreProjectSession,
    val definitions: List<ProjectSectionDefinition>,
    val occurrences: List<MidiCoreBarOccurrencePlacement>,
)

sealed interface MidiCoreStructureTimelineResult {
    data class Updated(
        val session: MidiCoreProjectSession,
        val markerLabels: List<String>,
        val invalidation: app.melotrail.arrangement.core.MidiCoreInvalidationPreview,
    ) : MidiCoreStructureTimelineResult
    data class Rejected(val problem: MidiCoreStructureTimelineProblem) : MidiCoreStructureTimelineResult
}

data class MidiCoreStructureTimelineProblem(val code: MidiCoreStructureTimelineProblemCode, val message: String, val nextAction: String)
enum class MidiCoreStructureTimelineProblemCode { INVALID_PROJECT, STALE_PROJECT, AUTHORITY_REQUIRED, INVALID_STRUCTURE, SAVE_FAILED }

/** Atomically persists the sole target section-occurrence timeline. */
class MidiCoreStructureTimeline(private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore()) {
    fun replace(request: ReplaceMidiCoreStructure): MidiCoreStructureTimelineResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { replaceLocked(request) }

    private fun replaceLocked(request: ReplaceMidiCoreStructure): MidiCoreStructureTimelineResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try { artifacts.openProject(root) } catch (_: Exception) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_PROJECT, "The project cannot be verified before changing structure.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) return rejected(MidiCoreStructureTimelineProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before editing structure.")
        val authority = current.authority ?: return rejected(MidiCoreStructureTimelineProblemCode.AUTHORITY_REQUIRED, "Confirm tempo, meter, key, and mode before defining structure.", "Complete musical authority first.")
        val ppq = MidiPpq(requireNotNull(current.sourceMidi).ppq)
        val expectedEnd = authority.arrangementEndTick
        val timeline = try {
            MidiCoreOccurrenceTimeline.buildFromBars(ppq, authority.meter, request.definitions, request.occurrences, expectedEnd)
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, error.message ?: "Structure is invalid.", "Use positive whole-bar section lengths whose total exactly matches the source melody.")
        }
        val updatedAuthority = try {
            authority.copy(
                sectionDefinitions = request.definitions,
                occurrences = timeline.occurrences,
                pickupTicks = 0L,
                arrangementEndTick = expectedEnd,
            )
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, error.message ?: "Structure is invalid.", "Review the structure and authoritative harmony before retrying.")
        }
        // A plan is exact occurrence authority. Keep it only when the edited
        // timeline still matches every referenced occurrence; never retarget a
        // purpose/phrase record to a new or reordered section implicitly.
        val compatiblePlan = current.arrangementPlan?.takeIf { plan ->
            runCatching { plan.requireMatches(updatedAuthority) }.isSuccess
        }
        val survivingIds = updatedAuthority.occurrences.map { it.id }.toSet()
        val updatedProject = try {
            current.copy(
                authority = updatedAuthority,
                arrangementPlan = compatiblePlan,
                revision = current.revision + 1L,
                candidates = current.candidates.map { candidate ->
                    if (candidate.occurrenceId !in survivingIds && candidate.status != MidiCoreCandidateStatus.REJECTED)
                        candidate.copy(status = MidiCoreCandidateStatus.STALE)
                    else candidate
                },
            )
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, error.message ?: "Structure is incompatible with current authority.", "Update dependent authority windows before retrying.")
        }
        val invalidation = MidiCoreInvalidationPlanner.preview(
            MidiCoreAuthorityHasher.from(current),
            MidiCoreAuthorityHasher.from(updatedProject),
            current.candidates.map { candidate ->
                MidiCoreCandidateDependency(candidate.id, candidate.role, candidate.occurrenceId, candidate.authorityHash, candidate.acceptedDependencyIds, candidate.draftDependencyRests)
            },
            current.exportSnapshots.map { snapshot -> MidiCoreExportDependency(snapshot.id, snapshot.authorityHash) },
        )
        val persisted = updatedProject.withInvalidatedCandidates(invalidation.staleCandidateIds)
        return try {
            artifacts.saveProject(root, persisted)
            MidiCoreStructureTimelineResult.Updated(MidiCoreProjectSession(root, persisted), timeline.markerLabels(), invalidation)
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreStructureTimelineProblemCode.SAVE_FAILED, "Structure could not be saved safely.", "Retry the save; the last known-good project remains available.")
        } catch (_: Exception) {
            rejected(MidiCoreStructureTimelineProblemCode.INVALID_PROJECT, "Structure could not be saved to this project.", "Check project artifacts and retry.")
        }
    }

    private fun rejected(code: MidiCoreStructureTimelineProblemCode, message: String, nextAction: String) =
        MidiCoreStructureTimelineResult.Rejected(MidiCoreStructureTimelineProblem(code, message, nextAction))
}
