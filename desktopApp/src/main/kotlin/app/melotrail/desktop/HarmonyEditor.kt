package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import app.melotrail.application.HarmonyView
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonyOptionModels
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.PitchClass

/** UI-only draft. Chord order and identities stay entirely in the application service. */
data class HarmonyEditorUiState(
    val view: HarmonyView? = null,
    val selectedSection: SectionTypeId = SectionTypeId.VERSE,
    val selectedEventId: ChordEventId? = null,
    val draftRoot: PitchClass = PitchClass.canonical(0),
    val draftQuality: ChordQuality = ChordQuality.MAJOR,
    val dirty: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val pendingMutation: HarmonyMutation? = null
)

sealed interface HarmonyMutation {
    data object Add : HarmonyMutation
    data object Save : HarmonyMutation
    data object Delete : HarmonyMutation
    data class Move(val earlier: Boolean) : HarmonyMutation
}

internal object HarmonyPageTags {
    const val TABS = "harmony-section-tabs"
    const val TAB_PREFIX = "harmony-section-tab-"
    const val PROGRESSION = "harmony-progression"
    const val CHORD_PREFIX = "harmony-chord-"
    const val SELECT_PREFIX = "harmony-select-"
    const val ROOT_PREFIX = "harmony-root-"
    const val QUALITY_PREFIX = "harmony-quality-"
    const val ADD = "harmony-add"
    const val SAVE = "harmony-save"
    const val DELETE = "harmony-delete"
    const val MOVE_EARLIER = "harmony-move-earlier"
    const val MOVE_LATER = "harmony-move-later"
    const val STATUS = "harmony-status"
    const val INVALIDATION = "harmony-invalidation"
    const val CONFIRM = "harmony-confirm"
    const val CANCEL = "harmony-cancel"
}

@Composable
internal fun HarmonyPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val editor = state.harmony
    val view = editor.view
    val sections = (view?.completeness?.requiredSections.orEmpty() + view?.progressions.orEmpty().map { it.sectionType })
        .distinct().ifEmpty { listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS, SectionTypeId.BRIDGE) }
    val progression = view?.progressions?.firstOrNull { it.sectionType == editor.selectedSection }
    val selected = progression?.events?.firstOrNull { it.id == editor.selectedEventId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
        Column(
            Modifier.fillMaxSize().padding(bottom = MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
        ) {
            Text("Harmony", style = MaterialTheme.typography.headlineMedium)
            Text("Complete Setup, choose Verse, Chorus, and Bridge harmony, then continue with Melody Parts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (editor.loading) Text("Loading canonical harmony…", modifier = Modifier.semantics { testTag = HarmonyPageTags.STATUS })
            if (view == null && !editor.loading) {
                Text(editor.error ?: "Save Setup before adding structured harmony.", modifier = Modifier.semantics { testTag = HarmonyPageTags.STATUS })
                return@Column
            }
            val harmonyView = requireNotNull(view)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { testTag = HarmonyPageTags.TABS }, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                sections.forEach { section ->
                    val selectedTab = section == editor.selectedSection
                    OutlinedButton(
                        onClick = { onIntent(WorkspaceIntent.SelectHarmonySection(section)) },
                        modifier = Modifier.semantics {
                            testTag = HarmonyPageTags.TAB_PREFIX + section.value
                            contentDescription = "Open ${section.value} harmony${if (selectedTab) ", selected" else ""}"
                        }
                    ) { Text(section.value.replaceFirstChar(Char::uppercase)) }
                }
            }
            HarmonyCompleteness(harmonyView, editor, onIntent)
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    HarmonyProgression(progression?.events.orEmpty(), editor, onIntent)
                    HarmonyChordEditor(selected, editor, onIntent)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md), verticalAlignment = Alignment.Top) {
                HarmonyProgression(progression?.events.orEmpty(), editor, onIntent, Modifier.weight(1.2f))
                HarmonyChordEditor(selected, editor, onIntent, Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun HarmonyCompleteness(view: HarmonyView, editor: HarmonyEditorUiState, onIntent: (WorkspaceIntent) -> Unit) = HarmonyCard(HarmonyPageTags.STATUS, "Readiness") {
    val missing = (view.completeness.missingSections + view.completeness.emptySections).distinct()
    Text(if (view.ready) "Harmony is complete. Melody Parts can use this authored context." else "Add at least one chord to: ${missing.joinToString { it.value.replaceFirstChar(Char::uppercase) }}.")
    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    view.validationErrors.forEach { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    editor.pendingMutation?.let {
        Text("This change will invalidate: AI Fix, MIDI Feel, Cohesion, Arrangement, generated MIDI, stems, dry mix, audio texture, master, release, and commercial export. Existing artifacts remain inspectable evidence.", modifier = Modifier.semantics { testTag = HarmonyPageTags.INVALIDATION })
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Button(onClick = { onIntent(WorkspaceIntent.ConfirmHarmonyMutation) }, modifier = Modifier.semantics { testTag = HarmonyPageTags.CONFIRM }) { Text("Confirm change") }
            TextButton(onClick = { onIntent(WorkspaceIntent.CancelHarmonyMutation) }, modifier = Modifier.semantics { testTag = HarmonyPageTags.CANCEL }) { Text("Keep current harmony") }
        }
    }
}

@Composable
private fun HarmonyProgression(events: List<ChordEvent>, editor: HarmonyEditorUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) = HarmonyCard(HarmonyPageTags.PROGRESSION, "${editor.selectedSection.value.replaceFirstChar(Char::uppercase)} progression", modifier) {
    if (events.isEmpty()) Text("No chords yet. Add the first structured chord.")
    events.forEachIndexed { index, event ->
        val selected = event.id == editor.selectedEventId
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectHarmonyEvent(event.id)) }, modifier = Modifier.weight(1f).semantics {
                testTag = HarmonyPageTags.SELECT_PREFIX + event.id.value
                contentDescription = "Select chord ${chordSymbol(event)}, ${event.quality.displayName}${if (selected) ", selected" else ""}"
            }) { Text(chordSymbol(event)) }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveHarmonyEvent(earlier = true)) }, enabled = selected && index > 0 && !editor.loading,
                modifier = Modifier.semantics { testTag = HarmonyPageTags.MOVE_EARLIER + "-" + event.id.value; contentDescription = "Move ${chordSymbol(event)} earlier" }) { Text("↑") }
            TextButton(onClick = { onIntent(WorkspaceIntent.MoveHarmonyEvent(earlier = false)) }, enabled = selected && index < events.lastIndex && !editor.loading,
                modifier = Modifier.semantics { testTag = HarmonyPageTags.MOVE_LATER + "-" + event.id.value; contentDescription = "Move ${chordSymbol(event)} later" }) { Text("↓") }
        }
    }
}

@Composable
private fun HarmonyChordEditor(selected: ChordEvent?, editor: HarmonyEditorUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) = HarmonyCard("harmony-editor", if (selected == null) "Add chord" else "Edit ${chordSymbol(selected)}", modifier) {
    Text(if (selected == null) "Choose tonic and quality, then add a chord." else "Edit tonic and quality. Duration, bass, inversion, and extensions are intentionally not part of this MVP.", style = MaterialTheme.typography.bodySmall)
    Text("Tonic", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        HarmonyOptionModels.roots.forEach { option ->
            OutlinedButton(onClick = { onIntent(WorkspaceIntent.SetHarmonyRoot(option.value)) }, modifier = Modifier.semantics {
                testTag = HarmonyPageTags.ROOT_PREFIX + option.label
                contentDescription = "Use tonic ${option.label}${if (option.value == editor.draftRoot) ", selected" else ""}"
            }) { Text(option.label) }
        }
    }
    Text("Quality", style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        HarmonyOptionModels.qualities.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            row.forEach { option ->
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.SetHarmonyQuality(option.value)) }, modifier = Modifier.weight(1f).semantics {
                    testTag = HarmonyPageTags.QUALITY_PREFIX + option.value.name.lowercase()
                    contentDescription = "Use ${option.label}${if (option.value == editor.draftQuality) ", selected" else ""} quality"
                }) { Text(option.label) }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } }
    }
    if (selected == null) Button(onClick = { onIntent(WorkspaceIntent.AddHarmonyEvent) }, enabled = !editor.loading,
        modifier = Modifier.fillMaxWidth().semantics { testTag = HarmonyPageTags.ADD; contentDescription = "Add ${editor.draftRoot}${editor.draftQuality.symbolSuffix} to ${editor.selectedSection.value}" }) { Text("Add chord") }
    else {
        Button(onClick = { onIntent(WorkspaceIntent.SaveHarmonyEvent) }, enabled = editor.dirty && !editor.loading,
            modifier = Modifier.fillMaxWidth().semantics { testTag = HarmonyPageTags.SAVE; contentDescription = "Save ${editor.draftRoot}${editor.draftQuality.symbolSuffix}" }) { Text("Save chord") }
        TextButton(onClick = { onIntent(WorkspaceIntent.DeleteHarmonyEvent) }, enabled = !editor.loading,
            modifier = Modifier.semantics { testTag = HarmonyPageTags.DELETE; contentDescription = "Delete ${chordSymbol(selected)}" }) { Text("Remove chord") }
    }
}

internal fun chordSymbol(event: ChordEvent): String = event.root.toString() + event.quality.symbolSuffix

@Composable
private fun HarmonyCard(tag: String, title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) = Card(
    modifier.fillMaxWidth().semantics { testTag = tag },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
) {
    Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
