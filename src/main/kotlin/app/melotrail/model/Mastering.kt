package app.melotrail.model

import kotlinx.serialization.Serializable

@Serializable
data class MasteringState(
    val eqEnabled: Boolean = true,
    val eqSettings: MasteringEQSettings = MasteringEQSettings(),
    val compressorEnabled: Boolean = true,
    val compressorSettings: CompressorSettings = CompressorSettings(),
    val saturationEnabled: Boolean = false,
    val saturationSettings: SaturationSettings = SaturationSettings(),
    val stereoEnabled: Boolean = false,
    val stereoSettings: StereoSettings = StereoSettings(),
    val limiterEnabled: Boolean = true,
    val limiterSettings: LimiterSettings = LimiterSettings()
)

@Serializable
data class MasteringEQSettings(
    val bands: List<EQBand> = listOf(
        EQBand(type = "highshelf", frequency = 10000.0, gain = 0.0, q = 0.707),
        EQBand(type = "peaking", frequency = 5000.0, gain = 0.0, q = 1.0),
        EQBand(type = "peaking", frequency = 1000.0, gain = 0.0, q = 1.0),
        EQBand(type = "peaking", frequency = 200.0, gain = 0.0, q = 1.0),
        EQBand(type = "lowshelf", frequency = 100.0, gain = 0.0, q = 0.707)
    )
)

@Serializable
data class CompressorSettings(
    val threshold: Double = -24.0,
    val ratio: Double = 4.0,
    val attack: Double = 10.0,
    val release: Double = 100.0,
    val makeup: Double = 0.0
)

@Serializable
data class SaturationSettings(
    val mode: String = "tape",
    val amount: Double = 0.5
)

@Serializable
data class StereoSettings(
    val width: Double = 1.0
)

@Serializable
data class LimiterSettings(
    val threshold: Double = -1.0
)

@Serializable
data class EQBand(
    val type: String = "peaking",
    val frequency: Double = 1000.0,
    val gain: Double = 0.0,
    val q: Double = 1.0
)

@Serializable
data class LoudnessReport(
    val integratedLUFS: Double,
    val truePeak: Double,
    val rms: Double
)
