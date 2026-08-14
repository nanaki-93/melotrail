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
            WorkspaceTags.WORKSPACE_NAV,
            WorkspaceTags.PARTS_PANEL,
            WorkspaceTags.STRUCTURE_PANEL,
            WorkspaceTags.ARRANGEMENT_PANEL,
            WorkspaceTags.TIMELINE_PANEL,
            WorkspaceTags.MIX_PANEL,
            WorkspaceTags.OPERATION_STATUS
        ).forEach { onNodeWithTag(it).assertExists() }
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

    @Test
    fun `arrangement controls and validated proportional timeline are accessible`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val root = java.nio.file.Path.of("build/test-project")
        val arrangement = ai.music.workstation.application.ArrangementSnapshot(
            root, listOf(
                ai.music.workstation.application.ArrangementSectionSnapshot(0, "A1", "A", "introduction", 0.3, listOf(
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("piano", "source", null, null)
                ), "build", 2.0),
                ai.music.workstation.application.ArrangementSectionSnapshot(1, "A2", "A", "climax", 0.8, listOf(
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("piano", "source", null, null),
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("bass", "generated", "bass", 0.7)
                ), "none", 6.0)
            ), approvalRequired = true, approved = false, stale = false, artifact = root.resolve("arrangement.draft.json")
        )
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(arrangement = arrangement, selectedArrangementSection = 0), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ARRANGEMENT_GENERATE).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_PREVIEW).assertExists()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_APPROVE).assertExists()
        onNodeWithText("Transition out: build").assertExists()
    }

    @Test
    fun `mix and transport expose available artifact controls`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val project = projectState().project!!.copy(
            readiness = ai.music.workstation.application.ProjectReadiness(true, true, true, true, true, true, true, true, true, true)
        )
        val mix = ai.music.workstation.application.MixSnapshot(
            root, ai.music.workstation.application.PersistedMixSettings(), listOf("piano"), root.resolve("mix/dry.wav"), stale = false
        )
        val arrangement = ai.music.workstation.application.ArrangementSnapshot(root, emptyList(), false, true, false, root.resolve("arrangement.json"))
        setContent { MusicWorkstationTheme { WorkspaceScreen(WorkspaceUiState(project = project, arrangement = arrangement, mix = mix), onIntent = {}) } }

        onNodeWithTag(WorkspaceTags.BUILD_SONG).assertExists()
        onNodeWithTag(WorkspaceTags.MIX_RESET).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_DRY).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_LOFI).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_MASTER).assertExists()
    }

    @Test
    fun `discard and close confirmations are keyboard reachable dialogs`() = runComposeUiTest {
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmDiscardDraft(root = java.nio.file.Path.of("build/other"))), onIntent = {})
            }
        }
        onNodeWithText("Discard arrangement draft?").assertIsDisplayed()
        onNodeWithText("Discard and continue").assertIsDisplayed()

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmClose), onIntent = {})
            }
        }
        onNodeWithText("Close Personal AI Music Arranger?").assertIsDisplayed()
        onNodeWithText("Keep working").assertIsDisplayed()
    }

    @Test
    fun `sound library dialog exposes choose clear and validation feedback`() = runComposeUiTest {
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(WorkspaceUiState(dialog = WorkspaceDialog.SoundLibrarySettings, soundLibrary = SoundLibrarySettingsState(validationError = "Registry is invalid")), onIntent = {})
            }
        }
        onNodeWithTag(WorkspaceTags.SOUND_LIBRARY_CHOOSE).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.SOUND_LIBRARY_CLEAR).assertIsDisplayed()
        onNodeWithText("Registry is invalid").assertIsDisplayed()
    }

    @Test
    fun `readiness header renders checking ready partial and failed states`() = runComposeUiTest {
        val states = listOf(
            RuntimeReadiness.checking(),
            runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")),
            runtimeReadiness(DependencyReadiness(DependencyStatus.UNAVAILABLE, "Basic Pitch missing")),
            runtimeReadiness(DependencyReadiness(DependencyStatus.FAILED, "Registry invalid"))
        )
        states.forEach { readiness ->
            setContent { MusicWorkstationTheme { WorkspaceScreen(projectState().copy(runtimeReadiness = readiness), onIntent = {}) } }
            onNodeWithTag(WorkspaceTags.PROJECT_HEADER).assertIsDisplayed()
        }
    }

    private fun runtimeReadiness(worker: DependencyReadiness): RuntimeReadiness = RuntimeReadiness.of(
        RuntimeDependency.WORKER to worker,
        RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
    )

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
        ), runtimeReadiness = runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")))
    }
}
