package app.melotrail.desktop

import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreExportSnapshot

/** Match the entire current accepted package, including roles represented only by rests. */
internal fun midiCoreExportMatchesAcceptedWork(state: MidiCoreWorkspaceState, snapshot: MidiCoreExportSnapshot): Boolean {
    val project = state.project ?: return false
    if (exportReadiness(state).isNotEmpty() || !snapshot.isCurrent(project)) return false
    val activeRoles = midiCoreAcceptedScopeSummary(project).filterNot { it.rest }.map { it.role }.toSet()
    return snapshot.enabledRoles.toSet() == activeRoles
}

/** Presentation preflight only; publication still verifies files and all dependency evidence. */
internal fun exportReadiness(state: MidiCoreWorkspaceState): List<MidiCoreWorkspaceBlocker> {
    fun required(code: MidiCoreWorkspaceBlockerCode, message: String) = listOf(
        MidiCoreWorkspaceBlocker(code, message, "Complete this step before publishing accepted MIDI."),
    )
    val project = state.project ?: return required(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, "Open a project before exporting.")
    if (project.sourceMidi == null) return required(MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED, "Import the source MIDI before exporting.")
    if (project.selectedMelody == null) return required(MidiCoreWorkspaceBlockerCode.MELODY_REQUIRED, "Protect the source melody before exporting.")
    val authority = project.authority ?: return required(MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED, "Confirm musical settings before exporting.")
    if (authority.occurrences.isEmpty()) return required(MidiCoreWorkspaceBlockerCode.STRUCTURE_REQUIRED, "Define the song sections before exporting.")
    if (runCatching { app.melotrail.structure.MidiCoreHarmonyTimeline.build(authority) }.isFailure) {
        return required(MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED, "Complete the chord durations for every section before exporting.")
    }
    if (runCatching { MidiCoreAuthorityHasher.from(project) }.isFailure) {
        return required(MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED, "The current musical authority cannot be resolved for export.")
    }
    return midiCoreAcceptedScopeSummary(project).mapNotNull { scope -> scope.problem?.let {
        MidiCoreWorkspaceBlocker(
            MidiCoreWorkspaceBlockerCode.EXPORT_NOT_READY, it,
            "Review ${scope.location} and explicitly use current work. Existing accepted files are preserved.",
            occurrenceId = scope.occurrenceId, role = scope.role,
        )
    } }
}

internal data class MidiCoreAcceptedScopeSummary(
    val occurrenceId: String,
    val location: String,
    val role: CandidateRole,
    val rest: Boolean,
    val locked: Boolean,
    val problem: String?,
)

internal fun midiCoreAcceptedScopeSummary(project: app.melotrail.project.MidiCoreProject): List<MidiCoreAcceptedScopeSummary> {
    val fingerprint = runCatching { MidiCoreAuthorityHasher.from(project) }.getOrNull()
    val locations = runCatching { midiCoreSongMap(project) }.getOrDefault(emptyList()).associateBy { it.occurrence.id }
    return project.authority?.occurrences.orEmpty().flatMap { occurrence ->
        CandidateRole.entries.map { role ->
            val rest = project.acceptedPlannedRests.singleOrNull { it.role == role && it.occurrenceId == occurrence.id }
            val acceptance = project.acceptances.singleOrNull { it.role == role && it.occurrenceId == occurrence.id }
            val candidate = project.candidates.singleOrNull { it.id == acceptance?.candidateId }
            val hash = fingerprint?.let { runCatching { it.scopeHash(occurrence.id, role) }.getOrNull() }
            val currentRest = rest != null && rest.authorityHash == hash && project.arrangementPlan?.occurrences
                ?.singleOrNull { it.occurrenceId == occurrence.id }?.roleSettings
                ?.singleOrNull { it.role == role }?.activity == app.melotrail.project.MidiCoreRoleActivity.INACTIVE
            val problem = when {
                rest != null && currentRest -> null
                rest != null -> "The planned ${role.exportDisplayName} rest for ${occurrence.label} is stale. Confirm the plan and use a current complete draft before exporting."
                acceptance == null -> "Accept one current ${role.exportDisplayName} candidate for ${occurrence.label} before exporting."
                candidate == null -> "The accepted ${role.exportDisplayName} evidence for ${occurrence.label} is unavailable. Reload the project and accept a current candidate before exporting."
                candidate.status == MidiCoreCandidateStatus.STALE || candidate.authorityHash != hash -> "The accepted ${role.exportDisplayName} candidate for ${occurrence.label} is stale. Regenerate and explicitly accept a current candidate before exporting."
                candidate.status != MidiCoreCandidateStatus.ACCEPTED -> "The ${role.exportDisplayName} candidate accepted for ${occurrence.label} is no longer accepted. Review and explicitly accept a current candidate before exporting."
                candidate.role != role || candidate.occurrenceId != occurrence.id -> "The accepted ${role.exportDisplayName} candidate does not belong to ${occurrence.label}."
                else -> null
            }
            MidiCoreAcceptedScopeSummary(occurrence.id, locations[occurrence.id]?.let { "${it.displayLabel} · ${it.barRange}" } ?: occurrence.label,
                role, rest != null, rest?.locked == true || acceptance?.locked == true, problem)
        }
    }
}

internal val CandidateRole.exportDisplayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)
