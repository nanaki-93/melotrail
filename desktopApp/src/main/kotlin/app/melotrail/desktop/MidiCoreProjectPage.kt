package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.nio.file.Path

/** Target-only file choices used by the Project page; project JSON remains the source of truth. */
internal data class MidiCoreProjectPageActions(
    val chooseProjectDirectory: suspend () -> Path? = { null },
    val chooseNewProjectDirectory: suspend () -> Path? = { null },
)

internal object MidiCoreProjectPageTags {
    const val ROOT = "midi-core-project-page"
    const val NAME = "midi-core-project-name"
    const val LOCATION = "midi-core-project-location"
    const val CHOOSE_NEW_LOCATION = "midi-core-project-choose-new-location"
    const val CREATE = "midi-core-project-create"
    const val OPEN = "midi-core-project-open"
    const val OPEN_RECENT = "midi-core-project-open-recent"
    const val RELOAD = "midi-core-project-reload"
    const val CLOSE = "midi-core-project-close"
    const val SUMMARY = "midi-core-project-readiness"
    const val NEXT_STEP = "midi-core-project-next-step"
    const val RECOVERY = "midi-core-project-recovery"
    const val UNSUPPORTED = "midi-core-project-unsupported"
    const val RETRY = "midi-core-project-retry"
}

/** Project lifecycle and readiness page for the target shell. */
@Composable
internal fun MidiCoreProjectPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    onNavigate: (MidiCoreWorkspaceDestination) -> Unit,
    actions: MidiCoreProjectPageActions,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var projectName by remember { mutableStateOf("") }
    var newProjectRoot by remember { mutableStateOf<Path?>(null) }
    Column(
        modifier.semantics {
            testTag = MidiCoreWorkspaceShellTags.PAGE + "-project"
            contentDescription = "Project destination page"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "WORKSPACE",
            title = "Project",
            summary = "Open your song workspace and see the next safe step at a glance.",
        )
        if (state.project == null) {
            ProjectCreationCard(
                projectName = projectName,
                onProjectNameChanged = { projectName = it },
                selectedRoot = newProjectRoot,
                onChooseRoot = {
                    scope.launch { actions.chooseNewProjectDirectory().orEmptyPath()?.let { newProjectRoot = it } }
                },
                onCreate = {
                    newProjectRoot?.let { root ->
                        onIntent(MidiCoreWorkspaceIntent.CreateProject(root, projectName.trim()))
                    }
                },
                onOpen = {
                    scope.launch { actions.chooseProjectDirectory().orEmptyPath()?.let { onIntent(MidiCoreWorkspaceIntent.OpenProject(it)) } }
                },
                onOpenRecent = { onIntent(MidiCoreWorkspaceIntent.OpenLastProject) },
                busy = state.busy,
            )
        } else {
            ProjectCurrentCard(state, onIntent)
            ProjectReadinessCard(state)
            ProjectNextStepCard(state, onNavigate)
        }
        ProjectRecoveryCard(state, onIntent)
        Text(
            "Project files, imported MIDI, candidates, and export snapshots are preserved as immutable evidence. Changes are validated and saved atomically.",
            Modifier.fillMaxWidth().padding(bottom = MusicWorkspaceTokens.Spacing.Xl),
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
}

@Composable
private fun ProjectCreationCard(
    projectName: String,
    onProjectNameChanged: (String) -> Unit,
    selectedRoot: Path?,
    onChooseRoot: () -> Unit,
    onCreate: () -> Unit,
    onOpen: () -> Unit,
    onOpenRecent: () -> Unit,
    busy: Boolean,
) {
    ProjectCard(MidiCoreProjectPageTags.ROOT, "Start a MIDI Core project") {
        Text("Create a project or reopen a saved one.", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = projectName,
            onValueChange = onProjectNameChanged,
            modifier = Modifier.fillMaxWidth().semantics { testTag = MidiCoreProjectPageTags.NAME },
            label = { Text("Project name") },
            supportingText = { Text("Use a short name for the project document.") },
            singleLine = true,
            enabled = !busy,
        )
        OutlinedButton(
            onClick = onChooseRoot,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreProjectPageTags.CHOOSE_NEW_LOCATION },
        ) { Text(if (selectedRoot == null) "Choose project folder" else "Change project folder") }
        Text(
            selectedRoot?.toString() ?: "No project folder selected.",
            modifier = Modifier.fillMaxWidth().semantics {
                testTag = MidiCoreProjectPageTags.LOCATION
                contentDescription = selectedRoot?.let { "New project folder $it" } ?: "No new project folder selected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = onCreate,
            enabled = !busy && projectName.isNotBlank() && selectedRoot != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreProjectPageTags.CREATE },
        ) { Text("Create MIDI Core project") }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = onOpen,
                enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreProjectPageTags.OPEN },
            ) { Text("Open project") }
            TextButton(
                onClick = onOpenRecent,
                enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreProjectPageTags.OPEN_RECENT },
            ) { Text("Open recent") }
        }
    }
}

@Composable
private fun ProjectCurrentCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    val project = state.project ?: return
    ProjectCard(MidiCoreProjectPageTags.ROOT, "Current MIDI Core project") {
        Text(project.metadata.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            state.projectRoot?.toString() ?: "Project location unavailable.",
            Modifier.fillMaxWidth().semantics {
                testTag = MidiCoreProjectPageTags.LOCATION
                contentDescription = "Current project location ${state.projectRoot ?: "unavailable"}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Project revision ${project.revision}", style = MaterialTheme.typography.labelLarge, color = MusicWorkspaceTokens.Primary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.ReloadProject) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreProjectPageTags.RELOAD },
            ) { Text("Reload project") }
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.CloseProject) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreProjectPageTags.CLOSE },
            ) { Text("Close project") }
        }
    }
}

@Composable
private fun ProjectReadinessCard(state: MidiCoreWorkspaceState) {
    val project = state.project ?: return
    val checks = listOf(
        "MIDI source" to (project.sourceMidi != null),
        "Protected melody" to (project.selectedMelody != null),
        "Musical authority" to (project.authority != null),
        "Candidate evidence" to project.candidates.isNotEmpty(),
        "MIDI export history" to project.exportSnapshots.isNotEmpty(),
    )
    ProjectCard(MidiCoreProjectPageTags.SUMMARY, "Project readiness") {
        Text("Persisted project state determines the available workflow actions.", style = MaterialTheme.typography.bodyMedium)
        checks.forEach { (label, complete) ->
            Text(
                "${if (complete) "✓" else "○"} $label — ${if (complete) "complete" else "not started"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (complete) MusicWorkspaceTokens.Success else MusicWorkspaceTokens.TextSecondary,
            )
        }
    }
}

@Composable
private fun ProjectNextStepCard(state: MidiCoreWorkspaceState, onNavigate: (MidiCoreWorkspaceDestination) -> Unit) {
    val next = nextProjectStep(state)
    ProjectCard(MidiCoreProjectPageTags.NEXT_STEP, "Next target step") {
        Text(next.message, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { onNavigate(next.destination) },
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget),
        ) { Text("Go to ${next.destination.label}") }
    }
}

private data class ProjectNextStep(
    val destination: MidiCoreWorkspaceDestination,
    val message: String,
)

private fun nextProjectStep(state: MidiCoreWorkspaceState): ProjectNextStep {
    val blocker = state.blockers.firstOrNull()
    return when (blocker?.code) {
        MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED,
        MidiCoreWorkspaceBlockerCode.MELODY_REQUIRED,
        -> ProjectNextStep(MidiCoreWorkspaceDestination.MIDI, "Import and protect the source melody before arranging.")
        MidiCoreWorkspaceBlockerCode.AUTHORITY_REQUIRED,
        MidiCoreWorkspaceBlockerCode.STRUCTURE_REQUIRED,
        MidiCoreWorkspaceBlockerCode.HARMONY_REQUIRED,
        -> ProjectNextStep(MidiCoreWorkspaceDestination.STRUCTURE_HARMONY, "Confirm the project authority before generating candidates.")
        MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED -> ProjectNextStep(MidiCoreWorkspaceDestination.REVIEW, "Review candidate evidence before publishing an arrangement.")
        MidiCoreWorkspaceBlockerCode.EXPORT_NOT_READY -> ProjectNextStep(MidiCoreWorkspaceDestination.EXPORT, "Complete candidate review before exporting the MIDI package.")
        else -> ProjectNextStep(MidiCoreWorkspaceDestination.MIDI, "Start with the protected source melody, then continue through authority and review.")
    }
}

@Composable
private fun ProjectRecoveryCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    val blockers = state.blockers
    val unsupported = blockers.firstOrNull { it.sourceCode == "UNSUPPORTED_PROJECT" }
    if (blockers.isEmpty() && state.operation.retry == null) return
    ProjectCard(MidiCoreProjectPageTags.RECOVERY, "Project action status") {
        unsupported?.let {
            Card(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreProjectPageTags.UNSUPPORTED
                    contentDescription = "Unsupported project explanation"
                },
                colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
            ) {
                Column(Modifier.padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Text("Unsupported project schema", style = MaterialTheme.typography.titleMedium)
                    Text("This folder was not migrated or changed because it is not a current MIDI Core project.", style = MaterialTheme.typography.bodyMedium)
                    Text("Choose another folder or create a new MIDI Core project.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                }
            }
        }
        blockers.filterNot { it === unsupported }.forEach { blocker ->
            Column(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreWorkspaceShellTags.BLOCKER_PREFIX + blocker.code.name.lowercase()
                    contentDescription = "${blocker.message} Next action: ${blocker.nextAction}"
                },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
            ) {
                Text(blocker.message, style = MaterialTheme.typography.bodyMedium)
                Text("Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
                blocker.action?.let { action ->
                    TextButton(onClick = { onIntent(action) }) { Text("Take next action") }
                }
            }
        }
        state.operation.retry?.let {
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) },
                enabled = !state.busy,
                modifier = Modifier.semantics { testTag = MidiCoreProjectPageTags.RETRY },
            ) { Text("Retry project action") }
        }
    }
}

@Composable
private fun ProjectCard(tag: String, title: String, content: @Composable () -> Unit) {
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

private fun Path?.orEmptyPath(): Path? = this?.toAbsolutePath()?.normalize()
