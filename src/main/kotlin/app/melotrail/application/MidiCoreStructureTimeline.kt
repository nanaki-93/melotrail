package app.melotrail.application

import app.melotrail.midi.domain.MidiPpq
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import app.melotrail.structure.MidiCoreOccurrencePlacement
import app.melotrail.structure.MidiCoreOccurrenceTimeline
import app.melotrail.structure.MidiCoreStructureEditor

data class ReplaceMidiCoreStructure(
    val session: MidiCoreProjectSession,
    val definitions: List<ProjectSectionDefinition>,
    val occurrences: List<MidiCoreOccurrencePlacement>,
    val pickupTicks: Long = 0L,
    val expectedSongEndTick: Long? = null,
)

sealed interface MidiCoreStructureTimelineResult {
    data class Updated(val session: MidiCoreProjectSession, val markerLabels: List<String>) : MidiCoreStructureTimelineResult
    data class Rejected(val problem: MidiCoreStructureTimelineProblem) : MidiCoreStructureTimelineResult
}

data class MidiCoreStructureTimelineProblem(val code: MidiCoreStructureTimelineProblemCode, val message: String, val nextAction: String)
enum class MidiCoreStructureTimelineProblemCode { INVALID_PROJECT, STALE_PROJECT, AUTHORITY_REQUIRED, DERIVED_WORK_INVALIDATION_REQUIRED, INVALID_STRUCTURE, SAVE_FAILED }

/** Atomically persists the sole target section-occurrence timeline. */
class MidiCoreStructureTimeline(private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore()) {
    fun replace(request: ReplaceMidiCoreStructure): MidiCoreStructureTimelineResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try { artifacts.openProject(root) } catch (_: Exception) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_PROJECT, "The project cannot be verified before changing structure.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) return rejected(MidiCoreStructureTimelineProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before editing structure.")
        val authority = current.authority ?: return rejected(MidiCoreStructureTimelineProblemCode.AUTHORITY_REQUIRED, "Confirm tempo, meter, key, and mode before defining structure.", "Complete musical authority first.")
        val ppq = MidiPpq(requireNotNull(current.sourceMidi).ppq)
        val expectedEnd = request.expectedSongEndTick ?: requireNotNull(current.sourceMidi).sourceEndTick
        val timeline = try {
            MidiCoreOccurrenceTimeline.build(ppq, authority.meter, request.definitions, request.occurrences, request.pickupTicks, expectedEnd)
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, error.message ?: "Structure is invalid.", "Use known section definitions and contiguous positive durations.")
        }
        val changed = authority.sectionDefinitions != request.definitions || authority.occurrences != timeline.occurrences || authority.pickupTicks != timeline.pickupTicks
        if ((current.candidates.isNotEmpty() || current.acceptances.isNotEmpty() || current.exportSnapshots.isNotEmpty()) && changed) {
            return rejected(MidiCoreStructureTimelineProblemCode.DERIVED_WORK_INVALIDATION_REQUIRED, "Changing structure would invalidate immutable derived work.", "Review or explicitly invalidate derived work before changing structure.")
        }
        val updated = try {
            MidiCoreStructureEditor(ppq).replace(authority, request.definitions, request.occurrences, request.pickupTicks)
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, error.message ?: "Structure is invalid.", "Review the structure and authoritative harmony before retrying.")
        }
        return try {
            artifacts.saveProject(root, current.copy(authority = updated))
            MidiCoreStructureTimelineResult.Updated(MidiCoreProjectSession(root, current.copy(authority = updated)), timeline.markerLabels())
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreStructureTimelineProblemCode.SAVE_FAILED, "Structure could not be saved safely.", "Retry the save; the last known-good project remains available.")
        } catch (_: Exception) {
            rejected(MidiCoreStructureTimelineProblemCode.INVALID_PROJECT, "Structure could not be saved to this project.", "Check project artifacts and retry.")
        }
    }

    private fun rejected(code: MidiCoreStructureTimelineProblemCode, message: String, nextAction: String) =
        MidiCoreStructureTimelineResult.Rejected(MidiCoreStructureTimelineProblem(code, message, nextAction))
}
