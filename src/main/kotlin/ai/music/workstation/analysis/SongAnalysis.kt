package ai.music.workstation.analysis

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongAnalysis(
    @SerialName("duration")
    val duration: Double,
    @SerialName("sampleRate")
    val sampleRate: Int,
    @SerialName("channels")
    val channels: Int,
    @SerialName("loudness")
    val loudness: LoudnessInfo,
    @SerialName("bpm")
    val bpm: Double? = null,
    @SerialName("key")
    val key: MusicalKey? = null,
    @SerialName("keyConfidence")
    val keyConfidence: Double = 0.0,
    @SerialName("beats")
    val beats: List<Beat> = emptyList(),
    @SerialName("sections")
    val sections: List<Section> = emptyList(),
    @SerialName("onsets")
    val onsets: List<Onset> = emptyList(),
    @SerialName("notes")
    val notes: List<MusicalNote> = emptyList(),
    @SerialName("pitchContour")
    val pitchContour: PitchContour? = null,
    @SerialName("qualityIssues")
    val qualityIssues: List<QualityIssue> = emptyList()
)

@Serializable
data class MusicalKey(
    @SerialName("root")
    val root: String,
    @SerialName("mode")
    val mode: String
) {
    override fun toString(): String = "$root$mode"
}

@Serializable
data class LoudnessInfo(
    @SerialName("integratedLUFS")
    val integratedLUFS: Double,
    @SerialName("truePeak")
    val truePeak: Double,
    @SerialName("rms")
    val rms: Double,
    @SerialName("momentaryLUFS")
    val momentaryLUFS: Double? = null,
    @SerialName("shortTermLUFS")
    val shortTermLUFS: Double? = null
)

@Serializable
data class Beat(
    @SerialName("position")
    val position: Double,
    @SerialName("confidence")
    val confidence: Double
)

@Serializable
data class Section(
    @SerialName("start")
    val start: Double,
    @SerialName("end")
    val end: Double,
    @SerialName("type")
    val type: SectionType
)

@Serializable
enum class SectionType {
    @SerialName("INTRO") INTRO,
    @SerialName("VERSE") VERSE,
    @SerialName("CHORUS") CHORUS,
    @SerialName("BRIDGE") BRIDGE,
    @SerialName("OUTRO") OUTRO,
    @SerialName("INSTRUMENTAL") INSTRUMENTAL,
    @SerialName("UNKNOWN") UNKNOWN
}

@Serializable
data class Onset(
    @SerialName("position")
    val position: Double,
    @SerialName("strength")
    val strength: Double
)

@Serializable
data class MusicalNote(
    @SerialName("position")
    val position: Double,
    @SerialName("duration")
    val duration: Double,
    @SerialName("frequency")
    val frequency: Double,
    @SerialName("midiNote")
    val midiNote: Int,
    @SerialName("velocity")
    val velocity: Double
)

@Serializable
data class PitchContour(
    @SerialName("positions")
    val positions: List<Double>,
    @SerialName("frequencies")
    val frequencies: List<Double>
)

@Serializable
data class QualityIssue(
    @SerialName("type")
    val type: IssueType,
    @SerialName("position")
    val position: Double? = null,
    @SerialName("severity")
    val severity: IssueSeverity,
    @SerialName("description")
    val description: String
)

@Serializable
enum class IssueType {
    @SerialName("CLIPPING") CLIPPING,
    @SerialName("DC_OFFSET") DC_OFFSET,
    @SerialName("SILENCE") SILENCE,
    @SerialName("NOISE") NOISE,
    @SerialName("DISTORTION") DISTORTION,
    @SerialName("POP") POP,
    @SerialName("CLICK") CLICK
}

@Serializable
enum class IssueSeverity {
    @SerialName("LOW") LOW,
    @SerialName("MEDIUM") MEDIUM,
    @SerialName("HIGH") HIGH
}

interface AudioAnalyzer {
    suspend fun analyze(path: String): SongAnalysis
    suspend fun cancel()
}
