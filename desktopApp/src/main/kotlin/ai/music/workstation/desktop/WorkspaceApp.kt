package ai.music.workstation.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object WorkspaceTags {
    const val PROJECT_HEADER = "project-header"
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
}

@Composable
fun WorkspaceApp(viewModel: WorkspaceViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.accept(WorkspaceIntent.RefreshRuntimeReadiness) }
    WorkspaceScreen(state, viewModel::accept)
}

@Composable
fun WorkspaceScreen(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProjectHeader(state, onIntent)
        WorkspaceShell(state, onIntent)
    }
    WorkspaceDialogs(state, onIntent)
}

@Composable
private fun ProjectHeader(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val mutationsDisabled = state.operation.isMutating
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.PROJECT_HEADER },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Personal AI Music Arranger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                val projectText = state.project?.let { "Project · ${it.name} · ${it.renderFormat?.sampleRate ?: "?"} Hz / ${it.renderFormat?.channels ?: "?"} ch / PCM-24" }
                    ?: "Start workspace · create or open an arranger project"
                Text(projectText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReadinessText(state.runtimeReadiness, onIntent)
            }
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.ShowCreateProject) },
                enabled = !mutationsDisabled,
                modifier = Modifier.semantics { testTag = WorkspaceTags.CREATE_PROJECT }
            ) { Text("Create") }
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.ChooseProject) },
                enabled = !mutationsDisabled,
                modifier = Modifier.semantics { testTag = WorkspaceTags.OPEN_PROJECT }
            ) { Text("Open project") }
            Button(onClick = {}, enabled = false) { Text("Build song") }
        }
    }
}

@Composable
private fun ReadinessText(readiness: RuntimeReadiness?, onIntent: (WorkspaceIntent) -> Unit) {
    val text = readiness?.let {
        buildString {
            append("Worker: ").append(if (it.worker.available) "ready" else "unavailable")
            if (!it.worker.available) append(" — ").append(it.worker.detail)
            append(" · Renderer: ").append(if (it.renderer.available) "ready" else "unavailable")
            if (!it.renderer.available) append(" — ").append(it.renderer.detail)
        }
    } ?: "Checking local worker and renderer readiness…"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onIntent(WorkspaceIntent.RefreshRuntimeReadiness) }) { Text("Refresh") }
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        panels.forEach { panel ->
            when (panel) {
                Panel.Parts -> PartsPanel(state, onIntent)
                Panel.Structure -> StructurePanel(state, onIntent)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onIntent(WorkspaceIntent.ShowRoleEditor(part.id)) }, enabled = !disabled) { Text("Edit role") }
                    TextButton(onClick = { onIntent(WorkspaceIntent.AnalyzePart(part.id)) }, enabled = !disabled) {
                        Text(if (analysis == null) "Analyze" else "Analyze again")
                    }
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
            onClick = { onIntent(WorkspaceIntent.ShowImportPart(audio = true)) }, enabled = !disabled,
            modifier = Modifier.weight(1f).semantics { testTag = WorkspaceTags.ADD_AUDIO }
        ) { Text("Add audio") }
    }
}

@Composable
private fun StructurePanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("Song structure", WorkspaceTags.STRUCTURE_PANEL) {
    val disabled = state.project == null || state.operation.isMutating
    if (state.structureDraft.isEmpty()) {
        Text("Add parts in the intended order. Empty structure cannot be arranged or built.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
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
                modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_LEFT + index }) { Text("←") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index + 1)) }, enabled = enabled && index < state.structureDraft.lastIndex,
                modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_RIGHT + index }) { Text("→") }
            TextButton(onClick = { onIntent(WorkspaceIntent.DuplicateStructurePart(index)) }, enabled = enabled) { Text("Duplicate") }
            TextButton(onClick = { onIntent(WorkspaceIntent.RemoveStructurePart(index)) }, enabled = enabled) { Text("Remove") }
        }
        if (seconds != null && maximumSeconds != null && maximumSeconds > 0.0) {
            LinearProgressIndicator(progress = { (seconds / maximumSeconds).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlaceholderPanel(panel: Panel, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val (title, detail, tag) = when (panel) {
        Panel.Arrangement -> Triple("AI arrangement", "Planner controls and approval remain explicit in the next workflow.", WorkspaceTags.ARRANGEMENT_PANEL)
        Panel.Timeline -> Triple("Song timeline", "Validated arrangement sections and instrument lanes appear here.", WorkspaceTags.TIMELINE_PANEL)
        Panel.Mix -> Triple("Mix & transport", "Lossless mix controls and playback are available after rendering.", WorkspaceTags.MIX_PANEL)
        Panel.Status -> Triple("Operation status", statusText(state.operation), WorkspaceTags.OPERATION_STATUS)
        else -> error("Functional panels are handled separately")
    }
    WorkspaceCard(title, tag) {
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val progress = (state.operation as? WorkspaceOperation.ImportingPart)?.progress ?: (state.operation as? WorkspaceOperation.AnalyzingPart)?.progress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.stageIndex.toFloat() / progress.stageCount },
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.IMPORT_PROGRESS }
            )
            Text("${progress.stageIndex}/${progress.stageCount} · ${progress.message}", style = MaterialTheme.typography.bodySmall)
            progress.artifact?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
        }
        if (state.retry != null) OutlinedButton(onClick = { onIntent(WorkspaceIntent.Retry) }) { Text("Retry") }
        if (panel == Panel.Timeline) InstrumentLaneLegend()
    }
}

@Composable
private fun WorkspaceCard(title: String, tag: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
    is WorkspaceOperation.OpenFailed -> operation.message
    is WorkspaceOperation.Failed -> operation.message
}

@Composable
private fun WorkspaceDialogs(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    when (val dialog = state.dialog) {
        is WorkspaceDialog.CreateProject -> CreateProjectDialog(dialog, onIntent)
        is WorkspaceDialog.ImportPart -> ImportPartDialog(dialog, onIntent)
        is WorkspaceDialog.EditRole -> EditRoleDialog(dialog, onIntent)
        null -> Unit
    }
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
private fun InstrumentLaneLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        instrumentLaneColors.forEach { (name, color) -> Text(name.replaceFirstChar(Char::uppercase), color = color, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun formatDuration(seconds: Double): String = "%d:%02d".format(seconds.toInt() / 60, seconds.toInt() % 60)
