package app.melotrail.application

import app.melotrail.audition.MidiAuditionScope
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
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
    fun `prepares automatically protected immutable source melody as one MIDI audition role`() {
        val store = MidiCoreArtifactStore()
        val imported = imported(store, "whole-song-one-bar.mid")

        val result = assertIs<MidiCoreSourceAuditionResult.Ready>(
            MidiCoreSourceAudition(store).prepare(PrepareMidiCoreSourceAudition(imported)),
        )

        assertEquals(MidiAuditionScope.SourceMelody, result.plan.view.scope)
        assertEquals(listOf(MidiExportRole.MELODY), result.plan.view.roles)
        assertEquals(imported.project.sourceMidi?.sourceEndTick, result.plan.view.song.songEndTick)
        assertTrue(result.plan.view.song.role(MidiExportRole.MELODY).events.isNotEmpty())
    }

    @Test
    fun `source audition rejects a changed preserved artifact`() {
        val store = MidiCoreArtifactStore()
        val imported = imported(store, "whole-song-one-bar.mid")
        val sourcePath = imported.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)
        Files.write(sourcePath, Files.readAllBytes(sourcePath) + byteArrayOf(0x01))

        val changed = assertIs<MidiCoreSourceAuditionResult.Rejected>(
            MidiCoreSourceAudition(store).prepare(PrepareMidiCoreSourceAudition(imported)),
        )
        assertEquals(MidiCoreSourceAuditionProblemCode.INVALID_PROJECT, changed.problem.code)
    }

    @Test
    fun `prepares one exact saved occurrence window without rewriting melody ticks`() {
        val store = MidiCoreArtifactStore()
        val imported = imported(store, "whole-song-one-bar.mid")
        val confirmed = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    imported,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        ).session
        val sourceEnd = requireNotNull(confirmed.project.sourceMidi).sourceEndTick
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    confirmed,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1)),
                ),
            ),
        ).session

        val result = assertIs<MidiCoreSourceAuditionResult.Ready>(
            MidiCoreSourceAudition(store).prepareOccurrence(
                PrepareMidiCoreOccurrenceAudition(structured, "verse-1"),
            ),
        )

        assertEquals(MidiAuditionScope.Occurrence("verse-1"), result.plan.view.scope)
        assertEquals(0L, result.plan.view.window.startTick)
        assertEquals(sourceEnd, result.plan.view.window.endTick)
        assertEquals(listOf(MidiExportRole.MELODY), result.plan.view.roles)
        assertEquals(
            structured.project.sourceMidi?.sourceEndTick,
            result.plan.view.song.songEndTick,
        )
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
