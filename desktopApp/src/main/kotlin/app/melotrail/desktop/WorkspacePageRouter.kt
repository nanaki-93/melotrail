package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.StructureSectionSummary

internal object WorkspacePageTags {
    const val ROOT_PREFIX = "workspace-page-"
    const val OVERVIEW_SECTION_STRIP = "overview-section-strip"
    const val OVERVIEW_TRACKS = "overview-track-overview"
    const val OVERVIEW_PREVIEW = "overview-video-preview"
    const val OVERVIEW_SECTION_INFO = "overview-section-info"
    const val OVERVIEW_EXPORT = "overview-export"
}

@Composable
internal fun WorkspacePageRouter(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) {
    if (state.workspaceSection == WorkspaceSection.OVERVIEW) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
            OverviewNavigation(state, onIntent)
            OverviewPage(state, onIntent, Modifier.weight(1f))
        }
    } else {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
            WorkflowNavigation(state, onIntent)
            InterimWorkflowPage(state, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Pages.NavigationHeight)
            .horizontalScroll(rememberScrollState()).semantics {
                testTag = WorkspaceTags.WORKSPACE_NAV
                contentDescription = "Overview navigation"
            },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceSection.entries.forEach { destination ->
            NavigationItem(destination, selected = destination == state.workspaceSection, onIntent, compact = true)
        }
    }
}

@Composable
private fun WorkflowNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Column(
        Modifier.width(MusicWorkspaceTokens.Pages.SidebarWidth).fillMaxHeight().semantics {
            testTag = WorkspaceTags.WORKSPACE_NAV
            contentDescription = "Workflow navigation"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        Text("WORKSPACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkspaceSection.entries.forEach { destination ->
            NavigationItem(destination, selected = destination == state.workspaceSection, onIntent, compact = false)
        }
    }
}

@Composable
private fun NavigationItem(destination: WorkspaceSection, selected: Boolean, onIntent: (WorkspaceIntent) -> Unit, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (selected) MusicWorkspaceTokens.OliveAccent.copy(alpha = 0.18f) else MusicWorkspaceTokens.ElevatedSurface)
            .clickable { onIntent(WorkspaceIntent.SelectWorkspaceSection(destination)) }
            .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = if (compact) MusicWorkspaceTokens.Spacing.Xs else MusicWorkspaceTokens.Spacing.Sm)
            .semantics {
                testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + destination.name.lowercase()
                contentDescription = "Open ${destination.label}${if (selected) ", selected" else ""}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(destination.navigationSymbol(), color = if (selected) MusicWorkspaceTokens.OliveAccent else MaterialTheme.colorScheme.onSurfaceVariant)
        if (!compact || destination != WorkspaceSection.VIDEO_PREVIEW) {
            Text(destination.label, modifier = Modifier.padding(start = MusicWorkspaceTokens.Spacing.Xs), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) MusicWorkspaceTokens.OliveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun WorkspaceSection.navigationSymbol(): String = when (this) {
    WorkspaceSection.OVERVIEW -> "⌂"
    WorkspaceSection.IMPORT -> "⇩"
    WorkspaceSection.STRUCTURE -> "▤"
    WorkspaceSection.ARRANGE -> "◇"
    WorkspaceSection.MIX_MASTER -> "▥"
    WorkspaceSection.VIDEO_PREVIEW -> "▧"
    WorkspaceSection.EXPORT -> "⇧"
}

@Composable
private fun OverviewPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) = PageRoot(WorkspaceSection.OVERVIEW, modifier) {
    val project = state.project
    val sections = overviewSections(state)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                PageTitle(project?.name ?: "No project open", overviewMetadata(state))
                OverviewSectionStrip(sections, state.selectedArrangementSection, onIntent)
                TrackOverview(state, sections)
            }
            Column(Modifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                VideoPreviewPlaceholder()
                SelectedSectionInfo(sections, state.selectedArrangementSection)
                Button(
                    onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.EXPORT)) },
                    modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.OVERVIEW_EXPORT; contentDescription = "Open Export" }
                ) { Text("Export") }
            }
        }
        Spacer(Modifier.weight(1f))
        CompactTransport(state, onIntent, Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Pages.OverviewTransportHeight))
    }
}

@Composable
private fun PageTitle(title: String, metadata: String) = Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun overviewMetadata(state: WorkspaceUiState): String {
    val project = state.project ?: return "Project metadata unavailable"
    val format = project.renderFormat?.let { "${it.sampleRate} Hz · ${it.channels} ch" } ?: "Render format unavailable"
    val total = project.structure.mapNotNull(StructureSectionSummary::durationSeconds)
    val duration = if (project.structure.isNotEmpty() && total.size == project.structure.size) formatDuration(total.sum()) else "Duration unavailable"
    return "$format · $duration"
}

private data class OverviewSection(val index: Int, val id: String, val duration: Double?, val instruments: Set<String>?)

private fun overviewSections(state: WorkspaceUiState): List<OverviewSection> {
    val arrangement = state.arrangement
    if (arrangement != null && !arrangement.stale) return arrangement.sections.map(ArrangementSectionSnapshot::toOverviewSection)
    return state.project?.structure.orEmpty().map { section -> OverviewSection(section.index, section.instanceId, section.durationSeconds, null) }
}

private fun ArrangementSectionSnapshot.toOverviewSection() = OverviewSection(index, instanceId, durationSeconds, instruments.map { it.name }.toSet())

@Composable
private fun OverviewSectionStrip(sections: List<OverviewSection>, selected: Int?, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.OVERVIEW_SECTION_STRIP, "Song sections") {
    if (sections.isEmpty()) {
        Text("Song sections unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        sections.forEach { section ->
            val isSelected = section.index == selected
            Column(
                Modifier.width(84.dp).clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MusicWorkspaceTokens.OliveAccent.copy(alpha = 0.18f) else MusicWorkspaceTokens.ElevatedSurface)
                    .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }
                    .padding(MusicWorkspaceTokens.Spacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
            ) {
                Text(section.id, fontWeight = FontWeight.SemiBold)
                Text(section.duration?.let(::formatDuration) ?: "Time unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrackOverview(state: WorkspaceUiState, sections: List<OverviewSection>) = OverviewCard(WorkspacePageTags.OVERVIEW_TRACKS, "Track overview") {
    listOf("piano", "bass", "drums", "pad", "strings").forEach { lane ->
        Row(Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Pages.CompactRowHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(lane.replaceFirstChar(Char::uppercase), modifier = Modifier.width(76.dp), style = MaterialTheme.typography.labelMedium)
            if (state.arrangement == null || state.arrangement.stale || sections.isEmpty()) {
                Text("Signal unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                sections.forEach { section ->
                    val active = lane in section.instruments.orEmpty()
                    Box(Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall)
                        .background(if (active) instrumentLaneColors[lane]?.copy(alpha = 0.42f) ?: MusicWorkspaceTokens.ElevatedSurface else MusicWorkspaceTokens.ElevatedSurface),
                        contentAlignment = Alignment.Center
                    ) { if (active) Text("Active", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewPlaceholder() = OverviewCard(WorkspacePageTags.OVERVIEW_PREVIEW, "Video preview") {
    Box(Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.small).background(MusicWorkspaceTokens.ScenePlaceholder), contentAlignment = Alignment.Center) {
        Text("Local video preview unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedSectionInfo(sections: List<OverviewSection>, selected: Int?) = OverviewCard(WorkspacePageTags.OVERVIEW_SECTION_INFO, "Section info") {
    val section = sections.firstOrNull { it.index == selected } ?: sections.firstOrNull()
    if (section == null) Text("Selected section unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else {
        Text(section.id, style = MaterialTheme.typography.titleLarge)
        Text("Time: ${section.duration?.let(::formatDuration) ?: "unavailable"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Mood, energy, and density unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewCard(tag: String, title: String, content: @Composable () -> Unit) = Card(
    Modifier.fillMaxWidth().semantics { testTag = tag }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
) { Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    content()
} }

@Composable
private fun InterimWorkflowPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) = PageRoot(state.workspaceSection, modifier) {
    val title = state.workspaceSection.label
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle(title, workflowSubtitle(state))
        OverviewCard("${WorkspacePageTags.ROOT_PREFIX}${state.workspaceSection.name.lowercase()}-body", "${title} workspace") {
            Text(workflowBody(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state.workspaceSection) {
                WorkspaceSection.IMPORT -> Button(onClick = { onIntent(WorkspaceIntent.ShowAddPart) }) { Text("Add part") }
                WorkspaceSection.EXPORT -> OutlinedButton(onClick = {}, enabled = false) { Text("Export Song") }
                else -> Unit
            }
        }
    }
}

private fun workflowSubtitle(state: WorkspaceUiState): String = when (state.workspaceSection) {
    WorkspaceSection.IMPORT -> "Import a MIDI or eligible solo-piano audio source."
    WorkspaceSection.STRUCTURE -> "Canonical structure has ${state.project?.structure?.size ?: 0} section(s)."
    WorkspaceSection.ARRANGE -> "Arrangement state is derived from canonical artifacts."
    WorkspaceSection.MIX_MASTER -> "Mix and master readiness is derived from validated artifacts."
    WorkspaceSection.VIDEO_PREVIEW -> "Local visual preview only."
    WorkspaceSection.EXPORT -> "Release export is available only after a current master."
    WorkspaceSection.OVERVIEW -> error("Overview has its own page")
}

private fun workflowBody(state: WorkspaceUiState): String = when (state.workspaceSection) {
    WorkspaceSection.IMPORT -> if (state.project == null) "Create or open a project before importing." else "Choose a source through the validated import dialog."
    WorkspaceSection.EXPORT -> if (state.project?.readiness?.releaseAvailable == true && !state.downstreamArtifactsStale) "A release is available for export." else "A current validated release is unavailable. Build the current project first."
    else -> "This focused page is not implemented in Task 083."
}

@Composable
private fun PageRoot(section: WorkspaceSection, modifier: Modifier, content: @Composable () -> Unit) = Box(
    modifier.fillMaxSize().semantics { testTag = WorkspacePageTags.ROOT_PREFIX + section.name.lowercase() }
) { content() }
