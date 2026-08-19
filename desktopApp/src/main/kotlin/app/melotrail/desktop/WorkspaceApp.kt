package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.arrangement.MidiCleanupProfile
import app.melotrail.arrangement.ArrangementRole
import app.melotrail.arrangement.SoundTrait

object WorkspaceTags {
    const val PROJECT_HEADER = "project-header"
    const val WORKSPACE_NAV = "workspace-nav"
    const val PROJECT_SELECTOR = "project-selector"
    const val WORKSPACE_SECTION_PREFIX = "workspace-section-"
    const val GLOBAL_FEEDBACK = "global-feedback"
    const val GLOBAL_FEEDBACK_RETRY = "global-feedback-retry"
    const val GLOBAL_FEEDBACK_DISMISS = "global-feedback-dismiss"
    const val READINESS_RECOVERY = "readiness-recovery"
    const val PARTS_PANEL = "parts-panel"
    const val PRESENTATION_PANEL = "presentation-panel"
    const val SCENE_PLAY_PAUSE = "scene-play-pause"
    const val SCENE_STOP = "scene-stop"
    const val SCENE_PROGRESS = "scene-progress"
    const val VIDEO_CONCEPT_PANEL = "video-concept-panel"
    const val CURRENT_LOCATION_PANEL = "current-location-panel"
    const val NEXT_DESTINATION_PANEL = "next-destination-panel"
    const val AI_PLAN_PANEL = "ai-plan-panel"
    const val AI_PLAN_SECTION_PREFIX = "ai-plan-section-"
    const val AI_PLAN_REGENERATE = "ai-plan-regenerate"
    const val MIXER = "mixer"
    const val FOOTER_WAVEFORM = "footer-waveform"
    const val MIX_CHANNEL_PREFIX = "mix-channel-"
    const val MIX_GAIN_PREFIX = "mix-gain-"
    const val MIX_MUTE_PREFIX = "mix-mute-"
    const val MIX_SOLO_PREFIX = "mix-solo-"
    const val MASTER_OUTPUT = "master-output"
    const val MASTER_EFFECT_LOFI = "master-effect-lofi"
    const val PART_ROW_PREFIX = "part-row-"
    const val PART_PREVIEW_PREFIX = "part-preview-"
    const val STRUCTURE_PANEL = "structure-panel"
    const val STRUCTURE_OCCURRENCE_PREFIX = "structure-occurrence-"
    const val STRUCTURE_OVERVIEW = "structure-overview"
    const val STRUCTURE_SELECTED_DETAIL = "structure-selected-detail"
    const val STRUCTURE_RULER = "structure-ruler"
    const val STRUCTURE_TOTAL = "structure-total"
    const val STRUCTURE_DUPLICATE = "structure-duplicate"
    const val ARRANGEMENT_PANEL = "arrangement-panel"
    const val TIMELINE_PANEL = "timeline-panel"
    const val TIMELINE_LANE_PREFIX = "timeline-lane-"
    const val TIMELINE_SECTION_PREFIX = "timeline-section-"
    const val TIMELINE_RULER = "timeline-ruler"
    const val TIMELINE_CURSOR = "timeline-cursor"
    const val TIMELINE_CONTROLS = "timeline-controls"
    const val MIX_PANEL = "mix-panel"
    const val COMPACT_TRANSPORT = "compact-transport"
    const val LIBRARY_PANEL = "library-panel"
    const val MIX_TRACK_PREFIX = "mix-track-"
    const val OPERATION_STATUS = "operation-status"
    const val OPERATION_FEEDBACK = "operation-feedback"
    const val CREATE_PROJECT = "create-project"
    const val OPEN_PROJECT = "open-project"
    const val MIGRATE_PROJECT = "migrate-project"
    const val ADD_MIDI = "add-midi"
    const val ADD_AUDIO = "add-audio"
    const val IMPORT_MIDI = "import-midi"
    const val IMPORT_AUDIO = "import-audio"
    const val IMPORT_ENTRY = "import-source-entry"
    const val IMPORT_SOURCE = "import-source"
    const val IMPORT_CONFIRM = "import-confirm"
    const val IMPORT_STEPS = "import-steps"
    const val IMPORT_PROVENANCE = "import-provenance"
    const val IMPORT_DETAILS = "import-details"
    const val IMPORT_PROGRESS = "import-progress"
    const val STRUCTURE_CLEAR = "structure-clear"
    const val STRUCTURE_MOVE_LEFT = "structure-move-left-"
    const val STRUCTURE_MOVE_RIGHT = "structure-move-right-"
    const val ARRANGEMENT_GENERATE = "arrangement-generate"
    const val ARRANGEMENT_APPROVE = "arrangement-approve"
    const val ARRANGEMENT_PREVIEW = "arrangement-preview"
    const val ARRANGEMENT_STYLE = "arrangement-style"
    const val ARRANGEMENT_EDIT_SECTION = "arrangement-edit-section"
    const val ARRANGEMENT_INSTRUMENT_PREFIX = "arrangement-instrument-"
    const val ARRANGEMENT_TRANSITION_IN = "arrangement-transition-in"
    const val ARRANGEMENT_TRANSITION_OUT = "arrangement-transition-out"
    const val BUILD_SONG = "build-song"
    const val HEADER_SAVE = "header-save"
    const val HEADER_SETTINGS = "header-settings"
    const val HEADER_THEME = "header-theme"
    const val COMMERCIAL_READINESS = "commercial-readiness-panel"
    const val COMMERCIAL_EXPORT = "commercial-export"
    const val BUILD_LIFECYCLE = "build-lifecycle"
    const val BUILD_START = "build-start"
    const val BUILD_CANCEL = "build-cancel"
    const val MIX_RESET = "mix-reset"
    const val PLAYBACK_DRY = "playback-dry"
    const val PLAYBACK_LOFI = "playback-lofi"
    const val PLAYBACK_MASTER = "playback-master"
    const val PLAYBACK_TOGGLE = "playback-toggle"
    const val PLAYBACK_SEEK = "playback-seek"
    const val PLAYBACK_VOLUME = "playback-volume"
    const val PLAYBACK_RETRY = "playback-retry"
    const val PREPARATION_PANEL = "preparation-panel"
    const val PREPARATION_INSPECT = "preparation-inspect"
    const val PREPARATION_APPLY = "preparation-apply"
    const val PREPARATION_TRANSCRIBE = "preparation-transcribe"
    const val PREPARATION_ORIGINAL = "preparation-original"
    const val PREPARATION_CLEAN = "preparation-clean"
    const val MIDI_QUALITY_PANEL = "midi-quality-panel"
    const val MIDI_QUALITY_CLEAN = "midi-quality-clean"
    const val MIDI_QUALITY_PROFILE_PREFIX = "midi-quality-profile-"
    const val MIDI_FEEL_ORIGINAL = "midi-feel-original"
    const val MIDI_FEEL_LOFI = "midi-feel-lofi"
    const val MIDI_FEEL_APPLY = "midi-feel-apply"
    const val PART_DETAILS_DIALOG = "part-details-dialog"
    const val PART_DETAILS_CLOSE = "part-details-close"
    const val PART_COMPARISON = "part-artifact-comparison"
    const val PART_COMPARISON_PLAY_PREFIX = "part-artifact-comparison-play-"
    const val SOUND_LIBRARY_SETTINGS = "sound-library-settings"
    const val SOUND_LIBRARY_CHOOSE = "sound-library-choose"
    const val SOUND_LIBRARY_CLEAR = "sound-library-clear"
}

@Composable
fun WorkspaceApp(viewModel: WorkspaceViewModel, onExit: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness)
        viewModel.accept(WorkspaceIntent.RefreshSoundLibrary)
        viewModel.accept(WorkspaceIntent.RestoreLastProject)
    }
    WorkspaceScreen(state, viewModel::accept, onExit)
}

@Composable
fun WorkspaceScreen(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, onExit: () -> Unit = {}) {
    val partDetailsFocusTargets = remember { mutableStateMapOf<PartDetailsFocusReturn, FocusRequester>() }
    var partDetailsFocusReturn by remember { mutableStateOf<PartDetailsFocusReturn?>(null) }
    LaunchedEffect(state.dialog) {
        (state.dialog as? WorkspaceDialog.PartDetails)?.let { partDetailsFocusReturn = it.focusReturn }
        if (state.dialog == null) {
            partDetailsFocusReturn?.let { target -> partDetailsFocusTargets[target]?.requestFocus() }
            partDetailsFocusReturn = null
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(MusicWorkspaceTokens.Reference.OuterPadding)
            .onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown && state.workspaceSection == WorkspaceSection.SETTINGS && state.dialog == null) {
                    onIntent(WorkspaceIntent.BackFromSettings)
                    true
                } else {
                    transportShortcutIntent(event, state.playback)?.let(onIntent) != null
                }
            }
    ) {
        WorkspaceShellFrame(state, onIntent, partDetailsFocusTargets)
        OperationFeedbackBanner(state, onIntent, Modifier.align(Alignment.TopCenter).padding(top = MusicWorkspaceTokens.Shell.TopBarHeight + MusicWorkspaceTokens.Spacing.Sm))
    }
    WorkspaceDialogs(state, onIntent, onExit)
}

@Composable
internal fun OperationFeedbackBanner(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) {
    val feedback = state.operationFeedback
    if (feedback.phase == OperationPhase.IDLE) {
        Box(modifier.semantics { testTag = WorkspaceTags.GLOBAL_FEEDBACK; contentDescription = "Global operation feedback: ready" })
        return
    }
    Card(
        modifier = modifier.widthIn(max = 620.dp).semantics { testTag = WorkspaceTags.GLOBAL_FEEDBACK; contentDescription = "Global operation feedback" },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface), border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Box(Modifier.padding(MusicWorkspaceTokens.Spacing.Md)) { OperationStatusSurface(state, onIntent) }
    }
}

internal fun transportShortcutIntent(
    event: androidx.compose.ui.input.key.KeyEvent,
    playback: PlaybackSnapshot
): WorkspaceIntent? = transportShortcutIntent(
    key = event.key,
    shortcutPressed = event.isCtrlPressed || event.isMetaPressed,
    keyDown = event.type == KeyEventType.KeyDown,
    playback = playback
)

internal fun transportShortcutIntent(
    key: Key,
    shortcutPressed: Boolean,
    keyDown: Boolean,
    playback: PlaybackSnapshot
): WorkspaceIntent? {
    if (!keyDown || !shortcutPressed) return null
    return when (key) {
        Key.Spacebar -> WorkspaceIntent.PlayPause
        Key.DirectionLeft -> WorkspaceIntent.SeekPlayback((playback.positionSeconds - 5.0).coerceAtLeast(0.0))
        Key.DirectionRight -> WorkspaceIntent.SeekPlayback((playback.positionSeconds + 5.0).coerceAtMost(playback.durationSeconds))
        Key.K -> WorkspaceIntent.StopPlayback
        else -> null
    }
}

@Composable
private fun ProjectHeader(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val mutationsDisabled = state.operation.isMutating
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Reference.HeaderHeight).semantics { testTag = WorkspaceTags.PROJECT_HEADER },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Lg, vertical = MusicWorkspaceTokens.Spacing.Xs)) {
            if (maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint) {
                Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WorkspaceBrand(Modifier.weight(1f))
                        HeaderIconControl("＋", "Create a new project", !mutationsDisabled, WorkspaceTags.CREATE_PROJECT) { onIntent(WorkspaceIntent.ShowCreateProject) }
                        HeaderIconControl("⚙", "Open Settings", !mutationsDisabled, WorkspaceTags.HEADER_SETTINGS) { onIntent(WorkspaceIntent.OpenSettings) }
                    }
                    SelectedProjectControl(state, mutationsDisabled, onIntent, Modifier.fillMaxWidth())
                    if (state.project?.migration?.requiresMigration == true) TextButton(onClick = { onIntent(WorkspaceIntent.MigrateProject) }, modifier = Modifier.semantics { testTag = WorkspaceTags.MIGRATE_PROJECT }) { Text("Migrate") }
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
            ) {
                WorkspaceBrand(Modifier.width(MusicWorkspaceTokens.Shell.HeaderBrandWidth))
                Spacer(Modifier.weight(1f))
                SelectedProjectControl(state, mutationsDisabled, onIntent)
                HeaderActions(state, mutationsDisabled, onIntent)
                if (state.project?.migration?.requiresMigration == true) TextButton(onClick = { onIntent(WorkspaceIntent.MigrateProject) }, modifier = Modifier.semantics { testTag = WorkspaceTags.MIGRATE_PROJECT }) { Text("Migrate") }
            }
        }
    }
}

@Composable
private fun SelectedProjectControl(state: WorkspaceUiState, mutationsDisabled: Boolean, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier.width(MusicWorkspaceTokens.Shell.HeaderProjectWidth)) {
    val projectText = state.project?.name ?: "No project"
    OutlinedButton(
        onClick = { onIntent(WorkspaceIntent.ChooseProject) }, enabled = !mutationsDisabled,
        modifier = modifier.heightIn(min = MusicWorkspaceTokens.Shell.HeaderIconSize).semantics {
            testTag = WorkspaceTags.PROJECT_SELECTOR
            contentDescription = "Selected project: $projectText. Choose another project."
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("PROJECT", style = MaterialTheme.typography.labelSmall, fontSize = MusicWorkspaceTokens.Type.HeaderProjectLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(projectText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WorkspaceBrand(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text("▣", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Melotrail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Compose · Arrange · Mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeaderActions(state: WorkspaceUiState, mutationsDisabled: Boolean, onIntent: (WorkspaceIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        HeaderIconControl("＋", "Create a new project", !mutationsDisabled, WorkspaceTags.CREATE_PROJECT) { onIntent(WorkspaceIntent.ShowCreateProject) }
        HeaderIconControl("⌑", "Open an existing project", !mutationsDisabled, WorkspaceTags.OPEN_PROJECT) { onIntent(WorkspaceIntent.ChooseProject) }
        HeaderIconControl("▣", "Save is unavailable. Project changes are saved by their explicit workflow actions.", false, WorkspaceTags.HEADER_SAVE) {}
        HeaderIconControl("⚙", "Open Settings", !mutationsDisabled, WorkspaceTags.HEADER_SETTINGS) { onIntent(WorkspaceIntent.OpenSettings) }
        HeaderIconControl("☼", "Theme selection is unavailable. Melotrail currently uses its fixed dark workspace theme.", false, WorkspaceTags.HEADER_THEME) {}
        HeaderIconControl("▶", "Build song release artifacts. ${buildSongPrerequisite(state)}", canBuild(state), WorkspaceTags.BUILD_SONG) { onIntent(WorkspaceIntent.BuildSong) }
    }
}

@Composable
private fun HeaderIconControl(symbol: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(MusicWorkspaceTokens.Shell.HeaderIconSize).semantics {
            testTag = tag
            contentDescription = description
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) { Text(symbol, style = MaterialTheme.typography.titleMedium) }
}

internal fun soundLibrarySummary(library: SoundLibrarySettingsState): String = when {
    library.resolvedRoot != null && library.validationError == null -> "Library: ${library.source ?: "configured"} · validated locally"
    else -> "Library unavailable — ${library.validationError ?: "choose a valid folder"}"
}

@Composable
private fun WorkspaceNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState())
            .semantics { testTag = WorkspaceTags.WORKSPACE_NAV; contentDescription = "Workspace sections" },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        WorkspaceSection.entries.forEach { section ->
            val selected = state.workspaceSection == section
            Row(
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(MusicWorkspaceTokens.Radius.Control))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MusicWorkspaceTokens.ElevatedSurface)
                    .clickable { onIntent(WorkspaceIntent.SelectWorkspaceSection(section)) }
                    .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Spacing.Sm)
                    .semantics {
                        testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + section.name.lowercase()
                        contentDescription = "Open ${section.label} workspace${if (selected) ", selected" else ""}"
                    },
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Shell.NavigationIconGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(section.referenceIcon(), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(section.label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

private fun WorkspaceSection.referenceIcon(): String = when (this) {
    WorkspaceSection.SETUP -> "⚙"
    WorkspaceSection.HARMONY -> "♬"
    WorkspaceSection.OVERVIEW -> "▣"
    WorkspaceSection.IMPORT -> "⇩"
    WorkspaceSection.STRUCTURE -> "▤"
    WorkspaceSection.ARRANGE -> "◇"
    WorkspaceSection.MIX_MASTER -> "▥"
    WorkspaceSection.LIBRARY -> "▤"
    WorkspaceSection.VIDEO_PREVIEW -> "▧"
    WorkspaceSection.EXPORT -> "⇧"
    WorkspaceSection.SETTINGS -> "⚙"
}

@Composable
private fun WorkspaceShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        when (workspaceLayoutForWidth(maxWidth)) {
            WorkspaceLayout.WIDE -> WideWorkspace(state, onIntent)
            WorkspaceLayout.MEDIUM -> MediumWorkspace(state, onIntent)
            WorkspaceLayout.NARROW -> NarrowWorkspace(state, onIntent)
        }
    }
}

internal enum class WorkspaceLayout { WIDE, MEDIUM, NARROW }

internal fun workspaceLayoutForWidth(width: androidx.compose.ui.unit.Dp): WorkspaceLayout = when {
    width >= MusicWorkspaceTokens.Reference.WideBreakpoint -> WorkspaceLayout.WIDE
    width >= MusicWorkspaceTokens.Reference.MediumBreakpoint -> WorkspaceLayout.MEDIUM
    else -> WorkspaceLayout.NARROW
}

@Composable
private fun WideWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            when (state.workspaceSection) {
                WorkspaceSection.SETUP,
                WorkspaceSection.HARMONY,
                WorkspaceSection.OVERVIEW, WorkspaceSection.IMPORT -> {
                    PanelColumn(Modifier.widthIn(min = 235.dp, max = 300.dp).weight(0.95f), state, onIntent, projectLeftPanels(state))
                    PanelColumn(Modifier.weight(1.7f), state, onIntent, listOf(Panel.Structure, Panel.Arrangement, Panel.Timeline))
                    PanelColumn(Modifier.widthIn(min = 255.dp, max = 340.dp).weight(1f), state, onIntent, listOf(Panel.Status))
                }
                WorkspaceSection.STRUCTURE -> {
                    PanelColumn(Modifier.widthIn(min = 260.dp, max = 330.dp).weight(0.9f), state, onIntent, listOf(Panel.Parts))
                    PanelColumn(Modifier.weight(1.8f), state, onIntent, listOf(Panel.Structure, Panel.Timeline))
                    PanelColumn(Modifier.widthIn(min = 270.dp, max = 360.dp).weight(1f), state, onIntent, listOf(Panel.Preparation, Panel.MidiQuality, Panel.Status))
                }
                WorkspaceSection.ARRANGE -> {
                    PanelColumn(Modifier.widthIn(min = 270.dp, max = 360.dp).weight(1f), state, onIntent, listOf(Panel.Structure))
                    PanelColumn(Modifier.weight(1.8f), state, onIntent, listOf(Panel.Arrangement, Panel.Timeline))
                    PanelColumn(Modifier.widthIn(min = 260.dp, max = 340.dp).weight(0.9f), state, onIntent, listOf(Panel.Status))
                }
                WorkspaceSection.MIX_MASTER -> {
                    PanelColumn(Modifier.weight(1.45f), state, onIntent, listOf(Panel.Timeline))
                    PanelColumn(Modifier.weight(1.2f), state, onIntent, listOf(Panel.Mix))
                    PanelColumn(Modifier.widthIn(min = 260.dp, max = 340.dp).weight(0.8f), state, onIntent, listOf(Panel.Status))
                }
                WorkspaceSection.LIBRARY, WorkspaceSection.VIDEO_PREVIEW, WorkspaceSection.EXPORT, WorkspaceSection.SETTINGS -> {
                    PanelColumn(Modifier.weight(1.8f), state, onIntent, listOf(Panel.Library))
                    PanelColumn(Modifier.weight(1f), state, onIntent, listOf(Panel.Status))
                }
            }
        }
    }
}

@Composable
private fun MediumWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
        Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            val panels = panelsForSection(state.workspaceSection, state)
            PanelColumn(Modifier.widthIn(min = 300.dp, max = 360.dp), state, onIntent, panels.first)
            PanelColumn(Modifier.widthIn(min = 500.dp, max = 720.dp), state, onIntent, panels.second)
            if (panels.third.isNotEmpty()) PanelColumn(Modifier.widthIn(min = 300.dp, max = 380.dp), state, onIntent, panels.third)
        }
    }
}

@Composable
private fun NarrowWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val panels = panelsForSection(state.workspaceSection, state)
    PanelColumn(Modifier.fillMaxSize(), state, onIntent, panels.first + panels.second + panels.third)
}

private data class SectionPanels(val first: List<Panel>, val second: List<Panel>, val third: List<Panel>)

private fun projectLeftPanels(state: WorkspaceUiState): List<Panel> =
    if (state.selectedPartId == null) listOf(Panel.Parts) else listOf(Panel.Preparation, Panel.MidiQuality, Panel.Parts)

private fun panelsForSection(section: WorkspaceSection, state: WorkspaceUiState? = null): SectionPanels = when (section) {
    WorkspaceSection.SETUP,
    WorkspaceSection.HARMONY,
    WorkspaceSection.OVERVIEW, WorkspaceSection.IMPORT -> SectionPanels(state?.let(::projectLeftPanels) ?: listOf(Panel.Parts), listOf(Panel.Structure, Panel.Arrangement, Panel.Timeline), listOf(Panel.Status))
    WorkspaceSection.STRUCTURE -> SectionPanels(listOf(Panel.Parts), listOf(Panel.Structure, Panel.Timeline), listOf(Panel.Preparation, Panel.MidiQuality, Panel.Status))
    WorkspaceSection.ARRANGE -> SectionPanels(listOf(Panel.Structure), listOf(Panel.Arrangement, Panel.Timeline), listOf(Panel.Status))
    WorkspaceSection.MIX_MASTER -> SectionPanels(listOf(Panel.Timeline), listOf(Panel.Mix), listOf(Panel.Status))
    WorkspaceSection.LIBRARY, WorkspaceSection.VIDEO_PREVIEW, WorkspaceSection.EXPORT, WorkspaceSection.SETTINGS -> SectionPanels(listOf(Panel.Library), emptyList(), listOf(Panel.Status))
}

private enum class Panel { Parts, MidiQuality, Preparation, Structure, Arrangement, Timeline, Mix, Status, Library }

@Composable
private fun PanelColumn(modifier: Modifier, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, panels: List<Panel>) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        panels.forEach { panel ->
            when (panel) {
                Panel.Parts -> PartsPanel(state, onIntent)
                Panel.MidiQuality -> if (state.selectedPartId != null) MidiQualityReviewPanel(state, onIntent)
                Panel.Preparation -> if (state.selectedPartId != null) AudioPreparationPanel(state, onIntent)
                Panel.Structure -> StructurePanel(state, onIntent)
                Panel.Arrangement -> ArrangementPanel(state, onIntent)
                Panel.Timeline -> TimelinePanel(state, onIntent)
                Panel.Mix -> MixPanel(state, onIntent)
                Panel.Library -> LibraryPanel(state, onIntent)
                else -> PlaceholderPanel(panel, state, onIntent)
            }
        }
    }
}

@Composable
internal fun PartsPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.PARTS_PANEL },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    val disabled = state.project == null || state.operation.isMutating
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SCENES / PARTS", style = MaterialTheme.typography.labelLarge, fontSize = MusicWorkspaceTokens.Type.Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.ShowAddPart) },
                enabled = !disabled,
                modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Shell.RailHeaderHeight).semantics {
                    testTag = WorkspaceTags.ADD_MIDI
                    contentDescription = if (state.project == null) "Add part unavailable. Create or open a project first." else "Add a MIDI, WAV, or MP3 part"
                }
            ) { Text("＋  Add Part", style = MaterialTheme.typography.labelMedium) }
        }
    if (state.project?.parts.isNullOrEmpty()) {
        Text("No scenes yet. Import MIDI or solo-piano WAV/MP3 to prepare the first part.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            state.project!!.parts.forEach { part ->
            val selected = state.selectedPartId == part.id
            val playing = (state.playbackSession.request as? PlaybackRequest.Part)?.partId == part.id && state.playbackSession.phase in setOf(PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED)
            val previewCapability = if (part.sourceType == app.melotrail.application.PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
            val previewReadiness = state.runtimeReadiness?.capability(previewCapability)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Shell.PartRowHeight)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(MusicWorkspaceTokens.Radius.Control))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                    .clickable(enabled = !disabled) { onIntent(WorkspaceIntent.SelectPart(part.id)) }
                    .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Shell.PartRowVerticalPadding)
                    .semantics {
                        testTag = WorkspaceTags.PART_ROW_PREFIX + part.id
                        contentDescription = "Part ${part.id}, ${part.sourceType.name.lowercase()}, ${state.partPreparationLabel(part.id)}${if (selected) ", selected" else ""}${if (playing) ", playing" else ""}"
                    },
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(MusicWorkspaceTokens.Shell.PartThumbnailSize).clip(MaterialTheme.shapes.small)
                        .background(if (part.sourceType == app.melotrail.application.PartSourceType.MIDI) MusicWorkspaceTokens.Piano.copy(alpha = 0.20f) else MusicWorkspaceTokens.Warning.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) { Text(if (part.sourceType == app.melotrail.application.PartSourceType.MIDI) "♫" else "⌁", color = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${part.id}  ${part.role.ifBlank { part.sourceName }}", fontWeight = FontWeight.Medium, fontSize = MusicWorkspaceTokens.Type.PartTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val analysis = part.analysis
                    Text("${part.sourceType.name.lowercase()} · ${analysis?.bars?.let { "$it bars" } ?: state.partPreparationLabel(part.id)}", fontSize = MusicWorkspaceTokens.Type.PartMetadata, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (part.preparation.warnings.isNotEmpty()) Text("Needs attention", fontSize = MusicWorkspaceTokens.Type.PartMetadata, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onIntent(WorkspaceIntent.PreviewPart(part.id)) },
                        enabled = !disabled && previewReadiness?.available == true,
                        modifier = Modifier.semantics {
                            testTag = WorkspaceTags.PART_PREVIEW_PREFIX + part.id
                            contentDescription = if (previewReadiness?.available == true) "Preview part ${part.id}" else "Preview part ${part.id} unavailable. ${previewReadiness?.reason ?: "Checking local preview requirements."}"
                        }
                    ) { Text(if (playing) "❚❚" else "▶", fontSize = MusicWorkspaceTokens.Type.PartMetadata) }
                }
            }
                HorizontalDivider(thickness = MusicWorkspaceTokens.Shell.DividerThickness, color = MaterialTheme.colorScheme.outline.copy(alpha = MusicWorkspaceTokens.Shell.DividerAlpha))
            }
        }
    }
    OutlinedButton(
        onClick = { onIntent(WorkspaceIntent.ShowAddPart) },
        enabled = !disabled,
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = WorkspaceTags.IMPORT_ENTRY
            contentDescription = "Import one MIDI, WAV, WAVE, or MP3 source"
        }
    ) { Text("Import source", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
internal fun MidiQualityReviewPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Clean MIDI", WorkspaceTags.MIDI_QUALITY_PANEL) {
    val part = state.selectedPartId?.let { id -> state.project?.parts?.find { it.id == id } } ?: return@WorkspaceCard
    val quality = part.preparation.midiQuality
    Text("Part ${part.id}", fontWeight = FontWeight.Medium)
    val midiPreviewReady = state.runtimeReadiness?.capability(RuntimeCapability.MIDI_PREVIEW)?.available == true
    if (part.preparation.rawMidi) {
        Text("Audition", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, app.melotrail.application.PreviewMidiSource.RAW)) },
                enabled = !state.operation.isMutating && midiPreviewReady
            ) { Text("Preview raw MIDI") }
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, app.melotrail.application.PreviewMidiSource.CLEANED)) },
                enabled = !state.operation.isMutating && midiPreviewReady && part.preparation.cleanMidi
            ) { Text("Preview cleaned MIDI") }
            if (part.preparation.midiFeel.available) {
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, app.melotrail.application.PreviewMidiSource.LOFI_FEEL)) },
                    enabled = !state.operation.isMutating && midiPreviewReady
                ) { Text("Preview Lo-fi Feel") }
            }
        }
    }
    when (quality.status) {
        app.melotrail.application.MidiQualityStatus.LEGACY_UNKNOWN -> {
            Text("Legacy MIDI has no raw-to-clean quality record.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("Analysis and arrangement are blocked until this part is re-imported with MIDI cleanup. Structure may still be edited.", style = MaterialTheme.typography.bodySmall)
        }
        app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID -> {
            Text("Raw MIDI is ready but cleaned MIDI evidence is missing, stale, or invalid.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("Analysis and arrangement are blocked. Run Clean MIDI, review its report, then analyze ${part.id}; structure may still be edited.", style = MaterialTheme.typography.bodySmall)
        }
        app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED -> {
            Text("Cleaning changed more notes or timing than the automatic threshold allows.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text("Review the raw/cleaned previews and report, then explicitly approve before analysis.", style = MaterialTheme.typography.bodySmall)
            quality.report?.let { MidiQualityDiffSummary(it) }
            quality.warnings.forEach { Text("Warning: ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            quality.recommendations.forEach { Text(midiQualityRecommendationText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = { onIntent(WorkspaceIntent.ApproveCleanMidi) }, enabled = !state.operation.isMutating) { Text("Approve Clean MIDI") }
        }
        app.melotrail.application.MidiQualityStatus.CURRENT -> {
            val report = quality.report
            Text("Current profile: ${quality.cleanup?.profile?.qualityProfileLabel() ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
            if (report != null) MidiQualityDiffSummary(report)
            if (quality.warnings.isEmpty()) Text("No quality warnings.", style = MaterialTheme.typography.bodySmall)
            quality.warnings.forEach { Text("Warning: ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            quality.recommendations.forEach { Text(midiQualityRecommendationText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val next = when {
                part.analysis == null -> "Next: analyze ${part.id}."
                state.structureDraft.isEmpty() -> "Next: add at least one structure section before arranging."
                state.project?.readiness?.analysesReady != true -> "Arrangement is blocked until every part has MIDI analysis."
                else -> "This part is ready for structure and arrangement."
            }
            Text(next, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!part.preparation.analyzed || part.analysis?.status != app.melotrail.application.PartAnalysisStatus.MIDI) {
                Button(
                    onClick = { onIntent(WorkspaceIntent.AnalyzePart(part.id)) },
                    enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { contentDescription = "Analyze cleaned MIDI for ${part.id}" }
                ) { Text("Analyze") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Text("MIDI Feel", style = MaterialTheme.typography.labelLarge)
            Text("Choose the canonical MIDI source. Lo-fi MIDI Feel is fixed at 80 BPM and 58% swing; it does not apply Lo-fi audio texture.", style = MaterialTheme.typography.bodySmall)
            val selectedFeel = state.pendingMidiFeel ?: part.preparation.midiFeel.selected
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.CURRENT)) },
                    enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.MIDI_FEEL_ORIGINAL }
                ) { Text(if (selectedFeel == app.melotrail.arrangement.MidiAnalysisInput.CURRENT) "Original selected" else "Original") }
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL)) },
                    enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.MIDI_FEEL_LOFI }
                ) { Text(if (selectedFeel == app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL) "Lo-fi MIDI Feel selected" else "Lo-fi MIDI Feel") }
            }
            if (state.pendingMidiFeel != null && state.pendingMidiFeel != part.preparation.midiFeel.selected) {
                Button(
                    onClick = { onIntent(WorkspaceIntent.ApplyMidiFeelAndReanalyze) },
                    enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.MIDI_FEEL_APPLY }
                ) { Text("Apply and re-analyze") }
            }
            if (part.preparation.midiFeel.available) Text("A/B preview uses the same monitor-volume control for cleaned MIDI and Lo-fi Feel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val correction = part.preparation.technicalCorrection
            if (correction.available) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Text("Technical correction", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (correction.selected == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) "Corrected baseline selected. ${if (correction.approvalRequired) "Review is required before it can be used." else "It remains separate from creative enhancement."}"
                    else "A corrected baseline is available but not selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (correction.approvalRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                correction.warnings.forEach { warning -> Text("Warning: $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (quality.status != app.melotrail.application.MidiQualityStatus.LEGACY_UNKNOWN) {
        Text("Cleaning profile", style = MaterialTheme.typography.labelLarge)
        Text("Choose a named profile. This never edits source MIDI or exposes worker parameters.", style = MaterialTheme.typography.bodySmall)
        MidiCleanupProfile.entries.forEach { profile ->
            val selected = state.midiQualityReview.profile == profile
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.SelectMidiCleanupProfile(profile)) },
                enabled = !state.operation.isMutating,
                modifier = Modifier.semantics { testTag = WorkspaceTags.MIDI_QUALITY_PROFILE_PREFIX + profile.name.lowercase() }
            ) { Text(if (selected) "${profile.qualityProfileLabel()} selected" else profile.qualityProfileLabel()) }
        }
        if (state.midiQualityReview.profile == MidiCleanupProfile.TIGHTEN_TIMING) {
            Text("Timing warning: tighten timing uses a fixed 1/16 grid at 40% strength. It may shift expressive note starts and ends; confirmation is required.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { onIntent(WorkspaceIntent.CleanMidi(part.id)) },
            enabled = !state.operation.isMutating,
            modifier = Modifier.semantics { testTag = WorkspaceTags.MIDI_QUALITY_CLEAN }
        ) { Text("Clean MIDI") }
    }
    if ((state.operation as? WorkspaceOperation.Failed)?.action == "Clean MIDI") {
        Text("Clean MIDI failed: ${(state.operation as WorkspaceOperation.Failed).message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MidiQualityDiffSummary(report: app.melotrail.arrangement.MidiQualityReport) {
    Text("Raw to clean", style = MaterialTheme.typography.labelLarge)
    Text(
        "Notes ${report.raw.noteCount} to ${report.clean.noteCount} · ${formatMetric(report.raw.notesPerSecond)} to ${formatMetric(report.clean.notesPerSecond)} notes/s · polyphony ${report.raw.maximumPolyphony} to ${report.clean.maximumPolyphony}",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        "Timing: ${report.timing.changedStarts} starts, ${report.timing.changedEnds} ends changed; ${report.timing.removedNotes} removed, ${report.timing.addedNotes} added; max shift ${maxOf(report.timing.maxStartShiftTicks, report.timing.maxEndShiftTicks)} ticks.",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        if (report.tempoAndTimeSignaturesPreserved) "Tempo and time signatures preserved." else "Tempo or time signatures changed; review before arranging.",
        color = if (report.tempoAndTimeSignaturesPreserved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

private fun MidiCleanupProfile.qualityProfileLabel(): String = when (this) {
    MidiCleanupProfile.CONSERVATIVE -> "Conservative"
    MidiCleanupProfile.TRANSCRIPTION_SAFE -> "Transcription-safe"
    MidiCleanupProfile.TIGHTEN_TIMING -> "Tighten timing"
}

private fun midiQualityRecommendationText(recommendation: app.melotrail.arrangement.MidiQualityRecommendation): String = when (recommendation) {
    app.melotrail.arrangement.MidiQualityRecommendation.RETRY_TRANSCRIPTION -> "Recommendation: retry transcription before analysis."
    app.melotrail.arrangement.MidiQualityRecommendation.REVIEW_CLEANUP_PROFILE -> "Recommendation: review the cleanup profile before arranging."
    app.melotrail.arrangement.MidiQualityRecommendation.REVIEW_TIMING -> "Recommendation: review timing changes before arranging."
}

@Composable
internal fun AudioPreparationPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Selected-part preparation", WorkspaceTags.PREPARATION_PANEL) {
    val part = state.selectedPartId?.let { id -> state.project?.parts?.find { it.id == id } }
    if (part == null) {
        Text("Select a WAV or MP3 part to inspect its source, compare prepared audio, and choose transcription input.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        return@WorkspaceCard
    }
    if (part.sourceType != app.melotrail.application.PartSourceType.AUDIO) {
        Text("${part.id} is MIDI. Audio preparation applies only to WAV/MP3 sources; its original MIDI remains unchanged.", style = MaterialTheme.typography.bodySmall)
        return@WorkspaceCard
    }
    val preparation = state.audioPreparation
    val snapshot = preparation.snapshot
    Text("Part ${part.id} · Original source is always preserved", fontWeight = FontWeight.Medium)
    when (snapshot?.availability) {
        null, app.melotrail.application.AudioPreparationAvailability.NOT_INSPECTED -> {
            Text("No current inspection report. Inspect only measures the preserved source and does not create or modify audio.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onIntent(WorkspaceIntent.InspectSelectedPart) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_INSPECT }) { Text("Inspect source") }
        }
        app.melotrail.application.AudioPreparationAvailability.STALE -> {
            Text("The inspection report is stale or unavailable. Inspect the preserved source again before cleanup or transcription.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onIntent(WorkspaceIntent.InspectSelectedPart) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_INSPECT }) { Text("Inspect again") }
        }
        app.melotrail.application.AudioPreparationAvailability.NOT_AUDIO -> Unit
        app.melotrail.application.AudioPreparationAvailability.AVAILABLE -> {
            val report = checkNotNull(snapshot.report)
            val metrics = checkNotNull(report.measurements)
            Text("Inspection", style = MaterialTheme.typography.labelLarge)
            Text(
                "${formatDuration(report.durationSeconds)} · ${report.audioFormat?.sampleRate ?: "—"} Hz · ${report.audioFormat?.channels ?: "—"} channel(s) · peak ${formatMetric(metrics.peak)} · DC ${formatMetric(metrics.dcOffset)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Clipped runs: ${metrics.clippedRunCount}; hum evidence: ${metrics.hum.evidence.name.lowercase()}; noise evidence: ${metrics.noise.evidence.name.lowercase()}.", style = MaterialTheme.typography.bodySmall)
            report.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val recommendation = snapshot.safeCleanupPlan
            if (recommendation == null) {
                Text("No measured safe cleanup is recommended. Inspect-only keeps original audio selected for transcription.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Measured safe cleanup recommendation", style = MaterialTheme.typography.labelLarge)
                Text(recommendation.operations.joinToString(" · ") { cleanupOperationLabel(it) }, style = MaterialTheme.typography.bodySmall)
                Text("Only these measured, reversible operations are available. Loudness, silence/timing, pitch, tempo, stems, and the original source are never changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Cleanup choice", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectCleanupMode(app.melotrail.preparation.InputCleanupMode.INSPECT_ONLY)) }) { Text(if (preparation.cleanupMode == app.melotrail.preparation.InputCleanupMode.INSPECT_ONLY) "Inspect only selected" else "Inspect only") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectCleanupMode(app.melotrail.preparation.InputCleanupMode.SAFE_CLEANUP)) }, enabled = recommendation != null) { Text(if (preparation.cleanupMode == app.melotrail.preparation.InputCleanupMode.SAFE_CLEANUP) "Safe cleanup selected" else "Safe cleanup") }
            }
            Button(
                onClick = { onIntent(WorkspaceIntent.ApplySelectedCleanup) },
                enabled = !state.operation.isMutating && (preparation.cleanupMode == app.melotrail.preparation.InputCleanupMode.INSPECT_ONLY || recommendation != null),
                modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_APPLY }
            ) { Text(if (preparation.cleanupMode == app.melotrail.preparation.InputCleanupMode.INSPECT_ONLY) "Record inspect-only choice" else "Review and apply safe cleanup") }

            val cleanAvailable = snapshot.cleanWavAvailable
            Text("A/B monitor", style = MaterialTheme.typography.labelLarge)
            Text("Original and prepared audio use the same monitor volume (${(state.playback.volume * 100).toInt()}%). A/B never changes release files.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewPreparation(app.melotrail.application.PreviewAudioSource.ORIGINAL)) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_ORIGINAL }) { Text("Play original") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewPreparation(app.melotrail.application.PreviewAudioSource.PREPARED_CLEAN)) }, enabled = !state.operation.isMutating && cleanAvailable, modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_CLEAN }) { Text("Play prepared") }
            }
            Text("Active monitor: ${if (state.preview.source?.partId == part.id && state.preview.source?.audioSource == app.melotrail.application.PreviewAudioSource.PREPARED_CLEAN) "prepared clean audio" else "original source"}", style = MaterialTheme.typography.bodySmall)

            Text("Transcription input", style = MaterialTheme.typography.labelLarge)
            Text("Choose exactly which validated project artifact feeds solo-piano transcription.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectTranscriptionInput(app.melotrail.preparation.TranscriptionInputArtifact.SOURCE)) }) { Text(if (preparation.transcriptionInput == app.melotrail.preparation.TranscriptionInputArtifact.SOURCE) "Original selected" else "Original") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectTranscriptionInput(app.melotrail.preparation.TranscriptionInputArtifact.DECODED_WAV)) }, enabled = snapshot.decodedWavAvailable) { Text(if (preparation.transcriptionInput == app.melotrail.preparation.TranscriptionInputArtifact.DECODED_WAV) "Decoded selected" else "Decoded WAV") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectTranscriptionInput(app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV)) }, enabled = cleanAvailable) { Text(if (preparation.transcriptionInput == app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV) "Prepared selected" else "Prepared") }
            }
            Button(onClick = { onIntent(WorkspaceIntent.TranscribeSelectedPart) }, enabled = !state.operation.isMutating && (preparation.transcriptionInput != app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV || cleanAvailable), modifier = Modifier.semantics { testTag = WorkspaceTags.PREPARATION_TRANSCRIBE }) { Text("Run transcription quality gate") }
            report.transcription?.let { transcription ->
                val detail = transcription.metrics?.let { "${it.noteCount} notes · ${formatDuration(it.durationSeconds)} · piano ${it.minPitch}–${it.maxPitch}" }
                Text("Quality gate: ${transcription.status.name.lowercase()}${detail?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Next: analyze ${part.id} after the quality gate succeeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun cleanupOperationLabel(operation: app.melotrail.preparation.CleanupPlanOperation): String = when (operation.type) {
    app.melotrail.preparation.CleanupOperationType.DC_REMOVAL -> "Remove measured DC offset"
    app.melotrail.preparation.CleanupOperationType.CLIP_REPAIR -> "Repair short clipped runs"
    app.melotrail.preparation.CleanupOperationType.DECLICK -> "Repair measured clicks"
    app.melotrail.preparation.CleanupOperationType.HUM_REMOVAL -> "Reduce ${operation.frequencyHz} Hz hum"
    app.melotrail.preparation.CleanupOperationType.NOISE_REDUCTION -> "Gently reduce stationary noise"
}

private fun formatMetric(value: Double): String = String.format(java.util.Locale.ROOT, "%.3f", value)

internal fun previewStatusLabel(phase: PreviewPhase): String = when (phase) {
    PreviewPhase.CHECKING -> "Checking preview prerequisites…"
    PreviewPhase.PREPARING -> "Preparing monitor audio…"
    PreviewPhase.READY -> "Preview ready; starting audio output…"
    PreviewPhase.STARTING -> "Starting audio output…"
    PreviewPhase.PLAYING -> "Playing"
    PreviewPhase.PAUSED -> "Paused"
    PreviewPhase.STOPPED -> "Stopped"
    PreviewPhase.FAILED -> "Preview unavailable"
}

private val workstationInstruments = listOf("piano", "bass", "drums", "pad", "strings")

private data class CenterTimelineSection(
    val section: app.melotrail.application.ArrangementSectionSnapshot,
    val bars: Int?
)

private fun centerTimelineSections(state: WorkspaceUiState, arrangement: app.melotrail.application.ArrangementSnapshot): List<CenterTimelineSection> =
    arrangement.sections.map { section ->
        CenterTimelineSection(section, state.project?.parts?.firstOrNull { it.id == section.partId }?.analysis?.bars?.takeIf { it > 0 })
    }

private fun selectedCenterIndex(state: WorkspaceUiState, indexes: List<Int>): Int? =
    state.selectedArrangementSection?.takeIf { it in indexes } ?: indexes.firstOrNull()

@Composable
internal fun StructurePanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = CenterWorkstationCard(
    title = "Song structure", tag = WorkspaceTags.STRUCTURE_PANEL, height = MusicWorkspaceTokens.Center.StructureHeight
) {
    val disabled = state.project == null || state.operation.isMutating
    val selected = selectedCenterIndex(state, state.structureDraft.indices.toList())
    val timing = state.structureDraft.mapIndexed { index, _ -> state.project?.structure?.getOrNull(index)?.durationSeconds }
    val total = timing.takeIf { it.all { value -> value != null && value > 0.0 } }?.sumOf { it ?: 0.0 }
    CenterCardHeader("SONG STRUCTURE") {
        Text(
            total?.let { "Total ${formatDuration(it)}" } ?: "Timing unavailable",
            modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_TOTAL },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        selected?.let { index ->
            val partId = state.structureDraft[index]
            val occurrence = state.structureDraft.take(index + 1).count { it == partId }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index - 1)) }, enabled = !disabled && index > 0, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_LEFT + index; contentDescription = "Move $partId$occurrence earlier" }) { Text("‹") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index + 1)) }, enabled = !disabled && index < state.structureDraft.lastIndex, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_RIGHT + index; contentDescription = "Move $partId$occurrence later" }) { Text("›") }
            TextButton(onClick = { onIntent(WorkspaceIntent.DuplicateStructurePart(index)) }, enabled = !disabled, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_DUPLICATE; contentDescription = "Duplicate $partId$occurrence" }) { Text("Duplicate") }
        }
        TextButton(onClick = { onIntent(WorkspaceIntent.ClearStructure) }, enabled = !disabled && state.structureDraft.isNotEmpty(), modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_CLEAR }) { Text("Clear") }
    }
    when {
        state.project == null -> Text("Blocked — open a project to create a canonical structure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.structureDraft.isEmpty() -> Text("Empty — add prepared parts in order. Arrangement and timeline remain unavailable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Row(Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Center.SectionBlockHeight).semantics { testTag = WorkspaceTags.STRUCTURE_OVERVIEW }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.structureDraft.forEachIndexed { index, partId ->
                    val occurrence = state.structureDraft.take(index + 1).count { it == partId }
                    val isSelected = index == selected
                    val duration = timing[index]
                    Column(
                        Modifier.weight(timelineSectionWeight(duration)).fillMaxSize().clip(MaterialTheme.shapes.small)
                            .background(if (isSelected) MusicWorkspaceTokens.SelectedSurface else instrumentLaneColors.values.elementAt(index % instrumentLaneColors.size).copy(alpha = 0.16f))
                            .clickable(enabled = !disabled) { onIntent(WorkspaceIntent.SelectArrangementSection(index)) }
                            .padding(MusicWorkspaceTokens.Spacing.Sm)
                            .semantics {
                                testTag = WorkspaceTags.STRUCTURE_OCCURRENCE_PREFIX + index
                                contentDescription = "Section $partId$occurrence${if (isSelected) ", selected" else ""}${duration?.let { ", ${formatDuration(it)}" }.orEmpty()}"
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(partId, style = MaterialTheme.typography.titleMedium, color = if (isSelected) semanticColor(WorkspaceSemanticState.FOCUS) else MaterialTheme.colorScheme.onSurface)
                        Text(state.project.parts.firstOrNull { it.id == partId }?.role?.ifBlank { "Section" } ?: "Unknown part", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (total != null) CenterTimingRuler(state.structureDraft, timing, onIntent) else Text("Timing ruler is unavailable until every selected part has validated duration evidence.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.downstreamArtifactsStale) Text("Structure changed — existing arrangement and render artifacts are stale evidence; regenerate them.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CenterTimingRuler(partIds: List<String>, durations: List<Double?>, onIntent: (WorkspaceIntent) -> Unit) {
    var elapsed = 0.0
    Row(Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.STRUCTURE_RULER; contentDescription = "Canonical structure timing ruler" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        partIds.forEachIndexed { index, _ ->
            val start = elapsed
            elapsed += requireNotNull(durations[index])
            Text(
                formatDuration(start), modifier = Modifier.weight(timelineSectionWeight(durations[index])).clickable { onIntent(WorkspaceIntent.SelectArrangementSection(index)) },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ArrangementPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = CenterWorkstationCard(
    title = "Arrangement", tag = WorkspaceTags.ARRANGEMENT_PANEL, height = MusicWorkspaceTokens.Center.ArrangementHeight
) {
    val arrangement = state.arrangement
    when {
        arrangement == null -> ArrangementPlanningControls(state, onIntent)
        arrangement.stale -> {
            CenterCardHeader("ARRANGEMENT") { Text("Stale", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            Text("Arrangement is stale for the current structure or analyses. Its lanes are retained as evidence but cannot be edited or built.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onIntent(WorkspaceIntent.GenerateArrangement) }, enabled = state.project != null && !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_GENERATE }) { Text("Regenerate arrangement") }
        }
        arrangement.sections.isEmpty() -> Text("No validated arrangement sections are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val selectedIndex = selectedCenterIndex(state, arrangement.sections.map { it.index })
            val section = arrangement.sections.first { it.index == selectedIndex }
            CenterCardHeader("ARRANGEMENT — ${section.instanceId} · ${section.purpose}") {
                Text("ϟ Energy ${(section.energy * 100).toInt()}%", color = semanticColor(WorkspaceSemanticState.PROGRESS), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { onIntent(WorkspaceIntent.GenerateArrangement) }, enabled = state.project != null && !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_GENERATE; contentDescription = "Regenerate arrangement from the current canonical structure and analyses" }) { Text("Regenerate") }
                TextButton(onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE)) }, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_EDIT_SECTION; contentDescription = "Edit selected section in Song Structure" }) { Text("Edit section") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    workstationInstruments.forEach { instrument -> CenterArrangementInstrumentRow(state, section, instrument, onIntent) }
                }
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    val previous = arrangement.sections.sortedBy { it.index }.indexOfFirst { it.index == section.index }.let { position -> arrangement.sections.sortedBy { it.index }.getOrNull(position - 1) }
                    CenterTransitionCard("Transition in", previous?.transition ?: "none", WorkspaceTags.ARRANGEMENT_TRANSITION_IN)
                    CenterTransitionCard("Transition out", section.transition, WorkspaceTags.ARRANGEMENT_TRANSITION_OUT)
                }
            }
            ArrangementReview(state, onIntent)
        }
    }
}

@Composable
private fun ArrangementPlanningControls(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val draft = state.arrangementDraft
    val disabled = state.project == null || state.operation.isMutating
    CenterCardHeader("ARRANGEMENT") { Text("Blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(arrangementPrerequisite(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        PlannerButton("Deterministic", draft.planner.name == "DETERMINISTIC", !disabled) { onIntent(WorkspaceIntent.UpdateArrangementPlanner(app.melotrail.application.ArrangementPlannerKind.DETERMINISTIC)) }
        PlannerButton("Qwen", draft.planner.name == "QWEN", !disabled) { onIntent(WorkspaceIntent.UpdateArrangementPlanner(app.melotrail.application.ArrangementPlannerKind.QWEN)) }
    }
    Text("Role", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        ArrangementRole.entries.forEach { role ->
            val required = role == ArrangementRole.MELODY
            TextButton(
                onClick = { onIntent(WorkspaceIntent.ToggleArrangementRole(role)) },
                enabled = !disabled && !required,
                modifier = Modifier.semantics { contentDescription = "$role ${if (required) "is required" else if (role in draft.roles) "is selected" else "is not selected"} for arrangement generation" }
            ) { Text(if (role in draft.roles) "✓ ${role.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)}" else role.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }
        }
    }
    Text("Desired Character", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        listOf(SoundTrait.SOFT, SoundTrait.WARM, SoundTrait.MUTED, SoundTrait.SUSTAINED, SoundTrait.BRUSHED).forEach { trait ->
            val selected = trait in draft.attackTraits || trait in draft.toneTraits || trait in draft.articulationTraits
            TextButton(onClick = { onIntent(WorkspaceIntent.ToggleArrangementTrait(trait)) }, enabled = !disabled) {
                Text(if (selected) "✓ ${trait.name.lowercase()}" else trait.name.lowercase())
            }
        }
    }
    Text("Suggested / Pinned Instrument — resolved separately; user ownership applies only to a pinned stable ID.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { onIntent(WorkspaceIntent.GenerateArrangement) }, enabled = !disabled, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_GENERATE }) { Text("Generate arrangement") }
}

@Composable
private fun CenterArrangementInstrumentRow(state: WorkspaceUiState, section: app.melotrail.application.ArrangementSectionSnapshot, instrument: String, onIntent: (WorkspaceIntent) -> Unit) {
    val planned = section.instruments.firstOrNull { it.name == instrument }
    val setting = state.mix?.settings?.tracks?.get(instrument) ?: app.melotrail.application.LogicalMixSetting()
    val enabled = state.mix != null && instrument in state.mix.availableStems && !state.mix.stale && !state.operation.isMutating
    val unavailable = "Render current stems before changing $instrument mix controls."
    Row(
        Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Center.TimelineLaneHeight).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface)
            .semantics { testTag = WorkspaceTags.ARRANGEMENT_INSTRUMENT_PREFIX + instrument; contentDescription = "$instrument ${planned?.mode ?: "not arranged"}.${if (enabled) " Mix controls available." else " $unavailable"}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        Text("●", color = instrumentLaneColors.getValue(instrument), modifier = Modifier.padding(start = MusicWorkspaceTokens.Spacing.Sm))
        Text(instrument.replaceFirstChar(Char::uppercase), modifier = Modifier.width(54.dp), style = MaterialTheme.typography.labelMedium)
        Text(planned?.let { it.role ?: it.mode } ?: "Not arranged", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(value = setting.gainDb.toFloat(), onValueChange = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(gainDb = it.toDouble()))) }, valueRange = -24f..12f, enabled = enabled, modifier = Modifier.width(64.dp).height(MusicWorkspaceTokens.Center.ControlHeight))
        Text("%.1f".format(java.util.Locale.ROOT, setting.gainDb), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(solo = !setting.solo))) }, enabled = enabled, modifier = Modifier.height(MusicWorkspaceTokens.Center.ControlHeight).semantics { contentDescription = if (enabled) "${if (setting.solo) "Disable" else "Enable"} solo for $instrument" else unavailable }) { Text("S") }
        TextButton(onClick = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(muted = !setting.muted))) }, enabled = enabled, modifier = Modifier.height(MusicWorkspaceTokens.Center.ControlHeight).semantics { contentDescription = if (enabled) "${if (setting.muted) "Unmute" else "Mute"} $instrument" else unavailable }) { Text("M") }
    }
}

@Composable
private fun CenterTransitionCard(label: String, transition: String, tag: String) = Column(
    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface).padding(MusicWorkspaceTokens.Spacing.Sm).semantics { testTag = tag; contentDescription = "$label: $transition" },
    verticalArrangement = Arrangement.spacedBy(2.dp)
) {
    Text("$label: $transition", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PlannerButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, enabled = enabled) { Text(label) }
    else OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
}

@Composable
private fun ArrangementReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val arrangement = state.arrangement ?: return
    if (arrangement.stale) {
        Text("Stale arrangement: its parts, structure, or planning inputs no longer validate. Regenerate before building.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        return
    }
    if (arrangement.approvalRequired) {
        Text("Validated Qwen draft — review the plan and timeline, then approve explicitly.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewArrangement) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_PREVIEW }) { Text("Preview draft") }
            Button(onClick = { onIntent(WorkspaceIntent.ApproveArrangement) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.ARRANGEMENT_APPROVE }) { Text("Approve") }
        }
    } else if (arrangement.approved) {
        Text("Approved arrangement is current.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun TimelinePanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = CenterWorkstationCard(
    title = "Timeline", tag = WorkspaceTags.TIMELINE_PANEL, height = MusicWorkspaceTokens.Center.TimelineHeight
) {
    val arrangement = state.arrangement
    CenterCardHeader("TIMELINE") {
        Text("Snap · Bar · −  +", modifier = Modifier.semantics { testTag = WorkspaceTags.TIMELINE_CONTROLS; contentDescription = "Timeline view controls are unavailable; this workstation does not provide MIDI editing." }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when {
        arrangement == null -> Text("Blocked — generate a validated arrangement before its canonical timing can be shown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        arrangement.stale -> Text("Timeline is unavailable because the arrangement is stale. Regenerate from current structure and analyses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        arrangement.sections.isEmpty() -> Text("No validated arrangement sections are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val sections = centerTimelineSections(state, arrangement)
            val selected = selectedCenterIndex(state, sections.map { it.section.index })
            CenterBarRuler(sections, onIntent)
            workstationInstruments.forEach { instrument -> CenterTimelineLane(instrument, sections, selected, onIntent) }
            Text("Visual timing overview only — MIDI editing is not available here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenterBarRuler(sections: List<CenterTimelineSection>, onIntent: (WorkspaceIntent) -> Unit) {
    val barsKnown = sections.all { it.bars != null }
    var bar = 1
    Row(Modifier.fillMaxWidth().padding(start = MusicWorkspaceTokens.Center.LaneLabelWidth).semantics { testTag = WorkspaceTags.TIMELINE_RULER; contentDescription = if (barsKnown) "Canonical bar ruler" else "Bar positions unavailable until analyses provide bar counts" }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        sections.forEach { visual ->
            val label = if (barsKnown) bar.toString() else "—"
            Text(label, modifier = Modifier.weight(timelineSectionWeight(visual.section.durationSeconds)).clickable { onIntent(WorkspaceIntent.SelectArrangementSection(visual.section.index)) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            bar += visual.bars ?: 0
        }
    }
}

@Composable
private fun CenterTimelineLane(instrument: String, sections: List<CenterTimelineSection>, selected: Int?, onIntent: (WorkspaceIntent) -> Unit) {
    val lane = checkNotNull(instrumentLane(instrument))
    val color = lane.color
    Row(Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Center.TimelineLaneHeight).semantics { testTag = WorkspaceTags.TIMELINE_LANE_PREFIX + instrument; contentDescription = "$instrument visual timeline lane" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${lane.icon} ${lane.label}", modifier = Modifier.width(MusicWorkspaceTokens.Center.LaneLabelWidth), color = color, style = MaterialTheme.typography.labelMedium)
        sections.forEachIndexed { sectionPosition, visual ->
            val active = visual.section.instruments.any { it.name == instrument }
            Box(
                Modifier.weight(timelineSectionWeight(visual.section.durationSeconds)).height(MusicWorkspaceTokens.Center.TimelineLaneHeight - 4.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(if (active) color.copy(alpha = if (visual.section.index == selected) 0.68f else 0.38f) else MaterialTheme.colorScheme.surface)
                    .semantics {
                        if (instrument == "piano") {
                            testTag = WorkspaceTags.TIMELINE_SECTION_PREFIX + visual.section.index
                            onClick("Select ${visual.section.instanceId}") {
                                onIntent(WorkspaceIntent.SelectArrangementSection(visual.section.index))
                                true
                            }
                        }
                        contentDescription = "$instrument lane, ${visual.section.instanceId}, ${if (active) "active" else "inactive"}, canonical duration ${visual.section.durationSeconds?.let(::formatDuration) ?: "unavailable"}"
                    }
                    .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(visual.section.index)) }
            ) {
                if (active) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2 + ((sectionPosition + instrument.length) % 3)) { clip ->
                            Box(Modifier.weight(1f).height(2.dp).background(MusicWorkspaceTokens.Canvas.copy(alpha = 0.68f)))
                        }
                    }
                }
                if (instrument == "piano" && visual.section.index == selected) Box(Modifier.width(2.dp).height(MusicWorkspaceTokens.Center.TimelineLaneHeight).background(semanticColor(WorkspaceSemanticState.FOCUS)).semantics { testTag = WorkspaceTags.TIMELINE_CURSOR; contentDescription = "Selected section cursor at ${visual.section.instanceId}" })
            }
        }
    }
}

@Composable
internal fun LibraryPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Sound library", WorkspaceTags.LIBRARY_PANEL) {
    Text("Sound-library configuration and local dependency recovery are available from the shell Settings gear.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(
        onClick = { onIntent(WorkspaceIntent.OpenSettings) },
        modifier = Modifier.semantics { testTag = WorkspaceTags.SOUND_LIBRARY_SETTINGS; contentDescription = "Open shell sound-library settings" }
    ) { Text("Open Settings") }
}

@Composable
internal fun CompactTransport(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) {
    val session = state.playbackSession
    val hasPlayableSelection = when (val request = session.request) {
        is PlaybackRequest.Part -> session.phase in setOf(PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED) || (session.phase == PlaybackSessionPhase.STOPPED && session.artifact != null)
        is PlaybackRequest.Mix -> session.phase in setOf(PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED) || playbackSourceAvailable(state, request.source)
        null -> playbackSourceAvailable(state, PlaybackSource.DRY)
    }
    val canStop = session.phase in setOf(PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED)
    val canSeek = session.artifact != null && session.durationSeconds > 0.0 && session.phase in setOf(PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED, PlaybackSessionPhase.STOPPED)
    val label = when (val request = session.request) {
        is PlaybackRequest.Part -> "Part ${request.partId} preview"
        is PlaybackRequest.Mix -> request.source.name.lowercase().replaceFirstChar(Char::uppercase) + " mix"
        null -> "Dry mix"
    }
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 82.dp).semantics {
            testTag = WorkspaceTags.COMPACT_TRANSPORT
            contentDescription = "Persistent song transport"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Lg, vertical = MusicWorkspaceTokens.Spacing.Sm)) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            val controls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onIntent(WorkspaceIntent.PlayPause) }, enabled = hasPlayableSelection,
                        modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_TOGGLE; contentDescription = if (hasPlayableSelection) "Play or pause $label" else "Playback unavailable. Select a ready preview or build a current mix first." }
                    ) { Text(if (session.phase == PlaybackSessionPhase.PLAYING) "Pause" else "Play") }
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = canStop) { Text("Stop") }
                    if (session.phase == PlaybackSessionPhase.FAILED && session.retryAction == PlaybackRetryAction.RETRY_SAME_SELECTION) {
                        OutlinedButton(
                            onClick = { onIntent(WorkspaceIntent.RetryPreview) },
                            modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_RETRY; contentDescription = "Retry $label" }
                        ) { Text("Retry") }
                    }
                }
            }
            val progress: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Text("${formatDuration(session.positionSeconds)} / ${formatDuration(session.durationSeconds)}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (session.request is PlaybackRequest.Part) {
                        Text(previewStatusLabel(state.preview.phase), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.preview.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Slider(
                        value = session.positionSeconds.toFloat(),
                        onValueChange = { onIntent(WorkspaceIntent.SeekPlayback(it.toDouble())) },
                        valueRange = 0f..session.durationSeconds.coerceAtLeast(0.01).toFloat(), enabled = canSeek,
                        modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_SEEK; contentDescription = "Seek $label" }
                    )
                }
            }
            if (narrow) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                controls()
                progress()
                FooterWaveform(session, Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                controls()
                Column(Modifier.weight(1f)) { progress() }
                FooterWaveform(session, Modifier.weight(1.15f))
            }
        }
    }
}

/**
 * The playback boundary exposes no decoded sample frames. This is therefore a
 * deterministic timeline placeholder, visibly labelled so it never claims to
 * be an audio waveform or a live signal meter.
 */
@Composable
private fun FooterWaveform(session: PlaybackSession, modifier: Modifier = Modifier) {
    Box(
        modifier.height(52.dp).clip(MaterialTheme.shapes.small).background(MusicWorkspaceTokens.Canvas.copy(alpha = 0.74f))
            .semantics {
                testTag = WorkspaceTags.FOOTER_WAVEFORM
                contentDescription = if (session.artifact != null) "Decoded waveform unavailable for the selected playback artifact." else "Waveform unavailable because no playback artifact is selected."
            },
        contentAlignment = Alignment.Center
    ) {
        Text("WAVEFORM UNAVAILABLE", style = MaterialTheme.typography.labelSmall, color = semanticColor(WorkspaceSemanticState.DISABLED))
    }
}

@Composable
internal fun MixPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Mix & transport", WorkspaceTags.MIX_PANEL) {
    val mix = state.mix
    val disabled = state.project == null || state.operation.isMutating
    if (mix == null || mix.availableStems.isEmpty()) {
        Text("Render or build the approved arrangement to create compatible stems and a dry mix.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        mix.availableStems.forEach { instrument ->
            val setting = mix.settings.tracks[instrument] ?: app.melotrail.application.LogicalMixSetting()
            Column(modifier = Modifier.semantics { testTag = WorkspaceTags.MIX_TRACK_PREFIX + instrument; contentDescription = "$instrument mix track" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row { Text("${instrumentLane(instrument)?.icon.orEmpty()} ${instrumentLane(instrument)?.label ?: instrument.replaceFirstChar(Char::uppercase)}".trim(), modifier = Modifier.weight(1f), color = instrumentLane(instrument)?.color ?: MaterialTheme.colorScheme.onSurface); Text("%.1f dB".format(setting.gainDb)) }
                Slider(value = setting.gainDb.toFloat(), onValueChange = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(gainDb = it.toDouble()))) }, valueRange = -24f..12f, enabled = !disabled)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(muted = !setting.muted))) }, enabled = !disabled) { Text(if (setting.muted) "Unmute" else "Mute") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(solo = !setting.solo))) }, enabled = !disabled) { Text(if (setting.solo) "Unsolo" else "Solo") }
                    Text("Pan %.2f".format(setting.pan), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
                }
                Slider(value = setting.pan.toFloat(), onValueChange = { onIntent(WorkspaceIntent.UpdateMixSetting(instrument, setting.copy(pan = it.toDouble()))) }, valueRange = -1f..1f, enabled = !disabled)
            }
        }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.ResetMix) }, enabled = !disabled, modifier = Modifier.semantics { testTag = WorkspaceTags.MIX_RESET }) { Text("Reset engine defaults") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    Text("Build options", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Checkbox(state.buildOptions.loFi, { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFi = it))) }, enabled = !disabled); Text("Lo-fi audio texture", modifier = Modifier.padding(top = 12.dp))
        Checkbox(state.buildOptions.mp3, { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(mp3 = it))) }, enabled = !disabled); Text("MP3", modifier = Modifier.padding(top = 12.dp))
    }
    BuildLifecycle(state, onIntent)
    CommercialReadinessPanel(state, onIntent)
    PlaybackSourceSelector(state, onIntent)
}

/** A presentation-only adapter over project readiness; report generation remains a typed application service. */
@Composable
private fun CommercialReadinessPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Commercial & YouTube", WorkspaceTags.COMMERCIAL_READINESS) {
    val readiness = state.project?.readiness
    val evidence = state.commercialEvidence
    val ready = evidence?.commercialReady == true
    Text(if (ready) "Commercial-ready evidence complete" else "Commercial-ready requires evidence review", style = MaterialTheme.typography.labelLarge)
    Text(
        if (evidence == null) "Create evidence to verify the exact selected source-to-export lineage."
        else if (ready) "The selected lineage is hash-verified. Review the report before release; this is not a legal ownership claim."
        else "Resolve the listed source, model, license, attribution, or lineage evidence before calling this release Commercial-ready.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    evidence?.let { current ->
        if (current.unresolvedActions.isNotEmpty()) {
            Text("Unresolved actions", style = MaterialTheme.typography.labelMedium)
            current.unresolvedActions.forEach { action -> Text("• $action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        Text("Report: ${current.reportReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Manifest: ${current.manifestReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("For AI-generated music, use the YouTube Studio AI-use disclosure when the policy applies; disclosure is not a monetization guarantee.", style = MaterialTheme.typography.bodySmall)
    Text("Add required attribution and original, non-mass-produced video/channel value. This is evidence and workflow assistance—not legal advice, copyright clearance, Content ID clearance, or a monetization guarantee.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { onIntent(WorkspaceIntent.ExportCommercialProvenance) }, enabled = readiness?.releaseAvailable == true && !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.COMMERCIAL_EXPORT }) { Text("Create commercial evidence") }
}

@Composable
private fun BuildLifecycle(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val progress = (state.operation as? WorkspaceOperation.BuildingSong)?.progress
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { testTag = WorkspaceTags.BUILD_LIFECYCLE },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Build Song", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(buildSongPrerequisite(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("The service validates, generates/reuses MIDI and stems, mixes, repairs, optionally applies Lo-fi audio texture/MP3, masters, then writes release metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Stems are reused only when their canonical fingerprints are current; cancellation waits for the current atomic stage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.stageIndex.toFloat() / progress.stageCount }, modifier = Modifier.fillMaxWidth())
            Text("Stage ${progress.stageIndex} of ${progress.stageCount}: ${progress.message}", style = MaterialTheme.typography.bodySmall)
            progress.artifact?.let { Text("Current artifact: ${it.fileName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.CancelOperation) }, modifier = Modifier.semantics { testTag = WorkspaceTags.BUILD_CANCEL }) { Text("Cancel at boundary") }
        } else {
            Button(onClick = { onIntent(WorkspaceIntent.BuildSong) }, enabled = canBuild(state), colors = workspacePrimaryButtonColors(), modifier = Modifier.semantics { testTag = WorkspaceTags.BUILD_START }) { Text("Start Build Song") }
        }
        state.project?.readiness?.let { readiness ->
            Text(
                "Available: dry ${availabilityLabel(readiness.dryMixAvailable)}, Lo-fi audio texture ${availabilityLabel(readiness.loFiMixAvailable)}, master ${availabilityLabel(readiness.masterAvailable)}, release ${availabilityLabel(readiness.releaseAvailable)}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun availabilityLabel(available: Boolean): String = if (available) "ready" else "not yet built"

private fun playbackSourceAvailable(state: WorkspaceUiState, source: PlaybackSource): Boolean {
    val project = state.project ?: return false
    if (state.downstreamArtifactsStale) return false
    return when (source) {
        PlaybackSource.DRY -> project.readiness.dryMixAvailable
        PlaybackSource.LOFI -> project.readiness.loFiMixAvailable
        PlaybackSource.MASTER -> project.readiness.masterAvailable
    }
}

@Composable
private fun PlaybackSourceSelector(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val source = (state.playbackSession.request as? PlaybackRequest.Mix)?.source ?: PlaybackSource.DRY
    fun enabled(value: PlaybackSource) = playbackSourceAvailable(state, value)
    Text("Audition source", style = MaterialTheme.typography.labelLarge)
    Text("Choose the release artifact controlled by the persistent footer transport.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.DRY)) }, enabled = enabled(PlaybackSource.DRY), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_DRY }) { Text("Dry") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.LOFI)) }, enabled = enabled(PlaybackSource.LOFI), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_LOFI }) { Text("Lo-fi audio texture") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.MASTER)) }, enabled = enabled(PlaybackSource.MASTER), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_MASTER }) { Text("Master") }
    }
    val selectedEnabled = enabled(source)
    if (!selectedEnabled) Text("${source.name.lowercase().replaceFirstChar(Char::uppercase)} is unavailable or stale. Build Song creates current audition artifacts.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PlaceholderPanel(panel: Panel, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val (title, detail, tag) = when (panel) {
        Panel.Status -> Triple("Operation status", statusText(state), WorkspaceTags.OPERATION_STATUS)
        else -> error("Functional panels are handled separately")
    }
    WorkspaceCard(title, tag) {
        if (panel == Panel.Status) {
            OperationStatusSurface(state, onIntent)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ReadinessRecovery(state.runtimeReadiness, onIntent)
        } else {
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
internal fun OperationStatusSurface(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val feedback = state.operationFeedback
    val (label, color) = when {
        feedback.active -> "Loading · ${feedback.phase.label()}" to semanticColor(WorkspaceSemanticState.PROGRESS)
        feedback.outcomeSeverity == OperationSeverity.ERROR -> "Error" to semanticColor(WorkspaceSemanticState.ERROR)
        feedback.outcomeSeverity == OperationSeverity.WARNING -> "Warning" to semanticColor(WorkspaceSemanticState.WARNING)
        feedback.outcomeSeverity == OperationSeverity.INFORMATION -> "Information" to semanticColor(WorkspaceSemanticState.INFORMATION)
        feedback.outcomeSeverity == OperationSeverity.SUCCESS -> "Complete" to semanticColor(WorkspaceSemanticState.READY)
        else -> "Ready" to semanticColor(WorkspaceSemanticState.READY)
    }
    Column(
        modifier = Modifier.semantics {
            testTag = WorkspaceTags.OPERATION_FEEDBACK
            liveRegion = LiveRegionMode.Polite
            contentDescription = "$label: ${if (feedback.phase == OperationPhase.IDLE) detailForIdle(state) else feedback.message}"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        Text(if (feedback.phase == OperationPhase.IDLE) detailForIdle(state) else feedback.message, color = MaterialTheme.colorScheme.onSurface)
        feedback.artifactLabel?.let { Text("Artifact: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (feedback.active) {
            if (feedback.determinate) {
                val work = checkNotNull(feedback.work)
                LinearProgressIndicator(progress = { work.completed.toFloat() / work.total }, modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.IMPORT_PROGRESS })
                Text("${work.completed}/${work.total} steps", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.IMPORT_PROGRESS })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            if (feedback.retryAction != null && state.retry != null) {
                TextButton(onClick = { onIntent(WorkspaceIntent.Retry) }, modifier = Modifier.semantics { testTag = WorkspaceTags.GLOBAL_FEEDBACK_RETRY }) { Text("Retry") }
            }
            if (feedback.cancellableAtBoundary) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.CancelOperation) }, modifier = Modifier.semantics { testTag = WorkspaceTags.BUILD_CANCEL }) { Text("Cancel at boundary") }
            }
            if (!feedback.active && feedback.phase != OperationPhase.IDLE) {
                TextButton(onClick = { onIntent(WorkspaceIntent.DismissNotification) }, modifier = Modifier.semantics { testTag = WorkspaceTags.GLOBAL_FEEDBACK_DISMISS }) { Text("Dismiss") }
            }
        }
    }
}

private fun OperationPhase.label(): String = when (this) {
    OperationPhase.LOCAL -> "working locally"
    OperationPhase.WAITING_FOR_WORKER -> "waiting for worker"
    OperationPhase.WAITING_FOR_MODEL -> "waiting for model"
    OperationPhase.WAITING_FOR_RENDERER -> "waiting for renderer"
    OperationPhase.VALIDATING -> "validating"
    OperationPhase.CANCELLING -> "reaching safe boundary"
    else -> name.lowercase()
}

private fun detailForIdle(state: WorkspaceUiState): String = state.project?.let { "Ready · ${it.name} is open." } ?: "Ready. Create or open a project to begin."

@Composable
private fun ReadinessRecovery(readiness: RuntimeReadiness?, onIntent: (WorkspaceIntent) -> Unit) {
    Column(
        modifier = Modifier.semantics { testTag = WorkspaceTags.READINESS_RECOVERY },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        Text("Local readiness", style = MaterialTheme.typography.labelLarge)
        if (readiness == null) {
            Text("Checking local dependency readiness…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            RuntimeDependency.entries.forEach { dependency ->
                val item = readiness.dependency(dependency)
                Text(
                    "${dependency.name.lowercase().replace('_', ' ')}: ${item.status.name.lowercase()}${if (item.available) "" else " — ${item.detail}"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.RefreshRuntimeReadiness) }) { Text("Refresh readiness") }
    }
}

@Composable
internal fun WorkspaceCard(title: String, tag: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Column(modifier = Modifier.padding(MusicWorkspaceTokens.Spacing.Lg), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            content()
        }
    }
}

/** Reusable fixed-density card shell for the reference center workstation. */
@Composable
private fun CenterWorkstationCard(title: String, tag: String, height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(height).semantics { testTag = tag; contentDescription = title },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Lg), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            content()
        }
    }
}

@Composable
private fun CenterCardHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

private fun statusText(state: WorkspaceUiState): String = when (val operation = state.operation) {
    WorkspaceOperation.Idle -> state.project?.let { "Ready · ${it.name} is open." } ?: "Ready. Create or open a project to begin."
    is WorkspaceOperation.OpeningProject -> "Opening ${operation.root.fileName}…"
    WorkspaceOperation.SavingProjectSetup -> "Saving project setup…"
    WorkspaceOperation.SavingHarmony -> "Saving structured harmony…"
    is WorkspaceOperation.CreatingProject -> "Creating ${operation.root.fileName}…"
    is WorkspaceOperation.ImportingPart -> "Preparing ${operation.id}…"
    is WorkspaceOperation.AnalyzingPart -> "Analyzing ${operation.id}…"
    is WorkspaceOperation.InspectingPart -> "Inspecting preserved source for ${operation.id}…"
    is WorkspaceOperation.ApplyingAudioCleanup -> "Applying selected cleanup for ${operation.id}…"
    is WorkspaceOperation.CleaningMidi -> "Cleaning MIDI for ${operation.id}…"
    is WorkspaceOperation.SelectingMidiFeel -> "Selecting Lo-fi Feel for ${operation.id}…"
    is WorkspaceOperation.SelectingEnhancement -> "Selecting enhancement for ${operation.id}…"
    is WorkspaceOperation.CreatingMidiAiFix -> "Creating a bounded AI-fix draft for ${operation.id}…"
    is WorkspaceOperation.ApprovingMidiAiFix -> "Approving AI-fix draft for ${operation.id}…"
    is WorkspaceOperation.TranscribingPart -> "Running transcription quality gate for ${operation.id}…"
    is WorkspaceOperation.UpdatingPartRole -> "Saving ${operation.id} role…"
    WorkspaceOperation.SavingStructure -> "Saving song structure…"
    is WorkspaceOperation.GeneratingCohesion -> "Generating arrangement-aware boundary cohesion…"
    is WorkspaceOperation.ReviewingCohesion -> "Recording review for cohesion boundary ${operation.outgoingInstanceId} → ${operation.incomingInstanceId}…"
    WorkspaceOperation.ApprovingCohesion -> "Approving validated cohesion…"
    is WorkspaceOperation.GeneratingArrangement -> "Generating reviewed song plan and detailed arrangement…"
    is WorkspaceOperation.ApplyingMix -> "Applying persisted mix settings to existing stems…"
    WorkspaceOperation.Humanizing -> "Creating deterministic humanization evidence…"
    WorkspaceOperation.ExportingCommercialProvenance -> "Writing hash-bound commercial provenance evidence…"
    WorkspaceOperation.ExportingRelease -> "Validating and publishing release export…"
    is WorkspaceOperation.BuildingSong -> "Building song through the lossless release pipeline…"
    WorkspaceOperation.ApprovingArrangement -> "Approving validated arrangement…"
    is WorkspaceOperation.OpenFailed -> operation.message
    is WorkspaceOperation.Failed -> operation.message
}

@Composable
private fun WorkspaceDialogs(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, onExit: () -> Unit) {
    when (val dialog = state.dialog) {
        is WorkspaceDialog.CreateProject -> CreateProjectDialog(dialog, onIntent)
        is WorkspaceDialog.ImportPart -> ImportPartDialog(dialog, onIntent)
        is WorkspaceDialog.EditRole -> EditRoleDialog(dialog, onIntent)
        is WorkspaceDialog.ConfirmSectionChange -> ConfirmSectionChangeDialog(dialog, onIntent)
        is WorkspaceDialog.ConfirmPartStructureChange -> ConfirmPartStructureChangeDialog(dialog, onIntent)
        is WorkspaceDialog.PartDetails -> PartDetailsDialog(state, dialog, onIntent)
        is WorkspaceDialog.ConfirmSafeCleanup -> ConfirmSafeCleanupDialog(dialog, onIntent)
        is WorkspaceDialog.ConfirmTightenTiming -> ConfirmTightenTimingDialog(dialog, onIntent)
        is WorkspaceDialog.ConfirmSourceKey -> ConfirmSourceKeyDialog(dialog, onIntent)
        is WorkspaceDialog.ConfirmDiscardDraft -> ConfirmDiscardDraftDialog(dialog, onIntent)
        WorkspaceDialog.ConfirmClose -> ConfirmCloseDialog(onIntent, onExit)
        WorkspaceDialog.ConfirmClearSoundLibraryRoot -> ConfirmClearSoundLibraryRootDialog(onIntent)
        null -> Unit
    }
}

@Composable
private fun PartDetailsDialog(state: WorkspaceUiState, dialog: WorkspaceDialog.PartDetails, onIntent: (WorkspaceIntent) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(dialog.partId) { focusRequester.requestFocus() }
    val part = state.project?.parts?.firstOrNull { it.id == dialog.partId }
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Part details · ${dialog.partId}") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).focusRequester(focusRequester).focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                            onIntent(WorkspaceIntent.DismissDialog)
                            true
                        } else false
                    }
                    .semantics {
                        testTag = WorkspaceTags.PART_DETAILS_DIALOG
                        contentDescription = "Details for selected part ${dialog.partId}"
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (part?.sourceType) {
                    app.melotrail.application.PartSourceType.MIDI -> MidiQualityReviewPanel(state, onIntent)
                    app.melotrail.application.PartSourceType.AUDIO -> AudioPreparationPanel(state, onIntent)
                    else -> Text("This source is no longer supported. Re-import a validated MIDI, WAV, or MP3 source.", color = MaterialTheme.colorScheme.error)
                }
                part?.let { PartArtifactComparisonPanel(state, it, onIntent) }
                val failed = state.operation as? WorkspaceOperation.Failed
                if (failed != null) {
                    Text("${failed.action}: ${failed.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (state.retry != null) Button(onClick = { onIntent(WorkspaceIntent.Retry) }) { Text("Retry safely") }
                }
                if (state.operation.isMutating) {
                    Text("Controls are temporarily unavailable while ${statusText(state)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = { onIntent(WorkspaceIntent.DismissDialog) },
                modifier = Modifier.semantics { testTag = WorkspaceTags.PART_DETAILS_CLOSE }
            ) { Text("Close") }
        }
    )
}

@Composable
private fun PartArtifactComparisonPanel(state: WorkspaceUiState, part: app.melotrail.application.PartSummary, onIntent: (WorkspaceIntent) -> Unit) {
    val project = state.project ?: return
    val choices = availablePartArtifactComparisons(project, part)
    Card(Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.PART_COMPARISON }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text("Compare representations", fontWeight = FontWeight.SemiBold)
            Text("Play controls prepare the named, hash-validated source. They do not replace project artifacts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            choices.forEach { choice ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (choice.current) "${choice.label} · Current" else choice.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${choice.runLabel} — ${choice.detail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            when (val preview = choice.preview) {
                                is PartArtifactPreview.Audio -> onIntent(WorkspaceIntent.PreviewPreparation(preview.source))
                                is PartArtifactPreview.Midi -> onIntent(WorkspaceIntent.PreviewMidiPart(part.id, preview.source))
                            }
                        },
                        modifier = Modifier.semantics { testTag = WorkspaceTags.PART_COMPARISON_PLAY_PREFIX + choice.kind.name.lowercase(); contentDescription = "Play ${choice.label} for A/B comparison" }
                    ) { Text("Play") }
                }
            }
            val review = state.enhancementReview?.takeIf { it.partId == part.id }
            if (part.preparation.technicalCorrection.available && !part.preparation.technicalCorrection.approvalRequired &&
                part.preparation.technicalCorrection.selected == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.SelectEnhancement(app.melotrail.arrangement.EnhancementIntensity.OFF)) }, enabled = !state.operation.isMutating) { Text("Use Corrected (AI Off)") }
                    app.melotrail.arrangement.EnhancementIntensity.entries.filter { it != app.melotrail.arrangement.EnhancementIntensity.OFF }.forEach { intensity ->
                        TextButton(onClick = { onIntent(WorkspaceIntent.SelectEnhancement(intensity)) }, enabled = !state.operation.isMutating) { Text(if (review == null) "Generate ${intensity.name.lowercase()}" else "Rerun ${intensity.name.lowercase()}") }
                    }
                }
            }
            if (review != null && review.approval == app.melotrail.arrangement.EnhancementApproval.DRAFT) {
                Text("Draft edit report: ${review.edits} edits${review.reasons.takeIf { it.isNotEmpty() }?.joinToString(prefix = " — ") ?: ""}", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    Button(onClick = { onIntent(WorkspaceIntent.ApproveEnhancement) }, enabled = !state.operation.isMutating) { Text("Approve Enhanced") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.RejectEnhancement) }, enabled = !state.operation.isMutating) { Text("Reject draft") }
                }
            }
            if (review?.approval == app.melotrail.arrangement.EnhancementApproval.APPROVED && choices.any { it.kind == PartArtifactKind.ENHANCED && !it.current }) {
                TextButton(onClick = { onIntent(WorkspaceIntent.SelectApprovedEnhancement) }, enabled = !state.operation.isMutating) { Text("Use Enhanced") }
            }
            if (state.downstreamArtifactsStale || project.readiness.staleArtifacts.isNotEmpty()) {
                Text("Changing the selected representation requires downstream analysis and build artifacts to be regenerated; retained artifacts remain inspectable.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            (state.playbackSession.artifact?.source)?.takeIf { state.playbackSession.artifact.partId == part.id }?.let { identity ->
                Text("Playing ${identity.label} · ${identity.sha256.take(12)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConfirmSafeCleanupDialog(dialog: WorkspaceDialog.ConfirmSafeCleanup, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Apply safe cleanup to ${dialog.partId}?") },
        text = { Text("This creates a separate prepared clean WAV from the measured recommendation. The original source remains available and unchanged. Continue only if you want this derived monitor/transcription option.") },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmSafeCleanup) }) { Text("Apply safe cleanup") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep original") } }
    )
}

@Composable
private fun ConfirmTightenTimingDialog(dialog: WorkspaceDialog.ConfirmTightenTiming, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Tighten timing for ${dialog.partId}?") },
        text = { Text("This Clean MIDI run uses the fixed 1/16 grid at 40% strength. It can move expressive note timing. The raw source MIDI remains unchanged; cleaned MIDI, its quality report, analysis, and preview fingerprint will be replaced or refreshed only after validation.") },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmTightenTiming) }) { Text("Clean with timing changes") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep current cleanup") } }
    )
}

@Composable
private fun ConfirmSourceKeyDialog(dialog: WorkspaceDialog.ConfirmSourceKey, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Confirm source key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text("Choose the source tonic and mode. This does not change the original or normalized MIDI; it authorizes a separate project-key transpose artifact.")
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    app.melotrail.music.MusicalOptionModels.tonics.forEach { option ->
                        TextButton(onClick = { onIntent(WorkspaceIntent.SelectConfirmedSourceKey(dialog.selected.copy(tonic = option.value))) }) { Text(option.label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.SelectConfirmedSourceKey(dialog.selected.copy(modeId = app.melotrail.music.ScaleModeId.MAJOR))) }) { Text("Major") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.SelectConfirmedSourceKey(dialog.selected.copy(modeId = app.melotrail.music.ScaleModeId.NATURAL_MINOR))) }) { Text("Natural minor") }
                }
                Text("Selected: ${dialog.selected.displayName}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmSourceKey) }) { Text("Confirm source key") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmClearSoundLibraryRootDialog(onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Clear saved sound-library preference?") },
        text = { Text("This removes only the locally saved library path. Project data, audio, samples, and the MUSIC_SOUNDS_ROOT override are unchanged.") },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmClearSoundLibraryRoot) }, modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_CLEAR_CONFIRM }) { Text("Clear preference") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep preference") } }
    )
}

@Composable
private fun ConfirmDiscardDraftDialog(draft: WorkspaceDialog.ConfirmDiscardDraft, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Discard arrangement draft?") },
        text = { Text("Your planner, style, and instrument selections have not been generated or saved. ${if (draft.root != null) "Opening another project" else "Creating a project"} will discard them. Project artifacts are unchanged.") },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmDiscardDraft) }) { Text("Discard and continue") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep editing") } }
    )
}

@Composable
private fun ConfirmCloseDialog(onIntent: (WorkspaceIntent) -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Close Melotrail?") },
        text = { Text("A draft is unsaved or an operation is still running. Closing requests cancellation at a safe boundary; canonical project files are never replaced mid-write.") },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmClose); onExit() }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep working") } }
    )
}

@Composable
private fun CreateProjectDialog(draft: WorkspaceDialog.CreateProject, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Create project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose an empty or new folder. Projects always render lossless PCM-24 WAV.")
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.ChooseCreateProjectDirectory) }) { Text(draft.root?.toString() ?: "Choose folder") }
                OutlinedTextField(draft.name, { onIntent(WorkspaceIntent.UpdateCreateProject(draft.copy(name = it))) }, label = { Text("Song name") })
                OutlinedTextField(draft.sampleRate, { onIntent(WorkspaceIntent.UpdateCreateProject(draft.copy(sampleRate = it))) }, label = { Text("Sample rate") })
                OutlinedTextField(draft.channels, { onIntent(WorkspaceIntent.UpdateCreateProject(draft.copy(channels = it))) }, label = { Text("Channels") })
            }
        },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.CreateProject) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun ImportPartDialog(draft: WorkspaceDialog.ImportPart, onIntent: (WorkspaceIntent) -> Unit) {
    val type = draft.detectedType
    val currentStep = importFlowStep(draft)
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Import source") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportStepIndicator(currentStep)
                Text("1. Select one MIDI or eligible solo-piano audio source.", fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.ChooseImportSource) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.IMPORT_SOURCE }
                ) { Text(draft.source?.fileName?.toString() ?: "Browse source") }
                if (draft.source == null) {
                    Text("MIDI, WAV, WAVE, and MP3 are accepted. Audio is limited to the solo-piano transcription route.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    HorizontalDivider()
                    Text("2. Inspect and validate", fontWeight = FontWeight.SemiBold)
                    Text("${draft.source.fileName} · ${type?.label ?: "unknown"} · ${formatFileSize(draft.sourceSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Route: ${importRoute(type)}. The canonical importer validates the actual file container before immutable publication.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(name = it))) },
                        label = { Text("Melody part name") },
                        supportingText = { Text("The stable ID is ${draft.id}; this display name can be changed later.") }
                    )
                    Text("Section", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            app.melotrail.arrangement.SectionTypeId.INTRO,
                            app.melotrail.arrangement.SectionTypeId.VERSE,
                            app.melotrail.arrangement.SectionTypeId.CHORUS,
                            app.melotrail.arrangement.SectionTypeId.BRIDGE,
                            app.melotrail.arrangement.SectionTypeId.OUTRO
                        ).forEach { section ->
                            val selected = draft.sectionType == section
                            if (selected) Button(onClick = { }, modifier = Modifier.semantics { contentDescription = "${app.melotrail.arrangement.SectionTypeCatalog.label(section)} section selected" }) {
                                Text(app.melotrail.arrangement.SectionTypeCatalog.label(section))
                            } else OutlinedButton(onClick = { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(sectionType = section, role = section.value))) }) {
                                Text(app.melotrail.arrangement.SectionTypeCatalog.label(section))
                            }
                        }
                    }
                    draft.validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(
                        onClick = { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(detailsExpanded = !draft.detailsExpanded))) },
                        modifier = Modifier.semantics { testTag = WorkspaceTags.IMPORT_DETAILS }
                    ) { Text(if (draft.detailsExpanded) "Hide details" else "Details") }
                    if (draft.detailsExpanded) {
                        Text("The stable part ID is ${draft.id}; its initial section is ${app.melotrail.arrangement.SectionTypeCatalog.label(draft.sectionType)}. You can change the section with downstream-impact confirmation after import.", style = MaterialTheme.typography.bodySmall)
                        Text("Audio cleanup, Clean MIDI, and Lo-fi MIDI Feel are available from Details only when their workflow stage is current.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (draft.source != null && type != ImportSourceKind.UNSUPPORTED && draft.validationMessage == null) {
                    HorizontalDivider()
                    Text("3. Confirm source rights", fontWeight = FontWeight.SemiBold)
                    Text("This source-rights record is retained with the import. “Not established” keeps local work available but blocks commercial-ready export.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.confirmationMessage?.takeIf { !draft.provenanceConfirmed }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    app.melotrail.commercial.SourceRightsClaim.entries.forEach { claim ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(claim == draft.rightsClaim, { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(rightsClaim = claim))) })
                            Text(when (claim) {
                                app.melotrail.commercial.SourceRightsClaim.OWNED -> "I own this source"
                                app.melotrail.commercial.SourceRightsClaim.COMMERCIAL_PERMISSION -> "I have commercial permission"
                                app.melotrail.commercial.SourceRightsClaim.PUBLIC_DOMAIN -> "I believe it is public domain"
                                app.melotrail.commercial.SourceRightsClaim.NOT_ESTABLISHED -> "I have not established rights"
                            })
                        }
                    }
                }
                if (draft.provenanceConfirmed && draft.validationMessage == null) {
                    HorizontalDivider()
                    Text("4. Next action", fontWeight = FontWeight.SemiBold)
                    draft.confirmationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        if (draft.audio) "Import once to preserve the immutable source and begin the bounded automatic solo-piano route." else "Import once to preserve the immutable source and begin the automatic MIDI preparation route.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            when (currentStep) {
                ImportFlowStep.CONFIRM_PROVENANCE -> Button(
                    onClick = { onIntent(WorkspaceIntent.ConfirmImportProvenance) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.IMPORT_PROVENANCE }
                ) { Text("Confirm source rights") }
                ImportFlowStep.NEXT_ACTION -> Button(
                    onClick = { onIntent(WorkspaceIntent.ImportPart) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.IMPORT_CONFIRM }
                ) { Text("Import melody part") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun ImportStepIndicator(current: ImportFlowStep) = Row(
    Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.IMPORT_STEPS },
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
    ImportFlowStep.entries.forEach { step ->
        val status = when {
            step.number < current.number -> "complete"
            step == current -> "current"
            else -> "upcoming"
        }
        Text(
            "${step.number}. ${step.label}",
            modifier = Modifier.weight(1f).semantics { contentDescription = "Step ${step.number}: ${step.label}, $status" },
            style = MaterialTheme.typography.labelSmall,
            color = if (step == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(bytes: Long?): String = when {
    bytes == null -> "size unavailable"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun EditRoleDialog(draft: WorkspaceDialog.EditRole, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Change ${draft.partId} section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose Intro, Verse, Chorus, Bridge, or Outro. This retains the source and marks dependent structure and arrangement evidence stale for review.")
                OutlinedTextField(draft.role, { onIntent(WorkspaceIntent.UpdateRole(it)) }, label = { Text("Section") })
            }
        },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.SaveRole) }) { Text("Review change") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmSectionChangeDialog(draft: WorkspaceDialog.ConfirmSectionChange, onIntent: (WorkspaceIntent) -> Unit) = AlertDialog(
    onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
    title = { Text("Change section for ${draft.partId}?") },
    text = { Text("This changes the part's section to ${app.melotrail.arrangement.SectionTypeCatalog.label(draft.sectionType)}. The source stays immutable; downstream structure, cohesion, and arrangement evidence may need regeneration.") },
    confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmSectionChange) }) { Text("Change section") } },
    dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Keep current section") } }
)

@Composable
private fun ConfirmPartStructureChangeDialog(draft: WorkspaceDialog.ConfirmPartStructureChange, onIntent: (WorkspaceIntent) -> Unit) = AlertDialog(
    onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
    title = { Text(if (draft.instanceId == null) "Add ${draft.partId} to structure?" else "Remove ${draft.partId} from structure?") },
    text = { Text("The source and melody part are retained. This changes canonical structure, so downstream cohesion, arrangement, and build artifacts may become stale and remain inspectable until regenerated.") },
    confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ConfirmPartStructureChange) }) { Text(if (draft.instanceId == null) "Add to structure" else "Remove occurrence") } },
    dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
)

private fun buildSongPrerequisite(state: WorkspaceUiState): String = when {
    state.project == null -> "Build Song needs an open project."
    state.arrangement == null -> "Build Song needs an approved arrangement."
    state.arrangement.stale -> "Build Song is blocked: regenerate the stale arrangement."
    state.arrangement.approvalRequired -> "Build Song is blocked: approve the Qwen draft."
    state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available != true -> state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.reason ?: "Build Song is checking local readiness."
    else -> "Build Song will generate/reuse MIDI and stems, then mix, repair, master, and write release metadata."
}

private fun arrangementPrerequisite(state: WorkspaceUiState): String = when {
    state.project == null -> "Open a project before generating an arrangement."
    state.structureDraft.isEmpty() -> "Add and save at least one section before generating an arrangement."
    else -> {
        val missing = state.structureDraft.toSet().filter { id ->
            state.project.parts.firstOrNull { it.id == id }?.analysis?.status != app.melotrail.application.PartAnalysisStatus.MIDI
        }
        when {
            missing.isNotEmpty() -> "Analyze every structure part before arranging: ${missing.joinToString(", ")}."
            state.arrangement?.stale == true -> "The arrangement is stale; regenerate it from the current analyses and structure."
            state.arrangement?.approvalRequired == true -> "Review and explicitly approve the Qwen draft before building."
            else -> "Analyses and structure are ready. Generate a deterministic arrangement or a reviewable Qwen draft."
        }
    }
}

private fun canBuild(state: WorkspaceUiState): Boolean = state.project != null && !state.operation.isMutating && state.arrangement?.approved == true && state.arrangement?.approvalRequired == false && state.arrangement?.stale == false && state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available == true

internal fun timelineSectionWeight(durationSeconds: Double?): Float =
    (durationSeconds?.takeIf { it > 0.0 } ?: 1.0).toFloat()

internal fun formatDuration(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)
