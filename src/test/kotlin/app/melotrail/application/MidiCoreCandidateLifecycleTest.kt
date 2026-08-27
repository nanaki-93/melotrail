package app.melotrail.application

import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreExportSnapshot
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
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreCandidateLifecycleTest {
    @TempDir lateinit var root: Path

    @Test
    fun `publishes immutable candidates and enforces review state transitions`() {
        val store = MidiCoreArtifactStore()
        var session = project(store)
        val lifecycle = lifecycle(store, "history-1", "history-2", "history-3", "history-4", "history-5", "history-6")
        val first = publish(lifecycle, session, "candidate-1", "first")
        session = first.session
        val firstBytes = Files.readAllBytes(session.root.resolve(first.candidate.midi.path.value))

        val accepted = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-1")),
        )
        session = accepted.session
        assertEquals(MidiCoreCandidateStatus.ACCEPTED, candidate(session.project, "candidate-1").status)
        assertEquals("candidate-1", session.project.acceptances.single().candidateId)
        assertEquals(MidiCoreAcceptanceAction.ACCEPTED, session.project.acceptanceHistory.single().action)

        val locked = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.lock(LockMidiCoreCandidate(session, "candidate-1")),
        )
        session = locked.session
        assertTrue(session.project.acceptances.single().locked)

        val second = publish(lifecycle, session, "candidate-2", "second")
        session = second.session
        val blocked = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            lifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-2")),
        )
        assertEquals(MidiCoreCandidateProblemCode.LOCKED, blocked.problem.code)
        assertEquals("candidate-1", store.openProject(session.root).acceptances.single().candidateId)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.unlock(UnlockMidiCoreCandidate(session, "candidate-1")),
        ).session
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-2")),
        ).session
        assertEquals(MidiCoreCandidateStatus.CURRENT, candidate(session.project, "candidate-1").status)
        assertEquals(MidiCoreCandidateStatus.ACCEPTED, candidate(session.project, "candidate-2").status)
        assertEquals(MidiCoreAcceptanceAction.REPLACED, session.project.acceptanceHistory.last().action)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.restore(RestoreMidiCoreCandidate(session, "intro-1", CandidateRole.CHORDS, "candidate-1")),
        ).session
        assertEquals("candidate-1", session.project.acceptances.single().candidateId)
        assertEquals(MidiCoreCandidateStatus.ACCEPTED, candidate(session.project, "candidate-1").status)
        assertEquals(MidiCoreCandidateStatus.CURRENT, candidate(session.project, "candidate-2").status)
        assertEquals(MidiCoreAcceptanceAction.RESTORED, session.project.acceptanceHistory.last().action)

        val rejectedCandidate = publish(lifecycle, session, "candidate-3", "third")
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            lifecycle.reject(RejectMidiCoreCandidate(rejectedCandidate.session, "candidate-3", "Too dense")),
        ).session
        assertEquals(MidiCoreCandidateStatus.REJECTED, candidate(session.project, "candidate-3").status)
        assertEquals("Too dense", candidate(session.project, "candidate-3").rejectionReason)
        val rejectionCannotBeAccepted = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            lifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-3")),
        )
        assertEquals(MidiCoreCandidateProblemCode.INVALID_STATE, rejectionCannotBeAccepted.problem.code)

        assertContentEquals(firstBytes, Files.readAllBytes(session.root.resolve("candidates/chords/intro-1/candidate-1.mid")))
        assertTrue(Files.isRegularFile(session.root.resolve("reports/candidates/candidate-1.json")))
        assertEquals(session.project, store.openProject(session.root))
        assertTrue(session.project.acceptanceHistory.map { it.id }.distinct().size == session.project.acceptanceHistory.size)
    }

    @Test
    fun `rejects stale authority and marks dependent candidates without deleting evidence`() {
        val store = MidiCoreArtifactStore()
        var session = project(store)
        val lifecycle = lifecycle(store, "history-1")
        session = publish(lifecycle, session, "candidate-1", "candidate").session
        val candidatePath = session.root.resolve("candidates/chords/intro-1/candidate-1.mid")
        val candidateBytes = Files.readAllBytes(candidatePath)

        val changed = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    session,
                    listOf(AuthoritativeChordEvent("chord-1", "intro-1", "Db", 0, 480)),
                ),
            ),
        )
        session = changed.session
        assertEquals(MidiCoreCandidateStatus.STALE, candidate(session.project, "candidate-1").status)
        val rejected = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            lifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-1")),
        )
        assertEquals(MidiCoreCandidateProblemCode.CANDIDATE_STALE, rejected.problem.code)
        assertContentEquals(candidateBytes, Files.readAllBytes(candidatePath))
        assertEquals(session.project, store.openProject(session.root))
    }

    @Test
    fun `captures accepted candidate digests and reports old snapshot stale after authority change`() {
        val store = MidiCoreArtifactStore()
        var session = project(store)
        val candidateLifecycle = lifecycle(store, "history-1")
        session = publish(candidateLifecycle, session, "candidate-1", "candidate").session
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            candidateLifecycle.accept(AcceptMidiCoreCandidate(session, "candidate-1")),
        ).session
        val complete = store.publishExportFile(projectRoot = session.root, snapshotId = "export-1", kind = ExportedFileKind.COMPLETE_SONG, source = bytesFile(root.resolve("complete.mid"), "complete"))
        val manifest = store.publishExportFile(session.root, "export-1", ExportedFileKind.MANIFEST, bytesFile(root.resolve("manifest.json"), "{}"))
        val snapshot = assertIs<MidiCoreExportSnapshotLifecycleResult.Captured>(
            MidiCoreExportSnapshotLifecycle(store, fixedClock(), { "unused" }).capture(
                CaptureMidiCoreExportSnapshot(
                    session,
                    listOf(
                        ExportedSnapshotFile(ExportedFileKind.COMPLETE_SONG, complete),
                        ExportedSnapshotFile(ExportedFileKind.MANIFEST, manifest),
                    ),
                    snapshotId = "export-1",
                ),
            ),
        )
        session = snapshot.session
        assertTrue(snapshot.snapshot.isCurrent(session.project))
        assertEquals("candidate-1", snapshot.snapshot.acceptedCandidates.single().candidateId)
        assertEquals(candidate(session.project, "candidate-1").midi.sha256, snapshot.snapshot.acceptedCandidates.single().midiSha256)
        assertEquals(session.project, store.openProject(session.root))

        val changed = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(session, listOf(AuthoritativeChordEvent("chord-1", "intro-1", "Db", 0, 480))),
            ),
        )
        session = changed.session
        assertFalse(snapshot.snapshot.isCurrent(session.project))
        assertEquals(MidiCoreCandidateStatus.STALE, candidate(session.project, "candidate-1").status)
        assertTrue(Files.isRegularFile(session.root.resolve("exports/export-1/manifest.json")))
    }

    private fun publish(
        lifecycle: MidiCoreCandidateLifecycle,
        session: MidiCoreProjectSession,
        id: String,
        content: String,
    ): MidiCoreCandidateLifecycleResult.Published = assertIs(
        lifecycle.publish(
            PublishMidiCoreCandidate(
                session = session,
                role = CandidateRole.CHORDS,
                occurrenceId = "intro-1",
                generatorVersion = "chords-v1",
                authorityHash = MidiCoreAuthorityHasher.from(session.project).scopeHash("intro-1", CandidateRole.CHORDS),
                seed = 7,
                midi = bytesFile(root.resolve("$id-input.mid"), content),
                validationReportJson = "{\"valid\":true}",
                candidateId = id,
                profileId = "sustained",
                patternId = "quarter-chords",
            ),
        ),
    )

    private fun candidate(project: MidiCoreProject, id: String) = requireNotNull(project.candidates.singleOrNull { it.id == id })

    private fun project(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val projectRoot = root.resolve("project")
        store.initialize(projectRoot)
        val source = store.publishSource(projectRoot, bytesFile(root.resolve("source.mid"), "source-midi"))
        val report = store.publishImportReport(projectRoot, "{\"status\":\"accepted\"}")
        val project = MidiCoreProject(
            id = ProjectId("lifecycle-project"),
            metadata = ProjectMetadata("Lifecycle", "2026-08-27T00:00:00Z"),
            sourceMidi = SourceMidiRecord("source.mid", source.sha256, 1, 480, source, report, emptyList<MidiTrackSummary>(), 480),
            selectedMelody = SelectedMelodyTrack(0, 0, "b".repeat(64)),
            authority = ProjectAuthority(
                ProjectKey(0, "major"),
                app.melotrail.music.core.ProjectTempo(500_000),
                app.melotrail.music.core.ProjectMeter(4, 2),
                listOf(ProjectSectionDefinition("intro", "Intro")),
                listOf(ProjectSectionOccurrence("intro-1", "intro", "Intro", 0, 480)),
                listOf(AuthoritativeChordEvent("chord-1", "intro-1", "C", 0, 480)),
            ),
        )
        store.saveProject(projectRoot, project)
        return MidiCoreProjectSession(projectRoot, project)
    }

    private fun lifecycle(store: MidiCoreArtifactStore, vararg ids: String): MidiCoreCandidateLifecycle {
        var index = 0
        return MidiCoreCandidateLifecycle(store, fixedClock()) {
            ids.getOrElse(index++) { "history-fallback-$index" }
        }
    }

    private fun fixedClock() = Clock.fixed(Instant.parse("2026-08-27T00:10:00Z"), ZoneOffset.UTC)

    private fun bytesFile(path: Path, value: String): Path = path.also { file ->
        Files.createDirectories(requireNotNull(file.parent))
        Files.writeString(file, value)
    }
}
