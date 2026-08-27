package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import app.melotrail.structure.MidiCoreHarmonyTimeline
import app.melotrail.structure.MidiCoreHarmonyValidation
import app.melotrail.structure.MidiCoreHarmonyValidator

data class ReplaceMidiCoreHarmony(
    val session: MidiCoreProjectSession,
    val events: List<AuthoritativeChordEvent>,
)

sealed interface MidiCoreAuthoritativeHarmonyResult {
    data class Updated(
        val session: MidiCoreProjectSession,
        val timeline: MidiCoreHarmonyTimeline,
        val validation: MidiCoreHarmonyValidation,
        val invalidation: app.melotrail.arrangement.core.MidiCoreInvalidationPreview,
    ) : MidiCoreAuthoritativeHarmonyResult

    data class Rejected(
        val problem: MidiCoreAuthoritativeHarmonyProblem,
        val validation: MidiCoreHarmonyValidation? = null,
    ) : MidiCoreAuthoritativeHarmonyResult
}

data class MidiCoreAuthoritativeHarmonyProblem(val code: MidiCoreAuthoritativeHarmonyProblemCode, val message: String, val nextAction: String)

enum class MidiCoreAuthoritativeHarmonyProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    AUTHORITY_REQUIRED,
    INVALID_HARMONY,
    SAVE_FAILED,
}

/** Atomically replaces explicit authoritative chord windows without key-based substitution. */
class MidiCoreAuthoritativeHarmony(private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore()) {
    fun replace(request: ReplaceMidiCoreHarmony): MidiCoreAuthoritativeHarmonyResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (_: Exception) {
            return rejected(MidiCoreAuthoritativeHarmonyProblemCode.INVALID_PROJECT, "The project cannot be verified before changing harmony.", "Open a valid MIDI Core project and retry.")
        }
        if (current != request.session.project) {
            return rejected(MidiCoreAuthoritativeHarmonyProblemCode.STALE_PROJECT, "The project changed since this screen was opened.", "Reopen the project before editing authoritative harmony.")
        }
        val authority = current.authority ?: return rejected(MidiCoreAuthoritativeHarmonyProblemCode.AUTHORITY_REQUIRED, "Confirm tempo, meter, key, and structure before defining harmony.", "Complete musical authority and section occurrences first.")
        val validation = MidiCoreHarmonyValidator.validate(authority, request.events)
        if (!validation.valid) {
            return rejected(MidiCoreAuthoritativeHarmonyProblemCode.INVALID_HARMONY, "The authoritative harmony has blocking coverage or realization findings.", "Correct the affected chord windows and retry.", validation)
        }
        val updated = try {
            current.copy(authority = authority.copy(chordEvents = request.events))
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreAuthoritativeHarmonyProblemCode.INVALID_HARMONY, error.message ?: "The authoritative harmony is invalid.", "Save chord windows in deterministic occurrence and tick order.", validation)
        }
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
            MidiCoreAuthoritativeHarmonyResult.Updated(
                MidiCoreProjectSession(root, persisted),
                MidiCoreHarmonyTimeline.build(persisted.authority!!),
                validation,
                invalidation,
            )
        } catch (_: MidiCoreProjectSaveException) {
            rejected(MidiCoreAuthoritativeHarmonyProblemCode.SAVE_FAILED, "Authoritative harmony could not be saved safely.", "Retry the save; the last known-good project remains available.", validation)
        } catch (_: Exception) {
            rejected(MidiCoreAuthoritativeHarmonyProblemCode.INVALID_PROJECT, "Authoritative harmony could not be bound to the project.", "Check project artifacts and retry.", validation)
        }
    }

    private fun rejected(
        code: MidiCoreAuthoritativeHarmonyProblemCode,
        message: String,
        nextAction: String,
        validation: MidiCoreHarmonyValidation? = null,
    ) = MidiCoreAuthoritativeHarmonyResult.Rejected(MidiCoreAuthoritativeHarmonyProblem(code, message, nextAction), validation)
}
