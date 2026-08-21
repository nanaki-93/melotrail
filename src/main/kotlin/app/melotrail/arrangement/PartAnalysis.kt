package app.melotrail.arrangement

import kotlinx.serialization.Serializable

/** Deterministic audio metadata for one imported part. */
@Serializable
data class PartAnalysis(
    val duration: Double,
    val sampleRate: Int,
    val channels: Int,
    val frameCount: Long,
    val peak: Double,
    val rms: Double,
    val nearSilence: Boolean,
    /** Optional musical metadata; null keeps analyses written by V1 compatible. */
    val bpm: Double? = null,
    val keyRoot: String? = null,
    val keyMode: String? = null,
    val keyConfidence: Double = 0.0,
    val leadingSilenceSeconds: Double = 0.0,
    val trailingSilenceSeconds: Double = 0.0,
    val onsetsSeconds: List<Double> = emptyList()
)
