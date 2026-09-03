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
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.arrangement.core.MidiCoreArrangementStyle
import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.arrangement.core.MidiCorePatternCatalog
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectSectionOccurrence

/** Stable semantic anchors for the guided arrangement-preparation page. */
internal object MidiCoreArrangePageTags {
    const val ROOT = "midi-core-arrange-page"
    const val EMPTY = "midi-core-arrange-empty"
    const val PROGRESS = "midi-core-arrange-progress"
    const val SCOPE = "midi-core-arrange-scope"
    const val ROLE_PREFIX = "midi-core-arrange-role-"
    const val OCCURRENCE_MENU = "midi-core-arrange-occurrence-menu"
    const val OCCURRENCE_PREFIX = "midi-core-arrange-occurrence-"
    const val PROFILE_MENU = "midi-core-arrange-profile-menu"
    const val PROFILE_PREFIX = "midi-core-arrange-profile-"
    const val PATTERN_MENU = "midi-core-arrange-pattern-menu"
    const val PATTERN_PREFIX = "midi-core-arrange-pattern-"
    const val GENERATE = "midi-core-arrange-generate"
    const val STYLES = "midi-core-arrange-styles"
    const val STYLE_PREFIX = "midi-core-arrange-style-"
    const val ADVANCED = "midi-core-arrange-advanced"
    const val STATUS = "midi-core-arrange-status"
    const val CANDIDATES = "midi-core-arrange-candidates"
    const val CANDIDATE_PREFIX = "midi-core-arrange-candidate-"
    const val CANCEL = "midi-core-arrange-cancel"
    const val REVIEW = "midi-core-arrange-open-review"
    const val BLOCKERS = "midi-core-arrange-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun occurrence(id: String): String = OCCURRENCE_PREFIX + id
    fun profile(id: String): String = PROFILE_PREFIX + id
    fun pattern(id: String): String = PATTERN_PREFIX + id
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
    fun style(id: String): String = STYLE_PREFIX + id
}

internal data class MidiCoreArrangementScope(
    val role: CandidateRole,
    val occurrence: ProjectSectionOccurrence,
)

internal data class MidiCoreArrangementProgress(
    val accepted: Int,
    val total: Int,
    val nextIncomplete: MidiCoreArrangementScope?,
) {
    val complete: Boolean get() = total > 0 && accepted == total
}

internal val midiCoreArrangementRoleOrder = listOf(CandidateRole.CHORDS, CandidateRole.BASS, CandidateRole.DRUMS)

/** Return the musician's section-first arrangement order: Chords, Bass, then Drums. */
internal fun midiCoreArrangementScopes(project: MidiCoreProject): List<MidiCoreArrangementScope> =
    project.authority?.occurrences.orEmpty().flatMap { occurrence ->
        midiCoreArrangementRoleOrder.map { role -> MidiCoreArrangementScope(role, occurrence) }
    }

/** Summarize accepted work and the next unfinished musical decision. */
internal fun midiCoreArrangementProgress(project: MidiCoreProject): MidiCoreArrangementProgress {
    val scopes = midiCoreArrangementScopes(project)
    val acceptedScopes = project.acceptances.map { it.occurrenceId to it.role }.toSet()
    return MidiCoreArrangementProgress(
        accepted = scopes.count { it.occurrence.id to it.role in acceptedScopes },
        total = scopes.size,
        nextIncomplete = scopes.firstOrNull { it.occurrence.id to it.role !in acceptedScopes },
    )
}

/** Pick the next unused deterministic seed for one role/section scope. */
internal fun midiCoreNextCandidateSeed(project: MidiCoreProject, role: CandidateRole, occurrenceId: String): Long {
    val used = project.candidates.asSequence()
        .filter { it.role == role && it.occurrenceId == occurrenceId }
        .map { it.seed }
        .toSet()
    var seed = 1L
    while (seed in used) {
        require(seed < Long.MAX_VALUE) { "No deterministic candidate seed remains for this arrangement scope" }
        seed += 1L
    }
    return seed
}

/** Guide one scoped alternative from musical choices into Review. */
@Composable
internal fun MidiCoreArrangePage(
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
    var profileId by remember(project?.id?.value, role) {
        mutableStateOf(MidiCorePerformanceProfileCatalog.allowedProfileIds(role).firstOrNull().orEmpty())
    }
    var patternId by remember(project?.id?.value, role) {
        mutableStateOf(MidiCorePatternCatalog.allowedPatternIds(role).firstOrNull().orEmpty())
    }
    var advancedControlsOpen by remember(project?.id?.value) { mutableStateOf(false) }

    LaunchedEffect(project?.id?.value, role, occurrenceId) {
        val selectedOccurrence = occurrenceId ?: return@LaunchedEffect
        if (project != null && authority != null) {
            onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, selectedOccurrence))
            onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, selectedOccurrence))
        }
    }

    Column(
        modifier.semantics {
            testTag = MidiCoreArrangePageTags.ROOT
            contentDescription = "Prepare the MIDI arrangement in three guided steps"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "PREPARE",
            title = "Arrange",
            summary = "Choose a musical direction to hear a short MIDI preview. Use role adjustments only when a specific section needs repair.",
        )
        if (project == null || authority == null || occurrences.isEmpty() || authority.chordEvents.isEmpty()) {
            ArrangeEmptyState(state)
            return@Column
        }

        requireNotNull(progress)
        ArrangeProgressCard(progress, role, occurrenceId, occurrences)
        val selectedOccurrence = occurrences.singleOrNull { it.id == occurrenceId }
        ArrangeStyleGallery(
            state = state,
            occurrence = selectedOccurrence,
            onPreview = { style ->
                onIntent(MidiCoreWorkspaceIntent.PreviewArrangementStyle(style.id, requireNotNull(occurrenceId)))
            },
        )
        ArrangeScopeCard(
            role = role,
            occurrenceId = occurrenceId,
            occurrences = occurrences,
            enabled = !state.busy,
            onRoleSelected = { role = it },
            onOccurrenceSelected = { occurrenceId = it },
        )
        val scopeMatches = state.review.role == role && state.review.occurrenceId == occurrenceId
        val candidates = if (scopeMatches) state.review.candidates else emptyList()
        val nextSeed = midiCoreNextCandidateSeed(project, role, occurrenceId.orEmpty())
        ArrangeAdvancedRoleAdjustment(
            open = advancedControlsOpen,
            onOpenChanged = { advancedControlsOpen = it },
        ) {
            ArrangeFeelCard(
                role = role,
                profileId = profileId,
                patternId = patternId,
                enabled = !state.busy,
                onProfileSelected = { profileId = it },
                onPatternSelected = { patternId = it },
            )
            ArrangeGenerateCard(
                state = state,
                role = role,
                occurrenceLabel = selectedOccurrence?.label.orEmpty(),
                ready = selectedOccurrence != null && profileId.isNotBlank() && patternId.isNotBlank(),
                nextSeed = nextSeed,
                onGenerate = {
                    onIntent(generationIntent(role, requireNotNull(occurrenceId), profileId, patternId, nextSeed))
                },
                onCancel = { onIntent(MidiCoreWorkspaceIntent.CancelOperation) },
            )
        }
        ArrangeCandidateSummary(
            state = state,
            role = role,
            occurrence = selectedOccurrence,
            candidates = candidates,
            onReview = {
                onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, requireNotNull(occurrenceId)))
                onNavigate(MidiCoreWorkspaceDestination.REVIEW)
            },
        )
    }
}

@Composable
private fun ArrangeEmptyState(state: MidiCoreWorkspaceState) {
    ArrangeCard(MidiCoreArrangePageTags.EMPTY, "Arrangement is not ready yet") {
        Text("Finish the song settings, section list, and chord progressions first.", style = MaterialTheme.typography.bodyLarge)
        Column(
            Modifier.fillMaxWidth().semantics {
                testTag = MidiCoreArrangePageTags.BLOCKERS
                contentDescription = "${state.blockers.size} arrangement blocker${if (state.blockers.size == 1) "" else "s"}"
            },
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
        ) {
            state.blockers.forEach { blocker ->
                Text("${blocker.message} Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Warning)
            }
        }
    }
}

@Composable
private fun ArrangeProgressCard(
    progress: MidiCoreArrangementProgress,
    role: CandidateRole,
    occurrenceId: String?,
    occurrences: List<ProjectSectionOccurrence>,
) {
    val currentLabel = occurrences.singleOrNull { it.id == occurrenceId }?.label.orEmpty()
    ArrangeCard(MidiCoreArrangePageTags.PROGRESS, "Arrangement progress") {
        Text(
            if (progress.complete) "All ${progress.total} section-role choices are accepted."
            else "${progress.accepted} of ${progress.total} section-role choices accepted",
            style = MaterialTheme.typography.titleMedium,
            color = if (progress.complete) MusicWorkspaceTokens.Success else MusicWorkspaceTokens.Information,
        )
        Text("Now preparing: $currentLabel · ${role.displayName}", style = MaterialTheme.typography.bodyMedium)
        progress.nextIncomplete?.let { next ->
            Text("Recommended order: ${next.occurrence.label} · ${next.role.displayName}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
    }
}

/** Primary Arrange choice: one named all-role style immediately opens the persistent MIDI player. */
@Composable
private fun ArrangeStyleGallery(
    state: MidiCoreWorkspaceState,
    occurrence: ProjectSectionOccurrence?,
    onPreview: (MidiCoreArrangementStyle) -> Unit,
) {
    val previewBusy = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.AUDITION &&
        state.operation.retry is MidiCoreWorkspaceIntent.PreviewArrangementStyle
    val canPreview = occurrence != null && (!state.busy || previewBusy)
    ArrangeCard(MidiCoreArrangePageTags.STYLES, "1. Choose a direction") {
        Text(
            occurrence?.let { "Choose a style to hear ${it.label} as a two-to-four-bar MIDI loop." }
                ?: "Choose a section occurrence before previewing a style.",
            style = MaterialTheme.typography.bodyMedium,
        )
        MidiCoreArrangementStyleCatalog.styles.forEach { style ->
            val selected = state.stylePreview.selectedStyleId == style.id && state.stylePreview.occurrenceId == occurrence?.id
            OutlinedButton(
                onClick = { onPreview(style) },
                enabled = canPreview,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.style(style.id)
                    this.selected = selected
                    contentDescription = "Preview ${style.displayName} style${if (selected) ", selected" else ""}"
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(style.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(style.summary, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                }
            }
        }
        state.stylePreview.cacheStatus?.let { cache ->
            Text(
                if (cache == app.melotrail.application.MidiCoreArrangementStylePreviewCacheStatus.WARM) "Preview ready from this session's MIDI cache."
                else "Preview generated from the current protected melody and authority.",
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
            )
        }
    }
}

@Composable
private fun ArrangeScopeCard(
    role: CandidateRole,
    occurrenceId: String?,
    occurrences: List<ProjectSectionOccurrence>,
    enabled: Boolean,
    onRoleSelected: (CandidateRole) -> Unit,
    onOccurrenceSelected: (String) -> Unit,
) {
    var occurrenceMenuOpen by remember { mutableStateOf(false) }
    val occurrence = occurrences.singleOrNull { it.id == occurrenceId } ?: occurrences.first()
    ArrangeCard(MidiCoreArrangePageTags.SCOPE, "2. Targeted correction") {
        Text("Choose a section and role only when a specific part needs an alternative. Style previews always include all three generated roles.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        Box {
            OutlinedButton(
                onClick = { occurrenceMenuOpen = true },
                enabled = enabled,
                colors = workspaceSelectableButtonColors(true),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.OCCURRENCE_MENU
                    contentDescription = "Section ${occurrence.label}; open section choices"
                },
            ) { Text("Section · ${occurrence.label}") }
            DropdownMenu(expanded = occurrenceMenuOpen, onDismissRequest = { occurrenceMenuOpen = false }) {
                occurrences.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { occurrenceMenuOpen = false; onOccurrenceSelected(option.id) },
                        modifier = Modifier.semantics {
                            testTag = MidiCoreArrangePageTags.occurrence(option.id)
                            selected = option.id == occurrenceId
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            midiCoreArrangementRoleOrder.forEach { option ->
                val selected = option == role
                OutlinedButton(
                    onClick = { onRoleSelected(option) },
                    enabled = enabled,
                    colors = workspaceSelectableButtonColors(selected),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.role(option)
                        this.selected = selected
                        contentDescription = "${option.displayName}${if (selected) ", selected" else ""}"
                    },
                ) { Text(option.displayName) }
            }
        }
    }
}

/** Profile and pattern controls are intentionally secondary to style selection. */
@Composable
private fun ArrangeAdvancedRoleAdjustment(
    open: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.ADVANCED, "Advanced role adjustment") {
        Text("Create a scoped alternative only when the selected style needs a local correction.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onOpenChanged(!open) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                contentDescription = if (open) "Hide advanced role adjustment" else "Show advanced role adjustment"
            },
        ) { Text(if (open) "Hide role controls" else "Adjust one role") }
        if (open) content()
    }
}

@Composable
private fun ArrangeFeelCard(
    role: CandidateRole,
    profileId: String,
    patternId: String,
    enabled: Boolean,
    onProfileSelected: (String) -> Unit,
    onPatternSelected: (String) -> Unit,
) {
    val profiles = MidiCorePerformanceProfileCatalog.profiles.filter { it.role == role }
    val patterns = MidiCorePatternCatalog.inventory().filter { it.role == role }
    ArrangeCard("midi-core-arrange-feel", "Choose a role feel") {
        Text("Performance controls note shape; rhythm controls where notes are played.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        ArrangeDropdown(
            tag = MidiCoreArrangePageTags.PROFILE_MENU,
            label = "Performance",
            selectedLabel = friendlyToken(profileId),
            options = profiles.map { it.id to friendlyToken(it.id) },
            enabled = enabled,
            optionTag = MidiCoreArrangePageTags::profile,
            onSelected = onProfileSelected,
        )
        ArrangeDropdown(
            tag = MidiCoreArrangePageTags.PATTERN_MENU,
            label = "Rhythm",
            selectedLabel = patterns.singleOrNull { it.id == patternId }?.displayName ?: friendlyToken(patternId),
            options = patterns.map { it.id to it.displayName },
            enabled = enabled,
            optionTag = MidiCoreArrangePageTags::pattern,
            onSelected = onPatternSelected,
        )
    }
}

@Composable
private fun ArrangeDropdown(
    tag: String,
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    optionTag: (String) -> String,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            colors = workspaceSelectableButtonColors(true),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = tag
                contentDescription = "$label $selectedLabel; open choices"
            },
        ) { Text("$label · $selectedLabel") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = { open = false; onSelected(id) },
                    modifier = Modifier.semantics { testTag = optionTag(id) },
                )
            }
        }
    }
}

@Composable
private fun ArrangeGenerateCard(
    state: MidiCoreWorkspaceState,
    role: CandidateRole,
    occurrenceLabel: String,
    ready: Boolean,
    nextSeed: Long,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    val generating = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION
    ArrangeCard(MidiCoreArrangePageTags.STATUS, "3. Create an alternative") {
        Text("This creates alternative #$nextSeed. Existing alternatives and accepted work stay untouched.", style = MaterialTheme.typography.bodyMedium)
        if (generating) {
            Text(state.operation.message, style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.Information)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.CANCEL
                    contentDescription = "Cancel candidate generation"
                },
            ) { Text("Cancel") }
        } else {
            Button(
                onClick = onGenerate,
                enabled = ready && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.GENERATE
                    contentDescription = "Generate ${role.displayName} alternative for $occurrenceLabel"
                },
            ) { Text("Generate ${role.displayName} for $occurrenceLabel") }
            if (state.operation.kind == MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION || state.operation.kind == MidiCoreWorkspaceOperationKind.CANDIDATE_REVIEW) {
                Text(
                    state.operation.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.operation.outcome == MidiCoreWorkspaceOperationOutcome.FAILURE) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Success,
                )
            }
        }
    }
}

@Composable
private fun ArrangeCandidateSummary(
    state: MidiCoreWorkspaceState,
    role: CandidateRole,
    occurrence: ProjectSectionOccurrence?,
    candidates: List<MidiCoreCandidateReviewItem>,
    onReview: () -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.CANDIDATES, "Ready to review") {
        if (candidates.isEmpty()) {
            Text("No alternatives yet for ${occurrence?.label.orEmpty()} · ${role.displayName}.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        } else {
            Text("${candidates.size} alternative${if (candidates.size == 1) "" else "s"} available", style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.Success)
            candidates.takeLast(3).reversed().forEachIndexed { index, item ->
                val report = item.validation
                Card(
                    Modifier.fillMaxWidth().semantics {
                        testTag = MidiCoreArrangePageTags.candidate(item.candidate.id)
                        contentDescription = "Alternative ${candidates.size - index}: ${item.candidate.status.name.lowercase()}, ${report.noteCount} notes"
                    },
                    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                        Text("Alternative ${candidates.size - index}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${friendlyToken(item.candidate.patternId)} · ${report.noteCount} notes · ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} notes to review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MusicWorkspaceTokens.TextSecondary,
                        )
                        if (item.accepted) Text("Accepted${if (item.locked) " and locked" else ""}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Success)
                        if (!item.authorityCurrent || item.candidate.status == MidiCoreCandidateStatus.STALE) Text("Needs regeneration after an authority change", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                    }
                }
            }
        }
        Button(
            onClick = onReview,
            enabled = !state.busy && occurrence != null && candidates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.REVIEW
                contentDescription = "Listen to and choose from the generated alternatives"
            },
        ) { Text("Listen and choose") }
        if (candidates.isEmpty()) Text("Generate an alternative to continue.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
    }
}

@Composable
private fun ArrangeCard(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) {
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

private fun generationIntent(
    role: CandidateRole,
    occurrenceId: String,
    profileId: String,
    patternId: String,
    seed: Long,
): MidiCoreWorkspaceIntent.GenerateCandidate = MidiCoreWorkspaceIntent.GenerateCandidate(
    role = role,
    occurrenceId = occurrenceId,
    performanceProfileId = profileId,
    patternId = patternId,
    generator = MidiCoreGeneratorInput(
        generatorId = "midi-core-desktop",
        generatorVersion = "midi-core-v1",
        patternId = patternId,
        seed = seed,
    ),
)

internal fun friendlyToken(value: String): String = value.substringAfterLast('.').replace('-', ' ').replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

internal val CandidateRole.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)
