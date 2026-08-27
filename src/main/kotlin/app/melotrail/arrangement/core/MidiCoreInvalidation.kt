package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityDimension
import app.melotrail.project.MidiCoreAuthorityFingerprint
import app.melotrail.project.MidiCoreAuthorityScopeFingerprint
import app.melotrail.project.MidiCoreAuthorityScopeKey
import app.melotrail.project.MidiCoreGenerationFingerprint

/** Why a previously current derived result is no longer current. */
enum class MidiCoreInvalidationReason {
    SOURCE_CHANGED,
    MELODY_CHANGED,
    TIMING_CHANGED,
    STRUCTURE_CHANGED,
    HARMONY_CHANGED,
    SETTINGS_CHANGED,
    ACCEPTED_DEPENDENCY_CHANGED,
}

/** Minimal candidate dependency information needed for a pure invalidation preview. */
data class MidiCoreCandidateDependency(
    val id: String,
    val role: CandidateRole,
    val occurrenceId: String,
    val authorityHash: String,
    val acceptedDependencyIds: List<String> = emptyList(),
) {
    init {
        require(id.matches(SAFE_ID) && occurrenceId.matches(SAFE_ID)) { "Candidate dependency identity is invalid" }
        require(authorityHash.matches(HASH)) { "Candidate dependency authority hash is invalid" }
        require(acceptedDependencyIds == acceptedDependencyIds.distinct()) { "Accepted dependency IDs must be unique" }
        require(acceptedDependencyIds.all { it.matches(SAFE_ID) }) { "Accepted dependency IDs are invalid" }
    }

    val scope: MidiCoreAuthorityScopeKey get() = MidiCoreAuthorityScopeKey(occurrenceId, role)
}

/** Minimal immutable export binding used to preview whole-song staleness. */
data class MidiCoreExportDependency(val id: String, val authorityHash: String) {
    init {
        require(id.matches(SAFE_ID)) { "Export dependency identity is invalid" }
        require(authorityHash.matches(HASH)) { "Export dependency authority hash is invalid" }
    }
}

enum class MidiCoreDerivedWorkKind { CANDIDATE, EXPORT }

/** UI-ready explanation of one result that will no longer be current. */
data class MidiCoreInvalidationTarget(
    val kind: MidiCoreDerivedWorkKind,
    val id: String,
    val role: CandidateRole? = null,
    val occurrenceId: String? = null,
    val reasons: List<MidiCoreInvalidationReason>,
) {
    init {
        require(id.matches(SAFE_ID)) { "Invalidation target identity is invalid" }
        require((kind == MidiCoreDerivedWorkKind.CANDIDATE) == (role != null && occurrenceId != null)) {
            "Candidate invalidation targets require a role and occurrence"
        }
        require(reasons.isNotEmpty() && reasons == reasons.distinct().sortedBy(MidiCoreInvalidationReason::ordinal)) {
            "Invalidation reasons must be stable and non-empty"
        }
    }
}

/** Complete pure preview calculated before an authority mutation is published. */
data class MidiCoreInvalidationPreview(
    val before: MidiCoreAuthorityFingerprint,
    val after: MidiCoreAuthorityFingerprint,
    val changedDimensions: List<MidiCoreAuthorityDimension>,
    val affectedScopes: List<MidiCoreAuthorityScopeKey>,
    val staleTargets: List<MidiCoreInvalidationTarget>,
) {
    val hasImpact: Boolean get() = changedDimensions.isNotEmpty()
    val staleCandidateIds: List<String> get() = staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.CANDIDATE }.map { it.id }
    val staleExportIds: List<String> get() = staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.EXPORT }.map { it.id }

    fun affects(role: CandidateRole, occurrenceId: String): Boolean =
        affectedScopes.contains(MidiCoreAuthorityScopeKey(occurrenceId, role))
}

/** Computes dimension- and dependency-scoped staleness without deleting any artifact. */
object MidiCoreInvalidationPlanner {
    fun preview(
        before: MidiCoreAuthorityFingerprint,
        after: MidiCoreAuthorityFingerprint,
        candidates: List<MidiCoreCandidateDependency> = emptyList(),
        exports: List<MidiCoreExportDependency> = emptyList(),
    ): MidiCoreInvalidationPreview {
        val changedDimensions = listOf(
            MidiCoreAuthorityDimension.SOURCE to (before.sourceSha256 != after.sourceSha256),
            MidiCoreAuthorityDimension.MELODY to (before.melodySha256 != after.melodySha256),
            MidiCoreAuthorityDimension.TIMING to (before.timingSha256 != after.timingSha256),
            MidiCoreAuthorityDimension.STRUCTURE to (before.structureSha256 != after.structureSha256),
            MidiCoreAuthorityDimension.HARMONY to (before.harmonySha256 != after.harmonySha256),
            MidiCoreAuthorityDimension.SETTINGS to (before.settingsSha256 != after.settingsSha256),
        ).filter { it.second }.map { it.first }
        val beforeScopes = before.scopes.associateBy(MidiCoreAuthorityScopeFingerprint::key)
        val afterScopes = after.scopes.associateBy(MidiCoreAuthorityScopeFingerprint::key)
        val allScopeKeys = (beforeScopes.keys + afterScopes.keys).distinct().sortedWith(scopeKeyOrder())
        val affectedScopes = allScopeKeys.filter { key -> beforeScopes[key]?.sha256 != afterScopes[key]?.sha256 }
        require(candidates.groupBy(MidiCoreCandidateDependency::id).values.all { it.size == 1 }) {
            "Candidate dependency IDs must be unique"
        }
        val targetReasons = linkedMapOf<String, MutableSet<MidiCoreInvalidationReason>>()
        val targetCandidates = linkedMapOf<String, MidiCoreCandidateDependency>()
        candidates.sortedBy(MidiCoreCandidateDependency::id).forEach { candidate ->
            val beforeScope = beforeScopes[candidate.scope]
            val afterScope = afterScopes[candidate.scope]
            val wasCurrent = beforeScope?.sha256 == candidate.authorityHash
            val remainsCurrent = afterScope?.sha256 == candidate.authorityHash
            if (before.sha256 != after.sha256 && wasCurrent && !remainsCurrent) {
                targetCandidates[candidate.id] = candidate
                targetReasons.getOrPut(candidate.id) { linkedSetOf() }.addAll(scopeReasons(before, after, beforeScope, afterScope))
            }
        }
        var dependencyChanged = true
        while (dependencyChanged) {
            dependencyChanged = false
            candidates.sortedBy(MidiCoreCandidateDependency::id).forEach { candidate ->
                if (candidate.id in targetCandidates) return@forEach
                if (candidate.acceptedDependencyIds.any { it in targetCandidates }) {
                    targetCandidates[candidate.id] = candidate
                    targetReasons.getOrPut(candidate.id) { linkedSetOf() }.add(MidiCoreInvalidationReason.ACCEPTED_DEPENDENCY_CHANGED)
                    dependencyChanged = true
                }
            }
        }
        val targetList = targetCandidates.values.sortedBy(MidiCoreCandidateDependency::id).map { candidate ->
            MidiCoreInvalidationTarget(
                MidiCoreDerivedWorkKind.CANDIDATE,
                candidate.id,
                candidate.role,
                candidate.occurrenceId,
                targetReasons.getValue(candidate.id).toList().sortedBy(MidiCoreInvalidationReason::ordinal),
            )
        }.toMutableList()
        if (before.sha256 != after.sha256) {
            exports.sortedBy(MidiCoreExportDependency::id).forEach { export ->
                if (export.authorityHash == before.sha256 && export.authorityHash != after.sha256) {
                    targetList += MidiCoreInvalidationTarget(
                        MidiCoreDerivedWorkKind.EXPORT,
                        export.id,
                        reasons = changedDimensions.map(::reasonFor),
                    )
                }
            }
        }
        return MidiCoreInvalidationPreview(
            before,
            after,
            changedDimensions,
            affectedScopes,
            targetList.sortedWith(compareBy<MidiCoreInvalidationTarget> { it.kind.ordinal }.thenBy { it.id }),
        )
    }

    private fun scopeReasons(
        before: MidiCoreAuthorityFingerprint,
        after: MidiCoreAuthorityFingerprint,
        beforeScope: MidiCoreAuthorityScopeFingerprint?,
        afterScope: MidiCoreAuthorityScopeFingerprint?,
    ): List<MidiCoreInvalidationReason> = buildList {
        if (before.sourceSha256 != after.sourceSha256) add(MidiCoreInvalidationReason.SOURCE_CHANGED)
        if (before.melodySha256 != after.melodySha256) add(MidiCoreInvalidationReason.MELODY_CHANGED)
        if (before.timingSha256 != after.timingSha256) add(MidiCoreInvalidationReason.TIMING_CHANGED)
        if (beforeScope == null || afterScope == null) {
            if (before.settingsSha256 != after.settingsSha256) add(MidiCoreInvalidationReason.SETTINGS_CHANGED)
        } else if (beforeScope.settingsSha256 != afterScope.settingsSha256) {
            add(MidiCoreInvalidationReason.SETTINGS_CHANGED)
        }
        if (beforeScope == null || afterScope == null || beforeScope.structureSha256 != afterScope.structureSha256) {
            add(MidiCoreInvalidationReason.STRUCTURE_CHANGED)
        }
        if (beforeScope != null && afterScope != null && beforeScope.harmonySha256 != afterScope.harmonySha256) {
            add(MidiCoreInvalidationReason.HARMONY_CHANGED)
        }
    }.ifEmpty { listOf(MidiCoreInvalidationReason.HARMONY_CHANGED) }

    private fun reasonFor(dimension: MidiCoreAuthorityDimension): MidiCoreInvalidationReason = when (dimension) {
        MidiCoreAuthorityDimension.SOURCE -> MidiCoreInvalidationReason.SOURCE_CHANGED
        MidiCoreAuthorityDimension.MELODY -> MidiCoreInvalidationReason.MELODY_CHANGED
        MidiCoreAuthorityDimension.TIMING -> MidiCoreInvalidationReason.TIMING_CHANGED
        MidiCoreAuthorityDimension.STRUCTURE -> MidiCoreInvalidationReason.STRUCTURE_CHANGED
        MidiCoreAuthorityDimension.HARMONY -> MidiCoreInvalidationReason.HARMONY_CHANGED
        MidiCoreAuthorityDimension.SETTINGS -> MidiCoreInvalidationReason.SETTINGS_CHANGED
    }
}

/** Result of admitting an asynchronous generation completion against current authority. */
sealed interface MidiCoreGenerationAdmissionResult {
    data class Accepted(val fingerprint: MidiCoreGenerationFingerprint) : MidiCoreGenerationAdmissionResult
    data class Rejected(val problem: MidiCoreStaleGenerationProblem) : MidiCoreGenerationAdmissionResult
}

data class MidiCoreStaleGenerationProblem(
    val expectedGenerationSha256: String,
    val currentGenerationSha256: String,
    val expectedAuthorityHash: String,
    val currentAuthorityHash: String,
) {
    val message: String get() = "Generation completed against an older authority or dependency snapshot."
}

/** Admission boundary for off-UI-thread generation results. */
object MidiCoreGenerationAdmission {
    fun admit(
        expected: MidiCoreGenerationFingerprint,
        current: MidiCoreGenerationFingerprint,
    ): MidiCoreGenerationAdmissionResult = if (expected.sha256 == current.sha256) {
        MidiCoreGenerationAdmissionResult.Accepted(current)
    } else {
        MidiCoreGenerationAdmissionResult.Rejected(
            MidiCoreStaleGenerationProblem(
                expected.sha256,
                current.sha256,
                expected.authorityHash,
                current.authorityHash,
            ),
        )
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
private val HASH = Regex("[0-9a-f]{64}")

private fun scopeKeyOrder() = compareBy<MidiCoreAuthorityScopeKey> { it.occurrenceId }.thenBy { it.role.ordinal }
