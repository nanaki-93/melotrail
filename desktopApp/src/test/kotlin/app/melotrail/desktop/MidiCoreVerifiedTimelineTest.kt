package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.width
import app.melotrail.application.MidiCoreVisualEvidence
import app.melotrail.application.MidiCoreVisualEvidenceAvailable
import app.melotrail.application.MidiCoreVisualEvidenceCacheStatus
import app.melotrail.application.MidiCoreVisualEvidenceCurrentness
import app.melotrail.application.MidiCoreVisualEvidenceEvent
import app.melotrail.application.MidiCoreVisualEvidenceIdentity
import app.melotrail.application.MidiCoreVisualEvidenceLane
import app.melotrail.application.MidiCoreVisualEvidenceProjection
import app.melotrail.application.MidiCoreVisualEvidenceScope
import app.melotrail.application.MidiCoreVisualEvidenceTiming
import app.melotrail.application.MidiCoreVisualEvidenceUnavailable
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionState
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreVerifiedTimelineTest {
    @Test
    fun `real semantic event ticks use the exact shared x axis at fit and zoom widths`() {
        val timing = timing()
        val event = MidiCoreVisualEvidenceEvent(480L, 960L, 0, 60, 96, false)

        assertEquals(120f, midiCoreTimelineX(event.startTick, timing, 480f))
        assertEquals(240f, midiCoreTimelineX(event.endTick, timing, 480f))
        assertEquals(300f, midiCoreTimelineX(event.startTick, timing, 1_200f))
        assertEquals(600f, midiCoreTimelineX(event.endTick, timing, 1_200f))
        assertEquals(0f, midiCoreTimelineX(-1L, timing, 480f))
        assertEquals(480f, midiCoreTimelineX(9_999L, timing, 480f))
    }

    @Test
    fun `rendered zoom and horizontal scroll keep notes loop and playhead on one x axis`() {
        val timing = timing()
        val zoomedContentWidth = 1_200f
        val scroll = 480f
        val noteStart = midiCoreTimelineViewportX(960L, timing, zoomedContentWidth, scroll)
        val loopStart = midiCoreTimelineViewportX(960L, timing, zoomedContentWidth, scroll)
        val playhead = midiCoreTimelineViewportX(960L, timing, zoomedContentWidth, scroll)
        val loopEnd = midiCoreTimelineViewportX(1_440L, timing, zoomedContentWidth, scroll)

        assertEquals(120f, noteStart)
        assertEquals(noteStart, loopStart)
        assertEquals(noteStart, playhead)
        assertEquals(420f, loopEnd)
    }

    @Test
    fun `fit uses the exact compact viewport and clipping keeps every fact inside the shared axis`() {
        val timing = timing()

        assertEquals(612f, midiCoreTimelineContentWidth(612f, 1f))
        assertEquals(765f, midiCoreTimelineContentWidth(612f, 1.25f))
        assertEquals(MidiCoreTimelineSpan(0f, 120f), midiCoreTimelineSpan(-240L, 480L, timing, 480f))
        assertEquals(MidiCoreTimelineSpan(360f, 120f), midiCoreTimelineSpan(1_440L, 3_000L, timing, 480f))
        assertEquals(MidiCoreTimelineSpan(480f, 0f), midiCoreTimelineSpan(2_000L, 3_000L, timing, 480f))
    }

    @Test
    fun `unrepresentable meter never supplies a zero bar interval to the timeline renderer`() {
        assertEquals<Long?>(null, midiCoreTimelineTicksPerBar(timing().copy(meterDenominatorExponent = 30)))
        assertEquals<Long?>(null, midiCoreTimelineTicksPerBar(timing().copy(meterDenominatorExponent = 64)))
        assertEquals<Long?>(null, midiCoreTimelineTicksPerBar(timing().copy(ppq = 101, meterDenominatorExponent = 3)))
        assertEquals<Long?>(null, midiCoreTimelineTicksPerBar(timing().copy(ppq = Int.MAX_VALUE, meterNumerator = Int.MAX_VALUE, meterDenominatorExponent = 0)))
        assertEquals(1_920L, midiCoreTimelineTicksPerBar(timing()))
    }

    @Test
    fun `active player scope chooses matching verified evidence before fallback`() {
        val source = available(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE)
        val candidate = available(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE)
        val draft = available(MidiCoreVisualEvidenceScope.DRAFT)
        val accepted = available(MidiCoreVisualEvidenceScope.ACCEPTED)
        val projection = MidiCoreVisualEvidenceProjection(source, candidate, draft, accepted)

        val visible = midiCoreVisibleTimelineEvidence(
            projection,
            MidiAuditionState(scope = MidiAuditionScope.Candidate("candidate-1", MidiExportRole.CHORDS)),
        )

        assertIs<MidiCoreVisualEvidence.Available>(visible)
        assertEquals(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE, visible.value.scope)

        val idle = assertIs<MidiCoreVisualEvidence.Available>(midiCoreVisibleTimelineEvidence(projection, MidiAuditionState()))
        assertEquals(MidiCoreVisualEvidenceScope.DRAFT, idle.value.scope)
    }

    @Test
    fun `active player scope exposes its unavailable evidence instead of silently drawing another scope`() {
        val unavailableCandidate = MidiCoreVisualEvidence.Unavailable(
            MidiCoreVisualEvidenceUnavailable(
                MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE,
                "CANDIDATE_EVIDENCE_UNAVAILABLE",
                "The selected candidate artifact does not match its digest.",
                "Restore it or choose another candidate.",
            ),
        )
        val projection = MidiCoreVisualEvidenceProjection(
            source = available(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE),
            selectedCandidate = unavailableCandidate,
            draft = available(MidiCoreVisualEvidenceScope.DRAFT),
            accepted = available(MidiCoreVisualEvidenceScope.ACCEPTED),
        )

        val visible = midiCoreVisibleTimelineEvidence(
            projection,
            MidiAuditionState(scope = MidiAuditionScope.Candidate("candidate-1", MidiExportRole.CHORDS)),
        )

        assertEquals(unavailableCandidate, visible)
    }

    @Test
    fun `timeline renders every factual role lane from immutable evidence`() = runComposeUiTest {
        val source = available(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE)
        val projection = MidiCoreVisualEvidenceProjection(
            source = source,
            selectedCandidate = available(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE),
            draft = available(MidiCoreVisualEvidenceScope.DRAFT),
            accepted = available(MidiCoreVisualEvidenceScope.ACCEPTED),
        )
        setContent {
            MelotrailTheme {
                MidiCoreVerifiedTimeline(
                    MidiCoreProject(ProjectId("timeline-project"), ProjectMetadata("Timeline", "2026-09-07T00:00:00Z")),
                    projection,
                    MidiAuditionState(),
                )
            }
        }

        onNodeWithTag(MidiCoreVerifiedTimelineTags.AXIS).assertExists()
        MidiExportRole.entries.forEach { role -> onNodeWithTag(MidiCoreVerifiedTimelineTags.lane(role)).assertExists() }
    }

    @Test
    fun `zoom control widens the actual shared axis and every rendered lane together`() = runComposeUiTest {
        val source = available(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE)
        val projection = MidiCoreVisualEvidenceProjection(
            source = source,
            selectedCandidate = available(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE),
            draft = available(MidiCoreVisualEvidenceScope.DRAFT),
            accepted = available(MidiCoreVisualEvidenceScope.ACCEPTED),
        )
        setContent {
            MelotrailTheme {
                MidiCoreVerifiedTimeline(
                    MidiCoreProject(ProjectId("timeline-project"), ProjectMetadata("Timeline", "2026-09-07T00:00:00Z")),
                    projection,
                    MidiAuditionState(),
                )
            }
        }

        val axisWidth = onNodeWithTag(MidiCoreVerifiedTimelineTags.AXIS).getUnclippedBoundsInRoot().width
        val melodyWidth = onNodeWithTag(MidiCoreVerifiedTimelineTags.lane(MidiExportRole.MELODY)).getUnclippedBoundsInRoot().width
        onNodeWithTag(MidiCoreVerifiedTimelineTags.ZOOM_IN).performClick()
        val zoomedAxisWidth = onNodeWithTag(MidiCoreVerifiedTimelineTags.AXIS).getUnclippedBoundsInRoot().width
        val zoomedMelodyWidth = onNodeWithTag(MidiCoreVerifiedTimelineTags.lane(MidiExportRole.MELODY)).getUnclippedBoundsInRoot().width

        assertEquals(axisWidth, melodyWidth)
        assertTrue(zoomedAxisWidth > axisWidth)
        assertEquals(zoomedAxisWidth, zoomedMelodyWidth)
    }

    private fun available(scope: MidiCoreVisualEvidenceScope): MidiCoreVisualEvidence.Available = MidiCoreVisualEvidence.Available(
        MidiCoreVisualEvidenceAvailable(
            scope = scope,
            identity = MidiCoreVisualEvidenceIdentity("timeline-project", "a".repeat(64), authorityHash = null),
            timing = timing(),
            lanes = MidiExportRole.entries.map { role ->
                MidiCoreVisualEvidenceLane(
                    role,
                    if (role == MidiExportRole.MELODY) listOf(MidiCoreVisualEvidenceEvent(480L, 960L, role.channel, 60, 96, false)) else emptyList(),
                )
            },
            currentness = MidiCoreVisualEvidenceCurrentness.CURRENT,
            cacheStatus = MidiCoreVisualEvidenceCacheStatus.COLD,
        ),
    )

    private fun timing() = MidiCoreVisualEvidenceTiming(480, 1_920L, 500_000, 4, 2, true)
}
