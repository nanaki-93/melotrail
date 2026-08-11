package ai.music.workstation.dsp

import ai.music.workstation.model.DSPSettings
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
            description = "Warm analog cassette tape warmth with subtle vinyl texture",
            settings = DSPSettings(
                amount = 0.6,
                tape = 0.7,
                vinyl = 0.3,
                noise = 0.2,
                wowFlutter = 0.4,
                warmth = 0.7,
                sampleRateReduction = 4,
                bitDepthReduction = 14,
                lowPassCutoff = 6000.0,
                softClip = true,
                compression = 0.3,
                stereoWidth = 0.9
            )
        ),
        LOFIPreset(
            name = "Dusty Vinyl",
            description = "Crackly vinyl record with authentic surface noise",
            settings = DSPSettings(
                amount = 0.7,
                tape = 0.3,
                vinyl = 0.8,
                noise = 0.6,
                wowFlutter = 0.3,
                warmth = 0.5,
                sampleRateReduction = null,
                bitDepthReduction = null,
                lowPassCutoff = 7000.0,
                softClip = true,
                compression = 0.2,
                stereoWidth = 1.0
            )
        ),
        LOFIPreset(
            name = "Bedroom LoFi",
            description = "Low-fidelity bedroom recording aesthetic",
            settings = DSPSettings(
                amount = 0.5,
                tape = 0.5,
                vinyl = 0.4,
                noise = 0.3,
                wowFlutter = 0.3,
                warmth = 0.6,
                sampleRateReduction = 8,
                bitDepthReduction = 12,
                lowPassCutoff = 5000.0,
                softClip = true,
                compression = 0.4,
                stereoWidth = 0.8
            )
        ),
        LOFIPreset(
            name = "Old Sampler",
            description = "Vintage sampler grit with heavy bit reduction",
            settings = DSPSettings(
                amount = 0.8,
                tape = 0.2,
                vinyl = 0.2,
                noise = 0.4,
                wowFlutter = 0.6,
                warmth = 0.4,
                sampleRateReduction = 16,
                bitDepthReduction = 8,
                lowPassCutoff = 4000.0,
                softClip = true,
                compression = 0.5,
                stereoWidth = 0.7
            )
        ),
        LOFIPreset(
            name = "Late Night",
            description = "Subtle warmth for late night listening sessions",
            settings = DSPSettings(
                amount = 0.4,
                tape = 0.4,
                vinyl = 0.2,
                noise = 0.1,
                wowFlutter = 0.2,
                warmth = 0.8,
                sampleRateReduction = null,
                bitDepthReduction = null,
                lowPassCutoff = 8000.0,
                softClip = false,
                compression = 0.2,
                stereoWidth = 1.1
            )
        ),
        LOFIPreset(
            name = "Rainy Coffee Shop",
            description = "Cozy coffee shop atmosphere with rain ambiance",
            settings = DSPSettings(
                amount = 0.5,
                tape = 0.5,
                vinyl = 0.5,
                noise = 0.4,
                wowFlutter = 0.3,
                warmth = 0.6,
                sampleRateReduction = 4,
                bitDepthReduction = 14,
                lowPassCutoff = 6500.0,
                softClip = true,
                compression = 0.3,
                stereoWidth = 0.9
            )
        )
    )

    fun getByName(name: String): LOFIPreset? = DEFAULT_PRESETS.find { it.name == name }
}
