package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import java.nio.file.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkstationPrimitivesTest {
    @Test
    fun `real primary pixels and keyboard outline match full pinned images`() =
        runSkikoComposeUiTest(size = Size(160f, 80f)) {
            MidiCoreVisualCapture.verifyEnvironment()
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 1f),
                ) { MidiCoreVisualCapture.PrimaryFixture() }
            }
            val primary = onNodeWithTag(WorkstationPrimitiveTags.PRIMARY)
            val image = onRoot().captureToImage().toAwtImage()
            VisualImageComparator.assertMatches(image, "/visual/primary-unfocused.png",
                Path.of("build/test-results/visual-primary/unfocused"))
            // Literal values remain independent of both theme tokens and the expected PNG.
            assertEquals(0xff594080.toInt(), image.getRGB(20, 40), "Primary fill")
            val text = 0xfff1f2f4.toInt()
            assertTrue((0 until image.height).any { y ->
                (0 until image.width).any { x -> image.getRGB(x, y) == text }
            }, "The primary label must use readable text color")
            assertTrue(contrast(text, image.getRGB(20, 40)) >= 4.5, "Primary text contrast")
            assertEquals(0xff0b131e.toInt(), image.getRGB(16, 16), "Rounded corner remains outside the fill")
            assertEquals(0xff594080.toInt(), image.getRGB(19, 19), "Six-pixel primary corner must not become a pill")
            val bounds = primary.getUnclippedBoundsInRoot()
            assertEquals(128f, (bounds.right - bounds.left).value)
            assertEquals(48f, (bounds.bottom - bounds.top).value)
            primary.performSemanticsAction(SemanticsActions.RequestFocus)
            primary.assertIsFocused()
            waitForIdle()
            val focused = onRoot().captureToImage().toAwtImage()
            VisualImageComparator.assertMatches(focused, "/visual/primary-focused.png",
                Path.of("build/test-results/visual-primary/focused"))
            assertEquals(0xffd4b8ff.toInt(), focused.getRGB(80, 16), "Visible keyboard focus")
            assertTrue(contrast(focused.getRGB(80, 16), image.getRGB(20, 40)) >= 3.0, "Focus boundary contrast")
        }

    @Test
    fun `compact primitive gallery exposes every target control state and long text`() = runComposeUiTest {
        setContent { MelotrailTheme { WorkstationPrimitiveGallery() } }

        onNodeWithTag(WorkstationPrimitiveTags.GALLERY).assertExists()
        listOf(
            WorkstationPrimitiveTags.PRIMARY,
            WorkstationPrimitiveTags.SECONDARY,
            WorkstationPrimitiveTags.ICON,
            WorkstationPrimitiveTags.NAVIGATION,
            WorkstationPrimitiveTags.DISCLOSURE,
        ).forEach { tag -> onNodeWithTag(tag).assertExists() }
        onNodeWithTag(WorkstationPrimitiveTags.DISABLED).assertIsNotEnabled()
        onNodeWithContentDescription("Export MIDI package unavailable: Approve an arrangement first").assertExists()
        onNodeWithText("Long source name that must not make the compact controls overlap").assertExists()
        onNodeWithContentDescription("Ready: MIDI source preserved").assertExists()
    }

    @Test
    fun `primary and secondary controls retain focus and activate with enter and space`() = runComposeUiTest {
        var primaryActivations = 0
        var secondaryActivations = 0
        var expanded by mutableStateOf(false)
        setContent {
            MelotrailTheme {
                Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                    WorkstationPrimaryButton(
                        label = "Primary activation",
                        onClick = { primaryActivations += 1 },
                        modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.PRIMARY },
                    )
                    WorkstationSecondaryButton(
                        label = "Secondary activation",
                        onClick = { secondaryActivations += 1 },
                        modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.SECONDARY },
                    )
                    WorkstationDisclosure(
                        label = "Evidence",
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.DISCLOSURE },
                    ) { androidx.compose.material3.Text("Evidence details", modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.DISCLOSURE_CONTENT }) }
                }
            }
        }

        val primary = onNodeWithTag(WorkstationPrimitiveTags.PRIMARY)
        primary.performSemanticsAction(SemanticsActions.RequestFocus)
        primary.assertIsFocused()
        primary.performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, primaryActivations)

        primary.performKeyInput { pressKey(Key.Tab) }
        val secondary = onNodeWithTag(WorkstationPrimitiveTags.SECONDARY)
        secondary.assertIsFocused()
        secondary.performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(1, secondaryActivations)
        // Probe the edges of the actual 48 dp target, not only semantic center activation.
        primary.performTouchInput { click(Offset(1f, height / 2f)) }
        primary.performTouchInput { click(Offset(width / 2f, height - 1f)) }
        secondary.performTouchInput { click(Offset(width - 1f, height / 2f)) }
        assertEquals(3, primaryActivations)
        assertEquals(2, secondaryActivations)

        val disclosure = onNodeWithContentDescription("Expand Evidence")
        disclosure.performSemanticsAction(SemanticsActions.RequestFocus)
        disclosure.performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(WorkstationPrimitiveTags.DISCLOSURE_CONTENT).assertExists()
    }

    @Test
    fun `adjacent action hit boxes do not overlap and use the measured compact radii`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                ) {
                    WorkstationPrimaryButton("Continue", {}, Modifier.weight(1f).semantics { testTag = WorkstationPrimitiveTags.PRIMARY })
                    WorkstationSecondaryButton("Review", {}, Modifier.weight(1f).semantics { testTag = WorkstationPrimitiveTags.SECONDARY })
                    WorkstationIconButton(WorkspaceVectorIcon.MIDI.image, "Open MIDI tools", {}, Modifier.semantics { testTag = WorkstationPrimitiveTags.ICON })
                }
            }
        }

        val primary = onNodeWithTag(WorkstationPrimitiveTags.PRIMARY).getUnclippedBoundsInRoot()
        val secondary = onNodeWithTag(WorkstationPrimitiveTags.SECONDARY).getUnclippedBoundsInRoot()
        val icon = onNodeWithTag(WorkstationPrimitiveTags.ICON).getUnclippedBoundsInRoot()
        assertTrue((primary.right - primary.left).value >= 48f && (primary.bottom - primary.top).value >= 48f)
        assertTrue((secondary.right - secondary.left).value >= 48f && (secondary.bottom - secondary.top).value >= 48f)
        assertTrue((icon.right - icon.left).value >= 48f && (icon.bottom - icon.top).value >= 48f)
        assertTrue(primary.right <= secondary.left)
        assertTrue(secondary.right <= icon.left)
        assertEquals(6f, MusicWorkspaceTokens.Radius.Control.value)
        assertEquals(8f, MusicWorkspaceTokens.Radius.Panel.value)
    }

    private fun contrast(first: Int, second: Int): Double {
        fun luminance(argb: Int): Double {
            fun linear(shift: Int): Double {
                val channel = ((argb ushr shift) and 255) / 255.0
                return if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
        }
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
