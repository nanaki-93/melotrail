package app.melotrail.audition.adapter

import app.melotrail.audition.MidiAuditionController
import app.melotrail.audition.MidiAuditionOutputException
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionView
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import javax.sound.midi.MidiUnavailableException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class JdkMidiAuditionOutputTest {
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
