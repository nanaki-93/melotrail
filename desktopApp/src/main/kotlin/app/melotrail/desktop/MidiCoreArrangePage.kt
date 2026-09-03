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

/** Stable semantic anchors for the song-map arrangement workspace. */
internal object MidiCoreArrangePageTags {
    const val ROOT = "midi-core-arrange-page"
    const val EMPTY = "midi-core-arrange-empty"
    const val STYLES = "midi-core-arrange-styles"
    const val STYLE_PREFIX = "midi-core-arrange-style-"
    const val DRAFT = "midi-core-arrange-draft"
    const val CREATE_DRAFT = "midi-core-arrange-create-draft"
    const val CANCEL = "midi-core-arrange-cancel"
    const val RETRY_DRAFT = "midi-core-arrange-retry-draft"
    const val INSPECTOR = "midi-core-arrange-section-inspector"
    const val REGENERATE_SECTION = "midi-core-arrange-regenerate-section"
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
    val mapOccurrences = midiCoreSongMap(project)
    val selectedMapIndex = mapOccurrences.indexOfFirst { it.occurrence.id == selectedOccurrence.id }
    val selectedMapOccurrence = mapOccurrences.getValueAt(selectedMapIndex)
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
        MidiCoreSongMap(
            project = project,
            selectedOccurrenceId = selectedOccurrence.id,
            audition = state.audition,
            onOccurrenceSelected = { onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(it.occurrence.id)) },
        )
        ArrangeStyleGallery(state, selectedOccurrence) { style ->
            onIntent(MidiCoreWorkspaceIntent.PreviewArrangementStyle(style.id, selectedOccurrence.id))
        }
        ArrangeDraftAction(state, selectedOccurrence, onIntent)
        ArrangeSelectedSectionInspector(
            mapOccurrence = selectedMapOccurrence,
            previousOccurrence = mapOccurrences.getOrNull(selectedMapIndex - 1),
            nextOccurrence = mapOccurrences.getOrNull(selectedMapIndex + 1),
            styleId = state.stylePreview.selectedStyleId,
            enabled = !state.busy,
            onSelectOccurrence = { onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(it.occurrence.id)) },
            onRegenerate = { styleId -> onIntent(MidiCoreWorkspaceIntent.RegenerateArrangementSection(selectedOccurrence.id, styleId, state.arrangement.rootSeed)) },
        )
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
    occurrence: ProjectSectionOccurrence,
    onPreview: (MidiCoreArrangementStyle) -> Unit,
) {
    val previewBusy = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.AUDITION &&
        state.operation.retry is MidiCoreWorkspaceIntent.PreviewArrangementStyle
    ArrangeCard(MidiCoreArrangePageTags.STYLES, "Choose a direction") {
        Text("A style previews ${occurrence.label} as a short all-role MIDI loop. It does not save candidates.", color = MusicWorkspaceTokens.TextSecondary)
        MidiCoreArrangementStyleCatalog.styles.forEach { style ->
            val selected = state.stylePreview.selectedStyleId == style.id
            OutlinedButton(
                onClick = { onPreview(style) }, enabled = !state.busy || previewBusy,
                colors = workspaceSelectableButtonColors(selected),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.style(style.id); this.selected = selected
                    contentDescription = "Preview ${style.displayName}${if (selected) ", selected" else ""} for ${occurrence.label}"
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(style.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(style.summary, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                }
            }
        }
        state.stylePreview.cacheStatus?.let { Text("Preview ${it.name.lowercase()} from the current MIDI session.", color = MusicWorkspaceTokens.TextSecondary) }
    }
}

@Composable
private fun ArrangeDraftAction(
    state: MidiCoreWorkspaceState,
    occurrence: ProjectSectionOccurrence,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val styleId = state.stylePreview.selectedStyleId
    val generating = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.DRAFT_GENERATION
    val retry = state.operation.retry as? MidiCoreWorkspaceIntent.CreateArrangementDraft
    ArrangeCard(MidiCoreArrangePageTags.DRAFT, "Create the complete draft") {
        Text(
            styleId?.let { "${friendlyToken(it)} will generate Chords, Bass, and Drums for every section. Existing immutable work is preserved." }
                ?: "Choose a style to preview it, then create a complete draft.",
            color = MusicWorkspaceTokens.TextSecondary,
        )
        when {
            generating -> {
                state.operation.progress?.let { Text("${it.completed} of ${it.total} scopes complete", color = MusicWorkspaceTokens.Information) }
                Text(state.operation.message, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.CancelOperation) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.CANCEL; contentDescription = "Cancel complete draft generation"
                    },
                ) { Text("Cancel draft") }
            }
            retry != null -> {
                Text(state.operation.message, color = MusicWorkspaceTokens.Warning)
                Button(
                    onClick = { onIntent(retry) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.RETRY_DRAFT; contentDescription = "Retry incomplete complete draft"
                    },
                ) { Text("Retry complete draft") }
            }
            else -> Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.CreateArrangementDraft(requireNotNull(styleId), state.arrangement.rootSeed)) },
                enabled = styleId != null && !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.CREATE_DRAFT
                    contentDescription = styleId?.let { "Create full $it arrangement draft from ${occurrence.label}" } ?: "Choose a style before creating a full arrangement draft"
                },
            ) { Text("Create full draft") }
        }
    }
}

@Composable
private fun ArrangeSelectedSectionInspector(
    mapOccurrence: MidiCoreSongMapOccurrence,
    previousOccurrence: MidiCoreSongMapOccurrence?,
    nextOccurrence: MidiCoreSongMapOccurrence?,
    styleId: String?,
    enabled: Boolean,
    onSelectOccurrence: (MidiCoreSongMapOccurrence) -> Unit,
    onRegenerate: (String) -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.INSPECTOR, "${mapOccurrence.displayLabel} · selected section") {
        Text("${mapOccurrence.barRange} · ${mapOccurrence.chordSummary}", color = MusicWorkspaceTokens.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = { previousOccurrence?.let(onSelectOccurrence) },
                enabled = previousOccurrence != null,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreSongMapTags.PREVIOUS
                    contentDescription = previousOccurrence?.let { "Select previous section, ${it.displayLabel}" } ?: "No previous section"
                },
            ) { Text("Previous") }
            OutlinedButton(
                onClick = { nextOccurrence?.let(onSelectOccurrence) },
                enabled = nextOccurrence != null,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreSongMapTags.NEXT
                    contentDescription = nextOccurrence?.let { "Select next section, ${it.displayLabel}" } ?: "No next section"
                },
            ) { Text("Next") }
        }
        mapOccurrence.roleStates.forEach { (role, state) -> Text("${role.displayName}: ${state.label}") }
        Button(
            onClick = { onRegenerate(requireNotNull(styleId)) }, enabled = enabled && styleId != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.REGENERATE_SECTION
                contentDescription = if (styleId == null) "Choose a style before regenerating ${mapOccurrence.displayLabel}" else "Regenerate ${mapOccurrence.displayLabel} with the selected style"
            },
        ) { Text("Regenerate section") }
        if (styleId == null) Text("Choose a style before repairing this section.", color = MusicWorkspaceTokens.TextSecondary)
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
private fun ArrangeCard(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().semantics { testTag = tag }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun generationIntent(role: CandidateRole, occurrenceId: String, profileId: String, patternId: String, seed: Long) =
    MidiCoreWorkspaceIntent.GenerateCandidate(role, occurrenceId, profileId, patternId, MidiCoreGeneratorInput("midi-core-desktop", "midi-core-v1", patternId, seed))

internal fun friendlyToken(value: String): String = value.substringAfterLast('.').replace('-', ' ').replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

internal val CandidateRole.displayName: String get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)
