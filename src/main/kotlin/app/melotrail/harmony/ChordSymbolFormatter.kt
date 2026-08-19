package app.melotrail.harmony

import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling

/** Adapter for display/import symbols. Domain and persisted DTOs never store this derived string. */
object ChordSymbolFormatter {
    fun format(event: ChordEvent): String = event.root.toString() + event.quality.symbolSuffix

    fun parse(symbol: String): ParsedChordSymbol? {
        val root = PitchSpelling.entries
            .sortedByDescending { it.symbol.length }
            .firstOrNull { symbol.startsWith(it.symbol) }
            ?: return null
        val suffix = symbol.removePrefix(root.symbol)
        val quality = ChordQuality.entries.firstOrNull { it.symbolSuffix == suffix } ?: return null
        return ParsedChordSymbol(PitchClass.of(root), quality)
    }
}

data class ParsedChordSymbol(val root: PitchClass, val quality: ChordQuality)
