package app.melotrail.arrangement

import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MusicalPatternLibraryTest {
    private val context = CanonicalPatternContext(
        MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), Tempo(90.0), TimeSignature(4, 4),
        ChordProgression(SectionTypeId.VERSE, listOf(
            ChordEvent(ChordEventId("c"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0),
            ChordEvent(ChordEventId("g"), PitchClass.of(PitchSpelling.G), ChordQuality.MAJOR, 1)
        )), 480
    )

    @Test fun `pattern ids are typed stable values rather than MIDI filenames`() {
        assertEquals("bass.diatonic-approach", BassPatternId.DIATONIC_APPROACH.id.value)
        assertEquals("pad.common-tone", PadVoicingPatternId.COMMON_TONE.id.value)
        assertEquals("drums.lazy-swing", DrumGroovePatternId.LAZY_SWING.id.value)
        assertEquals("drums.fill.bridge-half-time-break", DrumFillPatternId.BRIDGE_HALF_TIME_BREAK.id.value)
        assertEquals("chords.rhythm.broken-syncopation", ChordRhythmPatternId.BROKEN_SYNCOPATION.id.value)
        assertEquals(listOf(4, 8, 12), MusicalPatternLibrary.chordRhythm(ChordRhythmPatternId.LATE_ENTRY).steps.map { it.sixteenth })
        assertEquals("transition.drop-build", TransitionPatternId.DROP_BUILD.id.value)
        assertTrue(MusicalPatternLibrary.drumGrooves.all { !it.displayName.contains(".mid") })
        assertEquals(DrumFillPatternId.entries.toSet(), MusicalPatternLibrary.drumFills.map { it.id }.toSet())
        assertEquals(ChordRhythmPatternId.entries.toSet(), MusicalPatternLibrary.chordRhythms.map { it.id }.toSet())
    }

    @Test fun `bass patterns use canonical chord roots and resolve their approaches`() {
        assertEquals(listOf(36, 43), MusicalPatternLibrary.bass(context, BassPatternParameters(BassPatternId.SUSTAINED_ROOT)).map { it.pitch })
        assertEquals(listOf(36, 43, 36, 43), MusicalPatternLibrary.bass(context.copy(progression = oneChord()), BassPatternParameters(BassPatternId.ROOT_FIFTH)).map { it.pitch })
        assertEquals(listOf(36, 48, 36, 48), MusicalPatternLibrary.bass(context.copy(progression = oneChord()), BassPatternParameters(BassPatternId.OCTAVE)).map { it.pitch })
        val approached = MusicalPatternLibrary.bass(context, BassPatternParameters(BassPatternId.DIATONIC_APPROACH, seed = 4))
        assertTrue(approached.any { it.startTick == 1440L && it.pitch % 12 in setOf(5, 9) })
        assertEquals(43, approached.first { it.startTick == 1920L }.pitch)
        assertEquals(MusicalPatternLibrary.bass(context, BassPatternParameters(BassPatternId.WALK_TO_NEXT_ROOT, 8)), MusicalPatternLibrary.bass(context, BassPatternParameters(BassPatternId.WALK_TO_NEXT_ROOT, 8)))
    }

    @Test fun `canonical bass patterns honor an executable slash bass`() {
        val slash = oneChord().copy(events = listOf(
            oneChord().events.single().copy(
                root = PitchClass.of(PitchSpelling.G),
                bass = PitchClass.of(PitchSpelling.B)
            )
        ))

        assertEquals(listOf(47), MusicalPatternLibrary.bass(
            context.copy(progression = slash), BassPatternParameters(BassPatternId.SUSTAINED_ROOT)
        ).map { it.pitch })
    }

    @Test fun `pad strategies remain harmony-bound and common-tone minimizes movement`() {
        val close = MusicalPatternLibrary.pad(context, PadPatternParameters(PadVoicingPatternId.CLOSE))
        val open = MusicalPatternLibrary.pad(context, PadPatternParameters(PadVoicingPatternId.OPEN))
        val common = MusicalPatternLibrary.pad(context, PadPatternParameters(PadVoicingPatternId.COMMON_TONE))
        val minimal = MusicalPatternLibrary.pad(context, PadPatternParameters(PadVoicingPatternId.MINIMAL))
        assertEquals(2, minimal.groupBy { it.startTick }.values.first().size)
        assertTrue(close.all { it.pitch % 12 in setOf(0, 2, 4, 7, 11) })
        assertTrue(open.groupBy { it.startTick }.values.first().maxOf { it.pitch } - open.groupBy { it.startTick }.values.first().minOf { it.pitch } > 7)
        assertTrue(common.isNotEmpty())
    }

    @Test fun `curated grooves and transitions are reproducible and tempo-aware`() {
        val parameters = DrumPatternParameters(DrumGroovePatternId.LAZY_SWING, seed = 22)
        val first = MusicalPatternLibrary.drums(context, parameters)
        assertEquals(first, MusicalPatternLibrary.drums(context, parameters))
        assertFalse(first == MusicalPatternLibrary.drums(context.copy(tempo = Tempo(160.0)), parameters))
        assertTrue(MusicalPatternLibrary.transition(context, TransitionPatternParameters(TransitionPatternId.DRUM_FILL)).drums.isNotEmpty())
        assertTrue(MusicalPatternLibrary.transition(context, TransitionPatternParameters(TransitionPatternId.BASS_APPROACH, seed = 2)).bass.isNotEmpty())
        assertTrue(MusicalPatternLibrary.transition(context, TransitionPatternParameters(TransitionPatternId.PAD_SUSTAIN)).pad.isNotEmpty())
        assertEquals(DropBuildInstruction(1, 1), MusicalPatternLibrary.transition(context, TransitionPatternParameters(TransitionPatternId.DROP_BUILD)).dropBuild)
    }

    private fun oneChord() = ChordProgression(SectionTypeId.VERSE, listOf(ChordEvent(ChordEventId("c"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR, 0)))
}
