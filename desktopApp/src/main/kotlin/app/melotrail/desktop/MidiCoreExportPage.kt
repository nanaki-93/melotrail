package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import app.melotrail.application.MidiCoreExportedPackage
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.MidiCoreExportSnapshot
import java.nio.file.Path

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

    fun file(kind: ExportedFileKind): String = FILE_PREFIX + kind.name.lowercase()
}

/** Publish and inspect one immutable, DAW-ready MIDI package without audio-production options. */
@Composable
internal fun MidiCoreExportPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    actions: MidiCoreExportPageActions = MidiCoreExportPageActions(),
    modifier: Modifier = Modifier,
) {
    val project = state.project
    val readiness = exportReadiness(state)
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
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        if (project == null) {
            ExportEmptyCard(state)
            return@Column
        }
        ExportReadinessCard(readiness, state, onIntent)
        ExportDestinationCard(projectExportRoot, state, exporting, readiness.isEmpty(), onIntent)
        ExportSnapshotCard(snapshot, latest, snapshotDirectory, actions)
        ExportInstrumentSuggestionsCard()
        ExportDawGuidanceCard()
    }
}

@Composable
private fun ExportEmptyCard(state: MidiCoreWorkspaceState) {
    ExportCard(MidiCoreExportPageTags.EMPTY, "Export") {
        Text("Open a MIDI Core project to inspect package readiness and publish a DAW MIDI package.", style = MaterialTheme.typography.bodyLarge)
        ExportBlockers(state.blockers)
    }
}

@Composable
private fun ExportReadinessCard(
    readiness: List<String>,
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
) {
    ExportCard(MidiCoreExportPageTags.READINESS, "Export readiness") {
        Text("Publishing revalidates the protected melody, authority, accepted candidate digests, SMF semantics, and manifest before a package becomes visible.", style = MaterialTheme.typography.bodyMedium)
        if (readiness.isEmpty()) {
            Text("Ready to publish a new immutable snapshot. Existing packages will never be overwritten.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Success)
        } else {
            readiness.forEach { message -> Text(message, style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Warning) }
        }
        ExportBlockers(state.blockers)
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
        Text("New packages are written under the project-owned export directory as a fresh snapshot ID. Collision handling chooses a new snapshot; no MIDI file or prior snapshot is silently replaced.", style = MaterialTheme.typography.bodyMedium)
        Text(projectExportRoot?.toString() ?: "Project export directory will be available after opening a project.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        Text(
            "Package files: complete-song.mid, melody.mid, chords.mid, bass.mid, drums.mid, and manifest.json.",
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
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.CancelOperation) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreExportPageTags.CANCEL
                    contentDescription = "Cancel MIDI package publication"
                },
            ) { Text("Cancel export") }
        } else {
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.ExportPackage) },
                enabled = ready,
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
) {
    ExportCard(MidiCoreExportPageTags.SNAPSHOT, "Latest immutable snapshot") {
        if (snapshot == null) {
            Text("No MIDI package has been published from this project yet.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
            return@ExportCard
        }
        Text(snapshot.id, style = MaterialTheme.typography.titleMedium)
        Text("Source SHA-256: ${snapshot.sourceSha256}", style = MaterialTheme.typography.bodySmall)
        Text("Authority SHA-256: ${snapshot.authorityHash}", style = MaterialTheme.typography.bodySmall)
        Text("Enabled roles: ${snapshot.enabledRoles.joinToString { it.exportDisplayName }}", style = MaterialTheme.typography.bodySmall)
        Text("Validation: generated MIDI files passed semantic re-import before this immutable snapshot was published.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Success)
        snapshot.acceptedCandidates.forEach { candidate ->
            Text("${candidate.role} ${candidate.occurrenceId}: ${candidate.candidateId} · ${candidate.midiSha256}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        }
        val currentFiles = latest?.files?.associateBy { it.kind }.orEmpty()
        snapshot.files.sortedBy { it.kind }.forEach { file ->
            val generated = currentFiles[file.kind]
            Text(
                "${file.kind.exportFilename}: ${file.artifact.sha256}${generated?.validation?.let { " · ${it.noteCount} notes · end ${it.songEndTick}" }.orEmpty()}",
                modifier = Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreExportPageTags.file(file.kind)
                    contentDescription = "${file.kind.exportFilename}, SHA-256 ${file.artifact.sha256}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        latest?.let { Text("Manifest SHA-256: ${it.manifestSha256}", style = MaterialTheme.typography.bodySmall) }
        directory?.let { path ->
            Text(path.toString(), style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            OutlinedButton(
                onClick = { actions.revealDirectory(path) },
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                    testTag = MidiCoreExportPageTags.REVEAL
                    contentDescription = "Reveal published MIDI package folder"
                },
            ) { Text("Reveal package folder") }
        }
    }
}

@Composable
private fun ExportInstrumentSuggestionsCard() {
    ExportCard(MidiCoreExportPageTags.SUGGESTIONS, "DAW instrument suggestions") {
        Text("Melody — Lead melody: search lead, flute, or vocal guide; preserve the source melody register.", style = MaterialTheme.typography.bodyMedium)
        Text("Chords — Keys or electric piano: search electric piano, keys, or soft pad; leave the melody clear.", style = MaterialTheme.typography.bodyMedium)
        Text("Bass — Electric or acoustic bass: search bass guitar, sub bass, or upright bass; keep the low register controlled.", style = MaterialTheme.typography.bodyMedium)
        Text("Drums — GM drum kit: choose a dusty acoustic or electronic kit and retain General MIDI channel 10 mapping.", style = MaterialTheme.typography.bodyMedium)
        Text("These are DAW-side suggestions; the MIDI Core project neither selects nor owns an instrument.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
    }
}

@Composable
private fun ExportDawGuidanceCard() {
    ExportCard(MidiCoreExportPageTags.DAW_GUIDANCE, "Logic Pro") {
        Text("Logic Pro: import complete-song.mid at bar 1, confirm whether to adopt its fixed tempo and meter, then assign instruments to Melody, Chords, Bass, and Drums.", style = MaterialTheme.typography.bodyMedium)
        Text("Track names, timing, channels, and MIDI event content are package evidence. Destination instrument choices remain in the DAW.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
    }
}

@Composable
private fun ExportBlockers(blockers: List<MidiCoreWorkspaceBlocker>) {
    if (blockers.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreExportPageTags.BLOCKERS
            contentDescription = "${blockers.size} export blocker${if (blockers.size == 1) "" else "s"}"
        },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        blockers.forEach { blocker ->
            Text("${blocker.message} Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
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
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun exportReadiness(state: MidiCoreWorkspaceState): List<String> {
    val project = state.project ?: return listOf("Open a MIDI Core project before exporting.")
    val authority = project.authority ?: return listOf("Confirm complete musical authority before exporting.")
    return CandidateRole.entries.flatMap { role ->
        authority.occurrences.mapNotNull { occurrence ->
            val acceptance = project.acceptances.singleOrNull { accepted ->
                accepted.role == role && accepted.occurrenceId == occurrence.id
            } ?: return@mapNotNull "Accept one current ${role.exportDisplayName} candidate for ${occurrence.label} before exporting."
            val candidate = project.candidates.singleOrNull { candidate -> candidate.id == acceptance.candidateId }
                ?: return@mapNotNull "The accepted ${role.exportDisplayName} evidence for ${occurrence.label} is unavailable. Reload the project and accept a current candidate before exporting."
            when (candidate.status) {
                app.melotrail.project.MidiCoreCandidateStatus.ACCEPTED -> null
                app.melotrail.project.MidiCoreCandidateStatus.STALE -> "The accepted ${role.exportDisplayName} candidate for ${occurrence.label} is stale. Regenerate and explicitly accept a current candidate before exporting."
                else -> "The ${role.exportDisplayName} candidate accepted for ${occurrence.label} is no longer accepted. Review and explicitly accept a current candidate before exporting."
            }
        }
    }
}

private val CandidateRole.exportDisplayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercaseChar)

private val ExportedFileKind.exportFilename: String
    get() = when (this) {
        ExportedFileKind.COMPLETE_SONG -> "complete-song.mid"
        ExportedFileKind.MELODY -> "melody.mid"
        ExportedFileKind.CHORDS -> "chords.mid"
        ExportedFileKind.BASS -> "bass.mid"
        ExportedFileKind.DRUMS -> "drums.mid"
        ExportedFileKind.MANIFEST -> "manifest.json"
    }
