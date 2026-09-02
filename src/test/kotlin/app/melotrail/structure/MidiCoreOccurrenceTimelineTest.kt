package app.melotrail.structure

import app.melotrail.midi.domain.MidiBeatPosition
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class MidiCoreOccurrenceTimelineTest {
    private val definitions = listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus"))
    private val ppq = MidiPpq(480)

    @Test
    fun `bar placements derive contiguous exact ticks and require the source total`() {
        val timeline = MidiCoreOccurrenceTimeline.buildFromBars(
            ppq,
            ProjectMeter(4, 2),
            definitions,
            listOf(
                MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 2),
                MidiCoreBarOccurrencePlacement("chorus-1", "chorus", "Chorus", 1),
            ),
            expectedSongEndTick = 5_760,
        )

        assertEquals(listOf(0L to 3_840L, 3_840L to 5_760L), timeline.occurrences.map { it.startTick to it.endTick })
        assertEquals(0L, timeline.pickupTicks)
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.buildFromBars(
                ppq,
                ProjectMeter(4, 2),
                definitions,
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 2)),
                expectedSongEndTick = 5_760,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.buildFromBars(
                ppq,
                ProjectMeter(4, 2),
                definitions,
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1)),
                expectedSongEndTick = 1_919,
            )
        }
    }

    @Test
    fun `timeline keeps repeated sections contiguous with exact tick and beat positions`() {
        val timeline = MidiCoreOccurrenceTimeline.build(
            ppq,
            ProjectMeter(4, 2),
            definitions,
            listOf(placement("verse-1", "verse", 1_920), placement("chorus-1", "chorus", 960), placement("verse-2", "verse", 1_920)),
            pickupTicks = 240,
        )

        assertEquals(listOf(0L to 1_920L, 1_920L to 2_880L, 2_880L to 4_800L), timeline.occurrences.map { it.startTick to it.endTick })
        assertEquals(MidiBeatPosition.of(4, 1), timeline.startPosition("chorus-1"))
        assertEquals(MidiBeatPosition.of(2, 1), timeline.durationPosition("chorus-1"))
        assertEquals(listOf("1:verse-1", "2:chorus-1", "3:verse-2"), timeline.markerLabels())
        assertEquals(listOf(0L, 1_920L, 2_880L), timeline.markers().map { it.tick })
    }

    @Test
    fun `editor inserts duplicates moves and removes without duration inference`() {
        val editor = MidiCoreStructureEditor(ppq)
        val base = editor.replace(emptyAuthority().copy(pickupTicks = 240), definitions, listOf(placement("verse-1", "verse", 1_920)), pickupTicks = 240)
        val inserted = editor.insert(base, 1, placement("chorus-1", "chorus", 960))
        val duplicated = editor.duplicate(inserted, "verse-1", "verse-2", "Verse repeat")
        val moved = editor.move(duplicated, "chorus-1", 0)
        val removed = editor.remove(moved, "verse-1")

        assertEquals(listOf("chorus-1", "verse-2"), removed.occurrences.map { it.id })
        assertEquals(listOf(0L to 960L, 960L to 2_880L), removed.occurrences.map { it.startTick to it.endTick })
        assertEquals(240L, removed.pickupTicks)
    }

    @Test
    fun `timeline rejects gaps overlaps unknown definitions and an oversized pickup`() {
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(ppq, ProjectMeter(4, 2), definitions, listOf(placement("missing-1", "missing", 480)))
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(ppq, ProjectMeter(4, 2), definitions, listOf(placement("verse-1", "verse", 480)), pickupTicks = 1_921)
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(ppq, ProjectMeter(4, 2), definitions, listOf(placement("verse-1", "verse", 480, start = 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(ppq, ProjectMeter(4, 2), definitions, listOf(placement("verse-1", "verse", 480)), expectedSongEndTick = 479)
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(MidiPpq(100), ProjectMeter(4, 5), definitions, listOf(placement("verse-1", "verse", 100)))
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreOccurrenceTimeline.build(ppq, ProjectMeter(4, 2), definitions, emptyList(), pickupTicks = 1)
        }
        assertEquals(listOf("1:Verse one"), MidiCoreOccurrenceTimeline.build(
            ppq,
            ProjectMeter(4, 2),
            definitions,
            listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse   one", 480)),
        ).markerLabels())
    }

    private fun emptyAuthority() = ProjectAuthority(
        ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR), ProjectTempo(500_000), ProjectMeter(4, 2), emptyList(), emptyList(), emptyList(),
    )

    private fun placement(id: String, definitionId: String, duration: Long, start: Long? = null) = MidiCoreOccurrencePlacement(id, definitionId, id, duration, start)

}
