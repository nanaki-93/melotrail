package app.melotrail.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object MusicWorkspaceTokens {
    /** Measurements taken from plan/UI.png at 1536 × 1024, 100% scale. */
    object Reference {
        val ViewportWidth = 1536.dp
        val ViewportHeight = 1024.dp
        val OuterPadding = 16.dp
        val HeaderHeight = 56.dp
        val FooterHeight = 104.dp
        val ColumnGap = 12.dp
        val LeftRailWidth = 254.dp
        val CenterWidth = 698.dp
        val RightRailWidth = 533.dp
        val WideBreakpoint = 1180.dp
        val MediumBreakpoint = 760.dp
        const val BorderAlpha = 0.78f
    }
    val Canvas = Color(0xFF071017)
    val Surface = Color(0xFF0D1821)
    val ElevatedSurface = Color(0xFF12212B)
    val Border = Color(0xFF253845)
    val Teal = Color(0xFF4BD7C3)
    val TealFocus = Color(0xFF8EF4E4)
    val TealPressed = Color(0xFF2AAE9E)
    val TextPrimary = Color(0xFFE2EDF1)
    val TextSecondary = Color(0xFFAEBDC5)
    val Disabled = Color(0xFF60717B)
    val Error = Color(0xFFFFB4AB)
    val Warning = Color(0xFFF0B356)
    val Information = Color(0xFF8AB4F8)
    val Loading = Color(0xFFC7A6FF)
    val Success = Teal
    val Piano = Color(0xFF59CCC4)
    val Bass = Color(0xFF86C979)
    val Drums = Color(0xFFF0B356)
    val Pad = Color(0xFFAB91EB)
    val Strings = Color(0xFFF08262)
    val ScenePlaceholder = Color(0xFF13232B)

    object Spacing {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
    }

    object Radius {
        val Compact = 6.dp
        val Control = 8.dp
        val Card = 12.dp
        val Panel = 16.dp
    }

    object Interaction {
        val MinimumHitTarget = 48.dp
        const val HoverAlpha = 0.12f
        const val PressedAlpha = 0.22f
        const val DisabledAlpha = 0.38f
    }
}

val instrumentLaneColors = mapOf(
    "piano" to MusicWorkspaceTokens.Piano,
    "bass" to MusicWorkspaceTokens.Bass,
    "drums" to MusicWorkspaceTokens.Drums,
    "pad" to MusicWorkspaceTokens.Pad,
    "strings" to MusicWorkspaceTokens.Strings
)

private val musicColorScheme = darkColorScheme(
    primary = MusicWorkspaceTokens.Teal,
    onPrimary = MusicWorkspaceTokens.Canvas,
    background = MusicWorkspaceTokens.Canvas,
    onBackground = MusicWorkspaceTokens.TextPrimary,
    surface = MusicWorkspaceTokens.Surface,
    onSurface = MusicWorkspaceTokens.TextPrimary,
    surfaceVariant = MusicWorkspaceTokens.ElevatedSurface,
    outline = MusicWorkspaceTokens.Border,
    onSurfaceVariant = MusicWorkspaceTokens.TextSecondary,
    error = MusicWorkspaceTokens.Error
)

private val workspaceShapes = Shapes(
    extraSmall = RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact),
    small = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
    medium = RoundedCornerShape(MusicWorkspaceTokens.Radius.Card),
    large = RoundedCornerShape(MusicWorkspaceTokens.Radius.Panel)
)

private val workspaceTypography = Typography()

@Composable
internal fun workspacePrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MusicWorkspaceTokens.Teal,
    contentColor = MusicWorkspaceTokens.Canvas,
    disabledContainerColor = MusicWorkspaceTokens.Disabled,
    disabledContentColor = MusicWorkspaceTokens.TextSecondary
)

object ThemeShowcaseTags {
    const val ROOT = "theme-showcase"
    const val PALETTE = "theme-showcase-palette"
    const val STATES = "theme-showcase-states"
}

/** Deterministic palette fixture for Compose tests and local visual review; it has no workspace behavior. */
@Composable
internal fun MusicWorkspaceThemeShowcase() {
    Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().background(MusicWorkspaceTokens.Canvas)
            .padding(MusicWorkspaceTokens.Spacing.Lg).semantics { testTag = ThemeShowcaseTags.ROOT },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)
    ) {
        Text("Workspace visual tokens", color = MusicWorkspaceTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
        Row(modifier = androidx.compose.ui.Modifier.semantics { testTag = ThemeShowcaseTags.PALETTE }, horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            listOf(MusicWorkspaceTokens.Canvas, MusicWorkspaceTokens.Surface, MusicWorkspaceTokens.ElevatedSurface, MusicWorkspaceTokens.Teal, MusicWorkspaceTokens.Error).forEach { color ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(MusicWorkspaceTokens.Interaction.MinimumHitTarget).height(MusicWorkspaceTokens.Interaction.MinimumHitTarget).background(color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Control)))
            }
        }
        Text(
            "Ready · teal primary · Error · text labels remain available when colour is not.",
            modifier = androidx.compose.ui.Modifier.semantics { testTag = ThemeShowcaseTags.STATES },
            color = MusicWorkspaceTokens.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun MelotrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = musicColorScheme, shapes = workspaceShapes, typography = workspaceTypography, content = content)
}
