package app.melotrail.arrangement

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
        val oneSection = DeterministicGlobalSongPlanner().plan(input(structure = listOf(SectionInstance(0, "A"))))
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
    fun `Qwen rejects malformed prose extra fields unsafe note data and invalid structure values`() {
        listOf(
            "not JSON",
            "```json\n{}\n```",
            fixture("valid-song-plan.json").replace("\"ending\": \"resolved\"", "\"ending\": \"resolved\", \"path\": \"/tmp/model.json\""),
            fixture("valid-song-plan.json").replace("\"ending\": \"resolved\"", "\"ending\": \"resolved\", \"notes\": [{\"pitch\":60}]"),
            fixture("valid-song-plan.json").replace("\"bass\"", "\"synth\""),
            fixture("valid-song-plan.json").replace("\"development\"", "\"freeform\""),
            fixture("valid-song-plan.json").replace("0.20", "1e309"),
            fixture("valid-song-plan.json").replace("\"index\": 1", "\"index\": 9"),
            fixture("valid-song-plan.json").replace("\"instanceId\": \"A2\", \"partId\": \"A\"", "\"instanceId\": \"A2\", \"partId\": \"B\""),
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
            SectionInstance(0, "A"), SectionInstance(1, "A"), SectionInstance(2, "B"),
            SectionInstance(3, "B"), SectionInstance(4, "A")
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
