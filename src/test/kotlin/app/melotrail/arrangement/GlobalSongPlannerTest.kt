package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    fun `different source analyses produce different reviewable global arrangement fingerprints`() {
        val quiet = input()
        val energetic = quiet.copy(analyses = quiet.analyses.mapValues { (partId, analysis) ->
            analysis.copy(energy = if (partId == "A") 0.95 else 0.05)
        })

        val quietPlan = DeterministicGlobalSongPlanner().plan(quiet)
        val energeticPlan = DeterministicGlobalSongPlanner().plan(energetic)

        assertFalse(quietPlan == energeticPlan)
        assertFalse(Json { encodeDefaults = true }.encodeToString(quietPlan) == Json { encodeDefaults = true }.encodeToString(energeticPlan))
    }

    @Test
    fun `fixture backed Qwen plan is strict and receives no paths`() {
        val client = CapturingFixtureClient(fixture("valid-song-plan.json"))
        val plan = LocalQwenGlobalSongPlanner(client).plan(input())

        assertEquals(3, plan.climaxIndex)
        assertEquals(listOf("A1", "A2", "B1", "B2", "A3"), plan.sections.map { it.instanceId })
        assertTrue(client.systemPrompt.contains("whole-song musical planner"))
        assertTrue(client.systemPrompt.contains("NON-EXECUTABLE SCHEMA ILLUSTRATION ONLY"))
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
    fun `Qwen enhanced plan tolerates only legacy application-owned musical-intent context hash`() {
        val input = enhancedInput()
        val expected = DeterministicGlobalSongPlanner().plan(input)
        val json = Json { encodeDefaults = true }
        val root = json.parseToJsonElement(json.encodeToString(expected)).jsonObject
        val legacy = JsonObject(root + ("sections" to JsonArray(root.getValue("sections").jsonArray.map { rawSection ->
            val section = rawSection.jsonObject
            val intent = section.getValue("musicalIntent").jsonObject
            JsonObject(section + ("musicalIntent" to JsonObject(intent + ("contextHash" to JsonNull))) + ("contextHash" to JsonNull))
        }))).toString()

        val plan = LocalQwenGlobalSongPlanner(FixtureClient(legacy)).plan(input)

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
    fun `enhanced occurrence planning carries profile groove voicing and intentional section contrast`() {
        val input = enhancedInput()
        val plan = DeterministicGlobalSongPlanner().plan(input)
        val variations = DeterministicSectionVariationPlanner.plan(input, plan)

        assertEquals(
            listOf(SongSectionPurpose.INTRODUCTION, SongSectionPurpose.DEVELOPMENT, SongSectionPurpose.CLIMAX, SongSectionPurpose.CONCLUSION),
            plan.sections.map { it.purpose }
        )
        assertTrue(plan.energyCurve.distinct().size >= 3)
        assertTrue(plan.sections.all { it.musicalIntent?.profile == input.soundContext?.profile })
        assertTrue(plan.sections.all { it.musicalIntent?.grooveMapSha256 == input.grooveMapHash() })
        assertEquals(listOf(55, 60, 64), plan.sections[1].musicalIntent?.previousAcceptedVoicing?.pad)
        assertEquals(listOf(55, 60, 64), plan.sections[2].musicalIntent?.previousAcceptedVoicing?.pad)
        assertEquals(listOf(62, 67, 71), plan.sections[2].musicalIntent?.previousAcceptedVoicing?.strings)
        assertTrue(plan.sections.flatMap { it.musicalIntent!!.roles }.any { it.role == ArrangementRole.BASS && it.groove.timingPolicy == GrooveTimingPolicy.FOLLOW_SOURCE_SUBTLE })
        assertTrue(plan.sections.flatMap { it.musicalIntent!!.roles }.any { it.role == ArrangementRole.DRUMS && it.groove.timingPolicy == GrooveTimingPolicy.FOLLOW_SOURCE_STANDARD })
        assertTrue(variations.sections.map { section -> section.instruments.joinToString { "${it.role}:${it.density}:${it.articulation}" } }.distinct().size >= 3)

        val flattened = variations.copy(sections = variations.sections.map { section ->
            section.copy(instruments = section.instruments.map { instrument ->
                if (instrument.name == "bass") instrument.copy(role = "root", density = 0.4) else instrument
            })
        })
        assertFalse(flattened.validate(input, plan).isValid)
    }

    @Test
    fun `Qwen flat schema defaults are rejected for enhanced occurrence planning`() {
        val input = enhancedInput()
        val baseline = DeterministicGlobalSongPlanner().plan(input)
        val flat = baseline.copy(energyCurve = List(baseline.sections.size) { 0.4 })

        assertThrows(IllegalArgumentException::class.java) {
            LocalQwenGlobalSongPlanner(FixtureClient(Json { encodeDefaults = true }.encodeToString(flat))).plan(input)
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

    private fun enhancedInput(): SongPlanningInput {
        val profile = CompositionProfileRef("lofi", 1)
        val mood = MoodRef("nostalgic", 1)
        val structure = listOf(
            SectionInstance(0, "A", "A1"), SectionInstance(1, "B", "B1"),
            SectionInstance(2, "C", "C1"), SectionInstance(3, "D", "D1")
        )
        return SongPlanningInput(
            projectName = "enhanced",
            projectVersion = Project.CURRENT_VERSION,
            analyses = structure.associate { section ->
                section.partId to analysis(section.partId, mapOf("A" to 0.10, "B" to 0.35, "C" to 0.95, "D" to 0.20).getValue(section.partId))
            },
            structure = structure,
            allowedInstruments = listOf("piano", "bass", "drums", "pad", "strings"),
            soundContext = ArrangementSoundContext(profile, mood, "C-major-v1", 4, 4, "a".repeat(64)),
            requestedIntents = listOf(
                ArrangementRole.MELODY, ArrangementRole.BASS, ArrangementRole.DRUMS, ArrangementRole.TEXTURE, ArrangementRole.COUNTER_MELODY
            ).map { role -> InstrumentIntent(role = role, profile = profile, mood = mood) },
            acceptedFullSongGrooveMap = grooveMap(structure),
            acceptedOccurrenceVoicings = listOf(
                AcceptedOccurrenceVoicing("A1", AcceptedPadStringVoicing(pad = listOf(55, 60, 64))),
                AcceptedOccurrenceVoicing("B1", AcceptedPadStringVoicing(strings = listOf(62, 67, 71)))
            ),
            constraints = SongPlanningConstraints(maxInstrumentsPerSection = 5, maxNewInstrumentsPerSection = 2)
        )
    }

    private fun grooveMap(structure: List<SectionInstance>): FullSongGrooveMap = FullSongGrooveMap(
        ppq = 480,
        meterDenominator = 4,
        subdivisionsPerBeat = 4,
        points = structure.mapIndexed { index, section -> FullSongGroovePoint(section.instanceId, 0, 0, index * 1_920L, 0) },
        occurrenceTemplateFingerprints = structure.map { section -> FullSongGrooveOccurrenceTemplate(section.instanceId, section.partId, "b".repeat(64)) },
        boundaries = structure.zipWithNext().mapIndexed { index, (outgoing, incoming) ->
            FullSongGrooveBoundary("boundary-$index", (index + 1) * 1_920L, outgoing.instanceId, incoming.instanceId, 0, 0, FullSongGrooveBoundaryStatus.CONTINUOUS)
        },
        maximumUnreviewedDiscontinuityTicks = 24
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
