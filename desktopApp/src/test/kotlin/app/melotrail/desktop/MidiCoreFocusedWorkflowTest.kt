package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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
import app.melotrail.midi.domain.MidiFindingCode
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals

@OptIn(ExperimentalTestApi::class)
class MidiCoreFocusedWorkflowTest {
    @Test
    fun `ready authority reaches an audible full draft in three Arrange actions`() {
        val temporaryRoot = Files.createTempDirectory("melotrail-u05-")
        val projectRoot = temporaryRoot.resolve("project")
        val artifacts = MidiCoreArtifactStore()
        val audition = WorkflowFakeMidiAudition()
        val workspace = newWorkspace(artifacts, audition, WorkflowPreferences())
        fun apply(intent: MidiCoreWorkspaceIntent) {
            workspace.accept(intent)
            awaitWorkspaceCompletion(workspace, intent.toString())
            assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, workspace.state.value.operation.phase,
                "${intent}: ${workspace.state.value.blockers}")
        }
        try {
            apply(MidiCoreWorkspaceIntent.CreateProject(projectRoot, "Arrange fixture"))
            apply(MidiCoreWorkspaceIntent.ImportSource(writeSourceMidi(temporaryRoot.resolve("source.mid"), pitch = 84)))
            apply(MidiCoreWorkspaceIntent.ConfirmAuthority)
            apply(MidiCoreWorkspaceIntent.ReplaceStructure(
                listOf(ProjectSectionDefinition("verse", "Verse")),
                listOf(
                    MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse 1", 1),
                    MidiCoreBarOccurrencePlacement("verse-2", "verse", "Verse 2", 1),
                ),
            ))
            apply(MidiCoreWorkspaceIntent.ReplaceHarmony(listOf(
                AuthoritativeChordEvent("c1", "verse-1", "C", 0, 1920),
                AuthoritativeChordEvent("c2", "verse-2", "F", 1920, 3840),
            )))

            // 1. hear the style with its ephemeral plan; 2. review the plan;
            // 3. explicitly confirm it and create/play the full draft.
            apply(MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "verse-1", 41L))
            assertEquals(app.melotrail.application.MidiCoreArrangementStylePreviewPlanState.EPHEMERAL_STYLE_PROPOSAL,
                workspace.state.value.stylePreview.planState)
            apply(MidiCoreWorkspaceIntent.ProposeArrangementPlan("steady-road"))
            apply(MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft("steady-road", 41L))

            assertNotNull(workspace.state.value.project?.arrangementPlan)
            assertEquals(1, workspace.state.value.project?.arrangementDrafts?.size)
            assertEquals(MidiAuditionPlaybackState.PLAYING, workspace.state.value.audition.playback)
            assertTrue(workspace.state.value.audition.scope is app.melotrail.audition.MidiAuditionScope.ArrangementDraft)
        } finally {
            workspace.close()
            deleteTree(temporaryRoot)
        }
    }

    @Test
    fun `real mixed draft Use Undo and reuse are atomic and preserve Review selection loop`() {
        val temporaryRoot = Files.createTempDirectory("melotrail-u06a-")
        val projectRoot = temporaryRoot.resolve("project")
        val artifacts = MidiCoreArtifactStore()
        val workspace = newWorkspace(artifacts, WorkflowFakeMidiAudition(), WorkflowPreferences())
        fun apply(intent: MidiCoreWorkspaceIntent) {
            workspace.accept(intent)
            awaitWorkspaceCompletion(workspace, intent.toString())
            assertEquals(
                MidiCoreWorkspaceOperationPhase.SUCCEEDED,
                workspace.state.value.operation.phase,
                "${intent}: ${workspace.state.value.blockers}",
            )
        }
        try {
            apply(MidiCoreWorkspaceIntent.CreateProject(projectRoot, "Review rest fixture"))
            apply(MidiCoreWorkspaceIntent.ImportSource(writeSourceMidi(temporaryRoot.resolve("source.mid"), pitch = 84)))
            apply(MidiCoreWorkspaceIntent.ConfirmAuthority)
            apply(MidiCoreWorkspaceIntent.ReplaceStructure(
                listOf(ProjectSectionDefinition("verse", "Verse")),
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 2)),
            ))
            apply(MidiCoreWorkspaceIntent.ReplaceHarmony(listOf(
                AuthoritativeChordEvent("c1", "verse-1", "C", 0L, 3_840L),
            )))
            apply(MidiCoreWorkspaceIntent.ProposeArrangementPlan("steady-road"))
            apply(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
            val restingPlan = requireNotNull(workspace.state.value.project?.arrangementPlan).let { plan ->
                plan.copy(occurrences = plan.occurrences.map { occurrence ->
                    occurrence.copy(roleSettings = occurrence.roleSettings.map { settings ->
                        if (settings.role == CandidateRole.BASS) settings.copy(
                            activity = app.melotrail.project.MidiCoreRoleActivity.INACTIVE,
                            density = 0,
                        ) else settings
                    })
                })
            }
            apply(MidiCoreWorkspaceIntent.PreviewArrangementPlanEdit(restingPlan))
            apply(MidiCoreWorkspaceIntent.ConfirmArrangementPlanEdit(restingPlan))
            apply(MidiCoreWorkspaceIntent.CreateArrangementDraft("steady-road", 41L))

            val beforeUse = requireNotNull(workspace.state.value.project)
            val draft = beforeUse.arrangementDrafts.single()
            assertEquals(listOf(CandidateRole.BASS), draft.plannedRests.map { it.role })
            assertEquals(setOf(CandidateRole.CHORDS, CandidateRole.DRUMS), draft.candidateReferences.map { it.role }.toSet())
            workspace.accept(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("verse-1"))
            val selectedLoop = MidiAuditionLoop(0L, 3_840L)
            assertEquals("verse-1", workspace.state.value.arrangement.selectedOccurrenceId)
            assertEquals(selectedLoop, workspace.state.value.audition.loop)

            apply(MidiCoreWorkspaceIntent.UseArrangementDraft(draft.id))
            val accepted = requireNotNull(workspace.state.value.project)
            assertEquals(beforeUse.revision + 1L, accepted.revision)
            assertEquals(2, accepted.acceptances.size)
            assertEquals(listOf(CandidateRole.BASS), accepted.acceptedPlannedRests.map { it.role })
            assertEquals(3, accepted.arrangementDraftAcceptanceHistory.single().appliedScopes.size)
            assertEquals("verse-1", workspace.state.value.arrangement.selectedOccurrenceId)
            assertEquals(selectedLoop, workspace.state.value.audition.loop)

            val historyId = accepted.arrangementDraftAcceptanceHistory.single().id
            apply(MidiCoreWorkspaceIntent.UndoArrangementDraftAcceptance(historyId))
            val undone = requireNotNull(workspace.state.value.project)
            assertEquals(accepted.revision + 1L, undone.revision)
            assertTrue(undone.acceptances.isEmpty())
            assertTrue(undone.acceptedPlannedRests.isEmpty())
            assertTrue(undone.arrangementDraftAcceptanceHistory.isEmpty())
            assertEquals(beforeUse.sourceMidi, undone.sourceMidi)
            assertEquals(beforeUse.arrangementDrafts, undone.arrangementDrafts)
            assertEquals("verse-1", workspace.state.value.arrangement.selectedOccurrenceId)
            assertEquals(selectedLoop, workspace.state.value.audition.loop)

            apply(MidiCoreWorkspaceIntent.UseArrangementDraft(draft.id))
            val reused = requireNotNull(workspace.state.value.project)
            assertEquals(2, reused.acceptances.size)
            assertEquals(listOf(CandidateRole.BASS), reused.acceptedPlannedRests.map { it.role })
        } finally {
            workspace.close()
            deleteTree(temporaryRoot)
        }
    }

    @Test
    fun `real musical repair covers neighboring scopes and atomically accepts matching dependencies`() {
        val temporaryRoot = Files.createTempDirectory("melotrail-m09-")
        val projectRoot = temporaryRoot.resolve("project")
        val artifacts = MidiCoreArtifactStore()
        val source = writeSourceMidi(temporaryRoot.resolve("source.mid"), pitch = 84)
        val acceptanceClock = WorkflowAcceptanceClock()
        val workspace = newWorkspace(artifacts, WorkflowFakeMidiAudition(), WorkflowPreferences(), acceptanceClock)
        fun apply(intent: MidiCoreWorkspaceIntent) {
            workspace.accept(intent)
            awaitWorkspaceCompletion(workspace, intent.toString())
            assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, workspace.state.value.operation.phase,
                "${intent}: ${workspace.state.value.blockers}")
        }
        try {
            apply(MidiCoreWorkspaceIntent.CreateProject(projectRoot, "Repair fixture"))
            apply(MidiCoreWorkspaceIntent.ImportSource(source))
            apply(MidiCoreWorkspaceIntent.ConfirmAuthority)
            apply(MidiCoreWorkspaceIntent.ReplaceStructure(listOf(ProjectSectionDefinition("verse", "Verse")),
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse 1", 1), MidiCoreBarOccurrencePlacement("verse-2", "verse", "Verse 2", 1))))
            apply(MidiCoreWorkspaceIntent.ReplaceHarmony(listOf(AuthoritativeChordEvent("c1", "verse-1", "C", 0, 1920),
                AuthoritativeChordEvent("c2", "verse-2", "F", 1920, 3840))))
            apply(MidiCoreWorkspaceIntent.ProposeArrangementPlan("steady-road"))
            apply(MidiCoreWorkspaceIntent.ConfirmArrangementPlan)
            val activePlan = requireNotNull(workspace.state.value.project?.arrangementPlan).let { plan ->
                plan.copy(occurrences = plan.occurrences.map { occurrence -> occurrence.copy(roleSettings = occurrence.roleSettings.map {
                    it.copy(activity = app.melotrail.project.MidiCoreRoleActivity.SUPPORTING, density = 65,
                        registerPreference = if (it.role == CandidateRole.CHORDS) app.melotrail.project.MidiCoreRegisterPreference.HIGH else it.registerPreference)
                }) })
            }
            apply(MidiCoreWorkspaceIntent.PreviewArrangementPlanEdit(activePlan))
            apply(MidiCoreWorkspaceIntent.ConfirmArrangementPlanEdit(activePlan))
            apply(MidiCoreWorkspaceIntent.CreateArrangementDraft("steady-road", 41))
            val draft = requireNotNull(workspace.state.value.project).arrangementDrafts.last()
            apply(MidiCoreWorkspaceIntent.UseArrangementDraft(draft.id))
            apply(MidiCoreWorkspaceIntent.ExportPackage)
            val before = artifacts.openProject(projectRoot)
            val oldBytes = before.candidates.associate { it.id to Files.readAllBytes(projectRoot.resolve(it.midi.path.value)) }
            val sourceBytes = Files.readAllBytes(projectRoot.resolve(requireNotNull(before.sourceMidi).original.path.value))
            apply(MidiCoreWorkspaceIntent.PreviewMusicalRepair("verse-2", app.melotrail.application.MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION))
            val prepared = requireNotNull(workspace.state.value.musicalRepair.prepared)
            assertEquals(before, artifacts.openProject(projectRoot), "Preview must be write-free")
            apply(MidiCoreWorkspaceIntent.ApplyMusicalRepair)
            val repair = workspace.state.value.musicalRepair
            val expectedScopes = prepared.invalidation.affectedScopes.map { it.occurrenceId to it.role }.toSet()
            assertTrue(expectedScopes.any { it.first == "verse-1" }, "Fixture must exercise a neighboring dependency")
            assertEquals(expectedScopes, repair.alternativesByScope.keys.map { it.occurrenceId to it.role }.toSet())
            val ids = repair.alternativesByScope.values.map { it.single().candidateId }
            val generated = artifacts.openProject(projectRoot)
            assertEquals(before.acceptances, generated.acceptances, "Generating repairs must not accept them")
            val downstream = generated.candidates.first { it.id in ids && it.draftDependencyIds.isNotEmpty() }
            val rejected = MidiCoreCandidateReview(artifacts).accept(app.melotrail.application.AcceptMidiCoreCandidate(
                app.melotrail.application.MidiCoreProjectSession(projectRoot, generated), downstream.id))
            assertTrue(rejected is app.melotrail.application.MidiCoreCandidateLifecycleResult.Rejected,
                "A dependent role cannot be accepted against unaccepted repair inputs")
            assertEquals(generated, artifacts.openProject(projectRoot))
            val successor = generated.candidates.single { it.id in ids && it.role == CandidateRole.CHORDS && it.occurrenceId == "verse-2" }
            val boundary = MidiCoreCandidateReview(artifacts).precedingPianoBoundary(
                app.melotrail.application.MidiCoreProjectSession(projectRoot, generated), "verse-2", ids)
            assertEquals(requireNotNull(boundary).sha256, successor.boundarySummarySha256)
            val projectFile = projectRoot.resolve("project.json")
            val validProjectBytes = Files.readAllBytes(projectFile)
            val tampered = generated.copy(candidates = generated.candidates.map {
                if (it.id == successor.id) it.copy(boundarySummarySha256 = null) else it
            })
            Files.writeString(projectFile, app.melotrail.project.MidiCoreProjectSchema.encode(tampered))
            val badBytes = Files.readAllBytes(projectFile)
            val badBatch = MidiCoreCandidateReview(artifacts).acceptBatch(app.melotrail.application.AcceptMidiCoreCandidateBatch(
                app.melotrail.application.MidiCoreProjectSession(projectRoot, tampered), ids))
            assertTrue(badBatch is app.melotrail.application.MidiCoreCandidateLifecycleResult.Rejected)
            assertTrue(badBytes.contentEquals(Files.readAllBytes(projectFile)), "Rejected batch must not write any selection")
            Files.write(projectFile, validProjectBytes)
            val collisionReview = MidiCoreCandidateReview(artifacts, lifecycle = MidiCoreCandidateLifecycle(artifacts, idFactory = { "batch-history-collision" }))
            val collided = collisionReview.acceptBatch(app.melotrail.application.AcceptMidiCoreCandidateBatch(
                app.melotrail.application.MidiCoreProjectSession(projectRoot, generated), ids))
            assertTrue(collided is app.melotrail.application.MidiCoreCandidateLifecycleResult.Rejected)
            assertTrue(validProjectBytes.contentEquals(Files.readAllBytes(projectFile)), "History collisions must leave the whole batch untouched")

            acceptanceClock.next = Instant.parse("2027-01-01T00:00:00.123Z")
            apply(MidiCoreWorkspaceIntent.UseMusicalRepair(ids))
            val accepted = artifacts.openProject(projectRoot)
            assertEquals(listOf("2027-01-01T00:00:00.123Z", "2027-01-01T00:00:00.123001Z"),
                accepted.acceptanceHistory.takeLast(ids.size).take(2).map { it.recordedAt })
            assertFailsWith<IllegalArgumentException> {
                accepted.copy(acceptanceHistory = accepted.acceptanceHistory.reversed())
            }
            assertEquals(generated.revision + 1, accepted.revision, "The complete repair must commit in one revision")
            assertTrue(ids.all { id -> accepted.acceptances.any { it.candidateId == id } })
            assertTrue(sourceBytes.contentEquals(Files.readAllBytes(projectRoot.resolve(requireNotNull(accepted.sourceMidi).original.path.value))))
            oldBytes.forEach { (id, bytes) -> assertTrue(bytes.contentEquals(Files.readAllBytes(projectRoot.resolve(accepted.candidates.single { it.id == id }.midi.path.value)))) }
            apply(MidiCoreWorkspaceIntent.PreviewMusicalRepair("verse-2", app.melotrail.application.MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER))
            apply(MidiCoreWorkspaceIntent.ApplyMusicalRepair)
            val lowerChoices = workspace.state.value.musicalRepair.alternativesByScope.values.map { it.single().candidateId }
            val lowerCandidate = requireNotNull(workspace.state.value.project).candidates.single {
                it.id in lowerChoices && it.role == CandidateRole.CHORDS && it.occurrenceId == "verse-2"
            }
            assertTrue(lowerCandidate.generatorVersion.contains("-i2-r-12-"), "The used lower-register policy must survive candidate publication")
            apply(MidiCoreWorkspaceIntent.UseMusicalRepair(lowerChoices))
            val lowered = artifacts.openProject(projectRoot)
            assertEquals(lowerCandidate.generatorVersion, lowered.candidates.single { it.id == lowerCandidate.id }.generatorVersion)
            val exported = MidiCoreMidiPackageExporter(artifacts).export(app.melotrail.application.ExportMidiCorePackage(
                app.melotrail.application.MidiCoreProjectSession(projectRoot, lowered)))
            assertTrue(exported is app.melotrail.application.MidiCoreMidiPackageExportResult.Exported, exported.toString())
            val evidence = Path.of("build/m09-repair-evidence/project")
            deleteTree(evidence)
            Files.walk(projectRoot).use { paths -> paths.forEach { input ->
                val output = evidence.resolve(projectRoot.relativize(input).toString())
                if (Files.isDirectory(input)) Files.createDirectories(output) else Files.copy(input, output)
            } }

        } finally { workspace.close(); deleteTree(temporaryRoot) }
    }

    @Test
    fun `reference-wide six target pages complete a real MIDI Core workflow and reopen an immutable export`() =
        runFocusedWorkflow(Size(1536f, 1024f), "reference-wide")

    @Test
    fun `wide six target pages complete a real MIDI Core workflow and reopen an immutable export`() =
        runFocusedWorkflow(Size(1280f, 900f), "wide")

    @Test
    fun `compact six target pages complete a real MIDI Core workflow and reopen an immutable export`() =
        runFocusedWorkflow(Size(720f, 900f), "compact")

    @Test
    fun `reference-wide whole-song rest inventory and immutable handoff use real services`() =
        runRestExportWorkflow(Size(1536f, 1024f), "reference-wide")

    @Test
    fun `wide whole-song rest inventory and immutable handoff use real services`() =
        runRestExportWorkflow(Size(1280f, 900f), "wide")

    @Test
    fun `compact whole-song rest inventory and immutable handoff use real services`() =
        runRestExportWorkflow(Size(720f, 900f), "compact")

    private fun runRestExportWorkflow(size: Size, fixtureSet: String) = runSkikoComposeUiTest(size = size) {
        val temporaryRoot = Files.createTempDirectory("melotrail-u06-rest-")
        val projectRoot = temporaryRoot.resolve("Song with spaces")
        val source = writeSourceMidi(temporaryRoot.resolve("source.mid"), pitch = 84, endTick = 7_680L)
        val sourceBytes = Files.readAllBytes(source)
        val artifacts = MidiCoreArtifactStore()
        val audition = WorkflowFakeMidiAudition()
        val workspace = newWorkspace(artifacts, audition, WorkflowPreferences())
        var revealed: Path? = null
        fun apply(intent: MidiCoreWorkspaceIntent) {
            workspace.accept(intent)
            awaitWorkspaceCompletion(workspace, intent.toString())
            assertEquals(MidiCoreWorkspaceOperationPhase.SUCCEEDED, workspace.state.value.operation.phase,
                "${intent}: ${workspace.state.value.blockers}")
        }
        fun capture(name: String) {
            onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertIsDisplayed()
            val image = onRoot().captureToImage().toAwtImage()
            assertEquals(size.width.toInt(), image.width)
            assertEquals(size.height.toInt(), image.height)
            val target = Path.of("build/test-results/midi-core-export-rest/$fixtureSet/$name.png")
            Files.createDirectories(target.parent)
            assertTrue(ImageIO.write(image, "png", target.toFile()))
        }
        try {
            apply(MidiCoreWorkspaceIntent.CreateProject(projectRoot, "Quiet beginning and ending"))
            apply(MidiCoreWorkspaceIntent.ImportSource(source))
            apply(MidiCoreWorkspaceIntent.ConfirmAuthority)
            apply(MidiCoreWorkspaceIntent.ReplaceStructure(
                listOf(ProjectSectionDefinition("a", "A"), ProjectSectionDefinition("b", "B")),
                listOf(MidiCoreBarOccurrencePlacement("intro", "a", "Opening", 2),
                    MidiCoreBarOccurrencePlacement("outro", "b", "Ending", 2)),
            ))
            apply(MidiCoreWorkspaceIntent.ReplaceHarmony(listOf(
                AuthoritativeChordEvent("c1", "intro", "C", 0L, 3_840L),
                AuthoritativeChordEvent("c2", "outro", "G", 3_840L, 7_680L),
            )))
            // These rests come from the standard style proposal, with no custom plan or generator settings.
            apply(MidiCoreWorkspaceIntent.ProposeArrangementPlan("late-night"))
            apply(MidiCoreWorkspaceIntent.ConfirmPlanAndCreateArrangementDraft("late-night", 41L))
            val draft = requireNotNull(workspace.state.value.project).arrangementDrafts.single()
            assertEquals(4, draft.plannedRests.size)
            assertEquals(2, draft.candidateReferences.size)
            workspace.accept(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("outro"))
            val loop = MidiAuditionLoop(3_840L, 7_680L)
            assertEquals(loop, workspace.state.value.audition.loop)
            setContent { MelotrailTheme { MidiCoreWorkspaceShell(
                workspace = workspace,
                exportActions = MidiCoreExportPageActions { revealed = it },
            ) } }
            val reviewNavigation = onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.REVIEW))
            if (size.width < 1100f) reviewNavigation.performScrollTo()
            reviewNavigation.performClick()
            onNodeWithTag(MidiCoreReviewPageTags.USE_DRAFT).performScrollTo().performClick()
            awaitWorkspaceCompletion(workspace, "use mixed draft")
            val accepted = requireNotNull(workspace.state.value.project)
            assertEquals(4, accepted.acceptedPlannedRests.size)
            assertEquals(2, accepted.acceptances.size)
            onNodeWithTag(MidiCoreReviewPageTags.EXPORT).performScrollTo().assertIsEnabled()
            onNodeWithTag(MidiCoreReviewPageTags.UNDO_DRAFT).performScrollTo().performClick()
            awaitWorkspaceCompletion(workspace, "undo mixed draft")
            assertTrue(requireNotNull(workspace.state.value.project).acceptances.isEmpty())
            assertTrue(requireNotNull(workspace.state.value.project).acceptedPlannedRests.isEmpty())
            onNodeWithTag(MidiCoreReviewPageTags.USE_DRAFT).performScrollTo().performClick()
            awaitWorkspaceCompletion(workspace, "reuse mixed draft")
            assertEquals(loop, workspace.state.value.audition.loop)
            assertEquals("outro", workspace.state.value.arrangement.selectedOccurrenceId)
            onNodeWithTag(MidiCoreReviewPageTags.EXPORT).performScrollTo().performClick()
            onAllNodesWithTag(MidiCoreExportPageTags.DAW_GUIDANCE).assertCountEquals(1)
            onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_CONTEXT).assertDoesNotExist()
            if (size.width >= 1440f) {
                val handoffBounds = onNodeWithTag(MidiCoreExportPageTags.DAW_GUIDANCE).getUnclippedBoundsInRoot()
                assertEquals(407f, handoffBounds.right.value - handoffBounds.left.value)
            }
            onNodeWithTag(MidiCoreExportPageTags.INVENTORY).performScrollTo().performClick()
            onNodeWithText("Bass — 0 accepted · 2 rests · whole-song rest · file omitted").assertExists()
            onNodeWithText("Ending · Bars 3–4 · Bass: Accepted planned rest").assertExists()
            capture("accepted-inventory")
            onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsEnabled().performClick()
            awaitWorkspaceCompletion(workspace, "publish rest package")
            val result = requireNotNull(workspace.state.value.export.latest)
            assertEquals(listOf(CandidateRole.CHORDS), result.snapshot.enabledRoles)
            assertTrue(midiCoreExportMatchesAcceptedWork(workspace.state.value, result.snapshot))
            val withoutRestAcceptance = workspace.state.value.copy(project = result.session.project.copy(acceptedPlannedRests = emptyList()))
            assertTrue(result.snapshot.isCurrent(requireNotNull(withoutRestAcceptance.project)), "Candidate references alone cannot establish rest acceptance")
            kotlin.test.assertFalse(midiCoreExportMatchesAcceptedWork(withoutRestAcceptance, result.snapshot))
            assertEquals(setOf("complete-song.mid", "melody.mid", "chords.mid"), result.files.map { it.filename }.toSet())
            assertTrue(result.files.all { it.validation.songEndTick == 7_680L && it.validation.ppq == 480 })
            onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().performClick()
            assertEquals(result.directory, revealed)
            capture("published-result")
            assertContentEquals(sourceBytes, Files.readAllBytes(projectRoot.resolve(requireNotNull(accepted.sourceMidi).original.path.value)))
            val evidence = Path.of("build/test-results/midi-core-export-rest/$fixtureSet/logic-package")
            deleteTree(evidence)
            Files.createDirectories(evidence)
            result.snapshot.files.forEach { file ->
                val saved = projectRoot.resolve(file.artifact.path.value)
                Files.copy(saved, evidence.resolve(saved.fileName))
            }
        } finally {
            workspace.close()
            deleteTree(temporaryRoot)
        }
    }

    @Test
    fun `real rejected tempo map reaches scoped MIDI findings without binding source artifacts`() =
        runSkikoComposeUiTest(size = Size(720f, 900f)) {
            val temporaryRoot = Files.createTempDirectory("melotrail-u03-rejected-import-")
            val projectRoot = temporaryRoot.resolve("project")
            val source = writeTempoMapSourceMidi(temporaryRoot.resolve("input/tempo-map.mid"))
            val workspace = newWorkspace(MidiCoreArtifactStore(), WorkflowFakeMidiAudition(), WorkflowPreferences())

            try {
                setContent { MelotrailTheme { MidiCoreWorkspaceShell(workspace = workspace) } }
                workspace.accept(MidiCoreWorkspaceIntent.CreateProject(projectRoot, "Rejected import evidence"))
                awaitWorkspaceCompletion(workspace, "create project")
                workspace.accept(MidiCoreWorkspaceIntent.ImportSource(source))
                awaitWorkspaceCompletion(workspace, "reject tempo-map source")

                assertEquals(MidiCoreWorkspaceOperationPhase.FAILED, workspace.state.value.operation.phase)
                assertEquals(MidiCoreSourceStatus.REJECTED, workspace.state.value.source.status)
                assertTrue(workspace.state.value.source.findings.any { it.code == MidiFindingCode.TEMPO_MAP_UNSUPPORTED })
                assertEquals(null, workspace.state.value.project?.sourceMidi)
                assertTrue(Files.notExists(projectRoot.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
                assertTrue(Files.notExists(projectRoot.resolve(MidiCoreArtifactStore.IMPORT_REPORT.value)))

                onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.MIDI)).performScrollTo().performClick()
                waitForIdle()
                onNodeWithTag(MidiCoreMidiPageTags.FINDINGS).performScrollTo().assertIsDisplayed()
                onNodeWithText("Scope: Tempo").assertIsDisplayed()
                onNodeWithText("Tempo changes are not supported in MIDI Core V1.").assertIsDisplayed()
                onNodeWithText("Next: Resolve the blocking findings shown in MIDI, then retry the import.").performScrollTo().assertIsDisplayed()
                onNodeWithTag(MidiCoreMidiPageTags.IMPORT).assertIsEnabled()
            } finally {
                workspace.close()
                deleteTree(temporaryRoot)
            }
        }

    private fun runFocusedWorkflow(size: Size, fixtureSet: String) = runSkikoComposeUiTest(size = size) {
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
        var revealedPackage: Path? = null

        try {
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        workspace = workspace,
                        projectActions = projectActions,
                        midiActions = midiActions,
                        exportActions = MidiCoreExportPageActions { revealedPackage = it },
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
                val destinationNode = onNodeWithTag(MidiCoreWorkspaceShellTags.destination(destination))
                if (fixtureSet == "compact") destinationNode.performScrollTo()
                destinationNode.performClick()
                waitForIdle()
            }
            fun captureFixture(name: String) {
                onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertIsDisplayed()
                val player = onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).getUnclippedBoundsInRoot()
                assertTrue(player.bottom.value <= size.height, "Persistent player must remain outside page scrolling on $name")
                val image = onRoot().captureToImage().toAwtImage()
                assertTrue(image.width > 0 && image.height > 0, "$name visual fixture must be non-empty")
                val target = visualFixtureRoot(fixtureSet).resolve("$name.png")
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
                    occurrences = listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse 1", 2)),
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
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_HARMONY).performScrollTo().assertIsDisplayed()
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.SHARED_SECTION_STRIP).assertExists()
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.SECTION_CONTEXT).assertIsDisplayed()
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.CHORD_SPANS).assertIsDisplayed()
            captureFixture("structure-harmony")

            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            onNodeWithTag(MidiCoreArrangePageTags.style("late-night")).performScrollTo().performClick()
            awaitWorkspaceSuccess("preview selected arrangement style")
            val sourceBeforePlan = checkNotNull(workspace.state.value.project?.sourceMidi)
            val draftsBeforePlan = workspace.state.value.project?.arrangementDrafts.orEmpty()
            onNodeWithTag(MidiCoreArrangePageTags.PROPOSE_PLAN).performScrollTo().performClick()
            awaitWorkspaceSuccess("propose session-only arrangement plan")
            assertEquals(null, workspace.state.value.project?.arrangementPlan)
            assertEquals(sourceBeforePlan, workspace.state.value.project?.sourceMidi)
            assertEquals(draftsBeforePlan, workspace.state.value.project?.arrangementDrafts)
            onNodeWithTag(MidiCoreArrangePageTags.CONFIRM_PLAN).performScrollTo().assertIsEnabled().assertIsDisplayed()
            captureFixture("arrange-proposal")
            onNodeWithTag(MidiCoreArrangePageTags.CANCEL_PLAN).performClick()
            awaitWorkspaceSuccess("cancel unsaved arrangement plan")
            assertEquals(null, workspace.state.value.project?.arrangementPlan)
            assertEquals(sourceBeforePlan, workspace.state.value.project?.sourceMidi)
            assertEquals(draftsBeforePlan, workspace.state.value.project?.arrangementDrafts)
            onNodeWithTag(MidiCoreArrangePageTags.PROPOSE_PLAN).performScrollTo().performClick()
            awaitWorkspaceSuccess("re-propose arrangement plan after cancellation")
            onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .performKeyInput { pressKey(Key.Enter) }
            awaitWorkspaceSuccess("explicitly confirm arrangement plan and create complete draft")
            onNodeWithTag(MidiCoreArrangePageTags.CONFIRMED_PLAN).performScrollTo().assertIsDisplayed()
            onNodeWithTag(MidiCoreArrangePageTags.PROPOSE_PLAN).assertDoesNotExist()
            assertNotNull(workspace.state.value.project?.arrangementPlan)
            assertEquals(sourceBeforePlan, workspace.state.value.project?.sourceMidi)
            assertEquals(1, workspace.state.value.project?.arrangementDrafts?.size)
            assertEquals(MidiAuditionPlaybackState.PLAYING, workspace.state.value.audition.playback)
            onNodeWithTag(MidiCoreVerifiedTimelineTags.ROOT).performScrollTo()
            captureFixture("arrange-top")
            val projectBeforeRepairPreview = checkNotNull(workspace.state.value.project)
            onNodeWithTag(MidiCoreArrangePageTags.repair(app.melotrail.application.MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO)).performScrollTo().assertIsEnabled().performClick()
            awaitWorkspaceSuccess("preview selected section repair")
            assertEquals(projectBeforeRepairPreview, workspace.state.value.project, "Repair preview must remain session-only until Apply")
            assertNotNull(workspace.state.value.musicalRepair.prepared)
            onNodeWithTag(MidiCoreArrangePageTags.APPLY_REPAIR).performScrollTo().assertIsEnabled()
            captureFixture("arrange-repair-preview")
            onNodeWithTag(MidiCoreArrangePageTags.CANCEL_REPAIR).performScrollTo().performClick()
            assertEquals(null, workspace.state.value.musicalRepair.prepared)
            captureFixture("arrange")

            // An audible draft is still excluded from export, even while its player is running.
            val originalDraft = requireNotNull(workspace.state.value.project).arrangementDrafts.single()
            val originalCandidateBytes = requireNotNull(workspace.state.value.project).candidates.associate {
                it.midi.path.value to Files.readAllBytes(projectRoot.resolve(it.midi.path.value))
            }
            navigateTo(MidiCoreWorkspaceDestination.EXPORT)
            onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().assertIsNotEnabled()
            captureFixture("export-draft-blocked")
            assertEquals(MidiAuditionPlaybackState.PLAYING, audition.state.playback)
            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            onNodeWithTag(MidiCoreArrangePageTags.repair(app.melotrail.application.MidiCoreMusicalRepairIntent.LOWER_PIANO_REGISTER))
                .performScrollTo().performClick()
            awaitWorkspaceSuccess("review lower piano repair")
            val repairPlan = requireNotNull(workspace.state.value.musicalRepair.prepared).proposal.plan
            onNodeWithTag(MidiCoreArrangePageTags.APPLY_REPAIR).performScrollTo().performClick()
            awaitWorkspaceSuccess("generate real lower piano alternatives")
            assertTrue(workspace.state.value.musicalRepair.alternatives.isNotEmpty())
            assertTrue(requireNotNull(workspace.state.value.project).acceptances.isEmpty())
            assertEquals(repairPlan, workspace.state.value.project?.arrangementPlan)
            navigateTo(MidiCoreWorkspaceDestination.REVIEW)
            onNodeWithTag(MidiCoreReviewPageTags.OPEN_EXCEPTIONS).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag(MidiCoreReviewPageTags.PLAY_CANDIDATE).performScrollTo().performClick()
            awaitWorkspaceSuccess("compare the real repair with the protected melody")
            val repairPlayback = requireNotNull(audition.lastPlan)
            assertEquals(listOf(MidiExportRole.MELODY, MidiExportRole.CHORDS), repairPlayback.view.roles)
            val protectedNotes = repairPlayback.view.song.roles.single { it.role == MidiExportRole.MELODY }.events
                .filterIsInstance<app.melotrail.midi.domain.MidiNoteEvent>()
            assertEquals(listOf(60), protectedNotes.map { it.pitch })
            assertEquals(0L, protectedNotes.single().orderingKey.tick)
            assertEquals(sourceBeforePlan.sourceEndTick, protectedNotes.single().endTick)
            captureFixture("review-repair")
            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            // Review the complete song under the explicitly confirmed repair plan.
            onNodeWithTag(MidiCoreArrangePageTags.CREATE_DRAFT).performScrollTo().performClick()
            awaitWorkspaceSuccess("create complete draft from the confirmed repair plan")
            assertEquals(repairPlan, workspace.state.value.project?.arrangementPlan)
            assertTrue(originalDraft in requireNotNull(workspace.state.value.project).arrangementDrafts)
            originalCandidateBytes.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(projectRoot.resolve(path))) }
            assertEquals(2, workspace.state.value.project?.arrangementDrafts?.size)

            navigateTo(MidiCoreWorkspaceDestination.REVIEW)
            onNodeWithTag(MidiCoreVerifiedTimelineTags.ROOT).performScrollTo()
            captureFixture("review-top")
            onNodeWithTag(MidiCoreReviewPageTags.PLAY_DRAFT).performScrollTo().assertIsEnabled().performClick()
            awaitWorkspaceSuccess("play complete draft before acceptance")
            assertEquals(MidiAuditionPlaybackState.PLAYING, audition.state.playback)
            onNodeWithTag(MidiCoreReviewPageTags.USE_DRAFT).performScrollTo().assertIsEnabled().performClick()
            awaitWorkspaceSuccess("use complete draft atomically")
            assertEquals(CandidateRole.entries.size, workspace.state.value.project?.acceptances?.size)
            onNodeWithTag(MidiCoreReviewPageTags.UNDO_DRAFT).performScrollTo().performClick()
            awaitWorkspaceSuccess("undo complete draft acceptance")
            assertEquals(0, workspace.state.value.project?.acceptances?.size)
            onNodeWithTag(MidiCoreReviewPageTags.USE_DRAFT).performScrollTo().performClick()
            awaitWorkspaceSuccess("reaccept complete draft")
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
            assertNotNull(reopened.arrangementPlan)
            assertEquals(sourceBeforePlan, reopened.sourceMidi)
            assertEquals(2, reopened.arrangementDrafts.size)
            navigateTo(MidiCoreWorkspaceDestination.ARRANGE)
            onNodeWithTag(MidiCoreArrangePageTags.CONFIRMED_PLAN).performScrollTo().assertIsDisplayed()
            onNodeWithTag(MidiCoreArrangePageTags.PROPOSE_PLAN).assertDoesNotExist()

            navigateTo(MidiCoreWorkspaceDestination.EXPORT)
            captureFixture("export")
            onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().performClick()
            awaitWorkspaceSuccess("export immutable MIDI package")
            val snapshot = assertNotNull(workspace.state.value.export.latestSnapshot)
            val packageDirectory = projectRoot.resolve("exports").resolve(snapshot.id)
            assertTrue(Files.isRegularFile(packageDirectory.resolve("complete-song.mid")))
            assertTrue(Files.isRegularFile(packageDirectory.resolve("manifest.json")))
            assertEquals(reopened.acceptances.map { it.candidateId }.toSet(), snapshot.acceptedCandidates.map { it.candidateId }.toSet())
            assertTrue(snapshot.isCurrent(requireNotNull(workspace.state.value.project)))
            val exportedBytes = snapshot.files.associate { file -> file.artifact.path.value to Files.readAllBytes(projectRoot.resolve(file.artifact.path.value)) }
            onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().performClick()
            assertEquals(packageDirectory, revealedPackage)
            captureFixture("export-result")

            // Reopening retains the exact saved result and reveal target without another write.
            workspace.accept(MidiCoreWorkspaceIntent.CloseProject)
            assertEquals(null, workspace.state.value.project)
            workspace.accept(MidiCoreWorkspaceIntent.OpenProject(projectRoot))
            awaitWorkspaceSuccess("reopen exported project")
            assertEquals(snapshot, workspace.state.value.export.latestSnapshot)
            navigateTo(MidiCoreWorkspaceDestination.EXPORT)
            onNodeWithTag(MidiCoreExportPageTags.REVEAL).performScrollTo().performClick()
            assertEquals(packageDirectory, revealedPackage)
            captureFixture("export-reopened")
            onNodeWithTag(MidiCoreExportPageTags.PUBLISH).performScrollTo().performClick()
            awaitWorkspaceSuccess("publish another immutable snapshot")
            val nextSnapshot = requireNotNull(workspace.state.value.export.latestSnapshot)
            assertTrue(nextSnapshot.id != snapshot.id)
            exportedBytes.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(projectRoot.resolve(path))) }
            assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(projectRoot.resolve(sourceBeforePlan.original.path.value)))
            val evidencePackage = visualFixtureRoot(fixtureSet).resolve("logic-package")
            deleteTree(evidencePackage)
            Files.createDirectories(evidencePackage)
            snapshot.files.forEach { file ->
                val saved = projectRoot.resolve(file.artifact.path.value)
                Files.copy(saved, evidencePackage.resolve(saved.fileName))
            }

            // Repair uses explicit rest dependencies, and UI preview reports their downstream invalidation.
            workspace.accept(MidiCoreWorkspaceIntent.CloseProject)
            var restCloseAttempts = 0
            while (workspace.state.value.project != null && restCloseAttempts++ < 100) Thread.sleep(10)
            assertEquals(null, workspace.state.value.project)
            val store = MidiCoreArtifactStore()
            val current = store.openProject(projectRoot)
            val originalPlan = checkNotNull(current.arrangementPlan)
            val restingPlan = originalPlan.copy(occurrences = originalPlan.occurrences.map { occurrence ->
                occurrence.copy(roleSettings = occurrence.roleSettings.map {
                    if (it.role == CandidateRole.BASS) it.copy(activity = app.melotrail.project.MidiCoreRoleActivity.INACTIVE, density = 0) else it
                })
            })
            kotlin.test.assertIs<app.melotrail.application.MidiCoreArrangementPlanEditResult.Confirmed>(
                app.melotrail.application.MidiCoreArrangementPlanEdit(store).confirm(
                    app.melotrail.application.ConfirmMidiCoreArrangementPlanEdit(
                        app.melotrail.application.MidiCoreProjectSession(projectRoot, current), restingPlan),
                ),
            )
            workspace.accept(MidiCoreWorkspaceIntent.OpenProject(projectRoot))
            awaitWorkspaceSuccess("reopen rest repair fixture")
            val beforeRestRepair = checkNotNull(workspace.state.value.project).candidates.map { it.id }.toSet()
            workspace.accept(MidiCoreWorkspaceIntent.RegenerateArrangementSection("verse-1", "late-night", 84L))
            awaitWorkspaceSuccess("repair inactive Bass with active Drums")
            val repairedProject = checkNotNull(workspace.state.value.project)
            val repaired = repairedProject.candidates.filterNot { it.id in beforeRestRepair }
            assertEquals(listOf(CandidateRole.CHORDS, CandidateRole.DRUMS), repaired.map { it.role })
            val drums = repaired.single { it.role == CandidateRole.DRUMS }
            assertEquals(listOf(CandidateRole.BASS), drums.draftDependencyRests.map { it.role })
            val updatedAuthority = checkNotNull(repairedProject.authority).let { authority ->
                authority.copy(chordEvents = authority.chordEvents.map { it.copy(symbol = "Dm") })
            }
            val preview = assertNotNull(previewInvalidation(repairedProject, updatedAuthority))
            assertTrue(drums.id in preview.staleCandidateIds)
            assertTrue(app.melotrail.arrangement.core.MidiCoreInvalidationReason.PLANNED_REST_DEPENDENCY_CHANGED in
                preview.staleTargets.single { it.id == drums.id }.reasons)

            assertEquals(
                listOf("arrange", "arrange-proposal", "arrange-repair-preview", "arrange-top", "export", "export-draft-blocked", "export-reopened", "export-result", "midi", "project", "review", "review-repair", "review-top", "structure-harmony"),
                capturedFixtureNames(fixtureSet),
            )
        } finally {
            workspace.close()
            deleteTree(temporaryRoot)
        }
    }

    private fun capturedFixtureNames(fixtureSet: String): List<String> = Files.list(visualFixtureRoot(fixtureSet)).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".png") }
            .map { it.fileName.toString().removeSuffix(".png") }.sorted().toList()
    }

    private fun visualFixtureRoot(fixtureSet: String): Path = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath()
        .resolve("build/test-results/midi-core-focused-workflow/$fixtureSet")

    private fun deleteTree(root: Path) {
        if (Files.notExists(root)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun writeSourceMidi(path: Path, pitch: Int = 60, endTick: Long = 3_840L): Path {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        val name = "Lead".encodeToByteArray()
        track.add(MidiEvent(MetaMessage(0x03, name, name.size), 0L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 96), 0L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), endTick))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun writeTempoMapSourceMidi(path: Path): Path {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        fun tempo(microsecondsPerQuarter: Int): ByteArray = byteArrayOf(
            (microsecondsPerQuarter ushr 16).toByte(),
            (microsecondsPerQuarter ushr 8).toByte(),
            microsecondsPerQuarter.toByte(),
        )
        track.add(MidiEvent(MetaMessage(0x51, tempo(500_000), 3), 0L))
        track.add(MidiEvent(MetaMessage(0x51, tempo(600_000), 3), 480L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0L))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 960L))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }
}

private fun awaitWorkspaceCompletion(workspace: MidiCoreWorkspaceViewModel, action: String) {
    var attempts = 0
    while (workspace.state.value.operation.active && attempts < 3_000) {
        Thread.sleep(10)
        attempts += 1
    }
    assertTrue(!workspace.state.value.operation.active, "$action did not finish in time")
}

private fun newWorkspace(
    artifacts: MidiCoreArtifactStore,
    audition: MidiAuditionPort,
    preferences: MidiCoreDesktopPreferences,
    acceptanceClock: Clock = Clock.systemUTC(),
): MidiCoreWorkspaceViewModel {
    val lifecycle = MidiCoreProjectLifecycle(artifacts)
    val candidateLifecycle = MidiCoreCandidateLifecycle(artifacts, clock = acceptanceClock)
    val generation = MidiCoreCandidateGeneration(artifacts = artifacts, lifecycle = candidateLifecycle)
    val review = MidiCoreCandidateReview(artifacts = artifacts, lifecycle = candidateLifecycle, generation = generation)
    val useCases = DefaultMidiCoreWorkspaceUseCases(
        project = lifecycle,
        sourceImport = MidiCoreSourceImport(artifacts),
        authority = MidiCoreMusicalAuthority(artifacts),
        arrangementExtent = app.melotrail.application.MidiCoreArrangementExtent(artifacts),
        structure = MidiCoreStructureTimeline(artifacts),
        harmony = MidiCoreAuthoritativeHarmony(artifacts),
        arrangementPlan = app.melotrail.application.MidiCoreArrangementPlanProposalUseCase(artifacts),
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
    var lastPlan: MidiAuditionPlaybackPlan? = null
        private set

    override val state: MidiAuditionState get() = current
    override val stateHistory: List<MidiAuditionState> get() = history.toList()

    override fun selectScope(plan: MidiAuditionPlaybackPlan): MidiAuditionResult = apply(MidiAuditionAction.SELECT_SCOPE) {
        it.copy(scope = plan.view.scope, window = plan.view.window, positionTick = plan.startTick, mutedRoles = plan.mutedRoles, soloRoles = plan.soloRoles)
    }

    override fun play(plan: MidiAuditionPlaybackPlan): MidiAuditionResult {
        lastPlan = plan
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

private class WorkflowAcceptanceClock : Clock() {
    var next: Instant = Instant.parse("2027-01-01T00:00:00Z")
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = next.also { next = it.plusNanos(1_000) }
}
