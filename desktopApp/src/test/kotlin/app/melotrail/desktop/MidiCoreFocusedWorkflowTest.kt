package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreCandidateLifecycle
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreExportSnapshotLifecycle
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreSourceAudition
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.audition.MidiAuditionAction
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.audition.MidiAuditionPort
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionState
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import javax.imageio.ImageIO
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreFocusedWorkflowTest {
    @Test
    fun `six target pages complete a real MIDI Core workflow and reopen an immutable export`() = runSkikoComposeUiTest(size = Size(1280f, 900f)) {
        val temporaryRoot = Files.createTempDirectory("melotrail-mc040-")
        val projectRoot = temporaryRoot.resolve("focused-project")
        val source = writeSourceMidi(temporaryRoot.resolve("input/source.mid"))
        val preferences = WorkflowPreferences()
        val audition = WorkflowFakeMidiAudition()
        val workspace = newWorkspace(MidiCoreArtifactStore(), audition, preferences)
        val projectActions = MidiCoreProjectPageActions(
            chooseNewProjectDirectory = { projectRoot },
        )
        val midiActions = MidiCoreMidiPageActions(
            chooseMidiSource = { source },
        )

        try {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        workspace = workspace,
                        projectActions = projectActions,
                        midiActions = midiActions,
                    )
                }
            }
            fun awaitWorkspaceSuccess(action: String) {
                var attempts = 0
                while (workspace.state.value.operation.active && attempts < 3_000) {
                    Thread.sleep(10)
                    attempts += 1
                }
                assertTrue(!workspace.state.value.operation.active, "$action did not finish in time")
                assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, workspace.state.value.operation.phase, action)
            }
            fun navigateTo(destination: MidiCoreWorkspaceDestination) {
                onNodeWithTag(MidiCoreWorkspaceShellTags.destination(destination)).performClick()
                waitForIdle()
            }
            fun captureFixture(name: String) {
                val image = onRoot().captureToImage().toAwtImage()
                assertTrue(image.width > 0 && image.height > 0, "$name visual fixture must be non-empty")
                val target = visualFixtureRoot().resolve("$name.png")
                Files.createDirectories(target.parent)
                assertTrue(ImageIO.write(image, "png", target.toFile()), "$name visual fixture must be writable")
            }

            onNodeWithTag(MidiCoreProjectPageTags.CHOOSE_NEW_LOCATION).performClick()
            waitForIdle()
            onNodeWithTag(MidiCoreProjectPageTags.NAME).performTextInput("Focused workflow")
            onNodeWithTag(MidiCoreProjectPageTags.CREATE).performClick()
            awaitWorkspaceSuccess("create project")
            captureFixture("project")

            navigateTo(MidiCoreWorkspaceDestination.MIDI)
            onNodeWithTag(MidiCoreMidiPageTags.IMPORT).performClick()
            awaitWorkspaceSuccess("import source")
            onNodeWithTag(MidiCoreMidiPageTags.PLAY).performClick()
            awaitWorkspaceSuccess("play imported source")
            assertEquals(MidiAuditionPlaybackState.PLAYING, audition.state.playback)
            workspace.accept(MidiCoreWorkspaceIntent.StopAudition)
            captureFixture("midi")

            navigateTo(MidiCoreWorkspaceDestination.STRUCTURE_HARMONY)
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.CONFIRM_AUTHORITY).performScrollTo().performClick()
            awaitWorkspaceSuccess("confirm authority")
            val sourceEndTick = checkNotNull(workspace.state.value.project?.sourceMidi?.sourceEndTick)
            workspace.accept(
                MidiCoreWorkspaceIntent.ReplaceStructure(
                    definitions = listOf(ProjectSectionDefinition("verse", "Verse")),
                    occurrences = listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse 1", 1)),
                ),
            )
            awaitWorkspaceSuccess("save structure")
            workspace.accept(
                MidiCoreWorkspaceIntent.ReplaceHarmony(
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, sourceEndTick)),
                ),
            )
            awaitWorkspaceSuccess("save harmony")
            navigateTo(MidiCoreWorkspaceDestination.MIDI)
            navigateTo(MidiCoreWorkspaceDestination.STRUCTURE_HARMONY)
            captureFixture("structure-harmony")

            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            CandidateRole.entries.forEachIndexed { index, role ->
                onNodeWithTag(MidiCoreArrangePageTags.role(role)).performScrollTo().performClick()
                waitForIdle()
                onNodeWithTag(MidiCoreArrangePageTags.GENERATE).performScrollTo().assertIsEnabled().performClick()
                awaitWorkspaceSuccess("generate ${role.name.lowercase()} candidate")
                assertEquals(role, workspace.state.value.review.role)
                assertTrue(workspace.state.value.review.candidates.isNotEmpty(), "generated alternative must appear without a manual refresh")
                onNodeWithTag(MidiCoreArrangePageTags.REVIEW).performScrollTo().assertIsEnabled().performClick()
                waitForIdle()
                if (workspace.state.value.operation.active) awaitWorkspaceSuccess("load ${role.name.lowercase()} review")
                onNodeWithTag(MidiCoreReviewPageTags.PLAY_SELECTED).performScrollTo().assertIsEnabled().performClick()
                awaitWorkspaceSuccess("play ${role.name.lowercase()} candidate")
                onNodeWithTag(MidiCoreReviewPageTags.ACCEPT_SELECTED).performScrollTo().assertIsEnabled().performClick()
                awaitWorkspaceSuccess("accept ${role.name.lowercase()} candidate")
                assertTrue(workspace.state.value.review.candidates.single { it.candidate.id == workspace.state.value.review.selectedCandidateId }.accepted)
                if (index < CandidateRole.entries.lastIndex) {
                    onNodeWithTag(MidiCoreReviewPageTags.NEXT_SCOPE).performScrollTo().assertIsEnabled().performClick()
                    waitForIdle()
                }
            }
            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            captureFixture("arrange")

            navigateTo(MidiCoreWorkspaceDestination.REVIEW)
            if (workspace.state.value.operation.active) awaitWorkspaceSuccess("load review evidence")
            onNodeWithTag(MidiCoreReviewPageTags.PLAY_ARRANGEMENT).performScrollTo().performClick()
            awaitWorkspaceSuccess("play accepted arrangement")
            assertEquals(MidiAuditionPlaybackState.PLAYING, audition.state.playback)
            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            navigateTo(MidiCoreWorkspaceDestination.REVIEW)
            captureFixture("review")

            navigateTo(MidiCoreWorkspaceDestination.PROJECT)
            onNodeWithTag(MidiCoreProjectPageTags.CLOSE).performScrollTo().performClick()
            var closeAttempts = 0
            while (workspace.state.value.project != null && closeAttempts < 100) {
                Thread.sleep(10)
                closeAttempts += 1
            }
            assertEquals(null, workspace.state.value.project)
            onNodeWithTag(MidiCoreProjectPageTags.OPEN_RECENT).performClick()
            awaitWorkspaceSuccess("reopen project")
            val reopened = checkNotNull(workspace.state.value.project)
            assertEquals(CandidateRole.entries.size, reopened.acceptances.size)
            assertTrue(reopened.authority?.chordEvents?.isNotEmpty() == true)

            navigateTo(MidiCoreWorkspaceDestination.EXPORT)
            captureFixture("export")
            onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().performClick()
            awaitWorkspaceSuccess("export immutable MIDI package")
            val snapshot = assertNotNull(workspace.state.value.export.latestSnapshot)
            val packageDirectory = projectRoot.resolve("exports").resolve(snapshot.id)
            assertTrue(Files.isRegularFile(packageDirectory.resolve("complete-song.mid")))
            assertTrue(Files.isRegularFile(packageDirectory.resolve("manifest.json")))

            assertEquals(
                listOf("arrange", "export", "midi", "project", "review", "structure-harmony"),
                capturedFixtureNames(),
            )
        } finally {
            workspace.close()
            deleteTree(temporaryRoot)
        }
    }

    private fun capturedFixtureNames(): List<String> = Files.list(visualFixtureRoot()).use { paths ->
        paths.map { it.fileName.toString().removeSuffix(".png") }.sorted().toList()
    }

    private fun visualFixtureRoot(): Path = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath()
        .resolve("build/test-results/midi-core-focused-workflow/wide")

    private fun deleteTree(root: Path) {
        if (Files.notExists(root)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun writeSourceMidi(path: Path): Path {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        val name = "Lead".encodeToByteArray()
        track.add(MidiEvent(MetaMessage(0x03, name, name.size), 0L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1_920L))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }
}

private fun newWorkspace(
    artifacts: MidiCoreArtifactStore,
    audition: MidiAuditionPort,
    preferences: MidiCoreDesktopPreferences,
): MidiCoreWorkspaceViewModel {
    val lifecycle = MidiCoreProjectLifecycle(artifacts)
    val candidateLifecycle = MidiCoreCandidateLifecycle(artifacts)
    val generation = MidiCoreCandidateGeneration(artifacts = artifacts, lifecycle = candidateLifecycle)
    val review = MidiCoreCandidateReview(artifacts = artifacts, lifecycle = candidateLifecycle, generation = generation)
    val useCases = DefaultMidiCoreWorkspaceUseCases(
        project = lifecycle,
        sourceImport = MidiCoreSourceImport(artifacts),
        authority = MidiCoreMusicalAuthority(artifacts),
        structure = MidiCoreStructureTimeline(artifacts),
        harmony = MidiCoreAuthoritativeHarmony(artifacts),
        generation = generation,
        review = review,
        exporter = MidiCoreMidiPackageExporter(
            artifacts = artifacts,
            assembly = MidiCoreAcceptedSongAssembly(artifacts),
            snapshotLifecycle = MidiCoreExportSnapshotLifecycle(artifacts),
        ),
        audition = audition,
        sourceAudition = MidiCoreSourceAudition(artifacts),
    )
    return MidiCoreWorkspaceViewModel(
        useCases,
        preferences,
        NoOpDesktopOperationLogger,
        MidiCoreWorkspaceDispatchers(WorkflowImmediateDispatcher, WorkflowImmediateDispatcher),
    )
}

private object WorkflowImmediateDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

private class WorkflowPreferences : MidiCoreDesktopPreferences {
    private var lastOpened: Path? = null

    override fun lastOpenedProject(): Path? = lastOpened
    override fun saveLastOpenedProject(root: Path) { lastOpened = root }
    override fun clearLastOpenedProject() { lastOpened = null }
}

private class WorkflowFakeMidiAudition : MidiAuditionPort {
    private var current = MidiAuditionState()
    private val history = mutableListOf(current)

    override val state: MidiAuditionState get() = current
    override val stateHistory: List<MidiAuditionState> get() = history.toList()

    override fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult = apply(MidiAuditionAction.SELECT_SCOPE) {
        it.copy(scope = plan.view.scope, window = plan.view.window, positionTick = plan.startTick, mutedRoles = plan.mutedRoles, soloRoles = plan.soloRoles)
    }

    override fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        selectScope(plan)
        return apply(MidiAuditionAction.PLAY) { it.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = 1L) }
    }

    override fun play(): MidiAuditionResult = apply(MidiAuditionAction.PLAY) { it.copy(playback = MidiAuditionPlaybackState.PLAYING, sessionId = it.sessionId ?: 1L) }
    override fun pause(): MidiAuditionResult = apply(MidiAuditionAction.PAUSE) { it.copy(playback = MidiAuditionPlaybackState.PAUSED) }
    override fun stop(): MidiAuditionResult = apply(MidiAuditionAction.STOP) { it.copy(playback = MidiAuditionPlaybackState.STOPPED, sessionId = null) }
    override fun seek(tick: Long): MidiAuditionResult = apply(MidiAuditionAction.SEEK) { it.copy(positionTick = tick) }
    override fun setLoop(loop: MidiAuditionLoop?): MidiAuditionResult = apply(MidiAuditionAction.LOOP) { it.copy(loop = loop) }
    override fun setMutedRole(role: MidiExportRole, muted: Boolean): MidiAuditionResult = apply(MidiAuditionAction.MUTE) { it.copy(mutedRoles = if (muted) it.mutedRoles + role else it.mutedRoles - role) }
    override fun setSoloRole(role: MidiExportRole, solo: Boolean): MidiAuditionResult = apply(MidiAuditionAction.SOLO) { it.copy(soloRoles = if (solo) it.soloRoles + role else it.soloRoles - role) }
    override fun selectOutputDevice(outputDeviceId: String?): MidiAuditionResult = apply(MidiAuditionAction.SELECT_OUTPUT) { it.copy(outputDeviceId = outputDeviceId) }
    override fun close() { record(current.copy(isClosed = true, playback = MidiAuditionPlaybackState.STOPPED, sessionId = null)) }

    private fun apply(action: MidiAuditionAction, transform: (MidiAuditionState) -> MidiAuditionState): MidiAuditionResult {
        record(transform(current))
        return MidiAuditionResult.Applied(action, current)
    }

    private fun record(next: MidiAuditionState) {
        current = next
        history += next
    }
}
