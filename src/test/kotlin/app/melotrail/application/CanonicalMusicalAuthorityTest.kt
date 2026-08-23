package app.melotrail.application

import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiKey
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.StructureOccurrence
import app.melotrail.arrangement.canonicalMidiReferences
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.HarmonySettings
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CanonicalMusicalAuthorityTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `repeated sections retain identities and harmonic queries cycle on half-open boundaries`() {
        val root = project("repeated", durationTicks = 5_760, occurrences = listOf("first", "again"))
        val authority = MusicalAuthorityBuilder().build(root)

        assertEquals(listOf("first", "again"), authority.occurrenceTimeline.map(MusicalOccurrence::occurrenceId))
        assertEquals(listOf(0L to 5_760L, 5_760L to 11_520L), authority.occurrenceTimeline.map { it.startTick to it.endTick })
        assertEquals(listOf("C", "Dm", "C", "C", "Dm", "C"), authority.harmonicTimeline.entries.map { it.chord.symbol })
        assertEquals("again", authority.harmonicTimeline.atTick(5_760L).occurrenceId)
        assertEquals(listOf("first", "again"), authority.harmonicTimeline.forNoteInterval(5_759L, 5_761L).map { it.occurrenceId }.distinct())
        assertEquals("C", authority.harmonicTimeline.atBar(2L).chord.symbol)
    }

    @Test
    fun `declared facts stay authoritative and conflicting analysis is diagnostic`() {
        val root = project("conflict")
        val analysisPath = root.resolve("analysis/A.json")
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath))
        Files.writeString(analysisPath, Json.encodeToString(MidiAnalysis.serializer(), analysis.copy(key = MidiKey("D", "minor", 0.95))))

        val authority = MusicalAuthorityBuilder().build(root)

        assertEquals("C major", authority.projectKey.displayName)
        assertTrue(authority.diagnostics.any { it.kind == MusicalAuthorityDiagnosticKind.ANALYZED_KEY_CONFLICT && it.analyzedValue == "D natural minor" })
    }

    @Test
    fun `arrangement projection retains repeated occurrence identity and canonical harmony`() {
        val root = project("arrangement-projection", occurrences = listOf("verse-one", "verse-two"))

        val projection = MusicalAuthorityBuilder().arrangementGeneration(root)

        assertEquals(listOf("verse-one", "verse-two"), projection.occurrences.map(MusicalOccurrence::occurrenceId))
        assertEquals(listOf("verse-one", "verse-two"), projection.harmony.map { it.occurrenceId }.distinct())
        assertEquals("C major", projection.projectKey.displayName)
        assertTrue(projection.inputSha256.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(projection.contextSha256, projection.inputSha256)
    }

    @Test
    fun `AI Fix and Enhance receive the same declared tick-addressable harmony`() {
        val root = project("shared-repair-context", durationTicks = 5_760)
        val builder = MusicalAuthorityBuilder()

        val repair = builder.partRepair(root, "A")
        val enhancement = builder.partEnhancement(root, "A")

        assertEquals(repair.contextSha256, enhancement.contextSha256)
        assertEquals(repair.projectKey, enhancement.projectKey)
        assertEquals(repair.tempo, enhancement.tempo)
        assertEquals(repair.meter, enhancement.meter)
        assertEquals(repair.harmony, enhancement.harmony)
        assertEquals(listOf("C", "Dm", "C"), repair.harmony.map { it.chord.symbol })
        assertEquals(listOf(0L, 1_920L, 3_840L), repair.harmony.map { it.startTick })
    }

    @Test
    fun `hash reflects canonical inputs but not project location`() {
        val root = project("hash")
        val builder = MusicalAuthorityBuilder()
        val original = builder.build(root).contextSha256

        val relocated = tempDir.resolve("relocated")
        copyTree(root, relocated)
        assertEquals(original, builder.build(relocated).contextSha256)

        fun changed(update: (Project) -> Project): String {
            ProjectStore.write(root, update(ProjectStore.read(root)))
            return builder.build(root).contextSha256
        }

        assertNotEquals(original, changed { project -> project.copy(envelope = project.envelope.copy(compositionSettings = project.envelope.compositionSettings!!.copy(key = key(PitchSpelling.D)))) })
        val afterKey = builder.build(root).contextSha256
        assertNotEquals(afterKey, changed { project -> project.copy(envelope = project.envelope.copy(compositionSettings = project.envelope.compositionSettings!!.copy(tempo = Tempo(93.0)))) })
        val afterTempo = builder.build(root).contextSha256
        assertNotEquals(afterTempo, changed { project -> project.copy(envelope = project.envelope.copy(compositionSettings = project.envelope.compositionSettings!!.copy(timeSignature = TimeSignature(3, 4)))) })
        val afterMeter = builder.build(root).contextSha256
        assertNotEquals(afterMeter, changed { project -> project.copy(envelope = project.envelope.copy(harmony = harmony(PitchSpelling.G))) })
        val afterHarmony = builder.build(root).contextSha256
        assertNotEquals(afterHarmony, changed { project -> project.copy(envelope = project.envelope.copy(structureOccurrences = project.envelope.structureOccurrences + StructureOccurrence("second", "A"))) })
        val afterStructure = builder.build(root).contextSha256

        val analysisPath = root.resolve("analysis/A.json")
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath))
        Files.writeString(analysisPath, Json.encodeToString(MidiAnalysis.serializer(), analysis.copy(energy = 0.23)))
        assertNotEquals(afterStructure, builder.build(root).contextSha256)
        val afterAnalysis = builder.build(root).contextSha256

        writeMidi(root.resolve("midi/clean/A.mid"), 1_920, pitch = 62)
        val current = ProjectStore.read(root)
        val updatedMidi = canonicalMidiReferences(root, "A")
        ProjectStore.write(root, current.copy(parts = current.parts.map { if (it.id == "A") it.copy(midi = updatedMidi) else it }))
        assertNotEquals(afterAnalysis, builder.build(root).contextSha256)
    }

    @Test
    fun `stale measured duration fails with a recovery action`() {
        val root = project("stale")
        val analysisPath = root.resolve("analysis/A.json")
        val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(analysisPath))
        Files.writeString(analysisPath, Json.encodeToString(MidiAnalysis.serializer(), analysis.copy(durationTicks = analysis.durationTicks - 1)))

        val error = assertFailsWith<IllegalArgumentException> { MusicalAuthorityBuilder().build(root) }

        assertTrue(error.message!!.contains("Run part analyze again"))
    }

    private fun project(name: String, durationTicks: Long = 1_920, occurrences: List<String> = listOf("first")): Path {
        val root = tempDir.resolve(name)
        root.resolve("source").createDirectories(); root.resolve("midi/clean").createDirectories()
        writeMidi(root.resolve("source/A.mid"), durationTicks)
        writeMidi(root.resolve("midi/clean/A.mid"), durationTicks)
        val project = Project(
            name = name,
            parts = listOf(SongPart("A", "source/A.mid", sectionType = SectionTypeId.VERSE, midi = canonicalMidiReferences(root, "A"))),
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(
                compositionSettings = app.melotrail.arrangement.CompositionSettings(
                    key = key(PitchSpelling.C), tempo = Tempo(120.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1),
                    decisionRevision = 1, resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = harmony(PitchSpelling.C),
                structureOccurrences = occurrences.map { StructureOccurrence(it, "A") }
            )
        )
        ProjectStore.write(root, project)
        MidiAnalysisStore.write(root, project, "A", MidiPartAnalyzer().analyze(root.resolve("midi/clean/A.mid"), "A"))
        return root
    }

    private fun harmony(root: PitchSpelling): HarmonySettings = HarmonySettings(progressions = listOf(
        ChordProgression(
            app.melotrail.harmony.SectionTypeId("verse"), listOf(
                ChordEvent(ChordEventId("one"), PitchClass.of(root), ChordQuality.MAJOR, 0),
                ChordEvent(ChordEventId("two"), PitchClass.of(PitchSpelling.D), ChordQuality.MINOR, 1)
            )
        )
    ))

    private fun key(root: PitchSpelling) = MusicalKey(PitchClass.of(root), ScaleModeId.MAJOR)

    private fun writeMidi(path: Path, durationTicks: Long, pitch: Int = 60) {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), durationTicks))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun copyTree(source: Path, destination: Path) {
        Files.walk(source).use { paths -> paths.forEach { path ->
            val target = destination.resolve(source.relativize(path).toString())
            if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
        } }
    }
}
