package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.AudioFormat
import ai.music.workstation.audio.WAVDecoder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class StemRenderingMixerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `renders aligned project-format stems including a transition and reuses only current artifacts`() = runBlocking {
        val project = project()
        val analyses = mapOf("A" to analysis("A"), "B" to analysis("B"))
        writeMidi(root.resolve("source/A.mid"), 0, 1_920)
        writeMidi(root.resolve("source/B.mid"), 0, 1_920)
        Files.createDirectories(root.resolve("midi/clean"))
        Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        Files.copy(root.resolve("source/B.mid"), root.resolve("midi/clean/B.mid"))
        writeMidi(root.resolve("midi/generated/bass.mid"), 0, 3_840)
        writeMidi(root.resolve("midi/generated/transitions.mid"), 1_920, 2_040)
        ProjectStore.write(root, project)

        val renderer = FakeRenderer()
        val service = StemRenderingMixer(renderer)
        val first = service.render(root, project, arrangement(), analyses)

        assertFalse(first.reused)
        assertEquals(2, renderer.calls)
        assertEquals(48_000, first.report.timelineFrames) // two 2-second sections plus one 2-second inserted 4/4 bar
        assertEquals(listOf("piano", "bass"), first.report.stems.map { it.name })
        listOf("stems/piano.wav", "stems/bass.wav", "mix/dry.wav").forEach { relative ->
            val audio = WAVDecoder(QuietReporter).decode(root.resolve(relative))
            assertEquals(8_000, audio.format.sampleRate)
            assertEquals(1, audio.format.channels)
            assertEquals(24, audio.format.bitDepth)
            assertEquals(48_000, audio.length)
        }
        assertTrue(first.report.predictedPeak > 0.95f)
        assertTrue(first.report.appliedGain < 1f)
        assertTrue(WAVDecoder(QuietReporter).decode(root.resolve("mix/dry.wav")).samples.all { kotlin.math.abs(it) <= 0.9501f })

        assertTrue(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(2, renderer.calls)
        Files.write(root.resolve("stems/bass.wav"), byteArrayOf(0))
        assertFalse(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(4, renderer.calls)
        Files.write(root.resolve("midi/generated/bass.mid"), byteArrayOf(0), java.nio.file.StandardOpenOption.APPEND)
        assertFalse(service.render(root, project, arrangement(), analyses).reused)
        assertEquals(6, renderer.calls)
    }

    @Test
    fun `mixer uses a uniform reduction instead of hard clipping and rejects non-finite samples`() {
        val mixer = DeterministicStemMixer()
        val loud = AudioBuffer(floatArrayOf(0.8f, 0.8f), AudioFormat(44_100, 1, 24, false, false, "WAV"), 2.0 / 44_100)
        val mixed = mixer.mix(listOf(MixTrack("a", loud), MixTrack("b", loud)))
        assertEquals(1.6f, mixed.predictedPeak)
        assertEquals(0.95f, mixed.buffer.samples[0], 0.0001f)
        assertTrue(mixed.appliedGain < 1f)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            mixer.mix(listOf(MixTrack("bad", loud.copy(samples = floatArrayOf(Float.NaN, 0f)))))
        }
    }

    @Test
    fun `bridge uses the incoming tempo before generated MIDI resumes`() = runBlocking {
        val project = project()
        val analyses = mapOf("A" to analysis("A", bpm = 120.0), "B" to analysis("B", bpm = 90.0))
        writeMidi(root.resolve("source/A.mid"), 0, 1_920)
        writeMidi(root.resolve("source/B.mid"), 0, 1_920)
        Files.createDirectories(root.resolve("midi/clean"))
        Files.copy(root.resolve("source/A.mid"), root.resolve("midi/clean/A.mid"))
        Files.copy(root.resolve("source/B.mid"), root.resolve("midi/clean/B.mid"))
        writeMidi(root.resolve("midi/generated/bass.mid"), 0, 3_840)
        writeMidi(root.resolve("midi/generated/transitions.mid"), 1_920, 2_040)

        val renderer = FakeRenderer()
        StemRenderingMixer(renderer).render(root, project, arrangement(), analyses)

        val bass = requireNotNull(renderer.sequences[LogicalInstrument.BASS])
        assertEquals(90.0, tempos(bass).getValue(1_920L), 0.001)
        assertEquals(90.0, tempos(bass).getValue(3_840L), 0.001)
    }

    private fun project() = Project(Project.CURRENT_VERSION, "render", listOf(
        Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid")),
        Part("B", "source/B.mid", midi = MidiReferences(clean = "midi/clean/B.mid"))
    ), listOf("A", "B"), RenderFormat(8_000, 1, 24))

    private fun arrangement() = DetailedArrangement(sections = listOf(
        DetailedArrangementSection(0, "A1", "A", SongSectionPurpose.DEVELOPMENT, 0.3, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.4, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan(TransitionType.BRIDGE, 1)),
        DetailedArrangementSection(1, "B1", "B", SongSectionPurpose.CLIMAX, 0.7, listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.6, movement = DetailedBassMovement.ROOT_MOTION, register = MusicalRegister.LOW, syncopation = 0.0)), TransitionPlan())
    ))

    private fun analysis(id: String, bpm: Double = 120.0) = MidiAnalysis(partId = id, ppq = 480, durationTicks = 1_920, durationSeconds = 240.0 / bpm,
        tempoMap = listOf(MidiTempoChange(0, bpm)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0,
        noteCount = 1, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.5)

    private fun writeMidi(path: Path, start: Long, end: Long) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 48, 96), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 48, 0), end))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private class FakeRenderer : InstrumentRenderer {
        var calls = 0
        val sequences = mutableMapOf<LogicalInstrument, Sequence>()
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            calls++
            sequences[instrument] = MidiSystem.getSequence(midi.toFile())
            val sample = if (instrument == LogicalInstrument.PIANO) 0.8f else 0.8f
            val audio = AudioBuffer(FloatArray(expectedFrames.toInt() * format.channels) { sample }, AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate)
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, sample.toDouble(), "fake", "test", "", "")
        }
    }

    private fun tempos(sequence: Sequence): Map<Long, Double> = buildMap {
        sequence.tracks.first().let { track ->
            (0 until track.size()).map(track::get).forEach { event ->
                val message = event.message as? javax.sound.midi.MetaMessage ?: return@forEach
                if (message.type == 0x51) {
                    val data = message.data
                    val micros = ((data[0].toInt() and 0xff) shl 16) or
                        ((data[1].toInt() and 0xff) shl 8) or
                        (data[2].toInt() and 0xff)
                    put(event.tick, 60_000_000.0 / micros)
                }
            }
        }
    }

    private object QuietReporter : ai.music.workstation.model.ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
}
