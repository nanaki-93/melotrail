package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ArrangementTest {
    private val json = Json { ignoreUnknownKeys = true }

    @TempDir
    lateinit var projectRoot: Path

    @Test
    fun `deterministic planner preserves every source section and emits fixed generated plans`() {
        val project = project("A", "B")
        val structure = listOf(
            SectionInstance(0, "A"),
            SectionInstance(1, "A"),
            SectionInstance(2, "B")
        )

        val planner: ArrangementPlanner = DeterministicArrangementPlanner()
        val arrangement = planner.plan(
            ArrangementInput(
                project = project,
                analyses = mapOf("A" to analysis()),
                structure = structure,
                requestedInstruments = listOf("piano", "bass"),
                style = "warm"
            )
        )

        assertEquals(Arrangement.CURRENT_VERSION, arrangement.version)
        assertEquals(structure.map { it.partId }, arrangement.sections.map { it.partId })
        assertEquals(structure.map { it.index }, arrangement.sections.map { it.index })
        assertTrue(arrangement.sections.all { section ->
            section.instruments.first() == InstrumentPlan("piano", InstrumentMode.SOURCE)
        })
        assertTrue(arrangement.sections.all { section ->
            section.instruments[1] == InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 0.3)
        })
        assertTrue(arrangement.validate(project.parts.map { it.id }, structure).isValid)
    }

    @Test
    fun `planner uses an explicit source marker when no instruments are requested`() {
        val project = project("A")
        val arrangement = DeterministicArrangementPlanner().plan(
            ArrangementInput(project = project, structure = listOf(SectionInstance(0, "A")))
        )

        assertEquals(listOf(InstrumentPlan("source", InstrumentMode.SOURCE)), arrangement.sections.single().instruments)
    }

    @Test
    fun `validation rejects unknown parts invalid density and unsupported transitions`() {
        val arrangement = Arrangement(
            sections = listOf(
                ArrangementSection(
                    index = 2,
                    partId = "missing",
                    instruments = listOf(
                        InstrumentPlan("piano", InstrumentMode.SOURCE, density = 0.1),
                        InstrumentPlan("bass", InstrumentMode.GENERATED, density = 1.1)
                    ),
                    transitionOut = TransitionPlan(bars = 1)
                )
            )
        )

        val validation = arrangement.validate(setOf("A"))

        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("unknown part ID 'missing'") })
        assertTrue(validation.errors.any { it.contains("density must be between 0 and 1") })
        assertTrue(validation.errors.any { it.contains("source instrument 'piano' must not set density") })
        assertTrue(validation.errors.any { it.contains("must use 0 bars") })
    }

    @Test
    fun `planner rejects invalid input rather than inventing sections or instruments`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DeterministicArrangementPlanner().plan(
                ArrangementInput(
                    project = project("A"),
                    structure = listOf(SectionInstance(0, "missing")),
                    requestedInstruments = listOf("bass", "Bass")
                )
            )
        }

        assertTrue(exception.message.orEmpty().contains("unknown part ID 'missing'"))
        assertTrue(exception.message.orEmpty().contains("Duplicate requested instruments: bass"))
    }

    @Test
    fun `store writes validated arrangement json without changing the source part`() {
        val source = projectRoot.resolve("parts/A.wav")
        Files.createDirectories(source.parent)
        Files.writeString(source, "source audio bytes")
        val sourceBefore = Files.readString(source)
        val project = Project(name = "demo", parts = listOf(Part("A", "parts/A.wav")))
        val arrangement = DeterministicArrangementPlanner().plan(
            ArrangementInput(project = project, structure = listOf(SectionInstance(0, "A")))
        )

        val arrangementPath = ArrangementStore.write(projectRoot, project, arrangement)
        val storedJson = Files.readString(arrangementPath)
        val decoded = json.decodeFromString<Arrangement>(storedJson)

        assertEquals(arrangement, decoded)
        assertEquals(sourceBefore, Files.readString(source))
        assertEquals("arrangement.json", arrangementPath.fileName.toString())
        assertTrue(storedJson.contains("\"version\": 1"))
        assertTrue(storedJson.contains("\"mode\": \"source\""))
        assertFalse(storedJson.contains("\"role\": null"))
    }

    private fun project(vararg ids: String): Project = Project(
        name = "demo",
        parts = ids.map { id -> Part(id, "parts/$id.wav") }
    )

    private fun analysis() = PartAnalysis(
        duration = 1.0,
        sampleRate = 44_100,
        channels = 1,
        frameCount = 44_100,
        peak = 0.5,
        rms = 0.25,
        nearSilence = false
    )
}
