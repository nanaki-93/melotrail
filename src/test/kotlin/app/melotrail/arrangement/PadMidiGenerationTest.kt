package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

class PadMidiGenerationTest {
    private val generator = DeterministicPadMidiGenerator()
    @TempDir lateinit var projectRoot: Path

    @Test
    fun `parses major minor seventh and suspended chords into bounded sustained voicings`() {
        assertEquals(setOf(0, 4, 7), pitchClasses(notes(chords = listOf(chord(0, 480, "C")))))
        assertEquals(setOf(0, 3, 7), pitchClasses(notes(chords = listOf(chord(0, 480, "Cm")))))
        assertEquals(setOf(0, 4, 7, 10), pitchClasses(notes(chords = listOf(chord(0, 480, "C7")), energy = 0.8)))
        assertEquals(setOf(0, 2, 7), pitchClasses(notes(chords = listOf(chord(0, 480, "Csus2")))))
        assertEquals(setOf(0, 5, 7), pitchClasses(notes(chords = listOf(chord(0, 480, "Csus4")))))
    }

    @Test
    fun `unsupported and unknown confident chords remain silent without inventing harmony`() {
        val unsupported = generator.generate(request(chords = listOf(chord(0, 480, "Cdim"))))
        val unknown = generator.generate(request(chords = listOf(chord(0, 480, null))))

        assertTrue(unsupported.notes.isEmpty())
        assertTrue(unsupported.diagnostics.single().contains("Unsupported or unknown"))
        assertTrue(unknown.notes.isEmpty())
        assertTrue(unknown.diagnostics.single().contains("Unsupported or unknown"))
    }

    @Test
    fun `chooses deterministic inversions with minimum adjacent voice movement`() {
        val result = generator.generate(request(length = 960, chords = listOf(chord(0, 480, "C"), chord(480, 960, "F"))))
        val voicings = result.notes.groupBy { it.startTick }.toSortedMap().values.map { it.sortedBy { note -> note.pitch }.map { note -> note.pitch } }

        assertEquals(listOf(55, 60, 64), voicings[0])
        assertEquals(listOf(57, 60, 65), voicings[1])
        assertEquals(3, voicings[0].zip(voicings[1]).sumOf { (left, right) -> kotlin.math.abs(right - left) })
        assertEquals(result, generator.generate(request(length = 960, chords = listOf(chord(0, 480, "C"), chord(480, 960, "F")))))
    }

    @Test
    fun `density energy and register rules stay bounded`() {
        val fourChords = listOf(chord(0, 480, "C"), chord(480, 960, "Dm"), chord(960, 1440, "Em"), chord(1440, 1920, "F"))
        assertTrue(generator.generate(request(length = 1920, chords = fourChords, density = 0.0)).notes.isEmpty())
        assertEquals(listOf(0L, 960L), generator.generate(request(length = 1920, chords = fourChords, density = 0.5)).notes.map { it.startTick }.distinct())
        assertEquals(2, notes(energy = 0.2).size)
        assertEquals(3, notes(energy = 0.5).size)
        assertEquals(34, notes(energy = 0.0).first().velocity)
        assertEquals(76, notes(energy = 1.0).first().velocity)
        assertTrue(notes(register = "mid").all { it.pitch in 48..71 })
        assertTrue(notes(register = "mid_high").all { it.pitch in 60..83 })
        assertThrows(IllegalArgumentException::class.java) { generator.generate(request(register = "low")) }
    }

    @Test
    fun `rests changing chord durations fallbacks and release gaps do not cross boundaries`() {
        val changing = generator.generate(request(
            length = 1920,
            chords = listOf(chord(0, 480, "C"), chord(960, 1920, "F"))
        )).notes
        assertEquals(listOf(0L, 960L), changing.map { it.startTick }.distinct())
        assertEquals(setOf(460L), changing.filter { it.startTick == 0L }.map { it.endTick }.toSet())
        assertEquals(setOf(1900L), changing.filter { it.startTick == 960L }.map { it.endTick }.toSet())

        val fallback = generator.generate(request(chords = listOf(chord(0, 480, "D", confidence = 0.5)), key = MidiKey("A", "minor", 0.7)))
        assertEquals(setOf(9, 0, 4), pitchClasses(fallback.notes))
        assertTrue(fallback.diagnostics.single().contains("tonic fallback"))
        val silent = generator.generate(request(chords = listOf(chord(0, 480, "D", confidence = 0.5)), key = MidiKey("A", "minor", 0.69)))
        assertTrue(silent.notes.isEmpty())

        val first = generator.generate(request(start = 0, length = 480, chords = listOf(chord(0, 480, "C"))))
        val second = generator.generate(request(sectionIndex = 1, start = 480, length = 480, chords = listOf(chord(0, 480, "C"))))
        (first.notes + second.notes).groupBy { it.pitch }.values.forEach { samePitch ->
            assertTrue(samePitch.sortedBy { it.startTick }.zipWithNext().all { (left, right) -> left.endTick <= right.startTick })
        }
    }

    @Test
    fun `ordinary piano voicing receives a reduced pad shell while a truly dense core rests`() {
        val ordinaryState = ArrangementState.fromAcceptedPiano(
            480,
            (60..65).map { pitch -> MidiNote(0, pitch, 80, 0, 480) },
            "a".repeat(64)
        )
        val denseState = ArrangementState.fromAcceptedPiano(
            480,
            (60..69).map { pitch -> MidiNote(0, pitch, 80, 0, 480) },
            "a".repeat(64)
        )

        val ordinary = generator.generate(request(arrangementState = ordinaryState))
        val result = generator.generate(request(arrangementState = denseState))

        assertEquals(2, ordinary.notes.size)
        assertTrue(result.notes.isEmpty())
        assertTrue(result.diagnostics.single().contains("pad rests"))
    }

    @Test
    fun `moving melody that occupies every shell receives one free chord-tone texture`() {
        val state = ArrangementState.fromAcceptedPiano(
            480,
            listOf(57, 60, 64, 69).mapIndexed { index, pitch -> MidiNote(0, pitch, 80, index * 120L, index * 120L + 100) },
            "a".repeat(64)
        )

        val result = generator.generate(request(chords = listOf(chord(0, 480, "Am")), arrangementState = state))

        assertEquals(1, result.notes.size)
        assertTrue(result.notes.single().pitch % 12 in setOf(9, 0, 4))
        assertTrue(state.requireTrack(ArrangementState.PIANO).notes.none { it.pitch == result.notes.single().pitch })
    }

    @Test
    fun `adapter writes full timeline on pad channel without changing source or other generated MIDI`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        val drums = projectRoot.resolve("midi/generated/drums.mid")
        Files.createDirectories(source.parent)
        Files.createDirectories(clean.parent)
        Files.createDirectories(drums.parent)
        Files.writeString(source, "source MIDI remains untouched")
        writeTestMidi(clean)
        Files.writeString(drums, "existing drums MIDI remains untouched")
        val project = Project(Project.CURRENT_VERSION, "pad", listOf(Part("A", "source/A.mid", midi = canonicalMidiReferences(projectRoot, "A"))), renderFormat = RenderFormat())
        val arrangement = DetailedArrangement(sections = listOf(section(0, MusicalRegister.LOW), section(1, MusicalRegister.HIGH)))
        val analysis = analysis()
        val sourceBefore = Files.readAllBytes(source)
        val drumsBefore = Files.readAllBytes(drums)

        val generated = PadMidiGenerationAdapter(libraryRoot = TestSoundLibrary.root()).generate(projectRoot, project, arrangement, mapOf("A" to analysis))
        val sequence = MidiSystem.getSequence(generated.path.toFile())
        val channels = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
            .mapNotNull { it.message as? ShortMessage }
            .filter { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }.map { it.channel }.toSet()

        assertEquals(projectRoot.resolve("midi/generated/pad.mid"), generated.path)
        assertEquals(6, generated.notes.size)
        assertEquals(480, sequence.resolution)
        assertTrue(sequence.tickLength >= 3840)
        assertEquals(setOf(0), channels)
        assertTrue(Files.readAllBytes(source).contentEquals(sourceBefore))
        assertTrue(Files.readAllBytes(drums).contentEquals(drumsBefore))
    }

    private fun notes(
        chords: List<MidiChord> = listOf(chord(0, 480, "C")),
        energy: Double = 0.5,
        register: String = "mid"
    ): List<PadMidiNote> = generator.generate(request(length = chords.maxOf { it.endTick }, chords = chords, energy = energy, register = register)).notes

    private fun request(
        sectionIndex: Int = 0,
        start: Long = 0,
        length: Long = 480,
        chords: List<MidiChord> = listOf(chord(0, length, "C")),
        key: MidiKey? = MidiKey("C", "major", 0.8),
        density: Double = 1.0,
        energy: Double = 0.5,
        register: String = "mid",
        arrangementState: ArrangementState? = null
    ) = PadGenerationRequest(
        sectionIndex, start, 480, listOf(MidiTempoChange(0, 120.0)), listOf(MidiTimeSignature(0, 4, 4)), length,
        key, chords, energy, density, PadRole.SUSTAINED_CHORDS, register, 0, 89, arrangementState
    )

    private fun section(index: Int, register: MusicalRegister) = DetailedArrangementSection(
        index, "A${index + 1}", "A", SongSectionPurpose.DEVELOPMENT, if (register == MusicalRegister.HIGH) 0.8 else 0.5,
        listOf(PianoSourcePlan(), PadInstrumentPlan(role = SustainedRole.SUSTAINED, density = 1.0, register = register)), TransitionPlan()
    )

    private fun analysis() = MidiAnalysis(
        partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1, beats = 4.0, noteCount = 3, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.5,
        key = MidiKey("C", "major", 0.8), chords = listOf(chord(0, 1920, "C"))
    )

    private fun chord(start: Long, end: Long, symbol: String?, confidence: Double = 0.9) = MidiChord(start, end, symbol, confidence)
    private fun pitchClasses(notes: List<PadMidiNote>): Set<Int> = notes.map { it.pitch % 12 }.toSet()
}
