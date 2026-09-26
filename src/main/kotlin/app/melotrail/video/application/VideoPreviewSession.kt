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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
 * In-flight native work is cancelled through its owned process token. */
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
    private data class Pending(val command: Command, val generation: Long, val openGeneration: Long)
    private val commands = Channel<Pending>(16)
    private val mutableState = MutableStateFlow<VideoPreviewState>(VideoPreviewState.Empty())
    val state: StateFlow<VideoPreviewState> = mutableState
    private val gate = Any()
    private var closing = false
    private var generation = 0L
    private var openGeneration = 0L
    private var activeCancellation: VideoMediaProcessCancellation? = null
    private var admittingOpen = false
    private val workerJob = scope.launch { run() }

    private fun submit(command: Command) {
        synchronized(gate) {
            if (closing) return
            val queued = mutableListOf<Pending>()
            while (true) queued += commands.tryReceive().getOrNull() ?: break
            when (command) {
                is Command.Open -> queued.clear()
                is Command.Seek -> queued.removeAll { it.command is Command.Seek }
                Command.Stop -> {
                    val lastOpen = queued.lastOrNull { it.command is Command.Open }
                    queued.clear()
                    if (lastOpen != null) queued += lastOpen
                }
                else -> Unit
            }
            if (queued.size == 16) {
                // Requeue exactly the original bounded work on rejection.
                queued.forEach { check(commands.trySend(it).isSuccess) }
                throw IllegalStateException("Preview command queue is full; wait for the worker before sending another transport command.")
            }
            if (command is Command.Open) openGeneration++
            if (command != Command.Play) {
                generation++
                // A seek arriving while admission is still resolving must wait for that
                // verified source, not cancel it and then attempt to seek an empty session.
                if (command !is Command.Seek || !admittingOpen) activeCancellation?.cancel()
            }
            queued.forEach { check(commands.trySend(it).isSuccess) }
            check(commands.trySend(Pending(command, generation, openGeneration)).isSuccess)
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
                generation++
                openGeneration++
                activeCancellation?.cancel()
                while (commands.tryReceive().isSuccess) { /* discard obsolete commands */ }
                check(commands.trySend(Pending(Command.Close, generation, openGeneration)).isSuccess)
            }
        }
        withContext(NonCancellable) {
            workerJob.join()
            // A failed native teardown is not a clean close, even when the worker stopped.
            decoder.requireConfirmedTeardown()
        }
    }

    private suspend fun run() {
        var admitted: VideoPreviewOpenResult.Admitted? = null
        var frame: VideoPreviewDecoder.Frame? = null
        var id = 0L
        var playing = false
        var anchorTime = 0L
        var anchorPts = BigInteger.ZERO
        var operationGeneration = 0L
        var cancellation = VideoMediaProcessCancellation()
        fun current(): Boolean = synchronized(gate) { !closing && operationGeneration == generation }
        fun begin() {
            synchronized(gate) {
                operationGeneration = generation
                cancellation = VideoMediaProcessCancellation()
                activeCancellation = cancellation
                if (closing) cancellation.cancel()
            }
        }

        fun publish(next: VideoPreviewState, terminal: Boolean = false): Boolean = synchronized(gate) {
            if (!terminal && (closing || operationGeneration != generation)) {
                if (next.frame !== frame) next.frame?.close()
                return@synchronized false
            }
            val previous = frame
            frame = next.frame
            mutableState.value = next
            if (previous !== frame) previous?.close()
            true
        }
        fun fail(message: String, action: String) {
            playing = false
            publish(VideoPreviewState.Failed(id, admitted?.takeId ?: mutableState.value.takeId, frame, message.take(512), action))
        }
        // A seek changes the request generation but cannot turn a rejected admission into
        // an invalid-frame error. A replacement open or close still owns the newer source.
        fun failAdmission(pending: Pending, message: String, action: String) {
            playing = false
            synchronized(gate) {
                if (!closing && pending.openGeneration == openGeneration) {
                    publish(VideoPreviewState.Failed(id, mutableState.value.takeId, null, message.take(512), action), terminal = true)
                }
            }
        }
        fun decode(index: Long): Boolean {
            val source = admitted ?: return false
            if (!publish(VideoPreviewState.Buffering(id, source.takeId, frame, index))) return false
            return try {
                val result = decoder.extractWindow(source, index, 1, cancellation).single()
                publish(VideoPreviewState.Paused(id, source.takeId, result))
            } catch (error: Exception) {
                if (current()) fail(error.message ?: "Preview frame could not be decoded.", "Check the persisted take and pinned media tools, then reopen it.")
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
                // Consume queued transport before any more native timing work. The poll and
                // token reset share the submit gate: a command cannot cancel an old token
                // only to have playback install a fresh uncancelled one behind its back.
                var pending = synchronized(gate) {
                    val queued = commands.tryReceive().getOrNull()
                    if (queued == null && playing) begin()
                    queued
                }
                val source = admitted
                val current = frame
                val nextIndex = if (pending == null && playing && source != null && current != null && current.presentation.frameIndex < source.measurement.decodedFrameCount - 1)
                    current.presentation.frameIndex + 1 else null
                // The next presentation is looked up only on the worker, and only when playing.
                val nextTime = if (nextIndex != null) try {
                    decoder.presentationAt(source!!, nextIndex, cancellation).takeIf { current() }
                } catch (error: Exception) {
                    if (current()) fail(error.message ?: "Preview timing is invalid.", "Reimport a measured silent take.")
                    null
                } else null
                val remaining = if (nextTime != null) remainingNanos(nextTime) else 0L
                val waitMs = remaining / 1_000_000 + if (remaining % 1_000_000 == 0L) 0L else 1L
                if (pending == null) pending = if (nextTime == null) commands.receive() else select<Pending?> {
                    commands.onReceive { it }
                    onTimeout(waitMs.coerceAtMost(Long.MAX_VALUE / 2)) { null }
                }
                if (pending == null) {
                    // Timeout can race with a transport command; do not decode on a new
                    // token until the queued command has had its turn.
                    pending = synchronized(gate) {
                        val queued = commands.tryReceive().getOrNull()
                        if (queued == null) begin()
                        queued
                    }
                }
                if (pending == null) {
                    if (!current()) continue
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
                val command = pending.command
                if (command is Command.Open && pending.openGeneration != synchronized(gate) { openGeneration }) continue
                if (command is Command.Seek && pending.generation != synchronized(gate) { generation }) continue
                synchronized(gate) {
                    if (command is Command.Open) admittingOpen = true
                    begin()
                }
                // A seek queued before admission must not discard its still-current open.
                // Replacement opens and close, unlike seeks, supersede that source entirely.
                if (command is Command.Open && pending.openGeneration != synchronized(gate) { openGeneration }) {
                    synchronized(gate) { admittingOpen = false }
                    continue
                }
                if (command !is Command.Open && command != Command.Close && !current()) continue
                when (command) {
                    is Command.Open -> {
                        playing = false
                        admitted = null
                        id++
                        publish(VideoPreviewState.Opening(id, command.request.takeId))
                        try {
                            val opened = try { decoder.open(command.request, cancellation) }
                                finally { synchronized(gate) { admittingOpen = false } }
                            when (opened) {
                                is VideoPreviewOpenResult.Rejected -> failAdmission(pending, opened.message, opened.nextAction)
                                is VideoPreviewOpenResult.Admitted -> if (synchronized(gate) { !closing && pending.openGeneration == openGeneration }) {
                                    admitted = opened
                                    // A queued seek replaces the initial frame-zero extraction.
                                    if (pending.generation == synchronized(gate) { generation }) decode(0)
                                }
                            }
                        } catch (error: Exception) { failAdmission(pending, error.message ?: "Preview open failed.", "Check the take and media setup, then reopen.") }
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
                        if (source == null && mutableState.value is VideoPreviewState.Failed) continue // admission failure is authoritative
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
                        try {
                            decoder.requireConfirmedTeardown()
                            publish(VideoPreviewState.Closed(id, mutableState.value.takeId), terminal = true)
                        } catch (error: Exception) {
                            playing = false
                            publish(VideoPreviewState.Failed(id, mutableState.value.takeId, null,
                                (error.message ?: "Preview teardown is unconfirmed.").take(512),
                                "Inspect the owned preview process and private scratch child before retrying."), terminal = true)
                        }
                        return
                    }
                }
            }
        } finally {
            frame?.close()
            synchronized(gate) { activeCancellation = null }
            commands.close()
        }
    }
}
