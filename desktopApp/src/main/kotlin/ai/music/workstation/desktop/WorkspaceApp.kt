package ai.music.workstation.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object WorkspaceTags {
    const val PROJECT_HEADER = "project-header"
    const val WORKSPACE_NAV = "workspace-nav"
    const val PARTS_PANEL = "parts-panel"
    const val STRUCTURE_PANEL = "structure-panel"
    const val ARRANGEMENT_PANEL = "arrangement-panel"
    const val TIMELINE_PANEL = "timeline-panel"
    const val MIX_PANEL = "mix-panel"
    const val OPERATION_STATUS = "operation-status"
    const val CREATE_PROJECT = "create-project"
    const val OPEN_PROJECT = "open-project"
    const val ADD_MIDI = "add-midi"
    const val ADD_AUDIO = "add-audio"
    const val IMPORT_PROGRESS = "import-progress"
    const val STRUCTURE_CLEAR = "structure-clear"
    const val STRUCTURE_MOVE_LEFT = "structure-move-left-"
    const val STRUCTURE_MOVE_RIGHT = "structure-move-right-"
    const val ARRANGEMENT_GENERATE = "arrangement-generate"
    const val ARRANGEMENT_APPROVE = "arrangement-approve"
    const val ARRANGEMENT_PREVIEW = "arrangement-preview"
    const val ARRANGEMENT_STYLE = "arrangement-style"
    const val BUILD_SONG = "build-song"
    const val MIX_RESET = "mix-reset"
    const val PLAYBACK_DRY = "playback-dry"
    const val PLAYBACK_LOFI = "playback-lofi"
    const val PLAYBACK_MASTER = "playback-master"
    const val PLAYBACK_TOGGLE = "playback-toggle"
    const val PLAYBACK_SEEK = "playback-seek"
    const val PLAYBACK_VOLUME = "playback-volume"
    const val PREVIEW_TRANSPORT = "preview-transport"
    const val PREVIEW_TOGGLE = "preview-toggle"
    const val PREVIEW_STOP = "preview-stop"
    const val PREVIEW_SEEK = "preview-seek"
    const val PREVIEW_RETRY = "preview-retry"
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
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(14.dp)
            .onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                transportShortcutIntent(event, state.playback)?.let(onIntent) != null
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProjectHeader(state, onIntent)
        WorkspaceShell(state, onIntent)
    }
    WorkspaceDialogs(state, onIntent, onExit)
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
        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.PROJECT_HEADER },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("♫", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("AI Music Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Compose · Arrange · Mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WorkspaceNavigation()
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.ShowCreateProject) },
                    enabled = !mutationsDisabled,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.CREATE_PROJECT; contentDescription = "Create a new project" }
                ) { Text("New") }
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.ChooseProject) },
                    enabled = !mutationsDisabled,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.OPEN_PROJECT; contentDescription = "Open an existing project" }
                ) { Text("Open") }
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.ShowSoundLibrarySettings) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.SOUND_LIBRARY_SETTINGS; contentDescription = "Configure local sound library" }
                ) { Text("Library") }
                Button(onClick = { onIntent(WorkspaceIntent.BuildSong) }, enabled = canBuild(state), modifier = Modifier.semantics { testTag = WorkspaceTags.BUILD_SONG; contentDescription = "Build song release artifacts" }) { Text("Build song") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val projectText = state.project?.let { "Project · ${it.name} · ${it.renderFormat?.sampleRate ?: "?"} Hz / ${it.renderFormat?.channels ?: "?"} ch / PCM-24" }
                    ?: "Start workspace · create or open an arranger project"
                Text(projectText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReadinessText(state.runtimeReadiness, onIntent)
                Text(soundLibrarySummary(state.soundLibrary), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(buildSongPrerequisite(state), modifier = Modifier.widthIn(max = 330.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun soundLibrarySummary(library: SoundLibrarySettingsState): String = when {
    library.resolvedRoot != null && library.validationError == null -> "Library: ${library.source ?: "configured"} · ${library.resolvedRoot}"
    else -> "Library unavailable — ${library.validationError ?: "choose a valid folder"}"
}

@Composable
private fun WorkspaceNavigation() {
    Row(
        modifier = Modifier.semantics { testTag = WorkspaceTags.WORKSPACE_NAV; contentDescription = "Workspace sections: Project, Arrange, Mix and Master" },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WorkspaceNavItem("Project", active = true)
        WorkspaceNavItem("Arrange")
        WorkspaceNavItem("Mix & Master")
    }
}

@Composable
private fun WorkspaceNavItem(label: String, active: Boolean = false) {
    Text(
        label,
        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MusicWorkspaceTokens.ElevatedSurface)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun ReadinessText(readiness: RuntimeReadiness?, onIntent: (WorkspaceIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val text = readiness?.let {
            val unavailable = RuntimeDependency.entries.map { dependency -> dependency to it.dependency(dependency) }.firstOrNull { !it.second.available }
            if (unavailable == null) "Local readiness: all dependencies ready."
            else "Local readiness: ${unavailable.first.name.lowercase().replace('_', ' ')} ${unavailable.second.status.name.lowercase()} — ${unavailable.second.detail}"
        } ?: "Checking local dependency readiness…"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onIntent(WorkspaceIntent.RefreshRuntimeReadiness) }) { Text("Refresh") }
        }
    }
}

@Composable
private fun WorkspaceShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            maxWidth >= 1180.dp -> WideWorkspace(state, onIntent)
            maxWidth >= 760.dp -> MediumWorkspace(state, onIntent)
            else -> NarrowWorkspace(state, onIntent)
        }
    }
}

@Composable
private fun WideWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelColumn(Modifier.widthIn(min = 235.dp, max = 300.dp).weight(0.95f), state, onIntent, listOf(Panel.Parts, Panel.Structure))
        PanelColumn(Modifier.weight(1.7f), state, onIntent, listOf(Panel.Arrangement, Panel.Timeline))
        PanelColumn(Modifier.widthIn(min = 255.dp, max = 340.dp).weight(1f), state, onIntent, listOf(Panel.Mix, Panel.Status))
    }
}

@Composable
private fun MediumWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Row(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelColumn(Modifier.widthIn(min = 300.dp, max = 340.dp), state, onIntent, listOf(Panel.Parts, Panel.Structure, Panel.Status))
        PanelColumn(Modifier.widthIn(min = 500.dp, max = 720.dp), state, onIntent, listOf(Panel.Arrangement, Panel.Timeline, Panel.Mix))
    }
}

@Composable
private fun NarrowWorkspace(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    PanelColumn(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), state, onIntent, Panel.entries.toList())
}

private enum class Panel { Parts, Structure, Arrangement, Timeline, Mix, Status }

@Composable
private fun PanelColumn(modifier: Modifier, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, panels: List<Panel>) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        panels.forEach { panel ->
            when (panel) {
                Panel.Parts -> PartsPanel(state, onIntent)
                Panel.Structure -> StructurePanel(state, onIntent)
                Panel.Arrangement -> ArrangementPanel(state, onIntent)
                Panel.Timeline -> TimelinePanel(state, onIntent)
                Panel.Mix -> MixPanel(state, onIntent)
                else -> PlaceholderPanel(panel, state, onIntent)
            }
        }
    }
}

@Composable
private fun PartsPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Parts", WorkspaceTags.PARTS_PANEL) {
    val disabled = state.project == null || state.operation.isMutating
    if (state.project?.parts.isNullOrEmpty()) {
        Text("Import MIDI or solo-piano WAV/MP3 to prepare the first part.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        state.project!!.parts.forEach { part ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${part.id} · ${part.sourceName} (${part.sourceType.name.lowercase()})", fontWeight = FontWeight.Medium)
                val role = part.role.ifBlank { "not set" }
                Text("Role: $role · ${state.partPreparationLabel(part.id)}", style = MaterialTheme.typography.bodySmall)
                val analysis = part.analysis
                Text(
                    "Bars: ${analysis?.bars ?: "—"} · Key: ${analysis?.key ?: "—"} · Duration: ${analysis?.durationSeconds?.let(::formatDuration) ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val previewCapability = if (part.sourceType == ai.music.workstation.application.PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
                val previewReadiness = state.runtimeReadiness?.capability(previewCapability)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.ShowRoleEditor(part.id)) }, enabled = !disabled) { Text("Edit role") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.PreviewPart(part.id)) }, enabled = !disabled && previewReadiness?.available == true) { Text("Preview") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.AnalyzePart(part.id)) }, enabled = !disabled) {
                        Text(if (analysis == null) "Analyze" else "Analyze again")
                    }
                }
                if (!disabled && previewReadiness?.available != true) {
                    Text(
                        "Preview unavailable — ${previewReadiness?.reason ?: "Checking local preview requirements."}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onIntent(WorkspaceIntent.ShowImportPart(audio = false)) }, enabled = !disabled,
            modifier = Modifier.weight(1f).semantics { testTag = WorkspaceTags.ADD_MIDI }
        ) { Text("Add MIDI") }
        OutlinedButton(
            onClick = { onIntent(WorkspaceIntent.ShowImportPart(audio = true)) }, enabled = !disabled && state.runtimeReadiness?.capability(RuntimeCapability.AUDIO_IMPORT)?.available == true,
            modifier = Modifier.weight(1f).semantics { testTag = WorkspaceTags.ADD_AUDIO }
        ) { Text("Add audio") }
    }
    state.runtimeReadiness?.capability(RuntimeCapability.AUDIO_IMPORT)?.reason?.let { Text("Audio import unavailable — $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun PreviewTransport(preview: PreviewUiState, volume: Double, onIntent: (WorkspaceIntent) -> Unit) = Column(
    modifier = Modifier.semantics { testTag = WorkspaceTags.PREVIEW_TRANSPORT },
    verticalArrangement = Arrangement.spacedBy(6.dp)
) {
    Text("Selected preview", style = MaterialTheme.typography.labelLarge)
    val partLabel = preview.source?.partId?.let { "Part $it" } ?: "No part selected"
    val hasArtifact = preview.source?.artifact != null
    val canPause = preview.phase == PreviewPhase.PLAYING
    val canResume = preview.phase == PreviewPhase.PAUSED
    val canStop = preview.phase in setOf(PreviewPhase.PREPARING, PreviewPhase.READY, PreviewPhase.STARTING, PreviewPhase.PLAYING, PreviewPhase.PAUSED)
    val canSeek = hasArtifact && preview.durationSeconds > 0.0 && preview.phase in setOf(PreviewPhase.READY, PreviewPhase.PLAYING, PreviewPhase.PAUSED, PreviewPhase.STOPPED)

    Text(partLabel, fontWeight = FontWeight.Medium)
    Text(previewStatusLabel(preview.phase), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (preview.phase == PreviewPhase.FAILED) {
        Text(preview.reason ?: "Preview failed. Retry after resolving the prerequisite.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.RetryPreview) }, enabled = preview.source != null, modifier = Modifier.semantics { testTag = WorkspaceTags.PREVIEW_RETRY; contentDescription = "Retry selected preview" }) { Text("Retry preview") }
    } else if (preview.phase == PreviewPhase.STOPPED && preview.source != null && !hasArtifact) {
        Text("Select Preview on a part to prepare its monitor artifact.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { onIntent(if (canPause) WorkspaceIntent.PausePreview else WorkspaceIntent.ResumePreview) },
            enabled = canPause || canResume,
            modifier = Modifier.semantics { testTag = WorkspaceTags.PREVIEW_TOGGLE; contentDescription = if (canPause) "Pause selected preview" else "Resume selected preview" }
        ) { Text(if (canPause) "Pause" else "Resume") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.StopPreview) }, enabled = canStop, modifier = Modifier.semantics { testTag = WorkspaceTags.PREVIEW_STOP; contentDescription = "Stop selected preview" }) { Text("Stop") }
        Text("${formatDuration(preview.elapsedSeconds)} / ${formatDuration(preview.durationSeconds)}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall)
    }
    Slider(
        value = preview.elapsedSeconds.toFloat(), onValueChange = { onIntent(WorkspaceIntent.SeekPreview(it.toDouble())) },
        valueRange = 0f..preview.durationSeconds.coerceAtLeast(0.01).toFloat(), enabled = canSeek,
        modifier = Modifier.semantics { testTag = WorkspaceTags.PREVIEW_SEEK; contentDescription = "Seek selected preview" }
    )
    Text("Output volume ${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
    Slider(
        value = volume.toFloat(), onValueChange = { onIntent(WorkspaceIntent.SetPlaybackVolume(it.toDouble())) }, enabled = hasArtifact,
        modifier = Modifier.semantics { contentDescription = "Selected preview output volume" }
    )
}

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

@Composable
private fun StructurePanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Song structure", WorkspaceTags.STRUCTURE_PANEL) {
    val disabled = state.project == null || state.operation.isMutating
    if (state.structureDraft.isEmpty()) {
        Text("Add parts in the intended order. Empty structure cannot be arranged or built.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("SECTION FLOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.structureDraft.forEachIndexed { index, partId ->
                val occurrence = state.structureDraft.take(index + 1).count { it == partId }
                Text(
                    "$partId$occurrence",
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(if (index % 2 == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MusicWorkspaceTokens.ElevatedSurface)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    color = if (index % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        state.structureDraft.forEachIndexed { index, partId ->
            StructureRow(index, partId, state, !disabled, onIntent)
        }
    }
    if (!state.project?.parts.isNullOrEmpty()) {
        Text("Add section", style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.project!!.parts.forEach { part -> TextButton(onClick = { onIntent(WorkspaceIntent.AddStructurePart(part.id)) }, enabled = !disabled) { Text(part.id) } }
        }
    }
    OutlinedButton(
        onClick = { onIntent(WorkspaceIntent.ClearStructure) }, enabled = !disabled && state.structureDraft.isNotEmpty(),
        modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_CLEAR }
    ) { Text("Clear") }
    if (state.downstreamArtifactsStale) {
        Text("Structure changed: existing plans, generated MIDI, stems, mixes, and releases are stale; nothing was deleted.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StructureRow(index: Int, partId: String, state: WorkspaceUiState, enabled: Boolean, onIntent: (WorkspaceIntent) -> Unit) {
    var dragY by remember(index, partId) { mutableFloatStateOf(0f) }
    var rowHeight by remember(index, partId) { mutableFloatStateOf(56f) }
    val occurrence = state.structureDraft.take(index + 1).count { it == partId }
    val seconds = state.project?.structure?.getOrNull(index)?.durationSeconds
    val duration = seconds?.let(::formatDuration) ?: "—"
    val maximumSeconds = state.project?.structure?.mapNotNull { it.durationSeconds }?.maxOrNull()
    Column(
        modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeight = it.height.toFloat() }
            .offset { IntOffset(0, dragY.roundToInt()) }
            .pointerInput(index, state.structureDraft) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = {
                        val delta = (dragY / rowHeight).roundToInt()
                        onIntent(WorkspaceIntent.MoveStructurePart(index, (index + delta).coerceIn(state.structureDraft.indices)))
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                    onDrag = { change, amount -> change.consume(); dragY += amount.y }
                )
            }.padding(vertical = 3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${partId}${occurrence} · $duration", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index - 1)) }, enabled = enabled && index > 0,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_LEFT + index; contentDescription = "Move $partId$occurrence earlier" }) { Text("←") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index + 1)) }, enabled = enabled && index < state.structureDraft.lastIndex,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_RIGHT + index; contentDescription = "Move $partId$occurrence later" }) { Text("→") }
            TextButton(onClick = { onIntent(WorkspaceIntent.DuplicateStructurePart(index)) }, enabled = enabled) { Text("Duplicate") }
            TextButton(onClick = { onIntent(WorkspaceIntent.RemoveStructurePart(index)) }, enabled = enabled) { Text("Remove") }
        }
        if (seconds != null && maximumSeconds != null && maximumSeconds > 0.0) {
            LinearProgressIndicator(progress = { (seconds / maximumSeconds).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ArrangementPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("AI arrangement", WorkspaceTags.ARRANGEMENT_PANEL) {
    val project = state.project
    val draft = state.arrangementDraft
    val disabled = project == null || state.operation.isMutating
    Text("Generate a reviewed whole-song plan and bounded detailed arrangement. Qwen drafts always need explicit approval.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlannerButton("Deterministic", draft.planner.name == "DETERMINISTIC", !disabled) { onIntent(WorkspaceIntent.UpdateArrangementPlanner(ai.music.workstation.application.ArrangementPlannerKind.DETERMINISTIC)) }
        PlannerButton("Qwen", draft.planner.name == "QWEN", !disabled) { onIntent(WorkspaceIntent.UpdateArrangementPlanner(ai.music.workstation.application.ArrangementPlannerKind.QWEN)) }
    }
    OutlinedTextField(
        value = draft.style,
        onValueChange = { onIntent(WorkspaceIntent.UpdateArrangementStyle(it)) },
        enabled = !disabled,
        label = { Text("Style (optional, 160 characters max)") },
        supportingText = { Text("${draft.style.length}/160") },
        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.ARRANGEMENT_STYLE }
    )
    Text("Instruments", style = MaterialTheme.typography.labelLarge)
    listOf("piano", "bass", "drums", "pad", "strings").forEach { instrument ->
        val piano = instrument == "piano"
        Row(modifier = Modifier.semantics { contentDescription = "$instrument instrument ${if (piano) "required" else "optional"}" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(
                checked = instrument in draft.instruments,
                onCheckedChange = { onIntent(WorkspaceIntent.ToggleArrangementInstrument(instrument)) },
                enabled = !disabled && !piano
            )
            Text(instrument.replaceFirstChar(Char::uppercase), modifier = Modifier.padding(top = 12.dp), color = instrumentLaneColors[instrument] ?: MaterialTheme.colorScheme.onSurface)
            if (piano) Text("Source · required", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Button(onClick = { onIntent(WorkspaceIntent.GenerateArrangement) }, enabled = !disabled, modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.ARRANGEMENT_GENERATE }) { Text("Generate arrangement") }
    ArrangementReview(state, onIntent)
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
private fun TimelinePanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Song timeline", WorkspaceTags.TIMELINE_PANEL) {
    val arrangement = state.arrangement
    when {
        arrangement == null -> Text("Generate an arrangement to view validated song-plan sections and instrument lanes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        arrangement.stale -> Text("Timeline is unavailable because the arrangement artifact is stale. Regenerate it from the current project.", color = MaterialTheme.colorScheme.error)
        arrangement.sections.isEmpty() -> Text("No validated arrangement sections are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Text(if (arrangement.approvalRequired) "AI song plan · validated draft" else "AI song plan · approved", style = MaterialTheme.typography.labelLarge)
            arrangement.sections.forEach { section ->
                SongPlanRow(section, section.index == state.selectedArrangementSection) { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Text("Instrument timeline", style = MaterialTheme.typography.labelLarge)
            TimelineLanes(arrangement, state.selectedArrangementSection, onIntent)
            state.selectedArrangementSection?.let { index -> arrangement.sections.find { it.index == index } }?.let { section ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                Text("${section.instanceId} details · ${section.purpose} · ${(section.energy * 100).toInt()}% energy", fontWeight = FontWeight.Medium)
                section.instruments.forEach { instrument ->
                    Text("${instrument.name.replaceFirstChar(Char::uppercase)} · ${instrument.mode}${instrument.role?.let { " · $it" }.orEmpty()}${instrument.density?.let { " · density %.2f".format(it) }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Transition out: ${section.transition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SongPlanRow(section: ai.music.workstation.application.ArrangementSectionSnapshot, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(7.dp)
            .semantics { contentDescription = "${section.instanceId}, ${section.purpose}, ${(section.energy * 100).toInt()} percent energy, ${section.instruments.joinToString { it.name }}, transition ${section.transition}" },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(section.instanceId, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(section.purpose, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("${(section.energy * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        Text(section.instruments.joinToString(" + ") { it.name.replaceFirstChar(Char::uppercase) }, modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
        Text(section.transition, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimelineLanes(arrangement: ai.music.workstation.application.ArrangementSnapshot, selectedIndex: Int?, onIntent: (WorkspaceIntent) -> Unit) {
    listOf("piano", "bass", "drums", "pad", "strings").forEach { instrument ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(instrument.replaceFirstChar(Char::uppercase), modifier = Modifier.padding(top = 7.dp).widthIn(min = 52.dp), color = instrumentLaneColors.getValue(instrument), style = MaterialTheme.typography.labelMedium)
            arrangement.sections.forEach { section ->
                val active = section.instruments.any { it.name == instrument }
                val weight = timelineSectionWeight(section.durationSeconds)
                val color = instrumentLaneColors.getValue(instrument)
                Text(
                    text = if (active) section.instanceId else "",
                    modifier = Modifier.weight(weight).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(if (active) color.copy(alpha = if (section.index == selectedIndex) 0.72f else 0.42f) else MaterialTheme.colorScheme.surface)
                        .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }.padding(vertical = 6.dp, horizontal = 3.dp)
                        .semantics { contentDescription = "$instrument lane, ${section.instanceId}, ${if (active) "active" else "inactive"}, duration ${section.durationSeconds ?: 0.0} seconds" },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MixPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Mix & transport", WorkspaceTags.MIX_PANEL) {
    val mix = state.mix
    val disabled = state.project == null || state.operation.isMutating
    if (mix == null || mix.availableStems.isEmpty()) {
        Text("Render or build the approved arrangement to create compatible stems and a dry mix.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        mix.availableStems.forEach { instrument ->
            val setting = mix.settings.tracks[instrument] ?: ai.music.workstation.application.LogicalMixSetting()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row { Text(instrument.replaceFirstChar(Char::uppercase), modifier = Modifier.weight(1f), color = instrumentLaneColors[instrument] ?: MaterialTheme.colorScheme.onSurface); Text("%.1f dB".format(setting.gainDb)) }
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
        Checkbox(state.buildOptions.loFi, { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFi = it))) }, enabled = !disabled); Text("LoFi", modifier = Modifier.padding(top = 12.dp))
        Checkbox(state.buildOptions.mp3, { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(mp3 = it))) }, enabled = !disabled); Text("MP3", modifier = Modifier.padding(top = 12.dp))
    }
    PlaybackControls(state, onIntent)
}

@Composable
private fun PlaybackControls(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val root = state.project?.root
    val source = state.playback.source
    fun enabled(value: PlaybackSource) = root != null && when (value) {
        PlaybackSource.DRY -> state.project.readiness.dryMixAvailable && !state.downstreamArtifactsStale
        PlaybackSource.LOFI -> state.project.readiness.loFiMixAvailable && !state.downstreamArtifactsStale
        PlaybackSource.MASTER -> state.project.readiness.masterAvailable && !state.downstreamArtifactsStale
    }
    Text("Audition validated artifacts", style = MaterialTheme.typography.labelLarge)
    Text("Keyboard: Ctrl/Cmd+Space play or pause; Ctrl/Cmd+Left/Right seek 5 seconds; Ctrl/Cmd+K stop.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.DRY)) }, enabled = enabled(PlaybackSource.DRY), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_DRY }) { Text("Dry") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.LOFI)) }, enabled = enabled(PlaybackSource.LOFI), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_LOFI }) { Text("LoFi") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.MASTER)) }, enabled = enabled(PlaybackSource.MASTER), modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_MASTER }) { Text("Master") }
    }
    val selectedEnabled = enabled(source)
    if (!selectedEnabled) Text("${source.name.lowercase().replaceFirstChar(Char::uppercase)} is unavailable or stale. Build Song creates current audition artifacts.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { onIntent(WorkspaceIntent.PlayPause) }, enabled = selectedEnabled, modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_TOGGLE; contentDescription = if (state.playback.state == ai.music.workstation.audio.PlaybackState.PLAYING) "Pause selected audio artifact" else "Play selected audio artifact" }) { Text(if (state.playback.state == ai.music.workstation.audio.PlaybackState.PLAYING) "Pause" else "Play") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = state.playback.state != ai.music.workstation.audio.PlaybackState.STOPPED) { Text("Stop") }
        Text("${formatDuration(state.playback.positionSeconds)} / ${formatDuration(state.playback.durationSeconds)}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall)
    }
    Slider(value = state.playback.positionSeconds.toFloat(), onValueChange = { onIntent(WorkspaceIntent.SeekPlayback(it.toDouble())) }, valueRange = 0f..state.playback.durationSeconds.coerceAtLeast(0.01).toFloat(), enabled = selectedEnabled && state.playback.durationSeconds > 0.0, modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_SEEK; contentDescription = "Seek selected audio artifact" })
    Text("Output volume ${(state.playback.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
    Slider(value = state.playback.volume.toFloat(), onValueChange = { onIntent(WorkspaceIntent.SetPlaybackVolume(it.toDouble())) }, enabled = selectedEnabled, modifier = Modifier.semantics { testTag = WorkspaceTags.PLAYBACK_VOLUME; contentDescription = "Selected audio artifact output volume" })
}

@Composable
private fun PlaceholderPanel(panel: Panel, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val (title, detail, tag) = when (panel) {
        Panel.Status -> Triple("Operation status", statusText(state.operation), WorkspaceTags.OPERATION_STATUS)
        else -> error("Functional panels are handled separately")
    }
    WorkspaceCard(title, tag) {
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (panel == Panel.Status) {
            PreviewTransport(state.preview, state.playback.volume, onIntent)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            state.runtimeReadiness?.let { readiness ->
                Text("Local readiness", style = MaterialTheme.typography.labelLarge)
                RuntimeDependency.entries.forEach { dependency ->
                    val item = readiness.dependency(dependency)
                    Text(
                        "${dependency.name.lowercase().replace('_', ' ')}: ${item.status.name.lowercase()}${if (item.available) "" else " — ${item.detail}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val progress = (state.operation as? WorkspaceOperation.ImportingPart)?.progress
            ?: (state.operation as? WorkspaceOperation.AnalyzingPart)?.progress
            ?: (state.operation as? WorkspaceOperation.GeneratingArrangement)?.progress
            ?: (state.operation as? WorkspaceOperation.ApplyingMix)?.progress
            ?: (state.operation as? WorkspaceOperation.BuildingSong)?.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.stageIndex.toFloat() / progress.stageCount },
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.IMPORT_PROGRESS }
            )
            Text("${progress.stageIndex}/${progress.stageCount} · ${progress.message}", style = MaterialTheme.typography.bodySmall)
            progress.artifact?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
        if (state.retry != null) OutlinedButton(onClick = { onIntent(WorkspaceIntent.Retry) }) { Text("Retry") }
        if (state.operation is WorkspaceOperation.BuildingSong) OutlinedButton(onClick = { onIntent(WorkspaceIntent.CancelOperation) }) { Text("Cancel at boundary") }
    }
}

@Composable
private fun WorkspaceCard(title: String, tag: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            content()
        }
    }
}

private fun statusText(operation: WorkspaceOperation): String = when (operation) {
    WorkspaceOperation.Idle -> "Ready. Create or open a project to begin."
    is WorkspaceOperation.OpeningProject -> "Opening ${operation.root.fileName}…"
    is WorkspaceOperation.CreatingProject -> "Creating ${operation.root.fileName}…"
    is WorkspaceOperation.ImportingPart -> "Preparing ${operation.id}…"
    is WorkspaceOperation.AnalyzingPart -> "Analyzing ${operation.id}…"
    is WorkspaceOperation.UpdatingPartRole -> "Saving ${operation.id} role…"
    WorkspaceOperation.SavingStructure -> "Saving song structure…"
    is WorkspaceOperation.GeneratingArrangement -> "Generating reviewed song plan and detailed arrangement…"
    is WorkspaceOperation.ApplyingMix -> "Applying persisted mix settings to existing stems…"
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
        is WorkspaceDialog.ConfirmDiscardDraft -> ConfirmDiscardDraftDialog(dialog, onIntent)
        WorkspaceDialog.ConfirmClose -> ConfirmCloseDialog(onIntent, onExit)
        WorkspaceDialog.SoundLibrarySettings -> SoundLibrarySettingsDialog(state.soundLibrary, onIntent)
        null -> Unit
    }
}

@Composable
private fun SoundLibrarySettingsDialog(settings: SoundLibrarySettingsState, onIntent: (WorkspaceIntent) -> Unit) {
    val selectionDisabled = settings.selectionDisabledReason != null
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Local sound library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(settings.resolvedRoot?.toString() ?: "No validated sound-library folder is available.")
                Text("Discovery source: ${settings.source ?: "none"}", style = MaterialTheme.typography.bodySmall)
                settings.validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                settings.selectionDisabledReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Choose the folder containing instruments.json and LICENSES.json. If starter samples are missing, copy the approved 25 WAV files into the existing sounds subfolders; see sounds/README.md. No files are copied or changed here.", style = MaterialTheme.typography.bodySmall)
                if (settings.restartRequired) Text("Restart the desktop app to apply this validated library to renderer services.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !selectionDisabled,
                    onClick = { onIntent(WorkspaceIntent.ClearSoundLibraryRoot) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.SOUND_LIBRARY_CLEAR }
                ) { Text("Clear") }
                Button(
                    enabled = !selectionDisabled,
                    onClick = { onIntent(WorkspaceIntent.ChooseSoundLibraryRoot) },
                    modifier = Modifier.semantics { testTag = WorkspaceTags.SOUND_LIBRARY_CHOOSE }
                ) { Text("Choose folder") }
            }
        },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.RefreshSoundLibrary) }) { Text("Refresh") } }
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
        title = { Text("Close Personal AI Music Arranger?") },
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
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text(if (draft.audio) "Add audio part" else "Add MIDI part") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.audio) Text("Audio import supports solo piano WAV/MP3. It requires the local transcription worker, then deterministic MIDI cleanup before the part is registered.")
                else Text("The original MIDI is preserved under source/ and a cleaned MIDI artifact is prepared before registration.")
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.ChooseImportSource) }) { Text(draft.source?.fileName?.toString() ?: "Choose source") }
                OutlinedTextField(draft.id, { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(id = it))) }, label = { Text("Part ID (stable after import)") })
                OutlinedTextField(draft.role, { onIntent(WorkspaceIntent.UpdateImportPart(draft.copy(role = it))) }, label = { Text("Role") })
            }
        },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.ImportPart) }) { Text("Prepare part") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun EditRoleDialog(draft: WorkspaceDialog.EditRole, onIntent: (WorkspaceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(WorkspaceIntent.DismissDialog) },
        title = { Text("Edit ${draft.partId} role") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Part IDs are stable in this MVP. Rename and removal are intentionally deferred.")
                OutlinedTextField(draft.role, { onIntent(WorkspaceIntent.UpdateRole(it)) }, label = { Text("Role") })
            }
        },
        confirmButton = { Button(onClick = { onIntent(WorkspaceIntent.SaveRole) }) { Text("Save role") } },
        dismissButton = { TextButton(onClick = { onIntent(WorkspaceIntent.DismissDialog) }) { Text("Cancel") } }
    )
}

@Composable
private fun buildSongPrerequisite(state: WorkspaceUiState): String = when {
    state.project == null -> "Build Song needs an open project."
    state.arrangement == null -> "Build Song needs an approved arrangement."
    state.arrangement.stale -> "Build Song is blocked: regenerate the stale arrangement."
    state.arrangement.approvalRequired -> "Build Song is blocked: approve the Qwen draft."
    state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available != true -> state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.reason ?: "Build Song is checking local readiness."
    else -> "Build Song will generate/reuse MIDI and stems, then mix, repair, master, and write release metadata."
}

private fun canBuild(state: WorkspaceUiState): Boolean = state.project != null && !state.operation.isMutating && state.arrangement?.approved == true && state.arrangement?.approvalRequired == false && state.arrangement?.stale == false && state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available == true

internal fun timelineSectionWeight(durationSeconds: Double?): Float =
    (durationSeconds?.takeIf { it > 0.0 } ?: 1.0).toFloat()

private fun formatDuration(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)
