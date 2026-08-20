package app.melotrail.harmony

import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HarmonyDomainTest {
    @TempDir lateinit var root: Path

    @Test
    fun `every quality and enharmonic root formats and parses exactly`() {
        val expectedIntervals = mapOf(
            ChordQuality.MAJOR to listOf(0, 4, 7),
            ChordQuality.MINOR to listOf(0, 3, 7),
            ChordQuality.DOMINANT_7 to listOf(0, 4, 7, 10),
            ChordQuality.MAJOR_7 to listOf(0, 4, 7, 11),
            ChordQuality.MINOR_7 to listOf(0, 3, 7, 10),
            ChordQuality.MAJOR_9 to listOf(0, 4, 7, 11, 14),
            ChordQuality.MINOR_9 to listOf(0, 3, 7, 10, 14),
            ChordQuality.ADD_9 to listOf(0, 4, 7, 14),
            ChordQuality.SUS_2 to listOf(0, 2, 7),
            ChordQuality.SUS_4 to listOf(0, 5, 7)
        )
        ChordQuality.entries.forEach { quality ->
            assertEquals(expectedIntervals.getValue(quality), quality.intervals)
            PitchSpelling.entries.forEach { spelling ->
                val event = event("id-${quality.name}-${spelling.name}", PitchClass.of(spelling), quality, 0)
                val symbol = ChordSymbolFormatter.format(event)
                val parsed = requireNotNull(ChordSymbolFormatter.parse(symbol))
                assertEquals(spelling, parsed.root.spelling)
                assertEquals(quality, parsed.quality)
            }
        }
        assertEquals(null, ChordSymbolFormatter.parse("Hmaj7"))
        assertEquals(null, ChordSymbolFormatter.parse("C13"))
    }

    @Test
    fun `catalog progressions contain only chord tones from their selected key`() {
        listOf(
            MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
            MusicalKey(PitchClass.of(PitchSpelling.A), ScaleModeId.NATURAL_MINOR)
        ).forEach { key ->
            HarmonyTemplateCatalog.options(key).forEach { template ->
                val progression = HarmonyTemplateCatalog.resolve(template.id, key, SectionTypeId.VERSE)
                progression.events.forEach { event ->
                    event.quality.intervals.forEach { interval ->
                        assertTrue(key.contains(PitchClass.canonical(event.root.chromatic + interval)))
                    }
                }
            }
        }
    }

    @Test
    fun `ordered operations preserve event identity while normalizing presentation order`() {
        val first = event("first", PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0)
        val second = event("second", PitchClass.of(PitchSpelling.G), ChordQuality.DOMINANT_7, 1)
        val progression = ChordProgression(SectionTypeId.VERSE, listOf(first, second))

        val moved = progression.move(first.id, 1)
        assertEquals(listOf(second.id, first.id), moved.events.map(ChordEvent::id))
        assertEquals(listOf(0, 1), moved.events.map(ChordEvent::order))

        val edited = moved.edit(first.copy(quality = ChordQuality.MAJOR_7, order = 99))
        assertEquals(first.id, edited.events.last().id)
        assertEquals(1, edited.events.last().order)
        assertEquals(ChordQuality.MAJOR_7, edited.events.last().quality)

        val removed = edited.remove(second.id)
        assertEquals(listOf(first.id), removed.events.map(ChordEvent::id))
        assertEquals(0, removed.events.single().order)
    }

    @Test
    fun `structured persistence round trips verse chorus bridge chromatic chords and future fields`() {
        val verse = ChordProgression(SectionTypeId.VERSE, listOf(
            event("verse-c", PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0),
            event("verse-am", PitchClass.of(PitchSpelling.A), ChordQuality.MINOR, 1)
        ))
        val chorus = ChordProgression(SectionTypeId.CHORUS, listOf(
            event("chorus-db", PitchClass.of(PitchSpelling.D_FLAT), ChordQuality.MAJOR_9, 0),
            event("chorus-g", PitchClass.of(PitchSpelling.G), ChordQuality.DOMINANT_7, 1)
        ))
        val bridge = ChordProgression(SectionTypeId.BRIDGE, listOf(
            event("bridge-future", PitchClass.of(PitchSpelling.F), ChordQuality.SUS_4, 0,
                durationMeasures = 2, bass = PitchClass.of(PitchSpelling.C), inversion = 1, extension = "future-v1")
        ))
        val harmony = HarmonySettings(progressions = listOf(verse, chorus, bridge))

        val json = HarmonyJson.encode(harmony)
        val restored = HarmonyJson.decode(json)
        assertEquals(harmony, restored)
        assertTrue(json.contains("\"quality\":\"major9\""))
        assertFalse(json.contains("symbol"))
        assertEquals("Dbmaj9", ChordSymbolFormatter.format(restored.progressions[1].events.first()))
        assertFailsWith<IllegalArgumentException> { restored.progressions[2].events.single().requireExecutable() }

        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "harmony",
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(
                compositionSettings = compositionSettings(),
                harmony = harmony
            )
        )
        ProjectStore.write(root, project)
        val restoredProject = ProjectStore.read(root)
        assertEquals(harmony, restoredProject.envelope.harmony)
        assertTrue(Files.readString(root.resolve(ProjectStore.FILE_NAME)).contains("\"sectionType\": \"bridge\""))
    }

    @Test
    fun `invalid identities duplicate events and missing key context are rejected without banning chromatic roots`() {
        assertFailsWith<IllegalArgumentException> { ChordEventId("not a valid id") }
        val chord = event("same", PitchClass.of(PitchSpelling.D_FLAT), ChordQuality.MINOR, 0)
        assertFailsWith<IllegalArgumentException> {
            ChordProgression(SectionTypeId.VERSE, listOf(chord, chord.copy(order = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            ChordProgression(SectionTypeId.VERSE, listOf(chord.copy(order = 1)))
        }

        val harmony = HarmonySettings(progressions = listOf(ChordProgression(SectionTypeId.VERSE, listOf(chord))))
        val withoutContext = Project(
            version = Project.CURRENT_VERSION,
            name = "missing context",
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(harmony = harmony)
        )
        assertTrue(withoutContext.validate(root).errors.any { "project key context" in it })

        val chromaticProject = withoutContext.copy(envelope = withoutContext.envelope.copy(compositionSettings = compositionSettings()))
        assertTrue(chromaticProject.validate(root).isValid)
    }

    private fun compositionSettings() = CompositionSettings(
        key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR),
        tempo = Tempo(120.0),
        timeSignature = TimeSignature(4, 4)
    )

    private fun event(
        id: String,
        root: PitchClass,
        quality: ChordQuality,
        order: Int,
        durationMeasures: Int? = null,
        bass: PitchClass? = null,
        inversion: Int? = null,
        extension: String? = null
    ) = ChordEvent(ChordEventId(id), root, quality, order, durationMeasures, bass, inversion, extension)
}
