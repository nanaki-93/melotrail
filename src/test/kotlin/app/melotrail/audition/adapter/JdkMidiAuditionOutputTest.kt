package app.melotrail.audition.adapter

import app.melotrail.audition.MidiAuditionController
import app.melotrail.audition.MidiAuditionOutputException
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionView
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import javax.sound.midi.MidiUnavailableException
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.Sequence
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Transmitter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JdkMidiAuditionOutputTest {
    @Test
    fun `default preview opens and closes the managed audible synthesizer`() {
        val sequencer = RecordingSequencer()
        val receiver = RecordingReceiver()
        val synthesizer = RecordingMidiDevice(receiver)
        val output = JdkMidiAuditionOutput(
            sequencerFactory = { sequencer.instance },
            defaultDeviceFactory = { synthesizer.instance },
        )
        val controller = MidiAuditionController(output)

        assertIs<MidiAuditionResult.Applied>(
            controller.play(MidiAuditionPlaybackPlan(MidiAuditionView.sourceMelody(song()))),
        )
        assertTrue(synthesizer.opened)
        assertEquals(1, synthesizer.receiverRequests)

        assertIs<MidiAuditionResult.Applied>(controller.stop())
        controller.close()
        assertTrue(receiver.closed)
        assertTrue(synthesizer.closed)
    }

    @Test
    fun `maps an unavailable JVM sequencer to a recoverable device result`() {
        val output = JdkMidiAuditionOutput(
            sequencerFactory = { throw MidiUnavailableException("no local MIDI sequencer") },
        )
        val controller = MidiAuditionController(output)

        val result = assertIs<MidiAuditionResult.Failed>(
            controller.play(MidiAuditionPlaybackPlan(MidiAuditionView.sourceMelody(song()))),
        )

        assertEquals(app.melotrail.audition.MidiAuditionProblemCode.DEVICE_UNAVAILABLE, result.problem.code)
        assertEquals(MidiAuditionPlaybackState.STOPPED, result.state.playback)
    }

    @Test
    fun `closes a partially opened default synthesizer after failure`() {
        val synthesizer = RecordingMidiDevice(RecordingReceiver(), MidiUnavailableException("audio line unavailable"))
        val output = JdkMidiAuditionOutput(
            sequencerFactory = { RecordingSequencer().instance },
            defaultDeviceFactory = { synthesizer.instance },
        )

        val result = assertIs<MidiAuditionResult.Failed>(
            MidiAuditionController(output).play(MidiAuditionPlaybackPlan(MidiAuditionView.sourceMelody(song()))),
        )

        assertEquals(app.melotrail.audition.MidiAuditionProblemCode.DEVICE_UNAVAILABLE, result.problem.code)
        assertTrue(synthesizer.closed)
    }

    @Test
    fun `preserves an explicit output failure code from the JVM boundary`() {
        val output = JdkMidiAuditionOutput(
            sequencerFactory = { throw MidiAuditionOutputException(app.melotrail.audition.MidiAuditionProblemCode.DEVICE_LOST, "device lost") },
        )
        val controller = MidiAuditionController(output)

        val result = assertIs<MidiAuditionResult.Failed>(
            controller.play(MidiAuditionPlaybackPlan(MidiAuditionView.sourceMelody(song()))),
        )

        assertEquals(app.melotrail.audition.MidiAuditionProblemCode.DEVICE_LOST, result.problem.code)
    }

    @Test
    fun `routes authoritative tempo meter and safe live transport rebuilds through MIDI only`() {
        val sequencer = RecordingSequencer()
        val receiver = RecordingReceiver()
        val output = JdkMidiAuditionOutput(
            sequencerFactory = { sequencer.instance },
            defaultReceiverFactory = { receiver },
        )
        val controller = MidiAuditionController(output)
        val plan = MidiAuditionPlaybackPlan(MidiAuditionView.accepted(song()))

        assertIs<MidiAuditionResult.Applied>(controller.play(plan))
        val sequence = requireNotNull(sequencer.sequence)
        assertEquals(480, sequence.resolution)
        val conductor = sequence.tracks.first().toList().mapNotNull { it.message as? MetaMessage }
        assertTrue(conductor.any { it.type == 0x51 })
        assertTrue(conductor.any { it.type == 0x58 })

        assertIs<MidiAuditionResult.Applied>(controller.seek(240))
        assertIs<MidiAuditionResult.Applied>(controller.pause())
        assertEquals(240L, controller.state.positionTick)
        assertIs<MidiAuditionResult.Applied>(controller.play())
        assertIs<MidiAuditionResult.Applied>(controller.setLoop(MidiAuditionLoop(0, 240)))
        assertIs<MidiAuditionResult.Applied>(controller.setMutedRole(MidiExportRole.MELODY, true))
        assertIs<MidiAuditionResult.Applied>(controller.setSoloRole(MidiExportRole.MELODY, true))
        assertTrue(sequencer.startCalls >= 5)
        assertTrue(sequencer.stopCalls >= 4)
        assertTrue(receiver.controllerChanges.count { it.data1 == 123 } >= 16 * 3)
        assertTrue(receiver.controllerChanges.count { it.data1 == 120 } >= 16 * 3)

        assertIs<MidiAuditionResult.Applied>(controller.stop())
        controller.close()
        assertTrue(receiver.closed)
        assertTrue(sequencer.closed)
    }

    private fun song() = MidiExportSong(
        ppq = MidiPpq(480),
        sequenceName = "Unavailable device fixture",
        tempoMicrosecondsPerQuarter = 500_000,
        meterNumerator = 4,
        meterDenominatorExponent = 2,
        markers = listOf(MidiExportMarker(1, "Verse", 0)),
        roles = listOf(
            MidiExportRoleTrack(
                MidiExportRole.MELODY,
                listOf(MidiNoteEvent(MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE, generatedEventKey = 1), 480, 0, 60, 96)),
            ),
        ),
        songEndTick = 480,
    )
}

private class RecordingReceiver : Receiver {
    val controllerChanges = mutableListOf<ShortMessage>()
    var closed = false

    override fun send(message: MidiMessage, timeStamp: Long) {
        (message as? ShortMessage)?.takeIf { it.command == ShortMessage.CONTROL_CHANGE }?.let(controllerChanges::add)
    }

    override fun close() { closed = true }
}

private class RecordingMidiDevice(
    private val receiver: Receiver,
    private val openFailure: Exception? = null,
) : InvocationHandler {
    var opened = false
    var closed = false
    var receiverRequests = 0

    val instance: MidiDevice = Proxy.newProxyInstance(
        MidiDevice::class.java.classLoader,
        arrayOf(MidiDevice::class.java),
        this,
    ) as MidiDevice

    override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? = when (method.name) {
        "open" -> openFailure?.let { throw it } ?: Unit.also { opened = true }
        "close" -> Unit.also { closed = true; opened = false }
        "isOpen" -> opened
        "getReceiver" -> receiver.also { receiverRequests += 1 }
        "getReceivers" -> listOf(receiver)
        "getMaxReceivers" -> -1
        "getTransmitters" -> emptyList<Transmitter>()
        "getMaxTransmitters" -> 0
        "getMicrosecondPosition" -> -1L
        "toString" -> "RecordingMidiDevice"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
    }
}

private class RecordingSequencer : InvocationHandler {
    private val transmitter = RecordingTransmitter()
    var sequence: Sequence? = null
    var startCalls = 0
    var stopCalls = 0
    var closed = false
    private var running = false
    private var tickPosition = 0L

    val instance: Sequencer = Proxy.newProxyInstance(
        Sequencer::class.java.classLoader,
        arrayOf(Sequencer::class.java),
        this,
    ) as Sequencer

    override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? = when (method.name) {
        "open" -> Unit
        "close" -> Unit.also { closed = true; running = false }
        "getTransmitter" -> transmitter.instance
        "setSequence" -> Unit.also { sequence = args?.firstOrNull() as Sequence }
        "start" -> Unit.also { startCalls += 1; running = true }
        "stop" -> Unit.also { stopCalls += 1; running = false }
        "isRunning" -> running
        "isOpen" -> !closed
        "getTickPosition" -> tickPosition
        "setTickPosition" -> Unit.also { tickPosition = args?.firstOrNull() as Long }
        "setLoopStartPoint",
        "setLoopEndPoint",
        "setLoopCount",
        "setTrackMute",
        "setTrackSolo",
        "removeMetaEventListener",
        -> Unit
        "addMetaEventListener" -> true
        "toString" -> "RecordingSequencer"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> defaultValue(method.returnType)
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0F
        java.lang.Double.TYPE -> 0.0
        else -> null
    }
}

private class RecordingTransmitter : InvocationHandler {
    private var receiver: Receiver? = null
    val instance: Transmitter = Proxy.newProxyInstance(
        Transmitter::class.java.classLoader,
        arrayOf(Transmitter::class.java),
        this,
    ) as Transmitter

    override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? = when (method.name) {
        "setReceiver" -> Unit.also { receiver = args?.firstOrNull() as? Receiver }
        "getReceiver" -> receiver
        "close" -> Unit
        "toString" -> "RecordingTransmitter"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
    }
}

private fun javax.sound.midi.Track.toList() = (0 until size()).map(::get)
