package app.melotrail.harmony

import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling

/** Adapter for display/import symbols. Domain and persisted DTOs never store this derived string. */
object ChordSymbolFormatter {
    fun format(event: ChordEvent): String = buildString {
        append(event.root)
        append(event.quality.symbolSuffix)
        event.bass?.let { append('/').append(it) }
    }

    fun parse(symbol: String): ParsedChordSymbol? {
        val value = symbol.trim()
        val chordAndBass = value.split('/', limit = 2)
        val chord = chordAndBass.first()
        val root = PitchSpelling.entries
            .sortedByDescending { it.symbol.length }
            .firstOrNull { chord.startsWith(it.symbol, ignoreCase = true) }
            ?: return null
        val suffix = chord.drop(root.symbol.length)
        val quality = ChordQuality.entries.firstOrNull { it.symbolSuffix.equals(suffix, ignoreCase = true) }
            ?: when (suffix.lowercase()) {
                "min" -> ChordQuality.MINOR
                "min7" -> ChordQuality.MINOR_7
                "min9" -> ChordQuality.MINOR_9
                "sus" -> ChordQuality.SUS_4
                else -> return null
            }
        val bass = chordAndBass.getOrNull(1)?.let { bassSymbol ->
            PitchSpelling.entries.singleOrNull { it.symbol.equals(bassSymbol, ignoreCase = true) }
                ?.let(PitchClass::of) ?: return null
        }
        return ParsedChordSymbol(PitchClass.of(root), quality, bass)
    }
}

data class ParsedChordSymbol(val root: PitchClass, val quality: ChordQuality, val bass: PitchClass? = null)
