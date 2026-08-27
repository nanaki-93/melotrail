package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.midi.domain.MidiImportValidationResult
import app.melotrail.midi.domain.MidiImportValidator
import app.melotrail.midi.domain.MidiFindingCode
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiTempoEvent
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import app.melotrail.midi.domain.MidiValidationContext
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.ProjectKey
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException

/** Reads optional source timing facts without turning them into project authority. */
data class MidiCoreAuthoritySuggestions(
    val tempo: ProjectTempo?,
    val meter: ProjectMeter?,
)

data class ConfirmMidiCoreAuthority(
    val session: MidiCoreProjectSession,
    val key: ProjectKey,
    val tempo: ProjectTempo,
    val meter: ProjectMeter,
)

sealed interface MidiCoreAuthorityResult {
    data class Confirmed(
        val session: MidiCoreProjectSession,
        val suggestions: MidiCoreAuthoritySuggestions,
        val validation: MidiImportValidationResult,
        val invalidation: app.melotrail.arrangement.core.MidiCoreInvalidationPreview,
    ) : MidiCoreAuthorityResult

    data class Rejected(
        val problem: MidiCoreAuthorityProblem,
        val validation: MidiImportValidationResult? = null,
    ) : MidiCoreAuthorityResult
}

data class MidiCoreAuthorityProblem(
    val code: MidiCoreAuthorityProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreAuthorityProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    SOURCE_REQUIRED,
    MELODY_REQUIRED,
    SOURCE_TIMING_UNSUPPORTED,
    SAVE_FAILED,
}

/**
 * Makes one explicit fixed tempo, meter, key, and mode authoritative for a
 * project. Source facts remain suggestions; no source MIDI is altered here.
 */
class MidiCoreMusicalAuthority(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val validator: MidiImportValidator = MidiImportValidator(),
) {
    fun confirm(request: ConfirmMidiCoreAuthority): MidiCoreAuthorityResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (_: Exception) {
            return rejected(MidiCoreAuthorityProblemCode.INVALID_PROJECT, "The project cannot be verified before confirming musical authority.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) {
            return rejected(MidiCoreAuthorityProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before confirming musical authority.")
        }
        val source = current.sourceMidi ?: return rejected(
            MidiCoreAuthorityProblemCode.SOURCE_REQUIRED,
            "Import one source MIDI file before confirming tempo, meter, and key.",
            "Import a Standard MIDI file first.",
        )
        val melody = current.selectedMelody ?: return rejected(
            MidiCoreAuthorityProblemCode.MELODY_REQUIRED,
            "Select the protected melody before confirming musical authority.",
            "Choose exactly one source track and channel as melody first.",
        )
        val inspection = try {
            reader.inspect(artifacts.verify(root, source.original))
        } catch (_: Exception) {
            return rejected(MidiCoreAuthorityProblemCode.INVALID_PROJECT, "The preserved source MIDI cannot be inspected safely.", "Restore the immutable source artifact and retry.")
        }
        if (inspection.sequence.source.sha256 != source.sha256 || inspection.sequence.source.ppq.value != source.ppq) {
            return rejected(MidiCoreAuthorityProblemCode.INVALID_PROJECT, "The preserved source MIDI no longer matches its project identity.", "Restore the immutable source artifact before changing authority.")
        }

        val sourceValidation = validator.validate(
            inspection,
            MidiValidationContext(MidiMelodySelection(melody.trackIndex, melody.channel), request.key.advisoryPitchClasses),
        )
        val validation = MidiImportValidationResult(
            sourceValidation.findings.filterNot { it.code == MidiFindingCode.MISSING_TEMPO || it.code == MidiFindingCode.MISSING_TIME_SIGNATURE },
        )
        if (validation.disposition == MidiImportDisposition.REJECTED) {
            return rejected(
                MidiCoreAuthorityProblemCode.SOURCE_TIMING_UNSUPPORTED,
                "The source has blocking MIDI findings and cannot supply fixed project authority.",
                "Resolve the blocking timing or melody findings in the source MIDI and import it again.",
                validation,
            )
        }
        val existingAuthority = current.authority
        val authority = ProjectAuthority(
            request.key,
            request.tempo,
            request.meter,
            existingAuthority?.sectionDefinitions.orEmpty(),
            existingAuthority?.occurrences.orEmpty(),
            existingAuthority?.chordEvents.orEmpty(),
            existingAuthority?.pickupTicks ?: 0L,
        )
        val updated = current.copy(authority = authority)
        val invalidation = MidiCoreInvalidationPlanner.preview(
            MidiCoreAuthorityHasher.from(current),
            MidiCoreAuthorityHasher.from(updated),
            current.candidates.map { candidate ->
                MidiCoreCandidateDependency(candidate.id, candidate.role, candidate.occurrenceId, candidate.authorityHash, candidate.acceptedDependencyIds)
            },
            current.exportSnapshots.map { snapshot -> MidiCoreExportDependency(snapshot.id, snapshot.authorityHash) },
        )
        val persisted = updated.withInvalidatedCandidates(invalidation.staleCandidateIds)
        return try {
            artifacts.saveProject(root, persisted)
            MidiCoreAuthorityResult.Confirmed(
                MidiCoreProjectSession(root, persisted),
                suggestions(inspection),
                validation,
                invalidation,
            )
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreAuthorityProblemCode.SAVE_FAILED, "Musical authority could not be saved safely.", "Retry the save; the last known-good project remains available.", validation)
        } catch (_: Exception) {
            rejected(MidiCoreAuthorityProblemCode.INVALID_PROJECT, "Musical authority could not be bound to the project.", "Check project artifacts and retry.", validation)
        }
    }

    private fun suggestions(inspection: app.melotrail.midi.domain.MidiInspectionResult): MidiCoreAuthoritySuggestions {
        val sequence = inspection.sequence
        val tempo = sequence.orderedEvents().filterIsInstance<MidiTempoEvent>()
            .map(MidiTempoEvent::microsecondsPerQuarter).distinct().singleOrNull()?.let(::ProjectTempo)
        val meter = sequence.orderedEvents().filterIsInstance<MidiTimeSignatureEvent>()
            .map { ProjectMeter(it.numerator, it.denominatorExponent) }.distinct().singleOrNull()
        return MidiCoreAuthoritySuggestions(tempo, meter)
    }

    private fun rejected(
        code: MidiCoreAuthorityProblemCode,
        message: String,
        nextAction: String,
        validation: MidiImportValidationResult? = null,
    ): MidiCoreAuthorityResult.Rejected = MidiCoreAuthorityResult.Rejected(MidiCoreAuthorityProblem(code, message, nextAction), validation)
}
