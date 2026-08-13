package ai.music.workstation.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object WorkspaceTags {
    const val PROJECT_HEADER = "project-header"
    const val PARTS_PANEL = "parts-panel"
    const val STRUCTURE_PANEL = "structure-panel"
    const val ARRANGEMENT_PANEL = "arrangement-panel"
    const val TIMELINE_PANEL = "timeline-panel"
    const val MIX_PANEL = "mix-panel"
    const val OPERATION_STATUS = "operation-status"
}

@Composable
fun WorkspaceApp(viewModel: WorkspaceViewModel) {
    val state by viewModel.state.collectAsState()
    WorkspaceScreen(state, viewModel::accept)
}

@Composable
fun WorkspaceScreen(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProjectHeader(state, onIntent)
        WorkspaceShell(state)
    }
}

@Composable
private fun ProjectHeader(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
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
                val projectText = state.project?.let { "Project · ${it.name}" } ?: "Start workspace · choose an existing arranger project"
                Text(projectText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.ChooseProject) }) { Text("Open project") }
            Button(onClick = {}, enabled = false) { Text("Build song") }
        }
    }
}

@Composable
private fun WorkspaceShell(state: WorkspaceUiState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            maxWidth >= 1180.dp -> WideWorkspace(state)
            maxWidth >= 760.dp -> MediumWorkspace(state)
            else -> NarrowWorkspace(state)
        }
    }
}

@Composable
private fun WideWorkspace(state: WorkspaceUiState) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelColumn(Modifier.widthIn(min = 235.dp, max = 280.dp).weight(0.9f), state, listOf(Panel.Parts, Panel.Structure))
        PanelColumn(Modifier.weight(1.7f), state, listOf(Panel.Arrangement, Panel.Timeline))
        PanelColumn(Modifier.widthIn(min = 255.dp, max = 330.dp).weight(1f), state, listOf(Panel.Mix, Panel.Status))
    }
}

@Composable
private fun MediumWorkspace(state: WorkspaceUiState) {
    Row(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PanelColumn(Modifier.widthIn(min = 280.dp, max = 320.dp), state, listOf(Panel.Parts, Panel.Structure, Panel.Status))
        PanelColumn(Modifier.widthIn(min = 500.dp, max = 720.dp), state, listOf(Panel.Arrangement, Panel.Timeline, Panel.Mix))
    }
}

@Composable
private fun NarrowWorkspace(state: WorkspaceUiState) {
    PanelColumn(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        state,
        listOf(Panel.Parts, Panel.Structure, Panel.Arrangement, Panel.Timeline, Panel.Mix, Panel.Status)
    )
}

private enum class Panel { Parts, Structure, Arrangement, Timeline, Mix, Status }

@Composable
private fun PanelColumn(modifier: Modifier, state: WorkspaceUiState, panels: List<Panel>) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        panels.forEach { panel -> PlaceholderPanel(panel, state) }
    }
}

@Composable
private fun PlaceholderPanel(panel: Panel, state: WorkspaceUiState) {
    val (title, detail, tag) = when (panel) {
        Panel.Parts -> Triple("Parts", "MIDI and audio parts will appear here after import.", WorkspaceTags.PARTS_PANEL)
        Panel.Structure -> Triple("Song structure", "Arrange section instances after parts are ready.", WorkspaceTags.STRUCTURE_PANEL)
        Panel.Arrangement -> Triple("AI arrangement", "Planner controls and approval remain explicit in the next workflow.", WorkspaceTags.ARRANGEMENT_PANEL)
        Panel.Timeline -> Triple("Song timeline", "Validated arrangement sections and instrument lanes appear here.", WorkspaceTags.TIMELINE_PANEL)
        Panel.Mix -> Triple("Mix & transport", "Lossless mix controls and playback are available after rendering.", WorkspaceTags.MIX_PANEL)
        Panel.Status -> Triple("Operation status", statusText(state.operation), WorkspaceTags.OPERATION_STATUS)
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (panel == Panel.Timeline) InstrumentLaneLegend()
        }
    }
}

private fun statusText(operation: WorkspaceOperation): String = when (operation) {
    WorkspaceOperation.Idle -> "Ready. Open a project to begin."
    is WorkspaceOperation.OpeningProject -> "Opening ${operation.root.fileName}…"
    is WorkspaceOperation.OpenFailed -> operation.message
}

@Composable
private fun InstrumentLaneLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        instrumentLaneColors.forEach { (name, color) ->
            Text(name.replaceFirstChar(Char::uppercase), color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}
