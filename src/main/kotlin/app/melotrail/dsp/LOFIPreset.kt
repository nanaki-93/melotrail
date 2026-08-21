package app.melotrail.dsp

import app.melotrail.model.DSPSettings
import kotlinx.serialization.Serializable

@Serializable
data class LOFIPreset(
    val name: String,
    val settings: DSPSettings,
    val description: String? = null
)

object LOFIPresets {
    val DEFAULT_PRESETS: List<LOFIPreset> = listOf(
        LOFIPreset(
            name = "Warm Cassette",
            description = "Subtle tape character while preserving the piano",
            settings = DSPSettings(
                amount = 0.25,
                tape = 0.10,
                vinyl = 0.02,
                noise = 0.01,
                wowFlutter = 0.03,
                warmth = 0.65,
                sampleRateReduction = 2,
                bitDepthReduction = 16,
                lowPassCutoff = 12000.0,
                softClip = false,
                compression = 0.08,
                stereoWidth = 1.0
            )
        ),
        LOFIPreset(
            name = "Dusty Vinyl",
            description = "Very subtle vinyl texture without masking the piano",
            settings = DSPSettings(
                amount = 0.30,
                tape = 0.05,
                vinyl = 0.06,
                noise = 0.02,
                wowFlutter = 0.02,
                warmth = 0.55,
                sampleRateReduction = null,
                bitDepthReduction = null,
                lowPassCutoff = 12000.0,
                softClip = false,
                compression = 0.05,
                stereoWidth = 1.0
            )
        ),
        LOFIPreset(
            name = "Bedroom LoFi",
            description = "Audible low-fidelity character while keeping the melody intact",
            settings = DSPSettings(
                amount = 0.65,
                tape = 0.12,
                vinyl = 0.04,
                noise = 0.015,
                wowFlutter = 0.05,
                warmth = 0.65,
                sampleRateReduction = 2,
                bitDepthReduction = 14,
                lowPassCutoff = 10000.0,
                softClip = false,
                compression = 0.12,
                stereoWidth = 0.95
            )
        ),
        LOFIPreset(
            name = "Old Sampler",
            description = "Clearly degraded sampler character with controlled noise",
            settings = DSPSettings(
                amount = 0.90,
                tape = 0.08,
                vinyl = 0.03,
                noise = 0.01,
                wowFlutter = 0.06,
                warmth = 0.55,
                sampleRateReduction = 4,
                bitDepthReduction = 12,
                lowPassCutoff = 9000.0,
                softClip = false,
                compression = 0.18,
                stereoWidth = 0.95
            )
        ),
        LOFIPreset(
            name = "Late Night",
            description = "Very subtle warmth for piano and ambient recordings",
            settings = DSPSettings(
                amount = 0.15,
                tape = 0.05,
                vinyl = 0.01,
                noise = 0.005,
                wowFlutter = 0.015,
                warmth = 0.70,
                sampleRateReduction = null,
                bitDepthReduction = null,
                lowPassCutoff = 14000.0,
                softClip = false,
                compression = 0.05,
                stereoWidth = 1.0
            )
        ),
        LOFIPreset(
            name = "Rainy Coffee Shop",
            description = "Subtle tape/vinyl ambience without overwhelming the source",
            settings = DSPSettings(
                amount = 0.30,
                tape = 0.10,
                vinyl = 0.08,
                noise = 0.025,
                wowFlutter = 0.03,
                warmth = 0.65,
                sampleRateReduction = 2,
                bitDepthReduction = 16,
                lowPassCutoff = 11000.0,
                softClip = false,
                compression = 0.08,
                stereoWidth = 1.0
            )
        )
    )

    fun getByName(name: String): LOFIPreset? =
        DEFAULT_PRESETS.find { it.name == name }
}
