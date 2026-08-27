package app.melotrail.music.core

/** Supported deterministic chord realizations for the MIDI Core arranger. */
enum class MidiCoreChordQuality(val suffix: String, val intervals: List<Int>) {
    MAJOR("", listOf(0, 4, 7)),
    MINOR("m", listOf(0, 3, 7)),
    MAJOR_6("6", listOf(0, 4, 7, 9)),
    DOMINANT_7("7", listOf(0, 4, 7, 10)),
    MAJOR_7("maj7", listOf(0, 4, 7, 11)),
    MINOR_7("m7", listOf(0, 3, 7, 10)),
    DOMINANT_9("9", listOf(0, 4, 7, 10, 14)),
    MAJOR_9("maj9", listOf(0, 4, 7, 11, 14)),
    MINOR_9("m9", listOf(0, 3, 7, 10, 14)),
    ADD_9("add9", listOf(0, 4, 7, 14)),
    SUS_2("sus2", listOf(0, 2, 7)),
    SUS_4("sus4", listOf(0, 5, 7)),
}

/** A parsed, spelling-preserving chord symbol with deterministic pitch-class realization. */
data class MidiCoreChordSymbol(
    val root: ProjectKeySpelling,
    val quality: MidiCoreChordQuality,
    val bass: ProjectKeySpelling? = null,
) {
    val rootPitchClass: Int get() = root.chromatic
    val bassPitchClass: Int get() = bass?.chromatic ?: root.chromatic
    val pitchClasses: Set<Int> get() = buildSet {
        quality.intervals.forEach { interval -> add(Math.floorMod(root.chromatic + interval, 12)) }
        add(bassPitchClass)
    }
    val canonicalSymbol: String get() = root.symbol + quality.suffix + (bass?.let { "/${it.symbol}" } ?: "")

    /** Returns true when a pitch class is one of the authoritative chord tones. */
    fun containsPitchClass(pitchClass: Int): Boolean = Math.floorMod(pitchClass, 12) in pitchClasses

    companion object {
        /** Parses the bounded V1 symbol vocabulary without changing the supplied authority. */
        fun parse(symbol: String): MidiCoreChordSymbol? {
            val value = symbol.trim()
            if (value.isEmpty() || value.any(Char::isWhitespace)) return null
            val pieces = value.split('/')
            if (pieces.size > 2 || pieces.any(String::isEmpty)) return null
            val chord = pieces[0]
            val root = ProjectKeySpelling.entries
                .sortedByDescending { it.symbol.length }
                .firstOrNull { chord.startsWith(it.symbol, ignoreCase = true) }
                ?: return null
            val quality = qualityFor(chord.drop(root.symbol.length)) ?: return null
            val bass = pieces.getOrNull(1)?.let { bassText ->
                ProjectKeySpelling.entries.firstOrNull { it.symbol.equals(bassText, ignoreCase = true) }
                    ?: return null
            }
            return MidiCoreChordSymbol(root, quality, bass)
        }

        private fun qualityFor(suffix: String): MidiCoreChordQuality? {
            if (suffix == "M") return MidiCoreChordQuality.MAJOR
            return when (suffix.lowercase()) {
            "", "maj" -> MidiCoreChordQuality.MAJOR
            "m", "min", "minor" -> MidiCoreChordQuality.MINOR
            "6", "maj6" -> MidiCoreChordQuality.MAJOR_6
            "7", "dom7", "dominant7" -> MidiCoreChordQuality.DOMINANT_7
            "maj7" -> MidiCoreChordQuality.MAJOR_7
            "min7", "minor7" -> MidiCoreChordQuality.MINOR_7
            "m7" -> MidiCoreChordQuality.MINOR_7
            "9" -> MidiCoreChordQuality.DOMINANT_9
            "maj9" -> MidiCoreChordQuality.MAJOR_9
            "m9", "min9", "minor9" -> MidiCoreChordQuality.MINOR_9
            "add9" -> MidiCoreChordQuality.ADD_9
            "sus2" -> MidiCoreChordQuality.SUS_2
            "sus", "sus4" -> MidiCoreChordQuality.SUS_4
            else -> null
        }
        }
    }
}
