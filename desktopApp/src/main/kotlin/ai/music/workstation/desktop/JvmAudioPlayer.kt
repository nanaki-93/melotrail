package ai.music.workstation.desktop

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioPlayer
import ai.music.workstation.audio.PlaybackState
import ai.music.workstation.audio.WAVDecoder
import ai.music.workstation.model.ErrorReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

interface ArtifactAudioPlayer : AudioPlayer {
    fun play(path: Path)
}

/** Local monitor-only player. It accepts canonical WAV artifacts and never writes project files. */
class JvmAudioPlayer : ArtifactAudioPlayer {
    private val _state = MutableStateFlow(PlaybackState.STOPPED)
    private val _position = MutableStateFlow(0.0)
    private val _duration = MutableStateFlow(0.0)
    private val _volume = MutableStateFlow(1.0)
    override val state: StateFlow<PlaybackState> = _state
    override val currentPosition: StateFlow<Double> = _position
    override val totalDuration: StateFlow<Double> = _duration
    override val volume: StateFlow<Double> = _volume

    private val lock = Any()
    private var buffer: AudioBuffer? = null
    private var frame = 0
    private var line: SourceDataLine? = null
    private var worker: Thread? = null

    override fun play(path: Path) {
        require(Files.isRegularFile(path)) { "Preview artifact is missing: $path" }
        val decoded = WAVDecoder(noOpReporter).decode(path)
        require(decoded.samples.isNotEmpty() && decoded.samples.all { it.isFinite() }) { "Preview artifact is not valid audio: $path" }
        play(decoded)
    }

    override fun play(buffer: AudioBuffer) = synchronized(lock) {
        closeLine()
        this.buffer = buffer
        frame = 0
        _duration.value = buffer.length.toDouble() / buffer.format.sampleRate
        _position.value = 0.0
        start()
    }

    override fun pause() = synchronized(lock) {
        if (_state.value == PlaybackState.PLAYING) {
            _state.value = PlaybackState.PAUSED
            line?.stop()
        }
    }

    override fun resume() = synchronized(lock) {
        if (_state.value == PlaybackState.PAUSED && buffer != null) start()
    }

    override fun stop() = synchronized(lock) {
        _state.value = PlaybackState.STOPPED
        frame = 0
        _position.value = 0.0
        closeLine()
    }

    override fun seek(position: Double) = synchronized(lock) {
        val audio = buffer ?: return
        frame = (position.coerceIn(0.0, _duration.value) * audio.format.sampleRate).toInt().coerceIn(0, audio.length)
        _position.value = frame.toDouble() / audio.format.sampleRate
        if (_state.value == PlaybackState.PLAYING) {
            closeLine()
            start()
        }
    }

    override fun setVolume(volume: Double) { _volume.value = volume.coerceIn(0.0, 1.0) }
    override fun getVolume(): Double = _volume.value

    private fun start() {
        val audio = buffer ?: return
        _state.value = PlaybackState.PLAYING
        val startFrame = frame
        worker?.interrupt()
        worker = thread(name = "jvm-audio-player", isDaemon = true) {
            var localLine: SourceDataLine? = null
            try {
                val format = AudioFormat(audio.format.sampleRate.toFloat(), 16, audio.format.channels, true, false)
                localLine = AudioSystem.getSourceDataLine(format).also { it.open(format); it.start() }
                synchronized(lock) { line = localLine }
                var current = startFrame
                val chunkFrames = 1024
                while (!Thread.currentThread().isInterrupted && current < audio.length && _state.value == PlaybackState.PLAYING) {
                    val count = minOf(chunkFrames, audio.length - current)
                    localLine.write(pcm16(audio, current, count, _volume.value), 0, count * audio.format.channels * 2)
                    current += count
                    synchronized(lock) {
                        frame = current
                        _position.value = current.toDouble() / audio.format.sampleRate
                    }
                }
                if (current >= audio.length && _state.value == PlaybackState.PLAYING) synchronized(lock) {
                    frame = audio.length
                    _position.value = _duration.value
                    _state.value = PlaybackState.STOPPED
                }
            } catch (_: Exception) {
                // Audio devices may be unplugged or unavailable. Monitoring must never crash the workspace.
                synchronized(lock) { _state.value = PlaybackState.STOPPED }
            } finally {
                localLine?.let { active ->
                    runCatching { active.drain(); active.close() }
                    synchronized(lock) { if (line === active) line = null }
                }
            }
        }
    }

    private fun pcm16(audio: AudioBuffer, start: Int, frames: Int, volume: Double): ByteArray {
        val bytes = ByteBuffer.allocate(frames * audio.format.channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { offset ->
            repeat(audio.format.channels) { channel ->
                bytes.putShort((audio.getSample(channel, start + offset) * volume.toFloat()).coerceIn(-1f, 1f).times(32767f).toInt().toShort())
            }
        }
        return bytes.array()
    }

    private fun closeLine() {
        worker?.interrupt(); worker = null
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
    }

    override fun close() = stop()

    private companion object {
        val noOpReporter = object : ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
    }
}
