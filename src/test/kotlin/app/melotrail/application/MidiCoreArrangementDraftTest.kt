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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
    fun `retry preserves but does not reuse a retained candidate from an older generator version`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val cancellation = AtomicBoolean(false)
        val cancelled = assertIs<MidiCoreArrangementDraftGenerationResult.Cancelled>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    session = session,
                    styleId = "open-sky",
                    rootSeed = 13L,
                    draftId = "draft-version-retry",
                    cancellation = MidiCoreGenerationCancellation { cancellation.get() },
                    onProgress = { progress -> if (progress.completedCount == 1) cancellation.set(true) },
                ),
            ),
        )
        val retained = cancelled.session.project.candidates.single()
        val withOlderCandidate = cancelled.session.project.copy(
            candidates = listOf(retained.copy(generatorVersion = "midi-core-style-v1")),
            revision = cancelled.session.project.revision + 1L,
        )
        store.saveProject(cancelled.session.root, withOlderCandidate)

        val retried = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    MidiCoreProjectSession(cancelled.session.root, withOlderCandidate),
                    "open-sky",
                    13L,
                    draftId = cancelled.draftId,
                ),
            ),
        )

        assertEquals(4, retried.session.project.candidates.size)
        assertTrue(retried.session.project.candidates.any { it.id == retained.id && it.generatorVersion == "midi-core-style-v1" })
        val currentChord = retried.session.project.candidates.single { it.id == retried.draft.candidateReferences.first().candidateId }
        assertTrue(currentChord.id != retained.id)
        assertEquals("midi-core-style-v5-patterns-v2-comping-v1", currentChord.generatorVersion)
    }

    @Test
    fun `multi-occurrence retry regenerates mismatched piano boundary and completed draft use rejects it`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val cancellation = AtomicBoolean(false)
        val cancelled = assertIs<MidiCoreArrangementDraftGenerationResult.Cancelled>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    session = multiOccurrenceSession(store),
                    styleId = "steady-road",
                    rootSeed = 41L,
                    draftId = "draft-boundary-retry",
                    cancellation = MidiCoreGenerationCancellation { cancellation.get() },
                    onProgress = { progress -> if (progress.completedCount == 2) cancellation.set(true) },
                ),
            ),
        )
        assertEquals(2, cancelled.progress.completedCount)
        val reopened = store.openProject(cancelled.session.root)
        val retained = reopened.candidates.single { it.role == CandidateRole.CHORDS && it.occurrenceId == "part-2" }
        val correctBoundary = assertNotNull(retained.boundarySummarySha256)
        assertEquals(reopened, MidiCoreProjectSchema.decode(MidiCoreProjectSchema.encode(reopened)))
        val retainedPath = cancelled.session.root.resolve(retained.midi.path.value)
        val retainedBytes = Files.readAllBytes(retainedPath)
        val mismatch = if (correctBoundary == "f".repeat(64)) "e".repeat(64) else "f".repeat(64)
        val mismatchedProject = reopened.copy(
            candidates = reopened.candidates.map { candidate ->
                if (candidate.id == retained.id) candidate.copy(boundarySummarySha256 = mismatch) else candidate
            },
            revision = reopened.revision + 1L,
        )
        store.saveProject(cancelled.session.root, mismatchedProject)
        val retrySession = MidiCoreProjectSession(cancelled.session.root, store.openProject(cancelled.session.root))

        val retryResult = MidiCoreArrangementDraftGeneration(artifacts = store).generate(
            GenerateMidiCoreArrangementDraft(retrySession, "steady-road", 41L, draftId = cancelled.draftId),
        )
        val retried = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            retryResult,
            (retryResult as? MidiCoreArrangementDraftGenerationResult.Incomplete)?.problem.toString(),
        )
        val replacementId = retried.draft.candidateReferences.single {
            it.role == CandidateRole.CHORDS && it.occurrenceId == "part-2"
        }.candidateId
        val replacement = retried.session.project.candidates.single { it.id == replacementId }

        assertNotEquals(retained.id, replacement.id)
        assertEquals(correctBoundary, replacement.boundarySummarySha256)
        assertTrue(retried.session.project.candidates.any { it.id == retained.id && it.boundarySummarySha256 == mismatch })
        assertContentEquals(retainedBytes, Files.readAllBytes(retainedPath))
        assertEquals(retried.session.project, store.openProject(retried.session.root))

        val missingBoundaryProject = retried.session.project.copy(
            candidates = retried.session.project.candidates.map { candidate ->
                if (candidate.id == replacement.id) candidate.copy(boundarySummarySha256 = null) else candidate
            },
        )
        assertEquals(
            MidiCoreArrangementDraftProblemCode.DRAFT_STALE,
            validateDraft(retried.session.root, missingBoundaryProject, retried.draft, store)?.code,
        )

        val mismatchedDraftProject = retried.session.project.copy(
            candidates = retried.session.project.candidates.map { candidate ->
                if (candidate.id == replacement.id) candidate.copy(boundarySummarySha256 = mismatch) else candidate
            },
            revision = retried.session.project.revision + 1L,
        )
        store.saveProject(retried.session.root, mismatchedDraftProject)
        val mismatchedSession = MidiCoreProjectSession(retried.session.root, store.openProject(retried.session.root))
        val assembly = assertIs<MidiCoreArrangementDraftAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assembleDraft(
                AssembleMidiCoreArrangementDraft(mismatchedSession, retried.draft.id),
            ),
        )
        val beforeUse = Files.readAllBytes(mismatchedSession.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val use = assertIs<MidiCoreArrangementDraftAcceptanceResult.Rejected>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(
                UseMidiCoreArrangementDraft(mismatchedSession, retried.draft.id),
            ),
        )

        assertEquals(MidiCoreSongAssemblyProblemCode.DRAFT_STALE, assembly.problem.code)
        assertEquals(MidiCoreArrangementDraftProblemCode.DRAFT_STALE, use.problem.code)
        assertTrue(store.openProject(mismatchedSession.root).acceptances.isEmpty())
        assertContentEquals(beforeUse, Files.readAllBytes(mismatchedSession.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
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

    private fun multiOccurrenceSession(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store, idFactory = { "draft-multi-project" }).create(
                CreateMidiCoreProject(root.resolve("multi-project"), "Multi Draft Test", "draft-multi-project"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("multi-fixtures")).single {
            it.fileName.toString() == "whole-song-three-bars.mid"
        }
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
                    listOf(ProjectSectionDefinition("part", "Part")),
                    listOf(
                        MidiCoreBarOccurrencePlacement("part-1", "part", "Part 1", 1),
                        MidiCoreBarOccurrencePlacement("part-2", "part", "Part 2", 1),
                        MidiCoreBarOccurrencePlacement("part-3", "part", "Part 3", 1),
                    ),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    listOf(
                        AuthoritativeChordEvent("chord-1", "part-1", "C", 0, 1_920),
                        AuthoritativeChordEvent("chord-2", "part-2", "F", 1_920, 3_840),
                        AuthoritativeChordEvent("chord-3", "part-3", "G", 3_840, 5_760),
                    ),
                ),
            ),
        ).session
    }
}
