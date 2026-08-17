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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

object MusicWorkspaceTokens {
    /** Measurements taken from plan/pictures/App-pages.png at 1536 × 1024, 100% scale. */
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
        /** At this width a page and two rails no longer fit without page-level scrolling. */
        val NarrowBreakpoint = 760.dp
        const val BorderAlpha = 0.78f
    }

    /** Shared measurements for the compact reference shell; avoid per-widget approximations. */
    object Shell {
        val TopBarHeight = 64.dp
        val ProjectRailWidth = 248.dp
        val CompactProjectRailWidth = 184.dp
        val ContextRailWidth = 288.dp
        val CompactContextRailWidth = 240.dp
        val PageHorizontalInset = 24.dp
        val PageVerticalInset = 20.dp
        val HeaderBrandWidth = 224.dp
        val HeaderProjectWidth = 248.dp
        val HeaderIconSize = 48.dp
        val NavigationIconGap = 6.dp
        val RailHeaderHeight = 36.dp
        val PartRowHeight = 56.dp
        val PartRowVerticalPadding = 4.dp
        val PartThumbnailSize = 48.dp
        val DividerThickness = 1.dp
        const val DividerAlpha = 0.45f
    }

    /** Shared page-shell geometry measured from the focused page reference. */
    object Pages {
        val SidebarWidth = 168.dp
        val NavigationHeight = 40.dp
        val PageGap = 16.dp
        val ContentInset = 20.dp
        val OverviewTopHeight = 176.dp
        val OverviewTrackHeight = 226.dp
        val OverviewPreviewWidth = 420.dp
        val OverviewTransportHeight = 86.dp
        val CompactRowHeight = 38.dp
        val ImportDropHeight = 154.dp
        val VideoPreviewSceneHeight = 240.dp
    }

    /** Center workstation cards mirror the reference column without creating a second song clock. */
    object Center {
        val StructureHeight = 188.dp
        val ArrangementHeight = 268.dp
        val TimelineHeight = 315.dp
        val SectionBlockHeight = 74.dp
        val TimelineLaneHeight = 34.dp
        val LaneLabelWidth = 72.dp
        val ControlHeight = 28.dp
    }

    object Type {
        val Eyebrow = 11.sp
        val PartTitle = 13.sp
        val PartMetadata = 11.sp
        val HeaderProjectLabel = 10.sp
    }
    val Canvas = Color(0xFF110B1D)
    val Surface = Color(0xFF1B1329)
    val ElevatedSurface = Color(0xFF261A38)
    val SelectedSurface = Color(0xFF35244E)
    val Border = Color(0xFF4D3B66)
    /** Compatibility name retained for existing components; the product accent is purple. */
    val Teal = Color(0xFFC7A6FF)
    val OliveAccent = Color(0xFFE3D7FF)
    val TealFocus = Color(0xFFF0E9FF)
    val TealPressed = Color(0xFF9E7CDF)
    val TextPrimary = Color(0xFFF4EEFF)
    val TextSecondary = Color(0xFFD6CAE5)
    val Disabled = Color(0xFF978AA8)
    val Error = Color(0xFFFFB4AB)
    val Warning = Color(0xFFF0B356)
    val Information = Color(0xFF8AB4F8)
    val Loading = Color(0xFFC7A6FF)
    val Success = Color(0xFF78D8B6)
    val Piano = Color(0xFF59CCC4)
    val Bass = Color(0xFF86C979)
    val Drums = Color(0xFFF0B356)
    val Pad = Color(0xFFAB91EB)
    val Strings = Color(0xFFF08262)
    val ScenePlaceholder = Color(0xFF31234A)

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

private val workspaceTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

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
            listOf(MusicWorkspaceTokens.Canvas, MusicWorkspaceTokens.Surface, MusicWorkspaceTokens.SelectedSurface, MusicWorkspaceTokens.Teal, MusicWorkspaceTokens.Error).forEach { color ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(MusicWorkspaceTokens.Interaction.MinimumHitTarget).height(MusicWorkspaceTokens.Interaction.MinimumHitTarget).background(color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Control)))
            }
        }
        Text(
            "Ready · purple primary · Error · text labels remain available when colour is not.",
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
