package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreRoleActivity
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
    fun `unsupported comping meter returns a preview rejection without writes`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val original = readySession(store, "whole-song-three-bars.mid", 3)
        val session = original.copy(project = original.project.copy(
            authority = requireNotNull(original.project.authority).copy(meter = ProjectMeter(2, 2)),
        ))
        store.saveProject(session.root, session.project)
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectFile)
        val result = assertIs<MidiCoreArrangementStylePreviewResult.Rejected>(
            MidiCoreArrangementStylePreview(artifacts = store).prepare(
                PrepareMidiCoreArrangementStylePreview(session, "steady-road", "verse-1", 41L),
            ),
        )
        assertTrue(result.toString().contains("Piano comping does not support 2/4"), result.toString())
        assertContentEquals(before, Files.readAllBytes(projectFile))
        assertEquals(session.project, store.openProject(session.root))
    }

    @Test
    fun `catalog has stable readable all-role bundles`() {
        val styles = MidiCoreArrangementStyleCatalog.styles

        assertEquals(5, MidiCoreArrangementStyleCatalog.VERSION)
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
    fun `preview loops one real bar without any state mutation`() = runBlocking {
        val store = MidiCoreArtifactStore()
        // This source has one real 4/4 bar and its sole protected note ends on
        // the boundary, so the assertion exercises the bounded loop rather
        // than a deliberately unplayable melody/voicing collision.
        val session = readySession(store, "final-boundary-note.mid", 1)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = MidiCoreArrangementStylePreview(artifacts = store).prepare(
            // Keep this boundary regression independent of a style-specific
            // short-section rejection; this style/seed is proven for one-bar scopes.
            PrepareMidiCoreArrangementStylePreview(session, "steady-road", "verse-1", 41L),
        )

        val ready = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(result, result.toString())
        assertEquals(0L, ready.plan.view.window.startTick)
        assertEquals(1_920L, ready.plan.view.window.endTick)
        assertEquals(ready.plan.view.window, ready.plan.loop?.asWindow())
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(session.project, store.openProject(session.root))
    }

    @Test
    fun `style preview resolves the same plan before and after explicit confirmation`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, "whole-song-three-bars.mid", 3, splitBars = true)
        val request = PrepareMidiCoreArrangementStylePreview(session, "late-night", "verse-1", 41L)
        val preview = MidiCoreArrangementStylePreview(artifacts = store)

        val ephemeral = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(preview.prepare(request))
        assertEquals(MidiCoreArrangementStylePreviewPlanState.EPHEMERAL_STYLE_PROPOSAL, ephemeral.planState)
        assertTrue(ephemeral.plan.view.song.role(MidiExportRole.BASS).events.isEmpty())
        assertTrue(ephemeral.plan.view.song.role(MidiExportRole.DRUMS).events.isEmpty())

        val proposals = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            proposals.propose(ProposeMidiCoreArrangementPlan(session, "late-night")),
        ).proposal
        val confirmed = assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(
            proposals.confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal)),
        ).session
        val persisted = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(
            preview.prepare(request.copy(session = confirmed)),
        )

        assertEquals(MidiCoreArrangementStylePreviewPlanState.CONFIRMED, persisted.planState)
        val confirmedOccurrence = requireNotNull(confirmed.project.arrangementPlan).occurrences
            .single { it.occurrenceId == "verse-1" }
        assertEquals(
            setOf(CandidateRole.BASS, CandidateRole.DRUMS),
            confirmedOccurrence.roleSettings.filter { it.activity == MidiCoreRoleActivity.INACTIVE }.map { it.role }.toSet(),
        )
        assertEquals(ephemeral.key.authorityHash, persisted.key.authorityHash)
        assertEquals(ephemeral.plan.view.song, persisted.plan.view.song)
        assertEquals(ephemeral.plan.loop, persisted.plan.loop)

        // Confirmation may reuse the identical resolved plan, but an actual plan edit
        // must invalidate the warm preview and preserve planned silence.
        assertEquals(MidiCoreArrangementStylePreviewCacheStatus.WARM, persisted.cacheStatus)
        val silentPlan = requireNotNull(confirmed.project.arrangementPlan).let { plan ->
            plan.copy(occurrences = plan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId != "verse-1") occurrence else occurrence.copy(
                    roleSettings = occurrence.roleSettings.map { it.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0) },
                )
            })
        }
        val edited = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(confirmed, silentPlan)),
        ).session
        val silent = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(preview.prepare(request.copy(session = edited)))
        assertEquals(MidiCoreArrangementStylePreviewCacheStatus.COLD, silent.cacheStatus)
        assertFalse(silent.key.authorityHash == persisted.key.authorityHash)
        listOf(MidiExportRole.CHORDS, MidiExportRole.BASS, MidiExportRole.DRUMS).forEach { role ->
            assertTrue(silent.plan.view.song.role(role).events.isEmpty())
        }
        assertEquals(persisted.plan.view.song.role(MidiExportRole.MELODY), silent.plan.view.song.role(MidiExportRole.MELODY))
    }

    @Test
    fun `style preview preparation stays within cold and warm p95 budgets`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, "whole-song-three-bars.mid", 3)
        val preview = MidiCoreArrangementStylePreview(artifacts = store)
        val requests = (1L..8L).map { seed ->
            PrepareMidiCoreArrangementStylePreview(session, "late-night", "verse-1", seed)
        }

        val coldMillis = requests.map { request ->
            val elapsed = measureMillis { preview.prepare(request) }
            assertEquals(MidiCoreArrangementStylePreviewCacheStatus.COLD, elapsed.value.cacheStatus)
            elapsed.millis
        }
        val warmMillis = requests.map { request ->
            val elapsed = measureMillis { preview.prepare(request) }
            assertEquals(MidiCoreArrangementStylePreviewCacheStatus.WARM, elapsed.value.cacheStatus)
            elapsed.millis
        }
        val coldP95 = p95(coldMillis)
        val warmP95 = p95(warmMillis)

        // This is end-to-end preparation through the in-memory audition plan, not a claim about a user's audio hardware.
        println("MC-048I preview preparation: cold p95=${coldP95}ms $coldMillis; warm p95=${warmP95}ms $warmMillis")
        assertTrue(coldP95 <= 1_000L, "Cold preview p95 was ${coldP95}ms; budget is 1000ms. Samples: $coldMillis")
        assertTrue(warmP95 <= 300L, "Warm preview p95 was ${warmP95}ms; budget is 300ms. Samples: $warmMillis")
    }

    private fun readySession(store: MidiCoreArtifactStore, fixtureName: String, bars: Int, splitBars: Boolean = false): MidiCoreProjectSession {
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
                    if (splitBars) (1..bars).map { MidiCoreBarOccurrencePlacement("verse-$it", "verse", "Verse $it", 1) }
                    else listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", bars)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    if (splitBars) (1..bars).map { AuthoritativeChordEvent("chord-$it", "verse-$it", "C", (it - 1) * 1920L, it * 1920L) }
                    else listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, bars * 1920L)),
                ),
            ),
        ).session
    }

    private suspend fun measureMillis(block: suspend () -> MidiCoreArrangementStylePreviewResult): TimedPreview {
        val startedAt = System.nanoTime()
        val result = assertIs<MidiCoreArrangementStylePreviewResult.Ready>(block())
        return TimedPreview(result, (System.nanoTime() - startedAt) / 1_000_000L)
    }

    private fun p95(samples: List<Long>): Long = samples.sorted()[((samples.size * 95 + 99) / 100 - 1).coerceIn(0, samples.lastIndex)]

    private data class TimedPreview(val value: MidiCoreArrangementStylePreviewResult.Ready, val millis: Long)
}
