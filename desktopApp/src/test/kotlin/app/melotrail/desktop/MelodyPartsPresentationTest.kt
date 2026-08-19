package app.melotrail.desktop

import app.melotrail.application.PartPreparationSummary
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.ProjectReadiness
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.StageRunSnapshot
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

    private fun project(part: PartSummary, runs: List<StageRunSnapshot>) = ProjectSnapshot(
        root = Path.of("build/melody-parts"), version = 4, name = "Melody Parts", renderFormat = null, parts = listOf(part), structure = emptyList(),
        readiness = ProjectReadiness(
            cleanMidiReady = false, analysesReady = false, structureReady = false, songPlanAvailable = false,
            arrangementAvailable = false, generatedMidiAvailable = false, stemsAvailable = false, dryMixAvailable = false,
            loFiMixAvailable = false, masterAvailable = false, stageRuns = runs
        )
    )

    private fun part(sourceKeyConfirmed: Boolean = true) = PartSummary(
        id = "verse", role = "verse", sourceFile = "source/verse.mid", sourceName = "verse.mid", sourceType = PartSourceType.MIDI,
        analysis = null, preparation = PartPreparationSummary(sourcePreserved = true, inspected = true, preparedAudio = false,
            rawMidi = true, cleanMidi = false, analyzed = false, ready = false, warnings = emptyList()),
        name = "Verse melody", sectionType = app.melotrail.arrangement.SectionTypeId.VERSE, sourceKeyConfirmed = sourceKeyConfirmed
    )

    private fun run(stage: StageId, status: StageRunStatus) = StageRunSnapshot(
        runId = "run-${stage.name}", stage = stage, subject = StageSubject.Part("verse"), status = status,
        retryable = status == StageRunStatus.FAILED
    )
}
