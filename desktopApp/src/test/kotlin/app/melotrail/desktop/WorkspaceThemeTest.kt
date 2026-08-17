package app.melotrail.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceThemeTest {
    @Test
    fun `named visual tokens provide the dark palette accents and minimum hit target`() {
        assertEquals(setOf("piano", "bass", "drums", "pad", "strings"), instrumentLaneColors.keys)
        assertEquals(48f, MusicWorkspaceTokens.Interaction.MinimumHitTarget.value)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextPrimary, MusicWorkspaceTokens.Canvas) >= 7.0)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextSecondary, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Error, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Information, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Warning, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Loading, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(MusicWorkspaceTokens.Success != MusicWorkspaceTokens.Teal)
    }

    @Test
    fun `theme showcase is deterministic and exposes non color state labels`() = runComposeUiTest {
        setContent { MelotrailTheme { MusicWorkspaceThemeShowcase() } }

        onNodeWithTag(ThemeShowcaseTags.ROOT).assertExists()
        onNodeWithTag(ThemeShowcaseTags.PALETTE).assertExists()
        onNodeWithTag(ThemeShowcaseTags.STATES).assertExists()
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
        val darker = min(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(value: Float): Double = if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).let { it * it * it }.toDouble()
}
