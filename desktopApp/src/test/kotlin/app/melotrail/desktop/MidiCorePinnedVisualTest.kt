package app.melotrail.desktop

import androidx.compose.ui.geometry.Rect
import app.melotrail.midi.domain.MidiExportRole
import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Full captures use the production shell/pages. Geometry and palette do not read theme tokens. */
class MidiCorePinnedVisualTest {
    @TestFactory
    fun `six destinations retain pinned pixels geometry and transport at all three sizes`() =
        MidiCoreVisualCapture.viewports.flatMap { viewport ->
            midiCoreWorkspaceDestinations.flatMap { destination ->
                listOf(false, true).map { populated ->
                    val name = "${viewport.width}-${destination.route}-${if (populated) "populated" else "empty"}"
                    dynamicTest(name) {
                        val capture = MidiCoreVisualCapture.shell(viewport, destination, populated)
                        VisualImageComparator.assertMatches(capture.image, "/visual/shell/$name.png",
                            Path.of("build/test-results/visual-shell/$name"))
                        assertGeometry(capture, viewport, destination, populated)
                    }
                }
            }
        }

    @TestFactory
    fun `ready destinations and completed decisions retain their own pinned coverage`() =
        MidiCoreVisualCapture.viewports.flatMap { viewport ->
            val pages = midiCoreWorkspaceDestinations.map { it to false }
            val decisions = listOf(MidiCoreWorkspaceDestination.REVIEW to true, MidiCoreWorkspaceDestination.EXPORT to true)
            (pages + decisions).map { (destination, detail) ->
                val name = "${viewport.width}-${destination.route}-ready${if (detail) "-result" else ""}"
                dynamicTest(name) {
                    val target = when (destination) {
                        MidiCoreWorkspaceDestination.REVIEW -> MidiCoreReviewPageTags.DRAFT_IDENTITY
                        else -> MidiCoreExportPageTags.SNAPSHOT_STATUS
                    }
                    val capture = MidiCoreVisualCapture.shell(viewport, destination, populated = true, ready = true,
                        scrollToTag = if (detail) target else null)
                    val output = Path.of("build/test-results/visual-shell/$name")
                    java.nio.file.Files.createDirectories(output)
                    javax.imageio.ImageIO.write(capture.image, "png", output.resolve("actual.png").toFile())
                    // Assert the intended state even before a new image baseline is installed.
                    if (destination == MidiCoreWorkspaceDestination.REVIEW) {
                        assertTrue("Accepted arrangement" in capture.texts)
                        assertTrue(MidiCoreReviewPageTags.USE_DRAFT !in capture.bounds)
                        assertTrue(MidiCoreReviewPageTags.PLAY_DRAFT in capture.enabledTags)
                        assertTrue(MidiCoreReviewPageTags.UNDO_DRAFT in capture.enabledTags)
                    }
                    if (destination == MidiCoreWorkspaceDestination.EXPORT) {
                        assertTrue(MidiCoreExportPageTags.PUBLISH in capture.enabledTags)
                        assertTrue(MidiCoreExportPageTags.REVEAL in capture.enabledTags)
                        assertTrue(capture.texts.any { it.contains("Matches current accepted work") })
                        app.melotrail.project.ExportedFileKind.entries.forEach {
                            assertTrue(MidiCoreExportPageTags.file(it) in capture.bounds)
                        }
                    }
                    if (detail) {
                        val player = capture.bounds.getValue(MidiCoreWorkspaceShellTags.PLAYER).single()
                        val decision = capture.bounds.getValue(target).single()
                        assertTrue(decision.top >= 150f && decision.bottom <= player.top, "Ready decision is visible: $decision, player=$player")
                        val action = capture.bounds.getValue(if (destination == MidiCoreWorkspaceDestination.REVIEW)
                            MidiCoreReviewPageTags.PLAY_DRAFT else MidiCoreExportPageTags.REVEAL).single()
                        assertTrue(action.top >= 150f && action.bottom <= player.top, "Ready action is visible")
                    } else assertGeometry(capture, viewport, destination, populated = true)
                    VisualImageComparator.assertMatches(capture.image, "/visual/shell/$name.png",
                        Path.of("build/test-results/visual-shell/$name"))
                }
            }
        }

    @TestFactory
    fun `progress and retry states retain the workspace at every pinned size`() =
        MidiCoreVisualCapture.viewports.flatMap { viewport ->
            listOf(false, true).map { failed ->
                val name = "${viewport.width}-arrange-${if (failed) "failed" else "progress"}"
                dynamicTest(name) {
                    val capture = MidiCoreVisualCapture.shell(viewport, MidiCoreWorkspaceDestination.ARRANGE,
                        populated = true, operation = MidiCoreVisualFixture.operation(failed))
                    VisualImageComparator.assertMatches(capture.image, "/visual/shell/$name.png",
                        Path.of("build/test-results/visual-shell/$name"))
                    assertGeometry(capture, viewport, MidiCoreWorkspaceDestination.ARRANGE, populated = true)
                }
            }
        }

    @Test
    fun `real panel retains independently measured surface border and compact corners`() {
        val image = MidiCoreVisualCapture.panel().image
        VisualImageComparator.assertMatches(image, "/visual/panel.png", Path.of("build/test-results/visual-panel"))
        assertEquals(0xff0b131e.toInt(), image.getRGB(16, 16), "Panel corner exposes canvas")
        assertEquals(0xff26303e.toInt(), image.getRGB(160, 16), "One pixel panel border")
        assertEquals(0xff101923.toInt(), image.getRGB(160, 18), "Panel interior surface")
        assertEquals(0xff101923.toInt(), image.getRGB(21, 21), "An eight-pixel corner must not become a pill")
    }

    private fun assertGeometry(capture: MidiCoreVisualCapture.Capture,
        viewport: MidiCoreVisualCapture.Viewport, destination: MidiCoreWorkspaceDestination, populated: Boolean) {
        fun bounds(tag: String): Rect = checkNotNull(capture.bounds[tag]) { "Missing $tag" }.single()
        val compact = viewport.width == 720
        val inset = if (compact) 16f else 24f
        val header = bounds(MidiCoreWorkspaceShellTags.HEADER)
        assertEquals(0f, header.top)
        assertEquals(if (compact) 56f else 64f, header.height)
        assertEquals(inset, header.left)
        assertEquals(viewport.width - inset, header.right)
        if (!compact) {
            val rail = bounds(MidiCoreWorkspaceShellTags.PROJECT_RAIL)
            assertEquals(inset, rail.left)
            assertEquals(80f, rail.top)
            assertEquals(if (viewport.width == 1536) 224f else 196f, rail.width)
            midiCoreWorkspaceDestinations.forEach { route ->
                val target = bounds(MidiCoreWorkspaceShellTags.destination(route))
                assertTrue(target.width >= 48f && target.height >= 48f, "${route.label} hit bounds")
            }
        }
        val player = bounds(MidiCoreWorkspaceShellTags.PLAYER) // single() also rejects a second player.
        assertEquals(inset, player.left)
        assertEquals(viewport.width - inset, player.right)
        assertTrue(player.height in 72f..112f)
        assertEquals(viewport.height - 8f, player.bottom)
        val transport = listOf(MidiCoreWorkspaceShellTags.PLAYER_PLAY_PAUSE,
            MidiCoreWorkspaceShellTags.PLAYER_STOP, MidiCoreWorkspaceShellTags.PLAYER_LOOP,
            MidiCoreWorkspaceShellTags.PLAYER_OPTIONS).map(::bounds)
        transport.forEach { target ->
            assertTrue(target.width >= 48f && target.height >= 48f, "Transport hit bounds")
            assertTrue(target.top >= player.top && target.bottom <= player.bottom)
        }
        transport.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        val inspectors = listOf(MidiCoreWorkspaceShellTags.CONTEXT, MidiCoreWorkspaceShellTags.PAGE_INSPECTOR)
            .flatMap { capture.bounds[it].orEmpty() }
        assertTrue(inspectors.size <= 1, "Only one contextual inspector")
        if (viewport.width == 1536 && inspectors.isNotEmpty()) {
            val expected = when (destination) {
                MidiCoreWorkspaceDestination.PROJECT -> 458f
                MidiCoreWorkspaceDestination.MIDI -> 381f
                MidiCoreWorkspaceDestination.STRUCTURE_HARMONY -> 390f
                MidiCoreWorkspaceDestination.ARRANGE, MidiCoreWorkspaceDestination.REVIEW -> 332f
                MidiCoreWorkspaceDestination.EXPORT -> 407f
            }
            assertEquals(expected, inspectors.single().width)
        }
        if (populated && destination == MidiCoreWorkspaceDestination.MIDI) {
            listOf(
                MidiCoreMidiPageTags.SOURCE_FACTS,
                MidiCoreMidiPageTags.NOTE_LANE,
                MidiCoreMidiPageTags.NOTE_LANE + "-canvas",
                MidiCoreMidiPageTags.TRACK_TABLE,
                MidiCoreMidiPageTags.track(0),
                MidiCoreMidiPageTags.channel(0, 0),
                MidiCoreMidiPageTags.SELECTION,
                MidiCoreMidiPageTags.AUTHORITY_STATUS,
            ).forEach { tag ->
                checkNotNull(capture.bounds[tag]) { "Populated MIDI capture must include $tag" }.single()
            }
        }
        // Check literal measured palette independently of the PNG and runtime tokens.
        assertEquals(0xff0b131e.toInt(), capture.image.getRGB(0, 100), "Canvas color")
        if (populated && destination in listOf(MidiCoreWorkspaceDestination.ARRANGE, MidiCoreWorkspaceDestination.REVIEW)) {
            val axis = bounds(MidiCoreVerifiedTimelineTags.AXIS)
            val colors = listOf(0xffd77a9e, 0xff9caa61, 0xff5a9dd2, 0xffd79a43).map { it.toInt() }
            val lanes = MidiExportRole.entries.map { bounds(MidiCoreVerifiedTimelineTags.lane(it)) }
            assertEquals(4, lanes.size)
            assertEquals(axis.bottom, lanes.first().top)
            lanes.forEachIndexed { index, lane ->
                assertEquals(52f, lane.height)
                assertEquals(axis.left, lane.left)
                assertEquals(axis.right, lane.right)
                assertTrue(lane.bottom <= player.top)
                assertTrue((lane.top.toInt() until lane.bottom.toInt()).any { y ->
                    (lane.left.toInt() until lane.right.toInt()).any { x -> capture.image.getRGB(x, y) == colors[index] }
                }, "${MidiExportRole.entries[index]} must paint actual notes in its measured role color")
            }
            lanes.zipWithNext().forEach { (first, second) -> assertEquals(first.bottom, second.top) }
        }
    }
}
