package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import app.melotrail.harmony.HarmonyTemplateId
import app.melotrail.harmony.HarmonyTemplateOption
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
    data class ApplyTemplate(val templateId: HarmonyTemplateId) : HarmonyMutation
    data object Add : HarmonyMutation
    data object Save : HarmonyMutation
    data object Delete : HarmonyMutation
    data class Move(val earlier: Boolean) : HarmonyMutation
}

internal object HarmonyPageTags {
    const val TABS = "harmony-section-tabs"
    const val TAB_PREFIX = "harmony-section-tab-"
    const val PROGRESSION = "harmony-progression"
    const val TEMPLATE_PREFIX = "harmony-template-"
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

/**
 * These are authoring choices, not evidence that a melody part has already
 * been imported. A structure later decides which authored progressions become
 * required for arrangement generation.
 */
private val authorableSections = listOf(
    SectionTypeId("intro"),
    SectionTypeId.VERSE,
    SectionTypeId.CHORUS,
    SectionTypeId.BRIDGE,
    SectionTypeId("outro")
)

@Composable
internal fun HarmonyPage(state: WorkspaceUiState, onIntent: (WorkspaceIntent) -> Unit) {
    val editor = state.harmony
    val view = editor.view
    val sections = (authorableSections + view?.completeness?.requiredSections.orEmpty() + view?.progressions.orEmpty().map { it.sectionType })
        .distinct()
    val progression = view?.progressions?.firstOrNull { it.sectionType == editor.selectedSection }
    val selected = progression?.events?.firstOrNull { it.id == editor.selectedEventId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < MusicWorkspaceTokens.Reference.MediumBreakpoint
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = MusicWorkspaceTokens.Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Pages.PageGap)
        ) {
            Text("Harmony", style = MaterialTheme.typography.headlineMedium)
            Text("Authoritative project harmony. Create key-aware progressions for any song section; processing uses them and never replaces them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (view == null) {
                Text(
                    if (editor.loading) "Loading canonical harmony…" else editor.error ?: "Save Setup before adding structured harmony.",
                    modifier = Modifier.semantics { testTag = HarmonyPageTags.STATUS }
                )
                return@Column
            }
            val harmonyView = view
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { testTag = HarmonyPageTags.TABS }, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                sections.forEach { section ->
                    val selectedTab = section == editor.selectedSection
                    if (selectedTab) Button(
                        onClick = { onIntent(WorkspaceIntent.SelectHarmonySection(section)) },
                        modifier = Modifier.semantics {
                            testTag = HarmonyPageTags.TAB_PREFIX + section.value
                            contentDescription = "Open ${section.value} harmony, selected"
                        }
                    ) { Text(section.value.replaceFirstChar(Char::uppercase)) }
                    else OutlinedButton(
                        onClick = { onIntent(WorkspaceIntent.SelectHarmonySection(section)) },
                        modifier = Modifier.semantics {
                            testTag = HarmonyPageTags.TAB_PREFIX + section.value
                            contentDescription = "Open ${section.value} harmony"
                        }
                    ) { Text(section.value.replaceFirstChar(Char::uppercase)) }
                }
            }
            HarmonyCompleteness(harmonyView, editor, onIntent)
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
                    HarmonyProgression(progression?.events.orEmpty(), progression?.templateId, editor)
                    HarmonyTemplatePicker(harmonyView.templateOptions, progression?.templateId, editor, onIntent)
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md), verticalAlignment = Alignment.Top) {
                HarmonyProgression(progression?.events.orEmpty(), progression?.templateId, editor, Modifier.weight(1.2f))
                HarmonyTemplatePicker(harmonyView.templateOptions, progression?.templateId, editor, onIntent, Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun HarmonyCompleteness(view: HarmonyView, editor: HarmonyEditorUiState, onIntent: (WorkspaceIntent) -> Unit) = HarmonyCard(HarmonyPageTags.STATUS, "Readiness") {
    val missing = (view.completeness.missingSections + view.completeness.emptySections).distinct()
    Text(if (view.ready) "Harmony is complete. Melody Parts can use this authored context." else "Choose a progression for: ${missing.joinToString { it.value.replaceFirstChar(Char::uppercase) }}.")
    if (view.replacementRequiredSections.isNotEmpty()) {
        Text("Choose new ${view.key?.displayName ?: ""} progressions for: ${view.replacementRequiredSections.joinToString { it.value.replaceFirstChar(Char::uppercase) }}.", color = MaterialTheme.colorScheme.error)
    }
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
private fun HarmonyProgression(events: List<ChordEvent>, templateId: HarmonyTemplateId?, editor: HarmonyEditorUiState, modifier: Modifier = Modifier) = HarmonyCard(HarmonyPageTags.PROGRESSION, "${editor.selectedSection.value.replaceFirstChar(Char::uppercase)} progression", modifier) {
    when {
        events.isEmpty() -> Text("No progression selected yet.")
        templateId == null -> {
            Text("Legacy authored harmony", style = MaterialTheme.typography.labelLarge)
            Text(events.joinToString("  •  ") { chordSymbol(it) })
            Text("Replace it with a key-aware progression to transpose it later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Text(events.joinToString("  •  ") { chordSymbol(it) }, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HarmonyTemplatePicker(options: List<HarmonyTemplateOption>, selectedId: HarmonyTemplateId?, editor: HarmonyEditorUiState, onIntent: (WorkspaceIntent) -> Unit, modifier: Modifier = Modifier) = HarmonyCard("harmony-editor", "Choose progression", modifier) {
    Text("Each option is transposed from the Setup key and uses only diatonic chord tones.", style = MaterialTheme.typography.bodySmall)
    options.forEach { option ->
        val selected = option.id == selectedId
        val action = { onIntent(WorkspaceIntent.SelectHarmonyTemplate(option.id)) }
        if (selected) Button(onClick = action, enabled = false, modifier = Modifier.fillMaxWidth().semantics { testTag = HarmonyPageTags.TEMPLATE_PREFIX + option.id.value; contentDescription = "${option.label}, selected" }) {
            Column { Text(option.label); Text("${option.romanNumerals}  ·  ${option.chordSymbols.joinToString(" ")}", style = MaterialTheme.typography.bodySmall) }
        } else OutlinedButton(onClick = action, enabled = !editor.loading, modifier = Modifier.fillMaxWidth().semantics { testTag = HarmonyPageTags.TEMPLATE_PREFIX + option.id.value; contentDescription = "Use ${option.label}: ${option.romanNumerals}" }) {
            Column { Text(option.label); Text("${option.romanNumerals}  ·  ${option.chordSymbols.joinToString(" ")}", style = MaterialTheme.typography.bodySmall) }
        }
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
