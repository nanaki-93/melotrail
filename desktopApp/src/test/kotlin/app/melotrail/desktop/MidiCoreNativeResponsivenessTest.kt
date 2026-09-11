package app.melotrail.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import app.melotrail.application.MidiCoreVisualEvidence
import app.melotrail.midi.domain.MidiExportRole
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skia.Image
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real AWT geometry/density and recorded-frame replay; explicit nativeDesktopCapture uses screen pixels. */
@OptIn(ExperimentalComposeUiApi::class)
class MidiCoreNativeResponsivenessTest {
    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun `large source and long names survive native short window resize and all six destinations`() {
        check(!GraphicsEnvironment.isHeadless()) { "U07 native evidence requires a graphical desktop" }
        val captures = mutableListOf<JsonObject>()
        val resizes = mutableListOf<JsonObject>()
        val density = AtomicReference(Density(1f))
        val screenCapture = when (val mode = System.getProperty("melotrail.nativeScreenCapture", "false")) {
            "true" -> true
            "false" -> false
            else -> error("Invalid explicit screen capture mode: $mode")
        }
        val path = U07Evidence.root.resolve(if (screenCapture) "native-screen" else "native")
        Files.createDirectories(path)
        MidiCoreResponsivenessFixture(bars = 256, notesPerBar = 32, sectionBars = 4, longNames = true).use { fixture ->
            fixture.prepare()
            val projected = fixture.awaitSourceProjection().visualEvidence!!.source as MidiCoreVisualEvidence.Available
            assertEquals(8_192, projected.value.lanes.sumOf { it.events.size })
            fixture.confirmPlan()
            val largePreviewMs = fixture.apply(MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "section-1", 41L))
            fixture.apply(MidiCoreWorkspaceIntent.PlaySourceMelody)
            onEdt { fixture.workspace.accept(MidiCoreWorkspaceIntent.StopAudition) }
            val window = onEdt {
                ComposeWindow().apply {
                    title = "Melotrail — U07 owned responsiveness fixture"
                    configureMidiCoreDesktopWindow(this)
                    setSize(1280, 720)
                    val display = usableDisplay(this)
                    setLocation(display.x, display.y)
                    setContent {
                        val currentDensity = LocalDensity.current
                        SideEffect { density.set(currentDensity) }
                        val state by fixture.workspace.state.collectAsState()
                        MelotrailTheme { MidiCoreWorkspaceShell(state, fixture.workspace::accept) }
                    }
                    isVisible = true
                    toFront()
                }
            }
            var completed = false
            try {
                val robot = if (screenCapture) onEdt { Robot(window.graphicsConfiguration.device) } else null
                // Select once: resizing must preserve this selection without restoring it in the test.
                onEdt { fixture.workspace.accept(MidiCoreWorkspaceIntent.SelectArrangementOccurrence("section-63")) }
                listOf(1280 to 720, 1024 to 768, 720 to 900, 1280 to 720).forEachIndexed { resize, (requestedWidth, requestedHeight) ->
                    val display = onEdt { usableDisplay(window) }
                    val clientSize = onEdt {
                        val frame = window.insets
                        val size = Dimension(
                            minOf(requestedWidth, display.width - frame.left - frame.right),
                            minOf(requestedHeight, display.height - frame.top - frame.bottom),
                        )
                        check(size.width >= 720 && size.height + frame.top + frame.bottom >= window.minimumSize.height) {
                            "Native display cannot accommodate the supported minimum window: $display, frame=$frame"
                        }
                        val frameWidth = size.width + frame.left + frame.right
                        val frameHeight = size.height + frame.top + frame.bottom
                        // Keep the native origin fixed. Combining relocation, resizing and forced
                        // validation can leave Metal's layer at the previous height on macOS.
                        // Let AWT deliver resize/layout events before ordinary repaint/capture.
                        window.setSize(frameWidth, frameHeight)
                        size
                    }
                    val width = clientSize.width
                    val height = clientSize.height
                    settle(window)
                    onEdt {
                        resizes += buildJsonObject {
                            put("requestedWidth", requestedWidth); put("requestedHeight", requestedHeight)
                            put("intendedWidth", width); put("intendedHeight", height)
                            put("realizedWidth", window.contentPane.width); put("realizedHeight", window.contentPane.height)
                            put("usableDisplayPoints", JsonArray(listOf(display.x, display.y, display.width, display.height).map(::JsonPrimitive)))
                        }
                        assertEquals(clientSize, window.contentPane.size,
                            "Native minimum must permit the requested client size within the usable display; requested=${requestedWidth}x$requestedHeight, usable=$display")
                        assertEquals(window.graphicsConfiguration.defaultTransform.scaleX.toFloat(), density.get().density)
                        assertTrue(density.get().fontScale > 0f)
                    }
                    // Repeated resize back to wide must preserve the current musical selection and sole player.
                    assertEquals("section-63", fixture.workspace.state.value.arrangement.selectedOccurrenceId)
                    midiCoreWorkspaceDestinations.forEach { destination ->
                        onEdt {
                            val navigation = node(window, MidiCoreWorkspaceShellTags.destination(destination))
                            check(navigation.config[SemanticsActions.OnClick].action!!.invoke())
                        }
                        settle(window)
                        val scale = density.get().density
                        val geometry = onEdt { checkGeometry(window, width, height, scale) }
                        if (destination == MidiCoreWorkspaceDestination.ARRANGE || destination == MidiCoreWorkspaceDestination.REVIEW) {
                            onEdt {
                                val lanes = MidiExportRole.entries.map { node(window, MidiCoreVerifiedTimelineTags.lane(it)) }
                                // Unclipped positions retain the musical geometry even below the short viewport.
                                lanes.forEach { assertEquals(52f * scale, it.size.height.toFloat(), 1f) }
                                assertEquals(1, lanes.map { it.positionInRoot.x }.distinct().size)
                            }
                        }
                        val filename = "$resize-${width}x$height-${destination.route}.png"
                        val image = captureWindow(window, robot, scale, path)
                        check(image.width == (width * scale).toInt() && image.height == (height * scale).toInt())
                        // A denied screen capture or an obscured window must never become visual evidence.
                        assertTrue(hasWorkspacePalette(image), "Native capture did not contain the workspace; inspect screen-capture access")
                        ImageIO.write(image, "png", path.resolve(filename).toFile())
                        captures += buildJsonObject {
                            put("file", filename); put("sha256", U07Evidence.sha256(Files.readAllBytes(path.resolve(filename))))
                            put("destination", destination.route); put("clientWidthDp", width); put("clientHeightDp", height)
                            put("requestedClientWidthDp", requestedWidth); put("requestedClientHeightDp", requestedHeight)
                            put("screenConstrained", width != requestedWidth || height != requestedHeight)
                            put("usableDisplayPoints", JsonArray(listOf(display.x, display.y, display.width, display.height).map(::JsonPrimitive)))
                            put("pixelWidth", image.width); put("pixelHeight", image.height)
                            put("density", scale); put("fontScale", density.get().fontScale)
                            put("renderer", onEdt { window.renderApi.toString() })
                            put("font", "Production MelotrailTheme system-sans fallback; no pinned-font override")
                            put("geometry", geometry)
                        }
                        if (destination == MidiCoreWorkspaceDestination.ARRANGE) {
                            onEdt { scrollTo(window, MidiCoreArrangePageTags.CREATE_DRAFT, scale) }
                            settle(window)
                            onEdt {
                                val action = node(window, MidiCoreArrangePageTags.CREATE_DRAFT)
                                val player = node(window, MidiCoreWorkspaceShellTags.PLAYER).boundsInRoot
                                assertTrue(action.boundsInRoot.height >= 48f * scale)
                                assertTrue(action.boundsInRoot.bottom <= player.top && action.boundsInRoot.top >= 0f,
                                    "Create full draft must be reachable above the persistent player")
                                check(action.config[SemanticsActions.RequestFocus].action!!.invoke())
                            }
                            settle(window)
                            onEdt { assertTrue(node(window, MidiCoreArrangePageTags.CREATE_DRAFT).config[SemanticsProperties.Focused]) }
                            val actionFile = "$resize-arrange-action.png"
                            ImageIO.write(captureWindow(window, robot, scale, path), "png", path.resolve(actionFile).toFile())
                            captures += buildJsonObject {
                                put("file", actionFile); put("sha256", U07Evidence.sha256(Files.readAllBytes(path.resolve(actionFile))))
                                put("destination", destination.route); put("state", "action-focused")
                                put("clientWidthDp", width); put("clientHeightDp", height)
                                put("requestedClientWidthDp", requestedWidth); put("requestedClientHeightDp", requestedHeight)
                                put("screenConstrained", width != requestedWidth || height != requestedHeight)
                                put("density", scale); put("fontScale", density.get().fontScale)
                                put("geometry", onEdt { checkGeometry(window, width, height, scale) })
                            }
                        }
                        assertEquals("section-63", fixture.workspace.state.value.arrangement.selectedOccurrenceId)
                    }
                }
                fixture.assertSourcePreserved()
                assertEquals(1, fixture.output.maximumSessions)
                completed = true
            } finally {
                onEdt { window.dispose() }
                U07Evidence.write(path.resolve("observations.json"), buildJsonObject {
                    put("identity", U07Evidence.identity()); put("machine", U07Evidence.machine())
                    put("status", if (completed) "MEASURED" else "INCOMPLETE")
                    put("fixture", "Owned 256-bar source; 8192 verified notes; 64 four-bar occurrences; 120-character project name and long Unicode section labels")
                    put("largePreviewUiCompletionMs", largePreviewMs)
                    put("captureMethod", if (screenCapture) "Robot multi-resolution client-area screen capture" else
                        "Skia recorded-frame raster replay from the real window; NOT screen/compositor pixels")
                    put("onscreenCompositorCapture", if (!screenCapture) "NOT_MEASURED" else if (completed) "MEASURED" else "INCOMPLETE")
                    put("nativeSizePolicy", "Requested client size is reduced only when it plus native frame insets exceeds the recorded usable display; realized client bounds must match exactly. Fixed-size goldens remain separate.")
                    put("humanVisualDecision", "NOT_RECORDED"); put("acousticOnset", "UNMEASURED")
                    put("resizes", JsonArray(resizes))
                    put("captures", JsonArray(captures))
                })
            }
        }
    }

    private fun settle(window: ComposeWindow) {
        // Allow real focus/scroll animations to finish; a virtual test clock is not used here.
        repeat(12) { onEdt { window.repaint() }; Thread.sleep(50) }
    }

    private fun usableDisplay(window: ComposeWindow): java.awt.Rectangle {
        val configuration = window.graphicsConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        return java.awt.Rectangle(bounds.x + insets.left, bounds.y + insets.top,
            bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
    }

    private fun node(window: ComposeWindow, tag: String): SemanticsNode = nodes(window).single {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    }

    private fun nodes(window: ComposeWindow): List<SemanticsNode> {
        fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        return window.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }
    }

    private fun checkGeometry(window: ComposeWindow, width: Int, height: Int, scale: Float): JsonObject {
        val player = node(window, MidiCoreWorkspaceShellTags.PLAYER).boundsInRoot
        val header = node(window, MidiCoreWorkspaceShellTags.HEADER).boundsInRoot
        assertTrue(player.height / scale in 72f..112f)
        assertTrue(player.left >= 0 && player.right <= width * scale && player.bottom <= height * scale)
        assertEquals((height - 8f) * scale, player.bottom, 1f)
        assertEquals((if (width >= 1240) 64f else 56f) * scale, header.height, 1f)
        val controls = listOf(MidiCoreWorkspaceShellTags.PLAYER_PLAY_PAUSE, MidiCoreWorkspaceShellTags.PLAYER_STOP,
            MidiCoreWorkspaceShellTags.PLAYER_LOOP, MidiCoreWorkspaceShellTags.PLAYER_OPTIONS).map { node(window, it).boundsInRoot }
        controls.forEach { bounds ->
            assertTrue(bounds.width >= 48f * scale && bounds.height >= 48f * scale)
            assertTrue(bounds.left >= player.left && bounds.right <= player.right && bounds.top >= player.top && bounds.bottom <= player.bottom)
        }
        assertTrue(nodes(window).count { it.config.getOrNull(SemanticsProperties.TestTag) in
            setOf(MidiCoreWorkspaceShellTags.CONTEXT, MidiCoreWorkspaceShellTags.PAGE_INSPECTOR) } <= 1)
        fun rect(value: Rect) = JsonArray(listOf(value.left, value.top, value.right, value.bottom).map { JsonPrimitive(it / scale) })
        return buildJsonObject { put("playerDp", rect(player)); put("headerDp", rect(header)); put("transportDp", JsonArray(controls.map(::rect))) }
    }

    private fun scrollTo(window: ComposeWindow, tag: String, scale: Float) {
        fun path(node: SemanticsNode): List<SemanticsNode>? =
            if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) listOf(node)
            else node.children.firstNotNullOfOrNull { child -> path(child)?.let { listOf(node) + it } }
        val ancestors = checkNotNull(window.semanticsOwners.firstNotNullOfOrNull { path(it.unmergedRootSemanticsNode) })
        val scroll = ancestors.dropLast(1).last { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
        check(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,
            ancestors.last().positionInRoot.y - scroll.boundsInRoot.top - 8f * scale))
    }

    private fun captureWindow(window: ComposeWindow, robot: Robot?, scale: Float, path: java.nio.file.Path): BufferedImage {
        val rect = onEdt {
            java.awt.Rectangle(window.contentPane.locationOnScreen, window.contentPane.size).also {
                check(usableDisplay(window).contains(it)) { "The native client capture extends beyond the usable display: $it" }
            }
        }
        // Choose the evidence boundary before capture. A rejected Robot image never falls back.
        val image = if (robot != null) {
            robot.createMultiResolutionScreenCapture(rect)
                .getResolutionVariant(rect.width * scale.toDouble(), rect.height * scale.toDouble()) as BufferedImage
        } else onEdt {
            window.renderImmediately()
            fun layers(component: Component): List<SkiaLayer> =
                if (component is SkiaLayer) listOf(component)
                else if (component is Container) component.components.flatMap(::layers) else emptyList()
            val layer = layers(window.contentPane).single()
            assertEquals(java.awt.Point(0, 0), SwingUtilities.convertPoint(layer, 0, 0, window.contentPane))
            assertEquals(window.contentPane.size, layer.size)
            checkNotNull(layer.screenshot()) { "The native window has no recorded frame" }.use { bitmap ->
                Image.makeFromBitmap(bitmap).use { rendered ->
                    checkNotNull(rendered.encodeToData()).use { data ->
                        ImageIO.read(data.bytes.inputStream())
                    }
                }
            }
        }
        // Retain the exact failing capture before checking it, so native failures are diagnosable.
        ImageIO.write(image, "png", path.resolve("last-capture.png").toFile())
        // Screen pixels additionally detect native peer movement/clipping; replay proves frame content only.
        // Check a broad interior band: a single corner pixel is not stable across native window
        // shapes/compositors, while a cropped or obscured client cannot supply this coverage.
        assertHorizontalColorCoverage(
            image = image,
            y = (8 * scale).toInt(),
            color = 0xff151e2a.toInt(),
            minimumCoverage = 0.65,
            message = "Native capture must include the top of the persistent header",
        )
        assertHorizontalColorCoverage(
            image = image,
            y = image.height - (4 * scale).toInt(),
            color = 0xff0b131e.toInt(),
            minimumCoverage = 0.65,
            message = "Native capture must end inside the workspace, not the surrounding desktop",
        )
        return image
    }

    private fun assertHorizontalColorCoverage(
        image: BufferedImage,
        y: Int,
        color: Int,
        minimumCoverage: Double,
        message: String,
    ) {
        val step = maxOf(1, image.width / 256)
        val samples = (0 until image.width step step).map { image.getRGB(it, y) }
        val coverage = samples.count { nearColor(it, color) }.toDouble() / samples.size
        assertTrue(coverage >= minimumCoverage, "$message; matching color coverage=$coverage")
    }

    private fun nearColor(pixel: Int, color: Int): Boolean = listOf(0, 8, 16).all { shift ->
        kotlin.math.abs(((pixel shr shift) and 255) - ((color shr shift) and 255)) <= 8
    }

    private fun hasWorkspacePalette(image: BufferedImage): Boolean {
        val colors = setOf(0xff0b131e.toInt(), 0xff101923.toInt(), 0xff151e2a.toInt())
        var matches = 0
        var count = 0
        for (y in 0 until image.height step 16) for (x in 0 until image.width step 16) {
            count++
            val pixel = image.getRGB(x, y)
            if (colors.any { nearColor(pixel, it) }) matches++
        }
        return matches > count / 3
    }
}
