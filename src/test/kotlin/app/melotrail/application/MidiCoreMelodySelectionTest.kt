package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreMelodySelectionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `selects exactly one SMF1 source track and channel into a channel-1-ready view`() {
        val (store, session) = imported("smf1-reference-tracks.mid")

        val result = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(session, 1, 0)),
        )

        assertEquals(MidiImportDisposition.ACCEPTED, result.validation.disposition)
        assertEquals(1, result.view.sourceTrackIndex)
        assertEquals(0, result.view.sourceChannel)
        assertEquals(1, result.view.notes.size)
        assertTrue(result.view.protectedAnchorIds.isNotEmpty())
        assertTrue(result.view.events.all { outputChannel(it) == 0 })
        assertEquals(result.view.identitySha256, requireNotNull(result.session.project.selectedMelody).identitySha256)
        assertEquals(result.session.project, store.openProject(result.session.root))
    }

    @Test
    fun `resolves format zero selection on its one source track`() {
        val (store, session) = imported("smf0-melody.mid")

        val result = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(session, 0, 0)),
        )

        assertEquals(0, result.view.sourceTrackIndex)
        assertEquals(0, result.view.sourceChannel)
        assertEquals(1, result.view.notes.size)
    }

    @Test
    fun `maps selected source controllers and expression to export channel one`() {
        val (store, session) = imported("expressive-controller-pitch.mid")

        val result = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(session, 1, 0)),
        )

        assertEquals(2, result.view.events.filterIsInstance<MidiControlChangeEvent>().size)
        assertEquals(1, result.view.events.filterIsInstance<MidiPitchBendEvent>().size)
        assertEquals(0, result.view.events.filterIsInstance<MidiChannelPressureEvent>().size)
        assertTrue(result.view.events.all { outputChannel(it) == 0 })
    }

    @Test
    fun `selection derives a semantic view without changing preserved source bytes`() {
        val (store, session) = imported("expressive-controller-pitch.mid")
        val sourcePath = session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)
        val beforeBytes = Files.readAllBytes(sourcePath)
        val reader = JdkMidiReader()
        val beforeSemantic = reader.inspect(sourcePath).sequence.orderedEvents().map(Any::toString)

        val result = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(session, 1, 0)),
        )

        assertContentEquals(beforeBytes, Files.readAllBytes(sourcePath))
        assertEquals(beforeSemantic, reader.inspect(sourcePath).sequence.orderedEvents().map(Any::toString))
        assertNotEquals(beforeSemantic, result.view.events.map(Any::toString))
    }

    @Test
    fun `selection can change before derived work exists and changes the durable melody identity`() {
        val (store, session) = imported("smf1-reference-tracks.mid")
        val selection = MidiCoreMelodySelection(store)
        val first = assertIs<MidiCoreMelodySelectionResult.Selected>(selection.select(SelectMidiCoreMelody(session, 1, 0)))

        val changed = assertIs<MidiCoreMelodySelectionResult.Selected>(selection.select(SelectMidiCoreMelody(first.session, 2, 1)))

        assertEquals(2, requireNotNull(changed.session.project.selectedMelody).trackIndex)
        assertEquals(1, changed.session.project.selectedMelody?.channel)
        assertNotEquals(first.view.identitySha256, changed.view.identitySha256)
        assertEquals(changed.session.project, store.openProject(changed.session.root))
    }

    @Test
    fun `rejects MPE-like multi-channel expression without changing project selection`() {
        val store = MidiCoreArtifactStore()
        val source = writeMpeLikeSource(root.resolve("input/mpe-like.mid"))
        val session = imported(store, source, root.resolve("project"))
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = assertIs<MidiCoreMelodySelectionResult.Rejected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(session, 0, 0)),
        )

        assertEquals(MidiCoreMelodySelectionProblemCode.UNSUPPORTED_MPE_LIKE_EXPRESSION, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertFalse(Files.exists(session.root.resolve("candidates")))
    }

    private fun imported(filename: String): Pair<MidiCoreArtifactStore, MidiCoreProjectSession> {
        val store = MidiCoreArtifactStore()
        val source = OwnedMidiFixtures.writeAll(root.resolve("input-$filename")).first { it.fileName.toString() == filename }
        return store to imported(store, source, root.resolve("project-$filename"))
    }

    private fun imported(store: MidiCoreArtifactStore, source: Path, projectRoot: Path): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle(store).create(CreateMidiCoreProject(projectRoot, "Melody Test", "project-1")),
        ).session
        return assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
    }

    private fun writeMpeLikeSource(path: Path): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(MetaMessage(0x51, byteArrayOf(7, -95, 32), 3), 0))
        track.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        note(track, 0, 60, 0, 480)
        note(track, 1, 64, 0, 480)
        track.add(MidiEvent(ShortMessage(ShortMessage.PITCH_BEND, 0, 0, 72), 120))
        track.add(MidiEvent(ShortMessage(ShortMessage.PITCH_BEND, 1, 0, 72), 120))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun note(track: javax.sound.midi.Track, channel: Int, pitch: Int, start: Long, end: Long) {
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, 96), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), end))
    }

    private fun outputChannel(event: Any): Int = when (event) {
        is MidiNoteEvent -> event.channel
        is MidiControlChangeEvent -> event.channel
        is MidiPitchBendEvent -> event.channel
        is MidiChannelPressureEvent -> event.channel
        else -> error("Unexpected melody view event: $event")
    }

    private fun lifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-project" },
    )
}
