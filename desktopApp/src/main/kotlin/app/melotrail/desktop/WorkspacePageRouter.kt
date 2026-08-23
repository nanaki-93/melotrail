package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.LocalSoundLibraryInstrument
import app.melotrail.application.PartSourceType
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.ReleaseExportFormat
import app.melotrail.application.StructureSectionSummary
import app.melotrail.application.WorkflowStage
import app.melotrail.application.filtered
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.ArrangementRole
import app.melotrail.arrangement.EnsembleCohesionEnhancementIntensity
import app.melotrail.arrangement.SoundTrait
import java.net.URI
import java.nio.file.Path

internal object WorkspacePageTags {
    const val ROOT_PREFIX = "workspace-page-"
    const val OVERVIEW_SECTION_STRIP = "overview-section-strip"
    const val OVERVIEW_TRACKS = "overview-track-overview"
    const val OVERVIEW_PREVIEW = "overview-video-preview"
    const val OVERVIEW_SECTION_INFO = "overview-section-info"
    const val OVERVIEW_SUMMARY = "overview-summary"
    const val OVERVIEW_SUMMARY_PREFIX = "overview-summary-"
    const val OVERVIEW_SECTION_PREFIX = "overview-section-"
    const val OVERVIEW_PROJECT_INFO = "overview-project-info"
    const val OVERVIEW_ARTIFACTS = "overview-artifact-status"
    const val OVERVIEW_ACTIVITY = "overview-current-activity"
    const val OVERVIEW_QUICK_ACTIONS = "overview-quick-actions"
    const val OVERVIEW_QUICK_ACTION_PREFIX = "overview-quick-action-"
    const val OVERVIEW_PRIMARY_ACTION = "overview-primary-action"
    const val OVERVIEW_MORE_ACTIONS_TOGGLE = "overview-more-actions-toggle"
    const val OVERVIEW_MORE_ACTIONS = "overview-more-actions"
    /** Compatibility tag for the Overview export quick action. */
    const val OVERVIEW_EXPORT = "overview-quick-action-export"
    const val SETUP_FORM = "setup-form"
    const val SETUP_LOADING = "setup-loading"
    const val SETUP_ERROR = "setup-error"
    const val SETUP_NAME = "setup-name"
    const val SETUP_TONIC = "setup-tonic"
    const val SETUP_MODE = "setup-mode"
    const val SETUP_TEMPO = "setup-tempo"
    const val SETUP_METER = "setup-meter"
    const val SETUP_PROFILE = "setup-profile"
    const val SETUP_MOOD = "setup-mood"
    const val SETUP_RECOMMENDATION = "setup-recommendation"
    const val SETUP_INVALIDATION = "setup-invalidation"
    const val SETUP_SAVE = "setup-save"
    const val SETUP_CONFIRM = "setup-confirm"
    const val HARMONY_ROOT = "harmony-root"
    const val VIDEO_PREVIEW_STAGE = "video-preview-stage"
    const val VIDEO_PREVIEW_TIMELINE = "video-preview-timeline"
    const val VIDEO_PREVIEW_OCCURRENCE_PREFIX = "video-preview-occurrence-"
    const val VIDEO_PREVIEW_STATUS = "video-preview-status"
    const val IMPORT_DROP_SURFACE = "import-drop-surface"
    const val IMPORT_BROWSE = "import-browse"
    const val IMPORT_WORKFLOW_HELP = "import-workflow-help"
    const val IMPORT_MIDI_HELP = "import-midi-help"
    const val IMPORT_AUDIO_CHOOSER = "import-audio-chooser"
    const val IMPORT_MIDI_CHOOSER = "import-midi-chooser"
    const val IMPORT_HELP = "import-help"
    const val IMPORT_TABLE_HEADER = "imported-files-header"
    const val IMPORT_SELECTION = "import-selected-part"
    const val IMPORT_CONTEXT = "import-context-rail"
    const val IMPORT_CONTEXT_ACTION = "import-context-action"
    const val IMPORT_LOFI_MIDI_PROCESSOR = "import-lofi-midi-processor"
    const val IMPORT_LOFI_AUDIO_PROCESSOR = "import-lofi-audio-processor"
    const val IMPORT_AI_FIX = "import-ai-fix"
    const val IMPORT_AI_FIX_CREATE = "import-ai-fix-create"
    const val IMPORT_AI_FIX_KEEP = "import-ai-fix-keep-cleaned"
    const val IMPORT_AI_FIX_APPROVE = "import-ai-fix-approve"
    const val IMPORT_AI_FIX_REJECT = "import-ai-fix-reject"
    const val IMPORT_AI_FIX_REGENERATE = "import-ai-fix-regenerate"
    const val IMPORT_AI_FIX_PREVIEW_CLEANED = "import-ai-fix-preview-cleaned"
    const val IMPORT_AI_FIX_PREVIEW_DRAFT = "import-ai-fix-preview-draft"
    const val IMPORT_AI_FIX_DIFF = "import-ai-fix-diff"
    const val IMPORT_MIDI_FEEL = "import-midi-feel"
    const val IMPORT_MIDI_FEEL_KEEP = "import-midi-feel-keep"
    const val IMPORT_MIDI_FEEL_APPLY = "import-midi-feel-apply"
    const val IMPORT_MIDI_FEEL_PREVIEW_BASE = "import-midi-feel-preview-base"
    const val IMPORT_MIDI_FEEL_PREVIEW_LOFI = "import-midi-feel-preview-lofi"
    const val IMPORTED_FILES = "imported-files"
    const val IMPORTED_ROW_PREFIX = "imported-file-"
    const val IMPORTED_DETAILS_PREFIX = "imported-details-"
    const val IMPORTED_PREVIEW_PREFIX = "imported-preview-"
    const val IMPORT_PRIMARY_ACTION = "import-primary-action"
    const val STRUCTURE_PALETTE = "structure-palette"
    const val STRUCTURE_ADD_PREFIX = "structure-add-"
    const val STRUCTURE_STRIP = "structure-strip"
    const val STRUCTURE_TABLE = "structure-table"
    const val STRUCTURE_ROW_PREFIX = "structure-row-"
    const val STRUCTURE_EDIT_PREFIX = "structure-edit-"
    const val STRUCTURE_DUPLICATE_PREFIX = "structure-duplicate-"
    const val STRUCTURE_REMOVE_PREFIX = "structure-remove-"
    const val STRUCTURE_CONTEXT = "structure-context-rail"
    const val STRUCTURE_SUMMARY = "structure-song-summary"
    const val STRUCTURE_PREVIEW = "structure-preview"
    const val STRUCTURE_HELP = "structure-help"
    const val STRUCTURE_OPTIONS_TOGGLE = "structure-options-toggle"
    const val STRUCTURE_OPTIONS = "structure-options"
    const val SOURCE_SONG_REVIEW = "source-song-review"
    const val SOURCE_SONG_GENERATE = "source-song-generate"
    const val SOURCE_SONG_PREVIEW = "source-song-preview"
    const val SOURCE_SONG_APPROVE = "source-song-approve"
    const val SOURCE_SONG_BOUNDARY_PREFIX = "source-song-boundary-"
    const val SOURCE_SONG_ISSUE_PREFIX = "source-song-issue-"
    const val ARRANGE_PLANNER_PREFIX = "arrange-planner-"
    const val ARRANGE_INSTRUMENT_PREFIX = "arrange-instrument-"
    const val ARRANGE_ROLE_PREFIX = "arrange-role-"
    const val ARRANGE_TRAIT_PREFIX = "arrange-trait-"
    const val ARRANGE_STYLE = "arrange-style"
    const val ARRANGE_INTENSITY = "arrange-intensity"
    const val ARRANGE_PRIMARY_ACTION = "arrange-primary-action"
    const val ARRANGE_PREREQUISITE = "arrange-prerequisite"
    const val ARRANGE_COHESION_ACTION = "arrange-cohesion-action"
    const val ARRANGE_COHESION_REVIEW = "arrange-cohesion-review"
    const val ARRANGE_COHESION_BOUNDARY_PREFIX = "arrange-cohesion-boundary-"
    const val ARRANGE_DENSITY_BUDGET = "arrange-density-budget"
    const val ARRANGE_CRITIC = "arrange-full-song-critic"
    const val ARRANGE_CRITIC_ISSUE_PREFIX = "arrange-critic-issue-"
    const val ARRANGE_TARGETED_FIX = "arrange-targeted-fix"
    const val ARRANGE_FINAL_MIDI = "arrange-final-midi"
    const val ARRANGE_CRITIC_FOCUS = "arrange-critic-focus"
    const val ARRANGE_DIAGNOSTICS_TOGGLE = "arrange-diagnostics-toggle"
    const val ARRANGE_DIAGNOSTICS = "arrange-diagnostics"
    const val ARRANGE_REVIEW = "arrange-review"
    const val ARRANGE_ENERGY = "arrange-energy-plan"
    const val ARRANGE_ROLE_PROGRESS = "arrange-role-progress"
    const val ARRANGE_ROLE_ACTION_PREFIX = "arrange-role-action-"
    const val ARRANGE_APPROVE = "arrange-approve"
    const val ARRANGE_TABS = "arrange-tabs"
    const val ARRANGE_TAB_PREFIX = "arrange-tab-"
    const val ARRANGE_TIMELINE = "arrange-timeline"
    const val ARRANGE_SECTION_PREFIX = "arrange-section-"
    const val ARRANGE_TRACK_PREFIX = "arrange-track-"
    const val ARRANGE_TRANSPORT = "arrange-transport"
    const val ARRANGE_TRANSPORT_SELECT = "arrange-transport-select"
    const val ARRANGE_TRANSPORT_PLAY = "arrange-transport-play"
    const val ARRANGE_CONTEXT = "arrange-context-rail"
    const val ARRANGE_SUMMARY = "arrange-summary"
    const val ARRANGE_OPTIONS_TOGGLE = "arrange-options-toggle"
    const val ARRANGE_OPTIONS = "arrange-options"
    const val MIX_CHANNEL_PREFIX = "mix-master-channel-"
    const val MIXER_VIEWPORT = "mix-master-viewport"
    const val MIX_EMPTY_CHANNELS = "mix-master-empty-channels"
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
    const val MIX_OPTIONS_TOGGLE = "mix-master-options-toggle"
    const val MIX_OPTIONS = "mix-master-options"
    const val EXPORT_FORMAT_PREFIX = "export-format-"
    const val EXPORT_AUDIO_ONLY = "export-audio-only"
    const val EXPORT_QUALITY = "export-quality"
    const val EXPORT_SAMPLE_RATE = "export-sample-rate"
    const val EXPORT_FILENAME = "export-filename"
    const val EXPORT_DESTINATION = "export-destination"
    const val EXPORT_BROWSE = "export-browse"
    const val EXPORT_SUMMARY = "export-summary"
    const val EXPORT_PREVIEW = "export-preview"
    const val EXPORT_ACTION = "export-action"
    const val EXPORT_COMMERCIAL_ACTION = "export-commercial-action"
    const val EXPORT_CREDITS_PREVIEW = "export-credits-preview"
    const val EXPORT_CREDITS_COPY = "export-credits-copy"
    const val EXPORT_STATUS = "export-status"
    const val EXPORT_RECOVERY = "export-recovery"
    const val EXPORT_OPTIONS_TOGGLE = "export-options-toggle"
    const val EXPORT_OPTIONS = "export-options"
    const val LIBRARY_TYPE_TAB = "library-type-instruments"
    const val LIBRARY_SEARCH = "library-search"
    const val LIBRARY_CATEGORY_PREFIX = "library-category-"
    const val LIBRARY_LAYOUT_GRID = "library-layout-grid"
    const val LIBRARY_LAYOUT_LIST = "library-layout-list"
    const val LIBRARY_GRID = "library-grid"
    const val LIBRARY_LIST = "library-list"
    const val LIBRARY_CARD_PREFIX = "library-card-"
    const val LIBRARY_DETAIL = "library-detail"
    const val LIBRARY_RECOVERY = "library-recovery"
    const val LIBRARY_REFRESH = "library-refresh"
    const val LIBRARY_SETTINGS = "library-settings"
    const val LIBRARY_OPTIONS_TOGGLE = "library-options-toggle"
    const val LIBRARY_OPTIONS = "library-options"
    const val SETTINGS_LIBRARY = "settings-library"
    const val SETTINGS_CHOOSE = "settings-choose-library"
    const val SETTINGS_CLEAR = "settings-clear-library"
    const val SETTINGS_REFRESH = "settings-refresh"
    const val SETTINGS_RUNTIME = "settings-runtime"
    const val SETTINGS_RUNTIME_PREFIX = "settings-runtime-"
    const val SETTINGS_ABOUT = "settings-about"
    const val SETTINGS_BACK = "settings-back"
    const val SETTINGS_CLEAR_CONFIRM = "settings-clear-confirm"
    const val SETTINGS_DETAILS_TOGGLE = "settings-details-toggle"
    const val SETTINGS_DETAILS = "settings-details"
    const val VIDEO_PREVIEW_OPTIONS_TOGGLE = "video-preview-options-toggle"
    const val VIDEO_PREVIEW_OPTIONS = "video-preview-options"
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
        PageForSection(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize(),
            narrow = maxWidth < MusicWorkspaceTokens.Reference.NarrowBreakpoint,
            partDetailsFocusTargets = partDetailsFocusTargets
        )
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
    if (state.workspaceSection == WorkspaceSection.OVERVIEW) OverviewPage(state, modifier, narrow)
    else InterimWorkflowPage(state, onIntent, modifier, partDetailsFocusTargets)
}

@Composable
private fun OverviewPage(state: WorkspaceUiState, modifier: Modifier, narrow: Boolean = false) = PageRoot(WorkspaceSection.OVERVIEW, modifier) {
    val project = state.project
    val sections = overviewSections(state)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                Text("PROJECT INFO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PageTitle(project?.name ?: "No project open", overviewMetadata(state))
                OverviewSummaryCards(state, sections)
                OverviewSectionStrip(sections)
                TrackOverview(state, sections)
            }
        }, second = { columnModifier ->
            Column(columnModifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                VideoPreviewPlaceholder(state)
                ProjectInfo(state)
                ArtifactStatus(state)
                OverviewActivity(state)
            }
        })
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
    val format = project.renderFormat?.let { "${it.sampleRate} Hz · ${it.channels} ch · ${it.bitDepth}-bit" } ?: "Render format unavailable"
    val total = project.structure.mapNotNull(StructureSectionSummary::durationSeconds)
    val duration = if (project.structure.isNotEmpty() && total.size == project.structure.size) formatDuration(total.sum()) else "Duration unavailable"
    return "$format · $duration"
}

private data class OverviewSection(val index: Int, val id: String, val duration: Double?, val instruments: Set<String>?)

private data class OverviewMetric(val id: String, val value: String, val label: String, val detail: String)

private fun overviewSections(state: WorkspaceUiState): List<OverviewSection> {
    val arrangement = state.arrangement
    if (arrangement != null && !arrangement.stale) return arrangement.sections.map(ArrangementSectionSnapshot::toOverviewSection)
    return state.project?.structure.orEmpty().map { section -> OverviewSection(section.index, section.instanceId, section.durationSeconds, null) }
}

private fun ArrangementSectionSnapshot.toOverviewSection() = OverviewSection(index, instanceId, durationSeconds, instruments.map { it.name }.toSet())

@Composable
private fun OverviewSummaryCards(state: WorkspaceUiState, sections: List<OverviewSection>) {
    val metrics = overviewMetrics(state, sections)
    Row(
        Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.OVERVIEW_SUMMARY }.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
    ) {
        metrics.forEach { metric ->
            Card(
                Modifier.width(132.dp).semantics { testTag = WorkspacePageTags.OVERVIEW_SUMMARY_PREFIX + metric.id },
                colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
            ) {
                Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Text(metric.value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(metric.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metric.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun overviewMetrics(state: WorkspaceUiState, sections: List<OverviewSection>): List<OverviewMetric> {
    val project = state.project
    val setup = state.projectSetup.saved
    val completeTiming = sections.isNotEmpty() && sections.all { it.duration != null }
    val arrangement = state.arrangement
    val trackCount = overviewTrackNames(arrangement).size
    return listOf(
        OverviewMetric("sections", if (project == null) "Unavailable" else sections.size.toString(), "Sections", if (sections.isEmpty()) "No saved structure" else sections.joinToString(" ") { it.id }),
        OverviewMetric("tracks", when { arrangement == null -> "Unavailable"; arrangement.stale -> "Stale"; else -> trackCount.toString() }, "Tracks", if (trackCount == 0) "Track availability unavailable" else "$trackCount logical tracks"),
        OverviewMetric("duration", if (completeTiming) formatDuration(sections.sumOf { checkNotNull(it.duration) }) else "Unavailable", "Duration", if (completeTiming) "Saved structure length" else "Timing unavailable"),
        OverviewMetric("tempo", setup?.tempo?.displayName ?: "Unavailable", "Tempo", if (setup == null) "Setup unavailable" else "Saved setup value"),
        OverviewMetric("key", setup?.key?.displayName ?: "Unavailable", "Key", if (setup == null) "Setup unavailable" else "Saved setup value")
    )
}

@Composable
private fun OverviewSectionStrip(sections: List<OverviewSection>) = OverviewCard(WorkspacePageTags.OVERVIEW_SECTION_STRIP, "Song structure") {
    if (sections.isEmpty()) {
        Text("Song sections unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        sections.forEach { section ->
            Column(
                Modifier.width(84.dp).clip(MaterialTheme.shapes.small)
                    .background(MusicWorkspaceTokens.ElevatedSurface)
                    .semantics { testTag = WorkspacePageTags.OVERVIEW_SECTION_PREFIX + section.id; contentDescription = "Section ${section.id}" }
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
    val tracks = overviewTrackNames(state.arrangement)
    if (tracks.isEmpty()) {
        Text("Track availability unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    tracks.forEach { lane ->
        Row(Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Pages.CompactRowHeight), verticalAlignment = Alignment.CenterVertically) {
            val laneStyle = instrumentLane(lane)
            Text("${laneStyle?.icon.orEmpty()} ${laneStyle?.label ?: lane.replaceFirstChar(Char::uppercase)}".trim(), modifier = Modifier.width(76.dp), color = laneStyle?.color ?: MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
            if (state.arrangement?.stale == true || sections.isEmpty()) {
                Text(if (state.arrangement?.stale == true) "Stale lane" else "Signal unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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

private fun overviewTrackNames(arrangement: app.melotrail.application.ArrangementSnapshot?): List<String> {
    val available = arrangement?.sections.orEmpty().flatMap { section -> section.instruments.map { it.name } }.toSet()
    return LogicalInstrument.entries.map(LogicalInstrument::wireName).filter(available::contains)
}

@Composable
private fun VideoPreviewPlaceholder(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.OVERVIEW_PREVIEW, "Video preview") {
    val message = overviewPreviewMessage(state)
    Box(Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.small).background(MusicWorkspaceTokens.ScenePlaceholder), contentAlignment = Alignment.Center) {
        Text(message, color = if (state.playbackSession.phase == PlaybackSessionPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun overviewPreviewMessage(state: WorkspaceUiState): String = when {
    state.playbackSession.phase == PlaybackSessionPhase.FAILED -> "Playback unavailable: ${state.playbackSession.failureMessage ?: "local playback failed"}"
    state.runtimeReadiness?.audioOutput?.available == false -> "Audio output unavailable: ${state.runtimeReadiness.audioOutput.detail}"
    state.playbackSession.phase in setOf(PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.STARTING) -> "Preview loading…"
    state.playbackSession.artifact != null -> "Local audio preview selected; video unavailable"
    else -> "Local video preview unavailable"
}

@Composable
private fun ProjectInfo(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.OVERVIEW_PROJECT_INFO, "Project info") {
    val format = state.project?.renderFormat
    val setup = state.projectSetup.saved
    val arrangement = state.arrangement
    val arrangementState = when {
        arrangement == null -> "unavailable"
        arrangement.stale -> "stale"
        arrangement.approvalRequired -> "review required"
        arrangement.approved -> "approved"
        else -> "available"
    }
    val releaseState = when {
        state.project?.readiness?.releaseAvailable == true -> "ready"
        state.project?.readiness?.masterAvailable == true -> "not released"
        else -> "unavailable"
    }
    Text("Sample rate: ${format?.sampleRate?.let { "$it Hz" } ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Channels: ${format?.channels?.let { "$it ch" } ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Bit depth: ${format?.bitDepth?.let { "$it-bit" } ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Key: ${setup?.key?.displayName ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Tempo: ${setup?.tempo?.displayName ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Time signature: ${setup?.timeSignature?.displayName ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Profile: ${setup?.profile?.id ?: "unavailable"} · Mood: ${setup?.mood?.id ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Arrangement: $arrangementState", style = MaterialTheme.typography.bodySmall)
    Text("Release: $releaseState", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ArtifactStatus(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.OVERVIEW_ARTIFACTS, "Artifact status") {
    if (state.project == null) return@OverviewCard Text("Project artifacts unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(
        "Setup" to WorkflowStage.PROJECT,
        "Clean MIDI" to WorkflowStage.CLEAN_MIDI,
        "Analysis" to WorkflowStage.ANALYSIS,
        "Structure" to WorkflowStage.STRUCTURE,
        "Arrangement" to WorkflowStage.ARRANGEMENT,
        "Stems" to WorkflowStage.RENDER,
        "Master" to WorkflowStage.MASTER,
        "Release" to WorkflowStage.COMMERCIAL_EXPORT
    ).forEach { (label, stage) ->
        val step = state.workflow[stage]
        val versions = step.artifactVersions.size
        Text("$label: ${workflowStatusLabel(step)}${if (versions > 0) " · $versions version(s)" else ""}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun OverviewActivity(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.OVERVIEW_ACTIVITY, "Current activity") {
    if (state.operation.isMutating) {
        Text("Loading", style = MaterialTheme.typography.titleMedium)
        Text(state.operationFeedback.message ?: "A local operation is in progress.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    val current = state.workflow.current
    Text("${current.stage.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)} · ${workflowStatusLabel(current)}", style = MaterialTheme.typography.titleMedium)
    Text(workflowDescription(current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OverviewCard(tag: String, title: String, content: @Composable () -> Unit) = Card(
    Modifier.fillMaxWidth().semantics { testTag = tag }, colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
) { Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    content()
} }

/** Keeps infrequent configuration and evidence available without competing with the next action. */
@Composable
private fun SecondaryOptions(toggleTag: String, panelTag: String, label: String = "More options", content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.semantics {
            testTag = toggleTag
            contentDescription = if (expanded) "Hide $label" else "Show $label"
        }
    ) { Text(if (expanded) "Hide options" else label) }
    if (expanded) Column(
        Modifier.fillMaxWidth().semantics { testTag = panelTag },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
    ) { content() }
}

@Composable
private fun InterimWorkflowPage(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    modifier: Modifier,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = PageRoot(state.workspaceSection, modifier) {
    if (state.workspaceSection == WorkspaceSection.SETUP) {
        ProjectSetupContent(state, onIntent)
        return@PageRoot
    }
    if (state.workspaceSection == WorkspaceSection.HARMONY) {
        HarmonyPage(state, onIntent)
        return@PageRoot
    }
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
    if (state.workspaceSection == WorkspaceSection.LIBRARY) {
        LibraryPage(state, onIntent)
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
    if (state.workspaceSection == WorkspaceSection.SETTINGS) {
        SettingsInterimPage(state, onIntent)
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

/**
 * Read-only projection of [WorkspaceUiState.libraryBrowser].  File and
 * registry work is deliberately absent from this composable.
 */
@Composable
private fun LibraryPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val browser = state.libraryBrowser
    val inventory = browser.inventory
    val categories = inventory.instruments.map(LocalSoundLibraryInstrument::category).distinct().sorted()
    val visible = inventory.filtered(browser.query, browser.category)
    val selected = visible.firstOrNull { it.id == browser.selectedId }
        ?: inventory.instruments.firstOrNull { it.id == browser.selectedId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
    ) {
        PageTitle("Library", "Validated local instruments and samples only")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Instruments",
                modifier = Modifier.semantics {
                    testTag = WorkspacePageTags.LIBRARY_TYPE_TAB
                    contentDescription = "Instruments is the only supported local library type"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.RefreshSoundLibrary) },
                modifier = Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_REFRESH; contentDescription = "Refresh validated local library inventory" }
            ) { Text("Refresh") }
        }
        SecondaryOptions(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE, WorkspacePageTags.LIBRARY_OPTIONS, "Search and filters") {
            OutlinedTextField(
                value = browser.query,
                onValueChange = { onIntent(WorkspaceIntent.UpdateLibrarySearch(it)) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.LIBRARY_SEARCH },
                label = { Text("Search local library") },
                singleLine = true
            )
        }
        if (inventory.instruments.isEmpty()) {
            LibraryRecovery(state, onIntent)
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            val content: @Composable (Modifier) -> Unit = { modifier ->
                LibraryResults(browser.layout, visible, browser.selectedId, onIntent, modifier)
            }
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    SecondaryOptions(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE + "-categories", WorkspacePageTags.LIBRARY_OPTIONS + "-categories", "Category filter") {
                        LibraryCategoryRail(categories, browser.category, onIntent, Modifier.fillMaxWidth())
                    }
                    content(Modifier.fillMaxWidth())
                    LibraryDetail(selected, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md), verticalAlignment = Alignment.Top) {
                    SecondaryOptions(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE + "-categories", WorkspacePageTags.LIBRARY_OPTIONS + "-categories", "Category filter") {
                        LibraryCategoryRail(categories, browser.category, onIntent, Modifier.width(148.dp))
                    }
                    content(Modifier.weight(1f))
                    LibraryDetail(selected, Modifier.widthIn(min = 220.dp, max = 272.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryRecovery(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.LIBRARY_RECOVERY, "Library readiness") {
    val message = state.libraryBrowser.refreshError ?: state.libraryBrowser.inventory.recoveryMessage
        ?: state.soundLibrary.validationError ?: "Choose a validated local sound library."
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("No catalog data is shown until the registry, SFZ files, and samples validate locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        OutlinedButton(
            onClick = { onIntent(WorkspaceIntent.OpenSettings) },
            modifier = Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_SETTINGS }
        ) { Text("Open Settings") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.RefreshSoundLibrary) }) { Text("Refresh") }
    }
}

@Composable
private fun LibraryCategoryRail(categories: List<String>, selected: String?, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) = Box(modifier) {
    OverviewCard("library-categories", "Categories") {
        LibraryCategoryButton("All", null, selected, onIntent)
        categories.forEach { category -> LibraryCategoryButton(category, category, selected, onIntent) }
    }
}

@Composable
private fun LibraryCategoryButton(label: String, category: String?, selected: String?, onIntent: (WorkspaceIntent) -> Unit) = OutlinedButton(
    onClick = { onIntent(WorkspaceIntent.SelectLibraryCategory(category)) },
    colors = workspaceSelectableButtonColors(category == selected),
    modifier = Modifier.fillMaxWidth().semantics {
        testTag = WorkspacePageTags.LIBRARY_CATEGORY_PREFIX + (category ?: "all").lowercase()
        contentDescription = "$label instrument category${if (category == selected) ", selected" else ""}"
    }
) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }

@Composable
private fun LibraryResults(layout: LibraryLayout, instruments: List<LocalSoundLibraryInstrument>, selectedId: String?, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically) {
        Text("${instruments.size} validated instrument${if (instruments.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        SecondaryOptions(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE + "-layout", WorkspacePageTags.LIBRARY_OPTIONS + "-layout", "Layout") {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.SelectLibraryLayout(LibraryLayout.GRID)) },
                    colors = workspaceSelectableButtonColors(layout == LibraryLayout.GRID),
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_LAYOUT_GRID }
                ) { Text("Grid") }
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.SelectLibraryLayout(LibraryLayout.LIST)) },
                    colors = workspaceSelectableButtonColors(layout == LibraryLayout.LIST),
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_LAYOUT_LIST }
                ) { Text("List") }
            }
        }
    }
    if (instruments.isEmpty()) {
        OverviewCard("library-empty", "No matching instruments") {
            Text("Adjust the local search or category filter. No remote catalog is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (layout == LibraryLayout.LIST) {
        Column(Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_LIST }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            instruments.forEach { LibraryInstrumentCard(it, it.id == selectedId, true, onIntent) }
        }
    } else {
        BoxWithConstraints(Modifier.semantics { testTag = WorkspacePageTags.LIBRARY_GRID }) {
            val columns = if (maxWidth >= 560.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                instruments.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                        row.forEach { instrument -> Box(Modifier.weight(1f)) { LibraryInstrumentCard(instrument, instrument.id == selectedId, false, onIntent) } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryInstrumentCard(instrument: LocalSoundLibraryInstrument, selected: Boolean, compact: Boolean, onIntent: (WorkspaceIntent) -> Unit) = Card(
    Modifier.fillMaxWidth().clickable { onIntent(WorkspaceIntent.SelectLibraryInstrument(instrument.id)) }
        .semantics { testTag = WorkspacePageTags.LIBRARY_CARD_PREFIX + instrument.id; contentDescription = "${instrument.name}, ${instrument.category}, ${instrument.sampleCount} validated samples${if (selected) ", selected" else ""}" },
    colors = CardDefaults.cardColors(containerColor = if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.Surface),
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) semanticColor(WorkspaceSemanticState.SELECTED) else MusicWorkspaceTokens.Border)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text(instrument.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${instrument.category} · SFZ ready · ${instrument.sampleCount} validated sample${if (instrument.sampleCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        if (!compact) Text(instrument.licenseName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryDetail(instrument: LocalSoundLibraryInstrument?, modifier: Modifier) = Box(modifier) {
    OverviewCard(WorkspacePageTags.LIBRARY_DETAIL, "Details") {
        if (instrument == null) {
            Text("Select a validated instrument to inspect its registry and license metadata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(instrument.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Category: ${instrument.category}\nSFZ: validated\nSamples: ${instrument.sampleCount} validated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MusicWorkspaceTokens.Border)
            Text("License: ${instrument.licenseName}\n${instrument.license}\nSource: ${instrument.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Text(if (instrument.commercialUse) "Commercial use recorded as permitted" else "Commercial use is not recorded as permitted", style = MaterialTheme.typography.bodySmall, color = if (instrument.commercialUse) semanticColor(WorkspaceSemanticState.READY) else semanticColor(WorkspaceSemanticState.WARNING))
            if (instrument.attributionRequired) Text("Attribution is required; inspect the project provenance before release.", style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.WARNING))
            Text("Preview is unavailable here. Configure a renderer and use a project MIDI preview when a validated artifact is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InterimDestinationPage(title: String, message: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle(title, "Local-only workspace destination")
        OverviewCard("${WorkspacePageTags.ROOT_PREFIX}${title.lowercase()}-body", "$title workspace") {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsInterimPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val settings = state.soundLibrary
    val selectionDisabled = settings.selectionDisabledReason != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageTitle("Settings", "Local preferences, runtime readiness, and build information")
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.BackFromSettings) },
                modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_BACK; contentDescription = "Return to the previous workspace destination" }
            ) { Text("Back") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            ResponsivePageColumns(narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint, first = { columnModifier ->
                Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    OverviewCard(WorkspacePageTags.SETTINGS_LIBRARY, "Sound library") {
                        Text(settings.resolvedRoot?.toString() ?: "No validated sound-library folder is configured.", maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("Source: ${settings.source ?: "none"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        settings.validationError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        settings.selectionDisabledReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.WARNING)) }
                        if (settings.restartRequired) Text("Restart the desktop app before renderer services use this validated library.", style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.WARNING))
                        Text("Choose the absolute folder containing instruments.json. Selection validates the full registry before the preference is saved; no project or audio data is stored here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                            Button(
                                enabled = !selectionDisabled && !state.operation.isMutating,
                                onClick = { onIntent(WorkspaceIntent.ChooseSoundLibraryRoot) },
                                modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_CHOOSE; contentDescription = "Choose and validate a local sound-library folder" }
                            ) { Text("Choose folder") }
                            OutlinedButton(
                                enabled = !selectionDisabled && settings.resolvedRoot != null && !state.operation.isMutating,
                                onClick = { onIntent(WorkspaceIntent.RequestClearSoundLibraryRoot) },
                                modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_CLEAR; contentDescription = "Clear the saved sound-library preference" }
                            ) { Text("Clear") }
                        }
                    }
                    SecondaryOptions(WorkspacePageTags.SETTINGS_DETAILS_TOGGLE, WorkspacePageTags.SETTINGS_DETAILS, "Runtime details") {
                        OverviewCard(WorkspacePageTags.SETTINGS_RUNTIME, "Local runtime readiness") {
                            if (state.runtimeReadiness == null) {
                                Text("Runtime readiness has not been checked yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                RuntimeDependency.entries.forEach { dependency -> SettingsDependencyRow(dependency, state.runtimeReadiness.dependency(dependency)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RefreshSoundLibrary) }, modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_REFRESH }) { Text("Refresh library") }
                                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RefreshRuntimeReadiness) }) { Text("Refresh readiness") }
                            }
                        }
                    }
                }
            }, second = { columnModifier ->
                Box(columnModifier) { SecondaryOptions(WorkspacePageTags.SETTINGS_DETAILS_TOGGLE + "-about", WorkspacePageTags.SETTINGS_DETAILS + "-about", "Build information") { OverviewCard(WorkspacePageTags.SETTINGS_ABOUT, "About this local build") {
                    val about = desktopAboutInfo()
                    Text("Melotrail", style = MaterialTheme.typography.titleMedium)
                    Text("Version: ${about.version}", style = MaterialTheme.typography.bodySmall)
                    Text("Platform: ${about.platform}", style = MaterialTheme.typography.bodySmall)
                    Text("Runtime: ${about.runtime}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MusicWorkspaceTokens.Border)
                    Text("Preferences retain only the last successfully opened project and validated sound-library root. Projects and audio remain in their selected canonical folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No telemetry, cloud sync, update checks, themes, device selection, backups, or model downloads are available in this build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } } }
            })
        }
    }
}

@Composable
private fun SettingsDependencyRow(dependency: RuntimeDependency, readiness: DependencyReadiness) {
    val title = dependency.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
    Text("$title · ${readiness.status.name.lowercase()} — ${readiness.detail}", style = MaterialTheme.typography.bodySmall,
        color = if (readiness.available) semanticColor(WorkspaceSemanticState.READY) else semanticColor(WorkspaceSemanticState.ERROR),
        modifier = Modifier.semantics { testTag = WorkspacePageTags.SETTINGS_RUNTIME_PREFIX + dependency.name.lowercase() })
    readiness.recoveryAction?.let { action ->
        Text("Recovery: ${settingsRecovery(action)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class DesktopAboutInfo(val version: String, val platform: String, val runtime: String)

private fun desktopAboutInfo(): DesktopAboutInfo = DesktopAboutInfo(
    version = WorkspaceUiState::class.java.`package`?.implementationVersion ?: System.getProperty("melotrail.version") ?: "development build",
    platform = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
    runtime = System.getProperty("java.runtime.version") ?: System.getProperty("java.version") ?: "unknown JVM"
)

private fun settingsRecovery(action: RecoveryAction): String = when (action) {
    RecoveryAction.START_WORKER -> "Start the Python worker with make worker."
    RecoveryAction.INSTALL_BASIC_PITCH -> "Install the optional Basic Pitch runtime with worker/requirements-transcription.txt in Python 3.11."
    RecoveryAction.CHOOSE_SOUND_LIBRARY -> "Choose a validated local sound-library folder."
    RecoveryAction.INSTALL_SAMPLES -> "Restore the missing catalog samples in the selected library."
    RecoveryAction.CONFIGURE_RENDERER -> "Set SFZ_RENDERER_PATH to an absolute executable sfizz_render path, then refresh readiness."
    RecoveryAction.CHECK_AUDIO_OUTPUT -> "Connect or enable a local audio output device, then refresh readiness."
}

@Composable
private fun ExportPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val export = state.export
    val draft = export.draft
    val inspection = export.inspection
    val summary = inspection?.summary
    val formats = inspection?.supportedFormats.orEmpty()
    val destinationIsProjectOutput = state.project?.root?.resolve("output")?.toAbsolutePath()?.normalize() == draft.destination?.toAbsolutePath()?.normalize()
    val canExport = inspection?.ready == true && draft.format in formats && destinationIsProjectOutput && !state.operation.isMutating
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Export", "Publish a validated audio release")
        OverviewCard(WorkspacePageTags.EXPORT_AUDIO_ONLY, "Release mode") {
            Text("Audio only", style = MaterialTheme.typography.titleMedium)
            Text("Video, audio-and-video, FLAC, metadata editing, stems, and cloud destinations are not available in this local release flow.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                OverviewCard("export-settings", "Release export") {
                    Button(onClick = { onIntent(WorkspaceIntent.ExportSong) }, enabled = canExport, colors = workspacePrimaryButtonColors(),
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_ACTION; contentDescription = if (canExport) "Export Song" else exportBlockedMessage(export, destinationIsProjectOutput) }) { Text(if (state.operation is WorkspaceOperation.ExportingRelease) "Exporting…" else "Export Song") }
                    Text(exportBlockedMessage(export, destinationIsProjectOutput), style = MaterialTheme.typography.bodySmall,
                        color = if (canExport) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.EXPORT_STATUS })
                    val evidence = state.commercialEvidence
                    val commercialReady = evidence?.commercialReady == true
                    OverviewCard(WorkspacePageTags.EXPORT_CREDITS_PREVIEW, "Commercial credits preview") {
                        val creditsName = draft.filename.substringBeforeLast('.', draft.filename).trim()
                            .replace(Regex("[^A-Za-z0-9_-]"), "-").replace(Regex("-+"), "-").trim('-')
                            .ifBlank { "song" } + "-credits.txt"
                        Text("$creditsName", style = MaterialTheme.typography.labelMedium)
                        if (evidence == null) Text("Create commercial evidence to validate and preview frozen instrument attribution.", style = MaterialTheme.typography.bodySmall)
                        else if (evidence.requiredAttribution.isEmpty()) Text("No instrument attribution required.", style = MaterialTheme.typography.bodySmall)
                        else evidence.requiredAttribution.sorted().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        evidence?.creditsReference?.let { Text("Generated: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (evidence?.creditsReference != null) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(evidence.requiredAttribution.sorted().joinToString("\n\n").ifBlank { "No instrument attribution required." }))
                            }, modifier = Modifier.semantics { testTag = WorkspacePageTags.EXPORT_CREDITS_COPY }) { Text("Copy credits") }
                        }
                    }
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.ExportCommercialSong) }, enabled = canExport && commercialReady,
                        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.EXPORT_COMMERCIAL_ACTION;
                            contentDescription = if (commercialReady) "Export commercially with hash-paired credits" else "Create commercial-ready evidence before commercial export" }) {
                        Text(if (state.operation is WorkspaceOperation.ExportingRelease) "Exporting…" else "Export commercially with credits")
                    }
                    if (summary == null) {
                        OutlinedButton(
                            onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.MIX_MASTER)) },
                            modifier = Modifier.semantics { testTag = WorkspacePageTags.EXPORT_RECOVERY; contentDescription = "Open Mix & Master to build a current master" }
                        ) { Text("Open Mix & Master") }
                    }
                    SecondaryOptions(WorkspacePageTags.EXPORT_OPTIONS_TOGGLE, WorkspacePageTags.EXPORT_OPTIONS, "Release options") {
                    Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                        formats.sortedBy(ReleaseExportFormat::name).forEach { format ->
                            OutlinedButton(
                                onClick = {
                                    val base = draft.filename.substringBeforeLast('.', draft.filename).ifBlank { "song" }
                                    onIntent(WorkspaceIntent.UpdateExportDraft(draft.copy(format = format, filename = "$base.${format.extension}")))
                                },
                                modifier = Modifier.weight(1f).semantics { testTag = WorkspacePageTags.EXPORT_FORMAT_PREFIX + format.name.lowercase(); contentDescription = "${format.name} export format${if (draft.format == format) ", selected" else ""}" }
                            ) { Text(format.name) }
                        }
                    }
                    Text("Format and quality", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(summary?.let { "WAV · PCM ${it.pcmBitDepth}-bit · ${it.channels} ch" } ?: "Unavailable", {}, readOnly = true, enabled = summary != null,
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
                    }
                    }
                }
            }, second = { columnModifier ->
            Column(columnModifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                OverviewCard(WorkspacePageTags.EXPORT_PREVIEW, "Audio preview") {
                    val selected = state.playbackSession.artifact != null
                    Text(
                        if (selected) "The shared transport below previews the selected local audio artifact."
                        else "Select a current mix or master in Mix & Master to preview it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("No video preview or export is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        CompactTransport(state, onIntent, Modifier.fillMaxWidth())
    }
}

private fun exportBlockedMessage(export: ExportUiState, destinationIsProjectOutput: Boolean = true): String = when {
    export.inspecting -> "Checking current release artifacts…"
    export.inspection?.blockedReason != null -> export.inspection.blockedReason ?: "Build a current master and release metadata first."
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
    val title = state.project?.name ?: "No project open"
    val status = videoPreviewStatus(state)
    val occurrences = overviewSections(state)
    // This is deliberately visual state: occurrence selection never changes
    // the canonical structure and playback remains owned by PlaybackSession.
    var selectedOccurrenceId by remember(occurrences.map(OverviewSection::id)) { mutableStateOf(occurrences.firstOrNull()?.id) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Video Preview", "Local visual audition for $title")
        OverviewCard(WorkspacePageTags.VIDEO_PREVIEW_STAGE, "Local visual placeholder") {
            Box(
                Modifier.fillMaxWidth().height(MusicWorkspaceTokens.Pages.VideoPreviewSceneHeight).clip(MaterialTheme.shapes.small)
                    .background(MusicWorkspaceTokens.ScenePlaceholder),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Text("LOCAL VISUAL PLACEHOLDER", style = MaterialTheme.typography.labelMedium, color = semanticColor(WorkspaceSemanticState.FOCUS))
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                    Text("Artwork and video rendering are unavailable; shared audio can still audition a selected artifact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(status, modifier = Modifier.semantics { testTag = WorkspacePageTags.VIDEO_PREVIEW_STATUS }, style = MaterialTheme.typography.bodySmall,
            color = if (session.phase == PlaybackSessionPhase.FAILED || state.runtimeReadiness?.audioOutput?.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        CompactTransport(state, onIntent, Modifier.fillMaxWidth())
        SecondaryOptions(WorkspacePageTags.VIDEO_PREVIEW_OPTIONS_TOGGLE, WorkspacePageTags.VIDEO_PREVIEW_OPTIONS, "Timeline evidence") {
            VideoPreviewTimeline(occurrences, selectedOccurrenceId) { selectedOccurrenceId = it }
        }
    }
}

@Composable
private fun VideoPreviewTimeline(
    occurrences: List<OverviewSection>,
    selectedOccurrenceId: String?,
    onSelect: (String) -> Unit
) = OverviewCard(WorkspacePageTags.VIDEO_PREVIEW_TIMELINE, "Canonical timeline") {
    if (occurrences.isEmpty()) {
        Text("No canonical section occurrences are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    val knownDuration = occurrences.mapNotNull(OverviewSection::duration).sum()
    val everyDurationKnown = occurrences.all { it.duration != null }
    Text(
        if (everyDurationKnown) "${occurrences.size} occurrence${if (occurrences.size == 1) "" else "s"} · ${formatDuration(knownDuration)} total"
        else "${occurrences.size} occurrence${if (occurrences.size == 1) "" else "s"} · duration unavailable",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        occurrences.forEach { occurrence ->
            val selected = occurrence.id == selectedOccurrenceId
            OutlinedButton(
                onClick = { onSelect(occurrence.id) },
                colors = workspaceSelectableButtonColors(selected),
                modifier = Modifier.widthIn(min = 112.dp, max = 180.dp).semantics {
                    testTag = WorkspacePageTags.VIDEO_PREVIEW_OCCURRENCE_PREFIX + occurrence.id
                    contentDescription = "Occurrence ${occurrence.id}, ${occurrence.duration?.let(::formatDuration) ?: "duration unavailable"}${if (selected) ", selected" else ""}"
                }
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(occurrence.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(occurrence.duration?.let(::formatDuration) ?: "Duration unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

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
            diagnostics = listOf("Missing: project", "Missing: canonical structure", "Missing: MIDI analyses")
        )
    val structureReady = project.readiness.structureReady && state.structureDraft.isNotEmpty()
    val missingAnalyses = state.structureDraft.toSet().filter { id ->
        project.parts.firstOrNull { it.id == id }?.analysis?.status != app.melotrail.application.PartAnalysisStatus.MIDI
    }
    val analysesReady = project.readiness.analysesReady && missingAnalyses.isEmpty()
    val diagnostics = listOf(
        if (structureReady) "Canonical structure is current." else "Missing: save a current canonical structure.",
        if (analysesReady) "MIDI analyses are current for every structure part." else "Missing: analyze ${missingAnalyses.ifEmpty { state.structureDraft.toSet() }.joinToString(", ")}."
    )
    val shortReason = when {
        !structureReady -> "Save a current structure before arranging."
        !analysesReady -> "Analyze every structure part before arranging."
        state.arrangement?.stale == true -> "The retained arrangement is stale; regenerate it."
        state.arrangement?.approvalRequired == true -> "Qwen draft needs explicit approval; generation can replace it."
        else -> "Structure and analyses are current."
    }
    return ArrangePrerequisites(shortReason, diagnostics)
}

@Composable
private fun ArrangePage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val prerequisites = arrangePrerequisites(state)
    val mutating = state.operation.isMutating
    val arrangementApproved = state.arrangement?.let { it.approved && !it.approvalRequired && !it.stale } == true
    val cohesionAction = if (!arrangementApproved || state.project?.readiness?.cohesionReady == true) null else when {
        state.cohesion?.stale == true -> "Retry Ensemble Cohesion"
        state.cohesion?.approvalRequired == true -> "Approve Ensemble Cohesion"
        else -> "Generate Ensemble Cohesion"
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
    ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                Column(Modifier.weight(1f)) { PageTitle("Arrange", "Create a bounded arrangement from saved structure and MIDI analyses") }
                Button(
                    onClick = { onIntent(WorkspaceIntent.GenerateArrangement) },
                    enabled = prerequisites.canGenerate && !mutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_PRIMARY_ACTION }
                ) { Text(if (mutating) "Generating…" else if (state.arrangement?.stale == true) "Regenerate Arrangement" else "Generate Arrangement") }
                cohesionAction?.let { label ->
                    OutlinedButton(
                        onClick = {
                            when (label) {
                                "Generate Ensemble Cohesion", "Retry Ensemble Cohesion" -> onIntent(WorkspaceIntent.GenerateCohesion)
                                "Approve Ensemble Cohesion" -> onIntent(WorkspaceIntent.ApproveCohesion)
                                else -> onIntent(WorkspaceIntent.SelectArrangeTab(ArrangeTab.TRANSITIONS))
                            }
                        },
                        enabled = !mutating,
                        modifier = Modifier.semantics {
                            testTag = WorkspacePageTags.ARRANGE_COHESION_ACTION
                            contentDescription = "$label after approved arrangement"
                        }
                    ) { Text(label) }
                }
            }
            SecondaryOptions(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE, WorkspacePageTags.ARRANGE_OPTIONS, "Arrangement options") {
                ArrangeTabs(state.arrangeTab, mutating, onIntent)
            }
        ArrangeTimeline(state, onIntent)
        ArrangeEnergyAndRoleProgress(state, onIntent)
        if (arrangementApproved) CohesionReview(state, onIntent)
        if (arrangementApproved) WholeSongReview(state, onIntent)
        ArrangeTransport(state, onIntent)
        ArrangeReview(state, onIntent)
        ArrangeSummary(state)
    }
}

/** Presents planner-derived energy and persisted role progress without reproducing musical decisions in Compose. */
@Composable
private fun ArrangeEnergyAndRoleProgress(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val arrangement = state.arrangement
    if (arrangement != null && !arrangement.stale) {
        OverviewCard(WorkspacePageTags.ARRANGE_ENERGY, "Energy, density & role plan") {
            Text("Planner-derived from saved structure, harmony, and MIDI analyses. Change those authoritative inputs to revise this plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            arrangement.sections.forEach { section ->
                val density = section.instruments.mapNotNull { it.density }.average().takeUnless { it.isNaN() }
                Text("${section.instanceId} · energy ${(section.energy * 100).toInt()}%${density?.let { " · density ${(it * 100).toInt()}%" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                section.instruments.filter { it.density != null || it.pattern != null || it.register != null }.forEach { instrument ->
                    Text("${instrument.name}: ${instrument.pattern ?: instrument.role ?: instrument.mode}${instrument.density?.let { " · ${(it * 100).toInt()}%" }.orEmpty()}${instrument.register?.let { " · ${it} register" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val workspace = state.arrangementWorkspace ?: return
    OverviewCard(WorkspacePageTags.ARRANGE_ROLE_PROGRESS, "Incremental role review") {
        Text("Core candidates are generated and validated as one dependency-safe batch. Optional layers remain locked until the exact core is approved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        workspace.roles.filter { it.active }.forEach { role ->
            val label = role.instrument.replaceFirstChar(Char::uppercase)
            val status = role.status.name.lowercase().replace('_', ' ')
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                Column(Modifier.weight(1f)) {
                    Text("$label · $status", style = MaterialTheme.typography.labelLarge)
                    Text(role.role ?: if (role.instrument == "piano") "protected source melody" else "logical role", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (role.status) {
                    app.melotrail.application.ArrangementRoleProgressStatus.READY_TO_GENERATE -> OutlinedButton(
                        onClick = { onIntent(WorkspaceIntent.GenerateCoreArrangementMidi) }, enabled = !state.operation.isMutating,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_ROLE_ACTION_PREFIX + role.instrument }
                    ) { Text("Generate & validate core") }
                    app.melotrail.application.ArrangementRoleProgressStatus.VALIDATED -> Button(
                        onClick = { onIntent(WorkspaceIntent.ApproveCoreArrangement) }, enabled = !state.operation.isMutating,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_ROLE_ACTION_PREFIX + role.instrument }
                    ) { Text("Accept core") }
                    app.melotrail.application.ArrangementRoleProgressStatus.LOCKED -> OutlinedButton(
                        onClick = {}, enabled = false,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_ROLE_ACTION_PREFIX + role.instrument; contentDescription = "$label is locked until core approval" }
                    ) { Text("Locked until core approval") }
                    app.melotrail.application.ArrangementRoleProgressStatus.ACCEPTED -> if (role.optional) OutlinedButton(
                        onClick = { onIntent(WorkspaceIntent.GenerateOptionalArrangementMidi) }, enabled = !state.operation.isMutating,
                        modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_ROLE_ACTION_PREFIX + role.instrument }
                    ) { Text("Regenerate optional") } else Text("Accepted", color = semanticColor(WorkspaceSemanticState.READY), style = MaterialTheme.typography.labelSmall)
                    app.melotrail.application.ArrangementRoleProgressStatus.SOURCE_READY -> Text("Protected source", color = semanticColor(WorkspaceSemanticState.READY), style = MaterialTheme.typography.labelSmall)
                    app.melotrail.application.ArrangementRoleProgressStatus.NOT_ACTIVE -> Unit
                }
            }
        }
        if (workspace.roles.any { it.active && it.optional && it.status == app.melotrail.application.ArrangementRoleProgressStatus.ACCEPTED }) {
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.GenerateOptionalArrangementMidi) }, enabled = !state.operation.isMutating) { Text("Generate optional layers") }
        }
        workspace.densityBudget?.let { budget ->
            HorizontalDivider()
            Column(Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_DENSITY_BUDGET }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Optional-layer density budget", style = MaterialTheme.typography.labelLarge)
                Text("Capacity ${budget.capacity} · core occupied ${budget.occupied} · remaining ${budget.remaining}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (budget.remaining == 0L) "Optional sustained layers are recommended OFF: the approved core fills the pitched-note budget."
                    else "Optional layers may use only the validated remaining pitched-note capacity.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Cohesion review is intentionally shown only after Arrangement approval. */
@Composable
private fun CohesionReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val cohesion = state.cohesion
    OverviewCard(WorkspacePageTags.ARRANGE_COHESION_REVIEW, "Ensemble Cohesion") {
        Text("Creates only adjacent-occurrence boundary bridges while preserving melody identity, structure, tempo, meter, and instrument roles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            EnsembleCohesionEnhancementIntensity.entries.forEach { intensity ->
                val selected = state.cohesionDraft.intensity == intensity
                if (selected) Button(
                    onClick = { onIntent(WorkspaceIntent.UpdateCohesionIntensity(intensity)) },
                    enabled = !state.operation.isMutating
                ) { Text(intensity.name.lowercase().replaceFirstChar(Char::uppercase)) }
                else OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.UpdateCohesionIntensity(intensity)) },
                    enabled = !state.operation.isMutating
                ) { Text(intensity.name.lowercase().replaceFirstChar(Char::uppercase)) }
            }
        }
        cohesion?.takeIf { it.stale }?.let {
            Text("Stale historical Ensemble Cohesion evidence · regenerate before approval or build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        cohesion?.takeIf { it.approved && !it.stale }?.let { approved ->
            Text("Current approved comparison evidence: ${approved.melodyEditCount} melody edits and ${approved.accompanimentEditCount} transition handoffs.", style = MaterialTheme.typography.bodySmall)
        }
        cohesion?.takeIf { it.approvalRequired && !it.approved && !it.stale }?.let { draft ->
            Text("Draft comparison evidence: ${draft.melodyEditCount} melody edits and ${draft.accompanimentEditCount} transition handoffs.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.PlayCohesionPreview(false)) },
                    enabled = !state.operation.isMutating && draft.baselinePreview != null
                ) { Text("Play baseline") }
                Button(
                    onClick = { onIntent(WorkspaceIntent.PlayCohesionPreview(true)) },
                    enabled = !state.operation.isMutating && draft.enhancedPreview != null
                ) { Text("Play enhanced") }
            }
            draft.boundaries.forEach { boundary ->
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.ReviewCohesionBoundary(boundary.outgoingInstanceId, boundary.incomingInstanceId)) },
                    enabled = !state.operation.isMutating,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.ARRANGE_COHESION_BOUNDARY_PREFIX + boundary.outgoingInstanceId + "-" + boundary.incomingInstanceId }
                ) { Text("${boundary.outgoingInstanceId} → ${boundary.incomingInstanceId}: ${if (boundary.reviewed) "reviewed" else "review boundary"}") }
                Text(boundary.rationale, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Approve or reject this boundary-only result after comparing the baseline and Ensemble Cohesion previews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RejectCohesion) }, enabled = !state.operation.isMutating) { Text("Reject draft") }
                Button(onClick = { onIntent(WorkspaceIntent.ApproveCohesion) }, enabled = !state.operation.isMutating && draft.boundaries.all { it.reviewed }) { Text("Approve Cohesion") }
            }
        }
        if (cohesion == null) Button(onClick = { onIntent(WorkspaceIntent.GenerateCohesion) }, enabled = !state.operation.isMutating) { Text("Generate Ensemble Cohesion") }
    }
}

/** Full-song review remains in Arrange: its actions are bounded by the current Critic evidence. */
@Composable
private fun WholeSongReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val critic = state.fullSongCritic
    val mutating = state.operation.isMutating
    OverviewCard(WorkspacePageTags.ARRANGE_CRITIC, "Whole-song Critic") {
        if (critic == null) {
            Text("Run Critic after approved Cohesion to review harmony, density, transitions, masking, groove, and recognizability.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onIntent(WorkspaceIntent.GenerateCritic) }, enabled = !mutating && state.project?.readiness?.cohesionReady == true) { Text("Run Critic") }
        } else {
            val recognizability = critic.report.aggregateMetrics.firstOrNull { it.name == "recognizabilityIssueCount" }?.value?.toInt() ?: 0
            Text("${critic.report.issues.size} finding(s) · recognizability ${if (recognizability == 0) "preserved" else "$recognizability regression finding(s)"}", style = MaterialTheme.typography.bodySmall)
            Text("Recognizability is checked before MIDI can advance to rendering.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            critic.report.warnings.forEach { warning -> Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            critic.report.advice?.observations?.forEach { observation -> Text(observation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (critic.report.issues.isEmpty()) Text("No deterministic Critic findings. Select final-MIDI bypass or no-op evidence before rendering.", style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.READY))
            critic.report.issues.forEach { issue ->
                val location = critic.issueLocations.firstOrNull { it.issueId == issue.id }
                val selected = state.focusedCriticIssueId == issue.id
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.FocusCriticIssue(issue.id)) },
                    enabled = !mutating,
                    modifier = Modifier.fillMaxWidth().semantics {
                        testTag = WorkspacePageTags.ARRANGE_CRITIC_ISSUE_PREFIX + issue.id
                        contentDescription = "${issue.category.name.lowercase().replace('_', ' ')} finding for ${location?.occurrenceId ?: "song"}, bars ${location?.startBar ?: issue.window.startBar} to ${location?.endBar ?: issue.window.endBar}${if (selected) ", focused" else ""}"
                    }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${issue.severity.name.lowercase().replaceFirstChar(Char::uppercase)} · ${issue.category.name.lowercase().replace('_', ' ')}")
                        Text("${location?.occurrenceId ?: "Song"} · bars ${location?.startBar ?: issue.window.startBar}–${location?.endBar ?: issue.window.endBar} · ${issue.targetRole}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (critic != null) TargetedFixAndFinalMidi(state, onIntent)
}

@Composable
private fun TargetedFixAndFinalMidi(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val enhancement = state.fullSongEnhancement
    val mutating = state.operation.isMutating
    OverviewCard(WorkspacePageTags.ARRANGE_TARGETED_FIX, "Targeted fix") {
        val description = enhancement?.let { snapshot ->
            when (snapshot.selection) {
                app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED -> "Targeted candidate approved: ${snapshot.addressedIssues} of ${snapshot.actionableIssues} actionable findings addressed."
                app.melotrail.arrangement.FullSongEnhancementSelection.NO_OP -> "Critic recorded a no-op; Cohesion MIDI remains selected."
                app.melotrail.arrangement.FullSongEnhancementSelection.BYPASS -> "Targeted fixes explicitly bypassed; Cohesion MIDI remains selected."
                app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED -> if (snapshot.candidateAvailable) "Candidate addresses ${snapshot.addressedIssues} of ${snapshot.actionableIssues} actionable findings. Review before approval." else "No candidate is available."
            }
        } ?: "Generate only a bounded candidate from current actionable Critic findings."
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        enhancement?.warnings?.forEach { warning -> Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.BypassFullSongEnhancement) }, enabled = !mutating) { Text("Bypass targeted fixes") }
            when {
                enhancement?.candidateAvailable == true -> Button(onClick = { onIntent(WorkspaceIntent.ApproveFullSongEnhancement) }, enabled = !mutating) { Text("Approve targeted candidate") }
                enhancement?.selection !in setOf(app.melotrail.arrangement.FullSongEnhancementSelection.NO_OP, app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED) -> Button(onClick = { onIntent(WorkspaceIntent.GenerateFullSongEnhancement) }, enabled = !mutating) { Text("Generate targeted candidate") }
            }
        }
    }
    OverviewCard(WorkspacePageTags.ARRANGE_FINAL_MIDI, "Final MIDI approval") {
        val selection = enhancement?.selection
        val approved = selection in setOf(
            app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED,
            app.melotrail.arrangement.FullSongEnhancementSelection.NO_OP,
            app.melotrail.arrangement.FullSongEnhancementSelection.BYPASS
        )
        Text(
            if (approved) "Final MIDI selection is explicit and current; rendering may use this approved evidence."
            else "Choose the bounded candidate, no-op, or bypass after reviewing the Critic before rendering.",
            style = MaterialTheme.typography.bodySmall,
            color = if (approved) semanticColor(WorkspaceSemanticState.READY) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArrangeTabs(selected: ArrangeTab, mutating: Boolean, onIntent: (WorkspaceIntent) -> Unit) = Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { testTag = WorkspacePageTags.ARRANGE_TABS },
    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
) {
    ArrangeTab.entries.forEach { tab ->
        val modifier = Modifier.semantics {
            testTag = WorkspacePageTags.ARRANGE_TAB_PREFIX + tab.name.lowercase()
            contentDescription = "${tab.label} arrange tab${if (tab == selected) ", selected" else ""}"
        }
        if (tab == selected) Button(onClick = { onIntent(WorkspaceIntent.SelectArrangeTab(tab)) }, enabled = !mutating, modifier = modifier) { Text(tab.label) }
        else OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectArrangeTab(tab)) }, enabled = !mutating, modifier = modifier) { Text(tab.label) }
    }
}

private data class ArrangeTimelineSection(
    val index: Int,
    val instanceId: String,
    val purpose: String?,
    val durationSeconds: Double?,
    val instruments: List<app.melotrail.application.ArrangementInstrumentSnapshot>,
    val transition: String?
)

private fun arrangeTimelineSections(state: WorkspaceUiState): List<ArrangeTimelineSection> = state.arrangement?.sections?.map { section ->
    ArrangeTimelineSection(section.index, section.instanceId, section.purpose, section.durationSeconds, section.instruments, section.transition)
} ?: state.project?.structure.orEmpty().map { section ->
    ArrangeTimelineSection(section.index, section.instanceId, null, section.durationSeconds, emptyList(), null)
}

private fun arrangeTimelineWidth(section: ArrangeTimelineSection) = ((section.durationSeconds?.coerceAtLeast(2.0) ?: 4.0) * 28.0).coerceIn(92.0, 210.0).dp

private fun arrangeSectionStartTimes(sections: List<ArrangeTimelineSection>): List<Double?> {
    var current = 0.0
    var known = true
    return sections.map { section ->
        val start = current.takeIf { known }
        val duration = section.durationSeconds
        if (duration == null || duration < 0.0) known = false else current += duration
        start
    }
}

private fun arrangeTrackColor(name: String) = instrumentLane(name)?.color ?: MusicWorkspaceTokens.Border

@Composable
private fun ArrangeTimeline(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.ARRANGE_TIMELINE, "Arrangement timeline") {
    val sections = arrangeTimelineSections(state)
    val startTimes = arrangeSectionStartTimes(sections)
    val tracks = state.arrangement?.sections.orEmpty().flatMap { it.instruments }.map { it.name }.distinct()
    state.focusedCriticIssueId?.let { issueId ->
        val issue = state.fullSongCritic?.report?.issues?.firstOrNull { it.id == issueId }
        val location = state.fullSongCritic?.issueLocations?.firstOrNull { it.issueId == issueId }
        if (issue != null) Text(
            "Critic focus: ${location?.occurrenceId ?: "song"}, bars ${location?.startBar ?: issue.window.startBar}–${location?.endBar ?: issue.window.endBar}.",
            modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_CRITIC_FOCUS },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.Primary
        )
    }
    if (sections.isEmpty()) {
        Text("No saved structure is available. Timeline timing and track lanes appear only from canonical structure and arrangement snapshots.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    Text(
        if (state.arrangement?.stale == true) "Stale arrangement placements are shown as inspectable evidence." else if (state.arrangement == null) "Structure timing is shown; generate an arrangement to reveal logical tracks." else "Logical placements only — this view does not invent notes, waveforms, or MIDI blocks.",
        style = MaterialTheme.typography.bodySmall,
        color = if (state.arrangement?.stale == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(760.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("SECTIONS", modifier = Modifier.width(112.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sections.forEachIndexed { position, section ->
                    val selected = section.index == state.selectedArrangementSection
                    Column(
                        Modifier.width(arrangeTimelineWidth(section)).padding(end = 2.dp).clip(MaterialTheme.shapes.small)
                            .background(if (selected) structureOccurrenceColor(StructureSectionSummary(section.index, section.instanceId, 1, section.instanceId, section.durationSeconds)).copy(alpha = 0.46f) else MusicWorkspaceTokens.ElevatedSurface)
                            .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }
                            .padding(MusicWorkspaceTokens.Spacing.Sm)
                            .semantics {
                                testTag = WorkspacePageTags.ARRANGE_SECTION_PREFIX + section.index
                                contentDescription = "${section.instanceId}${section.purpose?.let { " · $it" }.orEmpty()}${startTimes[position]?.let { " · starts ${formatDuration(it)}" }.orEmpty()}${section.durationSeconds?.let { " · duration ${formatDuration(it)}" }.orEmpty()}${if (selected) ", selected" else ""}"
                            },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(section.instanceId, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(section.purpose ?: "Saved structure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(startTimes[position]?.let { "Starts ${formatDuration(it)}" } ?: "Start unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (tracks.isEmpty()) Text("TRACKS  No canonical logical tracks are available yet.", modifier = Modifier.padding(top = MusicWorkspaceTokens.Spacing.Md), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tracks.forEach { track ->
                Row(Modifier.padding(top = MusicWorkspaceTokens.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                    val lane = instrumentLane(track)
                    Text("${lane?.icon.orEmpty()} ${lane?.label ?: track.replaceFirstChar(Char::uppercase)}".trim(), modifier = Modifier.width(112.dp).semantics { testTag = WorkspacePageTags.ARRANGE_TRACK_PREFIX + track }, style = MaterialTheme.typography.labelMedium, color = arrangeTrackColor(track))
                    sections.forEach { section ->
                        val placement = section.instruments.firstOrNull { it.name == track }
                        val selected = section.index == state.selectedArrangementSection
                        Box(
                            Modifier.width(arrangeTimelineWidth(section)).padding(end = 2.dp).height(38.dp).clip(MaterialTheme.shapes.small)
                                .background(if (placement == null) MusicWorkspaceTokens.ElevatedSurface else arrangeTrackColor(track).copy(alpha = if (selected) 0.55f else 0.30f))
                                .clickable(enabled = placement != null) { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }
                                .padding(horizontal = MusicWorkspaceTokens.Spacing.Xs)
                                .semantics {
                                    contentDescription = placement?.let { "$track in ${section.instanceId}: ${it.role ?: it.mode}" } ?: "$track is not planned in ${section.instanceId}"
                                },
                            contentAlignment = Alignment.CenterStart
                        ) { placement?.let { Text(it.role ?: it.mode, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrangeTransport(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.ARRANGE_TRANSPORT, "Shared transport") {
    val session = state.playbackSession
    val dryReady = state.project?.readiness?.dryMixAvailable == true && !state.downstreamArtifactsStale && state.arrangement?.stale != true
    val selected = session.artifact != null
    val playbackReady = selected && (state.runtimeReadiness?.audioOutput?.available == true || session.phase in setOf(PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED))
    Text(
        when {
            session.phase == PlaybackSessionPhase.FAILED -> "Playback unavailable: ${session.failureMessage ?: "local playback failed"}"
            selected -> "${formatDuration(session.positionSeconds)} / ${formatDuration(session.durationSeconds)} · shared local playback"
            else -> "No rendered mix selected. Arrangement generation itself does not render audio."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (session.phase == PlaybackSessionPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(PlaybackSource.DRY)) }, enabled = dryReady && !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_TRANSPORT_SELECT; contentDescription = if (dryReady) "Select current dry mix in the shared playback session" else "Render a current dry mix before playback" }) { Text("Select rendered mix") }
        Button(onClick = { onIntent(WorkspaceIntent.PlayPause) }, enabled = playbackReady, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_TRANSPORT_PLAY }) { Text(if (session.phase == PlaybackSessionPhase.PLAYING) "Pause" else "Play") }
        TextButton(onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = selected) { Text("Stop") }
    }
}

@Composable
internal fun ArrangeContextRail(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = Column(
    Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.ARRANGE_CONTEXT },
    verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
) {
    val prerequisites = arrangePrerequisites(state)
    Text(state.arrangeTab.label, style = MaterialTheme.typography.titleMedium)
    val mutating = state.operation.isMutating
    SecondaryOptions(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE + "-context", WorkspacePageTags.ARRANGE_OPTIONS + "-context", "Planner and instrument options") {
        when (state.arrangeTab) {
            ArrangeTab.ARRANGEMENT -> {
                Text("Select a canonical section in the timeline to inspect its actual purpose, instruments, and transition.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ArrangePlannerControls(state, onIntent)
                ArrangeInstrumentControls(state, onIntent)
            }
            ArrangeTab.INSTRUMENTS -> ArrangeInstrumentControls(state, onIntent)
            ArrangeTab.TRANSITIONS -> ArrangeTransitionEvidence(state)
            ArrangeTab.PLANNER -> ArrangePlannerControls(state, onIntent)
        }
    }
    var diagnosticsExpanded by remember(state.project, state.structureDraft, state.arrangement, state.operation) { mutableStateOf(false) }
    Text(prerequisites.shortReason, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_PREREQUISITE }, style = MaterialTheme.typography.bodySmall, color = if (prerequisites.canGenerate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
    TextButton(onClick = { diagnosticsExpanded = !diagnosticsExpanded }, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_DIAGNOSTICS_TOGGLE }) { Text(if (diagnosticsExpanded) "Hide details" else "Show details") }
    if (diagnosticsExpanded) Column(Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_DIAGNOSTICS }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        prerequisites.diagnostics.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (mutating) Text("Generation is in progress. The current draft and selection are retained.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArrangePlannerControls(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val draft = state.arrangementDraft
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        ArrangementPlannerKind.entries.forEach { planner -> PlannerChoiceCard(planner, draft.planner == planner, !state.operation.isMutating, { onIntent(WorkspaceIntent.UpdateArrangementPlanner(planner)) }, Modifier.weight(1f)) }
    }
    Text("Profile, mood, key, and meter come from saved Setup. The planner receives only controlled role and character requests.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Energy and density remain planner-derived from validated analyses; no manual intensity control is available.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArrangeInstrumentControls(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    Text("Role", style = MaterialTheme.typography.labelMedium)
    ArrangementRole.entries.forEach { role ->
        val selected = role in state.arrangementDraft.roles
        val required = role == ArrangementRole.MELODY
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(enabled = !state.operation.isMutating && !required) { onIntent(WorkspaceIntent.ToggleArrangementRole(role)) }.padding(vertical = MusicWorkspaceTokens.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = if (required || state.operation.isMutating) null else { _: Boolean -> onIntent(WorkspaceIntent.ToggleArrangementRole(role)) }, modifier = Modifier.semantics {
                testTag = WorkspacePageTags.ARRANGE_ROLE_PREFIX + role.name.lowercase()
                contentDescription = "${role.name.lowercase()} role ${if (required) "is required" else if (selected) "is selected" else "is not selected"} for arrangement generation"
            })
            Text(role.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Desired Character", style = MaterialTheme.typography.labelMedium)
    listOf(SoundTrait.SOFT, SoundTrait.WARM, SoundTrait.MUTED, SoundTrait.SUSTAINED, SoundTrait.BRUSHED).forEach { trait ->
        val selected = trait in state.arrangementDraft.attackTraits || trait in state.arrangementDraft.toneTraits || trait in state.arrangementDraft.articulationTraits
        Row(Modifier.fillMaxWidth().clickable(enabled = !state.operation.isMutating) { onIntent(WorkspaceIntent.ToggleArrangementTrait(trait)) }.padding(vertical = MusicWorkspaceTokens.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = if (state.operation.isMutating) null else { _: Boolean -> onIntent(WorkspaceIntent.ToggleArrangementTrait(trait)) }, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_TRAIT_PREFIX + trait.name.lowercase() })
            Text(trait.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Suggested / Pinned Instrument", style = MaterialTheme.typography.labelMedium)
    Text("Instrument resolution is a separate suggestion and user-choice step; this arrangement does not select files or engine settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("User ownership is retained only for a pinned stable instrument ID.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ArrangeTransitionEvidence(state: WorkspaceUiState) {
    val sections = state.arrangement?.sections.orEmpty()
    if (sections.isEmpty()) Text("Transitions become inspectable after a canonical arrangement exists. This page has no free-form transition editor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else sections.filter { it.transition.isNotBlank() && it.transition.lowercase() != "none" }.ifEmpty { listOf() }.let { transitioned ->
        if (transitioned.isEmpty()) Text("No planned transitions are recorded in the current arrangement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else transitioned.forEach { section -> Text("${section.instanceId} → ${section.transition}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ArrangeSummary(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.ARRANGE_SUMMARY, "Arrangement summary") {
    val sections = arrangeTimelineSections(state)
    val knownDurations = sections.mapNotNull(ArrangeTimelineSection::durationSeconds)
    val trackCount = state.arrangement?.sections.orEmpty().flatMap { it.instruments }.map { it.name }.distinct().size
    Text("${sections.size} section${if (sections.size == 1) "" else "s"} · ${if (knownDurations.size == sections.size && sections.isNotEmpty()) formatDuration(knownDurations.sum()) else "timing unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text(if (trackCount == 0) "No logical tracks generated" else "$trackCount logical track${if (trackCount == 1) "" else "s"} from arrangement evidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selection = state.selectedArrangementSection?.let { selected -> sections.firstOrNull { it.index == selected } }
    selection?.let { Text("Selected: ${it.instanceId}${it.purpose?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    colors = CardDefaults.cardColors(containerColor = if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.ElevatedSurface)
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
                Text("Validated Qwen draft — it is not approved or current.", color = semanticColor(WorkspaceSemanticState.WARNING))
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewArrangement) }, enabled = !state.operation.isMutating) { Text("Preview draft") }
                    Button(onClick = { onIntent(WorkspaceIntent.ApproveArrangement) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.ARRANGE_APPROVE }) { Text("Approve draft") }
                }
            }
            else -> Text("Approved arrangement is current.", color = semanticColor(WorkspaceSemanticState.READY))
        }
    }
}

@Composable
private fun StructurePage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val sections = state.project?.structure.orEmpty()
    val selected = sections.firstOrNull { it.instanceId == state.selectedStructureOccurrenceId } ?: sections.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
        ) {
            PageTitle("Structure", "Build and save the canonical order of your song")
            StructureAddArea(state, onIntent)
            StructureStrip(sections, selected?.instanceId, onIntent)
            SourceSongReview(state, onIntent)
            ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
                Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    SecondaryOptions(WorkspacePageTags.STRUCTURE_OPTIONS_TOGGLE, WorkspacePageTags.STRUCTURE_OPTIONS, "Choose a different prepared part") {
                        StructurePalette(state, onIntent)
                    }
                    StructureTable(state, selected?.instanceId, onIntent)
                }
            }, second = { columnModifier ->
                Column(columnModifier.widthIn(min = 260.dp, max = MusicWorkspaceTokens.Pages.OverviewPreviewWidth), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    StructurePreview(state, selected, onIntent)
                    StructureContextRail(state, selected)
                    StructureSongSummary(state)
                    StructureHelp(state)
                }
            })
        }
    }
}

/** Pre-arrangement review uses only the typed state published by source-song services. */
@Composable
private fun SourceSongReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(
    WorkspacePageTags.SOURCE_SONG_REVIEW,
    "Melody connection · solo source song"
) {
    val review = state.sourceSongReview
    val busy = state.operation.isMutating
    Text(
        "Connect the canonical structure before arrangement. The candidate remains separate from the selected source MIDI.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (review.connection == null || review.sourceSong == null) {
        Text("No connected source candidate yet. Generate it after saving at least two prepared sections.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onIntent(WorkspaceIntent.GenerateSourceSongConnections) },
            enabled = state.project?.structure?.size?.let { it >= 2 } == true && !busy,
            modifier = Modifier.semantics { testTag = WorkspacePageTags.SOURCE_SONG_GENERATE; contentDescription = "Generate source-song melody connections and critic report" }
        ) { Text("Generate connections") }
        review.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        return@OverviewCard
    }

    val sections = review.sourceSong.sections.associateBy { it.instance.instanceId }
    Text("Occurrence timeline", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        review.sourceSong.sections.forEach { section ->
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.SelectStructureOccurrence(section.instance.instanceId)) },
                colors = workspaceSelectableButtonColors(section.instance.instanceId == state.selectedStructureOccurrenceId)
            ) { Text("${section.sourcePartId}${section.occurrenceNumber} · ${section.sectionRole.value}") }
        }
    }
    Text("Boundary inspector", style = MaterialTheme.typography.labelLarge)
    review.connection.boundaries.forEach { boundary ->
        val outgoing = sections[boundary.decision.outgoingInstanceId]
        val incoming = sections[boundary.decision.incomingInstanceId]
        val report = boundary.report
        val outgoingChord = outgoing?.canonicalHarmony?.lastOrNull()?.let { it.rootSymbol + it.quality.symbolSuffix } ?: "—"
        val incomingChord = incoming?.canonicalHarmony?.firstOrNull()?.let { it.rootSymbol + it.quality.symbolSuffix } ?: "—"
        Card(
            Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.SOURCE_SONG_BOUNDARY_PREFIX + boundary.decision.boundaryId },
            colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
            border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
        ) {
            Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text("${outgoing?.sourcePartId ?: boundary.decision.outgoingInstanceId}${outgoing?.occurrenceNumber ?: ""} → ${incoming?.sourcePartId ?: boundary.decision.incomingInstanceId}${incoming?.occurrenceNumber ?: ""}", style = MaterialTheme.typography.titleSmall)
                Text("$outgoingChord → $incomingChord · ${boundary.decision.strategy.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${report.budget.changedNotes} changed note${if (report.budget.changedNotes == 1) "" else "s"} · budget ${report.budget.maximumChanges} · ${report.mutations.size} inspectable mutation${if (report.mutations.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.warnings.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.WARNING)) }
            }
        }
    }
    val report = review.critic?.report
    if (report != null) {
        Text("Source Song Critic · ${if (report.hasBlockingIssues) "blocking findings" else "ready for approval"}", style = MaterialTheme.typography.labelLarge,
            color = if (report.hasBlockingIssues) semanticColor(WorkspaceSemanticState.WARNING) else semanticColor(WorkspaceSemanticState.READY))
        if (report.issues.isEmpty()) Text("No critic findings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.issues.forEach { issue ->
            Text("${issue.severity.name} · ${issue.location.boundaryId}, bar ${issue.location.bar + 1}: ${issue.message}",
                modifier = Modifier.semantics { testTag = WorkspacePageTags.SOURCE_SONG_ISSUE_PREFIX + issue.id },
                style = MaterialTheme.typography.bodySmall,
                color = if (issue.severity.name == "BLOCKING") MaterialTheme.colorScheme.error else semanticColor(WorkspaceSemanticState.WARNING))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        OutlinedButton(
            onClick = { onIntent(WorkspaceIntent.PreviewConnectedSourceSong) }, enabled = !busy,
            modifier = Modifier.semantics { testTag = WorkspacePageTags.SOURCE_SONG_PREVIEW; contentDescription = "Preview connected solo source song as piano" }
        ) { Text("Preview solo source") }
        Button(
            onClick = { onIntent(WorkspaceIntent.RequestApproveSourceSong) }, enabled = review.critic != null && !review.approved && !busy,
            modifier = Modifier.semantics { testTag = WorkspacePageTags.SOURCE_SONG_APPROVE; contentDescription = "Approve current connected source song" }
        ) { Text(if (review.approved) "Source approved" else "Approve source song") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.GenerateSourceSongConnections) }, enabled = !busy) { Text("Regenerate") }
    }
    if (review.approved) Text("Approved current source-song candidate. Arrangement may now use this approval gate.", color = semanticColor(WorkspaceSemanticState.READY), style = MaterialTheme.typography.bodySmall)
    review.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun StructurePalette(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_PALETTE, "Eligible prepared parts") {
    val eligible = state.project?.parts.orEmpty().filter { primaryPartAction(it, state.pendingMidiFeel) is PartPrimaryAction.AddToStructure }
    if (eligible.isEmpty()) {
        Text("No part is ready to add. Finish the current repair and MIDI analysis steps first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Only parts with current canonical MIDI analysis can be added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        eligible.forEach { part ->
            OutlinedButton(
                onClick = { onIntent(WorkspaceIntent.AddStructurePart(part.id)) }, enabled = !state.operation.isMutating,
                modifier = Modifier.fillMaxWidth().semantics {
                    testTag = WorkspacePageTags.STRUCTURE_ADD_PREFIX + part.id
                    contentDescription = "Add prepared part ${part.id} to the canonical structure"
                }
            ) { Text("+ Add ${part.id}${part.role.takeIf(String::isNotBlank)?.let { " · $it" } ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun StructureStrip(sections: List<StructureSectionSummary>, selectedId: String?, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_STRIP, "Canonical occurrence order") {
    if (sections.isEmpty()) {
        Text("No saved sections yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        sections.forEach { section ->
            val selected = section.instanceId == selectedId
            Column(
                Modifier.width(94.dp).clip(MaterialTheme.shapes.small)
                    .background(if (selected) structureOccurrenceColor(section).copy(alpha = 0.42f) else structureOccurrenceColor(section).copy(alpha = 0.20f))
                    .clickable { onIntent(WorkspaceIntent.SelectStructureOccurrence(section.instanceId)) }
                    .padding(MusicWorkspaceTokens.Spacing.Sm).semantics {
                        testTag = WorkspaceTags.STRUCTURE_OCCURRENCE_PREFIX + section.instanceId
                        contentDescription = "Select canonical occurrence ${section.label} (${section.instanceId})${if (selected) ", selected" else ""}"
                    }
            ) {
                Text(section.label, fontWeight = FontWeight.SemiBold)
                Text("${section.partId} · ${section.durationSeconds?.let(::formatDuration) ?: "time unknown"}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StructureTable(state: WorkspaceUiState, selectedId: String?, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_TABLE, "Sections") {
    val sections = state.project?.structure.orEmpty()
    val mutating = state.operation.isMutating
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SECTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        selectedId?.let { id ->
            TextButton(onClick = { onIntent(WorkspaceIntent.DuplicateStructureOccurrence(id)) }, enabled = !mutating,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_DUPLICATE_PREFIX + id; contentDescription = "Duplicate $id" }) { Text("Duplicate") }
        }
        TextButton(onClick = { onIntent(WorkspaceIntent.ClearStructure) }, enabled = !mutating && sections.isNotEmpty(),
            modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_CLEAR; contentDescription = "Clear saved structure" }) { Text("Clear") }
    }
    if (sections.isEmpty()) {
        Text("Choose a prepared part to start", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@OverviewCard
    }
    StructureTableHeader()
    val starts = structureStartTimes(sections)
    sections.forEachIndexed { index, section ->
        val part = state.project?.parts?.firstOrNull { it.id == section.partId }
        val selected = section.instanceId == selectedId
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .background(if (selected) MusicWorkspaceTokens.SelectedSurface.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface)
                .semantics { testTag = WorkspacePageTags.STRUCTURE_ROW_PREFIX + section.instanceId; contentDescription = "${section.label} (${section.instanceId}), ${part?.role?.ifBlank { "role unknown" } ?: "part unavailable"}${if (selected) ", selected" else ""}" }
                .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Spacing.Xs)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text("${index + 1}", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall)
                Text(section.label, modifier = Modifier.width(42.dp), fontWeight = FontWeight.SemiBold)
                Text(starts[index]?.let(::formatDuration) ?: "—", modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall)
                Text(section.durationSeconds?.let(::formatDuration) ?: "—", modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall)
                Text(part?.analysis?.key ?: "Key unknown", modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("BPM —", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(part?.role?.ifBlank { "Role unknown" } ?: "Part unavailable", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onIntent(WorkspaceIntent.PreviewPart(section.partId)) }, enabled = !mutating,
                    modifier = Modifier.semantics { contentDescription = "Preview ${section.instanceId} with the shared playback session" }) { Text("Play") }
                TextButton(onClick = { onIntent(WorkspaceIntent.ShowRoleEditor(section.partId)) }, enabled = !mutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_EDIT_PREFIX + section.instanceId; contentDescription = "Edit shared role for ${section.instanceId}" }) { Text("Edit") }
                TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructureOccurrence(section.instanceId, earlier = true)) }, enabled = !mutating && index > 0,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_LEFT + section.instanceId; contentDescription = if (index > 0) "Move ${section.instanceId} earlier" else "${section.instanceId} is already first; it cannot move earlier" }) { Text("↑") }
                TextButton(onClick = { onIntent(WorkspaceIntent.MoveStructureOccurrence(section.instanceId, earlier = false)) }, enabled = !mutating && index < sections.lastIndex,
                    modifier = Modifier.semantics { testTag = WorkspaceTags.STRUCTURE_MOVE_RIGHT + section.instanceId; contentDescription = if (index < sections.lastIndex) "Move ${section.instanceId} later" else "${section.instanceId} is already last; it cannot move later" }) { Text("↓") }
                TextButton(onClick = { onIntent(WorkspaceIntent.RemoveStructureOccurrence(section.instanceId)) }, enabled = !mutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.STRUCTURE_REMOVE_PREFIX + section.instanceId; contentDescription = "Remove ${section.instanceId}" }) { Text("Delete") }
            }
        }
        if (index < sections.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun StructureTableHeader() = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    Text("#", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("SECTION", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("START", modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("DURATION", modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("KEY", modifier = Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("BPM", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StructureAddArea(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = OutlinedButton(
    onClick = { state.project?.parts?.firstOrNull { primaryPartAction(it, state.pendingMidiFeel) is PartPrimaryAction.AddToStructure }?.let { onIntent(WorkspaceIntent.AddStructurePart(it.id)) } },
    enabled = !state.operation.isMutating && state.project?.parts.orEmpty().any { primaryPartAction(it, state.pendingMidiFeel) is PartPrimaryAction.AddToStructure },
    modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Add the next eligible prepared part to the saved structure" }
) { Text("+ Add Section") }

@Composable
private fun StructurePreview(state: WorkspaceUiState, selected: StructureSectionSummary?, onIntent: (WorkspaceIntent) -> Unit) = OverviewCard(WorkspacePageTags.STRUCTURE_PREVIEW, "Preview") {
    Box(Modifier.fillMaxWidth().height(110.dp).clip(MaterialTheme.shapes.small).background(MusicWorkspaceTokens.ScenePlaceholder), contentAlignment = Alignment.Center) {
        Text(if (selected == null) "Select a section to preview" else "${selected.label} · local part preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val session = state.playbackSession
    val isSelectedPreview = (session.request as? PlaybackRequest.Part)?.partId == selected?.partId
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (session.phase == PlaybackSessionPhase.FAILED) "Unavailable: ${session.failureMessage ?: "local playback failed"}" else if (isSelectedPreview) "${formatDuration(session.positionSeconds)} / ${formatDuration(session.durationSeconds)}" else "No selected playback", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { selected?.let { onIntent(WorkspaceIntent.PreviewPart(it.partId)) } }, enabled = selected != null && !state.operation.isMutating) { Text("Play") }
        TextButton(onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = session.phase in setOf(PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.READY, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED)) { Text("Stop") }
    }
}

@Composable
private fun StructureContextRail(state: WorkspaceUiState, selected: StructureSectionSummary?) = OverviewCard(WorkspacePageTags.STRUCTURE_CONTEXT, "Selected section") {
    val part = selected?.let { section -> state.project?.parts?.firstOrNull { it.id == section.partId } }
    if (selected == null) Text("No canonical occurrence selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else {
        Text(selected.label, style = MaterialTheme.typography.titleLarge)
        Text(part?.role?.ifBlank { "Role unknown" } ?: "Part unavailable", fontWeight = FontWeight.Medium)
        StructureFact("Duration", selected.durationSeconds?.let(::formatDuration) ?: "Unknown")
        StructureFact("Bars", part?.analysis?.bars?.takeIf { it > 0 }?.toString() ?: "Unknown")
        StructureFact("Key", part?.analysis?.key ?: "Unknown")
        StructureFact("BPM", "Unknown")
        StructureFact("Time signature", "Unknown")
    }
}

@Composable
private fun StructureSongSummary(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.STRUCTURE_SUMMARY, "Song summary") {
    val sections = state.project?.structure.orEmpty()
    val durations = sections.mapNotNull(StructureSectionSummary::durationSeconds)
    val keys = sections.mapNotNull { section -> state.project?.parts?.firstOrNull { it.id == section.partId }?.analysis?.key }.distinct()
    StructureFact("Total duration", if (sections.isNotEmpty() && durations.size == sections.size) formatDuration(durations.sum()) else "Unknown")
    StructureFact("Total sections", sections.size.toString())
    StructureFact("Tempo", "Unknown")
    StructureFact("Key", when (keys.size) { 0 -> "Unknown"; 1 -> keys.single(); else -> "Mixed" })
    StructureFact("Time signature", "Unknown")
}

@Composable
private fun StructureFact(label: String, value: String) = Row(Modifier.fillMaxWidth()) {
    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StructureHelp(state: WorkspaceUiState) = OverviewCard(WorkspacePageTags.STRUCTURE_HELP, "Help and recovery") {
    when {
        state.operation is WorkspaceOperation.SavingStructure -> Text("Saving the canonical structure…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.operation is WorkspaceOperation.Failed && state.retry is WorkspaceRetry.SaveStructure -> Text("Structure save failed. Use the global Retry action; the last saved structure remains selected.", color = MaterialTheme.colorScheme.error)
        state.downstreamArtifactsStale -> Text("Structure changed. Existing plans and rendered artifacts are stale evidence; regenerate them when ready.", color = MaterialTheme.colorScheme.error)
        else -> Text("Use the earlier/later buttons to reorder without drag and drop. This page has no automatic structure suggestion.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun structureStartTimes(sections: List<StructureSectionSummary>): List<Double?> {
    var current = 0.0
    var known = true
    return sections.map { section ->
        val start = current.takeIf { known }
        val duration = section.durationSeconds
        if (duration == null || duration < 0.0) known = false else current += duration
        start
    }
}

private fun structureOccurrenceColor(section: StructureSectionSummary) = instrumentLanes.values.elementAt(
    kotlin.math.abs(section.partId.fold(0) { total, char -> total + char.code }) % instrumentLanes.size
).color

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportPage(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
) {
    PageTitle("Melody Parts", "Import and process each melody through a simple MIDI pipeline")
    ImportDropSurface(state, onIntent)
    MelodyPartsCards(state, onIntent, partDetailsFocusTargets)
}

@Composable
private fun MidiAiFixReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val part = importPrimaryPart(state) ?: return
    if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT) return
    val fix = state.midiAiFix?.takeIf { it.partId == part.id }
    val available = state.runtimeReadiness?.capability(RuntimeCapability.MIDI_PREVIEW)?.available == true
    OverviewCard(WorkspacePageTags.IMPORT_AI_FIX, "Optional AI-assisted track fix") {
        Text("Choose a base after Clean MIDI. The local model may propose only bounded note corrections; it cannot change the selected input until you approve its draft.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fix == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.ReturnToCleanedMidi) }, enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_KEEP }) { Text("Keep cleaned MIDI") }
                Button(onClick = { onIntent(WorkspaceIntent.CreateMidiAiFix) }, enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_CREATE }) { Text("Create AI fix") }
            }
        } else if (fix.noSafeFix) {
            Text("No safe AI correction was available. ${fix.noSafeFixReason ?: "Corrected MIDI remains selected."}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onIntent(WorkspaceIntent.RegenerateMidiAiFix) }, enabled = !state.operation.isMutating,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_REGENERATE }) { Text("Try AI Fix again") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, app.melotrail.application.PreviewMidiSource.CLEANED)) }, enabled = available,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_PREVIEW_CLEANED }) { Text("Preview cleaned") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, if (fix.approved) app.melotrail.application.PreviewMidiSource.AI_FIX_APPROVED else app.melotrail.application.PreviewMidiSource.AI_FIX_DRAFT)) }, enabled = available && (if (fix.approved) fix.approvedAvailable else fix.draftAvailable),
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_PREVIEW_DRAFT }) { Text("Preview AI fix") }
            }
            Text("Comparison evidence: ${fix.edits.size} bounded edit${if (fix.edits.size == 1) "" else "s"} · ${if (fix.approved) "current approved" else "draft review required"}", style = MaterialTheme.typography.bodySmall, color = if (fix.approved) semanticColor(WorkspaceSemanticState.READY) else semanticColor(WorkspaceSemanticState.WARNING))
            Column(Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_DIFF }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                fix.edits.forEachIndexed { index, edit -> Text("${index + 1}. ${edit.kind.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                if (!fix.approved) Button(onClick = { onIntent(WorkspaceIntent.ApproveMidiAiFix) }, enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_APPROVE }) { Text("Approve AI fix") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RejectMidiAiFix) }, enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_REJECT }) { Text(if (fix.approved) "Return to cleaned" else "Reject draft") }
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RegenerateMidiAiFix) }, enabled = !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_AI_FIX_REGENERATE }) { Text("Regenerate") }
            }
        }
    }
}

/** Per-part MIDI timing choice; post-mix Lo-fi audio texture remains a separate Mix action. */
@Composable
private fun MidiLoFiFeelReview(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val part = importPrimaryPart(state) ?: return
    if (part.preparation.midiQuality.status != MidiQualityStatus.CURRENT || !part.preparation.midiAiFix.selectedAvailable) return
    val available = state.runtimeReadiness?.capability(RuntimeCapability.MIDI_PREVIEW)?.available == true
    val feel = part.preparation.midiFeel
    val basePreview = if (part.preparation.midiAiFix.selected == app.melotrail.arrangement.MidiAiFixSelection.APPROVED) {
        app.melotrail.application.PreviewMidiSource.AI_FIX_APPROVED
    } else {
        app.melotrail.application.PreviewMidiSource.CLEANED
    }
    OverviewCard(WorkspacePageTags.IMPORT_MIDI_FEEL, "Per-track Lo-fi MIDI Feel") {
        Text("Part ${part.id} · timing-only MIDI transform (80 BPM, 58% swing). This is not the post-mix Lo-fi audio texture.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Current: ${if (feel.selected == app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL) "Lo-fi Feel" else "current feel"}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = {
                    onIntent(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.CURRENT))
                    if (feel.selected != app.melotrail.arrangement.MidiAnalysisInput.CURRENT) onIntent(WorkspaceIntent.ApplyMidiFeelAndReanalyze)
                },
                enabled = !state.operation.isMutating,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_MIDI_FEEL_KEEP }
            ) { Text("Keep current feel") }
            Button(
                onClick = {
                    onIntent(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL))
                    if (feel.selected != app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL) onIntent(WorkspaceIntent.ApplyMidiFeelAndReanalyze)
                },
                enabled = !state.operation.isMutating,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_MIDI_FEEL_APPLY }
            ) { Text("Apply Lo-fi Feel") }
        }
        if (feel.available) {
            Text("A/B preview is monitor-only and does not change either MIDI artifact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, basePreview)) },
                    enabled = available && !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_MIDI_FEEL_PREVIEW_BASE }
                ) { Text("Preview current feel") }
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.PreviewMidiPart(part.id, app.melotrail.application.PreviewMidiSource.LOFI_FEEL)) },
                    enabled = available && !state.operation.isMutating,
                    modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_MIDI_FEEL_PREVIEW_LOFI }
                ) { Text("Preview Lo-fi Feel") }
            }
        }
    }
    val enhancement = part.preparation.enhancement
    OverviewCard("enhancement-intensity", "Enhancement") {
        Text("${enhancement.capabilityLabel}. It requires the corrected baseline; its retained comparison evidence is hash-bound and review-only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Current: ${if (enhancement.selected == app.melotrail.arrangement.EnhancementSelection.CORRECTED) "Corrected" else enhancement.intensity.name.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            app.melotrail.arrangement.EnhancementIntensity.entries.forEach { intensity ->
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectEnhancement(intensity)) }, enabled = !state.operation.isMutating) {
                    Text(if (intensity == app.melotrail.arrangement.EnhancementIntensity.OFF) "Off" else intensity.name.lowercase().replaceFirstChar(Char::uppercase))
                }
            }
        }
        state.enhancementReview?.takeIf { it.partId == part.id }?.let { review ->
            Text("Comparison evidence: ${review.edits} validated edit${if (review.edits == 1) "" else "s"} · ${review.approval.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
            review.reasons.forEach { reason -> Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (review.approval == app.melotrail.arrangement.EnhancementApproval.DRAFT) {
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Button(onClick = { onIntent(WorkspaceIntent.ApproveEnhancement) }, enabled = !state.operation.isMutating) { Text("Approve enhancement") }
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.RejectEnhancement) }, enabled = !state.operation.isMutating) { Text("Reject draft") }
                }
            }
        }
    }
}

@Composable
private fun ImportHelpLinks() = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg)
) {
    Text(
        "Workflow guide · docs/TRACK_PROCESS_WORKFLOW.md",
        modifier = Modifier.weight(1f).semantics {
            testTag = WorkspacePageTags.IMPORT_WORKFLOW_HELP
            contentDescription = "Workflow guide: docs/TRACK_PROCESS_WORKFLOW.md"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        "MIDI guide · docs/MIDI_IMPORT_PROCESS.md",
        modifier = Modifier.weight(1f).semantics {
            testTag = WorkspacePageTags.IMPORT_MIDI_HELP
            contentDescription = "MIDI import guide: docs/MIDI_IMPORT_PROCESS.md"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportDropSurface(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val enabled = state.project != null && !state.operation.isMutating
    val dropTarget = remember(onIntent, enabled) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                if (!enabled) return false
                val path = droppedPartSource(event) ?: return false
                onIntent(WorkspaceIntent.ImportSourceChosen(path))
                return true
            }
        }
    }
    Card(
        Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Pages.ImportDropHeight)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> enabled && droppedPartSource(event) != null },
                target = dropTarget
            ).semantics {
                testTag = WorkspacePageTags.IMPORT_DROP_SURFACE
                contentDescription = if (enabled) "Drop one MIDI, WAV, WAVE, or MP3 file. The same validated import dialog is used for drops and browsing." else "Import unavailable. Create or open a project first."
            },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Pages.ContentInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
        ) {
            Text("⌑", style = MaterialTheme.typography.headlineMedium, color = MusicWorkspaceTokens.WarmAccent)
            Text("1. Select one source", fontWeight = FontWeight.Medium)
            Text("Drop one MIDI, WAV, WAVE, or MP3 file. Audio continues only through the eligible solo-piano transcription route.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onIntent(WorkspaceIntent.ShowAddPart) }, enabled = enabled,
                modifier = Modifier.semantics {
                    testTag = WorkspacePageTags.IMPORT_BROWSE
                    contentDescription = "Browse supported source files"
                }
            ) { Text("Browse source") }
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
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        val parts = state.project?.parts.orEmpty()
        Text("IMPORTED FILES (${parts.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (parts.isEmpty()) {
            Text("No files imported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ImportTableHeader()
            parts.forEachIndexed { index, part ->
                val focusReturn = PartDetailsFocusReturn.ImportedRow(part.id)
                val focusRequester = remember(part.id) { FocusRequester() }
                DisposableEffect(focusReturn, focusRequester) {
                    partDetailsFocusTargets[focusReturn] = focusRequester
                    onDispose { partDetailsFocusTargets.remove(focusReturn, focusRequester) }
                }
                val selected = state.selectedPartId == part.id
                val previewCapability = if (part.sourceType == PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
                val preview = state.runtimeReadiness?.capability(previewCapability)
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.ElevatedSurface)
                        .clickable(enabled = !state.operation.isMutating) { onIntent(WorkspaceIntent.SelectPart(part.id)) }
                        .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Spacing.Xs)
                        .semantics {
                            testTag = WorkspacePageTags.IMPORTED_ROW_PREFIX + part.id
                            contentDescription = "${part.sourceName}, ${part.sourceType.name.lowercase()}, ${state.partPreparationLabel(part.id)}${if (selected) ", selected" else ""}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
                ) {
                    Text(if (part.sourceType == PartSourceType.MIDI) "♫" else "⌁", color = MusicWorkspaceTokens.WarmAccent)
                    Column(Modifier.weight(1.4f)) {
                        Text(part.sourceName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatImportFileSize(part.sourceSizeBytes), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(part.sourceType.name, modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Column(Modifier.weight(0.62f)) {
                        Text(part.analysis?.key ?: "—", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("BPM unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(part.analysis?.durationSeconds?.let(::formatDuration) ?: "—", modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Column(Modifier.weight(0.78f)) {
                        Text(state.partPreparationLabel(part.id), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (preview?.available == true) "Preview ready" else "Preview unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    TextButton(
                        onClick = { onIntent(WorkspaceIntent.PreviewPart(part.id)) },
                        enabled = !state.operation.isMutating && preview?.available == true,
                        modifier = Modifier.semantics {
                            testTag = WorkspacePageTags.IMPORTED_PREVIEW_PREFIX + part.id
                            contentDescription = if (preview?.available == true) "Preview ${part.sourceName}" else "Preview unavailable. ${preview?.reason ?: "Checking local preview requirements."}"
                        }
                    ) { Text("▶") }
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
private fun ImportTableHeader() = Row(
    Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Sm).semantics { testTag = WorkspacePageTags.IMPORT_TABLE_HEADER },
    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
) {
    Spacer(Modifier.width(20.dp))
    Text("File name", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Type", modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Key / BPM", modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Duration", modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Status", modifier = Modifier.weight(0.78f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(56.dp))
}

@Composable
private fun ImportPrimaryAction(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) {
    val part = importPrimaryPart(state)
    val action = part?.let { primaryPartAction(it, state.pendingMidiFeel) }
    val focusReturn = PartDetailsFocusReturn.ImportPrimaryAction
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(focusRequester) {
        partDetailsFocusTargets[focusReturn] = focusRequester
        onDispose { partDetailsFocusTargets.remove(focusReturn, focusRequester) }
    }
    when {
        state.operation is WorkspaceOperation.Failed -> Unit // The one safe retry remains in the global feedback banner.
        action != null -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text(
                if (state.selectedPartId == part.id) "Selected part · ${part.id}" else "Next incomplete part · ${part.id}",
                modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_SELECTION },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { dispatchImportPrimaryAction(action, onIntent, focusReturn) }, enabled = !state.operation.isMutating,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { testTag = WorkspacePageTags.IMPORT_PRIMARY_ACTION; contentDescription = "${action.label()} for part ${part.id}" }
            ) { Text(action.label()) }
        }
    }
}

internal fun importPrimaryPart(state: WorkspaceUiState): app.melotrail.application.PartSummary? = state.project?.parts?.let { parts ->
    state.selectedPartId?.let { selected -> parts.firstOrNull { it.id == selected } }
        ?: parts.firstOrNull { primaryPartAction(it, null) !is PartPrimaryAction.AddToStructure }
        ?: parts.firstOrNull()
}

/** The shell hosts this on wide layouts; it remains a presentation adapter over existing typed intents. */
@Composable
internal fun ImportContextRail(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val part = importPrimaryPart(state)
    Column(Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.IMPORT_CONTEXT }, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text("SELECTED SOURCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (part == null) {
            Text("Select or import a source to see its validated preparation state.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Part ${part.id}", fontWeight = FontWeight.SemiBold)
            Text(part.sourceName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Text("${part.sourceType.name} · ${state.partPreparationLabel(part.id)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(part.preparation.warnings.firstOrNull() ?: "Source remains immutable; derived preparation is inspectable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick = { onIntent(WorkspaceIntent.ShowPartDetails(part.id, PartDetailsFocusReturn.ImportPrimaryAction)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Details") }
        }
    }
}

private fun dispatchImportPrimaryAction(
    action: PartPrimaryAction,
    onIntent: (WorkspaceIntent) -> Unit,
    focusReturn: PartDetailsFocusReturn = PartDetailsFocusReturn.ImportPrimaryAction
) = when (action) {
    is PartPrimaryAction.CleanMidi -> onIntent(WorkspaceIntent.CleanMidi(action.partId))
    is PartPrimaryAction.ReviewCleanMidi -> onIntent(WorkspaceIntent.ShowPartDetails(action.partId, focusReturn))
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
    WorkspaceSection.SETUP -> "Set the explicit musical context before downstream analysis."
    WorkspaceSection.HARMONY -> "Author structured Intro, Verse, Chorus, Bridge, and Outro chord progressions."
    WorkspaceSection.IMPORT -> "Import a MIDI or eligible solo-piano audio source."
    WorkspaceSection.STRUCTURE -> "Canonical structure has ${state.project?.structure?.size ?: 0} section(s)."
    WorkspaceSection.ARRANGE -> "Arrangement state is derived from canonical artifacts."
    WorkspaceSection.MIX_MASTER -> "Mix and master readiness is derived from validated artifacts."
    WorkspaceSection.LIBRARY -> "Validated local instruments and samples only."
    WorkspaceSection.VIDEO_PREVIEW -> "Local visual preview only."
    WorkspaceSection.EXPORT -> "Release export is available only after a current master."
    WorkspaceSection.SETTINGS -> "Settings never contain project or audio data."
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
    val channelNames = LogicalInstrument.entries.map { it.wireName }.filter { it in mix?.availableStems.orEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        PageTitle("Mix & Master", "Adjust stems and build the master WAV")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
            ResponsivePageColumns(narrow = narrow, first = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OverviewCard(WorkspacePageTags.MIXER_VIEWPORT, "Channels") {
                    if (channelNames.isEmpty()) {
                        Text(
                            "No rendered stems are available. Render the approved arrangement to create real channel strips.",
                            modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_EMPTY_CHANNELS },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().heightIn(max = 520.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
                        ) {
                            channelNames.forEach { name ->
                                val setting = mix?.settings?.tracks?.get(name) ?: app.melotrail.application.LogicalMixSetting()
                                MixMasterChannel(name, setting, !mutating) { onIntent(WorkspaceIntent.UpdateMixSetting(name, it)) }
                            }
                        }
                    }
                }
                SecondaryOptions(WorkspacePageTags.MIX_OPTIONS_TOGGLE + "-channels", WorkspacePageTags.MIX_OPTIONS + "-channels", "Channel reset") {
                    OutlinedButton(onClick = { onIntent(WorkspaceIntent.ResetMix) }, enabled = channelNames.isNotEmpty() && !mutating, modifier = Modifier.semantics {
                        testTag = WorkspacePageTags.MIX_RESET
                        contentDescription = if (channelNames.isEmpty()) "Reset mix unavailable. Render stems first." else "Reset all rendered channel settings to engine defaults."
                    }) { Text("Reset engine defaults") }
                }
            }
            }, second = { columnModifier ->
            Column(columnModifier, verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                SecondaryOptions(WorkspacePageTags.MIX_OPTIONS_TOGGLE, WorkspacePageTags.MIX_OPTIONS, "Listening and build options") {
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
                    OverviewCard("mix-master-options", "Build options") {
                        Text(if (mode == MixMasterMode.LISTEN) "Build options" else "Supported build options", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.buildOptions.loFi, onCheckedChange = { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFi = it))) }, enabled = !mutating,
                                modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_LOFI; contentDescription = "Apply the fixed supported Lo-fi audio texture during Build Song." })
                            Text("Lo-fi texture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.buildOptions.loFi) {
                            val presets = app.melotrail.application.LoFiPresetId.entries
                            TextButton(onClick = {
                                val next = presets[(presets.indexOf(state.buildOptions.loFiPreset) + 1) % presets.size]
                                onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFiPreset = next)))
                            }, enabled = !mutating) { Text("Preset: ${state.buildOptions.loFiPreset.name.lowercase().replaceFirstChar(Char::uppercase)}") }
                            Text("Strength ${(state.buildOptions.loFiStrength * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(value = state.buildOptions.loFiStrength.toFloat(), onValueChange = { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(loFiStrength = it.toDouble()))) }, valueRange = 0f..1f, enabled = !mutating)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.buildOptions.mp3, onCheckedChange = { onIntent(WorkspaceIntent.UpdateBuildOptions(state.buildOptions.copy(mp3 = it))) }, enabled = !mutating,
                                modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_MP3; contentDescription = "Request optional final MP3 export after the authoritative master WAV." })
                            Text("Optional final MP3 export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OverviewCard("mix-master-humanization", "Humanization") {
                        val humanization = state.humanization
                        val humanizationReady = state.project?.readiness?.let { readiness ->
                            readiness.criticAvailable && readiness.fullSongEnhancementSelection != app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED
                        } == true
                        Text(
                            humanization?.let { snapshot ->
                                if (snapshot.selection == app.melotrail.arrangement.HumanizationSelection.HUMANIZED)
                                    "Current comparison evidence · seed ${snapshot.seed} · ${snapshot.artifacts} artifacts · ${snapshot.changedNotes} recorded edits"
                                else "Bypass selected · cohesive MIDI will be rendered unchanged"
                            } ?: if (humanizationReady) "Select bypass or create a deterministic variation."
                            else "Run Critic, then choose or bypass Full-Song Enhance before Humanization.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        humanization?.warnings?.forEach { warning -> Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                            OutlinedButton(onClick = { onIntent(WorkspaceIntent.BypassHumanization) }, enabled = !mutating && humanizationReady,
                                modifier = Modifier.semantics { testTag = "mix-master-humanization-bypass"; contentDescription = "Bypass humanization and use cohesive MIDI input." }) { Text("Bypass") }
                            Button(onClick = { onIntent(WorkspaceIntent.GenerateHumanization) }, enabled = !mutating && humanizationReady,
                                modifier = Modifier.semantics { testTag = "mix-master-humanization-regenerate"; contentDescription = "Create and select a new deterministic humanization variation." }) {
                                Text(if (humanization?.selection == app.melotrail.arrangement.HumanizationSelection.HUMANIZED) "New variation" else "Use profile default")
                            }
                        }
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
private fun MixMasterChannel(name: String, setting: app.melotrail.application.LogicalMixSetting, enabled: Boolean, onSetting: (app.melotrail.application.LogicalMixSetting) -> Unit) =
    Card(
        Modifier.width(148.dp).heightIn(min = 404.dp).semantics { testTag = WorkspacePageTags.MIX_CHANNEL_PREFIX + name },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
        border = BorderStroke(1.dp, instrumentLane(name)?.color ?: MusicWorkspaceTokens.Border)
    ) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val lane = instrumentLane(name)
            Text("${lane?.icon.orEmpty()} ${lane?.label ?: name.replaceFirstChar(Char::uppercase)}".trim(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = lane?.color ?: MaterialTheme.colorScheme.onSurface)
            Text("${"%.1f".format(setting.gainDb)} dB", style = MaterialTheme.typography.labelSmall)
        }
        Slider(value = setting.gainDb.toFloat(), onValueChange = { onSetting(setting.copy(gainDb = it.toDouble())) }, valueRange = -24f..12f, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.MIX_GAIN_PREFIX + name; contentDescription = "$name gain ${"%.1f".format(setting.gainDb)} decibels" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onSetting(setting.copy(muted = !setting.muted)) }, enabled = enabled, modifier = Modifier.width(52.dp).semantics { testTag = WorkspacePageTags.MIX_MUTE_PREFIX + name; contentDescription = "${if (setting.muted) "Unmute" else "Mute"} $name" }) { Text("M") }
            OutlinedButton(onClick = { onSetting(setting.copy(solo = !setting.solo)) }, enabled = enabled, modifier = Modifier.width(52.dp).semantics { testTag = WorkspacePageTags.MIX_SOLO_PREFIX + name; contentDescription = "${if (setting.solo) "Unsolo" else "Solo"} $name" }) { Text("S") }
        }
        Text("Pan ${"%.2f".format(setting.pan)}", style = MaterialTheme.typography.labelSmall)
        Slider(value = setting.pan.toFloat(), onValueChange = { onSetting(setting.copy(pan = it.toDouble())) }, valueRange = -1f..1f, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.MIX_PAN_PREFIX + name; contentDescription = "$name pan ${"%.2f".format(setting.pan)}" })
        Spacer(Modifier.weight(1f))
        Text("0.0 dBFS · Level unavailable", style = MaterialTheme.typography.labelSmall, color = semanticColor(WorkspaceSemanticState.DISABLED), modifier = Modifier.semantics { testTag = WorkspacePageTags.MIX_METER_PREFIX + name; contentDescription = "Level unavailable for $name; zero signal is displayed because no measured level data is available." })
        }
    }

@Composable
private fun MixMasterModeSelector(selected: MixMasterMode, onSelected: (MixMasterMode) -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
    listOf(MixMasterMode.LISTEN, MixMasterMode.MIX, MixMasterMode.MASTER).forEach { mode ->
        OutlinedButton(onClick = { onSelected(mode) }, colors = workspaceSelectableButtonColors(selected == mode), modifier = Modifier.weight(1f).semantics {
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
    OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPlaybackSource(source)) }, enabled = available && !state.operation.isMutating, colors = workspaceSelectableButtonColors(selected == source), modifier = modifier.semantics {
        testTag = tag
        contentDescription = if (available) "$label playback source${if (selected == source) ", selected" else ""}" else "$label playback is unavailable or stale. Build a current artifact first."
    }) { Text(label) }
}

@Composable
private fun ZeroSignalPlaceholder() = OverviewCard(WorkspacePageTags.MIX_ZERO_SIGNAL, "Levels") {
    Text("0.0 dBFS · No measured signal", style = MaterialTheme.typography.bodySmall, color = semanticColor(WorkspaceSemanticState.DISABLED))
}

private fun mixMasterCanBuild(state: WorkspaceUiState): Boolean = state.project != null &&
    !state.downstreamArtifactsStale &&
    !state.operation.isMutating &&
    state.arrangement?.approved == true &&
    state.arrangement.approvalRequired == false &&
    state.arrangement.stale == false &&
    state.project.readiness.cohesionReady &&
    state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available == true

private fun mixMasterBuildMessage(state: WorkspaceUiState): String = when {
    state.project == null -> "Open a project before building."
    state.arrangement == null -> "Build Song needs a current approved arrangement."
    state.arrangement.stale -> "The arrangement is stale; regenerate it before building."
    state.downstreamArtifactsStale -> "Mix artifacts are stale; regenerate them from the current arrangement before building."
    state.arrangement.approvalRequired || !state.arrangement.approved -> "Approve the reviewed arrangement before building."
    state.project.readiness.cohesionApprovalRequired -> "Compare and approve Cohesion before building."
    !state.project.readiness.cohesionReady -> "Generate and approve Cohesion before building."
    state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.available != true -> state.runtimeReadiness?.capability(RuntimeCapability.BUILD_SONG)?.reason ?: "Checking local build readiness."
    else -> "Build validates artifacts, publishes a lossless master WAV atomically, then optionally exports MP3."
}

private fun workflowBody(state: WorkspaceUiState): String = when (state.workspaceSection) {
    WorkspaceSection.SETUP -> "Choose and save the explicit musical context for this project."
    WorkspaceSection.HARMONY -> "Save Setup, then add at least one executable chord to each required progression."
    WorkspaceSection.IMPORT -> if (state.project == null) "Create or open a project before importing." else "Choose a source through the validated import dialog."
    WorkspaceSection.EXPORT -> if (state.project?.readiness?.releaseAvailable == true && !state.downstreamArtifactsStale) "A release is available for export." else "A current validated release is unavailable. Build the current project first."
    WorkspaceSection.LIBRARY -> "Library inventory is limited to the configured local sound pack."
    WorkspaceSection.SETTINGS -> "Settings are local preferences, not project data."
    WorkspaceSection.STRUCTURE, WorkspaceSection.ARRANGE, WorkspaceSection.MIX_MASTER, WorkspaceSection.VIDEO_PREVIEW -> "This focused page is not implemented in Task 083."
    WorkspaceSection.OVERVIEW -> error("Overview has its own page")
}

@Composable
private fun PageRoot(section: WorkspaceSection, modifier: Modifier, content: @Composable () -> Unit) = Box(
    modifier.fillMaxSize().semantics { testTag = WorkspacePageTags.ROOT_PREFIX + section.name.lowercase() }
) { content() }
