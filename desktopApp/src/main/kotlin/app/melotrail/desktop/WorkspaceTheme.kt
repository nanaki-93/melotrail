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
    /** Transitional measurements retained only for the legacy shell until MC-051 removes it. */
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

    /** Geometry for the focused MIDI-only workspace. */
    object Layout {
        val WideBreakpoint = 1240.dp
        val NavigationWidth = 196.dp
        val ContextRailWidth = 256.dp
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
    /** Deep navy and violet language derived from the approved UI references. */
    val Canvas = Color(0xFF070A14)
    val Surface = Color(0xFF0E1422)
    val ElevatedSurface = Color(0xFF151D2E)
    val SelectedSurface = Color(0xFF2B2147)
    val Border = Color(0xFF323A50)
    val Primary = Color(0xFFA78BFA)
    val Focus = Color(0xFFC4B5FD)
    val WarmAccent = Color(0xFFF4BC64)
    val TextPrimary = Color(0xFFF4F2FF)
    val TextSecondary = Color(0xFFC2BED3)
    val Disabled = Color(0xFF9892A8)
    val DisabledSurface = Color(0xFF1B2030)
    val Error = Color(0xFFFFB4AB)
    val Warning = Color(0xFFF0B356)
    val Information = Color(0xFF8AB4F8)
    val Success = Color(0xFF7BDBA5)
    val Progress = Color(0xFF8DB8FF)
    val Piano = Color(0xFF65D6CE)
    val Bass = Color(0xFF86C979)
    val Drums = Color(0xFFF0B356)
    val Pad = Color(0xFFAB91EB)
    val Strings = Color(0xFFF08262)
    val ScenePlaceholder = Color(0xFF1A2032)

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

/** A colour is always paired with a readable text label and a compact icon. */
internal data class InstrumentLaneStyle(val color: Color, val label: String, val icon: String)

internal val instrumentLanes = mapOf(
    "piano" to InstrumentLaneStyle(MusicWorkspaceTokens.Piano, "Piano", "♫"),
    "bass" to InstrumentLaneStyle(MusicWorkspaceTokens.Bass, "Bass", "♩"),
    "drums" to InstrumentLaneStyle(MusicWorkspaceTokens.Drums, "Drums", "▣"),
    "pad" to InstrumentLaneStyle(MusicWorkspaceTokens.Pad, "Pad", "◇"),
    "strings" to InstrumentLaneStyle(MusicWorkspaceTokens.Strings, "Strings", "♬")
)

/** Use [instrumentLane] for new UI so lanes retain their text/icon equivalent. */
internal val instrumentLaneColors = instrumentLanes.mapValues { it.value.color }

internal fun instrumentLane(instrument: String): InstrumentLaneStyle? = instrumentLanes[instrument.lowercase()]

internal enum class WorkspaceSemanticState(val label: String, val icon: String) {
    READY("Ready", "✓"),
    WARNING("Review", "!"),
    ERROR("Blocked", "×"),
    INFORMATION("Information", "i"),
    DISABLED("Unavailable", "—"),
    SELECTED("Selected", "●"),
    PROGRESS("In progress", "…"),
    FOCUS("Focused", "◌")
}

internal val semanticStateColors = mapOf(
    WorkspaceSemanticState.READY to MusicWorkspaceTokens.Success,
    WorkspaceSemanticState.WARNING to MusicWorkspaceTokens.Warning,
    WorkspaceSemanticState.ERROR to MusicWorkspaceTokens.Error,
    WorkspaceSemanticState.INFORMATION to MusicWorkspaceTokens.Information,
    WorkspaceSemanticState.DISABLED to MusicWorkspaceTokens.Disabled,
    WorkspaceSemanticState.SELECTED to MusicWorkspaceTokens.Primary,
    WorkspaceSemanticState.PROGRESS to MusicWorkspaceTokens.Progress,
    WorkspaceSemanticState.FOCUS to MusicWorkspaceTokens.Focus
)

internal fun semanticColor(state: WorkspaceSemanticState): Color = semanticStateColors.getValue(state)

internal val musicColorScheme = darkColorScheme(
    primary = MusicWorkspaceTokens.Primary,
    onPrimary = MusicWorkspaceTokens.Canvas,
    primaryContainer = MusicWorkspaceTokens.SelectedSurface,
    onPrimaryContainer = MusicWorkspaceTokens.TextPrimary,
    secondary = MusicWorkspaceTokens.WarmAccent,
    onSecondary = MusicWorkspaceTokens.Canvas,
    secondaryContainer = Color(0xFF3A2D1B),
    onSecondaryContainer = MusicWorkspaceTokens.TextPrimary,
    tertiary = MusicWorkspaceTokens.Information,
    onTertiary = MusicWorkspaceTokens.Canvas,
    background = MusicWorkspaceTokens.Canvas,
    onBackground = MusicWorkspaceTokens.TextPrimary,
    surface = MusicWorkspaceTokens.Surface,
    onSurface = MusicWorkspaceTokens.TextPrimary,
    surfaceVariant = MusicWorkspaceTokens.ElevatedSurface,
    outline = MusicWorkspaceTokens.Border,
    onSurfaceVariant = MusicWorkspaceTokens.TextSecondary,
    error = MusicWorkspaceTokens.Error,
    onError = MusicWorkspaceTokens.Canvas,
    errorContainer = Color(0xFF482626),
    onErrorContainer = MusicWorkspaceTokens.TextPrimary
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
    containerColor = MusicWorkspaceTokens.Primary,
    contentColor = MusicWorkspaceTokens.Canvas,
    disabledContainerColor = MusicWorkspaceTokens.DisabledSurface,
    disabledContentColor = MusicWorkspaceTokens.Disabled
)

@Composable
internal fun workspaceSelectableButtonColors(selected: Boolean): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = if (selected) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.ElevatedSurface,
    contentColor = if (selected) MusicWorkspaceTokens.TextPrimary else MusicWorkspaceTokens.TextSecondary,
    disabledContainerColor = MusicWorkspaceTokens.DisabledSurface,
    disabledContentColor = MusicWorkspaceTokens.Disabled
)

/** Compact target-page heading shared by the six MIDI workspace destinations. */
@Composable
internal fun WorkspacePageHeading(eyebrow: String, title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MusicWorkspaceTokens.TextPrimary)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
    }
}

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
            listOf(MusicWorkspaceTokens.Canvas, MusicWorkspaceTokens.Surface, MusicWorkspaceTokens.SelectedSurface, MusicWorkspaceTokens.Primary, MusicWorkspaceTokens.Warning, MusicWorkspaceTokens.Error).forEach { color ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(MusicWorkspaceTokens.Interaction.MinimumHitTarget).height(MusicWorkspaceTokens.Interaction.MinimumHitTarget).background(color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Control)))
            }
        }
        Text(
            "Ready ✓ · Review ! · Blocked × · Selected ● · text and icons remain available when colour is not.",
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
