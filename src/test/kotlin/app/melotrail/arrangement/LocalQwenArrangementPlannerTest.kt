package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalQwenArrangementPlannerTest {
    @Test
    fun `fixture response plans only allowed instruments and receives safe project metadata`() {
        val fixture = fixture("valid-arrangement.json")
        val client = CapturingFixtureClient(fixture)
        val arrangement = LocalQwenArrangementPlanner(client).plan(input())

        assertEquals(listOf("A", "B"), arrangement.sections.map { it.partId })
        assertEquals(listOf("piano", "bass"), arrangement.sections.first().instruments.map { it.name })
        assertTrue(client.systemPrompt.contains("Return only a valid JSON arrangement"))
        assertTrue(client.userPrompt.contains("\"partId\":\"A\""))
        assertTrue(client.userPrompt.contains("\"sampleRate\":44100"))
        assertTrue(client.userPrompt.contains("[\"piano\",\"bass\"]"))
        assertFalse(client.userPrompt.contains("parts/A.wav"))
    }

    @Test
    fun `fixture response rejects instruments outside the allow list`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            LocalQwenArrangementPlanner(FixtureClient(fixture("disallowed-instrument.json"))).plan(input())
        }

        assertTrue(exception.message.orEmpty().contains("instrument 'synth', which is not allowed"))
    }

    @Test
    fun `fixture response rejects extra path fields instead of accepting model supplied paths`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            LocalQwenArrangementPlanner(FixtureClient(fixture("unexpected-path.json"))).plan(input())
        }

        assertTrue(exception.message.orEmpty().contains("Qwen returned invalid arrangement JSON"))
        assertTrue(exception.message.orEmpty().contains("path"))
    }

    @Test
    fun `Qwen arrangement makes five automatic correction requests before failing`() {
        var calls = 0
        val prompts = mutableListOf<String>()
        val client = LocalQwenClient { _, prompt ->
            calls++
            prompts += prompt
            fixture("disallowed-instrument.json")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            LocalQwenArrangementPlanner(client).plan(input())
        }

        assertEquals(QWEN_MAX_ATTEMPTS, calls)
        assertTrue(error.message.orEmpty().contains("after 5 automatic retries"))
        assertTrue(prompts.last().contains("Automatic repair attempt 5 of 5"))
    }

    private fun input() = ArrangementInput(
        project = Project(
            name = "demo",
            parts = listOf(
                Part("A", "parts/A.wav", "verse"),
                Part("B", "parts/B.wav", "chorus")
            )
        ),
        analyses = mapOf(
            "A" to PartAnalysis(1.0, 44_100, 1, 44_100, 0.5, 0.2, false),
            "B" to PartAnalysis(2.0, 48_000, 2, 96_000, 0.6, 0.3, false)
        ),
        structure = listOf(SectionInstance(0, "A", "A1"), SectionInstance(1, "B", "B1")),
        requestedInstruments = listOf("piano", "bass"),
        style = "warm"
    )

    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResource("/fixtures/qwen/$name")
    ).readText()

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
