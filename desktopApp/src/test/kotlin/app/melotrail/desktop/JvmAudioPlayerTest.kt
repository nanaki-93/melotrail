package app.melotrail.desktop

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.audio.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat as JavaxAudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmAudioPlayerTest {
    @Test
    fun `prepare and start failures are typed and retain prepared source for retry`() = runBlocking {
        val device = FakeDevice(failOpen = true)
        val player = player(device = device)

        val prepared = player.prepare(tempArtifact())
        assertIs<PlaybackPrepareResult.Ready>(prepared)
        val failed = assertIs<PlaybackStartResult.Failed>(player.start())
        assertEquals(PlaybackFailureStage.DEVICE_OPEN, failed.failure.stage)
        assertEquals(PlaybackState.STOPPED, player.state.value)

        device.failOpen = false
        device.failStart = true
        val startFailure = assertIs<PlaybackStartResult.Failed>(player.start())
        assertEquals(PlaybackFailureStage.DEVICE_START, startFailure.failure.stage)
        assertTrue(device.lines.single().closed)
        device.failStart = false
        assertEquals(PlaybackStartResult.Started, player.start())
        player.close()
    }

    @Test
    fun `decode and device open run on the playback dispatcher rather than caller dispatcher`() = runBlocking {
        val ui = Executors.newSingleThreadExecutor { Thread(it, "ui-dispatcher") }.asCoroutineDispatcher()
        val calls = mutableListOf<String>()
        val device = FakeDevice(onOpen = { calls += "open:${Thread.currentThread().name}" })
        val player = JvmAudioPlayer(
            decoder = AudioArtifactDecoder { calls += "decode:${Thread.currentThread().name}"; audio() },
            outputDevice = device,
            workDispatcher = Dispatchers.Default
        )
        try {
            withContext(ui) { assertEquals(PlaybackStartResult.Started, player.play(tempArtifact())) }
            assertTrue(calls.all { !it.endsWith("ui-dispatcher") }, calls.toString())
        } finally {
            player.close()
            ui.close()
        }
    }

    @Test
    fun `pause resume seek and EOF preserve one worker and bounded position`() = runBlocking {
        val device = FakeDevice(writeDelayMillis = 3)
        val player = player(device = device, frames = 8_192)
        player.prepare(tempArtifact())
        assertEquals(PlaybackStartResult.Started, player.start())
        waitUntil { device.lines.singleOrNull()?.writes?.get() ?: 0 > 0 }

        player.pause()
        assertEquals(PlaybackState.PAUSED, player.state.value)
        player.resume()
        assertEquals(PlaybackState.PLAYING, player.state.value)
        player.seek(-10.0)
        assertEquals(0.0, player.currentPosition.value)
        player.seek(999.0)
        assertEquals(player.totalDuration.value, player.currentPosition.value)
        waitUntil { player.state.value == PlaybackState.STOPPED }
        assertTrue(device.maxActive.get() <= 1)
        player.close()
    }

    @Test
    fun `source switch close volume and repeated play keep one line and worker`() = runBlocking {
        val device = FakeDevice(writeDelayMillis = 5)
        val player = player(device = device, frames = 8_192)
        player.setVolume(2.0)
        assertEquals(1.0, player.volume.value)
        player.setVolume(-1.0)
        assertEquals(0.0, player.volume.value)

        assertEquals(PlaybackStartResult.Started, player.play(tempArtifact()))
        waitUntil { device.lines.isNotEmpty() && device.lines.first().writes.get() > 0 }
        assertEquals(PlaybackStartResult.Started, player.play(tempArtifact()))
        player.close()

        assertEquals(PlaybackState.STOPPED, player.state.value)
        assertEquals(0, device.active.get())
        assertTrue(device.lines.all { it.closed })
        assertTrue(device.maxActive.get() <= 1)
    }

    @Test
    fun `invalid audio is rejected during prepare`() = runBlocking {
        val invalid = AudioBuffer(floatArrayOf(Float.NaN), AudioFormat(44_100, 1, 16, false, false, "WAV"), 0.1)
        val player = JvmAudioPlayer(AudioArtifactDecoder { invalid }, FakeDevice(), Dispatchers.Default)
        val result = assertIs<PlaybackPrepareResult.Failed>(player.prepare(tempArtifact()))
        assertEquals(PlaybackFailureStage.PREPARE, result.failure.stage)
        player.close()
    }

    @Test
    fun `container validation and EOF replay keep playback on one output line`() = runBlocking {
        val malformed = java.nio.file.Files.createTempFile("jvm-audio-player-invalid", ".wav")
        java.nio.file.Files.writeString(malformed, "not a wave")
        val device = FakeDevice(writeDelayMillis = 1)
        val player = player(device = device, frames = 1_024)
        try {
            val invalid = assertIs<PlaybackPrepareResult.Failed>(player.prepare(malformed))
            assertEquals(PlaybackFailureStage.DECODE, invalid.failure.stage)

            assertEquals(PlaybackStartResult.Started, player.play(tempArtifact()))
            waitUntil { player.state.value == PlaybackState.STOPPED }
            assertEquals(PlaybackStartResult.Started, player.play(tempArtifact()))
            waitUntil { player.state.value == PlaybackState.STOPPED && device.lines.size >= 2 && device.lines[1].writes.get() > 0 }
            assertTrue(device.maxActive.get() <= 1)
        } finally {
            player.close()
            java.nio.file.Files.deleteIfExists(malformed)
        }
    }

    @Test
    fun `runtime line failures remain typed after the worker stops`() = runBlocking {
        val device = FakeDevice(failWrite = true)
        val player = player(device = device)
        try {
            assertEquals(PlaybackStartResult.Started, player.play(tempArtifact()))
            waitUntil { player.state.value == PlaybackState.STOPPED }
            assertEquals(PlaybackFailureStage.RUNTIME, player.failure.value?.stage)
        } finally {
            player.close()
        }
    }

    private fun player(device: FakeDevice, frames: Int = 4_096): JvmAudioPlayer = JvmAudioPlayer(
        decoder = AudioArtifactDecoder { audio(frames) },
        outputDevice = device,
        workDispatcher = Dispatchers.Default
    )

    private fun audio(frames: Int = 4_096) = AudioBuffer(
        samples = FloatArray(frames) { 0.25f },
        format = AudioFormat(44_100, 1, 16, false, false, "WAV"),
        duration = frames / 44_100.0
    )

    private fun tempArtifact() = java.nio.file.Files.createTempFile("jvm-audio-player", ".wav").also {
        java.nio.file.Files.write(it, "RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(Charsets.US_ASCII))
        it.toFile().deleteOnExit()
    }

    private fun waitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition(), "condition was not met within $timeoutMillis ms")
    }

    private class FakeDevice(
        private val onOpen: () -> Unit = {},
        var failOpen: Boolean = false,
        var failStart: Boolean = false,
        var failWrite: Boolean = false,
        private val writeDelayMillis: Long = 0
    ) : AudioOutputDevice {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val lines = mutableListOf<FakeLine>()

        override fun open(format: JavaxAudioFormat): AudioOutputLine {
            onOpen()
            check(!failOpen) { "No output device" }
            return FakeLine(this, writeDelayMillis).also { synchronized(lines) { lines += it } }
        }
    }

    private class FakeLine(private val device: FakeDevice, private val writeDelayMillis: Long) : AudioOutputLine {
        val writes = AtomicInteger()
        @Volatile var closed = false
        @Volatile private var started = false

        override fun start() {
            check(!device.failStart) { "Line failed to start" }
            if (!started) {
                started = true
                val active = device.active.incrementAndGet()
                device.maxActive.accumulateAndGet(active, ::maxOf)
            }
        }

        override fun stop() = Unit
        override fun flush() = Unit
        override fun close() {
            if (!closed) {
                closed = true
                if (started) device.active.decrementAndGet()
            }
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int): Int {
            writes.incrementAndGet()
            check(!device.failWrite) { "Line failed during write" }
            if (writeDelayMillis > 0) Thread.sleep(writeDelayMillis)
            return length
        }
    }
}
