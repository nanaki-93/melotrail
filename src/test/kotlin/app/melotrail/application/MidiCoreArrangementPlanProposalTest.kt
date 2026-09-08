package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreArrangementStyleCatalog
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementPurpose
import app.melotrail.project.MidiCoreRoleActivity
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArrangementPlanProposalTest {
    @TempDir lateinit var root: Path

    @Test
    fun `style proposal is deterministic and never writes project authority or artifacts`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val useCase = MidiCoreArrangementPlanProposalUseCase(store)
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val beforeProject = Files.readAllBytes(projectFile)
        val beforePaths = paths(session.root)

        val first = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "late-night")),
        )
        val second = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "late-night")),
        )

        assertEquals(first.proposal, second.proposal)
        assertEquals(
            listOf(MidiCoreArrangementPurpose.OTHER),
            first.proposal.plan.occurrences.map { it.purpose },
        )
        assertContentEquals(beforeProject, Files.readAllBytes(projectFile))
        assertEquals(beforePaths, paths(session.root))
        assertNull(store.openProject(session.root).arrangementPlan)
        assertTrue(store.openProject(session.root).arrangementDrafts.isEmpty())
    }

    @Test
    fun `proposal purposes are order-derived and give each song function distinct intent`() {
        val authority = sixOccurrenceAuthority()
        val plan = MidiCoreArrangementPlanProposalFactory.create(
            authority,
            MidiCoreArrangementStyleCatalog.require("rising-room"),
        )

        assertEquals(
            listOf(
                MidiCoreArrangementPurpose.INTRO,
                MidiCoreArrangementPurpose.VERSE,
                MidiCoreArrangementPurpose.PRE_CHORUS,
                MidiCoreArrangementPurpose.CHORUS,
                MidiCoreArrangementPurpose.BRIDGE,
                MidiCoreArrangementPurpose.OUTRO,
            ),
            plan.occurrences.map { it.purpose },
        )
        val activities = plan.occurrences.associate { it.purpose to it.roleSettings.associateBy { settings -> settings.role } }
        assertEquals(MidiCoreRoleActivity.INACTIVE, activities.getValue(MidiCoreArrangementPurpose.INTRO).getValue(CandidateRole.DRUMS).activity)
        assertEquals(MidiCoreRoleActivity.SPARSE, activities.getValue(MidiCoreArrangementPurpose.VERSE).getValue(CandidateRole.DRUMS).activity)
        assertEquals(MidiCoreRoleActivity.PROMINENT, activities.getValue(MidiCoreArrangementPurpose.CHORUS).getValue(CandidateRole.DRUMS).activity)
        assertEquals(MidiCoreRoleActivity.SPARSE, activities.getValue(MidiCoreArrangementPurpose.BRIDGE).getValue(CandidateRole.BASS).activity)
        assertEquals(MidiCoreRoleActivity.INACTIVE, activities.getValue(MidiCoreArrangementPurpose.OUTRO).getValue(CandidateRole.BASS).activity)
        assertNotEquals(
            plan.occurrences.single { it.purpose == MidiCoreArrangementPurpose.INTRO }.entryIntent,
            plan.occurrences.single { it.purpose == MidiCoreArrangementPurpose.CHORUS }.entryIntent,
        )
    }

    @Test
    fun `renaming section labels cannot change a proposal purpose`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val useCase = MidiCoreArrangementPlanProposalUseCase(store)
        val before = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "open-sky")),
        ).proposal

        val renamed = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    session,
                    listOf(ProjectSectionDefinition("verse", "Not a musical-purpose hint")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "A label may say bridge", 3)),
                ),
            ),
        ).session
        val after = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(renamed, "open-sky")),
        ).proposal

        assertEquals(before.plan.occurrences.map { it.purpose }, after.plan.occurrences.map { it.purpose })
        assertNotEquals(before.authorityHash, after.authorityHash)
        assertEquals("verse-1", after.plan.occurrences.single().occurrenceId)
    }

    @Test
    fun `cancelling leaves the proposal unsaved while confirmation saves only explicit plan authority`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val useCase = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "wide-bridge")),
        ).proposal
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val beforeCancellation = Files.readAllBytes(projectFile)
        val beforeSource = Files.readAllBytes(session.root.resolve("source/original.mid"))
        val beforePaths = paths(session.root)

        val cancelled = assertIs<MidiCoreArrangementPlanProposalResult.Cancelled>(
            useCase.cancel(CancelMidiCoreArrangementPlanProposal(proposal)),
        )

        assertEquals(proposal, cancelled.proposal)
        assertContentEquals(beforeCancellation, Files.readAllBytes(projectFile))
        assertEquals(beforePaths, paths(session.root))
        assertNull(store.openProject(session.root).arrangementPlan)

        val confirmed = assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(
            useCase.confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal)),
        )

        assertEquals(proposal.plan, confirmed.plan)
        assertEquals(session.project.revision + 1L, confirmed.session.project.revision)
        assertEquals(proposal.plan, store.openProject(session.root).arrangementPlan)
        assertTrue(confirmed.session.project.candidates.isEmpty())
        assertTrue(confirmed.session.project.arrangementDrafts.isEmpty())
        assertFalse(beforeCancellation.contentEquals(Files.readAllBytes(projectFile)))
        assertContentEquals(beforeSource, Files.readAllBytes(session.root.resolve("source/original.mid")))
    }

    @Test
    fun `changed authority rejects both stale session and stale proposal without writes`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val useCase = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "open-sky")),
        ).proposal
        val renamed = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(ReplaceMidiCoreStructure(
                session,
                listOf(ProjectSectionDefinition("verse", "Changed")),
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Changed", 3)),
            )),
        ).session
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectFile)
        val staleSession = assertIs<MidiCoreArrangementPlanProposalResult.Rejected>(
            useCase.confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal)),
        )
        assertEquals(MidiCoreArrangementPlanProposalProblemCode.STALE_PROJECT, staleSession.problem.code)
        val staleProposal = assertIs<MidiCoreArrangementPlanProposalResult.Rejected>(
            useCase.confirm(ConfirmMidiCoreArrangementPlanProposal(renamed, proposal)),
        )
        assertEquals(MidiCoreArrangementPlanProposalProblemCode.STALE_PROPOSAL, staleProposal.problem.code)
        assertContentEquals(before, Files.readAllBytes(projectFile))
        assertNull(store.openProject(session.root).arrangementPlan)
    }

    @Test
    fun `confirmation cannot overwrite an already confirmed plan`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store)
        val useCase = MidiCoreArrangementPlanProposalUseCase(store)
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            useCase.propose(ProposeMidiCoreArrangementPlan(session, "open-sky")),
        ).proposal
        val confirmed = assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(
            useCase.confirm(ConfirmMidiCoreArrangementPlanProposal(session, proposal)),
        ).session
        val file = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val before = Files.readAllBytes(file)
        val repeated = assertIs<MidiCoreArrangementPlanProposalResult.Rejected>(
            useCase.confirm(ConfirmMidiCoreArrangementPlanProposal(confirmed, proposal)),
        )
        assertEquals(MidiCoreArrangementPlanProposalProblemCode.PLAN_ALREADY_CONFIRMED, repeated.problem.code)
        assertContentEquals(before, Files.readAllBytes(file))
    }

    private fun readySession(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store, idFactory = { "plan-proposal-project" }).create(
                CreateMidiCoreProject(root.resolve("project"), "Plan proposal", "plan-proposal-project"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures")).single { it.fileName.toString() == "whole-song-three-bars.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source))).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    imported,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        ).session
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    authority,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 3)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(structured, listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 5760))),
            ),
        ).session
    }

    private fun sixOccurrenceAuthority(): ProjectAuthority {
        val definitions = (1..6).map { ProjectSectionDefinition("section-$it", "Misleading label $it") }
        val occurrences = definitions.mapIndexed { index, definition ->
            ProjectSectionOccurrence("section-${index + 1}", definition.id, "Not ${index + 1}", index * 1920L, (index + 1) * 1920L)
        }
        return ProjectAuthority(
            ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            ProjectTempo(500_000),
            ProjectMeter(4, 2),
            definitions,
            occurrences,
            emptyList(),
            arrangementEndTick = occurrences.last().endTick,
        )
    }

    private fun paths(directory: Path): List<Path> = Files.walk(directory).use { stream ->
        stream.map { it.toAbsolutePath().normalize() }.sorted().toList()
    }
}
