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
import androidx.compose.material3.Icon
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.melotrail.project.CandidateRole

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
    /** UI-001's measured target palette. Its bright accent is intentionally not a primary fill. */
    val Canvas = Color(0xFF0B131E)
    val Surface = Color(0xFF101923)
    val ElevatedSurface = Color(0xFF151E2A)
    val SelectedSurface = Color(0xFF2C2442)
    val Border = Color(0xFF26303E)
    val PrimaryFill = Color(0xFF594080)
    val Primary = Color(0xFFB18ADE)
    val Focus = Color(0xFFD4B8FF)
    val WarmAccent = Color(0xFFD79A43)
    val TextPrimary = Color(0xFFF1F2F4)
    val TextSecondary = Color(0xFFAFB3BE)
    val Disabled = Color(0xFFB5B8C0)
    val DisabledSurface = Color(0xFF252B35)
    val Error = Color(0xFFFFB4AB)
    val Warning = Color(0xFFF0B356)
    val Information = Color(0xFF9BC6FF)
    val Success = Color(0xFF8FE0AF)
    val Progress = Color(0xFFA9C8FF)
    val ScenePlaceholder = Color(0xFF172331)

    /** Four target roles only. Legacy Piano/Pad/Strings palette names do not belong to MIDI Core. */
    object Role {
        val Melody = Color(0xFFD77A9E)
        val Chords = Color(0xFF9CAA61)
        val Bass = Color(0xFF5A9DD2)
        val Drums = Color(0xFFD79A43)
    }

    /** Stable section families are not validation severities and always carry textual labels. */
    val SectionColors = listOf(
        Color(0xFF6B7955),
        Color(0xFF8D6331),
        Color(0xFF685281),
        Color(0xFF86515F),
        Color(0xFF4D6682),
    )

    object Spacing {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
    }

    object Radius {
        val Compact = 6.dp
        val Control = 6.dp
        val Card = 8.dp
        val Panel = 8.dp
    }

    object Interaction {
        val MinimumHitTarget = 48.dp
        const val HoverAlpha = 0.12f
        const val PressedAlpha = 0.22f
        const val DisabledAlpha = 0.38f
    }
}

/**
 * The old router still compiles while MC-051 removes it. Keep its role labels
 * isolated from the target MIDI Core vocabulary so new UI never inherits Piano,
 * Pad or Strings as an arrangement role.
 */
private object LegacyInstrumentPalette {
    val Piano = Color(0xFF65D6CE)
    val Bass = Color(0xFF86C979)
    val Drums = Color(0xFFF0B356)
    val Pad = Color(0xFFAB91EB)
    val Strings = Color(0xFFF08262)
}

/** Legacy-only text-glyph style. New target components consume [MidiWorkspaceRoleStyle]. */
internal data class InstrumentLaneStyle(val color: Color, val label: String, val icon: String)

internal val instrumentLanes = mapOf(
    "piano" to InstrumentLaneStyle(LegacyInstrumentPalette.Piano, "Piano", "♫"),
    "bass" to InstrumentLaneStyle(LegacyInstrumentPalette.Bass, "Bass", "♩"),
    "drums" to InstrumentLaneStyle(LegacyInstrumentPalette.Drums, "Drums", "▣"),
    "pad" to InstrumentLaneStyle(LegacyInstrumentPalette.Pad, "Pad", "◇"),
    "strings" to InstrumentLaneStyle(LegacyInstrumentPalette.Strings, "Strings", "♬")
)

internal val instrumentLaneColors = instrumentLanes.mapValues { it.value.color }

internal fun instrumentLane(instrument: String): InstrumentLaneStyle? = instrumentLanes[instrument.lowercase()]

/** A single vector-icon registry gives every target icon a stable accessible name. */
internal enum class WorkspaceVectorIcon(val image: ImageVector, val contentDescription: String) {
    PROJECT(Icons.Default.Folder, "Project"),
    MIDI(Icons.Default.LibraryMusic, "MIDI"),
    STRUCTURE(Icons.Default.ViewWeek, "Structure and harmony"),
    ARRANGE(Icons.AutoMirrored.Filled.QueueMusic, "Arrange"),
    REVIEW(Icons.Default.RateReview, "Review"),
    EXPORT(Icons.Default.UploadFile, "Export MIDI package"),
    MELODY(Icons.Default.MusicNote, "Melody role"),
    CHORDS(Icons.Default.LibraryMusic, "Chords role"),
    BASS(Icons.AutoMirrored.Filled.QueueMusic, "Bass role"),
    DRUMS(Icons.Default.Album, "Drums role"),
}

/** Target-only roles for all MIDI evidence lanes, never an instrument claim. */
internal enum class MidiWorkspaceRoleStyle(
    val label: String,
    val color: Color,
    val icon: WorkspaceVectorIcon,
) {
    MELODY("Melody", MusicWorkspaceTokens.Role.Melody, WorkspaceVectorIcon.MELODY),
    CHORDS("Chords", MusicWorkspaceTokens.Role.Chords, WorkspaceVectorIcon.CHORDS),
    BASS("Bass", MusicWorkspaceTokens.Role.Bass, WorkspaceVectorIcon.BASS),
    DRUMS("Drums", MusicWorkspaceTokens.Role.Drums, WorkspaceVectorIcon.DRUMS),
}

/** Resolve a generated core role to its target-only MIDI evidence style. */
internal fun midiWorkspaceRoleStyle(role: CandidateRole): MidiWorkspaceRoleStyle = when (role) {
    CandidateRole.CHORDS -> MidiWorkspaceRoleStyle.CHORDS
    CandidateRole.BASS -> MidiWorkspaceRoleStyle.BASS
    CandidateRole.DRUMS -> MidiWorkspaceRoleStyle.DRUMS
}

/** Return one stable non-severity section family for the supplied zero-based occurrence index. */
internal fun sectionColor(index: Int): Color = MusicWorkspaceTokens.SectionColors[index.mod(MusicWorkspaceTokens.SectionColors.size)]

internal enum class WorkspaceSemanticState(val label: String) {
    READY("Ready"),
    WARNING("Review"),
    ERROR("Blocked"),
    INFORMATION("Information"),
    DISABLED("Unavailable"),
    SELECTED("Selected"),
    PROGRESS("In progress"),
    FOCUS("Focused")
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
    primary = MusicWorkspaceTokens.PrimaryFill,
    onPrimary = MusicWorkspaceTokens.TextPrimary,
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

/**
 * System Sans Serif is deliberately the current fallback: no unknown font is
 * bundled merely to mimic a supplied image. UI-017 pins the host/capture font;
 * a redistributable bundled font can replace this only with license evidence.
 */
internal val workspaceFontFamily = FontFamily.SansSerif

/** Build a fixed type slot from the target point-size, line-height and family contract. */
private fun workspaceText(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, fontFamily = workspaceFontFamily)

/** Every Material type slot used by the workspace is explicitly sized. */
internal val workspaceTypography = Typography(
    displayLarge = workspaceText(30, 36, FontWeight.SemiBold),
    displayMedium = workspaceText(28, 34, FontWeight.SemiBold),
    displaySmall = workspaceText(26, 32, FontWeight.SemiBold),
    headlineLarge = workspaceText(24, 30, FontWeight.SemiBold),
    headlineMedium = workspaceText(22, 28, FontWeight.SemiBold),
    headlineSmall = workspaceText(20, 26, FontWeight.SemiBold),
    titleLarge = workspaceText(18, 24, FontWeight.SemiBold),
    titleMedium = workspaceText(16, 22, FontWeight.Medium),
    titleSmall = workspaceText(14, 20, FontWeight.Medium),
    bodyLarge = workspaceText(14, 20),
    bodyMedium = workspaceText(13, 18),
    bodySmall = workspaceText(12, 16),
    labelLarge = workspaceText(13, 18, FontWeight.Medium),
    labelMedium = workspaceText(12, 16, FontWeight.Medium),
    labelSmall = workspaceText(11, 16, FontWeight.Medium),
)

@Composable
internal fun workspacePrimaryButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MusicWorkspaceTokens.PrimaryFill,
    contentColor = MusicWorkspaceTokens.TextPrimary,
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
    WorkstationHeadingActionRow(eyebrow = eyebrow, title = title, summary = summary)
}

object ThemeShowcaseTags {
    const val ROOT = "theme-showcase"
    const val PALETTE = "theme-showcase-palette"
    const val ROLE_PALETTE = "theme-showcase-role-palette"
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
            listOf(MusicWorkspaceTokens.Canvas, MusicWorkspaceTokens.Surface, MusicWorkspaceTokens.SelectedSurface, MusicWorkspaceTokens.PrimaryFill, MusicWorkspaceTokens.Primary, MusicWorkspaceTokens.Warning, MusicWorkspaceTokens.Error).forEach { color ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(MusicWorkspaceTokens.Interaction.MinimumHitTarget).height(MusicWorkspaceTokens.Interaction.MinimumHitTarget).background(color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Control)))
            }
        }
        Row(
            modifier = androidx.compose.ui.Modifier.semantics { testTag = ThemeShowcaseTags.ROLE_PALETTE },
            horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
        ) {
            MidiWorkspaceRoleStyle.entries.forEach { role ->
                Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                    Icon(role.icon.image, contentDescription = role.icon.contentDescription, tint = role.color)
                    Text(role.label, color = MusicWorkspaceTokens.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            "Ready · Review · Blocked · Selected · text and vector icons remain available when colour is not.",
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
