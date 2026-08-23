package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import app.melotrail.application.CompositionSettingsInput
import app.melotrail.application.CompositionSettingsOptions
import app.melotrail.application.CompositionSettingsView
import app.melotrail.application.GetCompositionSettingsResult
import app.melotrail.application.SettingsInvalidationPreview
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef

/** Presentation-only copy of the typed Task 005 decision. */
data class ProjectSetupDraft(
    val name: String,
    val tonic: PitchClass,
    val mode: ScaleModeId,
    val tempo: String,
    val timeSignature: TimeSignature,
    val profile: CompositionProfileRef,
    val mood: MoodRef
) {
    fun inputOrError(): Result<CompositionSettingsInput> = runCatching {
        require(name.isNotBlank()) { "Project name is required." }
        val bpm = tempo.toDoubleOrNull() ?: throw IllegalArgumentException("Tempo must be a number of BPM.")
        CompositionSettingsInput(name, MusicalKey(tonic, mode), Tempo(bpm), timeSignature, profile, mood)
    }
}

data class ProjectSetupUiState(
    val options: CompositionSettingsOptions? = null,
    val saved: CompositionSettingsView? = null,
    val draft: ProjectSetupDraft? = null,
    val validationError: String? = null,
    val invalidationPreview: SettingsInvalidationPreview? = null,
    val loading: Boolean = false
) {
    val isDirty: Boolean get() = saved != null && draft != saved.toDraft()
    val requiresSetup: Boolean get() = saved == null && draft != null
    fun recommendation(): String? {
        val candidate = draft ?: return null
        val meters = options?.profileMeters.orEmpty()
        return if (meters.isNotEmpty() && candidate.timeSignature !in meters) {
            "${candidate.timeSignature.displayName} is valid for this project, but the selected profile recommends ${meters.joinToString { it.displayName }}."
        } else null
    }
    companion object {
        val Empty = ProjectSetupUiState()
        fun from(result: GetCompositionSettingsResult, projectName: String): ProjectSetupUiState {
            val settings = result.settings
            val options = result.options
            val draft = settings?.toDraft() ?: ProjectSetupDraft(projectName, options.tonics.first().value, options.modes.first().value, "80", options.commonMeters.first().value, options.profiles.first().ref, options.profiles.first().defaultMood)
            return ProjectSetupUiState(options, settings, draft, result.validationError)
        }
    }
}

private fun CompositionSettingsView.toDraft() = ProjectSetupDraft(name, key.tonic, key.modeId, tempo.bpm.toString(), timeSignature, profile, mood)

@Composable
internal fun ProjectSetupContent(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val setup = state.projectSetup
    val project = state.project
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)) {
        Text("SETUP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Project setup", style = MaterialTheme.typography.headlineMedium)
        Text(if (project == null) "Open or create a project to set its musical context." else "Choose the explicit musical context used by downstream analysis and arrangement.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (project == null) return@Column
        when {
            setup.loading -> Text("Loading setup choices…", modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_LOADING })
            setup.draft == null || setup.options == null -> WorkspaceCard("Setup unavailable", WorkspacePageTags.SETUP_ERROR) { Text(setup.validationError ?: "Setup choices are unavailable. Reopen the project and try again.") }
            else -> SetupForm(state, setup, onIntent)
        }
    }
}

@Composable
private fun SetupForm(state: WorkspaceUiState, setup: ProjectSetupUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val draft = checkNotNull(setup.draft)
    val options = checkNotNull(setup.options)
    WorkspaceCard("Authoritative musical context", WorkspacePageTags.SETUP_FORM) {
        Text(
            when {
                state.operation is WorkspaceOperation.SavingProjectSetup -> "Saving…"
                setup.requiresSetup -> "Required before analysis"
                setup.isDirty -> "Unsaved changes"
                else -> "Saved"
            },
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(draft.name, { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(name = it))) }, Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.SETUP_NAME }, label = { Text("Project name") }, singleLine = true)
        SetupPicker("Tonic", draft.tonic.toString(), options.tonics.map { it.label to it.value }, WorkspacePageTags.SETUP_TONIC) { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(tonic = it))) }
        SetupPicker("Mode", draft.mode.executable?.displayName ?: draft.mode.value, options.modes.map { it.label to it.value }, WorkspacePageTags.SETUP_MODE) { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(mode = it))) }
        OutlinedTextField(draft.tempo, { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(tempo = it))) }, Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.SETUP_TEMPO }, label = { Text("BPM") }, singleLine = true)
        val meters = (options.commonMeters.map { it.value } + draft.timeSignature).distinct().sortedWith(compareBy(TimeSignature::numerator).thenBy(TimeSignature::denominator))
        SetupPicker("Time signature", draft.timeSignature.displayName, meters.map { it.displayName to it }, WorkspacePageTags.SETUP_METER) { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(timeSignature = it))) }
        SetupPicker("Lo-fi profile", options.profiles.firstOrNull { it.ref == draft.profile }?.label ?: draft.profile.id, options.profiles.map { it.label to it.ref }, WorkspacePageTags.SETUP_PROFILE) { profile -> onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(profile = profile, mood = options.profiles.first { it.ref == profile }.defaultMood))) }
        SetupPicker("Mood", options.moods.firstOrNull { it.ref == draft.mood }?.label ?: draft.mood.id, options.moods.map { it.label to it.ref }, WorkspacePageTags.SETUP_MOOD) { onIntent(WorkspaceIntent.UpdateProjectSetup(draft.copy(mood = it))) }
        options.profiles.firstOrNull { it.ref == draft.profile }?.let { Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        options.moods.firstOrNull { it.ref == draft.mood }?.let { Text("Mood: ${it.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        setup.recommendation()?.let { Text("Recommendation: $it", modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_RECOMMENDATION }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        setup.validationError?.let { Text(it, modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_ERROR }, color = MaterialTheme.colorScheme.error) }
        setup.invalidationPreview?.let { preview ->
            Text("Saving will mark these downstream stages stale: ${preview.affectedStages.joinToString { it.name.lowercase().replace('_', ' ') }}.", modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_INVALIDATION })
            if (preview.transposedHarmonySections.isNotEmpty()) {
                Text("The selected progressions will transpose to the new key: ${preview.transposedHarmonySections.joinToString { it.value.replaceFirstChar(Char::uppercase) }}.", style = MaterialTheme.typography.bodySmall)
            }
            if (preview.harmonyReplacementRequired.isNotEmpty()) {
                Text("Changing mode keeps the existing chords for review. Choose new progressions in Harmony for: ${preview.harmonyReplacementRequired.joinToString { it.value.replaceFirstChar(Char::uppercase) }}.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onIntent(WorkspaceIntent.ConfirmProjectSetupSave) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_CONFIRM }) { Text("Save and mark stale") }
        } ?: Button(onClick = { onIntent(WorkspaceIntent.SaveProjectSetup) }, enabled = !state.operation.isMutating, modifier = Modifier.semantics { testTag = WorkspacePageTags.SETUP_SAVE }) { Text(if (setup.requiresSetup) "Save setup" else "Save changes") }
        Text("Key, BPM, and meter here are authoritative. Set the section chord progressions in Harmony; processing may use them but never replaces them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.HARMONY)) }, enabled = !state.operation.isMutating) { Text("Edit authoritative harmony") }
    }
}

@Composable
private fun <T> SetupPicker(label: String, selected: String, choices: List<Pair<String, T>>, tag: String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { testTag = tag; contentDescription = "$label: $selected" }) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { (choice, value) -> DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelected(value) }) } }
    }
}
