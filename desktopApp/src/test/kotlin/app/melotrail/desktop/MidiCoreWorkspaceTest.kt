package app.melotrail.desktop

import app.melotrail.application.ConfirmMidiCoreAuthority
import app.melotrail.application.CreateMidiCoreProject
import app.melotrail.application.ExportMidiCorePackage
import app.melotrail.application.GenerateMidiCoreCandidate
import app.melotrail.application.ImportMidiCoreSource
import app.melotrail.application.ListMidiCoreCandidates
import app.melotrail.application.MidiCoreAuthorityResult
import app.melotrail.application.MidiCoreAuthoritySuggestions
import app.melotrail.application.MidiCoreCandidateGenerationResult
import app.melotrail.application.MidiCoreCandidateLifecycleResult
import app.melotrail.application.MidiCoreCandidateReviewResult
import app.melotrail.application.MidiCoreMelodySelectionResult
import app.melotrail.application.MidiCoreMidiPackageExportResult
import app.melotrail.application.MidiCoreProjectCloseResult
import app.melotrail.application.MidiCoreProjectLifecycleResult
import app.melotrail.application.MidiCoreProjectSession
import app.melotrail.application.MidiCoreSourceImportResult
import app.melotrail.application.MidiCoreStructureTimelineResult
import app.melotrail.application.MidiCoreAuthoritativeHarmonyResult
import app.melotrail.application.MidiCoreCandidateProblem
import app.melotrail.application.MidiCoreProjectProblem
import app.melotrail.application.MidiCoreSourceImportProblem
import app.melotrail.application.MidiCoreMelodySelectionProblem
import app.melotrail.application.MidiCoreAuthorityProblem
import app.melotrail.application.MidiCoreStructureTimelineProblem
import app.melotrail.application.MidiCoreAuthoritativeHarmonyProblem
import app.melotrail.application.MidiCorePackageExportProblem
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreMelodySelection
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.ReplaceMidiCoreHarmony
import app.melotrail.application.ReplaceMidiCoreStructure
import app.melotrail.application.SelectMidiCoreMelody
import app.melotrail.application.AcceptMidiCoreCandidate
import app.melotrail.application.RejectMidiCoreCandidate
import app.melotrail.application.LockMidiCoreCandidate
import app.melotrail.application.UnlockMidiCoreCandidate
import app.melotrail.application.RestoreMidiCoreCandidate
import app.melotrail.application.CompareMidiCoreCandidates
import app.melotrail.application.RegenerateMidiCoreCandidate
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionPort
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class MidiCoreWorkspaceTest {
    @Test
    fun `intent routing exposes target blockers and keeps authority draft unsaved`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val dispatchers = MidiCoreWorkspaceDispatchers(
            ui = StandardTestDispatcher(testScheduler),
            io = StandardTestDispatcher(testScheduler),
        )
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, dispatchers)

        viewModel.accept(MidiCoreWorkspaceIntent.ImportSource(Path.of("source.mid")))
        assertEquals(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, viewModel.state.value.blockers.first().code)

        val root = Path.of("build/midi-core-workspace-routing")
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(root))
        assertEquals(MidiCoreWorkspaceOperationPhase.RUNNING, viewModel.state.value.operation.phase)
        advanceUntilIdle()
        assertEquals(fake.session.project, viewModel.state.value.project)
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, viewModel.state.value.operation.phase)

        val draft = viewModel.state.value.authority.draft.copy(
            key = ProjectKey(ProjectKeySpelling.G, ProjectScaleMode.NATURAL_MINOR),
            tempo = ProjectTempo(400_000),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(draft))
        assertTrue(viewModel.state.value.authority.draftDirty)
        assertEquals(0, fake.confirmAuthorityCalls)
        assertEquals(null, viewModel.state.value.project?.authority)

        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmAuthority)
        advanceUntilIdle()
        assertEquals(1, fake.confirmAuthorityCalls)
        assertFalse(viewModel.state.value.authority.draftDirty)
        assertEquals(draft.key, viewModel.state.value.authority.confirmed?.key)
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, viewModel.state.value.operation.phase)
        viewModel.close()
    }

    @Test
    fun `source audition is asynchronous and preserves project state on device failure`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeSourcePlan())
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        advanceUntilIdle()
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, viewModel.state.value.operation.phase)
        assertEquals(MidiAuditionPlaybackState.PLAYING, viewModel.state.value.audition.playback)
        val projectAfterPlay = viewModel.state.value.project

        viewModel.accept(MidiCoreWorkspaceIntent.StopAudition)
        fake.audition.playProblem = app.melotrail.audition.MidiAuditionProblem(
            app.melotrail.audition.MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
            "No MIDI output device is available.",
            "Connect a MIDI output and retry.",
        )
        val beforeFailure = viewModel.state.value.audition
        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        advanceUntilIdle()

        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals(beforeFailure, viewModel.state.value.audition)
        assertEquals(projectAfterPlay, viewModel.state.value.project)
        assertEquals(MidiCoreWorkspaceIntent.PlaySourceMelody, viewModel.state.value.operation.retry)
        viewModel.close()
    }

    @Test
    fun `authority draft requires explicit discard before closing project`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val dispatchers = testDispatchers(testScheduler)
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, dispatchers)
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.UpdateAuthorityDraft(viewModel.state.value.authority.draft.copy(tempo = ProjectTempo(300_000))))

        viewModel.accept(MidiCoreWorkspaceIntent.CloseProject)
        assertIs<MidiCoreWorkspaceDialog.ConfirmDiscardAuthorityDraft>(viewModel.state.value.dialog)
        assertNotNull(viewModel.state.value.project)
        assertEquals(0, fake.closeCalls)

        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmDiscardAuthorityDraft)
        assertNull(viewModel.state.value.project)
        assertNull(viewModel.state.value.dialog)
        assertEquals(1, fake.closeCalls)
        viewModel.close()
    }

    @Test
    fun `busy generation can be cancelled without replacing the last known good state`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val pending = CompletableDeferred<MidiCoreCandidateGenerationResult>()
        fake.pendingGeneration = pending
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.GenerateCandidate(
            role = app.melotrail.project.CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            performanceProfileId = "chords-default",
            patternId = "sustained",
            generator = app.melotrail.project.MidiCoreGeneratorInput("midi-core", "1", "sustained", 7L),
        ))
        assertEquals(MidiCoreWorkspaceOperationPhase.RUNNING, viewModel.state.value.operation.phase)
        viewModel.accept(MidiCoreWorkspaceIntent.CancelOperation)
        assertEquals(MidiCoreWorkspaceOperationPhase.CANCELLING, viewModel.state.value.operation.phase)
        pending.complete(MidiCoreCandidateGenerationResult.Cancelled(null, null, emptyList()))
        advanceUntilIdle()

        assertEquals(MidiCoreWorkspaceOperationPhase.CANCELLED, viewModel.state.value.operation.phase)
        assertEquals(fake.session.project, viewModel.state.value.project)
        assertTrue(viewModel.state.value.notification.orEmpty().contains("last known-good"))
        viewModel.close()
    }

    @Test
    fun `failed operation retries the same intent and restart rehydrates persisted target state`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val preferences = MemoryMidiCorePreferences()
        fake.seedPersistedAuthority()
        fake.openResults += MidiCoreProjectLifecycleResult.Rejected(
            MidiCoreProjectProblem(
                app.melotrail.application.MidiCoreProjectProblemCode.IO_FAILURE,
                "The project could not be opened.",
                "Check the project folder and retry.",
            ),
        )
        fake.openResults += MidiCoreProjectLifecycleResult.Opened(fake.persistedSession())
        val dispatchers = testDispatchers(testScheduler)
        val first = MidiCoreWorkspaceViewModel(fake, preferences, NoOpDesktopOperationLogger, dispatchers)

        first.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, first.state.value.operation.phase)
        assertIs<MidiCoreWorkspaceIntent.OpenProject>(first.state.value.operation.retry)
        first.accept(MidiCoreWorkspaceIntent.Retry)
        advanceUntilIdle()
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, first.state.value.operation.phase)
        assertEquals(fake.session.root, preferences.lastOpenedProject())
        first.close()

        val second = MidiCoreWorkspaceViewModel(fake, preferences, NoOpDesktopOperationLogger, dispatchers)
        second.accept(MidiCoreWorkspaceIntent.OpenLastProject)
        advanceUntilIdle()
        assertEquals(fake.persistedSession().project, second.state.value.project)
        assertEquals(fake.persistedSession().project.revision, second.state.value.projectRevision)
        assertNotNull(second.state.value.authority.confirmed)
        assertEquals(fake.persistedSession().project.authority, second.state.value.authority.confirmed)
        second.close()
    }

    @Test
    fun `stale completion is rejected when the admitted project revision changes`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val pending = CompletableDeferred<MidiCoreCandidateGenerationResult>()
        fake.pendingGeneration = pending
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.GenerateCandidate(
            role = app.melotrail.project.CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            performanceProfileId = "chords-default",
            patternId = "sustained",
            generator = app.melotrail.project.MidiCoreGeneratorInput("midi-core", "1", "sustained", 9L),
        ))
        fake.advanceRevisionWithoutReplacingSession()
        pending.complete(MidiCoreCandidateGenerationResult.Cancelled(null, null, emptyList()))
        advanceUntilIdle()

        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals(MidiCoreWorkspaceBlockerCode.STALE_COMPLETION, viewModel.state.value.blockers.first().code)
        viewModel.close()
    }

    private fun testDispatchers(scheduler: TestCoroutineScheduler) = MidiCoreWorkspaceDispatchers(
        ui = StandardTestDispatcher(scheduler),
        io = StandardTestDispatcher(scheduler),
    )
}

private class MemoryMidiCorePreferences : MidiCoreDesktopPreferences {
    private var last: Path? = null

    override fun lastOpenedProject(): Path? = last
    override fun saveLastOpenedProject(root: Path) { last = root }
    override fun clearLastOpenedProject() { last = null }
}

private class FakeMidiCoreWorkspaceUseCases : MidiCoreWorkspaceUseCases {
    val session = MidiCoreProjectSession(
        Path.of("build/fake-midi-core-project"),
        MidiCoreProject(
            id = ProjectId("fake-project"),
            metadata = ProjectMetadata("Fake project", "2026-08-28T00:00:00Z"),
            revision = 4L,
        ),
    )
    override val audition = FakeMidiAudition()
    var sourceAuditionResult: app.melotrail.application.MidiCoreSourceAuditionResult =
        app.melotrail.application.MidiCoreSourceAuditionResult.Rejected(
            app.melotrail.application.MidiCoreSourceAuditionProblem(
                app.melotrail.application.MidiCoreSourceAuditionProblemCode.MELODY_REQUIRED,
                "not used",
                "not used",
            ),
        )
    val openResults = ArrayDeque<MidiCoreProjectLifecycleResult>()
    var pendingGeneration: CompletableDeferred<MidiCoreCandidateGenerationResult>? = null
    var confirmAuthorityCalls = 0
    var closeCalls = 0
    private var currentSession = session

    override fun create(request: CreateMidiCoreProject): MidiCoreProjectLifecycleResult = MidiCoreProjectLifecycleResult.Opened(session)

    override fun open(root: Path): MidiCoreProjectLifecycleResult {
        return if (openResults.isEmpty()) MidiCoreProjectLifecycleResult.Opened(currentSession) else openResults.removeFirst()
    }

    override fun readCurrent(root: Path): MidiCoreProjectSession? = currentSession

    override fun close(session: MidiCoreProjectSession): MidiCoreProjectCloseResult {
        closeCalls += 1
        return MidiCoreProjectCloseResult.Closed(session.root, session.project.id)
    }

    override fun importSource(request: ImportMidiCoreSource): MidiCoreSourceImportResult = error("not used")

    override fun selectMelody(request: SelectMidiCoreMelody): MidiCoreMelodySelectionResult = error("not used")

    override fun prepareSourceAudition(request: app.melotrail.application.PrepareMidiCoreSourceAudition): app.melotrail.application.MidiCoreSourceAuditionResult = sourceAuditionResult

    override fun confirmAuthority(request: ConfirmMidiCoreAuthority): MidiCoreAuthorityResult {
        confirmAuthorityCalls += 1
        val authority = ProjectAuthority(
            key = request.key,
            tempo = request.tempo,
            meter = request.meter,
            sectionDefinitions = emptyList(),
            occurrences = emptyList(),
            chordEvents = emptyList(),
        )
        val updated = currentSession.project.copy(authority = authority, revision = currentSession.project.revision + 1L)
        val updatedSession = MidiCoreProjectSession(currentSession.root, updated)
        val invalidation = MidiCoreInvalidationPlanner.preview(
            app.melotrail.project.MidiCoreAuthorityHasher.from(currentSession.project),
            app.melotrail.project.MidiCoreAuthorityHasher.from(updated),
        )
        currentSession = updatedSession
        return MidiCoreAuthorityResult.Confirmed(
            updatedSession,
            MidiCoreAuthoritySuggestions(null, null),
            app.melotrail.midi.domain.MidiImportValidationResult(emptyList()),
            invalidation,
        )
    }

    override fun replaceStructure(request: ReplaceMidiCoreStructure): MidiCoreStructureTimelineResult = error("not used")

    override fun replaceHarmony(request: ReplaceMidiCoreHarmony): MidiCoreAuthoritativeHarmonyResult = error("not used")

    override fun listCandidates(request: ListMidiCoreCandidates): MidiCoreCandidateReviewResult = error("not used")

    override fun compareCandidates(request: CompareMidiCoreCandidates): MidiCoreCandidateReviewResult = error("not used")

    override fun acceptCandidate(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun rejectCandidate(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun lockCandidate(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun unlockCandidate(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun restoreCandidate(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override suspend fun generateCandidate(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult = pendingGeneration?.await()
        ?: MidiCoreCandidateGenerationResult.Cancelled(null, null, emptyList())

    override suspend fun regenerateCandidate(request: RegenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult = generateCandidate(request.generation)

    override fun export(request: ExportMidiCorePackage): MidiCoreMidiPackageExportResult = error("not used")

    fun advanceRevisionWithoutReplacingSession() {
        currentSession = MidiCoreProjectSession(currentSession.root, currentSession.project.copy(revision = currentSession.project.revision + 1L))
    }

    fun persistedSession(): MidiCoreProjectSession = currentSession

    fun seedPersistedAuthority() {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = emptyList(),
            occurrences = emptyList(),
            chordEvents = emptyList(),
        )
        currentSession = MidiCoreProjectSession(
            currentSession.root,
            currentSession.project.copy(authority = authority, revision = currentSession.project.revision + 1L),
        )
    }
}

private class FakeMidiAudition : MidiAuditionPort {
    private var current = MidiAuditionState()
    private val history = mutableListOf(current)

    override val state: MidiAuditionState get() = current
    override val stateHistory: List<MidiAuditionState> get() = history.toList()
    var playProblem: app.melotrail.audition.MidiAuditionProblem? = null

    override fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        record(current.copy(scope = plan.view.scope, window = plan.view.window, positionTick = plan.startTick, mutedRoles = plan.mutedRoles, soloRoles = plan.soloRoles))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.SELECT_SCOPE, current)
    }

    override fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        playProblem?.let { return MidiAuditionResult.Failed(it, current) }
        selectScope(plan)
        record(current.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = 1L))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.PLAY, current)
    }

    override fun play(): MidiAuditionResult {
        record(current.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = current.sessionId ?: 1L))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.PLAY, current)
    }

    override fun pause(): MidiAuditionResult {
        record(current.copy(playback = MidiAuditionPlaybackState.PAUSED))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.PAUSE, current)
    }

    override fun stop(): MidiAuditionResult {
        record(current.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.STOP, current)
    }

    override fun seek(tick: Long): MidiAuditionResult {
        record(current.copy(positionTick = tick))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.SEEK, current)
    }

    override fun setLoop(loop: MidiAuditionLoop?): MidiAuditionResult {
        record(current.copy(loop = loop))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.LOOP, current)
    }

    override fun setMutedRole(role: MidiExportRole, muted: Boolean): MidiAuditionResult = MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.MUTE, current)

    override fun setSoloRole(role: MidiExportRole, solo: Boolean): MidiAuditionResult = MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.SOLO, current)

    override fun close() { record(current.copy(isClosed = true, playback = MidiAuditionPlaybackState.STOPPED, sessionId = null)) }

    private fun record(next: MidiAuditionState) {
        current = next
        history += next
    }
}

private fun fakeSourcePlan(): MidiAuditionPlaybackPlan = MidiAuditionPlaybackPlan(
    app.melotrail.audition.MidiAuditionView.sourceMelody(
        app.melotrail.midi.domain.MidiExportSong(
            app.melotrail.midi.domain.MidiPpq(480),
            "fake-source",
            500_000,
            4,
            2,
            emptyList(),
            listOf(app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.MELODY, emptyList())),
            1L,
        ),
    ),
)
