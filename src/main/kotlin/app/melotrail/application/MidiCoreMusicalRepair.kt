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

/**
 * A reviewable repair alternative.  Its [semanticSignature] deliberately
 * omits velocity: a velocity-only variation is cosmetic rather than a useful
 * musical option for this workflow.
 */
data class MidiCoreMusicalRepairAlternative(
    val candidateId: String,
    val semanticSignature: String,
    val impactLabel: String,
    val matchesBaseline: Boolean = false,
)

data class MidiCoreMusicalRepairAlternativeRejection(
    val candidateId: String,
    val reason: String,
) {
    init {
        require(candidateId.isNotBlank() && reason.isNotBlank()) { "Rejected repair evidence must be explained" }
    }
}

data class MidiCoreMusicalRepairAlternativeRanking(
    val alternatives: List<MidiCoreMusicalRepairAlternative>,
    val rejections: List<MidiCoreMusicalRepairAlternativeRejection>,
    /** Honest terminal explanation; generation is never retried by changing seeds indefinitely. */
    val noResultReason: String? = null,
) {
    init {
        require(alternatives.size <= MAXIMUM_ALTERNATIVES) { "Repair alternatives must be bounded" }
        require(alternatives.map(MidiCoreMusicalRepairAlternative::candidateId).distinct().size == alternatives.size) {
            "Repair alternatives must not repeat candidates"
        }
        require(rejections.map { it.candidateId } == rejections.map { it.candidateId }.distinct().sorted()) {
            "Rejected repair alternatives must be deterministic"
        }
        require((alternatives.isEmpty()) == (noResultReason != null)) {
            "A repair ranking must explain exactly when it has no usable alternative"
        }
    }

    companion object { const val MAXIMUM_ALTERNATIVES = 3 }
}

/**
 * Keeps only musically observable alternatives.  The signature has note
 * timing and pitch but no velocity so seed/velocity-only duplicates cannot
 * crowd out a genuinely different texture.
 */
object MidiCoreMusicalRepairAlternativeRanker {
    fun rank(
        candidates: List<MidiCoreCandidateReviewItem>,
        intent: MidiCoreMusicalRepairIntent,
        baseline: MidiCoreCandidateReviewItem? = null,
        heldThresholdTicks: Long = 480L,
        attemptedCount: Int = candidates.size,
        generationProblems: List<String> = emptyList(),
        allowBaselineReuse: Boolean = false,
    ): MidiCoreMusicalRepairAlternativeRanking {
        require(heldThresholdTicks > 0L) { "Repair held-note threshold must be positive" }
        require(attemptedCount in 0..3 && candidates.size <= attemptedCount) { "Repair generation attempts must be bounded" }
        val baselineSignature = baseline?.let(::semanticSignature)
        val retainedBySignature = linkedMapOf<String, MidiCoreCandidateReviewItem>()
        val rejected = mutableListOf<MidiCoreMusicalRepairAlternativeRejection>()
        candidates.sortedBy { it.candidate.id }.forEach { candidate ->
            val signature = semanticSignature(candidate)
            val unusableReason = when {
                !candidate.authorityCurrent -> "This candidate is stale for the confirmed repair settings."
                candidate.candidate.status == app.melotrail.project.MidiCoreCandidateStatus.REJECTED ->
                    "This candidate was rejected and remains evidence only."
                candidate.candidate.status == app.melotrail.project.MidiCoreCandidateStatus.STALE ->
                    "This candidate is stale and remains evidence only."
                else -> null
            }
            if (unusableReason != null) {
                rejected += MidiCoreMusicalRepairAlternativeRejection(candidate.candidate.id, unusableReason)
                return@forEach
            }
            if (signature == baselineSignature && !allowBaselineReuse) {
                rejected += MidiCoreMusicalRepairAlternativeRejection(
                    candidate.candidate.id,
                    "This candidate matches the audible baseline's note timing and pitches; velocity-only changes are cosmetic.",
                )
                return@forEach
            }
            val existing = retainedBySignature[signature]
            if (existing == null) {
                retainedBySignature[signature] = candidate
            } else {
                // Prefer current evidence, then the stable identifier.  This
                // leaves rejected/stale evidence inspectable without showing
                // it as another audible choice.
                val preferred = listOf(existing, candidate).sortedWith(
                    compareByDescending<MidiCoreCandidateReviewItem> { it.authorityCurrent }
                        .thenByDescending { it.candidate.status == app.melotrail.project.MidiCoreCandidateStatus.CURRENT }
                        .thenBy { it.candidate.id },
                ).first()
                retainedBySignature[signature] = preferred
                val duplicate = if (preferred == existing) candidate else existing
                rejected += MidiCoreMusicalRepairAlternativeRejection(
                    duplicate.candidate.id,
                    "This candidate duplicates ${preferred.candidate.id} in note timing and pitches; velocity-only changes are cosmetic.",
                )
            }
        }
        val retained = retainedBySignature.map { (signature, item) ->
            MidiCoreMusicalRepairAlternative(item.candidate.id, signature, impactLabel(item, baseline, heldThresholdTicks), signature == baselineSignature)
        }.sortedWith(compareBy<MidiCoreMusicalRepairAlternative> { alternative ->
            intentScore(intent, candidates.single { it.candidate.id == alternative.candidateId }, heldThresholdTicks)
        }.thenBy { it.semanticSignature }.thenBy { it.candidateId })
        retained.drop(MidiCoreMusicalRepairAlternativeRanking.MAXIMUM_ALTERNATIVES).forEach { alternative ->
            rejected += MidiCoreMusicalRepairAlternativeRejection(
                alternative.candidateId,
                "This valid candidate ranked outside the three-choice repair limit.",
            )
        }
        val orderedRejections = rejected.distinctBy { it.candidateId }.sortedBy { it.candidateId }
        return MidiCoreMusicalRepairAlternativeRanking(
            retained.take(MidiCoreMusicalRepairAlternativeRanking.MAXIMUM_ALTERNATIVES),
            orderedRejections,
            noResultReason = if (retained.isEmpty()) {
                noResultReason(attemptedCount, generationProblems, orderedRejections, baseline != null)
            } else null,
        )
    }

    private fun semanticSignature(item: MidiCoreCandidateReviewItem): String = item.notes
        .sortedWith(compareBy<MidiCoreReviewNote> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch })
        .joinToString(";") { note -> "${note.startTick}-${note.endTick}-${note.pitch}" }

    private fun impactLabel(item: MidiCoreCandidateReviewItem, baseline: MidiCoreCandidateReviewItem?, heldThreshold: Long): String {
        val metrics = metrics(item, heldThreshold)
        if (baseline == null) return "${metrics.attacks} attacks · ${metrics.notes} notes · ${metrics.held} held"
        val prior = metrics(baseline, heldThreshold)
        return listOf(
            delta(metrics.attacks - prior.attacks, "attack"),
            delta(metrics.notes - prior.notes, "note"),
            delta(metrics.held - prior.held, "held note"),
        ).joinToString(" · ")
    }

    private fun delta(value: Int, noun: String): String = when {
        value < 0 -> "${-value} fewer ${noun}${if (value == -1) "" else "s"}"
        value > 0 -> "$value more ${noun}${if (value == 1) "" else "s"}"
        else -> "same ${noun} count"
    }

    private fun intentScore(intent: MidiCoreMusicalRepairIntent, item: MidiCoreCandidateReviewItem, heldThreshold: Long): Long {
        val metrics = metrics(item, heldThreshold)
        return when (intent) {
            MidiCoreMusicalRepairIntent.LEAVE_MORE_MELODY_SPACE,
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
            MidiCoreMusicalRepairIntent.CALMER_DRUMS,
            -> metrics.attacks * 100_000L + metrics.notes * 1_000L - metrics.held
            MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER ->
                (metrics.pitchSum * 1_000L / metrics.notes.coerceAtLeast(1)) + metrics.attacks
            MidiCoreMusicalRepairIntent.REDUCE_BASS_MOVEMENT -> metrics.pitchMovement * 100_000L + metrics.attacks * 1_000L + metrics.notes
            MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION -> -metrics.heldTicks + metrics.attacks * 1_000L + metrics.pitchMovement
        }
    }

    private fun metrics(item: MidiCoreCandidateReviewItem, heldThreshold: Long): Metrics {
        val ordered = item.notes.sortedWith(compareBy<MidiCoreReviewNote> { it.startTick }.thenBy { it.pitch })
        return Metrics(
            attacks = ordered.map { it.startTick }.distinct().size,
            notes = ordered.size,
            held = ordered.count { it.endTick - it.startTick >= heldThreshold },
            heldTicks = ordered.sumOf { it.endTick - it.startTick },
            pitchSum = ordered.sumOf { it.pitch.toLong() },
            pitchMovement = ordered.zipWithNext().sumOf { (first, second) -> kotlin.math.abs(second.pitch - first.pitch).toLong() },
        )
    }

    private fun noResultReason(
        attemptedCount: Int,
        generationProblems: List<String>,
        rejections: List<MidiCoreMusicalRepairAlternativeRejection>,
        hadBaseline: Boolean,
    ): String {
        val reason = when {
            attemptedCount == 0 -> "No generation attempt was available."
            hadBaseline && rejections.isNotEmpty() && rejections.all {
                it.reason.contains("matches the audible baseline") || it.reason.contains("duplicates")
            } -> "Every validated result matched the audible baseline or duplicated another result."
            rejections.isNotEmpty() -> "Every published result was stale, rejected, or duplicated other repair evidence."
            generationProblems.isNotEmpty() -> "$attemptedCount bounded generation attempts produced no validated candidate: ${generationProblems.distinct().sorted().joinToString("; ")}"
            else -> "No current semantically distinct validated alternative was produced."
        }
        return "$reason Retry this same scope after reviewing its confirmed repair settings; seed hunting will not continue automatically."
    }

    private data class Metrics(
        val attacks: Int,
        val notes: Int,
        val held: Int,
        val heldTicks: Long,
        val pitchSum: Long,
        val pitchMovement: Long,
    )
}

data class RankMidiCoreMusicalRepairAlternatives(
    val candidates: ListMidiCoreCandidates,
    val intent: MidiCoreMusicalRepairIntent,
    val generatedCandidateIds: List<String>,
    val baselineCandidateId: String?,
    val attemptedCount: Int,
    val generationProblems: List<String> = emptyList(),
    val allowBaselineReuse: Boolean = false,
) {
    init {
        require(attemptedCount in 0..3) { "Repair attempts must be bounded" }
        require(generatedCandidateIds.size <= attemptedCount && generatedCandidateIds == generatedCandidateIds.distinct()) {
            "Repair candidates must identify only this bounded attempt"
        }
        require(baselineCandidateId == null || baselineCandidateId !in generatedCandidateIds) {
            "Repair baseline must precede the generated alternatives"
        }
    }
}

/**
 * Loads immutable same-scope candidate evidence before ranking it.  This is
 * intentionally read-only, so an A/B request cannot publish, accept, reject,
 * or otherwise alter a project.
 */
class MidiCoreMusicalRepairAlternatives(
    private val review: MidiCoreCandidateReview = MidiCoreCandidateReview(),
) {
    fun rank(request: RankMidiCoreMusicalRepairAlternatives): MidiCoreMusicalRepairAlternativesResult = when (val listed = review.list(
        request.candidates.copy(candidateIds = (request.generatedCandidateIds + listOfNotNull(request.baselineCandidateId)).toSet()),
    )) {
        is MidiCoreCandidateReviewResult.Listed -> {
            val byId = listed.candidates.associateBy { it.candidate.id }
            val missing = (request.generatedCandidateIds + listOfNotNull(request.baselineCandidateId)).filterNot(byId::containsKey)
            val wrongGenerator = request.generatedCandidateIds.filter { candidateId ->
                byId[candidateId]?.candidate?.generatorVersion?.startsWith("musical-repair-v${MidiCoreMusicalRepairSettings.VERSION}-") != true
            }
            if (missing.isNotEmpty() || wrongGenerator.isNotEmpty()) {
                MidiCoreMusicalRepairAlternativesResult.Rejected(
                    MidiCoreCandidateProblem(
                        MidiCoreCandidateProblemCode.INVALID_STATE,
                        "Repair candidate evidence changed or does not belong to this repair policy before alternatives could be ranked.",
                        "Reload the project and retry this same repair scope.",
                    ),
                )
            } else MidiCoreMusicalRepairAlternativesResult.Ranked(
                listed.session,
                listed.revision,
                MidiCoreMusicalRepairAlternativeRanker.rank(
                    request.generatedCandidateIds.map(byId::getValue),
                    request.intent,
                    request.baselineCandidateId?.let(byId::getValue),
                    listed.session.project.sourceMidi?.ppq?.toLong() ?: 480L,
                    request.attemptedCount,
                    request.generationProblems,
                    request.allowBaselineReuse,
                ),
            )
        }
        is MidiCoreCandidateReviewResult.Rejected -> MidiCoreMusicalRepairAlternativesResult.Rejected(listed.problem)
        is MidiCoreCandidateReviewResult.Compared -> error("Candidate listing cannot return comparison evidence")
    }
}

sealed interface MidiCoreMusicalRepairAlternativesResult {
    data class Ranked(
        val session: MidiCoreProjectSession,
        val revision: Long,
        val ranking: MidiCoreMusicalRepairAlternativeRanking,
    ) : MidiCoreMusicalRepairAlternativesResult

    data class Rejected(val problem: MidiCoreCandidateProblem) : MidiCoreMusicalRepairAlternativesResult
}

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
