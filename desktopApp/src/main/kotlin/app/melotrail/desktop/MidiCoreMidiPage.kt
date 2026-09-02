package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiFindingSeverity
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Target-only MIDI source chooser used by the MIDI page. */
internal data class MidiCoreMidiPageActions(
    val chooseMidiSource: suspend () -> Path? = { null },
)

internal object MidiCoreMidiPageTags {
    const val ROOT = "midi-core-midi-page"
    const val IMPORT = "midi-core-midi-import"
    const val SOURCE_FACTS = "midi-core-midi-source-facts"
    const val SOURCE_FILENAME = "midi-core-midi-source-filename"
    const val SOURCE_DIGEST = "midi-core-midi-source-digest"
    const val SOURCE_FORMAT = "midi-core-midi-source-format"
    const val SOURCE_DURATION = "midi-core-midi-source-duration"
    const val TRACK_TABLE = "midi-core-midi-track-table"
    const val TRACK_PREFIX = "midi-core-midi-track-"
    const val CHANNEL_PREFIX = "midi-core-midi-channel-"
    const val SELECTION = "midi-core-midi-selection"
    const val FINDINGS = "midi-core-midi-findings"
    const val BLOCKING_FINDINGS = "midi-core-midi-findings-blocking"
    const val ADVISORY_FINDINGS = "midi-core-midi-findings-advisory"
    const val AWAITING_FINDINGS = "midi-core-midi-findings-awaiting-authority"
    const val IMMUTABILITY = "midi-core-midi-immutability"
    const val UNSUPPORTED = "midi-core-midi-unsupported"
    const val TRANSPORT = "midi-core-midi-transport"
    const val PLAY = "midi-core-midi-play-source"
    const val PAUSE = "midi-core-midi-pause"
    const val STOP = "midi-core-midi-stop"
    const val SEEK = "midi-core-midi-seek"
    const val LOOP = "midi-core-midi-loop"
    const val RECOVERY = "midi-core-midi-recovery"
    const val RETRY = "midi-core-midi-retry"

    fun track(index: Int) = TRACK_PREFIX + index
    fun channel(trackIndex: Int, channel: Int) = "$CHANNEL_PREFIX$trackIndex-$channel"
}

/** MIDI source import, automatically protected melody evidence, and MIDI transport page. */
@Composable
internal fun MidiCoreMidiPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    actions: MidiCoreMidiPageActions,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier.semantics {
            testTag = MidiCoreMidiPageTags.ROOT
            contentDescription = "MIDI source and protected melody page"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        if (state.project == null) {
            MidiCard(MidiCoreMidiPageTags.ROOT + "-empty", "Import MIDI source") {
                Text("Open or create a MIDI Core project before importing a source.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            MidiImportCard(
                state = state,
                onChooseSource = {
                    scope.launch {
                        actions.chooseMidiSource()?.toAbsolutePath()?.normalize()?.let { onIntent(MidiCoreWorkspaceIntent.ImportSource(it)) }
                    }
                },
            )
            state.source.takeIf { it.status == MidiCoreSourceStatus.IMPORTED }?.let { source ->
                MidiSourceFacts(source)
                MidiTrackTable(state)
                MidiSelectionCard(state)
                MidiFindingsCard(source.findings)
                MidiSourceTransport(state, onIntent)
                MidiExplanationCards()
            }
            MidiRecoveryCard(state, onIntent)
        }
    }
}

@Composable
private fun MidiImportCard(state: MidiCoreWorkspaceState, onChooseSource: () -> Unit) {
    MidiCard(MidiCoreMidiPageTags.ROOT + "-import", "Source MIDI") {
        Text(
            if (state.source.status == MidiCoreSourceStatus.IMPORTED) {
                "One immutable Standard MIDI source is bound to this project."
            } else {
                "Choose one .mid or .midi file containing the complete song as one note-bearing melody track. Additional tracks cannot contain notes."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onChooseSource,
            enabled = !state.busy && state.source.status != MidiCoreSourceStatus.IMPORTED,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics {
                    testTag = MidiCoreMidiPageTags.IMPORT
                    contentDescription = "Import one Standard MIDI source"
                },
        ) { Text("Import MIDI source") }
    }
}

@Composable
private fun MidiSourceFacts(source: MidiCoreSourceUiState) {
    MidiCard(MidiCoreMidiPageTags.SOURCE_FACTS, "Source facts") {
        FactLine(MidiCoreMidiPageTags.SOURCE_FILENAME, "Original filename", source.originalFilename ?: "Unavailable")
        FactLine(MidiCoreMidiPageTags.SOURCE_DIGEST, "Immutable SHA-256", source.sha256 ?: "Unavailable")
        FactLine(MidiCoreMidiPageTags.SOURCE_FORMAT, "Standard MIDI", "Format ${source.format ?: "?"} · PPQ ${source.ppq ?: "?"}")
        FactLine(MidiCoreMidiPageTags.SOURCE_DURATION, "Source duration", "${source.sourceEndTick ?: 0L} ticks")
        Text(
            if (source.reportAvailable) "Import report is preserved with the source artifact." else "Import report is not available.",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
}

@Composable
private fun MidiTrackTable(state: MidiCoreWorkspaceState) {
    MidiCard(MidiCoreMidiPageTags.TRACK_TABLE, "Tracks and channels") {
        Text(
            "The only note-bearing track is protected automatically. Additional non-note tracks remain immutable source evidence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MusicWorkspaceTokens.TextSecondary,
        )
        state.source.trackSummaries.forEach { track ->
            MidiTrackRow(track, state)
        }
    }
}

@Composable
private fun MidiTrackRow(track: MidiTrackSummary, state: MidiCoreWorkspaceState) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreMidiPageTags.track(track.trackIndex)
            contentDescription = "Track ${track.trackIndex}: ${track.name ?: "unnamed"}, ${track.durationTicks} ticks"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Text("Track ${track.trackIndex}: ${track.name ?: "Unnamed"}", style = MaterialTheme.typography.titleMedium)
            Text("Duration ${track.durationTicks} ticks", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            if (track.channels.isEmpty()) {
                Text("No channel note/controller facts", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            } else {
                track.channels.forEach { channel ->
                    val selected = state.melody.selected?.let { it.trackIndex == track.trackIndex && it.channel == channel.channel } == true
                    MidiChannelRow(track, channel.channel, channel.noteCount, channel.minimumPitch, channel.maximumPitch, channel.controllerCount, channel.likelyRoles, selected)
                }
            }
        }
    }
}

@Composable
private fun MidiChannelRow(
    track: MidiTrackSummary,
    channel: Int,
    noteCount: Int,
    minimumPitch: Int?,
    maximumPitch: Int?,
    controllerCount: Int,
    likelyRoles: List<MidiTrackRoleHint>,
    selected: Boolean,
) {
    val range = if (minimumPitch != null && maximumPitch != null) "$minimumPitch–$maximumPitch" else "none"
    val roles = likelyRoles.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) }.ifBlank { "none" }
    Row(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreMidiPageTags.channel(track.trackIndex, channel)
            contentDescription = "Track ${track.trackIndex}, channel ${channel + 1}; $noteCount notes; pitch range $range; $controllerCount controllers; likely roles $roles"
        },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text("Channel ${channel + 1}", style = MaterialTheme.typography.labelLarge)
            Text(
                "$noteCount notes · pitch $range · controllers ${if (controllerCount == 0) "none" else controllerCount} · likely $roles",
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text("Protected automatically", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.Success)
        }
    }
}

@Composable
private fun MidiSelectionCard(state: MidiCoreWorkspaceState) {
    MidiCard(MidiCoreMidiPageTags.SELECTION, "Protected melody") {
        val selected = state.melody.selected
        Text(
            selected?.let { "Track ${it.trackIndex}, channel ${it.channel + 1} was protected automatically during import." }
                ?: "Import one valid single-track melody source.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "The protected melody is immutable source evidence. Import a new project to use a different melody.",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
}

@Composable
private fun MidiFindingsCard(findings: List<MidiFinding>) {
    val grouped = findings.groupBy(MidiFinding::severity)
    MidiCard(MidiCoreMidiPageTags.FINDINGS, "Import findings") {
        if (findings.isEmpty()) {
            Text("No current import findings.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Success)
        } else {
            FindingGroup(MidiFindingSeverity.BLOCKING, grouped[MidiFindingSeverity.BLOCKING].orEmpty())
            FindingGroup(MidiFindingSeverity.AWAITING_AUTHORITY, grouped[MidiFindingSeverity.AWAITING_AUTHORITY].orEmpty())
            FindingGroup(MidiFindingSeverity.ADVISORY, grouped[MidiFindingSeverity.ADVISORY].orEmpty())
        }
    }
}

@Composable
private fun FindingGroup(severity: MidiFindingSeverity, findings: List<MidiFinding>) {
    if (findings.isEmpty()) return
    val tag = when (severity) {
        MidiFindingSeverity.BLOCKING -> MidiCoreMidiPageTags.BLOCKING_FINDINGS
        MidiFindingSeverity.ADVISORY -> MidiCoreMidiPageTags.ADVISORY_FINDINGS
        MidiFindingSeverity.AWAITING_AUTHORITY -> MidiCoreMidiPageTags.AWAITING_FINDINGS
    }
    Column(
        Modifier.fillMaxWidth().semantics {
            testTag = tag
            contentDescription = "${severityLabel(severity)} findings"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
    ) {
        Text(severityLabel(severity), style = MaterialTheme.typography.titleMedium, color = severityColor(severity))
        findings.forEachIndexed { index, finding ->
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = "$tag-$index"
                    contentDescription = "${finding.message} Action: ${finding.action}"
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                Text(finding.message, style = MaterialTheme.typography.bodyMedium)
                Text("Action: ${finding.action}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            }
        }
    }
}

@Composable
private fun MidiSourceTransport(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    val audition = state.audition
    val sourceScope = audition.scope == MidiAuditionScope.SourceMelody
    val sourceEnd = state.source.sourceEndTick ?: 0L
    val position = if (sourceScope) audition.positionTick.coerceIn(0L, sourceEnd.coerceAtLeast(1L)) else 0L
    MidiCard(MidiCoreMidiPageTags.TRANSPORT, "MIDI source audition") {
        Text("Audition sends MIDI events to the selected local MIDI output; it never renders an audio file.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlaySourceMelody) },
                enabled = !state.busy && state.melody.selected != null,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreMidiPageTags.PLAY },
            ) { Text("Play source melody") }
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PauseAudition) },
                enabled = sourceScope && audition.playback == MidiAuditionPlaybackState.PLAYING,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreMidiPageTags.PAUSE },
            ) { Text("Pause") }
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.StopAudition) },
                enabled = sourceScope && audition.playback != MidiAuditionPlaybackState.STOPPED,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreMidiPageTags.STOP },
            ) { Text("Stop") }
        }
        Text(
            if (sourceScope) "${audition.playback.name.lowercase().replaceFirstChar(Char::uppercaseChar)} at $position / $sourceEnd ticks" else "Source melody is not playing.",
            Modifier.semantics { contentDescription = "MIDI audition transport status" },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
        Slider(
            value = position.toFloat(),
            onValueChange = { onIntent(MidiCoreWorkspaceIntent.SeekAudition(it.toLong().coerceIn(0L, sourceEnd))) },
            valueRange = 0f..sourceEnd.coerceAtLeast(1L).toFloat(),
            enabled = sourceScope && sourceEnd > 0L,
            modifier = Modifier.fillMaxWidth().semantics {
                testTag = MidiCoreMidiPageTags.SEEK
                contentDescription = "Seek source MIDI audition"
            },
        )
        OutlinedButton(
            onClick = {
                onIntent(
                    MidiCoreWorkspaceIntent.SetAuditionLoop(
                        if (audition.loop == null && sourceEnd > 0L) app.melotrail.audition.MidiAuditionLoop(0L, sourceEnd) else null,
                    ),
                )
            },
            enabled = sourceScope && sourceEnd > 0L,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics {
                    testTag = MidiCoreMidiPageTags.LOOP
                    contentDescription = if (audition.loop == null) "Loop entire source melody" else "Disable source melody loop"
                },
        ) { Text(if (audition.loop == null) "Loop source" else "Loop enabled") }
    }
}

@Composable
private fun MidiExplanationCards() {
    MidiCard(MidiCoreMidiPageTags.UNSUPPORTED, "Supported source boundary") {
        Text("MIDI Core accepts Standard MIDI File format 0 or 1 with PPQ timing.", style = MaterialTheme.typography.bodyMedium)
        Text("Tempo and time-signature maps are reported as blocking findings; they are never flattened silently.", style = MaterialTheme.typography.bodySmall)
        Text("Unsupported messages remain visible in the import report and are omitted from generated-role output.", style = MaterialTheme.typography.bodySmall)
        Text("Files with multiple note-bearing tracks or multiple note-bearing channels are rejected.", style = MaterialTheme.typography.bodySmall)
    }
    MidiCard(MidiCoreMidiPageTags.IMMUTABILITY, "Source identity") {
        Text("The imported filename, bytes, SHA-256, import report, and selected melody identity are preserved. Importing never overwrites an existing source.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MidiRecoveryCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    if (state.blockers.isEmpty() && state.operation.retry == null) return
    MidiCard(MidiCoreWorkspaceShellTags.BLOCKERS, "MIDI action status") {
        state.blockers.forEach { blocker ->
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreWorkspaceShellTags.BLOCKER_PREFIX + blocker.code.name.lowercase()
                    contentDescription = "${blocker.message} Next action: ${blocker.nextAction}"
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                Text(blocker.message, style = MaterialTheme.typography.bodyMedium)
                Text("Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                blocker.action?.let { action -> TextButton(onClick = { onIntent(action) }) { Text("Take next action") } }
            }
        }
        state.operation.retry?.let {
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) },
                enabled = !state.busy,
                modifier = Modifier.semantics { testTag = MidiCoreMidiPageTags.RETRY },
            ) { Text("Retry MIDI action") }
        }
    }
}

@Composable
private fun FactLine(tag: String, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics { testTag = tag; contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
    ) {
        Text(label, Modifier.widthIn(min = 150.dp), style = MaterialTheme.typography.labelLarge)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MidiCard(tag: String, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun severityLabel(severity: MidiFindingSeverity): String = when (severity) {
    MidiFindingSeverity.BLOCKING -> "Blocking findings"
    MidiFindingSeverity.AWAITING_AUTHORITY -> "Awaiting authority"
    MidiFindingSeverity.ADVISORY -> "Advisory findings"
}

private fun severityColor(severity: MidiFindingSeverity) = when (severity) {
    MidiFindingSeverity.BLOCKING -> MusicWorkspaceTokens.Error
    MidiFindingSeverity.AWAITING_AUTHORITY -> MusicWorkspaceTokens.Warning
    MidiFindingSeverity.ADVISORY -> MusicWorkspaceTokens.Information
}
