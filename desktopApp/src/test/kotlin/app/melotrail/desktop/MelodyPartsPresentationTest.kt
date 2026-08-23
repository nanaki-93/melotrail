package app.melotrail.desktop

import app.melotrail.application.PartPreparationSummary
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.EnhancementSummary
import app.melotrail.application.MidiQualityStatus
import app.melotrail.application.MidiQualitySummary
import app.melotrail.application.ProjectReadiness
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.StageRunSnapshot
import app.melotrail.application.TechnicalCorrectionSummary
import app.melotrail.arrangement.EnhancementApproval
import app.melotrail.arrangement.EnhancementSelection
import app.melotrail.arrangement.TechnicalCorrectionSelection
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageSubject
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MelodyPartsPresentationTest {
    @Test
    fun `persisted part stage snapshots keep completed stages visible through a later failure`() {
        val part = part()
        val card = reduceMelodyPartCard(project(part, listOf(
            run(StageId.SOURCE, StageRunStatus.COMPLETED),
            run(StageId.EXTRACTED, StageRunStatus.COMPLETED),
            run(StageId.CLEANED, StageRunStatus.FAILED)
        )), part)

        assertEquals(MelodyPartStageStatus.COMPLETE, card.stages[0].status)
        assertEquals(MelodyPartStageStatus.COMPLETE, card.stages[1].status)
        assertEquals(MelodyPartStageStatus.FAILED, card.stages[2].status)
        assertTrue(card.retryable)
        assertFalse(card.processing)
    }

    @Test
    fun `processing and required inputs are textual rather than inferred complete`() {
        val part = part(sourceKeyConfirmed = false)
        val card = reduceMelodyPartCard(project(part, listOf(run(StageId.EXTRACTED, StageRunStatus.PROCESSING))), part)

        assertEquals(MelodyPartStageStatus.PROCESSING, card.stages[1].status)
        assertEquals(MelodyPartStageStatus.WAITING, card.stages.last().status)
        assertEquals("Confirm source key", card.requiredAction)
        assertTrue(card.processing)
    }

    @Test
    fun `comparison exposes only current validated representations with exact completed run labels`() {
        val preparation = PartPreparationSummary(
            sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true, cleanMidi = true, analyzed = true, ready = true, warnings = emptyList(),
            midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT),
            technicalCorrection = TechnicalCorrectionSummary(TechnicalCorrectionSelection.CORRECTED, available = true),
            midiAiFix = app.melotrail.application.MidiAiFixSummary(
                selected = app.melotrail.arrangement.MidiAiFixSelection.APPROVED,
                approvedAvailable = true
            ),
            enhancement = EnhancementSummary(selected = EnhancementSelection.ENHANCED, available = true, approval = EnhancementApproval.APPROVED)
        )
        val choices = availablePartArtifactComparisons(project(part(preparation = preparation), listOf(
            run(StageId.SOURCE, StageRunStatus.COMPLETED), run(StageId.CLEANED, StageRunStatus.COMPLETED),
            run(StageId.CORRECTED, StageRunStatus.COMPLETED), run(StageId.AI_FIXED, StageRunStatus.COMPLETED), run(StageId.ENHANCED, StageRunStatus.COMPLETED)
        )), part(preparation = preparation))

        assertEquals(listOf(PartArtifactKind.SOURCE, PartArtifactKind.RAW, PartArtifactKind.CLEANED, PartArtifactKind.CORRECTED, PartArtifactKind.AI_FIX, PartArtifactKind.ENHANCED), choices.map { it.kind })
        assertEquals("Enhanced · run-ENHANCED", choices.last().runLabel)
        assertEquals(PartArtifactKind.ENHANCED, choices.single { it.current }.kind)
    }

    @Test
    fun `comparison omits rejected enhancement and labels audio versus derived MIDI honestly`() {
        val preparation = PartPreparationSummary(
            sourcePreserved = true, inspected = true, preparedAudio = true, rawMidi = true, cleanMidi = true, analyzed = true, ready = true, warnings = emptyList(),
            midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT),
            enhancement = EnhancementSummary(selected = EnhancementSelection.CORRECTED, available = true, approval = EnhancementApproval.REJECTED)
        )
        val audio = part(preparation = preparation).copy(sourceType = PartSourceType.AUDIO, sourceFile = "source/verse.wav", sourceName = "verse.wav")
        val choices = availablePartArtifactComparisons(project(audio, emptyList()), audio)

        assertEquals("SOURCE · audio", choices.first().label)
        assertTrue(choices.single { it.kind == PartArtifactKind.CLEANED }.detail.contains("Derived MIDI"))
        assertFalse(choices.any { it.kind == PartArtifactKind.ENHANCED })
    }

    @Test
    fun `AI fix draft is safely previewable without becoming the current representation`() {
        val preparation = PartPreparationSummary(
            sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true, cleanMidi = true, analyzed = false, ready = false, warnings = emptyList(),
            midiQuality = MidiQualitySummary(MidiQualityStatus.CURRENT),
            midiAiFix = app.melotrail.application.MidiAiFixSummary(draftAvailable = true)
        )

        val choices = availablePartArtifactComparisons(project(part(preparation = preparation), emptyList()), part(preparation = preparation))

        assertEquals(PartArtifactKind.AI_FIX, choices.last().kind)
        assertEquals(PartArtifactPreview.Midi(app.melotrail.application.PreviewMidiSource.AI_FIX_DRAFT), choices.last().preview)
        assertFalse(choices.last().current)
    }

    private fun project(part: PartSummary, runs: List<StageRunSnapshot>) = ProjectSnapshot(
        root = Path.of("build/melody-parts"), version = 4, name = "Melody Parts", renderFormat = null, parts = listOf(part), structure = emptyList(),
        readiness = ProjectReadiness(
            cleanMidiReady = false, analysesReady = false, structureReady = false, songPlanAvailable = false,
            arrangementAvailable = false, generatedMidiAvailable = false, stemsAvailable = false, dryMixAvailable = false,
            loFiMixAvailable = false, masterAvailable = false, stageRuns = runs
        )
    )

    private fun part(sourceKeyConfirmed: Boolean = true, preparation: PartPreparationSummary = PartPreparationSummary(sourcePreserved = true, inspected = true, preparedAudio = false,
        rawMidi = true, cleanMidi = false, analyzed = false, ready = false, warnings = emptyList())) = PartSummary(
        id = "verse", role = "verse", sourceFile = "source/verse.mid", sourceName = "verse.mid", sourceType = PartSourceType.MIDI,
        analysis = null, preparation = preparation,
        name = "Verse melody", sectionType = app.melotrail.arrangement.SectionTypeId.VERSE, sourceKeyConfirmed = sourceKeyConfirmed
    )

    private fun run(stage: StageId, status: StageRunStatus) = StageRunSnapshot(
        runId = "run-${stage.name}", stage = stage, subject = StageSubject.Part("verse"), status = status,
        retryable = status == StageRunStatus.FAILED
    )
}
