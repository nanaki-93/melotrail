package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class SectionVariationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `identities preserve order occurrences and multi digit repetitions`() {
        val repeated = List(12) { index -> SectionInstance(index, "A", "A${index + 1}") }
        val identities = SongPlanningSectionInstances.create(repeated)

        assertEquals((1..12).toList(), identities.map { it.occurrence })
        assertEquals(listOf("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12"), identities.map { it.instanceId })
        assertEquals(listOf("A1", "B1"), SongPlanningSectionInstances.create(listOf(SectionInstance(0, "A", "A1"), SectionInstance(1, "B", "B1"))).map { it.instanceId })
    }

    @Test
    fun `variations are deterministic bounded and follow explicit progression`() {
        val input = input()
        val songPlan = songPlan(input)
        val variations = DeterministicSectionVariationPlanner.plan(input, songPlan)

        assertEquals(listOf("A1", "A2", "B1", "B2", "A3"), variations.sections.map { it.instanceId })
        assertEquals(listOf(1, 2, 1, 2, 3), variations.sections.map { it.occurrence })
        assertEquals(listOf(1, 2, 3, 2, 1), variations.sections.map { it.instruments.size })
        assertTrue(variations.sections.all { it.instruments.first().name == "piano" && it.instruments.first().role == "source" })
        assertTrue(variations.sections.flatMap { it.instruments }.all { it.density in 0.0..1.0 && it.density.isFinite() })
        assertTrue(variations.sections[2].instruments.size > variations.sections[0].instruments.size)
        assertTrue(variations.sections[4].instruments.size < variations.sections[2].instruments.size)
        assertEquals(variations, DeterministicSectionVariationPlanner.plan(input, songPlan))
    }

    @Test
    fun `validator rejects unknown details structure mutations and accidental repeated copies`() {
        val input = input()
        val songPlan = songPlan(input)
        val variations = DeterministicSectionVariationPlanner.plan(input, songPlan)

        val invalidInstrument = variations.copy(sections = variations.sections.mapIndexed { index, section ->
            if (index == 1) section.copy(instruments = section.instruments.dropLast(1) + SectionVariationInstrument("synth", "texture", 0.5)) else section
        })
        assertFalse(invalidInstrument.validate(input, songPlan).isValid)

        val invalidRole = variations.copy(sections = variations.sections.mapIndexed { index, section ->
            if (index == 1) section.copy(instruments = section.instruments.map { instrument ->
                if (instrument.name == "bass") instrument.copy(role = "freeform") else instrument
            }) else section
        })
        assertFalse(invalidRole.validate(input, songPlan).isValid)

        val reordered = variations.copy(sections = variations.sections.reversed())
        assertFalse(reordered.validate(input, songPlan).isValid)

        val copiedDetails = variations.copy(sections = variations.sections.map { section ->
            if (section.partId == "A") section.copy(instruments = listOf(SectionVariationInstrument("piano", "source", 1.0), SectionVariationInstrument("bass", "root", 0.5))) else section
        })
        assertFalse(copiedDetails.validate(input, songPlan).isValid)
    }

    @Test
    fun `variation persistence never changes source MIDI bytes`() {
        val sourceMidi = tempDir.resolve("source/A.mid")
        val cleanMidi = tempDir.resolve("midi/clean/A.mid")
        Files.createDirectories(sourceMidi.parent)
        Files.createDirectories(cleanMidi.parent)
        Files.write(sourceMidi, byteArrayOf(1, 2, 3, 4))
        Files.write(cleanMidi, byteArrayOf(5, 6, 7, 8))
        val sourceHash = sha256(sourceMidi)
        val cleanHash = sha256(cleanMidi)
        val input = input()
        val songPlan = songPlan(input)

        val variations = DeterministicSectionVariationPlanner.plan(input, songPlan)
        SectionVariationStore.write(tempDir, input, songPlan, variations)

        assertEquals(sourceHash, sha256(sourceMidi))
        assertEquals(cleanHash, sha256(cleanMidi))
        assertEquals(variations, SectionVariationStore.read(tempDir, input, songPlan))
    }

    private fun input() = SongPlanningInput(
        projectName = "demo",
        projectVersion = Project.CURRENT_VERSION,
        analyses = mapOf("A" to analysis("A", 0.25), "B" to analysis("B", 0.85)),
        structure = listOf(
            SectionInstance(0, "A", "A1"), SectionInstance(1, "A", "A2"), SectionInstance(2, "B", "B1"),
            SectionInstance(3, "B", "B2"), SectionInstance(4, "A", "A3")
        ),
        allowedInstruments = listOf("piano", "bass", "pad"),
        style = "warm"
    )

    private fun songPlan(input: SongPlanningInput): SongPlan = SongPlan(
        version = SongPlan.CURRENT_VERSION,
        style = input.resolvedStyle,
        energyCurve = listOf(0.20, 0.50, 0.85, 0.45, 0.20),
        sections = listOf(
            section(0, "A1", "A", 1, SongSectionPurpose.INTRODUCTION, listOf("piano"), SongTransitionIntent.NONE),
            section(1, "A2", "A", 2, SongSectionPurpose.DEVELOPMENT, listOf("piano", "bass"), SongTransitionIntent.BUILD),
            section(2, "B1", "B", 1, SongSectionPurpose.CLIMAX, listOf("piano", "bass", "pad"), SongTransitionIntent.RELEASE),
            section(3, "B2", "B", 2, SongSectionPurpose.RELEASE, listOf("piano", "bass"), SongTransitionIntent.RELEASE),
            section(4, "A3", "A", 3, SongSectionPurpose.CONCLUSION, listOf("piano"), SongTransitionIntent.NONE)
        ),
        climaxIndex = 2,
        ending = SongEnding.RESOLVED
    )

    private fun section(
        index: Int,
        instanceId: String,
        partId: String,
        occurrence: Int,
        purpose: SongSectionPurpose,
        instruments: List<String>,
        transition: SongTransitionIntent
    ) = SongPlanSection(index, instanceId, partId, occurrence, purpose, instruments, transition)

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

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
