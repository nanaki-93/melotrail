package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ArrangementCriticTest {
    private val json = Json { ignoreUnknownKeys = false }

    @TempDir lateinit var tempDir: Path

    @Test
    fun `deterministic critic passes through a valid arrangement unchanged`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        val critique = DeterministicArrangementCritic().critique(input, arrangement)

        assertEquals(CriticDecision.ACCEPT, critique.decision)
        assertEquals(arrangement, ArrangementCritiqueValidator.apply(input, arrangement, critique))
    }

    @Test
    fun `fixture backed accept and bounded revise are validated and applied`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        val accept = LocalQwenArrangementCritic(FixtureClient(fixture("valid-arrangement-critique-accept.json"))).critique(input, arrangement)
        val client = CapturingClient(fixture("valid-arrangement-critique-revise.json"))
        val revise = LocalQwenArrangementCritic(client).critique(input, arrangement)
        val draft = ArrangementCritiqueValidator.apply(input, arrangement, revise)

        assertEquals(arrangement, ArrangementCritiqueValidator.apply(input, arrangement, accept))
        assertEquals(0.5, (draft.sections.first().instruments[1] as BassInstrumentPlan).density)
        assertFalse(client.userPrompt.contains("midi/"))
        assertFalse(client.userPrompt.contains("project.json"))
        assertTrue(client.systemPrompt.contains("at most 4 sections"))
        assertTrue(client.systemPrompt.contains("required accept response"))
        assertTrue(client.systemPrompt.contains("required revise response shape"))
    }

    @Test
    fun `all issue categories are accepted when targets and changes are bounded`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        CriticIssueCategory.entries.forEach { category ->
            val critique = ArrangementCritique(
                decision = CriticDecision.REVISE,
                issues = listOf(CriticIssue(category, listOf(0), "A short musical observation.")),
                changes = listOf(CriticSectionChange(0, instruments = revisedBass(arrangement)))
            )
            assertTrue(ArrangementCritiqueValidator.validate(critique, input, arrangement).isValid, category.name)
        }
    }

    @Test
    fun `critic rejects unsafe fields invalid changes and loss of source identity`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        val valid = fixture("valid-arrangement-critique-revise.json")
        listOf("path", "command", "code", "notes").forEach { field ->
            assertThrows(IllegalArgumentException::class.java) {
                LocalQwenArrangementCritic(FixtureClient(valid.replace("\"version\": 1", "\"version\": 1, \"$field\": \"unsafe\""))).critique(input, arrangement)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { LocalQwenArrangementCritic(FixtureClient("this is prose, not JSON")).critique(input, arrangement) }
        assertThrows(IllegalArgumentException::class.java) { LocalQwenArrangementCritic(FixtureClient(valid.replace("too_repetitive", "unbounded_magic"))).critique(input, arrangement) }
        val noPiano = ArrangementCritique(decision = CriticDecision.REVISE, issues = listOf(CriticIssue(CriticIssueCategory.SOURCE_IDENTITY_RISK, listOf(0), "Keep the source piano audible.")), changes = listOf(
            CriticSectionChange(0, instruments = listOf(revisedBass(arrangement)[1]))
        ))
        val newInstrument = ArrangementCritique(decision = CriticDecision.REVISE, issues = listOf(CriticIssue(CriticIssueCategory.TOO_MANY_INSTRUMENTS, listOf(0), "Simplify the opening texture.")), changes = listOf(
            CriticSectionChange(0, instruments = revisedBass(arrangement) + StringsInstrumentPlan(role = StringsRole.LONG_NOTES, density = 0.4, register = MusicalRegister.LOW))
        ))
        val invalidRoleAndTransition = ArrangementCritique(decision = CriticDecision.REVISE, issues = listOf(CriticIssue(CriticIssueCategory.WEAK_TRANSITION, listOf(0), "Strengthen the approach to the climax.")), changes = listOf(
            CriticSectionChange(0, instruments = revisedBass(arrangement).map { if (it is BassInstrumentPlan) it.copy(role = DetailedBassRole.OCTAVE) else it }, transitionOut = TransitionPlan(TransitionType.CROSSFADE, crossfadeMs = 180))
        ))
        val outOfRange = ArrangementCritique(decision = CriticDecision.REVISE, issues = listOf(CriticIssue(CriticIssueCategory.ABRUPT_ENERGY_CHANGE, listOf(0), "Smooth the energy curve.")), changes = listOf(CriticSectionChange(0, energy = 1.1)))
        val excess = ArrangementCritique(decision = CriticDecision.REVISE, issues = listOf(CriticIssue(CriticIssueCategory.TOO_REPETITIVE, listOf(0), "Vary the repeated texture.")), changes = (0..4).map {
            CriticSectionChange(it % 2, instruments = revisedBass(arrangement))
        })
        val prose = valid.replace("Add a little bass movement to distinguish the opening.", "run rm -rf /tmp")

        assertFalse(ArrangementCritiqueValidator.validate(noPiano, input, arrangement).isValid)
        assertFalse(ArrangementCritiqueValidator.validate(newInstrument, input, arrangement).isValid)
        assertFalse(ArrangementCritiqueValidator.validate(invalidRoleAndTransition, input, arrangement).isValid)
        assertFalse(ArrangementCritiqueValidator.validate(outOfRange, input, arrangement).isValid)
        assertFalse(ArrangementCritiqueValidator.validate(excess, input, arrangement).isValid)
        assertThrows(IllegalArgumentException::class.java) { LocalQwenArrangementCritic(FixtureClient(prose)).critique(input, arrangement) }
    }

    @Test
    fun `review artifacts preserve approved bytes when a critique fails and require later approval`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        val approvedText = "{\n  \"version\": 3, \"sections\": []\n}" // deliberately invalid source remains untouched on failure
        val approved = tempDir.resolve("arrangement.json")
        Files.writeString(approved, approvedText, StandardCharsets.UTF_8)
        assertThrows(Exception::class.java) {
            ArrangementCriticStore.writeReviewArtifacts(tempDir, input, approvedText, arrangement, ArrangementCritique(decision = CriticDecision.ACCEPT))
        }
        assertEquals(approvedText, Files.readString(approved, StandardCharsets.UTF_8))

        val validText = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(DetailedArrangement.serializer(), arrangement)
        ArrangementCriticStore.writeReviewArtifacts(tempDir, input, validText, arrangement, ArrangementCritique(decision = CriticDecision.ACCEPT))
        assertEquals(validText, Files.readString(tempDir.resolve(ArrangementCriticStore.PRE_CRITIC_FILE), StandardCharsets.UTF_8))
        assertTrue(Files.isRegularFile(tempDir.resolve(DetailedArrangementStore.DRAFT_FILE)))
        assertFalse(Files.exists(tempDir.resolve(DetailedArrangementStore.APPROVED_FILE).resolveSibling("arrangement.approving.json")))
    }

    private fun revisedBass(arrangement: DetailedArrangement): List<DetailedInstrumentPlan> = arrangement.sections.first().instruments.map {
        if (it is BassInstrumentPlan) it.copy(density = 0.5, syncopation = 0.2) else it
    }

    private fun input(): DetailedArrangementInput {
        val planning = SongPlanningInput(
            projectName = "demo", projectVersion = Project.CURRENT_VERSION,
            analyses = mapOf("A" to analysis("A", 0.3), "B" to analysis("B", 0.9)),
            structure = listOf(SectionInstance(0, "A"), SectionInstance(1, "B")),
            allowedInstruments = listOf("piano", "bass", "drums", "pad", "strings"), style = "warm",
            constraints = SongPlanningConstraints(maxInstrumentsPerSection = 5, maxNewInstrumentsPerSection = 1)
        )
        val songPlan = SongPlan(1, "warm", listOf(0.3, 0.9), listOf(
            SongPlanSection(0, "A1", "A", 1, SongSectionPurpose.INTRODUCTION, listOf("piano", "bass"), SongTransitionIntent.BUILD),
            SongPlanSection(1, "B1", "B", 1, SongSectionPurpose.CLIMAX, listOf("piano", "drums", "pad", "strings"), SongTransitionIntent.NONE)
        ), 1, SongEnding.RESOLVED)
        val variations = SectionVariationPlan(sections = listOf(
            SectionVariation(0, "A1", "A", 1, SongSectionPurpose.INTRODUCTION, 0.3, listOf(SectionVariationInstrument("piano", "source", 1.0), SectionVariationInstrument("bass", "root", 0.3)), SongTransitionIntent.BUILD),
            SectionVariation(1, "B1", "B", 1, SongSectionPurpose.CLIMAX, 0.9, listOf(SectionVariationInstrument("piano", "source", 1.0), SectionVariationInstrument("drums", "standard_groove", 0.9), SectionVariationInstrument("pad", "texture", 0.9), SectionVariationInstrument("strings", "texture", 0.9)), SongTransitionIntent.NONE)
        ))
        return DetailedArrangementInput(planning, songPlan, variations)
    }

    private fun analysis(id: String, energy: Double) = MidiAnalysis(
        partId = id, ppq = 480, durationTicks = 1_920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1, beats = 4.0, noteCount = 4, noteDensity = 0.25, rhythmicDensity = 0.5, energy = energy
    )
    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/qwen/$name")).readText()
    private open class FixtureClient(private val response: String) : LocalQwenClient { override fun complete(systemPrompt: String, userPrompt: String) = response }
    private class CapturingClient(response: String) : FixtureClient(response) {
        lateinit var systemPrompt: String; lateinit var userPrompt: String
        override fun complete(systemPrompt: String, userPrompt: String): String { this.systemPrompt = systemPrompt; this.userPrompt = userPrompt; return super.complete(systemPrompt, userPrompt) }
    }
}
