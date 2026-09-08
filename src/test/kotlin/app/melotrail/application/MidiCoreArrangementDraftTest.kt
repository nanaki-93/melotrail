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
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreRoleActivity
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
        assertEquals(
            listOf(CandidateRole.CHORDS, CandidateRole.CHORDS),
            cancelled.progress.completedScopes.map(MidiCoreArrangementDraftScope::role),
        )
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
    fun `full draft requires an explicitly confirmed plan before any candidate is published`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val planned = readySession(store)
        val unplannedProject = planned.project.copy(arrangementPlan = null, revision = planned.project.revision + 1L)
        store.saveProject(planned.root, unplannedProject)
        val unplanned = MidiCoreProjectSession(planned.root, unplannedProject)
        val before = Files.readAllBytes(unplanned.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = MidiCoreArrangementDraftGeneration(artifacts = store).generate(
            GenerateMidiCoreArrangementDraft(unplanned, "steady-road", 77L, draftId = "draft-needs-plan"),
        )

        val incomplete = assertIs<MidiCoreArrangementDraftGenerationResult.Incomplete>(result)
        assertEquals(MidiCoreArrangementDraftProblemCode.AUTHORITY_REQUIRED, incomplete.problem.code)
        assertTrue(store.openProject(unplanned.root).candidates.isEmpty())
        assertContentEquals(before, Files.readAllBytes(unplanned.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `planned rest survives cancellation retry and atomically uses and undoes with candidates`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val initial = readySession(store)
        val plan = requireNotNull(initial.project.arrangementPlan).copy(
            occurrences = requireNotNull(initial.project.arrangementPlan).occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { setting ->
                    if (setting.role == CandidateRole.BASS) setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0) else setting
                })
            },
        )
        val restingProject = initial.project.copy(arrangementPlan = plan, revision = initial.project.revision + 1L)
        store.saveProject(initial.root, restingProject)
        val resting = MidiCoreProjectSession(initial.root, restingProject)

        val cancelledAfterRest = AtomicBoolean(false)
        val cancelled = assertIs<MidiCoreArrangementDraftGenerationResult.Cancelled>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    resting, "steady-road", 73L, draftId = "draft-planned-rest",
                    cancellation = MidiCoreGenerationCancellation { cancelledAfterRest.get() },
                    onProgress = { progress -> if (progress.completedCount == 2) cancelledAfterRest.set(true) },
                ),
            ),
        )
        assertEquals(2, cancelled.progress.completedCount)
        assertEquals(1, cancelled.session.project.candidates.size)

        val generated = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(cancelled.session, "steady-road", 73L, draftId = cancelled.draftId),
            ),
        )
        assertEquals(listOf(CandidateRole.BASS), generated.draft.plannedRests.map { it.role })
        assertEquals(2, generated.draft.candidateReferences.size)
        assertEquals(listOf(CandidateRole.BASS), generated.session.project.candidates.single { it.role == CandidateRole.DRUMS }.draftDependencyRests.map { it.role })
        assertEquals(generated.session.project, store.openProject(generated.session.root))
        assertEquals(generated.session.project, MidiCoreProjectSchema.decode(MidiCoreProjectSchema.encode(generated.session.project)))
        assertIs<MidiCoreReviewAuditionResult.Ready>(
            MidiCoreReviewAudition(assembly = MidiCoreAcceptedSongAssembly(artifacts = store)).draft(
                PrepareMidiCoreArrangementDraftAudition(generated.session, generated.draft.id),
            ),
        )

        val accepted = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store, idFactory = { "rest-batch" }).use(
                UseMidiCoreArrangementDraft(generated.session, generated.draft.id, locked = true),
            ),
        )
        assertEquals(2, accepted.session.project.acceptances.size)
        assertEquals(listOf(CandidateRole.BASS), accepted.session.project.acceptedPlannedRests.map { it.role })
        assertTrue(accepted.session.project.acceptedPlannedRests.single().locked)
        assertIs<MidiCoreReviewAuditionResult.Ready>(
            MidiCoreReviewAudition(assembly = MidiCoreAcceptedSongAssembly(artifacts = store)).acceptedArrangement(
                PrepareMidiCoreAcceptedArrangementAudition(accepted.session),
            ),
        )

        val undone = assertIs<MidiCoreArrangementDraftAcceptanceUndoResult.Applied>(
            MidiCoreArrangementDraftAcceptanceUndo(artifacts = store).undo(
                UndoMidiCoreArrangementDraftAcceptance(accepted.session, accepted.history.id),
            ),
        )
        assertTrue(undone.session.project.acceptances.isEmpty())
        assertTrue(undone.session.project.acceptedPlannedRests.isEmpty())
    }

    @Test
    fun `inactive plan scope rejects candidate acceptance and draft selection`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val initial = readySession(store)
        val activeDraft = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(initial, "steady-road", 78L, draftId = "draft-active-source"),
            ),
        )
        val sourceCandidate = activeDraft.session.project.candidates.single { it.role == CandidateRole.BASS }
        val inactivePlan = requireNotNull(activeDraft.session.project.arrangementPlan).copy(
            occurrences = requireNotNull(activeDraft.session.project.arrangementPlan).occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { setting ->
                    if (setting.role == CandidateRole.BASS) {
                        setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0)
                    } else {
                        setting
                    }
                })
            },
        )
        val inactiveProject = activeDraft.session.project.copy(
            arrangementPlan = inactivePlan,
            revision = activeDraft.session.project.revision + 1L,
        )
        store.saveProject(initial.root, inactiveProject)
        val inactiveSession = MidiCoreProjectSession(initial.root, inactiveProject)
        val lifecycle = MidiCoreCandidateLifecycle(artifacts = store)
        val directCandidate = assertIs<MidiCoreCandidateLifecycleResult.Published>(
            lifecycle.publish(
                PublishMidiCoreCandidate(
                    session = inactiveSession,
                    role = CandidateRole.BASS,
                    occurrenceId = "verse-1",
                    generatorVersion = sourceCandidate.generatorVersion,
                    authorityHash = MidiCoreAuthorityHasher.from(inactiveProject).scopeHash("verse-1", CandidateRole.BASS),
                    seed = sourceCandidate.seed,
                    midi = inactiveSession.root.resolve(sourceCandidate.midi.path.value),
                    validationReportJson = Files.readString(inactiveSession.root.resolve(sourceCandidate.validationReport.path.value)),
                    candidateId = "inactive-bass-candidate",
                    profileId = sourceCandidate.profileId,
                    patternId = sourceCandidate.patternId,
                ),
            ),
        )
        val beforeAcceptance = Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val rejectedAcceptance = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            lifecycle.accept(AcceptMidiCoreCandidate(directCandidate.session, directCandidate.candidate.id)),
        )

        assertEquals(MidiCoreCandidateProblemCode.INVALID_STATE, rejectedAcceptance.problem.code)
        assertContentEquals(beforeAcceptance, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertTrue(store.openProject(initial.root).acceptances.isEmpty())

        val restingDraft = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(directCandidate.session, "steady-road", 79L, draftId = "draft-valid-rest"),
            ),
        )
        val drums = restingDraft.session.project.candidates.single { it.id == restingDraft.draft.candidateReferences.single { ref -> ref.role == CandidateRole.DRUMS }.candidateId }
        val upstreamIds = restingDraft.draft.candidateReferences.filter { it.role == CandidateRole.CHORDS }.map { it.candidateId } + directCandidate.candidate.id
        val beforeDependency = Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val badGeneration = assertIs<MidiCoreCandidateGenerationResult.Rejected>(
            MidiCoreCandidateGeneration(artifacts = store).generate(GenerateMidiCoreCandidate(
                restingDraft.session, CandidateRole.DRUMS, "verse-1", drums.profileId, drums.patternId,
                MidiCoreGeneratorInput("inactive-upstream", drums.generatorVersion, drums.patternId, 90L),
                draftDependencyIds = upstreamIds, useDraftDependencies = true,
            )),
        )
        assertEquals(MidiCoreCandidateProblemCode.INVALID_STATE, badGeneration.problem.code)
        val badPublication = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(lifecycle.publish(PublishMidiCoreCandidate(
            session = restingDraft.session, role = CandidateRole.DRUMS, occurrenceId = "verse-1",
            generatorVersion = drums.generatorVersion, authorityHash = drums.authorityHash, seed = drums.seed,
            midi = initial.root.resolve(drums.midi.path.value),
            validationReportJson = Files.readString(initial.root.resolve(drums.validationReport.path.value)),
            candidateId = "invalid-upstream-drums", profileId = drums.profileId, patternId = drums.patternId,
            draftDependencyIds = upstreamIds,
        )))
        assertEquals(MidiCoreCandidateProblemCode.INVALID_STATE, badPublication.problem.code)
        assertContentEquals(beforeDependency, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))

        val reference = MidiCoreArrangementDraftCandidateReference(
            directCandidate.candidate.occurrenceId,
            directCandidate.candidate.role,
            directCandidate.candidate.id,
            directCandidate.candidate.midi.sha256,
            directCandidate.candidate.validationReport.sha256,
            directCandidate.candidate.authorityHash,
        )
        val invalidReferences = restingDraft.draft.candidateReferences + reference
        val invalidSummary = requireNotNull(
            validationSummary(
                restingDraft.session.root,
                invalidReferences,
                project = restingDraft.session.project,
                artifacts = store,
            ),
        )
        val invalidDraft = restingDraft.draft.copy(
            id = "draft-inactive-candidate",
            candidateReferences = invalidReferences,
            plannedRests = emptyList(),
            validation = invalidSummary,
        )
        val beforeDraft = Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val rejectedDraft = assertIs<MidiCoreArrangementDraftLifecycleResult.Rejected>(
            MidiCoreArrangementDraftLifecycle(store).publish(
                PublishMidiCoreArrangementDraft(restingDraft.session, invalidDraft),
            ),
        )

        assertEquals(MidiCoreArrangementDraftProblemCode.DRAFT_INVALID, rejectedDraft.problem.code)
        assertEquals(MidiCoreArrangementDraftScope("verse-1", CandidateRole.BASS), rejectedDraft.problem.scope)
        assertContentEquals(beforeDraft, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertTrue(store.openProject(initial.root).arrangementDrafts.none { it.id == invalidDraft.id })
    }

    @Test
    fun `planned Chords rest breaks piano boundary continuity before the next occurrence`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val initial = multiOccurrenceSession(store)
        val restingPlan = requireNotNull(initial.project.arrangementPlan).copy(
            occurrences = requireNotNull(initial.project.arrangementPlan).occurrences.map { occurrence ->
                if (occurrence.occurrenceId != "part-2") occurrence else occurrence.copy(
                    roleSettings = occurrence.roleSettings.map { setting ->
                        if (setting.role == CandidateRole.CHORDS) {
                            setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0)
                        } else {
                            setting
                        }
                    },
                )
            },
        )
        val restingProject = initial.project.copy(
            arrangementPlan = restingPlan,
            revision = initial.project.revision + 1L,
        )
        store.saveProject(initial.root, restingProject)

        val generated = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    MidiCoreProjectSession(initial.root, restingProject),
                    "steady-road",
                    75L,
                    draftId = "draft-chords-rest-boundary",
                ),
            ),
        )

        assertEquals(
            listOf("part-2"),
            generated.draft.plannedRests.filter { it.role == CandidateRole.CHORDS }.map { it.occurrenceId },
        )
        assertEquals(
            null,
            generated.session.project.candidates.single {
                it.occurrenceId == "part-3" && it.role == CandidateRole.CHORDS
            }.boundarySummarySha256,
        )
        assertIs<MidiCoreReviewAuditionResult.Ready>(
            MidiCoreReviewAudition(assembly = MidiCoreAcceptedSongAssembly(artifacts = store)).draft(
                PrepareMidiCoreArrangementDraftAudition(generated.session, generated.draft.id),
            ),
        )
    }

    @Test
    fun `locked candidate can unlock after inactive plan edit for atomic rest use and undo`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val generated = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(readySession(store), "steady-road", 79L, draftId = "active-before-rest"),
            ),
        )
        val used = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(
                UseMidiCoreArrangementDraft(generated.session, generated.draft.id, locked = true),
            ),
        )
        val lifecycle = MidiCoreCandidateLifecycle(artifacts = store)
        var session = used.session
        listOf(CandidateRole.CHORDS, CandidateRole.DRUMS).forEach { role ->
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(lifecycle.unlock(
                UnlockMidiCoreCandidate(session, session.project.acceptances.single { it.role == role }.candidateId),
            )).session
        }
        val bass = session.project.acceptances.single { it.role == CandidateRole.BASS }.candidateId
        val source = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val candidateBytes = session.project.candidates.associate { it.id to Files.readAllBytes(store.verify(session.root, it.midi)) }
        val plan = requireNotNull(session.project.arrangementPlan).let { current ->
            current.copy(occurrences = current.occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { setting ->
                    if (setting.role == CandidateRole.BASS) setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0) else setting
                })
            })
        }
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, plan)),
        ).session
        assertEquals(MidiCoreCandidateStatus.STALE, session.project.candidates.single { it.id == bass }.status)
        val replacement = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(session, "steady-road", 80L, draftId = "rest-after-active"),
            ),
        )
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val blocked = assertIs<MidiCoreArrangementDraftAcceptanceResult.Rejected>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(replacement.session, replacement.draft.id)),
        )
        assertEquals(MidiCoreArrangementDraftProblemCode.LOCKED, blocked.problem.code)
        val stale = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(lifecycle.unlock(
            UnlockMidiCoreCandidate(replacement.session, bass, replacement.session.project.revision - 1),
        ))
        assertEquals(MidiCoreCandidateProblemCode.REVISION_CONFLICT, stale.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        val unlocked = assertIs<MidiCoreCandidateLifecycleResult.Updated>(lifecycle.unlock(
            UnlockMidiCoreCandidate(replacement.session, bass, replacement.session.project.revision),
        ))
        assertEquals(MidiCoreCandidateStatus.STALE, unlocked.session.project.candidates.single { it.id == bass }.status)
        assertFalse(unlocked.session.project.acceptances.single { it.candidateId == bass }.locked)
        val applied = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(unlocked.session, replacement.draft.id)),
        )
        assertEquals(listOf(CandidateRole.BASS), applied.session.project.acceptedPlannedRests.map { it.role })
        val undone = assertIs<MidiCoreArrangementDraftAcceptanceUndoResult.Applied>(
            MidiCoreArrangementDraftAcceptanceUndo(artifacts = store).undo(UndoMidiCoreArrangementDraftAcceptance(applied.session, applied.history.id)),
        )
        assertEquals(unlocked.session.project.acceptances, undone.session.project.acceptances)
        assertEquals(MidiCoreCandidateStatus.STALE, undone.session.project.candidates.single { it.id == bass }.status)
        assertContentEquals(source, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        candidateBytes.forEach { (id, bytes) ->
            assertContentEquals(bytes, Files.readAllBytes(store.verify(session.root, undone.session.project.candidates.single { it.id == id }.midi)))
        }
    }

    @Test
    fun `reactivating upstream rest invalidates downstream candidate and rejects direct acceptance`() = runBlocking {
        val store = MidiCoreArtifactStore()
        var session = readySession(store)
        val activePlan = requireNotNull(session.project.arrangementPlan)
        val restingPlan = activePlan.copy(occurrences = activePlan.occurrences.map { occurrence ->
            occurrence.copy(roleSettings = occurrence.roleSettings.map {
                if (it.role == CandidateRole.BASS) it.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0) else it
            })
        })
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, restingPlan)),
        ).session
        val draft = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(session, "steady-road", 83L, draftId = "rest-dependent-drums"),
            ),
        )
        session = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(draft.session, draft.draft.id)),
        ).session
        val drums = session.project.candidates.single { it.role == CandidateRole.DRUMS }
        assertEquals(listOf(CandidateRole.BASS), drums.draftDependencyRests.map { it.role })
        val preview = assertIs<MidiCoreArrangementPlanEditResult.Previewed>(
            MidiCoreArrangementPlanEdit(store).preview(PreviewMidiCoreArrangementPlanEdit(session, activePlan)),
        )
        assertTrue(drums.id in preview.invalidation.staleCandidateIds)
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, activePlan)),
        ).session
        assertEquals(MidiCoreCandidateStatus.STALE, session.project.candidates.single { it.id == drums.id }.status)
        // Also prove lifecycle defense when persisted status alone incorrectly claims currentness.
        for (status in listOf(MidiCoreCandidateStatus.STALE, MidiCoreCandidateStatus.CURRENT)) {
            val project = session.project.copy(candidates = session.project.candidates.map {
                if (it.id == drums.id) it.copy(status = status) else it
            })
            store.saveProject(session.root, project)
            session = MidiCoreProjectSession(session.root, project)
            val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
            val lifecycle = MidiCoreCandidateLifecycle(artifacts = store)
            assertEquals(MidiCoreCandidateProblemCode.CANDIDATE_STALE,
                assertIs<MidiCoreCandidateLifecycleResult.Rejected>(lifecycle.accept(AcceptMidiCoreCandidate(session, drums.id))).problem.code)
            assertEquals(MidiCoreCandidateProblemCode.CANDIDATE_STALE,
                assertIs<MidiCoreCandidateLifecycleResult.Rejected>(lifecycle.restore(RestoreMidiCoreCandidate(session, drums.occurrenceId, drums.role, drums.id))).problem.code)
            assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        }
    }

    @Test
    fun `locked rest with changed authority requires unlock before rest replacement`() = runBlocking {
        val store = MidiCoreArtifactStore()
        var session = readySession(store)
        fun restPlan(energy: Int) = requireNotNull(session.project.arrangementPlan).copy(
            occurrences = requireNotNull(session.project.arrangementPlan).occurrences.map { occurrence ->
                occurrence.copy(energy = energy, roleSettings = occurrence.roleSettings.map {
                    if (it.role == CandidateRole.BASS) it.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0) else it
                })
            },
        )
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(artifacts = store).confirm(
                ConfirmMidiCoreArrangementPlanEdit(session, restPlan(40)),
            ),
        ).session
        val first = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(session, "steady-road", 81L, draftId = "rest-authority-first"),
            ),
        )
        session = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(first.session, first.draft.id, locked = true)),
        ).session
        val lifecycle = MidiCoreCandidateLifecycle(artifacts = store)
        session.project.acceptances.map { it.candidateId }.forEach { id ->
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(lifecycle.unlock(UnlockMidiCoreCandidate(session, id, session.project.revision))).session
        }
        val oldRest = session.project.acceptedPlannedRests.single()
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(artifacts = store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, restPlan(60))),
        ).session
        val replacement = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(session, "steady-road", 82L, draftId = "rest-authority-second"),
            ),
        )
        assertNotEquals(oldRest.authorityHash, replacement.draft.plannedRests.single().authorityHash)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val rejected = assertIs<MidiCoreArrangementDraftAcceptanceResult.Rejected>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(replacement.session, replacement.draft.id)),
        )
        assertEquals(MidiCoreArrangementDraftProblemCode.LOCKED, rejected.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        session = assertIs<MidiCorePlannedRestLockResult.Updated>(lifecycle.unlock(
            UnlockMidiCorePlannedRest(replacement.session, oldRest.occurrenceId, oldRest.role, replacement.session.project.revision),
        )).session
        val applied = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(UseMidiCoreArrangementDraft(session, replacement.draft.id)),
        )
        assertEquals(replacement.draft.plannedRests, applied.session.project.acceptedPlannedRests)
    }

    @Test
    fun `locked planned rest unlock is revision guarded and permits atomic mixed replacement`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val initial = readySession(store)
        val restingPlan = requireNotNull(initial.project.arrangementPlan).copy(
            occurrences = requireNotNull(initial.project.arrangementPlan).occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { setting ->
                    if (setting.role == CandidateRole.BASS) {
                        setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0)
                    } else {
                        setting
                    }
                })
            },
        )
        val restingProject = initial.project.copy(
            arrangementPlan = restingPlan,
            revision = initial.project.revision + 1L,
        )
        store.saveProject(initial.root, restingProject)
        val restingDraft = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    MidiCoreProjectSession(initial.root, restingProject),
                    "steady-road",
                    76L,
                    draftId = "draft-locked-rest",
                ),
            ),
        )
        val restingAcceptance = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store, idFactory = { "locked-rest-batch" }).use(
                UseMidiCoreArrangementDraft(restingDraft.session, restingDraft.draft.id, locked = true),
            ),
        )

        val lifecycle = MidiCoreCandidateLifecycle(artifacts = store)
        val review = MidiCoreCandidateReview(artifacts = store, lifecycle = lifecycle)
        var restLockedSession = restingAcceptance.session
        listOf(CandidateRole.CHORDS, CandidateRole.DRUMS).forEach { role ->
            val candidateId = restLockedSession.project.acceptances.single { it.role == role }.candidateId
            restLockedSession = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                lifecycle.unlock(UnlockMidiCoreCandidate(restLockedSession, candidateId, restLockedSession.project.revision)),
            ).session
        }

        val replacementPlan = requireNotNull(initial.project.arrangementPlan).copy(
            occurrences = requireNotNull(initial.project.arrangementPlan).occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map { setting ->
                    if (setting.role == CandidateRole.DRUMS) {
                        setting.copy(activity = MidiCoreRoleActivity.INACTIVE, density = 0)
                    } else {
                        setting
                    }
                })
            },
        )
        val activeProject = restLockedSession.project.copy(
            arrangementPlan = replacementPlan,
            revision = restLockedSession.project.revision + 1L,
        )
        store.saveProject(initial.root, activeProject)
        val replacement = assertIs<MidiCoreArrangementDraftGenerationResult.Completed>(
            MidiCoreArrangementDraftGeneration(artifacts = store).generate(
                GenerateMidiCoreArrangementDraft(
                    MidiCoreProjectSession(initial.root, activeProject),
                    "steady-road",
                    77L,
                    draftId = "draft-replace-locked-rest",
                ),
            ),
        )
        val protectedSourceBytes = Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val protectedCandidateBytes = replacement.session.project.candidates.associate { candidate ->
            candidate.id to Files.readAllBytes(initial.root.resolve(candidate.midi.path.value))
        }
        assertEquals(listOf(CandidateRole.DRUMS), replacement.draft.plannedRests.map { it.role })
        val before = Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val rejected = assertIs<MidiCoreArrangementDraftAcceptanceResult.Rejected>(
            MidiCoreArrangementDraftAcceptance(artifacts = store).use(
                UseMidiCoreArrangementDraft(replacement.session, replacement.draft.id),
            ),
        )

        assertEquals(MidiCoreArrangementDraftProblemCode.LOCKED, rejected.problem.code)
        assertEquals(MidiCoreArrangementDraftScope("verse-1", CandidateRole.BASS), rejected.problem.scope)
        assertContentEquals(before, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(
            restLockedSession.project.acceptedPlannedRests,
            store.openProject(initial.root).acceptedPlannedRests,
        )

        val staleUnlock = assertIs<MidiCorePlannedRestLockResult.Rejected>(
            review.unlock(
                UnlockMidiCorePlannedRest(
                    replacement.session,
                    "verse-1",
                    CandidateRole.BASS,
                    replacement.session.project.revision - 1L,
                ),
            ),
        )
        assertEquals(MidiCoreCandidateProblemCode.REVISION_CONFLICT, staleUnlock.problem.code)
        assertContentEquals(before, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))

        val unlocked = assertIs<MidiCorePlannedRestLockResult.Updated>(
            review.unlock(
                UnlockMidiCorePlannedRest(
                    replacement.session,
                    "verse-1",
                    CandidateRole.BASS,
                    replacement.session.project.revision,
                ),
            ),
        )
        assertFalse(unlocked.rest.locked)
        assertEquals(replacement.session.project.revision + 1L, unlocked.session.project.revision)
        assertFalse(unlocked.session.project.acceptedPlannedRests.single { it.role == CandidateRole.BASS }.locked)

        val applied = assertIs<MidiCoreArrangementDraftAcceptanceResult.Applied>(
            MidiCoreArrangementDraftAcceptance(artifacts = store, idFactory = { "mixed-replacement-batch" }).use(
                UseMidiCoreArrangementDraft(unlocked.session, replacement.draft.id),
            ),
        )
        assertEquals(setOf(CandidateRole.CHORDS, CandidateRole.BASS), applied.session.project.acceptances.map { it.role }.toSet())
        assertEquals(listOf(CandidateRole.DRUMS), applied.session.project.acceptedPlannedRests.map { it.role })
        assertEquals(applied.session.project, store.openProject(initial.root))
        assertContentEquals(protectedSourceBytes, Files.readAllBytes(initial.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        protectedCandidateBytes.forEach { (candidateId, bytes) ->
            val candidate = applied.session.project.candidates.single { it.id == candidateId }
            assertContentEquals(bytes, Files.readAllBytes(initial.root.resolve(candidate.midi.path.value)))
        }
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
        val harmonic = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(structured, listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 5760))),
            ),
        ).session
        return confirmPlan(store, harmonic)
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
        val harmonic = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
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
        return confirmPlan(store, harmonic)
    }

    private fun confirmPlan(store: MidiCoreArtifactStore, session: MidiCoreProjectSession): MidiCoreProjectSession {
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            MidiCoreArrangementPlanProposalUseCase(store).propose(ProposeMidiCoreArrangementPlan(session, "steady-road")),
        ).proposal
        return assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(
            MidiCoreArrangementPlanProposalUseCase(store).confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal)),
        ).session
    }
}
