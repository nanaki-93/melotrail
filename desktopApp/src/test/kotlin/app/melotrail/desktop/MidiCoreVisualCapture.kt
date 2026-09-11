package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

/** Capture-only renderer; there is deliberately no resource-writing or golden-update entrypoint. */
@OptIn(ExperimentalComposeUiApi::class)
internal object MidiCoreVisualCapture {
    data class Viewport(val width: Int, val height: Int)
    val viewports = listOf(Viewport(1536, 1024), Viewport(1280, 900), Viewport(720, 900))
    data class Capture(val image: BufferedImage, val bounds: Map<String, List<Rect>>,
        val enabledTags: Set<String>, val texts: Set<String>)

    fun shell(viewport: Viewport, destination: MidiCoreWorkspaceDestination, populated: Boolean,
        operation: MidiCoreWorkspaceOperation = MidiCoreWorkspaceOperation.idle(),
        ready: Boolean = false, scrollToTag: String? = null): Capture = render(viewport, scrollToTag = scrollToTag) {
        Theme {
            MidiCoreWorkspaceShell(
                state = (if (ready) MidiCoreVisualFixture.readyState() else if (populated) MidiCoreVisualFixture.populatedState() else MidiCoreWorkspaceState())
                    .copy(operation = operation),
                initialDestination = destination,
            )
        }
    }

    fun render(viewport: Viewport, focusTag: String? = null, scrollToTag: String? = null, content: @Composable () -> Unit): Capture {
        verifyEnvironment()
        return ImageComposeScene(viewport.width, viewport.height, density = Density(1f, 1f)) {
            content()
        }.use { scene ->
            // Fixed frame times settle initial composition and pin any progress animation.
            repeat(10) { frame -> scene.render(frame * 16_000_000L).close() }
            if (focusTag != null) {
                fun find(node: SemanticsNode): SemanticsNode? =
                    if (node.config.getOrNull(SemanticsProperties.TestTag) == focusTag) node
                    else node.children.firstNotNullOfOrNull(::find)
                val node = checkNotNull(scene.semanticsOwners.firstNotNullOfOrNull { find(it.unmergedRootSemanticsNode) })
                check(node.config[SemanticsActions.RequestFocus].action!!.invoke())
                repeat(10) { frame -> scene.render(160_000_000L + frame * 16_000_000L).close() }
            }
            if (scrollToTag != null) {
                fun pathTo(node: SemanticsNode): List<SemanticsNode>? =
                    if (node.config.getOrNull(SemanticsProperties.TestTag) == scrollToTag) listOf(node)
                    else node.children.firstNotNullOfOrNull { child -> pathTo(child)?.let { listOf(node) + it } }
                val path = checkNotNull(scene.semanticsOwners.firstNotNullOfOrNull { pathTo(it.unmergedRootSemanticsNode) })
                val scroll = path.dropLast(1).last { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                check(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, path.last().positionInRoot.y - 170f))
                repeat(60) { frame -> scene.render(320_000_000L + frame * 16_000_000L).close() }
            }
            val image = scene.render(if (scrollToTag == null) 320_000_000L else 1_280_000_000L).use { frame ->
                frame.encodeToData()!!.use { data -> ImageIO.read(ByteArrayInputStream(data.bytes)) }
            }
            val bounds = linkedMapOf<String, MutableList<Rect>>()
            val enabled = mutableSetOf<String>()
            val texts = mutableSetOf<String>()
            fun visit(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts.add(it.text) }
                node.config.getOrNull(SemanticsProperties.TestTag)?.let { tag ->
                    bounds.getOrPut(tag) { mutableListOf() }.add(node.boundsInRoot)
                    if (!node.config.contains(SemanticsProperties.Disabled)) enabled.add(tag)
                }
                node.children.forEach(::visit)
            }
            scene.semanticsOwners.forEach { visit(it.unmergedRootSemanticsNode) }
            Capture(image, bounds, enabled, texts)
        }
    }

    fun primary(focused: Boolean): Capture = render(Viewport(160, 80),
        focusTag = if (focused) WorkstationPrimitiveTags.PRIMARY else null) { PrimaryFixture() }

    @Composable
    fun PrimaryFixture() {
        Theme {
            Box(Modifier.fillMaxSize().background(MusicWorkspaceTokens.Canvas).padding(16.dp)) {
                WorkstationPrimaryButton("Continue", {}, Modifier.width(128.dp)
                    .semantics { testTag = WorkstationPrimitiveTags.PRIMARY })
            }
        }
    }

    fun panel(): Capture = render(Viewport(320, 160)) {
        Theme {
            Box(Modifier.fillMaxSize().background(MusicWorkspaceTokens.Canvas).padding(16.dp)) {
                WorkstationPanel(title = "Panel", modifier = Modifier.fillMaxSize()) {}
            }
        }
    }

    private val fontFiles = listOf(
        File("/System/Library/Fonts/Supplemental/Arial.ttf"),
        File("/System/Library/Fonts/Supplemental/Arial Bold.ttf"),
    )
    fun verifyEnvironment() {
        check(System.getProperty("os.name") == "Mac OS X") {
            "Visual goldens require the recorded macOS raster/font environment; see docs/VALIDATION.md"
        }
        val pins = java.util.Properties().apply {
            checkNotNull(MidiCoreVisualCapture::class.java.getResourceAsStream("/visual/renderer.properties")).use { load(it) }
        }
        listOf("os.name", "os.version", "os.arch").forEach { key ->
            check(System.getProperty(key) == pins.getProperty(key)) {
                "Visual renderer $key changed; use the documented environment or review new baselines explicitly"
            }
        }
        listOf(
            "compose.scene.sha256" to "/androidx/compose/ui/ImageComposeScene.class",
            "skia.surface.sha256" to "/org/jetbrains/skia/Surface.class",
        ).forEach { (key, resource) ->
            val bytes = checkNotNull(javaClass.getResourceAsStream(resource)).use { it.readBytes() }
            check(sha256(bytes) == pins.getProperty(key)) { "Pinned renderer changed: $resource" }
        }
        val native = checkNotNull(javaClass.getResourceAsStream("/libskiko-macos-arm64.dylib.sha256"))
            .bufferedReader().use { it.readText().trim() }
        check(native == pins.getProperty("skia.native.sha256")) { "Pinned native rasterizer changed" }
        fontFiles.forEachIndexed { index, file ->
            check(file.isFile) { "Visual captures require the pinned macOS Arial files; see docs/VALIDATION.md" }
            val digest = sha256(file.readBytes())
            check(digest == pins.getProperty("font.$index.sha256")) {
                "Visual font changed: ${file.name}; review renderer and goldens explicitly, never auto-update"
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val family = remember { FontFamily(Font(fontFiles[0]), Font(fontFiles[1], FontWeight.Bold)) }
        MelotrailTheme {
            val t = MaterialTheme.typography
            MaterialTheme(typography = t.copy(
                displayLarge = t.displayLarge.copy(fontFamily = family),
                displayMedium = t.displayMedium.copy(fontFamily = family),
                displaySmall = t.displaySmall.copy(fontFamily = family),
                headlineLarge = t.headlineLarge.copy(fontFamily = family),
                headlineMedium = t.headlineMedium.copy(fontFamily = family),
                headlineSmall = t.headlineSmall.copy(fontFamily = family),
                titleLarge = t.titleLarge.copy(fontFamily = family),
                titleMedium = t.titleMedium.copy(fontFamily = family),
                titleSmall = t.titleSmall.copy(fontFamily = family),
                bodyLarge = t.bodyLarge.copy(fontFamily = family),
                bodyMedium = t.bodyMedium.copy(fontFamily = family),
                bodySmall = t.bodySmall.copy(fontFamily = family),
                labelLarge = t.labelLarge.copy(fontFamily = family),
                labelMedium = t.labelMedium.copy(fontFamily = family),
                labelSmall = t.labelSmall.copy(fontFamily = family),
            ), content = content)
        }
    }
}
