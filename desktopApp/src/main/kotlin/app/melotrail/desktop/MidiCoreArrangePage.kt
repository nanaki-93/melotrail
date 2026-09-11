package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.melotrail.application.MidiCoreMusicalRepairIntent
import app.melotrail.arrangement.core.MidiCoreArrangementStyle
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCoreBassDrumCoordination
import app.melotrail.arrangement.core.MidiCoreChordCompingPhrasePatterns
import app.melotrail.arrangement.core.MidiCorePatternCatalog
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreOccurrenceArrangementPlan
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.ProjectSectionOccurrence

/** Stable semantic anchors for the song-map arrangement workspace. */
internal object MidiCoreArrangePageTags {
    const val ROOT = "midi-core-arrange-page"
    const val EMPTY = "midi-core-arrange-empty"
    const val STYLES = "midi-core-arrange-styles"
    const val STYLE_PREFIX = "midi-core-arrange-style-"
    const val DRAFT = "midi-core-arrange-draft"
    const val PLAN_PROPOSAL = "midi-core-arrange-plan-proposal"
    const val PROPOSE_PLAN = "midi-core-arrange-propose-plan"
    const val CONFIRM_PLAN = "midi-core-arrange-confirm-plan"
    const val CANCEL_PLAN = "midi-core-arrange-cancel-plan"
    const val CONFIRMED_PLAN = "midi-core-arrange-confirmed-plan"
    const val CREATE_DRAFT = "midi-core-arrange-create-draft"
    const val CANCEL = "midi-core-arrange-cancel"
    const val RETRY_DRAFT = "midi-core-arrange-retry-draft"
    const val INSPECTOR = "midi-core-arrange-section-inspector"
    const val REPAIR = "midi-core-arrange-repair"
    const val REPAIR_PREFIX = "midi-core-arrange-repair-"
    const val APPLY_REPAIR = "midi-core-arrange-apply-repair"
    const val CANCEL_REPAIR = "midi-core-arrange-cancel-repair"
    const val ADVANCED = "midi-core-arrange-advanced"
    const val ROLE_PREFIX = "midi-core-arrange-role-"
    const val PROFILE_MENU = "midi-core-arrange-profile-menu"
    const val PROFILE_PREFIX = "midi-core-arrange-profile-"
    const val PATTERN_MENU = "midi-core-arrange-pattern-menu"
    const val PATTERN_PREFIX = "midi-core-arrange-pattern-"
    const val GENERATE = "midi-core-arrange-generate-role"
    const val CANDIDATES = "midi-core-arrange-candidates"
    const val CANDIDATE_PREFIX = "midi-core-arrange-candidate-"
    const val REVIEW = "midi-core-arrange-open-review"
    const val BLOCKERS = "midi-core-arrange-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun profile(id: String): String = PROFILE_PREFIX + id
    fun pattern(id: String): String = PATTERN_PREFIX + id
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
    fun style(id: String): String = STYLE_PREFIX + id
    fun repair(intent: MidiCoreMusicalRepairIntent): String = REPAIR_PREFIX + intent.name.lowercase()
}

internal data class MidiCoreArrangementScope(val role: CandidateRole, val occurrence: ProjectSectionOccurrence)

internal data class MidiCoreArrangementProgress(val accepted: Int, val total: Int, val nextIncomplete: MidiCoreArrangementScope?) {
    val complete: Boolean get() = total > 0 && accepted == total
}

internal val midiCoreArrangementRoleOrder = listOf(CandidateRole.CHORDS, CandidateRole.BASS, CandidateRole.DRUMS)

internal fun midiCoreArrangementScopes(project: MidiCoreProject): List<MidiCoreArrangementScope> =
    project.authority?.occurrences.orEmpty().flatMap { occurrence -> midiCoreArrangementRoleOrder.map { MidiCoreArrangementScope(it, occurrence) } }

internal fun midiCoreArrangementProgress(project: MidiCoreProject): MidiCoreArrangementProgress {
    val scopes = midiCoreArrangementScopes(project)
    val acceptedScopes = project.acceptances.map { it.occurrenceId to it.role }.toSet()
    return MidiCoreArrangementProgress(
        accepted = scopes.count { it.occurrence.id to it.role in acceptedScopes },
        total = scopes.size,
        nextIncomplete = scopes.firstOrNull { it.occurrence.id to it.role !in acceptedScopes },
    )
}

/** Pick the next unused deterministic seed for an intentionally scoped role repair. */
internal fun midiCoreNextCandidateSeed(project: MidiCoreProject, role: CandidateRole, occurrenceId: String): Long {
    val used = project.candidates.asSequence().filter { it.role == role && it.occurrenceId == occurrenceId }.map { it.seed }.toSet()
    var seed = 1L
    while (seed in used) {
        require(seed < Long.MAX_VALUE) { "No deterministic candidate seed remains for this arrangement scope" }
        seed += 1L
    }
    return seed
}

/** Whole-song-first Arrange page: map, style preview, full draft, then contextual correction. */
@Composable
internal fun MidiCoreArrangePage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
    showSelectedSectionInspector: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val project = state.project
    val authority = project?.authority
    val occurrences = authority?.occurrences.orEmpty()
    if (project == null || authority == null || occurrences.isEmpty() || authority.chordEvents.isEmpty()) {
        Column(modifier.semantics { testTag = MidiCoreArrangePageTags.ROOT; contentDescription = "Arrange a MIDI song" }) {
            ArrangeEmptyState(state)
        }
        return
    }
    val selectedOccurrence = occurrences.singleOrNull { it.id == state.arrangement.selectedOccurrenceId } ?: occurrences.first()
    val selectedMapOccurrence = midiCoreSongMap(project).single { it.occurrence.id == selectedOccurrence.id }
    var advancedOpen by remember(project.id.value) { mutableStateOf(false) }
    var role by remember(project.id.value) { mutableStateOf(CandidateRole.CHORDS) }
    var profileId by remember(project.id.value, role) { mutableStateOf(MidiCorePerformanceProfileCatalog.allowedProfileIds(role).first()) }
    var patternId by remember(project.id.value, role) { mutableStateOf(MidiCorePatternCatalog.allowedPatternIds(role).first()) }

    Column(
        modifier.verticalScroll(rememberScrollState()).semantics { testTag = MidiCoreArrangePageTags.ROOT; contentDescription = "Arrange the whole MIDI song, then repair selected exceptions" },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "ARRANGE",
            title = "Shape the whole song",
            summary = "Select a section on the song map, preview a style, then create one complete MIDI draft.",
        )
        MidiCoreVerifiedTimeline(project, state.visualEvidence, state.audition, selectedOccurrence.id)
        MidiCoreSongMap(
            project = project,
            selectedOccurrenceId = selectedOccurrence.id,
            audition = state.audition,
            onOccurrenceSelected = { onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(it.occurrence.id)) },
        )
        ArrangeStyleGallery(
            state = state,
            occurrenceLabel = selectedMapOccurrence.displayLabel,
            onPreview = { style -> onIntent(MidiCoreWorkspaceIntent.PreviewArrangementStyle(style.id, selectedOccurrence.id)) },
            onDraft = { styleId ->
                val proposal = state.arrangementPlanProposal.proposal
                onIntent(
                    if (proposal?.styleId == styleId) {
                        MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft(styleId, state.arrangement.rootSeed)
                    } else {
                        MidiCoreWorkspaceIntent.CreateArrangementDraft(styleId, state.arrangement.rootSeed)
                    },
                )
            },
            onCancel = { onIntent(MidiCoreWorkspaceIntent.CancelOperation) },
            onRetry = { retry -> onIntent(retry) },
            onProposePlan = { styleId -> onIntent(MidiCoreWorkspaceIntent.ProposeArrangementPlan(styleId)) },
            onConfirmPlan = { onIntent(MidiCoreWorkspaceIntent.ConfirmArrangementPlan) },
            onCancelPlan = { onIntent(MidiCoreWorkspaceIntent.CancelArrangementPlan) },
        )
        if (showSelectedSectionInspector) MidiCoreArrangeSelectedSectionInspector(state, onIntent)
        ArrangeAdvancedRoleAdjustment(advancedOpen, { advancedOpen = it }) {
            ArrangeRoleRepair(
                project = project,
                state = state,
                role = role,
                occurrence = selectedOccurrence,
                profileId = profileId,
                patternId = patternId,
                onRoleSelected = {
                    role = it
                    onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(it, selectedOccurrence.id))
                    onIntent(MidiCoreWorkspaceIntent.LoadCandidates(it, selectedOccurrence.id))
                },
                onProfileSelected = { profileId = it },
                onPatternSelected = { patternId = it },
                onGenerate = {
                    onIntent(generationIntent(role, selectedOccurrence.id, profileId, patternId, midiCoreNextCandidateSeed(project, role, selectedOccurrence.id)))
                },
                onReview = {
                    onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, selectedOccurrence.id))
                    onNavigate(MidiCoreWorkspaceDestination.REVIEW)
                },
            )
        }
    }
}

/** The selected-section inspector has one owner and moves beside the page only at reference-wide layouts. */
@Composable
internal fun MidiCoreArrangeSelectedSectionInspector(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val project = state.project ?: return
    val occurrences = project.authority?.occurrences.orEmpty()
    if (occurrences.isEmpty()) return
    val selectedOccurrence = occurrences.singleOrNull { it.id == state.arrangement.selectedOccurrenceId } ?: occurrences.first()
    val mapOccurrences = midiCoreSongMap(project)
    val selectedMapIndex = mapOccurrences.indexOfFirst { it.occurrence.id == selectedOccurrence.id }
    val selectedMapOccurrence = mapOccurrences.getValueAt(selectedMapIndex)
    ArrangeSelectedSectionInspector(
        mapOccurrence = selectedMapOccurrence,
        previousOccurrence = mapOccurrences.getOrNull(selectedMapIndex - 1),
        nextOccurrence = mapOccurrences.getOrNull(selectedMapIndex + 1),
        occurrencePlan = project.arrangementPlan?.occurrences?.singleOrNull { it.occurrenceId == selectedOccurrence.id },
        state = state,
        onSelectOccurrence = { onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(it.occurrence.id)) },
        onPreviewRepair = { intent -> onIntent(MidiCoreWorkspaceIntent.PreviewMusicalRepair(selectedOccurrence.id, intent)) },
        onApplyRepair = { onIntent(MidiCoreWorkspaceIntent.ApplyMusicalRepair) },
        onCancelRepair = { onIntent(MidiCoreWorkspaceIntent.CancelMusicalRepair) },
    )
}

@Composable
private fun ArrangeEmptyState(state: MidiCoreWorkspaceState) {
    ArrangeCard(MidiCoreArrangePageTags.EMPTY, "Arrangement is not ready yet") {
        Text("Finish the song settings, section list, and chord progressions first.", style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.BLOCKERS }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            state.blockers.forEach { blocker -> Text("${blocker.message} Next: ${blocker.nextAction}", color = MusicWorkspaceTokens.Warning) }
        }
    }
}

@Composable
private fun ArrangeStyleGallery(
    state: MidiCoreWorkspaceState,
    occurrenceLabel: String,
    onPreview: (MidiCoreArrangementStyle) -> Unit,
    onDraft: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: (MidiCoreWorkspaceIntent.CreateArrangementDraft) -> Unit,
    onProposePlan: (String) -> Unit,
    onConfirmPlan: () -> Unit,
    onCancelPlan: () -> Unit,
) {
    val previewBusy = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.AUDITION &&
        state.operation.retry is MidiCoreWorkspaceIntent.PreviewArrangementStyle
    val generating = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.DRAFT_GENERATION
    val retry = state.operation.retry as? MidiCoreWorkspaceIntent.CreateArrangementDraft
    ArrangeCard(MidiCoreArrangePageTags.STYLES, "Choose a direction", compact = true) {
        when {
            generating -> ArrangeDraftProgress(state, onCancel)
            retry != null -> ArrangeDraftRetry(state, retry, onRetry)
            else -> {
                val styleId = state.stylePreview.selectedStyleId
                Row(
                    Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.DRAFT },
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                ) {
                    val planLabel = when (state.stylePreview.planState) {
                        app.melotrail.application.MidiCoreArrangementStylePreviewPlanState.EPHEMERAL_STYLE_PROPOSAL -> "ephemeral plan"
                        app.melotrail.application.MidiCoreArrangementStylePreviewPlanState.CONFIRMED -> "confirmed plan"
                        null -> null
                    }
                    val proposalMatchesStyle = state.arrangementPlanProposal.proposal?.styleId == styleId
                    Text(
                        state.stylePreview.cacheStatus?.let { "Preview ${it.name.lowercase()}${planLabel?.let { label -> " · $label" }.orEmpty()}" }
                            ?: "Choose one of five styles",
                        modifier = Modifier.weight(1f),
                        color = MusicWorkspaceTokens.TextSecondary,
                        maxLines = 2,
                    )
                    Button(
                        onClick = { styleId?.let(onDraft) },
                        enabled = styleId != null && !state.busy,
                        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                        contentPadding = PaddingValues(horizontal = MusicWorkspaceTokens.Spacing.Sm),
                        modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                            testTag = MidiCoreArrangePageTags.CREATE_DRAFT
                            contentDescription = styleId?.let {
                                if (proposalMatchesStyle) "Confirm the proposed plan and create full ${arrangementStyleDisplayName(it)} arrangement draft from $occurrenceLabel"
                                else "Create full ${arrangementStyleDisplayName(it)} arrangement draft from $occurrenceLabel"
                            }
                                ?: "Choose a style before creating a full arrangement draft"
                        },
                    ) { Text(if (proposalMatchesStyle) "Confirm plan & create" else "Create full draft", maxLines = 1) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                ) {
                    MidiCoreArrangementStyleCatalog.styles.forEach { style ->
                        val selected = state.stylePreview.selectedStyleId == style.id
                        OutlinedButton(
                            onClick = { onPreview(style) }, enabled = !state.busy || previewBusy,
                            colors = workspaceSelectableButtonColors(selected),
                            contentPadding = PaddingValues(MusicWorkspaceTokens.Spacing.Sm),
                            shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                            modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                testTag = MidiCoreArrangePageTags.style(style.id); this.selected = selected
                                contentDescription = "Preview ${style.displayName}: ${style.summary}${if (selected) ". Selected" else ""} for $occurrenceLabel"
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                                Text(style.displayName, maxLines = 1)
                                Text(style.summary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
                ArrangementPlanProposalCard(state, onProposePlan, onConfirmPlan, onCancelPlan)
            }
        }
    }
}

@Composable
private fun ArrangementPlanProposalCard(
    state: MidiCoreWorkspaceState,
    onPropose: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val proposal = state.arrangementPlanProposal.proposal
    val confirmed = state.project?.arrangementPlan
    val plan = confirmed ?: proposal?.plan
    val selectedStyleId = state.stylePreview.selectedStyleId
    Column(
        Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.PLAN_PROPOSAL },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        if (plan == null) {
            Text("Song plan is not saved yet. A style proposal stays in this session until you explicitly confirm it.", color = MusicWorkspaceTokens.TextSecondary)
            OutlinedButton(
                onClick = { selectedStyleId?.let(onPropose) },
                enabled = selectedStyleId != null && !state.busy,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.PROPOSE_PLAN
                    contentDescription = selectedStyleId?.let { "Propose a ${arrangementStyleDisplayName(it)} whole-song plan" }
                        ?: "Preview a style before proposing a whole-song plan"
                },
            ) { Text("Propose song plan") }
        } else {
            if (confirmed != null) {
                Text("Confirmed song plan", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { testTag = MidiCoreArrangePageTags.CONFIRMED_PLAN })
            } else {
                Text("Proposed ${arrangementStyleDisplayName(requireNotNull(proposal).styleId)} plan · not saved", style = MaterialTheme.typography.titleSmall)
            }
            Text("Groove: ${plan.sharedGroove.feel.name.lowercase().replace('_', ' ')} · ${plan.sharedGroove.subdivision.name.lowercase()} · ${plan.sharedGroove.drive.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            plan.occurrences.forEach { occurrence ->
                val roles = occurrence.roleSettings.joinToString(" · ") { "${it.role.displayName}: ${it.activity.name.lowercase()}, density ${it.density}%, ${it.registerPreference.name.lowercase()} register" }
                val occurrenceLabel = state.project?.authority?.occurrences
                    ?.singleOrNull { it.id == occurrence.occurrenceId }?.label ?: "Selected occurrence"
                Text("$occurrenceLabel: ${occurrence.purpose.name.lowercase().replace('_', ' ')} · energy ${occurrence.energy}%", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                Text(roles, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                Text("Entry: ${occurrence.entryIntent.name.lowercase().replace('_', ' ')} · Exit: ${occurrence.exitIntent.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            }
            if (confirmed == null) Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                Button(
                    onClick = onConfirm,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.CONFIRM_PLAN
                        contentDescription = "Confirm and save the proposed whole-song arrangement plan"
                    },
                ) { Text("Confirm plan") }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.CANCEL_PLAN
                        contentDescription = "Cancel the unsaved whole-song arrangement proposal"
                    },
                ) { Text("Cancel proposal") }
            }
        }
    }
}

@Composable
private fun ArrangeDraftProgress(
    state: MidiCoreWorkspaceState,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.DRAFT },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        state.operation.progress?.let { Text("${it.completed} of ${it.total} scopes complete", color = MusicWorkspaceTokens.Information) }
        Text(state.operation.message, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.CANCEL; contentDescription = "Cancel complete draft generation"
            },
        ) { Text("Cancel draft") }
    }
}

@Composable
private fun ArrangeDraftRetry(
    state: MidiCoreWorkspaceState,
    retry: MidiCoreWorkspaceIntent.CreateArrangementDraft,
    onRetry: (MidiCoreWorkspaceIntent.CreateArrangementDraft) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.DRAFT },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        Text(state.operation.message, color = MusicWorkspaceTokens.Warning)
        Button(
            onClick = { onRetry(retry) },
            shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.RETRY_DRAFT; contentDescription = "Retry complete draft generation"
            },
        ) { Text("Retry complete draft") }
    }
}

@Composable
private fun ArrangeSelectedSectionInspector(
    mapOccurrence: MidiCoreSongMapOccurrence,
    previousOccurrence: MidiCoreSongMapOccurrence?,
    nextOccurrence: MidiCoreSongMapOccurrence?,
    occurrencePlan: MidiCoreOccurrenceArrangementPlan?,
    state: MidiCoreWorkspaceState,
    onSelectOccurrence: (MidiCoreSongMapOccurrence) -> Unit,
    onPreviewRepair: (MidiCoreMusicalRepairIntent) -> Unit,
    onApplyRepair: () -> Unit,
    onCancelRepair: () -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.INSPECTOR, "${mapOccurrence.displayLabel} · selected section") {
        Text("${mapOccurrence.barRange} · ${mapOccurrence.chordSummary}", color = MusicWorkspaceTokens.TextSecondary)
        ArrangeSelectedSectionPlan(occurrencePlan, mapOccurrence.roleStates, state.operation)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = { previousOccurrence?.let(onSelectOccurrence) },
                enabled = previousOccurrence != null,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreSongMapTags.PREVIOUS
                    contentDescription = previousOccurrence?.let { "Select previous section, ${it.displayLabel}" } ?: "No previous section"
                },
            ) { Text("Previous") }
            OutlinedButton(
                onClick = { nextOccurrence?.let(onSelectOccurrence) },
                enabled = nextOccurrence != null,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreSongMapTags.NEXT
                    contentDescription = nextOccurrence?.let { "Select next section, ${it.displayLabel}" } ?: "No next section"
                },
            ) { Text("Next") }
        }
        ArrangeContextualRepair(
            occurrence = mapOccurrence,
            state = state,
            onPreview = onPreviewRepair,
            onApply = onApplyRepair,
            onCancel = onCancelRepair,
        )
    }
}

/**
 * A repair stays reviewable session state until Apply confirms the exact plan
 * adjustment.  The controls deliberately expose only the bounded M09 intents;
 * they never start the retired section-wide generation path.
 */
@Composable
private fun ArrangeContextualRepair(
    occurrence: MidiCoreSongMapOccurrence,
    state: MidiCoreWorkspaceState,
    onPreview: (MidiCoreMusicalRepairIntent) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    val repair = state.musicalRepair
    val prepared = repair.prepared
    Column(
        Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.REPAIR },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        if (prepared == null) {
            Text("Repair this selected section", style = MaterialTheme.typography.titleSmall)
            Text(
                if (state.project?.arrangementPlan == null) "Confirm a song plan before previewing a bounded repair."
                else "Preview an exact affected scope before generating any alternatives.",
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
            )
            repairActions(occurrence, state.busy, onPreview)
        } else {
            val settings = prepared.proposal.settings
            val sameOccurrence = settings.occurrenceId == occurrence.occurrence.id
            Text(
                if (repair.applied) "Repair plan saved · alternatives not accepted"
                else if (sameOccurrence) "Repair preview · not saved" else "Repair preview for ${settings.occurrenceId} · not saved",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(settings.description, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            Text(
                "Affected: ${prepared.invalidation.affectedScopes.joinToString { "${it.occurrenceId} ${it.role.displayName}" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
            )
            if (repair.noResultReason != null) {
                Text(repair.noResultReason, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                Button(
                    onClick = onApply,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.APPLY_REPAIR
                        contentDescription = if (repair.applied) "Retry this exact musical repair" else "Apply this reviewed musical repair"
                    },
                ) { Text(if (repair.applied) "Retry repair" else "Apply repair") }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.busy,
                    shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.CANCEL_REPAIR
                        contentDescription = "Cancel this unaccepted musical repair"
                    },
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun repairActions(
    occurrence: MidiCoreSongMapOccurrence,
    busy: Boolean,
    onPreview: (MidiCoreMusicalRepairIntent) -> Unit,
) {
    val actions = listOf(
        MidiCoreMusicalRepairIntent.LEAVE_MORE_MELODY_SPACE to "Leave more melody space",
        MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO to "Simplify piano",
        MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER to "Lower piano register",
        MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION to "Smooth transition",
        MidiCoreMusicalRepairIntent.REDUCE_BASS_MOVEMENT to "Reduce bass movement",
        MidiCoreMusicalRepairIntent.CALMER_DRUMS to "Calmer drums",
    )
    actions.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            row.forEach { (intent, label) ->
                OutlinedButton(
                    onClick = { onPreview(intent) },
                    enabled = !busy,
                    shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.repair(intent)
                        contentDescription = "$label for ${occurrence.displayLabel}"
                    },
                ) { Text(label, maxLines = 2) }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArrangeSelectedSectionPlan(
    occurrencePlan: MidiCoreOccurrenceArrangementPlan?,
    roleStates: Map<CandidateRole, MidiCoreSongMapRoleState>,
    operation: MidiCoreWorkspaceOperation,
) {
    if (occurrencePlan == null) {
        Text("No confirmed song plan for this section.", color = MusicWorkspaceTokens.TextSecondary)
    } else {
        Text(
            "Plan · ${friendlyToken(occurrencePlan.purpose.name)} · energy ${occurrencePlan.energy}%",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Entry ${friendlyToken(occurrencePlan.entryIntent.name)} · exit ${friendlyToken(occurrencePlan.exitIntent.name)}",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
    CandidateRole.entries.forEach { role ->
        val setting = occurrencePlan?.roleSettings?.singleOrNull { it.role == role }
        val state = roleStates.getValue(role)
        val activity = setting?.activity?.let { friendlyToken(it.name) }
        val status = when {
            state == MidiCoreSongMapRoleState.PLANNED_REST_IN_DRAFT -> "Planned rest in draft"
            state == MidiCoreSongMapRoleState.ACCEPTED_PLANNED_REST -> "Accepted planned rest"
            setting?.activity == MidiCoreRoleActivity.INACTIVE -> "Planned rest on next draft"
            else -> state.label
        }
        val details = setting?.let { "${it.density}% · ${friendlyToken(it.registerPreference.name)} register" }
        Text(
            listOfNotNull("${role.displayName}: $status", activity, details).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = if (state == MidiCoreSongMapRoleState.STALE || state == MidiCoreSongMapRoleState.ATTENTION) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.TextSecondary,
        )
    }
    operation.progress?.takeIf { operation.active && operation.kind == MidiCoreWorkspaceOperationKind.DRAFT_GENERATION }?.let { progress ->
        Text(
            "Draft progress: ${progress.completed} of ${progress.total} scopes complete.",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.Information,
        )
    }
}

private fun <T> List<T>.getValueAt(index: Int): T {
    require(index >= 0) { "The selected authoritative occurrence must be present in the song map" }
    return get(index)
}

@Composable
private fun ArrangeAdvancedRoleAdjustment(open: Boolean, onOpenChanged: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ArrangeCard(MidiCoreArrangePageTags.ADVANCED, "Adjust roles") {
        Text("Use profile and rhythm controls only for a deliberate local correction.", color = MusicWorkspaceTokens.TextSecondary)
        OutlinedButton(
            onClick = { onOpenChanged(!open) }, modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                contentDescription = if (open) "Hide advanced role adjustment" else "Show advanced role adjustment"
            },
        ) { Text(if (open) "Hide role controls" else "Adjust roles") }
        if (open) content()
    }
}

@Composable
private fun ArrangeRoleRepair(
    project: MidiCoreProject,
    state: MidiCoreWorkspaceState,
    role: CandidateRole,
    occurrence: ProjectSectionOccurrence,
    profileId: String,
    patternId: String,
    onRoleSelected: (CandidateRole) -> Unit,
    onProfileSelected: (String) -> Unit,
    onPatternSelected: (String) -> Unit,
    onGenerate: () -> Unit,
    onReview: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        midiCoreArrangementRoleOrder.forEach { option ->
            OutlinedButton(
                onClick = { onRoleSelected(option) }, enabled = !state.busy, colors = workspaceSelectableButtonColors(option == role),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.role(option); selected = option == role
                    contentDescription = "${option.displayName}${if (option == role) ", selected" else ""} role repair"
                },
            ) { Text(option.displayName) }
        }
    }
    ArrangeDropdown(MidiCoreArrangePageTags.PROFILE_MENU, "Performance", friendlyToken(profileId), MidiCorePerformanceProfileCatalog.profiles.filter { it.role == role }.map { it.id to friendlyToken(it.id) }, !state.busy, MidiCoreArrangePageTags::profile, onProfileSelected)
    val patterns = MidiCorePatternCatalog.inventory().filter { it.role == role }
    ArrangeDropdown(MidiCoreArrangePageTags.PATTERN_MENU, "Rhythm", patterns.singleOrNull { it.id == patternId }?.displayName ?: friendlyToken(patternId), patterns.map { it.id to it.displayName }, !state.busy, MidiCoreArrangePageTags::pattern, onPatternSelected)
    Button(
        onClick = onGenerate, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
            testTag = MidiCoreArrangePageTags.GENERATE; contentDescription = "Regenerate ${role.displayName} for ${occurrence.label}"
        },
    ) { Text("Regenerate ${role.displayName}") }
    val candidates = if (state.review.role == role && state.review.occurrenceId == occurrence.id) state.review.candidates else emptyList()
    ArrangeCandidateSummary(candidates, role, occurrence, state.busy, onReview)
}

@Composable
private fun ArrangeDropdown(
    tag: String, label: String, selectedLabel: String, options: List<Pair<String, String>>, enabled: Boolean,
    optionTag: (String) -> String, onSelected: (String) -> Unit,
) {
    var open by remember(label, selectedLabel) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = tag; contentDescription = "$label $selectedLabel; open choices" }) { Text("$label · $selectedLabel") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelected(id) }, modifier = Modifier.semantics { testTag = optionTag(id) }) }
        }
    }
}

@Composable
private fun ArrangeCandidateSummary(
    candidates: List<MidiCoreCandidateReviewItem>, role: CandidateRole, occurrence: ProjectSectionOccurrence, busy: Boolean, onReview: () -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.CANDIDATES, "Local alternatives") {
        if (candidates.isEmpty()) Text("No local ${role.displayName} alternatives are loaded for ${occurrence.label}.", color = MusicWorkspaceTokens.TextSecondary)
        candidates.takeLast(3).reversed().forEachIndexed { index, item ->
            val advisories = item.validation.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }
            Text(
                "Alternative ${candidates.size - index}: ${item.candidate.status.name.lowercase()} · ${item.validation.noteCount} notes · $advisories advisories",
                modifier = Modifier.semantics { testTag = MidiCoreArrangePageTags.candidate(item.candidate.id) },
            )
            if (!item.authorityCurrent || item.candidate.status == MidiCoreCandidateStatus.STALE) Text("Needs regeneration after an authority change", color = MusicWorkspaceTokens.Warning)
        }
        Button(onClick = onReview, enabled = candidates.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreArrangePageTags.REVIEW; contentDescription = "Review local alternatives for ${occurrence.label}" }) { Text("Review this role") }
    }
}

@Composable
internal fun ArrangeCard(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) =
    ArrangeCard(tag, title, compact = false, content = content)

@Composable
internal fun ArrangeCard(tag: String, title: String, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().semantics { testTag = tag }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(if (compact) MusicWorkspaceTokens.Spacing.Xs else MusicWorkspaceTokens.Spacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun generationIntent(role: CandidateRole, occurrenceId: String, profileId: String, patternId: String, seed: Long) =
    MidiCoreWorkspaceIntent.GenerateCandidate(
        role,
        occurrenceId,
        profileId,
        patternId,
        MidiCoreGeneratorInput(
            "midi-core-desktop",
            MidiCoreBassDrumCoordination.generatorVersion(
                MidiCoreChordCompingPhrasePatterns.generatorVersion(
                    "midi-core-v${MidiCoreArrangementStyleCatalog.VERSION}-patterns-v${MidiCorePatternCatalog.VERSION}",
                    role,
                ),
                role,
            ),
            patternId,
            seed,
        ),
    )

internal fun friendlyToken(value: String): String = value.substringAfterLast('.').replace('-', ' ').replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

internal fun arrangementStyleDisplayName(styleId: String): String =
    MidiCoreArrangementStyleCatalog.styles.singleOrNull { it.id == styleId }?.displayName ?: friendlyToken(styleId)

internal val CandidateRole.displayName: String get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)
