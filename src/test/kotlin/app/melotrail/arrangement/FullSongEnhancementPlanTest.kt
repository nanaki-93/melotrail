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
import kotlinx.serialization.json.JsonObject
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

    @Test fun `Qwen explanatory fields are discarded before strict plan publication`() {
        val input = enhancementInput(noteCount = 50)
        val response = """{"operations":[{"kind":"REDUCE_DENSITY","issueId":"${input.issues.single().id}","targetId":"bass","noteId":"${input.targets.single().notes.first().id}","note":"remove repeated low attack"}]}"""

        val plan = LocalQwenFullSongEnhancementPlanner(LocalQwenClient { _, _ -> response }).plan(input)

        assertEquals(FullSongEnhancementOperationKind.REDUCE_DENSITY, FullSongEnhancementPlanParser.parse(plan).operations.single().kind)
    }

    @Test fun `Qwen duplicate edits for one target note are deterministically reduced`() {
        val input = enhancementInput(noteCount = 50)
        val target = input.targets.single()
        val issueId = input.issues.single().id
        val noteId = target.notes.first().id
        val response = """{"operations":[
            {"kind":"REDUCE_DENSITY","issueId":"$issueId","targetId":"${target.id}","noteId":"$noteId"},
            {"kind":"REMOVE_COLLISION","issueId":"$issueId","targetId":"${target.id}","noteId":"$noteId"}
        ]}"""

        val plan = FullSongEnhancementPlanParser.parse(
            LocalQwenFullSongEnhancementPlanner(LocalQwenClient { _, _ -> response }).plan(input)
        )

        assertEquals(1, plan.operations.size)
        assertEquals(FullSongEnhancementOperationKind.REDUCE_DENSITY, plan.operations.single().kind)
    }

    @Test fun `Qwen excessive piano pitch is snapped to the nearest bounded chord tone`() {
        val bassInput = enhancementInput(noteCount = 20)
        val input = bassInput.copy(
            issues = bassInput.issues.map { it.copy(targetRole = "piano") },
            targets = bassInput.targets.map { it.copy(id = "piano-one", role = "piano", occurrenceId = "one") }
        )
        val issueId = input.issues.single().id
        val target = input.targets.single()
        val noteId = target.notes.first().id
        val planner = LocalQwenFullSongEnhancementPlanner(LocalQwenClient { _, _ ->
            """{"operations":[{"kind":"CORRECT_CHORD_CLASH","issueId":"$issueId","targetId":"${target.id}","noteId":"$noteId","pitch":60}]}"""
        })

        val plan = FullSongEnhancementPlanParser.parse(planner.plan(input))

        assertEquals(48, plan.operations.single().pitch)
    }

    @Test fun `Qwen operations are capped to the code-owned per-target budget`() {
        val input = enhancementInput(noteCount = 40)
        val issueId = input.issues.single().id
        val target = input.targets.single()
        val operations = target.notes.take(6).joinToString(",") { note ->
            """{"kind":"ADJUST_VELOCITY","issueId":"$issueId","targetId":"${target.id}","noteId":"${note.id}","velocityDelta":-1}"""
        }

        val plan = FullSongEnhancementPlanParser.parse(
            LocalQwenFullSongEnhancementPlanner(LocalQwenClient { _, _ -> "{\"operations\":[$operations]}" }).plan(input)
        )

        assertEquals(input.policy.totalBudget(target.notes.size), plan.operations.size)
    }

    @Test fun `Qwen uses strict JSON schema when the local client supports it`() {
        var schemaUsed = false
        val client = object : JsonSchemaLocalQwenClient {
            override fun complete(systemPrompt: String, userPrompt: String): String = error("unconstrained completion must not be used")
            override fun completeJsonSchema(systemPrompt: String, userPrompt: String, schema: JsonObject): String {
                schemaUsed = true
                assertEquals(false, schema["additionalProperties"]?.toString()?.toBooleanStrict())
                return "{\"operations\":[]}"
            }
        }

        val plan = LocalQwenFullSongEnhancementPlanner(client).plan(enhancementInput(noteCount = 1))

        assertTrue(schemaUsed)
        assertTrue(FullSongEnhancementPlanParser.parse(plan).operations.isEmpty())
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
