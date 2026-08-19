package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArrangementRoleIntentTest {
    private val profile = CompositionProfileRef("lofi", 1)
    private val mood = MoodRef("nostalgic", 1)
    private val context = ArrangementSoundContext(profile, mood, "C-major-v1", 4, 4, "a".repeat(64))

    @Test
    fun `structured request preserves profile mood meter and a user-pinned stable ID`() {
        val intent = InstrumentIntent(
            role = ArrangementRole.BASS, profile = profile, mood = mood,
            sectionPurpose = SongSectionPurpose.DEVELOPMENT,
            attackTraits = setOf(SoundTrait.SOFT), toneTraits = setOf(SoundTrait.WARM),
            articulationTraits = setOf(SoundTrait.SUSTAINED),
            requiredCapabilities = setOf(PerformanceCapability.PITCHED, PerformanceCapability.SUSTAIN),
            pinnedInstrumentId = "acoustic-bass-v1", userOwned = true
        )

        context.requireValid(); intent.requireValid()
        assertEquals("acoustic-bass-v1", intent.pinnedInstrumentId)
        assertEquals(MoodRef("nostalgic", 1), intent.mood)
        assertEquals(4, context.meterNumerator)
    }

    @Test
    fun `intent rejects paths filenames and invalid user ownership`() {
        listOf("/tmp/bass", "bass.sfz", "samples/bass", "Bass").forEach { pinned ->
            assertThrows(IllegalArgumentException::class.java) {
                InstrumentIntent(role = ArrangementRole.BASS, profile = profile, mood = mood, pinnedInstrumentId = pinned).requireValid()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstrumentIntent(role = ArrangementRole.BASS, profile = profile, mood = mood, userOwned = true).requireValid()
        }
    }

    @Test
    fun `strict JSON rejects unknown traits rather than accepting planner prose`() {
        val value = """{"role":"bass","profile":{"id":"lofi","version":1},"mood":{"id":"nostalgic","version":1},"toneTraits":["filename-or-prompt"]}"""
        assertThrows(Exception::class.java) { Json { ignoreUnknownKeys = false }.decodeFromString(InstrumentIntent.serializer(), value) }
    }

    @Test
    fun `legacy logical names have stable role compatibility aliases`() {
        assertEquals(ArrangementRole.MELODY, LegacyLogicalInstrumentRoles.roleFor("piano"))
        assertEquals(ArrangementRole.BASS, LegacyLogicalInstrumentRoles.roleFor("bass"))
        assertEquals(ArrangementRole.DRUMS, LegacyLogicalInstrumentRoles.roleFor("drums"))
        assertEquals(ArrangementRole.TEXTURE, LegacyLogicalInstrumentRoles.roleFor("pad"))
        assertEquals(ArrangementRole.COUNTER_MELODY, LegacyLogicalInstrumentRoles.roleFor("strings"))
    }

    @Test
    fun `deterministic planner emits structured roles and intents without a style string`() {
        val input = SongPlanningInput(
            projectName = "structured", projectVersion = Project.CURRENT_VERSION,
            analyses = mapOf("A" to analysis("A")), structure = listOf(
                SectionInstance(0, "A", "A1"), SectionInstance(1, "A", "A2"), SectionInstance(2, "A", "A3")
            ),
            allowedInstruments = listOf("piano", "bass"), soundContext = context,
            requestedIntents = listOf(
                InstrumentIntent(role = ArrangementRole.MELODY, profile = profile, mood = mood, attackTraits = setOf(SoundTrait.SOFT)),
                InstrumentIntent(role = ArrangementRole.BASS, profile = profile, mood = mood, toneTraits = setOf(SoundTrait.WARM))
            )
        )

        val plan = DeterministicGlobalSongPlanner().plan(input)

        assertEquals(SongPlan.CURRENT_VERSION, plan.version)
        assertEquals(input.contextHash(), plan.contextHash)
        assertTrue(plan.style.isBlank())
        assertEquals(input.sectionsWithIdentity().map { it.occurrenceHash }, plan.sections.map { it.occurrenceHash })
        assertEquals(setOf(ArrangementRole.MELODY, ArrangementRole.BASS), plan.sections.flatMap { it.soundIntents }.map { it.role }.toSet())
        assertTrue(plan.sections.flatMap { it.soundIntents }.all { it.profile == profile && it.mood == mood })
    }

    private fun analysis(id: String) = MidiAnalysis(
        partId = id, ppq = 480, durationTicks = 1_920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 80.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1, beats = 4.0, noteCount = 4, noteDensity = 0.25, rhythmicDensity = 0.25, energy = 0.25
    )
}
