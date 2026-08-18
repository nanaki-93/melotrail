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
    fun `named visual tokens provide the cinematic palette and readable state contrast`() {
        assertEquals(setOf("piano", "bass", "drums", "pad", "strings"), instrumentLaneColors.keys)
        assertEquals(48f, MusicWorkspaceTokens.Interaction.MinimumHitTarget.value)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextPrimary, MusicWorkspaceTokens.Canvas) >= 7.0)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextSecondary, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Error, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Information, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Warning, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Progress, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(MusicWorkspaceTokens.Success != MusicWorkspaceTokens.Primary)
    }

    @Test
    fun `Material lanes and semantic state roles map to their shared tokens`() {
        assertEquals(MusicWorkspaceTokens.Primary, musicColorScheme.primary)
        assertEquals(MusicWorkspaceTokens.SelectedSurface, musicColorScheme.primaryContainer)
        assertEquals(MusicWorkspaceTokens.WarmAccent, musicColorScheme.secondary)
        assertEquals(MusicWorkspaceTokens.Information, musicColorScheme.tertiary)
        assertEquals(MusicWorkspaceTokens.Error, musicColorScheme.error)

        assertEquals(MusicWorkspaceTokens.Piano, instrumentLane("Piano")?.color)
        assertEquals("♫", instrumentLane("piano")?.icon)
        assertEquals("Drums", instrumentLane("drums")?.label)
        assertTrue(instrumentLanes.values.all { it.label.isNotBlank() && it.icon.isNotBlank() })

        assertEquals(WorkspaceSemanticState.entries.toSet(), semanticStateColors.keys)
        assertEquals(MusicWorkspaceTokens.Success, semanticColor(WorkspaceSemanticState.READY))
        assertEquals(MusicWorkspaceTokens.Warning, semanticColor(WorkspaceSemanticState.WARNING))
        assertEquals(MusicWorkspaceTokens.Error, semanticColor(WorkspaceSemanticState.ERROR))
        assertEquals(MusicWorkspaceTokens.Focus, semanticColor(WorkspaceSemanticState.FOCUS))
        assertEquals(WorkspaceSemanticState.READY.label, "Ready")
        assertEquals(WorkspaceSemanticState.ERROR.label, "Blocked")
        assertEquals(WorkspaceSemanticState.DISABLED.label, "Unavailable")
        assertEquals(WorkspaceSemanticState.entries.size, semanticStateColors.values.toSet().size)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Disabled, MusicWorkspaceTokens.DisabledSurface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Focus, MusicWorkspaceTokens.SelectedSurface) >= 4.5)
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
