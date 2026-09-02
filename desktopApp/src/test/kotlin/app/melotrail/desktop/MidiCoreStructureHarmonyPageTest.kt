package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreStructureHarmonyPageTest {
    @Test
    fun `page exposes explicit authority exact timelines coverage and chromatic advisories`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = authorityState(),
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.AUTHORITY_STATUS).assertExists()
        onNodeWithText("Exact range: bar 1 · beat 1 · tick 0 → bar 1 · beat 3 · tick 0 · 0–960 ticks").assertExists()
        onNodeWithText("Exact range: bar 1 · beat 3 · tick 0 → bar 2 · beat 1 · tick 0 · 960–1920 ticks").assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.HARMONY_FINDINGS).assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.finding("CHROMATIC_CHORD")).assertExists()
    }

    @Test
    fun `page routes authority mutations and both source and occurrence audition`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = authorityState(),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.KEY).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.MODE).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.CONFIRM_AUTHORITY).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_STRUCTURE).performScrollTo().assertIsEnabled()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_HARMONY).performScrollTo().assertIsEnabled()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SOURCE_AUDITION).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.occurrenceAudition("verse-1")).performScrollTo().performClick()

        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.UpdateAuthorityDraft(
                    MidiCoreAuthorityDraft(
                        ProjectKey(ProjectKeySpelling.C_SHARP, ProjectScaleMode.MAJOR),
                        ProjectTempo(500_000),
                        ProjectMeter(4, 2),
                    ),
                ),
                MidiCoreWorkspaceIntent.UpdateAuthorityDraft(
                    MidiCoreAuthorityDraft(
                        ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.NATURAL_MINOR),
                        ProjectTempo(500_000),
                        ProjectMeter(4, 2),
                    ),
                ),
                MidiCoreWorkspaceIntent.ConfirmAuthority,
                MidiCoreWorkspaceIntent.PlaySourceMelody,
                MidiCoreWorkspaceIntent.PlayOccurrence("verse-1"),
            ),
            intents,
        )
    }

    @Test
    fun `page blocks a mismatched bar total and still permits additive authoring`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = authorityState(),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.occurrenceBars(1)).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.ADD_DEFINITION).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.ADD_OCCURRENCE).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.ADD_CHORD).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.definition(2)).assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.occurrence(2)).assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.chord(4)).assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.STRUCTURE_FINDINGS).assertExists()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun `page shows the last invalidation and restart-ready persisted authority`() = runComposeUiTest {
        val project = authorityState().project!!
        val before = MidiCoreAuthorityHasher.from(project)
        val after = MidiCoreAuthorityHasher.from(project.copy(authority = project.authority!!.copy(tempo = ProjectTempo(400_000))))
        val preview = MidiCoreInvalidationPlanner.preview(before, after)
        val state = authorityState().copy(
            authority = authorityState().authority.copy(lastInvalidation = preview),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = state,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithText("Last saved authority change marked the following derived work stale.").assertExists()
        onNodeWithText("Changed dimensions: timing").assertExists()
        onNodeWithText("Confirmed: C major, 4/4, 120.00 BPM.").assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.RECOVERY).assertDoesNotExist()
    }

    @Test
    fun `page previews pending authority impact before confirmation`() = runComposeUiTest {
        val base = authorityState()
        val pending = base.copy(
            authority = base.authority.copy(
                draft = base.authority.draft.copy(tempo = ProjectTempo(400_000)),
                draftDirty = true,
            ),
        )
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = pending,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithText("Before saving, this explicit authority change will mark the following derived work stale.").assertExists()
        onNodeWithText("Changed dimensions: timing").assertExists()
    }

    private fun authorityState(): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(
                ProjectSectionDefinition("verse", "Verse"),
                ProjectSectionDefinition("chorus", "Chorus"),
            ),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse one", 0, 1920),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 1920, 3840),
                ProjectSectionOccurrence("verse-2", "verse", "Verse repeat", 3840, 5760),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 960),
                AuthoritativeChordEvent("chord-2", "verse-1", "Dbmaj9/F", 960, 1920),
                AuthoritativeChordEvent("chord-3", "chorus-1", "G", 1920, 3840),
                AuthoritativeChordEvent("chord-4", "verse-2", "C", 3840, 5760),
            ),
        )
        val source = SourceMidiRecord(
            originalFilename = "authority-source.mid",
            sha256 = "a".repeat(64),
            format = 1,
            ppq = 480,
            original = ProjectArtifact(ProjectRelativePath("source.mid"), "a".repeat(64)),
            importReport = ProjectArtifact(ProjectRelativePath("report.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList(), 5760)),
            sourceEndTick = 5760,
        )
        val project = MidiCoreProject(
            id = ProjectId("authority-page-project"),
            metadata = ProjectMetadata("Authority page", "2026-08-28T00:00:00Z"),
            sourceMidi = source,
            selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
            authority = authority,
            revision = 7L,
        )
        return MidiCoreWorkspaceState(
            project = project,
            projectRoot = Path.of("build/authority-page-project"),
            source = MidiCoreSourceUiState(
                status = MidiCoreSourceStatus.IMPORTED,
                originalFilename = source.originalFilename,
                sha256 = source.sha256,
                format = source.format,
                ppq = source.ppq,
                sourceEndTick = source.sourceEndTick,
                trackSummaries = source.trackSummaries,
                reportAvailable = true,
            ),
            melody = MidiCoreMelodyUiState(project.selectedMelody),
            authority = MidiCoreAuthorityUiState(
                confirmed = authority,
                draft = MidiCoreAuthorityDraft(authority.key, authority.tempo, authority.meter),
            ),
        )
    }
}
