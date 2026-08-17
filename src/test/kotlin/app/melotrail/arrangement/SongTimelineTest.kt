package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToLong

class SongTimelineTest {
    @TempDir lateinit var root: Path

    @Test
    fun `mixed PPQ repeated parts transition meter changes and boundary semantics share one clock`() {
        val timeline = SongTimeline.create(SongTimelineFixtures.write(root))

        assertEquals(960, timeline.canonicalPpq)
        assertTrue(timeline.ppqConversions.all { it.exact })
        assertEquals(listOf(0L to 7_680L, 7_680L to 15_360L, 18_240L to 24_000L), timeline.occurrences.map { it.startTick to it.endTick })
        assertEquals(15_360L to 18_240L, timeline.transitions.single().let { it.startTick to it.endTick })
        assertEquals("B-1", timeline.transitions.single().tempoOwnerOccurrenceId)
        assertEquals(listOf(0L, 3_840L, 7_680L, 11_520L, 15_360L, 18_240L, 21_120L), timeline.tempoMap.map { it.tick })
        assertEquals(listOf(0L, 7_680L, 15_360L, 18_240L), timeline.timeSignatureMap.map { it.tick })
        assertEquals(24_000L, timeline.totalTicks)
        assertEquals(14.5696969697, timeline.totalSeconds, 0.0000001)
        assertEquals((timeline.totalSeconds * 44_100).roundToLong(), timeline.frames(44_100))

        assertEquals(7_200L, timeline.localToSongTick("A-1", 3_600, noteOn = true))
        assertTrue(timeline.acceptsNoteOff("A-1", 7_680L))
        assertFalse(timeline.acceptsNoteOn("A-1", 7_680L))
        assertThrows(IllegalArgumentException::class.java) { timeline.localToSongTick("A-1", 3_840, noteOn = true) }
        assertEquals(15_360L, timeline.localToSongTick("A-2", 3_840))
        assertTrue(Files.isRegularFile(root.resolve("midi/generated/transitions.mid")))
        listOf("piano", "bass", "drums", "pad", "strings").forEach { assertTrue(Files.isRegularFile(root.resolve("midi/generated/$it.mid"))) }
    }

    @Test
    fun `rounded PPQ conversion is half up bounded and overflow safe`() {
        val rounded = PpqNormalization.plan(listOf(PpqNormalizationInput("high", 19_200, evidenceTicks = listOf(0, 1, 2, 19_200))))
        val conversion = rounded.conversion("high")
        assertEquals(9_600, rounded.canonicalPpq)
        assertFalse(conversion.exact)
        assertEquals(1L, conversion.toCanonical(1)) // 0.5 rounds upward.
        assertEquals(2L, conversion.toCanonical(3))
        assertEquals(2L, conversion.toSource(1))
        assertTrue(conversion.maximumTickError <= 0.5)
        assertThrows(IllegalArgumentException::class.java) {
            SongTimeline.create(SongTimelineInput(listOf(occurrence("rounded", 19_200, 20.0, 3, listOf(1)))))
        }
        val expanding = PpqNormalization.plan(listOf(PpqNormalizationInput("low", 1), PpqNormalizationInput("high-again", 19_200))).conversion("low")
        assertThrows(IllegalArgumentException::class.java) { expanding.toCanonical(Long.MAX_VALUE) }
    }

    @Test
    fun `exact PPQ conversion round trips and rejects duration overflow`() {
        val exact = PpqNormalization.plan(listOf(PpqNormalizationInput("a", 480), PpqNormalizationInput("b", 960)))
        assertEquals(960, exact.canonicalPpq)
        assertEquals(480L, exact.conversion("a").toCanonical(240))
        assertEquals(240L, exact.conversion("a").toSource(480))
        assertThrows(IllegalArgumentException::class.java) {
            val overflowing = occurrence("overflow", 1, 120.0, Long.MAX_VALUE).copy(timeSignatures = listOf(MidiTimeSignature(0, 4, 8)))
            SongTimeline.create(SongTimelineInput(listOf(overflowing)))
        }
    }

    @Test
    fun `synchronization report is deterministic and contains timing evidence`() {
        val timeline = SongTimeline.create(SongTimelineFixtures.write(root))
        val first = timeline.synchronizationReport(48_000)
        val second = timeline.synchronizationReport(48_000)

        assertEquals(first, second)
        assertEquals(SongSynchronizationReporter.serialize(first), SongSynchronizationReporter.serialize(second))
        assertEquals(3, first.inputs.size)
        assertEquals(1, first.transitions.size)
        assertEquals(7, first.tempoMap.size)
        assertEquals(timeline.frames(48_000), first.totalFrames)
        assertTrue(first.inputs.all { it.inputFingerprint.length == 64 })
    }

    @Test
    fun `silence remains an explicit occurrence range`() {
        val silent = occurrence("silent", 480, 120.0, 1_920)
        val sounding = occurrence("sounding", 480, 120.0, 1_920)
        val timeline = SongTimeline.create(SongTimelineInput(listOf(silent, sounding)))

        assertEquals(0L to 1_920L, timeline.occurrence("silent").let { it.startTick to it.endTick })
        assertEquals(1_920L, timeline.occurrence("sounding").startTick)
        assertEquals(4.0, timeline.totalSeconds, 0.000001)
    }

    private fun occurrence(id: String, ppq: Int, bpm: Double, duration: Long, evidence: List<Long> = emptyList()) = SongTimelineOccurrenceInput(
        occurrenceId = id, partId = "P$id", inputId = "$id-input", inputFingerprint = "a".repeat(64), ppq = ppq, durationTicks = duration,
        tempoMap = listOf(MidiTempoChange(0, bpm)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), alignmentEvidenceTicks = evidence
    )
}
