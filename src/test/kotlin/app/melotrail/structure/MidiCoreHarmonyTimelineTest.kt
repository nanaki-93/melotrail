package app.melotrail.structure

import app.melotrail.music.core.MidiCoreChordQuality
import app.melotrail.music.core.MidiCoreChordSymbol
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreHarmonyTimelineTest {
    @Test
    fun `parses slash extensions and keeps chromatic chord tones authoritative`() {
        val chord = assertNotNull(MidiCoreChordSymbol.parse("Dbmaj9/F"))

        assertEquals(ProjectKeySpelling.D_FLAT, chord.root)
        assertEquals(MidiCoreChordQuality.MAJOR_9, chord.quality)
        assertEquals(ProjectKeySpelling.F, chord.bass)
        assertEquals(setOf(0, 1, 3, 5, 8), chord.pitchClasses)
        assertTrue(chord.containsPitchClass(1))
        assertFalse(chord.containsPitchClass(2))
        assertNull(MidiCoreChordSymbol.parse("Cunknown"))
        assertNull(MidiCoreChordSymbol.parse("C/"))
        assertNull(MidiCoreChordSymbol.parse("C/G/ B"))
    }

    @Test
    fun `valid sub-bar and repeated occurrence windows resolve at exact boundaries`() {
        val authority = authority(
            occurrences = listOf(
                occurrence("verse-1", "verse", 0, 1_920),
                occurrence("verse-2", "verse", 1_920, 3_840),
            ),
            chords = listOf(
                chord("c-1", "verse-1", "C", 0, 960),
                chord("db-1", "verse-1", "Db", 960, 1_920),
                chord("g-1", "verse-2", "G7", 1_920, 2_880),
                chord("c-2", "verse-2", "C", 2_880, 3_840),
            ),
        )

        val validation = MidiCoreHarmonyValidator.validate(authority)
        val timeline = MidiCoreHarmonyTimeline.build(authority)

        assertTrue(validation.valid)
        assertTrue(validation.findings.any { it.code == MidiCoreHarmonyFindingCode.CHROMATIC_CHORD })
        assertEquals(listOf("c-1", "db-1"), timeline.forOccurrence("verse-1").map { it.event.id })
        assertEquals("db-1", timeline.atTick(960).event.id)
        assertEquals("g-1", timeline.atTick(1_920).event.id)
        assertEquals(960L, timeline.forOccurrence("verse-1").first().durationTicks)
    }

    @Test
    fun `reports missing gaps overlaps outside windows invalid symbols and order`() {
        val authority = authority(
            occurrences = listOf(occurrence("verse-1", "verse", 0, 1_920)),
            chords = emptyList(),
        )

        val validation = MidiCoreHarmonyValidator.validate(
            authority,
            listOf(
                chord("z-2", "verse-1", "C", 960, 1_920),
                chord("z-1", "verse-1", "not-a-chord", 0, 480),
                chord("z-3", "verse-1", "G", 1_440, 1_920),
                AuthoritativeChordEvent("z-4", "verse-1", "G", 1_440, 2_000),
            ),
        )
        val codes = validation.findings.map(MidiCoreHarmonyFinding::code).toSet()

        assertFalse(validation.valid)
        assertTrue(MidiCoreHarmonyFindingCode.INVALID_CHORD_SYMBOL in codes)
        assertTrue(MidiCoreHarmonyFindingCode.CHORD_OUTSIDE_OCCURRENCE in codes)
        assertTrue(MidiCoreHarmonyFindingCode.CHORD_WINDOW_GAP in codes)
        assertTrue(MidiCoreHarmonyFindingCode.CHORD_WINDOW_OVERLAP in codes)
        assertTrue(MidiCoreHarmonyFindingCode.CHORD_EVENT_ORDER in codes)
    }

    @Test
    fun `changing project key never substitutes or transposes an authoritative symbol`() {
        val authority = authority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            occurrences = listOf(occurrence("verse-1", "verse", 0, 480)),
            chords = listOf(chord("c-1", "verse-1", "Db", 0, 480)),
        )
        val changedKey = authority.copy(key = ProjectKey(ProjectKeySpelling.G, ProjectScaleMode.MAJOR))

        assertEquals("Db", changedKey.chordEvents.single().symbol)
        assertTrue(MidiCoreHarmonyValidator.validate(changedKey).findings.any { it.code == MidiCoreHarmonyFindingCode.CHROMATIC_CHORD })
    }

    private fun authority(
        key: ProjectKey = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
        occurrences: List<ProjectSectionOccurrence>,
        chords: List<AuthoritativeChordEvent>,
    ) = ProjectAuthority(
        key,
        ProjectTempo(500_000),
        ProjectMeter(4, 2),
        listOf(ProjectSectionDefinition("verse", "Verse")),
        occurrences,
        chords,
    )

    private fun occurrence(id: String, definitionId: String, start: Long, end: Long) =
        ProjectSectionOccurrence(id, definitionId, id, start, end)

    private fun chord(id: String, occurrenceId: String, symbol: String, start: Long, end: Long) =
        AuthoritativeChordEvent(id, occurrenceId, symbol, start, end)
}
