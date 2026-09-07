package app.melotrail.application

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArrangementExtentTest {
    @TempDir lateinit var root: Path

    @Test
    fun `non-bar source end can be padded cancelled and reopened without changing source bytes`() {
        val store = MidiCoreArtifactStore()
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle(store).create(CreateMidiCoreProject(root.resolve("project"), "Extent", "extent-project"))).session
        val input = sourceWithTrailingEndOfTrack(root.resolve("input.mid"))
        val inputBytes = Files.readAllBytes(input)
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, input))).session
        val source = requireNotNull(imported.project.sourceMidi)
        assertEquals(1_700L, source.lastNoteEndTick)
        assertEquals(1_800L, source.sourceEndTick)

        val authoritative = assertIs<MidiCoreAuthorityResult.Confirmed>(MidiCoreMusicalAuthority(store).confirm(
            ConfirmMidiCoreAuthority(imported, ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR), ProjectTempo(500_000), ProjectMeter(4, 2)),
        )).session
        val unchanged = assertIs<MidiCoreArrangementExtentResult.Confirmed>(MidiCoreArrangementExtent(store).confirm(ConfirmMidiCoreArrangementExtent(authoritative, false)))
        assertEquals(authoritative.project.revision, unchanged.session.project.revision)
        assertFalse(unchanged.invalidation.hasImpact)

        val padded = assertIs<MidiCoreArrangementExtentResult.Confirmed>(MidiCoreArrangementExtent(store).confirm(ConfirmMidiCoreArrangementExtent(authoritative, true)))
        assertEquals(1_920L, padded.arrangementEndTick)
        assertEquals(120L, padded.paddingTicks)
        assertTrue(padded.invalidation.hasImpact)
        assertEquals(listOf(app.melotrail.project.MidiCoreAuthorityDimension.TIMING), padded.invalidation.changedDimensions)
        assertEquals(1_920L, requireNotNull(padded.session.project.authority).arrangementEndTick)
        assertContentEquals(inputBytes, Files.readAllBytes(padded.session.root.resolve(source.original.path.value)))

        val cancelled = assertIs<MidiCoreArrangementExtentResult.Confirmed>(MidiCoreArrangementExtent(store).confirm(ConfirmMidiCoreArrangementExtent(padded.session, false)))
        assertEquals(1_800L, cancelled.arrangementEndTick)
        val reopened = assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle(store).open(cancelled.session.root)).session
        assertEquals(1_700L, requireNotNull(reopened.project.sourceMidi).lastNoteEndTick)
        assertEquals(1_800L, reopened.project.sourceMidi?.sourceEndTick)
        assertEquals(1_800L, reopened.project.authority?.arrangementEndTick)
        assertContentEquals(inputBytes, Files.readAllBytes(reopened.root.resolve(source.original.path.value)))
    }

    private fun sourceWithTrailingEndOfTrack(path: Path): Path {
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_700))
        track.add(MidiEvent(MetaMessage(0x2f, byteArrayOf(), 0), 1_800))
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
        return path
    }

    private fun lifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "extent-project" },
    )
}
