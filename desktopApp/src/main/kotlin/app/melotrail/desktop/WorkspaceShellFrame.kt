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
import app.melotrail.application.WorkflowStage
import app.melotrail.application.WorkflowStageStatus

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
    const val WORKER_STATUS = "workspace-worker-status"
    const val PIPELINE_STATUS = "workspace-pipeline-status"
    const val DESTINATION_PREFIX = "workflow-destination-"
}

private enum class SharedShellLayout { WIDE, MEDIUM, NARROW }

/**
 * The shell speaks in musician-facing workflow destinations.  Individual
 * implementation pages remain routable for their focused milestones, but
 * are not presented as the primary product workflow.
 */
internal enum class WorkspaceDestination(
    val label: String,
    val route: WorkspaceSection,
    val stages: List<WorkflowStage>
) {
    PROJECT("Project", WorkspaceSection.OVERVIEW, listOf(WorkflowStage.PROJECT)),
    SOURCE(
        "Source",
        WorkspaceSection.IMPORT,
        listOf(
            WorkflowStage.IMPORT_AND_INSPECTION,
            WorkflowStage.TRANSCRIPTION,
            WorkflowStage.CLEAN_MIDI,
            WorkflowStage.AI_FIX,
            WorkflowStage.MIDI_FEEL,
            WorkflowStage.ANALYSIS
        )
    ),
    STRUCTURE("Structure", WorkspaceSection.STRUCTURE, listOf(WorkflowStage.STRUCTURE)),
    ARRANGE(
        "Arrange",
        WorkspaceSection.ARRANGE,
        listOf(
            WorkflowStage.ARRANGEMENT,
            WorkflowStage.GENERATED_MIDI,
            WorkflowStage.COHESION,
            WorkflowStage.CRITIC,
            WorkflowStage.FULL_SONG_ENHANCE,
            WorkflowStage.HUMANIZATION
        )
    ),
    MIX("Mix", WorkspaceSection.MIX_MASTER, listOf(WorkflowStage.RENDER, WorkflowStage.MIX, WorkflowStage.MASTER)),
    RELEASE("Release", WorkspaceSection.EXPORT, listOf(WorkflowStage.COMMERCIAL_EXPORT))
}

internal val primaryWorkspaceDestinations = WorkspaceDestination.entries

private fun WorkspaceUiState.selectedDestination(): WorkspaceDestination = when (workspaceSection) {
    WorkspaceSection.SETUP, WorkspaceSection.HARMONY, WorkspaceSection.OVERVIEW -> WorkspaceDestination.PROJECT
    WorkspaceSection.IMPORT -> WorkspaceDestination.SOURCE
    WorkspaceSection.STRUCTURE -> WorkspaceDestination.STRUCTURE
    WorkspaceSection.ARRANGE -> WorkspaceDestination.ARRANGE
    WorkspaceSection.MIX_MASTER -> WorkspaceDestination.MIX
    WorkspaceSection.EXPORT -> WorkspaceDestination.RELEASE
    else -> WorkspaceDestination.PROJECT
}

private fun WorkspaceUiState.destinationStep(destination: WorkspaceDestination) =
    destination.stages.map { workflow[it] }.firstOrNull { step ->
        step.status !in setOf(WorkflowStageStatus.COMPLETE, WorkflowStageStatus.APPROVED)
    } ?: workflow[destination.stages.last()]

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
            ShellStatus(state)
            HeaderProjectActions(state, onIntent)
            HeaderOverflow(onIntent)
        }
    }
}

/** The worker and pipeline lifecycle come from typed readiness/workflow snapshots, never file probes. */
@Composable
private fun ShellStatus(state: WorkspaceUiState) {
    val worker = state.runtimeReadiness?.worker
    val workerText = when {
        worker == null -> "Worker checking"
        worker.available -> "Worker ready"
        else -> "Worker unavailable"
    }
    val current = state.workflow.current
    val pipelineText = if (state.project == null) "Pipeline: create or open a project" else
        "Pipeline: ${current.stage.workflowLabel()} · ${workflowStatusLabel(current)}"
    Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text(
            workerText,
            style = MaterialTheme.typography.labelSmall,
            color = if (worker?.available == true) MusicWorkspaceTokens.Success else MusicWorkspaceTokens.Warning,
            modifier = Modifier.semantics {
                testTag = WorkspaceShellTags.WORKER_STATUS
                contentDescription = "$workerText. ${worker?.detail ?: "Local runtime readiness is loading."}"
            }
        )
        Text(
            pipelineText,
            style = MaterialTheme.typography.labelSmall,
            color = workflowStatusColor(current.status),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics {
                testTag = WorkspaceShellTags.PIPELINE_STATUS
                contentDescription = pipelineText
            }
        )
    }
}

private fun WorkflowStage.workflowLabel(): String = when (this) {
    WorkflowStage.PROJECT -> "Project"
    WorkflowStage.IMPORT_AND_INSPECTION -> "Source import"
    WorkflowStage.TRANSCRIPTION -> "Transcription"
    WorkflowStage.CLEAN_MIDI -> "MIDI cleanup"
    WorkflowStage.AI_FIX -> "MIDI correction"
    WorkflowStage.MIDI_FEEL -> "MIDI feel"
    WorkflowStage.ANALYSIS -> "Source analysis"
    WorkflowStage.STRUCTURE -> "Structure"
    WorkflowStage.ARRANGEMENT -> "Arrangement"
    WorkflowStage.GENERATED_MIDI -> "Generated MIDI"
    WorkflowStage.COHESION -> "Cohesion"
    WorkflowStage.CRITIC -> "Critic"
    WorkflowStage.FULL_SONG_ENHANCE -> "Full-song enhance"
    WorkflowStage.HUMANIZATION -> "Humanization"
    WorkflowStage.RENDER -> "Stem render"
    WorkflowStage.MIX -> "Mix"
    WorkflowStage.MASTER -> "Master"
    WorkflowStage.COMMERCIAL_EXPORT -> "Release review"
}

private fun workflowStatusColor(status: WorkflowStageStatus) = when (status) {
    WorkflowStageStatus.COMPLETE, WorkflowStageStatus.APPROVED -> MusicWorkspaceTokens.Success
    WorkflowStageStatus.READY -> MusicWorkspaceTokens.Primary
    WorkflowStageStatus.RUNNING -> MusicWorkspaceTokens.Progress
    WorkflowStageStatus.REVIEW_REQUIRED -> MusicWorkspaceTokens.Warning
    WorkflowStageStatus.FAILED, WorkflowStageStatus.STALE -> MusicWorkspaceTokens.Error
    WorkflowStageStatus.LOCKED -> MusicWorkspaceTokens.Disabled
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
            listOf(WorkspaceSection.LIBRARY, WorkspaceSection.VIDEO_PREVIEW).forEach { destination ->
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
        }) { Text(state.selectedDestination().label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            primaryWorkspaceDestinations.forEach { destination ->
                val step = state.destinationStep(destination)
                DropdownMenuItem(text = { Text("${destination.label} · ${workflowStatusLabel(step)}") }, onClick = {
                    expanded = false
                    onIntent(WorkspaceIntent.SelectWorkspaceSection(destination.route))
                }, modifier = Modifier.semantics {
                    testTag = WorkspaceShellTags.DESTINATION_PREFIX + destination.name.lowercase()
                    selected = destination == state.selectedDestination()
                    contentDescription = "Open ${destination.label}. ${workflowStatusLabel(step)}${if (destination == state.selectedDestination()) ", selected" else ""}"
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
    state.downstreamArtifactsStale && state.workspaceSection == WorkspaceSection.IMPORT ->
        "Melody changes are current; later song artifacts need regeneration."
    state.downstreamArtifactsStale -> "Some derived artifacts are stale."
    state.project.readiness.releaseAvailable -> "Validated release available."
    else -> "Current stage: ${state.workflow.current.stage.workflowLabel()} · ${workflowStatusLabel(state.workflow.current)}"
}

@Composable
private fun DestinationNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, tag: String, compact: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.semantics { testTag = tag }) {
        val navigationModifier = Modifier.semantics { testTag = WorkspaceTags.WORKSPACE_NAV; contentDescription = "Workspace navigation" }
        if (compact) Row(navigationModifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            primaryWorkspaceDestinations.forEach { destination -> NavigationButton(destination, state, onIntent, compact = true) }
        } else Column(navigationModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            primaryWorkspaceDestinations.forEach { destination -> NavigationButton(destination, state, onIntent, compact = false) }
        }
    }
}

@Composable
private fun NavigationButton(destination: WorkspaceDestination, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, compact: Boolean) {
    val selected = destination == state.selectedDestination()
    val step = state.destinationStep(destination)
    val status = workflowStatusLabel(step)
    OutlinedButton(
        // Locked destinations remain inspectable; their visible typed status explains the prerequisite.
        onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(destination.route)) },
        modifier = (if (compact) Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget) else Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)).semantics {
            testTag = WorkspaceShellTags.DESTINATION_PREFIX + destination.name.lowercase()
            this.selected = selected
            contentDescription = "Open ${destination.label}. $status${if (selected) ", selected" else ""}"
        },
        colors = workspaceSelectableButtonColors(selected)
    ) {
        Text(if (compact) destination.label else "${destination.label}\n$status", maxLines = if (compact) 1 else 2)
    }
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
                    Text(if (state.harmony.view?.ready == true) "Required harmony is complete. You can now import Melody Parts or continue composing." else "Complete Setup, then add a chord to every required progression.", style = MaterialTheme.typography.bodySmall)
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
