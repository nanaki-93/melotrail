package app.melotrail.arrangement

import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A deterministic, arrangement-only summary for release comparison.  Melody,
 * harmony, and the source MIDI are deliberately excluded: they are human
 * authorship evidence, not knobs for reducing a similarity score.
 */
@Serializable
data class ReleaseFingerprint(
    val version: Int = VERSION,
    val sha256: String,
    val structure: List<String>,
    val energyCurve: List<Int>,
    val instrumentEntryExitSequence: List<String>,
    val bassPatternSequence: List<String>,
    val drumGrooveSequence: List<String>,
    val transitionSequence: List<String>,
    val tempoBpmMilli: Int,
    val meter: String,
    val swingProfile: List<Int>,
    val arrangementDensityCurve: List<Int>
) {
    companion object { const val VERSION = 1 }
}

@Serializable
enum class ReleaseSimilarityFeature {
    STRUCTURE,
    ENERGY_CURVE,
    INSTRUMENT_ENTRY_EXIT_SEQUENCE,
    BASS_PATTERN_SEQUENCE,
    DRUM_GROOVE_SEQUENCE,
    TRANSITION_SEQUENCE,
    TEMPO_SWING_PROFILE,
    ARRANGEMENT_DENSITY_CURVE
}

@Serializable
data class ReleaseSimilarityFeatureExplanation(
    val feature: ReleaseSimilarityFeature,
    val score: Double,
    val explanation: String
)

@Serializable
data class ReleaseSimilarityComparison(
    val referenceFingerprintSha256: String,
    val score: Double,
    val explanations: List<ReleaseSimilarityFeatureExplanation>
)

@Serializable
enum class ReleaseSimilarityReviewStatus { NOT_COMPARED, CLEAR, WARNING }

/**
 * Advisory release evidence only. It intentionally makes no copyright,
 * Content ID, or YouTube Partner Program determination.
 */
@Serializable
data class ReleaseSimilarityReport(
    val version: Int = VERSION,
    val fingerprint: ReleaseFingerprint,
    val comparisonCount: Int,
    val highestSimilarityScore: Double? = null,
    val status: ReleaseSimilarityReviewStatus,
    val comparisons: List<ReleaseSimilarityComparison>,
    val advisory: String = ADVISORY,
    val optionalReplanScope: List<String> = OPTIONAL_REPLAN_SCOPE
) {
    companion object {
        const val VERSION = 1
        const val ADVISORY = "Similarity review is advisory only; it does not determine YouTube Partner Program eligibility or any platform-policy outcome."
        val OPTIONAL_REPLAN_SCOPE = listOf("arrangement", "groove", "orchestration")
    }
}

/** Pure deterministic critic; callers retain control over which completed releases are compared. */
class ReleaseSimilarityCritic {
    /** Confirms that a serialized fingerprint still matches its canonical feature payload. */
    fun isValid(fingerprint: ReleaseFingerprint): Boolean =
        fingerprint.version == ReleaseFingerprint.VERSION && fingerprint.sha256 == digest(FingerprintValues(
            fingerprint.structure, fingerprint.energyCurve, fingerprint.instrumentEntryExitSequence, fingerprint.bassPatternSequence,
            fingerprint.drumGrooveSequence, fingerprint.transitionSequence, fingerprint.tempoBpmMilli, fingerprint.meter,
            fingerprint.swingProfile, fingerprint.arrangementDensityCurve
        ).canonical())

    /** Derives the complete non-authorship feature vector from an approved detailed arrangement. */
    fun fingerprint(
        arrangement: DetailedArrangement,
        tempo: Tempo,
        meter: TimeSignature
    ): ReleaseFingerprint {
        val sections = arrangement.sections.sortedBy(DetailedArrangementSection::index)
        val structure = sections.map { "${it.instanceId}:${it.partId}:${it.role.name.lowercase()}" }
        val energy = sections.map { scaled(it.energy) }
        val activity = sections.mapIndexed { index, section ->
            val current = section.instruments.map(DetailedInstrumentPlan::name).sorted()
            val previous = sections.getOrNull(index - 1)?.instruments?.map(DetailedInstrumentPlan::name)?.toSet().orEmpty()
            val entered = (current.toSet() - previous).sorted()
            val exited = (previous - current.toSet()).sorted()
            "enter=${entered.joinToString(",")};exit=${exited.joinToString(",")}"
        }
        val bass = sections.map { section ->
            section.instruments.filterIsInstance<BassInstrumentPlan>().singleOrNull()?.let {
                "${it.pattern.id.value}:${it.role.name.lowercase()}:${it.movement.name.lowercase()}"
            } ?: "absent"
        }
        val drums = sections.map { section ->
            section.instruments.filterIsInstance<DrumsInstrumentPlan>().singleOrNull()?.let {
                "${it.pattern.id.value}:${it.grooveCharacter.name.lowercase()}:${it.fillPlacement.name.lowercase()}"
            } ?: "absent"
        }
        val transitions = sections.map { section ->
            val bridge = section.transitionOut.bridge?.elements.orEmpty().map { it.name.lowercase() }.sorted().joinToString(",")
            "${section.transitionOut.type.name.lowercase()}:${section.transitionOut.bars}:$bridge"
        }
        val swing = sections.map { section -> scaled(section.instruments.filterIsInstance<DrumsInstrumentPlan>().singleOrNull()?.swing ?: 0.0) }
        val density = sections.map { section ->
            scaled(section.instruments.map { instrument ->
                when (instrument) {
                    is PianoSourcePlan -> 1.0
                    is BassInstrumentPlan -> instrument.density
                    is DrumsInstrumentPlan -> instrument.density
                    is PadInstrumentPlan -> instrument.density
                    is StringsInstrumentPlan -> instrument.density
                }
            }.average())
        }
        val unsigned = FingerprintValues(structure, energy, activity, bass, drums, transitions, scaled(tempo.bpm), meter.displayName, swing, density)
        return ReleaseFingerprint(
            sha256 = digest(unsigned.canonical()), structure = structure, energyCurve = energy,
            instrumentEntryExitSequence = activity, bassPatternSequence = bass, drumGrooveSequence = drums,
            transitionSequence = transitions, tempoBpmMilli = scaled(tempo.bpm), meter = meter.displayName,
            swingProfile = swing, arrangementDensityCurve = density
        )
    }

    /** Scores the current release against explicit completed-release references without self-comparison. */
    fun review(current: ReleaseFingerprint, references: List<ReleaseFingerprint>): ReleaseSimilarityReport {
        require(isValid(current)) { "Release similarity fingerprint is invalid." }
        require(references.all(::isValid)) { "A release similarity reference fingerprint is invalid." }
        val comparisons = references.filter { it.sha256 != current.sha256 }.distinctBy(ReleaseFingerprint::sha256)
            .sortedBy(ReleaseFingerprint::sha256).map { reference -> compare(current, reference) }
        val highest = comparisons.maxOfOrNull(ReleaseSimilarityComparison::score)
        return ReleaseSimilarityReport(
            fingerprint = current, comparisonCount = comparisons.size, highestSimilarityScore = highest,
            status = when {
                comparisons.isEmpty() -> ReleaseSimilarityReviewStatus.NOT_COMPARED
                highest!! >= WARNING_THRESHOLD -> ReleaseSimilarityReviewStatus.WARNING
                else -> ReleaseSimilarityReviewStatus.CLEAR
            },
            comparisons = comparisons
        )
    }

    /** Builds one feature-level comparison against a validated reference fingerprint. */
    private fun compare(current: ReleaseFingerprint, reference: ReleaseFingerprint): ReleaseSimilarityComparison {
        /** Converts a positional token sequence into a named explanation. */
        fun sequence(feature: ReleaseSimilarityFeature, name: String, left: List<String>, right: List<String>) =
            explanation(feature, sequenceScore(left, right), "$name ${comparisonText(sequenceScore(left, right))}")
        val explanations = listOf(
            sequence(ReleaseSimilarityFeature.STRUCTURE, "Structure", current.structure, reference.structure),
            numeric(ReleaseSimilarityFeature.ENERGY_CURVE, "Energy curve", current.energyCurve, reference.energyCurve),
            sequence(ReleaseSimilarityFeature.INSTRUMENT_ENTRY_EXIT_SEQUENCE, "Instrument entry/exit sequence", current.instrumentEntryExitSequence, reference.instrumentEntryExitSequence),
            sequence(ReleaseSimilarityFeature.BASS_PATTERN_SEQUENCE, "Bass-pattern sequence", current.bassPatternSequence, reference.bassPatternSequence),
            sequence(ReleaseSimilarityFeature.DRUM_GROOVE_SEQUENCE, "Drum-groove sequence", current.drumGrooveSequence, reference.drumGrooveSequence),
            sequence(ReleaseSimilarityFeature.TRANSITION_SEQUENCE, "Transition sequence", current.transitionSequence, reference.transitionSequence),
            tempoSwing(current, reference),
            numeric(ReleaseSimilarityFeature.ARRANGEMENT_DENSITY_CURVE, "Arrangement-density curve", current.arrangementDensityCurve, reference.arrangementDensityCurve)
        )
        return ReleaseSimilarityComparison(reference.sha256, explanations.map(ReleaseSimilarityFeatureExplanation::score).average(), explanations)
    }

    /** Blends bounded tempo distance, exact meter, and the per-section swing curve. */
    private fun tempoSwing(current: ReleaseFingerprint, reference: ReleaseFingerprint): ReleaseSimilarityFeatureExplanation {
        val tempoScore = (1.0 - abs(current.tempoBpmMilli - reference.tempoBpmMilli).toDouble() / TEMPO_WINDOW_MILLI).coerceIn(0.0, 1.0)
        val meterScore = if (current.meter == reference.meter) 1.0 else 0.0
        val swingScore = numericScore(current.swingProfile, reference.swingProfile)
        val score = (tempoScore + meterScore + swingScore) / 3.0
        return explanation(ReleaseSimilarityFeature.TEMPO_SWING_PROFILE, score, "Tempo/swing profile ${comparisonText(score)}")
    }

    /** Formats a normalized numeric-curve comparison for one named feature. */
    private fun numeric(feature: ReleaseSimilarityFeature, name: String, left: List<Int>, right: List<Int>): ReleaseSimilarityFeatureExplanation {
        val score = numericScore(left, right)
        return explanation(feature, score, "$name ${comparisonText(score)}")
    }

    /** Rounds a stable score only for persisted/presented evidence. */
    private fun explanation(feature: ReleaseSimilarityFeature, score: Double, explanation: String) =
        ReleaseSimilarityFeatureExplanation(feature, rounded(score), explanation + " (${format(score)})")

    /** Uses positional equality so arrangement order remains materially significant. */
    private fun sequenceScore(left: List<String>, right: List<String>): Double {
        val length = maxOf(left.size, right.size)
        if (length == 0) return 1.0
        return (0 until length).count { left.getOrNull(it) == right.getOrNull(it) }.toDouble() / length
    }

    /** Scores scaled curves while penalizing unmatched trailing sections. */
    private fun numericScore(left: List<Int>, right: List<Int>): Double {
        val length = maxOf(left.size, right.size)
        if (length == 0) return 1.0
        val valueScore = (0 until length).sumOf { index ->
            val a = left.getOrNull(index) ?: return@sumOf 0.0
            val b = right.getOrNull(index) ?: return@sumOf 0.0
            (1.0 - abs(a - b).toDouble() / SCALE).coerceIn(0.0, 1.0)
        } / length
        val lengthScore = minOf(left.size, right.size).toDouble() / length
        return valueScore * lengthScore
    }

    /** Selects an interpretable, non-policy label for one feature score. */
    private fun comparisonText(score: Double) = when {
        score >= 0.999 -> "is identical"
        score >= WARNING_THRESHOLD -> "is highly similar"
        score <= 0.001 -> "does not match"
        else -> "partially overlaps"
    }

    /** Converts a bounded ratio or BPM value to fixed integer precision. */
    private fun scaled(value: Double): Int = (value * SCALE).roundToInt()
    /** Keeps persisted feature scores independent of locale-specific floating formatting. */
    private fun rounded(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0
    /** Formats an explanation score with a fixed root locale. */
    private fun format(value: Double): String = "%.2f".format(java.util.Locale.ROOT, value)
    /** Computes the stable digest used as the public fingerprint identity. */
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private data class FingerprintValues(
        val structure: List<String>, val energy: List<Int>, val activity: List<String>, val bass: List<String>, val drums: List<String>,
        val transitions: List<String>, val tempo: Int, val meter: String, val swing: List<Int>, val density: List<Int>
    ) {
        /** Provides the one ordered serialization covered by the fingerprint digest. */
        fun canonical(): String = listOf(
            structure.joinToString(","), energy.joinToString(","), activity.joinToString("|"), bass.joinToString(","), drums.joinToString(","),
            transitions.joinToString("|"), tempo.toString(), meter, swing.joinToString(","), density.joinToString(",")
        ).joinToString("\n")
    }

    private companion object {
        const val SCALE = 1_000
        const val TEMPO_WINDOW_MILLI = 40_000.0
        const val WARNING_THRESHOLD = 0.80
    }
}
