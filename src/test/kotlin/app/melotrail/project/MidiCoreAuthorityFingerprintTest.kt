package app.melotrail.project

import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreAuthorityFingerprintTest {
    @Test
    fun `each authority dimension changes its canonical fingerprint`() {
        val project = project()
        val baseline = MidiCoreAuthorityHasher.from(project)

        val changedSource = MidiCoreAuthorityHasher.from(project.copy(sourceMidi = source("e".repeat(64))))
        val changedMelody = MidiCoreAuthorityHasher.from(
            project.copy(selectedMelody = SelectedMelodyTrack(0, 0, "f".repeat(64))),
        )
        val changedTiming = MidiCoreAuthorityHasher.from(
            project.copy(authority = requireNotNull(project.authority).copy(tempo = ProjectTempo(1_000_000))),
        )
        val changedStructure = MidiCoreAuthorityHasher.from(
            project.copy(
                authority = requireNotNull(project.authority).copy(
                    occurrences = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse changed", 0, 480)),
                ),
            ),
        )
        val changedHarmony = MidiCoreAuthorityHasher.from(
            project.copy(
                authority = requireNotNull(project.authority).copy(
                    chordEvents = listOf(AuthoritativeChordEvent("chord-1", "verse-1", "Db", 0, 480)),
                ),
            ),
        )
        val changedSettings = MidiCoreAuthorityHasher.from(
            project,
            MidiCoreAuthoritySettings(mapOf("density" to "sparse")),
        )

        assertNotEquals(baseline.sourceSha256, changedSource.sourceSha256)
        assertNotEquals(baseline.melodySha256, changedMelody.melodySha256)
        assertNotEquals(baseline.timingSha256, changedTiming.timingSha256)
        assertNotEquals(baseline.structureSha256, changedStructure.structureSha256)
        assertNotEquals(baseline.harmonySha256, changedHarmony.harmonySha256)
        assertNotEquals(baseline.settingsSha256, changedSettings.settingsSha256)
        assertTrue(baseline.canonicalSerialization.isNotBlank())
        assertNotEquals(baseline.sha256, changedSettings.sha256)
    }

    @Test
    fun `chorus harmony changes only chorus scoped hashes`() {
        val project = project(
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 480),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 480, 960),
            ),
            chords = listOf(
                AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 480, 960),
            ),
        )
        val changed = project.copy(
            authority = requireNotNull(project.authority).copy(
                chordEvents = listOf(
                    AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 480),
                    AuthoritativeChordEvent("chorus-chord", "chorus-1", "Db", 480, 960),
                ),
            ),
        )
        val before = MidiCoreAuthorityHasher.from(project)
        val after = MidiCoreAuthorityHasher.from(changed)

        assertEquals(before.scopeHash("verse-1", CandidateRole.CHORDS), after.scopeHash("verse-1", CandidateRole.CHORDS))
        assertNotEquals(before.scopeHash("chorus-1", CandidateRole.CHORDS), after.scopeHash("chorus-1", CandidateRole.CHORDS))
        assertEquals(before.scopeHash("verse-1", CandidateRole.BASS), after.scopeHash("verse-1", CandidateRole.BASS))
        assertNotEquals(before.harmonySha256, after.harmonySha256)
    }

    @Test
    fun `generation fingerprint binds generator and accepted dependency inputs`() {
        val project = project()
        val dependency = MidiCoreAcceptedDependency(CandidateRole.CHORDS, "verse-1", "chords-1", "d".repeat(64))
        val first = MidiCoreAuthorityHasher.generation(
            project,
            "verse-1",
            CandidateRole.BASS,
            MidiCoreGeneratorInput("midi-core", "bass-v1", "root-quarter", 7),
            listOf(dependency),
        )
        val reorderedEquivalent = MidiCoreAuthorityHasher.generation(
            project,
            "verse-1",
            CandidateRole.BASS,
            MidiCoreGeneratorInput("midi-core", "bass-v1", "root-quarter", 7),
            listOf(dependency),
        )
        val changedSeed = MidiCoreAuthorityHasher.generation(
            project,
            "verse-1",
            CandidateRole.BASS,
            MidiCoreGeneratorInput("midi-core", "bass-v1", "root-quarter", 8),
            listOf(dependency),
        )
        val changedDependency = MidiCoreAuthorityHasher.generation(
            project,
            "verse-1",
            CandidateRole.BASS,
            MidiCoreGeneratorInput("midi-core", "bass-v1", "root-quarter", 7),
            listOf(dependency.copy(candidateId = "chords-2")),
        )

        assertEquals(first.authorityHash, MidiCoreAuthorityHasher.from(project).scopeHash("verse-1", CandidateRole.BASS))
        assertEquals(first.sha256, reorderedEquivalent.sha256)
        assertNotEquals(first.sha256, changedSeed.sha256)
        assertNotEquals(first.sha256, changedDependency.sha256)
        assertTrue(first.canonicalSerialization.contains("root-quarter"))
    }

    private fun project(
        occurrences: List<ProjectSectionOccurrence> = listOf(ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 480)),
        chords: List<AuthoritativeChordEvent> = listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 480)),
    ): MidiCoreProject = MidiCoreProject(
        ProjectId("fingerprint-project"),
        ProjectMetadata("Fingerprint", "2026-08-27T00:00:00Z"),
        sourceMidi = source("a".repeat(64)),
        selectedMelody = SelectedMelodyTrack(0, 0, "b".repeat(64)),
        authority = ProjectAuthority(
            ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            ProjectTempo(500_000),
            ProjectMeter(4, 2),
            listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
            occurrences,
            chords,
        ),
    )

    private fun source(hash: String) = SourceMidiRecord(
        "source.mid",
        hash,
        0,
        480,
        ProjectArtifact(ProjectRelativePath("source/original.mid"), hash),
        ProjectArtifact(ProjectRelativePath("reports/import.json"), "c".repeat(64)),
        listOf(MidiTrackSummary(0, null, emptyList())),
        480,
    )
}
