package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature

/**
 * MIDI analysis is evidence, not composition authority. Its legacy wire
 * labels are parsed once at this adapter boundary before a processor may use
 * the inferred values.
 */
fun MidiKey.toMusicalKeyOrNull(): MusicalKey? {
    val spelling = PitchSpelling.fromSymbol(tonic) ?: return null
    val modeId = when (mode) {
        "major" -> ScaleModeId.MAJOR
        "minor" -> ScaleModeId.NATURAL_MINOR
        else -> return null
    }
    return MusicalKey(PitchClass.of(spelling), modeId)
}

fun MidiTempoChange.toTempoOrNull(): Tempo? = runCatching { Tempo(bpm) }.getOrNull()
fun MidiTimeSignature.toTimeSignatureOrNull(): TimeSignature? = runCatching { TimeSignature(numerator, denominator) }.getOrNull()
