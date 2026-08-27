package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreCandidateReviewTest {
    @TempDir lateinit var root: Path

    @Test
    fun `lists and compares only same-scope candidates with semantic note differences`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("review-read-project"))
        val first = generate(store, request(session, "first-candidate", "chords.sustained", "chords.rhythm.sustained"))
        val second = generate(store, request(first.session, "second-candidate", "chords.pulsed", "chords.rhythm.laid-back-quarters"))
        val review = MidiCoreCandidateReview(artifacts = store)

        val listed = assertIs<MidiCoreCandidateReviewResult.Listed>(
            review.list(ListMidiCoreCandidates(second.session, CandidateRole.CHORDS, "verse-1")),
        )
        assertEquals(listOf("first-candidate", "second-candidate"), listed.candidates.map { it.candidate.id })
        assertTrue(listed.candidates.all { it.validation.passed && it.authorityCurrent && !it.accepted })
        assertEquals(second.session.project.revision, listed.revision)

        val compared = assertIs<MidiCoreCandidateReviewResult.Compared>(
            review.compare(
                CompareMidiCoreCandidates(
                    second.session,
                    CandidateRole.CHORDS,
                    "verse-1",
                    "first-candidate",
                    "second-candidate",
                ),
            ),
        )
        assertTrue(compared.differences.any { it.kind == MidiCoreCandidateDifferenceKind.CHANGED })

        val wrongScope = assertIs<MidiCoreCandidateReviewResult.Rejected>(
            review.compare(
                CompareMidiCoreCandidates(
                    second.session,
                    CandidateRole.BASS,
                    "verse-1",
                    "first-candidate",
                    "second-candidate",
                ),
            ),
        )
        assertEquals(MidiCoreCandidateProblemCode.CANDIDATE_SCOPE_MISMATCH, wrongScope.problem.code)
    }

    @Test
    fun `accepts rejects locks unlocks and restores without automatic pointer changes`() {
        val store = MidiCoreArtifactStore()
        var session = readySession(store, root.resolve("review-state-project"))
        val first = generate(store, request(session, "first-candidate", "chords.sustained", "chords.rhythm.sustained"))
        session = first.session
        val second = generate(store, request(session, "second-candidate", "chords.pulsed", "chords.rhythm.laid-back-quarters"))
        session = second.session
        val third = generate(store, request(session, "third-candidate", "chords.sustained", "chords.rhythm.sustained"))
        session = third.session
        val review = MidiCoreCandidateReview(artifacts = store)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.accept(AcceptMidiCoreCandidate(session, "first-candidate")),
        ).session
        assertEquals("first-candidate", session.project.acceptances.single().candidateId)
        assertEquals(MidiCoreCandidateStatus.ACCEPTED, session.project.candidates.single { it.id == "first-candidate" }.status)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.lock(LockMidiCoreCandidate(session, "first-candidate")),
        ).session
        val lockedReplacement = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            review.accept(AcceptMidiCoreCandidate(session, "second-candidate")),
        )
        assertEquals(MidiCoreCandidateProblemCode.LOCKED, lockedReplacement.problem.code)
        assertEquals("first-candidate", store.openProject(session.root).acceptances.single().candidateId)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.unlock(UnlockMidiCoreCandidate(session, "first-candidate")),
        ).session
        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.accept(AcceptMidiCoreCandidate(session, "second-candidate")),
        ).session
        assertEquals("second-candidate", session.project.acceptances.single().candidateId)
        assertEquals(MidiCoreAcceptanceAction.REPLACED, session.project.acceptanceHistory.last().action)

        val wrongRestore = assertIs<MidiCoreCandidateLifecycleResult.Rejected>(
            review.restore(RestoreMidiCoreCandidate(session, "verse-1", CandidateRole.BASS, "first-candidate")),
        )
        assertEquals(MidiCoreCandidateProblemCode.INVALID_STATE, wrongRestore.problem.code)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.restore(RestoreMidiCoreCandidate(session, "verse-1", CandidateRole.CHORDS, "first-candidate")),
        ).session
        assertEquals("first-candidate", session.project.acceptances.single().candidateId)
        assertEquals(MidiCoreAcceptanceAction.RESTORED, session.project.acceptanceHistory.last().action)

        session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            review.reject(RejectMidiCoreCandidate(session, "third-candidate", "Not the selected variation")),
        ).session
        assertEquals(MidiCoreCandidateStatus.REJECTED, session.project.candidates.single { it.id == "third-candidate" }.status)
        assertEquals("Not the selected variation", session.project.candidates.single { it.id == "third-candidate" }.rejectionReason)
        assertEquals("first-candidate", session.project.acceptances.single().candidateId)
    }

    @Test
    fun `rejects tampered evidence and stale expected revisions`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("review-integrity-project"))
        val published = generate(store, request(session, "tampered-candidate", "chords.sustained", "chords.rhythm.sustained"))
        val candidatePath = published.session.root.resolve(published.candidate.midi.path.value)
        Files.write(candidatePath, Files.readAllBytes(candidatePath) + byteArrayOf(0x01))
        val review = MidiCoreCandidateReview(artifacts = store)

        val tampered = assertIs<MidiCoreCandidateReviewResult.Rejected>(
            review.list(ListMidiCoreCandidates(published.session, CandidateRole.CHORDS, "verse-1")),
        )
        assertEquals(MidiCoreCandidateProblemCode.DIGEST_MISMATCH, tampered.problem.code)

        val cleanStore = MidiCoreArtifactStore()
        val clean = readySession(cleanStore, root.resolve("review-revision-project"))
        val one = generate(cleanStore, request(clean, "one-candidate", "chords.sustained", "chords.rhythm.sustained"))
        val two = generate(cleanStore, request(one.session, "two-candidate", "chords.pulsed", "chords.rhythm.laid-back-quarters"))
        val stale = MidiCoreCandidateReview(artifacts = cleanStore).accept(
            AcceptMidiCoreCandidate(one.session, "one-candidate", expectedRevision = one.session.project.revision),
        )
        assertIs<MidiCoreCandidateLifecycleResult.Rejected>(stale).also {
            assertEquals(MidiCoreCandidateProblemCode.REVISION_CONFLICT, it.problem.code)
        }
        assertEquals(two.session.project, cleanStore.openProject(two.session.root))
    }

    @Test
    fun `regeneration is targeted and concurrent review revisions admit only one decision`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("review-concurrency-project"))
        val first = generate(store, request(session, "accepted-candidate", "chords.sustained", "chords.rhythm.sustained"))
        val accepted = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            MidiCoreCandidateReview(artifacts = store).accept(AcceptMidiCoreCandidate(first.session, "accepted-candidate")),
        ).session
        val review = MidiCoreCandidateReview(artifacts = store)

        val regenerated = assertIs<MidiCoreCandidateGenerationResult.Published>(
            review.regenerate(
                RegenerateMidiCoreCandidate(
                    request(accepted, "regenerated-candidate", "chords.pulsed", "chords.rhythm.laid-back-quarters"),
                    expectedRevision = accepted.project.revision,
                ),
            ),
        )
        assertEquals("accepted-candidate", regenerated.session.project.acceptances.single().candidateId)
        assertEquals(2, regenerated.session.project.candidates.size)

        val third = generate(store, request(regenerated.session, "third-candidate", "chords.sustained", "chords.rhythm.sustained"))
        val revision = third.session.project.revision
        val decisions = listOf("regenerated-candidate", "third-candidate").map { candidateId ->
            async(Dispatchers.Default) {
                review.accept(AcceptMidiCoreCandidate(third.session, candidateId, expectedRevision = revision))
            }
        }.awaitAll()
        assertEquals(1, decisions.count { it is MidiCoreCandidateLifecycleResult.Updated })
        assertEquals(1, decisions.count {
            it is MidiCoreCandidateLifecycleResult.Rejected && it.problem.code == MidiCoreCandidateProblemCode.REVISION_CONFLICT
        })
        assertEquals(1, store.openProject(third.session.root).acceptances.size)
        assertFalse(store.openProject(third.session.root).candidates.any { it.id == "missing" })
    }

    private fun generate(store: MidiCoreArtifactStore, request: GenerateMidiCoreCandidate) = assertIs<MidiCoreCandidateGenerationResult.Published>(
        runBlocking { MidiCoreCandidateGeneration(artifacts = store).generate(request) },
    )

    private fun readySession(store: MidiCoreArtifactStore, projectRoot: Path): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(artifacts = store).create(CreateMidiCoreProject(projectRoot, "Review Test", "review-project")),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-${projectRoot.fileName}"))
            .single { it.fileName.toString() == "smf0-melody.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        val selected = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(imported, 0, 0)),
        ).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    selected,
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
                    listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 480)),
                    expectedSongEndTick = 480,
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 480)),
                ),
            ),
        ).session
    }

    private fun request(
        session: MidiCoreProjectSession,
        candidateId: String,
        profile: String,
        pattern: String,
    ) = GenerateMidiCoreCandidate(
        session = session,
        role = CandidateRole.CHORDS,
        occurrenceId = "verse-1",
        performanceProfileId = profile,
        patternId = pattern,
        generator = MidiCoreGeneratorInput("review-generator", "review-generator-v1", pattern, candidateId.hashCode().toLong()),
        sectionPolicy = app.melotrail.arrangement.core.MidiCoreSectionPolicy(density = 1.0),
        candidateId = candidateId,
    )
}
