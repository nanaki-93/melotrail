package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.input.key.Key
import app.melotrail.application.PartAnalysisStatus
import app.melotrail.application.PartAnalysisSummary
import app.melotrail.application.PartPreparationSummary
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.ProjectReadiness
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.StructureSectionSummary
import app.melotrail.arrangement.RenderFormat
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @Test
    fun `each destination composes one navigation surface and only its page root`() = runComposeUiTest {
        WorkspaceSection.entries.forEach { selected ->
            setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = selected), onIntent = {}) } }

            onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
            WorkspaceSection.entries.forEach { destination ->
                onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + destination.name.lowercase())
                    .assertCountEquals(if (destination == selected) 1 else 0)
            }
        }
    }

    @Test
    fun `navigation dispatches selection without composing a second page`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }

        onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.STRUCTURE.name.lowercase()).performClick()

        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE), intents.single())
        onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
    }

    @Test
    fun `overview navigation remains keyboard reachable`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }

        val importNavigation = onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.IMPORT.name.lowercase())
        importNavigation.performClick()
        intents.clear()
        importNavigation.performKeyInput { pressKey(Key.Enter) }

        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.IMPORT), intents.single())
    }

    @Test
    fun `overview exposes real-state regions one export route and one shared transport`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }

        listOf(
            WorkspacePageTags.OVERVIEW_SECTION_STRIP,
            WorkspacePageTags.OVERVIEW_TRACKS,
            WorkspacePageTags.OVERVIEW_PREVIEW,
            WorkspacePageTags.OVERVIEW_SECTION_INFO,
            WorkspacePageTags.OVERVIEW_EXPORT,
            WorkspaceTags.COMPACT_TRANSPORT
        ).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        onAllNodesWithTag(WorkspaceTags.COMPACT_TRANSPORT).assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.OVERVIEW_EXPORT).performClick()
        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.EXPORT), intents.last())
    }

    @Test
    fun `overview labels unavailable signal video and waveform state instead of inventing data`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(), onIntent = {}) } }

        onAllNodesWithTag(WorkspacePageTags.OVERVIEW_TRACKS).assertCountEquals(1)
        onAllNodesWithTag(WorkspacePageTags.OVERVIEW_PREVIEW).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.FOOTER_WAVEFORM).assertCountEquals(1)
    }

    @Test
    fun `deterministic overview fixture uses reference page-shell geometry`() = runSkikoComposeUiTest(size = Size(1158f, 462f)) {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), onIntent = {}) } }

        val image = onNodeWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.OVERVIEW.name.lowercase()).captureToImage()
        assertEquals(1126, image.width)
        assertEquals(302, image.height)
        // Major card edges are measured from the 1158 × 462 large Overview crop in App-pages.png.
        val preview = onNodeWithTag(WorkspacePageTags.OVERVIEW_PREVIEW).getUnclippedBoundsInRoot()
        assertTrue(abs((preview.right - preview.left).value - MusicWorkspaceTokens.Pages.OverviewPreviewWidth.value) <= 4f, "preview width: ${preview.right - preview.left}")
    }

    private fun populatedState(): WorkspaceUiState = WorkspaceUiState(
        project = ProjectSnapshot(
            root = Path.of("build/task-083-project"), version = 3, name = "Midnight Train",
            renderFormat = RenderFormat(44_100, 2),
            parts = listOf(
                PartSummary("A", "verse", "source/A.mid", "A.mid", PartSourceType.MIDI, PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json", bars = 16, durationSeconds = 32.0), readyPreparation()),
                PartSummary("B", "chorus", "source/B.mid", "B.mid", PartSourceType.MIDI, PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/B.json", bars = 16, durationSeconds = 32.0), readyPreparation())
            ),
            structure = listOf(
                StructureSectionSummary(0, "A", 1, "A1", 32.0),
                StructureSectionSummary(1, "B", 1, "B1", 32.0)
            ),
            readiness = ProjectReadiness(
                cleanMidiReady = true, analysesReady = true, structureReady = true,
                songPlanAvailable = false, arrangementAvailable = false, generatedMidiAvailable = false,
                stemsAvailable = false, dryMixAvailable = false, loFiMixAvailable = false, masterAvailable = false
            )
        ),
        selectedArrangementSection = 0
    )

    private fun readyPreparation() = PartPreparationSummary(
        sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true,
        cleanMidi = true, analyzed = true, ready = true, warnings = emptyList()
    )
}
