package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.input.key.Key
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
class MidiCoreWorkspaceShellTest {
    @Test
    fun `target navigation has exactly six accessible destinations and no legacy labels`() = runComposeUiTest {
        assertEquals(
            listOf("Project", "MIDI", "Structure & Harmony", "Arrange", "Review", "Export"),
            midiCoreWorkspaceDestinations.map(MidiCoreWorkspaceDestination::label),
        )
        assertEquals(
            listOf("project", "midi", "structure-harmony", "arrange", "review", "export"),
            midiCoreWorkspaceDestinations.map(MidiCoreWorkspaceDestination::route),
        )

        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = targetState(),
                    initialDestination = MidiCoreWorkspaceDestination.MIDI,
                )
            }
        }

        onNodeWithTag(MidiCoreWorkspaceShellTags.NAVIGATION).assertExists()
        midiCoreWorkspaceDestinations.forEach { destination ->
            onNodeWithTag(MidiCoreWorkspaceShellTags.destination(destination)).assertIsEnabled()
        }
        onNodeWithContentDescription("Open MIDI. ${MidiCoreWorkspaceDestination.MIDI.summary} Selected.").assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.BLOCKERS).assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.BLOCKER_PREFIX + "source_required").assertExists()

        onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.ARRANGE)).performClick()
        onNodeWithContentDescription("Open Arrange. ${MidiCoreWorkspaceDestination.ARRANGE.summary} Selected.").assertExists()
        onNodeWithContentDescription("Open MIDI. ${MidiCoreWorkspaceDestination.MIDI.summary} Selected.").assertDoesNotExist()

        listOf("Mix & Master", "Library", "Video Preview", "Release", "Settings", "Overview", "Import", "Harmony")
            .forEach { label -> onAllNodesWithText(label).assertCountEquals(0) }
    }

    @Test
    fun `destination controls preserve keyboard focus and activation semantics`() = runComposeUiTest {
        setContent { MelotrailTheme { MidiCoreWorkspaceShell(targetState()) } }

        val project = onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.PROJECT))
        project.performSemanticsAction(SemanticsActions.RequestFocus)
        project.assertIsFocused()
        project.performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.MIDI)).assertIsFocused()
        onNodeWithTag(MidiCoreWorkspaceShellTags.destination(MidiCoreWorkspaceDestination.MIDI)).performKeyInput { pressKey(Key.Enter) }
        onNodeWithContentDescription("Open MIDI. ${MidiCoreWorkspaceDestination.MIDI.summary} Selected.").assertExists()
    }

    @Test
    fun `wide layout uses a rail and compact layout uses a horizontally traversable row`() = runSkikoComposeUiTest(size = Size(1280f, 900f)) {
        setContent { MelotrailTheme { MidiCoreWorkspaceShell(targetState()) } }

        onNodeWithTag(MidiCoreWorkspaceShellTags.WIDE_LAYOUT).assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.WIDE_NAVIGATION).assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_LAYOUT).assertDoesNotExist()
        onNodeWithTag(MidiCoreWorkspaceShellTags.PAGE + "-project").assertExists()
    }

    @Test
    fun `compact layout keeps all target navigation reachable without old routes`() = runSkikoComposeUiTest(size = Size(720f, 900f)) {
        setContent { MelotrailTheme { MidiCoreWorkspaceShell(targetState()) } }

        onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_LAYOUT).assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_NAVIGATION).assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.WIDE_LAYOUT).assertDoesNotExist()
        midiCoreWorkspaceDestinations.forEach { destination ->
            onNodeWithTag(MidiCoreWorkspaceShellTags.destination(destination)).assertIsEnabled()
        }
    }

    @Test
    fun `target shell source contains no superseded navigation owner or route`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreWorkspaceShell.kt"))
        listOf(
            "WorkspaceSection",
            "class WorkspaceViewModel",
            "WorkspacePageRouter",
            "Mix & Master",
            "Library",
            "Video Preview",
            "Release",
            "Settings",
        ).forEach { forbidden -> assertFalse(source.contains(forbidden), "Target shell must not contain $forbidden") }
        assertTrue(source.contains("Structure & Harmony"))
    }

    private fun targetState(): MidiCoreWorkspaceState = MidiCoreWorkspaceState(
        project = MidiCoreProject(
            id = ProjectId("shell-project"),
            metadata = ProjectMetadata("Shell project", "2026-08-28T00:00:00Z"),
            revision = 7L,
        ),
        projectRoot = Path.of("build/shell-project"),
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
