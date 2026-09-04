package app.melotrail.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.sp
import app.melotrail.project.CandidateRole
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WorkspaceThemeTest {
    @Test
    fun `target visual tokens provide the measured MIDI palette and readable contrast`() {
        assertEquals(48f, MusicWorkspaceTokens.Interaction.MinimumHitTarget.value)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextPrimary, MusicWorkspaceTokens.Canvas) >= 7.0)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextSecondary, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.TextPrimary, MusicWorkspaceTokens.PrimaryFill) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Error, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Information, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Warning, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(contrastRatio(MusicWorkspaceTokens.Progress, MusicWorkspaceTokens.Surface) >= 4.5)
        assertTrue(MusicWorkspaceTokens.Success != MusicWorkspaceTokens.Primary)
        assertEquals(6f, MusicWorkspaceTokens.Radius.Control.value)
        assertEquals(8f, MusicWorkspaceTokens.Radius.Card.value)
        assertEquals(8f, MusicWorkspaceTokens.Radius.Panel.value)
        assertEquals(5, MusicWorkspaceTokens.SectionColors.size)
    }

    @Test
    fun `target MIDI roles vector icons and semantic states map to shared tokens`() {
        assertEquals(MusicWorkspaceTokens.PrimaryFill, musicColorScheme.primary)
        assertEquals(MusicWorkspaceTokens.TextPrimary, musicColorScheme.onPrimary)
        assertEquals(MusicWorkspaceTokens.SelectedSurface, musicColorScheme.primaryContainer)
        assertEquals(MusicWorkspaceTokens.WarmAccent, musicColorScheme.secondary)
        assertEquals(MusicWorkspaceTokens.Information, musicColorScheme.tertiary)
        assertEquals(MusicWorkspaceTokens.Error, musicColorScheme.error)

        assertEquals(
            setOf("Melody", "Chords", "Bass", "Drums"),
            MidiWorkspaceRoleStyle.entries.map(MidiWorkspaceRoleStyle::label).toSet(),
        )
        assertEquals(MusicWorkspaceTokens.Role.Melody, MidiWorkspaceRoleStyle.MELODY.color)
        assertEquals(MusicWorkspaceTokens.Role.Chords, midiWorkspaceRoleStyle(CandidateRole.CHORDS).color)
        assertEquals(MusicWorkspaceTokens.Role.Bass, midiWorkspaceRoleStyle(CandidateRole.BASS).color)
        assertEquals(MusicWorkspaceTokens.Role.Drums, midiWorkspaceRoleStyle(CandidateRole.DRUMS).color)
        assertTrue(MidiWorkspaceRoleStyle.entries.all { it.icon.contentDescription.isNotBlank() })
        assertEquals("Export MIDI package", WorkspaceVectorIcon.EXPORT.contentDescription)
        assertEquals(MusicWorkspaceTokens.SectionColors[2], sectionColor(2))
        assertEquals(MusicWorkspaceTokens.SectionColors[0], sectionColor(5))

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
    fun `typography and application identity are explicit and available offline`() {
        assertEquals(FontFamily.SansSerif, workspaceFontFamily)
        assertEquals(24.sp, workspaceTypography.headlineLarge.fontSize)
        assertEquals(30.sp, workspaceTypography.headlineLarge.lineHeight)
        assertEquals(13.sp, workspaceTypography.bodyMedium.fontSize)
        assertEquals(18.sp, workspaceTypography.bodyMedium.lineHeight)
        assertEquals(11.sp, workspaceTypography.labelSmall.fontSize)
        assertEquals(16.sp, workspaceTypography.labelSmall.lineHeight)
        assertNotNull(javaClass.classLoader.getResource("Melotrail.icns"))
        assertNotNull(javaClass.classLoader.getResource("arranger-icon.svg"))
    }

    @Test
    fun `theme showcase is deterministic and exposes non color state labels`() = runComposeUiTest {
        setContent { MelotrailTheme { MusicWorkspaceThemeShowcase() } }

        onNodeWithTag(ThemeShowcaseTags.ROOT).assertExists()
        onNodeWithTag(ThemeShowcaseTags.PALETTE).assertExists()
        onNodeWithTag(ThemeShowcaseTags.ROLE_PALETTE).assertExists()
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
