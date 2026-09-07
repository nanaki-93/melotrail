package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import app.melotrail.structure.MidiCoreOccurrenceTimeline

/** An explicit confirmation of whether trailing source silence extends to the next whole bar. */
data class ConfirmMidiCoreArrangementExtent(
    val session: MidiCoreProjectSession,
    val padToNextBar: Boolean,
)

sealed interface MidiCoreArrangementExtentResult {
    data class Confirmed(
        val session: MidiCoreProjectSession,
        val arrangementEndTick: Long,
        val paddingTicks: Long,
        val invalidation: app.melotrail.arrangement.core.MidiCoreInvalidationPreview,
    ) : MidiCoreArrangementExtentResult
    data class Rejected(val problem: MidiCoreArrangementExtentProblem) : MidiCoreArrangementExtentResult
}

data class MidiCoreArrangementExtentProblem(
    val code: MidiCoreArrangementExtentProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreArrangementExtentProblemCode { INVALID_PROJECT, STALE_PROJECT, AUTHORITY_REQUIRED, STRUCTURE_PRESENT, SAVE_FAILED }

/**
 * Changes only the confirmed song boundary.  Existing section windows are not
 * stretched implicitly: the musician must update structure afterwards.
 */
class MidiCoreArrangementExtent(private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore()) {
    fun confirm(request: ConfirmMidiCoreArrangementExtent): MidiCoreArrangementExtentResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try { artifacts.openProject(root) } catch (_: Exception) {
            return rejected(MidiCoreArrangementExtentProblemCode.INVALID_PROJECT, "The project cannot be verified before confirming its arrangement end.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) {
            return rejected(MidiCoreArrangementExtentProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before changing trailing silence.")
        }
        val source = current.sourceMidi ?: return rejected(MidiCoreArrangementExtentProblemCode.INVALID_PROJECT, "Import one source MIDI file before confirming its arrangement end.", "Import a source MIDI first.")
        val authority = current.authority ?: return rejected(MidiCoreArrangementExtentProblemCode.AUTHORITY_REQUIRED, "Confirm tempo and meter before choosing trailing silence.", "Save song settings first.")
        if (authority.occurrences.isNotEmpty()) {
            return rejected(MidiCoreArrangementExtentProblemCode.STRUCTURE_PRESENT, "Sections already define the arrangement boundary.", "Edit sections first, then change padding before recreating their timeline.")
        }
        val end = if (request.padToNextBar) nextBarEnd(source.sourceEndTick, source.ppq, authority.meter) else source.sourceEndTick
        val before = MidiCoreAuthorityHasher.from(current)
        if (end == authority.arrangementEndTick) {
            return MidiCoreArrangementExtentResult.Confirmed(
                MidiCoreProjectSession(root, current),
                end,
                0L,
                MidiCoreInvalidationPlanner.preview(before, before),
            )
        }
        val updated = current.copy(authority = authority.copy(arrangementEndTick = end), revision = current.revision + 1L)
        val invalidation = MidiCoreInvalidationPlanner.preview(
            before, MidiCoreAuthorityHasher.from(updated),
            current.candidates.map { MidiCoreCandidateDependency(it.id, it.role, it.occurrenceId, it.authorityHash, it.acceptedDependencyIds) },
            current.exportSnapshots.map { MidiCoreExportDependency(it.id, it.authorityHash) },
        )
        val persisted = updated.withInvalidatedCandidates(invalidation.staleCandidateIds)
        return try {
            artifacts.saveProject(root, persisted)
            MidiCoreArrangementExtentResult.Confirmed(MidiCoreProjectSession(root, persisted), end, end - source.sourceEndTick, invalidation)
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreArrangementExtentProblemCode.SAVE_FAILED, "The arrangement end could not be saved safely.", "Retry the save; the last known-good project remains available.")
        } catch (_: Exception) {
            rejected(MidiCoreArrangementExtentProblemCode.INVALID_PROJECT, "The arrangement end could not be bound to the project.", "Check project artifacts and retry.")
        }
    }

    private fun nextBarEnd(sourceEnd: Long, ppq: Int, meter: app.melotrail.music.core.ProjectMeter): Long {
        val bar = MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(ppq), meter)
        return if (sourceEnd % bar == 0L) sourceEnd else ((sourceEnd / bar) + 1L) * bar
    }

    private fun rejected(code: MidiCoreArrangementExtentProblemCode, message: String, nextAction: String) =
        MidiCoreArrangementExtentResult.Rejected(MidiCoreArrangementExtentProblem(code, message, nextAction))
}
