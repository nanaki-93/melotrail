package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.math.roundToLong

class SongTimingReproductionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `observed independent rounded section starts drift from authoritative lane clock`() {
        val timeline = SongTimeline.create(SongTimelineFixtures.write(root))
        val bStart = timeline.occurrence("B-1").startTick
        val anchors = mapOf(
            "piano" to listOf(0L, 7_680L, bStart),
            "bass" to listOf(0L, 7_680L, bStart),
            "drums" to listOf(0L, 7_680L, bStart),
            "pad" to listOf(0L, 7_680L, bStart),
            "strings" to listOf(0L, 7_680L, bStart),
            "transitions" to listOf(15_360L)
        )
        val absoluteFrames = anchors.mapValues { (_, ticks) -> ticks.map { timeline.framesAt(it, 8_000) } }
        val independentlyRoundedBStart = (timeline.secondsAt(7_680L) * 8_000).roundToLong() * 2L +
            (timeline.secondsAt(18_240L) - timeline.secondsAt(15_360L)).times(8_000).roundToLong()

        println("Task 073 arranged-MIDI drift fixture: canonicalPpq=${timeline.canonicalPpq}, piano/bass/drums/pad/strings B-start=${absoluteFrames.filterKeys { it != "transitions" }.values.map { it.last() }.distinct()}, transition-start=${absoluteFrames.getValue("transitions")}, authoritativeBStart=${timeline.framesAt(bStart, 8_000)}, independentlyRoundedBStart=$independentlyRoundedBStart")
        assertEquals(1, absoluteFrames.filterKeys { it != "transitions" }.values.map { it.last() }.distinct().size)
        assertTrue(absoluteFrames.getValue("transitions").single() < absoluteFrames.getValue("piano").last())
        assertNotEquals(timeline.framesAt(bStart, 8_000), independentlyRoundedBStart)
    }

}
