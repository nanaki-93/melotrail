package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable semantic IDs for the target shell and its six reachable destinations. */
internal object MidiCoreWorkspaceShellTags {
    const val ROOT = "midi-core-workspace-shell"
    const val HEADER = "midi-core-workspace-header"
    const val CURRENT_PROJECT = "midi-core-current-project"
    const val OPERATION = "midi-core-workspace-operation"
    const val NAVIGATION = "midi-core-workspace-navigation"
    const val WIDE_LAYOUT = "midi-core-workspace-layout-wide"
    const val COMPACT_LAYOUT = "midi-core-workspace-layout-compact"
    const val WIDE_NAVIGATION = "midi-core-workspace-navigation-wide"
    const val COMPACT_NAVIGATION = "midi-core-workspace-navigation-compact"
    const val PAGE = "midi-core-workspace-page"
    const val BLOCKERS = "midi-core-workspace-blockers"
    const val DESTINATION_PREFIX = "midi-core-destination-"
    const val BLOCKER_PREFIX = "midi-core-blocker-"

    fun destination(destination: MidiCoreWorkspaceDestination): String = DESTINATION_PREFIX + destination.route
}

/** The only top-level pages reachable from the target desktop shell. */
internal enum class MidiCoreWorkspaceDestination(
    val route: String,
    val label: String,
    val summary: String,
) {
    PROJECT("project", "Project", "Create or reopen a MIDI Core project and inspect its current authority."),
    MIDI("midi", "MIDI", "Import one immutable Standard MIDI source and choose the protected melody."),
    STRUCTURE_HARMONY("structure-harmony", "Structure & Harmony", "Define the authoritative section timeline and chord windows."),
    ARRANGE("arrange", "Arrange", "Generate and regenerate deterministic Chords, Bass, and Drums candidates."),
    REVIEW("review", "Review", "Compare, accept, reject, lock, and restore candidate evidence."),
    EXPORT("export", "Export", "Publish a portable MIDI package for Logic Pro or GarageBand."),
}

internal val midiCoreWorkspaceDestinations: List<MidiCoreWorkspaceDestination> = MidiCoreWorkspaceDestination.entries

private enum class MidiCoreWorkspaceShellLayout { WIDE, COMPACT }

internal fun midiCoreWorkspaceShellLayout(width: Dp): String =
    if (width >= MusicWorkspaceTokens.Reference.WideBreakpoint) "wide" else "compact"

/** Collect the focused ViewModel state and render the target-only shell. */
@Composable
internal fun MidiCoreWorkspaceShell(
    workspace: MidiCoreWorkspaceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by workspace.state.collectAsState()
    MidiCoreWorkspaceShell(state, workspace::accept, modifier)
}

/** Render the shell from a stable state snapshot so its semantic tree is independently testable. */
@Composable
internal fun MidiCoreWorkspaceShell(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit = {},
    modifier: Modifier = Modifier,
    initialDestination: MidiCoreWorkspaceDestination = MidiCoreWorkspaceDestination.PROJECT,
) {
    var selectedDestination by remember(initialDestination) { mutableStateOf(initialDestination) }
    BoxWithConstraints(
        modifier.fillMaxSize().semantics {
            testTag = MidiCoreWorkspaceShellTags.ROOT
            contentDescription = "Melotrail MIDI Core workspace"
        },
    ) {
        val layout = when (midiCoreWorkspaceShellLayout(maxWidth)) {
            "wide" -> MidiCoreWorkspaceShellLayout.WIDE
            else -> MidiCoreWorkspaceShellLayout.COMPACT
        }
        Column(Modifier.fillMaxSize().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            MidiCoreWorkspaceHeader(state)
            when (layout) {
                MidiCoreWorkspaceShellLayout.WIDE -> Row(
                    Modifier.fillMaxSize().semantics { testTag = MidiCoreWorkspaceShellTags.WIDE_LAYOUT },
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
                ) {
                    MidiCoreWorkspaceNavigation(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { selectedDestination = it },
                        compact = false,
                        modifier = Modifier.width(224.dp).fillMaxHeight(),
                    )
                    MidiCoreWorkspacePage(
                        destination = selectedDestination,
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                MidiCoreWorkspaceShellLayout.COMPACT -> Column(
                    Modifier.fillMaxSize().semantics { testTag = MidiCoreWorkspaceShellTags.COMPACT_LAYOUT },
                    verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
                ) {
                    MidiCoreWorkspaceNavigation(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { selectedDestination = it },
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MidiCoreWorkspacePage(
                        destination = selectedDestination,
                        state = state,
                        onIntent = onIntent,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MidiCoreWorkspaceHeader(state: MidiCoreWorkspaceState) {
    Card(
        Modifier.fillMaxWidth().semantics { testTag = MidiCoreWorkspaceShellTags.HEADER },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Lg, vertical = MusicWorkspaceTokens.Spacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
        ) {
            Column {
                Text("Melotrail", style = MaterialTheme.typography.headlineSmall)
                Text("MIDI Core", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.Primary)
            }
            Column(
                Modifier.weight(1f).semantics {
                    testTag = MidiCoreWorkspaceShellTags.CURRENT_PROJECT
                    contentDescription = currentProjectDescription(state)
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                Text("Current project", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
                Text(
                    state.project?.metadata?.name ?: "No project open",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.projectRoot?.toString() ?: "Create or open a project to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MusicWorkspaceTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Card(
                Modifier.widthIn(min = 140.dp).semantics {
                    testTag = MidiCoreWorkspaceShellTags.OPERATION
                    contentDescription = state.operation.message
                },
                colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
            ) {
                Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm)) {
                    Text(operationLabel(state), style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
                    Text(state.operation.message, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun currentProjectDescription(state: MidiCoreWorkspaceState): String = state.project?.let {
    "Current project ${it.metadata.name}, revision ${it.revision}."
} ?: "No current project is open."

private fun operationLabel(state: MidiCoreWorkspaceState): String = when {
    state.operation.active -> "Operation in progress"
    state.operation.outcome == MidiCoreWorkspaceOperationOutcome.FAILURE -> "Action blocked"
    state.operation.outcome == MidiCoreWorkspaceOperationOutcome.CANCELLED -> "Operation cancelled"
    else -> "Workspace status"
}

@Composable
private fun MidiCoreWorkspaceNavigation(
    selectedDestination: MidiCoreWorkspaceDestination,
    onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    val navigationTag = if (compact) MidiCoreWorkspaceShellTags.COMPACT_NAVIGATION else MidiCoreWorkspaceShellTags.WIDE_NAVIGATION
    Card(
        modifier.semantics {
            testTag = navigationTag
            contentDescription = "MIDI Core navigation with six destinations"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        val navigationModifier = Modifier.semantics {
            testTag = MidiCoreWorkspaceShellTags.NAVIGATION
            contentDescription = "MIDI Core workspace navigation"
        }
        if (compact) {
            Row(
                navigationModifier.horizontalScroll(rememberScrollState()).padding(MusicWorkspaceTokens.Spacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                midiCoreWorkspaceDestinations.forEach { destination ->
                    MidiCoreDestinationButton(destination, selectedDestination, onDestinationSelected, compact = true)
                }
            }
        } else {
            Column(
                navigationModifier.padding(MusicWorkspaceTokens.Spacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                midiCoreWorkspaceDestinations.forEach { destination ->
                    MidiCoreDestinationButton(destination, selectedDestination, onDestinationSelected, compact = false)
                }
            }
        }
    }
}

@Composable
private fun MidiCoreDestinationButton(
    destination: MidiCoreWorkspaceDestination,
    selectedDestination: MidiCoreWorkspaceDestination,
    onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit,
    compact: Boolean,
) {
    val selected = destination == selectedDestination
    Button(
        onClick = { onDestinationSelected(destination) },
        modifier = (if (compact) Modifier.widthIn(min = 116.dp) else Modifier.fillMaxWidth())
            .heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
            .semantics {
                testTag = MidiCoreWorkspaceShellTags.destination(destination)
                this.selected = selected
                contentDescription = "Open ${destination.label}. ${destination.summary}${if (selected) " Selected." else ""}"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.Surface,
            contentColor = MusicWorkspaceTokens.TextPrimary,
        ),
    ) {
        Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MidiCoreWorkspacePage(
    destination: MidiCoreWorkspaceDestination,
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.semantics {
            testTag = MidiCoreWorkspaceShellTags.PAGE + "-" + destination.route
            contentDescription = "${destination.label} destination page"
        },
    ) {
        Card(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Xl),
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
            ) {
                Text(destination.label, style = MaterialTheme.typography.headlineMedium)
                Text(destination.summary, style = MaterialTheme.typography.bodyLarge, color = MusicWorkspaceTokens.TextSecondary)
                Text(
                    if (state.project == null) "This destination is unavailable until a MIDI Core project is open."
                    else "This destination is part of the current MIDI Core project.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.blockers.isEmpty()) {
                    Text("No current blockers.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Success)
                } else {
                    MidiCoreBlockers(state.blockers, onIntent)
                }
                state.notification?.let { notification ->
                    Text(notification, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Information)
                }
                Spacer(Modifier.heightIn(min = MusicWorkspaceTokens.Spacing.Xl))
            }
        }
    }
}

@Composable
private fun MidiCoreBlockers(blockers: List<MidiCoreWorkspaceBlocker>, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreWorkspaceShellTags.BLOCKERS
            contentDescription = "${blockers.size} actionable MIDI Core blocker${if (blockers.size == 1) "" else "s"}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Text("Action needed", style = MaterialTheme.typography.titleMedium)
            blockers.forEach { blocker ->
                Column(
                    Modifier.fillMaxWidth().semantics {
                        testTag = MidiCoreWorkspaceShellTags.BLOCKER_PREFIX + blocker.code.name.lowercase()
                        contentDescription = "${blocker.message} Next action: ${blocker.nextAction}"
                    },
                    verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
                ) {
                    Text(blocker.message, style = MaterialTheme.typography.bodyMedium)
                    Text("Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                    blocker.action?.let { action ->
                        TextButton(onClick = { onIntent(action) }) { Text("Take next action") }
                    }
                }
            }
        }
    }
}
