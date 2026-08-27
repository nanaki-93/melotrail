package app.melotrail.music.core

/**
 * The preferred written tonic remains part of project authority even though
 * pitch-class compatibility is advisory. It is never inferred from a source.
 */
enum class ProjectKeySpelling(val chromatic: Int, val symbol: String) {
    C(0, "C"),
    C_SHARP(1, "C#"),
    D_FLAT(1, "Db"),
    D(2, "D"),
    D_SHARP(3, "D#"),
    E_FLAT(3, "Eb"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#"),
    G_FLAT(6, "Gb"),
    G(7, "G"),
    G_SHARP(8, "G#"),
    A_FLAT(8, "Ab"),
    A(9, "A"),
    A_SHARP(10, "A#"),
    B_FLAT(10, "Bb"),
    B(11, "B"),
    B_SHARP(0, "B#"),
    C_FLAT(11, "Cb"),
    E_SHARP(5, "E#"),
    F_FLAT(4, "Fb");

    companion object {
        fun canonical(chromatic: Int): ProjectKeySpelling = entries.first { it.chromatic == chromatic }
    }
}

/** The fixed V1 scale modes used solely for advisory key compatibility. */
enum class ProjectScaleMode(val id: String, val displayName: String, val intervals: Set<Int>) {
    MAJOR("major", "major", setOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR("natural-minor", "natural minor", setOf(0, 2, 3, 5, 7, 8, 10));

    companion object {
        fun fromId(id: String): ProjectScaleMode? = entries.firstOrNull { it.id == id }
    }
}

/** Fixed Standard MIDI tempo, stored without lossy BPM floating-point conversion. */
@JvmInline
value class ProjectTempo(val microsecondsPerQuarter: Int) {
    init {
        require(microsecondsPerQuarter in 1..0xFF_FF_FF) {
            "Project tempo must fit one positive Standard MIDI tempo value"
        }
    }

    val beatsPerMinute: Double get() = 60_000_000.0 / microsecondsPerQuarter
}

/** Fixed Standard MIDI meter. The exponent is preserved exactly from the SMF policy. */
data class ProjectMeter(val numerator: Int, val denominatorExponent: Int) {
    init {
        require(numerator in 1..255 && denominatorExponent in 0..30) { "Project meter is invalid" }
    }

    val denominator: Long get() = 1L shl denominatorExponent
}
