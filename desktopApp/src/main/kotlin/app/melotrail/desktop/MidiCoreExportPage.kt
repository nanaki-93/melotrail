package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import app.melotrail.application.MidiCoreExportedPackage
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.MidiCoreExportSnapshot
import java.nio.file.Path
import java.util.Locale

/** Local UI action for revealing an already-published, immutable package directory. */
internal data class MidiCoreExportPageActions(
    val revealDirectory: (Path) -> Unit = {},
)

/** Stable semantic anchors for the focused DAW MIDI package destination. */
internal object MidiCoreExportPageTags {
    const val ROOT = "midi-core-export-page"
    const val EMPTY = "midi-core-export-empty"
    const val READINESS = "midi-core-export-readiness"
    const val DESTINATION = "midi-core-export-destination"
    const val PUBLISH = "midi-core-export-publish"
    const val PROGRESS = "midi-core-export-progress"
    const val CANCEL = "midi-core-export-cancel"
    const val RETRY = "midi-core-export-retry"
    const val FILENAMES = "midi-core-export-filenames"
    const val SNAPSHOT = "midi-core-export-snapshot"
    const val FILE_PREFIX = "midi-core-export-file-"
    const val REVEAL = "midi-core-export-reveal"
    const val SUGGESTIONS = "midi-core-export-instrument-suggestions"
    const val DAW_GUIDANCE = "midi-core-export-daw-guidance"
    const val BLOCKERS = "midi-core-export-blockers"
    const val SUMMARY = "midi-core-export-summary"
    const val INVENTORY = "midi-core-export-inventory"
    const val DETAILS = "midi-core-export-details"
    const val SNAPSHOT_STATUS = "midi-core-export-snapshot-status"
    const val REVEAL_ERROR = "midi-core-export-reveal-error"

    fun scope(occurrenceId: String, role: CandidateRole) = "midi-core-export-scope-$occurrenceId-${role.name.lowercase()}"

    fun file(kind: ExportedFileKind): String = FILE_PREFIX + kind.name.lowercase()
}

/** Publish and inspect one immutable, DAW-ready MIDI package without audio-production options. */
@Composable
internal fun MidiCoreExportPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    actions: MidiCoreExportPageActions = MidiCoreExportPageActions(),
    modifier: Modifier = Modifier,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit = {},
    showHandoff: Boolean = true,
) {
    val project = state.project
    val readiness = remember(project) { exportReadiness(state) }
    val projectExportRoot = state.projectRoot?.resolve("exports")
    val latest = state.export.latest
    val snapshot = latest?.snapshot ?: state.export.latestSnapshot
    val snapshotDirectory = latest?.directory ?: snapshot?.let { state.projectRoot?.resolve("exports")?.resolve(it.id) }
    val exporting = state.operation.active && state.operation.kind == MidiCoreWorkspaceOperationKind.EXPORT

    Column(
        modifier.semantics {
            testTag = MidiCoreExportPageTags.ROOT
            contentDescription = "Export immutable DAW MIDI package"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "DELIVER",
            title = "Export",
            summary = "Validate the arrangement and publish an immutable MIDI package for Logic Pro.",
        )
        if (project == null) {
            ExportEmptyCard(state, onIntent, onNavigate)
            return@Column
        }
        ExportSummaryCard(state)
        ExportDestinationCard(projectExportRoot, state, exporting, readiness.isEmpty() && !state.busy, onIntent)
        ExportReadinessCard(readiness, state, onIntent, onNavigate)
        ExportSnapshotCard(snapshot, latest, snapshotDirectory, actions, state)
        if (showHandoff) MidiCoreExportHandoff()
    }
}

@Composable
private fun ExportEmptyCard(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    ExportCard(MidiCoreExportPageTags.EMPTY, "Export") {
        Text("Open a MIDI Core project to inspect package readiness and publish a DAW MIDI package.", style = MaterialTheme.typography.bodyLarge)
        ExportBlockers(state.blockers, state, onIntent, onNavigate)
    }
}

@Composable
private fun ExportSummaryCard(state: MidiCoreWorkspaceState) {
    val project = requireNotNull(state.project)
    val scopes = remember(project) { midiCoreAcceptedScopeSummary(project) }
    val authority = project.authority
    ExportCard(MidiCoreExportPageTags.SUMMARY, "Accepted MIDI package") {
        Text(project.metadata.name, style = MaterialTheme.typography.titleMedium)
        authority?.let {
            Text("${formatBpmDisplay(it.tempo)} BPM · ${it.meter.numerator}/${it.meter.denominator} · ${it.key.spelling.symbol} ${it.key.mode.displayName}")
            val ppq = project.sourceMidi?.ppq
            val duration = ppq?.let { resolution ->
                String.format(Locale.ROOT, "%.1f s", it.arrangementEndTick.toDouble() * it.tempo.microsecondsPerQuarter / resolution / 1_000_000.0)
            } ?: "—"
            Text("${it.occurrences.size} sections · $duration · PPQ ${ppq ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("All files begin at song origin and retain the confirmed ending, including silence.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
        Text(if (project.selectedMelody == null) "Melody — protection required" else "Melody — protected source · melody.mid", style = MaterialTheme.typography.bodyMedium)
        CandidateRole.entries.forEach { role ->
            val selected = scopes.filter { it.role == role }
            val accepted = selected.count { it.problem == null && !it.rest }
            val rests = selected.count { it.problem == null && it.rest }
            val pending = selected.size - accepted - rests
            val file = when {
                selected.isEmpty() || pending > 0 -> "not ready"
                accepted == 0 -> "whole-song rest · file omitted"
                else -> "${role.name.lowercase()}.mid"
            }
            Text("${role.exportDisplayName} — $accepted accepted · $rests rests${if (pending > 0) " · $pending need attention" else ""} · $file", style = MaterialTheme.typography.bodyMedium)
        }
        var inventoryOpen by remember(project.id) { mutableStateOf(false) }
        OutlinedButton(
            onClick = { inventoryOpen = !inventoryOpen },
            shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
            modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreExportPageTags.INVENTORY },
        ) { Text(if (inventoryOpen) "Hide section inventory" else "Section inventory") }
        if (inventoryOpen) scopes.forEach { scope ->
            Text(
                "${scope.location} · ${scope.role.exportDisplayName}: ${if (scope.problem != null) "Needs attention" else if (scope.rest) "Accepted planned rest" else "Accepted"}${if (scope.locked) " · Locked" else ""}",
                modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.scope(scope.occurrenceId, scope.role) },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ExportReadinessCard(
    readiness: List<MidiCoreWorkspaceBlocker>,
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    ExportCard(MidiCoreExportPageTags.READINESS, "Export readiness") {
        Text("Only accepted work is included. Draft playback, mute and solo do not change the package. Publishing verifies the source, accepted files and MIDI alignment.", style = MaterialTheme.typography.bodyMedium)
        if (readiness.isEmpty()) {
            Text("Accepted selections are complete. File and dependency checks run on publication.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Success)
        }
        ExportBlockers((readiness + state.blockers).distinct(), state, onIntent, onNavigate)
    }
}

@Composable
private fun ExportDestinationCard(
    projectExportRoot: Path?,
    state: MidiCoreWorkspaceState,
    exporting: Boolean,
    ready: Boolean,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    ExportCard(MidiCoreExportPageTags.DESTINATION, "Package destination") {
        Text("Every export creates a new snapshot folder. Existing packages are preserved, including if publication fails.", style = MaterialTheme.typography.bodyMedium)
        Text(projectExportRoot?.toString() ?: "Project export directory will be available after opening a project.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        Text(
            "Package files: complete-song.mid, melody.mid, each active generated role file, and manifest.json. A role inactive for the whole song is recorded in the manifest and omitted.",
            modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.FILENAMES },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
        if (exporting) {
            Text(
                "Publishing, semantic re-import, and manifest validation are in progress.",
                modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.PROGRESS },
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.Information,
            )
            Text("Finishing this atomic publication; the result will appear below.", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.ExportPackage) },
                enabled = ready,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreExportPageTags.PUBLISH
                    contentDescription = "Publish a new immutable DAW MIDI package"
                },
            ) { Text("Publish MIDI package") }
            if (ready) {
                Text("A new snapshot ID is created for every successful export. To recover from a collision or transient failure, retry without replacing an existing package.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            }
        }
        if (!exporting && state.operation.retry == MidiCoreWorkspaceIntent.ExportPackage) {
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) },
                enabled = ready,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreExportPageTags.RETRY
                    contentDescription = "Retry the last MIDI package publication without overwriting an existing snapshot"
                },
            ) { Text("Retry export") }
        }
    }
}

@Composable
private fun ExportSnapshotCard(
    snapshot: MidiCoreExportSnapshot?,
    latest: MidiCoreExportedPackage?,
    directory: Path?,
    actions: MidiCoreExportPageActions,
    state: MidiCoreWorkspaceState,
) {
    ExportCard(MidiCoreExportPageTags.SNAPSHOT, "Latest immutable snapshot") {
        if (snapshot == null) {
            Text("No MIDI package has been published from this project yet.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
            return@ExportCard
        }
        Text("Published ${snapshot.createdAt}", style = MaterialTheme.typography.bodySmall)
        val current = remember(snapshot, state.project) { midiCoreExportMatchesAcceptedWork(state, snapshot) }
        Text(
            if (current) "Matches current accepted work" else "Earlier accepted work — this saved package is unchanged",
            modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.SNAPSHOT_STATUS },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Included: Melody${snapshot.enabledRoles.joinToString(prefix = if (snapshot.enabledRoles.isEmpty()) "" else ", ") { it.exportDisplayName }}", style = MaterialTheme.typography.bodySmall)
        Text("Validation: generated MIDI files passed semantic re-import before this immutable snapshot was published.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Success)
        var details by remember(snapshot.id) { mutableStateOf(false) }
        var revealError by remember(snapshot.id) { mutableStateOf<String?>(null) }
        val currentFiles = latest?.files?.associateBy { it.kind }.orEmpty()
        snapshot.files.sortedBy { it.kind }.forEach { file ->
            val generated = currentFiles[file.kind]
            Text(
                "${file.kind.exportFilename}${generated?.validation?.let { " · ${it.noteCount} notes · SMF ${it.format}" }.orEmpty()}",
                modifier = Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreExportPageTags.file(file.kind)
                    contentDescription = file.kind.exportFilename
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        directory?.let { path ->
            Text(path.toString(), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            OutlinedButton(
                onClick = {
                    revealError = runCatching { actions.revealDirectory(path) }.exceptionOrNull()?.let {
                        "Could not open the package folder. Open the saved path above in Finder."
                    }
                },
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreExportPageTags.REVEAL
                    contentDescription = "Reveal published MIDI package folder"
                },
            ) { Text("Reveal package folder") }
        }
        revealError?.let { Text(it, modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.REVEAL_ERROR }, color = MusicWorkspaceTokens.Warning) }
        OutlinedButton(
            onClick = { details = !details },
            shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
            modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics { testTag = MidiCoreExportPageTags.DETAILS },
        ) { Text(if (details) "Hide package details" else "Package details") }
        if (details) {
            Text("Snapshot: ${snapshot.id}", style = MaterialTheme.typography.bodySmall)
            Text("Source SHA-256: ${snapshot.sourceSha256}", style = MaterialTheme.typography.bodySmall)
            Text("Authority SHA-256: ${snapshot.authorityHash}", style = MaterialTheme.typography.bodySmall)
            snapshot.acceptedCandidates.forEach { candidate ->
                Text("${candidate.role} ${candidate.occurrenceId}: ${candidate.candidateId}", style = MaterialTheme.typography.bodySmall)
            }
            snapshot.files.forEach { Text("${it.kind.exportFilename}: ${it.artifact.sha256}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun MidiCoreExportHandoff() {
    ExportCard(MidiCoreExportPageTags.DAW_GUIDANCE, "Logic Pro") {
        Text("1. Import complete-song.mid at bar 1. Alternatively, import the individual role files together at that same origin; their leading silence is intentional.", style = MaterialTheme.typography.bodyMedium)
        Text("2. Confirm Logic’s tempo and meter match the package manifest. Assign instruments to the included tracks.", style = MaterialTheme.typography.bodyMedium)
        Text("3. Check section boundaries, role entries, planned rests and the final note. Play, save, close and reopen the Logic project to confirm alignment.", style = MaterialTheme.typography.bodyMedium)
        Text("Instrument hints: Melody 1 · Chords 2 · Bass 3 · GM Drums 10 (MIDI channels). Choose the final instruments and sound in Logic.", modifier = Modifier.semantics { testTag = MidiCoreExportPageTags.SUGGESTIONS }, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
    }
}

@Composable
private fun ExportBlockers(
    blockers: List<MidiCoreWorkspaceBlocker>,
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
) {
    if (blockers.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreExportPageTags.BLOCKERS
            contentDescription = "${blockers.size} export blocker${if (blockers.size == 1) "" else "s"}"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        blockers.forEach { blocker ->
            Text(blocker.message, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            Text(blocker.nextAction, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            val location = state.project?.let { runCatching { midiCoreSongMap(it) }.getOrDefault(emptyList()) }
                ?.singleOrNull { it.occurrence.id == blocker.occurrenceId }
            OutlinedButton(
                enabled = !state.busy,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
                modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget),
                onClick = {
                    blocker.occurrenceId?.let { id ->
                        onIntent(MidiCoreWorkspaceIntent.SelectArrangementOccurrence(id))
                        blocker.role?.let { onIntent(MidiCoreWorkspaceIntent.SelectReviewScope(it, id)) }
                    }
                    onNavigate(when (blocker.code) {
                        MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED -> MidiCoreWorkspaceDestination.PROJECT
                        MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED, MidiCoreWorkspaceBlockerCode.MELODY_REQUIRED -> MidiCoreWorkspaceDestination.MIDI
                        MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED, MidiCoreWorkspaceBlockerCode.STRUCTURE_REQUIRED,
                        MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED -> MidiCoreWorkspaceDestination.STRUCTURE_HARMONY
                        else -> MidiCoreWorkspaceDestination.REVIEW
                    })
                },
            ) { Text(location?.let { "Review ${it.displayLabel} · ${it.barRange} · ${blocker.role?.exportDisplayName.orEmpty()}" } ?: "Review required work") }
        }
    }
}

@Composable
private fun ExportCard(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private val ExportedFileKind.exportFilename: String
    get() = when (this) {
        ExportedFileKind.COMPLETE_SONG -> "complete-song.mid"
        ExportedFileKind.MELODY -> "melody.mid"
        ExportedFileKind.CHORDS -> "chords.mid"
        ExportedFileKind.BASS -> "bass.mid"
        ExportedFileKind.DRUMS -> "drums.mid"
        ExportedFileKind.MANIFEST -> "manifest.json"
    }
