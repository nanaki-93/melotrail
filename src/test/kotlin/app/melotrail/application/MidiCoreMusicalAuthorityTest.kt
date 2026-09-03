package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiFindingCode
import app.melotrail.midi.domain.MidiFindingSeverity
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.ProjectKey
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreMusicalAuthorityTest {
    @TempDir lateinit var root: Path

    @Test
    fun `explicit fixed authority persists imported timing suggestions and reopens unchanged`() {
        val (store, selection) = imported(fixture("whole-song-one-bar.mid"))
        val key = ProjectKey(ProjectKeySpelling.D_FLAT, ProjectScaleMode.NATURAL_MINOR)

        val result = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(selection, key, ProjectTempo(500_000), ProjectMeter(4, 2)),
            ),
        )

        assertEquals(ProjectTempo(500_000), result.suggestions.tempo)
        assertEquals(ProjectMeter(4, 2), result.suggestions.meter)
        assertEquals(key, requireNotNull(result.session.project.authority).key)
        assertEquals(ProjectTempo(500_000), result.session.project.authority?.tempo)
        assertEquals(ProjectMeter(4, 2), result.session.project.authority?.meter)
        assertEquals(result.session.project, store.openProject(result.session.root))
    }

    @Test
    fun `missing source timing is resolved only by explicit user confirmation`() {
        val (store, selection) = imported(writeSource(root.resolve("input/no-timing.mid"), pitch = 60, timing = false))

        val result = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    selection,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(666_667),
                    ProjectMeter(7, 3),
                ),
            ),
        )

        assertNull(result.suggestions.tempo)
        assertNull(result.suggestions.meter)
        assertEquals(ProjectTempo(666_667), result.session.project.authority?.tempo)
        assertEquals(ProjectMeter(7, 3), result.session.project.authority?.meter)
        assertTrue(result.validation.findings.none { it.code == MidiFindingCode.MISSING_TEMPO || it.code == MidiFindingCode.MISSING_TIME_SIGNATURE })
    }

    @Test
    fun `chromatic melody remains advisory after project key confirmation without source mutation`() {
        val source = writeSource(root.resolve("input/chromatic.mid"), pitch = 61, timing = true)
        val (store, selection) = imported(source)
        val before = Files.readAllBytes(selection.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))

        val result = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    selection,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        )

        assertTrue(result.validation.findings.any { it.code == MidiFindingCode.CHROMATIC_MELODY && it.severity == MidiFindingSeverity.ADVISORY })
        assertContentEquals(before, Files.readAllBytes(result.session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
    }

    @Test
    fun `a source tempo map is rejected before it can become fixed project authority`() {
        val store = MidiCoreArtifactStore()
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle(store).create(CreateMidiCoreProject(root.resolve("project-tempo-map"), "Authority Test", "project-1")),
        ).session
        val source = writeTempoMapSource(root.resolve("input/tempo-map.mid"))
        val before = Files.readAllBytes(created.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = assertIs<MidiCoreSourceImportResult.Rejected>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        )

        assertEquals(MidiCoreSourceImportProblemCode.IMPORT_REJECTED, result.problem.code)
        assertTrue(requireNotNull(result.validation).findings.any { it.code == MidiFindingCode.TEMPO_MAP_UNSUPPORTED })
        assertContentEquals(before, Files.readAllBytes(created.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `typed authority primitives enforce Standard MIDI and enharmonic boundaries`() {
        assertEquals(ProjectTempo(500_000), ProjectTempo.fromBeatsPerMinute(120.0))
        assertEquals(ProjectTempo(652_174), ProjectTempo.fromBeatsPerMinute(92.0))
        assertEquals(92.0, ProjectTempo.fromBeatsPerMinute(92.0).beatsPerMinute, 0.001)
        assertFailsWith<IllegalArgumentException> { ProjectTempo.fromBeatsPerMinute(0.0) }
        assertFailsWith<IllegalArgumentException> { ProjectTempo.fromBeatsPerMinute(Double.NaN) }
        assertEquals(1, ProjectTempo(1).microsecondsPerQuarter)
        assertEquals(0xFF_FF_FF, ProjectTempo(0xFF_FF_FF).microsecondsPerQuarter)
        assertFailsWith<IllegalArgumentException> { ProjectTempo(0) }
        assertFailsWith<IllegalArgumentException> { ProjectTempo(0x1_00_00_00) }
        assertEquals(1_073_741_824L, ProjectMeter(255, 30).denominator)
        assertFailsWith<IllegalArgumentException> { ProjectMeter(0, 2) }
        assertFailsWith<IllegalArgumentException> { ProjectMeter(4, 31) }
        assertEquals(1, ProjectKey(ProjectKeySpelling.D_FLAT, ProjectScaleMode.NATURAL_MINOR).tonic)
        assertFailsWith<IllegalArgumentException> { ProjectKey(1, "major", ProjectKeySpelling.D) }
        assertFailsWith<IllegalArgumentException> { ProjectKey(0, "dorian", ProjectKeySpelling.C) }
    }

    private fun imported(source: Path): Pair<MidiCoreArtifactStore, MidiCoreProjectSession> {
        val store = MidiCoreArtifactStore()
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle(store).create(CreateMidiCoreProject(root.resolve("project-${source.fileName}"), "Authority Test", "project-1")),
        ).session
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        return store to imported
    }

    private fun fixture(filename: String): Path =
        OwnedMidiFixtures.writeAll(root.resolve("fixtures")).first { it.fileName.toString() == filename }

    private fun writeSource(path: Path, pitch: Int, timing: Boolean): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        if (timing) {
            track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0))
            track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        }
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 96), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun writeTempoMapSource(path: Path): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0))
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(6, 26, -128), 3), 240))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun lifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-project" },
    )
}
