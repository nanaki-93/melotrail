package app.melotrail.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Stable workstation composition. Navigation only selects the editing focus;
 * it never substitutes the wide workspace columns.
 */
@Composable
internal fun StableWorkspaceShell(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        when (workspaceLayoutForWidth(maxWidth)) {
            WorkspaceLayout.WIDE -> WideWorkstation(state, onIntent)
            WorkspaceLayout.MEDIUM -> MediumWorkstation(state, onIntent)
            WorkspaceLayout.NARROW -> NarrowWorkstation(state, onIntent)
        }
    }
}

@Composable
private fun WideWorkstation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
        WorkstationColumn(Modifier.width(MusicWorkspaceTokens.Reference.LeftRailWidth), state, onIntent) {
            PartsPanel(state, onIntent)
            VideoConceptPanel()
            CurrentLocationPanel()
            NextDestinationPanel()
            if (state.selectedPartId != null) {
                MidiQualityReviewPanel(state, onIntent)
                AudioPreparationPanel(state, onIntent)
            }
        }
        WorkstationColumn(Modifier.width(MusicWorkspaceTokens.Reference.CenterWidth), state, onIntent) {
            StructurePanel(state, onIntent)
            ArrangementPanel(state, onIntent)
            TimelinePanel(state, onIntent)
        }
        WorkstationColumn(Modifier.width(MusicWorkspaceTokens.Reference.RightRailWidth), state, onIntent) {
            ScenePresentationPanel()
            AiSongPlanPanel(state, onIntent)
            if (state.workspaceSection == WorkspaceSection.MIX_MASTER) MixPanel(state, onIntent)
            if (state.workspaceSection == WorkspaceSection.LIBRARY) LibraryPanel(state, onIntent)
        }
    }
}

@Composable
private fun MediumWorkstation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    // No horizontal page scroll: center editing remains visible; the selected
    // navigation destination determines which side pane is currently shown.
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
        WorkstationColumn(Modifier.widthIn(min = 240.dp, max = 300.dp), state, onIntent) {
            when (state.workspaceSection) {
                WorkspaceSection.LIBRARY -> LibraryPanel(state, onIntent)
                else -> {
                    PartsPanel(state, onIntent)
                    if (state.selectedPartId != null) {
                        MidiQualityReviewPanel(state, onIntent)
                        AudioPreparationPanel(state, onIntent)
                    }
                }
            }
        }
        WorkstationColumn(Modifier.weight(1f), state, onIntent) {
            StructurePanel(state, onIntent)
            ArrangementPanel(state, onIntent)
            TimelinePanel(state, onIntent)
        }
        WorkstationColumn(Modifier.widthIn(min = 250.dp, max = 320.dp), state, onIntent) {
            AiSongPlanPanel(state, onIntent)
            if (state.workspaceSection == WorkspaceSection.MIX_MASTER) MixPanel(state, onIntent) else ScenePresentationPanel(compact = true)
        }
    }
}

@Composable
private fun NarrowWorkstation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) =
    WorkstationColumn(Modifier.fillMaxSize(), state, onIntent) {
        when (state.workspaceSection) {
            WorkspaceSection.PROJECT -> {
                PartsPanel(state, onIntent)
                if (state.selectedPartId != null) {
                    MidiQualityReviewPanel(state, onIntent)
                    AudioPreparationPanel(state, onIntent)
                }
            }
            WorkspaceSection.STRUCTURE -> StructurePanel(state, onIntent)
            WorkspaceSection.ARRANGE -> { ArrangementPanel(state, onIntent); TimelinePanel(state, onIntent); AiSongPlanPanel(state, onIntent) }
            WorkspaceSection.MIX_MASTER -> MixPanel(state, onIntent)
            WorkspaceSection.LIBRARY -> LibraryPanel(state, onIntent)
        }
    }

@Composable
private fun WorkstationColumn(modifier: Modifier, state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)
    ) { content() }
}

/** Visual-only reference regions. They deliberately never read project, clock, network, weather, or location data. */
@Composable
private fun VideoConceptPanel() = WorkspaceCard("Video concept", WorkspaceTags.VIDEO_CONCEPT_PANEL) {
    Text("No visual concept configured", fontWeight = FontWeight.Medium)
    Text("This deterministic placeholder does not create or fetch scene artwork.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CurrentLocationPanel() = CompactVisualPlaceholder(
    title = "Current location",
    tag = WorkspaceTags.CURRENT_LOCATION_PANEL,
    headline = "Location unavailable",
    detail = "No local location data is collected."
)

@Composable
private fun NextDestinationPanel() = CompactVisualPlaceholder(
    title = "Next destination",
    tag = WorkspaceTags.NEXT_DESTINATION_PANEL,
    headline = "Destination unavailable",
    detail = "No destination is configured."
)

@Composable
private fun CompactVisualPlaceholder(title: String, tag: String, headline: String, detail: String) = Card(
    modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, fontSize = MusicWorkspaceTokens.Type.Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(thickness = MusicWorkspaceTokens.Shell.DividerThickness, color = MaterialTheme.colorScheme.outline.copy(alpha = MusicWorkspaceTokens.Shell.DividerAlpha))
        Text(headline, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScenePresentationPanel(compact: Boolean = false) = WorkspaceCard("Scene", WorkspaceTags.PRESENTATION_PANEL) {
    Box(Modifier.fillMaxWidth().height(if (compact) 80.dp else 190.dp).clip(MaterialTheme.shapes.medium).background(MusicWorkspaceTokens.ScenePlaceholder).semantics { contentDescription = "Presentation artwork placeholder; no scene artwork is available" }) {
        Text("Artwork unavailable", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
    }
    Text("Presentation metadata is visual-only and deterministic.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AiSongPlanPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("AI Song Plan", WorkspaceTags.AI_PLAN_PANEL) {
    val arrangement = state.arrangement
    when {
        arrangement == null -> Text("Generate a validated arrangement to see the local song plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        arrangement.stale -> Text("Plan is stale. Regenerate from current structure and analyses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        arrangement.sections.isEmpty() -> Text("No validated plan sections are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> arrangement.sections.forEach { section ->
            val selected = section.index == state.selectedArrangementSection
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .background(if (selected) MusicWorkspaceTokens.Teal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                    .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }.padding(MusicWorkspaceTokens.Spacing.Sm)
                    .semantics { contentDescription = "${section.instanceId}, ${section.purpose}, ${(section.energy * 100).toInt()} percent energy" },
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(section.instanceId, color = MusicWorkspaceTokens.Teal, fontWeight = FontWeight.SemiBold)
                Text(section.purpose, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(section.energy * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun WorkstationFooter(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= MusicWorkspaceTokens.Reference.MediumBreakpoint) {
            Row(Modifier.height(MusicWorkspaceTokens.Reference.FooterHeight), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Reference.ColumnGap)) {
                CompactTransport(state, onIntent, Modifier.weight(1.35f))
                MixerStrips(state, onIntent, Modifier.weight(1f))
                MasterBusStrip(Modifier.width(210.dp))
            }
        } else CompactTransport(state, onIntent)
    }
}

@Composable
private fun MixerStrips(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) = Card(
    modifier = modifier.height(MusicWorkspaceTokens.Reference.FooterHeight).semantics { testTag = WorkspaceTags.MIXER; contentDescription = "Five channel mixer strips" },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface), border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
) {
    Row(Modifier.fillMaxSize().padding(MusicWorkspaceTokens.Spacing.Sm), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        listOf("piano", "bass", "drums", "pad", "strings").forEach { name ->
            val setting = state.mix?.settings?.tracks?.get(name)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).width(4.dp).background(instrumentLaneColors.getValue(name)))
                Text("%.1f dB".format(java.util.Locale.ROOT, setting?.gainDb ?: 0.0), style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.MIX_MASTER)) }, modifier = Modifier.sizeIn(minWidth = MusicWorkspaceTokens.Interaction.MinimumHitTarget, minHeight = MusicWorkspaceTokens.Interaction.MinimumHitTarget)) { Text("Mix") }
    }
}

@Composable
private fun MasterBusStrip(modifier: Modifier) = Card(
    modifier = modifier.height(MusicWorkspaceTokens.Reference.FooterHeight).semantics { testTag = WorkspaceTags.MASTER_OUTPUT; contentDescription = "Master output and bus controls" },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface), border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text("MASTER BUS", style = MaterialTheme.typography.labelSmall)
        Text("Soft Lo-Fi · unavailable until Build Song", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Glue Comp · unavailable until Build Song", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Limiter · unavailable until Build Song", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
