package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GlobalSongPlannerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `deterministic planner handles one section and preserves repeated structure with bounded energy`() {
        val oneSection = DeterministicGlobalSongPlanner().plan(input(structure = listOf(SectionInstance(0, "A", "A1"))))
        assertEquals(listOf("A1"), oneSection.sections.map { it.instanceId })
        assertEquals(0, oneSection.climaxIndex)
        assertEquals(SongSectionPurpose.CLIMAX, oneSection.sections.single().purpose)

        val input = input()
        val plan = DeterministicGlobalSongPlanner().plan(input)

        assertEquals(listOf("A", "A", "B", "B", "A"), plan.sections.map { it.partId })
        assertEquals(listOf("A1", "A2", "B1", "B2", "A3"), plan.sections.map { it.instanceId })
        assertEquals(listOf(1, 2, 1, 2, 3), plan.sections.map { it.occurrence })
        assertEquals(5, plan.energyCurve.size)
        assertTrue(plan.energyCurve.all { it.isFinite() && it in 0.0..1.0 })
        assertEquals(1, plan.sections.count { it.purpose == SongSectionPurpose.CLIMAX })
        assertEquals(SongTransitionIntent.NONE, plan.sections.last().transitionIntent)
        assertTrue(plan.sections.all { section -> section.instrumentProgression.all { it in input.allowedInstruments } })
        assertEquals(plan, DeterministicGlobalSongPlanner().plan(input))
    }

    @Test
    fun `fixture backed Qwen plan is strict and receives no paths`() {
        val client = CapturingFixtureClient(fixture("valid-song-plan.json"))
        val plan = LocalQwenGlobalSongPlanner(client).plan(input())

        assertEquals(3, plan.climaxIndex)
        assertEquals(listOf("A1", "A2", "B1", "B2", "A3"), plan.sections.map { it.instanceId })
        assertTrue(client.systemPrompt.contains("whole-song musical planner"))
        assertTrue(client.systemPrompt.contains("The required response schema is exactly"))
        assertTrue(client.systemPrompt.contains("\"instrumentProgression\""))
        assertTrue(client.systemPrompt.contains("every supplied logical instrument"))
        assertTrue(client.userPrompt.contains("exactly 5 entries"))
        assertTrue(client.userPrompt.contains("Versioned MIDI part analyses"))
        assertTrue(client.userPrompt.contains("\"instanceId\":\"A1\""))
        assertFalse(client.userPrompt.contains("parts/"))
        assertFalse(client.userPrompt.contains("sounds/"))
    }

    @Test
    fun `Qwen structured plan rejects an invalid musical outline`() {
        val input = structuredInput()
        val expected = DeterministicGlobalSongPlanner().plan(input)
        val modelPlan = expected.copy(
            version = 1,
            style = "model text must not persist",
            contextHash = null,
            sections = expected.sections.map { section ->
                section.copy(
                    index = 99,
                    instanceId = "model-id",
                    partId = "model-part",
                    occurrence = 99,
                    purpose = SongSectionPurpose.INTRODUCTION,
                    occurrenceHash = null,
                    soundIntents = emptyList()
                )
            }
        )
        val client = CapturingFixtureClient(Json { encodeDefaults = true }.encodeToString(modelPlan))

        assertThrows(IllegalArgumentException::class.java) { LocalQwenGlobalSongPlanner(client).plan(input) }
        assertTrue(client.userPrompt.contains(input.contextHash().orEmpty()))
        assertTrue(client.userPrompt.contains("melody and harmony = piano"))
        assertTrue(client.userPrompt.contains("texture and ambience = pad"))
        assertTrue(client.systemPrompt.contains("occurrenceHash must be null"))
        assertTrue(client.userPrompt.contains("counter-melody = strings"))
    }

    @Test
    fun `Qwen plan rebinds application owned identity hashes and sound intents`() {
        val input = structuredInput()
        val expected = DeterministicGlobalSongPlanner().plan(input)
        val copiedInputIntents = expected.copy(
            version = 1,
            style = "model text must not persist",
            contextHash = null,
            sections = expected.sections.map { section ->
                section.copy(
                    index = 99,
                    instanceId = "model-id",
                    partId = "model-part",
                    occurrence = 99,
                    occurrenceHash = "0".repeat(64),
                    soundIntents = section.soundIntents.map { it.copy(sectionPurpose = null) }
                )
            }
        )

        val plan = LocalQwenGlobalSongPlanner(
            FixtureClient(Json { encodeDefaults = true }.encodeToString(copiedInputIntents))
        ).plan(input)

        assertEquals(expected, plan)
    }

    @Test
    fun `Qwen song plan retries with the prior validation error`() {
        val prompts = mutableListOf<String>()
        var calls = 0
        val invalid = fixture("valid-song-plan.json").replace("\"bass\"", "\"synth\"")
        val client = LocalQwenClient { _, prompt ->
            prompts += prompt
            if (calls++ == 0) invalid else fixture("valid-song-plan.json")
        }

        val plan = LocalQwenGlobalSongPlanner(client).plan(input())

        assertEquals(2, calls)
        assertEquals(3, plan.climaxIndex)
        assertTrue(prompts[1].contains("Automatic repair attempt 1 of 5"))
        assertTrue(prompts[1].contains("uses instrument 'synth', which is not allowed"))
    }

    @Test
    fun `Qwen rejects malformed prose extra fields unsafe note data and invalid structure values`() {
        listOf(
            "not JSON",
            "```json\n{}\n```",
            fixture("valid-song-plan.json").replace("\"ending\": \"resolved\"", "\"ending\": \"resolved\", \"path\": \"/tmp/model.json\""),
            fixture("valid-song-plan.json").replace("\"ending\": \"resolved\"", "\"ending\": \"resolved\", \"notes\": [{\"pitch\":60}]"),
            fixture("valid-song-plan.json").replace("\"bass\"", "\"synth\""),
            fixture("valid-song-plan.json").replace("\"development\"", "\"freeform\""),
            fixture("valid-song-plan.json").replace("0.20", "1e309"),
            fixture("valid-song-plan.json").replace("\"climaxIndex\": 3", "\"climaxIndex\": 9"),
            "{\"version\":1,\"style\":\"warm melancholic lo-fi piano\",\"energyCurve\":[],\"sections\":[],\"climaxIndex\":0,\"ending\":\"resolved\"}"
        ).forEach { response ->
            assertThrows(IllegalArgumentException::class.java) {
                LocalQwenGlobalSongPlanner(FixtureClient(response)).plan(input())
            }
        }
    }

    @Test
    fun `path command and code-like styles are rejected at the planning boundary`() {
        listOf("/tmp/song", "warm; rm -rf", "function arrange() {}").forEach { style ->
            assertThrows(IllegalArgumentException::class.java) {
                DeterministicGlobalSongPlanner().plan(input().copy(style = style))
            }
        }
    }

    @Test
    fun `failed Qwen response leaves existing song plan unchanged`() {
        val input = input()
        val expected = DeterministicGlobalSongPlanner().plan(input)
        val path = SongPlanStore.write(tempDir, input, expected)
        val before = Files.readString(path)

        assertThrows(IllegalArgumentException::class.java) {
            SongPlanStore.write(
                tempDir,
                input,
                LocalQwenGlobalSongPlanner(FixtureClient("{\"version\":1,\"path\":\"/tmp/nope\"}")).plan(input)
            )
        }

        assertEquals(before, Files.readString(path))
        assertEquals(expected, SongPlanStore.read(tempDir, input))
    }

    private fun input(
        structure: List<SectionInstance> = listOf(
            SectionInstance(0, "A", "A1"), SectionInstance(1, "A", "A2"), SectionInstance(2, "B", "B1"),
            SectionInstance(3, "B", "B2"), SectionInstance(4, "A", "A3")
        )
    ) = SongPlanningInput(
        projectName = "demo",
        projectVersion = Project.CURRENT_VERSION,
        analyses = structure.map { it.partId }.distinct().associateWith { partId ->
            analysis(partId, if (partId == "B") 0.85 else 0.25)
        },
        structure = structure,
        allowedInstruments = listOf("piano", "bass", "pad"),
        style = "warm melancholic lo-fi piano"
    )

    private fun structuredInput(): SongPlanningInput {
        val profile = CompositionProfileRef("lofi", 1)
        val mood = MoodRef("nostalgic", 1)
        return input().copy(
            allowedInstruments = listOf("piano", "bass", "pad", "strings"),
            style = null,
            soundContext = ArrangementSoundContext(profile, mood, "C-major-v1", 4, 4, "a".repeat(64)),
            requestedIntents = listOf(
                InstrumentIntent(role = ArrangementRole.MELODY, profile = profile, mood = mood),
                InstrumentIntent(role = ArrangementRole.BASS, profile = profile, mood = mood),
                InstrumentIntent(role = ArrangementRole.COUNTER_MELODY, profile = profile, mood = mood),
                InstrumentIntent(role = ArrangementRole.AMBIENCE, profile = profile, mood = mood)
            )
        )
    }

    private fun analysis(partId: String, energy: Double) = MidiAnalysis(
        partId = partId,
        ppq = 480,
        durationTicks = 1_920,
        durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)),
        timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1,
        beats = 4.0,
        noteCount = 4,
        noteDensity = 0.25,
        rhythmicDensity = 0.5,
        energy = energy
    )

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/qwen/$name")).readText()

    private open class FixtureClient(private val response: String) : LocalQwenClient {
        override fun complete(systemPrompt: String, userPrompt: String): String = response
    }

    private class CapturingFixtureClient(response: String) : FixtureClient(response) {
        lateinit var systemPrompt: String
        lateinit var userPrompt: String

        override fun complete(systemPrompt: String, userPrompt: String): String {
            this.systemPrompt = systemPrompt
            this.userPrompt = userPrompt
            return super.complete(systemPrompt, userPrompt)
        }
    }
}
