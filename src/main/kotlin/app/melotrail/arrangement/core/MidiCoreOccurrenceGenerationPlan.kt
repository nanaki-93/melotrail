package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementPlan
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreBoundaryIntent
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCoreRolePlanSettings
import app.melotrail.project.MidiCoreSharedGrooveIntent
import app.melotrail.project.ProjectSectionOccurrence

/** Role-scoped intent copied from one occurrence without exposing unrelated role settings. */
data class MidiCoreResolvedPlanOccurrence(
    val occurrenceId: String,
    val purpose: MidiCoreArrangementPurpose,
    val phraseGroupId: String,
    val repeatFamilyId: String,
    val repeatOrdinal: Int,
    val energy: Int,
    val roleSettings: MidiCoreRolePlanSettings,
    val entryIntent: MidiCoreBoundaryIntent,
    val exitIntent: MidiCoreBoundaryIntent,
) {
    init {
        require(roleSettings.density in 0..100 && energy in 0..100 && repeatOrdinal > 0) {
            "Resolved occurrence plan values are invalid"
        }
    }
}

/**
 * The bounded, immutable arrangement inputs for one role occurrence.
 *
 * Neighbor and repeat data are intent summaries, not mutable accepted MIDI.
 * Chords' actual preceding-voicing evidence remains the separate explicit
 * piano-boundary input; Bass and Drums retain their same-occurrence upstream
 * candidate dependencies.
 */
data class MidiCoreResolvedOccurrenceGenerationPlan(
    /** Shared rhythmic intent is consumed by Bass and Drums, never by Chords. */
    val sharedGroove: MidiCoreSharedGrooveIntent?,
    val current: MidiCoreResolvedPlanOccurrence,
    val previousNeighbor: MidiCoreResolvedPlanOccurrence?,
    val nextNeighbor: MidiCoreResolvedPlanOccurrence?,
    val repeatSource: MidiCoreResolvedPlanOccurrence?,
    val fingerprintMaterial: String,
) {
    init {
        require(fingerprintMaterial.isNotBlank()) { "Resolved generation-plan fingerprint material is required" }
        val role = current.roleSettings.role
        require((role == CandidateRole.CHORDS) == (sharedGroove == null)) {
            "Only Bass and Drums generation may consume shared groove intent"
        }
        require(listOfNotNull(previousNeighbor, nextNeighbor, repeatSource).all { it.roleSettings.role == role }) {
            "Resolved generation-plan summaries must be scoped to one role"
        }
        require(repeatSource == null || (
            repeatSource.repeatFamilyId == current.repeatFamilyId &&
                repeatSource.repeatOrdinal < current.repeatOrdinal
            )) { "Resolved repeat input must be an earlier member of the current repeat family" }
    }

    companion object {
        fun resolve(
            plan: MidiCoreArrangementPlan,
            authoritativeOccurrences: List<ProjectSectionOccurrence>,
            occurrenceId: String,
            role: CandidateRole,
        ): MidiCoreResolvedOccurrenceGenerationPlan {
            require(plan.occurrences.map(MidiCoreOccurrenceArrangementPlan::occurrenceId) ==
                authoritativeOccurrences.map(ProjectSectionOccurrence::id)) {
                "Arrangement plan must match authoritative occurrence order before generation"
            }
            val index = plan.occurrences.indexOfFirst { it.occurrenceId == occurrenceId }
            require(index >= 0) { "Arrangement plan has no occurrence '$occurrenceId'" }
            val current = plan.occurrences[index]
            val repeatSource = plan.occurrences.take(index)
                .filter { it.repeatFamilyId == current.repeatFamilyId && it.repeatOrdinal < current.repeatOrdinal }
                .maxByOrNull(MidiCoreOccurrenceArrangementPlan::repeatOrdinal)
            val resolvedCurrent = current.forRole(role)
            return MidiCoreResolvedOccurrenceGenerationPlan(
                sharedGroove = plan.sharedGroove.takeUnless { role == CandidateRole.CHORDS },
                current = resolvedCurrent,
                previousNeighbor = plan.occurrences.getOrNull(index - 1)?.forRole(role),
                nextNeighbor = plan.occurrences.getOrNull(index + 1)?.forRole(role),
                repeatSource = repeatSource?.forRole(role),
                fingerprintMaterial = plan.resolvedScopeCanonicalSerialization(occurrenceId, role),
            )
        }

        private fun MidiCoreOccurrenceArrangementPlan.forRole(role: CandidateRole) = MidiCoreResolvedPlanOccurrence(
            occurrenceId,
            purpose,
            phraseGroupId,
            repeatFamilyId,
            repeatOrdinal,
            energy,
            roleSettings.single { it.role == role },
            entryIntent,
            exitIntent,
        )
    }
}
