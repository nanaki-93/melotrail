package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.application.MidiCoreProjectProblemCode
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectMetadata
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreProjectPageTest {
    @Test
    fun `project page creates opens recent and exposes only target readiness actions`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val newRoot = Path.of("build/project-page-new")
        val openRoot = Path.of("build/project-page-open")
        val actions = MidiCoreProjectPageActions(
            chooseProjectDirectory = { openRoot },
            chooseNewProjectDirectory = { newRoot },
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = MidiCoreWorkspaceState(),
                    onIntent = intents::add,
                    projectActions = actions,
                )
            }
        }

        onNodeWithTag(MidiCoreProjectPageTags.CREATE).assertIsNotEnabled()
        onNodeWithTag(MidiCoreProjectPageTags.CHOOSE_NEW_LOCATION).performClick()
        waitForIdle()
        onNodeWithTag(MidiCoreProjectPageTags.NAME).performTextInput("  New song  ")
        onNodeWithTag(MidiCoreProjectPageTags.CREATE).assertIsEnabled().performClick()
        assertEquals(MidiCoreWorkspaceIntent.CreateProject(newRoot.toAbsolutePath().normalize(), "New song"), intents.last())

        onNodeWithTag(MidiCoreProjectPageTags.OPEN).performClick()
        waitForIdle()
        assertEquals(MidiCoreWorkspaceIntent.OpenProject(openRoot.toAbsolutePath().normalize()), intents.last())
        onNodeWithTag(MidiCoreProjectPageTags.OPEN_RECENT).performClick()
        assertEquals(MidiCoreWorkspaceIntent.OpenLastProject, intents.last())
    }

    @Test
    fun `current project page shows persisted readiness location and next incomplete step`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = currentProjectState(),
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithTag(MidiCoreProjectPageTags.SUMMARY).assertExists()
        onNodeWithTag(MidiCoreProjectPageTags.LOCATION).assertExists()
        onNodeWithText("Project revision 12").assertExists()
        onNodeWithText("Go to MIDI").performClick()
        onNodeWithContentDescription("Open MIDI. ${MidiCoreWorkspaceDestination.MIDI.summary} Selected.").assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.PROJECT)).performClick()
        onNodeWithTag(MidiCoreProjectPageTags.RELOAD).performClick()
        onNodeWithTag(MidiCoreProjectPageTags.CLOSE).performClick()
        assertEquals(listOf(MidiCoreWorkspaceIntent.ReloadProject, MidiCoreWorkspaceIntent.CloseProject), intents)
    }

    @Test
    fun `unsupported project and failed save have explicit recoverable explanations`() = runComposeUiTest {
        val state = MidiCoreWorkspaceState(
            operation = MidiCoreWorkspaceOperation(
                id = 3L,
                phase = MidiCoreWorkspaceOperationPhase.FAILED,
                message = "The project could not be saved safely.",
                retry = MidiCoreWorkspaceIntent.ReloadProject,
                outcome = MidiCoreWorkspaceOperationOutcome.FAILURE,
            ),
            blockers = listOf(
                MidiCoreWorkspaceBlocker(
                    code = MidiCoreWorkspaceBlockerCode.APPLICATION_FAILURE,
                    message = "This project uses an unsupported legacy schema.",
                    nextAction = "Create a MIDI Core project instead.",
                    sourceCode = MidiCoreProjectProblemCode.UNSUPPORTED_PROJECT.name,
                ),
            ),
        )
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreWorkspaceShell(state, intents::add) } }

        onNodeWithTag(MidiCoreProjectPageTags.UNSUPPORTED).assertExists()
        onNodeWithText("This folder was not migrated or changed because it is not a current MIDI Core project.").assertExists()
        onNodeWithTag(MidiCoreProjectPageTags.RETRY).assertIsEnabled().performClick()
        assertEquals(MidiCoreWorkspaceIntent.Retry, intents.single())
    }

    @Test
    fun `project page source has no audio-era setup or readiness controls`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreProjectPage.kt"))
        listOf("render format", "sound profile", "source-part count", "commercial", "worker", "Settings", "Mix", "Library").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Project page must not contain $forbidden")
        }
        assertTrue(source.contains("MIDI Core project"))
        assertFalse(source.contains("AudioPreparation"))
    }

    private fun currentProjectState(): MidiCoreWorkspaceState = MidiCoreWorkspaceState(
        project = MidiCoreProject(
            id = ProjectId("current-project"),
            metadata = ProjectMetadata("Current song", "2026-08-28T00:00:00Z"),
            revision = 12L,
        ),
        projectRoot = Path.of("build/current-project"),
        blockers = listOf(
            MidiCoreWorkspaceBlocker(
                code = MidiCoreWorkspaceBlockerCode.SOURCE_REQUIRED,
                message = "A source MIDI file has not been imported.",
                nextAction = "Import one Standard MIDI source.",
            ),
        ),
    )

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}
