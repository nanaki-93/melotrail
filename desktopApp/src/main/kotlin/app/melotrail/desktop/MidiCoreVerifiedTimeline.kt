package app.melotrail.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.melotrail.application.MidiCoreVisualEvidence
import app.melotrail.application.MidiCoreVisualEvidenceAvailable
import app.melotrail.application.MidiCoreVisualEvidenceProjection
import app.melotrail.application.MidiCoreVisualEvidenceScope
import app.melotrail.application.MidiCoreVisualEvidenceUnavailable
import app.melotrail.application.MidiCoreVisualEvidenceTiming
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectSectionOccurrence
import kotlinx.coroutines.launch

/** Stable semantic anchors for verified MIDI lanes, not screenshot artwork. */
internal object MidiCoreVerifiedTimelineTags {
    const val ROOT = "midi-core-verified-timeline"
    const val AXIS = "midi-core-verified-timeline-axis"
    const val FIT = "midi-core-verified-timeline-fit"
    const val ZOOM_IN = "midi-core-verified-timeline-zoom-in"
    const val ZOOM_OUT = "midi-core-verified-timeline-zoom-out"
    const val UNAVAILABLE = "midi-core-verified-timeline-unavailable"
    const val LANE_PREFIX = "midi-core-verified-timeline-lane-"

    fun lane(role: MidiExportRole) = LANE_PREFIX + role.name.lowercase()
}

private val TimelineLabelWidth = 76.dp

/** One shared tick-to-x rule for notes, bars, chords, loop shading and the real playhead. */
internal data class MidiCoreTimelineGeometry(
    val timing: MidiCoreVisualEvidenceTiming,
    val contentWidthPx: Float,
) {
    init {
        require(contentWidthPx > 0f) { "Timeline width must be positive" }
    }

    fun x(tick: Long): Float = tick.coerceIn(0L, timing.songEndTick).toFloat() / timing.songEndTick * contentWidthPx
}

/** Resolve exact x positions independently of Compose so all timeline facts stay aligned under fit and zoom. */
internal fun midiCoreTimelineX(tick: Long, timing: MidiCoreVisualEvidenceTiming, contentWidthPx: Float): Float =
    MidiCoreTimelineGeometry(timing, contentWidthPx).x(tick)

/**
 * Maps the shared content coordinate into the visible horizontally-scrolled viewport.
 * Compose applies precisely this scroll translation to the axis and every lane, so
 * notes, loop edges, and the observed playhead keep one x-axis at every zoom level.
 */
internal fun midiCoreTimelineViewportX(
    tick: Long,
    timing: MidiCoreVisualEvidenceTiming,
    contentWidthPx: Float,
    scrollPx: Float,
): Float {
    require(scrollPx >= 0f) { "Timeline scroll must not be negative" }
    return midiCoreTimelineX(tick, timing, contentWidthPx) - scrollPx
}

/** A clipped shared-axis span used by chords, notes, loops and section selection. */
internal data class MidiCoreTimelineSpan(val startX: Float, val width: Float)

internal fun midiCoreTimelineSpan(
    startTick: Long,
    endTick: Long,
    timing: MidiCoreVisualEvidenceTiming,
    contentWidthPx: Float,
    minimumWidthPx: Float = 1f,
): MidiCoreTimelineSpan {
    require(endTick > startTick) { "Timeline span must have positive duration" }
    require(minimumWidthPx >= 0f) { "Timeline minimum width must not be negative" }
    val start = midiCoreTimelineX(startTick, timing, contentWidthPx)
    val end = midiCoreTimelineX(endTick, timing, contentWidthPx)
    val availableWidth = (contentWidthPx - start).coerceAtLeast(0f)
    return MidiCoreTimelineSpan(start, (end - start).coerceAtLeast(minimumWidthPx).coerceAtMost(availableWidth))
}

/** Fit is exactly the available viewport; zoom scales that same shared coordinate space. */
internal fun midiCoreTimelineContentWidth(viewportWidth: Float, zoom: Float): Float {
    require(viewportWidth >= 0f && zoom > 0f) { "Timeline viewport and zoom are invalid" }
    return (viewportWidth * zoom).coerceAtLeast(1f)
}

/** Returns a representable bar size, or null when the imported meter has no whole tick grid. */
internal fun midiCoreTimelineTicksPerBar(timing: MidiCoreVisualEvidenceTiming): Long? {
    if (timing.meterDenominatorExponent >= 63) return null
    val denominator = 1L shl timing.meterDenominatorExponent
    val quarterTicks = timing.ppq.toLong() * 4L
    if (quarterTicks % denominator != 0L) return null
    val ticksPerBeat = quarterTicks / denominator
    if (ticksPerBeat == 0L || ticksPerBeat > Long.MAX_VALUE / timing.meterNumerator) return null
    return ticksPerBeat * timing.meterNumerator
}

/** Select evidence that corresponds to the active player when possible, then prefer draft, accepted, candidate, source. */
internal fun midiCoreVisibleTimelineEvidence(
    projection: MidiCoreVisualEvidenceProjection?,
    audition: MidiAuditionState,
): MidiCoreVisualEvidence? {
    projection ?: return null
    fun candidateEvidence(candidateId: String): MidiCoreVisualEvidence {
        val evidence = projection.selectedCandidate
        if (evidence is MidiCoreVisualEvidence.Available &&
            evidence.value.identity.candidateIds == listOf(candidateId)) return evidence
        return MidiCoreVisualEvidence.Unavailable(MidiCoreVisualEvidenceUnavailable(
            MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
            "PLAYING_CANDIDATE_EVIDENCE_MISMATCH",
            "Verified lanes for the alternative in the player are unavailable.",
            "Review the selected alternative’s findings, then play it to align lanes with playback.",
        ))
    }
    val fromScope = when (val scope = audition.scope) {
        is MidiAuditionScope.Candidate -> candidateEvidence(scope.candidateId)
        is MidiAuditionScope.ArrangementDraft -> projection.draft
        MidiAuditionScope.AcceptedArrangement, is MidiAuditionScope.Role -> projection.accepted
        MidiAuditionScope.SourceMelody -> projection.source
        is MidiAuditionScope.Occurrence -> scope.candidateId?.let(::candidateEvidence) ?: projection.source
        is MidiAuditionScope.StylePreview, null -> null
    }
    // Never hide a stale/missing/digest failure for the scope the player actually selected.
    if (fromScope != null) return fromScope
    return listOf(projection.draft, projection.accepted, projection.selectedCandidate, projection.source)
        .firstOrNull { it is MidiCoreVisualEvidence.Available }
}

/** Shared factual four-lane map for Arrange and Review. It consumes only reducer-owned verified data. */
@Composable
internal fun MidiCoreVerifiedTimeline(
    project: MidiCoreProject,
    projection: MidiCoreVisualEvidenceProjection?,
    audition: MidiAuditionState,
    selectedOccurrenceId: String? = null,
    modifier: Modifier = Modifier,
) {
    val evidence = midiCoreVisibleTimelineEvidence(projection, audition)
    Card(
        modifier.fillMaxWidth().semantics {
            testTag = MidiCoreVerifiedTimelineTags.ROOT
            contentDescription = "Verified four-lane MIDI timeline"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        when (evidence) {
            is MidiCoreVisualEvidence.Available -> TimelineAvailable(project, evidence.value, audition, selectedOccurrenceId)
            is MidiCoreVisualEvidence.Unavailable -> TimelineUnavailable(evidence)
            null -> Text(
                "Verified MIDI lanes are preparing from the current project.",
                modifier = Modifier.padding(MusicWorkspaceTokens.Spacing.Md).semantics { testTag = MidiCoreVerifiedTimelineTags.UNAVAILABLE },
                color = MusicWorkspaceTokens.TextSecondary,
            )
        }
    }
}

@Composable
private fun TimelineUnavailable(evidence: MidiCoreVisualEvidence.Unavailable) {
    Text(
        "${evidence.value.message} Next: ${evidence.value.nextAction}",
        modifier = Modifier.padding(MusicWorkspaceTokens.Spacing.Md).semantics { testTag = MidiCoreVerifiedTimelineTags.UNAVAILABLE },
        color = MusicWorkspaceTokens.Warning,
    )
}

@Composable
private fun TimelineAvailable(
    project: MidiCoreProject,
    evidence: MidiCoreVisualEvidenceAvailable,
    audition: MidiAuditionState,
    selectedOccurrenceId: String?,
) {
    var zoom by remember(evidence.identity, evidence.scope) { mutableFloatStateOf(1f) }
    val scroll = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val selectedOccurrence = project.authority?.occurrences?.singleOrNull { it.id == selectedOccurrenceId }
    Column(
        Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md),
        verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text("VERIFIED MIDI LANES · ${scopeLabel(evidence.scope)}", modifier = Modifier.weight(1f), color = MusicWorkspaceTokens.Primary)
            OutlinedButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.5f) }, modifier = Modifier.semantics { testTag = MidiCoreVerifiedTimelineTags.ZOOM_OUT }) { Text("−") }
            OutlinedButton(
                onClick = {
                    zoom = 1f
                    coroutineScope.launch { scroll.scrollTo(0) }
                },
                modifier = Modifier.semantics { testTag = MidiCoreVerifiedTimelineTags.FIT },
            ) { Text("Fit") }
            OutlinedButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(4f) }, modifier = Modifier.semantics { testTag = MidiCoreVerifiedTimelineTags.ZOOM_IN }) { Text("+") }
        }
        Text(
            buildString {
                append(if (evidence.currentness.name == "CURRENT") "Current" else "Stale")
                append(" verified evidence · per-lane pitch scale")
                selectedOccurrence?.let { append(" · ${it.label} selected") }
            },
            color = if (evidence.currentness.name == "CURRENT") MusicWorkspaceTokens.TextSecondary else MusicWorkspaceTokens.Warning,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val timelineViewportWidth = (maxWidth - TimelineLabelWidth).coerceAtLeast(0.dp)
            val contentWidth = midiCoreTimelineContentWidth(timelineViewportWidth.value, zoom).dp
            val geometry = MidiCoreTimelineGeometry(evidence.timing, with(LocalDensity.current) { contentWidth.toPx() })
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(TimelineLabelWidth)) {
                    Text("BARS", color = MusicWorkspaceTokens.TextSecondary, modifier = Modifier.height(40.dp))
                    MidiExportRole.entries.forEach { role ->
                        Text(role.trackName.uppercase(), color = roleColor(role), modifier = Modifier.height(52.dp).padding(top = 16.dp))
                    }
                }
                Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                    TimelineAxis(project, geometry, selectedOccurrence, contentWidth)
                    MidiExportRole.entries.forEach { role ->
                        val events = evidence.lanes.singleOrNull { it.role == role }?.events.orEmpty()
                        TimelineLane(role, events, geometry, audition, selectedOccurrence, contentWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineAxis(
    project: MidiCoreProject,
    geometry: MidiCoreTimelineGeometry,
    selectedOccurrence: ProjectSectionOccurrence?,
    width: Dp,
) {
    val authority = project.authority
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        Modifier.width(width).height(40.dp).semantics {
            testTag = MidiCoreVerifiedTimelineTags.AXIS
            contentDescription = buildString {
                append("Shared bar and chord axis")
                authority?.chordEvents?.takeIf { it.isNotEmpty() }?.let { chords ->
                    append(": ")
                    append(chords.joinToString { "${it.symbol} at ${it.startTick}" })
                }
                selectedOccurrence?.let { append(", ${it.label} selected from tick ${it.startTick} to ${it.endTick}") }
            }
        },
    ) {
        drawRect(MusicWorkspaceTokens.ElevatedSurface)
        authority?.chordEvents?.forEachIndexed { index, chord ->
            val span = midiCoreTimelineSpan(chord.startTick, chord.endTick, geometry.timing, geometry.contentWidthPx)
            drawRect(sectionColor(index).copy(alpha = 0.34f), Offset(span.startX, 0f), Size(span.width, size.height))
        }
        selectedOccurrence?.let { occurrence ->
            val selection = midiCoreTimelineSpan(occurrence.startTick, occurrence.endTick, geometry.timing, geometry.contentWidthPx)
            drawRect(MusicWorkspaceTokens.PrimaryFill.copy(alpha = 0.14f), Offset(selection.startX, 0f), Size(selection.width, size.height))
            drawLine(MusicWorkspaceTokens.Focus, Offset(selection.startX, 0f), Offset(selection.startX, size.height), strokeWidth = 2f)
            drawLine(MusicWorkspaceTokens.Focus, Offset(selection.startX + selection.width, 0f), Offset(selection.startX + selection.width, size.height), strokeWidth = 2f)
        }
        midiCoreTimelineTicksPerBar(geometry.timing)?.let { ticksPerBar ->
            var tick = 0L
            var bar = 1
            while (tick <= geometry.timing.songEndTick) {
                val x = geometry.x(tick)
                drawLine(MusicWorkspaceTokens.Border, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                if (tick < geometry.timing.songEndTick) {
                    drawText(
                        textMeasurer,
                        "Bar $bar",
                        Offset(x + 4f, 1f),
                        TextStyle(color = MusicWorkspaceTokens.TextSecondary, fontSize = 9.sp),
                        maxLines = 1,
                    )
                }
                if (tick > geometry.timing.songEndTick - ticksPerBar) break
                tick += ticksPerBar
                bar += 1
            }
        }
        authority?.chordEvents?.forEach { chord ->
            val span = midiCoreTimelineSpan(chord.startTick, chord.endTick, geometry.timing, geometry.contentWidthPx)
            if (span.width >= 12f) {
                drawText(
                    textMeasurer,
                    chord.symbol,
                    Offset(span.startX + 4f, 18f),
                    TextStyle(color = MusicWorkspaceTokens.TextPrimary, fontSize = 10.sp),
                    maxLines = 1,
                    size = Size((span.width - 8f).coerceAtLeast(1f), 20f),
                )
            }
        }
    }
}

@Composable
private fun TimelineLane(
    role: MidiExportRole,
    events: List<app.melotrail.application.MidiCoreVisualEvidenceEvent>,
    geometry: MidiCoreTimelineGeometry,
    audition: MidiAuditionState,
    selectedOccurrence: ProjectSectionOccurrence?,
    width: Dp,
) {
    Canvas(
        Modifier.width(width).height(52.dp).semantics {
            testTag = MidiCoreVerifiedTimelineTags.lane(role)
            contentDescription = "${role.trackName} verified MIDI lane with ${events.size} events"
        },
    ) {
        drawRect(MusicWorkspaceTokens.ElevatedSurface)
        selectedOccurrence?.let { occurrence ->
            val selection = midiCoreTimelineSpan(occurrence.startTick, occurrence.endTick, geometry.timing, geometry.contentWidthPx)
            drawRect(MusicWorkspaceTokens.PrimaryFill.copy(alpha = 0.10f), Offset(selection.startX, 0f), Size(selection.width, size.height))
        }
        val pitchRange = events.minOfOrNull { it.pitch }?.let { low -> low..requireNotNull(events.maxOfOrNull { it.pitch }) }
        events.forEach { event ->
            val span = midiCoreTimelineSpan(event.startTick, event.endTick, geometry.timing, geometry.contentWidthPx, minimumWidthPx = 2f)
            val y = pitchRange?.let { range ->
                val span = (range.last - range.first).coerceAtLeast(1)
                6f + (range.last - event.pitch).toFloat() / span * (size.height - 18f)
            } ?: size.height / 2f
            val height = if (event.percussion) 8f else 6f
            drawRect(roleColor(role), Offset(span.startX, y), Size(span.width, height))
        }
        audition.loop?.let { loop ->
            val span = midiCoreTimelineSpan(loop.startTick, loop.endTick, geometry.timing, geometry.contentWidthPx)
            drawRect(MusicWorkspaceTokens.PrimaryFill.copy(alpha = 0.16f), Offset(span.startX, 0f), Size(span.width, size.height))
        }
        val playhead = geometry.x(audition.positionTick)
        drawLine(MusicWorkspaceTokens.Focus, Offset(playhead, 0f), Offset(playhead, size.height), strokeWidth = 2f)
    }
}

private fun roleColor(role: MidiExportRole): Color = when (role) {
    MidiExportRole.MELODY -> MusicWorkspaceTokens.Role.Melody
    MidiExportRole.CHORDS -> MusicWorkspaceTokens.Role.Chords
    MidiExportRole.BASS -> MusicWorkspaceTokens.Role.Bass
    MidiExportRole.DRUMS -> MusicWorkspaceTokens.Role.Drums
}

private fun scopeLabel(scope: MidiCoreVisualEvidenceScope): String = when (scope) {
    MidiCoreVisualEvidenceScope.PROTECTED_SOURCE -> "Source"
    MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE -> "Selected candidate"
    MidiCoreVisualEvidenceScope.DRAFT -> "Draft"
    MidiCoreVisualEvidenceScope.ACCEPTED -> "Accepted"
}
