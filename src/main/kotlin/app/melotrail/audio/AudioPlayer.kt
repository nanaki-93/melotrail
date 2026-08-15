package app.melotrail.audio

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

/**
 * Platform-agnostic audio player interface.
 * Implementations are provided per-platform (JVM, etc.).
 */
interface AudioPlayer : AutoCloseable {
    val state: StateFlow<PlaybackState>
    val currentPosition: StateFlow<Double>
    val totalDuration: StateFlow<Double>
    val volume: StateFlow<Double>

    fun play(buffer: AudioBuffer)
    fun pause()
    fun resume()
    fun stop()
    fun seek(position: Double)
    fun setVolume(volume: Double)
    fun getVolume(): Double

    override fun close() {
        stop()
    }
}
