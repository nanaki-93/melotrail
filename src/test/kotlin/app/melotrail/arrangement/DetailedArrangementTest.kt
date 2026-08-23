package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

class DetailedArrangementTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `deterministic v4 expansion is decision complete and round trips`() {
        val input = input()
        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)

        assertEquals(4, arrangement.version)
        assertEquals(listOf("A1", "B1"), arrangement.sections.map { it.instanceId })
        assertEquals(listOf(SongSectionPurpose.INTRODUCTION, SongSectionPurpose.CLIMAX), arrangement.sections.map { it.role })
        assertTrue(arrangement.sections.all { it.instruments.singleOrNull { plan -> plan is PianoSourcePlan }?.mode == InstrumentMode.SOURCE })
        assertEquals(DetailedBassMovement.ROOT_MOTION.name, (arrangement.sections.first().instruments[1] as BassInstrumentPlan).movement.name)
        assertEquals(MusicalRegister.LOW, (arrangement.sections.first().instruments[1] as BassInstrumentPlan).register)
        assertEquals(TransitionType.BRIDGE, arrangement.sections.first().transitionOut.type)
        assertEquals(listOf(BridgeElement.DRUM_FILL, BridgeElement.BASS_PICKUP), arrangement.sections.first().transitionOut.bridge?.elements)
        assertTrue(arrangement.validate(input).isValid)
        assertEquals(arrangement, json.decodeFromString<DetailedArrangement>(json.encodeToString(arrangement)))
    }

    @Test
    fun `legacy v1 and v2 documents remain readable`() {
        val v1 = json.decodeFromString<Arrangement>("""{"version":1,"sections":[{"index":0,"partId":"A","instruments":[{"name":"source","mode":"source"}]}]}""")
        val v2 = json.decodeFromString<Arrangement>("""{"version":2,"sections":[{"index":0,"partId":"A","instruments":[{"name":"piano","mode":"source"}],"transitionOut":{"type":"crossfade","bars":0,"crossfadeMs":180}}]}""")

        assertTrue(v1.validate(setOf("A")).isValid)
        assertTrue(v2.validate(setOf("A")).isValid)
    }

    @Test
    fun `validator rejects structure source and typed role violations including non finite values`() {
        val input = input()
        val valid = DeterministicDetailedArrangementPlanner().plan(input)
        val wrongIdentity = valid.copy(sections = valid.sections.mapIndexed { index, section ->
            if (index == 1) section.copy(instanceId = "A2") else section
        })
        val duplicatePiano = valid.copy(sections = valid.sections.mapIndexed { index, section ->
            if (index == 0) section.copy(instruments = listOf(PianoSourcePlan(), PianoSourcePlan())) else section
        })
        val invalidBass = valid.copy(sections = valid.sections.mapIndexed { index, section ->
            if (index == 0) section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is BassInstrumentPlan) instrument.copy(name = "synth") else instrument
            }) else section
        })
        val nan = valid.copy(sections = valid.sections.mapIndexed { index, section -> if (index == 0) section.copy(energy = Double.NaN) else section })

        listOf(wrongIdentity, duplicatePiano, invalidBass, nan).forEach { candidate ->
            assertFalse(candidate.validate(input).isValid)
        }

        val unsupportedBassControls = valid.copy(sections = valid.sections.mapIndexed { index, section ->
            if (index == 0) section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is BassInstrumentPlan) instrument.copy(register = MusicalRegister.MID, syncopation = 0.26) else instrument
            }) else section
        })
        assertFalse(unsupportedBassControls.validate(input).isValid)
    }

    @Test
    fun `fixture backed Qwen v4 output is strict and cannot include unsafe fields`() {
        val input = input()
        val fixture = fixture("valid-detailed-arrangement.json")
        val client = CapturingClient(fixture)
        val arrangement = LocalQwenDetailedArrangementPlanner(client).plan(input)

        assertTrue(arrangement.validate(input).isValid)
        assertTrue(client.systemPrompt.contains("never provide notes"))
        assertTrue(client.systemPrompt.contains("Instrument objects are a tagged union"))
        assertTrue(client.systemPrompt.contains("\"kind\":\"strings\""))
        assertTrue(client.systemPrompt.contains("transitionOut is also a union"))
        assertTrue(client.systemPrompt.contains("0..0.25"))
        assertTrue(client.systemPrompt.contains("Bass must use register low"))
        assertTrue(client.userPrompt.contains("exactly 2 sections"))
        assertTrue(client.userPrompt.contains("MIDI analysis facts by part"))
        assertFalse(client.userPrompt.contains("midi/"))

        listOf("path", "notes", "command", "renderer", "outputPath").forEach { field ->
            val invalid = fixture.replace("\"version\": 4", "\"version\": 4, \"$field\": \"unsafe\"")
            assertThrows(IllegalArgumentException::class.java) {
                LocalQwenDetailedArrangementPlanner(FixtureClient(invalid)).plan(input)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenDetailedArrangementPlanner(FixtureClient(fixture.replace("0.3", "1e309"))).plan(input)
        }
    }

    @Test
    fun `Qwen detailed arrangement rejects changed locked bass and drum fields`() {
        val input = input()
        val fixture = json.decodeFromString<DetailedArrangement>(fixture("valid-detailed-arrangement.json"))
        val response = fixture.copy(sections = fixture.sections.mapIndexed { index, section ->
            if (index == 0) section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is BassInstrumentPlan) instrument.copy(role = DetailedBassRole.OCTAVE, mode = InstrumentMode.SOURCE) else instrument
            }) else section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is DrumsInstrumentPlan) instrument.copy(role = DrumsRole.SOFT_LOFI, mode = InstrumentMode.SOURCE) else instrument
            })
        })
        val client = CapturingClient(json.encodeToString(response))

        assertThrows(IllegalArgumentException::class.java) { LocalQwenDetailedArrangementPlanner(client).plan(input) }
        assertTrue(client.userPrompt.contains("Locked instrument values"))
    }

    @Test
    fun `Qwen detailed arrangement retries with the prior validation error`() {
        val valid = fixture("valid-detailed-arrangement.json")
        val invalid = valid.replace("0.3", "1e309")
        val prompts = mutableListOf<String>()
        var calls = 0
        val client = LocalQwenClient { _, prompt ->
            prompts += prompt
            if (calls++ == 0) invalid else valid
        }

        val arrangement = LocalQwenDetailedArrangementPlanner(client).plan(input())

        assertEquals(2, calls)
        assertTrue(arrangement.validate(input()).isValid)
        assertTrue(prompts[1].contains("Automatic repair attempt 1 of 5"))
        assertTrue(prompts[1].contains("invalid detailed-arrangement JSON"))
    }

    @Test
    fun `Qwen detailed arrangement discards instruments outside the validated variation`() {
        val input = input()
        val fixture = json.decodeFromString<DetailedArrangement>(fixture("valid-detailed-arrangement.json"))
        val response = fixture.copy(sections = fixture.sections.mapIndexed { index, section ->
            if (index == 1) section.copy(instruments = section.instruments + BassInstrumentPlan(
                role = DetailedBassRole.ROOT,
                density = 0.4,
                movement = DetailedBassMovement.ROOT_MOTION,
                register = MusicalRegister.LOW,
                syncopation = 0.1
            )) else section
        })

        val arrangement = LocalQwenDetailedArrangementPlanner(
            FixtureClient(json.encodeToString(response))
        ).plan(input)

        assertTrue(arrangement.validate(input).isValid)
        assertEquals(listOf("piano", "drums", "pad", "strings"), arrangement.sections[1].instruments.map { it.name })
    }

    @Test
    fun `Qwen selects bounded arrangement variation and rejects omitted pattern controls`() {
        val input = input()
        val model = json.decodeFromString<DetailedArrangement>(fixture("valid-detailed-arrangement.json"))
        val varied = model.copy(sections = model.sections.mapIndexed { index, section ->
            if (index == 0) section.copy(instruments = section.instruments.map { instrument ->
                if (instrument is BassInstrumentPlan) instrument.copy(
                    role = DetailedBassRole.OCTAVE,
                    density = 0.67,
                    movement = DetailedBassMovement.OCTAVES,
                    pattern = BassPatternId.DIATONIC_APPROACH
                ) else instrument
            }) else section
        })

        val arrangement = LocalQwenDetailedArrangementPlanner(FixtureClient(json.encodeToString(varied))).plan(input)
        val bass = arrangement.sections.first().instruments.filterIsInstance<BassInstrumentPlan>().single()
        assertEquals(DetailedBassRole.OCTAVE, bass.role)
        assertEquals(0.67, bass.density)
        assertEquals(BassPatternId.DIATONIC_APPROACH, bass.pattern)

        val missingPattern = fixture("valid-detailed-arrangement.json").replace(
            ", \"pattern\": \"sustained-root\"", ""
        )
        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenDetailedArrangementPlanner(FixtureClient(missingPattern)).plan(input)
        }
    }

    @Test
    fun `draft approval is atomic and preserves approved arrangement after a failed validation`() {
        val input = input()
        val approved = tempDir.resolve(DetailedArrangementStore.APPROVED_FILE)
        Files.writeString(approved, "approved before failure", StandardCharsets.UTF_8)
        Files.writeString(tempDir.resolve(DetailedArrangementStore.DRAFT_FILE), """{"version":3,"path":"/tmp/nope"}""", StandardCharsets.UTF_8)

        assertThrows(Exception::class.java) { DetailedArrangementStore.approve(tempDir, input) }
        assertEquals("approved before failure", Files.readString(approved, StandardCharsets.UTF_8))

        val arrangement = DeterministicDetailedArrangementPlanner().plan(input)
        DetailedArrangementStore.writeDraft(tempDir, input, arrangement)
        val approvedPath = DetailedArrangementStore.approve(tempDir, input)
        assertEquals(arrangement, json.decodeFromString<DetailedArrangement>(Files.readString(approvedPath, StandardCharsets.UTF_8)))
        assertTrue(Files.isRegularFile(tempDir.resolve(DetailedArrangementStore.DRAFT_FILE)))
    }

    private fun input(): DetailedArrangementInput {
        val planningInput = SongPlanningInput(
            projectName = "demo",
            projectVersion = Project.CURRENT_VERSION,
            analyses = mapOf("A" to analysis("A", 0.3), "B" to analysis("B", 0.9)),
            structure = listOf(SectionInstance(0, "A", "A1"), SectionInstance(1, "B", "B1")),
            allowedInstruments = listOf("piano", "bass", "drums", "pad", "strings"),
            style = "warm",
            constraints = SongPlanningConstraints(maxInstrumentsPerSection = 5, maxNewInstrumentsPerSection = 1)
        )
        val songPlan = SongPlan(
            version = 1,
            style = "warm",
            energyCurve = listOf(0.3, 0.9),
            sections = listOf(
                SongPlanSection(0, "A1", "A", 1, SongSectionPurpose.INTRODUCTION, listOf("piano", "bass"), SongTransitionIntent.BUILD),
                SongPlanSection(1, "B1", "B", 1, SongSectionPurpose.CLIMAX, listOf("piano", "drums", "pad", "strings"), SongTransitionIntent.NONE)
            ),
            climaxIndex = 1,
            ending = SongEnding.RESOLVED
        )
        val variations = SectionVariationPlan(sections = listOf(
            SectionVariation(0, "A1", "A", 1, SongSectionPurpose.INTRODUCTION, 0.3, listOf(
                SectionVariationInstrument("piano", "source", 1.0), SectionVariationInstrument("bass", "root", 0.3)
            ), SongTransitionIntent.BUILD),
            SectionVariation(1, "B1", "B", 1, SongSectionPurpose.CLIMAX, 0.9, listOf(
                SectionVariationInstrument("piano", "source", 1.0), SectionVariationInstrument("drums", "standard_groove", 0.9),
                SectionVariationInstrument("pad", "texture", 0.9), SectionVariationInstrument("strings", "texture", 0.9)
            ), SongTransitionIntent.NONE)
        ))
        return DetailedArrangementInput(planningInput, songPlan, variations)
    }

    private fun analysis(partId: String, energy: Double) = MidiAnalysis(
        partId = partId, ppq = 480, durationTicks = 1_920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1, beats = 4.0, noteCount = 4, noteDensity = 0.25, rhythmicDensity = 0.5, energy = energy
    )

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/qwen/$name")).readText()

    private open class FixtureClient(private val response: String) : LocalQwenClient {
        override fun complete(systemPrompt: String, userPrompt: String): String = response
    }

    private class CapturingClient(response: String) : FixtureClient(response) {
        lateinit var systemPrompt: String
        lateinit var userPrompt: String
        override fun complete(systemPrompt: String, userPrompt: String): String {
            this.systemPrompt = systemPrompt
            this.userPrompt = userPrompt
            return super.complete(systemPrompt, userPrompt)
        }
    }
}
