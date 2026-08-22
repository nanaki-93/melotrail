package app.melotrail.arrangement

import app.melotrail.application.CanonicalChord
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import app.melotrail.application.WholeSongAnalysisProjection
import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullSongEnhancementPlanTest {
    private val hash = "a".repeat(64)
    private val issue = "b".repeat(32)
    private val note = "n-" + "c".repeat(64)

    @Test fun `strict parser accepts every allow-listed operation`() {
        FullSongEnhancementOperationKind.entries.forEach { kind ->
            val plan = FullSongEnhancementPlanParser.parse("""{"schemaVersion":1,"inputSha256":"$hash","contextSha256":"$hash","criticInputSha256":"$hash","criticReportSha256":"$hash","modelIdentity":"fake-v1","operations":[{"kind":"${kind.name}","issueId":"$issue","targetId":"bass","noteId":"$note","pitch":48}]}""")
            assertEquals(kind, plan.operations.single().kind)
        }
    }

    @Test fun `parser rejects prose and unknown fields`() {
        assertFailsWith<IllegalArgumentException> { FullSongEnhancementPlanParser.parse("here is a plan") }
        assertFailsWith<IllegalArgumentException> {
            FullSongEnhancementPlanParser.parse("""{"schemaVersion":1,"inputSha256":"$hash","contextSha256":"$hash","criticInputSha256":"$hash","criticReportSha256":"$hash","modelIdentity":"fake-v1","operations":[],"unsafe":"path"}""")
        }
    }

    @Test fun `policy uses exact floors including zero`() {
        val policy = FullSongEnhancementPolicy()
        assertEquals(0, policy.totalBudget(19)); assertEquals(1, policy.totalBudget(20))
        assertEquals(0, policy.additionDeletionBudget(49)); assertEquals(1, policy.additionDeletionBudget(50))
    }

    @Test fun `Qwen receives a capped issue-window request instead of the full song payload`() {
        var prompt = ""
        val input = enhancementInput(noteCount = 64)

        val plan = LocalQwenFullSongEnhancementPlanner(LocalQwenClient { _, userPrompt ->
            prompt = userPrompt
            "{\"operations\":[]}"
        }).plan(input)

        assertTrue(FullSongEnhancementPlanParser.parse(plan).operations.isEmpty())
        assertFalse(prompt.contains("contextSha256"))
        assertFalse(prompt.contains("source.mid"))
        assertEquals(48, "\"id\":\"n-".toRegex().findAll(prompt).count())
        assertTrue(prompt.contains(input.issues.single().id))
    }

    private fun enhancementInput(noteCount: Int) = FullSongEnhancementInput(
        inputSha256 = "a".repeat(64),
        contextSha256 = "b".repeat(64),
        criticInputSha256 = "c".repeat(64),
        criticReportSha256 = "d".repeat(64),
        authority = WholeSongAnalysisProjection(
            contextSha256 = "b".repeat(64),
            projectKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
            tempo = Tempo(120.0),
            meter = TimeSignature(4, 4),
            harmonyPpq = 480,
            occurrences = listOf(MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, 1_920)),
            harmony = listOf(HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, 1_920)),
            selectedParts = emptyList(),
            analyzedFacts = emptyList(),
            melodyEvidence = emptyList(),
            approvedArrangement = WorkflowArtifactReference("arrangement.json", "e".repeat(64)),
            generatedRoles = emptyList()
        ),
        issues = listOf(FullSongIssue(
            id = "f".repeat(32),
            category = FullSongIssueCategory.DENSITY_MISMATCH,
            severity = FullSongIssueSeverity.ACTIONABLE,
            targetRole = "bass",
            window = FullSongWindow(0, 1_000, 0, 1),
            observed = emptyList(),
            expected = emptyList(),
            reasonCode = "normalized-density-delta",
            suggestedCorrections = listOf(FullSongCorrectionFamily.DENSITY_REDUCTION)
        )),
        targets = listOf(FullSongEnhancementTarget(
            id = "bass",
            role = "bass",
            input = WorkflowArtifactReference("source.mid", "e".repeat(64)),
            notes = (0 until noteCount).map { index ->
                FullSongEnhancementNote(
                    id = "n-" + index.toString(16).padStart(64, '0'),
                    track = 0,
                    channel = 0,
                    pitch = 48,
                    velocity = 80,
                    startTick = index * 10L,
                    endTick = index * 10L + 5
                )
            }
        ))
    )
}
