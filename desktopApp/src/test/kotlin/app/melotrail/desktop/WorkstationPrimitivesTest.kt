package app.melotrail.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkstationPrimitivesTest {
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

        val secondary = onNodeWithTag(WorkstationPrimitiveTags.SECONDARY)
        secondary.performSemanticsAction(SemanticsActions.RequestFocus)
        secondary.assertIsFocused()
        secondary.performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(1, secondaryActivations)

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
}
