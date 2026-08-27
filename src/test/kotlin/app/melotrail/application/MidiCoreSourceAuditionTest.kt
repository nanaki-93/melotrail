package app.melotrail.application

import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreSourceAuditionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `prepares selected immutable source melody as one MIDI audition role`() {
        val store = MidiCoreArtifactStore()
        val imported = imported(store, "smf0-melody.mid")
        val selected = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(imported, 0, 0)),
        )

        val result = assertIs<MidiCoreSourceAuditionResult.Ready>(
            MidiCoreSourceAudition(store).prepare(PrepareMidiCoreSourceAudition(selected.session)),
        )

        assertEquals(MidiAuditionScope.SourceMelody, result.plan.view.scope)
        assertEquals(listOf(MidiExportRole.MELODY), result.plan.view.roles)
        assertEquals(selected.session.project.sourceMidi?.sourceEndTick, result.plan.view.song.songEndTick)
        assertTrue(result.plan.view.song.role(MidiExportRole.MELODY).events.isNotEmpty())
    }

    @Test
    fun `source audition requires selection and rejects a changed preserved artifact`() {
        val store = MidiCoreArtifactStore()
        val imported = imported(store, "smf0-melody.mid")
        val missingSelection = assertIs<MidiCoreSourceAuditionResult.Rejected>(
            MidiCoreSourceAudition(store).prepare(PrepareMidiCoreSourceAudition(imported)),
        )
        assertEquals(MidiCoreSourceAuditionProblemCode.MELODY_REQUIRED, missingSelection.problem.code)

        val selected = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(imported, 0, 0)),
        )
        val sourcePath = selected.session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)
        Files.write(sourcePath, Files.readAllBytes(sourcePath) + byteArrayOf(0x01))

        val changed = assertIs<MidiCoreSourceAuditionResult.Rejected>(
            MidiCoreSourceAudition(store).prepare(PrepareMidiCoreSourceAudition(selected.session)),
        )
        assertEquals(MidiCoreSourceAuditionProblemCode.INVALID_PROJECT, changed.problem.code)
    }

    private fun imported(store: MidiCoreArtifactStore, filename: String): MidiCoreProjectSession {
        val source = OwnedMidiFixtures.writeAll(root.resolve("input-$filename")).first { it.fileName.toString() == filename }
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(
                store,
                clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC),
                idFactory = { "source-audition-project" },
            ).create(CreateMidiCoreProject(root.resolve("project"), "Source audition")),
        ).session
        return assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
    }
}
