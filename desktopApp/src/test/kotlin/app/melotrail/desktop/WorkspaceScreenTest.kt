package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.melotrail.application.PartAnalysisStatus
import app.melotrail.application.PartAnalysisSummary
import app.melotrail.application.PartPreparationSummary
import app.melotrail.application.PartSourceType
import app.melotrail.application.PartSummary
import app.melotrail.application.ArrangementPlannerKind
import app.melotrail.application.ArrangementInstrumentSnapshot
import app.melotrail.application.ArrangementSectionSnapshot
import app.melotrail.application.ArrangementSnapshot
import app.melotrail.application.EnsembleCohesionBoundarySnapshot
import app.melotrail.application.EnsembleCohesionPlannerKind
import app.melotrail.application.EnsembleCohesionSnapshot
import app.melotrail.application.LogicalMixSetting
import app.melotrail.application.MixSnapshot
import app.melotrail.application.PersistedMixSettings
import app.melotrail.application.ProjectReadiness
import app.melotrail.application.ProjectSnapshot
import app.melotrail.application.HarmonyCompleteness
import app.melotrail.application.HarmonyView
import app.melotrail.application.StructureSectionSummary
import app.melotrail.application.ReleaseExportFormat
import app.melotrail.application.ReleaseExportInspection
import app.melotrail.application.ReleaseExportSummary
import app.melotrail.application.LocalSoundLibraryInstrument
import app.melotrail.application.LocalSoundLibraryInventory
import app.melotrail.application.LocalSoundLibraryInventoryState
import app.melotrail.arrangement.RenderFormat
import app.melotrail.harmony.ChordEvent
import app.melotrail.harmony.ChordEventId
import app.melotrail.harmony.ChordProgression
import app.melotrail.harmony.ChordQuality
import app.melotrail.harmony.SectionTypeId
import app.melotrail.music.PitchClass
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @Test
    fun `each destination composes one navigation surface and only its page root`() = runComposeUiTest {
        WorkspaceSection.entries.forEach { selected ->
            setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = selected), onIntent = {}) } }

            onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
            onAllNodesWithTag(WorkspaceTags.GLOBAL_FEEDBACK).assertCountEquals(1)
            WorkspaceSection.entries.forEach { destination ->
                onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + destination.name.lowercase())
                    .assertCountEquals(if (destination == selected) 1 else 0)
            }
        }
    }

    @Test
    fun `wide shell has one top navigation one page root and a context rail`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }
        onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
        onNodeWithTag(WorkspaceShellTags.WIDE_NAVIGATION).assertExists()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_RAIL).assertExists()
        assertOnlyLibraryPageRoot()
        writeShellCapture("wide", onNodeWithTag(WorkspaceShellTags.ROOT).captureToImage().toAwtImage())
    }

    @Test
    fun `medium shell has one compact rail navigation and collapsible context`() = runSkikoComposeUiTest(size = Size(1000f, 900f)) {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }
        onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
        onNodeWithTag(WorkspaceShellTags.MEDIUM_NAVIGATION).assertExists()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_RAIL).assertExists()
        assertOnlyLibraryPageRoot()
        writeShellCapture("medium", onNodeWithTag(WorkspaceShellTags.ROOT).captureToImage().toAwtImage())
    }

    @Test
    fun `narrow shell has one chooser navigation and a stacked context`() = runSkikoComposeUiTest(size = Size(720f, 1120f)) {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }
        onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.NAVIGATION_MENU).assertExists()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_RAIL).assertExists()
        assertOnlyLibraryPageRoot()
        writeShellCapture("narrow", onNodeWithTag(WorkspaceShellTags.ROOT).captureToImage().toAwtImage())
    }

    @Test
    fun `Library projects only typed local inventory with filtering layout selection and truthful recovery`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val inventory = LocalSoundLibraryInventory(
            LocalSoundLibraryInventoryState.READY,
            listOf(
                libraryInstrument("bass", "Bass"),
                libraryInstrument("piano", "A deliberately long local piano instrument name that must truncate safely")
            ).sortedBy { it.id }
        )
        val state = populatedState().copy(
            workspaceSection = WorkspaceSection.LIBRARY,
            libraryBrowser = LibraryBrowserState(inventory = inventory, selectedId = "piano")
        )
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithTag(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE).performClick()
        listOf(
            WorkspacePageTags.LIBRARY_TYPE_TAB,
            WorkspacePageTags.LIBRARY_SEARCH,
            WorkspacePageTags.LIBRARY_GRID,
            WorkspacePageTags.LIBRARY_CARD_PREFIX + "piano",
            WorkspacePageTags.LIBRARY_DETAIL
        ).forEach { onNodeWithTag(it).assertExists() }
        onNodeWithTag(WorkspacePageTags.LIBRARY_SEARCH).performTextInput("bass")
        assertEquals(WorkspaceIntent.UpdateLibrarySearch("bass"), intents.last())
        onNodeWithTag(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE + "-categories").performClick()
        waitForIdle()
        onNodeWithTag(WorkspacePageTags.LIBRARY_CATEGORY_PREFIX + "bass").performClick()
        assertEquals(WorkspaceIntent.SelectLibraryCategory("Bass"), intents.last())
        onNodeWithTag(WorkspacePageTags.LIBRARY_CARD_PREFIX + "bass").assertExists()
        onNodeWithTag(WorkspacePageTags.LIBRARY_OPTIONS_TOGGLE + "-layout").assertExists()
        listOf("Add Item", "Download", "Favorite", "Insert to Project", "Storage", "Page 1").forEach { text ->
            onAllNodesWithText(text).assertCountEquals(0)
        }

        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }
        onNodeWithTag(WorkspacePageTags.LIBRARY_RECOVERY).assertExists()
        onNodeWithText("No catalog data is shown until the registry, SFZ files, and samples validate locally.").assertExists()
    }

    @Test
    fun `unsupported library types and shell help are omitted rather than shown as disabled actions`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.LIBRARY_TYPE_TAB).assertExists()
        onAllNodesWithContentDescription("Help is unavailable in this local build.").assertCountEquals(0)
        listOf("Samples", "Loops", "Download", "Add Item").forEach { unsupported ->
            onAllNodesWithText(unsupported).assertCountEquals(0)
        }
    }

    @Test
    fun `Library grid and empty fixtures capture the numbered reference without mock catalog claims`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val inventory = LocalSoundLibraryInventory(
            LocalSoundLibraryInventoryState.READY,
            listOf("bass", "drums", "pad", "piano", "strings").map { libraryInstrument(it, it.replaceFirstChar(Char::uppercase)) }.sortedBy { it.id }
        )
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.LIBRARY, libraryBrowser = LibraryBrowserState(inventory = inventory, selectedId = "piano")), onIntent = {}) } }
        val image = onRoot().captureToImage().toAwtImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        writeTask098LibraryCapture(image)
        writeTask098LibraryReferenceOverlay(image)
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {}) } }
        writeTask098LibraryUnconfiguredCapture(onRoot().captureToImage().toAwtImage())
    }

    @Test
    fun `narrow Import keeps one responsive entry surface and opens a full-height preparation sheet with focus return`() = runSkikoComposeUiTest(size = Size(720f, 1120f)) {
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("piano.mid", rawMidi = true)), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_DROP_SURFACE).assertExists()
        onAllNodesWithTag(WorkspacePageTags.IMPORT_AUDIO_CHOOSER).assertCountEquals(0)
        onAllNodesWithTag(WorkspacePageTags.IMPORT_MIDI_CHOOSER).assertCountEquals(0)
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        val sheet = onNodeWithTag(WorkspaceShellTags.CONTEXT_RAIL)
        sheet.assertExists()
        assertTrue((sheet.getUnclippedBoundsInRoot().bottom - sheet.getUnclippedBoundsInRoot().top).value >= 1_000f)
        sheet.performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(WorkspaceShellTags.CONTEXT_RAIL).assertDoesNotExist()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).assertIsFocused()
        onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.IMPORT.name.lowercase()).assertCountEquals(1)
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
    fun `part comparison exposes labeled keyboard reachable A B preview actions`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val state = importState(importPart("A.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT)).copy(
            selectedPartId = "A",
            dialog = WorkspaceDialog.PartDetails("A", PartDetailsFocusReturn.ImportedRow("A"))
        )
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithTag(WorkspaceTags.PART_COMPARISON).assertExists()
        val original = onNodeWithTag(WorkspaceTags.PART_COMPARISON_PLAY_PREFIX + "original")
        original.performSemanticsAction(SemanticsActions.RequestFocus)
        original.performKeyInput { pressKey(Key.Enter) }

        assertEquals(WorkspaceIntent.PreviewMidiPart("A", app.melotrail.application.PreviewMidiSource.RAW), intents.single())
        onNodeWithContentDescription("Play Original MIDI for A/B comparison").assertExists()
    }

    @Test
    fun `Harmony offers key-aware progression choices per section`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val harmony = harmonyView(
            ChordProgression(SectionTypeId.VERSE, listOf(
                ChordEvent(ChordEventId("v1"), PitchClass.canonical(0), ChordQuality.MAJOR, 0),
                ChordEvent(ChordEventId("v2"), PitchClass.canonical(5), ChordQuality.MAJOR, 1)
            )),
            ChordProgression(SectionTypeId.CHORUS), ChordProgression(SectionTypeId.BRIDGE)
        )
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(
            workspaceSection = WorkspaceSection.HARMONY,
            harmony = HarmonyEditorUiState(view = harmony, selectedEventId = ChordEventId("v2"), draftRoot = PitchClass.canonical(5))
        ), intents::add) } }

        val template = app.melotrail.harmony.HarmonyTemplateCatalog.options(
            app.melotrail.music.MusicalKey(PitchClass.canonical(0), app.melotrail.music.ScaleModeId.MAJOR)
        ).first()
        listOf(
            HarmonyPageTags.TABS,
            HarmonyPageTags.TAB_PREFIX + "intro",
            HarmonyPageTags.TAB_PREFIX + "verse",
            HarmonyPageTags.TAB_PREFIX + "chorus",
            HarmonyPageTags.TAB_PREFIX + "bridge",
            HarmonyPageTags.TAB_PREFIX + "outro",
            HarmonyPageTags.TEMPLATE_PREFIX + template.id.value
        )
            .forEach { onNodeWithTag(it).assertExists() }
        onNodeWithTag(HarmonyPageTags.TAB_PREFIX + "chorus").performClick()
        assertEquals(WorkspaceIntent.SelectHarmonySection(SectionTypeId.CHORUS), intents.last())
        onNodeWithTag(HarmonyPageTags.TEMPLATE_PREFIX + template.id.value).performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag(HarmonyPageTags.TEMPLATE_PREFIX + template.id.value).performKeyInput { pressKey(Key.Enter) }
        assertEquals(WorkspaceIntent.SelectHarmonyTemplate(template.id), intents.last())
        onNodeWithContentDescription("Use ${template.label}: ${template.romanNumerals}").assertExists()
    }

    @Test
    fun `Harmony loading state composes before the harmony view arrives`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(
                    populatedState().copy(
                        workspaceSection = WorkspaceSection.HARMONY,
                        harmony = HarmonyEditorUiState(loading = true)
                    ),
                    onIntent = {}
                )
            }
        }

        onNodeWithTag(HarmonyPageTags.STATUS).assertExists()
        onNodeWithText("Loading canonical harmony…").assertExists()
        onAllNodesWithTag(HarmonyPageTags.TABS).assertCountEquals(0)
    }

    @Test
    fun `Setup exposes typed controls recommendation and confirmation without navigation writes`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(setupState(), intents::add) } }

        listOf(
            WorkspacePageTags.SETUP_FORM, WorkspacePageTags.SETUP_NAME, WorkspacePageTags.SETUP_TONIC,
            WorkspacePageTags.SETUP_MODE, WorkspacePageTags.SETUP_TEMPO, WorkspacePageTags.SETUP_METER,
            WorkspacePageTags.SETUP_PROFILE, WorkspacePageTags.SETUP_MOOD, WorkspacePageTags.SETUP_RECOMMENDATION,
            WorkspacePageTags.SETUP_INVALIDATION, WorkspacePageTags.SETUP_CONFIRM
        ).forEach { onNodeWithTag(it).assertExists() }
        onNodeWithTag(WorkspacePageTags.SETUP_CONFIRM).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.ConfirmProjectSetupSave, intents.single())

        assertEquals(WorkspaceIntent.ConfirmProjectSetupSave, intents.single())
    }

    @Test
    fun `Setup remains one keyboard reachable page at wide medium and narrow breakpoints`() {
        listOf(Size(1536f, 1024f), Size(1000f, 900f), Size(720f, 1120f)).forEach { size ->
            runSkikoComposeUiTest(size = size) {
                setContent { MelotrailTheme { WorkspaceScreen(setupState(), onIntent = {}) } }
                onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.SETUP.name.lowercase()).assertCountEquals(1)
                onNodeWithTag(WorkspacePageTags.SETUP_NAME).performClick()
                onNodeWithTag(WorkspacePageTags.SETUP_NAME).assertIsFocused()
                onNodeWithTag(WorkspacePageTags.SETUP_SAVE).assertDoesNotExist()
            }
        }
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
    fun `no-project header makes labelled project actions primary accessible and ordered`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(), intents::add) } }

        onNodeWithText("New Project").assertExists()
        onNodeWithText("Open Project").assertExists()
        onNodeWithContentDescription("New Project").assertIsEnabled()
        onNodeWithContentDescription("Open Project").assertIsEnabled()
        onAllNodesWithTag(WorkspaceTags.CREATE_PROJECT).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.OPEN_PROJECT).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.HEADER_SAVE).assertCountEquals(0)
        onAllNodesWithTag(WorkspaceTags.HEADER_THEME).assertCountEquals(0)
        onNodeWithText("Start with New Project, or Open Project to continue an existing project.").assertExists()

        onNodeWithTag(WorkspaceTags.CREATE_PROJECT).performClick()
        onNodeWithTag(WorkspaceTags.CREATE_PROJECT).assertIsFocused()
        intents.clear()
        onNodeWithTag(WorkspaceTags.CREATE_PROJECT).performKeyInput { pressKey(Key.Tab) }
        onNodeWithTag(WorkspaceTags.OPEN_PROJECT).assertIsFocused()
        onNodeWithTag(WorkspaceTags.OPEN_PROJECT).performKeyInput { pressKey(Key.Enter) }
        assertEquals(WorkspaceIntent.ChooseProject, intents.single())
    }

    @Test
    fun `project-open navigation keeps guided stages visible and secondary destinations in More`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }

        listOf(WorkspaceSection.OVERVIEW, WorkspaceSection.IMPORT, WorkspaceSection.STRUCTURE, WorkspaceSection.ARRANGE, WorkspaceSection.MIX_MASTER).forEach { section ->
            onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + section.name.lowercase()).assertExists()
        }
        listOf(WorkspaceSection.LIBRARY, WorkspaceSection.VIDEO_PREVIEW, WorkspaceSection.EXPORT, WorkspaceSection.SETTINGS).forEach { section ->
            onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + section.name.lowercase()).assertDoesNotExist()
        }

        onNodeWithTag(WorkspaceShellTags.OVERFLOW_MENU).performClick()
        onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.LIBRARY.name.lowercase()).performClick()
        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.LIBRARY), intents.single())
    }

    @Test
    fun `Info is the first primary destination and keeps the Overview route`() = runComposeUiTest {
        assertEquals(WorkspaceSection.OVERVIEW, primaryWorkspaceDestinations.first())
        assertEquals("Info", WorkspaceSection.OVERVIEW.label)

        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }
        onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.OVERVIEW.name.lowercase()).assertExists()
        onNodeWithContentDescription("Open Info, selected").assertExists()
    }

    @Test
    fun `project actions disable only while a mutation is in flight`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(operation = WorkspaceOperation.OpeningProject(Path.of("build", "opening"))), onIntent = {})
            }
        }

        onNodeWithTag(WorkspaceTags.CREATE_PROJECT).assertIsNotEnabled()
        onNodeWithTag(WorkspaceTags.OPEN_PROJECT).assertIsNotEnabled()
        onNodeWithTag(WorkspaceShellTags.OVERFLOW_MENU).assertIsEnabled()
    }

    @Test
    fun `New Project remains visible in wide medium and narrow shells`() {
        listOf(Size(1536f, 1024f), Size(1000f, 900f), Size(720f, 1120f)).forEach { size ->
            runSkikoComposeUiTest(size = size) {
                setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(), onIntent = {}) } }
                val bounds = onNodeWithTag(WorkspaceTags.CREATE_PROJECT).getUnclippedBoundsInRoot()
                assertTrue(
                    bounds.left.value >= 0f && bounds.right.value <= size.width && bounds.top.value >= 0f && bounds.bottom.value <= size.height,
                    "New Project must remain visible at ${size.width} by ${size.height}px"
                )
                onNodeWithText("New Project").assertExists()
            }
        }
    }

    @Test
    fun `narrow shells keep one reachable navigation and their primary page regions within the viewport`() = runSkikoComposeUiTest(size = Size(720f, 1_120f)) {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), intents::add) } }

        onAllNodesWithTag(WorkspaceTags.WORKSPACE_NAV).assertCountEquals(1)
        onNodeWithTag(WorkspaceShellTags.OVERFLOW_MENU).performClick()
        onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.EXPORT.name.lowercase()).performClick()
        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.EXPORT), intents.single())

        listOf(WorkspaceTags.PROJECT_HEADER, WorkspaceTags.CREATE_PROJECT, WorkspaceTags.OPEN_PROJECT, WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.OVERVIEW.name.lowercase(), WorkspacePageTags.OVERVIEW_PREVIEW, WorkspacePageTags.OVERVIEW_ARTIFACTS).forEach { assertFitsNarrowViewport(it) }

        setContent { MelotrailTheme { WorkspaceScreen(exportState(), onIntent = {}) } }
        listOf(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.EXPORT.name.lowercase(), WorkspacePageTags.EXPORT_SUMMARY, WorkspacePageTags.EXPORT_ACTION).forEach { assertFitsNarrowViewport(it) }

        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState(), onIntent = {}) } }
        listOf(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.MIX_MASTER.name.lowercase(), WorkspacePageTags.MIX_CHANNEL_PREFIX + "piano", WorkspacePageTags.MIX_PRIMARY_ACTION).forEach { assertFitsNarrowViewport(it) }
    }

    @Test
    fun `Info exposes a read-only project snapshot`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState(), onIntent = {}) } }

        listOf(
            WorkspacePageTags.OVERVIEW_SUMMARY,
            WorkspacePageTags.OVERVIEW_SECTION_STRIP,
            WorkspacePageTags.OVERVIEW_TRACKS,
            WorkspacePageTags.OVERVIEW_PREVIEW,
            WorkspacePageTags.OVERVIEW_PROJECT_INFO,
            WorkspacePageTags.OVERVIEW_ARTIFACTS,
            WorkspacePageTags.OVERVIEW_ACTIVITY,
        ).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        listOf(
            WorkspacePageTags.OVERVIEW_QUICK_ACTIONS,
            WorkspacePageTags.OVERVIEW_PRIMARY_ACTION,
            WorkspacePageTags.OVERVIEW_MORE_ACTIONS_TOGGLE,
            WorkspaceTags.COMPACT_TRANSPORT,
            WorkspaceTags.PLAYBACK_TOGGLE
        ).forEach { onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test
    fun `Info labels unavailable metadata and artifact state instead of inventing data`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(), onIntent = {}) } }

        onAllNodesWithTag(WorkspacePageTags.OVERVIEW_TRACKS).assertCountEquals(1)
        onAllNodesWithTag(WorkspacePageTags.OVERVIEW_PREVIEW).assertCountEquals(1)
        onAllNodesWithText("Track availability unavailable").assertCountEquals(2)
        onNodeWithText("Tempo: unavailable").assertExists()
        onNodeWithText("Key: unavailable").assertExists()
        onNodeWithText("Project artifacts unavailable").assertExists()
        onNodeWithText("Local video preview unavailable").assertExists()
    }

    @Test
    fun `overview reports loading and failed playback from the shared session`() = runComposeUiTest {
        val failed = WorkspaceUiState(
            playbackSession = PlaybackSession(phase = PlaybackSessionPhase.FAILED, failureMessage = "Audio device unavailable"),
            operation = WorkspaceOperation.ImportingPart("A"),
            operationFeedback = OperationFeedback("task-093-loading", OperationKind.IMPORT, OperationPhase.LOCAL, message = "Inspecting source…")
        )
        setContent { MelotrailTheme { WorkspaceScreen(failed, onIntent = {}) } }

        onNodeWithText("Playback unavailable: Audio device unavailable").assertExists()
        onNodeWithText("Loading").assertExists()
        onAllNodesWithText("Inspecting source…").assertCountEquals(2)
    }

    @Test
    fun `Info does not expose workflow shortcuts`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(overviewReadyState(), intents::add) } }

        listOf(
            WorkspacePageTags.OVERVIEW_PRIMARY_ACTION,
            WorkspacePageTags.OVERVIEW_MORE_ACTIONS_TOGGLE,
            WorkspacePageTags.OVERVIEW_MORE_ACTIONS
        ).forEach { onNodeWithTag(it).assertDoesNotExist() }
        assertTrue(intents.isEmpty())
    }

    @Test
    fun `Info shows static occurrence identities and stale track state`() = runComposeUiTest {
        val sections = listOf(
            arrangementSection(0, "A1", 16.0, "piano"),
            arrangementSection(1, "B1", 20.0, "bass"),
            arrangementSection(2, "A2", 24.0, "piano", "drums"),
            arrangementSection(3, "C1", 18.0, "pad"),
            arrangementSection(4, "B2", 22.0, "strings")
        )
        val stale = overviewReadyState(arrangement = arrangementSnapshot(approved = true, sections = sections).copy(stale = true)).let { state ->
            state.copy(project = state.project!!.copy(structure = sections.map { section ->
                StructureSectionSummary(section.index, section.partId, section.index + 1, section.instanceId, section.durationSeconds)
            }))
        }
        setContent { MelotrailTheme { WorkspaceScreen(stale, onIntent = {}) } }

        onAllNodesWithTag(WorkspacePageTags.OVERVIEW_SECTION_PREFIX + "A2").assertCountEquals(1)
        onNodeWithContentDescription("Section A2").assertExists()
        onAllNodesWithText("Stale lane").assertCountEquals(5)
    }

    @Test
    fun `Info has no playback action while Video Preview and Export share playback`() = runComposeUiTest {
        val session = PlaybackSession(artifact = PlaybackArtifactIdentity(Path.of("build/task-093-project"), Path.of("build/task-093-project/mix/dry.wav")))
        val overviewIntents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(overviewReadyState().copy(playbackSession = session), overviewIntents::add) } }
        onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE).assertDoesNotExist()
        assertTrue(overviewIntents.isEmpty())

        val previewIntents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(overviewReadyState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = session, runtimeReadiness = readyRuntime()), previewIntents::add) } }
        onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.PlayPause, previewIntents.single())

        val exportIntents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(overviewReadyState().copy(workspaceSection = WorkspaceSection.EXPORT, export = exportState().export, playbackSession = session), exportIntents::add) } }
        onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.PlayPause, exportIntents.single())
    }

    @Test
    fun `Export page renders ready blocked exporting complete failed and optional MP3 unavailable states`() = runComposeUiTest {
        val ready = exportState()
        val blocked = ready.copy(export = ready.export.copy(inspection = ReleaseExportInspection(null, emptySet(), "Build a current master and release metadata first.")))
        val exporting = ready.copy(operation = WorkspaceOperation.ExportingRelease)
        val complete = ready.copy(operationFeedback = OperationFeedback("export-complete", OperationKind.EXPORT, OperationPhase.COMPLETE, message = "Exported Midnight Train.wav.", outcomeSeverity = OperationSeverity.SUCCESS))
        val failed = ready.copy(operation = WorkspaceOperation.Failed("export song", "Output validation failed"))
        listOf(ready, blocked, exporting, complete, failed).forEach { state ->
            setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }
            onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.EXPORT.name.lowercase()).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_AUDIO_ONLY).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_PREVIEW).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_SUMMARY).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_ACTION).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_CREDITS_PREVIEW).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.EXPORT_COMMERCIAL_ACTION).assertCountEquals(1)
        }
        setContent { MelotrailTheme { WorkspaceScreen(ready, onIntent = {}) } }
        onNodeWithTag(WorkspacePageTags.EXPORT_ACTION).assertIsEnabled()
        onNodeWithTag(WorkspacePageTags.EXPORT_OPTIONS_TOGGLE).performClick()
        onAllNodesWithTag(WorkspacePageTags.EXPORT_FORMAT_PREFIX + "wav").assertCountEquals(1)
        onAllNodesWithTag(WorkspacePageTags.EXPORT_FORMAT_PREFIX + "mp3").assertCountEquals(0)
        setContent { MelotrailTheme { WorkspaceScreen(blocked, onIntent = {}) } }
        onNodeWithTag(WorkspacePageTags.EXPORT_ACTION).assertIsNotEnabled()
        onNodeWithTag(WorkspacePageTags.EXPORT_RECOVERY).assertExists()

        val recoveryIntents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(blocked, recoveryIntents::add) } }
        onNodeWithTag(WorkspacePageTags.EXPORT_RECOVERY).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.MIX_MASTER), recoveryIntents.single())
    }

    @Test
    fun `Export page exposes typed destination and a ready export action`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(exportState(), intents::add) } }
        onNodeWithTag(WorkspacePageTags.EXPORT_OPTIONS_TOGGLE).performClick()
        waitForIdle()
        onNodeWithTag(WorkspacePageTags.EXPORT_BROWSE).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.ChooseExportDestination, intents.single())

        intents.clear()
        setContent { MelotrailTheme { WorkspaceScreen(exportState(), intents::add) } }
        onNodeWithTag(WorkspacePageTags.EXPORT_ACTION).assertIsEnabled()
    }

    @Test
    fun `Import renders one chooser one imported list and only the Import page root across required states`() = runComposeUiTest {
        val states = listOf(
            WorkspaceUiState(workspaceSection = WorkspaceSection.IMPORT),
            importState(importPart("validating.mid")).copy(operation = WorkspaceOperation.ImportingPart("A")),
            importState(importPart("piano.mid", rawMidi = true)),
            importState(importPart("solo-piano.wav", audio = true, inspected = false)),
            importState(importPart("needs-review.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED)),
            importState(importPart("ready.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT, analyzed = true)),
            importState(importPart("failed.mid")).copy(operation = WorkspaceOperation.Failed("import part", "Worker unavailable"), retry = WorkspaceRetry.Import(app.melotrail.application.ImportPartRequest(Path.of("build/import"), "A", Path.of("failed.mid"))))
        )
        states.forEach { state ->
            setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }
            onAllNodesWithTag(WorkspacePageTags.IMPORT_DROP_SURFACE).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.IMPORTED_FILES).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.IMPORT.name.lowercase()).assertCountEquals(1)
            listOf(WorkspaceSection.STRUCTURE, WorkspaceSection.ARRANGE, WorkspaceSection.MIX_MASTER, WorkspaceSection.OVERVIEW, WorkspaceSection.VIDEO_PREVIEW, WorkspaceSection.EXPORT).forEach { excluded ->
                onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + excluded.name.lowercase()).assertCountEquals(0)
            }
        }
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import primary actions dispatch existing typed intents and long filenames remain concise`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val longMidi = importPart("a-very-long-source-name-that-remains-readable-in-the-imported-files-list.mid", rawMidi = true)
        setContent { MelotrailTheme { WorkspaceScreen(importState(longMidi), intents::add) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performScrollTo().performClick()
        assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.CleanMidi("A")), intents)
        onAllNodesWithTag(WorkspacePageTags.IMPORTED_ROW_PREFIX + "A").assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "A").performClick()
        assertEquals(WorkspaceIntent.ShowPartDetails("A"), intents.last())
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `import overflow opens the clicked part details surface and dismissal preserves Import`() = runComposeUiTest {
        val midi = importPart("first.mid", rawMidi = true)
        val audio = importPart("second.wav", audio = true).copy(id = "B")
        var state by mutableStateOf(
            importState(midi).copy(project = importState(midi).project!!.copy(parts = listOf(midi, audio)))
        )
        setContent {
            MelotrailTheme {
                WorkspaceScreen(state, onIntent = { intent ->
                    state = when (intent) {
                        is WorkspaceIntent.ShowPartDetails -> state.copy(
                            selectedPartId = intent.partId,
                            dialog = WorkspaceDialog.PartDetails(intent.partId, intent.focusReturn)
                        )
                        WorkspaceIntent.DismissDialog -> state.copy(dialog = null)
                        else -> state
                    }
                })
            }
        }

        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "B").performClick()
        onAllNodesWithTag(WorkspaceTags.PART_DETAILS_DIALOG).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.PREPARATION_PANEL).assertCountEquals(1)
        onNodeWithTag(WorkspaceTags.PART_DETAILS_DIALOG).assertIsFocused()
        assertEquals("B", state.selectedPartId)

        onNodeWithTag(WorkspaceTags.PART_DETAILS_DIALOG).performKeyInput { pressKey(Key.Escape) }
        onAllNodesWithTag(WorkspaceTags.PART_DETAILS_DIALOG).assertCountEquals(0)
        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "B").assertIsFocused()

        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "B").performClick()
        onNodeWithTag(WorkspaceTags.PART_DETAILS_CLOSE).performClick()
        onAllNodesWithTag(WorkspaceTags.PART_DETAILS_DIALOG).assertCountEquals(0)
        onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.IMPORT.name.lowercase()).assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "B").assertIsFocused()
        assertEquals(WorkspaceSection.IMPORT, state.workspaceSection)
    }

    @Test
    fun `Import browse action is keyboard reachable`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("source.mid")), intents::add) } }

        val browse = onNodeWithTag(WorkspacePageTags.IMPORT_BROWSE)
        browse.performClick()
        intents.clear()
        browse.performKeyInput { pressKey(Key.Enter) }

        assertEquals(WorkspaceIntent.ShowAddPart, intents.single())
    }

    @Test
    fun `guided import dialog exposes one accessible route at a time`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        var state by mutableStateOf(importState(importPart("existing.mid")).copy(
            dialog = WorkspaceDialog.ImportPart(
                source = Path.of("verse.mid"),
                id = "verse",
                role = "verse",
                detectedType = ImportSourceKind.MIDI
            )
        ))
        setContent {
            MelotrailTheme {
                WorkspaceScreen(state, onIntent = { intent ->
                    intents += intent
                    state = when (intent) {
                        is WorkspaceIntent.UpdateImportPart -> state.copy(dialog = intent.draft)
                        WorkspaceIntent.ConfirmImportProvenance -> state.copy(dialog = (state.dialog as WorkspaceDialog.ImportPart).copy(provenanceConfirmed = true))
                        else -> state
                    }
                })
            }
        }

        onNodeWithTag(WorkspaceTags.IMPORT_STEPS).assertExists()
        onNodeWithText("Route: Direct MIDI import. The canonical importer validates the actual file container before immutable publication.").assertExists()
        onAllNodesWithText("Part ID (stable after import)").assertCountEquals(0)
        onNodeWithTag(WorkspaceTags.IMPORT_SOURCE).performClick()
        assertEquals(WorkspaceIntent.ChooseImportSource, intents.removeLast())
        onNodeWithTag(WorkspaceTags.IMPORT_DETAILS).performClick()
        onNodeWithText("The stable part ID is verse; its initial section is Verse. You can change the section with downstream-impact confirmation after import.").assertExists()
        state = state.copy(dialog = (state.dialog as WorkspaceDialog.ImportPart).copy(provenanceConfirmed = true))
        onNodeWithTag(WorkspaceTags.IMPORT_CONFIRM).assertIsEnabled()

        state = state.copy(dialog = WorkspaceDialog.ImportPart(
            source = Path.of("solo.wav"), id = "solo", role = "verse", audio = true, detectedType = ImportSourceKind.WAV
        ))
        onNodeWithText("Route: Solo-piano transcription. The canonical importer validates the actual file container before immutable publication.").assertExists()
        onNodeWithText("Import the immutable source and begin the bounded solo-piano transcription route.").assertDoesNotExist()
    }

    @Test
    fun `Import no-project state keeps the one browse entry disabled`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(workspaceSection = WorkspaceSection.IMPORT), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_BROWSE).assertIsNotEnabled()
        onAllNodesWithTag(WorkspacePageTags.IMPORT_AUDIO_CHOOSER).assertCountEquals(0)
        onAllNodesWithTag(WorkspacePageTags.IMPORT_MIDI_CHOOSER).assertCountEquals(0)
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import reconstruction keeps one entry action and a dense selected row`() = runComposeUiTest {
        val midi = importPart("piano_loop.mid", rawMidi = true, analyzed = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT).copy(
            analysis = PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json", bars = 16, durationSeconds = 32.0, key = "A minor"),
            sourceSizeBytes = 2_097_152L
        )
        val audio = importPart("solo_piano.wav", audio = true, inspected = false).copy(id = "B", sourceSizeBytes = 12_582_912L)
        var state by mutableStateOf(importState(midi).copy(project = importState(midi).project!!.copy(parts = listOf(midi, audio))))
        val intents = mutableListOf<WorkspaceIntent>()
        setContent {
            MelotrailTheme {
                WorkspaceScreen(state, onIntent = { intent ->
                    intents += intent
                    if (intent is WorkspaceIntent.SelectPart) state = state.copy(selectedPartId = intent.partId)
                })
            }
        }

        listOf(
            WorkspacePageTags.IMPORT_BROWSE,
            WorkspacePageTags.IMPORT_TABLE_HEADER,
            WorkspacePageTags.IMPORTED_ROW_PREFIX + "A",
            WorkspacePageTags.IMPORTED_ROW_PREFIX + "B"
        ).forEach { onNodeWithTag(it).assertExists() }
        onNodeWithTag(WorkspacePageTags.IMPORT_BROWSE).performClick()
        assertEquals(WorkspaceIntent.ShowAddPart, intents.removeLast())

        onNodeWithTag(WorkspacePageTags.IMPORTED_ROW_PREFIX + "B").performClick()
        assertEquals(WorkspaceIntent.SelectPart("B"), intents.removeLast())
        onNodeWithTag(WorkspacePageTags.IMPORT_SELECTION).assertExists()
        onNodeWithText("12.0 MiB").assertExists()
        onNodeWithText("A minor").assertExists()
        onNodeWithText("0:32").assertExists()
        onNodeWithTag(WorkspacePageTags.IMPORTED_PREVIEW_PREFIX + "B").assertIsNotEnabled()
        onAllNodesWithText("Process All").assertCountEquals(0)
        onAllNodesWithText("Clear All").assertCountEquals(0)
        onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import primary action visibly identifies deterministic fallback while context exposes Details only`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val current = importPart("ready.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT, analyzed = true)
        val pending = importPart("needs-repair.mid", rawMidi = true).copy(id = "B")
        val state = importState(current).copy(project = importState(current).project!!.copy(parts = listOf(current, pending)))
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithText("Next incomplete part · B").assertExists()
        onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.CleanMidi("B"), intents.removeLast())
        onNodeWithTag(WorkspacePageTags.IMPORT_CONTEXT).assertExists()
        onNodeWithText("Details").performClick()
        assertEquals(WorkspaceIntent.ShowPartDetails("B", PartDetailsFocusReturn.ImportPrimaryAction), intents.removeLast())
    }

    @Test
    fun `Import context keeps advanced processing out of the initial surface`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val midi = importPart("ready.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT)
        setContent { MelotrailTheme { WorkspaceScreen(importState(midi), onIntent = {}) } }

        onAllNodesWithTag(WorkspacePageTags.IMPORT_LOFI_MIDI_PROCESSOR).assertCountEquals(0)
        onAllNodesWithTag(WorkspacePageTags.IMPORT_LOFI_AUDIO_PROCESSOR).assertCountEquals(0)
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import audio and ready actions keep orchestration in the view model boundary`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("solo.wav", audio = true, inspected = false)), intents::add) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performScrollTo().performClick()
        assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.InspectSelectedPart), intents)
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `successful direct and audio imports expose the same Clean MIDI action`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        listOf(
            importPart("direct.mid", rawMidi = true),
            importPart("solo.wav", audio = true, rawMidi = true)
        ).forEach { part ->
            intents.clear()
            setContent { MelotrailTheme { WorkspaceScreen(importState(part), intents::add) } }
            onNodeWithText("Clean MIDI").assertExists()
            val action = onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performScrollTo()
            action.performClick()
            assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.CleanMidi("A")), intents)
            intents.clear()
            action.performKeyInput { pressKey(Key.Enter) }
            assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.CleanMidi("A")), intents)
        }
    }

    @Test
    fun `Clean MIDI details use one keyboard reachable action and raw cleaned terminology`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val part = importPart("current.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT)
        val intents = mutableListOf<WorkspaceIntent>()
        val state = importState(part).copy(
            selectedPartId = "A",
            dialog = WorkspaceDialog.PartDetails("A", PartDetailsFocusReturn.ImportedRow("A"))
        )
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithText("Preview raw MIDI").assertExists()
        onNodeWithText("Preview cleaned MIDI").assertExists()
        onAllNodesWithText("Repair MIDI").assertCountEquals(0)
        val clean = onNodeWithTag(WorkspaceTags.MIDI_QUALITY_CLEAN).performScrollTo()
        clean.performClick()
        assertEquals(WorkspaceIntent.CleanMidi("A"), intents.single())
        intents.clear()
        clean.performKeyInput { pressKey(Key.Enter) }
        assertEquals(WorkspaceIntent.CleanMidi("A"), intents.single())
    }

    @Test
    fun `Clean MIDI review keeps concise diff evidence beside explicit approval`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val tempo = listOf(app.melotrail.arrangement.MidiTempoChange(0, 120.0, true))
        val signature = listOf(app.melotrail.arrangement.MidiTimeSignature(0, 4, 4, true))
        fun artifact(hash: Char, notes: Int) = app.melotrail.arrangement.MidiQualityArtifact(
            hash.toString().repeat(64), notes, notes.toDouble(), app.melotrail.arrangement.MidiIntRange(60, 72),
            2, 480, 0.5, app.melotrail.arrangement.MidiVelocityDistribution(80, 90, 85.0, 85.0), tempo, signature
        )
        val report = app.melotrail.arrangement.MidiQualityReport(
            partId = "A", raw = artifact('a', 10), clean = artifact('b', 1),
            cleanup = app.melotrail.arrangement.MidiCleanupOptions(),
            timing = app.melotrail.arrangement.MidiTimingChangeSummary(1, 9, 0, 0, 0, 0, 0),
            tempoAndTimeSignaturesPreserved = true, approvalRequired = true
        )
        val base = importPart("review.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED)
        val part = base.copy(preparation = base.preparation.copy(
            cleanMidi = true,
            midiQuality = app.melotrail.application.MidiQualitySummary(
                app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED,
                report.cleanup,
                report = report
            )
        ))
        val intents = mutableListOf<WorkspaceIntent>()
        val state = importState(part).copy(selectedPartId = "A", dialog = WorkspaceDialog.PartDetails("A", PartDetailsFocusReturn.ImportedRow("A")))
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithText("Notes 10 to 1 · 10.000 to 1.000 notes/s · polyphony 2 to 2").assertExists()
        onNodeWithText("Timing: 0 starts, 0 ends changed; 9 removed, 0 added; max shift 0 ticks.").assertExists()
        onNodeWithText("Approve Clean MIDI").performScrollTo().performClick()
        assertEquals(WorkspaceIntent.ApproveCleanMidi, intents.single())
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import exposes workflow and MIDI guide references`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("ready.mid")), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_WORKFLOW_HELP).assertExists()
        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_HELP).assertExists()
        onNodeWithText("Workflow guide · docs/TRACK_PROCESS_WORKFLOW.md").assertExists()
        onNodeWithText("MIDI guide · docs/MIDI_IMPORT_PROCESS.md").assertExists()
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `remaining Import primary actions dispatch review feel structure and transcription intents`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val current = importPart("current.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT, analyzed = true)
        val unanalyzed = importPart("needs-analysis.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT)
        val cases: List<Pair<WorkspaceUiState, List<WorkspaceIntent>>> = listOf(
            importState(importPart("review.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED)) to listOf(WorkspaceIntent.ShowPartDetails("A", PartDetailsFocusReturn.ImportPrimaryAction)),
            importState(unanalyzed) to listOf(WorkspaceIntent.AnalyzePart("A")),
            importState(importPart("solo.wav", audio = true, inspected = true)) to listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.TranscribeSelectedPart),
            importState(current).copy(selectedPartId = "A", pendingMidiFeel = app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL) to listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.ApplyMidiFeelAndReanalyze),
            importState(current) to listOf(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE))
        )
        cases.forEach { (state, expected) ->
            intents.clear()
            setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }
            onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performScrollTo().performClick()
            assertEquals(expected, intents)
        }
    }

    @Ignore("Replaced by the sequential Melody Parts pipeline")
    @Test
    fun `Import offers per-track Lo-fi MIDI Feel with safe terminology and AI-base A B preview`() = runComposeUiTest {
        val base = importPart("approved.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT)
        val part = base.copy(preparation = base.preparation.copy(
            midiAiFix = app.melotrail.application.MidiAiFixSummary(app.melotrail.arrangement.MidiAiFixSelection.APPROVED, approvedAvailable = true),
            midiFeel = app.melotrail.application.MidiFeelSummary(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL, available = true)
        ))
        val intents = mutableListOf<WorkspaceIntent>()
        val state = importState(part).copy(selectedPartId = "A", runtimeReadiness = readyRuntime())
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_FEEL).performScrollTo().assertExists()
        onNodeWithText("Apply Lo-fi Feel").assertExists()
        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_FEEL_KEEP).performScrollTo().performClick()
        assertEquals(
            listOf(
                WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.CURRENT),
                WorkspaceIntent.ApplyMidiFeelAndReanalyze
            ),
            intents
        )
        intents.clear()
        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_FEEL_APPLY).performScrollTo().performClick()
        assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.SelectMidiFeel(app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL)), intents)
        intents.clear()
        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_FEEL_PREVIEW_BASE).performScrollTo().performClick()
        onNodeWithTag(WorkspacePageTags.IMPORT_MIDI_FEEL_PREVIEW_LOFI).performScrollTo().performClick()
        assertEquals<List<WorkspaceIntent>>(
            listOf(
                WorkspaceIntent.PreviewMidiPart("A", app.melotrail.application.PreviewMidiSource.AI_FIX_APPROVED),
                WorkspaceIntent.PreviewMidiPart("A", app.melotrail.application.PreviewMidiSource.LOFI_FEEL)
            ),
            intents
        )
    }

    @Test
    fun `Structure renders one focused root with canonical occurrence selection and keyboard reorder alternatives`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.STRUCTURE), intents::add) } }

        onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.STRUCTURE.name.lowercase()).assertCountEquals(1)
        listOf(
            WorkspacePageTags.STRUCTURE_STRIP, WorkspacePageTags.STRUCTURE_TABLE,
            WorkspacePageTags.STRUCTURE_CONTEXT, WorkspacePageTags.STRUCTURE_SUMMARY, WorkspacePageTags.STRUCTURE_PREVIEW,
            WorkspacePageTags.STRUCTURE_HELP
        ).forEach {
            onAllNodesWithTag(it).assertCountEquals(1)
        }
        onNodeWithTag(WorkspacePageTags.STRUCTURE_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.STRUCTURE_PALETTE).assertExists()
        onNodeWithTag(WorkspacePageTags.STRUCTURE_ADD_PREFIX + "A").performClick()
        waitForIdle()
        assertEquals(WorkspaceIntent.AddStructurePart("A"), intents.last())
        val earlier = onNodeWithTag(WorkspaceTags.STRUCTURE_MOVE_LEFT + "B1")
        earlier.performSemanticsAction(SemanticsActions.OnClick) { it.invoke() }
        waitForIdle()
        assertEquals(WorkspaceIntent.MoveStructureOccurrence("B1", earlier = true), intents.last())
        earlier.assertIsEnabled()
        onNodeWithTag(WorkspaceTags.STRUCTURE_OCCURRENCE_PREFIX + "B1").performClick()
        assertEquals(WorkspaceIntent.SelectStructureOccurrence("B1"), intents.last())
    }

    @Test
    fun `Structure empty state stays concise and exposes only eligible prepared-part actions`() = runComposeUiTest {
        val ready = populatedState().project!!.parts.first()
        val raw = ready.copy(id = "raw", analysis = null, preparation = ready.preparation.copy(
            analyzed = false, ready = false,
            midiQuality = app.melotrail.application.MidiQualitySummary(app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID)
        ))
        val state = populatedState().copy(project = populatedState().project!!.copy(parts = listOf(ready, raw), structure = emptyList()), workspaceSection = WorkspaceSection.STRUCTURE)
        setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.STRUCTURE_OPTIONS_TOGGLE).performClick()
        onNodeWithText("Choose a prepared part to start").assertExists()
        onAllNodesWithTag(WorkspacePageTags.STRUCTURE_ADD_PREFIX + "A").assertCountEquals(1)
        onAllNodesWithTag(WorkspacePageTags.STRUCTURE_ADD_PREFIX + "raw").assertCountEquals(0)
        onAllNodesWithText("Unknown").assertCountEquals(4)
    }

    @Test
    fun `Structure composes long repeated stale mutating and failed canonical states truthfully`() = runComposeUiTest {
        val base = populatedState()
        val project = checkNotNull(base.project)
        val longRole = "A deliberately long section role that must remain readable without creating page-level horizontal scrolling"
        val repeated = List(12) { index ->
            val partId = if (index % 2 == 0) "A" else "B"
            val occurrence = index / 2 + 1
            StructureSectionSummary(index, partId, occurrence, "$partId$occurrence", if (index == 4) null else 32.0)
        }
        val mixed = base.copy(
            project = project.copy(
                parts = project.parts.map { part ->
                    part.copy(
                        role = if (part.id == "A") longRole else "chorus",
                        analysis = part.analysis?.copy(key = if (part.id == "A") "A minor" else "C major")
                    )
                },
                structure = repeated
            ),
            workspaceSection = WorkspaceSection.STRUCTURE,
            selectedStructureOccurrenceId = "B4",
            structureDraft = repeated.map(StructureSectionSummary::partId),
            downstreamArtifactsStale = true,
            operation = WorkspaceOperation.Failed("save structure", "disk full"),
            retry = WorkspaceRetry.SaveStructure(project.root, repeated.map(StructureSectionSummary::partId), 7)
        )
        setContent { MelotrailTheme { WorkspaceScreen(mixed, onIntent = {}) } }

        repeated.forEach { section -> onAllNodesWithTag(WorkspacePageTags.STRUCTURE_ROW_PREFIX + section.instanceId).assertCountEquals(1) }
        onAllNodesWithTag(WorkspacePageTags.STRUCTURE_ROW_PREFIX + "B4").assertCountEquals(1)
        onNodeWithText("Mixed").assertExists()
        onAllNodesWithText("Unknown").assertCountEquals(5)
        onNodeWithText("Structure save failed. Use the global Retry action; the last saved structure remains selected.").assertExists()

        setContent { MelotrailTheme { WorkspaceScreen(mixed.copy(operation = WorkspaceOperation.SavingStructure), onIntent = {}) } }
        onNodeWithTag(WorkspacePageTags.STRUCTURE_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.STRUCTURE_ADD_PREFIX + "A").assertIsNotEnabled()
        onNodeWithText("Saving the canonical structure…").assertExists()
    }

    @Test
    fun `Arrange stays focused across blocked generating draft approval stale and failed states`() = runComposeUiTest {
        val ready = arrangeState()
        val states = listOf(
            WorkspaceUiState(workspaceSection = WorkspaceSection.ARRANGE),
            ready.copy(operation = WorkspaceOperation.GeneratingArrangement()),
            ready.copy(arrangement = arrangementSnapshot(approvalRequired = true, approved = false)),
            ready.copy(arrangement = arrangementSnapshot(approved = true)),
            ready.copy(arrangement = arrangementSnapshot(stale = true)),
            ready.copy(operation = WorkspaceOperation.Failed("generate arrangement", "Qwen response rejected"), retry = WorkspaceRetry.GenerateArrangement(app.melotrail.application.GenerateArrangementRequest(Path.of("build/task-086-project"))))
        )

        states.forEach { state ->
            setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }
            onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.ARRANGE.name.lowercase()).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.ARRANGE_PRIMARY_ACTION).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.ARRANGE_TABS).assertCountEquals(0)
            onAllNodesWithTag(WorkspacePageTags.ARRANGE_TIMELINE).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.ARRANGE_TRANSPORT).assertCountEquals(1)
            onAllNodesWithTag(WorkspaceTags.STRUCTURE_PANEL).assertCountEquals(0)
            onAllNodesWithTag(WorkspaceTags.TIMELINE_PANEL).assertCountEquals(0)
            onAllNodesWithTag(WorkspaceTags.AI_PLAN_PANEL).assertCountEquals(0)
        }
    }

    @Test
    fun `Arrange dispatches typed role and desired-character settings generate and approval intents`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(arrangeState(), intents::add) } }

        onNodeWithTag(WorkspacePageTags.ARRANGE_PRIMARY_ACTION).assertIsEnabled()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE + "-context").performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_PLANNER_PREFIX + "qwen").performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_ROLE_PREFIX + "bass").performScrollTo().performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_TRAIT_PREFIX + "muted").performScrollTo().performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_PRIMARY_ACTION).performScrollTo().performClick()

        assertEquals(
            listOf(
                WorkspaceIntent.UpdateArrangementPlanner(ArrangementPlannerKind.QWEN),
                WorkspaceIntent.ToggleArrangementRole(app.melotrail.arrangement.ArrangementRole.BASS),
                WorkspaceIntent.ToggleArrangementTrait(app.melotrail.arrangement.SoundTrait.MUTED),
                WorkspaceIntent.GenerateArrangement
            ),
            intents
        )

        intents.clear()
        setContent { MelotrailTheme { WorkspaceScreen(arrangeState().copy(arrangement = arrangementSnapshot(approvalRequired = true, approved = false)), intents::add) } }
        onNodeWithTag(WorkspacePageTags.ARRANGE_APPROVE).performScrollTo().performClick()
        assertEquals(WorkspaceIntent.ApproveArrangement, intents.single())
    }

    @Test
    fun `Arrange timeline uses canonical placements and tabs without mock controls`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val arrangement = arrangementSnapshot(approved = true, sections = listOf(
            arrangementSection(0, "A1", 18.0, "piano", "bass"),
            arrangementSection(1, "A2", 22.0, "piano", "pad"),
            arrangementSection(2, "B1", 28.0, "piano", "bass", "drums")
        ))
        setContent { MelotrailTheme { WorkspaceScreen(arrangeState().copy(arrangement = arrangement, selectedArrangementSection = 1), intents::add) } }

        listOf("piano", "bass", "drums", "pad").forEach { track -> onNodeWithTag(WorkspacePageTags.ARRANGE_TRACK_PREFIX + track).assertExists() }
        onAllNodesWithTag(WorkspacePageTags.ARRANGE_TRACK_PREFIX + "strings").assertCountEquals(0)
        onNodeWithTag(WorkspacePageTags.ARRANGE_SECTION_PREFIX + "1").performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_TAB_PREFIX + "transitions").performClick()

        assertEquals(
            listOf(WorkspaceIntent.SelectArrangementSection(1), WorkspaceIntent.SelectArrangeTab(ArrangeTab.TRANSITIONS)),
            intents
        )
        onAllNodesWithTag(WorkspacePageTags.ARRANGE_INTENSITY).assertCountEquals(0)
        onAllNodesWithText("AI Arrangement Suggestions").assertCountEquals(0)
        onAllNodesWithText("Undo").assertCountEquals(0)
    }

    @Test
    fun `Arrange planner choice remains keyboard reachable`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(arrangeState(), intents::add) } }

        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.ARRANGE_OPTIONS_TOGGLE + "-context").performClick()
        val qwen = onNodeWithTag(WorkspacePageTags.ARRANGE_PLANNER_PREFIX + "qwen")
        qwen.performClick()
        intents.clear()
        qwen.performKeyInput { pressKey(Key.Enter) }

        assertEquals(WorkspaceIntent.UpdateArrangementPlanner(ArrangementPlannerKind.QWEN), intents.single())
    }

    @Test
    fun `Arrange blocks its only generation action and exposes diagnostics when prerequisites are missing`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(workspaceSection = WorkspaceSection.ARRANGE), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.ARRANGE_PRIMARY_ACTION).assertIsNotEnabled()
        onNodeWithTag(WorkspaceShellTags.CONTEXT_TOGGLE).performClick()
        onAllNodesWithTag(WorkspacePageTags.ARRANGE_PREREQUISITE).assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.ARRANGE_DIAGNOSTICS_TOGGLE).performScrollTo().performClick()
        onAllNodesWithTag(WorkspacePageTags.ARRANGE_DIAGNOSTICS).assertCountEquals(1)
    }

    @Test
    fun `Arrange remains enabled when retained Cohesion is not ready`() = runComposeUiTest {
        val base = arrangeState()
        val project = checkNotNull(base.project)
        val draft = EnsembleCohesionSnapshot(
            root = project.root,
            planner = EnsembleCohesionPlannerKind.QWEN,
            inputHash = "0".repeat(64),
            structureSha256 = "0".repeat(64),
            boundaries = emptyList(),
            approvalRequired = true,
            approved = false,
            stale = false,
            artifact = project.root.resolve("cohesion.draft.json")
        )
        val reviewState = base.copy(
            project = project.copy(readiness = project.readiness.copy(cohesionReady = false, cohesionApprovalRequired = true)),
            cohesion = draft
        )
        setContent { MelotrailTheme { WorkspaceScreen(reviewState, onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.ARRANGE_PRIMARY_ACTION).assertIsEnabled()
        onNodeWithTag(WorkspacePageTags.ARRANGE_COHESION_ACTION).assertDoesNotExist()
    }

    @Test
    fun `Mix Master derives no one and all channel strips from rendered stems with truthful unavailable meters`() = runComposeUiTest {
        val noStems = WorkspaceUiState(workspaceSection = WorkspaceSection.MIX_MASTER)
        setContent { MelotrailTheme { WorkspaceScreen(noStems, onIntent = {}) } }
        onAllNodesWithTag(WorkspacePageTags.MIX_EMPTY_CHANNELS).assertCountEquals(1)
        listOf("piano", "bass", "drums", "pad", "strings").forEach { channel ->
            onAllNodesWithTag(WorkspacePageTags.MIX_CHANNEL_PREFIX + channel).assertCountEquals(0)
            onAllNodesWithTag(WorkspacePageTags.MIX_METER_PREFIX + channel).assertCountEquals(0)
        }

        val oneStem = mixMasterState().let { state -> state.copy(mix = state.mix!!.copy(availableStems = listOf("piano"))) }
        setContent { MelotrailTheme { WorkspaceScreen(oneStem, onIntent = {}) } }
        onAllNodesWithTag(WorkspacePageTags.MIX_CHANNEL_PREFIX + "piano").assertCountEquals(1)
        onAllNodesWithTag(WorkspacePageTags.MIX_METER_PREFIX + "piano").assertCountEquals(1)
        listOf("bass", "drums", "pad", "strings").forEach { channel -> onAllNodesWithTag(WorkspacePageTags.MIX_CHANNEL_PREFIX + channel).assertCountEquals(0) }

        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState(), onIntent = {}) } }
        onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.MIX_MASTER.name.lowercase()).assertCountEquals(1)
        listOf("piano", "bass", "drums", "pad", "strings").forEach { channel ->
            onAllNodesWithTag(WorkspacePageTags.MIX_CHANNEL_PREFIX + channel).assertCountEquals(1)
            onAllNodesWithTag(WorkspacePageTags.MIX_METER_PREFIX + channel).assertCountEquals(1)
        }
        onAllNodesWithText("0.0 dBFS · Level unavailable").assertCountEquals(5)
        onAllNodesWithTag(WorkspacePageTags.MIX_ZERO_SIGNAL).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.TIMELINE_PANEL).assertCountEquals(0)
        onAllNodesWithTag(WorkspaceTags.MIX_PANEL).assertCountEquals(0)
        onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.ARRANGE.name.lowercase()).assertCountEquals(0)
    }

    @Test
    fun `Mix Master dispatches existing settings listener volume and one Build Song action`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState(), intents::add) } }

        onNodeWithTag(WorkspacePageTags.MIX_MUTE_PREFIX + "piano").performClick()
        onNodeWithTag(WorkspacePageTags.MIX_SOLO_PREFIX + "piano").performClick()
        onNodeWithTag(WorkspacePageTags.MIX_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_LOFI).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_MP3).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_PLAYBACK_DRY).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_MASTER_VOLUME).assertIsEnabled()
        onNodeWithTag(WorkspacePageTags.MIX_PRIMARY_ACTION).performScrollTo().performClick()

        assertTrue(intents.any { it is WorkspaceIntent.UpdateMixSetting && it.instrument == "piano" && it.setting.muted })
        assertTrue(intents.any { it is WorkspaceIntent.UpdateMixSetting && it.instrument == "piano" && it.setting.solo })
        assertTrue(intents.any { it == WorkspaceIntent.UpdateBuildOptions(BuildOptionsDraft(loFi = true)) })
        assertTrue(intents.any { it == WorkspaceIntent.UpdateBuildOptions(BuildOptionsDraft(mp3 = true)) })
        assertTrue(intents.any { it == WorkspaceIntent.SelectPlaybackSource(PlaybackSource.DRY) })
        assertEquals(WorkspaceIntent.BuildSong, intents.last())
    }

    @Test
    fun `Mix Master channel and playback controls remain keyboard reachable`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState(), intents::add) } }

        val mute = onNodeWithTag(WorkspacePageTags.MIX_MUTE_PREFIX + "piano")
        mute.performClick(); intents.clear(); mute.performKeyInput { pressKey(Key.Enter) }
        assertTrue(intents.any { it is WorkspaceIntent.UpdateMixSetting && it.instrument == "piano" && it.setting.muted })

        onNodeWithTag(WorkspacePageTags.MIX_OPTIONS_TOGGLE).performClick()
        val master = onNodeWithTag(WorkspacePageTags.MIX_PLAYBACK_MASTER)
        master.performClick(); intents.clear(); master.performKeyInput { pressKey(Key.Enter) }
        assertTrue(intents.any { it == WorkspaceIntent.SelectPlaybackSource(PlaybackSource.MASTER) })

    }

    @Test
    fun `Mix Master disables stale playback omits unsupported DSP and exposes recovery reasons`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState().copy(downstreamArtifactsStale = true), onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.MIX_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_PLAYBACK_DRY).assertIsNotEnabled()
        onAllNodesWithTag(WorkspacePageTags.MIX_UNSUPPORTED_DSP).assertCountEquals(0)
        listOf("Equalizer", "Dynamics", "Sends", "Automation", "Reference Track", "LUFS", "True Peak", "Monitor Out").forEach { unsupported -> onAllNodesWithText(unsupported).assertCountEquals(0) }
        onNodeWithTag(WorkspacePageTags.MIX_PRIMARY_ACTION).assertIsNotEnabled()
        onNodeWithTag(WorkspacePageTags.MIX_MODE_MIX).performClick()
        onNodeWithTag(WorkspacePageTags.MIX_MODE_MASTER).performClick()
    }

    @Test
    fun `Video Preview stays focused across placeholder selected playing paused failed and unavailable states`() = runComposeUiTest {
        val selected = PlaybackSession(
            id = 7L,
            request = PlaybackRequest.Mix(Path.of("build/task-088-project"), PlaybackSource.DRY),
            sourceKind = PlaybackSourceKind.DRY_MIX,
            artifact = PlaybackArtifactIdentity(Path.of("build/task-088-project"), Path.of("build/task-088-project/mix/dry.wav")),
            phase = PlaybackSessionPhase.STOPPED,
            durationSeconds = 252.0
        )
        val unavailable = RuntimeReadiness.of(*RuntimeDependency.entries.map { dependency ->
            dependency to DependencyReadiness(if (dependency == RuntimeDependency.AUDIO_OUTPUT) DependencyStatus.UNAVAILABLE else DependencyStatus.READY, if (dependency == RuntimeDependency.AUDIO_OUTPUT) "Check the selected output device and retry." else "ready")
        }.toTypedArray())
        val states = listOf(
            WorkspaceUiState(workspaceSection = WorkspaceSection.VIDEO_PREVIEW) to "No local playback artifact selected. The visual remains a placeholder.",
            populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = selected, runtimeReadiness = readyRuntime()) to "A local audio artifact is selected; the visual remains a placeholder.",
            populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = selected.copy(phase = PlaybackSessionPhase.PLAYING), runtimeReadiness = readyRuntime()) to "Local audio playback is playing; the visual remains a placeholder.",
            populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = selected.copy(phase = PlaybackSessionPhase.PAUSED), runtimeReadiness = readyRuntime()) to "Local audio playback is paused; the visual remains a placeholder.",
            populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = selected.copy(phase = PlaybackSessionPhase.FAILED, failureMessage = "Renderer unavailable"), runtimeReadiness = readyRuntime()) to "Playback unavailable: Renderer unavailable",
            populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW, playbackSession = selected, runtimeReadiness = unavailable, project = populatedState().project!!.copy(name = "A deliberately long project title that remains concise in the local preview header")) to "Audio output unavailable: Check the selected output device and retry."
        )
        states.forEach { (state, expectedStatus) ->
            setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }
            onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.VIDEO_PREVIEW.name.lowercase()).assertCountEquals(1)
            listOf(WorkspacePageTags.VIDEO_PREVIEW_STAGE, WorkspacePageTags.VIDEO_PREVIEW_STATUS, WorkspaceTags.COMPACT_TRANSPORT).forEach {
                onAllNodesWithTag(it).assertCountEquals(1)
            }
            listOf("video-preview-camera", "video-preview-change-scene", "video-preview-fullscreen", "video-preview-play-pause", "video-preview-stop", "video-preview-seek", "video-preview-volume").forEach {
                onNodeWithTag(it).assertDoesNotExist()
            }
            onNodeWithText(expectedStatus).assertExists()
        }
    }

    @Test
    fun `Video Preview timeline keeps canonical occurrences selectable without changing shared playback`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val state = populatedState().copy(
            workspaceSection = WorkspaceSection.VIDEO_PREVIEW,
            runtimeReadiness = readyRuntime(),
            playbackSession = PlaybackSession(
                request = PlaybackRequest.Mix(Path.of("build/task-088-project"), PlaybackSource.DRY),
                sourceKind = PlaybackSourceKind.DRY_MIX,
                artifact = PlaybackArtifactIdentity(Path.of("build/task-088-project"), Path.of("build/task-088-project/mix/dry.wav")),
                phase = PlaybackSessionPhase.PLAYING,
                durationSeconds = 120.0
            )
        )
        setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }

        onNodeWithTag(WorkspacePageTags.VIDEO_PREVIEW_OPTIONS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.VIDEO_PREVIEW_OCCURRENCE_PREFIX + "B1").performClick()
        assertTrue(intents.isEmpty(), "timeline selection is UI-only")
        val playPause = onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE)
        playPause.performClick()
        onNodeWithText("Stop").performClick()

        assertEquals(listOf(WorkspaceIntent.PlayPause, WorkspaceIntent.StopPlayback), intents)
    }

    @Test
    fun `Video Preview shows duration unavailable and scrolls stable canonical occurrences`() = runComposeUiTest {
        val occurrences = (1..12).map { index ->
            StructureSectionSummary(index - 1, "A", index, "A$index", if (index == 7) null else index.toDouble())
        }
        val state = populatedState().copy(
            workspaceSection = WorkspaceSection.VIDEO_PREVIEW,
            project = populatedState().project!!.copy(structure = occurrences)
        )
        setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }

        onNodeWithTag(WorkspacePageTags.VIDEO_PREVIEW_OPTIONS_TOGGLE).performClick()
        onNodeWithText("12 occurrences · duration unavailable").assertExists()
        onNodeWithTag(WorkspacePageTags.VIDEO_PREVIEW_OCCURRENCE_PREFIX + "A7").assertExists()
        onNodeWithText("Duration unavailable").assertExists()
    }

    @Test
    fun `Settings destination contains real local controls readiness recovery and About information`() = runComposeUiTest {
        val unavailable = RuntimeReadiness.of(
            RuntimeDependency.WORKER to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.READY, "ready"),
            RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "Sound library registry ready"),
            RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Copy approved local samples.", RecoveryAction.INSTALL_SAMPLES),
            RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Set SFZ_RENDERER_PATH.", RecoveryAction.CONFIGURE_RENDERER),
            RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
        )
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(
            workspaceSection = WorkspaceSection.SETTINGS,
            soundLibrary = SoundLibrarySettingsState(resolvedRoot = Path.of("/validated/local/sounds"), source = "configured"),
            runtimeReadiness = unavailable
        ), intents::add) } }

        onNodeWithTag(WorkspacePageTags.SETTINGS_LIBRARY).assertExists()
        onNodeWithTag(WorkspacePageTags.SETTINGS_CHOOSE).assertIsEnabled()
        onNodeWithTag(WorkspacePageTags.SETTINGS_CLEAR).assertIsEnabled()
        onNodeWithTag(WorkspacePageTags.SETTINGS_DETAILS_TOGGLE).performClick()
        onNodeWithTag(WorkspacePageTags.SETTINGS_DETAILS_TOGGLE + "-about").performClick()
        onNodeWithTag(WorkspacePageTags.SETTINGS_RUNTIME_PREFIX + "samples").assertExists()
        onNodeWithTag(WorkspacePageTags.SETTINGS_ABOUT).assertExists()
        onNodeWithText("Recovery: Restore the missing catalog samples in the selected library.").assertExists()
        onNodeWithText("Recovery: Set SFZ_RENDERER_PATH to an absolute executable sfizz_render path, then refresh readiness.").assertExists()
        onNodeWithTag(WorkspacePageTags.SETTINGS_BACK).performClick()
        assertEquals(WorkspaceIntent.BackFromSettings, intents.last())
        listOf("Telemetry", "Check for updates", "Cloud sync", "Theme", "Audio device").forEach { text ->
            onAllNodesWithText(text, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun `Settings wide fixture captures and overlays the approved reference while documenting omitted controls`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val partial = RuntimeReadiness.of(
            RuntimeDependency.WORKER to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Start the Python worker with make worker.", RecoveryAction.START_WORKER),
            RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Transcription needs the running Python worker.", RecoveryAction.START_WORKER),
            RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "Sound library registry ready"),
            RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Copy the approved local starter samples.", RecoveryAction.INSTALL_SAMPLES),
            RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Set SFZ_RENDERER_PATH to an executable sfizz_render.", RecoveryAction.CONFIGURE_RENDERER),
            RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.UNAVAILABLE, "No audio output device is available.", RecoveryAction.CHECK_AUDIO_OUTPUT)
        )
        val longRoot = Path.of("/validated/local/sounds/with/a/deliberately/long/path/that/remains/readable/without/creating/another/instrument/tree")
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(
            workspaceSection = WorkspaceSection.SETTINGS,
            soundLibrary = SoundLibrarySettingsState(resolvedRoot = longRoot, source = "configured", restartRequired = true),
            runtimeReadiness = partial
        ), onIntent = {}) } }

        val image = onRoot().captureToImage().toAwtImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        writeTask100SettingsCapture(image)
        writeTask100SettingsReferenceOverlay(image)
    }

    @Test
    fun `Task 101 records a complete 1536 by 1024 window for every destination`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val library = LocalSoundLibraryInventory(
            LocalSoundLibraryInventoryState.READY,
            listOf(libraryInstrument("piano", "Piano"))
        )
        val fixtures = mapOf(
            WorkspaceSection.SETUP to setupState(),
            WorkspaceSection.HARMONY to populatedState().copy(workspaceSection = WorkspaceSection.HARMONY, harmony = HarmonyEditorUiState(view = harmonyView(ChordProgression(SectionTypeId.VERSE), ChordProgression(SectionTypeId.CHORUS), ChordProgression(SectionTypeId.BRIDGE)))),
            WorkspaceSection.OVERVIEW to overviewReadyState(),
            WorkspaceSection.IMPORT to importState(importPart("piano_loop.mid", rawMidi = true)),
            WorkspaceSection.STRUCTURE to populatedState().copy(workspaceSection = WorkspaceSection.STRUCTURE),
            WorkspaceSection.ARRANGE to arrangeState(),
            WorkspaceSection.MIX_MASTER to mixMasterState(),
            WorkspaceSection.LIBRARY to populatedState().copy(
                workspaceSection = WorkspaceSection.LIBRARY,
                libraryBrowser = LibraryBrowserState(inventory = library, selectedId = "piano")
            ),
            WorkspaceSection.VIDEO_PREVIEW to populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW),
            WorkspaceSection.EXPORT to exportState(),
            WorkspaceSection.SETTINGS to populatedState().copy(workspaceSection = WorkspaceSection.SETTINGS)
        )

        WorkspaceSection.entries.forEach { destination ->
            setContent { MelotrailTheme { WorkspaceScreen(checkNotNull(fixtures[destination]), onIntent = {}) } }
            val image = onRoot().captureToImage().toAwtImage()
            assertEquals(1536, image.width, destination.label)
            assertEquals(1024, image.height, destination.label)
            writeTask101Capture(destination.name.lowercase(), image)
        }
    }

    @Test
    fun `deterministic full-window overview fixture overlays the task reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val fixtureArrangement = arrangementSnapshot(approved = true, sections = listOf(
            arrangementSection(0, "A1", 32.0, "piano", "bass"),
            arrangementSection(1, "A2", 32.0, "piano", "pad"),
            arrangementSection(2, "B1", 32.0, "piano", "bass", "drums"),
            arrangementSection(3, "B2", 32.0, "piano", "drums", "strings"),
            arrangementSection(4, "A3", 32.0, "piano", "bass", "pad")
        ))
        setContent { MelotrailTheme { WorkspaceScreen(overviewReadyState(fixtureArrangement), onIntent = {}) } }

        val image = onNodeWithTag(WorkspaceShellTags.ROOT).captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
        writeTask093OverviewCapture(image.toAwtImage())
        writeTask093OverviewReferenceOverlay(image.toAwtImage())
        // The Task 092 shell owns the outer edges. This verifies the shared Overview
        // preview edge within 4 px while the overlay is retained for visual review.
        val preview = onNodeWithTag(WorkspacePageTags.OVERVIEW_PREVIEW).getUnclippedBoundsInRoot()
        assertTrue(abs((preview.right - preview.left).value - MusicWorkspaceTokens.Pages.OverviewPreviewWidth.value) <= 4f, "preview width: ${preview.right - preview.left}")
    }

    @Test
    fun `deterministic Import fixture captures and overlays the full reference shell`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("piano_loop.mid", rawMidi = true)), onIntent = {}) } }

        val image = onRoot().captureToImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        val drop = onNodeWithTag(WorkspacePageTags.IMPORT_DROP_SURFACE).getUnclippedBoundsInRoot()
        assertTrue((drop.bottom - drop.top).value >= MusicWorkspaceTokens.Pages.ImportDropHeight.value)
        val table = onNodeWithTag(WorkspacePageTags.IMPORTED_FILES).getUnclippedBoundsInRoot()
        val context = onNodeWithTag(WorkspacePageTags.IMPORT_CONTEXT).getUnclippedBoundsInRoot()
        assertTrue(table.top.value > drop.bottom.value, "table should follow the shared drop surface")
        assertTrue(context.left.value > table.right.value, "context rail should remain outside the import page")
        writeTask094ImportCapture(image.toAwtImage())
        writeImportReferenceOverlay(image.toAwtImage())
    }

    @Test
    fun `deterministic Structure fixture captures and overlays the full task reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        setContent { MelotrailTheme { WorkspaceScreen(populatedState().copy(workspaceSection = WorkspaceSection.STRUCTURE), onIntent = {}) } }

        val image = onRoot().captureToImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        val page = onNodeWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.STRUCTURE.name.lowercase()).getUnclippedBoundsInRoot()
        val table = onNodeWithTag(WorkspacePageTags.STRUCTURE_TABLE).getUnclippedBoundsInRoot()
        val rail = onNodeWithTag(WorkspacePageTags.STRUCTURE_CONTEXT).getUnclippedBoundsInRoot()
        assertTrue(table.right.value <= rail.left.value, "wide Structure table and context rail must not require page-level horizontal scrolling")
        assertTrue(page.right.value <= 1536f && page.left.value >= 0f)
        writePageCapture("structure", image.toAwtImage())
        writeStructureReferenceOverlay(image.toAwtImage())
    }

    @Test
    fun `deterministic Arrange fixture captures and overlays the full task reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        val fixtureArrangement = arrangementSnapshot(approved = true, sections = listOf(
            arrangementSection(0, "A1", 20.0, "piano", "bass"),
            arrangementSection(1, "A2", 28.0, "piano", "pad"),
            arrangementSection(2, "B1", 32.0, "piano", "bass", "drums"),
            arrangementSection(3, "C1", 24.0, "piano", "pad", "strings"),
            arrangementSection(4, "B2", 32.0, "piano", "bass", "drums")
        ))
        setContent { MelotrailTheme { WorkspaceScreen(arrangeState().copy(arrangement = fixtureArrangement), onIntent = {}) } }

        val image = onRoot().captureToImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        val timeline = onNodeWithTag(WorkspacePageTags.ARRANGE_TIMELINE).getUnclippedBoundsInRoot()
        val rail = onNodeWithTag(WorkspacePageTags.ARRANGE_CONTEXT).getUnclippedBoundsInRoot()
        assertTrue(timeline.right.value <= rail.left.value, "wide Arrange timeline and context rail must remain separate")
        // The reference's waveform clips, preview art, AI suggestion, undo history, and
        // unsupported tracks are intentionally absent: this fixture uses only canonical
        // structure/arrangement evidence and reports unavailable rendered playback.
        writeTask096ArrangeCapture(image.toAwtImage())
        writeTask096ArrangeReferenceOverlay(image.toAwtImage())
    }

    @Test
    fun `deterministic Mix Master fixture captures and overlays the full task reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        setContent { MelotrailTheme { WorkspaceScreen(mixMasterState(), onIntent = {}) } }

        val image = onRoot().captureToImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        onNodeWithTag(WorkspacePageTags.MIXER_VIEWPORT).assertExists()
        // The reference's Lead/FX/Vocal/Master mock tracks, group buses, reference import,
        // EQ/dynamics/sends/automation, loudness figures, and monitor routing are intentionally
        // absent: this fixture renders only canonical stems and unavailable measured levels.
        writeTask097MixMasterCapture(image.toAwtImage())
        writeTask097MixMasterReferenceOverlay(image.toAwtImage())
    }

    @Test
    fun `Task 099 Video Preview fixture captures and overlays the full reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        // Capability-driven differences from the reference are intentional: no bundled
        // scene artwork, aspect/scene/camera controls, video clock, or video export.
        // The page shows a local placeholder, canonical occurrences, and shared audio.
        val state = populatedState().copy(workspaceSection = WorkspaceSection.VIDEO_PREVIEW)
        setContent { MelotrailTheme { WorkspaceScreen(state, onIntent = {}) } }

        val image = onRoot().captureToImage().toAwtImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        writeTask099ReferenceOverlay(image, "08-video-preview.png", "video-preview")
    }

    @Test
    fun `Task 099 Export fixture captures and overlays the full reference`() = runSkikoComposeUiTest(size = Size(1536f, 1024f)) {
        // Capability-driven differences from the reference are intentional: audio-only
        // WAV plus optional validated MP3, with no video, FLAC, DSP, metadata, stems,
        // file-size/time estimates, or cloud destination controls.
        setContent { MelotrailTheme { WorkspaceScreen(exportState(), onIntent = {}) } }

        val image = onRoot().captureToImage().toAwtImage()
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
        writeTask099ReferenceOverlay(image, "09-export.png", "export")
    }

    private fun libraryInstrument(id: String, name: String) = LocalSoundLibraryInstrument(
        id = id,
        name = name,
        category = id.replaceFirstChar(Char::uppercase),
        sampleCount = if (id == "drums") 5 else 1,
        licenseName = "Fixture Library",
        license = "fixture-license",
        source = "local-fixture",
        commercialUse = true,
        attributionRequired = false
    )

    private fun setupState(): WorkspaceUiState {
        val profile = app.melotrail.profile.CompositionProfileRef("lofi", 1)
        val mood = app.melotrail.profile.MoodRef("warm", 1)
        val options = app.melotrail.application.CompositionSettingsOptions(
            tonics = listOf(app.melotrail.music.TonicOption(app.melotrail.music.PitchClass.canonical(0))),
            modes = listOf(app.melotrail.music.ScaleModeOption(app.melotrail.music.ScaleModeId.MAJOR)),
            commonMeters = listOf(app.melotrail.music.TimeSignatureOption(app.melotrail.music.TimeSignature(4, 4)), app.melotrail.music.TimeSignatureOption(app.melotrail.music.TimeSignature(7, 8))),
            profiles = listOf(app.melotrail.profile.CompositionProfileSummary(profile, "Lo-fi", "Warm, restrained texture", mood)),
            moods = listOf(app.melotrail.profile.MoodSummary(mood, "Warm", "Soft and familiar")),
            profileMeters = listOf(app.melotrail.music.TimeSignature(4, 4))
        )
        val saved = app.melotrail.application.CompositionSettingsView(
            "Midnight Train", app.melotrail.music.MusicalKey(app.melotrail.music.PitchClass.canonical(0), app.melotrail.music.ScaleModeId.MAJOR), app.melotrail.music.Tempo(80.0), app.melotrail.music.TimeSignature(4, 4), profile, mood, 1, "0".repeat(64), "1".repeat(64)
        )
        val setup = ProjectSetupUiState.from(app.melotrail.application.GetCompositionSettingsResult(saved, options, false), "Midnight Train").copy(
            draft = ProjectSetupDraft("Midnight Train", app.melotrail.music.PitchClass.canonical(0), app.melotrail.music.ScaleModeId.MAJOR, "80", app.melotrail.music.TimeSignature(7, 8), profile, mood),
            invalidationPreview = app.melotrail.application.SettingsInvalidationPreview(emptySet(), setOf(app.melotrail.arrangement.WorkflowArtifact.ANALYSIS), setOf(app.melotrail.application.WorkflowStage.ANALYSIS))
        )
        val base = populatedState()
        return base.copy(
            project = base.project,
            projectSetup = setup,
            workspaceSection = WorkspaceSection.SETUP
        )
    }

    private fun harmonyView(vararg progressions: ChordProgression) = HarmonyView(
        projectRevision = 1,
        revision = 1,
        progressions = progressions.toList(),
        validationErrors = emptyList(),
        completeness = HarmonyCompleteness(
            listOf(SectionTypeId.VERSE, SectionTypeId.CHORUS, SectionTypeId.BRIDGE),
            emptyList(),
            progressions.filter { it.events.isEmpty() }.map { it.sectionType }
        ),
        key = app.melotrail.music.MusicalKey(PitchClass.canonical(0), app.melotrail.music.ScaleModeId.MAJOR),
        templateOptions = app.melotrail.harmony.HarmonyTemplateCatalog.options(
            app.melotrail.music.MusicalKey(PitchClass.canonical(0), app.melotrail.music.ScaleModeId.MAJOR)
        )
    )

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
                stemsAvailable = false, dryMixAvailable = false, loFiMixAvailable = false, masterAvailable = false,
                cohesionReady = true
            )
        ),
        selectedArrangementSection = 0,
        structureDraft = listOf("A", "B")
    )

    private fun overviewReadyState(arrangement: ArrangementSnapshot = arrangementSnapshot(approved = true, sections = listOf(
        arrangementSection(0, "A1", 32.0, "piano", "bass"),
        arrangementSection(1, "B1", 32.0, "piano", "drums", "pad")
    ))): WorkspaceUiState {
        val base = populatedState()
        val project = base.project!!
        return base.copy(
            project = project.copy(readiness = project.readiness.copy(
                songPlanAvailable = true,
                arrangementAvailable = true,
                generatedMidiAvailable = true,
                stemsAvailable = true,
                dryMixAvailable = true,
                loFiMixAvailable = true,
                masterAvailable = true,
                releaseAvailable = true
            )),
            arrangement = arrangement
        )
    }

    private fun arrangementSection(index: Int, instanceId: String, durationSeconds: Double, vararg instruments: String) = ArrangementSectionSnapshot(
        index = index,
        instanceId = instanceId,
        partId = instanceId.take(1),
        purpose = "section",
        energy = 0.5,
        instruments = instruments.map { ArrangementInstrumentSnapshot(it, "active", null, null) },
        transition = "none",
        durationSeconds = durationSeconds
    )

    private fun arrangeState(): WorkspaceUiState = populatedState().copy(workspaceSection = WorkspaceSection.ARRANGE)

    private fun mixMasterState(): WorkspaceUiState = populatedState().copy(
        workspaceSection = WorkspaceSection.MIX_MASTER,
        arrangement = arrangementSnapshot(approved = true),
        mix = MixSnapshot(
            root = Path.of("build/task-087-project"),
            settings = PersistedMixSettings(tracks = PersistedMixSettings.defaults() + ("piano" to LogicalMixSetting(gainDb = -2.0))),
            availableStems = listOf("piano", "bass", "drums", "pad", "strings"),
            dryMix = Path.of("build/task-087-project/mix/dry.wav"),
            stale = false
        ),
        runtimeReadiness = readyRuntime(),
        project = populatedState().project!!.copy(readiness = populatedState().project!!.readiness.copy(
            stemsAvailable = true, dryMixAvailable = true, loFiMixAvailable = true, masterAvailable = true
        ))
    )

    private fun exportState(): WorkspaceUiState = populatedState().copy(
        workspaceSection = WorkspaceSection.EXPORT,
        export = ExportUiState(
            inspection = ReleaseExportInspection(
                ReleaseExportSummary(Path.of("build/task-089-project/output/master.wav"), 252.0, 48_000, 2, 24, 6),
                setOf(ReleaseExportFormat.WAV)
            ),
            draft = ExportDraft(ReleaseExportFormat.WAV, "Midnight Train.wav", Path.of("build/task-083-project/output"))
        )
    )

    private fun readyRuntime() = RuntimeReadiness.of(*RuntimeDependency.entries.map { dependency ->
        dependency to DependencyReadiness(DependencyStatus.READY, "ready")
    }.toTypedArray())

    private fun arrangementSnapshot(
        approvalRequired: Boolean = false,
        approved: Boolean = false,
        stale: Boolean = false,
        sections: List<ArrangementSectionSnapshot> = emptyList()
    ) = ArrangementSnapshot(
        root = Path.of("build/task-086-project"),
        sections = sections,
        approvalRequired = approvalRequired,
        approved = approved,
        stale = stale,
        artifact = Path.of("build/task-086-project/arrangement.json")
    )

    private fun readyPreparation() = PartPreparationSummary(
        sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true,
        cleanMidi = true, analyzed = true, ready = true, warnings = emptyList(),
        midiQuality = app.melotrail.application.MidiQualitySummary(app.melotrail.application.MidiQualityStatus.CURRENT)
    )

    private fun importState(part: PartSummary) = WorkspaceUiState(
        project = ProjectSnapshot(
            root = Path.of("build/task-084-project"), version = 3, name = "Import project", renderFormat = RenderFormat(44_100, 2),
            parts = listOf(part), structure = emptyList(),
            readiness = ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
        ),
        workspaceSection = WorkspaceSection.IMPORT
    )

    private fun importPart(
        sourceName: String,
        audio: Boolean = false,
        inspected: Boolean = true,
        rawMidi: Boolean = false,
        analyzed: Boolean = false,
        quality: app.melotrail.application.MidiQualityStatus = app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID
    ) = PartSummary(
        id = "A", role = "", sourceFile = "source/$sourceName", sourceName = sourceName,
        sourceType = if (audio) PartSourceType.AUDIO else PartSourceType.MIDI,
        analysis = if (analyzed) PartAnalysisSummary(PartAnalysisStatus.MIDI, "analysis/A.json", bars = 8) else null,
        preparation = PartPreparationSummary(
            sourcePreserved = true, inspected = inspected, preparedAudio = false, rawMidi = rawMidi,
            cleanMidi = quality == app.melotrail.application.MidiQualityStatus.CURRENT,
            analyzed = analyzed, ready = analyzed, warnings = emptyList(),
            midiQuality = app.melotrail.application.MidiQualitySummary(quality)
        ),
        sourceSizeBytes = 2_048L
    )

    private fun writeImportReferenceOverlay(importCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/02-import.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/02-import.png").toFile())
        val overlay = BufferedImage(importCapture.width, importCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(importCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-094-import-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask094ImportCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/02-import.png")) }
            ?: error("Could not locate the Task 094 Import reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-094-import-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask096ArrangeCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/04-arrange.png")) }
            ?: error("Could not locate the Task 096 Arrange reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-096-arrange-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask096ArrangeReferenceOverlay(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/04-arrange.png")) }
            ?: error("Could not locate the Task 096 Arrange reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/04-arrange.png").toFile())
        val overlay = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(capture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-096-arrange-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask098LibraryCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/07-library.png")) }
            ?: error("Could not locate the Task 098 Library reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-098-library-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask098LibraryReferenceOverlay(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/07-library.png")) }
            ?: error("Could not locate the Task 098 Library reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/07-library.png").toFile())
        val overlay = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(capture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-098-library-overlay.png")
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask098LibraryUnconfiguredCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/07-library.png")) }
            ?: error("Could not locate the Task 098 Library reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-098-library-unconfigured.png")
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun SemanticsNodeInteractionsProvider.assertFitsNarrowViewport(tag: String) {
        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(bounds.left.value >= 0f, "$tag starts outside the viewport: $bounds")
        assertTrue(bounds.right.value <= 720f, "$tag extends outside the viewport: $bounds")
    }

    private fun SemanticsNodeInteractionsProvider.assertOnlyLibraryPageRoot() {
        WorkspaceSection.entries.forEach { destination ->
            onAllNodesWithTag(WorkspacePageTags.ROOT_PREFIX + destination.name.lowercase())
                .assertCountEquals(if (destination == WorkspaceSection.LIBRARY) 1 else 0)
        }
    }

    private fun writePageCapture(page: String, capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-090-$page-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask101Capture(destination: String, capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/01-dashboard-overview.png")) }
            ?: error("Could not locate the Task 101 UI references.")
        val target = repository.resolve("desktopApp/build/reports/task-101-$destination-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask093OverviewCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/01-dashboard-overview.png")) }
            ?: error("Could not locate the Task 093 Overview reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-093-overview-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeTask093OverviewReferenceOverlay(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/01-dashboard-overview.png")) }
            ?: error("Could not locate the Task 093 Overview reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/01-dashboard-overview.png").toFile())
        val overlay = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(capture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-093-overview-overlay.png")
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeShellCapture(layout: String, capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-092-$layout-shell.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    private fun writeStructureReferenceOverlay(structureCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/03-structure.png")) }
            ?: error("Could not locate the Task 095 Structure reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/03-structure.png").toFile())
        val overlay = BufferedImage(structureCapture.width, structureCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(structureCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-095-structure-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask099ReferenceOverlay(capture: BufferedImage, referenceName: String, pageName: String) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/$referenceName")) }
            ?: error("Could not locate the Task 099 $referenceName reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/$referenceName").toFile())
        val overlay = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(capture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-099-$pageName-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask100SettingsCapture(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/10-settings.png")) }
            ?: error("Could not locate the Task 100 Settings reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-100-settings-capture.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(capture, "png", target.toFile()))
    }

    /** The reference includes unsupported account/update/privacy controls; this overlay records their deliberate omission. */
    private fun writeTask100SettingsReferenceOverlay(capture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/10-settings.png")) }
            ?: error("Could not locate the Task 100 Settings reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/10-settings.png").toFile())
        val overlay = BufferedImage(capture.width, capture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(capture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-100-settings-overlay.png")
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeExportReferenceOverlay(exportCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/App-pages.png").toFile())
        val exportRegion = reference.getSubimage(1142, 776, 384, 241)
        val overlay = BufferedImage(exportCapture.width, exportCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(exportRegion, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(exportCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-089-export-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeArrangeReferenceOverlay(arrangeCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/App-pages.png").toFile())
        val arrangeRegion = reference.getSubimage(777, 483, 356, 284)
        val overlay = BufferedImage(arrangeCapture.width, arrangeCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(arrangeRegion, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(arrangeCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-086-arrange-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeTask097MixMasterCapture(mixMasterCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/06-mix-master.png")) }
            ?: error("Could not locate the Task 097 reference image.")
        val target = repository.resolve("desktopApp/build/reports/task-097-mix-master.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(mixMasterCapture, "png", target.toFile()))
    }

    private fun writeTask097MixMasterReferenceOverlay(mixMasterCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/UI/06-mix-master.png")) }
            ?: error("Could not locate the Task 097 reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/UI/06-mix-master.png").toFile())
        val overlay = BufferedImage(mixMasterCapture.width, mixMasterCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(reference, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(mixMasterCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-097-mix-master-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }

    private fun writeVideoPreviewReferenceOverlay(videoCapture: BufferedImage) {
        val repository = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("docs/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val reference = ImageIO.read(repository.resolve("docs/pictures/App-pages.png").toFile())
        val videoRegion = reference.getSubimage(12, 777, 379, 247)
        val overlay = BufferedImage(videoCapture.width, videoCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(videoRegion, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(videoCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-088-video-preview-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }
}
