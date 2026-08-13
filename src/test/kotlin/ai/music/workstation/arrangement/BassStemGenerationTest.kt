package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem

class BassStemGenerationTest {
    private val generator = DeterministicBassMidiGenerator()
    @TempDir lateinit var projectRoot: Path

    @Test
    fun `each role has an explicit deterministic C-major pattern`() {
        assertEquals(listOf(36, 36, 36, 36), notes(BassRole.ROOT).map { it.pitch })
        assertEquals(listOf(36, 43, 36, 43), notes(BassRole.ROOT_FIFTH).map { it.pitch })
        assertEquals(listOf(36, 48, 36, 48), notes(BassRole.OCTAVE).map { it.pitch })
        assertEquals(listOf(36), notes(BassRole.SUSTAINED).map { it.pitch })
        assertEquals(listOf(36, 36, 37, 38), notes(BassRole.SIMPLE_WALKING, chords = listOf(chord(0, 1920, "C"), chord(1920, 3840, "D"))).filter { it.startTick < 1920 }.map { it.pitch })
        assertEquals(listOf(0L, 480L, 960L, 1440L), notes(BassRole.ROOT).map { it.startTick })
        assertEquals(listOf(1920L), notes(BassRole.SUSTAINED).map { it.endTick })
    }

    @Test
    fun `uses major minor and accidental chord roots in the low bass range`() {
        assertEquals(37, notes(BassRole.ROOT, chords = listOf(chord(0, 1920, "C#m"))).first().pitch)
        assertEquals(46, notes(BassRole.ROOT, chords = listOf(chord(0, 1920, "Bb"))).first().pitch)
        assertEquals(40, notes(BassRole.ROOT, chords = listOf(chord(0, 1920, "E"))).first().pitch)
    }

    @Test
    fun `uses a confident key for weak chords and silence for insufficient harmony`() {
        val fallback = generator.generate(request(chords = listOf(chord(0, 1920, "D", confidence = 0.5)), key = MidiKey("A", "minor", 0.7)))
        assertEquals(setOf(45), fallback.notes.map { it.pitch }.toSet())

        val silent = generator.generate(request(chords = listOf(chord(0, 1920, "D", confidence = 0.5)), key = MidiKey("A", "minor", 0.5)))
        assertTrue(silent.notes.isEmpty())
        assertTrue(silent.diagnostics.single().contains("No confident harmony"))
    }

    @Test
    fun `density and energy have bounded deterministic outcomes`() {
        assertEquals(0, generator.generate(request(density = 0.0)).notes.size)
        assertEquals(1, generator.generate(request(density = 0.1)).notes.size)
        assertEquals(2, generator.generate(request(density = 0.5)).notes.size)
        assertEquals(4, generator.generate(request(density = 1.0)).notes.size)
        assertTrue(generator.generate(request(energy = 0.0)).notes.all { it.velocity == 52 })
        assertTrue(generator.generate(request(energy = 1.0)).notes.all { it.velocity in 100..106 })
    }

    @Test
    fun `movement and syncopation remain inside the section`() {
        val static = notes(BassRole.ROOT, movement = BassMovement.STATIC)
        val rising = notes(BassRole.ROOT, movement = BassMovement.RISING)
        val falling = notes(BassRole.ROOT, movement = BassMovement.FALLING)
        val balanced = notes(BassRole.ROOT, movement = BassMovement.BALANCED)
        assertEquals(48, rising.last().pitch)
        assertEquals(48, falling.first().pitch)
        assertEquals(listOf(36, 48, 36, 48), balanced.map { it.pitch })
        assertEquals(listOf(36, 36, 36, 36), static.map { it.pitch })

        val syncopated = generator.generate(request(syncopation = 0.25)).notes
        assertEquals(listOf(0L, 600L, 1080L, 1560L), syncopated.map { it.startTick })
        assertTrue(syncopated.all { it.startTick >= 0 && it.endTick <= 1920 && it.endTick > it.startTick })
        assertThrows(IllegalArgumentException::class.java) { generator.generate(request(syncopation = 0.26)) }
    }

    @Test
    fun `supports three-four sections and repeated section requests without overflows or overlaps`() {
        val threeFour = generator.generate(request(length = 1440, signatures = listOf(MidiTimeSignature(0, 3, 4)), density = 1.0))
        assertEquals(listOf(0L, 480L, 960L), threeFour.notes.map { it.startTick })
        assertTrue(threeFour.notes.all { it.endTick <= 1440 })

        val first = generator.generate(request(sectionIndex = 0, start = 0, movement = BassMovement.STATIC))
        val second = generator.generate(request(sectionIndex = 1, start = 1920, movement = BassMovement.RISING))
        assertFalse(first.notes == second.notes)
        assertEquals(1920L, second.notes.first().startTick)
        assertEquals(first, generator.generate(request(sectionIndex = 0, start = 0, movement = BassMovement.STATIC)))
        (first.notes + second.notes).groupBy { it.pitch }.values.forEach { samePitch ->
            assertTrue(samePitch.sortedBy { it.startTick }.zipWithNext().all { (left, right) -> left.endTick <= right.startTick })
        }
    }

    @Test
    fun `adapter writes and reparses one full timeline MIDI artifact`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.writeString(source, "source MIDI remains untouched")
        Files.writeString(clean, "clean MIDI reference")
        val project = Project(Project.CURRENT_VERSION, "bass", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat())
        val arrangement = Arrangement(sections = listOf(
            ArrangementSection(0, "A", listOf(InstrumentPlan("source", InstrumentMode.SOURCE), InstrumentPlan("bass", InstrumentMode.GENERATED, "root_fifth", 1.0))),
            ArrangementSection(1, "A", listOf(InstrumentPlan("source", InstrumentMode.SOURCE), InstrumentPlan("bass", InstrumentMode.GENERATED, "octave", 1.0)))
        ))
        val analysis = MidiAnalysis(
            partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
            bars = 1, beats = 4.0, noteCount = 3, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.5,
            key = MidiKey("C", "major", 0.8), chords = listOf(chord(0, 1920, "C"))
        )
        val sourceBefore = Files.readString(source)

        val generated = BassMidiGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis))
        val sequence = MidiSystem.getSequence(generated.path.toFile())

        assertEquals(projectRoot.resolve("midi/generated/bass.mid"), generated.path)
        assertEquals(8, generated.notes.size)
        assertEquals(480, sequence.resolution)
        assertTrue(sequence.tickLength >= 3840)
        assertEquals(sourceBefore, Files.readString(source))
    }

    @Test
    fun `adapter consumes detailed arrangement bass controls`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.writeString(source, "source MIDI remains untouched")
        Files.writeString(clean, "clean MIDI reference")
        val project = Project(Project.CURRENT_VERSION, "bass-v3", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat())
        val arrangement = DetailedArrangement(sections = listOf(
            DetailedArrangementSection(
                0, "A1", "A", SongSectionPurpose.DEVELOPMENT, 0.6,
                listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT_FIFTH, density = 1.0,
                    movement = DetailedBassMovement.LEAPING, register = MusicalRegister.LOW, syncopation = 0.1)),
                TransitionPlan()
            ),
            DetailedArrangementSection(
                1, "A2", "A", SongSectionPurpose.DEVELOPMENT, 0.6,
                listOf(PianoSourcePlan(), BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 1.0,
                    movement = DetailedBassMovement.ROOT_MOTION, register = MusicalRegister.LOW, syncopation = 0.0)),
                TransitionPlan()
            )
        ))
        val analysis = MidiAnalysis(
            partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0,
            tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
            bars = 1, beats = 4.0, noteCount = 3, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.5,
            key = MidiKey("C", "major", 0.8), chords = listOf(chord(0, 1920, "C"))
        )

        val generated = BassMidiGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis))

        assertEquals(listOf(36, 43, 36, 43, 36, 36, 36, 36), generated.notes.map { it.pitch })
        assertEquals(listOf(0L, 528L, 1008L, 1488L, 1920L, 2400L, 2880L, 3360L), generated.notes.map { it.startTick })
    }

    private fun notes(
        role: BassRole,
        chords: List<MidiChord> = listOf(chord(0, 1920, "C")),
        movement: BassMovement = BassMovement.STATIC
    ): List<BassMidiNote> = generator.generate(request(role = role, length = chords.maxOf { it.endTick }, chords = chords, movement = movement)).notes

    private fun request(
        sectionIndex: Int = 0,
        start: Long = 0,
        length: Long = 1920,
        chords: List<MidiChord> = listOf(chord(0, length, "C")),
        key: MidiKey? = MidiKey("C", "major", 0.8),
        signatures: List<MidiTimeSignature> = listOf(MidiTimeSignature(0, 4, 4)),
        density: Double = 1.0,
        energy: Double = 0.5,
        role: BassRole = BassRole.ROOT,
        movement: BassMovement = BassMovement.STATIC,
        syncopation: Double = 0.0
    ) = BassGenerationRequest(
        sectionIndex, start, 480, listOf(MidiTempoChange(0, 120.0)), signatures, length, key, chords,
        energy, density, role, movement, "low", syncopation, 0, 33
    )

    private fun chord(start: Long, end: Long, symbol: String, confidence: Double = 0.9) = MidiChord(start, end, symbol, confidence)
}
