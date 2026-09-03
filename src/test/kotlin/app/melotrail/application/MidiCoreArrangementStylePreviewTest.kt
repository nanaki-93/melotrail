package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArrangementStylePreviewTest {
    @TempDir lateinit var root: Path

    @Test
    fun `catalog has stable readable all-role bundles`() {
        val styles = MidiCoreArrangementStyleCatalog.styles

        assertEquals(1, MidiCoreArrangementStyleCatalog.VERSION)
        assertEquals(listOf("open-sky", "late-night", "steady-road", "rising-room", "wide-bridge"), styles.map { it.id })
        styles.forEach { style ->
            assertEquals(app.melotrail.project.CandidateRole.entries, style.roles.map { it.role })
            style.roles.forEach { choice ->
                assertTrue(choice.performanceProfileId.isNotBlank())
                assertTrue(choice.patternId.isNotBlank())
            }
        }
    }

    @Test
    fun `preview is deterministic bounded and never mutates project or artifacts`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, "whole-song-three-bars.mid", 3)
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val beforeProject = Files.readAllBytes(projectFile)
        val beforePaths = Files.walk(session.root).use { paths -> paths.map { it.toAbsolutePath().normalize() }.sorted().toList() }
        val preview = MidiCoreArrangementStylePreview(artifacts = store)
        val request = PrepareMidiCoreArrangementStylePreview(session, "late-night", "verse-1", seed = 918L)

        val cold = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(preview.prepare(request))
        val warm = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(preview.prepare(request))

        assertEquals(MidiCoreArrangementStylePreviewCacheStatus.COLD, cold.cacheStatus)
        assertEquals(MidiCoreArrangementStylePreviewCacheStatus.WARM, warm.cacheStatus)
        assertEquals(cold.key, warm.key)
        assertEquals(0L, cold.plan.view.window.startTick)
        assertEquals(5760L, cold.plan.view.window.endTick)
        assertEquals(MidiExportRole.entries, cold.plan.view.roles)
        assertEquals(cold.plan.view.song, warm.plan.view.song)
        assertEquals(cold.plan.view.window, cold.plan.loop?.asWindow())
        assertTrue(cold.validation.all { it.passed })
        cold.plan.view.song.roles.flatMap { it.events }.forEach { event ->
            assertTrue(event.orderingKey.tick >= cold.plan.view.window.startTick)
            val end = (event as? app.melotrail.midi.domain.MidiNoteEvent)?.endTick ?: event.orderingKey.tick
            assertTrue(end <= cold.plan.view.window.endTick)
        }
        assertContentEquals(beforeProject, Files.readAllBytes(projectFile))
        assertEquals(session.project, store.openProject(session.root))
        val afterPaths = Files.walk(session.root).use { paths -> paths.map { it.toAbsolutePath().normalize() }.sorted().toList() }
        assertEquals(beforePaths, afterPaths)
        assertEquals(MidiCoreArrangementStylePreviewCacheStats(1, 1, 1), preview.cacheStats())
    }

    @Test
    fun `style authority and seed form independent preview cache identities`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, "whole-song-three-bars.mid", 3)
        val preview = MidiCoreArrangementStylePreview(artifacts = store)

        val first = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(
            preview.prepare(PrepareMidiCoreArrangementStylePreview(session, "open-sky", "verse-1", 1L)),
        )
        val changedStyle = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(
            preview.prepare(PrepareMidiCoreArrangementStylePreview(session, "rising-room", "verse-1", 1L)),
        )
        val changedSeed = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(
            preview.prepare(PrepareMidiCoreArrangementStylePreview(session, "open-sky", "verse-1", 2L)),
        )
        val changedProject = session.project.copy(
            authority = requireNotNull(session.project.authority).copy(
                key = ProjectKey(ProjectKeySpelling.G, ProjectScaleMode.NATURAL_MINOR),
            ),
            revision = session.project.revision + 1L,
        )
        store.saveProject(session.root, changedProject)
        val changedAuthority = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(
            preview.prepare(
                PrepareMidiCoreArrangementStylePreview(MidiCoreProjectSession(session.root, changedProject), "open-sky", "verse-1", 1L),
            ),
        )

        assertFalse(first.key == changedStyle.key)
        assertFalse(first.key == changedSeed.key)
        assertFalse(first.key.authorityHash == changedAuthority.key.authorityHash)
        assertEquals(4, preview.cacheStats().entries)
    }

    @Test
    fun `preview rejects a one-bar occurrence without any state mutation`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, "whole-song-one-bar.mid", 1)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = MidiCoreArrangementStylePreview(artifacts = store).prepare(
            PrepareMidiCoreArrangementStylePreview(session, "open-sky", "verse-1"),
        )

        val rejected = assertIs<MidiCoreArrangementStylePreviewResult.Rejected>(result)
        assertEquals(MidiCoreArrangementStylePreviewProblemCode.WINDOW_TOO_SHORT, rejected.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(session.project, store.openProject(session.root))
    }

    private fun readySession(store: MidiCoreArtifactStore, fixtureName: String, bars: Int): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store, idFactory = { "style-preview-project" }).create(
                CreateMidiCoreProject(root.resolve("project-$bars"), "Style Preview", "style-preview-project"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-$bars")).single { it.fileName.toString() == fixtureName }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    imported,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        ).session
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    authority,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", bars)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, bars * 1920L)),
                ),
            ),
        ).session
    }
}
