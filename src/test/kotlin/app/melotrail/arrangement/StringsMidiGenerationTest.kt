package app.melotrail.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

class StringsMidiGenerationTest {
    private val generator = DeterministicStringsMidiGenerator()
    @TempDir lateinit var projectRoot: Path
    @Test
    fun `every supported role has deterministic bounded output`() {
        val sustained = generator.generate(request(role = StringsMidiRole.SUSTAINED_HARMONY)).notes
        val long = generator.generate(request(role = StringsMidiRole.LONG_NOTES)).notes
        val climax = generator.generate(request(role = StringsMidiRole.CLIMAX_REINFORCEMENT, purpose = SongSectionPurpose.CLIMAX)).notes
        val counter = generator.generate(request(role = StringsMidiRole.SIMPLE_COUNTERMELODY)).notes

        assertEquals(listOf(StringsMidiNote(0, 460, 64, 62), StringsMidiNote(0, 460, 67, 62), StringsMidiNote(0, 460, 72, 62)), sustained)
        assertEquals(listOf(StringsMidiNote(0, 460, 67, 62), StringsMidiNote(0, 460, 72, 62)), long)
        assertEquals(listOf(StringsMidiNote(0, 460, 64, 68), StringsMidiNote(0, 460, 67, 68), StringsMidiNote(0, 460, 72, 68)), climax)
        assertEquals(listOf(StringsMidiNote(0, 460, 72, 62)), counter)
        assertEquals(counter, generator.generate(request(role = StringsMidiRole.SIMPLE_COUNTERMELODY)).notes)
    }

    @Test
    fun `shared exact chord parser accepts sixths and slash chords`() {
        val sixth = generator.generate(request(chords = listOf(chord(0, 480, "C6")))).notes
        val slash = generator.generate(request(chords = listOf(chord(0, 480, "G/B")))).notes

        assertEquals(setOf(0, 4, 7, 9), sixth.map { it.pitch % 12 }.toSet())
        assertEquals(setOf(2, 7, 11), slash.map { it.pitch % 12 }.toSet())
    }

    @Test
    fun `carries a prior strings voicing across a section cardinality change without an octave reset`() {
        val first = generator.generate(request(role = StringsMidiRole.SUSTAINED_HARMONY, chords = listOf(chord(0, 480, "C")))).notes.map { it.pitch }.sorted()
        val carried = generator.generate(request(role = StringsMidiRole.SUSTAINED_HARMONY, chords = listOf(chord(0, 480, "F"))).copy(
            sectionIndex = 1, sectionStartTick = 480, previousAcceptedVoicing = first
        )).notes.map { it.pitch }.sorted()
        val reset = generator.generate(request(role = StringsMidiRole.SUSTAINED_HARMONY, chords = listOf(chord(0, 480, "F"))).copy(
            sectionIndex = 1, sectionStartTick = 480
        )).notes.map { it.pitch }.sorted()

        val carriedMovement = SustainedVoicingContinuity.measure(first, carried)
        val resetMovement = SustainedVoicingContinuity.measure(first, reset)
        assertTrue(carriedMovement.totalSemitoneMotion <= resetMovement.totalSemitoneMotion)
        assertTrue(carriedMovement.maximumVoiceMotion <= 12)
    }

    @Test
    fun `climax and countermelody gates conservatively degrade`() {
        val outsideClimax = generator.generate(request(role = StringsMidiRole.CLIMAX_REINFORCEMENT))
        assertTrue(outsideClimax.notes.isEmpty())
        assertTrue(outsideClimax.diagnostics.single().contains("outside a climax"))

        val fallback = generator.generate(request(
            role = StringsMidiRole.SIMPLE_COUNTERMELODY,
            sourceRange = MidiIntRange(60, 84), sourceNoteDensity = 0.8, sourceRhythmicDensity = 0.8
        ))
        assertTrue(fallback.notes.isNotEmpty())
        assertTrue(fallback.diagnostics.any { it.contains("fell back") })
    }

    @Test
    fun `density budget resolves strings off and countermelody waits for a melody gap`() {
        val fullBudget = DensityBudget(0, 480, DensityBudget.MAX_PITCHED_NOTES, DensityBudget.MAX_PITCHED_NOTES)
        val off = generator.generate(request().copy(densityBudget = fullBudget))
        assertTrue(off.notes.isEmpty())
        assertTrue(off.diagnostics.single().contains("resolved OFF"))

        val state = ArrangementState.fromAcceptedPiano(
            480, listOf(MidiNote(0, 60, 90, 0, 480)), "a".repeat(64)
        )
        val counter = generator.generate(request(role = StringsMidiRole.SIMPLE_COUNTERMELODY, sourceRange = MidiIntRange(48, 60)).copy(arrangementState = state))
        assertTrue(counter.diagnostics.any { it.contains("fell back") })
    }

    @Test
    fun `wide source range uses time local collision space instead of forcing strings off`() {
        val state = ArrangementState.fromAcceptedPiano(
            480, listOf(MidiNote(0, 60, 90, 0, 480)), "a".repeat(64)
        )

        val result = generator.generate(request(
            role = StringsMidiRole.LONG_NOTES,
            sourceRange = MidiIntRange(36, 86)
        ).copy(
            arrangementState = state,
            densityBudget = DensityBudget(0, 480, DensityBudget.MAX_PITCHED_NOTES, 7)
        ))

        assertTrue(result.notes.isNotEmpty())
        assertTrue(result.notes.none { it.pitch == 60 })

        val highRegisterSliver = generator.generate(request(
            role = StringsMidiRole.SUSTAINED_HARMONY,
            sourceRange = MidiIntRange(36, 86)
        ).copy(
            register = "high",
            arrangementState = state,
            densityBudget = DensityBudget(0, 480, DensityBudget.MAX_PITCHED_NOTES, 7)
        ))
        assertTrue(highRegisterSliver.notes.isNotEmpty())
    }

    @Test
    fun `dense melody uses a later collision free window for sparse strings`() {
        val occupiedChordTones = (60..79).filter { it % 12 in setOf(0, 4, 7) }
        val state = ArrangementState.fromAcceptedPiano(
            480,
            occupiedChordTones.map { pitch -> MidiNote(0, pitch, 90, 0, 240) },
            "a".repeat(64)
        )

        val result = generator.generate(request().copy(
            arrangementState = state,
            densityBudget = DensityBudget(0, 480, DensityBudget.MAX_PITCHED_NOTES, 6)
        ))

        assertTrue(result.notes.isNotEmpty())
        assertTrue(result.notes.all { it.startTick == 240L })
        assertTrue(result.diagnostics.any { it.contains("source-melody gap") })
    }

    @Test
    fun `harmony voice-leading density source clearance and boundaries stay bounded`() {
        val result = generator.generate(request(
            length = 960,
            chords = listOf(chord(0, 480, "C"), chord(480, 960, "F")),
            density = 1.0,
            sourceRange = MidiIntRange(48, 65)
        ))

        assertEquals(listOf(0L, 480L), result.notes.map { it.startTick }.distinct())
        assertTrue(result.notes.all { it.pitch in 67..79 && it.endTick < 960 })
        result.notes.groupBy { it.pitch }.values.forEach { notes ->
            assertTrue(notes.sortedBy { it.startTick }.zipWithNext().all { (left, right) -> left.endTick <= right.startTick })
        }
        assertEquals(listOf(0L, 960L), generator.generate(request(
            length = 1920,
            chords = listOf(chord(0, 480, "C"), chord(480, 960, "Dm"), chord(960, 1440, "Em"), chord(1440, 1920, "F")),
            density = 0.5
        )).notes.map { it.startTick }.distinct())
    }

    @Test
    fun `adapter writes a registry mapped full timeline without changing earlier MIDI`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        val pad = projectRoot.resolve("midi/generated/pad.mid")
        Files.createDirectories(source.parent); Files.createDirectories(clean.parent); Files.createDirectories(pad.parent)
        Files.writeString(source, "source remains untouched"); writeTestMidi(clean); Files.writeString(pad, "pad remains untouched")
        val sourceBefore = Files.readAllBytes(source); val padBefore = Files.readAllBytes(pad)
        val project = Project(Project.CURRENT_VERSION, "strings", listOf(Part("A", "source/A.mid", midi = canonicalMidiReferences(projectRoot, "A"))), renderFormat = RenderFormat())
        val arrangement = DetailedArrangement(sections = listOf(
            section(0, SongSectionPurpose.DEVELOPMENT, StringsRole.SUSTAINED_HARMONY),
            section(1, SongSectionPurpose.CLIMAX, StringsRole.CLIMAX_REINFORCEMENT)
        ))

        val generated = StringsMidiGenerationAdapter(libraryRoot = TestSoundLibrary.root()).generate(projectRoot, project, arrangement, mapOf("A" to analysis()))
        val sequence = MidiSystem.getSequence(generated.path.toFile())
        val channels = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }.mapNotNull { it.message as? ShortMessage }
            .filter { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }.map { it.channel }.toSet()

        assertEquals(projectRoot.resolve("midi/generated/strings.mid"), generated.path)
        assertEquals(6, generated.notes.size)
        assertEquals(480, sequence.resolution)
        assertEquals(setOf(0), channels)
        assertTrue(Files.readAllBytes(source).contentEquals(sourceBefore))
        assertTrue(Files.readAllBytes(pad).contentEquals(padBefore))
    }

    private fun request(
        role: StringsMidiRole = StringsMidiRole.SUSTAINED_HARMONY,
        purpose: SongSectionPurpose = SongSectionPurpose.DEVELOPMENT,
        length: Long = 480,
        chords: List<MidiChord> = listOf(chord(0, length, "C")),
        density: Double = 1.0,
        sourceRange: MidiIntRange? = null,
        sourceNoteDensity: Double = 0.0,
        sourceRhythmicDensity: Double = 0.0
    ) = StringsGenerationRequest(0, purpose, 0, 480, listOf(MidiTempoChange(0, 120.0)), listOf(MidiTimeSignature(0, 4, 4)), length,
        MidiKey("C", "major", 0.9), chords, sourceRange, 8, sourceNoteDensity, sourceRhythmicDensity, 0.5, density, role, "mid", 0, 48)

    private fun section(index: Int, purpose: SongSectionPurpose, role: StringsRole) = DetailedArrangementSection(
        index, "A${index + 1}", "A", purpose, if (purpose == SongSectionPurpose.CLIMAX) 0.8 else 0.5,
        listOf(PianoSourcePlan(), StringsInstrumentPlan(role = role, density = 1.0, register = MusicalRegister.MID)), TransitionPlan()
    )

    private fun analysis() = MidiAnalysis(partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)), bars = 1, beats = 4.0,
        noteCount = 3, noteDensity = 0.1, rhythmicDensity = 0.1, melodicRange = 8, energy = 0.5,
        key = MidiKey("C", "major", 0.9), chords = listOf(chord(0, 1920, "C")))
    private fun chord(start: Long, end: Long, symbol: String, confidence: Double = 0.9) = MidiChord(start, end, symbol, confidence)
}
