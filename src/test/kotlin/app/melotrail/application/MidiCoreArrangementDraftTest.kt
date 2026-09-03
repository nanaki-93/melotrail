package app.melotrail.application

import app.melotrail.audition.MidiAuditionScope
import app.melotrail.arrangement.core.MidiCoreSectionPolicy
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArrangementDraftTest {
    @TempDir lateinit var root: Path

    @Test
    fun `complete style draft persists deterministic all-role evidence and auditions before acceptance`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val beforeSource = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val generated = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store, draftIdFactory = { "draft-style-1" }).generate(
                GenerateMidiCoreArrangementDraft(session, "late-night", rootSeed = 904L),
            ),
        )

        val draft = generated.draft
        assertEquals("draft-style-1", draft.id)
        assertEquals(3, draft.validation.scopeCount)
        assertEquals(
            listOf(CandidateRole.CHORDS, CandidateRole.BASS, CandidateRole.DRUMS),
            draft.candidateReferences.map { it.role },
        )
        assertEquals(emptyList(), generated.session.project.acceptances)
        assertEquals(3, generated.session.project.candidates.size)
        val candidates = generated.session.project.candidates.associateBy { it.role }
        assertEquals(emptyList(), candidates.getValue(CandidateRole.CHORDS).draftDependencyIds)
        assertEquals(listOf(candidates.getValue(CandidateRole.CHORDS).id), candidates.getValue(CandidateRole.BASS).draftDependencyIds)
        assertEquals(
            listOf(candidates.getValue(CandidateRole.CHORDS).id, candidates.getValue(CandidateRole.BASS).id),
            candidates.getValue(CandidateRole.DRUMS).draftDependencyIds,
        )
        assertContentEquals(beforeSource, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))

        val reopened = store.openProject(generated.session.root)
        assertEquals(generated.session.project, reopened)
        assertEquals(generated.session.project, MidiCoreProjectSchema.decode(MidiCoreProjectSchema.encode(reopened)))
        val reused = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(generated.session, "late-night", 904L, draftId = draft.id),
            ),
        )
        assertEquals(generated.session.project.revision, reused.session.project.revision)
        assertEquals(draft, reused.draft)
        val audition = assertIs<MidiCoreReviewAuditionResult.Ready>(
            MidiCoreReviewAudition(assembly = MidiCoreAcceptedSongAssembly(artifacts = store)).draft(
                PrepareMidiCoreArrangementDraftAudition(generated.session, draft.id),
            ),
        )
        assertEquals(MidiAuditionScope.ArrangementDraft(draft.id), audition.plan.view.scope)
        assertEquals(MidiExportRole.entries, audition.plan.view.roles)
        assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(generated.session)),
        )
    }

    @Test
    fun `cancelled draft retains completed scope and retry uses it before atomic acceptance`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val cancellation = AtomicBoolean(false)
        val first = MidiCoreArrangementDraftGeneration(artifacts = store).generate(
            GenerateMidiCoreArrangementDraft(
                session = session,
                styleId = "open-sky",
                rootSeed = 12L,
                draftId = "draft-retry-1",
                cancellation = MidiCoreGenerationCancellation { cancellation.get() },
                onProgress = { progress -> if (progress.completedCount == 1) cancellation.set(true) },
            ),
        )
        val cancelled = assertIs<MidiCoreArrangementDraftGenerationResult.Cancelled>(first)
        assertEquals(1, cancelled.progress.completedCount)
        assertEquals(1, cancelled.session.project.candidates.size)
        assertTrue(cancelled.session.project.arrangementDrafts.isEmpty())
        val retained = cancelled.session.project.candidates.single()
        val retried = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(cancelled.session, "open-sky", 12L, draftId = cancelled.draftId),
            ),
        )
        assertEquals(3, retried.session.project.candidates.size)
        assertEquals(retained, retried.session.project.candidates.single { it.id == retained.id })

        val beforeRevision = retried.session.project.revision
        val historyIds = AtomicInteger(0)
        val accepted = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store, idFactory = { "draft-accept-1" }, historyIdFactory = { "accept-${historyIds.incrementAndGet()}" }).use(
                UseMidiCoreArrangementDraft(retried.session, retried.draft.id),
            ),
        )
        assertEquals(beforeRevision + 1L, accepted.session.project.revision)
        assertEquals(3, accepted.session.project.acceptances.size)
        assertTrue(accepted.session.project.candidates.all { it.status == app.melotrail.project.MidiCoreCandidateStatus.ACCEPTED })
        assertEquals(emptyList(), accepted.history.previousAcceptances)
        assertEquals(3, accepted.history.appliedAcceptances.size)
        assertEquals(1, accepted.session.project.arrangementDraftAcceptanceHistory.size)
        assertIs<MidiCoreAcceptedSongAssemblyResult.Assembled>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(accepted.session)),
        )

        val laterScopedChange = accepted.session.project.copy(
            acceptances = accepted.session.project.acceptances.map { it.copy(locked = true) },
            revision = accepted.session.project.revision + 1L,
        )
        store.saveProject(accepted.session.root, laterScopedChange)
        val beforeRejectedUndo = Files.readAllBytes(accepted.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val rejectedUndo = assertIs<MidiCoreArrangementDraftAcceptanceUndoResult.Rejected>(
            MidiCoreArrangementDraftAcceptanceUndo(artifacts = store).undo(
                UndoMidiCoreArrangementDraftAcceptance(MidiCoreProjectSession(accepted.session.root, laterScopedChange), accepted.history.id),
            ),
        )
        assertEquals(MidiCoreArrangementDraftProblemCode.REVISION_CONFLICT, rejectedUndo.problem.code)
        assertContentEquals(beforeRejectedUndo, Files.readAllBytes(accepted.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        store.saveProject(accepted.session.root, accepted.session.project)

        val undone = assertIs<MidiCoreArrangementDraftAcceptanceUndoResult.Applied>(
            MidiCoreArrangementDraftAcceptanceUndo(artifacts = store, historyIdFactory = { "undo-draft-accept-1" }).undo(
                UndoMidiCoreArrangementDraftAcceptance(accepted.session, accepted.history.id),
            ),
        )
        assertEquals(beforeRevision + 2L, undone.session.project.revision)
        assertEquals(emptyList(), undone.session.project.acceptances)
        assertTrue(undone.session.project.candidates.all { it.status == app.melotrail.project.MidiCoreCandidateStatus.CURRENT })
        assertEquals(emptyList(), undone.session.project.arrangementDraftAcceptanceHistory)
        assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(undone.session)),
        )
    }

    @Test
    fun `invalid style does not mutate project or publish a partial draft`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = MidiCoreArrangementDraftGeneration(artifacts = store).generate(
            GenerateMidiCoreArrangementDraft(session, "missing-style", 1L, draftId = "draft-invalid-1"),
        )

        val incomplete = assertIs<MidiCoreArrangementDraftGenerationResult.Incomplete>(result)
        assertEquals(MidiCoreArrangementDraftProblemCode.STYLE_NOT_FOUND, incomplete.problem.code)
        assertFalse(Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)).isEmpty())
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(session.project, store.openProject(session.root))
    }

    @Test
    fun `locked replacement and changed authority reject draft acceptance without a partial write`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val generated = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(readySession(store), "steady-road", 33L, draftId = "draft-locked-1"),
            ),
        )
        val alternative = assertIs<MidiCoreCandidateGenerationResult.Published>(
            MidiCoreCandidateGeneration(artifacts = store).generate(
                GenerateMidiCoreCandidate(
                    session = generated.session,
                    role = CandidateRole.CHORDS,
                    occurrenceId = "verse-1",
                    performanceProfileId = "chords.sustained",
                    patternId = "chords.rhythm.sustained",
                    generator = MidiCoreGeneratorInput("draft-test", "draft-test-v1", "chords.rhythm.sustained", 991L),
                    sectionPolicy = MidiCoreSectionPolicy(),
                    candidateId = "locked-alternative",
                ),
            ),
        )
        val locked = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            MidiCoreCandidateLifecycle(artifacts = store).accept(
                AcceptMidiCoreCandidate(alternative.session, alternative.candidate.id, locked = true),
            ),
        ).session
        val before = Files.readAllBytes(locked.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = MidiCoreArrangementDraftAcceptance(artifacts = store).use(
            UseMidiCoreArrangementDraft(locked, generated.draft.id),
        )

        val rejected = assertIs<MidiCoreArrangementDraftAcceptanceResult.Rejected>(result)
        assertEquals(MidiCoreArrangementDraftProblemCode.LOCKED, rejected.problem.code)
        assertContentEquals(before, Files.readAllBytes(locked.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(listOf(alternative.candidate.id), store.openProject(locked.root).acceptances.map { it.candidateId })

        val changed = locked.project.copy(
            authority = requireNotNull(locked.project.authority).copy(
                key = ProjectKey(ProjectKeySpelling.D, ProjectScaleMode.MAJOR),
            ),
            revision = locked.project.revision + 1L,
        )
        store.saveProject(locked.root, changed)
        val stale = assertIs<MidiCoreArrangementDraftAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assembleDraft(
                AssembleMidiCoreArrangementDraft(MidiCoreProjectSession(locked.root, changed), generated.draft.id),
            ),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.DRAFT_STALE, stale.problem.code)
    }

    private fun readySession(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store, idFactory = { "draft-project" }).create(
                CreateMidiCoreProject(root.resolve("project"), "Draft Test", "draft-project"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures")).single { it.fileName.toString() == "whole-song-three-bars.mid" }
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
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 3)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(structured, listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 5760))),
            ),
        ).session
    }
}
