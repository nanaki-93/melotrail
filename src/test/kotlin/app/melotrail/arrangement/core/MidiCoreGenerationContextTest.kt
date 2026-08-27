package app.melotrail.arrangement.core

import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptedDependency
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreGeneratorInput
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MidiCoreGenerationContextTest {
    @Test
    fun `equivalent scoped requests have stable hashes and explicit seeds affect identity`() {
        val first = context()
        val equivalent = context()
        val changedSeed = context(seed = 8)
        val unrelatedAuthorityEdit = context(sourceProject = project().copy(
            authority = requireNotNull(project().authority).copy(
                chordEvents = listOf(
                    AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 1_920),
                    AuthoritativeChordEvent("chorus-chord", "chorus-1", "Db", 1_920, 3_840),
                ),
            ),
        ))

        assertEquals(first.contextSha256, equivalent.contextSha256)
        assertEquals(first.contextSha256, unrelatedAuthorityEdit.contextSha256)
        assertEquals(first.canonicalSerialization, equivalent.canonicalSerialization)
        assertEquals(first.generationFingerprint.sha256, equivalent.generationFingerprint.sha256)
        assertNotEquals(first.contextSha256, changedSeed.contextSha256)
        assertNotEquals(first.generationFingerprint.sha256, changedSeed.generationFingerprint.sha256)
        assertEquals(7L, first.seed)
        assertEquals(first.authorityHash, MidiCoreAuthorityHasher.from(project()).scopeHash("verse-1", CandidateRole.BASS))
    }

    @Test
    fun `catalog exposes only core role patterns and complete authored steps`() {
        assertEquals(
            MidiCoreChordRhythmPatternId.entries.map(MidiCoreChordRhythmPatternId::id).toSet(),
            MidiCorePatternCatalog.allowedPatternIds(CandidateRole.CHORDS).toSet(),
        )
        assertEquals(
            MidiCoreBassPatternId.entries.map(MidiCoreBassPatternId::id).toSet(),
            MidiCorePatternCatalog.allowedPatternIds(CandidateRole.BASS).toSet(),
        )
        assertEquals(
            (MidiCoreDrumGroovePatternId.entries.map(MidiCoreDrumGroovePatternId::id) +
                MidiCoreDrumFillPatternId.entries.map(MidiCoreDrumFillPatternId::id)).toSet(),
            MidiCorePatternCatalog.allowedPatternIds(CandidateRole.DRUMS).toSet(),
        )
        assertTrue(MidiCorePatternCatalog.drumGrooves.all { it.steps.isNotEmpty() })
        assertTrue(MidiCorePatternCatalog.drumFills.all { it.steps.isNotEmpty() })
        assertFalse(MidiCorePatternCatalog.inventory().any { it.id.startsWith("transition.") })
        assertFailsWith<IllegalArgumentException> {
            MidiCorePatternCatalog.requireAllowed(CandidateRole.BASS, "transition.bass-approach")
        }
    }

    @Test
    fun `tick grid rejects unrepresentable authored subdivisions`() {
        val grid = MidiCoreTickGrid(MidiPpq(480), ProjectMeter(4, 2))

        assertEquals(480L, grid.ticksPerBeat)
        assertEquals(120L, grid.ticksPerSubdivision)
        assertEquals(240L, grid.ticksForQuarterBeats(1, 2))
        assertEquals(1_920L, grid.ticksPerBar)
        assertEquals(240L, grid.requireRepresentable(240L))
        assertFailsWith<IllegalArgumentException> { grid.requireRepresentable(241L) }
        assertFailsWith<IllegalArgumentException> { grid.ticksForQuarterBeats(1, 7) }
        assertFailsWith<IllegalArgumentException> { MidiCoreTickGrid(MidiPpq(481), ProjectMeter(4, 2)) }
    }

    @Test
    fun `factory scopes harmony melody and accepted dependency evidence to one occurrence`() {
        val snapshot = MidiCoreAuthoritySnapshot.from(project())
        val melodyNotes = listOf(
            protectedNote(120, 240),
            protectedNote(2_040, 2_160),
        )
        val scoped = MidiCoreGenerationContext.forOccurrence(
            authority = snapshot,
            role = CandidateRole.BASS,
            occurrenceId = "verse-1",
            performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.sustained-sub-like"),
            patternId = MidiCoreBassPatternId.ROOT_FIFTH.id,
            generator = MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
            protectedMelodyNotes = melodyNotes,
            acceptedDependencies = listOf(
                MidiCoreAcceptedDependencyContext(
                    MidiCoreAcceptedDependency(CandidateRole.CHORDS, "verse-1", "chords-1", "d".repeat(64)),
                    listOf(MidiCoreGenerationNote(0, 240, 36, 70)),
                ),
            ),
        )

        assertEquals("verse-1", scoped.occurrence.id)
        assertEquals(listOf("verse-chord"), scoped.chordWindows.map { it.event.id })
        assertEquals(listOf(120L), scoped.protectedMelodyNotes.map { it.startTick })
        assertEquals(CandidateRole.CHORDS, scoped.dependency(CandidateRole.CHORDS)?.dependency?.role)
        assertFailsWith<IllegalArgumentException> {
            MidiCoreGenerationContext.forOccurrence(
                authority = snapshot,
                role = CandidateRole.BASS,
                occurrenceId = "verse-1",
                performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.sustained-sub-like"),
                patternId = MidiCoreBassPatternId.ROOT_FIFTH.id,
                generator = MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, 7),
                acceptedDependencies = listOf(
                    MidiCoreAcceptedDependencyContext(
                        MidiCoreAcceptedDependency(CandidateRole.CHORDS, "chorus-1", "chords-2", "e".repeat(64)),
                    ),
                ),
            )
        }
    }

    @Test
    fun `context enforces role profile pattern and fill boundaries`() {
        val snapshot = MidiCoreAuthoritySnapshot.from(project())

        assertFailsWith<IllegalArgumentException> {
            MidiCoreGenerationContext.forOccurrence(
                snapshot,
                CandidateRole.BASS,
                "verse-1",
                MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.CHORDS, "chords.sustained"),
                MidiCoreBassPatternId.SUSTAINED_ROOT.id,
                MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.SUSTAINED_ROOT.id, 1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreGenerationContext.forOccurrence(
                snapshot,
                CandidateRole.BASS,
                "verse-1",
                MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.sustained-sub-like"),
                MidiCoreBassPatternId.SUSTAINED_ROOT.id,
                MidiCoreGeneratorInput("midi-core", "bass-v1", "transition.bass-approach", 1),
            )
        }
        assertEquals("drums.dusty", MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.DRUMS, "drums.dusty").id)
        assertEquals("drums.fill.dusty-snare-roll", MidiCoreDrumFillPatternId.DUSTY_SNARE_ROLL.id)
    }

    private fun context(seed: Long = 7L, sourceProject: MidiCoreProject = project()): MidiCoreGenerationContext = MidiCoreGenerationContext.forOccurrence(
        authority = MidiCoreAuthoritySnapshot.from(sourceProject),
        role = CandidateRole.BASS,
        occurrenceId = "verse-1",
        performanceProfile = MidiCorePerformanceProfileCatalog.requireForRole(CandidateRole.BASS, "bass.muted-plucked"),
        patternId = MidiCoreBassPatternId.ROOT_FIFTH.id,
        generator = MidiCoreGeneratorInput("midi-core", "bass-v1", MidiCoreBassPatternId.ROOT_FIFTH.id, seed),
    )

    private fun protectedNote(start: Long, end: Long) = MidiCoreProtectedMelodyNote(
        id = "pmn-${"a".repeat(63)}${if (start == 120L) "a" else "b"}",
        startTick = start,
        endTick = end,
        pitch = 60,
        velocity = 90,
        anchor = start == 120L,
    )

    private fun project(): MidiCoreProject = MidiCoreProject(
        id = ProjectId("generation-context-project"),
        metadata = ProjectMetadata("Generation context", "2026-08-27T00:00:00Z"),
        sourceMidi = SourceMidiRecord(
            originalFilename = "source.mid",
            sha256 = "a".repeat(64),
            format = 1,
            ppq = 480,
            original = ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)),
            importReport = ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)),
            trackSummaries = listOf(MidiTrackSummary(0, "Melody", emptyList())),
            sourceEndTick = 3_840,
        ),
        selectedMelody = SelectedMelodyTrack(0, 0, "c".repeat(64)),
        authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
            tempo = ProjectTempo(500_000),
            meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0, 1_920),
                ProjectSectionOccurrence("chorus-1", "chorus", "Chorus", 1_920, 3_840),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("verse-chord", "verse-1", "C", 0, 1_920),
                AuthoritativeChordEvent("chorus-chord", "chorus-1", "F", 1_920, 3_840),
            ),
        ),
    )
}
