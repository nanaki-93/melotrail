package app.melotrail.arrangement

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class MidiPartAnalyzerTest {
    @TempDir lateinit var root: Path
    private val analyzer = MidiPartAnalyzer()

    @Test
    fun `analyzes a known C major 4-4 phrase and persists a distinct MIDI reference`() {
        val midi = midi("phrase.mid") { sequence, track ->
            tempo(track, 0, 500_000); signature(track, 0, 4, 4)
            note(track, 0, 1920, 0, 60, 100); note(track, 0, 1920, 0, 64, 90); note(track, 0, 1920, 0, 67, 80)
        }
        val analysis = analyzer.analyze(midi, "A")

        assertEquals(480, analysis.ppq)
        assertEquals(1920, analysis.durationTicks)
        assertEquals(2.0, analysis.durationSeconds, 1e-9)
        assertEquals(1, analysis.bars)
        assertEquals(4.0, analysis.beats, 1e-9)
        assertEquals(3, analysis.noteCount)
        assertEquals(MidiIntRange(60, 67), analysis.pitchRange)
        assertEquals("C", analysis.key?.tonic)
        assertEquals("major", analysis.key?.mode)
        assertEquals("C", analysis.chords.single().symbol)
        assertEquals(0.1875, analysis.noteDensity, 1e-9)
        assertTrue(analysis.energy in 0.0..1.0)

        val project = v2Project("A", midi)
        val stored = MidiAnalysisStore.write(root, project, "A", analysis)
        val decoded = Json.decodeFromString<MidiAnalysis>(Files.readString(stored))
        assertEquals(analysis, decoded)
        assertEquals(AnalysisKind.MIDI, ProjectStore.read(root).parts.single().analysis?.kind)
    }

    @Test
    fun `respects tempo meter channel overlap rests and explicit fallbacks`() {
        val tempoMidi = midi("tempo.mid") { _, track ->
            tempo(track, 0, 500_000); tempo(track, 480, 1_000_000); signature(track, 0, 3, 4)
            note(track, 0, 240, 0, 60, 100); note(track, 720, 960, 1, 60, 70)
        }
        val tempo = analyzer.analyze(tempoMidi, "tempo")
        assertEquals(1.5, tempo.durationSeconds, 1e-9)
        assertEquals(1, tempo.bars)
        assertEquals(2.0, tempo.beats, 1e-9)
        assertEquals(2, tempo.noteCount)

        val fallbackMidi = midi("fallback.mid") { _, track -> note(track, 0, 480, 0, 69, 90) }
        val fallback = analyzer.analyze(fallbackMidi, "fallback")
        assertTrue(fallback.tempoMap.single().inferred)
        assertTrue(fallback.timeSignatures.single().inferred)
        assertEquals(0.5, fallback.durationSeconds, 1e-9)
    }

    @Test
    fun `empty and ambiguous MIDI are finite and conservative`() {
        val empty = analyzer.analyze(midi("empty.mid") { _, _ -> }, "empty")
        assertEquals(0, empty.noteCount)
        assertNull(empty.pitchRange)
        assertNull(empty.key)
        assertTrue(empty.noteDensity in 0.0..1.0 && empty.energy in 0.0..1.0)

        val ambiguous = analyzer.analyze(midi("ambiguous.mid") { _, track ->
            note(track, 0, 240, 0, 60, 80); note(track, 480, 720, 0, 61, 80)
        }, "ambiguous")
        assertNull(ambiguous.chords.single().symbol)
    }

    @Test
    fun `rejects invalid unclosed notes and mid-bar meter changes without updating a project`() {
        val invalid = midi("invalid.mid") { _, track -> on(track, 0, 0, 60, 100) }
        assertTrue(assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(invalid, "A") }.message.orEmpty().contains("unclosed"))
        val meter = midi("meter.mid") { _, track ->
            signature(track, 0, 4, 4); signature(track, 480, 3, 4); note(track, 0, 960, 0, 60, 100)
        }
        assertTrue(assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(meter, "A") }.message.orEmpty().contains("bar boundary"))
    }

    private fun v2Project(id: String, midi: Path): Project {
        val source = root.resolve("source/$id.mid")
        val clean = root.resolve("midi/clean/$id.mid")
        Files.createDirectories(source.parent); Files.copy(midi, source)
        Files.createDirectories(clean.parent); Files.copy(midi, clean)
        ProjectStore.create(root, "test", RenderFormat())
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "test",
            renderFormat = RenderFormat(),
            parts = listOf(Part(id, "source/$id.mid", midi = MidiReferences(clean = "midi/clean/$id.mid")))
        )
        ProjectStore.write(root, project)
        return project
    }

    private fun midi(name: String, events: (Sequence, javax.sound.midi.Track) -> Unit): Path {
        val sequence = Sequence(Sequence.PPQ, 480)
        events(sequence, sequence.createTrack())
        return root.resolve(name).also { MidiSystem.write(sequence, 1, it.toFile()) }
    }
    private fun tempo(track: javax.sound.midi.Track, tick: Long, micros: Int) = track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3), tick))
    private fun signature(track: javax.sound.midi.Track, tick: Long, numerator: Int, denominator: Int) = track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4), tick))
    private fun note(track: javax.sound.midi.Track, start: Long, end: Long, channel: Int, pitch: Int, velocity: Int) { on(track, start, channel, pitch, velocity); off(track, end, channel, pitch) }
    private fun on(track: javax.sound.midi.Track, tick: Long, channel: Int, pitch: Int, velocity: Int) = track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), tick))
    private fun off(track: javax.sound.midi.Track, tick: Long, channel: Int, pitch: Int) = track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), tick))
}
