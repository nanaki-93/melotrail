package ai.music.workstation.arrangement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem

class DrumMidiGenerationTest {
    private val generator = DeterministicDrumMidiGenerator()
    @TempDir lateinit var projectRoot: Path

    @Test
    fun `every allow-listed role has an exact deterministic four-four pattern`() {
        assertEquals(
            listOf("kick@0", "closedHat@0", "closedHat@240", "closedHat@480", "closedHat@720", "closedHat@960", "closedHat@1200", "closedHat@1440", "closedHat@1680"),
            events(DrumsRole.MINIMAL, SnarePattern.NONE)
        )
        assertEquals(
            listOf("kick@0", "closedHat@0", "closedHat@240", "snare@480", "closedHat@480", "closedHat@720", "closedHat@960", "closedHat@1200", "snare@1440", "closedHat@1440", "closedHat@1680"),
            events(DrumsRole.SOFT_LOFI, SnarePattern.BEATS_2_4)
        )
        assertEquals(
            listOf("kick@0", "closedHat@0", "closedHat@240", "snare@480", "closedHat@480", "closedHat@720", "kick@960", "closedHat@960", "closedHat@1200", "snare@1440", "closedHat@1440", "closedHat@1680"),
            events(DrumsRole.STANDARD_GROOVE, SnarePattern.BEATS_2_4)
        )
        assertEquals(
            listOf("kick@0", "closedHat@0", "closedHat@240", "closedHat@480", "closedHat@720", "snare@960", "closedHat@960", "closedHat@1200", "closedHat@1440", "closedHat@1680"),
            events(DrumsRole.HALF_TIME, SnarePattern.BEAT_3)
        )
        assertEquals(
            listOf("kick@0", "closedHat@0", "closedHat@120", "closedHat@240", "closedHat@360", "kick@480", "snare@480", "closedHat@480", "closedHat@600", "closedHat@720", "closedHat@840", "kick@960", "closedHat@960", "closedHat@1080", "closedHat@1200", "closedHat@1320", "kick@1440", "snare@1440", "closedHat@1440", "closedHat@1560", "closedHat@1680", "closedHat@1800"),
            events(DrumsRole.BUILD, SnarePattern.BEATS_2_4)
        )
    }

    @Test
    fun `registry named-hit resolution missing hits meters and boundaries are validated`() {
        val missingKick = request(noteMap = noteMap - "kick")
        assertThrows(IllegalArgumentException::class.java) { generator.generate(missingKick) }

        val threeFour = generator.generate(request(length = 1440, signatures = listOf(MidiTimeSignature(0, 3, 4)))).hits
        assertTrue(threeFour.all { it.startTick in 0 until 1440 && it.endTick <= 1440 })
        assertEquals(listOf(0L, 480L, 960L), threeFour.filter { it.name == "kick" || it.name == "snare" }.map { it.startTick })
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(request(signatures = listOf(MidiTimeSignature(0, 5, 4))))
        }
    }

    @Test
    fun `density energy swing and half-time stay bounded`() {
        assertTrue(generator.generate(request(density = 0.0)).hits.isEmpty())
        assertTrue(generator.generate(request(kickDensity = 0.0)).hits.none { it.name == "kick" })
        assertTrue(generator.generate(request(energy = 0.0)).hits.all { it.velocity == 44 })
        assertTrue(generator.generate(request(energy = 1.0)).hits.all { it.velocity in 104..127 })

        val straight = generator.generate(request()).hits.filter { it.name == "closedHat" }.map { it.startTick }
        val swung = generator.generate(request(swing = 0.5)).hits.filter { it.name == "closedHat" }.map { it.startTick }
        assertEquals(listOf(0L, 240L, 480L, 720L, 960L, 1200L, 1440L, 1680L), straight)
        assertEquals(listOf(0L, 300L, 480L, 780L, 960L, 1260L, 1440L, 1740L), swung)
        assertEquals(listOf(960L), generator.generate(request(role = DrumsRole.HALF_TIME, snarePattern = SnarePattern.BEAT_3)).hits.filter { it.name == "snare" }.map { it.startTick })
        assertThrows(IllegalArgumentException::class.java) { generator.generate(request(swing = 0.51)) }
    }

    @Test
    fun `last-bar fills and repeated requests do not spill or vary randomly`() {
        val withoutFill = generator.generate(request(length = 3840, fillLastBar = false)).hits.filter { it.name == "snare" }.map { it.startTick }
        val withFill = generator.generate(request(length = 3840, fillLastBar = true)).hits.filter { it.name == "snare" }.map { it.startTick }
        assertEquals(listOf(480L, 1440L, 2400L, 3360L), withoutFill)
        assertEquals(listOf(480L, 1440L, 2400L, 3360L, 3480L, 3600L, 3720L), withFill)
        assertTrue(withFill.all { it < 3840 })
        val buildFill = generator.generate(request(length = 3840, fillLastBar = true, transitionIntent = SongTransitionIntent.BUILD)).hits
        assertTrue(buildFill.first { it.name == "snare" && it.startTick == 3720L }.velocity >
            generator.generate(request(length = 3840, fillLastBar = true)).hits.first { it.name == "snare" && it.startTick == 3720L }.velocity)

        val first = generator.generate(request(sectionIndex = 0, start = 0, role = DrumsRole.MINIMAL))
        val repeated = generator.generate(request(sectionIndex = 1, start = 1920, role = DrumsRole.BUILD))
        assertTrue(first.hits != repeated.hits)
        assertEquals(1920L, repeated.hits.first().startTick)
        assertEquals(first, generator.generate(request(sectionIndex = 0, start = 0, role = DrumsRole.MINIMAL)))
    }

    @Test
    fun `adapter writes full timeline on registry channel without changing source or bass MIDI`() {
        val source = projectRoot.resolve("source/A.mid")
        val clean = projectRoot.resolve("midi/clean/A.mid")
        val bass = projectRoot.resolve("midi/generated/bass.mid")
        Files.createDirectories(clean.parent)
        Files.createDirectories(bass.parent)
        Files.createDirectories(source.parent)
        Files.writeString(source, "source MIDI remains untouched")
        Files.writeString(clean, "clean MIDI reference")
        Files.writeString(bass, "existing bass MIDI remains untouched")
        val project = Project(Project.CURRENT_VERSION, "drums", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat())
        val arrangement = DetailedArrangement(sections = listOf(
            section(0, DrumsRole.MINIMAL, false), section(1, DrumsRole.BUILD, true)
        ))
        val analysis = analysis()
        val sourceBefore = Files.readAllBytes(source)
        val bassBefore = Files.readAllBytes(bass)

        val generated = DrumMidiGenerationAdapter().generate(projectRoot, project, arrangement, mapOf("A" to analysis))
        val sequence = MidiSystem.getSequence(generated.path.toFile())
        val channels = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
            .mapNotNull { it.message as? javax.sound.midi.ShortMessage }
            .filter { it.command == javax.sound.midi.ShortMessage.NOTE_ON && it.data2 > 0 }.map { it.channel }.toSet()

        assertEquals(projectRoot.resolve("midi/generated/drums.mid"), generated.path)
        assertTrue(generated.hits.isNotEmpty())
        assertEquals(480, sequence.resolution)
        assertTrue(sequence.tickLength >= 3840)
        assertEquals(setOf(9), channels)
        assertTrue(Files.readAllBytes(source).contentEquals(sourceBefore))
        assertTrue(Files.readAllBytes(bass).contentEquals(bassBefore))
    }

    private fun events(role: DrumsRole, snare: SnarePattern): List<String> = generator.generate(request(role = role, snarePattern = snare)).hits.map { "${it.name}@${it.startTick}" }

    private fun request(
        sectionIndex: Int = 0,
        start: Long = 0,
        length: Long = 1920,
        signatures: List<MidiTimeSignature> = listOf(MidiTimeSignature(0, 4, 4)),
        density: Double = 1.0,
        energy: Double = 0.5,
        role: DrumsRole = DrumsRole.STANDARD_GROOVE,
        kickDensity: Double = 1.0,
        snarePattern: SnarePattern = SnarePattern.BEATS_2_4,
        hiHatDensity: Double = 1.0,
        swing: Double = 0.0,
        fillLastBar: Boolean = false,
        transitionIntent: SongTransitionIntent = SongTransitionIntent.NONE,
        noteMap: Map<String, Int> = this.noteMap
    ) = DrumGenerationRequest(
        sectionIndex, start, 480, listOf(MidiTempoChange(0, 120.0)), signatures, length, energy, density, role,
        kickDensity, snarePattern, hiHatDensity, swing, fillLastBar, transitionIntent, 9, noteMap
    )

    private fun section(index: Int, role: DrumsRole, fill: Boolean) = DetailedArrangementSection(
        index, "A${index + 1}", "A", SongSectionPurpose.DEVELOPMENT, 0.7,
        listOf(PianoSourcePlan(), DrumsInstrumentPlan(role = role, density = 1.0, kickDensity = 1.0, snarePattern = SnarePattern.BEATS_2_4, hiHatDensity = 1.0, swing = 0.0, fillLastBar = fill)),
        TransitionPlan()
    )

    private fun analysis() = MidiAnalysis(
        partId = "A", ppq = 480, durationTicks = 1920, durationSeconds = 2.0,
        tempoMap = listOf(MidiTempoChange(0, 120.0)), timeSignatures = listOf(MidiTimeSignature(0, 4, 4)),
        bars = 1, beats = 4.0, noteCount = 3, noteDensity = 0.1, rhythmicDensity = 0.1, energy = 0.7,
        key = MidiKey("C", "major", 0.8), chords = emptyList()
    )

    private val noteMap = mapOf("kick" to 36, "snare" to 38, "clap" to 39, "closedHat" to 42, "openHat" to 46)
}
