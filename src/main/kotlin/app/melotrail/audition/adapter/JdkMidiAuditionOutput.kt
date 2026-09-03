package app.melotrail.audition.adapter

import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionOutput
import app.melotrail.audition.MidiAuditionOutputDevice
import app.melotrail.audition.MidiAuditionOutputException
import app.melotrail.audition.MidiAuditionOutputListener
import app.melotrail.audition.MidiAuditionOutputSession
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionProblemCode
import app.melotrail.midi.adapter.JdkMidiWriter
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportSong
import java.util.concurrent.CopyOnWriteArraySet
import javax.sound.midi.MetaEventListener
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Transmitter

/** JVM adapter that routes an in-memory target MIDI sequence through a local MIDI receiver. */
class JdkMidiAuditionOutput(
    private val writer: JdkMidiWriter = JdkMidiWriter(),
    private val sequencerFactory: () -> Sequencer = { MidiSystem.getSequencer(false) },
    private val defaultDeviceFactory: () -> MidiDevice = { MidiSystem.getSynthesizer() },
    private val defaultReceiverFactory: (() -> Receiver)? = null,
) : MidiAuditionOutput {
    private val sessions = CopyOnWriteArraySet<JdkMidiAuditionOutputSession>()

    /** Return receiver-capable local MIDI devices without opening them. */
    override fun availableDevices(): List<MidiAuditionOutputDevice> = deviceDescriptors()
        .map { it.public }
        .distinctBy(MidiAuditionOutputDevice::id)
        .sortedWith(compareBy(MidiAuditionOutputDevice::name, MidiAuditionOutputDevice::vendor, MidiAuditionOutputDevice::id))

    override fun open(plan: MidiAuditionPlaybackPlan, listener: MidiAuditionOutputListener): MidiAuditionOutputSession {
        val song = auditionSong(plan)
        val sequence = try {
            writer.toSequence(song)
        } catch (error: Exception) {
            throw MidiAuditionOutputException(
                MidiAuditionProblemCode.OUTPUT_FAILURE,
                "The MIDI audition sequence could not be prepared.",
                error,
            )
        }
        var sequencer: Sequencer? = null
        var transmitter: Transmitter? = null
        var endpoint: JdkMidiOutputEndpoint? = null
        try {
            sequencer = sequencerFactory()
            sequencer.open()
            endpoint = openEndpoint(plan.outputDeviceId)
            transmitter = sequencer.transmitter
            transmitter.receiver = endpoint.receiver
            sequencer.sequence = sequence
            val session = JdkMidiAuditionOutputSession(
                sequencer = sequencer,
                transmitter = transmitter,
                endpoint = endpoint,
                plan = plan,
                listener = listener,
                onClosed = { closed -> sessions.remove(closed) },
            )
            sessions += session
            return session
        } catch (error: Exception) {
            closeQuietly(transmitter)
            closeQuietly(endpoint)
            closeQuietly(sequencer)
            throw outputException(error, "The local MIDI output could not be opened.")
        }
    }

    override fun close() {
        sessions.toList().forEach { it.close() }
        sessions.clear()
    }

    private fun openEndpoint(deviceId: String?): JdkMidiOutputEndpoint {
        if (deviceId == null) {
            defaultReceiverFactory?.let { receiverFactory ->
                return try {
                    JdkMidiOutputEndpoint(receiverFactory(), null)
                } catch (error: Exception) {
                    throw MidiAuditionOutputException(
                        MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                        "The built-in MIDI preview could not be opened.",
                        error,
                    )
                }
            }
            val device = try {
                defaultDeviceFactory()
            } catch (error: Exception) {
                throw MidiAuditionOutputException(
                    MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                    "The built-in MIDI synthesizer is unavailable.",
                    error,
                )
            }
            try {
                device.open()
            } catch (error: Exception) {
                closeQuietly(device)
                throw MidiAuditionOutputException(
                    MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                    "The built-in MIDI synthesizer is unavailable.",
                    error,
                )
            }
            return try {
                JdkMidiOutputEndpoint(device.receiver, device)
            } catch (error: Exception) {
                closeQuietly(device)
                throw MidiAuditionOutputException(
                    MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                    "The built-in MIDI synthesizer receiver could not be opened.",
                    error,
                )
            }
        }
        val descriptor = deviceDescriptors().singleOrNull { it.public.id == deviceId }
            ?: throw MidiAuditionOutputException(
                MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                "The selected MIDI output device is unavailable.",
            )
        val device = try {
            MidiSystem.getMidiDevice(descriptor.info).also(MidiDevice::open)
        } catch (error: Exception) {
            throw MidiAuditionOutputException(
                MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                "The selected MIDI output device could not be opened.",
                error,
            )
        }
        return try {
            JdkMidiOutputEndpoint(device.receiver, device)
        } catch (error: Exception) {
            closeQuietly(device)
            throw MidiAuditionOutputException(
                MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                "The selected MIDI output receiver could not be opened.",
                error,
            )
        }
    }

    private fun deviceDescriptors(): List<DeviceDescriptor> = MidiSystem.getMidiDeviceInfo().mapNotNull { info ->
        runCatching {
            val device = MidiSystem.getMidiDevice(info)
            if (device.maxReceivers == 0) null else DeviceDescriptor(info, device, publicDevice(info))
        }.getOrNull()
    }

    private fun publicDevice(info: MidiDevice.Info): MidiAuditionOutputDevice {
        val id = listOf(info.name, info.vendor, info.description, info.version).joinToString("\u001f")
        return MidiAuditionOutputDevice(id, info.name, info.vendor, info.description, info.version)
    }

    /** Clip only the transient audition sequence so occurrence playback cannot run past its view window. */
    private fun auditionSong(plan: MidiAuditionPlaybackPlan): MidiExportSong {
        val window = plan.view.window
        val roles = plan.view.song.roles.filter { it.role in plan.view.roles }.map { track ->
            track.copy(
                events = track.events.mapNotNull { event ->
                    when (event) {
                        is MidiNoteEvent -> {
                            if (event.endTick <= window.startTick || event.orderingKey.tick >= window.endTick) {
                                null
                            } else {
                                event.copy(
                                    orderingKey = event.orderingKey.copy(tick = maxOf(event.orderingKey.tick, window.startTick)),
                                    endTick = minOf(event.endTick, window.endTick),
                                )
                            }
                        }
                        else -> event.takeIf { it.orderingKey.tick in window.startTick until window.endTick }
                    }
                }.sortedBy { it.orderingKey },
            )
        }
        return plan.view.song.copy(
            markers = plan.view.song.markers.filter { it.tick <= window.endTick },
            roles = roles,
            songEndTick = window.endTick,
        )
    }

    private data class DeviceDescriptor(val info: MidiDevice.Info, val device: MidiDevice, val public: MidiAuditionOutputDevice)

    private companion object {
        fun outputException(error: Exception, fallback: String): MidiAuditionOutputException =
            if (error is MidiAuditionOutputException) error else MidiAuditionOutputException(
                MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                error.message ?: fallback,
                error,
            )

        fun closeQuietly(resource: AutoCloseable?) {
            try {
                resource?.close()
            } catch (_: Exception) {
                // Cleanup is best effort after an unavailable or lost local device.
            }
        }
    }
}

private class JdkMidiOutputEndpoint(
    val receiver: Receiver,
    private val device: MidiDevice?,
) : AutoCloseable {
    override fun close() {
        try {
            receiver.close()
        } finally {
            try {
                device?.close()
            } catch (_: Exception) {
                // A lost device is already unavailable; keep teardown idempotent.
            }
        }
    }
}

private class JdkMidiAuditionOutputSession(
    private val sequencer: Sequencer,
    private val transmitter: Transmitter,
    private val endpoint: JdkMidiOutputEndpoint,
    private val plan: MidiAuditionPlaybackPlan,
    private val listener: MidiAuditionOutputListener,
    private val onClosed: (JdkMidiAuditionOutputSession) -> Unit,
) : MidiAuditionOutputSession {
    private val roleTracks = plan.view.song.roles.mapIndexed { index, track -> track.role to index + 1 }.toMap()
    private var closed = false
    private var endReported = false
    private val metaListener = MetaEventListener { message ->
        if (message.type == END_OF_TRACK) reportEnd()
    }

    init {
        sequencer.addMetaEventListener(metaListener)
        applyLoop(plan.loop)
        applyMutedRoles(plan.mutedRoles)
        applySoloRoles(plan.soloRoles)
        sequencer.tickPosition = plan.startTick
    }

    override fun play() = withOutput("MIDI audition playback could not start") { sequencer.start() }

    override fun pause() = withOutput("MIDI audition playback could not pause") { sequencer.stop() }

    override fun stop() = withOutput("MIDI audition playback could not stop") {
        try {
            sequencer.stop()
        } finally {
            allNotesOff()
        }
    }

    override fun positionTick(): Long = withOutput("MIDI audition position could not be read") { sequencer.tickPosition }

    override fun seek(tick: Long) = withOutput("MIDI audition seek failed") {
        require(tick in plan.view.window.startTick..plan.view.window.endTick) {
            "Seek position must remain inside the selected audition view"
        }
        val wasRunning = sequencer.isRunning
        if (wasRunning) {
            sequencer.stop()
            allNotesOff()
        }
        sequencer.tickPosition = tick
        if (wasRunning) sequencer.start()
    }

    override fun setLoop(loop: MidiAuditionLoop?) = withOutput("MIDI audition loop update failed") {
        loop?.let { candidate ->
            require(candidate.startTick >= plan.view.window.startTick && candidate.endTick <= plan.view.window.endTick) {
                "Audition loop must remain inside the selected view window"
            }
        }
        rebuildTransport { applyLoop(loop) }
    }

    override fun setMutedRoles(roles: Set<MidiExportRole>) = withOutput("MIDI audition mute update failed") {
        require(roles.all(plan.view.roles::contains)) { "Muted role is not present in the selected audition view" }
        rebuildTransport { applyMutedRoles(roles) }
    }

    override fun setSoloRoles(roles: Set<MidiExportRole>) = withOutput("MIDI audition solo update failed") {
        require(roles.all(plan.view.roles::contains)) { "Solo role is not present in the selected audition view" }
        rebuildTransport { applySoloRoles(roles) }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            try {
                sequencer.stop()
            } finally {
                allNotesOff()
            }
        } catch (_: Exception) {
            // Device loss must not prevent the remaining cleanup steps.
        } finally {
            try {
                sequencer.removeMetaEventListener(metaListener)
            } catch (_: Exception) {
                // The sequencer may already be unavailable.
            }
            closeQuietly(transmitter)
            closeQuietly(endpoint)
            closeQuietly(sequencer)
            onClosed(this)
        }
    }

    private fun applyLoop(loop: MidiAuditionLoop?) {
        val window = loop ?: plan.view.window.let { MidiAuditionLoop(it.startTick, it.endTick) }
        sequencer.loopStartPoint = window.startTick
        sequencer.loopEndPoint = window.endTick
        sequencer.loopCount = if (loop == null) 0 else Sequencer.LOOP_CONTINUOUSLY
    }

    private fun applyMutedRoles(roles: Set<MidiExportRole>) {
        roleTracks.forEach { (role, trackIndex) -> sequencer.setTrackMute(trackIndex, role in roles) }
    }

    private fun applySoloRoles(roles: Set<MidiExportRole>) {
        roleTracks.forEach { (role, trackIndex) -> sequencer.setTrackSolo(trackIndex, role in roles) }
    }

    /** Rebuild a live routing mask from one tick boundary so previously sounding notes cannot leak through. */
    private fun rebuildTransport(update: () -> Unit) {
        val wasRunning = sequencer.isRunning
        if (wasRunning) {
            sequencer.stop()
            allNotesOff()
        }
        update()
        if (wasRunning) sequencer.start()
    }

    private fun allNotesOff() {
        for (channel in 0..15) {
            for (controller in listOf(123, 120)) {
                try {
                    endpoint.receiver.send(ShortMessage(ShortMessage.CONTROL_CHANGE, channel, controller, 0), -1L)
                } catch (_: Exception) {
                    // The device may have disappeared; continue attempting all channels and controllers.
                }
            }
        }
    }

    private fun reportEnd() {
        if (!closed && !endReported) {
            endReported = true
            listener.onPlaybackEnded()
        }
    }

    private inline fun <T> withOutput(message: String, operation: () -> T): T {
        if (closed) throw MidiAuditionOutputException(MidiAuditionProblemCode.CLOSED, "MIDI audition output session is closed")
        try {
            return operation()
        } catch (error: MidiAuditionOutputException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw MidiAuditionOutputException(MidiAuditionProblemCode.DEVICE_LOST, error.message ?: message, error)
        }
    }

    private fun closeQuietly(resource: AutoCloseable?) {
        try {
            resource?.close()
        } catch (_: Exception) {
            // Cleanup is best effort after an unavailable or lost local device.
        }
    }

    private companion object {
        const val END_OF_TRACK = 0x2f
    }
}
