package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreInvalidationPreview
import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthorityScopeKey
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreRegisterPreference
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.adapter.MidiCoreArtifactStore

/** The six musician-facing repairs are deliberately finite, named plan adjustments. */
enum class MidiCoreMusicalRepairIntent {
    LEAVE_MORE_MELODY_SPACE,
    SIMPLIFY_PIANO,
    LOWER_PIANO_REGISTER,
    SMOOTH_TRANSITION,
    REDUCE_BASS_MOVEMENT,
    CALMER_DRUMS,
}

/**
 * Versioned, inspectable inputs for a repair.  The resulting plan is still
 * ordinary arrangement authority: this record is intentionally not a second
 * candidate schema or a hidden generation override.
 */
data class MidiCoreMusicalRepairSettings(
    val version: Int,
    val intent: MidiCoreMusicalRepairIntent,
    val occurrenceId: String,
    val changedRoles: List<CandidateRole>,
    val description: String,
) {
    init {
        require(version == VERSION) { "Unsupported musical repair settings version '$version'" }
        require(occurrenceId.matches(SAFE_ID)) { "Repair occurrence identity is invalid" }
        require(changedRoles.isNotEmpty() && changedRoles == changedRoles.distinct().sortedBy(CandidateRole::ordinal)) {
            "Repair role scope must be non-empty and deterministic"
        }
        require(description.isNotBlank() && description.length <= 240 && description.none(Char::isISOControl)) {
            "Repair description is invalid"
        }
    }

    companion object { const val VERSION = 1 }

    val policyId: String get() = "musical-repair-v$version"
}

/** A write-free repair proposal with its projected plan inputs. */
data class MidiCoreMusicalRepairProposal(
    val settings: MidiCoreMusicalRepairSettings,
    val plan: MidiCoreArrangementPlan,
)

/** Translate a named repair into one bounded confirmed-plan adjustment. */
object MidiCoreMusicalRepairPlanner {
    fun propose(
        plan: MidiCoreArrangementPlan,
        occurrenceId: String,
        intent: MidiCoreMusicalRepairIntent,
    ): MidiCoreMusicalRepairProposal {
        require(plan.occurrences.any { it.occurrenceId == occurrenceId }) {
            "Arrangement plan has no occurrence '$occurrenceId'"
        }
        val changedRoles = when (intent) {
            MidiCoreMusicalRepairIntent.LEAVE_MORE_MELODY_SPACE,
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
            MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER -> listOf(CandidateRole.CHORDS)
            MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION -> CandidateRole.entries.toList()
            MidiCoreMusicalRepairIntent.REDUCE_BASS_MOVEMENT -> listOf(CandidateRole.BASS)
            MidiCoreMusicalRepairIntent.CALMER_DRUMS -> listOf(CandidateRole.DRUMS)
        }
        val description = when (intent) {
            MidiCoreMusicalRepairIntent.LEAVE_MORE_MELODY_SPACE ->
                "Sets Chords to sparse and reduces its planned density by 20 points."
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO ->
                "Sets Chords to sparse and reduces its planned density by 35 points."
            MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER ->
                "Sets the selected Chords register preference to low."
            MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION ->
                "Uses gradual entry and a held exit for the selected occurrence."
            MidiCoreMusicalRepairIntent.REDUCE_BASS_MOVEMENT ->
                "Sets Bass to sparse and reduces its planned density by 25 points."
            MidiCoreMusicalRepairIntent.CALMER_DRUMS ->
                "Sets Drums to sparse and reduces its planned density by 30 points."
        }
        val updated = plan.copy(occurrences = plan.occurrences.map { occurrence ->
            if (occurrence.occurrenceId != occurrenceId) occurrence else when (intent) {
                MidiCoreMusicalRepairIntent.LEAVE_MORE_MELODY_SPACE -> occurrence.withRole(CandidateRole.CHORDS) {
                    it.copy(activity = MidiCoreRoleActivity.SPARSE, density = (it.density - 20).coerceAtLeast(0))
                }
                MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO -> occurrence.withRole(CandidateRole.CHORDS) {
                    it.copy(activity = MidiCoreRoleActivity.SPARSE, density = (it.density - 35).coerceAtLeast(0))
                }
                MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER -> occurrence.withRole(CandidateRole.CHORDS) {
                    it.copy(registerPreference = MidiCoreRegisterPreference.LOW)
                }
                MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION -> occurrence.copy(
                    entryIntent = MidiCoreBoundaryIntent.GRADUAL_ENTRY,
                    exitIntent = MidiCoreBoundaryIntent.HOLD,
                )
                MidiCoreMusicalRepairIntent.REDUCE_BASS_MOVEMENT -> occurrence.withRole(CandidateRole.BASS) {
                    it.copy(activity = MidiCoreRoleActivity.SPARSE, density = (it.density - 25).coerceAtLeast(0))
                }
                MidiCoreMusicalRepairIntent.CALMER_DRUMS -> occurrence.withRole(CandidateRole.DRUMS) {
                    it.copy(activity = MidiCoreRoleActivity.SPARSE, density = (it.density - 30).coerceAtLeast(0))
                }
            }
        })
        return MidiCoreMusicalRepairProposal(
            MidiCoreMusicalRepairSettings(MidiCoreMusicalRepairSettings.VERSION, intent, occurrenceId, changedRoles, description),
            updated,
        )
    }

    private fun app.melotrail.project.MidiCoreOccurrenceArrangementPlan.withRole(
        role: CandidateRole,
        change: (MidiCoreRolePlanSettings) -> MidiCoreRolePlanSettings,
    ) = copy(roleSettings = roleSettings.map { setting -> if (setting.role == role) change(setting) else setting })
}

data class PreviewMidiCoreMusicalRepair(
    val session: MidiCoreProjectSession,
    val occurrenceId: String,
    val intent: MidiCoreMusicalRepairIntent,
)

sealed interface MidiCoreMusicalRepairResult {
    data class Prepared(
        val proposal: MidiCoreMusicalRepairProposal,
        val invalidation: MidiCoreInvalidationPreview,
        /** Existing locked selections that the later candidate-acceptance flow must not replace. */
        val lockedCandidateIds: List<String>,
        /** Locked accepted candidates and planned rests in the projected affected scope. */
        val lockedScopes: List<MidiCoreAuthorityScopeKey>,
    ) : MidiCoreMusicalRepairResult

    data class Rejected(val problem: MidiCoreMusicalRepairProblem) : MidiCoreMusicalRepairResult
}

data class MidiCoreMusicalRepairProblem(
    val code: MidiCoreMusicalRepairProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreMusicalRepairProblemCode { PLAN_REQUIRED, INVALID_OCCURRENCE, INVALID_PROJECT, STALE_PROJECT }

/**
 * Prepares the exact settings and invalidation set consumed by the later M09
 * candidate workflow.  It intentionally has no confirm/write operation:
 * defining or previewing a repair must not mutate arrangement authority,
 * acceptances, locks, source MIDI, candidates, or exports.
 */
class MidiCoreMusicalRepair(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
) {
    fun preview(request: PreviewMidiCoreMusicalRepair): MidiCoreMusicalRepairResult {
        val current = try {
            artifacts.openProject(request.session.root.toAbsolutePath().normalize())
        } catch (_: Exception) {
            return rejected(
                MidiCoreMusicalRepairProblemCode.INVALID_PROJECT,
                "The project cannot be verified before preparing a musical repair.",
                "Open a valid MIDI Core project and retry the selected repair.",
            )
        }
        if (current != request.session.project) {
            return rejected(
                MidiCoreMusicalRepairProblemCode.STALE_PROJECT,
                "The project changed since this arrangement view was opened.",
                "Reload the project, then prepare the selected repair again.",
            )
        }
        val currentSession = MidiCoreProjectSession(request.session.root.toAbsolutePath().normalize(), current)
        val proposal = proposal(currentSession, request.occurrenceId, request.intent)
            ?: return missingPlanOrScope(currentSession, request.occurrenceId)
        return try {
            val before = MidiCoreAuthorityHasher.from(current)
            val after = MidiCoreAuthorityHasher.from(current.copy(arrangementPlan = proposal.plan))
            val invalidation = MidiCoreInvalidationPlanner.preview(
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
            )
            val locked = current.acceptances.filter { acceptance ->
                acceptance.locked && acceptance.candidateId in invalidation.staleCandidateIds
            }.map { it.candidateId }.sorted()
            val lockedScopes = (
                current.acceptances.filter { acceptance ->
                    acceptance.locked && acceptance.candidateId in invalidation.staleCandidateIds
                }.map { MidiCoreAuthorityScopeKey(it.occurrenceId, it.role) } +
                    current.acceptedPlannedRests.filter { rest ->
                        rest.locked && invalidation.affects(rest.role, rest.occurrenceId)
                    }.map { MidiCoreAuthorityScopeKey(it.occurrenceId, it.role) }
                ).distinct().sortedWith(compareBy<MidiCoreAuthorityScopeKey> { it.occurrenceId }.thenBy { it.role.ordinal })
            MidiCoreMusicalRepairResult.Prepared(proposal, invalidation, locked, lockedScopes)
        } catch (_: IllegalArgumentException) {
            rejected(
                MidiCoreMusicalRepairProblemCode.INVALID_PROJECT,
                "The project cannot provide a deterministic repair scope from its current authority.",
                "Reload the project, confirm its arrangement plan, and retry the selected repair.",
            )
        }
    }

    private fun proposal(
        session: MidiCoreProjectSession,
        occurrenceId: String,
        intent: MidiCoreMusicalRepairIntent,
    ): MidiCoreMusicalRepairProposal? = session.project.arrangementPlan
        ?.takeIf { plan -> plan.occurrences.any { it.occurrenceId == occurrenceId } }
        ?.let { plan -> MidiCoreMusicalRepairPlanner.propose(plan, occurrenceId, intent) }

    private fun missingPlanOrScope(session: MidiCoreProjectSession, occurrenceId: String) =
        if (session.project.arrangementPlan == null) {
            rejected(
                MidiCoreMusicalRepairProblemCode.PLAN_REQUIRED,
                "A confirmed arrangement plan is required before preparing a musical repair.",
                "Choose and confirm a style plan before repairing one occurrence.",
            )
        } else {
            rejected(
                MidiCoreMusicalRepairProblemCode.INVALID_OCCURRENCE,
                "The selected occurrence '$occurrenceId' is not part of the confirmed arrangement plan.",
                "Select an occurrence from the current song map and retry the repair.",
            )
        }

    private fun rejected(
        code: MidiCoreMusicalRepairProblemCode,
        message: String,
        nextAction: String,
    ) = MidiCoreMusicalRepairResult.Rejected(MidiCoreMusicalRepairProblem(code, message, nextAction))
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
