package app.melotrail.application

import app.melotrail.arrangement.CompositionSettings
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectV4Envelope
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.SectionTypeId
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.SourceSong
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceSongApplicationServiceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `assembles repeated source MIDI into distinct structured instances with authoritative timing and harmony`() {
        val root = project()

        val artifact = SourceSongApplicationService().assemble(root)
        val repeated = SourceSongApplicationService().assemble(root)
        val assembled = MidiSystem.getSequence(artifact.song.assembledMidi.file.let(root::resolve).toFile())
        val persisted = Json.decodeFromString(SourceSong.serializer(), Files.readString(artifact.metadataPath))

        assertEquals(artifact.song, repeated.song)
        assertEquals(artifact.song, persisted)
        assertEquals(listOf("A1", "A2", "B1", "C1", "B2"), artifact.song.sections.map { "${it.sourcePartId}${it.occurrenceNumber}" })
        assertEquals(listOf("a-one", "a-two", "b-one", "c-one", "b-two"), artifact.song.sections.map { it.instance.instanceId })
        assertEquals(listOf(0L to 1_920L, 1_920L to 3_840L, 3_840L to 5_760L, 5_760L to 7_680L, 7_680L to 9_600L),
            artifact.song.sections.map { it.startTick to it.endTick })
        assertEquals(listOf("verse", "verse", "chorus", "bridge", "chorus"), artifact.song.sections.map { it.sectionRole.value })
        assertEquals(listOf("C", "C", "Dm", "G", "Dm"), artifact.song.sections.map { it.canonicalHarmony.single().rootSymbol + it.canonicalHarmony.single().quality.symbolSuffix })
        assertEquals(480, assembled.resolution)
        assertEquals(9_600L, assembled.tickLength)
        assertEquals(listOf(90), tempoEvents(assembled))
        assertEquals(listOf(4 to 4), meterEvents(assembled))
        assertTrue(artifact.metadataPath.startsWith(root.resolve("source-song")))
    }

    @Test
    fun `source critic persists boundary-addressed blockers and requires a recorded override`() {
        val root = project()
        val critic = DefaultSourceSongCriticApplicationService()

        val report = critic.run(root)

        assertTrue(report.report.issues.isNotEmpty())
        assertTrue(report.report.issues.all { it.location.boundaryId.startsWith("boundary-") && it.location.bar >= 0 })
        assertTrue(report.report.hasBlockingIssues)
        assertFailsWith<IllegalArgumentException> { critic.approve(root) }
        val approval = critic.approve(root, overrideBlockingIssues = true, overrideReason = "Keep the authored chromatic pickup.")

        assertEquals(report.report.connectedMidi.sha256, approval.approval.connectedMidiSha256)
        assertEquals(report.report.issues.filter { it.severity == app.melotrail.arrangement.SourceSongIssueSeverity.BLOCKING }.map { it.id }.sorted(), approval.approval.overriddenBlockingIssueIds.sorted())
        assertEquals(approval.approval, critic.requireApproved(root).approval)
    }

    @Test
    fun `connected source melody resolves as an independent piano preview input`() = runTest {
        val root = project()
        val renderer = CapturingRenderer()

        val result = DefaultPartPreviewApplicationService(renderer).resolveConnectedSource(root)

        assertTrue(result is PreviewResult.Prerequisite, "Expected connected-source preview to reach the renderer: $result")
        assertTrue(renderer.input?.startsWith(root.resolve("source-song")) == true)
        assertTrue(renderer.input?.fileName.toString() == "connected.mid")
    }

    private fun project(): Path {
        val root = tempDir.resolve("source-song")
        root.resolve("source").createDirectories(); root.resolve("midi/clean").createDirectories()
        val definitions = listOf(
            Triple("A", SectionTypeId.VERSE, PitchSpelling.C),
            Triple("B", SectionTypeId.CHORUS, PitchSpelling.D),
            Triple("C", SectionTypeId.BRIDGE, PitchSpelling.G)
        )
        definitions.forEachIndexed { index, (id, _, _) ->
            writeMidi(root.resolve("source/$id.mid"), 60 + index, 60.0, 3, 4)
            writeMidi(root.resolve("midi/clean/$id.mid"), 60 + index, 60.0, 3, 4)
        }
        val project = Project(
            name = "structured-source",
            parts = definitions.map { (id, role, _) -> SongPart(id, "source/$id.mid", sectionType = role, midi = canonicalMidiReferences(root, id)) },
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(
                compositionSettings = CompositionSettings(
                    key = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), tempo = Tempo(90.0), timeSignature = TimeSignature(4, 4),
                    profile = CompositionProfileRef("lofi", 1), mood = MoodRef("warm", 1), decisionRevision = 1,
                    resolvedProfileSha256 = "a".repeat(64), decisionSha256 = "b".repeat(64)
                ),
                harmony = HarmonySettings(progressions = definitions.map { (_, role, chord) ->
                    ChordProgression(app.melotrail.harmony.SectionTypeId(role.value), listOf(
                        ChordEvent(ChordEventId("${role.value}-one"), PitchClass.of(chord), if (role == SectionTypeId.CHORUS) ChordQuality.MINOR else ChordQuality.MAJOR, 0)
                    ))
                }),
                structureOccurrences = listOf(
                    StructureOccurrence("a-one", "A"), StructureOccurrence("a-two", "A"), StructureOccurrence("b-one", "B"),
                    StructureOccurrence("c-one", "C"), StructureOccurrence("b-two", "B")
                )
            )
        )
        ProjectStore.write(root, project)
        project.parts.forEach { part ->
            val current = ProjectStore.read(root)
            MidiAnalysisStore.write(root, current, part.id, MidiPartAnalyzer().analyze(root.resolve("midi/clean/${part.id}.mid"), part.id))
        }
        return root
    }

    private fun writeMidi(path: Path, pitch: Int, bpm: Double, numerator: Int, denominator: Int) {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        val micros = (60_000_000.0 / bpm).toInt()
        track.add(MidiEvent(MetaMessage().also { it.setMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }, 0))
        track.add(MidiEvent(MetaMessage().also { it.setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }, 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private inner class CapturingRenderer : InstrumentRenderer {
        var input: Path? = null
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            input = midi
            error("renderer unavailable")
        }
    }

    private fun tempoEvents(sequence: Sequence): List<Int> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .mapNotNull { it.message as? MetaMessage }.filter { it.type == 0x51 }.map { message ->
            val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
            (60_000_000.0 / micros).toInt()
        }

    private fun meterEvents(sequence: Sequence): List<Pair<Int, Int>> = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
        .mapNotNull { it.message as? MetaMessage }.filter { it.type == 0x58 }.map { it.data[0].toInt() and 0xff to (1 shl (it.data[1].toInt() and 0xff)) }
}
