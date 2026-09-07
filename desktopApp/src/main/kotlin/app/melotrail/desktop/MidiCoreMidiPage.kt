@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.melotrail.desktop

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.application.MidiCoreVisualEvidence
import app.melotrail.application.MidiCoreVisualEvidenceAvailable
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiFindingScope
import app.melotrail.midi.domain.MidiFindingSeverity
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import kotlinx.coroutines.launch
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Target-only MIDI source chooser used by the MIDI page. */
internal data class MidiCoreMidiPageActions(
    val chooseMidiSource: suspend () -> Path? = { null },
)

/** Native desktop drop adapter. Source admission still flows through the existing import intent. */
internal class MidiCoreMidiDropTarget(
    private val canImport: () -> Boolean,
    private val onSource: (Path) -> Unit,
) : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean {
        val files = runCatching { (event.dragData() as? DragData.FilesList)?.readFiles() }.getOrNull() ?: return false
        return dropFileUris(files)
    }

    internal fun dropFileUris(files: List<String>): Boolean {
        if (!canImport()) return false
        val source = midiSourcePathFromDroppedFiles(files) ?: return false
        onSource(source)
        return true
    }
}

/** Compose Desktop reports native file drops as file URIs. Accept exactly one existing MIDI file. */
internal fun midiSourcePathFromDroppedFiles(files: List<String>): Path? {
    if (files.size != 1) return null
    val uri = runCatching { URI(files.single()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("file", ignoreCase = true)) return null
    val source = runCatching { Path.of(uri).toAbsolutePath().normalize() }.getOrNull() ?: return null
    val extension = source.fileName?.toString()?.substringAfterLast('.', missingDelimiterValue = "").orEmpty()
    return source.takeIf { extension.equals("mid", ignoreCase = true) || extension.equals("midi", ignoreCase = true) }
        ?.takeIf { Files.isRegularFile(it) }
}

internal object MidiCoreMidiPageTags {
    const val ROOT = "midi-core-midi-page"
    const val IMPORT = "midi-core-midi-import"
    const val SOURCE_FACTS = "midi-core-midi-source-facts"
    const val SOURCE_FILENAME = "midi-core-midi-source-filename"
    const val SOURCE_DIGEST = "midi-core-midi-source-digest"
    const val SOURCE_FORMAT = "midi-core-midi-source-format"
    const val SOURCE_DURATION = "midi-core-midi-source-duration"
    const val NOTE_LANE = "midi-core-midi-protected-note-lane"
    const val AUTHORITY_STATUS = "midi-core-midi-authority-status"
    const val TRACK_TABLE = "midi-core-midi-track-table"
    const val TRACK_COUNT = "midi-core-midi-track-count"
    const val TRACK_PREFIX = "midi-core-midi-track-"
    const val CHANNEL_PREFIX = "midi-core-midi-channel-"
    const val SELECTION = "midi-core-midi-selection"
    const val FINDINGS = "midi-core-midi-findings"
    const val BLOCKING_FINDINGS = "midi-core-midi-findings-blocking"
    const val ADVISORY_FINDINGS = "midi-core-midi-findings-advisory"
    const val AWAITING_FINDINGS = "midi-core-midi-findings-awaiting-authority"
    const val IMMUTABILITY = "midi-core-midi-immutability"
    const val UNSUPPORTED = "midi-core-midi-unsupported"
    const val PLAY = "midi-core-midi-play-source"
    const val RECOVERY = "midi-core-midi-recovery"
    const val RETRY = "midi-core-midi-retry"

    fun track(index: Int) = TRACK_PREFIX + index
    fun channel(trackIndex: Int, channel: Int) = "$CHANNEL_PREFIX$trackIndex-$channel"
}

/** MIDI source import and automatically protected melody evidence page. */
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
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "SOURCE",
            title = "MIDI",
            summary = "Import one complete melody, listen to it immediately, then review the preserved source facts.",
        )
        if (state.project == null) {
            MidiCard(MidiCoreMidiPageTags.ROOT + "-empty", "Import MIDI source") {
                Text("Open or create a MIDI Core project before importing a source.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val canImport = !state.busy && state.project.sourceMidi == null && state.source.status != MidiCoreSourceStatus.IMPORTED
            val importDroppedSource: (Path) -> Unit = { onIntent(MidiCoreWorkspaceIntent.ImportSource(it)) }
            MidiImportCard(
                state = state,
                onChooseSource = {
                    scope.launch {
                        actions.chooseMidiSource()?.toAbsolutePath()?.normalize()?.let { onIntent(MidiCoreWorkspaceIntent.ImportSource(it)) }
                    }
                },
                canImport = canImport,
                onDropSource = importDroppedSource,
            )
            state.source.takeIf { it.status == MidiCoreSourceStatus.IMPORTED }?.let { source ->
                MidiSourceAuditionAction(state, onIntent)
                MidiSourceFacts(source)
                MidiProtectedNoteLane(state.visualEvidence?.source)
                MidiTrackTable(state)
                MidiSelectionCard(state)
                MidiFindingsCard(source.findings)
                MidiExplanationCards()
            }
            state.source.takeIf { it.status == MidiCoreSourceStatus.REJECTED && it.findings.isNotEmpty() }?.let { source ->
                MidiFindingsCard(source.findings)
            }
            MidiRecoveryCard(state, onIntent)
        }
    }
}

@Composable
private fun MidiImportCard(
    state: MidiCoreWorkspaceState,
    onChooseSource: () -> Unit,
    canImport: Boolean,
    onDropSource: (Path) -> Unit,
) {
    val dropTarget = remember(canImport, onDropSource) {
        MidiCoreMidiDropTarget(canImport = { canImport }, onSource = onDropSource)
    }
    MidiCard(
        tag = MidiCoreMidiPageTags.ROOT + "-import",
        title = "Source MIDI",
        modifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                canImport && runCatching { event.dragData() is DragData.FilesList }.getOrDefault(false)
            },
            target = dropTarget,
        ),
    ) {
        Text(
            if (state.source.status == MidiCoreSourceStatus.IMPORTED) {
                "One immutable Standard MIDI source is bound to this project."
            } else {
                "Drop one .mid or .midi file here, or use the chooser. It must contain the complete song as one note-bearing melody track; additional tracks cannot contain notes."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onChooseSource,
            enabled = canImport,
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
        Text("SOURCE-DERIVED · preserved evidence", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.Information)
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

/** Draw only digest-checked source evidence; this compact lane has no edit or cleanup affordance. */
@Composable
private fun MidiProtectedNoteLane(sourceEvidence: MidiCoreVisualEvidence?) {
    MidiCard(MidiCoreMidiPageTags.NOTE_LANE, "Protected melody note lane") {
        when (sourceEvidence) {
            is MidiCoreVisualEvidence.Available -> SourceNoteLane(sourceEvidence.value)
            is MidiCoreVisualEvidence.Unavailable -> Text(
                "${sourceEvidence.value.message} Next: ${sourceEvidence.value.nextAction}",
                color = MusicWorkspaceTokens.Warning,
            )
            null -> Text("Verified source notes are preparing from the preserved MIDI artifact.", color = MusicWorkspaceTokens.TextSecondary)
        }
    }
}

@Composable
private fun SourceNoteLane(evidence: MidiCoreVisualEvidenceAvailable) {
    val notes = evidence.lanes.flatMap { it.events }
    Text(
        "Protected melody · ${notes.size} notes · global MIDI pitch 0–127 · source-derived",
        style = MaterialTheme.typography.bodySmall,
        color = MusicWorkspaceTokens.TextSecondary,
    )
    Canvas(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).semantics {
            testTag = MidiCoreMidiPageTags.NOTE_LANE + "-canvas"
            contentDescription = "Protected melody note lane with ${notes.size} verified notes on a global MIDI pitch scale"
        },
    ) {
        val duration = evidence.timing.songEndTick.toFloat()
        notes.forEach { note ->
            val start = size.width * note.startTick / duration
            val end = size.width * note.endTick / duration
            val y = (127 - note.pitch) / 127f * (size.height - 8f)
            drawRect(
                color = MusicWorkspaceTokens.Role.Melody,
                topLeft = Offset(start, y),
                size = Size((end - start).coerceAtLeast(2f).coerceAtMost(size.width - start), 6f),
            )
        }
    }
}

@Composable
private fun MidiTrackTable(state: MidiCoreWorkspaceState) {
    MidiCard(MidiCoreMidiPageTags.TRACK_TABLE, "Tracks and channels") {
        val tracks = state.source.trackSummaries
        val channels = tracks.sumOf { it.channels.size }
        val notes = tracks.sumOf { track -> track.channels.sumOf { it.noteCount } }
        Text(
            "${tracks.size} source ${if (tracks.size == 1) "track" else "tracks"} · " +
                "$channels ${if (channels == 1) "channel" else "channels"} · " +
                "$notes ${if (notes == 1) "note" else "notes"}",
            modifier = Modifier.semantics { testTag = MidiCoreMidiPageTags.TRACK_COUNT },
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            "The only note-bearing track is protected automatically. Additional non-note tracks remain immutable source evidence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MusicWorkspaceTokens.TextSecondary,
        )
        tracks.forEach { track ->
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
        val authority = state.authority.confirmed
        Text(
            authority?.let {
                "CONFIRMED AUTHORITY · ${formatBpmDisplay(it.tempo)} BPM · ${it.key.spelling.symbol} ${it.key.mode.displayName} · ${it.meter.numerator}/${it.meter.denominator}"
            } ?: "AUTHORITY NOT CONFIRMED · imported MIDI facts and suggestions do not change project settings.",
            modifier = Modifier.semantics { testTag = MidiCoreMidiPageTags.AUTHORITY_STATUS },
            style = MaterialTheme.typography.bodySmall,
            color = if (authority == null) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Success,
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
            val location = midiFindingLocation(finding)
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = "$tag-$index"
                    contentDescription = "$location. ${finding.message} Action: ${finding.action}"
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                Text(location, style = MaterialTheme.typography.labelLarge, color = severityColor(severity))
                Text(finding.message, style = MaterialTheme.typography.bodyMedium)
                Text("Action: ${finding.action}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            }
        }
    }
}

@Composable
private fun MidiSourceAuditionAction(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    MidiCard(MidiCoreMidiPageTags.ROOT + "-source-audition-action", "Listen to the imported melody") {
        Text("Choose the protected melody here. Playback, output, looping, and position stay in the persistent player below.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        Button(
            onClick = { onIntent(MidiCoreWorkspaceIntent.PlaySourceMelody) },
            enabled = !state.busy && state.melody.selected != null,
            colors = workspacePrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics {
                    testTag = MidiCoreMidiPageTags.PLAY
                    contentDescription = "Play protected source melody in the persistent MIDI player"
                },
        ) { Text("Play protected melody") }
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
private fun MidiCard(
    tag: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth().semantics { testTag = tag },
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

/** Human-facing scope plus every precise location supplied by the import validator. */
private fun midiFindingLocation(finding: MidiFinding): String = buildList {
    add(
        "Scope: " + when (finding.scope) {
            MidiFindingScope.SOURCE -> "Source"
            MidiFindingScope.TEMPO -> "Tempo"
            MidiFindingScope.METER -> "Meter"
            MidiFindingScope.MELODY_SELECTION -> "Melody selection"
            MidiFindingScope.TRACK -> "Track"
            MidiFindingScope.CHANNEL -> "Channel"
            MidiFindingScope.EVENT -> "Event"
        },
    )
    finding.trackIndex?.let { add("Track $it") }
    finding.channel?.let { add("MIDI channel ${it + 1}") }
    finding.tick?.let { add("Tick $it") }
}.joinToString(" · ")
