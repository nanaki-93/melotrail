package app.melotrail.desktop

import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MidiCoreAuthorityDraftingTest {
    @Test
    fun `simple named section rows derive one exact whole-bar timeline`() {
        val drafts = listOf(
            MidiCoreSectionDraft("hidden-verse", "hidden-part-verse", "Verse", "Verse", "2"),
            MidiCoreSectionDraft("hidden-chorus", "hidden-part-chorus", "Chorus", "Chorus", "2"),
        )

        val parsed = MidiCoreAuthorityDrafting.parseStructure(drafts, 480, ProjectMeter(4, 2), 7_680)

        assertEquals(listOf("Verse", "Chorus"), parsed.occurrences.map(ProjectSectionOccurrence::label))
        assertEquals(listOf(0L to 3_840L, 3_840L to 7_680L), parsed.occurrences.map { it.startTick to it.endTick })
        assertFailsWith<IllegalArgumentException> {
            MidiCoreAuthorityDrafting.parseStructure(drafts, 480, ProjectMeter(4, 2), 9_600)
        }
    }

    @Test
    fun `explicit 3 plus 1 plus 2 plus 2 beat rows derive deterministic gap-free windows`() {
        val authority = authority()
        val drafts = listOf(
            MidiCoreProgressionDraft("verse-1", "Verse", listOf(
                MidiCoreChordRowDraft("verse-1-chord-1", "C", "3"),
                MidiCoreChordRowDraft("verse-1-chord-2", "Am", "1"),
                MidiCoreChordRowDraft("verse-1-chord-3", "Dm", "2"),
                MidiCoreChordRowDraft("verse-1-chord-4", "G", "2"),
            )),
            MidiCoreProgressionDraft("chorus-1", "Chorus", listOf(MidiCoreChordRowDraft("chorus-1-chord-1", "C", "8"))),
        )

        val events = MidiCoreAuthorityDrafting.parseHarmony(drafts, authority, 480)

        assertEquals(listOf("C", "Am", "Dm", "G", "C"), events.map(AuthoritativeChordEvent::symbol))
        assertEquals(
            listOf(0L to 1_440L, 1_440L to 1_920L, 1_920L to 2_880L, 2_880L to 3_840L),
            events.filter { it.occurrenceId == "verse-1" }.map { it.startTick to it.endTick },
        )
        events.groupBy(AuthoritativeChordEvent::occurrenceId).forEach { (occurrenceId, windows) ->
            val occurrence = authority.occurrences.single { it.id == occurrenceId }
            assertEquals(occurrence.startTick, windows.first().startTick)
            assertEquals(occurrence.endTick, windows.last().endTick)
            assertEquals(true, windows.zipWithNext().all { (left, right) -> left.endTick == right.startTick })
        }
    }

    @Test
    fun `unchanged explicit rows preserve exact existing chord durations`() {
        val authority = authority().copy(
            chordEvents = listOf(
                AuthoritativeChordEvent("original-a", "verse-1", "C", 0, 480),
                AuthoritativeChordEvent("original-b", "verse-1", "G", 480, 3_840),
                AuthoritativeChordEvent("original-c", "chorus-1", "F", 3_840, 7_680),
            ),
        )
        val drafts = MidiCoreAuthorityDrafting.progressionDrafts(authority, 480)

        assertEquals(authority.chordEvents, MidiCoreAuthorityDrafting.parseHarmony(drafts, authority, 480))
    }

    @Test
    fun `odd PPQ rejects unrepresentable durations and rejects gaps overlaps and overflow`() {
        val short = authority().copy(
            sectionDefinitions = listOf(ProjectSectionDefinition("short", "Short")),
            occurrences = listOf(ProjectSectionOccurrence("short-1", "short", "Short", 0, 479)),
            chordEvents = emptyList(),
            arrangementEndTick = 479,
        )
        assertFailsWith<IllegalArgumentException> {
            MidiCoreAuthorityDrafting.parseHarmony(listOf(MidiCoreProgressionDraft("short-1", "Short", listOf(MidiCoreChordRowDraft("a", "C", "1/2")))), short, 479)
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreAuthorityDrafting.parseHarmony(listOf(MidiCoreProgressionDraft("short-1", "Short", listOf(MidiCoreChordRowDraft("a", "C", "2")))), short, 479)
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreAuthorityDrafting.parseHarmony(listOf(MidiCoreProgressionDraft("short-1", "Short", listOf(MidiCoreChordRowDraft("a", "C", Long.MAX_VALUE.toString())))), short, 479)
        }
    }

    private fun authority() = ProjectAuthority(
        key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
        tempo = ProjectTempo(500_000),
        meter = ProjectMeter(4, 2),
        sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
        occurrences = listOf(
            ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 3_840),
            ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 3_840, 7_680),
        ),
        chordEvents = emptyList(),
    )
}
