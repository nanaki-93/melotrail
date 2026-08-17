package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.PartSourceType
import app.melotrail.application.StructureSectionSummary
import app.melotrail.arrangement.LogicalInstrument
import java.net.URI
import java.nio.file.Path

internal object WorkspacePageTags {
    const val ROOT_PREFIX = "workspace-page-"
    const val OVERVIEW_SECTION_STRIP = "overview-section-strip"
    const val OVERVIEW_TRACKS = "overview-track-overview"
    const val OVERVIEW_PREVIEW = "overview-video-preview"
    const val OVERVIEW_SECTION_INFO = "overview-section-info"
    const val OVERVIEW_EXPORT = "overview-export"
    const val VIDEO_PREVIEW_STAGE = "video-preview-stage"
    const val VIDEO_PREVIEW_TIMELINE = "video-preview-timeline"
    const val VIDEO_PREVIEW_PLAY_PAUSE = "video-preview-play-pause"
    const val VIDEO_PREVIEW_STOP = "video-preview-stop"
    const val VIDEO_PREVIEW_SEEK = "video-preview-seek"
    const val VIDEO_PREVIEW_VOLUME = "video-preview-volume"
    const val VIDEO_PREVIEW_CAMERA = "video-preview-camera"
    const val VIDEO_PREVIEW_CHANGE_SCENE = "video-preview-change-scene"
    const val VIDEO_PREVIEW_FULLSCREEN = "video-preview-fullscreen"
    const val VIDEO_PREVIEW_STATUS = "video-preview-status"
    const val IMPORT_DROP_SURFACE = "import-drop-surface"
    const val IMPORT_BROWSE = "import-browse"
    const val IMPORTED_FILES = "imported-files"
    const val IMPORTED_ROW_PREFIX = "imported-file-"
    const val IMPORTED_DETAILS_PREFIX = "imported-details-"
    const val IMPORT_PRIMARY_ACTION = "import-primary-action"
    const val STRUCTURE_PALETTE = "structure-palette"
    const val STRUCTURE_ADD_PREFIX = "structure-add-"
    const val STRUCTURE_STRIP = "structure-strip"
    const val STRUCTURE_TABLE = "structure-table"
    const val STRUCTURE_ROW_PREFIX = "structure-row-"
    const val STRUCTURE_EDIT_PREFIX = "structure-edit-"
    const val STRUCTURE_DUPLICATE_PREFIX = "structure-duplicate-"
    const val STRUCTURE_REMOVE_PREFIX = "structure-remove-"
    const val ARRANGE_PLANNER_PREFIX = "arrange-planner-"
    const val ARRANGE_INSTRUMENT_PREFIX = "arrange-instrument-"
    const val ARRANGE_STYLE = "arrange-style"
    const val ARRANGE_INTENSITY = "arrange-intensity"
    const val ARRANGE_PRIMARY_ACTION = "arrange-primary-action"
    const val ARRANGE_PREREQUISITE = "arrange-prerequisite"
    const val ARRANGE_DIAGNOSTICS_TOGGLE = "arrange-diagnostics-toggle"
    const val ARRANGE_DIAGNOSTICS = "arrange-diagnostics"
    const val ARRANGE_REVIEW = "arrange-review"
    const val ARRANGE_APPROVE = "arrange-approve"
    const val MIX_CHANNEL_PREFIX = "mix-master-channel-"
    const val MIX_GAIN_PREFIX = "mix-master-gain-"
    const val MIX_PAN_PREFIX = "mix-master-pan-"
    const val MIX_MUTE_PREFIX = "mix-master-mute-"
    const val MIX_SOLO_PREFIX = "mix-master-solo-"
    const val MIX_METER_PREFIX = "mix-master-meter-"
    const val MIX_MODE_LISTEN = "mix-master-mode-listen"
    const val MIX_MODE_MIX = "mix-master-mode-mix"
    const val MIX_MODE_MASTER = "mix-master-mode-master"
    const val MIX_PLAYBACK_DRY = "mix-master-playback-dry"
    const val MIX_PLAYBACK_LOFI = "mix-master-playback-lofi"
    const val MIX_PLAYBACK_MASTER = "mix-master-playback-master"
    const val MIX_MASTER_VOLUME = "mix-master-volume"
    const val MIX_LOFI = "mix-master-lofi"
    const val MIX_MP3 = "mix-master-mp3"
    const val MIX_RESET = "mix-master-reset"
    const val MIX_UNSUPPORTED_DSP = "mix-master-unsupported-dsp"
    const val MIX_PRIMARY_ACTION = "mix-master-primary-action"
    const val MIX_BUILD_STATUS = "mix-master-build-status"
    const val MIX_ZERO_SIGNAL = "mix-master-zero-signal"
    const val EXPORT_FORMAT_PREFIX = "export-format-"
    const val EXPORT_QUALITY = "export-quality"
    const val EXPORT_SAMPLE_RATE = "export-sample-rate"
    const val EXPORT_FILENAME = "export-filename"
    const val EXPORT_DESTINATION = "export-destination"
    const val EXPORT_BROWSE = "export-browse"
    const val EXPORT_SUMMARY = "export-summary"
    const val EXPORT_ACTION = "export-action"
    const val EXPORT_STATUS = "export-status"
    const val NAVIGATION_MENU = "workspace-navigation-menu"
}

@Composable
internal fun WorkspacePageRouter(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    modifier: Modifier = Modifier,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester> = mutableMapOf()
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
        when {
            narrow -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
                NarrowNavigation(state, onIntent)
                PageForSection(state, onIntent, Modifier.weight(1f), narrow = true, partDetailsFocusTargets)
            }
            state.workspaceSection == WorkspaceSection.OVERVIEW -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
                OverviewNavigation(state, onIntent)
                OverviewPage(state, onIntent, Modifier.weight(1f))
            }
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
                WorkflowNavigation(state, onIntent)
                InterimWorkflowPage(state, onIntent, Modifier.weight(1f), partDetailsFocusTargets)
            }
        }
    }
}

@Composable
private fun PageForSection(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    modifier: Modifier,
    narrow: Boolean,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) {
    if (state.workspaceSection == WorkspaceSection.OVERVIEW) OverviewPage(state, onIntent, modifier, narrow)
    else InterimWorkflowPage(state, onIntent, modifier, partDetailsFocusTargets)
}

@Composable
private fun NarrowNavigation(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().semantics {
        testTag = WorkspaceTags.WORKSPACE_NAV
        contentDescription = "Workspace navigation. Current page: ${state.workspaceSection.label}."
    }) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().semantics {
                testTag = WorkspacePageTags.NAVIGATION_MENU
                contentDescription = "Choose workspace page. Current page: ${state.workspaceSection.label}."
            }
        ) { Text(state.workspaceSection.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkspaceSection.entries.forEach { destination ->
                DropdownMenuItem(
                    text = { Text(destination.label) },
                    onClick = {
                        expanded = false
                        onIntent(WorkspaceIntent.SelectWorkspaceSection(destination))
                    },
                    modifier = Modifier.semantics {
                        testTag = WorkspaceTags.WORKSPACE_SECTION_PREFIX + destination.name.lowercase()
                        contentDescription = "Open ${destination.label}${if (destination == state.workspaceSection) ", selected" else ""}"
                    }
                )
            }
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
private fun OverviewPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier, narrow: Boolean = false) = PageRoot(WorkspaceSection.OVERVIEW, modifier) {
    val project = state.project
    val sections = overviewSections(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                PageTitle(project?.name ?: "No project open", overviewMetadata(state))
                OverviewSectionStrip(sections, state.selectedArrangementSection, onIntent)
                TrackOverview(state, sections)
            }
        }, second = { columnModifier ->
            Column(columnModifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                VideoPreviewPlaceholder()
                SelectedSectionInfo(sections, state.selectedArrangementSection)
                Button(
                    onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.EXPORT)) },
                    modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.OVERVIEW_EXPORT; contentDescription = "Open Export" }
                ) { Text("Export") }
            }
        })
        CompactTransport(state, onIntent, Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Pages.OverviewTransportHeight))
    }
}

@Composable
private fun ResponsivePageColumns(
    narrow: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (narrow) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        first(Modifier.fillMaxWidth())
        second(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PageTitle(title: String, metadata: String) = Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
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
private fun InterimWorkflowPage(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    modifier: Modifier,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = PageRoot(state.workspaceSection, modifier) {
    if (state.workspaceSection == WorkspaceSection.IMPORT) {
        ImportPage(state, onIntent, partDetailsFocusTargets)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.STRUCTURE) {
        StructurePage(state, onIntent)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.ARRANGE) {
        ArrangePage(state, onIntent)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.MIX_MASTER) {
        MixMasterPage(state, onIntent)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.VIDEO_PREVIEW) {
        VideoPreviewPage(state, onIntent)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.EXPORT) {
        ExportPage(state, onIntent)
        return@PageRoot
    }
    val title = state.workspaceSection.label
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle(title, workflowSubtitle(state))
        OverviewCard("${WorkspacePageTags.ROOT_PREFIX}${state.workspaceSection.name.lowercase()}-body", "${title} workspace") {
            Text(workflowBody(state), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val export = state.export
    val draft = export.draft
    val inspection = export.inspection
    val summary = inspection?.summary
    val formats = inspection?.supportedFormats.orEmpty()
    val destinationIsProjectOutput = state.project?.root?.resolve("output")?.toAbsolutePath()?.normalize() == draft.destination?.toAbsolutePath()?.normalize()
    val canExport = summary != null && draft.format in formats && destinationIsProjectOutput && !state.operation.isMutating
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Export", "Render and export your final track")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                OverviewCard("export-settings", "Export settings") {
                    Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                        formats.forEach { format ->
                            OutlinedButton(
                                onClick = {
                                    val base = draft.filename.substringBeforeLast('.', draft.filename).ifBlank { "song" }
                                    onIntent(WorkspaceIntent.UpdateExportDraft(draft.copy(format = format, filename = "$base.${format.extension}")))
                                },
                                modifier = Modifier.weight(1f).semantics { testTag = WorkspacePageTags.EXPORT_FORMAT_PREFIX + format.name.lowercase(); contentDescription = "${format.name} export format${if (draft.format == format) ", selected" else ""}" }
                            ) { Text(format.name) }
                        }
                    }
                    Text("Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField("PCM ${summary?.pcmBitDepth ?: 24}-bit lossless", {}, readOnly = true, enabled = summary != null,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_QUALITY })
                    Text("Sample rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(summary?.sampleRate?.let { "$it Hz" } ?: "Unavailable", {}, readOnly = true, enabled = summary != null,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_SAMPLE_RATE })
                    Text("File name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(draft.filename, { onIntent(WorkspaceIntent.UpdateExportDraft(draft.copy(filename = it))) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_FILENAME })
                    Text("Destination", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(draft.destination?.toString() ?: "Project output folder", {}, readOnly = true, modifier = Modifier.weight(1f).semantics { testTag = WorkspacePageTags.EXPORT_DESTINATION })
                        OutlinedButton(onClick = { onIntent(WorkspaceIntent.ChooseExportDestination) }, enabled = !state.operation.isMutating,
                            modifier = Modifier.semantics { testTag = WorkspacePageTags.EXPORT_BROWSE; contentDescription = "Choose export destination" }) { Text("Browse") }
                    }
                    Button(onClick = { onIntent(WorkspaceIntent.ExportSong) }, enabled = canExport, colors = workspacePrimaryButtonColors(),
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_ACTION; contentDescription = if (canExport) "Export Song" else exportBlockedMessage(export, destinationIsProjectOutput) }) { Text(if (state.operation is WorkspaceOperation.ExportingRelease) "Exporting…" else "Export Song") }
                    Text(exportBlockedMessage(export, destinationIsProjectOutput), style = MaterialTheme.typography.bodySmall,
                        color = if (canExport) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.EXPORT_STATUS })
                }
            }
            }, second = { columnModifier ->
            Column(columnModifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                OverviewCard(WorkspacePageTags.EXPORT_SUMMARY, "Export summary") {
                    Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.durationSeconds?.let(::formatDuration) ?: "Unavailable")
                    Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.let { "WAV · PCM ${it.pcmBitDepth}-bit" } ?: "Unavailable")
                    Text("Quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.let { "PCM ${it.pcmBitDepth}-bit lossless" } ?: "Unavailable")
                    Text("Sample rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.sampleRate?.let { "$it Hz" } ?: "Unavailable")
                    Text("Channels / tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary?.let { "${it.channels} ch · ${it.trackCount} source tracks" } ?: "Unavailable")
                }
            }
            })
        }
    }
}

private fun exportBlockedMessage(export: ExportUiState, destinationIsProjectOutput: Boolean = true): String = when {
    export.inspecting -> "Checking current release artifacts…"
    export.inspection?.summary == null -> export.inspection?.blockedReason ?: "Build a current master and release metadata first."
    export.draft.format !in export.inspection.supportedFormats -> "That export format is unavailable. Use WAV or start the local worker with lameenc installed."
    !destinationIsProjectOutput -> "Choose the project output folder; exports cannot escape the project."
    else -> "WAV is the authoritative lossless master. Export validates the published output before reporting success."
}

/**
 * Visual-only adapter over the shared playback session. It deliberately has
 * no scene clock, video decoder, artwork provider, or export state.
 */
@Composable
private fun VideoPreviewPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val session = state.playbackSession
    val audioOutput = state.runtimeReadiness?.audioOutput
    val canControlPlayback = session.artifact != null && (audioOutput?.available == true || session.phase in setOf(PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED))
    val canStop = session.phase in setOf(PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED)
    val canSeek = session.artifact != null && session.durationSeconds > 0.0 && session.phase in setOf(PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED, PlaybackSessionPhase.STOPPED)
    val title = state.project?.name ?: "No project open"
    val status = videoPreviewStatus(state)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Video Preview", "Local visual reference for $title")
        OverviewCard(WorkspacePageTags.VIDEO_PREVIEW_STAGE, "Local scene placeholder") {
            Box(
                Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Pages.VideoPreviewSceneHeight).clip(MaterialTheme.shapes.small)
                    .background(MusicWorkspaceTokens.ScenePlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Text("LOCAL VISUAL PLACEHOLDER", style = MaterialTheme.typography.labelMedium, color = MusicWorkspaceTokens.TealFocus)
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    Text("No local scene is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_TIMELINE }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDuration(session.positionSeconds), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(session.durationSeconds), style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = session.positionSeconds.toFloat(),
                    onValueChange = { onIntent(WorkspaceIntent.SeekPlayback(it.toDouble())) },
                    valueRange = 0f..session.durationSeconds.coerceAtLeast(0.01).toFloat(),
                    enabled = canSeek,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_SEEK; contentDescription = "Seek shared local playback selection" }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    VideoUnavailableControl("▣", "Camera selection is unavailable", WorkspacePageTags.VIDEO_PREVIEW_CAMERA)
                    VideoUnavailableControl("↻", "Scene change is unavailable", WorkspacePageTags.VIDEO_PREVIEW_CHANGE_SCENE)
                    Spacer(Modifier.weight(1f))
                    VideoUnavailableControl("⛶", "Fullscreen video is unavailable", WorkspacePageTags.VIDEO_PREVIEW_FULLSCREEN)
                }
            }
        }
        Text(status, modifier = Modifier.semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_STATUS }, style = MaterialTheme.typography.bodySmall,
            color = if (session.phase == PlaybackSessionPhase.FAILED || audioOutput?.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.COMPACT_TRANSPORT; contentDescription = "Shared local playback transport" },
            colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
        ) {
            Row(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = canStop,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_STOP; contentDescription = "Stop shared local playback" }) { Text("Stop") }
                Button(onClick = { onIntent(WorkspaceIntent.PlayPause) }, enabled = canControlPlayback,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_PLAY_PAUSE; contentDescription = if (canControlPlayback) "Play or pause shared local playback" else status }) {
                    Text(if (session.phase == PlaybackSessionPhase.PLAYING) "Pause" else "Play")
                }
                Column(Modifier.weight(1f)) {
                    Text("Shared playback volume · ${(session.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(session.volume.toFloat(), { onIntent(WorkspaceIntent.SetPlaybackVolume(it.toDouble())) }, valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_VOLUME; contentDescription = "Set shared playback volume" })
                }
            }
        }
    }
}

@Composable
private fun VideoUnavailableControl(symbol: String, description: String, tag: String) = OutlinedButton(
    onClick = {}, enabled = false, modifier = Modifier.size(36.dp).semantics { testTag = tag; contentDescription = description },
    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
) { Text(symbol) }

private fun videoPreviewStatus(state: WorkspaceUiState): String {
    val session = state.playbackSession
    return when {
        session.phase == PlaybackSessionPhase.FAILED -> "Playback unavailable: ${session.failureMessage ?: "local playback failed"}"
        state.runtimeReadiness?.audioOutput?.available == false -> "Audio output unavailable: ${state.runtimeReadiness.audioOutput.detail}"
        session.artifact == null -> "No local playback artifact selected. The visual remains a placeholder."
        session.phase == PlaybackSessionPhase.PLAYING -> "Local audio playback is playing; the visual remains a placeholder."
        session.phase == PlaybackSessionPhase.PAUSED -> "Local audio playback is paused; the visual remains a placeholder."
        else -> "A local audio artifact is selected; the visual remains a placeholder."
    }
}

private data class ArrangePrerequisites(
    val shortReason: String,
    val diagnostics: List<String>
) {
    val canGenerate: Boolean get() = diagnostics.none { it.startsWith("Missing:") }
}

private fun arrangePrerequisites(state: WorkspaceUiState): ArrangePrerequisites {
    val project = state.project
        ?: return ArrangePrerequisites(
            shortReason = "Open a project to arrange.",
            diagnostics = listOf("Missing: project", "Missing: canonical structure", "Missing: MIDI analyses", "Missing: approved cohesion")
        )
    val structureReady = project.readiness.structureReady && state.structureDraft.isNotEmpty()
    val missingAnalyses = state.structureDraft.toSet().filter { id ->
        project.parts.firstOrNull { it.id == id }?.analysis?.status != app.melotrail.application.PartAnalysisStatus.MIDI
    }
    val analysesReady = project.readiness.analysesReady && missingAnalyses.isEmpty()
    val cohesionRequired = project.version >= 3
    val cohesionReady = !cohesionRequired || project.readiness.cohesionReady
    val diagnostics = listOf(
        if (structureReady) "Canonical structure is current." else "Missing: save a current canonical structure.",
        if (analysesReady) "MIDI analyses are current for every structure part." else "Missing: analyze ${missingAnalyses.ifEmpty { state.structureDraft.toSet() }.joinToString(", ")}.",
        when {
            !cohesionRequired -> "Cohesion is not required for this legacy project."
            cohesionReady -> "Cohesion is current and approved."
            else -> "Missing: generate and approve current cohesion."
        }
    )
    val shortReason = when {
        !structureReady -> "Save a current structure before arranging."
        !analysesReady -> "Analyze every structure part before arranging."
        !cohesionReady -> "Approve current cohesion before arranging."
        state.arrangement?.stale == true -> "The retained arrangement is stale; regenerate it."
        state.arrangement?.approvalRequired == true -> "Qwen draft needs explicit approval; generation can replace it."
        else -> "Structure, analyses, and cohesion are current."
    }
    return ArrangePrerequisites(shortReason, diagnostics)
}

@Composable
private fun ArrangePage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val prerequisites = arrangePrerequisites(state)
    val draft = state.arrangementDraft
    val mutating = state.operation.isMutating
    var diagnosticsExpanded by remember(state.project, state.structureDraft, state.arrangement, state.operation) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
    ) {
        PageTitle("Arrange", "Choose how to create the arrangement")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            ArrangementPlannerKind.entries.forEach { planner ->
                PlannerChoiceCard(
                    planner = planner,
                    selected = draft.planner == planner,
                    enabled = !mutating,
                    onClick = { onIntent(WorkspaceIntent.UpdateArrangementPlanner(planner)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        OverviewCard("arrange-instruments", "Instruments") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                LogicalInstrument.entries.chunked(3).forEach { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                        column.forEach { instrument ->
                            val selected = instrument.wireName in draft.instruments
                            val required = instrument == LogicalInstrument.PIANO
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                    .clickable(enabled = !mutating && !required) { onIntent(WorkspaceIntent.ToggleArrangementInstrument(instrument.wireName)) }
                                    .padding(vertical = MusicWorkspaceTokens.Spacing.Xs)
                                    .semantics {
                                        testTag = WorkspacePageTags.ARRANGE_INSTRUMENT_PREFIX + instrument.wireName
                                        contentDescription = "${instrument.wireName} ${if (required) "is required" else if (selected) "is selected" else "is not selected"} for arrangement generation"
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected, onCheckedChange = if (required || mutating) null else { _: Boolean -> onIntent(WorkspaceIntent.ToggleArrangementInstrument(instrument.wireName)) })
                                Text(instrument.wireName.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        OverviewCard("arrange-settings", "Arrangement settings") {
            OutlinedTextField(
                value = draft.style,
                onValueChange = { onIntent(WorkspaceIntent.UpdateArrangementStyle(it)) },
                enabled = !mutating,
                label = { Text("Style (optional)") },
                supportingText = { Text("Up to 160 characters.") },
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.ARRANGE_STYLE }
            )
            Text("Intensity · planner-derived", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = 0.72f,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.weight(1f).semantics {
                        testTag = WorkspacePageTags.ARRANGE_INTENSITY
                        contentDescription = "Intensity is derived by the bounded planner and cannot be manually changed."
                    }
                )
                Text("72%", modifier = Modifier.padding(start = MusicWorkspaceTokens.Spacing.Sm), style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text(
                prerequisites.shortReason,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_PREREQUISITE },
                style = MaterialTheme.typography.bodySmall,
                color = if (prerequisites.canGenerate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_DIAGNOSTICS_TOGGLE }) { Text(if (diagnosticsExpanded) "Hide details" else "Show details") }
            if (diagnosticsExpanded) {
                Column(Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_DIAGNOSTICS }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    prerequisites.diagnostics.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Button(
                onClick = { onIntent(WorkspaceIntent.GenerateArrangement) },
                enabled = prerequisites.canGenerate && !mutating,
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.ARRANGE_PRIMARY_ACTION }
            ) { Text(if (mutating) "Generating arrangement…" else if (state.arrangement?.stale == true) "Regenerate Arrangement" else "Generate Arrangement") }
        }
        ArrangeReview(state, onIntent)
    }
}

@Composable
private fun PlannerChoiceCard(
    planner: ArrangementPlannerKind,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) = Card(
    modifier.clip(MaterialTheme.shapes.small).clickable(enabled = enabled, onClick = onClick).semantics {
        testTag = WorkspacePageTags.ARRANGE_PLANNER_PREFIX + planner.name.lowercase()
        contentDescription = "${planner.name.lowercase()} planner${if (selected) ", selected" else ""}"
    },
    colors = CardDefaults.cardColors(containerColor = if (selected) MusicWorkspaceTokens.OliveAccent.copy(alpha = 0.18f) else MusicWorkspaceTokens.ElevatedSurface)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text(if (planner == ArrangementPlannerKind.DETERMINISTIC) "Deterministic" else "AI (Qwen)", fontWeight = FontWeight.SemiBold)
        Text(
            if (planner == ArrangementPlannerKind.DETERMINISTIC) "Bounded local rules." else "Draft requires approval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArrangeReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val arrangement = state.arrangement ?: return
    OverviewCard(WorkspacePageTags.ARRANGE_REVIEW, "Arrangement review") {
        when {
            arrangement.stale -> Text("Stale arrangement retained as evidence. Regenerate from current canonical inputs before building.", color = MaterialTheme.colorScheme.error)
            arrangement.approvalRequired || !arrangement.approved -> {
                Text("Validated Qwen draft — it is not approved or current.", color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewArrangement) }, enabled = !state.operation.isMutating) { Text("Preview draft") }
                    Button(onClick = { onIntent(WorkspaceIntent.ApproveArrangement) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_APPROVE }) { Text("Approve draft") }
                }
            }
            else -> Text("Approved deterministic arrangement is current.", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StructurePage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = Column(
    Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
) {
    PageTitle("Structure", "Build the canonical order of your song")
    StructurePalette(state, onIntent)
    StructureStrip(state.project?.structure.orEmpty())
    StructureTable(state, onIntent)
}

@Composable
private fun StructurePalette(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_PALETTE, "Prepared parts") {
    val eligible = state.project?.parts.orEmpty().filter { primaryPartAction(it, state.pendingMidiFeel) is PartPrimaryAction.AddToStructure }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        eligible.forEach { part ->
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.AddStructurePart(part.id)) },
                enabled = !state.operation.isMutating,
                modifier = Modifier.semantics {
                    testTag = WorkspacePageTags.STRUCTURE_ADD_PREFIX + part.id
                    contentDescription = "Add prepared part ${part.id} to structure"
                }
            ) { Text("Add ${part.id}") }
        }
    }
}

@Composable
private fun StructureStrip(sections: List<StructureSectionSummary>) = OverviewCard(WorkspacePageTags.STRUCTURE_STRIP, "Order") {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        sections.forEach { section ->
            Column(
                Modifier.width(76.dp).clip(MaterialTheme.shapes.small).background(MusicWorkspaceTokens.ElevatedSurface)
                    .padding(MusicWorkspaceTokens.Spacing.Sm).semantics {
                        testTag = WorkspaceTags.STRUCTURE_OCCURRENCE_PREFIX + section.index
                        contentDescription = "Structure occurrence ${section.instanceId}"
                    }
            ) {
                Text(section.instanceId, fontWeight = FontWeight.SemiBold)
                Text(section.durationSeconds?.let(::formatDuration) ?: "Time unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StructureTable(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_TABLE, "Sections") {
    val sections = state.project?.structure.orEmpty()
    if (sections.isEmpty()) {
        Text("Choose a prepared part to start", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    sections.forEachIndexed { index, section ->
        val part = state.project?.parts?.firstOrNull { it.id == section.partId }
        Row(
            Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.STRUCTURE_ROW_PREFIX + index },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
        ) {
            Text(section.instanceId, modifier = Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
            Text(part?.role?.ifBlank { "Role unavailable" } ?: "Role unavailable", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(part?.analysis?.bars?.let { "$it bars" } ?: "Bars unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onIntent(WorkspaceIntent.ShowRoleEditor(section.partId)) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_EDIT_PREFIX + index; contentDescription = "Edit role for ${section.instanceId}" }) { Text("✎") }
            TextButton(onClick = { onIntent(WorkspaceIntent.DuplicateStructurePart(index)) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_DUPLICATE_PREFIX + index; contentDescription = "Duplicate ${section.instanceId}" }) { Text("⧉") }
            TextButton(onClick = { onIntent(WorkspaceIntent.RemoveStructurePart(index)) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_REMOVE_PREFIX + index; contentDescription = "Remove ${section.instanceId}" }) { Text("×") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index - 1)) }, enabled = !state.operation.isMutating && index > 0, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_LEFT + index; contentDescription = "Move ${section.instanceId} earlier" }) { Text("↑") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructurePart(index, index + 1)) }, enabled = !state.operation.isMutating && index < sections.lastIndex, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_RIGHT + index; contentDescription = "Move ${section.instanceId} later" }) { Text("↓") }
        }
        if (index < sections.lastIndex) HorizontalDivider()
    }
    TextButton(onClick = { onIntent(WorkspaceIntent.ClearStructure) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_CLEAR }) { Text("Clear structure") }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportPage(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = Column(
    Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
) {
    PageTitle("Import", "Import MIDI or eligible solo-piano audio files")
    ImportDropSurface(state, onIntent)
    ImportedFiles(state, onIntent, partDetailsFocusTargets)
    ImportPrimaryAction(state, onIntent, partDetailsFocusTargets)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportDropSurface(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val dropTarget = remember(onIntent) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val path = droppedPartSource(event) ?: return false
                onIntent(WorkspaceIntent.ImportSourceChosen(path))
                return true
            }
        }
    }
    val enabled = state.project != null && !state.operation.isMutating
    Card(
        Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Pages.ImportDropHeight)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> droppedPartSource(event) != null },
                target = dropTarget
            ).semantics {
                testTag = WorkspacePageTags.IMPORT_DROP_SURFACE
                contentDescription = if (enabled) "Drop one MIDI, WAV, or MP3 file, or browse files" else "Import unavailable. Create or open a project first."
            },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Pages.ContentInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
        ) {
            Text("⌑", style = MaterialTheme.typography.headlineMedium, color = MusicWorkspaceTokens.OliveAccent)
            Text("Drag and drop a file here", fontWeight = FontWeight.Medium)
            Text("or", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onIntent(WorkspaceIntent.ShowAddPart) }, enabled = enabled,
                modifier = Modifier.semantics {
                    testTag = WorkspacePageTags.IMPORT_BROWSE
                    contentDescription = "Browse supported source files"
                }
            ) { Text("Browse files") }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun droppedPartSource(event: DragAndDropEvent): Path? = runCatching {
    val files = event.dragData() as? DragData.FilesList ?: return null
    val uri = files.readFiles().singleOrNull() ?: return null
    URI(uri).takeIf { it.scheme.equals("file", ignoreCase = true) }?.let(Path::of)
}.getOrNull()

@Composable
private fun ImportedFiles(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = Card(
    Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.IMPORTED_FILES },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text("IMPORTED FILES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val parts = state.project?.parts.orEmpty()
        if (parts.isEmpty()) {
            Text("No files imported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            parts.forEachIndexed { index, part ->
                val focusReturn = PartDetailsFocusReturn.ImportedRow(part.id)
                val focusRequester = remember(part.id) { FocusRequester() }
                DisposableEffect(focusReturn, focusRequester) {
                    partDetailsFocusTargets[focusReturn] = focusRequester
                    onDispose { partDetailsFocusTargets.remove(focusReturn, focusRequester) }
                }
                Row(
                    Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.IMPORTED_ROW_PREFIX + part.id },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
                ) {
                    Text(if (part.sourceType == PartSourceType.MIDI) "♫" else "⌁", color = MusicWorkspaceTokens.OliveAccent)
                    Column(Modifier.weight(1f)) {
                        Text(part.sourceName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${part.sourceType.name} · ${formatImportFileSize(part.sourceSizeBytes)} · ${state.partPreparationLabel(part.id)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { onIntent(WorkspaceIntent.ShowPartDetails(part.id, focusReturn)) },
                        modifier = Modifier.focusRequester(focusRequester).semantics {
                            testTag = WorkspacePageTags.IMPORTED_DETAILS_PREFIX + part.id
                            contentDescription = "Details for ${part.sourceName}"
                        }
                    ) { Text("⋮") }
                }
                if (index < parts.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ImportPrimaryAction(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) {
    val part = state.project?.parts?.let { parts ->
        state.selectedPartId?.let { selected -> parts.firstOrNull { it.id == selected } }
            ?: parts.firstOrNull { primaryPartAction(it, null) !is PartPrimaryAction.AddToStructure }
            ?: parts.firstOrNull()
    }
    val action = part?.let { primaryPartAction(it, state.pendingMidiFeel) }
    val focusReturn = PartDetailsFocusReturn.ImportPrimaryAction
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(focusRequester) {
        partDetailsFocusTargets[focusReturn] = focusRequester
        onDispose { partDetailsFocusTargets.remove(focusReturn, focusRequester) }
    }
    when {
        state.operation is WorkspaceOperation.Failed -> Unit // The one safe retry remains in the global feedback banner.
        action != null -> Button(
            onClick = { dispatchImportPrimaryAction(action, onIntent, focusReturn) }, enabled = !state.operation.isMutating,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { testTag = WorkspacePageTags.IMPORT_PRIMARY_ACTION }
        ) { Text(action.label()) }
    }
}

private fun dispatchImportPrimaryAction(
    action: PartPrimaryAction,
    onIntent: (WorkspaceIntent) -> Unit,
    focusReturn: PartDetailsFocusReturn = PartDetailsFocusReturn.ImportPrimaryAction
) = when (action) {
    is PartPrimaryAction.PrepareMidi -> onIntent(WorkspaceIntent.PrepareMidi(action.partId))
    is PartPrimaryAction.ReviewRepair -> onIntent(WorkspaceIntent.ShowPartDetails(action.partId, focusReturn))
    is PartPrimaryAction.InspectOrTranscribeAudio -> {
        onIntent(WorkspaceIntent.SelectPart(action.partId))
        onIntent(if (action.inspected) WorkspaceIntent.TranscribeSelectedPart else WorkspaceIntent.InspectSelectedPart)
    }
    is PartPrimaryAction.ApplyLoFiChange -> {
        onIntent(WorkspaceIntent.SelectPart(action.partId))
        onIntent(WorkspaceIntent.ApplyMidiFeelAndReanalyze)
    }
    is PartPrimaryAction.Analyze -> onIntent(WorkspaceIntent.AnalyzePart(action.partId))
    is PartPrimaryAction.AddToStructure -> onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE))
    is PartPrimaryAction.FixIssue -> onIntent(WorkspaceIntent.ShowPartDetails(action.partId, focusReturn))
}

private fun formatImportFileSize(bytes: Long?): String = when {
    bytes == null -> "size unavailable"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
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

private enum class MixMasterMode { LISTEN, MIX, MASTER }

/** Focused adapter over existing mix/build/playback intents; it owns no audio or file work. */
@Composable
private fun MixMasterPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    var mode by remember { mutableStateOf(MixMasterMode.LISTEN) }
    val mix = state.mix
    val mutating = state.operation.isMutating
    val currentSource = (state.playbackSession.request as? PlaybackRequest.Mix)?.source ?: PlaybackSource.DRY
    val buildReady = mixMasterCanBuild(state)
    val buildMessage = mixMasterBuildMessage(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Mix & Master", "Adjust stems and build the master WAV")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                Text("CHANNELS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LogicalInstrument.entries.forEach { instrument ->
                    val name = instrument.wireName
                    val setting = mix?.settings?.tracks?.get(name) ?: app.melotrail.application.LogicalMixSetting()
                    val available = name in mix?.availableStems.orEmpty()
                    MixMasterChannel(name, setting, available, available && !mutating) { onIntent(WorkspaceIntent.UpdateMixSetting(name, it)) }
                }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.ResetMix) }, enabled = mix != null && !mutating, modifier = Modifier.semantics {
                    testTag = WorkspacePageTags.MIX_RESET
                    contentDescription = if (mix == null) "Reset mix unavailable. Render stems first." else "Reset all logical channel settings to engine defaults."
                }) { Text("Reset engine defaults") }
            }
            }, second = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                MixMasterModeSelector(mode) { mode = it }
                OverviewCard("mix-master-listen", "Listen") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                        MixPlaybackSourceButton("Dry", PlaybackSource.DRY, currentSource, state, WorkspacePageTags.MIX_PLAYBACK_DRY, onIntent, Modifier.weight(1f))
                        MixPlaybackSourceButton("Lo-fi", PlaybackSource.LOFI, currentSource, state, WorkspacePageTags.MIX_PLAYBACK_LOFI, onIntent, Modifier.weight(1f))
                        MixPlaybackSourceButton("Master", PlaybackSource.MASTER, currentSource, state, WorkspacePageTags.MIX_PLAYBACK_MASTER, onIntent, Modifier.weight(1f))
                    }
                    Text("Master volume · ${(state.playbackSession.volume * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = state.playbackSession.volume.toFloat(), onValueChange = { onIntent(WorkspaceIntent.SetPlaybackVolume(it.toDouble())) }, valueRange = 0f..1f, enabled = !mutating,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.MIX_MASTER_VOLUME; contentDescription = "Master playback volume ${(state.playbackSession.volume * 100).toInt()} percent" })
                }
                OverviewCard("mix-master-effects", if (mode == MixMasterMode.MIX) "Mix effects" else "Mastering") {
                    Text("Lo-fi audio texture", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.buildOptions.loFi, onCheckedChange = { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFi = it))) }, enabled = !mutating,
                            modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_LOFI; contentDescription = "Apply the fixed supported Lo-fi audio texture during Build Song." })
                        Text("Fixed Bedroom LoFi preset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.semantics {
                        testTag = WorkspacePageTags.MIX_UNSUPPORTED_DSP
                        contentDescription = "Custom mastering DSP controls are unavailable because Melotrail accepts only the validated fixed mastering chain."
                    }) { Text("Custom DSP unavailable") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.buildOptions.mp3, onCheckedChange = { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(mp3 = it))) }, enabled = !mutating,
                            modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_MP3; contentDescription = "Request optional final MP3 export after the authoritative master WAV." })
                        Text("Optional final MP3 export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OverviewCard(WorkspacePageTags.MIX_BUILD_STATUS, "Render / Build") {
                    Text(buildMessage, style = MaterialTheme.typography.bodySmall, color = if (buildReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    (state.operation as? WorkspaceOperation.BuildingSong)?.progress?.let { Text("Stage ${it.stageIndex} of ${it.stageCount}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { onIntent(WorkspaceIntent.BuildSong) }, enabled = buildReady, modifier = Modifier.fillMaxWidth().semantics {
                        testTag = WorkspacePageTags.MIX_PRIMARY_ACTION
                        contentDescription = if (buildReady) "Build Song using validated current artifacts." else "Build Song unavailable. $buildMessage"
                    }) { Text(if (state.operation is WorkspaceOperation.BuildingSong) "Building Song…" else "Build Song") }
                }
            }
            })
        }
        ZeroSignalPlaceholder()
    }
}

@Composable
private fun MixMasterChannel(name: String, setting: app.melotrail.application.LogicalMixSetting, available: Boolean, enabled: Boolean, onSetting: (app.melotrail.application.LogicalMixSetting) -> Unit) =
    OverviewCard(WorkspacePageTags.MIX_CHANNEL_PREFIX + name, name.replaceFirstChar(Char::uppercase)) {
        val unavailableReason = "${name.replaceFirstChar(Char::uppercase)} stem is unavailable. Render the approved arrangement first."
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (available) "Rendered stem" else "Stem unavailable", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            Text("${"%.1f".format(setting.gainDb)} dB", style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = setting.gainDb.toFloat(), onValueChange = { onSetting(setting.copy(gainDb = it.toDouble())) }, valueRange = -24f..12f, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.MIX_GAIN_PREFIX + name; contentDescription = if (enabled) "$name gain ${"%.1f".format(setting.gainDb)} decibels" else unavailableReason })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onSetting(setting.copy(muted = !setting.muted)) }, enabled = enabled, modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_MUTE_PREFIX + name; contentDescription = if (enabled) "${if (setting.muted) "Unmute" else "Mute"} $name" else unavailableReason }) { Text(if (setting.muted) "Unmute" else "Mute") }
            OutlinedButton(onClick = { onSetting(setting.copy(solo = !setting.solo)) }, enabled = enabled, modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_SOLO_PREFIX + name; contentDescription = if (enabled) "${if (setting.solo) "Unsolo" else "Solo"} $name" else unavailableReason }) { Text(if (setting.solo) "Unsolo" else "Solo") }
            Text("Pan ${"%.2f".format(setting.pan)}", style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = setting.pan.toFloat(), onValueChange = { onSetting(setting.copy(pan = it.toDouble())) }, valueRange = -1f..1f, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.MIX_PAN_PREFIX + name; contentDescription = if (enabled) "$name pan ${"%.2f".format(setting.pan)}" else unavailableReason })
        Text("0.0 dBFS · level unavailable", style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Disabled, modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_METER_PREFIX + name; contentDescription = "Zero signal placeholder. No measured level is available for $name." })
    }

@Composable
private fun MixMasterModeSelector(selected: MixMasterMode, onSelected: (MixMasterMode) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    listOf(MixMasterMode.LISTEN, MixMasterMode.MIX, MixMasterMode.MASTER).forEach { mode ->
        OutlinedButton(onClick = { onSelected(mode) }, modifier = Modifier.weight(1f).semantics {
            testTag = when (mode) { MixMasterMode.LISTEN -> WorkspacePageTags.MIX_MODE_LISTEN; MixMasterMode.MIX -> WorkspacePageTags.MIX_MODE_MIX; MixMasterMode.MASTER -> WorkspacePageTags.MIX_MODE_MASTER }
            contentDescription = "${mode.name.lowercase().replaceFirstChar(Char::uppercase)} mode${if (selected == mode) ", selected" else ""}"
        }) { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
    }
}

@Composable
private fun MixPlaybackSourceButton(label: String, source: PlaybackSource, selected: PlaybackSource, state: WorkspaceUiState, tag: String, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) {
    val available = !state.downstreamArtifactsStale && when (source) {
        PlaybackSource.DRY -> state.project?.readiness?.dryMixAvailable == true
        PlaybackSource.LOFI -> state.project?.readiness?.loFiMixAvailable == true
        PlaybackSource.MASTER -> state.project?.readiness?.masterAvailable == true
    }
    OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(source)) }, enabled = available && !state.operation.isMutating, modifier = modifier.semantics {
        testTag = tag
        contentDescription = if (available) "$label playback source${if (selected == source) ", selected" else ""}" else "$label playback is unavailable or stale. Build a current artifact first."
    }) { Text(label) }
}

@Composable
private fun ZeroSignalPlaceholder() = OverviewCard(WorkspacePageTags.MIX_ZERO_SIGNAL, "Levels") {
    Text("0.0 dBFS · No measured signal", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Disabled)
}

private fun mixMasterCanBuild(state: WorkspaceUiState): Boolean = state.project != null && !state.downstreamArtifactsStale && !state.operation.isMutating && state.arrangement?.approved == true && state.arrangement.approvalRequired == false && state.arrangement.stale == false && state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available == true

private fun mixMasterBuildMessage(state: WorkspaceUiState): String = when {
    state.project == null -> "Open a project before building."
    state.arrangement == null -> "Build Song needs a current approved arrangement."
    state.arrangement.stale -> "The arrangement is stale; regenerate it before building."
    state.downstreamArtifactsStale -> "Mix artifacts are stale; regenerate them from the current arrangement before building."
    state.arrangement.approvalRequired || !state.arrangement.approved -> "Approve the reviewed arrangement before building."
    state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available != true -> state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.reason ?: "Checking local build readiness."
    else -> "Build validates artifacts, publishes a lossless master WAV atomically, then optionally exports MP3."
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
