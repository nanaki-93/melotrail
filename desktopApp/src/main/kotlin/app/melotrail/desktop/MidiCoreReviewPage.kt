package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectSectionOccurrence

/** Stable semantic anchors for the guided candidate decision page. */
internal object MidiCoreReviewPageTags {
    const val ROOT = "midi-core-review-page"
    const val EMPTY = "midi-core-review-empty"
    const val PROGRESS = "midi-core-review-progress"
    const val SCOPE = "midi-core-review-scope"
    const val ROLE_PREFIX = "midi-core-review-role-"
    const val OCCURRENCE_MENU = "midi-core-review-occurrence-menu"
    const val OCCURRENCE_PREFIX = "midi-core-review-occurrence-"
    const val CANDIDATES = "midi-core-review-candidates"
    const val CANDIDATE_MENU = "midi-core-review-candidate-menu"
    const val CANDIDATE_PREFIX = "midi-core-review-candidate-"
    const val PLAY_SELECTED = "midi-core-review-play-selected"
    const val ACCEPT_SELECTED = "midi-core-review-accept-selected"
    const val COMPARE_MENU = "midi-core-review-compare-menu"
    const val COMPARE_PREFIX = "midi-core-review-compare-"
    const val DIFF = "midi-core-review-diff"
    const val MORE_ACTIONS = "midi-core-review-more-actions"
    const val REJECT_SELECTED = "midi-core-review-reject-selected"
    const val RESTORE_SELECTED = "midi-core-review-restore-selected"
    const val LOCK_SELECTED = "midi-core-review-lock-selected"
    const val UNLOCK_SELECTED = "midi-core-review-unlock-selected"
    const val NEXT_SCOPE = "midi-core-review-next-scope"
    const val ARRANGEMENT = "midi-core-review-arrangement"
    const val PLAY_ARRANGEMENT = "midi-core-review-play-arrangement"
    const val ARRANGE = "midi-core-review-open-arrange"
    const val EXPORT = "midi-core-review-open-export"
    const val STATUS = "midi-core-review-status"
    const val BLOCKERS = "midi-core-review-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun occurrence(id: String): String = OCCURRENCE_PREFIX + id
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
    fun compare(id: String): String = COMPARE_PREFIX + id
}

/** Listen, decide, and continue through one section-role scope at a time. */
@Composable
internal fun MidiCoreReviewPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = state.project
    val authority = project?.authority
    val occurrences = authority?.occurrences.orEmpty()
    val progress = project?.let(::midiCoreArrangementProgress)
    val requestedOccurrence = state.review.occurrenceId?.takeIf { id -> occurrences.any { it.id == id } }
        ?: progress?.nextIncomplete?.occurrence?.id
        ?: occurrences.firstOrNull()?.id
    val requestedRole = state.review.role ?: progress?.nextIncomplete?.role ?: CandidateRole.CHORDS
    var role by remember(project?.id?.value) { mutableStateOf(requestedRole) }
    var occurrenceId by remember(project?.id?.value) { mutableStateOf(requestedOccurrence) }
    val scopeMatches = state.review.role == role && state.review.occurrenceId == occurrenceId
    val candidates = if (scopeMatches) state.review.candidates else emptyList()
    var selectedCandidateId by remember(project?.id?.value, role, occurrenceId) { mutableStateOf(state.review.selectedCandidateId) }

    LaunchedEffect(project?.id?.value, role, occurrenceId) {
        val selectedOccurrence = occurrenceId ?: return@LaunchedEffect
        if (project != null && authority != null) {
            onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, selectedOccurrence))
            onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, selectedOccurrence))
        }
    }
    LaunchedEffect(candidates, state.review.selectedCandidateId) {
        val preferred = state.review.selectedCandidateId?.takeIf { id -> candidates.any { it.candidate.id == id } }
            ?: selectedCandidateId?.takeIf { id -> candidates.any { it.candidate.id == id } }
            ?: candidates.singleOrNull { it.accepted }?.candidate?.id
            ?: candidates.lastOrNull { it.candidate.status != MidiCoreCandidateStatus.REJECTED }?.candidate?.id
            ?: candidates.lastOrNull()?.candidate?.id
        selectedCandidateId = preferred
    }

    Column(
        modifier.semantics {
            testTag = MidiCoreReviewPageTags.ROOT
            contentDescription = "Listen to one MIDI alternative, decide, then continue"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "LISTEN & CHOOSE",
            title = "Review",
            summary = "Audition one alternative, accept it when it works, then continue to the next unfinished part.",
        )
        if (project == null || authority == null || occurrences.isEmpty()) {
            ReviewEmptyState(state)
            return@Column
        }

        requireNotNull(progress)
        ReviewProgressCard(progress)
        ReviewScopeCard(
            role = role,
            occurrenceId = occurrenceId,
            occurrences = occurrences,
            enabled = !state.busy,
            onRole = { role = it },
            onOccurrence = { occurrenceId = it },
        )
        val selectedItem = candidates.singleOrNull { it.candidate.id == selectedCandidateId }
        ReviewDecisionCard(
            state = state,
            project = project,
            candidates = candidates,
            selected = selectedItem,
            onSelected = { selectedCandidateId = it },
            onIntent = onIntent,
        )
        state.review.comparison?.let { comparison -> ReviewDiffCard(comparison) }
        ReviewContinueCard(
            state = state,
            progress = progress,
            role = role,
            occurrenceId = requireNotNull(occurrenceId),
            selected = selectedItem,
            onIntent = onIntent,
            onNavigate = onNavigate,
        )
        ReviewArrangementCard(state, progress.complete, onIntent)
    }
}

@Composable
private fun ReviewEmptyState(state: MidiCoreWorkspaceState) {
    ReviewCard(MidiCoreReviewPageTags.EMPTY, "Nothing to review yet") {
        Text("Finish the song settings, section list, and chord progressions, then generate an alternative in Arrange.", style = MaterialTheme.typography.bodyLarge)
        if (state.blockers.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreReviewPageTags.BLOCKERS
                    contentDescription = "${state.blockers.size} review blocker${if (state.blockers.size == 1) "" else "s"}"
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
private fun ReviewProgressCard(progress: MidiCoreArrangementProgress) {
    ReviewCard(MidiCoreReviewPageTags.PROGRESS, "Arrangement progress") {
        Text(
            if (progress.complete) "All ${progress.total} parts are accepted."
            else "${progress.accepted} of ${progress.total} parts accepted",
            style = MaterialTheme.typography.titleMedium,
            color = if (progress.complete) MusicWorkspaceTokens.Success else MusicWorkspaceTokens.Information,
        )
        progress.nextIncomplete?.let { next ->
            Text("Next unfinished part: ${next.occurrence.label} · ${next.role.displayName}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
    }
}

@Composable
private fun ReviewScopeCard(
    role: CandidateRole,
    occurrenceId: String?,
    occurrences: List<ProjectSectionOccurrence>,
    enabled: Boolean,
    onRole: (CandidateRole) -> Unit,
    onOccurrence: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val occurrence = occurrences.singleOrNull { it.id == occurrenceId } ?: occurrences.first()
    ReviewCard(MidiCoreReviewPageTags.SCOPE, "1. Choose a part") {
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = enabled,
                colors = workspaceSelectableButtonColors(true),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.OCCURRENCE_MENU
                    contentDescription = "Section ${occurrence.label}; open section choices"
                },
            ) { Text("Section · ${occurrence.label}") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                occurrences.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { menuOpen = false; onOccurrence(option.id) },
                        modifier = Modifier.semantics {
                            testTag = MidiCoreReviewPageTags.occurrence(option.id)
                            selected = option.id == occurrenceId
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            midiCoreArrangementRoleOrder.forEach { option ->
                val isSelected = option == role
                OutlinedButton(
                    onClick = { onRole(option) },
                    enabled = enabled,
                    colors = workspaceSelectableButtonColors(isSelected),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.role(option)
                        selected = isSelected
                        contentDescription = "${option.displayName}${if (isSelected) ", selected" else ""}"
                    },
                ) { Text(option.displayName) }
            }
        }
    }
}

@Composable
private fun ReviewDecisionCard(
    state: MidiCoreWorkspaceState,
    project: MidiCoreProject,
    candidates: List<MidiCoreCandidateReviewItem>,
    selected: MidiCoreCandidateReviewItem?,
    onSelected: (String) -> Unit,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    var candidateMenuOpen by remember { mutableStateOf(false) }
    var compareMenuOpen by remember { mutableStateOf(false) }
    var moreOpen by remember(selected?.candidate?.id) { mutableStateOf(false) }
    ReviewCard(MidiCoreReviewPageTags.CANDIDATES, "2. Listen and decide") {
        when {
            candidates.isEmpty() -> {
                Text("No alternatives are available for this part.", style = MaterialTheme.typography.titleMedium)
                Text("Return to Arrange and generate one to continue.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            }
            else -> {
                val selectedIndex = candidates.indexOf(selected).takeIf { it >= 0 } ?: 0
                Box {
                    OutlinedButton(
                        onClick = { candidateMenuOpen = true },
                        enabled = !state.busy,
                        colors = workspaceSelectableButtonColors(true),
                        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                            testTag = MidiCoreReviewPageTags.CANDIDATE_MENU
                            contentDescription = "Alternative ${selectedIndex + 1} of ${candidates.size}; open alternative choices"
                        },
                    ) { Text("Alternative ${selectedIndex + 1} of ${candidates.size}") }
                    DropdownMenu(expanded = candidateMenuOpen, onDismissRequest = { candidateMenuOpen = false }) {
                        candidates.forEachIndexed { index, item ->
                            DropdownMenuItem(
                                text = { Text("Alternative ${index + 1}${if (item.accepted) " · Accepted" else ""}") },
                                onClick = { candidateMenuOpen = false; onSelected(item.candidate.id) },
                                modifier = Modifier.semantics {
                                    testTag = MidiCoreReviewPageTags.candidate(item.candidate.id)
                                    this.selected = item.candidate.id == selected?.candidate?.id
                                },
                            )
                        }
                    }
                }
                selected?.let { item ->
                    ReviewSelectedCandidate(item)
                    Button(
                        onClick = { onIntent(MidiCoreWorkspaceIntent.PlayCandidate(item.candidate.id, item.candidate.role, item.candidate.occurrenceId)) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                            testTag = MidiCoreReviewPageTags.PLAY_SELECTED
                            contentDescription = "Play the selected MIDI alternative"
                        },
                    ) { Text(if (state.audition.playback == MidiAuditionPlaybackState.PLAYING && state.audition.scope is MidiAuditionScope.Candidate) "Play again" else "Play alternative") }

                    if (!item.accepted && item.authorityCurrent && item.candidate.status != MidiCoreCandidateStatus.STALE && item.candidate.status != MidiCoreCandidateStatus.REJECTED) {
                        Button(
                            onClick = { onIntent(MidiCoreWorkspaceIntent.AcceptCandidate(item.candidate.id)) },
                            enabled = !state.busy && item.validation.blockers.isEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                testTag = MidiCoreReviewPageTags.ACCEPT_SELECTED
                                contentDescription = "Accept the selected alternative for this part"
                            },
                        ) { Text("Accept this alternative") }
                    } else if (item.accepted) {
                        Text("Accepted for this part${if (item.locked) " · Locked" else ""}", style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.Success)
                    }

                    val comparisonOptions = candidates.filter { it.candidate.id != item.candidate.id }
                    if (comparisonOptions.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { compareMenuOpen = true },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                    testTag = MidiCoreReviewPageTags.COMPARE_MENU
                                    contentDescription = "Compare the selected alternative with another"
                                },
                            ) { Text("Compare with…") }
                            DropdownMenu(expanded = compareMenuOpen, onDismissRequest = { compareMenuOpen = false }) {
                                comparisonOptions.forEach { other ->
                                    val index = candidates.indexOf(other) + 1
                                    DropdownMenuItem(
                                        text = { Text("Alternative $index") },
                                        onClick = {
                                            compareMenuOpen = false
                                            onIntent(MidiCoreWorkspaceIntent.CompareCandidates(item.candidate.id, other.candidate.id))
                                        },
                                        modifier = Modifier.semantics { testTag = MidiCoreReviewPageTags.compare(other.candidate.id) },
                                    )
                                }
                            }
                        }
                    }
                    val restorable = project.acceptanceHistory.any { history ->
                        history.candidateId == item.candidate.id && history.action in setOf(
                            MidiCoreAcceptanceAction.ACCEPTED,
                            MidiCoreAcceptanceAction.REPLACED,
                            MidiCoreAcceptanceAction.RESTORED,
                        )
                    }
                    val hasLifecycleActions = item.accepted ||
                        (!item.accepted && item.candidate.status != MidiCoreCandidateStatus.REJECTED && item.candidate.status != MidiCoreCandidateStatus.STALE) ||
                        (!item.accepted && restorable && item.authorityCurrent && item.candidate.status != MidiCoreCandidateStatus.STALE)
                    if (hasLifecycleActions) {
                        OutlinedButton(
                            onClick = { moreOpen = !moreOpen },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                testTag = MidiCoreReviewPageTags.MORE_ACTIONS
                                this.selected = moreOpen
                                contentDescription = "${if (moreOpen) "Hide" else "Show"} less common candidate actions"
                            },
                        ) { Text(if (moreOpen) "Hide more actions" else "More actions") }
                        if (moreOpen) ReviewLifecycleActions(item, restorable, !state.busy, onIntent)
                    }
                }
            }
        }
        if (state.operation.kind == MidiCoreWorkspaceOperationKind.CANDIDATE_REVIEW) {
            Text(
                state.operation.message,
                modifier = Modifier.semantics { testTag = MidiCoreReviewPageTags.STATUS },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.operation.outcome == MidiCoreWorkspaceOperationOutcome.FAILURE) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Success,
            )
        }
    }
}

@Composable
private fun ReviewSelectedCandidate(item: MidiCoreCandidateReviewItem) {
    val candidate = item.candidate
    val report = item.validation
    Card(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "Selected alternative: ${candidate.status.name.lowercase()}, ${report.noteCount} notes, ${report.blockers.size} blocking findings, ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} advisory findings${if (!item.authorityCurrent) ". Needs regeneration." else ""}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text("${friendlyToken(candidate.profileId)} · ${friendlyToken(candidate.patternId)}", style = MaterialTheme.typography.titleSmall)
            Text("Seed ${candidate.seed} · ${report.noteCount} notes", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            if (!item.authorityCurrent || candidate.status == MidiCoreCandidateStatus.STALE) {
                Text("This alternative needs regeneration after a song-setting change.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            }
            if (report.blockers.isNotEmpty()) Text("${report.blockers.size} validation blocker${if (report.blockers.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            report.findings.take(3).forEach { finding ->
                Text(finding.message, style = MaterialTheme.typography.bodySmall, color = if (finding.severity == MidiCoreRoleFindingSeverity.BLOCKING) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.TextSecondary)
            }
        }
    }
}

@Composable
private fun ReviewLifecycleActions(
    item: MidiCoreCandidateReviewItem,
    restorable: Boolean,
    enabled: Boolean,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val candidate = item.candidate
    if (item.accepted && !item.locked) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.LockCandidate(candidate.id)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.LOCK_SELECTED },
        ) { Text("Lock accepted work") }
    }
    if (item.accepted && item.locked) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.UnlockCandidate(candidate.id)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.UNLOCK_SELECTED },
        ) { Text("Unlock accepted work") }
    }
    if (!item.accepted && candidate.status != MidiCoreCandidateStatus.REJECTED && candidate.status != MidiCoreCandidateStatus.STALE) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.RejectCandidate(candidate.id, "Not selected in Review.")) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.REJECT_SELECTED },
        ) { Text("Reject alternative") }
    }
    if (!item.accepted && restorable && item.authorityCurrent && candidate.status != MidiCoreCandidateStatus.STALE) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.RestoreCandidate(candidate.id, candidate.role, candidate.occurrenceId)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.RESTORE_SELECTED },
        ) { Text("Restore prior acceptance") }
    }
}

@Composable
private fun ReviewDiffCard(comparison: MidiCoreCandidateComparison) {
    val summary = MidiCoreCandidateDiff.summary(comparison.differences)
    ReviewCard(MidiCoreReviewPageTags.DIFF, "What changed") {
        Text("${summary.additions} notes added · ${summary.removals} removed · ${summary.changes} changed", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewContinueCard(
    state: MidiCoreWorkspaceState,
    progress: MidiCoreArrangementProgress,
    role: CandidateRole,
    occurrenceId: String,
    selected: MidiCoreCandidateReviewItem?,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    ReviewCard("midi-core-review-continue", "3. Continue") {
        when {
            progress.complete -> Button(
                onClick = { onNavigate(MidiCoreWorkspaceDestination.EXPORT) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.EXPORT },
            ) { Text("Continue to Export") }
            selected?.accepted == true -> {
                val next = progress.nextIncomplete
                Button(
                    onClick = {
                        if (next != null) onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(next.role, next.occurrence.id))
                        onNavigate(MidiCoreWorkspaceDestination.ARRANGE)
                    },
                    enabled = !state.busy && next != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.NEXT_SCOPE
                        contentDescription = next?.let { "Continue to ${it.occurrence.label} ${it.role.displayName}" }.orEmpty()
                    },
                ) { Text(next?.let { "Continue with ${it.occurrence.label} · ${it.role.displayName}" } ?: "Continue") }
            }
            else -> Text("Play and accept an alternative to finish this part.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
        OutlinedButton(
            onClick = {
                onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, occurrenceId))
                onNavigate(MidiCoreWorkspaceDestination.ARRANGE)
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.ARRANGE },
        ) { Text("Back to Arrange") }
    }
}

@Composable
private fun ReviewArrangementCard(
    state: MidiCoreWorkspaceState,
    complete: Boolean,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.ARRANGEMENT, "Full arrangement") {
        Text(
            if (complete) "Every part is accepted. Listen to the protected melody and complete accompaniment together."
            else "Full-song playback becomes available after every section and role has an accepted alternative.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (complete) MusicWorkspaceTokens.TextPrimary else MusicWorkspaceTokens.TextSecondary,
        )
        Button(
            onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAcceptedArrangement) },
            enabled = complete && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.PLAY_ARRANGEMENT
                contentDescription = if (complete) "Play the complete accepted MIDI arrangement" else "Complete every part before playing the full arrangement"
            },
        ) { Text("Play full arrangement") }
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
