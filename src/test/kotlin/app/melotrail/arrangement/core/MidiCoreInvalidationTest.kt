package app.melotrail.arrangement.core

import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAuthorityDimension
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreAuthoritySettings
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.MidiCoreAuthorityScopeKey
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreInvalidationTest {
    @Test
    fun `chorus-only harmony edit targets chorus and its accepted dependents`() {
        val beforeProject = project()
        val afterProject = beforeProject.copy(
            authority = requireNotNull(beforeProject.authority).copy(
                chordEvents = listOf(
                    AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                    AuthoritativeChordEvent("chorus-chord", "chorus-1", "Db", 480, 960),
                ),
            ),
        )
        val before = MidiCoreAuthorityHasher.from(beforeProject)
        val after = MidiCoreAuthorityHasher.from(afterProject)
        val dependencies = listOf(
            MidiCoreCandidateDependency("verse-chords", CandidateRole.CHORDS, "verse-1", before.scopeHash("verse-1", CandidateRole.CHORDS)),
            MidiCoreCandidateDependency("chorus-chords", CandidateRole.CHORDS, "chorus-1", before.scopeHash("chorus-1", CandidateRole.CHORDS)),
            MidiCoreCandidateDependency(
                "verse-bass",
                CandidateRole.BASS,
                "verse-1",
                before.scopeHash("verse-1", CandidateRole.BASS),
                acceptedDependencyIds = listOf("chorus-chords"),
            ),
        )
        val preview = MidiCoreInvalidationPlanner.preview(
            before,
            after,
            dependencies,
            listOf(
                MidiCoreExportDependency("export-current", before.sha256),
                MidiCoreExportDependency("export-old", "e".repeat(64)),
            ),
        )

        assertEquals(listOf(MidiCoreAuthorityDimension.HARMONY), preview.changedDimensions)
        assertEquals(
            setOf(
                MidiCoreAuthorityScopeKey("chorus-1", CandidateRole.CHORDS),
                MidiCoreAuthorityScopeKey("chorus-1", CandidateRole.BASS),
                MidiCoreAuthorityScopeKey("chorus-1", CandidateRole.DRUMS),
            ),
            preview.affectedScopes.toSet(),
        )
        assertEquals(listOf("chorus-chords", "verse-bass"), preview.staleCandidateIds)
        assertEquals(listOf("export-current"), preview.staleExportIds)
        assertEquals(
            listOf(MidiCoreInvalidationReason.HARMONY_CHANGED),
            preview.staleTargets.single { it.id == "chorus-chords" }.reasons,
        )
        assertEquals(
            listOf(MidiCoreInvalidationReason.ACCEPTED_DEPENDENCY_CHANGED),
            preview.staleTargets.single { it.id == "verse-bass" }.reasons,
        )
        assertFalse(preview.staleCandidateIds.contains("verse-chords"))
    }

    @Test
    fun `unchanged authority has no impact and scoped changes explain their dimensions`() {
        val project = project()
        val fingerprint = MidiCoreAuthorityHasher.from(project)
        val noOp = MidiCoreInvalidationPlanner.preview(fingerprint, fingerprint)

        assertFalse(noOp.hasImpact)
        assertTrue(noOp.staleTargets.isEmpty())
        assertFalse(noOp.affects(CandidateRole.CHORDS, "verse-1"))

        val changed = project.copy(
            authority = requireNotNull(project.authority).copy(
                occurrences = listOf(
                    ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 480),
                    ProjectSectionOccurrence("chorus-1", "chorus", "Chorus moved", 480, 960),
                ),
                chordEvents = listOf(
                    AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                    AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 480, 960),
                ),
            ),
        )
        val changedPreview = MidiCoreInvalidationPlanner.preview(fingerprint, MidiCoreAuthorityHasher.from(changed))

        assertEquals(listOf(MidiCoreAuthorityDimension.STRUCTURE), changedPreview.changedDimensions)
        assertTrue(changedPreview.affects(CandidateRole.CHORDS, "chorus-1"))
        assertFalse(changedPreview.affects(CandidateRole.CHORDS, "verse-1"))
    }

    @Test
    fun `role-prefixed settings invalidate only that role`() {
        val project = project()
        val before = MidiCoreAuthorityHasher.from(project, MidiCoreAuthoritySettings(mapOf("chords.rhythm" to "block")))
        val after = MidiCoreAuthorityHasher.from(project, MidiCoreAuthoritySettings(mapOf("chords.rhythm" to "offbeat")))
        val preview = MidiCoreInvalidationPlanner.preview(before, after)

        assertEquals(listOf(MidiCoreAuthorityDimension.SETTINGS), preview.changedDimensions)
        assertEquals(
            setOf(
                MidiCoreAuthorityScopeKey("verse-1", CandidateRole.CHORDS),
                MidiCoreAuthorityScopeKey("chorus-1", CandidateRole.CHORDS),
            ),
            preview.affectedScopes.toSet(),
        )
        assertTrue(preview.affects(CandidateRole.CHORDS, "verse-1"))
        assertFalse(preview.affects(CandidateRole.BASS, "verse-1"))
    }

    @Test
    fun `async generation completion is admitted only for the exact authority and inputs`() {
        val beforeProject = project()
        val changedProject = beforeProject.copy(
            authority = requireNotNull(beforeProject.authority).copy(
                chordEvents = listOf(
                    AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                    AuthoritativeChordEvent("chorus-chord", "chorus-1", "Db", 480, 960),
                ),
            ),
        )
        val generator = MidiCoreGeneratorInput("midi-core", "v1", "root-quarter", 11)
        val verseExpected = MidiCoreAuthorityHasher.generation(beforeProject, "verse-1", CandidateRole.BASS, generator)
        val unrelatedChorusEdit = MidiCoreAuthorityHasher.generation(changedProject, "verse-1", CandidateRole.BASS, generator)
        val chorusExpected = MidiCoreAuthorityHasher.generation(beforeProject, "chorus-1", CandidateRole.CHORDS, generator)
        val changedChorus = MidiCoreAuthorityHasher.generation(changedProject, "chorus-1", CandidateRole.CHORDS, generator)
        val changedGenerator = MidiCoreAuthorityHasher.generation(
            beforeProject,
            "verse-1",
            CandidateRole.BASS,
            generator.copy(seed = 12),
        )

        assertIs<MidiCoreGenerationAdmissionResult.Accepted>(MidiCoreGenerationAdmission.admit(verseExpected, unrelatedChorusEdit))
        val staleAuthority = assertIs<MidiCoreGenerationAdmissionResult.Rejected>(
            MidiCoreGenerationAdmission.admit(chorusExpected, changedChorus),
        )
        val staleGenerator = assertIs<MidiCoreGenerationAdmissionResult.Rejected>(
            MidiCoreGenerationAdmission.admit(verseExpected, changedGenerator),
        )
        assertEquals(chorusExpected.authorityHash, staleAuthority.problem.expectedAuthorityHash)
        assertNotEquals(changedChorus.authorityHash, staleAuthority.problem.expectedAuthorityHash)
        assertNotEquals(verseExpected.sha256, staleGenerator.problem.currentGenerationSha256)
    }

    private fun project(): MidiCoreProject = MidiCoreProject(
        ProjectId("invalidation-project"),
        ProjectMetadata("Invalidation", "2026-08-27T00:00:00Z"),
        authority = ProjectAuthority(
            ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            ProjectTempo(500_000),
            ProjectMeter(4, 2),
            listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
            listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 480),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 480, 960),
            ),
            listOf(
                AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 480, 960),
            ),
        ),
    )
}
