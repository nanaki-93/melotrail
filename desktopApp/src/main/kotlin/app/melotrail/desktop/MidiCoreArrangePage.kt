package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import app.melotrail.arrangement.core.MidiCorePatternCatalog
import app.melotrail.arrangement.core.MidiCorePatternInventoryEntry
import app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog
import app.melotrail.arrangement.core.MidiCoreRoleFindingSeverity
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreGeneratorInput

/** Stable semantic anchors for the focused, deterministic arrangement page. */
internal object MidiCoreArrangePageTags {
    const val ROOT = "midi-core-arrange-page"
    const val EMPTY = "midi-core-arrange-empty"
    const val SCOPE = "midi-core-arrange-scope"
    const val ROLE_PREFIX = "midi-core-arrange-role-"
    const val OCCURRENCE_PREFIX = "midi-core-arrange-occurrence-"
    const val PROFILE_PREFIX = "midi-core-arrange-profile-"
    const val PATTERN_PREFIX = "midi-core-arrange-pattern-"
    const val SEED = "midi-core-arrange-seed"
    const val GENERATE = "midi-core-arrange-generate"
    const val ALTERNATIVE = "midi-core-arrange-alternative"
    const val REGENERATE = "midi-core-arrange-regenerate"
    const val LOAD_CANDIDATES = "midi-core-arrange-load-candidates"
    const val CANDIDATES = "midi-core-arrange-candidates"
    const val CANDIDATE_PREFIX = "midi-core-arrange-candidate-"
    const val PROGRESS = "midi-core-arrange-progress"
    const val CANCEL = "midi-core-arrange-cancel"
    const val REVIEW = "midi-core-arrange-open-review"
    const val BLOCKERS = "midi-core-arrange-blockers"

    fun role(role: CandidateRole): String = ROLE_PREFIX + role.name.lowercase()
    fun occurrence(id: String): String = OCCURRENCE_PREFIX + id
    fun profile(id: String): String = PROFILE_PREFIX + id
    fun pattern(id: String): String = PATTERN_PREFIX + id
    fun candidate(id: String): String = CANDIDATE_PREFIX + id
}

/** Scoped candidate generation and existing-candidate evidence for the Arrange destination. */
@Composable
internal fun MidiCoreArrangePage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val authority = state.project?.authority
    val occurrences = authority?.occurrences.orEmpty()
    var role by remember(state.projectRevision) { mutableStateOf(state.review.role ?: CandidateRole.CHORDS) }
    var occurrenceId by remember(state.projectRevision) { mutableStateOf(state.review.occurrenceId ?: occurrences.firstOrNull()?.id) }
    var profileId by remember(state.projectRevision, role) {
        mutableStateOf(MidiCorePerformanceProfileCatalog.allowedProfileIds(role).firstOrNull().orEmpty())
    }
    var patternId by remember(state.projectRevision, role) {
        mutableStateOf(MidiCorePatternCatalog.allowedPatternIds(role).firstOrNull().orEmpty())
    }
    var seedText by remember(state.projectRevision, role, occurrenceId) { mutableStateOf("1") }
    val seed = seedText.toLongOrNull()
    val generationReady = !state.busy && occurrenceId != null && profileId.isNotBlank() && patternId.isNotBlank() && seed != null

    Column(
        modifier.semantics {
            testTag = MidiCoreArrangePageTags.ROOT
            contentDescription = "Arrange deterministic MIDI candidates"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "CREATE",
            title = "Arrange",
            summary = "Choose a section and role, then generate repeatable MIDI alternatives.",
        )
        if (state.project == null || authority == null || occurrences.isEmpty() || authority.chordEvents.isEmpty()) {
            ArrangeEmptyState(state)
        } else {
            ArrangeScopeCard(
                role = role,
                occurrenceId = occurrenceId,
                occurrences = occurrences.map { it.id to it.label },
                enabled = !state.busy,
                onRoleSelected = { nextRole ->
                    role = nextRole
                    profileId = MidiCorePerformanceProfileCatalog.allowedProfileIds(nextRole).first()
                    patternId = MidiCorePatternCatalog.allowedPatternIds(nextRole).first()
                    onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(nextRole, occurrenceId ?: occurrences.first().id))
                    onIntent(MidiCoreWorkspaceIntent.LoadCandidates(nextRole, occurrenceId ?: occurrences.first().id))
                },
                onOccurrenceSelected = { nextOccurrenceId ->
                    occurrenceId = nextOccurrenceId
                    onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, nextOccurrenceId))
                    onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, nextOccurrenceId))
                },
            )
            ArrangeChoicesCard(
                role = role,
                profileId = profileId,
                patternId = patternId,
                enabled = !state.busy,
                onProfileSelected = { profileId = it },
                onPatternSelected = { patternId = it },
            )
            ArrangeGenerationCard(
                seedText = seedText,
                seedValid = seed != null,
                generationReady = generationReady,
                isGenerating = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.CANDIDATE_GENERATION,
                operationMessage = state.operation.message,
                onSeedChanged = { seedText = it },
                onGenerate = {
                    val request = generationIntent(role, requireNotNull(occurrenceId), profileId, patternId, requireNotNull(seed), regenerate = false)
                    onIntent(request)
                },
                onAlternative = {
                    val nextSeed = requireNotNull(seed).plus(1L)
                    seedText = nextSeed.toString()
                    onIntent(generationIntent(role, requireNotNull(occurrenceId), profileId, patternId, nextSeed, regenerate = false))
                },
                onRegenerate = {
                    onIntent(generationIntent(role, requireNotNull(occurrenceId), profileId, patternId, requireNotNull(seed), regenerate = true))
                },
                onCancel = { onIntent(MidiCoreWorkspaceIntent.CancelOperation) },
            )
            ArrangeCandidateEvidenceCard(
                state = state,
                role = role,
                occurrenceId = occurrenceId,
                onLoad = { onIntent(MidiCoreWorkspaceIntent.LoadCandidates(role, requireNotNull(occurrenceId))) },
                onReview = {
                    onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(role, requireNotNull(occurrenceId)))
                    onNavigate(MidiCoreWorkspaceDestination.REVIEW)
                },
            )
        }
    }
}

@Composable
private fun ArrangeEmptyState(state: MidiCoreWorkspaceState) {
    ArrangeCard(MidiCoreArrangePageTags.EMPTY, "Arrange") {
        Text("Generation becomes available after the protected melody, complete section timeline, and authoritative chord windows are saved.", style = MaterialTheme.typography.bodyLarge)
        if (state.blockers.isEmpty()) {
            Text("Open a MIDI Core project to begin.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        } else {
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreArrangePageTags.BLOCKERS
                    contentDescription = "${state.blockers.size} generation blocker${if (state.blockers.size == 1) "" else "s"}"
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
private fun ArrangeScopeCard(
    role: CandidateRole,
    occurrenceId: String?,
    occurrences: List<Pair<String, String>>,
    enabled: Boolean,
    onRoleSelected: (CandidateRole) -> Unit,
    onOccurrenceSelected: (String) -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.SCOPE, "Target scope") {
        Text("Generate one role for one exact occurrence. Existing candidate evidence is never replaced or accepted automatically.", style = MaterialTheme.typography.bodyMedium)
        Text("Role", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            CandidateRole.entries.forEach { option ->
                OutlinedButton(
                    onClick = { onRoleSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreArrangePageTags.role(option)
                        selected = option == role
                        contentDescription = "Select ${option.displayName} role${if (option == role) ". Selected." else ""}"
                    },
                ) { Text(option.displayName) }
            }
        }
        Text("Occurrence", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.TextSecondary)
        occurrences.forEach { (id, label) ->
            OutlinedButton(
                onClick = { onOccurrenceSelected(id) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.occurrence(id)
                    selected = id == occurrenceId
                    contentDescription = "Select occurrence $label${if (id == occurrenceId) ". Selected." else ""}"
                },
            ) { Text(label) }
        }
    }
}

@Composable
private fun ArrangeChoicesCard(
    role: CandidateRole,
    profileId: String,
    patternId: String,
    enabled: Boolean,
    onProfileSelected: (String) -> Unit,
    onPatternSelected: (String) -> Unit,
) {
    val profiles = MidiCorePerformanceProfileCatalog.profiles.filter { it.role == role }
    val patterns = MidiCorePatternCatalog.inventory().filter { it.role == role }
    ArrangeCard("midi-core-arrange-choices", "Allowed MIDI choices") {
        Text("Profiles express MIDI performance intent; patterns are the complete curated variants allowed for this role.", style = MaterialTheme.typography.bodyMedium)
        Text("Performance profile", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.TextSecondary)
        profiles.forEach { profile ->
            OutlinedButton(
                onClick = { onProfileSelected(profile.id) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.profile(profile.id)
                    selected = profile.id == profileId
                    contentDescription = "Select performance profile ${profile.id}${if (profile.id == profileId) ". Selected." else ""}"
                },
            ) { Text(profile.id) }
        }
        Text("Pattern", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.TextSecondary)
        patterns.forEach { pattern -> ArrangePatternChoice(pattern, patternId, enabled, onPatternSelected) }
    }
}

@Composable
private fun ArrangePatternChoice(
    pattern: MidiCorePatternInventoryEntry,
    selectedPatternId: String,
    enabled: Boolean,
    onPatternSelected: (String) -> Unit,
) {
    OutlinedButton(
        onClick = { onPatternSelected(pattern.id) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
            testTag = MidiCoreArrangePageTags.pattern(pattern.id)
            selected = pattern.id == selectedPatternId
            contentDescription = "Select pattern ${pattern.displayName}${if (pattern.id == selectedPatternId) ". Selected." else ""}"
        },
    ) { Text(pattern.displayName) }
}

@Composable
private fun ArrangeGenerationCard(
    seedText: String,
    seedValid: Boolean,
    generationReady: Boolean,
    isGenerating: Boolean,
    operationMessage: String,
    onSeedChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onAlternative: () -> Unit,
    onRegenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    ArrangeCard(MidiCoreArrangePageTags.PROGRESS, "Generate alternatives") {
        Text("The seed is recorded with each deterministic candidate. Change it to request a different alternative for this same scope.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = seedText,
            onValueChange = onSeedChanged,
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth().semantics { testTag = MidiCoreArrangePageTags.SEED },
            label = { Text("Deterministic seed") },
            supportingText = { Text(if (seedValid) "Same authority, settings, and seed reproduce this candidate." else "Enter a signed whole-number seed.") },
            singleLine = true,
        )
        if (isGenerating) {
            Text(operationMessage, style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Information)
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.CANCEL
                    contentDescription = "Cancel candidate generation"
                },
            ) { Text("Cancel generation") }
        } else {
            Button(
                onClick = onGenerate,
                enabled = generationReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.GENERATE
                    contentDescription = "Generate one scoped MIDI candidate"
                },
            ) { Text("Generate candidate") }
            OutlinedButton(
                onClick = onAlternative,
                enabled = generationReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.ALTERNATIVE
                    contentDescription = "Generate next deterministic alternative"
                },
            ) { Text("Generate next alternative") }
            OutlinedButton(
                onClick = onRegenerate,
                enabled = generationReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreArrangePageTags.REGENERATE
                    contentDescription = "Regenerate only this role and occurrence"
                },
            ) { Text("Regenerate this scope") }
        }
    }
}

@Composable
private fun ArrangeCandidateEvidenceCard(
    state: MidiCoreWorkspaceState,
    role: CandidateRole,
    occurrenceId: String?,
    onLoad: () -> Unit,
    onReview: () -> Unit,
) {
    val scopeMatches = state.review.role == role && state.review.occurrenceId == occurrenceId
    val candidates = if (scopeMatches) state.review.candidates else emptyList()
    ArrangeCard(MidiCoreArrangePageTags.CANDIDATES, "Candidate evidence") {
        Text("Candidate IDs, validation summaries, and lifecycle status remain inspectable. Approval happens in Review.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = onLoad,
            enabled = !state.busy && occurrenceId != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.LOAD_CANDIDATES
                contentDescription = "Load candidate evidence for the selected scope"
            },
        ) { Text("Refresh candidates") }
        if (!scopeMatches) {
            Text("Load candidate evidence for this selected scope.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        } else if (candidates.isEmpty()) {
            Text("No candidate has been published for this scope yet.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        } else {
            candidates.forEach { item ->
                val candidate = item.candidate
                val report = item.validation
                Card(
                    Modifier.fillMaxWidth().semantics {
                        testTag = MidiCoreArrangePageTags.candidate(candidate.id)
                        contentDescription = "Candidate ${candidate.id}: ${candidate.status.name.lowercase()}, ${report.noteCount} notes, ${report.blockers.size} blocking findings, ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} advisory findings"
                    },
                    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                        Text(candidate.id, style = MaterialTheme.typography.titleSmall)
                        Text("${candidate.status.name.lowercase().replaceFirstChar(Char::uppercaseChar)} · seed ${candidate.seed} · ${candidate.profileId} · ${candidate.patternId}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                        Text("Validation: ${report.noteCount} notes · ${report.blockers.size} blocking · ${report.findings.count { it.severity == MidiCoreRoleFindingSeverity.ADVISORY }} advisory", style = MaterialTheme.typography.bodySmall)
                        report.findings.take(3).forEach { finding -> Text(finding.message, style = MaterialTheme.typography.bodySmall, color = if (finding.severity == MidiCoreRoleFindingSeverity.BLOCKING) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.TextSecondary) }
                    }
                }
            }
        }
        Button(
            onClick = onReview,
            enabled = !state.busy && occurrenceId != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                testTag = MidiCoreArrangePageTags.REVIEW
                contentDescription = "Open Review for selected candidate scope"
            },
        ) { Text("Review candidates") }
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
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            },
        )
    }
}

private fun generationIntent(
    role: CandidateRole,
    occurrenceId: String,
    profileId: String,
    patternId: String,
    seed: Long,
    regenerate: Boolean,
): MidiCoreWorkspaceIntent {
    val generator = MidiCoreGeneratorInput(
        generatorId = "midi-core-desktop",
        generatorVersion = "midi-core-v1",
        patternId = patternId,
        seed = seed,
    )
    return if (regenerate) {
        MidiCoreWorkspaceIntent.RegenerateCandidate(role, occurrenceId, profileId, patternId, generator)
    } else {
        MidiCoreWorkspaceIntent.GenerateCandidate(role, occurrenceId, profileId, patternId, generator)
    }
}

private val CandidateRole.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)
