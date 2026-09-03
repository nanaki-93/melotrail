package app.melotrail.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.audition.MidiAuditionState
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectSectionOccurrence

/** Stable semantic anchors for the shared Arrange/Review song map. */
internal object MidiCoreSongMapTags {
    const val ROOT = "midi-core-song-map"
    const val TRACK = "midi-core-song-map-track"
    const val PREVIOUS = "midi-core-song-map-previous"
    const val NEXT = "midi-core-song-map-next"
    const val BLOCK_PREFIX = "midi-core-song-map-occurrence-"
    const val STATUS_PREFIX = "midi-core-song-map-status-"

    fun occurrence(id: String) = BLOCK_PREFIX + id
    fun status(occurrenceId: String, role: CandidateRole) = STATUS_PREFIX + occurrenceId + "-" + role.name.lowercase()
}

/** A textual role state prevents the map from communicating safety through colour alone. */
internal enum class MidiCoreSongMapRoleState(val label: String) {
    NOT_GENERATED("Not generated"),
    DRAFT("In draft"),
    ACCEPTED("Accepted"),
    STALE("Stale"),
    ATTENTION("Needs attention"),
}

/** One presentational occurrence block derived solely from persisted project records. */
internal data class MidiCoreSongMapOccurrence(
    val occurrence: ProjectSectionOccurrence,
    val displayLabel: String,
    val barRange: String,
    val barCount: Int,
    val chordSummary: String,
    val roleStates: Map<CandidateRole, MidiCoreSongMapRoleState>,
)

/** Build exact, bar-proportional song-map facts without adding another timeline owner. */
internal fun midiCoreSongMap(project: MidiCoreProject): List<MidiCoreSongMapOccurrence> {
    val authority = project.authority ?: return emptyList()
    val ppq = project.sourceMidi?.ppq ?: 480
    val ticksPerBar = ppq.toLong() * 4L * authority.meter.numerator / authority.meter.denominator
    if (ticksPerBar <= 0L) return emptyList()
    val labelCounts = authority.occurrences.groupingBy(ProjectSectionOccurrence::label).eachCount()
    val currentDraft = project.arrangementDrafts.lastOrNull { it.authorityHash == app.melotrail.project.MidiCoreAuthorityHasher.from(project).sha256 }
    return authority.occurrences.mapIndexed { index, occurrence ->
        val startBar = occurrence.startTick / ticksPerBar + 1L
        val endBar = occurrence.endTick / ticksPerBar
        val chords = authority.chordEvents.filter { it.occurrenceId == occurrence.id }
            .joinToString(" · ") { it.symbol }.ifBlank { "No chord summary" }
        val states = CandidateRole.entries.associateWith { role ->
            val accepted = project.acceptances.singleOrNull { it.occurrenceId == occurrence.id && it.role == role }
            val candidate = accepted?.let { acceptedCandidate -> project.candidates.singleOrNull { it.id == acceptedCandidate.candidateId } }
            when {
                candidate?.status == MidiCoreCandidateStatus.STALE -> MidiCoreSongMapRoleState.STALE
                accepted != null && candidate != null -> MidiCoreSongMapRoleState.ACCEPTED
                currentDraft?.candidateReferences?.any { it.occurrenceId == occurrence.id && it.role == role } == true -> MidiCoreSongMapRoleState.DRAFT
                project.candidates.any { it.occurrenceId == occurrence.id && it.role == role && it.status == MidiCoreCandidateStatus.STALE } -> MidiCoreSongMapRoleState.STALE
                project.candidates.any { it.occurrenceId == occurrence.id && it.role == role && it.status == MidiCoreCandidateStatus.REJECTED } -> MidiCoreSongMapRoleState.ATTENTION
                project.candidates.any { it.occurrenceId == occurrence.id && it.role == role } -> MidiCoreSongMapRoleState.ATTENTION
                else -> MidiCoreSongMapRoleState.NOT_GENERATED
            }
        }
        MidiCoreSongMapOccurrence(
            occurrence = occurrence,
            displayLabel = if (labelCounts.getValue(occurrence.label) > 1) {
                "${occurrence.label} ${authority.occurrences.take(index + 1).count { it.label == occurrence.label }}"
            } else occurrence.label,
            barRange = if (startBar == endBar) "Bar $startBar" else "Bars $startBar–$endBar",
            barCount = ((occurrence.endTick - occurrence.startTick) / ticksPerBar).toInt().coerceAtLeast(1),
            chordSummary = chords,
            roleStates = states,
        )
    }
}

/** The exact loop to use when a musician selects an authoritative map block. */
internal fun MidiCoreSongMapOccurrence.loop() = MidiAuditionLoop(occurrence.startTick, occurrence.endTick)

/** Shared horizontally scrollable song-map control for Arrange and Review. */
@Composable
internal fun MidiCoreSongMap(
    project: MidiCoreProject,
    selectedOccurrenceId: String?,
    audition: MidiAuditionState,
    onOccurrenceSelected: (MidiCoreSongMapOccurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val occurrences = midiCoreSongMap(project)
    Card(
        modifier.fillMaxWidth().semantics {
            testTag = MidiCoreSongMapTags.ROOT
            contentDescription = "Song map with ${occurrences.size} authoritative section occurrences"
        },
        colors = CardDefaults.cardColors(containerColor = MusicWorkspaceTokens.Surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(MusicWorkspaceTokens.Spacing.Md), verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm)) {
            Text("SONG MAP", color = MusicWorkspaceTokens.Primary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics {
                    testTag = MidiCoreSongMapTags.TRACK
                    contentDescription = "Horizontally navigable bar-proportional song map"
                },
                horizontalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Sm),
            ) {
                occurrences.forEach { item ->
                    val selected = item.occurrence.id == selectedOccurrenceId
                    val looped = audition.loop == item.loop()
                    val playing = audition.positionTick in item.occurrence.startTick..item.occurrence.endTick
                    OutlinedButton(
                        onClick = { onOccurrenceSelected(item) },
                        colors = workspaceSelectableButtonColors(selected),
                        modifier = Modifier.width((item.barCount * 52).coerceAtLeast(148).dp)
                            .heightIn(min = 132.dp)
                            .semantics {
                                testTag = MidiCoreSongMapTags.occurrence(item.occurrence.id)
                                this.selected = selected
                                contentDescription = buildString {
                                    append("${item.displayLabel}, ${item.barRange}, ${item.chordSummary}. ")
                                    append(item.roleStates.entries.joinToString("; ") { "${it.key.displayName}: ${it.value.label}" })
                                    if (selected) append(" Selected.")
                                    if (looped) append(" Loop selected.")
                                    if (playing) append(" Playhead here.")
                                }
                            },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(MusicWorkspaceTokens.Spacing.Xs)) {
                            Text(item.displayLabel)
                            Text(item.barRange, color = MusicWorkspaceTokens.TextSecondary)
                            Text(item.chordSummary, color = MusicWorkspaceTokens.TextSecondary, maxLines = 2)
                            item.roleStates.forEach { (role, status) ->
                                Text(
                                    "${role.displayName}: ${status.label}",
                                    modifier = Modifier.semantics { testTag = MidiCoreSongMapTags.status(item.occurrence.id, role) },
                                    color = when (status) {
                                        MidiCoreSongMapRoleState.ACCEPTED -> MusicWorkspaceTokens.Success
                                        MidiCoreSongMapRoleState.STALE, MidiCoreSongMapRoleState.ATTENTION -> MusicWorkspaceTokens.Warning
                                        else -> MusicWorkspaceTokens.TextSecondary
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
