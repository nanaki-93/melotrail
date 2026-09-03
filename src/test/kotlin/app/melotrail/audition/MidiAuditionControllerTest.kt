package app.melotrail.audition

import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiAuditionControllerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `controls play pause seek loop mute solo and deterministic cleanup`() {
        val output = FakeOutput()
        val controller = MidiAuditionController(output)
        val plan = MidiAuditionPlaybackPlan(
            MidiAuditionView.accepted(song()),
            startTick = 120,
            loop = MidiAuditionLoop(240, 720),
        )

        assertIs<MidiAuditionResult.Applied>(controller.play(plan))
        val session = output.sessions.single()
        assertEquals(MidiAuditionPlaybackState.PLAYING, controller.state.playback)
        assertEquals(1L, controller.state.sessionId)
        assertEquals(plan, session.plan)

        session.playbackTick = 180
        assertIs<MidiAuditionResult.Applied>(controller.pause())
        assertEquals(MidiAuditionPlaybackState.PAUSED, controller.state.playback)
        assertEquals(180L, controller.state.positionTick)
        assertIs<MidiAuditionResult.Applied>(controller.seek(360))
        assertEquals(360L, controller.state.positionTick)
        assertIs<MidiAuditionResult.Applied>(controller.setLoop(null))
        assertIs<MidiAuditionResult.Applied>(controller.setMutedRole(MidiExportRole.BASS, true))
        assertIs<MidiAuditionResult.Applied>(controller.setSoloRole(MidiExportRole.CHORDS, true))
        assertEquals(setOf(MidiExportRole.BASS), controller.state.mutedRoles)
        assertEquals(setOf(MidiExportRole.CHORDS), controller.state.soloRoles)
        assertTrue(session.operations.containsAll(listOf("pause", "seek:360", "loop:none", "mute:BASS", "solo:CHORDS")))

        assertIs<MidiAuditionResult.Applied>(controller.play())
        assertEquals(MidiAuditionPlaybackState.PLAYING, controller.state.playback)
        assertIs<MidiAuditionResult.Applied>(controller.stop())
        assertEquals(MidiAuditionPlaybackState.STOPPED, controller.state.playback)
        assertEquals(0L, controller.state.positionTick)
        assertTrue(session.closed)
        assertTrue(session.allNotesOffSent)
        assertTrue(controller.stateHistory.map { it.playback }.containsAll(listOf(
            MidiAuditionPlaybackState.PLAYING,
            MidiAuditionPlaybackState.PAUSED,
            MidiAuditionPlaybackState.STOPPED,
        )))
    }

    @Test
    fun `selects every supported scope and rejects seek and loop boundary violations`() {
        val full = song()
        assertEquals(listOf(MidiExportRole.MELODY), MidiAuditionView.sourceMelody(full).roles)
        assertEquals(listOf(MidiExportRole.CHORDS), MidiAuditionView.candidate("candidate-1", MidiExportRole.CHORDS, full).roles)
        assertEquals(listOf(MidiExportRole.BASS), MidiAuditionView.role(MidiExportRole.BASS, full).roles)
        assertEquals(listOf(MidiExportRole.MELODY, MidiExportRole.CHORDS, MidiExportRole.BASS, MidiExportRole.DRUMS), MidiAuditionView.accepted(full).roles)
        val style = MidiAuditionView.stylePreview("late-night", "verse-2", full, 240, 720)
        assertEquals(MidiAuditionScope.StylePreview("late-night", "verse-2"), style.scope)
        assertEquals(MidiExportRole.entries, style.roles)
        assertEquals(MidiAuditionWindow(240, 720), style.window)

        val occurrence = MidiAuditionView.occurrence("verse-2", full, 240, 720)
        val output = FakeOutput()
        val controller = MidiAuditionController(output)
        assertIs<MidiAuditionResult.Applied>(controller.selectScope(MidiAuditionPlaybackPlan(occurrence)))
        assertEquals(MidiAuditionScope.Occurrence("verse-2"), controller.state.scope)
        assertEquals(MidiAuditionWindow(240, 720), controller.state.window)
        assertEquals(0, output.sessions.size)

        val outsideSeek = assertIs<MidiAuditionResult.Failed>(controller.seek(721))
        assertEquals(MidiAuditionProblemCode.INVALID_REQUEST, outsideSeek.problem.code)
        val outsideLoop = assertIs<MidiAuditionResult.Failed>(controller.setLoop(MidiAuditionLoop(120, 360)))
        assertEquals(MidiAuditionProblemCode.INVALID_REQUEST, outsideLoop.problem.code)

        assertIs<MidiAuditionResult.Applied>(controller.play())
        assertEquals(MidiAuditionWindow(240, 720), output.sessions.single().plan.view.window)
        assertIs<MidiAuditionResult.Applied>(controller.stop())
    }

    @Test
    fun `supersedes rapid sessions and ignores an old completion callback`() {
        val output = FakeOutput()
        val controller = MidiAuditionController(output)
        val first = MidiAuditionPlaybackPlan(MidiAuditionView.stylePreview("open-sky", "verse-1", song(), 0, 960))
        val second = MidiAuditionPlaybackPlan(MidiAuditionView.role(MidiExportRole.BASS, song()))

        assertIs<MidiAuditionResult.Applied>(controller.play(first))
        val firstSession = output.sessions.single()
        assertIs<MidiAuditionResult.Applied>(controller.play(second))
        val secondSession = output.sessions.last()
        assertTrue(firstSession.closed)
        assertEquals(MidiAuditionScope.Role(MidiExportRole.BASS), controller.state.scope)
        assertEquals(2L, controller.state.sessionId)

        firstSession.finish()
        assertEquals(2L, controller.state.sessionId)
        assertEquals(MidiAuditionPlaybackState.PLAYING, controller.state.playback)
        secondSession.finish()
        assertEquals(MidiAuditionPlaybackState.STOPPED, controller.state.playback)
        assertEquals(960L, controller.state.positionTick)
    }

    @Test
    fun `returns recoverable device errors and keeps project bytes untouched`() {
        val projectFile = root.resolve("project.json")
        val projectBytes = "unchanged-project".encodeToByteArray()
        Files.write(projectFile, projectBytes)
        val output = FakeOutput().apply {
            openFailure = MidiAuditionOutputException(
                MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
                "No MIDI output",
            )
        }
        val controller = MidiAuditionController(output)
        val plan = MidiAuditionPlaybackPlan(MidiAuditionView.accepted(song()))

        val unavailable = assertIs<MidiAuditionResult.Failed>(controller.play(plan))
        assertEquals(MidiAuditionProblemCode.DEVICE_UNAVAILABLE, unavailable.problem.code)
        assertEquals(MidiAuditionPlaybackState.STOPPED, unavailable.state.playback)
        assertContentEquals(projectBytes, Files.readAllBytes(projectFile))

        output.openFailure = null
        assertIs<MidiAuditionResult.Applied>(controller.play(plan))
        output.sessions.last().failure = MidiAuditionOutputException(MidiAuditionProblemCode.DEVICE_LOST, "MIDI device disappeared")
        val lost = assertIs<MidiAuditionResult.Failed>(controller.pause())
        assertEquals(MidiAuditionProblemCode.DEVICE_LOST, lost.problem.code)
        assertEquals(MidiAuditionPlaybackState.STOPPED, controller.state.playback)
        assertTrue(output.sessions.last().closed)
        assertContentEquals(projectBytes, Files.readAllBytes(projectFile))

        controller.close()
        assertTrue(controller.state.isClosed)
        assertTrue(output.closed)
        assertNotNull(controller.state.lastProblem)
    }

    @Test
    fun `switches a live output at the exact current tick without losing transport masks`() {
        val firstDevice = MidiAuditionOutputDevice("first", "First output", "Test", "First", "1")
        val secondDevice = MidiAuditionOutputDevice("second", "Second output", "Test", "Second", "1")
        val output = FakeOutput().apply { devices = listOf(firstDevice, secondDevice) }
        val controller = MidiAuditionController(output)
        val plan = MidiAuditionPlaybackPlan(MidiAuditionView.accepted(song()), outputDeviceId = firstDevice.id)

        assertIs<MidiAuditionResult.Applied>(controller.play(plan))
        val firstSession = output.sessions.single()
        assertIs<MidiAuditionResult.Applied>(controller.seek(480))
        assertIs<MidiAuditionResult.Applied>(controller.setLoop(MidiAuditionLoop(480, 960)))
        assertIs<MidiAuditionResult.Applied>(controller.setMutedRole(MidiExportRole.BASS, true))
        assertIs<MidiAuditionResult.Applied>(controller.setSoloRole(MidiExportRole.CHORDS, true))
        firstSession.playbackTick = 600

        assertIs<MidiAuditionResult.Applied>(controller.selectOutputDevice(secondDevice.id))
        val secondSession = output.sessions.last()
        assertTrue(firstSession.closed)
        assertTrue(firstSession.allNotesOffSent)
        assertEquals(MidiAuditionPlaybackState.PLAYING, controller.state.playback)
        assertEquals(secondDevice.id, controller.state.outputDeviceId)
        assertEquals(listOf(firstDevice, secondDevice), controller.state.outputDevices)
        assertEquals(600L, secondSession.plan.startTick)
        assertEquals(600L, controller.state.positionTick)
        assertEquals(MidiAuditionLoop(480, 960), secondSession.plan.loop)
        assertEquals(setOf(MidiExportRole.BASS), secondSession.plan.mutedRoles)
        assertEquals(setOf(MidiExportRole.CHORDS), secondSession.plan.soloRoles)
        assertEquals(secondDevice.id, secondSession.plan.outputDeviceId)

        val unavailable = assertIs<MidiAuditionResult.Failed>(controller.selectOutputDevice("gone"))
        assertEquals(MidiAuditionProblemCode.INVALID_REQUEST, unavailable.problem.code)
        assertEquals(secondDevice.id, controller.state.outputDeviceId)
    }

    @Test
    fun `failed playback never publishes playing state and repeated lifecycle leaves every session closed`() {
        val output = FakeOutput().apply {
            sessionFailure = MidiAuditionOutputException(MidiAuditionProblemCode.DEVICE_LOST, "MIDI output disappeared")
        }
        val controller = MidiAuditionController(output)
        val plan = MidiAuditionPlaybackPlan(MidiAuditionView.accepted(song()))

        val failedStart = assertIs<MidiAuditionResult.Failed>(controller.play(plan))
        assertEquals(MidiAuditionProblemCode.DEVICE_LOST, failedStart.problem.code)
        assertFalse(controller.stateHistory.any { it.playback == MidiAuditionPlaybackState.PLAYING })
        assertTrue(output.sessions.single().closed)

        output.sessionFailure = null
        repeat(64) { attempt ->
            val startTick = (attempt % 4) * 240L
            assertIs<MidiAuditionResult.Applied>(controller.play(plan.copy(startTick = startTick)))
            assertIs<MidiAuditionResult.Applied>(controller.pause())
            assertIs<MidiAuditionResult.Applied>(controller.seek(startTick))
            assertIs<MidiAuditionResult.Applied>(controller.stop())
        }
        controller.close()

        assertTrue(output.sessions.all { it.closed && it.allNotesOffSent })
        assertTrue(output.closed)
        assertTrue(controller.state.isClosed)
    }

    private fun song() = MidiExportSong(
        ppq = MidiPpq(480),
        sequenceName = "Audition fixture",
        tempoMicrosecondsPerQuarter = 500_000,
        meterNumerator = 4,
        meterDenominatorExponent = 2,
        markers = listOf(MidiExportMarker(1, "Verse", 0), MidiExportMarker(2, "Verse repeat", 480)),
        roles = listOf(
            role(MidiExportRole.MELODY, 60, 0),
            role(MidiExportRole.CHORDS, 48, 1),
            role(MidiExportRole.BASS, 36, 2),
            role(MidiExportRole.DRUMS, 36, 3),
        ),
        songEndTick = 960,
    )

    private fun role(role: MidiExportRole, pitch: Int, key: Long) = MidiExportRoleTrack(
        role,
        listOf(
            MidiNoteEvent(
                MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE, generatedEventKey = key),
                240,
                role.channel,
                pitch,
                96,
            ),
            MidiNoteEvent(
                MidiEventOrderingKey(480, MidiSemanticEventKind.NOTE, generatedEventKey = key + 10),
                720,
                role.channel,
                pitch + 2,
                88,
            ),
        ),
    )
}

private class FakeOutput : MidiAuditionOutput {
    val sessions = mutableListOf<FakeSession>()
    var openFailure: Exception? = null
    var sessionFailure: Exception? = null
    var devices: List<MidiAuditionOutputDevice> = emptyList()
    var closed = false

    override fun availableDevices(): List<MidiAuditionOutputDevice> = devices

    override fun open(plan: MidiAuditionPlaybackPlan, listener: MidiAuditionOutputListener): MidiAuditionOutputSession {
        openFailure?.let { throw it }
        return FakeSession(plan, listener).apply { failure = sessionFailure }.also(sessions::add)
    }

    override fun close() {
        closed = true
    }
}

private class FakeSession(
    val plan: MidiAuditionPlaybackPlan,
    private val listener: MidiAuditionOutputListener,
) : MidiAuditionOutputSession {
    val operations = mutableListOf<String>()
    var closed = false
    var allNotesOffSent = false
    var failure: Exception? = null
    var playbackTick: Long? = null

    override fun play() {
        operations += "play"
        failure?.let { throw it }
    }

    override fun pause() {
        operations += "pause"
        failure?.let { throw it }
    }

    override fun stop() {
        operations += "stop"
        allNotesOffSent = true
    }

    override fun positionTick(): Long? = playbackTick

    override fun seek(tick: Long) {
        operations += "seek:$tick"
        playbackTick = tick
        failure?.let { throw it }
    }

    override fun setLoop(loop: MidiAuditionLoop?) {
        operations += "loop:${loop?.let { "${it.startTick}-${it.endTick}" } ?: "none"}"
        failure?.let { throw it }
    }

    override fun setMutedRoles(roles: Set<MidiExportRole>) {
        operations += "mute:${roles.singleOrNull()?.name ?: roles.size}"
        failure?.let { throw it }
    }

    override fun setSoloRoles(roles: Set<MidiExportRole>) {
        operations += "solo:${roles.singleOrNull()?.name ?: roles.size}"
        failure?.let { throw it }
    }

    override fun close() {
        closed = true
        allNotesOffSent = true
    }

    fun finish() = listener.onPlaybackEnded()
}
