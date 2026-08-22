package app.melotrail.arrangement

import app.melotrail.application.MusicalAuthorityBuilder
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

class GeneratedRoleValidationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `bass report is deterministic and accepts registered boundary values`() {
        val context = context("drums")
        writeMidi(context.midi, listOf(Note(0, 480, 36, 1), Note(480, 960, 36, 127)), channel = 9)

        val first = DeterministicGeneratedRoleValidator().validate(context.input())
        val second = DeterministicGeneratedRoleValidator().validate(context.input())

        assertTrue(first.passed, first.violations.joinToString("; "))
        assertEquals(first, second)
        assertEquals(listOf("arrangement", "authority", "registry"), first.inputHashes.map { it.name })
        assertEquals(listOf("noteCount", "ppq"), first.metrics.map { it.name })
        assertEquals(listOf("occ-0"), first.target.occurrenceIds)
    }

    @Test
    fun `invalid note event and occurrence end produce a failed bounded report`() {
        val context = context()
        writeMidi(context.midi, listOf(Note(0, 480, 28, 100), Note(1_920, 1_921, 28, 100), Note(0, 480, 28, 100)))

        val report = DeterministicGeneratedRoleValidator().validate(context.input())

        assertFalse(report.passed)
        assertTrue(report.violations.any { "occurrence" in it || "duplicate" in it })
        assertEquals(report.violations.sorted(), report.violations)
        assertTrue(report.violations.size <= RoleValidationPolicy().maximumViolations)
    }

    private fun context(role: String = "bass"): Context {
        val source = root.resolve("source/A.mid"); val clean = root.resolve("midi/clean/A.mid")
        writeMidi(source, listOf(Note(0, 1_920, 60, 100))); Files.createDirectories(clean.parent); Files.copy(source, clean)
        val project = Project(
            name = "validator", parts = listOf(Part("A", "source/A.mid", "verse", midi = canonicalMidiReferences(root, "A"))),
            renderFormat = RenderFormat(), envelope = ProjectV4Envelope(
                compositionSettings = CompositionSettings(
                    key = MusicalKey(PitchClass.of(PitchSpelling.E), ScaleModeId.MAJOR), tempo = Tempo(120.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1), decisionRevision = 1,
                    resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = HarmonySettings(progressions = listOf(ChordProgression(SectionTypeId("verse"), listOf(ChordEvent(ChordEventId("e"), PitchClass.of(PitchSpelling.E), ChordQuality.MAJOR, 0))))),
                structureOccurrences = listOf(StructureOccurrence("occ-0", "A"))
            )
        )
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiPartAnalyzer().analyze(clean, "A"))
        val plan: DetailedInstrumentPlan = if (role == "drums") DrumsInstrumentPlan(role = DrumsRole.MINIMAL, density = 1.0, kickDensity = 1.0, snarePattern = SnarePattern.BEATS_2_4, hiHatDensity = 1.0, swing = 0.0, fillLastBar = false)
        else BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 1.0, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)
        val arrangement = DetailedArrangement(sections = listOf(DetailedArrangementSection(0, "occ-0", "A", SongSectionPurpose.DEVELOPMENT, 0.5, listOf(PianoSourcePlan(), plan), TransitionPlan())))
        return Context(role, project, arrangement, MusicalAuthorityBuilder().arrangementGeneration(root), root.resolve("midi/generated/$role.mid"))
    }

    private fun writeMidi(path: Path, notes: List<Note>, channel: Int = 0) {
        Files.createDirectories(requireNotNull(path.parent)); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        val micros = 500_000
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        notes.forEach { note -> track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, note.pitch, note.velocity), note.start)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, note.pitch, 0), note.end)) }
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private data class Context(val role: String, val project: Project, val arrangement: DetailedArrangement, val projection: app.melotrail.application.ArrangementGenerationProjection, val midi: Path) {
        fun input() = GeneratedRoleValidationInput(role, midi, project, arrangement, projection, InstrumentRegistryLoader(TestSoundLibrary.root()).load(), "a".repeat(64), "b".repeat(64))
    }
    private data class Note(val start: Long, val end: Long, val pitch: Int, val velocity: Int)
}
