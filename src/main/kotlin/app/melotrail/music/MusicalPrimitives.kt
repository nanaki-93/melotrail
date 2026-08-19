package app.melotrail.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A preferred written name for one of the twelve chromatic pitch classes.
 * The spelling is data, while [PitchClass.chromatic] is its musical identity.
 */
@Serializable
enum class PitchSpelling(val chromatic: Int, val symbol: String) {
    @SerialName("C") C(0, "C"),
    @SerialName("C#") C_SHARP(1, "C#"),
    @SerialName("Db") D_FLAT(1, "Db"),
    @SerialName("D") D(2, "D"),
    @SerialName("D#") D_SHARP(3, "D#"),
    @SerialName("Eb") E_FLAT(3, "Eb"),
    @SerialName("E") E(4, "E"),
    @SerialName("F") F(5, "F"),
    @SerialName("F#") F_SHARP(6, "F#"),
    @SerialName("Gb") G_FLAT(6, "Gb"),
    @SerialName("G") G(7, "G"),
    @SerialName("G#") G_SHARP(8, "G#"),
    @SerialName("Ab") A_FLAT(8, "Ab"),
    @SerialName("A") A(9, "A"),
    @SerialName("A#") A_SHARP(10, "A#"),
    @SerialName("Bb") B_FLAT(10, "Bb"),
    @SerialName("B") B(11, "B"),
    @SerialName("B#") B_SHARP(0, "B#"),
    @SerialName("Cb") C_FLAT(11, "Cb"),
    @SerialName("E#") E_SHARP(5, "E#"),
    @SerialName("Fb") F_FLAT(4, "Fb");

    companion object {
        /** Adapter-only parsing for evidence imported from MIDI or an API. */
        fun fromSymbol(symbol: String): PitchSpelling? = entries.firstOrNull { it.symbol == symbol }
    }
}

/** A chromatic identity with the preferred enharmonic spelling retained for display. */
@Serializable
class PitchClass(val chromatic: Int, val spelling: PitchSpelling) {
    init {
        require(chromatic in 0..11) { "Pitch class chromatic value must be from 0 to 11" }
        require(spelling.chromatic == chromatic) { "Pitch spelling ${spelling.symbol} does not match chromatic value $chromatic" }
    }

    fun isSameChromaticAs(other: PitchClass): Boolean = chromatic == other.chromatic

    /** Ascending chromatic distance in semitones, from 0 through 11. */
    fun ascendingIntervalTo(other: PitchClass): Int = Math.floorMod(other.chromatic - chromatic, 12)

    override fun equals(other: Any?): Boolean = other is PitchClass && isSameChromaticAs(other)
    override fun hashCode(): Int = chromatic
    override fun toString(): String = spelling.symbol

    companion object {
        fun of(spelling: PitchSpelling): PitchClass = PitchClass(spelling.chromatic, spelling)

        /** Deterministic default spelling for generated scale members. */
        fun canonical(chromatic: Int): PitchClass {
            val normalized = Math.floorMod(chromatic, 12)
            return of(CANONICAL_SPELLINGS[normalized])
        }

        private val CANONICAL_SPELLINGS = arrayOf(
            PitchSpelling.C, PitchSpelling.C_SHARP, PitchSpelling.D, PitchSpelling.D_SHARP,
            PitchSpelling.E, PitchSpelling.F, PitchSpelling.F_SHARP, PitchSpelling.G,
            PitchSpelling.G_SHARP, PitchSpelling.A, PitchSpelling.A_SHARP, PitchSpelling.B
        )
    }
}

/** A forward-compatible stable mode identifier. Unknown IDs are preserved but have no behavior. */
@Serializable
@JvmInline
value class ScaleModeId(val value: String) {
    init {
        require(ID.matches(value)) { "Scale mode ID is invalid: $value" }
    }

    val executable: ExecutableScaleMode?
        get() = ExecutableScaleMode.entries.firstOrNull { it.id == this }

    companion object {
        private val ID = Regex("[a-z][a-z0-9-]{0,63}")
        val MAJOR = ScaleModeId("major-v1")
        val NATURAL_MINOR = ScaleModeId("natural-minor-v1")
    }
}

/** The only scale algorithms currently executable in the local product. */
enum class ExecutableScaleMode(val id: ScaleModeId, val displayName: String, val intervals: List<Int>) {
    MAJOR(ScaleModeId.MAJOR, "major", listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR(ScaleModeId.NATURAL_MINOR, "natural minor", listOf(0, 2, 3, 5, 7, 8, 10));
}

@Serializable
data class MusicalKey(val tonic: PitchClass, val modeId: ScaleModeId) {
    val isExecutable: Boolean get() = modeId.executable != null
    val displayName: String get() = "$tonic ${modeId.executable?.displayName ?: modeId.value}"

    fun scaleIntervals(): List<Int> = requireExecutable().intervals

    /** Returns pitch-class members only; this never changes or quantizes notes. */
    fun scalePitchClasses(): List<PitchClass> = scaleIntervals().mapIndexed { index, interval ->
        if (index == 0) tonic else PitchClass.canonical(tonic.chromatic + interval)
    }

    fun contains(pitch: PitchClass): Boolean =
        Math.floorMod(pitch.chromatic - tonic.chromatic, 12) in scaleIntervals()

    private fun requireExecutable(): ExecutableScaleMode = requireNotNull(modeId.executable) {
        "Scale mode '${modeId.value}' is preserved for compatibility but cannot be executed by this version"
    }
}

@Serializable
data class Tempo(val bpm: Double) {
    init { require(bpm.isFinite() && bpm > 0.0) { "Tempo BPM must be positive and finite" } }

    val displayName: String get() = if (bpm % 1.0 == 0.0) "${bpm.toInt()} BPM" else "$bpm BPM"
}

@Serializable
data class TimeSignature(val numerator: Int, val denominator: Int) {
    init {
        require(numerator > 0) { "Time-signature numerator must be positive" }
        require(denominator in setOf(1, 2, 4, 8, 16, 32, 64)) {
            "Time-signature denominator must be a supported power of two"
        }
    }

    val displayName: String get() = "$numerator/$denominator"
}

/** Ordered, presentation-ready choices. UI adapters may display them without inventing defaults. */
data class TonicOption(val value: PitchClass) { val label: String get() = value.toString() }
data class ScaleModeOption(val value: ScaleModeId) { val label: String get() = value.executable?.displayName ?: value.value }
data class TimeSignatureOption(val value: TimeSignature) { val label: String get() = value.displayName }

object MusicalOptionModels {
    val tonics: List<TonicOption> = (0..11).map { TonicOption(PitchClass.canonical(it)) }
    val modes: List<ScaleModeOption> = listOf(ScaleModeId.MAJOR, ScaleModeId.NATURAL_MINOR).map(::ScaleModeOption)
    val timeSignatures: List<TimeSignatureOption> = listOf(
        TimeSignature(2, 4), TimeSignature(3, 4), TimeSignature(4, 4), TimeSignature(5, 4),
        TimeSignature(6, 8), TimeSignature(7, 8), TimeSignature(9, 8), TimeSignature(12, 8)
    ).map(::TimeSignatureOption)
}
