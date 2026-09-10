package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectRelativePath
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreMusicalRepairAlternativeRankerTest {
    @Test
    fun `dependency refresh explicitly identifies matching baseline without a new isolated choice`() {
        val baseline = item("baseline", listOf(note(0, 480, 60, 72)))
        val refreshed = item("refreshed", baseline.notes)
        val grouped = MidiCoreMusicalRepairAlternativeRanker.rank(listOf(refreshed), MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION,
            baseline, allowBaselineReuse = true)
        assertTrue(grouped.alternatives.single().matchesBaseline)
        val isolated = MidiCoreMusicalRepairAlternativeRanker.rank(listOf(refreshed), MidiCoreMusicalRepairIntent.SMOOTH_TRANSITION, baseline)
        assertTrue(isolated.alternatives.isEmpty())
    }

    @Test
    fun `repair attempt cannot admit more than three generated candidates`() {
        assertFailsWith<IllegalArgumentException> {
            RankMidiCoreMusicalRepairAlternatives(
                ListMidiCoreCandidates(
                    MidiCoreProjectSession(
                        java.nio.file.Path.of("project"),
                        app.melotrail.project.MidiCoreProject(
                            app.melotrail.project.ProjectId("project"),
                            app.melotrail.project.ProjectMetadata("Project", "2026-09-10T00:00:00Z"),
                        ),
                    ),
                    CandidateRole.CHORDS,
                    "verse-1",
                ),
                MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
                listOf("one", "two", "three", "four"),
                null,
                attemptedCount = 3,
            )
        }
    }

    @Test
    fun `ranking keeps at most three timing and pitch distinct repair choices`() {
        val baseline = item("baseline", listOf(note(0, 480, 72, 72)))
        val first = item("first", listOf(note(0, 480, 60, 72), note(0, 480, 64, 72)))
        val velocityOnlyDuplicate = item("velocity-only", listOf(note(0, 480, 60, 108), note(0, 480, 64, 108)))
        val sparse = item("sparse", listOf(note(0, 960, 55, 80)))

        val ranked = MidiCoreMusicalRepairAlternativeRanker.rank(
            listOf(velocityOnlyDuplicate, first, sparse),
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
            baseline,
        )

        assertEquals(listOf("sparse", "first"), ranked.alternatives.map { it.candidateId })
        assertEquals(listOf("velocity-only"), ranked.rejections.map { it.candidateId })
        assertTrue(ranked.rejections.single().reason.contains("duplicates first"))
        assertTrue(ranked.alternatives.all { it.impactLabel.contains("attack") && it.impactLabel.contains("held note") })
    }

    @Test
    fun `candidate matching audible baseline is rejected as cosmetic`() {
        val baseline = item("baseline", listOf(note(0, 480, 60, 72)))
        val sameNotes = item("same-notes", listOf(note(0, 480, 60, 108)))

        val ranked = MidiCoreMusicalRepairAlternativeRanker.rank(
            listOf(sameNotes),
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
            baseline,
        )

        assertTrue(ranked.alternatives.isEmpty())
        assertTrue(ranked.rejections.single().reason.contains("audible baseline"))
        assertTrue(requireNotNull(ranked.noResultReason).contains("same scope"))
    }

    @Test
    fun `empty repair scope stops with a same-scope retry reason`() {
        val ranked = MidiCoreMusicalRepairAlternativeRanker.rank(
            emptyList(),
            MidiCoreMusicalRepairIntent.CALMER_DRUMS,
            attemptedCount = 3,
            generationProblems = listOf("role validation failed"),
        )

        assertTrue(ranked.alternatives.isEmpty())
        val reason = requireNotNull(ranked.noResultReason)
        assertTrue(reason.contains("same scope"))
        assertTrue(reason.contains("3 bounded generation attempts"))
    }

    @Test
    fun `stale and rejected candidates remain evidence instead of repair choices`() {
        val stale = item("stale", listOf(note(0, 480, 60, 72)), authorityCurrent = false)
        val rejected = item("rejected", listOf(note(480, 960, 62, 72)), status = MidiCoreCandidateStatus.REJECTED)

        val ranked = MidiCoreMusicalRepairAlternativeRanker.rank(
            listOf(stale, rejected),
            MidiCoreMusicalRepairIntent.SIMPLIFY_PIANO,
            attemptedCount = 2,
        )

        assertTrue(ranked.alternatives.isEmpty())
        assertEquals(listOf("rejected", "stale"), ranked.rejections.map { it.candidateId })
        assertTrue(ranked.rejections.all { it.reason.contains("evidence only") || it.reason.contains("stale") })
    }

    private fun item(
        id: String,
        notes: List<MidiCoreReviewNote>,
        authorityCurrent: Boolean = true,
        status: MidiCoreCandidateStatus = MidiCoreCandidateStatus.CURRENT,
    ): MidiCoreCandidateReviewItem = MidiCoreCandidateReviewItem(
        candidate = MidiCoreCandidate(
            id = id,
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            generatorVersion = "musical-repair-v1",
            authorityHash = "a".repeat(64),
            seed = 1L,
            midi = artifact("candidates/chords/verse-1/$id.mid"),
            validationReport = artifact("reports/candidates/$id.json"),
            createdAt = "2026-09-10T00:00:00Z",
            status = status,
            rejectionReason = if (status == MidiCoreCandidateStatus.REJECTED) "Rejected by reviewer" else null,
        ),
        validation = MidiCoreRoleValidationReport("b".repeat(64), "c".repeat(64), CandidateRole.CHORDS, "verse-1", notes.size, emptyList()),
        notes = notes,
        authorityCurrent = authorityCurrent,
        accepted = false,
        locked = false,
    )

    private fun note(start: Long, end: Long, pitch: Int, velocity: Int) = MidiCoreReviewNote(start, end, 1, pitch, velocity)

    private fun artifact(path: String) = ProjectArtifact(ProjectRelativePath(path), "d".repeat(64))
}
