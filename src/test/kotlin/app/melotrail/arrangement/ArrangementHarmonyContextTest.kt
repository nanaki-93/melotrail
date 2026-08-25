package app.melotrail.arrangement

import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId as HarmonySectionTypeId
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.MusicalKey
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArrangementHarmonyContextTest {
    @Test
    fun `project tempo meter and key replace transcription timing before arrangement`() {
        val analysis = MidiAnalysis(
            partId = "A", ppq = 480, durationTicks = 7_680, durationSeconds = 8.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 3, 4)),
            bars = 4, beats = 16.0, noteCount = 8, noteDensity = 0.5, rhythmicDensity = 0.5, energy = 0.5,
            key = MidiKey("G", "major", 0.8)
        )
        val project = Project(
            name = "authority", renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(compositionSettings = CompositionSettings(
                key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.NATURAL_MINOR),
                tempo = Tempo(80.0), timeSignature = TimeSignature(4, 4)
            ))
        )

        val resolved = ArrangementHarmonyContext.apply(analysis, SectionTypeId.VERSE, project)

        assertEquals(listOf(MidiTempoChange(0, 80.0)), resolved.tempoMap)
        assertEquals(listOf(MidiTimeSignature(0, 4, 4)), resolved.timeSignatures)
        assertEquals(MidiKey("C", ScaleModeId.NATURAL_MINOR.value, 1.0), resolved.key)
        assertEquals(12.0, resolved.durationSeconds)
    }

    @Test
    fun `saved section harmony replaces inferred chords on the shared MIDI clock`() {
        val analysis = MidiAnalysis(
            partId = "A", ppq = 480, durationTicks = 7_680, durationSeconds = 8.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
            bars = 4, beats = 16.0, noteCount = 8, noteDensity = 0.5, rhythmicDensity = 0.5, energy = 0.5,
            chords = listOf(MidiChord(0, 7_680, "F#", 0.9))
        )
        val harmony = HarmonySettings(progressions = listOf(ChordProgression(
            HarmonySectionTypeId.VERSE,
            listOf(
                ChordEvent(ChordEventId("c1"), PitchClass.of(PitchSpelling.C), ChordQuality.MAJOR_7, 0),
                ChordEvent(ChordEventId("c2"), PitchClass.of(PitchSpelling.G), ChordQuality.MAJOR, 1,
                    bass = PitchClass.of(PitchSpelling.B))
            )
        )))

        val resolved = ArrangementHarmonyContext.apply(analysis, SectionTypeId.VERSE, harmony)

        assertEquals(listOf("Cmaj7", "G/B", "Cmaj7", "G/B"), resolved.chords.map(MidiChord::symbol))
        assertEquals(listOf(0L, 1_920L, 3_840L, 5_760L), resolved.chords.map(MidiChord::startTick))
        assertEquals(7_680L, resolved.chords.last().endTick)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), resolved.chords.map(MidiChord::confidence))
    }

    @Test
    fun `analysis harmony remains the fallback for an unmatched section`() {
        val analysis = MidiAnalysis(
            partId = "A", ppq = 480, durationTicks = 1_920, durationSeconds = 2.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
            bars = 1, beats = 4.0, noteCount = 4, noteDensity = 0.5, rhythmicDensity = 0.5, energy = 0.5,
            chords = listOf(MidiChord(0, 1_920, "Dm", 0.8))
        )

        assertEquals(analysis, ArrangementHarmonyContext.apply(analysis, SectionTypeId.CHORUS, HarmonySettings()))
    }
}
