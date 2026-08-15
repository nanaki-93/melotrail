package app.melotrail.audio

import app.melotrail.model.ProjectTrack
import app.melotrail.model.TrackType

class PlaybackController(
    private val player: AudioPlayer
) {
    private val _trackVolumes = mutableMapOf<String, Double>()
    private val _trackMutes = mutableMapOf<String, Boolean>()
    private val _trackSolos = mutableMapOf<String, Boolean>()

    private var _currentBuffer: AudioBuffer? = null
    private var _currentTracks: List<ProjectTrack> = emptyList()

    fun setTracks(tracks: List<ProjectTrack>) {
        _currentTracks = tracks
        tracks.forEach { track ->
            _trackVolumes.putIfAbsent(track.id, track.gain)
            _trackMutes.putIfAbsent(track.id, track.muted)
            _trackSolos.putIfAbsent(track.id, track.solo)
        }
    }

    fun play() {
        val buffer = mixTracks()
        if (buffer != null) {
            _currentBuffer = buffer
            player.play(buffer)
        }
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.resume()
    }

    fun stop() {
        player.stop()
        _currentBuffer = null
    }

    fun seek(position: Double) {
        player.seek(position)
    }

    fun setTrackVolume(trackId: String, volume: Double) {
        _trackVolumes[trackId] = volume.coerceIn(0.0, 1.0)
    }

    fun setTrackMute(trackId: String, muted: Boolean) {
        _trackMutes[trackId] = muted
    }

    fun setTrackSolo(trackId: String, soloed: Boolean) {
        _trackSolos[trackId] = soloed
    }

    fun getTrackVolume(trackId: String): Double {
        return _trackVolumes[trackId] ?: 1.0
    }

    fun isTrackMuted(trackId: String): Boolean {
        return _trackMutes[trackId] ?: false
    }

    fun isTrackSoloed(trackId: String): Boolean {
        return _trackSolos[trackId] ?: false
    }

    fun hasAnySolo(): Boolean {
        return _trackSolos.values.any { it }
    }

    fun getState(): PlaybackState = player.state.value

    fun getCurrentPosition(): Double = player.currentPosition.value

    fun getTotalDuration(): Double = player.totalDuration.value

    fun getVolume(): Double = player.volume.value

    fun setVolume(volume: Double) {
        player.setVolume(volume)
    }

    private fun mixTracks(): AudioBuffer? {
        if (_currentTracks.isEmpty()) return null

        val soloedTracks = _currentTracks.filter { track ->
            val isSoloed = isTrackSoloed(track.id)
            if (hasAnySolo()) isSoloed else !isTrackMuted(track.id)
        }.filter { track ->
            val volume = getTrackVolume(track.id)
            volume > 0.0
        }

        if (soloedTracks.isEmpty()) return null

        // Find the longest track
        val maxDuration = soloedTracks.maxOfOrNull { it.duration ?: 0.0 } ?: 0.0

        // For now, return the first non-muted track
        // In production, implement proper mixing
        val firstTrack = soloedTracks.first()
        return null // Buffer would be loaded from file path
    }
}
