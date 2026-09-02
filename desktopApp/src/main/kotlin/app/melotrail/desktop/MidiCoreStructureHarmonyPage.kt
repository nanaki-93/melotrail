package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.melotrail.arrangement.core.MidiCoreCandidateDependency
import app.melotrail.arrangement.core.MidiCoreDerivedWorkKind
import app.melotrail.arrangement.core.MidiCoreExportDependency
import app.melotrail.arrangement.core.MidiCoreInvalidationPreview
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
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
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.structure.MidiCoreHarmonyFinding
import app.melotrail.structure.MidiCoreHarmonyFindingSeverity
import app.melotrail.structure.MidiCoreHarmonyValidator
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import app.melotrail.structure.MidiCoreOccurrenceTimeline

/** Stable semantic anchors for the target Structure & Harmony authoring page. */
internal object MidiCoreStructureHarmonyPageTags {
    const val ROOT = "midi-core-structure-harmony-page"
    const val AUTHORITY = "midi-core-authority"
    const val TEMPO = "midi-core-authority-tempo"
    const val METER_NUMERATOR = "midi-core-authority-meter-numerator"
    const val METER_DENOMINATOR = "midi-core-authority-meter-denominator"
    const val KEY = "midi-core-authority-key"
    const val MODE = "midi-core-authority-mode"
    const val CONFIRM_AUTHORITY = "midi-core-authority-confirm"
    const val AUTHORITY_STATUS = "midi-core-authority-status"
    const val STRUCTURE = "midi-core-structure"
    const val DEFINITION_PREFIX = "midi-core-structure-definition-"
    const val DEFINITION_ID_PREFIX = "midi-core-structure-definition-id-"
    const val DEFINITION_NAME_PREFIX = "midi-core-structure-definition-name-"
    const val ADD_DEFINITION = "midi-core-structure-add-definition"
    const val OCCURRENCE_PREFIX = "midi-core-structure-occurrence-"
    const val OCCURRENCE_DEFINITION_PREFIX = "midi-core-structure-occurrence-definition-"
    const val OCCURRENCE_BARS_PREFIX = "midi-core-structure-occurrence-bars-"
    const val ADD_OCCURRENCE = "midi-core-structure-add-occurrence"
    const val SAVE_STRUCTURE = "midi-core-structure-save"
    const val STRUCTURE_FINDINGS = "midi-core-structure-findings"
    const val HARMONY = "midi-core-harmony"
    const val CHORD_PREFIX = "midi-core-harmony-chord-"
    const val CHORD_SYMBOL_PREFIX = "midi-core-harmony-chord-symbol-"
    const val CHORD_START_PREFIX = "midi-core-harmony-chord-start-"
    const val CHORD_DURATION_PREFIX = "midi-core-harmony-chord-duration-"
    const val ADD_CHORD = "midi-core-harmony-add-chord"
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

    fun definition(index: Int) = DEFINITION_PREFIX + index
    fun definitionId(index: Int) = DEFINITION_ID_PREFIX + index
    fun definitionName(index: Int) = DEFINITION_NAME_PREFIX + index
    fun occurrence(index: Int) = OCCURRENCE_PREFIX + index
    fun occurrenceDefinition(index: Int) = OCCURRENCE_DEFINITION_PREFIX + index
    fun occurrenceBars(index: Int) = OCCURRENCE_BARS_PREFIX + index
    fun chord(index: Int) = CHORD_PREFIX + index
    fun chordSymbol(index: Int) = CHORD_SYMBOL_PREFIX + index
    fun chordStart(index: Int) = CHORD_START_PREFIX + index
    fun chordDuration(index: Int) = CHORD_DURATION_PREFIX + index
    fun finding(code: String) = HARMONY_FINDING_PREFIX + code.lowercase()
    fun occurrenceAudition(id: String) = OCCURRENCE_AUDITION_PREFIX + id
}

private data class DefinitionDraft(val id: String, val name: String)

private data class OccurrenceDraft(
    val id: String,
    val definitionId: String,
    val label: String,
    val barCountText: String,
)

private data class ChordDraft(
    val id: String,
    val occurrenceId: String,
    val symbol: String,
    val startTickText: String,
    val durationTicksText: String,
)

private data class ParsedStructure(
    val definitions: List<ProjectSectionDefinition>,
    val placements: List<MidiCoreBarOccurrencePlacement>,
    val occurrences: List<ProjectSectionOccurrence>,
)

private data class ParsedHarmony(val events: List<AuthoritativeChordEvent>)

/** Authoritative tempo, meter, key, mode, section, and chord editing surface. */
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
    var tempoText by remember(state.projectRevision) {
        mutableStateOf(state.authority.draft.tempo.microsecondsPerQuarter.toString())
    }
    var numeratorText by remember(state.projectRevision) {
        mutableStateOf(state.authority.draft.meter.numerator.toString())
    }
    var denominatorText by remember(state.projectRevision) {
        mutableStateOf(state.authority.draft.meter.denominator.toString())
    }
    var definitions by remember(state.projectRevision) {
        mutableStateOf(persistedAuthority?.sectionDefinitions.orEmpty().map { DefinitionDraft(it.id, it.name) })
    }
    var occurrences by remember(state.projectRevision) {
        mutableStateOf(persistedAuthority?.occurrences.orEmpty().map { occurrence ->
            OccurrenceDraft(
                occurrence.id,
                occurrence.definitionId,
                occurrence.label,
                occurrenceBarCount(occurrence, ppq, persistedAuthority?.meter ?: meter)?.toString().orEmpty(),
            )
        })
    }
    var chords by remember(state.projectRevision) {
        mutableStateOf(persistedAuthority?.chordEvents.orEmpty().map { event ->
            ChordDraft(
                event.id,
                event.occurrenceId,
                event.symbol,
                event.startTick.toString(),
                (event.endTick - event.startTick).toString(),
            )
        })
    }

    val parsedStructure = parseStructure(definitions, occurrences, ppq, meter, expectedSongEndTick)
    val structureDirty = parsedStructure?.let { parsed ->
        persistedAuthority == null ||
            parsed.definitions != persistedAuthority.sectionDefinitions ||
            parsed.occurrences != persistedAuthority.occurrences || persistedAuthority.pickupTicks != 0L
    } ?: true
    val parsedHarmony = parseHarmony(chords)
    val harmonyAuthority = parsedStructure?.let { parsed ->
        persistedAuthority?.copy(
            sectionDefinitions = parsed.definitions,
            occurrences = parsed.occurrences,
            pickupTicks = 0L,
            chordEvents = emptyList(),
        )
    } ?: persistedAuthority
    val harmonyValidation = if (harmonyAuthority != null && parsedHarmony != null) {
        runCatching { MidiCoreHarmonyValidator.validate(harmonyAuthority, parsedHarmony.events) }.getOrNull()
    } else {
        null
    }
    val harmonyDirty = persistedAuthority != null && parsedHarmony?.events != persistedAuthority.chordEvents
    val pendingMutation = state.authority.draftDirty || structureDirty || harmonyDirty
    val authorityForPreview = if (pendingMutation) {
        buildPreviewAuthority(
            persistedAuthority = persistedAuthority,
            draft = state.authority.draft,
            parsedStructure = parsedStructure,
            parsedHarmony = parsedHarmony,
            harmonyValid = harmonyValidation?.valid == true,
        )
    } else {
        null
    }
    val preview = previewInvalidation(project, authorityForPreview)
    val visibleInvalidation = preview?.takeIf { it.hasImpact } ?: state.authority.lastInvalidation?.takeIf { it.hasImpact }

    Column(
        modifier.semantics {
            testTag = MidiCoreStructureHarmonyPageTags.ROOT
            contentDescription = "Structure and Harmony authority page"
        }.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        if (project == null) {
            MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.ROOT + "-empty", "Structure & Harmony") {
                Text("Open or create a MIDI Core project before authoring generation authority.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            AuthorityCard(
                state = state,
                tempoText = tempoText,
                numeratorText = numeratorText,
                denominatorText = denominatorText,
                onTempoChanged = { value ->
                    tempoText = value
                    value.toIntOrNull()?.takeIf { it in 1..0xFF_FF_FF }?.let {
                        onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(tempo = ProjectTempo(it))))
                    }
                },
                onNumeratorChanged = { value ->
                    numeratorText = value
                    value.toIntOrNull()?.takeIf { it in 1..255 }?.let {
                        onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(meter = ProjectMeter(it, state.authority.draft.meter.denominatorExponent))))
                    }
                },
                onDenominatorChanged = { value ->
                    denominatorText = value
                    denominatorExponent(value)?.let {
                        onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(meter = ProjectMeter(state.authority.draft.meter.numerator, it))))
                    }
                },
                onKeyChanged = { spelling ->
                    onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(key = ProjectKey(spelling, state.authority.draft.key.mode))))
                },
                onModeChanged = {
                    val mode = if (state.authority.draft.key.mode == ProjectScaleMode.MAJOR) ProjectScaleMode.NATURAL_MINOR else ProjectScaleMode.MAJOR
                    onIntent(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(state.authority.draft.copy(key = ProjectKey(state.authority.draft.key.spelling, mode))))
                },
                onConfirm = { onIntent(MidiCoreWorkspaceIntent.ConfirmAuthority) },
                fieldsValid = tempoText.toIntOrNull()?.let { it in 1..0xFF_FF_FF } == true &&
                    numeratorText.toIntOrNull()?.let { it in 1..255 } == true && denominatorExponent(denominatorText) != null,
            )
            StructureCard(
                state = state,
                definitions = definitions,
                occurrences = occurrences,
                ppq = ppq,
                meter = meter,
                expectedSongEndTick = expectedSongEndTick,
                structureError = structureError(definitions, occurrences, ppq, meter, expectedSongEndTick),
                onDefinitionChanged = { index, value -> definitions = definitions.updated(index, value) },
                onOccurrenceChanged = { index, value -> occurrences = occurrences.updated(index, value) },
                onAddDefinition = {
                    val id = nextSafeId("section", definitions.map(DefinitionDraft::id).toSet())
                    definitions = definitions + DefinitionDraft(id, "Section ${definitions.size + 1}")
                },
                onAddOccurrence = {
                    val definition = definitions.firstOrNull()
                    if (definition != null) {
                        val bars = defaultOccurrenceBars(occurrences, expectedSongEndTick, ppq, meter)
                        val id = nextSafeId("occurrence", occurrences.map(OccurrenceDraft::id).toSet())
                        occurrences = occurrences + OccurrenceDraft(id, definition.id, definition.name, bars.toString())
                    }
                },
                onMoveOccurrence = { index, delta -> occurrences = occurrences.reorderedOccurrence(index, delta) },
                onRemoveOccurrence = { index -> occurrences = occurrences.filterIndexed { current, _ -> current != index } },
                onSave = {
                    parseStructure(definitions, occurrences, ppq, meter, expectedSongEndTick)?.let { parsed ->
                        onIntent(
                            MidiCoreWorkspaceIntent.ReplaceStructure(
                                parsed.definitions,
                                parsed.placements,
                            ),
                        )
                    }
                },
                enabled = !state.busy && state.authority.confirmed != null && parsedStructure != null,
            )
            HarmonyCard(
                state = state,
                chords = chords,
                occurrences = parsedStructure?.occurrences.orEmpty().ifEmpty { persistedAuthority?.occurrences.orEmpty() },
                ppq = ppq,
                meter = meter,
                validation = harmonyValidation,
                parseError = if (parsedHarmony == null) "Every chord ID, occurrence, start tick, and duration must be valid." else null,
                structureDirty = structureDirty,
                onChordChanged = { index, value -> chords = chords.updated(index, value) },
                onAddChord = {
                    val occurrence = (parsedStructure?.occurrences.orEmpty().ifEmpty { persistedAuthority?.occurrences.orEmpty() }).firstOrNull()
                    if (occurrence != null) {
                        val id = nextSafeId("chord", chords.map(ChordDraft::id).toSet())
                        chords = chords + ChordDraft(
                            id,
                            occurrence.id,
                            "C",
                            occurrence.startTick.toString(),
                            (occurrence.endTick - occurrence.startTick).toString(),
                        )
                    }
                },
                onRemoveChord = { index -> chords = chords.filterIndexed { current, _ -> current != index } },
                onSave = {
                    if (parsedHarmony != null && harmonyValidation?.valid == true) {
                        onIntent(MidiCoreWorkspaceIntent.ReplaceHarmony(parsedHarmony.events))
                    }
                },
                enabled = !state.busy && state.authority.confirmed != null && !structureDirty && parsedHarmony != null && harmonyValidation?.valid == true,
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
    tempoText: String,
    numeratorText: String,
    denominatorText: String,
    onTempoChanged: (String) -> Unit,
    onNumeratorChanged: (String) -> Unit,
    onDenominatorChanged: (String) -> Unit,
    onKeyChanged: (ProjectKeySpelling) -> Unit,
    onModeChanged: () -> Unit,
    onConfirm: () -> Unit,
    fieldsValid: Boolean,
) {
    val draft = state.authority.draft
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.AUTHORITY, "Musical authority") {
        Text("These values are the explicit generation authority. Imported suggestions remain advisory until confirmed.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedTextField(
                value = tempoText,
                onValueChange = onTempoChanged,
                modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.TEMPO },
                label = { Text("Tempo µs/qn") },
                supportingText = { Text("Fixed Standard MIDI tempo · ${"%.2f".format(java.util.Locale.ROOT, draft.tempo.beatsPerMinute)} BPM") },
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = numeratorText,
                onValueChange = onNumeratorChanged,
                modifier = Modifier.widthIn(min = 100.dp).semantics { testTag = MidiCoreStructureHarmonyPageTags.METER_NUMERATOR },
                label = { Text("Beats") },
                supportingText = { Text("Meter numerator") },
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = denominatorText,
                onValueChange = onDenominatorChanged,
                modifier = Modifier.widthIn(min = 120.dp).semantics { testTag = MidiCoreStructureHarmonyPageTags.METER_DENOMINATOR },
                label = { Text("Beat value") },
                supportingText = { Text("Power of two") },
                singleLine = true,
                enabled = !state.busy,
            )
        }
        Text("Key and mode are written choices, not corrections inferred from the source.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = { onKeyChanged(nextSpelling(draft.key.spelling)) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics {
                        testTag = MidiCoreStructureHarmonyPageTags.KEY
                        contentDescription = "Key ${draft.key.spelling.symbol}; choose the next written tonic"
                    },
            ) { Text("Key: ${draft.key.spelling.symbol}") }
            OutlinedButton(
                onClick = onModeChanged,
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics {
                        testTag = MidiCoreStructureHarmonyPageTags.MODE
                        contentDescription = "Mode ${draft.key.mode.displayName}; toggle major or natural minor"
                    },
            ) { Text("Mode: ${draft.key.mode.displayName}") }
        }
        Text(
            if (state.authority.confirmed == null) "Draft authority is not confirmed."
            else if (state.authority.draftDirty) "Confirmed authority exists, but these edits are still a draft."
            else "Confirmed: ${draft.key.spelling.symbol} ${draft.key.mode.displayName}, ${draft.meter.numerator}/${draft.meter.denominator}, ${"%.2f".format(java.util.Locale.ROOT, draft.tempo.beatsPerMinute)} BPM.",
            Modifier.semantics {
                testTag = MidiCoreStructureHarmonyPageTags.AUTHORITY_STATUS
                contentDescription = "Authority status"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.authority.draftDirty || state.authority.confirmed == null) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Success,
        )
        state.authority.suggestions?.let { suggestions ->
            val suggestionText = listOfNotNull(
                suggestions.tempo?.let { "tempo ${"%.2f".format(java.util.Locale.ROOT, it.beatsPerMinute)} BPM" },
                suggestions.meter?.let { "meter ${it.numerator}/${it.denominator}" },
            ).joinToString(" · ")
            if (suggestionText.isNotBlank()) Text("Imported suggestion (advisory): $suggestionText", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Information)
        }
        Button(
            onClick = onConfirm,
            enabled = !state.busy && fieldsValid,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.CONFIRM_AUTHORITY },
        ) { Text(if (state.authority.draftDirty || state.authority.confirmed == null) "Confirm musical authority" else "Authority already confirmed") }
    }
}

@Composable
private fun StructureCard(
    state: MidiCoreWorkspaceState,
    definitions: List<DefinitionDraft>,
    occurrences: List<OccurrenceDraft>,
    ppq: Int?,
    meter: ProjectMeter,
    expectedSongEndTick: Long?,
    structureError: String?,
    onDefinitionChanged: (Int, DefinitionDraft) -> Unit,
    onOccurrenceChanged: (Int, OccurrenceDraft) -> Unit,
    onAddDefinition: () -> Unit,
    onAddOccurrence: () -> Unit,
    onMoveOccurrence: (Int, Int) -> Unit,
    onRemoveOccurrence: (Int) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean,
) {
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.STRUCTURE, "Section structure") {
        Text("Define the song in ordered sections and enter each length in whole bars. The total must exactly match the imported melody.", style = MaterialTheme.typography.bodyMedium)
        Text("Section definitions", style = MaterialTheme.typography.titleMedium)
        definitions.forEachIndexed { index, definition ->
            Card(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreStructureHarmonyPageTags.definition(index)
                    contentDescription = "Section definition ${index + 1}"
                },
                colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                ) {
                    OutlinedTextField(
                        value = definition.id,
                        onValueChange = { onDefinitionChanged(index, definition.copy(id = it)) },
                        modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.definitionId(index) },
                        label = { Text("Stable ID") },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                    OutlinedTextField(
                        value = definition.name,
                        onValueChange = { onDefinitionChanged(index, definition.copy(name = it)) },
                        modifier = Modifier.weight(1.5f).semantics { testTag = MidiCoreStructureHarmonyPageTags.definitionName(index) },
                        label = { Text("Display name") },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = onAddDefinition,
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.ADD_DEFINITION },
            ) { Text("Add section definition") }
            OutlinedButton(
                onClick = onAddOccurrence,
                enabled = !state.busy && definitions.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.ADD_OCCURRENCE },
            ) { Text("Add occurrence") }
        }
        Text("Occurrence timeline", style = MaterialTheme.typography.titleMedium)
        if (occurrences.isEmpty()) {
            Text("No occurrences yet. Add at least one occurrence to author the song range.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
        }
        occurrences.forEachIndexed { index, occurrence ->
            OccurrenceRow(
                state = state,
                index = index,
                occurrence = occurrence,
                definitions = definitions,
                ppq = ppq,
                meter = meter,
                onChanged = { onOccurrenceChanged(index, it) },
                onMoveEarlier = { onMoveOccurrence(index, -1) },
                onMoveLater = { onMoveOccurrence(index, 1) },
                onRemove = { onRemoveOccurrence(index) },
            )
        }
        Text(
            sourceBarCount(expectedSongEndTick, ppq, meter)?.let { "Imported melody length: $it bars." }
                ?: expectedSongEndTick?.let { "The source ends at tick $it, which is not a whole bar in the confirmed meter." }
                ?: "Import a source MIDI to show the required bar total.",
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
        structureError?.let {
            Card(
                Modifier.fillMaxWidth().semantics {
                    testTag = MidiCoreStructureHarmonyPageTags.STRUCTURE_FINDINGS
                    contentDescription = "Blocking structure finding: $it"
                },
                colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
            ) { Text(it, Modifier.padding(MusicWorkspaceTokens.Spacing.Md), style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Warning) }
        }
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                .semantics { testTag = MidiCoreStructureHarmonyPageTags.SAVE_STRUCTURE },
        ) { Text(if (structureError == null) "Save section structure" else "Correct structure before saving") }
    }
}

@Composable
private fun OccurrenceRow(
    state: MidiCoreWorkspaceState,
    index: Int,
    occurrence: OccurrenceDraft,
    definitions: List<DefinitionDraft>,
    ppq: Int?,
    meter: ProjectMeter,
    onChanged: (OccurrenceDraft) -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
) {
    val bars = occurrence.barCountText.toIntOrNull()
    val barTicks = if (ppq != null) runCatching { MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(ppq), meter) }.getOrNull() else null
    val durationTicks = if (bars != null && bars > 0 && barTicks != null) {
        runCatching { Math.multiplyExact(bars.toLong(), barTicks) }.getOrNull()
    } else {
        null
    }
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreStructureHarmonyPageTags.occurrence(index)
            contentDescription = "Section occurrence ${occurrence.id}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedTextField(
                    value = occurrence.id,
                    onValueChange = { onChanged(occurrence.copy(id = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Occurrence ID") },
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = occurrence.definitionId,
                    onValueChange = { onChanged(occurrence.copy(definitionId = it)) },
                    modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.occurrenceDefinition(index) },
                    label = { Text("Definition ID") },
                    supportingText = { Text(definitions.joinToString(", ") { it.id }.ifBlank { "No definitions" }) },
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            OutlinedTextField(
                value = occurrence.label,
                onValueChange = { onChanged(occurrence.copy(label = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Occurrence label") },
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = occurrence.barCountText,
                onValueChange = { onChanged(occurrence.copy(barCountText = it)) },
                modifier = Modifier.fillMaxWidth().semantics { testTag = MidiCoreStructureHarmonyPageTags.occurrenceBars(index) },
                label = { Text("Length in bars") },
                supportingText = { Text("Positive whole bars, for example 8 or 12.") },
                singleLine = true,
                enabled = !state.busy,
            )
            Text(
                if (durationTicks != null) {
                    "$bars bars · $durationTicks ticks"
                } else {
                    "Enter a positive whole number of bars."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                OutlinedButton(onClick = onMoveEarlier, enabled = !state.busy && index > 0, modifier = Modifier.weight(1f)) { Text("Move earlier") }
                OutlinedButton(onClick = onMoveLater, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Move later") }
                TextButton(onClick = onRemove, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun HarmonyCard(
    state: MidiCoreWorkspaceState,
    chords: List<ChordDraft>,
    occurrences: List<ProjectSectionOccurrence>,
    ppq: Int?,
    meter: ProjectMeter,
    validation: app.melotrail.structure.MidiCoreHarmonyValidation?,
    parseError: String?,
    structureDirty: Boolean,
    onChordChanged: (Int, ChordDraft) -> Unit,
    onAddChord: () -> Unit,
    onRemoveChord: (Int) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean,
) {
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.HARMONY, "Authoritative harmony") {
        Text("Enter explicit chord windows with exact durations. Chromatic chords remain authoritative and are reported as advisories when outside the chosen key.", style = MaterialTheme.typography.bodyMedium)
        if (structureDirty) Text("Save the section structure before saving harmony for its new occurrence IDs and boundaries.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
        if (occurrences.isEmpty()) Text("Add and save section occurrences before entering harmony.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
        chords.forEachIndexed { index, chord ->
            ChordRow(state, index, chord, occurrences, ppq, meter, { value -> onChordChanged(index, value) }, { onRemoveChord(index) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            OutlinedButton(
                onClick = onAddChord,
                enabled = !state.busy && occurrences.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.ADD_CHORD },
            ) { Text("Add chord window") }
            Button(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.SAVE_HARMONY },
            ) { Text(if (structureDirty) "Save structure first" else "Save authoritative harmony") }
        }
        parseError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning) }
        HarmonyFindings(validation)
    }
}

@Composable
private fun ChordRow(
    state: MidiCoreWorkspaceState,
    index: Int,
    chord: ChordDraft,
    occurrences: List<ProjectSectionOccurrence>,
    ppq: Int?,
    meter: ProjectMeter,
    onChanged: (ChordDraft) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val start = chord.startTickText.toLongOrNull()
    val duration = chord.durationTicksText.toLongOrNull()
    val end = if (start != null && duration != null && start >= 0 && duration > 0) start + duration else null
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreStructureHarmonyPageTags.chord(index)
            contentDescription = "Chord window ${chord.id}"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.ElevatedSurface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedTextField(
                    value = chord.id,
                    onValueChange = { onChanged(chord.copy(id = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Chord ID") },
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = chord.occurrenceId,
                    onValueChange = { onChanged(chord.copy(occurrenceId = it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Occurrence ID") },
                    supportingText = { Text(occurrences.joinToString(", ") { it.id }.ifBlank { "No occurrences" }) },
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                OutlinedTextField(
                    value = chord.symbol,
                    onValueChange = { onChanged(chord.copy(symbol = it)) },
                    modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.chordSymbol(index) },
                    label = { Text("Chord symbol") },
                    supportingText = { Text("Supported MIDI Core spelling, e.g. C or Dbmaj9/F") },
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = chord.startTickText,
                    onValueChange = { onChanged(chord.copy(startTickText = it)) },
                    modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.chordStart(index) },
                    label = { Text("Start tick") },
                    singleLine = true,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = chord.durationTicksText,
                    onValueChange = { onChanged(chord.copy(durationTicksText = it)) },
                    modifier = Modifier.weight(1f).semantics { testTag = MidiCoreStructureHarmonyPageTags.chordDuration(index) },
                    label = { Text("Duration ticks") },
                    singleLine = true,
                    enabled = !state.busy,
                )
            }
            Text(
                if (start != null && end != null && ppq != null) {
                    "Exact range: ${formatPosition(start, ppq, meter)} → ${formatPosition(end, ppq, meter)} · $start–$end ticks"
                } else {
                    "Enter a positive duration inside its occurrence for exact timing feedback."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MusicWorkspaceTokens.TextSecondary,
            )
            TextButton(onClick = { onRemove(index) }, enabled = !state.busy) { Text("Remove chord window") }
        }
    }
}

@Composable
private fun HarmonyFindings(validation: app.melotrail.structure.MidiCoreHarmonyValidation?) {
    Card(
        Modifier.fillMaxWidth().semantics {
            testTag = MidiCoreStructureHarmonyPageTags.HARMONY_FINDINGS
            contentDescription = "Authoritative harmony coverage and key compatibility findings"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.DisabledSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text("Coverage and findings", style = MaterialTheme.typography.titleMedium)
            if (validation == null) {
                Text("Enter valid chord fields to calculate coverage.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            } else if (validation.findings.isEmpty()) {
                Text("Every occurrence has exact, gap-free chord coverage.", style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.Success)
            } else {
                validation.findings.forEach { finding ->
                    HarmonyFindingRow(finding)
                }
            }
        }
    }
}

@Composable
private fun HarmonyFindingRow(finding: MidiCoreHarmonyFinding) {
    val color = if (finding.severity == MidiCoreHarmonyFindingSeverity.BLOCKING) MusicWorkspaceTokens.Warning else MusicWorkspaceTokens.Information
    Text(
        "${finding.severity.name.lowercase().replaceFirstChar(Char::uppercaseChar)} · ${finding.code.name}: ${finding.message} Action: ${finding.action}",
        Modifier.semantics {
            testTag = MidiCoreStructureHarmonyPageTags.finding(finding.code.name)
            contentDescription = "${finding.severity.name.lowercase()} harmony finding ${finding.code.name}: ${finding.message}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun InvalidationCard(preview: MidiCoreInvalidationPreview?, pendingMutation: Boolean) {
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.INVALIDATION, "Authority change preview") {
        if (preview == null) {
            Text("Confirm authority and save valid structure or harmony to preview affected derived work.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                if (pendingMutation) "Before saving, this explicit authority change will mark the following derived work stale."
                else "Last saved authority change marked the following derived work stale.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Changed dimensions: ${preview.changedDimensions.joinToString { it.name.lowercase() }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Affected occurrence scopes: ${preview.affectedScopes.joinToString { it.occurrenceId + "/" + it.role.name.lowercase() }.ifBlank { "none" }}",
                style = MaterialTheme.typography.bodySmall,
            )
            val candidates = preview.staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.CANDIDATE }
            val exports = preview.staleTargets.filter { it.kind == MidiCoreDerivedWorkKind.EXPORT }
            Text("Stale candidates: ${candidates.joinToString { it.id }.ifBlank { "none" }}", style = MaterialTheme.typography.bodySmall)
            Text("Stale exports: ${exports.joinToString { it.id }.ifBlank { "none" }}", style = MaterialTheme.typography.bodySmall)
            Text("No imported MIDI, accepted candidate, or export file is deleted by this change.", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Success)
        }
    }
}

@Composable
private fun AuditionCard(
    state: MidiCoreWorkspaceState,
    onIntent: (MidiCoreWorkspaceIntent) -> Unit,
    occurrences: List<ProjectSectionOccurrence>,
) {
    val audition = state.audition
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.AUDITION, "MIDI authority audition") {
        Text("Audition uses the protected MIDI melody and exact occurrence windows on the selected local MIDI output.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Button(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlaySourceMelody) },
                enabled = !state.busy && state.melody.selected != null,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.SOURCE_AUDITION },
            ) { Text("Play source melody") }
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PauseAudition) },
                enabled = audition.playback == MidiAuditionPlaybackState.PLAYING,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.PAUSE_AUDITION },
            ) { Text("Pause") }
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.StopAudition) },
                enabled = audition.playback != MidiAuditionPlaybackState.STOPPED,
                modifier = Modifier.weight(1f).heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics { testTag = MidiCoreStructureHarmonyPageTags.STOP_AUDITION },
            ) { Text("Stop") }
        }
        occurrences.forEach { occurrence ->
            OutlinedButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.PlayOccurrence(occurrence.id)) },
                enabled = !state.busy && state.melody.selected != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
                    .semantics {
                        testTag = MidiCoreStructureHarmonyPageTags.occurrenceAudition(occurrence.id)
                        contentDescription = "Play protected melody for occurrence ${occurrence.label}"
                    },
            ) { Text("Play occurrence: ${occurrence.label}") }
        }
        Text(
            auditionStatus(audition.scope, audition.playback, audition.positionTick),
            Modifier.semantics {
                testTag = MidiCoreStructureHarmonyPageTags.AUDITION_STATUS
                contentDescription = "MIDI authority audition status"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MusicWorkspaceTokens.TextSecondary,
        )
    }
}

@Composable
private fun RecoveryCard(state: MidiCoreWorkspaceState, onIntent: (MidiCoreWorkspaceIntent) -> Unit) {
    if (state.blockers.isEmpty() && state.operation.retry == null) return
    MidiCoreStructureHarmonyCard(MidiCoreStructureHarmonyPageTags.RECOVERY, "Authority action status") {
        state.blockers.forEach { blocker ->
            Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                Text(blocker.message, style = MaterialTheme.typography.bodyMedium)
                Text("Next: ${blocker.nextAction}", style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.Warning)
            }
        }
        if (state.operation.retry != null) {
            TextButton(
                onClick = { onIntent(MidiCoreWorkspaceIntent.Retry) },
                enabled = !state.busy,
                modifier = Modifier.semantics { testTag = MidiCoreStructureHarmonyPageTags.RETRY },
            ) { Text("Retry authority action") }
        }
    }
}

@Composable
private fun MidiCoreStructureHarmonyCard(tag: String, title: String, content: @Composable () -> Unit) {
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

private fun parseStructure(
    definitions: List<DefinitionDraft>,
    occurrences: List<OccurrenceDraft>,
    ppq: Int?,
    meter: ProjectMeter,
    expectedSongEndTick: Long?,
): ParsedStructure? {
    val parsed = runCatching {
        require(definitions.isNotEmpty()) { "Add at least one section definition." }
        val projectDefinitions = definitions.map { ProjectSectionDefinition(it.id.trim(), it.name.trim()) }
        val placements = occurrences.map { occurrence ->
            val bars = occurrence.barCountText.toIntOrNull() ?: error("Occurrence ${occurrence.id} bar count is not a whole number.")
            MidiCoreBarOccurrencePlacement(occurrence.id.trim(), occurrence.definitionId.trim(), occurrence.label.trim(), bars)
        }
        require(placements.isNotEmpty()) { "Add at least one section occurrence." }
        val resolved = if (ppq == null) {
            error("Import a source MIDI before validating the exact project timeline.")
        } else {
            MidiCoreOccurrenceTimeline.buildFromBars(MidiPpq(ppq), meter, projectDefinitions, placements, requireNotNull(expectedSongEndTick))
        }
        ParsedStructure(projectDefinitions, placements, resolved.occurrences)
    }.getOrNull()
    return parsed
}

private fun structureError(
    definitions: List<DefinitionDraft>,
    occurrences: List<OccurrenceDraft>,
    ppq: Int?,
    meter: ProjectMeter,
    expectedSongEndTick: Long?,
): String? {
    val result = runCatching {
        require(definitions.isNotEmpty()) { "Add at least one section definition." }
        val projectDefinitions = definitions.map { ProjectSectionDefinition(it.id.trim(), it.name.trim()) }
        val placements = occurrences.map { occurrence ->
            val bars = occurrence.barCountText.toIntOrNull() ?: error("Occurrence ${occurrence.id} bar count is not a whole number.")
            MidiCoreBarOccurrencePlacement(occurrence.id.trim(), occurrence.definitionId.trim(), occurrence.label.trim(), bars)
        }
        require(placements.isNotEmpty()) { "Add at least one section occurrence." }
        requireNotNull(ppq) { "Import a source MIDI before validating the exact project timeline." }
        requireNotNull(expectedSongEndTick) { "Import a source MIDI before validating the exact project timeline." }
        MidiCoreOccurrenceTimeline.buildFromBars(MidiPpq(ppq), meter, projectDefinitions, placements, expectedSongEndTick)
    }
    return result.exceptionOrNull()?.message
}

private fun parseHarmony(chords: List<ChordDraft>): ParsedHarmony? = runCatching {
    ParsedHarmony(chords.map { chord ->
        val start = chord.startTickText.toLongOrNull() ?: error("Chord ${chord.id} start tick is not an integer.")
        val duration = chord.durationTicksText.toLongOrNull() ?: error("Chord ${chord.id} duration is not an integer.")
        AuthoritativeChordEvent(chord.id.trim(), chord.occurrenceId.trim(), chord.symbol.trim(), start, Math.addExact(start, duration))
    })
}.getOrNull()

private fun buildPreviewAuthority(
    persistedAuthority: ProjectAuthority?,
    draft: MidiCoreAuthorityDraft,
    parsedStructure: ParsedStructure?,
    parsedHarmony: ParsedHarmony?,
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
                harmonyValid && parsedHarmony != null -> parsedHarmony.events
                parsedStructure != null && parsedHarmony != null -> emptyList()
                else -> authority.chordEvents
            },
        )
    }.getOrNull()
}

private fun previewInvalidation(project: app.melotrail.project.MidiCoreProject?, updatedAuthority: ProjectAuthority?): MidiCoreInvalidationPreview? {
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

private fun denominatorExponent(value: String): Int? {
    val denominator = value.toLongOrNull() ?: return null
    if (denominator !in 1L..(1L shl 30) || denominator and (denominator - 1L) != 0L) return null
    var exponent = 0
    var current = denominator
    while (current > 1L) {
        exponent += 1
        current = current shr 1
    }
    return exponent
}

private fun nextSpelling(current: ProjectKeySpelling): ProjectKeySpelling {
    val entries = ProjectKeySpelling.entries
    return entries[(entries.indexOf(current) + 1) % entries.size]
}

private fun nextSafeId(prefix: String, used: Set<String>): String {
    var index = 1
    var candidate: String
    do {
        candidate = "$prefix-$index"
        index += 1
    } while (candidate in used)
    return candidate
}

private fun defaultOccurrenceBars(
    occurrences: List<OccurrenceDraft>,
    expectedSongEndTick: Long?,
    ppq: Int?,
    meter: ProjectMeter,
): Int {
    val currentBars = occurrences.sumOf { it.barCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
    val requiredBars = sourceBarCount(expectedSongEndTick, ppq, meter)
    return requiredBars?.minus(currentBars)?.takeIf { it > 0 } ?: 1
}

private fun sourceBarCount(expectedSongEndTick: Long?, ppq: Int?, meter: ProjectMeter): Int? {
    if (expectedSongEndTick == null || ppq == null) return null
    val barTicks = runCatching { MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(ppq), meter) }.getOrNull() ?: return null
    if (expectedSongEndTick % barTicks != 0L) return null
    return (expectedSongEndTick / barTicks).takeIf { it in 0..Int.MAX_VALUE }?.toInt()
}

private fun occurrenceBarCount(occurrence: ProjectSectionOccurrence, ppq: Int?, meter: ProjectMeter): Int? {
    val ticks = occurrence.endTick - occurrence.startTick
    val barTicks = ppq?.let { runCatching { MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(it), meter) }.getOrNull() } ?: return null
    if (ticks % barTicks != 0L) return null
    return (ticks / barTicks).takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}

private fun formatPosition(tick: Long, ppq: Int, meter: ProjectMeter): String {
    val ticksPerQuarter = ppq.toLong()
    val denominator = meter.denominator
    val beatNumerator = ticksPerQuarter * 4L
    if (beatNumerator % denominator != 0L) return "tick $tick"
    val ticksPerBeat = beatNumerator / denominator
    val barTicks = ticksPerBeat * meter.numerator
    if (ticksPerBeat <= 0L || barTicks <= 0L) return "tick $tick"
    val bar = tick / barTicks + 1L
    val inBar = tick % barTicks
    val beat = inBar / ticksPerBeat + 1L
    val tickInBeat = inBar % ticksPerBeat
    return "bar $bar · beat $beat · tick $tickInBeat"
}

private fun auditionStatus(scope: MidiAuditionScope?, playback: MidiAuditionPlaybackState, positionTick: Long): String = when (scope) {
    null -> "No MIDI authority view is selected."
    MidiAuditionScope.SourceMelody -> "Source melody: ${playback.name.lowercase()} at tick $positionTick."
    is MidiAuditionScope.Occurrence -> "Occurrence ${scope.occurrenceId}: ${playback.name.lowercase()} at tick $positionTick."
    is MidiAuditionScope.Candidate -> "Candidate audition: ${playback.name.lowercase()} at tick $positionTick."
    is MidiAuditionScope.Role -> "Role audition: ${playback.name.lowercase()} at tick $positionTick."
    MidiAuditionScope.AcceptedArrangement -> "Accepted arrangement: ${playback.name.lowercase()} at tick $positionTick."
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = mapIndexed { current, item -> if (current == index) value else item }

private fun List<OccurrenceDraft>.reorderedOccurrence(index: Int, delta: Int): List<OccurrenceDraft> {
    val destination = index + delta
    if (index !in indices || destination !in indices) return this
    return toMutableList().also { it.add(destination, it.removeAt(index)) }
}
