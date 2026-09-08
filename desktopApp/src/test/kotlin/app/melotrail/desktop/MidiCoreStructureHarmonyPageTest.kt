package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
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
import app.melotrail.arrangement.core.MidiCoreInvalidationPlanner
import app.melotrail.midi.domain.MidiExportRole
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
        onNodeWithText("C bars 1.1–1.2  ·  Dbmaj9/F bars 1.3–1.4").assertExists()
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
        onNodeWithText("C#", useUnmergedTree = true).performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.MODE).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.CONFIRM_AUTHORITY).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_STRUCTURE).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_HARMONY).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.AUDITION).performScrollTo()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.occurrenceAudition("verse-1")).assertIsEnabled().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SOURCE_AUDITION).performScrollTo().performClick()

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
                MidiCoreWorkspaceIntent.PlayOccurrence("verse-1"),
                MidiCoreWorkspaceIntent.PlaySourceMelody,
            ),
            intents,
        )
    }

    @Test
    fun `page exposes explicit pre-structure padding and cancellation controls`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val base = unstructuredAuthorityState(padded = false)
        // Unsaved meter suggestions must not reinterpret the confirmed source-end position.
        val pendingMeter = base.copy(authority = base.authority.copy(draft = base.authority.draft.copy(meter = ProjectMeter(6, 8))))
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = pendingMeter,
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithText("Last melody note ends bar 3 · beat 4 + 2/3 beat. Source end-of-track is bar 3 · beat 4 + 7/8 beat.")
            .performScrollTo()
            .assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.PAD_ARRANGEMENT).performScrollTo().assertIsEnabled().performClick()
        assertEquals(
            listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ConfirmArrangementExtent(true)),
            intents,
        )

        intents.clear()
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = unstructuredAuthorityState(padded = true),
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.CANCEL_PADDING)
            .performScrollTo()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(
            listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.ConfirmArrangementExtent(false)),
            intents,
        )
    }

    @Test
    fun `duration inspector saves unequal chord rows with verified melody and section context`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val base = authorityState().copy(visualEvidence = sourceEvidence())
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = base,
                    onIntent = intents::add,
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SECTION_TABS).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SECTION_CONTEXT).assertTextContains("Verse one · bar 1 · beat 1–bar 2 · beat 1")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.MELODY_CONTEXT).assertTextContains("2 protected melody notes overlap this section.")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.sectionTab(1)).performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SECTION_CONTEXT).assertTextContains("Chorus · bar 2 · beat 1–bar 3 · beat 1")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.chordDuration(0, 0)).assertDoesNotExist()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.sectionTab(0)).performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.chordDuration(0, 0)).performTextReplacement("3")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.chordDuration(0, 1)).performTextReplacement("1")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.CHORD_SPANS).assertTextContains("C bars 1.1–1.3  ·  Dbmaj9/F bars 1.4–1.4")
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_HARMONY).performScrollTo().assertIsEnabled().performClick()

        assertEquals(
            listOf<MidiCoreWorkspaceIntent>(
                MidiCoreWorkspaceIntent.ReplaceHarmony(
                    listOf(
                        AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1440),
                        AuthoritativeChordEvent("chord-2", "verse-1", "Dbmaj9/F", 1440, 1920),
                        AuthoritativeChordEvent("chord-3", "chorus-1", "G", 1920, 3840),
                        AuthoritativeChordEvent("chord-4", "verse-2", "C", 3840, 5760),
                    ),
                ),
            ),
            intents,
        )
    }

    @Test
    fun `removing a middle chord then adding preserves unique identities on save`() = runComposeUiTest {
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
        repeat(3) { onNodeWithText("+ Add chord row").performScrollTo().performClick() }
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.removeChord(0, 3)).performScrollTo().performClick()
        onNodeWithText("+ Add chord row").performScrollTo().performClick()
        repeat(5) { index ->
            onNodeWithTag(MidiCoreStructureHarmonyPageTags.chordDuration(0, index))
                .performScrollTo().performTextReplacement(if (index == 0) "2" else "1/2")
        }
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.SAVE_HARMONY).performScrollTo().assertIsEnabled().performClick()
        val events = (intents.single() as MidiCoreWorkspaceIntent.ReplaceHarmony).events
        assertEquals(events.size, events.map { it.id }.distinct().size)
        assertEquals(1920L, events.filter { it.occurrenceId == "verse-1" }.last().endTick)
    }

    @Test
    fun `duration and row removal controls are keyboard reachable`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = authorityState(),
                    initialDestination = MidiCoreWorkspaceDestination.STRUCTURE_HARMONY,
                )
            }
        }

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.chordDuration(0, 0))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.removeChord(0, 1))
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.removeChord(0, 1)).assertDoesNotExist()
    }

    @Test
    fun `tempo is edited as BPM and internal IDs and ticks are not musician-facing`() = runComposeUiTest {
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

        onNodeWithTag(MidiCoreStructureHarmonyPageTags.TEMPO).assertTextContains("120")
            .performTextReplacement("92")
        assertEquals(
            ProjectTempo.fromBeatsPerMinute(92.0),
            (intents.single() as MidiCoreWorkspaceIntent.UpdateAuthorityDraft).draft.tempo,
        )
        listOf("Tempo µs/qn", "Stable ID", "Occurrence ID", "Definition ID", "Start tick", "Duration ticks")
            .forEach { removed -> onNodeWithText(removed).assertDoesNotExist() }
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
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.ADD_SECTION).performScrollTo().performClick()
        onNodeWithTag(MidiCoreStructureHarmonyPageTags.section(3)).assertExists()
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

        onNodeWithText("The last saved change marked only the affected generated work as stale.").assertExists()
        onNodeWithText("Changes · timing").assertExists()
        onNodeWithText("Confirmed · C major · 4/4 · 120 BPM").assertExists()
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

        onNodeWithText("Saving these changes will mark only the affected generated work as stale.").assertExists()
        onNodeWithText("Changes · timing").assertExists()
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

    private fun unstructuredAuthorityState(padded: Boolean): MidiCoreWorkspaceState {
        val base = authorityState()
        val source = requireNotNull(base.project?.sourceMidi).copy(sourceEndTick = 5_700, lastNoteEndTick = 5_600)
        val authority = requireNotNull(base.project?.authority).copy(
            sectionDefinitions = emptyList(),
            occurrences = emptyList(),
            chordEvents = emptyList(),
            arrangementEndTick = if (padded) 5_760 else 5_700,
        )
        val project = requireNotNull(base.project).copy(sourceMidi = source, authority = authority)
        return base.copy(
            project = project,
            source = base.source.copy(sourceEndTick = source.sourceEndTick, lastNoteEndTick = source.lastNoteEndTick),
            authority = base.authority.copy(confirmed = authority),
        )
    }

    private fun sourceEvidence(): MidiCoreVisualEvidenceProjection {
        val source = MidiCoreVisualEvidence.Available(
            MidiCoreVisualEvidenceAvailable(
                scope = MidiCoreVisualEvidenceScope.PROTECTED_SOURCE,
                identity = MidiCoreVisualEvidenceIdentity("authority-page-project", "a".repeat(64), "authority-hash"),
                timing = MidiCoreVisualEvidenceTiming(480, 5760, 500_000, 4, 2, authoritative = true),
                lanes = listOf(
                    MidiCoreVisualEvidenceLane(
                        MidiExportRole.MELODY,
                        listOf(
                            MidiCoreVisualEvidenceEvent(0, 480, 0, 60, 96, percussion = false),
                            MidiCoreVisualEvidenceEvent(960, 1440, 0, 64, 96, percussion = false),
                            MidiCoreVisualEvidenceEvent(1920, 2400, 0, 67, 96, percussion = false),
                        ),
                    ),
                ),
                currentness = MidiCoreVisualEvidenceCurrentness.CURRENT,
                cacheStatus = MidiCoreVisualEvidenceCacheStatus.COLD,
            ),
        )
        fun unavailable(scope: MidiCoreVisualEvidenceScope) = MidiCoreVisualEvidence.Unavailable(
            MidiCoreVisualEvidenceUnavailable(scope, "NOT_REQUESTED", "Not needed for this inspector.", "Continue editing harmony."),
        )
        return MidiCoreVisualEvidenceProjection(
            source = source,
            selectedCandidate = unavailable(MidiCoreVisualEvidenceScope.SELECTED_CANDIDATE),
            draft = unavailable(MidiCoreVisualEvidenceScope.DRAFT),
            accepted = unavailable(MidiCoreVisualEvidenceScope.ACCEPTED),
        )
    }
}
