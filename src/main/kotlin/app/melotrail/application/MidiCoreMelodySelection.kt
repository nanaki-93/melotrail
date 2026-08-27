package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.midi.domain.MidiImportValidationResult
import app.melotrail.midi.domain.MidiImportValidator
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiMelodySelectionException
import app.melotrail.midi.domain.MidiMelodySelectionFailure
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.midi.domain.MidiProtectedMelodyView
import app.melotrail.midi.domain.MidiValidationContext
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException

/** Binds one protected source track/channel to the project without changing the source MIDI artifact. */
class MidiCoreMelodySelection(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val validator: MidiImportValidator = MidiImportValidator(),
    private val selector: MidiProtectedMelodySelector = MidiProtectedMelodySelector(),
) {
    fun select(request: SelectMidiCoreMelody): MidiCoreMelodySelectionResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (_: Exception) {
            return rejected(MidiCoreMelodySelectionProblemCode.INVALID_PROJECT, "The project cannot be verified before selecting its melody.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) {
            return rejected(MidiCoreMelodySelectionProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before changing melody authority.")
        }
        val source = current.sourceMidi ?: return rejected(
            MidiCoreMelodySelectionProblemCode.SOURCE_REQUIRED,
            "Import one source MIDI file before selecting the protected melody.",
            "Import a Standard MIDI file, then select one track and channel.",
        )
        val inspection = try {
            reader.inspect(artifacts.verify(root, source.original))
        } catch (_: Exception) {
            return rejected(MidiCoreMelodySelectionProblemCode.INVALID_PROJECT, "The preserved source MIDI cannot be inspected safely.", "Restore the original source artifact and retry.")
        }
        if (inspection.sequence.source.sha256 != source.sha256 || inspection.sequence.source.format != source.format || inspection.sequence.source.ppq.value != source.ppq) {
            return rejected(MidiCoreMelodySelectionProblemCode.INVALID_PROJECT, "The preserved source MIDI no longer matches its project identity.", "Restore the original source artifact before selecting melody authority.")
        }
        val melodySelection = try {
            MidiMelodySelection(request.trackIndex, request.channel)
        } catch (_: IllegalArgumentException) {
            return rejected(MidiCoreMelodySelectionProblemCode.INVALID_SELECTION, "Choose one valid source track and MIDI channel.", "Select a track index and a channel from 1 through 16.")
        }
        val view = try {
            selector.select(inspection.sequence, melodySelection)
        } catch (error: MidiMelodySelectionException) {
            return rejected(problemCode(error.failure), error.message ?: "The selected melody cannot be protected safely.", selectionAction(error.failure))
        }
        val validation = validator.validate(inspection, MidiValidationContext(melodySelection))
        if (validation.disposition == MidiImportDisposition.REJECTED) {
            return rejected(
                MidiCoreMelodySelectionProblemCode.SELECTION_REJECTED,
                "The selected melody has blocking safety findings.",
                "Choose a safely pairable source track and channel or repair the source MIDI.",
                validation,
            )
        }
        val selected = SelectedMelodyTrack(request.trackIndex, request.channel, view.identitySha256)
        current.selectedMelody?.let { existing ->
            if (existing == selected) {
                val fingerprint = MidiCoreAuthorityHasher.from(current)
                return MidiCoreMelodySelectionResult.Selected(
                    request.session,
                    view,
                    validation,
                    MidiCoreInvalidationPlanner.preview(fingerprint, fingerprint),
                )
            }
        }
        val updated = current.copy(selectedMelody = selected)
        val invalidation = MidiCoreInvalidationPlanner.preview(
            MidiCoreAuthorityHasher.from(current),
            MidiCoreAuthorityHasher.from(updated),
            current.candidates.map { candidate ->
                MidiCoreCandidateDependency(candidate.id, candidate.role, candidate.occurrenceId, candidate.authorityHash)
            },
            current.exportSnapshots.map { snapshot -> MidiCoreExportDependency(snapshot.id, snapshot.authorityHash) },
        )
        return try {
            artifacts.saveProject(root, updated)
            MidiCoreMelodySelectionResult.Selected(MidiCoreProjectSession(root, updated), view, validation, invalidation)
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreMelodySelectionProblemCode.SAVE_FAILED, "The protected melody could not be saved safely.", "Retry the save; the last known-good project remains available.", validation)
        } catch (_: Exception) {
            rejected(MidiCoreMelodySelectionProblemCode.INVALID_PROJECT, "The protected melody could not be bound to the project.", "Check the project artifacts and retry.", validation)
        }
    }

    private fun problemCode(failure: MidiMelodySelectionFailure): MidiCoreMelodySelectionProblemCode = when (failure) {
        MidiMelodySelectionFailure.TRACK_NOT_FOUND -> MidiCoreMelodySelectionProblemCode.TRACK_NOT_FOUND
        MidiMelodySelectionFailure.CHANNEL_NOT_FOUND -> MidiCoreMelodySelectionProblemCode.CHANNEL_NOT_FOUND
        MidiMelodySelectionFailure.NO_COMPLETE_NOTES -> MidiCoreMelodySelectionProblemCode.NO_COMPLETE_NOTES
        MidiMelodySelectionFailure.UNSUPPORTED_MPE_LIKE_EXPRESSION -> MidiCoreMelodySelectionProblemCode.UNSUPPORTED_MPE_LIKE_EXPRESSION
    }

    private fun selectionAction(failure: MidiMelodySelectionFailure): String = when (failure) {
        MidiMelodySelectionFailure.TRACK_NOT_FOUND -> "Select a track shown in the source MIDI inspection."
        MidiMelodySelectionFailure.CHANNEL_NOT_FOUND -> "Select a channel with complete notes in the chosen track."
        MidiMelodySelectionFailure.NO_COMPLETE_NOTES -> "Select a channel with one or more complete melody notes."
        MidiMelodySelectionFailure.UNSUPPORTED_MPE_LIKE_EXPRESSION -> "Export a single-channel melody before importing it into MIDI Core."
    }

    private fun rejected(
        code: MidiCoreMelodySelectionProblemCode,
        message: String,
        nextAction: String,
        validation: MidiImportValidationResult? = null,
    ): MidiCoreMelodySelectionResult.Rejected =
        MidiCoreMelodySelectionResult.Rejected(MidiCoreMelodySelectionProblem(code, message, nextAction), validation)
}

data class SelectMidiCoreMelody(val session: MidiCoreProjectSession, val trackIndex: Int, val channel: Int)

sealed interface MidiCoreMelodySelectionResult {
    data class Selected(
        val session: MidiCoreProjectSession,
        val view: MidiProtectedMelodyView,
        val validation: MidiImportValidationResult,
        val invalidation: app.melotrail.arrangement.core.MidiCoreInvalidationPreview,
    ) : MidiCoreMelodySelectionResult

    data class Rejected(
        val problem: MidiCoreMelodySelectionProblem,
        val validation: MidiImportValidationResult? = null,
    ) : MidiCoreMelodySelectionResult
}

data class MidiCoreMelodySelectionProblem(
    val code: MidiCoreMelodySelectionProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreMelodySelectionProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    SOURCE_REQUIRED,
    INVALID_SELECTION,
    TRACK_NOT_FOUND,
    CHANNEL_NOT_FOUND,
    NO_COMPLETE_NOTES,
    UNSUPPORTED_MPE_LIKE_EXPRESSION,
    SELECTION_REJECTED,
    SAVE_FAILED,
}
