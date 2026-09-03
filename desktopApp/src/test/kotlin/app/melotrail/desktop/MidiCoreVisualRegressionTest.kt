package app.melotrail.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compact blocked-state fixtures complement the wide ready-state workflow fixtures. */
@OptIn(ExperimentalTestApi::class)
class MidiCoreVisualRegressionTest {
    @Test
    fun `compact shell captures every target destination without clipping the workspace`() =
        runSkikoComposeUiTest(size = Size(720f, 900f)) {
            var destination by mutableStateOf(MidiCoreWorkspaceDestination.PROJECT)
            setContent {
                MelotrailTheme {
                    MidiCoreWorkspaceShell(
                        state = MidiCoreWorkspaceState(),
                        initialDestination = destination,
                    )
                }
            }

            val root = visualFixtureRoot()
            Files.createDirectories(root)
            midiCoreWorkspaceDestinations.forEach { next ->
                destination = next
                waitForIdle()
                onNodeWithTag(MidiCoreWorkspaceShellTags.COMPACT_LAYOUT).assertExists()
                onNodeWithTag(pageTag(next)).assertExists()
                onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertIsDisplayed()
                val image = onRoot().captureToImage().toAwtImage()
                assertEquals(720, image.width)
                assertEquals(900, image.height)
                assertTrue(ImageIO.write(image, "png", root.resolve("${next.route}.png").toFile()))
            }

            assertEquals(
                midiCoreWorkspaceDestinations.map(MidiCoreWorkspaceDestination::route).sorted(),
                Files.list(root).use { paths -> paths.map { it.fileName.toString().removeSuffix(".png") }.sorted().toList() },
            )
        }

    private fun pageTag(destination: MidiCoreWorkspaceDestination): String = when (destination) {
        MidiCoreWorkspaceDestination.PROJECT -> MidiCoreWorkspaceShellTags.PAGE + "-project"
        MidiCoreWorkspaceDestination.MIDI -> MidiCoreMidiPageTags.ROOT
        MidiCoreWorkspaceDestination.STRUCTURE_HARMONY -> MidiCoreStructureHarmonyPageTags.ROOT
        MidiCoreWorkspaceDestination.ARRANGE -> MidiCoreArrangePageTags.ROOT
        MidiCoreWorkspaceDestination.REVIEW -> MidiCoreReviewPageTags.ROOT
        MidiCoreWorkspaceDestination.EXPORT -> MidiCoreExportPageTags.ROOT
    }

    private fun visualFixtureRoot(): Path = Path.of(System.getProperty("user.dir"))
        .toAbsolutePath()
        .resolve("build/test-results/midi-core-focused-workflow/compact")
}
