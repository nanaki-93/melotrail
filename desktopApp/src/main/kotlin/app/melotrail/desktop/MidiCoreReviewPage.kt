package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import app.melotrail.application.MidiCoreCandidateDiff
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus

/** Stable semantic anchors for the focused, evidence-led Review destination. */
internal object MidiCoreReviewPageTags {
    const val ROOT = "midi-core-review-page"
    const val EMPTY = "midi-core-review-empty"
    const val SCOPE = "midi-core-review-scope"
    const val ROLE_PREFIX = "midi-core-review-role-"
    const val OCCURRENCE_PREFIX = "midi-core-review-occurrence-"
    const val REFRESH = "midi-core-review-refresh"
    const val CANDIDATES = "midi-core-review-candidates"
    const val CANDIDATE_PREFIX = "midi-core-review-candidate-"
    const val SELECT_PREFIX = "midi-core-review-select-"
    const val COMPARE = "midi-core-review-compare"
    const val DIFF = "midi-core-review-diff"
    const val ACCEPT_PREFIX = "midi-core-review-accept-"
    const val REJECT_PREFIX = "midi-core-review-reject-"
    const val LOCK_PREFIX = "midi-core-review-lock-"
    const val UNLOCK_PREFIX = "midi-core-review-unlock-"
    const val RESTORE_PREFIX = "midi-core-review-restore-"
    const val PLAY_CANDIDATE_PREFIX = "midi-core-review-play-candidate-"
    const val ARRANGEMENT = "midi-core-review-arrangement"
    const val PLAY_ARRANGEMENT = "midi-core-review-play-arrangement"
    const val PLAY_ROLE_PREFIX = "midi-core-review-play-role-"
    const val PLAY_OCCURRENCE_PREFIX = "midi-core-review-play-occurrence-"
    const val TRANSPORT = "midi-core-review-transport"
    const val PAUSE = "midi-core-review-pause"
    const val STOP = "midi-core-review-stop"
    const val SEEK_START = "midi-core-review-seek-start"
    const val LOOP = "midi-core-review-loop"
    const val CLEAR_LOOP = "midi-core-review-clear-loop"
    const val MUTE_PREFIX = "midi-core-review-mute-"
    const val SOLO_PREFIX = "midi-core-review-solo-"
    const val ARRANGE = "midi-core-review-open-arrange"
    const val BLOCKERS = "midi-core-review-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun occurrence(id: String): String = OCCURRENCE_PREFIX + id
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
    fun select(id: String): String = SELECT_PREFIX + id
    fun accept(id: String): String = ACCEPT_PREFIX + id
    fun reject(id: String): String = REJECT_PREFIX + id
    fun lock(id: String): String = LOCK_PREFIX + id
    fun unlock(id: String): String = UNLOCK_PREFIX + id
    fun restore(id: String): String = RESTORE_PREFIX + id
    fun playCandidate(id: String): String = PLAY_CANDIDATE_PREFIX + id
    fun playRole(role: MidiExportRole): String = PLAY_ROLE_PREFIX + role.name.lowercase()
    fun playOccurrence(id: String): String = PLAY_OCCURRENCE_PREFIX + id
    fun mute(role: MidiExportRole): String = MUTE_PREFIX + role.name.lowercase()
    fun solo(role: MidiExportRole): String = SOLO_PREFIX + role.name.lowercase()
}

/**
 * Compare immutable alternatives, make the explicit acceptance decision, and
 * control non-authoritative MIDI audition from a single focused view.
 */
@Composable
internal fun MidiCoreReviewPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val authority = state.project?.authority
    val occurrences = authority?.occurrences.orEmpty()
    var role by remember(state.projectRevision) { mutableStateOf(state.review.role ?: CandidateRole.CHORDS) }
    var occurrenceId by remember(state.projectRevision) { mutableStateOf(state.review.occurrenceId ?: occurrences.firstOrNull()?.id) }
    var comparisonSelection by remember(state.projectRevision, role, occurrenceId) { mutableStateOf(emptySet<String>()) }
    val scopeMatches = state.review.role == role && state.review.occurrenceId == occurrenceId
    val candidates = if (scopeMatches) state.review.candidates else emptyList()

    Column(
        modifier.semantics {
            testTag = MidiCoreReviewPageTags.ROOT
            contentDescription = "Review immutable MIDI candidate evidence and audition"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        if (state.project == null || authority == null || occurrences.isEmpty()) {
            ReviewEmptyState(state)
            return@Column
        }
        ReviewScopeCard(
            role = role,
            occurrenceId = occurrenceId,
            occurrences = occurrences.map { it.id to it.label },
            enabled = !state.busy,
            onRole = { nextRole ->
                role = nextRole
                comparisonSelection = emptySet()
                val scope = occurrenceId ?: occurrences.first().id
                onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(nextRole, scope))
                onIntent(MidiCoreWorkspaceIntent.LoadCandidates(nextRole, scope))
            },
            onOccurrence = { nextOccurrence ->
                occurrenceId = nextOccurrence
                comparisonSelection = emptySet()
                onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, nextOccurrence))
                onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, nextOccurrence))
            },
            onRefresh = { onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, requireNotNull(occurrenceId))) },
        )
        ReviewCandidateEvidenceCard(
            state = state,
            role = role,
            occurrenceId = occurrenceId,
            scopeMatches = scopeMatches,
            candidates = candidates,
            comparisonSelection = comparisonSelection,
            onComparisonToggle = { candidateId ->
                comparisonSelection = if (candidateId in comparisonSelection) comparisonSelection - candidateId
                else (comparisonSelection + candidateId).take(2).toSet()
            },
            onCompare = {
                val selected = comparisonSelection.toList().sorted()
                onIntent(MidiCoreWorkspaceIntent.CompareCandidates(selected[0], selected[1]))
            },
            onIntent = onIntent,
        )
        state.review.comparison?.let { comparison -> ReviewDiffCard(comparison) }
        ReviewArrangementCard(state, authority.occurrences.map { it.id to it.label }, onIntent)
        ReviewTransportCard(state, onIntent)
        OutlinedButton(
            onClick = {
                onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, requireNotNull(occurrenceId)))
                onNavigate(MidiCoreWorkspaceDestination.ARRANGE)
            },
            enabled = !state.busy && occurrenceId != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.ARRANGE
                contentDescription = "Return to Arrange for this exact role and occurrence"
            },
        ) { Text("Return to Arrange") }
    }
}

@Composable
private fun ReviewEmptyState(state: MidiCoreWorkspaceState) {
    ReviewCard(MidiCoreReviewPageTags.EMPTY, "Review") {
        Text("Candidate decisions become available after a project has complete authority and at least one saved section occurrence.", style = MaterialTheme.typography.bodyLarge)
        if (state.blockers.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreReviewPageTags.BLOCKERS
                    contentDescription = "${state.blockers.size} Review blocker${if (state.blockers.size == 1) "" else "s"}"
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                state.blockers.forEach { blocker ->
                    Text("${blocker.message} Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Warning)
                }
            }
        }
    }
}

@Composable
private fun ReviewScopeCard(
    role: CandidateRole,
    occurrenceId: String?,
    occurrences: List<Pair<String, String>>,
    enabled: Boolean,
    onRole: (CandidateRole) -> Unit,
    onOccurrence: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.SCOPE, "Review scope") {
        Text("Compare alternatives and make an explicit decision for one role in one exact occurrence. Evidence is never replaced by review.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            CandidateRole.entries.forEach { option ->
                OutlinedButton(
                    onClick = { onRole(option) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.role(option)
                        selected = option == role
                        contentDescription = "Select ${option.reviewDisplayName} Review role${if (option == role) ". Selected." else ""}"
                    },
                ) { Text(option.reviewDisplayName) }
            }
        }
        occurrences.forEach { (id, label) ->
            OutlinedButton(
                onClick = { onOccurrence(id) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.occurrence(id)
                    selected = id == occurrenceId
                    contentDescription = "Select Review occurrence $label${if (id == occurrenceId) ". Selected." else ""}"
                },
            ) { Text(label) }
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = enabled && occurrenceId != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.REFRESH
                contentDescription = "Refresh candidate evidence for the selected Review scope"
            },
        ) { Text("Refresh candidate evidence") }
    }
}

@Composable
private fun ReviewCandidateEvidenceCard(
    state: MidiCoreWorkspaceState,
    role: CandidateRole,
    occurrenceId: String?,
    scopeMatches: Boolean,
    candidates: List<MidiCoreCandidateReviewItem>,
    comparisonSelection: Set<String>,
    onComparisonToggle: (String) -> Unit,
    onCompare: () -> Unit,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.CANDIDATES, "Candidate evidence") {
        Text("Each item includes its immutable identity, seed, profile, pattern, validation findings, authority state, and lifecycle state.", style = MaterialTheme.typography.bodyMedium)
        when {
            !scopeMatches -> Text("Refresh the selected scope to load its current evidence.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            candidates.isEmpty() -> Text("No candidate evidence has been published for this scope yet. Return to Arrange to generate an alternative.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            else -> candidates.forEach { item ->
                ReviewCandidateCard(
                    item = item,
                    selectedForComparison = item.candidate.id in comparisonSelection,
                    enabled = !state.busy && occurrenceId != null,
                    onComparisonToggle = { onComparisonToggle(item.candidate.id) },
                    onIntent = onIntent,
                )
            }
        }
        if (comparisonSelection.size == 2) {
            Button(
                onClick = onCompare,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.COMPARE
                    contentDescription = "Compare the two selected candidate event streams"
                },
            ) { Text("Compare selected alternatives") }
        } else {
            Text("Select two candidates to calculate the deterministic event-level difference.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
    }
}

@Composable
private fun ReviewCandidateCard(
    item: MidiCoreCandidateReviewItem,
    selectedForComparison: Boolean,
    enabled: Boolean,
    onComparisonToggle: () -> Unit,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val candidate = item.candidate
    val report = item.validation
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreReviewPageTags.candidate(candidate.id)
            contentDescription = "Candidate ${candidate.id}: ${candidate.status.name.lowercase()}, ${report.noteCount} notes, ${report.blockers.size} blocking findings, ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} advisory findings${if (!item.authorityCurrent) ". Stale for current authority." else ""}${if (item.locked) ". Locked." else ""}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
        ) {
            Text(candidate.id, style = MaterialTheme.typography.titleSmall)
            Text("${candidate.status.reviewLabel} · seed ${candidate.seed} · ${candidate.profileId} · ${candidate.patternId}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            Text("Validation: ${report.noteCount} notes · ${report.blockers.size} blocking · ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} advisory", style = MaterialTheme.typography.bodySmall)
            if (!item.authorityCurrent) Text("Stale for current authority; it remains inspectable but cannot become current or exportable.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            candidate.rejectionReason?.let { Text("Rejection reason: $it", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary) }
            report.findings.forEach { finding ->
                Text(finding.message, style = MaterialTheme.typography.bodySmall, color = if (finding.severity == MidiCoreRoleFindingSeverity.BLOCKING) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.TextSecondary)
            }
            OutlinedButton(
                onClick = onComparisonToggle,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.select(candidate.id)
                    selected = selectedForComparison
                    contentDescription = "${if (selectedForComparison) "Remove" else "Select"} candidate ${candidate.id} for comparison"
                },
            ) { Text(if (selectedForComparison) "Remove from comparison" else "Select for comparison") }
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlayCandidate(candidate.id, candidate.role, candidate.occurrenceId)) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.playCandidate(candidate.id)
                    contentDescription = "Audition candidate ${candidate.id} through local MIDI output"
                },
            ) { Text("Audition candidate") }
            if (!item.accepted && item.authorityCurrent && candidate.status != MidiCoreCandidateStatus.STALE) {
                Button(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.AcceptCandidate(candidate.id)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.accept(candidate.id)
                        contentDescription = "Accept candidate ${candidate.id} for this scope"
                    },
                ) { Text("Accept candidate") }
            }
            if (!item.accepted && candidate.status != MidiCoreCandidateStatus.REJECTED && candidate.status != MidiCoreCandidateStatus.STALE) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.RejectCandidate(candidate.id, "Not selected in Review.")) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.reject(candidate.id)
                        contentDescription = "Reject candidate ${candidate.id} while preserving its evidence"
                    },
                ) { Text("Reject candidate") }
            }
            if (item.accepted && !item.locked) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.LockCandidate(candidate.id)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.lock(candidate.id)
                        contentDescription = "Lock accepted candidate ${candidate.id}"
                    },
                ) { Text("Lock accepted work") }
            }
            if (item.accepted && item.locked) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.UnlockCandidate(candidate.id)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.unlock(candidate.id)
                        contentDescription = "Unlock accepted candidate ${candidate.id}"
                    },
                ) { Text("Unlock accepted work") }
            }
            if (!item.accepted && item.authorityCurrent && candidate.status != MidiCoreCandidateStatus.STALE) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.RestoreCandidate(candidate.id, candidate.role, candidate.occurrenceId)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.restore(candidate.id)
                        contentDescription = "Restore candidate ${candidate.id} when it has a prior accepted reference"
                    },
                ) { Text("Restore prior acceptance") }
            }
        }
    }
}

@Composable
private fun ReviewDiffCard(comparison: MidiCoreCandidateComparison) {
    val summary = MidiCoreCandidateDiff.summary(comparison.differences)
    ReviewCard(MidiCoreReviewPageTags.DIFF, "Semantic difference") {
        Text("${comparison.first.candidate.id} → ${comparison.second.candidate.id}: ${summary.additions} added, ${summary.removals} removed, ${summary.changes} changed MIDI note events.", style = MaterialTheme.typography.bodyMedium)
        comparison.differences.forEach { difference ->
            Text(difference.kind.name.lowercase().replaceFirstChar(Char::uppercaseChar), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
    }
}

@Composable
private fun ReviewArrangementCard(
    state: MidiCoreWorkspaceState,
    occurrences: List<Pair<String, String>>,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.ARRANGEMENT, "Current arrangement") {
        Text("The accepted arrangement is assembled from the protected melody and current accepted candidates. Auditioning it never writes MIDI or alters an approval.", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAcceptedArrangement) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.PLAY_ARRANGEMENT
                contentDescription = "Audition the current accepted arrangement"
            },
        ) { Text("Audition accepted arrangement") }
        CandidateRole.entries.forEach { role ->
            val exportRole = role.exportRole
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAcceptedRole(role)) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.playRole(exportRole)
                    contentDescription = "Audition accepted ${role.reviewDisplayName} role"
                },
            ) { Text("Audition accepted ${role.reviewDisplayName}") }
        }
        occurrences.forEach { (id, label) ->
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAcceptedOccurrence(id)) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.playOccurrence(id)
                    contentDescription = "Audition accepted arrangement occurrence $label"
                },
            ) { Text("Audition $label") }
        }
    }
}

@Composable
private fun ReviewTransportCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    val audition = state.audition
    ReviewCard(MidiCoreReviewPageTags.TRANSPORT, "MIDI transport") {
        Text("Preview timbre is local MIDI playback only; it is not exported audio.", style = MaterialTheme.typography.bodyMedium)
        Text("${audition.playback.name.lowercase().replaceFirstChar(Char::uppercaseChar)} · tick ${audition.positionTick}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        if (audition.playback == MidiAuditionPlaybackState.PLAYING) {
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PauseAudition) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.PAUSE
                    contentDescription = "Pause MIDI audition"
                },
            ) { Text("Pause") }
        }
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.StopAudition) },
            enabled = audition.scope != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.STOP
                contentDescription = "Stop MIDI audition"
            },
        ) { Text("Stop") }
        audition.window?.let { window ->
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.SeekAudition(window.startTick)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.SEEK_START
                    contentDescription = "Seek MIDI audition to the selected view start"
                },
            ) { Text("Seek to start") }
            if (audition.loop == null) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.SetAuditionLoop(MidiAuditionLoop(window.startTick, window.endTick))) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.LOOP
                        contentDescription = "Loop the selected MIDI audition view"
                    },
                ) { Text("Loop selected view") }
            } else {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.SetAuditionLoop(null)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.CLEAR_LOOP
                        contentDescription = "Disable MIDI audition loop"
                    },
                ) { Text("Disable loop") }
            }
        }
        audition.scope?.let { scope ->
            reviewAuditionRoles(scope).forEach { role ->
                val muted = role in audition.mutedRoles
                val solo = role in audition.soloRoles
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.MuteAuditionRole(role, !muted)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.mute(role)
                        selected = muted
                        contentDescription = "${if (muted) "Unmute" else "Mute"} ${role.trackName} during MIDI audition"
                    },
                ) { Text(if (muted) "Unmute ${role.trackName}" else "Mute ${role.trackName}") }
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.SoloAuditionRole(role, !solo)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.solo(role)
                        selected = solo
                        contentDescription = "${if (solo) "Stop soloing" else "Solo"} ${role.trackName} during MIDI audition"
                    },
                ) { Text(if (solo) "Stop soloing ${role.trackName}" else "Solo ${role.trackName}") }
            }
        }
        audition.lastProblem?.let { problem -> Text("${problem.message} Next: ${problem.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning) }
    }
}

@Composable
private fun ReviewCard(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private val CandidateRole.reviewDisplayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)

private val CandidateRole.exportRole: MidiExportRole
    get() = when (this) {
        CandidateRole.CHORDS -> MidiExportRole.CHORDS
        CandidateRole.BASS -> MidiExportRole.BASS
        CandidateRole.DRUMS -> MidiExportRole.DRUMS
    }

private val MidiCoreCandidateStatus.reviewLabel: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)

private fun reviewAuditionRoles(scope: MidiAuditionScope): List<MidiExportRole> = when (scope) {
    MidiAuditionScope.SourceMelody -> listOf(MidiExportRole.MELODY)
    is MidiAuditionScope.Candidate -> listOf(scope.role)
    is MidiAuditionScope.Role -> listOf(scope.role)
    is MidiAuditionScope.Occurrence,
    MidiAuditionScope.AcceptedArrangement,
    -> MidiExportRole.entries
}
