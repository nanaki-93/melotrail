package app.melotrail.desktop

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioPlayer
import app.melotrail.audio.PlaybackState
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** The boundary at which a monitor session failed. Kept deliberately distinct for recovery UI. */
enum class PlaybackFailureStage { RESOLUTION, DECODE, PREPARE, DEVICE_OPEN, DEVICE_START, RUNTIME }

data class PlaybackFailure(
    val stage: PlaybackFailureStage,
    val message: String,
    val cause: Throwable
)

sealed interface PlaybackPrepareResult {
    data class Ready(val durationSeconds: Double) : PlaybackPrepareResult
    data class Failed(val failure: PlaybackFailure) : PlaybackPrepareResult
}

sealed interface PlaybackStartResult {
    data object Started : PlaybackStartResult
    data class Failed(val failure: PlaybackFailure) : PlaybackStartResult
}

interface ArtifactAudioPlayer : AudioPlayer {
    /** The last typed boundary failure; cleared only after a new valid preparation or start. */
    val failure: StateFlow<PlaybackFailure?>
    suspend fun prepare(path: Path): PlaybackPrepareResult
    suspend fun start(): PlaybackStartResult
    suspend fun play(path: Path): PlaybackStartResult
}

internal fun interface AudioArtifactDecoder {
    fun decode(path: Path): AudioBuffer
}

internal interface AudioOutputLine {
    fun start()
    fun stop()
    fun flush()
    fun close()
    fun write(bytes: ByteArray, offset: Int, length: Int): Int
}

internal fun interface AudioOutputDevice {
    fun open(format: AudioFormat): AudioOutputLine
}

/** Local monitor-only player. It accepts canonical WAV artifacts and never writes project files. */
class JvmAudioPlayer internal constructor(
    private val decoder: AudioArtifactDecoder = AudioArtifactDecoder { WAVDecoder(ErrorReporter.NoOp).decode(it) },
    private val outputDevice: AudioOutputDevice = AudioOutputDevice { format -> JavaxAudioOutputLine(AudioSystem.getSourceDataLine(format).also { it.open(format) }) },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val failureReporter: (PlaybackFailure) -> Unit = {}
) : ArtifactAudioPlayer {
    constructor() : this(
        decoder = AudioArtifactDecoder { WAVDecoder(ErrorReporter.NoOp).decode(it) },
        outputDevice = AudioOutputDevice { format -> JavaxAudioOutputLine(AudioSystem.getSourceDataLine(format).also { it.open(format) }) }
    )

    private val _state = MutableStateFlow(PlaybackState.STOPPED)
    private val _position = MutableStateFlow(0.0)
    private val _duration = MutableStateFlow(0.0)
    private val _volume = MutableStateFlow(1.0)
    private val _failure = MutableStateFlow<PlaybackFailure?>(null)
    override val state: StateFlow<PlaybackState> = _state
    override val currentPosition: StateFlow<Double> = _position
    override val totalDuration: StateFlow<Double> = _duration
    override val volume: StateFlow<Double> = _volume
    override val failure: StateFlow<PlaybackFailure?> = _failure

    private val lifecycleLock = ReentrantLock()
    private val stateLock = Object()
    private val legacyScope = CoroutineScope(SupervisorJob() + workDispatcher)
    private var buffer: AudioBuffer? = null
    private var frame = 0
    private var line: AudioOutputLine? = null
    private var worker: Thread? = null
    private var generation = 0L
    private var closed = false

    override suspend fun prepare(path: Path): PlaybackPrepareResult = withContext(workDispatcher) {
        if (!Files.isRegularFile(path)) return@withContext prepareFailure(PlaybackFailureStage.RESOLUTION, "Preview artifact is missing.", IllegalArgumentException("Preview artifact is missing: $path"))
        if (!isRiffWave(path)) return@withContext prepareFailure(PlaybackFailureStage.DECODE, "Preview artifact is not a RIFF/WAVE file.", IllegalArgumentException("Preview artifact is not a RIFF/WAVE file: $path"))
        val decoded = try {
            decoder.decode(path)
        } catch (error: Throwable) {
            return@withContext prepareFailure(PlaybackFailureStage.DECODE, "Preview artifact could not be decoded.", error)
        }
        prepareDecoded(decoded)
    }

    override suspend fun play(path: Path): PlaybackStartResult = withContext(workDispatcher) {
        when (val prepared = prepare(path)) {
            is PlaybackPrepareResult.Failed -> PlaybackStartResult.Failed(prepared.failure)
            is PlaybackPrepareResult.Ready -> start()
        }
    }

    override suspend fun start(): PlaybackStartResult = withContext(workDispatcher) {
        lifecycleLock.withLock {
            val audio = synchronized(stateLock) {
                if (closed) null
                else if (_state.value == PlaybackState.PLAYING) return@withLock PlaybackStartResult.Started
                else if (_state.value == PlaybackState.PAUSED && line != null) {
                    try {
                        line?.start()
                    } catch (error: Throwable) {
                        return@withLock startFailure(PlaybackFailureStage.DEVICE_START, "Audio output could not be restarted. Check the selected output device and retry.", error)
                    }
                    _state.value = PlaybackState.PLAYING
                    stateLock.notifyAll()
                    return@withLock PlaybackStartResult.Started
                } else buffer
            }
                ?: return@withLock startFailure(PlaybackFailureStage.PREPARE, "No prepared audio is available.", IllegalStateException("No prepared audio is available"))
            synchronized(stateLock) {
                if (_state.value == PlaybackState.STOPPED && frame >= audio.length) {
                    frame = 0
                    _position.value = 0.0
                }
            }
            val output = try {
                outputDevice.open(deviceFormat(audio))
            } catch (error: Throwable) {
                return@withLock startFailure(PlaybackFailureStage.DEVICE_OPEN, "Audio output could not be opened. Check the selected output device and retry.", error)
            }
            try {
                output.start()
            } catch (error: Throwable) {
                closeQuietly(output)
                return@withLock startFailure(PlaybackFailureStage.DEVICE_START, "Audio output could not be started. Check the selected output device and retry.", error)
            }
            val token: Long
            synchronized(stateLock) {
                if (closed || buffer !== audio) {
                    closeQuietly(output)
                    return@withLock startFailure(PlaybackFailureStage.PREPARE, "Prepared audio changed before playback could start.", IllegalStateException("Prepared audio replaced during start"))
                }
                token = ++generation
                line = output
                _failure.value = null
                _state.value = PlaybackState.PLAYING
                worker = thread(name = "jvm-audio-player", isDaemon = true) { runPlayback(token, audio, output) }
            }
            PlaybackStartResult.Started
        }
    }

    /** Retained for the older platform-neutral controller; desktop callers use the typed path API. */
    override fun play(buffer: AudioBuffer) {
        legacyScope.launch {
            when (val prepared = prepareDecoded(buffer)) {
                is PlaybackPrepareResult.Failed -> Unit
                is PlaybackPrepareResult.Ready -> start()
            }
        }
    }

    override fun pause() = lifecycleLock.withLock {
        synchronized(stateLock) {
            if (_state.value == PlaybackState.PLAYING) {
                line?.stop()
                _state.value = PlaybackState.PAUSED
            }
        }
    }

    override fun resume() = lifecycleLock.withLock {
        synchronized(stateLock) {
            if (_state.value == PlaybackState.PAUSED && line != null) {
                line?.start()
                _state.value = PlaybackState.PLAYING
                stateLock.notifyAll()
            }
        }
    }

    override fun stop() = lifecycleLock.withLock { stopActive(resetPosition = true) }

    override fun seek(position: Double) = lifecycleLock.withLock {
        synchronized(stateLock) {
            val audio = buffer ?: return@withLock
            frame = (position.coerceIn(0.0, _duration.value) * audio.format.sampleRate).toInt().coerceIn(0, audio.length)
            _position.value = frame.toDouble() / audio.format.sampleRate
            if (_state.value == PlaybackState.PLAYING) line?.flush()
        }
    }

    override fun setVolume(volume: Double) { _volume.value = volume.coerceIn(0.0, 1.0) }
    override fun getVolume(): Double = _volume.value

    override fun close() = lifecycleLock.withLock {
        synchronized(stateLock) { closed = true }
        stopActive(resetPosition = true)
    }

    private fun prepareDecoded(audio: AudioBuffer): PlaybackPrepareResult = lifecycleLock.withLock {
        val invalid = validationFailure(audio)
        if (invalid != null) return@withLock prepareFailure(PlaybackFailureStage.PREPARE, "Preview artifact is not valid audio.", invalid)
        stopActive(resetPosition = true)
        synchronized(stateLock) {
            if (closed) return@withLock prepareFailure(PlaybackFailureStage.RUNTIME, "Audio player is closed.", IllegalStateException("Audio player is closed"))
            buffer = audio
            _failure.value = null
            _duration.value = audio.length.toDouble() / audio.format.sampleRate
            _position.value = 0.0
            frame = 0
        }
        PlaybackPrepareResult.Ready(_duration.value)
    }

    private fun stopActive(resetPosition: Boolean) {
        val exiting: Thread?
        synchronized(stateLock) {
            ++generation
            exiting = worker
            worker = null
            val activeLine = line
            line = null
            _state.value = PlaybackState.STOPPED
            if (resetPosition) {
                frame = 0
                _position.value = 0.0
            }
            stateLock.notifyAll()
            activeLine?.let(::closeQuietly)
        }
        if (exiting != null && exiting !== Thread.currentThread()) exiting.join()
    }

    private fun runPlayback(token: Long, audio: AudioBuffer, output: AudioOutputLine) {
        var outputFailure: Throwable? = null
        try {
            while (true) {
                val current = synchronized(stateLock) {
                    while (token == generation && _state.value == PlaybackState.PAUSED) stateLock.wait()
                    if (token != generation || _state.value != PlaybackState.PLAYING || frame >= audio.length) return@synchronized -1
                    frame
                }
                if (current < 0) break
                val count = minOf(CHUNK_FRAMES, audio.length - current)
                output.write(pcm16(audio, current, count, _volume.value), 0, count * audio.format.channels * 2)
                synchronized(stateLock) {
                    if (token == generation && frame == current) {
                        frame = current + count
                        _position.value = frame.toDouble() / audio.format.sampleRate
                    }
                }
            }
        } catch (error: Throwable) {
            outputFailure = error
        } finally {
            closeQuietly(output)
            synchronized(stateLock) {
                if (token == generation && line === output) {
                    if (frame >= audio.length) _position.value = _duration.value
                    _state.value = PlaybackState.STOPPED
                    line = null
                    worker = null
                }
            }
            if (outputFailure != null && token == generation) {
                reportFailure(PlaybackFailure(PlaybackFailureStage.RUNTIME, "Audio output stopped unexpectedly. Check the selected output device and retry.", outputFailure))
            }
        }
    }

    private fun validationFailure(audio: AudioBuffer): Throwable? = when {
        audio.format.sampleRate <= 0 -> IllegalArgumentException("Sample rate must be positive")
        audio.format.channels !in 1..8 -> IllegalArgumentException("Unsupported channel count: ${audio.format.channels}")
        audio.samples.isEmpty() || audio.samples.size % audio.format.channels != 0 -> IllegalArgumentException("Audio has no complete frames")
        !audio.duration.isFinite() || audio.duration < 0.0 -> IllegalArgumentException("Audio duration must be finite")
        audio.samples.any { !it.isFinite() } -> IllegalArgumentException("Audio contains non-finite samples")
        else -> null
    }

    private fun prepareFailure(stage: PlaybackFailureStage, message: String, cause: Throwable): PlaybackPrepareResult.Failed = PlaybackPrepareResult.Failed(
        PlaybackFailure(stage, message, cause).also(::reportFailure)
    )

    private fun startFailure(stage: PlaybackFailureStage, message: String, cause: Throwable): PlaybackStartResult.Failed = PlaybackStartResult.Failed(
        PlaybackFailure(stage, message, cause).also(::reportFailure)
    )

    private fun reportFailure(failure: PlaybackFailure) {
        _failure.value = failure
        failureReporter(failure)
    }

    private fun isRiffWave(path: Path): Boolean = runCatching {
        Files.newInputStream(path).use { input ->
            val header = ByteArray(12)
            input.read(header) == header.size &&
                header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE"
        }
    }.getOrDefault(false)

    private fun deviceFormat(audio: AudioBuffer) = AudioFormat(audio.format.sampleRate.toFloat(), 16, audio.format.channels, true, false)

    private fun pcm16(audio: AudioBuffer, start: Int, frames: Int, volume: Double): ByteArray {
        val bytes = ByteBuffer.allocate(frames * audio.format.channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { offset ->
            repeat(audio.format.channels) { channel ->
                bytes.putShort((audio.getSample(channel, start + offset) * volume.toFloat()).coerceIn(-1f, 1f).times(32767f).toInt().toShort())
            }
        }
        return bytes.array()
    }

    private fun closeQuietly(output: AudioOutputLine) = runCatching { output.stop(); output.flush(); output.close() }

    private class JavaxAudioOutputLine(private val line: SourceDataLine) : AudioOutputLine {
        override fun start() = line.start()
        override fun stop() = line.stop()
        override fun flush() = line.flush()
        override fun close() = line.close()
        override fun write(bytes: ByteArray, offset: Int, length: Int): Int = line.write(bytes, offset, length)
    }

    private companion object {
        const val CHUNK_FRAMES = 1024
    }
}
