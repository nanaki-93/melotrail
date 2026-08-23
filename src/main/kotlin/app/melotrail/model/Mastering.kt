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

/** Bounded delivery and dynamics policy; it is not an instruction to destroy dynamics for a numeric target. */
@Serializable
data class MasteringProfile(
    val id: String,
    val nominalIntegratedLufs: Double,
    val loudnessToleranceLu: Double,
    val maximumTruePeakDbtp: Double,
    val minimumLraLu: Double,
    val minimumCrestDb: Double,
    val maximumLimiterGainReductionDb: Double
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9-]{1,63}")) && nominalIntegratedLufs.isFinite() && loudnessToleranceLu >= 0.0 &&
            maximumTruePeakDbtp <= 0.0 && minimumLraLu >= 0.0 && minimumCrestDb >= 0.0 && maximumLimiterGainReductionDb >= 0.0) {
            "Mastering profile is invalid"
        }
    }
}

/** The current product profile uses a delivery reference while preserving a separate dynamics safety gate. */
object MasteringProfiles {
    val LOFI = MasteringProfile(
        id = "lofi-v1",
        nominalIntegratedLufs = -14.0,
        loudnessToleranceLu = 1.0,
        maximumTruePeakDbtp = -1.0,
        minimumLraLu = 2.0,
        minimumCrestDb = 5.0,
        maximumLimiterGainReductionDb = 4.0
    )
}

/** Immutable worker measurement evidence returned with one published master. */
data class MasteringMeasurement(
    val standard: String,
    val integratedLufs: Double,
    val truePeakDbtp: Double,
    val lraLu: Double,
    val crestDb: Double,
    val limiterMaxGainReductionDb: Double,
    val limiterMeanGainReductionDb: Double,
    val dynamicsPreserved: Boolean,
    val qualityIssues: List<String>,
    val loudnessReference: String
) {
    init {
        require(standard == "ITU-R BS.1770-4 / EBU R128" && listOf(integratedLufs, truePeakDbtp, lraLu, crestDb,
            limiterMaxGainReductionDb, limiterMeanGainReductionDb).all(Double::isFinite) && qualityIssues == qualityIssues.distinct().sorted()) {
            "Mastering measurement is invalid"
        }
    }
}
