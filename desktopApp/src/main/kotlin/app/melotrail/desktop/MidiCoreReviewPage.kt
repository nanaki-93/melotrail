package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject

/** Stable semantic anchors for the whole-draft Review decision and contextual evidence. */
internal object MidiCoreReviewPageTags {
    const val ROOT = "midi-core-review-page"
    const val EMPTY = "midi-core-review-empty"
    const val DRAFT = "midi-core-review-draft"
    const val PLAY_DRAFT = "midi-core-review-play-draft"
    const val USE_DRAFT = "midi-core-review-use-draft"
    const val UNDO_DRAFT = "midi-core-review-undo-draft"
    const val EXPORT = "midi-core-review-open-export"
    const val INSPECTOR = "midi-core-review-selected-section"
    const val OPEN_EXCEPTIONS = "midi-core-review-open-exceptions"
    const val EXCEPTIONS = "midi-core-review-exceptions"
    const val ROLE_PREFIX = "midi-core-review-role-"
    const val CANDIDATE_MENU = "midi-core-review-candidate-menu"
    const val CANDIDATE_PREFIX = "midi-core-review-candidate-"
    const val PLAY_CANDIDATE = "midi-core-review-play-candidate"
    const val ACCEPT_CANDIDATE = "midi-core-review-accept-candidate"
    const val COMPARE_PREFIX = "midi-core-review-compare-"
    const val DIFF = "midi-core-review-diff"
    const val MORE_ACTIONS = "midi-core-review-more-actions"
    const val REJECT_CANDIDATE = "midi-core-review-reject-candidate"
    const val RESTORE_CANDIDATE = "midi-core-review-restore-candidate"
    const val LOCK_CANDIDATE = "midi-core-review-lock-candidate"
    const val UNLOCK_CANDIDATE = "midi-core-review-unlock-candidate"
    const val REPAIR = "midi-core-review-repair-section"
    const val BLOCKERS = "midi-core-review-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
    fun compare(id: String): String = COMPARE_PREFIX + id
}

/** Hear and accept one persisted draft first; detailed candidate work is selected-section context. */
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
    if (project == null || authority == null || occurrences.isEmpty()) {
        Column(modifier.verticalScroll(rememberScrollState()).semantics {
            testTag = MidiCoreReviewPageTags.ROOT
            contentDescription = "Review a complete MIDI arrangement draft"
        }) { ReviewEmptyState(state) }
        return
    }
    val selectedOccurrence = occurrences.singleOrNull { it.id == state.arrangement.selectedOccurrenceId } ?: occurrences.first()
    val selectedMapOccurrence = midiCoreSongMap(project).single { it.occurrence.id == selectedOccurrence.id }
    val progress = midiCoreArrangementProgress(project)
    val draft = currentArrangementDraft(project)
    var exceptionsOpen by remember(project.id.value) { mutableStateOf(false) }

    Column(
        modifier.verticalScroll(rememberScrollState()).semantics {
            testTag = MidiCoreReviewPageTags.ROOT
            contentDescription = "Review the complete MIDI draft, then inspect selected exceptions"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "REVIEW",
            title = "Listen to the whole draft",
            summary = "Play the complete arrangement, use it in one decision, and inspect exceptions only when needed.",
        )
        MidiCoreSongMap(
            project = project,
            selectedOccurrenceId = selectedOccurrence.id,
            audition = state.audition,
            onOccurrenceSelected = { onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(it.occurrence.id)) },
        )
        ReviewDraftDecision(state, draft, progress, onIntent, onNavigate)
        ReviewSelectedSectionInspector(state, selectedMapOccurrence, onNavigate)
        ReviewExceptionDisclosure(exceptionsOpen, { exceptionsOpen = it }) {
            ReviewExceptionDetails(state, selectedOccurrence.id, onIntent)
        }
    }
}

@Composable
private fun ReviewEmptyState(state: MidiCoreWorkspaceState) {
    ReviewCard(MidiCoreReviewPageTags.EMPTY, "Nothing to review yet") {
        Text("Create a complete draft in Arrange after saving the song settings, sections, and chord progressions.", style = MaterialTheme.typography.bodyLarge)
        ReviewBlockers(state.blockers)
    }
}

@Composable
private fun ReviewDraftDecision(
    state: MidiCoreWorkspaceState,
    draft: MidiCoreArrangementDraft?,
    progress: MidiCoreArrangementProgress,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.DRAFT, "Complete draft") {
        when (draft) {
            null -> {
                Text("No current complete draft is ready to hear.", style = MaterialTheme.typography.titleMedium)
                Text("Choose a style and create one complete draft in Arrange.", color = MusicWorkspaceTokens.TextSecondary)
                Button(
                    onClick = { onNavigate(MidiCoreWorkspaceDestination.ARRANGE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        contentDescription = "Open Arrange to create a complete draft"
                    },
                ) { Text("Create a draft in Arrange") }
            }
            else -> {
                Text("${friendlyToken(draft.styleId)} · ${draft.validation.scopeCount} validated parts", style = MaterialTheme.typography.titleMedium)
                Text("Playback does not require accepting each role first.", color = MusicWorkspaceTokens.TextSecondary)
                Button(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.PlayArrangementDraft(draft.id)) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.PLAY_DRAFT
                        contentDescription = "Play complete ${friendlyToken(draft.styleId)} MIDI draft"
                    },
                ) { Text("Play complete draft") }
                Button(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.UseArrangementDraft(draft.id)) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.USE_DRAFT
                        contentDescription = "Use this complete draft as the accepted arrangement"
                    },
                ) { Text("Use this draft") }
            }
        }
        val latestBatch = state.project?.arrangementDraftAcceptanceHistory?.lastOrNull()
        if (latestBatch != null) {
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.UndoArrangementDraftAcceptance(latestBatch.id)) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.UNDO_DRAFT
                    contentDescription = "Undo the latest complete-draft acceptance"
                },
            ) { Text("Undo last draft acceptance") }
        }
        if (progress.complete) {
            Text("Every section and role is accepted. Export is ready.", color = MusicWorkspaceTokens.Success)
            OutlinedButton(
                onClick = { onNavigate(MidiCoreWorkspaceDestination.EXPORT) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.EXPORT
                    contentDescription = "Open Export for the accepted MIDI arrangement"
                },
            ) { Text("Open Export") }
        } else {
            Text("${progress.accepted} of ${progress.total} roles accepted. Export remains locked until the complete accepted arrangement is ready.", color = MusicWorkspaceTokens.TextSecondary)
        }
        ReviewBlockers(state.blockers)
    }
}

@Composable
private fun ReviewSelectedSectionInspector(
    state: MidiCoreWorkspaceState,
    occurrence: MidiCoreSongMapOccurrence,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    ReviewCard(MidiCoreReviewPageTags.INSPECTOR, "${occurrence.displayLabel} · selected section") {
        Text("${occurrence.barRange} · ${occurrence.chordSummary}", color = MusicWorkspaceTokens.TextSecondary)
        occurrence.roleStates.forEach { (role, roleState) -> Text("${role.displayName}: ${roleState.label}") }
        val localBlockers = state.blockers.filter { it.occurrenceId == occurrence.occurrence.id }
        if (localBlockers.isNotEmpty()) {
            Text("This section needs attention.", color = MusicWorkspaceTokens.Warning)
            localBlockers.forEach { blocker -> Text("${blocker.message} Next: ${blocker.nextAction}", color = MusicWorkspaceTokens.Warning) }
        }
        Button(
            onClick = { onNavigate(MidiCoreWorkspaceDestination.ARRANGE) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.REPAIR
                contentDescription = "Repair ${occurrence.displayLabel} in Arrange"
            },
        ) { Text("Repair this section in Arrange") }
    }
}

@Composable
private fun ReviewExceptionDisclosure(open: Boolean, onOpenChanged: (Boolean) -> Unit, content: @Composable () -> Unit) {
    ReviewCard(MidiCoreReviewPageTags.EXCEPTIONS, "Inspect alternatives") {
        Text("Compare, lock, reject, or restore immutable alternatives only for this selected section.", color = MusicWorkspaceTokens.TextSecondary)
        OutlinedButton(
            onClick = { onOpenChanged(!open) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreReviewPageTags.OPEN_EXCEPTIONS
                selected = open
                contentDescription = if (open) "Hide selected-section alternatives" else "Show selected-section alternatives"
            },
        ) { Text(if (open) "Hide alternatives" else "Inspect alternatives") }
        if (open) content()
    }
}

@Composable
private fun ReviewExceptionDetails(
    state: MidiCoreWorkspaceState,
    occurrenceId: String,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    var role by remember(state.project?.id?.value, occurrenceId) { mutableStateOf(CandidateRole.CHORDS) }
    val scopeMatches = state.review.role == role && state.review.occurrenceId == occurrenceId
    val candidates = if (scopeMatches) state.review.candidates else emptyList()
    var selectedCandidateId by remember(state.project?.id?.value, occurrenceId, role) { mutableStateOf(state.review.selectedCandidateId) }
    LaunchedEffect(occurrenceId, role) {
        onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, occurrenceId))
        onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, occurrenceId))
    }
    LaunchedEffect(candidates, state.review.selectedCandidateId) {
        selectedCandidateId = state.review.selectedCandidateId?.takeIf { id -> candidates.any { it.candidate.id == id } }
            ?: selectedCandidateId?.takeIf { id -> candidates.any { it.candidate.id == id } }
            ?: candidates.singleOrNull { it.accepted }?.candidate?.id
            ?: candidates.lastOrNull { it.candidate.status != MidiCoreCandidateStatus.REJECTED }?.candidate?.id
            ?: candidates.lastOrNull()?.candidate?.id
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        CandidateRole.entries.forEach { option ->
            OutlinedButton(
                onClick = { role = option }, enabled = !state.busy, colors = workspaceSelectableButtonColors(option == role),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.role(option)
                    selected = option == role
                    contentDescription = "${option.displayName}${if (option == role) ", selected" else ""} alternatives"
                },
            ) { Text(option.displayName) }
        }
    }
    val selected = candidates.singleOrNull { it.candidate.id == selectedCandidateId }
    ReviewCandidateDetails(state, candidates, selected, onIntent)
    state.review.comparison?.let { comparison ->
        ReviewCard(MidiCoreReviewPageTags.DIFF, "Alternative comparison") {
            Text("${comparison.first.candidate.id} compared with ${comparison.second.candidate.id}")
            comparison.differences.forEach { difference -> Text("${difference.kind}: ${difference.first ?: "none"} → ${difference.second ?: "none"}") }
        }
    }
}

@Composable
private fun ReviewCandidateDetails(
    state: MidiCoreWorkspaceState,
    candidates: List<MidiCoreCandidateReviewItem>,
    selected: MidiCoreCandidateReviewItem?,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    var menuOpen by remember(candidates) { mutableStateOf(false) }
    var moreOpen by remember(selected?.candidate?.id) { mutableStateOf(false) }
    when {
        candidates.isEmpty() -> Text("No immutable alternatives are available for this role yet. Repair the selected section in Arrange.", color = MusicWorkspaceTokens.TextSecondary)
        selected == null -> Text("Choose an alternative to inspect its evidence.", color = MusicWorkspaceTokens.TextSecondary)
        else -> {
            val selectedIndex = candidates.indexOf(selected) + 1
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.CANDIDATE_MENU
                        contentDescription = "Alternative $selectedIndex of ${candidates.size}; open choices"
                    },
                ) { Text("Alternative $selectedIndex of ${candidates.size}") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    candidates.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = { Text("Alternative ${index + 1}${if (item.accepted) " · Accepted" else ""}") },
                            onClick = { menuOpen = false; onIntent(MidiCoreWorkspaceIntent.SelectReviewCandidate(item.candidate.id)) },
                            modifier = Modifier.semantics { testTag = MidiCoreReviewPageTags.candidate(item.candidate.id) },
                        )
                    }
                }
            }
            val blockers = selected.validation.findings.filter { it.severity == MidiCoreRoleFindingSeverity.BLOCKING }
            Text(
                "${selected.candidate.profileId} · ${selected.candidate.patternId} · ${selected.validation.noteCount} notes. " +
                    if (!selected.authorityCurrent || selected.candidate.status == MidiCoreCandidateStatus.STALE) "Needs regeneration." else "${blockers.size} blocking findings.",
                color = if (blockers.isEmpty() && selected.authorityCurrent) MusicWorkspaceTokens.TextSecondary else MusicWorkspaceTokens.Warning,
            )
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlayCandidate(selected.candidate.id, selected.candidate.role, selected.candidate.occurrenceId)) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.PLAY_CANDIDATE
                    contentDescription = "Play the selected MIDI alternative"
                },
            ) { Text("Play alternative") }
            if (!selected.accepted && selected.authorityCurrent && selected.candidate.status == MidiCoreCandidateStatus.CURRENT && blockers.isEmpty()) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.AcceptCandidate(selected.candidate.id)) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.ACCEPT_CANDIDATE
                        contentDescription = "Accept only this selected exception alternative"
                    },
                ) { Text("Accept this exception") }
            }
            candidates.firstOrNull { it.candidate.id != selected.candidate.id }?.let { other ->
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.CompareCandidates(selected.candidate.id, other.candidate.id)) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreReviewPageTags.compare(other.candidate.id)
                        contentDescription = "Compare the selected alternative with another"
                    },
                ) { Text("Compare alternatives") }
            }
            val restorable = state.project?.acceptanceHistory?.any { history ->
                history.candidateId == selected.candidate.id && history.action in setOf(MidiCoreAcceptanceAction.ACCEPTED, MidiCoreAcceptanceAction.REPLACED, MidiCoreAcceptanceAction.RESTORED)
            } == true
            OutlinedButton(
                onClick = { moreOpen = !moreOpen }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreReviewPageTags.MORE_ACTIONS
                    this.selected = moreOpen
                    contentDescription = if (moreOpen) "Hide less common candidate actions" else "Show less common candidate actions"
                },
            ) { Text(if (moreOpen) "Hide more actions" else "More actions") }
            if (moreOpen) ReviewCandidateLifecycleActions(selected, restorable, !state.busy, onIntent)
        }
    }
}

@Composable
private fun ReviewCandidateLifecycleActions(
    selected: MidiCoreCandidateReviewItem,
    restorable: Boolean,
    enabled: Boolean,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val candidate = selected.candidate
    if (selected.accepted) {
        OutlinedButton(
            onClick = { onIntent(if (selected.locked) MidiCoreWorkspaceIntent.UnlockCandidate(candidate.id) else MidiCoreWorkspaceIntent.LockCandidate(candidate.id)) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = if (selected.locked) MidiCoreReviewPageTags.UNLOCK_CANDIDATE else MidiCoreReviewPageTags.LOCK_CANDIDATE
            },
        ) { Text(if (selected.locked) "Unlock exception" else "Lock exception") }
    } else if (candidate.status != MidiCoreCandidateStatus.REJECTED && candidate.status != MidiCoreCandidateStatus.STALE) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.RejectCandidate(candidate.id, "Not selected in Review.")) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.REJECT_CANDIDATE },
        ) { Text("Reject this alternative") }
    }
    if (!selected.accepted && restorable && selected.authorityCurrent && candidate.status != MidiCoreCandidateStatus.STALE) {
        OutlinedButton(
            onClick = { onIntent(MidiCoreWorkspaceIntent.RestoreCandidate(candidate.id, candidate.role, candidate.occurrenceId)) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreReviewPageTags.RESTORE_CANDIDATE },
        ) { Text("Restore this alternative") }
    }
}

@Composable
private fun ReviewBlockers(blockers: List<MidiCoreWorkspaceBlocker>) {
    if (blockers.isEmpty()) return
    Column(Modifier.fillMaxWidth().semantics { testTag = MidiCoreReviewPageTags.BLOCKERS }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        blockers.forEach { blocker -> Text("${blocker.message} Next: ${blocker.nextAction}", color = MusicWorkspaceTokens.Warning) }
    }
}

private fun currentArrangementDraft(project: MidiCoreProject): MidiCoreArrangementDraft? = runCatching {
    app.melotrail.project.MidiCoreAuthorityHasher.from(project).sha256
}.getOrNull()?.let { hash -> project.arrangementDrafts.lastOrNull { it.authorityHash == hash } }

@Composable
private fun ReviewCard(tag: String, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ArrangeCard(tag, title, content)
}
