package app.melotrail.desktop

import app.melotrail.application.ConfirmMidiCoreAuthority
import app.melotrail.application.ConfirmMidiCoreArrangementExtent
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
import app.melotrail.application.MidiCoreSourceImportProblemCode
import app.melotrail.application.MidiCoreAuthorityProblem
import app.melotrail.application.MidiCoreStructureTimelineProblem
import app.melotrail.application.MidiCoreAuthoritativeHarmonyProblem
import app.melotrail.application.MidiCorePackageExportProblem
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.ReplaceMidiCoreHarmony
import app.melotrail.application.ReplaceMidiCoreStructure
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
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiFindingCode
import app.melotrail.midi.domain.MidiFindingScope
import app.melotrail.midi.domain.MidiFindingSeverity
import app.melotrail.midi.domain.MidiImportValidationResult
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.CandidateRole
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    fun `arrangement extent intent is routed through the workspace use case`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementExtent(padToNextBar = true))
        advanceUntilIdle()

        assertEquals(listOf(true), fake.arrangementExtentRequests.map(ConfirmMidiCoreArrangementExtent::padToNextBar))
        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        viewModel.close()
    }

    @Test
    fun `arrangement-plan proposal stays session-only until one explicit confirmation and clears on cancel and close`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        val before = requireNotNull(viewModel.state.value.project)

        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        assertEquals(1, fake.proposeArrangementPlanRequests.size)
        assertEquals(before, viewModel.state.value.project)
        assertNotNull(viewModel.state.value.arrangementPlanProposal.proposal)

        fake.advanceRevisionWithoutReplacingSession()
        viewModel.accept(MidiCoreWorkspaceIntent.ReloadProject)
        advanceUntilIdle()
        assertNull(viewModel.state.value.arrangementPlanProposal.proposal)

        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.CancelArrangementPlan)
        advanceUntilIdle()
        assertEquals(1, fake.cancelArrangementPlanRequests.size)
        assertEquals(fake.persistedSession().project, viewModel.state.value.project)
        assertNull(viewModel.state.value.arrangementPlanProposal.proposal)

        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
        advanceUntilIdle()
        assertEquals(1, fake.confirmArrangementPlanRequests.size)
        assertNotNull(viewModel.state.value.project?.arrangementPlan)
        assertNull(viewModel.state.value.arrangementPlanProposal.proposal)

        viewModel.accept(MidiCoreWorkspaceIntent.CloseProject)
        assertNull(viewModel.state.value.arrangementPlanProposal.proposal)
        viewModel.close()
    }

    @Test
    fun `confirmed purpose and phrase edits require an impact preview before the workspace saves them`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
        advanceUntilIdle()
        val confirmed = requireNotNull(viewModel.state.value.project?.arrangementPlan)
        val changed = confirmed.copy(occurrences = confirmed.occurrences.map { occurrence ->
            if (occurrence.occurrenceId == "verse-1") occurrence.copy(
                purpose = app.melotrail.project.MidiCoreArrangementPurpose.BRIDGE,
                phraseGroupId = "phrase-bridge",
            ) else occurrence
        })

        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlanEdit(changed))
        assertTrue(fake.confirmArrangementPlanEditRequests.isEmpty())
        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementPlanEdit(changed))
        advanceUntilIdle()
        assertEquals(listOf(changed), fake.previewArrangementPlanEditRequests.map { it.plan })
        assertEquals(changed, viewModel.state.value.arrangementPlanEdit.plan)
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlanEdit(changed))
        advanceUntilIdle()

        assertEquals(listOf(changed), fake.confirmArrangementPlanEditRequests.map { it.plan })
        assertEquals(changed, viewModel.state.value.project?.arrangementPlan)
        assertNull(viewModel.state.value.arrangementPlanEdit.plan)
        viewModel.close()
    }

    @Test
    fun `musical repair preview is write-free and apply keeps candidate acceptance explicit`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
        advanceUntilIdle()
        val before = requireNotNull(viewModel.state.value.project)
        val proposal = app.melotrail.application.MidiCoreMusicalRepairPlanner.propose(
            requireNotNull(before.arrangementPlan), "verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
        )
        fake.musicalRepairResult = app.melotrail.application.MidiCoreMusicalRepairResult.Prepared(
            proposal,
            MidiCoreInvalidationPlanner.preview(
                app.melotrail.project.MidiCoreAuthorityHasher.from(before),
                app.melotrail.project.MidiCoreAuthorityHasher.from(before.copy(arrangementPlan = proposal.plan)),
            ),
            emptyList(),
            emptyList(),
        )

        viewModel.accept(MidiCoreWorkspaceIntent.PreviewMusicalRepair("verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO))
        advanceUntilIdle()
        assertEquals(before, viewModel.state.value.project)
        assertEquals(proposal, viewModel.state.value.musicalRepair.prepared?.proposal)
        assertTrue(fake.confirmArrangementPlanEditRequests.isEmpty())
        fake.musicalRepairAlternativesResult = app.melotrail.application.MidiCoreMusicalRepairAlternativesResult.Ranked(
            MidiCoreProjectSession(fake.persistedSession().root, before),
            before.revision,
            app.melotrail.application.MidiCoreMusicalRepairAlternativeRanking(
                emptyList(),
                emptyList(),
                "Three bounded attempts produced no valid result; retry this same scope.",
            ),
        )

        viewModel.accept(MidiCoreWorkspaceIntent.ApplyMusicalRepair)
        advanceUntilIdle()
        assertEquals(listOf(proposal.plan), fake.confirmArrangementPlanEditRequests.map { it.plan })
        assertEquals(proposal.plan, viewModel.state.value.project?.arrangementPlan)
        assertTrue(viewModel.state.value.project?.acceptances.orEmpty().isEmpty())
        assertEquals(proposal, viewModel.state.value.musicalRepair.prepared?.proposal)
        assertTrue(viewModel.state.value.musicalRepair.applied)
        assertEquals(3, fake.candidateGenerationRequests.size)
        assertEquals(3, fake.candidateGenerationRequests.map { it.patternId }.distinct().size)
        assertTrue(fake.candidateGenerationRequests.all {
            it.role == CandidateRole.CHORDS && it.occurrenceId == "verse-1" && it.useDraftDependencies &&
                it.generator.generatorVersion.contains("patterns-v") && it.generator.generatorVersion.contains("profiles-v")
        })
        assertTrue(viewModel.state.value.musicalRepair.noResultReason?.contains("same scope") == true)
        val firstAttempt = fake.candidateGenerationRequests.toList()
        viewModel.accept(MidiCoreWorkspaceIntent.ApplyMusicalRepair)
        advanceUntilIdle()
        assertEquals(1, fake.confirmArrangementPlanEditRequests.size, "Retry must not apply the density adjustment again")
        assertEquals(proposal.plan, viewModel.state.value.project?.arrangementPlan)
        assertEquals(6, fake.candidateGenerationRequests.size)
        assertEquals(firstAttempt.map { it.sectionPolicy }, fake.candidateGenerationRequests.drop(3).map { it.sectionPolicy })
        assertEquals(firstAttempt.map { it.generator }, fake.candidateGenerationRequests.drop(3).map { it.generator })

        viewModel.close()
    }

    @Test
    fun `locked musical repair is blocked before its plan can be written`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
        advanceUntilIdle()
        val project = requireNotNull(viewModel.state.value.project)
        val proposal = app.melotrail.application.MidiCoreMusicalRepairPlanner.propose(
            requireNotNull(project.arrangementPlan), "verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
        )
        fake.musicalRepairResult = app.melotrail.application.MidiCoreMusicalRepairResult.Prepared(
            proposal,
            MidiCoreInvalidationPlanner.preview(
                app.melotrail.project.MidiCoreAuthorityHasher.from(project),
                app.melotrail.project.MidiCoreAuthorityHasher.from(project.copy(arrangementPlan = proposal.plan)),
            ),
            listOf("locked-chords"),
            listOf(app.melotrail.project.MidiCoreAuthorityScopeKey("verse-1", CandidateRole.CHORDS)),
        )

        viewModel.accept(MidiCoreWorkspaceIntent.PreviewMusicalRepair("verse-1", app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ApplyMusicalRepair)

        assertTrue(fake.confirmArrangementPlanEditRequests.isEmpty())
        assertEquals(MidiCoreWorkspaceBlockerCode.CANDIDATE_REVIEW_REQUIRED, viewModel.state.value.blockers.first().code)
        assertEquals(project, viewModel.state.value.project)
        viewModel.close()
    }

    @Test
    fun `real position observation updates only while playing and stops after pause`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeSourcePlan())
        fake.audition.observedPositionTick = 480L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = MidiCoreWorkspaceViewModel(
            fake,
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            MidiCoreWorkspaceDispatchers(ui = dispatcher, io = dispatcher, position = dispatcher),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        runCurrent()
        assertEquals(480L, viewModel.state.value.audition.positionTick)
        assertEquals(1, fake.audition.positionObservations)

        viewModel.accept(MidiCoreWorkspaceIntent.PauseAudition)
        runCurrent()
        advanceTimeBy(2 * 66L)
        runCurrent()
        assertEquals(1, fake.audition.positionObservations)
        assertEquals(MidiAuditionPlaybackState.PAUSED, viewModel.state.value.audition.playback)
        viewModel.close()
    }

    @Test
    fun `position observer stops after a device loss reported during playback`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeSourcePlan())
        fake.audition.observedProblem = app.melotrail.audition.MidiAuditionProblem(
            app.melotrail.audition.MidiAuditionProblemCode.DEVICE_LOST,
            "MIDI device disappeared",
            "Reconnect the MIDI device and retry.",
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = MidiCoreWorkspaceViewModel(
            fake,
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            MidiCoreWorkspaceDispatchers(ui = dispatcher, io = dispatcher, position = dispatcher),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        runCurrent()
        advanceTimeBy(2 * 66L)
        runCurrent()

        assertEquals(1, fake.audition.positionObservations)
        assertEquals(MidiAuditionPlaybackState.STOPPED, viewModel.state.value.audition.playback)
        assertEquals(app.melotrail.audition.MidiAuditionProblemCode.DEVICE_LOST, viewModel.state.value.audition.lastProblem?.code)
        viewModel.close()
    }

    @Test
    fun `position observer remains single through section navigation and is disposed when the project closes`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeSourcePlan())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = MidiCoreWorkspaceViewModel(
            fake,
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            MidiCoreWorkspaceDispatchers(ui = dispatcher, io = dispatcher, position = dispatcher),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        runCurrent()
        assertEquals(1, fake.audition.positionObservations)

        viewModel.accept(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-1"))
        runCurrent()
        assertEquals(1, fake.audition.positionObservations)
        advanceTimeBy(66L)
        runCurrent()
        assertEquals(2, fake.audition.positionObservations)

        viewModel.accept(MidiCoreWorkspaceIntent.CloseProject)
        runCurrent()
        advanceTimeBy(2 * 66L)
        runCurrent()
        assertEquals(2, fake.audition.positionObservations)
        viewModel.close()
    }

    @Test
    fun `occurrence audition prepares a separate MIDI occurrence scope`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeOccurrencePlan())
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PlayOccurrence("verse-1"))
        advanceUntilIdle()

        assertEquals(1, fake.occurrenceAuditionCalls)
        assertEquals(app.melotrail.audition.MidiAuditionScope.Occurrence("verse-1"), viewModel.state.value.audition.scope)
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, viewModel.state.value.operation.phase)
        viewModel.close()
    }

    @Test
    fun `song-map selection preserves the selected section and loops its authoritative range in the shared player`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementStyle("open-sky", "verse-1"))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-1"))

        assertEquals("verse-1", viewModel.state.value.arrangement.selectedOccurrenceId)
        assertEquals(MidiAuditionLoop(0L, 1920L), viewModel.state.value.audition.loop)
        assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, viewModel.state.value.operation.phase)
        viewModel.close()
    }

    @Test
    fun `Review audition prepares candidate and accepted arrangement scopes without project mutation`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        val projectBeforeAudition = viewModel.state.value.project

        viewModel.accept(MidiCoreWorkspaceIntent.PlayCandidate("candidate-review", app.melotrail.project.CandidateRole.CHORDS, "verse-1"))
        advanceUntilIdle()
        assertEquals(app.melotrail.audition.MidiAuditionScope.Candidate("candidate-review", MidiExportRole.CHORDS), viewModel.state.value.audition.scope)

        viewModel.accept(MidiCoreWorkspaceIntent.PlayAcceptedArrangement)
        advanceUntilIdle()
        assertEquals(app.melotrail.audition.MidiAuditionScope.AcceptedArrangement, viewModel.state.value.audition.scope)
        assertEquals(projectBeforeAudition, viewModel.state.value.project)
        viewModel.close()
    }

    @Test
    fun `rapid style previews are latest-wins and a device failure retains the selected style`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val firstPreview = CompletableDeferred<app.melotrail.application.MidiCoreArrangementStylePreviewResult>()
        fake.pendingStylePreview = firstPreview
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        val projectBeforePreview = viewModel.state.value.project

        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementStyle("open-sky", "verse-1"))
        advanceUntilIdle()
        assertEquals(MidiCoreWorkspaceOperationPhase.RUNNING, viewModel.state.value.operation.phase)
        fake.pendingStylePreview = null
        fake.stylePreviewResult = fakeStylePreviewResult("rising-room")
        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementStyle("rising-room", "verse-1"))
        advanceUntilIdle()

        assertEquals(listOf("open-sky", "rising-room"), fake.stylePreviewRequests.map { it.styleId })
        assertEquals("rising-room", viewModel.state.value.stylePreview.selectedStyleId)
        assertEquals(MidiAuditionScope.StylePreview("rising-room", "verse-1"), viewModel.state.value.audition.scope)
        assertEquals(projectBeforePreview, viewModel.state.value.project)

        fake.stylePreviewResult = fakeStylePreviewResult("late-night")
        fake.audition.playProblem = app.melotrail.audition.MidiAuditionProblem(
            app.melotrail.audition.MidiAuditionProblemCode.DEVICE_UNAVAILABLE,
            "No MIDI output device is available.",
            "Connect a MIDI output and retry.",
        )
        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementStyle("late-night", "verse-1"))
        advanceUntilIdle()

        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals("late-night", viewModel.state.value.stylePreview.selectedStyleId)
        assertEquals(projectBeforePreview, viewModel.state.value.project)
        viewModel.close()
    }

    @Test
    fun `project close and authority changes stop and clear the active MIDI target`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.sourceAuditionResult = app.melotrail.application.MidiCoreSourceAuditionResult.Ready(fakeSourcePlan())
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmAuthority)
        advanceUntilIdle()
        assertEquals(MidiAuditionPlaybackState.STOPPED, fake.audition.state.playback)
        assertEquals(null, viewModel.state.value.audition.scope)

        viewModel.accept(MidiCoreWorkspaceIntent.PlaySourceMelody)
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.CloseProject)
        assertEquals(MidiAuditionPlaybackState.STOPPED, fake.audition.state.playback)
        assertEquals(null, viewModel.state.value.audition.scope)
        assertNull(viewModel.state.value.project)
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
    fun `complete draft intent preserves retry identity after a scoped incomplete result`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.CreateArrangementDraft("late-night", rootSeed = 17L))
        advanceUntilIdle()

        assertEquals(1, fake.draftRequests.size)
        assertEquals("late-night", fake.draftRequests.single().styleId)
        assertEquals(17L, fake.draftRequests.single().rootSeed)
        assertEquals(MidiCoreWorkspaceOperationKind.DRAFT_GENERATION, viewModel.state.value.operation.kind)
        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals("draft-incomplete", viewModel.state.value.arrangement.incompleteDraftId)
        assertEquals(
            MidiCoreWorkspaceIntent.CreateArrangementDraft("late-night", 17L, "draft-incomplete"),
            viewModel.state.value.operation.retry,
        )
        viewModel.close()
    }

    @Test
    fun `reviewed plan confirms and starts the full draft in the third Arrange action`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()

        viewModel.accept(MidiCoreWorkspaceIntent.PreviewArrangementStyle("late-night", "verse-1"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
        advanceUntilIdle()
        viewModel.accept(MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft("late-night", 17L))
        advanceUntilIdle()

        assertEquals(1, fake.stylePreviewRequests.size)
        assertEquals(1, fake.proposeArrangementPlanRequests.size)
        assertEquals(1, fake.confirmArrangementPlanRequests.size)
        assertEquals(1, fake.draftRequests.size)
        assertEquals("late-night", fake.draftRequests.single().styleId)
        assertEquals(17L, fake.draftRequests.single().rootSeed)
        assertNull(viewModel.state.value.arrangementPlanProposal.proposal)
        assertEquals(MidiCoreWorkspaceIntent.CreateArrangementDraft("late-night", 17L, "draft-incomplete"), viewModel.state.value.operation.retry)
        viewModel.close()
    }

    @Test
    fun `thrown generation after confirmation retains saved plan and retries only the draft`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        val vm = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        vm.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root)); advanceUntilIdle()
        vm.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night")); advanceUntilIdle()
        fake.draftFailure = IllegalStateException("owned generation failure")
        vm.accept(MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft("late-night", 17L)); advanceUntilIdle()
        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, vm.state.value.operation.phase)
        assertEquals(fake.persistedSession().project, vm.state.value.project)
        assertNotNull(vm.state.value.project?.arrangementPlan)
        assertNull(vm.state.value.arrangementPlanProposal.proposal)
        val retry = kotlin.test.assertIs<MidiCoreWorkspaceIntent.CreateArrangementDraft>(vm.state.value.operation.retry)
        assertNotNull(retry.draftId)
        fake.draftFailure = null
        vm.accept(retry); advanceUntilIdle()
        assertEquals(1, fake.confirmArrangementPlanRequests.size)
        assertEquals(fake.draftRequests.first().draftId, fake.draftRequests.last().draftId)
        vm.close()
    }

    @Test
    fun `cancel at draft preparation or playback boundary stops playback and keeps confirmed plan`() = runTest {
        for (afterPlay in listOf(false, true)) {
            val fake = FakeMidiCoreWorkspaceUseCases()
            fake.seedPersistedSong()
            val vm = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
            vm.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root)); advanceUntilIdle()
            vm.accept(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night")); advanceUntilIdle()
            val draft = app.melotrail.project.MidiCoreArrangementDraft(
                "complete-draft", "late-night", 1, "a".repeat(64), 17L, emptyList(),
                app.melotrail.project.MidiCoreArrangementDraftValidationSummary(1, 0, true, "b".repeat(64)),
                "2026-09-11T00:00:00Z",
                listOf(app.melotrail.project.MidiCorePlannedRest("verse-1", CandidateRole.CHORDS, "a".repeat(64))),
            )
            fake.draftGenerationResult = app.melotrail.application.MidiCoreArrangementDraftGenerationResult.Completed(
                fake.persistedSession(), draft,
                app.melotrail.application.MidiCoreArrangementDraftProgress(draft.id, 1, emptyList()),
            )
            val cancel = { vm.accept(MidiCoreWorkspaceIntent.CancelOperation) }
            if (afterPlay) fake.audition.onPlay = cancel else fake.onPrepareDraft = cancel
            vm.accept(MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft("late-night", 17L)); advanceUntilIdle()
            assertEquals(MidiCoreWorkspaceOperationPhase.CANCELLED, vm.state.value.operation.phase)
            assertEquals(MidiAuditionPlaybackState.STOPPED, fake.audition.state.playback)
            assertEquals(MidiAuditionPlaybackState.STOPPED, vm.state.value.audition.playback)
            assertEquals(fake.persistedSession().project, vm.state.value.project)
            assertNotNull(vm.state.value.project?.arrangementPlan)
            assertNull(vm.state.value.arrangementPlanProposal.proposal)
            vm.close()
        }
    }

    @Test
    fun `export collision preserves the current project and offers the same safe retry`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.exportResult = MidiCoreMidiPackageExportResult.Rejected(
            MidiCorePackageExportProblem(
                app.melotrail.application.MidiCorePackageExportProblemCode.DESTINATION_COLLISION,
                "The target export snapshot directory already exists.",
                "Retry with a fresh snapshot identifier; existing evidence was preserved.",
            ),
        )
        val viewModel = MidiCoreWorkspaceViewModel(fake, MemoryMidiCorePreferences(), NoOpDesktopOperationLogger, testDispatchers(testScheduler))
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        val projectBeforeExport = viewModel.state.value.project

        viewModel.accept(MidiCoreWorkspaceIntent.ExportPackage)
        advanceUntilIdle()

        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals(MidiCoreWorkspaceIntent.ExportPackage, viewModel.state.value.operation.retry)
        assertEquals(MidiCoreWorkspaceBlockerCode.EXPORT_NOT_READY, viewModel.state.value.blockers.first().code)
        assertEquals("DESTINATION_COLLISION", viewModel.state.value.blockers.first().sourceCode)
        assertEquals(projectBeforeExport, viewModel.state.value.project)
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
    fun `missing last project explains recovery without manufacturing a project location`() = runTest {
        val viewModel = MidiCoreWorkspaceViewModel(
            FakeMidiCoreWorkspaceUseCases(),
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            testDispatchers(testScheduler),
        )

        viewModel.accept(MidiCoreWorkspaceIntent.OpenLastProject)

        assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, viewModel.state.value.operation.phase)
        assertEquals(MidiCoreWorkspaceBlockerCode.PROJECT_REQUIRED, viewModel.state.value.blockers.single().code)
        assertEquals(null, viewModel.state.value.blockers.single().action)
        assertEquals("Create a project or choose a project folder.", viewModel.state.value.blockers.single().nextAction)
        viewModel.close()
    }

    @Test
    fun `rejected import keeps scoped validation in the workspace without changing the project`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        val finding = MidiFinding(
            MidiFindingCode.TEMPO_MAP_UNSUPPORTED,
            MidiFindingSeverity.BLOCKING,
            MidiFindingScope.TEMPO,
            "Tempo changes are not supported in MIDI Core V1.",
            "Use one fixed tempo before importing.",
        )
        val validation = MidiImportValidationResult(listOf(finding))
        fake.sourceImportResult = MidiCoreSourceImportResult.Rejected(
            MidiCoreSourceImportProblem(
                MidiCoreSourceImportProblemCode.IMPORT_REJECTED,
                "The MIDI source has blocking structural issues and was not imported.",
                "Resolve the blocking findings shown in MIDI, then retry the import.",
            ),
            validation,
        )
        val viewModel = MidiCoreWorkspaceViewModel(
            fake,
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            testDispatchers(testScheduler),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.session.root))
        advanceUntilIdle()
        val projectBeforeImport = viewModel.state.value.project
        val intent = MidiCoreWorkspaceIntent.ImportSource(Path.of("tempo-map.mid"))

        viewModel.accept(intent)
        advanceUntilIdle()

        assertEquals(projectBeforeImport, viewModel.state.value.project)
        assertEquals(MidiCoreSourceStatus.REJECTED, viewModel.state.value.source.status)
        assertEquals(validation, viewModel.state.value.source.validation)
        assertEquals(listOf(finding), viewModel.state.value.source.findings)
        assertFalse(viewModel.state.value.source.reportAvailable)
        assertEquals(intent, viewModel.state.value.operation.retry)
        assertEquals("Resolve the blocking findings shown in MIDI, then retry the import.", viewModel.state.value.blockers.first().nextAction)
        viewModel.close()
    }

    @Test
    fun `rejected replacement attempt does not obscure the accepted source`() = runTest {
        val fake = FakeMidiCoreWorkspaceUseCases()
        fake.seedPersistedSong()
        fake.sourceImportResult = MidiCoreSourceImportResult.Rejected(
            MidiCoreSourceImportProblem(
                MidiCoreSourceImportProblemCode.SOURCE_ALREADY_IMPORTED,
                "This project already has an immutable source MIDI file.",
                "Create a new project to import a different source MIDI file.",
            ),
        )
        val viewModel = MidiCoreWorkspaceViewModel(
            fake,
            MemoryMidiCorePreferences(),
            NoOpDesktopOperationLogger,
            testDispatchers(testScheduler),
        )
        viewModel.accept(MidiCoreWorkspaceIntent.OpenProject(fake.persistedSession().root))
        advanceUntilIdle()
        val acceptedSource = viewModel.state.value.source
        val projectBeforeAttempt = viewModel.state.value.project

        viewModel.accept(MidiCoreWorkspaceIntent.ImportSource(Path.of("replacement.mid")))
        advanceUntilIdle()

        assertEquals(projectBeforeAttempt, viewModel.state.value.project)
        assertEquals(acceptedSource, viewModel.state.value.source)
        assertEquals(MidiCoreSourceStatus.IMPORTED, viewModel.state.value.source.status)
        viewModel.close()
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
    override fun visualEvidence(request: app.melotrail.application.ProjectMidiCoreVisualEvidence): app.melotrail.application.MidiCoreVisualEvidenceProjection =
        app.melotrail.application.MidiCoreVisualEvidenceProvider().project(request)
    var sourceAuditionResult: app.melotrail.application.MidiCoreSourceAuditionResult =
        app.melotrail.application.MidiCoreSourceAuditionResult.Rejected(
            app.melotrail.application.MidiCoreSourceAuditionProblem(
                app.melotrail.application.MidiCoreSourceAuditionProblemCode.MELODY_REQUIRED,
                "not used",
                "not used",
            ),
        )
    var sourceImportResult: MidiCoreSourceImportResult? = null
    val openResults = ArrayDeque<MidiCoreProjectLifecycleResult>()
    var pendingGeneration: CompletableDeferred<MidiCoreCandidateGenerationResult>? = null
    var draftFailure: Exception? = null
    var onPrepareDraft: () -> Unit = {}
    val draftRequests = mutableListOf<app.melotrail.application.GenerateMidiCoreArrangementDraft>()
    var draftGenerationResult: app.melotrail.application.MidiCoreArrangementDraftGenerationResult =
        app.melotrail.application.MidiCoreArrangementDraftGenerationResult.Incomplete(
            session,
            "draft-incomplete",
            app.melotrail.application.MidiCoreArrangementDraftProgress("draft-incomplete", 3, emptyList()),
            app.melotrail.application.MidiCoreArrangementDraftProblem(
                app.melotrail.application.MidiCoreArrangementDraftProblemCode.CANDIDATE_FAILURE,
                "The bass scope needs another attempt.",
                "Retry the incomplete draft.",
                app.melotrail.application.MidiCoreArrangementDraftScope("verse-1", CandidateRole.BASS),
            ),
        )
    var pendingStylePreview: CompletableDeferred<app.melotrail.application.MidiCoreArrangementStylePreviewResult>? = null
    val stylePreviewRequests = mutableListOf<app.melotrail.application.PrepareMidiCoreArrangementStylePreview>()
    var stylePreviewResult: app.melotrail.application.MidiCoreArrangementStylePreviewResult = fakeStylePreviewResult()
    var confirmAuthorityCalls = 0
    val proposeArrangementPlanRequests = mutableListOf<app.melotrail.application.ProposeMidiCoreArrangementPlan>()
    val confirmArrangementPlanRequests = mutableListOf<app.melotrail.application.ConfirmMidiCoreArrangementPlanProposal>()
    val cancelArrangementPlanRequests = mutableListOf<app.melotrail.application.CancelMidiCoreArrangementPlanProposal>()
    val previewArrangementPlanEditRequests = mutableListOf<app.melotrail.application.PreviewMidiCoreArrangementPlanEdit>()
    val confirmArrangementPlanEditRequests = mutableListOf<app.melotrail.application.ConfirmMidiCoreArrangementPlanEdit>()
    val candidateGenerationRequests = mutableListOf<GenerateMidiCoreCandidate>()
    var musicalRepairResult: app.melotrail.application.MidiCoreMusicalRepairResult =
        app.melotrail.application.MidiCoreMusicalRepairResult.Rejected(
            app.melotrail.application.MidiCoreMusicalRepairProblem(
                app.melotrail.application.MidiCoreMusicalRepairProblemCode.PLAN_REQUIRED,
                "not used",
                "not used",
            ),
        )
    var musicalRepairAlternativesResult: app.melotrail.application.MidiCoreMusicalRepairAlternativesResult =
        app.melotrail.application.MidiCoreMusicalRepairAlternativesResult.Ranked(session, session.project.revision,
            app.melotrail.application.MidiCoreMusicalRepairAlternativeRanking(emptyList(), emptyList(), "not used"))
    val arrangementExtentRequests = mutableListOf<ConfirmMidiCoreArrangementExtent>()
    var occurrenceAuditionCalls = 0
    var closeCalls = 0
    var exportResult: MidiCoreMidiPackageExportResult = MidiCoreMidiPackageExportResult.Rejected(
        MidiCorePackageExportProblem(
            app.melotrail.application.MidiCorePackageExportProblemCode.EXPORT_NOT_READY,
            "not used",
            "not used",
        ),
    )
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

    override fun importSource(request: ImportMidiCoreSource): MidiCoreSourceImportResult =
        requireNotNull(sourceImportResult) { "Source import result was not configured" }

    override fun prepareSourceAudition(request: app.melotrail.application.PrepareMidiCoreSourceAudition): app.melotrail.application.MidiCoreSourceAuditionResult = sourceAuditionResult

    override fun prepareOccurrenceAudition(request: app.melotrail.application.PrepareMidiCoreOccurrenceAudition): app.melotrail.application.MidiCoreSourceAuditionResult {
        occurrenceAuditionCalls += 1
        return sourceAuditionResult
    }

    override fun prepareCandidateAudition(request: app.melotrail.application.PrepareMidiCoreCandidateAudition): app.melotrail.application.MidiCoreReviewAuditionResult =
        app.melotrail.application.MidiCoreReviewAuditionResult.Ready(fakeCandidatePlan())

    override fun prepareAcceptedRoleAudition(request: app.melotrail.application.PrepareMidiCoreAcceptedRoleAudition): app.melotrail.application.MidiCoreReviewAuditionResult =
        app.melotrail.application.MidiCoreReviewAuditionResult.Ready(fakeAcceptedPlan())

    override fun prepareAcceptedOccurrenceAudition(request: app.melotrail.application.PrepareMidiCoreAcceptedOccurrenceAudition): app.melotrail.application.MidiCoreReviewAuditionResult =
        app.melotrail.application.MidiCoreReviewAuditionResult.Ready(fakeOccurrencePlan())

    override fun prepareAcceptedArrangementAudition(request: app.melotrail.application.PrepareMidiCoreAcceptedArrangementAudition): app.melotrail.application.MidiCoreReviewAuditionResult =
        app.melotrail.application.MidiCoreReviewAuditionResult.Ready(fakeAcceptedPlan())

    override fun prepareArrangementDraftAudition(request: app.melotrail.application.PrepareMidiCoreArrangementDraftAudition): app.melotrail.application.MidiCoreReviewAuditionResult {
        onPrepareDraft()
        return app.melotrail.application.MidiCoreReviewAuditionResult.Ready(fakeAcceptedPlan())
    }

    override suspend fun previewArrangementStyle(
        request: app.melotrail.application.PrepareMidiCoreArrangementStylePreview,
    ): app.melotrail.application.MidiCoreArrangementStylePreviewResult {
        stylePreviewRequests += request
        return pendingStylePreview?.await() ?: stylePreviewResult
    }

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

    override fun proposeArrangementPlan(
        request: app.melotrail.application.ProposeMidiCoreArrangementPlan,
    ): app.melotrail.application.MidiCoreArrangementPlanProposalResult {
        proposeArrangementPlanRequests += request
        val authority = requireNotNull(request.session.project.authority)
        val plan = app.melotrail.project.MidiCoreArrangementPlan(
            version = 1,
            sharedGroove = app.melotrail.project.MidiCoreSharedGrooveIntent(
                app.melotrail.project.MidiCoreGrooveFeel.STRAIGHT,
                app.melotrail.project.MidiCoreGrooveSubdivision.EIGHTH,
                app.melotrail.project.MidiCoreGrooveDrive.STEADY,
            ),
            occurrences = authority.occurrences.map { occurrence ->
                app.melotrail.project.MidiCoreOccurrenceArrangementPlan(
                    occurrence.id, app.melotrail.project.MidiCoreArrangementPurpose.VERSE,
                    "phrase-${occurrence.id}", "repeat-${occurrence.id}", 1, 50,
                    CandidateRole.entries.map { role -> app.melotrail.project.MidiCoreRolePlanSettings(role, app.melotrail.project.MidiCoreRoleActivity.SUPPORTING, 50, app.melotrail.project.MidiCoreRegisterPreference.MID) },
                    app.melotrail.project.MidiCoreBoundaryIntent.NONE, app.melotrail.project.MidiCoreBoundaryIntent.NONE,
                )
            },
        )
        return app.melotrail.application.MidiCoreArrangementPlanProposalResult.Proposed(
            request.session,
            app.melotrail.application.MidiCoreArrangementPlanProposal(request.styleId, 1, "d".repeat(64), plan),
        )
    }

    override fun confirmArrangementPlan(
        request: app.melotrail.application.ConfirmMidiCoreArrangementPlanProposal,
    ): app.melotrail.application.MidiCoreArrangementPlanProposalResult {
        confirmArrangementPlanRequests += request
        currentSession = MidiCoreProjectSession(
            currentSession.root,
            currentSession.project.copy(arrangementPlan = request.proposal.plan, revision = currentSession.project.revision + 1L),
        )
        return app.melotrail.application.MidiCoreArrangementPlanProposalResult.Confirmed(currentSession, request.proposal.plan)
    }

    override fun cancelArrangementPlan(
        request: app.melotrail.application.CancelMidiCoreArrangementPlanProposal,
    ): app.melotrail.application.MidiCoreArrangementPlanProposalResult {
        cancelArrangementPlanRequests += request
        return app.melotrail.application.MidiCoreArrangementPlanProposalResult.Cancelled(request.proposal)
    }

    override fun previewArrangementPlanEdit(
        request: app.melotrail.application.PreviewMidiCoreArrangementPlanEdit,
    ): app.melotrail.application.MidiCoreArrangementPlanEditResult {
        previewArrangementPlanEditRequests += request
        return app.melotrail.application.MidiCoreArrangementPlanEditResult.Previewed(
            MidiCoreInvalidationPlanner.preview(
                app.melotrail.project.MidiCoreAuthorityHasher.from(request.session.project),
                app.melotrail.project.MidiCoreAuthorityHasher.from(request.session.project.copy(arrangementPlan = request.plan)),
            ),
        )
    }

    override fun confirmArrangementPlanEdit(
        request: app.melotrail.application.ConfirmMidiCoreArrangementPlanEdit,
    ): app.melotrail.application.MidiCoreArrangementPlanEditResult {
        confirmArrangementPlanEditRequests += request
        currentSession = MidiCoreProjectSession(
            currentSession.root,
            currentSession.project.copy(arrangementPlan = request.plan, revision = currentSession.project.revision + 1L),
        )
        return app.melotrail.application.MidiCoreArrangementPlanEditResult.Confirmed(
            currentSession,
            request.plan,
            MidiCoreInvalidationPlanner.preview(
                app.melotrail.project.MidiCoreAuthorityHasher.from(request.session.project),
                app.melotrail.project.MidiCoreAuthorityHasher.from(currentSession.project),
            ),
        )
    }

    override fun previewMusicalRepair(
        request: app.melotrail.application.PreviewMidiCoreMusicalRepair,
    ): app.melotrail.application.MidiCoreMusicalRepairResult = musicalRepairResult

    override fun rankMusicalRepairAlternatives(
        request: app.melotrail.application.RankMidiCoreMusicalRepairAlternatives,
    ): app.melotrail.application.MidiCoreMusicalRepairAlternativesResult = musicalRepairAlternativesResult

    override fun confirmArrangementExtent(
        request: ConfirmMidiCoreArrangementExtent,
    ): app.melotrail.application.MidiCoreArrangementExtentResult {
        arrangementExtentRequests += request
        return app.melotrail.application.MidiCoreArrangementExtentResult.Rejected(
            app.melotrail.application.MidiCoreArrangementExtentProblem(
                app.melotrail.application.MidiCoreArrangementExtentProblemCode.STRUCTURE_PRESENT,
                "Sections already define the arrangement boundary.",
                "Edit sections first.",
            ),
        )
    }

    override fun replaceStructure(request: ReplaceMidiCoreStructure): MidiCoreStructureTimelineResult = error("not used")

    override fun replaceHarmony(request: ReplaceMidiCoreHarmony): MidiCoreAuthoritativeHarmonyResult = error("not used")

    override fun listCandidates(request: ListMidiCoreCandidates): MidiCoreCandidateReviewResult = error("not used")

    override fun compareCandidates(request: CompareMidiCoreCandidates): MidiCoreCandidateReviewResult = error("not used")

    override fun precedingPianoBoundary(session: MidiCoreProjectSession, occurrenceId: String, generatedIds: List<String>): app.melotrail.arrangement.core.MidiCorePianoVoicingBoundarySummary? = null

    override fun acceptBatch(request: app.melotrail.application.AcceptMidiCoreCandidateBatch): MidiCoreCandidateLifecycleResult = error("not used")

    override fun acceptCandidate(request: AcceptMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun rejectCandidate(request: RejectMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun lockCandidate(request: LockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun unlockCandidate(request: UnlockMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override fun restoreCandidate(request: RestoreMidiCoreCandidate): MidiCoreCandidateLifecycleResult = error("not used")

    override suspend fun generateCandidate(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult {
        candidateGenerationRequests += request
        return pendingGeneration?.await() ?: MidiCoreCandidateGenerationResult.Rejected(
            app.melotrail.application.MidiCoreCandidateProblem(
                app.melotrail.application.MidiCoreCandidateProblemCode.INVALID_CANDIDATE,
                "No valid repair candidate for the fake request.",
                "Retry this same scope.",
            ),
        )
    }

    override suspend fun regenerateCandidate(request: RegenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult = generateCandidate(request.generation)

    override suspend fun generateArrangementDraft(
        request: app.melotrail.application.GenerateMidiCoreArrangementDraft,
    ): app.melotrail.application.MidiCoreArrangementDraftGenerationResult {
        draftRequests += request
        draftFailure?.let { throw it }
        // The real draft service returns the exact session passed to it when
        // no candidate is published. Keep this fake revision-accurate after a
        // combined plan-confirmation/draft request.
        return when (val result = draftGenerationResult) {
            is app.melotrail.application.MidiCoreArrangementDraftGenerationResult.Incomplete -> result.copy(session = request.session)
            is app.melotrail.application.MidiCoreArrangementDraftGenerationResult.Cancelled -> result.copy(session = request.session)
            is app.melotrail.application.MidiCoreArrangementDraftGenerationResult.Completed -> result.copy(session = request.session)
        }
    }

    override fun useArrangementDraft(request: app.melotrail.application.UseMidiCoreArrangementDraft): app.melotrail.application.MidiCoreArrangementDraftAcceptanceResult =
        error("not used")

    override fun undoArrangementDraftAcceptance(request: app.melotrail.application.UndoMidiCoreArrangementDraftAcceptance): app.melotrail.application.MidiCoreArrangementDraftAcceptanceUndoResult =
        error("not used")

    override fun export(request: ExportMidiCorePackage): MidiCoreMidiPackageExportResult = exportResult

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

    fun seedPersistedSong() {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0L, 1_920L)),
            chordEvents = listOf(app.melotrail.project.AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1_920L)),
        )
        val source = app.melotrail.project.SourceMidiRecord(
            "source.mid", "a".repeat(64), 1, 480,
            app.melotrail.project.ProjectArtifact(app.melotrail.project.ProjectRelativePath("source/original.mid"), "a".repeat(64)),
            app.melotrail.project.ProjectArtifact(app.melotrail.project.ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            emptyList(), 1_920L,
        )
        currentSession = MidiCoreProjectSession(
            currentSession.root,
            currentSession.project.copy(
                sourceMidi = source,
                selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
                authority = authority,
                revision = currentSession.project.revision + 1L,
            ),
        )
    }
}

private class FakeMidiAudition : MidiAuditionPort {
    var onPlay: () -> Unit = {}
    private var current = MidiAuditionState()
    private val history = mutableListOf(current)

    override val state: MidiAuditionState get() = current
    override val stateHistory: List<MidiAuditionState> get() = history.toList()
    var playProblem: app.melotrail.audition.MidiAuditionProblem? = null
    var observedProblem: app.melotrail.audition.MidiAuditionProblem? = null
    var observedPositionTick: Long? = null
    var positionObservations = 0

    override fun observePosition(): MidiAuditionState {
        positionObservations += 1
        observedProblem?.let { record(current.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null, lastProblem = it)) }
        observedPositionTick?.let { record(current.copy(positionTick = it)) }
        return current
    }

    override fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        record(current.copy(scope = plan.view.scope, window = plan.view.window, positionTick = plan.startTick, mutedRoles = plan.mutedRoles, soloRoles = plan.soloRoles))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.SELECT_SCOPE, current)
    }

    override fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        playProblem?.let { return MidiAuditionResult.Failed(it, current) }
        selectScope(plan)
        record(current.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = 1L))
        onPlay()
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

    override fun selectOutputDevice(outputDeviceId: String?): MidiAuditionResult {
        record(current.copy(outputDeviceId = outputDeviceId))
        return MidiAuditionResult.Applied(app.melotrail.audition.MidiAuditionAction.SELECT_OUTPUT, current)
    }

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

private fun fakeOccurrencePlan(): MidiAuditionPlaybackPlan {
    val source = fakeSourcePlan()
    return MidiAuditionPlaybackPlan(
        app.melotrail.audition.MidiAuditionView.occurrence("verse-1", source.view.song, 0L, 1L),
    )
}

private fun fakeStylePreviewResult(styleId: String = "open-sky"): app.melotrail.application.MidiCoreArrangementStylePreviewResult.Ready {
    val song = app.melotrail.midi.domain.MidiExportSong(
        app.melotrail.midi.domain.MidiPpq(480),
        "fake-style-preview",
        500_000,
        4,
        2,
        emptyList(),
        listOf(
            app.melotrail.midi.domain.MidiExportRoleTrack(MidiExportRole.MELODY, emptyList()),
            app.melotrail.midi.domain.MidiExportRoleTrack(MidiExportRole.CHORDS, emptyList()),
            app.melotrail.midi.domain.MidiExportRoleTrack(MidiExportRole.BASS, emptyList()),
            app.melotrail.midi.domain.MidiExportRoleTrack(MidiExportRole.DRUMS, emptyList()),
        ),
        1920L,
    )
    return app.melotrail.application.MidiCoreArrangementStylePreviewResult.Ready(
        app.melotrail.application.MidiCoreArrangementStylePreviewKey("0".repeat(64), styleId, "verse-1", 1L),
        MidiAuditionPlaybackPlan(
            app.melotrail.audition.MidiAuditionView.stylePreview(styleId, "verse-1", song, 0L, 1920L),
            loop = MidiAuditionLoop(0L, 1920L),
        ),
        emptyList(),
        app.melotrail.application.MidiCoreArrangementStylePreviewCacheStatus.COLD,
        app.melotrail.application.MidiCoreArrangementStylePreviewPlanState.EPHEMERAL_STYLE_PROPOSAL,
    )
}

private fun fakeCandidatePlan(): MidiAuditionPlaybackPlan = MidiAuditionPlaybackPlan(
    app.melotrail.audition.MidiAuditionView.candidate(
        "candidate-review",
        app.melotrail.midi.domain.MidiExportRole.CHORDS,
        app.melotrail.midi.domain.MidiExportSong(
            app.melotrail.midi.domain.MidiPpq(480),
            "fake-candidate",
            500_000,
            4,
            2,
            emptyList(),
            listOf(app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.CHORDS, emptyList())),
            1L,
        ),
    ),
)

private fun fakeAcceptedPlan(): MidiAuditionPlaybackPlan = MidiAuditionPlaybackPlan(
    app.melotrail.audition.MidiAuditionView.accepted(
        app.melotrail.midi.domain.MidiExportSong(
            app.melotrail.midi.domain.MidiPpq(480),
            "fake-accepted",
            500_000,
            4,
            2,
            emptyList(),
            listOf(
                app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.MELODY, emptyList()),
                app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.CHORDS, emptyList()),
                app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.BASS, emptyList()),
                app.melotrail.midi.domain.MidiExportRoleTrack(app.melotrail.midi.domain.MidiExportRole.DRUMS, emptyList()),
            ),
            1L,
        ),
    ),
)
