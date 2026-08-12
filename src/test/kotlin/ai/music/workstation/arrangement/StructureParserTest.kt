package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StructureParserTest {
    private val validPartIds = setOf("A", "B", "C")

    @Test
    fun `parses normalized whitespace in the original order`() {
        val sections = StructureParser.parse("  A\nA   B\tB A C B  ", validPartIds)

        assertEquals(listOf("A", "A", "B", "B", "A", "C", "B"), sections.map { it.partId })
        assertEquals((0..6).toList(), sections.map { it.index })
    }

    @Test
    fun `expands repeated section shorthand with stable indexes`() {
        val sections = StructureParser.parse("A*2 B*2 A C*2", validPartIds)

        assertEquals(listOf("A", "A", "B", "B", "A", "C", "C"), sections.map { it.partId })
        assertEquals((0..6).toList(), sections.map { it.index })
    }

    @Test
    fun `parses against part ids declared by a project`() {
        val project = Project(
            name = "demo",
            parts = listOf(Part("A", "parts/A.wav"), Part("B", "parts/B.wav"))
        )

        val sections = StructureParser.parse("B A B", project)

        assertEquals(listOf("B", "A", "B"), sections.map { it.partId })
    }

    @Test
    fun `rejects unknown ids and invalid repetition syntax`() {
        val unknown = assertThrows(IllegalArgumentException::class.java) {
            StructureParser.parse("A D", validPartIds)
        }
        val invalidRepeat = assertThrows(IllegalArgumentException::class.java) {
            StructureParser.parse("A*0", validPartIds)
        }
        val malformedRepeat = assertThrows(IllegalArgumentException::class.java) {
            StructureParser.parse("A*two", validPartIds)
        }

        assertEquals("Unknown part ID in structure: D", unknown.message)
        assertEquals("Repetition count must be positive: A*0", invalidRepeat.message)
        assertEquals("Invalid repeated structure token: A*two", malformedRepeat.message)
    }

    @Test
    fun `rejects empty input`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            StructureParser.parse("  \n\t", validPartIds)
        }

        assertEquals("Structure must not be empty", exception.message)
    }
}
