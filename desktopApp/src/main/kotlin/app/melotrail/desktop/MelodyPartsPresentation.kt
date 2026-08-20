package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.StageRunSnapshot
import app.melotrail.application.PreviewAudioSource
import app.melotrail.application.PreviewMidiSource
import app.melotrail.arrangement.EnhancementSelection
import app.melotrail.arrangement.SectionTypeCatalog
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageSubject

/** UI-only, immutable adapter over canonical part and stage-run snapshots. */
internal data class MelodyPartCardState(
    val part: PartSummary,
    val stages: List<MelodyPartStageState>,
    val requiredAction: String?,
    val processing: Boolean,
    val retryable: Boolean
)

internal data class MelodyPartStageState(
    val label: String,
    val status: MelodyPartStageStatus,
    val detail: String
)

internal enum class MelodyPartStageStatus { COMPLETE, PROCESSING, FAILED, WAITING }

/** A presentation-only choice. Persisted selection remains owned by the application services. */
internal data class PartArtifactComparison(
    val kind: PartArtifactKind,
    val label: String,
    val runLabel: String,
    val detail: String,
    val current: Boolean,
    val preview: PartArtifactPreview
)

internal enum class PartArtifactKind { ORIGINAL, CLEANED, CORRECTED, ENHANCED }

internal sealed interface PartArtifactPreview {
    data class Audio(val source: PreviewAudioSource) : PartArtifactPreview
    data class Midi(val source: PreviewMidiSource) : PartArtifactPreview
}

/**
 * The desktop never discovers files itself: availability is the already
 * validated application snapshot. Draft, rejected, and stale enhancement
 * evidence is deliberately omitted from this current-ready list.
 */
internal fun availablePartArtifactComparisons(project: ProjectSnapshot, part: PartSummary): List<PartArtifactComparison> {
    if (!part.preparation.sourcePreserved || (part.sourceType == PartSourceType.MIDI && !part.preparation.rawMidi)) return emptyList()
    val runs = project.readiness.stageRuns.filter { (it.subject as? StageSubject.Part)?.partId == part.id }
    fun run(stage: StageId) = runs.lastOrNull { it.stage == stage && it.status == StageRunStatus.COMPLETED }
        ?.let { "${stage.name.lowercase().replaceFirstChar(Char::uppercase)} · ${it.runId}" }
        ?: "${stage.name.lowercase().replaceFirstChar(Char::uppercase)} · canonical artifact"
    val preparation = part.preparation
    val current = when {
        preparation.enhancement.selected == EnhancementSelection.ENHANCED && preparation.enhancement.approvedAvailable -> PartArtifactKind.ENHANCED
        preparation.technicalCorrection.selected == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED && preparation.technicalCorrection.available && !preparation.technicalCorrection.approvalRequired -> PartArtifactKind.CORRECTED
        preparation.cleanMidi && preparation.midiQuality.status == MidiQualityStatus.CURRENT -> PartArtifactKind.CLEANED
        else -> PartArtifactKind.ORIGINAL
    }
    val original = if (part.sourceType == PartSourceType.AUDIO) {
        PartArtifactComparison(PartArtifactKind.ORIGINAL, "Original audio", run(StageId.SOURCE), "Immutable source audio monitor; it is not a MIDI representation.", current == PartArtifactKind.ORIGINAL, PartArtifactPreview.Audio(PreviewAudioSource.ORIGINAL))
    } else {
        PartArtifactComparison(PartArtifactKind.ORIGINAL, "Original MIDI", run(StageId.SOURCE), "Immutable raw MIDI representation.", current == PartArtifactKind.ORIGINAL, PartArtifactPreview.Midi(PreviewMidiSource.RAW))
    }
    return buildList {
        add(original)
        if (preparation.cleanMidi && preparation.midiQuality.status == MidiQualityStatus.CURRENT) {
            add(PartArtifactComparison(PartArtifactKind.CLEANED, "Cleaned MIDI", run(StageId.CLEANED), if (part.sourceType == PartSourceType.AUDIO) "Derived MIDI render from source audio; compare it with audio as different representations." else "Validated derived MIDI.", current == PartArtifactKind.CLEANED, PartArtifactPreview.Midi(PreviewMidiSource.CLEANED)))
        }
        if (preparation.technicalCorrection.available && !preparation.technicalCorrection.approvalRequired) {
            add(PartArtifactComparison(PartArtifactKind.CORRECTED, "Corrected MIDI", run(StageId.CORRECTED), "Validated conservative correction; downstream selection can bypass Enhanced.", current == PartArtifactKind.CORRECTED, PartArtifactPreview.Midi(PreviewMidiSource.CORRECTED)))
        }
        if (preparation.enhancement.approvedAvailable) {
            add(PartArtifactComparison(PartArtifactKind.ENHANCED, "Enhanced MIDI", run(StageId.ENHANCED), "Approved enhanced MIDI; its corrected input and context were hash-validated.", current == PartArtifactKind.ENHANCED, PartArtifactPreview.Midi(PreviewMidiSource.ENHANCED)))
        }
    }
}

private data class MelodyPartStageDefinition(val label: String, val stage: StageId?)

private val melodyPartStages = listOf(
    MelodyPartStageDefinition("Importing", StageId.SOURCE),
    MelodyPartStageDefinition("Extracting", StageId.EXTRACTED),
    MelodyPartStageDefinition("Cleaning", StageId.CLEANED),
    MelodyPartStageDefinition("Normalizing", StageId.NORMALIZED),
    MelodyPartStageDefinition("Transposing", StageId.TRANSPOSED),
    MelodyPartStageDefinition("Correcting", StageId.CORRECTED),
    MelodyPartStageDefinition("Enhancing", StageId.ENHANCED),
    MelodyPartStageDefinition("Ready", null)
)

/**
 * Reduces persisted records only. Missing later-stage records stay visibly
 * waiting; the UI never treats them as complete merely because an import was started.
 */
internal fun reduceMelodyPartCard(project: ProjectSnapshot, part: PartSummary): MelodyPartCardState {
    val runs = project.readiness.stageRuns.filter { (it.subject as? StageSubject.Part)?.partId == part.id }
    val stages = melodyPartStages.mapIndexed { index, definition ->
        if (definition.stage == null) readyStage(part, index, runs) else stageState(definition, runs)
    }
    val failed = stages.any { it.status == MelodyPartStageStatus.FAILED }
    return MelodyPartCardState(
        part = part,
        stages = stages,
        requiredAction = requiredAction(project, part),
        processing = stages.any { it.status == MelodyPartStageStatus.PROCESSING },
        retryable = failed
    )
}

private fun stageState(definition: MelodyPartStageDefinition, runs: List<StageRunSnapshot>): MelodyPartStageState {
    val run = runs.lastOrNull { it.stage == definition.stage }
    return when (run?.status) {
        StageRunStatus.COMPLETED -> MelodyPartStageState(definition.label, MelodyPartStageStatus.COMPLETE, "Complete")
        StageRunStatus.PROCESSING -> MelodyPartStageState(definition.label, MelodyPartStageStatus.PROCESSING, "Processing")
        StageRunStatus.FAILED -> MelodyPartStageState(definition.label, MelodyPartStageStatus.FAILED, safeFailureDetail(run))
        else -> MelodyPartStageState(definition.label, MelodyPartStageStatus.WAITING, "Waiting for a supported stage")
    }
}

private fun readyStage(part: PartSummary, index: Int, runs: List<StageRunSnapshot>): MelodyPartStageState = when {
    part.preparation.ready -> MelodyPartStageState("Ready", MelodyPartStageStatus.COMPLETE, "Current representation is ready")
    runs.any { it.status == StageRunStatus.FAILED } -> MelodyPartStageState("Ready", MelodyPartStageStatus.WAITING, "Resolve the failed stage first")
    melodyPartStages.take(index).any { definition -> runs.lastOrNull { it.stage == definition.stage }?.status == StageRunStatus.PROCESSING } ->
        MelodyPartStageState("Ready", MelodyPartStageStatus.WAITING, "Waiting for the active stage")
    else -> MelodyPartStageState("Ready", MelodyPartStageStatus.WAITING, "Further validated processing is required")
}

private fun safeFailureDetail(run: StageRunSnapshot): String = when (run.failure?.name) {
    "DEPENDENCY_UNAVAILABLE" -> "A required local dependency is unavailable. Recover it, then retry."
    "INPUT_INVALID" -> "Required input needs confirmation or correction, then retry."
    "OUTPUT_INVALID" -> "The derived output was rejected. Review the source and retry."
    "INTERRUPTED" -> "The previous stage was interrupted. Retry the stage."
    else -> "This stage did not complete. Review its input and retry."
}

private fun requiredAction(project: ProjectSnapshot, part: PartSummary): String? = when {
    !part.sourceKeyConfirmed -> "Confirm source key"
    part.preparation.midiQuality.status == MidiQualityStatus.APPROVAL_REQUIRED -> "Review cleaned MIDI quality"
    !project.readiness.compositionSettingsReady -> "Complete project Setup"
    !project.readiness.harmonyReady -> "Complete required harmony"
    else -> null
}

@Composable
internal fun MelodyPartsCards(
    state: WorkspaceUiState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) = Card(
    Modifier.fillMaxWidth().semantics { testTag = WorkspacePageTags.IMPORTED_FILES },
    colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface)
) {
    val project = state.project
    Column(Modifier.padding(MusicWorkspaceTokens.Pages.ContentInset), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        Text("MELODY PIPELINE (${project?.parts?.size ?: 0})", modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORT_TABLE_HEADER }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (project == null || project.parts.isEmpty()) {
            Text("Import a MIDI file or solo-piano audio source to begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else project.parts.forEachIndexed { index, part ->
            SimplifiedMelodyPartCard(state, part, onIntent)
            if (index < project.parts.lastIndex) HorizontalDivider()
        }
    }
}

private enum class PipelineStepStatus { COMPLETE, CURRENT, REVIEW, WAITING, FAILED }
private data class PipelineStep(val label: String, val status: PipelineStepStatus)

@Composable
private fun SimplifiedMelodyPartCard(state: WorkspaceUiState, part: PartSummary, onIntent: (WorkspaceIntent) -> Unit) {
    val locked = state.operation.isMutating
    val correctionReady = part.preparation.technicalCorrection.selected == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED &&
        part.preparation.technicalCorrection.available
    val aiFix = part.preparation.midiAiFix
    val aiFixComplete = aiFix.selected == app.melotrail.arrangement.MidiAiFixSelection.SKIP ||
        (aiFix.selected == app.melotrail.arrangement.MidiAiFixSelection.APPROVED && aiFix.approvedAvailable)
    val enhancement = part.preparation.enhancement
    val enhancementComplete = enhancement.selected == EnhancementSelection.CORRECTED ||
        (enhancement.selected == EnhancementSelection.ENHANCED && enhancement.approvedAvailable)
    val steps = listOf(
        PipelineStep("Import audio/MIDI", if (part.preparation.rawMidi) PipelineStepStatus.COMPLETE else PipelineStepStatus.WAITING),
        PipelineStep("Clean MIDI", when {
            !part.preparation.rawMidi -> PipelineStepStatus.WAITING
            part.preparation.midiQuality.status == MidiQualityStatus.CURRENT -> PipelineStepStatus.COMPLETE
            else -> PipelineStepStatus.CURRENT
        }),
        PipelineStep("Technical Correction", when {
            part.preparation.midiQuality.status != MidiQualityStatus.CURRENT -> PipelineStepStatus.WAITING
            correctionReady -> PipelineStepStatus.COMPLETE
            else -> PipelineStepStatus.CURRENT
        }),
        PipelineStep("AI Fix", when {
            !correctionReady -> PipelineStepStatus.WAITING
            aiFixComplete -> PipelineStepStatus.COMPLETE
            aiFix.draftAvailable -> PipelineStepStatus.REVIEW
            else -> PipelineStepStatus.CURRENT
        }),
        PipelineStep("AI Enhance", when {
            !aiFixComplete -> PipelineStepStatus.WAITING
            enhancementComplete -> PipelineStepStatus.COMPLETE
            enhancement.available && enhancement.approval == app.melotrail.arrangement.EnhancementApproval.DRAFT -> PipelineStepStatus.REVIEW
            else -> PipelineStepStatus.CURRENT
        }),
        PipelineStep("Apply Lo-fi Feel", when {
            !enhancementComplete -> PipelineStepStatus.WAITING
            part.preparation.midiFeel.selected == app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL && part.preparation.midiFeel.available -> PipelineStepStatus.COMPLETE
            else -> PipelineStepStatus.CURRENT
        })
    )
    val active = steps.firstOrNull { it.status == PipelineStepStatus.CURRENT || it.status == PipelineStepStatus.REVIEW }
    Column(
        Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Sm).semantics { testTag = WorkspacePageTags.IMPORTED_ROW_PREFIX + part.id },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)
    ) {
        Text(part.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        steps.forEach { step ->
            Text("${step.label}: ${step.status.name.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall,
                color = if (step == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (active?.label) {
            "Clean MIDI" -> Button(onClick = { onIntent(WorkspaceIntent.CleanMidi(part.id)) }, enabled = !locked) { Text("Clean MIDI") }
            "Technical Correction" -> Button(onClick = { onIntent(WorkspaceIntent.SelectPart(part.id)); onIntent(WorkspaceIntent.CreateTechnicalCorrection) }, enabled = !locked) { Text("Apply Technical Correction") }
            "AI Fix" -> AiFixActions(part.id, aiFix.draftAvailable, locked, onIntent)
            "AI Enhance" -> EnhancementActions(part.id, enhancement.approval == app.melotrail.arrangement.EnhancementApproval.DRAFT, locked, onIntent)
            "Apply Lo-fi Feel" -> Button(onClick = {
                onIntent(WorkspaceIntent.SelectPart(part.id))
                onIntent(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL))
                onIntent(WorkspaceIntent.ApplyMidiFeelAndReanalyze)
            }, enabled = !locked) { Text("Apply Lo-fi Feel") }
        }
        TextButton(
            onClick = { onIntent(WorkspaceIntent.RequestRemoveSongPart(part.id)) },
            enabled = !locked
        ) { Text("Remove Melody track") }
        if (state.operation is WorkspaceOperation.Failed && state.retry != null) TextButton(onClick = { onIntent(WorkspaceIntent.Retry) }) { Text("Retry") }
    }
}

@Composable
private fun AiFixActions(partId: String, hasDraft: Boolean, locked: Boolean, onIntent: (WorkspaceIntent) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    if (hasDraft) {
        Button(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.ApproveMidiAiFix) }, enabled = !locked) { Text("Accept") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.RejectMidiAiFix) }, enabled = !locked) { Text("Refuse") }
        TextButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.RegenerateMidiAiFix) }, enabled = !locked) { Text("Regenerate") }
    } else {
        Button(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.CreateMidiAiFix) }, enabled = !locked) { Text("Run AI Fix") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.ReturnToCleanedMidi) }, enabled = !locked) { Text("Skip") }
    }
}

@Composable
private fun EnhancementActions(partId: String, hasDraft: Boolean, locked: Boolean, onIntent: (WorkspaceIntent) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
    if (hasDraft) {
        Button(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.ApproveEnhancement) }, enabled = !locked) { Text("Accept") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.RejectEnhancement) }, enabled = !locked) { Text("Refuse") }
        TextButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.SelectEnhancement(app.melotrail.arrangement.EnhancementIntensity.SUBTLE)) }, enabled = !locked) { Text("Regenerate") }
    } else {
        Button(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.SelectEnhancement(app.melotrail.arrangement.EnhancementIntensity.SUBTLE)) }, enabled = !locked) { Text("Run AI Enhance") }
        OutlinedButton(onClick = { onIntent(WorkspaceIntent.SelectPart(partId)); onIntent(WorkspaceIntent.SelectEnhancement(app.melotrail.arrangement.EnhancementIntensity.OFF)) }, enabled = !locked) { Text("Skip") }
    }
}

@Composable
private fun MelodyPartCard(
    state: WorkspaceUiState,
    card: MelodyPartCardState,
    onIntent: (WorkspaceIntent) -> Unit,
    partDetailsFocusTargets: MutableMap<PartDetailsFocusReturn, FocusRequester>
) {
    val part = card.part
    val selected = state.selectedPartId == part.id
    val locked = state.operation.isMutating || card.processing
    val focusReturn = PartDetailsFocusReturn.ImportedRow(part.id)
    val focusRequester = remember(part.id) { FocusRequester() }
    DisposableEffect(focusReturn, focusRequester) {
        partDetailsFocusTargets[focusReturn] = focusRequester
        onDispose { partDetailsFocusTargets.remove(focusReturn, focusRequester) }
    }
    Column(
        Modifier.fillMaxWidth().background(if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.ElevatedSurface)
            .clickable(enabled = !locked) { onIntent(WorkspaceIntent.SelectPart(part.id)) }
            .padding(MusicWorkspaceTokens.Spacing.Sm)
            .semantics {
                testTag = WorkspacePageTags.IMPORTED_ROW_PREFIX + part.id
                contentDescription = "Melody part ${part.name}, ${SectionTypeCatalog.label(part.sectionType)}, ${part.sourceType.name.lowercase()}. ${card.stages.joinToString { "${it.label} ${it.status.name.lowercase()}" }}"
            },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(part.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${SectionTypeCatalog.label(part.sectionType)} · ${part.sourceType.name.lowercase()} · ${part.sourceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Playing: ${playingArtifactLabel(state, part.id)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onIntent(WorkspaceIntent.ShowRoleEditor(part.id)) }, enabled = !locked) { Text("Section") }
            TextButton(onClick = { onIntent(WorkspaceIntent.ShowPartDetails(part.id, focusReturn)) }, modifier = Modifier.focusRequester(focusRequester).semantics { testTag = WorkspacePageTags.IMPORTED_DETAILS_PREFIX + part.id }) { Text("Inspect") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text(melodyPartFileSize(part.sourceSizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(part.sourceKey?.let { key ->
                val detected = key.detectedKey?.displayName ?: "Key unavailable"
                "Source key: $detected (${String.format(java.util.Locale.ROOT, "%.0f%%", key.confidence * 100)})"
            } ?: part.analysis?.key ?: "Key unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(part.analysis?.durationSeconds?.let(::formatDuration) ?: "Duration unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val previewCapability = if (part.sourceType == PartSourceType.AUDIO) RuntimeCapability.SOURCE_PREVIEW else RuntimeCapability.MIDI_PREVIEW
            val preview = state.runtimeReadiness?.capability(previewCapability)
            TextButton(onClick = { onIntent(WorkspaceIntent.PreviewPart(part.id)) }, enabled = !locked && preview?.available == true,
                modifier = Modifier.semantics { testTag = WorkspacePageTags.IMPORTED_PREVIEW_PREFIX + part.id }) { Text("Preview") }
        }
        MelodyStageRail(card.stages)
        card.requiredAction?.let { Text("Required: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            if (card.retryable && state.retry != null) TextButton(onClick = { onIntent(WorkspaceIntent.Retry) }) { Text("Retry") }
            if (part.sourceKey?.confirmationRequired == true) {
                TextButton(onClick = { onIntent(WorkspaceIntent.ShowSourceKeyConfirmation(part.id)) }, enabled = !locked) { Text("Confirm source key") }
            } else if (part.sourceKey != null && !part.preparation.transposedMidi) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.TransposePart(part.id)) }, enabled = !locked) { Text("Transpose to project key") }
            }
            if (primaryPartAction(part, state.pendingMidiFeel) is PartPrimaryAction.AddToStructure) {
                OutlinedButton(onClick = { onIntent(WorkspaceIntent.RequestAddPartToStructure(part.id)) }, enabled = !locked) { Text("Add to structure") }
            }
            state.project?.structure?.firstOrNull { it.partId == part.id }?.let { occurrence ->
                TextButton(onClick = { onIntent(WorkspaceIntent.RequestRemovePartFromStructure(part.id, occurrence.instanceId)) }, enabled = !locked) { Text("Remove from structure") }
            }
        }
        if (card.processing) Text("This part is processing. Conflicting edits and actions are unavailable; cancellation is not supported.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MelodyStageRail(stages: List<MelodyPartStageState>) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    stages.forEach { stage ->
        val marker = when (stage.status) {
            MelodyPartStageStatus.COMPLETE -> "Complete"
            MelodyPartStageStatus.PROCESSING -> "Processing"
            MelodyPartStageStatus.FAILED -> "Failed"
            MelodyPartStageStatus.WAITING -> "Waiting"
        }
        Text("${stage.label}: $marker", modifier = Modifier.weight(1f).semantics { contentDescription = "${stage.label}, $marker. ${stage.detail}" }, style = MaterialTheme.typography.labelSmall,
            color = when (stage.status) {
                MelodyPartStageStatus.FAILED -> MaterialTheme.colorScheme.error
                MelodyPartStageStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun playingArtifactLabel(state: WorkspaceUiState, partId: String): String {
    val session = state.playbackSession
    val playingPart = (session.request as? PlaybackRequest.Part)?.partId
    return if (playingPart == partId && session.artifact != null) {
        session.artifact.source?.let { "${it.label} · ${it.sha256.take(12)}" } ?: "Validated preview artifact"
    } else "No artifact playing"
}

private fun melodyPartFileSize(bytes: Long?): String = when {
    bytes == null -> "size unavailable"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
}
