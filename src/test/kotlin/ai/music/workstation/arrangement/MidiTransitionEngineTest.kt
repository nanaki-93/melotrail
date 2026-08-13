package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem

class MidiTransitionEngineTest {
    private val engine = DeterministicMidiTransitionEngine()
    @TempDir lateinit var projectRoot: Path

    @Test
    fun `every transition type has bounded duration and cymbal remains unavailable`() {
        assertTrue(result(MidiTransitionType.NONE, 0).events.isEmpty())
        assertTrue(result(MidiTransitionType.DROP, 0).events.isEmpty())
        assertTrue(result(MidiTransitionType.DRUM_FILL, 1).events.any { it.instrument == LogicalInstrument.DRUMS })
        assertTrue(result(MidiTransitionType.BASS_WALK, 2).events.any { it.instrument == LogicalInstrument.BASS })
        assertTrue(result(MidiTransitionType.PAD_SUSTAIN, 1).events.any { it.instrument == LogicalInstrument.PAD })
        assertTrue(result(MidiTransitionType.BUILD, 1).events.map { it.instrument }.containsAll(setOf(LogicalInstrument.DRUMS, LogicalInstrument.BASS, LogicalInstrument.PAD)))

        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.DRUM_FILL, 0) }
        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.DROP, 1) }
        assertThrows(IllegalArgumentException::class.java) { result(MidiTransitionType.CYMBAL, 1) }
    }

    @Test
    fun `first middle and final boundaries account for inserted bars exactly once across meter changes`() {
        val sections = listOf(
            section(0, "A", duration = 1920),
            section(1, "B", duration = 1440, meter = MidiTimeSignature(0, 3, 4)),
            section(2, "C", duration = 1920)
        )
        val generated = engine.generate(
            sections,
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan(MidiTransitionType.DRUM_FILL, 2), MidiTransitionPlan()),
            instruments, drumMap
        )

        assertEquals(listOf(0L, 3360L, 8640L), generated.placements.map { it.startTick })
        assertEquals(listOf(1440L, 3840L, 0L), generated.placements.map { it.insertedTicksAfter })
        assertTrue(generated.events.all { it.startTick in 1920 until 3360 || it.startTick in 4800 until 8640 })
        assertThrows(IllegalArgumentException::class.java) {
            engine.generate(sections, listOf(MidiTransitionPlan(), MidiTransitionPlan(), MidiTransitionPlan(MidiTransitionType.BUILD, 1)), instruments, drumMap)
        }
    }

    @Test
    fun `bass walk uses only confident boundary harmony including Am to F and degrades conservatively`() {
        val walk = result(MidiTransitionType.BASS_WALK, 1).events.filter { it.instrument == LogicalInstrument.BASS }
        assertEquals(listOf(45, 44, 42, 41), walk.map { it.pitch }) // A2 -> G#2 -> F#2 -> F2

        val weak = engine.generate(
            listOf(section(0, "A", finalChord = chord("Am", confidence = 0.74)), section(1, "B")),
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap
        )
        assertTrue(weak.events.isEmpty())
        assertTrue(weak.diagnostics.single().contains("low-confidence"))
    }

    @Test
    fun `pad hold collision filtering unavailable instruments and regeneration stay deterministic`() {
        val first = result(MidiTransitionType.PAD_SUSTAIN, 1)
        assertTrue(first.events.all { it.instrument == LogicalInstrument.PAD && it.endTick <= 3840 })
        assertEquals(first, result(MidiTransitionType.PAD_SUSTAIN, 1))

        val collision = engine.generate(
            listOf(section(0, "A"), section(1, "B")), listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap,
            occupied = listOf(TransitionMidiEvent(LogicalInstrument.BASS, 1920, 2280, 45, 80))
        )
        assertTrue(collision.events.none { it.startTick == 1920L && it.pitch == 45 })
        assertTrue(collision.diagnostics.any { it.contains("Dropped colliding") })

        val noBass = engine.generate(
            listOf(section(0, "A", instruments = setOf(LogicalInstrument.PAD)), section(1, "B", instruments = setOf(LogicalInstrument.PAD))),
            listOf(MidiTransitionPlan(MidiTransitionType.BASS_WALK, 1), MidiTransitionPlan()), instruments, drumMap
        )
        assertTrue(noBass.events.isEmpty())
        assertTrue(noBass.diagnostics.single().contains("bass is not active"))
    }

    @Test
    fun `adapter writes a stable inspectable transition artifact without touching source midi`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.writeString(source, "source MIDI remains untouched")
        Files.writeString(clean, "clean MIDI reference")
        val project = Project(Project.CURRENT_VERSION, "transitions", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat(sampleRate = 32_000, channels = 3))
        val arrangement = DetailedArrangement(sections = listOf(
            detailedSection(0, TransitionPlan(TransitionType.BRIDGE, 1, bridge = BridgePlan(0.7, listOf(BridgeElement.DRUM_FILL)))),
            detailedSection(1, TransitionPlan())
        ))
        val before = Files.readAllBytes(source)

        val generated = MidiTransitionGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis()))
        val sequence = MidiSystem.getSequence(generated.path.toFile())

        assertEquals(projectRoot.resolve("midi/generated/transitions.mid"), generated.path)
        assertTrue(generated.result.events.isNotEmpty())
        assertEquals(480, sequence.resolution)
        assertTrue(sequence.tickLength >= 5760)
        assertTrue(Files.readAllBytes(source).contentEquals(before))
        assertEquals(Files.readAllBytes(generated.path).toList(), Files.readAllBytes(MidiTransitionGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis())).path).toList())
    }

    private fun result(type: MidiTransitionType, bars: Int) = engine.generate(
        listOf(section(0, "A"), section(1, "B")), listOf(MidiTransitionPlan(type, bars), MidiTransitionPlan()), instruments, drumMap
    )

    private fun section(
        index: Int, part: String, duration: Long = 1920, meter: MidiTimeSignature = MidiTimeSignature(0, 4, 4),
        finalChord: MidiChord = chord("Am"), instruments: Set<LogicalInstrument> = setOf(LogicalInstrument.BASS, LogicalInstrument.DRUMS, LogicalInstrument.PAD)
    ) = TransitionSectionContext(index, part, 480, duration, listOf(MidiTempoChange(0, if (index == 1) 90.0 else 120.0)), listOf(meter), MidiKey("A", "minor", 0.8),
        listOf(finalChord.copy(startTick = 0, endTick = duration)), instruments, 0.7).let { context ->
        if (part == "B") context.copy(chords = listOf(chord("F").copy(endTick = duration))) else context
    }

    private fun detailedSection(index: Int, transition: TransitionPlan) = DetailedArrangementSection(
        index, "A${index + 1}", "A", SongSectionPurpose.DEVELOPMENT, 0.7,
        listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.7, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0),
            DrumsInstrumentPlan(role = DrumsRole.BUILD, density = 0.7, kickDensity = 0.7, snarePattern = SnarePattern.BEATS_2_4, hiHatDensity = 0.7, swing = 0.0, fillLastBar = false),
            PadInstrumentPlan(role = SustainedRole.SUSTAINED, density = 0.7, register = MusicalRegister.MID)), transition
    )

    private fun analysis() = MidiAnalysis(partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0, tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0, noteCount = 3,
        noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.7, key = MidiKey("A", "minor", 0.8), chords = listOf(chord("Am").copy(endTick = 1920)))
    private fun chord(symbol: String, confidence: Double = 0.9) = MidiChord(0, 1920, symbol, confidence)

    private val instruments = mapOf(LogicalInstrument.BASS to TransitionInstrument(0, 32), LogicalInstrument.DRUMS to TransitionInstrument(9), LogicalInstrument.PAD to TransitionInstrument(1, 89))
    private val drumMap = mapOf("kick" to 36, "snare" to 38, "clap" to 39, "closedHat" to 42, "openHat" to 46)
}
