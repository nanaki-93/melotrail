package ai.music.workstation.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @Test
    fun `workspace shell exposes its core regions`() = runComposeUiTest {
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(WorkspaceUiState(), onIntent = {})
            }
        }

        listOf(
            WorkspaceTags.PROJECT_HEADER,
            WorkspaceTags.PARTS_PANEL,
            WorkspaceTags.STRUCTURE_PANEL,
            WorkspaceTags.ARRANGEMENT_PANEL,
            WorkspaceTags.TIMELINE_PANEL,
            WorkspaceTags.MIX_PANEL,
            WorkspaceTags.OPERATION_STATUS
        ).forEach { onNodeWithTag(it).assertIsDisplayed() }
    }

    @Test
    fun `empty project workflow exposes import actions and an audio requirement dialog`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState(), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ADD_MIDI).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.ADD_AUDIO).performClick()
        assertEquals(WorkspaceIntent.ShowImportPart(audio = true), intents.last())

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ImportPart(audio = true)), onIntent = {})
            }
        }
        onNodeWithText("Add audio part").assertIsDisplayed()
        onNodeWithText("Prepare part").assertIsDisplayed()
    }

    @Test
    fun `role editor and keyboard structure movement controls are visible`() = runComposeUiTest {
        val project = projectState().project!!.copy(
            parts = listOf(ai.music.workstation.application.PartSummary("A", "verse", "source/A.mid", "A.mid", ai.music.workstation.application.PartSourceType.MIDI, null)),
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 12.0))
        )
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(
                    WorkspaceUiState(project = project, structureDraft = listOf("A"), dialog = WorkspaceDialog.EditRole("A", "verse")),
                    onIntent = {}
                )
            }
        }

        onNodeWithText("Edit A role").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.STRUCTURE_MOVE_RIGHT + "0").assertIsDisplayed()
    }

    private fun projectState(): WorkspaceUiState {
        val root = java.nio.file.Path.of("build/test-project")
        return WorkspaceUiState(project = ai.music.workstation.application.ProjectSnapshot(
            root = root,
            version = 2,
            name = "test-project",
            renderFormat = ai.music.workstation.arrangement.RenderFormat(),
            parts = emptyList(),
            structure = emptyList(),
            readiness = ai.music.workstation.application.ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
        ))
    }
}
