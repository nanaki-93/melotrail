package app.melotrail.video.application

import app.melotrail.video.adapter.VideoMediaProcessCancellation
import app.melotrail.video.adapter.VideoPreviewDecoder
import app.melotrail.video.adapter.VideoPreviewOpen
import app.melotrail.video.adapter.VideoPreviewOpenResult
import app.melotrail.video.adapter.VideoPreviewPresentation
import app.melotrail.video.domain.VideoVersionedId
import java.math.BigInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/** Session owns the displayed image until it is replaced or the session closes. Consumers must
 * not close it; a previously observed image may become unreadable after a state transition. */
sealed interface VideoPreviewState {
    val sessionId: Long
    val takeId: VideoVersionedId?
    val frame: VideoPreviewDecoder.Frame?

    data class Empty(override val sessionId: Long = 0) : VideoPreviewState {
        override val takeId: VideoVersionedId? = null
        override val frame: VideoPreviewDecoder.Frame? = null
    }
    data class Opening(override val sessionId: Long, override val takeId: VideoVersionedId,
                       override val frame: VideoPreviewDecoder.Frame? = null) : VideoPreviewState
    data class Paused(override val sessionId: Long, override val takeId: VideoVersionedId,
                      override val frame: VideoPreviewDecoder.Frame) : VideoPreviewState
    data class Playing(override val sessionId: Long, override val takeId: VideoVersionedId,
                       override val frame: VideoPreviewDecoder.Frame) : VideoPreviewState
    data class Buffering(override val sessionId: Long, override val takeId: VideoVersionedId,
                         override val frame: VideoPreviewDecoder.Frame?, val requestedFrame: Long) : VideoPreviewState
    data class Ended(override val sessionId: Long, override val takeId: VideoVersionedId,
                     override val frame: VideoPreviewDecoder.Frame) : VideoPreviewState
    data class Failed(override val sessionId: Long, override val takeId: VideoVersionedId?,
                      override val frame: VideoPreviewDecoder.Frame?, val message: String,
                      val nextAction: String) : VideoPreviewState
    data class Closed(override val sessionId: Long, override val takeId: VideoVersionedId?) : VideoPreviewState {
        override val frame: VideoPreviewDecoder.Frame? = null
    }
}

/** A monotonic nanosecond counter (not wall-clock time), injected independently of scheduling. */
fun interface VideoPreviewClock { fun nowNanos(): Long }

/** One worker owns transport and pixel publication. Every public command only enqueues work.
 * The worker dispatcher must be suitable for blocking media operations, not the UI dispatcher.
 * Replacement/cancellation of an in-flight native operation is handled in the next slice. */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPreviewSession(
    private val decoder: VideoPreviewDecoder,
    worker: CoroutineDispatcher = Dispatchers.IO,
    private val clock: VideoPreviewClock = VideoPreviewClock(System::nanoTime),
) {
    private sealed interface Command {
        data class Open(val request: VideoPreviewOpen) : Command
        data object Play : Command
        data object Pause : Command
        data class Seek(val index: Long) : Command
        data class Step(val delta: Int) : Command
        data object Stop : Command
        data object Close : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val commands = Channel<Command>(16)
    private val mutableState = MutableStateFlow<VideoPreviewState>(VideoPreviewState.Empty())
    val state: StateFlow<VideoPreviewState> = mutableState
    private val gate = Any()
    private var closing = false
    private val workerJob = scope.launch { run() }

    private fun submit(command: Command) {
        synchronized(gate) {
            if (closing) return
            if (commands.trySend(command).isSuccess) return
            // Open supersedes all queued transport, while Stop needs the most recent queued
            // Open to establish its source. Never drop either in favour of stale transport.
            // The worker alone changes state; saturation cannot publish from the caller.
            if (command is Command.Open || command == Command.Stop) {
                var latestOpen: Command.Open? = null
                while (true) {
                    val pending = commands.tryReceive().getOrNull() ?: break
                    if (pending is Command.Open) latestOpen = pending
                }
                if (command == Command.Stop) latestOpen?.let { check(commands.trySend(it).isSuccess) }
                check(commands.trySend(command).isSuccess)
            } else {
                throw IllegalStateException("Preview command queue is full; wait for the worker before sending another transport command.")
            }
        }
    }

    fun open(request: VideoPreviewOpen) = submit(Command.Open(request))
    fun play() = submit(Command.Play)
    fun pause() = submit(Command.Pause)
    fun seek(frameIndex: Long) = submit(Command.Seek(frameIndex))
    fun step(delta: Int) = submit(Command.Step(delta))
    fun stop() = submit(Command.Stop)

    suspend fun closeAndJoin() {
        synchronized(gate) {
            if (!closing) {
                closing = true
                // Preserve the active worker's ownership until its operation completes.
                commands.trySend(Command.Close).also {
                    if (it.isFailure) { commands.tryReceive(); commands.trySend(Command.Close) }
                }
            }
        }
        workerJob.join()
    }

    private suspend fun run() {
        var admitted: VideoPreviewOpenResult.Admitted? = null
        var frame: VideoPreviewDecoder.Frame? = null
        var id = 0L
        var playing = false
        var anchorTime = 0L
        var anchorPts = BigInteger.ZERO
        val cancellation = VideoMediaProcessCancellation()

        fun publish(next: VideoPreviewState) {
            val previous = frame
            frame = next.frame
            mutableState.value = next
            if (previous !== frame) previous?.close()
        }
        fun fail(message: String, action: String) {
            playing = false
            publish(VideoPreviewState.Failed(id, admitted?.takeId ?: mutableState.value.takeId, frame, message.take(512), action))
        }
        fun decode(index: Long): Boolean {
            val source = admitted ?: return false
            publish(VideoPreviewState.Buffering(id, source.takeId, frame, index))
            return try {
                val result = decoder.extractWindow(source, index, 1, cancellation).single()
                publish(VideoPreviewState.Paused(id, source.takeId, result))
                true
            } catch (error: Exception) {
                fail(error.message ?: "Preview frame could not be decoded.", "Check the persisted take and pinned media tools, then reopen it.")
                false
            }
        }
        fun remainingNanos(next: VideoPreviewPresentation): Long {
            if (frame == null) return 0L
            val pts = next.relativePts.subtract(anchorPts)
            val scale = BigInteger.valueOf(next.timeBase.numerator).multiply(BigInteger.valueOf(1_000_000_000L))
            val duration = pts.multiply(scale).divide(BigInteger.valueOf(next.timeBase.denominator))
            // nanoTime has an arbitrary (possibly negative) origin. Compare durations,
            // not absolute deadlines, without overflowing a signed Long at either end.
            val elapsed = BigInteger.valueOf(clock.nowNanos()).subtract(BigInteger.valueOf(anchorTime))
            return duration.subtract(elapsed).coerceIn(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE / 2)).toLong()
        }
        try {
            while (true) {
                val source = admitted
                val current = frame
                val nextIndex = if (playing && source != null && current != null && current.presentation.frameIndex < source.measurement.decodedFrameCount - 1)
                    current.presentation.frameIndex + 1 else null
                // The next presentation is looked up only on the worker, and only when playing.
                val nextTime = if (nextIndex != null) try { decoder.presentationAt(source!!, nextIndex, cancellation) }
                    catch (error: Exception) {
                        fail(error.message ?: "Preview timing is invalid.", "Reimport a measured silent take.")
                        null
                    } else null
                val remaining = if (nextTime != null) remainingNanos(nextTime) else 0L
                val waitMs = remaining / 1_000_000 + if (remaining % 1_000_000 == 0L) 0L else 1L
                val command = if (nextTime == null) commands.receive() else select<Command?> {
                    commands.onReceive { it }
                    onTimeout(waitMs.coerceAtMost(Long.MAX_VALUE / 2)) { null }
                }
                if (command == null) {
                    if (nextIndex != null && decode(nextIndex)) {
                        val loaded = frame!!
                        if (loaded.presentation != nextTime) {
                            fail("Decoded frame timing differs from measured presentation.", "Reimport the changed take.")
                        } else if (nextIndex == source!!.measurement.decodedFrameCount - 1) {
                            playing = false
                            publish(VideoPreviewState.Ended(id, source.takeId, loaded))
                        } else publish(VideoPreviewState.Playing(id, source!!.takeId, loaded))
                    }
                    continue
                }
                when (command) {
                    is Command.Open -> {
                        playing = false
                        admitted = null
                        id++
                        publish(VideoPreviewState.Opening(id, command.request.takeId))
                        try {
                            when (val opened = decoder.open(command.request, cancellation)) {
                                is VideoPreviewOpenResult.Rejected -> fail(opened.message, opened.nextAction)
                                is VideoPreviewOpenResult.Admitted -> {
                                    admitted = opened
                                    decode(0)
                                }
                            }
                        } catch (error: Exception) { fail(error.message ?: "Preview open failed.", "Check the take and media setup, then reopen.") }
                    }
                    Command.Play -> {
                        if (source == null || current == null) fail("Open a decoded take before playing.", "Open a persisted silent take first.")
                        else if (current.presentation.frameIndex == source.measurement.decodedFrameCount - 1) {
                            playing = false
                            publish(VideoPreviewState.Ended(id, source.takeId, current))
                        } else {
                            playing = true
                            anchorTime = clock.nowNanos()
                            anchorPts = current.presentation.relativePts
                            publish(VideoPreviewState.Playing(id, source.takeId, current))
                        }
                    }
                    Command.Pause -> {
                        playing = false
                        if (source == null || current == null) fail("Nothing is available to pause.", "Open a persisted silent take first.")
                        else publish(VideoPreviewState.Paused(id, source.takeId, current))
                    }
                    is Command.Seek -> {
                        playing = false
                        if (source == null || command.index < 0 || command.index >= source.measurement.decodedFrameCount)
                            fail("Frame ${command.index} is outside the opened take.", "Choose a frame within the measured take.")
                        else if (current?.presentation?.frameIndex == command.index) publish(VideoPreviewState.Paused(id, source.takeId, current))
                        else decode(command.index)
                    }
                    is Command.Step -> {
                        playing = false
                        if (command.delta !in listOf(-1, 1) || source == null || current == null)
                            fail("Step requires an opened frame and a direction of -1 or +1.", "Open a take and choose one frame in either direction.")
                        else {
                            val target = (current.presentation.frameIndex + command.delta).coerceIn(0, source.measurement.decodedFrameCount - 1)
                            if (target == current.presentation.frameIndex) publish(VideoPreviewState.Paused(id, source.takeId, current))
                            else decode(target)
                        }
                    }
                    Command.Stop -> {
                        playing = false
                        if (source == null) fail("Nothing is available to stop.", "Open a persisted silent take first.")
                        else if (current?.presentation?.frameIndex == 0L) publish(VideoPreviewState.Paused(id, source.takeId, current))
                        else decode(0)
                    }
                    Command.Close -> {
                        publish(VideoPreviewState.Closed(id, mutableState.value.takeId))
                        return
                    }
                }
            }
        } finally {
            frame?.close()
            commands.close()
        }
    }
}
