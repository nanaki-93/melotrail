package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import app.melotrail.audio.WAVDecoder
import app.melotrail.cli.ArrangementProjectCommands
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class PianoBassQualityGateTest {
    @TempDir lateinit var root: Path

    @Test
    fun `runs piano bass only MIDI-first gate in order preserves sources and safely resumes`() = runBlocking {
        val projectRoot = root.resolve("gate")
        createProject(projectRoot)
        val sourceA = Files.readAllBytes(projectRoot.resolve("source/A.mid"))
        val sourceB = Files.readAllBytes(projectRoot.resolve("source/B.mid"))
        val renderer = FakeRenderer()
        val gate = PianoBassQualityGate(renderer, bassGenerator = BassMidiGenerationAdapter(libraryRoot = Path.of("sounds")))

        val first = gate.run(projectRoot)
        val expectedStages = listOf(
            "Validated MIDI-first project", "Prepared clean MIDI", "Analyzed or reused current MIDI analyses",
            "Created or reused piano+bass song plan", "Created or reused approved piano+bass arrangement",
            "Generated full-timeline piano and bass MIDI", "Rendered timeline-aligned piano and bass PCM-24 stems", "Created dry lossless mix"
        )
        assertTrue(first.progress.map { it.substringAfter("] ") }.zip(expectedStages).all { (actual, expected) -> actual.startsWith(expected) })
        assertFalse(first.reusedFinalArtifacts)
        assertEquals(2, renderer.calls)
        listOf("song_plan.json", "section_variations.json", "arrangement.json", "midi/generated/piano.mid", "midi/generated/bass.mid", "stems/piano.wav", "stems/bass.wav", "mix/dry.wav", "quality-gate.json").forEach {
            assertTrue(Files.isRegularFile(projectRoot.resolve(it)), "$it must be inspectable")
        }
        assertTrue(Files.readAllBytes(projectRoot.resolve("source/A.mid")).contentEquals(sourceA))
        assertTrue(Files.readAllBytes(projectRoot.resolve("source/B.mid")).contentEquals(sourceB))

        val report = first.report
        listOf("stems/piano.wav", "stems/bass.wav", "mix/dry.wav").forEach { path ->
            val audio = WAVDecoder(QuietReporter).decode(projectRoot.resolve(path))
            assertEquals(32_000, audio.format.sampleRate)
            assertEquals(3, audio.format.channels)
            assertEquals(24, audio.format.bitDepth)
            assertEquals(report.timelineFrames, audio.length.toLong())
            assertTrue(audio.samples.all { it.isFinite() })
        }
        assertTrue(report.dryMixPeak <= report.peakCeiling)
        assertBassStartsOnBoundaries(projectRoot.resolve("midi/generated/bass.mid"))
        assertEquals(listOf("A1", "A2", "B1", "B2", "A3"), firstArrangementInstanceIds(projectRoot))

        val resumed = gate.run(projectRoot)
        assertTrue(resumed.reusedFinalArtifacts)
        assertEquals(2, renderer.calls, "valid artifacts must be resumed without rendering again")
        assertEquals(first.report.inputFingerprint, resumed.report.inputFingerprint)

        val cleanA = projectRoot.resolve("midi/clean/A.mid")
        Files.setLastModifiedTime(cleanA, FileTime.fromMillis(System.currentTimeMillis() + 2_000))
        val refreshed = gate.run(projectRoot)
        assertFalse(refreshed.reusedFinalArtifacts)
        assertEquals(4, renderer.calls, "a stale clean-MIDI dependency must trigger a fresh render")
    }

    @Test
    fun `CLI routes the narrow quality gate without post production stages`() {
        val projectRoot = root.resolve("cli-gate")
        createProject(projectRoot)
        val output = ArrangementProjectCommands.executeQualityGateForTest(
            arrayOf("quality-gate", "--project", projectRoot.toString()),
            FakeRenderer()
        )

        assertTrue(ArrangementProjectCommands.handles(arrayOf("quality-gate")))
        assertTrue(output.contains("[8/8] Created dry lossless mix"))
        assertTrue(output.contains("mix/dry.wav"))
        assertFalse(output.contains("LoFi"))
        assertFalse(output.contains("master"))
    }

    private fun createProject(projectRoot: Path) {
        val sourceA = projectRoot.resolve("source/A.mid")
        val sourceB = projectRoot.resolve("source/B.mid")
        val cleanA = projectRoot.resolve("midi/clean/A.mid")
        val cleanB = projectRoot.resolve("midi/clean/B.mid")
        writeChordMidi(sourceA, 60, 64, 67)
        writeChordMidi(sourceB, 62, 65, 69)
        Files.createDirectories(cleanA.parent)
        Files.copy(sourceA, cleanA)
        Files.copy(sourceB, cleanB)
        ProjectStore.write(projectRoot, Project(
            version = Project.CURRENT_VERSION,
            name = "gate",
            parts = listOf(
                Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid")),
                Part("B", "source/B.mid", midi = MidiReferences(clean = "midi/clean/B.mid"))
            ),
            structure = listOf("A", "A", "B", "B", "A"),
            renderFormat = RenderFormat(32_000, 3, 24)
        ))
    }

    private fun writeChordMidi(path: Path, root: Int, third: Int, fifth: Int) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        listOf(root, third, fifth).forEachIndexed { index, pitch ->
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 84 - index * 4), 0))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 1_920))
        }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun assertBassStartsOnBoundaries(path: Path) {
        val starts = MidiSystem.getSequence(path.toFile()).tracks.flatMap { track ->
            (0 until track.size()).map { track[it] }
        }.mapNotNull { event ->
            (event.message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { event.tick }
        }
        assertTrue(starts.isNotEmpty())
        assertTrue(starts.all { it % 480L == 0L }, "bass notes must start on analyzed beat/chord boundaries")
    }

    private fun firstArrangementInstanceIds(projectRoot: Path): List<String> {
        val text = Files.readString(projectRoot.resolve("arrangement.json"))
        return Regex("\\\"instanceId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").findAll(text).map { it.groupValues[1] }.toList()
    }

    private class FakeRenderer : InstrumentRenderer {
        var calls = 0
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            calls++
            val sample = if (instrument == LogicalInstrument.PIANO) 0.20f else 0.14f
            val audio = AudioBuffer(
                FloatArray(expectedFrames.toInt() * format.channels) { sample },
                AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"),
                expectedFrames.toDouble() / format.sampleRate
            )
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, sample.toDouble(), "fake-renderer", "test", "", "")
        }
    }

    private object QuietReporter : app.melotrail.model.ErrorReporter {
        override fun report(message: String) = Unit
        override fun report(message: String, cause: Throwable) = Unit
    }
}
