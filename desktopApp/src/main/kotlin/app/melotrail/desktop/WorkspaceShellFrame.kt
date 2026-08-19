package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object WorkspaceShellTags {
    const val ROOT = "workspace-shell"
    const val WIDE_NAVIGATION = "workspace-navigation-wide"
    const val MEDIUM_NAVIGATION = "workspace-navigation-medium"
    const val NARROW_NAVIGATION = "workspace-navigation-narrow"
    const val PROJECT_RAIL = "workspace-project-rail"
    const val CONTEXT_RAIL = "workspace-context-rail"
    const val CONTEXT_TOGGLE = "workspace-context-toggle"
    const val OVERFLOW_MENU = "workspace-overflow-menu"
    const val ARTWORK = "workspace-local-artwork"
}

private enum class SharedShellLayout { WIDE, MEDIUM, NARROW }

private fun sharedShellLayout(width: Dp): SharedShellLayout = when {
    width >= MusicWorkspaceTokens.Reference.WideBreakpoint -> SharedShellLayout.WIDE
    width >= MusicWorkspaceTokens.Reference.NarrowBreakpoint -> SharedShellLayout.MEDIUM
    else -> SharedShellLayout.NARROW
}

/** The active desktop frame owns only responsive presentation and navigation. */
@Composable
internal fun WorkspaceShellFrame(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().semantics { testTag = WorkspaceShellTags.ROOT }) {
        when (sharedShellLayout(maxWidth)) {
            SharedShellLayout.WIDE -> WideShell(state, onIntent, partDetailsFocusTargets)
            SharedShellLayout.MEDIUM -> MediumShell(state, onIntent, partDetailsFocusTargets)
            SharedShellLayout.NARROW -> NarrowShell(state, onIntent, partDetailsFocusTargets)
        }
    }
}

@Composable
private fun WideShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, focusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
        TopBar(state, onIntent, WorkspaceShellTags.WIDE_NAVIGATION)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
            ProjectRail(state, onIntent, Modifier.width(MusicWorkspaceTokens.Shell.ProjectRailWidth).fillMaxHeight(), includeNavigation = false)
            WorkspacePageRouter(state, onIntent, Modifier.weight(1f).fillMaxHeight(), focusTargets)
            // Overview already supplies the reference page's preview/context rail.
            // Keeping the generic shell rail there would compress its canonical
            // track overview and create a second, empty source of page context.
            if (state.workspaceSection != WorkspaceSection.OVERVIEW) {
                ContextRail(state, onIntent, Modifier.width(MusicWorkspaceTokens.Shell.ContextRailWidth).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MediumShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, focusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>) {
    var contextExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
        TopBar(state, onIntent, null)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
            ProjectRail(state, onIntent, Modifier.width(MusicWorkspaceTokens.Shell.CompactProjectRailWidth).fillMaxHeight(), includeNavigation = true)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                ContextToggle(contextExpanded) { contextExpanded = !contextExpanded }
                WorkspacePageRouter(state, onIntent, Modifier.weight(1f), focusTargets)
                if (contextExpanded) ContextRail(state, onIntent, Modifier.fillMaxWidth().heightIn(max = 184.dp))
            }
        }
    }
}

@Composable
private fun NarrowShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, focusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>) {
    var contextExpanded by remember { mutableStateOf(false) }
    val contextToggleFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            TopBar(state, onIntent, null)
            if (state.project != null) DestinationChooser(state, onIntent)
            ProjectRail(state, onIntent, Modifier.fillMaxWidth(), includeNavigation = false, compact = true)
            ContextToggle(contextExpanded, Modifier.focusRequester(contextToggleFocus)) { contextExpanded = !contextExpanded }
            WorkspacePageRouter(state, onIntent, Modifier.weight(1f), focusTargets)
            if (contextExpanded && state.workspaceSection != WorkspaceSection.IMPORT) {
                ContextRail(state, onIntent, Modifier.fillMaxWidth().heightIn(max = 160.dp))
            }
        }
        if (contextExpanded && state.workspaceSection == WorkspaceSection.IMPORT) {
            ImportContextSheet(state, onIntent) {
                contextExpanded = false
                contextToggleFocus.requestFocus()
            }
        }
    }
}

@Composable
private fun ContextToggle(expanded: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle, modifier = modifier.semantics {
        testTag = WorkspaceShellTags.CONTEXT_TOGGLE
        contentDescription = if (expanded) "Collapse page context" else "Expand page context"
    }) { Text(if (expanded) "Hide context" else "Show context") }
}

/** Narrow Import keeps preparation in an overlay sheet and leaves the single page root intact underneath. */
@Composable
private fun ImportContextSheet(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, onDismiss: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Card(
        Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else false
            }
            .semantics { testTag = WorkspaceShellTags.CONTEXT_RAIL; contentDescription = "Import preparation sheet" },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)
    ) {
        Column(Modifier.fillMaxSize().padding(MusicWorkspaceTokens.Spacing.Lg), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            ImportContextRail(state, onIntent)
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Return to Import") }
        }
    }
}

@Composable
private fun TopBar(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, navigationTag: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Shell.TopBarHeight).semantics { testTag = WorkspaceTags.PROJECT_HEADER },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Lg, vertical = MusicWorkspaceTokens.Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
        ) {
            BrandMark()
            if (navigationTag != null && state.project != null) {
                DestinationNavigation(state, onIntent, navigationTag, compact = true, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            HeaderProjectActions(state, onIntent)
            HeaderOverflow(onIntent)
        }
    }
}

/** Keeps first-project actions visible while secondary destinations remain keyboard reachable. */
@Composable
private fun HeaderProjectActions(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val enabled = !state.operation.isMutating
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Button(
            onClick = { onIntent(WorkspaceIntent.ShowCreateProject) },
            enabled = enabled,
            modifier = Modifier.semantics {
                testTag = WorkspaceTags.CREATE_PROJECT
                contentDescription = "New Project"
            }
        ) { Text("New Project") }
        OutlinedButton(
            onClick = { onIntent(WorkspaceIntent.ChooseProject) },
            enabled = enabled,
            modifier = Modifier.semantics {
                testTag = WorkspaceTags.OPEN_PROJECT
                contentDescription = "Open Project"
            }
        ) { Text("Open Project") }
    }
}

/** Offers secondary destinations without presenting them as primary workflow navigation. */
@Composable
private fun HeaderOverflow(onIntent: (WorkspaceIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics {
                testTag = WorkspaceShellTags.OVERFLOW_MENU
                contentDescription = "More workspace destinations"
            }
        ) { Text("More") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(WorkspaceSection.LIBRARY, WorkspaceSection.VIDEO_PREVIEW, WorkspaceSection.EXPORT).forEach { destination ->
                DropdownMenuItem(
                    text = { Text(destination.label) },
                    onClick = {
                        expanded = false
                        onIntent(WorkspaceIntent.SelectWorkspaceSection(destination))
                    },
                    modifier = Modifier.semantics {
                        testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + destination.name.lowercase()
                        contentDescription = "Open ${destination.label}"
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    onIntent(WorkspaceIntent.OpenSettings)
                },
                modifier = Modifier.semantics {
                    testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.SETTINGS.name.lowercase()
                    contentDescription = "Open Settings"
                }
            )
        }
    }
}

@Composable
private fun BrandMark() = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text("Melotrail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DestinationChooser(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.WORKSPACE_NAV; contentDescription = "Workspace destination chooser" }) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics {
            testTag = WorkspacePageTags.NAVIGATION_MENU
            contentDescription = "Choose workspace page. Current page: ${state.workspaceSection.label}."
        }) { Text(state.workspaceSection.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkspaceSection.entries.forEach { destination ->
                DropdownMenuItem(text = { Text(destination.label) }, onClick = {
                    expanded = false
                    onIntent(WorkspaceIntent.SelectWorkspaceSection(destination))
                }, modifier = Modifier.semantics {
                    testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + destination.name.lowercase()
                    selected = destination == state.workspaceSection
                    contentDescription = "Open ${destination.label}${if (destination == state.workspaceSection) ", selected" else ""}"
                })
            }
        }
    }
}

@Composable
private fun ProjectRail(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier, includeNavigation: Boolean, compact: Boolean = false) {
    Card(modifier.semantics { testTag = WorkspaceShellTags.PROJECT_RAIL }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            Text("PROJECT", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
            Text(state.project?.name ?: "No project open", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(projectRailStatus(state), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            if (includeNavigation && state.project != null) DestinationNavigation(state, onIntent, WorkspaceShellTags.MEDIUM_NAVIGATION, compact = false)
            if (!compact && state.project != null) LocalArtworkSlot()
        }
    }
}

private fun projectRailStatus(state: WorkspaceUiState): String = when {
    state.project == null -> "Start with New Project, or Open Project to continue an existing project."
    state.downstreamArtifactsStale -> "Some derived artifacts are stale."
    state.project.readiness.releaseAvailable -> "Validated release available."
    else -> "Stage: ${state.workspaceSection.label}"
}

@Composable
private fun DestinationNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, tag: String, compact: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.semantics { testTag = tag }) {
        val navigationModifier = Modifier.semantics { testTag = WorkspaceTags.WORKSPACE_NAV; contentDescription = "Workspace navigation" }
        if (compact) Row(navigationModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            listOf(WorkspaceSection.SETUP, WorkspaceSection.HARMONY, WorkspaceSection.OVERVIEW, WorkspaceSection.IMPORT, WorkspaceSection.STRUCTURE, WorkspaceSection.ARRANGE, WorkspaceSection.MIX_MASTER)
                .forEach { destination -> NavigationButton(destination, state, onIntent, compact = true) }
        } else Column(navigationModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            listOf(WorkspaceSection.SETUP, WorkspaceSection.HARMONY, WorkspaceSection.OVERVIEW, WorkspaceSection.IMPORT, WorkspaceSection.STRUCTURE, WorkspaceSection.ARRANGE, WorkspaceSection.MIX_MASTER)
                .forEach { destination -> NavigationButton(destination, state, onIntent, compact = false) }
        }
    }
}

@Composable
private fun NavigationButton(destination: WorkspaceSection, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, compact: Boolean) {
    val selected = destination == state.workspaceSection
    OutlinedButton(
        onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(destination)) },
        modifier = (if (compact) Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget) else Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)).semantics {
            testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + destination.name.lowercase()
            this.selected = selected
            contentDescription = "Open ${destination.label}${if (selected) ", selected" else ""}"
        },
        colors = workspaceSelectableButtonColors(selected)
    ) { Text(if (compact) destination.shortLabel else destination.navigationLabel) }
}

private val WorkspaceSection.shortLabel: String
    get() = when (this) {
        WorkspaceSection.SETUP -> "Setup"
        WorkspaceSection.HARMONY -> "Harmony"
        WorkspaceSection.OVERVIEW -> "Project"
        WorkspaceSection.MIX_MASTER -> "Mix"
        WorkspaceSection.VIDEO_PREVIEW -> "Preview"
        else -> label
    }

private val WorkspaceSection.navigationLabel: String
    get() = when (this) {
        WorkspaceSection.OVERVIEW -> "Project"
        else -> label
    }

@Composable
private fun LocalArtworkSlot() = Box(
    Modifier.fillMaxWidth().heightIn(min = 132.dp).clip(MaterialTheme.shapes.medium)
        .background(Brush.linearGradient(listOf(MusicWorkspaceTokens.ScenePlaceholder, MusicWorkspaceTokens.SelectedSurface)))
        .semantics { testTag = WorkspaceShellTags.ARTWORK; contentDescription = "Deterministic local artwork placeholder" },
    contentAlignment = Alignment.Center
) { Text("LOCAL VISUAL\nPLACEHOLDER", style = MaterialTheme.typography.labelMedium, color = MusicWorkspaceTokens.TextSecondary) }

@Composable
private fun ContextRail(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) {
    Card(modifier.semantics { testTag = WorkspaceShellTags.CONTEXT_RAIL; contentDescription = "${state.workspaceSection.label} page context" }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            when (state.workspaceSection) {
                WorkspaceSection.IMPORT -> ImportContextRail(state, onIntent)
                WorkspaceSection.HARMONY -> {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("HARMONY READINESS", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
                    Text(if (state.harmony.view?.ready == true) "Verse, Chorus, and Bridge are complete. Continue to Melody Parts." else "Complete Setup, then add a chord to every required progression.", style = MaterialTheme.typography.bodySmall)
                }
                WorkspaceSection.ARRANGE -> {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    ArrangeContextRail(state, onIntent)
                }
                WorkspaceSection.MIX_MASTER -> {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("PREVIEW & BUILD", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
                    val source = (state.playbackSession.request as? PlaybackRequest.Mix)?.source ?: PlaybackSource.DRY
                    Text("Shared source: ${source.name.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            state.downstreamArtifactsStale -> "Mix artifacts are stale. Regenerate them from the current arrangement."
                            state.project?.readiness?.masterAvailable == true -> "A validated master is available for shared playback."
                            else -> "Build Song validates and publishes the lossless master before it is available."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MusicWorkspaceTokens.TextSecondary
                    )
                }
                else -> {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("${state.workspaceSection.label} context", style = MaterialTheme.typography.titleMedium)
                    Text(contextDescription(state), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                }
            }
        }
    }
}

private fun contextDescription(state: WorkspaceUiState): String = state.selectedPartId?.let { "Selected part: $it" }
    ?: state.selectedArrangementSection?.let { "Selected section: ${it + 1}" }
    ?: "Select a part or section to reveal its current validated details."
