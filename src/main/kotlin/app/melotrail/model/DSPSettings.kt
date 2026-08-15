package app.melotrail.model

import kotlinx.serialization.Serializable

@Serializable
data class DSPSettings(
    val amount: Double = 0.5,
    val tape: Double = 0.0,
    val vinyl: Double = 0.0,
    val noise: Double = 0.0,
    val wowFlutter: Double = 0.0,
    val warmth: Double = 0.5,
    val sampleRateReduction: Int? = null,
    val bitDepthReduction: Int? = null,
    val lowPassCutoff: Double? = null,
    val softClip: Boolean = false,
    val compression: Double? = null,
    val stereoWidth: Double = 1.0
)

object DSPSettingsDefaults {
    val DEFAULT = DSPSettings()
}
