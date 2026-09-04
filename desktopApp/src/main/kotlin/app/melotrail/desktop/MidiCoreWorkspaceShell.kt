package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.domain.MidiExportRole

/** Stable semantic IDs for the target shell and its six reachable destinations. */
internal object MidiCoreWorkspaceShellTags {
    const val ROOT = "midi-core-workspace-shell"
    const val HEADER = "midi-core-workspace-header"
    const val CURRENT_PROJECT = "midi-core-current-project"
    const val OPERATION = "midi-core-workspace-operation"
    const val PROJECT_RAIL = "midi-core-workspace-project-rail"
    const val LOCAL_FOOTER = "midi-core-workspace-local-footer"
    const val NAVIGATION = "midi-core-workspace-navigation"
    const val WIDE_LAYOUT = "midi-core-workspace-layout-wide"
    const val COMPACT_LAYOUT = "midi-core-workspace-layout-compact"
    const val WIDE_NAVIGATION = "midi-core-workspace-navigation-wide"
    const val COMPACT_NAVIGATION = "midi-core-workspace-navigation-compact"
    const val PAGE = "midi-core-workspace-page"
    const val CONTEXT = "midi-core-workspace-context"
    const val COMPACT_CONTEXT = "midi-core-workspace-context-compact"
    const val BLOCKERS = "midi-core-workspace-blockers"
    const val PLAYER = "midi-core-workspace-player"
    const val PLAYER_TARGET = "midi-core-workspace-player-target"
    const val PLAYER_SOURCE = "midi-core-workspace-player-source"
    const val PLAYER_CURRENT = "midi-core-workspace-player-current"
    const val PLAYER_ACCEPTED = "midi-core-workspace-player-accepted"
    const val PLAYER_PLAY_PAUSE = "midi-core-workspace-player-play-pause"
    const val PLAYER_STOP = "midi-core-workspace-player-stop"
    const val PLAYER_POSITION = "midi-core-workspace-player-position"
    const val PLAYER_LOOP = "midi-core-workspace-player-loop"
    const val PLAYER_OPTIONS = "midi-core-workspace-player-options"
    const val PLAYER_OUTPUT_MENU = "midi-core-workspace-player-output-menu"
    const val PLAYER_OUTPUT_DEFAULT = "midi-core-workspace-player-output-default"
    const val PLAYER_SEEK_START = "midi-core-workspace-player-seek-start"
    const val PLAYER_RECOVERY = "midi-core-workspace-player-recovery"
    const val PLAYER_OUTPUT_PREFIX = "midi-core-workspace-player-output-"
    const val PLAYER_MUTE_PREFIX = "midi-core-workspace-player-mute-"
    const val PLAYER_SOLO_PREFIX = "midi-core-workspace-player-solo-"
    const val DESTINATION_PREFIX = "midi-core-destination-"
    const val BLOCKER_PREFIX = "midi-core-blocker-"

    fun destination(destination: MidiCoreWorkspaceDestination): String = DESTINATION_PREFIX + destination.route
    fun output(id: String): String = PLAYER_OUTPUT_PREFIX + id.hashCode().toUInt().toString(16)
    fun mute(role: MidiExportRole): String = PLAYER_MUTE_PREFIX + role.name.lowercase()
    fun solo(role: MidiExportRole): String = PLAYER_SOLO_PREFIX + role.name.lowercase()
}

/** The only top-level pages reachable from the target desktop shell. */
internal enum class MidiCoreWorkspaceDestination(
    val route: String,
    val label: String,
    val summary: String,
    val icon: WorkspaceVectorIcon,
) {
    PROJECT("project", "Project", "Create or reopen a MIDI Core project and inspect its current authority.", WorkspaceVectorIcon.PROJECT),
    MIDI("midi", "MIDI", "Import one immutable Standard MIDI source and automatically protect its melody.", WorkspaceVectorIcon.MIDI),
    STRUCTURE_HARMONY("structure-harmony", "Structure & Harmony", "Define the authoritative section timeline and chord windows.", WorkspaceVectorIcon.STRUCTURE),
    ARRANGE("arrange", "Arrange", "Preview one named style, create a complete draft, and repair only selected sections.", WorkspaceVectorIcon.ARRANGE),
    REVIEW("review", "Review", "Listen to the complete draft and inspect detailed alternatives only for exceptions.", WorkspaceVectorIcon.REVIEW),
    EXPORT("export", "Export", "Publish a portable MIDI package for Logic Pro.", WorkspaceVectorIcon.EXPORT),
}

internal val midiCoreWorkspaceDestinations: List<MidiCoreWorkspaceDestination> = MidiCoreWorkspaceDestination.entries

private enum class MidiCoreWorkspaceShellLayout { WIDE, COMPACT }

internal fun midiCoreWorkspaceShellLayout(width: Dp): String =
    if (width >= MusicWorkspaceTokens.Layout.WideBreakpoint) "wide" else "compact"

/** Collect the focused ViewModel state and render the target-only shell. */
@Composable
internal fun MidiCoreWorkspaceShell(
    workspace: MidiCoreWorkspaceViewModel,
    modifier: Modifier = Modifier,
    projectActions: MidiCoreProjectPageActions = MidiCoreProjectPageActions(),
    midiActions: MidiCoreMidiPageActions = MidiCoreMidiPageActions(),
    exportActions: MidiCoreExportPageActions = MidiCoreExportPageActions(),
) {
    val state by workspace.state.collectAsState()
    MidiCoreWorkspaceShell(state, workspace::accept, modifier, projectActions = projectActions, midiActions = midiActions, exportActions = exportActions)
}

/** Render the shell from a stable state snapshot so its semantic tree is independently testable. */
@Composable
internal fun MidiCoreWorkspaceShell(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit = {},
    modifier: Modifier = Modifier,
    initialDestination: MidiCoreWorkspaceDestination = MidiCoreWorkspaceDestination.PROJECT,
    projectActions: MidiCoreProjectPageActions = MidiCoreProjectPageActions(),
    midiActions: MidiCoreMidiPageActions = MidiCoreMidiPageActions(),
    exportActions: MidiCoreExportPageActions = MidiCoreExportPageActions(),
) {
    val projectKey = state.project?.id?.value
    var selectedDestination by remember(projectKey, initialDestination) {
        mutableStateOf(initialDestination)
    }
    var compactContextOpen by remember(projectKey, selectedDestination) { mutableStateOf(false) }
    val pageStateHolder = rememberSaveableStateHolder()
    val onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit = { selectedDestination = it }
    BoxWithConstraints(
        modifier.fillMaxSize().background(MusicWorkspaceTokens.Canvas).semantics {
            testTag = MidiCoreWorkspaceShellTags.ROOT
            contentDescription = "Melotrail MIDI Core workspace"
        },
    ) {
        val layout = when (midiCoreWorkspaceShellLayout(maxWidth)) {
            "wide" -> MidiCoreWorkspaceShellLayout.WIDE
            else -> MidiCoreWorkspaceShellLayout.COMPACT
        }
        val compact = layout == MidiCoreWorkspaceShellLayout.COMPACT
        val navigationWidth = if (maxWidth >= 1536.dp) 224.dp else 196.dp
        val inspectorWidth = if (maxWidth >= 1536.dp) {
            when (selectedDestination) {
                MidiCoreWorkspaceDestination.PROJECT -> 458.dp
                MidiCoreWorkspaceDestination.MIDI -> 381.dp
                MidiCoreWorkspaceDestination.STRUCTURE_HARMONY -> 390.dp
                MidiCoreWorkspaceDestination.ARRANGE,
                MidiCoreWorkspaceDestination.REVIEW,
                -> 332.dp
                MidiCoreWorkspaceDestination.EXPORT -> 407.dp
            }
        } else if (selectedDestination == MidiCoreWorkspaceDestination.PROJECT) {
            352.dp
        } else {
            320.dp
        }
        Column(Modifier.fillMaxSize()) {
            MidiCoreWorkspaceHeader(state, compact)
            when (layout) {
                MidiCoreWorkspaceShellLayout.WIDE -> Row(
                    Modifier.fillMaxWidth().weight(1f).padding(
                        horizontal = MusicWorkspaceTokens.Shell.PageHorizontalInset,
                        vertical = MusicWorkspaceTokens.Spacing.Lg,
                    ).semantics { testTag = MidiCoreWorkspaceShellTags.WIDE_LAYOUT },
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
                ) {
                    MidiCoreWorkspaceNavigation(
                        state = state,
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                        compact = false,
                        modifier = Modifier.width(navigationWidth).fillMaxHeight(),
                    )
                    pageStateHolder.SaveableStateProvider("${projectKey ?: "empty"}:${selectedDestination.route}") {
                        MidiCoreWorkspacePage(
                            destination = selectedDestination,
                            state = state,
                            onIntent = onIntent,
                            onDestinationSelected = onDestinationSelected,
                            projectActions = projectActions,
                            midiActions = midiActions,
                            exportActions = exportActions,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    MidiCoreWorkspaceContext(
                        destination = selectedDestination,
                        state = state,
                        modifier = Modifier.width(inspectorWidth).fillMaxHeight(),
                    )
                }

                MidiCoreWorkspaceShellLayout.COMPACT -> Column(
                    Modifier.fillMaxWidth().weight(1f).padding(
                        horizontal = MusicWorkspaceTokens.Spacing.Lg,
                        vertical = MusicWorkspaceTokens.Spacing.Md,
                    ).semantics { testTag = MidiCoreWorkspaceShellTags.COMPACT_LAYOUT },
                    verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
                ) {
                    MidiCoreWorkspaceNavigation(
                        state = state,
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    pageStateHolder.SaveableStateProvider("${projectKey ?: "empty"}:${selectedDestination.route}") {
                        MidiCoreWorkspacePage(
                            destination = selectedDestination,
                            state = state,
                            onIntent = onIntent,
                            onDestinationSelected = onDestinationSelected,
                            projectActions = projectActions,
                            midiActions = midiActions,
                            exportActions = exportActions,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                    WorkstationDisclosure(
                        label = "${selectedDestination.label} context",
                        expanded = compactContextOpen,
                        onExpandedChange = { compactContextOpen = it },
                        modifier = Modifier.semantics { testTag = MidiCoreWorkspaceShellTags.COMPACT_CONTEXT },
                    ) {
                        MidiCoreWorkspaceContext(
                            destination = selectedDestination,
                            state = state,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(
                    horizontal = if (compact) MusicWorkspaceTokens.Spacing.Lg else MusicWorkspaceTokens.Shell.PageHorizontalInset,
                    vertical = MusicWorkspaceTokens.Spacing.Sm,
                ),
            ) {
                MidiCoreWorkspacePlaybackDock(state, onIntent)
            }
        }
    }
}

/**
 * The one workspace-owned MIDI transport. Destination pages choose a musical
 * view, but never render their own pause, stop, loop, device, or role controls.
 */
@Composable
private fun MidiCoreWorkspacePlaybackDock(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    val audition = state.audition
    val window = audition.window
    val sourceAvailable = state.source.status == MidiCoreSourceStatus.IMPORTED && state.melody.selected != null
    val acceptedAvailable = state.project?.let(::midiCoreArrangementProgress)?.complete == true
    val currentAvailable = audition.scope != null
    val position = window?.let { audition.positionTick.coerceIn(it.startTick, it.endTick) } ?: 0L
    var optionsOpen by remember { mutableStateOf(false) }
    var outputOpen by remember { mutableStateOf(false) }
    val selectedOutput = audition.outputDevices.singleOrNull { it.id == audition.outputDeviceId }

    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreWorkspaceShellTags.PLAYER
            contentDescription = "Persistent MIDI player. ${auditionTargetDescription(state)}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Text("PLAYER", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Primary)
                    Text(
                        auditionTargetDescription(state),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            testTag = MidiCoreWorkspaceShellTags.PLAYER_TARGET
                            contentDescription = "Current playback target: ${auditionTargetDescription(state)}"
                        },
                    )
                }
                Text(
                    "${audition.playback.name.lowercase().replaceFirstChar(Char::uppercaseChar)} · $position${window?.let { " / ${it.endTick}" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MusicWorkspaceTokens.TextSecondary,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.PlaySourceMelody) },
                    enabled = sourceAvailable && !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_SOURCE
                        selected = audition.scope == MidiAuditionScope.SourceMelody
                        contentDescription = "Play protected source melody"
                    },
                ) { Text("Source") }
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAudition()) },
                    enabled = currentAvailable && !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_CURRENT
                        selected = currentAvailable && audition.scope != MidiAuditionScope.SourceMelody && audition.scope != MidiAuditionScope.AcceptedArrangement
                        contentDescription = "Play the current MIDI audition target"
                    },
                ) { Text("Current") }
                OutlinedButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.PlayAcceptedArrangement) },
                    enabled = acceptedAvailable && !state.busy,
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_ACCEPTED
                        selected = audition.scope == MidiAuditionScope.AcceptedArrangement
                        contentDescription = if (acceptedAvailable) "Play the accepted MIDI arrangement" else "Accept every required role before playing the accepted arrangement"
                    },
                ) { Text("Accepted") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
            ) {
                Button(
                    onClick = {
                        onIntent(
                            if (audition.playback == MidiAuditionPlaybackState.PLAYING) MidiCoreWorkspaceIntent.PauseAudition
                            else MidiCoreWorkspaceIntent.PlayAudition()
                        )
                    },
                    enabled = currentAvailable && !state.busy,
                    colors = workspacePrimaryButtonColors(),
                    modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_PLAY_PAUSE
                        contentDescription = if (audition.playback == MidiAuditionPlaybackState.PLAYING) "Pause MIDI playback" else "Play current MIDI target"
                    },
                ) { Text(if (audition.playback == MidiAuditionPlaybackState.PLAYING) "Pause" else "Play") }
                TextButton(
                    onClick = { onIntent(MidiCoreWorkspaceIntent.StopAudition) },
                    enabled = currentAvailable && audition.playback != MidiAuditionPlaybackState.STOPPED && !state.busy,
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_STOP
                        contentDescription = "Stop MIDI playback"
                    },
                ) { Text("Stop") }
                OutlinedButton(
                    onClick = {
                        val currentWindow = window ?: return@OutlinedButton
                        onIntent(MidiCoreWorkspaceIntent.SetAuditionLoop(if (audition.loop == null) MidiAuditionLoop(currentWindow.startTick, currentWindow.endTick) else null))
                    },
                    enabled = window != null && currentAvailable && !state.busy,
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_LOOP
                        selected = audition.loop != null
                        contentDescription = if (audition.loop == null) "Loop current MIDI target" else "Disable MIDI loop"
                    },
                ) { Text(if (audition.loop == null) "Loop" else "Loop on") }
                OutlinedButton(
                    onClick = { optionsOpen = !optionsOpen },
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_OPTIONS
                        selected = optionsOpen
                        contentDescription = if (optionsOpen) "Hide player options" else "Show player options"
                    },
                ) { Text("Options") }
            }
            if (window != null) {
                Slider(
                    value = position.toFloat(),
                    onValueChange = { onIntent(MidiCoreWorkspaceIntent.SeekAudition(it.toLong().coerceIn(window.startTick, window.endTick))) },
                    valueRange = window.startTick.toFloat()..window.endTick.toFloat(),
                    enabled = currentAvailable && !state.busy,
                    modifier = Modifier.fillMaxWidth().semantics {
                        testTag = MidiCoreWorkspaceShellTags.PLAYER_POSITION
                        contentDescription = "Seek current MIDI playback position"
                    },
                )
            }
            if (optionsOpen) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    Box {
                        OutlinedButton(
                            onClick = { outputOpen = true },
                            enabled = currentAvailable && !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                testTag = MidiCoreWorkspaceShellTags.PLAYER_OUTPUT_MENU
                                contentDescription = "Choose MIDI output. Current output: ${selectedOutput?.name ?: "Built-in synthesizer"}"
                            },
                        ) { Text("Output · ${selectedOutput?.name ?: "Built-in synthesizer"}") }
                        DropdownMenu(expanded = outputOpen, onDismissRequest = { outputOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Built-in synthesizer") },
                                onClick = { outputOpen = false; onIntent(MidiCoreWorkspaceIntent.SelectAuditionOutputDevice(null)) },
                                modifier = Modifier.semantics { testTag = MidiCoreWorkspaceShellTags.PLAYER_OUTPUT_DEFAULT },
                            )
                            audition.outputDevices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.name) },
                                    onClick = { outputOpen = false; onIntent(MidiCoreWorkspaceIntent.SelectAuditionOutputDevice(device.id)) },
                                    modifier = Modifier.semantics { testTag = MidiCoreWorkspaceShellTags.output(device.id) },
                                )
                            }
                        }
                    }
                    window?.let { currentWindow ->
                        OutlinedButton(
                            onClick = { onIntent(MidiCoreWorkspaceIntent.SeekAudition(currentWindow.startTick)) },
                            enabled = currentAvailable && !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                testTag = MidiCoreWorkspaceShellTags.PLAYER_SEEK_START
                                contentDescription = "Seek to current MIDI view boundary"
                            },
                        ) { Text("Restart current view") }
                    }
                    auditionRoles(audition.scope).forEach { role ->
                        val muted = role in audition.mutedRoles
                        val solo = role in audition.soloRoles
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                            OutlinedButton(
                                onClick = { onIntent(MidiCoreWorkspaceIntent.MuteAuditionRole(role, !muted)) },
                                enabled = currentAvailable && !state.busy,
                                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                    testTag = MidiCoreWorkspaceShellTags.mute(role)
                                    selected = muted
                                },
                            ) { Text(if (muted) "Unmute ${role.trackName}" else "Mute ${role.trackName}") }
                            OutlinedButton(
                                onClick = { onIntent(MidiCoreWorkspaceIntent.SoloAuditionRole(role, !solo)) },
                                enabled = currentAvailable && !state.busy,
                                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                                    testTag = MidiCoreWorkspaceShellTags.solo(role)
                                    selected = solo
                                },
                            ) { Text(if (solo) "Unsolo ${role.trackName}" else "Solo ${role.trackName}") }
                        }
                    }
                    audition.lastProblem?.let { problem ->
                        Column(
                            Modifier.fillMaxWidth().semantics {
                                testTag = MidiCoreWorkspaceShellTags.PLAYER_RECOVERY
                                contentDescription = "MIDI device problem: ${problem.message} Next action: ${problem.nextAction}"
                            },
                            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
                        ) {
                            Text(problem.message, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                            Text("Next: ${problem.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
                            if (!state.busy) TextButton(onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) }) { Text("Retry playback") }
                        }
                    }
                }
            }
        }
    }
}

private fun auditionTargetDescription(state: MidiCoreWorkspaceState): String {
    val scope = state.audition.scope
    val target = when (scope) {
    null -> "No MIDI target selected"
    MidiAuditionScope.SourceMelody -> "Protected source melody"
    is MidiAuditionScope.Candidate -> "Current ${scope.role.trackName} alternative"
    is MidiAuditionScope.Occurrence -> "Current section ${scope.occurrenceId}"
    is MidiAuditionScope.StylePreview -> "${scope.styleId.replace('-', ' ')} style preview"
    is MidiAuditionScope.ArrangementDraft -> "Draft ${scope.draftId}"
    is MidiAuditionScope.Role -> "Current accepted ${scope.role.trackName}"
    MidiAuditionScope.AcceptedArrangement -> "Accepted full arrangement"
    }
    val occurrenceId = when (scope) {
        is MidiAuditionScope.Candidate -> state.project?.candidates?.singleOrNull { it.id == scope.candidateId }?.occurrenceId
        is MidiAuditionScope.Occurrence -> scope.occurrenceId
    is MidiAuditionScope.StylePreview -> scope.occurrenceId
        else -> null
    }
    val occurrenceLabel = occurrenceId?.let { id -> state.project?.authority?.occurrences?.singleOrNull { it.id == id }?.label }
    return occurrenceLabel?.let { "$target · $it" } ?: target
}

private fun auditionRoles(scope: MidiAuditionScope?): List<MidiExportRole> = when (scope) {
    null -> emptyList()
    MidiAuditionScope.SourceMelody -> listOf(MidiExportRole.MELODY)
    is MidiAuditionScope.Candidate -> listOf(scope.role)
    is MidiAuditionScope.Role -> listOf(scope.role)
    is MidiAuditionScope.Occurrence -> listOf(MidiExportRole.MELODY)
    is MidiAuditionScope.StylePreview -> MidiExportRole.entries
    is MidiAuditionScope.ArrangementDraft -> MidiExportRole.entries
    MidiAuditionScope.AcceptedArrangement -> MidiExportRole.entries
}

@Composable
private fun MidiCoreWorkspaceHeader(state: MidiCoreWorkspaceState, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .height(if (compact) 56.dp else MusicWorkspaceTokens.Shell.TopBarHeight)
            .background(MusicWorkspaceTokens.ElevatedSurface)
            .border(1.dp, MusicWorkspaceTokens.Border)
            .padding(horizontal = if (compact) MusicWorkspaceTokens.Spacing.Lg else MusicWorkspaceTokens.Shell.PageHorizontalInset)
            .semantics { testTag = MidiCoreWorkspaceShellTags.HEADER },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text("MELOTRAIL", style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.TextPrimary)
            if (!compact) Text("MIDI ARRANGEMENT", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Primary)
        }
        Column(
            Modifier.weight(1f).semantics {
                testTag = MidiCoreWorkspaceShellTags.CURRENT_PROJECT
                contentDescription = currentProjectDescription(state)
            },
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
        ) {
            Text("CURRENT PROJECT", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
            Text(
                state.project?.metadata?.name ?: "No project open",
                style = MaterialTheme.typography.titleSmall,
                color = MusicWorkspaceTokens.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            operationLabel(state) + if (state.operation.active) " · ${state.operation.progress?.let { "${it.completed}/${it.total}" } ?: "working"}" else "",
            modifier = Modifier.widthIn(max = 180.dp).semantics {
                testTag = MidiCoreWorkspaceShellTags.OPERATION
                contentDescription = state.operation.message
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.operation.outcome == MidiCoreWorkspaceOperationOutcome.FAILURE) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    state: MidiCoreWorkspaceState,
    selectedDestination: MidiCoreWorkspaceDestination,
    onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    val navigationTag = if (compact) MidiCoreWorkspaceShellTags.COMPACT_NAVIGATION else MidiCoreWorkspaceShellTags.WIDE_NAVIGATION
    Column(
        modifier = if (compact) Modifier else modifier.semantics { testTag = MidiCoreWorkspaceShellTags.PROJECT_RAIL },
    ) {
        WorkstationPanel(
            modifier = (if (compact) modifier else Modifier.fillMaxSize()).semantics {
                testTag = navigationTag
                contentDescription = "MIDI Core navigation with six destinations"
            },
        ) {
            val navigationModifier = Modifier.semantics {
                testTag = MidiCoreWorkspaceShellTags.NAVIGATION
                contentDescription = "MIDI Core workspace navigation"
            }
            if (compact) {
                Row(
                    navigationModifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
                ) {
                    midiCoreWorkspaceDestinations.forEach { destination ->
                        MidiCoreDestinationItem(destination, selectedDestination, onDestinationSelected, compact = true)
                    }
                }
            } else {
                WorkspaceProjectRailSummary(state)
                Column(
                    navigationModifier,
                    verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
                ) {
                    midiCoreWorkspaceDestinations.forEach { destination ->
                        MidiCoreDestinationItem(destination, selectedDestination, onDestinationSelected, compact = false)
                    }
                }
                WorkspaceLocalFooter(state)
            }
        }
    }
}

@Composable
private fun MidiCoreDestinationItem(
    destination: MidiCoreWorkspaceDestination,
    selectedDestination: MidiCoreWorkspaceDestination,
    onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit,
    compact: Boolean,
) {
    val selected = destination == selectedDestination
    WorkstationNavigationItem(
        label = destination.label,
        summary = destination.summary,
        icon = destination.icon,
        selected = selected,
        onClick = { onDestinationSelected(destination) },
        compact = compact,
        modifier = Modifier.semantics { testTag = MidiCoreWorkspaceShellTags.destination(destination) },
    )
}

@Composable
private fun WorkspaceProjectRailSummary(state: MidiCoreWorkspaceState) {
    Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text("PROJECT", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Primary)
        Text(state.project?.metadata?.name ?: "No project open", style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            state.projectRoot?.fileName?.toString() ?: "Choose a local project folder to begin.",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        WorkstationStatusBadge(
            tone = if (state.blockers.isEmpty()) WorkstationStatusTone.READY else WorkstationStatusTone.WARNING,
            label = if (state.blockers.isEmpty()) "Ready for the next MIDI step" else "${state.blockers.size} action${if (state.blockers.size == 1) "" else "s"} needed",
        )
    }
}

@Composable
private fun WorkspaceLocalFooter(state: MidiCoreWorkspaceState) {
    Text(
        if (state.projectRoot == null) "LOCAL DESKTOP · MIDI-ONLY" else "LOCAL PROJECT · ${state.projectRoot.fileName}",
        modifier = Modifier.fillMaxWidth().semantics { testTag = MidiCoreWorkspaceShellTags.LOCAL_FOOTER },
        style = MaterialTheme.typography.labelSmall,
        color = MusicWorkspaceTokens.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MidiCoreWorkspaceContext(
    destination: MidiCoreWorkspaceDestination,
    state: MidiCoreWorkspaceState,
    modifier: Modifier,
) {
    WorkstationPanel(
        modifier = modifier.semantics {
            testTag = MidiCoreWorkspaceShellTags.CONTEXT
            contentDescription = "${destination.label} contextual inspector"
        },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
        ) {
            WorkstationHeadingActionRow(
                eyebrow = "CONTEXT",
                title = "${destination.label} inspector",
                summary = "Factual project evidence for the current MIDI decision.",
            )
            when (destination) {
                MidiCoreWorkspaceDestination.PROJECT -> {
                    ContextFact("Project revision", state.project?.revision?.toString() ?: "No project")
                    ContextFact("Source MIDI", if (state.source.status == MidiCoreSourceStatus.IMPORTED) "Bound" else "Required")
                    ContextFact("Protected melody", if (state.melody.selected == null) "Required" else "Selected")
                    ContextFact("Exports", state.project?.exportSnapshots?.size?.toString() ?: "0")
                }
                MidiCoreWorkspaceDestination.MIDI -> {
                    ContextFact("Source file", state.source.originalFilename ?: "Not imported")
                    ContextFact("Format", state.source.format?.let { "SMF $it" } ?: "Unavailable")
                    ContextFact("Tracks", state.source.trackSummaries.size.toString())
                    ContextFact("Melody", if (state.melody.selected == null) "Not selected" else "Protected")
                }
                MidiCoreWorkspaceDestination.STRUCTURE_HARMONY -> {
                    val authority = state.project?.authority
                    ContextFact("Authority", if (authority == null) "Not confirmed" else "Confirmed")
                    ContextFact("Sections", authority?.occurrences?.size?.toString() ?: "0")
                    ContextFact("Key", authority?.let { "${it.key.spelling.symbol} ${it.key.mode.displayName}" } ?: "Awaiting authority")
                    ContextFact("Meter", authority?.let { "${it.meter.numerator}/${it.meter.denominator}" } ?: "Awaiting authority")
                }
                MidiCoreWorkspaceDestination.ARRANGE -> {
                    val progress = state.project?.let(::midiCoreArrangementProgress)
                    ContextFact("Selected section", state.arrangement.selectedOccurrenceId ?: "Choose from the song map")
                    ContextFact("Style preview", state.stylePreview.selectedStyleId ?: "None")
                    ContextFact("Accepted roles", progress?.let { "${it.accepted}/${it.total}" } ?: "0/0")
                    ContextFact("Draft", state.arrangement.incompleteDraftId ?: "No active draft")
                }
                MidiCoreWorkspaceDestination.REVIEW -> {
                    ContextFact("Review role", state.review.role?.displayName ?: "No exception selected")
                    ContextFact("Selected candidate", state.review.selectedCandidateId ?: "None")
                    ContextFact("Candidate evidence", state.review.candidates.size.toString())
                    ContextFact("Accepted arrangement", if (state.project?.let(::midiCoreArrangementProgress)?.complete == true) "Complete" else "Pending")
                }
                MidiCoreWorkspaceDestination.EXPORT -> {
                    ContextFact("Package status", if (state.export.latestSnapshot == null) "Not exported" else "Snapshot available")
                    ContextFact("Snapshots", state.project?.exportSnapshots?.size?.toString() ?: "0")
                    ContextFact("Destination", state.projectRoot?.fileName?.toString() ?: "Open a project")
                    ContextFact("Logic Pro", "MIDI package only")
                }
            }
            val blocker = state.blockers.firstOrNull()
            WorkstationInlineMessage(
                title = if (blocker == null) "Ready" else "Next action",
                message = blocker?.nextAction ?: "No current blockers.",
                tone = if (blocker == null) WorkstationStatusTone.READY else WorkstationStatusTone.WARNING,
            )
        }
    }
}

@Composable
private fun ContextFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MidiCoreWorkspacePage(
    destination: MidiCoreWorkspaceDestination,
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onDestinationSelected: (MidiCoreWorkspaceDestination) -> Unit,
    projectActions: MidiCoreProjectPageActions,
    midiActions: MidiCoreMidiPageActions,
    exportActions: MidiCoreExportPageActions,
    modifier: Modifier,
) {
    if (destination == MidiCoreWorkspaceDestination.PROJECT) {
        MidiCoreProjectPage(state, onIntent, onDestinationSelected, projectActions, modifier)
        return
    }
    if (destination == MidiCoreWorkspaceDestination.MIDI) {
        MidiCoreMidiPage(state, onIntent, midiActions, modifier)
        return
    }
    if (destination == MidiCoreWorkspaceDestination.STRUCTURE_HARMONY) {
        MidiCoreStructureHarmonyPage(state, onIntent, modifier)
        return
    }
    if (destination == MidiCoreWorkspaceDestination.ARRANGE) {
        MidiCoreArrangePage(state, onIntent, onDestinationSelected, modifier)
        return
    }
    if (destination == MidiCoreWorkspaceDestination.REVIEW) {
        MidiCoreReviewPage(state, onIntent, onDestinationSelected, modifier)
        return
    }
    if (destination == MidiCoreWorkspaceDestination.EXPORT) {
        MidiCoreExportPage(state, onIntent, exportActions, modifier)
        return
    }
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
