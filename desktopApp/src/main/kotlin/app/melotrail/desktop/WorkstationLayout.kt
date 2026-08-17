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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
            ScenePresentationPanel(state, onIntent)
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
            if (state.workspaceSection == WorkspaceSection.MIX_MASTER) MixPanel(state, onIntent) else ScenePresentationPanel(state, onIntent, compact = true)
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
private fun ScenePresentationPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit, compact: Boolean = false) {
    val session = state.playbackSession
    val playbackAvailable = session.request != null && session.artifact != null && session.phase !in setOf(
        PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.STARTING, PlaybackSessionPhase.FAILED
    )
    val canStop = session.phase in setOf(
        PlaybackSessionPhase.RESOLVING, PlaybackSessionPhase.PREPARING, PlaybackSessionPhase.READY,
        PlaybackSessionPhase.STARTING, PlaybackSessionPhase.PLAYING, PlaybackSessionPhase.PAUSED
    )
    val canSeek = playbackAvailable && session.durationSeconds > 0.0
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = WorkspaceTags.PRESENTATION_PANEL },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface), border = BorderStroke(1.dp, MusicWorkspaceTokens.Border)
    ) {
        Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Sm), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Box(
                Modifier.fillMaxWidth().height(if (compact) 170.dp else 225.dp).clip(MaterialTheme.shapes.medium)
                    .background(Brush.linearGradient(listOf(MusicWorkspaceTokens.ScenePlaceholder, MusicWorkspaceTokens.Pad.copy(alpha = 0.42f), MusicWorkspaceTokens.Canvas)))
                    .semantics { contentDescription = "Placeholder scene artwork. Local deterministic illustration; no scene artwork is loaded." }
            ) {
                Text("LOCAL PLACEHOLDER", modifier = Modifier.align(Alignment.TopStart).padding(MusicWorkspaceTokens.Spacing.Sm), style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
                Text("✦", modifier = Modifier.align(Alignment.Center), color = MusicWorkspaceTokens.Teal.copy(alpha = 0.7f), style = MaterialTheme.typography.displayMedium)
                OutlinedButton(
                    onClick = {}, enabled = false,
                    modifier = Modifier.align(Alignment.TopEnd).padding(MusicWorkspaceTokens.Spacing.Sm).semantics {
                        testTag = WorkspaceTags.SCENE_CHANGE
                        contentDescription = "Change scene unavailable. Scene generation is not available in this local placeholder."
                    }
                ) { Text("Change scene") }
            }
            Text("Placeholder route · local visual-only location", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(scenePlayerTitle(session), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Travel and weather are deterministic visual placeholders — no location or weather data is used.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                TextButton(
                    onClick = { onIntent(WorkspaceIntent.PlayPause) }, enabled = playbackAvailable,
                    modifier = Modifier.semantics {
                        testTag = WorkspaceTags.SCENE_PLAY_PAUSE
                        contentDescription = if (playbackAvailable) "Play or pause the persistent playback session" else "Playback unavailable. Select a ready preview or build a current mix first."
                    }
                ) { Text(if (session.phase == PlaybackSessionPhase.PLAYING) "Pause" else "Play") }
                TextButton(
                    onClick = { onIntent(WorkspaceIntent.StopPlayback) }, enabled = canStop,
                    modifier = Modifier.semantics {
                        testTag = WorkspaceTags.SCENE_STOP
                        contentDescription = if (canStop) "Stop the persistent playback session" else "Stop unavailable because playback has not started."
                    }
                ) { Text("Stop") }
                Text("${sceneDuration(session.positionSeconds)} / ${sceneDuration(session.durationSeconds)}", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { if (session.durationSeconds > 0.0) (session.positionSeconds / session.durationSeconds).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.weight(1f).semantics {
                        testTag = WorkspaceTags.SCENE_PROGRESS
                        contentDescription = if (canSeek) "Playback progress for the persistent session" else "Playback progress unavailable until a local artifact is ready."
                    },
                    color = MusicWorkspaceTokens.Teal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun scenePlayerTitle(session: PlaybackSession): String = when (val request = session.request) {
    is PlaybackRequest.Part -> "Now playing · Part ${request.partId} preview"
    is PlaybackRequest.Mix -> "Now playing · ${request.source.name.lowercase().replaceFirstChar(Char::uppercase)} mix"
    null -> "Now playing · No local playback selected"
}

private fun sceneDuration(seconds: Double): String {
    val wholeSeconds = seconds.coerceAtLeast(0.0).toInt()
    return "%d:%02d".format(java.util.Locale.ROOT, wholeSeconds / 60, wholeSeconds % 60)
}

@Composable
private fun AiSongPlanPanel(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) = WorkspaceCard("AI Song Plan", WorkspaceTags.AI_PLAN_PANEL) {
    val arrangement = state.arrangement
    when {
        state.operationFeedback.kind == OperationKind.ARRANGEMENT && state.operationFeedback.phase == OperationPhase.FAILED ->
            Text("Song plan generation failed: ${state.operationFeedback.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        arrangement == null -> Text("No song plan yet. Generate a validated arrangement to see the local plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        arrangement.stale -> Text("Plan is stale. Regenerate from current structure and analyses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        arrangement.sections.isEmpty() -> Text("No validated plan sections are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Row(Modifier.fillMaxWidth().padding(horizontal = MusicWorkspaceTokens.Spacing.Sm), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text("SECTION", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("PURPOSE", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ENERGY", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("INSTRUMENTS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            arrangement.sections.forEach { section ->
                val selected = section.index == state.selectedArrangementSection
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.small)
                        .background(if (selected) MusicWorkspaceTokens.Teal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                        .clickable { onIntent(WorkspaceIntent.SelectArrangementSection(section.index)) }.padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Spacing.Xs)
                        .semantics {
                            testTag = WorkspaceTags.AI_PLAN_SECTION_PREFIX + section.index
                            contentDescription = "${section.instanceId}, ${section.purpose}, ${(section.energy * 100).toInt()} percent energy, ${section.instruments.joinToString { it.name }}${if (selected) ", selected" else ""}"
                        },
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(40.dp).background(MusicWorkspaceTokens.Teal.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                        Text(section.instanceId, color = MusicWorkspaceTokens.Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(section.purpose.replaceFirstChar(Char::uppercase), modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(section.energy * 100).toInt()}%", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(section.instruments.joinToString(" · ") { it.name.replaceFirstChar(Char::uppercase) }.ifBlank { "No instruments" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PlanWaveform(section.index, section.energy)
                    }
                    TextButton(onClick = {}, enabled = false, modifier = Modifier.semantics {
                            testTag = WorkspaceTags.AI_PLAN_PLAY_PREFIX + section.index
                            contentDescription = "Play section ${section.instanceId} unavailable. Section playback is not a separate playback session."
                        }) { Text("Play", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
    HorizontalDivider(thickness = MusicWorkspaceTokens.Shell.DividerThickness, color = MusicWorkspaceTokens.Border)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.GenerateArrangement) }, enabled = state.project != null && !state.operation.isMutating, modifier = Modifier.weight(1f).semantics { testTag = WorkspaceTags.AI_PLAN_REGENERATE; contentDescription = "Regenerate the song plan from canonical project artifacts" }) { Text("Regenerate") }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).semantics { testTag = WorkspaceTags.AI_PLAN_EXPORT; contentDescription = "Export song plan unavailable. Export pipeline changes are outside this workspace." }) { Text("Export") }
    }
}

@Composable
private fun PlanWaveform(index: Int, energy: Double) {
    val bars = List(12) { position -> ((index * 17 + position * 11) % 29 + 7) / 36f * energy.coerceIn(0.15, 1.0).toFloat() }
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEach { amplitude -> Box(Modifier.weight(1f).height((1f + amplitude * 7f).dp).background(MusicWorkspaceTokens.Teal.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall)) }
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
