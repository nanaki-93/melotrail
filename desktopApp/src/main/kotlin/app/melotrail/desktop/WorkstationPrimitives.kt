package app.melotrail.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stable semantic tags for the compact-control gallery and target workstation primitives. */
internal object WorkstationPrimitiveTags {
    const val GALLERY = "workstation-primitives-gallery"
    const val PRIMARY = "workstation-primitives-primary"
    const val SECONDARY = "workstation-primitives-secondary"
    const val ICON = "workstation-primitives-icon"
    const val NAVIGATION = "workstation-primitives-navigation"
    const val DISABLED = "workstation-primitives-disabled"
    const val DISCLOSURE = "workstation-primitives-disclosure"
    const val DISCLOSURE_CONTENT = "workstation-primitives-disclosure-content"
}

/** Render a compact, thin-bordered surface shared by target workstation pages. */
@Composable
internal fun WorkstationPanel(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Panel),
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
        border = BorderStroke(1.dp, MusicWorkspaceTokens.Border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
        ) {
            title?.let { WorkstationHeadingActionRow(title = it, action = action) }
            content()
        }
    }
}

/** Place a readable title and optional compact actions without widening the heading column. */
@Composable
internal fun WorkstationHeadingActionRow(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    summary: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            eyebrow?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.Primary)
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = MusicWorkspaceTokens.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm), content = action)
    }
}

/** Render the one filled action treatment for work that advances a MIDI workflow. */
@Composable
internal fun WorkstationPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    disabledReason: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val description = if (!enabled && disabledReason != null) "$label unavailable: $disabledReason" else contentDescription
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
            .onFocusChanged { focused = it.isFocused }
            .then(if (description == null) Modifier else Modifier.semantics { this.contentDescription = description }),
        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
        colors = workspacePrimaryButtonColors(),
        border = if (focused) BorderStroke(2.dp, MusicWorkspaceTokens.Focus) else BorderStroke(1.dp, MusicWorkspaceTokens.PrimaryFill),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Render the bordered action treatment for a safe alternative or supporting action. */
@Composable
internal fun WorkstationSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    disabledReason: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val description = if (!enabled && disabledReason != null) "$label unavailable: $disabledReason" else contentDescription
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
            .onFocusChanged { focused = it.isFocused }
            .then(if (description == null) Modifier else Modifier.semantics { this.contentDescription = description }),
        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MusicWorkspaceTokens.ElevatedSurface,
            contentColor = MusicWorkspaceTokens.TextPrimary,
            disabledContainerColor = MusicWorkspaceTokens.DisabledSurface,
            disabledContentColor = MusicWorkspaceTokens.Disabled,
        ),
        border = if (focused) BorderStroke(2.dp, MusicWorkspaceTokens.Focus) else BorderStroke(1.dp, MusicWorkspaceTokens.Border),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Render an accessible 48-dp icon action with an explicit text alternative. */
@Composable
internal fun WorkstationIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .sizeIn(
                minWidth = MusicWorkspaceTokens.Interaction.MinimumHitTarget,
                minHeight = MusicWorkspaceTokens.Interaction.MinimumHitTarget,
            )
            .clip(RoundedCornerShape(MusicWorkspaceTokens.Radius.Control))
            .background(if (focused) MusicWorkspaceTokens.SelectedSurface else MusicWorkspaceTokens.ElevatedSurface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MusicWorkspaceTokens.Focus else MusicWorkspaceTokens.Border,
                shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
            )
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) MusicWorkspaceTokens.TextPrimary else MusicWorkspaceTokens.Disabled)
    }
}

/** Render one icon-labelled route item with truthful selected state and keyboard activation. */
@Composable
internal fun WorkstationNavigationItem(
    label: String,
    summary: String,
    icon: WorkspaceVectorIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .then(if (compact) Modifier.widthIn(min = 116.dp) else Modifier.fillMaxWidth())
            .heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                this.selected = selected
                contentDescription = "Open $label. $summary${if (selected) " Selected." else ""}"
            },
        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                focused -> MusicWorkspaceTokens.SelectedSurface
                selected -> MusicWorkspaceTokens.SelectedSurface
                else -> MusicWorkspaceTokens.ElevatedSurface
            },
            contentColor = MusicWorkspaceTokens.TextPrimary,
        ),
        border = if (focused) BorderStroke(2.dp, MusicWorkspaceTokens.Focus) else BorderStroke(1.dp, MusicWorkspaceTokens.Border),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Icon(icon.image, contentDescription = null, modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Render a compact metric whose label remains available independently of its emphasized value. */
@Composable
internal fun WorkstationMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .background(MusicWorkspaceTokens.ElevatedSurface)
            .border(1.dp, MusicWorkspaceTokens.Border, RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .padding(MusicWorkspaceTokens.Spacing.Sm)
            .semantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MusicWorkspaceTokens.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MusicWorkspaceTokens.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The visual severity families available to compact status badges and inline messages. */
internal enum class WorkstationStatusTone(val color: Color, val label: String) {
    READY(MusicWorkspaceTokens.Success, "Ready"),
    NOTICE(MusicWorkspaceTokens.Information, "Notice"),
    WARNING(MusicWorkspaceTokens.Warning, "Warning"),
    BLOCKED(MusicWorkspaceTokens.Error, "Blocked"),
}

/** Render a text-labelled status marker so colour never carries its meaning alone. */
@Composable
internal fun WorkstationStatusBadge(
    tone: WorkstationStatusTone,
    label: String = tone.label,
    modifier: Modifier = Modifier,
) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .background(tone.color.copy(alpha = 0.16f))
            .border(1.dp, tone.color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .padding(horizontal = MusicWorkspaceTokens.Spacing.Sm, vertical = MusicWorkspaceTokens.Spacing.Xs)
            .semantics { contentDescription = "${tone.label}: $label" },
        style = MaterialTheme.typography.labelSmall,
        color = tone.color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Render an input with target border colours, a compact control radius, and a 48-dp minimum target. */
@Composable
internal fun WorkstationCompactTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = MusicWorkspaceTokens.Interaction.MinimumHitTarget),
        enabled = enabled,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(MusicWorkspaceTokens.Radius.Control),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MusicWorkspaceTokens.TextPrimary,
            unfocusedTextColor = MusicWorkspaceTokens.TextPrimary,
            disabledTextColor = MusicWorkspaceTokens.Disabled,
            focusedBorderColor = MusicWorkspaceTokens.Focus,
            unfocusedBorderColor = MusicWorkspaceTokens.Border,
            disabledBorderColor = MusicWorkspaceTokens.DisabledSurface,
            focusedLabelColor = MusicWorkspaceTokens.Focus,
            unfocusedLabelColor = MusicWorkspaceTokens.TextSecondary,
            disabledLabelColor = MusicWorkspaceTokens.Disabled,
            cursorColor = MusicWorkspaceTokens.Primary,
        ),
    )
}

/** Render a label/value selector row whose action remains keyboard accessible and truthfully disabled. */
@Composable
internal fun WorkstationSelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextSecondary)
        WorkstationSecondaryButton(
            label = value,
            onClick = onClick,
            enabled = enabled,
            disabledReason = disabledReason,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Render a bounded table row that keeps long evidence labels and metadata legible. */
@Composable
internal fun WorkstationTableRow(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .background(MusicWorkspaceTokens.ElevatedSurface)
            .border(1.dp, MusicWorkspaceTokens.Border, RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .padding(MusicWorkspaceTokens.Spacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text(primaryText, style = MaterialTheme.typography.bodyMedium, color = MusicWorkspaceTokens.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(secondaryText, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs), content = trailing)
    }
}

/** Render a textual status explanation that remains readable without relying on its colour. */
@Composable
internal fun WorkstationInlineMessage(
    title: String,
    message: String,
    tone: WorkstationStatusTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .background(tone.color.copy(alpha = 0.12f))
            .border(1.dp, tone.color, RoundedCornerShape(MusicWorkspaceTokens.Radius.Compact))
            .padding(MusicWorkspaceTokens.Spacing.Md)
            .semantics { contentDescription = "${tone.label}: $title. $message${detail?.let { " $it" } ?: ""}" },
        horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
    ) {
        WorkstationStatusBadge(tone = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MusicWorkspaceTokens.TextPrimary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MusicWorkspaceTokens.TextSecondary)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tone.color) }
        }
    }
}

/** Render a state-hoisted disclosure so advanced evidence stays out of the default workflow path. */
@Composable
internal fun WorkstationDisclosure(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
        WorkstationSecondaryButton(
            label = if (expanded) "Hide $label" else "Show $label",
            onClick = { onExpandedChange(!expanded) },
            contentDescription = "${if (expanded) "Collapse" else "Expand"} $label",
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        )
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().semantics { contentDescription = "$label details" },
                verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
                content = content,
            )
        }
    }
}

/** Render every target control state for deterministic desktop test coverage and local review. */
@Composable
internal fun WorkstationPrimitiveGallery() {
    var selected by remember { mutableStateOf(false) }
    var disclosureOpen by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf("Long source name that must not make the compact controls overlap") }
    Column(
        Modifier.fillMaxWidth().semantics { testTag = WorkstationPrimitiveTags.GALLERY },
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Md),
    ) {
        WorkstationPanel(title = "Compact controls") {
            Row(horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
                WorkstationPrimaryButton("Continue", { selected = true }, Modifier.weight(1f).semantics { testTag = WorkstationPrimitiveTags.PRIMARY })
                WorkstationSecondaryButton("Review", { selected = false }, Modifier.weight(1f).semantics { testTag = WorkstationPrimitiveTags.SECONDARY })
                WorkstationIconButton(WorkspaceVectorIcon.MIDI.image, "Open MIDI tools", { selected = !selected }, Modifier.semantics { testTag = WorkstationPrimitiveTags.ICON })
            }
            WorkstationSecondaryButton(
                label = "Export MIDI package",
                onClick = {},
                enabled = false,
                disabledReason = "Approve an arrangement first",
                modifier = Modifier.fillMaxWidth().semantics { testTag = WorkstationPrimitiveTags.DISABLED },
            )
            WorkstationNavigationItem(
                label = "Arrange",
                summary = "Create or repair only a selected section.",
                icon = WorkspaceVectorIcon.ARRANGE,
                selected = selected,
                onClick = { selected = !selected },
                modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.NAVIGATION },
            )
            WorkstationMetricTile("Tempo", "120 BPM")
            WorkstationStatusBadge(WorkstationStatusTone.READY, "MIDI source preserved")
            WorkstationCompactTextField("Project name", fieldValue, { fieldValue = it })
            WorkstationSelectRow("Arrangement style", "Late night", { selected = !selected })
            WorkstationTableRow("Protected melody track", "Track 1 · 960 PPQ")
            WorkstationInlineMessage("Authority required", "Confirm the structure and harmony before generating a draft.", WorkstationStatusTone.WARNING)
            WorkstationDisclosure(
                label = "Advanced evidence",
                expanded = disclosureOpen,
                onExpandedChange = { disclosureOpen = it },
                modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.DISCLOSURE },
            ) {
                Text(
                    "Only explicit advanced review reveals this detail.",
                    modifier = Modifier.semantics { testTag = WorkstationPrimitiveTags.DISCLOSURE_CONTENT },
                    color = MusicWorkspaceTokens.TextSecondary,
                )
            }
        }
    }
}
