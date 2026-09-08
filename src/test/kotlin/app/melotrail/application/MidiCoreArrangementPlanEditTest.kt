package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreInvalidationReason
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthorityDimension
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.AtomicWriteObserver
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArrangementPlanEditTest {
    @TempDir lateinit var root: Path

    @Test
    fun `confirmed purpose and phrase edits preserve protected source bytes`() {
        val store = MidiCoreArtifactStore()
        val session = confirmedSession(store)
        val source = requireNotNull(session.project.sourceMidi)
        val beforeSource = Files.readAllBytes(store.verify(session.root, source.original))
        val plan = requireNotNull(session.project.arrangementPlan)
        val changed = plan.copy(occurrences = plan.occurrences.map { occurrence ->
            if (occurrence.occurrenceId == "verse-1") occurrence.copy(
                purpose = app.melotrail.project.MidiCoreArrangementPurpose.BRIDGE,
                phraseGroupId = "phrase-bridge",
            ) else occurrence
        })

        val result = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, changed)),
        )

        assertEquals(changed, result.session.project.arrangementPlan)
        assertContentEquals(beforeSource, Files.readAllBytes(store.verify(session.root, source.original)))
    }

    @Test
    fun `a confirmed plan edit previews and invalidates only affected locked work without rewriting evidence`() {
        val store = MidiCoreArtifactStore()
        var session = confirmedSession(store)
        // This test locks audible Bass work; the intro proposal initially leaves Bass inactive.
        val activeBassPlan = requireNotNull(session.project.arrangementPlan).let { plan ->
            plan.copy(occurrences = plan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId != "verse-1") occurrence else occurrence.copy(
                    roleSettings = occurrence.roleSettings.map { setting ->
                        if (setting.role == CandidateRole.BASS) setting.copy(
                            activity = app.melotrail.project.MidiCoreRoleActivity.SUPPORTING, density = 30,
                        ) else setting
                    },
                )
            })
        }
        session = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, activeBassPlan)),
        ).session
        session = publish(store, session, CandidateRole.BASS, "verse-locked", "verse-1")
        val lockedMidi = session.project.candidates.single { it.id == "verse-locked" }.midi
        val lockedBytes = Files.readAllBytes(store.verify(session.root, lockedMidi))
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            MidiCoreCandidateLifecycle(store).accept(AcceptMidiCoreCandidate(session, "verse-locked", locked = true)),
        ).session
        session = publish(store, session, CandidateRole.CHORDS, "verse-chords", "verse-1")
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            MidiCoreCandidateLifecycle(store).accept(AcceptMidiCoreCandidate(session, "verse-chords")),
        ).session
        session = publish(store, session, CandidateRole.BASS, "chorus-bass", "chorus-1")
        session = publish(
            store,
            session,
            CandidateRole.CHORDS,
            "chorus-dependent",
            "chorus-1",
            acceptedDependencies = listOf("verse-locked"),
        )
        val source = requireNotNull(session.project.sourceMidi)
        val sourceBytes = Files.readAllBytes(store.verify(session.root, source.original))
        val beforeAcceptances = session.project.acceptances
        val beforePlan = requireNotNull(session.project.arrangementPlan)
        val changedPlan = beforePlan.copy(
            occurrences = beforePlan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId == "verse-1") {
                    occurrence.copy(roleSettings = occurrence.roleSettings.map { settings ->
                        if (settings.role == CandidateRole.BASS) settings.copy(density = settings.density + 1) else settings
                    })
                } else occurrence
            },
        )
        val edit = MidiCoreArrangementPlanEdit(store)

        val preview = assertIs<MidiCoreArrangementPlanEditResult.Previewed>(
            edit.preview(PreviewMidiCoreArrangementPlanEdit(session, changedPlan)),
        ).invalidation

        assertEquals(listOf("chorus-bass", "chorus-dependent", "verse-locked"), preview.staleCandidateIds)
        assertTrue(preview.affects(CandidateRole.BASS, "verse-1"))
        assertFalse(preview.affects(CandidateRole.CHORDS, "verse-1"))
        assertTrue(preview.affects(CandidateRole.BASS, "chorus-1"))
        assertEquals(listOf(MidiCoreAuthorityDimension.ARRANGEMENT_PLAN), preview.changedDimensions)
        assertEquals(
            listOf(MidiCoreInvalidationReason.ACCEPTED_DEPENDENCY_CHANGED),
            preview.staleTargets.single { it.id == "chorus-dependent" }.reasons,
        )
        assertEquals(
            listOf(MidiCoreInvalidationReason.ARRANGEMENT_PLAN_CHANGED),
            preview.staleTargets.single { it.id == "verse-locked" }.reasons,
        )

        val confirmed = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            edit.confirm(ConfirmMidiCoreArrangementPlanEdit(session, changedPlan)),
        )
        val persisted = store.openProject(session.root)

        assertEquals(changedPlan, persisted.arrangementPlan)
        assertEquals(beforeAcceptances, persisted.acceptances)
        assertEquals(MidiCoreCandidateStatus.STALE, persisted.candidates.single { it.id == "verse-locked" }.status)
        assertEquals(MidiCoreCandidateStatus.ACCEPTED, persisted.candidates.single { it.id == "verse-chords" }.status)
        assertEquals(MidiCoreCandidateStatus.STALE, persisted.candidates.single { it.id == "chorus-bass" }.status)
        assertEquals(MidiCoreCandidateStatus.STALE, persisted.candidates.single { it.id == "chorus-dependent" }.status)
        assertEquals("verse-locked", persisted.acceptances.single { it.role == CandidateRole.BASS }.candidateId)
        assertTrue(persisted.acceptances.single { it.role == CandidateRole.BASS }.locked)
        assertEquals("verse-chords", persisted.acceptances.single { it.role == CandidateRole.CHORDS }.candidateId)
        assertFalse(persisted.acceptances.single { it.role == CandidateRole.CHORDS }.locked)
        assertContentEquals(lockedBytes, Files.readAllBytes(store.verify(session.root, lockedMidi)))
        assertContentEquals(sourceBytes, Files.readAllBytes(store.verify(session.root, source.original)))
        assertEquals(confirmed.session.project, persisted)
    }

    @Test
    fun `structure replacement waits for plan confirmation and rejects its stale session`() {
        val blockSave = AtomicBoolean(false)
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val store = MidiCoreArtifactStore(AtomicWriteObserver { _, target ->
            if (target.fileName.toString() == MidiCoreArtifactStore.PROJECT_FILE && blockSave.compareAndSet(true, false)) {
                saveStarted.countDown()
                check(releaseSave.await(5, TimeUnit.SECONDS))
            }
        })
        val session = confirmedSession(store)
        val plan = requireNotNull(session.project.arrangementPlan)
        val changed = plan.copy(occurrences = plan.occurrences.map { it.copy(phraseGroupId = "confirmed-phrase") })
        val executor = Executors.newFixedThreadPool(2)
        try {
            blockSave.set(true)
            val planFuture = executor.submit<MidiCoreArrangementPlanEditResult> {
                MidiCoreArrangementPlanEdit(store).confirm(ConfirmMidiCoreArrangementPlanEdit(session, changed))
            }
            assertTrue(saveStarted.await(5, TimeUnit.SECONDS))
            val structureStarted = CountDownLatch(1)
            val structureFuture = executor.submit<MidiCoreStructureTimelineResult> {
                structureStarted.countDown()
                MidiCoreStructureTimeline(store).replace(ReplaceMidiCoreStructure(session,
                    requireNotNull(session.project.authority).sectionDefinitions,
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Renamed verse", 1),
                        MidiCoreBarOccurrencePlacement("chorus-1", "chorus", "Chorus", 2))))
            }
            assertTrue(structureStarted.await(5, TimeUnit.SECONDS))
            kotlin.test.assertFailsWith<java.util.concurrent.TimeoutException> { structureFuture.get(150, TimeUnit.MILLISECONDS) }
            releaseSave.countDown()
            val confirmed = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(planFuture.get(5, TimeUnit.SECONDS))
            val rejected = assertIs<MidiCoreStructureTimelineResult.Rejected>(structureFuture.get(5, TimeUnit.SECONDS))
            assertEquals(MidiCoreStructureTimelineProblemCode.STALE_PROJECT, rejected.problem.code)
            assertEquals(confirmed.session.project, store.openProject(session.root))
            assertEquals(changed, store.openProject(session.root).arrangementPlan)
        } finally {
            releaseSave.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a no-op confirmed plan edit has stable hashes and does not write a new revision`() {
        val store = MidiCoreArtifactStore()
        val session = confirmedSession(store)
        val plan = requireNotNull(session.project.arrangementPlan)
        val projectFile = session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)
        val beforeBytes = Files.readAllBytes(projectFile)
        val beforeFingerprint = MidiCoreAuthorityHasher.from(session.project)
        val edit = MidiCoreArrangementPlanEdit(store)

        val preview = assertIs<MidiCoreArrangementPlanEditResult.Previewed>(
            edit.preview(PreviewMidiCoreArrangementPlanEdit(session, plan.copy())),
        ).invalidation
        val confirmed = assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(
            edit.confirm(ConfirmMidiCoreArrangementPlanEdit(session, plan.copy())),
        )

        assertFalse(preview.hasImpact)
        assertTrue(preview.staleTargets.isEmpty())
        assertEquals(session.project.revision, confirmed.session.project.revision)
        assertEquals(beforeFingerprint, MidiCoreAuthorityHasher.from(confirmed.session.project))
        assertContentEquals(beforeBytes, Files.readAllBytes(projectFile))
    }

    @Test
    fun `confirmed plan edit serializes concurrent acceptance and snapshot writes instead of losing them`() {
        val blockPlanSave = AtomicBoolean(false)
        val planSaveStarted = CountDownLatch(1)
        val releasePlanSave = CountDownLatch(1)
        val store = MidiCoreArtifactStore(AtomicWriteObserver { _, target ->
            if (blockPlanSave.compareAndSet(true, false) && target.fileName.toString() == MidiCoreArtifactStore.PROJECT_FILE) {
                planSaveStarted.countDown()
                check(releasePlanSave.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release the plan save" }
            }
        })
        var session = confirmedSession(store)
        session = publish(store, session, CandidateRole.BASS, "concurrent-candidate", "verse-1")
        val plan = requireNotNull(session.project.arrangementPlan)
        val changedPlan = plan.copy(
            occurrences = plan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId == "verse-1") occurrence.copy(
                    roleSettings = occurrence.roleSettings.map { settings ->
                        if (settings.role == CandidateRole.BASS) settings.copy(density = settings.density + 1) else settings
                    },
                ) else occurrence
            },
        )
        val manifestInput = root.resolve("concurrent-manifest.json")
        Files.writeString(manifestInput, "{}")
        val manifest = store.publishExportFile(session.root, "concurrent-snapshot", ExportedFileKind.MANIFEST, manifestInput)
        val edit = MidiCoreArrangementPlanEdit(store)
        val executor = Executors.newFixedThreadPool(3)
        try {
            blockPlanSave.set(true)
            val planFuture = executor.submit<MidiCoreArrangementPlanEditResult> {
                edit.confirm(ConfirmMidiCoreArrangementPlanEdit(session, changedPlan))
            }
            assertTrue(planSaveStarted.await(5, TimeUnit.SECONDS))

            val acceptanceStarted = CountDownLatch(1)
            val snapshotStarted = CountDownLatch(1)
            val acceptanceFuture = executor.submit<MidiCoreCandidateLifecycleResult> {
                acceptanceStarted.countDown()
                MidiCoreCandidateLifecycle(store).accept(AcceptMidiCoreCandidate(session, "concurrent-candidate"))
            }
            val snapshotFuture = executor.submit<MidiCoreExportSnapshotLifecycleResult> {
                snapshotStarted.countDown()
                MidiCoreExportSnapshotLifecycle(store).capture(
                    CaptureMidiCoreExportSnapshot(
                        session,
                        listOf(ExportedSnapshotFile(ExportedFileKind.MANIFEST, manifest)),
                        snapshotId = "concurrent-snapshot",
                        enabledRoles = emptySet(),
                    ),
                )
            }
            assertTrue(acceptanceStarted.await(5, TimeUnit.SECONDS))
            assertTrue(snapshotStarted.await(5, TimeUnit.SECONDS))
            assertFalse(acceptanceFuture.isDone)
            assertFalse(snapshotFuture.isDone)

            releasePlanSave.countDown()

            assertIs<MidiCoreArrangementPlanEditResult.Confirmed>(planFuture.get(5, TimeUnit.SECONDS))
            val acceptance = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(acceptanceFuture.get(5, TimeUnit.SECONDS))
            val snapshot = assertIs<MidiCoreExportSnapshotLifecycleResult.Rejected>(snapshotFuture.get(5, TimeUnit.SECONDS))
            assertEquals(MidiCoreCandidateProblemCode.STALE_PROJECT, acceptance.problem.code)
            assertEquals(MidiCoreExportSnapshotProblemCode.STALE_PROJECT, snapshot.problem.code)
            val persisted = store.openProject(session.root)
            assertEquals(changedPlan, persisted.arrangementPlan)
            assertTrue(persisted.acceptances.isEmpty())
            assertTrue(persisted.exportSnapshots.isEmpty())
            assertEquals(MidiCoreCandidateStatus.STALE, persisted.candidates.single().status)
        } finally {
            releasePlanSave.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a shared occurrence-plan input invalidates that occurrence and its bounded neighbors for every role`() {
        val store = MidiCoreArtifactStore()
        val session = confirmedSession(store)
        val plan = requireNotNull(session.project.arrangementPlan)
        val changed = plan.copy(
            occurrences = plan.occurrences.map { occurrence ->
                if (occurrence.occurrenceId == "verse-1") occurrence.copy(energy = occurrence.energy + 1) else occurrence
            },
        )
        val before = MidiCoreAuthorityHasher.from(session.project)
        val after = MidiCoreAuthorityHasher.from(session.project.copy(arrangementPlan = changed))

        assertNotEquals(before.arrangementPlanSha256, after.arrangementPlanSha256)
        CandidateRole.entries.forEach { role ->
            assertNotEquals(before.scopeHash("verse-1", role), after.scopeHash("verse-1", role))
            assertNotEquals(before.scopeHash("chorus-1", role), after.scopeHash("chorus-1", role))
        }
    }

    private fun confirmedSession(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store, idFactory = { "plan-edit-project" }).create(
                CreateMidiCoreProject(root.resolve("project"), "Plan edit", "plan-edit-project"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures")).single { it.fileName.toString() == "whole-song-three-bars.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source))).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(imported, ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR), ProjectTempo(500_000), ProjectMeter(4, 2)),
            ),
        ).session
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    authority,
                    listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1), MidiCoreBarOccurrencePlacement("chorus-1", "chorus", "Chorus", 2)),
                ),
            ),
        ).session
        val harmonic = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    listOf(
                        AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 1920),
                        AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 1920, 5760),
                    ),
                ),
            ),
        ).session
        val proposal = assertIs<MidiCoreArrangementPlanProposalResult.Proposed>(
            MidiCoreArrangementPlanProposalUseCase(store).propose(ProposeMidiCoreArrangementPlan(harmonic, "steady-road")),
        ).proposal
        return assertIs<MidiCoreArrangementPlanProposalResult.Confirmed>(
            MidiCoreArrangementPlanProposalUseCase(store).confirm(ConfirmMidiCoreArrangementPlanProposal(harmonic, proposal)),
        ).session
    }

    private fun publish(
        store: MidiCoreArtifactStore,
        session: MidiCoreProjectSession,
        role: CandidateRole,
        candidateId: String,
        occurrenceId: String,
        acceptedDependencies: List<String> = emptyList(),
    ): MidiCoreProjectSession {
        val input = root.resolve("$candidateId.mid")
        Files.copy(store.verify(session.root, requireNotNull(session.project.sourceMidi).original), input)
        val authorityHash = MidiCoreAuthorityHasher.from(session.project).scopeHash(occurrenceId, role)
        return assertIs<MidiCoreCandidateLifecycleResult.Published>(
            MidiCoreCandidateLifecycle(store).publish(
                PublishMidiCoreCandidate(
                    session,
                    role,
                    occurrenceId,
                    "test-v1",
                    authorityHash,
                    1L,
                    input,
                    "{}",
                    candidateId,
                    acceptedDependencyIds = acceptedDependencies,
                ),
            ),
        ).session
    }
}
