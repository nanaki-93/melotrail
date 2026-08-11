package ai.music.workstation.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProjectTrack(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: TrackType,
    @SerialName("gain")
    var gain: Double = 0.0,
    @SerialName("pan")
    var pan: Double = 0.0,
    @SerialName("muted")
    var muted: Boolean = false,
    @SerialName("solo")
    var solo: Boolean = false,
    @SerialName("filePath")
    val filePath: String? = null,
    @SerialName("duration")
    val duration: Double = 0.0,
    @SerialName("sampleRate")
    val sampleRate: Int = 44100,
    @SerialName("createdAt")
    val createdAt: Instant = Clock.System.now()
) {
    fun withGain(gain: Double): ProjectTrack = copy(gain = gain)
    fun withMuted(muted: Boolean): ProjectTrack = copy(muted = muted)
    fun withSolo(solo: Boolean): ProjectTrack = copy(solo = solo)
    fun withPan(pan: Double): ProjectTrack = copy(pan = pan)
}
