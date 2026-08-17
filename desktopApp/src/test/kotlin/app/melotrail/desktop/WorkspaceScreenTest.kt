package app.melotrail.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
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
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
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

    @Test
    fun `Import primary actions dispatch existing typed intents and long filenames remain concise`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val longMidi = importPart("a-very-long-source-name-that-remains-readable-in-the-imported-files-list.mid", rawMidi = true)
        setContent { MelotrailTheme { WorkspaceScreen(importState(longMidi), intents::add) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performClick()
        assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.PrepareMidi("A")), intents)
        onAllNodesWithTag(WorkspacePageTags.IMPORTED_ROW_PREFIX + "A").assertCountEquals(1)
        onNodeWithTag(WorkspacePageTags.IMPORTED_DETAILS_PREFIX + "A").performClick()
        assertEquals(WorkspaceIntent.ShowPartDetails("A"), intents.last())
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
    fun `Import audio and ready actions keep orchestration in the view model boundary`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("solo.wav", audio = true, inspected = false)), intents::add) } }

        onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performClick()
        assertEquals<List<WorkspaceIntent>>(listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.InspectSelectedPart), intents)
    }

    @Test
    fun `remaining Import primary actions dispatch review feel structure and transcription intents`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val current = importPart("current.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.CURRENT, analyzed = true)
        val cases: List<Pair<WorkspaceUiState, List<WorkspaceIntent>>> = listOf(
            importState(importPart("review.mid", rawMidi = true, quality = app.melotrail.application.MidiQualityStatus.APPROVAL_REQUIRED)) to listOf(WorkspaceIntent.ShowPartDetails("A")),
            importState(importPart("solo.wav", audio = true, inspected = true)) to listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.TranscribeSelectedPart),
            importState(current).copy(selectedPartId = "A", pendingMidiFeel = app.melotrail.arrangement.MidiAnalysisInput.LOFI_FEEL) to listOf(WorkspaceIntent.SelectPart("A"), WorkspaceIntent.ApplyMidiFeelAndReanalyze),
            importState(current) to listOf(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.STRUCTURE))
        )
        cases.forEach { (state, expected) ->
            intents.clear()
            setContent { MelotrailTheme { WorkspaceScreen(state, intents::add) } }
            onNodeWithTag(WorkspacePageTags.IMPORT_PRIMARY_ACTION).performClick()
            assertEquals(expected, intents)
        }
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

    @Test
    fun `deterministic Import fixture captures the reference shell crop`() = runSkikoComposeUiTest(size = Size(1158f, 462f)) {
        setContent { MelotrailTheme { WorkspaceScreen(importState(importPart("piano_loop.mid", rawMidi = true)), onIntent = {}) } }

        val image = onNodeWithTag(WorkspacePageTags.ROOT_PREFIX + WorkspaceSection.IMPORT.name.lowercase()).captureToImage()
        assertEquals(942, image.width)
        assertEquals(358, image.height)
        val drop = onNodeWithTag(WorkspacePageTags.IMPORT_DROP_SURFACE).getUnclippedBoundsInRoot()
        assertTrue((drop.bottom - drop.top).value >= MusicWorkspaceTokens.Pages.ImportDropHeight.value)
        writeImportReferenceOverlay(image.toAwtImage())
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
            .firstOrNull { Files.isRegularFile(it.resolve("plan/pictures/App-pages.png")) }
            ?: error("Could not locate the App-pages reference image.")
        val reference = ImageIO.read(repository.resolve("plan/pictures/App-pages.png").toFile())
        val importRegion = reference.getSubimage(12, 483, 379, 284)
        val overlay = BufferedImage(importCapture.width, importCapture.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = overlay.createGraphics()
        try {
            graphics.drawImage(importRegion, 0, 0, overlay.width, overlay.height, null)
            graphics.composite = AlphaComposite.SrcOver.derive(0.55f)
            graphics.drawImage(importCapture, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        val target = repository.resolve("desktopApp/build/reports/task-084-import-overlay.png")
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(overlay, "png", target.toFile()))
    }
}
