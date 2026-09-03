package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreDerivedWorkKind
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.arrangement.core.MidiCoreInvalidationPreview
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.structure.MidiCoreHarmonyFinding
import app.melotrail.structure.MidiCoreHarmonyFindingSeverity
import app.melotrail.structure.MidiCoreHarmonyValidation
import app.melotrail.structure.MidiCoreHarmonyValidator
import app.melotrail.structure.MidiCoreOccurrenceTimeline
import java.util.Locale

/** Stable semantic anchors for the simplified Structure & Harmony workflow. */
internal object MidiCoreStructureHarmonyPageTags {
    const val ROOT = "midi-core-structure-harmony-page"
    const val AUTHORITY = "midi-core-authority"
    const val TEMPO = "midi-core-authority-tempo"
    const val METER = "midi-core-authority-meter"
    const val KEY = "midi-core-authority-key"
    const val MODE = "midi-core-authority-mode"
    const val CONFIRM_AUTHORITY = "midi-core-authority-confirm"
    const val AUTHORITY_STATUS = "midi-core-authority-status"
    const val STRUCTURE = "midi-core-structure"
    const val BAR_SUMMARY = "midi-core-structure-bar-summary"
    const val SECTION_PREFIX = "midi-core-structure-section-"
    const val SECTION_NAME_PREFIX = "midi-core-structure-section-name-"
    const val SECTION_BARS_PREFIX = "midi-core-structure-section-bars-"
    const val DUPLICATE_SECTION_PREFIX = "midi-core-structure-section-duplicate-"
    const val ADD_SECTION = "midi-core-structure-add-section"
    const val SAVE_STRUCTURE = "midi-core-structure-save"
    const val STRUCTURE_FINDINGS = "midi-core-structure-findings"
    const val HARMONY = "midi-core-harmony"
    const val PROGRESSION_PREFIX = "midi-core-harmony-progression-"
    const val USE_ONE_CHORD_PREFIX = "midi-core-harmony-use-one-chord-"
    const val SAVE_HARMONY = "midi-core-harmony-save"
    const val HARMONY_FINDINGS = "midi-core-harmony-findings"
    const val HARMONY_FINDING_PREFIX = "midi-core-harmony-finding-"
    const val INVALIDATION = "midi-core-authority-invalidation"
    const val SOURCE_AUDITION = "midi-core-authority-source-audition"
    const val OCCURRENCE_AUDITION_PREFIX = "midi-core-authority-occurrence-audition-"
    const val AUDITION = "midi-core-authority-audition"
    const val AUDITION_STATUS = "midi-core-authority-audition-status"
    const val PAUSE_AUDITION = "midi-core-authority-audition-pause"
    const val STOP_AUDITION = "midi-core-authority-audition-stop"
    const val RECOVERY = "midi-core-authority-recovery"
    const val RETRY = "midi-core-authority-retry"

    fun section(index: Int) = SECTION_PREFIX + index
    fun sectionName(index: Int) = SECTION_NAME_PREFIX + index
    fun occurrenceBars(index: Int) = SECTION_BARS_PREFIX + index
    fun duplicateSection(index: Int) = DUPLICATE_SECTION_PREFIX + index
    fun progression(index: Int) = PROGRESSION_PREFIX + index
    fun useOneChord(index: Int) = USE_ONE_CHORD_PREFIX + index
    fun finding(code: String) = HARMONY_FINDING_PREFIX + code.lowercase()
    fun occurrenceAudition(id: String) = OCCURRENCE_AUDITION_PREFIX + id
}

/** Three-step musical authority editor with no musician-facing IDs or MIDI tick arithmetic. */
@Composable
internal fun MidiCoreStructureHarmonyPage(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    modifier: Modifier,
) {
    val project = state.project
    val persistedAuthority = state.authority.confirmed ?: project?.authority
    val meter = state.authority.draft.meter
    val ppq = state.source.ppq ?: project?.sourceMidi?.ppq
    val expectedSongEndTick = state.source.sourceEndTick ?: project?.sourceMidi?.sourceEndTick
    var bpmText by remember(state.projectRevision) { mutableStateOf(formatBpmInput(state.authority.draft.tempo)) }
    var sections by remember(state.projectRevision) {
        mutableStateOf(MidiCoreAuthorityDrafting.sectionDrafts(persistedAuthority, ppq))
    }
    var progressions by remember(state.projectRevision) {
        mutableStateOf(MidiCoreAuthorityDrafting.progressionDrafts(persistedAuthority))
    }

    val parsedTempo = parseBpm(bpmText)
    val structureResult = runCatching {
        MidiCoreAuthorityDrafting.parseStructure(sections, ppq, meter, expectedSongEndTick)
    }
    val parsedStructure = structureResult.getOrNull()
    val structureError = structureResult.exceptionOrNull()?.message
    val structureDirty = parsedStructure?.let { parsed ->
        persistedAuthority == null || parsed.definitions != persistedAuthority.sectionDefinitions ||
            parsed.occurrences != persistedAuthority.occurrences || persistedAuthority.pickupTicks != 0L
    } ?: true
    val harmonyResult = persistedAuthority?.let { authority ->
        runCatching { MidiCoreAuthorityDrafting.parseHarmony(progressions, authority) }
    }
    val parsedHarmony = harmonyResult?.getOrNull()
    val harmonyError = MidiCoreAuthorityDrafting.harmonyError(progressions, persistedAuthority)
        ?: harmonyResult?.exceptionOrNull()?.message
    val harmonyValidation = if (persistedAuthority != null && parsedHarmony != null) {
        MidiCoreHarmonyValidator.validate(persistedAuthority, parsedHarmony)
    } else {
        null
    }
    val harmonyDirty = persistedAuthority != null && parsedHarmony != persistedAuthority.chordEvents
    val pendingMutation = state.authority.draftDirty || structureDirty || harmonyDirty
    val previewAuthority = if (pendingMutation) {
        buildPreviewAuthority(persistedAuthority, state.authority.draft, parsedStructure, parsedHarmony, harmonyValidation?.valid == true)
    } else {
        null
    }
    val preview = previewInvalidation(project, previewAuthority)
    val visibleInvalidation = preview?.takeIf { it.hasImpact } ?: state.authority.lastInvalidation?.takeIf { it.hasImpact }

    Column(
        modifier.semantics {
            testTag = MidiCoreStructureHarmonyPageTags.ROOT
            contentDescription = "Structure and Harmony musical editor"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Lg),
    ) {
        WorkspacePageHeading(
            eyebrow = "SONG AUTHORITY",
            title = "Structure & Harmony",
            summary = "Set the song basics, arrange named sections in bars, then write one chord progression for each section.",
        )
        if (project == null) {
            AuthorityPanel(MidiCoreStructureHarmonyPageTags.ROOT + "-empty", "Start with a project") {
                Text("Open or create a project, then import the complete melody MIDI.")
            }
        } else {
            AuthorityCard(
                state = state,
                bpmText = bpmText,
                parsedTempo = parsedTempo,
                onBpmChanged = { value ->
                    bpmText = value
                    parseBpm(value)?.let { tempo ->
                        onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(tempo = tempo)))
                    }
                },
                onMeterChanged = { selected ->
                    onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(meter = selected)))
                },
                onKeyChanged = { spelling ->
                    onIntent(
                        MidiCoreWorkspaceIntent.UpdateAuthorityDraft(
                            state.authority.draft.copy(key = ProjectKey(spelling, state.authority.draft.key.mode)),
                        ),
                    )
                },
                onModeChanged = { mode ->
                    onIntent(
                        MidiCoreWorkspaceIntent.UpdateAuthorityDraft(
                            state.authority.draft.copy(key = ProjectKey(state.authority.draft.key.spelling, mode)),
                        ),
                    )
                },
                onConfirm = { onIntent(MidiCoreWorkspaceIntent.ConfirmAuthority) },
            )
            StructureCard(
                state = state,
                sections = sections,
                ppq = ppq,
                meter = meter,
                expectedSongEndTick = expectedSongEndTick,
                structureError = structureError,
                onSectionChanged = { index, section -> sections = sections.updated(index, section) },
                onAddSection = {
                    sections = sections + MidiCoreAuthorityDrafting.nextSection(sections, expectedSongEndTick, ppq, meter)
                },
                onDuplicateSection = { index -> sections = MidiCoreAuthorityDrafting.duplicateSection(sections, index) },
                onMoveSection = { index, delta -> sections = sections.reordered(index, delta) },
                onRemoveSection = { index -> sections = sections.filterIndexed { current, _ -> current != index } },
                onSave = {
                    parsedStructure?.let { parsed ->
                        onIntent(MidiCoreWorkspaceIntent.ReplaceStructure(parsed.definitions, parsed.placements))
                    }
                },
                enabled = !state.busy && state.authority.confirmed != null && parsedStructure != null && structureDirty,
            )
            HarmonyCard(
                state = state,
                progressions = progressions,
                authority = persistedAuthority,
                ppq = ppq,
                parsedHarmony = parsedHarmony,
                validation = harmonyValidation,
                error = harmonyError,
                structureDirty = structureDirty,
                defaultChord = state.authority.draft.key.spelling.symbol,
                onProgressionChanged = { index, value ->
                    progressions = progressions.updated(index, progressions[index].copy(text = value))
                },
                onUseOneChord = { index ->
                    progressions = progressions.updated(index, progressions[index].copy(text = state.authority.draft.key.spelling.symbol))
                },
                onSave = { parsedHarmony?.let { onIntent(MidiCoreWorkspaceIntent.ReplaceHarmony(it)) } },
                enabled = !state.busy && !structureDirty && harmonyDirty && harmonyValidation?.valid == true,
            )
            InvalidationCard(visibleInvalidation, pendingMutation)
            AuditionCard(state, onIntent, persistedAuthority?.occurrences.orEmpty())
            RecoveryCard(state, onIntent)
        }
        Spacer(Modifier.height(MusicWorkspaceTokens.Spacing.Xl))
    }
}

@Composable
private fun AuthorityCard(
    state: MidiCoreWorkspaceState,
    bpmText: String,
    parsedTempo: ProjectTempo?,
    onBpmChanged: (String) -> Unit,
    onMeterChanged: (ProjectMeter) -> Unit,
    onKeyChanged: (ProjectKeySpelling) -> Unit,
    onModeChanged: (ProjectScaleMode) -> Unit,
    onConfirm: () -> Unit,
) {
    val draft = state.authority.draft
    var keyMenuOpen by remember { mutableStateOf(false) }
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.AUTHORITY, "1 · Song settings") {
        Text("Use the same tempo, meter, and key you intend to use in Logic Pro.", color = MusicWorkspaceTokens.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            OutlinedTextField(
                value = bpmText,
                onValueChange = onBpmChanged,
                modifier = Modifier.width(180.dp).semantics {
                    testTag = MidiCoreStructureHarmonyPageTags.TEMPO
                    contentDescription = "Tempo in beats per minute"
                },
                label = { Text("Tempo (BPM)") },
                supportingText = { Text(if (parsedTempo == null) "Enter a positive BPM" else "Fixed project tempo") },
                isError = parsedTempo == null,
                singleLine = true,
                enabled = !state.busy,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text("Time signature", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).semantics { testTag = MidiCoreStructureHarmonyPageTags.METER },
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
                ) {
                    commonMeters.forEach { meter ->
                        val selected = draft.meter == meter
                        OutlinedButton(
                            onClick = { onMeterChanged(meter) },
                            enabled = !state.busy,
                            colors = workspaceSelectableButtonColors(selected),
                            modifier = Modifier.semantics {
                                this.selected = selected
                                contentDescription = "${meter.numerator}/${meter.denominator} time${if (selected) ", selected" else ""}"
                            },
                        ) { Text("${meter.numerator}/${meter.denominator}") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Box {
                OutlinedButton(
                    onClick = { keyMenuOpen = true },
                    enabled = !state.busy,
                    colors = workspaceSelectableButtonColors(true),
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        testTag = MidiCoreStructureHarmonyPageTags.KEY
                        contentDescription = "Project key ${draft.key.spelling.symbol}; open key choices"
                    },
                ) { Text("Key · ${draft.key.spelling.symbol}") }
                DropdownMenu(expanded = keyMenuOpen, onDismissRequest = { keyMenuOpen = false }) {
                    commonKeys.forEach { spelling ->
                        DropdownMenuItem(
                            text = { Text(spelling.symbol) },
                            onClick = { keyMenuOpen = false; onKeyChanged(spelling) },
                        )
                    }
                }
            }
            ProjectScaleMode.entries.forEach { mode ->
                val selected = draft.key.mode == mode
                OutlinedButton(
                    onClick = { onModeChanged(mode) },
                    enabled = !state.busy,
                    colors = workspaceSelectableButtonColors(selected),
                    modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget).semantics {
                        if (mode == ProjectScaleMode.NATURAL_MINOR) testTag = MidiCoreStructureHarmonyPageTags.MODE
                        this.selected = selected
                        contentDescription = "${mode.displayName} mode${if (selected) ", selected" else ""}"
                    },
                ) { Text(mode.displayName.replaceFirstChar(Char::uppercaseChar)) }
            }
        }
        Text(
            when {
                state.authority.confirmed == null -> "Not confirmed yet"
                state.authority.draftDirty -> "Unsaved song-setting changes"
                else -> "Confirmed · ${draft.key.spelling.symbol} ${draft.key.mode.displayName} · ${draft.meter.numerator}/${draft.meter.denominator} · ${formatBpmDisplay(draft.tempo)} BPM"
            },
            Modifier.semantics {
                testTag = MidiCoreStructureHarmonyPageTags.AUTHORITY_STATUS
                contentDescription = "Song settings status"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.authority.draftDirty || state.authority.confirmed == null) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Success,
        )
        state.authority.suggestions?.let { suggestion ->
            val imported = listOfNotNull(
                suggestion.tempo?.let { "${formatBpmDisplay(it)} BPM" },
                suggestion.meter?.let { "${it.numerator}/${it.denominator}" },
            ).joinToString(" · ")
            if (imported.isNotBlank()) Text("Imported MIDI suggests $imported", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Information)
        }
        Button(
            onClick = onConfirm,
            enabled = !state.busy && parsedTempo != null && (state.authority.draftDirty || state.authority.confirmed == null),
            colors = workspacePrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.CONFIRM_AUTHORITY },
        ) { Text(if (state.authority.confirmed == null) "Confirm song settings" else "Save song settings") }
    }
}

@Composable
private fun StructureCard(
    state: MidiCoreWorkspaceState,
    sections: List<MidiCoreSectionDraft>,
    ppq: Int?,
    meter: ProjectMeter,
    expectedSongEndTick: Long?,
    structureError: String?,
    onSectionChanged: (Int, MidiCoreSectionDraft) -> Unit,
    onAddSection: () -> Unit,
    onDuplicateSection: (Int) -> Unit,
    onMoveSection: (Int, Int) -> Unit,
    onRemoveSection: (Int) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean,
) {
    val requiredBars = MidiCoreAuthorityDrafting.sourceBarCount(expectedSongEndTick, ppq, meter)
    val enteredBars = sections.sumOf { it.barsText.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.STRUCTURE, "2 · Sections") {
        Text("Build the song from top to bottom. Each row needs only a name and its length in whole bars.", color = MusicWorkspaceTokens.TextSecondary)
        Card(
            Modifier.fillMaxWidth().semantics {
                testTag = MidiCoreStructureHarmonyPageTags.BAR_SUMMARY
                contentDescription = barSummary(enteredBars, requiredBars)
            },
            colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
        ) {
            Row(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Song length", fontWeight = FontWeight.SemiBold)
                Text(barSummary(enteredBars, requiredBars), color = barSummaryColor(enteredBars, requiredBars))
            }
        }
        if (sections.isEmpty()) Text("Add the first section to begin.", color = MusicWorkspaceTokens.Warning)
        sections.forEachIndexed { index, section ->
            SectionRow(
                state = state,
                index = index,
                section = section,
                onChanged = { onSectionChanged(index, it) },
                onDuplicate = { onDuplicateSection(index) },
                onMoveEarlier = { onMoveSection(index, -1) },
                onMoveLater = { onMoveSection(index, 1) },
                onRemove = { onRemoveSection(index) },
                lastIndex = sections.lastIndex,
            )
        }
        OutlinedButton(
            onClick = onAddSection,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.ADD_SECTION },
        ) { Text("+ Add section") }
        structureError?.let { BlockingNote(MidiCoreStructureHarmonyPageTags.STRUCTURE_FINDINGS, it) }
        Button(
            onClick = onSave,
            enabled = enabled,
            colors = workspacePrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.SAVE_STRUCTURE },
        ) { Text(if (structureError == null) "Save sections" else "Match the song length to continue") }
    }
}

@Composable
private fun SectionRow(
    state: MidiCoreWorkspaceState,
    index: Int,
    section: MidiCoreSectionDraft,
    onChanged: (MidiCoreSectionDraft) -> Unit,
    onDuplicate: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    lastIndex: Int,
) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreStructureHarmonyPageTags.section(index)
            contentDescription = "Section ${index + 1}, ${section.name}, ${section.barsText} bars"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                Text("${index + 1}", color = MusicWorkspaceTokens.Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
                OutlinedTextField(
                    value = section.name,
                    onValueChange = { onChanged(section.copy(name = it, definitionName = it)) },
                    modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.sectionName(index) },
                    label = { Text("Section name") },
                    placeholder = { Text("Intro, Verse, Chorus…") },
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = section.barsText,
                    onValueChange = { onChanged(section.copy(barsText = it)) },
                    modifier = Modifier.width(120.dp).semantics { testTag = MidiCoreStructureHarmonyPageTags.occurrenceBars(index) },
                    label = { Text("Bars") },
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                OutlinedButton(onClick = onMoveEarlier, enabled = !state.busy && index > 0) { Text("↑ Earlier") }
                OutlinedButton(onClick = onMoveLater, enabled = !state.busy && index < lastIndex) { Text("↓ Later") }
                OutlinedButton(
                    onClick = onDuplicate,
                    enabled = !state.busy,
                    modifier = Modifier.semantics { testTag = MidiCoreStructureHarmonyPageTags.duplicateSection(index) },
                ) { Text("Duplicate") }
                TextButton(onClick = onRemove, enabled = !state.busy) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun HarmonyCard(
    state: MidiCoreWorkspaceState,
    progressions: List<MidiCoreProgressionDraft>,
    authority: ProjectAuthority?,
    ppq: Int?,
    parsedHarmony: List<AuthoritativeChordEvent>?,
    validation: MidiCoreHarmonyValidation?,
    error: String?,
    structureDirty: Boolean,
    defaultChord: String,
    onProgressionChanged: (Int, String) -> Unit,
    onUseOneChord: (Int) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean,
) {
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.HARMONY, "3 · Chord progressions") {
        Text(
            "Write chords in playing order, separated by |. They are spaced evenly across the section; repeat a chord to hold it longer.",
            color = MusicWorkspaceTokens.TextSecondary,
        )
        if (structureDirty) Text("Save section changes first so each progression keeps the correct range.", color = MusicWorkspaceTokens.Warning)
        progressions.forEachIndexed { index, progression ->
            val occurrence = authority?.occurrences?.singleOrNull { it.id == progression.occurrenceId }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface)) {
                Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    Text(progression.sectionName, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = progression.text,
                        onValueChange = { onProgressionChanged(index, it) },
                        modifier = Modifier.fillMaxWidth().semantics {
                            testTag = MidiCoreStructureHarmonyPageTags.progression(index)
                            contentDescription = "Chord progression for ${progression.sectionName}"
                        },
                        label = { Text("Chord progression") },
                        placeholder = { Text("C | Am | F | G") },
                        supportingText = { Text("Examples: C, Am7, F#dim, Bbmaj7, G/B") },
                        singleLine = true,
                        enabled = !state.busy && !structureDirty,
                    )
                    if (progression.text.isBlank()) {
                        TextButton(
                            onClick = { onUseOneChord(index) },
                            enabled = !state.busy && !structureDirty,
                            modifier = Modifier.semantics { testTag = MidiCoreStructureHarmonyPageTags.useOneChord(index) },
                        ) { Text("Use $defaultChord for the whole section") }
                    }
                    if (occurrence != null && parsedHarmony != null && ppq != null) {
                        val preview = parsedHarmony.filter { it.occurrenceId == occurrence.id }
                        if (preview.isNotEmpty()) {
                            Text(
                                preview.joinToString("  ·  ") { event -> "${event.symbol} ${formatMusicalRange(event, occurrence, ppq, authority.meter)}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MusicWorkspaceTokens.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        if (progressions.isEmpty()) Text("Save at least one section before adding harmony.", color = MusicWorkspaceTokens.Warning)
        error?.let { BlockingNote(MidiCoreStructureHarmonyPageTags.HARMONY + "-error", it) }
        HarmonyFindings(validation)
        Button(
            onClick = onSave,
            enabled = enabled,
            colors = workspacePrimaryButtonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.SAVE_HARMONY },
        ) { Text(if (structureDirty) "Save sections first" else "Save chord progressions") }
    }
}

@Composable
private fun HarmonyFindings(validation: MidiCoreHarmonyValidation?) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreStructureHarmonyPageTags.HARMONY_FINDINGS
            contentDescription = "Chord progression coverage and key compatibility"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            when {
                validation == null -> Text("Complete every progression to check harmony.", color = MusicWorkspaceTokens.TextSecondary)
                validation.findings.isEmpty() -> Text("✓ Every section has complete chord coverage.", color = MusicWorkspaceTokens.Success)
                else -> validation.findings.forEach { HarmonyFindingRow(it) }
            }
        }
    }
}

@Composable
private fun HarmonyFindingRow(finding: MidiCoreHarmonyFinding) {
    val blocking = finding.severity == MidiCoreHarmonyFindingSeverity.BLOCKING
    Text(
        "${if (blocking) "! Fix" else "i Review"} · ${finding.message}",
        Modifier.semantics {
            testTag = MidiCoreStructureHarmonyPageTags.finding(finding.code.name)
            contentDescription = "${finding.severity.name.lowercase()} harmony finding: ${finding.message}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (blocking) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Information,
    )
}

@Composable
private fun InvalidationCard(preview: MidiCoreInvalidationPreview?, pendingMutation: Boolean) {
    if (preview == null) return
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.INVALIDATION, "What will be refreshed") {
        Text(if (pendingMutation) "Saving these changes will mark only the affected generated work as stale." else "The last saved change marked only the affected generated work as stale.")
        val candidates = preview.staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.CANDIDATE }
        val exports = preview.staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.EXPORT }
        Text("Changes · ${preview.changedDimensions.joinToString { it.name.lowercase() }}", style = MaterialTheme.typography.bodySmall)
        Text("Candidates to regenerate · ${candidates.size}", style = MaterialTheme.typography.bodySmall)
        Text("Exports to recreate · ${exports.size}", style = MaterialTheme.typography.bodySmall)
        Text("Imported MIDI and accepted candidate files are never deleted.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Success)
    }
}

@Composable
private fun AuditionCard(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    occurrences: List<ProjectSectionOccurrence>,
) {
    val audition = state.audition
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.AUDITION, "Listen while you work") {
        Text("Preview uses the built-in MIDI synthesizer unless you select an external MIDI output.", color = MusicWorkspaceTokens.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlaySourceMelody) },
                enabled = !state.busy && state.melody.selected != null,
                colors = workspacePrimaryButtonColors(),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.SOURCE_AUDITION },
            ) { Text("▶ Play full melody") }
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PauseAudition) },
                enabled = audition.playback == MidiAuditionPlaybackState.PLAYING,
                modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.PAUSE_AUDITION },
            ) { Text("Pause") }
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.StopAudition) },
                enabled = audition.playback != MidiAuditionPlaybackState.STOPPED,
                modifier = Modifier.heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.STOP_AUDITION },
            ) { Text("Stop") }
        }
        if (occurrences.isNotEmpty()) {
            Text("Preview one saved section", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                occurrences.forEach { occurrence ->
                    OutlinedButton(
                        onClick = { onIntent(MidiCoreWorkspaceIntent.PlayOccurrence(occurrence.id)) },
                        enabled = !state.busy && state.melody.selected != null,
                        modifier = Modifier.semantics {
                            testTag = MidiCoreStructureHarmonyPageTags.occurrenceAudition(occurrence.id)
                            contentDescription = "Play melody for ${occurrence.label}"
                        },
                    ) { Text("▶ ${occurrence.label}") }
                }
            }
        }
        Text(
            auditionStatus(audition.scope, audition.playback, audition.positionTick),
            Modifier.semantics {
                testTag = MidiCoreStructureHarmonyPageTags.AUDITION_STATUS
                contentDescription = "MIDI preview status"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
}

@Composable
private fun RecoveryCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    if (state.blockers.isEmpty() && state.operation.retry == null) return
    AuthorityPanel(MidiCoreStructureHarmonyPageTags.RECOVERY, "Action needed") {
        state.blockers.forEach { blocker ->
            Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text(blocker.message)
                Text(blocker.nextAction, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            }
        }
        if (state.operation.retry != null) {
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) },
                enabled = !state.busy,
                modifier = Modifier.semantics { testTag = MidiCoreStructureHarmonyPageTags.RETRY },
            ) { Text("Retry") }
        }
    }
}

@Composable
private fun AuthorityPanel(tag: String, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Xl), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun BlockingNote(tag: String, message: String) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = tag
            contentDescription = "Action needed: $message"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
    ) {
        Text("! $message", Modifier.padding(MusicWorkspaceTokens.Spacing.Md), color = MusicWorkspaceTokens.Warning)
    }
}

private fun parseBpm(value: String): ProjectTempo? = value.trim().toDoubleOrNull()?.let { bpm ->
    runCatching { ProjectTempo.fromBeatsPerMinute(bpm) }.getOrNull()
}

private fun formatBpmInput(tempo: ProjectTempo): String {
    val bpm = tempo.beatsPerMinute
    return if (kotlin.math.abs(bpm - kotlin.math.round(bpm)) < 0.001) kotlin.math.round(bpm).toInt().toString()
    else String.format(Locale.ROOT, "%.2f", bpm).trimEnd('0').trimEnd('.')
}

private fun formatBpmDisplay(tempo: ProjectTempo): String = String.format(Locale.ROOT, "%.2f", tempo.beatsPerMinute).trimEnd('0').trimEnd('.')

private fun barSummary(entered: Int, required: Int?): String = when {
    required == null -> "$entered bars entered"
    entered == required -> "$entered / $required bars · Ready"
    entered < required -> "$entered / $required bars · ${required - entered} remaining"
    else -> "$entered / $required bars · ${entered - required} too many"
}

private fun barSummaryColor(entered: Int, required: Int?) = when {
    required != null && entered == required -> MusicWorkspaceTokens.Success
    required == null -> MusicWorkspaceTokens.TextSecondary
    else -> MusicWorkspaceTokens.Warning
}

private fun formatMusicalRange(
    event: AuthoritativeChordEvent,
    occurrence: ProjectSectionOccurrence,
    ppq: Int,
    meter: ProjectMeter,
): String {
    val ticksPerBeat = (ppq.toLong() * 4L) / meter.denominator
    if (ticksPerBeat <= 0L) return ""
    val startBeat = (event.startTick - occurrence.startTick) / ticksPerBeat
    val endBeat = (event.endTick - occurrence.startTick) / ticksPerBeat
    val startBar = startBeat / meter.numerator + 1L
    val startInBar = startBeat % meter.numerator + 1L
    val endBar = (endBeat.coerceAtLeast(1L) - 1L) / meter.numerator + 1L
    val endInBar = (endBeat.coerceAtLeast(1L) - 1L) % meter.numerator + 1L
    return "bars $startBar.$startInBar–$endBar.$endInBar"
}

private fun buildPreviewAuthority(
    persistedAuthority: ProjectAuthority?,
    draft: MidiCoreAuthorityDraft,
    parsedStructure: MidiCoreParsedStructure?,
    parsedHarmony: List<AuthoritativeChordEvent>?,
    harmonyValid: Boolean,
): ProjectAuthority? {
    val authority = persistedAuthority ?: return null
    return runCatching {
        authority.copy(
            key = draft.key,
            tempo = draft.tempo,
            meter = draft.meter,
            sectionDefinitions = parsedStructure?.definitions ?: authority.sectionDefinitions,
            occurrences = parsedStructure?.occurrences ?: authority.occurrences,
            pickupTicks = if (parsedStructure != null) 0L else authority.pickupTicks,
            chordEvents = when {
                harmonyValid && parsedHarmony != null -> parsedHarmony
                parsedStructure != null -> emptyList()
                else -> authority.chordEvents
            },
        )
    }.getOrNull()
}

private fun previewInvalidation(
    project: app.melotrail.project.MidiCoreProject?,
    updatedAuthority: ProjectAuthority?,
): MidiCoreInvalidationPreview? {
    if (project == null || updatedAuthority == null || project.authority == null) return null
    val updated = runCatching { project.copy(authority = updatedAuthority) }.getOrNull() ?: return null
    return runCatching {
        MidiCoreInvalidationPlanner.preview(
            MidiCoreAuthorityHasher.from(project),
            MidiCoreAuthorityHasher.from(updated),
            project.candidates.map { candidate ->
                MidiCoreCandidateDependency(candidate.id, candidate.role, candidate.occurrenceId, candidate.authorityHash, candidate.acceptedDependencyIds)
            },
            project.exportSnapshots.map { snapshot -> MidiCoreExportDependency(snapshot.id, snapshot.authorityHash) },
        )
    }.getOrNull()
}

private fun auditionStatus(scope: MidiAuditionScope?, playback: MidiAuditionPlaybackState, positionTick: Long): String = when (scope) {
    null -> "Ready to preview."
    MidiAuditionScope.SourceMelody -> "Full melody · ${playback.name.lowercase()} · position $positionTick"
    is MidiAuditionScope.Occurrence -> "Section preview · ${playback.name.lowercase()} · position $positionTick"
    is MidiAuditionScope.StylePreview -> "Style preview · ${playback.name.lowercase()} · position $positionTick"
    is MidiAuditionScope.Candidate -> "Candidate preview · ${playback.name.lowercase()}"
    is MidiAuditionScope.Role -> "Role preview · ${playback.name.lowercase()}"
    MidiAuditionScope.AcceptedArrangement -> "Arrangement preview · ${playback.name.lowercase()}"
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { current, item -> if (current == index) value else item }

private fun <T> List<T>.reordered(index: Int, delta: Int): List<T> {
    val destination = index + delta
    if (index !in indices || destination !in indices) return this
    return toMutableList().also { it.add(destination, it.removeAt(index)) }
}

private val commonMeters = listOf(ProjectMeter(4, 2), ProjectMeter(3, 2), ProjectMeter(6, 3))

private val commonKeys = listOf(
    ProjectKeySpelling.C,
    ProjectKeySpelling.C_SHARP,
    ProjectKeySpelling.D_FLAT,
    ProjectKeySpelling.D,
    ProjectKeySpelling.E_FLAT,
    ProjectKeySpelling.E,
    ProjectKeySpelling.F,
    ProjectKeySpelling.F_SHARP,
    ProjectKeySpelling.G_FLAT,
    ProjectKeySpelling.G,
    ProjectKeySpelling.A_FLAT,
    ProjectKeySpelling.A,
    ProjectKeySpelling.B_FLAT,
    ProjectKeySpelling.B,
)
