package ai.music.workstation.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

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
}
